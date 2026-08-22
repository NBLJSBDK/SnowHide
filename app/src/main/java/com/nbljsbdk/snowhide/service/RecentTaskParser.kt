package com.nbljsbdk.snowhide.service

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/** Recent 窗口和应用卡片解析器。只返回候选应用包名，不执行任何业务。 */
internal object RecentTaskParser {

    data class Snapshot(
        val packages: Set<String>,
        val windowPackage: String,
        val windowClass: String,
    )

    private val recentHints = listOf(
        "最近任务",
        "最近使用",
        "多任务",
        "概览",
        "recent apps",
        "recents",
        "overview",
    )

    fun parse(
        event: AccessibilityEvent,
        root: AccessibilityNodeInfo,
        candidates: Set<String>,
        labels: Map<String, String>,
        launcherPackage: String?,
        knownWindowPackage: String?,
        knownWindowClass: String?,
        ownPackage: String,
        wasRecent: Boolean,
        previousWindowPackage: String?,
        previousWindowClass: String?,
        lastRecentAt: Long,
        now: Long,
        manualCalibration: Boolean,
    ): Snapshot? {
        if (candidates.isEmpty()) return null

        val uniqueLabels = labels.entries
            .groupBy { normalize(it.value) }
            .filterKeys { it.length >= 2 }
            .filterValues { it.size == 1 }
            .mapValues { it.value.first().key }
        val uniquePackageAliases = candidates
            .flatMap { pkg -> packageAliases(pkg).map { alias -> alias to pkg } }
            .groupBy({ it.first }, { it.second })
            .filterValues { it.distinct().size == 1 }
            .mapValues { it.value.first() }
        val packages = linkedSetOf<String>()
        val values = ArrayList<String>(64)
        val nodes = ArrayDeque<AccessibilityNodeInfo>()
        nodes.add(root)
        var visited = 0
        var hasRecentResource = false

        fun addValue(value: CharSequence?) {
            val text = value?.toString()?.trim().orEmpty()
            if (text.isNotEmpty() && values.size < MAX_VALUES) values += text
        }

        while (nodes.isNotEmpty() && visited < MAX_NODES) {
            val node = nodes.removeFirst()
            visited++
            try {
                val nodePackage = node.packageName?.toString()
                if (nodePackage in candidates) packages += nodePackage!!
                addValue(node.text)
                addValue(node.contentDescription)
                node.viewIdResourceName?.let { id ->
                    if (id.contains("recent_container") ||
                        id.contains("overview_panel") ||
                        id.contains("overview_actions_view")
                    ) {
                        hasRecentResource = true
                    }
                    candidates.firstOrNull { id.contains(it) }?.let(packages::add)
                }
                for (index in 0 until node.childCount) {
                    node.getChild(index)?.let(nodes::addLast)
                }
            } finally {
                if (node !== root) runCatching { node.recycle() }
            }
        }
        while (nodes.isNotEmpty()) {
            runCatching { nodes.removeFirst().recycle() }
        }

        values.forEach { value ->
            val normalized = normalize(value)
            uniquePackageAliases[normalized]?.let(packages::add)
            uniqueLabels[normalized]?.let(packages::add)
            if (normalized.length >= 2) {
                val fuzzy = uniqueLabels.entries.filter { (label, _) ->
                    normalized.contains(label) || label.contains(normalized)
                }
                if (fuzzy.size == 1) packages += fuzzy.first().value
            }
        }

        val rootPackage = root.packageName?.toString().orEmpty()
        val rootClass = root.className?.toString().orEmpty()
        val eventClass = event.className?.toString().orEmpty()
        val classText = "$eventClass $rootClass"
        val lowerClass = classText.lowercase(Locale.ROOT)
        val eventText = event.text.joinToString(" ")
        val hintText = (values + eventText + classText)
            .joinToString(" ")
            .lowercase(Locale.ROOT)
        val hasRecentHint = recentHints.any(hintText::contains)
        val hasRecentClass = lowerClass.contains("recent") ||
            lowerClass.contains("overview")
        val isSystemUiRecent = rootPackage == "com.android.systemui" &&
            (hasRecentClass || hasRecentHint)
        val isLauncherRecent = rootPackage == launcherPackage &&
            (hasRecentClass || hasRecentResource)
        val knownClassMatches = knownWindowClass.isNullOrBlank() ||
            rootClass == knownWindowClass ||
            lowerClass.contains("recent") ||
            lowerClass.contains("overview")
        val isKnownRecent = rootPackage == knownWindowPackage && knownClassMatches &&
            (rootPackage != launcherPackage || hasRecentClass || hasRecentResource)
        val isContinuingRecent = wasRecent &&
            rootPackage == previousWindowPackage &&
            (previousWindowClass.isNullOrBlank() || rootClass == previousWindowClass) &&
            now - lastRecentAt <= RECENT_CONTINUATION_MS &&
            (rootPackage != launcherPackage || hasRecentClass || hasRecentResource)
        val isManualCalibrationWindow = manualCalibration &&
            rootPackage != ownPackage && rootPackage !in candidates
        val isContainerHint = hasRecentHint &&
            rootPackage == "com.android.systemui"
        val isRecentWindow = isKnownRecent ||
            isSystemUiRecent ||
            isLauncherRecent ||
            isContainerHint ||
            isContinuingRecent ||
            isManualCalibrationWindow

        if (!isRecentWindow) return null
        if (packages.isEmpty() && !isContinuingRecent && !isKnownRecent &&
            !isSystemUiRecent && !isLauncherRecent && !isContainerHint &&
            !isManualCalibrationWindow
        ) return null

        return Snapshot(packages, rootPackage, rootClass)
    }

    /** 无障碍事件缺失时，用当前窗口根节点判断 Recent 是否仍在前台。 */
    fun isRecentWindow(
        root: AccessibilityNodeInfo,
        launcherPackage: String?,
        knownWindowPackage: String?,
        knownWindowClass: String?,
    ): Boolean {
        val rootPackage = root.packageName?.toString().orEmpty()
        val rootClass = root.className?.toString().orEmpty()
        val lowerClass = rootClass.lowercase(Locale.ROOT)
        val knownClassMatches = knownWindowClass.isNullOrBlank() ||
            rootClass == knownWindowClass ||
            lowerClass.contains("recent") ||
            lowerClass.contains("overview")

        val nodes = ArrayDeque<AccessibilityNodeInfo>()
        nodes.add(root)
        var visited = 0
        var hasRecentHint = false
        var hasRecentResource = false
        while (nodes.isNotEmpty() && visited < WATCHDOG_MAX_NODES) {
            val node = nodes.removeFirst()
            visited++
            try {
                val text = listOf(node.text, node.contentDescription)
                    .joinToString(" ")
                    .lowercase(Locale.ROOT)
                if (recentHints.any(text::contains)) hasRecentHint = true
                node.viewIdResourceName?.let { id ->
                    if (id.contains("recent_container") ||
                        id.contains("overview_panel") ||
                        id.contains("overview_actions_view")
                    ) {
                        hasRecentResource = true
                    }
                }
                for (index in 0 until node.childCount) {
                    node.getChild(index)?.let(nodes::addLast)
                }
            } finally {
                if (node !== root) runCatching { node.recycle() }
            }
        }
        while (nodes.isNotEmpty()) {
            runCatching { nodes.removeFirst().recycle() }
        }

        val isKnownRecent = rootPackage == knownWindowPackage && knownClassMatches &&
            (rootPackage != launcherPackage || lowerClass.contains("recent") ||
                lowerClass.contains("overview") || hasRecentResource)
        val isSystemUiRecent = rootPackage == "com.android.systemui" &&
            (lowerClass.contains("recent") || lowerClass.contains("overview") || hasRecentHint)
        val isLauncherRecent = rootPackage == launcherPackage &&
            (lowerClass.contains("recent") || lowerClass.contains("overview") || hasRecentResource)
        return isKnownRecent || isSystemUiRecent || isLauncherRecent
    }

    private fun normalize(value: String): String =
        value.trim().replace(WHITESPACE, "").lowercase(Locale.ROOT)

    /** Recent 卡片常显示品牌英文名，系统应用标签却可能是中文，使用包名稳定词补齐映射。 */
    private fun packageAliases(pkg: String): List<String> = pkg
        .split('.', '_', '-')
        .asSequence()
        .map { it.lowercase(Locale.ROOT) }
        .filter { it.length >= 3 && it !in GENERIC_PACKAGE_PARTS }
        .distinct()
        .toList()

    private val WHITESPACE = Regex("\\s+")
    private val GENERIC_PACKAGE_PARTS = setOf(
        "android",
        "app",
        "client",
        "com",
        "in",
        "main",
        "mobile",
        "net",
        "org",
        "service",
    )
    private const val MAX_NODES = 300
    private const val MAX_VALUES = 600
    private const val WATCHDOG_MAX_NODES = 120
    private const val RECENT_CONTINUATION_MS = 1_500L
}
