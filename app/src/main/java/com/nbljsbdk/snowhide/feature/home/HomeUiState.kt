package com.nbljsbdk.snowhide.feature.home

import com.nbljsbdk.snowhide.data.model.AppRuntimeState
import com.nbljsbdk.snowhide.core.model.AppTarget
import com.nbljsbdk.snowhide.data.model.Folder
import com.nbljsbdk.snowhide.data.model.FolderApp
import com.nbljsbdk.snowhide.data.model.GridItem
import com.nbljsbdk.snowhide.domain.accessibility.AccessibilityRequirementState
import com.nbljsbdk.snowhide.domain.folder.FolderPagePlan
import com.nbljsbdk.snowhide.domain.settings.AnimationLevel

/** 主屏 Content 所需的不可变状态快照，不包含 Compose 类型或 Repository。 */
data class HomeUiState(
    val gridItems: List<GridItem>,
    val homeFolderIds: List<Long>,
    val folders: List<Folder>,
    val folderApps: List<FolderApp>,
    val folderPagePlan: FolderPagePlan,
    val folderPageLoopEnabled: Boolean,
    val excludedFolderIds: Set<Long>,
    val frozenStates: Map<AppTarget, Boolean>,
    val pendingFreezeTargets: Set<AppTarget>,
    val appStates: Map<AppTarget, AppRuntimeState>,
    val lockedTargets: Set<AppTarget>,
    val labels: Map<AppTarget, String>,
    val engineReady: Boolean,
    val shizukuRunning: Boolean,
    val accessibilityRequirement: AccessibilityRequirementState,
    val columns: Int,
    val iconSize: Int,
    val verticalSpace: Int,
    val dockIconSize: Int,
    val dockActionIconSize: Int,
    val folderPreview: Int,
    val showAppName: Boolean,
    val showReturnHomeButton: Boolean,
    val resetHomeOnReentry: Boolean,
    val showReentryToast: Boolean,
    val autoSyncStatus: Boolean,
    val iconPack: String,
    val transparentBg: Boolean,
    val wallpaperOverlay: Float,
    val iconShape: String,
    val animationLevel: AnimationLevel,
    val freezeStyle: String,
    val message: String?,
    val batchProgress: Float?,
    val batchLabel: String?,
    val menuOpen: Boolean,
    val organizing: Boolean,
    val searchQuery: String,
)

/** HomeContent 的业务回调端口；具体 ViewModel 只在 Route 层出现。 */
interface HomeActions {
    fun openApp(target: AppTarget)
    fun consumeMessage()
    fun syncActualStatus(silent: Boolean = false)
    fun refreshFrozenStates()
    fun setSearchQuery(query: String)
    fun dismissMenu()
    fun setOrganizing(enabled: Boolean)
    fun toggleMenu()
    fun unfreezeAll()
    fun freezeAll()
    fun openAppManage()
    fun openQuickToggle()
    fun openSettings()
    fun openAbout()
    fun refreshEngineStatus()
    fun refreshAccessibilityStatus()
    fun quickClean()
    fun toggleLock(target: AppTarget)
    fun renameFolder(folderId: Long, name: String)
    fun deleteFolder(folderId: Long)
    fun removeApp(target: AppTarget)
    fun uninstallApp(target: AppTarget)
    fun toggleFreeze(target: AppTarget)
    fun freezeFolder(folder: Folder)
    fun unfreezeFolder(folder: Folder)
    fun applyIconPack(pkg: String)
    fun setShowAppName(enabled: Boolean)
    fun setTransparentBg(enabled: Boolean)
    fun setWallpaperOverlay(alpha: Float)
    fun setAnimationLevel(level: AnimationLevel)
    fun setFreezeStyle(style: String)
    fun setIconShape(shape: String)
    fun setColumns(value: Int)
    fun setIconSize(value: Int)
    fun setVerticalSpace(value: Int)
    fun setDockIconSize(value: Int)
    fun setDockActionIconSize(value: Int)
    fun setFolderPreview(value: Int)
    fun setFolderPageLoopEnabled(enabled: Boolean)
    fun setFolderExcluded(folderId: Long, excluded: Boolean)
    fun setShowReturnHomeButton(enabled: Boolean)
    fun setResetHomeOnReentry(enabled: Boolean)
}
