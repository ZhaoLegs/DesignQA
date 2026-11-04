"""Execute overlay state regressions against the production Kotlin policy."""
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
cache = Path.home() / ".gradle/caches/modules-2/files-2.1"
jars = []
for module in ("kotlin-compiler-embeddable", "kotlin-stdlib", "kotlin-script-runtime"):
    jars.extend((cache / "org.jetbrains.kotlin" / module / "1.9.24").glob("**/*.jar"))
for group, module in (("org.jetbrains.kotlin", "kotlin-reflect"),
                      ("org.jetbrains.intellij.deps", "trove4j"),
                      ("org.jetbrains", "annotations")):
    jars.extend((cache / group / module).glob("**/*.jar"))
java = "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home/bin/java"
classpath = os.pathsep.join(map(str, jars))
with tempfile.TemporaryDirectory(prefix="spec-overlay-test-") as output:
    subprocess.run([java, "-cp", classpath, "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
                    "-no-stdlib", "-no-reflect", "-classpath", classpath, "-d", output,
                    str(root / "app/src/main/java/com/legs/designqa/OverlayState.kt"),
                    str(root / "tests/OverlayStateRegression.kt")], check=True)
    subprocess.run([java, "-cp", output + os.pathsep + classpath,
                    "com.legs.designqa.OverlayStateRegressionKt"], check=True)

qa = (root / "app/src/main/java/com/legs/designqa/QaService.kt").read_text()
activity = (root / "app/src/main/java/com/legs/designqa/MainActivity.kt").read_text()
show = qa.split("fun showOverlay()", 1)[1].split("fun isOverlayShowing()", 1)[0]
assert "if (isOverlayShowing()) return true" in show
assert show.index("removeOverlay()") < show.index("wm.addView(cv, lp)")
state = qa.split("fun isOverlayShowing()", 1)[1].split("var onOverlayChanged", 1)[0]
assert "OverlayState.showing(" in state and "bubble?.isAttachedToWindow" in state
assert "canvasView?.hideForCapture == true" in state
assert "stateHandler.removeCallbacks(stateRefresh)" in activity
assert "QaService.onStateChanged = null" in activity
assert "if (s.showOverlay()) moveTaskToBack(true)" in activity
request = qa.split("private fun requestScreenshot(", 1)[1].split("private fun screenshotFailure", 1)[0]
assert "h.postDelayed(timeout, 3000)" in request
assert "if (completed) { result.hardwareBuffer.close(); return }" in request
print("Overlay recovery, screen lifecycle and dropped-callback integration contracts: passed")
