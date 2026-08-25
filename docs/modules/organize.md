# 整理目录模块

> 整理目录是主屏覆盖式编辑模式，不跳转到独立页面。本文记录最终确认的状态机、键位语义、即时应用规则和实现边界。

## 1. 模块边界

- 入口：主屏齿轮菜单中的「整理目录」。
- 主屏进入：保持主屏宫格显示，不自动高亮任何对象。
- 文件夹页进入：回到主屏，并自动高亮刚才所在的文件夹。
- 整理期间：Pager 锁定主屏，底部显示整理操作区。
- 所有结构操作即时写入 `GridRepository` 和 SharedPreferences。
- 「确认」和 Android 返回键只负责提交当前名称并退出，不提供取消或回滚。
- 文件夹不能嵌套。

相关实现：

- 状态模型：`app/src/main/java/com/nbljsbdk/snowhide/domain/organize/OrganizeState.kt`
- 状态转移：`app/src/main/java/com/nbljsbdk/snowhide/domain/organize/OrganizeReducer.kt`
- 页面业务：`app/src/main/java/com/nbljsbdk/snowhide/feature/home/organize/OrganizeViewModel.kt`
- 操作区 UI：`app/src/main/java/com/nbljsbdk/snowhide/feature/home/organize/OrganizeOverlay.kt`
- 结构持久化：`app/src/main/java/com/nbljsbdk/snowhide/data/repo/GridRepository.kt`

最终决策来源：OpenCode 主会话 `ses_006078d2fffemcjHHFyFvG2HY7`，2026-08-20 17:19 后的即时应用版本。

## 2. 状态模型

记号：

- `A`：主屏应用。
- `F`：主屏文件夹。
- `M`：当前文件夹内的应用。
- `focus`：左右键当前操作焦点。

### 2.1 逻辑状态

| 状态 | 含义 | 左右键焦点 | 上下键 |
|---|---|---|---|
| `None` | 没有任何高亮 | 无 | 灰显 |
| `HomeApp(A)` | 只高亮主屏应用 | `A` | 灰显 |
| `Folder(F)` | 只高亮文件夹 | `F` | 灰显 |
| `AppToFolder(A,F)` | 主屏应用和目标文件夹双高亮 | 最近点击的对象 | 下：`A` 加入 `F` |
| `FolderMember(F,M)` | 文件夹和内部应用双高亮 | `M` | 上：`M` 移回主屏 |
| `CreatingFolder(F)` | 刚创建的文件夹正在改名 | `F` | 按当前选择处理 |

### 2.2 Kotlin 状态表示

当前代码使用三个顶层状态表达上述逻辑状态：

- `OrganizeState.Empty` 对应 `None`。
- `OrganizeState.HomeAppSelected` 对应 `HomeApp(A)`。
- `OrganizeState.FolderSelected` 携带以下字段表达其余状态：
  - `folderId`、`folderNameInput`：当前文件夹和名称输入。
  - `subHomeApp`：双高亮中的主屏应用 `A`。
  - `subFolderAppPkg`：双高亮中的内部应用 `M`。
  - `focus`：`FOLDER`、`HOME_APP` 或 `FOLDER_APP`。
  - `justCreated`：新建文件夹后的临时输入状态，不是独立业务状态。

## 3. 状态转移

| 当前状态 | 操作 | 结果 |
|---|---|---|
| `None` | 点击主屏应用 `A` | 进入 `HomeApp(A)` |
| `None` | 点击文件夹 `F` | 进入 `Folder(F)` |
| `None` | 点击 `+` | 新建文件夹，进入 `CreatingFolder(F)` |
| `HomeApp(A)` | 点击另一个主屏应用 `A2` | 替换为 `HomeApp(A2)` |
| `HomeApp(A)` | 点击文件夹 `F` | 保留 `A`，形成 `AppToFolder(A,F)`，文件夹成为最近焦点 |
| `HomeApp(A)` | 左/右 | `A` 在主屏混排区域内线性移位 |
| `Folder(F)` | 点击主屏应用 `A` | 形成 `AppToFolder(A,F)`，应用成为最近焦点 |
| `Folder(F)` | 点击另一个文件夹 `F2` | 切换到 `Folder(F2)`，不形成嵌套 |
| `Folder(F)` | 点击内部应用 `M` | 进入 `FolderMember(F,M)` |
| `Folder(F)` | 左/右 | `F` 在主屏混排区域内线性移位 |
| `AppToFolder(A,F)` | 左/右 | 移动最近点击的对象；焦点是 `A` 就移动 `A`，焦点是 `F` 就移动 `F` |
| `AppToFolder(A,F)` | 点击下 | `A` 加入 `F` 末尾，进入 `FolderMember(F,A)` |
| `FolderMember(F,M)` | 点击另一个主屏应用 `A` | 替换为 `AppToFolder(A,F)` |
| `FolderMember(F,M)` | 点击另一个内部应用 `M2` | 替换为 `FolderMember(F,M2)` |
| `FolderMember(F,M)` | 左/右 | `M` 在当前文件夹内部线性移位 |
| `FolderMember(F,M)` | 点击上 | `M` 移回主屏末尾，焦点回到文件夹 |
| 任意状态 | 点击 `+` | 新建文件夹并自动选中，新文件夹名称进入编辑状态 |
| `Folder(F)` 或含 `F` 的双高亮状态 | 点击 `-` | 直接删除 `F`，成员按原目录顺序续补到主屏末尾，回到 `None` |
| 任意整理状态 | 点击确认或按返回键 | 提交非空文件夹名并退出整理 |

双高亮规则：

- 先点 `A` 再点 `F` 不丢失 `A`。
- 先点 `F` 再点 `A` 同样形成双高亮。
- 双高亮时最近点击对象获得左右键焦点，另一个对象只保留跨区转移关系。
- 再点当前目标文件夹时，文件夹成为左右键焦点，不产生文件夹嵌套。

## 4. 键位和按钮

操作行最终顺序：`上`、`下`、`左`、`右`、`+`、`-`。

| 操作 | 启用条件 | 行为 |
|---|---|---|
| 上 | 选中内部应用 `M` | `M` 移回主屏末尾 |
| 下 | 选中主屏应用 `A` 且存在目标文件夹 `F` | `A` 加入 `F` 末尾，并自动高亮加入后的 `M` |
| 左/右 | 存在主屏应用、文件夹或内部应用焦点 | 在所属区域内移动，不循环；到边界时对应方向灰显 |
| `+` | 始终可用 | 新建文件夹并选中 |
| `-` | 选中文件夹 | 直接删除文件夹，不二次确认 |

高亮区分：

- 主屏应用 `A`：冰蓝色。
- 主屏文件夹 `F`：紫色。
- 文件夹内应用 `M`：暖橙色。
- 高亮统一使用约 50% 透明度。

## 5. 名称编辑

- 选中文件夹时显示名称输入行和内部应用横排。
- 新建文件夹后自动聚焦名称并全选原名称，方便直接按删除键清空。
- 普通点击文件夹不自动弹出输入法。
- 点击名称栏时聚焦并全选当前名称。
- IME Done、顶部确认或退出整理时提交名称。
- 空名称不写入仓库。

## 6. 数据和副作用

- 主屏应用/文件夹顺序由 `GridItem.sortOrder` 维护。
- 文件夹顺序由 `Folder.sortOrder` 维护。
- 文件夹内应用顺序由 `FolderApp.sortOrder` 维护。
- 左右移动、上下转移、新建、删除和改名均立即 `persist()`。
- 删除文件夹不删除应用数据，只改变宫格归属并把成员补回主屏。
- `OrganizeReducer` 只负责纯状态转移；`OrganizeViewModel` 调用 `GridRepository` 执行结构操作。

## 7. 相关边界

- 整理模式只编辑主屏结构，不打开文件夹全屏页。
- 整理模式期间主屏 Pager 不响应左右滑动。
- 整理模式下不使用普通文件夹长按菜单。
- 整理目录的左右排序取代拖拽排序。
- 最新需求要求新建文件夹追加到当前最后一个文件夹之后；如果实现代码仍把新文件夹放到首位，应按该需求修正并同步本文档。
