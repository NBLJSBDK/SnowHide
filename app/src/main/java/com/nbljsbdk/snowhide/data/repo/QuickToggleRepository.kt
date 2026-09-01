package com.nbljsbdk.snowhide.data.repo

import android.content.Context
import com.nbljsbdk.snowhide.core.model.AppTarget
import com.nbljsbdk.snowhide.core.model.QuickToggleStore
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
object QuickToggleRepository : QuickToggleStore {

    private const val PREFS_NAME = "snowhide_settings"
    private const val KEY_MEMBERS = "quick_toggle_members"
    private const val KEY_OPENED = "quick_toggle_opened"

    private lateinit var prefs: android.content.SharedPreferences

    private val _members = MutableStateFlow<List<AppTarget>>(emptyList())
    /** 快速启停成员，保留用户加入顺序。 */
    override val members: StateFlow<List<AppTarget>> = _members.asStateFlow()

    private val _opened = MutableStateFlow<List<AppTarget>>(emptyList())
    /** 最近一次点亮动作实际解冻的应用快照。 */
    override val opened: StateFlow<List<AppTarget>> = _opened.asStateFlow()

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
    fun addMember(target: AppTarget): Boolean {
        if (target in _members.value) return false
        _members.value = _members.value + target
        persist(KEY_MEMBERS, _members.value)
        return true
    }

    /** 旧 user 0 调用适配，避免已保存设置和旧入口失效。 */
    fun addMember(pkg: String): Boolean =
        AppTarget.create(pkg, AppTarget.PRIMARY_USER_ID).getOrNull()?.let(::addMember) == true

    /** 移出成员。 */
    @Synchronized
    fun removeMember(target: AppTarget): Boolean {
        if (target !in _members.value) return false
        _members.value = _members.value.filterNot { it == target }
        persist(KEY_MEMBERS, _members.value)
        return true
    }

    /** 旧 user 0 调用适配。 */
    fun removeMember(pkg: String): Boolean =
        AppTarget.create(pkg, AppTarget.PRIMARY_USER_ID).getOrNull()?.let(::removeMember) == true

    /** 清理已从“已添加”体系移出的成员，并保留剩余顺序。 */
    @Synchronized
    fun replaceTargetMembers(targets: Collection<AppTarget>) {
        val cleaned = targets.distinct()
        if (cleaned == _members.value) return
        _members.value = cleaned
        persist(KEY_MEMBERS, cleaned)
    }

    /** 旧 user 0 成员数组适配。 */
    fun replaceMembers(packages: Collection<String>) {
        replaceTargetMembers(
            packages.mapNotNull { AppTarget.create(it, AppTarget.PRIMARY_USER_ID).getOrNull() },
        )
    }

    /** 保存本次成功点亮的应用快照。 */
    @Synchronized
    override fun setOpened(targets: Collection<AppTarget>) {
        val cleaned = targets.distinct()
        _opened.value = cleaned
        persist(KEY_OPENED, cleaned)
    }

    /** 熄灭流程完成后清空快照。 */
    @Synchronized
    override fun clearOpened() {
        if (_opened.value.isEmpty()) return
        _opened.value = emptyList()
        persist(KEY_OPENED, emptyList())
    }

    private fun read(key: String): List<AppTarget> {
        val json = prefs.getString(key, "[]") ?: "[]"
        return runCatching {
            JSONArray(json).let { array ->
                (0 until array.length())
                    .mapNotNull { parseTarget(array.opt(it)) }
                    .distinct()
            }
        }.getOrDefault(emptyList())
    }

    private fun parseTarget(value: Any?): AppTarget? = when (value) {
        is String -> AppTarget.create(value, AppTarget.PRIMARY_USER_ID).getOrNull()
        is org.json.JSONObject -> {
            val pkg = value.optString("pkg")
            val userId = value.optInt("userId", AppTarget.PRIMARY_USER_ID)
            AppTarget.create(pkg, userId).getOrNull()
        }
        else -> null
    }

    /** user 0 仍写旧字符串格式，分身写带 userId 的对象，兼容旧版本。 */
    private fun persist(key: String, targets: Collection<AppTarget>) {
        val array = JSONArray()
        targets.distinct().forEach { target ->
            if (target.isPrimaryUser) {
                array.put(target.packageName.value)
            } else {
                array.put(
                    org.json.JSONObject()
                        .put("pkg", target.packageName.value)
                        .put("userId", target.userId),
                )
            }
        }
        prefs.edit().putString(key, array.toString()).apply()
    }
}
