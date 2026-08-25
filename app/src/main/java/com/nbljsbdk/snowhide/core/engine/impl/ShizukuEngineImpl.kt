package com.nbljsbdk.snowhide.core.engine.impl

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.DeadObjectException
import android.os.IBinder
import android.os.Parcel
import com.nbljsbdk.snowhide.core.engine.BinderConnectionEvent
import com.nbljsbdk.snowhide.core.engine.BinderConnectionState
import com.nbljsbdk.snowhide.core.engine.PowerEngine
import com.nbljsbdk.snowhide.core.model.PackageName
import com.nbljsbdk.snowhide.core.operation.PmCommand
import com.nbljsbdk.snowhide.core.operation.PmOperation
import com.nbljsbdk.snowhide.core.engine.reduceBinderConnectionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
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

    override fun isAvailable(): Boolean = runCatching {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    override fun isBinderConnected(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    override suspend fun exec(cmd: String): Result<String> = runCatching {
        execViaUserService(cmd)
    }

    override suspend fun disableApp(pkg: String): Result<Unit> =
        PmCommand.build(PmOperation.DISABLE_USER, pkg).fold(
            onSuccess = { command -> exec(command).map { } },
            onFailure = { Result.failure(it) },
        )

    override suspend fun enableApp(pkg: String): Result<Unit> =
        PmCommand.build(PmOperation.ENABLE, pkg).fold(
            onSuccess = { command -> exec(command).map { } },
            onFailure = { Result.failure(it) },
        )

    override suspend fun isFrozen(pkg: String): Result<Boolean> =
        PackageName.parse(pkg).fold(
            onSuccess = {
                runCatching {
                    exec("pm list packages -d").getOrThrow()
                        .lineSequence()
                        .mapNotNull { line -> line.removePrefix("package:").trim() }
                        .any { it == pkg }
                }
            },
            onFailure = { Result.failure(it) },
        )

    override suspend fun listFrozenPackages(): Result<List<String>> = runCatching {
        exec("pm list packages -d").getOrThrow()
            .lineSequence()
            .mapNotNull { line -> line.removePrefix("package:").trim() }
            .filter { it.isNotEmpty() }
            .toList()
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
        withContext(Dispatchers.IO) {
            var binder = awaitBinder()
            try {
                transact(binder, cmd)
            } catch (e: DeadObjectException) {
                // binder 已死（server 重启等）：清缓存重建连接后重试一次
                invalidateBinder(binder)
                binder = awaitBinder()
                transact(binder, cmd)
            }
        }

    /** 常驻连接 binder（绑定失败/断开时清空） */
    @Volatile
    private var cachedBinder: IBinder? = null

    /** bind 状态只允许由这个锁保护，避免多个入口并发 bind 同一个 UserService。 */
    private val bindLock = Any()
    private var pendingBind: CompletableDeferred<IBinder>? = null
    private var nextBindGeneration = 0L
    private var activeBindGeneration = 0L
    @Volatile
    private var connectionState: BinderConnectionState = BinderConnectionState.Disconnected

    /** 获取（必要时建立）常驻连接 */
    private suspend fun awaitBinder(): IBinder {
        var readyBinder: IBinder? = null
        var deferredToAwait: CompletableDeferred<IBinder>? = null
        var bindGeneration = 0L

        synchronized(bindLock) {
            val current = cachedBinder
            if (current != null && current.isBinderAlive && current.pingBinder()) {
                readyBinder = current
            } else {
                cachedBinder = null
                val pending = pendingBind
                if (pending != null) {
                    deferredToAwait = pending
                    bindGeneration = activeBindGeneration
                } else {
                    val deferred = CompletableDeferred<IBinder>()
                    pendingBind = deferred
                    bindGeneration = ++nextBindGeneration
                    activeBindGeneration = bindGeneration
                    updateConnectionState(BinderConnectionEvent.Begin(bindGeneration))
                    deferredToAwait = deferred
                    val args = Shizuku.UserServiceArgs(
                        ComponentName(context, ShellCommandService::class.java),
                    )
                        .daemon(false)
                        .version(1)
                        .processNameSuffix("shellcmd")
                        .tag("snowhide-shell")
                    val connection = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                            onServiceConnected(bindGeneration, deferred, service)
                        }

                        override fun onServiceDisconnected(name: ComponentName?) {
                            onServiceDisconnected(bindGeneration, deferred)
                        }

                        override fun onBindingDied(name: ComponentName?) {
                            onServiceDisconnected(bindGeneration, deferred)
                        }

                        override fun onNullBinding(name: ComponentName?) {
                            onServiceConnected(bindGeneration, deferred, null)
                        }
                    }
                    try {
                        Shizuku.bindUserService(args, connection)
                    } catch (error: Throwable) {
                        pendingBind = null
                        activeBindGeneration = 0L
                        updateConnectionState(
                            BinderConnectionEvent.Failed(bindGeneration, error.message ?: "bind 失败")
                        )
                        deferred.completeExceptionally(error)
                    }
                }
            }
        }

        readyBinder?.let { return it }
        val deferred = deferredToAwait ?: error("Shizuku 连接状态丢失")
        return try {
            withTimeout(BINDER_BIND_TIMEOUT_MS) { deferred.await() }
        } catch (error: Throwable) {
            synchronized(bindLock) {
                if (pendingBind === deferred) {
                    pendingBind = null
                    if (bindGeneration == activeBindGeneration) activeBindGeneration = 0L
                }
                updateConnectionState(
                    if (error is kotlinx.coroutines.CancellationException) {
                        BinderConnectionEvent.Cancelled(bindGeneration)
                    } else {
                        BinderConnectionEvent.TimedOut(bindGeneration)
                    }
                )
            }
            deferred.completeExceptionally(error)
            throw error
        }
    }

    /** 连接成功回调：忽略已经超时或被新一代连接取代的旧回调。 */
    private fun onServiceConnected(
        generation: Long,
        deferred: CompletableDeferred<IBinder>,
        service: IBinder?,
    ) {
        synchronized(bindLock) {
            if (generation != activeBindGeneration || pendingBind !== deferred) return
            pendingBind = null
            if (service != null) cachedBinder = service
            updateConnectionState(
                if (service == null) {
                    BinderConnectionEvent.Failed(generation, "Shizuku 服务绑定失败")
                } else {
                    BinderConnectionEvent.Connected(generation)
                }
            )
        }
        if (service == null) {
            deferred.completeExceptionally(IllegalStateException("Shizuku 服务绑定失败"))
        } else {
            deferred.complete(service)
        }
    }

    /** 连接断开回调：清除缓存并唤醒正在等待的调用者，不允许永久挂起。 */
    private fun onServiceDisconnected(
        generation: Long,
        deferred: CompletableDeferred<IBinder>,
    ) {
        var pending: CompletableDeferred<IBinder>? = null
        synchronized(bindLock) {
            if (generation != activeBindGeneration) return
            cachedBinder = null
            activeBindGeneration = 0L
            if (pendingBind === deferred) {
                pending = pendingBind
                pendingBind = null
            }
            updateConnectionState(BinderConnectionEvent.Disconnected(generation))
        }
        pending?.completeExceptionally(IllegalStateException("Shizuku 服务已断开"))
    }

    private fun invalidateBinder(binder: IBinder) {
        synchronized(bindLock) {
            if (cachedBinder === binder) {
                cachedBinder = null
                val generation = (connectionState as? BinderConnectionState.Connected)?.generation
                if (generation != null) {
                    updateConnectionState(BinderConnectionEvent.Disconnected(generation))
                }
            }
        }
    }

    private fun updateConnectionState(event: BinderConnectionEvent) {
        connectionState = reduceBinderConnectionState(connectionState, event)
    }

    /** 手写 Binder 事务：写入命令 → 读取输出 */
    private suspend fun transact(binder: IBinder, cmd: String): String =
        suspendCancellableCoroutine { cont ->
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(ShellCommandService.DESCRIPTOR)
                data.writeString(cmd)
                if (!binder.transact(ShellCommandService.TRANSACTION_EXEC, data, reply, 0)) {
                    throw IllegalStateException("Shizuku Binder 未处理执行事务")
                }
                reply.readException()
                cont.resume(reply.readString() ?: "")
            } catch (e: Exception) {
                cont.resumeWithException(e)
            } finally {
                data.recycle()
                reply.recycle()
            }
        }

    private companion object {
        private const val BINDER_BIND_TIMEOUT_MS = 8_000L
    }
}
