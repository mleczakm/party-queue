# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/). The version lives in `version.properties`; a release is the
git tag `v<version>`.

## [Unreleased]

## [0.2.0] - 2026-10-08

### Added
- The host can promote any guest to co-host right away (*Nadaj uprawnienia hosta* in the app, *Mianuj hostem* on the web page);
  the guest's page reloads itself into host mode (and again if the rights are taken away).
- Volume slider (the phone's media volume, follows the volume keys; tap the speaker to mute) in the now-playing card.
- Changing a song by hand (next, previous, play now / swipe right, or a guest co-host pressing next) fades the old song
  out (2 s) while the next one fades in, once the next one is loaded; a song that simply ends still switches instantly.
- The preview of the playing video is one icon button (crossed-out screen / screen / full screen) and is hidden by default.
- The title bar hides after 8 s without a touch; a small handle (or a pull down) brings it back.
- New look: light and dark colour themes (header button: auto / light / dark), party style with gradients, a
  slowly flowing now-playing card, animated equalizer, sliding tab indicator and transitions.
- Branding: new app icon (adaptive, with a monochrome variant), notification icon, splash colour, `branding/` assets.
- A short tap on a song moves it to the top of the queue.
- Guest page rewritten: works on phones and big screens (two columns), light/dark theme, icons, a connection banner,
  keyboard shortcuts for hosts (Space, N, P, arrows, Enter, T, Delete, Alt+arrows, R, /, 1-5, D, ?), mouse drag-and-drop
  reordering, double-click to play, tooltips; `POST /api/queue/{uid}/move` and `/api/player/repeat`.
- *Wklej link* in the YouTube tab; *Udostępnij link* / *Kopiuj* in the *Zaproś* tab.
- Song thumbnails in the queue, in proposals and in search results.
- Song titles stay on one line; a long title slowly scrolls so it can be read in full.
- Next song is loaded in advance in a second, silent player page, so a change of song is instant (about 0.05 s)
  with the screen on or off. YouTube delays the start of videos for people who block ads by 5-13 s; this hides it.
- Reorder the queue by holding a row and dragging it (it lifts, follows the finger, and the list scrolls at the
  edges so a song can be carried far); the ▲▼ buttons are gone from the host screen.
- Queue gestures: swipe a row right to play it now (green), left to remove it (red); after a few seconds without
  touching the list the first rows slide sideways to show this. *Wyczyść kolejkę* empties the queue (with confirmation;
  also for co-hosts on the guest page and via `POST /api/queue/clear`).
- YouTube tab shows only the buttons that fit the page: *Ustaw jako aktualną playlistę* on playlist pages,
  *Dodaj do kolejki* / *Jako następny* on videos. The sign-in button disappears once you are signed in.
- Auto-generated YouTube Mixes can be imported from a video page (their songs are read from the page's "up next" list).

### Fixed
- Some headings were drawn black on the dark background.
- The guest page did not work (it needed a one-time code from the QR link).
- The next song did not start (or started half a minute late) while the screen was off: the player page now has high
  priority so Android no longer slows or kills it, songs are loaded inside the running page instead of reloading it,
  and the next song is prepared in advance (see Added).

### Changed
- *Dodaj do kolejki* (host app, pasted link, the host's *Dodaj* on the guest page) puts the song at the **end** of the queue;
  *Jako następny* still puts it first, and songs approved from guests' proposals still go before the rest of the playlist.
- The *Dołącz* tab is now *Zaproś*; the *Dodaj* tab is gone (its functions moved to the queue header and the YouTube tab).
- Joining needs no code: whoever opens the page and gives a name is a guest. The QR code simply opens the page and has a
  proper white border; *Nowy kod QR* is gone.
- Playlists keep YouTube's order by default (shuffle is off unless switched on).
- The hint that shows swiping appears rarely (after ~45 s of idleness, at most every 3 minutes) and only on the first row.
- A song that does not start within 45 s (was 30 s) is skipped.
- "Wczytaj playlistę" is now called *Ustaw jako aktualną playlistę*; the "home" button is gone.

## [0.1.0] - 2026-10-07

First public version.

### Added
- Ad-free YouTube playback in an embedded Firefox engine (GeckoView) with uBlock Origin and a
  background-playback add-on; playback continues with the screen off (foreground service, wake and Wi-Fi locks).
- Local queue loaded from a public YouTube playlist, plus song search and pasted links, without a Google
  account or API key.
- Guest web page on the local network (Ktor, WebSocket): QR join, live queue, song proposals.
- Host approval of proposals; guests can ask for host rights; co-hosts have the host's powers, including
  approving other co-hosts.
- Host UI (Compose) that keeps the YouTube page clear of the system bars; queue is restored after a restart.
- YouTube tab: a separate browser session for looking around YouTube, with one-tap *Importuj playlistę*,
  *Dodaj do kolejki* and *Jako następny*; browsing never disturbs the playing queue. Playlists that cannot be read
  anonymously are read from the page after signing in (experimental, not yet verified with a private list).
- Unit tests for the parser, queue logic and server API; `scripts/smoke-test.sh` for a connected phone.

### Fixed
- Picture and sound stuttered once a second while the queue list was visible: the whole screen was rebuilt on
  every playback tick; now only the now-playing bar updates.
- Blank player pane after restart or after closing the app with BACK (BACK now hides the app).
- Music stuttered once a second when the YouTube page was small or hidden (for example on the YouTube tab):
  YouTube paused a video it thought nobody could see and the app resumed it again. The player page can no longer
  pause itself; the app's own pause button still works. The bridge also acts only on the page's main `<video>`
  element instead of any spare one.

[Unreleased]: https://github.com/mleczakm/party-queue/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/mleczakm/party-queue/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/mleczakm/party-queue/releases/tag/v0.1.0
