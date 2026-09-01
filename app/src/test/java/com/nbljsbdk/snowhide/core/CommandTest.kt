package com.nbljsbdk.snowhide.core

import com.nbljsbdk.snowhide.core.model.PackageName
import com.nbljsbdk.snowhide.core.operation.PmCommand
import com.nbljsbdk.snowhide.core.operation.PmOperation
import com.nbljsbdk.snowhide.core.operation.PmQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandTest {

    @Test
    fun packageNameRejectsShellCharacters() {
        assertTrue(PackageName.isValid("com.example.app_2"))
        assertFalse(PackageName.isValid(""))
        assertFalse(PackageName.isValid("com.example.app\nwhoami"))
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
        assertEquals(
            "pm disable --user 0 com.example.app",
            PmCommand.build(PmOperation.DISABLE, "com.example.app").getOrThrow(),
        )
        assertEquals(
            "pm enable --user 0 com.example.app",
            PmCommand.build(PmOperation.ENABLE, "com.example.app").getOrThrow(),
        )
        assertEquals(
            "pm enable --user 10 com.example.app",
            PmCommand.build(PmOperation.ENABLE_USER, "com.example.app", userId = 10).getOrThrow(),
        )
        assertEquals(
            "pm list packages --user 10 -d",
            PmQuery.listPackages(10, frozenOnly = true).getOrThrow(),
        )
        assertEquals(
            "pm list packages --user 10 -s",
            PmQuery.listPackages(10, systemOnly = true).getOrThrow(),
        )
        assertEquals("pm list users", PmQuery.listUsers())
        assertTrue(PmCommand.build(PmOperation.ENABLE, "bad;command").isFailure)
        assertTrue(PmCommand.build(PmOperation.DISABLE, "com.example.app", userId = -1).isFailure)
        assertTrue(PmCommand.build(PmOperation.ENABLE, "com.example.app", userId = 10).isFailure)
        assertTrue(PmCommand.build(PmOperation.DISABLE, "com.example.app", userId = 10).isFailure)
        assertTrue(PmQuery.listPackages(-1).isFailure)
        assertTrue(PmQuery.listPackages(10, frozenOnly = true, systemOnly = true).isFailure)
    }
}
