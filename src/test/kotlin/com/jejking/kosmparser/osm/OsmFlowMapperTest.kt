package com.jejking.kosmparser.osm

import com.jejking.kosmparser.io.asFlow
import com.jejking.kosmparser.osm.OsmFlowMapper.toOsmDataFlow
import com.jejking.kosmparser.util.getPath
import com.jejking.kosmparser.util.openFileChannel
import com.jejking.kosmparser.xml.SimpleXmlParseEvent
import com.jejking.kosmparser.xml.XmlFlowMapper.coalesceText
import com.jejking.kosmparser.xml.XmlFlowMapper.toParseEvents
import com.jejking.kosmparser.xml.XmlFlowTools.toParseEventFlow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.time.Month
import java.time.ZoneOffset
import java.time.ZonedDateTime

@kotlinx.coroutines.ExperimentalCoroutinesApi
class OsmFlowMapperTest : FunSpec() {

  init {
    context("correct osm xml") {
      val expectedTimestamp = ZonedDateTime.of(2014, Month.MAY.value, 14, 14, 12, 39, 0, ZoneOffset.UTC)

      test("should emit an osmmetadata object with version, generator, bounds") {
        val bounds = Bounds(Point(53.5646, 10.0155), Point(53.5707, 10.0314))
        val osmMetadata = OsmMetadata("0.6", "manual", bounds)

        val osmDataFlow = toOsmDataFlow(xmlParseEvents("test1.osm"))

        runBlocking {
          osmDataFlow.first() shouldBe osmMetadata
        }
      }
      test("should emit two nodes") {
        val osmDataFlow = toOsmDataFlow(xmlParseEvents("test1.osm"))

        val node1 = Node(
          elementMetadata = ElementMetadata(
            id = 1,
            changeSet = 1,
            uid = 1,
            visible = true,
            timestamp = expectedTimestamp,
            user = "foo",
            version = 1
          ),
          tags = emptyMap(),
          point = Point(lat = 53.12345, lon = 10.2345)
        )

        val node2 = Node(
          elementMetadata = ElementMetadata(
            id = 2,
            changeSet = 1,
            uid = 1,
            visible = true,
            timestamp = expectedTimestamp,
            user = "foo",
            version = 1
          ),
          tags = mapOf("foo" to "bar", "wibble" to "wobble"),
          point = Point(lat = 54.23456, lon = 11.5432)
        )

        runBlocking {
          val nodes = osmDataFlow.filter { it is Node }.toList()
          nodes shouldBe listOf(node1, node2)
        }
      }

      test("should emit a way") {

        val osmDataFlow = toOsmDataFlow(xmlParseEvents("test1.osm"))
        val way = Way(
          elementMetadata = ElementMetadata(
            id = 3,
            changeSet = 1,
            uid = 1,
            visible = true,
            timestamp = expectedTimestamp,
            user = "foo",
            version = 1
          ),
          tags = mapOf("highway" to "motorway"),
          nds = listOf(1, 2)
        )

        runBlocking {
          val ways = osmDataFlow.filter { it is Way }.toList()
          ways shouldBe listOf(way)
        }
      }

      test("should emit a relation") {
        val osmDataFlow = toOsmDataFlow(xmlParseEvents("test1.osm"))

        val relation = Relation(
          elementMetadata = ElementMetadata(
            id = 4,
            changeSet = 1,
            timestamp = expectedTimestamp,
            uid = 1,
            user = "foo",
            version = 1,
            visible = true
          ),
          tags = mapOf("route" to "secret"),
          members = listOf(
            Member(type = MemberType.NODE, id = 1, role = "thing"),
            Member(type = MemberType.WAY, id = 3, role = null),
            Member(type = MemberType.RELATION, id = 666, role = "dark-satanic")
          )
        )
        runBlocking {
          val relations = osmDataFlow.filter { it is Relation }.toList()
          relations shouldBe listOf(relation)
        }
      }
    }
    context("nodes only") {
      test("should run through OK") {
        val xml =
          """
        <osm generator="manual" version="0.6">
          <bounds maxlat="53.5707" maxlon="10.0314" minlat="53.5646" minlon="10.0155"/>
          <node id="1" lat="53.12345" lon="10.2345" changeset="1" timestamp="2014-05-14T14:12:39Z"
          uid="1" user="foo" version="1" visible="true"/>
          <node id="2" lat="54.23456" lon="11.5432" changeset="1" timestamp="2014-05-14T14:12:39Z"
            uid="1" user="foo" version="1" visible="true">
            <tag k="foo" v="bar"/>
            <tag k="wibble" v="wobble"/>
          </node>
        </osm>
          """.trimIndent()

        val osmDataFlow = toOsmDataFlow(toParseEventFlow(xml))
        osmDataFlow.count() shouldBe 3
      }
    }
    context("ways only") {
      test("should run through OK") {
        val xml =
          """
        <osm generator="manual" version="0.6">
          <bounds maxlat="53.5707" maxlon="10.0314" minlat="53.5646" minlon="10.0155"/>
          <way id="3" changeset="1" timestamp="2014-05-14T14:12:39Z" uid="1" user="foo" version="1" visible="true">
            <nd ref="1"/>
            <nd ref="2"/>
            <tag k="highway" v="motorway"/>
          </way>
        </osm>
          """.trimIndent()

        val osmDataFlow = toOsmDataFlow(toParseEventFlow(xml))
        osmDataFlow.count() shouldBe 2
      }
    }
  }

  fun xmlParseEvents(path: String): Flow<SimpleXmlParseEvent> {
    val testFilePath = getPath(path)
    val fileChannel = autoClose(openFileChannel(testFilePath))

    return toParseEvents(fileChannel.asFlow())
  }
}
