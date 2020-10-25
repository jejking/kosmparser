package com.jejking.kosmparser.osm

import com.jejking.kosmparser.xml.EndDocument
import com.jejking.kosmparser.xml.EndElement
import com.jejking.kosmparser.xml.SimpleXmlParseEvent
import com.jejking.kosmparser.xml.StartDocument
import com.jejking.kosmparser.xml.StartElement
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldBeTypeOf
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
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

      test("should consume end document") {
        val (parserState, _) = ReadingOsmMetadata.accept(EndDocument)
        parserState shouldBe Finished
      }
    }

    context("bounds") {
      test("should consume bounds start element and return OsmMetadata") {
        val readingBounds = ReadingBounds(apiVersion = "6", generator = "manual")
        val boundsStartElement = StartElement(
          "bounds",
          mapOf(
            "maxlat" to "53.5707",
            "minlat" to "53.5646",
            "maxlon" to "10.0314",
            "minlon" to "10.0155"
          )
        )

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
        val boundsStartElement = StartElement(
          "bounds",
          mapOf(
            "maxlat" to "53.5707",
            "maxlon" to "10.0314",
            "minlon" to "10.0155"
          )
        )

        shouldThrow<IllegalStateException> {
          readingBounds.accept(boundsStartElement)
        }
      }

      test("should throw exception if bounds missing expected minlon attribute") {
        val readingBounds = ReadingBounds(apiVersion = "6", generator = "manual")
        val boundsStartElement = StartElement(
          "bounds",
          mapOf(
            "maxlat" to "53.5707",
            "minlat" to "53.5646",
            "maxlon" to "10.0314"
          )
        )

        shouldThrow<IllegalStateException> {
          readingBounds.accept(boundsStartElement)
        }
      }

      test("should throw exception if bounds missing expected maxlat attribute") {
        val readingBounds = ReadingBounds(apiVersion = "6", generator = "manual")
        val boundsStartElement = StartElement(
          "bounds",
          mapOf(
            "minlat" to "53.5646",
            "maxlon" to "10.0314",
            "minlon" to "10.0155"
          )
        )

        shouldThrow<IllegalStateException> {
          readingBounds.accept(boundsStartElement)
        }
      }

      test("should throw exception if bounds missing expected maxlon attribute") {
        val readingBounds = ReadingBounds(apiVersion = "6", generator = "manual")
        val boundsStartElement = StartElement(
          "bounds",
          mapOf(
            "maxlat" to "53.5707",
            "minlat" to "53.5646",
            "minlon" to "10.0155"
          )
        )

        shouldThrow<IllegalStateException> {
          readingBounds.accept(boundsStartElement)
        }
      }

      test("should rethrow exception if bounds semantically wrong") {
        val readingBounds = ReadingBounds(apiVersion = "6", generator = "manual")
        val boundsStartElement = StartElement(
          "bounds",
          mapOf(
            "maxlat" to "92.5707",
            "minlat" to "53.5646",
            "maxlon" to "10.0314",
            "minlon" to "10.0155"
          )
        )

        shouldThrow<IllegalStateException> {
          readingBounds.accept(boundsStartElement)
        }
      }
    }

    context("reading tags") {

      class TestTagReceiver : TagReceiver() {
        override fun accept(xmlparseEventSimpleXml: SimpleXmlParseEvent): Pair<ParserState, OsmData?> {
          return this to null
        }

        fun getSuppliedTags() = this.tags
      }

      test("should read single tag") {
        val startElement = StartElement("tag", mapOf("k" to "foo", "v" to "bar"))
        val tagReceiver = TestTagReceiver()
        val readingTags = ReadingTags(tagReceiver)

        readingTags.accept(startElement)
        readingTags.accept(EndElement("tag"))
        val (parserState, osmData) = readingTags.accept(EndElement("node"))

        parserState shouldBe tagReceiver
        osmData shouldBe null
        tagReceiver.getSuppliedTags() shouldBe mapOf("foo" to "bar")
      }

      test("should read multiple tags") {
        val startElement1 = StartElement("tag", mapOf("k" to "foo", "v" to "bar"))
        val startElement2 = StartElement("tag", mapOf("k" to "wibble", "v" to "wobble"))
        val tagReceiver = TestTagReceiver()
        val readingTags = ReadingTags(tagReceiver)

        readingTags.accept(startElement1)
        readingTags.accept(EndElement("tag"))

        readingTags.accept(startElement2)
        readingTags.accept(EndElement("tag"))
        val (parserState, osmData) = readingTags.accept(EndElement("node"))

        parserState shouldBe tagReceiver
        osmData shouldBe null
        tagReceiver.getSuppliedTags() shouldBe mapOf("foo" to "bar", "wibble" to "wobble")
      }
    }

    context("reading nodes") {

      val standardElementMetadataAttrs = mapOf(
        "id" to "123456",
        "user" to "aUser",
        "uid" to "987654321",
        "timestamp" to "2014-05-14T14:12:29Z",
        "visible" to "true",
        "version" to "123",
        "changeset" to "456789"
      )

      val expectedTimestamp = ZonedDateTime.of(2014, Month.MAY.value, 14, 14, 12, 29, 0, ZoneOffset.UTC)
      val expectedElementMetadata = ElementMetadata(
        id = 123456,
        user = "aUser",
        uid = 987654321,
        timestamp = expectedTimestamp,
        visible = true,
        version = 123,
        changeSet = 456789
      )

      test("node with no tags") {
        val readingNodes = ReadingNodes()

        val attrs = standardElementMetadataAttrs.toMutableMap()
        attrs["lat"] = "54.23456"
        attrs["lon"] = "11.5432"
        val nodeStartElement = StartElement("node", attributes = attrs.toMap())

        // read in node start element
        val (parserState1, osmData1) = readingNodes.accept(nodeStartElement)

        parserState1.shouldBeTypeOf<ReadingTags>()
        osmData1 shouldBe null

        // read end element - emit Node
        val (parserState2, osmData2) = parserState1.accept(EndElement("node"))

        parserState2.shouldBeTypeOf<ReadingNodes>()
        parserState2 shouldNotBeSameInstanceAs readingNodes
        val node = osmData2 as Node
        node.elementMetadata shouldBe expectedElementMetadata
        node.point shouldBe Point(lat = 54.23456, lon = 11.5432)
        node.tags shouldBe emptyMap()
      }

      test("node with tags") {
        val readingNodes = ReadingNodes()

        val attrs = standardElementMetadataAttrs.toMutableMap()
        attrs["lat"] = "54.23456"
        attrs["lon"] = "11.5432"
        val nodeStartElement = StartElement("node", attributes = attrs.toMap())

        // read in node start element
        val (parserState1, osmData1) = readingNodes.accept(nodeStartElement)

        parserState1.shouldBeTypeOf<ReadingTags>()
        osmData1 shouldBe null

        // read in a tag
        val tag = StartElement("tag", attributes = mapOf("k" to "key", "v" to "value"))
        val (parserState2, osmData2) = parserState1.accept(tag)
        parserState2 shouldBeSameInstanceAs parserState1
        osmData2 shouldBe null

        // end tag
        val (parserState3, osmData3) = parserState2.accept(EndElement("tag"))
        parserState3 shouldBeSameInstanceAs parserState2
        osmData3 shouldBe null

        // read end element - emit Node
        val (parserState4, osmData4) = parserState3.accept(EndElement("node"))

        parserState4.shouldBeTypeOf<ReadingNodes>()
        parserState4 shouldNotBeSameInstanceAs readingNodes
        val node = osmData4 as Node
        node.elementMetadata shouldBe expectedElementMetadata
        node.point shouldBe Point(lat = 54.23456, lon = 11.5432)
        node.tags shouldBe mapOf("key" to "value")
      }

      test("sees a way element") {
        // if we see a way element, we move to ReadingWays
        val way = StartElement("way", standardElementMetadataAttrs)
        val readingNodes = ReadingNodes()
        val (parserState, _) = readingNodes.accept(way)
        parserState.shouldBeTypeOf<ReadingNds>() // goes straight through....
      }

      test("sees a relation element") {
        // if we see a way element, we move to ReadingRelations
        val relation = StartElement("relation", standardElementMetadataAttrs)
        val readingNodes = ReadingNodes()
        val (parserState, _) = readingNodes.accept(relation)
        parserState.shouldBeTypeOf<ReadingMembers>()
      }

      test("it sees the end of osm") {
        // case where we have no ways or relations. Unlikely but feasible edge case
        val endOsm = EndElement("osm")
        val readingNodes = ReadingNodes()
        val (parserState, _) = readingNodes.accept(endOsm)
        parserState shouldBe ReadingOsmMetadata
      }
    }

    context("reading ways") {

      val standardElementMetadataAttrs = mapOf(
        "id" to "123456",
        "user" to "aUser",
        "uid" to "987654321",
        "timestamp" to "2014-05-14T14:12:29Z",
        "visible" to "true",
        "version" to "123",
        "changeset" to "456789"
      )

      test("should read in id and other standard element metadata") {
        val startElement = StartElement("way", standardElementMetadataAttrs)
        val readingWays = ReadingWays()
        val (parserState, osmData) = readingWays.accept(startElement)
        parserState.shouldBeTypeOf<ReadingNds>()
        osmData shouldBe null
      }

      test("should throw exception if missing expected id attribute") {

        val attrs = mapOf(
          "user" to "aUser",
          "uid" to "987654321",
          "timestamp" to "2014-05-14T14:12:29Z",
          "visible" to "true",
          "version" to "123",
          "changeset" to "456789"
        )

        val startElement = StartElement("way", attrs)
        val readingWays = ReadingWays()
        shouldThrow<IllegalStateException> {
          readingWays.accept(startElement)
        }
      }

      context("reading nds") {
        test("should read in one nd") {
          val startElement = StartElement("nd", attributes = mapOf("ref" to "12345"))
          val readingNds = ReadingNds(ReadingWays())

          val (parserState1, _) = readingNds.accept(startElement)
          parserState1 shouldBeSameInstanceAs readingNds

          val (parserState2, _) = readingNds.accept(EndElement("nd"))
          parserState2 shouldBeSameInstanceAs readingNds
        }

        test("should read in several nds") {
          val startElement = StartElement("nd", attributes = mapOf("ref" to "12345"))
          val readingNds = ReadingNds(ReadingWays())

          val (parserState1, _) = readingNds.accept(startElement)
          parserState1 shouldBeSameInstanceAs readingNds

          val (parserState2, _) = parserState1.accept(EndElement("nd"))
          parserState2 shouldBeSameInstanceAs readingNds

          val startElement2 = StartElement("nd", attributes = mapOf("ref" to "12345"))
          val (parserState3, _) = parserState2.accept(startElement2)
          parserState3 shouldBeSameInstanceAs readingNds
        }

        test("sees a tag element") {
          // should return back to  reading ways
          val readingWays = ReadingWays()
          val readingNds = ReadingNds(readingWays)

          val tag = StartElement("tag", mapOf("k" to "key", "v" to "value"))

          val (parserState, _) = readingNds.accept(tag)
          parserState.shouldBeTypeOf<ReadingTags>()
        }

        // untagged ways are conceivable
        test("sees an end of way element") {
          // hand back to reading ways
          val readingWays = ReadingWays()
          readingWays.accept(StartElement("way", standardElementMetadataAttrs))
          val readingNds = ReadingNds(readingWays)

          val endWay = EndElement("way")

          val (parserState, _) = readingNds.accept(endWay)
          parserState shouldNotBeSameInstanceAs readingWays
          parserState.shouldBeTypeOf<ReadingWays>()
        }

        test("should throw exception if nd missing expected ref attribute") {
          val nd = StartElement("nd") // no attributes
          val readingNds = ReadingNds(ReadingWays())

          shouldThrow<IllegalStateException> {
            readingNds.accept(nd)
          }
        }

        test("should throw exception if ref cannot be parsed to Long") {
          val nd = StartElement("nd", mapOf("ref" to "123.45")) // ref not a LONG
          val readingNds = ReadingNds(ReadingWays())

          shouldThrow<IllegalStateException> {
            readingNds.accept(nd)
          }
        }
      }

      /*
       Tests for number of ND elements not needed at parser level as we deal with
       that in the domain object.
       */
      test("should emit fully formed way element on endElement") {
        // including nds & tags
        val startWay = StartElement("way", standardElementMetadataAttrs)
        val nd1 = StartElement("nd", mapOf("ref" to "12"))
        val nd2 = StartElement("nd", mapOf("ref" to "34"))

        val fooTag = StartElement("tag", mapOf("k" to "foo", "v" to "bar"))
        val wibbleTag = StartElement("tag", mapOf("k" to "wibble", "v" to "wobble"))

        var parserState: ParserState = ReadingWays()
        parserState = parserState.accept(startWay).first
        parserState = parserState.accept(nd1).first
        parserState = parserState.accept(EndElement("nd")).first
        parserState = parserState.accept(nd2).first
        parserState = parserState.accept(EndElement("nd")).first
        parserState = parserState.accept(fooTag).first
        parserState = parserState.accept(EndElement("tag")).first
        parserState = parserState.accept(wibbleTag).first
        parserState = parserState.accept(EndElement("tag")).first
        val (endParserState, osmData) = parserState.accept(EndElement("way"))

        endParserState.shouldBeTypeOf<ReadingWays>()
        val way = osmData as Way
        way.nds shouldBe listOf(12, 34)
        way.tags shouldBe mapOf("foo" to "bar", "wibble" to "wobble")
        way.elementMetadata shouldBe readElementMetadata(startWay)
      }

      test("should handle edge case with no nds") {
        val startWay = StartElement("way", standardElementMetadataAttrs)

        val fooTag = StartElement("tag", mapOf("k" to "foo", "v" to "bar"))
        val wibbleTag = StartElement("tag", mapOf("k" to "wibble", "v" to "wobble"))

        var parserState: ParserState = ReadingWays()
        parserState = parserState.accept(startWay).first
        parserState = parserState.accept(fooTag).first
        parserState = parserState.accept(EndElement("tag")).first
        parserState = parserState.accept(wibbleTag).first
        parserState = parserState.accept(EndElement("tag")).first
        val (endParserState, osmData) = parserState.accept(EndElement("way"))

        endParserState.shouldBeTypeOf<ReadingWays>()
        val way = osmData as Way
        way.nds shouldBe listOf()
        way.tags shouldBe mapOf("foo" to "bar", "wibble" to "wobble")
        way.elementMetadata shouldBe readElementMetadata(startWay)
      }

      test("should handle case with no tags") {
        val startWay = StartElement("way", standardElementMetadataAttrs)
        val nd1 = StartElement("nd", mapOf("ref" to "12"))
        val nd2 = StartElement("nd", mapOf("ref" to "34"))

        var parserState: ParserState = ReadingWays()
        parserState = parserState.accept(startWay).first
        parserState = parserState.accept(nd1).first
        parserState = parserState.accept(EndElement("nd")).first
        parserState = parserState.accept(nd2).first
        parserState = parserState.accept(EndElement("nd")).first

        val (endParserState, osmData) = parserState.accept(EndElement("way"))

        endParserState.shouldBeTypeOf<ReadingWays>()
        val way = osmData as Way
        way.nds shouldBe listOf(12, 34)
        way.tags shouldBe mapOf()
        way.elementMetadata shouldBe readElementMetadata(startWay)
      }

      test("sees a relation element") {
        val relation = StartElement("relation", standardElementMetadataAttrs)
        val readingWays = ReadingWays()

        val (parserState, _) = readingWays.accept(relation)
        parserState.shouldBeTypeOf<ReadingMembers>()
      }

      test("sees end of osm element") {
        val readingWays = ReadingWays()
        val endElement = EndElement("osm")

        val (parserState, _) = readingWays.accept(endElement)
        parserState shouldBe ReadingOsmMetadata
      }
    }

    context("reading relations") {

      val standardElementMetadataAttrs = mapOf(
        "id" to "123456",
        "user" to "aUser",
        "uid" to "987654321",
        "timestamp" to "2014-05-14T14:12:29Z",
        "visible" to "true",
        "version" to "123",
        "changeset" to "456789"
      )

      test("should read in relation start element") {
        // nothing special, just standard element metadata
        val relation = StartElement("relation", standardElementMetadataAttrs)
        val readingRelations = ReadingRelations()

        val (parserState, _) = readingRelations.accept(relation)

        parserState.shouldBeTypeOf<ReadingMembers>()
      }

      test("should throw exception (as usual) if missing standard element metadata") {
        val attrs = mapOf(
          "user" to "aUser",
          "uid" to "987654321",
          "timestamp" to "2014-05-14T14:12:29Z",
          "visible" to "true",
          "version" to "123",
          "changeset" to "456789"
        )

        val startElement = StartElement("relation", attrs)
        val readingRelations = ReadingWays()
        shouldThrow<IllegalStateException> {
          readingRelations.accept(startElement)
        }
      }

      context("reading members") {
        test("should read node member with type, long and role set") {
          val nodeMember = StartElement("member", mapOf("type" to "node", "ref" to "123", "role" to "foo"))
          val readingMembers = ReadingMembers(ReadingRelations())

          val (parserState, _) = readingMembers.accept(nodeMember)
          parserState shouldBeSameInstanceAs readingMembers
        }

        test("should read way member") {
          val wayMember = StartElement("member", mapOf("type" to "way", "ref" to "123", "role" to "foo"))
          val readingMembers = ReadingMembers(ReadingRelations())

          readingMembers.accept(wayMember)
        }

        test("should read relation member") {
          val wayMember = StartElement("member", mapOf("type" to "relation", "ref" to "123", "role" to "foo"))
          val readingMembers = ReadingMembers(ReadingRelations())

          readingMembers.accept(wayMember)
        }

        test("should throw exception if type cannot be mapped") {
          val fooMember = StartElement("rel", mapOf("type" to "foo", "ref" to "123", "role" to "foo"))
          val readingMembers = ReadingMembers(ReadingRelations())

          shouldThrow<IllegalStateException> {
            readingMembers.accept(fooMember)
          }
        }

        test("should throw exception if ref cannot be parsed to long") {
          val wayMember = StartElement("rel", mapOf("type" to "way", "ref" to "abcd", "role" to "foo"))
          val readingMembers = ReadingMembers(ReadingRelations())

          shouldThrow<IllegalStateException> {
            readingMembers.accept(wayMember)
          }
        }

        test("should go to reading tags if tag start element seen") {
          val tagElement = StartElement("tag", mapOf("k" to "key", "v" to "value"))
          val readingMembers = ReadingMembers(ReadingRelations())

          val (parserState, _) = readingMembers.accept(tagElement)
          parserState.shouldBeTypeOf<ReadingTags>()
        }
      }

      test("should emit Relation with members and tags") {
        // including members & tags
        val startRelation = StartElement("relation", standardElementMetadataAttrs)
        val member1 = StartElement("member", mapOf("type" to "node", "ref" to "123", "role" to "undetermined"))
        val member2 = StartElement("member", mapOf("type" to "way", "ref" to "456", "role" to ""))

        val fooTag = StartElement("tag", mapOf("k" to "foo", "v" to "bar"))
        val wibbleTag = StartElement("tag", mapOf("k" to "wibble", "v" to "wobble"))

        var parserState: ParserState = ReadingRelations()
        parserState = parserState.accept(startRelation).first
        parserState = parserState.accept(member1).first
        parserState = parserState.accept(EndElement("member")).first
        parserState = parserState.accept(member2).first
        parserState = parserState.accept(EndElement("member")).first
        parserState = parserState.accept(fooTag).first
        parserState = parserState.accept(EndElement("tag")).first
        parserState = parserState.accept(wibbleTag).first
        parserState = parserState.accept(EndElement("tag")).first
        val (endParserState, osmData) = parserState.accept(EndElement("relation"))

        endParserState.shouldBeTypeOf<ReadingRelations>()
        val relation = osmData as Relation
        relation.members shouldBe listOf(
          Member(MemberType.NODE, 123, "undetermined"),
          Member(MemberType.WAY, 456, null)
        )
        relation.tags shouldBe mapOf("foo" to "bar", "wibble" to "wobble")
        relation.elementMetadata shouldBe readElementMetadata(startRelation)
      }

      test("should handle case with no members") {
        val startRelation = StartElement("relation", standardElementMetadataAttrs)

        val fooTag = StartElement("tag", mapOf("k" to "foo", "v" to "bar"))
        val wibbleTag = StartElement("tag", mapOf("k" to "wibble", "v" to "wobble"))

        var parserState: ParserState = ReadingRelations()
        parserState = parserState.accept(startRelation).first
        parserState = parserState.accept(fooTag).first
        parserState = parserState.accept(EndElement("tag")).first
        parserState = parserState.accept(wibbleTag).first
        parserState = parserState.accept(EndElement("tag")).first
        val (endParserState, osmData) = parserState.accept(EndElement("relation"))

        endParserState.shouldBeTypeOf<ReadingRelations>()
        val relation = osmData as Relation
        relation.members shouldBe listOf()
        relation.tags shouldBe mapOf("foo" to "bar", "wibble" to "wobble")
        relation.elementMetadata shouldBe readElementMetadata(startRelation)
      }

      test("should handle case with no tags") {
        val startRelation = StartElement("relation", standardElementMetadataAttrs)
        val member1 = StartElement("member", mapOf("type" to "node", "ref" to "123", "role" to "undetermined"))
        val member2 = StartElement("member", mapOf("type" to "way", "ref" to "456", "role" to ""))

        var parserState: ParserState = ReadingRelations()
        parserState = parserState.accept(startRelation).first
        parserState = parserState.accept(member1).first
        parserState = parserState.accept(EndElement("member")).first
        parserState = parserState.accept(member2).first
        parserState = parserState.accept(EndElement("member")).first
        val (endParserState, osmData) = parserState.accept(EndElement("relation"))

        endParserState.shouldBeTypeOf<ReadingRelations>()
        val relation = osmData as Relation
        relation.members shouldBe listOf(
          Member(MemberType.NODE, 123, "undetermined"),
          Member(MemberType.WAY, 456, null)
        )
        relation.tags shouldBe mapOf()
        relation.elementMetadata shouldBe readElementMetadata(startRelation)
      }
    }

    context("readElementMetadata function") {

      test("handles all fields if set correctly") {
        val startElement = StartElement(
          "foo",
          mapOf(
            "id" to "123456",
            "user" to "aUser",
            "uid" to "987654321",
            "timestamp" to "2014-05-14T14:12:29Z",
            "visible" to "true",
            "version" to "123",
            "changeset" to "456789"
          )
        )

        val expectedTimestamp = ZonedDateTime.of(2014, Month.MAY.value, 14, 14, 12, 29, 0, ZoneOffset.UTC)
        val expected = ElementMetadata(
          id = 123456,
          user = "aUser",
          uid = 987654321,
          timestamp = expectedTimestamp,
          visible = true,
          version = 123,
          changeSet = 456789
        )

        readElementMetadata(startElement) shouldBe expected
      }

      test("handles missing user field") {
        val startElement = StartElement(
          "foo",
          mapOf(
            "id" to "123456",
            "uid" to "987654321",
            "timestamp" to "2014-05-14T14:12:29Z",
            "visible" to "true",
            "version" to "123",
            "changeset" to "456789"
          )
        )

        val expectedTimestamp = ZonedDateTime.of(2014, Month.MAY.value, 14, 14, 12, 29, 0, ZoneOffset.UTC)
        val expected = ElementMetadata(
          id = 123456,
          user = null,
          uid = 987654321,
          timestamp = expectedTimestamp,
          visible = true,
          version = 123,
          changeSet = 456789
        )

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
