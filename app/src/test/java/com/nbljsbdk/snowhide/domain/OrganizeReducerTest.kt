package com.nbljsbdk.snowhide.domain

import com.nbljsbdk.snowhide.domain.organize.OrganizeAppRef
import com.nbljsbdk.snowhide.domain.organize.OrganizeIntent
import com.nbljsbdk.snowhide.domain.organize.OrganizeReducer
import com.nbljsbdk.snowhide.domain.organize.OrganizeState
import com.nbljsbdk.snowhide.domain.organize.SelectionFocus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrganizeReducerTest {

    @Test
    fun folderSelectionKeepsHomeAppAndMovesFocus() {
        val app = OrganizeAppRef(7L, "com.example.app")
        var state: OrganizeState = OrganizeState.Empty
        state = OrganizeReducer.reduce(state, OrganizeIntent.TapHomeApp(app))
        state = OrganizeReducer.reduce(state, OrganizeIntent.TapFolder(9L, "工具"))

        val folder = state as OrganizeState.FolderSelected
        assertEquals(app, folder.subHomeApp)
        assertEquals(SelectionFocus.FOLDER, folder.focus)

        state = OrganizeReducer.reduce(state, OrganizeIntent.MoveDownCompleted(app.pkg))
        val moved = state as OrganizeState.FolderSelected
        assertNull(moved.subHomeApp)
        assertEquals(app.pkg, moved.subFolderAppPkg)
        assertEquals(SelectionFocus.FOLDER_APP, moved.focus)
    }

    @Test
    fun emptyAndFinishTransitionsClearSelection() {
        val created = OrganizeReducer.reduce(
            OrganizeState.Empty,
            OrganizeIntent.FolderCreated(3L, "文件夹1"),
        ) as OrganizeState.FolderSelected
        assertEquals(true, created.justCreated)

        val afterDelete = OrganizeReducer.reduce(created, OrganizeIntent.FolderDeleted)
        assertEquals(OrganizeState.Empty, afterDelete)
    }
}
