package com.androsaver.visualizer

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * GL-thread-owned staging shared by mutually exclusive triangle/line batches.
 *
 * The staging lives entirely in native memory: a direct buffer is allocated
 * outside the managed heap, so the visualizer's 16 MiB heap budget stays
 * available for the effects themselves. Vertices are written straight into
 * the buffer, which also removes the per-frame copy a mirrored heap array
 * would need.
 */
internal class VertexBatchStorage(vertices: Int, stride: Int) {
    init { require(vertices > 0 && stride > 0 && vertices.toLong() * stride * 8 <= Int.MAX_VALUE) }
    private val floats = vertices * stride
    private val bytes = floats * 4
    val capacityBytes: Int = bytes
    private val direct = lazy { ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder()).asFloatBuffer() }
    val upload by direct
    val allocatedBytes: Int get() = if (direct.isInitialized()) bytes else 0

    /** Read back the first [count] staged floats — inspection and tests only. */
    fun snapshot(count: Int): FloatArray {
        val values = FloatArray(count.coerceIn(0, floats))
        val buffer = upload
        for (i in values.indices) values[i] = buffer.get(i)
        return values
    }
}
