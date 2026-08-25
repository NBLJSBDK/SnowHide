package com.nbljsbdk.snowhide.domain

import com.nbljsbdk.snowhide.core.engine.EngineProvider
import com.nbljsbdk.snowhide.core.model.FreezeTargetStore
import com.nbljsbdk.snowhide.core.model.QuickToggleStore
import com.nbljsbdk.snowhide.core.operation.PmOperation
import com.nbljsbdk.snowhide.data.repo.FrozenStateStore

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
) {

    /** 磁贴只需观察是否存在本次点亮快照，不接触 SP。 */
    val opened = repository.opened

    /** 熄灭结果：冻结数量 + 因锁定跳过的包 + 失败信息 */
    data class TurnOffResult(
        val frozen: Int,
        val lockedSkipped: List<String>,
        val failures: List<String>,
    )

    /** 点亮：解冻成员中「已添加且被冻结」的应用，并记录本批 opened */
    suspend fun lightUp(): Result<Int> {
        val engine = engineProvider.primaryEngine.value
            ?: return Result.failure(IllegalStateException("没有可用的权限引擎"))
        val added = targetStore.allAddedPackages().toSet()
        val frozen = engine.listFrozenPackages().getOrElse { return Result.failure(it) }.toSet()
        val targets = repository.members.value.filter { it in added && it in frozen }

        // 批量解冻（<20 逐个 / ≥20 串联分块，统一进度）
        val result = engine.execBatched(targets, PmOperation.ENABLE, "解冻")
        val success = result.getOrNull() ?: 0
        val failedPkgs = if (result.isFailure) {
            // 从失败消息里不好还原明细，保守处理：失败的都记为未打开
            targets
        } else emptyList()
        // 只记录解冻成功的（熄灭时冻回这批）
        repository.setOpened(targets.filterNot { it in failedPkgs })
        // 同步共享冻结状态（主屏霜化/dock 立即更新）
        FrozenStateStore.refresh()
        return result
    }

    /** 熄灭：冻回本批打开的应用；有锁的跳过 */
    suspend fun turnOff(): Result<TurnOffResult> {
        val opened = repository.opened.value
        val unlocked = opened.filterNot { targetStore.isLocked(it) }
        val lockedSkipped = opened.filter { targetStore.isLocked(it) }
        val engine = engineProvider.primaryEngine.value
            ?: return Result.failure(IllegalStateException("没有可用的权限引擎"))
        // 批量冻结（统一进度）
        val result = engine.execBatched(unlocked, PmOperation.DISABLE_USER, "停用")
        if (result.isFailure) {
            return Result.failure(result.exceptionOrNull() ?: IllegalStateException("快速启停停用失败"))
        }
        repository.clearOpened()
        // 同步共享冻结状态（主屏霜化/dock 立即更新）
        FrozenStateStore.refresh()
        return Result.success(
            TurnOffResult(
                frozen = result.getOrNull() ?: 0,
                lockedSkipped = lockedSkipped,
                failures = emptyList(),
            )
        )
    }

    /**
     * 反转（App Shortcut「快速启停」用）：
     * opened 非空（点亮中）→ 熄灭冻回；空 → 点亮解冻。
     * 磁贴 UI 由 TileService.onStartListening 按 opened 恢复，自动同步。
     */
    suspend fun toggle(): Result<Int> {
        return if (repository.opened.value.isNotEmpty()) {
            turnOff().map { it.frozen }
        } else {
            lightUp()
        }
    }
}
