package com.nbljsbdk.snowhide.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nbljsbdk.snowhide.domain.backup.BackupUseCase

/**
 * 备份页面业务边界。
 *
 * SAF Uri、输出流和重启确认留在 SettingsScreen；JSON 校验、SP 写入和结果语义
 * 统一交给 [BackupUseCase]。
 */
class BackupViewModel(
    private val useCase: BackupUseCase,
) : ViewModel() {

    fun export(scope: BackupUseCase.Scope): Result<String> = useCase.export(scope)

    fun import(json: String): Result<BackupUseCase.ImportResult> = useCase.import(json)

    class Factory(private val useCase: BackupUseCase) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BackupViewModel::class.java)) {
                return BackupViewModel(useCase) as T
            }
            error("未知 ViewModel：${modelClass.name}")
        }
    }
}
