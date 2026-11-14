import importlib.util
import tempfile
import unittest
import warnings
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("audit_apk_fonts", ROOT / "scripts/audit_apk_fonts.py")
audit_module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(audit_module)
FONT = (ROOT / "app/src/main/assets/sesl_indexscroll_group_font.ttf").read_bytes()


class FontAuditTest(unittest.TestCase):
    def make_apk(self, entries, extension="apk"):
        folder = tempfile.TemporaryDirectory()
        self.addCleanup(folder.cleanup)
        path = Path(folder.name) / f"test.{extension}"
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            with zipfile.ZipFile(path, "w") as apk:
                for name, data in entries:
                    apk.writestr(name, data)
        return path

    def test_approved_fonts_pass(self):
        apk = self.make_apk([(audit_module.INDEX_ASSET, FONT), ("res/font/override.ttf", FONT)])
        self.assertEqual([], audit_module.audit(apk))

    def test_unknown_and_missing_fonts_fail(self):
        apk = self.make_apk([("res/font/proprietary.otf", b"not an approved font")])
        errors = audit_module.audit(apk)
        self.assertTrue(any("not found" in error for error in errors))
        self.assertTrue(any("Unapproved font" in error for error in errors))

    def test_bundle_requires_base_override_and_checks_all_modules(self):
        bundle_asset = f"base/{audit_module.INDEX_ASSET}"
        bundle = self.make_apk([(bundle_asset, FONT), ("base/res/font/override.ttf", FONT)], "aab")
        self.assertEqual([], audit_module.audit(bundle))
        bad = self.make_apk([(audit_module.INDEX_ASSET, FONT), ("feature/assets/unknown.OTF", b"unknown")], "aab")
        errors = audit_module.audit(bad)
        self.assertTrue(any("not found" in error for error in errors))
        self.assertTrue(any("feature/assets/unknown.OTF" in error for error in errors))

    def test_duplicate_assets_fail(self):
        apk = self.make_apk([(audit_module.INDEX_ASSET, FONT), (audit_module.INDEX_ASSET, FONT)])
        self.assertTrue(any("Duplicate" in error for error in audit_module.audit(apk)))


if __name__ == "__main__":
    unittest.main()
