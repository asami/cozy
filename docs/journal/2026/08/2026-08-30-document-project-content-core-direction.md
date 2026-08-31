# Document Project and Content Core Direction

Date: 2026-08-30

Status: planning rationale; non-normative

## Context

The current article-media work has four representative use cases:

| Use case | Workspace | Delivered representations |
|---|---|---|
| SimpleModeling.org article | SimpleModeling.org site project | article HTML, article PDF, infographic, summary-slides PDF, video |
| BoK article | Cozy BoK project | article HTML, article PDF, infographic, summary-slides PDF, video |
| Business report | document-production directory | article PDF, infographic, summary-slides PDF |
| Business report with video | document-production directory | article HTML, article PDF, infographic, summary-slides PDF, video |

The production sequence is currently described in artifact-oriented terms:
review the article outline in HTML, author the SmartDox article, create the
infographic and summary slides, review the video outline in HTML, and render
the video. The commands and delivery adapters differ among a normal SmartDox
site, Cozy BoK, and a standalone business-document directory, but the semantic
work is largely common.

The infographic is not merely a publication sidecar. It is used in the
article, summary slides, and video. Treating the completed article as the sole
upstream authority therefore creates an undesirable dependency: editorial
wording changes would make unrelated video or visual output stale, while
article-only knowledge could drift away from the infographic and video.

The requested direction is to establish a media-neutral semantic core before
article authoring, derive the article and other representations from that
core, manage the work as a Document Project, and visualize its workflow and
currentness.

## Confirmed current boundaries

- `cozy media scaffold article` already creates an embryonic source package
  containing `article.dox`, `brief.json`, `media.yaml`, an editable
  infographic SVG, Slide IR, review files, and output directories.
- The current scaffolded `brief.json` carries only knowledge and language
  identity. It is a suitable evolutionary location, but it is not yet a
  shared semantic model.
- `cozy.media.v1` binds a knowledge source and declared media resources.
  Current receipts prove exact input/output identity and structural
  currentness.
- The current media contract intentionally keeps article `.dox`, editable
  infographic SVG, and semantic slide or Visual Page input as their respective
  medium-specific sources of truth.
- `cozy.media.review-state.v1` records presentation alignment and must not be
  silently widened into a generic project workflow state.
- Phase 41 plans a self-contained Explanation Structure Review HTML over
  accepted presentation semantics. It is a read-only review projection, not a
  project authority or a complete Document Project dashboard.

These constraints mean that the new semantic core must be additive. It should
not replace medium-specific authoring sources or reinterpret existing receipt
and review-state schemas.

## Direction selected

Introduce two separate concepts:

1. **Content Core** is the shared semantic authority for claims, concepts,
   evidence, terminology, examples, and relations that must remain consistent
   across article, infographic, slides, and video.
2. **Document Project** is the operational envelope that selects a reusable
   workflow, workspace and delivery adapters, requested deliverables, provider
   bindings, review gates, and derived currentness.

`Document Project` is retained as the user-facing project term even when the
deliverable set includes video. `Content Core` is media-neutral and may later
be reused by another project type without turning the project workflow into a
knowledge model.

The authority layers are:

```text
source material and research evidence
  -> Content Core                     shared semantic authority
      -> Article Composition/Source   article expression authority
      -> Visual Composition/SVG       infographic expression authority
      -> Slide/Visual Page IR         presentation expression authority
      -> Video Composition/Storyboard audiovisual expression authority
          -> renderer outputs         derived delivery artifacts
```

Content Core is not a complete article draft. Conversely, article prose is
not automatically promoted into Content Core. When article authoring discovers
a new shared claim, fact, term, or relation, that information must first be
accepted into Content Core before cross-media acceptance. Article-local
transitions, rhetorical phrasing, and detailed exposition remain in the
article source.

## Provisional Content Core contract

The candidate schema identity is `cozy.content-core.v1`. The exact grammar is
deferred to a normative specification Phase, but the first contract should
cover these semantic areas:

```yaml
schema: cozy.content-core.v1
id: knowledge-hub-overview
language: ja

intent:
  audience: business-and-technology-readers
  purpose: explain-the-value-and-mechanism

messages:
  primary: ...
  supporting: [...]

concepts:
  - id: knowledge
    term: ...
    definition: ...

claims:
  - id: claim-1
    statement: ...
    evidence: [evidence-1]
    importance: primary

evidence:
  - id: evidence-1
    source: source-1
    summary: ...

relations:
  - from: knowledge
    type: enables
    to: model

examples: [...]
terminology: [...]
constraints: [...]
visualCandidates: [...]
openQuestions: [...]
sources: [...]
```

The normative design should use stable IDs and a closed, versioned vocabulary
where an existing Cozy catalog already defines the concept. In particular,
logical relations and visual candidates should reference the accepted
Presentation Semantics catalogs instead of creating free-form synonyms.

The Content Core must not contain:

- page coordinates, fonts, colors, CSS selectors, or PowerPoint Shape kinds;
- completed article prose solely for stylistic reasons;
- slide numbers or video timing;
- renderer commands or workspace-specific paths;
- publication destinations; or
- mutable workflow state.

For the first version, one Content Core should have one declared language.
Bilingual projects may contain aligned locale-specific cores with stable
semantic item IDs. A later normative design must decide how localization
alignment is recorded; it should not mix unrelated localized strings into an
untyped multilingual value.

## Provisional Document Project contract

The candidate descriptor identity is `cozy.document-project.v1`:

```yaml
schema: cozy.document-project.v1
id: knowledge-hub-overview-ja
profile: simplemodeling-article-v1
workflow: article-media-v1
contentCore: content/core-ja.yaml

workspace:
  kind: site

deliverables:
  - article-html
  - article-pdf
  - infographic
  - summary-slides-pdf
  - video

bindings:
  article.build:
    provider: smartdox
  infographic.build:
    provider: cozy
  summary-slides.build:
    provider: cozy
  video.build:
    provider: cozy
  delivery.publish:
    provider: simplemodeling-site
```

The project must bind logical operations to providers independently. A single
global `provider: cozy` or `provider: smartdox` is insufficient for BoK:
SmartDox may render article HTML/PDF while Cozy validates, packages, registers,
or publishes the BoK representation.

The initial closed workspace kinds should be `site`, `bok`, and `directory`.
The initial deliverable vocabulary should be `article-html`, `article-pdf`,
`infographic`, `summary-slides-pdf`, and `video`. The exact provider and
delivery-adapter identity extension rules remain a normative design decision.

## Common workflow and dependency graph

The reusable workflow should be expressed in logical operations rather than
embedded CLI command strings:

```text
core.prepare
core.review
article.plan
article.review
article.build
infographic.plan
infographic.review
infographic.build
summary-slides.plan
summary-slides.review
summary-slides.build
video.plan
video.review
video.build
package.verify
delivery.publish
```

Requested deliverables activate branches; they do not select separately
copied workflow definitions. A non-video business report omits the video
branch. A project that does not deliver article HTML may still maintain the
same article source and generate only the selected PDF representation.

### Workflow instance and work-product model

The reusable workflow definition and one project's execution state must be
separate concepts. `article-media-v1` defines the logical operation DAG,
dependencies, required inputs, produced work-product roles, and review gates.
The Document Project selects that definition and derives one Workflow Instance
from its requested deliverables, profile, provider bindings, and workspace
binding. The project must not copy and then independently edit the common DAG.

Workflow management needs three related projections:

1. **Workflow definition**: the reusable logical operations and dependency
   graph;
2. **Workflow instance snapshot**: the currently active, omitted, ready,
   blocked, running, and completed nodes for one Document Project; and
3. **Operation attempts**: append-only execution evidence recording exact
   inputs, provider/profile, outputs, diagnostics, receipt, and completion
   time for each attempted logical operation.

Intermediate results are first-class Work Products rather than anonymous files
between commands. Each declared Work Product has a stable project-local ID,
role, kind, authority level, producer operation, consumer operations, expected
completion criteria, current artifact identity when materialized, and optional
review gate. The initial role vocabulary should distinguish at least:

- `authority`: Content Core or medium-specific editable source;
- `plan`: outline, composition, Visual Page Set, Storyboard, or another
  accepted production plan;
- `candidate`: materialized but not yet accepted representation;
- `review-projection`: generated HTML/PDF used to inspect a candidate;
- `deliverable`: selected final project output; and
- `receipt`: deterministic identity/currentness evidence.

This role is independent from file format. For example, an HTML file can be a
review projection or a selected article deliverable, and a PDF can be a review
artifact or a final business-report deliverable. The descriptor or workflow
definition must declare the role explicitly instead of inferring it from the
extension or output directory.

The current Workflow Instance Snapshot is generated from source identities,
receipts, reviews, and operation-attempt evidence. It may be cached as a
versioned machine-readable projection for fast inspection, but it is never a
second writable workflow authority. Removing that cache and inspecting the
same accepted project inputs must reconstruct the same state.

The semantic and rendering dependencies are provisionally:

```text
Content Core
  + Article Composition/Source
      + accepted Infographic -> article HTML / article PDF

Content Core
  + Visual Composition       -> editable Infographic -> delivery image

Content Core
  + Slide/Visual Page IR
  + accepted Infographic     -> summary-slides PDF

Content Core
  + Video Composition
  + accepted Infographic     -> video review HTML -> video
```

Workflow order may put article authoring before video authoring for practical
reasons, but that order must not make article prose the video's semantic
authority. The video depends on the Content Core, its own composition, and the
shared infographic. This avoids invalidating the video because of an
article-only editorial correction.

The current article-PDF contract binds the infographic authority identity but
does not pass an infographic path to the SmartDox renderer. A future
normative change must separately define how an article actually includes or
references the visual; receipt binding alone proves alignment/currentness, not
visible inclusion.

## Project profiles

Profiles should be templates over the common workflow, not independent
workflow copies:

| Profile | Workspace | Typical bindings | Active deliverables | Delivery adapter |
|---|---|---|---|---|
| `simplemodeling-article-v1` | `site` | SmartDox article, Cozy media | all five | SimpleModeling.org site |
| `bok-article-v1` | `bok` | SmartDox and/or Cozy per operation | all five | Cozy BoK |
| `business-report-v1` | `directory` | SmartDox article, Cozy media | PDF, infographic, summary slides | filesystem package |
| `business-report-video-v1` | `directory` | SmartDox article, Cozy media/video | all five | filesystem package |

For the video-bearing business-report profile, article HTML is treated as a
required deliverable because it was included in the stated publication set,
even though the initial procedural list mentioned only SmartDox article PDF.
The normative profile can revise that assumption explicitly if the HTML is
only an internal review artifact.

`filesystem package` means a locally deliverable package, not an inferred
external publication. Upload, deployment, or remote distribution remains a
separate explicitly selected operation.

## Standalone and hosted project topology

A filesystem directory and a BoK are not equivalent project containers. The
management model must distinguish:

- **Document Project**: one Content Core and its aligned article, infographic,
  slide, video, review, receipt, and deliverable set;
- **Document Workspace**: the environment that hosts zero or more Document
  Projects and supplies source roots, registries, build context, delivery
  adapters, and workspace-wide operations; and
- **Workspace Binding**: the explicit association between one Document Project
  and a hosted article identity, locale, project package, artifact-placement
  profile, and publication ownership unit.

The default topologies are:

```text
standalone directory
  Document Workspace (directory root)
    == Document Project root
       -> local deliverable package

BoK repository
  Document Workspace (BoK root)
    -> Document Project A (article identity + locale)
    -> Document Project B (article identity + locale)
    -> shared BoK taxonomy, bibliography, registry, build, and publication
```

SimpleModeling.org is also a hosted topology rather than a standalone
directory, although its site-registration and delivery adapter differ from a
normal BoK.

### Refinement: one canonical co-located project package

The initial workspace-binding analysis still exposed the current physical
split between `src/main/doxsite` and `src/main/media` to the author. That would
leave BoK authoring operationally different from an ordinary directory and is
not the selected long-term direction.

Both topologies should use the same canonical, self-contained Document Project
layout. The leading suffix candidate is a `.dox` directory. The article is the
project-root `index.dox`, while the directory name preserves the legacy article
source name:

```text
abc.dox/
  document-project.yaml
  index.dox
  content/
    core-ja.yaml
  infographic/
    infographic.svg
  presentation/
    ...
  video/
    ...
  review/
    ...
  target/                 generated and ignored
```

For a standalone document, `abc.dox/` is the working directory selected by the
user. For a BoK, the same package is hosted at the article's location below
the BoK source root:

```text
src/main/doxsite/<category-path>/<slug>.dox/
```

The proposed compatibility mapping is explicit:

```text
legacy source:  src/main/doxsite/xxx/yyy/abc.dox            (regular file)
project source: src/main/doxsite/xxx/yyy/abc.dox/index.dox  (directory package)
articleIdentity: xxx/yyy/abc
```

The two source paths are alternative physical representations of the same
article identity, not two articles and not two writable authorities. They must
not coexist for one normalized identity; using the same `abc.dox` pathname
makes that exclusivity a filesystem invariant. The project form must preserve
the legacy canonical article identity, public route, locale association,
links, and publication keys. This is a new Cozy/SmartDox source-projection
contract; current automatic `index.dox` behavior is not assumed to provide
that equivalence. The normative Phase must freeze and prove the exact
HTML/public path compatibility and collision diagnostics.

The proposed closed dispatch is:

| Path shape | Meaning |
|---|---|
| `<slug>.dox` direct regular file | legacy single-source article |
| `<slug>.dox/` direct directory containing `document-project.yaml` and `index.dox` | Document Project package |
| `<slug>.dox/` without either required file | invalid package |
| `<slug>.dox.d/` | reserved/rejected generated or work directory |
| symlinked `.dox` file or directory | rejected |

This follows the accepted Cozy/SmartDox `.video/` precedent:
`<slug>.video/index.dox` is explicitly folded into the public `<slug>.dox`
article identity, while `<slug>.video.d` is reserved for generated/work data.
A `.dox/` package still requires a new explicit fold; it must not be obtained
by generalizing the `.video` rule accidentally.

Suffix alternatives remain design inputs:

- `.dox/` is the leading candidate because it preserves the exact logical
  filename, prevents legacy/project coexistence at the same pathname, and is
  concise;
- `.document/` is more visibly a directory package but introduces another
  public-name rewrite and permits an accidental sibling `abc.dox`; and
- `.dox-project/` is explicit but verbose and couples a user-facing pathname to
  the implementation term `Project`.

The `.dox/` choice is provisional until file discovery, editors, shell globs,
SmartDox source loading, links, bibliography scanning, watch mode, publication
metadata, and file-to-directory Git migration are covered by Executable
Specification.

This refines the earlier separate `src/main/document-projects` candidate. The
co-located form better preserves ordinary-directory operation and removes the
author-visible split between article and media work.

There is existing structural precedent, but not yet the complete Document
Project contract: Cozy already admits project knowledge packages below
`src/main/doxsite/projects/<category>/<slug>` containing `index.dox` and
`project.yaml`, and accepted article-media fixtures place `media.yaml` below
the configured BoK source. The new package must use a distinct
`document-project.yaml` marker and must not collide with the existing software
Project knowledge-package semantics.

Because the current BoK configuration passes `src/main/doxsite` to SmartDox,
the normative contract must define a safe source projection. Only `index.dox`
and explicitly declared public source resources may enter the article/site
input. Content Core, raw media sources, review evidence, receipts, templates,
and `target` work must be excluded even if the underlying SmartDox scanner
would currently ignore their extensions. Production behavior must be proved by
Executable Specification rather than relying on an incidental filename filter.

A marked Document Project may expose its working article in an explicit local
preview, but a production BoK build or registration must require current
project verification and applicable review/alignment evidence. An incomplete
co-located directory must not become published merely because `index.dox`
exists below the source tree. Unmarked legacy `.dox` articles retain their
existing behavior until an explicit migration policy changes it.

The canonical package is integrated into host-owned views only after its
selected gates pass:

```text
Document Project package
  -> article source view    -> project-root index.dox
  -> public artifact place  -> configured site/artifact repository paths
  -> registry transaction   -> src/main/publication/...
  -> aggregate BoK build    -> generated site trees
```

This preserves the user's proposed lifecycle—create and manage one `abc.dox/`
project, then place the required data—without making authors operate multiple
source roots. `src/main/media` becomes a legacy/compatibility input for existing
packages rather than the default authoring root for new Document Projects.

Placement must be a deterministic operation with an exact manifest and
receipt. It must preflight collisions, validate the expected prior installed
identity, write selected artifacts atomically, reject hand-edited drift, and
update only the exact article/locale/resource registry ownership unit.
Symlinks and broad directory synchronization are not part of the contract.

During migration, current flat `src/main/doxsite/<category>/<slug>.dox` files and
split `src/main/media/<category>/<slug>` inputs remain supported. An explicit
import/migration operation may replace the file pathname with a same-named
directory package and combine its article/media inputs, but a project must
never have two writable authorities after migration. New scaffolding should
create only the co-located form. Compatibility validation must prove that the
legacy and new forms project the same accepted article/media identities where
both are supported.

### Rollout boundary: Article 8 pilot

Adoption will use an explicit operational boundary rather than changing an
article already in production:

- Article 7 continues with the current workflow and current directory layout.
  It will not be converted to a Document Project during its production.
- Article 8 is the first pilot of the `*.dox/` Document Project form, Content
  Core, derived article/media workflow, and project-level review view.
- The BoK workspace must therefore support the current and new forms at the
  same time. Introducing Article 8 must not change the accepted behavior or
  public identity of Article 7 and earlier articles.
- Articles 1 through 7 are not automatically or retroactively migrated as part
  of the pilot.

The Article 8 pilot is the representative production driver for the first
implementation plan. Its acceptance evidence should cover at least:

1. the same Document Project contract in an ordinary directory and a
   BoK-hosted article directory;
2. Content Core derivation into the article, infographic, summary slides, and
   optional video authorities;
3. preservation of the BoK article identity, links, and public URL behavior;
4. safe source projection that excludes project-internal files from SmartDox
   and site publication inputs;
5. verification and review gates before workspace placement or publication;
6. deterministic placement manifests and receipts for generated artifacts;
7. `current`, `stale`, `missing`, and `not-applicable` propagation across the
   project outputs; and
8. a project review page that makes the page flow, page semantics, and visual
   structure inspectable together.

A pilot failure must be isolated to Article 8 and must not require rewriting
Article 7 or disabling the current workflow. After Article 8 is accepted, a
separate decision can make the new form the default for later articles, revise
the suffix or descriptor contract, and decide whether older articles should
ever be migrated. Until that acceptance, new-form scaffolding should require an
explicit pilot selection rather than silently changing the default workflow.

This rollout decision records the implementation driver and compatibility
boundary only. It does not activate a Phase, change strategy state, or modify
Article 7 or Article 8 content.

### Standalone directory

For an ordinary document-production directory, the descriptor parent can be
both project root and workspace root. The project owns its Content Core,
article source, media authorities, review artifacts, receipts, and local
delivery package. There is no external article registry or aggregate-site
state unless one is explicitly added later.

The usual lifecycle can therefore close locally:

```text
produce -> review -> verify -> assemble local deliverables
```

`assemble` does not imply upload or external publication. A directory may
contain more than one project in the future, but v1 should make the one-project
root the simple default rather than require a registry for the ordinary case.

### BoK-hosted article project

A BoK repository is an aggregate Document Workspace. Existing Cozy BoK
contracts already separate:

- `src/main/doxsite`: public human-readable SmartDox source;
- `src/main/media`: durable article-media input;
- `src/main/publication`: durable Cozy-managed publication registry; and
- `doxsite.d`, `website.d`, `antora.d`, and `target`: generated working or site
  output.

Current BoK article production spans host-owned roots, but the planned
Document Project removes that split for new work. Its binding needs at least:

```yaml
workspaceBinding:
  kind: bok-article
  articleIdentity: development-process/example
  locale: ja
  projectPackage: src/main/doxsite/development-process/example.dox
  placements:
    articleSource: index.dox
    artifactProfile: bok-publication
```

This is a provisional shape, not a frozen placement rule. In particular,
existing accepted packages may use another explicit descriptor location. The
normative contract must support them through explicit import/migration or a
compatibility binding and must never infer identity or ownership from a
filename or directory alone.

The portable `document-project.yaml` must not embed a machine-absolute BoK root
or escape its package through authored `..` paths. The article identity is
declared explicitly and validated against the package's admitted BoK binding;
it is not inferred silently from the directory spelling. Workspace artifact
placement belongs to the BoK-owned profile/registry. The BoK adapter resolves
named host roots and then applies the existing containment, direct-file,
non-symlink, registry ownership, and locking rules.

The stable identities are independent:

- Document Project ID identifies the production effort and deliverable family;
- Content Core ID identifies the shared semantic subject;
- BoK `articleIdentity` identifies the hosted SmartDox article;
- locale identifies the exact article-media variant; and
- media resource/artifact identities identify individual representations.

They may have similar text, but one must not be inferred from another. Existing
BoK article-media correlation remains keyed by exact article identity, locale,
and role, with all recognized roles/locales for one article identity governed
by its existing publication ownership rules.

### Project completion versus workspace integration

A BoK article has three independent completion surfaces:

| Surface | Meaning | Owner |
|---|---|---|
| Production | Content Core and selected article/media outputs are current and reviewed | Document Project |
| Integration | Exact article/locale representations are registered consistently in the BoK workspace | BoK workspace adapter and registry |
| Workspace delivery | Aggregate BoK build/stage/publish reflects the registered project | BoK workspace workflow |

A project may be `production: accepted` while `integration: unregistered`.
It may be registered while the aggregate BoK site has not been rebuilt. A
successful BoK build does not prove that an edited local project was current or
semantically accepted before registration.

The ordinary directory has the same model with hosted integration explicitly
not applicable:

```yaml
production: accepted
integration: not-applicable
delivery: assembled-current
```

A BoK-hosted project may instead report:

```yaml
production: accepted
integration: registered-current
workspaceDelivery: not-observed
```

These are derived views over receipts, review decisions, registry evidence,
and workspace build evidence. They are not mutable user-authored status fields.

### Operation and authority consequences

The logical workflow must split local project operations from host workspace
operations:

```text
project.verify
project.assemble
workspace.place
workspace.register
workspace.verify
workspace.build
workspace.publish
```

For a standalone directory, `project.assemble` normally completes the selected
workflow and workspace placement/registration are omitted. For a BoK,
`index.dox` is already the co-located article source; project assembly produces
an exact artifact-placement candidate. `workspace.place` installs only the
selected public artifacts and `workspace.register` performs the exact registry
mutation using existing ownership and locking rules. Aggregate BoK build or
publish remains a separately visible workspace operation and must not run
implicitly merely because one article project completed.

This separation also prevents one article workflow from claiming ownership of
unrelated BoK articles, categories, glossary entries, bibliography, publication
records, generated site trees, or upload policy. The project dashboard may
display the host workspace state, but it cannot silently convert workspace
readiness into project semantic approval or vice versa.

## Receipts, alignment, and derived state

Document Project state should be a projection of evidence, never a mutable
stage string that can drift away from the files.

Four independent state dimensions are needed:

- completion coverage: `not-started | partial | complete` plus satisfied and
  total criterion counts;
- artifact currentness: `missing | current | stale | failed`;
- review decision: `pending | accepted | rejected | stale`;
- operation readiness: `blocked | ready | running | succeeded | failed | omitted`.

An aggregate project view may summarize those dimensions, but it must retain
the underlying reason and receipt identities.

Completion coverage answers what has been authored or assembled inside a Work
Product. It must be derived from typed criteria rather than an arbitrary
user-entered percentage. A compact display may show `3/5` or `60%`, but the
underlying state must retain the exact satisfied, unsatisfied, and
not-applicable criteria. `complete` does not imply `current` or `accepted`: a
complete article draft may be stale after a Content Core change, and a current
infographic candidate may still have a pending visual review.

The initial Work Product kinds should provide reusable criterion catalogs. The
exact normative vocabulary remains Phase work, but the representative coverage
should include:

| Work Product | Example completion criteria |
|---|---|
| Content Core | intent, primary/supporting messages, concepts, claims, evidence links, relations, terminology, open-question disposition |
| Article | accepted plan, required section drafts, claim coverage, references, infographic inclusion, buildable source |
| Infographic | semantic plan, editable SVG, claim/relation coverage, content alignment, visual review, delivery export |
| Summary slides | page plan, page semantics, infographic use, visual projection, deterministic render, review |
| Video | scene plan, narration, Visual Page/infographic mapping, audio/asset readiness, review render, final render |
| Package/delivery | selected deliverables present, exact receipts, verification, placement/registration evidence where applicable |

Criteria may be required, optional, or not applicable according to the selected
profile and deliverables. The dashboard must show why a criterion is omitted;
it must not count omitted work as silently completed. Criteria should normally
be satisfied by inspectable source or receipt evidence. A human/AI review
decision satisfies only an explicit review criterion and cannot manufacture
missing source or build evidence.

The project summary should therefore avoid one ambiguous `70% complete`
status. It should instead be able to report, for example:

```yaml
article:
  coverage: { status: partial, satisfied: 4, total: 6 }
  currentness: stale
  review: stale
  blockedBy: [content-core-alignment]
  nextOperations: [article.review]
```

This makes authored progress, dependency freshness, approval, and executable
next actions separately visible.

Hosted projects additionally need integration and workspace-delivery
projections. Those projections should be absent or `not-applicable` for a
standalone directory rather than being faked as successful project operations.

Every accepted derived artifact should bind:

- the exact Content Core semantic identity;
- its medium-specific authority identity;
- every shared asset it consumes, including the infographic;
- provider/profile identity; and
- output identity.

Structural receipts continue to prove deterministic identity and currentness.
Semantic approval remains an explicit human/AI review decision. A candidate
future `cozy.content-alignment.v1` record may bind one Content Core revision to
one article, infographic, slide, or video authority and its review decision.
It must not mutate or silently replace `cozy.media.review-state.v1`.

The minimum stale propagation is:

| Change | Directly stale consequences |
|---|---|
| Content Core | all medium-specific alignments and all deliverables |
| Infographic authority | article HTML/PDF, summary slides, video review HTML, video |
| Article authority | article review/output only |
| Slide/Visual Page authority | summary-slides review/output only |
| Video authority | video review/output only |
| Provider/profile binding | outputs and receipts produced by that binding |

Staleness should propagate through declared dependency identities. It should
not be inferred from timestamps or filenames.

## Review HTML and visualization

Review HTML artifacts are generated, self-contained projections and never
semantic authorities:

- `core-review.html` exposes messages, claims, evidence, concepts, relations,
  open questions, and planned representations;
- `article-review.html` exposes the article outline, narrative flow,
  per-section meaning, and intended display structure before full prose;
- `video-review.html` exposes scene flow, narration intent, Visual Page use,
  and infographic use before rendering; and
- `project-dashboard.html` exposes the complete workflow graph, active and
  omitted branches, provider bindings, current/stale evidence, review gates,
  deliverables, and next executable logical operations.

Phase 41's Explanation Structure Review HTML can supply the presentation-page
inspection primitive used within article-summary and video review. The later
Document Project dashboard sits above that artifact and must not be folded
into Phase 41 or turn Phase 41 HTML into workflow authority.

The project dashboard must show command/provider differences rather than hide
them, while still presenting one common logical workflow. It should make the
following distinctions visible at a glance:

- semantic authority versus generated review/output;
- currentness versus review acceptance;
- SmartDox, Cozy, Cozy BoK, and delivery-adapter bindings;
- required, optional, and omitted deliverables;
- shared infographic use by article, slides, and video; and
- the exact dependency that makes an artifact stale or blocks an operation.

The integrated dashboard should provide three coordinated views rather than
forcing every question into one graph:

1. **Workflow view** shows the logical DAG, active/omitted branches, current
   operation readiness, provider binding, gates, and next executable nodes.
2. **Work Product matrix** lists every authority, plan, candidate, review
   projection, deliverable, and receipt with coverage, currentness, review,
   producer, consumers, and last accepted evidence.
3. **Work Product detail** shows the exact completion criteria, satisfied and
   missing items, consumed identities, stale reasons, attempt history,
   diagnostics, reviews, and available next operations.

The default graph node should remain compact: logical operation name,
provider, readiness, produced Work Product, and a small four-dimension status
summary. Selecting the node or Work Product reveals criteria and evidence. A
shared Work Product such as the infographic must appear once with visible
edges to article, slides, and video rather than as three duplicated progress
items.

Operation history and current project state must also remain distinct. The
dashboard initially shows the current evidence-derived snapshot, while an
attempt-history view explains how it was reached and retains failed or
superseded attempts. A successful old attempt remains historical evidence; it
must not make a stale current output appear successful.

For a hosted project it must also show the boundary between project production,
workspace registration, aggregate build, and external delivery. The default
view should remain small for a standalone directory and reveal BoK-wide state
only when the selected workspace binding requires it.

## Refinement: conversational core authoring and selectable outputs

The later Phase 42 discussion clarified that Content Core is not expected to
appear as one manually completed input file. The intended authoring experience
starts with an idea and source material, develops the idea through a dialogue
with generative AI, and accumulates accepted semantic items in Content Core.
The generated conversation is not itself authority. Provider/model identity,
prompt and response receipts, proposal disposition, and the exact resulting
core identity remain attempt/provenance evidence.

The next loop uses generated `core-review.html` (内容確認HTML) rather than editing a
dashboard as source:

```text
idea -> AI dialogue -> core candidate -> core-review.html
     -> human feedback -> core revision -> accepted core
     -> selected artifact generation and review
```

Feedback must be classified before write-back. A change to claims, evidence,
terms, relations, narrative intent, or cross-media representation returns to
Content Core. A SmartDox wording edit, slide layout adjustment, infographic
styling change, or video timing change remains in that medium's authority.
This preserves one shared semantic source without turning Content Core into a
complete article, storyboard, or visual-layout document.

The discussion also refined “必要に応じて作成” into an explicit branch
disposition: `required`, `optional`, or `disabled`. Required branches
participate in project completion. Optional branches are visible but do not
block completion until activated. Disabled branches are omitted with a reason
and are never counted as completed work.

The first artifact catalog is now understood as follows:

| Artifact family | Phase 42 interpretation |
| --- | --- |
| Article | SmartDox source is the medium authority; article HTML/PDF are selected derived deliverables |
| Slides | Slide/Visual Page semantics produce a selected summary-slides PDF |
| Infographic | editable SVG remains authority and PNG is the selected delivery representation |
| Video | Storyboard/Visual Page/video authority produces the rendered video and `video-review.html` (動画確認HTML) |
| Logical chart HTML | Phase 41 Explanation Structure Review supplies the page-flow, meaning-structure, and display-structure projection; Phase 42 manages it as a selectable Work Product |

`core-review.html`, `video-review.html`, Phase 41 logical chart,
and Document Project dashboard remain separate projections. The first gathers
semantic feedback for core. The second reviews audiovisual composition and
render evidence. The third inspects explanation structure. The dashboard shows
workflow, evidence-derived state, currentness, gates, and next operations.

For usability, the workflow may present the derived lifecycle
`IDEATION -> CORE_DRAFT -> CORE_REVIEW -> CORE_ACCEPTED ->
ARTIFACT_PLANNED -> ARTIFACT_GENERATING -> ARTIFACT_REVIEW ->
ARTIFACT_ACCEPTED -> PROJECT_COMPLETE`. These labels are projections from
Work Products, reviews, and receipts rather than mutable status authority.

This refinement changes the earlier exclusion of “automatic semantic
authoring.” Phase 42 still excludes autonomous acceptance or unreviewed claim
invention, but it explicitly admits AI-assisted dialogue, proposal capture,
`core-review.html` review, and accepted feedback into Content Core.

## Phase allocation

The user selected Phase 42 on 2026-08-30 as the development boundary for this
direction. Phase 42 remains planned and not started and does not expand Phase
41. Its cohesive internal stages are:

1. **Contract kernel, scaffold, and commands**: schemas, authority and
   compatibility boundaries, AI-assisted core proposal/review evidence,
   canonical authored `*.dox/` skeleton, and the `document-project` command
   family.
2. **Workflow Instance and Work Products**: logical DAG, profile activation,
   provider bindings, first-class intermediate results, typed criteria, and
   gates.
3. **Evidence-derived state and attempts**: coverage, currentness, review,
   readiness, receipts, stale propagation, and append-only attempt history.
4. **Review projections and project dashboard**: `core-review.html`,
   `video-review.html`, Phase 41 logical-chart integration, coordinated
   Workflow, Work Product matrix, and Work Product detail views.
5. **Representative drivers and closure**: standalone directory and Article 8
   BoK pilot, full validation, and focused Phase review.

The corresponding non-normative specification proposal is
`docs/notes/document-project-workflow-management-specification-proposal.md`.
The authoritative work ledger is `docs/phase/phase-42.md` plus
`docs/phase/phase-42-checklist.md`. This allocation plans work but does not
activate implementation.

## Open design decisions

1. Freeze the exact `cozy.content-core.v1` grammar and decide which current
   Explanation/Presentation catalog identities it references directly.
2. Decide whether `brief.json` is migrated in place to Content Core or retained
   as a compatibility projection during a bounded transition.
3. Define localized Content Core alignment and whether stable semantic IDs are
   required across locales in v1.
4. Define the exact content-alignment decision schema, reviewer identity, and
   stale behavior without weakening existing presentation review state.
5. Define actual visible infographic inclusion for SmartDox HTML/PDF separately
   from receipt authority binding.
6. Freeze provider-binding and delivery-adapter extension rules, especially
   SmartDox-versus-Cozy responsibility inside a BoK project.
7. Freeze navigation and identity links among the separate
   `core-review.html`, `video-review.html`, Phase 41 Explanation Structure
   Review HTML, and Document Project dashboard projections.
8. Determine which operations are public Cozy commands and which remain
   configured SmartDox/BoK workflow bindings.
9. Freeze Document Workspace discovery and binding: standalone descriptor-root
   default, explicit BoK-host binding, registry placement, and compatibility
   with existing accepted media-package locations.
10. Define production, integration, aggregate-build, and external-delivery
    evidence independently, including what `not-applicable` and `not-observed`
    mean in the project dashboard.
11. Freeze the provisional co-located
    `doxsite/<category-path>/<slug>.dox/` package grammar, SmartDox source
    projection, and exact atomic artifact-placement manifest, including import
    of current flat-article/split-media projects and detection of edits made
    directly to installed artifacts or registry projections.

## Non-goals

- Automatically inventing claims, evidence, relations, or narrative meaning.
- Replacing SmartDox article source, editable infographic SVG, Visual Page,
  Slide IR, or Storyboard authority.
- Treating an HTML dashboard or a rendered PDF/video as a write-back source.
- Conflating deterministic receipt currentness with semantic approval.
- Copying the common workflow four times for four project profiles.
- Inferring remote publication from a local business-report package.
- Changing Phase 41, strategy, source code, schema, command, or release state
  through this consideration record.

## Planned handoff

Phase 42 now owns the ready-to-execute planning boundary. It begins by turning
the authority graph, schema examples, scaffold, and command grammar into
normative design/specification. It must then prove that a Content Core change
invalidates all dependent representations while an article-only editorial
change leaves infographic, slides, and video current. Workflow execution and
the integrated dashboard follow only after that kernel is accepted.
