#!/usr/bin/env python3
"""Report instrumented-test failures when CI artifacts cannot be fetched."""

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

from ci_report_failure import annotations


def reports(root):
    return sorted(root.glob("*/build/outputs/androidTest-results/connected/**/*.xml"))


def results(root):
    files = reports(root)
    if not files:
        yield "::notice title=Android tests::No instrumented test result XML was found."
        return
    for path in files:
        try:
            suite = ET.parse(path).getroot()
        except ET.ParseError as error:
            yield from annotations(str(error), "warning", "Invalid Android test report")
            continue
        tests = suite.get("tests", "?")
        failures = suite.get("failures", "?")
        errors = suite.get("errors", "?")
        yield f"::notice title=Android test summary::{path}: tests={tests} failures={failures} errors={errors}"
        for case in suite.iter("testcase"):
            for failure in list(case.findall("failure")) + list(case.findall("error")):
                detail = f"{case.get('classname', '')}.{case.get('name', '')}\n{failure.get('message', '')}\n{failure.text or ''}"
                yield from annotations(detail, "error", "Android test failure")
        # Some runners report an infrastructure failure outside a testcase.
        for failure in list(suite.findall("failure")) + list(suite.findall("error")):
            detail = f"{path}\n{failure.get('message', '')}\n{failure.text or ''}"
            yield from annotations(detail, "error", "Android test infrastructure failure")


if __name__ == "__main__":
    print("\n".join(results(Path(sys.argv[1]))))
