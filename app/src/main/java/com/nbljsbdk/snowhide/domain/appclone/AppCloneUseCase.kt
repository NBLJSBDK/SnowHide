package com.nbljsbdk.snowhide.domain.appclone

import com.nbljsbdk.snowhide.core.engine.EngineProvider
import com.nbljsbdk.snowhide.core.engine.TargetedPowerEngine
import com.nbljsbdk.snowhide.core.model.AppCloneSelectionStore
import com.nbljsbdk.snowhide.core.model.AppTarget
import com.nbljsbdk.snowhide.core.model.UserProfile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 用户空间应用信息及其目标状态。 */
data class AppCloneApp(
    val target: AppTarget,
    val frozen: Boolean,
    val isSystem: Boolean,
) {
    val packageName: String
        get() = target.packageName.value
}

/** 用户空间的 UI 可用信息，隐藏底层 pm 输出格式。 */
data class AppCloneUser(
    val id: Int,
    val name: String,
    val isManagedProfile: Boolean,
    val running: Boolean,
)

/** 增删应用中分身模式的一次完整快照。 */
data class AppCloneSnapshot(
    val users: List<AppCloneUser>,
    val selectedUserId: Int?,
    val apps: List<AppCloneApp>,
)

/**
 * 应用分身业务入口。
 *
 * 所有操作都先确认目标用户仍存在、应用确实安装在该用户空间，随后才执行
 * 带明确 `--user` 的命令；目标失效时绝不回退到 user 0。
 */
class AppCloneUseCase(
    private val engineProvider: EngineProvider,
    private val selectionStore: AppCloneSelectionStore,
    private val selfPackageName: String,
) {

    /** 同一引擎连接上的目标命令串行执行，连续滑动不会丢操作或并发 transact。 */
    private val targetOperationMutex = Mutex()

    /** 读取用户空间，并加载当前选中用户的应用和冻结状态。 */
    suspend fun refresh(): Result<AppCloneSnapshot> {
        val engine = targetedEngine()
            ?: return Result.failure(IllegalStateException("当前权限引擎不支持用户空间操作"))
        val users = engine.listUsers().getOrElse { return Result.failure(it) }
            .filterNot { it.id == PRIMARY_USER_ID }
            .distinctBy { it.id }
        val uiUsers = users.map(::toAppCloneUser)
        if (users.isEmpty()) {
            return Result.success(AppCloneSnapshot(emptyList(), null, emptyList()))
        }

        val stored = selectionStore.selectedUserId.value
        val selected = users.firstOrNull { it.id == stored }?.id
            ?: users.first().id
        if (stored != selected) selectionStore.setSelectedUserId(selected)
        return loadSnapshot(engine, uiUsers, users, selected)
    }

    /** 选择一个当前仍存在的用户空间，并立即刷新该空间的应用状态。 */
    suspend fun selectUser(userId: Int): Result<AppCloneSnapshot> {
        if (userId < 0) {
            return Result.failure(IllegalArgumentException("非法用户 ID：$userId"))
        }
        if (userId == PRIMARY_USER_ID) {
            return Result.failure(IllegalArgumentException("应用分身不操作主用户 user 0"))
        }
        val engine = targetedEngine()
            ?: return Result.failure(IllegalStateException("当前权限引擎不支持用户空间操作"))
        val users = engine.listUsers().getOrElse { return Result.failure(it) }
            .filterNot { it.id == PRIMARY_USER_ID }
            .distinctBy { it.id }
        if (users.none { it.id == userId }) {
            return Result.failure(IllegalStateException("用户空间 $userId 不存在，未执行任何操作"))
        }
        selectionStore.setSelectedUserId(userId)
        return loadSnapshot(engine, users.map(::toAppCloneUser), users, userId)
    }

    /** 冻结指定用户空间中的应用。 */
    suspend fun freezeApp(target: AppTarget): Result<Unit> = targetOperationMutex.withLock {
        val engine = targetedEngine()
            ?: return@withLock Result.failure(IllegalStateException("当前权限引擎不支持用户空间操作"))
        validateTarget(engine, target).getOrElse { return@withLock Result.failure(it) }
        engine.disableApp(target)
    }

    /** 解冻指定用户空间中的应用。 */
    suspend fun unfreezeApp(target: AppTarget): Result<Unit> = targetOperationMutex.withLock {
        val engine = targetedEngine()
            ?: return@withLock Result.failure(IllegalStateException("当前权限引擎不支持用户空间操作"))
        validateTarget(engine, target).getOrElse { return@withLock Result.failure(it) }
        engine.enableApp(target)
    }

    private fun targetedEngine(): TargetedPowerEngine? =
        engineProvider.primaryEngine.value as? TargetedPowerEngine

    private suspend fun loadSnapshot(
        engine: TargetedPowerEngine,
        uiUsers: List<AppCloneUser>,
        users: List<UserProfile>,
        userId: Int,
    ): Result<AppCloneSnapshot> {
        if (users.none { it.id == userId }) {
            return Result.failure(IllegalStateException("用户空间 $userId 不存在，未执行任何操作"))
        }
        val installed = engine.listInstalledPackages(userId).getOrElse { return Result.failure(it) }
        val frozen = engine.listFrozenPackages(userId).getOrElse { return Result.failure(it) }.toSet()
        val system = engine.listSystemPackages(userId).getOrElse { return Result.failure(it) }.toSet()
        val apps = installed.asSequence()
            .filterNot { it == selfPackageName }
            .mapNotNull { pkg ->
                AppTarget.create(pkg, userId).getOrNull()?.let { target ->
                    AppCloneApp(
                        target = target,
                        frozen = pkg in frozen,
                        isSystem = pkg in system,
                    )
                }
            }
            .distinctBy { it.packageName }
            .sortedBy { it.packageName }
            .toList()
        return Result.success(AppCloneSnapshot(uiUsers, userId, apps))
    }

    private fun toAppCloneUser(user: UserProfile): AppCloneUser = AppCloneUser(
        id = user.id,
        name = user.name,
        isManagedProfile = user.isManagedProfile,
        running = user.running,
    )

    private suspend fun validateTarget(
        engine: TargetedPowerEngine,
        target: AppTarget,
    ): Result<Unit> {
        if (target.packageName.value == selfPackageName) {
            return Result.failure(IllegalArgumentException("不能操作雪藏自身"))
        }
        if (target.userId == PRIMARY_USER_ID) {
            return Result.failure(IllegalArgumentException("应用分身不操作主用户 user 0"))
        }
        val users = engine.listUsers().getOrElse { return Result.failure(it) }
        if (users.none { it.id == target.userId }) {
            return Result.failure(IllegalStateException("目标用户空间 ${target.userId} 不存在，未执行任何操作"))
        }
        val installed = engine.listInstalledPackages(target.userId).getOrElse {
            return Result.failure(it)
        }
        if (target.packageName.value !in installed) {
            return Result.failure(
                IllegalStateException(
                    "应用 ${target.packageName.value} 未安装在用户空间 ${target.userId}，未执行任何操作",
                ),
            )
        }
        return Result.success(Unit)
    }

    private companion object {
        private const val PRIMARY_USER_ID = 0
    }
}
