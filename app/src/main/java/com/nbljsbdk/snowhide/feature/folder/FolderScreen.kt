@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.nbljsbdk.snowhide.feature.folder

import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nbljsbdk.snowhide.data.model.Folder
import com.nbljsbdk.snowhide.data.model.GridItem
import com.nbljsbdk.snowhide.ui.util.frosted

/**
 * 文件夹全屏页（设计文档 §3.3）
 *
 * - 全屏展示（v1.0.9 式，不是 v3 小弹窗），二级封顶不嵌套
 * - 左上角「..」上级图标（大小与文件夹内 app 一致，P0 用箭头占位）
 * - 内部宫格展示成员应用：单击打开、长按菜单
 * - 左右滑动循环由外层 HorizontalPager 负责（本组件只是其中一页）
 */
@Composable
fun FolderScreen(
    folder: Folder,
    memberPackages: List<String>,
    icons: Map<String, ImageBitmap>,
    frozenStates: Map<String, Boolean>,
    columns: Int,
    iconSize: Dp,
    verticalSpace: Int,
    freezeStyle: com.nbljsbdk.snowhide.ui.util.FreezeStyle = com.nbljsbdk.snowhide.ui.util.FreezeStyle.BLUE,
    showAppName: Boolean,
    onBackToHome: () -> Unit,
    onAppClick: (String) -> Unit,
    onAppLongClick: (GridItem) -> Unit,
    onAppLabel: (String) -> String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // 顶栏：.. 上级 + 文件夹名
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            // 左上角「..」上级图标（大小与文件夹内 app 图标一致）
            IconButton(
                onClick = onBackToHome,
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                Icon(
                    Icons.Filled.Home,
                    contentDescription = "返回主屏",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(iconSize),
                )
            }
            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // 成员应用宫格
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(verticalSpace.dp),
        ) {
            items(memberPackages, key = { it }) { pkg ->
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
                    icons[pkg]?.let { bmp ->
                        Image(
                            bitmap = bmp,
                            contentDescription = pkg,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(iconSize)
                                .clip(RoundedCornerShape(iconSize.value * 0.22f))
                                .frosted(enabled = frozenStates[pkg] == true, style = freezeStyle),
                        )
                    }
                    if (showAppName) {
                        Text(
                            text = onAppLabel(pkg),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (frozenStates[pkg] == true)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
