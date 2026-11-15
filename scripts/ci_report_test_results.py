#!/usr/bin/env python3
"""Report instrumented results and reject missing, incomplete or skipped suites in CI."""

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

from ci_report_failure import annotations


# One emulator per CI job. Update these minima deliberately when tests are removed.
MIN_TESTS = {"core-database": 3, "feature-drawing": 9, "app": 18}


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
        child_tags = ",".join(sorted({child.tag for child in suite}))
        yield (
            f"::notice title=Android test summary::{path}: root={suite.tag} children={child_tags} "
            f"tests={tests} failures={failures} errors={errors} skipped={skipped} "
            f"skipped_cases={skipped_cases} cases={len(cases)}"
        )
        for case in cases:
            for failure in list(case.findall("failure")) + list(case.findall("error")):
                detail = f"{case.get('classname', '')}.{case.get('name', '')}\n{failure.get('message', '')}\n{failure.text or ''}"
                yield from annotations(detail, "error", "Android test failure")
        # Some runners report an infrastructure failure outside a testcase.
        for parent in suite.iter():
            if parent.tag not in ("testsuite", "testsuites"):
                continue
            for failure in list(parent.findall("failure")) + list(parent.findall("error")):
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
            if suite.tag not in ("testsuite", "testsuites"):
                yield f"{path}: unsupported report root {suite.tag!r}"
            if any(count < 0 for count in (*counts.values(), skipped)):
                yield f"{path}: negative count"
            if counts["tests"] < minimum:
                yield f"{path}: expected at least {minimum} tests, found {counts['tests']}"
            if len(cases) != counts["tests"]:
                yield f"{path}: declared {counts['tests']} tests but found {len(cases)} test cases"
            failure_elements = next(suite.iter("failure"), None) is not None or next(suite.iter("error"), None) is not None
            if counts["failures"] or counts["errors"] or failure_elements:
                yield f"{path}: failures={counts['failures']} errors={counts['errors']} or failure elements present"
            if skipped or skipped_cases:
                yield f"{path}: skipped={skipped}, skipped cases={skipped_cases}"
            if suite.tag == "testsuite" and suite.find(".//testsuite") is not None:
                yield f"{path}: nested testsuites are not supported"
            if suite.tag == "testsuites":
                children = suite.findall("testsuite")
                if not children or sum(len(list(child.iter("testcase"))) for child in children) != len(cases):
                    yield f"{path}: aggregate must contain test cases inside direct testsuite children"
                for child in children:
                    if child.find(".//testsuite") is not None:
                        yield f"{path}: nested testsuites are not supported"
                    for name in ("tests", "failures", "errors", "skipped"):
                        if name not in child.attrib:
                            continue
                        try:
                            value = int(child.attrib[name])
                        except ValueError:
                            yield f"{path}: invalid child testsuite {name} count"
                            continue
                        if value < 0 or (name != "tests" and value != 0):
                            yield f"{path}: child testsuite {name}={value}"
                        if name == "tests" and value != len(list(child.iter("testcase"))):
                            yield f"{path}: child testsuite count does not match its test cases"


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
