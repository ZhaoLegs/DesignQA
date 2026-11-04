"""Static wiring checks supplementing the executable Kotlin regression tests."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/java/com/legs/designqa"
QA = (SRC / "QaService.kt").read_text()
VIEW = (SRC / "MeasureView.kt").read_text()
MAIN = (SRC / "MainActivity.kt").read_text()


class FeatureIntegration(unittest.TestCase):
    def test_cancelled_annotation_is_not_reenabled(self):
        body = QA.split("private fun toggleAnnotate()", 1)[1].split("fun capture(", 1)[0]
        self.assertIn("cv.annots !== nodes", body)
        self.assertLess(body.index("cv.annots = nodes"), body.index("capture {"))

    def test_notice_cannot_create_window_on_lockscreen(self):
        body = QA.split("fun notice(", 1)[1]
        self.assertLess(body.index("isKeyguardLocked"), body.index("wm.addView"))

    def test_touch_flags_are_snapshotted_before_changes(self):
        body = QA.split("private fun applyCanvasTouch()", 1)[1].split("private fun setPanelOpen", 1)[0]
        self.assertLess(body.index("val oldF = lp.flags"), body.index("lp.flags = if"))
        self.assertIn("oldF != lp.flags", body)

    def test_all_eight_controls_share_persistent_selection(self):
        chips = QA.split("val CHIPS =", 1)[1].split("private lateinit", 1)[0]
        for key in ("size", "space", "font", "family", "color", "bounds", "radius", "stroke"):
            self.assertIn(f'"{key}" to', chips)
        self.assertIn("annotationPrefs.select(k)", MAIN)
        self.assertIn("annotationPrefs.select(k)", QA)
        self.assertIn("refreshDisplaySwitches()", MAIN)

    def test_image_and_button_annotations_include_foreground_color(self):
        body = VIEW.split("private fun labelParts(", 1)[1].split("private fun wrap(", 1)[0]
        for kind, end in (("BUTTON", "IMAGE"), ("IMAGE", "CONTAINER")):
            branch = body.split(f"Kind.{kind} ->", 1)[1].split(f"Kind.{end} ->", 1)[0]
            self.assertIn('on("color")', branch)
            self.assertIn("hex(it[1])", branch)

    def test_geometry_and_row_policies_are_used_by_collector(self):
        body = QA.split("private fun collectAnnots()", 1)[1].split("fun findNode(", 1)[0]
        self.assertIn("GapGeometry(v, pos, a, b)", body)
        self.assertIn("representativeRows(signatures)", body)
        self.assertIn("spacingAnchor(n, k, r, kids)", body)
        self.assertIn("fontAnnotationRepresentatives(visible)", VIEW)

    def test_repeated_rows_keep_candidates_until_style_sampling(self):
        body = QA.split("private fun collectAnnots()", 1)[1].split("fun findNode(", 1)[0]
        self.assertIn("if (include) {", body)
        self.assertNotIn("if (include && annotate)", body)
        self.assertIn("structuralRepresentative = annotate", body)
        self.assertIn("val cr = w(ch, annotate && index in representatives)", body)
        self.assertIn("styleMode || it.structuralRepresentative", VIEW)

    def test_enabled_save_label_is_opaque_white(self):
        body = QA.split("val canSave =", 1)[1].split("val chipsEnabled", 1)[0]
        self.assertIn("if (canSave) 1f", body)
        self.assertIn("if (canSave) Color.WHITE", body)


if __name__ == "__main__":
    unittest.main()
