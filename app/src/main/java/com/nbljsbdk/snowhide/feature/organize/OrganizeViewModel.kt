package com.nbljsbdk.snowhide.feature.organize

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.ViewModel
import com.nbljsbdk.snowhide.data.model.Folder
import com.nbljsbdk.snowhide.data.model.GridItem
import com.nbljsbdk.snowhide.data.repo.GridRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 整理目录状态机 ViewModel（设计文档 §3.10 终版）
 *
 * 状态：
 * ① Empty（刚进入）：全灰仅「创建」
 * ② HomeAppSelected：选中主屏 app——左右/创建可用
 * ③ FolderSelected：选中文件夹——左右/创建/删除+名字输入+内应用展示
 *    可同时保留一个主屏 app 高亮；左右键跟随最近点击的对象
 *    ③ 内点主屏 app → subHomeApp（下键亮）；点内 app → subFolderApp（上键亮）
 *
 * 数据操作委托 GridRepository（区内移位/跨区转移/删除续补等已实现）。
 */
class OrganizeViewModel : ViewModel() {

    /** 文件夹页中左右键的当前操作焦点 */
    enum class SelectionFocus {
        FOLDER,
        HOME_APP,
        FOLDER_APP,
    }

    /** 整理目录状态（UI 渲染依据） */
    sealed interface OrganizeState {
        /** ① 无选中 */
        data object Empty : OrganizeState

        /** ② 选中主屏 app */
        data class HomeAppSelected(val app: GridItem) : OrganizeState

        /** ③ 选中文件夹（含子选择：subHomeApp / subFolderApp 互斥） */
        data class FolderSelected(
            val folderId: Long,
            val folderNameInput: String,
            val subHomeApp: GridItem? = null,
            val subFolderAppPkg: String? = null,
            val focus: SelectionFocus = SelectionFocus.FOLDER,
            /** 刚创建（true=自动聚焦全选名称，点选文件夹时为 false） */
            val justCreated: Boolean = false,
        ) : OrganizeState
    }

    private val _state = MutableStateFlow<OrganizeState>(OrganizeState.Empty)
    val state: StateFlow<OrganizeState> = _state.asStateFlow()

    /** 一次性提示事件（UI Snackbar） */
    private val _events = MutableStateFlow<String?>(null)
    val events: StateFlow<String?> = _events.asStateFlow()

    fun consumeEvent() {
        _events.value = null
    }

    /** 进入整理目录：主屏进入不选中，文件夹页进入自动选中当前文件夹。 */
    fun enter(initialFolderId: Long?) {
        val folder = initialFolderId?.let { id ->
            GridRepository.folders.value.firstOrNull { it.id == id }
        }
        _state.value = folder?.let {
            OrganizeState.FolderSelected(
                folderId = it.id,
                folderNameInput = it.name,
                focus = SelectionFocus.FOLDER,
            )
        } ?: OrganizeState.Empty
        _events.value = null
    }

    /**
     * 当前选中文件夹的成员（响应式 StateFlow：移动/增删后自动刷新，
     * 普通 getter 无法驱动 Compose 重组——移动后横排顺序不更新的根因）
     */
    val currentFolderApps: StateFlow<List<String>> = combine(
        _state,
        GridRepository.folderApps,
    ) { s, folderApps ->
        val sel = s as? OrganizeState.FolderSelected ?: return@combine emptyList()
        folderApps.filter { it.folderId == sel.folderId }
            .sortedBy { it.sortOrder }
            .map { it.pkg }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 当前选中文件夹 */
    val currentFolder: Folder?
        get() {
            val s = _state.value as? OrganizeState.FolderSelected ?: return null
            return GridRepository.folders.value.find { it.id == s.folderId }
        }

    // ═══════════════════════════════════════
    // 点选（状态机入口）
    // ═══════════════════════════════════════

    /** 点主屏 app：② 或 ③ 内 subHomeApp；最近点击的对象获得左右键焦点。 */
    fun tapHomeApp(item: GridItem) {
        when (val s = _state.value) {
            is OrganizeState.Empty -> _state.value = OrganizeState.HomeAppSelected(item)
            is OrganizeState.HomeAppSelected ->
                _state.value = OrganizeState.HomeAppSelected(item) // 换选
            is OrganizeState.FolderSelected ->
                // 保留文件夹高亮；主屏 app 获得左右键焦点（下键亮）
                _state.value = s.copy(
                    subHomeApp = item,
                    subFolderAppPkg = null,
                    focus = SelectionFocus.HOME_APP,
                )
        }
    }

    /** 点文件夹：③；已有主屏 app 高亮时保留它，文件夹获得左右键焦点。 */
    fun tapFolder(folder: Folder) {
        val pairedApp = when (val s = _state.value) {
            is OrganizeState.HomeAppSelected -> s.app
            is OrganizeState.FolderSelected -> s.subHomeApp
            else -> null
        }
        _state.value = OrganizeState.FolderSelected(
            folderId = folder.id,
            folderNameInput = folder.name,
            subHomeApp = pairedApp,
            focus = SelectionFocus.FOLDER,
            justCreated = false, // 点选文件夹：不自动聚焦（用户拍板）
        )
    }

    /** 点文件夹内 app：③ 内 subFolderApp（上键亮），该应用获得左右键焦点。 */
    fun tapFolderApp(pkg: String) {
        val s = _state.value as? OrganizeState.FolderSelected ?: return
        _state.value = s.copy(
            subFolderAppPkg = pkg,
            subHomeApp = null,
            focus = SelectionFocus.FOLDER_APP,
        )
    }

    // ═══════════════════════════════════════
    // 键位操作
    // ═══════════════════════════════════════

    /** 左/右：区内线性移位（不循环，到头即止） */
    fun shift(step: Int) {
        when (val s = _state.value) {
            is OrganizeState.HomeAppSelected -> {
                GridRepository.shiftItem(s.app.id, step)
            }
            is OrganizeState.FolderSelected -> {
                when (s.focus) {
                    SelectionFocus.HOME_APP -> s.subHomeApp?.let {
                        GridRepository.shiftItem(it.id, step)
                    }
                    SelectionFocus.FOLDER_APP -> s.subFolderAppPkg?.let {
                        GridRepository.shiftFolderApp(s.folderId, it, step)
                    }
                    SelectionFocus.FOLDER -> {
                        // 文件夹在主屏移位：找到对应 GridItem
                        val item = GridRepository.gridItems.value
                            .find { it.type == "folder" && it.folderId == s.folderId }
                        item?.let { GridRepository.shiftItem(it.id, step) }
                    }
                }
            }
            else -> Unit
        }
    }

    /** 下键：选中的主屏 app 加入文件夹最后 */
    fun moveDown() {
        val s = _state.value as? OrganizeState.FolderSelected ?: return
        val app = s.subHomeApp ?: return
        GridRepository.moveAppToFolder(app.pkg!!, s.folderId)
        // 移动完成后自动聚焦文件夹下栏中刚加入的应用。
        _state.value = s.copy(
            subHomeApp = null,
            subFolderAppPkg = app.pkg,
            focus = SelectionFocus.FOLDER_APP,
        )
    }

    /** 上键：选中的文件夹内 app 移回主屏最后 */
    fun moveUp() {
        val s = _state.value as? OrganizeState.FolderSelected ?: return
        val pkg = s.subFolderAppPkg ?: return
        GridRepository.moveAppToHome(pkg)
        _state.value = s.copy(
            subFolderAppPkg = null,
            focus = SelectionFocus.FOLDER,
        )
    }

    /** 创建：新建文件夹并自动选中（自动聚焦全选名称，用户拍板） */
    fun createFolder() {
        val name = nextFolderName()
        val folder = GridRepository.createFolder(name)
        _state.value = OrganizeState.FolderSelected(
            folderId = folder.id,
            folderNameInput = name,
            focus = SelectionFocus.FOLDER,
            justCreated = true,
        )
    }

    /**
     * 新文件夹名：已有名字里「文件夹N」最大编号 +1；
     * 若该名字已被占用（用户改过名/删过文件夹），继续 +1 直到不重名。
     */
    private fun nextFolderName(): String {
        val regex = Regex("^文件夹(\\d+)$")
        val maxN = GridRepository.folders.value
            .mapNotNull { regex.matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull() }
            .maxOrNull() ?: 0
        var n = maxN + 1
        val names = GridRepository.folders.value.map { it.name }.toSet()
        while ("文件夹$n" in names) n++
        return "文件夹$n"
    }

    /** 减号：只对选中文件夹，直接删除（用户拍板不需要二次确认）；未选中时给提示 */
    fun requestDeleteFolder() {
        val folder = currentFolder
        if (folder == null) {
            _events.value = "请先选择一个文件夹"
        } else {
            GridRepository.deleteFolder(folder.id)
            _state.value = OrganizeState.Empty
        }
    }

    /** 文件夹名输入（③ 底部第一行） */
    fun updateFolderName(name: String) {
        val s = _state.value as? OrganizeState.FolderSelected ?: return
        _state.value = s.copy(folderNameInput = name)
    }

    /** 确认改名（输入完成时调用） */
    fun commitFolderName() {
        val s = _state.value as? OrganizeState.FolderSelected ?: return
        if (s.folderNameInput.isNotBlank()) {
            GridRepository.renameFolder(s.folderId, s.folderNameInput.trim())
        }
    }

}
