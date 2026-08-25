package com.nbljsbdk.snowhide.core.engine

import kotlinx.coroutines.flow.StateFlow

/**
 * 当前主引擎的最小读取端口。
 *
 * 业务层只需要这个端口，不需要依赖具体的注册表实现；测试可以注入
 * 内存中的假引擎而不触碰 Shizuku。
 */
interface EngineProvider {
    val primaryEngine: StateFlow<PowerEngine?>
}
