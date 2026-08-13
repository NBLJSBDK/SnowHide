@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.nbljsbdk.snowhide.feature.appmanage

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import com.nbljsbdk.snowhide.data.repo.AppListRepository
import com.nbljsbdk.snowhide.ui.components.LazyAppIcon

/**
 * 增删应用界面（设计文档 §3.8 用户拍板终版，左右分栏 + 暂存模式）
 *
 * 顶行：搜索框 + 系统应用切换（彩蛋解锁后显示）+ 显示隐藏包名
 * 左栏：未添加应用（默认安装时间倒序，左下排序选项）
 * 右栏：已添加应用（默认名称正序，右下排序选项）
 * 滑动移动：左栏右滑=加入，右栏左滑=解冻并移出（暂存，确认才落盘）
 * 顶栏三按钮（长按=说明，点击=弹窗确认）：
 *   返回=丢弃改动并返回；确认=加入列表不冻结；应用=加入列表并立即冻结
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppManageScreen(
    onClose: () -> Unit,
    viewModel: AppManageViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
            LocalContext.current.applicationContext as Application
        )
    ),
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val showPackageName by viewModel.showPackageName.collectAsState()
    val systemUnlocked by viewModel.systemUnlocked.collectAsState()
    val showSystemOnly by viewModel.showSystemOnly.collectAsState()
    val leftSort by viewModel.leftSort.collectAsState()
    val rightSort by viewModel.rightSort.collectAsState()
    val leftApps by viewModel.leftApps.collectAsState()
    val rightApps by viewModel.rightApps.collectAsState()
    val loaded by AppListRepository.loaded.collectAsState()
    val pendingAdded by viewModel.pendingAdded.collectAsState()
    val pendingRemoved by viewModel.pendingRemoved.collectAsState()
    val hasPending by viewModel.hasPendingChanges.collectAsState()

    // 顶栏弹窗：back / confirm / apply / help-back / help-confirm / help-apply
    var dialog by remember { mutableStateOf<String?>(null) }

    // 返回键 = 丢弃并返回（同「返回」按钮）
    BackHandler(onBack = {
        viewModel.cancelChanges()
        onClose()
    })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("增删应用", fontWeight = FontWeight.Bold) },
                actions = {
                    TopBarAction("返回", onClick = {
                        if (hasPending) dialog = "back"
                        else {
                            viewModel.cancelChanges()
                            onClose()
                        }
                    }, onLongClick = { dialog = "help-back" })
                    TopBarAction("确认", onClick = { dialog = "confirm" },
                        onLongClick = { dialog = "help-confirm" })
                    TopBarAction("应用", onClick = { dialog = "apply" },
                        onLongClick = { dialog = "help-apply" })
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
            // 顶行：搜索 + 系统应用切换（解锁后显示）+ 显示包名
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("搜索应用") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                if (systemUnlocked) {
                    TextButton(onClick = { viewModel.toggleSystemOnly() }) {
                        Text(
                            text = if (showSystemOnly) "系统应用 ✓" else "系统应用",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (showSystemOnly) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(onClick = { viewModel.toggleShowPackageName() }) {
                    Text(
                        text = if (showPackageName) "显示应用名" else "显示包名",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            // 左右分栏
            Row(modifier = Modifier.fillMaxSize()) {
                // 左栏：未添加应用
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(end = 4.dp),
                ) {
                    ColumnLabel("未添加应用（右滑加入）", leftApps.size)
                    if (!loaded && leftApps.isEmpty()) {
                        CircularProgressIndicator(modifier = Modifier.padding(24.dp))
                    }
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(leftApps, key = { it.pkg }) { app ->
                            SwipeableAppRow(
                                label = app.label,
                                pkg = app.pkg,
                                showPackageName = showPackageName,
                                background = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                onSwipe = { viewModel.addApp(app.pkg) },
                            )
                        }
                    }
                    SortButton(label = sortLabel(leftSort), onClick = { viewModel.cycleLeftSort() })
                }

                // 右栏：已添加应用
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 4.dp),
                ) {
                    ColumnLabel("已添加应用（左滑移出）", rightApps.size)
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(rightApps, key = { it.pkg }) { app ->
                            SwipeableAppRow(
                                label = app.label,
                                pkg = app.pkg,
                                showPackageName = showPackageName,
                                background = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                onSwipe = { viewModel.removeApp(app.pkg) },
                            )
                        }
                    }
                    SortButton(label = sortLabel(rightSort), onClick = { viewModel.cycleRightSort() })
                }
            }
        }
    }

    // ── 顶栏弹窗 ──
    when (dialog) {
        "back" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("丢弃改动？") },
            text = {
                Text("将丢弃本次改动：${pendingAdded.size} 个加入、${pendingRemoved.size} 个移出。")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.cancelChanges(); dialog = null; onClose()
                }) { Text("丢弃并返回", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { dialog = null }) { Text("继续编辑") }
            },
        )
        "confirm" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("确认加入列表？") },
            text = {
                Text("${pendingAdded.size} 个应用将加入列表（不立即冻结），${pendingRemoved.size} 个将移出。")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.confirmChanges(); dialog = null; onClose()
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { dialog = null }) { Text("取消") }
            },
        )
        "apply" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("加入并立即冻结？") },
            text = {
                Text("${pendingAdded.size} 个应用将加入列表并立即冻结，${pendingRemoved.size} 个将移出。")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.applyAndFreeze(); dialog = null; onClose()
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { dialog = null }) { Text("取消") }
            },
        )
        "help-back" -> HelpDialog("返回", "丢弃本次所有改动并返回主界面。未确认的加入/移出都不会生效。") { dialog = null }
        "help-confirm" -> HelpDialog("确认", "将本次改动加入列表，但不立即冻结。应用加入后处于解冻状态。") { dialog = null }
        "help-apply" -> HelpDialog("应用", "将本次改动加入列表，并立即冻结新加入的应用（图标消失、进程全断）。") { dialog = null }
    }
}

/** 顶栏操作按钮（长按弹说明） */
@Composable
private fun TopBarAction(
    label: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/** 长按说明弹窗 */
@Composable
private fun HelpDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        },
    )
}

/** 排序 4 档显示文案 */
private fun sortLabel(mode: AppManageViewModel.SortMode): String = when (mode) {
    AppManageViewModel.SortMode.TIME_DESC -> "时间倒序"
    AppManageViewModel.SortMode.TIME_ASC -> "时间正序"
    AppManageViewModel.SortMode.NAME_DESC -> "名字倒序"
    AppManageViewModel.SortMode.NAME_ASC -> "名字正序"
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

/** 排序选项按钮（栏底） */
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

/** 可滑动应用行：滑动触发动作后自动回弹；应用图标 + 名称 +（开关时）包名 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableAppRow(
    label: String,
    pkg: String,
    showPackageName: Boolean,
    background: androidx.compose.ui.graphics.Color,
    onSwipe: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                onSwipe()
                false // 不真正移除列表项，由数据刷新驱动
            } else true
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
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
            LazyAppIcon(pkg = pkg, size = 36.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showPackageName) {
                    Text(
                        text = pkg,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
