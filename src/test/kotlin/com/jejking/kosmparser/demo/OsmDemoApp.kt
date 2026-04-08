@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.jejking.kosmparser.demo


import com.jejking.kosmparser.osm.OsmData
import com.jejking.kosmparser.osm.pbf.PbfFlowMapper
import com.jejking.kosmparser.osm.xml.OsmFlowMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import java.net.URI
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * Command-line demo application that streams OSM data and prints each element to stdout.
 *
 * ## Usage
 *
 * ```
 * OsmDemoApp <format> <source>
 * ```
 *
 * Where:
 * - `<format>` is `xml` or `pbf`
 * - `<source>` is either a local file path (e.g. `/tmp/map.osm`) or an HTTP(S) URL
 *   (e.g. `https://example.com/map.osm.pbf`)
 *
 * ## Examples
 *
 * Read local XML:
 * ```
 * OsmDemoApp xml /path/to/map.osm
 * ```
 *
 * Read XML from URL:
 * ```
 * OsmDemoApp xml https://example.com/region.osm
 * ```
 *
 * Read local PBF:
 * ```
 * OsmDemoApp pbf /path/to/map.osm.pbf
 * ```
 *
 * Read PBF from URL:
 * ```
 * OsmDemoApp pbf https://download.geofabrik.de/europe/andorra-latest.osm.pbf
 * ```
 *
 * ## Running from the IDE
 *
 * Run the `main` function in this file directly, passing the two arguments
 * via your run configuration.
 */
object OsmDemoApp {

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size != 2) {
            System.err.println("Usage: OsmDemoApp <format> <source>")
            System.err.println("  format : xml | pbf")
            System.err.println("  source : /path/to/file  OR  https://example.com/file")
            exitProcess(1)
        }

        val format = args[0].lowercase()
        val source = args[1]
        val flow = buildFlow(format, source)

        println("Streaming $format data from: $source")
        println("─".repeat(60))
        var count = 0
        runBlocking { flow.collect { item -> println(item); count++ } }
        println("─".repeat(60))
        println("Total items: $count")
    }

    private fun buildFlow(format: String, source: String): Flow<OsmData> {
        val remote = source.startsWith("http://") || source.startsWith("https://")
        return when (format) {
            "xml" -> buildXmlFlow(source, remote)
            "pbf" -> buildPbfFlow(source, remote)
            else -> error("Unknown format '$format'. Use 'xml' or 'pbf'.")
        }
    }

    private fun buildXmlFlow(source: String, remote: Boolean): Flow<OsmData> =
        if (remote) with(OsmFlowMapper) { URI.create(source).toOsmDataFlow() }
        else with(OsmFlowMapper) { Path.of(source).toOsmDataFlow() }

    private fun buildPbfFlow(source: String, remote: Boolean): Flow<OsmData> =
        if (remote) with(PbfFlowMapper) { URI.create(source).toOsmDataFlow() }
        else with(PbfFlowMapper) { Path.of(source).toOsmDataFlow() }
}
