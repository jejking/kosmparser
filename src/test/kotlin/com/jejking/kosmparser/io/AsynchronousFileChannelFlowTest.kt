package com.jejking.kosmparser.io

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

@ExperimentalCoroutinesApi
class AsynchronousFileChannelFlowTest : FunSpec() {

  val testFilePath = Paths.get(this.javaClass.getResource("/testfile1.bin").toURI())

  init {
    context("coroutine to read buffer") {
      test("should read in 256 bytes of zeroes") {

        val channel = openFileChannel(testFilePath)
        val buffer = ByteBuffer.allocate(256)
        val bytesRead = async { channel.aRead(0, buffer) }

        bytesRead.await() shouldBe 256
        buffer.array() shouldBe ByteArray(256)
      }

      test("should read in 128 bytes of fives") {
        val channel = openFileChannel(testFilePath)
        val buffer = ByteBuffer.allocate(256)
        val bytesRead = async { channel.aRead(256 * 5, buffer) }

        bytesRead.await() shouldBe 128

        val expectedBytes = ByteArray(256)
        expectedBytes.fill(5, 0, 128)
        buffer.array() shouldBe expectedBytes
      }

      test("should return -1 when attempting to read past end of file") {
        val channel = openFileChannel(testFilePath)
        val buffer = ByteBuffer.allocate(256)
        val bytesRead = async { channel.aRead((256 * 5) + 128, buffer) }

        bytesRead.await() shouldBe -1
      }
    }

    context("flow of byte array") {
      test("should stream six blocks of byte array") {
        val channel = openFileChannel(testFilePath)
        runBlocking { channel.asFlow(256).count() shouldBe 6 }
      }

      test("first five blocks should be of size 256 bytes") {
        val channel = openFileChannel(testFilePath)
        runBlocking { channel.asFlow(256).take(5).collect { value -> value.size shouldBe 256 } }
      }

      test("last block should be of size 128 bytes") {
        val channel = openFileChannel(testFilePath)
        runBlocking { channel.asFlow(256).drop(5).collect { value -> value.size shouldBe 128 } }
      }

      test("should deliver zero elements on reading empty file") {
        val emptyFilePath = Paths.get(this.javaClass.getResource("/emptyfile").toURI())
        val channel = openFileChannel(emptyFilePath)
        runBlocking { channel.asFlow(256).count() shouldBe 0 }
      }
    }
  }

  private fun openFileChannel(path: Path) =
    autoClose(AsynchronousFileChannel.open(path, StandardOpenOption.READ))
}
