# Document Project Confirmation Views and Make-Level Dependencies

Date: 2026-09-14
Status: exploratory; non-normative
Tracking: [Phase 60](../phase/phase-60.md), DEV-028

## Purpose and source responsibilities

Provide three local confirmation HTML targets through Cozy Document Project.
Core owns logical meaning. The localized Document Description DSL owns complete
document organization and prose. `index.dox` is the SmartDox-targeted article
representation of that description; Codex currently authors that representation.
Generating confirmation HTML does not compose or revise article prose.

| Confirmation target | Direct content sources | Review focus |
| --- | --- | --- |
| Structure-centered document | Core and localized Document Description | Nested Steps, inter-Step Flow and Step-local Structure, with corresponding prose |
| Document-centered document | The same Core and Document Description | Reader-ordered headings and prose, with corresponding logic and diagrams as secondary information |
| Rendered article | `index.dox` and explicitly consumed rendering files | Actual SmartDox article expression and rendering |

The two document views are projections of the same DSL, not separate authoring
sources. Summary confirmation remains a separate existing target.

## Existing capability and required changes

The v2 Document confirmation renderer already has authored prose, typed Core
references, nested Step selection and local Structure/Flow panels. Its current
CLI selects `document` or `summary` and exposes no document-view selector.
Reuse its model, validation, vocabulary and correspondence for both document
views. Preserve the current structure-centered default and Summary behavior.

Add a document-centered projection: an article-like reading column with the
complete heading hierarchy and prose, and collapsible or side-panel Core and
diagram information. Selecting a passage reveals its explicit Core references;
selecting a Step navigates to its associated prose. Multiple targets remain
visible rather than being reduced to an arbitrary first match. Keep nested
containment, direct-child Flow and local Structure distinct. Audit identifiers
stay secondary. Do not infer edges or paraphrase prose in the renderer.

The existing `review --kind article` reads `index.dox` but flattens narrative
into review tables and requires Visual Page/infographic sources. Keep that
legacy behavior explicitly named and compatible; it is not actual article
rendering and does not substitute for either new reading surface.

Add a Document Project entry point for actual article HTML. Delegate parsing
and rendering to normal SmartDox instead of adding a Cozy article parser.
The standalone `dox html index.dox` route currently produces `html.d/index.html`;
normalize its generated location into the declared project-local target and
retain associated renderer assets when present. Use isolated temporary output
and expose the new result only after successful rendering and output checks.
Confirm the admitted CLI/version and exact output shape in executable specs.

The actual article target displays article content. It does not need Core
annotations, an article-to-Core mapping, Visual Pages or an infographic merely
to render. Document-centered DSL confirmation carries the explanatory context.
SmartDox annotation extensions are not a prerequisite for this scope.

## Dependency management: ordinary make semantics

Cozy manages named targets, generation actions, output destinations and direct
file dependencies. Dependencies may include renderer configuration, templates,
locale vocabulary and referenced assets when the action actually consumes them.
Transitive generated prerequisites are built before their dependent target.
Freeze the exact CLI spelling, target names and descriptor representation in
P600-01; illustrative view names here are not implemented commands.

For each target:

- If an expected output is absent, generate it.
- If any dependency has a newer modification time than the output, regenerate.
- Otherwise reuse the output without invoking the renderer or touching its time.
- A missing required input is an error. A generated prerequisite is built first.
- An explicit force/rebuild option may regenerate an otherwise current target.

Use the strict newer-than comparison of ordinary make, including equal-time
behavior. Specify filesystem time resolution, future-dated inputs and multiple
outputs without inventing content-change detection. When multiple outputs are
declared, an absent output or an output older than a dependency requires a build.
Changing the target definition/configuration must be represented by an actual
file dependency. Same-time replacement and restored historical timestamps have
the same limitations as make; force rebuilding is the explicit remedy.

Ordinary filesystem timestamps provide freshness. No content hashes, digest
manifests, hash-based identities, acceptance receipts or an independent evidence
database are required to generate or reuse these local confirmation targets.
Cozy can report the target, inputs, output path, generated/reused decision and
process result. The output timestamp already supplies its generation time.
This is build freshness, not human approval or semantic equivalence validation.

Keep existing source-schema validation and existing export/acceptance systems
outside this local dependency-policy change. Do not silently migrate their
contracts or require their hash receipts to use a new confirmation target.

## Failure, safety and operation

Build only below admitted project-local output roots. Validate paths and prevent
source/output collisions. Detect dependency cycles before rendering. Failure
returns a failing status, preserves the previous successful output and does not
advance its modification time. No persistent preview server is started by an
HTML build; opening/serving HTML is a separate operation with its own lifetime.

Article rendering depends on the existing `index.dox`. If Document Description
changes while `index.dox` stays unchanged, report/review the representation
update separately; a renderer cannot manufacture the missing article conversion.
An explicit article-authoring prerequisite needs an implemented contract; 60A
does not provide it. The 60B extension below plans Codex-side authoring and
orchestration separately from the local renderer.

## Acceptance examples and exclusions

An isolated Article-9-shaped fixture demonstrates both DSL views with identical
accepted prose, nested Step/Flow/Structure navigation, and actual `index.dox`
rendering preserving headings, lists and supported links/figures. Fresh targets
reuse their files unchanged. Touching an admitted dependency rebuilds only its
target and dependents; deleting an output rebuilds it; a rendering failure keeps
the prior output. Missing inputs, cycles and changed configuration have specs.

The 60A local-build scope excludes article composition, new SmartDox annotation grammar,
article/Core annotation integration, PDF/slides/video production, whole-site
builds, Antora publication, public media registration, uploads, deployment and
changes to the external SimpleModeling.org driver or installed skills by the
60A local HTML builder. The later 60B authoring workflow is scoped below.

## Goal-driven DSL dependency resolution: Phase 60B extension

The user subsequently requested that Phase 60 include the Codex capability set
needed to generate and update products from Document Description. This extends
the authoring workflow boundary while retaining the 60A renderer boundary.
Document is the complete localized prose authority, not merely background for
an independently written article. Core continues to own logical meaning.

The subsequent clarification makes this a goal-driven workflow. The user asks
for a product, not for each intermediate DSL. Codex traces the product's
declared grounding DSLs recursively back to Core, then proceeds from Core
toward the product in dependency order. Document remains the prose authority
at its node; starting with a product does not grant it an independent prose
authority or require rewriting an already valid Core.

### Reverse planning and forward execution

1. Resolve the requested product(s), project, locale and applicable profile.
2. For each product, resolve its declared direct prerequisites. Recursively
   follow grounding DSL prerequisites until Core is reached, retaining all
   branches and shared nodes. Record asset/configuration/tool inputs separately
   as external prerequisites; not every rendering file is a Core-derived DSL.
3. Validate existing nodes and select reuse, create, update or render actions.
   Identify missing sources, stale/invalid bindings and unsupported producers
   distinctly. Detect cycles before dependent generation. Feedback reconciliation
   is an editing operation, not a reverse edge in the generation graph.
4. Execute from the Core side in topological dependency order. Reuse valid
   inputs. Author or update missing/affected DSLs after their prerequisites are
   ready, validate them and refresh existing bindings as required, then render
   the requested product. Generate a shared prerequisite only once.
5. Report requested outputs and unresolved dependencies. On failure preserve
   prior successful outputs and resumable source state; resume the affected
   dependency closure rather than regenerating all products.

Each node needs an explicit producer, direct inputs, locale/profile selection,
validation/reuse rule and admitted tool/skill route. Existing maintained skills
become node producers and adapters under one shared planner; the workflow is
not a separately hard-coded complete pipeline for every product. The graph is
admitted at P600-06 and used by P600-07 companion skills. It introduces neither
a new generic workflow engine nor a new hash-based evidence framework.

Core can be reused when present and valid. If absent or lacking needed meaning,
Core authoring requires the user's authorized content/intent and review boundary;
the resolver exposes that need instead of inventing unsupported claims. A
request for a product does not itself authorize unrelated semantic changes.

### Skill-led orchestration with intermediate authored documents

Phase 60B delivers an executable Codex skill workflow. The entry-point skill
owns the user's product request, reverse dependency discovery, reuse/update
decisions and forward scheduling. It dispatches node-specific content work
to Codex/authoring skills and deterministic operations to Cozy. Existing
maintained skills are reused or adapted; a maintained orchestration entry point
is created where the existing set does not provide this contract. Its name and
interface are admitted in P600-06 rather than invented as a working command.

Cozy supplies the operation set called by the skill, rather than the overall
AI workflow controller. Each admitted validation, confirmation, conversion,
rendering or local-build operation has independently callable CLI/API behavior,
explicit consumed inputs and parameters, declared output files/assets and
success/failure semantics. Inventory existing implemented routes first and
track missing functions explicitly; this does not add unsupported conversion
commands or new renderers by implication. The skill chooses and schedules
operations around Codex authoring. Cozy can still build a selected target's
declared local prerequisites under 60A make semantics; that is separate from
the skill's overall semantic DSL authoring dependency graph.

Codex creates/updates content-bearing intermediate documents such as Core,
Document, Summary, `index.dox` and applicable storyboard/visual DSLs. These are
persisted source products with declared input/output paths, not ephemeral text
hidden inside a renderer or a second authority outside the DSLs. Cozy performs
the admitted source validation, confirmation projection, rendering and local
dependency management; SmartDox parsing/rendering remains delegated through
the admitted routes. A workflow may alternate authoring, validation, further
authoring and rendering several times as its dependency graph requires.

For an article-PDF request, the skill resolves PDF to the SmartDox article,
then Document and Core. It reuses valid inputs, asks Codex to create/update
missing or affected Document/article source, validates through supported Cozy
operations, and invokes the admitted PDF route only after those sources are
ready. It does not merely call a renderer with assumed pre-existing prose.

P600-07 creates/adapts the orchestration and producer skills under the applicable
skill authoring workflow. P600-08 verifies a real skill invocation with actual
Codex-authored intermediate files and Cozy calls, followed by feedback-driven
updates. Complete manually authored fixtures and deterministic renderer tests
are useful supporting evidence, but alone do not establish the requested
Codex-inclusive workflow. Generation/review still does not imply approval or
authorize publication. This planning update does not itself edit/install skills.

### Source/action graph details

The intended source/action graph is:

- Core to Document: Codex authors complete localized prose and organization
  grounded in Core and authorized authoring inputs, retaining explicit Core
  references. Existing accepted Document prose is reused when valid.
- Document to `index.dox`: Codex preserves accepted organization and prose in
  SmartDox syntax, including term markup and declared article assets.
- Document and Core to Summary: Codex authors deliberate concise selection,
  ordering, wording and semantic diagrams in the existing Summary DSL.
- `index.dox` to article HTML/PDF: supported SmartDox rendering routes.
- Summary to summary-slide PDF: the admitted Phase 59/40 route when available.
- Document/Core/Summary to infographic and optional video: existing supported
  authoring and rendering tools, with explicit visual/storyboard inputs.

This is a capability-set plan, not a claim that every downstream route already
works. P600-06 inventories existing maintained article, article/media and preview
skills and their tested tools; missing capabilities become explicit upstream
dependencies. Phase 59/61 priorities and contracts are preserved. No new media
renderer or automatic creative article compiler is introduced by this proposal.

For wording feedback, update Document and then derive the article. Direct
article prose edits are reconciled to Document before acceptance. Logical
changes update Core and the affected Document; syntax-only article repairs can
remain in `index.dox` when prose is unchanged. Review Summary after relevant
Document changes and update its selected content and existing bindings through
the admitted source-validation mechanisms. This does not introduce new
hash-based local artifact management.

Promote the earlier representation-update observation into P600-01 and P600-05:
article-target freshness means current with respect to its actual article
inputs, not that Document-to-article reflection has occurred. With Document
changed and `index.dox` unchanged, DSL views rebuild while the article target
may reuse its existing HTML. The UI/help identifies that source boundary and
the separate authoring action. No hash or annotation extension is required;
mtime is build freshness, not prose-equivalence evidence.

P600-07 reconciles maintained companion skills after contract admission; the
skill loop derives inputs before invoking affected requested render targets.
P600-08 verifies initial creation and feedback updates in isolated projects,
including failures, resume, locale boundaries and unsupported-route reporting.
Its scenarios include Core-only input with downstream DSLs absent, an all-current
request with no rewriting, selective update and a multi-product request with
shared prerequisites generated once. Missing producers and cycles expose the
blocking dependency path. Existing make-level render freshness and source-schema
validation remain distinct from Codex's semantic authoring decisions.
60A generation remains non-authoring. Neither workflow approves prose or
authorizes publication. This planning update changes no installed skill,
article, media artifact or external driver.

## Earlier planning and handoff

The existing Phase 60 is revised in place from its unimplemented article/Core
annotation proposal. Its current Steps remain OPEN; the earlier proposal is
retained in notes and chronological journals as planning history.
The chronological prose-authority clarification remains applicable. Phase 59
and Phase 61 stay independently planned; closed Phase 58.2 is not reopened.
After product contracts are promoted, reconcile article-authoring, article/media
and preview skills under Phase 60B with the new targets and make-level behavior.

## References

- [Decision journal](../journal/2026/09/2026-09-14-confirmation-views-and-make-dependencies-decision.md)
- [Prose authority clarification](../journal/2026/09/2026-09-14-document-description-prose-authority-decision.md)
- [Existing Document/Summary v2 specification](../spec/document-project-document-and-summary-description-v2.md)
- [Phase 60 checklist](../phase/phase-60-checklist.md)
