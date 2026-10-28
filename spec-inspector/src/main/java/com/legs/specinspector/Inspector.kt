package com.legs.specinspector

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject

internal object Inspector {
    private const val MAX_NODES = 3000

    /** Root views: tracked activities first, then WindowManagerGlobal (dialogs, popups) when reflection is allowed. */
    private fun roots(): List<View> {
        val out = LinkedHashSet<View>()
        runCatching {
            val g = Class.forName("android.view.WindowManagerGlobal")
            val inst = g.getMethod("getInstance").invoke(null)
            val f = g.getDeclaredField("mViews").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            (f.get(inst) as? List<View>)?.let { out.addAll(it) }
        }
        synchronized(SpecInspectorProvider.activities) {
            SpecInspectorProvider.activities.mapNotNull { it.get() }.filter { !it.isFinishing }
                .forEach { a -> runCatching { a.window?.decorView?.let { out += it } } }
        }
        return out.filter { it.isAttachedToWindow && it.isShown }
    }

    fun dump(ctx: Context, point: IntArray?): String {
        val arr = JSONArray()
        val loc = IntArray(2)
        var n = 0
        fun walk(v: View, depth: Int) {
            if (n >= MAX_NODES || !v.isShown) return
            v.getLocationOnScreen(loc)
            val l = loc[0]; val t = loc[1]; val r = l + v.width; val b = t + v.height
            val inside = point == null || (point[0] in l until r && point[1] in t until b)
            if (inside && v.width > 0 && v.height > 0) {
                runCatching { arr.put(describe(ctx, v, l, t, r, b, depth)); n++ }
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), depth + 1)
            if (inside) Compose.maybeWalk(v, arr, point)
        }
        roots().forEach { runCatching { walk(it, 0) } }
        return JSONObject().put("pkg", ctx.packageName).put("version", 1).put("density", ctx.resources.displayMetrics.density.toDouble())
            .put("views", arr).toString()
    }

    private fun idName(v: View): String? = if (v.id == View.NO_ID) null else runCatching { v.resources.getResourceEntryName(v.id) }.getOrNull()

    private fun describe(ctx: Context, v: View, l: Int, t: Int, r: Int, b: Int, depth: Int): JSONObject {
        val o = JSONObject()
        o.put("cls", v.javaClass.name).put("l", l).put("t", t).put("r", r).put("b", b).put("depth", depth)
        idName(v)?.let { o.put("id", it) }
        o.put("alpha", v.alpha.toDouble())
        background(v.background, o)
        if (v is TextView) o.put("text", text(ctx, v))
        return o
    }

    private fun background(d: Drawable?, o: JSONObject) {
        when (d) {
            null -> {}
            is ColorDrawable -> o.put("bg", d.color)
            is GradientDrawable -> {
                if (Build.VERSION.SDK_INT >= 24) {
                    d.color?.defaultColor?.let { o.put("bg", it) }
                    runCatching { o.put("radius", d.cornerRadius.toDouble()) }
                    d.cornerRadii?.let { o.put("radii", JSONArray(it.map { f -> f.toDouble() })) }
                }
                runCatching {
                    val st = d.constantState!!
                    val c = st.javaClass
                    val sw = c.getDeclaredField("mStrokeWidth").apply { isAccessible = true }.getInt(st)
                    if (sw > 0) {
                        o.put("strokeW", sw)
                        val cs = c.getDeclaredField("mStrokeColors").apply { isAccessible = true }.get(st) as? android.content.res.ColorStateList
                        cs?.let { o.put("stroke", it.defaultColor) }
                    }
                }
            }
            else -> {
                // MaterialShapeDrawable (no compile dependency): best effort via reflection
                if (d.javaClass.name.endsWith("MaterialShapeDrawable")) runCatching {
                    o.put("radius", (d.javaClass.getMethod("getTopLeftCornerResolvedSize").invoke(d) as Float).toDouble())
                    (d.javaClass.getMethod("getFillColor").invoke(d) as? android.content.res.ColorStateList)?.let { o.put("bg", it.defaultColor) }
                    (d.javaClass.getMethod("getStrokeWidth").invoke(d) as? Float)?.let { if (it > 0) o.put("strokeW", it.toDouble()) }
                    (d.javaClass.getMethod("getStrokeColor").invoke(d) as? android.content.res.ColorStateList)?.let { o.put("stroke", it.defaultColor) }
                }
                o.put("bgCls", d.javaClass.simpleName)
            }
        }
    }

    private val fontResCache = HashMap<String, Map<Typeface, String>>()
    /** Typeface instance -> res/font entry name, by loading every R.font id of the host (cached). */
    private fun fontRes(ctx: Context, tf: Typeface?): String? {
        if (tf == null || Build.VERSION.SDK_INT < 26) return null
        val map = fontResCache.getOrPut(ctx.packageName) {
            val m = HashMap<Typeface, String>()
            runCatching {
                val cls = Class.forName(ctx.packageName + ".R\$font")
                for (f in cls.fields) runCatching {
                    val id = f.getInt(null)
                    val base = ctx.resources.getFont(id)
                    m[base] = f.name
                    if (Build.VERSION.SDK_INT >= 28) for (w in 100..900 step 100) for (it in listOf(false, true)) m[Typeface.create(base, w, it)] = f.name
                    for (s in 0..3) m[Typeface.create(base, s)] = f.name
                }
            }
            m
        }
        return map[tf]
    }

    private fun weightName(w: Int) = when {
        w <= 100 -> "Thin"; w <= 200 -> "ExtraLight"; w <= 300 -> "Light"; w <= 400 -> "Regular"; w <= 500 -> "Medium"
        w <= 600 -> "SemiBold"; w <= 700 -> "Bold"; w <= 800 -> "ExtraBold"; else -> "Black"
    }

    private fun systemFamily(tf: Typeface?): String? {
        if (tf == null) return null
        if (Build.VERSION.SDK_INT >= 34) tf.systemFontFamilyName?.let { return it }
        return runCatching {
            val f = Typeface::class.java.getDeclaredField("sSystemFontMap").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val map = f.get(null) as Map<String, Typeface>
            map.entries.firstOrNull { it.value == tf }?.key
                ?: map.entries.firstOrNull { e -> Build.VERSION.SDK_INT >= 28 && (100..900 step 100).any { w -> Typeface.create(e.value, w, tf.isItalic) == tf } }?.key
        }.getOrNull() ?: if (tf == Typeface.DEFAULT || tf == Typeface.DEFAULT_BOLD) "sans-serif" else null
    }

    private fun text(ctx: Context, v: TextView): JSONObject {
        val o = JSONObject()
        val tf = v.typeface
        val px = v.textSize
        o.put("sizePx", px.toDouble())
        val raw = if (Build.VERSION.SDK_INT >= 28) tf?.weight ?: 400 else if (tf?.isBold == true) 700 else 400
        val bold = (tf?.isBold == true) || v.paint.isFakeBoldText
        val eff = if (bold && raw < 600) 700 else raw
        o.put("weight", eff).put("weightRaw", raw).put("weightName", weightName(eff))
        o.put("italic", tf?.isItalic == true).put("fakeBold", v.paint.isFakeBoldText).put("style", tf?.style ?: 0)
        systemFamily(tf)?.let { o.put("family", it) }
        fontRes(ctx, tf)?.let { o.put("fontRes", it) }
        o.put("letterEm", v.letterSpacing.toDouble()).put("letterPx", (v.letterSpacing * px).toDouble())
        o.put("lineHeightPx", v.lineHeight)
        o.put("lineExtra", v.lineSpacingExtra.toDouble()).put("lineMult", v.lineSpacingMultiplier.toDouble())
        o.put("color", v.currentTextColor).put("includeFontPadding", v.includeFontPadding)
        o.put("gravity", v.gravity).put("maxLines", v.maxLines).put("lines", v.lineCount)
        // fonts actually used to draw this text (shaped with the view's real paint/typeface); the text itself is not exported
        runCatching {
            val runs = FontResolver.resolve(v.text, android.graphics.Paint(v.paint))
            if (runs.isNotEmpty()) o.put("fonts", JSONArray().apply {
                runs.forEach { put(JSONObject().put("script", it.script).put("family", it.family).put("sub", it.sub).put("weight", it.weight)) }
            })
        }
        return o
    }
}
