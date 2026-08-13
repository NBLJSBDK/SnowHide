package com.nbljsbdk.snowhide.feature.about

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.nbljsbdk.snowhide.feature.appmanage.AppManageViewModel

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

    val systemUnlocked by appManageViewModel.systemUnlocked.collectAsState()

    // 彩蛋点击计数 + 警告弹窗
    var tapCount by remember { mutableIntStateOf(0) }
    var showWarning by remember { mutableStateOf(false) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于", fontWeight = FontWeight.Bold) },
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
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            Text(
                text = "雪藏",
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
                text = "版本 $versionName",
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
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = if (systemUnlocked) "系统应用按钮：已解锁（再点版本号 7 次关闭）"
                else "彩蛋：点击版本号 7 次解锁系统应用管理",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))
            // ═══════════════════════════════════
            // 神之一手（临时调试：解冻全部已冻结应用，含系统/未列表应用）
            // 之后可能注释掉不用
            // ═══════════════════════════════════
            TextButton(
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

    // 神之一手结果弹窗
    godHandMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { godHandMessage = null },
            title = { Text("神之一手") },
            text = { Text(msg) },
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
