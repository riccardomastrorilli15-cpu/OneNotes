#!/usr/bin/env bash
# Preserve Gradle's exit code; expose concise failure diagnostics as a check annotation.
set -uo pipefail
log="${RUNNER_TEMP:-/tmp}/lume-gradle-${GITHUB_JOB:-local}.log"
./gradlew --console=plain "$@" 2>&1 | tee "$log"
status=${PIPESTATUS[0]}
if [ "$status" -ne 0 ]; then
    python3 scripts/ci_report_failure.py "$log" || true
fi
exit "$status"
