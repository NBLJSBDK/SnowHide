package com.nbljsbdk.snowhide.data.repo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 批量操作进度共享单例（停用/启用全部、神之一手、快速启停、智能清理）
 *
 * 多入口（主屏、增删界面、About 页）批量操作时，各页面订阅进度
 * 显示进度条；null = 空闲。
 */
object BatchProgress {

    /** 进度：null=空闲；否则 0f..1f（当前完成/总数） */
    private val _progress = MutableStateFlow<Float?>(null)
    val progress: StateFlow<Float?> = _progress.asStateFlow()

    /** 当前操作文案（如「正在停用 40/100」） */
    private val _label = MutableStateFlow<String?>(null)
    val label: StateFlow<String?> = _label.asStateFlow()

    /** 批量是否进行中（防重复点击） */
    val active: Boolean get() = _progress.value != null

    /** 当前操作动词（update 组装文案用） */
    private var currentVerb: String = ""

    /** 开始批量：总目标数 + 动作动词（如「停用」） */
    fun begin(total: Int, verb: String) {
        currentVerb = verb
        _label.value = "$verb: 0/$total"
        _progress.value = 0f
    }

    /** 更新已完成数量（完成 n/total，进度条平滑 +1，文案「停用: 45/130」） */
    fun update(done: Int, total: Int) {
        _label.value = "$currentVerb: $done/$total"
        _progress.value = if (total <= 0) 1f else (done.toFloat() / total).coerceIn(0f, 1f)
    }

    /** 结束（清空） */
    fun end() {
        _progress.value = null
        _label.value = null
    }
}
