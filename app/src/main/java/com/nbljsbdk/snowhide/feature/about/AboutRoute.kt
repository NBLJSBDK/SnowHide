package com.nbljsbdk.snowhide.feature.about

import androidx.compose.runtime.Composable
import com.nbljsbdk.snowhide.domain.FreezeUseCase

/** 关于页 Route：组合根负责传入跨页面共享的业务用例和回调。 */
@Composable
fun AboutRoute(
    onClose: () -> Unit,
    systemUnlocked: Boolean,
    onUnlockSystemApps: () -> Unit,
    onRelockSystemApps: () -> Unit,
    freezeUseCase: FreezeUseCase,
) {
    AboutScreen(
        onClose = onClose,
        systemUnlocked = systemUnlocked,
        onUnlockSystemApps = onUnlockSystemApps,
        onRelockSystemApps = onRelockSystemApps,
        freezeUseCase = freezeUseCase,
    )
}
