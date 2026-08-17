package com.nbljsbdk.snowhide.feature.organize

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import com.nbljsbdk.snowhide.R
import com.nbljsbdk.snowhide.data.model.Folder
import com.nbljsbdk.snowhide.data.model.GridItem

/**
 * 整理目录底部操作区（无状态 Composable，设计文档 §3.10 状态机）
 *
 * 布局自上而下（用户拍板）：
 *   ① 文件夹名称输入行（选中文件夹时显示，不自动弹输入法）
 *   ② 文件夹内应用图标横排（选中文件夹时显示）
 *   ③ 键位行（固定最底）：[上][下][左][右][垃圾桶][+]
 *
 * 键位灰显规则按状态机推导（OrganizeViewModel.OrganizeState）：
 * 垃圾桶仅在选中文件夹时可点，其余按选中目标启用。
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
        // ① 键位行（固定最上，用户拍板）：[上][下][左][右][垃圾桶][+]
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth(),
        ) {
            OrganizeKeyIcon(R.drawable.ic_arrow_up, enabled = canUp, onClick = onMoveUp)
            OrganizeKeyIcon(R.drawable.ic_arrow_down, enabled = canDown, onClick = onMoveDown)
            OrganizeKeyIcon(R.drawable.ic_arrow_left, enabled = canLeftRight, onClick = { onShift(-1) })
            OrganizeKeyIcon(R.drawable.ic_arrow_right, enabled = canLeftRight, onClick = { onShift(1) })
            OrganizeKeyIcon(R.drawable.ic_trash, enabled = canDelete, onClick = onDelete)
            OrganizeKeyIcon(R.drawable.ic_folder_plus, enabled = true, onClick = onCreate)
        }

        // ② 名称/内应用区：选中文件夹时显示，未选中时固定留白
        // （用户拍板：操作区高度恒定，选中不跳动不弹出）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(136.dp),
        ) {
            if (folderSelected != null) {
                // 新建文件夹时自动聚焦并全选名称（删除键快速清除原名）；
                // 点选文件夹不自动聚焦，点名称栏才聚焦（用户拍板）
                val focusRequester = remember { FocusRequester() }
                val keyboard = LocalSoftwareKeyboardController.current
                var selectAll by remember(folderSelected.folderId) {
                    mutableStateOf(folderSelected.justCreated)
                }
                LaunchedEffect(folderSelected.folderId, folderSelected.justCreated) {
                    if (folderSelected.justCreated) {
                        focusRequester.requestFocus()
                        keyboard?.show()
                    }
                }
                Column {
                    // 文件夹名称输入行（IME Done 提交改名，用户拍板）
                    OutlinedTextField(
                        value = TextFieldValue(
                            folderSelected.folderNameInput,
                            selection = if (selectAll) TextRange(0, folderSelected.folderNameInput.length)
                            else TextRange(folderSelected.folderNameInput.length),
                        ),
                        onValueChange = {
                            selectAll = false
                            onNameChange(it.text)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .focusRequester(focusRequester)
                            .onFocusChanged { selectAll = true },
                        placeholder = { Text("文件夹名字") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                onNameCommit()
                                keyboard?.hide()
                            },
                        ),
                    )
                    // 文件夹内应用图标横排
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
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
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
    }
}

/** 单个键位图标（灰显/高亮按 enabled） */
@Composable
private fun OrganizeKeyIcon(
    drawableRes: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.alpha(if (enabled) 1f else 0.35f),
    ) {
        Image(
            painter = painterResource(drawableRes),
            contentDescription = null,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .size(24.dp),
        )
    }
}
