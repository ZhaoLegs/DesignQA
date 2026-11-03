package com.legs.designqa

internal enum class ConnectionState { DISABLED, CONNECTING, CONNECTED, INTERRUPTED }

/** Settings express user intent; only a live service binding makes inspection available. */
internal fun connectionState(enabled: Boolean, connected: Boolean, waitingMillis: Long): ConnectionState =
    when {
        connected -> ConnectionState.CONNECTED
        !enabled -> ConnectionState.DISABLED
        waitingMillis < 8_000 -> ConnectionState.CONNECTING
        else -> ConnectionState.INTERRUPTED
    }
