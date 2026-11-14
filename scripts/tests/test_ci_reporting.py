import base64
import importlib.util
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


if __name__ == "__main__":
    unittest.main()
