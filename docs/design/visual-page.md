# Visual Page Design

Status: NORMATIVE DESIGN; Phase 36 `VIS36-01` through `VIS36-06` are DONE.

`P36-04-DEC-001` is consumed. Focused validation invocation
`90284-20260827T085957Z` (`testOnly cozy.video.CozyVideoStoryboardSpec`) reported
16 succeeded, 0 failed/aborted, SBT/wrapper 0, and the lock released.
Independent `P36-04-REREVIEW-002` is PASS and `CB-P36-04-RR-001` is resolved.
P36-04 through P36-06 are accepted as complete. P36-06's closed Cross-media
Review route strictly rejects duplicate JSON object fields before decoding
Cross-media proof or Storyboard v2 evidence/handoff. No push, publish, or
external-consumer acceptance is claimed; Phase 37 remains NOT STARTED.

This design fixes the ownership and architecture boundary for the Visual Page
contract in [`docs/spec/visual-page.md`](../spec/visual-page.md). It admits the
strict direct core commands, the renderer-independent semantic Preview, the
separately discriminated P36-03C presentation route, and the P36-04
Storyboard-v2 reference route defined below. It creates no consumer-acceptance,
migration-result, or downstream-runtime claim.

## 1. One-screen semantic authority

The design chooses one `VisualPage` as the authority for exactly one displayed
semantic screen. It separates three values that must never be collapsed:

```text
logical graph and provenance
  -> selected compatible Visual Pattern and typed parameters
  -> binding-owned physical renderer/template slots
```

The logical graph owns meaning. The selected Visual Pattern owns a renderer-
independent projection choice. The binding owns physical layout. A renderer
therefore cannot infer a Logical Pattern from arrows or slide geometry, and a
template cannot become a source of semantic meaning.

The selected closed catalog revision has canonical root order `schema, version,
id, revision, relations, logicalPatterns, visualPatterns`. Its exact five
relation entries are `{id, direction}` with `direction: from-to` and IDs
`next`, `causes`, `depends-on`, `enables`, and `maps-to`. Its Logical Pattern
entries are exactly `{id, nodeRoles, relationRules}`; node roles are exactly
`{role, min, max}`, and relation rules are exactly `{relation, fromRoles,
toRoles, min, max, topology}`. Role arrays preserve sorted canonical token
order, cardinalities are integers, and topology is only `linear`, `acyclic`,
or `bipartite`. Its Visual Pattern entries are exactly `{id,
compatibleLogicalPatterns, parameters}`, with parameter entries exactly
`{name, type, required}`. Types are only `node-ref`, `boolean`, and `string`;
there are no defaults, aliases, nullable values, or undeclared forms.

The exact initial catalog constraints use literal inclusive integer fields:
`sequence` has `step` min=2, max=8 and `next` step->step min=1, max=7 with
`linear` topology; `causal-chain` has `cause` min=1, max=7, `effect` min=1,
max=7, total min=2, max=8, and `causes` then `enables` cause->effect each
min=1, max=16 with `acyclic` topology; `dependency-map` has `dependency`
min=1, max=7, `dependent` min=1, max=7, total min=2, max=8, and `depends-on`
dependent->dependency min=1, max=16 with `acyclic` topology; and `mapping`
has `source` min=1, max=7, `target` min=1, max=7, total min=2, max=8, and
`maps-to` source->target min=1, max=16 with `bipartite` topology. No catalog
field serializes a dynamic expression.

`linear` requires the exact `step` node set to be one directed connected chain
with one start, one end, one incoming and one outgoing relation for each
internal node, no duplicate endpoint pairs, and exactly step-count minus one
edges. `acyclic` and `bipartite` prohibit duplicate same-type endpoint pairs
and enforce all endpoints and the stated role/total cardinalities, so observed
edge count cannot exceed eligible endpoint-pair count despite the static
maximum of 16. No initial form admits extra nodes or relations.

Canonical arrays preserve normalized semantics: root order remains stated above;
relations use their listed order; logical patterns are exactly `sequence`,
`causal-chain`, `dependency-map`, `mapping`; visual patterns are exactly
`flow-horizontal`, `flow-vertical`, `mapping-columns`; node roles sort
lexicographically by role; relation rules sort by relation, then lexicographic
`fromRoles`, then `toRoles`; role arrays and compatible logical-pattern arrays
sort lexicographically; and parameter arrays sort lexicographically by name.
Duplicates reject. `flow-horizontal` permits `causal-chain` and `sequence`;
`flow-vertical` permits `causal-chain`, `dependency-map`, and `sequence`; both
have optional `emphasisNode: node-ref` and `showRelationLabels: boolean`.
`mapping-columns` permits only `mapping` and has optional
`showRelationLabels: boolean` plus required nonempty trimmed
`sourceColumnTitle` and `targetColumnTitle` strings. A node-ref is an exact
current-page node ID. A new pattern, relation, alias, or parameter form is an
explicit catalog revision rather than a renderer extension or best-effort
normalization rule.

Revision 1 remains admitted unchanged with exactly those five relation entries,
four logical patterns, and three visual patterns. Revision 2 is the one closed
additive shape: it retains every Revision 1 array entry in its existing order,
then appends `standalone` as exactly one `item` node (`min=1`, `max=1`) with
zero relation rules, and appends `standalone-card` as compatible only with
`standalone` and with zero parameters. Revision 2 admits no hybrid shape,
relation alias, extra `item`, visual parameter, or inferred rendering meaning.
The relationless form is still an explicit logical/visual selection, never a
renderer-side reading of an empty graph.

## 2. Current typed inputs and provenance

DM14 removes the five VisualPage-owned digest APIs and calculations without
replacement proof APIs. `ValidatedDocument` is exactly
`(document,catalog,canonicalJson)`. Compatible projection changes and valid
content updates are processed by the current typed model. Canonical serializers
support normalization and lossless interchange, not historical-current proof.

Every asset requires `{id,path,mediaType}` and resolves to a safe direct regular
file beneath the actual input parent, without symlinked ancestors or leaf.
Current valid bytes require no digest rebinding. PageSet order remains semantic,
page IDs are unique, every page selects the same catalog ID/revision, and each
reference resolves exactly once. Actual graph, role, parameter, catalog, and
path validation remains strict.

## 3. Representation architecture

Markdown is review-oriented, YAML is human-authored structured input, and JSON
is machine interchange. None is a different semantic model. They converge through typed normalization:

```text
restricted Markdown / YAML / JSON
  -> strict representation parser
  -> typed VisualPage or VisualPageSet
  -> catalog resolution and validation
  -> canonical JSON
```

YAML aliases/merges, duplicate keys, unsafe references, and non-lossless
Markdown reject. Asset readers require the three current fields and ignore
ordinary extra asset fields without parsing, validating, retaining, or
re-emitting them, including malformed obsolete metadata. All other object
shapes remain closed. Restricted Markdown permits this normalization only in
`assets:` or `pages:` inline JSON: the original inline JSON must already be
compact canonical JSON, required-key order is preserved while extras are
removed, and the entire normalized envelope must equal canonical Markdown.
Heading, prose, field order, line-count, and trailing-LF constraints remain.

Direct core `convert` accepts the existing JSON/YAML/restricted-Markdown output
suffixes, parses and validates before writing the canonical UTF-8 serialization
directly to its normalized selected destination. The existing direct-directory
parent and non-symlink destination admission remains. Invalid semantic input,
catalog, or output suffix leaves output untouched because no write has begun.
Ordinary I/O failures retain `VISUAL_PAGE_SAVE`; a failed write may leave partial
output and recovery is regeneration.

## 4. Fast semantic preview

The direct Preview command is deliberately outside the implemented separate
Presentation route. Its exact public form is:

```text
cozy media visual-page preview <input> --catalog <catalog> --save <output.html> [--png <output.png>]
```

Preview first takes the same strict `VisualPage.load` path as the direct core
commands. It neither infers, repairs, selects, nor alters semantic content.
Its semantic input is limited to the validated Visual Page or ordered Visual
Page Set and resolved catalog. It accepts no binding, template, renderer,
PPTX, PowerPoint, receipt, review, video, or other generated-artifact input.

The required UTF-8 HTML file is a deterministic semantic review artifact. It
shows ordered page IDs; each page's Logical and Visual Pattern IDs; ordered
logical nodes; Relations with ID, type, `from-to` direction, endpoints, and
source references; typed visual parameters; document schema and ID; and catalog
ID and revision.
Every page-supplied value is HTML-escaped. The HTML has no physical layout or
renderer vocabulary in its input contract.

`--png` is optional. When requested, Preview generates a deterministic
logical-structure PNG solely from that same validated value. Its physical
diagram arrangement is generated output only. It is not an input to a PPT,
business binding, renderer, receipt, review, video, or another reusable
physical-layout contract.

`--save` occurs exactly once and targets exactly one `.html` output; `--png`
occurs zero or one time and targets exactly one `.png` output. Missing,
duplicate, unsupported, valueless, or ambiguous arguments reject, as do
normalized-identical HTML and PNG targets. Preview initially admits all arguments
and selected outputs, validates input and catalog, and renders all selected bytes
before direct writes in HTML then optional PNG order. Existing direct-directory
parents and absent-or-direct-regular non-symlink destinations remain required.
Invalid input, catalog, or arguments leave all pre-existing selected output bytes
unchanged because no write has begun. Ordinary I/O failures retain
`VISUAL_PAGE_PREVIEW_SAVE`; failed writing may leave partial or earlier completed
outputs and recovery is regeneration. The report follows only after every
selected write succeeds.

The deterministic text report has this ordered form, with `png` absent
when `--png` is absent:

```text
schema: cozy.visual-page.preview.v1
version: 1
generator: cozy media visual-page preview
documentId: <validated-document-id>
catalogId: <resolved-catalog-id>
catalogRevision: <resolved-catalog-revision>
pageCount: <validated-page-count>
html: generated
png: generated
status: generated
```

Phase 71.1 supersedes Preview hash-output provenance with these semantic
identifiers and generation results. The report and HTML compute and emit no
artifact digest or document/catalog digest metadata. Optional PNG presence is
selected by the command. Deterministic rendering, semantic ordering and strict
validity remain, including PNG CRC32 format integrity; a valid label change is
rendered directly without identity rebinding.

The direct Preview, DM12 material, and DM14 VisualPage proof removals leave
Explanation/Document, renderer, receipt, review-state, and output identities as
unfinished same-Phase migration obligations, not
permanent integrity exemptions. It claims neither full inventory removal nor
timestamp currentness/adoption, Step or Phase completion, or downstream
consumer acceptance.

## 5. Versioned business binding

`cozy.visual-page.binding.v1` is the separate business-binding value between
selected Visual Page semantics and a physical template. Its canonical JSON
root is exactly `schema`, `version`, `id`, `profile`, `catalog`, and `patterns`.
`schema` is exactly `cozy.visual-page.binding.v1`, `version` is integer `1`,
`id` and `profile` are nonempty canonical identity tokens, and this first
business binding requires `profile: business`. `catalog` is exactly
`{id,revision}` and must equal the resolved Visual Page catalog pair supplied
to the strict loader.

`patterns` is nonempty and contains exactly `{visualPattern,slots}` entries.
Each `visualPattern` is a catalog Visual Pattern ID; entries are unique and
canonical output sorts them lexicographically. The binding is complete: every
Visual Pattern in the selected resolved catalog occurs exactly once, and every
pattern selected by the supplied validated Visual Page document is therefore
bound. `slots` contains exactly five entries, each exactly
`{semanticSlot,physicalSlot}`. The semantic slots are exactly
`knowledge`, `nodes`, `relations`, `assets`, and `parameters`, in that order;
each `physicalSlot` is a nonempty identity token and is unique within its
Visual Pattern entry.

The loader reads only a direct regular UTF-8 `.json` file with neither a final
symlink nor a symlinked ancestor below its allowed root. It
rejects unsupported suffixes, malformed UTF-8 or JSON, duplicate or unknown
fields, unsafe/nonregular/symlink input, wrong schema/version/profile, invalid
tokens, catalog mismatches, unknown or incomplete patterns, and invalid slot
sets with structured `VISUAL_PAGE_BINDING_*` diagnostics carrying a field path
and reason. Equivalent JSON key or pattern-entry ordering normalizes to one
canonical JSON value. `ValidatedBinding` carries the typed `binding` and
`canonicalJson`; `Binding.canonicalValue` parses that canonical serialization
into the exact complete object `schema,version,id,profile,catalog,patterns`,
using existing BindingFault vocabulary on conversion failure. The typed value
preserves the actual physical mapping; consumers do not copy it into a
historical-current proof or require historical slot equality.

Physical slots are opaque template/renderer slot identifiers only. They carry
no coordinate, font, color, PowerPoint Shape kind, renderer object ID,
template path, or executable command. P36-03B validates and identifies the
business binding; P36-03C consumes it only in the separately discriminated
presentation route below. Neither binding nor output changes Visual Page
semantic authority. SmartDox and Textus consumer acceptance remains separate.

Accordingly, a Revision 2 business binding contains the preserved three
Revision 1 visual-pattern entries and one `standalone-card` entry. That entry
uses the same complete five semantic slots; it introduces no sixth slot,
physical-layout meaning, or binding-generation path.

## 6. Presentation and video coexistence

The legacy `presentation` object remains its accepted untagged closed object.
It never permits `contract`, `catalog`, or `binding`. P36-03C implements a
distinct discriminated `presentation` object whose exact keys are
`contract`, `profile`, `catalog`, `binding`, `slideImages`, `montage`,
`rendererManifest`, `reviewManifest`, `reviewState`, `articlePdf`, and
`infographic`; `contract` is exactly `visual-page-v1`, the resource's existing
required `source` is the VisualPageSet input, and `source`, `catalog`, and
`binding` are safe descriptor-relative direct regular files under the
descriptor root, with neither a final symlink nor a symlinked ancestor. They
reject empty, absolute, traversal, URI-like, control-character, and backslash
paths; normalization changes; symlink escapes; and missing or nonregular
resolved files. `source` must parse as exactly
`cozy.visual-page-set.v1` with integer version `1`. The profile remains the
accepted exact template/renderer shape. This object accepts no untagged legacy
shape and never infers `--slide-ir`.

Its fixed v2 renderer argv replaces `--slide-ir <source>` with
`--visual-page-set <source> --catalog <catalog> --binding <binding>`.
The v2 renderer requires relative field order
`schema,target,profile,renderer,pptx,slides,montage`. Renderer requires
`name,version`; PPTX and montage require `path`; page-ordered slides require
`id,path,assets` with ordered unique `{id}` assets. Readers positively select
known fields and ignore arbitrary extra metadata at every object level. Slides
retain actual PPTX/PNG paths and structure, safe direct selected asset paths and
existence of every linked OOXML ZIP image entry, without copied-image byte,
multiset or cardinality comparison. Summary-PDF v2 retains actual PDF page count
and PNG checks without copied-asset agreement.
S09 passes the declared primary output directly in `--pdf`, admits the selected
actual inputs and dependency files before rendering, and checks actual required
renderer fields, ordered IDs, PDF/PNG structure and configured PPTX-file floor.
Page asset paths and declared resource paths remain admitted as safe direct files
without copied-byte comparison. No before/after input observations are made.

Review wrappers for `visualPageSet`, `catalog`, and `binding` contain only
`{path}`. The private page evidence is `(id,assets)` and the aggregate evidence
is `(reviewPath,pages)`; neither contains semantic snapshots. Shared acceptance
admits all normalized selected targets, symlink/direct-file restrictions and
distinct non-aliased ownership once before writing prepared review states
directly in order, then the receipt. Input/format/preparation failures before
writes leave acceptance records untouched; failure to create the first state
parent precedes receipt writing. I/O failure during writes may leave earlier or
partial documents, including the receipt if its write starts, and reports no
success. No atomic installation, rollback or prior-record preservation is promised.
Direct Summary PDF generation can leave partial or changed primary/sidecar
outputs on renderer, structure or I/O failure, with no fresh acceptance or
successful report; recovery is regeneration. It promises no atomic install,
staged cleanup, rollback or prior-primary preservation. Immediate active-build
acceptance carries one initial input/producer capture through actual output
verification. S10 removes Presentation embedded-byte checks and shared acceptance
staging/backup/rollback. S11 removes migration/aggregate staging and article
scaffold installation/cleanup. Publication/prepared-input observations and the
remaining finite publication/runtime/CAR groups remain unfinished same-Phase work.

On 2026-10-08 S09's six existing focused suites completed with 114 successes,
zero failed/canceled/ignored/pending cases, zero aborted suites, zero
warnings/errors, SBT/wrapper exit 0, and terminal shared serial-lock release.
On 2026-10-08 S10's six existing focused suites completed with 113 successes,
zero failed/canceled/ignored/pending cases, zero aborted suites, zero SBT
warnings/errors, SBT/wrapper exit 0, and terminal shared serial-lock release.
Five hostile-XML/DOCTYPE diagnostics are intentional existing negative-fixture
output, not SBT compilation or test failures. S11's bounded direct-output and
article-scaffold implementation completed its five existing focused suites on
2026-10-08 with 58 successes, zero failed/canceled/ignored/pending cases, zero
aborted suites, zero SBT warnings/errors, SBT/wrapper exit 0, and terminal shared
serial-lock release. S11 emitted five intentional hostile-XML/DOCTYPE
negative-fixture diagnostics, separate from SBT compilation or test failures.
S12's current JA native route is planned/not-run; generation, assembly, native
inspection, browser EOF and current listening remain OPEN
([journal](../journal/2026/10/2026-10-08-phase-71.5-ja-main-route.md)).
Independent Step/Phase acceptance, the same full 34-scene native route, external
E715-01..04 reconciliation, Receipt publication/prepared-input observations and
remaining finite publication/runtime/CAR groups remain pending without a
whole-Phase closure claim.

Storyboard v2 keeps the literal `{kind,source,catalog,pageId}` screen reference.
Its review loads the actual current set, named catalog, selected page, and
Binding. Generated entries are `sceneId,kind,source,catalog,pageId,assets`, with
`{id,path,mediaType,modifiedAt}` asset observations and no VisualPage digest or
Binding copy. Legacy removed proof fields are ignored; old assets or Binding
snapshots are not compared with current inputs. The non-VisualPage Storyboard,
approval, self-identity, optional visual-input, and effective-renderer checks
remain distinct unfinished obligations, not substitutes for removed proof.

CrossReview resolves current per-side page references and safe actual selected
files, requiring equal ordered declared asset IDs/media types without cross-root
asset-byte equality. It neither compares semantic snapshots nor
demands a saved aggregate equal a freshly reconstructed whole value. It validates
saved operational structure and ID references. Build constructs once and directly
writes canonical aggregate JSON plus newline to the admitted target, but never
actual input or review evidence. Initial path and input/evidence collision checks
remain. Pre-write failures leave prior aggregate bytes unchanged; ordinary writing
failures report no success and may leave partial output without atomic installation,
cleanup or preservation. Verification is read-only. It records `not-recorded`
approvals and invokes no renderer.
Existing media receipt admission and outer evidence/artifact hashes remain
later work; this bounded change claims no Step/Phase closure or external
renderer/consumer acceptance.

## 7. Explicit migration only

The v1-to-v2 Storyboard migration can produce only a text-screen equivalent; a
visual-page screen cannot downgrade because v1 would lose its semantic
reference. Legacy Slide IR conversion is one direct command:

```text
cozy media presentation migrate <legacy-slide-ir> --semantic-map <semantic-map> --catalog <catalog> --save <visual-page-set>
```

Each required option occurs once, in separated or `--option=value` form. Its
semantic map is a direct regular non-symlink UTF-8 `.json` file and a closed
`cozy.visual-page.migration-map.v1` object with integer `version: 1`, exactly
the named fields `schema, version, visualPageSet, bindings`, and no duplicate
or unknown fields. JSON member order carries no semantic meaning; only the
deterministic report and saved VisualPageSet are canonical ordered outputs.
`visualPageSet` is a complete embedded `cozy.visual-page-set.v1`, not a
path or Page; the Visual Page contract resolves it through the separately
supplied catalog using the semantic-map directory as its source/asset root.

Every binding is exactly `{slideId, elementIndex, target}` and every target is
exactly `{pageId, kind, id}`. `elementIndex` is nonnegative and zero-based;
`kind` is only `node-label`, `asset-id`, or `source-path`. The complete
flattened legacy `(slideId, elementIndex)` source set and target
`(pageId, kind, id)` set are each bijective. Page IDs and selected node-label,
asset-ID, or source-path target values resolve exactly once. Text binds only
to an equal node-label/source-path value; assets bind only to an equal asset
ID. Any ambiguity, loss, inference, malformed/unsafe input, schema/version,
or incomplete/duplicate/unresolved/wrong-kind binding fails
without an output claim.

Only a validated map and admitted selected path permit an ordinary direct write
of canonical VisualPageSet JSON plus newline to the `.json` output, then emit a deterministic
`cozy.visual-page.migration-report.v1` text report containing exactly schema,
version, source/binding count, and `status: migrated`. This boundary invokes no renderer, receipt,
Storyboard, Media Package, review state, or external consumer. It has no
inference path and does not declare an old slide to be a Logical Pattern.
Invalid inputs before writing preserve existing bytes. Ordinary writing failures
retain `MIGRATION_SAVE` diagnostics without a successful report and may leave
partial output; there is no atomic replacement or cleanup promise.

Article scaffolding checks exact slug/business/language grammar, an absent
destination and its existing parent, creates that destination directly, and
writes the same authored payloads, review state and directories in their established
order. Existing destinations are never merged or overwritten. A later ordinary
creation/write error propagates without success and may leave a partial new package;
no whole-directory staging, atomic installation or failure cleanup is promised.

## 8. Deferred boundary

Subject Patterns, Explanation or Argument Patterns, explanation development,
AI pattern/parameter selection, and deterministic multi-page or multi-scene
expansion remain Phase 37 concerns. This foundation admits a `VisualPageSet`
only as an ordered set of already-complete one-screen values; it does not
choose how values are authored or selected.

## 2026-10-09 S33 presentation production and explicit review facts

**P715-02-S33-PRESENTATION-EXPLICIT-REVIEW-FACTS** is
**implemented-unvalidated-unreviewed** at this initial 2026-10-09 editing
handoff, within original **R715-03/04/14/15**. The four parent-selected existing
suites `cozy.media.CozyMediaPresentationSpec`,
`cozy.media.CozyMediaPdfReviewStateSpec`, `cozy.media.CozyMediaCrossReviewSpec`,
and `cozy.media.CozyMediaSpec` are **pending**. Validation, parent admission and
independent review are not claimed.

The [actual S32 focused result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s32-focused-validation-result.json)
records **48 successes across four completed suites**, separately dated
**2026-10-09** and attributed to S32. It does not validate or accept S33.
Other original finite obligations, including R715-03/04/14/15 remainders and
concrete external **E715-01..05**, remain **OPEN**. Final current **JA34 native
generation, assembly, audible hearing, end-card and EOF**, independent
**M2 cycles 6/7/8/10**, whole-Step/full-Phase reviews and acceptance, exact local
Step commits and the distinct release remain **OPEN**. Repository-full SBT and
broader integration remain **deferred-not-run to PHASE-71.7**. Existing base,
authority, epoch, repair counters, stage/aggregate metadata and prior history
retain their state; this authoring handoff makes no workflow transition.

This normative amendment supersedes the earlier presentation review-state
promises that use the review manifest's modification time, generation/decision
date ordering, or a review-manifest date no newer than `current` to establish
approval or currentness. It applies to both the legacy slide-IR and
`visual-page-v1` presentation routes.

An ordinary media build that selects the existing `Action.Current` presentation
reuses its generated output and retains the recorded semantic review state
when `current` exists, including the existing explicit decision. An actual
successful render, including the explicit slide-build route, records the new
acceptance observation time as UTC ISO-8601 `acceptedAt`, preserves
`last_aligned` and `selected_authority`, and sets `sync` to `stale`. A first
acceptance without a current record also requires explicit alignment. Failed
or rejected rendering never records acceptance. The execution branch supplies
this production fact; no file equality or timestamp comparison establishes it.

Explicit alignment first requires the ordinary current presentation validity
checks and an existing `current` record. It records the actual decision time,
selects `article` or `slide-ir`, and sets `sync` to `aligned`. An aligned state
requires `current`, `last_aligned`, and `selected_authority` plus `sync: aligned`.
Date fields record acceptance and decision facts; they do not prove current
approval, unchanged content, or race-free execution. No temporal ordering is
required to load those facts. The exact root grammar and required UTC string
type/syntax remain, with arbitrary snapshot extras ignored generically.

The ordinary presentation verifier still checks actual renderer/review
schemas, semantic IDs and mappings, selected paths, PPTX/OOXML structure,
relationships, legacy titles, and PNG format. Ordinary dependency existence
and modification times continue to select rebuild/reuse; they are not approval
or race evidence. Direct writes, acceptance order and ordinary input, output,
reuse and I/O failures retain their existing boundaries. The obsolete
review-state refresh seam and timestamp approval comparisons are removed.

The existing presentation scenarios specify actual reuse, explicit rendering,
and explicit re-alignment on both routes, plus retained XML/relationship/title/
PNG/page-mapping failures and arbitrary annotations. The existing PDF review
fixture emits the canonical summary-renderer schema, target/profile/renderer,
PDF page count, slide IDs/titles/paths and asset IDs, and montage path; retired
digest transport and protection-field absence expectations are removed.

See [the frozen S33 Implementation Manifest](../../.codex-workflow/phases/PHASE-71.5/p715-02-s33-implementation-manifest.json) and [the dated implementation handoff](../journal/2026/10/2026-10-09-phase-71.5-presentation-explicit-review-facts.md). At the initial handoff, selected validation and independent review remained pending.

### Actual S33 focused validation failure — 2026-10-09

**P715-02-S33-PRESENTATION-EXPLICIT-REVIEW-FACTS** is currently
**implemented-focused-validation-failed-unreviewed**. The initial
**implemented-unvalidated-unreviewed** handoff and its pending-suite record
remain dated history. The [actual failure result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s33-focused-validation-failure01.json)
and [raw execution log](/private/tmp/skill.cncf.d/a-ede4e1d9-7928-4d8e-9170-635b2dbaa193/73204-20261009T043126Z.log)
record the first group, `cozy.media.CozyMediaPresentationSpec` plus
`cozy.media.CozyMediaPdfReviewStateSpec`: **31 tests, 30 succeeded, 1 failed,
2 suites completed**, with **0 aborted, canceled, ignored or pending**.
The second group, `cozy.media.CozyMediaCrossReviewSpec` plus
`cozy.media.CozyMediaSpec`, was **NOT RUN** after the first group's failure.
There is no four-suite success or acceptance result.

Implicit compilation completed for **3 Main and 2 Test Scala sources**, with
**zero compiler warnings**. The **three existing duplicate `logback.xml`
runtime WARN lines** remain under **HYG-P715-S19-LOGBACK-001**, unsuppressed and
pending independent classification. Separately, the hostile-XML negative
fixture emitted **DOCTYPE disallowed Fatal Error on stderr**; that diagnostic
is retained unsuppressed, with independent classification still open. Zero
compiler warnings does not establish clean runtime diagnostics.

The failed existing scenario is **preserve explicit alignment on ordinary
reuse and require realignment after explicit rendering**. Reuse and the stale
review-state facts were reached successfully. After `_slide_build`, ordinary
media verification reports missing accepted receipt evidence for
`article-pdf` and `infographic`; the expected `stale` substring assertion fails
at **CozyMediaPresentationSpec.scala:576**. Repair design and validation remain
pending. This factual record changes no product behavior or executable
expectation. The [parent edit admission](../../.codex-workflow/phases/PHASE-71.5/p715-02-s33-parent-edit-admission.json)
is parent inspection, not independent review or acceptance.

The [quote-stop correction completion](../../target/phase715-s33-quote-skill-repair-completion.json)
records the minimal shared correction to **two canonical instruction files**
and **one deployed TOML**, **55 focused tests passed** and **independent PASS**,
with deployed contract **revision 20**. The existing stopped child retained
loaded **revision 19** and used **one same-child, one-use correction** for the
first original submission; no new request, attempt or permission and no retry
were created. Actual **SBT exit 1, wrapper exit 1 and serial lock released**
establish a terminal validation failure. Fresh registered revision-20
operational acceptance remains pending; shared correction evidence does not
accept the Cozy product.

Prior **S32 48 successes / 4 completed suites** remain separately attributed.
Original **R715-03/04/14/15** finite remainders, concrete external
**E715-01..05**, final current **JA34 native generation, assembly, audible
hearing, end-card and EOF**, independent **M2 cycles 6/7/8/10**, whole-Step and
full-Phase review, exact local Step commits and the distinct release remain
**OPEN**. Repository-full SBT and broader integration remain
**deferred-not-run to PHASE-71.7**. Original authority/base/goal binding/epoch,
repair history, stage and aggregate metadata and closure checkboxes retain
their state. This addition records facts without a workflow transition.

### Actual S33 repair11 focused validation result — 2026-10-09

**P715-02-S33-PRESENTATION-EXPLICIT-REVIEW-FACTS** is currently
**implemented-focused-validated-unreviewed**. The [current typed repair11 result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s33-repair11-focused-validation-result.json)
records the fresh repaired-subject run at **2026-10-09T04:51:35.719977+00:00**.
The initial unvalidated handoff, [original failed result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s33-focused-validation-failure01.json)
(**31 tests, 30 succeeded, 1 failed, 2 completed suites**) and earlier second
suite group **NOT RUN** remain dated history, rather than current failures.
The prior partial-receipt handoff's pending-rerun statement is also historical.

The first group, `cozy.media.CozyMediaPresentationSpec` and
`cozy.media.CozyMediaPdfReviewStateSpec`, reports **31 successes / 2 completed
suites**. The second group, `cozy.media.CozyMediaReceiptSpec`,
`cozy.media.CozyMediaCrossReviewSpec` and `cozy.media.CozyMediaSpec`, reports
**52 successes / 3 completed suites**. The total is **83 successes / 5 completed
suites**, with **0 failed, aborted, canceled, ignored or pending**. All five
selected suites and the unchanged explicit alignment/reuse/render lifecycle,
target-build stale-unselected retention and full-build obsolete-entry
replacement scenarios passed. No test, fixture or expectation was edited for
repair11. Implicit compilation completed for **1 Main Scala source**, with
**zero compiler warnings**.

The **three known duplicate `logback.xml` runtime warnings** remain retained
unsuppressed under **HYG-P715-S19-LOGBACK-001**. One exact hostile-XML rejection
was also retained on stderr:
`[Fatal Error] :1:10: DOCTYPE is disallowed when the feature "http://apache.org/xml/features/disallow-doctype-decl" set to true.`
The parent classified it from the actual source and log as deliberate hostile
XML fixture rejection: the existing unsafe-OOXML and legacy/VisualPage cases
use `hostiledtd`, and the real parser's `disallow-doctype-decl` maps rejection
to failure. The typed result records the source attribution at
`CozyMediaPresentationSpec.scala:426,638,1192` and
`CozyMediaPresentation.scala:769`. Independent Step classification of these
diagnostics remains **OPEN**. Zero compiler warnings is not a claim of zero
runtime diagnostics or independent acceptance.

The [repair11 parent edit admission](../../.codex-workflow/phases/PHASE-71.5/p715-02-s33-repair11-parent-edit-admission.json)
records the actual partial-selection receipt repair. Partial slide builds
retain unselected accepted receipts without reaccepting dependencies; target
merge and untargeted full replacement retain their meanings. Repair cycle
**11** was consumed on the actual source edit, and the current focused
validation converges: **cumulative 11 / nonconverging streak 0**. The six prior
repair-history entries and independent **M2 cycles 6/7/8/10** remain preserved;
independent **M2 cycle 11** also remains **OPEN**. Editing and passing tests do
not complete independent review.

Actual **SBT exit 0 / wrapper exit 0 / serial lock released** establish terminal
execution. Fresh registered **revision-20** runner operation is now observed
as `/root/phase715_s33_repair11_sbt_ba34cd9e`; the earlier quote-repair completion
note about pending fresh operation remains historical. This new repaired-subject
attempt needed no recovery, correction or repeated approval. The actual log is
`/private/tmp/skill.cncf.d/a-396741e5-182f-4ae5-a34a-7bf8efb9d6dd/82193-20261009T044958Z.log`.

The original Goal, base `fc640ec25e443d00c22b2c26b437a205d39910eb`, authority,
Goal binding, PLAN epoch, finite work list, aggregate metadata, Stage Status
triples and closure checkboxes retain their state, together with prior refusals
and reviews. Prior **S32 48 successes / 4 completed suites** and earlier native
evidence remain separately dated. Concrete external **E715-01..05**, other
original finite work, final current **JA34 native generation, assembly, audible
hearing, end-card and EOF**, whole-Step and full-Phase reviews, exact local Step
commits and the distinct release remain **OPEN**. Repository-full SBT and
broader integration remain **deferred-not-run to PHASE-71.7**. This factual
addition performs no workflow transition and claims no Step acceptance, Phase
closure or commit.
