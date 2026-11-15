#!/usr/bin/env bash
# Launch the signed R8 release on the disposable API 29 emulator after debug tests.
set -euo pipefail
apk=${1:?Signed APK path required}
package=dev.lumenotes.app
activity="$package/.MainActivity"
log="${RUNNER_TEMP:-/tmp}/lume-release-logcat.log"
adb install -r "$apk"
adb shell am force-stop "$package"
adb logcat -c
start=$(adb shell am start -W -n "$activity" | tr -d '\r')
printf '%s\n' "$start"
grep -q 'Status: ok' <<< "$start"
# Give a startup crash or failed Hilt/Room initialization time to surface.
sleep 5
adb shell pidof "$package"
adb shell dumpsys activity activities | tr -d '\r' | grep -E "mResumedActivity.*$package/\.MainActivity"
adb logcat -d -v brief -s AndroidRuntime:E > "$log"
if grep -F "Process: $package," "$log"; then
    cat "$log" >&2
    exit 1
fi
printf 'Signed release launched without an observed startup crash on API 29.\n'
