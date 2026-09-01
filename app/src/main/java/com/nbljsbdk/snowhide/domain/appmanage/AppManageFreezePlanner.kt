package com.nbljsbdk.snowhide.domain.appmanage

import com.nbljsbdk.snowhide.core.model.AppTarget

/**
 * 增删应用页「应用」按钮的目标规划：只处理本次进入页面后新增、且当前未冻结的应用。
 */
object AppManageFreezePlanner {

    fun newlyAddedUnfrozenPackages(
        initialPackages: Set<String>,
        currentPackages: Collection<String>,
        frozenStates: Map<String, Boolean>,
    ): List<String> = currentPackages
        .asSequence()
        .filter { it !in initialPackages }
        .filter { frozenStates[it] != true }
        .distinct()
        .toList()

    fun newlyAddedUnfrozenTargets(
        initialTargets: Set<AppTarget>,
        currentTargets: Collection<AppTarget>,
        frozenStates: Map<AppTarget, Boolean>,
    ): List<AppTarget> = currentTargets
        .asSequence()
        .filter { it !in initialTargets }
        .filter { frozenStates[it] != true }
        .distinct()
        .toList()
}
