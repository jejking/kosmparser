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
import java.time.Duration

private const val HTTP_CONNECT_TIMEOUT_SECONDS = 30L
private const val HTTP_SUCCESS_MIN = 200
private const val HTTP_SUCCESS_MAX = 299

/**
 * Provides extension functions to read OSM PBF data as a [OsmDataFlow].
 *
 * Usage:
 * ```kotlin
 * import com.jejking.kosmparser.osm.pbf.PbfFlowMapper.toOsmDataFlow
 *
 * Path.of("/tmp/map.osm.pbf").toOsmDataFlow()
 * URI.create("https://example.com/map.osm.pbf").toOsmDataFlow()
 * ```
 */
object PbfFlowMapper {

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
            for (typedBlob in inputStream.readBlobSequence()) {
                val bytes = typedBlob.blob.decompress()
                when (typedBlob.type) {
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
     *
     * @throws IllegalStateException if the server responds with a non-2xx HTTP status.
     */
    fun URI.toOsmDataFlow(): OsmDataFlow = flow {
        val client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(HTTP_CONNECT_TIMEOUT_SECONDS))
            .build()
        val request = HttpRequest.newBuilder(this@toOsmDataFlow).GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        check(response.statusCode() in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX) {
            "HTTP error ${response.statusCode()} fetching PBF from ${this@toOsmDataFlow}"
        }
        response.body().use { inputStream ->
            for (typedBlob in inputStream.readBlobSequence()) {
                val bytes = typedBlob.blob.decompress()
                when (typedBlob.type) {
                    "OSMHeader" -> emit(Osmformat.HeaderBlock.parseFrom(bytes).toOsmMetadata())
                    "OSMData" -> Osmformat.PrimitiveBlock.parseFrom(bytes).toOsmDataList()
                        .forEach { emit(it) }
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}
