package com.nbljsbdk.snowhide.feature.quicktoggle

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nbljsbdk.snowhide.core.feedback.HapticType
import com.nbljsbdk.snowhide.data.prefs.SettingsRepository
import com.nbljsbdk.snowhide.ui.components.LazyAppIcon
import com.nbljsbdk.snowhide.ui.util.HapticController

/**
 * 快速启停管理界面（设计文档 §3.9，用户拍板终版）
 *
 * 仿增删应用的左右分栏：
 * - 数据源 = 已添加应用（不是全部已装）
 * - 左栏 = 已添加但未加入快速启停；右滑 = 加入
 * - 右栏 = 快速启停成员；左滑 = 移出
 * - 成员在「已添加」里被移出时自动同步剔除（数据一致性）
 * - 两栏排序、图标形状、包名整行显示与增删应用一致
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickToggleScreen(
    onClose: () -> Unit,
    viewModel: QuickToggleViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
            LocalContext.current.applicationContext as Application
        )
    ),
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val showPackageName by viewModel.showPackageName.collectAsState()
    val members by viewModel.members.collectAsState()
    val leftSort by viewModel.leftSort.collectAsState()
    val rightSort by viewModel.rightSort.collectAsState()
    val iconShape by SettingsRepository.iconShape.collectAsState()
    // 左右栏列表：combine 派生 StateFlow，数据变化立即刷新
    val leftApps by viewModel.leftApps.collectAsState()
    val rightApps by viewModel.rightApps.collectAsState()

    val context = LocalContext.current

    BackHandler(onBack = onClose)

    // 磁贴手动添加引导弹窗（requestAddTileService 是系统隐藏 API，第三方不可用）
    var showTileHelp by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("快速启停", fontWeight = FontWeight.Bold) },
                actions = {
                    // 右上角确认 = 返回（加入/移出即时生效，无需暂存）
                    Text(
                        text = "确认",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable(onClick = onClose)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp),
        ) {
            // 说明 + 磁贴申请
            Text(
                text = "点亮磁贴 = 解冻成员中已冻结的应用；熄灭 = 冻回本批（有锁定的跳过并提示）。成员只能从已添加应用中选择。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            TextButton(
                onClick = { showTileHelp = true },
                modifier = Modifier.padding(vertical = 2.dp),
            ) {
                Text("如何添加下拉磁贴", style = MaterialTheme.typography.labelLarge)
            }

            // 搜索 + 显示包名（用户拍板：显示包名放搜索右边）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("搜索") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { viewModel.toggleShowPackageName() }) {
                    Text(
                        text = if (showPackageName) "显示应用名" else "显示包名",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            Row(modifier = Modifier.fillMaxSize()) {
                // 左栏：已添加但未加入
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(end = 4.dp),
                ) {
                    ColumnLabel("可加入（右滑加入）", leftApps.size)
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(leftApps, key = { it }) { pkg ->
                            SwipeRow(
                                 label = viewModel.displayLabel(pkg),
                                 pkg = pkg,
                                 showPackageName = showPackageName,
                                 iconShape = iconShape,
                                 background = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                 allowedDirection = SwipeToDismissBoxValue.StartToEnd,
                                  onSwipe = {
                                      viewModel.addMember(pkg)
                                      HapticController.vibrate(context, HapticType.ORGANIZE_LIST)
                                  },
                            )
                         }
                     }
                    SortButton(label = sortLabel(leftSort), onClick = { viewModel.cycleLeftSort() })
                 }

                // 右栏：快速启停成员
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 4.dp),
                ) {
                    ColumnLabel("快速启停成员（左滑移出）", rightApps.size)
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(rightApps, key = { it }) { pkg ->
                            SwipeRow(
                                 label = viewModel.displayLabel(pkg),
                                 pkg = pkg,
                                 showPackageName = showPackageName,
                                 iconShape = iconShape,
                                 background = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                 allowedDirection = SwipeToDismissBoxValue.EndToStart,
                                  onSwipe = {
                                      viewModel.removeMember(pkg)
                                      HapticController.vibrate(context, HapticType.ORGANIZE_LIST)
                                  },
                            )
                 }
             }
                    SortButton(label = sortLabel(rightSort), onClick = { viewModel.cycleRightSort() })
         }
    }

    // 磁贴手动添加引导
    if (showTileHelp) {
        AlertDialog(
            onDismissRequest = { showTileHelp = false },
            title = { Text("添加下拉磁贴") },
            text = {
                Text(
                    "系统限制，应用无法自动申请磁贴，请手动添加：\n\n" +
                        "1. 下拉两次展开快速设置面板\n" +
                        "2. 点编辑（铅笔图标）\n" +
                        "3. 在列表里找到「快速启停」拖到面板上"
                )
            },
            confirmButton = {
                TextButton(onClick = { showTileHelp = false }) { Text("知道了") }
            },
        )
    }
}
    }
}

/** 栏标题 + 数量 */
@Composable
private fun ColumnLabel(text: String, count: Int) {
    Text(
        text = "$text（$count）",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

private fun sortLabel(mode: QuickToggleViewModel.SortMode): String = when (mode) {
    QuickToggleViewModel.SortMode.TIME_DESC -> "时间倒序"
    QuickToggleViewModel.SortMode.TIME_ASC -> "时间正序"
    QuickToggleViewModel.SortMode.NAME_DESC -> "名字倒序"
    QuickToggleViewModel.SortMode.NAME_ASC -> "名字正序"
    QuickToggleViewModel.SortMode.RECENT_DESC -> "最近添加"
}

@Composable
private fun SortButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = "排序：$label ▸",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 可滑动行（与增删应用同款交互）；显示应用名+（开关时）包名两行 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeRow(
    label: String,
    pkg: String,
    showPackageName: Boolean,
    iconShape: String,
    background: androidx.compose.ui.graphics.Color,
    allowedDirection: SwipeToDismissBoxValue,
    onSwipe: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == allowedDirection) {
                onSwipe()
                false
            } else value == SwipeToDismissBoxValue.Settled
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = allowedDirection == SwipeToDismissBoxValue.StartToEnd,
        enableDismissFromEndToStart = allowedDirection == SwipeToDismissBoxValue.EndToStart,
        backgroundContent = {
            // 无文字提示，仅淡色背景
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(background),
            )
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(background)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LazyAppIcon(pkg = pkg, size = 36.dp, iconShape = iconShape)
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(start = 10.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (showPackageName) {
                    Text(
                        text = pkg,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
