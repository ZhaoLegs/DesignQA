package com.legs.designqa

import android.graphics.Color

/**
 * Native homepage tokens adapted from Konsta UI 5.5.0's iOS theme.
 * Reference: https://konstaui.com/kitchen-sink/react/dist/
 * These tokens apply to MainActivity and its switches only.
 */
internal object HomeStyle {
    val page = Color.parseColor("#EFEFF4")
    val surface = Color.WHITE
    val text = Color.BLACK
    val secondary = Color.parseColor("#8A8A8E")
    val sectionText = Color.parseColor("#666666")
    val accent = Color.parseColor("#007AFF")
    val accentTint = Color.parseColor("#14007AFF")
    val divider = Color.parseColor("#D9D9DE")
    val success = Color.parseColor("#248A3D")
    val successTint = Color.parseColor("#1434C759")
    val danger = Color.parseColor("#FF3B30")
    val dangerTint = Color.parseColor("#14FF3B30")
    val toggleOff = Color.parseColor("#F1F1F5")

    const val titleSp = 34f
    const val rowSp = 17f
    const val bodySp = 15f
    const val captionSp = 13f
    const val groupRadius = 24
    const val sectionGap = 32
    const val rowHeight = 52
    const val rowInset = 16
    const val controlHeight = 48
    const val toggleWidth = 64
    const val toggleHeight = 28
    const val thumbWidth = 38
    const val thumbHeight = 24
    const val motionMs = 300L
}
