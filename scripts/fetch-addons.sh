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

# --- local patch of "Play YouTube Video in Background" (AGPL-3.0; see THIRD_PARTY.md) ---------------------------------
# Its recovery loop resumes every pause it did not see a person cause, which undoes the host's pause button after ~15 s.
# Our bridge publishes a deliberate pause as the DOM attribute data-pq-host-paused; the patched loop leaves it alone.
bg="$dest/bgplay/play-youtube-video-in-background.js"
if [ -f "$bg" ] && ! grep -q 'data-pq-host-paused' "$bg"; then
  python3 - "$bg" <<'PY'
import sys
path = sys.argv[1]
s = open(path).read()
anchor = "function checkPlayback() {\n"
if anchor not in s:
    sys.exit("bgplay: checkPlayback() not found; the add-on changed, update the patch in scripts/fetch-addons.sh")
patch = (anchor +
         "            // Party Queue: the app paused on purpose (set by the bridge add-on); never resume it.\n"
         "            if (document.documentElement.hasAttribute('data-pq-host-paused')) {\n"
         "                consecutivePauses = 0;\n"
         "                return;\n"
         "            }\n")
open(path, "w").write(s.replace(anchor, patch, 1))
# A new version number makes Gecko load the patched copy instead of a cached one on devices that had the original.
mpath = path.replace("play-youtube-video-in-background.js", "manifest.json")
m = open(mpath).read()
open(mpath, "w").write(m.replace('"version": "1.1.0"', '"version": "1.1.0.1"', 1))
print("bgplay: patched to respect the host's pause")
PY
fi
