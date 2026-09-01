package com.nbljsbdk.snowhide.core.model

import kotlinx.coroutines.flow.StateFlow

/** 增删应用中分身模式选择的用户空间持久化端口。 */
interface AppCloneSelectionStore {
    val selectedUserId: StateFlow<Int?>

    fun setSelectedUserId(userId: Int)
}
