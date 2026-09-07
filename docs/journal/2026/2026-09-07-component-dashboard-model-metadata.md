# Component Dashboard Model Metadata

Date: 2026-09-07

A cross-project discussion with Textus CBD Support identified model metadata needed for a semantic Component Dashboard.

The Dashboard Content View will navigate to DomainModel View and Use Case View. DomainModel View separates Static Model and Dynamic Model. Static Model contains Structure View and Classification View. Dynamic Model uses Workflow as the overview and StateMachine as the lifecycle deep dive.

The main Cozy implication is that generated/published CML metadata must preserve semantics rather than only diagram-ready names and edges. Structure metadata must keep composition, aggregation, and association distinct, including ownership and lifecycle information where modeled. Classification metadata must keep generalization, trait, and powertype distinct while allowing them to be projected together. Workflow, StateMachine, and Use Case metadata need stable cross-references so CBD Support can navigate among views without reconstructing identity from names.

Composition and aggregation are especially important. Their distinction is intended to provide modeling value through lifecycle rules, aggregate/persistence boundaries, command/API constraints, and runtime validation. Cozy should represent these semantics faithfully; runtime enforcement belongs to CNCF and Dashboard presentation belongs to Textus CBD Support.

Detailed requirements are recorded in `docs/notes/component-dashboard-model-metadata.md`.
