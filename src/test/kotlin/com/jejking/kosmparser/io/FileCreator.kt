package com.jejking.kosmparser.io

import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.channels.CompletionHandler
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Helper file to write a predictable binary file for testing purposes.
 */
object FileCreator {

  const val BUFFER_SIZE = 256

  @JvmStatic
  fun main(args: Array<String>) {
    val path = Paths.get(this.javaClass.getResource("/testfile1.bin")!!.toURI())
    val channel = AsynchronousFileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
    val buffer = ByteBuffer.allocate(BUFFER_SIZE)

    // write four discrete full buffers of 0, 1, 2, 3, and 4
    for (i in 0..4) {
      fillBuffer(buffer, i.toByte())
      runBlocking { writeBuffer(i * BUFFER_SIZE, buffer, channel) }
    }
    val buffer2 = ByteBuffer.allocate(128)
    fillBuffer(buffer2, 5)
    runBlocking { writeBuffer(BUFFER_SIZE * 5, buffer2, channel) }
    channel.close()
    println("done")
  }

  private suspend fun writeBuffer(offset: Int, byteBuffer: ByteBuffer, channel: AsynchronousFileChannel) {
    byteBuffer.flip()
    suspendCoroutine<Int> { cont ->
      channel.write(
        byteBuffer, offset.toLong(), Unit,
        object : CompletionHandler<Int, Unit> {
          override fun completed(bytesRead: Int, attachment: Unit) {
            byteBuffer.flip()
            cont.resume(bytesRead)
          }

          override fun failed(exception: Throwable, attachment: Unit) {
            cont.resumeWithException(exception)
          }
        }
      )
    }
  }

  private fun fillBuffer(byteBuffer: ByteBuffer, b: Byte) {
    byteBuffer.clear()
    while (byteBuffer.hasRemaining()) {
      byteBuffer.put(b)
    }
  }
}
