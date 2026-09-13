# Document Project Interactive Confirmation Projection Specification

Status: normative for Phase 58.2, Step P582-01, Slice P582-01A

This specification is paired with
`docs/design/document-project-interactive-confirmation-projection.md`. It
freezes the semantic and interaction acceptance contract for future
interactive Document and Summary confirmation projections. It does not admit
the v2 source schema and does not authorize implementation in this slice.

## 1. Reference evidence and acceptance oracle

The approved Article 9 files are behavior and UX evidence, not semantic
authorities. Their exact identities are fixed as follows:

| Reference | Relative path below the read-only reference root | SHA-256 |
| --- | --- | --- |
| Document confirmation | `document-confirmation.html` | `sha256:108f128dd715f6125ad060dbbf52f16e3005a653c096044afc1ea35725effbaa` |
| Summary confirmation | `summary-confirmation.html` | `sha256:bc38ec155502cbbca079095cbcc4ae72871bb74789b2a27e7c5f2412321b0006` |
| Reference README | `README.md` | `sha256:8a6a3579200131fa419575f38d8e34cc8580d3357f521d645847ce1c203e47e8` |

### 1.1 Semantic equivalence, not byte equality

A future projection MUST be accepted by semantic DOM, required information,
interaction behavior, accessibility state, self-contained behavior, and
representative desktop/mobile presentation. It MUST NOT be accepted or
rejected solely because its HTML bytes, CSS text, class names, incidental DOM
ordering, or whitespace do or do not equal a reference file. The reference
SHA-256 values are provenance identities only. Repeated rendering of identical
admitted sources remains a separate future determinism obligation.

### 1.2 Resumed acceptance scope

The direct user instruction for the resumed normal-acceptance boundary is:

> 正式なレビュー、フルテスト、リリースしてクローズして。

The related presentation instruction is:

> レスポンシブは考慮しなくていよい。サンプルを完全に再現して。

It prioritizes complete sample/Desktop layout and functionality for this
acceptance boundary. No responsive redesign is required or authorized here.
Any narrow fallback behavior is characterization only; a mobile or
full-responsive parity claim requires direct observation. The responsive rules
in Section 6 remain the future projection contract and are not authorization
for a redesign in this resumed documentation boundary.

## 2. Authority and input boundary

The projection MUST preserve these five distinct authorities:

1. **Content Core** owns locale-independent recursive Steps, nodes, typed
   Relations, and typed child-Step Flows.
2. **Localized Document Description** owns the complete localized title,
   recursive sections, prose, project-specific labels, and exact Core
   references used by the Document page.
3. **Localized Summary Description** owns deliberate ordered units, concise
   wording, coordinate-free diagram selection, retained points, explicit
   Document omissions, and exact references used by the Summary page.
4. **Cozy locale resources** own generic Logical Pattern, node-role,
   Relation-type, Flow-type, and review-chrome translations.
5. **Renderer/projection** owns semantic DOM, HTML/CSS, responsive layout,
   colors, typography, native interaction, accessibility, print behavior, and
   self-contained packaging.

The renderer MUST NOT infer semantic content, add missing labels, create a
Relation or Flow, or replace any of the first four authorities. The reference
README and reference HTMLs are evidence outside these five authorities.
Generated HTML is review evidence and MUST NOT become a semantic authority.

## 3. Document confirmation requirements

### 3.1 Required information

The Document page MUST expose all of the following as distinguishable review
content:

- the recursively nested Core Step containment hierarchy, with localized
  labels and stable identities;
- the selected Step's direct-child typed Flow transitions in a separate Flow
  region;
- the selected Step's local Structure, including Logical Pattern, nodes,
  node roles, and exact typed Relations; and
- the localized Document title, sections, blocks, list items, prose, and exact
  traceability to the selected Core meaning.

Containment, child-Step Flow, and Step-local Structure have separate meaning:

- a nested parent/child relationship MUST represent containment only;
- a Flow entry MUST represent only an admitted typed transition among direct
  child Steps; and
- a Structure entry MUST represent only the selected Step's local meaning.

The projection MUST NOT treat one of these three contracts as another, merge
them into an undifferentiated sequence, or infer one from the others.

When a Step is selected, the page MUST highlight or otherwise identify every
Document Section, Block, and List Item whose admitted typed references include
that Step or its selected claims, nodes, Relations, or Flows. This mapping
MUST use exact references and MUST NOT be inferred from prose wording, order,
or visual proximity. Currentness and coverage status MUST be available while
full identities and diagnostics remain secondary.

### 3.2 Semantic DOM

The DOM MUST expose a page heading and accessible regions for Core
containment, child-Step Flow, local Structure, and Document prose. The
containment hierarchy MUST be represented by semantic nesting or an equivalent
programmatic parent/child association. The selected Step control and its
displayed detail/prose region MUST be programmatically associated; visual
placement alone is insufficient.

### 3.3 Selection and accessibility

Every selectable Step MUST use a native keyboard-operable control, such as a
native button or a native tab/tree control. The control MUST be selectable by
normal keyboard focus and Enter/Space activation; pointer activation MAY
supplement it but MUST NOT be the only route. Focus MUST be visibly indicated.

The control MUST expose one consistent programmatic selected-state model:
`aria-selected` for a tab/tree selection model or `aria-pressed` for a
toggle-button model. The selected control MUST retain its localized accessible
name and stable identity without requiring color perception, and MUST be
programmatically associated with the selected detail/prose region.

## 4. Summary confirmation requirements

### 4.1 Required information

The Summary page MUST expose all of the following:

- ordered Summary Unit navigation;
- one selected unit's slide-level semantic explanation at a time, with a
  responsive 16:9-oriented review area but no physical coordinates in the
  Summary authority;
- only explicitly authored coordinate-free diagram items and edges;
- exact Core sources for the selected unit;
- localized retained points; and
- explicitly authored Document elements omitted or condensed by the unit,
  with localized rationale.

Selecting a Summary Unit MUST update the selected explanation, diagram,
sources, retained points, omissions, and selected-state indication as one
semantic transaction. The renderer MUST NOT synthesize a unit, message,
diagram item, Relation, retained point, or omission from Document ordering or
visual convenience.

Summary references are intentionally selective. The Summary status MUST report
unresolved or invalid references when present. It MUST NOT use
`unreferenced: 0`, or equivalent wording, to imply universal Core or Document
coverage merely because all valid authored references are displayed. A
selective Summary with no unresolved or invalid references MAY state that its
authored references resolve, but MUST NOT imply complete coverage.

### 4.2 Summary DOM and interaction

The DOM MUST expose an accessible region for ordered unit navigation, a
selected explanation region, and an inspector for sources, retained points,
and omissions. The selected Unit control MUST be programmatically associated
with the selected explanation and inspector regions.

Every Unit control MUST be a native keyboard-operable control and MUST support
normal keyboard focus and Enter/Space activation. Pointer activation MAY
supplement keyboard activation but MUST NOT be required. Focus MUST be
visible, the selected state MUST use one consistent `aria-selected` (tab/tree)
or `aria-pressed` (toggle-button) model, and the control MUST expose an
accessible localized name and stable identity.

## 5. Typed grounding of visible edges

Every visible arrow, semantic chevron connector, or labeled directional edge
in either page MUST resolve to exactly one admitted typed Core Relation or
typed child-Step Flow transition. Its endpoints and label MUST remain
consistent with that typed source. A decorative divider or layout cue MUST
NOT use an arrow glyph that can be interpreted as a semantic edge.

An inverse display MUST be used only when the admitted semantic contract
explicitly declares that inverse for the exact typed Relation or Flow. An
inverse is a display reading only; it MUST NOT create, mutate, or relabel the
Core source. Any prototype-only explanatory edge without an exact typed
grounding MUST be removed or explicitly grounded before acceptance. The
renderer MUST NOT infer edges from adjacency, wording, or visual usefulness.

## 6. Responsive, safe, and self-contained output

At representative desktop widths the Document and Summary pages MAY arrange
their semantic regions side by side. At representative mobile widths they
MUST reflow to a readable stacked or equivalent responsive arrangement. The
reflow MUST preserve information, semantic associations, meaningful order,
keyboard path, focus visibility, selected state, and typed edge grounding. A
page MUST NOT depend on pointer-only hover, fixed canvas coordinates, or an
inaccessible horizontal-only presentation.

Each projection MUST be one self-contained HTML artifact containing its
required styles and interaction. It MUST require no network request, external
Web resource, or runtime service, and MUST safely escape user-authored values.
For identical admitted semantic inputs, future rendering MUST be
deterministic.

## 7. Secondary identities and diagnostics

The reference identities in Section 1 MUST remain provenance metadata and MUST
NOT become byte-golden acceptance criteria. Generated source identities,
currentness identities, resolution details, and diagnostics MAY be exposed,
but MUST remain secondary to review content: title, prose, the three Document
relationship levels, selected Summary explanation, source traceability,
retained points, omissions, and unresolved/invalid status. Normal review MUST
not be forced to read hashes or diagnostic metadata before the semantic
content.

## 8. Scope and non-goals

This specification authorizes only the paired design/specification contract.
It does not authorize edits to any source, Scala model, codec, loader,
renderer, executable specification, test resource, or Article 9 driver. It
also excludes:

- Cozy locale-resource changes;
- mutation of the external SimpleModeling.org repository or driver;
- PDF, PPTX, infographic, video, SmartDox/site, publication, registration,
  deployment, upload, push, or other external-service work; and
- any Phase 48 source, specification, resource, status, or checklist change.

The closed Phase 58.1 v1 design/specification remains unchanged and
authoritative for v1. Future strict v2 work MUST preserve v1 behavior and MUST
not weaken v1 through permissive fields or silent adaptation.
