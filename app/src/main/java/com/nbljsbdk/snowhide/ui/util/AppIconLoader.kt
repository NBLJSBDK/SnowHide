package com.nbljsbdk.snowhide.ui.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
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
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 应用图标加载器（图标包协议 + appfilter 解析 + 系统回退 + 内存缓存）
 *
 * 设计文档 §3.4：支持第三方系统图标包——
 * 1. 发现：INSTALL_ICON_PACK 广播（标准包）+ **appfilter 扫描**
 *    （Pure 轻语/轻风等无广播的包，assets/appfilter.xml 存在即图标包）
 * 2. 请求：RESOLVE_ICON 有序广播（标准包）→ 失败回退
 *    **appfilter.xml 解析**（组件 → drawable 资源，createPackageContext
 *    访问非公开资源）
 * 3. 失败/未选择 → 回退系统默认图标
 */
object AppIconLoader {

    private lateinit var context: Context

    /** 初始化（MainActivity 启动时调用一次） */
    fun init(context: Context) {
        if (::context.isInitialized) return
        AppIconLoader.context = context.applicationContext
    }

    /** 已装图标包信息（美化浮框选择器数据源） */
    data class IconPackInfo(val pkg: String, val label: String, val icon: ImageBitmap)

    private val pm: PackageManager get() = context.packageManager
    private val cache = mutableMapOf<String, ImageBitmap>()

    /** 已解析的 appfilter 映射缓存（图标包 → component→drawable 名） */
    private val appFilterCache = mutableMapOf<String, Map<String, String>>()

    /** drawable 名 → 资源 ID 缓存（getIdentifier 是 IPC，113 次很慢） */
    private val resIdCache = mutableMapOf<String, Int>()

    /** appfilter 扫描结果缓存（避免每次全量遍历已装包） */
    private var scannedPacks: List<IconPackInfo>? = null

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
    fun clearCache() {
        cache.clear()
        appFilterCache.clear()
        resIdCache.clear()
        scannedPacks = null
    }

    /**
     * 启动预热（HomeViewModel init 调用）：后台解析当前图标包的
     * appfilter——否则首图标触发时 synchronized 阻塞 2 秒，图标逐个
     * 慢慢出现 + 期间滑动卡顿。
     */
    suspend fun prewarm() = withContext(Dispatchers.IO) {
        if (iconPackPkg.isEmpty()) return@withContext
        runCatching {
            val packContext = context.createPackageContext(iconPackPkg, restrictedFlags())
            appFilterMap(packContext)
        }
    }

    /** 只清除图标包扫描缓存（手动刷新列表用，不清图标缓存） */
    fun clearScanCache() {
        scannedPacks = null
    }

    /** 发现所有已装图标包（广播 + appfilter 扫描，去重） */
    suspend fun queryIconPacks(): List<IconPackInfo> = withContext(Dispatchers.IO) {
        scannedPacks?.let { return@withContext it }
        val result = linkedMapOf<String, IconPackInfo>()

        // 1. 标准广播发现
        val receivers: List<ResolveInfo> = runCatching {
            pm.queryBroadcastReceivers(Intent(ACTION_INSTALL_ICON_PACK), 0)
        }.getOrDefault(emptyList())
        receivers.forEach { info ->
            val pkg = info.activityInfo?.packageName ?: return@forEach
            result.putIfAbsent(
                pkg,
                IconPackInfo(
                    pkg,
                    runCatching { info.loadLabel(pm).toString() }.getOrDefault(pkg),
                    runCatching { info.loadIcon(pm).toBitmap().asImageBitmap() }
                        .getOrNull() ?: loadSystemIcon(pkg),
                ),
            )
        }

        // 2. appfilter 扫描（非系统第三方包，assets/appfilter.xml 存在即图标包）
        val apps = pm.getInstalledApplications(0)
        apps.filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .filter { it.packageName != context.packageName }
            .forEach { app ->
                if (app.packageName in result) return@forEach
                val hasAppFilter = runCatching {
                    val pkgCtx = context.createPackageContext(
                        app.packageName,
                        restrictedFlags(),
                    )
                    pkgCtx.assets.open("appfilter.xml").close()
                    true
                }.onFailure { e ->
                    if (app.packageName.contains("pure") || app.packageName.contains("wind") ||
                        app.packageName.contains("iconpack")
                    ) {
                    }
                }.getOrDefault(false)
                if (hasAppFilter) {
                    result[app.packageName] = IconPackInfo(
                        app.packageName,
                        app.loadLabel(pm).toString(),
                        loadSystemIcon(app.packageName),
                    )
                }
            }
        scannedPacks = result.values.toList()
        scannedPacks!!
    }

    /** 向图标包请求自定义图标：RESOLVE_ICON 广播优先（仅支持该协议的包），appfilter 解析回退 */
    private suspend fun loadIconPackIcon(pkg: String): ImageBitmap? {
        if (iconPackPkg.isEmpty()) return null
        // 只有图标包声明了 RESOLVE_ICON receiver 才发广播——
        // 轻语/轻风等无 receiver 的包发广播会挂起超时（113 次=卡死）
        if (supportsResolveIcon(iconPackPkg)) {
            val broadcast = requestResolveIcon(pkg)
            if (broadcast != null) return broadcast
        }
        return loadAppFilterIcon(pkg)
    }

    /** 图标包是否声明 RESOLVE_ICON receiver（结果缓存，避免每次查询） */
    private val resolveIconSupport = mutableMapOf<String, Boolean>()

    private fun supportsResolveIcon(packPkg: String): Boolean =
        resolveIconSupport.getOrPut(packPkg) {
            val support = runCatching {
                pm.queryBroadcastReceivers(
                    Intent(ACTION_RESOLVE_ICON).setPackage(packPkg),
                    0,
                ).isNotEmpty()
            }.getOrDefault(false)
            support
        }

    /** 标准 Launcher 协议：RESOLVE_ICON 有序广播（500ms 超时防挂起） */
    private suspend fun requestResolveIcon(pkg: String): ImageBitmap? {
        val intent = Intent(ACTION_RESOLVE_ICON).apply {
            setPackage(iconPackPkg)
            putExtra(EXTRA_PACKAGE, pkg)
            val component = runCatching {
                pm.getLaunchIntentForPackage(pkg)?.component
            }.getOrNull()
            if (component != null) {
                putExtra(EXTRA_COMPONENT, component)
            }
        }
        return withTimeoutOrNull(500) {
            runCatching {
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
    }

    /**
     * appfilter.xml 协议（Pure 轻语/轻风等无广播图标包）：
     * 解析 <item component="ComponentInfo{com.pkg/...}" drawable="name"/>
     * 匹配目标应用组件 → 加载图标包 res/drawable 资源。
     */
    private fun loadAppFilterIcon(pkg: String): ImageBitmap? = runCatching {
        val packContext = context.createPackageContext(iconPackPkg, restrictedFlags())
        val map = appFilterMap(packContext)
        // 匹配顺序：包名优先（冻结应用 getLaunchIntentForPackage 返回 null，
        // 组件匹配失效——包名 key 在 parseAppFilter 里已存），再组件
        val launchComponent = pm.getLaunchIntentForPackage(pkg)?.component
        val drawableName = map[pkg]
            ?: launchComponent?.flattenToString()?.let { map[it] }
            ?: launchComponent?.flattenToShortString()?.let { map[it] }
        if (drawableName == null) {
            return@runCatching null
        }
        val id = resIdCache.getOrPut(drawableName) {
            packContext.resources.getIdentifier(drawableName, "drawable", iconPackPkg)
        }
        if (id == 0) {
            return@runCatching null
        }
        val drawable = packContext.getDrawable(id) ?: return@runCatching null
        val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: runCatching {
            Bitmap.createBitmap(
                drawable.intrinsicWidth.coerceAtLeast(1),
                drawable.intrinsicHeight.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            ).also { b ->
                val canvas = android.graphics.Canvas(b)
                drawable.setBounds(0, 0, b.width, b.height)
                drawable.draw(canvas)
            }
        }.getOrNull()
        bitmap?.let { scaleIcon(it) }?.asImageBitmap()
    }.getOrNull()

    /** 统一缩放图标（图标包资源是 xxxhdpi 大图，113 个原始纹理滑动卡顿——缩到 256px 内） */
    private fun scaleIcon(bitmap: Bitmap): Bitmap {
        val target = 256
        if (bitmap.width <= target && bitmap.height <= target) return bitmap
        val ratio = target.toFloat() / maxOf(bitmap.width, bitmap.height)
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt(),
            (bitmap.height * ratio).toInt(),
            true,
        )
    }

    /**
     * appfilter 映射（单例同步解析——113 个并发 loadIcon 同时
     * getOrPut 同一个大文件会重复解析+map 竞争，其余全部卡住，实锤）。
     */
    private fun appFilterMap(packContext: Context): Map<String, String> {
        appFilterCache[iconPackPkg]?.let { return it }
        return synchronized(appFilterCache) {
            appFilterCache.getOrPut(iconPackPkg) {
                val t0 = System.currentTimeMillis()
                val map = parseAppFilter(packContext)
                map
            }
        }
    }

    /** 解析 appfilter.xml → component 全名 → drawable 名 */
    private fun parseAppFilter(packContext: Context): Map<String, String> {
        val map = mutableMapOf<String, String>()
        runCatching {
            val parser = android.util.Xml.newPullParser()
            parser.setInput(packContext.assets.open("appfilter.xml"), null)
            var event = parser.eventType
            while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (event == org.xmlpull.v1.XmlPullParser.START_TAG &&
                    parser.name == "item"
                ) {
                    val component = parser.getAttributeValue(null, "component")
                    val drawable = parser.getAttributeValue(null, "drawable")
                    if (component != null && drawable != null) {
                        // ComponentInfo{com.pkg/com.pkg.Activity} → 提取内部全名
                        val inner = component.substringAfter("{").substringBefore("}")
                        map[inner] = drawable
                        map[component.substringAfter("{").substringBefore("/")] = drawable
                    }
                }
                event = parser.next()
            }
        }
        return map
    }

    /** 系统默认图标（回退，统一缩放防滑动卡顿） */
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
        return bitmap?.let { scaleIcon(it) }?.asImageBitmap() ?: fallbackIcon()
    }

    /** 兜底占位图标（浅色圆块） */
    private fun fallbackIcon(): ImageBitmap {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xFFD8E4F1.toInt())
        return bitmap.asImageBitmap()
    }


    /**
     * createPackageContext 标志：Android 11+ 用 CONTEXT_RESTRICTED
     * （允许读受限包 assets/资源，但不能加载代码——CONTEXT_INCLUDE_CODE
     * 从 11 起对普通应用禁止，实锤 SecurityException）。
     * Android 10 无此常量，传 0（无限制）。
     */
    private fun restrictedFlags(): Int =
        if (android.os.Build.VERSION.SDK_INT >= 30) Context.CONTEXT_RESTRICTED else 0

        const val ACTION_INSTALL_ICON_PACK = "com.android.launcher.action.INSTALL_ICON_PACK"
    const val ACTION_RESOLVE_ICON = "com.android.launcher.action.RESOLVE_ICON"
    const val EXTRA_PACKAGE = "package"
    const val EXTRA_COMPONENT = "component"
    const val EXTRA_ICON = "icon"
}
