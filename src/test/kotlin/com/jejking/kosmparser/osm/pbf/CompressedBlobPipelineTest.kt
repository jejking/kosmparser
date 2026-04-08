package com.jejking.kosmparser.osm.pbf

import com.google.protobuf.ByteString
import com.jejking.kosmparser.osm.Member
import com.jejking.kosmparser.osm.MemberType
import com.jejking.kosmparser.osm.Node
import com.jejking.kosmparser.osm.OsmMetadata
import com.jejking.kosmparser.osm.Relation
import com.jejking.kosmparser.osm.Way
import com.jejking.kosmparser.osm.pbf.PbfFlowMapper.toOsmDataFlow
import crosby.binary.Fileformat
import crosby.binary.Osmformat
import crosby.binary.blob
import crosby.binary.denseNodes
import crosby.binary.headerBlock
import crosby.binary.primitiveBlock
import crosby.binary.primitiveGroup
import crosby.binary.relation
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
import java.nio.file.Path

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

    /** Wraps [protoBytes] in a zlib-compressed [Fileformat.Blob]. */
    fun compressedBlob(protoBytes: ByteArray): Fileformat.Blob = blob {
        zlibData = ByteString.copyFrom(PbfTestUtils.zlibCompress(protoBytes))
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

    /**
     * Writes [stream] to a temp PBF file, passes its [Path] to [block], then deletes the file.
     */
    fun withTempPbf(stream: InputStream, block: (Path) -> Unit) {
        val tmpFile = Files.createTempFile("kosmparser-test-", ".osm.pbf")
        try {
            tmpFile.toFile().outputStream().use { out -> stream.copyTo(out) }
            block(tmpFile)
        } finally {
            Files.deleteIfExists(tmpFile)
        }
    }

    context("direct blob reading") {

        test("OSMHeader compressed blob is read with correct type and decompresses to OsmMetadata") {
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
    }

    context("full pipeline with zlib-compressed blobs") {

        test("compressed header + compressed OSMData → OsmMetadata + Nodes") {
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

            withTempPbf(stream) { tmpFile ->
                val result = runBlocking { tmpFile.toOsmDataFlow().toList() }
                result shouldHaveSize 3  // OsmMetadata + 2 Nodes
                result[0].shouldBeInstanceOf<OsmMetadata>()
                val node1 = result[1] as Node
                val node2 = result[2] as Node
                node1.elementMetadata.id shouldBe 1L
                node1.point.lat shouldBe 53.12345
                node1.point.lon shouldBe 10.2345
                node2.elementMetadata.id shouldBe 2L
                node2.point.lat shouldBe 53.12345
                node2.point.lon shouldBe 10.2345
            }
        }

        test("compressed OSMData with Way is decoded correctly") {
            val header = headerBlock {
                requiredFeatures += "OsmSchema-V0.6"
                requiredFeatures += "DenseNodes"
            }
            val block = primitiveBlock {
                stringtable = stringTable {
                    s += ByteString.copyFromUtf8("")           // index 0: empty
                    s += ByteString.copyFromUtf8("highway")   // index 1
                    s += ByteString.copyFromUtf8("residential") // index 2
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
            withTempPbf(stream) { tmpFile ->
                val result = runBlocking { tmpFile.toOsmDataFlow().toList() }
                result shouldHaveSize 2  // OsmMetadata + Way
                val w = result[1] as Way
                w.elementMetadata.id shouldBe 10L
                w.tags shouldBe mapOf("highway" to "residential")
                w.nds shouldBe listOf(100L, 101L)
            }
        }

        test("compressed OSMData with Relation is decoded correctly") {
            val header = headerBlock {
                requiredFeatures += "OsmSchema-V0.6"
                requiredFeatures += "DenseNodes"
            }
            val block = primitiveBlock {
                stringtable = stringTable {
                    s += ByteString.copyFromUtf8("")              // 0: empty
                    s += ByteString.copyFromUtf8("type")          // 1
                    s += ByteString.copyFromUtf8("multipolygon")  // 2
                    s += ByteString.copyFromUtf8("outer")         // 3
                    s += ByteString.copyFromUtf8("inner")         // 4
                }
                granularity = 100
                latOffset = 0
                lonOffset = 0
                dateGranularity = 1000
                primitivegroup += primitiveGroup {
                    relations += relation {
                        id = 42L
                        keys += 1
                        vals += 2
                        rolesSid += 3   // "outer"
                        rolesSid += 4   // "inner"
                        memids += 100L  // delta: abs IDs = [100, 101]
                        memids += 1L
                        types += Osmformat.Relation.MemberType.WAY
                        types += Osmformat.Relation.MemberType.WAY
                    }
                }
            }
            val stream = buildPbfStream(
                "OSMHeader" to compressedBlob(header.toByteArray()),
                "OSMData" to compressedBlob(block.toByteArray())
            )
            withTempPbf(stream) { tmpFile ->
                val result = runBlocking { tmpFile.toOsmDataFlow().toList() }
                result shouldHaveSize 2  // OsmMetadata + Relation
                val rel = result[1] as Relation
                rel.elementMetadata.id shouldBe 42L
                rel.tags shouldBe mapOf("type" to "multipolygon")
                rel.members shouldBe listOf(
                    Member(MemberType.WAY, 100L, "outer"),
                    Member(MemberType.WAY, 101L, "inner")
                )
            }
        }

        test("multiple OSMData blobs in one stream are all decoded") {
            val header = headerBlock {
                requiredFeatures += "OsmSchema-V0.6"
                requiredFeatures += "DenseNodes"
            }
            fun nodeBlock(nodeId: Long) = primitiveBlock {
                stringtable = stringTable { s += ByteString.copyFromUtf8("") }
                granularity = 100
                latOffset = 0
                lonOffset = 0
                dateGranularity = 1000
                primitivegroup += primitiveGroup {
                    dense = denseNodes {
                        id += nodeId
                        lat += 0L
                        lon += 0L
                    }
                }
            }
            val stream = buildPbfStream(
                "OSMHeader" to compressedBlob(header.toByteArray()),
                "OSMData" to compressedBlob(nodeBlock(1L).toByteArray()),
                "OSMData" to compressedBlob(nodeBlock(2L).toByteArray())
            )
            withTempPbf(stream) { tmpFile ->
                val result = runBlocking { tmpFile.toOsmDataFlow().toList() }
                result shouldHaveSize 3  // OsmMetadata + Node(1) + Node(2)
                result[0].shouldBeInstanceOf<OsmMetadata>()
                (result[1] as Node).elementMetadata.id shouldBe 1L
                (result[2] as Node).elementMetadata.id shouldBe 2L
            }
        }

        test("HasSorting required feature is accepted with compressed blob") {
            val header = headerBlock {
                requiredFeatures += "OsmSchema-V0.6"
                requiredFeatures += "DenseNodes"
                requiredFeatures += "HasSorting"
            }
            val stream = buildPbfStream("OSMHeader" to compressedBlob(header.toByteArray()))
            withTempPbf(stream) { tmpFile ->
                val result = runBlocking { tmpFile.toOsmDataFlow().toList() }
                result shouldHaveSize 1
                result[0].shouldBeInstanceOf<OsmMetadata>()
            }
        }
    }

    context("BlobReader error cases") {

        test("truncated blob body throws IllegalStateException with clear message") {
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
