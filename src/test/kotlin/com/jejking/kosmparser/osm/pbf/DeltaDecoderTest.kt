package com.jejking.kosmparser.osm.pbf

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

class DeltaDecoderTest : FunSpec({

    context("decodeDelta") {
        test("empty list returns empty list") {
            decodeDelta(emptyList()) shouldBe emptyList()
        }

        test("single-element list returns that element") {
            decodeDelta(listOf(42L)) shouldBe listOf(42L)
        }

        test("decodes two consecutive elements") {
            // deltas: 10, 5 → absolute: 10, 15
            decodeDelta(listOf(10L, 5L)) shouldBe listOf(10L, 15L)
        }

        test("handles negative deltas") {
            // deltas: 100, -30, -20 → absolute: 100, 70, 50
            decodeDelta(listOf(100L, -30L, -20L)) shouldBe listOf(100L, 70L, 50L)
        }

        test("handles large node IDs typical in OSM data") {
            val base = 7_000_000_000L
            decodeDelta(listOf(base, 1L, 1L)) shouldBe listOf(base, base + 1, base + 2)
        }

        test("property: round-trip with prefix sums") {
            // If we encode absolute values as deltas, decode should recover originals.
            // i.e. decodeDelta(toDeltaList(originals)) == originals
            checkAll(Arb.list(Arb.long(-1_000_000L, 1_000_000L), 1..100)) { absolutes ->
                // encode as deltas: first element as-is, subsequent as differences
                val deltas = absolutes.zipWithNext { a, b -> b - a }
                    .let { listOf(absolutes.first()) + it }
                decodeDelta(deltas) shouldBe absolutes
            }
        }
    }

    context("decodeCoordinate") {
        test("zero encoded value returns offset contribution only") {
            decodeCoordinate(0L, 0L, 100) shouldBe 0.0
        }

        test("decodes lat using default granularity and zero offset") {
            // lat=531234500 * 1e-9 * 100 rounded to 7 decimal places = 53.12345
            val encoded = 531234500L
            decodeCoordinate(encoded, 0L, 100) shouldBe 53.12345
        }

        test("applies offset correctly") {
            // offset=1_000_000_000 (1 degree), encoded=0, granularity=100 → 1.0
            decodeCoordinate(0L, 1_000_000_000L, 100) shouldBe 1.0
        }

        test("property: granularity scales linearly") {
            checkAll(Arb.long(-900_000_000L, 900_000_000L)) { encoded ->
                val gran100 = decodeCoordinate(encoded, 0L, 100)
                val gran200 = decodeCoordinate(encoded, 0L, 200)
                // doubling granularity should double the coordinate (within floating-point tolerance)
                (gran200 / gran100).let { ratio ->
                    if (encoded != 0L) ratio shouldBe 2.0
                }
            }
        }
    }

    context("decodeTimestamp") {
        test("epoch zero returns 1970-01-01T00:00:00Z") {
            decodeTimestamp(0L, 1000) shouldBe
                ZonedDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC)
        }

        test("decodes Unix timestamp with default dateGranularity 1000ms") {
            // 2014-05-14T14:12:39Z = 1400076759 seconds = 1400076759000 ms
            // encoded = 1400076759 (seconds), dateGranularity = 1000
            val encoded = 1400076759L
            val expected = ZonedDateTime.of(2014, 5, 14, 14, 12, 39, 0, ZoneOffset.UTC)
            decodeTimestamp(encoded, 1000) shouldBe expected
        }

        test("respects non-default dateGranularity") {
            // 1 millisecond = encoded=1 with dateGranularity=1
            decodeTimestamp(1L, 1) shouldBe
                ZonedDateTime.ofInstant(Instant.ofEpochMilli(1L), ZoneOffset.UTC)
        }
    }
})
