# ADR: Refactor OSM XML Parser to Event-Based, Order-Independent Pipeline

## Status
Accepted

## Context

The current XML parsing implementation in `kosmparser` is built around a finite state machine (`ParserState`) that:

- Consumes a stream of XML parsing events
- Maintains implicit knowledge of OSM document structure and ordering
- Constructs domain objects (`Node`, `Way`, etc.) during parsing

This design has several strengths:

- Streaming and memory-efficient (Flow-based)
- Explicit state transitions
- Deterministic and testable behaviour

However, it also exhibits structural limitations:

### 1. Implicit Schema Coupling

The parser encodes assumptions about OSM XML ordering:

```xml
<osm>
  <bounds/>
  <node/>
  <way/>
</osm>
```

This results in:

- Tight coupling between parser states and expected element order
- Reduced robustness to variations in input, such as other xml elements
- Difficulty supporting alternative formats (e.g. PBF)

---

### 2. Conflation of Responsibilities

`ParserState` currently combines:

- XML structure interpretation
- OSM semantic interpretation
- Domain object construction
- Partial validation

This leads to:

- High cognitive load
- Limited extensibility
- Difficulty reusing parsing logic independently of domain construction

---

### 3. Limited Extensibility

Adding support for:

- additional XML elements
- alternative input formats (e.g. PBF)
- alternative outputs (e.g. GeoJSON, graph projections)

requires modifying the core FSM, increasing risk and complexity.

---

### 4. Flow Underutilisation

Although the system uses Kotlin `Flow`, the pipeline is effectively:

```
Flow<XML Event> → Monolithic FSM → Flow<OsmData>
```

This limits composability and reuse of intermediate representations.

---

## Decision

Refactor the XML parsing pipeline into a **two-stage, event-driven architecture**:

```
Flow<SimpleXmlParseEvent>
  → Flow<OsmElementEvent>
  → Flow<OsmData>
```

### Key Principles

1. **Decouple parsing from domain construction**
2. **Preserve streaming semantics**
3. **Retain Flow as the primary abstraction**
4. **Introduce a format-agnostic intermediate representation**
5. **Handle optional elements and metadata gracefully**

Note: The OSM XML spec guarantees block ordering (nodes → ways → relations), so strict order independence is not a goal. The real problems addressed are: absent optional blocks, unknown/extra elements, and missing metadata attributes.

---

## New Architecture

### 1. Stage 1 — XML → OSM Element Events

Introduce a new transformation:

```kotlin
fun Flow<SimpleXmlParseEvent>.toOsmElementEvents(): Flow<OsmElementEvent>
```

#### OsmElementEvent

```kotlin
sealed class OsmElementEvent {

    // Document lifecycle
    data class StartOsm(
        val version: String?,
        val generator: String?
    ) : OsmElementEvent()

    data class BoundsEvent(
        val minlat: String,
        val maxlat: String,
        val minlon: String,
        val maxlon: String
    ) : OsmElementEvent()

    // Node lifecycle
    data class StartNode(
        val id: Long,
        val lat: Double,
        val lon: Double,
        val metadata: ElementMetadata?
    ) : OsmElementEvent()

    data class EndNode(val id: Long) : OsmElementEvent()

    // Way lifecycle
    data class StartWay(
        val id: Long,
        val metadata: ElementMetadata?
    ) : OsmElementEvent()

    data class NodeRef(val ref: Long) : OsmElementEvent()

    data class EndWay(val id: Long) : OsmElementEvent()

    // Relation lifecycle
    data class StartRelation(
        val id: Long,
        val metadata: ElementMetadata?
    ) : OsmElementEvent()

    data class RelationMemberRef(
        val type: String,
        val ref: Long,
        val role: String?
    ) : OsmElementEvent()

    data class EndRelation(val id: Long) : OsmElementEvent()

    // Shared
    data class Tag(val key: String, val value: String) : OsmElementEvent()
}
```

Notes:
- `BoundsEvent` carries raw string attribute values; parsing to `Double` is done in Stage 2.
- `Member` is named `RelationMemberRef` to avoid shadowing the `Member` domain class in `OsmTypes.kt`.
- `StartOsm` is always emitted first; `BoundsEvent` is optional (absent if `<bounds>` is not present).
- Unknown XML elements (e.g. `<note>`, `<meta>`) are silently skipped; no event is emitted for them.
- `EndDocument` completes the Flow; no event is emitted.

---

### 2. Replace FSM with ParserContext

Replace `ParserState` with a simpler, localised context:

```kotlin
class ParserContext {

    private var current: ElementContext = ElementContext.None

    fun handle(event: SimpleXmlParseEvent): List<OsmElementEvent> {
        return when (event) {
            is StartElement -> handleStart(event)
            is EndElement -> handleEnd(event)
            else -> emptyList()
        }
    }
}
```

`handle()` returns a `List<OsmElementEvent>` that is flatMapped in the Flow transform. Unknown XML elements produce an empty list (silently skipped). `EndDocument` completes the Flow rather than being handled here.

#### ElementContext

```kotlin
sealed class ElementContext {
    object None : ElementContext()

    data class NodeContext(
        val id: Long,
        val lat: Double,
        val lon: Double,
        val metadata: ElementMetadata?
    ) : ElementContext()

    data class WayContext(
        val id: Long,
        val metadata: ElementMetadata?
    ) : ElementContext()

    data class RelationContext(
        val id: Long,
        val metadata: ElementMetadata?
    ) : ElementContext()
}
```

---

### 3. Event Handling Rules

#### Start Element

```kotlin
when (elementName) {
    "osm" -> {
        emit(StartOsm(version, generator))
    }
    "bounds" -> {
        emit(BoundsEvent(minlat, maxlat, minlon, maxlon))  // raw strings
    }
    "node" -> {
        current = NodeContext(...)
        emit(StartNode(...))
    }
    "way" -> {
        current = WayContext(...)
        emit(StartWay(...))
    }
    "relation" -> {
        current = RelationContext(...)
        emit(StartRelation(...))
    }
    "tag" -> {
        emit(Tag(k, v))
    }
    "nd" -> {
        emit(NodeRef(ref))
    }
    "member" -> {
        emit(RelationMemberRef(...))
    }
    else -> { /* unknown element — emit nothing */ }
}
```

#### End Element

```kotlin
when (elementName) {
    "node" -> {
        emit(EndNode(current.id))
        current = None
    }
    "way" -> {
        emit(EndWay(current.id))
        current = None
    }
    "relation" -> {
        emit(EndRelation(current.id))
        current = None
    }
}
```

---

### 4. Stage 2 — Event → Domain Model

Introduce:

```kotlin
fun Flow<OsmElementEvent>.toOsmData(): Flow<OsmData>
```

This stage:

- Accumulates `StartOsm` and optional `BoundsEvent`; emits `OsmMetadata` before the first domain element
- Accumulates events between Start/End pairs; constructs `Node`, `Way`, `Relation`
- Handles nullable `ElementMetadata` fields (`uid`, `timestamp`, `version`, `changeSet` may all be null)

Example:

```kotlin
when (event) {
    is StartOsm -> storeOsmHeader(event)
    is BoundsEvent -> storeBounds(event)   // optional; bounds = null if never seen
    is StartNode -> {
        emitOsmMetadataIfNotYetEmitted()
        startNode(event)
    }
    is Tag -> currentContext.addTag(event)
    is EndNode -> emit(buildNode())
    ...
}
```

#### `ElementMetadata` nullable fields

Per the OSM XML spec, several attributes are not guaranteed to be present in all file flavours:

| Field | Nullable | Default when absent |
|---|---|---|
| `uid` | `Long?` | `null` |
| `timestamp` | `ZonedDateTime?` | `null` |
| `version` | `Long?` | `null` |
| `changeSet` | `Long?` | `null` |
| `visible` | `Boolean` (non-null) | `true` |
| `user` | `String?` | `null` |

Negative element IDs are accepted (used by editors for new placeholder objects).

---

### 5. Flow Composition

Final pipeline:

```kotlin
source
  .parseXml()
  .toOsmElementEvents()
  .toOsmData()
```

---

## Trade-offs

### Benefits

#### 1. Order Independence

- No reliance on XML element ordering
- Robust to variations in input

#### 2. Separation of Concerns

- Parsing logic isolated from domain construction
- Easier to reason about and test

#### 3. Extensibility

- New elements can be added without modifying global state machine
- Alternative formats (e.g. PBF) can emit the same event model

#### 4. Reusability

- Intermediate `OsmElementEvent` stream can be reused for:
  - debugging
  - filtering
  - alternative projections

#### 5. Alignment with Streaming Model

- Flow stages become composable and explicit
- Improved pipeline clarity

---

### Costs

#### 1. Increased Abstraction

- Additional layer (`OsmElementEvent`)
- More moving parts

#### 2. Slight Overhead

- More objects emitted (events vs direct domain objects)
- Negligible relative to IO cost

#### 3. Refactor Effort

- Requires replacing existing FSM
- Requires new builder implementation

---

## Alternatives Considered

### 1. Keep Existing FSM and Extend

Rejected because:

- Does not address schema coupling
- Increases complexity over time
- Makes PBF integration difficult

---

### 2. Introduce Stack-Based XML Parser Only

Rejected because:

- Does not separate parsing from domain construction
- Retains core coupling problem

---

### 3. Full Rewrite

Rejected because:

- Existing design is fundamentally sound
- Incremental refactor is lower risk

---

## Implementation Plan

### Step 1
- Update `ElementMetadata` (nullable fields, `visible` defaults `true`)

### Step 2
- Define `OsmElementEvent` sealed class

### Step 3
- Define shared `Arb<ElementMetadata>`, `Arb<Node>`, `Arb<Way>`, `Arb<Relation>` arbitraries in `OsmArbitraries.kt`

### Step 4
- TDD `ParserContext` + `toOsmElementEvents()`:
  - Unit tests for all element types
  - Property tests: unknown elements injected at random positions produce identical output

### Step 5
- TDD `toOsmData()` builder:
  - Unit tests for `OsmMetadata` assembly, Node/Way/Relation construction
  - Property-based round-trip at event level (all nullable field combinations)
  - Property-based round-trip at XML level (generate XML → parse → assert equality)

### Step 6
- Update `OsmFlowMapper` to use the new pipeline

### Step 7
- Remove old FSM (`OsmParserState.kt`, `OsmParserStateTest.kt`)

### Step 8
- Update `README.md`

---

## Consequences

### Positive

- Enables PBF integration with minimal duplication
- Simplifies parser logic
- Improves long-term maintainability

### Negative

- Temporary duplication during migration
- Requires careful validation to ensure behavioural parity

---

## Future Work

- Add PBF ingestion using same event model
- Introduce validation layer (optional, possibly Arrow-based)
- Add alternative sinks (GeoJSON, graph projections)
