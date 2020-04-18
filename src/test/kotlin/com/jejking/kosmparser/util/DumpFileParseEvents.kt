package com.jejking.kosmparser.util

import com.jejking.kosmparser.io.asFlow
import com.jejking.kosmparser.xml.Characters
import com.jejking.kosmparser.xml.XmlParser.toParseEvents
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking

object DumpFileParseEvents {

    @JvmStatic
    fun main(args: Array<String>) {
        xmlParseEvents()
    }

    fun xmlParseEvents(): Unit {
        val testFilePath = getPath("test1.osm")
        val fileChannel = openFileChannel(testFilePath)

        runBlocking {
            toParseEvents(fileChannel.asFlow())
                    .filter { !(it is Characters && it.text.isBlank()) }
                    .collect { println(it) }
        }
    }
}