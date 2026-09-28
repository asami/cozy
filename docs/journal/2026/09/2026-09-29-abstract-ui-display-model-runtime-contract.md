# Abstract UI / Display Model Runtime Contract

Date: 2026-09-29
Status: architectural direction

## Direction

The accepted Logical UI developed in Phases 43/50 remains the target-neutral semantic UI authority. The new Display Model concept does not replace it.

Cozy Abstract/Logical UI Model defines the vocabulary/schema used by CNCF Display Model runtime instances produced by Display Projection. Those instances cross the Display Model Protocol and are consumed by client runtime/visual realization.

The minimum vocabulary is driven by the Editing Studio Android reference path and begins with List, Detail, Section, Field, displayable Value, Action, navigation and relevant adaptive intent.

A Display Projection may map domain names such as product_name to presentation roles such as title and convert opaque/server-native values such as JSON into displayable values. Domain meaning remains available separately through CNCF View Model; it is intentionally not required by generic presentation clients.

## Phase alignment

Phase 50 target-ready Logical UI remains target-neutral. Phase 51 Flutter Target Projection remains a compile-time Flutter projection path. The Display Model Protocol adds a runtime/server-driven path that shares the same target-neutral UI semantics rather than creating a second abstract UI authority.

The two paths should converge on compatible visual semantics: compile-time Logical UI to Flutter Target Plan/configuration/source; runtime View to Display Projection to Display Model Protocol to client runtime.

Cross-project coordination is owned by textus-knowledge-workbench strategy knowledge-application-integration.md.
