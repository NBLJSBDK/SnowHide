package com.nbljsbdk.snowhide.domain

import com.nbljsbdk.snowhide.domain.appmanage.AppManageFreezePlanner
import org.junit.Assert.assertEquals
import org.junit.Test

class AppManageFreezePlannerTest {

    @Test
    fun onlyNewUnfrozenPackagesAreSelected() {
        assertEquals(
            listOf("com.example.new"),
            AppManageFreezePlanner.newlyAddedUnfrozenPackages(
                initialPackages = setOf("com.example.existing", "com.example.locked"),
                currentPackages = listOf(
                    "com.example.existing",
                    "com.example.locked",
                    "com.example.new",
                    "com.example.new",
                    "com.example.alreadyFrozen",
                ),
                frozenStates = mapOf("com.example.alreadyFrozen" to true),
            ),
        )
    }
}
