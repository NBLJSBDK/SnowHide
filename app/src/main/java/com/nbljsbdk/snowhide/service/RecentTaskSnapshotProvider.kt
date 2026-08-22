package com.nbljsbdk.snowhide.service

import com.nbljsbdk.snowhide.core.engine.EngineManager

/**
 * 通过当前权限引擎读取系统 Recent 任务身份。
 *
 * ColorOS 的无障碍卡片节点统一属于桌面进程，且同名应用只暴露一个
 * content-desc；任务列表中的 baseActivity/realActivity 才能区分真实包名。
 */
internal object RecentTaskSnapshotProvider {

    private val activityPattern = Regex(
        "(?:baseActivity|realActivity)=\\{([^/}\\s]+)/",
    )
    private val componentPattern = Regex(
        "mActivityComponent=([^/\\s]+)/",
    )

    suspend fun query(
        candidates: Set<String>,
        ownPackage: String,
    ): Result<Set<String>> {
        if (candidates.isEmpty()) return Result.success(emptySet())
        val engine = EngineManager.primaryEngine.value
            ?: return Result.failure(IllegalStateException("没有可用的权限引擎"))
        return engine.exec("dumpsys activity recents").map { output ->
            (activityPattern.findAll(output).map { it.groupValues[1] } +
                componentPattern.findAll(output).map { it.groupValues[1] })
                .filter { it != ownPackage && it in candidates }
                .toSet()
        }
    }
}
