import base64
import importlib.util
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts"))


def load(name):
    spec = importlib.util.spec_from_file_location(name, ROOT / "scripts" / (name + ".py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


failure = load("ci_report_failure")
schemas = load("ci_report_schemas")
tests = load("ci_report_test_results")
crashes = load("ci_report_crashes")
screenshots = load("ci_report_screenshot")
lint = load("ci_report_lint")


def write_instrumented_suite(root, module, xml):
    path = root / module / "build/outputs/androidTest-results/connected/debug/TEST-device.xml"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(xml)
    return path


class ReportingTest(unittest.TestCase):
    def test_first_compiler_error_is_not_lost_in_repeated_missing_types(self):
        log = "e: Main.kt:1:1 unresolved reference 'realError'\n" + "\n".join(
            f"e: Main.kt:{i}:1 Cannot access 'androidx.core.Missing' which is a supertype" for i in range(500)
        ) + "\nFAILURE: Build failed\n* What went wrong:\nCompilation error"
        result = failure.diagnostic(log)
        self.assertIn("realError", result)
        self.assertIn("Compilation error", result)
        self.assertEqual(1, result.count("androidx.core.Missing"))
        self.assertTrue(all(len(part) <= 3000 for part in failure.chunks(result)))

    def test_large_schema_is_chunked_and_losslessly_reconstructed(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            schema = root / "dev.example.Db" / "1.json"
            schema.parent.mkdir()
            original = b'{"schema":"' + b'x' * 10000 + b'"}'
            schema.write_bytes(original)
            entries = list(schemas.annotations(root))
            self.assertGreater(len(entries), 1)
            encoded = ''.join(entry.split('::', 2)[2] for entry in entries)
            self.assertTrue(all(len(entry.split('::', 2)[2]) <= 3000 for entry in entries))
            self.assertEqual(original, base64.b64decode(encoded))

    def test_instrumented_failure_report_extracts_stack_trace(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            xml = root / "feature-drawing/build/outputs/androidTest-results/connected/debug/TEST-test.xml"
            xml.parent.mkdir(parents=True)
            xml.write_text('<testsuite tests="1" failures="1" errors="0"><testcase classname="Ink" name="roundTrip"><failure message="bad">trace details</failure></testcase></testsuite>')
            result = '\n'.join(tests.results(root))
            self.assertIn("tests=1 failures=1 errors=0", result)
            self.assertIn("Ink.roundTrip", result)
            self.assertIn("trace details", result)

    def test_instrumented_validation_requires_every_expected_suite(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            suite = '<testsuite tests="1" failures="0" errors="0"><testcase name="one"/></testsuite>'
            write_instrumented_suite(root, "app", suite)
            errors = list(tests.validation_errors(root, {"app": 1, "core-database": 1}))
            self.assertEqual(["core-database: no instrumented test XML found"], errors)

    def test_instrumented_validation_rejects_incomplete_failed_or_skipped_results(self):
        valid = '<testsuite tests="2" failures="0" errors="0" skipped="0"><testcase name="a"/><testcase name="b"/></testsuite>'
        variants = {
            "too few tests": valid.replace('tests="2"', 'tests="1"').replace('<testcase name="b"/>', ''),
            "missing testcase": valid.replace('<testcase name="b"/>', ''),
            "skipped attribute": valid.replace('skipped="0"', 'skipped="1"'),
            "skipped testcase": valid.replace('<testcase name="a"/>', '<testcase name="a"><skipped/></testcase>'),
            "failure count": valid.replace('failures="0"', 'failures="1"'),
            "failure element": valid.replace('<testcase name="a"/>', '<testcase name="a"><failure/></testcase>'),
            "missing count": valid.replace('errors="0"', ''),
            "invalid count": valid.replace('tests="2"', 'tests="?"'),
            "negative count": valid.replace('errors="0"', 'errors="-1"'),
            "invalid xml": '<testsuite>',
        }
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = write_instrumented_suite(root, "app", valid)
            self.assertEqual([], list(tests.validation_errors(root, {"app": 2})))
            for name, xml in variants.items():
                with self.subTest(name=name):
                    path.write_text(xml)
                    self.assertTrue(list(tests.validation_errors(root, {"app": 2})))

    def test_instrumented_validation_supports_aggregate_suites_without_double_counting(self):
        valid = (
            '<testsuites tests="2" failures="0" errors="0">'
            '<testsuite name="A" tests="1" failures="0" errors="0" skipped="0"><testcase name="a"/></testsuite>'
            '<testsuite name="B" tests="1" failures="0" errors="0" skipped="0"><testcase name="b"/></testsuite>'
            '</testsuites>'
        )
        variants = {
            "child skipped": valid.replace('skipped="0"', 'skipped="1"', 1),
            "child failure": valid.replace('failures="0"', 'failures="1"', 2).replace('failures="1"', 'failures="0"', 1),
            "child count mismatch": valid.replace('name="A" tests="1"', 'name="A" tests="2"'),
            "invalid child count": valid.replace('name="A" tests="1"', 'name="A" tests="?"'),
            "nested suite": valid.replace('<testcase name="a"/>', '<testsuite><testcase name="a"/></testsuite>'),
            "case outside suite": valid.replace('<testcase name="a"/>', '').replace('</testsuites>', '<testcase name="a"/></testsuites>'),
            "unsupported root": valid.replace('testsuites', 'other'),
        }
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = write_instrumented_suite(root, "app", valid)
            self.assertEqual([], list(tests.validation_errors(root, {"app": 2})))
            for name, xml in variants.items():
                with self.subTest(name=name):
                    path.write_text(xml)
                    self.assertTrue(list(tests.validation_errors(root, {"app": 2})))

    def test_instrumented_validation_command_accepts_complete_reports(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for module, count in tests.MIN_TESTS.items():
                cases = ''.join(f'<testcase name="case-{i}"/>' for i in range(count))
                suite = f'<testsuite tests="{count}" failures="0" errors="0">{cases}</testsuite>'
                write_instrumented_suite(root, module, suite)
            result = subprocess.run(
                [sys.executable, str(ROOT / "scripts/ci_report_test_results.py"), directory],
                capture_output=True,
                text=True,
                check=False,
            )
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_instrumented_validation_command_fails_when_reports_are_missing(self):
        with tempfile.TemporaryDirectory() as directory:
            result = subprocess.run(
                [sys.executable, str(ROOT / "scripts/ci_report_test_results.py"), directory],
                capture_output=True,
                text=True,
                check=False,
            )
            self.assertEqual(1, result.returncode)
            for module in tests.MIN_TESTS:
                self.assertIn(f"{module}: no instrumented test XML found", result.stdout)

    def test_instrumented_task_diagnostics_filters_gradle_output(self):
        with tempfile.TemporaryDirectory() as directory:
            log = Path(directory) / "gradle.log"
            log.write_text(
                "> Task :core-database:connectedDebugAndroidTest NO-SOURCE\n"
                "unrelated build output\n"
                "> Task :app:connectedDebugAndroidTest\n"
            )
            output = tests.task_diagnostics(log)
            self.assertIn("core-database:connectedDebugAndroidTest NO-SOURCE", output)
            self.assertIn("app:connectedDebugAndroidTest", output)
            self.assertNotIn("unrelated build output", output)

    def test_crash_report_keeps_lume_stack_and_drops_other_processes(self):
        text = "E AndroidRuntime: FATAL EXCEPTION: main\nE AndroidRuntime: Process: dev.lumenotes.app, PID: 123\nE AndroidRuntime: java.lang.NoSuchMethodError: missing\nE AndroidRuntime: FATAL EXCEPTION: main\nE AndroidRuntime: Process: com.example.other, PID: 456\nE AndroidRuntime: unrelated secret\n"
        result = crashes.diagnostics(text)
        self.assertIn("NoSuchMethodError", result)
        self.assertNotIn("unrelated secret", result)

    def test_lifecycle_mismatch_is_reported_when_there_is_no_crash(self):
        text = (
            "V ActivityScenario: ignored: startActivityIntent=Intent { act=android.intent.action.MAIN "
            "cmp=dev.lumenotes.app/.MainActivity }, activity.getIntent()=Intent { act=null }\n"
            "I ActivityManager: unrelated process\n"
        )
        text += "I/LumeScreen: dev.lumenotes SCREEN 1 1/1 U0VDUkVU\n"
        result = crashes.diagnostics(text)
        self.assertIn("android.intent.action.MAIN", result)
        self.assertIn("act=null", result)
        self.assertNotIn("unrelated process", result)
        self.assertNotIn("U0VDUkVU", result)

    def test_screenshot_reassembles_only_complete_valid_jpeg(self):
        image = b"\xff\xd8" + b"synthetic" * 200 + b"\xff\xd9"
        encoded = base64.b64encode(image).decode()
        parts = [encoded[i:i + 500] for i in range(0, len(encoded), 500)]
        lines = [f"I/LumeScreen: dev.lumenotes SCREEN 1 {i + 1}/{len(parts)} {part}" for i, part in enumerate(parts)]
        lines += ["I/LumeScreen: dev.lumenotes SCREEN 2 1/2 incomplete"]
        self.assertEqual(encoded, screenshots.screenshot("\n".join(lines)))
        self.assertIsNone(screenshots.screenshot("\n".join(lines[:-2])))

    def test_lint_report_selects_errors(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            xml = root / "app/build/reports/lint-results-debug.xml"
            xml.parent.mkdir(parents=True)
            xml.write_text('<issues><issue id="Actual" severity="Error" message="broken"><location file="src/foo.xml" line="2"/></issue><issue id="Warning" severity="Warning" message="notice"/></issues>')
            result = lint.diagnostics(root)
            self.assertIn("Actual at src/foo.xml:2: broken", result)
            self.assertNotIn("Warning", result)


if __name__ == "__main__":
    unittest.main()
