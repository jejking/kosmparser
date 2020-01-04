package com.jejking.kosmparser.io

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.channels.CompletionHandler
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

// aRead() mostly from https://github.com/Kotlin/coroutines-examples/blob/master/examples/io/io.kt

/**
 * Does an asynchronous read from underlying file, wrapped in a continuation that provides
 * the [CompletionHandler] implementation.
 *
 * @param offset the offset in the file to read from. Must be positive. See [AsynchronousFileChannel.read].
 * @param buf the  byte buffer to read into
 */
suspend fun AsynchronousFileChannel.aRead(offset: Long, buf: ByteBuffer): Int =
    suspendCoroutine { cont ->
        read(buf, offset, Unit, object : CompletionHandler<Int, Unit> {
            override fun completed(bytesRead: Int, attachment: Unit) {
                cont.resume(bytesRead)
            }

            override fun failed(exception: Throwable, attachment: Unit) {
                cont.resumeWithException(exception)
            }
        })
    }

/**
 * Exposes the file as a [Flow], reading it from the start in chunks
 * corresponding to the supplied buffer size.
 *
 * @param bufferSize bufferSize to allocate, must be positive
 */
fun AsynchronousFileChannel.asFlow(bufferSize: Int = 1024): Flow<ByteArray> = flow {
    var offset = 0L
    val buffer = ByteBuffer.allocate(bufferSize)
    var bytesRead = aRead(offset, buffer)

    while (bytesRead != -1) {
        emit(readBufferToArray(buffer, bytesRead))
        offset += bytesRead
        bytesRead = aRead(offset, buffer)
    }
}

fun readBufferToArray(byteBuffer: ByteBuffer, bytesRead: Int): ByteArray {
    val targetArray = ByteArray(bytesRead)
    byteBuffer.rewind()
    byteBuffer.get(targetArray)
    byteBuffer.clear()
    return targetArray
}