// Runs inside youtube.com pages in two roles, decided by the "pq" URL parameter that the app adds:
//   player: the page that plays the queue; reports state, obeys commands, starts videos by itself.
//   browse: the page the host uses to look around; it stays quiet and can hand over a playlist's data.
(function () {
  const port = browser.runtime.connect({ name: "yt" });
  const params = new URL(location.href).searchParams;
  const mode = params.has("pq") ? "player" : "browse";
  const send = (m) => port.postMessage(Object.assign({ mode }, m));
  const log = (m) => send({ type: "log", msg: String(m) });

  // Chrome clean-up. The player pane is small, so its logo (which overlaps the search icon) and the
  // "open the app" button go; the browse view keeps the logo but loses the nag banners.
  const style = document.createElement("style");
  style.textContent = `
    ytm-open-app-banner, ytm-upsell-dialog-renderer, .mobile-topbar-header-sign-in-button { display: none !important; }
    ${mode === "player" ? "ytm-home-logo { display: none !important; }" : ""}
  `;
  (document.head || document.documentElement).append(style);

  // Cookie banner: always choose the privacy-preserving "reject all". The reject button is only
  // rendered after the dialog has been expanded, so expand first.
  const REJECT = /odrzu[cć]|reject|refuse|ablehn|rechaz|rifiut/i;
  const EXPAND = /czytaj dalej|read more|weiterlesen|lire la suite|leer m[aá]s|continua a leggere/i;
  function dismissConsent() {
    const dialog = location.hostname.startsWith("consent.") ||
      document.querySelector("ytm-consent-bump-v2-renderer, ytd-consent-bump-v2-renderer, #consent-bump");
    if (!dialog) return;
    const buttons = [...document.querySelectorAll("button, a[role=button], [role=button]")];
    const label = (b) => (b.getAttribute("aria-label") || b.textContent || "").trim();
    const reject = buttons.find((b) => REJECT.test(label(b)));
    if (reject) {
      log("consent: rejecting via '" + label(reject).slice(0, 40) + "'");
      reject.click();
      return;
    }
    const expand = buttons.find((b) => EXPAND.test(label(b)));
    if (expand) expand.click();
  }

  if (mode === "browse") {
    // Choosing a song must not make noise next to the queue's own player.
    setInterval(() => {
      dismissConsent();
      document.querySelectorAll("video").forEach((v) => { if (!v.paused) { v.muted = true; v.pause(); } });
    }, 700);

    // The app reloads a playlist page with ?pqimport=1 to read it: ytInitialData is only fresh on a real load.
    if (params.has("pqimport")) {
      let tries = 0;
      const timer = setInterval(() => {
        const data = window.wrappedJSObject.ytInitialData;
        if (data || ++tries > 40) {
          clearInterval(timer);
          if (data) send({ type: "playlistData", url: location.href, data: JSON.stringify(data) });
          else send({ type: "playlistData", url: location.href, data: "" });
        }
      }, 500);
    }
    return;
  }

  // ---------------------------------------------------------------- player mode
  // YouTube pauses a video it thinks nobody can see (small or hidden player, covered screen). The queue must keep
  // playing, so the page loses the ability to pause. Pauses from the app go through the element directly
  // (this script sees the native method), so the host's pause button still works.
  try {
    const proto = window.wrappedJSObject.HTMLMediaElement.prototype;
    exportFunction(function () { /* ignored: only the app decides */ }, proto, { defineAs: "pause" });
  } catch (e) {
    log("could not neutralise page pauses: " + e);
  }

  let video = null;
  let lastTick = 0;
  let hostPaused = false;

  const videoId = () => new URL(location.href).searchParams.get("v") || "";

  // YouTube keeps several <video> elements around (ads, previews, hidden spares). Only the main one counts;
  // acting on a spare one makes the real player stutter and the reported state flap.
  function mainVideo() {
    const all = [...document.querySelectorAll("video")];
    const tagged = all.find((v) => v.classList.contains("html5-main-video"));
    if (tagged) return tagged;
    const area = (v) => { const r = v.getBoundingClientRect(); return r.width * r.height; };
    return all.sort((x, y) => area(y) - area(x))[0] || null;
  }
  const isAd = () =>
    !!document.querySelector(".ad-showing, .ad-interrupting, .ytp-ad-player-overlay, ytm-ad-slot-renderer");

  function report(state) {
    if (!video) return;
    send({
      type: "state",
      state,
      videoId: videoId(),
      pos: Math.round((video.currentTime || 0) * 1000),
      dur: Math.round((isFinite(video.duration) ? video.duration : 0) * 1000),
      ad: isAd(),
    });
  }

  const events = {
    playing: "playing",
    pause: "paused",
    ended: "ended",
    waiting: "waiting",
    loadedmetadata: "loading",
  };

  function attach(v) {
    if (v === video) return;
    video = v;
    for (const [ev, state] of Object.entries(events)) {
      v.addEventListener(ev, () => report(state));
    }
    v.addEventListener("timeupdate", () => {
      const now = Date.now();
      if (now - lastTick > 1000) {
        lastTick = now;
        report(v.paused ? "paused" : "playing");
      }
    });
    v.addEventListener("error", () => send({ type: "error", videoId: videoId() }));
    // YouTube's own "up next" must never take over; the app drives the queue.
    v.loop = false;
    log("video attached paused=" + v.paused + " ready=" + v.readyState);
  }

  // The mobile site loads nothing until its big play button is pressed; do that for it.
  let startTries = 0;
  let startedFor = "";
  function kickStart() {
    if (!video || video.readyState !== 0 || video.ended) return;
    const id = videoId();
    if (id !== startedFor) { startedFor = id; startTries = 0; }
    if (startTries >= 6) return;
    const btn = document.querySelector(".ytp-large-play-button, .player-controls-play-pause-icon button");
    if (btn && !isAd()) {
      startTries++;
      log("kickstart click #" + startTries);
      btn.click();
    }
  }

  setInterval(() => {
    dismissConsent();
    const v = mainVideo();
    if (v) attach(v);
    kickStart();
    // Loaded but idle (e.g. after a stall): resume unless the host paused on purpose.
    if (video && video.readyState > 0 && video.paused && !video.ended && !hostPaused) {
      video.play().catch(() => {});
    }
  }, 1000);

  port.onMessage.addListener((cmd) => {
    if (!video || (cmd.target && cmd.target !== "player")) return;
    switch (cmd.cmd) {
      case "play": hostPaused = false; video.play().catch(() => {}); break;
      case "pause": hostPaused = true; video.pause(); break;
      case "seek": video.currentTime = (cmd.arg || 0) / 1000; break;
      case "stop": video.pause(); break;
    }
  });
})();
