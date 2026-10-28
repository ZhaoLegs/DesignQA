package com.legs.specinspector

import android.view.View
import org.json.JSONArray
import org.json.JSONObject

/**
 * Best-effort Jetpack Compose support without a compile dependency.
 * Walks the semantics tree of AndroidComposeView and reads TextLayoutResult via the GetTextLayoutResult action.
 * Internal Compose APIs change between versions; any failure is silently ignored.
 */
internal object Compose {
    private val composeViewCls: Class<*>? = runCatching { Class.forName("androidx.compose.ui.platform.AndroidComposeView") }.getOrNull()

    fun maybeWalk(v: View, out: JSONArray, point: IntArray?) {
        val cls = composeViewCls ?: return
        if (!cls.isInstance(v)) return
        runCatching {
            val owner = cls.getMethod("getSemanticsOwner").invoke(v)
            val root = owner.javaClass.getMethod("getUnmergedRootSemanticsNode").invoke(owner)
            walk(root, out, point, 0)
        }
    }

    private fun call(o: Any, vararg names: String): Any? {
        for (n in names) runCatching { return o.javaClass.methods.first { it.name == n && it.parameterCount == 0 }.invoke(o) }
        return null
    }

    private fun walk(node: Any, out: JSONArray, point: IntArray?, depth: Int) {
        if (depth > 200 || out.length() > 3000) return
        val rect = call(node, "getBoundsInWindow", "getBoundsInRoot") ?: return
        // androidx.compose.ui.geometry.Rect -> left/top/right/bottom floats (window coords; compose views are full-window in most apps)
        val l = (call(rect, "getLeft") as Float).toInt(); val t = (call(rect, "getTop") as Float).toInt()
        val r = (call(rect, "getRight") as Float).toInt(); val b = (call(rect, "getBottom") as Float).toInt()
        val inside = point == null || (point[0] in l until r && point[1] in t until b)
        if (inside) runCatching { textOf(node)?.let { out.put(JSONObject().put("cls", "Compose.Text").put("l", l).put("t", t).put("r", r).put("b", b).put("text", it)) } }
        @Suppress("UNCHECKED_CAST")
        val kids = (call(node, "getChildren", "getChildren\$ui_release", "getReplacedChildren\$ui_release") as? List<Any>) ?: return
        kids.forEach { walk(it, out, point, depth + 1) }
    }

    private fun textOf(node: Any): JSONObject? {
        val config = call(node, "getConfig") ?: return null
        val actions = Class.forName("androidx.compose.ui.semantics.SemanticsActions")
        val inst = actions.getField("INSTANCE").get(null)
        val key = actions.getMethod("getGetTextLayoutResult").invoke(inst)
        val accessible = config.javaClass.methods.first { it.name == "getOrElseNullable" || it.name == "getOrNull" }
        val action = (if (accessible.parameterCount == 1) accessible.invoke(config, key) else null) ?: return null
        val fn = call(action, "getAction") ?: return null
        val list = ArrayList<Any>()
        fn.javaClass.methods.first { it.name == "invoke" && it.parameterCount == 1 }.invoke(fn, list)
        val res = list.firstOrNull() ?: return null
        val input = call(res, "getLayoutInput") ?: return null
        val style = call(input, "getStyle") ?: return null
        val density = call(input, "getDensity")
        val o = JSONObject()
        val dens = (density?.let { call(it, "getDensity") } as? Float) ?: 1f
        val fs = call(style, "getFontSize-XSAIIZE")
        fun spToPx(packed: Any?): Double? = (packed as? Long)?.let { java.lang.Float.intBitsToFloat((it and 0xffffffffL).toInt()).toDouble() * dens }
        spToPx(fs)?.let { o.put("sizePx", it) }
        (call(style, "getFontWeight")?.let { call(it, "getWeight") } as? Int)?.let { o.put("weight", it) }
        call(style, "getFontFamily")?.let { o.put("family", it.toString()) }
        spToPx(call(style, "getLetterSpacing-XSAIIZE"))?.let { o.put("letterPx", it) }
        spToPx(call(style, "getLineHeight-XSAIIZE"))?.let { o.put("lineHeightPx", it) }
        o.put("compose", true)
        return o
    }
}
