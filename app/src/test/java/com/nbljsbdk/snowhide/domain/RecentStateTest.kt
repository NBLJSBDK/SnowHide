package com.nbljsbdk.snowhide.domain

import com.nbljsbdk.snowhide.domain.recent.RecentAccessibilitySnapshot
import com.nbljsbdk.snowhide.domain.recent.RecentFreezePolicy
import com.nbljsbdk.snowhide.domain.recent.RecentSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentStateTest {

    @Test
    fun firstTaskSnapshotOnlyEstablishesBaseline() {
        val state = RecentSessionState().begin(
            snapshot = RecentAccessibilitySnapshot(setOf("com.a", "com.b"), "recent", "window"),
            now = 1L,
            calibration = false,
        )
        val baseline = state.initializeOrDiffTaskSnapshot(setOf("com.a", "com.b"), 2L)
        assertTrue(baseline.baselineEstablished)
        assertTrue(baseline.removed.isEmpty())

        val diff = baseline.state.initializeOrDiffTaskSnapshot(setOf("com.b"), 3L)
        assertFalse(diff.baselineEstablished)
        assertEquals(setOf("com.a"), diff.removed)
    }

    @Test
    fun emptyRecentSnapshotNeedsConfirmation() {
        val state = RecentSessionState().begin(
            snapshot = RecentAccessibilitySnapshot(setOf("com.a"), "recent", "window"),
            now = 1L,
            calibration = false,
        )
        val first = state.acceptAccessibilitySnapshot(
            snapshot = RecentAccessibilitySnapshot(emptySet(), "recent", "window"),
            now = 2L,
            scrolled = false,
            emptyConfirmationCount = 2,
        )
        assertFalse(first.accepted)
        assertEquals(setOf("com.a"), first.state.recentPackages)

        val second = first.state.acceptAccessibilitySnapshot(
            snapshot = RecentAccessibilitySnapshot(emptySet(), "recent", "window"),
            now = 3L,
            scrolled = false,
            emptyConfirmationCount = 2,
        )
        assertTrue(second.accepted)
        assertTrue(second.state.recentPackages.isEmpty())
    }

    @Test
    fun recentPolicyExcludesOwnLockedAndUnaddedPackages() {
        assertEquals(
            listOf("com.ok"),
            RecentFreezePolicy.eligiblePackages(
                packages = listOf("com.self", "com.ok", "com.locked", "com.missing", "bad;pkg"),
                addedPackages = setOf("com.self", "com.ok", "com.locked"),
                lockedPackages = setOf("com.locked"),
                ownPackage = "com.self",
            ),
        )
    }
}
