package com.nbljsbdk.snowhide.data.repo

import android.content.Context
import com.nbljsbdk.snowhide.core.model.PackageName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * 快速启停数据仓库——成员列表和本次点亮快照的唯一所有者。
 *
 * 继续使用既有 `snowhide_settings` SP 和 JSON 数组格式，避免迁移已保存数据。
 * ViewModel、UseCase、TileService 不得再直接解析这两个 key。
 */
object QuickToggleRepository {

    private const val PREFS_NAME = "snowhide_settings"
    private const val KEY_MEMBERS = "quick_toggle_members"
    private const val KEY_OPENED = "quick_toggle_opened"

    private lateinit var prefs: android.content.SharedPreferences

    private val _members = MutableStateFlow<List<String>>(emptyList())
    /** 快速启停成员，保留用户加入顺序。 */
    val members: StateFlow<List<String>> = _members.asStateFlow()

    private val _opened = MutableStateFlow<List<String>>(emptyList())
    /** 最近一次点亮动作实际解冻的应用快照。 */
    val opened: StateFlow<List<String>> = _opened.asStateFlow()

    /** 初始化并读取旧 JSON 数据（幂等、线程安全）。 */
    @Synchronized
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _members.value = read(KEY_MEMBERS)
        _opened.value = read(KEY_OPENED)
    }

    /** 加入成员，已存在时保持原顺序。 */
    @Synchronized
    fun addMember(pkg: String): Boolean {
        if (!PackageName.isValid(pkg) || pkg in _members.value) return false
        _members.value = _members.value + pkg
        persist(KEY_MEMBERS, _members.value)
        return true
    }

    /** 移出成员。 */
    @Synchronized
    fun removeMember(pkg: String): Boolean {
        if (pkg !in _members.value) return false
        _members.value = _members.value.filterNot { it == pkg }
        persist(KEY_MEMBERS, _members.value)
        return true
    }

    /** 清理已从“已添加”体系移出的成员，并保留剩余顺序。 */
    @Synchronized
    fun replaceMembers(packages: Collection<String>) {
        val cleaned = packages.filter(PackageName::isValid).distinct()
        if (cleaned == _members.value) return
        _members.value = cleaned
        persist(KEY_MEMBERS, cleaned)
    }

    /** 保存本次成功点亮的应用快照。 */
    @Synchronized
    fun setOpened(packages: Collection<String>) {
        val cleaned = packages.filter(PackageName::isValid).distinct()
        _opened.value = cleaned
        persist(KEY_OPENED, cleaned)
    }

    /** 熄灭流程完成后清空快照。 */
    @Synchronized
    fun clearOpened() {
        if (_opened.value.isEmpty()) return
        _opened.value = emptyList()
        persist(KEY_OPENED, emptyList())
    }

    private fun read(key: String): List<String> {
        val json = prefs.getString(key, "[]") ?: "[]"
        return runCatching {
            JSONArray(json).let { array ->
                (0 until array.length())
                    .map { array.getString(it) }
                    .filter(PackageName::isValid)
                    .distinct()
            }
        }.getOrDefault(emptyList())
    }

    private fun persist(key: String, packages: Collection<String>) {
        val array = JSONArray()
        packages.filter(PackageName::isValid).distinct().forEach(array::put)
        prefs.edit().putString(key, array.toString()).apply()
    }
}
