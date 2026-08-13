package com.nbljsbdk.snowhide.core.engine.impl

import android.content.pm.PackageManager
import com.nbljsbdk.snowhide.core.engine.PowerEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * Shizuku 引擎（P0 唯一实现，隔离区）
 *
 * 实现方式：`Shizuku.newProcess()` 以 shell 身份执行 `pm` 命令——
 * 与黑白门 AppGate 4.3.8 的 Shizuku 通道同原理（shell 身份可直接
 * disable-user/enable）。
 *
 * 重构约定：将来换集成方式（直连 Binder / TCP），只重写本文件。
 * 所有 Shizuku 相关 import 只允许出现在本文件。
 */
class ShizukuEngineImpl : PowerEngine {

    override val id: String = "shizuku"
    override val displayName: String = "Shizuku"

    override fun isAvailable(): Boolean {
        if (!Shizuku.pingBinder()) return false
        return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }

    override suspend fun exec(cmd: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
            val output = process.inputStream.bufferedReader().readText()
            val exit = process.waitFor()
            if (exit != 0) {
                throw IllegalStateException("命令退出码 $exit：$output")
            }
            output.trim()
        }
    }

    override suspend fun disableApp(pkg: String): Result<Unit> =
        runPm("disable-user", "--user", "0", pkg)

    override suspend fun enableApp(pkg: String): Result<Unit> =
        runPm("enable", pkg)

    override suspend fun isFrozen(pkg: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val output = exec("pm list packages -d").getOrThrow()
            // pm list packages -d 输出形如 "package:com.xxx"，逐行比对包名
            output.lineSequence()
                .mapNotNull { line -> line.removePrefix("package:").trim() }
                .any { it == pkg }
        }
    }

    override suspend fun listFrozenPackages(): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            exec("pm list packages -d").getOrThrow()
                .lineSequence()
                .mapNotNull { line -> line.removePrefix("package:").trim() }
                .filter { it.isNotEmpty() }
                .toList()
        }
    }

    /** 执行 pm 命令并校验退出码（pm 失败时输出含错误信息） */
    private suspend fun runPm(vararg args: String): Result<Unit> {
        return exec(listOf("pm", *args).joinToString(" ")).map { }
    }
}
