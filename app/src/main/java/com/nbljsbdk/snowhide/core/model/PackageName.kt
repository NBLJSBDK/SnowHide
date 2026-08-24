package com.nbljsbdk.snowhide.core.model

/**
 * 包名输入校验。
 *
 * 包名会进入特权 shell 命令，不能把来自备份或 UI 的原始字符串直接拼入命令。
 * 这里只做与 Android 无关的语法校验；包是否安装由上层的 PackageManager 适配负责。
 */
@JvmInline
value class PackageName private constructor(val value: String) {

    companion object {
        // Android 应用包名遵循 Java 包名形式；禁止空白和 shell 元字符。
        private val pattern = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)*$")

        /** 解析并校验包名，失败时返回可展示的原因。 */
        fun parse(raw: String): Result<PackageName> {
            if (!pattern.matches(raw)) {
                return Result.failure(IllegalArgumentException("非法应用包名：$raw"))
            }
            return Result.success(PackageName(raw))
        }

        /** 仅用于已经由系统返回的包名集合快速判断。 */
        fun isValid(raw: String): Boolean = pattern.matches(raw)
    }
}
