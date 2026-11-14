#!/usr/bin/env bash
# Capture crash details before emulator-runner tears down the virtual device.
set -uo pipefail
adb logcat -c >/dev/null 2>&1 || true
# ActivityScenario's verbose lifecycle matching explains timeouts without a crash.
adb shell setprop log.tag.ActivityScenario VERBOSE >/dev/null 2>&1 || true
# A previous run's reports must not make a skipped task look successful.
if ! rm -rf -- core-database/build/outputs/androidTest-results/connected \
    feature-drawing/build/outputs/androidTest-results/connected \
    app/build/outputs/androidTest-results/connected; then
    echo 'Unable to clear old Android test reports' >&2
    exit 1
fi
# All three suites share one emulator. Avoid running modules' instrumentation in parallel.
./scripts/ci_gradle.sh --continue --no-daemon --no-parallel "$@"
status=$?
if [ "$status" -ne 0 ]; then
    log="${RUNNER_TEMP:-/tmp}/lume-android-logcat.log"
    adb logcat -d -v brief -s AndroidRuntime:E ActivityTaskManager:I ActivityManager:I ActivityScenario:V LumeUi:I LumeScreen:I > "$log" 2>&1 || true
    python3 scripts/ci_report_crashes.py "$log" || true
    python3 scripts/ci_report_screenshot.py "$log" || true
fi
exit "$status"
