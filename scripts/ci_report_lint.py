#!/usr/bin/env python3
"""Expose lint errors from CI reports without requiring artifact downloads."""

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

from ci_report_failure import annotations


def diagnostics(root):
    errors = []
    for path in sorted(root.glob("*/build/reports/lint-results*.xml")):
        try:
            report = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        for issue in report.iter("issue"):
            if issue.get("severity") not in {"Error", "Fatal"}:
                continue
            location = issue.find("location")
            where = f"{location.get('file', '')}:{location.get('line', '')}" if location is not None else str(path)
            errors.append(f"{issue.get('id', '?')} at {where}: {issue.get('message', '')}")
    if errors:
        return "\n".join(errors)
    text_reports = sorted(root.glob("*/build/intermediates/lint_intermediate_text_report/**/lint-results*.txt"))
    return "\n".join(path.read_text(errors="replace") for path in text_reports)[:20000] or "No lint report found."


if __name__ == "__main__":
    print("\n".join(annotations(diagnostics(Path(sys.argv[1])), "warning", "Lint report")))
