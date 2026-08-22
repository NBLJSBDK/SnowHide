package com.nbljsbdk.snowhide.feature.about

/** 内置更新记录；版本号与正式 tag 保持同一语义版本。 */
data class VersionRecord(
    val version: String,
    val title: String,
    val details: String,
)

val versionHistory = listOf(
    VersionRecord(
        "v0.2.1",
        "Recent 即时停用修复",
        "修复进入 Recent 建立基线期间误停用全部应用；改为任务快照确认后逐个即时停用，不触发主界面批量进度条。增加服务重连补执行队列、雪藏自身排除和 ColorOS 窗口兜底；停用提示显示为“应用名称已被划卡停用”。",
    ),
    VersionRecord(
        "v0.2.0",
        "Recent 划卡停用",
        "增加无障碍 Recent 识别、自动与手动校准，以及退出 Recent 后冻结；通过 Shizuku 静默读取真实任务包名，支持 ColorOS 同名卡片并优化滑动时序。仅处理已添加且未锁定的应用，不删除应用数据。",
    ),
    VersionRecord(
        "v0.1.7",
        "版本记录与关于应用",
        "关于页显示语义版本和编译时间，增加从 v0.1.0 开始的更新说明。",
    ),
    VersionRecord(
        "v0.1.6",
        "应用列表管理",
        "完善应用列表管理、启动容错和系统应用解锁流程。",
    ),
    VersionRecord(
        "v0.1.5",
        "锁屏清理与震动",
        "增加锁屏自动清理、锁定应用豁免和全局震动反馈。",
    ),
    VersionRecord(
        "v0.1.4",
        "备份与快捷方式",
        "增加数据备份导入导出和动态快捷方式执行反馈。",
    ),
    VersionRecord(
        "v0.1.3",
        "整理目录状态机",
        "完善文件夹整理、跨区移动和即时应用规则。",
    ),
    VersionRecord(
        "v0.1.2",
        "冻结状态同步",
        "统一冻结状态刷新、应用图标状态和批量操作反馈。",
    ),
    VersionRecord(
        "v0.1.1",
        "基础界面",
        "完善主屏宫格、文件夹和 Shizuku 引导。",
    ),
    VersionRecord(
        "v0.1.0",
        "初始版本",
        "完成 Shizuku 冻结、解冻和基础应用管理能力。",
    ),
)
