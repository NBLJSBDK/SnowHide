package com.nbljsbdk.snowhide.feature.shortcut

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.nbljsbdk.snowhide.R
import com.nbljsbdk.snowhide.core.engine.EngineManager
import com.nbljsbdk.snowhide.core.engine.registry.EngineRegistry
import com.nbljsbdk.snowhide.core.mode.FreezeExecutor
import com.nbljsbdk.snowhide.data.prefs.SettingsRepository
import com.nbljsbdk.snowhide.data.repo.FrozenStateStore
import com.nbljsbdk.snowhide.data.repo.GridRepository
import com.nbljsbdk.snowhide.domain.FreezeUseCase
import com.nbljsbdk.snowhide.domain.QuickToggleUseCase
import kotlinx.coroutines.runBlocking

/**
 * App Shortcuts 透明入口 Activity（设计文档 §3.12，MediaSync 方案）
 *
 * 长按桌面图标弹出的快捷方式点击后进入本页：执行对应动作 →
 * 刷新共享冻结状态 → 系统通知反馈 → 立即退出。
 *
 * ⚠️ 冷启动路径：快捷方式启动**不经过 MainActivity**，
 * 必须自行初始化全部单例仓库。
 */
class ShortcutActionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 冷启动初始化（与 MainActivity 同批）
        EngineRegistry.init(applicationContext)
        GridRepository.init(applicationContext)
        FrozenStateStore.init(applicationContext)
        SettingsRepository.init(applicationContext)

        val action = intent.action
        val freezeUseCase = FreezeUseCase(FreezeExecutor(EngineManager), GridRepository, EngineManager)
        val quickToggle = QuickToggleUseCase(
            FreezeExecutor(EngineManager),
            GridRepository,
            EngineManager,
            getSharedPreferences("snowhide_settings", Context.MODE_PRIVATE),
        )

        Thread {
            // 执行结果：first=标题，second=成功文案（null=失败，third 为失败消息）
            val result = when (action) {
                ACTION_SMART_CLEAN -> {
                    val r = runCatching { runBlocking { freezeUseCase.quickClean() } }
                    Triple("智能清理",
                        r.fold({ it.fold({ "已停用 $it 个应用" }, { null }) }, { null }),
                        r.fold({ it.fold({ null }, { "失败：${it.message}" }) }, { "失败：${it.message}" }),
                    )
                }
                ACTION_FREEZE_ALL -> {
                    val r = runCatching { runBlocking { freezeUseCase.freezeAll(null, exceptLocked = false) } }
                    Triple("全部停用",
                        r.fold({ it.fold({ "已停用 $it 个应用" }, { null }) }, { null }),
                        r.fold({ it.fold({ null }, { "失败：${it.message}" }) }, { "失败：${it.message}" }),
                    )
                }
                ACTION_TOGGLE_QUICK -> {
                    val r = runCatching { runBlocking { quickToggle.toggle() } }
                    Triple("快速启停",
                        r.fold({ it.fold({ "已完成（$it 个应用）" }, { null }) }, { null }),
                        r.fold({ it.fold({ null }, { "失败：${it.message}" }) }, { "失败：${it.message}" }),
                    )
                }
                else -> {
                    finish()
                    return@Thread
                }
            }
            runCatching { runBlocking { FrozenStateStore.refresh() } }
            // 成功 → toast（用户拍板）；失败 → 系统通知（确保可见）
            val successMsg = result.second
            runOnUiThread {
                if (successMsg != null) {
                    Toast.makeText(this, successMsg, Toast.LENGTH_SHORT).show()
                } else {
                    ShortcutNotifier.notify(this, result.first, result.third ?: "失败")
                }
                finish()
            }
        }.start()
    }

    companion object {
        const val ACTION_SMART_CLEAN = "com.nbljsbdk.snowhide.shortcut.SMART_CLEAN"
        const val ACTION_FREEZE_ALL = "com.nbljsbdk.snowhide.shortcut.FREEZE_ALL"
        const val ACTION_TOGGLE_QUICK = "com.nbljsbdk.snowhide.shortcut.TOGGLE_QUICK"
        // 第 4 个快捷方式位备用（系统动态上限 5，Android 11+ 桌面显示 4）：
        // const val ACTION_RESERVED = "com.nbljsbdk.snowhide.shortcut.RESERVED"
    }
}

/** 快捷方式执行结果通知（ColorOS 吞后台 Toast，通知兜底——AGENTS 平台经验） */
object ShortcutNotifier {

    private const val CHANNEL_ID = "shortcut_result"

    fun notify(context: Context, title: String, message: String) {
        runCatching {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "快捷方式执行结果", NotificationManager.IMPORTANCE_DEFAULT)
            )
            val notification = android.app.Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_snowflake)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .build()
            nm.notify(title.hashCode(), notification)
        }
    }
}
