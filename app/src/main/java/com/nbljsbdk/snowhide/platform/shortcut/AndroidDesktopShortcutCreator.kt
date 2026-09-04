package com.nbljsbdk.snowhide.platform.shortcut

import android.content.Context
import android.content.Intent
import android.app.Activity
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.UserHandle
import com.nbljsbdk.snowhide.core.model.AppTarget
import com.nbljsbdk.snowhide.domain.shortcut.DesktopShortcutCreator
import com.nbljsbdk.snowhide.domain.shortcut.DesktopShortcutSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Android 桌面固定快捷方式适配：系统应用图标 + 雪藏角标。 */
class AndroidDesktopShortcutCreator(
    context: Context,
    private val shortcutActivity: Class<out Activity>,
) : DesktopShortcutCreator {

    private val appContext = context.applicationContext

    override suspend fun requestPin(target: AppTarget, appLabel: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val shortcutManager = appContext.getSystemService(ShortcutManager::class.java)
                ?: return@withContext Result.failure<Unit>(
                    IllegalStateException("系统不支持桌面快捷方式"),
                )
            if (!shortcutManager.isRequestPinShortcutSupported) {
                return@withContext Result.failure(
                    IllegalStateException("当前系统桌面不支持固定快捷方式"),
                )
            }
            if (target.userId > Int.MAX_VALUE / USER_ID_RANGE) {
                return@withContext Result.failure(
                    IllegalArgumentException("非法用户空间：${target.userId}"),
                )
            }

            val baseIcon = loadSystemLauncherIcon(target)
                ?: return@withContext Result.failure(
                    IllegalStateException("无法读取应用原始图标"),
                )
            val snowHideIcon = runCatching {
                appContext.packageManager.getApplicationIcon(appContext.packageName)
            }.getOrNull() ?: return@withContext Result.failure(
                IllegalStateException("无法读取雪藏图标"),
            )
            val icon = runCatching { composeIcon(baseIcon, snowHideIcon) }
                .getOrElse { error ->
                    return@withContext Result.failure(error)
                }
            val displayLabel = DesktopShortcutSpec.longLabel(target, appLabel)
            val shortcutIntent = Intent(appContext, shortcutActivity).apply {
                action = DesktopShortcutSpec.ACTION_OPEN_TARGET
                putExtra(DesktopShortcutSpec.EXTRA_PACKAGE_NAME, target.packageName.value)
                putExtra(DesktopShortcutSpec.EXTRA_USER_ID, target.userId)
            }
            val shortcutInfo = try {
                ShortcutInfo.Builder(appContext, DesktopShortcutSpec.shortcutId(target))
                    .setShortLabel(DesktopShortcutSpec.shortLabel(target, appLabel))
                    .setLongLabel(displayLabel.take(MAX_LONG_LABEL_LENGTH))
                    .setIcon(Icon.createWithAdaptiveBitmap(icon))
                    .setIntent(shortcutIntent)
                    .build()
            } catch (error: Throwable) {
                return@withContext Result.failure(error)
            }

            val requestResult = withContext(Dispatchers.Main.immediate) {
                runCatching { shortcutManager.requestPinShortcut(shortcutInfo, null) }
            }
            requestResult.fold(
                onSuccess = { requested ->
                    if (requested) {
                        Result.success(Unit)
                    } else {
                        Result.failure(IllegalStateException("系统桌面拒绝了固定快捷方式请求"))
                    }
                },
                onFailure = { Result.failure(it) },
            )
        }

    /** LauncherActivityInfo 能按目标用户空间返回系统桌面感知的图标和分身角标。 */
    private fun loadSystemLauncherIcon(target: AppTarget): Drawable? {
        val user = UserHandle.getUserHandleForUid(target.userId * USER_ID_RANGE)
        val launcherIcon = runCatching {
            appContext.getSystemService(LauncherApps::class.java)
                ?.getActivityList(target.packageName.value, user)
                ?.firstOrNull()
                ?.getBadgedIcon(appContext.resources.displayMetrics.densityDpi)
        }.getOrNull()
        return launcherIcon ?: runCatching {
            appContext.packageManager.getApplicationIcon(target.packageName.value)
        }.getOrNull()
    }

    /** 合成固定尺寸图标，给雪藏角标留出安全边距，避免被桌面再次裁剪。 */
    private fun composeIcon(baseIcon: Drawable, badgeIcon: Drawable): Bitmap {
        val bitmap = render(baseIcon, ICON_SIZE)
        val canvas = Canvas(bitmap)
        val badgeSize = (ICON_SIZE * 0.30f).roundToInt()
        val margin = (ICON_SIZE * 0.045f).roundToInt()
        val center = ICON_SIZE - margin - badgeSize / 2f
        val backingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, badgeSize / 2f + margin * 0.18f, backingPaint)

        val inset = (badgeSize * 0.12f).roundToInt()
        badgeIcon.mutate().setBounds(
            (center - badgeSize / 2f + inset).roundToInt(),
            (center - badgeSize / 2f + inset).roundToInt(),
            (center + badgeSize / 2f - inset).roundToInt(),
            (center + badgeSize / 2f - inset).roundToInt(),
        )
        badgeIcon.draw(canvas)
        return bitmap
    }

    private fun render(drawable: Drawable, size: Int): Bitmap =
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            drawable.mutate().setBounds(0, 0, size, size)
            drawable.draw(canvas)
        }

    private companion object {
        private const val USER_ID_RANGE = 100_000
        private const val ICON_SIZE = 432
        private const val MAX_LONG_LABEL_LENGTH = 100
    }
}
