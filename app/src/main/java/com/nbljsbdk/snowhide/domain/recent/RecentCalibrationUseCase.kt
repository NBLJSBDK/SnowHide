package com.nbljsbdk.snowhide.domain.recent

import com.nbljsbdk.snowhide.data.repo.RecentCalibrationRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * Recent 校准数据门面；设置页不直接拥有 Repository。
 */
class RecentCalibrationUseCase(
    private val repository: RecentCalibrationRepository,
) {
    val packages: StateFlow<List<String>> = repository.packages

    fun clear() {
        repository.clear()
    }
}
