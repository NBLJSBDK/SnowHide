package com.nbljsbdk.snowhide.feature.home

import com.nbljsbdk.snowhide.data.model.AppRuntimeState
import com.nbljsbdk.snowhide.data.model.Folder
import com.nbljsbdk.snowhide.data.model.FolderApp
import com.nbljsbdk.snowhide.data.model.GridItem

/** 主屏 Content 所需的不可变状态快照，不包含 Compose 类型或 Repository。 */
data class HomeUiState(
    val gridItems: List<GridItem>,
    val folders: List<Folder>,
    val folderApps: List<FolderApp>,
    val frozenStates: Map<String, Boolean>,
    val appStates: Map<String, AppRuntimeState>,
    val lockedPackages: Set<String>,
    val labels: Map<String, String>,
    val engineReady: Boolean,
    val shizukuRunning: Boolean,
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
    val animationsEnabled: Boolean,
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
    fun openApp(pkg: String)
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
    fun quickClean()
    fun toggleLock(pkg: String)
    fun renameFolder(folderId: Long, name: String)
    fun deleteFolder(folderId: Long)
    fun removeApp(pkg: String)
    fun uninstallApp(pkg: String)
    fun toggleFreeze(pkg: String)
    fun freezeFolder(folder: Folder)
    fun unfreezeFolder(folder: Folder)
    fun applyIconPack(pkg: String)
    fun setTransparentBg(enabled: Boolean)
    fun setWallpaperOverlay(alpha: Float)
    fun setAnimationsEnabled(enabled: Boolean)
    fun setFreezeStyle(style: String)
    fun setIconShape(shape: String)
    fun setColumns(value: Int)
    fun setIconSize(value: Int)
    fun setVerticalSpace(value: Int)
    fun setDockIconSize(value: Int)
    fun setDockActionIconSize(value: Int)
    fun setFolderPreview(value: Int)
}
