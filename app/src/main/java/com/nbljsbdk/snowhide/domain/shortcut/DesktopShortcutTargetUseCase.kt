package com.nbljsbdk.snowhide.domain.shortcut

import com.nbljsbdk.snowhide.core.model.AppTarget
import com.nbljsbdk.snowhide.core.model.FreezeTargetStore

/** 桌面快捷方式打开目标前的业务准备：校验归属，并按宫格规则临时解冻。 */
class DesktopShortcutTargetUseCase(
    private val targetStore: FreezeTargetStore,
    private val isFrozen: (AppTarget) -> Boolean,
    private val unfreeze: suspend (AppTarget) -> Result<Unit>,
) {

    /** 目标移出宫格后，旧快捷方式不能继续启动它。 */
    suspend fun prepareToOpen(target: AppTarget): Result<Unit> {
        if (target !in targetStore.allAddedTargets()) {
            return Result.failure(IllegalStateException("应用已不在雪藏宫格中"))
        }
        if (!isFrozen(target)) return Result.success(Unit)
        return unfreeze(target)
    }
}
