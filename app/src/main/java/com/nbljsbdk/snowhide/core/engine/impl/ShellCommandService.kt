package com.nbljsbdk.snowhide.core.engine.impl

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel

/**
 * Shizuku UserService——被 Shizuku 拉起到 **shell 身份进程** 的命令执行服务
 *
 * 原理：Shizuku.bindUserService 会把本 Service 启动在 Shizuku server 的
 * 特权进程里，因此这里的 `Runtime.exec` 天然拥有 shell 权限——
 * `pm disable-user/enable` 可直接执行（黑白门 AppGate 同款通道）。
 *
 * 通信：手写 Binder（不依赖 AIDL 编译，AGP 9 下更稳）。
 * 事务码 [TRANSACTION_EXEC]：入参 String(cmd) → 出参 String(输出)。
 */
class ShellCommandService : Service() {

    override fun onBind(intent: Intent?): IBinder = binder

    private val binder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            return when (code) {
                TRANSACTION_EXEC -> {
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
    }

    /** shell 身份执行命令（本进程已是 shell 权限） */
    private fun execSh(cmd: String): String {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
        val output = process.inputStream.bufferedReader().readText()
        val exit = process.waitFor()
        if (exit != 0) {
            throw IllegalStateException("exit=$exit：$output")
        }
        return output.trim()
    }

    companion object {
        const val TRANSACTION_EXEC = 1
        const val DESCRIPTOR = "com.nbljsbdk.snowhide.IShellService"
    }
}
