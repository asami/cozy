# Logical UI Runtime Handoff Reconciliation

Date: 2026-10-03
Status: development decision
Creates: Phase 76

## Background

CNCF Phase 96 planning noticed that Phase 74 Logical UI reference/runtime classes are built inside Scala 2.12 Cozy while CNCF is Scala 3. This initially looked like a distribution/cross-build problem.

## Existing architecture recalled

Other CML elements already solve the compiler/runtime version boundary differently. Cozy owns grammar/semantic normalization and generated producer output. CNCF owns the runtime ABI, admission and ComponentFactory bootstrap. The generated application source targets CNCF; CNCF does not link Cozy as its runtime model library.

Workflow/StateMachine is the primary recent example.

## Decision

Apply the same mechanism to Logical UI.

CNCF Phase 96 first freezes a Scala 3 consumer ABI and a hand-written expected-generated fixture. Cozy Phase 76 then maps the closed Phase 74 semantics into that ABI and generates Scala 3 source/metadata/provider bootstrap. CNCF consumes the generated fixture through ComponentFactory and proves Display Projection.

This makes the Scala 2.12/3 concern a normal code-generation boundary rather than a binary compatibility project.

## Why a new Phase

Phase 74 is already closed and correctly owns semantic authority. Reopening it would mix semantic-contract acceptance with a new producer/code-generation responsibility. Phase 76 therefore owns only the CNCF runtime producer handoff.

Phase 75 is already used by the SmartDox/site production sequence, so Phase 76 is the next free top-level phase number.

## Implementation consequence

The producer work must begin from the exact CNCF Phase 96 expected-generated fixture, not independently invent a second ABI. End-to-end acceptance is only claimed when the generated Scala 3 fixture compiles and CNCF admits it without a Cozy runtime dependency.
