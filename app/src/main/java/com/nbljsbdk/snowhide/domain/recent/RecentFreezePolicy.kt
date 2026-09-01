package com.nbljsbdk.snowhide.domain.recent

import com.nbljsbdk.snowhide.core.model.PackageName
import com.nbljsbdk.snowhide.core.model.AppTarget

/** Recent 划卡停用候选过滤：只保留已添加、未锁定且排除自身的合法包名。 */
object RecentFreezePolicy {

    fun eligiblePackages(
        packages: Collection<String>,
        addedPackages: Set<String>,
        lockedPackages: Set<String>,
        ownPackage: String,
    ): List<String> = packages.asSequence()
        .filter { PackageName.isValid(it) }
        .filter { it != ownPackage }
        .filter { it in addedPackages }
        .filter { it !in lockedPackages }
        .distinct()
        .toList()

    /** Recent 任务已带明确用户空间时，按完整目标身份过滤。 */
    fun eligibleTargets(
        targets: Collection<AppTarget>,
        addedTargets: Set<AppTarget>,
        lockedTargets: Set<AppTarget>,
        ownPackage: String,
    ): List<AppTarget> = targets.asSequence()
        .filter { it.packageName.value != ownPackage }
        .filter { it in addedTargets }
        .filter { it !in lockedTargets }
        .distinct()
        .toList()
}
