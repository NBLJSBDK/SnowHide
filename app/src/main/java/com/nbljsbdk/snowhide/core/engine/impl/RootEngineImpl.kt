package com.nbljsbdk.snowhide.core.engine.impl

import com.nbljsbdk.snowhide.core.engine.PowerEngine

/**
 * Root 引擎（P3 实现，空壳预留）
 *
 * 将来实现：`su -c pm ...` 执行。
 * 注意黑白门「Use Shell」教训：root 与 shell 可分别挂起同一应用，
 * 冻结/解冻身份必须一致（多引擎身份一致性，P3 处理）。
 */
class RootEngineImpl : PowerEngine {

    override val id: String = "root"
    override val displayName: String = "Root"

    override fun isAvailable(): Boolean = false

    override suspend fun exec(cmd: String): Result<String> =
        Result.failure(NotImplementedError("Root 引擎未实现（P3）"))

    override suspend fun disableApp(pkg: String): Result<Unit> =
        Result.failure(NotImplementedError("Root 引擎未实现（P3）"))

    override suspend fun enableApp(pkg: String): Result<Unit> =
        Result.failure(NotImplementedError("Root 引擎未实现（P3）"))

    override suspend fun isFrozen(pkg: String): Result<Boolean> =
        Result.failure(NotImplementedError("Root 引擎未实现（P3）"))
}
