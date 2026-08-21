package com.nbljsbdk.snowhide.feature.settings

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nbljsbdk.snowhide.data.prefs.SettingsRepository

/**
 * 设置页（设计文档 §3.11 更多选项，P0 子集）
 *
 * - 简单设置：提示与反馈 / 显示图标名称 / 返回主屏按钮 / 退出回目录
 * - 图标包选择器
 * - 壁纸：透明开关（图片选择 P1）
 * - 布局设置已移到主屏长按菜单 → 透明浮框（实时预览）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    onSyncStatus: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
            LocalContext.current.applicationContext as Application
        )
    ),
) {
    val settings = viewModel.settings
    val showToast by settings.showToast.collectAsState()
    val showAppName by settings.showAppName.collectAsState()
    val showReturnHomeButton by settings.showReturnHomeButton.collectAsState()
    val resetHomeOnReentry by settings.resetHomeOnReentry.collectAsState()
    val autoSyncStatus by settings.autoSyncStatus.collectAsState()
    val hapticEnabled by settings.hapticEnabled.collectAsState()
    val lockCleanEnabled by settings.lockCleanEnabled.collectAsState()
    val lockCleanDelay by settings.lockCleanDelay.collectAsState()
    val lockCleanNotify by settings.lockCleanNotify.collectAsState()

    // 三级菜单：创建快捷方式子屏
    var showShortcutCreate by remember { mutableStateOf(false) }
    var showFeedbackSettings by remember { mutableStateOf(false) }
    var showHapticSettings by remember { mutableStateOf(false) }
    var reentryInfoOpen by remember { mutableStateOf(false) }
    var lockCleanInfoOpen by remember { mutableStateOf(false) }

    // 备份导出/导入提示
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            message = null
        }
    }

    // 导出：SAF 创建文档（无需存储权限）；类型 all/grid/settings 区分内容
    var exportType by remember { mutableStateOf("all") }
    var exportMenuOpen by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val ok = runCatching {
                val json = when (exportType) {
                    "grid" -> com.nbljsbdk.snowhide.data.repo.BackupRepository.exportGrid(context)
                    "settings" -> com.nbljsbdk.snowhide.data.repo.BackupRepository.exportSettings(context)
                    else -> com.nbljsbdk.snowhide.data.repo.BackupRepository.exportBackup(context)
                }
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray())
                } ?: error("无法打开输出流")
            }
            message = ok.fold(
                { "导出成功：${uri.lastPathSegment}" },
                { "导出失败：${it.message}" },
            )
        }
    }

    // 导入：SAF 打开文档；成功后弹「立即重启」确认
    var restartPrompt by remember { mutableStateOf(false) }
    var importedCount by remember { mutableStateOf(0) }
    val importLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val ok = runCatching {
                val json = context.contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                } ?: error("无法读取文件")
                com.nbljsbdk.snowhide.data.repo.BackupRepository.importBackup(context, json)
            }
            ok.onSuccess { n ->
                importedCount = n
                restartPrompt = true
            }.onFailure {
                message = "导入失败：${it.message}"
            }
        }
    }

    // 导入成功 → 重启确认
    if (restartPrompt) {
        AlertDialog(
            onDismissRequest = { restartPrompt = false },
            title = { Text("导入成功") },
            text = { Text("已导入 $importedCount 项数据，是否立即重启应用生效？") },
            confirmButton = {
                TextButton(onClick = {
                    restartPrompt = false
                    // 重建 Activity + 杀旧进程（单例仓库重新加载数据）
                    val intent = android.content.Intent(
                        context,
                        com.nbljsbdk.snowhide.MainActivity::class.java,
                    )
                    intent.addFlags(
                        android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                            android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                    )
                    context.startActivity(intent)
                    android.os.Process.killProcess(android.os.Process.myPid())
                }) { Text("立即重启") }
            },
            dismissButton = {
                TextButton(onClick = { restartPrompt = false }) { Text("稍后") }
            },
        )
    }

    if (reentryInfoOpen) {
        AlertDialog(
            onDismissRequest = { reentryInfoOpen = false },
            title = { Text("重进时回到主屏") },
            text = {
                Text("开启后，离开雪藏超过 10 秒，再次进入会自动回到主屏并关闭搜索状态。是否显示提示可在“提示与反馈”中单独设置。10 秒内返回则保留当前页面。")
            },
            confirmButton = {
                TextButton(onClick = { reentryInfoOpen = false }) { Text("知道了") }
            },
        )
    }

    if (lockCleanInfoOpen) {
        AlertDialog(
            onDismissRequest = { lockCleanInfoOpen = false },
            title = { Text("锁屏自动清理说明") },
            text = {
                Column {
                    Text(
                        text = "息屏后 ${lockCleanDelay} 分钟内未解锁 → 自动智能清理（豁免锁定）；解锁即取消，下次息屏重新计时。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                lockCleanInfoOpen = false
                                context.startActivity(
                                    android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                )
                            }
                            .padding(vertical = 10.dp),
                    ) {
                        Text(
                            text = "开启无障碍保活（推荐，防杀后台）",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        Text("▸", color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { lockCleanInfoOpen = false }) { Text("知道了") }
            },
        )
    }

    // 返回键回到主界面（而非退出到桌面）
    BackHandler(onBack = onClose)

    if (showShortcutCreate) {
        ShortcutCreateScreen(onBack = { showShortcutCreate = false })
        return
    }
    if (showFeedbackSettings) {
        FeedbackSettingsScreen(onBack = { showFeedbackSettings = false })
        return
    }
    if (showHapticSettings) {
        HapticSettingsScreen(onBack = { showHapticSettings = false })
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("更多选项", fontWeight = FontWeight.Bold) },
                actions = {
                    // 右上角返回（用户拍板：返回按钮放右上）
                    Text(
                        text = "返回",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable(onClick = onClose)
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
            // ── 简单设置（基础开关置顶） ──
            SettingCard("简单设置") {
                SwitchSetting("显示图标名称", showAppName) { settings.setShowAppName(it) }
                SwitchSetting("显示返回主屏按钮", showReturnHomeButton) {
                    settings.setShowReturnHomeButton(it)
                }
                SwitchSetting(
                    label = "重进时回到主屏",
                    checked = resetHomeOnReentry,
                    onChange = { settings.setResetHomeOnReentry(it) },
                    onInfo = { reentryInfoOpen = true },
                )
            }

            // ── 提示与反馈（独立三级页入口） ──
            SettingCard("提示与反馈") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showFeedbackSettings = true },
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("操作结果 Toast")
                        Text(
                            text = if (showToast) "已开启" else "已关闭",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("▸", color = MaterialTheme.colorScheme.primary)
                }
            }

            // ── 震动反馈（独立三级页入口） ──
            SettingCard("震动反馈") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showHapticSettings = true },
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("震动反馈设置")
                        Text(
                            text = if (hapticEnabled) "已开启" else "已关闭",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("▸", color = MaterialTheme.colorScheme.primary)
                }
            }

            // ── 状态同步（手动按钮 + 静默自动同步） ──
            SettingCard("状态同步") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("手动同步实际状态", modifier = Modifier.weight(1f))
                    TextButton(onClick = onSyncStatus) { Text("立即同步") }
                }
                SwitchSetting("自动同步状态", autoSyncStatus) {
                    settings.setAutoSyncStatus(it)
                }
                Text(
                    text = "同步系统真实冻结状态和已删除应用；自动同步在回到前台时静默执行。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── 系统集成 ──
            SettingCard("系统集成") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showShortcutCreate = true },
                ) {
                    Text("创建快捷方式", modifier = Modifier.weight(1f))
                    Text("▸", color = MaterialTheme.colorScheme.primary)
                }
            }

            // ── 备份（debug 导出 → release 导入，跨包迁移） ──
            SettingCard("备份") {
                // 导出：气泡菜单（全部/目录/设置），靠右弹出（用户拍板）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { exportMenuOpen = true },
                ) {
                    Text("导出数据", modifier = Modifier.weight(1f))
                    Text("▸", color = MaterialTheme.colorScheme.primary)
                }
                DropdownMenu(
                    expanded = exportMenuOpen,
                    onDismissRequest = { exportMenuOpen = false },
                    modifier = Modifier.width(140.dp),
                    // 靠右弹出：菜单宽 140dp，行宽约 300dp，右移约 160dp
                    offset = androidx.compose.ui.unit.DpOffset(160.dp, 0.dp),
                ) {
                    DropdownMenuItem(
                        text = { Text("导出全部") },
                        onClick = {
                            exportMenuOpen = false
                            exportType = "all"
                            val stamp = java.text.SimpleDateFormat("yyMMddHHmmss", java.util.Locale.US)
                                .format(java.util.Date())
                            exportLauncher.launch("雪藏备份$stamp.json")
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("导出目录") },
                        onClick = {
                            exportMenuOpen = false
                            exportType = "grid"
                            val stamp = java.text.SimpleDateFormat("yyMMddHHmmss", java.util.Locale.US)
                                .format(java.util.Date())
                            exportLauncher.launch("雪藏目录$stamp.json")
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("导出设置") },
                        onClick = {
                            exportMenuOpen = false
                            exportType = "settings"
                            val stamp = java.text.SimpleDateFormat("yyMMddHHmmss", java.util.Locale.US)
                                .format(java.util.Date())
                            exportLauncher.launch("雪藏设置$stamp.json")
                        },
                    )
                }
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(vertical = 2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                ) {
                    Text("导入数据", modifier = Modifier.weight(1f))
                    Text("▸", color = MaterialTheme.colorScheme.primary)
                }
            }

            // ── 锁屏自动清理（用户拍板语义：首次熄屏计时，解锁取消，到时清理一次） ──
            SettingCard("锁屏自动清理") {
                SwitchSetting(
                    label = "锁屏后自动清理",
                    checked = lockCleanEnabled,
                    onChange = { settings.setLockCleanEnabled(it) },
                    onInfo = { lockCleanInfoOpen = true },
                )
                SliderSetting(
                    label = "延迟：${lockCleanDelay} 分钟（0=息屏立即）",
                    value = lockCleanDelay.toFloat(),
                    range = 0f..120f,
                    steps = 11, // 10 分钟一档，共 13 档
                    onValue = { settings.setLockCleanDelay(it.toInt()) },
                )
                SwitchSetting("清理完成通知", lockCleanNotify) { settings.setLockCleanNotify(it) }
            }
        }
    }
}

/** 设置分组卡片 */
@Composable
internal fun SettingCard(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
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

/** 滑条设置行（5 档内整数档位） */
@Composable
private fun SliderSetting(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValue: (Float) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Slider(
            value = value,
            onValueChange = onValue,
            valueRange = range,
            steps = steps,
        )
    }
}

/** 开关设置行 */
@Composable
internal fun SwitchSetting(
    label: String,
    checked: Boolean,
    onInfo: (() -> Unit)? = null,
    onChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) },
    ) {
        Text(label, modifier = Modifier.weight(1f))
        if (onInfo != null) {
            IconButton(onClick = onInfo) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "功能说明",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Checkbox(checked = checked, onCheckedChange = onChange)
    }
}
