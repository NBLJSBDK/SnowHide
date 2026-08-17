package com.nbljsbdk.snowhide

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.nbljsbdk.snowhide.R
import com.nbljsbdk.snowhide.core.engine.EngineManager
import com.nbljsbdk.snowhide.core.engine.registry.EngineRegistry
import com.nbljsbdk.snowhide.data.prefs.SettingsRepository
import com.nbljsbdk.snowhide.data.repo.AppListRepository
import com.nbljsbdk.snowhide.data.repo.GridRepository
import com.nbljsbdk.snowhide.feature.home.HomeScreen
import com.nbljsbdk.snowhide.ui.theme.SnowHideTheme
import rikka.shizuku.Shizuku

/**
 * 应用唯一入口
 *
 * 职责：初始化引擎注册表 + 设置 Compose 内容 + 监听 Shizuku 授权结果。
 * 业务逻辑全部在 ViewModel/domain，本类保持极薄。
 *
 * Shizuku 13 授权机制（注意）：授权结果走
 * `OnRequestPermissionResultListener` 回调（listener 模式），
 * **不是** onRequestPermissionsResult 转发（那是旧 API 的做法）。
 */
class MainActivity : ComponentActivity() {

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_SHIZUKU) {
            // 授权结果到达（grantResult 为 PackageManager.PERMISSION_*），刷新引擎状态
            EngineManager.refresh()
        }
    }

    /** Shizuku binder 到达时刷新引擎状态（服务连接是异步的） */
    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        EngineManager.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EngineRegistry.init(applicationContext)
        GridRepository.init(applicationContext)
        com.nbljsbdk.snowhide.data.repo.FrozenStateStore.init(applicationContext)
        SettingsRepository.init(applicationContext)
        AppListRepository.init(applicationContext) // 预加载应用列表（避免首次打开增删界面空白）
        com.nbljsbdk.snowhide.ui.util.AppIconLoader.init(applicationContext) // 图标加载器单例
        registerDynamicShortcuts()
        requestNotificationPermissionIfNeeded()
        enableEdgeToEdge()
        setContent {
            SnowHideTheme {
                HomeScreen(
                    modifier = Modifier.fillMaxSize(),
                    onRequestShizuku = {
                        // 请求前检查 binder 是否已连接（未连接时 requestPermission 会抛
                        // "binder haven't been received"）
                        if (Shizuku.pingBinder()) {
                            Shizuku.requestPermission(REQUEST_SHIZUKU)
                        } else {
                            // Shizuku 未运行：引导用户打开 Shizuku
                            // 注意：Shizuku 应用包名是 moe.shizuku.privileged.api
                            //（moe.shizuku.manager 只是权限命名空间，不是可启动包名）
                            runCatching {
                                val intent = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                if (intent != null) startActivity(intent)
                            }
                        }
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
    }

    override fun onPause() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        super.onPause()
    }

    // ═══════════════════════════════════════
    // App Shortcuts（设计文档 §3.12）
    // ═══════════════════════════════════════

    /** 注册 3 个动态快捷方式（第 4 位留空备用——系统上限 5） */
    private fun registerDynamicShortcuts() {
        runCatching {
            val scm = getSystemService(android.content.pm.ShortcutManager::class.java)
            val shortcuts = listOf(
                android.content.pm.ShortcutInfo.Builder(this, "smart_clean")
                    .setShortLabel("智能清理")
                    .setIcon(android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_sc_smart_clean))
                    .setIntent(
                        android.content.Intent(
                            this,
                            com.nbljsbdk.snowhide.feature.shortcut.ShortcutActionActivity::class.java,
                        ).setAction(com.nbljsbdk.snowhide.feature.shortcut.ShortcutActionActivity.ACTION_SMART_CLEAN)
                    )
                    .build(),
                android.content.pm.ShortcutInfo.Builder(this, "freeze_all")
                    .setShortLabel("全部停用")
                    .setIcon(android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_sc_freeze_all))
                    .setIntent(
                        android.content.Intent(
                            this,
                            com.nbljsbdk.snowhide.feature.shortcut.ShortcutActionActivity::class.java,
                        ).setAction(com.nbljsbdk.snowhide.feature.shortcut.ShortcutActionActivity.ACTION_FREEZE_ALL)
                    )
                    .build(),
                android.content.pm.ShortcutInfo.Builder(this, "toggle_quick")
                    .setShortLabel("快速启停")
                    .setIcon(android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_sc_toggle))
                    .setIntent(
                        android.content.Intent(
                            this,
                            com.nbljsbdk.snowhide.feature.shortcut.ShortcutActionActivity::class.java,
                        ).setAction(com.nbljsbdk.snowhide.feature.shortcut.ShortcutActionActivity.ACTION_TOGGLE_QUICK)
                    )
                    .build(),
                // 第 4 位：临时「启用全部」（用户测试用，后续可替换）
                android.content.pm.ShortcutInfo.Builder(this, "enable_all")
                    .setShortLabel("启用全部")
                    .setIcon(android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_sc_enable_all))
                    .setIntent(
                        android.content.Intent(
                            this,
                            com.nbljsbdk.snowhide.feature.shortcut.ShortcutActionActivity::class.java,
                        ).setAction(com.nbljsbdk.snowhide.feature.shortcut.ShortcutActionActivity.ACTION_ENABLE_ALL)
                    )
                    .build(),
            )
            scm.setDynamicShortcuts(shortcuts)
        }
    }

    /** Android 13+ 通知权限（快捷方式结果通知用，一次性弹窗） */
    private fun requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_NOTIFICATION,
                )
            }
        }
    }

    companion object {
        private const val REQUEST_SHIZUKU = 1000
        private const val REQUEST_NOTIFICATION = 1001
    }
}
