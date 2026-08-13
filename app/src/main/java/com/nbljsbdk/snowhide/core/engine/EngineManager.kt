package com.nbljsbdk.snowhide.core.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 引擎管理器（稳定层）
 *
 * 职责：
 * 1. 注册表——引擎注册的唯一入口，UI/业务层永远不 import 具体引擎类
 * 2. 探测——遍历注册表检测可用引擎，按优先级选出主引擎
 * 3. 状态——暴露 StateFlow，授权状态变化时 UI 自动刷新（注册表驱动渲染）
 *
 * 优先级：root > do > shizuku（P0 只注册 shizuku，其余留空壳）
 */
object EngineManager {

    /** 引擎信息（供 UI 渲染引擎区块） */
    data class EngineInfo(
        val id: String,
        val displayName: String,
        val available: Boolean,
    )

    /** 引擎优先级：数值越大越优先 */
    private data class Entry(val engine: PowerEngine, val priority: Int)

    private val registry = mutableListOf<Entry>()

    private val _primaryEngine = MutableStateFlow<PowerEngine?>(null)
    /** 当前主引擎（可用引擎中优先级最高者；无可用引擎为 null） */
    val primaryEngine: StateFlow<PowerEngine?> = _primaryEngine.asStateFlow()

    private val _engineInfos = MutableStateFlow<List<EngineInfo>>(emptyList())
    /** 全部已注册引擎的可用性状态（设置页引擎区块的数据源） */
    val engineInfos: StateFlow<List<EngineInfo>> = _engineInfos.asStateFlow()

    /** 注册引擎（EngineRegistry 调用，P0 只注册 shizuku） */
    fun register(engine: PowerEngine, priority: Int) {
        registry.add(Entry(engine, priority))
        registry.sortByDescending { it.priority }
        refresh()
    }

    /** 重新探测所有引擎并更新主引擎 */
    fun refresh() {
        _engineInfos.value = registry.map { entry ->
            EngineInfo(entry.engine.id, entry.engine.displayName, entry.engine.isAvailable())
        }
        _primaryEngine.value = registry.firstOrNull { it.engine.isAvailable() }?.engine
    }

    /** 可用引擎列表（按优先级排序） */
    fun availableEngines(): List<PowerEngine> =
        registry.filter { it.engine.isAvailable() }.map { it.engine }

    /** 当前主引擎是否可用（UI 常用快捷判断） */
    fun isEngineReady(): Boolean = _primaryEngine.value != null
}
