package com.nbljsbdk.snowhide.domain

import com.nbljsbdk.snowhide.core.engine.EngineManager
import com.nbljsbdk.snowhide.core.mode.FreezeExecutor
import com.nbljsbdk.snowhide.core.mode.FreezeMode
import com.nbljsbdk.snowhide.core.operation.PmCommand
import com.nbljsbdk.snowhide.core.operation.PmOperation
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
        return PmCommand.build(PmOperation.UNINSTALL, pkg).fold(
            onSuccess = { command -> engine.exec(command).map { } },
            onFailure = { Result.failure(it) },
        )
    }

    private fun executorEngine() =
        engineManager.primaryEngine.value

    /** 冻结单个应用（P0 单模式 FREEZE） */
    suspend fun freezeApp(pkg: String, mode: FreezeMode = FreezeMode.FREEZE): Result<Unit> =
        executor.freeze(mode, pkg)

    /** 解冻单个应用 */
    suspend fun unfreezeApp(pkg: String, mode: FreezeMode = FreezeMode.FREEZE): Result<Unit> =
        executor.unfreeze(mode, pkg)

    /**
     * 冻结 Recent 划卡产生的一组应用。
     *
     * 只过滤“已添加”和“未锁定”，不查询当前冻结状态；调用方要求每个
     * 目标都发送一次 `pm disable-user`，并通过统一批量入口串行执行。
     */
    suspend fun freezePackages(packages: Collection<String>): Result<Int> {
        val targets = packages
            .asSequence()
            .filter { gridRepository.isAppAdded(it) }
            .filterNot { gridRepository.isLocked(it) }
            .distinct()
            .toList()
        if (targets.isEmpty()) return Result.success(0)
        val engine = executorEngine()
            ?: return Result.failure(IllegalStateException("没有可用的权限引擎"))
        return engine.execBatched(targets, PmOperation.DISABLE_USER, "划卡停用")
    }

    /** 查询冻结状态（引擎不可用时返回 false，不报错） */
    suspend fun isFrozen(pkg: String): Boolean =
        executor.isFrozen(FreezeMode.FREEZE, pkg).getOrDefault(false)

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
            null -> gridRepository.allAddedPackages()
            else -> gridRepository.folderApps.value
                .filter { it.folderId == onlyFolderId }
                .map { it.pkg }
        }
        val engine = executorEngine()
            ?: return Result.failure(IllegalStateException("没有可用的权限引擎"))
        val filtered = if (exceptLocked) {
            targets.filterNot { gridRepository.isLocked(it) }
        } else targets
        if (filtered.isEmpty()) return Result.success(0)
        return engine.execBatched(filtered, PmOperation.DISABLE_USER, "停用")
    }

    /** 一键解冻全部已添加应用（齿轮菜单「启用全部」，全部解冻兜底） */
    suspend fun unfreezeAll(): Result<Int> {
        val engine = executorEngine()
            ?: return Result.failure(IllegalStateException("没有可用的权限引擎"))
        val targets = gridRepository.allAddedPackages()
        if (targets.isEmpty()) return Result.success(0)
        return engine.execBatched(targets, PmOperation.ENABLE, "启用")
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
        val frozenSet = engine.listFrozenPackages().getOrDefault(emptyList()).toSet()
        val targets = gridRepository.allAddedPackages()
            .filter { !gridRepository.isLocked(it) && it !in frozenSet }
        if (targets.isEmpty()) return Result.success(emptyList())
        return engine.execBatched(targets, PmOperation.DISABLE_USER, "停用")
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
        val engine = executorEngine()
            ?: return Result.failure(IllegalStateException("没有可用的权限引擎"))
        val frozen = engine.listFrozenPackages().getOrElse { return Result.failure(it) }
        if (frozen.isEmpty()) return Result.success(0)
        return engine.execBatched(frozen, PmOperation.ENABLE, "解冻")
    }
}
