package com.androsaver

import com.androsaver.visualizer.VertexBatchStorage
import org.junit.Assert.*
import org.junit.Test

class VertexBatchStorageTest {
    @Test fun storageAllocatesLazilyAndKeepsTheStagingOutOfTheManagedHeap() {
        val storage = VertexBatchStorage(4, 6)
        assertEquals(0, storage.allocatedBytes)
        val upload = storage.upload
        assertEquals(96, storage.allocatedBytes)
        upload.put(0, 0.25f)
        assertEquals(0.25f, upload.get(0), 0f)
        assertEquals(96, storage.allocatedBytes)
        assertSame(upload, storage.upload)
    }

    @Test fun theStagingBudgetIsNativeMemoryOnly() {
        val storage = VertexBatchStorage(262144, 6)
        assertEquals(6 * 1024 * 1024, storage.capacityBytes)
        assertEquals(0, storage.allocatedBytes)
    }

    @Test fun snapshotReadsBackTheStagedVertices() {
        val storage = VertexBatchStorage(4, 6)
        val upload = storage.upload
        upload.put(0, 1f); upload.put(1, 2f); upload.put(6, 7f)
        val values = storage.snapshot(12)
        assertEquals(12, values.size)
        assertEquals(1f, values[0], 0f)
        assertEquals(2f, values[1], 0f)
        assertEquals(0f, values[2], 0f)
        assertEquals(7f, values[6], 0f)
    }
}
