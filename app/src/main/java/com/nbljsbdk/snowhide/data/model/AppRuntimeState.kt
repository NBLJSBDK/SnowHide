package com.nbljsbdk.snowhide.data.model

/**
 * 应用在系统中的实际状态。
 *
 * UNKNOWN 不等于未冻结：无法读取 Shizuku 状态时保留缓存显示，避免误判。
 */
enum class AppRuntimeState {
    ACTIVE,
    FROZEN,
    MISSING,
    UNKNOWN,
}
