package com.nbljsbdk.snowhide.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nbljsbdk.snowhide.data.prefs.SettingsRepository

/** 提示与反馈三级设置页。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackSettingsScreen(onBack: () -> Unit) {
    val showToast by SettingsRepository.showToast.collectAsState()
    val showReentryToast by SettingsRepository.showReentryToast.collectAsState()

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("提示与反馈", fontWeight = FontWeight.Bold) },
                actions = {
                    Text(
                        text = "返回",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable(onClick = onBack)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingCard("Toast 提示") {
                SwitchSetting("显示操作结果 Toast", showToast) {
                    SettingsRepository.setShowToast(it)
                }
                Text(
                    text = "控制冻结、解冻、批量清理、快捷方式、快速启停等成功或失败结果的短暂弹窗。关闭后不影响进度条、Snackbar 和系统通知。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
                SwitchSetting("重进时提示回到主屏", showReentryToast) {
                    SettingsRepository.setShowReentryToast(it)
                }
                Text(
                    text = "离开雪藏超过 10 秒再次进入时显示；总 Toast 开关关闭时不会显示。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }

            SettingCard("失败反馈") {
                Text(
                    text = "主界面操作失败使用 Snackbar；快捷方式和下拉磁贴等后台操作失败使用系统通知，不会因为关闭 Toast 而完全没有结果。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
