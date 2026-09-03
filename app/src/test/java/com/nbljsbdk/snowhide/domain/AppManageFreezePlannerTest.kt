package com.nbljsbdk.snowhide.domain

import com.nbljsbdk.snowhide.core.model.AppTarget
import com.nbljsbdk.snowhide.data.model.AppRuntimeState
import com.nbljsbdk.snowhide.domain.appmanage.AppManageFreezePlanner
import org.junit.Assert.assertEquals
import org.junit.Test

class AppManageFreezePlannerTest {

    @Test
    fun packagePlannerOnlySelectsNewActivePackages() {
        assertEquals(
            listOf("com.example.active"),
            AppManageFreezePlanner.newlyAddedUnfrozenPackages(
                initialPackages = setOf("com.example.existing"),
                currentPackages = listOf(
                    "com.example.existing",
                    "com.example.active",
                    "com.example.active",
                    "com.example.frozen",
                    "com.example.missing",
                    "com.example.unknown",
                    "com.example.noState",
                ),
                runtimeStates = mapOf(
                    "com.example.active" to AppRuntimeState.ACTIVE,
                    "com.example.frozen" to AppRuntimeState.FROZEN,
                    "com.example.missing" to AppRuntimeState.MISSING,
                    "com.example.unknown" to AppRuntimeState.UNKNOWN,
                ),
            ),
        )
    }

    @Test
    fun targetPlannerOnlySelectsActiveTargetsByFullIdentity() {
        val primary = AppTarget.create("com.example.same", 0).getOrThrow()
        val clone = AppTarget.create("com.example.same", 999).getOrThrow()
        val frozen = AppTarget.create("com.example.frozen", 999).getOrThrow()
        val missing = AppTarget.create("com.example.missing", 999).getOrThrow()
        val unknown = AppTarget.create("com.example.unknown", 999).getOrThrow()
        val noState = AppTarget.create("com.example.noState", 999).getOrThrow()

        assertEquals(
            listOf(clone),
            AppManageFreezePlanner.newlyAddedUnfrozenTargets(
                initialTargets = setOf(primary),
                currentTargets = listOf(primary, clone, clone, frozen, missing, unknown, noState),
                runtimeStates = mapOf(
                    primary to AppRuntimeState.ACTIVE,
                    clone to AppRuntimeState.ACTIVE,
                    frozen to AppRuntimeState.FROZEN,
                    missing to AppRuntimeState.MISSING,
                    unknown to AppRuntimeState.UNKNOWN,
                ),
            ),
        )
    }
}
