package com.jejking.kosmparser.util

import com.jejking.kosmparser.io.asFlow
import com.jejking.kosmparser.xml.XmlParser.toParseEvents
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import java.lang.Thread.currentThread
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

fun openFileChannel(path: Path) = AsynchronousFileChannel.open(path, StandardOpenOption.READ)

fun getPath(path: String): Path {
    val url = currentThread().contextClassLoader.getResource(path)
    return Paths.get(url.toURI())
}


