package com.nbljsbdk.snowhide.core.engine.impl

import android.content.Context
import android.os.Binder
import android.os.Parcel

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
                android.util.Log.d("ShellCmd", "exec: $cmd")
                try {
                    val result = execSh(cmd)
                    android.util.Log.d("ShellCmd", "ok: ${result.take(80)}")
                    reply?.writeNoException()
                    reply?.writeString(result)
                } catch (e: Exception) {
                    android.util.Log.d("ShellCmd", "err: $e")
                    reply?.writeException(e)
                }
                true
            }
            else -> super.onTransact(code, data, reply, flags)
        }
    }

    /** shell 身份执行命令（本进程已是 shell 权限） */
    private fun execSh(cmd: String): String {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
        // 并发读 stdout/stderr 防止管道写满死锁
        val stdout = process.inputStream.bufferedReader().use { it.readText() }
        val stderr = process.errorStream.bufferedReader().use { it.readText() }
        val exit = process.waitFor()
        if (exit != 0) {
            throw IllegalStateException("exit=$exit：${stderr.ifBlank { stdout }}")
        }
        return stdout.trim()
    }

    companion object {
        const val TRANSACTION_EXEC = 1
        const val DESCRIPTOR = "com.nbljsbdk.snowhide.IShellService"
    }
}
