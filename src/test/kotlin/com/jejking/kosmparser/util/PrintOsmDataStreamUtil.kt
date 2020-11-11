package com.jejking.kosmparser.util

import com.jejking.kosmparser.io.asFlow
import com.jejking.kosmparser.osm.OsmFlowMapper.toOsmDataFlow
import com.jejking.kosmparser.xml.XmlFlowMapper.toParseEvents
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import java.net.URI
import java.net.http.HttpClient
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption

@ExperimentalCoroutinesApi
fun main(args: Array<String>) {

  val arg = args[0]
  if (arg.startsWith("http")) {
    parseAndPrint(toUrlFlow(arg))
  } else {
    parseAndPrint(toFileFlow(arg))
  }
}

private fun toUrlFlow(url: String): Flow<ByteArray> {
  val httpClient = HttpClient.newBuilder()
    .version(HttpClient.Version.HTTP_2)
    .build()
  return URI.create(url).asFlow(httpClient)
}

private fun toFileFlow(filePath: String): Flow<ByteArray> {
  val path = Path.of(filePath)
  val asyncFileChannel = AsynchronousFileChannel.open(path, StandardOpenOption.READ)
  return asyncFileChannel.asFlow(1024)
}

private fun parseAndPrint(fileFlow: Flow<ByteArray>) {
  val parseEventFlow = toParseEvents(fileFlow)
  val osmFlow = toOsmDataFlow(parseEventFlow)

  runBlocking {
    osmFlow
      .onEach { println(it) }
      .collect()
  }
}
