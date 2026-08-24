package com.nbljsbdk.snowhide.domain.organize

/** 整理目录的纯状态转移意图。结构写入仍由 feature ViewModel 委托 Repository。 */
sealed interface OrganizeIntent {
    data class Enter(val folderId: Long?, val folderName: String? = null) : OrganizeIntent
    data class TapHomeApp(val app: OrganizeAppRef) : OrganizeIntent
    data class TapFolder(val folderId: Long, val folderName: String) : OrganizeIntent
    data class TapFolderApp(val pkg: String) : OrganizeIntent
    data class MoveDownCompleted(val pkg: String) : OrganizeIntent
    data object MoveUpCompleted : OrganizeIntent
    data class FolderCreated(val folderId: Long, val folderName: String) : OrganizeIntent
    data object FolderDeleted : OrganizeIntent
    data class FolderNameChanged(val name: String) : OrganizeIntent
}

/** 无副作用的整理目录状态机。 */
object OrganizeReducer {

    fun reduce(state: OrganizeState, intent: OrganizeIntent): OrganizeState = when (intent) {
        is OrganizeIntent.Enter -> intent.folderId?.let { id ->
            OrganizeState.FolderSelected(
                folderId = id,
                folderNameInput = intent.folderName.orEmpty(),
            )
        } ?: OrganizeState.Empty

        is OrganizeIntent.TapHomeApp -> when (state) {
            OrganizeState.Empty -> OrganizeState.HomeAppSelected(intent.app)
            is OrganizeState.HomeAppSelected -> OrganizeState.HomeAppSelected(intent.app)
            is OrganizeState.FolderSelected -> state.copy(
                subHomeApp = intent.app,
                subFolderAppPkg = null,
                focus = SelectionFocus.HOME_APP,
            )
        }

        is OrganizeIntent.TapFolder -> {
            val pairedApp = when (state) {
                is OrganizeState.HomeAppSelected -> state.app
                is OrganizeState.FolderSelected -> state.subHomeApp
                OrganizeState.Empty -> null
            }
            OrganizeState.FolderSelected(
                folderId = intent.folderId,
                folderNameInput = intent.folderName,
                subHomeApp = pairedApp,
                focus = SelectionFocus.FOLDER,
            )
        }

        is OrganizeIntent.TapFolderApp -> when (state) {
            is OrganizeState.FolderSelected -> state.copy(
                subFolderAppPkg = intent.pkg,
                subHomeApp = null,
                focus = SelectionFocus.FOLDER_APP,
            )
            else -> state
        }

        is OrganizeIntent.MoveDownCompleted -> when (state) {
            is OrganizeState.FolderSelected -> state.copy(
                subHomeApp = null,
                subFolderAppPkg = intent.pkg,
                focus = SelectionFocus.FOLDER_APP,
            )
            else -> state
        }

        OrganizeIntent.MoveUpCompleted -> when (state) {
            is OrganizeState.FolderSelected -> state.copy(
                subFolderAppPkg = null,
                focus = SelectionFocus.FOLDER,
            )
            else -> state
        }

        is OrganizeIntent.FolderCreated -> OrganizeState.FolderSelected(
            folderId = intent.folderId,
            folderNameInput = intent.folderName,
            focus = SelectionFocus.FOLDER,
            justCreated = true,
        )

        OrganizeIntent.FolderDeleted -> OrganizeState.Empty

        is OrganizeIntent.FolderNameChanged -> when (state) {
            is OrganizeState.FolderSelected -> state.copy(folderNameInput = intent.name)
            else -> state
        }
    }
}
