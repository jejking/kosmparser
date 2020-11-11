package com.jejking.kosmparser.osm

import kotlinx.coroutines.flow.Flow
import java.time.ZonedDateTime

const val MIN_LAT = -90.0
const val MAX_LAT = 90.0
const val MIN_LON = -180.0
const val MAX_LON = 180.0

val LAT_RANGE = MIN_LAT.rangeTo(MAX_LAT)
val LON_RANGE = MIN_LON.rangeTo(MAX_LON)

/**
 * Represents a point on the Earth's surface using the WGS84 projection.
 *
 * @param lat latitude coordinate in degrees between -90 and 90. Positive is north of the equator.
 * @param lon longitude coordinate in degrees between -180 and 180. Positive is east of Greenwich.
 * @throws IllegalStateException if values outside permitted range are supplied
 */
data class Point(val lat: Double, val lon: Double) {
  init {
    check(LAT_RANGE.contains(lat)) { "$lat out of range (-90, 90)" }
    check(LON_RANGE.contains(lon)) { "$lon out of range (-180, 180)" }
  }
}

/**
 * Represents the geographical bounds of an OSM XML representation.
 *
 * @param minPoint - smallest latitude and longitude pair
 * @param maxPoint - largest latitutde and longitude pair
 */
data class Bounds(val minPoint: Point, val maxPoint: Point)

/**
 * Represents [attributes that are common](https://wiki.openstreetmap.org/wiki/Elements#Common_attributes)
 * to [Node], [Way] and [Relation] elements.
 *
 * @param id identifier
 * @param user display name of user who last changed object
 * @param uid numeric identifier of user who last changed the object
 * @param timestamp time of last modification
 * @param visible whether has been deleted from the database
 * @param version edit version of the object
 * @param changeSet most recent changeset number in which object was created or updated
 */
data class ElementMetadata(
  val id: Long,
  val user: String?,
  val uid: Long,
  val timestamp: ZonedDateTime,
  val visible: Boolean = true,
  val version: Long,
  val changeSet: Long
)

sealed class OsmData

/**
 * OSM Map [Element](https://wiki.openstreetmap.org/wiki/Elements) -
 * sealed supertype for [Node], [Way] and [Relation].
 */
sealed class Element : OsmData() {
  /**
   * Common element metadata.
   */
  abstract val elementMetadata: ElementMetadata

  /**
   * Element tags - may be empty.
   */
  abstract val tags: Map<String, String>
}

/**
 * Represents an [OSM Node](https://wiki.openstreetmap.org/wiki/Node).
 *
 * @param elementMetadata common element metadata
 * @param tags element tags
 * @param point the point that is functioning as a Node
 */
data class Node(
  override val elementMetadata: ElementMetadata,
  override val tags: Map<String, String>,
  val point: Point
) : Element()

const val MAX_NODES_IN_WAY = 2000
const val MIN_NODES_IN_WAY = 2


/**
 * Represents an [OSM Way](https://wiki.openstreetmap.org/wiki/Way). It would normally have
 * at last one tag or be included in a [Relation].
 *
 * @param elementMetadata common element metadata
 * @param tags element tags
 * @param nds list of node identifiers. Should be between 2 and 2000 identifiers, but faulty
 * ways with zero or one node identifiers will be accepted.
 * @throws IllegalStateException if list of more than 2000 node identifiers is presented
 */
data class Way(
  override val elementMetadata: ElementMetadata,
  override val tags: Map<String, String>,
  val nds: List<Long>
) : Element() {


  init {
    check(nds.size <= MAX_NODES_IN_WAY) { "nds exceeds $MAX_NODES_IN_WAY limit with ${nds.size} elements." }
  }

  /**
   * Declares if way is faulty - i.e has less than 2 node identifiers.
   */
  fun isFaulty(): Boolean = nds.size < MIN_NODES_IN_WAY

  /**
   * Declares if way is closed - i.e. has 2 or more identifiers where the first and the
   * last identifier are the same. Note - a faulty way is not considered closed.
   */
  fun isClosed(): Boolean = !isFaulty() && nds.first() == nds.last()
}

/**
 * Describes type of a [Member] in a [Relation].
 */
enum class MemberType {
  NODE, WAY, RELATION
}

/**
 * Represents a member in a [Relation].
 *
 * @param type the type of the member
 * @param id identifier of the member
 * @param role role of the member in the [Relation]
 */
data class Member(val type: MemberType, val id: Long, val role: String?)

/**
 * Represents an [OSM Relation](https://wiki.openstreetmap.org/wiki/Relation).
 *
 * @param elementMetadata common element metadata
 * @param tags element tags
 * @param members list of relation members
 */
data class Relation(
  override val elementMetadata: ElementMetadata,
  override val tags: Map<String, String>,
  val members: List<Member>
) : Element()

/**
 * Represents metadata about an OSM XML.
 *
 * @param version OSM API version
 * @param generator application generating the XML
 * @param bounds geographical bounds of the area described
 */
data class OsmMetadata(val version: String?, val generator: String?, val bounds: Bounds?) : OsmData()

/**
 * Represents the data produced whilst reading an
 * [Open Street Map XML](https://wiki.openstreetmap.org/wiki/OSM_XML).
 *
 * It is to be expected that the first object will a single [OsmMetadata] instance.
 *
 * Following that, the elements will come in the regular order:
 *  1 [Node] elements
 *  2 [Way] elements
 *  3 [Relation] elements
 *
 * As per the documentation, there are no guarantees that all such elements will be present
 * of that any particular ordering within the blocks will be followed.
 */
typealias OsmDataFlow = Flow<OsmData>
