package com.nbljsbdk.snowhide

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.nbljsbdk.snowhide.core.engine.EngineManager
import com.nbljsbdk.snowhide.core.engine.registry.EngineRegistry
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EngineRegistry.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            SnowHideTheme {
                HomeScreen(
                    modifier = Modifier.fillMaxSize(),
                    onRequestShizuku = { Shizuku.requestPermission(REQUEST_SHIZUKU) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Shizuku.addRequestPermissionResultListener(permissionListener)
    }

    override fun onPause() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        super.onPause()
    }

    companion object {
        private const val REQUEST_SHIZUKU = 1000
    }
}
