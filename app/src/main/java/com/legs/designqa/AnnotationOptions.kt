package com.legs.designqa

/** Every measurement and supporting layer is independently selectable. */
data class AnnotationOptions private constructor(val enabled: Set<String>) {
    fun isOn(key: String) = canonical(key) in enabled
    fun toggle(key: String): AnnotationOptions {
        val type = canonical(key)
        if (type !in allTypes) return this
        return copy(enabled = if (type in enabled) enabled - type else enabled + type)
    }

    companion object {
        val allTypes = setOf("size", "space", "font", "family", "color", "bounds", "radius", "stroke", "grid")
        private val legacyPrimaryTypes = setOf("size", "space", "font", "family", "color", "radius")
        private val legacyAuxiliaryTypes = setOf("bounds", "stroke", "grid")
        private fun canonical(key: String) = if (key == "typeface") "family" else key
        fun from(enabled: Set<String>) = AnnotationOptions(enabled.map(::canonical).toSet() intersect allTypes)
        /** Preserve the old selected type and independent layers during migration. */
        fun from(primary: String?, auxiliary: Set<String>): AnnotationOptions =
            from(setOf(primary?.let(::canonical)?.takeIf { it in legacyPrimaryTypes } ?: "size") +
                (auxiliary intersect legacyAuxiliaryTypes))
    }
}

fun shouldShowOverlays(keyguardLocked: Boolean) = !keyguardLocked
