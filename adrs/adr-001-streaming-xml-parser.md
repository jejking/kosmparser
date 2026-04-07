# ADR: Use of Streaming XML Parser (Aalto) over SAX/DOM

## Status
Accepted (retrospective)

## Context

The system ingests large OpenStreetMap (OSM) XML datasets and transforms them into domain objects (Node, Way, Relation).

Key characteristics of the input:

- Potentially very large (hundreds of MB to GB)
- Sequential structure (nodes, ways, relations)
- Not randomly accessed
- Requires transformation rather than full document inspection

Traditional XML parsing approaches include:

- DOM (tree-based, in-memory)
- SAX (event-based, push model)

The implementation instead uses a **non-blocking streaming parser** via Aalto.

---

## Decision

Use **Aalto asynchronous streaming XML parser** instead of:

- DOM (Document Object Model)
- classic SAX parser

---

## Rationale

### 1. Memory Efficiency

- DOM requires loading the entire document into memory → not viable for large OSM datasets
- Aalto processes the stream incrementally
- Memory usage is effectively O(1) relative to input size

---

### 2. True Streaming Capability

Unlike SAX:

- Aalto supports **non-blocking, incremental parsing**
- Works naturally with byte streams and backpressure-aware pipelines
- Enables integration with streaming abstractions (Kotlin Flow)

---

### 3. Pull-Based Model (vs SAX Push)

SAX:

- Pushes events into handlers
- Requires inversion of control
- Harder to compose and test

Aalto:

- Allows controlled consumption of parsing events
- Easier to integrate into a pipeline
- More predictable execution flow

---

### 4. Alignment with Functional Pipeline Design

- Aalto produces a stream of parsing events
- These are mapped into higher-level events and domain objects
- This aligns naturally with a transformation pipeline

---

### 5. Backpressure Compatibility

- SAX has no notion of backpressure
- Aalto integrates cleanly with coroutine-based pipelines
- Enables controlled throughput and avoids overload

---

## Consequences

### Positive

- Scales to very large OSM datasets
- Enables streaming, pipeline-based architecture
- Supports non-blocking processing
- Improves composability and testability

---

### Negative

- More complex than DOM
- Less familiar than SAX for many developers
- Requires explicit state management (FSM or equivalent)
- Error handling is more involved

---

## Alternatives Considered

### DOM Parser

Rejected because:

- Requires full document in memory
- Not viable for large datasets

---

### SAX Parser

Rejected because:

- Push-based model complicates composition
- Harder to integrate with Flow
- Limited control over event consumption
- Less natural fit for functional transformations

---

## Notes

This decision directly enables:

- Flow-based processing pipeline
- Explicit parser state machine design
- Future extension to other streaming formats (e.g. PBF)

---
