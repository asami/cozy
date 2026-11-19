# Development artifact generation: sbt-bridge v2

This contract describes the Cozy supplier boundary. The separate sbt-cozy
adapter resolves the generator graph with SBT and starts `cozy.Cozy` in a fresh
JVM. Generator Scala 2.12 artifacts must be isolated from the consumer's Scala 3
and CNCF runtime graph. A configuration name alone does not establish isolation.

Invoke `sbt-bridge v2 --request <absolute-json-file>`. The request has
`version: "v2"`, `action: "generate-development"`, and the following fields:

- `selection`: nonempty `scope`, `owner`, `selectionId`, `batch`, and absolute
  `publicationRoot`.
- `roots`: absolute `build`, `owner`, `source`, `output`, and `result` paths. The
  publication root must be inside the admitted owner root. The output
  and result are separate paths inside build; output must be absent or empty.
- `modules`: the ordered SBT-resolved modules, each with `organization`, `name`,
  `requestedVersion`, `selectedVersion`, `baseVersion`, absolute `artifact`,
  `development`, and optional `publication` and `control` paths. `classpath`
  contains the same artifact paths in the same order. `edges` is an array of
  `{from,to,requestedVersion}`, where each end is `organization:name:selectedVersion` and `requestedVersion` is the dependency revision declared by the source publication. This retains SBT evictions explicitly.
- `arguments`: exactly one of `modeler-scala` or `modeler-scala-value`, the
  absolute input file, `--save`, and the output root. `settings` supplies the
  existing bridge generation settings, including `sbt.project_dir` equal to
  build and `generation.versions.cozy` equal to the actual Cozy base version.

The request also supplies absolute `resolution` and `batchRecord` paths. The
resolution record contains the ordered modules and edges and must agree with the
request. Development artifacts use canonical Ivy layout
`<repository>/<organization>/<name>/<selectedVersion>/jars/<name>.jar`, with
`ivys/ivy.xml` beside `jars`, within the admitted publication root. Ordinary
artifacts may instead use a canonical Maven layout
`<repository>/<organization as path>/<name>/<selectedVersion>/<name>-<selectedVersion>.jar`
and its sibling `<name>-<selectedVersion>.pom`, including the existing Coursier
cache. Cozy reads metadata in place and binds its coordinates to the selected
artifact. The supplied edges must exactly equal the supported declared runtime
dependencies, including each declared requested revision and selected target.
The SBT producer remains responsible for resolution and version mediation.

Ivy 2.0 support requires `runtime` extending `compile`, and `compile` without
parents. Supported runtime mappings are `compile->default(compile)`,
`runtime->default(runtime)` and `runtime->default(compile)`; compound mappings,
nested runtime rules and force/transitive attributes are rejected. Maven POMs
support locally available canonical parent POMs, properties, simple dependency
management, and compile/runtime JAR dependencies. Provided, test, system and
optional Maven dependencies are excluded. Profiles, imported management,
classifiers, exclusions, relocations, unresolved properties, version ranges and missing local
metadata fail closed. Maven model features outside this bounded support are not
resolved over the network. This metadata check does not establish the provenance
of a forged repository or qualify the downstream SBT producer.

The common `batchRecord` is under the publication root and contains `scope`,
`owner`, `batch`, `producerSelectionId`, `complete: true`, and the complete
`members` array of development module records. Every member's publication and
control must exist under that root, including members outside the selected graph.
Each control binds the batch, producer selection, module coordinates and artifact
and declares completion. Selected development modules must be batch members.
The consumer `selectionId` identifies the current selection; it is independent
of the immutable producer selection so completed publications can be reused.
Each development selected version differs from its explicit base version; an
ordinary module has equal selected/base versions and no control; its requested version may differ after mediation.

Cozy checks graph references, reachability, duplicate/conflicting coordinates,
classpath order, existing artifacts and control records, generator roots, and a
single Scala 2 library. It compares Cozy's actual BuildInfo and its declared
backend requirement to the root base versions. It uses the actual code sources
and class resources for `cozy.Cozy` and `Scala3ClassGeneratorExecutor`; backend
version comes from `Implementation-Version` in that class's own JAR manifest.
Missing or inconsistent origin/version metadata is rejected. The ordered JVM
classpath must match the supplied resolved classpath. Classloader URLs, when
exposed by URLClassLoader, are recorded separately. Failure never falls back to
the ambient CLI, a different selection or normal coordinates.

The result contains supplied resolution records separately from actual JVM
observations, actual output inventory, metadata paths, outcome and diagnostics.
After guards pass, generation uses the existing v1 generation checks, including
target, version, descriptor, ABI, source identity and library/CAR/SAR rules.
Generation failure records a failed result and propagates the error. Preflight
rejection occurs before generation. Existing v1 and ordinary generation retain
their own version contracts.

The v2 request and control fields above define the supplier API schema. The
original sbt-cozy task must qualify a private producer and consumer conforming
to this schema.

The graph is supplied resolution evidence, not an independent Cozy resolution.
The private publication control record is a producer binding and must be supplied
only after the complete batch exists. The original sbt-cozy task remains
responsible for actual private Ivy resolution and child-JVM correlation, real CML
output and consumer compilation, including compatible success, pre-generation
rejection and a distinct actual compiler failure. It also owns atomic complete
multi-module publication with unique non-overwriting coordinates and preserved
cross names/metadata; direct/transitive conflicts and foreign selection; exact
completed-coordinate cache reuse; two isolated consumers; unchanged shared Ivy
publications; and selection A → B → A → detach in the same opted-in build.
Selection changes take effect on a new invocation or supported reload/restart,
not by writing a file into an already running JVM. No-input ordinary behavior
and invalid-input refusal must be retained without cache clearing, global-current
state, persistent session saves or new global locks.

Cozy source commit, native focused validation, independent review and normal local
library publication are separate supplier evidence. The original five-condition
sbt-cozy task continues only after attributable Cozy supply through its existing
public semantic-continuation contract with the actual target, human direction and
upstream evidence. Its delivery, requester adoption and Phase acceptance are not
implied by Cozy API implementation or ordinary local publication.

## `sbt-bridge v3`: multi-artifact generation

Version 3 is a separate `sbt-bridge v3 --request <absolute-file>` fixed action;
versions 1 and 2 retain their existing meanings. The action is
`generate-development`, and its only commands are `modeler-scala` and
`modeler-scala-value` with source and `--save` output. Existing target,
component, source identity, CAR/SAR, descriptor and compatibility guards still
apply. There is no arbitrary executable, main class or environment input.

The v3 JSON records use the following exact field names. Arrays preserve order;
optional fields are JSON null or absent, and maps are JSON objects of strings.

* `ModuleV3`: `organization`, `name`, `requestedVersion`, `selectedVersion`,
  `baseVersion`, `development`, `descriptor`, `control`. Its identity is
  `organization:name:selectedVersion`. A development alias and base are bound
  explicitly by the completed batch, not derived by removing a suffix.
* `ArtifactKeyV3`: `module` (that identity), `name`, `artifactType`,
  `extension`, `classifier`, `extraAttributes`. `ArtifactV3` has `key`,
  `resolverFile`, `launchFile`, `runtime`. Each artifact retains its own key;
  several keys can belong to one actual module.
* `ReportArtifactV3`: `key`, `file`. `ExclusionV3`: `organization`, `name`,
  `artifact`, `configurations`, `attributes`. `CallerV3`: `from`,
  `sourceRevision`, `configurations`, `attributes`, `explicitArtifacts`,
  `exclusions`, `isForce`, `isChanging`, `isTransitive`, `isDirectlyForce`.
  `ReportArtifactSelectorV3`: `name`, `artifactType`, `extension`, `classifier`
  (optional string), `extraAttributes` (string map). `ReportTargetModuleV3`:
  `organization`, `name`, `revision` (strings), `configurations` (optional
  string), `explicitArtifacts` (array of `ReportArtifactSelectorV3`),
  `exclusions` (array of `ExclusionV3`), `isForce`, `isChanging`,
  `isTransitive` (booleans), `extraAttributes` (string map).
  `EvictionV3`: `evicted`, `selectedRevision`, `details`. `ReportRowV3`:
  `module`, `revision`, `configurations`, `artifacts`, `missingArtifacts`,
  `callers`, `eviction`, `problem`, `attributes`, `targetModule` (required
  `ReportTargetModuleV3`), `resolver` and `artifactResolver` (optional strings).
  `module` is the normalized selected module identity; `revision` and
  `targetModule.revision` preserve the actual reported row revision, including
  an evicted revision. Resolver references are reported data, not proof of
  execution or authority. Empty artifacts on a selected
  row means no artifact was reported; nonempty `missingArtifacts` or a
  nonempty problem is rejected. Evicted rows can retain report-only artifacts.
* `Edge` has `from`, `to`, `requestedVersion`. `ResolutionV3` has
  `producerRunReference`, `requestRoots`, `exactOverrides`,
  `requestedConfigurations`, `reportRows`, `modules`, `artifacts`, `edges`,
  `runtimeClasspath`, `producerJvm`, `resolverConfiguration`,
  `optionalDiagnostics`. Overrides are keyed by `organization:name`.
  `runtimeClasspath` is an ordered array of artifact keys, exactly the runtime
  artifacts, including classified artifacts where selected.
* `PublishedModuleV3`: `organization`, `name`, `selectedVersion`, `baseVersion`,
  `descriptor`, `control`. `PublishedArtifactV3`: `key`, `publicationFile`.
  `BatchMemberV3`: `module`, `artifacts`. `BatchV3`: `scope`, `owner`, `batch`,
  `producerSelectionId`, `complete`, `members`. This immutable record does not
  contain consumer resolver paths, selected runtime flags, or consumer identity.
* `RequestV3`: `version`, `action`, `selection`, `roots`, `resolution`,
  `batchRecord`, `arguments`, `settings`. `selection` has `scope`, `owner`,
  `selectionId`, `batch`, `publicationRoot`; `roots` has `build`, `owner`,
  `source`, `output`, `result`.
* `ResultV3`: `version`, `outcome`, `selection`, `receivedResolution`,
  `observation`, `generatedFiles`, `metadataFiles`, `diagnostics`.
  `ObservationV3`: `osName`, `osArch`, `javaVersion`, `javaVendor`, `classpath`,
  `loaderUrls`, `cozy`, `simpleModeler`, `cozyVersion`,
  `requiredSimpleModelerVersion`, `simpleModelerVersion`. Each origin has
  `className`, `codeSource`, `resource`.

For example, one module can have these two separate classpath keys:

```json
{"module":"org.openjfx:javafx-graphics:21","name":"javafx-graphics","artifactType":"jar","extension":"jar","classifier":null,"extraAttributes":{}}
{"module":"org.openjfx:javafx-graphics:21","name":"javafx-graphics","artifactType":"jar","extension":"jar","classifier":"mac-aarch64","extraAttributes":{}}
```

A completed member's control file contains exactly the batch values `scope`,
`owner`, `producerSelectionId`, `batch`, `complete: true`, plus its `module`
and complete `artifacts` array in the forms above. All batch members and all
published artifacts, including members outside the selected closure and artifacts
outside the runtime classpath, must exist. The canonical publication file is
`<publicationRoot>/<organization>/<name>/<selectedVersion>/jars/<artifactName>[-classifier].<extension>`;
the descriptor is at that revision's `ivys/ivy.xml`. Descriptor coordinates and
artifact identities including extra attributes must match the member record.
The selected development artifact must match a published key and path, and the
current resolver file must have the same bytes. Old producer resolver cache files
are not batch members or prerequisites for reuse. The control and batch bind the
producer selection independently of the consumer's `selectionId`.

The fixed roots are exactly Cozy and SimpleModeler. The supplied graph must bind
all nodes, edges and selected artifacts to report rows and be reachable from
those roots. A self-edge is legal. Requested revisions are retained separately
from selected revisions; a development request must bind to the batch's explicit
base or alias; the report need not contain a separate row for the requested base.
An edge requesting the base can match the reported selected alias only when an
exact override selects that alias and the completed development publication
binds that base and alias. Other requested revisions must match a reported row.
Exact overrides must agree with selected coordinates. Paths,
complete batches, foreign ownership, actual JVM classpath and loaded origin,
resource uniqueness and BuildInfo/backend versions are checked before fixed
generation; a refusal never falls back to ordinary artifacts.

The trusted producer constructs this value from the same successful generator
SBT public UpdateReport and retains the actual in-memory value through child
reconciliation. A JSON field, stored report, run reference or echo is supplied
data and does not prove an SBT execution. Cozy checks structural and execution
facts; it does not reimplement Maven effective-model or profile resolution.
Applied profile traces and complete effective POMs may be explicitly unavailable
and are not gates. Actual JVM OS/architecture/JDK, selected classifier files and
observed origins do not claim an unobserved profile ID or another platform.

Supplier fixture graphs are test-supplied and do not qualify a real producer.
After this supply, the original adapter must still qualify real private-Ivy
multi-artifact atomic batches and public report projection, complete closure,
actual child generation and compiler positive and negative cases, two consumers,
A-to-B-to-A-to-detach across invocations, exact cache reuse, no ordinary-baseline
prerequisite, unchanged shared Ivy publications and normal attributable delivery.
All original five ordered adapter conditions and nine shared conditions remain
unchanged; supplier results do not establish their completion.

A concrete control record for one published artifact is shown below. The batch
file wraps the same `module` and `artifacts` in a `members` array and repeats
`scope`, `owner`, `batch`, `producerSelectionId`, and `complete`. Paths and
coordinates are examples and must refer to the actual completed publication.

```json
{
  "scope": "work", "owner": "owner", "producerSelectionId": "build-A",
  "batch": "batch-A", "complete": true,
  "module": {
    "organization": "example", "name": "library_2.12",
    "selectedVersion": "private-A", "baseVersion": "1.0",
    "descriptor": "/owner/repo/example/library_2.12/private-A/ivys/ivy.xml",
    "control": "/owner/repo/example/library_2.12/private-A/control.json"
  },
  "artifacts": [{
    "key": {"module":"example:library_2.12:private-A", "name":"library_2.12",
      "artifactType":"jar", "extension":"jar", "classifier":null, "extraAttributes":{}},
    "publicationFile":"/owner/repo/example/library_2.12/private-A/jars/library_2.12.jar"
  }]
}
```

For a selected artifact the corresponding request record contains that same
`key` and `launchFile`, plus the actual current `resolverFile` and `runtime`.
A report row for that selection has, for example, `revision: "private-A"`,
`eviction: {"evicted":false,"selectedRevision":null,"details":[]}`,
`artifacts: [{"key": <the key above>, "file": <actual resolver file>}]`,
`missingArtifacts: []`, `problem: null`, and its actual caller records. Its
required `targetModule` records the reported organization, name, revision,
optional configurations, explicit artifact selectors, exclusions, dependency
flags and extra attributes; `resolver` and `artifactResolver` hold optional
reported references. A row
with `evicted: true` retains its reported revision and points to the selected
revision without requiring its old artifact file in the runtime classpath.
