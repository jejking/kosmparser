package com.jejking.kosmparser.osm.xml

import com.jejking.kosmparser.osm.Bounds
import com.jejking.kosmparser.osm.Member
import com.jejking.kosmparser.osm.MemberType
import com.jejking.kosmparser.osm.Node
import com.jejking.kosmparser.osm.OsmData
import com.jejking.kosmparser.osm.OsmDataFlow
import com.jejking.kosmparser.osm.OsmMetadata
import com.jejking.kosmparser.osm.Point
import com.jejking.kosmparser.osm.Relation
import com.jejking.kosmparser.osm.Way
import com.jejking.kosmparser.osm.xml.OsmElementEvent.BoundsEvent
import com.jejking.kosmparser.osm.xml.OsmElementEvent.EndNode
import com.jejking.kosmparser.osm.xml.OsmElementEvent.EndRelation
import com.jejking.kosmparser.osm.xml.OsmElementEvent.EndWay
import com.jejking.kosmparser.osm.xml.OsmElementEvent.NodeRef
import com.jejking.kosmparser.osm.xml.OsmElementEvent.RelationMemberRef
import com.jejking.kosmparser.osm.xml.OsmElementEvent.StartNode
import com.jejking.kosmparser.osm.xml.OsmElementEvent.StartOsm
import com.jejking.kosmparser.osm.xml.OsmElementEvent.StartRelation
import com.jejking.kosmparser.osm.xml.OsmElementEvent.StartWay
import com.jejking.kosmparser.osm.xml.OsmElementEvent.Tag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Converts a [Flow] of [OsmElementEvent] to a [OsmDataFlow].
 *
 * This is Stage 2 of the two-stage OSM XML parsing pipeline:
 * ```
 * Flow<SimpleXmlParseEvent> → Flow<OsmElementEvent> → Flow<OsmData>
 * ```
 *
 * - [OsmMetadata] is always the first emitted item, assembled from the mandatory [StartOsm]
 *   event and the optional [BoundsEvent]. It is emitted immediately before the first domain
 *   element, or at the end of the stream if no domain elements are present.
 * - Tags, node references, and relation members are accumulated within their enclosing
 *   element's Start/End pair and attached to the constructed domain object.
 * - [RelationMemberRef.type] is a lowercase string (`"node"`, `"way"`, `"relation"`); any
 *   other value causes an error.
 */
fun Flow<OsmElementEvent>.toOsmData(): OsmDataFlow = flow {
    val accumulator = OsmDataAccumulator()
    collect { event -> accumulator.handle(event).forEach { emit(it) } }
    accumulator.finalMetadata()?.let { emit(it) }
}

/**
 * Accumulates [OsmElementEvent] values and assembles them into [OsmData] domain objects.
 * Each call to [handle] returns zero or more objects ready to emit downstream.
 */
@Suppress("TooManyFunctions")
internal class OsmDataAccumulator {

    private var startOsm: StartOsm? = null
    private var boundsEvent: BoundsEvent? = null
    private var metadataEmitted = false

    private var currentNodeStart: StartNode? = null
    private val currentNodeTags = mutableMapOf<String, String>()

    private var currentWayStart: StartWay? = null
    private val currentWayNds = mutableListOf<Long>()
    private val currentWayTags = mutableMapOf<String, String>()

    private var currentRelationStart: StartRelation? = null
    private val currentRelationMembers = mutableListOf<Member>()
    private val currentRelationTags = mutableMapOf<String, String>()

    fun handle(event: OsmElementEvent): List<OsmData> = when (event) {
        is StartOsm -> { startOsm = event; emptyList() }
        is BoundsEvent -> { boundsEvent = event; emptyList() }
        is StartNode -> handleStartNode(event)
        is Tag -> { routeTag(event); emptyList() }
        is EndNode -> listOf(buildNode())
        is StartWay -> handleStartWay(event)
        is NodeRef -> { currentWayNds.add(event.ref); emptyList() }
        is EndWay -> listOf(buildWay())
        is StartRelation -> handleStartRelation(event)
        is RelationMemberRef -> { addMember(event); emptyList() }
        is EndRelation -> listOf(buildRelation())
    }

    fun finalMetadata(): OsmMetadata? = if (!metadataEmitted) assembleOsmMetadata() else null

    private fun emitMetadataIfNeeded(): List<OsmData> = if (!metadataEmitted) {
        metadataEmitted = true
        listOf(assembleOsmMetadata())
    } else emptyList()

    private fun assembleOsmMetadata(): OsmMetadata {
        val bounds = boundsEvent?.let {
            Bounds(
                minPoint = Point(it.minlat.toDouble(), it.minlon.toDouble()),
                maxPoint = Point(it.maxlat.toDouble(), it.maxlon.toDouble())
            )
        }
        return OsmMetadata(startOsm?.version, startOsm?.generator, bounds)
    }

    private fun handleStartNode(event: StartNode): List<OsmData> {
        val result = emitMetadataIfNeeded()
        currentNodeStart = event
        currentNodeTags.clear()
        return result
    }

    private fun buildNode(): Node {
        val start = requireNotNull(currentNodeStart)
        val node = Node(start.metadata, currentNodeTags.toMap(), Point(start.lat, start.lon))
        currentNodeStart = null
        currentNodeTags.clear()
        return node
    }

    private fun handleStartWay(event: StartWay): List<OsmData> {
        val result = emitMetadataIfNeeded()
        currentWayStart = event
        currentWayNds.clear()
        currentWayTags.clear()
        return result
    }

    private fun buildWay(): Way {
        val start = requireNotNull(currentWayStart)
        val way = Way(start.metadata, currentWayTags.toMap(), currentWayNds.toList())
        currentWayStart = null
        currentWayNds.clear()
        currentWayTags.clear()
        return way
    }

    private fun handleStartRelation(event: StartRelation): List<OsmData> {
        val result = emitMetadataIfNeeded()
        currentRelationStart = event
        currentRelationMembers.clear()
        currentRelationTags.clear()
        return result
    }

    private fun addMember(event: RelationMemberRef) {
        val type = when (event.type) {
            "node" -> MemberType.NODE
            "way" -> MemberType.WAY
            "relation" -> MemberType.RELATION
            else -> error("Unknown member type: ${event.type}")
        }
        currentRelationMembers.add(Member(type, event.ref, event.role))
    }

    private fun buildRelation(): Relation {
        val start = requireNotNull(currentRelationStart)
        val relation = Relation(
            start.metadata,
            currentRelationTags.toMap(),
            currentRelationMembers.toList()
        )
        currentRelationStart = null
        currentRelationMembers.clear()
        currentRelationTags.clear()
        return relation
    }

    private fun routeTag(event: Tag) {
        when {
            currentNodeStart != null -> currentNodeTags[event.key] = event.value
            currentWayStart != null -> currentWayTags[event.key] = event.value
            currentRelationStart != null -> currentRelationTags[event.key] = event.value
        }
    }
}
