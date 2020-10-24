package com.jejking.kosmparser.util

import com.jejking.kosmparser.io.asFlow
import com.jejking.kosmparser.xml.Characters
import com.jejking.kosmparser.xml.XmlFlowMapper.toParseEvents
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.runBlocking

@ExperimentalCoroutinesApi
object DumpFileParseEvents {

  @JvmStatic
  fun main(args: Array<String>) {
    xmlParseEvents()
  }

  fun xmlParseEvents() {
    val testFilePath = getPath("test1.osm")
    val fileChannel = openFileChannel(testFilePath)

    runBlocking {
      toParseEvents(fileChannel.asFlow())
        .filter { !(it is Characters && it.text.isBlank()) }
        .collect { println(it) }
    }
  }
}
