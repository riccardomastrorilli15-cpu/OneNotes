#!/usr/bin/env python3
"""Expose the earliest useful Gradle failures in bounded GitHub annotations."""

import re
import sys
from pathlib import Path


LIMIT = 3000  # GitHub check annotations truncate message bodies at 4096 characters.
PATTERN = re.compile(r"(^e: |\berror:|\bFAILED\b|Exception:|\bERROR\b)", re.IGNORECASE)


def diagnostic(log):
    lines = log.splitlines()
    interesting = []
    seen = set()
    for index, line in enumerate(lines):
        if not PATTERN.search(line):
            continue
        # A missing supertype can generate hundreds of location-specific copies.
        missing = re.search(r"Cannot access '([^']+)'", line)
        key = ("missing", missing.group(1)) if missing else ("line", line.strip())
        if key in seen:
            continue
        seen.add(key)
        interesting.append(line)
        if not line.lstrip().startswith("e: "):
            interesting.extend(lines[index + 1 : min(len(lines), index + 3)])
    marker = next((i for i, line in enumerate(lines) if line.startswith("FAILURE: Build failed")), None)
    summary = lines[marker : marker + 45] if marker is not None else lines[-30:]
    return "\n".join(interesting[:200] + ["--- Gradle summary ---"] + summary)


def escape(message):
    return message.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def chunks(text, limit=LIMIT):
    return [text[i : i + limit] for i in range(0, len(text), limit)] or [""]


def annotations(text, level="error", title="Gradle failure"):
    parts = chunks(text)
    for index, part in enumerate(parts, 1):
        yield f"::{level} title={title} {index}/{len(parts)}::{escape(part)}"


if __name__ == "__main__":
    print("\n".join(annotations(diagnostic(Path(sys.argv[1]).read_text(errors="replace")))))
