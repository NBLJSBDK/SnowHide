package com.nbljsbdk.snowhide.core

import com.nbljsbdk.snowhide.core.model.CloneBadgePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CloneBadgePaletteTest {
    @Test
    fun colorForIsStableAndCyclesAcrossPalette() {
        assertEquals(CloneBadgePalette.colorFor(999), CloneBadgePalette.colorFor(999))
        assertEquals(CloneBadgePalette.colorFor(0), CloneBadgePalette.colorFor(6))
        assertNotEquals(CloneBadgePalette.colorFor(0), CloneBadgePalette.colorFor(1))
    }
}
