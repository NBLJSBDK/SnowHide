package com.nbljsbdk.snowhide.data.model

/**
 * 将旧版 GridItem.locked 字段迁移到独立锁定集合，并把旧字段归一化。
 *
 * 纯 Kotlin 保证迁移可以在 JVM 测试中验证，且重复执行保持幂等。
 */
data class GridLockMigration(
    val items: List<GridItem>,
    val lockedPackages: Set<String>,
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
