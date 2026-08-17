package com.nbljsbdk.snowhide.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
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
import com.nbljsbdk.snowhide.ui.util.AppIconLoader

/**
 * 美化设置透明浮框（长按主屏空白 → 美化设置，与布局设置同款浮框）
 *
 * 内容：图标包选择（点击行在浮框内展开列表）+ 背景透明开关。
 * 无状态组件（值+回调）。
 */
@Composable
fun BeautyPanel(
    iconPack: String,
    transparentBg: Boolean,
    freezeStyle: com.nbljsbdk.snowhide.ui.util.FreezeStyle,
    iconPacks: List<AppIconLoader.IconPackInfo>,
    onIconPackSelect: (String) -> Unit,
    onTransparentToggle: (Boolean) -> Unit,
    onFreezeStyleSelect: (com.nbljsbdk.snowhide.ui.util.FreezeStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    // 浮框内两态：false=主视图，true=图标包列表
    var showPackList by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .width(300.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = if (showPackList) "选择图标包" else "美化设置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (showPackList) {
                // 图标包列表（浮框内展开，限高滚动）
                Column(
                    modifier = Modifier
                        .height(240.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    PackRow("系统默认", selected = iconPack.isEmpty()) {
                        onIconPackSelect("")
                        showPackList = false
                    }
                    iconPacks.forEach { pack ->
                        PackRow(pack.label, selected = iconPack == pack.pkg) {
                            onIconPackSelect(pack.pkg)
                            showPackList = false
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                    TextButton(onClick = { showPackList = false }) { Text("返回") }
                }
            } else {
                // 图标包当前值行
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPackList = true }
                        .padding(vertical = 8.dp),
                ) {
                    Text(
                        text = "图标包",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (iconPack.isEmpty()) "系统默认 ▸" else "$iconPack ▸",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                // 背景透明开关
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTransparentToggle(!transparentBg) }
                        .padding(vertical = 8.dp),
                ) {
                    Text(
                        text = "背景透明（透出壁纸）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Checkbox(
                        checked = transparentBg,
                        onCheckedChange = onTransparentToggle,
                    )
                }
                // 冻结滤镜样式（4 选 1，默认变蓝）
                Text(
                    text = "冻结图标滤镜",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    com.nbljsbdk.snowhide.ui.util.FreezeStyle.entries.forEach { style ->
                        val label = when (style) {
                            com.nbljsbdk.snowhide.ui.util.FreezeStyle.NONE -> "原色"
                            com.nbljsbdk.snowhide.ui.util.FreezeStyle.GRAY -> "变灰"
                            com.nbljsbdk.snowhide.ui.util.FreezeStyle.INVERT -> "反色"
                            com.nbljsbdk.snowhide.ui.util.FreezeStyle.BLUE -> "淡化"
                        }
                        TextButton(
                            onClick = { onFreezeStyleSelect(style) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (freezeStyle == style) FontWeight.Bold else FontWeight.Normal,
                                color = if (freezeStyle == style) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("完成") }
                }
            }
        }
    }
}

/** 图标包单行（浮框内列表） */
@Composable
private fun PackRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (selected) Text("✓", color = MaterialTheme.colorScheme.primary)
    }
}
