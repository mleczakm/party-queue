#!/usr/bin/env bash
# Makes CI sign its APKs with the same key as your local builds, so a release downloaded from GitHub installs over
# the app built on your computer (and the other way round) without uninstalling it first.
#
#   scripts/set-signing-secret.sh [path/to/keystore]      (default: ~/.android/debug.keystore)
#
# It stores the keystore, base64-encoded, as the repository secret SIGNING_KEYSTORE_B64 (needs `gh` and write access).
# Secrets are write-only on GitHub: nobody, including you, can read them back, and they are not given to pull
# requests from forks (CI then falls back to a throw-away debug key). Local builds need no setup: Android's Gradle
# plugin already signs with ~/.android/debug.keystore.
set -euo pipefail

keystore="${1:-$HOME/.android/debug.keystore}"
[ -f "$keystore" ] || { echo "No keystore at $keystore (build the app once, or pass the path)" >&2; exit 1; }
command -v gh >/dev/null || { echo "Install the GitHub CLI (gh) first" >&2; exit 1; }

echo "Publishing $keystore as the secret SIGNING_KEYSTORE_B64 of $(gh repo view --json nameWithOwner -q .nameWithOwner)"
base64 < "$keystore" | tr -d '\n' | gh secret set SIGNING_KEYSTORE_B64
echo "Done. The next CI run and the next release are signed with this key."
