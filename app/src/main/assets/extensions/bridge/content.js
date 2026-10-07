// Runs inside youtube.com pages in two roles, decided by the "pq" URL parameter that the app adds:
//   player: a page that plays (or is getting ready to play) songs of the queue. The app keeps two of them,
//           slot "a" and slot "b"; the one that is not playing ("standby") already holds the next song, paused.
//   browse: the page the host uses to look around; it stays quiet and can hand over a playlist's data.
(function () {
  const port = browser.runtime.connect({ name: "yt" });
  const params = new URL(location.href).searchParams;
  const mode = params.has("pq") ? "player" : "browse";
  const slot = params.get("pq") || "a";
  const send = (m) => port.postMessage(Object.assign({ mode, slot }, m));
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

    // Tell the app whether the host is signed in (it hides the "sign in" button afterwards).
    let lastLogin = null;
    setInterval(() => {
      let signedIn = false;
      try {
        const cfg = window.wrappedJSObject.ytcfg;
        signedIn = !!(cfg && (cfg.get ? cfg.get("LOGGED_IN") : cfg.data_ && cfg.data_.LOGGED_IN));
      } catch (e) { /* page not ready */ }
      if (signedIn !== lastLogin) {
        lastLogin = signedIn;
        send({ type: "login", signedIn });
      }
    }, 1500);

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
  // A standby page loads its song and then waits, silent and paused, until the app activates it.
  let standby = params.has("standby");
  let announced = "";
  // After an in-place load the element still holds the old song's data until the new load starts.
  let freshLoadPending = false;

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
    if (!video || standby) return;
    send({
      type: "state",
      state,
      videoId: videoId(),
      pos: Math.round((video.currentTime || 0) * 1000),
      dur: Math.round((isFinite(video.duration) ? video.duration : 0) * 1000),
      ad: isAd(),
    });
  }

  /** Standby pages tell the app once their song can start instantly. */
  function announceReady() {
    const id = videoId();
    if (!standby || !video || freshLoadPending || video.readyState < 3 || announced === id || isAd()) return;
    announced = id;
    video.pause();
    send({ type: "standbyReady", videoId: id });
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
    if (standby) v.muted = true;
    for (const [ev, state] of Object.entries(events)) {
      v.addEventListener(ev, () => {
        if (standby && ev === "playing") { announceReady(); if (!announced) return; v.pause(); return; }
        report(state);
      });
    }
    v.addEventListener("loadstart", () => { freshLoadPending = false; });
    v.addEventListener("canplay", announceReady);
    v.addEventListener("timeupdate", () => {
      const now = Date.now();
      if (now - lastTick > 1000) {
        lastTick = now;
        report(v.paused ? "paused" : "playing");
      }
    });
    v.addEventListener("error", () => { if (!standby) send({ type: "error", videoId: videoId() }); });
    // YouTube's own "up next" must never take over; the app drives the queue.
    v.loop = false;
    log("video attached paused=" + v.paused + " ready=" + v.readyState + (standby ? " (standby)" : ""));
  }

  // The mobile site loads nothing until its big play button is pressed; do that for it, politely: at most one
  // press a second (earlier presses hit a page that is not ready), until the video has data.
  let startTries = 0;
  let startedFor = "";
  let lastPress = 0;
  function kickStart() {
    if (!video || video.readyState !== 0 || video.ended) return;
    const id = videoId();
    if (id !== startedFor) { startedFor = id; startTries = 0; }
    if (startTries >= 40 || Date.now() - lastPress < 1000) return;
    const btn = document.querySelector(".ytp-large-play-button, .player-controls-play-pause-icon button");
    if (btn && !isAd()) {
      startTries++;
      lastPress = Date.now();
      log("kickstart click #" + startTries);
      btn.click();
    }
  }

  function tick() {
    dismissConsent();
    const v = mainVideo();
    if (v) attach(v);
    kickStart();
  }

  // Timers can be slowed down when the screen is off, DOM notifications are not: start on both.
  new MutationObserver(() => { if (video === null || video.readyState === 0) tick(); })
    .observe(document, { childList: true, subtree: true });

  setInterval(() => {
    tick();
    if (!video) return;
    if (standby) {
      // Stay silent and still, whatever the page tries.
      if (!video.muted) video.muted = true;
      if (video.readyState >= 3) announceReady();
      if (!video.paused && announced === videoId()) video.pause();
      return;
    }
    // Loaded but idle (e.g. after a stall): resume unless the host paused on purpose.
    if (video.readyState > 0 && video.paused && !video.ended && !hostPaused) {
      video.play().catch(() => {});
    }
  }, 1000);

  // Switching songs inside the page that is already running avoids reloading YouTube's player.
  function loadInPlace(id, asStandby) {
    try {
      const el = document.querySelector(".html5-video-player");
      const api = el && el.wrappedJSObject;
      if (!api || typeof api.loadVideoById !== "function") throw new Error("no player api");
      standby = !!asStandby;
      hostPaused = standby;
      announced = "";
      freshLoadPending = true;
      if (video) video.muted = standby;
      api.loadVideoById(id);
      history.replaceState(null, "", "/watch?v=" + id + "&pq=" + slot + (standby ? "&standby=1" : ""));
      startedFor = id;
      startTries = 0;
      send({ type: "loadAck", videoId: id, ok: true });
    } catch (e) {
      log("in-place load failed: " + e);
      send({ type: "loadAck", videoId: id, ok: false });
    }
  }

  port.onMessage.addListener((cmd) => {
    if (cmd.slot && cmd.slot !== slot) return;
    if (cmd.cmd === "loadVideo") { loadInPlace(cmd.id, cmd.standby); return; }
    if (!video) return;
    switch (cmd.cmd) {
      case "activate": // the app switches to this page: the prepared song starts now
        standby = false;
        hostPaused = false;
        video.muted = false;
        video.currentTime = 0;
        video.play().catch(() => {});
        break;
      case "deactivate": // the song of this page is over; it becomes the spare
        standby = true;
        hostPaused = true;
        announced = "";
        video.pause();
        video.muted = true;
        break;
      case "play": hostPaused = false; video.play().catch(() => {}); break;
      case "pause": hostPaused = true; video.pause(); break;
      case "seek": video.currentTime = (cmd.arg || 0) / 1000; break;
      case "stop": video.pause(); break;
    }
  });
})();
