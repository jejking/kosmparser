package com.jejking.kosmparser.osm.pbf

import java.util.zip.Deflater

/** Shared test utilities for PBF tests. */
object PbfTestUtils {

    /** Compresses [data] with zlib (same as Osmosis/standard PBF writers). */
    fun zlibCompress(data: ByteArray): ByteArray {
        val deflater = Deflater()
        deflater.setInput(data)
        deflater.finish()
        val output = ByteArray(data.size + 1024)
        val count = deflater.deflate(output)
        deflater.end()
        return output.copyOf(count)
    }
}
