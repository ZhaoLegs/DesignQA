package com.legs.designqa

import android.content.SharedPreferences
import android.content.Context
import android.animation.ValueAnimator
import kotlin.random.Random

class MemoryPrefs : SharedPreferences {
    val values = mutableMapOf<String, Any?>()
    override fun getString(key: String, default: String?) = values[key] as? String ?: default
    override fun getBoolean(key: String, default: Boolean) = values[key] as? Boolean ?: default
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        val pending = mutableMapOf<String, Any?>()
        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun apply() { values.putAll(pending) }
    }
}

fun main() {
    val raw = MemoryPrefs()
    val prefs = AnnotationPreferences(raw)
    val primaries = listOf("size", "space", "font", "family", "color", "radius")
    val aux = listOf("bounds", "stroke")
    val expected = mutableSetOf("size")
    val rng = Random(20261102)
    repeat(100_000) {
        val key = (primaries + aux).random(rng)
        if (!expected.add(key)) expected.remove(key)
        prefs.select(key)
        // Recreate the preferences adapter each time to exercise persisted reads.
        val actual = AnnotationPreferences(raw).read()
        check((primaries + aux).all { actual.isOn(it) == (it in expected) })
    }
    val legacy = MemoryPrefs()
    legacy.values.putAll(mapOf("annotType" to "typeface", "color" to true, "stroke" to true))
    val migrated = AnnotationPreferences(legacy)
    check(migrated.read().isOn("family") && migrated.read().isOn("stroke"))
    check(!migrated.read().isOn("color")) // Ignore stale old primary booleans.
    migrated.select("size")
    legacy.values["annotType"] = "color"
    check(migrated.read().isOn("size") && migrated.read().isOn("family"))
    check(!migrated.read().isOn("color")) // Legacy state must not overwrite the new saved set.
    println("PASS: 100000 seeded toggle/restart operations; independent selection and legacy migration")

    // Use the production IosSwitch + preference methods with a deterministic animator.
    val switchPrefs = AnnotationPreferences(MemoryPrefs())
    val sw = IosSwitch(Context())
    sw.setChecked(true)
    sw.onChange = { switchPrefs.select("size"); sw.setChecked(switchPrefs.read().isOn("size")) }
    sw.performClick()
    ValueAnimator.finishAll()
    val field = IosSwitch::class.java.getDeclaredField("pos").apply { isAccessible = true }
    val knob = field.getFloat(sw)
    check(!sw.checked && knob == 0f) { "An enabled type must toggle off" }
    check(!switchPrefs.read().isOn("size")) { "All-off must persist instead of falling back to size" }
    println("PASS: active switch can turn off, including the last enabled type")

    val controls = listOf("size", "font").associateWith { IosSwitch(Context()) }
    fun refresh() = controls.forEach { (key, control) -> control.setChecked(switchPrefs.read().isOn(key)) }
    refresh()
    controls.forEach { (key, control) -> control.onChange = { switchPrefs.select(key); refresh() } }
    controls.getValue("font").performClick()
    controls.getValue("size").performClick()
    ValueAnimator.finishAll()
    val visualOn = controls.filterValues { field.getFloat(it) == 1f }.keys
    check(visualOn == setOf("size", "font"))
    println("PASS: rapid changes leave both independently enabled switches visually on")
}
