package com.jejking.kosmparser.osm

import com.jejking.kosmparser.xml.SimpleXmlParseEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform

object OsmFlowMapper {

  fun toOsmDataFlow(simpleXmlParseEventFlow: Flow<SimpleXmlParseEvent>): OsmDataFlow {

    var osmParserState: ParserState = ReadingOsmMetadata

    return simpleXmlParseEventFlow.transform { simpleXmlParseEvent ->
      val (newParserState, osmData) = osmParserState.accept(simpleXmlParseEvent)
      osmData?.also { emit(it) }
      osmParserState = newParserState
    }
  }
}
