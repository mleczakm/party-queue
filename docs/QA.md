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

Install a debug build on a phone, start the app, then (debug builds also accept `--ei tab <0-3> --ei preview <0-2> --es browse <url>` extras on the launch intent, handy for scripted UI checks):

```bash
scripts/smoke-test.sh <phone-ip> [adb-serial]
```

It drives the whole guest/host/co-host flow through the HTTP API of the running app (14 checks). It needs the
phone and the computer on the same network and `adb` access (it reads the host token with `run-as`, which only
works for debug builds).

## 3. Manual checklist (before a release, and when playback or the add-ons change)

Use a real phone, not an emulator. Note the phone model and Android version in the release PR.

**Playback**
- [ ] Load a public playlist (*YouTube* tab → *Wklej link*, or open the playlist and press *Ustaw jako aktualną playlistę*). The first song starts without touching the screen.
- [ ] No advert appears in 5 songs. (If one does, note which and whether uBlock lists were updated.)
- [ ] A song that ends moves to the next one; skipping with ⏭ and ⏮ works.
- [ ] A video that cannot be embedded or is removed is skipped with a notice within ~30 s.
- [ ] After a fresh install the cookie banner disappears by itself and is not shown again.

**Song changes**
- [ ] With the screen **on** and with it **off**, the next song starts at once (no gap of seconds) when a song ends,
  when *Następny* is pressed, and when a song is swiped to play now.
- [ ] Lock the screen and let at least three songs change; the music never stops.
- [ ] Only one song is audible at any time (the spare page is silent). In `adb logcat -s PartyQueue`, `STARTUP` lines after
  a `swap` show a few tens of milliseconds.
- [ ] Changing the head of the queue (move, remove, play now, clear) never makes the wrong song play.

**Screen off and background**
- [ ] Lock the screen with music playing: sound continues for at least 10 minutes.
- [ ] A guest phone can still propose a song and see the queue while the host screen is off.
- [ ] Press BACK on the host: the app hides, music keeps playing; reopening shows the video, not a blank pane.
- [ ] Swipe the app away from recents: the notification and playback survive or stop cleanly (note which).

**Volume**
- [ ] The slider under the controls changes the media volume and follows the phone's volume keys; tapping the speaker mutes and restores.

**Fading**
- [ ] Press ⏭ while a song plays: the old song fades out over ~2 s as the next one fades in (no gap, no jump in loudness).
  Same for ⏮ and for swiping a row right. When a song ends by itself the next one starts at once, without a fade.
- [ ] Skip to a song that was not prepared (e.g. swipe a row far down the queue): the old song keeps playing until the new one is loaded, then they cross over.

**Queue list**
- [ ] Every row shows a thumbnail; titles stay on one line, and a title longer than the row scrolls slowly.

**Queue gestures**
- [ ] Hold a row for ~half a second: the phone vibrates, the row lifts and follows the finger; neighbours step aside; releasing keeps the new place.
- [ ] While holding a row near the top or bottom edge of the list the list scrolls; a song can be carried across a 100-song queue.
- [ ] Swipe a row right: the song plays now and leaves the queue. Swipe left: it is removed. A short, slow drag springs back.
- [ ] Leave the queue alone for ~45 s: only the **first** row slides right (green, play icon) then left (red, bin icon); the hint repeats at most every 3 minutes and never while you touch the list.
- [ ] A short tap on a song moves it to the top of the queue (a toast confirms; the first row ignores the tap).
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

**Guest page on a computer** (open the address in a desktop browser)
- [ ] The layout uses two columns (player on the left, tabs on the right) and scales to a wide window.
- [ ] As a host: Space pauses, N / P skip, ↑ ↓ select a song, Enter plays it, T moves it to the top, Delete removes it,
  Alt+↑/↓ moves it, R toggles repeat, / opens search, 1-5 switch tabs, D changes the theme, ? lists the shortcuts.
- [ ] Rows can be dragged with the mouse; double-click plays a song; buttons show tooltips with their shortcut.
- [ ] As a guest only the shortcuts that apply (search, tabs, theme, help) do anything.

**Guests**
- [ ] The QR code from the *Zaproś* tab (with a clear white border) opens the guest page on another phone on the same Wi-Fi; no sign-in step, just a name.
- [ ] *Udostępnij link* and *Kopiuj* work.
- [ ] A guest can search, paste a link, and propose; the host sees it under *Propozycje* and can approve or reject.
- [ ] Approved songs play before the rest of the playlist, in the order they were approved.
- [ ] *Poproś o uprawnienia hosta* → the host approves → the guest sees the host controls and can approve others.
- [ ] *Przyjmuj nowych gości* off blocks new joins; existing guests stay connected.
- [ ] A removed guest is logged out at once.

**Interface**
- [ ] The preview button next to ⏭ cycles: crossed-out screen (no preview, the default) → empty screen (small) → full screen.
- [ ] The title bar disappears after ~8 s without touching; the status-bar strip and a small handle stay; dragging down or tapping the handle brings it back.
- [ ] *Dodaj do kolejki* in the YouTube tab puts the song at the end of the queue.
- [ ] Light, dark and *auto* theme (sun/moon button in the header) all look right; the choice is remembered.
- [ ] The launcher icon (also the themed monochrome one on Android 13+) and the notification icon show the three bars.
- [ ] Tabs are *Kolejka · Propozycje · Zaproś · YouTube*; there is no *Dodaj* tab. The *Powtarzaj* switch and *Wyczyść* are in the queue header.
- [ ] The YouTube pane never goes under the status bar or the navigation bar (portrait and landscape).
- [ ] With nothing playing, the pane shows the "Party Queue" placeholder, not a blank or grey area.
- [ ] The YouTube logo and the "open the app" button are not shown in the pane.
- [ ] Restarting the app restores the queue (paused) and the guests.

## Reporting a defect

Include: phone model, Android version, app version (`versionName`), what you did, what you
expected, and `adb logcat -s PartyQueue` output around the failure.
