package com.androsaver

import com.androsaver.visualizer.VertexBatchStorage
import org.junit.Assert.*
import org.junit.Test

class VertexBatchStorageTest {
    @Test fun storageAllocatesLazilyAndReusesTheSameHeapAndUploadBuffers() {
        val storage = VertexBatchStorage(4, 6)
        assertEquals(0, storage.allocatedBytes)
        val vertices = storage.values
        assertEquals(96, storage.allocatedBytes)
        vertices[0] = 0.25f
        val upload = storage.upload
        upload.put(vertices)
        assertEquals(0.25f, upload.get(0), 0f)
        assertEquals(192, storage.allocatedBytes)
        assertSame(vertices, storage.values)
        assertSame(upload, storage.upload)
    }

    @Test fun unchangedVertexLimitUsesHalfThePreviousStagingBudget() {
        val storage = VertexBatchStorage(262144, 6)
        assertEquals(12 * 1024 * 1024, storage.capacityBytes)
        assertEquals(0, storage.allocatedBytes)
    }
}
