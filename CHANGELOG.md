# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/). The version lives in `version.properties`; a release is the
git tag `v<version>`.

## [Unreleased]

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

[Unreleased]: https://github.com/mleczakm/party-queue/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/mleczakm/party-queue/releases/tag/v0.1.0
