package com.nbljsbdk.snowhide.domain

import com.nbljsbdk.snowhide.core.engine.PowerEngine
import com.nbljsbdk.snowhide.data.repo.BatchProgress

/**
 * 批量 pm 命令执行（用户拍板终版：**逐个**执行，进度条平滑 +1）
 *
 * 逐个 exec 每个应用（transact 已在 IO 线程，主线程不卡），
 * 每完成一个 BatchProgress.update +1——弹窗/进度条平滑递增。
 * 不做 `;` 串联（会一卡一卡、进度不丝滑）。
 *
 * 使用方：FreezeUseCase（停用/启用全部、智能清理、神之一手）、
 * QuickToggleUseCase（快速启停点亮/熄灭）。
 */
suspend fun PowerEngine.execBatched(
    pkgs: List<String>,
    pmPrefix: String,
    verb: String,
): Result<Int> {
    if (pkgs.isEmpty()) return Result.success(0)
    val total = pkgs.size
    BatchProgress.begin(total, verb)
    var success = 0
    val failures = mutableListOf<String>()
    try {
        pkgs.forEachIndexed { i, pkg ->
            exec("$pmPrefix $pkg")
                .onSuccess { success++ }
                .onFailure { failures.add("$pkg: ${it.message}") }
            BatchProgress.update(i + 1, total)
        }
    } finally {
        BatchProgress.end()
    }
    return if (failures.isEmpty()) Result.success(success)
    else Result.failure(IllegalStateException("部分失败：${failures.joinToString("；")}"))
}
