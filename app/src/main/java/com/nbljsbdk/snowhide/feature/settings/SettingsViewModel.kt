package com.nbljsbdk.snowhide.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.nbljsbdk.snowhide.data.prefs.SettingsRepository

/**
 * 设置页 ViewModel（设计文档 §3.11 更多选项）
 *
 * 设置读写走 SettingsRepository 单例。
 * 美化设置（图标包/背景透明）已移到主屏空白长按 → 美化浮框（BeautyPanel）。
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    val settings = SettingsRepository
}
