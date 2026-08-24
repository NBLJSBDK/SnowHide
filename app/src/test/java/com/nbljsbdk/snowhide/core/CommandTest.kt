package com.nbljsbdk.snowhide.core

import com.nbljsbdk.snowhide.core.model.PackageName
import com.nbljsbdk.snowhide.core.operation.PmCommand
import com.nbljsbdk.snowhide.core.operation.PmOperation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandTest {

    @Test
    fun packageNameRejectsShellCharacters() {
        assertTrue(PackageName.isValid("com.example.app_2"))
        assertFalse(PackageName.isValid("com.example;rm"))
        assertFalse(PackageName.isValid("com.example app"))
        assertTrue(PackageName.parse("com.example.app").isSuccess)
    }

    @Test
    fun pmCommandKeepsControlledFormats() {
        assertEquals(
            "pm disable-user --user 0 com.example.app",
            PmCommand.build(PmOperation.DISABLE_USER, "com.example.app").getOrThrow(),
        )
        assertEquals(
            "pm uninstall --user 10 com.example.app",
            PmCommand.build(PmOperation.UNINSTALL, "com.example.app", userId = 10).getOrThrow(),
        )
        assertTrue(PmCommand.build(PmOperation.ENABLE, "bad;command").isFailure)
        assertTrue(PmCommand.build(PmOperation.DISABLE, "com.example.app", userId = -1).isFailure)
    }
}
