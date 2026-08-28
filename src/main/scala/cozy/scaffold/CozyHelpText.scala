package cozy.scaffold

/*
 * @since   Aug. 25, 2026
 *  version Aug. 25, 2026
 * @version Aug. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyHelpText {
  private[cozy] val _text: String =
    """Usage:
      |  cozy [command] [options]
      |
      |Commands:
      |  help, --help, -h
      |      Show this help and exit.
      |
      |  version, --version
      |      Show the Cozy runtime version and exit.
      |
      |  init component --save <dir> [--config <file>] [--name <artifact>] [--component-name <name>|--component <name>] [--service-name <name>] [--entity <name>] [--command-operation <name>] [--query-operation <name>] [--display-name <title>] [--organization <organization>] [--package <package>] [--version <version>] [--kind car|car-sar] [--bounded-context <name>] [--domain <name>] [--gitignore] [--readme] [--tests] [--mcp-ready-service] [--no-project-files] [--overwrite-project-files]
      |    canonical identity keys: project.namespace, project.id
      |    Canonical identity is authoritative when either canonical key is authored. Component, artifact, organization, package, class, and path are shared derived projections; corresponding legacy CLI/config identity inputs do not override them.
      |    --name, --component-name/--component, --organization, --package, and legacy project/cml identity fields are fallback inputs only when canonical identity is absent.
      |    config keys: project.namespace, project.id, project.name, project.organization, project.scalaPackage, project.component.*, project.component.config.*, project.scaffold.*, cml.package, cml.component.name, cml.service.name, cml.entity.name, cml.operation.command, cml.operation.query
      |      Initialize a component project scaffold.
      |
      |  car-sbt-project [model-file] --save <dir> [--style car|car-sar] [--component <name>] [--service-name <name>] [--entity <name>] [--command-operation <name>] [--query-operation <name>] [--package <package>] [--name <artifact>] [--organization <organization>] [--version <version>] [--bounded-context <name>] [--domain <name>] [--gitignore] [--readme] [--tests] [--no-project-files] [--overwrite-project-files]
      |      Generate an sbt project scaffold. `car` creates a single CAR component project.
      |      `car-sar` creates an application root with `component/` and `subsystem/`.
      |      When model-file is omitted, create a scaffold sample model.
      |      By default, existing differing project files are written as .bak files.
      |
      |  lint build <project-root> [--format text|json] [--strict]
      |      Lint project/plugins.sbt and build.sbt for Cozy build wiring, including sbt-cozy plugin freshness and CAR ABI manifest compatibility when present.
      |
      |  lint cml <path> [--format text|json]
      |      Lint one CML file or a directory of CML files for CAR modeling smells.
      |
      |  lint abi <car|manifest|project-root> [--baseline <car|manifest>] [--format text|json] [--strict]
      |      Lint CAR ABI manifest compatibility. CAR ABI is the public component contract; JVM class-file ABI is not the primary lint surface.
      |      Without --baseline, Cozy looks for src/main/car/<version>/abi-manifest.json, target/cozy/abi-baseline.json, or target/abi-baseline.json.
      |      Missing baseline is a warning, even in strict mode, so the first ABI release can start operation.
      |
      |  lint car <project-root> [--baseline <car|manifest>] [--format text|json] [--strict] [--no-abi]
      |      Run integrated CAR project lint by aggregating build, CML, documentation, and ABI findings. Documentation lint checks the reference manual, user guide, and CML descriptions used by generated Help. CNCF runtime owns CLI, Help, Manual, OpenAPI, and MCP navigation. Use --no-abi for early checks before an ABI manifest exists.
      |
      |  car lint <project-root> [--baseline <car|manifest>] [--format text|json] [--strict] [--no-abi]
      |      Alias for lint car.
      |  lint repository <repository-root> [--format text|json]
      |      Validate repository/catalog/index.json and referenced CAR/SAR catalogs without network access.
      |
      |  bok create --save <dir> [--name <name>] [--url <url>] [--language ja] [--no-project-files] [--overwrite-project-files]
      |      Create a SmartDox category-driven BoK source project scaffold without generated HTML, Arcadia assets, or site-structure.yaml.
      |      Public source is src/main/doxsite; durable article-media and publication inputs are src/main/media and src/main/publication.
      |      The standard Manual, History, RDF, machine metadata, and site UI are generated outputs. Optional src/main/extensions/rdf is absent by default.
      |      Repository operations, design, specification, and journal documents remain outside the public source tree.
      |
      |  bok create-category <category-name> [--project <dir>] [--title <title>] [--description <text>] [--vision <text>] [--goal <text>] [--subgoal <text>] [--article <slug:title:purpose>] [--term <slug:title:definition>]
      |      Add a category, category index, and optional article or term seeds to a BoK source project.
      |      --goal and --subgoal may be specified multiple times and are rendered on the Category Dashboard.
      |
      |  bok build [<project-dir>] [--strategy wip|draft|preview|production] [--docker-image <image>] [--no-bib-service]
      |      Build BoK HTML under website.d using SmartDox and Antora through the configured Docker image.
      |      By default, unresolved external bibliography references are fetched into target/cozy-bok/bibliography/cache.
      |      Use --no-bib-service for offline/cache-only builds; unresolved references are reported as warnings.
      |      The default Docker image is the standard Textus toolchain image: ghcr.io/asami/textus-toolchain:latest.
      |
      |  bok update [<project-dir>] [--strategy wip|draft|preview|production] [--docker-image <image>]
      |      Update the BoK output. In this version it runs the same generation flow as bok build.
      |
      |  bok doctor [<project-dir>] [--fix] [--dry-run]
      |      Inspect the specified path, resolve the actual BoK root, and report source/config/workflow drift.
      |
      |  bok fix [<project-dir>] [--dry-run]
      |      Apply safe non-destructive BoK repairs, such as current Docker image and generated-directory .gitignore entries.
      |
      |  bok guide [scenario]
      |      Show scenario-based BoK operation guides. Use bok guide to list scenarios.
      |
      |  bok publish-video <project-dir> [--publication <dir>] [--repository <dir>] [--warehouse <dir>] [--version <version>] [--force]
      |      Publish all .video packages in a BoK source tree into src/main/publication and the artifact repository.
      |
      |  bok publish-projects <project-dir> [--publication <dir>] [--repository <dir>] [--warehouse <dir>] [--version <version>] [--force]
      |      Register all src/main/doxsite/projects/<category>/<slug> project knowledge packages into src/main/publication. Use publish-car for CAR artifacts.
      |
      |  bok update-publication <project-dir> [--publication <dir>] [--repository <dir>] [--warehouse <dir>] [--version <version>] [--force]
      |      Update BoK publication registry metadata for .video and project knowledge packages.
      |
      |  bok search-bibliography <query> [--provider crossref|openlibrary|dblp|all] [--limit <n>] [--format text|json]
      |      Search external bibliography/reference providers without changing BoK source files.
      |
      |  bok update-bibliography [<project-dir>] [--force] [--report-only|--no-fetch]
      |      Fetch explicit BibTeX/cache sources registered in bibliography metadata into target/cozy-bok/bibliography/cache.
      |      Local .bib files under repository/bibliography, repository/catalog/bibliography, or src/main/doxsite/bibliography are used before external providers.
      |      With --report-only or --no-fetch, report missing bibliography cache entries without external fetches.
      |
      |  bok publish <project-dir> [--publication <dir>] [--repository <dir>] [--warehouse <dir>] [--version <version>] [--strategy production] [--force] [--dry-run]
      |      Run update-publication, production BoK build, and the configured bok.workflow.upload.command.
      |      With --dry-run, print the planned steps and write a target manifest without changing publication, repository, or site output.
      |
      |  bok preview [<project-dir>] [--port 8980]
      |      Serve website.d with python3 -m http.server for local preview.
      |      Open http://127.0.0.1:<port>/ in a browser instead of opening generated HTML files directly.
      |
      |  bok stage [<project-dir>]
      |      Run the external command registered at bok.workflow.stage.command to stage generated website output.
      |
      |  bok upload [<project-dir>]
      |      Run the external command registered at bok.workflow.upload.command. Cozy does not interpret upload targets or credentials.
      |      When bok.backup.enabled is true, website.d is backed up before upload. Defaults: enabled=false, dir=website.backup, compressed=true.
      |
      |  video inspect <project-file> [--check-tools] [--tool-mode=<docker|host>] [--docker-image=<image>]
      |      Inspect a video project file and print a deterministic project, part, script, and tool-check plan.
      |      Project and script files may be JSON, YAML, HOCON, or XML.
      |      Docker is the default tool mode; --check-tools validates the selected narration provider.
      |      VOICEVOX uses HTTP, macos-say uses host tools, and Piper uses the Docker toolchain image.
      |
      |  video scaffold <slug> [--save=<slug>.video] [--title=<title>] [--profile=<explanation|explanation-demo-explanation>]
      |      Create a Git-managed video source package with deterministic script and license-safe placeholder assets.
      |      Visual profiles may be selected with --opening-effect, --section-start-effect, --summary-effect, and --final-page-effect.
      |
      |  video build <project-file> [--dry-run] [--check-tools] [--tool-mode=<docker|host>] [--docker-image=<image>]
      |      Assemble already-rendered part MP4 files into the project final output, or print the plan with --dry-run.
      |      Docker mode wraps Remotion, Playwright, ffmpeg, whisper.cpp, and Python/Pillow helper steps in the configured Textus toolchain image.
      |
      |  video synthesize <script-file> --save <audio-dir> [--check-tools] [--tool-mode=<docker|host>] [--docker-image=<image>] [--voicevox-url=<url>]
      |      Generate provider-selected scene WAV files, a combined WAV, and manifest.json.
      |      CLI execution settings override script tools and Cozy video defaults. VOICEVOX remains an external HTTP service.
      |
      |  video render <project-file> --renderer=remotion|simple-java2d [--part=<id>] [--tool-mode=<docker|host>] [--docker-image=<image>] [--check-tools]
      |      Render project parts with Cozy-generated Remotion compositions or a simple Python/Pillow plus ffmpeg renderer.
      |      Final concat/mux remains a separate later video build step.
      |
      |  video review-evidence <project-file> --save=<dir> [--check-tools] [--tool-mode=<docker|host>] [--docker-image=<image>]
      |      Extract deterministic PNG review evidence and a manifest from an already-built final video and Cozy-authored Remotion props/audio manifests.
      |      This command does not generate video, slides, or presentation files.
      |
      |  video transcribe <input-video> --save <dir> [--tool-mode=<docker|host>] [--docker-image=<image>] [--whisper-model=<path>] [--check-tools]
      |      Extract audio from a recorded demo video, run whisper.cpp, and write transcript, captions, narration draft, and manifest files.
      |
      |  video demo-script <input-video> --save <script-file> [--events=<file>] [--har=<file>] [--trace=<trace.zip>] [--transcript=<transcript.json>]
      |      Generate a Playwright replay script draft from recorded demo metadata.
      |
      |  video replay <script-file> [--save=<output-video.webm>] [--dry-run] [--tool-mode=<docker|host>] [--docker-image=<image>] [--check-tools]
      |      Dry-run or execute a generated Playwright replay script. Recorded replay output is WebM.
      |
      |  video rdf <project-file> --save <dir> [--tool-mode=<docker|host>] [--docker-image=<image>]
      |      Generate Turtle and JSON-LD video metadata using the SmartDox semanticweb RDF renderer.
      |
      |  media inspect <media-file>
      |      Inspect a BoK Media Package and its knowledge, language, resource, and publication-profile bindings.
      |
      |  media plan <media-file> [--target <id>] [--profile <name>]
      |      Report deterministic build and publication actions without changing files.
      |
      |  media build <media-file> [--target <id>] [--dry-run]
      |      Build configured resources, including business presentations, then accept deterministic receipt evidence.
      |
      |  media verify <media-file> [--target <id>] [--profile <name>]
      |      Verify knowledge sources, generated outputs, PNG dimensions, and optional publication equality.
      |
      |  media publish <media-file> --profile <name> [--target <id>] [--dry-run]
      |      Publish verified outputs through a logical profile without storing machine-specific absolute paths in the package.
      |
      |  media slide validate|plan|verify <media-file> [--target <id>] [--profile business]
      |      Validate, plan, or verify business presentation resources with semantic slide IR and deterministic artifact evidence.
      |
      |  media slide build <media-file> [--target <id>] [--profile business] [--dry-run]
      |      Render selected business presentation resources with deterministic artifact evidence.
      |
      |  media cross-review build <media-file> --target <presentation-id> --video-project <project-file> --save <output.json>
      |      Reconstruct current Visual Page presentation and Storyboard evidence into structural cross-media review proof.
      |
      |  media cross-review verify <media-file> --target <presentation-id> --video-project <project-file> --cross-review <input.json>
      |      Verify saved cross-media review proof against exact current Cozy evidence without rendering or approving content.
      |
      |  media presentation migrate <legacy-slide-ir> --semantic-map <semantic-map> --catalog <catalog> --save <visual-page-set>
      |      Migrate legacy Slide IR only through a complete digest-bound semantic map, then atomically save the canonical Visual Page Set.
      |
      |  media visual-page validate|inspect <input> --catalog <catalog>
      |      Strictly validate or inspect one Visual Page or ordered Visual Page Set without rendering or generating media.
      |
      |  media visual-page convert <input> --catalog <catalog> --save <output.json|output.yaml|output.yml|output.md>
      |      Validate first, then atomically write the selected canonical lossless Visual Page serialization.
      |
      |  media visual-page preview <input> --catalog <catalog> --save <output.html> [--png <output.png>]
      |      Generate a deterministic semantic preview from one validated Visual Page or ordered Visual Page Set without selecting a presentation route.
      |
      |  media explanation validate|inspect <catalog>
      |  media explanation validate|inspect <composition> --explanation-catalog <catalog> --presentation-catalog <catalog> [--source <id>=<file>] [--asset <id>=<file>]
      |  media explanation validate|inspect <plan> --composition <composition> --explanation-catalog <catalog> --presentation-catalog <catalog> [--source <id>=<file>] [--asset <id>=<file>]
      |  media explanation validate|inspect <projection-map> --composition <composition> --plan <plan> --explanation-catalog <catalog> --presentation-catalog <catalog> [--source <id>=<file>] [--asset <id>=<file>]
      |  media explanation validate|inspect <projection> --composition <composition> --plan <plan> --projection-map <projection-map> --explanation-catalog <catalog> --presentation-catalog <catalog> --visual-page-set <set> --storyboard <storyboard> [--source <id>=<file>] [--asset <id>=<file>]
      |      Strictly validate or inspect one direct JSON Explanation Catalog, authored Composition, deterministic Plan, ProjectionMap, or Projection; no catalog or resource discovery is performed.
      |
      |  media explanation convert <catalog> --save <output.json>
      |  media explanation convert <composition> --explanation-catalog <catalog> --presentation-catalog <catalog> --save <output.json> [--source <id>=<file>] [--asset <id>=<file>]
      |  media explanation convert <plan> --composition <composition> --explanation-catalog <catalog> --presentation-catalog <catalog> --save <output.json> [--source <id>=<file>] [--asset <id>=<file>]
      |  media explanation convert <projection-map> --composition <composition> --plan <plan> --explanation-catalog <catalog> --presentation-catalog <catalog> --save <output.json> [--source <id>=<file>] [--asset <id>=<file>]
      |  media explanation convert <projection> --composition <composition> --plan <plan> --projection-map <projection-map> --explanation-catalog <catalog> --presentation-catalog <catalog> --visual-page-set <set> --storyboard <storyboard> --save <output.json> [--source <id>=<file>] [--asset <id>=<file>]
      |      Validate first, then atomically write the selected canonical JSON without inferring claims, graph structure, roles, steps, or media.
      |
      |  media explanation expand <composition> --explanation-catalog <catalog> --presentation-catalog <catalog> --save <plan.json> [--source <id>=<file>] [--asset <id>=<file>]
      |      Validate explicitly authored Composition inputs and deterministically copy them into a medium-neutral Explanation Plan without projection or media generation.
      |
      |  media explanation project --projection-map <projection-map> --composition <composition> --plan <plan> --explanation-catalog <catalog> --presentation-catalog <catalog> --visual-page-set <set> --storyboard <storyboard> --save <projection.json> [--source <id>=<file>] [--asset <id>=<file>]
      |  media explanation verify-projection <projection> --composition <composition> --plan <plan> --projection-map <projection-map> --explanation-catalog <catalog> --presentation-catalog <catalog> --visual-page-set <set> --storyboard <storyboard> [--source <id>=<file>] [--asset <id>=<file>]
      |      ProjectionMap and Projection use explicitly named files only. They verify currentness and linkage only; they do not discover, generate, render, modify media, or approve anything.
      |
      |  media review align <media-file> --target <presentation-id> --authority <article|slide-ir>
      |      Record an explicit article or slide-IR semantic alignment decision after deterministic verification.
      |
      |  media scaffold article <slug> --profile business --language <tag> --save <dir>
      |      Atomically create an article media package without fabricating a template binary or rendered PPTX.
      |
      |  media register-site <media-file> --publication <dir> [--target <resource-id>] [--dry-run]
      |      Register declared published site-media evidence as provider-neutral SmartDox article-media metadata.
      |
      |  media register-site-wip <media-file> --publication <publication-root> --website <website-root> [--target <resource-id>] [--dry-run]
      |      Install validated local WIP video and register provider-neutral SmartDox article-media metadata.
      |
      |  modeler-scala <model-file> --save <dir> [--generation-source-identity <project-relative-path>]
      |      Generate Scala sources from a CML/Dox model. CNCF descriptor generation requires a stable project-relative source identity and writes target/cozy/generation-provenance.json.
      |
      |  modeler-scala-value <model-file> --save <dir> [--generation-source-identity <project-relative-path>]
      |      Generate value/domain model Scala sources without a component. CNCF descriptor generation requires a stable project-relative source identity and writes target/cozy/generation-provenance.json.
      |
      |  generation-provenance-validate <model-file> --save <generation-output-root> --cncf-version <version> --cncf-runtime-descriptor-sha256 <sha256> --cozy-generator-version <version> --generation-source-identity <project-relative-path> --generation-source-sha256 <sha256>
      |      Validate generated Scala and target/cozy/generation-provenance.json against the owning build's exact generation inputs.
      |
      |  package-car --save <file> --main-jar <file> --name <name> --version <version> --project-dir <dir> [--component <component>] [--car-dir <dir>] [--entities <spec>] [--abi-manifest <file>]
      |      Build a CAR archive with abi-manifest.json and car-runtime-manifest.json from the required project.yaml CAR contract. Explicit --abi-manifest overrides src/main/car/abi-manifest.json; versioned src/main/car/<version>/abi-manifest.json files are lint baselines only.
      |
      |  package-sar --save <file> --source-dir <dir> --name <name> --version <version>
      |      Build a SAR archive.
      |
      |  package-subcomponent-release --project-dir <dir> --parent-car <car> --child-car <car>... --composition <RSC-02-json> --required-child <qualified-id>... --save <car> --integrity <file> --published-at <RFC3339 instant>
      |      Validate the project-authoritative parent and RSC-02 composition membership, then emit a deterministic parent/child release CAR, copied canonical composition metadata, and matching integrity evidence. This envelope does not update the ordinary CAR repository index.
      |
      |  publish-car <project-dir> --warehouse <dir> --name <artifact> --version <version> [--car <file> | --main-jar <file>]
      |      Publish a CAR archive and CAR catalog, update repository/catalog/index.json atomically, and write derived Maven metadata.
      |      sbt-cozy cozyPublishLocalCar calls this command with ~/.cncf/local as the warehouse root.
      |
      |  publish-sar <project-dir> --warehouse <dir> --name <artifact> --version <version> [--sar <file> | --source-dir <dir>]
      |      Publish a SAR archive and SAR catalog, update repository/catalog/index.json atomically, and write derived Maven metadata.
      |      sbt-cozy cozyPublishLocalSar calls this command with ~/.cncf/local as the warehouse root.
      |
      |  publish-subcomponent-release --project-dir <dir> --warehouse <dir> --release <car> --integrity <file> --published-at <RFC3339 instant>
      |      Revalidate release and integrity evidence, then atomically admit the immutable release under its canonical parent coordinate. admission.json is the final visibility marker; this command does not alter CAR/SAR catalogs, indexes, or runtime activation.
      |
      |  publish-project <project-dir> [--save <dir>] [--kind car|sar|sample-single|sample-multi|maven-repository] [--name <slug>] [--title <title>] [--path <path>]
      |      Generate SmartDox site BoK publication registry sources from an sbt project.
      |      Writes or replaces one publication bundle under the registry.
      |
      |  publish-video <slug>.video --save <publication-dir> --warehouse <warehouse-dir> [--version <version>] [--force]
      |      Build and publish a .video source package. Generated MP4/RDF sidecars go to warehouse/repository/video; only metadata is written to the publication registry.
      |
      |  publish-maven-repository <repository-dir> --save <dir> --name <slug> [--title <title>] [--path <path>] [--maven-coordinates <group:artifact,...>]
      |      Generate a SmartDox publication bundle and Maven artifact metadata from a Maven repository directory.
      |
      |  unpublish-project --save <dir> --name <slug>
      |      Remove a publication bundle from a publication registry.
      |
      |  distribute-samples <project-dir> --warehouse <dir> --name <slug> --version <version> [--samples-dir <dir>] [--dry-run]
      |      Zip the sample collection and each sample project under warehouse/repository/download/<publication.path>.
      |      With --dry-run, print planned output paths without writing archives.
      |
      |  index-warehouse <warehouse-dir> --save <dir> --name <slug> [--title <title>] [--repository-artifacts car,sar,zip] [--repository-modules <module,...>] [--download-samples <publication,...>]
      |      Generate publication registry download/repository release metadata by indexing a warehouse.
      |
      |  sbt-bridge v1 --request <file>
      |      Run the sbt-cozy bridge for generation or archive packaging.
      |
      |  web
      |      Start the Cozy web server.
      |
      |With no arguments, cozy starts the interactive REPL.
      |""".stripMargin
}
