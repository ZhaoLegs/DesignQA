package com.legs.designqa

import org.junit.Assert.*
import org.junit.Test

class RowShapeTest {
    private val label = RowShape("TextView", "title", 48f, 16f, 80f, 24f, true, 16f, true, false, emptyList())
    private val row = RowShape("LinearLayout", "row", 0f, 0f, 360f, 56f, false, null, false, false, listOf(label))

    @Test fun differentLabelLengthsShareOneRepresentative() {
        val longer = row.copy(children = listOf(label.copy(width = 180f)))
        assertEquals(row.signature(), longer.signature())
        assertEquals(listOf(0 to 3), representativeRows(listOf(row.signature(), longer.signature(), row.signature())))
    }

    @Test fun textWrappingContainersMayGrowWithTheirLabels() {
        val wrapper = row.copy(kind = "TitleContainer", width = 100f, children = listOf(label))
        val first = row.copy(children = listOf(wrapper))
        val second = row.copy(children = listOf(wrapper.copy(width = 200f, children = listOf(label.copy(width = 180f)))))
        assertEquals(first.signature(), second.signature())
        assertNotEquals(first.signature(), second.copy(width = 300f).signature())
    }

    @Test fun layoutAndFontExceptionsRemainVisible() {
        val variants = listOf(row,
            row.copy(height = 80f),
            row.copy(children = listOf(label.copy(fontSp = 20f))),
            row.copy(children = listOf(label.copy(height = 48f))),
            row.copy(children = listOf(label.copy(left = 64f))),
            row.copy(children = listOf(label, label.copy(kind = "Switch", isText = false, checkable = true))))
        assertEquals(variants.size, representativeRows(variants.map { it.signature() }).size)
    }

    @Test fun ungroupedTitlesAndDifferentSectionsArePreserved() {
        assertEquals(listOf(0 to 1, 1 to 2, 3 to 1), representativeRows(listOf(null, "row", "row", null)))
        assertEquals(listOf(0 to 2), representativeRows(listOf("row", "row")))
        assertEquals(listOf(0 to 2), representativeRows(listOf("row", "row")))
        assertTrue(representativeRows(emptyList()).isEmpty())
    }

    @Test fun everyDisplayedFontSizeKeepsOneRepresentative() {
        val sizes = listOf("12sp", "14sp", "12sp", null, "16sp", "14sp")

        assertEquals(
            listOf("12sp", "14sp", "16sp"),
            fontAnnotationRepresentatives(sizes) { it }
        )
    }

    @Test fun equalVerticalGapsAtDifferentYCoordinatesRemainVisible() {
        val gaps = listOf(
            GapGeometry(vertical = true, pos = 120, a = 100, b = 116),
            GapGeometry(vertical = true, pos = 120, a = 156, b = 172),
            GapGeometry(vertical = true, pos = 120, a = 100, b = 116)
        )

        assertEquals(gaps.take(2), representativeGapGeometries(gaps))
    }
}
