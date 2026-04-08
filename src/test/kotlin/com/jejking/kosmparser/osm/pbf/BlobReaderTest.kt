package com.jejking.kosmparser.osm.pbf

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class BlobReaderTest : FunSpec({

    test("empty stream produces empty sequence") {
        val result = ByteArray(0).inputStream().readBlobSequence().toList()
        result shouldBe emptyList()
    }

    test("truncated header throws IllegalStateException with clear message") {
        // Write a 4-byte big-endian header size (e.g. 100) but no header bytes follow
        val truncated = java.io.ByteArrayOutputStream().also {
            java.io.DataOutputStream(it).writeInt(100)
        }.toByteArray()

        shouldThrow<IllegalStateException> {
            truncated.inputStream().readBlobSequence().toList()
        }.message shouldContain "Truncated PBF"
    }
})
