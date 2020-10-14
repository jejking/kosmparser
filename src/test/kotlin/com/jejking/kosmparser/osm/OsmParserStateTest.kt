package com.jejking.kosmparser.osm

import com.jejking.kosmparser.xml.EndElement
import com.jejking.kosmparser.xml.StartDocument
import com.jejking.kosmparser.xml.StartElement
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestCase
import io.kotest.matchers.shouldBe

class OsmParserStateTest : FunSpec() {


  override fun beforeTest(testCase: TestCase) {
    super.beforeTest(testCase)
  }

  init {
    context("reading osm metadata") {
      test("should ignore StartDocument") {
        val startDocument = StartDocument("", "UTF-8", false)
        ReadingOsmMetadata.accept(startDocument) shouldBe (ReadingOsmMetadata to null)
      }

      context("start and end of osm element") {
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
        test("should consume bounds start element").config(enabled = false) {
          val boundsStartElement = StartElement("bounds",
            mapOf(
              "minlat" to "53.5646",
              "minlon" to "10.0155",
              "maxlat" to "53.5707",
              "maxlon" to "10.0314"))

        }

        test("should consume bounds end element and return OSM Metadata and ReadingNodes") {

        }

        test("should throw exception if bounds missing expected minlat attribute") {

        }

        test("should throw exception if bounds missing expected minlon attribute") {

        }

        test("should throw exception if bounds missing expected maxlat attribute") {

        }

        test("should throw exception if bounds missing expected maxlon attribute") {

        }

        test("should rethrow exception if bounds semantically wrong") {

        }
      }

      context("element ordering") {

        test("should throw exception if osm element not followed by bounds") {

        }

      }

      context("characters") {

        test("should ignore characters with whitespace only") {

        }

      }


    }
  }
}
