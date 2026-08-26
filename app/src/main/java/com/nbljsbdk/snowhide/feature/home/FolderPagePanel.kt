package com.nbljsbdk.snowhide.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nbljsbdk.snowhide.domain.folder.FolderPageOption

/** 文件夹页面设置浮框：只控制页面导航，不改变主屏宫格数据。 */
@Composable
fun FolderPagePanel(
    folders: List<FolderPageOption>,
    loopEnabled: Boolean,
    excludedFolderIds: Set<Long>,
    showReturnHomeButton: Boolean,
    onLoopEnabledChange: (Boolean) -> Unit,
    onFolderExcludedChange: (Long, Boolean) -> Unit,
    onShowReturnHomeButtonChange: (Boolean) -> Unit,
    onComplete: () -> Unit,
    onBackToMenu: () -> Unit,
) {
    var showExcludedList by remember { mutableStateOf(false) }
    var infoTarget by remember { mutableStateOf<InfoTarget?>(null) }
    val sortedFolders = remember(folders) { folders.sortedBy { it.sortOrder } }
    val excludedCount = sortedFolders.count { it.id in excludedFolderIds }

    Dialog(
        onDismissRequest = {
            if (showExcludedList) showExcludedList = false else onBackToMenu()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .heightIn(max = 620.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = if (showExcludedList) "排除文件夹" else "目录设置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (showExcludedList) {
                if (sortedFolders.isEmpty()) {
                    Text(
                        text = "暂无文件夹",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        sortedFolders.forEach { folder ->
                            val excluded = folder.id in excludedFolderIds
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onFolderExcludedChange(folder.id, !excluded) }
                                    .padding(vertical = 6.dp),
                            ) {
                                Text(
                                    text = folder.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                Checkbox(
                                    checked = excluded,
                                    onCheckedChange = { onFolderExcludedChange(folder.id, it) },
                                )
                            }
                        }
                    }
                }
                PanelButtons(
                    primaryText = "返回",
                    onPrimary = { showExcludedList = false },
                    onDismiss = { showExcludedList = false },
                )
            } else {
                SettingInfoRow(
                    label = "循环滑动",
                    checked = loopEnabled,
                    onCheckedChange = onLoopEnabledChange,
                    onInfo = { infoTarget = InfoTarget.LOOP },
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showExcludedList = true }
                        .padding(vertical = 8.dp),
                ) {
                    Text(
                        text = "排除文件夹",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = when (excludedCount) {
                            0 -> "未排除 ▸"
                            else -> "已排除 ${excludedCount} 个 ▸"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    InfoButton(onClick = { infoTarget = InfoTarget.EXCLUDED })
                }
                SettingInfoRow(
                    label = "显示返回主屏按钮",
                    checked = showReturnHomeButton,
                    onCheckedChange = onShowReturnHomeButtonChange,
                    onInfo = { infoTarget = InfoTarget.RETURN_HOME },
                )
                PanelButtons(
                    primaryText = "完成",
                    onPrimary = onComplete,
                    onDismiss = onBackToMenu,
                )
            }
        }
    }

    infoTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { infoTarget = null },
            title = { Text(target.title) },
            text = { Text(target.description) },
            confirmButton = {
                TextButton(onClick = { infoTarget = null }) { Text("知道了") }
            },
        )
    }
}

private enum class InfoTarget(
    val title: String,
    val description: String,
) {
    LOOP(
        title = "循环滑动说明",
        description = "开启后，主屏与文件夹页面首尾相接；关闭后，左右滑动到边界时停止。",
    ),
    EXCLUDED(
        title = "排除文件夹说明",
        description = "这里只从左右滑动页面中排除文件夹，主屏宫格中的文件夹不会被删除或隐藏。",
    ),
    RETURN_HOME(
        title = "返回主屏按钮说明",
        description = "开启后，文件夹页面的首格显示返回主屏按钮；关闭后不显示这个首格。",
    ),
}

@Composable
private fun SettingInfoRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onInfo: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        InfoButton(onClick = onInfo)
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun InfoButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = "功能说明",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PanelButtons(
    primaryText: String,
    onPrimary: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = onDismiss) { Text("取消") }
        TextButton(onClick = onPrimary) { Text(primaryText) }
    }
}
