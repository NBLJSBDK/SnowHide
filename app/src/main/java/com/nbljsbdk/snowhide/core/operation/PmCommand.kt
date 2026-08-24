package com.nbljsbdk.snowhide.core.operation

import com.nbljsbdk.snowhide.core.model.PackageName

/** 目前 P0 允许的包管理器操作。新增操作必须先在这里定义，而不是传入任意前缀。 */
enum class PmOperation {
    DISABLE_USER,
    ENABLE,
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
        val pkg = parsed.value
        return Result.success(
            when (operation) {
                PmOperation.DISABLE_USER -> "pm disable-user --user $userId $pkg"
                // 保持 P0 已验证的原始命令格式；多用户启用在目标能力接入时单独扩展。
                PmOperation.ENABLE -> "pm enable $pkg"
                PmOperation.DISABLE -> "pm disable $pkg"
                PmOperation.UNINSTALL -> "pm uninstall --user $userId $pkg"
            },
        )
    }
}
