"""Integration contracts for foreground accessibility and disconnection cleanup."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"
QA = (ROOT / "app/src/main/java/com/legs/designqa/QaService.kt").read_text()


class ServiceLifecycleRegression(unittest.TestCase):
    def test_foreground_service_has_required_android_permissions(self):
        manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
        permissions = {p.get(ANDROID + "name") for p in manifest.findall("uses-permission")}
        self.assertIn("android.permission.FOREGROUND_SERVICE", permissions)
        self.assertIn("android.permission.FOREGROUND_SERVICE_SPECIAL_USE", permissions)
        service = manifest.find("application/service")
        self.assertEqual("specialUse", service.get(ANDROID + "foregroundServiceType"))

    def test_connected_service_starts_foreground_notification(self):
        connected = QA.split("override fun onServiceConnected()", 1)[1].split("private val boundsRefresh", 1)[0]
        self.assertIn("startMonitoringForeground()", connected)
        self.assertIn("startForeground(", QA)

    def test_disconnect_clears_stale_service_and_overlays(self):
        self.assertIn("override fun onUnbind(", QA)
        self.assertIn("private fun disconnect()", QA)
        cleanup = QA.split("private fun disconnect()", 1)[1].split("private fun winType()", 1)[0]
        self.assertIn("removeOverlay()", cleanup)
        self.assertIn("instance = null", cleanup)
        self.assertIn("stopForeground(", cleanup)

    def test_screenshot_checks_live_connection_before_request(self):
        capture = QA.split("fun capture(", 1)[1].split("private fun captureHiding", 1)[0]
        self.assertLess(capture.index("isConnected()"), capture.index("screenshotGate.begin"))


if __name__ == "__main__":
    unittest.main()
