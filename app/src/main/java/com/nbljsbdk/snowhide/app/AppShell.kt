package com.nbljsbdk.snowhide.app

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nbljsbdk.snowhide.domain.FreezeUseCase
import com.nbljsbdk.snowhide.domain.accessibility.AccessibilityRequirementUseCase
import com.nbljsbdk.snowhide.domain.appclone.AppCloneUseCase
import com.nbljsbdk.snowhide.domain.backup.BackupUseCase
import com.nbljsbdk.snowhide.domain.folder.FolderPageSettingsUseCase
import com.nbljsbdk.snowhide.domain.recent.RecentCalibrationUseCase
import com.nbljsbdk.snowhide.domain.settings.AppearanceSettingsUseCase
import com.nbljsbdk.snowhide.domain.shortcut.DesktopShortcutCreator
import com.nbljsbdk.snowhide.domain.shortcut.DesktopShortcutMaintenance
import com.nbljsbdk.snowhide.feature.about.AboutRoute
import com.nbljsbdk.snowhide.feature.appmanage.AppManageScreen
import com.nbljsbdk.snowhide.feature.appmanage.AppManageViewModel
import com.nbljsbdk.snowhide.feature.home.HomeRoute
import com.nbljsbdk.snowhide.feature.home.HomeViewModel
import com.nbljsbdk.snowhide.feature.quicktoggle.QuickToggleScreen
import com.nbljsbdk.snowhide.feature.settings.SettingsRoute
import com.nbljsbdk.snowhide.service.RecentSwipeController

/**
 * 应用页面组合根。
 *
 * HomeScreen 只绘制主屏；一级覆盖页面统一在这里组合，避免 feature 之间互相
 * import。ViewModel 仍与现有 Activity 生命周期绑定，后续再按 Route 拆分。
 */
@Composable
fun AppShell(
    modifier: Modifier = Modifier,
    onRequestShizuku: () -> Unit = {},
    onOpenAccessibilitySettings: () -> Unit = {},
    backupUseCase: BackupUseCase,
    freezeUseCase: FreezeUseCase,
    recentCalibrationUseCase: RecentCalibrationUseCase,
    appearanceSettingsUseCase: AppearanceSettingsUseCase,
    folderPageSettingsUseCase: FolderPageSettingsUseCase,
    appCloneUseCase: AppCloneUseCase,
    accessibilityRequirementUseCase: AccessibilityRequirementUseCase,
    desktopShortcutCreator: DesktopShortcutCreator,
    desktopShortcutMaintenance: DesktopShortcutMaintenance,
    homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.Factory(
            LocalContext.current.applicationContext as Application,
            freezeUseCase,
            appearanceSettingsUseCase,
            folderPageSettingsUseCase,
            accessibilityRequirementUseCase,
            desktopShortcutCreator,
        )
    ),
    appManageViewModel: AppManageViewModel = viewModel(
        factory = AppManageViewModel.Factory(
            LocalContext.current.applicationContext as Application,
            freezeUseCase,
            appCloneUseCase,
        )
    ),
) {
    val context = LocalContext.current
    val appManageOpen by homeViewModel.appManageOpen.collectAsState()
    val settingsOpen by homeViewModel.settingsOpen.collectAsState()
    val quickToggleOpen by homeViewModel.quickToggleOpen.collectAsState()
    val aboutOpen by homeViewModel.aboutOpen.collectAsState()
    val destination = when {
        appManageOpen -> AppDestination.APP_MANAGE
        settingsOpen -> AppDestination.SETTINGS
        quickToggleOpen -> AppDestination.QUICK_TOGGLE
        aboutOpen -> AppDestination.ABOUT
        else -> AppDestination.HOME
    }
    val systemUnlocked by appManageViewModel.systemUnlocked.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        HomeRoute(
            modifier = Modifier.fillMaxSize(),
            onRequestShizuku = onRequestShizuku,
            onOpenAccessibilitySettings = onOpenAccessibilitySettings,
            viewModel = homeViewModel,
        )

        when (destination) {
            AppDestination.HOME -> Unit
            AppDestination.APP_MANAGE -> AppManageScreen(
                onClose = { homeViewModel.closeAppManage() },
                viewModel = appManageViewModel,
            )
            AppDestination.SETTINGS -> SettingsRoute(
                onClose = { homeViewModel.closeSettings() },
                onSyncStatus = { homeViewModel.syncActualStatus() },
                backupUseCase = backupUseCase,
                recentCalibrationUseCase = recentCalibrationUseCase,
                desktopShortcutMaintenance = desktopShortcutMaintenance,
                onRequestRecentCalibration = {
                    RecentSwipeController.requestCalibration(context)
                },
            )
            AppDestination.QUICK_TOGGLE -> QuickToggleScreen(
                onClose = { homeViewModel.closeQuickToggle() },
            )
            AppDestination.ABOUT -> AboutRoute(
                onClose = { homeViewModel.closeAbout() },
                systemUnlocked = systemUnlocked,
                onUnlockSystemApps = { appManageViewModel.unlockSystemApps() },
                onRelockSystemApps = { appManageViewModel.relockSystemApps() },
                freezeUseCase = freezeUseCase,
            )
        }
    }
}
