package com.nbljsbdk.snowhide.domain

import com.nbljsbdk.snowhide.core.engine.EngineProvider
import com.nbljsbdk.snowhide.core.mode.FreezeExecutor
import com.nbljsbdk.snowhide.core.model.AppTarget
import com.nbljsbdk.snowhide.core.model.UserProfile
import com.nbljsbdk.snowhide.test.FakeEngineProvider
import com.nbljsbdk.snowhide.test.FakeFreezeTargetStore
import com.nbljsbdk.snowhide.test.FakePowerEngine
import com.nbljsbdk.snowhide.test.FakeTargetFreezeStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreezeUseCaseTest {

    @Test
    fun freezeAndUnfreezeUseInjectedEngine() = runBlocking {
        val engine = FakePowerEngine()
        val provider: EngineProvider = FakeEngineProvider(engine)
        val useCase = FreezeUseCase(
            FreezeExecutor(provider),
            FakeFreezeTargetStore(setOf("com.example.app")),
            provider,
        )

        assertTrue(useCase.freezeApp("com.example.app").isSuccess)
        assertTrue(useCase.unfreezeApp("com.example.app").isSuccess)
        assertEquals(listOf("com.example.app"), engine.disabledPackages)
        assertEquals(listOf("com.example.app"), engine.enabledPackages)
    }

    @Test
    fun recentBatchFiltersUnknownLockedAndDuplicateTargets() = runBlocking {
        val engine = FakePowerEngine()
        val provider = FakeEngineProvider(engine)
        val useCase = FreezeUseCase(
            FreezeExecutor(provider),
            FakeFreezeTargetStore(
                added = setOf("com.example.one", "com.example.two"),
                locked = setOf("com.example.two"),
            ),
            provider,
        )

        val result = useCase.freezePackages(
            listOf(
                "com.example.one",
                "com.example.one",
                "com.example.two",
                "com.example.unknown",
            )
        )

        assertEquals(1, result.getOrThrow())
        assertEquals(listOf("pm disable-user --user 0 com.example.one"), engine.executedCommands)
    }

    @Test
    fun quickCleanStopsWhenFrozenStateCannotBeRead() = runBlocking {
        val engine = FakePowerEngine().apply {
            frozenPackagesResult = Result.failure(IllegalStateException("state unavailable"))
        }
        val provider = FakeEngineProvider(engine)
        val useCase = FreezeUseCase(
            FreezeExecutor(provider),
            FakeFreezeTargetStore(setOf("com.example.app")),
            provider,
        )

        assertTrue(useCase.quickCleanPackages().isFailure)
        assertFalse(engine.executedCommands.isNotEmpty())
    }

    @Test
    fun frozenQueryPreservesFailureResult() = runBlocking {
        val engine = FakePowerEngine().apply {
            frozenResult = Result.failure(IllegalStateException("unknown"))
        }
        val provider = FakeEngineProvider(engine)
        val useCase = FreezeUseCase(
            FreezeExecutor(provider),
            FakeFreezeTargetStore(setOf("com.example.app")),
            provider,
        )

        assertTrue(useCase.isFrozenResult("com.example.app").isFailure)
    }

    @Test
    fun quickCleanFailsImmediatelyWhenNoEngineIsAvailable() = runBlocking {
        val provider = FakeEngineProvider(null)
        val useCase = FreezeUseCase(
            FreezeExecutor(provider),
            FakeFreezeTargetStore(setOf("com.example.app")),
            provider,
        )

        assertTrue(useCase.quickClean().isFailure)
    }

    @Test
    fun targetBatchUsesExplicitUserCommandAndDeduplicatesTargets() = runBlocking {
        val target = AppTarget.create("com.example.clone", 999).getOrThrow()
        val engine = FakePowerEngine().apply {
            usersResult = Result.success(listOf(UserProfile(999, "MultiApp")))
            installedPackagesByUser[999] = listOf(target.packageName.value)
        }
        val provider = FakeEngineProvider(engine)
        val useCase = FreezeUseCase(
            FreezeExecutor(provider),
            FakeTargetFreezeStore(setOf(target)),
            provider,
        )

        assertEquals(1, useCase.freezeTargets(listOf(target, target)).getOrThrow())
        assertEquals(
            listOf("pm disable-user --user 999 com.example.clone"),
            engine.executedCommands,
        )
    }

    @Test
    fun samePackageInPrimaryAndCloneUsesTwoIndependentCommands() = runBlocking {
        val primary = AppTarget.create("com.example.same", AppTarget.PRIMARY_USER_ID).getOrThrow()
        val clone = AppTarget.create("com.example.same", 999).getOrThrow()
        val engine = FakePowerEngine().apply {
            usersResult = Result.success(listOf(UserProfile(0, "Owner"), UserProfile(999, "MultiApp")))
            installedPackagesByUser[999] = listOf(clone.packageName.value)
        }
        val provider = FakeEngineProvider(engine)
        val useCase = FreezeUseCase(
            FreezeExecutor(provider),
            FakeTargetFreezeStore(setOf(primary, clone)),
            provider,
        )

        assertEquals(2, useCase.freezeTargets(listOf(primary, clone)).getOrThrow())
        assertEquals(
            listOf(
                "pm disable-user --user 0 com.example.same",
                "pm disable-user --user 999 com.example.same",
            ),
            engine.executedCommands,
        )
    }

    @Test
    fun targetBatchRejectsStaleUserBeforeExecutingAnyCommand() = runBlocking {
        val target = AppTarget.create("com.example.clone", 999).getOrThrow()
        val engine = FakePowerEngine().apply {
            usersResult = Result.success(listOf(UserProfile(0, "Owner")))
        }
        val provider = FakeEngineProvider(engine)
        val useCase = FreezeUseCase(
            FreezeExecutor(provider),
            FakeTargetFreezeStore(setOf(target)),
            provider,
        )

        assertTrue(useCase.freezeTargets(listOf(target)).isFailure)
        assertTrue(engine.executedCommands.isEmpty())
    }

    @Test
    fun targetBatchRejectsInstalledQueryFailureBeforeExecutingAnyCommand() = runBlocking {
        val target = AppTarget.create("com.example.clone", 999).getOrThrow()
        val engine = FakePowerEngine().apply {
            usersResult = Result.success(listOf(UserProfile(999, "MultiApp")))
            installedPackagesResultByUser[999] =
                Result.failure(IllegalStateException("installed query failed"))
        }
        val provider = FakeEngineProvider(engine)
        val useCase = FreezeUseCase(
            FreezeExecutor(provider),
            FakeTargetFreezeStore(setOf(target)),
            provider,
        )

        assertTrue(useCase.freezeTargets(listOf(target)).isFailure)
        assertTrue(engine.executedCommands.isEmpty())
        assertTrue(engine.targetedDisabledTargets.isEmpty())
    }

    @Test
    fun targetUnfreezePropagatesEnableFailure() = runBlocking {
        val target = AppTarget.create("com.example.clone", 999).getOrThrow()
        val engine = FakePowerEngine().apply {
            usersResult = Result.success(listOf(UserProfile(999, "MultiApp")))
            installedPackagesByUser[999] = listOf(target.packageName.value)
            targetedEnableResult = Result.failure(IllegalStateException("permission denied"))
        }
        val provider = FakeEngineProvider(engine)
        val useCase = FreezeUseCase(
            FreezeExecutor(provider),
            FakeTargetFreezeStore(setOf(target)),
            provider,
        )

        val result = useCase.unfreezeApp(target)

        assertTrue(result.isFailure)
        assertEquals(listOf(target), engine.targetedEnabledTargets)
    }

    @Test
    fun targetUnfreezeRejectsMissingPackageWithoutEnabling() = runBlocking {
        val target = AppTarget.create("com.example.missing", 999).getOrThrow()
        val engine = FakePowerEngine().apply {
            usersResult = Result.success(listOf(UserProfile(999, "MultiApp")))
            installedPackagesByUser[999] = emptyList()
        }
        val provider = FakeEngineProvider(engine)
        val useCase = FreezeUseCase(
            FreezeExecutor(provider),
            FakeTargetFreezeStore(setOf(target)),
            provider,
        )

        val result = useCase.unfreezeApp(target)

        assertTrue(result.isFailure)
        assertTrue(engine.targetedEnabledTargets.isEmpty())
    }

    @Test
    fun targetEnableAndDisableRequestsAreSerialized() = runBlocking {
        val first = AppTarget.create("com.example.first", 999).getOrThrow()
        val second = AppTarget.create("com.example.second", 999).getOrThrow()
        val engine = FakePowerEngine().apply {
            usersResult = Result.success(listOf(UserProfile(999, "MultiApp")))
            installedPackagesByUser[999] = listOf(
                first.packageName.value,
                second.packageName.value,
            )
            targetedOperationDelayMs = 20
        }
        val provider = FakeEngineProvider(engine)
        val useCase = FreezeUseCase(
            FreezeExecutor(provider),
            FakeTargetFreezeStore(setOf(first, second)),
            provider,
        )

        val results = listOf(
            async { useCase.freezeApp(first) },
            async { useCase.unfreezeApp(second) },
        ).awaitAll()

        assertTrue(results.all { it.isSuccess })
        assertEquals(listOf(first), engine.targetedDisabledTargets)
        assertEquals(listOf(second), engine.targetedEnabledTargets)
        assertEquals(1, engine.maxConcurrentTargetedOperations)
    }

    @Test
    fun targetBatchNeverTargetsSnowHideItself() = runBlocking {
        val self = AppTarget.create("com.nbljsbdk.snowhide", AppTarget.PRIMARY_USER_ID).getOrThrow()
        val engine = FakePowerEngine()
        val provider = FakeEngineProvider(engine)
        val useCase = FreezeUseCase(
            FreezeExecutor(provider),
            FakeTargetFreezeStore(setOf(self)),
            provider,
            selfPackageName = self.packageName.value,
        )

        assertEquals(0, useCase.freezeTargets(listOf(self)).getOrThrow())
        assertTrue(engine.executedCommands.isEmpty())
    }

    @Test
    fun unfreezeEverythingIncludesFrozenPackagesFromEveryKnownUser() = runBlocking {
        val engine = FakePowerEngine().apply {
            frozenPackagesResult = Result.success(listOf("com.example.owner"))
            usersResult = Result.success(
                listOf(UserProfile(0, "Owner"), UserProfile(999, "MultiApp")),
            )
            frozenPackagesByUser[999] = listOf("com.example.clone")
            installedPackagesByUser[999] = listOf("com.example.clone")
        }
        val provider = FakeEngineProvider(engine)
        val useCase = FreezeUseCase(
            FreezeExecutor(provider),
            FakeTargetFreezeStore(emptySet()),
            provider,
        )

        assertEquals(2, useCase.unfreezeEverything().getOrThrow())
        assertEquals(
            listOf(
                "pm enable --user 0 com.example.owner",
                "pm enable --user 999 com.example.clone",
            ),
            engine.executedCommands,
        )
    }
}
