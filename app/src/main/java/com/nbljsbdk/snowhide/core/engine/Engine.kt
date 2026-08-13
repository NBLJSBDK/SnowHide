package com.nbljsbdk.snowhide.core.engine

/**
 * 权限引擎接口（稳定层，定义后永不改签名）
 *
 * 每个引擎代表一种「特权身份」：Shizuku(shell) / Root / Device Owner。
 * 所有冻结/解冻等特权操作都通过本接口执行，UI 与业务层永远不直接
 * 接触任何具体引擎实现（依赖倒置，见设计文档 §9.4）。
 *
 * 新增引擎 = 新建一个 impl 文件 + 注册表加一行；重构引擎 = 只改 impl。
 */
interface PowerEngine {

    /** 引擎唯一标识："shizuku" / "root" / "do" */
    val id: String

    /** 展示名称（设置页引擎区块显示） */
    val displayName: String

    /** 探测该引擎当前是否可用（已授权/已激活） */
    fun isAvailable(): Boolean

    /** 以特权身份执行 shell 命令，返回标准输出 */
    suspend fun exec(cmd: String): Result<String>

    /** 停用应用（图标消失+进程全断），P0 唯一冻结模式 */
    suspend fun disableApp(pkg: String): Result<Unit>

    /** 启用应用（解冻，秒恢复） */
    suspend fun enableApp(pkg: String): Result<Unit>

    /** 查询应用当前是否处于停用状态 */
    suspend fun isFrozen(pkg: String): Result<Boolean>

    /** 一次查询全部已停用应用包名（UI 批量刷新冻结状态，默认实现返回失败） */
    suspend fun listFrozenPackages(): Result<List<String>> =
        Result.failure(NotImplementedError("$displayName 未实现批量查询"))

    /** P1：休眠（图标变灰）。未实现引擎返回失败，UI 按灰显规则隐藏该能力 */
    suspend fun suspendApp(pkg: String, suspend: Boolean): Result<Unit> =
        Result.failure(NotImplementedError("$displayName 未实现休眠"))

    /** P2：隐藏（像卸载）。未实现引擎返回失败 */
    suspend fun hideApp(pkg: String, hidden: Boolean): Result<Unit> =
        Result.failure(NotImplementedError("$displayName 未实现隐藏"))
}
