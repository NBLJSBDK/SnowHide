package com.nbljsbdk.snowhide.domain

import android.content.SharedPreferences
import com.nbljsbdk.snowhide.core.engine.EngineManager
import com.nbljsbdk.snowhide.core.mode.FreezeExecutor
import com.nbljsbdk.snowhide.core.mode.FreezeMode
import com.nbljsbdk.snowhide.data.repo.FrozenStateStore
import com.nbljsbdk.snowhide.data.repo.GridRepository

/**
 * 快速启停触发用例（下拉磁贴逻辑，§3.9 用户拍板简化版）
 *
 * - **点亮**：解冻「成员 ∩ 已添加 ∩ 当前冻结」的应用，
 *   把本批解冻的记为 opened（持久化，进程重启不丢）
 * - **熄灭**：把 opened 批冻回去；**有锁定的应用不关闭**（跳过），
 *   返回跳过清单交给上层 toast 提示
 * - 成员持久化键与 QuickToggleViewModel 共用（snowhide_settings）
 */
class QuickToggleUseCase(
    private val executor: FreezeExecutor,
    private val gridRepository: GridRepository,
    private val engineManager: EngineManager,
    private val prefs: SharedPreferences,
) {

    /** 熄灭结果：冻结数量 + 因锁定跳过的包 + 失败信息 */
    data class TurnOffResult(
        val frozen: Int,
        val lockedSkipped: List<String>,
        val failures: List<String>,
    )

    /** 点亮：解冻成员中「已添加且被冻结」的应用，并记录本批 opened */
    suspend fun lightUp(): Result<Int> {
        val engine = engineManager.primaryEngine.value
            ?: return Result.failure(IllegalStateException("没有可用的权限引擎"))
        val added = gridRepository.allAddedPackages().toSet()
        val frozen = engine.listFrozenPackages().getOrElse { return Result.failure(it) }.toSet()
        val targets = loadList(KEY_MEMBERS).filter { it in added && it in frozen }

        var success = 0
        val failedPkgs = mutableListOf<String>()
        targets.forEach { pkg ->
            executor.unfreeze(FreezeMode.FREEZE, pkg)
                .onSuccess { success++ }
                .onFailure { failedPkgs.add(pkg) }
        }
        // 只记录解冻成功的（熄灭时冻回这批）
        saveList(KEY_OPENED, targets.filterNot { it in failedPkgs })
        // 同步共享冻结状态（主屏霜化/dock 立即更新）
        FrozenStateStore.refresh()
        return if (failedPkgs.isEmpty()) Result.success(success)
        else Result.failure(IllegalStateException("部分失败：${failedPkgs.joinToString("；")}"))
    }

    /** 熄灭：冻回本批打开的应用；有锁的跳过 */
    suspend fun turnOff(): Result<TurnOffResult> {
        val opened = loadList(KEY_OPENED)
        var frozen = 0
        val lockedSkipped = mutableListOf<String>()
        val failures = mutableListOf<String>()
        opened.forEach { pkg ->
            if (gridRepository.isLocked(pkg)) {
                lockedSkipped.add(pkg)
            } else {
                executor.freeze(FreezeMode.FREEZE, pkg)
                    .onSuccess { frozen++ }
                    .onFailure { failures.add("$pkg: ${it.message}") }
            }
        }
        saveList(KEY_OPENED, emptyList())
        // 同步共享冻结状态（主屏霜化/dock 立即更新）
        FrozenStateStore.refresh()
        return Result.success(TurnOffResult(frozen, lockedSkipped, failures))
    }

    /**
     * 反转（App Shortcut「快速启停」用）：
     * opened 非空（点亮中）→ 熄灭冻回；空 → 点亮解冻。
     * 磁贴 UI 由 TileService.onStartListening 按 opened 恢复，自动同步。
     */
    suspend fun toggle(): Result<Int> {
        return if (loadList(KEY_OPENED).isNotEmpty()) {
            turnOff().map { it.frozen }
        } else {
            lightUp()
        }
    }

    private fun loadList(key: String): List<String> {
        val json = prefs.getString(key, "[]") ?: "[]"
        return runCatching {
            org.json.JSONArray(json).let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveList(key: String, list: List<String>) {
        val arr = org.json.JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    companion object {
        private const val KEY_MEMBERS = "quick_toggle_members"
        private const val KEY_OPENED = "quick_toggle_opened"
    }
}
