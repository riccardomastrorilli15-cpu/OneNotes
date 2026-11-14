#!/usr/bin/env python3
"""Make generated, non-secret Room schemas inspectable in GitHub check annotations."""

import base64
import sys
from pathlib import Path


root = Path(sys.argv[1])
for schema in sorted(root.rglob("*.json")):
    relative = schema.relative_to(root)
    encoded = base64.b64encode(schema.read_bytes()).decode("ascii")
    if len(encoded) <= 48_000:
        print(f"::notice title=Room schema {relative}::{encoded}")
    else:
        print(f"::warning title=Room schema omitted::{relative} exceeds the annotation size limit")
