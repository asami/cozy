# Phase 74: Abstract UI Runtime Contract

status=planned
planned_at=2026-09-29
strategy=textus-knowledge-workbench/docs/strategy/knowledge-application-integration.md
driver=KnowledgeHubProject/nict-editing-studio-app
related_phase=goldenport-cncf Phase 96

## Goal

Freeze the minimum target-neutral Abstract/Logical UI runtime contract needed by server-driven Display Models while preserving the accepted Logical UI authority established by the existing UI phases.

Phase 74 does not create a second UI metamodel. It selects and, only where necessary, extends the existing Logical UI vocabulary so runtime Display Model instances can use the same semantics as compile-time target projection.

## Initial scope

Driven by the Android-first Editing Studio mock implementation, establish the minimum runtime-facing semantics for:

- Resource List;
- Resource Detail;
- Section;
- Field;
- displayable Value;
- Action;
- navigation/selection relationships;
- presentation roles such as title, subtitle, description, status and image where justified by the driver;
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

Do not attempt to complete a universal UI vocabulary in this Phase. Derive the minimum contract from the executable Editing Studio List/Detail mock UI and generalize only semantics that remain valid for other applications and targets.

## Acceptance criteria

Phase 74 completes when:

1. one versioned minimum Abstract/Logical UI runtime contract covers the Editing Studio List/Detail scenario;
2. List, Detail, Field, displayable Value and Action semantics are target-neutral and contain no Flutter Widget types;
3. the contract clearly separates semantic View properties from presentation roles;
4. CNCF Phase 96 can construct Display Model instances without inventing a parallel UI vocabulary;
5. compile-time Flutter projection and runtime Display Model paths are documented as sharing one Logical UI semantic authority; and
6. focused executable/model fixtures demonstrate the minimum contract.

## Non-goals

- Complete UI metamodel coverage.
- Flutter Widget tree modeling.
- CNCF wire protocol implementation.
- Flutter client/runtime implementation.
- Editing Studio-specific domain semantics in Cozy.
- Visual design or application-specific styling.

## References

- docs/phase/phase-43.md
- docs/phase/phase-50.md
- docs/phase/phase-51.md
- docs/journal/2026/09/2026-09-29-abstract-ui-display-model-runtime-contract.md
