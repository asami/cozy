# Profile-Driven Video Scaffold

`cozy video scaffold` creates a Git-managed `.video` source package. The
package selects a composition profile, independent visual-effect profiles, and
project-local visual assets. Generated media under `build/`, `target/`, and
publication repositories remains outside the authored source contract.

## Create a Package

Create a short explanation:

```console
cozy video scaffold domain-overview \
  --profile=explanation \
  --save=src/main/doxsite/technology/domain-overview.video
```

Create an explanation, web demonstration, and concluding explanation:

```console
cozy video scaffold product-demo \
  --profile=explanation-demo-explanation \
  --save=src/main/doxsite/technology/product-demo.video
```

The second profile creates introduction and conclusion scripts plus a demo
script and a manual-review `demo-steps.json` draft. Complete the narration and
validate the browser steps before recording or publication.

For `explanation-demo-explanation`, the demonstration part uses the deterministic
recording directory `build/record/demonstration`. Record a reviewed browser demo
into that directory before running `cozy video render`; it must contain exactly
one directly contained `.webm` or `.mp4` recording. Dox and video-generation
skills may produce the reviewed recording and populate caller-owned character,
voice, placement, license, and credit declarations.

Recording directories and declared character or scene assets must use relative,
non-traversing paths whose resolved real paths remain inside the video project.
Cozy rejects absolute paths, `..` traversal, and symbolic-link escapes before
copying media into a renderer workspace.

Cozy supplies and bundles no Reimu, Marisa, Zundamon, or any other character
defaults or assets. Character identity, images, voices, placement, rights, and
credits are supplied only by caller tooling and its media package.

## Shared Pronunciations

Cozy applies the bundled UTF-8 dictionary
`cozy/video/pronunciations.properties` immediately before sending narration
text to VOICEVOX. The initial shared readings are:

```properties
値=あたい
BoK=ボック
```

The replacement affects synthesized speech only. Authored script text,
captions, and generated metadata retain their original spelling. A script can
override or extend the shared dictionary with its existing `pronunciations`
map; a script entry wins when it uses the same source spelling. Literal
replacement is case-sensitive and longer source spellings are applied first.
Cozy matches the original text in one pass, so a generated reading is not
processed again as another dictionary source.

## Narration Provider And Execution

Select narration independently from the renderer and general tool execution:

```yaml
narration:
  provider: voicevox
tools:
  toolMode: docker
  dockerImage: ghcr.io/asami/textus-toolchain:latest
  voicevoxUrl: http://127.0.0.1:50021
```

`narration.provider` is the canonical provider setting. Existing scripts without
it continue to use `voicevox`. The old `voice.engine` field is compatibility
input only and produces a deprecation warning.

VOICEVOX identity is caller-owned. Before synthesis, caller skills or media
packages must provide either both `speakerName` and `styleName`, or an explicit
`fallbackSpeakerId`. Cozy intentionally provides no character or VOICEVOX
speaker default; an unmatched configured name pair fails unless that caller
also supplies a fallback id.

Synthesize and validate the selected provider before creating audio:

```console
cozy video synthesize script.json \
  --save=build/audio/main \
  --check-tools \
  --tool-mode=docker \
  --docker-image=ghcr.io/asami/textus-toolchain:latest
```

Execution settings use this precedence: command line, script `tools`, project
Cozy configuration, then Cozy defaults. `--voicevox-url` follows the same
command-line-first rule. Docker settings prepare the shared execution boundary
for portable providers; VOICEVOX itself is checked and called as an external
HTTP service. `video inspect --check-tools` checks narration providers selected
by project parts instead of treating every installed provider as required.

Audio manifests record provider, execution mode, voice/model identity, and the
canonical WAV format. `video rdf` and `publish-video` consume that generated
manifest provenance rather than reconstructing it from script authoring.

On macOS, English narration can use the host-only `macos-say` provider:

```json
{
  "narration": {"provider": "macos-say"},
  "characters": {
    "guide": {"voice": {"voiceName": "Samantha", "rate": 185}},
    "reviewer": {"voice": {"voiceName": "Karen", "rate": 175}}
  }
}
```

Run it with `--tool-mode=host`. Cozy invokes `say` and ffmpeg as argument
vectors, converts the host audio to the canonical WAV contract, and records the
macOS voice name in each manifest entry. Docker mode is rejected before output;
`--check-tools` also diagnoses non-macOS hosts and missing `say` or ffmpeg.

Portable English narration uses the Docker-only `piper` provider:

```json
{
  "narration": {"provider": "piper"},
  "characters": {
    "guide": {"voice": {"model": "en_US-ljspeech-medium"}},
    "reviewer": {"voice": {"model": "en_US-joe-medium"}}
  }
}
```

Run it with `--tool-mode=docker` and a Textus toolchain image containing Piper.
The default model is `en_US-ljspeech-medium`; a character can select either
bundled model with `voice.model`. Cozy writes the narration text to a temporary
project workspace and invokes `textus-toolchain piper-synthesize` with
`--network=none`. Runtime model downloads are not allowed. The toolchain image
pins Piper `1.4.2`, model revisions, checksums, and source-license provenance in
`/opt/textus/models/piper/manifest.json`. Cozy records the selected model as
both voice and model identity in the generated audio manifest.

`--check-tools` runs `textus-toolchain check tts` in the selected image without
network access. Missing Docker, a missing image, an invalid runtime/model
checksum, or host tool mode stops checked synthesis before audio is generated.

## Composition And Visual Profiles

## Generic Character Dialogue Renderer

For a `dialogue` part, Cozy automatically selects the bundled generic
character-dialogue renderer when the script declares a non-empty `characters`
object. Set `renderer.strategy` to `character-dialogue` to select it
explicitly. Set `renderer.strategy` to `narration-card` (or `generic`) to
retain the generic narration-card renderer even when a script contains
characters.

The renderer template is `cozy-character-dialogue-v1`. Cozy records the
SHA-256 digest of its `DialogueVideo.jsx` entry resource and the named digest
of every staged template resource in `rendererTemplateResources`. Cozy stages
only assets declared in the script: character `asset`, `mouthClosedAsset`, and
`mouthOpenAsset`, plus a scene `visual.image`.

Cozy supplies no character names, images, voices, placement, credits, or
licenses. Caller tooling and its media package own those declarations and
their licensing; Cozy never fetches external character assets. A web-demo
presenter may declare `side`, `width`, `bottom`, `inset`, `maxHeight`, `flipX`,
and `shadow`; Cozy applies safe generic fallbacks only when a field is absent.

### Declarative Character-Dialogue Diagrams

The character-dialogue renderer also accepts a local declarative diagram. This
is an alternative to the legacy `visual.image` path; an existing image visual
continues to be staged and rendered unchanged.

```json
{
  "id": "reality-model",
  "speaker": "guide",
  "caption": "The model explains reality.",
  "visual": {
    "kind": "diagram",
    "heading": "Reality and model",
    "diagram": {
      "layout": "flow",
      "direction": "right",
      "clearance": 24,
      "nodes": [
        {"id": "reality", "label": "REALITY", "role": "lead", "labelPolicy": "atomic"},
        {"id": "model", "label": "Model", "role": "lead", "labelPolicy": "atomic"}
      ],
      "edges": [{"from": "reality", "to": "model"}]
    }
  }
}
```

`visual.heading` remains optional. `visual.layout: compact` is the existing
outer-layout opt-out; without it, diagram scenes use the large diagram
character layout. `diagram.layout` is either `flow` or `axis`; the only
supported direction in this first version is `right` (the default). `clearance`
defaults to 24 pixels and must be positive.

Every node has a unique non-empty `id` and `label`, a non-empty string `role`,
and `labelPolicy` of `atomic` or `balanced`. Atomic labels must fit on one line.
Balanced labels may break only at whitespace or a hyphen. Edges must name
existing `from` and `to` node IDs. A flow diagram respects declared node order.
For a flow that does not initially fit, the renderer keeps at least the declared
clearance, then reduces the permitted label font deterministically no lower than
18 pixels, then splits rows in declared order. If that sequence still cannot
fit, it stops with an explicit scene/node/violation diagnostic.

An axis diagram has exactly one `role: axis`. Nodes with `role: primary-view`
occupy the reserved right stack; `role: supporting-view` occupies the reserved
bottom row; all remaining nodes form the lead flow. The layout preserves the
declared clearance around those reserved regions.

Cozy validates this input before invoking Remotion and preserves authored
diagram JSON in renderer props. Contract diagnostics name the scene and, where
applicable, the node plus a violation such as `duplicate-node-id`,
`missing-edge-endpoint`, `unsupported-layout`, `unsupported-direction`,
`unsupported-label-policy`, `impossible-atomic-fit`, `label-overflow`, or
`axis-role-count`. The pure renderer layout also stops on stage overflow,
node/node or node/edge overlap, and clearance failure rather than silently
clipping or degrading output. Axis edges use deterministic reserved routing
corridors around the right stack and bottom row.

This first version is deterministic for one input, canvas, and font profile.
It does not auto-rewrite labels, infer roles, support directions other than
right, persist layout diagnostics into the part manifest, or provide a generic
repair for external artifact-tool label collisions.

`video.yaml` keeps composition and visual behavior separate:

```yaml
profile: explanation-demo-explanation
visual-effects:
  opening: title-hold-subtle-motion
  section-start: line-sweep
  summary: overview-and-conclusion
  final-page: end-card
renderer:
  engine: remotion
  policy: lightweight
```

Remotion consumes the opening, section-start, summary, and final-page profiles.
To use a renderer that does not declare those primitive capabilities, set the
corresponding profiles to `none`; Cozy otherwise stops before invoking the
renderer rather than silently substituting an effect.

`renderer.policy` is the policy-first encoding choice: `lightweight` is the
baseline for ordinary presentation and dialogue video, `standard` raises the
frame rate for motion-sensitive publication, and `quality` selects a 1080p
master. Add `fps`, `width`, `height`, `crf`, or `x264Preset` only when a
per-field override is required; each explicit field overrides the selected
policy while the remaining fields stay policy-resolved.

The default opening profile expands to a title card, subtle scale motion, and a
deterministic 4.5-second hold. It is inserted before the first renderable part
only. Scene audio and section-start timing begin after this interval; later
parts do not repeat it. This replaces project-local opening extraction and
manual ffmpeg prefix assembly.

The default final page appears after the final renderable composition part and
includes a deterministic two-second hold. Renderer workspace `props.json` and
the part manifest record the resulting frame timing.

Scaffold profile defaults can be changed independently with
`--opening-effect`, `--section-start-effect`, `--summary-effect`, and
`--final-page-effect`. Use `none` to disable one role explicitly.

## Replace Placeholder Assets

The scaffold generates these license-safe SVG slots:

```text
assets/opening.svg
assets/section-start.svg
assets/summary.svg
assets/final-page.svg
```

Replace a slot by editing its structured entry:

```yaml
assets:
  summary:
    path: assets/architecture-summary.svg
    kind: illustration
    required: true
    license: LicenseRef-Project-Owned
    provenance: project:architecture-team
```

Asset paths must be project-relative local files. Record an explicit license
and provenance for project-owned media. `required: true` makes a missing or
unreadable file a render error; an optional missing configured file may use the
generated slot placeholder.

Cozy does not fetch asset URLs. The scaffold neither copies nor references
media under `0714.techfirst.lt/assets`, whose redistribution license is not
established. Only renderer-neutral behavior inspired by that project is reused.

Each generated video package includes a `.gitignore` for `build/` and `target/`.
Narration output, renderer workspaces, intermediate MP4 files, final MP4 files,
and verification manifests therefore remain generated artifacts rather than
source files.

## Define Credit Profiles

Cozy resolves credits from a renderer-neutral profile instead of embedding
publication text in a Remotion component. Profiles use schema
`cozy.video.credits.v1` and may be installed at any existing Cozy configuration
layer:

```text
~/.cozy/video/credit-profiles/*.yaml
<project>/conf/cozy/video/credit-profiles/*.yaml
<project>/.cozy/video/credit-profiles/*.yaml
```

Later layers replace a profile with the same ID. Select a user or project
default in the corresponding `config.yaml`:

```yaml
video:
  credits:
    default-profile: organization-publication
```

An individual `video.yaml` may override that default and publication locale:

```yaml
locale: ja
credits:
  profile: organization-publication
  presentation:
    enabled: false
  include: []
  exclude: []
```

Profile selection precedence is explicit `video.yaml`, project-local `.cozy`,
project `conf/cozy`, user `~/.cozy`, then no profile. The scaffold leaves the
profile unspecified so a configured user or organization default applies.

`credits.presentation.enabled` defaults to `true`. Set it to `false` when the
final page already carries the credit information and only the standalone video
credit page should be suppressed. The selected credit items, validation,
`credits.json`, `credits.md`, `renderer-props.json`, RDF projection, semantic
digest, and isolated publication workspace remain present.

A minimal reusable profile is:

```yaml
schema: cozy.video.credits.v1
profile: organization-publication
required-audio-providers: [voicevox]
presentation:
  title: {ja: 使用素材・音声, en: Credits}
  hold-seconds: 5.0
selectors:
  - when:
      any-asset-tag: [character.guide]
    include: [guide-material]
  - when:
      audio-provider: voicevox
      voice-identity: Example Voice
    include: [voicevox-example]
credits:
  - id: guide-material
    category: character-material
    label: {ja: ガイド立ち絵, en: Guide character material}
    publication-text: {ja: ガイド立ち絵, en: Guide character material}
    creator: Example Studio
    terms-url: https://example.test/material-terms
    surfaces: [video, publication, rdf]
  - id: voicevox-example
    category: voice
    label: {ja: "VOICEVOX:Example Voice", en: "VOICEVOX:Example Voice"}
    publication-text: {ja: "VOICEVOX:Example Voice", en: "VOICEVOX:Example Voice"}
    obligation: required
    surfaces: [video, publication, rdf]
```

`obligation` accepts `required` or `recommended`; omitting it means
`required`. `surfaces` accepts `video`, `publication`, and `rdf`. Cozy rejects
unknown values so an authoring typo cannot silently weaken attribution. A
negative presentation hold is also invalid, and two files in one configuration
layer may not define the same profile ID.

Selectors support character IDs, asset tags, asset license/provenance IDs,
audio provider, voice/voice-ID/model identity, and locale. Character material
can also be declared as a semantic asset that does not occupy an effect slot:

```yaml
assets:
  guide:
    path: assets/guide.png
    kind: character-material
    required: true
    tags: [character.guide]
    license: LicenseRef-Guide
    provenance: creator:example-studio
    credits: [guide-material]
    credit-obligation: required
```

Voice selection always uses the generated audio manifest. Script locale or
stale `voice.engine` metadata never substitutes for the actual provider and
voice identity. A profile can name providers whose every manifested voice must
match a required `voice` credit with `required-audio-providers`.

`cozy video render`, `cozy video build`, and `cozy video rdf` validate required
credits. `cozy media verify` applies the same check to delegated video
resources. Unknown or excluded required items, unmatched required audio, and
missing localized required text fail; recommended asset declarations produce
inspection warnings. Declaring required asset credits without selecting a
profile also fails rather than discarding the declaration.

When a profile is selected, Cozy writes one effective set and two projections:

```text
build/credits/credits.json
build/credits/credits.md
build/credits/renderer-props.json
```

The semantic JSON digest is independent of workspace and manifest filesystem
locations, but includes localized presentation text, hold timing, and the
effective presentation-enabled state. It is
copied into build and RDF metadata. `credits.md` is ready
for a publication description. Remotion consumes `renderer-props.json` and
inserts a static, non-empty credit page after content/summary and before the
final URL page when presentation is enabled. No credit page or credit directory
is generated when no profile is selected.

## Inspect, Render, And Publish

Inspect the effective profile, primitive expansion, assets, and renderer
capability before rendering:

```console
cozy video inspect src/main/doxsite/technology/product-demo.video/video.yaml
```

Render profile parts and then assemble the project:

```console
cozy video render src/main/doxsite/technology/product-demo.video/video.yaml \
  --renderer=remotion
cozy video build src/main/doxsite/technology/product-demo.video/video.yaml
```

Docker rendering requires a Textus toolchain image whose `check video`
contract includes a compatible Remotion CLI, runtime, and renderer:

```console
docker run --rm ghcr.io/asami/textus-toolchain:latest \
  textus-toolchain check video
```

Generate the RDF description when required by the publication workflow:

```console
cozy video rdf src/main/doxsite/technology/product-demo.video/video.yaml \
  --save=src/main/doxsite/technology/product-demo.video/rdf
```

`publish-video` preserves the composition profile, visual effects, and resolved
project-owned assets in its isolated publication workspace:

```console
cozy publish-video src/main/doxsite/technology/product-demo.video \
  --save=src/main/publication \
  --warehouse=warehouse
```

Generated placeholder assets are suitable for validating the pipeline, but
authors should replace them with reviewed project-owned media before a public
release.
