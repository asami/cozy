# Capability Model IR Development Item

Date: 2026-09-16
Status: admitted for planning
Development item: DEV-032
Target phase: Phase 64

## Record

SimpleModeling adopted Capability Model as a non-instantiated IR. Cozy must
therefore support authoring and deterministic generation of Application and
Component Capability declarations while preserving their distinction from
executable Operation, Workflow, and StateMachine contracts.

This entry admits DEV-032 and creates Phase 64 as the Cozy execution ledger.
It records chronology only. The proposed syntax is not normative until Phase
64 promotes accepted design/specification and executable specifications.

## Cross-repository boundary

- CNCF Phase 78 owns the public Component Capability semantics, versioned
  projection, ABI admission, and package exposure.
- Cozy Phase 64 owns CML-facing IR, parser/metamodel, validation, diagnostics,
  source provenance, and generated projection production.
- Textus CBD Support Phase 11 owns ingestion and discovery of the admitted
  projection.

Cozy must consume the CNCF contract rather than define a competing external
identity. cbd-support must consume generated/admitted evidence rather than
reparse CML.

## Preserved boundary

Capability remains a model record. Cozy generation does not instantiate it or
assign runtime control-flow semantics. No implementation, publication, or CML
syntax decision is completed by this journal entry.

## References

- [Proposal](../../../notes/capability-model-ir-support-proposal.md)
- [Phase 64](../../../phase/phase-64.md)
- `asami/goldenport-cncf/docs/phase/phase-78.md`
