# Document Project Interactive Confirmation Projection Design

Status: normative for Phase 58.2, Step P582-01, Slice P582-01A

This design freezes the semantic and interaction contract for the future
interactive Document and Summary confirmation projections. It is paired with
`docs/spec/document-project-interactive-confirmation-projection.md`; the
specification states the same decisions as implementation-facing MUST rules.
This slice fixes acceptance meaning only. It does not define the v2 source
schema or implement a model, codec, renderer, driver, or executable spec.

## 1. Reference evidence and acceptance stance

The approved Article 9 files are UX and behavior exemplars. They are evidence
for the information, relationships, and review interaction that a generated
projection must provide; they are not semantic authorities and are not byte
goldens. The identities recorded for this slice are:

| Reference | Relative path below the read-only reference root | SHA-256 |
| --- | --- | --- |
| Document confirmation | `document-confirmation.html` | `sha256:108f128dd715f6125ad060dbbf52f16e3005a653c096044afc1ea35725effbaa` |
| Summary confirmation | `summary-confirmation.html` | `sha256:bc38ec155502cbbca079095cbcc4ae72871bb74789b2a27e7c5f2412321b0006` |
| Reference README | `README.md` | `sha256:8a6a3579200131fa419575f38d8e34cc8580d3357f521d645847ce1c203e47e8` |

Acceptance is semantic and behavioral. A future generated page is accepted
when its required information, semantic DOM relationships, keyboard and
pointer behavior, accessibility state, self-contained behavior, and
representative desktop/mobile presentation agree with this contract. Exact
HTML bytes, CSS text, class names, element order that has no semantic effect,
and equality with the hand-authored reference bytes are not acceptance
conditions. Re-rendering the same admitted sources remains a future
determinism requirement, but reference-byte equality is never substituted for
semantic equivalence.

## 2. Five authority boundaries

The projection preserves five distinct authorities. None may silently absorb
another authority's meaning.

| Authority | Owns | Does not own |
| --- | --- | --- |
| Content Core | Locale-independent recursive Steps, nodes, typed Relations, and typed child-Step Flows | Localized prose, summary editorial selection, UI text, or physical presentation |
| Localized Document Description | Complete localized title, recursive sections, prose, project-specific labels, and exact Core references used by the Document page | Core semantics, generic vocabulary, Summary selection, or CSS/HTML layout |
| Localized Summary Description | Deliberate ordered units, concise wording, coordinate-free diagram selection, retained points, explicit Document omissions, and exact references | Core semantics, complete Document coverage, generic vocabulary, or physical slide layout |
| Cozy locale resources | Generic Logical Pattern, node-role, Relation-type, Flow-type, and review-chrome translations | Article-specific Step/node labels, prose, or editorial decisions |
| Renderer/projection | Semantic DOM, HTML/CSS, responsive layout, colors, typography, native interaction, accessibility, print behavior, and self-contained packaging | Semantic inference, missing labels, new Relations/Flows, or replacement authoring content |

The reference README and reference HTMLs are review evidence outside those
five authorities. Generated HTML is likewise review evidence, not a new Core,
Document, Summary, or locale authority.

## 3. Document confirmation contract

The Document page presents one review workspace with four distinguishable
semantic regions:

1. a recursively nested Core Step containment view, including each Step's
   localized label and stable identity;
2. a separate child-Step Flow view for the selected Step, containing only
   direct-child transitions and their exact typed Flow labels;
3. a selected-Step local Structure view containing the Logical Pattern, nodes,
   node roles, and exact typed Relations; and
4. the localized Document title, sections, blocks, list items, and prose
   traceability that realize the selected Core meaning.

Containment, child-Step Flow, and local Structure are not interchangeable
views. A nested parent/child list expresses containment only. A Flow entry
expresses a typed transition among direct child Steps only. A Structure entry
expresses the selected Step's local meaning only. A renderer must not credit a
containment parent edge as a Flow, or a local Relation as a child transition.

Selecting a Core Step highlights every Document Section, Block, and List Item
whose admitted typed references include that Step or its selected claims,
nodes, Relations, or Flows. The highlight is a review projection of exact
references; it is not a text search and must not infer a match from wording,
order, or visual proximity. Currentness and coverage status remain visible,
while full identities and diagnostics remain available as secondary evidence.

### Document DOM and interaction

The DOM must expose a page heading and accessible regions for the Core
containment, child-Step Flow, local Structure, and Document prose. The
containment hierarchy must be represented by semantic nesting or an equivalent
programmatic parent/child relationship. The selected Step and its displayed
detail/prose region must have a programmatic association; visual placement
alone is insufficient.

Every selectable Step must be a native keyboard-operable control (for example,
a native button or a native tab/tree control). Selection must work through
normal keyboard focus and Enter/Space activation, expose a consistent
programmatic selected state (`aria-selected` for a tab/tree selection model or
`aria-pressed` for a toggle-button model), and provide a visible focus and
selected indication. Pointer selection may supplement keyboard selection but
may not be the only route. A selected control must be named by its localized
label and stable identity without requiring a pointer or color perception.

## 4. Summary confirmation contract

The Summary page presents:

- an ordered Summary Unit navigation flow;
- one selected unit's slide-level semantic explanation at a time, with a
  responsive 16:9-oriented review area but no layout coordinates in the
  Summary authority;
- only explicitly authored, coordinate-free diagram items and edges;
- exact Core sources for the selected unit;
- localized retained points; and
- each explicitly authored Document element omitted or condensed by the unit,
  with its localized rationale.

Selecting a Summary Unit updates the selected explanation, diagram, sources,
retained points, omissions, and selected-state indication as one semantic
transaction. The page must not synthesize a unit, message, diagram item,
relation, retained point, or omission from Document ordering or visual
convenience.

Summary selection is deliberately selective. The Summary status must report
unresolved or invalid references when they exist and must not claim universal
Core or Document coverage merely because all authored Summary references that
were valid have been displayed. `unreferenced: 0`, or equivalent wording that
implies complete coverage, is not an acceptable substitute for unresolved- or
invalid-reference status. A valid selective Summary with no unresolved or
invalid references may state that its authored references are resolved, but it
must still not imply universal coverage.

Summary Unit controls obey the same native keyboard, focus, selected-state,
accessible-name, and pointer-supplement rules as Document Step controls. The
selected control is programmatically associated with the selected explanation
and inspector regions.

## 5. Grounding every visible edge

Every visible arrow, chevron used as a semantic connector, or labeled
directional edge in either page must resolve to exactly one admitted typed
Core Relation or typed child-Step Flow transition. The rendered label and
endpoints must remain consistent with that typed source. A decorative divider
or non-semantic layout cue must not use an arrow glyph that could be read as a
semantic edge.

An inverse reading is allowed only when the admitted semantic contract
explicitly declares that inverse for that exact typed Relation or Flow. The
inverse is a display reading; it does not create, mutate, or relabel the Core
source. An ungrounded prototype-only explanatory arrow is removed or grounded
before acceptance. Renderer inference from adjacency, wording, or a useful
visual chain is forbidden.

## 6. Responsive, safe, and self-contained projection

At representative desktop widths the pages may place the semantic regions
side by side, as in the reference workspaces. At representative mobile
widths they must reflow to a readable stacked or otherwise responsive layout.
The reflow must preserve the same information, semantic associations, order
where order is meaningful, keyboard path, focus visibility, selected state,
and edge grounding. It must not depend on pointer-only hover, fixed canvas
coordinates, or an inaccessible horizontal-only presentation.

Each generated page is one self-contained HTML artifact. It must include its
required styles and interaction without network requests, external Web
resources, or runtime services; user-authored values must be safely escaped.
Given identical admitted semantic inputs, future renderers must produce the
same semantic content and behavior deterministically.

## 7. Secondary identity and diagnostics

The three reference SHA-256 values above establish provenance for this
contract; they do not become a requirement that generated bytes equal the
reference bytes. Generated source identities, currentness identities,
resolution details, and diagnostics may be exposed for review, but they are
secondary to the title, prose, tree/flow/structure distinction, selected
explanation, source traceability, retained points, omissions, and unresolved-
or-invalid status. Diagnostics must not displace or obscure the review content
in the normal view.

## 8. Scope and non-goals

This slice changes only this paired design and specification. The following
remain future work or outside the manifest:

- strict v2 Scala models, codecs, loaders, renderers, and executable specs;
- Article 9 Document/Summary driver sources or Article-specific production
  localization changes;
- Cozy locale-resource changes;
- any mutation of the external SimpleModeling.org repository or its driver;
- PDF, PPTX, infographic, video, SmartDox/site, publication, registration,
  deployment, upload, push, or other external-service work; and
- any Phase 48 source, specification, resource, status, or checklist change.

The closed v1 design/specification remains the Phase 58.1 authority and is not
weakened or rewritten by this contract. Future v2 work must preserve v1
behavior while admitting the additional semantics described here.
