package com.nbljsbdk.snowhide.core

import com.nbljsbdk.snowhide.core.operation.PmOutputParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PmOutputParserTest {

    @Test
    fun parsesOwnerAndManagedProfileWithoutLosingUserIdentity() {
        val users = PmOutputParser.users(
            """
            Users:
                UserInfo{0:Owner:c13} running
                UserInfo{10:Work profile:20} running
            """.trimIndent(),
        )

        assertEquals(listOf(0, 10), users.map { it.id })
        assertEquals("Work profile", users[1].name)
        assertTrue(users[1].isManagedProfile)
        assertTrue(users[1].running)
    }

    @Test
    fun parsesColorOsHexFlagsWithoutDroppingTheUser() {
        val users = PmOutputParser.users(
            "UserInfo{999:MultiApp:4001010} running",
        )

        assertEquals(listOf(999), users.map { it.id })
        assertEquals(0x4001010, users.single().flags)
        assertFalse(users.single().isManagedProfile)
    }

    @Test
    fun parsesUnsignedHighBitFlags() {
        val users = PmOutputParser.users("UserInfo{999:MultiApp:FFFFFFFF}")

        assertEquals(1, users.size)
        assertEquals(-1, users.single().flags)
    }

    @Test
    fun filtersInvalidAndDuplicatePackageLines() {
        val packages = PmOutputParser.packages(
            """
            package:com.example.two
            package:com.example.one
            package:com.example.one
            package:bad;command
            """.trimIndent(),
        )

        assertEquals(listOf("com.example.one", "com.example.two"), packages)
    }

    @Test
    fun runningFlagOnlyUsesTheStatusSuffix() {
        val users = PmOutputParser.users(
            """
            UserInfo{10:running profile:20}
            UserInfo{11:Work profile:20} running
            """.trimIndent(),
        )

        assertFalse(users.first { it.id == 10 }.running)
        assertTrue(users.first { it.id == 11 }.running)
    }
}
