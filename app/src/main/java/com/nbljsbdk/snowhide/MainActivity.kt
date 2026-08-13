package com.nbljsbdk.snowhide

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.nbljsbdk.snowhide.core.engine.EngineManager
import com.nbljsbdk.snowhide.core.engine.registry.EngineRegistry
import com.nbljsbdk.snowhide.data.prefs.SettingsRepository
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
        SettingsRepository.init(applicationContext)
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

    companion object {
        private const val REQUEST_SHIZUKU = 1000
    }
}
