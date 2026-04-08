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
fun InputStream.readBlobSequence(): Sequence<TypedBlob> = sequence {
    val dis = DataInputStream(this@readBlobSequence)
    while (true) {
        val headerSize = try {
            dis.readInt()
        } catch (_: EOFException) {
            break
        }
        val headerBytes = dis.readNBytes(headerSize)
        check(headerBytes.size == headerSize) {
            "Truncated PBF: expected $headerSize header bytes, got ${headerBytes.size}"
        }
        val blobHeader = Fileformat.BlobHeader.parseFrom(headerBytes)
        val blobBytes = dis.readNBytes(blobHeader.datasize)
        check(blobBytes.size == blobHeader.datasize) {
            "Truncated PBF: expected ${blobHeader.datasize} blob bytes, got ${blobBytes.size}"
        }
        val blob = Fileformat.Blob.parseFrom(blobBytes)
        yield(TypedBlob(blobHeader.type, blob))
    }
}
