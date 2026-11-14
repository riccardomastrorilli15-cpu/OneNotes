#!/usr/bin/env python3
"""Expose generated, non-secret Room schemas as bounded base64 annotations."""

import base64
import sys
from pathlib import Path

from ci_report_failure import chunks


def annotations(root):
    for schema in sorted(root.rglob("*.json")):
        relative = schema.relative_to(root)
        encoded = base64.b64encode(schema.read_bytes()).decode("ascii")
        parts = chunks(encoded)
        for index, part in enumerate(parts, 1):
            yield f"::notice title=Room schema {relative} {index}/{len(parts)}::{part}"


if __name__ == "__main__":
    print("\n".join(annotations(Path(sys.argv[1]))))
