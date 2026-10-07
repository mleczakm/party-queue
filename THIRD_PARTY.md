# Third-party software

Party Queue is licensed under the **GNU AGPL-3.0** (see `LICENSE`). It combines or bundles the following
components, each under its own licence. Because two of the bundled add-ons are (A)GPL, the combined app is
distributed under AGPL-3.0 as well.

| Component | Use | Licence |
| --- | --- | --- |
| [GeckoView](https://mozilla.github.io/geckoview/) | Browser engine that plays YouTube | MPL-2.0 |
| [uBlock Origin](https://github.com/gorhill/uBlock) 1.75.0 | Ad blocking, bundled as a built-in add-on | GPL-3.0 |
| [Play YouTube Video In Background](https://github.com/LabinatorSolutions/play-youtube-video-in-background) 1.1.0 | Keeps playback going with the screen off | AGPL-3.0 |
| [Ktor](https://ktor.io/) (CIO server, WebSockets) | Local guest server | Apache-2.0 |
| [ZXing](https://github.com/zxing/zxing) | QR code generation | Apache-2.0 |
| [Jetpack Compose / AndroidX](https://developer.android.com/jetpack) | Host UI | Apache-2.0 |
| [Kotlin and kotlinx.coroutines](https://kotlinlang.org/) | Language and runtime | Apache-2.0 |

The two add-ons are **not stored in this repository**. `scripts/fetch-addons.sh` downloads them from
addons.mozilla.org, checks the SHA-256 recorded in `addons.lock` and unpacks them before each build.

YouTube is a trademark of Google LLC. This project is not affiliated with or endorsed by Google, Mozilla,
the uBlock Origin authors or any other third party. Blocking ads and playing videos outside the official
player may conflict with YouTube's Terms of Service; use it at your own risk.
