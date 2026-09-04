package com.nbljsbdk.snowhide.domain

import com.nbljsbdk.snowhide.core.model.AppTarget
import com.nbljsbdk.snowhide.core.model.FreezeTargetStore
import com.nbljsbdk.snowhide.domain.shortcut.DesktopShortcutTargetUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopShortcutTargetUseCaseTest {

    @Test
    fun frozenTargetIsUnfrozenBeforeOpening() {
        val target = target(999)
        var unfreezeCount = 0
        val useCase = useCase(
            targets = listOf(target),
            isFrozen = true,
            unfreeze = {
                unfreezeCount++
                Result.success(Unit)
            },
        )

        val result = runBlocking { useCase.prepareToOpen(target) }

        assertTrue(result.isSuccess)
        assertEquals(1, unfreezeCount)
    }

    @Test
    fun activeTargetDoesNotUnfreeze() {
        val target = target(0)
        var unfreezeCalled = false
        val useCase = useCase(
            targets = listOf(target),
            isFrozen = false,
            unfreeze = {
                unfreezeCalled = true
                Result.success(Unit)
            },
        )

        val result = runBlocking { useCase.prepareToOpen(target) }

        assertTrue(result.isSuccess)
        assertFalse(unfreezeCalled)
    }

    @Test
    fun targetRemovedFromGridIsRejectedWithoutUnfreeze() {
        val target = target(999)
        var unfreezeCalled = false
        val useCase = useCase(
            targets = emptyList(),
            isFrozen = true,
            unfreeze = {
                unfreezeCalled = true
                Result.success(Unit)
            },
        )

        val result = runBlocking { useCase.prepareToOpen(target) }

        assertTrue(result.isFailure)
        assertFalse(unfreezeCalled)
    }

    private fun useCase(
        targets: List<AppTarget>,
        isFrozen: Boolean,
        unfreeze: suspend (AppTarget) -> Result<Unit>,
    ) = DesktopShortcutTargetUseCase(
        targetStore = TestTargetStore(targets),
        isFrozen = { isFrozen },
        unfreeze = unfreeze,
    )

    private fun target(userId: Int): AppTarget =
        AppTarget.create("com.example.app", userId).getOrThrow()

    private class TestTargetStore(
        private val targets: List<AppTarget>,
    ) : FreezeTargetStore {
        override fun isAppAdded(pkg: String): Boolean =
            targets.any { it.isPrimaryUser && it.packageName.value == pkg }

        override fun isLocked(pkg: String): Boolean = false

        override fun allAddedPackages(): List<String> =
            targets.filter { it.isPrimaryUser }.map { it.packageName.value }

        override fun folderPackages(folderId: Long): List<String> = emptyList()

        override fun allAddedTargets(): List<AppTarget> = targets
    }
}
