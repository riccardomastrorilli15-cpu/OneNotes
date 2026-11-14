#!/usr/bin/env bash
# Capture crash details before emulator-runner tears down the virtual device.
set -uo pipefail
adb logcat -c >/dev/null 2>&1 || true
./scripts/ci_gradle.sh --continue --no-daemon "$@"
status=$?
if [ "$status" -ne 0 ]; then
    log="${RUNNER_TEMP:-/tmp}/lume-android-logcat.log"
    adb logcat -d -v brief -s AndroidRuntime:E ActivityTaskManager:E ActivityManager:E > "$log" 2>&1 || true
    python3 scripts/ci_report_crashes.py "$log" || true
fi
exit "$status"
