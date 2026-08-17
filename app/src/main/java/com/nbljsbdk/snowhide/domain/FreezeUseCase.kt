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
        return engine.execBatched(filtered, "pm disable-user --user 0", "停用")
    }

    /** 一键解冻全部已添加应用（齿轮菜单「启用全部」，全部解冻兜底） */
    suspend fun unfreezeAll(): Result<Int> {
        val engine = executorEngine()
            ?: return Result.failure(IllegalStateException("没有可用的权限引擎"))
        val targets = gridRepository.allAddedPackages()
        if (targets.isEmpty()) return Result.success(0)
        return engine.execBatched(targets, "pm enable", "启用")
    }

    /**
     * 智能清理（底部图标栏最右按钮）：停用全部未锁定且未冻结的应用
     * 设计文档 §3.6；走统一批量入口（分块+进度）。
     */
    suspend fun quickClean(): Result<Int> {
        val engine = executorEngine()
            ?: return Result.failure(IllegalStateException("没有可用的权限引擎"))
        val frozenSet = engine.listFrozenPackages().getOrDefault(emptyList()).toSet()
        val targets = gridRepository.allAddedPackages()
            .filter { !gridRepository.isLocked(it) && it !in frozenSet }
        if (targets.isEmpty()) return Result.success(0)
        return engine.execBatched(targets, "pm disable-user --user 0", "停用")
    }

    /**
     * ⚠️ 神之一手（关于页，正式功能，用户拍板优先级最高）：
     * 解冻目标 = **全部用户应用**（不论是否在雪藏列表）+
     * **已添加列表中的系统应用**。
     * 未添加的系统应用（厂商预置禁用，Shell 无权启用）**不碰**——
     * 消除「Shell cannot change component state」失败噪音。
     *
     * 实现：`pm list packages -d`（全部禁用）与 `-d -s`（禁用系统应用）
     * 集合运算区分用户/系统；走统一批量入口（逐个+进度）。
     */
    suspend fun unfreezeEverything(): Result<Int> {
        val engine = executorEngine()
            ?: return Result.failure(IllegalStateException("没有可用的权限引擎"))
        val allFrozen = engine.listFrozenPackages().getOrElse { return Result.failure(it) }
        if (allFrozen.isEmpty()) return Result.success(0)
        // 禁用的系统应用列表
        val sysFrozen = engine.exec("pm list packages -d -s")
            .getOrElse { return Result.failure(it) }
            .lineSequence()
            .mapNotNull { it.removePrefix("package:").trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        val added = gridRepository.allAddedPackages().toSet()
        // 用户应用（非系统，不论列表）+ 已添加的系统应用
        val targets = allFrozen.filter { pkg ->
            pkg !in sysFrozen || pkg in added
        }
        if (targets.isEmpty()) return Result.success(0)
        return engine.execBatched(targets, "pm enable", "解冻")
    }
}
