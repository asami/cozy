# Phase 59 Checklist: Summary Description to Visual Page PDF Projection

Phase Status: IN_PROGRESS

Development item: DEV-027

Predecessors: [Phase 40](phase-40.md), [Phase 58.1](phase-58.1.md), and
[Phase 58.2](phase-58.2.md)

phase=[Phase 59](phase-59.md)

Planning rule: one Article 9-shaped executable vertical slice; preferred 4–8 h
band.

## P590-01: Projection contract and authority boundary

Stage Status:
- Current status: DONE
- Owner: Cozy Document Project summary-slide projection
- Update rule: Do not mark DONE until paired design/specification documents
  define every input/output identity, authority boundary, mapping, and
  fail-closed condition below.

- [x] Define the strict `cozy.summary-slide-projection.v1` Profile in paired
      design/specification documents as a typed, explicit projection contract
      separate from `summary.yaml` and renderer layout.
- [x] Define exact Core, Document, Summary, media, profile, catalog, and
      pre-existing binding identity/currentness inputs and the generated
      VisualPageSet output.
- [x] Define an ordered Summary-Unit-to-page mapping and explicit treatment of
      any allowed expansion/pagination without silent renderer interpretation.
- [x] Define page-local edge/endpoint coverage: every selected Summary edge is
      mapped once, shared endpoint items may recur only with their same source ID
      and Core reference, and an edge-unrelated item occurs once.
- [x] Define source/reference and Step Flow-versus-local-Structure provenance
      carried into each generated page through existing catalog-supported
      semantics, without inventing Visual Page tag text.
- [x] Define rejection for unknown fields, duplicate IDs, stale identities,
      unresolved references, invalid catalog/binding selections, and any
      projection that would introduce ungrounded semantics.

Evidence: accepted paired design/specification contract; Work class D static
validation (`git diff --check` and local target checks), no SBT/product/PDF
generation; independent clean closure review disposition
`review-disposition-sha256-3b0b9d077ef17c9786adc728d3e04d9804dd9b0bfd8a3491124463d9ab1386f9`.

## P590-02: Strict v2 projection implementation

Stage Status:
- Current status: DONE
- Owner: Cozy Summary-to-Visual-Page projector
- Update rule: Do not mark DONE until generated artifacts are canonical,
  deterministically validated Phase 36 Visual Page inputs and all P590-02
  specifications pass.

- [x] Implement typed loading and validation of the admitted v2 inputs and
      Projection Profile without a permissive legacy adapter.
- [x] Generate a canonical `cozy.visual-page-set.v1` from the ordered
      projection mapping and bind it to the accepted catalog.
- [x] Keep transformation read-only and require an explicit PageSet writer
      output; A does not read Phase 40 `source` or independently validate the
      consumer binding. Retain ordinary output errors without special
      destination collision/alias/overlap protection.
- [x] Validate and use the corresponding pre-existing Visual Page binding
      required by the existing Phase 40 direct PDF route; do not generate,
      copy, or modify that binding.
- [x] Preserve exact Summary Unit, Core, and Document traceability in generated
      page identities/provenance without turning confirmation HTML into input.
- [x] Prove byte/identity determinism and strict rejection through executable
      specifications.

Evidence: P590-03A-VAL-002 ran
`testOnly cozy.document.CozySummarySlideProjectionSpec cozy.media.CozyMediaSummarySlidesPdfSpec cozy.media.CozyVisualPageSpec`
successfully (29 tests, three suites). The independent module-connection Step
review recorded no Current Boundary Blocker; see
[`2026-09-14-phase-59-module-connection-review.md`](../journal/2026/09/2026-09-14-phase-59-module-connection-review.md).

## P590-03: Phase 40 PDF route connection

Stage Status:
- Current status: DONE
- Owner: Cozy Document Project media integration
- Update rule: Do not mark DONE until the generated Visual Page authority is
  accepted by the pre-existing Phase 40 `summary-slides-pdf` route and no
  parallel PDF/receipt behavior exists.

- [x] Bind a Document Project summary-slide PDF operation/driver to the
      generated VisualPageSet, catalog, pre-existing binding, and existing media
      descriptor inputs.
- [x] Resolve `media.yaml` `source` once in coordinator X and pass the same
      path to A as output and B as input without relocating withdrawn
      output-protection checks to X.
- [x] Reuse `CozyMediaSummarySlidesPdf` and the existing PDF verifier,
      renderer-manifest, receipt, and review-currentness contracts.
- [x] Make source/profile/catalog/binding changes stale or reject the derived
      PDF through the existing receipt/currentness chain.
- [x] Prove that no new PDF renderer, PDF receipt schema, or layout authority
      is introduced.

Evidence: the P590-03A focused receipt covers the in-process coordinator,
three-page existing Phase 40 fixture route, renderer evidence, and
currentness/rejection paths. The result-only review journal retains the exact
typed receipt and review-disposition identities.

## P590-04: Article 9 acceptance

Stage Status:
- Current status: DONE
- Owner: Phase 59 Article 9 local acceptance
- Update rule: Do not mark DONE until the local Article 9 fixture demonstrates
  the entire declared projection-to-PDF path with executable evidence.

Slice P590-04A freezes one local Article 9 Japanese fixture and one executable
acceptance specification. It introduces no new renderer, receipt schema,
production source, external operation, or responsive/layout work. The fixture
must contain a static complete Profile with its declared ordered page sequence;
test code may generate derived PageSet/PDF output only through the existing
Phase 59 coordinator and Phase 40 renderer seam.

- [x] Author the Article 9 Japanese Projection Profile and local media-driver
      fixture bound to the already admitted v2 inputs.
- [x] Generate the Article 9 summary-slide PDF through the existing Phase 40
      route and verify page order/count and exact source mapping.
- [x] Verify selected Step Flow and local Structure remain distinct and
      Core-grounded through existing catalog-supported visual semantics, with
      no invented tag text claimed from the Visual Page contract.
- [x] Cover stale propagation and invalid/missing source, profile, catalog, and
      binding cases without fresh PDF receipt visibility.
- [x] Preserve Phase 58.1/58.2 confirmation behavior as separate evidence and
      prove their owning specifications remain passing.

Evidence: `P590-04A-FIX-VAL-002` passed the dedicated Article 9 acceptance
specification, and `P590-04A-ACC-003` passed its P590-02/P590-03 and Phase
58.1/58.2 accumulator. The independent lightweight review recorded no Current
Phase Blocker in review-disposition
`ad50e95af977e87e046a91ed067edd793b01d8007a8ba106618d4b2c1bd318e5`.

## P590-05: Phase closure

Stage Status:
- Current status: PLANNED
- Owner: Phase 59 closure
- Update rule: Mark DONE only when all preceding stages are DONE on the settled
  tree and every closure gate below records the same accepted scope.

- [ ] Complete focused validation for the projector, media connection,
      currentness, and Article 9 acceptance driver.
- [ ] Complete one independent full-Phase review with no unresolved Current
      Phase Blocker.
- [ ] Complete full Cozy validation through the shared SBT lock.
- [ ] Complete a distinct local Phase release commit. No publication,
      deployment, upload, push, registration, or external production
      integration is claimed.

## Closure boundary

The following remain outside Phase 59 and do not appear as OPEN work:

- a new summary-slide PDF/PPTX renderer or renderer/receipt redesign;
- physical slide layout in Summary Description;
- migration of all Document Projects or scaffold/profile redesign;
- SmartDox or SimpleModeling.org production integration; and
- publication, registration, deployment, upload, push, external-service
  mutation, semantic inference, or automatic human acceptance.
