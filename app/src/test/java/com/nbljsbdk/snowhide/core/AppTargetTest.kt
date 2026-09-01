package com.nbljsbdk.snowhide.core

import com.nbljsbdk.snowhide.core.model.AppTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppTargetTest {

    @Test
    fun targetKeepsPackageAndUserAsOneValue() {
        val target = AppTarget.create("com.example.clone", 10).getOrThrow()

        assertEquals("com.example.clone", target.packageName.value)
        assertEquals(10, target.userId)
    }

    @Test
    fun targetRejectsNegativeUserAndShellPackage() {
        assertTrue(AppTarget.create("com.example.clone", -1).isFailure)
        assertTrue(AppTarget.create("com.example;rm", 10).isFailure)
    }
}
