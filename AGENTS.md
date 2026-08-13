# AGENTS.md — SnowHide（冻结工具 / 暂定名「雪藏」）

> **一句话**：Android 应用冻结器——用 Shizuku 把应用「冻结」（`pm disable-user`：图标消失+进程全断+断自启），
> 一键解冻秒恢复。**除了用户明确选择的「移除并卸载」，绝不删除任何应用数据。**

**设计文档**（唯一事实来源，所有 UI/功能/架构决策全在这）：
`~/opencode_dev/冻结工具设计.md` —— 新对话开工前先读它。

---

## 当前状态（2026-08-13）

- **工程已建**：`/mnt/d/GitHub/Android/SnowHide`（AS 创建，Empty Compose 模板）
  - 包名 `com.nbljsbdk.snowhide`、minSdk 29、targetSdk 36、AGP 9.1.1 + Kotlin 2.2.10 + Compose BOM 2024.09.00
  - `local.properties` 已有（含 MediaSync 同款签名配置 + sdk.dir）
  - `build_debug.bat` / `build_release.bat` 已建（WSL 用 `cmd.exe /c` 编译）
- **代码状态**：还是 AS 模板壳，未开始填模块（MainActivity/theme 是模板代码）
- **已完成**：调研（黑白门 1.0.5/1.0.9/美化版/3.3.3/AppGate 4.3.8 全反编译）+ 完整设计文档定稿
- **下一步**：按设计文档 §9 搭 `core/data/domain/feature` 骨架 + 移植 FontAwesome 图标（附录 A）

## 铁律（继承 MediaSync，不可违）

1. **git**：未经用户明确命令，绝不 commit/push/amend/force-push
2. **versionCode 永远 = 1**（覆盖安装），versionName = 构建日期 YYMMDD；debug 包 `.debug` 后缀 + 应用名加 D
3. **adb**：未经用户许可不操作其手机；装 APK 由用户自己来
4. **文件安全**：只动包管理器状态；卸载（移除并卸载）是唯一特例，须用户明确选择
5. **用中文沟通**；代码注释风格参考 MediaSync（中文 KDoc）

## 编译（WSL）

```bash
/mnt/c/Windows/System32/cmd.exe /c "cd /d D:\GitHub\Android\SnowHide && build_debug.bat"
# 输出 app/build/outputs/apk/debug/app-debug.apk
```

## 关键决策速查（详见设计文档）

- **P0**：Shizuku + 冻结单模式 + 混排宫格 + 全屏文件夹（二级）+ 整理目录 + 底部图标栏 + 增加应用界面 + 图标包 + 布局设置
- **引擎架构**：`PowerEngine` 接口 + impl 隔离区 + 注册表驱动 UI 渲染（将来必重构引擎，接口=宪法）
- **UI 解耦**：Composable 无状态、视觉细节收 ui/ 组件层、颜色走 theme（方便后面慢慢优化颜值）
- **应用桥**：预留 feature/bridge/ 目录，现在不实现
- **图标**：FontAwesome 5.15.4 → VectorDrawable（素材 /home/wywy521/temp/fontawesome-free-5.15.4-desktop.zip）
- **依赖**：Shizuku API + haze（毛玻璃）；不用 SaltUI（Hidden API 风险）
