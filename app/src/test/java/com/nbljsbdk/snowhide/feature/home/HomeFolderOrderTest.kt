package com.nbljsbdk.snowhide.feature.home

import com.nbljsbdk.snowhide.data.model.GridItem
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeFolderOrderTest {

    @Test
    fun projectsOnlyFoldersInDisplayedHomeOrder() {
        val items = listOf(
            GridItem(id = 10L, type = "app", pkg = "com.example.app", sortOrder = 0),
            GridItem(id = 11L, type = "folder", folderId = 2L, sortOrder = 1),
            GridItem(id = 12L, type = "folder", folderId = 1L, sortOrder = 3),
        )

        assertEquals(listOf(2L, 1L), folderIdsInHomeOrder(items))
    }
}
