package com.nbljsbdk.snowhide.app

import android.app.Application

/**
 * 进程级启动入口
 *
 * 只做轻量、幂等、非阻塞的进程初始化（SP 仓库 + 引擎注册表）。
 * 应用列表扫描、图标预热、Activity 窗口配置等重活不进 Application，
 * 由 [CompositionRoot.initActivity] 在 MainActivity 侧完成。
 */
class SnowHideApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        CompositionRoot.init(this)
    }
}
