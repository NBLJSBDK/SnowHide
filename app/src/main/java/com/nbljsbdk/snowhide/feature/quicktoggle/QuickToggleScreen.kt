package com.nbljsbdk.snowhide.feature.quicktoggle

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
 * 快速启停管理界面（设计文档 §3.9，用户拍板终版）
 *
 * 仿增删应用的左右分栏：
 * - 数据源 = 已添加应用（不是全部已装）
 * - 左栏 = 已添加但未加入快速启停；右滑 = 加入
 * - 右栏 = 快速启停成员；左滑 = 移出
 * - 成员在「已添加」里被移出时自动同步剔除（数据一致性）
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

    BackHandler(onBack = onClose)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("快速启停", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(onClick = onClose) { Text("返回") }
                },
                actions = {
                    TextButton(onClick = { viewModel.toggleShowPackageName() }) {
                        Text(
                            text = if (showPackageName) "显示应用名" else "显示包名",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
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
            // 说明 + 搜索
            Text(
                text = "加入的应用：磁贴点亮时记录状态并全部解冻，熄灭时还原。成员只能从已添加应用中选择。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("搜索") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(modifier = Modifier.fillMaxSize()) {
                // 左栏：已添加但未加入
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(end = 4.dp),
                ) {
                    val notMembers = viewModel.notMemberPackages()
                    ColumnLabel("可加入（右滑加入）", notMembers.size)
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(notMembers, key = { it }) { pkg ->
                            SwipeRow(
                                label = viewModel.displayLabel(pkg),
                                background = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                actionText = "加入",
                                onSwipe = { viewModel.addMember(pkg) },
                            )
                        }
                    }
                }

                // 右栏：快速启停成员
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 4.dp),
                ) {
                    val memberList = members
                    ColumnLabel("快速启停成员（左滑移出）", memberList.size)
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(memberList, key = { it }) { pkg ->
                            SwipeRow(
                                label = viewModel.displayLabel(pkg),
                                background = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                actionText = "移出",
                                onSwipe = { viewModel.removeMember(pkg) },
                            )
                        }
                    }
                }
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

/** 可滑动行（与增删应用同款交互） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeRow(
    label: String,
    background: androidx.compose.ui.graphics.Color,
    actionText: String,
    onSwipe: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                onSwipe()
                false
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
