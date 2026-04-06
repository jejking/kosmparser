# Copilot Agent Instructions — kosmparser

## Purpose

You are contributing to a Kotlin-based OSM parsing project.

Your goal is to implement small, well-scoped changes aligned with GitHub issues, while maintaining high code quality, correctness, and readability.

---

## Core Principles

### 1. Functional Kotlin Style

- Prefer **immutability by default**
- Use `val` over `var` unless mutation is unavoidable
- Avoid shared mutable state
- Prefer pure functions where possible
- Keep functions small and composable
- Avoid side effects except at boundaries (I/O, persistence)

---

### 2. Domain Clarity

- Model domain concepts explicitly (Node, Way, Relation, etc.)
- Avoid leaking parsing concerns into domain models
- Keep parsing, transformation, and domain layers separated

---

### 3. Simplicity Over Cleverness

- Do not introduce unnecessary abstractions
- Do not over-generalise
- Prefer readable code over “smart” code

---

## Test-Driven Development (TDD)

You MUST follow TDD:

1. Write a failing test first
2. Implement minimal code to pass the test
3. Refactor safely

### Requirements

- Every change must include tests
- Tests must be:
  - deterministic
  - isolated
  - readable

### Test types

- Unit tests for logic
- Small fixture-based tests for parsing

---

## Static Analysis

You MUST run:

```bash
./gradlew detekt
```

Before considering work complete:

- Fix all detekt issues
- Do not suppress warnings unless justified

---

## Build & Verification

Before completing any task:

```bash
./gradlew clean build
```

All of the following must pass:

- compilation
- tests
- detekt

---

## Git Workflow Constraints

### Branching

- Always create a new branch per task:

```bash
git checkout -b issue-<id>-<short-description>
```

---

### Commits

- Keep commits small and focused
- Use clear commit messages:

```text
Fix #<id>: <short description>
```

---

### Push Policy (IMPORTANT)

**DO NOT push changes unless explicitly instructed by the user.**

- You may prepare commits locally
- You must wait for confirmation before pushing

---

## Scope Control

- Only modify files relevant to the task
- Do not refactor unrelated code
- Do not introduce large-scale changes unless explicitly requested

---

## Safety Constraints

- Do NOT:
  - delete large sections of code without reason
  - change build configuration unnecessarily
  - introduce new dependencies without justification

---

## Error Handling

- Avoid unchecked exceptions where possible
- Prefer explicit handling
- Fail fast for invalid state

---

## Logging & Debugging

- Do not leave debug prints in final code
- Use structured logging only if already present in project

---

## Performance

- Do not optimise prematurely
- Maintain streaming behaviour where relevant (Flow-based processing)
- Avoid loading entire datasets into memory unnecessarily

---

## When Unsure

If requirements are unclear:

- Make the smallest reasonable change
- Document assumptions in comments
- Prefer correctness over completeness

---

## Definition of Done

A task is complete only if:

- Tests are written and passing
- `./gradlew build` succeeds
- `./gradlew detekt` passes
- Code follows functional and immutability principles
- Changes are committed (but NOT pushed)

---

## Optional Improvements (Only If Relevant)

- Improve naming for clarity
- Add small, useful tests
- Reduce duplication

Do NOT expand scope beyond the task.

---
