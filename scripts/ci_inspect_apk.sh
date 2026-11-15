#!/usr/bin/env bash
# Inspect a signed, minified test release; never publish the debug signing key.
set -euo pipefail
apk=${1:?APK path required}
report=${2:?Report path required}
tools="${ANDROID_HOME:?ANDROID_HOME is required}/build-tools/36.0.0"
tmp=$(mktemp -d)
trap 'rm -rf -- "$tmp"' EXIT
"$tools/aapt" dump permissions "$apk" > "$tmp/permissions.txt"
"$tools/aapt" dump badging "$apk" > "$tmp/badging.txt"
"$tools/aapt" dump xmltree "$apk" AndroidManifest.xml > "$tmp/manifest.txt"
"$tools/apksigner" verify --verbose --print-certs "$apk" > "$tmp/signature.txt"
"$tools/zipalign" -c -P 16 4 "$apk" > "$tmp/alignment.txt"
set +e
python3 scripts/audit_apk_permissions.py "$tmp/permissions.txt" "$tmp/badging.txt" "$tmp/manifest.txt" > "$tmp/audit.txt" 2>&1
audit_status=$?
set -e
{
    printf 'APK: %s\nBytes: ' "$(basename "$apk")"
    wc -c < "$apk"
    printf 'SHA-256: '
    sha256sum "$apk" | cut -d ' ' -f 1
    printf '\n=== aapt dump permissions ===\n'
    cat "$tmp/permissions.txt"
    printf '\n=== aapt dump badging ===\n'
    cat "$tmp/badging.txt"
    printf '\n=== aapt dump xmltree AndroidManifest.xml ===\n'
    cat "$tmp/manifest.txt"
    printf '\n=== apksigner verify ===\n'
    cat "$tmp/signature.txt"
    printf '\n=== zipalign verification ===\n'
    cat "$tmp/alignment.txt"
    printf '\n=== permission audit ===\n'
    cat "$tmp/audit.txt"
} > "$report"
python3 - "$report" <<'PY'
import sys
from pathlib import Path
sys.path.insert(0, 'scripts')
from ci_report_failure import annotations
for item in annotations(Path(sys.argv[1]).read_text(errors='replace'), 'notice', 'Test release APK inspection'):
    print(item)
PY
exit "$audit_status"
