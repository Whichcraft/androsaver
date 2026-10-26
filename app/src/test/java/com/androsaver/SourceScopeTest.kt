package com.androsaver

import com.androsaver.source.*
import org.junit.Assert.*
import org.junit.Test

class SourceScopeTest {
    @Test fun identicalPathsDoNotCollideAcrossAccountsServersOrFolders() {
        val item = ImageItem("https://photos.invalid/file", stableId = "dropbox:/photos/image.jpg")
        val a = SourceScope.create("dropbox", "account-one", listOf("folder"))
        val b = SourceScope.create("dropbox", "account-two", listOf("folder"))
        assertNotEquals(SourceScope.bind(item, a).stableId, SourceScope.bind(item, b).stableId)
        assertNotEquals(SourceScope.create("synology", "identity", listOf("server-a", "folder")),
            SourceScope.create("synology", "identity", listOf("server-b", "folder")))
        assertNotEquals(a, SourceScope.create("dropbox", "account-one", listOf("another-folder")))
        assertFalse(a.contains("folder"))
        assertFalse(SourceScope.eligible(null, setOf(a)))
        assertFalse(SourceScope.eligible(a, emptySet()))
        assertTrue(SourceScope.eligible(a, setOf(a)))
    }
}
