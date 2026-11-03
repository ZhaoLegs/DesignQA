package com.legs.designqa

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Native homepage using Konsta UI's iOS list, block and control styling. */
class MainActivity : Activity() {
    private lateinit var permSection: LinearLayout
    private val label = HomeStyle.text
    private val secondary = HomeStyle.secondary
    private val blue = HomeStyle.accent
    private val bg = HomeStyle.page
    private val sep = HomeStyle.divider
    private val prefs by lazy { getSharedPreferences("toggles", MODE_PRIVATE) }
    private val annotationPrefs by lazy { AnnotationPreferences(prefs) }
    private val displaySwitches = mutableMapOf<String, IosSwitch>()
    private fun refreshDisplaySwitches() {
        val options = annotationPrefs.read()
        displaySwitches.forEach { (key, control) -> control.setChecked(options.isOn(key)) }
    }

    private fun dp(v: Number) = (v.toFloat() * resources.displayMetrics.density).toInt()
    private fun w(weight: Int) = Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), weight, false)
    private fun tv(s: String, size: Float, color: Int = label, weight: Int = 400) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color); typeface = w(weight); includeFontPadding = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg; window.navigationBarColor = bg
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(32)); setBackgroundColor(bg) }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(tv("Spec", HomeStyle.titleSp, label, 700))
        val version = tv("v" + (runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: ""), HomeStyle.captionSp, secondary, 500).apply {
            background = rounded(Color.parseColor("#E5E5EB"), 12)
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        head.addView(version, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(12) })
        root.addView(head)
        permSection = section(root, "权限", "两项都开启后才能使用测量与颜色标注。")
        // primary filled button
        overlayBtn = tv("显示悬浮球", HomeStyle.rowSp, Color.WHITE, 600).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-1, dp(HomeStyle.controlHeight)).apply { topMargin = dp(24) }
            setOnClickListener {
                val s = QaService.instance
                when {
                    serviceState() == ConnectionState.CONNECTING ->
                        toast("无障碍已开启，正在等待系统连接")
                    s == null || !s.isConnected() -> {
                        if (accessibilityEnabled() && !batteryUnrestricted()) {
                            toast("系统尚未恢复服务，请允许 Spec 后台运行")
                            openBatterySettings()
                        } else {
                            toast(if (accessibilityEnabled()) "服务连接中断，可关闭后重新开启 Spec 服务" else "请开启 Spec 无障碍服务")
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    }
                    s.isOverlayShowing() -> { s.hideOverlay(); refreshOverlayBtn() }
                    else -> { if (s.showOverlay()) moveTaskToBack(true); refreshOverlayBtn() }
                }
            }
        }
        root.addView(overlayBtn)
        refreshOverlayBtn()
        val disp = section(root, "默认显示", "也可以在悬浮面板中随时切换。")
        val icons = mapOf("size" to R.drawable.ic_home_size, "space" to R.drawable.ic_home_space, "font" to R.drawable.ic_home_font_size,
            "color" to R.drawable.ic_home_color, "bounds" to R.drawable.ic_home_bounds,
            "family" to R.drawable.ic_home_font_family, "radius" to R.drawable.ic_home_radius, "stroke" to R.drawable.ic_home_stroke)
        QaService.CHIPS.forEachIndexed { i, (k, name) ->
            val sw = IosSwitch(this).apply {
                setChecked(annotationPrefs.read().isOn(k))
                onChange = { annotationPrefs.select(k); refreshDisplaySwitches(); QaService.instance?.refreshFromPrefs() }
            }
            displaySwitches[k] = sw
            sw.contentDescription = name
            addRow(disp, icons[k]!!, blue, name, null, sw, last = i == QaService.CHIPS.size - 1)
        }
        setContentView(ScrollView(this).apply { addView(root); isFillViewport = true; setBackgroundColor(bg); fitsSystemWindows = true })
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }

    private fun sectionTitle(text: String) = tv(text, 14f, HomeStyle.sectionText, 400).apply {
        setPadding(0, dp(HomeStyle.sectionGap), 0, dp(10))
    }

    private fun hairline(inset: Int) = View(this).apply {
        setBackgroundColor(sep)
        layoutParams = LinearLayout.LayoutParams(-1, maxOf(1, dp(0.33f))).apply { leftMargin = inset }
    }

    private fun section(root: LinearLayout, header: String, footer: String?): LinearLayout {
        root.addView(sectionTitle(header))
        val s = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(HomeStyle.surface, HomeStyle.groupRadius)
            clipToOutline = true
        }
        root.addView(s)
        footer?.let { root.addView(tv(it, HomeStyle.captionSp, secondary).apply { setPadding(dp(16), dp(10), dp(16), 0); setLineSpacing(dp(2).toFloat(), 1f) }) }
        return s
    }

    private fun iconTile(res: Int, color: Int) = ImageView(this).apply {
        background = rounded((color and 0x00FFFFFF) or 0x14000000, 8)
        setImageResource(res)
        imageTintList = ColorStateList.valueOf(color)
        setPadding(dp(5), dp(5), dp(5), dp(5))
    }

    private fun addRow(s: LinearLayout, icon: Int, tint: Int, title: String, sub: String?, trailing: View?, last: Boolean, chevron: Boolean = false, onClick: (() -> Unit)? = null) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            onClick?.let { f -> setOnClickListener { f() }; background = android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(HomeStyle.accentTint), null, android.graphics.drawable.ColorDrawable(HomeStyle.surface)) }
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(HomeStyle.rowHeight)
            setPadding(dp(HomeStyle.rowInset), dp(4), dp(HomeStyle.rowInset), dp(4))
        }
        bar.addView(iconTile(icon, tint), LinearLayout.LayoutParams(dp(28), dp(28)).apply { rightMargin = dp(16) })
        bar.addView(tv(title, HomeStyle.rowSp), LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(8) })
        trailing?.let { bar.addView(it) }
        if (chevron) bar.addView(ImageView(this).apply { setImageResource(R.drawable.ic_chevron); setColorFilter(Color.parseColor("#B8B8BD")) },
            LinearLayout.LayoutParams(dp(12), dp(12)).apply { leftMargin = dp(8) })
        row.addView(bar)
        sub?.let { row.addView(tv(it, HomeStyle.captionSp, secondary).apply {
            setPadding(dp(60), 0, dp(16), dp(12)); setLineSpacing(dp(2).toFloat(), 1f)
        }) }
        s.addView(row)
        if (!last) s.addView(hairline(dp(60)))
    }

    private fun status(on: Boolean, onText: String, offText: String) = tv(if (on) onText else offText, 12f,
        if (on) HomeStyle.success else secondary, 500).apply {
        background = rounded(if (on) HomeStyle.successTint else HomeStyle.toggleOff, 12)
        setPadding(dp(8), dp(4), dp(8), dp(4))
    }

    private fun tinted(s: String) = tv(s, HomeStyle.captionSp, blue, 600).apply {
        background = rounded(HomeStyle.accentTint, 12)
        setPadding(dp(10), dp(5), dp(10), dp(5))
    }

    private var overlayBtn: TextView? = null
    private var buttonShowing: Boolean? = null
    private fun refreshOverlayBtn() {
        val b = overlayBtn ?: return
        val showing = QaService.instance?.isOverlayShowing() == true
        val text = when (serviceState()) {
            ConnectionState.DISABLED -> "开启无障碍服务"
            ConnectionState.CONNECTING -> "等待无障碍连接…"
            ConnectionState.INTERRUPTED -> "检查服务连接"
            ConnectionState.CONNECTED -> if (showing) "关闭悬浮球" else "显示悬浮球"
        }
        if (b.text.toString() != text) b.text = text
        if (buttonShowing != showing) {
            buttonShowing = showing
            b.setTextColor(if (showing) HomeStyle.danger else Color.WHITE)
            val fill = rounded(if (showing) HomeStyle.dangerTint else blue, 24)
            b.background = android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x26FFFFFF), fill, null)
        }
    }
    private val stateHandler = Handler(Looper.getMainLooper())
    private var disconnectedAt: Long? = null
    private var lastState = ConnectionState.DISABLED
    private fun accessibilityEnabled(): Boolean {
        val en = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val component = ComponentName(this, QaService::class.java)
        return en.split(':').any { ComponentName.unflattenFromString(it) == component }
    }
    private fun serviceState(): ConnectionState {
        val connected = QaService.instance?.isConnected() == true
        val enabled = accessibilityEnabled()
        val now = SystemClock.uptimeMillis()
        if (connected || !enabled) disconnectedAt = null
        else if (disconnectedAt == null) disconnectedAt = now
        return connectionState(enabled, connected, disconnectedAt?.let { now - it } ?: 0)
    }
    private fun batteryUnrestricted() =
        getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    private fun openBatterySettings() {
        val intent = if (batteryUnrestricted()) Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        else Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
        runCatching { startActivity(intent) }.onFailure {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }
    // Only poll while this screen is visible, including ROM detachments without service callbacks.
    private val stateRefresh = object : Runnable {
        override fun run() {
            val state = serviceState()
            if (state != lastState) { lastState = state; refresh() }
            refreshOverlayBtn()
            stateHandler.postDelayed(this, 750)
        }
    }
    override fun onResume() {
        super.onResume()
        disconnectedAt = null
        lastState = serviceState()
        refresh(); refreshOverlayBtn(); refreshDisplaySwitches()
        QaService.onStateChanged = { refresh(); refreshOverlayBtn(); refreshDisplaySwitches() }
        stateHandler.post(stateRefresh)
    }
    override fun onPause() {
        stateHandler.removeCallbacks(stateRefresh)
        QaService.onStateChanged = null
        super.onPause()
    }

    private fun refresh() {
        val ov = Settings.canDrawOverlays(this)
        val state = serviceState()
        val a = state == ConnectionState.CONNECTED
        permSection.removeAllViews()
        val goOverlay = { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
        val goA11y = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        // finished steps collapse to a single compact row; unfinished ones show a hint and a tinted button
        addRow(permSection, R.drawable.ic_home_overlay, blue, "悬浮窗", if (ov) null else "允许显示在其他应用上层，用于悬浮球和测量图层",
            if (ov) status(true, "已开启", "") else tinted("去开启"), false, ov, goOverlay)
        val hint = when (state) {
            ConnectionState.CONNECTED -> null
            ConnectionState.CONNECTING -> "已开启，等待系统连接；连接恢复后即可使用"
            ConnectionState.INTERRUPTED -> "已开启但系统未连接。先检查后台运行设置；仍未恢复时关闭后重新开启 Spec 服务"
            ConnectionState.DISABLED -> "读取控件结构并截屏识别颜色：无障碍 → 已安装的服务 → Spec"
        }
        addRow(permSection, R.drawable.ic_home_accessibility, blue, "无障碍服务", hint,
            if (a) status(true, "已连接", "") else tinted(when (state) {
                ConnectionState.CONNECTING -> "连接中"
                ConnectionState.INTERRUPTED -> "检查连接"
                else -> "去开启"
            }), false, a) {
                if (state == ConnectionState.CONNECTING) toast("正在等待系统连接")
                else goA11y()
            }
        val unrestricted = batteryUnrestricted()
        addRow(permSection, R.drawable.ic_home_background, blue, "后台运行设置",
            null,
            if (unrestricted) status(true, "已豁免", "") else tinted("去允许"),
            true, true) { openBatterySettings() }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
