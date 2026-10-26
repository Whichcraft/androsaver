package com.androsaver

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

class StaticImageFilesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun importsUseDifferentCacheKeysAndPreserveThePreviousFile() = runBlocking {
        var selected: String? = null
        suspend fun select(text: String): String? = StaticImageFiles.importImage(
            temporary.root, { selected }, { ByteArrayInputStream(text.toByteArray()) },
            { it.length() > 0 }, { selected = it.absolutePath; true })
        val first = select("first")!!
        val second = select("second")!!
        assertNotEquals(first, second)
        assertEquals("first", File(first).readText())
        assertEquals("second", File(second).readText())
        val third = select("third")!!
        assertEquals(third, selected)
        assertFalse(File(first).exists())
        assertTrue(File(second).isFile)
    }

    @Test fun invalidImagesAndFailedPublicationLeaveSelectionIntact() = runBlocking {
        val previous = temporary.newFile("background_image").apply { writeText("working") }
        listOf(false, true).forEach { valid ->
            val result = StaticImageFiles.importImage(temporary.root, { previous.absolutePath },
                { ByteArrayInputStream("new".toByteArray()) }, { valid }, { false })
            assertNull(result)
            assertEquals("working", previous.readText())
            assertEquals(listOf(previous), temporary.root.listFiles()!!.toList())
        }
    }

    @Test fun earlierInterruptedReplacementCanRecoverItsBackup() = runBlocking {
        val previous = File(temporary.root, "background_image")
        temporary.newFile("background_image.previous").writeText("working")
        assertEquals(previous, StaticImageFiles.selectedFile(temporary.root, previous.absolutePath))
        assertEquals("working", previous.readText())
    }
}
