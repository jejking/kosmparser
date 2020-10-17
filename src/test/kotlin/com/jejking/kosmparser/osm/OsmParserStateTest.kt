package com.jejking.kosmparser.osm

import com.jejking.kosmparser.xml.EndElement
import com.jejking.kosmparser.xml.StartDocument
import com.jejking.kosmparser.xml.StartElement
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import java.time.Month
import java.time.ZoneOffset
import java.time.ZonedDateTime

class OsmParserStateTest : FunSpec() {

  init {
    context("reading osm metadata") {
      test("should ignore StartDocument") {
        val startDocument = StartDocument("", "UTF-8", false)
        ReadingOsmMetadata.accept(startDocument) shouldBe (ReadingOsmMetadata to null)
      }

      test("should consume osm start element and hand over to readingBounds wtih attributes passed on") {
        val osmStartElement = StartElement("osm", mapOf("version" to "0.6", "generator" to "manual"))
        val (parserState, osmdata) = ReadingOsmMetadata.accept(osmStartElement)
        val readingBounds = parserState as ReadingBounds
        readingBounds.apiVersion shouldBe "0.6"
        readingBounds.generator shouldBe "manual"
        osmdata shouldBe null
      }

      test("should consume osm end element") {
        val osmEndElement = EndElement("osm")
        ReadingOsmMetadata.accept(osmEndElement) shouldBe (ReadingOsmMetadata to null)
      }

      test("should not throw exception if cannot find api version attribute in osm start element") {
        val osmStartElement = StartElement("osm", mapOf("generator" to "manual"))
        val (parserState, _) = ReadingOsmMetadata.accept(osmStartElement)
        val readingBounds = parserState as ReadingBounds
        readingBounds.apiVersion shouldBe null
        readingBounds.generator shouldBe "manual"
      }

      test("should not throw exception if cannot find generator attribute in osm start element") {
        val osmStartElement = StartElement("osm", mapOf("version" to "0.6"))
        val (parserState, _) = ReadingOsmMetadata.accept(osmStartElement)
        val readingBounds = parserState as ReadingBounds
        readingBounds.apiVersion shouldBe "0.6"
        readingBounds.generator shouldBe null
      }

      test("should throw exception if first element is not osm") {
        val startElement = StartElement("foo")
        shouldThrow<IllegalStateException> {
          ReadingOsmMetadata.accept(startElement)
        }
      }
    }

    context("bounds") {
      test("should consume bounds start element and return OsmMetadata") {
        val readingBounds = ReadingBounds(apiVersion = "6", generator = "manual")
        val boundsStartElement = StartElement("bounds", mapOf(
          "maxlat" to "53.5707",
          "minlat" to "53.5646",
          "maxlon" to "10.0314",
          "minlon" to "10.0155"))

        val (parserState, osmdata) = readingBounds.accept(boundsStartElement)

        parserState.shouldBeTypeOf<ReadingBounds>()
        val expectedBounds = Bounds(minPoint = Point(53.5646, 10.0155), maxPoint = Point(53.5707, 10.0314))
        osmdata shouldBe OsmMetadata(version = "6", generator = "manual", bounds = expectedBounds)
      }

      test("should accept bounds end element and move to reading nodes") {
        val readingBounds = ReadingBounds(apiVersion = "6", generator = "manual")
        val endElement = EndElement("bounds")

        val (parserState, osmdata) = readingBounds.accept(endElement)

        parserState.shouldBeTypeOf<ReadingNodes>()
        osmdata shouldBe null
      }

      test("should throw exception if bounds missing expected minlat attribute") {
        val readingBounds = ReadingBounds(apiVersion = "6", generator = "manual")
        val boundsStartElement = StartElement("bounds", mapOf(
          "maxlat" to "53.5707",
          "maxlon" to "10.0314",
          "minlon" to "10.0155"))

        shouldThrow<IllegalStateException> {
          readingBounds.accept(boundsStartElement)
        }
      }

      test("should throw exception if bounds missing expected minlon attribute") {
        val readingBounds = ReadingBounds(apiVersion = "6", generator = "manual")
        val boundsStartElement = StartElement("bounds", mapOf(
          "maxlat" to "53.5707",
          "minlat" to "53.5646",
          "maxlon" to "10.0314"))

        shouldThrow<IllegalStateException> {
          readingBounds.accept(boundsStartElement)
        }
      }

      test("should throw exception if bounds missing expected maxlat attribute") {
        val readingBounds = ReadingBounds(apiVersion = "6", generator = "manual")
        val boundsStartElement = StartElement("bounds", mapOf(
          "minlat" to "53.5646",
          "maxlon" to "10.0314",
          "minlon" to "10.0155"))

        shouldThrow<IllegalStateException> {
          readingBounds.accept(boundsStartElement)
        }
      }

      test("should throw exception if bounds missing expected maxlon attribute") {
        val readingBounds = ReadingBounds(apiVersion = "6", generator = "manual")
        val boundsStartElement = StartElement("bounds", mapOf(
          "maxlat" to "53.5707",
          "minlat" to "53.5646",
          "minlon" to "10.0155"))

        shouldThrow<IllegalStateException> {
          readingBounds.accept(boundsStartElement)
        }
      }

      test("should rethrow exception if bounds semantically wrong") {
        val readingBounds = ReadingBounds(apiVersion = "6", generator = "manual")
        val boundsStartElement = StartElement("bounds", mapOf(
          "maxlat" to "92.5707",
          "minlat" to "53.5646",
          "maxlon" to "10.0314",
          "minlon" to "10.0155"))

        shouldThrow<IllegalStateException> {
          readingBounds.accept(boundsStartElement)
        }
      }
    }

    context("reading nodes") {



    }

    context("reading element metadata") {

      test("handles all fields if set correctly") {
        val startElement = StartElement("foo", mapOf(
          "id" to "123456",
          "user" to "aUser",
          "uid" to "987654321",
          "timestamp" to "2014-05-14T14:12:29Z",
          "visible" to "true",
          "version" to "123",
          "changeset" to "456789"
        ))

        val expectedTimestamp = ZonedDateTime.of(2014, Month.MAY.value, 14, 14, 12, 29, 0, ZoneOffset.UTC)
        val expected = ElementMetadata(id = 123456, user = "aUser", uid = 987654321, timestamp = expectedTimestamp, visible = true, version = 123, changeSet = 456789)

        readElementMetadata(startElement) shouldBe expected
      }

      test("handles missing user field") {
        val startElement = StartElement("foo", mapOf(
          "id" to "123456",
          "uid" to "987654321",
          "timestamp" to "2014-05-14T14:12:29Z",
          "visible" to "true",
          "version" to "123",
          "changeset" to "456789"
        ))

        val expectedTimestamp = ZonedDateTime.of(2014, Month.MAY.value, 14, 14, 12, 29, 0, ZoneOffset.UTC)
        val expected = ElementMetadata(id = 123456, user = null, uid = 987654321, timestamp = expectedTimestamp, visible = true, version = 123, changeSet = 456789)

        readElementMetadata(startElement) shouldBe expected
      }

      test("missing mandatory attributes lead to illegal state exception") {
        val missingId = mapOf(
          "uid" to "987654321",
          "timestamp" to "2014-05-14T14:12:39Z",
          "visible" to "true",
          "version" to "123",
          "changeset" to "456789"
        )

        val missingUid = mapOf(
          "id" to "123456",
          "timestamp" to "2014-05-14T14:12:39Z",
          "visible" to "true",
          "version" to "123",
          "changeset" to "456789"
        )

        val missingTimestamp = mapOf(
          "id" to "123456",
          "uid" to "987654321",
          "visible" to "true",
          "version" to "123",
          "changeset" to "456789"
        )

        val missingVisible = mapOf(
          "id" to "123456",
          "uid" to "987654321",
          "timestamp" to "2014-05-14T14:12:39Z",
          "version" to "123",
          "changeset" to "456789"
        )

        val missingVersion = mapOf(
          "id" to "123456",
          "uid" to "987654321",
          "timestamp" to "2014-05-14T14:12:39Z",
          "visible" to "true",
          "changeset" to "456789"
        )

        val missingChangeset = mapOf(
          "id" to "123456",
          "uid" to "987654321",
          "timestamp" to "2014-05-14T14:12:39Z",
          "visible" to "true",
          "version" to "123"
        )

        forAll(
          row(missingId),
          row(missingUid),
          row(missingTimestamp),
          row(missingVisible),
          row(missingVersion),
          row(missingChangeset)
        ) { attrs ->
          shouldThrow<IllegalStateException> {
            val startElement = StartElement("foo", attrs)
            readElementMetadata(startElement)
          }
        }
      }

    }
  }
}
