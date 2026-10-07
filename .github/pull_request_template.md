## What and why

## How it was checked

- [ ] `./gradlew lintDebug testDebugUnitTest assembleDebug` passes
- [ ] Added or updated unit tests for changed logic
- [ ] If playback, add-ons, the guest page or permissions changed: ran `scripts/smoke-test.sh` and the relevant items in `docs/QA.md` on a real phone
- [ ] `CHANGELOG.md` has an entry under *Unreleased* (user-visible changes)
- [ ] If the bridge add-on's scripts changed: bumped its `version` in `assets/extensions/bridge/manifest.json`
