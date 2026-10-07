# Quality assurance

Three layers, from fast to thorough. A change is ready when the first two are green and the third has been
walked through for the areas it touches. A release needs all three.

## 1. Automatic (every push and pull request, see `.github/workflows/ci.yml`)

```bash
./gradlew lintDebug testDebugUnitTest assembleDebug
```

| What | Where |
| --- | --- |
| Android lint (errors fail the build) | `lintDebug` |
| Parsing of YouTube pages, links and playlists | `YouTubeParserTest` |
| Queue, proposals, roles, playback and restart logic | `PartyControllerTest` |
| HTTP API: authentication, host-only routes, the proposal and co-host flows | `PartyServerTest` |
| Shell scripts | `shellcheck` |

Add a unit test with every behaviour change in `PartyController`, `PartyServer` or the parsers. When YouTube
changes its page layout, capture the new JSON shape as a fixture in `YouTubeParserTest` first, then fix the parser.

## 2. Device smoke test (before merging anything that touches the app's runtime behaviour)

Install a debug build on a phone, start the app, then (debug builds also accept `--ei tab <0-4> --es browse <url>` extras on the launch intent, handy for scripted UI checks):

```bash
scripts/smoke-test.sh <phone-ip> [adb-serial]
```

It drives the whole guest/host/co-host flow through the HTTP API of the running app (14 checks). It needs the
phone and the computer on the same network and `adb` access (it reads the host token with `run-as`, which only
works for debug builds).

## 3. Manual checklist (before a release, and when playback or the add-ons change)

Use a real phone, not an emulator. Note the phone model and Android version in the release PR.

**Playback**
- [ ] Load a public playlist (Dodaj → Wczytaj playlistę). The first song starts without touching the screen.
- [ ] No advert appears in 5 songs. (If one does, note which and whether uBlock lists were updated.)
- [ ] A song that ends moves to the next one; skipping with ⏭ and ⏮ works.
- [ ] A video that cannot be embedded or is removed is skipped with a notice within ~30 s.
- [ ] After a fresh install the cookie banner disappears by itself and is not shown again.

**Screen off and background**
- [ ] Lock the screen with music playing: sound continues for at least 10 minutes.
- [ ] A guest phone can still propose a song and see the queue while the host screen is off.
- [ ] Press BACK on the host: the app hides, music keeps playing; reopening shows the video, not a blank pane.
- [ ] Swipe the app away from recents: the notification and playback survive or stop cleanly (note which).

**Queue gestures**
- [ ] Swipe a row right: the song plays now and leaves the queue. Swipe left: it is removed. A short, slow drag springs back.
- [ ] Leave the queue alone for ~6 s: the first three rows slide right (green, play icon) then left (red, bin icon).
- [ ] *Wyczyść kolejkę* asks for confirmation, empties the queue, keeps the current song, and the queue does not refill itself.

**Browsing and importing (YouTube tab)**
- [ ] The tab shows one YouTube page; the player is not visible as a second window.
- [ ] Open a public playlist page: only *Ustaw jako aktualną playlistę* (and the shuffle switch) is offered; it fills the queue and does **not** interrupt the song that is playing.
- [ ] On the home page no import buttons are shown. After signing in, the sign-in button is gone.
- [ ] Open a Mix (a video opened from a "Mix" chip, URL contains `list=RD...`): the same button imports the songs listed in the mix.
- [ ] Open a video page (outside a playlist): only *Dodaj do kolejki* and *Jako następny* are offered, and they put it in the right place.
- [ ] Music keeps playing, without stutter, while you browse, while the queue list is visible, and after switching tabs.
  (Objective check: a pane that is too small makes YouTube pause the video; the position shown under the title must advance
  one second per second.)
- [ ] Optional: sign in (*Zaloguj się*), open a private playlist, import it. (Not verified yet; see CHANGELOG.)

**Guests**
- [ ] The QR code from the *Dołącz* tab opens the guest page on another phone on the same Wi-Fi.
- [ ] A guest can search, paste a link, and propose; the host sees it under *Propozycje* and can approve or reject.
- [ ] Approved songs play before the rest of the playlist, in the order they were approved.
- [ ] *Poproś o uprawnienia hosta* → the host approves → the guest sees the host controls and can approve others.
- [ ] *Nowy kod QR* makes the old QR stop working; *Przyjmuj nowych gości* off blocks new joins; existing guests stay connected.
- [ ] A removed guest is logged out at once.

**Interface**
- [ ] The YouTube pane never goes under the status bar or the navigation bar (portrait and landscape).
- [ ] With nothing playing, the pane shows the "Party Queue" placeholder, not a blank or grey area.
- [ ] The YouTube logo and the "open the app" button are not shown in the pane.
- [ ] Restarting the app restores the queue (paused) and the guests.

## Reporting a defect

Include: phone model, Android version, app version (*Dołącz* tab footer or `versionName`), what you did, what you
expected, and `adb logcat -s PartyQueue` output around the failure.
