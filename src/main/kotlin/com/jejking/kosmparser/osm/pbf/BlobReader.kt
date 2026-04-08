package com.jejking.kosmparser.osm.pbf

import crosby.binary.Fileformat
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream

/**
 * Reads a PBF blob sequence from this [InputStream] as a lazy [Sequence].
 *
 * The PBF binary format is a repeating sequence of:
 * 1. 4-byte big-endian `BlobHeader` length
 * 2. Serialised `BlobHeader` message
 * 3. Serialised `Blob` message (size given in the header's `datasize` field)
 *
 * This function is **blocking** — callers must ensure it runs on an appropriate dispatcher
 * (e.g. `Dispatchers.IO`).
 */
fun InputStream.readBlobSequence(): Sequence<RawBlob> = sequence {
    val dis = DataInputStream(this@readBlobSequence)
    while (true) {
        val headerSize = try {
            dis.readInt()
        } catch (_: EOFException) {
            break
        }
        val headerBytes = dis.readNBytes(headerSize)
        val blobHeader = Fileformat.BlobHeader.parseFrom(headerBytes)
        val blobBytes = dis.readNBytes(blobHeader.datasize)
        val blob = Fileformat.Blob.parseFrom(blobBytes)
        yield(RawBlob(blobHeader.type, blob))
    }
}
