# Phase 93 portable acceptance inputs

This fixture adapts the Repository and Data Mapper explanation from the DDD guide in `textus-technical-documentation` (working copy based on `28d1fa79957a7e51545ca4bbc90cc4e1c02a39fe`). Retry is from SmartDox producer revision `8a67fbf102166754c7f19fd3d64233a64e9c8fb0`. Fixture IRIs are isolated. The two localized Documents share one recursive Core and stable bindings; `expected-labels.json` specifies labels and page identities. The code identifier, repeated pattern, figures and table exercise surrounding content. No live guide is changed.

For a standalone project, copy `content` and `settings` to a fresh project directory. Copy `project/<locale>/index.dox` and `project/media.yaml` to the project root, and `project/document-project-targets.yaml` to `build/document-project-targets.yaml`. For English replace `content/ja/` with `content/en/` in the target descriptor. Generate a LexiDox site from `lexidox-source` using the adopted SmartDox producer and retain its generated site output as the catalog root. For example, from the repository root, after arranging those paths, build HTML with:

```sh
cozy document-project build "$project" --target smartdox-article-html --locale "$locale" --lexidox-root "$catalog" --term-presentation settings/terms.yaml
```

Set `locale` to `ja` or `en`, and set `project` and `catalog` to the prepared project and producer site output paths. Change `profile` in `settings/terms.yaml` to `original`, `established-japanese`, or `japanese`; `htmlSwitch` enables reader selection. The example `glossaryBase` is a fixture URI; downstream consumers must set their own admitted glossary base and role styles.

The shared media template has separate locale-tagged infographic and article PDF resources. The selected `--locale` chooses the corresponding pair. Create the small common infographic and the portable legacy renderer wrapper inside the prepared project:

```sh
mkdir -p "$project/images"
cat > "$project/images/infographic.svg" <<'SVG'
<svg xmlns="http://www.w3.org/2000/svg" width="480" height="120" viewBox="0 0 480 120"><rect width="480" height="120" fill="#f6f8fb"/><path d="M34 60H445" stroke="#21466a" stroke-width="4"/><circle cx="110" cy="60" r="14" fill="#21466a"/><circle cx="370" cy="60" r="14" fill="#503b2a"/><text x="86" y="100">Order</text><text x="346" y="100">Store</text></svg>
SVG
cat > "$project/render-pdf" <<'SH'
#!/bin/sh
exec dox pdf "$@"
SH
chmod +x "$project/render-pdf"
```

The infographic is a fixture dependency for the media plan; the localized Core figures are separate inputs. Install the adopted `dox` executable in `PATH` and configure its dependencies for the environment. The optional typed `pdf` object in `settings/terms.yaml` defaults to `latexEngine: lualatex` and `dependencyMode: auto`. For a local LuaLaTeX installation use `"pdf": {"latexEngine": "lualatex", "dependencyMode": "local"}`. Alternatively use `"pdf": {"latexEngine": "lualatex", "dependencyMode": "docker", "docker": "docker", "dockerImage": "<available-image>"}` and replace the image placeholder with an available compatible image. These settings are passed to the native PDF operation; no host-specific executable or image is embedded in the fixture.

Use a fresh independent work root outside the project for each run and select the output-time profile before running:

```sh
cozy document-project build "$project" --target article-pdf --locale "$locale" --work-root "$work_root" --term-presentation settings/terms.yaml --lexidox-root "$catalog"
```

The run reports the generated PDF in its isolated workspace. Repeat with a fresh work root for each locale and profile. The project source and its live DDD guide remain unchanged.

The executable integration specification builds producer metadata from these authored sources in-process and exercises the normal Cozy entry point. Native output observations and limitations are recorded in the Phase 93 journal; the fixture itself does not assert that final Phase review or closure has occurred.

## Materialize the fixture figure

Run this from the fixture source directory with the fresh project directory and its locale as arguments. Both localized captions point to the same compact order-to-store test figure. The full DDD guide diagrams remain in the separate native output evidence.

```sh
python3 - /path/to/project.dox ja <<'PYTHON'
import base64
import json
from pathlib import Path
import sys

project = Path(sys.argv[1])
locale = sys.argv[2]
if locale not in {"ja", "en"}:
    raise ValueError("locale must be ja or en")
data = json.loads(Path("expected-labels.json").read_text(encoding="utf-8"))
images = project / "images"
images.mkdir(exist_ok=True)
(images / f"order-model-execution-{locale}.png").write_bytes(
    base64.b64decode(data["figurePngBase64"], validate=True)
)
PYTHON
```

## Phase 92 compatibility comparison

`cozy.document.CozyPhase92CompatibilityDriver` is an explicit offline driver, separate from ordinary portable tests. Compile the test sources with the project-selected validation, then invoke its main on the resulting Test/Compile classpath with three arguments: the original Phase 92 fixture directory (`site.conf` and `spaces/*.dox`), the corresponding retained baseline `metadata/lexidox` directory, and a fresh, nonexistent output directory. For example, with `TEST_CLASSPATH` set to that compiled classpath:

```sh
java -cp "$TEST_CLASSPATH" cozy.document.CozyPhase92CompatibilityDriver "$PHASE92_FIXTURE" "$PHASE92_BASELINE_METADATA" "$FRESH_OUTPUT"
```

The current in-process SmartDox producer generates from the unmodified fixture. The driver compares the complete baseline JSON envelope after removing only presentation extension fields, and requires byte-identical Turtle and JSON-LD (including SKOS). It admits the new output through Cozy and resolves every admitted page in Japanese and English under all three profiles, requiring stable typed identities and links. A successful run writes `compatibility-result.txt` in the output directory; a mismatch stops the driver and is not acceptance evidence. The retained baseline and original fixture must be obtained from the Phase 92 producer delivery described in the knowledge-space handoff; the Phase 93 DDD fixture is not a substitute.
