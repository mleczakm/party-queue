#!/usr/bin/env bash
# End-to-end check against a real phone running a DEBUG build of the app (needs adb access to read the host token).
#
#   scripts/smoke-test.sh <phone-ip> [adb-serial]
#
# It joins as a guest, searches, proposes, approves, promotes to co-host and skips a track,
# using only the HTTP API. Needs network access to YouTube from the phone.
set -euo pipefail

ip="${1:?usage: smoke-test.sh <phone-ip> [adb-serial]}"
serial="${2:-}"
adb="${ADB:-adb}"
if [ -n "$serial" ]; then adb="$adb -s $serial"; fi
base="http://$ip:8080"
pkg=pl.mleczki.partyqueue
pass=0; fail=0

prefs="$($adb shell "run-as $pkg cat shared_prefs/party.xml")"
host="$(grep -oE 'hostToken&quot;:&quot;[a-z0-9]+' <<<"$prefs" | sed 's/.*;//')"
if [ -z "$host" ]; then
  echo "Could not read tokens: is the debug app installed and started?" >&2
  exit 2
fi

req() { # method path token [json]
  curl -s -m 40 -X "$1" "$base$2" -H "Authorization: Bearer $3" ${4:+-d "$4"}
}
code() { curl -s -m 40 -o /dev/null -w '%{http_code}' -X "$1" "$base$2" -H "Authorization: Bearer $3" -d "${4:-{\}}"; }
jq_() { python3 -c "import sys,json; d=json.load(sys.stdin); print($1)"; }
check() { # description expected actual
  if [ "$2" = "$3" ]; then echo "  ok    $1"; pass=$((pass+1)); else echo "  FAIL  $1 (expected '$2', got '$3')"; fail=$((fail+1)); fi
}

echo "Smoke test against $base"
check "guest page is served" 200 "$(curl -s -o /dev/null -w '%{http_code}' "$base/")"
check "api rejects missing token" 401 "$(curl -s -o /dev/null -w '%{http_code}' "$base/api/state")"
check "join without a name is refused" 403 "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$base/api/join" -d '{"name":"  "}')"

joined="$(curl -s -m 10 -X POST "$base/api/join" -d '{"name":"Smoke"}')"
guest="$(jq_ "d['token']" <<<"$joined")"; gid="$(jq_ "d['id']" <<<"$joined")"
check "anyone who opens the page can join" GUEST "$(req GET /api/state "$guest" | jq_ "d['me']['role']")"

vid="$(req GET "/api/search?q=maanam%20boskie%20buenos" "$guest" | jq_ "d['results'][0]['vid']")"
check "search returns a video id" 11 "${#vid}"

check "guest cannot skip" 403 "$(code POST /api/player/next "$guest")"
check "guest can propose" 200 "$(code POST /api/propose "$guest" "{\"videoId\":\"$vid\"}")"
pid="$(req GET /api/state "$host" | jq_ "[p['id'] for p in d['proposals'] if p['vid']=='$vid'][0]")"
check "host approves the proposal" 200 "$(code POST "/api/proposals/$pid/approve" "$host")"
check "approved song is queued or playing" True "$(req GET /api/state "$host" | jq_ "any(t['vid']=='$vid' for t in d['queue']) or (d['now']['track'] or {}).get('vid')=='$vid'")"

req POST /api/hostrequest "$guest" '{}' >/dev/null
check "host request is visible to the host" True "$(req GET /api/state "$host" | jq_ "any(g['hostRequested'] for g in d['guests'])")"
check "host grants co-host" 200 "$(code POST "/api/guests/$gid/approve" "$host")"
check "co-host can skip" 200 "$(code POST /api/player/next "$guest")"

check "host removes the test guest" 200 "$(code POST "/api/guests/$gid/kick" "$host")"
check "removed guest loses access" 401 "$(curl -s -o /dev/null -w '%{http_code}' "$base/api/state" -H "Authorization: Bearer $guest")"

echo "$pass passed, $fail failed"
[ "$fail" -eq 0 ]
