package com.nbljsbdk.snowhide.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nbljsbdk.snowhide.data.prefs.SettingsRepository
import com.nbljsbdk.snowhide.ui.util.AppIconLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 设置页 ViewModel（设计文档 §3.11 更多选项）
 *
 * 设置读写走 SettingsRepository 单例；
 * 图标包发现走 AppIconLoader（RESOLVE_ICON 协议）。
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val context get() = getApplication<Application>()
    val settings = SettingsRepository

    private val _iconPacks = MutableStateFlow<List<AppIconLoader.IconPackInfo>>(emptyList())
    /** 已装图标包列表（设置页选择器数据源） */
    val iconPacks: StateFlow<List<AppIconLoader.IconPackInfo>> = _iconPacks.asStateFlow()

    private val _pickerOpen = MutableStateFlow(false)
    val pickerOpen: StateFlow<Boolean> = _pickerOpen.asStateFlow()

    init {
        refreshIconPacks()
    }

    fun refreshIconPacks() {
        viewModelScope.launch {
            _iconPacks.value = AppIconLoader.queryIconPacks()
        }
    }

    fun openPicker() {
        refreshIconPacks()
        _pickerOpen.value = true
    }

    fun closePicker() {
        _pickerOpen.value = false
    }

    fun selectIconPack(pkg: String) {
        settings.setIconPack(pkg)
        _pickerOpen.value = false
    }
}
