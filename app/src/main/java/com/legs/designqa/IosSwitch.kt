package com.legs.designqa

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** Homepage-only Konsta iOS toggle: 64×28dp track and a 38×24dp pill thumb. */
class IosSwitch(ctx: Context) : View(ctx) {
    var checked = false; private set
    var onChange: ((Boolean) -> Unit)? = null
    private var pos = 0f
    private var animator: ValueAnimator? = null
    private val d = resources.displayMetrics.density
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; setShadowLayer(d, 0f, 0.5f * d, 0x24000000) }
    private val on = HomeStyle.accent; private val off = HomeStyle.toggleOff

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        setOnClickListener { setChecked(!checked, true); onChange?.invoke(checked) }
    }

    fun setChecked(v: Boolean, animate: Boolean = false) {
        animator?.cancel()
        animator = null
        checked = v
        val to = if (v) 1f else 0f
        if (!animate) { pos = to; invalidate(); return }
        animator = ValueAnimator.ofFloat(pos, to).apply {
            duration = HomeStyle.motionMs
            addUpdateListener { if (animator === it) { pos = it.animatedValue as Float; invalidate() } }
        }
        animator?.start()
    }

    override fun onDetachedFromWindow() {
        setChecked(checked)
        super.onDetachedFromWindow()
    }

    // Keep a 44dp touch height while drawing the reference's compact 28dp track.
    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension((HomeStyle.toggleWidth * d).toInt(), (44 * d).toInt())

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = HomeStyle.toggleHeight * d
        val top = (height - h) / 2f
        val r = h / 2
        track.color = blend(off, on, pos)
        c.drawRoundRect(0f, top, w, top + h, r, r, track)
        val thumbW = HomeStyle.thumbWidth * d
        val thumbH = HomeStyle.thumbHeight * d
        val x = 2 * d + (w - thumbW - 4 * d) * pos
        val y = (height - thumbH) / 2f
        c.drawRoundRect(x, y, x + thumbW, y + thumbH, thumbH / 2, thumbH / 2, knob)
    }

    private fun blend(a: Int, b: Int, t: Float) = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt())
}
