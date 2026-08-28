package com.hark.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mirrors web/src/sync/merge.selfcheck.ts — the data-loss-critical merge must behave
 *  identically on both platforms. Pure Kotlin (no org.json), runs on the JVM. */
class SyncMergeTest {
    private fun note(uid: String, updatedAt: Long, deleted: Boolean = false, title: String = "") =
        SyncNote(uid, title, "", null, "TYPED", false, false, 0, updatedAt, deleted)

    @Test fun newerRemoteWins() {
        val m = mergeRecords(listOf(note("a", 10, title = "local")), listOf(note("a", 20, title = "remote")))
        assertEquals(1, m.size)
        assertEquals("remote", m[0].title)
    }

    @Test fun olderRemoteLoses() {
        val m = mergeRecords(listOf(note("a", 30, title = "local")), listOf(note("a", 20, title = "remote")))
        assertEquals("local", m[0].title)
    }

    @Test fun newerDeletePropagates() {
        val m = mergeRecords(listOf(note("a", 10, deleted = false)), listOf(note("a", 20, deleted = true)))
        assertTrue(m[0].deleted)
    }

    @Test fun newerEditBeatsOlderDelete() {
        val m = mergeRecords(listOf(note("a", 30, deleted = false)), listOf(note("a", 20, deleted = true)))
        assertFalse(m[0].deleted)
    }

    @Test fun unionOfBothSides() {
        val m = mergeRecords(listOf(note("a", 10)), listOf(note("b", 10)))
        assertEquals(2, m.size)
    }

    @Test fun idempotent() {
        val remote = listOf(note("a", 20, title = "y"))
        val once = mergeRecords(listOf(note("a", 10, title = "x")), remote)
        val twice = mergeRecords(once, remote)
        assertEquals(1, twice.size)
        assertEquals("y", twice[0].title)
    }
}
