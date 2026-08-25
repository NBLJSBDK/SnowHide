package com.nbljsbdk.snowhide.service

import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.nbljsbdk.snowhide.R
import com.nbljsbdk.snowhide.app.CompositionRoot
import com.nbljsbdk.snowhide.core.feedback.FeedbackDuration
import com.nbljsbdk.snowhide.core.feedback.FeedbackRegistry
import com.nbljsbdk.snowhide.core.feedback.HapticType
import kotlinx.coroutines.runBlocking

/**
 * 快速启停下拉磁贴（§3.9 用户拍板简化逻辑）
 *
 * - 点亮：解冻成员中「已添加且被冻结」的应用（QuickToggleUseCase 记录本批）
 * - 熄灭：冻回本批；**有锁定的应用跳过**，toast 提示交给用户
 *
 * 图标随状态切换 toggle-on / toggle-off。
 */
class QuickToggleTileService : TileService() {

    private val useCase by lazy {
        CompositionRoot.appContainer(applicationContext).quickToggleUseCase
    }

    override fun onClick() {
        super.onClick()
        val tile = qsTile ?: return
        Thread {
            if (tile.state == Tile.STATE_ACTIVE) {
                // 熄灭：冻回刚才打开的应用（有锁跳过）
                val result = runCatching { runBlocking { useCase.turnOff() } }.getOrNull()
                val resultData = result?.getOrNull()
                // 失败时 opened 快照仍保留，磁贴保持点亮以便用户重试。
                setTileActive(tile, resultData == null && useCase.opened.value.isNotEmpty())
                val msg = buildString {
                    append("快速启停已关闭")
                    resultData?.let {
                        append("：冻结 ${it.frozen} 个")
                        if (it.lockedSkipped.isNotEmpty()) {
                            append("，${it.lockedSkipped.size} 个已锁定未关闭，请手动处理")
                        }
                        if (it.failures.isNotEmpty()) append("，部分失败")
                    }
                }
                val failureMessage = result?.exceptionOrNull()?.message
                    ?: resultData?.failures?.takeIf { it.isNotEmpty() }?.joinToString("；")
                if (failureMessage != null) {
                    FeedbackRegistry.notifyFailure("快速启停", failureMessage)
                } else {
                    FeedbackRegistry.vibrate(HapticType.BATCH)
                    toast(msg)
                }
            } else {
                // 点亮：解冻成员中已添加且冻结的应用
                val result = runCatching { runBlocking { useCase.lightUp() } }
                val n = result.getOrNull()?.getOrNull()
                val msg = when {
                    result.isFailure -> "快速启停失败：${result.exceptionOrNull()?.message}"
                    n == null -> "快速启停失败"
                    else -> "快速启停：已解冻 $n 个应用"
                }
                setTileActive(tile, n != null)
                val failureMessage = result.exceptionOrNull()?.message
                    ?: result.getOrNull()?.exceptionOrNull()?.message
                if (failureMessage != null || n == null) {
                    FeedbackRegistry.notifyFailure(
                        "快速启停",
                        failureMessage ?: msg,
                    )
                } else {
                    FeedbackRegistry.vibrate(HapticType.BATCH)
                    toast(msg)
                }
            }
        }.start()
    }

    override fun onStartListening() {
        super.onStartListening()
        // 服务重启后按持久化的 opened 恢复磁贴点亮状态
        val tile = qsTile ?: return
        setTileActive(tile, useCase.opened.value.isNotEmpty())
    }

    private fun setTileActive(tile: Tile, active: Boolean) {
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.icon = Icon.createWithResource(
            this,
            if (active) R.drawable.ic_toggle_on else R.drawable.ic_toggle_off,
        )
        tile.updateTile()
    }

    private fun toast(msg: String) {
        Handler(Looper.getMainLooper()).post {
            FeedbackRegistry.toast(msg, FeedbackDuration.LONG)
        }
    }
}
