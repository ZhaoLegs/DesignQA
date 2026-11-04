"""Execute production row, font-cache and annotation-budget code with JVM doubles."""
from pathlib import Path
import os
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/com/legs/designqa"
view = (src / "MeasureView.kt").read_text()
def section(start, end):
    return view[view.index(start):view.index(end, view.index(start))]

body = section("    private val runsCache", "    /** Font family for labels")
body += section("    private fun annotationBudget", "    private fun dpForBudget")
program = r'''
package com.legs.designqa
data class Exact(val fonts: List<FontResolver.Run>? = null, val weight: Int? = null, val sizePx: Float? = null)
data class NodeData(val text: String?, val fontSp: Float? = 16f, val exact: Exact? = null,
    val structuralRepresentative: Boolean = true)
class Paint { var textSize = 0f; var typeface = 0 }
object Typeface { const val DEFAULT = 0; fun create(type: Int, weight: Int, italic: Boolean) = weight }
object FontResolver {
    data class Run(val text: String, val size: Float, val weight: Int)
    fun resolve(text: String, paint: Paint) = listOf(Run(text, paint.textSize, paint.typeface))
}
class Metrics { val heightPixels = 800 }
class Config { val fontScale = 1f }
class Resources { val displayMetrics = Metrics(); val configuration = Config() }
class Service { val resources = Resources() }
class Harness {
    val svc = Service()
    val resources = svc.resources
    val d = 1f
    var selected = "color"
    fun weightOf(a: NodeData) = 400
    fun on(key: String) = selected == key
    fun labelParts(a: NodeData) = listOf(a.text.orEmpty()) to 0
    fun fontStr(a: NodeData) = a.fontSp.toString()
    fun dpForBudget() = 80
    fun runs(a: NodeData) = runsOf(a)
    fun budget(nodes: List<NodeData>) = annotationBudget(nodes)
    fun cacheSize() = runsCache.size
'''
program += body
program += r'''
}
fun main(args: Array<String>) {
    when (args.first()) {
        "row" -> {
            val icon = RowShape("ImageView", "icon", 8f, 8f, 24f, 24f, false, null, false, false, emptyList())
            val card = RowShape("CardView", "card", 16f, 8f, 100f, 40f, false, null, false, false, listOf(icon))
            val row = RowShape("LinearLayout", "row", 0f, 0f, 360f, 56f, false, null, false, false, listOf(card))
            val wider = row.copy(children = listOf(card.copy(width = 200f)))
            check(representativeRows(listOf(row.signature(), wider.signature())).size == 2)
            val text = icon.copy(kind = "TextView", isText = true)
            val wrapper = card.copy(kind = "TitleContainer", children = listOf(text))
            val fixedA = row.copy(children = listOf(wrapper))
            val fixedB = row.copy(children = listOf(wrapper.copy(width = 200f)))
            check(fixedA.signature() != fixedB.signature())
            println("PASS: fixed-width icon and text containers remain distinct")
        }
        "cache" -> {
            val s = Harness()
            val first = NodeData("A".repeat(64))
            val second = first.copy(text = first.text + "\u4e2d")
            check(s.runs(first) != s.runs(second))
            check(s.runs(first.copy(fontSp = 16.2f)) != s.runs(first.copy(fontSp = 16.8f)))
            val exact = listOf(FontResolver.Run("exact", 20f, 700))
            check(s.runs(first.copy(exact = Exact(fonts = exact))) == exact)
            println("PASS: complete text and fractional sizes have distinct font cache entries")
        }
        "budget" -> {
            val s = Harness()
            val nodes = List(20) { NodeData("same") } + NodeData("different", structuralRepresentative = false)
            for (kind in listOf("family", "color", "radius", "stroke")) {
                s.selected = kind
                check(s.budget(nodes).any { it.text == "different" })
            }
            println("PASS: late style exceptions survive annotation density limits")
        }
        "bounded" -> {
            val s = Harness()
            repeat(1000) { s.runs(NodeData("text-$it")) }
            check(s.cacheSize() <= 128)
            println("PASS: font cache is bounded")
        }
    }
}
'''
out = Path(tempfile.mkdtemp(prefix="spec-style-"))
(out / "StyleRegression.kt").write_text(program)
cache = Path.home() / ".gradle/caches/modules-2/files-2.1"
jars = []
for module in ("kotlin-compiler-embeddable", "kotlin-stdlib", "kotlin-script-runtime"):
    jars.extend((cache / "org.jetbrains.kotlin" / module / "1.9.24").glob("**/*.jar"))
for group, module in (("org.jetbrains.kotlin", "kotlin-reflect"),
                      ("org.jetbrains.intellij.deps", "trove4j"), ("org.jetbrains", "annotations")):
    jars.extend((cache / group / module).glob("**/*.jar"))
cp = os.pathsep.join(map(str, jars))
java = "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home/bin/java"
subprocess.run([java, "-cp", cp, "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
                "-nowarn", "-no-stdlib", "-no-reflect", "-classpath", cp, "-d", str(out),
                str(src / "RowShape.kt"), str(out / "StyleRegression.kt")], check=True)
for case in sys.argv[1:] or ["row", "cache", "budget", "bounded"]:
    subprocess.run([java, "-cp", str(out) + os.pathsep + cp,
                    "com.legs.designqa.StyleRegressionKt", case], check=True)
