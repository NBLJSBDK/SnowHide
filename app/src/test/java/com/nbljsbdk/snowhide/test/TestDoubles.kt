package com.nbljsbdk.snowhide.test

import com.nbljsbdk.snowhide.core.engine.EngineProvider
import com.nbljsbdk.snowhide.core.engine.PowerEngine
import com.nbljsbdk.snowhide.core.engine.TargetedPowerEngine
import com.nbljsbdk.snowhide.core.model.AppCloneSelectionStore
import com.nbljsbdk.snowhide.core.model.AppTarget
import com.nbljsbdk.snowhide.core.model.FreezeTargetStore
import com.nbljsbdk.snowhide.core.model.QuickToggleStore
import com.nbljsbdk.snowhide.core.model.UserProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakePowerEngine : PowerEngine, TargetedPowerEngine {
    override val id: String = "fake"
    override val displayName: String = "Fake"
    override fun isAvailable(): Boolean = true

    val disabledPackages = mutableListOf<String>()
    val enabledPackages = mutableListOf<String>()
    val executedCommands = mutableListOf<String>()
    var frozenPackagesResult: Result<List<String>> = Result.success(emptyList())
    var frozenResult: Result<Boolean> = Result.success(false)
    var failExec: Boolean = false
    var usersResult: Result<List<UserProfile>> = Result.success(emptyList())
    val installedPackagesByUser = mutableMapOf<Int, List<String>>()
    val frozenPackagesByUser = mutableMapOf<Int, List<String>>()
    val systemPackagesByUser = mutableMapOf<Int, List<String>>()
    val targetedDisabledTargets = mutableListOf<AppTarget>()
    val targetedEnabledTargets = mutableListOf<AppTarget>()
    var targetedOperationDelayMs: Long = 0
    var activeTargetedOperations: Int = 0
    var maxConcurrentTargetedOperations: Int = 0

    override suspend fun exec(cmd: String): Result<String> {
        executedCommands += cmd
        return if (failExec) {
            Result.failure(IllegalStateException("fake command failure"))
        } else {
            Result.success("")
        }
    }

    override suspend fun disableApp(pkg: String): Result<Unit> {
        disabledPackages += pkg
        return if (failExec) Result.failure(IllegalStateException("fake disable failure")) else Result.success(Unit)
    }

    override suspend fun enableApp(pkg: String): Result<Unit> {
        enabledPackages += pkg
        return if (failExec) Result.failure(IllegalStateException("fake enable failure")) else Result.success(Unit)
    }

    override suspend fun isFrozen(pkg: String): Result<Boolean> = frozenResult

    override suspend fun listFrozenPackages(): Result<List<String>> = frozenPackagesResult

    override suspend fun suspendApp(pkg: String, suspend: Boolean): Result<Unit> =
        Result.failure(NotImplementedError())

    override suspend fun hideApp(pkg: String, hidden: Boolean): Result<Unit> =
        Result.failure(NotImplementedError())

    override suspend fun listUsers(): Result<List<UserProfile>> = usersResult

    override suspend fun listInstalledPackages(userId: Int): Result<List<String>> =
        Result.success(installedPackagesByUser[userId].orEmpty())

    override suspend fun listFrozenPackages(userId: Int): Result<List<String>> =
        Result.success(frozenPackagesByUser[userId].orEmpty())

    override suspend fun listSystemPackages(userId: Int): Result<List<String>> =
        Result.success(systemPackagesByUser[userId].orEmpty())

    override suspend fun disableApp(target: AppTarget): Result<Unit> {
        activeTargetedOperations += 1
        maxConcurrentTargetedOperations = maxOf(
            maxConcurrentTargetedOperations,
            activeTargetedOperations,
        )
        return try {
            if (targetedOperationDelayMs > 0) delay(targetedOperationDelayMs)
            targetedDisabledTargets += target
            if (failExec) Result.failure(IllegalStateException("fake targeted disable failure"))
            else Result.success(Unit)
        } finally {
            activeTargetedOperations -= 1
        }
    }

    override suspend fun enableApp(target: AppTarget): Result<Unit> {
        targetedEnabledTargets += target
        return if (failExec) Result.failure(IllegalStateException("fake targeted enable failure"))
        else Result.success(Unit)
    }
}

class FakeEngineProvider(engine: PowerEngine?) : EngineProvider {
    override val primaryEngine: StateFlow<PowerEngine?> = MutableStateFlow(engine)
}

class FakeFreezeTargetStore(
    private val added: Set<String>,
    private val locked: Set<String> = emptySet(),
    private val folders: Map<Long, List<String>> = emptyMap(),
) : FreezeTargetStore {
    override fun isAppAdded(pkg: String): Boolean = pkg in added

    override fun isLocked(pkg: String): Boolean = pkg in locked

    override fun allAddedPackages(): List<String> = added.toList()

    override fun folderPackages(folderId: Long): List<String> = folders[folderId].orEmpty()
}

class FakeTargetFreezeStore(
    private val addedTargets: Set<AppTarget>,
    private val lockedTargets: Set<AppTarget> = emptySet(),
) : FreezeTargetStore {
    override fun isAppAdded(pkg: String): Boolean =
        AppTarget.create(pkg, AppTarget.PRIMARY_USER_ID).getOrNull()?.let { it in addedTargets } == true

    override fun isLocked(pkg: String): Boolean =
        AppTarget.create(pkg, AppTarget.PRIMARY_USER_ID).getOrNull()?.let { it in lockedTargets } == true

    override fun allAddedPackages(): List<String> =
        addedTargets.filter { it.isPrimaryUser }.map { it.packageName.value }

    override fun folderPackages(folderId: Long): List<String> = emptyList()

    override fun isAppAdded(target: AppTarget): Boolean = target in addedTargets

    override fun isLocked(target: AppTarget): Boolean = target in lockedTargets

    override fun allAddedTargets(): List<AppTarget> = addedTargets.toList()
}

class FakeQuickToggleStore(
    members: List<String> = emptyList(),
    opened: List<String> = emptyList(),
) : QuickToggleStore {
    private val _members = MutableStateFlow(members.mapNotNull { target(it) })
    private val _opened = MutableStateFlow(opened.mapNotNull { target(it) })

    override val members: StateFlow<List<AppTarget>> = _members
    override val opened: StateFlow<List<AppTarget>> = _opened

    override fun setOpened(targets: Collection<AppTarget>) {
        _opened.value = targets.distinct()
    }

    override fun clearOpened() {
        _opened.value = emptyList()
    }

    private fun target(pkg: String): AppTarget? =
        AppTarget.create(pkg, AppTarget.PRIMARY_USER_ID).getOrNull()
}

class TargetQuickToggleStore(
    members: List<AppTarget> = emptyList(),
    opened: List<AppTarget> = emptyList(),
) : QuickToggleStore {
    private val _members = MutableStateFlow(members)
    private val _opened = MutableStateFlow(opened)

    override val members: StateFlow<List<AppTarget>> = _members
    override val opened: StateFlow<List<AppTarget>> = _opened

    override fun setOpened(targets: Collection<AppTarget>) {
        _opened.value = targets.distinct()
    }

    override fun clearOpened() {
        _opened.value = emptyList()
    }
}

class FakeAppCloneSelectionStore(selectedUserId: Int? = null) : AppCloneSelectionStore {
    private val _selectedUserId = MutableStateFlow(selectedUserId)

    override val selectedUserId: StateFlow<Int?> = _selectedUserId

    override fun setSelectedUserId(userId: Int) {
        _selectedUserId.value = userId
    }
}
