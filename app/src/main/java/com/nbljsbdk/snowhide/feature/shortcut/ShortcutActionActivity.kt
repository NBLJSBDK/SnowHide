package com.nbljsbdk.snowhide.feature.shortcut

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nbljsbdk.snowhide.app.CompositionRoot
import com.nbljsbdk.snowhide.core.feedback.FeedbackRegistry
import com.nbljsbdk.snowhide.core.feedback.HapticType
import com.nbljsbdk.snowhide.data.repo.BatchProgress
import com.nbljsbdk.snowhide.data.repo.FrozenStateStore
import kotlinx.coroutines.runBlocking

/**
 * App Shortcuts 入口 Activity（设计文档 §3.12）
 *
 * 用户拍板终版：执行期间弹**长条冒泡弹窗**（进度条平滑 +1），
 * 逐个 exec 每个应用；完成 → Toast 结果 → 立即退出。
 * Translucent 保活窗口（NoDisplay 立即 finish 会被 ColorOS 秒杀进程）。
 */
class ShortcutActionActivity : androidx.activity.ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CompositionRoot.init(applicationContext)

        val action = intent.action
        val appContext = applicationContext
        val container = CompositionRoot.appContainer(appContext)
        val freezeUseCase = container.freezeUseCase
        val quickToggle = container.quickToggleUseCase

        // 长条冒泡弹窗：标题 + 进度条（逐个平滑 +1）
        setContent {
            com.nbljsbdk.snowhide.ui.theme.SnowHideTheme {
            val progress by BatchProgress.progress.collectAsState()
            val label by BatchProgress.label.collectAsState()
            Dialog(
                onDismissRequest = {},
                properties = androidx.compose.ui.window.DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                    usePlatformDefaultWidth = false,
                ),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(320.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    Text(
                        text = when (action) {
                            ACTION_SMART_CLEAN -> "智能清理"
                            ACTION_FREEZE_ALL -> "全部停用"
                            ACTION_TOGGLE_QUICK -> "快速启停"
                            ACTION_ENABLE_ALL -> "启用全部"
                            else -> "执行中"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    if (progress != null) {
                        Text(
                            text = label?.let { "正在$it" } ?: "执行中…",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { progress ?: 0f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                        )
                    } else {
                        Text("完成", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            }
        }

        // 逐个执行 + 完成后 Toast 结果并退出
        Thread {
            val result = when (action) {
                ACTION_SMART_CLEAN -> {
                    val r = runCatching { runBlocking { freezeUseCase.quickCleanTargets() } }
                    Triple(
                        "智能清理",
                        r.fold({ it.fold({ "已停用 ${it.size} 个应用" }, { null }) }, { null }),
                        r.fold({ it.fold({ null }, { "失败：${it.message}" }) }, { "失败：${it.message}" }),
                    )
                }
                ACTION_FREEZE_ALL -> {
                    val r = runCatching { runBlocking { freezeUseCase.freezeAllTargets(null, exceptLocked = false) } }
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
                    val r = runCatching { runBlocking { freezeUseCase.unfreezeAllTargets() } }
                    Triple(
                        "启用全部",
                        r.fold({ it.fold({ "已启用 $it 个应用" }, { null }) }, { null }),
                        r.fold({ it.fold({ null }, { "失败：${it.message}" }) }, { "失败：${it.message}" }),
                    )
                }
                else -> {
                    finish()
                    return@Thread
                }
            }
            runCatching { runBlocking { FrozenStateStore.refresh() } }
            // Toast 在窗口存活期间发出（前台可见，ColorOS 不吞）
            val successMsg = result.second
            runOnUiThread {
                if (successMsg != null) {
                    FeedbackRegistry.vibrate(HapticType.BATCH)
                    FeedbackRegistry.toast(successMsg)
                } else {
                    FeedbackRegistry.notifyFailure(
                        result.first,
                        result.third ?: "失败",
                    )
                }
                finish()
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
