# CML Composite StateMachine Static Analysis

status=accepted
phase=47
slice=CSM-03
updated_at=2026-09-07

## Authority and scope

This document is the accepted Phase 47 CSM-03 authority for static analysis
of a Composite StateMachine. It takes the accepted [CML Composite
StateMachine semantics](cml-composite-statemachine.md) as its semantic
prerequisite. In particular, it analyzes the complete state configuration and
the pure, exactly-one derivation rule contract defined there.

This document defines analysis semantics only. It does not add CML syntax,
parser behavior, runtime behavior, generator behavior, executable behavior,
or an executable specification. It does not decide how source syntax is
lowered into the normalized analysis input.

## Normalized analysis input

Analysis consumes a normalized finite input with the following semantic parts:

- constituent bindings with unique role identities; role identity remains
  distinct from the referenced StateMachine definition identity;
- a finite analyzable State universe for every role, with each State carrying
  its role-qualified State identity;
- pure typed derivation rules over role-qualified constituent States, each
  retaining rule identity and source identity;
- normalized constituent transition edges, each carrying its role identity,
  source and target State identities, transition identity, and source
  identity; and
- an optional normalized initial configuration, when one has been supplied by
  an upstream contract.

The normalized input must contain a State universe for every declared role. A
missing role-to-universe mapping or an empty State universe is an input
diagnostic and must be rejected or reported; the analyzer must not silently
omit that role. A supplied initial configuration must be a complete mapping
for all roles and use States from the corresponding role universes. Invalid
initial configuration data is likewise diagnosed rather than repaired by
omission.

State hierarchy and history lowering, and the source syntax that could produce
either, remain deferred. They are not assumed by this input contract.

## Complete configuration domain

For the normalized role set R, with finite State universe U_r for each role r,
the complete configuration domain is the Cartesian product of U_r for every
role in R.

Each configuration therefore contains exactly one State identity for every
declared role. A role is never removed from the domain because no transition
or rule currently mentions it. Missing role mappings and empty universes
remain diagnostics even when they would make a product empty.

The complete domain is the reference domain for rule analysis and for any
full-domain potential observations. It is not, by itself, a claim that every
configuration can occur at runtime.

## Structural may-reachability

When a valid normalized initial configuration is supplied, construct the
configuration graph by starting at that configuration and repeatedly applying
one constituent transition edge at a time. An edge is applicable only when
its role's source State is the State currently held by that role. Applying
the edge changes that role to the edge's target State and leaves every other
role State unchanged. Structural may-reachability is the least set of
configurations containing the initial configuration and closed under those
applications.

This is structural may-reachability. Runtime event delivery, guard
satisfiability, action effects, scheduling, and other runtime conditions are
not evaluated in this slice. A constituent edge with a guard or other runtime
condition is still a possible may-edge for this analysis. The edge and its
condition identity remain available as provenance.

If no initial configuration is supplied, reachability is reported as
indeterminate. The analyzer must not claim exact reachability, exact
deadness, or CSM-02 exactly-one compliance from that input. It may still
analyze the complete domain and report potential observations, explicitly
labeled as potential rather than reachable or exact.

## Derivation-rule analysis

For every analyzed configuration, compute the complete set of matching
derivation rules. Analysis of a structurally may-reachable configuration has
the following CSM-02 resolution contract:

- exactly one matching rule is a resolved derivation, and its output is the
  selected composite State;
- zero matching rules is an invalid, uncovered derivation, and no selected
  composite State exists; and
- two or more matching rules is an invalid, ambiguous derivation, and no
  selected composite State exists.

The zero-match and multiple-match cases are findings that violate the CSM-02
exactly-one derivation requirement. Only a resolved derivation may supply a
composite State for an edge's source or target configuration. For a relation
whose relevant source or target derivation is invalid, the structural
configuration graph and all constituent-edge provenance remain available, but
no derived State or derived-graph edge is emitted for that relation. The
uncovered or ambiguous diagnostic and the derivation-blocked relation evidence
are retained; any state, liveness, or derived-edge conclusion affected by an
invalid derivation is `indeterminate`, not a definite conclusion.

Priority, default rules, and declaration order never select a winner or
repair either finding. A consumer must not use them to bypass exactly-one
semantics.

An impossible rule has an empty match set across the complete configuration
domain. Subsumption, duplicate, and redundancy observations are reported
without weakening the exactly-one contract:

- overlapping or subsumed nonempty rules remain ambiguity findings wherever
  their match sets overlap;
- duplicate or subsumed rules retain their rule identities and match-set
  evidence; and
- an output-preserving redundant rule is only a secondary structural finding.
  It never licenses implicit rule removal or implicit rule selection.

When reachability is indeterminate, full-domain rule observations may still
be exact with respect to the complete domain, while reachability-qualified
claims remain indeterminate.

## State and configuration liveness

A derived State is structurally unreachable when no structurally
may-reachable configuration with a resolved derivation derives that State. If
reachability is indeterminate, or if an invalid derivation can affect the
relevant conclusion, the analyzer must not report this as an exact
unreachable finding.

A configuration is dead when it has no outgoing constituent may-edge in the
configuration graph under analysis. A derived State is dead when one or more
structurally may-reachable, resolved configurations derive that State and all
of those configurations are dead configurations. If an invalid derivation
can affect which configurations derive the State or the relevant outgoing
relation, the derived-State deadness result is `indeterminate`.

For a constituent may-edge whose endpoint derivations are resolved, the
analyzer derives the composite State before and after the edge using the rule
results. A derived outgoing edge is state-changing only when those derived
States differ. Constituent edges that preserve the derived State are not
silently discarded: they are reported as stuttering causes. Stuttering
demonstrates an outgoing configuration edge and therefore prevents dead-
configuration status, while remaining compatible with a distinct
quotient-terminal observation. A quotient-terminal derived State is observed
when it has no state-changing derived outgoing edge; it is not dead and may
have stuttering outgoing constituent edges. The quotient-terminal observation
is also `indeterminate` where an invalid derivation blocks a relevant
derived-edge conclusion.

Terminal intent and diagnostic severity policy are deferred. A state that is
intentionally terminal is not reclassified here by an inferred convention.

## Configuration and derived composite graphs

The configuration graph contains may-reachable configuration vertices when a
valid initial configuration is available. Its edges retain the full
constituent transition causation, including role identity, source and target
State identities, transition identity, and source identity. A no-initial
analysis may retain a separate full-domain potential graph, but that graph is
not called a reachable graph.

The derived composite graph contains only state-changing quotient edges from
configuration-graph relations whose relevant source and target derivations
are resolved. Every derived edge retains the full set of constituent
causation identities that can produce it. Multiple distinct constituent
transitions producing the same derived edge therefore remain distinct
provenance, not a single collapsed cause.

When a relation has an invalid source or target derivation, no derived graph
edge is emitted for it. Its derivation-blocked relation evidence, including
the relevant diagnostic and constituent causation identities, remains
available alongside the structural configuration relation. The absence of
that derived edge must not be interpreted as proof of quotient-terminal
behavior, deadness, or any other definite liveness outcome.

No expected-versus-allowed derived-edge contract exists in this slice. An
edge is reported as an observed or potential analysis result and is not
labelled `unexpected` unless a future accepted contract supplies the
comparator.

State-preserving constituent edges remain analysis observations and are
reported as stuttering causes even though they do not appear as derived
state-changing edges.

## Exact and symbolic analysis status

Exact enumeration is the reference semantic result whenever it is tractable.
A symbolic method may establish sound facts without enumerating the complete
domain, but every result must retain a status distinguishing:

- `exact`: the complete finite domain or graph was analyzed;
- `proven`: the stated fact is established soundly by the analysis;
- `possible`: the observation is not disproved but is not established as an
  exact or proven fact; and
- `indeterminate`: the available analysis cannot establish the claim.

A symbolic or otherwise incomplete approximation must never silently sample,
and must never claim coverage, exclusivity, reachability, or deadness from an
incomplete approximation. This contract does not set implementation
thresholds or choose algorithms.

## Finding identity and provenance

Every finding retains enough identity for later diagnostics, visualization,
review, and generation. At minimum, applicable findings preserve the
involved role identities, State identities, rule identities, transition
identities, and source identities, together with the configuration or
configuration relation that produced the finding. Causation sets are retained
rather than replaced by a display label or a guessed name.

Analysis findings may be consumed later by CSM-06 validation, CSM-07
generation, CSM-08 CNCF integration, and CSM-09 visualization/review, but
those consumers do not acquire implementation or presentation semantics from
this document.

## Explicit deferrals

The following remain outside this accepted CSM-03 contract:

- action semantics and action composition;
- Workflow specialization or Workflow profile decisions;
- CML grammar and parser behavior;
- runtime guard or event evaluation;
- implementation algorithms, configuration, and complexity thresholds;
- generators and CNCF integration;
- visualization presentation; and
- external repositories or cross-repository acceptance.

No CML DSL syntax, source-level initial-state syntax, or executable
specification is introduced by this document.
