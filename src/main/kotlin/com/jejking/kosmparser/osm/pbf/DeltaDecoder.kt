package com.jejking.kosmparser.osm.pbf

import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.roundToLong


/**
 * Accumulates a delta-encoded list into absolute values.
 * Each value is the sum of all preceding deltas including itself.
 */
fun decodeDelta(deltas: List<Long>): List<Long> {
    var acc = 0L
    return deltas.map { delta -> acc += delta; acc }
}

/**
 * Reconstructs a geographic coordinate (latitude or longitude) in degrees from its
 * PBF-encoded integer form using the block's granularity and offset.
 *
 * Formula: `coordinate = 1e-9 * (offset + granularity * encoded)`
 *
 * The result is rounded to 7 decimal places to eliminate floating-point noise introduced
 * by the integer-to-double conversion. OSM coordinates have at most 7 decimal places of
 * precision (nanodegrees with granularity=100), matching the OSM XML format.
 */
fun decodeCoordinate(encoded: Long, offset: Long, granularity: Int): Double {
    val raw = NANODEGREES_TO_DEGREES * (offset + granularity.toLong() * encoded)
    return (raw * OSM_COORDINATE_PRECISION_SCALE).roundToLong().toDouble() / OSM_COORDINATE_PRECISION_SCALE
}

/**
 * Converts a PBF-encoded timestamp to a [ZonedDateTime].
 *
 * PBF stores timestamps as integer multiples of `dateGranularity` milliseconds
 * since the Unix epoch (1970-01-01T00:00:00Z).
 *
 * Default `dateGranularity` per the spec is 1000 (milliseconds).
 */
fun decodeTimestamp(encodedTimestamp: Long, dateGranularity: Int): ZonedDateTime {
    val millis = encodedTimestamp * dateGranularity
    return ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC)
}
