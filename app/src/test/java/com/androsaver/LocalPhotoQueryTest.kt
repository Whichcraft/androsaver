package com.androsaver

import com.androsaver.source.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class LocalPhotoQueryTest {
    @Test fun providerFailuresKeepTheOldSnapshotInsteadOfPublishingAnEmptyLibrary() = runBlocking {
        for (query in listOf<suspend () -> List<ImageItem>?>(
            { null }, { throw IllegalStateException("Provider died") }, { throw java.io.IOException("Read interrupted") }
        )) {
            val state = SourceSnapshots()
            state.update("photos", ImageSourceResult.Success(listOf(ImageItem("old-photo"))))
            val result = imageSourceResult { ImageSourceResult.Success(localPhotoQuery(query)) }
            state.update("photos", result)
            assertEquals(listOf("old-photo"), state.items().map { it.url })
            assertTrue(result is ImageSourceResult.Failure)
        }
        assertTrue(localPhotoQuery<String> { emptyList() }.isEmpty())
    }
    @Test fun permissionAndCancellationStayDistinct() = runBlocking {
        val result = imageSourceResult { localPhotoQuery<String> { throw SecurityException("denied") }; ImageSourceResult.Empty }
        assertEquals(ImageSourceResult.Failure(ImageSourceResult.FailureKind.PERMISSION), result)
        try { localPhotoQuery<String> { throw CancellationException("stop") }; fail("Cancellation swallowed") }
        catch (_: CancellationException) { }
    }
}
