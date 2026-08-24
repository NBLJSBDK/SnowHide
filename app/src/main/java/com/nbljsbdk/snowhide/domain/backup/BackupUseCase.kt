package com.nbljsbdk.snowhide.domain.backup

import com.nbljsbdk.snowhide.data.repo.BackupRepository

/** SAF 之外的备份业务入口：决定导出范围并返回结构化导入结果。 */
class BackupUseCase(
    private val repository: BackupRepository,
) {

    enum class Scope {
        ALL,
        GRID,
        SETTINGS,
    }

    data class ImportResult(val importedKeys: Int)

    fun export(scope: Scope): Result<String> = runCatching {
        when (scope) {
            Scope.ALL -> repository.exportBackup()
            Scope.GRID -> repository.exportGrid()
            Scope.SETTINGS -> repository.exportSettings()
        }
    }

    fun import(json: String): Result<ImportResult> = runCatching {
        ImportResult(repository.importBackup(json))
    }
}
