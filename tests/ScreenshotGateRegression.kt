package com.legs.designqa

fun main() {
    var now = 0L
    val gate = ScreenshotGate<String> { now }
    val results = mutableListOf<String?>()
    check(gate.begin { results += it })
    check(!gate.begin { results += it })
    check(gate.delayBeforeAttempt() == 0L)
    gate.attemptStarted()
    now = 80
    check(gate.delayBeforeAttempt() == 320L) // immediate window -> display fallback
    now = 280
    check(gate.delayBeforeAttempt() == 120L) // scroll refresh
    now = 400
    check(gate.delayBeforeAttempt() == 0L)
    gate.finish("image")
    check(results == listOf("image", "image"))
    check(gate.begin { results += it })
    gate.finish(null)
    check(results.last() == null)
    check(gate.begin { check(gate.begin()); gate.finish(null) })
    gate.finish("image") // reentrant callbacks must not leave the gate busy
    check(gate.begin())
    gate.finish(null)
    check(gate.shouldRetry(3, 0))
    check(gate.shouldRetry(1, 1))
    check(!gate.shouldRetry(3, 2))
    check(!gate.shouldRetry(2, 0)) // permission
    check(!gate.shouldRetry(6, 0)) // secure window
    println("PASS: capture coalescing, pacing, failure recovery and bounded retries")
}
