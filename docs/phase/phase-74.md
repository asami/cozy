# Phase 74: Abstract UI Runtime Contract

status=closed
closed_at=2026-09-29
planned_at=2026-09-29
strategy=textus-knowledge-workbench/docs/strategy/knowledge-application-integration.md
driver=KnowledgeHubProject/nict-editing-studio-app
related_phase=goldenport-cncf Phase 96

## Goal

Freeze the minimum target-neutral Abstract/Logical UI runtime contract needed by server-driven Display Models while preserving the accepted Logical UI authority established by the existing UI phases.

Phase 74 does not create a second UI metamodel. It selects and, only where necessary, extends the existing Logical UI vocabulary so runtime Display Model instances can use the same semantics as compile-time target projection.

## Initial scope

Driven by the planned Android-first Editing Studio List/Detail scenario, establish the minimum runtime-facing semantics for:

- Resource List;
- ListItem;
- Resource Detail;
- Section;
- Field;
- displayable Value;
- Action;
- navigation/selection relationships;
- presentation roles for the proven minimum: title, subtitle, description and status;
- target-neutral adaptive intent required by the first List/Detail proof; and
- schema/version/provenance information required by runtime consumers.

## Runtime contract principle

The Abstract/Logical UI Model defines the vocabulary. A CNCF Display Model is a runtime instance of that vocabulary.

A semantic server property need not survive under the same name. For example, a product_name semantic property may be projected to the presentation role title. Server-native or opaque values such as JSON must be projected to displayable values before they become Display Model content.

Domain meaning remains available through semantic View Models. The Abstract UI runtime contract must not require a generic presentation client to understand that meaning.

## Relationship to existing phases

- Phase 43/50 Logical UI remains the target-neutral semantic UI authority.
- Phase 51 Flutter Target Projection remains the compile-time Flutter projection path.
- Phase 74 defines the runtime/server-driven use of the same target-neutral semantics.
- CNCF Phase 96 owns Display Projection, Display Model instances and Display Model Protocol.
- textus-flutter-core later consumes the protocol; TFAF owns visual realization.

## Development method

Do not attempt to complete a universal UI vocabulary in this Phase. The active
scenario-led hypothesis method starts with planned fake List/Detail resources:
each ListItem has an opaque resource identity, compact selection proposes
navigation, expanded selection updates a logical detail region, and a field
configuration changes visible Detail fields without changing selection logic.
Generalize only semantics that remain valid for other applications and targets.

This starts Cozy local provisional acceptance through executable fixtures. It
does not claim an actual Android mock proof: Android integration is a separate
future acceptance stage. Phase closure never claims that integration without
its own evidence. CNCF Phase 96 may consume the provisional Cozy contract
without waiting for the Android stage.

## Acceptance criteria

### Local provisional acceptance

Phase 74 local provisional acceptance is met when:

1. one versioned minimum Abstract/Logical UI runtime contract covers the planned Editing Studio List/ListItem/Detail scenario;
2. List, ListItem, Detail, Section, Field, displayable Value and Action semantics are target-neutral and contain no Flutter Widget types;
3. the contract clearly separates semantic View properties from presentation roles;
4. CNCF Phase 96 can construct Display Model instances without inventing a parallel UI vocabulary;
5. compile-time Flutter projection and runtime Display Model paths are documented as sharing one Logical UI semantic authority; and
6. focused executable/model fixtures prove the minimum List/ListItem/Detail/Section/Field/DisplayValue/Action contract and its selection/navigation behavior.

### Follow-up integration acceptance

Actual Android mock validation is a separate follow-up integration acceptance. It
records any Android integration gaps and refinements; its completion is not a
prerequisite for Phase 74 local provisional acceptance and this Phase makes no
executed Android claim.

## Non-goals

- Complete UI metamodel coverage.
- Flutter Widget tree modeling.
- CNCF wire protocol implementation.
- Flutter client/runtime implementation.
- Actual Android mock integration validation.
- Editing Studio-specific domain semantics in Cozy.
- Visual design or application-specific styling.

## Local acceptance and release evidence

Step 74.1 / Slice 74.1A was accepted in local commit
`d3ddb86f4251ea07714ff2ca586c441258201b06` after protected focused conformance
review and 52 passing runtime/legacy Logical UI tests in 6 suites. The
external-package Cozy fixture proves compact navigation, expanded detail
selection, and configured visible fields from the planned Editing Studio
List/Detail scenario.

The one comprehensive Phase review covered the Phase base
`fe71d0a9e7446af2e8d3a500fe4b50a0af1c0837` through that Step commit. Its sole
CPB-P74-001 required executable proof for malformed non-null DetailTarget IDs.
Repair cycle 1 added four exact diagnostic cases to the existing scenario;
8 focused tests passed and one independent focused closure review returned
PASS, with no remaining blocker or accepted Hygiene/Development Candidate.

Repository-full validation `cozy-P74-HYG-PREREQ-FULL-VAL01-A1` passed 2,053 tests
in 161 suites with 0 failures and 0 aborted suites (8 canceled). SBT and wrapper
exited 0 and released the shared lock. Receipt `1f321e268e8f2e792ec2e3c9a9fc1ccb9c25f25f83009386045dbb23fad2d3fc`
is retained through verified non-input closure-document drift. Existing test
prerequisites were restored in separately accepted Hygiene commit
`ab4941c4cdfa4069a4c7b8ffe3512dc82330b1e0`.

Phase 74 is closed for local provisional acceptance. The distinct local release
commit carries `Phase-Closure-Binding: PHASE-74`; its resulting hash belongs to
the closure receipt. The release keeps development version `0.3.3-SNAPSHOT`.
Actual Android mock integration and CNCF wire protocol implementation remain
separately owned follow-up work.

## References

- docs/phase/phase-43.md
- docs/phase/phase-50.md
- docs/phase/phase-51.md
- docs/journal/2026/09/2026-09-29-abstract-ui-display-model-runtime-contract.md
