package com.nbljsbdk.snowhide.app

import android.content.Context
import com.nbljsbdk.snowhide.core.engine.EngineManager
import com.nbljsbdk.snowhide.core.mode.FreezeExecutor
import com.nbljsbdk.snowhide.data.repo.GridRepository
import com.nbljsbdk.snowhide.data.repo.QuickToggleRepository
import com.nbljsbdk.snowhide.domain.FreezeUseCase
import com.nbljsbdk.snowhide.domain.QuickToggleUseCase

/**
 * 进程级依赖容器——UseCase 的唯一构造点。
 *
 * 由 [CompositionRoot] 创建并持有；Activity / Service / Receiver /
 * Tile 等 Android 入口只从容器取依赖，禁止在各入口内直接 new UseCase。
 */
class AppContainer(private val context: Context) {

    /** 冻结业务入口（主界面、快捷方式、Recent、锁屏清理共用） */
    val freezeUseCase: FreezeUseCase by lazy {
        FreezeUseCase(FreezeExecutor(EngineManager), GridRepository, EngineManager)
    }

    /** 快速启停入口（下拉磁贴、App Shortcut 共用） */
    val quickToggleUseCase: QuickToggleUseCase by lazy {
        QuickToggleUseCase(
            GridRepository,
            EngineManager,
            QuickToggleRepository,
        )
    }
}
