package com.androsaver

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class UpdateDownloadFilesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun cancelledAttemptCannotDeleteTheNextAttemptsFile() = runBlocking {
        withTimeout(2000) {
            val started = CompletableDeferred<File>()
            val first = launch {
                UpdateDownloadFiles.prepare(temporary.root, "verified.apk", {}, { part ->
                    part.writeText("partial")
                    started.complete(part)
                    awaitCancellation()
                })
            }
            val firstPart = started.await()
            first.cancel()
            var secondPart: File? = null
            val second = async {
                UpdateDownloadFiles.prepare(temporary.root, "verified.apk",
                    validate = { check(it.readText() == "complete") },
                    download = { part -> secondPart = part; part.writeText("complete") })
            }
            first.join()
            val artifact = second.await()
            assertNotEquals(firstPart, secondPart)
            assertFalse(firstPart.exists())
            assertEquals("complete", artifact.readText())
            assertFalse(temporary.root.listFiles()!!.any { it.extension == "part" })
        }
    }

    @Test fun verifiedArtifactsAreReusedButInvalidCachedFilesAreReplaced() = runBlocking {
        var downloads = 0
        val validate: suspend (File) -> Unit = {
            if (it.readText() != "verified") throw IOException("Invalid test artifact")
        }
        suspend fun prepare() = UpdateDownloadFiles.prepare(temporary.root, "verified.apk", validate) {
            downloads++
            it.writeText("verified")
        }
        val artifact = prepare()
        assertEquals(artifact, prepare())
        assertEquals(1, downloads)
        artifact.writeText("damaged")
        assertEquals("verified", prepare().readText())
        assertEquals(2, downloads)
    }
}
