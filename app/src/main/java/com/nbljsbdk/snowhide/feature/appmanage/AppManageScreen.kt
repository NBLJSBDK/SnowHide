@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.nbljsbdk.snowhide.feature.appmanage

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect

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
import com.nbljsbdk.snowhide.data.repo.AppListRepository
import com.nbljsbdk.snowhide.data.prefs.SettingsRepository
import com.nbljsbdk.snowhide.core.feedback.HapticType
import com.nbljsbdk.snowhide.domain.appclone.AppCloneUser
import com.nbljsbdk.snowhide.ui.components.LazyAppIcon
import com.nbljsbdk.snowhide.ui.util.HapticController

/**
 * 增删应用界面（设计文档 §3.8 用户拍板终版，左右分栏 + 即时应用）
 *
 * 顶行：搜索框 + 系统应用切换（彩蛋解锁后显示）+ 显示隐藏包名
 * 左栏：未添加应用（默认安装时间倒序，左下排序选项）
 * 右栏：已添加应用（默认名称正序，右下排序选项）
 * 滑动移动：左栏只允许右滑加入，右栏只允许左滑解冻并移出
 * 顶栏按钮（长按=说明，点击=弹窗确认）：
 *   确认=返回且不额外冻结；应用=只冻结本次新增应用并返回
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppManageScreen(
    onClose: () -> Unit,
    viewModel: AppManageViewModel,
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val showPackageName by viewModel.showPackageName.collectAsState()
    val systemUnlocked by viewModel.systemUnlocked.collectAsState()
    val showSystemOnly by viewModel.showSystemOnly.collectAsState()
    val leftSort by viewModel.leftSort.collectAsState()
    val rightSort by viewModel.rightSort.collectAsState()
    val iconShape by SettingsRepository.iconShape.collectAsState()
    val leftApps by viewModel.leftApps.collectAsState()
    val rightApps by viewModel.rightApps.collectAsState()
    val cloneMode by viewModel.cloneMode.collectAsState()
    val cloneUsers by viewModel.cloneUsers.collectAsState()
    val selectedCloneUserId by viewModel.selectedCloneUserId.collectAsState()
    val cloneLoading by viewModel.cloneLoading.collectAsState()
    val cloneError by viewModel.cloneError.collectAsState()
    val loaded by AppListRepository.loaded.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.beginSession()
    }

    // 顶栏弹窗：apply / help-confirm / help-apply
    var dialog by remember { mutableStateOf<String?>(null) }
    var cloneUserMenuOpen by remember { mutableStateOf(false) }

    // 移出成功提示 Snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    val message by viewModel.message.collectAsState()
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    // 返回键 = 直接退出（滑动加入/移出即时生效，无暂存可丢）
    BackHandler(onBack = onClose)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("增删应用", fontWeight = FontWeight.Bold) },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { viewModel.setCloneMode(!cloneMode) }
                            .padding(horizontal = 2.dp),
                    ) {
                        Checkbox(
                            checked = cloneMode,
                            onCheckedChange = viewModel::setCloneMode,
                            modifier = Modifier.size(36.dp),
                        )
                        Text("分身应用", style = MaterialTheme.typography.labelSmall)
                    }
                    // 两按钮：确认=退出；应用=只冻结本次新增且未冻结的应用并退出（长按=说明）
                    TopBarAction("确认", onClick = { onClose() },
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
            if (cloneMode) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "分身用户",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box {
                        val selectedUser = cloneUsers.firstOrNull { it.id == selectedCloneUserId }
                        TextButton(
                            onClick = { cloneUserMenuOpen = true },
                            enabled = !cloneLoading && cloneUsers.isNotEmpty(),
                        ) {
                            Text(selectedUser?.displayName() ?: "未发现用户空间")
                        }
                        DropdownMenu(
                            expanded = cloneUserMenuOpen,
                            onDismissRequest = { cloneUserMenuOpen = false },
                        ) {
                            cloneUsers.forEach { user ->
                                DropdownMenuItem(
                                    text = { Text(user.displayName()) },
                                    onClick = {
                                        cloneUserMenuOpen = false
                                        viewModel.selectCloneUser(user.id)
                                    },
                                )
                            }
                        }
                    }
                    if (cloneLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = viewModel::refreshCloneApps,
                        enabled = !cloneLoading,
                    ) { Text("刷新") }
                }
                cloneError?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

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
                    if ((cloneMode && cloneLoading && leftApps.isEmpty()) ||
                        (!cloneMode && !loaded && leftApps.isEmpty())) {
                        CircularProgressIndicator(modifier = Modifier.padding(24.dp))
                    }
                    if (cloneMode && !cloneLoading && cloneUsers.isEmpty()) {
                        Text(
                            text = "未发现非 user 0 用户空间",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(leftApps, key = { it.target.key }) { app ->
                            SwipeableAppRow(
                                item = app,
                                showPackageName = showPackageName,
                                iconShape = iconShape,
                                background = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                allowedDirection = SwipeToDismissBoxValue.StartToEnd,
                                onSwipe = {
                                    viewModel.addApp(app)
                                    HapticController.vibrate(context, HapticType.ORGANIZE_LIST)
                                },
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
                        items(rightApps, key = { it.target.key }) { app ->
                            SwipeableAppRow(
                                item = app,
                                showPackageName = showPackageName,
                                iconShape = iconShape,
                                background = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                allowedDirection = SwipeToDismissBoxValue.EndToStart,
                                onSwipe = {
                                    viewModel.removeApp(app)
                                    HapticController.vibrate(context, HapticType.ORGANIZE_LIST)
                                },
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
        "apply" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("冻结本次新增应用？") },
            text = {
                Text("将冻结本次进入页面后新加入、且当前未冻结的应用并退出。\n（增删即时生效，已有应用不会被处理）")
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
        "help-confirm" -> HelpDialog("确认", "加入/移出已即时生效，点击确认直接返回主界面。") { dialog = null }
        "help-apply" -> HelpDialog("应用", "只冻结本次进入页面后新加入、且当前未冻结的应用并返回主界面。") { dialog = null }
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
    AppManageViewModel.SortMode.RECENT_DESC -> "最近添加"
}

private fun AppCloneUser.displayName(): String = buildString {
    append(name.ifBlank { "用户 $id" })
    append(" (user ")
    append(id)
    append(')')
    if (isManagedProfile) append(" 工作资料")
    if (!running) append(" 未运行")
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
    item: AppManageItem,
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
                false // 不真正移除列表项，由数据刷新驱动
            } else value == SwipeToDismissBoxValue.Settled
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = allowedDirection == SwipeToDismissBoxValue.StartToEnd,
        enableDismissFromEndToStart = allowedDirection == SwipeToDismissBoxValue.EndToStart,
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
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LazyAppIcon(pkg = item.pkg, size = 36.dp, iconShape = iconShape)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!item.target.isPrimaryUser) {
                            Text(
                                text = "分身 user ${item.userId}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                if (showPackageName) {
                    Text(
                        text = item.pkg,
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
