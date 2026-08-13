package com.nbljsbdk.snowhide.core.engine.impl

import com.nbljsbdk.snowhide.core.engine.PowerEngine

/**
 * Device Owner 引擎（P2 实现，空壳预留）
 *
 * 将来实现：DevicePolicyManager.setPackagesSuspended /
 * setApplicationHidden（dhizuku 激活的 Device Owner）。
 * 冻结 = suspend + hide 组合（见设计文档 §2.3 矩阵）。
 */
class DoEngineImpl : PowerEngine {

    override val id: String = "do"
    override val displayName: String = "Device Owner"

    override fun isAvailable(): Boolean = false

    override suspend fun exec(cmd: String): Result<String> =
        Result.failure(NotImplementedError("Device Owner 引擎未实现（P2）"))

    override suspend fun disableApp(pkg: String): Result<Unit> =
        Result.failure(NotImplementedError("Device Owner 引擎未实现（P2）"))

    override suspend fun enableApp(pkg: String): Result<Unit> =
        Result.failure(NotImplementedError("Device Owner 引擎未实现（P2）"))

    override suspend fun isFrozen(pkg: String): Result<Boolean> =
        Result.failure(NotImplementedError("Device Owner 引擎未实现（P2）"))
}
