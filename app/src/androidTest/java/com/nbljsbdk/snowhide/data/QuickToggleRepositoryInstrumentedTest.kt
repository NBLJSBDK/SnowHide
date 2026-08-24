package com.nbljsbdk.snowhide.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nbljsbdk.snowhide.data.repo.QuickToggleRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickToggleRepositoryInstrumentedTest {

    @Test
    fun membersAndOpenedSnapshotAreDeduplicatedAndPersisted() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        QuickToggleRepository.init(context)
        QuickToggleRepository.replaceMembers(
            listOf("com.example.one", "com.example.one", "bad;package", "com.example.two"),
        )
        QuickToggleRepository.setOpened(listOf("com.example.two", "com.example.two"))

        assertEquals(listOf("com.example.one", "com.example.two"), QuickToggleRepository.members.value)
        assertEquals(listOf("com.example.two"), QuickToggleRepository.opened.value)

        QuickToggleRepository.clearOpened()
        QuickToggleRepository.replaceMembers(emptyList())
        assertEquals(emptyList<String>(), QuickToggleRepository.opened.value)
        assertEquals(emptyList<String>(), QuickToggleRepository.members.value)
    }
}
