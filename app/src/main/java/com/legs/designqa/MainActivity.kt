package com.legs.designqa

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

/** iOS-style inset grouped settings screen. */
class MainActivity : Activity() {
    private lateinit var permSection: LinearLayout
    private val label = Color.BLACK
    private val secondary = Color.parseColor("#8A8A8E")
    private val blue = Color.parseColor("#007AFF")
    private val bg = Color.parseColor("#F2F2F7")
    private val sep = Color.parseColor("#C6C6C8")
    private val prefs by lazy { getSharedPreferences("toggles", MODE_PRIVATE) }

    private fun dp(v: Number) = (v.toFloat() * resources.displayMetrics.density).toInt()
    private fun w(weight: Int) = Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), weight, false)
    private fun tv(s: String, size: Float, color: Int = label, weight: Int = 400) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color); typeface = w(weight); includeFontPadding = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg; window.navigationBarColor = bg
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(40)); setBackgroundColor(bg) }
        // navigation large title
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), dp(12), 0, 0) }
        head.addView(tv("Spec", 34f, label, 700).apply { letterSpacing = 0.01f })
        head.addView(tv("v" + (runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: ""), 13f, secondary).apply { setPadding(dp(8), dp(14), 0, 0) },
            LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(ImageView(this).apply { setImageResource(R.mipmap.ic_launcher) }, LinearLayout.LayoutParams(dp(44), dp(44)))
        root.addView(head)
        root.addView(tv("在任意 App 上测量尺寸、间距、字号、颜色。", 15f, secondary).apply {
            setLineSpacing(dp(3).toFloat(), 1f); setPadding(dp(4), dp(6), dp(4), 0)
        })
        permSection = section(root, "权限", "两项都开启后才能使用测量与颜色标注。")
        // primary filled button
        overlayBtn = tv("显示悬浮球", 17f, Color.WHITE, 600).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(28) }
            setOnClickListener {
                val s = QaService.instance
                when {
                    s == null -> toast("请先开启无障碍服务")
                    s.isOverlayShowing() -> { s.hideOverlay(); refreshOverlayBtn() }
                    else -> { s.showOverlay(); moveTaskToBack(true) }
                }
            }
        }
        root.addView(overlayBtn)
        refreshOverlayBtn()
        val disp = section(root, "默认显示", "也可以在悬浮面板中随时切换。")
        val icons = mapOf("size" to R.drawable.ic_inspect, "space" to R.drawable.ic_layers, "font" to R.drawable.ic_annot,
            "color" to R.drawable.ic_pick, "bounds" to R.drawable.ic_albums,
            "family" to R.drawable.ic_font, "radius" to R.drawable.ic_radius, "stroke" to R.drawable.ic_stroke)
        val tints = listOf("#007AFF", "#FF2D55", "#FF9500", "#AF52DE", "#5856D6", "#34C759", "#5AC8FA", "#8E8E93", "#FF3B30")
        QaService.CHIPS.forEachIndexed { i, (k, name) ->
            val sw = IosSwitch(this).apply {
                setChecked(prefs.getBoolean(k, k in setOf("size", "space", "font", "color")))
                onChange = { v -> prefs.edit().putBoolean(k, v).apply(); QaService.instance?.refreshFromPrefs() }
            }
            addRow(disp, icons[k]!!, Color.parseColor(tints[i % tints.size]), name, null, sw, last = i == QaService.CHIPS.size - 1)
        }
        val help = section(root, "使用方法", null)
        listOf("全屏标注" to "标注文本、按钮、图片和间距，标签自动避让",
            "检查" to "点选控件查看信息；再点一个控件测量间距",
            "保存图片" to "标注结果保存到相册 Pictures/Spec").forEachIndexed { i, (t, b) ->
            help.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(11), dp(16), dp(11))
                addView(tv(t, 17f))
                addView(tv(b, 15f, secondary).apply { setPadding(0, dp(4), 0, 0) })
            })
            if (i < 2) help.addView(hairline(dp(16)))
        }
        setContentView(ScrollView(this).apply { addView(root); isFillViewport = true; setBackgroundColor(bg); fitsSystemWindows = true })
    }

    private fun hairline(inset: Int) = View(this).apply {
        setBackgroundColor(sep)
        layoutParams = LinearLayout.LayoutParams(-1, maxOf(1, dp(0.33f))).apply { leftMargin = inset }
    }

    private fun section(root: LinearLayout, header: String, footer: String?): LinearLayout {
        root.addView(tv(header, 13f, secondary).apply { setPadding(dp(16), dp(28), 0, dp(7)) })
        val s = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(12).toFloat() }
            clipToOutline = true
        }
        root.addView(s)
        footer?.let { root.addView(tv(it, 13f, secondary).apply { setPadding(dp(16), dp(7), dp(16), 0); setLineSpacing(dp(2).toFloat(), 1f) }) }
        return s
    }

    private fun iconTile(res: Int, color: Int) = FrameLayout(this).apply {
        background = GradientDrawable().apply { setColor(color); cornerRadius = dp(7).toFloat() }
        addView(ImageView(this@MainActivity).apply { setImageResource(res) }, FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER))
    }

    private fun addRow(s: LinearLayout, icon: Int, tint: Int, title: String, sub: String?, trailing: View?, last: Boolean, chevron: Boolean = false, onClick: (() -> Unit)? = null) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(52); setPadding(dp(16), dp(8), dp(16), dp(8))
            onClick?.let { f -> setOnClickListener { f() }; background = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x14000000), null, android.graphics.drawable.ColorDrawable(Color.WHITE)) }
        }
        row.addView(iconTile(icon, tint), LinearLayout.LayoutParams(dp(29), dp(29)).apply { rightMargin = dp(14) })
        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(tv(title, 17f))
            sub?.let { addView(tv(it, 13f, secondary).apply { setPadding(0, dp(3), 0, 0); setLineSpacing(dp(2).toFloat(), 1f) }) }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        trailing?.let { row.addView(it) }
        if (chevron) row.addView(ImageView(this).apply { setImageResource(R.drawable.ic_chevron); setColorFilter(Color.parseColor("#C4C4C7")) },
            LinearLayout.LayoutParams(dp(14), dp(14)).apply { leftMargin = dp(8) })
        s.addView(row)
        if (!last) s.addView(hairline(dp(59)))
    }

    private fun status(on: Boolean, onText: String, offText: String) = tv(if (on) onText else offText, 13f,
        Color.parseColor(if (on) "#248A3D" else "#8A8A8E"), 600).apply {
        background = GradientDrawable().apply { setColor(Color.parseColor(if (on) "#2634C759" else "#14787880")); cornerRadius = dp(11).toFloat() }
        setPadding(dp(9), dp(4), dp(9), dp(4))
    }

    private fun tinted(s: String) = tv(s, 15f, blue, 600).apply {
        background = GradientDrawable().apply { setColor(Color.parseColor("#1F007AFF")); cornerRadius = dp(14).toFloat() }
        setPadding(dp(12), dp(6), dp(12), dp(6))
    }

    private var overlayBtn: TextView? = null
    private fun refreshOverlayBtn() {
        val b = overlayBtn ?: return
        val showing = QaService.instance?.isOverlayShowing() == true
        b.text = if (showing) "关闭悬浮球" else "显示悬浮球"
        // iOS: primary = filled blue; destructive = light gray fill with red label
        b.setTextColor(if (showing) Color.parseColor("#FF3B30") else Color.WHITE)
        b.background = GradientDrawable().apply { setColor(Color.parseColor(if (showing) "#1F767680" else "#007AFF")); cornerRadius = dp(12).toFloat() }
    }
    override fun onResume() { super.onResume(); refresh(); QaService.instance?.onOverlayChanged = { refreshOverlayBtn() }; refreshOverlayBtn() }
    override fun onPause() { QaService.instance?.onOverlayChanged = null; super.onPause() }

    private fun refresh() {
        val ov = Settings.canDrawOverlays(this)
        val en = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val a = en.contains(ComponentName(this, QaService::class.java).flattenToString()) || QaService.instance != null
        permSection.removeAllViews()
        val goOverlay = { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
        val goA11y = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        // finished steps collapse to a single compact row; unfinished ones show a hint and a tinted button
        addRow(permSection, R.drawable.ic_layers, Color.parseColor("#FF9500"), "悬浮窗", if (ov) null else "允许显示在其他应用上层，用于悬浮球和测量图层",
            if (ov) status(true, "已开启", "") else tinted("去开启"), false, ov, goOverlay)
        addRow(permSection, R.drawable.ic_a11y, blue, "无障碍服务", if (a) null else "读取控件结构并截屏识别颜色：无障碍 → 已安装的服务 → Spec",
            if (a) status(true, "已开启", "") else tinted("去开启"), true, a, goA11y)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
