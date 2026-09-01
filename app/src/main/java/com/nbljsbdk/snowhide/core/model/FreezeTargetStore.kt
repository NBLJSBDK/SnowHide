package com.nbljsbdk.snowhide.core.model

/**
 * 冻结业务读取应用目标和锁定状态所需的最小数据端口。
 */
interface FreezeTargetStore {
    fun isAppAdded(pkg: String): Boolean

    fun isLocked(pkg: String): Boolean

    fun allAddedPackages(): List<String>

    fun folderPackages(folderId: Long): List<String>

    /** 目标身份版端口；旧实现默认把包名解释为 user 0。 */
    fun isAppAdded(target: AppTarget): Boolean =
        target.isPrimaryUser && isAppAdded(target.packageName.value)

    fun isLocked(target: AppTarget): Boolean =
        target.isPrimaryUser && isLocked(target.packageName.value)

    fun allAddedTargets(): List<AppTarget> = allAddedPackages().mapNotNull {
        AppTarget.create(it, AppTarget.PRIMARY_USER_ID).getOrNull()
    }

    fun folderTargets(folderId: Long): List<AppTarget> = folderPackages(folderId).mapNotNull {
        AppTarget.create(it, AppTarget.PRIMARY_USER_ID).getOrNull()
    }
}
