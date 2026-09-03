package com.nbljsbdk.snowhide.domain.appmanage

/** 增删应用页的系统应用可见性规则。 */
object AppManageFilterPolicy {
    fun allowsSystemApp(
        isSystem: Boolean,
        systemUnlocked: Boolean,
        showSystemOnly: Boolean,
    ): Boolean = when {
        !systemUnlocked -> !isSystem
        showSystemOnly -> isSystem
        else -> !isSystem
    }
}
