#!/usr/bin/env bash
# End-to-end test on an Android emulator: real downloads through the app's share intent,
# entirely on-device (no server). Results: files in Download/AKSdown, history.json, ffprobe, logcat.
set -u
APK="$1"; OUT="$2"; mkdir -p "$OUT"
PKG=com.aksdown.mobile.debug
ACT=$PKG/com.aksdown.mobile.MainActivity
REPORT="$OUT/e2e-report.txt"
log(){ echo "$*" | tee -a "$REPORT"; }
adb wait-for-device
adb shell getprop ro.build.version.release | xargs -I{} echo "Android {} emulator, ABI $(adb shell getprop ro.product.cpu.abi)" | tee "$REPORT"
adb install -r -g "$APK" | tee -a "$REPORT"
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null
adb logcat -c
adb shell am start -W -n $ACT >/dev/null; sleep 8
adb exec-out screencap -p > "$OUT/01-home.png"

run_test () {
  local name="$1" url="$2" fmt="$3" timeout="$4"
  log ""; log "=== $name ($fmt) $url"
  local before; before=$(adb shell ls /sdcard/Download/AKSdown/ 2>/dev/null | wc -l)
  adb shell am start -n $ACT -a android.intent.action.SEND -t text/plain \
     --es android.intent.extra.TEXT "'$url'" --es aks_test_format $fmt >/dev/null
  local t=0 status=""
  while [ $t -lt $timeout ]; do
    sleep 10; t=$((t+10))
    [ $t -eq 30 ] && adb exec-out screencap -p > "$OUT/progress-$name.png"
    status=$(adb shell run-as $PKG cat files/history.json 2>/dev/null | python3 -c "
import json,sys
try: items=json.load(sys.stdin)
except Exception: print('none'); sys.exit()
m=[i for i in items if i['url'].rstrip('/')=='''$url'''.rstrip('/') and i['format']=='$fmt']
print(m[0]['status'] if m else 'none')")
    [ "$status" = "DONE" ] || [ "$status" = "FAILED" ] || [ "$status" = "CANCELED" ] && break
  done
  adb exec-out screencap -p > "$OUT/result-$name.png"
  adb shell run-as $PKG cat files/history.json > "$OUT/history.json" 2>/dev/null
  python3 - "$OUT/history.json" "$url" "$fmt" <<'PY' | tee -a "$REPORT"
import json,sys
items=json.load(open(sys.argv[1])); url=sys.argv[2].rstrip('/'); fmt=sys.argv[3]
m=[i for i in items if i['url'].rstrip('/')==url and i['format']==fmt]
if not m: print("   status: not recorded (still running or not started)")
else:
    i=m[0]; print(f"   status: {i['status']}  file: {i.get('fileName')}  size: {i.get('sizeBytes')}  resolution: {i.get('resolution')}")
    if i.get('errorKey'): print(f"   error: {i['errorKey']}\n   detail: {(i.get('errorDetail') or '')[-700:]}")
PY
  log "   elapsed: ${t}s"
  if [ "$status" = "DONE" ]; then
    local f; f=$(adb shell ls -t /sdcard/Download/AKSdown/ | head -1 | tr -d '\r')
    adb pull "/sdcard/Download/AKSdown/$f" "$OUT/$name.${fmt,,}" >/dev/null 2>&1
    log "   saved in public folder: /sdcard/Download/AKSdown/$f"
    ffprobe -v error -show_entries format=format_name,duration,bit_rate:stream=codec_type,codec_name,width,height,avg_frame_rate,bit_rate \
      -of compact "$OUT/$name.${fmt,,}" 2>&1 | sed 's/^/   ffprobe: /' | tee -a "$REPORT"
    # Visible to other apps through MediaStore (Gallery / music players / My Files)
    adb shell content query --uri content://media/external/file --projection _display_name:relative_path:mime_type \
      --where "relative_path=\'Download/AKSdown/\'" 2>/dev/null | tail -3 | sed 's/^/   mediastore: /' | tee -a "$REPORT"
    rm -f "$OUT/$name.${fmt,,}"   # keep the artifact small
    return 0
  fi
  return 1
}

PASS=0; FAIL=0
t(){ if run_test "$@"; then PASS=$((PASS+1)); else FAIL=$((FAIL+1)); fi; }
# YouTube and Vimeo often block datacenter IPs (GitHub runners); they are reported but not required.
t yt-mp4        "https://www.youtube.com/watch?v=jNQXAC9IVRw" MP4 420
t peertube-mp4  "https://framatube.org/videos/watch/9c9de5e8-0a1e-484a-b099-e80766180a6d" MP4 900
t peertube-mp3  "https://framatube.org/videos/watch/9c9de5e8-0a1e-484a-b099-e80766180a6d" MP3 900
t reddit-mp4    "https://www.reddit.com/r/videos/comments/6rrwyj/that_small_heart_attack/" MP4 600
t archive-mp4   "https://archive.org/details/Election_Ads/Commercial-Nixon1960ElectionAdToughonDefense.mpg" MP4 900

# Error handling: an unsupported link must be reported, never "succeed".
log ""; log "=== invalid/unsupported link"
adb shell am start -n $ACT -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "'https://example.com/not-a-video'" --es aks_test_format MP4 >/dev/null
sleep 45
adb shell run-as $PKG cat files/history.json | python3 -c "
import json,sys
m=[i for i in json.load(sys.stdin) if 'example.com' in i['url']]
print('   status:', m[0]['status'] if m else 'none', '| error:', m[0].get('errorKey') if m else '')" | tee -a "$REPORT"
adb exec-out screencap -p > "$OUT/result-unsupported.png"

log ""; log "SUMMARY: $PASS passed, $FAIL failed (network-dependent downloads)"
adb logcat -d > "$OUT/logcat.txt"
grep -E "AKSEngine|AndroidRuntime|FATAL" "$OUT/logcat.txt" | tail -50 > "$OUT/logcat-app.txt"
grep -q "FATAL EXCEPTION" "$OUT/logcat.txt" && { log "CRASH DETECTED"; exit 1; }
exit 0
