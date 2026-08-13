package com.nbljsbdk.snowhide.domain

import com.nbljsbdk.snowhide.core.engine.EngineManager
import com.nbljsbdk.snowhide.core.mode.FreezeExecutor
import com.nbljsbdk.snowhide.core.mode.FreezeMode
import com.nbljsbdk.snowhide.data.repo.GridRepository

/**
 * 冻结业务用例——所有冻结/解冻操作唯一入口（UI 永不直连 FreezeExecutor）
 */
class FreezeUseCase(
    private val executor: FreezeExecutor,
    private val gridRepository: GridRepository,
    private val engineManager: EngineManager,
) {

    /**
     * ⚠️ 安全特例（用户拍板，设计文档 §1 唯一例外）：
     * 「移除并卸载」——真正卸载应用（删除应用+其数据）。
     * 仅允许用户在长按菜单明确选择「移除并卸载」并二次确认后调用；
     * 其余一切操作仍遵守「不删任何应用数据」。
     * 实现：shell 身份执行 `pm uninstall --user 0 <pkg>`（经 PowerEngine）。
     */
    suspend fun uninstallApp(pkg: String): Result<Unit> {
        val engine = executorEngine() ?: return Result.failure(IllegalStateException("没有可用的权限引擎"))
        return engine.exec("pm uninstall --user 0 $pkg").map { }
    }

    private fun executorEngine() =
        engineManager.primaryEngine.value

    /** 冻结单个应用（P0 单模式 FREEZE） */
    suspend fun freezeApp(pkg: String, mode: FreezeMode = FreezeMode.FREEZE): Result<Unit> =
        executor.freeze(mode, pkg)

    /** 解冻单个应用 */
    suspend fun unfreezeApp(pkg: String, mode: FreezeMode = FreezeMode.FREEZE): Result<Unit> =
        executor.unfreeze(mode, pkg)

    /** 查询冻结状态（引擎不可用时返回 false，不报错） */
    suspend fun isFrozen(pkg: String): Boolean =
        executor.isFrozen(FreezeMode.FREEZE, pkg).getOrDefault(false)

    /**
     * 一键冻结全部已添加应用（齿轮菜单「停用全部」/「停用目录」）
     * @param onlyFolderId 非 null 时只冻结该文件夹内应用（目录级批量）
     * @param exceptLocked 是否豁免锁定应用
     * @return 成功冻结数量
     */
    suspend fun freezeAll(
        onlyFolderId: Long? = null,
        exceptLocked: Boolean = false,
    ): Result<Int> {
        val targets = when (onlyFolderId) {
            null -> gridRepository.allAddedPackages()
            else -> gridRepository.folderApps.value
                .filter { it.folderId == onlyFolderId }
                .map { it.pkg }
        }
        var success = 0
        val failures = mutableListOf<String>()
        targets.forEach { pkg ->
            if (exceptLocked && gridRepository.isLocked(pkg)) return@forEach
            executor.freeze(FreezeMode.FREEZE, pkg)
                .onSuccess { success++ }
                .onFailure { failures.add("$pkg: ${it.message}") }
        }
        return if (failures.isEmpty()) Result.success(success)
        else Result.failure(IllegalStateException("部分失败：${failures.joinToString("；")}"))
    }

    /** 一键解冻全部已添加应用（齿轮菜单「启用全部」，全部解冻兜底） */
    suspend fun unfreezeAll(): Result<Int> {
        var success = 0
        val failures = mutableListOf<String>()
        gridRepository.allAddedPackages().forEach { pkg ->
            executor.unfreeze(FreezeMode.FREEZE, pkg)
                .onSuccess { success++ }
                .onFailure { failures.add("$pkg: ${it.message}") }
        }
        return if (failures.isEmpty()) Result.success(success)
        else Result.failure(IllegalStateException("部分失败：${failures.joinToString("；")}"))
    }

    /**
     * 快速清理（底部图标栏最右按钮）：停用全部未锁定且未冻结的应用
     * 设计文档 §3.6
     *
     * 性能：冻结状态直接取共享内存（FrozenStateStore），
     * 冻结命令合并为**一次** `pm disable-user`（pm 支持多包名），
     * 避免逐个查询/执行导致卡顿。
     */
    suspend fun quickClean(): Result<Int> {
        val engine = executorEngine()
            ?: return Result.failure(IllegalStateException("没有可用的权限引擎"))
        val states = com.nbljsbdk.snowhide.data.repo.FrozenStateStore.states.value
        val targets = gridRepository.allAddedPackages()
            .filter { !gridRepository.isLocked(it) && states[it] != true }
        if (targets.isEmpty()) return Result.success(0)
        return engine.exec("pm disable-user --user 0 ${targets.joinToString(" ")}")
            .map { targets.size }
    }

    /**
     * ⚠️ 神之一手（关于页临时调试按钮，之后可能注释掉不用）
     *
     * 解冻设备上**全部**已冻结应用——包括未加入列表的应用与系统应用。
     * 数据源 `pm list packages -d`，逐个 `pm enable`。
     */
    suspend fun unfreezeEverything(): Result<Int> {
        val engine = executorEngine()
            ?: return Result.failure(IllegalStateException("没有可用的权限引擎"))
        val frozen = engine.listFrozenPackages().getOrElse { return Result.failure(it) }
        var success = 0
        val failures = mutableListOf<String>()
        frozen.forEach { pkg ->
            executor.unfreeze(FreezeMode.FREEZE, pkg)
                .onSuccess { success++ }
                .onFailure { failures.add("$pkg: ${it.message}") }
        }
        return if (failures.isEmpty()) Result.success(success)
        else Result.failure(IllegalStateException("部分失败：${failures.joinToString("；")}"))
    }
}
