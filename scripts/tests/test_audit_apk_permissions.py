import importlib.util
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("audit_apk_permissions", ROOT / "scripts/audit_apk_permissions.py")
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


class ApkPermissionsTest(unittest.TestCase):
    badging = "package: name='dev.lumenotes.app' versionCode='1'\nsdkVersion:'26'\n"

    def test_no_permissions_and_app_scoped_receiver_permission_are_allowed(self):
        self.assertEqual([], audit.audit("package: dev.lumenotes.app\n", self.badging))
        permission = "uses-permission: name='dev.lumenotes.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'"
        self.assertEqual([], audit.audit(permission, self.badging))

    def test_network_and_other_permissions_are_rejected(self):
        for name in ("INTERNET", "WAKE_LOCK", "READ_CONTACTS", "ACCESS_FINE_LOCATION", "MANAGE_EXTERNAL_STORAGE"):
            with self.subTest(name=name):
                output = f"uses-permission: name='android.permission.{name}'"
                self.assertTrue(audit.audit(output, self.badging))

    def test_missing_package_unparseable_permission_and_debuggable_fail(self):
        self.assertTrue(audit.audit("", "invalid"))
        self.assertTrue(audit.audit("uses-permission: unknown", self.badging))
        self.assertTrue(audit.audit("", self.badging + "application-debuggable\n"))


if __name__ == "__main__":
    unittest.main()
