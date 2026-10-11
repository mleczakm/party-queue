#!/usr/bin/env bash
# Draws a QR code as SVG and PNG (ZXing from the Gradle cache, run with the JDK's single-file launcher).
#
#   scripts/make-qr.sh <text-or-url> <output-basename>      e.g.  scripts/make-qr.sh https://example.org/ branding/qr-example
#
# Used for branding/qr-download.* (the newest APK) and branding/qr-site.* (the website); the same files are on the website.
set -euo pipefail

text="${1:?usage: make-qr.sh <text-or-url> <output-basename>}"
out="${2:?usage: make-qr.sh <text-or-url> <output-basename>}"
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
core="$(find "${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1/com.google.zxing" -name 'core-*.jar' ! -name '*sources*' 2>/dev/null | sort | tail -1)"
[ -n "$core" ] || { echo "ZXing not found in the Gradle cache: run ./gradlew assembleDebug once" >&2; exit 1; }
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp "$core" "$here/QrMake.java" "$text" "$out"
