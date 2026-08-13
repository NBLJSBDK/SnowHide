package com.nbljsbdk.snowhide.feature.appmanage

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * 增删应用界面（设计文档 §3.8 用户拍板终版，左右分栏）
 *
 * 顶行：搜索框 + 系统应用切换（彩蛋解锁后显示）+ 显示隐藏包名
 * 左栏：未添加应用（默认安装时间倒序，左下排序选项）
 * 右栏：已添加应用（默认名称正序，右下排序选项）
 * 滑动移动：左栏右滑=加入，右栏左滑=解冻并移出
 * 排序 4 档循环切换，不持久化
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
    // 订阅刷新触发器：宫格数据变化时立即重组（增删操作后列表实时刷新）
    val refreshTrigger by viewModel.refresh.collectAsState()

    // 返回键回到主界面（而非退出到桌面）
    BackHandler(onBack = onClose)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("增删应用", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(onClick = onClose) { Text("返回") }
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
                // ── 左栏：未添加应用 ──
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(end = 4.dp),
                ) {
                    val notAdded = viewModel.notAddedApps()
                    ColumnLabel("未添加应用（右滑加入）", notAdded.size)
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(notAdded, key = { it.pkg }) { app ->
                            SwipeableAppRow(
                                label = viewModel.displayLabel(app),
                                background = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                actionText = "加入",
                                onSwipe = { viewModel.addApp(app.pkg) },
                            )
                        }
                    }
                    // 左下角排序选项（4 档循环，不持久化）
                    SortButton(label = sortLabel(leftSort), onClick = { viewModel.cycleLeftSort() })
                }

                // ── 右栏：已添加应用 ──
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 4.dp),
                ) {
                    val added = viewModel.addedApps()
                    ColumnLabel("已添加应用（左滑移出）", added.size)
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(added, key = { it.pkg }) { app ->
                            SwipeableAppRow(
                                label = viewModel.displayLabel(app),
                                background = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                actionText = "移出",
                                onSwipe = { viewModel.removeApp(app.pkg) },
                            )
                        }
                    }
                    SortButton(label = sortLabel(rightSort), onClick = { viewModel.cycleRightSort() })
                }
            }
        }
    }
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

/** 可滑动应用行：滑动触发动作后自动回弹 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableAppRow(
    label: String,
    background: androidx.compose.ui.graphics.Color,
    actionText: String,
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
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(background)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        )
    }
}
