#!/usr/bin/env bash
# Downloads the bundled Firefox add-ons listed in addons.lock, verifies their SHA-256 and unpacks
# them into app/src/main/assets/extensions/<name>/. Existing, complete folders are left alone.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
dest="$root/app/src/main/assets/extensions"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

sha256() { if command -v sha256sum >/dev/null; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi; }

grep -v '^#' "$root/addons.lock" | while IFS='|' read -r name version url expected _licence; do
  [ -z "$name" ] && continue
  if [ -f "$dest/$name/manifest.json" ]; then
    echo "add-on $name $version: already present"
    continue
  fi
  echo "add-on $name $version: downloading"
  curl -fsSL "$url" -o "$tmp/$name.xpi"
  actual="$(sha256 "$tmp/$name.xpi")"
  if [ "$actual" != "$expected" ]; then
    echo "SHA-256 mismatch for $name: expected $expected, got $actual" >&2
    exit 1
  fi
  mkdir -p "$dest/$name"
  unzip -q -o "$tmp/$name.xpi" -d "$dest/$name"
  rm -rf "$dest/$name/META-INF"
done
