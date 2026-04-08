package com.jejking.kosmparser.osm.pbf

import com.jejking.kosmparser.osm.OsmDataFlow
import crosby.binary.Osmformat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path

/**
 * Reads a PBF file at this [Path] as an [OsmDataFlow].
 *
 * Emits [com.jejking.kosmparser.osm.OsmMetadata] first (from the OSMHeader blob), followed by
 * [com.jejking.kosmparser.osm.Node], [com.jejking.kosmparser.osm.Way], and
 * [com.jejking.kosmparser.osm.Relation] objects decoded from OSMData blobs.
 *
 * Blocking I/O is confined to [Dispatchers.IO] via [flowOn].
 */
fun Path.toOsmDataFlow(): OsmDataFlow = flow {
    Files.newInputStream(this@toOsmDataFlow).use { inputStream ->
        for (rawBlob in inputStream.readBlobSequence()) {
            val bytes = rawBlob.blob.decompress()
            when (rawBlob.type) {
                "OSMHeader" -> emit(Osmformat.HeaderBlock.parseFrom(bytes).toOsmMetadata())
                "OSMData" -> Osmformat.PrimitiveBlock.parseFrom(bytes).toOsmDataList()
                    .forEach { emit(it) }
            }
        }
    }
}.flowOn(Dispatchers.IO)

/**
 * Downloads a PBF resource from this [URI] and streams it as an [OsmDataFlow].
 *
 * Blocking I/O is confined to [Dispatchers.IO] via [flowOn].
 */
fun URI.toOsmDataFlow(): OsmDataFlow = flow {
    val client = HttpClient.newHttpClient()
    val request = HttpRequest.newBuilder(this@toOsmDataFlow).GET().build()
    val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
    response.body().use { inputStream ->
        for (rawBlob in inputStream.readBlobSequence()) {
            val bytes = rawBlob.blob.decompress()
            when (rawBlob.type) {
                "OSMHeader" -> emit(Osmformat.HeaderBlock.parseFrom(bytes).toOsmMetadata())
                "OSMData" -> Osmformat.PrimitiveBlock.parseFrom(bytes).toOsmDataList()
                    .forEach { emit(it) }
            }
        }
    }
}.flowOn(Dispatchers.IO)
