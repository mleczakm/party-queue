# Contributing

## Workflow

1. Branch from `main` (`feature/<topic>`, `fix/<topic>`); keep changes focused.
2. Write the test first or alongside the change (`app/src/test`).
3. Run `./gradlew lintDebug testDebugUnitTest assembleDebug`; for runtime changes also `scripts/smoke-test.sh`
   and the relevant part of [`docs/QA.md`](docs/QA.md).
4. Add a line to `CHANGELOG.md` under *Unreleased* for anything a user would notice.
5. Open a pull request; CI must be green. Use [Conventional Commits](https://www.conventionalcommits.org/)
   (`feat:`, `fix:`, `docs:`, `test:`, `chore:`) so history reads as a changelog.

## Versioning and releases

Semantic Versioning, stored once in `version.properties`. `versionCode` is derived as
`major*10000 + minor*100 + patch`. Rules of thumb: a guest- or host-visible behaviour change is a minor bump,
a fix is a patch, and an incompatible change to the stored data or the HTTP API is a major bump.

```bash
scripts/release.sh 0.2.0        # bumps the version, dates the changelog, commits, tags v0.2.0
git push origin main --follow-tags
```

Pushing the tag runs `.github/workflows/release.yml`, which re-checks the tag against `version.properties`
and the changelog, runs lint and tests, and publishes a GitHub release with the APK and its checksum.

## Things that bite

- Bump `version` in `app/src/main/assets/extensions/bridge/manifest.json` whenever the bridge add-on's scripts
  change; GeckoView keeps the old copy otherwise.
- `PartyApp.onCreate` runs in every Gecko child process. Create the runtime only in the main process.
- Do not commit the third-party add-ons; edit `addons.lock` (URL and SHA-256) to update them.
- Never log or commit the host token; it lives only in the app's private storage.
