package com.nbljsbdk.snowhide.feature.quicktoggle

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nbljsbdk.snowhide.data.repo.GridRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 快速启停管理 ViewModel（设计文档 §3.9，用户拍板终版）
 *
 * - 界面仿增删应用的左右分栏
 * - 数据源 = **已添加应用**（不是全部已装应用）
 * - 左栏 = 已添加但未加入快速启停的应用
 * - 右栏 = 快速启停成员
 * - 滑动加入/移出
 * - **数据一致性**：应用从「已添加」被移出（增删界面操作）→ 成员自动同步移出
 *
 * 成员持久化：SharedPreferences（JSON 数组）。
 * 触发（点亮/熄灭）走下拉磁贴，P1 实现。
 */
class QuickToggleViewModel(application: Application) : AndroidViewModel(application) {

    private val context get() = getApplication<Application>()
    private val prefs = context.getSharedPreferences("snowhide_settings", Context.MODE_PRIVATE)

    /** 快速启停成员（包名列表，持久化） */
    private val _members = MutableStateFlow(loadMembers())
    val members: StateFlow<List<String>> = _members.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showPackageName = MutableStateFlow(false)
    val showPackageName: StateFlow<Boolean> = _showPackageName.asStateFlow()

    /** 刷新触发器：宫格数据变化时 +1，UI 订阅它触发重组 */
    private val _refresh = MutableStateFlow(0)
    val refresh: StateFlow<Int> = _refresh.asStateFlow()

    init {
        // 数据一致性：订阅宫格数据，成员中已移出的应用自动剔除
        viewModelScope.launch {
            GridRepository.gridItems.collect { _ ->
                syncMembers()
                _refresh.value++
            }
        }
        viewModelScope.launch {
            GridRepository.folderApps.collect { _ ->
                syncMembers()
                _refresh.value++
            }
        }
    }

    /** 剔除已不在「已添加」里的成员 */
    private fun syncMembers() {
        val added = GridRepository.allAddedPackages().toSet()
        val cleaned = _members.value.filter { it in added }
        if (cleaned.size != _members.value.size) {
            _members.value = cleaned
            persist()
        }
    }

    /** 全部已添加应用（数据源，含系统/用户过滤同增删界面？快速启停只针对已添加，不过滤） */
    fun allAddedPackages(): List<String> = GridRepository.allAddedPackages()

    /** 左栏：已添加但未加入快速启停 */
    fun notMemberPackages(): List<String> {
        val query = _searchQuery.value.trim()
        return allAddedPackages()
            .filter { it !in _members.value }
            .filter { pkg -> query.isEmpty() || pkg.contains(query, ignoreCase = true) }
            .sortedBy { it }
    }

    /** 右栏：快速启停成员 */
    fun memberPackages(): List<String> =
        _members.value.sortedBy { it }

    fun setSearchQuery(q: String) {
        _searchQuery.value = q
    }

    fun toggleShowPackageName() {
        _showPackageName.value = !_showPackageName.value
    }

    /** 加入快速启停（左栏右滑） */
    fun addMember(pkg: String) {
        if (pkg !in _members.value) {
            _members.value = _members.value + pkg
            persist()
        }
    }

    /** 移出快速启停（右栏左滑） */
    fun removeMember(pkg: String) {
        _members.value = _members.value.filterNot { it == pkg }
        persist()
    }

    /** 应用显示名（包名由 UI 追加显示在名称下方，不再二选一） */
    fun displayLabel(pkg: String): String {
        return runCatching {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(info).toString()
        }.getOrDefault(pkg)
    }

    private fun loadMembers(): List<String> {
        val json = prefs.getString(KEY_MEMBERS, "[]") ?: "[]"
        return runCatching {
            org.json.JSONArray(json).let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            }
        }.getOrDefault(emptyList())
    }

    private fun persist() {
        val arr = org.json.JSONArray()
        _members.value.forEach { arr.put(it) }
        prefs.edit().putString(KEY_MEMBERS, arr.toString()).apply()
    }

    companion object {
        private const val KEY_MEMBERS = "quick_toggle_members"
    }
}
