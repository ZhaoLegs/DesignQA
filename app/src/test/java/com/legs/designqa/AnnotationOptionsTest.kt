package com.legs.designqa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotationOptionsTest {
    @Test fun allEightTypesCanBeEnabledAndDisabledIndependently() {
        val types = listOf("size", "space", "font", "family", "color", "bounds", "radius", "stroke")
        var options = AnnotationOptions.from(null, emptySet())
        types.filterNot { options.isOn(it) }.forEach { options = options.toggle(it) }
        assertTrue(types.all { options.isOn(it) })
        assertEquals(options, options.toggle("unknown"))
        types.forEach { key ->
            options = options.toggle(key)
            assertFalse(options.isOn(key))
        }
        assertTrue(types.none { options.isOn(it) })
    }
    @Test fun defaultsToSizeWhenNoValidPrimaryTypeWasSaved() {
        assertTrue(AnnotationOptions.from(null, emptySet()).isOn("size"))
        assertTrue(AnnotationOptions.from("grid", emptySet()).isOn("size"))
    }

    @Test fun enablingAnotherTypeKeepsThePreviousType() {
        val options = AnnotationOptions.from("size", setOf("grid")).toggle("color")

        assertTrue(options.isOn("color"))
        assertTrue(options.isOn("size"))
        assertTrue(options.isOn("grid"))
    }

    @Test fun selectingTheActiveTypeDisablesIt() {
        val options = AnnotationOptions.from("font", emptySet()).toggle("font")

        assertFalse(options.isOn("font"))
    }

    @Test fun auxiliaryLayersToggleIndependently() {
        val enabled = AnnotationOptions.from("space", setOf("bounds")).toggle("grid")
        val disabled = enabled.toggle("bounds")

        assertTrue(disabled.isOn("grid"))
        assertFalse(disabled.isOn("bounds"))
        assertTrue(disabled.isOn("space"))
    }

    @Test fun radiusCanCoexistWithSize() {
        val options = AnnotationOptions.from("size", emptySet()).toggle("radius")

        assertTrue(options.isOn("radius"))
        assertTrue(options.isOn("size"))
    }

    @Test fun legacyTypefaceMapsToFamilyAndCoexistsWithFont() {
        val options = AnnotationOptions.from("font", emptySet()).toggle("typeface")

        assertTrue(options.isOn("typeface"))
        assertTrue(options.isOn("family"))
        assertTrue(options.isOn("font"))
    }

    @Test fun overlaysAreHiddenWhileTheKeyguardIsLocked() {
        assertFalse(shouldShowOverlays(keyguardLocked = true))
        assertTrue(shouldShowOverlays(keyguardLocked = false))
    }
}
