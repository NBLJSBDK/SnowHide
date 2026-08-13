package com.nbljsbdk.snowhide.feature.settings

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nbljsbdk.snowhide.data.prefs.SettingsRepository

/**
 * 设置页（设计文档 §3.11 更多选项，P0 子集）
 *
 * - 简单设置：清理后 Toast / 显示应用名 / 退出回目录
 * - 图标包选择器
 * - 壁纸：透明开关（图片选择 P1）
 * - 布局设置已移到主屏长按菜单 → 透明浮框（实时预览）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    viewModel: SettingsViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
            LocalContext.current.applicationContext as Application
        )
    ),
) {
    val settings = viewModel.settings
    val showToast by settings.showToast.collectAsState()
    val showAppName by settings.showAppName.collectAsState()
    val backToDir by settings.backToLastDir.collectAsState()
    val iconPack by settings.iconPack.collectAsState()
    val transparent by settings.transparentBg.collectAsState()
    val iconPacks by viewModel.iconPacks.collectAsState()
    val pickerOpen by viewModel.pickerOpen.collectAsState()

    // 返回键回到主界面（而非退出到桌面）
    BackHandler(onBack = onClose)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("更多选项", fontWeight = FontWeight.Bold) },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── 简单设置 ──
            SettingCard("简单设置") {
                SwitchSetting("清理应用后展示 Toast", showToast) { settings.setShowToast(it) }
                SwitchSetting("显示应用名", showAppName) { settings.setShowAppName(it) }
                SwitchSetting("退出后回到当前目录", backToDir) { settings.setBackToLastDir(it) }
            }

            // ── 美化 ──
            SettingCard("美化") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.openPicker() },
                ) {
                    Text(
                        text = "图标包：${if (iconPack.isEmpty()) "系统默认" else iconPack}",
                        modifier = Modifier.weight(1f),
                    )
                    Text("选择 ▸", color = MaterialTheme.colorScheme.primary)
                }
                SwitchSetting("背景透明（透出壁纸）", transparent) { settings.setTransparentBg(it) }
            }
        }
    }

    // 图标包选择器
    if (pickerOpen) {
        AlertDialog(
            onDismissRequest = { viewModel.closePicker() },
            title = { Text("选择图标包") },
            text = {
                Column {
                    RadioRow(
                        label = "系统默认",
                        selected = iconPack.isEmpty(),
                        onClick = { viewModel.selectIconPack("") },
                    )
                    iconPacks.forEach { pack ->
                        RadioRow(
                            label = pack.label,
                            selected = iconPack == pack.pkg,
                            onClick = { viewModel.selectIconPack(pack.pkg) },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.closePicker() }) { Text("关闭") }
            },
        )
    }
}

/** 设置分组卡片 */
@Composable
private fun SettingCard(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(vertical = 4.dp))
            content()
        }
    }
}

/** 开关设置行 */
@Composable
private fun SwitchSetting(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) },
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Checkbox(checked = checked, onCheckedChange = onChange)
    }
}

/** 单选行（图标包选择器） */
@Composable
private fun RadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}
