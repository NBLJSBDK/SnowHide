package com.nbljsbdk.snowhide.feature.appmanage

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nbljsbdk.snowhide.data.repo.GridRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 增加/移除应用界面 ViewModel（设计文档 §3.8 左右分栏）
 *
 * - 左栏（未添加应用）：安装时间倒序（新装最前）
 * - 右栏（已添加应用）：名称正序
 * - 滑动移动：左栏右滑=加入，右栏左滑=解冻并移出
 * - 搜索框 + 显示隐藏包名；系统应用开关 P0 隐藏（版本号 7 次彩蛋 P1）
 */
class AppManageViewModel(application: Application) : AndroidViewModel(application) {

    private val context get() = getApplication<Application>()

    data class AppInfo(val pkg: String, val label: String)

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    /** 全部已安装应用（含系统），按安装时间倒序 */
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showPackageName = MutableStateFlow(false)
    /** 显示隐藏包名（按钮切换，替代应用名显示包名） */
    val showPackageName: StateFlow<Boolean> = _showPackageName.asStateFlow()

    init {
        refreshInstalledApps()
    }

    /** 加载全部已装应用（含系统；系统应用开关 P0 恒显示） */
    fun refreshInstalledApps() {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    .filter { it.packageName != context.packageName } // 排除自己
                    .sortedByDescending { info ->
                        runCatching {
                            pm.getPackageInfo(info.packageName, 0).firstInstallTime
                        }.getOrDefault(0L)
                    }
                    .map { info ->
                        AppInfo(
                            pkg = info.packageName,
                            label = pm.getApplicationLabel(info).toString(),
                        )
                    }
            }
            _installedApps.value = apps
        }
    }

    fun setSearchQuery(q: String) {
        _searchQuery.value = q
    }

    fun toggleShowPackageName() {
        _showPackageName.value = !_showPackageName.value
    }

    /** 应用是否已添加（右栏数据源） */
    fun isAdded(pkg: String): Boolean = GridRepository.isAppAdded(pkg)

    /** 右滑加入（左栏 → 已添加） */
    fun addApp(pkg: String) {
        GridRepository.addAppToHome(pkg)
    }

    /** 左滑移出（已添加 → 解冻并移出） */
    fun removeApp(pkg: String) {
        GridRepository.removeApp(pkg)
    }

    /** 显示名（是否显示包名开关） */
    fun displayLabel(app: AppInfo): String =
        if (_showPackageName.value) app.pkg else app.label
}
