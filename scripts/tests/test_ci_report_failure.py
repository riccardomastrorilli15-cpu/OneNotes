import importlib.util
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("ci_report_failure", ROOT / "scripts/ci_report_failure.py")
report = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(report)


class CiFailureReportTest(unittest.TestCase):
    def test_reports_compiler_and_gradle_failure(self):
        text = "start\ne: file:///Main.kt:2:3 unresolved reference\nFAILURE: Build failed\n* What went wrong:\nbroken\n"
        result = report.diagnostic(text)
        self.assertIn("unresolved reference", result)
        self.assertIn("* What went wrong:", result)

    def test_escapes_workflow_command_characters(self):
        self.assertEqual("a%25b%0Ac%0D", report.escape("a%b\nc\r"))


if __name__ == "__main__":
    unittest.main()
