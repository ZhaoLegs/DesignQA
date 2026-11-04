package com.legs.specsample

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.ViewFlipper
import kotlin.math.abs

/**
 * Spec Sample: full-page left/right swipe between three pages —
 * (0) 字重与样式, (1) 今天日期, (2) 中文倾斜.
 * No top title chrome / tabs; swipe alone is the switcher.
 */
class MainActivity : Activity() {
    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
    private fun dpi(v: Float) = dp(v).toInt()

    private lateinit var flipper: ViewFlipper
    /** When false (SeekBar scrubbing), ignore page flings. */
    @JvmField var pageSwipeEnabled = true

    private var swipeStartX = 0f
    private var swipeStartY = 0f
    private var swipeTracking = false
    private var swipeTriggered = false
    private var touchSlop = 0
    private var swipeMinDx = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.WHITE
        touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        swipeMinDx = dp(64f)

        // No top title / 「今天日期」 chrome — content is full-bleed
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            setPadding(0, dpi(24f), 0, 0)
        }

        flipper = ViewFlipper(this).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            // Instant switch — fade can race with dumps / look like swipe failed
            inAnimation = null
            outAnimation = null
        }

        // Three pages: styles | date | italic
        flipper.addView(wrapScroll(pageStyles()))
        flipper.addView(buildDatePage()) // FrameLayout overlay; do NOT wrap in ScrollView
        flipper.addView(wrapScroll(pageItalic()))
        root.addView(flipper)

        val start = intent.getIntExtra("page", 0).coerceIn(0, flipper.childCount - 1)
        flipper.displayedChild = start
        markPage()
        setContentView(root)
    }

    private fun markPage() {
        // Machine-readable page marker for adb dumps (not a visible title)
        flipper.contentDescription = "spec_page_${flipper.displayedChild}"
        title = "spec_page_${flipper.displayedChild}"
    }

    /**
     * Whole-page horizontal swipe via Activity dispatch — fires even when
     * ScrollView / panel children consume or CANCEL the stream.
     * Flip on MOVE once threshold hit (do not wait for UP).
     * SeekBar sets pageSwipeEnabled=false while scrubbing.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeStartX = ev.rawX
                swipeStartY = ev.rawY
                swipeTracking = pageSwipeEnabled
                swipeTriggered = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // Multi-touch: abort page swipe
                swipeTracking = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!pageSwipeEnabled) {
                    swipeTracking = false
                } else if (swipeTracking && !swipeTriggered) {
                    val dx = ev.rawX - swipeStartX
                    val dy = ev.rawY - swipeStartY
                    if (abs(dx) >= swipeMinDx && abs(dx) > abs(dy) * 1.15f) {
                        swipeTriggered = true
                        if (dx < 0f) showNext() else showPrev()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Fallback: if MOVE never crossed threshold but UP has a clear horizontal delta
                if (swipeTracking && pageSwipeEnabled && !swipeTriggered) {
                    val dx = ev.rawX - swipeStartX
                    val dy = ev.rawY - swipeStartY
                    if (abs(dx) >= swipeMinDx && abs(dx) > abs(dy) * 1.15f) {
                        swipeTriggered = true
                        if (dx < 0f) showNext() else showPrev()
                    }
                }
                swipeTracking = false
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun showNext() {
        if (!::flipper.isInitialized) return
        if (flipper.displayedChild >= flipper.childCount - 1) return
        flipper.displayedChild = flipper.displayedChild + 1
        markPage()
    }

    private fun showPrev() {
        if (!::flipper.isInitialized) return
        if (flipper.displayedChild <= 0) return
        flipper.displayedChild = flipper.displayedChild - 1
        markPage()
    }

    /** ScrollView: do not intercept clearly-horizontal moves. */
    private fun wrapScroll(content: View) = object : ScrollView(this) {
        private var startX = 0f
        private var startY = 0f

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = ev.x
                    startY = ev.y
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = abs(ev.x - startX)
                    val dy = abs(ev.y - startY)
                    if (dx > dy && dx > touchSlop) {
                        return false
                    }
                }
            }
            return try {
                super.onInterceptTouchEvent(ev)
            } catch (_: Exception) {
                false
            }
        }
    }.apply {
        addView(content)
        isFillViewport = true
        setBackgroundColor(Color.WHITE)
        overScrollMode = View.OVER_SCROLL_NEVER
    }

    private fun pageStyles(): LinearLayout {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(24f), dpi(16f), dpi(24f), dpi(40f))
        }
        fun add(label: String, block: TextView.() -> Unit) = col.addView(TextView(this).apply {
            text = label; textSize = 20f; setTextColor(Color.parseColor("#1C1C1E"))
            setPadding(0, dpi(10f), 0, dpi(10f)); block()
        })
        add("Spec Sample 字重 300 Light") { typeface = Typeface.create(Typeface.DEFAULT, 300, false) }
        add("Spec Sample 字重 400 Regular") { typeface = Typeface.create(Typeface.DEFAULT, 400, false) }
        add("Spec Sample 字重 500 Medium") { typeface = Typeface.create(Typeface.DEFAULT, 500, false) }
        add("Spec Sample 字重 600 SemiBold") { typeface = Typeface.create(Typeface.DEFAULT, 600, false) }
        add("Spec Sample 字重 700 Bold") { typeface = Typeface.create(Typeface.DEFAULT, 700, false) }
        add("textStyle=bold（400→700）") { setTypeface(null, Typeface.BOLD) }
        add("Letter spacing 0.1em") { letterSpacing = 0.1f; textSize = 16f }
        add("行高 32dp 的多行文本示例，第二行用于验证行高测量是否准确。") { textSize = 15f; lineHeight = dpi(32f) }
        add("serif 衬线 600") { typeface = Typeface.create(Typeface.create("serif", Typeface.NORMAL), 600, false); textSize = 18f }
        add("按钮 圆角 12 描边 2") {
            textSize = 16f; setTextColor(Color.WHITE)
            setPadding(dpi(20f), dpi(12f), dpi(20f), dpi(12f))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#007AFF")); cornerRadius = dp(12f)
                setStroke(dpi(2f), Color.parseColor("#FF2D55"))
            }
        }
        return col
    }

    private fun pageItalic(): LinearLayout {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(24f), dpi(16f), dpi(24f), dpi(40f))
        }
        fun label(s: String) = col.addView(TextView(this).apply {
            text = s; textSize = 13f; setTextColor(Color.parseColor("#8A8A8E"))
            setPadding(0, dpi(16f), 0, dpi(6f))
        })
        fun sample(s: String, block: TextView.() -> Unit) = col.addView(TextView(this).apply {
            text = s; textSize = 22f; setTextColor(Color.parseColor("#1C1C1E"))
            setPadding(0, dpi(4f), 0, dpi(4f)); block()
        })
        label("① 正体对照")
        sample("设计走查 字体倾斜 Spec") { typeface = Typeface.create(Typeface.DEFAULT, 400, false) }
        label("② Typeface.ITALIC")
        sample("设计走查 字体倾斜 Spec") { setTypeface(typeface, Typeface.ITALIC) }
        label("③ Typeface.create(…, italic=true)")
        sample("设计走查 字体倾斜 Spec") {
            typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, 400, true)
            else Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        }
        label("④ 假斜体 paint.textSkewX = -0.25")
        sample("设计走查 字体倾斜 Spec") {
            typeface = Typeface.create(Typeface.DEFAULT, 400, false); paint.textSkewX = -0.25f
        }
        label("⑤ 假斜体 + Medium 500")
        sample("设计走查 字体倾斜 Spec") {
            typeface = Typeface.create(Typeface.DEFAULT, 500, false); paint.textSkewX = -0.25f
        }
        label("⑥ 假斜体 + Bold 700")
        sample("设计走查 字体倾斜 Spec") {
            typeface = Typeface.create(Typeface.DEFAULT, 700, false); paint.textSkewX = -0.25f
        }
        label("⑦ 英文真斜体对照")
        sample("Spec Italic Sample Design QA") { setTypeface(Typeface.SANS_SERIF, Typeface.ITALIC) }
        return col
    }
}
