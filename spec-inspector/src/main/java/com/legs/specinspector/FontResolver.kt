package com.legs.specinspector

import android.graphics.Paint
import android.graphics.fonts.Font
import android.graphics.text.TextRunShaper
import android.os.Build
import java.nio.ByteBuffer

/**
 * Resolves the fonts the system ACTUALLY uses to draw a text, by shaping it (API 31+) with the given paint and
 * reading each glyph's android.graphics.fonts.Font, then the OpenType name table of that font.
 * Text is grouped by script (英文 / 中文 / 数字 …) so the result shows which font covers which script.
 */
object FontResolver {
    data class Run(val script: String, val family: String, val sub: String, val weight: Int)

    private val nameCache = HashMap<String, Pair<String, String>>()

    private fun scriptOf(cp: Int): String = when (Character.UnicodeScript.of(cp)) {
        Character.UnicodeScript.HAN -> "中文"
        Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> "日文"
        Character.UnicodeScript.HANGUL -> "韩文"
        Character.UnicodeScript.LATIN -> "英文"
        Character.UnicodeScript.COMMON -> if (Character.isDigit(cp)) "数字" else "符号"
        else -> Character.UnicodeScript.of(cp).name.lowercase()
    }

    fun resolve(text: CharSequence?, paint: Paint): List<Run> {
        if (text.isNullOrEmpty() || Build.VERSION.SDK_INT < 31) return emptyList()
        val groups = LinkedHashMap<String, StringBuilder>()
        text.codePoints().forEach { cp -> if (!Character.isWhitespace(cp)) groups.getOrPut(scriptOf(cp)) { StringBuilder() }.appendCodePoint(cp) }
        val out = ArrayList<Run>()
        for ((sc, sb) in groups) runCatching {
            val s = sb.toString().let { if (it.length > 64) it.substring(0, 64) else it }
            val pg = TextRunShaper.shapeTextRun(s, 0, s.length, 0, s.length, 0f, 0f, false, paint)
            val seen = LinkedHashMap<String, Font>()
            for (i in 0 until pg.glyphCount()) {
                val f = pg.getFont(i)
                seen.getOrPut(key(f)) { f }
            }
            for (f in seen.values) {
                val (fam, sub) = nameOf(f)
                val w = f.axes?.firstOrNull { it.tag == "wght" }?.styleValue?.toInt() ?: f.style.weight
                out += Run(sc, fam, sub, w)
            }
        }
        return out
    }

    /** "Roboto + Noto Sans CJK SC" */
    fun families(runs: List<Run>): String = runs.map { it.family }.distinct().joinToString(" + ")

    /** "英文 Roboto · 中文 Noto Sans CJK SC" (digits/symbols omitted when they share a font with another script) */
    fun detail(runs: List<Run>): String {
        val main = runs.filter { it.script != "数字" && it.script != "符号" }
        val extra = runs.filter { (it.script == "数字" || it.script == "符号") && main.none { m -> m.family == it.family } }
        return (main + extra).distinctBy { it.script + it.family }.joinToString(" · ") { "${it.script} ${it.family}" }
    }

    private fun key(f: Font): String = (f.file?.path ?: ("buf" + System.identityHashCode(f.buffer))) + "#" + f.ttcIndex

    private fun nameOf(f: Font): Pair<String, String> = nameCache.getOrPut(key(f)) {
        runCatching { parseName(f.buffer.duplicate(), f.ttcIndex) }.getOrNull()
            ?: ((f.file?.nameWithoutExtension ?: "未知字体") to "")
    }

    private fun parseName(b: ByteBuffer, ttc: Int): Pair<String, String>? {
        fun u16(o: Int) = b.getShort(o).toInt() and 0xFFFF
        fun u32(o: Int) = b.getInt(o)
        var base = 0
        if (u32(0) == 0x74746366) base = u32(12 + 4 * ttc) // 'ttcf'
        val n = u16(base + 4)
        var nameOff = -1
        for (i in 0 until n) {
            val r = base + 12 + i * 16
            if (u32(r) == 0x6E616D65) nameOff = u32(r + 8) // 'name'
        }
        if (nameOff < 0) return null
        val count = u16(nameOff + 2); val strOff = nameOff + u16(nameOff + 4)
        val names = HashMap<Int, String>(); val prio = HashMap<Int, Int>()
        for (i in 0 until count) {
            val r = nameOff + 6 + i * 12
            val plat = u16(r); val lang = u16(r + 4); val id = u16(r + 6); val len = u16(r + 8); val off = u16(r + 10)
            if (id != 1 && id != 2 && id != 16 && id != 17) continue
            val bytes = ByteArray(len)
            for (k in 0 until len) bytes[k] = b.get(strOff + off + k)
            val s = if (plat == 3 || plat == 0) String(bytes, Charsets.UTF_16BE) else String(bytes, Charsets.ISO_8859_1)
            val p = if (plat == 3 && lang == 0x409) 3 else if (plat == 3) 2 else 1
            if (p > (prio[id] ?: 0)) { names[id] = s; prio[id] = p }
        }
        val fam = names[16] ?: names[1] ?: return null
        return fam to (names[17] ?: names[2] ?: "")
    }
}
