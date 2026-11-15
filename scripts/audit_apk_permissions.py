#!/usr/bin/env python3
"""Fail the test-release build for unexpected permissions or background components."""

import re
import sys
from pathlib import Path


BADGING_PACKAGE = re.compile(r"^package: name='([^']+)'", re.MULTILINE)
PERMISSION = re.compile(r"^uses-permission(?:-sdk(?:-\d+)?)?:\s+name='([^']+)'")


def audit(permissions, badging, manifest=None):
    package = BADGING_PACKAGE.search(badging)
    if not package:
        return ["Unable to read the APK package name from aapt badging"]
    if re.search(r"^application-debuggable\b", badging, re.MULTILINE):
        return ["Test release is debuggable"]
    allowed = {package.group(1) + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"}
    issues = []
    for raw in permissions.splitlines():
        line = raw.strip()
        if not line.startswith("uses-permission"):
            continue
        match = PERMISSION.match(line)
        if not match:
            issues.append("Unrecognized aapt permission entry: " + line)
        elif match.group(1) not in allowed:
            issues.append("Unexpected requested permission: " + match.group(1))
    if manifest is not None:
        if not re.search(r"^\s*E: manifest\b", manifest, re.MULTILINE) or not re.search(
            r"^\s*E: application\b", manifest, re.MULTILINE
        ):
            issues.append("Unable to read the APK manifest tree")
        for component in re.findall(r"^\s*E: (service|receiver)\s*\(", manifest, re.MULTILINE):
            issues.append("Unexpected manifest component: " + component)
    return issues


if __name__ == "__main__":
    issues = audit(Path(sys.argv[1]).read_text(), Path(sys.argv[2]).read_text(), Path(sys.argv[3]).read_text())
    for issue in issues:
        print(issue, file=sys.stderr)
    if issues:
        sys.exit(1)
    print("APK permissions audit passed")
