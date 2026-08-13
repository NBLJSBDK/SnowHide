package com.nbljsbdk.snowhide.core.engine.registry

import android.content.Context
import com.nbljsbdk.snowhide.core.engine.EngineManager
import com.nbljsbdk.snowhide.core.engine.impl.DoEngineImpl
import com.nbljsbdk.snowhide.core.engine.impl.RootEngineImpl
import com.nbljsbdk.snowhide.core.engine.impl.ShizukuEngineImpl

/**
 * 引擎注册表——全工程唯一知道「存在哪些引擎」的地方。
 *
 * 新增引擎 = 在这里加一行 register；
 * 重构引擎 = 改 impl 包内的类，本文件与外部全部无感。
 */
object EngineRegistry {

    fun init(context: Context) {
        EngineManager.register(RootEngineImpl(), priority = 30)             // root，P3（能力最全，最优先）
        EngineManager.register(DoEngineImpl(), priority = 20)               // Device Owner，P2
        EngineManager.register(ShizukuEngineImpl(context.applicationContext), priority = 10) // shell，P0 默认
    }
}
