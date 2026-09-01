package com.nbljsbdk.snowhide.core.engine

import com.nbljsbdk.snowhide.core.model.AppTarget
import com.nbljsbdk.snowhide.core.model.UserProfile

/**
 * 支持按具体 Android 用户空间操作的引擎能力。
 *
 * 这是 PowerEngine 的可选能力，不改变既有 user 0 冻结接口；没有该能力的
 * 引擎不能被应用分身 UseCase 当作目标引擎使用。
 */
interface TargetedPowerEngine {
    suspend fun listUsers(): Result<List<UserProfile>>

    suspend fun listInstalledPackages(userId: Int): Result<List<String>>

    suspend fun listFrozenPackages(userId: Int): Result<List<String>>

    suspend fun listSystemPackages(userId: Int): Result<List<String>>

    suspend fun disableApp(target: AppTarget): Result<Unit>

    suspend fun enableApp(target: AppTarget): Result<Unit>
}
