package com.jejking.kosmparser.osm

import com.jejking.kosmparser.xml.ParseEvent
import kotlinx.coroutines.flow.Flow

object OsmFlow {

  fun toOsmDataFlow(parseEventFlow: Flow<ParseEvent>): Flow<OsmData> {
    return TODO()
  }
}
