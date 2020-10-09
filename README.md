# kosmparser

This will be a simple implementation of an asynchronous streaming parser for the [Open Street Map XML Format](https://wiki.openstreetmap.org/wiki/OSM_XML) to make it easier to work with in Kotlin.

Support is limited to the XML export format - i.e. it doesn't yet look at things like changesets and the like.

When correctly wired together (and the project is somewhat finished), the output will be a `Flow<OsmData>`. The data model uses a Kotlin sealed class (for exhaustive pattern matching) and has the following implementations as data classes:

* `OsmMetadata` - version, generator, bounds
* `Node`
* `Way`
* `Relation`

The parser ensures that they will be emitted in the expected order, but depending on the input, there is no guarantee that all will be present (other than `OsmMetadata`).

## Design Notes

The project is also a simple learning experiment with Kotlin Flows - and chains flows together to produce increasing layers of abstraction.

At the bottom layer, we use a `Flow<ByteArray>` for raw input data that is read from a file or a URI. We feed that into an XML Flow Mapper (wrapping the asynchronous [aalto-xml XML parser](https://github.com/FasterXML/aalto-xml)) to produce a `Flow<SimpleXmlParseEvent>`. Given the fact that OSM does not use all the features of XML, we use a simplified parse event model using Kotlin data classes. From that, we then feed the XML parse events into an OSM Flow Mapper to produce a `Flow<OsmData>`.

The OSM Flow Mapper handles validation and conversion logic for the XML Parse Events. Validation is handled programmatically in the absence of official DTDs or XML Schema. The syntactic validation rules are implemented by an internal state machine.