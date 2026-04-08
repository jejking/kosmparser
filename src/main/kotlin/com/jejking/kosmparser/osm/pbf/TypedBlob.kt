package com.jejking.kosmparser.osm.pbf

import crosby.binary.Fileformat

/**
 * A single blob read from a PBF file, pairing its block type (e.g. `"OSMHeader"`,
 * `"OSMData"`) with the not-yet-decompressed [Fileformat.Blob].
 */
data class TypedBlob(val type: String, val blob: Fileformat.Blob)
