package com.jejking.kosmparser.osm

import com.jejking.kosmparser.io.asFlow
import com.jejking.kosmparser.osm.OsmFlow.toOsmDataFlow
import com.jejking.kosmparser.util.getPath
import com.jejking.kosmparser.util.openFileChannel
import com.jejking.kosmparser.xml.ParseEvent
import com.jejking.kosmparser.xml.XmlParser.toParseEvents
import io.kotlintest.shouldBe
import io.kotlintest.specs.FunSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class OsmFlowTest: FunSpec() {

    init {
        context("correct osm xml") {
            test("should emit an osmmetadata object with version, generator, bounds") {
                val bounds = Bounds(Point(53.5646, 10.0155), Point(53.5707, 10.0314))
                val osmMetadata = OsmMetadata("0.6", "manual", bounds)

                val osmDataFlow = toOsmDataFlow(xmlParseEvents("test1.osm"))

                runBlocking {
                    osmDataFlow.first() shouldBe osmMetadata
                }
            }
        }
    }

    fun xmlParseEvents(path: String): Flow<ParseEvent> {
        val testFilePath = getPath(path)
        val fileChannel = autoClose(openFileChannel(testFilePath))

        return toParseEvents(fileChannel.asFlow())
    }
}