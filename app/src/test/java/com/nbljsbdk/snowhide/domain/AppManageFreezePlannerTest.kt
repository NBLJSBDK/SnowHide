package com.nbljsbdk.snowhide.domain

import com.nbljsbdk.snowhide.core.model.AppTarget
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

    @Test
    fun targetPlannerKeepsSamePackageInDifferentUsersIndependent() {
        val primary = AppTarget.create("com.example.same", 0).getOrThrow()
        val clone = AppTarget.create("com.example.same", 999).getOrThrow()

        assertEquals(
            listOf(clone),
            AppManageFreezePlanner.newlyAddedUnfrozenTargets(
                initialTargets = setOf(primary),
                currentTargets = listOf(primary, clone, clone),
                frozenStates = mapOf(primary to false, clone to false),
            ),
        )
    }
}
