package com.nbljsbdk.snowhide.core.operation

import com.nbljsbdk.snowhide.core.model.PackageName
import com.nbljsbdk.snowhide.core.model.UserProfile

/** 解析 shell 返回的用户空间和包名列表，保持与 Android 无关。 */
object PmOutputParser {

    private val userPattern = Regex("^\\s*UserInfo\\{(\\d+):(.*):([0-9a-fA-F]+)\\}.*$")

    fun users(output: String): List<UserProfile> = output.lineSequence()
        .mapNotNull { line ->
            val match = userPattern.matchEntire(line) ?: return@mapNotNull null
            val id = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val name = match.groupValues[2].trim().ifEmpty { "用户 $id" }
            // UserInfo.toString() 使用 Integer.toHexString(flags)，输出不带 0x 前缀。
            val flags = match.groupValues[3].toLongOrNull(radix = 16)
                ?.takeIf { it <= 0xFFFF_FFFFL }
                ?.toInt()
                ?: return@mapNotNull null
            UserProfile(
                id = id,
                name = name,
                flags = flags,
                running = line.trimEnd().endsWith(" running", ignoreCase = true),
            )
        }
        .distinctBy { it.id }
        .sortedBy { it.id }
        .toList()

    fun packages(output: String): List<String> = output.lineSequence()
        .map { it.trim().removePrefix("package:").trim() }
        .filter { PackageName.isValid(it) }
        .distinct()
        .sorted()
        .toList()
}
