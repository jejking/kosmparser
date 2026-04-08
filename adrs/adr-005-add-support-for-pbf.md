# ADR: Add Support for OSM Protocolbuffer Binary Format (PBF)

## Status
Accepted

## Implementation Decisions

- **Output format**: The PBF parser emits `OsmData` domain objects directly (no intermediate event
  layer). PBF blocks are self-contained units, unlike hierarchical XML, so the two-stage event
  pipeline used in the XML path is not needed.
- **Protobuf library**: Official `com.google.protobuf` Gradle plugin with `protobuf-kotlin` runtime
  generates idiomatic Kotlin classes from `fileformat.proto` and `osmformat.proto`.
- **Quality oracle**: [Osmosis](https://wiki.openstreetmap.org/wiki/Osmosis) (the reference PBF
  implementation) is used to generate authoritative `.osm.pbf` test fixtures from our XML test data.
  Integration tests cross-validate that our PBF parser produces identical `OsmData` output to our
  XML parser on the same data. This eliminates circular test dependencies.
- **Compression**: Must support uncompressed (`raw`) and zlib-compressed (`zlib_data`). Other
  compression formats (lzma, lz4, zstd) are not required and will throw on encounter.
- **Required features**: Accept `OsmSchema-V0.6` and `DenseNodes`. Reject files with unknown or
  unsupported required features (e.g. `HistoricalInformation`).
- **`OsmMetadata` from `OSMHeader`**: The `OSMHeader` block is decoded to emit an `OsmMetadata`
  object as the first item in the output flow, matching the XML parser's behaviour.

## Context

The system currently ingests OpenStreetMap (OSM) data via XML using a streaming parser.

However, OSM data is primarily distributed in **PBF (Protocolbuffer Binary Format)**:

- Binary format based on Protocol Buffers
- Smaller and faster than XML
- Standard distribution format for large datasets (e.g. Geofabrik extracts)

The PBF format is defined by the `.proto` files:

- https://github.com/openstreetmap/OSM-binary/tree/master/osmpbf

Key files:

- `fileformat.proto` → container (Blob, BlobHeader)
- `osmformat.proto` → OSM data structures (PrimitiveBlock, Node, Way, Relation)

---

## PBF File Structure

A PBF file is a sequence of blocks:

```
[BlobHeader][Blob][BlobHeader][Blob]...
```

### BlobHeader

- Contains:
    - `type` (e.g. `"OSMHeader"` or `"OSMData"`)
    - `datasize` (size of Blob)

### Blob

Contains:

- Either:
    - raw data (`raw`)
    - compressed data (`zlib_data` most common)

---

## Block Types

### 1. OSMHeader

Contains metadata:

- required features
- optional bounding box
- replication info

Not required for parsing core elements.

---

### 2. OSMData → PrimitiveBlock

This is the main data structure.

A `PrimitiveBlock` contains:

- `StringTable`
- `PrimitiveGroup[]`
- granularity + coordinate offsets

---

## String Table

```
message StringTable {
  repeated bytes s = 1;
}
```

- All tag keys and values are stored here
- Tags reference indices into this table
- Index `0` is typically empty

---

## PrimitiveGroup

Each `PrimitiveBlock` contains multiple `PrimitiveGroup`s.

Each group contains exactly one of:

- `nodes` (explicit Node objects)
- `dense` (DenseNodes encoding)
- `ways`
- `relations`

---

## Node Encoding

### A) Simple Node

```
message Node {
  required sint64 id
  repeated uint32 keys
  repeated uint32 vals
  optional sint64 lat
  optional sint64 lon
}
```

- Tags:
    - `keys[i]` → key index
    - `vals[i]` → value index

---

### B) DenseNodes (Important)

DenseNodes are the primary encoding for nodes.

```
message DenseNodes {
  repeated sint64 id
  repeated sint64 lat
  repeated sint64 lon
  repeated int32 keys_vals
}
```

Characteristics:

#### 1. Delta Encoding

Values are stored as deltas:

```
value[n] = value[n-1] + delta[n]
```

Applies to:

- `id`
- `lat`
- `lon`

---

#### 2. Tags (Flattened)

`keys_vals` is a flattened sequence:

```
[key, val, key, val, 0, key, val, 0, ...]
```

- `0` terminates a node’s tags

---

## Coordinate Decoding

Coordinates are stored as integers and must be reconstructed:

```
lat = (lat_offset + (lat * granularity)) / 1e9
lon = (lon_offset + (lon * granularity)) / 1e9
```

Defaults:

- granularity = 100
- offsets usually 0

---

## Way Encoding

```
message Way {
  required int64 id
  repeated uint32 keys
  repeated uint32 vals
  repeated sint64 refs
}
```

- `refs` are node IDs (delta encoded)

---

## Relation Encoding

```
message Relation {
  required int64 id
  repeated uint32 keys
  repeated uint32 vals
  repeated int32 roles_sid
  repeated sint64 memids
  repeated MemberType types
}
```

- `roles_sid` → string table indices
- `memids` → delta encoded
- `types` → NODE / WAY / RELATION

---

## Compression

Each Blob may be:

- uncompressed (`raw`)
- zlib compressed (`zlib_data`) — most common

The parser must:

1. Read BlobHeader
2. Read Blob
3. Decompress if needed
4. Decode protobuf message

---

## Decision

Extend the system to support ingestion of OSM PBF files by:

1. Reading and decoding Blob/BlobHeader sequences
2. Decoding `PrimitiveBlock` structures using protobuf
3. Converting encoded primitives into the system’s internal representation

---

## Design Constraints

### 1. Streaming Behaviour

- Must process blocks sequentially
- Must not load entire file into memory
- Each `PrimitiveBlock` is processed independently

---

### 2. Decoding Responsibilities

The implementation must correctly handle:

- zlib decompression
- protobuf decoding (`fileformat.proto`, `osmformat.proto`)
- string table resolution
- delta decoding (IDs, coordinates, refs)
- coordinate reconstruction

---

### 3. Separation of Concerns

The PBF parser is responsible only for:

- decoding binary representation
- reconstructing logical OSM primitives

It must NOT:

- embed business logic
- assume downstream representation details

---

### 4. Integration Boundary

The output of the PBF parser must be compatible with the existing processing pipeline.

This may be achieved by:

- emitting domain objects directly, OR
- emitting an intermediate representation

The choice is intentionally left to the implementation.

---

## Rationale

### 1. Performance

- PBF is significantly more compact and faster than XML
- Reduces IO and parsing overhead

---

### 2. Ecosystem Compatibility

- PBF is the standard format for OSM data distribution
- Required for working with real-world datasets

---

### 3. Structural Fit

- Block-based structure aligns with streaming processing
- Allows incremental decoding without full buffering

---

## Consequences

### Positive

- Enables ingestion of standard OSM datasets
- Improves performance and scalability
- Reduces dependency on XML format

---

### Negative

- Increased complexity:
    - protobuf decoding
    - delta decoding
    - string table handling
- Requires additional dependencies (protobuf, compression)

---

## Alternatives Considered

### Convert PBF to XML

Rejected because:

- loses performance benefits
- introduces unnecessary pipeline stage

---

### Use External Library to Fully Parse PBF

Rejected because:

- reduces control over streaming and memory behaviour
- harder to integrate with existing architecture

---

## Implementation Notes

- Use official `.proto` definitions from:
    - https://github.com/openstreetmap/OSM-binary/tree/master/osmpbf

- Generate Kotlin/Java classes from:
    - `fileformat.proto`
    - `osmformat.proto`

- Ensure correctness for:
    - DenseNodes decoding
    - delta accumulation
    - tag reconstruction

---

## Testing Strategy

- Use small `.osm.pbf` fixtures
- Validate:
    - node count
    - way structure
    - tag correctness
- Cross-check against equivalent XML dataset

---

## Open Questions

- Whether to parallelise block decoding
- Whether to expose partial decoding (filtering early)

---

## Notes

PBF is fundamentally:

- block-oriented
- columnar in parts (DenseNodes)
- not hierarchical like XML

This requires a different parsing strategy but represents the same logical OSM data model.

---