package com.legs.designqa

import android.content.Context
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/** Best-effort font identification. Accessibility exposes no typeface, so everything here is inferred. */
object FontNames {
    data class Face(val family: String, val sub: String, val weight: Int, val file: String, val apk: String = "", val entry: String = "")

    private val appCache = HashMap<String, List<Face>>()
    private var sysFace: Face? = null
    private var sysDone = false

    /** Parses OpenType/TrueType (or first face of a TTC) name + OS/2 tables. */
    fun parse(b: ByteArray, file: String): Face? = runCatching {
        fun u16(o: Int) = ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)
        fun u32(o: Int) = (u16(o).toLong() shl 16 or u16(o + 2).toLong()).toInt()
        var base = 0
        if (b[0] == 't'.code.toByte() && b[1] == 't'.code.toByte() && b[2] == 'c'.code.toByte()) base = u32(12)
        val n = u16(base + 4)
        var nameOff = -1; var os2 = -1
        for (i in 0 until n) {
            val r = base + 12 + i * 16
            val tag = String(b, r, 4, Charsets.ISO_8859_1)
            if (tag == "name") nameOff = u32(r + 8)
            if (tag == "OS/2") os2 = u32(r + 8)
        }
        if (nameOff < 0) return null
        val count = u16(nameOff + 2); val strOff = nameOff + u16(nameOff + 4)
        val names = HashMap<Int, String>(); val prio = HashMap<Int, Int>()
        for (i in 0 until count) {
            val r = nameOff + 6 + i * 12
            val plat = u16(r); val lang = u16(r + 4); val id = u16(r + 6); val len = u16(r + 8); val off = u16(r + 10)
            if (id !in setOf(1, 2, 4, 16, 17)) continue
            val s = if (plat == 3 || plat == 0) String(b, strOff + off, len, Charsets.UTF_16BE) else String(b, strOff + off, len, Charsets.ISO_8859_1)
            val p = if (plat == 3 && lang == 0x409) 3 else if (plat == 3) 2 else 1
            if (p > (prio[id] ?: 0)) { names[id] = s; prio[id] = p }
        }
        val w = if (os2 > 0) u16(os2 + 4) else 400
        Face(names[16] ?: names[1] ?: return null, names[17] ?: names[2] ?: "", w, file)
    }.getOrNull()

    private fun readHead(f: File) = runCatching { f.inputStream().use { readAll(it) } }.getOrNull()
    private fun readAll(i: InputStream): ByteArray = i.readBytes()

    /** System default sans-serif face (honours common OEM overrides such as MiSans). */
    fun systemFace(): Face? {
        if (sysDone) return sysFace
        sysDone = true
        val cands = mutableListOf<File>()
        for (cfg in listOf("/product/etc/fonts_customization.xml", "/system/etc/font_fallback.xml", "/system/etc/fonts.xml")) {
            val x = runCatching { File(cfg).readText() }.getOrNull() ?: continue
            val fam = Regex("<family[^>]*name=\"sans-serif\"[^>]*>(.*?)</family>", RegexOption.DOT_MATCHES_ALL).find(x)?.groupValues?.get(1) ?: continue
            val font = Regex("<font[^>]*>\\s*([^<\\s]+)").findAll(fam).map { it.groupValues[1] }.toList()
            val pick = font.firstOrNull { Regex("weight=\"400\"").containsMatchIn(fam) } ?: font.firstOrNull() ?: continue
            for (d in listOf("/product/fonts", "/system/fonts")) File(d, pick).takeIf { it.exists() }?.let { cands += it }
        }
        // OEM fonts that replace the default regardless of fonts.xml
        listOf("/system/fonts/MiSansVF.ttf", "/system/fonts/MiSans-Regular.ttf", "/system/fonts/HarmonyOS_Sans_SC.ttf",
            "/system/fonts/OPlusSans3.0.ttf", "/system/fonts/Roboto-Regular.ttf", "/system/fonts/RobotoStatic-Regular.ttf")
            .map(::File).filter { it.exists() }.let { oem -> if (oem.any { it.name.startsWith("MiSans") || it.name.startsWith("Harmony") || it.name.startsWith("OPlus") }) cands.add(0, oem.first()) else cands += oem }
        for (f in cands) { readHead(f)?.let { parse(it, f.name) }?.let { sysFace = it; return it } }
        return null
    }

    /** Fonts bundled in an app's APKs (res/font, assets, any .ttf/.otf/.ttc). Cached per package. */
    fun appFaces(ctx: Context, pkg: String): List<Face> = appCache.getOrPut(pkg) {
        val ai = runCatching { ctx.packageManager.getApplicationInfo(pkg, 0) }.getOrNull() ?: return@getOrPut emptyList()
        val apks = listOfNotNull(ai.sourceDir) + (ai.splitSourceDirs?.toList() ?: emptyList())
        val out = mutableListOf<Face>()
        for (apk in apks) runCatching {
            ZipFile(apk).use { z ->
                for (e in z.entries()) {
                    val nm = e.name.lowercase()
                    if (e.isDirectory || !(nm.endsWith(".ttf") || nm.endsWith(".otf") || nm.endsWith(".ttc"))) continue
                    if (e.size > 40_000_000) continue
                    z.getInputStream(e).use { readAll(it) }.let { parse(it, e.name.substringAfterLast('/')) }?.let { out += it.copy(apk = apk, entry = e.name) }
                    if (out.size > 40) break
                }
            }
        }
        out.distinctBy { it.family + it.sub }
    }

    private fun wName(w: Int) = when { w <= 150 -> "Thin"; w <= 250 -> "ExtraLight"; w <= 350 -> "Light"; w <= 450 -> "Regular"
        w <= 550 -> "Medium"; w <= 650 -> "Semibold"; w <= 750 -> "Bold"; else -> "Heavy" }

    /** Returns (label, fromApp). Label always inferred; caller prefixes with ≈. */
    fun guess(ctx: Context, pkg: String?, weight: Int = 400): Pair<String, Boolean>? {
        val app = pkg?.let { appFaces(ctx, it) } ?: emptyList()
        if (app.isNotEmpty()) {
            // most common family, Regular-ish weight
            val fam = app.groupBy { it.family }.maxByOrNull { it.value.size }!!
            val f = fam.value.minByOrNull { kotlin.math.abs(it.weight - weight) }!!
            val more = app.map { it.family }.distinct().size - 1
            return ("${f.family} ${f.sub.ifBlank { wName(f.weight) }}".trim() + if (more > 0) " +$more" else "") to true
        }
        val s = systemFace() ?: return null
        return "${s.family} ${wName(weight)}".trim() to false
    }

    data class Guess(val family: String, val style: String, val fromApp: Boolean, val fallback: String, val typeface: android.graphics.Typeface)

    private val tfCache = HashMap<String, android.graphics.Typeface?>()
    private fun loadTf(ctx: Context, f: Face): android.graphics.Typeface? = tfCache.getOrPut(f.apk + "!" + f.entry) {
        runCatching {
            val out = File(ctx.cacheDir, "font_" + (f.apk + f.entry).hashCode() + "." + f.entry.substringAfterLast('.'))
            if (!out.exists()) ZipFile(f.apk).use { z -> z.getInputStream(z.getEntry(f.entry)).use { i -> out.outputStream().use { i.copyTo(it) } } }
            android.graphics.Typeface.createFromFile(out)
        }.getOrNull()
    }

    /** Structured guess for the info card; call off the main thread. */
    fun guessFull(ctx: Context, pkg: String?, weight: Int): Guess {
        // Prefer the system font: only trust bundled fonts that live in res/font (UI fonts), not assets
        // (PDF/WebView/emoji/display fonts), and only when the app ships a small, coherent set.
        val app = (pkg?.let { appFaces(ctx, it) } ?: emptyList()).filter { it.entry.startsWith("res/font/") || it.entry.startsWith("res/") && !it.entry.startsWith("res/raw/") }
            .takeIf { fs -> fs.map { it.family }.distinct().size <= 3 }
            // evidence of a UI text family: the same family shipped in at least two weights (e.g. Regular + Bold);
            // single display fonts (e.g. a decorative CJK font in Settings) are ignored
            ?.groupBy { it.family }?.filterValues { v -> v.map { it.weight }.distinct().size >= 2 }?.values?.flatten() ?: emptyList()
        val sys = systemFace()
        val sysName = sys?.family ?: "sans-serif"
        if (app.isNotEmpty()) {
            val fam = app.groupBy { it.family }.maxByOrNull { it.value.size }!!
            val f = fam.value.minByOrNull { kotlin.math.abs(it.weight - weight) }!!
            val tf = loadTf(ctx, f) ?: android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, weight, false)
            return Guess(f.family, f.sub.ifBlank { wName(f.weight) }, true, sysName, tf)
        }
        return Guess(sysName, wName(weight), false, "sans-serif",
            android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, weight.coerceIn(100, 900), false))
    }

    fun appFamilies(ctx: Context, pkg: String?): String? =
        pkg?.let { appFaces(ctx, it) }?.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.family + " " + it.sub }?.take(80)
}
