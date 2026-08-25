package com.nbljsbdk.snowhide.test

import com.nbljsbdk.snowhide.core.engine.EngineProvider
import com.nbljsbdk.snowhide.core.engine.PowerEngine
import com.nbljsbdk.snowhide.core.model.FreezeTargetStore
import com.nbljsbdk.snowhide.core.model.QuickToggleStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakePowerEngine : PowerEngine {
    override val id: String = "fake"
    override val displayName: String = "Fake"
    override fun isAvailable(): Boolean = true

    val disabledPackages = mutableListOf<String>()
    val enabledPackages = mutableListOf<String>()
    val executedCommands = mutableListOf<String>()
    var frozenPackagesResult: Result<List<String>> = Result.success(emptyList())
    var frozenResult: Result<Boolean> = Result.success(false)
    var failExec: Boolean = false

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

class FakeQuickToggleStore(
    members: List<String> = emptyList(),
    opened: List<String> = emptyList(),
) : QuickToggleStore {
    private val _members = MutableStateFlow(members)
    private val _opened = MutableStateFlow(opened)

    override val members: StateFlow<List<String>> = _members
    override val opened: StateFlow<List<String>> = _opened

    override fun setOpened(packages: Collection<String>) {
        _opened.value = packages.distinct()
    }

    override fun clearOpened() {
        _opened.value = emptyList()
    }
}
