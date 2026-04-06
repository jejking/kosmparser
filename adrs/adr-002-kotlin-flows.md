# ADR: Use of Kotlin Flow for Streaming Data Processing

## Status
Accepted (retrospective)

## Context

The system processes OSM XML data as a stream:

```text
Byte stream → XML events → OSM elements → domain objects
```

The processing requirements are:

- Incremental transformation
- Low memory footprint
- Ability to compose multiple transformation stages
- Potential for asynchronous or non-blocking execution

Options considered:

- Java Streams
- Iterator-based pipelines
- Reactive frameworks (RxJava, Reactor)
- Kotlin Flow

---

## Decision

Use **Kotlin Flow** as the primary abstraction for streaming data processing.

---

## Rationale

### 1. Native Kotlin Integration

- Flow is part of Kotlin coroutines ecosystem
- No additional dependency required
- Idiomatic for Kotlin-based codebase

---

### 2. Sequential by Default (Important)

- Flow is **sequential unless explicitly made concurrent**
- This aligns with XML parsing requirements:
  - order matters
  - deterministic processing is required

This avoids complexity inherent in reactive frameworks.

---

### 3. Backpressure Awareness

- Flow supports suspension
- Producers and consumers naturally coordinate
- Prevents overwhelming downstream processing

---

### 4. Composability

Pipeline stages can be expressed cleanly:

```kotlin
source
  .parseXml()
  .map { ... }
  .filter { ... }
  .collect { ... }
```

This enables:

- clear separation of concerns
- reusable transformations
- testable intermediate stages

---

### 5. Structured Concurrency

- Integrates with coroutine scopes
- Ensures controlled lifecycle of processing
- Avoids resource leaks common in reactive systems

---

### 6. Alignment with Streaming Parser

- Aalto produces a stream of events
- Flow provides a natural abstraction to propagate and transform these events

---

## Consequences

### Positive

- Clean, readable pipeline structure
- Low memory overhead
- Strong composability
- Deterministic execution
- Easy to test individual stages

---

### Negative

- Requires understanding of coroutines
- Debugging can be harder than imperative code
- Overuse of operators can reduce clarity if not controlled

---

## Alternatives Considered

### Java Streams

Rejected because:

- Not designed for asynchronous or streaming IO
- Limited backpressure support
- Less suitable for long-running pipelines

---

### RxJava / Reactor

Rejected because:

- Higher conceptual overhead
- More complex concurrency model
- Not idiomatic in Kotlin-first codebase

---

### Iterator / Sequence-based approach

Rejected because:

- Lacks suspension/backpressure support
- Less flexible for asynchronous processing
- Harder to extend for non-blocking IO

---

## Notes

- Flow is used as the backbone of the pipeline
- State (e.g. parser state machine) is encapsulated within Flow transformations
- Future work should maintain Flow as the primary abstraction for streaming

---
