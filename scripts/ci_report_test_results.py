#!/usr/bin/env python3
"""Report instrumented results and reject missing, incomplete or skipped suites in CI."""

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

from ci_report_failure import annotations


# One emulator per CI job. Update these minima deliberately when tests are removed.
MIN_TESTS = {"core-database": 2, "feature-drawing": 7, "app": 7}


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
        except (ET.ParseError, OSError) as error:
            yield from annotations(str(error), "warning", "Invalid Android test report")
            continue
        tests = suite.get("tests", "?")
        failures = suite.get("failures", "?")
        errors = suite.get("errors", "?")
        cases = list(suite.iter("testcase"))
        skipped = suite.get("skipped", "0")
        skipped_cases = sum(case.find("skipped") is not None for case in cases)
        yield (
            f"::notice title=Android test summary::{path}: tests={tests} failures={failures} "
            f"errors={errors} skipped={skipped} skipped_cases={skipped_cases} cases={len(cases)}"
        )
        for case in cases:
            for failure in list(case.findall("failure")) + list(case.findall("error")):
                detail = f"{case.get('classname', '')}.{case.get('name', '')}\n{failure.get('message', '')}\n{failure.text or ''}"
                yield from annotations(detail, "error", "Android test failure")
        # Some runners report an infrastructure failure outside a testcase.
        for failure in list(suite.findall("failure")) + list(suite.findall("error")):
            detail = f"{path}\n{failure.get('message', '')}\n{failure.text or ''}"
            yield from annotations(detail, "error", "Android test infrastructure failure")


def validation_errors(root, minimums=MIN_TESTS):
    """Check every expected module and every device report, not just Gradle's exit code."""
    files = reports(root)
    for module, minimum in minimums.items():
        module_files = [path for path in files if path.relative_to(root).parts[0] == module]
        if not module_files:
            yield f"{module}: no instrumented test XML found"
            continue
        for path in module_files:
            try:
                suite = ET.parse(path).getroot()
                counts = {name: int(suite.attrib[name]) for name in ("tests", "failures", "errors")}
                skipped = int(suite.get("skipped", "0"))
            except (ET.ParseError, OSError, KeyError, ValueError) as error:
                yield f"{path}: invalid test report ({error})"
                continue
            cases = list(suite.iter("testcase"))
            skipped_cases = sum(case.find("skipped") is not None for case in cases)
            if suite.tag != "testsuite" or any(count < 0 for count in (*counts.values(), skipped)):
                yield f"{path}: invalid suite or negative count"
            if counts["tests"] < minimum:
                yield f"{path}: expected at least {minimum} tests, found {counts['tests']}"
            if len(cases) != counts["tests"]:
                yield f"{path}: declared {counts['tests']} tests but found {len(cases)} test cases"
            failure_elements = next(suite.iter("failure"), None) is not None or next(suite.iter("error"), None) is not None
            if counts["failures"] or counts["errors"] or failure_elements:
                yield f"{path}: failures={counts['failures']} errors={counts['errors']} or failure elements present"
            if skipped or skipped_cases:
                yield f"{path}: skipped={skipped}, skipped cases={skipped_cases}"


def task_diagnostics(log_path):
    """Surface task states when a successful Gradle command produced incomplete reports."""
    try:
        lines = log_path.read_text(errors="replace").splitlines()
    except OSError as error:
        return f"Gradle log unavailable: {error}"
    pattern = re.compile(r"(?:app|core-database|feature-drawing):connectedDebugAndroidTest\b|no tests found|no connected devices", re.IGNORECASE)
    matches = [line for line in lines if pattern.search(line)]
    return "\n".join(matches[-50:]) if matches else "No connected test task states found in the Gradle log."


if __name__ == "__main__":
    root = Path(sys.argv[1])
    print("\n".join(results(root)))
    problems = list(validation_errors(root))
    if problems:
        for problem in problems:
            print("\n".join(annotations(problem, "error", "Incomplete Android test results")))
        if len(sys.argv) > 2:
            print("\n".join(annotations(task_diagnostics(Path(sys.argv[2])), "notice", "Gradle test task states")))
        sys.exit(1)
