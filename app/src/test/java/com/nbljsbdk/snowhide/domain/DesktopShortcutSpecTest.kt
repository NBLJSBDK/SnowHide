package com.nbljsbdk.snowhide.domain

import com.nbljsbdk.snowhide.core.model.AppTarget
import com.nbljsbdk.snowhide.domain.shortcut.DesktopShortcutSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopShortcutSpecTest {

    @Test
    fun samePackageInDifferentUsersGetsDifferentShortcutIds() {
        val primary = AppTarget.create("com.example.app", 0).getOrThrow()
        val clone = AppTarget.create("com.example.app", 999).getOrThrow()

        assertNotEquals(
            DesktopShortcutSpec.shortcutId(primary),
            DesktopShortcutSpec.shortcutId(clone),
        )
        assertTrue(DesktopShortcutSpec.shortcutId(clone).contains("999"))
    }

    @Test
    fun cloneLabelIncludesUserAndFitsShortLabelLimit() {
        val clone = AppTarget.create("com.example.app", 999).getOrThrow()

        val label = DesktopShortcutSpec.shortLabel(clone, "这是一个很长的应用名称用于验证截断")

        assertTrue(label.contains("分身999"))
        assertTrue(label.length <= 25)
    }

    @Test
    fun primaryLabelRemainsUnmodified() {
        val primary = AppTarget.create("com.example.app", 0).getOrThrow()

        assertEquals("示例应用", DesktopShortcutSpec.shortLabel(primary, "示例应用"))
    }
}
