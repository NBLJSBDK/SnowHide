package com.nbljsbdk.snowhide.data.repo

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 已安装应用列表仓库（全局单例，启动时预加载）
 *
 * 增删应用界面的数据源。预加载解决「App 启动后第一次打开增删应用
 * 界面空白」的问题——列表在 MainActivity 启动时就异步加载，
 * 界面打开时数据已在或即将就绪（combine 派生流自动刷新）。
 */
object AppListRepository {

    data class AppInfo(
        val pkg: String,
        val label: String,
        val isSystem: Boolean,
        val installTime: Long,
    )

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    /** 全部已安装应用（含系统），按安装时间倒序 */
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    /** 是否已加载完成（UI 加载态提示用） */
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private var loading = false

    /** 进程级加载作用域（单例无 viewModelScope，自持） */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 初始化并预加载（MainActivity 启动时调用；重复调用安全） */
    fun init(context: Context) {
        if (_loaded.value || loading) return
        loading = true
        scope.launch {
            val apps = loadApps(context)
            _installedApps.value = apps
            _loaded.value = true
            loading = false
        }
    }

    /** 主动刷新（应用安装/卸载后调用） */
    suspend fun refresh(context: Context) {
        _installedApps.value = loadApps(context)
        _loaded.value = true
    }

    private suspend fun loadApps(context: Context): List<AppInfo> =
        withContext(Dispatchers.IO) {
            val pm = context.applicationContext.packageManager
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.packageName != context.packageName } // 排除自己
                .map { info ->
                    val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    AppInfo(
                        pkg = info.packageName,
                        label = pm.getApplicationLabel(info).toString(),
                        isSystem = isSystem,
                        installTime = runCatching {
                            pm.getPackageInfo(info.packageName, 0).firstInstallTime
                        }.getOrDefault(0L),
                    )
                }
                .sortedByDescending { it.installTime } // 新装最前
        }

    /** 应用显示名（单查） */
    fun labelOf(context: Context, pkg: String): String = runCatching {
        val pm = context.packageManager
        val info = pm.getApplicationInfo(pkg, 0)
        pm.getApplicationLabel(info).toString()
    }.getOrDefault(pkg)
}
