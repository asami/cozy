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

## Composition And Visual Profiles

`video.yaml` keeps composition and visual behavior separate:

```yaml
profile: explanation-demo-explanation
visual-effects:
  section-start: line-sweep
  summary: overview-and-conclusion
  final-page: end-card
renderer:
  engine: remotion
```

Remotion consumes the initial section-start, summary, and final-page profiles.
To use a renderer that does not declare those primitive capabilities, set the
corresponding profiles to `none`; Cozy otherwise stops before invoking the
renderer rather than silently substituting an effect.

The default final page appears after the final renderable composition part and
includes a deterministic two-second hold. Renderer workspace `props.json` and
the part manifest record the resulting frame timing.

## Replace Placeholder Assets

The scaffold generates these license-safe SVG slots:

```text
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
