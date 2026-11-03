package com.legs.designqa

internal object OverlayState {
    fun showing(connected: Boolean, canvasAttached: Boolean, bubbleAttached: Boolean,
                inScreen: Boolean, visible: Boolean, capturing: Boolean): Boolean =
        connected && canvasAttached && bubbleAttached && inScreen && (visible || capturing)

    fun position(value: Int, screen: Int, size: Int, edge: Int): Int =
        if (screen <= size) 0 else value.coerceIn(-edge, screen - size + edge)
}
