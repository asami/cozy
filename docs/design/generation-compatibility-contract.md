# Generation Compatibility Contract

The Cozy generation boundary owns admission of the exact pair used to generate
Scala source. The pair is independent of CAR runtime activation:

- the CNCF coordinate is the exact target used to compile generated Scala;
- the Cozy coordinate is the exact generator executing generation; and
- `packaging.car.runtime.cncf` is an independent runtime compatibility range.

Admission is typed and evidence-based for immutable coordinates. The
machine-readable `cozy.generation-compatibility.v1` evidence is persistent
immutable-only: every evidence pair must use immutable coordinates, and a
mutable or SNAPSHOT entry is malformed. An immutable pair is `Supported` only
when evidence identifies the exact coordinates and marks the pair `proven`. A
known pair marked `unproven` is `Incompatible`, as is a pair marked
`incompatible`; an absent coordinate is `Unsupported`, while individually known
coordinates without a pair record are an incompatible pair. CNCF-only,
Cozy-only, and both-unsupported diagnostics remain distinct.
Version-number equality or similarity never supplies proof. The current
checked-in `0.5.1`/`0.3.0` observation is explicitly unproven.

Project contract, owning-build bridge, and CLI are explicit sources. Every
present explicit source must agree; disagreement returns a typed contradiction
diagnostic. When they agree, provenance is selected by project contract,
owning-build bridge, then CLI. The published default is non-explicit fallback,
used only when no explicit source exists and ignored whenever one does, even
when its value differs. Environment variables and stale fallbacks are not
release-generation sources. An explicit SNAPSHOT pair is admitted for
development without persistent-pair registration, while release generation
requires an immutable evidence-proven pair. The executing Cozy version must
equal the selected exact generator coordinate; provenance plus compile and test
results supplies development evidence. The resource loader validates the exact
schema, owner/location, coordinate fields, immutable-only evidence pairs, pair
uniqueness, and status semantics before admission; malformed evidence cannot
produce `Supported`.

For sbt-cozy runtime selection, the CNCF compile version is authoritative when
no Cozy override is declared. The launcher runtime catalog projects only
proven compatible pairs from Cozy generation evidence and selects the highest
numeric Cozy version for that CNCF version. This selector is distinct from the
global latest Cozy runtime and from the single published default pair. Explicit
project or build Cozy versions and an explicit development checkout override
that default; contradictory explicit versions fail. The exact selected version
is still required at the generation and package admission boundaries.

Evidence owner and location are returned with every admission decision. Cozy is
the generation owner, and Cozy is not a CAR runtime dependency. CV-02 owns
pure admission of missing or unsupported coordinates, unproven or incompatible
pairs, source contradiction, and development/release lifecycle. CV-03 owns
resolving and wiring explicit inputs at the actual generation invocation,
including rejecting absent or contradictory invocation sources. CV-04 owns
runtime descriptor target/schema/digest validation only.

CV-06A makes the unmerged CAR `project.yaml` the package-gate authority for
an optional `build.cozyVersion`, the unique CNCF compile coordinate, and the independent
runtime range. `CarMetadataCompatibility` returns typed diagnostics for missing
or contradictory project values, while `CozyArchivePackager` requires exactly
one resolved CNCF JAR descriptor whose runtime/module/root-version identity
matches the compile dependency. The accepted exact version drives runtime
range admission without consulting merged operation defaults a second time.

CV-06B splits that evaluator into a project-only decision and the package-only
resolved-artifact extension. Integrated CAR lint and the Review Provider expose
the project-only decision with the same diagnostic codes. Publication runs the
same decision before repository writes and projects catalog runtime metadata
from its accepted contract rather than merged operation defaults. Resolved-JAR
identity remains owned by package admission.

CV-07 makes lifecycle a property of the actual generation or publication
boundary. A CAR command derives its output lifecycle from the project component
version, and `publish-car --version` must equal that version before repository
writes. `car-sbt-project` requires its explicit output version, while CNCF
value generation uses the selected CNCF target as its output version. Mutable
generation pairs are admitted only for mutable output; immutable generated
output requires a proven immutable pair and valid provenance.

For CAR/SAR sbt builds, unmerged `project.yaml` must own exactly one CNCF
compile dependency. An optional exact Cozy override may be in the project or
owning build; otherwise the launcher resolves the newest proven generator for
that CNCF target. sbt-cozy does not substitute its own plugin version or an
ambient generator. When the selected generator cannot be launched, the failure
names its CNCF or Cozy coordinate and gives the publish/select recovery action. Exact CAR dependency
archives are resolved independently of source-project builds.

CV-06C1 carries generation metadata into the CAR as top-level
`generation-provenance.json`. Package admission validates known versions and
canonical declarations, actual selected source readability and generated Scala
path inventory, and the accepted CNCF/Cozy pair. It copies the accepted metadata
snapshot unchanged to the CAR; this source-to-package byte transfer retains its
distribution owner. Invalid metadata or contradictory inputs fail before
archive writing. Nongenerated and legacy provenance absence remains allowed,
and `src/main/car/generation-provenance.json` remains reserved.

Cozy owns both existing provenance schemas and their validation. Direct isolated
generation writes v1 with `schemaVersion`, `target{cncfVersion}`,
`generator{cozyVersion,simpleModelerBackendVersion,simpleModelingModelVersion}`,
`source{identity}`, and `output{artifacts:[{path}]}`. The aggregate writes v2 with
`sources:[{identity,output:{artifacts:[{path}]}}]` in place of `source`.
Both emit only these known fields. Ordinary JSON loading ignores unknown extras
of any type at every object level; it retains strict known fields and types,
nonempty exact versions, canonical safe relative source/output paths, unique
ordered claims, and v2 source/output union. Historical extras supply no content
claim, special decoder, or compatibility hash adapter.

The selected source is `SourceInput(path, identity)`, a normalized actual readable
regular-file reference. The producer checks access by opening and closing the
stream without capturing content or time. The modeler and CML metadata read the
actual selected path directly. The provenance stage checks its source identity
and readability independently. Generated Scala enumeration follows the existing
`target`/delegate-exclusion rules and opens/closes each selected regular file.
Validation compares only declared and actual path inventories. Readable source
and generated content changes alone are valid; missing, added, removed, or
unreadable required paths remain genuine failures.

The aggregate accepts a nonempty explicit list of delegated manifest/output-root
pairs, validates every v1 pair, rejects duplicate canonical source identities
and target/generator disagreement, and verifies the final installed path set
excluding delegated work. It sorts sources by identity and records the sorted
path union. Shared output paths deduplicate independently of different readable
contents. No order-based source winner or replacement ownership certificate is
introduced. Empty or malformed inputs, unsafe paths, genuine access failures,
missing evidence, and contradictory source/top-level unions remain typed
failures. Stable source/output-tampered codes describe actual access and path
inventory failures; hash-only evidence-tamper and output-content-conflict checks
are retired.

Both producers validate their stage before creating/replacing final metadata,
then validate temporary JSON and publish by atomic replacement with temporary
cleanup. Failure preserves previous successful metadata. CLI preflight also
preserves that metadata by removing eager deletion. The backend's source output
installation is not redesigned into a new transaction. Packaged-only validation
uses `requireValidPackagedMetadata` and aggregate `validatePackagedMetadata`
without local source access or digest calculations.

Historically, Phase 63 froze and implemented the Cozy aggregate/rebind API, preserving the
legacy single-rebind API as a wrapper or one-source aggregate equivalent. It
does not change CozySbtBridge request grammar, sbt-cozy collection,
generated-side installation, incremental state, plugin behavior, or a
downstream driver. Those transport/orchestration responsibilities were assigned
to Phase 63.1, whose owning build may invoke the settled Cozy API, delete
delegate work after successful rebind, and require an installed final manifest
before incremental reuse.

The packaged provenance remains metadata. CV-06C2 separately owns CNCF runtime
range, ABI, and archive-integrity admission and must not load Cozy or use
generator compatibility to decide runtime activation.

Cozy materializes the CNCF-owned runtime evidence as top-level
`car-runtime-manifest.json` with schema
`cncf.car-runtime-manifest.v1`. It carries the CAR name/version/component,
the accepted `packaging.car.runtime.cncf` minimum, optional maximum, excluded
and tested versions, plus a sorted SHA-256 inventory of every other regular
entry in the completed archive staging tree. `src/main/car` cannot provide this
reserved document. CNCF validates this range, the matching
`cozy.car.abi-manifest.v1` sidecar, and the exact archive file set and digests
before packaged component discovery/classloading. Generation provenance
remains opaque bytes at that boundary.

The descriptor
contract is selected when either `--cncf-runtime-descriptor` or
`--cncf-runtime-descriptor-sha256` is present; Cozy then requires the exact
target, descriptor path, and digest together. CLI preflight and the sbt bridge
invoke one validator before source generation. The validator checks file
existence, SHA-256, root schema `1`, runtime identity `cncf`, exact target
version, and predefined Result schema `cncf.predefined-result.v1`.

Diagnostics use a typed code and deterministic `code`, `source`, `expected`,
`actual`, `message`, and `correctiveAction` fields. Missing, unreadable, and
malformed descriptor inputs remain distinguishable, as do invalid digest syntax
and digest mismatch. The sbt bridge preserves both
`runtime.cncf.descriptor` and `runtime.cncf.descriptor.sha256` from the owning
build. Project defaults, owning-build bridge settings, and request arguments
must agree for every descriptor-contract value; conflicting values fail with a
typed source-conflict diagnostic instead of applying override precedence.
Ambient global `~/.cozy` operation defaults are not a generation source and
are excluded from sbt-bridge generation resolution.
Version-only legacy commands remain outside the selected descriptor contract.
When the complete descriptor contract selects CNCF-aware generation, Cozy writes
the v1 nonhash metadata after Scala and model metadata generation. The exact
CNCF/Cozy/backend/model versions and canonical source/output declarations are
local descriptive contracts. They are not content immutability or historical
currentness certificates.

Descriptor validation retains one immutable distributed byte snapshot for
actual pin checking, schema/target validation, predefined Result catalog
construction and component-style resources. Those G03 byte-integrity consumers
continue to use `ValidatedDescriptor`; local provenance no longer records its
digest. CNCF-aware generation still requires `--generation-source-identity` as
a canonical project-relative logical path. The bridge derives it only when the
source is within explicit `sbt.project_dir`; otherwise the owning build provides
`generation.source.identity`.

CV-05A owns the producer/validator. The `generation-provenance-validate` CLI takes
the actual model path, output root, selected CNCF target, Cozy coordinate, source
identity and optional model version. It derives the compiled backend and current
default model versions, checks exact inputs and actual source/output access, and
prints the successful path without an evidence token. Source/descriptor digest
arguments are not metadata-validator inputs. Generation descriptor preflight
continues to require its exact distributed pin independently.

CV-05B owns external CNCF build adoption. The historical development build used
Cozy `0.3.2-SNAPSHOT` with explicit source identity and validator invocation;
that history does not assert a current downstream migration. External build and
sbt-cozy owners must adopt the settled nonhash projection/current CLI inputs in
their own repositories. This bounded Cozy implementation preserves those
ownership edges and does not claim their validation or Phase acceptance.
