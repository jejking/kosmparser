package com.jejking.kosmparser.osm.pbf

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Utility to regenerate the Osmosis-derived PBF test fixtures from their corresponding XML fixtures.
 *
 * This is **not a test**. Run it manually when the XML fixtures change.
 *
 * ## Prerequisites
 *
 * Osmosis must be available. The standalone distribution can be downloaded from:
 * https://github.com/openstreetmap/osmosis/releases
 *
 * By default this utility looks for Osmosis at `/tmp/osmosis/osmosis-0.49.2/bin/osmosis`.
 * Override via the `OSMOSIS_BIN` environment variable.
 *
 * Example:
 * ```
 * OSMOSIS_BIN=/usr/local/bin/osmosis ./gradlew ...
 * ```
 *
 * ## Why Osmosis?
 *
 * The OSM wiki designates Osmosis as the reference implementation of the PBF format.
 * Using Osmosis to generate fixtures ensures that our parser is validated against an
 * authoritative external oracle rather than self-referential test data.
 */
object OsmosisFixtureGenerator {

    private val osmosisBin: String =
        System.getenv("OSMOSIS_BIN") ?: findOsmosisInPath() ?: "/tmp/osmosis/osmosis-0.49.2/bin/osmosis"

    private fun findOsmosisInPath(): String? = runCatching {
        val process = ProcessBuilder("which", "osmosis").start()
        val result = process.inputStream.bufferedReader().readLine()?.trim()
        val exitCode = process.waitFor()
        if (exitCode == 0 && !result.isNullOrEmpty()) result else null
    }.getOrNull()

    private val resourceDir: Path =
        Paths.get("src/test/resources")

    /** Fixtures to convert: (input OSM XML, output PBF) */
    private val fixtures = listOf(
        "test1.osm" to "test1.osm.pbf",
        "test-no-bounds.osm" to "test-no-bounds.osm.pbf"
    )

    @JvmStatic
    fun main(args: Array<String>) {
        fixtures.forEach { (xmlFile, pbfFile) ->
            val inputPath = resourceDir.resolve(xmlFile).toAbsolutePath().toString()
            val outputPath = resourceDir.resolve(pbfFile).toAbsolutePath().toString()
            println("Converting $xmlFile → $pbfFile ...")
            val process = ProcessBuilder(
                osmosisBin,
                "--read-xml", "file=$inputPath",
                "--write-pbf", "file=$outputPath"
            )
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                System.err.println("Osmosis failed for $xmlFile (exit $exitCode):\n$output")
            } else {
                println("Done: $outputPath")
            }
        }
    }
}
