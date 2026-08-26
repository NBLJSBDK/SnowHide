package com.nbljsbdk.snowhide.domain

import com.nbljsbdk.snowhide.domain.folder.FolderPage
import com.nbljsbdk.snowhide.domain.folder.FolderPageInput
import com.nbljsbdk.snowhide.domain.folder.FolderPagePlanner
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderPagePlannerTest {

    @Test
    fun sortsFoldersAndExcludesConfiguredPages() {
        val plan = FolderPagePlanner.plan(
            folders = listOf(
                FolderPageInput(id = 8L, sortOrder = 2),
                FolderPageInput(id = 3L, sortOrder = 1),
                FolderPageInput(id = 5L, sortOrder = 1),
            ),
            excludedFolderIds = setOf(5L),
        )

        assertEquals(
            listOf(FolderPage.Home, FolderPage.Folder(3L), FolderPage.Folder(8L)),
            plan.pages,
        )
        assertEquals(2, plan.logicalIndex(2))
        assertEquals(0, plan.logicalIndex(3))
        assertEquals(1, plan.targetVirtualPage(4, 3L) % plan.pageCount)
    }

    @Test
    fun homeGridFolderOrderOverridesFolderMetadataOrder() {
        val plan = FolderPagePlanner.plan(
            folders = listOf(
                FolderPageInput(id = 1L, sortOrder = 0),
                FolderPageInput(id = 2L, sortOrder = 1),
            ),
            homeFolderIds = listOf(2L, 1L),
        )

        assertEquals(
            listOf(FolderPage.Home, FolderPage.Folder(2L), FolderPage.Folder(1L)),
            plan.pages,
        )
    }

    @Test
    fun nonLoopingMoveStopsAtEdges() {
        val plan = FolderPagePlanner.plan(
            folders = listOf(FolderPageInput(1L, 0)),
            loopEnabled = false,
        )
        assertEquals(0, plan.move(0, -1))
        assertEquals(1, plan.move(0, 1))
        assertEquals(1, plan.move(1, 1))
    }
}
