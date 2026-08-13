@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.nbljsbdk.snowhide.feature.home

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateTo
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModelProvider
import com.nbljsbdk.snowhide.R
import com.nbljsbdk.snowhide.data.model.GridItem
import com.nbljsbdk.snowhide.feature.about.AboutScreen
import com.nbljsbdk.snowhide.feature.appmanage.AppManageScreen
import com.nbljsbdk.snowhide.feature.folder.FolderScreen
import com.nbljsbdk.snowhide.feature.organize.OrganizeOverlay
import com.nbljsbdk.snowhide.feature.quicktoggle.QuickToggleScreen
import com.nbljsbdk.snowhide.feature.settings.SettingsScreen
import com.nbljsbdk.snowhide.feature.organize.OrganizeViewModel
import com.nbljsbdk.snowhide.ui.theme.FrostCard
import com.nbljsbdk.snowhide.ui.theme.IceBlue
import com.nbljsbdk.snowhide.ui.theme.WarmOrange
import com.nbljsbdk.snowhide.ui.util.frosted

/**
 * 主屏幕（P0，设计文档 §3.2）
 *
 * 结构：顶栏（标题+齿轮+搜索）→ Shizuku 引导卡 → 混排宫格 → 底部图标栏。
 * 无状态组件化：宫格单元/底部栏/菜单均为内部无状态 Composable，逻辑全在 ViewModel。
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onRequestShizuku: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
            LocalContext.current.applicationContext as Application
        )
    ),
) {
    val gridItems by viewModel.gridRepository.gridItems.collectAsState()
    val folders by viewModel.gridRepository.folders.collectAsState()
    val folderApps by viewModel.gridRepository.folderApps.collectAsState()
    val frozenStates by viewModel.frozenStates.collectAsState()
    val lockedPackages by viewModel.gridRepository.lockedPackages.collectAsState()
    val icons by viewModel.icons.collectAsState()
    val labels by viewModel.labels.collectAsState()
    val engineReady by viewModel.engineReady.collectAsState()
    val shizukuRunning by viewModel.shizukuRunning.collectAsState()
    val columns by viewModel.settingsRepository.columns.collectAsState()
    val iconSize by viewModel.settingsRepository.iconSize.collectAsState()
    val verticalSpace by viewModel.settingsRepository.verticalSpace.collectAsState()
    val dockIconSize by viewModel.settingsRepository.dockIconSize.collectAsState()
    val showAppName by viewModel.settingsRepository.showAppName.collectAsState()
    val message by viewModel.message.collectAsState()
    val menuOpen by viewModel.menuOpen.collectAsState()
    val organizing by viewModel.organizing.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var searchOpen by remember { mutableStateOf(false) }

    // 整理目录状态机
    val organizeViewModel: OrganizeViewModel = viewModel()
    val organizeState by organizeViewModel.state.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // dock 长按锁定/解锁震动（受系统静音/勿扰控制）
    val vibrator = remember {
        runCatching { context.getSystemService(android.os.Vibrator::class.java) }.getOrNull()
    }
    val audioManager = remember {
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
    }
    val notificationManager = remember {
        context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
    }
    fun shouldVibrate(): Boolean {
        val silent = audioManager.ringerMode == android.media.AudioManager.RINGER_MODE_SILENT
        val dnd = notificationManager.currentInterruptionFilter ==
            android.app.NotificationManager.INTERRUPTION_FILTER_NONE
        return !silent && !dnd
    }

    // 长按菜单状态
    var longPressTarget by remember { mutableStateOf<GridItem?>(null) }
    // 文件夹重命名/删除弹窗状态
    var renameFolder by remember { mutableStateOf<com.nbljsbdk.snowhide.data.model.Folder?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteFolderTarget by remember { mutableStateOf<com.nbljsbdk.snowhide.data.model.Folder?>(null) }
    // 移除应用二级菜单 / 卸载二次确认
    var removeAppTarget by remember { mutableStateOf<String?>(null) }
    var uninstallTarget by remember { mutableStateOf<String?>(null) }
    // 布局设置透明浮框
    var layoutPanelOpen by remember { mutableStateOf(false) }

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
            viewModel.consumeMessage()
        }
    }

    LaunchedEffect(engineReady) {
        if (engineReady) viewModel.refreshFrozenStates()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (organizing) "整理目录" else "雪藏",
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    if (organizing) {
                        // 整理操作即时生效（用户拍板）；取消按钮已删，仅确认退出
                        Text(
                            text = "确认",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable {
                                    organizeViewModel.commitFolderName()
                                    viewModel.setOrganizing(false)
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    } else if (searchOpen) {
                        // 搜索框（过滤宫格）
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("搜索应用") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 4.dp),
                        )
                        TextButton(onClick = {
                            searchOpen = false
                            viewModel.setSearchQuery("")
                        }) { Text("取消") }
                    } else {
                        IconButton(onClick = { searchOpen = true }) {
                            Icon(Icons.Default.Search, contentDescription = "搜索")
                        }
                        IconButton(onClick = { viewModel.toggleMenu() }) {
                            Icon(Icons.Default.Settings, contentDescription = "设置")
                        }
                        GearMenu(
                            expanded = menuOpen,
                            onDismiss = { viewModel.dismissMenu() },
                            onOrganize = { viewModel.setOrganizing(true) },
                            onUnfreezeAll = { viewModel.unfreezeAll() },
                            onFreezeAll = { viewModel.freezeAll() },
                            onAppManage = { viewModel.openAppManage() },
                            onQuickToggle = { viewModel.openQuickToggle() },
                            onSettings = { viewModel.openSettings() },
                            onAbout = { viewModel.openAbout() },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        snackbarHost = {
            // 上移避开底部 dock 栏（用户拍板：Snackbar 不挡栏位）
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = dockIconSize.dp + 12.dp),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        // ═══════════════════════════════════════
        // 循环滑动（设计文档 §3.2）：
        // 页面序列 = [主屏, 文件夹1, 文件夹2, ...]（文件夹按 sortOrder）
        // 左右滑动循环切换；从哪个文件夹进入就从哪里开始
        // ═══════════════════════════════════════
        val sortedFolders = folders.sortedBy { it.sortOrder }
        val actualCount = sortedFolders.size + 1 // 主屏 + N 文件夹
        val pagerState = rememberPagerState(
            initialPage = LOOP_BASE * actualCount,
        ) { LOOP_TOTAL * actualCount }

        // 返回键处理：
        // ① 搜索框展开 → 关闭搜索并清空（等同「取消」按钮）
        // ② 整理目录模式 → 保存并退出整理，回到主界面
        // ③ 文件夹页 → 滑回主屏页
        // ④ 主屏非整理 → 默认行为（退出 App）
        BackHandler(enabled = searchOpen || organizing || pagerState.currentPage % actualCount != 0) {
            if (searchOpen) {
                searchOpen = false
                viewModel.setSearchQuery("")
            } else if (organizing) {
                organizeViewModel.commitFolderName()
                viewModel.setOrganizing(false)
            } else {
                scope.launch { pagerState.animateHome(actualCount) }
            }
        }

        // 文件夹数量变化时的页面锚点对齐：
        // PagerState 是 rememberSaveable 不重建，currentPage 可能偏离基准倍数，
        // actualCount 变化会让取模结果漂到别的文件夹页（scrollHome 瞬时对齐）。
        // - 整理模式：锁定主屏
        // - 非整理：删除文件夹的唯一入口是主屏长按（用户本在主屏），漂移即对齐回主屏
        LaunchedEffect(organizing, actualCount) {
            if (organizing || pagerState.currentPage % actualCount != 0) {
                pagerState.scrollHome(actualCount)
            }
        }

        // 搜索有词时自动回主屏显示结果（结果只在主屏宫格渲染）
        LaunchedEffect(searchQuery) {
            if (searchQuery.isNotBlank()) {
                pagerState.animateHome(actualCount)
            }
        }

        HorizontalPager(
            state = pagerState,
            // 只有一个主屏（没有文件夹）时禁用滑动；搜索/整理期间锁定主屏
            userScrollEnabled = !organizing && sortedFolders.isNotEmpty() && searchQuery.isBlank(),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) { page ->
            // 整理模式强制渲染主屏：创建/删除文件夹的瞬间 actualCount 已变而
            // currentPage 还未对齐，取模结果会漂到文件夹页造成闪动。
            val idx = if (organizing) 0 else ((page % actualCount) + actualCount) % actualCount
            if (idx == 0) {
                // ── 主屏页 ──
                Column(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // Shizuku 引导卡：未运行 → 打开 Shizuku；未授权 → 授权
                    if (!engineReady) {
                        ShizukuGuideCard(
                            running = shizukuRunning,
                            onRequest = onRequestShizuku,
                            onRefresh = { viewModel.refreshEngineStatus() },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }

                    // 混排宫格
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val searchFiltered = if (searchQuery.isBlank()) {
                            gridItems.sortedBy { it.sortOrder }
                        } else {
                            // 搜索范围 = 主屏项 + 文件夹内应用（文件夹成员也能搜到，结果点击直接打开）
                            val memberItems = folderApps.map { fa ->
                                GridItem(
                                    id = (fa.folderId shl 32) xor fa.pkg.hashCode().toLong(),
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
                                                .take(4),
                                            icons = icons,
                                            frozenStates = frozenStates,
                                            selected = organizing &&
                                                organizeState is OrganizeViewModel.OrganizeState.FolderSelected &&
                                                (organizeState as OrganizeViewModel.OrganizeState.FolderSelected).folderId == folder.id,
                                            onClick = {
                                                if (organizing) {
                                                    organizeViewModel.tapFolder(folder)
                                                } else {
                                                    // 跳到该文件夹页（循环内当前位置的相邻页）
                                                    val folderIndex = sortedFolders.indexOfFirst { it.id == folder.id }
                                                    if (folderIndex >= 0) {
                                                        scope.launch {
                                                            pagerState.animateToFolder(actualCount, folderIndex)
                                                        }
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
                                        icon = icons[item.pkg],
                                        showName = showAppName,
                                        selected = organizing && when (val s = organizeState) {
                                            is OrganizeViewModel.OrganizeState.HomeAppSelected -> s.app.id == item.id
                                            is OrganizeViewModel.OrganizeState.FolderSelected -> s.subHomeApp?.id == item.id
                                            else -> false
                                        },
                                        onClick = {
                                            if (organizing) organizeViewModel.tapHomeApp(item)
                                            else viewModel.openApp(item.pkg)
                                        },
                                        onLongPress = {
                                            if (!organizing) longPressTarget = item
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // 底部：整理模式显示操作区，否则底部图标栏
                    if (organizing) {
                        OrganizeOverlay(
                            state = organizeState,
                            folders = folders,
                            folderApps = organizeViewModel.currentFolderApps,
                            icons = icons,
                            onTapHomeApp = { item -> organizeViewModel.tapHomeApp(item) },
                            onTapFolder = { folder -> organizeViewModel.tapFolder(folder) },
                            onTapFolderApp = { pkg -> organizeViewModel.tapFolderApp(pkg) },
                            onShift = { step -> organizeViewModel.shift(step) },
                            onMoveUp = { organizeViewModel.moveUp() },
                            onMoveDown = { organizeViewModel.moveDown() },
                            onCreate = { organizeViewModel.createFolder() },
                            onDelete = { organizeViewModel.requestDeleteFolder() },
                            onNameChange = { name -> organizeViewModel.updateFolderName(name) },
                            onNameCommit = { organizeViewModel.commitFolderName() },
                            onAppLabel = { pkg -> labels[pkg] ?: "" },
                        )
                    } else {
                        DockBar(
                            packages = dockPackages(gridItems, folderApps, frozenStates),
                            lockedPackages = lockedPackages,
                            icons = icons,
                            iconSize = dockIconSize.dp,
                            onQuickClean = { viewModel.quickClean() },
                            onAppClick = { viewModel.openApp(it) },
                            onAppLongClick = { pkg ->
                                viewModel.gridRepository.toggleLock(pkg)
                                // 锁定与解锁都短震；系统静音/勿扰时不震
                                if (shouldVibrate()) {
                                    vibrator?.vibrate(
                                        android.os.VibrationEffect.createOneShot(
                                            40,
                                            android.os.VibrationEffect.DEFAULT_AMPLITUDE
                                        )
                                    )
                                }
                            },
                            onAppSwipeUp = { pkg -> viewModel.toggleFreeze(pkg) },
                        )
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
                    showAppName = showAppName,
                    onBackToHome = {
                        scope.launch { pagerState.animateHome(actualCount) }
                    },
                    onAppClick = { viewModel.openApp(it) },
                    onAppLongClick = { item -> longPressTarget = item },
                    onAppLabel = { pkg -> labels[pkg] ?: "" },
                )
            }
        }
    }

    // 增加/移除应用界面（全屏覆盖，设计文档 §3.8）
    val appManageOpen by viewModel.appManageOpen.collectAsState()
    if (appManageOpen) {
        AppManageScreen(onClose = { viewModel.closeAppManage() })
    }

    // 设置页（全屏覆盖，设计文档 §3.11）
    val settingsOpen by viewModel.settingsOpen.collectAsState()
    if (settingsOpen) {
        SettingsScreen(onClose = { viewModel.closeSettings() })
    }

    // 快速启停管理（全屏覆盖，设计文档 §3.9）
    val quickToggleOpen by viewModel.quickToggleOpen.collectAsState()
    if (quickToggleOpen) {
        QuickToggleScreen(onClose = { viewModel.closeQuickToggle() })
    }

    // 关于页（全屏覆盖，含版本号彩蛋）
    val aboutOpen by viewModel.aboutOpen.collectAsState()
    if (aboutOpen) {
        AboutScreen(onClose = { viewModel.closeAbout() })
    }

    // 文件夹重命名对话框
    renameFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { renameFolder = null },
            title = { Text("重命名文件夹") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    if (renameText.isNotBlank()) {
                        viewModel.gridRepository.renameFolder(folder.id, renameText.trim())
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
                    viewModel.gridRepository.deleteFolder(folder.id)
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
                        viewModel.removeApp(pkg)
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
                    viewModel.uninstallApp(pkg)
                    uninstallTarget = null
                }) { Text("卸载", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { uninstallTarget = null }) { Text("取消") }
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
            targetFolder = folders.find { it.id == target.folderId },
            onDismiss = { longPressTarget = null },
            onToggleFreeze = { pkg -> viewModel.toggleFreeze(pkg); longPressTarget = null },
            onEnable = { pkg -> viewModel.enableApp(pkg); longPressTarget = null },
            onOpen = { pkg -> viewModel.openApp(pkg); longPressTarget = null },
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
                viewModel.freezeFolder(folder); longPressTarget = null
            },
            onUnfreezeFolder = { folder ->
                viewModel.unfreezeFolder(folder); longPressTarget = null
            },
            onLayoutSettings = {
                longPressTarget = null
                layoutPanelOpen = true
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
            onColumnsChange = { viewModel.settingsRepository.setColumns(it) },
            onIconSizeChange = { viewModel.settingsRepository.setIconSize(it) },
            onVerticalSpaceChange = { viewModel.settingsRepository.setVerticalSpace(it) },
            onDockIconSizeChange = { viewModel.settingsRepository.setDockIconSize(it) },
            onDismiss = { layoutPanelOpen = false },
        )
    }
}

/** 底部图标栏显示的应用 = 已添加且解冻的应用（设计文档 §3.6） */
private fun dockPackages(
    gridItems: List<GridItem>,
    folderApps: List<com.nbljsbdk.snowhide.data.model.FolderApp>,
    frozenStates: Map<String, Boolean>,
): List<String> {
    val all = (gridItems.mapNotNull { it.pkg } + folderApps.map { it.pkg }).distinct()
    return all.filter { frozenStates[it] != true }
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
    icon: ImageBitmap?,
    showName: Boolean,
    selected: Boolean = false,
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
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                else androidx.compose.ui.graphics.Color.Transparent,
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
                        .clip(RoundedCornerShape(size.value * 0.22f))
                        .frosted(enabled = frozen),
                )
            } else {
                // 图标未加载：灰色占位块（不显示包名文字）
                Box(
                    modifier = Modifier
                        .size(size)
                        .clip(RoundedCornerShape(size.value * 0.22f))
                        .background(androidx.compose.ui.graphics.Color(0xFF2A2F38)),
                )
            }
            if (frozen) {
                // 雪花角标（FontAwesome snowflake）
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.ic_snowflake),
                    contentDescription = "已冻结",
                    modifier = Modifier
                        .size(size * 0.38f)
                        .align(Alignment.TopStart),
                )
            }
        }
        if (showName) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (frozen) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 文件夹宫格单元（2×2 拼贴，冻结成员霜化+雪花角标） */
@Composable
private fun FolderCell(
    folderId: Long,
    name: String,
    size: androidx.compose.ui.unit.Dp,
    previewPackages: List<String>,
    icons: Map<String, ImageBitmap>,
    frozenStates: Map<String, Boolean> = emptyMap(),
    selected: Boolean = false,
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
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                else androidx.compose.ui.graphics.Color.Transparent,
            ),
    ) {
        // 文件夹 2×2 拼贴预览（实时显示前 4 个成员，空文件夹显示基础图标）
        if (previewPackages.isEmpty()) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.ic_folder),
                contentDescription = name,
                modifier = Modifier.size(size),
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(size.value * 0.22f))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            ) {
                Column {
                    for (row in 0..1) {
                        Row {
                            for (col in 0..1) {
                                val idx = row * 2 + col
                                val pkg = previewPackages.getOrNull(idx)
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(size * 0.5f),
                                ) {
                                    if (pkg != null) {
                                        val frozen = frozenStates[pkg] == true
                                        icons[pkg]?.let { bmp ->
                                            Box(contentAlignment = Alignment.TopStart) {
                                                androidx.compose.foundation.Image(
                                                    bitmap = bmp,
                                                    contentDescription = pkg,
                                                    contentScale = ContentScale.Fit,
                                                    modifier = Modifier
                                                        .size(size * 0.44f)
                                                        .clip(RoundedCornerShape(size.value * 0.1f))
                                                        .frosted(enabled = frozen),
                                                )
                                                if (frozen) {
                                                    // 雪花角标（2×2 预览内冻结成员）
                                                    androidx.compose.foundation.Image(
                                                        painter = painterResource(R.drawable.ic_snowflake),
                                                        contentDescription = "已冻结",
                                                        modifier = Modifier.size(size * 0.16f),
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
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 底部图标栏（已添加且解冻的应用横排 + 快速清理，设计文档 §3.6） */
@Composable
private fun DockBar(
    packages: List<String>,
    lockedPackages: Set<String>,
    icons: Map<String, ImageBitmap>,
    iconSize: androidx.compose.ui.unit.Dp,
    onQuickClean: () -> Unit,
    onAppClick: (String) -> Unit,
    onAppLongClick: (String) -> Unit,
    onAppSwipeUp: (String) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            // 默认居中开始，放不下时左右滚动（设计文档 §3.6）
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            items(packages, key = { it }) { pkg ->
                icons[pkg]?.let { bitmap ->
                    DockIcon(
                        pkg = pkg,
                        bitmap = bitmap,
                        iconSize = iconSize,
                        locked = pkg in lockedPackages,
                        onClick = { onAppClick(pkg) },
                        onLongClick = { onAppLongClick(pkg) },
                        onSwipeUp = { onAppSwipeUp(pkg) },
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        // 快速清理按钮（暖橙强调，P0 用扫帚图标占位）
        IconButton(onClick = onQuickClean) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.ic_broom),
                contentDescription = "快速清理",
                modifier = Modifier.size(iconSize * 0.9f),
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
                            scope.launch { offsetY.animateTo(0f) }
                        },
                        onDragCancel = {
                            scope.launch { offsetY.animateTo(0f) }
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
        DropdownMenuItem(
            text = { Text("增删应用") },
            onClick = { onDismiss(); onAppManage() },
        )
        DropdownMenuItem(
            text = { Text("整理目录") },
            onClick = { onDismiss(); onOrganize() },
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
            text = { Text("关于") },
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
    targetFolder: com.nbljsbdk.snowhide.data.model.Folder?,
    onDismiss: () -> Unit,
    onToggleFreeze: (String) -> Unit,
    onEnable: (String) -> Unit,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit,
    onRenameFolder: (Long) -> Unit,
    onDeleteFolder: (Long) -> Unit,
    onFreezeFolder: (com.nbljsbdk.snowhide.data.model.Folder) -> Unit,
    onUnfreezeFolder: (com.nbljsbdk.snowhide.data.model.Folder) -> Unit,
    onLayoutSettings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (item.type == "folder") folderName else appLabel)
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
                    DialogAction("布局设置") { onLayoutSettings() }
                } else {
                    val pkg = item.pkg ?: return@Column
                    DialogAction(if (frozen) "解冻" else "冻结") { onToggleFreeze(pkg) }
                    DialogAction("启用应用（不打开）") { onEnable(pkg) }
                    DialogAction("打开应用") { onOpen(pkg) }
                    DialogAction("移除应用") { onRemove(pkg) }
                    DialogAction("布局设置") { onLayoutSettings() }
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
