package com.nbljsbdk.snowhide.domain.organize

import com.nbljsbdk.snowhide.core.model.AppTarget

/** 整理目录状态所需的纯数据，不携带 Android、Compose 或 Repository 类型。 */
data class OrganizeAppRef(
    val id: Long,
    val pkg: String,
    val userId: Int = AppTarget.PRIMARY_USER_ID,
) {
    val target: AppTarget?
        get() = AppTarget.create(pkg, userId).getOrNull()
}

enum class SelectionFocus {
    FOLDER,
    HOME_APP,
    FOLDER_APP,
}

sealed interface OrganizeState {
    /** 无选中。 */
    data object Empty : OrganizeState

    /** 选中主屏应用。 */
    data class HomeAppSelected(val app: OrganizeAppRef) : OrganizeState

    /** 选中文件夹，可同时保留一个主屏应用子选中项。 */
    data class FolderSelected(
        val folderId: Long,
        val folderNameInput: String,
        val subHomeApp: OrganizeAppRef? = null,
        val subFolderAppPkg: String? = null,
        val subFolderAppTarget: AppTarget? = null,
        val focus: SelectionFocus = SelectionFocus.FOLDER,
        /** 刚创建，用于 UI 自动聚焦并全选名称。 */
        val justCreated: Boolean = false,
    ) : OrganizeState
}
