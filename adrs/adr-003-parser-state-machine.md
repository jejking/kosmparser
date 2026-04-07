# ADR: Use of Explicit Parser State Machine for OSM XML Interpretation

## Status
Superseded by [ADR-004](adr-004-refactor-xml-handling.md)

## Context

The system processes OSM XML as a stream of parsing events:

```text
XML → parsing events → domain objects
```

The structure of OSM XML is:

- shallow but structured
- element-driven (`node`, `way`, `relation`)
- contains nested components (e.g. tags, node references)

Key requirements:

- streaming (no full document in memory)
- deterministic transformation
- low allocation overhead
- ability to construct domain objects incrementally

Possible approaches:

1. Recursive descent / tree-based parsing
2. Stack-based generic XML handling
3. Callback-driven parsing (SAX-style handlers)
4. Explicit finite state machine (FSM)

The implementation uses an **explicit parser state machine (`ParserState`)**.

---

## Decision

Use an **explicit finite state machine (FSM)** to interpret XML parsing events and construct domain objects.

---

## Rationale

### 1. Streaming Compatibility

- XML is processed incrementally
- No full tree is available
- FSM allows:
  - partial construction of objects
  - emission when complete

Recursive or tree-based approaches require buffering.

---

### 2. Deterministic State Transitions

Each incoming event is handled as:

```kotlin
val (nextState, output) = currentState.accept(event)
```

This provides:

- explicit control over transitions
- predictable behaviour
- no hidden control flow

---

### 3. No Implicit Stack or Recursion

Alternative approaches rely on:

- call stack (recursive descent)
- implicit stacks (XML nesting)

FSM makes structure explicit:

- current state encodes position in parsing lifecycle
- no reliance on call stack behaviour

---

### 4. Efficient Incremental Construction

FSM allows:

- accumulation of partial data (e.g. tags, node refs)
- construction only when complete
- minimal intermediate allocations

Example:

- collect `<tag>` events
- emit `Node` only on `</node>`

---

### 5. Testability

Each state transition:

- is a pure function (state + event → new state + output)
- can be tested in isolation
- avoids hidden side effects

---

### 6. Alignment with Flow-Based Pipeline

- FSM consumes a stream of events
- produces a stream of domain objects
- fits naturally as a transformation stage within a Flow

---

## Consequences

### Positive

- Explicit and predictable parsing logic
- Efficient streaming processing
- Good testability
- No recursion or stack overflow risk
- Fine-grained control over parsing lifecycle

---

### Negative

- State explosion:
  - many states required (e.g. ReadingNodes, ReadingWays, ReadingTags)
- Harder to extend:
  - new elements require updating multiple states
- Implicit schema coupling:
  - state transitions encode expected XML order
- Reduced flexibility:
  - not tolerant of unexpected ordering or structure

---

## Alternatives Considered

### Recursive Descent Parsing

Rejected because:

- requires full or partial tree construction
- not suitable for streaming large inputs

---

### Generic Stack-Based XML Handling

Rejected because:

- introduces additional abstraction without clear benefit
- still requires mapping stack state to domain construction logic

---

### SAX Handler Callbacks

Rejected because:

- push-based model complicates composition
- logic becomes fragmented across callbacks
- harder to test deterministically

---

## Known Limitations

- Assumes a specific ordering of OSM elements
- Tight coupling between parsing structure and domain construction
- Difficult to reuse parsing logic independently of output format

---

## Future Considerations

A future refactor may:

- separate structure parsing from domain construction
- replace FSM with:
  - event-based intermediate representation
  - or context-driven element handling
- remove ordering assumptions

---

## Notes

This decision was appropriate given:

- constraints at the time (streaming, performance)
- lack of intermediate abstraction layer

However, it introduces architectural rigidity that motivates later refactoring.

---

## Superseded Scope (Post-Refactor)

This ADR originally covered both:

- structural interpretation of XML
- construction of domain objects

Following subsequent refactoring:

- The FSM is now responsible **only for structural parsing**
- Domain construction has been moved to a separate transformation stage

Revised responsibility:

```text
XML events → FSM → OsmElementEvent
```

and:

```text
OsmElementEvent → Domain model
```

This reduces coupling and improves composability while preserving the benefits of explicit state transitions.
