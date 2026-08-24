package com.nbljsbdk.snowhide.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.nbljsbdk.snowhide.app.CompositionRoot
import com.nbljsbdk.snowhide.data.prefs.SettingsRepository
import com.nbljsbdk.snowhide.data.repo.FrozenStateStore
import com.nbljsbdk.snowhide.data.repo.GridRepository
import com.nbljsbdk.snowhide.data.repo.RecentCalibrationRepository
import com.nbljsbdk.snowhide.data.repo.RecentFreezeQueueRepository
import com.nbljsbdk.snowhide.domain.FreezeUseCase
import com.nbljsbdk.snowhide.domain.recent.RecentAccessibilitySnapshot
import com.nbljsbdk.snowhide.domain.recent.RecentFreezePolicy
import com.nbljsbdk.snowhide.domain.recent.RecentSessionState
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
 * Recent 页面内更新快照，确认卡片消失后立即执行冻结；
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

    private var sessionState = RecentSessionState()
    private val recentSessionActive: Boolean get() = sessionState.active
    private val recentPackages: Set<String> get() = sessionState.recentPackages
    private val recentWindowPackage: String? get() = sessionState.recentWindowPackage
    private val recentWindowClass: String? get() = sessionState.recentWindowClass
    private val lastRecentAt: Long get() = sessionState.lastRecentAt
    private val calibrationMode: Boolean get() = sessionState.calibrationMode
    private var manualCalibrationRequested = false
    private var manualToastPending = false

    private var candidatePackages = emptySet<String>()
    private var candidateLabels = emptyMap<String, String>()
    private var candidatesRefreshedAt = 0L
    private var launcherPackage: String? = null
    private var knownWindowPackage: String? = null
    private var knownWindowClass: String? = null
    private val taskSnapshotInitialized: Boolean get() = sessionState.taskSnapshotInitialized
    private var taskSnapshotRequestedAt = 0L
    private var taskSnapshotInFlight = false
    private var taskSnapshotRefreshPending = false
    private var waitingToFinishForTaskSnapshot = false
    private val sessionGeneration: Long get() = sessionState.generation
    private var queueDrainInFlight = false
    private var queueDrainAttemptedAt = 0L
    private lateinit var freezeUseCase: FreezeUseCase

    private data class RecentFreezeOutcome(
        val handled: List<String>,
        val successful: List<String>,
        val failures: List<String>,
    )

    fun onServiceConnected() {
        current = this
        CompositionRoot.init(context)
        knownWindowPackage = RecentCalibrationRepository.windowPackage
        knownWindowClass = RecentCalibrationRepository.windowClass
        freezeUseCase = CompositionRoot.appContainer(context).freezeUseCase
        launcherPackage = service.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            0,
        )?.activityInfo?.packageName
        handler.postDelayed(::drainQueuedFreezes, QUEUE_INITIAL_DELAY_MS)
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
        drainQueuedFreezes()
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
        sessionState = sessionState.begin(
            snapshot = RecentAccessibilitySnapshot(
                packages = snapshot.packages,
                windowPackage = snapshot.windowPackage,
                windowClass = snapshot.windowClass,
            ),
            now = now,
            calibration = manualCalibrationRequested || noCalibrationData,
        )
        // 进入 Recent 的首轮无障碍事件仍在布局稳定过程中，必须等 Shizuku
        // 返回第一份任务列表后再建立基线，不能把首轮差异当成用户划卡。
        taskSnapshotRequestedAt = 0L
        taskSnapshotRefreshPending = false
        waitingToFinishForTaskSnapshot = false
        handler.removeCallbacks(taskFinishTimeoutRunnable)
        handler.removeCallbacks(sessionWatchdogRunnable)
        handler.postDelayed(sessionWatchdogRunnable, SESSION_WATCHDOG_MS)

        manualCalibrationRequested = false
    }

    private fun updateRecentSession(
        snapshot: RecentTaskParser.Snapshot,
        event: AccessibilityEvent,
        now: Long,
    ) {
        val current = snapshot.packages
        val update = sessionState.acceptAccessibilitySnapshot(
            snapshot = RecentAccessibilitySnapshot(
                packages = current,
                windowPackage = snapshot.windowPackage,
                windowClass = snapshot.windowClass,
            ),
            now = now,
            scrolled = event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED,
            emptyConfirmationCount = EMPTY_SNAPSHOT_CONFIRMATIONS,
        )
        sessionState = update.state
        if (!update.accepted) return
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
        sessionState = sessionState.copy(calibrationMode = false)
        if (manualToastPending) {
            manualToastPending = false
            manualCalibrationToken = null
            FeedbackController.toast(
                context,
                "校准成功：已识别 ${snapshot.packages.size} 个应用",
            )
        }
    }

    /** Recent 窗口离开后收尾会话；停用动作已在划卡确认时执行。 */
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
        handler.removeCallbacks(sessionWatchdogRunnable)
        sessionState = sessionState.finish()
        pendingExitToken = null
        manualCalibrationToken = null
        manualCalibrationRequested = false
        manualToastPending = false
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
        val update = sessionState.initializeOrDiffTaskSnapshot(packages, now)
        sessionState = update.state
        if (update.baselineEstablished) {
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

        if (!calibrationMode) {
            freezePackagesImmediately(update.removed)
        } else {
            // 校准期间只更新基线，不执行任何停用。
        }
    }

    /** 划卡确认后立即入队，避免依赖离开 Recent 的时序。 */
    private fun freezePackagesImmediately(packages: Collection<String>) {
        val targets = RecentFreezePolicy.eligiblePackages(
            packages = packages,
            addedPackages = GridRepository.allAddedPackages().toSet(),
            lockedPackages = GridRepository.lockedPackages.value,
            ownPackage = service.packageName,
        )
        if (targets.isEmpty()) return
        scope.launch {
            RecentFreezeQueueRepository.enqueue(targets)
            handler.post(::drainQueuedFreezes)
        }
    }

    /** 执行持久化队列；队列保留到命令成功，服务重连后可继续补执行。 */
    private fun drainQueuedFreezes() {
        if (!::freezeUseCase.isInitialized || queueDrainInFlight) return
        if (!SettingsRepository.swipeDisableEnabled.value) {
            RecentFreezeQueueRepository.clear()
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - queueDrainAttemptedAt < QUEUE_RETRY_INTERVAL_MS) return
        val queued = RecentFreezeQueueRepository.peek()
        if (queued.isEmpty()) return

        queueDrainAttemptedAt = now
        queueDrainInFlight = true
        scope.launch {
            val outcome = freezeMutex.withLock { executeRecentFreezes(queued) }
            if (outcome.successful.isNotEmpty()) {
                runCatching { FrozenStateStore.refresh() }
            }
            handler.post {
                queueDrainInFlight = false
                outcome.successful.forEach { pkg ->
                    FeedbackController.toast(context, "已停用：${appLabel(pkg)}")
                }
                if (outcome.failures.isNotEmpty()) {
                    FeedbackController.notifyFailure(
                        context,
                        "划卡停用",
                        outcome.failures.take(5).joinToString("；"),
                    )
                }
                if (RecentFreezeQueueRepository.peek().isNotEmpty()) {
                    queueDrainAttemptedAt = 0L
                    handler.post(::drainQueuedFreezes)
                }
            }
        }
    }

    /** Recent 专用逐包执行，不进入全局 BatchProgress。 */
    private suspend fun executeRecentFreezes(packages: List<String>): RecentFreezeOutcome {
        val handled = mutableListOf<String>()
        val successful = mutableListOf<String>()
        val failures = mutableListOf<String>()
        val eligible = RecentFreezePolicy.eligiblePackages(
            packages = packages,
            addedPackages = GridRepository.allAddedPackages().toSet(),
            lockedPackages = GridRepository.lockedPackages.value,
            ownPackage = service.packageName,
        ).toSet()
        packages.forEach { pkg ->
            if (pkg !in eligible || !GridRepository.isAppAdded(pkg) || GridRepository.isLocked(pkg)) {
                handled += pkg
                return@forEach
            }
            val result = runCatching { freezeUseCase.freezeApp(pkg) }
            if (result.isSuccess) {
                handled += pkg
                successful += pkg
            } else {
                failures += "$pkg: ${result.exceptionOrNull()?.message ?: "未知错误"}"
            }
        }
        if (handled.isNotEmpty()) RecentFreezeQueueRepository.remove(handled)
        return RecentFreezeOutcome(handled, successful, failures)
    }

    private fun appLabel(pkg: String): String = runCatching {
        val info = service.packageManager.getApplicationInfo(
            pkg,
            android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS,
        )
        service.packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(pkg)

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

    /** 兜底检测回桌面或直接进入其他应用时没有发出可靠退出事件。 */
    private val sessionWatchdogRunnable = object : Runnable {
        override fun run() {
            if (!recentSessionActive) return
            val root = runCatching { service.rootInActiveWindow }.getOrNull()
            val isRecent = root?.let {
                RecentTaskParser.isRecentWindow(
                    root = it,
                    launcherPackage = launcherPackage,
                    knownWindowPackage = knownWindowPackage,
                    knownWindowClass = knownWindowClass,
                )
            } == true
            root?.recycle()
            if (isRecent) {
                cancelSessionExit()
            } else if (!waitingToFinishForTaskSnapshot) {
                scheduleSessionExit()
            }
            handler.postDelayed(this, SESSION_WATCHDOG_MS)
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
        val packages = GridRepository.allAddedPackages()
            .filterNot { it == service.packageName }
            .toSet()
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
        private const val SESSION_WATCHDOG_MS = 250L
        private const val QUEUE_INITIAL_DELAY_MS = 500L
        private const val QUEUE_RETRY_INTERVAL_MS = 2_000L
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
