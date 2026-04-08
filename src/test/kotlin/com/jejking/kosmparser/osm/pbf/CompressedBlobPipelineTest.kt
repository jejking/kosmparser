package com.jejking.kosmparser.osm.pbf

import com.google.protobuf.ByteString
import com.jejking.kosmparser.osm.Node
import com.jejking.kosmparser.osm.OsmMetadata
import com.jejking.kosmparser.osm.Way
import com.jejking.kosmparser.osm.pbf.PbfFlowMapper.toOsmDataFlow
import crosby.binary.Fileformat
import crosby.binary.Osmformat
import crosby.binary.blob
import crosby.binary.denseNodes
import crosby.binary.headerBlock
import crosby.binary.primitiveBlock
import crosby.binary.primitiveGroup
import crosby.binary.stringTable
import crosby.binary.way
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.util.zip.Deflater

/**
 * Integration tests for the full PBF pipeline with zlib-compressed blobs.
 *
 * These tests build in-memory PBF streams with realistically compressed blobs
 * and run them through the complete pipeline:
 * `InputStream → readBlobSequence → decompress → parse → OsmData`.
 *
 * This exercises all the code paths that real-world PBF files exercise,
 * including the inflate loop and rawSize assertion in [Fileformat.Blob.decompress].
 */
class CompressedBlobPipelineTest : FunSpec({

    /** Compresses [data] with zlib (same as Osmosis/standard PBF writers). */
    fun zlibCompress(data: ByteArray): ByteArray {
        val deflater = Deflater()
        deflater.setInput(data)
        deflater.finish()
        val output = ByteArray(data.size + 1024)
        val count = deflater.deflate(output)
        deflater.end()
        return output.copyOf(count)
    }

    /** Wraps [protoBytes] in a zlib-compressed [Fileformat.Blob]. */
    fun compressedBlob(protoBytes: ByteArray): Fileformat.Blob = blob {
        zlibData = ByteString.copyFrom(zlibCompress(protoBytes))
        rawSize = protoBytes.size
    }

    /**
     * Builds a minimal valid PBF [InputStream] containing the given typed blobs.
     * Each pair is (type, serialised Blob bytes).
     */
    fun buildPbfStream(vararg blobs: Pair<String, Fileformat.Blob>): InputStream {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        for ((type, b) in blobs) {
            val blobBytes = b.toByteArray()
            val header = Fileformat.BlobHeader.newBuilder()
                .setType(type)
                .setDatasize(blobBytes.size)
                .build()
            val headerBytes = header.toByteArray()
            dos.writeInt(headerBytes.size)
            dos.write(headerBytes)
            dos.write(blobBytes)
        }
        dos.flush()
        return baos.toByteArray().inputStream()
    }

    context("full pipeline with zlib-compressed blobs") {

        test("OSMHeader blob decompresses to OsmMetadata") {
            val header = headerBlock {
                requiredFeatures += "OsmSchema-V0.6"
                requiredFeatures += "DenseNodes"
                writingprogram = "test"
            }
            val stream = buildPbfStream("OSMHeader" to compressedBlob(header.toByteArray()))

            val blobs = stream.readBlobSequence().toList()
            blobs shouldHaveSize 1
            blobs[0].type shouldBe "OSMHeader"

            val meta = Osmformat.HeaderBlock.parseFrom(blobs[0].blob.decompress()).toOsmMetadata()
            meta.generator shouldBe "test"
        }

        test("full pipeline: compressed header + compressed OSMData → OsmMetadata + Nodes") {
            val header = headerBlock {
                requiredFeatures += "OsmSchema-V0.6"
                requiredFeatures += "DenseNodes"
            }
            // Two DenseNodes at well-known coordinates
            val block = primitiveBlock {
                stringtable = stringTable { s += ByteString.copyFromUtf8("") }
                granularity = 100
                latOffset = 0
                lonOffset = 0
                dateGranularity = 1000
                primitivegroup += primitiveGroup {
                    dense = denseNodes {
                        id += 1L
                        id += 1L        // delta: abs IDs = [1, 2]
                        lat += 531234500L
                        lat += 0L
                        lon += 102345000L
                        lon += 0L
                    }
                }
            }
            val stream = buildPbfStream(
                "OSMHeader" to compressedBlob(header.toByteArray()),
                "OSMData" to compressedBlob(block.toByteArray())
            )

            // Write to temp file so we can use Path.toOsmDataFlow()
            val tmpFile = Files.createTempFile("kosmparser-test-", ".osm.pbf")
            try {
                tmpFile.toFile().outputStream().use { out -> stream.copyTo(out) }

                val result = runBlocking { tmpFile.toOsmDataFlow().toList() }
                result shouldHaveSize 3  // OsmMetadata + 2 Nodes
                result[0].shouldBeInstanceOf<OsmMetadata>()
                val node1 = result[1] as Node
                val node2 = result[2] as Node
                node1.elementMetadata.id shouldBe 1L
                node2.elementMetadata.id shouldBe 2L
                node1.point.lat shouldBe 53.12345
                node1.point.lon shouldBe 10.2345
            } finally {
                Files.deleteIfExists(tmpFile)
            }
        }

        test("compressed OSMData with Way is decoded correctly") {
            val header = headerBlock {
                requiredFeatures += "OsmSchema-V0.6"
                requiredFeatures += "DenseNodes"
            }
            val block = primitiveBlock {
                stringtable = stringTable {
                    s += ByteString.copyFromUtf8("")    // index 0: empty
                    s += ByteString.copyFromUtf8("highway")  // index 1
                    s += ByteString.copyFromUtf8("residential")  // index 2
                }
                granularity = 100
                latOffset = 0
                lonOffset = 0
                dateGranularity = 1000
                primitivegroup += primitiveGroup {
                    ways += way {
                        id = 10L
                        keys += 1
                        vals += 2
                        refs += 100L
                        refs += 1L  // delta: abs node IDs = [100, 101]
                    }
                }
            }
            val stream = buildPbfStream(
                "OSMHeader" to compressedBlob(header.toByteArray()),
                "OSMData" to compressedBlob(block.toByteArray())
            )
            val tmpFile = Files.createTempFile("kosmparser-test-", ".osm.pbf")
            try {
                tmpFile.toFile().outputStream().use { out -> stream.copyTo(out) }
                val result = runBlocking { tmpFile.toOsmDataFlow().toList() }
                result shouldHaveSize 2  // OsmMetadata + Way
                val w = result[1] as Way
                w.elementMetadata.id shouldBe 10L
                w.tags shouldBe mapOf("highway" to "residential")
                w.nds shouldBe listOf(100L, 101L)
            } finally {
                Files.deleteIfExists(tmpFile)
            }
        }

        test("HasSorting required feature is accepted with compressed blob") {
            val header = headerBlock {
                requiredFeatures += "OsmSchema-V0.6"
                requiredFeatures += "DenseNodes"
                requiredFeatures += "HasSorting"
            }
            val stream = buildPbfStream("OSMHeader" to compressedBlob(header.toByteArray()))
            val tmpFile = Files.createTempFile("kosmparser-test-", ".osm.pbf")
            try {
                tmpFile.toFile().outputStream().use { out -> stream.copyTo(out) }
                val result = runBlocking { tmpFile.toOsmDataFlow().toList() }
                result shouldHaveSize 1
                result[0].shouldBeInstanceOf<OsmMetadata>()
            } finally {
                Files.deleteIfExists(tmpFile)
            }
        }
    }

    context("BlobReader error cases") {

        test("truncated blob body throws IllegalStateException with clear message") {
            // Write valid 4-byte header size + valid BlobHeader, but only 10 bytes of blob data
            // when header declares datasize = 100
            val blobHeader = Fileformat.BlobHeader.newBuilder()
                .setType("OSMData")
                .setDatasize(100)
                .build()
            val headerBytes = blobHeader.toByteArray()
            val baos = ByteArrayOutputStream()
            val dos = DataOutputStream(baos)
            dos.writeInt(headerBytes.size)
            dos.write(headerBytes)
            dos.write(ByteArray(10))  // only 10 bytes, but datasize says 100
            dos.flush()

            shouldThrow<IllegalStateException> {
                baos.toByteArray().inputStream().readBlobSequence().toList()
            }.message shouldContain "Truncated PBF"
        }
    }
})
