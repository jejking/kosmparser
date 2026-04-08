package com.jejking.kosmparser.osm.pbf

import com.google.protobuf.ByteString
import crosby.binary.BlobKt
import crosby.binary.blob
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BlobDecoderTest : FunSpec({

    val payload = "hello PBF world".toByteArray(Charsets.UTF_8)

    test("decompresses raw blob") {
        val rawBlob = blob {
            raw = ByteString.copyFrom(payload)
            rawSize = payload.size
        }
        rawBlob.decompress() shouldBe payload
    }

    test("decompresses zlib_data blob") {
        val compressed = PbfTestUtils.zlibCompress(payload)
        val zlibBlob = blob {
            zlibData = ByteString.copyFrom(compressed)
            rawSize = payload.size
        }
        zlibBlob.decompress() shouldBe payload
    }

    test("throws IllegalStateException when rawSize does not match decompressed size") {
        val compressed = PbfTestUtils.zlibCompress(payload)
        val mismatchedBlob = blob {
            zlibData = ByteString.copyFrom(compressed)
            rawSize = payload.size + 99  // wrong size
        }
        shouldThrow<IllegalStateException> {
            mismatchedBlob.decompress()
        }
    }

    test("throws UnsupportedOperationException for unsupported compression") {
        // A blob with no recognised data field triggers the else branch
        val emptyBlob = blob {}
        shouldThrow<UnsupportedOperationException> {
            emptyBlob.decompress()
        }
    }
})
