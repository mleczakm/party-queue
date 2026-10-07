// Relays between the YouTube page (content scripts) and the Android app (native port).
const pagePorts = new Set();
let native = null;

function connectNative() {
  native = browser.runtime.connectNative("browser");
  native.onMessage.addListener((msg) => {
    for (const p of pagePorts) {
      try { p.postMessage(msg); } catch (e) { pagePorts.delete(p); }
    }
  });
  native.onDisconnect.addListener(() => {
    native = null;
    setTimeout(connectNative, 1000);
  });
}

browser.runtime.onConnect.addListener((port) => {
  pagePorts.add(port);
  port.onDisconnect.addListener(() => pagePorts.delete(port));
  port.onMessage.addListener((msg) => {
    if (native) native.postMessage(msg);
  });
});

connectNative();
