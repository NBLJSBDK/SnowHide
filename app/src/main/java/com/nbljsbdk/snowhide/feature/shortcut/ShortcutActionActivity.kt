package com.nbljsbdk.snowhide.feature.shortcut

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
        val appContext = applicationContext
        val freezeUseCase = FreezeUseCase(FreezeExecutor(EngineManager), GridRepository, EngineManager)
        val quickToggle = QuickToggleUseCase(
            GridRepository,
            EngineManager,
            getSharedPreferences("snowhide_settings", Context.MODE_PRIVATE),
        )

        // ═══════════════════════════════════════
        // 执行动作（worker 线程）+ 进度通知轮询（并行）
        // ═══════════════════════════════════════
        // 主题为 Translucent：**保活窗口直到执行完成**——
        // ColorOS 后台管理激进，NoDisplay 立即 finish 会秒杀进程
        // （动作丢失+无反馈，真机实锤）；窗口在前台期间 Toast 可见。
        ShortcutNotifier.showProgress(this, "正在执行…")

        val worker = Thread {
            val result = when (action) {
                ACTION_SMART_CLEAN -> {
                    val r = runCatching { runBlocking { freezeUseCase.quickClean() } }
                    Triple(
                        "智能清理",
                        r.fold({ it.fold({ "已停用 $it 个应用" }, { null }) }, { null }),
                        r.fold({ it.fold({ null }, { "失败：${it.message}" }) }, { "失败：${it.message}" }),
                    )
                }
                ACTION_FREEZE_ALL -> {
                    val r = runCatching { runBlocking { freezeUseCase.freezeAll(null, exceptLocked = false) } }
                    Triple(
                        "全部停用",
                        r.fold({ it.fold({ "已停用 $it 个应用" }, { null }) }, { null }),
                        r.fold({ it.fold({ null }, { "失败：${it.message}" }) }, { "失败：${it.message}" }),
                    )
                }
                ACTION_TOGGLE_QUICK -> {
                    val r = runCatching { runBlocking { quickToggle.toggle() } }
                    Triple(
                        "快速启停",
                        r.fold({ it.fold({ "已完成（$it 个应用）" }, { null }) }, { null }),
                        r.fold({ it.fold({ null }, { "失败：${it.message}" }) }, { "失败：${it.message}" }),
                    )
                }
                ACTION_ENABLE_ALL -> {
                    val r = runCatching { runBlocking { freezeUseCase.unfreezeAll() } }
                    Triple(
                        "启用全部",
                        r.fold({ it.fold({ "已启用 $it 个应用" }, { null }) }, { null }),
                        r.fold({ it.fold({ null }, { "失败：${it.message}" }) }, { "失败：${it.message}" }),
                    )
                }
                else -> return@Thread
            }
            runCatching { runBlocking { FrozenStateStore.refresh() } }
            // Toast（窗口前台期间可见）+ 通知兜底（结果）
            val successMsg = result.second
            Handler(Looper.getMainLooper()).post {
                if (successMsg != null) {
                    Toast.makeText(appContext, successMsg, Toast.LENGTH_SHORT).show()
                    ShortcutNotifier.showResult(appContext, result.first, successMsg)
                } else {
                    val fail = result.third ?: "失败"
                    Toast.makeText(appContext, "$result.first$fail", Toast.LENGTH_LONG).show()
                    ShortcutNotifier.showResult(appContext, result.first, fail)
                }
                finish()
            }
        }
        worker.start()

        // 进度通知轮询：BatchProgress 变化 → 更新通知进度条
        Thread {
            while (worker.isAlive) {
                val p = com.nbljsbdk.snowhide.data.repo.BatchProgress.progress.value
                if (p != null) {
                    ShortcutNotifier.updateProgress(this, p)
                }
                Thread.sleep(200)
            }
        }.start()
    }

    companion object {
        const val ACTION_SMART_CLEAN = "com.nbljsbdk.snowhide.shortcut.SMART_CLEAN"
        const val ACTION_FREEZE_ALL = "com.nbljsbdk.snowhide.shortcut.FREEZE_ALL"
        const val ACTION_TOGGLE_QUICK = "com.nbljsbdk.snowhide.shortcut.TOGGLE_QUICK"
        // 第 4 位：临时「启用全部」（用户测试用，后续可替换）
        const val ACTION_ENABLE_ALL = "com.nbljsbdk.snowhide.shortcut.ENABLE_ALL"
    }
}

/** 快捷方式执行通知（Toast 尽力 + 通知兜底带进度条，ColorOS 吞后台 Toast 的可靠方案） */
object ShortcutNotifier {

    private const val CHANNEL_ID = "shortcut_result"
    private const val NOTIF_ID = 1001

    private fun nm(context: Context): NotificationManager {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "快捷方式执行", NotificationManager.IMPORTANCE_LOW)
        )
        return manager
    }

    /** 执行中：进度通知（indeterminate 转圈） */
    fun showProgress(context: Context, text: String) {
        runCatching {
            val notification = android.app.Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_snowflake)
                .setContentTitle("雪藏")
                .setContentText(text)
                .setProgress(0, 0, true) // 不确定进度
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .build()
            nm(context).notify(NOTIF_ID, notification)
        }
    }

    /** 批量进度更新（0f..1f → 通知进度条） */
    fun updateProgress(context: Context, progress: Float) {
        runCatching {
            val percent = (progress.coerceIn(0f, 1f) * 100).toInt()
            val notification = android.app.Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_snowflake)
                .setContentTitle("雪藏")
                .setContentText("批量执行中 $percent%")
                .setProgress(100, percent, false)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .build()
            nm(context).notify(NOTIF_ID, notification)
        }
    }

    /** 完成：结果通知（自动消失） */
    fun showResult(context: Context, title: String, message: String) {
        runCatching {
            val notification = android.app.Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_snowflake)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .build()
            nm(context).notify(NOTIF_ID, notification)
        }
    }
}
