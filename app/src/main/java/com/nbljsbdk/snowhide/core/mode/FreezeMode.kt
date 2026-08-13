package com.nbljsbdk.snowhide.core.mode

/**
 * 冻结模式（扩展点）
 *
 * P0 只有 FREEZE 实现；新增模式 = 加枚举值 + 把 isImplemented 翻成 true。
 * UI 遍历本枚举渲染模式选项（注册表驱动，见设计文档 §9.4）。
 */
enum class FreezeMode(
    val label: String,
    val description: String,
    /** 是否已实现——未实现的模式在 UI 灰显「未开放」 */
    val isImplemented: Boolean,
) {
    /** 冻结：图标消失 + 进程全断（`pm disable-user`），P0 唯一模式 */
    FREEZE("冻结", "图标消失，进程、自启、通知全停", true),

    /** 休眠：图标变灰可见（`pm suspend`），P1 */
    SUSPEND("休眠", "图标变灰，点击提示已暂停", false),

    /** 禁用：桌面+设置都消失（`pm disable` 全局），P3 root */
    DISABLE("禁用", "桌面与设置中都不显示", false),

    /** 隐藏：像卸载（`pm hide` / `setApplicationHidden`），P2 */
    HIDE("隐藏", "像卸载一样消失", false),
    ;

    companion object {
        /** 已实现的模式列表（UI 可选项数据源） */
        fun implemented(): List<FreezeMode> = entries.filter { it.isImplemented }
    }
}
