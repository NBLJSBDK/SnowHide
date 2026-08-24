package com.nbljsbdk.snowhide.core.engine.impl

import android.content.Context
import android.os.Binder
import android.os.Parcel
import java.io.InputStream
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Shizuku UserService——命令执行服务（shell 身份进程）
 *
 * 官方协议（Shizuku-API README「UserService」节，13.1.5 实测核对）：
 * **服务类必须实现 IBinder 接口**（不是普通 Service！），
 * 通常直接继承 AIDL Stub。本工程手写 Binder（不引 AIDL 编译），
 * 故直接继承 [Binder]。
 *
 * Shizuku.bindUserService 会把本类实例拉起到 Shizuku server 的
 * 特权进程（root/shell 身份），因此这里的 Runtime.exec 天然拥有
 * shell 权限——`pm disable-user/enable` 可直接执行。
 *
 * 构造器：Shizuku v13 优先用 Context 构造器，旧版用默认构造器，
 * 两个都保留（官方文档明确）。
 *
 * 事务码 [TRANSACTION_EXEC]：入参 String(cmd) → 出参 String(输出)。
 */
class ShellCommandService : Binder {

    constructor() : super()

    @Suppress("unused") constructor(context: Context?) : super()

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        return when (code) {
            TRANSACTION_EXEC -> {
                // 客户端 writeInterfaceToken 写入的 token 必须先读掉，
                // 否则 readString 读到的是 token 而非命令（真实踩坑）
                data.enforceInterface(DESCRIPTOR)
                val cmd = data.readString() ?: ""
                try {
                    val result = execSh(cmd)
                    reply?.writeNoException()
                    reply?.writeString(result)
                } catch (e: Exception) {
                    reply?.writeException(e)
                }
                true
            }
            else -> super.onTransact(code, data, reply, flags)
        }
    }

    /** shell 身份执行命令（本进程已是 shell 权限） */
    private fun execSh(cmd: String): String {
        check(isAllowedCommand(cmd)) { "命令不在 Shizuku P0 允许列表中" }
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
        val stdoutTask = streamExecutor.submit(Callable { readLimited(process.inputStream) })
        val stderrTask = streamExecutor.submit(Callable { readLimited(process.errorStream) })
        try {
            if (!process.waitFor(PROCESS_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                destroyProcess(process)
                throw IllegalStateException("命令执行超时（${PROCESS_TIMEOUT_MS}ms）")
            }
            val stdout = readTask(stdoutTask)
            val stderr = readTask(stderrTask)
            val exit = process.exitValue()
            if (exit != 0) {
                throw IllegalStateException("exit=$exit：${stderr.ifBlank { stdout }}")
            }
            return stdout.trim()
        } finally {
            stdoutTask.cancel(true)
            stderrTask.cancel(true)
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
            runCatching { process.outputStream.close() }
            if (process.isAlive) destroyProcess(process)
        }
    }

    /** 只允许当前 P0 已验证的固定查询和受控 PM 命令。 */
    private fun isAllowedCommand(command: String): Boolean {
        if (command == "pm list packages -d" || command == "dumpsys activity recents") return true
        return command.matches(pmPackageCommand) || command.matches(pmUserPackageCommand)
    }

    private fun readTask(task: Future<String>): String =
        task.get(STREAM_DRAIN_TIMEOUT_MS, TimeUnit.MILLISECONDS)

    /** 并发读取管道，避免 stdout/stderr 任一缓冲区写满后让子进程死锁。 */
    private fun readLimited(stream: InputStream): String {
        stream.bufferedReader().use { reader ->
            val buffer = CharArray(STREAM_BUFFER_SIZE)
            val result = StringBuilder()
            var truncated = false
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                if (result.length < MAX_OUTPUT_CHARS) {
                    val remaining = MAX_OUTPUT_CHARS - result.length
                    result.append(buffer, 0, minOf(count, remaining))
                    if (count > remaining) truncated = true
                } else {
                    truncated = true
                }
            }
            if (truncated) result.append("\n[输出已截断]")
            return result.toString()
        }
    }

    private fun destroyProcess(process: Process) {
        runCatching { process.destroy() }
        runCatching {
            if (!process.waitFor(PROCESS_DESTROY_GRACE_MS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
            }
        }
    }

    companion object {
        const val TRANSACTION_EXEC = 1
        const val DESCRIPTOR = "com.nbljsbdk.snowhide.IShellService"

        private const val PROCESS_TIMEOUT_MS = 30_000L
        private const val PROCESS_DESTROY_GRACE_MS = 500L
        private const val STREAM_DRAIN_TIMEOUT_MS = 2_000L
        private const val STREAM_BUFFER_SIZE = 4 * 1024
        private const val MAX_OUTPUT_CHARS = 64 * 1024
        private const val PACKAGE_NAME_PATTERN =
            "[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)*"
        private val pmPackageCommand =
            Regex("^pm (enable|disable) ($PACKAGE_NAME_PATTERN)\\z")
        private val pmUserPackageCommand =
            Regex("^pm (disable-user|uninstall) --user ([0-9]+) ($PACKAGE_NAME_PATTERN)\\z")

        private val streamExecutor = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "snowhide-shell-stream").apply { isDaemon = true }
        }
    }
}
