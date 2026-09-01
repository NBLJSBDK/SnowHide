package com.nbljsbdk.snowhide.data.model

import com.nbljsbdk.snowhide.core.model.AppTarget

/**
 * 主屏幕宫格项——应用与文件夹混排（设计文档 §3.2）
 *
 * 顺序即用户摆放顺序（整理目录左右键维护），
 * 也是循环滑动的页面序列依据（文件夹按主屏混排中的出现顺序排列）。
 */
data class GridItem(
    val id: Long,            // 自增
    val type: String,        // "app" | "folder"
    val pkg: String? = null, // type=app 时
    val folderId: Long? = null, // type=folder 时
    val sortOrder: Int,      // 主屏混排顺序
    val frozenMode: String = "FREEZE", // 预留扩展点，P0 恒 FREEZE
    val locked: Boolean = false, // 底部图标栏锁定（豁免快速清理/息屏清理）
    /** 应用所属用户空间；旧 JSON 缺失时默认 user 0。文件夹项固定为 0。 */
    val userId: Int = 0,
) {
    val appTarget: AppTarget?
        get() = if (type == "app" && pkg != null) {
            AppTarget.create(pkg, userId).getOrNull()
        } else null
}

/**
 * 文件夹（二级封顶，不嵌套，设计文档 §3.3）
 */
data class Folder(
    val id: Long,
    val name: String,
    val sortOrder: Int,
)

/**
 * 文件夹内应用（按 sortOrder 排序；删除文件夹时按此序续补主屏后）
 */
data class FolderApp(
    val folderId: Long,
    val pkg: String,
    val sortOrder: Int,
    /** 应用所属用户空间；旧 JSON 缺失时默认 user 0。 */
    val userId: Int = 0,
) {
    val appTarget: AppTarget?
        get() = AppTarget.create(pkg, userId).getOrNull()
}
