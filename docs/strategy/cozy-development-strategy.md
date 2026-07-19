# Cozy Development Strategy

Date: 2026-07-15

Status: active

## Purpose

This document defines Cozy's standalone development strategy.

Cozy is no longer only a CML-to-Scala generator. Its role is expanding into a
compiler and publication toolchain for engineering knowledge.

The strategic direction is:

```text
source repository
  -> engineering model extraction
  -> generated publication knowledge
  -> SmartDox site BoK source
  -> CAR/SAR/repository artifacts
```

## Strategic Roles

Cozy owns compiler-like responsibilities:

- CML and SmartDox-based model compilation
- sbt project scaffold generation
- CAR/SAR project and archive generation
- engineering metadata extraction from source repositories
- normalized publication source generation
- AI-facing catalog source generation

SmartDox site owns rendering responsibilities:

- HTML/PDF/site projection
- navigation
- multilingual presentation
- site-specific layout

SimpleModeling.org owns the published knowledge platform structure:

- `/ja`
- `/en`
- `/glossary`
- `/ontology`
- `/schema`
- `/smartdox`
- `/textus`
- `/catalog`
- `/samples`
- `/repository`
- `/maven` (legacy compatibility)

Cozy should generate the semantic inputs that allow SmartDox site to build the
BoK and AI-facing engineering knowledge platform.

## Boundary Principles

### Cozy Generates Publication Knowledge

Cozy should produce stable, normalized publication sources such as:

- catalog metadata
- sample metadata
- source tree manifests
- component metadata
- topology metadata
- runtime artifact metadata
- generated SmartDox fragments when needed

These outputs belong in a generated `src/main/publication` workspace.

### SmartDox Site Renders Publication Knowledge

SmartDox site should consume generated publication knowledge and render it into
`website.d`.

It should not become responsible for extracting repository semantics or
packaging release artifacts.

### warehouse Stores Persistent Artifacts

Release artifacts such as CAR, SAR, ZIP packages, checksums, and Maven artifacts
should be treated as persistent and versioned.

They should not be confused with disposable `website.d` output.

## Roadmap

### Phase 4: State Machine Integration Alignment

Status: completed.

Purpose:

- align Cozy state machine generation with CNCF/core execution primitives

### Phase 5: Engineering Publication Compiler

Status: completed.

Purpose:

- create knowledge source from sbt projects
- generate SmartDox site BoK inputs
- align output with the SimpleModeling.org AI-era site structure

Primary reference:

- `docs/journal/2026/05/engineering-publication-compiler.md`
- `/Users/asami/src/dev2025/simplemodeling-org/docs/journal/2026/05/site-structure-expansion-for-ai-era-engineering-knowledge-platform.md`

### Phase 6: Component Repository Publication and Scaffolding

Status: completed.

Purpose:

- make CAR/SAR catalog and publication flow first-class Cozy workflows
- align `cozy`, `sbt-cozy`, and `textus` around component repository metadata
- add Cozy-owned component initialization/scaffolding for projects such as
  `textus-knowledge-editor`
- validate packaged CAR runtime compatibility metadata against the resolved CNCF
  runtime descriptor, while keeping bridge generation defaults rooted in the
  consuming sbt project

Primary reference:

- `docs/phase/phase-6.md`
- `docs/phase/phase-6-checklist.md`
- `docs/journal/2026/05/car-sar-catalog-publish-plan-2026-05-20.md`

### Phase 7: BoK Source and Site Operations Toolchain

Status: completed.

Purpose:

- make BoK source creation, category management, HTML build, and local preview
  first-class Cozy workflows
- support source-project and published-site project separation by default
- use SmartDox category-driven structure without `site-structure.yaml`
- support Japanese single-locale BoK operation with root `website.d` output
- use the standard Textus toolchain Docker image for Antora-based HTML generation

Primary reference:

- `docs/phase/phase-7.md`
- `docs/phase/phase-7-checklist.md`

### Phase 8: Video Knowledge Pipeline

Status: completed.

Purpose:

- make scripted video production a first-class Cozy workflow
- transform recorded demos into transcript, caption, RDF, and Playwright
  replay assets
- formalize and validate the unified Textus toolchain Docker image used by BoK,
  SmartDox PDF, and video workflows
- use the Textus toolchain Docker image for BoK HTML generation, SmartDox PDF
  rendering, video capture, encoding, Node, Remotion, Playwright,
  ffmpeg/ffprobe, whisper.cpp, transcription model/data, and related heavy
  dependencies while keeping Cozy responsible for orchestration and knowledge
  outputs
- consume the unified `ghcr.io/asami/textus-toolchain` image owned by
  `textus-toolchain-runner`, using the current SmartDox PDF dependency image
  line as the baseline
- integrate VOICEVOX as an external HTTP service rather than bundling it into
  the toolchain image
- generate RDF source files from video projects, scripts, timing metadata, and
  artifact provenance
- make Remotion the standard rendering direction while keeping Python/Pillow as
  an intentional toolchain-contained helper path, never as a required host
  dependency

Primary reference:

- `docs/phase/phase-8.md`
- `docs/phase/phase-8-checklist.md`

### Phase 9: Video Publication Registration and BoK Integration

Status: closed.

Purpose:

- complete the registry boundary for `.video/` package publication
- make `src/main/publication` the BoK-facing source of truth for video RDF and
  provenance metadata
- keep generated video artifacts in warehouse/work areas and outside source
  packages
- preserve the existing `publish-video` command surface while enriching its
  publication registry output

Primary reference:

- `docs/phase/phase-9.md`
- `docs/phase/phase-9-checklist.md`

### Phase 10: One-Stop BoK Build and Operation Integration

Status: closed.

Purpose:

- integrate BoK source, publication registry, artifact repository contents, and
  SmartDox site generation under `cozy bok`
- add BoK-level video publication and publication update commands
- make `bok build` consume registered publication metadata and merge registered
  RDF artifacts without running heavy video generation
- preserve source/work/artifact/site boundaries for BoK operation

Primary reference:

- `docs/phase/phase-10.md`
- `docs/phase/phase-10-checklist.md`

### Phase 11: BoK Publication and Upload Workflow Productionization

Status: closed.

Purpose:

- make `cozy bok publish` safe and repeatable for production BoK publication
  operation
- add dry-run, preflight, operation manifest, and step-level diagnostics
- keep upload implementation as a project-owned external workflow command
- preserve Phase 10 source, publication registry, artifact repository, and site output
  boundaries

Primary reference:

- `docs/phase/phase-11.md`
- `docs/phase/phase-11-checklist.md`

### Phase 12: KnowledgeHub BoK Operational Onboarding

Status: closed.

Purpose:

- use `/Users/asami/src/Project2026/bok-knowledgehub` as a real BoK operation
  driver for `cozy bok`
- align KnowledgeHub BoK source, `conf/cozy/config.yaml`, generated scaffold
  docs, and current Cozy defaults
- verify `bok build`, `bok publish --dry-run`, and upload workflow readiness
- convert operational friction into focused Cozy improvements and follow-up
  backlog
- expand BoK knowledge types from terms, projects, scenarios, bibliography, and
  event / actor / role term types toward mono-koto analysis

Primary reference:

- `docs/phase/phase-12.md`
- `docs/phase/phase-12-checklist.md`

### Phase 13: BoK Tag Knowledge Navigation

Status: closed.

Purpose:

- make tags a first-class BoK navigation surface
- keep tags distinct from categories and glossary terms
- connect articles, terms, scenarios, projects, bibliography entries, history,
  and RDF resources through lightweight cross-cutting labels
- render tag dashboards, tag hub pages, and tag-aware RDF navigation
- connect Project knowledge to Component Repository CAR artifact knowledge
  through repository catalog metadata, CAR version pages, descriptors, ABI
  manifests, CML sidecars, and model metadata
- preserve empty-tag fallback behavior for existing BoK projects

Primary reference:

- `docs/phase/phase-13.md`
- `docs/phase/phase-13-checklist.md`

### Phase 14: SIE Integration

Status: closed.

Purpose:

- make SIE (`textus-semantic-integration-engine`) a first-class integration
  surface for Cozy BoK and CAR/SAR publication workflows
- publish a BoK KnowledgeSource manifest at
  `metadata/cncf/knowledge-source.json` so SIE can ingest generated BoK sites
  without scraping rendered HTML
- connect BoK project metadata, SIE CAR/SAR repository catalog entries, and
  SIE-derived RDF / Information metadata
- keep Cozy responsible for BoK publication integration, validation,
  diagnostics, and UI navigation
- keep SIE responsible for semantic integration runtime behavior and
  Information-schema materialization
- verify KnowledgeHub with at least one SIE-linked project without mutating
  published release coordinates

Primary reference:

- `docs/phase/phase-14.md`
- `docs/phase/phase-14-checklist.md`
- `docs/journal/2026/06/bok-sie-integration-handoff-2026-06-28.md`

### Phase 15: CAR Project Metadata Centralization

Status: closed.

Purpose:

- make each generated CAR independently buildable from its own `project.yaml`;
- centralize CAR identity, Scala version, exact development dependencies,
  descriptor metadata, and runtime compatibility;
- distinguish the exact CNCF version used for development from the CNCF
  runtime versions required by the CAR;
- keep generated `build.sbt` files limited to SBT/Cozy projection and wiring;
- apply the same contract to `init component`, `car-sbt-project`, `car`, and
  `car-sar` layouts.

Primary reference:

- `docs/phase/phase-15.md`
- `docs/phase/phase-15-checklist.md`
- `docs/design/car-project-metadata-ownership.md`
- `docs/spec/car-project-scaffold.md`

### Phase 16: CML Value and Datatype Refactoring

Status: closed.

Documentation workflow:

- preserve point-in-time investigations and decisions in `docs/journal`;
- keep the latest implementation specification in `docs/notes`;
- implement and verify them with executable specifications and driver CARs;
- promote verified contracts to `docs/design`, `docs/spec`, and accepted
  grammar documents after implementation.

Purpose:

- restore `VALUE` as the single structural concept for operation payloads;
- define reusable command/query inputs as `# VALUE` plus `input-kind`;
- reject operation-kind and input-kind mismatches deterministically;
- support anonymous and named operation-local input Values;
- support anonymous and named operation-local output Results;
- resolve simple outputs such as `UnitResult` and `IntResult` through a
  CNCF-owned predefined Result catalog;
- retain top-level `# COMMAND`, `# QUERY`, current inline Values, and operation
  convenience forms as explicitly tested compatibility grammar;
- move new scaffold output to canonical Value grammar and migrate a
  representative CAR without silent API/ABI changes;
- use `textus-user-notification` as the primary migration driver and
  `textus-user-account` as the larger operation-contract and literate-metadata
  regression driver;
- audit string-only Values and Datatypes in both drivers instead of preserving
  nominal wrappers without model semantics;
- redefine accepted plain Datatypes as generated nominal scalar types with
  scalar Wire/datastore representation, without preserving the former Scala
  `String` source contract or adding a compatibility mode;
- propagate nominal types through entity and operation surfaces and validate
  the breaking migration in both driver CARs;
- model finite vocabularies as powertypes and transition-owned lifecycle state
  with statemachines;
- use the existing `name = Name` and `title = I18nTitle` semantics as the
  predefined text baseline, classify remaining text families from the same
  model, and carry explicit text-length constraints through generation and
  runtime metadata;
- keep `title` as one locale-aware value structure capable of holding either a
  single locale entry or multiple locale entries;
- model localized label/title/text values as I18N data with preserved locale
  entries and explicit fallback policy, not as an incidental `string` wrapper.

Primary reference:

- `docs/phase/phase-16.md`
- `docs/phase/phase-16-checklist.md`
- `docs/journal/2026/07/cml-operation-value-refactoring-discussion-2026-07-15.md`
- `docs/notes/cml-operation-value-refactoring-spec-proposal.md`

### Future Phase: Model-Driven CAR Project Scaffolding

Purpose:

- make `car-sbt-project` scaffold generation model-driven instead of
  Notice-template-driven
- connect scaffold options to generated CML, factory overrides, and web metadata

### Phase 17: Profile-Driven Video Scaffolding

Status: closed.

Purpose:

- add a first-class video scaffold command, provisionally
  `cozy video scaffold`, that creates a valid Git-managed `<slug>.video/`
  source package;
- generate `index.dox`, `video.yaml`, the initial script/storyboard, and an
  `assets/` contract that can be inspected and built by the existing
  `cozy video` workflow;
- vary the initial scene composition by profile rather than forcing every
  video into one template;
- provide at least an explanation profile and an
  explanation-demo-explanation profile, with room for additional profiles
  after their composition contracts are specified;
- keep profiles responsible for sequence, section roles, timing defaults, and
  asset slots, not for embedding third-party media;
- extract the reusable visual-effect vocabulary demonstrated by the local
  `0714.techfirst.lt` video source into renderer-owned effect primitives and
  named profiles instead of leaving scene scripts dependent on a project-local
  effect tool;
- support reusable transition primitives such as fade, film-burn, slide,
  wipe/soft-wipe, cross-zoom, linear/zoom blur, flip, dreamy zoom, ripple, and
  cross-warp where the renderer implements them;
- support reusable background, text-motion, and accent primitives such as
  flow/roadmap lines, spring pop/stagger, fade rise, underline sweep, and
  scanline;
- compose primitives into three independent purpose profiles:
  section-start visual effects, summary visual effects, and final-page visual
  effects;
- provide a section-start profile based on the left-to-right line sweep used
  by `0714.techfirst.lt`, without requiring a standalone section title page;
- provide a summary profile that introduces the summary, keeps overview and
  conclusion content on one page, and can emphasize the conclusion after the
  overview;
- provide a final-page profile that clearly signals completion with an End
  card, controlled entrance/hold behavior, and an optional completion chime,
  without presenting new explanatory content;
- keep composition profiles and visual-effect profiles orthogonal so an
  explanation or explanation-demo-explanation scaffold can choose each of the
  three visual roles independently;
- write the selected effect profiles into scaffolded `video.yaml` rather than
  hard-coding them into generated scene JSON;
- generate license-safe placeholder frames for every visual asset slot by
  default;
- do not copy, download, or reference the video assets currently published
  below `0714.techfirst.lt/assets` from the default scaffold because their
  redistribution license is not established;
- render the placeholder frame when an optional asset slot has no project
  file, and render the configured image when the project supplies the expected
  file under `assets/` or maps the slot to an explicit project-local path;
- keep asset files and their license/provenance declarations owned by the
  generated video project, and fail clearly when an explicitly configured
  required asset is missing or unreadable;
- make scaffold output deterministic and cover each profile, placeholder-only
  rendering, configured-asset rendering, and missing-asset diagnostics with
  executable specifications;
- cover effect-profile selection, deterministic primitive expansion, unknown
  profile diagnostics, and renderer capability diagnostics with executable
  specifications.

Initial scaffold configuration shape:

```yaml
profile: explanation-demo-explanation
visual-effects:
  section-start: line-sweep
  summary: overview-and-conclusion
  final-page: end-card
assets:
  # Project-owned image files may be assigned to generated asset slots.
```

Initial boundary:

- no network asset fetching during scaffold or build;
- no bundled copyrighted sample media;
- no automatic license inference;
- effect profiles resolve to renderer-neutral primitives before rendering;
- renderer adapters report unsupported primitives explicitly and do not
  silently substitute unrelated effects;
- existing video renderers consume resolved asset slots and expanded effect
  primitives without introducing a renderer implementation per composition
  profile.

### Phase 18: Shared Video Pronunciation Dictionary

Status: closed.

Purpose:

- provide shared video readings through a bundled UTF-8 definition file;
- apply the dictionary only at the VOICEVOX request boundary;
- preserve script-local pronunciation overrides;
- use deterministic longest matching without converting generated readings
  again;
- keep authored narration, captions, and generated metadata unchanged.

### Phase 19: Knowledge-Centered Media Packages

Status: closed.

Purpose:

- bind image and video representations to one BoK knowledge unit;
- separate AI-assisted semantic work from deterministic Cozy operations;
- provide inspect, plan, build, verify, and profile-based publish commands;
- keep reusable packages free of machine-specific absolute paths;
- keep large generated media outside source control while preserving reviewable
  briefs, scripts, SVG masters, PNG representations, and provenance.

## Current Priority

Phase 6 through Phase 19 are closed. Phase 19 added portable knowledge-centered
Media Packages and the `cozy media` inspect, plan, build, verify, and publish
workflow. Publication profiles project one package into a site or external
artifact archive without embedding machine-specific destinations. AI-assisted
skills own thesis extraction, bilingual writing, localization, and visual
review; Cozy owns deterministic conversion, validation, copying, and hashes.

Phase 18 added a shared video
pronunciation dictionary at the VOICEVOX boundary with script-local override,
longest-match, and non-cascading conversion contracts. Phase 17 introduced
profile-driven video scaffolding and established deterministic
`<slug>.video/` source packages, two composition profiles, independent
section-start/summary/final-page settings, and generated license-safe
placeholder assets. Named effect profiles expand to renderer-neutral primitives
with explicit capability diagnostics, and asset slots resolve project-owned
local files or generated placeholders through a machine-readable provenance
contract. The Remotion adapter now consumes the initial primitive set,
preserves project-owned assets, applies deterministic summary and final-page
timing, and carries the profile contract through RDF and publication. Real
Remotion integration verifies both composition profiles and a required
project-owned SVG through the pinned Textus toolchain snapshot image.

Phase 16 established coherent operation Values, CNCF-owned predefined Results,
generated nominal Datatypes, powertype/statemachine ownership, semantic text
and I18N behavior, domain length constraints, confidentiality projection, and
canonical scaffold output. User Notification and User Account verify the
accepted contract through generated Scala 3.3.8 code and CAR boundaries.

Phase 15 completed CAR project metadata centralization. Cozy scaffolds now
generate `project.yaml` as the CAR-local source of identity, Scala version,
exact development dependencies, descriptor metadata, and runtime compatibility.
Generated SBT files project this metadata and do not use the runtime minimum as
the compile dependency version.

Post-closure CAR documentation maintenance adds documentation findings to
integrated `cozy lint car`. The deterministic lint verifies a packaged reference
manual, a user guide, and descriptive component/service/operation metadata. CNCF
generated Help remains responsible for CLI, Help, Manual, OpenAPI, and MCP route
navigation, so CAR authors do not duplicate framework-owned URLs in CML prose.

Phase 8 completed the first-class `cozy video` workflow: structured
project/script parsing, inspect, dry-run artifact/command planning, dependency
checks, Docker-first toolchain mode, unified Textus toolchain image consumption,
VOICEVOX synthesis, rendering, final muxing/encoding, transcription, replay
generation, RDF output, and `.video` publication metadata handoff to SmartDox.

Phase 9 completed Video Publication Registration. `publish-video` registry
output now carries RDF/provenance metadata through `src/main/publication`, and
SmartDox/BoK consumers can use publication metadata instead of workspace or
warehouse scans.

Phase 10 completed one-stop BoK build and operation integration. `cozy bok` can
coordinate publication metadata, registered video artifacts, SmartDox site
generation, and registered Turtle RDF merge behavior.

Phase 11 completed BoK publication and upload workflow productionization.
`cozy bok publish` now has dry-run, preflight, operation manifest, step-level
diagnostics, and external upload workflow boundaries.

Phase 12 completed KnowledgeHub BoK operational onboarding. The driver project
was `/Users/asami/src/Project2026/bok-knowledgehub`, and the phase closed after
production build, CloudFront serving, upload workflow, and ignored generated
state were operationally verified.

Phase 13 implemented BoK tag knowledge navigation. Tags are lightweight
cross-cutting labels that connect articles, terms, scenarios, projects,
bibliography entries, history, and RDF resources without replacing categories
or glossary terms. Phase 13 also completes the generic Project-to-Component
Repository CAR knowledge layer before the SIE-specific Phase 14 catalog
integration.

Phase 14 integrates SIE with Cozy BoK and CAR/SAR publication workflows.
The first concrete contract is
`metadata/cncf/knowledge-source.json`, which lets SIE ingest generated BoK
sites through metadata resources instead of rendered HTML. The boundary is
explicit: Cozy publishes and validates BoK/SIE metadata for navigation and
diagnostics, while SIE remains responsible for semantic integration runtime
behavior and Information-schema materialization.

Current follow-up directions include:

- Post-Phase-16 CML policy backlog, when promoted into a separately scoped
  phase
- BoK KnowledgeSource manifest output and SIE ingestion
- BoK publication and Component Repository public pages
- Video publication registration follow-up, if richer article/navigation
  integration is needed
- Knowledge source compiler expansion
- Model-driven CAR project scaffolding
