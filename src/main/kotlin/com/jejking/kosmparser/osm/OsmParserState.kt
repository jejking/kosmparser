package com.jejking.kosmparser.osm

import com.jejking.kosmparser.xml.EndDocument
import com.jejking.kosmparser.xml.EndElement
import com.jejking.kosmparser.xml.SimpleXmlParseEvent
import com.jejking.kosmparser.xml.StartDocument
import com.jejking.kosmparser.xml.StartElement
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * States.
 *
 * <-- ignore start document
 *
 * osm start element. Read out attributes.
 *  state transition -> bounds. Any other element is error
 *
 * bounds
 *   --> start element. Read out attributes. Create bounds
 *   !! Emit OsmMetadata
 *   state transition -> nodes. Only element node is acceptable
 *
 * nodes
 *
 * ways
 *
 * relations
 *
 * end osm element
 *
 * <-- ignore end document
 *
 */

sealed class ParserState {
  abstract fun accept(xmlparseEventSimpleXml: SimpleXmlParseEvent): Pair<ParserState, OsmData?>
}

object ReadingOsmMetadata : ParserState() {

  override fun accept(xmlparseEventSimpleXml: SimpleXmlParseEvent): Pair<ParserState, OsmData?> {
    return when (xmlparseEventSimpleXml) {
      is StartDocument -> this to null
      is StartElement -> readStartElement(xmlparseEventSimpleXml)
      is EndElement -> readEndElement(xmlparseEventSimpleXml)
      is EndDocument -> Finished to null
      else -> throw IllegalStateException()
    }
  }

  private fun readEndElement(endElement: EndElement): Pair<ParserState, OsmData?> {
    return when (endElement.localName) {
      "osm" -> this to null
      else -> throw IllegalStateException()
    }
  }

  private fun readStartElement(startElement: StartElement): Pair<ParserState, OsmData?> {
    return when (startElement.localName) {
      "osm" -> readOsmElement(startElement)
      else -> throw IllegalStateException("Got unexpected start element ${startElement.localName}")
    }
  }

  private fun readOsmElement(osmElement: StartElement): Pair<ParserState, OsmData?> {
    val apiVersion = osmElement.attributes["version"]
    val generator = osmElement.attributes["generator"]
    return ReadingBounds(apiVersion, generator) to null
  }
}

class ReadingBounds(val apiVersion: String?, val generator: String?) : ParserState() {
  override fun accept(xmlparseEventSimpleXml: SimpleXmlParseEvent): Pair<ParserState, OsmData?> {
    return when (xmlparseEventSimpleXml) {
      is StartElement -> readStartElement(xmlparseEventSimpleXml)
      is EndElement -> ReadingNodes() to null
      else -> throw IllegalStateException()
    }
  }

  private fun readStartElement(startElement: StartElement): Pair<ParserState, OsmData?> {
    return when (startElement.localName) {
      "bounds" -> readBoundsElement(startElement)
      else -> throw IllegalStateException("Got unexpected start element ${startElement.localName}")
    }
  }

  private fun readBoundsElement(boundsElement: StartElement): Pair<ParserState, OsmData?> {
    val maxlat = boundsElement.attributes.getOrThrow("maxlat")
    val maxlon = boundsElement.attributes.getOrThrow("maxlon")
    val minlat = boundsElement.attributes.getOrThrow("minlat")
    val minlon = boundsElement.attributes.getOrThrow("minlon")
    val bounds = toBounds(maxlat, maxlon, minlat, minlon)
    return this to OsmMetadata(apiVersion, generator, bounds)
  }

  private fun toBounds(maxlat: String, maxlon: String, minlat: String, minlon: String): Bounds {
    val minPoint = Point(minlat.toDouble(), minlon.toDouble())
    val maxPoint = Point(maxlat.toDouble(), maxlon.toDouble())
    return Bounds(minPoint, maxPoint)
  }
}

class ReadingTags(private val tagReceiver: TagReceiver) : ParserState() {

  private val tagHolder = mutableMapOf<String, String>()

  override fun accept(xmlparseEventSimpleXml: SimpleXmlParseEvent): Pair<ParserState, OsmData?> {
    return when (xmlparseEventSimpleXml) {
      is StartElement -> readStartElement(xmlparseEventSimpleXml)
      is EndElement -> readEndElement(xmlparseEventSimpleXml)
      else -> throw IllegalStateException()
    }
  }

  private fun readEndElement(endElement: EndElement): Pair<ParserState, OsmData?> {
    return when (endElement.localName) {
      "tag" -> this to null
      else -> {
        // supply tags back
        tagReceiver.acceptTags(tagHolder.toMap())
        // "pop" ourselves off and return control to the tag receiver
        tagReceiver.accept(endElement)
      }
    }
  }

  private fun readStartElement(startElement: StartElement): Pair<ParserState, OsmData?> {
    return when (startElement.localName) {
      "tag" -> readTag(startElement)
      else -> throw IllegalStateException("Got unexpected start element ${startElement.localName}")
    }
  }

  private fun readTag(startElement: StartElement): Pair<ParserState, OsmData?> {
    val key = startElement.attributes.getOrThrow("k")
    val value = startElement.attributes.getOrThrow("v")
    tagHolder[key] = value
    return this to null
  }
}

abstract class TagReceiver : ParserState() {

  protected var tags: Map<String, String> = mapOf()

  fun acceptTags(tags: Map<String, String>) {
    this.tags = tags
  }
}

class ReadingNodes : TagReceiver() {

  private lateinit var elementMetadata: ElementMetadata
  private lateinit var point: Point

  override fun accept(xmlparseEventSimpleXml: SimpleXmlParseEvent): Pair<ParserState, OsmData?> {
    return when (xmlparseEventSimpleXml) {
      is StartElement -> readStartElement(xmlparseEventSimpleXml)
      is EndElement -> readEndElement(xmlparseEventSimpleXml)
      else -> throw IllegalStateException()
    }
  }

  private fun readEndElement(endElement: EndElement): Pair<ParserState, OsmData?> {
    return when (endElement.localName) {
      "node" -> {
        val node = Node(elementMetadata = elementMetadata, point = point, tags = tags)
        return ReadingNodes() to node
      }
      // could theoretically come if we have no way or relation elements
      "osm" -> ReadingOsmMetadata.accept(endElement)
      else -> throw IllegalStateException("Got unexpected end element ${endElement.localName}")
    }
  }

  private fun readStartElement(startElement: StartElement): Pair<ParserState, OsmData?> {
    return when (startElement.localName) {
      "node" -> readNodeElement(startElement)
      "way" -> ReadingWays().let { it.accept(startElement) }
      "relation" -> ReadingRelations().let { it.accept(startElement) }
      else -> throw IllegalStateException("Got unexpected start element ${startElement.localName}")
    }
  }

  private fun readNodeElement(startElement: StartElement): Pair<ParserState, OsmData?> {
    // extract elementMetadata, point
    elementMetadata = readElementMetadata(startElement)
    val lat = startElement.attributes.getOrThrow("lat").toDouble()
    val lon = startElement.attributes.getOrThrow("lon").toDouble()
    point = Point(lat = lat, lon = lon)
    return (ReadingTags(this)) to null
  }
}

class ReadingNds(private val readingWays: ReadingWays) : ParserState() {

  private val ndRefs = mutableListOf<Long>()

  override fun accept(xmlparseEventSimpleXml: SimpleXmlParseEvent): Pair<ParserState, OsmData?> {
    return when (xmlparseEventSimpleXml) {
      is StartElement -> readStartElement(xmlparseEventSimpleXml)
      is EndElement -> readEndElement(xmlparseEventSimpleXml)
      else -> throw IllegalStateException()
    }
  }

  private fun readStartElement(startElement: StartElement): Pair<ParserState, OsmData?> {
    return when (startElement.localName) {
      "nd" -> readNdElement(startElement)
      "tag" -> {
        readingWays.acceptNdRefs(this.ndRefs.toList())
        ReadingTags(readingWays).let { it.accept(startElement) }
      }
      else -> return readingWays.accept(startElement)
    }
  }

  private fun readNdElement(startElement: StartElement): Pair<ParserState, OsmData?> {
    try {
      val ndRef = startElement.attributes.getOrThrow("ref").toLong()
      ndRefs.add(ndRef)
      return this to null
    } catch (nfe: NumberFormatException) {
      throw IllegalStateException(nfe)
    }
  }

  private fun readEndElement(endElement: EndElement): Pair<ParserState, OsmData?> {
    return when (endElement.localName) {
      "nd" -> this to null
      "way" -> {
        readingWays.acceptNdRefs(this.ndRefs.toList())
        readingWays.accept(endElement)
      }
      else -> throw IllegalStateException()
    }
  }
}

class ReadingWays : TagReceiver() {

  private lateinit var elementMetadata: ElementMetadata
  private lateinit var ndRefs: List<Long>

  override fun accept(xmlparseEventSimpleXml: SimpleXmlParseEvent): Pair<ParserState, OsmData?> {
    return when (xmlparseEventSimpleXml) {
      is StartElement -> readStartElement(xmlparseEventSimpleXml)
      is EndElement -> readEndElement(xmlparseEventSimpleXml)
      else -> throw IllegalStateException()
    }
  }

  internal fun acceptNdRefs(ndRefs: List<Long>) {
    this.ndRefs = ndRefs
  }

  private fun readEndElement(endElement: EndElement): Pair<ParserState, OsmData?> {
    return when (endElement.localName) {
      "way" -> {
        val way = Way(elementMetadata, tags, ndRefs)
        return ReadingWays() to way
      }
      // could theoretically come if we have no relation elements
      "osm" -> ReadingOsmMetadata.accept(endElement)
      else -> throw IllegalStateException("Got unexpected end element ${endElement.localName}")
    }
  }

  private fun readStartElement(startElement: StartElement): Pair<ParserState, OsmData?> {
    return when (startElement.localName) {
      "way" -> readWayElement(startElement)
      "relation" -> ReadingRelations().let { it.accept(startElement) }
      else -> throw IllegalStateException("Got unexpected start element ${startElement.localName}")
    }
  }

  private fun readWayElement(startElement: StartElement): Pair<ParserState, OsmData?> {
    elementMetadata = readElementMetadata(startElement)
    return (ReadingNds(this)) to null
  }
}

class ReadingMembers(private val readingRelations: ReadingRelations) : ParserState() {

  private val members = mutableListOf<Member>()

  override fun accept(xmlparseEventSimpleXml: SimpleXmlParseEvent): Pair<ParserState, OsmData?> {
    return when (xmlparseEventSimpleXml) {
      is StartElement -> readStartElement(xmlparseEventSimpleXml)
      is EndElement -> readEndElement(xmlparseEventSimpleXml)
      else -> throw IllegalStateException()
    }
  }

  private fun readEndElement(endElement: EndElement): Pair<ParserState, OsmData?> {
    return when (endElement.localName) {
      "member" -> this to null
      "relation" -> {
        readingRelations.acceptMembers(this.members.toList())
        readingRelations.accept(endElement)
      }
      else -> throw IllegalStateException()
    }
  }

  private fun readStartElement(startElement: StartElement): Pair<ParserState, OsmData?> {
    return when (startElement.localName) {
      "member" -> readMemberElement(startElement)
      "tag" -> {
        readingRelations.acceptMembers(this.members.toList())
        ReadingTags(readingRelations).let { it.accept(startElement) }
      }
      else -> return readingRelations.accept(startElement)
    }
  }

  private fun readMemberElement(startElement: StartElement): Pair<ParserState, OsmData?> {
    val typeString = startElement.attributes.getOrThrow("type")
    val refString = startElement.attributes.getOrThrow("ref")
    val roleString = startElement.attributes.getOrThrow("role")

    val type = toMemberType(typeString)
    val ref = try {
      refString.toLong()
    } catch (nfe: NumberFormatException) {
      throw IllegalStateException(nfe)
    }
    val role = roleString.let { if (it.isBlank()) null else it }
    this.members.add(Member(type, ref, role))
    return this to null
  }

  private fun toMemberType(typeString: String): MemberType {
    return when (typeString) {
      "node" -> MemberType.NODE
      "way" -> MemberType.WAY
      "relation" -> MemberType.RELATION
      else -> throw IllegalStateException("unknown member type $typeString")
    }
  }
}

class ReadingRelations : TagReceiver() {

  private lateinit var elementMetadata: ElementMetadata
  private lateinit var members: List<Member>

  override fun accept(xmlparseEventSimpleXml: SimpleXmlParseEvent): Pair<ParserState, OsmData?> {
    return when (xmlparseEventSimpleXml) {
      is StartElement -> readStartElement(xmlparseEventSimpleXml)
      is EndElement -> readEndElement(xmlparseEventSimpleXml)
      else -> throw IllegalStateException()
    }
  }

  private fun readEndElement(endElement: EndElement): Pair<ParserState, OsmData?> {
    return when (endElement.localName) {
      "relation" -> {
        val relation = Relation(elementMetadata, tags, members)
        return ReadingRelations() to relation
      }
      "osm" -> ReadingOsmMetadata.accept(endElement)
      else -> throw IllegalStateException("Got unexpected end element ${endElement.localName}")
    }
  }

  private fun readStartElement(startElement: StartElement): Pair<ParserState, OsmData?> {
    return when (startElement.localName) {
      "relation" -> readRelationElement(startElement)
      else -> throw IllegalStateException("Got unexpected start element ${startElement.localName}")
    }
  }

  private fun readRelationElement(startElement: StartElement): Pair<ParserState, OsmData?> {
    elementMetadata = readElementMetadata(startElement)
    return (ReadingMembers(this)) to null
  }

  internal fun acceptMembers(members: List<Member>) {
    this.members = members
  }
}

object Finished : ParserState() {
  override fun accept(xmlparseEventSimpleXml: SimpleXmlParseEvent): Pair<ParserState, OsmData?> {
    TODO("Not yet implemented")
  }
}

fun readElementMetadata(startElement: StartElement): ElementMetadata {

  val attributes = startElement.attributes
  val id = attributes.getOrThrow("id").toLong()
  val user = attributes["user"]
  val uid = attributes.getOrThrow("uid").toLong()
  val timestampString = attributes.getOrThrow("timestamp")
  val timestamp = Instant.parse(timestampString).let { ZonedDateTime.ofInstant(it, ZoneOffset.UTC) }
  val visible = attributes.getOrThrow("visible").toBoolean()
  val version = attributes.getOrThrow("version").toLong()
  val changeSet = attributes.getOrThrow("changeset").toLong()

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

fun Map<String, String>.getOrThrow(key: String): String = this[key] ?: throw IllegalStateException("Missing key $key")
