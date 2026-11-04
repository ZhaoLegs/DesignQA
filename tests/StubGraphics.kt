package android.graphics
class Canvas {
    fun drawRoundRect(a: Float,b: Float,c: Float,d: Float,e: Float,f: Float,p: Paint) {}
    fun drawCircle(a: Float,b: Float,c: Float,p: Paint) {}
}
class Paint(flags: Int) {
    var color = 0
    fun setShadowLayer(a: Float,b: Float,c: Float,d: Int) {}
    companion object { const val ANTI_ALIAS_FLAG = 1 }
}
object Color {
    const val BLACK = -16777216
    const val WHITE = -1
    fun parseColor(value: String) = value.drop(1).toLong(16).toInt()
    fun red(v: Int) = v shr 16 and 255
    fun green(v: Int) = v shr 8 and 255
    fun blue(v: Int) = v and 255
    fun rgb(r: Int,g: Int,b: Int) = (r shl 16) or (g shl 8) or b
}
