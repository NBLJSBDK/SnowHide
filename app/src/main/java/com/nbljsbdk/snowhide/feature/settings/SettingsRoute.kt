package com.nbljsbdk.snowhide.feature.settings

import androidx.compose.runtime.Composable
import com.nbljsbdk.snowhide.domain.backup.BackupUseCase
import com.nbljsbdk.snowhide.domain.recent.RecentCalibrationUseCase

/**
 * 设置页 Route：页面依赖由 AppShell 传入，具体设置绘制留在 Content 层。
 */
@Composable
fun SettingsRoute(
    onClose: () -> Unit,
    backupUseCase: BackupUseCase,
    recentCalibrationUseCase: RecentCalibrationUseCase,
    onRequestRecentCalibration: () -> Unit,
    onSyncStatus: () -> Unit = {},
) {
    SettingsScreen(
        onClose = onClose,
        backupUseCase = backupUseCase,
        recentCalibrationUseCase = recentCalibrationUseCase,
        onRequestRecentCalibration = onRequestRecentCalibration,
        onSyncStatus = onSyncStatus,
    )
}
