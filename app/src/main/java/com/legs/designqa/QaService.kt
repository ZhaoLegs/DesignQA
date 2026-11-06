package com.legs.designqa

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.provider.MediaStore
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.animation.ValueAnimator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class Kind { TEXT, BUTTON, IMAGE, CONTAINER }

data class NodeData(
    val rect: Rect, val parent: Rect?, val siblings: List<Rect>,
    val cls: String, val id: String?, val text: String?, val fontSp: Float?, val fontExact: Boolean, val fontUnit: String?,
    val kind: Kind = Kind.CONTAINER, val clickable: Boolean = false, val pkg: String? = null,
    /** px; lineExact = measured from character bounds */
    val lineH: Float? = null, val lineExact: Boolean = false, val letterSp: Float? = null,
    val exact: Exact? = null,
    val structuralRepresentative: Boolean = true
)

/** A spacing mark: vertical=true means a vertical line at x=pos from y=a to y=b. */
data class Gap(val vertical: Boolean, val pos: Int, val a: Int, val b: Int)

class QaService : AccessibilityService() {
    companion object {
        var instance: QaService? = null
        var onStateChanged: (() -> Unit)? = null
        val CHIPS = listOf("size" to "尺寸", "space" to "间距", "font" to "字号", "family" to "字体",
            "color" to "颜色", "bounds" to "边界", "radius" to "圆角", "stroke" to "描边")
    }

    private lateinit var wm: WindowManager
    private val bridge by lazy { SpecBridge(this) }
    private fun withExact(d: NodeData): NodeData = runCatching { bridge.match(d.pkg, d.rect.left, d.rect.top, d.rect.right, d.rect.bottom)?.let { d.copy(exact = it) } }.getOrNull() ?: d
    private var canvasView: MeasureView? = null
    private var canvasLp: WindowManager.LayoutParams? = null
    private var panel: LinearLayout? = null
    private var bubbleWin: FrameLayout? = null
    private var bubbleLp: WindowManager.LayoutParams? = null
    private var panelLp: WindowManager.LayoutParams? = null
    private val tiles = mutableMapOf<String, View>()
    private var activeDot: View? = null
    private val btns = mutableMapOf<String, TextView>()
    private var chipHint: TextView? = null
    private val h = Handler(Looper.getMainLooper())
    private val screenshotGate = ScreenshotGate<Bitmap> { SystemClock.uptimeMillis() }
    private val prefs by lazy { getSharedPreferences("toggles", MODE_PRIVATE) }
    private val annotationPrefs by lazy { AnnotationPreferences(prefs) }
    private var lockReceiverRegistered = false
    private var overlaysLocked = false
    private val lockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { syncLockscreenVisibility() }
    }

    // ---- design tokens (see design-direction.md) ----
    private object T {
        // Figma UI3 dark: bg #2C2C2C, border #444444, hover #383838, text #FFF/#B3B3B3/#7A7A7A, brand #0D99FF
        val SURFACE = Color.parseColor("#FF2C2C2C")      // panel / bubble (Figma sidebar)
        val HAIRLINE = Color.parseColor("#FF444444")     // borders & section dividers
        val FILL = Color.parseColor("#FF383838")         // segmented track / hover fill
        val FILL_HI = Color.parseColor("#0FFFFFFF")      // icon button pressed
        val ACCENT = Color.parseColor("#0D99FF")         // bg-brand / selected-strong
        val ACCENT_TINT = Color.parseColor("#FF4A4A4A")  // segmented "on" item (lighter than track)
        val ACCENT_TEXT = Color.parseColor("#FFFFFF")
        val TEXT = Color.parseColor("#FFFFFF")
        val TEXT2 = Color.parseColor("#B3B3B3")
        val TEXT3 = Color.parseColor("#7A7A7A")
    }
    private val semibold by lazy { android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, 600, false) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    /** Panel / info-card shared width: 248dp or 75% of the screen, whichever is smaller. */
    private fun panelW() = minOf(dp(248), (resources.displayMetrics.widthPixels * 0.75f).toInt())
    fun isOn(k: String): Boolean = annotationPrefs.read().isOn(k)

    override fun onServiceConnected() {
        Log.i("SpecService", "connected pid=${android.os.Process.myPid()}")
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (!lockReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT)
            }
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(lockReceiver, filter, RECEIVER_NOT_EXPORTED)
            else registerReceiver(lockReceiver, filter)
            lockReceiverRegistered = true
        }
        startMonitoringForeground()
        showOverlay()
        onStateChanged?.invoke()
    }

    fun isConnected(): Boolean = runCatching { serviceInfo != null }.getOrDefault(false)

    private fun startMonitoringForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("spec_monitor", "设计检查服务", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, "spec_monitor")
            .setSmallIcon(R.drawable.ic_bubble_ruler)
            .setContentTitle("Spec 检查服务运行中")
            .setContentText("保持无障碍检查和截图可用，点击管理")
            .setContentIntent(open).setOngoing(true).setShowWhen(false)
            .apply { if (Build.VERSION.SDK_INT >= 31) setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE) }
            .build()
        runCatching {
            if (Build.VERSION.SDK_INT >= 34) startForeground(41, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(41, notification)
        }.onFailure { Log.e("SpecCapture", "Unable to start foreground accessibility service", it) }
    }

    private val boundsRefresh = Runnable { refreshBounds() }
    /** 全屏标注 + 滚动：滚动时立即隐藏标注；停止滚动 280ms 后重新采集 + 截图，再显示新标注。 */
    private var contentRevision = 0L
    private val annotRefresh = Runnable {
        val cv = canvasView
        val previous = cv?.annots
        if (cv != null && cv.mode == MeasureView.Mode.INSPECT) {
            capture()
            return@Runnable
        }
        if (cv != null && previous != null) {
            val revision = contentRevision
            val (nodes, gaps) = collectAnnots()
            capture(automatic = true) {
                if (canvasView !== cv || overlaysLocked || cv.annots !== previous ||
                    contentRevision != revision) return@capture
                // Structure is still usable if the platform refuses pixels; never leave saving blocked.
                cv.colorCache.clear(); cv.weightCache.clear()
                cv.annots = nodes; cv.gaps = gaps; cv.annotsHidden = false; cv.invalidate()
            }
        }
    }
    private fun contentChanged() {
        contentRevision++
        canvasView?.let { cv ->
            cv.shot = null
            if (cv.annots != null) {
                cv.annotsHidden = true; cv.invalidate()
            }
            if (cv.annots != null || cv.mode == MeasureView.Mode.INSPECT) {
                h.removeCallbacks(annotRefresh); h.postDelayed(annotRefresh, 280)
            }
        }
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        syncLockscreenVisibility()
        if (overlaysLocked) return
        if (event?.packageName == packageName) return
        if (canvasView != null && isOn("bounds")) { h.removeCallbacks(boundsRefresh); h.postDelayed(boundsRefresh, 300) }
        val t = event?.eventType ?: 0
        val pkg = event?.packageName?.toString()
        if (t == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg != null && pkg != "com.android.systemui" && !pkg.contains("inputmethod") && pkg != lastPkg) {
            val switched = lastPkg != null; lastPkg = pkg
            val cv = canvasView
            // a selection belongs to the app it was made in: drop it (and the stale screenshot) on app switch
            if (switched && cv != null && cv.mode == MeasureView.Mode.INSPECT) { cv.clearSel(); cv.invalidate() }
        }
        if ((t == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || t == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
                    t == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) && pkg != "com.android.systemui") {
            contentChanged()
        }
    }
    private var lastPkg: String? = null
    override fun onInterrupt() { Log.w("SpecService", "interrupted connected=${isConnected()}") }
    override fun onUnbind(intent: Intent?): Boolean { disconnect(); return super.onUnbind(intent) }
    override fun onDestroy() { disconnect(); super.onDestroy() }

    private fun disconnect() {
        Log.w("SpecService", "disconnected pid=${android.os.Process.myPid()}")
        if (lockReceiverRegistered) { unregisterReceiver(lockReceiver); lockReceiverRegistered = false }
        h.removeCallbacks(annotRefresh); h.removeCallbacks(boundsRefresh)
        removeOverlay()
        if (instance === this) instance = null
        onStateChanged?.invoke()
        stopForeground(STOP_FOREGROUND_REMOVE)
        // The in-flight request owns the gate until its callback/timeout restores controls.
    }

    private fun winType() =
        if (Settings.canDrawOverlays(this)) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY

    fun showOverlay(): Boolean {
        if (!isConnected()) return false
        if (isOverlayShowing()) return true
        removeOverlay()
        return runCatching {
        val cv = MeasureView(this, this)
        val lp = WindowManager.LayoutParams(-1, -1, winType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT)
        lp.gravity = Gravity.TOP or Gravity.START
        lp.layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= 30) WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        if (Build.VERSION.SDK_INT >= 31) lp.alpha = 0.8f // untrusted pass-through overlays must be <= 0.8
        canvasView = cv; canvasLp = lp
        wm.addView(cv, lp)
        Thread { runCatching { FontNames.systemFace() } }.start()
        buildPanel()
        syncLockscreenVisibility()
        if (isOn("bounds")) refreshBounds()
        onOverlayChanged?.invoke()
        onStateChanged?.invoke()
        true
        }.getOrElse {
            Log.e("SpecOverlay", "Unable to restore floating controls", it)
            removeOverlay()
            Toast.makeText(this, "悬浮球创建失败，请检查悬浮窗和无障碍权限", Toast.LENGTH_LONG).show()
            false
        }
    }

    fun isOverlayShowing(): Boolean {
        val bubble = bubbleWin
        val lp = bubbleLp
        val size = dp(64)
        val inScreen = lp != null && lp.x > -size && lp.y > -size &&
            lp.x < resources.displayMetrics.widthPixels && lp.y < resources.displayMetrics.heightPixels
        return !overlaysLocked && OverlayState.showing(isConnected(), canvasView?.isAttachedToWindow == true,
            bubble?.isAttachedToWindow == true, inScreen,
            bubble?.visibility == View.VISIBLE && bubble.alpha > 0f, canvasView?.hideForCapture == true)
    }
    var onOverlayChanged: (() -> Unit)? = null
    /** Hide bubble + panel, exit any mode and drop every overlay. */
    fun hideOverlay() {
        canvasView?.let { cv -> if (cv.mode != MeasureView.Mode.NONE) { cv.mode = MeasureView.Mode.NONE }; cv.annots = null; cv.gaps = null; cv.clearSel() }
        h.removeCallbacks(annotRefresh); h.removeCallbacks(boundsRefresh)
        panelOpen = false
        removeOverlay()
    }

    fun removeOverlay() {
        h.post { onOverlayChanged?.invoke(); onStateChanged?.invoke() }
        h.removeCallbacks(noticeHide)
        noticeView?.let { it.animate().cancel(); runCatching { wm.removeView(it) } }; noticeView = null
        canvasView?.let { it.shot = null; runCatching { wm.removeView(it) } }; canvasView = null
        panel?.let { runCatching { wm.removeView(it) } }; panel = null
        bubbleWin?.let { runCatching { wm.removeView(it) } }; bubbleWin = null
        canvasLp = null; panelLp = null; bubbleLp = null; panelOpen = false
        tiles.clear(); btns.clear(); activeDot = null; chipHint = null
    }

    private fun rounded(color: Int, r: Float, stroke: Int = 0, sw: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = r; if (sw > 0) setStroke(sw, stroke)
    }

    private fun restorePanels() {
        panel?.visibility = if (!overlaysLocked && panelOpen) View.VISIBLE else View.GONE
        bubbleWin?.visibility = if (overlaysLocked) View.INVISIBLE else View.VISIBLE
    }
    private var panelOpen = false

    private fun syncLockscreenVisibility() {
        val locked = !shouldShowOverlays(getSystemService(KeyguardManager::class.java).isKeyguardLocked)
        val changed = locked != overlaysLocked
        overlaysLocked = locked
        if (locked) {
            h.removeCallbacks(annotRefresh); h.removeCallbacks(boundsRefresh)
            canvasView?.let { it.mode = MeasureView.Mode.NONE; it.clearSel(); it.shot = null }
            panelOpen = false
            applyCanvasTouch()
            listOfNotNull(canvasView, panel, bubbleWin, noticeView).forEach { it.visibility = View.INVISIBLE }
        } else if (canvasView?.hideForCapture != true) {
            canvasView?.visibility = View.VISIBLE
            restorePanels()
        }
        if (changed) {
            if (!locked) contentChanged()
            refreshButtons(); onStateChanged?.invoke()
        }
    }

    /**
     * The measuring layer only captures touches while 检查 is on AND the panel is closed. While the panel is open the
     * layer is pass-through, so it can never steal touches from the panel's buttons/chips whatever the window z-order
     * (on some ROMs a full-screen touchable overlay can end up above the panel). Inspect taps resume when the panel closes.
     */
    private fun applyCanvasTouch() {
        val cv = canvasView ?: return; val lp = canvasLp ?: return
        val touchable = cv.mode != MeasureView.Mode.NONE && !panelOpen
        val oldF = lp.flags; val oldA = lp.alpha
        lp.flags = if (touchable) lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        else lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (Build.VERSION.SDK_INT >= 31) lp.alpha = if (touchable) 1f else 0.8f
        if (oldF != lp.flags || oldA != lp.alpha) runCatching { wm.updateViewLayout(cv, lp) }
    }

    private fun setPanelOpen(open: Boolean) {
        panelOpen = open
        applyCanvasTouch()
        val p = panel ?: return; val plp = panelLp ?: return; val blp = bubbleLp ?: return
        if (open) {
            val sw = resources.displayMetrics.widthPixels; val sh = resources.displayMetrics.heightPixels
            val pw = panelW()
            val bubbleRight = blp.x > sw / 2
            plp.x = (if (bubbleRight) sw - pw - dp(64) else dp(64)).coerceIn(dp(8), (sw - pw - dp(8)).coerceAtLeast(dp(8)))
            plp.y = blp.y.coerceIn(dp(40), (sh - dp(420)).coerceAtLeast(dp(40)))
            wm.updateViewLayout(p, plp)
        }
        p.animate().cancel()
        p.pivotX = if (blp.x > resources.displayMetrics.widthPixels / 2) panelW().toFloat() else 0f; p.pivotY = dp(24).toFloat()
        val ease = android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f)
        if (open) {
            p.alpha = 0f; p.scaleX = 0.94f; p.scaleY = 0.94f; p.visibility = View.VISIBLE
            p.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).setInterpolator(ease).withEndAction(null).start()
        } else if (p.visibility == View.VISIBLE) {
            p.animate().alpha(0f).scaleX(0.96f).scaleY(0.96f).setDuration(140).setInterpolator(ease)
                .withEndAction { if (!panelOpen) p.visibility = View.GONE; p.alpha = 1f; p.scaleX = 1f; p.scaleY = 1f }.start()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildPanel() {
        val sw = resources.displayMetrics.widthPixels
        // ---- bubble ----
        val pad = dp(8)
        val bw = FrameLayout(this).apply { setPadding(pad, pad, pad, pad); clipToPadding = false }
        val bubble = FrameLayout(this).apply {
            background = rounded(T.SURFACE, dp(24).toFloat(), T.HAIRLINE, dp(1))
            elevation = dp(6).toFloat()
            layoutParams = FrameLayout.LayoutParams(dp(48), dp(48))
            addView(ImageView(this@QaService).apply {
                setImageResource(R.drawable.ic_bubble_ruler)
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = FrameLayout.LayoutParams(dp(26), dp(12), Gravity.CENTER)
            })
            contentDescription = "Spec 悬浮球"
            addView(View(this@QaService).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(T.ACCENT); setStroke(dp(2), T.SURFACE) }
                layoutParams = FrameLayout.LayoutParams(dp(12), dp(12), Gravity.TOP or Gravity.END).apply { topMargin = dp(2); rightMargin = dp(2) }
                visibility = View.GONE
                activeDot = this
            })
        }
        bw.addView(bubble)
        val blp = WindowManager.LayoutParams(-2, -2, winType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT)
        blp.gravity = Gravity.TOP or Gravity.START
        val full = dp(48) + pad * 2
        blp.x = OverlayState.position(prefs.getInt("bx", sw - full + dp(2)), sw, full, dp(2))
        blp.y = OverlayState.position(prefs.getInt("by", dp(180)), resources.displayMetrics.heightPixels, full, 0)
        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
        val slop = android.view.ViewConfiguration.get(this).scaledTouchSlop
        var longFired = false
        val longPress = Runnable {
            if (!moved) { longFired = true; bubble.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS, android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING); turnOffAll() }
        }
        bubble.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = blp.x; oy = blp.y; moved = false; longFired = false
                    h.postDelayed(longPress, android.view.ViewConfiguration.getLongPressTimeout().toLong())
                    bubble.animate().scaleX(0.92f).scaleY(0.92f).setDuration(90).start() }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - sx; val dy = e.rawY - sy
                    if (!longFired && (moved || Math.hypot(dx.toDouble(), dy.toDouble()) > slop)) {
                        h.removeCallbacks(longPress)
                        moved = true
                        blp.x = OverlayState.position(ox + dx.toInt(), sw, full, dp(2))
                        blp.y = OverlayState.position(oy + dy.toInt(), resources.displayMetrics.heightPixels, full, 0)
                        wm.updateViewLayout(bw, blp)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    h.removeCallbacks(longPress)
                    bubble.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    if (longFired) { /* consumed by long-press */ }
                    else if (!moved) { if (e.action == MotionEvent.ACTION_UP) setPanelOpen(!panelOpen) } else {
                        val target = if (blp.x + full / 2 > sw / 2) sw - full + dp(2) else -dp(2)
                        ValueAnimator.ofInt(blp.x, target).apply {
                            duration = 220
                            addUpdateListener { blp.x = it.animatedValue as Int; runCatching { wm.updateViewLayout(bw, blp) } }
                            start()
                        }
                        prefs.edit().putInt("bx", target).putInt("by", blp.y).apply()
                    }
                }
            }
            true
        }

        // ---- panel ----
        val p = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; visibility = View.GONE
            setPadding(dp(Hdr.PAD), 0, dp(Hdr.PAD), dp(16))
            background = rounded(T.SURFACE, dp(13).toFloat(), T.HAIRLINE, dp(1))
            elevation = dp(12).toFloat()
            layoutParams = LinearLayout.LayoutParams(panelW(), -2)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, dp(Hdr.H)) // 与信息卡共用 Hdr 规格
        }
        header.addView(TextView(this).apply {
            text = "Spec"; textSize = Hdr.TITLE_SP; setTextColor(Hdr.TITLE_COLOR); typeface = semibold; includeFontPadding = false
        })
        header.addView(TextView(this).apply {
            val vn = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: ""
            text = "V $vn"; textSize = 11f; setTextColor(T.TEXT); typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, 500, false); includeFontPadding = false
            setPadding(dp(8), 0, 0, 0)
        })
        header.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        })
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_close_sm); alpha = Hdr.CLOSE_ALPHA
            background = android.graphics.drawable.InsetDrawable(GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Hdr.CLOSE_BG) }, dp((Hdr.CLOSE - Hdr.CLOSE_CIRCLE) / 2))
            // 可见 20dp 圆 + 10dp X，触控区 40dp
            setPadding(dp(Hdr.CLOSE_INSET), dp(Hdr.CLOSE_INSET), dp(Hdr.CLOSE_INSET), dp(Hdr.CLOSE_INSET)); scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(Hdr.CLOSE), dp(Hdr.CLOSE)).apply { marginEnd = -(dp(Hdr.PAD) - dp(Hdr.CLOSE_RIGHT)) }
            contentDescription = "收起"
            setOnClickListener { setPanelOpen(false) } // closing the panel never changes modes
        })
        p.addView(header)
        fun sectionView(t: String) = TextView(this).apply {
            text = t; textSize = 11f; setTextColor(T.TEXT); typeface = semibold; setPadding(0, dp(12), 0, dp(8))
        }
        fun divider(top: Int = 4) = p.addView(View(this).apply {
            setBackgroundColor(T.HAIRLINE); layoutParams = LinearLayout.LayoutParams(-1, dp(1)).apply { leftMargin = -dp(16); rightMargin = -dp(16); topMargin = dp(top) }
        })
        fun section(t: String, top: Int = 4) { divider(top); p.addView(sectionView(t)) }
        section("工具", 0) // 标题栏下的分隔线紧贴 48dp 标题栏，与信息卡一致
        val acts = listOf(
            Triple("annot", "全屏标注", R.drawable.ic_ruler), Triple("inspect", "检查", R.drawable.ic_inspect),
            Triple("save", "保存图片", R.drawable.ic_save))
        val actions: Map<String, () -> Unit> = mapOf(
            "inspect" to {
                val cv = canvasView
                if (cv?.mode == MeasureView.Mode.INSPECT) setMode(MeasureView.Mode.NONE)
                else if (cv != null) {
                    if (cv.annots != null) { cv.annots = null; cv.gaps = null; cv.invalidate(); notice("已退出全屏标注") }
                    setMode(MeasureView.Mode.INSPECT)
                    h.postDelayed({ if (canvasView?.mode == MeasureView.Mode.INSPECT) setPanelOpen(false) }, 250) // 选好工具自动收起面板，直接点控件
                }
            },
            "annot" to { toggleAnnotate() }, "save" to {
                val cv = canvasView
                if (cv != null && cv.annots == null && cv.mode != MeasureView.Mode.INSPECT) notice("请先开启全屏标注或检查")
                else saveAnnotated()
            })
        acts.filter { it.first != "save" }.chunked(2).forEach { row ->
            // 纯文字按钮，与下方“显示”分段控件同款（轨道 + 28dp 项 + 5dp 圆角）
            val rl = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) } } // 两个独立按钮，无共享轨道
            row.forEachIndexed { i, (k, label, _) ->
                val tile = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                    setPadding(dp(2), 0, dp(2), 0)
                    layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f).apply { if (i > 0) leftMargin = dp(8) }
                    addView(TextView(this@QaService).apply {
                        text = label; textSize = 11f; setTextColor(T.TEXT); gravity = Gravity.CENTER
                        maxLines = 1; includeFontPadding = false
                        layoutParams = LinearLayout.LayoutParams(-1, -2)
                    })
                    contentDescription = label
                    setOnClickListener { actions[k]?.invoke() }
                    setOnTouchListener { v, e ->
                        when (e.action) {
                            MotionEvent.ACTION_DOWN -> v.foreground = rounded(T.FILL_HI, dp(5).toFloat())
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.foreground = null
                        }
                        false
                    }
                }
                tiles[k] = tile; rl.addView(tile)
            }
            p.addView(rl)
        }
        divider()
        p.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM
            addView(sectionView("显示"))
            addView(TextView(this@QaService).apply {
                text = "检查模式下不可用"; textSize = 11f; setTextColor(T.TEXT3)
                setPadding(dp(6), 0, 0, dp(8)); visibility = View.GONE
                chipHint = this
            })
        })
        val rows = CHIPS.chunked(4)
        val track = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(2), dp(2), dp(2), dp(2))
            background = rounded(T.FILL, dp(6).toFloat())
        }
        p.addView(track)
        rows.forEachIndexed { ri, row ->
            val rl = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false; layoutParams = LinearLayout.LayoutParams(-1, -2).apply { if (ri > 0) topMargin = dp(2) } }
            row.forEachIndexed { i, (k, label) ->
                rl.addView(TextView(this).apply {
                    text = label; textSize = 11f; gravity = Gravity.CENTER; maxLines = 1; includeFontPadding = false
                    setPadding(dp(2), 0, dp(2), 0)
                    layoutParams = LinearLayout.LayoutParams(0, dp(28), 1f).apply { if (i > 0) leftMargin = dp(2) }
                    setOnClickListener { toggleChip(k) }
                    btns[k] = this
                })
            }
            repeat(4 - row.size) { rl.addView(View(this).apply { visibility = View.INVISIBLE; layoutParams = LinearLayout.LayoutParams(0, dp(28), 1f).apply { leftMargin = dp(2) } }) }
            track.addView(rl)
        }
        // 保存图片：面板最底部，独立整行按钮（同款轨道样式）
        p.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(dp(2), dp(2), dp(2), dp(2)); background = rounded(T.FILL, dp(6).toFloat())
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
            val save = LinearLayout(this@QaService).apply {
                gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(-1, dp(28))
                addView(TextView(this@QaService).apply { text = "保存图片"; textSize = 11f; setTextColor(T.TEXT); gravity = Gravity.CENTER; includeFontPadding = false })
                contentDescription = "保存图片"
                setOnClickListener { actions["save"]?.invoke() }
            }
            tiles["save"] = save; addView(save)
        })
        val plp = WindowManager.LayoutParams(panelW(), -2, winType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT)
        plp.gravity = Gravity.TOP or Gravity.START
        var hx = 0f; var hy = 0f; var hox = 0; var hoy = 0
        header.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { hx = e.rawX; hy = e.rawY; hox = plp.x; hoy = plp.y }
                MotionEvent.ACTION_MOVE -> { plp.x = hox + (e.rawX - hx).toInt(); plp.y = hoy + (e.rawY - hy).toInt(); wm.updateViewLayout(p, plp) }
            }
            true
        }
        panel = p; panelLp = plp; bubbleWin = bw; bubbleLp = blp
        wm.addView(p, plp); wm.addView(bw, blp)
        panelOpen = false; p.visibility = View.GONE
        refreshButtons()
    }

    fun refreshFromPrefs() {
        if (isOn("bounds")) refreshBounds() else canvasView?.allBounds = null
        canvasView?.invalidate(); refreshButtons()
    }

    private fun refreshButtons() {
        val cv = canvasView ?: return
        fun tile(k: String, on: Boolean) = tiles[k]?.let { it.background = if (it == tiles["save"]) rounded(if (on) T.ACCENT else Color.TRANSPARENT, dp(5).toFloat())
                else rounded(if (on) T.ACCENT else T.FILL, dp(6).toFloat(), if (on) T.ACCENT else T.HAIRLINE, dp(1))
            ((it as? LinearLayout)?.getChildAt(0) as? TextView)?.let { tv -> tv.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, if (on) 600 else 400, false); tv.setTextColor(if (on) Color.WHITE else T.TEXT2) } } // Figma 工具栏：选中蓝底，未选中无底
        tile("inspect", cv.mode == MeasureView.Mode.INSPECT)
        tile("annot", cv.annots != null)
        activeDot?.visibility = if (cv.annots != null || cv.mode != MeasureView.Mode.NONE) View.VISIBLE else View.GONE
        listOf("save").forEach { tile(it, false) }
        val canSave = cv.annots != null || cv.mode == MeasureView.Mode.INSPECT
        tiles["save"]?.let { save ->
            save.alpha = if (canSave) 1f else 0.6f // 仍可点：点了会提示先开启模式
            ((save as? LinearLayout)?.getChildAt(0) as? TextView)?.setTextColor(if (canSave) Color.WHITE else T.TEXT2)
        }
        val chipsEnabled = canvasView?.mode != MeasureView.Mode.INSPECT
        chipHint?.visibility = if (chipsEnabled) View.GONE else View.VISIBLE
        for ((k, _) in CHIPS) btns[k]?.let {
            val on = isOn(k) && chipsEnabled // 检查模式下不显示选中态，统一为禁用态
            it.isEnabled = chipsEnabled; it.isClickable = chipsEnabled
            it.alpha = if (chipsEnabled) 1f else 0.3f
            // 分段控件：选中 = 浅蓝底 + 蓝字（层级低于上方工具），未选中透明融入轨道
            it.background = if (on) rounded(T.ACCENT_TINT, dp(5).toFloat(), Color.parseColor("#FF555555"), dp(1)) else null
            it.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, if (on) 600 else 400, false)
            it.setTextColor(if (on) T.ACCENT_TEXT else T.TEXT3)
        }
    }

    private fun toggleChip(k: String) {
        if (canvasView?.mode == MeasureView.Mode.INSPECT) return
        annotationPrefs.select(k)
        val on = isOn(k)
        if (k == "bounds") { if (on) refreshBounds() else canvasView?.allBounds = null }
        if ((k == "color" || k == "radius" || k == "stroke") && on && canvasView?.shot == null && (canvasView?.mode != MeasureView.Mode.NONE || canvasView?.annots != null)) capture()
        canvasView?.invalidate(); refreshButtons()
        onStateChanged?.invoke()
    }

    /** Keep bubble and panel above the (possibly touchable) measuring layer so the bubble stays tappable. */
    private fun raiseControls() {
        panel?.let { v -> panelLp?.let { l -> runCatching { wm.removeViewImmediate(v); wm.addView(v, l) } } }
        bubbleWin?.let { v -> bubbleLp?.let { l -> runCatching { wm.removeViewImmediate(v); wm.addView(v, l) } } }
    }

    fun setMode(m: MeasureView.Mode) {
        val cv = canvasView ?: return
        cv.mode = m
        cv.clearSel()
        applyCanvasTouch()
        // Do NOT re-add panel/bubble here: removing the panel while it is dispatching this click broke later
        // touches on its chips. Z-order is guaranteed instead by adding the canvas window first (showOverlay).
        // Screenshot is needed for text/background color and stroke sampling.
        if (m == MeasureView.Mode.INSPECT) capture()
        refreshButtons()
    }

    /** Long-press on the bubble: exit any mode, clear every overlay and selection. */
    private fun turnOffAll() {
        val cv = canvasView ?: return
        if (cv.mode != MeasureView.Mode.NONE) setMode(MeasureView.Mode.NONE)
        cv.annots = null; cv.gaps = null; cv.clearSel(); cv.invalidate()
        refreshButtons()
        notice("已关闭标注")
    }

    private fun toggleAnnotate() {
        val cv = canvasView ?: return
        if (cv.annots != null) { cv.annots = null; cv.gaps = null; cv.invalidate(); refreshButtons(); return }
        // 检查 与 全屏标注 互斥：先退出当前模式并清掉其选中与标签
        val wasInspect = cv.mode != MeasureView.Mode.NONE
        if (wasInspect) setMode(MeasureView.Mode.NONE)
        val revision = contentRevision
        val (nodes, gaps) = collectAnnots()
        // Publish pending state so a second tap can cancel while the screenshot is in flight.
        cv.annots = nodes; cv.gaps = gaps; cv.annotsHidden = true
        refreshButtons()
        capture { bitmap ->
            if (canvasView !== cv || overlaysLocked || cv.annots !== nodes ||
                contentRevision != revision) return@capture
            if (bitmap == null && Build.VERSION.SDK_INT >= 30) {
                cv.annots = null; cv.gaps = null
            } else {
                cv.annotsHidden = false
                notice((if (wasInspect) "已退出检查，" else "") + "已标注 ${nodes.size} 个元素、${gaps.size} 处间距")
            }
            cv.invalidate(); refreshButtons()
        }
    }

    /**
     * Screenshot of the app underneath WITHOUT hiding our overlays (no flash): on API 34+ we capture the target app's
     * window(s) only via takeScreenshotOfWindow and composite them at their screen bounds. Older APIs fall back to
     * briefly hiding the measuring layer and controls.
     */
    fun capture(automatic: Boolean = false, after: ((Bitmap?) -> Unit)? = null) {
        val cv = canvasView ?: run { after?.invoke(null); return }
        if (!isConnected()) {
            notice("无障碍服务已断开，请关闭后重新开启 Spec 服务")
            cv.shot = null
            after?.invoke(null)
            return
        }
        if (Build.VERSION.SDK_INT < 30) { notice("截屏需要 Android 11 及以上"); after?.invoke(null); return }
        // A later consumer needs a frame from its own request, especially after scroll/recreation.
        // Drain the previous platform operation before starting another or restoring controls.
        if (screenshotGate.isBusy) {
            val revision = contentRevision
            val resume = {
                if (canvasView === cv && !overlaysLocked && contentRevision == revision)
                    capture(automatic, after)
                else after?.invoke(null)
            }
            if (automatic || after == null) screenshotGate.deferRefresh { resume(); Unit }
            else screenshotGate.begin { resume() }
            return
        }
        val revision = contentRevision
        if (!screenshotGate.begin(after)) return
        fun deliver(bmp: Bitmap?) {
            if (canvasView !== cv || overlaysLocked || contentRevision != revision) {
                bmp?.recycle(); screenshotGate.finish(null); return
            }
            cv.shot = bmp
            cv.colorCache.clear(); cv.weightCache.clear()
            cv.invalidate(); screenshotGate.finish(bmp)
        }
        if (Build.VERSION.SDK_INT >= 34) {
            val dm = resources.displayMetrics
            val wins = runCatching { windows }.getOrNull().orEmpty()
                .filter { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION }
                .filter { it.root?.packageName?.toString() != packageName }
                .sortedBy { it.layer }
            if (wins.isNotEmpty()) {
                val out = Bitmap.createBitmap(dm.widthPixels.coerceAtLeast(1), dm.heightPixels.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                val cvs = Canvas(out)
                fun captureWindow(index: Int) {
                    if (index >= wins.size) { deliver(out); return }
                    val w = wins[index]
                    val b = Rect(); w.getBoundsInScreen(b)
                    requestScreenshot(w.id, 0) { bitmap, error ->
                        if (canvasView !== cv || overlaysLocked || contentRevision != revision) {
                            bitmap?.recycle(); out.recycle(); deliver(null)
                        } else if (bitmap == null) {
                            out.recycle()
                            if (error == ERROR_TAKE_SCREENSHOT_SECURE_WINDOW) {
                                screenshotFailure(error); deliver(null)
                            } else captureHiding(::deliver)
                        } else {
                            cvs.drawBitmap(bitmap, b.left.toFloat(), b.top.toFloat(), null)
                            bitmap.recycle()
                            captureWindow(index + 1)
                        }
                    }
                }
                captureWindow(0)
                return
            }
        }
        captureHiding(::deliver)
    }

    private fun captureHiding(done: (Bitmap?) -> Unit) {
        val cv = canvasView ?: run { done(null); return }
        cv.hideForCapture = true; cv.invalidate()
        // Display capture must exclude our controls too, otherwise sampled colors include the panel.
        val controls = listOfNotNull(panel, bubbleWin, noticeView)
        val visibility = controls.map { it.visibility }
        controls.forEach { it.visibility = View.INVISIBLE }
        h.postDelayed({
            requestScreenshot(null, 0) { bitmap, error ->
                cv.hideForCapture = false
                controls.forEachIndexed { index, view -> view.visibility = visibility[index] }
                syncLockscreenVisibility()
                cv.invalidate()
                if (bitmap == null) screenshotFailure(error)
                done(bitmap)
            }
        }, 80)
    }

    /** All window, display and retry requests share the same interval. */
    @android.annotation.TargetApi(30)
    private fun requestScreenshot(windowId: Int?, retry: Int, done: (Bitmap?, Int) -> Unit) {
        h.postDelayed({
            if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
                syncLockscreenVisibility()
                done(null, ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS)
                return@postDelayed
            }
            if (!isConnected()) { done(null, ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS); return@postDelayed }
            screenshotGate.attemptStarted()
            var completed = false
            // Some ROMs drop the callback; always restore controls and release the capture gate.
            val timeout = Runnable {
                if (!completed) {
                    completed = true
                    Log.w("SpecCapture", "screenshot callback timed out: window=$windowId")
                    done(null, ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR)
                }
            }
            h.postDelayed(timeout, 3000)
            val callback = object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    if (completed) { result.hardwareBuffer.close(); return }
                    completed = true
                    h.removeCallbacks(timeout)
                    val bitmap = runCatching {
                        val buffer = result.hardwareBuffer
                        try {
                            val hardware = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                            try { hardware?.copy(Bitmap.Config.ARGB_8888, false) }
                            finally { hardware?.recycle() }
                        } finally { buffer.close() }
                    }.getOrNull()
                    done(bitmap, if (bitmap == null) ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR else 0)
                }

                override fun onFailure(error: Int) {
                    if (completed) return
                    completed = true
                    h.removeCallbacks(timeout)
                    Log.w("SpecCapture", "screenshot failed: window=$windowId code=$error retry=$retry")
                    if (!isConnected()) done(null, ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS)
                    else if (screenshotGate.shouldRetry(error, retry)) requestScreenshot(windowId, retry + 1, done)
                    else done(null, error)
                }
            }
            try {
                if (windowId != null && Build.VERSION.SDK_INT >= 34) takeScreenshotOfWindow(windowId, mainExecutor, callback)
                else takeScreenshot(android.view.Display.DEFAULT_DISPLAY, mainExecutor, callback)
            } catch (error: Exception) {
                Log.e("SpecCapture", "screenshot request failed", error)
                callback.onFailure(ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR)
            }
        }, screenshotGate.delayBeforeAttempt())
    }

    private fun screenshotFailure(error: Int) {
        val reason = when (error) {
            ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS ->
                if (isConnected()) "系统暂时拒绝截图，请稍后重试"
                else "服务连接已中断，请打开 Spec 检查后台运行设置"
            ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> "系统截图繁忙，请稍后再试"
            ERROR_TAKE_SCREENSHOT_SECURE_WINDOW -> "当前页面禁止截图，请切换到允许截图的页面"
            else -> "系统未返回截图（$error），请稍后重试"
        }
        notice("截屏失败：$reason")
    }

    private fun saveAnnotated() {
        val cv = canvasView ?: return
        if (cv.annots != null && cv.annotsHidden) { notice("标注正在刷新，请稍后保存"); return }
        val nodes = cv.annots
        capture { bmp ->
            if (bmp == null) return@capture // Preserve the actionable screenshot failure message.
            if (canvasView !== cv || cv.annots !== nodes || cv.annotsHidden) {
                notice("标注已更新，请重新保存"); return@capture
            }
            val out = bmp.copy(Bitmap.Config.ARGB_8888, true)
            cv.drawContent(Canvas(out), forExport = true)
            val name = "Spec_" + System.currentTimeMillis() + ".png"
            val ok = runCatching {
                val v = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Spec")
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)!!
                contentResolver.openOutputStream(uri)!!.use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }.isSuccess
            out.recycle()
            notice(if (ok) "已保存到相册：Pictures/Spec/$name" else "保存失败")
        }
    }

    private var noticeView: TextView? = null
    private val noticeHide = Runnable { noticeView?.let { v -> v.animate().alpha(0f).setDuration(160).withEndAction { runCatching { wm.removeView(v) }; if (noticeView === v) noticeView = null }.start() } }
    /**
     * Feedback pill drawn in our own overlay window. Toast from an accessibility service running in the background is
     * silently dropped on this ROM, which made 保存图片 look dead (it worked but showed nothing).
     */
    fun notice(msg: CharSequence) {
        if (!isConnected() || getSystemService(KeyguardManager::class.java).isKeyguardLocked) return
        h.removeCallbacks(noticeHide)
        val v = noticeView ?: TextView(this).apply {
            textSize = 13f; setTextColor(T.TEXT); setPadding(dp(14), dp(9), dp(14), dp(9))
            background = rounded(T.SURFACE, dp(18).toFloat(), T.HAIRLINE, dp(1)); elevation = dp(6).toFloat()
            val lp = WindowManager.LayoutParams(-2, -2, winType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT)
            lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; lp.y = dp(96)
            runCatching { wm.addView(this, lp) }
            noticeView = this
        }
        v.animate().cancel(); v.text = msg; v.alpha = 1f
        h.postDelayed(noticeHide, 2200)
    }

    private fun appRoots(): List<AccessibilityNodeInfo> =
        windows.sortedByDescending { it.layer }.mapNotNull { it.root }.filter { it.packageName != packageName }

    private fun walkAll(f: (AccessibilityNodeInfo, Rect) -> Unit) {
        fun w(n: AccessibilityNodeInfo) {
            if (!n.isVisibleToUser) return
            val r = Rect(); n.getBoundsInScreen(r); f(n, r)
            for (i in 0 until n.childCount) n.getChild(i)?.let { w(it) }
        }
        appRoots().forEach { w(it) }
    }

    fun refreshBounds() {
        val list = mutableListOf<Rect>()
        runCatching { walkAll { _, r -> if (!r.isEmpty) list.add(r) } }
        canvasView?.allBounds = list; canvasView?.invalidate()
    }

    private val btnRe = Regex("Button|Chip|Switch|CheckBox|RadioButton|ToggleButton|FloatingAction|Fab")

    fun classify(n: AccessibilityNodeInfo): Kind {
        val cls = n.className?.toString() ?: ""
        val hasText = !n.text.isNullOrBlank()
        return when {
            btnRe.containsMatchIn(cls) -> Kind.BUTTON
            hasText -> Kind.TEXT
            cls.contains("Image") -> Kind.IMAGE
            n.isClickable -> Kind.BUTTON
            !n.contentDescription.isNullOrBlank() -> Kind.IMAGE
            else -> Kind.CONTAINER
        }
    }

    /** Elements to label plus spacing marks (sibling gaps both axes + padding to parent edges). */
    private fun collectAnnots(): Pair<List<NodeData>, List<Gap>> {
        val dm = resources.displayMetrics
        val screen = Rect(0, 0, dm.widthPixels, dm.heightPixels + dp(200))
        val sArea = dm.widthPixels.toLong() * dm.heightPixels
        val byRect = LinkedHashMap<Rect, NodeData>()
        val gapKeys = HashSet<GapGeometry>(); val gaps = mutableListOf<Gap>()
        val maxGap = dp(240)
        fun addGap(v: Boolean, pos: Int, a: Int, b: Int) {
            if (b - a < dp(1) || b - a > maxGap) return
            val key = GapGeometry(v, pos, a, b)
            if (gapKeys.add(key)) gaps += Gap(v, pos, a, b)
        }
        /** returns the node rect if its subtree contains a labelled element, else null */
        fun rowShape(n: AccessibilityNodeInfo, origin: Rect, depth: Int = 0): RowShape {
            val r = Rect(); n.getBoundsInScreen(r)
            val text = !n.text.isNullOrBlank()
            val children = if (depth >= 8) emptyList() else (0 until n.childCount).mapNotNull { n.getChild(it) }
                .filter { it.isVisibleToUser }.map { rowShape(it, r, depth + 1) }
            return RowShape(n.className?.toString().orEmpty(), n.viewIdResourceName,
                (r.left - origin.left).toFloat(), (r.top - origin.top).toFloat(),
                r.width().toFloat(), r.height().toFloat(), text,
                if (text) fontInfo(n, r).first else null, n.isClickable, n.isCheckable, children)
        }
        fun w(n: AccessibilityNodeInfo, annotate: Boolean = true): Rect? {
            if (!n.isVisibleToUser) return null
            val r = Rect(); n.getBoundsInScreen(r)
            if (r.isEmpty || !Rect.intersects(r, screen)) return null
            val k = classify(n)
            val cls = n.className?.toString() ?: ""
            val include = when (k) {
                Kind.TEXT -> true
                Kind.BUTTON -> r.width().toLong() * r.height() < sArea * 0.4 && (btnRe.containsMatchIn(cls) || r.width() < dm.widthPixels * 0.8)
                Kind.IMAGE -> r.width() >= dp(8) && r.height() >= dp(8)
                Kind.CONTAINER -> cls.contains("Card")
            }
            // Keep every candidate: identical geometry says nothing about color/font/radius/stroke.
            if (include) {
                val old = byRect[r]
                if (old == null || k.ordinal < old.kind.ordinal || (k == Kind.BUTTON && old.kind != Kind.BUTTON)) {
                    val fi = if (!n.text.isNullOrBlank()) fontInfo(n, r) else null
                    byRect[Rect(r)] = NodeData(Rect(r), null, emptyList(), cls, n.viewIdResourceName,
                        n.text?.toString() ?: n.contentDescription?.toString(), fi?.first ?: old?.fontSp, fi?.second ?: old?.fontExact ?: false, fi?.third,
                        if (old != null && old.kind == Kind.BUTTON) Kind.BUTTON else k, n.isClickable || old?.clickable == true, n.packageName?.toString(),
                        structuralRepresentative = annotate || old?.structuralRepresentative == true)
                }
            }
            val kids = ArrayList<Rect>()
            var meaningful = include
            val children = (0 until n.childCount).mapNotNull { n.getChild(it) }.filter { it.isVisibleToUser }
            // Only group sibling containers. Individual labels/icons are never mistaken for repeated rows.
            val signatures = children.map { child ->
                if (child.childCount == 0) null else {
                    val childRect = Rect(); child.getBoundsInScreen(childRect)
                    rowShape(child, childRect).signature()
                }
            }
            val representatives = representativeRows(signatures).map { it.first }.toSet()
            children.forEachIndexed { index, ch ->
                val cr = w(ch, annotate && index in representatives)
                if (cr != null) { meaningful = true; if (cr != r) kids += cr }
            }
            for (a in kids) {
                var below: Rect? = null; var right: Rect? = null; var above = false; var left = false
                for (b in kids) {
                    if (b === a) continue
                    val hOv = b.right > a.left && b.left < a.right
                    val vOv = b.bottom > a.top && b.top < a.bottom
                    if (hOv && b.top >= a.bottom && (below == null || b.top < below.top)) below = b
                    if (vOv && b.left >= a.right && (right == null || b.left < right.left)) right = b
                    if (hOv && b.bottom <= a.top) above = true
                    if (vOv && b.right <= a.left) left = true
                }
                below?.let { b -> addGap(true, (max(a.left, b.left) + min(a.right, b.right)) / 2, a.bottom, b.top) }
                right?.let { b -> addGap(false, (max(a.top, b.top) + min(a.bottom, b.bottom)) / 2, a.right, b.left) }
                // padding to container edges where there is no sibling in between
                if (!above) addGap(true, a.centerX(), r.top, a.top)
                if (below == null && kids.none { it !== a && it.right > a.left && it.left < a.right && it.top >= a.bottom }) addGap(true, a.centerX(), a.bottom, r.bottom)
                if (!left) addGap(false, a.centerY(), r.left, a.left)
                if (right == null) addGap(false, a.centerY(), a.right, r.right)
            }
            return if (meaningful) spacingAnchor(n, k, r, kids) else null
        }
        runCatching { appRoots().forEach { w(it) } }
        return byRect.values.map { withExact(it) } to gaps
    }

    /** Icon-only compound buttons use the visible child's bounds for spacing. */
    private fun spacingAnchor(n: AccessibilityNodeInfo, kind: Kind, rect: Rect, children: List<Rect>): Rect =
        if (kind == Kind.BUTTON && n.text.isNullOrBlank() && children.size == 1) children.first() else rect

    /** Deepest node (smallest area on ties) containing the screen point, in the topmost non-own window. */
    fun findNode(x: Int, y: Int): NodeData? {
        val wins = windows.sortedByDescending { it.layer }
        for (w in wins) {
            val wr = Rect(); w.getBoundsInScreen(wr)
            if (!wr.contains(x, y)) continue
            val root = w.root ?: continue
            if (root.packageName == packageName) continue
            var best: AccessibilityNodeInfo? = null; var bestDepth = -1; var bestArea = Long.MAX_VALUE
            fun walk(n: AccessibilityNodeInfo, d: Int) {
                val r = Rect(); n.getBoundsInScreen(r)
                if (!r.contains(x, y) || !n.isVisibleToUser) return
                val area = r.width().toLong() * r.height()
                if (d > bestDepth || (d == bestDepth && area < bestArea)) { best = n; bestDepth = d; bestArea = area }
                for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, d + 1) }
            }
            walk(root, 0)
            val b = best ?: continue
            return withExact(toData(b))
        }
        return null
    }

    private fun toData(n: AccessibilityNodeInfo): NodeData {
        val r = Rect(); n.getBoundsInScreen(r)
        val par = n.parent
        var pr: Rect? = null
        val sibs = mutableListOf<Rect>()
        if (par != null) {
            pr = Rect().also { par.getBoundsInScreen(it) }
            for (i in 0 until par.childCount) {
                val c = par.getChild(i) ?: continue
                if (c == n || !c.isVisibleToUser) continue
                val cr = Rect(); c.getBoundsInScreen(cr)
                if (cr != r && !cr.isEmpty) sibs.add(cr)
            }
        }
        val txt = n.text?.toString() ?: n.contentDescription?.toString()
        val fi = if (n.text != null) fontInfo(n, r) else null
        val cm = if (fi != null) runCatching { charMetrics(n, r, fi.first) }.getOrNull() else null
        return NodeData(r, pr, sibs, n.className?.toString() ?: "?", n.viewIdResourceName, txt,
            fi?.first, fi?.second ?: false, fi?.third, classify(n), n.isClickable, n.packageName?.toString(),
            cm?.first, cm?.second ?: false, cm?.third)
    }

    private fun fontInfo(n: AccessibilityNodeInfo, r: Rect): Triple<Float, Boolean, String?> {
        val dm = resources.displayMetrics
        val scaled = dm.density * resources.configuration.fontScale
        if (Build.VERSION.SDK_INT >= 30 &&
            n.availableExtraData.contains(AccessibilityNodeInfo.EXTRA_DATA_RENDERING_INFO_KEY)) {
            runCatching {
                n.refreshWithExtraData(AccessibilityNodeInfo.EXTRA_DATA_RENDERING_INFO_KEY, Bundle())
                val info = n.extraRenderingInfo
                if (info != null && info.textSizeInPx > 0) {
                    val px = info.textSizeInPx
                    val unit = when (info.textSizeUnit) {
                        TypedValue.COMPLEX_UNIT_SP -> "sp"; TypedValue.COMPLEX_UNIT_DIP -> "dp"
                        TypedValue.COMPLEX_UNIT_PX -> "px"; else -> "单位${info.textSizeUnit}"
                    }
                    return Triple(px / scaled, true, unit)
                }
            }
        }
        val lines = (n.text?.count { it == '\n' } ?: 0) + 1
        val px = r.height() / lines / 1.35f
        return Triple(px / scaled, false, null)
    }

    fun density() = resources.displayMetrics.density

    /**
     * Line height (px, exact?) and letter spacing (px) from per-character bounds.
     * Line height = median distance between distinct line tops; single line falls back to node height.
     * Letter spacing = median(char advance from bounds − advance measured with the default typeface): rough.
     */
    private fun charMetrics(n: AccessibilityNodeInfo, r: Rect, sp: Float): Triple<Float?, Boolean, Float?> {
        val text = n.text?.toString() ?: return Triple(null, false, null)
        val fontPx = sp * resources.displayMetrics.density * resources.configuration.fontScale
        val lines = text.count { it == '\n' } + 1
        val fallback = Triple<Float?, Boolean, Float?>(r.height().toFloat() / lines, false, null)
        val key = AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY
        if (text.length < 2 || !n.availableExtraData.contains(key)) return fallback
        val cnt = min(text.length, 300)
        val args = Bundle().apply {
            putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, 0)
            putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH, cnt)
        }
        if (!n.refreshWithExtraData(key, args)) return fallback
        @Suppress("DEPRECATION")
        val arr = n.extras.getParcelableArray(key) ?: return fallback
        val boxes = arr.map { it as? android.graphics.RectF }
        val tops = boxes.filterNotNull().filter { it.height() > 0 }.map { it.top }.distinctBy { (it / 2).toInt() }.sorted()
        var lh: Float? = null; var exact = false
        if (tops.size >= 2) {
            val diffs = tops.zipWithNext { a, b -> b - a }.filter { it > fontPx * 0.6f }.sorted()
            if (diffs.isNotEmpty()) { lh = diffs[diffs.size / 2]; exact = true }
        }
        if (lh == null) {
            val h = boxes.filterNotNull().map { it.height() }.filter { it > 0 }.sorted()
            lh = if (h.isNotEmpty()) h[h.size / 2] else r.height().toFloat() / lines
            exact = h.isNotEmpty()
        }
        val p = android.graphics.Paint().apply { textSize = fontPx }
        val extra = ArrayList<Float>()
        for (i in 0 until min(boxes.size, cnt) - 1) {
            val b = boxes[i] ?: continue; val nx = boxes[i + 1] ?: continue
            val ch = text[i]
            if (ch.isWhitespace() || abs(nx.top - b.top) > 1f) continue
            extra += (nx.left - b.left) - p.measureText(ch.toString())
        }
        extra.sort()
        val ls = if (extra.size >= 2) extra[extra.size / 2] else null
        return Triple(lh, exact, ls)
    }
}
