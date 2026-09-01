package com.nbljsbdk.snowhide.data.model

import com.nbljsbdk.snowhide.core.model.AppTarget

/**
 * 将旧版 GridItem.locked 字段迁移到独立锁定集合，并把旧字段归一化。
 *
 * 纯 Kotlin 保证迁移可以在 JVM 测试中验证，且重复执行保持幂等。
 */
data class GridLockMigration(
    val items: List<GridItem>,
    val lockedPackages: Set<String>,
)

/** 目标身份版锁定迁移结果。 */
data class GridTargetLockMigration(
    val items: List<GridItem>,
    val lockedTargets: Set<AppTarget>,
)

fun migrateLegacyLockedItems(
    items: List<GridItem>,
    persistedLockedPackages: Set<String>,
): GridLockMigration {
    val legacyLocked = items.filter { it.locked }.mapNotNull { it.pkg }.toSet()
    return GridLockMigration(
        items = items.map { item -> if (item.locked) item.copy(locked = false) else item },
        lockedPackages = persistedLockedPackages + legacyLocked,
    )
}

/** 把旧 GridItem.locked 合并进带用户空间的锁定集合。 */
fun migrateLegacyLockedTargets(
    items: List<GridItem>,
    persistedLockedTargets: Set<AppTarget>,
): GridTargetLockMigration {
    val legacyLocked = items.asSequence()
        .filter { it.locked && it.type == "app" && it.pkg != null }
        .mapNotNull { AppTarget.create(it.pkg!!, it.userId).getOrNull() }
        .toSet()
    return GridTargetLockMigration(
        items = items.map { item -> if (item.locked) item.copy(locked = false) else item },
        lockedTargets = persistedLockedTargets + legacyLocked,
    )
}
