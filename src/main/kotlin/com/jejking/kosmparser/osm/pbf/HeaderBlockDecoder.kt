package com.jejking.kosmparser.osm.pbf

import com.jejking.kosmparser.osm.Bounds
import com.jejking.kosmparser.osm.OsmMetadata
import com.jejking.kosmparser.osm.Point
import crosby.binary.Osmformat
import kotlin.math.roundToLong


private fun Long.nanoDegreesToDouble(): Double {
    val raw = this * NANODEGREES_TO_DEGREES * OSM_COORDINATE_PRECISION_SCALE
    return raw.roundToLong().toDouble() / OSM_COORDINATE_PRECISION_SCALE
}

private val SUPPORTED_REQUIRED_FEATURES = setOf("OsmSchema-V0.6", "DenseNodes", "HasSorting")

/**
 * Decodes a [Osmformat.HeaderBlock] into an [OsmMetadata] domain object.
 *
 * Validates that all `required_features` in the block are among the known supported set.
 * Currently supported features: `OsmSchema-V0.6`, `DenseNodes`, `HasSorting`.
 *
 * The HeaderBlock's `bbox` (if present) is decoded from nanodegrees (fixed 1e-9 scale)
 * into a [Bounds]. The `writingprogram` field maps to `generator`.
 * There is no OSM schema version in the HeaderBlock, so [OsmMetadata.version] is always `null`.
 *
 * @throws IllegalArgumentException if any required feature is not supported.
 */
fun Osmformat.HeaderBlock.toOsmMetadata(): OsmMetadata {
    val unsupported = requiredFeaturesList.filterNot { it in SUPPORTED_REQUIRED_FEATURES }
    require(unsupported.isEmpty()) {
        "PBF file requires unsupported features: $unsupported"
    }

    val bounds = if (hasBbox()) {
        Bounds(
            minPoint = Point(lat = bbox.bottom.nanoDegreesToDouble(), lon = bbox.left.nanoDegreesToDouble()),
            maxPoint = Point(lat = bbox.top.nanoDegreesToDouble(), lon = bbox.right.nanoDegreesToDouble())
        )
    } else null

    return OsmMetadata(
        version = null,
        generator = if (hasWritingprogram()) writingprogram else null,
        bounds = bounds
    )
}
