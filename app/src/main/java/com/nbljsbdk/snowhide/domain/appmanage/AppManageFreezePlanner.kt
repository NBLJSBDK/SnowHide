package com.nbljsbdk.snowhide.domain.appmanage

import com.nbljsbdk.snowhide.core.model.AppTarget
import com.nbljsbdk.snowhide.data.model.AppRuntimeState

/**
 * 增删应用页「应用」按钮的目标规划：只处理本次进入页面后新增、且状态明确为 ACTIVE 的应用。
 */
object AppManageFreezePlanner {

    fun newlyAddedUnfrozenPackages(
        initialPackages: Set<String>,
        currentPackages: Collection<String>,
        runtimeStates: Map<String, AppRuntimeState>,
    ): List<String> = currentPackages
        .asSequence()
        .filter { it !in initialPackages }
        .filter { runtimeStates[it] == AppRuntimeState.ACTIVE }
        .distinct()
        .toList()

    fun newlyAddedUnfrozenTargets(
        initialTargets: Set<AppTarget>,
        currentTargets: Collection<AppTarget>,
        runtimeStates: Map<AppTarget, AppRuntimeState>,
    ): List<AppTarget> = currentTargets
        .asSequence()
        .filter { it !in initialTargets }
        .filter { runtimeStates[it] == AppRuntimeState.ACTIVE }
        .distinct()
        .toList()
}
