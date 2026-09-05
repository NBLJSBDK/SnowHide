package com.nbljsbdk.snowhide.app

import android.content.Context
import com.nbljsbdk.snowhide.core.engine.EngineManager
import com.nbljsbdk.snowhide.core.accessibility.AccessibilityServiceConnectionState
import com.nbljsbdk.snowhide.core.accessibility.AccessibilityServiceSettingsReader
import com.nbljsbdk.snowhide.core.mode.FreezeExecutor
import com.nbljsbdk.snowhide.data.repo.GridRepository
import com.nbljsbdk.snowhide.data.repo.AppCloneRepository
import com.nbljsbdk.snowhide.data.repo.QuickToggleRepository
import com.nbljsbdk.snowhide.data.repo.BackupRepository
import com.nbljsbdk.snowhide.data.repo.RecentCalibrationRepository
import com.nbljsbdk.snowhide.domain.backup.BackupUseCase
import com.nbljsbdk.snowhide.domain.FreezeUseCase
import com.nbljsbdk.snowhide.domain.QuickToggleUseCase
import com.nbljsbdk.snowhide.data.prefs.SettingsRepository
import com.nbljsbdk.snowhide.domain.folder.FolderPageSettingsUseCase
import com.nbljsbdk.snowhide.domain.recent.RecentCalibrationUseCase
import com.nbljsbdk.snowhide.domain.settings.AppearanceSettingsUseCase
import com.nbljsbdk.snowhide.domain.appclone.AppCloneUseCase
import com.nbljsbdk.snowhide.domain.accessibility.AccessibilityRequirementUseCase
import com.nbljsbdk.snowhide.domain.shortcut.DesktopShortcutCreator
import com.nbljsbdk.snowhide.domain.shortcut.DesktopShortcutMaintenance
import com.nbljsbdk.snowhide.domain.shortcut.DesktopShortcutTargetUseCase
import com.nbljsbdk.snowhide.feature.shortcut.ShortcutActionActivity
import com.nbljsbdk.snowhide.platform.shortcut.AndroidDesktopShortcutCreator
import com.nbljsbdk.snowhide.platform.shortcut.AndroidDynamicShortcutRegistrar
import com.nbljsbdk.snowhide.platform.shortcut.AndroidDesktopShortcutMaintenance
import com.nbljsbdk.snowhide.platform.shortcut.ShortcutIconProvider
import com.nbljsbdk.snowhide.ui.util.AppIconLoader
import com.nbljsbdk.snowhide.data.repo.FrozenStateStore

/**
 * 进程级依赖容器——UseCase 的唯一构造点。
 *
 * 由 [CompositionRoot] 创建并持有；Activity / Service / Receiver /
 * Tile 等 Android 入口只从容器取依赖，禁止在各入口内直接 new UseCase。
 */
class AppContainer(
    private val appContext: Context,
    private val appPackageName: String,
    private val accessibilitySettingsReader: AccessibilityServiceSettingsReader,
) {

    /** 冻结业务入口（主界面、快捷方式、Recent、锁屏清理共用） */
    val freezeUseCase: FreezeUseCase by lazy {
        FreezeUseCase(
            FreezeExecutor(EngineManager),
            GridRepository,
            EngineManager,
            appPackageName,
        )
    }

    /** 快速启停入口（下拉磁贴、App Shortcut 共用） */
    val quickToggleUseCase: QuickToggleUseCase by lazy {
        QuickToggleUseCase(
            GridRepository,
            EngineManager,
            QuickToggleRepository,
            appPackageName,
        )
    }

    /** 备份业务入口（SAF 由 SettingsScreen 适配） */
    val backupUseCase: BackupUseCase by lazy {
        BackupUseCase(BackupRepository)
    }

    /** Recent 校准数据门面（校准触发由 AppShell 注入系统回调）。 */
    val recentCalibrationUseCase: RecentCalibrationUseCase by lazy {
        RecentCalibrationUseCase(RecentCalibrationRepository)
    }

    /** 外观设置入口（图标名称、图标包、背景和冻结视觉）。 */
    val appearanceSettingsUseCase: AppearanceSettingsUseCase by lazy {
        AppearanceSettingsUseCase(SettingsRepository)
    }

    /** 文件夹页面设置入口（循环、排除和返回主屏按钮）。 */
    val folderPageSettingsUseCase: FolderPageSettingsUseCase by lazy {
        FolderPageSettingsUseCase(SettingsRepository)
    }

    /** 应用分身入口：只允许显式用户空间目标，不改变既有 user 0 数据。 */
    val appCloneUseCase: AppCloneUseCase by lazy {
        AppCloneUseCase(EngineManager, AppCloneRepository, appPackageName)
    }

    /** 依赖无障碍功能的提示状态；只提示，不参与冻结能力门禁。 */
    val accessibilityRequirementUseCase: AccessibilityRequirementUseCase by lazy {
        AccessibilityRequirementUseCase(
            SettingsRepository,
            accessibilitySettingsReader,
            AccessibilityServiceConnectionState,
        )
    }

    /** 桌面固定快捷方式适配，使用完整 AppTarget 区分主应用和分身。 */
    val desktopShortcutCreator: DesktopShortcutCreator by lazy {
        AndroidDesktopShortcutCreator(
            appContext,
            ShortcutActionActivity::class.java,
            ShortcutIconProvider { target ->
                AppIconLoader.loadShortcutIcon(target.packageName.value)
            },
            iconShapeProvider = { SettingsRepository.iconShape.value },
        )
    }

    /** 雪藏图标长按菜单的动态快捷方式注册器。 */
    val dynamicShortcutRegistrar: AndroidDynamicShortcutRegistrar by lazy {
        AndroidDynamicShortcutRegistrar(appContext)
    }

    /** 系统集成页的桌面快捷方式清理适配。 */
    val desktopShortcutMaintenance: DesktopShortcutMaintenance by lazy {
        AndroidDesktopShortcutMaintenance(
            appContext,
            EngineManager,
            dynamicShortcutRegistrar,
        )
    }

    /** 桌面快捷方式打开策略：复用宫格目标校验和临时解冻行为。 */
    val desktopShortcutTargetUseCase: DesktopShortcutTargetUseCase by lazy {
        DesktopShortcutTargetUseCase(
            targetStore = GridRepository,
            isFrozen = { target -> FrozenStateStore.targetStates.value[target] == true },
            unfreeze = { target -> freezeUseCase.unfreezeApp(target) },
        )
    }
}
