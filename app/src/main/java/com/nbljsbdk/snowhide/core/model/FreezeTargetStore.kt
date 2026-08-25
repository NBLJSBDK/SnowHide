package com.nbljsbdk.snowhide.core.model

/**
 * 冻结业务读取应用目标和锁定状态所需的最小数据端口。
 */
interface FreezeTargetStore {
    fun isAppAdded(pkg: String): Boolean

    fun isLocked(pkg: String): Boolean

    fun allAddedPackages(): List<String>

    fun folderPackages(folderId: Long): List<String>
}
