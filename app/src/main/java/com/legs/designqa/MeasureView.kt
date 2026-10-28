package com.legs.designqa

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Shared header spec for the floating panel (QaService) and the info card (drawCard): title left, close right. */
object Hdr {
    const val H = 48            // header height (dp)
    const val PAD = 16          // left padding of title (dp)
    const val CLOSE = 40        // close touch target (dp)
    const val CLOSE_INSET = 15  // close icon inset -> 10dp X
    const val CLOSE_CIRCLE = 20 // visible circle (dp)
    val CLOSE_BG = Color.parseColor("#14FFFFFF")
    const val CLOSE_RIGHT = 10  // close button right edge to container edge (dp)
    const val CLOSE_ALPHA = 1f
    const val TITLE_SP = 13f
    const val SUB_SP = 11f
    const val SUB_GAP = 8       // title -> subtitle (dp)
    val TITLE_COLOR = Color.WHITE
    val SUB_COLOR = Color.parseColor("#7A7A7A")
    const val CONTENT_GAP = 4   // divider -> first content (dp)
}

object Pal {
    val ACCENT = Color.parseColor("#007AFF")
    val SEL = Color.parseColor("#0D99FF")
    val MEASURE = Color.parseColor("#F24822")
    val TEXT = Color.parseColor("#FF2D55")
    val BUTTON = Color.parseColor("#007AFF")
    val IMAGE = Color.parseColor("#34C759")
    val CONTAINER = Color.parseColor("#8E8E93")
    val GAP = Color.parseColor("#A259FF")
    val CARD = Color.parseColor("#F22C2C2E")
    val POPOVER = Color.parseColor("#F7FFFFFF")
    fun of(k: Kind) = when (k) { Kind.TEXT -> TEXT; Kind.BUTTON -> BUTTON; Kind.IMAGE -> IMAGE; Kind.CONTAINER -> CONTAINER }
}

@SuppressLint("ViewConstructor")
class MeasureView(ctx: Context, private val svc: QaService) : View(ctx) {
    enum class Mode { NONE, INSPECT }

    var mode = Mode.NONE
    var hideForCapture = false
    var shot: Bitmap? = null
    var allBounds: List<Rect>? = null
    var annots: List<NodeData>? = null
    /** 全屏标注滚动中：暂时隐藏，停止滚动后由 QaService 刷新再显示 */
    var annotsHidden = false
    var gaps: List<Gap>? = null
    val colorCache = HashMap<Rect, IntArray>()
    private val shapeCache = HashMap<Rect, FloatArray>()
    private var selA: NodeData? = null
    private var selB: NodeData? = null
    private var closeBtn: RectF? = null
    private var cardRect: RectF? = null

    private val d = svc.density()
    /** "16pt · 48px": pt = 1x design points = px / density. */
    private fun fontLabel(sp: Float, exact: Boolean): String {
        val px = sp * d * svc.resources.configuration.fontScale
        val pt = px / d
        val ptS = if (exact && kotlin.math.abs(pt - Math.round(pt)) > 0.05f) "%.1f".format(pt) else "%.0f".format(pt)
        return (if (exact) "" else "≈") + ptS + "pt" // 只显示 pt，不再附 px
    }
    private val loc = IntArray(2)
    private fun dpf(v: Float) = v * d
    private fun toDp(px: Int) = (px / d).roundToInt().toString()

    private val mono = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val aa = Paint.ANTI_ALIAS_FLAG
    private val selStroke = Paint(aa).apply { color = Pal.SEL; style = Paint.Style.STROKE; strokeWidth = dpf(1.5f) }
    private val selFill = Paint().apply { color = (Pal.SEL and 0xFFFFFF) or 0x1F000000 }
    private val selB_Stroke = Paint(aa).apply { color = Pal.MEASURE; style = Paint.Style.STROKE; strokeWidth = dpf(1.5f) }
    private val selB_Fill = Paint().apply { color = (Pal.MEASURE and 0xFFFFFF) or 0x1F000000 }
    private val handleFill = Paint(aa).apply { color = Color.WHITE }
    private var cardTitleS = ""; private var cardSubS = ""
    private val measure = Paint(aa).apply { color = Pal.MEASURE; strokeWidth = dpf(1f) }
    private val dash = Paint(aa).apply { color = (Pal.MEASURE and 0xFFFFFF) or 0x99000000.toInt(); strokeWidth = dpf(1f); style = Paint.Style.STROKE; pathEffect = DashPathEffect(floatArrayOf(dpf(4f), dpf(3f)), 0f) }
    private val badgeBg = Paint(aa)
    private val badgeTx = Paint(aa).apply { color = Color.WHITE; textSize = dpf(10f); typeface = medium; fontFeatureSettings = "tnum" }
    private val cardBg = Paint(aa).apply { color = Pal.CARD }
    private val cardShadow = Paint(aa).apply { color = 0x00000000; setShadowLayer(dpf(20f), 0f, dpf(8f), 0x66000000) }
    private val lblTx = Paint(aa).apply { color = Color.parseColor("#8A8A8E"); textSize = dpf(12f) }
    private val popBg = Paint(aa).apply { color = Pal.POPOVER }
    private val valTx = Paint(aa).apply { color = Color.WHITE; textSize = dpf(13f) }
    private val titleTx = Paint(aa).apply { color = Color.BLACK; textSize = dpf(15f); typeface = Typeface.create(Typeface.DEFAULT, 600, false) }
    private val subTx = Paint(aa).apply { color = Color.parseColor("#8A8A8E"); textSize = dpf(12f) }
    private val hintTx = Paint(aa).apply { color = Color.WHITE; textSize = dpf(12.5f); typeface = medium }
    private val boundsP = Paint().apply { color = Color.parseColor("#80FF2D9A"); style = Paint.Style.STROKE; strokeWidth = 1f }
    private val outlineP = Paint(aa).apply { style = Paint.Style.STROKE; strokeWidth = dpf(1f) }
    private val swatchStroke = Paint(aa).apply { style = Paint.Style.STROKE; strokeWidth = dpf(1f); color = 0x66FFFFFF }
    private val tmp = Paint(aa)

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) } // shadow layers

    fun clearSel() { selA = null; selB = null; annots = null; annotsHidden = false; gaps = null; closeBtn = null; cardRect = null; layoutKey = null; invalidate() }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = e.rawX.toInt(); val y = e.rawY.toInt()
        when (mode) {
            Mode.INSPECT -> if (e.action == MotionEvent.ACTION_UP) {
                closeBtn?.let { if (it.contains(e.rawX, e.rawY)) { selA = null; selB = null; closeBtn = null; cardRect = null; invalidate(); return true } }
                cardRect?.let { if (it.contains(e.rawX, e.rawY)) return true }
                val n = runCatching { svc.findNode(x, y) }.getOrNull()
                if (selA == null || selB != null) { selA = n; selB = null } else selB = n
                invalidate()
            }
            Mode.NONE -> return false
        }
        return true
    }

    override fun onDraw(c: Canvas) {
        if (hideForCapture) return
        getLocationOnScreen(loc)
        c.save(); c.translate(-loc[0].toFloat(), -loc[1].toFloat())
        drawContent(c, false)
        c.restore()
        if (mode == Mode.INSPECT && selA == null) hint(c, "关闭弹窗点击任意空间查看信息")
    }

    /** In 检查 the chips are disabled and the card always shows the full core info. */
    private fun on(k: String) = if (mode == Mode.INSPECT) k != "bounds" else svc.isOn(k)

    /** Draws everything in screen coordinates. forExport = rendering onto a saved screenshot. */
    fun drawContent(c: Canvas, forExport: Boolean) {
        val sw = resources.displayMetrics.widthPixels.coerceAtLeast(width)
        val W = (if (forExport) c.width else width + loc[0]).toFloat()
        val H = (if (forExport) c.height else height + loc[1]).toFloat()
        if (on("bounds")) allBounds?.forEach { c.drawRect(it, boundsP) }
        if (!annotsHidden) annots?.let { drawAnnots(c, it, W, H) }
        if (mode == Mode.INSPECT) drawInspect(c, W, H)
    }

    fun hex(c: Int) = "#%06X".format(c and 0xFFFFFF)
    private fun cdist(a: Int, b: Int) = abs(Color.red(a) - Color.red(b)) + abs(Color.green(a) - Color.green(b)) + abs(Color.blue(a) - Color.blue(b))

    /** [background, text] colors sampled from the screenshot inside r; null without a screenshot. */
    val weightCache = HashMap<Rect, Int>()
    /**
     * Estimated font weight (CSS 100–900, -1 unknown) from the screenshot: classify pixels as text/background
     * by distance to the two dominant colours, take the median horizontal ink run (≈ stem width) and
     * normalise it by the font size in px. Thresholds are rough, uncalibrated per font.
     */
    fun weightOf(a: NodeData): Int {
        val s = shot ?: return -1
        val sp = a.fontSp ?: return -1
        val r = a.rect
        weightCache[r]?.let { return it }
        val fontPx = sp * d * svc.resources.configuration.fontScale
        val cs = colorsOf(r) ?: return -1
        val bg = cs[0]; val fg = cs[1]
        if (cdist(bg, fg) < 60 || fontPx < 6) { weightCache[Rect(r)] = -1; return -1 }
        val l = max(0, r.left); val t = max(0, r.top); val rr = min(s.width, r.right); val bb = min(s.height, r.bottom)
        val runs = ArrayList<Int>()
        val row = IntArray(max(0, rr - l))
        var y = t
        while (y < bb) {
            if (rr - l > 0) s.getPixels(row, 0, rr - l, l, y, rr - l, 1)
            var run = 0
            for (p in row) {
                if (cdist(p, fg) < cdist(p, bg)) run++ else { if (run > 0) runs += run; run = 0 }
            }
            if (run > 0) runs += run
            y += 1
        }
        val res = if (runs.size < 8) -1 else {
            runs.sort()
            val stem = runs[runs.size / 2].toFloat() // horizontal ink run ≈ vertical stem width (vertical runs under-read CJK)
            val ratio = stem / fontPx
            // calibrated on device (CJK UI text): Regular ≈0.07, Semibold title ≈0.114
            when { ratio < 0.058f -> 300; ratio < 0.085f -> 400; ratio < 0.100f -> 500; ratio < 0.125f -> 600; ratio < 0.155f -> 700; else -> 800 }
        }
        weightCache[Rect(r)] = res
        return res
    }

    fun weightName(w: Int) = when (w) { 300 -> "Light"; 400 -> "Regular"; 500 -> "Medium"; 600 -> "Semibold"; 700 -> "Bold"; else -> "Heavy" }

    fun colorsOf(r: Rect): IntArray? {
        val s = shot ?: return null
        colorCache[r]?.let { return it }
        val l = r.left.coerceIn(0, s.width - 1); val t = r.top.coerceIn(0, s.height - 1)
        val rr = r.right.coerceIn(l + 1, s.width); val bb = r.bottom.coerceIn(t + 1, s.height)
        val step = max(1, min(rr - l, bb - t) / 40)
        val hist = HashMap<Int, Int>()
        var y = t
        while (y < bb) { var x = l; while (x < rr) { val p = s.getPixel(x, y) and 0xF8F8F8; hist[p] = (hist[p] ?: 0) + 1; x += step }; y += step }
        val sorted = hist.entries.sortedByDescending { it.value }
        val bgQ = sorted.first().key
        val fgQ = sorted.firstOrNull { cdist(it.key, bgQ) > 120 }?.key ?: sorted.firstOrNull { cdist(it.key, bgQ) > 40 }?.key ?: bgQ
        fun exact(q: Int): Int {
            var yy = t
            while (yy < bb) { var x = l; while (x < rr) { val p = s.getPixel(x, yy); if (p and 0xF8F8F8 == q) return p; x += step }; yy += step }
            return q or 0xFF000000.toInt()
        }
        val res = intArrayOf(exact(bgQ), exact(fgQ))
        colorCache[Rect(r)] = res
        return res
    }

    /**
     * Estimated [radiusDp (-1 unknown), strokeColor (0 = none), fillColor] from the screenshot.
     * Radius: walk the diagonal from the top-left corner until the colour leaves the outside colour;
     * for a circular corner the diagonal gap is r·(1−1/√2).
     */
    /**
     * [radius dp (-1 unknown), stroke colour (0 none), fill colour, stroke width dp, stroke width px] from the screenshot.
     * Stroke: on each side, at 7 points between 20%–80% (corners skipped), look at the outermost 0–3px band and compare it
     * with the fill (≈6dp inside) and the pixels just outside. A side counts when ≥4/7 samples differ from both by ≥12
     * (sum of RGB diffs), so 1px light hairlines (#E5E5E5 on white) and anti-aliased edges are caught. Needs ≥3 sides.
     */
    fun shapeOf(r: Rect): FloatArray? {
        val s = shot ?: return null
        shapeCache[r]?.let { return it }
        val res = floatArrayOf(-1f, 0f, 0f, 0f, 0f)
        if (r.width() < 6 || r.height() < 6 || r.left < 0 || r.top < 0 || r.right > s.width || r.bottom > s.height) {
            shapeCache[Rect(r)] = res; return res
        }
        fun px(x: Int, y: Int) = s.getPixel(x.coerceIn(0, s.width - 1), y.coerceIn(0, s.height - 1))
        val T = 12
        val inset = min((6 * d).toInt(), min(r.width(), r.height()) / 3).coerceAtLeast(3)
        // side: 0 top,1 bottom,2 left,3 right. p(side, along, k) = pixel k px inward from that edge (k<0 = outside)
        fun p(side: Int, t: Float, k: Int): Int {
            val x = (r.left + t * (r.width() - 1)).toInt(); val y = (r.top + t * (r.height() - 1)).toInt()
            return when (side) { 0 -> px(x, r.top + k); 1 -> px(x, r.bottom - 1 - k); 2 -> px(r.left + k, y); else -> px(r.right - 1 - k, y) }
        }
        val fill = px(r.centerX(), r.top + inset).let { if (r.height() > inset * 2) it else px(r.left + inset, r.centerY()) }
        res[2] = fill.toFloat()
        val edgeCols = ArrayList<Int>(); val widths = ArrayList<Int>(); var sides = 0
        for (side in 0..3) {
            var hits = 0
            for (n in 0 until 7) {
                val t = 0.2f + n * 0.1f
                val inner = p(side, t, inset); val out = p(side, t, -2)
                var best = -1; var bd = 0
                for (k in 0..1) { val c = p(side, t, k); val dd = min(cdist(c, inner), cdist(c, out)); if (dd > bd) { bd = dd; best = k } }
                // reject anti-aliased edges of a filled shape: their colour lies "between" inside and outside
                val blend = best >= 0 && p(side, t, best).let { c -> cdist(c, inner) + cdist(c, out) <= cdist(inner, out) + T }
                if (best >= 0 && bd >= T && !blend) {
                    val ec = p(side, t, best)
                    var w = 1; while (best + w <= 6 && cdist(p(side, t, best + w), ec) < T) w++
                    if (best + w < inset) { hits++; edgeCols += ec; widths += w }
                }
            }
            if (hits >= 4) sides++
        }
        val hasStroke = sides >= 3
        var edge = fill
        if (hasStroke) {
            edgeCols.sortBy { Color.red(it) + Color.green(it) + Color.blue(it) }
            edge = edgeCols[edgeCols.size / 2]; widths.sort()
            val wpx = widths[widths.size / 2]
            res[1] = (edge or 0xFF000000.toInt()).toFloat().let { if (it == 0f) 1f else it }; res[3] = wpx / d; res[4] = wpx.toFloat()
        }
        val outside = if (r.left >= 2) px(r.left - 2, r.centerY()) else if (r.top >= 2) px(r.centerX(), r.top - 2) else px(r.left, r.top)
        val shapeCol = if (hasStroke) edge else fill
        if (cdist(shapeCol, outside) > 30) {
            val corner = px(r.left, r.top)
            if (cdist(corner, shapeCol) < 30) res[0] = 0f
            else {
                val lim = min(r.width(), r.height()) / 2
                var i = 0
                while (i < lim && cdist(px(r.left + i, r.top + i), shapeCol) > 40) i++
                res[0] = min(i * 3.414f, lim.toFloat()) / d
            }
        }
        shapeCache[Rect(r)] = res
        return res
    }

    /** "1px" for hairlines (≤2px), otherwise dp. */
    fun strokeW(sh: FloatArray): String = sh[4].toInt().let { if (it in 1..2) "${it}px" else "%.0fdp".format(max(1f, sh[3])) }

    // ---- standalone hairline / divider lines found in the screenshot (horizontal + vertical, ≤3px thick) ----
    class Hairline(val rect: Rect, val color: Int, val px: Int)
    private var hairShot: Bitmap? = null
    private var hairCache: List<Hairline> = emptyList()
    fun hairlines(): List<Hairline> {
        val s = shot ?: return emptyList()
        if (s === hairShot) return hairCache
        val out = ArrayList<Hairline>()
        runCatching {
            val W = s.width; val H = s.height; val T = 10
            val minRun = max((40 * d).toInt(), W / 5)
            val rows = Array(H) { IntArray(0) }
            fun row(y: Int): IntArray { if (rows[y].isEmpty()) { rows[y] = IntArray(W); s.getPixels(rows[y], 0, W, 0, y, W, 1) }; return rows[y] }
            // horizontal: row y (thickness 1..3) differs from y-1-ish above and below, uniform along x
            var y = 2
            while (y < H - 5) {
                var found = false
                for (th in 1..3) {
                    val a = row(y - 1); val c = row(y); val b = row(y + th)
                    var x = 0
                    while (x < W) {
                        val col = c[x]
                        var e = x
                        while (e < W && cdist(c[e], col) < T && cdist(c[e], a[e]) >= T && cdist(c[e], b[e]) >= T &&
                            (th == 1 || cdist(row(y + th - 1)[e], col) < T)) e++
                        if (e - x >= minRun) { out += Hairline(Rect(x, y, e, y + th), col, th); found = true }
                        x = max(e, x + 1)
                    }
                    if (found) break
                }
                y += if (found) 3 else 1
            }
            // vertical: same test on columns (sampled through rows already read)
            val minRunV = max((40 * d).toInt(), H / 12)
            var x = 2
            while (x < W - 5) {
                var yy = 0
                while (yy < H) {
                    val col = row(yy)[x]
                    var e = yy
                    while (e < H) { val r0 = row(e); if (cdist(r0[x], col) < T && cdist(r0[x], r0[x - 1]) >= T && cdist(r0[x], r0[x + 1]) >= T) e++ else break }
                    if (e - yy >= minRunV) out += Hairline(Rect(x, yy, x + 1, e), col, 1)
                    yy = max(e, yy + 1)
                }
                x++
            }
        }
        // drop runs that are just edges of big filled shapes: keep lines ≤3px between two similar backgrounds
        hairShot = s; hairCache = out.take(80)
        return hairCache
    }

    private fun pxPt(px: Float) = (px / d).let { if (abs(it - it.roundToInt()) < 0.05f) "${it.roundToInt()}" else "%.1f".format(it) }

    private val runsCache = HashMap<String, List<FontResolver.Run>>()
    /** Fonts that actually render this node's text: from Spec Inspector when integrated, else shaped here with the system default typeface. */
    private fun runsOf(a: NodeData): List<FontResolver.Run> {
        a.exact?.fonts?.takeIf { it.isNotEmpty() }?.let { return it }
        val txt = a.text?.takeIf { it.isNotBlank() && a.fontSp != null } ?: return emptyList()
        val w = a.exact?.weight ?: weightOf(a).takeIf { it > 0 } ?: 400
        val px = a.exact?.sizePx ?: (a.fontSp!! * d * svc.resources.configuration.fontScale)
        val key = "$w|${px.toInt()}|" + txt.take(64)
        return runsCache.getOrPut(key) {
            runCatching { FontResolver.resolve(txt, Paint().apply { textSize = px; typeface = Typeface.create(Typeface.DEFAULT, w, false) }) }.getOrNull() ?: emptyList()
        }
    }

    /** Font family for labels: resolved through the system fallback chain (exact names). */
    private fun familyStr(a: NodeData): String? {
        val runs = runsOf(a)
        if (runs.isNotEmpty()) return FontResolver.families(runs)
        a.exact?.takeIf { it.isText }?.let { return it.fontRes ?: it.family ?: "sans-serif" }
        if (a.fontSp == null) return null
        return guessFor(a.pkg, 400)?.let { "≈" + it.family }
    }

    private fun fontStr(a: NodeData): String? {
        a.exact?.takeIf { it.isText }?.let { e -> return "${pxPt(e.sizePx!!)}pt" + (e.weight?.let { " · $it" } ?: "") }
        val sp = a.fontSp ?: return null
        return fontLabel(sp, a.fontExact)
    }


    // ---- text clipping: glyph ink touching the top/bottom edge of a text node's bounds ----
    private val clipCache = HashMap<Rect, Int>()
    private var clipShot: Bitmap? = null
    /** 0 = ok, 1 = top clipped, 2 = bottom clipped, 3 = both. */
    fun clipOf(a: NodeData): Int {
        val s = shot ?: return 0
        if (a.kind != Kind.TEXT || a.text.isNullOrBlank() || isAppIcon(a)) return 0
        if (s !== clipShot) { clipCache.clear(); clipShot = s }
        clipCache[a.rect]?.let { return it }
        val r = a.rect
        val l = max(0, r.left); val rr = min(s.width, r.right); val t = max(0, r.top); val b = min(s.height, r.bottom)
        var res = 0
        if (rr - l >= 8 && b - t >= 6 && r.top >= 0 && r.bottom <= s.height) {
            val cs = colorsOf(r)
            if (cs != null && cdist(cs[0], cs[1]) >= 90) {
                val bg = cs[0]; val fg = cs[1]
                fun ink(p: Int) = cdist(p, bg) >= cdist(bg, fg) / 2 && cdist(p, fg) < cdist(p, bg)
                // count ink pixels in a row; reject rows that are borders/lines (long continuous runs)
                fun edgeInk(y: Int): Boolean {
                    var n = 0; var run = 0; var maxRun = 0
                    for (x in l until rr) { if (ink(s.getPixel(x, y))) { n++; run++; if (run > maxRun) maxRun = run } else run = 0 }
                    val w = rr - l
                    return n >= 3 && maxRun < w * 0.5f && n < w * 0.6f
                }
                // ink must reach the very edge row AND the adjacent rows (a glyph continuing past the bound)
                if (edgeInk(t) && edgeInk(t + 1)) res = res or 1
                if (edgeInk(b - 1) && edgeInk(b - 2)) res = res or 2
            }
        }
        clipCache[Rect(r)] = res
        return res
    }
    fun clipStr(c: Int) = when (c) { 1 -> "⚠ 文字被裁切(顶部)"; 2 -> "⚠ 文字被裁切(底部)"; 3 -> "⚠ 文字被裁切(上下)"; else -> null }
    private fun strokeStr(sh: FloatArray) = "描边 " + strokeW(sh) + " " + hex(sh[1].toInt())

    private val fontNameCache = HashMap<String, String?>()
    /** Inferred font family; loads app fonts on a worker thread, then redraws. */
    private fun fontName(pkg: String?, w: Int): String? {
        val key = (pkg ?: "") + "|" + w
        if (fontNameCache.containsKey(key)) return fontNameCache[key]
        fontNameCache[key] = "识别中…"
        Thread {
            val g = runCatching { FontNames.guess(context, pkg, if (w > 0) w else 400) }.getOrNull()
            val v = g?.let { "≈" + it.first + if (it.second) "（应用内置?）" else "（系统）" }
            post { fontNameCache[key] = v; layoutKey = null; invalidate() }
        }.start()
        return fontNameCache[key]
    }

    private fun ratio(w: Int, h: Int): String {
        if (w <= 0 || h <= 0) return "-"
        val v = w.toFloat() / h
        val common = listOf(1 to 1, 4 to 3, 3 to 4, 16 to 9, 9 to 16, 3 to 2, 2 to 3, 2 to 1, 1 to 2, 21 to 9, 5 to 4, 4 to 5)
        common.firstOrNull { abs(v - it.first.toFloat() / it.second) / v < 0.03f }?.let { return "${it.first}:${it.second}" }
        return if (v >= 1) "%.2f:1".format(v) else "1:%.2f".format(1 / v)
    }

    private fun radiusStr(sh: FloatArray?, r: Rect): String? {
        if (sh == null || sh[0] < 0) return null
        val full = sh[0] * d >= min(r.width(), r.height()) / 2f - 1
        return if (full) "R≈全圆" else "R≈%.0f".format(sh[0])
    }

    // ---------------- full-screen annotation ----------------

    private class Label(val lines: List<String>, val color: Int, val elem: Rect, val swatch: Int?, var box: RectF = RectF(), var leader: Boolean = false)
    private class GapMark(val vertical: Boolean, val pos: Float, val a: Float, val b: Float, val box: RectF, val text: String)

    private var layoutKey: String? = null
    private var placed: List<Label> = emptyList()
    private var gapMarks: List<GapMark> = emptyList()

    private val padX get() = dpf(5f)
    private val padY get() = dpf(3f)
    private val lineH get() = dpf(13f)
    private val swW get() = dpf(11f)

    /** Default launcher package (home screen). */
    private val homePkg by lazy {
        runCatching { svc.packageManager.resolveActivity(android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName }.getOrNull()
    }
    /** App launcher icons (home screen cells, icon views): their sampled "text colour" is the icon art, so no colour label. */
    private fun isAppIcon(a: NodeData): Boolean {
        val cls = a.cls
        if (cls.contains("BubbleTextView") || cls.contains("Icon", ignoreCase = false)) return true
        val pkg = a.pkg ?: return false
        if (pkg == homePkg || pkg.contains("launcher")) {
            // launcher cell: label text with an icon above it -> much taller than one text line
            val lineGuess = (a.fontSp ?: 14f) * d * 1.6f
            return a.rect.height() > lineGuess * 1.8f || a.kind == Kind.IMAGE
        }
        return false
    }

    private fun labelParts(a: NodeData): Pair<List<String>, Int?> {
        val r = a.rect
        val p = mutableListOf<String>()
        var sw: Int? = null
        val size = "${toDp(r.width())}×${toDp(r.height())}"
        when (a.kind) {
            Kind.TEXT -> {
                clipStr(clipOf(a))?.let { p += it }
                if (on("family")) familyStr(a)?.let { p += it }
                if (on("font")) fontStr(a)?.let { p += it }
                if (on("color") && !isAppIcon(a)) colorsOf(r)?.let { p += hex(it[1]); sw = it[1] }
                if (on("size")) p += size
            }
            Kind.BUTTON -> {
                if (on("size")) p += size
                if (on("font")) fontStr(a)?.let { p += it }
            }
            Kind.IMAGE -> {
                if (on("size")) { p += size; p += ratio(r.width(), r.height()) }
            }
            Kind.CONTAINER -> if (on("size")) p += size
        }
        // 描边 applies to text views too (e.g. TextView with an outlined background); radius stays for non-text
        if ((on("radius") && a.kind != Kind.TEXT) || on("stroke")) {
            val sh = shapeOf(r)
            if (on("radius") && a.kind != Kind.TEXT) radiusStr(sh, r)?.let { p += it }
            if (on("stroke") && sh != null && sh[1] != 0f) p += strokeStr(sh)
        }
        return p to sw
    }

    /** Greedy wrap of parts into lines no wider than maxW. */
    private fun wrap(parts: List<String>, maxW: Float, firstIndent: Float): List<String> {
        val out = mutableListOf<String>()
        var cur = ""
        for (part in parts) {
            val cand = if (cur.isEmpty()) part else "$cur · $part"
            val indent = if (out.isEmpty()) firstIndent else 0f
            if (cur.isNotEmpty() && badgeTx.measureText(cand) + indent > maxW) { out += cur; cur = part } else cur = cand
        }
        if (cur.isNotEmpty()) out += cur
        // hard-wrap single overlong parts
        return out.flatMap { l ->
            if (badgeTx.measureText(l) <= maxW) listOf(l) else {
                val res = mutableListOf<String>(); var s = ""
                for (ch in l) { if (badgeTx.measureText(s + ch) > maxW) { res += s; s = "" }; s += ch }
                if (s.isNotEmpty()) res += s; res
            }
        }
    }

    private fun layoutAnnots(list: List<NodeData>, W: Float, H: Float) {
        val m = dpf(4f)
        val top = dpf(2f)
        val screen = RectF(m, top, W - m, H - m)
        val taken = mutableListOf<RectF>()
        val gm = mutableListOf<GapMark>()
        // 2. element labels
        val maxW = screen.width() - padX * 2
        val labels = list.mapNotNull { a ->
            val (parts, sw) = labelParts(a)
            if (parts.isEmpty()) null else Label(wrap(parts, maxW, if (sw != null) swW else 0f), if (clipOf(a) != 0) 0xFFF04438.toInt() else Pal.of(a.kind), a.rect, sw)
        }.sortedWith(compareBy({ it.elem.top }, { it.elem.left }))
        val g = dpf(2f)
        val out = mutableListOf<Label>()
        for (lb in labels) {
            val tw = lb.lines.maxOf { badgeTx.measureText(it) } + (if (lb.swatch != null) swW else 0f)
            val w = tw + padX * 2; val h = lb.lines.size * lineH + padY * 2
            val r = lb.elem
            fun fits(x: Float, y: Float): RectF? {
                val xx = x.coerceIn(screen.left, max(screen.left, screen.right - w))
                val b = RectF(xx, y, xx + w, y + h)
                if (b.top < screen.top || b.bottom > screen.bottom) return null
                return if (taken.none { RectF.intersects(it, b) }) b else null
            }
            val cands = ArrayList<Pair<Float, Float>>()
            val L = r.left.toFloat(); val T = r.top.toFloat(); val R = r.right.toFloat(); val B = r.bottom.toFloat()
            cands += L to T - h - g          // above
            cands += L to B + g              // below
            cands += R + g to T              // right
            cands += L - w - g to T          // left
            cands += R - w to T - h - g      // above, right-aligned
            cands += R - w to B + g          // below, right-aligned
            cands += L + g to T + g          // inside top-left (big elements)
            for (k in 1..8) {
                val s = k * (h + g)
                cands += L to T - h - g - s
                cands += L to B + g + s
                cands += R + g to T + s
                cands += L - w - g to T + s
                cands += R + g to T - s
                cands += L - w - g to T - s
                cands += L + k * dpf(24f) to T - h - g
                cands += L + k * dpf(24f) to B + g
            }
            var found: RectF? = null
            for ((x, y) in cands) { found = fits(x, y); if (found != null) break }
            if (found == null) {
                // last resort: nearest free slot on a fine scan of the whole screen
                val cx = r.exactCenterX(); val cy = r.exactCenterY()
                var best: RectF? = null; var bd = Float.MAX_VALUE
                val st = dpf(6f)
                var y = screen.top
                while (y + h <= screen.bottom) {
                    var x = screen.left
                    while (x + w <= screen.right) {
                        val dd = hypot(x + w / 2 - cx, y + h / 2 - cy)
                        if (dd < bd) { val b = RectF(x, y, x + w, y + h); if (taken.none { RectF.intersects(it, b) }) { best = b; bd = dd } }
                        x += st
                    }
                    y += st
                }
                found = best
            }
            val b = found ?: continue // screen completely full
            lb.box = b
            val el = RectF(r)
            lb.leader = !RectF.intersects(RectF(b.left - g * 2, b.top - g * 2, b.right + g * 2, b.bottom + g * 2), el)
            taken += b; out += lb
        }
        placed = out
        // 1. spacing marks (fixed to their gap, never moved far)
        if (on("space")) gaps?.forEach { g ->
            val t = toDp(g.b - g.a)
            if (t == "0") return@forEach
            val w = badgeTx.measureText(t) + dpf(8f); val h = dpf(13f)
            val mid = (g.a + g.b) / 2f
            val offs = floatArrayOf(0f, 1f, -1f, 2f, -2f, 3f, -3f)
            for (o in offs) {
                val b = if (g.vertical) { val cx = g.pos + o * (w + dpf(2f)); RectF(cx - w / 2, mid - h / 2, cx + w / 2, mid + h / 2) }
                        else { val cy = g.pos + o * (h + dpf(2f)); RectF(mid - w / 2, cy - h / 2, mid + w / 2, cy + h / 2) }
                if (b.left < screen.left || b.right > screen.right || b.top < screen.top || b.bottom > screen.bottom) continue
                if (taken.any { RectF.intersects(it, b) }) continue
                taken += b; gm += GapMark(g.vertical, g.pos.toFloat(), g.a.toFloat(), g.b.toFloat(), b, t); break
            }
        }
        
        gapMarks = gm
        var ov = 0
        for (i in taken.indices) for (j in i + 1 until taken.size) if (RectF.intersects(taken[i], taken[j])) ov++
        val off = taken.count { it.left < 0 || it.top < 0 || it.right > W || it.bottom > H }
    }

    private fun drawAnnots(c: Canvas, list: List<NodeData>, W: Float, H: Float) {
        val key = "${System.identityHashCode(list)}|${QaService.CHIPS.joinToString("") { if (on(it.first)) "1" else "0" }}|${shot != null}|$W|$H"
        if (key != layoutKey) { layoutAnnots(list, W, H); layoutKey = key }
        // element outlines
        for (a in list) {
            outlineP.color = if (clipOf(a) != 0) 0xFFF04438.toInt() else (Pal.of(a.kind) and 0xFFFFFF) or 0xB0000000.toInt()
            c.drawRect(a.rect, outlineP)
        }
        // standalone 1–3px divider lines (描边 chip)
        if (on("stroke")) for (hl in hairlines()) {
            // skip lines that are just the (already labelled) stroke edge of an element
            val r0 = hl.rect
            if (list.any { e -> (abs(e.rect.top - r0.top) <= 2 || abs(e.rect.bottom - r0.bottom) <= 2 || abs(e.rect.left - r0.left) <= 2 || abs(e.rect.right - r0.right) <= 2) &&
                        r0.left >= e.rect.left - 2 && r0.right <= e.rect.right + 2 && r0.top >= e.rect.top - 2 && r0.bottom <= e.rect.bottom + 2 &&
                        shapeOf(e.rect)?.let { it[1] != 0f } == true }) continue
            outlineP.color = Pal.GAP; c.drawRect(RectF(hl.rect).apply { inset(-dpf(1f), -dpf(1f)) }, outlineP)
            val t = "${hl.px}px " + hex(hl.color)
            val bw = badgeTx.measureText(t) + dpf(10f); val bh = dpf(14f)
            val bx = (hl.rect.left + dpf(4f)).coerceAtMost(W - bw); val by = (hl.rect.top - bh - dpf(1f)).coerceAtLeast(0f)
            badgeBg.color = Pal.GAP; c.drawRoundRect(bx, by, bx + bw, by + bh, bh / 2, bh / 2, badgeBg)
            c.drawText(t, bx + dpf(5f), by + bh / 2 + dpf(3.5f), badgeTx)
        }
        // spacing marks
        for (gmk in gapMarks) {
            measure.color = Pal.MEASURE
            val cp = dpf(3f)
            if (gmk.vertical) {
                c.drawLine(gmk.pos, gmk.a, gmk.pos, gmk.b, measure)
                c.drawLine(gmk.pos - cp, gmk.a, gmk.pos + cp, gmk.a, measure); c.drawLine(gmk.pos - cp, gmk.b, gmk.pos + cp, gmk.b, measure)
            } else {
                c.drawLine(gmk.a, gmk.pos, gmk.b, gmk.pos, measure)
                c.drawLine(gmk.a, gmk.pos - cp, gmk.a, gmk.pos + cp, measure); c.drawLine(gmk.b, gmk.pos - cp, gmk.b, gmk.pos + cp, measure)
            }
        }
        measure.color = Pal.MEASURE
        // leader lines below badges
        for (lb in placed) if (lb.leader) {
            val b = lb.box; val r = lb.elem
            val ex = lb.box.centerX().coerceIn(r.left.toFloat(), r.right.toFloat())
            val ey = lb.box.centerY().coerceIn(r.top.toFloat(), r.bottom.toFloat())
            val bx = ex.coerceIn(b.left, b.right); val by = ey.coerceIn(b.top, b.bottom)
            tmp.color = lb.color; tmp.strokeWidth = dpf(1f); tmp.style = Paint.Style.STROKE
            c.drawLine(bx, by, ex, ey, tmp)
            tmp.style = Paint.Style.FILL
            c.drawCircle(ex, ey, dpf(2f), tmp)
        }
        for (gmk in gapMarks) {
            badgeBg.color = Pal.MEASURE
            c.drawRoundRect(gmk.box, dpf(6.5f), dpf(6.5f), badgeBg)
            c.drawText(gmk.text, gmk.box.centerX() - badgeTx.measureText(gmk.text) / 2, gmk.box.centerY() + dpf(3.5f), badgeTx)
        }
        for (lb in placed) drawBadge(c, lb)
    }

    private fun drawBadge(c: Canvas, lb: Label) {
        val b = lb.box
        badgeBg.color = lb.color
        c.drawRoundRect(b, dpf(6f), dpf(6f), badgeBg)
        var x0 = b.left + padX
        lb.lines.forEachIndexed { i, s ->
            val y = b.top + padY + lineH * i
            var x = x0
            if (i == 0 && lb.swatch != null) {
                tmp.style = Paint.Style.FILL; tmp.color = lb.swatch
                val cx = x + dpf(4f); val cy = y + lineH / 2
                c.drawCircle(cx, cy, dpf(4f), tmp); c.drawCircle(cx, cy, dpf(4f), swatchStroke)
                x += swW
            }
            c.drawText(s, x, y + dpf(10f), badgeTx)
        }
    }

    // ---------------- inspect ----------------

    private fun hint(c: Canvas, s: String) {
        val w = hintTx.measureText(s) + dpf(32f); val h = dpf(36f)
        val l = (width - w) / 2; val t = dpf(56f)
        c.drawRoundRect(l, t, l + w, t + h, h / 2, h / 2, cardShadow)
        c.drawRoundRect(l, t, l + w, t + h, h / 2, h / 2, cardBg)
        c.drawText(s, l + dpf(16f), t + h / 2 + dpf(4.5f), hintTx)
    }

    private fun badge(c: Canvas, s: String, cx: Float, cy: Float, col: Int = Pal.MEASURE) {
        val w = badgeTx.measureText(s) + dpf(10f); val h = dpf(16f)
        badgeBg.color = col
        c.drawRoundRect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2, h / 2, h / 2, badgeBg)
        c.drawText(s, cx - w / 2 + dpf(5f), cy + dpf(3.5f), badgeTx)
    }

    private fun vLine(c: Canvas, x: Float, y1: Int, y2: Int) {
        if (y2 - y1 <= 0) return
        val cap = dpf(4f)
        c.drawLine(x, y1.toFloat(), x, y2.toFloat(), measure)
        c.drawLine(x - cap, y1.toFloat(), x + cap, y1.toFloat(), measure)
        c.drawLine(x - cap, y2.toFloat(), x + cap, y2.toFloat(), measure)
        badge(c, toDp(y2 - y1), x, (y1 + y2) / 2f)
    }
    private fun hLine(c: Canvas, y: Float, x1: Int, x2: Int) {
        if (x2 - x1 <= 0) return
        val cap = dpf(4f)
        c.drawLine(x1.toFloat(), y, x2.toFloat(), y, measure)
        c.drawLine(x1.toFloat(), y - cap, x1.toFloat(), y + cap, measure)
        c.drawLine(x2.toFloat(), y - cap, x2.toFloat(), y + cap, measure)
        badge(c, toDp(x2 - x1), (x1 + x2) / 2f, y)
    }

    private fun sibGaps(r: Rect, sibs: List<Rect>): IntArray {
        val g = intArrayOf(-1, -1, -1, -1) // top, bottom, left, right
        for (s in sibs) {
            val hOverlap = s.right > r.left && s.left < r.right
            val vOverlap = s.bottom > r.top && s.top < r.bottom
            if (hOverlap && s.bottom <= r.top) g[0] = minPos(g[0], r.top - s.bottom)
            if (hOverlap && s.top >= r.bottom) g[1] = minPos(g[1], s.top - r.bottom)
            if (vOverlap && s.right <= r.left) g[2] = minPos(g[2], r.left - s.right)
            if (vOverlap && s.left >= r.right) g[3] = minPos(g[3], s.left - r.right)
        }
        return g
    }
    private fun minPos(a: Int, b: Int) = if (a < 0) b else min(a, b)

    private fun selBox(c: Canvas, r: Rect, stroke: Paint, fill: Paint) {
        c.drawRect(r, fill); c.drawRect(r, stroke)
        val hs = dpf(3f)
        for (x in intArrayOf(r.left, r.right)) for (y in intArrayOf(r.top, r.bottom)) {
            c.drawRect(x - hs, y - hs, x + hs, y + hs, handleFill)
            c.drawRect(x - hs, y - hs, x + hs, y + hs, stroke)
        }
        val s = "${toDp(r.width())} × ${toDp(r.height())}"
        badge(c, s, r.exactCenterX(), (r.bottom + dpf(12f)), stroke.color)
    }

    private fun kindName(k: Kind) = when (k) { Kind.TEXT -> "文本"; Kind.BUTTON -> "按钮"; Kind.IMAGE -> "图片/图标"; Kind.CONTAINER -> "容器" }

    // ---- font guess (async, cached per package+weight) ----
    private val guessCache = HashMap<String, FontNames.Guess?>()
    private fun guessFor(pkg: String?, w: Int): FontNames.Guess? {
        val key = (pkg ?: "") + "|" + w
        if (guessCache.containsKey(key)) return guessCache[key]
        guessCache[key] = null
        Thread {
            val g = runCatching { FontNames.guessFull(context, pkg, if (w > 0) w else 400) }.getOrNull()
            post { guessCache[key] = g; layoutKey = null; invalidate() }
        }.start()
        return null
    }

    private var cardExact = false
    private enum class Ic { SIZE, LINE, LETTER, WEIGHT, CODE, COLOR, BOX, RADIUS, STROKE, VGAP, HGAP, CLIP }
    private class Cell(val icon: Ic, val value: String, val swatch: Int? = null, val dim: Boolean = false)

    private fun pt(px: Float) = px / d
    private fun ptStr(px: Float) = (pt(px)).let { if (abs(it - it.roundToInt()) < 0.05f) "${it.roundToInt()}" else "%.1f".format(it) }

    private fun drawInspect(c: Canvas, W: Float, H: Float) {
        val a = selA ?: return
        val r = a.rect
        measure.color = Pal.MEASURE
        cardExact = false
        val cells = mutableListOf<Cell>()
        var title: String; var sub: String
        var stack: String? = null; var preview: String? = null; var tf: Typeface? = null
        var footer: String
        val b = selB
        if (b == null) {
            val g = sibGaps(r, a.siblings)
            val cx = r.exactCenterX(); val cy = r.exactCenterY()
            if (on("space")) {
                a.parent?.let { p -> c.drawRect(p, dash) }
                if (g[0] >= 0) vLine(c, cx, r.top - g[0], r.top) else a.parent?.let { vLine(c, cx, it.top, r.top) }
                if (g[1] >= 0) vLine(c, cx, r.bottom, r.bottom + g[1]) else a.parent?.let { vLine(c, cx, r.bottom, it.bottom) }
                if (g[2] >= 0) hLine(c, cy, r.left - g[2], r.left) else a.parent?.let { hLine(c, cy, it.left, r.left) }
                if (g[3] >= 0) hLine(c, cy, r.right, r.right + g[3]) else a.parent?.let { hLine(c, cy, r.right, it.right) }
            }
            selBox(c, r, selStroke, selFill)
            val cls = a.cls.substringAfterLast('.')
            val isText = a.fontSp != null && !a.text.isNullOrBlank()
            val cs = colorsOf(r)
            val size = Cell(Ic.BOX, "${ptStr(r.width().toFloat())}×${ptStr(r.height().toFloat())}pt")
            val ex = a.exact
            cardExact = ex != null
            if (ex != null && ex.isText) {
                if (on("family")) {
                    val runs = runsOf(a)
                    title = if (runs.isNotEmpty()) FontResolver.families(runs) else ex.fontRes ?: ex.family ?: "sans-serif"
                    sub = (if (runs.isNotEmpty()) FontResolver.detail(runs) + " · " else "") + (ex.fontRes?.let { "res/font/$it" } ?: (ex.family ?: "")) + (if (ex.compose) " · Compose" else "")
                } else { title = cls; sub = kindName(a.kind) }
                cells += Cell(Ic.SIZE, "${pxPt(ex.sizePx!!)}pt")
                (ex.weight ?: weightOf(a).takeIf { it > 0 })?.let { w -> cells += Cell(Ic.WEIGHT, "${ex.weightName ?: weightName(w)} $w" + if (ex.weight == null) " (估)" else "") }
                ex.lineHeightPx?.let { cells += Cell(Ic.LINE, pxPt(it) + "pt") }
                if (ex.letterEm != null) cells += Cell(Ic.LETTER, if (ex.letterEm == 0f) "0" else "%.2fem · %spt".format(ex.letterEm, pxPt(ex.letterPx ?: 0f)))
                else ex.letterPx?.let { cells += Cell(Ic.LETTER, pxPt(it) + "pt") }
                ex.color?.let { cells += Cell(Ic.COLOR, hex(it), it) }
                cells += size
            } else if (ex != null) {
                title = cls
                sub = kindName(a.kind)
                cells += size
                ex.radiusPx?.let { cells += Cell(Ic.RADIUS, pxPt(it) + "pt") }
                ex.strokeW?.let { cells += Cell(Ic.STROKE, pxPt(it) + "pt", ex.stroke) }
                ex.bg?.let { cells += Cell(Ic.COLOR, hex(it), it) }
            } else if (isText) {
                if (on("family")) {
                    val runs = runsOf(a)
                    val gs = guessFor(a.pkg, 400)
                    val src = if (gs?.fromApp == true) "可能使用应用内置字体" else "系统默认字体"
                    if (runs.isNotEmpty()) { title = FontResolver.families(runs); sub = FontResolver.detail(runs) + " · " + src }
                    else { title = gs?.let { "≈" + it.family } ?: cls; sub = if (gs == null) "识别字体中…" else "$src · 推测" }
                } else { title = cls; sub = kindName(a.kind) }
                cells += Cell(Ic.SIZE, fontLabel(a.fontSp!!, a.fontExact))
                weightOf(a).let { w -> cells += if (w > 0) Cell(Ic.WEIGHT, "≈${weightName(w)} $w") else Cell(Ic.WEIGHT, "识别中…", dim = true) }
                a.lineH?.let { cells += Cell(Ic.LINE, (if (a.lineExact) "" else "≈") + ptStr(it) + "pt") }
                a.letterSp?.let { cells += Cell(Ic.LETTER, if (abs(it) < 0.3f * d) "≈0" else "≈" + ptStr(it) + "pt") }
                if (cs != null) cells += Cell(Ic.COLOR, hex(cs[1]), cs[1])
                cells += size
            } else {
                title = cls
                sub = kindName(a.kind)
                cells += size
                val sh = shapeOf(r)
                if (sh != null) radiusStr(sh, r)?.let { cells += Cell(Ic.RADIUS, it.removePrefix("R").let { v -> if (v.endsWith("全圆")) v else v + "pt" }) }
                if (sh != null && sh[1] != 0f) cells += Cell(Ic.STROKE, strokeW(sh), sh[1].toInt())
                if (cs != null) cells += Cell(Ic.COLOR, hex(cs[0]), cs[0])
            }
            clipStr(clipOf(a))?.let { cells.add(0, Cell(Ic.CLIP, it.removePrefix("⚠ "))) }
            footer = ""
        } else {
            val s = b.rect
            selBox(c, s, selB_Stroke, selB_Fill)
            selBox(c, r, selStroke, selFill)
            val ox1 = max(r.left, s.left); val ox2 = min(r.right, s.right)
            val vx = if (ox2 > ox1) (ox1 + ox2) / 2f else (r.exactCenterX() + s.exactCenterX()) / 2
            val oy1 = max(r.top, s.top); val oy2 = min(r.bottom, s.bottom)
            val hy = if (oy2 > oy1) (oy1 + oy2) / 2f else (r.exactCenterY() + s.exactCenterY()) / 2
            var dv = 0; var dh = 0
            when {
                s.top >= r.bottom -> { dv = s.top - r.bottom; vLine(c, vx, r.bottom, s.top) }
                r.top >= s.bottom -> { dv = r.top - s.bottom; vLine(c, vx, s.bottom, r.top) }
            }
            when {
                s.left >= r.right -> { dh = s.left - r.right; hLine(c, hy, r.right, s.left) }
                r.left >= s.right -> { dh = r.left - s.right; hLine(c, hy, s.right, r.left) }
            }
            title = "两元素间距"
            sub = "蓝 → 橙 · 再点一次重新选择"
            cells += Cell(Ic.VGAP, "${ptStr(dv.toFloat())}pt")
            cells += Cell(Ic.HGAP, "${ptStr(dh.toFloat())}pt")
            cells += Cell(Ic.CODE, b.cls.substringAfterLast('.'))
            cells += Cell(Ic.VGAP, "上 ${toDp(s.top - r.top)} 下 ${toDp(s.bottom - r.bottom)}")
            cells += Cell(Ic.HGAP, "左 ${toDp(s.left - r.left)} 右 ${toDp(s.right - r.right)}")
            cells += Cell(Ic.BOX, "${toDp(s.width())}×${toDp(s.height())}")
            footer = ""
        }
        // font row (only when 字体 is on): resolved family names + per-script detail; no header/subtitle
        var fontLine: String? = null; var fontDetail: String? = null
        if (b == null && on("family") && a.fontSp != null) {
            val runs = runsOf(a)
            if (runs.isNotEmpty()) { fontLine = FontResolver.families(runs); fontDetail = FontResolver.detail(runs).takeIf { it.contains("·") } }
            else fontLine = familyStr(a)
        }
        // keep-out zone: selected element(s) + their size badges / spacing labels
        val keep = RectF(r)
        if (b != null) keep.union(RectF(b.rect))
        fun withBadge(x: Rect) { val bw = dpf(44f); keep.union(x.exactCenterX() - bw, x.top.toFloat(), x.exactCenterX() + bw, x.bottom + dpf(23f)) }
        withBadge(r); if (b != null) withBadge(b.rect)
        keep.inset(-dpf(4f), -dpf(4f))
        cardTitleS = title; cardSubS = sub
        drawCard(c, fontLine, fontDetail, b == null && a.exact != null, cells, keep, W, H)
    }

    private val cardBgDark = Paint(aa).apply { color = Color.parseColor("#F22C2C2E") }
    private val cardTitle = Paint(aa).apply { color = Color.WHITE; textSize = dpf(19f); typeface = Typeface.create(Typeface.DEFAULT, 700, false) }
    private val cardSub = Paint(aa).apply { color = Color.parseColor("#8E8E93"); textSize = dpf(13f) }
    private val cardVal = Paint(aa).apply { color = Color.parseColor("#F5F5F7"); textSize = dpf(12.5f); fontFeatureSettings = "tnum" }
    private val cardIcon = Paint(aa).apply { color = Color.parseColor("#AEAEB2"); style = Paint.Style.STROKE; strokeWidth = dpf(1.6f); strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val cardIconTx = Paint(aa).apply { color = Color.parseColor("#AEAEB2"); textSize = dpf(15f); typeface = Typeface.create(Typeface.DEFAULT, 600, false) }
    private val cardDiv = Paint().apply { color = Color.parseColor("#14FFFFFF") }
    private val prevBg = Paint(aa).apply { color = Color.parseColor("#3A3A3C") }
    private val prevTx = Paint(aa).apply { color = Color.WHITE; textSize = dpf(24f) }

    /** Greedy wrap without ellipsis: prefer breaking after spaces, fall back to per-char. */
    private fun wrap(p: Paint, s: String, firstW: Float, restW: Float): List<String> {
        val out = ArrayList<String>(); var rest = s.trim()
        while (rest.isNotEmpty()) {
            val mw = if (out.isEmpty()) firstW else restW
            if (p.measureText(rest) <= mw) { out += rest; break }
            var e = p.breakText(rest, true, mw, null).coerceAtLeast(1)
            val sp = rest.lastIndexOf(' ', e)
            if (sp > 0) e = sp
            out += rest.substring(0, e).trimEnd(); rest = rest.substring(e).trimStart()
        }
        return out.ifEmpty { listOf(s) }
    }

    private fun fit(p: Paint, s: String, maxW: Float): String {
        if (p.measureText(s) <= maxW) return s
        var e = s.length
        while (e > 1 && p.measureText(s, 0, e) + p.measureText("…") > maxW) e--
        return s.substring(0, e) + "…"
    }

    /** Outline icons (Ionicons look) drawn with paths; box is a ~18dp square at (x, cy). */
    private fun drawIcon(c: Canvas, ic: Ic, x: Float, cy: Float, swatch: Int?) {
        val u = dpf(1f); val p = cardIcon; val t = cardIconTx
        fun txt(s: String, size: Float) { t.textSize = dpf(size); c.drawText(s, x, cy + t.textSize * 0.36f, t) }
        when (ic) {
            Ic.SIZE -> { txt("T", 16f); t.textSize = dpf(11f); c.drawText("T", x + 9 * u, cy + 4 * u, t) }
            Ic.CLIP -> txt(" !", 15f)
            Ic.LINE -> {
                c.drawLine(x + 3 * u, cy - 7 * u, x + 3 * u, cy + 7 * u, p)
                c.drawLine(x + 1 * u, cy - 5 * u, x + 3 * u, cy - 7 * u, p); c.drawLine(x + 5 * u, cy - 5 * u, x + 3 * u, cy - 7 * u, p)
                c.drawLine(x + 1 * u, cy + 5 * u, x + 3 * u, cy + 7 * u, p); c.drawLine(x + 5 * u, cy + 5 * u, x + 3 * u, cy + 7 * u, p)
                for (k in -1..1) c.drawLine(x + 9 * u, cy + k * 5 * u, x + 17 * u, cy + k * 5 * u, p)
            }
            Ic.LETTER -> {
                c.drawLine(x + 1 * u, cy - 7 * u, x + 1 * u, cy + 7 * u, p); c.drawLine(x + 17 * u, cy - 7 * u, x + 17 * u, cy + 7 * u, p)
                t.textSize = dpf(12f); c.drawText("A", x + 9 * u - t.measureText("A") / 2, cy + 4.5f * u, t)
            }
            Ic.WEIGHT -> txt("B", 17f)
            Ic.CODE -> {
                c.drawLine(x + 6 * u, cy - 6 * u, x + 1 * u, cy, p); c.drawLine(x + 1 * u, cy, x + 6 * u, cy + 6 * u, p)
                c.drawLine(x + 12 * u, cy - 6 * u, x + 17 * u, cy, p); c.drawLine(x + 17 * u, cy, x + 12 * u, cy + 6 * u, p)
            }
            Ic.COLOR, Ic.STROKE -> {
                val rr = RectF(x + 1 * u, cy - 7 * u, x + 15 * u, cy + 7 * u)
                if (ic == Ic.COLOR && swatch != null) { tmp.style = Paint.Style.FILL; tmp.color = swatch; c.drawRoundRect(rr, 3 * u, 3 * u, tmp) }
                if (ic == Ic.STROKE && swatch != null) { val sp = Paint(p).apply { color = swatch; strokeWidth = 2.5f * u }; c.drawRoundRect(rr, 3 * u, 3 * u, sp) }
                c.drawRoundRect(rr, 3 * u, 3 * u, Paint(p).apply { strokeWidth = u; color = Color.parseColor("#8E8E93") })
            }
            Ic.BOX -> {
                c.drawRect(x + 1 * u, cy - 7 * u, x + 15 * u, cy + 7 * u, Paint(p).apply { pathEffect = DashPathEffect(floatArrayOf(3 * u, 2.5f * u), 0f) })
            }
            Ic.RADIUS -> {
                val path = Path().apply { moveTo(x + 1 * u, cy + 8 * u); lineTo(x + 1 * u, cy); quadTo(x + 1 * u, cy - 7 * u, x + 8 * u, cy - 7 * u); lineTo(x + 16 * u, cy - 7 * u) }
                c.drawPath(path, p)
            }
            Ic.VGAP -> {
                c.drawLine(x + 2 * u, cy - 7 * u, x + 16 * u, cy - 7 * u, p); c.drawLine(x + 2 * u, cy + 7 * u, x + 16 * u, cy + 7 * u, p)
                c.drawLine(x + 9 * u, cy - 4 * u, x + 9 * u, cy + 4 * u, p)
            }
            Ic.HGAP -> {
                c.drawLine(x + 1 * u, cy - 7 * u, x + 1 * u, cy + 7 * u, p); c.drawLine(x + 17 * u, cy - 7 * u, x + 17 * u, cy + 7 * u, p)
                c.drawLine(x + 5 * u, cy, x + 13 * u, cy, p)
            }
        }
    }

    private fun drawLayers(c: Canvas, x: Float, cy: Float) {
        val u = dpf(1f)
        val top = Path().apply { moveTo(x + 9 * u, cy - 8 * u); lineTo(x + 17 * u, cy - 4 * u); lineTo(x + 9 * u, cy); lineTo(x + 1 * u, cy - 4 * u); close() }
        c.drawPath(top, cardIcon)
        val mid = Path().apply { moveTo(x + 1 * u, cy); lineTo(x + 9 * u, cy + 4 * u); lineTo(x + 17 * u, cy) }
        c.drawPath(mid, cardIcon)
        val bot = Path().apply { moveTo(x + 1 * u, cy + 4 * u); lineTo(x + 9 * u, cy + 8 * u); lineTo(x + 17 * u, cy + 4 * u) }
        c.drawPath(bot, cardIcon)
    }

    /** Dark popover card with a pointer toward the element, kept on-screen. */
    // Same visual language as the floating panel (QaService): #EB1C1C1E, 22dp radius, 1dp #14FFFFFF hairline,
    // 16dp padding, 28dp round close button (#B33A3A3C, ic_close at 70%).
    private val panelBg = Paint(aa).apply { color = Color.parseColor("#FF2C2C2C") }
    private val panelLine = Paint(aa).apply { color = Color.parseColor("#FF444444"); style = Paint.Style.STROKE; strokeWidth = dpf(1f) }
    private val closeBg = Paint(aa).apply { color = Color.parseColor("#24FFFFFF") }
    private val closeIcon by lazy { context.getDrawable(R.drawable.ic_close_sm)!!.mutate().apply { alpha = (Hdr.CLOSE_ALPHA * 255).toInt() } }
    private val dimTx = Paint(aa).apply { color = Color.parseColor("#8E8E93"); textSize = dpf(11.5f) }

    private val keyTx = Paint(aa).apply { color = Color.parseColor("#B3B3B3"); textSize = dpf(11f) }
    private val codeTx = Paint(aa).apply { color = Color.WHITE; textSize = dpf(11.5f); typeface = mono }
    private val hdSub by lazy { Paint(aa).apply { color = Hdr.SUB_COLOR; textSize = Hdr.SUB_SP * d * svc.resources.configuration.fontScale } }
    private val hdTx = Paint(aa).apply { color = Hdr.TITLE_COLOR; textSize = Hdr.TITLE_SP * svc.resources.displayMetrics.scaledDensity; typeface = Typeface.create(Typeface.DEFAULT, 600, false) }
    private val copyP = Paint(aa).apply { color = Color.parseColor("#7A7A7A"); style = Paint.Style.STROKE; strokeWidth = dpf(1.2f) }
    private val DIMROW = 0x00123456
    private fun keyName(ic: Ic) = when (ic) {
        Ic.SIZE -> "字号"; Ic.LINE -> "行高"; Ic.LETTER -> "字距"; Ic.WEIGHT -> "字重"; Ic.CODE -> "类名"; Ic.COLOR -> "颜色"
        Ic.BOX -> "尺寸"; Ic.RADIUS -> "圆角"; Ic.STROKE -> "描边"; Ic.VGAP -> "垂直"; Ic.HGAP -> "水平"; Ic.CLIP -> "裁切"
    }
    private fun copyIcon(c: Canvas, x: Float, cy: Float) {
        val u = dpf(1f)
        c.drawRoundRect(x + 3 * u, cy - 5 * u, x + 11 * u, cy + 3 * u, 1.5f * u, 1.5f * u, copyP)
        c.drawRoundRect(x, cy - 2 * u, x + 8 * u, cy + 6 * u, 1.5f * u, 1.5f * u, copyP)
    }

    /** Dev Mode style inspect card: layer header, divider, key/value property rows with code-like values. */
    private fun drawCard(c: Canvas, fontLine: String?, fontDetail: String?, exact: Boolean, cells: List<Cell>, anchor: RectF, W: Float, H: Float) {
        val pad = dpf(16f); val rowH = dpf(28f); val headH = dpf(Hdr.H.toFloat()); val closeS = dpf(Hdr.CLOSE.toFloat()) // 与面板关闭按钮一致：40dp 触控区、6dp 内边距、无底、70% 白
        val w = min(dpf(264f), W - dpf(16f))
        val keyW = dpf(56f); val valX = pad + keyW
        val valW = w - valX - pad - dpf(20f)
        val rows = ArrayList<Triple<String, String, Int?>>() // third: swatch color, DIMROW = dim text
        if (fontLine != null) {
            wrap(codeTx, fontLine, valW, valW).forEachIndexed { i, l -> rows += Triple(if (i == 0) "字体" else "", l, null) }
            fontDetail?.split(" · ")?.forEach { part -> wrap(codeTx, part, valW, valW).forEach { rows += Triple("", it, DIMROW) } }
        }
        cells.forEach { rows += Triple(keyName(it.icon), it.value, if (it.dim) DIMROW else it.swatch) }
        val h = headH + dpf(Hdr.CONTENT_GAP.toFloat()) + rows.size * rowH + dpf(8f)
        val gap = dpf(8f); val m = dpf(8f); val topSafe = dpf(28f); val botSafe = H - dpf(16f)
        var left: Float; var top: Float
        val cxL = (anchor.centerX() - w / 2).coerceIn(m, (W - w - m).coerceAtLeast(m))
        when {
            anchor.bottom + gap + h <= botSafe -> { left = cxL; top = anchor.bottom + gap }
            anchor.top - gap - h >= topSafe -> { left = cxL; top = anchor.top - gap - h }
            else -> {
                val roomR = W - anchor.right; val roomL = anchor.left
                left = if (roomR >= roomL) anchor.right + gap else anchor.left - gap - w
                left = left.coerceIn(m, (W - w - m).coerceAtLeast(m))
                top = (anchor.centerY() - h / 2).coerceIn(topSafe, (botSafe - h).coerceAtLeast(topSafe))
            }
        }
        val rad = dpf(13f)
        val card = RectF(left, top, left + w, top + h)
        cardRect = RectF(card)
        c.drawRoundRect(card, rad, rad, cardShadow)
        c.drawRoundRect(card, rad, rad, panelBg)
        c.drawRoundRect(card, rad, rad, panelLine)
        // header: layer name + kind, close icon button
        val cb = RectF(left + w - dpf(Hdr.CLOSE_RIGHT.toFloat()) - closeS, top + (headH - closeS) / 2, left + w - dpf(Hdr.CLOSE_RIGHT.toFloat()), top + (headH + closeS) / 2)
        tmp.style = Paint.Style.FILL; tmp.color = Hdr.CLOSE_BG
        c.drawCircle(cb.centerX(), cb.centerY(), dpf(Hdr.CLOSE_CIRCLE / 2f), tmp)
        val ip = dpf(Hdr.CLOSE_INSET.toFloat())
        closeIcon.setBounds((cb.left + ip).toInt(), (cb.top + ip).toInt(), (cb.right - ip).toInt(), (cb.bottom - ip).toInt())
        closeIcon.draw(c)
        closeBtn = RectF(cb)
        // layer glyph (frame-like #) then name
        // header: same spec as the panel (Hdr): title at PAD, optional grey subtitle, close at right, vertically centred
        val gx = left + dpf(Hdr.PAD.toFloat()); val gy = top + headH / 2; val u = dpf(1f)
        val nameMax = cb.left - gx - dpf(8f)
        val sub = if (exact) "精确" else cardSubS
        val sg = dpf(Hdr.SUB_GAP.toFloat())
        val subW = if (sub.isNotEmpty()) hdSub.measureText(sub) + sg else 0f
        val showSub = sub.isNotEmpty() && hdTx.measureText(cardTitleS) + subW <= nameMax
        val name = fit(hdTx, cardTitleS, if (showSub) nameMax - subW else nameMax)
        val fm = hdTx.fontMetrics
        c.drawText(name, gx, gy - (fm.ascent + fm.descent) / 2, hdTx)
        if (showSub) { val sm = hdSub.fontMetrics; c.drawText(sub, gx + hdTx.measureText(name) + sg, gy - (sm.ascent + sm.descent) / 2, hdSub) }
        c.drawRect(left, top + headH, left + w, top + headH + 1f, cardDiv.apply { color = Color.parseColor("#FF444444") })
        var y = top + headH + dpf(Hdr.CONTENT_GAP.toFloat())
        rows.forEach { (k, v, sw) ->
            val cy = y + rowH / 2
            if (k.isNotEmpty()) c.drawText(k, left + pad, cy + dpf(4f), keyTx)
            var vx = left + valX
            if (sw != null && sw != DIMROW) {
                tmp.style = Paint.Style.FILL; tmp.color = sw
                val r = RectF(vx, cy - 6 * u, vx + 12 * u, cy + 6 * u)
                c.drawRoundRect(r, 2 * u, 2 * u, tmp); c.drawRoundRect(r, 2 * u, 2 * u, Paint(copyP).apply { color = Color.parseColor("#33FFFFFF"); strokeWidth = u })
                vx += dpf(18f)
            }
            codeTx.color = if (sw == DIMROW) Color.parseColor("#7A7A7A") else Color.WHITE
            c.drawText(fit(codeTx, v, left + w - pad - dpf(20f) - vx), vx, cy + dpf(4f), codeTx)
            if (k.isNotEmpty() && sw != DIMROW) copyIcon(c, left + w - pad - dpf(11f), cy)
            y += rowH
        }
        codeTx.color = Color.WHITE
    }

}
