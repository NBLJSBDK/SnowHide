package com.nbljsbdk.snowhide.core.engine.impl

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Parcel
import com.nbljsbdk.snowhide.core.engine.PowerEngine
import kotlinx.coroutines.suspendCancellableCoroutine
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Shizuku 引擎（P0 唯一实现，隔离区）
 *
 * 实现方式：`Shizuku.bindUserService` 把 [ShellCommandService] 拉起到
 * shell 身份进程，通过 AIDL 执行 `pm` 命令——
 * 与黑白门 AppGate 4.3.8 的 Shizuku 通道同原理。
 *
 * 注：Shizuku 13.1.5 中 `Shizuku.newProcess` 为 **private**，
 * 不可用；UserService 是官方公开的进程执行通道。
 *
 * 重构约定：将来换集成方式（Binder 直调/TCP），只重写本文件。
 * 所有 Shizuku 相关 import 只允许出现在本文件。
 */
class ShizukuEngineImpl(private val context: Context) : PowerEngine {

    override val id: String = "shizuku"
    override val displayName: String = "Shizuku"

    override fun isAvailable(): Boolean {
        val binderOk = Shizuku.pingBinder()
        android.util.Log.d("SnowHideEngine", "pingBinder=$binderOk")
        if (!binderOk) return false
        val perm = Shizuku.checkSelfPermission()
        android.util.Log.d("SnowHideEngine", "checkSelfPermission=$perm (GRANTED=${PackageManager.PERMISSION_GRANTED})")
        return perm == PackageManager.PERMISSION_GRANTED
    }

    override fun isBinderConnected(): Boolean = Shizuku.pingBinder()

    override suspend fun exec(cmd: String): Result<String> = runCatching {
        execViaUserService(cmd)
    }

    override suspend fun disableApp(pkg: String): Result<Unit> =
        runPm("disable-user", "--user", "0", pkg)

    override suspend fun enableApp(pkg: String): Result<Unit> =
        runPm("enable", pkg)

    override suspend fun isFrozen(pkg: String): Result<Boolean> = runCatching {
        exec("pm list packages -d").getOrThrow()
            .lineSequence()
            .mapNotNull { line -> line.removePrefix("package:").trim() }
            .any { it == pkg }
    }

    override suspend fun listFrozenPackages(): Result<List<String>> = runCatching {
        exec("pm list packages -d").getOrThrow()
            .lineSequence()
            .mapNotNull { line -> line.removePrefix("package:").trim() }
            .filter { it.isNotEmpty() }
            .toList()
    }

    /** 执行 pm 命令并校验退出码 */
    private suspend fun runPm(vararg args: String): Result<Unit> {
        return exec(listOf("pm", *args).joinToString(" ")).map { }
    }

    /**
     * 通过 Shizuku UserService 执行命令（shell 身份进程）
     * 每次调用建立一次绑定，完成后解绑（保持轻量）。
     */
    private suspend fun execViaUserService(cmd: String): String =
        suspendCancellableCoroutine { cont ->
            val args = Shizuku.UserServiceArgs(
                ComponentName(context, ShellCommandService::class.java)
            )
                .daemon(false)
                .version(1)
                .processNameSuffix("shellcmd")
                .tag("snowhide-shell")

            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    if (service == null) {
                        if (cont.isActive) cont.resumeWithException(IllegalStateException("Shizuku 服务绑定失败"))
                        return
                    }
                    try {
                        // 手写 Binder 事务：写入命令 → 读取输出
                        val data = Parcel.obtain()
                        val reply = Parcel.obtain()
                        try {
                            data.writeInterfaceToken(ShellCommandService.DESCRIPTOR)
                            data.writeString(cmd)
                            service.transact(ShellCommandService.TRANSACTION_EXEC, data, reply, 0)
                            reply.readException()
                            val result = reply.readString() ?: ""
                            if (cont.isActive) cont.resume(result)
                        } finally {
                            data.recycle()
                            reply.recycle()
                        }
                    } catch (e: Exception) {
                        if (cont.isActive) cont.resumeWithException(e)
                    } finally {
                        runCatching { Shizuku.unbindUserService(args, this, false) }
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {}
            }

            Shizuku.bindUserService(args, connection)
        }
}
