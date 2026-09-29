# Phase 71 Selected Production Contract

Status: frozen P710-01 handoff; implementation and real production pending

Date: 2026-09-29

This document records the selected private production contract from the
P710-01 graph investigation. It preserves the original declaration facts and
history while separating them from the approved private contract. It does not
claim that the original descriptors already form this graph, and it does not
accept a generated video.

## Exact request and scope

| Field | Frozen value |
| --- | --- |
| Project | `src/main/doxsite/development-process/ai-development-harness.dox` |
| Locale | `ja` |
| Selected product | `article-video-ja` |
| Media profile | `explanation` |
| Direct Document descriptor profile | `bok-video` |
| Task-private root | `target/phase71-private-driver/simplemodeling-org` |
| Authority | `P71-REAL-GRAPH-001`, approved for the task-private SimpleModeling.org copy only |
| Producer ownership | Codex authors semantic sources; Cozy validates, plans, and invokes declared native operations; external tools/providers remain prerequisites |

The private copy is an acceptance driver. The SimpleModeling.org production
source, media, scripts, and history remain unchanged and outside this contract.
The selected profile scopes are distinct: `explanation` is the media profile
and `bok-video` is the direct Document descriptor profile.

## Original declaration facts

The following are observations of the existing sources, not assertions that
they satisfy the approved graph without repair.

| Existing source | Observed fact |
| --- | --- |
| `src/main/doxsite/development-process/ai-development-harness.dox/document-project.yaml` | `contentCore` selects `content/core-ja.yaml` under `cozy.document-project.v2`; that legacy Core is not the locale-independent Logic Tree selected by this request. |
| `src/main/doxsite/development-process/ai-development-harness.dox/content/core.yaml` | Existing `cozy.content-core.logic-tree.v1`, id `ai-development-harness`, was read as a complete meaning source; nested `harness-concept` and `essence-framework-pointer` remain part of it. |
| `src/main/doxsite/development-process/ai-development-harness.dox/content/ja/document.yaml` | Existing `cozy.document-description.v2` is bound to Core id `ai-development-harness` and is the prose authority. Its legacy identity field requires native reconciliation; existence is not new-validation proof. |
| `src/main/doxsite/development-process/ai-development-harness.dox/content/ja/summary.yaml` | Existing `cozy.summary-description.v2` declares Core `ai-development-harness` and Document `ai-development-harness-document-ja`; it is deliberate concise authority, not an existence-based generation proof. |
| `src/main/media/development-process/ai-development-harness/media.yaml` | `article-video-ja` selects the `video-project` producer, `video/ja/video.yaml`, `video/ja/script.json`, and `video/ja/build/ai-development-harness-ja.mp4`. |
| `src/main/media/development-process/ai-development-harness/video/ja/video.yaml` | The existing declaration is a `dialogue` part backed by `script.json`, with Remotion 1280x720, root fps 18, VoiceVox endpoint, Docker image, and no Storyboard source declaration. |
| `src/main/media/development-process/ai-development-harness/video/ja/storyboard.md` | The existing `cozy.video.storyboard.v1` contains the actual nine-scene explanation Storyboard, not the Document Project placeholder. Its role-string asset references must be reconciled to real project-relative files. |

These facts preserve the legacy sources and the original source history. The
approved private contract below is the selected repair direction, not a claim
that the legacy `core-ja.yaml` or twenty-scene `script.json` is authoritative.

## Approved private graph

Every node has an explicit producer, direct inputs, scope, output boundary, and
admission condition. The graph is executed in the following order, with shared
nodes deduplicated:

`core -> document-ja -> summary-ja -> video-declaration -> assets and tools -> storyboard-ja -> native-storyboard-plan -> narration-and-part-video -> final-video`

| Node | Producer and ownership | Direct inputs | Output | Scope and admission |
| --- | --- | --- | --- | --- |
| `core` | Codex source-authoring; authorized logical meaning | none | `src/main/doxsite/development-process/ai-development-harness.dox/content/core.yaml` | Locale-independent; existing Logic Tree grammar and declared IDs/references, with no missing or invented meaning. |
| `document-ja` | Codex source-authoring | `core` | `src/main/doxsite/development-process/ai-development-harness.dox/content/ja/document.yaml` | `ja`; preserve accepted prose and Core correspondence, reconciling only immediately required native validators. |
| `summary-ja` | Codex source-authoring | `core`, `document-ja` | `src/main/doxsite/development-process/ai-development-harness.dox/content/ja/summary.yaml` | `ja`; deliberate concise content with valid Core and Document references. |
| `storyboard-ja` | Codex source-authoring | `core`, `document-ja`, `summary-ja`, `video-declaration`, `assets` | `src/main/media/development-process/ai-development-harness/video/ja/storyboard.md` | `ja/explanation`; one nine-scene v1 authority, preserving content while repairing `assetRefs` to real files; never project the old dialogue script. |
| `video-declaration` | Parent-approved private declaration repair; P710-02 owns execution | none | `src/main/media/development-process/ai-development-harness/video/ja/video.yaml` | `ja/explanation`; native `storyboard` type, `storyboard=storyboard.md`, no `script` field, preserved output/renderer/tool/credit configuration, and a declared existing `section-start` asset. |
| `assets` | Existing explicitly supplied project assets | none | The four asset files listed below | `ja`; safe readable paths with existing license/provenance, not fabricated Core-derived assets. |
| `tools-and-project-context` | Existing external/runtime prerequisites | none | Configured Remotion, ffmpeg, ffprobe, VoiceVox/provider, build configuration, and credits | Selected route only; exact prerequisites must be checked before synthesis. P710-F03 proves readiness inspection only, not provider or renderer success. |
| `native-storyboard-plan` | `CozyVideoPlanning._part_plan` / `_load_storyboard_script` | `storyboard-ja`, `video-declaration`, `assets`, `tools-and-project-context` | One validated `VideoPlan` with nine source-derived `VideoScenes` | `ja/explanation`; native path confinement/no-symlink, v1 schema/content, section selection, voice notes, and actual required files. |
| `narration-and-part-video` | Existing native synthesis and Remotion part rendering | `native-storyboard-plan` | Declared part audio and part MP4 | Actual successful processes and valid audio/video; no approval/hash prerequisite after P710-02 implementation. |
| `final-video` | Existing native project build, ffmpeg, and ffprobe | `narration-and-part-video`, `native-storyboard-plan` | `src/main/media/development-process/ai-development-harness/video/ja/build/ai-development-harness-ja.mp4` | `ja/explanation`; explicit `--mode final`, valid real MP4, and actual successful process evidence. Confirmation remains separately selectable and is never a final prerequisite. |

The direct declared edges are therefore explicit: Core to the localized
Document, Core/Document to Summary, all authored sources and declared assets to
the Storyboard, the Storyboard declaration and runtime context to the native
plan, and the plan to part and final media. No edge is inferred from a filename,
prose similarity, an existing output, or a reconciliation obligation.

## Authoritative scene grounding

The selected Storyboard is grounded against the existing Core meaning as
follows. This is an investigation mapping for authoring and validation, not a
serialized binding or execution result.

| Scene | Core source nodes | Core-derived explanation anchors |
| --- | --- | --- |
| `opening` | `ai-development-harness` | `episode-position`, `series-program` |
| `speed` | `background` | `ai-driven-development-speed`, `candidate-generation-advantage` |
| `business-wall` | `business-application-barrier` | `business-application-assurance` |
| `convergence` | `problem-definition` | `three-practical-concerns`, `cost-to-convergence` |
| `harness-definition` | `series-declaration`, `harness-concept` | `harness-as-countermeasure`, `harness-uses-software-engineering-assets` |
| `broad-concept` | `harness-concept` | `harness-broader-than-ai-skill-set`, `harnesses-across-development`, `combined-harness-platform` |
| `responsibility-boundary` | `series-declaration` | `interaction-execution-separation`, `architecture-principle` |
| `technology-context` | `shared-context` | `simplemodeling-context`, `cml-context`, `cozy-context`, `textus-context` |
| `series-roadmap` | `series-structure` | `harness-system`, `series-boundary` |

## Actual supplied assets

The contract admits these four existing files and no role-token substitute:

- `src/main/media/development-process/ai-development-harness/video/ja/assets/opening.svg`
- `src/main/media/development-process/ai-development-harness/video/ja/assets/section-start.svg`
- `src/main/media/development-process/ai-development-harness/video/ja/assets/summary.svg`
- `src/main/media/development-process/ai-development-harness/video/ja/assets/final-page.svg`

The native Storyboard build resolves each asset reference as a project-relative
file path. The original `opening`, `section-start`, `summary`, and `final-page`
role strings are not themselves file paths, and their presence does not prove a
valid asset admission.

## Authorized private repairs and pending production

The parent-approved private repair direction authorizes P710-02 to reconcile
the selected private copy as one coherent bootstrap:

1. change the private video declaration to the native Storyboard source,
   preserve its selected output, renderer, tools, and credits, remove the
   obsolete `script` selection, and declare the existing `section-start` asset;
2. repair Storyboard `assetRefs` to the four actual project-relative files
   while preserving the nine-scene content;
3. remove the Storyboard/visual/confirmation approval prerequisites and their
   obsolete hash fields/callers only where required for this native bootstrap;
4. run the real private driver for baseline generation, Core propagation,
   intermediate propagation, Storyboard-only change, all-current reuse, and
   missing-prerequisite failure with prior-output preservation.

Those repairs and all actual video proof remain pending P710-02. P710-01
freezes the request, graph, ownership, and currentness handoff; it does not
edit the external project, invoke a renderer, claim a product result, or close
Phase 71.

## Boundaries and excluded substitutions

The twenty-scene `video/ja/script.json`, legacy `content/core-ja.yaml`,
Document Project placeholder Storyboard, unselected language/assets, and
article/PDF/infographic/site products are not substitutes for this selected
contract. Confirmation is optional and separately selectable. Publication,
upload, display, deployment, a new dispatcher/renderer, and a speculative
metadata or receipt framework are outside this handoff. The original sources,
meaning, and production history remain preserved.
