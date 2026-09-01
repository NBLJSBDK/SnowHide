package com.nbljsbdk.snowhide.domain

import com.nbljsbdk.snowhide.core.model.AppTarget
import com.nbljsbdk.snowhide.core.model.UserProfile
import com.nbljsbdk.snowhide.domain.appclone.AppCloneUseCase
import com.nbljsbdk.snowhide.test.FakeAppCloneSelectionStore
import com.nbljsbdk.snowhide.test.FakeEngineProvider
import com.nbljsbdk.snowhide.test.FakePowerEngine
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCloneUseCaseTest {

    @Test
    fun samePackageInDifferentUsersGetsIndependentTargetState() = runBlocking {
        val engine = FakePowerEngine().apply {
            usersResult = Result.success(
                listOf(
                    UserProfile(0, "Owner", running = true),
                    UserProfile(10, "Work profile", flags = 0x20, running = true),
                ),
            )
            installedPackagesByUser[10] = listOf("com.example.clone", "com.nbljsbdk.snowhide")
            frozenPackagesByUser[10] = listOf("com.example.clone")
            systemPackagesByUser[10] = listOf("com.example.clone")
        }
        val useCase = AppCloneUseCase(
            FakeEngineProvider(engine),
            FakeAppCloneSelectionStore(),
            selfPackageName = "com.nbljsbdk.snowhide",
        )

        val snapshot = useCase.refresh().getOrThrow()

        assertEquals(listOf(10), snapshot.users.map { it.id })
        assertEquals(10, snapshot.selectedUserId)
        assertEquals(listOf("com.example.clone"), snapshot.apps.map { it.packageName })
        assertTrue(snapshot.apps.single().frozen)
        assertTrue(snapshot.apps.single().isSystem)
        assertTrue(useCase.unfreezeApp(snapshot.apps.single().target).isSuccess)
        assertEquals(
            listOf(AppTarget.create("com.example.clone", 10).getOrThrow()),
            engine.targetedEnabledTargets,
        )
    }

    @Test
    fun missingTargetUserNeverFallsBackToUserZero() = runBlocking {
        val engine = FakePowerEngine().apply {
            usersResult = Result.success(listOf(UserProfile(0, "Owner")))
            installedPackagesByUser[0] = listOf("com.example.clone")
        }
        val useCase = AppCloneUseCase(
            FakeEngineProvider(engine),
            FakeAppCloneSelectionStore(),
            selfPackageName = "com.nbljsbdk.snowhide",
        )

        val result = useCase.freezeApp(AppTarget.create("com.example.clone", 10).getOrThrow())

        assertFalse(result.isSuccess)
        assertTrue(engine.targetedDisabledTargets.isEmpty())
    }

    @Test
    fun primaryUserIsNotAnAppCloneTarget() = runBlocking {
        val engine = FakePowerEngine().apply {
            usersResult = Result.success(listOf(UserProfile(0, "Owner")))
            installedPackagesByUser[0] = listOf("com.example.clone")
        }
        val useCase = AppCloneUseCase(
            FakeEngineProvider(engine),
            FakeAppCloneSelectionStore(),
            selfPackageName = "com.nbljsbdk.snowhide",
        )

        val result = useCase.freezeApp(AppTarget.create("com.example.clone", 0).getOrThrow())

        assertTrue(result.isFailure)
        assertTrue(engine.targetedDisabledTargets.isEmpty())
    }

    @Test
    fun rejectsMissingPackageAndSelfWithoutExecutingTargetCommand() = runBlocking {
        val engine = FakePowerEngine().apply {
            usersResult = Result.success(listOf(UserProfile(10, "Clone")))
            installedPackagesByUser[10] = listOf("com.nbljsbdk.snowhide")
        }
        val useCase = AppCloneUseCase(
            FakeEngineProvider(engine),
            FakeAppCloneSelectionStore(),
            selfPackageName = "com.nbljsbdk.snowhide",
        )

        val missing = useCase.freezeApp(AppTarget.create("com.example.missing", 10).getOrThrow())
        val self = useCase.freezeApp(AppTarget.create("com.nbljsbdk.snowhide", 10).getOrThrow())

        assertTrue(missing.isFailure)
        assertTrue(self.isFailure)
        assertTrue(engine.targetedDisabledTargets.isEmpty())
    }

    @Test
    fun consecutiveFreezeRequestsAreQueuedAndAllExecuted() = runBlocking {
        val first = AppTarget.create("com.example.first", 10).getOrThrow()
        val second = AppTarget.create("com.example.second", 10).getOrThrow()
        val engine = FakePowerEngine().apply {
            usersResult = Result.success(listOf(UserProfile(10, "Clone")))
            installedPackagesByUser[10] = listOf(first.packageName.value, second.packageName.value)
            targetedOperationDelayMs = 20
        }
        val useCase = AppCloneUseCase(
            FakeEngineProvider(engine),
            FakeAppCloneSelectionStore(),
            selfPackageName = "com.nbljsbdk.snowhide",
        )

        val results = listOf(
            async { useCase.freezeApp(first) },
            async { useCase.freezeApp(second) },
        ).awaitAll()

        assertTrue(results.all { it.isSuccess })
        assertEquals(setOf(first, second), engine.targetedDisabledTargets.toSet())
        assertEquals(1, engine.maxConcurrentTargetedOperations)
    }
}
