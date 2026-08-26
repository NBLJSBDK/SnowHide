package com.nbljsbdk.snowhide.domain.settings

import com.nbljsbdk.snowhide.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.map

/** 外观设置业务入口：主屏只通过本门面读取和修改视觉设置。 */
class AppearanceSettingsUseCase(
    private val repository: SettingsRepository,
) {

    val showAppName = repository.showAppName
    val iconPack = repository.iconPack
    val transparentBg = repository.transparentBg
    val wallpaperOverlay = repository.wallpaperOverlay
    val iconShape = repository.iconShape
    val animationLevel = repository.animationLevel.map(AnimationLevel::fromStorageValue)
    val freezeStyle = repository.freezeStyle

    fun setShowAppName(enabled: Boolean) = repository.setShowAppName(enabled)

    fun setTransparentBg(enabled: Boolean) = repository.setTransparentBg(enabled)

    fun setWallpaperOverlay(alpha: Float) = repository.setWallpaperOverlay(alpha)

    fun setAnimationLevel(level: AnimationLevel) = repository.setAnimationLevel(level.storageValue)

    fun setFreezeStyle(style: String) = repository.setFreezeStyle(style)

    fun setIconShape(shape: String) = repository.setIconShape(shape)

    fun setIconPack(pkg: String) = repository.setIconPack(pkg)
}
