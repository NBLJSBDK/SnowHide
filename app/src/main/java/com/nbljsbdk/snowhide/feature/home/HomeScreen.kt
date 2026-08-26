@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.nbljsbdk.snowhide.feature.home

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.tween
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import com.nbljsbdk.snowhide.R
import com.nbljsbdk.snowhide.data.model.AppRuntimeState
import com.nbljsbdk.snowhide.data.model.GridItem
import com.nbljsbdk.snowhide.core.feedback.HapticType
import com.nbljsbdk.snowhide.feature.home.components.FolderScreen
import com.nbljsbdk.snowhide.domain.organize.OrganizeState
import com.nbljsbdk.snowhide.domain.folder.FolderPageOption
import com.nbljsbdk.snowhide.ui.components.OutlinedText
import com.nbljsbdk.snowhide.feature.home.organize.OrganizeOverlay
import com.nbljsbdk.snowhide.feature.home.organize.OrganizeViewModel
import com.nbljsbdk.snowhide.ui.theme.FrostCard
import com.nbljsbdk.snowhide.ui.theme.OrganizeAppHighlight
import com.nbljsbdk.snowhide.ui.theme.OrganizeFolderHighlight
import com.nbljsbdk.snowhide.ui.theme.TianyiBlue
import com.nbljsbdk.snowhide.ui.theme.WarmOrange
import com.nbljsbdk.snowhide.ui.util.frosted
import com.nbljsbdk.snowhide.ui.util.FeedbackController
import com.nbljsbdk.snowhide.ui.util.HapticController

/**
 * 主屏幕（P0，设计文档 §3.2）
 *
 * 结构：顶栏（标题+齿轮+搜索）→ Shizuku 引导卡 → 混排宫格 → 底部图标栏。
 * 无状态组件化：宫格单元/底部栏/菜单均为内部无状态 Composable，逻辑全在 ViewModel。
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HomeContent(
    modifier: Modifier = Modifier,
    onRequestShizuku: () -> Unit = {},
    state: HomeUiState,
    actions: HomeActions,
) {
    val gridItems = state.gridItems
    val folders = state.folders
    val folderApps = state.folderApps
    val frozenStates = state.frozenStates
    val appStates = state.appStates
    val lockedPackages = state.lockedPackages
    var icons by remember { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }
    var loadedIconPack by remember { mutableStateOf<String?>(null) }
    val iconPackages = remember(gridItems, folderApps) {
        (gridItems.mapNotNull { it.pkg } + folderApps.map { it.pkg }).distinct()
    }
    val labels = state.labels
    val engineReady = state.engineReady
    val shizukuRunning = state.shizukuRunning
    val columns = state.columns
    val iconSize = state.iconSize
    val verticalSpace = state.verticalSpace
    val dockIconSize = state.dockIconSize
    val dockActionIconSize = state.dockActionIconSize
    val folderPreview = state.folderPreview
    val showAppName = state.showAppName
    val showReturnHomeButton = state.showReturnHomeButton
    val folderPagePlan = state.folderPagePlan
    val folderPageLoopEnabled = state.folderPageLoopEnabled
    val excludedFolderIds = state.excludedFolderIds
    val resetHomeOnReentry = state.resetHomeOnReentry
    val showReentryToast = state.showReentryToast
    val autoSyncStatus = state.autoSyncStatus
    val message = state.message
    val menuOpen = state.menuOpen
    val organizing = state.organizing
    val searchQuery = state.searchQuery
    var searchOpen by remember { mutableStateOf(false) }

    // 搜索框展开时自动聚焦弹键盘（用户拍板）
    val searchFocusRequester = remember { FocusRequester() }
    LaunchedEffect(searchOpen) {
        if (searchOpen) searchFocusRequester.requestFocus()
    }

    // 整理目录状态机
    val organizeViewModel: OrganizeViewModel = viewModel()
    val organizeState by organizeViewModel.state.collectAsState()
    val organizeFolderApps by organizeViewModel.currentFolderApps.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 长按菜单状态
    var longPressTarget by remember { mutableStateOf<GridItem?>(null) }
    var invalidAppTarget by remember { mutableStateOf<String?>(null) }
    fun appLocation(pkg: String): String {
        if (gridItems.any { it.type == "app" && it.pkg == pkg }) {
            return "主屏幕"
        }
        val folderId = folderApps.firstOrNull { it.pkg == pkg }?.folderId
        return folders.firstOrNull { it.id == folderId }?.name ?: "主屏幕"
    }
    fun handleAppClick(pkg: String) {
        when (appStates[pkg]) {
            AppRuntimeState.MISSING, AppRuntimeState.UNKNOWN -> invalidAppTarget = pkg
            else -> actions.openApp(pkg)
        }
    }
    // 文件夹重命名/删除弹窗状态
    var renameFolder by remember { mutableStateOf<com.nbljsbdk.snowhide.data.model.Folder?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteFolderTarget by remember { mutableStateOf<com.nbljsbdk.snowhide.data.model.Folder?>(null) }
    // 移除应用二级菜单 / 卸载二次确认
    var removeAppTarget by remember { mutableStateOf<String?>(null) }
    var uninstallTarget by remember { mutableStateOf<String?>(null) }
    // 布局设置透明浮框 / 主屏空白长按菜单 / 美化浮框
    var layoutPanelOpen by remember { mutableStateOf(false) }
    var blankMenuOpen by remember { mutableStateOf(false) }
    var beautyPanelOpen by remember { mutableStateOf(false) }
    var folderPagePanelOpen by remember { mutableStateOf(false) }
    var returnHomeAfterFolderSetting by remember { mutableStateOf(false) }
    var currentFolderId by remember { mutableStateOf<Long?>(null) }
    var directFolderId by remember { mutableStateOf<Long?>(null) }

    val sortedFolders = folderPagePlan.folderIds.mapNotNull { id -> folders.firstOrNull { it.id == id } }
    val actualCount = folderPagePlan.pageCount
    val directFolder = directFolderId?.let { id -> folders.firstOrNull { it.id == id } }
    val folderPageOptions = remember(folders) {
        folders.map { FolderPageOption(it.id, it.name, it.sortOrder) }
    }
    LaunchedEffect(directFolderId, folders) {
        if (directFolderId != null && directFolder == null) directFolderId = null
    }

    // 美化浮框数据：当前图标包/透明开关 + 已装图标包列表
    val iconPack = state.iconPack
    val transparentBg = state.transparentBg
    val wallpaperOverlay = state.wallpaperOverlay
    val iconShape = state.iconShape
    val animationLevel = state.animationLevel
    val animationDurationMillis = animationLevel.durationMillis
    val freezeStyleName = state.freezeStyle
    val freezeStyle = com.nbljsbdk.snowhide.ui.util.FreezeStyle.entries
        .firstOrNull { it.name == freezeStyleName } ?: com.nbljsbdk.snowhide.ui.util.FreezeStyle.BLUE
    var iconPacks by remember { mutableStateOf<List<com.nbljsbdk.snowhide.ui.util.AppIconLoader.IconPackInfo>>(emptyList()) }
    var iconPacksLoading by remember { mutableStateOf(false) }
    LaunchedEffect(iconPack, iconPackages) {
        com.nbljsbdk.snowhide.ui.util.AppIconLoader.iconPackPkg = iconPack
        if (loadedIconPack != iconPack) {
            icons = emptyMap()
            loadedIconPack = iconPack
        }
        com.nbljsbdk.snowhide.ui.util.AppIconLoader.prewarm()
        val current = icons
        val loaded = iconPackages
            .filterNot { it in current }
            .map { pkg ->
                async {
                    pkg to runCatching {
                        com.nbljsbdk.snowhide.ui.util.AppIconLoader.loadIcon(pkg)
                    }.getOrNull()
                }
            }
            .awaitAll()
        icons = current
            .filterKeys { it in iconPackages }
            .toMutableMap()
            .apply { loaded.forEach { (pkg, icon) -> if (icon != null) put(pkg, icon) } }
    }
    LaunchedEffect(beautyPanelOpen) {
        // 打开时若列表为空才扫描（AppIconLoader 内部有扫描缓存，秒回）
        if (beautyPanelOpen && iconPacks.isEmpty()) {
            iconPacksLoading = true
            iconPacks = com.nbljsbdk.snowhide.ui.util.AppIconLoader.queryIconPacks()
            iconPacksLoading = false
        }
    }
    // 手动刷新图标包列表（用户拍板：不每次自动重扫，需要时点刷新）
    fun refreshIconPacks() {
        com.nbljsbdk.snowhide.ui.util.AppIconLoader.clearScanCache()
        scope.launch {
            iconPacksLoading = true
            iconPacks = com.nbljsbdk.snowhide.ui.util.AppIconLoader.queryIconPacks()
            iconPacksLoading = false
        }
    }

    // 整理目录提示事件 → Snackbar
    val organizeEvent by organizeViewModel.events.collectAsState()
    LaunchedEffect(organizeEvent) {
        organizeEvent?.let {
            snackbarHostState.showSnackbar(it)
            organizeViewModel.consumeEvent()
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            actions.consumeMessage()
        }
    }

    LaunchedEffect(engineReady, autoSyncStatus) {
        if (engineReady) {
            if (autoSyncStatus) actions.syncActualStatus(silent = true)
            else actions.refreshFrozenStates()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 透明背景：系统壁纸已由窗口层透出（FLAG_SHOW_WALLPAPER），
        // 这里只盖遮罩层控制可读性（用户拍板：遮罩浓度拉杆）
        if (transparentBg) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.White.copy(alpha = wallpaperOverlay)),
            )
        }
        // 循环滑动（设计文档 §3.2）：
        // 页面序列 = [主屏, 文件夹1, 文件夹2, ...]（文件夹顺序跟随主屏混排）
        // 页面计划由 HomeViewModel 组合 domain 规划器和持久化设置得到。
        val pagerState = rememberPagerState(
            initialPage = if (folderPageLoopEnabled) LOOP_BASE * actualCount else 0,
        ) {
            if (folderPageLoopEnabled) LOOP_TOTAL * actualCount else actualCount
        }
        // 当前页索引（0=主屏）：顶栏动态显示主屏「雪藏」/ 文件夹名
        val pageIdx = folderPagePlan.logicalIndex(pagerState.currentPage)
        val inFolder = directFolder != null || (!organizing && pageIdx != 0)
        LaunchedEffect(pageIdx, folderPagePlan.folderIds, directFolderId) {
            currentFolderId = directFolder?.id ?: folderPagePlan.folderIds.getOrNull(pageIdx - 1)
        }
        val lifecycleOwner = context as androidx.lifecycle.LifecycleOwner
        DisposableEffect(
            lifecycleOwner,
            resetHomeOnReentry,
            showReentryToast,
            actualCount,
            autoSyncStatus,
            engineReady,
            animationLevel,
        ) {
            var stoppedAt = 0L
            var hasStarted = false
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> {
                        stoppedAt = SystemClock.elapsedRealtime()
                    }
                    Lifecycle.Event.ON_START -> {
                        val awayFor = if (stoppedAt == 0L) {
                            0L
                        } else {
                            SystemClock.elapsedRealtime() - stoppedAt
                        }
                        stoppedAt = 0L
                        if (resetHomeOnReentry && awayFor >= REENTRY_HOME_DELAY_MS) {
                            searchOpen = false
                            actions.setSearchQuery("")
                            actions.dismissMenu()
                            scope.launch { pagerState.animateHome(actualCount, animationDurationMillis) }
                            FeedbackController.toast(
                                context,
                                "${context.getString(com.nbljsbdk.snowhide.R.string.app_name)}离开超过10秒，已回到主屏幕",
                                enabled = showReentryToast,
                            )
                        }
                        if (hasStarted && autoSyncStatus && engineReady) {
                            actions.syncActualStatus(silent = true)
                        }
                        hasStarted = true
                    }
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when {
                            organizing -> "整理目录"
                            directFolder != null -> directFolder.name
                            inFolder -> sortedFolders[pageIdx - 1].name
                            else -> stringResource(com.nbljsbdk.snowhide.R.string.app_name)
                        },
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    if (organizing) {
                        // 整理操作即时生效；确认按钮只负责退出整理模式
                        Text(
                            text = "确认",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable {
                                    organizeViewModel.commitFolderName()
                                     actions.setOrganizing(false)
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    } else if (searchOpen) {
                        // 搜索框（过滤宫格；展开时自动聚焦弹键盘）
                        OutlinedTextField(
                            value = searchQuery,
                             onValueChange = { actions.setSearchQuery(it) },
                            placeholder = { Text("搜索应用") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 4.dp)
                                .focusRequester(searchFocusRequester),
                        )
                        TextButton(onClick = {
                            searchOpen = false
                             actions.setSearchQuery("")
                        }) { Text("取消") }
                    } else {
                        IconButton(onClick = { searchOpen = true }) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "搜索",
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                         IconButton(onClick = { actions.toggleMenu() }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "设置",
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        GearMenu(
                            expanded = menuOpen,
                            onDismiss = { actions.dismissMenu() },
                            onOrganize = {
                                organizeViewModel.enter(
                                    directFolder?.id ?: if (!organizing && pageIdx != 0) {
                                        sortedFolders.getOrNull(pageIdx - 1)?.id
                                    } else {
                                        null
                                    },
                                )
                                 actions.setOrganizing(true)
                            },
                             onUnfreezeAll = { actions.unfreezeAll() },
                             onFreezeAll = { actions.freezeAll() },
                             onAppManage = { actions.openAppManage() },
                             onQuickToggle = { actions.openQuickToggle() },
                             onSettings = { actions.openSettings() },
                             onAbout = { actions.openAbout() },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (transparentBg) androidx.compose.ui.graphics.Color.Transparent
                    else MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        snackbarHost = {
            // 固定停在 dock 栏上方（用户拍板：toast 不挡栏位）
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = dockIconSize.dp + 20.dp),
            )
        },
        containerColor = if (transparentBg) androidx.compose.ui.graphics.Color.Transparent
        else MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        // 返回键处理：
        // ① 搜索框展开 → 关闭搜索并清空（等同「取消」按钮）
        // ② 整理目录模式 → 退出整理，所有操作已经即时生效
        // ③ 文件夹页 → 滑回主屏页
        // ④ 主屏非整理 → 默认行为（退出 App）
        BackHandler(
            enabled = searchOpen || organizing || directFolderId != null ||
                pagerState.currentPage % actualCount != 0,
        ) {
            if (searchOpen) {
                searchOpen = false
                 actions.setSearchQuery("")
            } else if (organizing) {
                organizeViewModel.commitFolderName()
                actions.setOrganizing(false)
            } else if (directFolderId != null) {
                directFolderId = null
            } else {
                scope.launch { pagerState.animateHome(actualCount, animationDurationMillis) }
            }
        }

        // 文件夹数量变化时的页面锚点对齐：
        // PagerState 是 rememberSaveable 不重建，currentPage 可能偏离基准倍数，
        // actualCount 变化会让取模结果漂到别的文件夹页（scrollHome 瞬时对齐）。
        // - 整理模式：锁定主屏
        // - 非整理：删除文件夹的唯一入口是主屏长按（用户本在主屏），漂移即对齐回主屏
        LaunchedEffect(organizing, actualCount, folderPageLoopEnabled) {
            val needsHome = if (folderPageLoopEnabled) {
                pagerState.currentPage % actualCount != 0
            } else {
                pagerState.currentPage >= actualCount
            }
            if (returnHomeAfterFolderSetting || organizing || needsHome) {
                returnHomeAfterFolderSetting = false
                if (folderPageLoopEnabled) pagerState.scrollHome(actualCount)
                else pagerState.scrollToPage(0)
            }
        }

        // 搜索有词时自动回主屏显示结果（结果只在主屏宫格渲染）
        LaunchedEffect(searchQuery) {
            if (searchQuery.isNotBlank()) {
                pagerState.animateHome(actualCount, animationDurationMillis)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
        HorizontalPager(
            state = pagerState,
            // 只有一个主屏（没有文件夹）时禁用滑动；搜索/整理期间锁定主屏
            userScrollEnabled = directFolderId == null &&
                !organizing && sortedFolders.isNotEmpty() && searchQuery.isBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            // 整理模式强制渲染主屏：创建/删除文件夹的瞬间 actualCount 已变而
            // currentPage 还未对齐，取模结果会漂到文件夹页造成闪动。
            val idx = if (organizing) 0 else folderPagePlan.logicalIndex(page)
            if (directFolderId != null) {
                directFolder?.let { folder ->
                    FolderScreen(
                        folder = folder,
                        memberPackages = folderApps
                            .filter { it.folderId == folder.id }
                            .sortedBy { it.sortOrder }
                            .map { it.pkg },
                        icons = icons,
                        frozenStates = frozenStates,
                        columns = columns,
                        iconSize = iconSize.dp,
                        verticalSpace = verticalSpace,
                        freezeStyle = freezeStyle,
                        frostAnimationDurationMillis = animationDurationMillis,
                        iconShape = iconShape,
                        showAppName = showAppName,
                        appStates = appStates,
                        showReturnHomeButton = showReturnHomeButton,
                        onBackToHome = { directFolderId = null },
                        onAppClick = { handleAppClick(it) },
                        onAppLongClick = { item -> longPressTarget = item },
                        onAppLabel = { pkg -> labels[pkg] ?: "" },
                        onBlankLongPress = { blankMenuOpen = true },
                    )
                }
            } else if (idx == 0) {
                // ── 主屏页 ──
                Column(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // Shizuku 引导卡：未运行 → 打开 Shizuku；未授权 → 授权
                    if (!engineReady) {
                        ShizukuGuideCard(
                            running = shizukuRunning,
                            onRequest = onRequestShizuku,
                             onRefresh = { actions.refreshEngineStatus() },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }

                    // 混排宫格
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                             // 空白处长按 → 宫格设置菜单（用户拍板）
                            .combinedClickable(
                                onClick = {},
                                onLongClick = { blankMenuOpen = true },
                            )
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(verticalSpace.dp),
                    ) {
                        val searchFiltered = if (searchQuery.isBlank()) {
                            gridItems.sortedBy { it.sortOrder }
                        } else {
                            // 搜索范围 = 主屏项 + 文件夹内应用（文件夹成员也能搜到，结果点击直接打开）
                            // 成员 id 用负数索引绝对唯一（hash 32 位会碰撞导致 LazyGrid key 重复崩溃）
                            val memberItems = folderApps.mapIndexed { index, fa ->
                                GridItem(
                                    id = Long.MIN_VALUE + index,
                                    type = "app",
                                    pkg = fa.pkg,
                                    sortOrder = Int.MAX_VALUE,
                                )
                            }
                            (gridItems + memberItems).filter { item ->
                                val name = item.pkg?.let { labels[it] ?: it } ?: folders.find { f -> f.id == item.folderId }?.name ?: ""
                                name.contains(searchQuery, ignoreCase = true) ||
                                    (item.pkg?.contains(searchQuery, ignoreCase = true) == true)
                            }.sortedBy { it.sortOrder }
                        }
                        items(searchFiltered, key = { it.id }) { item ->
                            when {
                                item.type == "folder" -> {
                                    val folder = folders.find { it.id == item.folderId }
                                    if (folder != null) {
                                        FolderCell(
                                            folderId = folder.id,
                                            name = folder.name,
                                            size = iconSize.dp,
                                            previewPackages = folderApps
                                                .filter { it.folderId == folder.id }
                                                .sortedBy { it.sortOrder }
                                                .map { it.pkg }
                                                .take(if (folderPreview >= 3) 9 else 4),
                                            icons = icons,
                                            frozenStates = frozenStates,
                                            appStates = appStates,
                                            freezeStyle = freezeStyle,
                                            frostAnimationDurationMillis = animationDurationMillis,
                                            previewSize = folderPreview,
                                            iconShape = iconShape,
                                            showName = showAppName,
                                            selectionColor = if (organizing &&
                                                 organizeState is OrganizeState.FolderSelected &&
                                                 (organizeState as OrganizeState.FolderSelected).folderId == folder.id
                                            ) OrganizeFolderHighlight else null,
                                            onClick = {
                                                if (organizing) {
                                                    organizeViewModel.tapFolder(folder)
                                                } else {
                                                    // 跳到该文件夹页（循环内当前位置的相邻页）
                                                     val folderIndex = sortedFolders.indexOfFirst { it.id == folder.id }
                                                     if (folderIndex >= 0) {
                                                          HapticController.vibrate(context, HapticType.NAVIGATION)
                                                          scope.launch {
                                                              pagerState.jumpToFolder(
                                                                  actualCount,
                                                                  folderIndex,
                                                                  animationDurationMillis,
                                                              )
                                                          }
                                                     } else {
                                                         // 被排除的文件夹仍可从主屏直接打开，但不加入左右滑动页面。
                                                         HapticController.vibrate(context, HapticType.NAVIGATION)
                                                         directFolderId = folder.id
                                                     }
                                                }
                                            },
                                            onLongPress = { longPressTarget = item },
                                        )
                                    }
                                }
                                item.pkg != null -> {
                                    AppCell(
                                        pkg = item.pkg,
                                        label = labels[item.pkg] ?: "",
                                        size = iconSize.dp,
                                        frozen = frozenStates[item.pkg] == true,
                                        missing = appStates[item.pkg] == AppRuntimeState.MISSING,
                                         icon = icons[item.pkg],
                                         showName = showAppName,
                                         freezeStyle = freezeStyle,
                                         frostAnimationDurationMillis = animationDurationMillis,
                                         iconShape = iconShape,
                                        selectionColor = if (organizing) when (val s = organizeState) {
                                             is OrganizeState.HomeAppSelected ->
                                                 if (s.app.id == item.id) OrganizeAppHighlight else null
                                             is OrganizeState.FolderSelected ->
                                                 if (s.subHomeApp?.id == item.id) OrganizeAppHighlight else null
                                            else -> null
                                        } else null,
                                        onClick = {
                                            if (organizing) organizeViewModel.tapHomeApp(item)
                                            else handleAppClick(item.pkg)
                                        },
                                        onLongPress = {
                                            if (!organizing) longPressTarget = item
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // ── 文件夹页（全屏，循环滑动的一页） ──
                val folder = sortedFolders[idx - 1]
                FolderScreen(
                    folder = folder,
                    memberPackages = folderApps
                        .filter { it.folderId == folder.id }
                        .sortedBy { it.sortOrder }
                        .map { it.pkg },
                    icons = icons,
                    frozenStates = frozenStates,
                    columns = columns,
                    iconSize = iconSize.dp,
                    verticalSpace = verticalSpace,
                    freezeStyle = freezeStyle,
                    frostAnimationDurationMillis = animationDurationMillis,
                    iconShape = iconShape,
                    showAppName = showAppName,
                    appStates = appStates,
                    showReturnHomeButton = showReturnHomeButton,
                    onBackToHome = {
                        HapticController.vibrate(context, HapticType.NAVIGATION)
                        scope.launch {
                            pagerState.animateHome(actualCount, animationDurationMillis)
                        }
                    },
                    onAppClick = { handleAppClick(it) },
                    onAppLongClick = { item -> longPressTarget = item },
                    onAppLabel = { pkg -> labels[pkg] ?: "" },
                    onBlankLongPress = { blankMenuOpen = true },
                )
            }
        }

        // 批量操作进度条（停用/启用全部、智能清理、神之一手、快速启停）
        val batchProgress = state.batchProgress
        val batchLabel = state.batchLabel
        if (batchProgress != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (transparentBg) androidx.compose.ui.graphics.Color.Transparent
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    .padding(horizontal = 12.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "批量$batchLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { batchProgress ?: 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                )
            }
        }

        // 底部：整理模式显示操作区，否则底部图标栏
        // （用户拍板：主桌面与文件夹页都显示 dock 栏）
        if (organizing) {
            OrganizeOverlay(
                state = organizeState,
                folders = folders,
                folderApps = organizeFolderApps,
                icons = icons,
                iconShape = iconShape,
                transparentBg = transparentBg,
                onTapHomeApp = { item -> organizeViewModel.tapHomeApp(item) },
                onTapFolder = { folder -> organizeViewModel.tapFolder(folder) },
                onTapFolderApp = { pkg -> organizeViewModel.tapFolderApp(pkg) },
                onShift = { step ->
                    organizeViewModel.shift(step)
                    HapticController.vibrate(context, HapticType.ORGANIZE_LIST)
                },
                onMoveUp = {
                    organizeViewModel.moveUp()
                    HapticController.vibrate(context, HapticType.ORGANIZE_LIST)
                },
                onMoveDown = {
                    organizeViewModel.moveDown()
                    HapticController.vibrate(context, HapticType.ORGANIZE_LIST)
                },
                onCreate = {
                    organizeViewModel.createFolder()
                    HapticController.vibrate(context, HapticType.ORGANIZE_LIST)
                },
                onDelete = {
                    organizeViewModel.requestDeleteFolder()
                    HapticController.vibrate(context, HapticType.ORGANIZE_LIST)
                },
                onNameChange = { name -> organizeViewModel.updateFolderName(name) },
                onNameCommit = { organizeViewModel.commitFolderName() },
                onAppLabel = { pkg -> labels[pkg] ?: "" },
            )
        } else {
            DockBar(
                packages = dockPackages(gridItems, folderApps, frozenStates, appStates),
                lockedPackages = lockedPackages,
                icons = icons,
                iconSize = dockIconSize.dp,
                actionIconSize = dockActionIconSize.dp,
                iconShape = iconShape,
                transparentBg = transparentBg,
                animationDurationMillis = animationDurationMillis,
                onQuickClean = { actions.quickClean() },
                onAppClick = { handleAppClick(it) },
                onAppLongClick = { pkg ->
                     actions.toggleLock(pkg)
                    HapticController.vibrate(context, HapticType.FREEZE_LOCK)
                },
                 onAppSwipeUp = { pkg -> actions.toggleFreeze(pkg) },
            )
        }
        }
    }
    }

    // 文件夹重命名对话框（自动聚焦+全选原名，用户拍板）
    renameFolder?.let { folder ->
        val renameFocusRequester = remember { FocusRequester() }
        var renameSelectAll by remember(folder.id) { mutableStateOf(true) }
        LaunchedEffect(folder.id) { renameFocusRequester.requestFocus() }
        AlertDialog(
            onDismissRequest = { renameFolder = null },
            title = { Text("重命名文件夹") },
            text = {
                OutlinedTextField(
                    value = androidx.compose.ui.text.input.TextFieldValue(
                        renameText,
                        selection = if (renameSelectAll) androidx.compose.ui.text.TextRange(0, renameText.length)
                        else androidx.compose.ui.text.TextRange(renameText.length),
                    ),
                    onValueChange = {
                        renameSelectAll = false
                        renameText = it.text
                    },
                    singleLine = true,
                    modifier = Modifier
                        .focusRequester(renameFocusRequester)
                        .onFocusChanged { renameSelectAll = true },
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    if (renameText.isNotBlank()) {
                         actions.renameFolder(folder.id, renameText.trim())
                    }
                    renameFolder = null
                }) { Text("确定") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { renameFolder = null }) { Text("取消") }
            },
        )
    }

    // 文件夹删除二次确认
    deleteFolderTarget?.let { folder ->
        AlertDialog(
            onDismissRequest = { deleteFolderTarget = null },
            title = { Text("删除文件夹") },
            text = { Text("删除「${folder.name}」？其中应用将移回主屏幕。") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                     actions.deleteFolder(folder.id)
                    deleteFolderTarget = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { deleteFolderTarget = null }) { Text("取消") }
            },
        )
    }

    // 移除应用二级菜单（设计文档 §3.4）
    removeAppTarget?.let { pkg ->
        AlertDialog(
            onDismissRequest = { removeAppTarget = null },
            title = { Text("移除应用") },
            text = {
                Column {
                    DialogAction("是否移除该应用（解冻并移出）") {
                         actions.removeApp(pkg)
                        removeAppTarget = null
                    }
                    DialogAction("移除并卸载（⚠️ 会删除应用数据）") {
                        removeAppTarget = null
                        uninstallTarget = pkg
                    }
                    DialogAction("取消") { removeAppTarget = null }
                }
            },
            confirmButton = {},
        )
    }

    // 卸载二次确认（安全特例）
    uninstallTarget?.let { pkg ->
        AlertDialog(
            onDismissRequest = { uninstallTarget = null },
            title = { Text("确认卸载") },
            text = { Text("确定要卸载 ${labels[pkg] ?: pkg} 吗？\n\n⚠️ 这会删除该应用及其全部数据，且不可恢复。") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                     actions.uninstallApp(pkg)
                    uninstallTarget = null
                }) { Text("卸载", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { uninstallTarget = null }) { Text("取消") }
            },
        )
    }

    // 系统实际状态异常：已删除或暂时无法确认时，点击只提示，不尝试启动
    invalidAppTarget?.let { pkg ->
        val missing = appStates[pkg] == AppRuntimeState.MISSING
        AlertDialog(
            onDismissRequest = { invalidAppTarget = null },
            title = { Text(if (missing) "应用已不存在" else "无法确认应用状态") },
            text = {
                Text(
                    if (missing) {
                        "${labels[pkg] ?: pkg} 已被系统删除，请在长按菜单中移除雪藏记录。"
                    } else {
                        "${labels[pkg] ?: pkg} 的冻结状态暂时无法确认，请先同步状态或检查 Shizuku。"
                    }
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { invalidAppTarget = null }) {
                    Text("知道了")
                }
            },
        )
    }

    // 长按上下文菜单
    longPressTarget?.let { target ->
        ContextMenu(
            item = target,
            frozen = target.pkg?.let { frozenStates[it] == true } ?: false,
            folderName = folders.find { it.id == target.folderId }?.name ?: "",
            appLabel = target.pkg?.let { labels[it] ?: it } ?: "",
            locationName = target.pkg?.let(::appLocation) ?: "",
            targetFolder = folders.find { it.id == target.folderId },
            onDismiss = { longPressTarget = null },
             onToggleFreeze = { pkg -> actions.toggleFreeze(pkg); longPressTarget = null },
            onOpen = { pkg -> handleAppClick(pkg); longPressTarget = null },
            onRemove = { pkg ->
                removeAppTarget = pkg
                longPressTarget = null
            },
            onRenameFolder = { id ->
                val folder = folders.find { it.id == id }
                if (folder != null) {
                    renameFolder = folder
                    renameText = folder.name
                }
                longPressTarget = null
            },
            onDeleteFolder = { id ->
                val folder = folders.find { it.id == id }
                if (folder != null) deleteFolderTarget = folder
                longPressTarget = null
            },
            onFreezeFolder = { folder ->
                 actions.freezeFolder(folder); longPressTarget = null
            },
            onUnfreezeFolder = { folder ->
                 actions.unfreezeFolder(folder); longPressTarget = null
            },
        )
    }

    // 任意宫格空白处长按菜单：三类宫格设置
    if (blankMenuOpen) {
        AlertDialog(
            onDismissRequest = { blankMenuOpen = false },
            title = { Text("宫格设置") },
            text = {
                Column {
                    DialogAction("布局设置") {
                        blankMenuOpen = false
                        layoutPanelOpen = true
                    }
                    DialogAction("美化设置") {
                        blankMenuOpen = false
                        beautyPanelOpen = true
                    }
                    DialogAction("目录设置") {
                        blankMenuOpen = false
                        folderPagePanelOpen = true
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { blankMenuOpen = false }) {
                    Text("取消")
                }
            },
        )
    }

    // 美化设置透明浮框（与布局设置同款，设计文档 §3.11）
    if (beautyPanelOpen) {
        BeautyPanel(
            iconPack = iconPack,
            transparentBg = transparentBg,
            wallpaperOverlay = wallpaperOverlay,
            animationLevel = animationLevel,
            showAppName = showAppName,
            freezeStyle = freezeStyle,
            iconPacks = iconPacks,
            iconPacksLoading = iconPacksLoading,
            onRefreshIconPacks = { refreshIconPacks() },
            iconShape = iconShape,
            onIconPackSelect = { pkg -> actions.applyIconPack(pkg) },
            onTransparentToggle = { on -> actions.setTransparentBg(on) },
            onWallpaperOverlayChange = { alpha -> actions.setWallpaperOverlay(alpha) },
            onAnimationLevelChange = { level -> actions.setAnimationLevel(level) },
            onShowAppNameChange = { on -> actions.setShowAppName(on) },
            onFreezeStyleSelect = { style -> actions.setFreezeStyle(style.name) },
            onIconShapeSelect = { shape -> actions.setIconShape(shape) },
            onComplete = { beautyPanelOpen = false },
            onBackToMenu = {
                beautyPanelOpen = false
                blankMenuOpen = true
            },
        )
    }

    if (folderPagePanelOpen) {
        FolderPagePanel(
            folders = folderPageOptions,
            loopEnabled = folderPageLoopEnabled,
            excludedFolderIds = excludedFolderIds,
            showReturnHomeButton = showReturnHomeButton,
            resetHomeOnReentry = resetHomeOnReentry,
            onLoopEnabledChange = { enabled -> actions.setFolderPageLoopEnabled(enabled) },
            onFolderExcludedChange = { folderId, excluded ->
                if (excluded && currentFolderId == folderId) {
                    returnHomeAfterFolderSetting = true
                }
                actions.setFolderExcluded(folderId, excluded)
            },
            onShowReturnHomeButtonChange = { enabled -> actions.setShowReturnHomeButton(enabled) },
            onResetHomeOnReentryChange = { enabled -> actions.setResetHomeOnReentry(enabled) },
            onComplete = { folderPagePanelOpen = false },
            onBackToMenu = {
                folderPagePanelOpen = false
                blankMenuOpen = true
            },
        )
    }

    // 布局设置透明浮框（实时生效，设计文档 §3.5）
    if (layoutPanelOpen) {
        LayoutPanel(
            columns = columns,
            iconSize = iconSize,
            verticalSpace = verticalSpace,
            dockIconSize = dockIconSize,
            dockActionIconSize = dockActionIconSize,
            folderPreview = folderPreview,
             onColumnsChange = { actions.setColumns(it) },
             onIconSizeChange = { actions.setIconSize(it) },
             onVerticalSpaceChange = { actions.setVerticalSpace(it) },
             onDockIconSizeChange = { actions.setDockIconSize(it) },
             onDockActionIconSizeChange = { actions.setDockActionIconSize(it) },
             onFolderPreviewChange = { actions.setFolderPreview(it) },
            onComplete = { layoutPanelOpen = false },
            onBackToMenu = {
                layoutPanelOpen = false
                blankMenuOpen = true
            },
        )
    }
}

/** 底部图标栏显示的应用 = 已添加且解冻的应用（设计文档 §3.6） */
private fun dockPackages(
    gridItems: List<GridItem>,
    folderApps: List<com.nbljsbdk.snowhide.data.model.FolderApp>,
    frozenStates: Map<String, Boolean>,
    appStates: Map<String, AppRuntimeState>,
): List<String> {
    val all = (gridItems.mapNotNull { it.pkg } + folderApps.map { it.pkg }).distinct()
    return all.filter {
        appStates[it] != AppRuntimeState.MISSING && frozenStates[it] != true
    }
}

/** Shizuku 引导卡（区分「服务未运行」与「未授权」两种状态） */
@Composable
private fun ShizukuGuideCard(
    running: Boolean,
    onRequest: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = FrostCard),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (running) "需要 Shizuku 授权" else "Shizuku 服务未运行",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (running) {
                    "冻结/解冻通过 Shizuku（shell 身份）执行。点击下方按钮授权。"
                } else {
                    "请先打开 Shizuku 并启动服务，然后回到本应用刷新状态。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (running) {
                    androidx.compose.material3.Button(onClick = onRequest) {
                        Text("授权 Shizuku")
                    }
                }
                androidx.compose.material3.OutlinedButton(onClick = onRefresh) {
                    Text("刷新状态")
                }
            }
        }
    }
}

/** 应用宫格单元 */
@Composable
private fun AppCell(
    pkg: String,
    label: String,
    size: androidx.compose.ui.unit.Dp,
    frozen: Boolean,
    missing: Boolean = false,
    icon: ImageBitmap?,
    showName: Boolean,
    freezeStyle: com.nbljsbdk.snowhide.ui.util.FreezeStyle = com.nbljsbdk.snowhide.ui.util.FreezeStyle.BLUE,
    frostAnimationDurationMillis: Int = 300,
    iconShape: String = "round",
    selectionColor: Color? = null,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(4.dp)
            .background(
                selectionColor?.copy(alpha = 0.5f) ?: Color.Transparent,
            ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (icon != null) {
                androidx.compose.foundation.Image(
                    bitmap = icon,
                    contentDescription = label,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(size)
                        .clip(if (iconShape == "circle") androidx.compose.foundation.shape.CircleShape
                        else RoundedCornerShape(size.value * 0.22f))
                        .frosted(
                            enabled = frozen,
                            style = freezeStyle,
                            animationDurationMillis = frostAnimationDurationMillis,
                        ),
                )
            } else {
                // 图标未加载：灰色占位块（不显示包名文字）
                Box(
                    modifier = Modifier
                        .size(size)
                        .clip(RoundedCornerShape(size.value * 0.22f))
                        .background(androidx.compose.ui.graphics.Color(0xFFD8E4F1)),
                )
            }
            if (frozen) {
                // 雪花角标（FontAwesome snowflake）
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.ic_snowflake),
                    contentDescription = "已冻结",
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(TianyiBlue),
                    modifier = Modifier
                        .size(size * 0.38f)
                        .align(Alignment.TopStart),
                )
            } else if (missing) {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.ic_trash),
                    contentDescription = "应用已删除",
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .size(size * 0.38f)
                        .align(Alignment.TopStart),
                )
            }
        }
        if (showName) {
            Spacer(modifier = Modifier.height(2.dp))
            // 白字黑边（壁纸透明背景下可读）
            OutlinedText(
                text = label,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/** 文件夹宫格单元（2×2 / 3×3 拼贴，冻结成员霜化+雪花角标） */
@Composable
private fun FolderCell(
    folderId: Long,
    name: String,
    size: androidx.compose.ui.unit.Dp,
    previewPackages: List<String>,
    icons: Map<String, ImageBitmap>,
    frozenStates: Map<String, Boolean> = emptyMap(),
    appStates: Map<String, AppRuntimeState> = emptyMap(),
    freezeStyle: com.nbljsbdk.snowhide.ui.util.FreezeStyle = com.nbljsbdk.snowhide.ui.util.FreezeStyle.BLUE,
    frostAnimationDurationMillis: Int = 300,
    previewSize: Int = 2,
    iconShape: String = "round",
    showName: Boolean = true,
    selectionColor: Color? = null,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(4.dp)
            .background(
                selectionColor?.copy(alpha = 0.5f) ?: Color.Transparent,
            ),
    ) {
        // 文件夹 2×2 / 3×3 拼贴预览（前 4/9 个成员，空文件夹显示空外框）
        if (previewPackages.isEmpty()) {
            // 空文件夹沿用非空文件夹的外框，只是不绘制内部应用。
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(size.value * 0.22f))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            )
        } else {
            val grid = previewSize.coerceIn(2, 3)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(size.value * 0.22f))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            ) {
                Column {
                    for (row in 0 until grid) {
                        Row {
                            for (col in 0 until grid) {
                                val idx = row * grid + col
                                val pkg = previewPackages.getOrNull(idx)
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(size / grid),
                                ) {
                                    if (pkg != null) {
                                        val runtimeState = appStates[pkg]
                                        val frozen = runtimeState == AppRuntimeState.FROZEN ||
                                            ((runtimeState == null || runtimeState == AppRuntimeState.UNKNOWN) &&
                                                frozenStates[pkg] == true)
                                        val missing = runtimeState == AppRuntimeState.MISSING
                                        Box(contentAlignment = Alignment.TopStart) {
                                            icons[pkg]?.let { bmp ->
                                                androidx.compose.foundation.Image(
                                                    bitmap = bmp,
                                                    contentDescription = pkg,
                                                    contentScale = ContentScale.Fit,
                                                    modifier = Modifier
                                                        .size(size / grid * 0.88f)
                                                        .clip(if (iconShape == "circle") androidx.compose.foundation.shape.CircleShape
                                                        else RoundedCornerShape(size.value * 0.08f))
                                                         .frosted(
                                                             enabled = frozen,
                                                             style = freezeStyle,
                                                             animationDurationMillis = frostAnimationDurationMillis,
                                                         ),
                                                )
                                            }
                                            if (frozen) {
                                                androidx.compose.foundation.Image(
                                                    painter = painterResource(R.drawable.ic_snowflake),
                                                    contentDescription = "已冻结",
                                                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(TianyiBlue),
                                                    modifier = Modifier.size(size / grid * 0.32f),
                                                )
                                            } else if (missing) {
                                                androidx.compose.foundation.Image(
                                                    painter = painterResource(R.drawable.ic_trash),
                                                    contentDescription = "应用已删除",
                                                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.error),
                                                    modifier = Modifier.size(size / grid * 0.32f),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showName) {
            Spacer(modifier = Modifier.height(2.dp))
            // 白字黑边（壁纸透明背景下可读）
            OutlinedText(
                text = name,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/** 底部图标栏（已添加且解冻的应用横排 + 快速清理，设计文档 §3.6） */
@Composable
private fun DockBar(
    packages: List<String>,
    lockedPackages: Set<String>,
    icons: Map<String, ImageBitmap>,
    iconSize: androidx.compose.ui.unit.Dp,
    actionIconSize: androidx.compose.ui.unit.Dp,
    iconShape: String,
    transparentBg: Boolean = false,
    animationDurationMillis: Int,
    onQuickClean: () -> Unit,
    onAppClick: (String) -> Unit,
    onAppLongClick: (String) -> Unit,
    onAppSwipeUp: (String) -> Unit,
) {
    val boundedActionIconSize = actionIconSize.coerceAtMost((iconSize.value - 8f).coerceAtLeast(12f).dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            // 透明背景（透出壁纸）时 dock 同步透明
            .background(
                if (transparentBg) androidx.compose.ui.graphics.Color.Transparent
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            // 默认居中开始，放不下时左右滚动（设计文档 §3.6）。
            // 右侧操作槽占掉固定宽度后视口中心左移，start padding 补偿半个
            // （操作槽宽 = Dock 槽位宽 + 间隔）使应用图标相对整个 Dock 居中
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = iconSize / 2 + 6.dp,
            ),
        ) {
            items(packages, key = { it }) { pkg ->
                icons[pkg]?.let { bitmap ->
                    DockIcon(
                        pkg = pkg,
                        bitmap = bitmap,
                        iconSize = iconSize,
                        animationDurationMillis = animationDurationMillis,
                        locked = pkg in lockedPackages,
                        onClick = { onAppClick(pkg) },
                        onLongClick = { onAppLongClick(pkg) },
                        onSwipeUp = { onAppSwipeUp(pkg) },
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        // 右侧操作槽与 Dock 应用槽完全同尺寸；内图标独立调节，便于未来替换扫帚。
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(iconSize)
                .clip(
                    if (iconShape == "circle") CircleShape
                    else RoundedCornerShape(iconSize.value * 0.22f),
                )
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
                .clickable(onClick = onQuickClean),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_broom),
                contentDescription = "智能清理",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(boundedActionIconSize),
            )
        }
    }
}

/**
 * 底部栏单个图标（设计文档 §3.6）
 *
 * 上划手势（用户拍板）：
 * - 拖动中图标跟手上移、逐渐变淡
 * - **松手才确认**：上划超过阈值 → 触发冻结；未到位或拉回原位 → 回弹，无行动
 */
@Composable
private fun DockIcon(
    pkg: String,
    bitmap: ImageBitmap,
    iconSize: androidx.compose.ui.unit.Dp,
    animationDurationMillis: Int,
    locked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSwipeUp: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // 上划偏移（负=向上）。拖动时直接赋值（跟手），松手 animateTo 回弹
    val offsetY = remember { Animatable(0f) }
    val maxDrag = iconSize.value * 1.4f
    val threshold = -iconSize.value * 0.9f

    Box {
        androidx.compose.foundation.Image(
            bitmap = bitmap,
            contentDescription = pkg,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .offset { IntOffset(0, offsetY.value.roundToInt()) }
                .graphicsLayer {
                    alpha = 1f - (offsetY.value / -maxDrag).coerceIn(0f, 1f) * 0.55f
                }
                .size(iconSize)
                .clip(RoundedCornerShape(iconSize.value * 0.22f))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .pointerInput(pkg) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, amount ->
                            // Main.immediate：launch 同步执行，跟手无延迟
                            scope.launch {
                                offsetY.snapTo((offsetY.value + amount).coerceIn(-maxDrag, 0f))
                            }
                            change.consume()
                        },
                        onDragEnd = {
                            if (offsetY.value <= threshold) {
                                // 上划到位并松手 → 确认冻结（成功后图标从栏中消失）
                                onSwipeUp()
                            }
                            // 无论触发与否都回弹：失败时回到原位，成功时 item 即将移除
                            scope.launch {
                                if (animationDurationMillis == 0) offsetY.snapTo(0f)
                                else offsetY.animateTo(0f, animationSpec = tween(animationDurationMillis))
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                if (animationDurationMillis == 0) offsetY.snapTo(0f)
                                else offsetY.animateTo(0f, animationSpec = tween(animationDurationMillis))
                            }
                        },
                    )
                },
        )
        // 锁定角标（长按锁定，豁免快速清理/磁贴熄灭冻回；红色警示）
        if (locked) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.ic_lock),
                contentDescription = "已锁定",
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(iconSize * 0.42f),
            )
        }
    }
}

/** 齿轮二级菜单（设计文档 §3.7 八项，P0 接入核心四项） */
@Composable
private fun GearMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onOrganize: () -> Unit,
    onUnfreezeAll: () -> Unit,
    onFreezeAll: () -> Unit,
    onAppManage: () -> Unit,
    onQuickToggle: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        // 用户拍板：整理目录第一、增删应用第二
        DropdownMenuItem(
            text = { Text("整理目录") },
            onClick = { onDismiss(); onOrganize() },
        )
        DropdownMenuItem(
            text = { Text("增删应用") },
            onClick = { onDismiss(); onAppManage() },
        )
        DropdownMenuItem(
            text = { Text("启用全部") },
            onClick = { onDismiss(); onUnfreezeAll() },
        )
        DropdownMenuItem(
            text = { Text("停用全部") },
            onClick = { onDismiss(); onFreezeAll() },
        )
        DropdownMenuItem(
            text = { Text("快速启停") },
            onClick = { onDismiss(); onQuickToggle() },
        )
        DropdownMenuItem(
            text = { Text("更多选项") },
            onClick = { onDismiss(); onSettings() },
        )
        DropdownMenuItem(
            text = { Text("关于应用") },
            onClick = { onDismiss(); onAbout() },
        )
    }
}

/** 长按上下文菜单（应用/文件夹，设计文档 §3.3/§3.4） */
@Composable
private fun ContextMenu(
    item: GridItem,
    frozen: Boolean,
    folderName: String,
    appLabel: String,
    locationName: String,
    targetFolder: com.nbljsbdk.snowhide.data.model.Folder?,
    onDismiss: () -> Unit,
    onToggleFreeze: (String) -> Unit,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit,
    onRenameFolder: (Long) -> Unit,
    onDeleteFolder: (Long) -> Unit,
    onFreezeFolder: (com.nbljsbdk.snowhide.data.model.Folder) -> Unit,
    onUnfreezeFolder: (com.nbljsbdk.snowhide.data.model.Folder) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            if (item.type == "folder") {
                Text(folderName)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = appLabel,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = locationName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        },
        text = {
            Column {
                if (item.type == "folder") {
                    targetFolder?.let { folder ->
                        DialogAction("启用目录") { onUnfreezeFolder(folder) }
                        DialogAction("停用目录") { onFreezeFolder(folder) }
                    }
                    DialogAction("重命名") { item.folderId?.let(onRenameFolder) }
                    DialogAction("删除文件夹") { item.folderId?.let(onDeleteFolder) }
                } else {
                    val pkg = item.pkg ?: return@Column
                    DialogAction(if (frozen) "解冻" else "冻结") { onToggleFreeze(pkg) }
                    DialogAction("打开应用") { onOpen(pkg) }
                    DialogAction("移除应用") { onRemove(pkg) }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 对话框菜单项 */
@Composable
private fun DialogAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    )
}

/** 循环 Pager 放大倍数（大页数实现无缝循环，取模定位真实页） */
private const val LOOP_BASE = 500
private const val LOOP_TOTAL = 1000
private const val REENTRY_HOME_DELAY_MS = 10_000L
