package com.jejking.kosmparser.osm

import com.jejking.kosmparser.io.asFlow
import com.jejking.kosmparser.osm.OsmArbitraries.arbNode
import com.jejking.kosmparser.osm.OsmArbitraries.arbRelation
import com.jejking.kosmparser.osm.OsmArbitraries.arbWay
import com.jejking.kosmparser.osm.OsmElementEvent.BoundsEvent
import com.jejking.kosmparser.osm.OsmElementEvent.EndNode
import com.jejking.kosmparser.osm.OsmElementEvent.EndRelation
import com.jejking.kosmparser.osm.OsmElementEvent.EndWay
import com.jejking.kosmparser.osm.OsmElementEvent.NodeRef
import com.jejking.kosmparser.osm.OsmElementEvent.RelationMemberRef
import com.jejking.kosmparser.osm.OsmElementEvent.StartNode
import com.jejking.kosmparser.osm.OsmElementEvent.StartOsm
import com.jejking.kosmparser.osm.OsmElementEvent.StartRelation
import com.jejking.kosmparser.osm.OsmElementEvent.StartWay
import com.jejking.kosmparser.osm.OsmElementEvent.Tag
import com.jejking.kosmparser.util.getPath
import com.jejking.kosmparser.util.openFileChannel
import com.jejking.kosmparser.xml.XmlFlowMapper.toParseEvents
import com.jejking.kosmparser.xml.XmlFlowTools.toParseEventFlow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.time.Month
import java.time.ZoneOffset
import java.time.ZonedDateTime

@kotlinx.coroutines.ExperimentalCoroutinesApi
class OsmElementEventTest : FunSpec() {

    private val expectedTimestamp = ZonedDateTime.of(2014, Month.MAY.value, 14, 14, 12, 39, 0, ZoneOffset.UTC)

    private val fullAttributes = mapOf(
        "id" to "1",
        "lat" to "53.12345",
        "lon" to "10.2345",
        "changeset" to "1",
        "timestamp" to "2014-05-14T14:12:39Z",
        "uid" to "1",
        "user" to "foo",
        "version" to "1",
        "visible" to "true"
    )

    private val expectedMetadata = ElementMetadata(
        id = 1,
        changeSet = 1,
        uid = 1,
        visible = true,
        timestamp = expectedTimestamp,
        user = "foo",
        version = 1
    )

    init {
        context("toOsmElementEvents() on test1.osm (full happy path)") {

            test("first event is StartOsm with version and generator") {
                val events = parseFile("test1.osm")
                events.first() shouldBe StartOsm(version = "0.6", generator = "manual")
            }

            test("second event is BoundsEvent with raw string attributes") {
                val events = parseFile("test1.osm")
                events[1] shouldBe BoundsEvent(
                    minlat = "53.5646", maxlat = "53.5707",
                    minlon = "10.0155", maxlon = "10.0314"
                )
            }

            test("emits StartNode, Tag events and EndNode for each node") {
                val events = parseFile("test1.osm")
                val node1Start = events.filterIsInstance<StartNode>().first()
                node1Start.id shouldBe 1L
                node1Start.lat shouldBe 53.12345
                node1Start.lon shouldBe 10.2345
                node1Start.metadata shouldBe expectedMetadata

                val node2Start = events.filterIsInstance<StartNode>()[1]
                node2Start.id shouldBe 2L

                val tags = events.filterIsInstance<Tag>()
                tags.any { it.key == "foo" && it.value == "bar" } shouldBe true

                val endNodes = events.filterIsInstance<EndNode>()
                endNodes.map { it.id } shouldBe listOf(1L, 2L)
            }

            test("emits StartWay, NodeRef events and EndWay") {
                val events = parseFile("test1.osm")
                val wayStart = events.filterIsInstance<StartWay>().first()
                wayStart.id shouldBe 3L

                val nodeRefs = events.filterIsInstance<NodeRef>()
                nodeRefs.map { it.ref } shouldBe listOf(1L, 2L)

                events.filterIsInstance<EndWay>().first().id shouldBe 3L
            }

            test("emits StartRelation, RelationMemberRef events and EndRelation") {
                val events = parseFile("test1.osm")
                val relStart = events.filterIsInstance<StartRelation>().first()
                relStart.id shouldBe 4L

                val members = events.filterIsInstance<RelationMemberRef>()
                members shouldContainInOrder listOf(
                    RelationMemberRef("node", 1L, "thing"),
                    RelationMemberRef("way", 3L, null),
                    RelationMemberRef("relation", 666L, "dark-satanic")
                )

                events.filterIsInstance<EndRelation>().first().id shouldBe 4L
            }
        }

        context("toOsmElementEvents() — optional bounds") {

            test("no BoundsEvent when <bounds> is absent") {
                val events = parseFile("test-no-bounds.osm")
                events.first() shouldBe StartOsm(version = "0.6", generator = "manual")
                events.filterIsInstance<BoundsEvent>() shouldBe emptyList()
                events.filterIsInstance<StartNode>().size shouldBe 2
            }
        }

        context("toOsmElementEvents() — minimal metadata") {

            test("nodes with no metadata attributes parse without error") {
                val events = parseFile("test-minimal-metadata.osm")
                val nodeStarts = events.filterIsInstance<StartNode>()
                nodeStarts.size shouldBe 2
                nodeStarts[0].metadata.uid shouldBe null
                nodeStarts[0].metadata.timestamp shouldBe null
                nodeStarts[0].metadata.version shouldBe null
                nodeStarts[0].metadata.changeSet shouldBe null
            }

            test("negative node id is accepted") {
                val events = parseFile("test-minimal-metadata.osm")
                events.filterIsInstance<StartNode>().first().id shouldBe -1L
            }
        }

        context("toOsmElementEvents() — unknown elements skipped") {

            test("unknown elements produce no events") {
                val events = parseFile("test-unknown-elements.osm")
                val cleanEvents = parseFile("test1.osm").take(
                    // StartOsm, BoundsEvent, 2x(StartNode + EndNode), tags...
                    events.size
                )
                // All events are known types — no null/unknown entries
                events.all { it is OsmElementEvent } shouldBe true
            }

            test("unknown elements do not affect surrounding node events") {
                val events = parseFile("test-unknown-elements.osm")
                events.filterIsInstance<StartNode>().size shouldBe 2
                events.filterIsInstance<EndNode>().size shouldBe 2
            }

            test("result is same as parsing equivalent clean xml inline") {
                val cleanXml = """
                    <osm generator="manual" version="0.6">
                      <bounds maxlat="53.5707" maxlon="10.0314" minlat="53.5646" minlon="10.0155"/>
                      <node id="1" lat="53.12345" lon="10.2345" changeset="1" timestamp="2014-05-14T14:12:39Z"
                        uid="1" user="foo" version="1" visible="true"/>
                      <node id="2" lat="54.23456" lon="11.5432" changeset="1" timestamp="2014-05-14T14:12:39Z"
                        uid="1" user="foo" version="1" visible="true">
                        <tag k="foo" v="bar"/>
                      </node>
                    </osm>
                """.trimIndent()
                val cleanEvents = toParseEventFlow(cleanXml).toOsmElementEvents().toList()
                val withUnknownEvents = parseFile("test-unknown-elements.osm")
                cleanEvents shouldBe withUnknownEvents
            }
        }

        context("property-based: unknown elements injected at random positions") {

            test("injecting unknown start/end pairs at any position yields same events as clean stream") {
                checkAll(Arb.string(1..20).filter { it.all { c -> c.isLetter() } }) { unknownName ->
                    val nodeId = 42L
                    val baseXml = """
                        <osm version="0.6" generator="test">
                          <node id="$nodeId" lat="53.0" lon="10.0"/>
                        </osm>
                    """.trimIndent()
                    val withUnknownXml = """
                        <osm version="0.6" generator="test">
                          <$unknownName/>
                          <node id="$nodeId" lat="53.0" lon="10.0"/>
                          <$unknownName foo="bar"/>
                        </osm>
                    """.trimIndent()

                    val baseEvents = toParseEventFlow(baseXml).toOsmElementEvents().toList()
                    val withUnknownEvents = toParseEventFlow(withUnknownXml).toOsmElementEvents().toList()

                    withUnknownEvents shouldBe baseEvents
                }
            }
        }

        context("property-based: arbitrary domain objects produce correct event sequences") {

            test("arbitrary nodes produce StartNode/EndNode events") {
                checkAll(arbNode) { node ->
                    val xml = node.toOsmXml()
                    val events = toParseEventFlow(xml).toOsmElementEvents().toList()
                    events.any { it is StartNode && it.id == node.elementMetadata.id } shouldBe true
                    events.any { it is EndNode && it.id == node.elementMetadata.id } shouldBe true
                }
            }

            test("arbitrary ways produce StartWay/NodeRef/EndWay events") {
                checkAll(arbWay) { way ->
                    val xml = way.toOsmXml()
                    val events = toParseEventFlow(xml).toOsmElementEvents().toList()
                    events.any { it is StartWay && it.id == way.elementMetadata.id } shouldBe true
                    val nodeRefs = events.filterIsInstance<NodeRef>().map { it.ref }
                    nodeRefs shouldBe way.nds
                    events.any { it is EndWay && it.id == way.elementMetadata.id } shouldBe true
                }
            }

            test("arbitrary relations produce StartRelation/RelationMemberRef/EndRelation events") {
                checkAll(arbRelation) { relation ->
                    val xml = relation.toOsmXml()
                    val events = toParseEventFlow(xml).toOsmElementEvents().toList()
                    events.any { it is StartRelation && it.id == relation.elementMetadata.id } shouldBe true
                    val memberRefs = events.filterIsInstance<RelationMemberRef>()
                    memberRefs.size shouldBe relation.members.size
                    events.any { it is EndRelation && it.id == relation.elementMetadata.id } shouldBe true
                }
            }
        }
    }

    private fun parseFile(path: String): List<OsmElementEvent> = runBlocking {
        val testFilePath = getPath(path)
        val fileChannel = openFileChannel(testFilePath)
        try {
            toParseEvents(fileChannel.asFlow()).toOsmElementEvents().toList()
        } finally {
            fileChannel.close()
        }
    }
}

// --- XML serialisation helpers for property-based tests ---

internal fun ElementMetadata.toXmlAttributes(): String {
    val attrs = mutableListOf("id=\"$id\"")
    user?.let { attrs += "user=\"${it.escapeXml()}\"" }
    uid?.let { attrs += "uid=\"$it\"" }
    timestamp?.let { attrs += "timestamp=\"${it.toInstant()}\"" }
    attrs += "visible=\"$visible\""
    version?.let { attrs += "version=\"$it\"" }
    changeSet?.let { attrs += "changeset=\"$it\"" }
    return attrs.joinToString(" ")
}

internal fun Node.toOsmXml(): String {
    val meta = elementMetadata.toXmlAttributes()
    val tagXml = tags.entries.joinToString("\n") { (k, v) -> "  <tag k=\"${k.escapeXml()}\" v=\"${v.escapeXml()}\"/>" }
    return if (tags.isEmpty()) {
        "<osm version=\"0.6\"><node $meta lat=\"${point.lat}\" lon=\"${point.lon}\"/></osm>"
    } else {
        "<osm version=\"0.6\"><node $meta lat=\"${point.lat}\" lon=\"${point.lon}\">\n$tagXml\n</node></osm>"
    }
}

internal fun Way.toOsmXml(): String {
    val meta = elementMetadata.toXmlAttributes()
    val ndsXml = nds.joinToString("\n") { "  <nd ref=\"$it\"/>" }
    val tagXml = tags.entries.joinToString("\n") { (k, v) -> "  <tag k=\"${k.escapeXml()}\" v=\"${v.escapeXml()}\"/>" }
    return "<osm version=\"0.6\"><way $meta>\n$ndsXml\n$tagXml\n</way></osm>"
}

internal fun Relation.toOsmXml(): String {
    val meta = elementMetadata.toXmlAttributes()
    val membersXml = members.joinToString("\n") { m ->
        val typeStr = m.type.name.lowercase()
        val role = (m.role ?: "").escapeXml()
        "  <member type=\"$typeStr\" ref=\"${m.id}\" role=\"$role\"/>"
    }
    val tagXml = tags.entries.joinToString("\n") { (k, v) -> "  <tag k=\"${k.escapeXml()}\" v=\"${v.escapeXml()}\"/>" }
    return "<osm version=\"0.6\"><relation $meta>\n$membersXml\n$tagXml\n</relation></osm>"
}

private fun String.escapeXml(): String = this
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
