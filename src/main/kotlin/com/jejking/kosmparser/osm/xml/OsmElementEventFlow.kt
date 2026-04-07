package com.jejking.kosmparser.osm.xml

import com.jejking.kosmparser.osm.ElementMetadata
import com.jejking.kosmparser.xml.EndDocument
import com.jejking.kosmparser.xml.EndElement
import com.jejking.kosmparser.xml.SimpleXmlParseEvent
import com.jejking.kosmparser.xml.StartElement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Converts a [Flow] of [SimpleXmlParseEvent] to a [Flow] of [OsmElementEvent].
 *
 * This is Stage 1 of the two-stage OSM XML parsing pipeline:
 * ```
 * Flow<SimpleXmlParseEvent> → Flow<OsmElementEvent> → Flow<OsmData>
 * ```
 *
 * - Unknown XML elements are silently skipped (no event emitted).
 * - [EndDocument] completes the flow naturally; no event is emitted for it.
 * - [com.jejking.kosmparser.xml.Characters], [com.jejking.kosmparser.xml.Space],
 *   [com.jejking.kosmparser.xml.Comment] and similar events are silently ignored.
 */
fun Flow<SimpleXmlParseEvent>.toOsmElementEvents(): Flow<OsmElementEvent> {
    val context = ParserContext()
    return transform { event ->
        context.handle(event).forEach { emit(it) }
    }
}

/**
 * Stateful context that interprets a stream of [SimpleXmlParseEvent] into [OsmElementEvent] values.
 *
 * [handle] returns a list (possibly empty) for each input event. The caller is responsible
 * for emitting each item into the downstream flow.
 *
 * Unknown elements produce an empty list; they are silently skipped.
 */
internal class ParserContext {

    private var currentId: Long = 0L

    fun handle(event: SimpleXmlParseEvent): List<OsmElementEvent> = when (event) {
        is StartElement -> handleStartElement(event)
        is EndElement -> handleEndElement(event)
        is EndDocument -> emptyList()
        else -> emptyList()
    }

    private fun handleStartElement(event: StartElement): List<OsmElementEvent> {
        val attrs = event.attributes
        return when (event.localName) {
            "osm" -> listOf(
                OsmElementEvent.StartOsm(
                    version = attrs["version"],
                    generator = attrs["generator"]
                )
            )
            "bounds" -> listOf(
                OsmElementEvent.BoundsEvent(
                    minlat = attrs.getOrThrow("minlat"),
                    maxlat = attrs.getOrThrow("maxlat"),
                    minlon = attrs.getOrThrow("minlon"),
                    maxlon = attrs.getOrThrow("maxlon")
                )
            )
            "node" -> {
                val id = attrs.getOrThrow("id").toLong()
                currentId = id
                listOf(
                    OsmElementEvent.StartNode(
                        id = id,
                        lat = attrs.getOrThrow("lat").toDouble(),
                        lon = attrs.getOrThrow("lon").toDouble(),
                        metadata = readElementMetadata(event)
                    )
                )
            }
            "way" -> {
                val id = attrs.getOrThrow("id").toLong()
                currentId = id
                listOf(OsmElementEvent.StartWay(id = id, metadata = readElementMetadata(event)))
            }
            "relation" -> {
                val id = attrs.getOrThrow("id").toLong()
                currentId = id
                listOf(OsmElementEvent.StartRelation(id = id, metadata = readElementMetadata(event)))
            }
            "tag" -> listOf(
                OsmElementEvent.Tag(
                    key = attrs.getOrThrow("k"),
                    value = attrs.getOrThrow("v")
                )
            )
            "nd" -> listOf(OsmElementEvent.NodeRef(ref = attrs.getOrThrow("ref").toLong()))
            "member" -> {
                val role = attrs["role"]?.let { if (it.isBlank()) null else it }
                listOf(
                    OsmElementEvent.RelationMemberRef(
                        type = attrs.getOrThrow("type"),
                        ref = attrs.getOrThrow("ref").toLong(),
                        role = role
                    )
                )
            }
            else -> emptyList()
        }
    }

    private fun handleEndElement(event: EndElement): List<OsmElementEvent> = when (event.localName) {
        "node" -> listOf(OsmElementEvent.EndNode(id = currentId))
        "way" -> listOf(OsmElementEvent.EndWay(id = currentId))
        "relation" -> listOf(OsmElementEvent.EndRelation(id = currentId))
        else -> emptyList()
    }
}

internal fun readElementMetadata(startElement: StartElement): ElementMetadata {
    val attributes = startElement.attributes
    val id = attributes.getOrThrow("id").toLong()
    val user = attributes["user"]
    val uid = attributes["uid"]?.toLong()
    val timestamp = attributes["timestamp"]
        ?.let { ZonedDateTime.ofInstant(Instant.parse(it), ZoneOffset.UTC) }
    val visible = attributes["visible"]?.toBoolean() ?: true
    val version = attributes["version"]?.toLong()
    val changeSet = attributes["changeset"]?.toLong()
    return ElementMetadata(
        id = id,
        user = user,
        uid = uid,
        timestamp = timestamp,
        visible = visible,
        version = version,
        changeSet = changeSet
    )
}

internal fun Map<String, String>.getOrThrow(key: String): String = this[key] ?: error("Missing key $key")
