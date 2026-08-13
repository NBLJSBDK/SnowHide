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
 * 职责：初始化引擎注册表 + 设置 Compose 内容 + 转发 Shizuku 授权回调。
 * 业务逻辑全部在 ViewModel/domain，本类保持极薄。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EngineRegistry.init()
        enableEdgeToEdge()
        setContent {
            SnowHideTheme {
                HomeScreen(modifier = Modifier.fillMaxSize())
            }
        }
    }

    /**
     * Shizuku 授权回调转发——授权结果交给 Shizuku 处理，
     * 然后刷新引擎管理器（授权成功 → 主引擎可用 → UI 自动解除引导卡）
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Shizuku.onRequestPermissionsResult(requestCode, grantResults)
        EngineManager.refresh()
    }
}
