package com.legs.specsample

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24f).toInt(), dp(48f).toInt(), dp(24f).toInt(), dp(24f).toInt()) }
        fun add(label: String, block: TextView.() -> Unit) = col.addView(TextView(this).apply {
            text = label; textSize = 20f; setTextColor(Color.parseColor("#1C1C1E")); setPadding(0, dp(10f).toInt(), 0, dp(10f).toInt()); block()
        })
        add("Spec Sample 字重 300 Light") { typeface = Typeface.create(Typeface.DEFAULT, 300, false) }
        add("Spec Sample 字重 400 Regular") { typeface = Typeface.create(Typeface.DEFAULT, 400, false) }
        add("Spec Sample 字重 500 Medium") { typeface = Typeface.create(Typeface.DEFAULT, 500, false) }
        add("Spec Sample 字重 600 SemiBold") { typeface = Typeface.create(Typeface.DEFAULT, 600, false) }
        add("Spec Sample 字重 700 Bold") { typeface = Typeface.create(Typeface.DEFAULT, 700, false) }
        add("textStyle=bold（400→700）") { setTypeface(null, Typeface.BOLD) }
        add("Letter spacing 0.1em") { letterSpacing = 0.1f; textSize = 16f }
        add("行高 32dp 的多行文本示例，第二行用于验证行高测量是否准确。") { textSize = 15f; lineHeight = dp(32f).toInt() }
        add("serif 衬线 600") { typeface = Typeface.create(Typeface.create("serif", Typeface.NORMAL), 600, false); textSize = 18f }
        add("按钮 圆角 12 描边 2") {
            textSize = 16f; setTextColor(Color.WHITE); setPadding(dp(20f).toInt(), dp(12f).toInt(), dp(20f).toInt(), dp(12f).toInt())
            background = GradientDrawable().apply { setColor(Color.parseColor("#007AFF")); cornerRadius = dp(12f); setStroke(dp(2f).toInt(), Color.parseColor("#FF2D55")) }
        }
        // 描边测试：1px 细线描边按钮 + 1px 分隔线（宽/高 = 1 物理像素）
        add("细线描边 1px #E5E5E5") {
            textSize = 16f; setPadding(dp(20f).toInt(), dp(12f).toInt(), dp(20f).toInt(), dp(12f).toInt())
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(8f); setStroke(1, Color.parseColor("#E5E5E5")) }
        }
        col.addView(android.view.View(this).apply { setBackgroundColor(Color.parseColor("#E5E5E5")) }, android.widget.LinearLayout.LayoutParams(-1, 1).apply { topMargin = dp(16f).toInt(); bottomMargin = dp(16f).toInt() })
        add("分隔线上方 / 下方 1px #DDDDDD 竖线在右侧") { textSize = 14f }
        col.addView(android.view.View(this).apply { setBackgroundColor(Color.parseColor("#DDDDDD")) }, android.widget.LinearLayout.LayoutParams(-1, 1).apply { topMargin = dp(16f).toInt(); bottomMargin = dp(40f).toInt() })
        // 中文 + 藏文混排：裁切复现 vs 修复
        val tib = "བཀྲ་ཤིས་བདེ་ལེགས། 扎西德勒 སྐྱིད་པོ་ 幸福 བསྟན་འཛིན་ སྒྲོལ་མ ཀྲི ཇུ"
        add("中藏混排 ❌ 裁切 ① 单行 16sp 固定高度 20dp + includeFontPadding=false  ② 多行 固定 44dp + lineSpacing 0.8") { textSize = 13f; setTextColor(Color.parseColor("#D92D20")) }
        col.addView(TextView(this).apply {
            text = tib; textSize = 16f; setTextColor(Color.BLACK)
            includeFontPadding = false; setLineSpacing(0f, 0.8f); maxLines = 1; isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
            if (android.os.Build.VERSION.SDK_INT >= 28) isFallbackLineSpacing = false
            setBackgroundColor(Color.parseColor("#FFF1F0"))
        }, android.widget.LinearLayout.LayoutParams(-1, dp(20f).toInt()).apply { topMargin = dp(4f).toInt() })
        col.addView(TextView(this).apply {
            text = tib + "\n第二行 " + tib; textSize = 17f; setTextColor(Color.BLACK)
            includeFontPadding = false; setLineSpacing(0f, 0.8f)
            if (android.os.Build.VERSION.SDK_INT >= 28) isFallbackLineSpacing = false
            setBackgroundColor(Color.parseColor("#FFF1F0"))
        }, android.widget.LinearLayout.LayoutParams(-1, dp(44f).toInt()).apply { topMargin = dp(8f).toInt() })
        add("中藏混排 ✅ 修复：wrap_content + includeFontPadding=true + fallbackLineSpacing + 行距 1.3 + 上下 padding 6dp") { textSize = 13f; setTextColor(Color.parseColor("#067647")); (this.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.topMargin = dp(16f).toInt() }
        col.addView(TextView(this).apply {
            text = tib + "\n第二行 " + tib; textSize = 17f; setTextColor(Color.BLACK)
            includeFontPadding = true; setLineSpacing(0f, 1.3f)
            if (android.os.Build.VERSION.SDK_INT >= 28) isFallbackLineSpacing = true
            setPadding(0, dp(6f).toInt(), 0, dp(6f).toInt())
            setBackgroundColor(Color.parseColor("#ECFDF3"))
        }, android.widget.LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4f).toInt(); bottomMargin = dp(60f).toInt() })
        // 16/16 单行：字号 16 + 行高 16（dp 为主；最后一条为物理 px）
        val zh16 = "扎西德勒 幸福安康 中文单行示例"
        val bo16 = "བཀྲ་ཤིས་བདེ་ལེགས། སྐྱིད་པོ་ སྒྲོལ་མ ཀྲི ཇུ"
        val mix16 = "བཀྲ་ཤིས་བདེ་ལེགས། 扎西德勒 སྐྱིད་པོ་ 幸福 སྒྲོལ་མ"
        fun v16(label: String, txt: String, unit: Int, pad: Boolean, fixedH: Boolean) {
            add(label) { textSize = 12f; setTextColor(Color.parseColor("#475467")); (layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.topMargin = dp(10f).toInt() }
            val px16 = TypedValue.applyDimension(unit, 16f, resources.displayMetrics)
            col.addView(TextView(this).apply {
                text = txt; setTextColor(Color.BLACK); setTextSize(unit, 16f)
                includeFontPadding = pad; isSingleLine = true; ellipsize = android.text.TextUtils.TruncateAt.END
                if (android.os.Build.VERSION.SDK_INT >= 28) lineHeight = px16.toInt()
                setBackgroundColor(Color.parseColor("#F2F4F7"))
            }, android.widget.LinearLayout.LayoutParams(-1, if (fixedH) px16.toInt() else -2).apply { topMargin = dp(2f).toInt() })
        }
        val D = TypedValue.COMPLEX_UNIT_DIP
        v16("16/16 ① 纯中文 · 16dp/行高16dp · fontPadding=false · wrap", zh16, D, false, false)
        v16("16/16 ② 纯藏文 · 16dp/行高16dp · fontPadding=false · wrap", bo16, D, false, false)
        v16("16/16 ③ 中藏混排 · 16dp/行高16dp · fontPadding=false · wrap", mix16, D, false, false)
        v16("16/16 ④ 中藏混排 · 16dp/行高16dp · 固定高度16dp", mix16, D, false, true)
        v16("16/16 ⑤ 中藏混排 · 16dp/行高16dp · fontPadding=true · wrap", mix16, D, true, false)
        v16("16/16 ⑥ 中藏混排 · 16物理px/行高16px · 固定16px (=4.6dp)", mix16, TypedValue.COMPLEX_UNIT_PX, false, true)
        run { val n = col.childCount; val mv = (n - 18 until n).map { col.getChildAt(it) }; mv.forEach { col.removeView(it) }; mv.forEachIndexed { i, v -> col.addView(v, i) } }
        run { val mv = (6 until 18).map { col.getChildAt(it) }; mv.forEach { col.removeView(it) }; mv.forEachIndexed { i, v -> col.addView(v, i) } }
        // ===== 系统级缓解示例：同样的恶劣条件（中藏混排 · 16dp 字号 · 固定高度 16dp · includeFontPadding=false · 单行）=====
        val hostile = "བཀྲ་ཤིས་བདེ་ལེགས། 扎西德勒 སྐྱིད་པོ་ 幸福 སྒྲོལ་མ"
        val h16 = dp(16f).toInt()
        col.clipChildren = false
        fun hdr(t: String, c: String) = add(t) { textSize = 12f; setTextColor(Color.parseColor(c)); (layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.topMargin = dp(12f).toInt() }
        fun hostileTv(tf: Typeface?) = TextView(this).apply {
            text = hostile; setTextColor(Color.BLACK); setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
            includeFontPadding = false; isSingleLine = true; ellipsize = android.text.TextUtils.TruncateAt.END
            if (tf != null) typeface = tf
            setBackgroundColor(Color.parseColor("#F2F4F7"))
        }
        add("【系统级缓解示例】条件同 ④：中藏混排 · 16dp · 固定高 16dp · fontPadding=false · 单行") { textSize = 14f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.BLACK); (layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.topMargin = dp(8f).toInt() }
        hdr("A 原样（对照）：藏文上下被裁", "#D92D20")
        col.addView(hostileTv(null), android.widget.LinearLayout.LayoutParams(-1, h16).apply { topMargin = dp(2f).toInt() })
        hdr("B 字体层：改造藏文字体（缩至 58% + 上移，度量对齐中文）。代价：藏文明显变小、基线与中文不齐", "#067647")
        val tfFit = runCatching {
            val fam = android.graphics.fonts.FontFamily.Builder(android.graphics.fonts.Font.Builder(resources, R.font.tibetan_fit).build()).build()
            Typeface.CustomFallbackBuilder(fam).setSystemFallback("sans-serif").build()
        }.getOrNull()
        col.addView(hostileTv(tfFit), android.widget.LinearLayout.LayoutParams(-1, h16).apply { topMargin = dp(2f).toInt() })
        hdr("C 框架层：模拟 ROM 改 TextView——所需行高 > 固定高度时自动缩小字号。代价：整行（含中文）一起变小", "#067647")
        col.addView(FitTextView(this, hostile, dp(16f)).apply { setBackgroundColor(Color.parseColor("#F2F4F7")) },
            android.widget.LinearLayout.LayoutParams(-1, h16).apply { topMargin = dp(2f).toInt() })
        hdr("D 允许绘制出界：clipChildren=false + 不裁剪绘制。代价：字形压到上下相邻元素", "#067647")
        col.addView(NoClipTextView(this, hostile, dp(16f)).apply { setBackgroundColor(Color.parseColor("#F2F4F7")) },
            android.widget.LinearLayout.LayoutParams(-1, h16).apply { topMargin = dp(2f).toInt(); bottomMargin = dp(16f).toInt() })
        run { val n = col.childCount; val mv = (n - 9 until n).map { col.getChildAt(it) }; mv.forEach { col.removeView(it) }; mv.forEachIndexed { i, v -> col.addView(v, i) } }
        setContentView(ScrollView(this).apply { addView(col); setBackgroundColor(Color.WHITE) })
    }
}

/** 模拟 ROM 级 TextView 补丁：所需行高超过可用高度时逐步缩小字号。 */
class FitTextView(ctx: android.content.Context, private val txt: String, private val sizePx: Float) : android.view.View(ctx) {
    private val p = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val b = android.graphics.Rect()
    override fun onDraw(c: android.graphics.Canvas) {
        // 量真实字形墨迹高度（含藏文上加/下加字），超出可用高度就按比例缩小字号，再把墨迹垂直居中
        p.textSize = sizePx; p.getTextBounds(txt, 0, txt.length, b)
        val avail = height * 0.96f
        if (b.height() > avail) { p.textSize = sizePx * avail / b.height(); p.getTextBounds(txt, 0, txt.length, b) }
        val y = (height - b.height()) / 2f - b.top
        android.util.Log.d("FitTV", "h=$height b=$b size=${p.textSize} y=$y")
        c.drawText(txt, paddingLeft.toFloat(), y, p)
    }
}

/** 不裁剪的文字：直接按字体度量把一行画在固定高度里，超出部分照画（父布局需 clipChildren=false）。 */
class NoClipTextView(ctx: android.content.Context, private val txt: String, sizePx: Float) : android.view.View(ctx) {
    private val p = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = sizePx; color = Color.BLACK }
    override fun onDraw(c: android.graphics.Canvas) {
        val fm = p.fontMetrics
        c.drawText(txt, paddingLeft.toFloat(), -fm.ascent, p)
    }
}
