package com.jejking.kosmparser.osm

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
    var startOsm: StartOsm? = null
    var boundsEvent: BoundsEvent? = null
    var metadataEmitted = false

    var currentNodeStart: StartNode? = null
    val currentNodeTags = mutableMapOf<String, String>()

    var currentWayStart: StartWay? = null
    val currentWayNds = mutableListOf<Long>()
    val currentWayTags = mutableMapOf<String, String>()

    var currentRelationStart: StartRelation? = null
    val currentRelationMembers = mutableListOf<Member>()
    val currentRelationTags = mutableMapOf<String, String>()

    collect { event ->
        when (event) {
            is StartOsm -> startOsm = event
            is BoundsEvent -> boundsEvent = event

            is StartNode -> {
                if (!metadataEmitted) {
                    emit(assembleOsmMetadata(startOsm, boundsEvent))
                    metadataEmitted = true
                }
                currentNodeStart = event
                currentNodeTags.clear()
            }
            is Tag -> when {
                currentNodeStart != null -> currentNodeTags[event.key] = event.value
                currentWayStart != null -> currentWayTags[event.key] = event.value
                currentRelationStart != null -> currentRelationTags[event.key] = event.value
            }
            is EndNode -> {
                val start = requireNotNull(currentNodeStart)
                emit(Node(start.metadata, currentNodeTags.toMap(), Point(start.lat, start.lon)))
                currentNodeStart = null
                currentNodeTags.clear()
            }

            is StartWay -> {
                if (!metadataEmitted) {
                    emit(assembleOsmMetadata(startOsm, boundsEvent))
                    metadataEmitted = true
                }
                currentWayStart = event
                currentWayNds.clear()
                currentWayTags.clear()
            }
            is NodeRef -> currentWayNds.add(event.ref)
            is EndWay -> {
                val start = requireNotNull(currentWayStart)
                emit(Way(start.metadata, currentWayTags.toMap(), currentWayNds.toList()))
                currentWayStart = null
                currentWayNds.clear()
                currentWayTags.clear()
            }

            is StartRelation -> {
                if (!metadataEmitted) {
                    emit(assembleOsmMetadata(startOsm, boundsEvent))
                    metadataEmitted = true
                }
                currentRelationStart = event
                currentRelationMembers.clear()
                currentRelationTags.clear()
            }
            is RelationMemberRef -> {
                val type = when (event.type) {
                    "node" -> MemberType.NODE
                    "way" -> MemberType.WAY
                    "relation" -> MemberType.RELATION
                    else -> error("Unknown member type: ${event.type}")
                }
                currentRelationMembers.add(Member(type, event.ref, event.role))
            }
            is EndRelation -> {
                val start = requireNotNull(currentRelationStart)
                emit(Relation(start.metadata, currentRelationTags.toMap(), currentRelationMembers.toList()))
                currentRelationStart = null
                currentRelationMembers.clear()
                currentRelationTags.clear()
            }
        }
    }

    if (!metadataEmitted) {
        emit(assembleOsmMetadata(startOsm, boundsEvent))
    }
}

private fun assembleOsmMetadata(startOsm: StartOsm?, boundsEvent: BoundsEvent?): OsmMetadata {
    val bounds = boundsEvent?.let {
        Bounds(
            minPoint = Point(it.minlat.toDouble(), it.minlon.toDouble()),
            maxPoint = Point(it.maxlat.toDouble(), it.maxlon.toDouble())
        )
    }
    return OsmMetadata(startOsm?.version, startOsm?.generator, bounds)
}
