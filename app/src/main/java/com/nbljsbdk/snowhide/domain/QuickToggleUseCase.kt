package com.nbljsbdk.snowhide.domain

import com.nbljsbdk.snowhide.core.engine.EngineProvider
import com.nbljsbdk.snowhide.core.engine.PowerEngine
import com.nbljsbdk.snowhide.core.engine.TargetedPowerEngine
import com.nbljsbdk.snowhide.core.model.AppTarget
import com.nbljsbdk.snowhide.core.model.FreezeTargetStore
import com.nbljsbdk.snowhide.core.model.QuickToggleStore
import com.nbljsbdk.snowhide.core.operation.PmOperation
import com.nbljsbdk.snowhide.data.repo.FrozenStateStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 快速启停触发用例（下拉磁贴逻辑，§3.9 用户拍板简化版）
 *
 * - **点亮**：解冻「成员 ∩ 已添加 ∩ 当前冻结」的应用，
 *   把本批解冻的记为 opened（持久化，进程重启不丢）
 * - **熄灭**：把 opened 批冻回去；**有锁定的应用不关闭**（跳过），
 *   返回跳过清单交给上层 toast 提示
 * - 成员与 opened 快照由 QuickToggleRepository 统一持久化
 * - 批量走共享 execBatched（阈值 20，统一进度）
 */
class QuickToggleUseCase(
    private val targetStore: FreezeTargetStore,
    private val engineProvider: EngineProvider,
    private val repository: QuickToggleStore,
    private val selfPackageName: String = "com.nbljsbdk.snowhide",
) {

    /** 磁贴和快捷方式可能同时触发，整个切换过程必须串行。 */
    private val operationMutex = Mutex()

    /** 磁贴只需观察是否存在本次点亮快照，不接触 SP。 */
    val opened = repository.opened

    /** 熄灭结果：冻结数量 + 因锁定跳过的包 + 失败信息 */
    data class TurnOffResult(
        val frozen: Int,
        val lockedSkipped: List<AppTarget>,
        val failures: List<AppTarget>,
    )

    /** 点亮：解冻成员中「已添加且被冻结」的应用，并记录本批 opened */
    suspend fun lightUp(): Result<Int> = operationMutex.withLock { lightUpInternal() }

    /** 熄灭：冻回本批打开的应用；有锁的跳过 */
    suspend fun turnOff(): Result<TurnOffResult> = operationMutex.withLock { turnOffInternal() }

    /**
     * 反转（App Shortcut「快速启停」用）：
     * opened 非空（点亮中）→ 熄灭冻回；空 → 点亮解冻。
     * 磁贴 UI 由 TileService.onStartListening 按 opened 恢复，自动同步。
     */
    suspend fun toggle(): Result<Int> = operationMutex.withLock {
        if (repository.opened.value.isNotEmpty()) {
            turnOffInternal().map { it.frozen }
        } else {
            lightUpInternal()
        }
    }

    private suspend fun lightUpInternal(): Result<Int> {
        val engine = engineProvider.primaryEngine.value
            ?: return Result.failure(IllegalStateException("没有可用的权限引擎"))
        val added = targetStore.allAddedTargets().toSet()
        val candidates = repository.members.value
            .filterNot { it.packageName.value == selfPackageName }
            .filter { it in added }
        validateTargets(engine, candidates).getOrElse { return Result.failure(it) }
        val frozen = frozenTargets(engine, candidates)
            .getOrElse { return Result.failure(it) }
        val targets = candidates.filter { it in frozen }

        val result = engine.execBatchedTargets(targets, PmOperation.ENABLE, "解冻")
        val failedTargets = if (result.isFailure) targets else emptyList()
        repository.setOpened(targets.filterNot { it in failedTargets })
        FrozenStateStore.refresh()
        return result
    }

    private suspend fun turnOffInternal(): Result<TurnOffResult> {
        val opened = repository.opened.value
            .filterNot { it.packageName.value == selfPackageName }
            .filter { targetStore.isAppAdded(it) }
        val unlocked = opened.filterNot { targetStore.isLocked(it) }
        val lockedSkipped = opened.filter { targetStore.isLocked(it) }
        val engine = engineProvider.primaryEngine.value
            ?: return Result.failure(IllegalStateException("没有可用的权限引擎"))
        validateTargets(engine, unlocked).getOrElse { return Result.failure(it) }
        val result = engine.execBatchedTargets(unlocked, PmOperation.DISABLE_USER, "停用")
        if (result.isFailure) {
            return Result.failure(result.exceptionOrNull() ?: IllegalStateException("快速启停停用失败"))
        }
        repository.clearOpened()
        FrozenStateStore.refresh()
        return Result.success(
            TurnOffResult(
                frozen = result.getOrNull() ?: 0,
                lockedSkipped = lockedSkipped,
                failures = emptyList(),
            )
        )
    }

    /** 按用户空间批量查询本次候选目标的冻结状态。 */
    private suspend fun frozenTargets(
        engine: PowerEngine,
        targets: Collection<AppTarget>,
    ): Result<Set<AppTarget>> {
        val frozen = linkedSetOf<AppTarget>()
        val primaryFrozen = engine.listFrozenPackages().getOrElse { return Result.failure(it) }.toSet()
        targets.filter { it.isPrimaryUser }
            .filter { it.packageName.value in primaryFrozen }
            .forEach(frozen::add)

        val clones = targets.filterNot { it.isPrimaryUser }
        if (clones.isEmpty()) return Result.success(frozen)
        val targeted = engine as? TargetedPowerEngine
            ?: return Result.failure(IllegalStateException("当前权限引擎不支持用户空间操作"))
        clones.groupBy { it.userId }.forEach { (userId, userTargets) ->
            val userFrozen = targeted.listFrozenPackages(userId).getOrElse { return Result.failure(it) }.toSet()
            userTargets.filter { it.packageName.value in userFrozen }.forEach(frozen::add)
        }
        return Result.success(frozen)
    }

    /** 执行快速启停前验证用户空间和包是否仍然有效，禁止静默回退到 user 0。 */
    private suspend fun validateTargets(
        engine: PowerEngine,
        targets: Collection<AppTarget>,
    ): Result<Unit> {
        val targeted = engine as? TargetedPowerEngine
        targets.distinct().forEach { target ->
            if (target.packageName.value == selfPackageName) {
                return Result.failure(IllegalArgumentException("不能操作雪藏自身"))
            }
            if (!target.isPrimaryUser) {
                if (targeted == null) {
                    return Result.failure(IllegalStateException("当前权限引擎不支持用户空间操作"))
                }
                val users = targeted.listUsers().getOrElse { return Result.failure(it) }
                if (users.none { it.id == target.userId }) {
                    return Result.failure(IllegalStateException("目标用户空间 ${target.userId} 不存在，未执行任何操作"))
                }
                val installed = targeted.listInstalledPackages(target.userId)
                    .getOrElse { return Result.failure(it) }
                if (target.packageName.value !in installed) {
                    return Result.failure(
                        IllegalStateException(
                            "应用 ${target.packageName.value} 未安装在用户空间 ${target.userId}，未执行任何操作",
                        ),
                    )
                }
            }
        }
        return Result.success(Unit)
    }
}
