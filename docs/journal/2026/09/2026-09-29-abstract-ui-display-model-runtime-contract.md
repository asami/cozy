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

## Planned-scenario-first acceptance decision

The user-approved decision `COZY-P74-DRIVER-SOURCE-01` selects a
planned-scenario-first method for the Phase 74 minimum runtime contract. Cozy
uses fake List/Detail resources to prove shared selection, compact navigation,
expanded detail update, and configuration-driven visible fields without
introducing a production Knowledge Candidate schema.

The decision separates two acceptance tracks. Cozy executable fixtures provide
local provisional acceptance now; actual Android mock integration is later
integration acceptance and is not claimed by this journal entry or Phase 74
closure. The pinned driver is `KnowledgeHubProject/nict-editing-studio-app`,
revision `48eb43b8eb839137e17ef4c944b3db4a73d0cec3`,
`docs/phase/phase-2.md`, scenario `resource-list-detail`. The pinned CNCF
consumer reference is goldenport-cncf Phase 96, revision
`1f9b3a392570a36af1bcb1e634e77c085525b0ce`; it may consume the Cozy
provisional contract without waiting for Android integration. CNCF Projection,
Protocol, and Android changes remain outside this Phase.
