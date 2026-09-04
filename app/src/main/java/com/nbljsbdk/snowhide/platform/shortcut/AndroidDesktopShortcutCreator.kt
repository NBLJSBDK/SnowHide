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
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.UserHandle
import com.nbljsbdk.snowhide.core.model.AppTarget
import com.nbljsbdk.snowhide.domain.shortcut.DesktopShortcutCreator
import com.nbljsbdk.snowhide.domain.shortcut.DesktopShortcutSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Android 桌面固定快捷方式适配：图标包图标（可回退系统图标）+ 雪藏角标。 */
class AndroidDesktopShortcutCreator(
    context: Context,
    private val shortcutActivity: Class<out Activity>,
    private val iconProvider: ShortcutIconProvider,
    private val iconShapeProvider: () -> String,
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

            val baseIcon = loadBaseIcon(target)
                ?: return@withContext Result.failure(
                    IllegalStateException("无法读取应用图标"),
                )
            val snowHideIcon = runCatching {
                appContext.packageManager.getApplicationIcon(appContext.packageName)
            }.getOrNull() ?: return@withContext Result.failure(
                IllegalStateException("无法读取雪藏图标"),
            )
            val icon = runCatching {
                composeIcon(baseIcon, snowHideIcon, iconShapeProvider())
            }
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
                    .setIcon(Icon.createWithBitmap(icon))
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

    /** 图标包优先；分身在自定义图标上继续叠加系统用户徽标。 */
    private suspend fun loadBaseIcon(target: AppTarget): Drawable? {
        val customIcon = runCatching { iconProvider.load(target) }
            .getOrNull()
            ?.let { BitmapDrawable(appContext.resources, it) }
        if (customIcon != null) {
            return if (target.isPrimaryUser) {
                customIcon
            } else {
                runCatching {
                    appContext.packageManager.getUserBadgedIcon(
                        customIcon,
                        UserHandle.getUserHandleForUid(target.userId * USER_ID_RANGE),
                    )
                }.getOrDefault(customIcon)
            }
        }
        return loadSystemLauncherIcon(target)
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
        if (launcherIcon != null) return launcherIcon
        if (!target.isPrimaryUser) return null
        return runCatching { appContext.packageManager.getApplicationIcon(target.packageName.value) }
            .getOrNull()
    }

    /** 合成固定尺寸图标，先按应用图标形状裁剪，再叠加雪藏角标。 */
    private fun composeIcon(
        baseIcon: Drawable,
        badgeIcon: Drawable,
        iconShape: String,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val clipped = iconShape == "circle"
        if (clipped) {
            val circle = Path().apply {
                addCircle(
                    ICON_SIZE / 2f,
                    ICON_SIZE / 2f,
                    ICON_SIZE / 2f,
                    Path.Direction.CW,
                )
            }
            canvas.save()
            canvas.clipPath(circle)
        }
        baseIcon.mutate().setBounds(0, 0, ICON_SIZE, ICON_SIZE)
        baseIcon.draw(canvas)
        if (clipped) canvas.restore()

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

    private companion object {
        private const val USER_ID_RANGE = 100_000
        private const val ICON_SIZE = 432
        private const val MAX_LONG_LABEL_LENGTH = 100
    }
}
