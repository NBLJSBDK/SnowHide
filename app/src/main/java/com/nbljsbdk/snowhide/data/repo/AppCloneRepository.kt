package com.nbljsbdk.snowhide.data.repo

import android.content.Context
import android.content.SharedPreferences
import com.nbljsbdk.snowhide.core.model.AppCloneSelectionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 增删应用中分身模式的持久化状态；不保存或复制任何应用数据。 */
object AppCloneRepository : AppCloneSelectionStore {

    private lateinit var prefs: SharedPreferences

    private val _selectedUserId = MutableStateFlow<Int?>(null)
    override val selectedUserId: StateFlow<Int?> = _selectedUserId.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _selectedUserId.value = prefs.getInt(KEY_SELECTED_USER, -1).takeIf { it >= 0 }
    }

    override fun setSelectedUserId(userId: Int) {
        require(userId >= 0) { "非法用户 ID：$userId" }
        _selectedUserId.value = userId
        if (::prefs.isInitialized) {
            prefs.edit().putInt(KEY_SELECTED_USER, userId).apply()
        }
    }

    private const val PREFS_NAME = "snowhide_app_clone"
    private const val KEY_SELECTED_USER = "selected_user_id"
}
