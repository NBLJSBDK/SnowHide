package com.nbljsbdk.snowhide.ui.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * 应用图标加载器（图标包协议 + 系统图标回退 + 内存缓存）
 *
 * 设计文档 §3.4：支持第三方系统图标包——
 * - 发现：queryBroadcastReceivers(INSTALL_ICON_PACK)
 * - 请求：RESOLVE_ICON 有序广播（extra 包名）→ 图标包返回 Bitmap
 * - 失败/未选择 → 回退系统默认图标
 */
object AppIconLoader {

    private lateinit var context: Context

    /** 初始化（MainActivity 启动时调用一次） */
    fun init(context: Context) {
        if (::context.isInitialized) return
        AppIconLoader.context = context.applicationContext
    }

    /** 已装图标包信息（设置页选择器数据源） */
    data class IconPackInfo(val pkg: String, val label: String, val icon: ImageBitmap)

    private val pm: PackageManager get() = context.packageManager
    private val cache = mutableMapOf<String, ImageBitmap>()

    /** 当前使用的图标包包名（空 = 系统默认） */
    @Volatile
    var iconPackPkg: String = ""

    /**
     * 加载应用图标
     * @param pkg 目标应用包名
     * @return 图标（图标包图标或系统图标）
     */
    suspend fun loadIcon(pkg: String): ImageBitmap = withContext(Dispatchers.IO) {
        cache[pkg]?.let { return@withContext it }
        val icon = loadIconPackIcon(pkg) ?: loadSystemIcon(pkg)
        cache[pkg] = icon
        icon
    }

    /** 清空缓存（切换图标包时调用） */
    fun clearCache() = cache.clear()

    /** 发现所有已装图标包 */
    suspend fun queryIconPacks(): List<IconPackInfo> = withContext(Dispatchers.IO) {
        val intent = Intent(ACTION_INSTALL_ICON_PACK)
        val receivers: List<ResolveInfo> = runCatching {
            pm.queryBroadcastReceivers(intent, 0)
        }.getOrDefault(emptyList())

        receivers.mapNotNull { info ->
            val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
            val label = runCatching {
                info.loadLabel(pm).toString()
            }.getOrDefault(pkg)
            val icon = runCatching {
                info.loadIcon(pm).toBitmap().asImageBitmap()
            }.getOrNull()
            IconPackInfo(pkg, label, icon ?: loadSystemIcon(pkg))
        }
    }

    /** 向图标包请求自定义图标（标准 Launcher 协议，有序广播等待结果） */
    private suspend fun loadIconPackIcon(pkg: String): ImageBitmap? {
        if (iconPackPkg.isEmpty()) return null
        val intent = Intent(ACTION_RESOLVE_ICON).apply {
            setPackage(iconPackPkg)
            putExtra(EXTRA_PACKAGE, pkg)
            // 标准协议：component 必须传 ComponentName（目标应用启动组件），
            // 传字符串 "pkg/." 图标包不识别
            val component = runCatching {
                pm.getLaunchIntentForPackage(pkg)?.component
            }.getOrNull()
            if (component != null) {
                putExtra(EXTRA_COMPONENT, component)
            }
        }
        return runCatching {
            suspendCancellableCoroutine { cont ->
                context.sendOrderedBroadcast(
                    intent,
                    null,
                    object : BroadcastReceiver() {
                        override fun onReceive(ctx: Context, i: Intent) {
                            val result = runCatching {
                                val extras: Bundle = getResultExtras(true) ?: return@runCatching null
                                val bitmap: Bitmap? = extras.getParcelable(EXTRA_ICON)
                                bitmap?.asImageBitmap()
                            }.getOrNull()
                            if (cont.isActive) cont.resume(result)
                        }
                    },
                    null,
                    0,
                    null,
                    null,
                )
            }
        }.getOrNull()
    }

    /** 系统默认图标（回退） */
    private fun loadSystemIcon(pkg: String): ImageBitmap {
        val drawable = runCatching { pm.getApplicationIcon(pkg) }.getOrNull()
        val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: runCatching {
            drawable?.let { d ->
                Bitmap.createBitmap(
                    d.intrinsicWidth.coerceAtLeast(1),
                    d.intrinsicHeight.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888,
                ).also { b ->
                    val canvas = android.graphics.Canvas(b)
                    d.setBounds(0, 0, b.width, b.height)
                    d.draw(canvas)
                }
            }
        }.getOrNull()
        return bitmap?.asImageBitmap() ?: fallbackIcon()
    }

    /** 兜底占位图标（灰色圆块） */
    private fun fallbackIcon(): ImageBitmap {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xFFD8E4F1.toInt())
        return bitmap.asImageBitmap()
    }

    const val ACTION_INSTALL_ICON_PACK = "com.android.launcher.action.INSTALL_ICON_PACK"
    const val ACTION_RESOLVE_ICON = "com.android.launcher.action.RESOLVE_ICON"
    const val EXTRA_PACKAGE = "package"
    const val EXTRA_COMPONENT = "component"
    const val EXTRA_ICON = "icon"
}
