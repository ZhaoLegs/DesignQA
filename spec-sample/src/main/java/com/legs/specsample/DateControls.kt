package com.legs.specsample

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Date preview: lockscreen-style two-line clock + floating leva dark panel (3 spacing modes).
 * Leva tokens ONLY from the listed stitches.config values in the task brief.
 *
 * Mode0 默认: single date line, locked letterSpacing
 * Mode1 字距: single date line, seek letterSpacing
 * Mode2 数·汉: FIVE separate TextViews 「10」「月」「15」「日」「周四」— gap only between groups
 * Time row is FIXED (never follows spacing).
 */
internal fun Activity.buildDatePage(): FrameLayout {
    fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
    fun dpi(v: Float) = dp(v).toInt()

    val elevation1 = Color.parseColor("#292d39")
    val elevation2 = Color.parseColor("#181c20")
    val elevation3 = Color.parseColor("#373c4b")
    val accent2 = Color.parseColor("#007bff")
    val highlight2 = Color.parseColor("#8c92a4")
    val highlight3 = Color.parseColor("#fefefe")
    val shadowColor = Color.parseColor("#00000088")
    val textPrimary = Color.parseColor("#1C1C1E")
    val radiusLg = dp(10f)
    val fontRootSp = 11f
    val rowHeight = dpi(24f)
    val controlWidth = dpi(160f)
    val rootWidth = dpi(280f)

    /** Exactly five groups: monthNum | 月 | dayNum | 日 | 周四 */
    fun fiveGroups(now: Date = Date()): List<String> {
        val cal = Calendar.getInstance().apply { time = now }
        val month = (cal.get(Calendar.MONTH) + 1).toString()
        val day = cal.get(Calendar.DAY_OF_MONTH).toString()
        // Short weekday 「周四」; strip leading 星期 if a device returns full form
        var weekday = SimpleDateFormat("E", Locale.CHINA).format(now)
        if (weekday.startsWith("星期") && weekday.length > 2) {
            weekday = "周" + weekday.removePrefix("星期")
        }
        return listOf(month, "月", day, "日", weekday)
    }

    fun nowDateLine() = fiveGroups().let { g -> "${g[0]}${g[1]}${g[2]}${g[3]} ${g[4]}" }
    fun nowTimeLine() = SimpleDateFormat("HH:mm", Locale.US).format(Date())

    var mode = 0
    var letterSpacingEm = 0f
    val letterSpacingDefault = 0f
    var numCnGapDp = 8 // default gap — visibly separated groups
    val numCnGapDefault = 8

    val root = FrameLayout(this).apply {
        layoutParams = FrameLayout.LayoutParams(-1, -1)
        setBackgroundColor(Color.WHITE)
        contentDescription = "date_page_root"
    }

    val preview = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        layoutParams = FrameLayout.LayoutParams(-1, -1)
        setPadding(dpi(24f), dpi(8f), dpi(24f), dpi(120f))
    }

    // Host for either single dateTv OR five group TextViews
    val dateHost = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(-1, -2)
        contentDescription = "date_host"
    }

    val dateTv = TextView(this).apply {
        textSize = 18f
        setTextColor(textPrimary)
        gravity = Gravity.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, 500, false)
        includeFontPadding = false
        contentDescription = "date_line_single"
    }

    // Pre-create five group views — reused in Mode 数·汉 only
    val groupTvs = Array(5) { idx ->
        TextView(this).apply {
            textSize = 18f
            setTextColor(textPrimary)
            typeface = Typeface.create(Typeface.DEFAULT, 500, false)
            includeFontPadding = false
            letterSpacing = 0f
            contentDescription = "date_group_$idx"
        }
    }

    // Time row FIXED — never follows spacing modes
    val timeTv = TextView(this).apply {
        textSize = 56f
        setTextColor(textPrimary)
        gravity = Gravity.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, 400, false)
        includeFontPadding = false
        setPadding(0, dpi(8f), 0, 0)
        letterSpacing = 0.02f
        contentDescription = "time_line_fixed"
    }

    preview.addView(dateHost)
    preview.addView(timeTv)

    fun attachFiveGroups() {
        dateHost.removeAllViews()
        dateHost.contentDescription = "date_host_groups"
        val groups = fiveGroups()
        groupTvs.forEachIndexed { index, tv ->
            tv.text = groups[index]
            tv.letterSpacing = 0f
            val lp = LinearLayout.LayoutParams(-2, -2)
            if (index > 0) {
                lp.leftMargin = dpi(numCnGapDp.toFloat())
            } else {
                lp.leftMargin = 0
            }
            dateHost.addView(tv, lp)
        }
    }

    fun updateFiveGroupMarginsOnly() {
        // Seek scrub: only margins change — keep the five views
        for (i in 1 until dateHost.childCount) {
            val lp = dateHost.getChildAt(i).layoutParams as? LinearLayout.LayoutParams ?: continue
            lp.leftMargin = dpi(numCnGapDp.toFloat())
            dateHost.getChildAt(i).layoutParams = lp
        }
        // Refresh texts (minute/day rollover) without destroying views
        val groups = fiveGroups()
        groupTvs.forEachIndexed { index, tv ->
            if (tv.text.toString() != groups[index]) tv.text = groups[index]
        }
    }

    fun attachSingleLine() {
        dateHost.removeAllViews()
        dateHost.contentDescription = "date_host_single"
        // Detach from any prior parent before re-add
        (dateTv.parent as? android.view.ViewGroup)?.removeView(dateTv)
        dateHost.addView(dateTv, LinearLayout.LayoutParams(-2, -2))
    }

    fun rebuildDateLine(forceStructure: Boolean = true) {
        when (mode) {
            0 -> {
                if (forceStructure || dateHost.contentDescription != "date_host_single") {
                    attachSingleLine()
                }
                dateTv.letterSpacing = letterSpacingDefault
                dateTv.text = nowDateLine()
            }
            1 -> {
                if (forceStructure || dateHost.contentDescription != "date_host_single") {
                    attachSingleLine()
                }
                dateTv.letterSpacing = letterSpacingEm
                dateTv.text = nowDateLine()
            }
            else -> {
                // Mode 数·汉 — ALWAYS five separate views
                if (forceStructure || dateHost.childCount != 5 || dateHost.contentDescription != "date_host_groups") {
                    attachFiveGroups()
                } else {
                    updateFiveGroupMarginsOnly()
                }
            }
        }
    }

    fun applyPreview(forceStructure: Boolean = false) {
        rebuildDateLine(forceStructure = forceStructure)
        timeTv.text = nowTimeLine()
        timeTv.letterSpacing = 0.02f // always fixed
    }
    applyPreview(forceStructure = true)

    val tick = Handler(Looper.getMainLooper())
    val ticker = object : Runnable {
        override fun run() {
            // Soft update — do not tear down five-group structure every second
            applyPreview(forceStructure = false)
            tick.postDelayed(this, 1000L)
        }
    }
    tick.post(ticker)
    root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            tick.removeCallbacks(ticker)
            tick.post(ticker)
        }
        override fun onViewDetachedFromWindow(v: View) {
            tick.removeCallbacks(ticker)
        }
    })

    fun setPageSwipeEnabled(enabled: Boolean) {
        (this as? MainActivity)?.pageSwipeEnabled = enabled
    }

    fun scrubTouchGuard(seek: SeekBar) {
        seek.setOnTouchListener { v, _ ->
            v.parent?.requestDisallowInterceptTouchEvent(true)
            false
        }
    }

    val titleBar = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = GradientDrawable().apply {
            setColor(elevation1)
            cornerRadii = floatArrayOf(radiusLg, radiusLg, radiusLg, radiusLg, 0f, 0f, 0f, 0f)
        }
        minimumHeight = dpi(28f)
        setPadding(dpi(10f), dpi(6f), dpi(10f), dpi(6f))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dpi(7f)
        }
    }
    titleBar.addView(TextView(this).apply {
        text = "Date Spacing"
        textSize = fontRootSp
        setTextColor(highlight3)
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
    })
    titleBar.addView(TextView(this).apply {
        text = "leva"
        textSize = fontRootSp
        setTextColor(highlight2)
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
    })

    val modeLabel = TextView(this).apply {
        text = "mode"
        textSize = fontRootSp
        setTextColor(highlight2)
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        layoutParams = LinearLayout.LayoutParams(-2, rowHeight).apply {
            gravity = Gravity.CENTER_VERTICAL
            rightMargin = dpi(7f)
        }
        gravity = Gravity.CENTER_VERTICAL
    }
    val modeRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = rowHeight
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dpi(7f) }
    }
    modeRow.addView(modeLabel)

    val modeButtons = mutableListOf<TextView>()
    val modeBtnRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(0, rowHeight, 1f)
        background = GradientDrawable().apply {
            setColor(elevation3)
            cornerRadius = dp(3f)
        }
        setPadding(dpi(2f), dpi(2f), dpi(2f), dpi(2f))
    }

    fun refreshModeButtons() {
        modeButtons.forEachIndexed { i, tv ->
            tv.background = if (i == mode) {
                GradientDrawable().apply {
                    setColor(accent2)
                    cornerRadius = dp(2f)
                }
            } else {
                null
            }
            tv.setTextColor(if (i == mode) highlight3 else highlight2)
        }
    }

    val controlHost = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    lateinit var rebuildControls: () -> Unit

    fun selectMode(newMode: Int) {
        if (mode == newMode) {
            // Still force-rebuild date structure (idempotent)
            applyPreview(forceStructure = true)
            return
        }
        mode = newMode
        refreshModeButtons()
        rebuildControls()
        applyPreview(forceStructure = true)
    }

    rebuildControls = {
        controlHost.removeAllViews()
        when (mode) {
            0 -> {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = rowHeight
                }
                row.addView(TextView(this).apply {
                    text = "letterSpacing"
                    textSize = fontRootSp
                    setTextColor(highlight2)
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                })
                row.addView(TextView(this).apply {
                    text = "default (0)"
                    textSize = fontRootSp
                    setTextColor(highlight3)
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply {
                        setColor(elevation3)
                        cornerRadius = dp(3f)
                    }
                    setPadding(dpi(8f), dpi(4f), dpi(8f), dpi(4f))
                    layoutParams = LinearLayout.LayoutParams(controlWidth, rowHeight)
                })
                controlHost.addView(row)
                letterSpacingEm = letterSpacingDefault
            }
            1 -> {
                val valueTv = TextView(this).apply {
                    textSize = fontRootSp
                    setTextColor(highlight3)
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply {
                        setColor(elevation3)
                        cornerRadius = dp(3f)
                    }
                    setPadding(dpi(6f), 0, dpi(6f), 0)
                    layoutParams = LinearLayout.LayoutParams(dpi(52f), rowHeight)
                }
                fun fmt() {
                    valueTv.text = String.format(Locale.US, "%.2f", letterSpacingEm)
                }
                fmt()
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = rowHeight
                }
                row.addView(TextView(this).apply {
                    text = "letterSpacing"
                    textSize = fontRootSp
                    setTextColor(highlight2)
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                })
                val seek = SeekBar(this).apply {
                    max = 40
                    progress = ((letterSpacingEm + 0.05f) * 100f).toInt().coerceIn(0, 40)
                    splitTrack = false
                    progressDrawable = GradientDrawable().apply {
                        setColor(accent2)
                        cornerRadius = dp(2f)
                    }
                    thumb = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(highlight3)
                        setSize(dpi(12f), dpi(12f))
                    }
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                            letterSpacingEm = (progress / 100f) - 0.05f
                            fmt()
                            applyPreview(forceStructure = false)
                        }
                        override fun onStartTrackingTouch(seekBar: SeekBar?) {
                            setPageSwipeEnabled(false)
                            seekBar?.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                        override fun onStopTrackingTouch(seekBar: SeekBar?) {
                            setPageSwipeEnabled(true)
                            seekBar?.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    })
                }
                scrubTouchGuard(seek)
                val scrubCol = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(controlWidth, rowHeight)
                }
                scrubCol.addView(seek, LinearLayout.LayoutParams(0, rowHeight, 1f))
                scrubCol.addView(valueTv)
                row.addView(scrubCol)
                controlHost.addView(row)
            }
            else -> {
                val valueTv = TextView(this).apply {
                    textSize = fontRootSp
                    setTextColor(highlight3)
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply {
                        setColor(elevation3)
                        cornerRadius = dp(3f)
                    }
                    setPadding(dpi(6f), 0, dpi(6f), 0)
                    layoutParams = LinearLayout.LayoutParams(dpi(52f), rowHeight)
                    text = "${numCnGapDp}dp"
                }
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = rowHeight
                }
                row.addView(TextView(this).apply {
                    text = "group gap"
                    textSize = fontRootSp
                    setTextColor(highlight2)
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                })
                val seek = SeekBar(this).apply {
                    max = 32
                    progress = numCnGapDp.coerceIn(0, 32)
                    splitTrack = false
                    progressDrawable = GradientDrawable().apply {
                        setColor(accent2)
                        cornerRadius = dp(2f)
                    }
                    thumb = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(highlight3)
                        setSize(dpi(12f), dpi(12f))
                    }
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                            numCnGapDp = progress
                            valueTv.text = "${numCnGapDp}dp"
                            // Update margins on the existing five views
                            applyPreview(forceStructure = false)
                        }
                        override fun onStartTrackingTouch(seekBar: SeekBar?) {
                            setPageSwipeEnabled(false)
                            seekBar?.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                        override fun onStopTrackingTouch(seekBar: SeekBar?) {
                            setPageSwipeEnabled(true)
                            seekBar?.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    })
                }
                scrubTouchGuard(seek)
                val scrubCol = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(controlWidth, rowHeight)
                }
                scrubCol.addView(seek, LinearLayout.LayoutParams(0, rowHeight, 1f))
                scrubCol.addView(valueTv)
                row.addView(scrubCol)
                controlHost.addView(row)
                controlHost.addView(TextView(this).apply {
                    text = "10|月|15|日|周四 gaps only (def ${numCnGapDefault}dp)"
                    textSize = fontRootSp
                    setTextColor(highlight2)
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                    setPadding(0, dpi(4f), 0, 0)
                })
            }
        }
    }

    listOf("默认", "字距", "数·汉").forEachIndexed { i, label ->
        val btn = TextView(this).apply {
            text = label
            textSize = fontRootSp
            gravity = Gravity.CENTER
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(0, -1, 1f)
            isClickable = true
            isFocusable = true
            contentDescription = "mode_btn_$i"
            setOnClickListener { selectMode(i) }
        }
        modeButtons.add(btn)
        modeBtnRow.addView(btn)
    }
    modeRow.addView(modeBtnRow)

    val panelFrame = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(rootWidth, -2)
        background = GradientDrawable().apply {
            setColor(elevation2)
            cornerRadius = radiusLg
        }
        elevation = dp(9f)
        if (Build.VERSION.SDK_INT >= 28) {
            outlineSpotShadowColor = shadowColor
            outlineAmbientShadowColor = shadowColor
        }
        clipToOutline = false
    }
    panelFrame.addView(titleBar)
    val body = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dpi(10f), 0, dpi(10f), dpi(10f))
    }
    body.addView(modeRow)
    body.addView(controlHost)
    panelFrame.addView(body)

    refreshModeButtons()
    rebuildControls()
    applyPreview(forceStructure = true)

    val panelHolder = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        layoutParams = FrameLayout.LayoutParams(-1, -2).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            leftMargin = dpi(16f)
            rightMargin = dpi(16f)
            bottomMargin = dpi(24f)
        }
    }
    panelHolder.addView(panelFrame)

    root.addView(preview)
    root.addView(panelHolder)
    return root
}
