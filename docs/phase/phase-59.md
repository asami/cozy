# Phase 59: Summary Description to Visual Page PDF Projection

Status: IN_PROGRESS

Plan date: 2026-09-13

Development item: DEV-027

Predecessors:

- [Phase 40](phase-40.md): accepted `summary_slides_pdf` rendering,
  receipt, and currentness route;
- [Phase 58.1](phase-58.1.md): v2 Document Description and Summary
  Description authorities; and
- [Phase 58.2](phase-58.2.md): Article 9 confirmation projections and
  semantic inspection evidence.

## Goal

Make an admitted v2 `content/<locale>/summary.yaml`, bound to its exact
`core.yaml` and `document.yaml`, a deterministic input to the already accepted
Phase 40 `summary_slides_pdf` route.

Phase 59 owns the missing strict projection from concise Summary Description
meaning into an explicitly bound `VisualPageSet`. It connects that generated
PageSet with one pre-existing fixed Visual Page binding, then invokes the
existing Phase 40 renderer, PDF verification, receipt, and currentness
contracts. It does not create another PDF renderer or turn the Summary
Description into a layout format.

## Phase Plan Gate

Phase Plan Gate: PROCEED

- target: one Article 9-shaped executable vertical slice; preferred 4–8 h
  band
- planning_demand: protected-decision
- recommended_parent_profile: `gpt-5.6-sol / xhigh`
- expensive_reasoning_kernel: preserve the v2 semantic-authoring boundary
  while producing a current, accepted Phase 40 presentation authority with no
  ungrounded or silently inferred slide meaning
- estimated_at_recommended_profile: 7–8 h
- incoming_handoff: closed Phase 40 PDF route and closed Phase 58.1/58.2
  Article 9 v2 source and confirmation evidence
- frozen_outcome: an explicit Summary-to-Visual-Page projection profile,
  deterministic Visual Page generation with a pre-existing binding, and one
  current Article 9 summary-slide PDF through the existing Phase 40 path
- short_child_exception: none
- source: user direction on 2026-09-13 to connect the latest Document Project
  DSL to the Phase 40 summary-PDF capability

## Full Phase review selection

- selected reviewer: `gpt-5.6-sol` / `xhigh`
- selection evidence: the Phase crosses three closed authority boundaries
  (v2 Summary Description, Phase 36 Visual Page, and Phase 40 PDF/receipt)
  and must prove that currentness and exact source traceability survive their
  connection without weakening any of them.
- reasoning mode: standard
- boundary: the eventual Phase base through all accepted P590-01 through
  P590-05 Step commits, including an Article 9 driver and no unrelated
  Document Project migration.

## In-scope work

| ID | Outcome | Status |
| --- | --- | --- |
| P590-01 | Define the strict `cozy.summary-slide-projection.v1` contract in paired design/specification documents: exact v2 Core/Document/Summary/media identities, pre-existing catalog/binding selection, ordered Summary-Unit-to-Visual-Page mappings, provenance/currentness, Article 9 three-page projection, and fail-closed rejection. | complete |
| P590-02 | Implement strict typed loading and deterministic read-only projection of a canonical Phase 36 `VisualPageSet`, plus a required explicit-output writer, from admitted v2 inputs and the projection profile, using one pre-existing fixed Visual Page binding. | complete |
| P590-03 | Connect the generated Visual Page Set and that pre-existing binding to the existing Phase 40 `summary-slides-pdf` media route and its existing PDF verification, receipt, and currentness behavior without a parallel renderer or receipt scheme. | complete |
| P590-04 | Add a real Article 9 Japanese projection driver and executable acceptance for page order, source/structure traceability, semantic marks, deterministic output, current/stale propagation, and rejection paths. | complete |
| P590-05 | Continue the settled scope with the frozen Revision 2 relationless standalone Visual Page capability, then complete focused validation, independent full-Phase review, full Cozy validation, and a distinct local Phase release commit. | in progress |

## Authority boundary

- `content/core.yaml` remains the locale-independent recursive logic-tree
  authority established by Phase 58.
- `content/<locale>/document.yaml` remains the complete localized document
  authority established by Phase 58.1.
- `content/<locale>/summary.yaml` remains the localized concise-content
  authority: it owns selected meaning, order, wording, emphasis, authored
  semantic diagrams, retained points, and explicit omissions. It does not own
  CSS, coordinates, fonts, pagination, or PDF parameters.
- A new explicit Summary Slide Projection Profile is a derived-presentation
  selection contract. It binds exact source identities and declares the
  admissible Summary Unit-to-Visual-Page projection, catalog, and Visual
  Pattern choices. It must not invent a Core node, Relation, Flow, source,
  retained point, or omission absent from the admitted inputs.
- The generated `VisualPageSet` and the profile-selected pre-existing binding
  are the named presentation authorities consumed by the existing Phase 40
  route. They retain exact source/projection provenance and are stale when any
  consumed authority or catalog/binding input changes. Phase 59 never generates
  or changes the binding.
- P590-05A admits only the closed Revision 2 catalog/binding extension for a
  relationless Summary item: `standalone`/`standalone-card`, one `item`, zero
  relations, zero visual parameters, and no emphasis. Edge-bearing mappings
  retain their already accepted resolved Core relation semantics unchanged.
- Phase 40 remains the sole owner of physical slide rendering, PDF generation,
  PDF verification, renderer manifest, receipt construction, and PDF review
  currentness.
- Phase 59 transformation returns the validated PageSet without filesystem
  mutation. Its separate writer takes that validated projection and an explicit
  output path. Coordinator X reads Phase 40 `source` and passes one resolved
  connection path to A as output and B as input. A does not inspect the consumer
  source or independently check its agreement. Special destination protection
  against input/artifact collision, aliases, and overlap is withdrawn, not
  relocated to X; ordinary write errors and semantic input validation remain.
  The user's [2026-09-14 module-connection handoff](../journal/2026/09/2026-09-14-phase-59-module-connection-decision.md) supersedes the earlier
  [output ownership decision](../journal/2026/09/2026-09-14-phase-59-projection-output-ownership-decision.md)
  without erasing its historical implementation/review evidence.
- Phase 58.2 Document and Summary confirmation HTML remains review evidence;
  it is neither a PDF input nor a second semantic authority.

## Projection requirements

- A projection begins only from explicitly admitted Core, Document, Summary,
  projection-profile, media descriptor/target, catalog, and pre-existing binding
  inputs. Missing, unknown, duplicate, stale, or unresolved inputs fail before
  PDF output or receipt visibility.
- The profile records the ordered Summary Unit-to-page mapping. One Summary
  Unit normally corresponds to one page, but any expansion or pagination is a
  declared projection decision, never a renderer-side convenience or implicit
  interpretation.
- Generated pages preserve Summary Unit order and every page records its exact
  originating Summary Unit and selected Core/Document/Summary/profile/media
  references. Each selected Summary edge occurs once; every mapping carries its
  exact endpoint items. A shared endpoint may recur with the same source ID and
  Core reference only when distinct selected edges require it, while an
  edge-unrelated item occurs once. Page-local catalog roles do not change Core
  meaning.
- Existing Summary-authorized Step Flow and Step-local Structure remain
  distinguishable through their selected catalog-supported nodes and relations.
  No relation direction, node role, Logical Pattern, or Visual Pattern may be
  reversed, flattened, or synthesized merely to make a page look complete;
  Phase 59 does not add new semantic tag text to the unchanged Visual Page or
  renderer contracts.
- Generated Visual Pages use the accepted Phase 36 catalog/binding contract.
  A selected Visual Pattern is explicit and validated; CSS and physical layout
  remain the renderer's responsibility.
- The Phase 40 route is reused as-is for `summary_slides_pdf`; its existing
  receipt and stale-input checks are extended only by adding this projection's
  verified inputs to the already established dependency chain.

## Required outputs

1. Paired [design](../design/document-project-summary-slide-pdf-projection.md)
   and [normative specification](../spec/document-project-summary-slide-pdf-projection.md)
   for the `cozy.summary-slide-projection.v1` Profile and
   v2-source-to-Visual-Page projection.
2. Strict typed profile/source loading, identity and reference validation,
   canonical VisualPageSet generation using the selected pre-existing binding,
   and deterministic rejection.
3. A Document Project operation/driver which reaches the existing
   `summary-slides-pdf` media build with generated, current Phase 36 inputs.
4. Article 9 Japanese source/profile/media-driver fixtures and one generated
   summary-slide PDF with retained provenance evidence.
5. Executable specifications for normal generation, exact mapping, semantic
   marks, stale propagation, source/profile/catalog/binding rejection, and
   repeated deterministic output.

## P590-04 execution slice

P590-04A is one Article 9 Japanese local-acceptance slice. It owns a static
test-resource Document Project rooted at
`src/test/resources/cozy/document/phase-59/application-modeling`: the already
admitted v2 Core, Japanese Document and Summary descriptions, a Japanese
Projection Profile, and a `media.yaml` with its accepted Visual Page catalog,
pre-existing binding, and local presentation dependencies. The Profile must
declare the complete ordered page sequence selected by the real Summary rather
than reconstructing source YAML inside a test.

Its executable acceptance owns one dedicated document-project specification.
It calls `CozySummarySlidePdf` with the existing Phase 40 test renderer seam,
then verifies the emitted PDF page count and ordered page/source mapping,
Summary-unit provenance, and distinct existing Visual Page semantics for
Step-Flow versus step-local Structure. It also proves deterministic repeated
output and that changes to Core, Document, Summary, Profile, catalog, or
binding reject or make the accepted PDF stale without a replacement receipt.
The focused validation additionally runs the owning Phase 58.1 and 58.2
confirmation specifications.

P590-04A does not change the Phase 40 renderer, its manifest/receipt schema,
the external site, or presentation layout. No production-source change is
planned; a failure that requires one widens the Slice and must return to PLAN.

## P590-05A V2 continuation

P590-05A is the user-approved continuation of the same Article 9 vertical
slice. It keeps the Revision 1 Visual Page catalog and binding valid, selects
the closed Revision 2 catalog/binding in the static Article 9 profile, and adds
the existing root Step `application-modeling` as one edge-free Summary item.
The profile appends `overview-application-modeling-standalone` after the three
existing edge mappings. Its resulting Article 9 PageSet/PDF therefore has four
ordered pages; the fourth contains one `item` node, `standalone-card`, zero
relations, and no fabricated Core Relation.

The static media descriptor records bytes-normalized receipt inputs for the
projection profile, Core, Document, Summary, media descriptor, catalog, and
binding. The existing Phase 40 receipt/currentness mechanism must make the
accepted PDF stale after a byte change to any one of those authorities. This
continuation does not change the renderer, verifier, receipt schema, layout,
Phase 40 implementation, Phase 58 sources, Phase 60/61, strategy, site, or
publication scope.

## Closure criteria

- The v2 source authorities, projection profile, generated presentation
  authorities, and Phase 40 renderer responsibilities are non-overlapping and
  specified consistently.
- Every generated page and PDF is traceable to its exact Summary Unit, its
  bound v2/media source identities, and the accepted Visual Page catalog and
  pre-existing binding.
- The Article 9 PDF preserves Summary order and distinguishes selected
  Step Flow from local Structure wherever the Summary selected them through
  the existing catalog-supported visual semantics; it does not claim new
  tag/structure text beyond the unchanged renderer contract.
- An input mutation to Core, Document, Summary, profile, media, catalog,
  binding, or consumed asset makes the derived presentation/PDF stale or rejects the
  operation deterministically; no fresh receipt is emitted for mixed input.
- Phase 40's existing PDF role, renderer manifest, PDF integrity check, and
  receipt/review currentness remain authoritative and passing.
- Existing Phase 40, Phase 58.1, and Phase 58.2 behavior remains executable
  and passing.
- Focused validation, independent full-Phase review, full Cozy validation, and
  a distinct local Phase release commit close the Phase.

## Exclusions

- Rewriting or replacing the accepted Phase 40 summary-slide PDF renderer,
  renderer manifest, PDF verifier, receipt format, or review-currentness
  contract.
- Adding PDF, PPTX, CSS, coordinates, fonts, physical layout, or automatic
  pagination fields to `summary.yaml`.
- Reopening completed Phase 40, Phase 58, Phase 58.1, or Phase 58.2 history.
- Migrating every Document Project, scaffold/profile redesign, or retirement
  of earlier semantic products.
- SmartDox or SimpleModeling.org production integration, publication,
  registration, deployment, upload, push, or external-service mutation.
- Automatic semantic inference or automatic acceptance of generated content.

## Execution readiness

- P590-01 is complete. Its checklist is authoritative for progress; no later
  implementation, test, PDF, review, or release Phase item is claimed complete.
- P590-01 must freeze the profile schema and exact source-to-page/provenance
  contract before P590-02 starts implementation.
- The first driver uses the already admitted local Article 9 v2 inputs. It is
  not authorization to mutate the external SimpleModeling.org production site.

## References

- [Phase 59 checklist](phase-59-checklist.md)
- [Phase 59 planning decision](../journal/2026/09/2026-09-13-phase-59-summary-slide-pdf-projection-decision.md)
- `docs/spec/document-project-document-and-summary-description-v2.md`
- `docs/design/document-project-summary-slide-pdf-projection.md`
- `docs/spec/document-project-summary-slide-pdf-projection.md`
- `docs/spec/visual-page.md`
- `docs/spec/media-package.md`
- `docs/phase/phase-40.md`
- `docs/phase/phase-58.1.md`
- `docs/phase/phase-58.2.md`
