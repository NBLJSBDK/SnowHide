package com.nbljsbdk.snowhide.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nbljsbdk.snowhide.core.model.AppTarget
import org.json.JSONArray
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
        QuickToggleRepository.replaceTargetMembers(emptyList())
        QuickToggleRepository.replaceMembers(
            listOf("com.example.one", "com.example.one", "bad;package", "com.example.two"),
        )
        val clone = target("com.example.two", 999)
        QuickToggleRepository.addMember(clone)
        QuickToggleRepository.setOpened(
            listOf(target("com.example.two"), target("com.example.two"), clone),
        )

        assertEquals(
            listOf(target("com.example.one"), target("com.example.two"), clone),
            QuickToggleRepository.members.value,
        )
        assertEquals(listOf(target("com.example.two"), clone), QuickToggleRepository.opened.value)

        QuickToggleRepository.clearOpened()
        QuickToggleRepository.replaceMembers(emptyList())
        assertEquals(emptyList<AppTarget>(), QuickToggleRepository.opened.value)
        assertEquals(emptyList<AppTarget>(), QuickToggleRepository.members.value)
    }

    @Test
    fun targetMemberUsesObjectFormatAndReadsBackAsSameIdentity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        QuickToggleRepository.init(context)
        val clone = target("com.example.clone", 999)

        QuickToggleRepository.replaceTargetMembers(listOf(clone))
        QuickToggleRepository.setOpened(listOf(clone))

        val settings = context.getSharedPreferences("snowhide_settings", Context.MODE_PRIVATE)
        val saved = JSONArray(settings.getString("quick_toggle_members", "[]"))
            .getJSONObject(0)
        assertEquals("com.example.clone", saved.getString("pkg"))
        assertEquals(999, saved.getInt("userId"))
        assertEquals(listOf(clone), QuickToggleRepository.members.value)
        assertEquals(listOf(clone), QuickToggleRepository.opened.value)

        QuickToggleRepository.clearOpened()
        QuickToggleRepository.replaceTargetMembers(emptyList())
    }

    private fun target(pkg: String, userId: Int = AppTarget.PRIMARY_USER_ID): AppTarget =
        AppTarget.create(pkg, userId).getOrThrow()
}
