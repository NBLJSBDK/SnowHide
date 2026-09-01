package com.nbljsbdk.snowhide.core.model

/** 由系统用户空间列表提供的稳定用户信息。 */
data class UserProfile(
    val id: Int,
    val name: String,
    val flags: Int = 0,
    val running: Boolean = false,
) {
    /** Android UserInfo.FLAG_MANAGED_PROFILE，避免 core 依赖 Android SDK 常量。 */
    val isManagedProfile: Boolean
        get() = flags and MANAGED_PROFILE_FLAG != 0

    companion object {
        private const val MANAGED_PROFILE_FLAG = 0x20
    }
}
