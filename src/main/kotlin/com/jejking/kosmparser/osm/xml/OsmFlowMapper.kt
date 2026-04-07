package com.jejking.kosmparser.osm.xml

import com.jejking.kosmparser.io.asFlow
import com.jejking.kosmparser.io.openAsynchronousFileChannelForRead
import com.jejking.kosmparser.osm.OsmDataFlow
import com.jejking.kosmparser.xml.SimpleXmlParseEvent
import com.jejking.kosmparser.xml.XmlFlowMapper.toParseEvents
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import java.net.URI
import java.net.http.HttpClient
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.Path

@kotlinx.coroutines.ExperimentalCoroutinesApi
object OsmFlowMapper {

  /**
   * Converts a flow of XML parse events to a [OsmDataFlow] using the two-stage pipeline:
   * `toOsmElementEvents()` then `toOsmData()`.
   */
  fun toOsmDataFlow(simpleXmlParseEventFlow: Flow<SimpleXmlParseEvent>): OsmDataFlow =
    simpleXmlParseEventFlow.toOsmElementEvents().toOsmData()

  /**
   * Renders a file [Path] as [OsmDataFlow]. Takes care of closing the underlying
   * [AsynchronousFileChannel].
   */
  fun Path.toOsmDataFlow(bufferSize: Int = 1024): OsmDataFlow {
    val fileChannel = this.openAsynchronousFileChannelForRead()
    val osmDataFlow = toOsmDataFlow(toParseEvents(fileChannel.asFlow(bufferSize)))
    osmDataFlow.onCompletion { fileChannel.close() }
    return osmDataFlow
  }

  /**
   * Renders as URI as [OsmDataFlow].
   */
  fun URI.toOsmDataFlow(): OsmDataFlow {
    val httpClient = HttpClient.newBuilder()
      .version(HttpClient.Version.HTTP_2)
      .build()
    return toOsmDataFlow(toParseEvents(this.asFlow(httpClient)))
  }
}
