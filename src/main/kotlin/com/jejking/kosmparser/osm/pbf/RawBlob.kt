package com.jejking.kosmparser.osm.pbf

import crosby.binary.Fileformat

/**
 * A single raw blob read from a PBF file, pairing its block type (e.g. `"OSMHeader"`,
 * `"OSMData"`) with the decompressed-ready [Fileformat.Blob].
 */
data class RawBlob(val type: String, val blob: Fileformat.Blob)
