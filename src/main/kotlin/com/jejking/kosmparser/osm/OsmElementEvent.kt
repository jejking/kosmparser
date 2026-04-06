package com.jejking.kosmparser.osm

/**
 * Intermediate event representation produced by Stage 1 of the parsing pipeline
 * (`toOsmElementEvents()`). Decouples XML structure interpretation from domain object
 * construction, enabling format-agnostic Stage 2 processing (`toOsmData()`).
 *
 * Event ordering follows OSM XML document structure:
 * - [StartOsm] is always first
 * - [BoundsEvent] is optional and follows [StartOsm] if present
 * - Node/Way/Relation lifecycle events follow in document order
 * - [Tag], [NodeRef], [RelationMemberRef] are emitted within their parent's Start/End pair
 */
sealed class OsmElementEvent {

    /** Emitted when the `<osm>` root element is encountered. Always the first event. */
    data class StartOsm(
        val version: String?,
        val generator: String?
    ) : OsmElementEvent()

    /**
     * Emitted when the `<bounds>` element is encountered. Optional — absent if the
     * source document does not include bounds. Attribute values are raw strings;
     * parsing to [Double] is performed in Stage 2.
     */
    data class BoundsEvent(
        val minlat: String,
        val maxlat: String,
        val minlon: String,
        val maxlon: String
    ) : OsmElementEvent()

    /** Emitted when a `<node>` start element is encountered. */
    data class StartNode(
        val id: Long,
        val lat: Double,
        val lon: Double,
        val metadata: ElementMetadata
    ) : OsmElementEvent()

    /** Emitted when a `</node>` end element is encountered. */
    data class EndNode(val id: Long) : OsmElementEvent()

    /** Emitted when a `<way>` start element is encountered. */
    data class StartWay(
        val id: Long,
        val metadata: ElementMetadata
    ) : OsmElementEvent()

    /** Emitted when an `<nd>` element is encountered inside a `<way>`. */
    data class NodeRef(val ref: Long) : OsmElementEvent()

    /** Emitted when a `</way>` end element is encountered. */
    data class EndWay(val id: Long) : OsmElementEvent()

    /** Emitted when a `<relation>` start element is encountered. */
    data class StartRelation(
        val id: Long,
        val metadata: ElementMetadata
    ) : OsmElementEvent()

    /**
     * Emitted when a `<member>` element is encountered inside a `<relation>`.
     * Named [RelationMemberRef] to avoid shadowing the [Member] domain class.
     */
    data class RelationMemberRef(
        val type: String,
        val ref: Long,
        val role: String?
    ) : OsmElementEvent()

    /** Emitted when a `</relation>` end element is encountered. */
    data class EndRelation(val id: Long) : OsmElementEvent()

    /** Emitted when a `<tag>` element is encountered inside any OSM element. */
    data class Tag(val key: String, val value: String) : OsmElementEvent()
}
