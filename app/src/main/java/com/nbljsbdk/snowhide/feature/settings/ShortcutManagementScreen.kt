package com.nbljsbdk.snowhide.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** 快捷方式管理三级菜单：上方保留待开放的创建入口，下方管理系统记录。 */

/** 桌面快捷方式计划条目（固定入口待开放）。 */
data class ShortcutPlan(
    val title: String,
    val description: String,
    val action: String,
)

private val shortcutPlans = listOf(
    ShortcutPlan("冻结全部", "冻结全部已添加应用（连锁定）", "freeze_all"),
    ShortcutPlan("启用全部", "解冻全部已添加应用", "enable_all"),
    ShortcutPlan("智能清理", "停用未锁定且未冻结的应用", "smart_clean"),
    ShortcutPlan("快速启停开关", "反转快速启停磁贴状态", "toggle_quick"),
    ShortcutPlan("打开雪藏", "回到雪藏主界面", "open_main"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutManagementScreen(
    onBack: () -> Unit,
    onClearAllShortcuts: suspend () -> Result<Int>,
) {
    BackHandler(onBack = onBack)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var infoTitle by remember { mutableStateOf<String?>(null) }
    var infoText by remember { mutableStateOf("") }

    fun showCleanupResult(title: String, operation: suspend () -> Result<Int>) {
        scope.launch {
            val message = operation().fold(
                onSuccess = { count ->
                    if (count == 0) "$title：当前没有可移除的快捷方式"
                    else "$title：已处理 $count 个快捷方式"
                },
                onFailure = { error -> "${title}失败：${error.message ?: "系统不支持"}" },
            )
            snackbarHostState.showSnackbar(message)
        }
    }

    fun showInfo(title: String, text: String) {
        infoTitle = title
        infoText = text
    }

    infoTitle?.let { title ->
        AlertDialog(
            onDismissRequest = { infoTitle = null },
            title = { Text(title) },
            text = { Text(infoText) },
            confirmButton = {
                TextButton(onClick = { infoTitle = null }) { Text("知道了") }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("快捷方式管理", fontWeight = FontWeight.Bold) },
                actions = {
                    Text(
                        text = "返回",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable(onClick = onBack)
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = "计划：以下快捷方式将通过「固定到桌面」创建（接口已预留）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(vertical = 6.dp))
            shortcutPlans.forEach { plan ->
                PlanRow(plan)
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                text = "快捷方式清理",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "只处理系统快捷方式，不影响雪藏宫格和应用数据。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ShortcutCleanupRow(
                title = "清理全部快捷方式",
                description = "无论是否有效，清除系统保存的全部快捷方式记录",
                onClick = {
                    showCleanupResult("全部快捷方式", onClearAllShortcuts)
                },
                onInfo = {
                    showInfo(
                        "清理全部快捷方式",
                        "无论快捷方式当前是否有效，都会清除系统保存的全部快捷方式记录。\n\n不会删除雪藏宫格、应用数据或应用本身。",
                    )
                },
            )
        }
    }
}

/** 计划条目行（灰显 + 待开放）。 */
@Composable
private fun PlanRow(plan: ShortcutPlan) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plan.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = plan.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "待开放",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

/** 快捷方式清理操作行。 */
@Composable
private fun ShortcutCleanupRow(
    title: String,
    description: String,
    onClick: () -> Unit,
    onInfo: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onInfo) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "说明",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            androidx.compose.material3.TextButton(onClick = onClick) {
                Text("清理", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
