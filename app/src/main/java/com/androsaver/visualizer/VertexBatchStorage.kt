package com.androsaver.visualizer

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** GL-thread-owned staging shared by mutually exclusive triangle/line batches. */
internal class VertexBatchStorage(vertices: Int, stride: Int) {
    init { require(vertices > 0 && stride > 0 && vertices.toLong() * stride * 8 <= Int.MAX_VALUE) }
    private val floats = vertices * stride
    private val bytes = floats * 4
    val capacityBytes: Int = bytes * 2
    private val heap = lazy { FloatArray(floats) }
    private val direct = lazy { ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder()).asFloatBuffer() }
    val values by heap
    val upload by direct
    val allocatedBytes: Int get() = (if (heap.isInitialized()) bytes else 0) +
        (if (direct.isInitialized()) bytes else 0)
}
