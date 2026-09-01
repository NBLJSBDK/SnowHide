package com.nbljsbdk.snowhide.core.operation

/** 按具体用户空间构造受控 pm 查询命令。 */
object PmQuery {

    fun listUsers(): String = "pm list users"

    fun listPackages(
        userId: Int,
        frozenOnly: Boolean = false,
        systemOnly: Boolean = false,
    ): Result<String> {
        if (userId < 0) {
            return Result.failure(IllegalArgumentException("非法用户 ID：$userId"))
        }
        if (frozenOnly && systemOnly) {
            return Result.failure(IllegalArgumentException("包查询过滤条件冲突"))
        }
        val suffix = when {
            frozenOnly -> " -d"
            systemOnly -> " -s"
            else -> ""
        }
        return Result.success("pm list packages --user $userId$suffix")
    }
}
