#!/usr/bin/env python3
"""Emit a short GitHub Actions annotation from a failed Gradle log."""

import re
import sys
from pathlib import Path


def diagnostic(log):
    lines = log.splitlines()
    interesting = []
    for index, line in enumerate(lines):
        if re.search(r"(^e: |\berror:|\bFAILED\b|Exception:|\bERROR\b)", line, re.IGNORECASE):
            interesting.extend(lines[max(0, index - 1) : min(len(lines), index + 3)])
    marker = next((i for i, line in enumerate(lines) if line.startswith("FAILURE: Build failed")), None)
    summary = lines[marker : marker + 45] if marker is not None else lines[-60:]
    return ("\n".join(interesting[-100:] + ["--- Gradle summary ---"] + summary))[-20000:]


def escape(message):
    return message.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


if __name__ == "__main__":
    print("::error title=Gradle failure::" + escape(diagnostic(Path(sys.argv[1]).read_text(errors="replace"))))
