#!/usr/bin/env python3
"""Compile current capture functions with deterministic platform doubles.

No Android APK, device, Gradle build, or repository writes are involved.
The real capture/gate/annotation callback bodies run on the JVM.
Platform screenshot completion and Handler time are controlled by each case.
"""
import os
from pathlib import Path
import subprocess
import tempfile
import sys

ROOT = Path(__file__).resolve().parents[1]
OUT = Path(tempfile.mkdtemp(prefix="spec-capture-races-"))
QA = (ROOT / "app/src/main/java/com/legs/designqa/QaService.kt").read_text()


def section(start, end):
    return QA[QA.index(start):QA.index(end, QA.index(start))]


bodies = "\n".join([
    section("    private var contentRevision", "    override fun onAccessibilityEvent"),
    section("    private fun toggleAnnotate()", "    /**\n     * Screenshot"),
    section("    fun capture(", "    /** All window, display"),
    section("    private fun screenshotFailure(", "    private fun saveAnnotated()"),
    section("    private fun restorePanels()", "    /**\n     * The measuring layer"),
    section("    fun hideOverlay()", "    fun removeOverlay()"),
])

prefix = r'''
package com.legs.designqa

class Bitmap(val name: String) {
    var recycled = false
    fun recycle() { recycled = true }
    enum class Config { ARGB_8888 }
    companion object {
        fun createBitmap(w: Int, h: Int, config: Config) = Bitmap("composite")
    }
}
class Canvas(val bitmap: Bitmap) {
    fun drawBitmap(source: Bitmap, x: Float, y: Float, paint: Any?) {
        check(!source.recycled && !bitmap.recycled)
    }
}
class Rect(var left: Int = 0, var top: Int = 0)
open class View {
    var visibility = VISIBLE
    companion object { const val VISIBLE = 0; const val INVISIBLE = 4; const val GONE = 8 }
}
class MeasureView : View() {
    enum class Mode { NONE, INSPECT }
    var mode = Mode.NONE
    var hideForCapture = false
    var shot: Bitmap? = null
        set(value) {
            if (field === value) return
            val previous = field
            field = value
            previous?.recycle()
            colorCache.clear(); weightCache.clear()
        }
    var annots: List<String>? = null
    var gaps: List<String>? = null
    var annotsHidden = false
    val colorCache = mutableMapOf<String, Int>()
    val weightCache = mutableMapOf<String, Int>()
    fun clearSel() { annots = null; gaps = null; annotsHidden = false }
    fun invalidate() {}
}
class Handler {
    var now = 0L
    data class Entry(val time: Long, val action: Runnable)
    private val work = mutableListOf<Entry>()
    fun postDelayed(action: Runnable, delay: Long) { work += Entry(now + delay, action) }
    fun removeCallbacks(action: Runnable) { work.removeAll { it.action === action } }
    fun advanceTo(time: Long) {
        while (true) {
            val next = work.filter { it.time <= time }.minByOrNull { it.time } ?: break
            work.remove(next)
            now = next.time
            next.action.run()
        }
        now = time
    }
}
object Build { object VERSION { var SDK_INT = 34 } }
class KeyguardManager { var isKeyguardLocked = false }
class Metrics { val widthPixels = 1080; val heightPixels = 2400 }
class Resources { val displayMetrics = Metrics() }
class Root { val packageName = "target.app" }
class Window {
    val id = 1; val type = 1; val root = Root(); val layer = 1
    fun getBoundsInScreen(rect: Rect) {}
}
fun shouldShowOverlays(keyguardLocked: Boolean) = !keyguardLocked

class Harness {
    val h = Handler()
    private val screenshotGate = ScreenshotGate<Bitmap> { h.now }
    var canvasView: MeasureView? = MeasureView()
    var panel: View? = View()
    var bubbleWin: View? = View()
    var noticeView: View? = null
    var overlaysLocked = false
    var onStateChanged: (() -> Unit)? = null
    val keyguard = KeyguardManager()
    val resources = Resources()
    val packageName = "com.legs.designqa"
    val windows = listOf(Window())
    val boundsRefresh = Runnable {}
    val pending = mutableListOf<(Bitmap?, Int) -> Unit>()
    val messages = mutableListOf<String>()
    var nextNodes = listOf("initial")
    var collections = 0
    fun collectAnnots(): Pair<List<String>, List<String>> {
        collections++
        return nextNodes to emptyList()
    }
    fun getSystemService(type: Class<KeyguardManager>) = keyguard
    var connected = true
    fun isConnected() = connected
    fun notice(msg: String) { messages += msg }
    fun screenshotFailureForTest(error: Int) { screenshotFailure(error) }
    fun refreshButtons() {}
    fun applyCanvasTouch() {}
    fun setMode(mode: MeasureView.Mode) { canvasView?.mode = mode; canvasView?.clearSel() }
    fun requestScreenshot(windowId: Int?, retry: Int, done: (Bitmap?, Int) -> Unit) {
        pending += done
    }
    fun complete() { pending.removeAt(0)(Bitmap("platform-frame"), 0) }
    fun refresh() { annotRefresh.run() }
    fun toggle() { toggleAnnotate() }
    fun lockSync() { syncLockscreenVisibility() }
    fun scrollEvent() {
        contentChanged()
    }
    fun removeOverlay() {
        canvasView?.shot = null
        canvasView = null; panel = null; bubbleWin = null
    }
    fun recreateOverlay() {
        // WindowManager addView is replaced; these are new identities as in showOverlay.
        canvasView = MeasureView(); panel = View(); bubbleWin = View()
    }
    companion object {
        const val ERROR_TAKE_SCREENSHOT_SECURE_WINDOW = 6
        const val ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS = 2
        const val ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT = 3
    }
'''

suffix = r'''
}

fun main(args: Array<String>) {
    if (args.first() == "unlock_refresh") run {
        val s = Harness()
        val cv = s.canvasView!!
        cv.annots = listOf("before-lock")
        s.scrollEvent()
        s.keyguard.isKeyguardLocked = true
        s.lockSync()
        s.h.advanceTo(500)
        check(s.pending.isEmpty())
        check(cv.annots == null && !cv.annotsHidden) // Existing lock policy exits measurement.
        s.nextNodes = listOf("after-unlock")
        s.keyguard.isKeyguardLocked = false
        s.lockSync()
        s.toggle()
        s.h.advanceTo(800)
        check(s.pending.size == 1) { "unlock must allow a fresh annotation session" }
        s.complete()
        check(!cv.annotsHidden && cv.annots == listOf("after-unlock"))
        println("PASS: locking exits annotations and unlocking allows a fresh capture")
    }
    if (args.first() == "failure_message") run {
        val s = Harness()
        s.screenshotFailureForTest(1)
        check(s.messages.none { it.contains("重新开启") }) { "live screenshot timeout is not lost accessibility permission" }
        s.screenshotFailureForTest(2)
        check(s.messages.none { it.contains("重新开启") })
        println("PASS: live capture failures do not misreport disabled accessibility")
    }
    if (args.first() == "burst") run {
        val s = Harness()
        val cv = s.canvasView!!
        cv.annots = listOf("initial")
        s.refresh()
        repeat(100) {
            s.nextNodes = listOf("revision-$it")
            s.scrollEvent()
            s.refresh()
        }
        s.complete()
        check(s.pending.size == 1)
        s.complete()
        check(s.pending.isEmpty()) { "obsolete refreshes must not start extra platform requests" }
        check(cv.annots == listOf("revision-99") && !cv.annotsHidden)
        println("PASS: refresh burst submits only the latest pending revision")
    }
    if (args.first() == "refresh_failure") run {
        val s = Harness()
        val cv = s.canvasView!!
        cv.annots = listOf("initial")
        cv.annotsHidden = true
        s.refresh()
        s.pending.removeAt(0)(null, Harness.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW)
        check(!cv.annotsHidden) { "failed refresh must release the saving/refreshing state" }
        check(cv.annots == s.nextNodes)
        println("PASS: failed refresh releases pending state")
    }
    if (args.first() == "inspect_revision") run {
        val s = Harness()
        val cv = s.canvasView!!
        cv.mode = MeasureView.Mode.INSPECT
        cv.shot = Bitmap("old")
        s.capture()
        s.scrollEvent()
        check(cv.shot == null) { "inspect must invalidate stale screenshot on content change" }
        s.h.advanceTo(280)
        s.complete()
        check(s.pending.size == 1) { "inspect must replace a discarded in-flight frame" }
        s.complete()
        check(cv.shot != null)
        println("PASS: inspect replaces stale screenshot after content change")
    }
    Build.VERSION.SDK_INT = 34
    if (args.first() == "session") run {
        val s = Harness()
        val cv = s.canvasView!!
        cv.annots = listOf("initial")
        s.nextNodes = listOf("old-refresh")
        s.refresh()
        check(s.pending.size == 1)
        s.toggle() // User turns annotations off while window screenshot is in flight.
        check(cv.annots == null)
        s.nextNodes = listOf("new-user-session")
        s.toggle()
        check(cv.annots == listOf("new-user-session"))
        check(s.pending.size == 1) // The real ScreenshotGate merged the second consumer.
        s.complete()
        check(cv.annots == listOf("new-user-session"))
        check(s.pending.size == 1)
        s.complete()
        check(!cv.annotsHidden)
        println("PASS: old refresh cannot overwrite a new annotation session")
    }

    Build.VERSION.SDK_INT = 33
    if (args.first() == "scroll") run {
        val s = Harness()
        val cv = s.canvasView!!
        cv.annots = listOf("initial")
        s.nextNodes = listOf("before-final-scroll")
        s.refresh()
        s.h.advanceTo(80) // captureHiding has submitted the display capture.
        check(s.pending.size == 1 && cv.hideForCapture)
        s.nextNodes = listOf("after-final-scroll")
        s.scrollEvent()
        s.h.advanceTo(360) // Scroll debounce expires while the platform callback is pending.
        s.h.advanceTo(500)
        s.complete()
        s.h.advanceTo(700)
        check(s.pending.size == 1)
        s.complete()
        check(cv.annots == listOf("after-final-scroll") && !cv.annotsHidden)
        check(s.collections == 2 && s.pending.isEmpty())
        println("PASS: final scroll refresh survives an in-flight display capture")
    }

    Build.VERSION.SDK_INT = 34
    if (args.first() == "overlay") run {
        val s = Harness()
        s.capture()
        check(s.pending.size == 1)
        s.hideOverlay()
        s.recreateOverlay()
        val newCv = s.canvasView!!
        s.nextNodes = listOf("new-overlay-session")
        s.toggle()
        check(newCv.annots != null)
        check(s.pending.size == 1)
        s.complete()
        check(s.pending.size == 1)
        s.complete()
        check(newCv.annots == listOf("new-overlay-session") && newCv.shot != null)
        check(s.pending.isEmpty())
        println("PASS: recreated overlay receives its own capture")
    }
}
'''

# Mechanical source extraction: writes only under the designated temporary directory.
(OUT / "Group1Repro.kt").write_text(prefix + bodies + suffix)
(OUT / "AndroidWindows.kt").write_text(
    "package android.view.accessibility\n"
    "object AccessibilityWindowInfo { const val TYPE_APPLICATION = 1 }\n"
)
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
commands = [
    [java, "-cp", classpath, "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
     "-nowarn", "-no-stdlib", "-no-reflect", "-classpath", classpath, "-d", str(OUT / "classes"),
     str(ROOT / "app/src/main/java/com/legs/designqa/ScreenshotGate.kt"),
     str(OUT / "AndroidWindows.kt"), str(OUT / "Group1Repro.kt")],
]
commands += [[java, "-cp", str(OUT / "classes") + os.pathsep + classpath,
              "com.legs.designqa.Group1ReproKt", case]
             for case in (sys.argv[1:] or ["session", "scroll", "overlay", "refresh_failure", "inspect_revision", "burst", "failure_message", "unlock_refresh"])]
with (OUT / "result.log").open("w") as log:
    for command in commands:
        result = subprocess.run(command, capture_output=True, text=True)
        output = result.stdout + result.stderr
        print(output, end="")
        log.write(output)
        if result.returncode:
            raise SystemExit(result.returncode)
