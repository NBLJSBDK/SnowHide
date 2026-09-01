package com.nbljsbdk.snowhide.core.model

/**
 * 应用操作目标：包名和具体用户空间必须成对存在。
 *
 * 不允许使用 -1、USER_ALL 或“找不到用户时回退到 user 0”等隐式目标。
 */
data class AppTarget(
    val packageName: PackageName,
    val userId: Int,
) {

    /** 用于 UI key、排序记录和内存集合的稳定身份键。 */
    val key: String
        get() = "$userId:${packageName.value}"

    /** user 0 仍可通过旧包名接口操作，其他用户必须显式携带 userId。 */
    val isPrimaryUser: Boolean
        get() = userId == PRIMARY_USER_ID

    companion object {
        const val PRIMARY_USER_ID = 0

        /** 创建并校验一个具体用户空间中的应用目标。 */
        fun create(packageName: String, userId: Int): Result<AppTarget> {
            val parsed = PackageName.parse(packageName).getOrElse { return Result.failure(it) }
            if (userId < 0) {
                return Result.failure(IllegalArgumentException("非法用户 ID：$userId"))
            }
            return Result.success(AppTarget(parsed, userId))
        }
    }
}
