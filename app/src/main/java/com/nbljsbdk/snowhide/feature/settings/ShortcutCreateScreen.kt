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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 创建快捷方式三级菜单（设置页 → 创建快捷方式，设计文档 §3.12/P2）
 *
 * 当前阶段：只展示计划清单（文字），接口预留。
 * P2 时逐条接入 [requestPinShortcut]（ShortcutManager.requestPinShortcut，
 * launcher 弹确认后固定到桌面）。
 */

/** 桌面快捷方式计划条目（未来可创建项） */
data class ShortcutPlan(
    val title: String,
    val description: String,
    val action: String, // 预留接口：对应动作标识
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
fun ShortcutCreateScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("创建快捷方式", fontWeight = FontWeight.Bold) },
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
                text = "计划：以下快捷方式将通过「固定到桌面」创建（开发中，接口已预留）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(vertical = 6.dp))
            shortcutPlans.forEach { plan ->
                PlanRow(plan)
            }
        }
    }
}

/** 计划条目行（灰显 + 即将到来） */
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
                text = "即将到来",
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

/**
 * 接口预留（P2 实现）：
 * ShortcutManager.requestPinShortcut 把快捷方式固定到桌面
 * （系统弹确认，用户确认后由 launcher 创建）。
 */
@Suppress("unused")
private fun requestPinShortcut(action: String) {
    // TODO(P2): ShortcutManager.requestPinShortcut(pinShortcutInfo, callback)
}
