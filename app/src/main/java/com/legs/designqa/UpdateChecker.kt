package com.legs.designqa

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Quietly checks GitHub Releases for a newer Spec build.
 * Expects a release tag like `v1.1` / `1.1` and preferably an `.apk` asset.
 */
internal object UpdateChecker {
    private const val API = "https://api.github.com/repos/ZhaoLegs/Spec/releases/latest"
    private const val PREFS = "update_check"
    private const val KEY_LAST = "last_check_ms"
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun maybeCheck(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST, 0L) < DAY_MS) return
        prefs.edit().putLong(KEY_LAST, now).apply()
        val current = runCatching {
            activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
        }.getOrNull().orEmpty()
        io.execute {
            val info = runCatching { fetchLatest() }.getOrNull() ?: return@execute
            if (!isNewer(info.tag, current)) return@execute
            main.post {
                if (activity.isFinishing) return@post
                showDialog(activity, info)
            }
        }
    }

    data class ReleaseInfo(val tag: String, val name: String, val body: String, val apkUrl: String?, val htmlUrl: String)

    private fun fetchLatest(): ReleaseInfo? {
        val conn = (URL(API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Spec-Android-UpdateCheck")
        }
        return try {
            if (conn.responseCode !in 200..299) return null
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            if (json.optBoolean("draft", false) || json.optBoolean("prerelease", false)) return null
            val tag = json.optString("tag_name").ifBlank { return null }
            val assets = json.optJSONArray("assets")
            var apk: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    val n = a.optString("name")
                    if (n.endsWith(".apk", ignoreCase = true)) {
                        apk = a.optString("browser_download_url").ifBlank { null }
                        break
                    }
                }
            }
            ReleaseInfo(
                tag = tag,
                name = json.optString("name").ifBlank { tag },
                body = json.optString("body").trim(),
                apkUrl = apk,
                htmlUrl = json.optString("html_url").ifBlank { "https://github.com/ZhaoLegs/Spec/releases" }
            )
        } finally {
            conn.disconnect()
        }
    }

    /** Compare semver-ish tags: strip leading V/v and spaces. */
    fun isNewer(remoteTag: String, localVersion: String): Boolean {
        val r = normalize(remoteTag)
        val l = normalize(localVersion)
        if (r.isEmpty() || l.isEmpty() || r == l) return false
        val rp = r.split('.', '-', '_').map { it.toIntOrNull() ?: 0 }
        val lp = l.split('.', '-', '_').map { it.toIntOrNull() ?: 0 }
        val n = maxOf(rp.size, lp.size)
        for (i in 0 until n) {
            val a = rp.getOrElse(i) { 0 }
            val b = lp.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun normalize(s: String) =
        s.trim().trimStart('V', 'v').replace(" ", "")

    private fun showDialog(activity: Activity, info: ReleaseInfo) {
        val d = (activity.resources.displayMetrics.density)
        fun dp(v: Int) = (v * d).toInt()
        fun rounded(c: Int, r: Int) = GradientDrawable().apply {
            setColor(c); cornerRadius = r * d
        }
        fun label(t: String, size: Float, color: Int, weight: Int = 400) = TextView(activity).apply {
            text = t; textSize = size; setTextColor(color)
            typeface = Typeface.create(Typeface.DEFAULT, weight, false)
            includeFontPadding = false
        }

        val dialog = Dialog(activity)
        dialog.setCancelable(true)
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(HomeStyle.surface, 20)
            setPadding(dp(20), dp(20), dp(20), dp(16))
        }
        card.addView(label("发现新版本", 20f, HomeStyle.text, 700))
        card.addView(label("V ${normalize(info.tag)}", 15f, HomeStyle.accent, 600).apply {
            setPadding(0, dp(8), 0, 0)
        })
        val notes = info.body.ifBlank { info.name }.ifBlank { "前往 GitHub 查看更新说明。" }
        val scroll = ScrollView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(160)).apply { topMargin = dp(12) }
        }
        scroll.addView(label(notes, 14f, HomeStyle.secondary, 400).apply {
            setLineSpacing(0f, 1.25f)
        })
        card.addView(scroll)

        val actions = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) }
        }
        val later = label("稍后", 16f, HomeStyle.secondary, 500).apply {
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setOnClickListener { dialog.dismiss() }
        }
        val update = label("更新", 16f, Color.WHITE, 600).apply {
            gravity = Gravity.CENTER
            background = rounded(HomeStyle.accent, 14)
            setPadding(dp(22), dp(12), dp(22), dp(12))
            setOnClickListener {
                val url = info.apkUrl ?: info.htmlUrl
                runCatching {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
                dialog.dismiss()
            }
        }
        actions.addView(later)
        actions.addView(update, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        card.addView(actions)

        dialog.setContentView(card, ViewGroup.LayoutParams(dp(300), -2))
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }
}
