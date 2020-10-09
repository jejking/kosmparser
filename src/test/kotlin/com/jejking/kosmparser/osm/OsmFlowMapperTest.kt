package com.jejking.kosmparser.osm

import com.jejking.kosmparser.io.asFlow
import com.jejking.kosmparser.osm.OsmFlowMapper.toOsmDataFlow
import com.jejking.kosmparser.util.getPath
import com.jejking.kosmparser.util.openFileChannel
import com.jejking.kosmparser.xml.SimpleXmlParseEvent
import com.jejking.kosmparser.xml.XmlFlowMapper.toParseEvents
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@ExperimentalCoroutinesApi
class OsmFlowMapperTest : FunSpec() {

  init {
    context("correct osm xml") {
      test("should emit an osmmetadata object with version, generator, bounds").config(enabled = false) {
        val bounds = Bounds(Point(53.5646, 10.0155), Point(53.5707, 10.0314))
        val osmMetadata = OsmMetadata("0.6", "manual", bounds)

        val osmDataFlow = toOsmDataFlow(xmlParseEvents("test1.osm"))

        runBlocking {
          osmDataFlow.first() shouldBe osmMetadata
        }
      }
    }
  }

  fun xmlParseEvents(path: String): Flow<SimpleXmlParseEvent> {
    val testFilePath = getPath(path)
    val fileChannel = autoClose(openFileChannel(testFilePath))

    return toParseEvents(fileChannel.asFlow())
  }
}
