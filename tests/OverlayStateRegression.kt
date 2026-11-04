package com.legs.designqa

fun main() {
    check(OverlayState.showing(true, true, true, true, true, false))
    check(!OverlayState.showing(true, true, false, true, true, false))
    check(!OverlayState.showing(false, true, true, true, true, false))
    check(!OverlayState.showing(true, true, true, false, true, false))
    check(!OverlayState.showing(true, true, true, true, false, false))
    // Screenshot temporarily hides controls without turning the overlay off.
    check(OverlayState.showing(true, true, true, true, false, true))
    check(!OverlayState.showing(true, true, false, true, false, true))
    check(OverlayState.position(-500, 1000, 100, 2) == -2)
    check(OverlayState.position(5000, 1000, 100, 2) == 902)
    check(OverlayState.position(180, 1000, 100, 2) == 180)
    check(OverlayState.position(500, 60, 100, 2) == 0)
    println("OverlayStateRegression: passed")
}
