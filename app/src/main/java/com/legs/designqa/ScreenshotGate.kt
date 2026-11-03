package com.legs.designqa

/** Main-thread capture gate. Merge consumers and pace every platform request, including fallbacks. */
internal class ScreenshotGate<T>(private val clock: () -> Long) {
    private var busy = false
    val isBusy: Boolean get() = busy
    private var lastAttempt: Long? = null
    private val callbacks = mutableListOf<(T?) -> Unit>()
    private var latestRefresh: (() -> Unit)? = null

    /** Automatic refreshes have one pending slot; explicit consumers remain independent. */
    fun deferRefresh(refresh: () -> Unit) { latestRefresh = refresh }

    fun begin(after: ((T?) -> Unit)? = null): Boolean {
        if (after != null) callbacks += after
        if (busy) return false
        busy = true
        return true
    }

    fun delayBeforeAttempt(): Long =
        lastAttempt?.let { (400L - (clock() - it)).coerceAtLeast(0) } ?: 0

    fun attemptStarted() { lastAttempt = clock() }

    fun shouldRetry(error: Int, retry: Int) = retry < 2 && error in setOf(1, 3)

    fun finish(result: T?) {
        val waiting = callbacks.toList()
        val refresh = latestRefresh
        latestRefresh = null
        callbacks.clear()
        busy = false
        waiting.forEach { callback -> runCatching { callback(result) } }
        refresh?.let { runCatching(it) }
    }
}
