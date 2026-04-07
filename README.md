# kosmparser

![Java CI with Gradle](https://github.com/jejking/kosmparser/workflows/Java%20CI%20with%20Gradle/badge.svg)

This is a simple implementation of an asynchronous streaming parser for the [Open Street Map XML Format](https://wiki.openstreetmap.org/wiki/OSM_XML) to make it easier to work with in Kotlin. Support is limited to the XML export format - i.e. it doesn't yet look at things like changesets and the like. Likewise unsupported are exports in Protocol Buffer Format (pbf), etc.

## Data Model

The main abstraction is `Flow<OsmData>`. The data model uses a Kotlin sealed class (for exhaustive pattern matching) and has the following implementations as data classes:

* `OsmMetadata` - version, generator, bounds
* `Node`
* `Way`
* `Relation`

The parser ensures that they will be emitted in the expected order, but depending on the input, there is no guarantee that all will be present (other than `OsmMetadata`).

## Usage

The object `OsmFlowMapper` offers utility functions to generate a `Flow<OsmData>` via extension functions on `java.nio.Path` and `java.net.URI`.

Example code.

```kotlin
import java.nio.Path
import com.jejking.kosmparser.OsmFlowMapper.toOsmDataFlow

Path.of("/tmp/map.osm").toOsmDataFlow()

URI.create("https://example.com/osm").toOsmDataFlow()
```

## Design Notes

The project chains Kotlin Flows to produce increasing layers of abstraction.

At the bottom layer, a `Flow<ByteArray>` reads raw input from a file or URI. This feeds into an XML Flow Mapper (wrapping the asynchronous [aalto-xml](https://github.com/FasterXML/aalto-xml) parser) to produce a `Flow<SimpleXmlParseEvent>`.

The OSM parser then uses a **two-stage pipeline** to convert XML events to domain objects:

1. **Stage 1 — `toOsmElementEvents()`**: converts `Flow<SimpleXmlParseEvent>` to `Flow<OsmElementEvent>`, a format-agnostic intermediate representation. Unknown XML elements (e.g. `<note>`, `<meta>`) are silently skipped.
2. **Stage 2 — `toOsmData()`**: converts `Flow<OsmElementEvent>` to `Flow<OsmData>`, assembling the domain model by accumulating tags, node references, and relation members within each element's scope.

`OsmMetadata` (assembled from the `<osm>` root element and the optional `<bounds>` element) is always emitted as the first item in the output flow.

Several element attributes are optional per the OSM XML specification and are represented as nullable fields in `ElementMetadata`: `uid`, `timestamp`, `version`, and `changeSet`. The `visible` attribute defaults to `true` when absent. Element IDs may be negative (editor placeholder objects).