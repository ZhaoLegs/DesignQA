package com.legs.designqa

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** UISwitch look-alike: 51x31pt track, green #34C759 when on, white knob with soft shadow. */
class IosSwitch(ctx: Context) : View(ctx) {
    var checked = false; private set
    var onChange: ((Boolean) -> Unit)? = null
    private var pos = 0f
    private val d = resources.displayMetrics.density
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; setShadowLayer(2.5f * d, 0f, 1.5f * d, 0x40000000) }
    private val on = Color.parseColor("#34C759"); private val off = Color.parseColor("#E9E9EA")

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        setOnClickListener { setChecked(!checked, true); onChange?.invoke(checked) }
    }

    fun setChecked(v: Boolean, animate: Boolean = false) {
        checked = v
        val to = if (v) 1f else 0f
        if (!animate) { pos = to; invalidate(); return }
        ValueAnimator.ofFloat(pos, to).apply { duration = 200; addUpdateListener { pos = it.animatedValue as Float; invalidate() }; start() }
    }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension((51 * d).toInt(), (31 * d).toInt())

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val r = h / 2
        track.color = blend(off, on, pos)
        c.drawRoundRect(0f, 0f, w, h, r, r, track)
        val kr = r - 2 * d
        val cx = r + (w - 2 * r) * pos
        c.drawCircle(cx, r, kr, knob)
    }

    private fun blend(a: Int, b: Int, t: Float) = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt())
}
