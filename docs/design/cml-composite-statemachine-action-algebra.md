# CML Composite StateMachine Action Algebra

status=accepted
phase=47
slice=CSM-04
updated_at=2026-09-07

## Authority and scope

This document is the accepted Phase 47 CSM-04 authority for the logical Action
model and ActionProgram composition of a Composite StateMachine. It takes the
committed-transition and derived-transition causation contract in [CML Composite
StateMachine Semantics](cml-composite-statemachine.md) as its prerequisite.
It defines pure model meaning only; it does not add CML syntax, parser behavior,
generation, runtime behavior, executable behavior, or an executable
specification.

## Shared logical Action model

One typed, non-code LogicalAction model is shared by actions attached to
constituent transitions and actions attached to composite transitions. A
LogicalAction has:

- a stable Action identity;
- a typed Action kind;
- typed inputs or bindings; and
- a typed Operation reference whenever its kind denotes an Operation invocation.

An Action is model meaning, not an embedded script, provider handle, raw
expression string, or transaction policy. An Action whose kind does not denote
an Operation invocation does not acquire an Operation reference merely by name
matching.

## Action occurrences and provenance

An ActionOccurrence places one LogicalAction in a particular causal program. It
retains all of the following:

- occurrence identity;
- LogicalAction identity;
- origin level: constituent or composite;
- causal transition identity;
- placement: exit, transition, entry, or derived-transition; and
- source identity.

The same LogicalAction may occur more than once. Each placement is retained as
its own ActionOccurrence; occurrences are never deduplicated because their
causation, placement, origin, or source identity may differ.

LogicalAction's stable semantic identity—its kind, typed inputs or bindings,
and typed Operation reference when applicable—together with occurrence
provenance supplies the information a later static analyzer may use to identify
candidate duplicate or conflicting effects when sufficient semantic identity
exists. CSM-04 defines no duplicate or conflict rule, diagnostic, transaction
classification, or execution policy.

## ActionProgram algebra

An ActionProgram is an inspectable abstract free-program algebra over ordered
ActionOccurrences. Its operations are:

- `empty`;
- `singleton(occurrence)`; and
- ordered sequential composition, written here as `first ; then`.

`empty` is the identity of sequential composition. Sequential composition is
associative. Composition neither reorders nor removes occurrences. An
ActionProgram denotes logical causal sequencing only: it is not physical
execution, a transaction, or a claim about effect timing.

This is an abstract free-program representation. It selects neither a Free
library nor a Scala API.

## Causal composition

For one committed constituent transition, form its lower logical program by
sequencing its present ActionOccurrences in this placement order:

1. exit;
2. transition; then
3. entry.

Absent placements contribute no occurrence. Apply the CSM-02 post-transition
configuration and derivation rule after forming that lower program. If the
derived composite State is unchanged, append nothing. If it differs, the
exactly-one CSM-02 derived transition supplies its composite ActionOccurrences,
which are appended after the lower program as `derived-transition` placements.

Every occurrence retains its constituent or derived-transition identity and
causation. This contract makes no choice about concurrency, batching or
interleaving, effect timing, failure, transactions, retry, compensation, or
recovery.

## Interpretation boundary and current implementation

Generated or interpreted ActionPrograms may be inspected and interpreted
differently by production, test, simulation, review, or visualization consumers.
The ActionProgram itself remains pure model meaning.

The current one-nonempty-line transition action text projected as `MAction` or
`RulePlan` remains legacy metadata. It is not retroactively a LogicalAction,
is not executable, and is not ActionProgram input solely because its text has a
matching name. No current code is changed by this authority.

CSM-05 owns Workflow residual analysis. CSM-06 owns CML action grammar,
resolution, and diagnostics. CSM-07 owns deterministic generated IR and ABI.
CSM-08 owns CNCF interpretation and admission. CSM-09 owns visualization
metadata. Phase 47.1 owns transaction, reversibility, compensation, and
recovery semantics.

## Explicit deferrals

The following remain outside this accepted CSM-04 contract:

- CML action syntax, parsing, resolution, and diagnostics;
- generated IR, ABI, and library/API selection;
- production, test, simulation, review, and visualization interpreter policy;
- concurrency, batching/interleaving, effect timing, failure, transactions,
  retry, compensation, reversibility, and recovery;
- Workflow specialization analysis;
- static duplicate or conflict policy; and
- external repositories or cross-repository acceptance.

No statement in this document admits code, parser, generator, configuration,
fixture, test, runtime, or external-repository work.
