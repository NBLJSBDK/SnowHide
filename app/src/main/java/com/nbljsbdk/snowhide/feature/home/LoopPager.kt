package com.nbljsbdk.snowhide.feature.home

import androidx.compose.foundation.pager.PagerState

/**
 * 循环滑动分页锚点（设计文档 §3.2）
 *
 * 页面序列 = [主屏, 文件夹1, 文件夹2, ...]（文件夹按主屏混排顺序），
 * 左右滑动循环切换。Pager 以 LOOP_BASE 倍数为起点、LOOP_TOTAL 倍数
 * 为总页数模拟循环；「主屏基准页」= currentPage 所在循环段的首页
 * （对 actualCount 取模为 0）。
 *
 * 集中管理基准页计算与对齐动作，避免各调用点重复
 * `(currentPage / actualCount) * actualCount` 魔数算式。
 */

/** 主屏基准页：currentPage 所在循环段的首页 */
internal fun PagerState.homeBase(actualCount: Int): Int =
    (currentPage / actualCount) * actualCount

/**
 * 瞬时对齐主屏基准（整理目录锁定主屏用）。
 * 必须瞬时：动画期间 actualCount 可能已变化，取模结果会显示文件夹页。
 */
internal suspend fun PagerState.scrollHome(actualCount: Int) {
    val base = homeBase(actualCount)
    if (currentPage != base) scrollToPage(base)
}

/** 带动画滑回主屏基准（文件夹页按返回键用） */
internal suspend fun PagerState.animateHome(actualCount: Int) =
    animateScrollToPage(homeBase(actualCount))

/** 瞬时跳到指定文件夹页（主屏点文件夹图标用，无动画快速弹出） */
internal suspend fun PagerState.jumpToFolder(actualCount: Int, folderIndex: Int) {
    val target = homeBase(actualCount) + folderIndex + 1
    if (currentPage != target) scrollToPage(target)
}
