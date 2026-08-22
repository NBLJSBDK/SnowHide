package com.nbljsbdk.snowhide.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.nbljsbdk.snowhide.core.engine.EngineManager
import com.nbljsbdk.snowhide.core.engine.registry.EngineRegistry
import com.nbljsbdk.snowhide.core.mode.FreezeExecutor
import com.nbljsbdk.snowhide.data.prefs.SettingsRepository
import com.nbljsbdk.snowhide.data.repo.FrozenStateStore
import com.nbljsbdk.snowhide.data.repo.GridRepository
import com.nbljsbdk.snowhide.data.repo.RecentCalibrationRepository
import com.nbljsbdk.snowhide.domain.FreezeUseCase
import com.nbljsbdk.snowhide.ui.util.FeedbackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Recent 会话控制器。
 *
 * Recent 页面内只更新快照和待冻结集合，离开 Recent 后才执行冻结；
 * 手动/自动校准只负责建立识别结果，不会把校准过程当作划卡。
 */
internal class RecentSwipeController(
    private val service: AccessibilityService,
) {

    private val context: Context
        get() = service.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val freezeMutex = Mutex()

    private var pendingEvent: AccessibilityEvent? = null
    private var eventScheduled = false
    private var pendingExitToken: Any? = null
    private var manualCalibrationToken: Any? = null

    private var recentSessionActive = false
    private var recentPackages = emptySet<String>()
    private var recentWindowPackage: String? = null
    private var recentWindowClass: String? = null
    private var lastRecentAt = 0L
    private var emptySnapshotStreak = 0
    private val pendingFreezePackages = linkedSetOf<String>()

    private var calibrationMode = false
    private var manualCalibrationRequested = false
    private var manualToastPending = false

    private var candidatePackages = emptySet<String>()
    private var candidateLabels = emptyMap<String, String>()
    private var candidatesRefreshedAt = 0L
    private var launcherPackage: String? = null
    private var knownWindowPackage: String? = null
    private var knownWindowClass: String? = null
    private var taskSnapshot = emptySet<String>()
    private var taskSnapshotInitialized = false
    private var taskSnapshotRequestedAt = 0L
    private var taskSnapshotInFlight = false
    private var taskSnapshotRefreshPending = false
    private var waitingToFinishForTaskSnapshot = false
    private var sessionGeneration = 0L
    private lateinit var freezeUseCase: FreezeUseCase

    fun onServiceConnected() {
        current = this
        EngineRegistry.init(context)
        GridRepository.init(context)
        FrozenStateStore.init(context)
        SettingsRepository.init(context)
        RecentCalibrationRepository.init(context)
        knownWindowPackage = RecentCalibrationRepository.windowPackage
        knownWindowClass = RecentCalibrationRepository.windowClass
        freezeUseCase = FreezeUseCase(
            FreezeExecutor(EngineManager),
            GridRepository,
            EngineManager,
        )
        launcherPackage = service.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            0,
        )?.activityInfo?.packageName
    }

    /** 合并高频事件，滑动/窗口切换优先处理。 */
    fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType !in SUPPORTED_EVENT_TYPES) return
        if (!shouldProcessEvents()) return

        pendingEvent?.recycle()
        pendingEvent = AccessibilityEvent.obtain(event)
        val immediate = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
        if (eventScheduled) {
            if (immediate) {
                handler.removeCallbacks(processRunnable)
                handler.post(processRunnable)
            }
            return
        }
        eventScheduled = true
        handler.postDelayed(processRunnable, if (immediate) 0L else EVENT_DEBOUNCE_MS)
    }

    fun onDestroy() {
        if (current === this) current = null
        handler.removeCallbacksAndMessages(null)
        pendingEvent?.recycle()
        pendingEvent = null
        pendingFreezePackages.clear()
        scope.cancel()
    }

    /** 手动校准：重建识别结果，但不冻结校准过程中的任何卡片。 */
    fun beginCalibration() {
        if (recentSessionActive) finishRecentSession(force = true)
        handler.removeCallbacks(processRunnable)
        eventScheduled = false
        pendingEvent?.recycle()
        pendingEvent = null
        cancelSessionExit()
        RecentCalibrationRepository.clear()
        knownWindowPackage = null
        knownWindowClass = null
        manualCalibrationRequested = true
        manualToastPending = true
        FeedbackController.toast(context, "校准已开始：请手动打开 Recent")
        val token = Any()
        manualCalibrationToken = token
        handler.postDelayed({
            if (manualCalibrationToken === token) {
                manualCalibrationToken = null
                manualCalibrationRequested = false
                manualToastPending = false
                FeedbackController.toast(context, "校准失败：未识别到 Recent 应用")
            }
        }, MANUAL_CALIBRATION_TIMEOUT_MS)
    }

    private val processRunnable = Runnable {
        eventScheduled = false
        val event = pendingEvent ?: return@Runnable
        pendingEvent = null
        try {
            processEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun processEvent(event: AccessibilityEvent) {
        if (!shouldProcessEvents()) return
        val now = SystemClock.elapsedRealtime()
        refreshCandidates(now)
        val refreshTaskSnapshot = event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        if (recentSessionActive && refreshTaskSnapshot) requestTaskSnapshot(now)
        val root = runCatching { service.rootInActiveWindow }.getOrNull()
        if (root == null) {
            scheduleSessionExit()
            return
        }

        val noCalibrationData = RecentCalibrationRepository.packages.value.isEmpty()
        val snapshot = try {
            RecentTaskParser.parse(
                event = event,
                root = root,
                candidates = candidatePackages,
                labels = candidateLabels,
                launcherPackage = launcherPackage,
                knownWindowPackage = knownWindowPackage,
                knownWindowClass = knownWindowClass,
                ownPackage = service.packageName,
                wasRecent = recentSessionActive,
                previousWindowPackage = recentWindowPackage,
                previousWindowClass = recentWindowClass,
                lastRecentAt = lastRecentAt,
                now = now,
                // 自动校准仍需 Recent/桌面/SystemUI 特征；只有手动入口放宽到未知容器，
                // 避免用户普通打开应用时被误保存为 Recent 校准结果。
                manualCalibration = manualCalibrationRequested,
            )
        } finally {
            root.recycle()
        }

        if (snapshot == null) {
            scheduleSessionExit()
            return
        }
        cancelSessionExit()
        val newSession = !recentSessionActive
        if (newSession) {
            beginRecentSession(snapshot, now, noCalibrationData)
        } else {
            updateRecentSession(snapshot, event, now)
        }
        if (newSession || refreshTaskSnapshot) {
            requestTaskSnapshot(now, force = newSession)
        }
    }

    private fun beginRecentSession(
        snapshot: RecentTaskParser.Snapshot,
        now: Long,
        noCalibrationData: Boolean,
    ) {
        recentSessionActive = true
        sessionGeneration++
        recentPackages = snapshot.packages
        recentWindowPackage = snapshot.windowPackage
        recentWindowClass = snapshot.windowClass
        lastRecentAt = now
        emptySnapshotStreak = if (snapshot.packages.isEmpty()) 1 else 0
        pendingFreezePackages.clear()
        taskSnapshot = emptySet()
        taskSnapshotInitialized = false
        taskSnapshotRefreshPending = false
        waitingToFinishForTaskSnapshot = false
        handler.removeCallbacks(taskFinishTimeoutRunnable)

        calibrationMode = manualCalibrationRequested || noCalibrationData
        manualCalibrationRequested = false
    }

    private fun updateRecentSession(
        snapshot: RecentTaskParser.Snapshot,
        event: AccessibilityEvent,
        now: Long,
    ) {
        val previous = recentPackages
        val current = snapshot.packages
        if (current.isEmpty() && previous.isNotEmpty()) {
            emptySnapshotStreak++
            val trustedEmpty = (previous.size == 1 &&
                event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) ||
                emptySnapshotStreak >= EMPTY_SNAPSHOT_CONFIRMATIONS
            if (!trustedEmpty) return
        } else {
            emptySnapshotStreak = 0
        }

        // 只有 Recent 内容变化事件才产生移除差异；窗口切换事件只用于进出判断。
        val removed = if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            emptySet()
        } else {
            previous - current
        }
        if (!calibrationMode && !taskSnapshotInitialized) {
            pendingFreezePackages.removeAll(current)
            pendingFreezePackages.addAll(removed)
        } else {
            pendingFreezePackages.clear()
        }

        recentPackages = current
        recentWindowPackage = snapshot.windowPackage
        recentWindowClass = snapshot.windowClass
        lastRecentAt = now
        if (calibrationMode && !taskSnapshotInitialized && current.isNotEmpty()) {
            completeCalibration(snapshot)
        }
    }

    private fun completeCalibration(snapshot: RecentTaskParser.Snapshot) {
        RecentCalibrationRepository.record(
            snapshot.packages,
            snapshot.windowPackage,
            snapshot.windowClass,
        )
        knownWindowPackage = snapshot.windowPackage
        knownWindowClass = snapshot.windowClass
        calibrationMode = false
        if (manualToastPending) {
            manualToastPending = false
            manualCalibrationToken = null
            FeedbackController.toast(
                context,
                "校准成功：已识别 ${snapshot.packages.size} 个应用",
            )
        }
    }

    /** Recent 窗口离开后执行待冻结集合。 */
    private fun finishRecentSession(force: Boolean = false) {
        if (!recentSessionActive) return
        if (taskSnapshotInFlight && !force) {
            waitingToFinishForTaskSnapshot = true
            handler.removeCallbacks(taskFinishTimeoutRunnable)
            handler.postDelayed(taskFinishTimeoutRunnable, TASK_FINISH_TIMEOUT_MS)
            return
        }
        waitingToFinishForTaskSnapshot = false
        handler.removeCallbacks(taskFinishTimeoutRunnable)
        recentSessionActive = false
        sessionGeneration++
        pendingExitToken = null
        val targets = if (!calibrationMode && SettingsRepository.swipeDisableEnabled.value) {
            pendingFreezePackages.toList()
        } else {
            emptyList()
        }
        pendingFreezePackages.clear()
        recentPackages = emptySet()
        recentWindowPackage = null
        recentWindowClass = null
        lastRecentAt = 0L
        emptySnapshotStreak = 0
        taskSnapshot = emptySet()
        taskSnapshotInitialized = false
        calibrationMode = false
        manualCalibrationToken = null
        manualCalibrationRequested = false
        manualToastPending = false

        if (targets.isEmpty()) return
        scope.launch {
            freezeMutex.withLock {
                val result = runCatching { freezeUseCase.freezePackages(targets) }
                    .getOrElse { Result.failure(it) }
                if (result.isSuccess) {
                    runCatching { FrozenStateStore.refresh() }
                    FeedbackController.toast(context, "已停用 ${targets.size} 个划掉的应用")
                } else {
                    FeedbackController.notifyFailure(
                        context,
                        "划卡停用",
                        result.exceptionOrNull()?.message ?: "未知错误",
                    )
                }
            }
        }
    }

    /** 在后台读取任务包名，解决 ColorOS 同名卡片无法靠文字区分的问题。 */
    private fun requestTaskSnapshot(now: Long, force: Boolean = false) {
        if (candidatePackages.isEmpty()) return
        if (taskSnapshotInFlight) {
            taskSnapshotRefreshPending = true
            return
        }
        if (!force && now - taskSnapshotRequestedAt < TASK_SNAPSHOT_REFRESH_MS) return
        taskSnapshotRequestedAt = now
        taskSnapshotInFlight = true
        taskSnapshotRefreshPending = false
        val generation = sessionGeneration
        val candidates = candidatePackages
        scope.launch {
            val result = RecentTaskSnapshotProvider.query(candidates, service.packageName)
            handler.post {
                taskSnapshotInFlight = false
                result.getOrNull()?.let { packages ->
                    if (recentSessionActive && generation == sessionGeneration) {
                        applyTaskSnapshot(packages)
                    } else if (recentSessionActive) {
                        taskSnapshotRefreshPending = true
                    }
                }
                if (taskSnapshotRefreshPending) {
                    taskSnapshotRefreshPending = false
                    taskSnapshotRequestedAt = 0L
                    requestTaskSnapshot(SystemClock.elapsedRealtime())
                } else if (waitingToFinishForTaskSnapshot) {
                    finishRecentSession()
                }
            }
        }
    }

    private fun applyTaskSnapshot(packages: Set<String>) {
        val now = SystemClock.elapsedRealtime()
        if (!taskSnapshotInitialized) {
            taskSnapshot = packages
            taskSnapshotInitialized = true
            if (calibrationMode && packages.isNotEmpty()) {
                completeCalibration(
                    RecentTaskParser.Snapshot(
                        packages = packages,
                        windowPackage = recentWindowPackage.orEmpty(),
                        windowClass = recentWindowClass.orEmpty(),
                    ),
                )
            }
            return
        }

        val removed = taskSnapshot - packages
        if (!calibrationMode) {
            pendingFreezePackages.removeAll(packages)
            pendingFreezePackages.addAll(removed)
        } else {
            pendingFreezePackages.clear()
        }
        taskSnapshot = packages
        recentPackages = packages
        lastRecentAt = now
    }

    private fun scheduleSessionExit() {
        if (!recentSessionActive) return
        val token = Any()
        pendingExitToken = token
        handler.removeCallbacks(exitRunnable)
        handler.postDelayed(exitRunnable, SESSION_EXIT_DEBOUNCE_MS)
    }

    private val exitRunnable = Runnable {
        if (pendingExitToken != null) finishRecentSession()
    }

    private fun cancelSessionExit() {
        pendingExitToken = null
        handler.removeCallbacks(exitRunnable)
        if (waitingToFinishForTaskSnapshot) {
            waitingToFinishForTaskSnapshot = false
            handler.removeCallbacks(taskFinishTimeoutRunnable)
        }
    }

    private val taskFinishTimeoutRunnable = Runnable {
        if (waitingToFinishForTaskSnapshot) {
            waitingToFinishForTaskSnapshot = false
            finishRecentSession(force = true)
        }
    }

    private fun shouldProcessEvents(): Boolean =
        SettingsRepository.swipeDisableEnabled.value ||
            manualCalibrationRequested ||
            recentSessionActive

    private fun refreshCandidates(now: Long) {
        if (now - candidatesRefreshedAt < CANDIDATE_REFRESH_INTERVAL_MS) return
        candidatesRefreshedAt = now
        val packages = GridRepository.allAddedPackages().toSet()
        if (packages == candidatePackages) return
        candidatePackages = packages
        candidateLabels = packages.associateWith { pkg ->
            runCatching {
                service.packageManager.getApplicationLabel(
                    service.packageManager.getApplicationInfo(pkg, 0),
                ).toString()
            }.getOrDefault(pkg)
        }
    }

    companion object {
        private const val EVENT_DEBOUNCE_MS = 80L
        private const val SESSION_EXIT_DEBOUNCE_MS = 180L
        private const val CANDIDATE_REFRESH_INTERVAL_MS = 1_000L
        private const val TASK_SNAPSHOT_REFRESH_MS = 300L
        private const val TASK_FINISH_TIMEOUT_MS = 800L
        private const val MANUAL_CALIBRATION_TIMEOUT_MS = 30_000L
        private const val EMPTY_SNAPSHOT_CONFIRMATIONS = 2
        private val SUPPORTED_EVENT_TYPES = setOf(
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
        )

        @Volatile
        private var current: RecentSwipeController? = null

        fun requestCalibration(context: Context) {
            current?.beginCalibration() ?: runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }
}
