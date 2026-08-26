@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.nbljsbdk.snowhide.feature.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nbljsbdk.snowhide.R
import com.nbljsbdk.snowhide.data.model.Folder
import com.nbljsbdk.snowhide.data.model.GridItem
import com.nbljsbdk.snowhide.data.model.AppRuntimeState
import com.nbljsbdk.snowhide.ui.theme.TianyiBlue
import com.nbljsbdk.snowhide.ui.util.frosted

/**
 * 文件夹全屏页（设计文档 §3.3）
 *
 * - 全屏展示（v1.0.9 式，不是 v3 小弹窗），二级封顶不嵌套
 * - 顶栏文件夹名由外层 Scaffold 统一接管
 * - 内部宫格首格可放返回主屏按钮，之后是成员应用
 * - 左右滑动循环由外层 HorizontalPager 负责（本组件只是其中一页）
 */
private const val RETURN_HOME_KEY = "__return_home__"

@Composable
fun FolderScreen(
    folder: Folder,
    memberPackages: List<String>,
    icons: Map<String, ImageBitmap>,
    frozenStates: Map<String, Boolean>,
    appStates: Map<String, AppRuntimeState> = emptyMap(),
    columns: Int,
    iconSize: Dp,
    verticalSpace: Int,
    freezeStyle: com.nbljsbdk.snowhide.ui.util.FreezeStyle = com.nbljsbdk.snowhide.ui.util.FreezeStyle.BLUE,
    iconShape: String = "round",
    showAppName: Boolean,
    showReturnHomeButton: Boolean,
    onBackToHome: () -> Unit,
    onAppClick: (String) -> Unit,
    onAppLongClick: (GridItem) -> Unit,
    onAppLabel: (String) -> String,
    onBlankLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // 成员应用宫格
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier
                // 空白处长按 → 宫格设置菜单（与主屏一致）
                .fillMaxSize()
                .combinedClickable(
                    onClick = {},
                    onLongClick = { onBlankLongPress() },
                )
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(verticalSpace.dp),
        ) {
            if (showReturnHomeButton) {
                item(key = RETURN_HOME_KEY) {
                    ReturnHomeCell(
                        iconSize = iconSize,
                        iconShape = iconShape,
                        showName = showAppName,
                        onClick = onBackToHome,
                    )
                }
            }
            // distinct 兜底：历史数据可能同文件夹重复 pkg（LazyGrid key 崩溃防御）
            items(memberPackages.distinct(), key = { "app:$it" }) { pkg ->
                val runtimeState = appStates[pkg]
                val frozen = runtimeState == AppRuntimeState.FROZEN ||
                    ((runtimeState == null || runtimeState == AppRuntimeState.UNKNOWN) &&
                        frozenStates[pkg] == true)
                val missing = runtimeState == AppRuntimeState.MISSING
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .combinedClickable(
                            onClick = { onAppClick(pkg) },
                            onLongClick = {
                                onAppLongClick(
                                    GridItem(
                                        id = pkg.hashCode().toLong(),
                                        type = "app",
                                        pkg = pkg,
                                        sortOrder = 0,
                                    )
                                )
                            },
                        )
                        .padding(4.dp),
                ) {
                    Box(contentAlignment = Alignment.TopStart) {
                        icons[pkg]?.let { bmp ->
                            Image(
                                bitmap = bmp,
                                contentDescription = pkg,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(iconSize)
                                    .clip(if (iconShape == "circle") CircleShape
                                    else RoundedCornerShape(iconSize.value * 0.22f))
                                    .frosted(enabled = frozen, style = freezeStyle),
                            )
                        }
                        // 雪花角标（与主屏一致）
                        if (frozen) {
                            Image(
                                painter = painterResource(R.drawable.ic_snowflake),
                                contentDescription = "已冻结",
                                colorFilter = ColorFilter.tint(TianyiBlue),
                                modifier = Modifier.size(iconSize * 0.38f),
                            )
                        } else if (missing) {
                            Image(
                                painter = painterResource(R.drawable.ic_trash),
                                contentDescription = "应用已删除",
                                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.error),
                                modifier = Modifier.size(iconSize * 0.38f),
                            )
                        }
                    }
                    if (showAppName) {
                        // 白字黑边（壁纸透明背景下可读）
                        com.nbljsbdk.snowhide.ui.components.OutlinedText(
                            text = onAppLabel(pkg),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

/** 文件夹首格的返回主屏伪应用（不写入宫格数据） */
@Composable
private fun ReturnHomeCell(
    iconSize: Dp,
    iconShape: String,
    showName: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(iconSize)
                .clip(
                    if (iconShape == "circle") CircleShape
                    else RoundedCornerShape(iconSize.value * 0.22f),
                )
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "回到主屏",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(iconSize * 0.52f),
            )
        }
        if (showName) {
            Spacer(modifier = Modifier.size(2.dp))
            com.nbljsbdk.snowhide.ui.components.OutlinedText(
                text = "回到主屏",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
