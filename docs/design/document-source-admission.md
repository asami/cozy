# Document Source Admission Design

P710-02E introduces a deliberately narrow connection between source structural admission and the existing hash-bound document render loaders. `CoreStructure` is the shared semantic context: it carries the admitted Core and catalog maps, but no hash. Source models carry actual IDs and parsed content only.

The existing tree, document, and summary semantic validators are factored once and reused. The source path does not create dummy bindings, compatibility adapters, a parallel DSL parser, authoring behavior, rendering, or a receipt/currentness framework. Legacy models and projections continue to own their hash-bound behavior unchanged.

This is required by the Phase 71 selected private graph: Codex authors meaningful source DSLs and Cozy validates their native semantic grammar before later private-driver work. General projection/media identity removal remains Phase 71.1 scope.
