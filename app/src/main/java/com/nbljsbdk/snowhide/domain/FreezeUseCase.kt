package com.nbljsbdk.snowhide.domain

import com.nbljsbdk.snowhide.core.engine.EngineProvider
import com.nbljsbdk.snowhide.core.engine.TargetedPowerEngine
import com.nbljsbdk.snowhide.core.mode.FreezeExecutor
import com.nbljsbdk.snowhide.core.mode.FreezeMode
import com.nbljsbdk.snowhide.core.model.AppTarget
import com.nbljsbdk.snowhide.core.model.FreezeTargetStore
import com.nbljsbdk.snowhide.core.operation.PmCommand
import com.nbljsbdk.snowhide.core.operation.PmOperation
import com.nbljsbdk.snowhide.data.repo.RecentFreezeQueueRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 冻结业务用例——所有冻结/解冻操作唯一入口（UI 永不直连 FreezeExecutor）
 */
class FreezeUseCase(
    private val executor: FreezeExecutor,
    private val targetStore: FreezeTargetStore,
    private val engineProvider: EngineProvider,
    private val selfPackageName: String = "com.nbljsbdk.snowhide",
) {

    /** 目标用户的 pm 操作必须串行，避免连续滑动并发 transact。 */
    private val targetOperationMutex = Mutex()

    /**
     * ⚠️ 安全特例（用户拍板，设计文档 §1 唯一例外）：
     * 「移除并卸载」——真正卸载应用（删除应用+其数据）。
     * 仅允许用户在长按菜单明确选择「移除并卸载」并二次确认后调用；
     * 其余一切操作仍遵守「不删任何应用数据」。
     * 实现：shell 身份执行 `pm uninstall --user 0 <pkg>`（经 PowerEngine）。
     */
    suspend fun uninstallApp(pkg: String): Result<Unit> {
        return uninstallApp(AppTarget.create(pkg, AppTarget.PRIMARY_USER_ID).getOrElse { return Result.failure(it) })
    }

    /** 显式用户空间卸载；只有 UI 的二次确认路径才允许调用。 */
    suspend fun uninstallApp(target: AppTarget): Result<Unit> = targetOperationMutex.withLock {
        val engine = executorEngine()
            ?: return@withLock Result.failure(IllegalStateException("没有可用的权限引擎"))
        if (!target.isPrimaryUser) validateTarget(target).getOrElse { return@withLock Result.failure(it) }
        PmCommand.build(PmOperation.UNINSTALL, target.packageName.value, target.userId).fold(
            onSuccess = { command -> engine.exec(command).map { } },
            onFailure = { Result.failure(it) },
        )
    }

    private fun executorEngine() =
        engineProvider.primaryEngine.value

    /** 冻结单个应用（P0 单模式 FREEZE） */
    suspend fun freezeApp(pkg: String, mode: FreezeMode = FreezeMode.FREEZE): Result<Unit> =
        executor.freeze(mode, pkg)

    /** 解冻单个应用 */
    suspend fun unfreezeApp(pkg: String, mode: FreezeMode = FreezeMode.FREEZE): Result<Unit> {
        val target = AppTarget.create(pkg, AppTarget.PRIMARY_USER_ID)
            .getOrElse { return Result.failure(it) }
        RecentFreezeQueueRepository.removeTargets(listOf(target))
        return executor.unfreeze(mode, pkg)
    }

    /** 冻结明确用户空间目标；user 0 走既有 FreezeExecutor 合同。 */
    suspend fun freezeApp(target: AppTarget, mode: FreezeMode = FreezeMode.FREEZE): Result<Unit> =
        targetOperationMutex.withLock {
            if (target.isPrimaryUser) return@withLock freezeApp(target.packageName.value, mode)
            validateTarget(target).getOrElse { return@withLock Result.failure(it) }
            val engine = targetedEngine()
                ?: return@withLock Result.failure(IllegalStateException("当前权限引擎不支持用户空间操作"))
            if (mode != FreezeMode.FREEZE) {
                return@withLock Result.failure(NotImplementedError("分身暂只支持冻结模式"))
            }
            engine.disableApp(target)
        }

    /** 解冻明确用户空间目标；user 0 走既有 FreezeExecutor 合同。 */
    suspend fun unfreezeApp(target: AppTarget, mode: FreezeMode = FreezeMode.FREEZE): Result<Unit> =
        targetOperationMutex.withLock {
            // 用户明确选择解冻时，取消同一目标尚未执行的 Recent 冻结意图。
            RecentFreezeQueueRepository.removeTargets(listOf(target))
            if (target.isPrimaryUser) return@withLock unfreezeApp(target.packageName.value, mode)
            validateTarget(target).getOrElse { return@withLock Result.failure(it) }
            val engine = targetedEngine()
                ?: return@withLock Result.failure(IllegalStateException("当前权限引擎不支持用户空间操作"))
            if (mode != FreezeMode.FREEZE) {
                return@withLock Result.failure(NotImplementedError("分身暂只支持冻结模式"))
            }
            engine.enableApp(target)
        }

    /**
     * 冻结 Recent 划卡产生的一组应用。
     *
     * 只过滤“已添加”和“未锁定”，不查询当前冻结状态；调用方要求每个
     * 目标都发送一次 `pm disable-user`，并通过统一批量入口串行执行。
     */
    suspend fun freezePackages(packages: Collection<String>): Result<Int> {
        val targets = packages
            .asSequence()
            .filterNot { it == selfPackageName }
            .filter { targetStore.isAppAdded(it) }
            .filterNot { targetStore.isLocked(it) }
            .distinct()
            .toList()
        if (targets.isEmpty()) return Result.success(0)
        val engine = executorEngine()
            ?: return Result.failure(IllegalStateException("没有可用的权限引擎"))
        return engine.execBatched(targets, PmOperation.DISABLE_USER, "划卡停用")
    }

    /** 查询冻结状态，保留引擎失败原因供安全调用方处理。 */
    suspend fun isFrozenResult(pkg: String): Result<Boolean> =
        executor.isFrozen(FreezeMode.FREEZE, pkg)

    suspend fun isFrozenResult(target: AppTarget): Result<Boolean> {
        if (target.isPrimaryUser) return isFrozenResult(target.packageName.value)
        val engine = targetedEngine()
            ?: return Result.failure(IllegalStateException("当前权限引擎不支持用户空间操作"))
        validateTarget(target).getOrElse { return Result.failure(it) }
        return engine.listFrozenPackages(target.userId).map { target.packageName.value in it }
    }

    /** 兼容旧调用方：查询失败时仍返回 false。 */
    suspend fun isFrozen(pkg: String): Boolean =
        isFrozenResult(pkg).getOrDefault(false)

    /**
     * 一键冻结全部已添加应用（齿轮菜单「停用全部」/「停用目录」）
     * @param onlyFolderId 非 null 时只冻结该文件夹内应用（目录级批量）
     * @param exceptLocked 是否豁免锁定应用
     * @return 成功冻结数量
     *
     * 性能：≥20 个走 `;` 串联分块（每批 40）一次 exec——避免逐个起
     * sh 进程，100+ 应用批量操作不再卡死/ANR。
     */
    suspend fun freezeAll(
        onlyFolderId: Long? = null,
        exceptLocked: Boolean = false,
    ): Result<Int> {
        val targets = when (onlyFolderId) {
            null -> targetStore.allAddedPackages()
            else -> targetStore.folderPackages(onlyFolderId)
        }.filterNot { it == selfPackageName }
        val engine = executorEngine()
            ?: return Result.failure(IllegalStateException("没有可用的权限引擎"))
        val filtered = if (exceptLocked) {
            targets.filterNot { targetStore.isLocked(it) }
        } else targets
        if (filtered.isEmpty()) return Result.success(0)
        return engine.execBatched(filtered, PmOperation.DISABLE_USER, "停用")
    }

    /** 冻结全部已添加目标，包含非 user 0 分身。 */
    suspend fun freezeAllTargets(
        onlyFolderId: Long? = null,
        exceptLocked: Boolean = false,
    ): Result<Int> = runSerialized {
        freezeAllTargetsUnsafe(onlyFolderId, exceptLocked)
    }

    private suspend fun freezeAllTargetsUnsafe(
        onlyFolderId: Long?,
        exceptLocked: Boolean,
    ): Result<Int> {
        val targets = (if (onlyFolderId == null) {
            targetStore.allAddedTargets()
        } else {
            targetStore.folderTargets(onlyFolderId)
        }).filterNot(::isSelfTarget)
        val filtered = targets.let { candidates ->
            if (exceptLocked) candidates.filterNot { targetStore.isLocked(it) } else candidates
        }
        val engine = executorEngine()
            ?: return Result.failure(IllegalStateException("没有可用的权限引擎"))
        validateTargets(filtered).getOrElse { return Result.failure(it) }
        return engine.execBatchedTargets(filtered, PmOperation.DISABLE_USER, "停用")
    }

    /** 批量冻结指定目标，供“应用”按钮等已经完成规划的入口使用。 */
    suspend fun freezeTargets(targets: Collection<AppTarget>): Result<Int> = runSerialized {
        freezeTargetsUnsafe(targets)
    }

    private suspend fun freezeTargetsUnsafe(targets: Collection<AppTarget>): Result<Int> {
        val distinctTargets = targets.distinct().filterNot(::isSelfTarget)
        if (distinctTargets.isEmpty()) return Result.success(0)
        val engine = executorEngine()
            ?: return Result.failure(IllegalStateException("没有可用的权限引擎"))
        validateTargets(distinctTargets).getOrElse { return Result.failure(it) }
        return engine.execBatchedTargets(distinctTargets, PmOperation.DISABLE_USER, "停用")
    }

    /** 一键解冻全部已添加应用（齿轮菜单「启用全部」，全部解冻兜底） */
    suspend fun unfreezeAll(): Result<Int> {
        val engine = executorEngine()
            ?: return Result.failure(IllegalStateException("没有可用的权限引擎"))
        val targets = targetStore.allAddedPackages().filterNot { it == selfPackageName }
        if (targets.isEmpty()) return Result.success(0)
        return engine.execBatched(targets, PmOperation.ENABLE, "启用")
    }

    /** 启用全部已管理目标，包含非 user 0 分身。 */
    suspend fun unfreezeAllTargets(): Result<Int> = runSerialized {
        unfreezeAllTargetsUnsafe()
    }

    private suspend fun unfreezeAllTargetsUnsafe(): Result<Int> {
        val engine = executorEngine()
            ?: return Result.failure(IllegalStateException("没有可用的权限引擎"))
        val targets = targetStore.allAddedTargets().filterNot(::isSelfTarget)
        if (targets.isEmpty()) return Result.success(0)
        validateTargets(targets).getOrElse { return Result.failure(it) }
        return engine.execBatchedTargets(targets, PmOperation.ENABLE, "启用")
    }

    /** 批量解冻指定目标，供文件夹和其他已规划入口使用。 */
    suspend fun unfreezeTargets(targets: Collection<AppTarget>): Result<Int> = runSerialized {
        unfreezeTargetsUnsafe(targets)
    }

    private suspend fun unfreezeTargetsUnsafe(targets: Collection<AppTarget>): Result<Int> {
        val distinctTargets = targets.distinct().filterNot(::isSelfTarget)
        if (distinctTargets.isEmpty()) return Result.success(0)
        val engine = executorEngine()
            ?: return Result.failure(IllegalStateException("没有可用的权限引擎"))
        validateTargets(distinctTargets).getOrElse { return Result.failure(it) }
        return engine.execBatchedTargets(distinctTargets, PmOperation.ENABLE, "启用")
    }

    /**
     * 智能清理（底部图标栏最右按钮）：停用全部未锁定且未冻结的应用
     * 设计文档 §3.6；走统一批量入口（分块+进度）。
     */
    suspend fun quickClean(): Result<Int> =
        quickCleanPackages().map { it.size }

    /** 智能清理并返回成功清理的应用包名（锁屏通知需要显示名称） */
    suspend fun quickCleanPackages(): Result<List<String>> {
        val engine = executorEngine()
            ?: return Result.failure(IllegalStateException("没有可用的权限引擎"))
        val frozenSet = engine.listFrozenPackages().getOrElse { return Result.failure(it) }.toSet()
        val targets = targetStore.allAddedPackages()
            .filterNot { it == selfPackageName }
            .filter { !targetStore.isLocked(it) && it !in frozenSet }
        if (targets.isEmpty()) return Result.success(emptyList())
        return engine.execBatched(targets, PmOperation.DISABLE_USER, "停用")
            .map { targets }
    }

    /** 智能清理全部用户空间目标，返回成功执行的明确目标。 */
    suspend fun quickCleanTargets(): Result<List<AppTarget>> = runSerialized {
        quickCleanTargetsUnsafe()
    }

    private suspend fun quickCleanTargetsUnsafe(): Result<List<AppTarget>> {
        val engine = executorEngine()
            ?: return Result.failure(IllegalStateException("没有可用的权限引擎"))
        val frozen = frozenTargetsForAddedApps(engine).getOrElse { return Result.failure(it) }
        val targets = targetStore.allAddedTargets()
            .filterNot(::isSelfTarget)
            .filter { !targetStore.isLocked(it) && it !in frozen }
        if (targets.isEmpty()) return Result.success(emptyList())
        validateTargets(targets).getOrElse { return Result.failure(it) }
        return engine.execBatchedTargets(targets, PmOperation.DISABLE_USER, "停用")
            .map { targets }
    }

    /**
     * ⚠️ 神之一手（关于页，正式功能，用户拍板优先级最高）：
     * 解冻设备上**全部**已冻结应用——包括未加入列表的应用与**系统应用**。
     * 理由（用户拍板）：可能不小心冻结了系统应用/重置后无法解冻，
     * 神之一手必须够「神」——全部禁用项都尝试启用（失败的系统预置
     * 组件在结果弹窗里滚动可见+可复制）。
     * 数据源 `pm list packages -d`，走统一批量入口（逐个+进度）。
     */
    suspend fun unfreezeEverything(): Result<Int> {
        return runSerialized { unfreezeEverythingUnsafe() }
    }

    private suspend fun unfreezeEverythingUnsafe(): Result<Int> {
        val engine = executorEngine()
            ?: return Result.failure(IllegalStateException("没有可用的权限引擎"))
        val frozenTargets = mutableListOf<AppTarget>()
        engine.listFrozenPackages().getOrElse { return Result.failure(it) }
            .forEach { pkg ->
                if (pkg != selfPackageName) {
                    AppTarget.create(pkg, AppTarget.PRIMARY_USER_ID).getOrNull()?.let(frozenTargets::add)
                }
        }
        val targeted = targetedEngine()
        val managedCloneTargets = targetStore.allAddedTargets().filterNot { it.isPrimaryUser }
        if (managedCloneTargets.isNotEmpty() && targeted == null) {
            return Result.failure(IllegalStateException("当前权限引擎不支持用户空间操作"))
        }
        if (targeted != null) {
            val users = targeted.listUsers().getOrElse { return Result.failure(it) }
            users.filterNot { it.id == AppTarget.PRIMARY_USER_ID }.forEach { user ->
                val frozen = targeted.listFrozenPackages(user.id).getOrElse { return Result.failure(it) }
                frozen.forEach { pkg ->
                    if (pkg != selfPackageName) {
                        AppTarget.create(pkg, user.id).getOrNull()?.let(frozenTargets::add)
                    }
                }
            }
        }
        validateTargets(frozenTargets).getOrElse { return Result.failure(it) }
        if (frozenTargets.isEmpty()) return Result.success(0)
        return engine.execBatchedTargets(frozenTargets, PmOperation.ENABLE, "解冻")
    }

    private fun targetedEngine(): TargetedPowerEngine? =
        engineProvider.primaryEngine.value as? TargetedPowerEngine

    private suspend fun <T> runSerialized(block: suspend () -> T): T =
        targetOperationMutex.withLock { block() }

    private suspend fun validateTarget(target: AppTarget): Result<Unit> {
        if (target.packageName.value == selfPackageName) {
            return Result.failure(IllegalArgumentException("不能操作雪藏自身"))
        }
        if (target.isPrimaryUser) return Result.success(Unit)
        val engine = targetedEngine()
            ?: return Result.failure(IllegalStateException("当前权限引擎不支持用户空间操作"))
        val users = engine.listUsers().getOrElse { return Result.failure(it) }
        if (users.none { it.id == target.userId }) {
            return Result.failure(IllegalStateException("目标用户空间 ${target.userId} 不存在，未执行任何操作"))
        }
        val installed = engine.listInstalledPackages(target.userId).getOrElse { return Result.failure(it) }
        if (target.packageName.value !in installed) {
            return Result.failure(
                IllegalStateException("应用 ${target.packageName.value} 未安装在用户空间 ${target.userId}，未执行任何操作"),
            )
        }
        return Result.success(Unit)
    }

    /** 批量命令开始前先验证全部目标，避免旧数据或失效用户空间触发半批操作。 */
    private suspend fun validateTargets(targets: Collection<AppTarget>): Result<Unit> {
        targets.distinct().forEach { target ->
            validateTarget(target).getOrElse { return Result.failure(it) }
        }
        return Result.success(Unit)
    }

    private fun isSelfTarget(target: AppTarget): Boolean =
        target.packageName.value == selfPackageName

    private suspend fun frozenTargetsForAddedApps(engine: com.nbljsbdk.snowhide.core.engine.PowerEngine): Result<Set<AppTarget>> {
        val result = mutableSetOf<AppTarget>()
        engine.listFrozenPackages().getOrElse { return Result.failure(it) }
            .forEach { pkg -> AppTarget.create(pkg, AppTarget.PRIMARY_USER_ID).getOrNull()?.let(result::add) }
        val targeted = targetedEngine()
        val cloneTargets = targetStore.allAddedTargets().filterNot { it.isPrimaryUser }
        if (cloneTargets.isNotEmpty() && targeted == null) {
            return Result.failure(IllegalStateException("当前权限引擎不支持用户空间操作"))
        }
        cloneTargets.groupBy { it.userId }.forEach { (userId, targets) ->
            val frozen = targeted!!.listFrozenPackages(userId).getOrElse { return Result.failure(it) }.toSet()
            targets.filter { it.packageName.value in frozen }.forEach(result::add)
        }
        return Result.success(result)
    }

}
