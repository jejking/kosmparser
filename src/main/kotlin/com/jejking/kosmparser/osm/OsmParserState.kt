package com.jejking.kosmparser.osm

import com.jejking.kosmparser.xml.EndElement
import com.jejking.kosmparser.xml.ParseEvent
import com.jejking.kosmparser.xml.StartDocument
import com.jejking.kosmparser.xml.StartElement
import java.lang.IllegalStateException

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
    abstract fun accept(xmlparseEvent: ParseEvent): Pair<ParserState, OsmData?>
}



class ReadingOsmMetadata: ParserState() {

    private var seenOsmElement = false
    private var apiVersion = ""
    private var generator = ""

    override fun accept(xmlparseEvent: ParseEvent): Pair<ParserState, OsmData?> {
        return when (xmlparseEvent) {
            is StartDocument -> this to null
            is StartElement -> readStartElement(xmlparseEvent)
            is EndElement -> readEndElement(xmlparseEvent)
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
            else -> throw IllegalStateException()
        }
    }

    private fun readOsmElement(osmElement: StartElement): Pair<ParserState, OsmData?> {
        this.apiVersion = osmElement.attributes.getOrThrow("version")
        this.generator = osmElement.attributes.getOrThrow("generator")
        this.seenOsmElement = true
        return this to null
    }
}


class ReadingTags: ParserState() {
    override fun accept(xmlparseEvent: ParseEvent): Pair<ParserState, OsmData?> {
        TODO("Not yet implemented")
    }
}

class ReadingNodes: ParserState() {
    override fun accept(xmlparseEvent: ParseEvent): Pair<ParserState, OsmData?> {
        TODO("Not yet implemented")
    }
}

class ReadingWays: ParserState() {
    override fun accept(xmlparseEvent: ParseEvent): Pair<ParserState, OsmData?> {
        TODO("Not yet implemented")
    }
}

class ReadingRelations: ParserState() {
    override fun accept(xmlparseEvent: ParseEvent): Pair<ParserState, OsmData?> {
        TODO("Not yet implemented")
    }
}

class Finished: ParserState() {
    override fun accept(xmlparseEvent: ParseEvent): Pair<ParserState, OsmData?> {
        TODO("Not yet implemented")
    }
}

fun Map<String, String>.getOrThrow(key: String): String = this[key] ?: throw IllegalStateException("Missing key $key")
