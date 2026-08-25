package com.nbljsbdk.snowhide.data

import com.nbljsbdk.snowhide.data.model.GridItem
import com.nbljsbdk.snowhide.data.model.migrateLegacyLockedItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GridLockMigrationTest {

    @Test
    fun legacyLockedFieldsMergeIntoIndependentSetAndNormalizeItems() {
        val items = listOf(
            GridItem(1L, "app", "com.example.legacy", sortOrder = 0, locked = true),
            GridItem(2L, "app", "com.example.normal", sortOrder = 1),
        )

        val result = migrateLegacyLockedItems(items, setOf("com.example.persisted"))

        assertEquals(
            setOf("com.example.legacy", "com.example.persisted"),
            result.lockedPackages,
        )
        assertFalse(result.items.first().locked)
        assertFalse(result.items.last().locked)
    }

    @Test
    fun migrationIsIdempotent() {
        val original = listOf(
            GridItem(1L, "app", "com.example.app", sortOrder = 0, locked = true),
        )
        val first = migrateLegacyLockedItems(original, emptySet())
        val second = migrateLegacyLockedItems(first.items, first.lockedPackages)

        assertEquals(first, second)
        assertTrue(second.items.none { it.locked })
    }
}
