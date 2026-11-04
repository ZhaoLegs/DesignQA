package android.view
import android.content.Context
import android.graphics.Canvas
open class View(ctx: Context) {
    val resources = ctx.resources
    var width = 51
    var height = 31
    private var click: ((View) -> Unit)? = null
    fun setLayerType(t: Int, p: Any?) {}
    fun setOnClickListener(handler: (View) -> Unit) { click = handler }
    fun performClick() { click?.invoke(this) }
    fun invalidate() {}
    fun setMeasuredDimension(w: Int,h: Int) { width = w; height = h }
    open fun onMeasure(w: Int,h: Int) {}
    open fun onDraw(c: Canvas) {}
    open fun onDetachedFromWindow() {}
    companion object { const val LAYER_TYPE_SOFTWARE = 1 }
}
