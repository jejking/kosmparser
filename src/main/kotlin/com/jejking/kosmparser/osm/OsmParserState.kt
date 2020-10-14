package com.jejking.kosmparser.osm

import com.jejking.kosmparser.xml.EndElement
import com.jejking.kosmparser.xml.SimpleXmlParseEvent
import com.jejking.kosmparser.xml.StartDocument
import com.jejking.kosmparser.xml.StartElement

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

class ReadingBounds(val apiVersion: String?, val generator: String?): ParserState() {
  override fun accept(xmlparseEventSimpleXml: SimpleXmlParseEvent): Pair<ParserState, OsmData?> {
    return when (xmlparseEventSimpleXml) {
      is StartElement -> readStartElement(xmlparseEventSimpleXml)
      is EndElement -> readEndElement(xmlparseEventSimpleXml)
      else -> throw IllegalStateException()
    }
  }

  private fun readEndElement(endElement: EndElement): Pair<ParserState, OsmData?> {
    return ReadingNodes() to null
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

class ReadingTags : ParserState() {
  override fun accept(xmlparseEventSimpleXml: SimpleXmlParseEvent): Pair<ParserState, OsmData?> {
    TODO("Not yet implemented")
  }
}

class ReadingNodes : ParserState() {
  override fun accept(xmlparseEventSimpleXml: SimpleXmlParseEvent): Pair<ParserState, OsmData?> {
    TODO("Not yet implemented")
  }
}

class ReadingWays : ParserState() {
  override fun accept(xmlparseEventSimpleXml: SimpleXmlParseEvent): Pair<ParserState, OsmData?> {
    TODO("Not yet implemented")
  }
}

class ReadingRelations : ParserState() {
  override fun accept(xmlparseEventSimpleXml: SimpleXmlParseEvent): Pair<ParserState, OsmData?> {
    TODO("Not yet implemented")
  }
}

class Finished : ParserState() {
  override fun accept(xmlparseEventSimpleXml: SimpleXmlParseEvent): Pair<ParserState, OsmData?> {
    TODO("Not yet implemented")
  }
}

fun Map<String, String>.getOrThrow(key: String): String = this[key] ?: throw IllegalStateException("Missing key $key")
