package com.nbljsbdk.snowhide.domain

import com.nbljsbdk.snowhide.core.engine.EngineProvider
import com.nbljsbdk.snowhide.core.mode.FreezeExecutor
import com.nbljsbdk.snowhide.test.FakeEngineProvider
import com.nbljsbdk.snowhide.test.FakeFreezeTargetStore
import com.nbljsbdk.snowhide.test.FakePowerEngine
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
}
