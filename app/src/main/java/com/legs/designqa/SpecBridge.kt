package com.legs.designqa

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import kotlin.math.abs

/** Exact properties reported by the in-app Spec Inspector debug library (if the inspected app bundles it). */
data class Exact(
    val sizePx: Float?, val weight: Int?, val weightName: String?, val family: String?, val fontRes: String?,
    val letterEm: Float?, val letterPx: Float?, val lineHeightPx: Float?, val color: Int?,
    val bg: Int?, val radiusPx: Float?, val strokeW: Float?, val stroke: Int?, val cls: String, val compose: Boolean,
    val fonts: List<FontResolver.Run> = emptyList()
) { val isText get() = sizePx != null }

class SpecBridge(private val ctx: Context) {
    private data class V(val l: Int, val t: Int, val r: Int, val b: Int, val depth: Int, val e: Exact)
    private var cachePkg: String? = null
    private var cacheAt = 0L
    private var cache: List<V> = emptyList()
    private val hasProvider = HashMap<String, Boolean>()

    fun available(pkg: String): Boolean = hasProvider.getOrPut(pkg) {
        runCatching { ctx.packageManager.resolveContentProvider("$pkg.specinspector", 0) != null }.getOrDefault(false)
    }

    private fun load(pkg: String): List<V> {
        val now = System.currentTimeMillis()
        if (pkg == cachePkg && now - cacheAt < 800) return cache
        val list = ArrayList<V>()
        runCatching {
            ctx.contentResolver.query(Uri.parse("content://$pkg.specinspector/dump"), null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val arr = JSONObject(c.getString(0)).getJSONArray("views")
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val t = o.optJSONObject("text")
                        fun f(j: JSONObject?, k: String) = if (j != null && j.has(k)) j.getDouble(k).toFloat() else null
                        fun n(j: JSONObject?, k: String) = if (j != null && j.has(k)) j.getInt(k) else null
                        list += V(o.getInt("l"), o.getInt("t"), o.getInt("r"), o.getInt("b"), o.optInt("depth"), Exact(
                            f(t, "sizePx"), n(t, "weight"), t?.optString("weightName")?.takeIf { it.isNotEmpty() },
                            t?.optString("family")?.takeIf { it.isNotEmpty() }, t?.optString("fontRes")?.takeIf { it.isNotEmpty() },
                            f(t, "letterEm"), f(t, "letterPx"), f(t, "lineHeightPx"), n(t, "color"),
                            n(o, "bg"), f(o, "radius"), f(o, "strokeW"), n(o, "stroke"), o.getString("cls"), t?.optBoolean("compose") == true,
                            t?.optJSONArray("fonts")?.let { fa -> (0 until fa.length()).map { k -> fa.getJSONObject(k).let { j ->
                                FontResolver.Run(j.getString("script"), j.getString("family"), j.optString("sub"), j.optInt("weight", 400)) } } } ?: emptyList()))
                    }
                }
            }
        }
        cachePkg = pkg; cacheAt = now; cache = list
        return list
    }

    /** Best match by bounds (±3px); text views and deeper views win ties. */
    fun match(pkg: String?, l: Int, t: Int, r: Int, b: Int): Exact? {
        if (pkg == null || !available(pkg)) return null
        val tol = 3
        return load(pkg).filter { abs(it.l - l) <= tol && abs(it.t - t) <= tol && abs(it.r - r) <= tol && abs(it.b - b) <= tol }
            .maxWithOrNull(compareBy<V> { it.e.isText }.thenBy { it.depth })?.e
    }
}
