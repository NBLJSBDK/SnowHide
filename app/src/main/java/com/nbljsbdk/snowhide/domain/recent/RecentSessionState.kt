package com.nbljsbdk.snowhide.domain.recent

import com.nbljsbdk.snowhide.core.model.AppTarget

/** Recent 解析器提供的纯快照事实。 */
data class RecentAccessibilitySnapshot(
    val packages: Set<String>,
    val windowPackage: String,
    val windowClass: String,
)

data class AccessibilitySnapshotUpdate(
    val state: RecentSessionState,
    val accepted: Boolean,
)

data class TaskSnapshotUpdate(
    val state: RecentSessionState,
    val removed: Set<AppTarget>,
    val baselineEstablished: Boolean,
)

/**
 * Recent 会话和任务基线的纯状态。
 *
 * 不知道 AccessibilityEvent、Handler 或冻结执行；空快照保护和 old-new 差异
 * 通过纯方法计算，Controller 只负责把系统事件和执行副作用接进来。
 */
data class RecentSessionState(
    val active: Boolean = false,
    val recentPackages: Set<String> = emptySet(),
    val recentWindowPackage: String? = null,
    val recentWindowClass: String? = null,
    val lastRecentAt: Long = 0L,
    val emptySnapshotStreak: Int = 0,
    val calibrationMode: Boolean = false,
    val taskSnapshot: Set<AppTarget> = emptySet(),
    val taskSnapshotInitialized: Boolean = false,
    val generation: Long = 0L,
) {

    fun begin(
        snapshot: RecentAccessibilitySnapshot,
        now: Long,
        calibration: Boolean,
    ): RecentSessionState = copy(
        active = true,
        recentPackages = snapshot.packages,
        recentWindowPackage = snapshot.windowPackage,
        recentWindowClass = snapshot.windowClass,
        lastRecentAt = now,
        emptySnapshotStreak = if (snapshot.packages.isEmpty()) 1 else 0,
        calibrationMode = calibration,
        taskSnapshot = emptySet(),
        taskSnapshotInitialized = false,
        generation = generation + 1,
    )

    /**
     * 接受无障碍快照；连续空快照未达到确认阈值时只累计 streak，不替换原快照。
     */
    fun acceptAccessibilitySnapshot(
        snapshot: RecentAccessibilitySnapshot,
        now: Long,
        scrolled: Boolean,
        emptyConfirmationCount: Int,
    ): AccessibilitySnapshotUpdate {
        if (snapshot.packages.isEmpty() && recentPackages.isNotEmpty()) {
            val streak = emptySnapshotStreak + 1
            val trusted = (recentPackages.size == 1 && scrolled) ||
                streak >= emptyConfirmationCount
            if (!trusted) {
                return AccessibilitySnapshotUpdate(
                    state = copy(emptySnapshotStreak = streak),
                    accepted = false,
                )
            }
        }
        return AccessibilitySnapshotUpdate(
            state = copy(
                recentPackages = snapshot.packages,
                recentWindowPackage = snapshot.windowPackage,
                recentWindowClass = snapshot.windowClass,
                lastRecentAt = now,
                emptySnapshotStreak = 0,
            ),
            accepted = true,
        )
    }

    fun initializeOrDiffTaskSnapshot(
        targets: Set<AppTarget>,
        now: Long,
    ): TaskSnapshotUpdate {
        if (!taskSnapshotInitialized) {
            return TaskSnapshotUpdate(
                state = copy(
                    taskSnapshot = targets,
                    taskSnapshotInitialized = true,
                ),
                removed = emptySet(),
                baselineEstablished = true,
            )
        }
        return TaskSnapshotUpdate(
                state = copy(
                taskSnapshot = targets,
                lastRecentAt = now,
            ),
            removed = taskSnapshot - targets,
            baselineEstablished = false,
        )
    }

    fun finish(): RecentSessionState = RecentSessionState(generation = generation + 1)
}
