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
import androidx.lifecycle.ViewModelProvider
import com.nbljsbdk.snowhide.data.model.GridItem
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

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // 长按菜单状态
    var longPressTarget by remember { mutableStateOf<GridItem?>(null) }

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
                    )
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                                onClick = { viewModel.openApp(item.pkg) },
                                onLongPress = { longPressTarget = item },
                            )
                        }
                    }
                }
            }

            // 底部图标栏
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

    // 整理目录页（P0 状态机入口，下一轮补全交互）
    if (viewModel.organizing.collectAsState().value) {
        OrganizePlaceholder(onClose = { viewModel.setOrganizing(false) })
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
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(4.dp),
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
    onLongPress: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = { /* 进入文件夹（下一轮接全屏文件夹页） */ }, onLongClick = onLongPress)
            .padding(4.dp),
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
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("增加应用") },
            onClick = onDismiss,
        )
        DropdownMenuItem(
            text = { Text("移除应用") },
            onClick = onDismiss,
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

/** 整理目录占位页（P0 下一轮补全状态机交互） */
@Composable
private fun OrganizePlaceholder(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("整理目录") },
        text = { Text("整理目录交互（状态机）下一轮实现") },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onClose) { Text("关闭") }
        },
    )
}
