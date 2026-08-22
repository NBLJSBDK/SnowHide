package com.nbljsbdk.snowhide.feature.about

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.nbljsbdk.snowhide.feature.appmanage.AppManageViewModel
import com.nbljsbdk.snowhide.ui.util.FeedbackController

/**
 * 关于页（极简）
 *
 * 彩蛋：连续点击版本号 7 次 → 解锁「系统应用」按钮（警告后持久显示）；
 * 再次 7 次关闭。解锁状态存 AppManageViewModel（与增删界面共享实例）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onClose: () -> Unit,
    appManageViewModel: AppManageViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
            LocalContext.current.applicationContext as Application
        )
    ),
) {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrDefault("1.0")
    }
    var showVersionHistory by remember { mutableStateOf(false) }

    val systemUnlocked by appManageViewModel.systemUnlocked.collectAsState()

    // 彩蛋点击计数 + 警告弹窗
    var tapCount by remember { mutableIntStateOf(0) }
    var showWarning by remember { mutableStateOf(false) }

    // 一键申请权限：依次弹出未授予的运行时权限（用户拍板）
    val permissionLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { }
    fun requestMissingPermissions() {
        val permissions = buildList {
            add(android.Manifest.permission.POST_NOTIFICATIONS)
            // 壁纸读取（透明背景）：Android 13+ 媒体图片权限，旧版外部存储
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                add(android.Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        val missing = permissions.filter {
            context.checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            FeedbackController.toast(context, "权限都已授予")
        }
    }

    // 神之一手（临时调试）：解冻设备上全部已冻结应用
    val godHandScope = rememberCoroutineScope()
    val godHandUseCase = remember {
        com.nbljsbdk.snowhide.domain.FreezeUseCase(
            com.nbljsbdk.snowhide.core.mode.FreezeExecutor(
                com.nbljsbdk.snowhide.core.engine.EngineManager
            ),
            com.nbljsbdk.snowhide.data.repo.GridRepository,
            com.nbljsbdk.snowhide.core.engine.EngineManager,
        )
    }
    var godHandMessage by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onClose)

    if (showVersionHistory) {
        VersionHistoryScreen(onBack = { showVersionHistory = false })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于应用", fontWeight = FontWeight.Bold) },
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
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            Text(
                text = stringResource(com.nbljsbdk.snowhide.R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Android 应用冻结器（Shizuku）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(32.dp))
            // 版本号（彩蛋点击区）
            Text(
                text = "版本 v$versionName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable {
                        tapCount++
                        if (tapCount >= 7) {
                            tapCount = 0
                            if (!systemUnlocked) {
                                showWarning = true // 未解锁 → 警告后解锁
                            } else {
                                appManageViewModel.relockSystemApps() // 已解锁 → 关闭
                            }
                        }
                    }
                    .padding(8.dp),
            )
            Text(
                text = "编译时间：${stringResource(com.nbljsbdk.snowhide.R.string.build_timestamp)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = { showVersionHistory = true },
                modifier = Modifier.padding(top = 4.dp),
            ) { Text("更新说明") }
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = if (systemUnlocked) "系统应用按钮：已解锁（再点版本号 7 次关闭）"
                else "彩蛋：点击版本号 7 次解锁系统应用管理",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))
            // ── 权限说明 ──
            Text(
                text = "权限说明",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "• Shizuku（API_V23）：以 shell 身份执行冻结/解冻命令\n" +
                    "• 查询已装应用：增删应用界面显示应用列表\n" +
                    "• 通知：快捷方式/锁屏清理结果提示（成功用 Toast）\n" +
                    "• 震动：底部栏锁定/解锁反馈（受系统静音控制）\n" +
                    "• 无障碍服务：锁屏自动清理与 Recent 划卡停用；\n" +
                    "  读取最近任务窗口，不读取应用数据\n" +
                    "• 自启动/后台运行：请在系统设置中允许（锁屏清理等后台功能需要）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 一键申请权限：依次弹出未授予的运行时权限（用户拍板）
            TextButton(
                onClick = { requestMissingPermissions() },
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text("一键申请权限", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(modifier = Modifier.height(24.dp))
            // ═══════════════════════════════════
            // 神之一手（正式功能：解冻全部已冻结应用，含系统/未列表应用）
            // ═══════════════════════════════════
            // 批量进度条（神之一手应用最多最卡，进度保护）
            val batchProgress by com.nbljsbdk.snowhide.data.repo.BatchProgress.progress.collectAsState()
            val batchLabel by com.nbljsbdk.snowhide.data.repo.BatchProgress.label.collectAsState()
            if (batchProgress != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "批量$batchLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { batchProgress ?: 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                    )
                }
            }
            TextButton(
                // 批量进行中防重复点击
                enabled = !com.nbljsbdk.snowhide.data.repo.BatchProgress.active,
                onClick = {
                    godHandScope.launch {
                        godHandMessage = godHandUseCase.unfreezeEverything().fold(
                            onSuccess = { "神之一手：已解冻 $it 个应用" },
                            onFailure = { "神之一手失败：${it.message}" },
                        )
                    }
                },
            ) { Text("神之一手") }
        }
    }

    // 神之一手结果弹窗（完整明细可滚动 + 复制按钮，用户拍板）
    godHandMessage?.let { msg ->
        val clipboard = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { godHandMessage = null },
            title = { Text("神之一手") },
            text = {
                Column {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                    TextButton(
                        onClick = {
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(msg))
                            FeedbackController.toast(context, "已复制")
                        },
                    ) { Text("复制") }
                }
            },
            confirmButton = {
                TextButton(onClick = { godHandMessage = null }) { Text("知道了") }
            },
        )
    }

    // 系统应用解锁警告
    if (showWarning) {
        AlertDialog(
            onDismissRequest = { showWarning = false },
            title = { Text("⚠️ 系统应用需要谨慎") },
            text = { Text("冻结系统应用可能影响系统功能（如电话、系统组件），请务必谨慎操作。解锁后增删应用界面将出现「系统应用」切换按钮。") },
            confirmButton = {
                TextButton(onClick = {
                    appManageViewModel.unlockSystemApps()
                    showWarning = false
                }) { Text("我知道了") }
            },
        )
    }
}
