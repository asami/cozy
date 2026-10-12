# Phase 81: CML-derived Review Scope Metadata

status=planned
planned_at=2026-10-13
driver=sm-workflow Phase 6
consumer=textus-cbd-support Phase 15
contract=goldenport-cncf Phase 106 minimum generic review contract
checklist=[Phase 81 checklist](phase-81-checklist.md)

## Goal

Provide the model relationships needed for a real Step review and integrated
Phase review, using existing CML/Cozy/CAR metadata paths. Model information helps
identify changed and connected scope; it is not proof of quality or completeness.
Source: [GitHub-origin journal](../journal/2026/10/2026-10-13-staged-quality-review-matrix.md).

## Bounded implementation

1. RMS-01: With CNCF Phase 106 and cbd-support Phase 15, fix the small shared
   model fixture and required identities/relationships. Start with Component,
   Service/Operation, Usage Contract-facing behavior and generated-artifact
   relationships. Include Entity/DataStore, Display and Capability/annotation
   relationships only as required by this driver; record unavailable information
   explicitly rather than claiming exhaustive coverage.
2. RMS-02: Extend existing semantic model/metadata generation to expose those
   relationships, origins and coverage limits. CML remains authoritative and
   generated Scala is a projection. Consume normalized CNCF representations at
   existing boundaries; do not implement a new traceability database or source
   snapshot store. Existing finding-disposition properties remain Phase 77 work.
3. RMS-03: Demonstrate changed Operation -> related contract/connected Operations
   and integrated model scope -> provider input. After a model repair, regenerated
   metadata identifies the affected relationship scope without asserting that
   unchanged metadata means unchanged implementation.
4. RMS-04: Hand actual generated output to cbd-support/sm-workflow in the shared
   driver; accept the producer's results, integrated specs and independent review.

## Boundaries and acceptance

Cozy supplies knowledge, not quality decisions or a second review state machine.
Do not encode Step/Phase/Final in generic generated runtime APIs. No entire-CML
expansion, new site/media work, hash/copy/TTL proof, unchanged-content certificate
or automatic property insertion is required. Runtime implementation and test
evidence remain separate from declared model relationships.

Deliver a fixture with two Steps that are locally plausible but conflict at an
integrated Usage Contract, and the metadata after an affected repair. Parser/model
and generation specs verify declared identity, relationships, missing/invalid
input diagnostics and truthful partial coverage. Consumer invocation confirms
these outputs can guide the actual review; source-level inspection alone is not
the connected outcome.

[sm-workflow Phase 6](https://github.com/asami/sm-workflow/blob/main/docs/phase/phase-6.md)
owns P6-SQ01..05, [CNCF Phase 106](https://github.com/asami/goldenport-cncf/blob/main/docs/phase/phase-106.md)
owns generic contracts and [cbd-support Phase 15](https://github.com/asami/textus-cbd-support/blob/main/docs/phase/phase-15.md)
owns evaluation. Minimum contract -> Step route -> integration -> repair/Final is
the delivery order, not completion of all three roadmaps before connection.
Close on this producer checklist plus its bounded consumer acceptance, without
waiting for the whole sm-workflow Phase 6 closure. Required producer work belongs
here; optional metadata breadth must not delay the working route.

This plan does not resume/reorder active Cozy work or change its recorded history.
