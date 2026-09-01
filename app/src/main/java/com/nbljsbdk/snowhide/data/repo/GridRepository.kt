package com.nbljsbdk.snowhide.data.repo

import android.content.Context
import com.nbljsbdk.snowhide.core.model.AppTarget
import com.nbljsbdk.snowhide.core.model.FreezeTargetStore
import com.nbljsbdk.snowhide.data.model.Folder
import com.nbljsbdk.snowhide.data.model.FolderApp
import com.nbljsbdk.snowhide.data.model.GridItem
import com.nbljsbdk.snowhide.data.model.migrateLegacyLockedTargets
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
object GridRepository : FreezeTargetStore {

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
            .groupBy { it.folderId to (it.userId to it.pkg) }
            .mapValues { (_, list) -> list.minByOrNull { it.sortOrder }!! }
            .values
            .toList()
        if (cleanedApps.size != _folderApps.value.size) {
            _folderApps.value = cleanedApps
            persistFolderApps()
        }
        // 锁定集：独立存储（覆盖文件夹内应用）；旧版 GridItem.locked=true 迁移进来
        val persistedLocked = loadLockedTargets()
        val hadLegacyLockedField = _gridItems.value.any { it.locked }
        val lockMigration = migrateLegacyLockedTargets(_gridItems.value, persistedLocked)
        _gridItems.value = lockMigration.items
        setLockedTargets(lockMigration.lockedTargets)
        if (hadLegacyLockedField) {
            persistLocked()
            persist()
        }
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
    /** 文件夹元数据；循环页面顺序由主屏混排中的文件夹项决定 */
    val folders: StateFlow<List<Folder>> = _folders.asStateFlow()

    private val _folderApps = MutableStateFlow<List<FolderApp>>(emptyList())
    /** 全部文件夹成员关系（按 sortOrder 升序） */
    val folderApps: StateFlow<List<FolderApp>> = _folderApps.asStateFlow()

    private val _lockedTargets = MutableStateFlow<Set<AppTarget>>(emptySet())
    /** 锁定应用目标集合（覆盖主屏与文件夹成员，持久化） */
    val lockedTargets: StateFlow<Set<AppTarget>> = _lockedTargets.asStateFlow()

    private val _lockedPackages = MutableStateFlow<Set<String>>(emptySet())
    /** 旧 user 0 锁定包名投影，Recent 等旧入口继续使用。 */
    val lockedPackages: StateFlow<Set<String>> = _lockedPackages.asStateFlow()

    // ═══════════════════════════════════════
    // 应用管理（增加应用界面）
    // ═══════════════════════════════════════

    /** 应用是否已添加（主屏或任一文件夹） */
    override fun isAppAdded(pkg: String): Boolean {
        val target = AppTarget.create(pkg, AppTarget.PRIMARY_USER_ID).getOrNull() ?: return false
        return isAppAdded(target)
    }

    /** 目标应用是否已添加（userId + packageName 必须同时匹配）。 */
    override fun isAppAdded(target: AppTarget): Boolean =
        _gridItems.value.any { it.type == "app" && it.target() == target } ||
            _folderApps.value.any { it.target() == target }

    /** 添加应用到主屏末尾 */
    fun addAppToHome(pkg: String) {
        AppTarget.create(pkg, AppTarget.PRIMARY_USER_ID).getOrNull()?.let(::addTargetToHome)
    }

    /** 添加明确用户空间的应用到主屏末尾。 */
    fun addTargetToHome(target: AppTarget) {
        if (isAppAdded(target)) return
        ListOrderRepository.recordAppManageAdded(target.key)
        val items = _gridItems.value.toMutableList()
        items.add(
            GridItem(
                id = nextId(),
                type = "app",
                pkg = target.packageName.value,
                sortOrder = (items.maxOfOrNull { it.sortOrder } ?: -1) + 1,
                userId = target.userId,
            )
        )
        _gridItems.value = items
        persist()
    }

    /** 从宫格体系完全移除应用（移除应用界面：解冻并移出） */
    fun removeApp(pkg: String) {
        AppTarget.create(pkg, AppTarget.PRIMARY_USER_ID).getOrNull()?.let(::removeTarget)
    }

    /** 从宫格体系完全移除明确目标，不能误删同包名的其他用户目标。 */
    fun removeTarget(target: AppTarget) {
        if (isAppAdded(target)) ListOrderRepository.recordAppManageRemoved(target.key)
        _gridItems.value = _gridItems.value.filterNot { it.type == "app" && it.target() == target }
        _folderApps.value = _folderApps.value.filterNot { it.target() == target }
        if (target in _lockedTargets.value) {
            setLockedTargets(_lockedTargets.value - target)
            persistLocked()
        }
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
                    userId = member.userId,
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
        AppTarget.create(pkg, AppTarget.PRIMARY_USER_ID).getOrNull()?.let {
            shiftFolderApp(folderId, it, step)
        }
    }

    /** 文件夹内明确目标移位（同包名多用户互不串位）。 */
    fun shiftFolderApp(folderId: Long, target: AppTarget, step: Int) {
        val members = _folderApps.value.filter { it.folderId == folderId }.sortedBy { it.sortOrder }.toMutableList()
        val index = members.indexOfFirst { it.target() == target }
        val targetIndex = index + step
        if (index < 0 || targetIndex < 0 || targetIndex >= members.size) return
        val a = members[index]
        val b = members[targetIndex]
        members[index] = b
        members[targetIndex] = a
        val others = _folderApps.value.filter { it.folderId != folderId }
        _folderApps.value = others + members.mapIndexed { i, m -> m.copy(sortOrder = i) }
        persist()
    }

    /** 上/下跨区转移：主屏 app 加入文件夹最后（下键） */
    fun moveAppToFolder(pkg: String, folderId: Long) {
        AppTarget.create(pkg, AppTarget.PRIMARY_USER_ID).getOrNull()?.let {
            moveAppToFolder(it, folderId)
        }
    }

    /** 将明确目标从主屏移入文件夹。 */
    fun moveAppToFolder(target: AppTarget, folderId: Long) {
        _gridItems.value = _gridItems.value.filterNot { it.type == "app" && it.target() == target }
        // 防重：目标文件夹已存在该成员则跳过（重复 pkg 会导致
        // FolderScreen 的 LazyGrid key 重复崩溃，真机实锤）
        if (_folderApps.value.any { it.folderId == folderId && it.target() == target }) {
            persist()
            return
        }
        val members = _folderApps.value.filter { it.folderId == folderId }
        _folderApps.value = _folderApps.value + FolderApp(
            folderId = folderId,
            pkg = target.packageName.value,
            sortOrder = (members.maxOfOrNull { it.sortOrder } ?: -1) + 1,
            userId = target.userId,
        )
        persist()
    }

    /** 上/下跨区转移：文件夹内 app 移回主屏最后（上键） */
    fun moveAppToHome(pkg: String) {
        AppTarget.create(pkg, AppTarget.PRIMARY_USER_ID).getOrNull()?.let(::moveAppToHome)
    }

    /** 将明确目标从文件夹移回主屏最后。 */
    fun moveAppToHome(target: AppTarget) {
        val member = _folderApps.value.firstOrNull { it.target() == target } ?: return
        _folderApps.value = _folderApps.value.filterNot { it.target() == target }
        val items = _gridItems.value.toMutableList()
        items.add(
            GridItem(
                id = nextId(),
                type = "app",
                pkg = target.packageName.value,
                sortOrder = (items.maxOfOrNull { it.sortOrder } ?: -1) + 1,
                userId = member.userId,
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
        AppTarget.create(pkg, AppTarget.PRIMARY_USER_ID).getOrNull()?.let(::toggleLock)
    }

    /** 切换明确目标的 Dock 锁定状态。 */
    fun toggleLock(target: AppTarget) {
        setLockedTargets(if (target in _lockedTargets.value) {
            _lockedTargets.value - target
        } else {
            _lockedTargets.value + target
        })
        persistLocked()
    }

    /** 查询应用锁定状态（主屏与文件夹成员通用） */
    override fun isLocked(pkg: String): Boolean =
        AppTarget.create(pkg, AppTarget.PRIMARY_USER_ID).getOrNull()?.let(::isLocked) == true

    override fun isLocked(target: AppTarget): Boolean = target in _lockedTargets.value

    private fun setLockedTargets(targets: Set<AppTarget>) {
        _lockedTargets.value = targets
        _lockedPackages.value = targets
            .filter { it.isPrimaryUser }
            .map { it.packageName.value }
            .toSet()
    }

    /** 锁定集持久化 */
    private fun persistLocked() {
        if (!::prefs.isInitialized) return
        val arr = org.json.JSONArray()
        _lockedTargets.value.forEach { target ->
            if (target.isPrimaryUser) {
                arr.put(target.packageName.value)
            } else {
                arr.put(
                    JSONObject()
                        .put("pkg", target.packageName.value)
                        .put("userId", target.userId),
                )
            }
        }
        prefs.edit().putString(KEY_LOCKED, arr.toString()).apply()
    }

    private fun loadLockedTargets(): Set<AppTarget> {
        if (!::prefs.isInitialized) return emptySet()
        val json = prefs.getString(KEY_LOCKED, "[]") ?: "[]"
        return runCatching {
            org.json.JSONArray(json).let { arr ->
                buildSet {
                    for (i in 0 until arr.length()) {
                        when (val value = arr.opt(i)) {
                            is String -> AppTarget.create(value, AppTarget.PRIMARY_USER_ID)
                                .getOrNull()?.let(::add)
                            is JSONObject -> AppTarget.create(
                                value.optString("pkg"),
                                value.optInt("userId", AppTarget.PRIMARY_USER_ID),
                            ).getOrNull()?.let(::add)
                        }
                    }
                }
            }
        }.getOrDefault(emptySet())
    }

    // ═══════════════════════════════════════
    // 循环滑动序列（设计文档 §3.2）
    // ═══════════════════════════════════════

    /** user 0 已添加包名投影，供 Recent/旧入口使用。 */
    override fun allAddedPackages(): List<String> =
        allAddedTargets()
            .filter { it.isPrimaryUser }
            .map { it.packageName.value }
            .distinct()

    /** 已添加的全部明确目标。 */
    override fun allAddedTargets(): List<AppTarget> =
        (_gridItems.value.mapNotNull { it.target() } + _folderApps.value.mapNotNull { it.target() })
            .distinct()

    override fun folderPackages(folderId: Long): List<String> =
        folderTargets(folderId)
            .filter { it.isPrimaryUser }
            .map { it.packageName.value }

    override fun folderTargets(folderId: Long): List<AppTarget> =
        _folderApps.value
            .filter { it.folderId == folderId }
            .sortedBy { it.sortOrder }
            .mapNotNull { it.target() }

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
                     .put("userId", item.userId)
            })
            .putString(KEY_FOLDERS, toJson(_folders.value) { obj, folder ->
                obj.put("id", folder.id).put("name", folder.name).put("sortOrder", folder.sortOrder)
            })
            .putString(KEY_FOLDER_APPS, toJson(_folderApps.value) { obj, fa ->
                obj.put("folderId", fa.folderId).put("pkg", fa.pkg)
                    .put("sortOrder", fa.sortOrder).put("userId", fa.userId)
            })
            .apply()
    }

    /** 仅持久化文件夹成员（init 清理重复后调用） */
    private fun persistFolderApps() {
        if (!::prefs.isInitialized) return
        prefs.edit()
            .putString(KEY_FOLDER_APPS, toJson(_folderApps.value) { obj, fa ->
                obj.put("folderId", fa.folderId).put("pkg", fa.pkg)
                    .put("sortOrder", fa.sortOrder).put("userId", fa.userId)
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
            userId = obj.optInt("userId", AppTarget.PRIMARY_USER_ID),
        )
    }

    private fun loadFolders(): List<Folder> = load(KEY_FOLDERS) { obj ->
        Folder(obj.optLong("id"), obj.optString("name"), obj.optInt("sortOrder"))
    }

    private fun loadFolderApps(): List<FolderApp> = load(KEY_FOLDER_APPS) { obj ->
        FolderApp(
            folderId = obj.optLong("folderId"),
            pkg = obj.optString("pkg"),
            sortOrder = obj.optInt("sortOrder"),
            userId = obj.optInt("userId", AppTarget.PRIMARY_USER_ID),
        )
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

    private fun GridItem.target(): AppTarget? = if (type == "app" && pkg != null) {
        AppTarget.create(pkg, userId).getOrNull()
    } else null

    private fun FolderApp.target(): AppTarget? =
        AppTarget.create(pkg, userId).getOrNull()
}
