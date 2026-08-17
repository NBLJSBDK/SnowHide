package com.nbljsbdk.snowhide.core.engine.impl

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Parcel
import com.nbljsbdk.snowhide.core.engine.PowerEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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
     *
     * 连接策略：**常驻单连接**——首次 bind 后复用同一 IBinder。
     * 不能在每次 exec 时 bind/unbind：Shizuku 库的连接集合在多线程
     * 并发 add/remove 时会抛 ConcurrentModificationException
     * （真机「启用全部」循环连续 exec 时崩溃实锤，13.1.5 库内部问题）。
     *
     * ⚠️ 线程：`binder.transact` 是**同步** Binder 调用（sh 进程跑完
     * 才返回，批量 40 个 pm 命令需数秒）——必须在 IO 线程执行，
     * 否则主线程阻塞 5s+ 触发 ANR（真机实锤：批量操作卡死）。
     */
    private suspend fun execViaUserService(cmd: String): String =
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            var binder = awaitBinder()
            try {
                transact(binder, cmd)
            } catch (e: android.os.DeadObjectException) {
                // binder 已死（server 重启等）：清缓存重建连接后重试一次
                cachedBinder = null
                binder = awaitBinder()
                transact(binder, cmd)
            }
        }

    /** 常驻连接 binder（绑定失败/断开时清空） */
    @Volatile private var cachedBinder: IBinder? = null
    private var pendingBind: CompletableDeferred<IBinder>? = null

    /** 获取（必要时建立）常驻连接 */
    private suspend fun awaitBinder(): IBinder {
        cachedBinder?.let { return it }
        pendingBind?.let { return it.await() }
        val deferred = CompletableDeferred<IBinder>()
        pendingBind = deferred
        val args = Shizuku.UserServiceArgs(
            ComponentName(context, ShellCommandService::class.java)
        )
            .daemon(false)
            .version(1)
            .processNameSuffix("shellcmd")
            .tag("snowhide-shell")
        Shizuku.bindUserService(args, object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                if (service == null) {
                    pendingBind = null
                    deferred.completeExceptionally(IllegalStateException("Shizuku 服务绑定失败"))
                    return
                }
                cachedBinder = service
                pendingBind = null
                deferred.complete(service)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                cachedBinder = null
            }
        })
        return deferred.await()
    }

    /** 手写 Binder 事务：写入命令 → 读取输出 */
    private suspend fun transact(binder: IBinder, cmd: String): String =
        suspendCancellableCoroutine { cont ->
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(ShellCommandService.DESCRIPTOR)
                data.writeString(cmd)
                binder.transact(ShellCommandService.TRANSACTION_EXEC, data, reply, 0)
                reply.readException()
                cont.resume(reply.readString() ?: "")
            } catch (e: Exception) {
                cont.resumeWithException(e)
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
}
