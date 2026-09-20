# CNCF Phase 64 Cozy Producer Extraction

Date: 2026-09-20
Status: superseded planning decision; retained as design history
Renumbered: 2026-09-21 from Cozy Phase 66 to Cozy Phase 70

## Context

CNCF Phase 64 accepted SWF-01 through SWF-06 and then stopped before its real
fixture and handoff Steps. The accepted contracts require a Cozy-generated,
closed, typed Composite StateMachine / minimal Workflow semantic contract.
CNCF must consume that contract without reparsing CML, inferring meaning from
names, or substituting a handwritten canonical definition.

Cozy development had reached a coherent stopping point. Later, execution of the
CNCF StateMachine/Workflow path produced Cozy-side commits and a working-tree
delta for initial/final/history projection, state values, named guards, and the
SalesOrder transition fixture. When the CNCF task stopped performing Cozy
changes, those cross-repository producer changes were left without explicit
Cozy Phase ownership.

The producer projection required by CNCF Phase 64 therefore consists of both
admitting that imported Cozy delta and adding the remaining generated composite
semantic contract on top of it.

## Decision

Create Cozy Phase 70 for the new producer projection and handoff work. The
original proposal used Phase 66; that number now belongs to the separately
planned `sm-workflow` Retry / Timeout ABI extension.

- Preserve CNCF Phase 64 and every accepted SWF-01 through SWF-06 result.
- Preserve the earlier completed Cozy baseline and import the later
  CNCF-driven Cozy delta under explicit Phase 70 ownership.
- Preserve Cozy Phase 64 Capability Model IR and Phase 65 Dart generation.
- Allow Cozy Phase 70 to execute immediately; repository-local Phase numbering
  does not create a dependency on Cozy Phase 64 or Phase 65.
- Resume CNCF Phase 64 at SWF-07 after the Phase 70 producer handoff is
  accepted.

## Ownership

Cozy Phase 70 owns review and acceptance of the imported producer delta,
mapping current CML/Cozy IR into the generated semantic contract, producer
static analysis, exact provenance, deterministic generation, and producer
fixtures.

### Split correction (2026-09-20)

This ownership statement records the pre-split Phase 70 planning boundary. The
applied `PHASE-70 -> PHASE-70.1` split preserves that decision while assigning
the imported-delta admission, IR mapping, identity/configuration, and provenance
foundation to Phase 70. Phase 70.1 consumes that accepted foundation for static
analysis, action/profile metadata, deterministic fixture evidence, and the
CNCF handoff; it is the sequence's final-only repository-full validation owner.
The authoritative current ledgers are `docs/phase/phase-70.md`,
`docs/phase/phase-70-checklist.md`, `docs/phase/phase-70.1.md`, and
`docs/phase/phase-70.1-checklist.md`.

CNCF Phase 64 continues to own Composite StateMachine / minimal Workflow
semantics, admission and real consumer-side derivation, and its Phase 77
handoff. Phase 64.2 owns planner/interpreter work. Phase 77 owns generated
API/SPI admission, `ComponentFactory`, Provider, durable Continuation and the
external protocol.

## Reprioritization (2026-09-20)

The extraction and split were stopped before implementation. They combined a
possible generalized producer contract with the immediate route to
`sm-workflow`, producing an unjustified twelve-hour plan.

The released Cozy Phase 62.3 handoff already contains the CML-first Workflow
fixture, deterministic generated ABI evidence, typed Required SPI boundary,
and CNCF consumer handoff named by CNCF Phase 77 and `sm-workflow` Phase 1.
Consequently:

- Phase 70 is deferred and Phase 70.1 is superseded before implementation;
- CNCF Phase 64 will first compose the existing Phase 62.3 producer evidence
  with its accepted Phase 63.2 `CommittedTransition` and Phase 64 semantics;
- no new Cozy implementation is a prerequisite unless that narrow proof finds
  a concrete producer incompatibility;
- generalized producer metadata and diagnostics are post-vertical-slice work;
- the CNCF-driven Cozy commits and working-tree delta remain preserved for a
  later bounded review and are not silently absorbed into the critical path.

This is a priority correction, not a claim that the deferred producer ideas
are invalid. The first operational goal is to make `sm-workflow` run.

### Preserved future definition

Phase 70 remains as an independent future Cozy capability rather than an empty
placeholder. It will generate a closed, typed, deterministic Composite
StateMachine semantic artifact containing explicit identities/versions, typed
constituents, rules and actions, exact provenance, and producer diagnostics.
Its entry is evidence-bound: the first `sm-workflow` vertical slice must be
stable and a concrete consumer must identify semantic data missing from the
then-current released artifacts. CNCF Phase 89 is the paired downstream
consumer and owns admission, compatibility diagnostics, `ComponentFactory`
discovery, and runtime projection for the accepted artifact. The two Phases
share the same post-vertical-slice, concrete-requirement entry; neither is a
Phase 64 prerequisite. The former Phase 70.1 split and time estimate are not
retained as future execution authority.

## Historical consequence

The CNCF task's stop did not force Phase 64 to be discarded or renumbered. The
original proposal to proceed through a new Cozy producer boundary is now
superseded by reuse of the already closed Phase 62.3 handoff.
