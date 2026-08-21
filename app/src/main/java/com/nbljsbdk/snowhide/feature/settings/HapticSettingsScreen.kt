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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nbljsbdk.snowhide.core.feedback.HapticType
import com.nbljsbdk.snowhide.data.prefs.SettingsRepository

/** 震动反馈三级设置页。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HapticSettingsScreen(onBack: () -> Unit) {
    val enabled by SettingsRepository.hapticEnabled.collectAsState()
    val navigationLevel by SettingsRepository.hapticNavigationLevel.collectAsState()
    val freezeLockLevel by SettingsRepository.hapticFreezeLockLevel.collectAsState()
    val organizeListLevel by SettingsRepository.hapticOrganizeListLevel.collectAsState()
    val batchLevel by SettingsRepository.hapticBatchLevel.collectAsState()

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("震动反馈", fontWeight = FontWeight.Bold) },
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
            SettingCard("总开关") {
                SwitchSetting("开启震动反馈", enabled) {
                    SettingsRepository.setHapticEnabled(it)
                }
                Text(
                    text = "静音、勿扰模式或设备没有震动器时，系统仍会自动静默。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }

            SettingCard("各场景强度") {
                HapticSlider("导航操作", navigationLevel) {
                    SettingsRepository.setHapticLevel(HapticType.NAVIGATION, it)
                }
                HapticSlider("冻结与锁定", freezeLockLevel) {
                    SettingsRepository.setHapticLevel(HapticType.FREEZE_LOCK, it)
                }
                HapticSlider("整理与列表操作", organizeListLevel) {
                    SettingsRepository.setHapticLevel(HapticType.ORGANIZE_LIST, it)
                }
                HapticSlider("批量操作完成", batchLevel) {
                    SettingsRepository.setHapticLevel(HapticType.BATCH, it)
                }
                Text(
                    text = "0=关闭，1=轻，2=中，3=强，4=很强。仅在动作确认或完成时触发。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun HapticSlider(
    label: String,
    level: Int,
    onFinished: (Int) -> Unit,
) {
    var draft by remember(level) { mutableFloatStateOf(level.toFloat()) }
    Column {
        Text("$label：${hapticLevelLabel(draft.toInt())}", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = draft,
            onValueChange = { draft = it },
            onValueChangeFinished = { onFinished(draft.toInt()) },
            valueRange = 0f..4f,
            steps = 3,
        )
    }
}

private fun hapticLevelLabel(level: Int): String = when (level.coerceIn(0, 4)) {
    0 -> "关闭"
    1 -> "轻"
    2 -> "中"
    3 -> "强"
    else -> "很强"
}
