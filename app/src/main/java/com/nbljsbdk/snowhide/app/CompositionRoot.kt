package com.nbljsbdk.snowhide.app

import android.content.Context
import com.nbljsbdk.snowhide.core.engine.registry.EngineRegistry
import com.nbljsbdk.snowhide.data.prefs.SettingsRepository
import com.nbljsbdk.snowhide.data.repo.AppListRepository
import com.nbljsbdk.snowhide.data.repo.FrozenStateStore
import com.nbljsbdk.snowhide.data.repo.GridRepository
import com.nbljsbdk.snowhide.data.repo.ListOrderRepository
import com.nbljsbdk.snowhide.data.repo.RecentCalibrationRepository
import com.nbljsbdk.snowhide.data.repo.RecentFreezeQueueRepository
import com.nbljsbdk.snowhide.data.repo.QuickToggleRepository
import com.nbljsbdk.snowhide.ui.util.AppIconLoader

/**
 * 组合根——全工程唯一依赖装配点（轻量手写 DI，不引入框架）
 *
 * 职责：
 * 1. 幂等初始化全部仓库与引擎注册表（任意 Android 入口可安全调用）
 * 2. 创建并持有 [AppContainer]（UseCase 唯一构造点）
 *
 * - [init]：SP 仓库 + 引擎注册表（Activity/Service/Receiver/Tile 冷启动均可用）
 * - [initActivity]：额外初始化应用列表预加载与图标预热（仅主界面需要）
 *
 * 线程安全：[@Synchronized] 保证多入口并发冷启动只初始化一次。
 */
object CompositionRoot {

    @Volatile
    private var initialized = false

    @Volatile
    private var container: AppContainer? = null

    /** 初始化数据层与引擎（幂等、线程安全） */
    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        val app = context.applicationContext
        EngineRegistry.init(app)
        GridRepository.init(app)
        ListOrderRepository.init(app)
        FrozenStateStore.init(app)
        SettingsRepository.init(app)
        RecentCalibrationRepository.init(app)
        RecentFreezeQueueRepository.init(app)
        QuickToggleRepository.init(app)
        container = AppContainer(app)
        initialized = true
    }

    /** 主界面级初始化：数据层 + 应用列表预加载 + 图标预热（可重复调用） */
    fun initActivity(context: Context) {
        init(context)
        val app = context.applicationContext
        AppListRepository.init(app)
        AppIconLoader.init(app)
    }

    /** 获取进程级依赖容器（未初始化时自动初始化） */
    fun appContainer(context: Context): AppContainer {
        init(context)
        return container ?: error("CompositionRoot 初始化失败")
    }
}
