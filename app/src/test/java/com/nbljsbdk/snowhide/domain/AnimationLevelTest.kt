package com.nbljsbdk.snowhide.domain

import com.nbljsbdk.snowhide.domain.settings.AnimationLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class AnimationLevelTest {

    @Test
    fun legacyStorageValuesUseFastAnimation() {
        listOf(0, 1, 2, 3).forEach { value ->
            assertEquals(AnimationLevel.FAST, AnimationLevel.fromStorageValue(value))
        }
    }

    @Test
    fun invalidStorageFallsBackToDefault() {
        assertEquals(AnimationLevel.FAST, AnimationLevel.fromStorageValue(-1))
        assertEquals(AnimationLevel.FAST, AnimationLevel.fromStorageValue(4))
    }

    @Test
    fun legacySwitchMapsToCompatibleLevels() {
        assertEquals(AnimationLevel.FAST, AnimationLevel.fromLegacy(true))
        assertEquals(AnimationLevel.FAST, AnimationLevel.fromLegacy(false))
    }
}
