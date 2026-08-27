# Cozy Development Strategy

Date: 2026-07-28

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

### Phase 20: Multi-Provider Narration and Cozy-Complete Video Build

Status: closed.

Purpose:

- make narration synthesis a provider-neutral Cozy Video contract rather than
  a VOICEVOX-only implementation;
- support VOICEVOX as an external HTTP provider, macOS `say` as a host-only
  provider, and Piper as the portable English TTS provider through the Textus
  toolchain Docker image;
- keep scene timing, pronunciation conversion, canonical WAV normalization,
  combined audio, and synthesis provenance under Cozy ownership;
- keep macOS system voices outside the Linux toolchain image and reject invalid
  provider/execution-mode combinations before starting synthesis;
- add a pinned, license-audited Piper runtime and English voice models to the
  `textus-toolchain-runner` image without runtime network fetching;
- migrate the Japanese and English SimpleModeling.org Overview videos away from
  project-local effect tools and manual ffmpeg commands;
- generate both Overview videos through Cozy commands alone, using built-in
  Remotion effects, provider-selected narration, final assembly, and ffprobe
  verification;
- add a generic credit catalog/profile/resolver that derives character-material
  and voice credits from actual build usage;
- generate the in-video credit page and publication-ready credit text from one
  effective localized credit set;
- allow a user-level default profile to cover Asami's recurring credits without
  per-video credit configuration;
- keep legal interpretation, third-party asset redistribution, and YouTube
  upload outside the phase boundary.

Primary reference:

- `docs/phase/phase-20.md`
- `docs/phase/phase-20-checklist.md`
- `docs/journal/2026/07/video-credit-profile-handoff-2026-07-20.md`

### Phase 21: Component Repository Discovery

Status: closed.

Purpose:

- add a CNCF-owned public repository index that can enumerate CAR and SAR
  catalogs without HTTP directory listing or repository crawling;
- update and validate that index deterministically from Cozy CAR/SAR
  publication;
- let Textus Launcher retrieve and cache configured public indexes and let
  CNCF Launcher present equivalent admitted development/local identities;
- extend Cozy BoK Component Repository pages from the existing CAR and
  SIE-referenced SAR knowledge to complete indexed CAR/SAR navigation;
- keep artifact availability, runtime registration, and runtime health as
  separate facts.

Primary reference:

- `docs/phase/phase-21.md`
- `docs/phase/phase-21-checklist.md`

### Phase 22: BoK Knowledge Map Component Handoff

Status: closed.

Purpose:

- extend `cozy.rdf-graph-summary.v1` graph nodes with optional source-declared
  `componentRef` metadata;
- permit `componentRef` only for `node_type = "component-reference"`;
- validate every declared CAR/SAR identity against the selected generation's
  component-reference index;
- reject absent, ambiguous, mismatched, malformed, or wrong-node-type
  component references as deterministic build diagnostics;
- keep CAR/SAR nodes existence-only and leave capability, dependency,
  compatibility, operation, and usage detail to Textus CBD Support;
- provide Textus BoK Knowledge Map with a portable, source-attributed handoff
  payload without browser-side CBD queries or label/id inference.

Primary reference:

- `docs/phase/phase-22.md`
- `docs/phase/phase-22-checklist.md`
- `docs/journal/2026/07/bok-knowledge-map-component-handoff-2026-07-23.md`

### Phase 23: Scalar Entity Persistence Round-trip

Status: closed.

Purpose:

- reproduce the generated scalar restoration failure and identify whether the
  cause is generator behavior, dependency-version skew, model-kind selection,
  or optional/update datastore shape;
- make generated nominal scalar `DATATYPE` values preserve validation while
  round-tripping through Entity create, update/upsert, and fresh load;
- preserve required and optional field shapes and reject malformed scalar data
  deterministically;
- keep structured `VALUE` and multi-field `DATATYPE` record behavior distinct;
- verify the corrected contract through User Account, User Notification, and
  CBD Support;
- return CBD Support P8-42 to the Entity Aggregate boundary without its
  temporary private persistence codec.

Primary reference:

- `docs/phase/phase-23.md`
- `docs/phase/phase-23-checklist.md`
- `docs/notes/scalar-entity-persistence-roundtrip-implementation-proposal.md`

### Phase 24: Component Skill Distribution

Status: planned after Phase 23 and CNCF Phase 66 CAR Skill Bundle Contract
closure.

Purpose:

- define one CNCF-owned `SkillBundleManifest` for component Codex skills;
- have Cozy validate source and package the declared bundle into a CAR;
- have CNCF Launcher install and diagnose an unreleased development bundle;
- have Textus Launcher install, update, and remove the equivalent bundle from
  a released/local/cache CAR;
- keep installation explicit, staged, non-destructive, and separate from CAR
  execution or MCP invocation;
- keep Cozy Launcher responsible only for selecting and invoking the Cozy
  runtime, not for installing component skills.
- consume CNCF Phase 66 as the sole `SK24-01` contract supplier; after its
  item-by-item evidence handoff, begin Cozy-owned CAR projection at `SK24-02`
  without redefining the CNCF schema, codec, or validation semantics.

Primary reference:

- `docs/phase/phase-24.md`
- `docs/phase/phase-24-checklist.md`
- CNCF repository `docs/phase/phase-66.md`
- CNCF repository `docs/phase/phase-66-checklist.md`

### Phase 25: Entity Revision Generator Alignment

Status: closed. CBD Support runtime-selection acceptance is separately tracked
as Phase 8 `P8-61`.

Purpose:

- align generated SimpleEntity CRUD source with CNCF's canonical embedded,
  framework-managed revision contract after the retired token API removal;
- remove obsolete generated `cncfRevision` request transport and let the
  Entity/UnitOfWork boundary manage revision lifecycle and concurrency;
- retain the corrected Cozy development `0.3.0-SNAPSHOT` source contract and
  transfer driver-CAR regeneration and SQLite-backed persistence acceptance to
  CBD Support when its launcher selects a different effective runtime;
- keep this dependency repair separate from Phase 24 Component Skill
  Distribution.

Primary reference:

- `docs/phase/phase-25.md`
- `docs/phase/phase-25-checklist.md`
- `docs/journal/2026/07/entity-revision-generator-alignment-handoff-2026-07-26.md`

### Phase 26: Article Media Publication and BoK Integration

Status: closed. The SmartDox dependency was satisfied by accepted
closed Phase 1 commit `fa21316973416c24bca7f8e366d65572c72720b7` and the
development integration coordinate `org.smartdox:smartdox_2.12:2.4.17-SNAPSHOT`.
Public/non-SNAPSHOT publication is not a Phase 26 start gate.

Purpose:

- consume SmartDox's provider-neutral article-media publication contract in a
  Cozy BoK build;
- keep the accepted SmartDox record surface separate from Cozy-owned
  association, integrity, artifact version, hash, and provenance records;
- register locale-specific detailed infographics and internally hosted video
  presentations against stable article identities;
- keep generated MP4, captions, transcripts, and repository-resident media
  outside Git while retaining their paths, hashes, versions, and provenance in
  the BoK publication registry;
- pass only registered metadata and configured repository context to SmartDox;
- stage and publish the BoK site plus artifact repository as one public URL
  space; and
- preserve existing `.video` publication and ordinary `bok build` behavior.

Primary reference:

- `docs/phase/phase-26.md`
- `docs/phase/phase-26-checklist.md`
- `docs/design/article-media-publication.md`
- `docs/spec/article-media-publication.md`
- `docs/notes/article-media-publication-bok-integration.md`
- `docs/journal/2026/08/article-media-publication-bok-integration-handoff-2026-08-03.md`

### Phase 27: SmartDox Site Media Registration

Status: closed. AM27-00 through AM27-04 are complete, and Part 5
normal-package acceptance passed.

Purpose:

- register infographic and accepted externally hosted video media from a
  normal Cozy media package into the SmartDox site registry for the special
  `simplemodeling.org` BoK, whose site is built directly by SmartDox rather
  than through Cozy BoK build;
- keep ordinary article registration/discovery in SmartDox while requiring
  skills and Cozy video publication to use supported product commands for
  media registration;
- keep SmartDox responsible for the provider-neutral article-media
  publication schema/model, validation, and site projection, while Cozy owns
  normal-media-package orchestration because it knows `media.yaml` resources,
  public paths, and accepted video evidence;
- require explicit article identity and exact locale, with deterministic
  owner-bundle role/locale merge, lock/preflight/atomic replacement, and
  preservation of unrelated entries; and
- drive the end-to-end acceptance from Part 5, with Part 4 retained for
  incident evidence only.

The normative AM27-00 contract is `cozy media register-site <media-file>
--publication <dir> [--target <resource-id>] [--dry-run]`, with explicit
descriptor `articleMedia.articleIdentity`, `publicationProfile`, and selected
resource association. It requires exact-locale deterministic owner-bundle
merge, complete preflight, one real-root lock, and atomic replacement. SmartDox
records remain provider-neutral and contain no Cozy internals. The phase
excludes `cozy bok publish-media`, BoK scan/build/repository/staging
dependencies, arbitrary target scans, direct skill/manual registry edits, media
generation/upload/site deployment, coupled deployment, and implicit host
fallback.

Primary references:

- `docs/phase/phase-27.md`
- `docs/phase/phase-27-checklist.md`
- `docs/phase/phase-26.md`
- `docs/phase/phase-26-checklist.md`
- `docs/design/article-media-publication.md`
- `docs/spec/article-media-publication.md`
- `docs/design/smartdox-site-media-registration.md`
- `docs/spec/smartdox-site-media-registration.md`

### Phase 28: Project Configuration and Profile Resolution

Status: closed. AM28-00 and AM28-01 are complete.

Purpose and boundaries:

- freeze the special `simplemodeling.org` `smartdox-site` boundary without
  changing standard-BoK or Phase 27 production behavior;
- discover the nearest direct non-symlink project `conf/cozy/config.yaml`
  without Git and expose deterministic configuration provenance;
- separate publication workflow selection, media publication destinations,
  and audiovisual credit-profile selection;
- support shared project credit/publication profiles while retaining
  package-local, standalone, and explicit override compatibility; and
- require explicit top-level publication-profile selection, discovered
  project kind, and resource-level article-media opt-in.

The approved 2026-08-12 split kept the first delivery unit and source history
as Phase 28. Its conservative estimate was 4–6 hours at minimum xhigh effort.
The existing design/spec documents are the frozen AM28-00 authority, and
AM28-00/AM28-01 implementation review and completion evidence are complete;
the Phase ledger remains operational rather than normative.

Primary references:

- `docs/phase/phase-28.md`
- `docs/phase/phase-28-checklist.md`
- `docs/design/simplemodeling-org-wip-article-media.md`
- `docs/spec/simplemodeling-org-wip-article-media.md`

### Phase 28.1: WIP Local Article Media Registration

Status: closed. AM28-02 implementation, focused validation, fifth-article
JA/EN WIP acceptance, and independent review are complete.

Purpose and boundaries:

- stage normalized, hash-verified, exact-locale local MP4s beneath disposable
  `website.d` through a supported provider-neutral Cozy command;
- emit local WIP video and infographic records without spoofing published
  YouTube or production QA evidence;
- preserve unrelated registry data through preflight, drift detection,
  rollback, and atomic replacement;
- reject symlinks, broad roots, unsafe destinations, stale identities, locale
  fallback, and opposite-locale leakage; and
- perform no upload, network publication, deploy, direct registry edit, or
  standard-BoK rerouting.

This reusable capability is estimated at 4–6 hours at minimum xhigh effort.
SimpleModeling.org script integration and rendered-card acceptance are left to
Phase 28.2.

Primary references:

- `docs/phase/phase-28.1.md`
- `docs/phase/phase-28.1-checklist.md`
- `docs/phase/phase-28.md`

### Phase 28.2: SimpleModeling.org Part 5 Integration and Regression

Status: closed. This final split unit completed AM28-03 and AM28-04 with
accepted Part 5 runtime, full executable regression, independent review, and
ledger-convergence evidence.

Purpose and boundaries:

- add SimpleModeling.org root configuration, shared profiles, and explicit
  Part 5 article-media bindings using the completed Phase 28 contract;
- wire `etc/runweb-wip.sh` only through supported Cozy/Dox product commands;
- use `development-process/object-modeling` as the deterministic JA/EN test
  driver and render introduction cards linking each locale's infographic and
  playable local video beneath `website.d`;
- verify admitted-artifact hashes, exact-locale behavior, repeat generation,
  and no opposite-locale leakage even when Part 5 is outside five visible card
  slots; and
- regress Phase 27 external-YouTube production, package/standalone behavior,
  and standard-BoK repository/publication placement.

This final split unit is estimated at 3–5 hours at minimum high effort. Actual
Part 5 WIP requires no YouTube publication; production acceptance remains a
separate evidence-backed path and listening may remain pending as non-gating
evidence.

Primary references:

- `docs/phase/phase-28.2.md`
- `docs/phase/phase-28.2-checklist.md`
- `docs/phase/phase-28.1.md`
- `docs/design/smartdox-site-media-registration.md`
- `docs/spec/smartdox-site-media-registration.md`

### Phase 29: Project-Owned Site BoK Metadata Finalization

Status: closed.

Purpose and boundaries:

- add a public `cozy bok finalize-metadata` boundary for an already generated
  project-owned site;
- reuse Cozy's canonical KnowledgeSource, RDF graph-summary, and
  component-reference normalization without invoking a full `cozy bok build`;
- preserve project-owned SmartDox, Antora, Arcadia, media-registration, direct
  asset, upload, and deployment orchestration;
- make finalization failure-atomic, deterministic, and limited to an explicit
  machine-metadata mutation set; and
- use SimpleModeling.org WIP and production wrappers as the acceptance driver,
  proving that their special site processing remains intact while Textus BoK
  receives a complete compatible handoff.

This Phase is estimated at 4–6 hours at minimum high effort. It does not move
SimpleModeling.org site generation into Cozy and does not add new Textus BoK
resource schemas.

Primary references:

- `docs/phase/phase-29.md`
- `docs/phase/phase-29-checklist.md`
- `docs/phase/phase-14.md`
- `docs/phase/phase-22.md`
- `docs/phase/phase-28.2.md`

### Phase 34: BoK Metadata Input Admission Hardening

Status: closed.

Purpose and boundaries:

- admit configured BoK source paths only after canonical project-root and
  symlink-safe validation;
- validate glossary and component-reference resources unconditionally before
  they can be staged or declared by a KnowledgeSource manifest; and
- preserve Phase 29's public finalization surface, failure atomicity, metadata
  allowlist, and prohibition on site-build or project-workflow execution.

This Phase owns and addresses `CPB-29-01` and `CPB-29-02` from the Phase 29
full review. BOK34-01 and BOK34-02 were provisionally accepted in Cozy commits
`e1a6416457d5692513741f686c7e1a34a56d390e` and
`c464d7788e939d4987c79250c27df76ee40b626d`; normal closure repair
`CPB-BOK34-001` and exceptional closure repair `CB-P34-CFB2-001` were
provisionally accepted
with focused invocation `52873-20260824T043314Z` (`testOnly cozy.CozyBokSpec
cozy.CozyBokMetadataFinalizationSpec`, 16 succeeded/0 failed/0 aborted, SBT 0,
wrapper 0, lock released), and the fresh focused re-review returned
`FOCUSED_PASS` with no Current Boundary Blocker, Hygiene, or Development
Candidate finding. CFB2 permits a safely absent in-project source only during
configuration resolution; actual Build admission remains strict. The final
official full Cozy test wrapper invocation `58874-20260824T044406Z` then
reported 1,369 total, 1,364 succeeded, 5 failed, 8 canceled, 99 suites
completed, and 0 aborted; SBT and wrapper exit codes were 1 and the lock was
released. All 5 failures are `cozy.bok.CozyBokSpec` actual-Build scenarios
using the default `src/main/doxsite` without making its local fixture:
configured Textus image, Arcadia, production direct assets, unrelated
YAML/direct assets, and the default production RDF missing-artifact policy.
Strict actual-Build admission remains correct. CFB3 then added local
`src/main/doxsite` fixtures in all five scenarios without changing production
behavior or adding an external project dependency. Valid focused evidence is
invocation `85555-20260824T054332Z`, exact command
`testOnly cozy.bok.CozyBokSpec`, with 60 succeeded/0 failed/0 canceled, one
suite, SBT and wrapper 0, and lock released; the fresh focused re-review was
`FOCUSED_PASS` with no findings and preserved CFB2 safe absence at
configuration time plus strict actual-Build admission. The candidate final
full Cozy `test` receipt `22693-20260824T094231Z` reported 1,376 succeeded, 0
failed, 8 canceled, 100 suites, and 0 aborted; SBT and wrapper exit codes were
0 and the lock was released. Phase 34 is closed; no Phase 35 successor work is
started and no SmartDox/Textus consumer execution or acceptance is claimed.
It does not repeat SmartDox literal-label work or generated-site runtime
acceptance: SmartDox Phase 8 `LITERAL8-03` and Textus BoK consumer acceptance
remain separate and are not evidence this Phase performs or claims.

Primary references:

- `docs/phase/phase-34.md`
- `docs/phase/phase-34-checklist.md`
- `docs/phase/phase-29.md`

### Phase 35: BoK Executable-Spec Source Fixture Self-Containment

Status: superseded; not started.

Purpose and boundaries:

- preserve CFB2's configuration-time safe-absence admission and strict
  actual-Build source admission;
- make only actual-Build executable-spec source fixtures self-contained local
  safe sources, or revise the stated behavior after rules/spec/design work;
- specify the exact future behavior in rules, spec, and design before any
  implementation; and
- keep SmartDox/Textus consumer acceptance separate from this Cozy correction.

This former successor for the Phase 34 final full-suite executable-spec fixture
regression is superseded by CFB3, which resolved its proposed fixture scope in
P34. Its historical boundaries must not weaken lexical, canonical, symlink,
outside-root, or strict actual-Build rejection, and no downstream site-build,
external SimpleModeling.org source, or SmartDox workaround was used. All Phase
35 items remain unexecuted; no Phase 35 implementation is started by this
record.

Primary references:

- `docs/phase/phase-35.md`
- `docs/phase/phase-35-checklist.md`
- `docs/journal/2026/08/2026-08-24-phase-34-development-candidates.md`

### Phase 30: Unified Storyboard and Three-Gate Video Review Workflow

Status: COMPLETE; explicit single-Phase execution closed 2026-08-26.

Purpose and boundaries:

- replace source-managed dialogue `script.json` on the new path with one
  versioned Storyboard contract;
- use `storyboard.md` for human authoring and content review and
  `storyboard.json` for external machine input/output;
- parse both representations into one typed Cozy `Storyboard` with lossless,
  deterministic conversion and common validation;
- make `storyboard.md` the normal, required content-review gate;
- allow image-backed visual-story slides only when individual PNG frames,
  diagrams, layout, or image selection need separate inspection;
- keep PowerPoint generation in the Dox/presentation boundary while Cozy owns
  normalized review evidence, identities, and stale-input rejection;
- build a confirmation video from the approved Storyboard separately from the
  final video, with separate output identities and lifecycle state;
- reuse unchanged audio and render chunks when their scene inputs are
  unchanged, while retaining deterministic invalidation for changed inputs;
- build the final video only after the accepted confirmation video and
  production settings in `video.yaml`; and
- retain a separate final-review gate for the final MP4 and rendered-video
  evidence. A video-derived review PPTX is an explicit special-purpose output
  for distribution, meeting, handoff, or archive use only.

The required workflow is:

```text
storyboard.md review
  -> optional image-backed visual-story review
  -> confirmation video review
  -> final video and rendered-video evidence review
```

The Phase was estimated at 10–15 hours at recommended high effort. The user
explicitly authorized one long-running Phase without splitting it into child
phases. Storyboard schema/Markdown/JSON, optional visual-story review evidence,
and confirmation/final build plus final-review integration are internal Steps
`P30-01`, `P30-02`, and `P30-03`, preceded by authorization and specification/
design foundation Step `P30-00`. Encoding policy profiles remain Phase 31's
concern. This closed delivery shape does not authorize a successor Phase,
external repository mutation, or Dox/PPTX/SmartDox/Textus runtime acceptance.

Primary references:

- `docs/phase/phase-30.md`
- `docs/phase/phase-30-checklist.md`
- `docs/spec/video-storyboard.md`
- `docs/design/video-storyboard.md`
- `docs/journal/2026/08/2026-08-26-phase-30-single-phase-authorization.md`

### Phase 36: Common Visual Page and Cross-Media Presentation Contract

Status: IN PROGRESS; VIS36-01 and VIS36-02 are DONE; VIS36-03 is IN PROGRESS
with P36-03A accepted and the P36-03B/P36-03C Step accepted in local
acceptance commit `4d4d621fdbc1822233d49de6a618708567c02d8a`. Final-tree
focused validation invocation `51002-20260827T073710Z` passed 29/29 with 0
failures across 3 suites, and independent Step review passed with zero findings.
VIS36-04 remains IN PROGRESS after the P36-04 implementation record.
`P36-04-DEC-001` is consumed; focused validation invocation
`90284-20260827T085957Z` (`testOnly cozy.video.CozyVideoStoryboardSpec`) reported
16 succeeded, 0 failed/aborted, SBT/wrapper 0, and the lock released.
Independent `P36-04-REREVIEW-002` is PASS and `CB-P36-04-RR-001` is resolved.
P36-04's implementation/validation record is included in this local acceptance
Step commit. No push, publish, or publication is claimed. Phase 36 remains IN
PROGRESS because renderer scene identity binding and evidence identity/proof
remain open for P36-05+. The `P36-05-DEC-001` decision is consumed; P36-05
focused validation and independent Step re-review passed
(`54038-20260827T112424Z` ReviewSpec 12/12;
`54763-20260827T112533Z` accumulator 39/39); acceptance evidence is recorded
in this local Step commit. VIS36-05 and Phase 36 remain IN PROGRESS; Phase
closure remains open. `P36-06-DEC-001` admits a closed structural cross-media
evidence route; VIS36-06 structural validation and independent review passed
and are recorded in this local Step commit; Phase 37 remains NOT STARTED.

Purpose and boundaries:

- define one normalized Visual Page carrying a one-screen Presentation
  Semantics IR: a typed Logical Pattern, semantic node/Relation graph,
  separately selected Visual Pattern, parameters, assets, sources, language,
  and stable identity;
- use the same Visual Page contract for one article-summary presentation slide
  and the displayed screen of one video Storyboard scene;
- define a versioned Visual Pattern Catalog and profile/template binding while
  keeping coordinates, fonts, and renderer object identities outside the
  semantic IR;
- implement the P36-03B versioned business-binding contract as a strict
  direct-file JSON boundary: bind every resolved Visual Pattern exactly once,
  use the canonical `knowledge`, `nodes`, `relations`, `assets`, and
  `parameters` semantic slots, retain only opaque physical-slot tokens, and
  derive deterministic binding identity without invoking a renderer;
- implement P36-03C as a separately closed `visual-page-v1` presentation
  branch with fixed catalog/binding renderer argv, canonical v2 renderer and
  review evidence, and explicit existing receipt inputs while leaving the
  legacy Slide IR route unchanged;
- define versioned closed Logical Pattern and semantic Relation catalogs so
  visually identical arrows retain distinct `next`, `causes`, `depends-on`,
  `enables`, or `maps-to` meaning;
- allow multiple compatible Visual Patterns for one logical graph without
  changing its semantic identity, while keeping coordinates, fonts, colors,
  Shape kinds, and renderer object IDs below the Visual Pattern boundary;
- provide human-reviewable restricted Markdown and structured YAML/JSON as
  lossless serializations of the same normalized Visual Page identity;
- replace `cozy.slide-ir.v1` as the future semantic authority through an
  explicit, diagnostic compatibility or migration path rather than silent
  reinterpretation;
- keep narration, timing, transition, and audiovisual review in the video
  Storyboard workflow;
- bind Visual Page, catalog, parameters, binding, template, renderer, and
  selected assets into Media Package receipts, review state, and stale-input
  rejection; and
- keep PPTX, slide PNGs, video frames, montage, renderer manifests, and video
  outputs as generated delivery or review artifacts.

Phase 36 starts only after Phase 30 closes its active video Storyboard
contract. VIS36-01 and VIS36-02 are accepted as DONE, and VIS36-03 remains IN
PROGRESS with P36-03A accepted and the P36-03B/P36-03C Step accepted in local
acceptance commit `4d4d621fdbc1822233d49de6a618708567c02d8a`. Final-tree focused
validation invocation `51002-20260827T073710Z` passed 29/29 with 0 failures
across 3 suites, and independent Step review passed with zero findings. Video
integration and cross-media receipt/review acceptance remain open for later
work; VIS36-04 and Phase 36 remain IN PROGRESS because renderer scene identity
binding and evidence identity/proof remain open for P36-05+. The
`P36-05-DEC-001` decision is consumed; P36-05 focused validation and
independent Step re-review passed (`54038-20260827T112424Z` ReviewSpec 12/12;
`54763-20260827T112533Z` accumulator 39/39); acceptance evidence is recorded
in this local Step commit. VIS36-05 and Phase 36 remain IN PROGRESS; Phase
closure remains open. `P36-06-DEC-001` admits a closed structural cross-media
evidence route; VIS36-06 structural validation and independent review passed
and are recorded in this local Step commit; Phase 37 remains NOT STARTED. This status does not
expand Phase 30.
Subject/Explanation Pattern composition and multi-slide/multi-scene expansion
remain Phase 37 work.

Primary references:

- `docs/phase/phase-36.md`
- `docs/phase/phase-36-checklist.md`
- `docs/spec/media-package.md`
- `docs/design/media-package-operation.md`
- `docs/journal/2026/08/2026-08-26-presentation-semantics-ir-phase-36-37.md`

### Phase 37: Logical Explanation Composition and Media Projection

Status: planned; not started.

Purpose and boundaries:

- define Subject Pattern as the typed logical structure of what is explained
  and Explanation Pattern as the independently selected Narrative / Argument
  strategy by which it is developed for an audience and purpose;
- normalize one selected subject, explanation pattern, and typed parameter set
  into an ordered, medium-neutral Explanation Step plan containing typed
  Logical Patterns and semantic Relation graphs;
- project Explanation Steps independently into multiple Visual Pages for a
  presentation and multiple Storyboard scenes containing Visual Pages for
  video, without requiring equal slide and scene counts;
- keep subject analysis, pattern selection, and parameter authoring with humans
  or AI while Cozy owns deterministic validation, expansion, projection,
  identity, and stale-input rejection;
- admit `software-product` as the first representative Subject Pattern,
  `product-overview` with `vision`, `goal`, `context`, `use-case`, and
  `main-scenario` as the first representative Explanation Pattern, and
  `product-mechanism` as the relationship from goals/use cases to realization
  mechanisms; and
- preserve logical composition, Visual Page, Storyboard, PPTX, and MP4 as
  distinct authority or artifact layers;
- use the authority chain `Narrative / Argument Pattern -> Logical Pattern +
  Relation graph -> Visual Pattern -> renderer binding`, never deriving
  semantic meaning from PowerPoint arrows or Shapes;
- define a reusable Narrative / Argument catalog spanning problem/solution,
  current/target, observation/insight/implication, claim/evidence,
  why/what/how, input/process/output, strategy/execution/outcome, and related
  forms while keeping the Phase 37 acceptance subset bounded; and
- keep the IR renderer-neutral enough for later HTML, infographic, article
  figure, Mermaid/PlantUML, and SVG renderers without making them Phase 37
  acceptance requirements.

Phase 37 starts only after Phase 36 accepts the common Visual Page and
cross-media display contracts. It does not authorize implementation or alter
the active Phase 30 boundary.

Primary references:

- `docs/phase/phase-37.md`
- `docs/phase/phase-37-checklist.md`
- `docs/phase/phase-36.md`
- `docs/spec/media-package.md`
- `docs/design/media-package-operation.md`
- `docs/journal/2026/08/2026-08-26-presentation-semantics-ir-phase-36-37.md`

### Phase 31: Video Encoding Policy Profiles

Status: complete; VP31-01 through VP31-03 are complete.

Purpose and boundaries:

- establish lightweight 1280x720, 18 fps, CRF 32 output as the default Cozy
  video baseline;
- add `renderer.policy: lightweight|standard|quality` for coarse encoding
  intent while allowing explicit fields to override individual values;
- separate encoding policy from composition strategy, renderer engine,
  visual-effect profile, and narration configuration;
- apply effective CRF and x264 preset to Remotion execution and expose all
  resolved settings consistently through inspect, manifests, RDF, and review
  evidence; and
- verify output dimensions, frame rate, file size, and visual readability with
  representative character-dialogue runtime evidence.

This Phase is estimated at 4-6 hours at minimum high effort. It does not change
video semantics, narration, scene timing, publication, or the Phase 30
Storyboard authority model.

Primary references:

- `docs/phase/phase-31.md`
- `docs/phase/phase-31-checklist.md`

### Phase 33: Declared Cozy Runtime Selection for CAR Publication

Status: completed.

Purpose:

- make the CAR-owned `project.yaml build.cozyVersion` select the executing
  Cozy runtime for `publish`, `publishLocal`, `cozyPublishCar`, and
  `cozyPublishLocalCar`;
- remove the operational requirement to add a temporary `.cozy` runtime
  setting before CAR publication;
- preserve `.cozy` for optional non-runtime local configuration rather than
  treating it as CAR publication authority;
- retain fail-fast descriptor and generation-provenance checks when the
  declared runtime cannot be selected; and
- establish focused Cozy Launcher / sbt-cozy acceptance for development and
  release CAR publication without altering archive or catalog semantics.

Primary references:

- `docs/phase/phase-33.md`
- `docs/phase/phase-33-checklist.md`
- `docs/journal/2026/08/entity-revision-generator-downstream-acceptance-transfer-2026-08-03.md`

### Future Follow-up: Explicit Component Root Generation

Status: planned, low priority.

Purpose:

- treat one explicit CML `COMPONENT` definition as sufficient input for
  `modeler-scala` to create the corresponding generated component root;
- preserve the declared component name, package, and component identity even
  when the model contains no Entity, Value, Datatype, Powertype, StateMachine,
  Service, or Operation;
- keep `COMPONENTLET` optional and independent so neither a participant
  declaration nor a synthetic domain declaration is required to trigger
  component generation;
- emit the generated component type and factory-facing contract
  deterministically for a component-only model;
- prove the behavior with Executable Specifications covering minimal input,
  cold and repeated generation, generated artifact identity, compilation of a
  factory using the generated root, and generation-provenance validation;
- preserve `modeler-scala-value` as the value-only generation path and do not
  introduce an implicit component root there.

Origin:

- `docs/journal/2026/07/spi-only-component-generation-handoff-2026-07-28.md`

## 9. Development Item Status

| ID | Source | Development item | Disposition | Target | Status |
| --- | --- | --- | --- | --- | --- |
| DEV-001 | `docs/journal/2026/08/2026-08-19-cncf-compatibility-naming-hygiene-follow-up.md` (`HYG-P57.4-001`) | Make `Resolved.projectRelativePath` the canonical CML source-result field, retiring the former source-result name without a deprecated accessor, constructor, or named-argument compatibility alias. | NEW_PHASE | [Phase 32](../phase/phase-32.md) | RESOLVED |
| DEV-002 | `docs/journal/2026/04/cml-operation-design-note.md` (Future Work) | Decide the action hierarchy, result type, async/job model, and CLI/OpenAPI mapping as one future CML operation design boundary. | STRATEGY_ITEM | Strategy section 9 | CANDIDATE |
| DEV-003 | User-reported CAR publication runtime-selection defect, corroborated by `docs/journal/2026/08/entity-revision-generator-downstream-acceptance-transfer-2026-08-03.md` | Make CAR publish paths select `project.yaml build.cozyVersion` without a temporary `.cozy` runtime override. | NEW_PHASE | [Phase 33](../phase/phase-33.md) | RESOLVED |
| DEV-004 | Phase 29 full review `CPB-29-01` / `CPB-29-02` | Canonically admit configured BoK source paths and unconditionally validate glossary/component-reference resources before manifest publication. | NEW_PHASE | [Phase 34](../phase/phase-34.md) | RESOLVED |
| DEV-005 | Phase 34 final official full test `58874-20260824T044406Z`; resolved by CFB3 | Make `cozy.bok.CozyBokSpec` actual-Build source fixtures self-contained local safe sources while preserving CFB2 configuration-time safe absence and strict actual-Build admission; specify any behavior change first. | NEW_PHASE | [Phase 34](../phase/phase-34.md) | RESOLVED |
| DEV-006 | User request on 2026-08-26 following the article-summary media workflow review | Establish a common Visual Page contract carrying a typed Logical Pattern and semantic Relation graph, projected separately through Visual Patterns into presentation slides and video Storyboard scene screens with deterministic renderer binding and receipt identity. This supersedes the earlier Markdown-only Slide IR successor proposal. | NEW_PHASE | [Phase 36](../phase/phase-36.md) | IN PROGRESS |
| DEV-007 | User request on 2026-08-26 following the ACE product-explanation review | Separate Subject Pattern from Explanation Pattern and deterministically project one logical explanation composition into independent multi-slide and multi-scene sequences through the common Visual Page contract. | NEW_PHASE | [Phase 37](../phase/phase-37.md) | PLANNED |

## Current Priority

Phase 30 is closed. Phase 36 VIS36-01 and VIS36-02 are DONE; VIS36-03 remains
IN PROGRESS with P36-03A accepted and the P36-03B/P36-03C Step accepted in
local acceptance commit `4d4d621fdbc1822233d49de6a618708567c02d8a`. Final-tree
focused validation invocation `51002-20260827T073710Z` passed 29/29 with 0
failures across 3 suites, and independent Step review passed with zero findings.
Video integration and cross-media receipt/review acceptance remain open for
later work; VIS36-04 and Phase 36 remain IN PROGRESS because renderer scene
identity binding and evidence identity/proof remain open for P36-05+. The
`P36-05-DEC-001` decision is consumed; P36-05 focused validation and
independent Step re-review passed (`54038-20260827T112424Z` ReviewSpec 12/12;
`54763-20260827T112533Z` accumulator 39/39); acceptance evidence is recorded
in this local Step commit. VIS36-05 and Phase 36 remain IN PROGRESS; Phase
closure remains open. `P36-06-DEC-001` admits a closed structural cross-media
evidence route; VIS36-06 structural validation and independent review passed
and are recorded in this local Step commit; Phase 37 remains NOT STARTED. Phase 37 remains excluded and not started; it is the
planned logical explanation-composition successor after Phase 36 closes.
Neither successor adds work to the closed Phase 30 boundary or claims
validation, review, publication, or external consumer mutation.

Phase 34 is closed after the candidate final official full Cozy validation.
The earlier invocation
`58874-20260824T044406Z` reported 1,369 total, 1,364 succeeded, 5 failed, 8
canceled, 99 suites completed, and 0 aborted; SBT and wrapper exit codes were 1
and the lock was released. All 5 failures are actual-Build
`cozy.bok.CozyBokSpec` scenarios whose default `src/main/doxsite` fixture was
not created locally; CFB3 subsequently added those five local fixtures without
changing production behavior or adding an external project dependency. Its
valid focused invocation `85555-20260824T054332Z` (`testOnly
cozy.bok.CozyBokSpec`) reported 60 succeeded/0 failed/0 canceled, one suite,
SBT and wrapper 0, lock released, and fresh focused re-review `FOCUSED_PASS`
with no findings. The earlier failure is superseded; no new full-suite test has
has been superseded by CFB3 and the candidate final receipt
`22693-20260824T094231Z` (`test`, 1,376 succeeded/0 failed/8 canceled, 100
suites, 0 aborted, SBT/wrapper 0, lock released). Strict actual-Build admission
remains correct. No Phase 35 implementation is started here. Phase 36
VIS36-01 and VIS36-02 are DONE; VIS36-03 remains IN PROGRESS with P36-03A
accepted and the P36-03B/P36-03C Step accepted in local acceptance commit
`4d4d621fdbc1822233d49de6a618708567c02d8a`. Final-tree focused validation
invocation `51002-20260827T073710Z` passed 29/29 with 0 failures across 3
suites, and independent Step review passed with zero findings. Video
integration and cross-media receipt/review acceptance remain open for later
work; VIS36-04 and Phase 36 remain IN PROGRESS because renderer scene identity
binding and evidence identity/proof remain open for P36-05+. The
`P36-05-DEC-001` decision is consumed; P36-05 focused validation and
independent Step re-review passed (`54038-20260827T112424Z` ReviewSpec 12/12;
`54763-20260827T112533Z` accumulator 39/39); acceptance evidence is recorded
in this local Step commit. VIS36-05 and Phase 36 remain IN PROGRESS; Phase
closure remains open. `P36-06-DEC-001` admits a closed structural cross-media
evidence route; VIS36-06 structural validation and independent review passed
and are recorded in this local Step commit; Phase 37 remains NOT STARTED. Phase 37 remains excluded and not started as its planned logical
explanation-composition successor. No SmartDox/Textus
consumer execution or acceptance is claimed.
SmartDox Phase 8
`LITERAL8-03` owns regenerated-site runtime
finalization, metadata-only inventory/hash evidence, and Textus BoK consumer
acceptance; those downstream checks remain separate and are not evidence this
Phase performs or claims.

Phase 29 is closed with the public `cozy bok finalize-metadata` boundary,
canonical metadata finalization, executable specifications, and static
SimpleModeling.org wrapper integration accepted. Its transferred correction
items are resolved in closed Phase 34 without changing the Phase 29
no-site-build or no-SmartDox-workaround boundary.

Phase 6 through Phase 23 are closed. Phase 23 completed scalar Entity
persistence round-trip, driver-CAR verification, and the CBD Support P8-42
Entity Aggregate handback. Phase 24 remains separately planned and blocked on
CNCF Phase 66 closure and its `SK24-01` evidence handoff. Phase 25 is a separate,
focused generator alignment now closed with the Cozy generator contract and
Executable Specifications complete. The attempted CBD Support acceptance was
blocked by that project's launcher selecting Cozy `0.3.1-SNAPSHOT`; its
deterministic runtime selection, regeneration, and persistence acceptance are
explicit Phase 8 `P8-61` work. Phase 26 is closed with its BoK article-media
publication, build handoff, and staged bilingual acceptance complete. Its
SmartDox dependency was satisfied by accepted closed
Phase 1 commit `fa21316973416c24bca7f8e366d65572c72720b7` through development
coordinate `org.smartdox:smartdox_2.12:2.4.17-SNAPSHOT`; public/non-SNAPSHOT
publication is not a start gate. It does not require a video binary to enter
Git or make ordinary `bok build` generate media. Phase 27 is closed with
normal-package site registration, skill integration, and Part 5 exact-locale
panel acceptance complete. Phase 28 is closed with AM28-00/AM28-01
implementation, review, and completion evidence complete. Phase 28.1 is closed
with AM28-02 WIP staging/registration, fifth-article JA/EN artifact acceptance,
and independent review complete. Phase 28.2 is closed. AM28-03 is complete in
SimpleModeling.org commit
`fd460312d992b15086225341360e7586a9acbe35`, with exact-locale local-media
cards, deterministic repeat output, and a clean focused re-review. AM28-04
completed standard-BoK, package/standalone, and Phase 27 production regression,
the 95-suite/1300-test Cozy full gate, final Part 5 runtime acceptance,
independent clean review, and ledger convergence. Phase 24
remains separately planned and blocked on CNCF Phase 66 closure and the Cozy
`SK24-01` evidence handoff, preserving the closed Phase 27 production boundary.

Phase 20 introduced provider-neutral narration with VOICEVOX, host-only macOS
`say`, and a portable Piper Docker route, then used that contract to migrate the
Japanese and English SimpleModeling.org Overview videos to a Cozy-only command
workflow. Cozy owns the common synthesis outputs and provenance, while
`textus-toolchain-runner` owns the pinned portable TTS runtime and
license-audited model files. Generic credit profiles resolve character-material
usage and actual audio-manifest provenance into one effective credit set for
both the in-video page and publication-ready Markdown. User-level profile
selection keeps recurring policy out of individual video projects.

Phase 19 added portable knowledge-centered Media Packages and the `cozy media`
inspect, plan, build, verify, and publish workflow. Publication profiles project
one package into a site or external artifact archive without embedding
machine-specific destinations. AI-assisted skills own thesis extraction,
bilingual writing, localization, and visual review; Cozy owns deterministic
conversion, validation, copying, and hashes.

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
- Explicit component root generation from a CML `COMPONENT` definition without
  requiring Entity, Value, Datatype, Service, Operation, or `COMPONENTLET`
- Video publication registration follow-up, if richer article/navigation
  integration is needed
- Knowledge source compiler expansion
- Model-driven CAR project scaffolding
