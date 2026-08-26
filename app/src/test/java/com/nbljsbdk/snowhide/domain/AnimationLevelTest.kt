package com.nbljsbdk.snowhide.domain

import com.nbljsbdk.snowhide.domain.settings.AnimationLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class AnimationLevelTest {

    @Test
    fun storageOrderMatchesSliderOrder() {
        assertEquals(AnimationLevel.OFF, AnimationLevel.fromStorageValue(0))
        assertEquals(AnimationLevel.HIGH, AnimationLevel.fromStorageValue(1))
        assertEquals(AnimationLevel.MEDIUM, AnimationLevel.fromStorageValue(2))
        assertEquals(AnimationLevel.LOW, AnimationLevel.fromStorageValue(3))
    }

    @Test
    fun invalidStorageFallsBackToDefault() {
        assertEquals(AnimationLevel.MEDIUM, AnimationLevel.fromStorageValue(-1))
        assertEquals(AnimationLevel.MEDIUM, AnimationLevel.fromStorageValue(4))
    }

    @Test
    fun legacySwitchMapsToCompatibleLevels() {
        assertEquals(AnimationLevel.MEDIUM, AnimationLevel.fromLegacy(true))
        assertEquals(AnimationLevel.OFF, AnimationLevel.fromLegacy(false))
    }
}
