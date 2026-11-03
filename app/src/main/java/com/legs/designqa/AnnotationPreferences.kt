package com.legs.designqa

import android.content.SharedPreferences

internal class AnnotationPreferences(private val prefs: SharedPreferences) {
    fun read(): AnnotationOptions {
        if (prefs.getBoolean("annotationMultiSelect", false)) {
            return AnnotationOptions.from(AnnotationOptions.allTypes.filter { prefs.getBoolean(it, false) }.toSet())
        }
        val saved = prefs.getString("annotType", null)
        return AnnotationOptions.from(saved,
            setOf("bounds", "stroke", "grid").filter { prefs.getBoolean(it, false) }.toSet())
    }

    fun select(key: String) = write(read().toggle(key))

    private fun write(options: AnnotationOptions) {
        prefs.edit().apply {
            putBoolean("annotationMultiSelect", true)
            AnnotationOptions.allTypes.forEach { putBoolean(it, options.isOn(it)) }
        }.apply()
    }
}
