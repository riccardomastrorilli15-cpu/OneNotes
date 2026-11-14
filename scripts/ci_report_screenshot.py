#!/usr/bin/env python3
"""Report one opt-in emulator-test screenshot, containing synthetic CI data only."""

import base64
import binascii
import re
import sys
from pathlib import Path

from ci_report_failure import annotations

SCREEN = re.compile(r"dev\.lumenotes SCREEN (\d+) (\d+)/(\d+) ([A-Za-z0-9+/=]+)$")
MAX_PARTS = 60
MAX_ENCODED = 100_000


def screenshot(log):
    captures = {}
    for line in log.splitlines():
        match = SCREEN.search(line)
        if not match:
            continue
        capture_id, index, total, data = match.groups()
        index, total = int(index), int(total)
        if not 1 <= index <= total <= MAX_PARTS:
            continue
        capture = captures.setdefault(capture_id, {"total": total, "parts": {}})
        if capture["total"] == total:
            capture["parts"][index] = data
    for capture in reversed(list(captures.values())):
        total, parts = capture["total"], capture["parts"]
        if len(parts) != total:
            continue
        encoded = "".join(parts[index] for index in range(1, total + 1))
        if len(encoded) > MAX_ENCODED:
            continue
        try:
            decoded = base64.b64decode(encoded, validate=True)
        except binascii.Error:
            continue
        if decoded.startswith(b"\xff\xd8") and decoded.endswith(b"\xff\xd9"):
            return encoded
    return None


if __name__ == "__main__":
    text = Path(sys.argv[1]).read_text(errors="replace")
    encoded = screenshot(text)
    if encoded:
        print("\n".join(annotations(encoded, "notice", "CI screenshot JPEG synthetic data")))
