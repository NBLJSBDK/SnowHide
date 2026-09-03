package com.nbljsbdk.snowhide.domain

import com.nbljsbdk.snowhide.domain.appmanage.AppManageFilterPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppManageFilterPolicyTest {

    @Test
    fun lockedSystemFilterOnlyShowsNonSystemApps() {
        assertTrue(
            AppManageFilterPolicy.allowsSystemApp(
                isSystem = false,
                systemUnlocked = false,
                showSystemOnly = false,
            ),
        )
        assertFalse(
            AppManageFilterPolicy.allowsSystemApp(
                isSystem = true,
                systemUnlocked = false,
                showSystemOnly = false,
            ),
        )
    }

    @Test
    fun unlockedDefaultFilterStillShowsOnlyNonSystemApps() {
        assertTrue(
            AppManageFilterPolicy.allowsSystemApp(
                isSystem = false,
                systemUnlocked = true,
                showSystemOnly = false,
            ),
        )
        assertFalse(
            AppManageFilterPolicy.allowsSystemApp(
                isSystem = true,
                systemUnlocked = true,
                showSystemOnly = false,
            ),
        )
    }

    @Test
    fun unlockedSystemOnlyFilterExcludesNonSystemApps() {
        assertTrue(
            AppManageFilterPolicy.allowsSystemApp(
                isSystem = true,
                systemUnlocked = true,
                showSystemOnly = true,
            ),
        )
        assertFalse(
            AppManageFilterPolicy.allowsSystemApp(
                isSystem = false,
                systemUnlocked = true,
                showSystemOnly = true,
            ),
        )
    }
}
