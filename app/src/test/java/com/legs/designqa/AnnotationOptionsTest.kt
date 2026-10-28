package com.legs.designqa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotationOptionsTest {
    @Test fun defaultsToSizeWhenNoValidPrimaryTypeWasSaved() {
        assertEquals("size", AnnotationOptions.from(null, emptySet()).primary)
        assertEquals("size", AnnotationOptions.from("grid", emptySet()).primary)
    }

    @Test fun selectingPrimaryTypeReplacesThePreviousType() {
        val options = AnnotationOptions.from("size", setOf("grid")).toggle("color")

        assertEquals("color", options.primary)
        assertTrue(options.isOn("color"))
        assertFalse(options.isOn("size"))
        assertTrue(options.isOn("grid"))
    }

    @Test fun selectingTheActivePrimaryTypeKeepsItEnabled() {
        val options = AnnotationOptions.from("font", emptySet()).toggle("font")

        assertEquals("font", options.primary)
        assertTrue(options.isOn("font"))
    }

    @Test fun auxiliaryLayersToggleIndependently() {
        val enabled = AnnotationOptions.from("space", setOf("bounds")).toggle("grid")
        val disabled = enabled.toggle("bounds")

        assertEquals("space", disabled.primary)
        assertTrue(disabled.isOn("grid"))
        assertFalse(disabled.isOn("bounds"))
        assertTrue(disabled.isOn("space"))
    }

    @Test fun radiusIsASelectablePrimaryType() {
        val options = AnnotationOptions.from("size", emptySet()).toggle("radius")

        assertEquals("radius", options.primary)
        assertTrue(options.isOn("radius"))
    }

    @Test fun typefaceIsASelectablePrimaryType() {
        val options = AnnotationOptions.from("font", emptySet()).toggle("typeface")

        assertEquals("typeface", options.primary)
        assertTrue(options.isOn("typeface"))
        assertFalse(options.isOn("font"))
    }

    @Test fun overlaysAreHiddenWhileTheKeyguardIsLocked() {
        assertFalse(shouldShowOverlays(keyguardLocked = true))
        assertTrue(shouldShowOverlays(keyguardLocked = false))
    }
}
