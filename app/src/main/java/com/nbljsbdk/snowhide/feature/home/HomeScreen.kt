@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.nbljsbdk.snowhide.feature.home

import android.app.Application
import androidx.compose.foundation.background
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModelProvider
import com.nbljsbdk.snowhide.data.model.GridItem
import com.nbljsbdk.snowhide.feature.appmanage.AppManageScreen
import com.nbljsbdk.snowhide.feature.folder.FolderScreen
import com.nbljsbdk.snowhide.feature.organize.OrganizeOverlay
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
    val icons by viewModel.icons.collectAsState()
    val engineReady by viewModel.engineReady.collectAsState()
    val columns by viewModel.settingsRepository.columns.collectAsState()
    val iconSize by viewModel.settingsRepository.iconSize.collectAsState()
    val dockIconSize by viewModel.settingsRepository.dockIconSize.collectAsState()
    val showAppName by viewModel.settingsRepository.showAppName.collectAsState()
    val message by viewModel.message.collectAsState()
    val menuOpen by viewModel.menuOpen.collectAsState()
    val organizing by viewModel.organizing.collectAsState()

    // 整理目录状态机
    val organizeViewModel: OrganizeViewModel = viewModel()
    val organizeState by organizeViewModel.state.collectAsState()
    val organizeFinished by organizeViewModel.finished.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 长按菜单状态
    var longPressTarget by remember { mutableStateOf<GridItem?>(null) }

    // 整理完成 → 关闭整理模式并刷新
    LaunchedEffect(organizeFinished) {
        if (organizeFinished) {
            viewModel.setOrganizing(false)
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
                title = { Text("雪藏", fontWeight = FontWeight.Bold) },
                actions = {
                    if (organizing) {
                        // 整理模式：取消（二次确认丢弃）/ 确认（保存）
                        Text(
                            text = "取消",
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .clickable { viewModel.setOrganizing(false) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                        Text(
                            text = "确认",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable {
                                    organizeViewModel.commitFolderName()
                                    organizeViewModel.finish()
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    } else {
                        IconButton(onClick = { /* 搜索（P0 占位） */ }) {
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
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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

        // 整理模式下锁定主屏页
        LaunchedEffect(organizing) {
            if (organizing) {
                val base = (pagerState.currentPage / actualCount) * actualCount
                pagerState.animateScrollToPage(base)
            }
        }

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !organizing,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) { page ->
            val idx = ((page % actualCount) + actualCount) % actualCount
            if (idx == 0) {
                // ── 主屏页 ──
                Column(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // Shizuku 未授权引导卡
                    if (!engineReady) {
                        ShizukuGuideCard(
                            onRequest = onRequestShizuku,
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
                        items(gridItems.sortedBy { it.sortOrder }, key = { it.id }) { item ->
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
                                            selected = organizeState is OrganizeViewModel.OrganizeState.FolderSelected &&
                                                (organizeState as OrganizeViewModel.OrganizeState.FolderSelected).folderId == folder.id,
                                            onClick = {
                                                if (organizing) organizeViewModel.tapFolder(folder)
                                                else {
                                                    // 跳到该文件夹页（循环内当前位置的相邻页）
                                                    val folderIndex = sortedFolders.indexOfFirst { it.id == folder.id }
                                                    if (folderIndex >= 0) {
                                                        scope.launch {
                                                            val base = (pagerState.currentPage / actualCount) * actualCount
                                                            pagerState.animateScrollToPage(base + folderIndex + 1)
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
                                        label = labelOf(item.pkg),
                                        size = iconSize.dp,
                                        frozen = frozenStates[item.pkg] == true,
                                        icon = icons[item.pkg],
                                        showName = showAppName,
                                        selected = when (val s = organizeState) {
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
                            onAppLabel = { labelOf(it) },
                        )
                        // 删除文件夹二次确认
                        organizeViewModel.pendingDelete.collectAsState().value?.let { folder ->
                            AlertDialog(
                                onDismissRequest = { organizeViewModel.cancelDeleteFolder() },
                                title = { Text("删除文件夹") },
                                text = { Text("删除「${folder.name}」？其中 ${organizeViewModel.currentFolderApps.size} 个应用将移回主屏幕。") },
                                confirmButton = {
                                    androidx.compose.material3.TextButton(onClick = { organizeViewModel.confirmDeleteFolder() }) {
                                        Text("删除", color = MaterialTheme.colorScheme.error)
                                    }
                                },
                                dismissButton = {
                                    androidx.compose.material3.TextButton(onClick = { organizeViewModel.cancelDeleteFolder() }) {
                                        Text("取消")
                                    }
                                },
                            )
                        }
                    } else {
                        DockBar(
                            packages = dockPackages(gridItems, folderApps, frozenStates),
                            icons = icons,
                            iconSize = dockIconSize.dp,
                            onQuickClean = { viewModel.quickClean() },
                            onAppClick = { viewModel.openApp(it) },
                            onAppLongClick = { pkg -> viewModel.gridRepository.toggleLock(pkg) },
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
                        scope.launch {
                            val base = (pagerState.currentPage / actualCount) * actualCount
                            pagerState.animateScrollToPage(base)
                        }
                    },
                    onAppClick = { viewModel.openApp(it) },
                    onAppLongClick = { item -> longPressTarget = item },
                    onAppLabel = { labelOf(it) },
                )
            }
        }
    }

    // 增加/移除应用界面（全屏覆盖，设计文档 §3.8）
    val appManageOpen by viewModel.appManageOpen.collectAsState()
    if (appManageOpen) {
        AppManageScreen(onClose = { viewModel.closeAppManage() })
    }

    // 长按上下文菜单
    longPressTarget?.let { target ->
        ContextMenu(
            item = target,
            frozen = target.pkg?.let { frozenStates[it] == true } ?: false,
            folderName = folders.find { it.id == target.folderId }?.name ?: "",
            onDismiss = { longPressTarget = null },
            onToggleFreeze = { pkg -> viewModel.toggleFreeze(pkg); longPressTarget = null },
            onEnable = { pkg -> viewModel.enableApp(pkg); longPressTarget = null },
            onOpen = { pkg -> viewModel.openApp(pkg); longPressTarget = null },
            onRemove = { pkg -> viewModel.gridRepository.removeApp(pkg); longPressTarget = null },
            onRenameFolder = { id -> longPressTarget = null },
            onDeleteFolder = { id -> viewModel.gridRepository.deleteFolder(id); longPressTarget = null },
            onToggleFolderFreeze = { folder ->
                viewModel.toggleFolderFreeze(folder); longPressTarget = null
            },
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

/** 显示名占位（P0 用包名，后续接 AppInfo 映射） */
private fun labelOf(pkg: String): String = pkg.substringAfterLast('.')

/** Shizuku 未授权引导卡 */
@Composable
private fun ShizukuGuideCard(onRequest: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = FrostCard),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "需要 Shizuku 权限",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "冻结/解冻通过 Shizuku（shell 身份）执行。点击下方按钮授权。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            androidx.compose.material3.Button(onClick = onRequest) {
                Text("授权 Shizuku")
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
            }
            if (frozen) {
                // 雪花角标（P0 用文字占位，后续换 FontAwesome VectorDrawable）
                Text(
                    text = "❄",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .alpha(0.9f),
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

/** 文件夹宫格单元（P0 先单一文件夹图标，2×2 拼贴下一轮美化） */
@Composable
private fun FolderCell(
    folderId: Long,
    name: String,
    size: androidx.compose.ui.unit.Dp,
    previewPackages: List<String>,
    icons: Map<String, ImageBitmap>,
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
        Icon(
            Icons.Default.Settings, // P0 占位图标，后续换 FontAwesome folder
            contentDescription = name,
            tint = IceBlue,
            modifier = Modifier.size(size),
        )
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
    icons: Map<String, ImageBitmap>,
    iconSize: androidx.compose.ui.unit.Dp,
    onQuickClean: () -> Unit,
    onAppClick: (String) -> Unit,
    onAppLongClick: (String) -> Unit,
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
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(packages, key = { it }) { pkg ->
                icons[pkg]?.let { bitmap ->
                    androidx.compose.foundation.Image(
                        bitmap = bitmap,
                        contentDescription = pkg,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(iconSize)
                            .clip(RoundedCornerShape(iconSize.value * 0.22f))
                            .combinedClickable(
                                onClick = { onAppClick(pkg) },
                                onLongClick = { onAppLongClick(pkg) },
                            ),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        // 快速清理按钮（暖橙强调，P0 用扫帚图标占位）
        IconButton(onClick = onQuickClean) {
            Icon(
                Icons.Default.Add, // P0 占位，后续换 FontAwesome broom
                contentDescription = "快速清理",
                tint = WarmOrange,
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
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("增加应用") },
            onClick = { onDismiss(); onAppManage() },
        )
        DropdownMenuItem(
            text = { Text("移除应用") },
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
            text = { Text("快速启停（未开放）") },
            onClick = onDismiss,
        )
        DropdownMenuItem(
            text = { Text("更多选项") },
            onClick = onDismiss,
        )
        DropdownMenuItem(
            text = { Text("关于") },
            onClick = onDismiss,
        )
    }
}

/** 长按上下文菜单（应用/文件夹，设计文档 §3.3/§3.4） */
@Composable
private fun ContextMenu(
    item: GridItem,
    frozen: Boolean,
    folderName: String,
    onDismiss: () -> Unit,
    onToggleFreeze: (String) -> Unit,
    onEnable: (String) -> Unit,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit,
    onRenameFolder: (Long) -> Unit,
    onDeleteFolder: (Long) -> Unit,
    onToggleFolderFreeze: (com.nbljsbdk.snowhide.data.model.Folder) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (item.type == "folder") folderName else item.pkg?.substringAfterLast('.') ?: "")
        },
        text = {
            Column {
                if (item.type == "folder") {
                    DialogAction("启用目录") { }
                    DialogAction("停用目录") { }
                    DialogAction("重命名") { item.folderId?.let(onRenameFolder) }
                    DialogAction("删除文件夹") { item.folderId?.let(onDeleteFolder) }
                } else {
                    val pkg = item.pkg ?: return@Column
                    DialogAction(if (frozen) "解冻" else "冻结") { onToggleFreeze(pkg) }
                    DialogAction("启用应用（不打开）") { onEnable(pkg) }
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
