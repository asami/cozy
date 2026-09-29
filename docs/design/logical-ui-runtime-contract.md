# Logical UI Runtime Contract Design

Document role: normative design for Cozy's provisional runtime instances of
the existing Logical UI vocabulary. The behavior contract is
[`logical-ui-runtime-contract.md`](../spec/logical-ui-runtime-contract.md).

## Shared vocabulary boundary

Phases 43 and 50 retain target-neutral Logical UI semantic authority. Phase 74
extracts only `Purpose`, `Display`, and `InteractionPattern` and their existing
singleton values into public `CozyLogicalUiVocabulary`. The package-local
`CozyLogicalUiSemantics` keeps public type aliases and stable value aliases, so
legacy imports, types, singleton identities, semantic IDs, schema/content, and
projection/review behavior remain unchanged. It does not make the whole
Semantics projection public or create a second UI vocabulary.

## Runtime instance boundary

`CozyLogicalUiRuntime` is a small, immutable, target-neutral instance model.
It describes List, ListItem, Detail, Section, Field, typed display value,
Action, and logical Detail identity. Presentation roles bridge illustrative
domain projection to generic display: a fixture may map `productName` to
TitleRole and source content to displayable text, while the semantic property
name and source-native representation do not become runtime-contract members.

The model includes exact contract and source provenance metadata because Phase
96 needs an attributable provisional value. The metadata does not assert
accepted Logical UI authority and does not claim Android execution. Contract
admission is exact, so a future schema cannot accidentally enter the v1
boundary.

No Widget, layout, styling, route, target framework, raw JSON, operation
implementation, network endpoint, callback, lifecycle, or production Knowledge
Candidate schema crosses this boundary.

## Selection boundary

`CozyLogicalUiSelection` is a thin, pure realization-intent boundary. It first
admits a List model and selected item, then returns a shared logical
`Selection` and `DetailRequest`. Compact and expanded clients differ only by
returned placement/pattern: navigation versus detail-region update. A client
later realizes that intent and resolves a matching Detail; Cozy does neither.

This keeps resource selection and Detail identity independent from visual
realization while allowing the planned List/Detail fixture to prove both
adaptive forms. Generic Action descriptors remain inert command-pattern
metadata.

## Ownership and acceptance

Cozy owns the provisional runtime vocabulary, exact local validation, and
fixture-led executable acceptance. CNCF Phase 96 can build on this contract
without waiting for an Android mock; it owns Display Projection, runtime
Display Model instances, and protocol work. Client/TFAF realization and actual
Android integration are separate follow-up acceptance work.

The accepted Logical UI candidate/acceptance transition, codecs, target source
generation, release authority, and existing compile-time Flutter path remain
outside this design.
