package com.jejking.kosmparser.io

import io.kotlintest.shouldBe
import io.kotlintest.specs.FunSpec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

@ExperimentalCoroutinesApi
class AsynchronousFileChannelFlowTest: FunSpec() {

    val path = Paths.get(this.javaClass.getResource("/testfile1.bin").toURI())

    init {
        context("coroutine to read buffer") {
            test("should read in 256 bytes of zeroes") {

                val channel = openFileChannel()
                val buffer = ByteBuffer.allocate(256)
                val bytesRead = async { channel.aRead(0, buffer) }

                bytesRead.await() shouldBe 256
                buffer.array() shouldBe ByteArray(256)
            }

            test("should read in 128 bytes of fives") {
                val channel = openFileChannel()
                val buffer = ByteBuffer.allocate(256)
                val bytesRead = async { channel.aRead(256 * 5, buffer) }

                bytesRead.await() shouldBe 128

                val expectedBytes = ByteArray(256)
                expectedBytes.fill(5, 0, 128)
                buffer.array() shouldBe expectedBytes
            }

            test("should return -1 when attempting to read past end of file") {
                val channel = openFileChannel()
                val buffer = ByteBuffer.allocate(256)
                val bytesRead = async { channel.aRead((256 * 5) + 128, buffer) }

                bytesRead.await() shouldBe -1
            }
        }

        context("flow of byte array") {
            test("should stream six blocks of byte array") {
                val channel = openFileChannel()
                runBlocking { channel.asFlow().count() shouldBe 6 }
            }

            test("first five blocks should be of size 256 bytes") {
                val channel = openFileChannel()
                runBlocking { channel.asFlow().take(5).collect { value -> value.size shouldBe 256 } }
            }

            test("last block should be of size 128 bytes") {
                val channel = openFileChannel()
                runBlocking { channel.asFlow().drop(5).collect { value -> value.size shouldBe 128 } }
            }
        }
    }

    private fun openFileChannel() =
            autoClose(AsynchronousFileChannel.open(path, StandardOpenOption.READ))
}