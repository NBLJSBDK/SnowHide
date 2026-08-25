package com.nbljsbdk.snowhide.core.mode

import com.nbljsbdk.snowhide.core.engine.EngineProvider
import com.nbljsbdk.snowhide.core.operation.PmCommand
import com.nbljsbdk.snowhide.core.operation.PmOperation

/**
 * 冻结执行器——引擎 × 模式分发（扩展点）
 *
 * 同一模式在不同引擎下实现不同（如 FREEZE：Shizuku 一条 pm 命令，
 * Device Owner 是 suspend+hide 两步组合）。分发逻辑收在这里，
 * 新增模式/引擎时只需扩展本类，UI 与数据层零改动。
 */
class FreezeExecutor(private val engineProvider: EngineProvider) {

    /**
     * 冻结指定应用
     * @param mode 冻结模式（P0 仅 FREEZE）
     * @return 失败原因封装在 Result 中
     */
    suspend fun freeze(mode: FreezeMode, pkg: String): Result<Unit> {
        if (!mode.isImplemented) {
            return Result.failure(NotImplementedError("${mode.label} 模式未开放"))
        }
        val engine = engineProvider.primaryEngine.value
            ?: return Result.failure(IllegalStateException("没有可用的权限引擎（请先授权 Shizuku）"))
        return when (mode) {
            FreezeMode.FREEZE -> engine.disableApp(pkg)
            FreezeMode.SUSPEND -> engine.suspendApp(pkg, true)
            FreezeMode.HIDE -> engine.hideApp(pkg, true)
            // DISABLE：root 下用 pm disable（全局禁用），shizuku 不支持
            FreezeMode.DISABLE -> PmCommand.build(PmOperation.DISABLE, pkg)
                .fold({ command -> engine.exec(command).map { } }, { Result.failure(it) })
        }
    }

    /**
     * 解冻指定应用（按冻结时使用的模式反向操作）
     */
    suspend fun unfreeze(mode: FreezeMode, pkg: String): Result<Unit> {
        val engine = engineProvider.primaryEngine.value
            ?: return Result.failure(IllegalStateException("没有可用的权限引擎（请先授权 Shizuku）"))
        return when (mode) {
            FreezeMode.FREEZE -> engine.enableApp(pkg)
            FreezeMode.SUSPEND -> engine.suspendApp(pkg, false)
            FreezeMode.HIDE -> engine.hideApp(pkg, false)
            FreezeMode.DISABLE -> PmCommand.build(PmOperation.ENABLE, pkg)
                .fold({ command -> engine.exec(command).map { } }, { Result.failure(it) })
        }
    }

    /**
     * 查询应用是否处于冻结状态
     */
    suspend fun isFrozen(mode: FreezeMode, pkg: String): Result<Boolean> {
        val engine = engineProvider.primaryEngine.value
            ?: return Result.failure(IllegalStateException("没有可用的权限引擎"))
        return engine.isFrozen(pkg)
    }
}
