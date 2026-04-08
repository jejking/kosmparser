# kosmparser

![Java CI with Gradle](https://github.com/jejking/kosmparser/workflows/Java%20CI%20with%20Gradle/badge.svg)

This is a simple implementation of an asynchronous streaming parser for OpenStreetMap data formats, making it easier to work with OSM data in Kotlin. Support covers:
- **OSM XML** (`.osm`) — the standard XML export format
- **OSM PBF** (`.osm.pbf`) — the Protocol Buffer binary format used by Geofabrik and planet.osm

Support is limited to the export format (not changesets, history files, etc.).

## Data Model

The main abstraction is `Flow<OsmData>`. The data model uses a Kotlin sealed class (for exhaustive pattern matching) and has the following implementations as data classes:

* `OsmMetadata` - version, generator, bounds
* `Node`
* `Way`
* `Relation`

The parser ensures that they will be emitted in the expected order, but depending on the input, there is no guarantee that all will be present (other than `OsmMetadata`).

## Usage

### XML

The object `OsmFlowMapper` offers utility functions to generate a `Flow<OsmData>` via extension functions on `java.nio.Path` and `java.net.URI`.

```kotlin
import java.nio.Path
import com.jejking.kosmparser.osm.xml.OsmFlowMapper.toOsmDataFlow

Path.of("/tmp/map.osm").toOsmDataFlow()

URI.create("https://example.com/map.osm").toOsmDataFlow()
```

### PBF

The `PbfFlowMapper` provides the same interface for `.osm.pbf` files:

```kotlin
import java.nio.Path
import com.jejking.kosmparser.osm.pbf.toOsmDataFlow

Path.of("/tmp/map.osm.pbf").toOsmDataFlow()

URI.create("https://example.com/map.osm.pbf").toOsmDataFlow()
```

In both cases, `OsmMetadata` is always emitted as the first item in the flow.

## Design Notes

### XML parsing pipeline

The project chains Kotlin Flows to produce increasing layers of abstraction.

At the bottom layer, a `Flow<ByteArray>` reads raw input from a file or URI. This feeds into an XML Flow Mapper (wrapping the asynchronous [aalto-xml](https://github.com/FasterXML/aalto-xml) parser) to produce a `Flow<SimpleXmlParseEvent>`.

The OSM XML parser then uses a **two-stage pipeline**:

1. **Stage 1 — `toOsmElementEvents()`**: converts `Flow<SimpleXmlParseEvent>` to `Flow<OsmElementEvent>`, a format-agnostic intermediate representation. Unknown XML elements (e.g. `<note>`, `<meta>`) are silently skipped.
2. **Stage 2 — `toOsmData()`**: converts `Flow<OsmElementEvent>` to `Flow<OsmData>`, assembling the domain model by accumulating tags, node references, and relation members within each element's scope.

### PBF parsing pipeline

PBF files consist of a sequence of length-prefixed blobs. Each blob is decompressed (zlib) and decoded directly to `OsmData` domain objects — there is no intermediate event representation:

```
InputStream → blob sequence → HeaderBlock → OsmMetadata
                            → PrimitiveBlock → List<OsmData>
```

This direct-decode approach makes PBF parsing significantly faster than XML. The PBF implementation uses the official `protobuf-kotlin` library with Kotlin DSL code generated from the OSM-binary `.proto` files.

### ElementMetadata nullability

Several element attributes are optional per the OSM specifications and are represented as nullable fields in `ElementMetadata`: `uid`, `timestamp`, `version`, and `changeSet`. The `visible` attribute defaults to `true` when absent. Element IDs may be negative (editor placeholder objects).

Note: `OsmMetadata.version` is always `null` when parsing PBF files, as the PBF format has no schema version field.