package com.nbljsbdk.snowhide.data.repo

import android.content.Context
import com.nbljsbdk.snowhide.data.model.Folder
import com.nbljsbdk.snowhide.data.model.FolderApp
import com.nbljsbdk.snowhide.data.model.GridItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * 宫格数据仓库——主屏混排 / 文件夹 / 排序 / 整理目录的全部数据操作
 *
 * P0 实现：SharedPreferences + JSON（数据规模小：几十个应用 + 文件夹，
 * 无需 Room；MediaSync 同款已验证方案）。
 *
 * 单例设计：全工程唯一数据源，各 ViewModel 共享同一实例，
 * 避免多处各自 new 导致状态不同步。
 *
 * UI 永不直连本仓库——所有读写通过 domain 层用例。
 */
object GridRepository {

    private lateinit var prefs: android.content.SharedPreferences

    /** 初始化（Application 启动时调用一次） */
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences("snowhide_grid", Context.MODE_PRIVATE)
        // 数据加载必须放在 prefs 就绪之后（对象初始化时 prefs 尚未就绪，
        // 之前版本在这里读取导致重启后数据全部加载为空）
        _gridItems.value = loadGridItems()
        _folders.value = loadFolders()
        _folderApps.value = loadFolderApps()
        // 清理历史重复的文件夹成员（同文件夹同 pkg 只保留 sortOrder 最小的——
        // 重复会导致 FolderScreen LazyGrid key 崩溃，真机实锤）
        val cleanedApps = _folderApps.value
            .groupBy { it.folderId to it.pkg }
            .mapValues { (_, list) -> list.minByOrNull { it.sortOrder }!! }
            .values
            .toList()
        if (cleanedApps.size != _folderApps.value.size) {
            _folderApps.value = cleanedApps
            persistFolderApps()
        }
        // 锁定集：独立存储（覆盖文件夹内应用）；旧版 GridItem.locked=true 迁移进来
        val migratedLocked = _gridItems.value.filter { it.locked }.mapNotNull { it.pkg }
        _lockedPackages.value = loadLockedPackages() + migratedLocked
        if (migratedLocked.isNotEmpty()) persistLocked()
        // 关键：把 id 种子推进到已有数据最大 id 之上。
        // 种子默认=当前毫秒，但进程被系统杀掉重启后时间可能回拨
        // （手动调时间/网络校时），新 id 撞上旧数据 id 会导致
        // 选中高亮、文件夹成员显示到错误文件夹上。
        val maxExisting = maxOf(
            _gridItems.value.maxOfOrNull { it.id } ?: 0L,
            _folders.value.maxOfOrNull { it.id } ?: 0L,
            _folderApps.value.maxOfOrNull { it.folderId } ?: 0L,
        )
        idCounter.accumulateAndGet(maxExisting) { prev, max ->
            if (prev > max) prev else max
        }
    }

    private val _gridItems = MutableStateFlow<List<GridItem>>(emptyList())
    /** 主屏混排项（按 sortOrder 升序） */
    val gridItems: StateFlow<List<GridItem>> = _gridItems.asStateFlow()

    private val _folders = MutableStateFlow<List<Folder>>(emptyList())
    /** 文件夹列表（按 sortOrder 升序，即循环滑动顺序） */
    val folders: StateFlow<List<Folder>> = _folders.asStateFlow()

    private val _folderApps = MutableStateFlow<List<FolderApp>>(emptyList())
    /** 全部文件夹成员关系（按 sortOrder 升序） */
    val folderApps: StateFlow<List<FolderApp>> = _folderApps.asStateFlow()

    private val _lockedPackages = MutableStateFlow<Set<String>>(emptySet())
    /** 锁定应用包名集合（覆盖主屏与文件夹成员，持久化） */
    val lockedPackages: StateFlow<Set<String>> = _lockedPackages.asStateFlow()

    // ═══════════════════════════════════════
    // 应用管理（增加应用界面）
    // ═══════════════════════════════════════

    /** 应用是否已添加（主屏或任一文件夹） */
    fun isAppAdded(pkg: String): Boolean =
        _gridItems.value.any { it.pkg == pkg } || _folderApps.value.any { it.pkg == pkg }

    /** 添加应用到主屏末尾 */
    fun addAppToHome(pkg: String) {
        if (isAppAdded(pkg)) return
        val items = _gridItems.value.toMutableList()
        items.add(
            GridItem(
                id = nextId(),
                type = "app",
                pkg = pkg,
                sortOrder = (items.maxOfOrNull { it.sortOrder } ?: -1) + 1,
            )
        )
        _gridItems.value = items
        persist()
    }

    /** 从宫格体系完全移除应用（移除应用界面：解冻并移出） */
    fun removeApp(pkg: String) {
        _gridItems.value = _gridItems.value.filterNot { it.pkg == pkg }
        _folderApps.value = _folderApps.value.filterNot { it.pkg == pkg }
        persist()
    }

    // ═══════════════════════════════════════
    // 文件夹操作（整理目录 + 文件夹长按菜单）
    // ═══════════════════════════════════════

    /** 创建文件夹并加入主屏最前（用户拍板：新文件夹排最前，其余依次后移） */
    fun createFolder(name: String): Folder {
        val folder = Folder(
            id = nextId(),
            name = name,
            sortOrder = 0,
        )
        // 已有文件夹与主屏项全部后移一位，新文件夹占首位
        _folders.value = listOf(folder) + getAllFolders().map { it.copy(sortOrder = it.sortOrder + 1) }
        _gridItems.value = listOf(
            GridItem(
                id = nextId(),
                type = "folder",
                folderId = folder.id,
                sortOrder = 0,
            )
        ) + _gridItems.value.map { it.copy(sortOrder = it.sortOrder + 1) }
        persist()
        return folder
    }

    /** 重命名文件夹 */
    fun renameFolder(folderId: Long, name: String) {
        _folders.value = _folders.value.map { if (it.id == folderId) it.copy(name = name) else it }
        persist()
    }

    /**
     * 删除文件夹（整理目录减号，点击后立即执行）
     * 文件夹内应用按原有 sortOrder 续补到主屏后面（应用不丢，设计文档 §3.10）
     */
    fun deleteFolder(folderId: Long) {
        val members = _folderApps.value.filter { it.folderId == folderId }.sortedBy { it.sortOrder }
        val items = _gridItems.value.filterNot { it.folderId == folderId }.toMutableList()
        var maxSort = items.maxOfOrNull { it.sortOrder } ?: -1
        members.forEach { member ->
            maxSort++
            items.add(
                GridItem(
                    id = nextId(),
                    type = "app",
                    pkg = member.pkg,
                    sortOrder = maxSort,
                )
            )
        }
        _gridItems.value = items
        _folders.value = _folders.value.filterNot { it.id == folderId }
        _folderApps.value = _folderApps.value.filterNot { it.folderId == folderId }
        persist()
    }

    // ═══════════════════════════════════════
    // 整理目录移动操作（状态机键位规则，设计文档 §3.10）
    // ═══════════════════════════════════════

    /** 区内线性移位：把 item 向左(step=-1)/右(step=+1)移动一位，不循环、到头即止 */
    fun shiftItem(itemId: Long, step: Int) {
        val items = _gridItems.value.sortedBy { it.sortOrder }.toMutableList()
        val index = items.indexOfFirst { it.id == itemId }
        val target = index + step
        if (index < 0 || target < 0 || target >= items.size) return // 到头即止
        // 与相邻项交换 sortOrder
        val a = items[index]
        val b = items[target]
        items[index] = b
        items[target] = a
        _gridItems.value = items.mapIndexed { i, item -> item.copy(sortOrder = i) }
        persist()
    }

    /** 文件夹内应用移位（区内排序，不循环） */
    fun shiftFolderApp(folderId: Long, pkg: String, step: Int) {
        val members = _folderApps.value.filter { it.folderId == folderId }.sortedBy { it.sortOrder }.toMutableList()
        val index = members.indexOfFirst { it.pkg == pkg }
        val target = index + step
        if (index < 0 || target < 0 || target >= members.size) return
        val a = members[index]
        val b = members[target]
        members[index] = b
        members[target] = a
        val others = _folderApps.value.filter { it.folderId != folderId }
        _folderApps.value = others + members.mapIndexed { i, m -> m.copy(sortOrder = i) }
        persist()
    }

    /** 上/下跨区转移：主屏 app 加入文件夹最后（下键） */
    fun moveAppToFolder(pkg: String, folderId: Long) {
        _gridItems.value = _gridItems.value.filterNot { it.pkg == pkg }
        // 防重：目标文件夹已存在该成员则跳过（重复 pkg 会导致
        // FolderScreen 的 LazyGrid key 重复崩溃，真机实锤）
        if (_folderApps.value.any { it.folderId == folderId && it.pkg == pkg }) {
            persist()
            return
        }
        val members = _folderApps.value.filter { it.folderId == folderId }
        _folderApps.value = _folderApps.value + FolderApp(
            folderId = folderId,
            pkg = pkg,
            sortOrder = (members.maxOfOrNull { it.sortOrder } ?: -1) + 1,
        )
        persist()
    }

    /** 上/下跨区转移：文件夹内 app 移回主屏最后（上键） */
    fun moveAppToHome(pkg: String) {
        _folderApps.value = _folderApps.value.filterNot { it.pkg == pkg }
        val items = _gridItems.value.toMutableList()
        items.add(
            GridItem(
                id = nextId(),
                type = "app",
                pkg = pkg,
                sortOrder = (items.maxOfOrNull { it.sortOrder } ?: -1) + 1,
            )
        )
        _gridItems.value = items
        persist()
    }

    // ═══════════════════════════════════════
    // 底部图标栏锁定
    // ═══════════════════════════════════════

    /** 切换底部图标栏锁定（持久化，豁免快速清理/磁贴熄灭冻回） */
    fun toggleLock(pkg: String) {
        _lockedPackages.value = if (pkg in _lockedPackages.value) {
            _lockedPackages.value - pkg
        } else {
            _lockedPackages.value + pkg
        }
        persistLocked()
    }

    /** 查询应用锁定状态（主屏与文件夹成员通用） */
    fun isLocked(pkg: String): Boolean = pkg in _lockedPackages.value

    /** 锁定集持久化 */
    private fun persistLocked() {
        if (!::prefs.isInitialized) return
        val arr = org.json.JSONArray()
        _lockedPackages.value.forEach { arr.put(it) }
        prefs.edit().putString(KEY_LOCKED, arr.toString()).apply()
    }

    private fun loadLockedPackages(): Set<String> {
        if (!::prefs.isInitialized) return emptySet()
        val json = prefs.getString(KEY_LOCKED, "[]") ?: "[]"
        return runCatching {
            org.json.JSONArray(json).let { arr ->
                (0 until arr.length()).map { arr.getString(it) }.toSet()
            }
        }.getOrDefault(emptySet())
    }

    // ═══════════════════════════════════════
    // 循环滑动序列（设计文档 §3.2）
    // ═══════════════════════════════════════

    /** 已添加的全部应用包名（底部图标栏数据源：已添加且解冻的应用） */
    fun allAddedPackages(): List<String> =
        (_gridItems.value.mapNotNull { it.pkg } + _folderApps.value.map { it.pkg }).distinct()

    // ═══════════════════════════════════════
    // 持久化（SharedPreferences + JSON）
    // ═══════════════════════════════════════

    /**
     * ID 生成：进程内自增（初始值=当前毫秒）。
     * 不能用 System.currentTimeMillis() 直接做 id——连续快速操作
     * （如连点创建文件夹）在同一毫秒内会生成相同 id，
     * LazyGrid key 冲突导致布局错乱。
     */
    private val idCounter = java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis())
    private fun nextId(): Long = idCounter.incrementAndGet()

    private fun getAllFolders(): List<Folder> = _folders.value

    private fun persist() {
        if (!::prefs.isInitialized) return
        prefs.edit()
            .putString(KEY_ITEMS, toJson(_gridItems.value) { obj, item ->
                obj.put("id", item.id)
                    .put("type", item.type)
                    .put("pkg", item.pkg ?: JSONObject.NULL)
                    .put("folderId", item.folderId ?: JSONObject.NULL)
                    .put("sortOrder", item.sortOrder)
                    .put("frozenMode", item.frozenMode)
                    .put("locked", item.locked)
            })
            .putString(KEY_FOLDERS, toJson(_folders.value) { obj, folder ->
                obj.put("id", folder.id).put("name", folder.name).put("sortOrder", folder.sortOrder)
            })
            .putString(KEY_FOLDER_APPS, toJson(_folderApps.value) { obj, fa ->
                obj.put("folderId", fa.folderId).put("pkg", fa.pkg).put("sortOrder", fa.sortOrder)
            })
            .apply()
    }

    /** 仅持久化文件夹成员（init 清理重复后调用） */
    private fun persistFolderApps() {
        if (!::prefs.isInitialized) return
        prefs.edit()
            .putString(KEY_FOLDER_APPS, toJson(_folderApps.value) { obj, fa ->
                obj.put("folderId", fa.folderId).put("pkg", fa.pkg).put("sortOrder", fa.sortOrder)
            })
            .apply()
    }

    private fun loadGridItems(): List<GridItem> = load(KEY_ITEMS) { obj ->
        GridItem(
            id = obj.optLong("id"),
            type = obj.optString("type"),
            pkg = if (obj.isNull("pkg")) null else obj.optString("pkg"),
            folderId = if (obj.isNull("folderId")) null else obj.optLong("folderId"),
            sortOrder = obj.optInt("sortOrder"),
            frozenMode = obj.optString("frozenMode", "FREEZE"),
            locked = obj.optBoolean("locked", false),
        )
    }

    private fun loadFolders(): List<Folder> = load(KEY_FOLDERS) { obj ->
        Folder(obj.optLong("id"), obj.optString("name"), obj.optInt("sortOrder"))
    }

    private fun loadFolderApps(): List<FolderApp> = load(KEY_FOLDER_APPS) { obj ->
        FolderApp(obj.optLong("folderId"), obj.optString("pkg"), obj.optInt("sortOrder"))
    }

    private fun <T> load(key: String, parse: (JSONObject) -> T): List<T> {
        if (!::prefs.isInitialized) return emptyList()
        val json = prefs.getString(key, "[]") ?: "[]"
        val array = JSONArray(json)
        val list = mutableListOf<T>()
        for (i in 0 until array.length()) {
            list.add(parse(array.getJSONObject(i)))
        }
        return list
    }

    private fun <T> toJson(list: List<T>, fill: (JSONObject, T) -> JSONObject): String {
        val array = JSONArray()
        list.forEach { item -> array.put(fill(JSONObject(), item)) }
        return array.toString()
    }

    private const val KEY_ITEMS = "grid_items"
    private const val KEY_FOLDERS = "folders"
    private const val KEY_FOLDER_APPS = "folder_apps"
    private const val KEY_LOCKED = "locked_packages"
}
