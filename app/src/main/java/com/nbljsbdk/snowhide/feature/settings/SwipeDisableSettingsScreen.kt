package com.nbljsbdk.snowhide.feature.settings

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nbljsbdk.snowhide.data.prefs.SettingsRepository
import com.nbljsbdk.snowhide.data.repo.RecentCalibrationRepository
import com.nbljsbdk.snowhide.service.RecentSwipeController

/** Recent 划卡停用三级设置页。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeDisableSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    SettingsRepository.init(context)
    RecentCalibrationRepository.init(context)
    val enabled by SettingsRepository.swipeDisableEnabled.collectAsState()
    val packages by RecentCalibrationRepository.packages.collectAsState()
    val labels = remember(packages) {
        packages.associateWith { pkg ->
            runCatching {
                context.packageManager.getApplicationLabel(
                    context.packageManager.getApplicationInfo(pkg, 0),
                ).toString()
            }.getOrDefault(pkg)
        }
    }

    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("划卡停用", fontWeight = FontWeight.Bold) },
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingCard("功能开关") {
                SwitchSetting("开启划卡停用", enabled) {
                    SettingsRepository.setSwipeDisableEnabled(it)
                }
                Text(
                    text = "每次进入 Recent 重新识别卡片；上滑只记录差异，退出 Recent 后冻结被划掉的已添加且未锁定应用。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            SettingCard("无障碍服务") {
                Text(
                    text = "需要开启“锁屏自动清理保活”无障碍服务。服务会读取 Recent 窗口，锁屏清理只使用其保活能力。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    modifier = Modifier.padding(top = 4.dp),
                ) { Text("打开无障碍设置") }
            }

            SettingCard("Recent 识别") {
                Text(
                    text = if (packages.isEmpty()) {
                        "尚未保存识别结果；首次打开 Recent 会自动识别"
                    } else {
                        "最近识别到 ${packages.size} 个应用"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                packages.forEach { pkg ->
                    Text(
                        text = "${labels[pkg] ?: pkg}\n$pkg",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { RecentSwipeController.requestCalibration(context) }) {
                        Text("手动校准")
                    }
                    TextButton(onClick = { RecentCalibrationRepository.clear() }) {
                        Text("清除识别结果")
                    }
                }
            }
        }
    }
}
