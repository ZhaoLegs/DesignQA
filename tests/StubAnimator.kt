package android.animation
/** Deterministic scheduler: start schedules future frames, like ValueAnimator. */
class ValueAnimator(private val target: Float) {
    var duration = 0L
    var animatedValue: Any = 0f
    private var listener: ((ValueAnimator) -> Unit)? = null
    fun addUpdateListener(fn: (ValueAnimator) -> Unit) { listener = fn }
    fun start() { active += this }
    fun cancel() { active.remove(this) }
    companion object {
        private val active = mutableListOf<ValueAnimator>()
        fun ofFloat(vararg values: Float) = ValueAnimator(values.last())
        fun finishAll() {
            val waiting = active.toList(); active.clear()
            waiting.forEach { it.animatedValue = it.target; it.listener?.invoke(it) }
        }
    }
}
