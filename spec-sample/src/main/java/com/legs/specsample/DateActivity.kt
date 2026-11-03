package com.legs.specsample

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Spec Sample fixture page that shows today's calendar date for inspection. */
class DateActivity : Activity() {
    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.WHITE
        val today = SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINA).format(Date())
        val iso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(24f), dp(72f), dp(24f), dp(24f))
        }
        root.addView(TextView(this).apply {
            text = "Spec Sample"
            textSize = 15f
            setTextColor(Color.parseColor("#8A8A8E"))
            typeface = Typeface.create(Typeface.DEFAULT, 500, false)
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "今天"
            textSize = 17f
            setTextColor(Color.parseColor("#1C1C1E"))
            typeface = Typeface.create(Typeface.DEFAULT, 600, false)
            gravity = Gravity.CENTER
            setPadding(0, dp(28f), 0, dp(8f))
        })
        root.addView(TextView(this).apply {
            text = today
            textSize = 28f
            setTextColor(Color.parseColor("#1C1C1E"))
            typeface = Typeface.create(Typeface.DEFAULT, 700, false)
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = iso
            textSize = 16f
            setTextColor(Color.parseColor("#8A8A8E"))
            typeface = Typeface.create(Typeface.DEFAULT, 400, false)
            gravity = Gravity.CENTER
            setPadding(0, dp(12f), 0, 0)
        })
        setContentView(root)
    }
}
