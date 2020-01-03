package com.jejking.kosmparser.io

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.channels.CompletionHandler
import kotlinx.coroutines.async
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

// mostly from https://github.com/Kotlin/coroutines-examples/blob/master/examples/io/io.kt

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

fun AsynchronousFileChannel.asFlow(bufferSize: Int = 256): Flow<ByteArray> = flow {
    var offset = 0L
    val buffer = ByteBuffer.allocate(bufferSize)
    var bytesRead = aRead(offset, buffer)
    do {
        val targetArray = ByteArray(bytesRead)
        buffer.rewind()
        buffer.get(targetArray)
        emit(targetArray)
        offset += bytesRead
        buffer.clear()
        bytesRead = aRead(offset, buffer)
    } while (bytesRead != -1)
}