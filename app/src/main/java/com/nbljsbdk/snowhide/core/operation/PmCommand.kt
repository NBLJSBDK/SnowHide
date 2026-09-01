package com.nbljsbdk.snowhide.core.operation

import com.nbljsbdk.snowhide.core.model.PackageName

/** 目前 P0 允许的包管理器操作。新增操作必须先在这里定义，而不是传入任意前缀。 */
enum class PmOperation {
    DISABLE_USER,
    ENABLE,
    ENABLE_USER,
    DISABLE,
    UNINSTALL,
}

/**
 * 受控 PM 命令构造器。
 *
 * Binder 协议目前仍接收 String，因此安全边界先放在命令构造处：
 * 所有带包名的命令必须经过 [PackageName.parse]，业务层不再拼接原始字符串。
 */
object PmCommand {

    fun build(
        operation: PmOperation,
        packageName: String,
        userId: Int = 0,
    ): Result<String> {
        val parsed = PackageName.parse(packageName).getOrElse { return Result.failure(it) }
        if (userId < 0) {
            return Result.failure(IllegalArgumentException("非法用户 ID：$userId"))
        }
        if ((operation == PmOperation.ENABLE || operation == PmOperation.DISABLE) && userId != 0) {
            return Result.failure(
                IllegalArgumentException("${operation.name} 必须使用明确的用户空间命令"),
            )
        }
        val pkg = parsed.value
        return Result.success(
            when (operation) {
                PmOperation.DISABLE_USER -> "pm disable-user --user $userId $pkg"
                PmOperation.ENABLE -> "pm enable --user $userId $pkg"
                PmOperation.ENABLE_USER -> "pm enable --user $userId $pkg"
                PmOperation.DISABLE -> "pm disable --user $userId $pkg"
                PmOperation.UNINSTALL -> "pm uninstall --user $userId $pkg"
            },
        )
    }
}
