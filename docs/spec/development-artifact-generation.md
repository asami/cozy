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
