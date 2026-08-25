package com.nbljsbdk.snowhide.core.model

import kotlinx.coroutines.flow.StateFlow

/**
 * 快速启停业务所需的成员和点亮快照端口。
 */
interface QuickToggleStore {
    val members: StateFlow<List<String>>
    val opened: StateFlow<List<String>>

    fun setOpened(packages: Collection<String>)

    fun clearOpened()
}
