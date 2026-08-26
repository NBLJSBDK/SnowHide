package com.nbljsbdk.snowhide.domain.folder

import com.nbljsbdk.snowhide.data.prefs.SettingsRepository

/** 文件夹页面设置面板只需展示的文件夹事实。 */
data class FolderPageOption(
    val id: Long,
    val name: String,
    val sortOrder: Int,
)

/** 文件夹页面设置业务入口：控制滑动页面，不改变主屏宫格结构。 */
class FolderPageSettingsUseCase(
    private val repository: SettingsRepository,
) {

    val loopEnabled = repository.folderPageLoopEnabled
    val excludedFolderIds = repository.excludedFolderIds
    val showReturnHomeButton = repository.showReturnHomeButton
    val resetHomeOnReentry = repository.resetHomeOnReentry

    fun setLoopEnabled(enabled: Boolean) = repository.setFolderPageLoopEnabled(enabled)

    fun setFolderExcluded(folderId: Long, excluded: Boolean) {
        val next = if (excluded) {
            excludedFolderIds.value + folderId
        } else {
            excludedFolderIds.value - folderId
        }
        repository.setExcludedFolderIds(next)
    }

    fun removeFolder(folderId: Long) {
        if (folderId in excludedFolderIds.value) {
            repository.setExcludedFolderIds(excludedFolderIds.value - folderId)
        }
    }

    fun setShowReturnHomeButton(enabled: Boolean) = repository.setShowReturnHomeButton(enabled)

    fun setResetHomeOnReentry(enabled: Boolean) = repository.setResetHomeOnReentry(enabled)
}
