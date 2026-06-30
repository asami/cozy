# Cozy Development Strategy

Date: 2026-05-12

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
- use the standard Cozy toolchain Docker image for Antora-based HTML generation

Primary reference:

- `docs/phase/phase-7.md`
- `docs/phase/phase-7-checklist.md`

### Phase 8: Video Knowledge Pipeline

Status: completed.

Purpose:

- make scripted video production a first-class Cozy workflow
- transform recorded demos into transcript, caption, RDF, and Playwright
  replay assets
- formalize and validate the unified Cozy toolchain Docker image used by BoK,
  SmartDox PDF, and video workflows
- use the Cozy toolchain Docker image for BoK HTML generation, SmartDox PDF
  rendering, video capture, encoding, Node, Remotion, Playwright,
  ffmpeg/ffprobe, whisper.cpp, transcription model/data, and related heavy
  dependencies while keeping Cozy responsible for orchestration and knowledge
  outputs
- own the unified `ghcr.io/asami/cozy-toolchain` image in the Cozy repository,
  using the current SmartDox PDF dependency image line as the baseline
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

- use `/Users/asami/src/Project2026/bok-knowlegehub` as a real BoK operation
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

Status: active.

Purpose:

- make tags a first-class BoK navigation surface
- keep tags distinct from categories and glossary terms
- connect articles, terms, scenarios, projects, bibliography entries, history,
  and RDF resources through lightweight cross-cutting labels
- render tag dashboards, tag hub pages, and tag-aware RDF navigation
- preserve empty-tag fallback behavior for existing BoK projects

Primary reference:

- `docs/phase/phase-13.md`
- `docs/phase/phase-13-checklist.md`

### Phase 14: SIE Integration

Status: planned.

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

### Future Phase: Model-Driven CAR Project Scaffolding

Purpose:

- make `car-sbt-project` scaffold generation model-driven instead of
  Notice-template-driven
- connect scaffold options to generated CML, factory overrides, and web metadata

## Current Priority

Phase 6, Phase 7, Phase 8, Phase 9, Phase 10, Phase 11, and Phase 12 are
closed. Phase 13 is active. Phase 14 is planned.

Phase 8 completed the first-class `cozy video` workflow: structured
project/script parsing, inspect, dry-run artifact/command planning, dependency
checks, Docker-first toolchain mode, unified Cozy toolchain image definition,
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
was `/Users/asami/src/Project2026/bok-knowlegehub`, and the phase closed after
production build, CloudFront serving, upload workflow, and ignored generated
state were operationally verified.

Phase 13 implements BoK tag knowledge navigation. Tags are lightweight
cross-cutting labels that connect articles, terms, scenarios, projects,
bibliography entries, history, and RDF resources without replacing categories
or glossary terms.

Phase 14 will integrate SIE with Cozy BoK and CAR/SAR publication workflows.
The first concrete contract is
`metadata/cncf/knowledge-source.json`, which lets SIE ingest generated BoK
sites through metadata resources instead of rendered HTML. The boundary is
explicit: Cozy publishes and validates BoK/SIE metadata for navigation and
diagnostics, while SIE remains responsible for semantic integration runtime
behavior and Information-schema materialization.

Candidate directions after Phase 13 include:

- SIE integration
- BoK publication and Component Repository public pages
- Video publication registration follow-up, if richer article/navigation
  integration is needed
- Knowledge source compiler expansion
- Model-driven CAR project scaffolding
