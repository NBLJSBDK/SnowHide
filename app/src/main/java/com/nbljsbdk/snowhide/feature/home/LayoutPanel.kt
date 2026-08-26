package com.nbljsbdk.snowhide.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

/**
 * 布局设置透明浮框（长按应用/文件夹 → 布局设置，设计文档 §3.5）
 *
 * 半透明小窗浮在主屏上，拖动滑条**实时生效**：
 * SettingsRepository 的 StateFlow 变化 → 主屏宫格即时重组，
 * 浮框保持半透明以便看到背后效果。无状态组件（值+回调）。
 */
@Composable
fun LayoutPanel(
    columns: Int,
    iconSize: Int,
    verticalSpace: Int,
    dockIconSize: Int,
    dockActionIconSize: Int,
    folderPreview: Int,
    onColumnsChange: (Int) -> Unit,
    onIconSizeChange: (Int) -> Unit,
    onVerticalSpaceChange: (Int) -> Unit,
    onDockIconSizeChange: (Int) -> Unit,
    onDockActionIconSizeChange: (Int) -> Unit,
    onFolderPreviewChange: (Int) -> Unit,
    onComplete: () -> Unit,
    onBackToMenu: () -> Unit,
) {
    Dialog(
        onDismissRequest = onBackToMenu,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .width(300.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = "布局设置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))

            // 每排数量（步进按钮）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "每排数量",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onColumnsChange((columns - 1).coerceAtLeast(3)) }) {
                    Text("−", style = MaterialTheme.typography.titleLarge)
                }
                Text("$columns", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { onColumnsChange((columns + 1).coerceAtMost(7)) }) {
                    Text("+", style = MaterialTheme.typography.titleLarge)
                }
            }

            SliderRow("图标大小", iconSize, 36f..96f, onIconSizeChange)
            SliderRow("上下间距", verticalSpace, 0f..40f, onVerticalSpaceChange)
            SliderRow("底部图标", dockIconSize, 28f..72f, onDockIconSizeChange)
            SliderRow("操作图标", dockActionIconSize, 12f..32f, onDockActionIconSizeChange)

            // 文件夹拼贴 2×2 / 3×3（用户拍板）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "文件夹拼贴",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { onFolderPreviewChange(2) },
                ) {
                    Text(
                        text = "2×2",
                        fontWeight = if (folderPreview == 2) FontWeight.Bold else FontWeight.Normal,
                        color = if (folderPreview == 2) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = { onFolderPreviewChange(3) },
                ) {
                    Text(
                        text = "3×3",
                        fontWeight = if (folderPreview == 3) FontWeight.Bold else FontWeight.Normal,
                        color = if (folderPreview == 3) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onBackToMenu) { Text("取消") }
                TextButton(onClick = onComplete) { Text("完成") }
            }
        }
    }
}

/** 布局滑条行：标签 + 滑条 + 当前值 */
@Composable
private fun SliderRow(
    label: String,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(72.dp),
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${value}dp",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp),
        )
    }
}
