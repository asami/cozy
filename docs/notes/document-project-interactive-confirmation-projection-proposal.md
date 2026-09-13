# Document Project Interactive Confirmation Projection Proposal

Date: 2026-09-12

Status: exploratory proposal; non-normative; Phase 58.2 planning input

## 1. Problem

Phase 58.1 established strict media-independent Document Description and
Summary Description authorities and generated deterministic review HTMLs.
Operational review with the real SimpleModeling.org Article 9 content showed
that the generated pages still present either composed prose or a vertical list
of summary units. They do not yet provide the review workspace needed to
inspect the relationship among recursive Core meaning, prose realization, and
deliberate summary condensation.

Two manually authored Article 9 reference pages now demonstrate the desired
review experience:

- `src/main/doxsite/development-process/application-modeling.dox/review/reference/document-confirmation.html`
- `src/main/doxsite/development-process/application-modeling.dox/review/reference/summary-confirmation.html`

The reference pages are maintained by the SimpleModeling.org driver. They are
UX and behavior exemplars, not new semantic authorities and not exact byte
goldens for Cozy output.

## 2. Desired document confirmation

The Document confirmation should display three distinct semantic levels:

1. recursive Step containment as the Core logic tree;
2. typed Flow among direct child Steps; and
3. the selected Step's local Structure, nodes, and typed Relations.

The document prose remains the primary localized reader content. Selecting a
Core Step should highlight every Section, Block, and List Item whose typed
references realize that Step or its selected claims, nodes, Relations, and
Flow. The review should answer whether Core meaning has been expressed
naturally and completely, rather than merely displaying all metadata.

## 3. Desired summary confirmation

The Summary confirmation should display:

- the ordered Summary Unit flow;
- one selected summary-slide-level explanation at a time;
- a semantic diagram for that unit without authored coordinates;
- the Core elements deliberately retained by the unit; and
- the Document material deliberately omitted or condensed.

The renderer must not invent a missing relation merely because three concepts
look visually useful in a chain. Every visible semantic edge must resolve to a
typed Core Relation or Flow transition, possibly with an explicitly declared
inverse reading. If the reference prototype contains an explanatory edge that
is absent from Core, the driver must either add the warranted Core relation or
remove that edge before acceptance.

## 4. Proposed source evolution

Preserve the closed v1 loaders and introduce strict v2 schemas. Do not add
optional unknown-field compatibility to v1.

### 4.1 Document Description v2

`cozy.document-description.v2` should retain the v1 document content and add
a locale-owned typed semantic-label catalog for project-specific Core Steps
and nodes. Generic controlled-vocabulary labels for Logical Patterns, node
roles, Relation types, and review chrome remain Cozy locale resources.

A label entry should contain an exact typed Core reference and localized text.
Dynamic YAML keys, labels inferred from IDs, and Article-specific Scala maps
should not be admitted. Every Step or node used in primary review presentation
must have one resolving label.

### 4.2 Summary Description v2

`cozy.summary-description.v2` should retain ordered units, heading, message,
emphasis, and exact Core/Document identity binding while adding:

- a short navigation label;
- ordered review points with exact Core references;
- an optional coordinate-free semantic diagram whose items and edges are
  exact typed Core references;
- explicit Document references for intentionally omitted or condensed
  material, with localized rationale.

The diagram is concise explanation semantics, not slide-layout IR. It may
select order and focus, but it must not contain pixels, coordinates, fonts,
CSS, HTML elements, or physical pagination.

## 5. Projection boundary

The renderer owns responsive layout, colors, typography, HTML, CSS, inline
interaction, accessibility, print behavior, and physical presentation. It
must generate one self-contained deterministic HTML file without external
assets.

Article-specific labels and prose come from the admitted DSLs. Generic UI text
and controlled-vocabulary translations come from validated Cozy locale
resources. The current Article 9-specific localization object in
`CozyDocumentDescriptionProjection` must not survive the Phase 58.2 boundary.

## 6. Reference tuning before acceptance

The first implementation slice should reconcile the reference prototype with
the admitted semantics before treating it as acceptance evidence:

- distinguish containment connectors from typed child-Step Flow;
- show local Structure separately from both containment and Flow;
- replace the ambiguous summary status `unreferenced: 0` with unresolved- or
  invalid-reference status, because Summary coverage is selective;
- remove or formally ground every prototype-only explanatory arrow; and
- keep hashes and complete diagnostic metadata available but secondary.

## 7. Verification direction

Acceptance should compare semantic DOM structure, visible information,
interaction behavior, and responsive screenshots rather than requiring Cozy
output to equal the hand-authored bytes. Executable specifications should
prove strict v2 loading, typed reference resolution, no semantic inference,
v1 preservation, deterministic output, safe escaping, accessible selection,
and the absence of Article 9 wording from Cozy production code.

## 8. Scope boundary

The Phase 58.2 vertical slice should update only the two confirmation
projections and the Article 9 v2 driver. It should not migrate every Document
Project, change scaffold/profile behavior, generate SmartDox, PDF, PPTX,
infographic, or video artifacts, publish a site, or accept AI-authored content
without human review.
