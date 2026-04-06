package com.jejking.kosmparser.osm

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
import com.jejking.kosmparser.xml.XmlFlowTools.toParseEventFlow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList

@kotlinx.coroutines.ExperimentalCoroutinesApi
class OsmDataBuilderTest : FunSpec() {

    init {
        context("OsmMetadata assembly") {
            test("StartOsm with version and generator, no bounds emits OsmMetadata(version, generator, null)") {
                val result = flowOf(StartOsm("0.6", "manual")).toOsmData().toList()
                result shouldHaveSize 1
                result.first() shouldBe OsmMetadata("0.6", "manual", null)
            }

            test("StartOsm with BoundsEvent emits OsmMetadata with bounds") {
                val result = flowOf(
                    StartOsm("0.6", "manual"),
                    BoundsEvent("53.5646", "53.5707", "10.0155", "10.0314")
                ).toOsmData().toList()
                result shouldHaveSize 1
                result.first() shouldBe OsmMetadata(
                    "0.6", "manual",
                    Bounds(Point(53.5646, 10.0155), Point(53.5707, 10.0314))
                )
            }

            test("StartOsm without version or generator emits OsmMetadata(null, null, null)") {
                val result = flowOf(StartOsm(null, null)).toOsmData().toList()
                result.first() shouldBe OsmMetadata(null, null, null)
            }

            test("OsmMetadata is emitted as first item before any domain element") {
                val meta = testMetadata(1L)
                val result = flowOf(
                    StartOsm("0.6", "test"),
                    StartNode(1L, 53.0, 10.0, meta),
                    EndNode(1L)
                ).toOsmData().toList()
                result.first().shouldBeInstanceOf<OsmMetadata>()
            }
        }

        context("Node construction") {
            test("StartNode/EndNode with no tags emits Node with empty tags") {
                val meta = testMetadata(1L)
                val result = flowOf(
                    StartOsm("0.6", "t"),
                    StartNode(1L, 53.12345, 10.2345, meta),
                    EndNode(1L)
                ).toOsmData().toList()
                result shouldHaveSize 2
                result[1] shouldBe Node(meta, emptyMap(), Point(53.12345, 10.2345))
            }

            test("StartNode/Tag/Tag/EndNode emits Node with tags") {
                val meta = testMetadata(2L)
                val result = flowOf(
                    StartOsm("0.6", "t"),
                    StartNode(2L, 54.0, 11.0, meta),
                    Tag("foo", "bar"),
                    Tag("wibble", "wobble"),
                    EndNode(2L)
                ).toOsmData().toList()
                (result[1] as Node).tags shouldBe mapOf("foo" to "bar", "wibble" to "wobble")
            }
        }

        context("Way construction") {
            test("StartWay/NodeRef/NodeRef/Tag/EndWay emits Way with nds and tags") {
                val meta = testMetadata(3L)
                val result = flowOf(
                    StartOsm("0.6", "t"),
                    StartWay(3L, meta),
                    NodeRef(1L),
                    NodeRef(2L),
                    Tag("highway", "motorway"),
                    EndWay(3L)
                ).toOsmData().toList()
                result shouldHaveSize 2
                result[1] shouldBe Way(meta, mapOf("highway" to "motorway"), listOf(1L, 2L))
            }
        }

        context("Relation construction") {
            test("StartRelation/RelationMemberRef/Tag/EndRelation emits Relation") {
                val meta = testMetadata(4L)
                val result = flowOf(
                    StartOsm("0.6", "t"),
                    StartRelation(4L, meta),
                    RelationMemberRef("node", 1L, "thing"),
                    RelationMemberRef("way", 3L, null),
                    RelationMemberRef("relation", 666L, "dark-satanic"),
                    Tag("route", "secret"),
                    EndRelation(4L)
                ).toOsmData().toList()
                result shouldHaveSize 2
                result[1] shouldBe Relation(
                    meta,
                    mapOf("route" to "secret"),
                    listOf(
                        Member(MemberType.NODE, 1L, "thing"),
                        Member(MemberType.WAY, 3L, null),
                        Member(MemberType.RELATION, 666L, "dark-satanic")
                    )
                )
            }
        }

        context("combined document") {
            test("OsmMetadata, Node, Way, Relation emitted in order from full event stream") {
                val nodeMeta = testMetadata(1L)
                val wayMeta = testMetadata(3L)
                val relMeta = testMetadata(4L)
                val result = flowOf(
                    StartOsm("0.6", "t"),
                    BoundsEvent("53.5646", "53.5707", "10.0155", "10.0314"),
                    StartNode(1L, 53.0, 10.0, nodeMeta), EndNode(1L),
                    StartWay(3L, wayMeta), NodeRef(1L), NodeRef(2L), EndWay(3L),
                    StartRelation(4L, relMeta), EndRelation(4L)
                ).toOsmData().toList()
                result shouldHaveSize 4
                result[0].shouldBeInstanceOf<OsmMetadata>()
                result[1].shouldBeInstanceOf<Node>()
                result[2].shouldBeInstanceOf<Way>()
                result[3].shouldBeInstanceOf<Relation>()
            }
        }

        context("property-based: event-level round-trip") {
            test("arbitrary nodes round-trip through event sequence") {
                checkAll(arbNode) { node ->
                    val result = node.toOsmEventFlow().toOsmData().toList()
                    result.filterIsInstance<Node>().first() shouldBe node
                }
            }

            test("arbitrary ways round-trip through event sequence") {
                checkAll(arbWay) { way ->
                    val result = way.toOsmEventFlow().toOsmData().toList()
                    result.filterIsInstance<Way>().first() shouldBe way
                }
            }

            test("arbitrary relations round-trip through event sequence") {
                checkAll(arbRelation) { relation ->
                    val result = relation.toOsmEventFlow().toOsmData().toList()
                    result.filterIsInstance<Relation>().first() shouldBe relation
                }
            }
        }

        context("property-based: XML round-trip") {
            test("arbitrary nodes round-trip through XML serialisation") {
                checkAll(arbNode) { node ->
                    val result = toParseEventFlow(node.toOsmXml())
                        .toOsmElementEvents()
                        .toOsmData()
                        .toList()
                    result.filterIsInstance<Node>().first() shouldBe node
                }
            }

            test("arbitrary ways round-trip through XML serialisation") {
                checkAll(arbWay) { way ->
                    val result = toParseEventFlow(way.toOsmXml())
                        .toOsmElementEvents()
                        .toOsmData()
                        .toList()
                    result.filterIsInstance<Way>().first() shouldBe way
                }
            }

            test("arbitrary relations round-trip through XML serialisation") {
                checkAll(arbRelation) { relation ->
                    val result = toParseEventFlow(relation.toOsmXml())
                        .toOsmElementEvents()
                        .toOsmData()
                        .toList()
                    result.filterIsInstance<Relation>().first() shouldBe relation
                }
            }
        }
    }

    private fun testMetadata(id: Long) = ElementMetadata(
        id = id,
        user = "u",
        uid = 1L,
        timestamp = null,
        visible = true,
        version = 1L,
        changeSet = 1L
    )
}

private fun Node.toOsmEventFlow(): Flow<OsmElementEvent> = flow {
    emit(StartOsm("0.6", "test"))
    emit(StartNode(elementMetadata.id, point.lat, point.lon, elementMetadata))
    tags.forEach { (k, v) -> emit(Tag(k, v)) }
    emit(EndNode(elementMetadata.id))
}

private fun Way.toOsmEventFlow(): Flow<OsmElementEvent> = flow {
    emit(StartOsm("0.6", "test"))
    emit(StartWay(elementMetadata.id, elementMetadata))
    nds.forEach { emit(NodeRef(it)) }
    tags.forEach { (k, v) -> emit(Tag(k, v)) }
    emit(EndWay(elementMetadata.id))
}

private fun Relation.toOsmEventFlow(): Flow<OsmElementEvent> = flow {
    emit(StartOsm("0.6", "test"))
    emit(StartRelation(elementMetadata.id, elementMetadata))
    members.forEach { m ->
        emit(RelationMemberRef(m.type.name.lowercase(), m.id, m.role))
    }
    tags.forEach { (k, v) -> emit(Tag(k, v)) }
    emit(EndRelation(elementMetadata.id))
}
