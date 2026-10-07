#!/usr/bin/env bash
# Prepares a release: bumps version.properties, dates the CHANGELOG, commits and tags. Does not push.
#
#   scripts/release.sh 0.2.0
set -euo pipefail

new="${1:?usage: release.sh <major.minor.patch>}"
[[ "$new" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "Version must look like 1.2.3" >&2; exit 1; }
cd "$(dirname "${BASH_SOURCE[0]}")/.."

[ -z "$(git status --porcelain)" ] || { echo "Working tree is not clean" >&2; exit 1; }
[ "$(git rev-parse --abbrev-ref HEAD)" = main ] || { echo "Release from main" >&2; exit 1; }
git rev-parse "v$new" >/dev/null 2>&1 && { echo "Tag v$new already exists" >&2; exit 1; }
grep -q '^## \[Unreleased\]' CHANGELOG.md || { echo "CHANGELOG.md has no [Unreleased] section" >&2; exit 1; }

old="$(sed -n 's/^versionName=//p' version.properties)"
echo "Releasing $old -> $new"
printf 'versionName=%s\n' "$new" > version.properties

today="$(date +%Y-%m-%d)"
python3 - "$new" "$old" "$today" <<'PY'
import re, sys
new, old, today = sys.argv[1:4]
s = open("CHANGELOG.md").read()
s = s.replace("## [Unreleased]\n", f"## [Unreleased]\n\n## [{new}] - {today}\n", 1)
s = re.sub(r"^\[Unreleased\]:.*$", f"[Unreleased]: https://github.com/mleczakm/party-queue/compare/v{new}...HEAD", s, flags=re.M)
link = f"[{new}]: https://github.com/mleczakm/party-queue/compare/v{old}...v{new}"
s = s.replace(f"[{old}]:", f"{link}\n[{old}]:", 1)
open("CHANGELOG.md", "w").write(s)
PY

git add version.properties CHANGELOG.md
git commit -m "chore(release): v$new"
git tag -a "v$new" -m "Party Queue $new"
echo "Done. Review, then: git push origin main --follow-tags"
