package com.jejking.kosmparser.util

import com.jejking.kosmparser.osm.OsmDataFlow
import com.jejking.kosmparser.osm.OsmFlowMapper.toOsmDataFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import java.net.URI
import java.nio.file.Path

@ExperimentalCoroutinesApi
fun main(args: Array<String>) {

  val arg = args[0]
  if (arg.startsWith("http")) {
    print(URI.create(arg).toOsmDataFlow())
  } else {
    print(Path.of(arg).toOsmDataFlow())
  }
}

private fun print(osmFlow: OsmDataFlow) {
  runBlocking {
    osmFlow
      .onEach { println(it) }
      .collect()
  }
}
