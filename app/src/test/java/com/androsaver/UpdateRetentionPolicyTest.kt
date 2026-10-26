package com.androsaver

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class UpdateRetentionPolicyTest {
    @get:Rule val temporary = TemporaryFolder()
    private val now = 200_000L
    private val policy = UpdateRetentionPolicy(maxBytes = 20, maxFiles = 2, protectedAgeMs = 1000)

    private fun artifact(name: String, bytes: Int, age: Long) = temporary.newFile(name).apply {
        writeBytes(ByteArray(bytes))
        assertTrue(setLastModified(now - age))
    }

    @Test fun oldestUnprotectedFilesAreRemovedToMeetCountAndSizeBudgets() {
        val old = artifact("old.apk", 10, 2000)
        val recent = artifact("recent.apk", 10, 100)
        policy.prune(temporary.root, File(temporary.root, "next.apk"), 10, null, now)
        assertFalse(old.exists())
        assertTrue(recent.exists())
    }

    @Test fun recentInstallerArtifactsAreProtectedAndNewDownloadIsRefused() = runBlocking {
        val one = artifact("one.apk", 10, 100)
        val two = artifact("two.apk", 10, 100)
        var downloads = 0
        val outcome = runCatching {
            UpdateDownloadFiles.prepareBounded(temporary.root, "next.apk", 10, null,
                validate = {}, download = { downloads++ }, retention = policy, now = { now })
        }
        assertTrue(outcome.exceptionOrNull() is IOException)
        assertEquals(0, downloads)
        assertTrue(one.exists())
        assertTrue(two.exists())
    }

    @Test fun alreadyInstalledVersionsAreObsoleteEvenWhenRecentlyStaged() {
        val old = artifact("androsaver-10-${"a".repeat(64)}.apk", 10, 100)
        policy.prune(temporary.root, File(temporary.root, "next.apk"), 10, 10, now)
        assertFalse(old.exists())
    }

    @Test fun activeTemporaryFileSurvivesEvenWhenItsTimestampIsOld() {
        val active = artifact("download-active.part", 10, 2000)
        val abandoned = artifact("download-abandoned.part", 10, 2000)
        policy.prune(temporary.root, File(temporary.root, "next.apk"), 10, null, now, activeTemporary = active)
        assertTrue(active.exists())
        assertFalse(abandoned.exists())
    }
}
