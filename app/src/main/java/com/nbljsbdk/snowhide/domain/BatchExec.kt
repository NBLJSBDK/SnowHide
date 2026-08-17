package com.nbljsbdk.snowhide.domain

import com.nbljsbdk.snowhide.core.engine.PowerEngine
import com.nbljsbdk.snowhide.data.repo.BatchProgress

/**
 * 批量 pm 命令执行（统一批量入口，用户拍板阈值 20）
 *
 * - <20 个：逐个 exec（失败明细精确，无额外开销）
 * - ≥20 个：`;` 串联分块（每批 [BATCH_SIZE]）单次 exec——
 *   避免逐个起 sh 进程（pm 不支持多包名参数）
 *
 * 每批完成后更新 [BatchProgress]（进度条）；finally 清空。
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
        if (total < BATCH_THRESHOLD) {
            pkgs.forEachIndexed { i, pkg ->
                exec("$pmPrefix $pkg")
                    .onSuccess { success++ }
                    .onFailure { failures.add("$pkg: ${it.message}") }
                BatchProgress.update(i + 1, total)
            }
        } else {
            pkgs.chunked(BATCH_SIZE).forEachIndexed { chunkIdx, chunk ->
                val cmd = chunk.joinToString("; ") { "$pmPrefix $it" }
                exec(cmd).onFailure {
                    // 整块失败：逐个重试定位失败项（保留粒度）
                    chunk.forEach { pkg ->
                        exec("$pmPrefix $pkg")
                            .onSuccess { success++ }
                            .onFailure { failures.add("$pkg: ${it.message}") }
                    }
                }.onSuccess { success += chunk.size }
                BatchProgress.update((chunkIdx + 1) * chunk.size, total)
            }
        }
    } finally {
        BatchProgress.end()
    }
    return if (failures.isEmpty()) Result.success(success)
    else Result.failure(IllegalStateException("部分失败：${failures.joinToString("；")}"))
}

/** 批量阈值：≥20 走串联分块（用户拍板） */
private const val BATCH_THRESHOLD = 20

/** 单批上限：命令过长可能被 shell/系统截断 */
private const val BATCH_SIZE = 40
