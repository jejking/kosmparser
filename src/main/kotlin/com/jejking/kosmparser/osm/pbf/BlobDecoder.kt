package com.jejking.kosmparser.osm.pbf

import crosby.binary.Fileformat
import java.util.zip.Inflater

/**
 * Decompresses a [Fileformat.Blob] and returns its raw bytes.
 *
 * Supports:
 * - `raw` — no compression; bytes returned as-is
 * - `zlib_data` — deflate/zlib compressed; decompressed using [Inflater]
 *
 * All other compression formats (`lzma_data`, `lz4_data`, `zstd_data`) are not supported
 * and will throw [UnsupportedOperationException].
 */
fun Fileformat.Blob.decompress(): ByteArray = when {
    hasRaw() -> raw.toByteArray()
    hasZlibData() -> {
        val input = zlibData.toByteArray()
        val output = ByteArray(rawSize)
        val inflater = Inflater()
        try {
            inflater.setInput(input)
            var offset = 0
            while (!inflater.finished()) {
                offset += inflater.inflate(output, offset, output.size - offset)
            }
            check(offset == rawSize) {
                "PBF zlib decompression produced $offset bytes but rawSize is $rawSize"
            }
        } finally {
            inflater.end()
        }
        output
    }
    else -> throw UnsupportedOperationException(
        "Unsupported PBF blob compression: only raw and zlib_data are supported"
    )
}
