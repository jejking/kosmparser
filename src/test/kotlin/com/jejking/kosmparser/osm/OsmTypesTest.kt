package com.jejking.kosmparser.osm

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arb
import io.kotest.property.arbitrary.numericDoubles
import io.kotest.property.forAll
import java.time.ZonedDateTime

class OsmTypesTest : FunSpec() {

  private val elementMetadata = ElementMetadata(1, "user", 1, ZonedDateTime.now(), true, 1, 1)
  private val tags = mapOf("foo" to "bar")

  init {
    context("point") {
      test("should accept lat values between -90 and 90 and long between -180 and 180") {
        val pointArb: Arb<Point> = arb(edgecases = listOf(Point(0.0, 0.0), Point(-90.0, -180.0), Point(90.0, 180.0))) { rs ->
          val latitudes = Arb.numericDoubles(-90.0, 90.0).values(rs)
          val longitudes = Arb.numericDoubles(-180.0, 180.0).values(rs)
          latitudes.zip(longitudes).map { (latitude, longitude) -> Point(latitude.value, longitude.value) }
        }

        forAll(pointArb) { p: Point -> LAT_RANGE.contains(p.lat) && LON_RANGE.contains(p.lon) }
      }
      test("should accept lat value of -90") {
        Point(-90.0, 0.0)
      }
      test("should accept lat value of 90") {
        Point(90.0, 0.0)
      }
      test("should accept long value of -180") {
        Point(0.0, -180.0)
      }
      test("should accept long value of 180") {
        Point(0.0, 180.0)
      }
      test("should reject lat value smaller than -90") {
        shouldThrow<IllegalStateException> {
          Point(-90.1, 0.0)
        }
      }
      test("should reject lat value greater than 90") {
        shouldThrow<IllegalStateException> {
          Point(90.1, 0.0)
        }
      }
      test("should reject long value smaller than -180") {
        shouldThrow<IllegalStateException> {
          Point(0.0, -180.1)
        }
      }
      test("should reject long value greater than 180") {
        shouldThrow<IllegalStateException> {
          Point(0.0, 180.1)
        }
      }
    }

    context("way") {

      test("should accept list of 2 nodes") {
        val way = Way(elementMetadata, tags, listOf(1, 2))
        way.isFaulty() shouldBe false
        way.isClosed() shouldBe false
      }

      test("should accept list of 2000 nodes") {
        val way = Way(elementMetadata, tags, (1..2000L).toList())
        way.isFaulty() shouldBe false
      }

      test("should reject list of nodes with 2001 elements") {
        shouldThrow<IllegalStateException> {
          Way(elementMetadata, tags, (1..2001L).toList())
        }
      }

      test("should declare a list of 1 node refs to be faulty") {
        val way = Way(elementMetadata, tags, listOf(1))
        way.isFaulty() shouldBe true
      }

      test("should declare a list of 0 node refs to be faulty") {
        val way = Way(elementMetadata, tags, emptyList())
        way.isFaulty() shouldBe true
      }

      test("should identify a closed way") {
        val way = Way(elementMetadata, tags, listOf(1, 2, 1))
        way.isClosed() shouldBe true
      }

      test("should not identify a faulty way as closed") {
        val way = Way(elementMetadata, tags, listOf(1))
        way.isClosed() shouldBe false

        val way2 = Way(elementMetadata, tags, emptyList())
        way2.isClosed() shouldBe false
      }
    }
  }

}
