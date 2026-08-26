package com.nbljsbdk.snowhide.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.nbljsbdk.snowhide.data.model.Folder

/**
 * 主屏 Route：接收组合根提供的 ViewModel，并把绘制交给无导航职责的 Content。
 */
@Composable
fun HomeRoute(
    modifier: Modifier = Modifier,
    onRequestShizuku: () -> Unit = {},
    viewModel: HomeViewModel,
) {
    val gridItems by viewModel.gridItems.collectAsState()
    val homeFolderIds by viewModel.homeFolderIds.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val folderApps by viewModel.folderApps.collectAsState()
    val folderPagePlan by viewModel.folderPagePlan.collectAsState()
    val folderPageLoopEnabled by viewModel.folderPageLoopEnabled.collectAsState()
    val excludedFolderIds by viewModel.excludedFolderIds.collectAsState()
    val frozenStates by viewModel.frozenStates.collectAsState()
    val appStates by viewModel.appStates.collectAsState()
    val lockedPackages by viewModel.lockedPackages.collectAsState()
    val labels by viewModel.labels.collectAsState()
    val engineReady by viewModel.engineReady.collectAsState()
    val shizukuRunning by viewModel.shizukuRunning.collectAsState()
    val columns by viewModel.columns.collectAsState()
    val iconSize by viewModel.iconSize.collectAsState()
    val verticalSpace by viewModel.verticalSpace.collectAsState()
    val dockIconSize by viewModel.dockIconSize.collectAsState()
    val dockActionIconSize by viewModel.dockActionIconSize.collectAsState()
    val folderPreview by viewModel.folderPreview.collectAsState()
    val showAppName by viewModel.showAppName.collectAsState()
    val showReturnHomeButton by viewModel.showReturnHomeButton.collectAsState()
    val resetHomeOnReentry by viewModel.resetHomeOnReentry.collectAsState()
    val showReentryToast by viewModel.showReentryToast.collectAsState()
    val autoSyncStatus by viewModel.autoSyncStatus.collectAsState()
    val iconPack by viewModel.iconPack.collectAsState()
    val transparentBg by viewModel.transparentBg.collectAsState()
    val wallpaperOverlay by viewModel.wallpaperOverlay.collectAsState()
    val iconShape by viewModel.iconShape.collectAsState()
    val animationsEnabled by viewModel.animationsEnabled.collectAsState()
    val freezeStyle by viewModel.freezeStyle.collectAsState()
    val message by viewModel.message.collectAsState()
    val batchProgress by viewModel.batchProgress.collectAsState()
    val batchLabel by viewModel.batchLabel.collectAsState()
    val menuOpen by viewModel.menuOpen.collectAsState()
    val organizing by viewModel.organizing.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val state = HomeUiState(
        gridItems = gridItems,
        homeFolderIds = homeFolderIds,
        folders = folders,
        folderApps = folderApps,
        folderPagePlan = folderPagePlan,
        folderPageLoopEnabled = folderPageLoopEnabled,
        excludedFolderIds = excludedFolderIds,
        frozenStates = frozenStates,
        appStates = appStates,
        lockedPackages = lockedPackages,
        labels = labels,
        engineReady = engineReady,
        shizukuRunning = shizukuRunning,
        columns = columns,
        iconSize = iconSize,
        verticalSpace = verticalSpace,
        dockIconSize = dockIconSize,
        dockActionIconSize = dockActionIconSize,
        folderPreview = folderPreview,
        showAppName = showAppName,
        showReturnHomeButton = showReturnHomeButton,
        resetHomeOnReentry = resetHomeOnReentry,
        showReentryToast = showReentryToast,
        autoSyncStatus = autoSyncStatus,
        iconPack = iconPack,
        transparentBg = transparentBg,
        wallpaperOverlay = wallpaperOverlay,
        iconShape = iconShape,
        animationsEnabled = animationsEnabled,
        freezeStyle = freezeStyle,
        message = message,
        batchProgress = batchProgress,
        batchLabel = batchLabel,
        menuOpen = menuOpen,
        organizing = organizing,
        searchQuery = searchQuery,
    )
    val actions = remember(viewModel) { HomeViewModelActions(viewModel) }
    HomeContent(
        modifier = modifier,
        onRequestShizuku = onRequestShizuku,
        state = state,
        actions = actions,
    )
}

private class HomeViewModelActions(
    private val viewModel: HomeViewModel,
) : HomeActions {
    override fun openApp(pkg: String) = viewModel.openApp(pkg)
    override fun consumeMessage() = viewModel.consumeMessage()
    override fun syncActualStatus(silent: Boolean) = viewModel.syncActualStatus(silent)
    override fun refreshFrozenStates() = viewModel.refreshFrozenStates()
    override fun setSearchQuery(query: String) = viewModel.setSearchQuery(query)
    override fun dismissMenu() = viewModel.dismissMenu()
    override fun setOrganizing(enabled: Boolean) = viewModel.setOrganizing(enabled)
    override fun toggleMenu() = viewModel.toggleMenu()
    override fun unfreezeAll() = viewModel.unfreezeAll()
    override fun freezeAll() = viewModel.freezeAll()
    override fun openAppManage() = viewModel.openAppManage()
    override fun openQuickToggle() = viewModel.openQuickToggle()
    override fun openSettings() = viewModel.openSettings()
    override fun openAbout() = viewModel.openAbout()
    override fun refreshEngineStatus() = viewModel.refreshEngineStatus()
    override fun quickClean() = viewModel.quickClean()
    override fun toggleLock(pkg: String) = viewModel.toggleLock(pkg)
    override fun renameFolder(folderId: Long, name: String) = viewModel.renameFolder(folderId, name)
    override fun deleteFolder(folderId: Long) = viewModel.deleteFolder(folderId)
    override fun removeApp(pkg: String) = viewModel.removeApp(pkg)
    override fun uninstallApp(pkg: String) = viewModel.uninstallApp(pkg)
    override fun toggleFreeze(pkg: String) = viewModel.toggleFreeze(pkg)
    override fun freezeFolder(folder: Folder) = viewModel.freezeFolder(folder)
    override fun unfreezeFolder(folder: Folder) = viewModel.unfreezeFolder(folder)
    override fun applyIconPack(pkg: String) = viewModel.applyIconPack(pkg)
    override fun setShowAppName(enabled: Boolean) = viewModel.setShowAppName(enabled)
    override fun setTransparentBg(enabled: Boolean) = viewModel.setTransparentBg(enabled)
    override fun setWallpaperOverlay(alpha: Float) = viewModel.setWallpaperOverlay(alpha)
    override fun setAnimationsEnabled(enabled: Boolean) = viewModel.setAnimationsEnabled(enabled)
    override fun setFreezeStyle(style: String) = viewModel.setFreezeStyle(style)
    override fun setIconShape(shape: String) = viewModel.setIconShape(shape)
    override fun setColumns(value: Int) = viewModel.setColumns(value)
    override fun setIconSize(value: Int) = viewModel.setIconSize(value)
    override fun setVerticalSpace(value: Int) = viewModel.setVerticalSpace(value)
    override fun setDockIconSize(value: Int) = viewModel.setDockIconSize(value)
    override fun setDockActionIconSize(value: Int) = viewModel.setDockActionIconSize(value)
    override fun setFolderPreview(value: Int) = viewModel.setFolderPreview(value)
    override fun setFolderPageLoopEnabled(enabled: Boolean) = viewModel.setFolderPageLoopEnabled(enabled)
    override fun setFolderExcluded(folderId: Long, excluded: Boolean) =
        viewModel.setFolderExcluded(folderId, excluded)
    override fun setShowReturnHomeButton(enabled: Boolean) = viewModel.setShowReturnHomeButton(enabled)
}
