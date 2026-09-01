package com.nbljsbdk.snowhide.service

import com.nbljsbdk.snowhide.core.model.AppTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentTaskSnapshotProviderTest {

    @Test
    fun parsesUserIdFromRecentTaskAndKeepsCloneIdentity() {
        val primary = AppTarget.create("com.example.same", 0).getOrThrow()
        val clone = AppTarget.create("com.example.same", 999).getOrThrow()
        val output = """
            ACTIVITY MANAGER RECENT TASKS
              Recent tasks:
              * Recent #0: Task{abc #1 type=standard A=10001:com.example.same}
                userId=999 effectiveUid=u999a1
                mActivityComponent=com.example.same/.MainActivity
              * Recent #1: Task{def #2 type=standard A=10001:com.example.same}
                userId=0 effectiveUid=u0a1
                mActivityComponent=com.example.same/.MainActivity
        """.trimIndent()

        assertEquals(
            setOf(primary, clone),
            RecentTaskSnapshotProvider.parseOutput(
                output = output,
                candidates = setOf(primary, clone),
                ownPackage = "com.nbljsbdk.snowhide",
            ),
        )
    }

    @Test
    fun missingUserIdDoesNotGuessCloneWhenPackageIsAmbiguous() {
        val primary = AppTarget.create("com.example.same", 0).getOrThrow()
        val clone = AppTarget.create("com.example.same", 999).getOrThrow()
        val output = """
            * Recent #0: Task{abc #1 type=standard A=10001:com.example.same}
              mActivityComponent=com.example.same/.MainActivity
        """.trimIndent()

        assertEquals(
            emptySet<AppTarget>(),
            RecentTaskSnapshotProvider.parseOutput(
                output = output,
                candidates = setOf(primary, clone),
                ownPackage = "com.nbljsbdk.snowhide",
            ),
        )
    }
}
