#!/usr/bin/env python3
"""Expose only Lume-related emulator crashes from a failed instrumentation run."""

import sys
from pathlib import Path

from ci_report_failure import annotations


def diagnostics(text):
    lines = text.splitlines()
    blocks = []
    for index, line in enumerate(lines):
        if "FATAL EXCEPTION" not in line:
            continue
        end = next((i for i in range(index + 1, min(len(lines), index + 100)) if "FATAL EXCEPTION" in lines[i]), min(len(lines), index + 100))
        block = lines[index:end]
        if any("dev.lumenotes" in entry for entry in block[:12]):
            blocks.append("\n".join(block))
    if blocks:
        return "\n--- another crash ---\n".join(blocks)[-24000:]
    related = [line for line in lines if "dev.lumenotes" in line]
    return "\n".join(related[-50:]) if related else "No Lume crash or activity manager error found in logcat."


if __name__ == "__main__":
    log = Path(sys.argv[1]).read_text(errors="replace")
    print("\n".join(annotations(diagnostics(log), "warning", "Emulator logcat")))
