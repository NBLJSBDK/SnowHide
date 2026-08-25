package com.nbljsbdk.snowhide.domain

import com.nbljsbdk.snowhide.test.FakeEngineProvider
import com.nbljsbdk.snowhide.test.FakeFreezeTargetStore
import com.nbljsbdk.snowhide.test.FakePowerEngine
import com.nbljsbdk.snowhide.test.FakeQuickToggleStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickToggleUseCaseTest {

    @Test
    fun lightUpOnlyOpensAddedFrozenMembers() = runBlocking {
        val engine = FakePowerEngine().apply {
            frozenPackagesResult = Result.success(listOf("com.example.frozen", "com.example.other"))
        }
        val store = FakeQuickToggleStore(
            members = listOf("com.example.frozen", "com.example.notAdded"),
        )
        val useCase = QuickToggleUseCase(
            FakeFreezeTargetStore(setOf("com.example.frozen")),
            FakeEngineProvider(engine),
            store,
        )

        assertEquals(1, useCase.lightUp().getOrThrow())
        assertEquals(listOf("com.example.frozen"), store.opened.value)
        assertEquals(listOf("pm enable com.example.frozen"), engine.executedCommands)
    }

    @Test
    fun turnOffFailureIsReturnedAndOpenedSnapshotIsRetained() = runBlocking {
        val engine = FakePowerEngine().apply { failExec = true }
        val store = FakeQuickToggleStore(opened = listOf("com.example.app"))
        val useCase = QuickToggleUseCase(
            FakeFreezeTargetStore(setOf("com.example.app")),
            FakeEngineProvider(engine),
            store,
        )

        val result = useCase.turnOff()

        assertTrue(result.isFailure)
        assertEquals(listOf("com.example.app"), store.opened.value)
    }

    @Test
    fun turnOffSkipsLockedAndClearsSuccessfulSnapshot() = runBlocking {
        val engine = FakePowerEngine()
        val store = FakeQuickToggleStore(
            opened = listOf("com.example.app", "com.example.locked"),
        )
        val useCase = QuickToggleUseCase(
            FakeFreezeTargetStore(
                added = setOf("com.example.app", "com.example.locked"),
                locked = setOf("com.example.locked"),
            ),
            FakeEngineProvider(engine),
            store,
        )

        val result = useCase.turnOff().getOrThrow()

        assertEquals(1, result.frozen)
        assertEquals(listOf("com.example.locked"), result.lockedSkipped)
        assertTrue(store.opened.value.isEmpty())
        assertEquals(listOf("pm disable-user --user 0 com.example.app"), engine.executedCommands)
    }
}
