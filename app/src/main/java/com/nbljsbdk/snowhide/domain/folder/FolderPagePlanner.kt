package com.nbljsbdk.snowhide.domain.folder

/** Planner 输入：只保留页面顺序所需的文件夹事实。 */
data class FolderPageInput(
    val id: Long,
    val sortOrder: Int,
)

sealed interface FolderPage {
    data object Home : FolderPage
    data class Folder(val id: Long) : FolderPage
}

/** 已排序且过滤后的页面计划，不持有 PagerState。 */
data class FolderPagePlan(
    val pages: List<FolderPage>,
    val loopEnabled: Boolean,
) {
    val pageCount: Int get() = pages.size
    val folderIds: List<Long> get() = pages.drop(1).map { (it as FolderPage.Folder).id }

    /** 将虚拟 Pager 页映射为页面序列中的逻辑页。 */
    fun logicalIndex(virtualPage: Int): Int {
        if (pages.isEmpty()) return 0
        return if (loopEnabled) {
            Math.floorMod(virtualPage, pages.size)
        } else {
            virtualPage.coerceIn(0, pages.lastIndex)
        }
    }

    /** 当前虚拟循环段的主屏页，用于瞬时回主屏。 */
    fun homeBase(virtualPage: Int): Int =
        if (loopEnabled && pages.isNotEmpty()) {
            (virtualPage / pages.size) * pages.size
        } else {
            0
        }

    /** 在当前循环段内跳到指定文件夹；找不到时返回主屏。 */
    fun targetVirtualPage(virtualPage: Int, folderId: Long): Int {
        val logical = pages.indexOf(FolderPage.Folder(folderId))
        if (logical < 0) return homeBase(virtualPage)
        return homeBase(virtualPage) + logical
    }

    /** 纯边界行为：循环时首尾相接，否则到边界停止。 */
    fun move(index: Int, step: Int): Int {
        if (pages.isEmpty()) return 0
        val next = index + step
        return if (loopEnabled) {
            Math.floorMod(next, pages.size)
        } else {
            next.coerceIn(0, pages.lastIndex)
        }
    }
}

/** 文件夹页面顺序的唯一纯计算入口。 */
object FolderPagePlanner {

    fun plan(
        folders: List<FolderPageInput>,
        loopEnabled: Boolean = true,
        excludedFolderIds: Set<Long> = emptySet(),
    ): FolderPagePlan {
        val pages = buildList {
            add(FolderPage.Home)
            folders.asSequence()
                .filter { it.id !in excludedFolderIds }
                .sortedWith(compareBy<FolderPageInput> { it.sortOrder }.thenBy { it.id })
                .forEach { add(FolderPage.Folder(it.id)) }
        }
        return FolderPagePlan(pages, loopEnabled)
    }
}
