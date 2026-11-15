#!/usr/bin/env bash
# emulator-runner executes each script line separately, so keep the control flow here.
set -euo pipefail
api=${1:?API level required}
./scripts/ci_instrumented.sh -Pandroid.testInstrumentationRunnerArguments.captureScreenshots=1 \
    :core-database:connectedDebugAndroidTest :feature-drawing:connectedDebugAndroidTest :app:connectedDebugAndroidTest
if [ "$api" = '29' ]; then
    ./scripts/ci_gradle.sh --no-daemon -Plume.signTestRelease=true :app:assembleRelease
    ./scripts/ci_release_smoke.sh app/build/outputs/apk/release/app-release.apk
fi
