package com.nbljsbdk.snowhide.feature.organize

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import com.nbljsbdk.snowhide.data.model.Folder
import com.nbljsbdk.snowhide.data.model.GridItem

/**
 * 整理目录底部操作区（无状态 Composable，设计文档 §3.10 状态机）
 *
 * 结构：[上][下][左][右][垃圾桶][文件夹加号] / 文件夹名输入行 / 文件夹内应用横排。
 * 键位灰显规则完全按状态机推导（OrganizeViewModel.OrganizeState）。
 */
@Composable
fun OrganizeOverlay(
    state: OrganizeViewModel.OrganizeState,
    folders: List<Folder>,
    folderApps: List<String>,
    icons: Map<String, ImageBitmap>,
    onTapHomeApp: (GridItem) -> Unit,
    onTapFolder: (Folder) -> Unit,
    onTapFolderApp: (String) -> Unit,
    onShift: (Int) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onCreate: () -> Unit,
    onDelete: () -> Unit,
    onNameChange: (String) -> Unit,
    onNameCommit: () -> Unit,
    onAppLabel: (String) -> String,
) {
    // 状态机 → 键位可用性（设计文档 §3.10）
    val homeAppSelected = state is OrganizeViewModel.OrganizeState.HomeAppSelected
    val folderSelected = state as? OrganizeViewModel.OrganizeState.FolderSelected
    val canLeftRight = homeAppSelected || folderSelected != null
    val canUp = folderSelected?.subFolderAppPkg != null
    val canDown = folderSelected?.subHomeApp != null
    val canDelete = folderSelected != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(8.dp),
    ) {
        // 键位行
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth(),
        ) {
            OrganizeKey("↑", enabled = canUp, onClick = onMoveUp)
            OrganizeKey("↓", enabled = canDown, onClick = onMoveDown)
            OrganizeKey("←", enabled = canLeftRight, onClick = { onShift(-1) })
            OrganizeKey("→", enabled = canLeftRight, onClick = { onShift(1) })
            OrganizeKey("🗑", enabled = canDelete, onClick = onDelete)
            OrganizeKey("＋", enabled = true, onClick = onCreate)
        }

        // 文件夹名输入行（③ 时显示）
        if (folderSelected != null) {
            OutlinedTextField(
                value = folderSelected.folderNameInput,
                onValueChange = onNameChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                placeholder = { Text("文件夹名字") },
                singleLine = true,
            )
            // 输入焦点丢失即提交改名
            // （P0 简化：确认按钮提交；这里 onNameCommit 由确认键调用）
        }

        // 文件夹内应用横排（③ 时显示）
        if (folderSelected != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
            ) {
                folderApps.forEach { pkg ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onTapFolderApp(pkg) }
                            .padding(4.dp)
                            .background(
                                if (folderSelected.subFolderAppPkg == pkg)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                else MaterialTheme.colorScheme.surface,
                            ),
                    ) {
                        icons[pkg]?.let { bmp ->
                            Image(
                                bitmap = bmp,
                                contentDescription = pkg,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(40.dp),
                            )
                        }
                        Text(
                            text = onAppLabel(pkg),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

/** 单个键位（灰显/高亮按 enabled） */
@Composable
private fun OrganizeKey(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = if (enabled) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
