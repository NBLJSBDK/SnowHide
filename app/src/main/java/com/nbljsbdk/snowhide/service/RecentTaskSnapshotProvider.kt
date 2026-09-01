package com.nbljsbdk.snowhide.service

import com.nbljsbdk.snowhide.core.engine.EngineManager
import com.nbljsbdk.snowhide.core.model.AppTarget

/**
 * 通过当前权限引擎读取系统 Recent 任务身份。
 *
 * ColorOS 的无障碍卡片节点统一属于桌面进程，且同名应用只暴露一个
 * content-desc；shell 身份的 `dumpsys activity recents` 同时提供
 * baseActivity/realActivity 和 userId，必须保留完整目标身份。
 */
internal object RecentTaskSnapshotProvider {

    private val taskStartPattern = Regex(
        "^\\s*\\* (?:Recent #\\d+: Task\\{|RecentTaskInfo #\\d+:)",
    )
    private val userPattern = Regex("\\buserId=(-?\\d+)")
    private val activityPatterns = listOf(
        Regex("mActivityComponent=([^/\\s]+)/"),
        Regex("(?:baseActivity|topActivity|realActivity)=\\{([^/}\\s]+)/"),
    )

    suspend fun query(
        candidates: Set<AppTarget>,
        ownPackage: String,
    ): Result<Set<AppTarget>> {
        if (candidates.isEmpty()) return Result.success(emptySet())
        val engine = EngineManager.primaryEngine.value
            ?: return Result.failure(IllegalStateException("没有可用的权限引擎"))
        return engine.exec("dumpsys activity recents").map { output ->
            parseOutput(output, candidates, ownPackage)
        }
    }

    /** 解析 shell 输出；缺少 userId 时只允许无歧义的 user 0 目标通过。 */
    internal fun parseOutput(
        output: String,
        candidates: Set<AppTarget>,
        ownPackage: String,
    ): Set<AppTarget> {
        val records = parseRecords(output)
        val result = linkedSetOf<AppTarget>()
        records.forEach { record ->
            record.packages.forEach { pkg ->
                if (pkg == ownPackage) return@forEach
                val matching = candidates.filter { it.packageName.value == pkg }
                val target = if (record.userId != null) {
                    matching.singleOrNull { it.userId == record.userId }
                } else {
                    matching.singleOrNull { it.isPrimaryUser }
                        ?.takeIf { matching.size == 1 }
                }
                target?.let(result::add)
            }
        }
        return result
    }

    private data class TaskRecord(
        val userId: Int?,
        val packages: Set<String>,
    )

    private class MutableTaskRecord {
        var userId: Int? = null
        val packages = linkedSetOf<String>()
    }

    private fun parseRecords(output: String): List<TaskRecord> {
        val records = mutableListOf<TaskRecord>()
        var current: MutableTaskRecord? = null

        fun flush() {
            val record = current ?: return
            if (record.packages.isNotEmpty()) {
                records += TaskRecord(record.userId, record.packages.toSet())
            }
            current = null
        }

        output.lineSequence().forEach { line ->
            if (line.trimStart().startsWith("Visible recent tasks")) {
                flush()
                return@forEach
            }
            if (taskStartPattern.containsMatchIn(line)) {
                flush()
                current = MutableTaskRecord()
                return@forEach
            }
            val record = current ?: return@forEach
            userPattern.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { userId ->
                if (userId >= 0) record.userId = userId
            }
            activityPatterns.forEach { pattern ->
                pattern.find(line)?.groupValues?.getOrNull(1)?.let(record.packages::add)
            }
        }
        flush()
        return records
    }
}
