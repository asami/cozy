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

Evidence owner and location are returned with every admission decision. Cozy is
the generation owner, and Cozy is not a CAR runtime dependency. CV-02 owns
pure admission of missing or unsupported coordinates, unproven or incompatible
pairs, source contradiction, and development/release lifecycle. CV-03 owns
resolving and wiring explicit inputs at the actual generation invocation,
including rejecting absent or contradictory invocation sources. CV-04 owns
runtime descriptor target/schema/digest validation only.

CV-06A makes the unmerged CAR `project.yaml` the package-gate authority for
`build.cozyVersion`, the unique CNCF compile coordinate, and the independent
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

For CAR/SAR sbt builds, unmerged `project.yaml` must own both exact generation
coordinates: `build.cozyVersion` and exactly one CNCF compile dependency.
sbt-cozy does not substitute its own plugin version or an ambient generator.
When the exact Cozy generator cannot be launched, the failure names its
coordinate and gives the publish/select recovery action. Exact CAR dependency
archives are resolved independently of source-project builds.

CV-06C1 carries validated generation provenance into the CAR as the top-level
`generation-provenance.json` entry. When
`target/cozy/generation-provenance.json` exists, package admission reruns the
authoritative source, generated-artifact, aggregate-output, and evidence-digest
validation. It additionally requires the recorded CNCF target and Cozy
generator to equal the accepted CAR project contract. Invalid or contradictory
provenance fails before archive creation. Validation and archive writing use
one immutable byte snapshot, so a later source-path replacement cannot change
the packaged evidence. A CAR without generated provenance remains valid for
non-generated and legacy sources. `src/main/car/generation-provenance.json`
is a reserved generic source name and cannot substitute for the validated
target artifact.

Cozy remains the schema, aggregation, validation, and digest-evidence owner.
Direct isolated generation continues to publish the one-source
`cozy.generation-provenance.v1` format and package admission continues to
read it for existing one-CML projects. The project aggregate/rebind API is the
separate V2 producer: it accepts a non-empty, explicit list of `(delegated
manifest path, delegated output root)` pairs and publishes
`cozy.generation-provenance.v2`. A single accepted delegated v1 pair is a
valid V2 aggregate input and needs no migration; direct v1 generation and
legacy v1 package admission remain supported.

The V2 representation preserves one exact project target and generator
identity, followed by a `sources` list sorted by canonical project-relative
source identity. Every source records that identity and its digest, the
accepted delegated source-output claims, and the delegated v1 evidence digest.
The top-level output is the deterministic sorted union of
project-output-relative artifact path/digest claims, with its deterministic
output digest. Its evidence digest covers every preceding V2 field. Identity
comes only from the canonical project-relative source representation; source
and output digests are integrity evidence and must neither derive identity nor
control source selection.

The aggregate/rebind API validates every selected delegated v1 input before
V2 publication. It rejects a missing or unreadable manifest/output root,
malformed evidence, duplicate or ambiguous canonical source identity, stale
source/output, inconsistent expected target/generator, and internally
contradictory evidence before it writes V2. The deterministic typed boundary
uses `GENERATION_PROVENANCE_MISSING`,
`GENERATION_PROVENANCE_MALFORMED`,
`GENERATION_PROVENANCE_SOURCE_TAMPERED` (or the existing output-tampered
equivalent), `GENERATION_PROVENANCE_INPUT_MISMATCH`, and
`GENERATION_PROVENANCE_EVIDENCE_TAMPERED` respectively. It adds
`GENERATION_PROVENANCE_SOURCE_AMBIGUOUS` for duplicate or ambiguous canonical
source identity. No enumeration order, input position, or digest is a fallback
selection rule.

Distinct accepted sources claiming the same project-output-relative path and
digest contribute one V2 artifact claim. The same path with differing digests
is rejected before publication as
`GENERATION_PROVENANCE_OUTPUT_CONFLICT`; no source wins by order. Cozy writes
the fully validated V2 bytes at a temporary path and atomically replaces the
project target only after the whole aggregate succeeds. Package admission reads
and validates either v1 or V2 while preserving its one immutable snapshot
before archive writing.

Phase 63 freezes and implements the Cozy aggregate/rebind API, preserving the
legacy single-rebind API as a wrapper or one-source aggregate equivalent. It
does not change CozySbtBridge request grammar, sbt-cozy collection,
generated-side installation, incremental state, plugin behavior, or a
downstream driver. Those transport/orchestration responsibilities belong
exclusively to Phase 63.1, which may invoke the settled Cozy API, delete
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
When the complete descriptor contract selects CNCF-aware generation, Cozy
writes `target/cozy/generation-provenance.json` after Scala generation and
other model metadata. The packaged metadata uses
`cozy.generation-provenance.v1`; generated Scala remains free of embedded
provenance. The document records the exact CNCF target, descriptor SHA-256,
executing Cozy version, compiled simple-modeler backend version, selected
`simplemodeling-model` version, stable project-relative CML identity and
SHA-256, and a sorted relative identity/SHA-256 entry for every generated Scala
file.

The generated-output digest hashes the ordered relative-path and file-digest
pairs. The evidence digest hashes the canonical provenance payload before the
evidence-digest field is added. Neither digest includes an absolute path,
timestamp, output-root identity, filesystem traversal order, or provenance
file itself. Validation recomputes source, artifact, aggregate-output, and
evidence digests and compares every recorded generation input with the owning
build's expected contract. A malformed schema, changed source, changed artifact
set or bytes, contradictory input, or changed provenance field is rejected
with a typed deterministic diagnostic.

Descriptor validation reads one immutable byte snapshot. Digest calculation,
schema/target validation, predefined Result catalog construction, and
provenance all consume the resulting `ValidatedDescriptor`; generation never
re-reads the path without the owning-build digest or copies an unvalidated raw
digest into evidence. CNCF-aware CLI generation requires
`--generation-source-identity` with a non-empty canonical project-relative
path. Absolute, parent-escaping, and Windows drive-prefixed identities are
rejected. The sbt bridge derives the same identity only when the source is
contained by its explicit `sbt.project_dir`; otherwise its owning build must
provide `generation.source.identity`.

Cozy captures the normalized CML identity and bytes before invoking the
modeler, computes SHA-256 from that one capture, and makes the modeler and CML
model-metadata producer consume an isolated materialization of the captured
bytes instead of re-reading the mutable source path. Provenance is written only
when the original source path still contains those exact bytes after generation;
a changed, removed, or unreadable source fails with typed source-tamper
evidence. A stale manifest is removed before generation, and a new manifest is
validated at a temporary path before an atomic replacement publishes it. The
manifest therefore binds the actual generation bytes and cannot remain visible
from a failed provenance validation.

CV-05A owns this Cozy producer/validator core. CNCF adoption of the schema at
its build boundary is CV-05B. Cozy exposes the same authoritative validator as
the `generation-provenance-validate` CLI command. Its caller must supply the
source file, generation output root, CNCF target, descriptor digest, executing
Cozy coordinate, stable source identity, and pre-launch source digest; the
command recomputes and validates the complete manifest rather than accepting a
build-local reconstruction of evidence.

The CNCF development build pins published-local Cozy `0.3.2-SNAPSHOT`, passes
its project-relative CML identity during generation, and invokes this validator
before accepting generated Scala. This explicit development SNAPSHOT pair
requires no persistent SNAPSHOT-pair registration and does not claim a
released-generator result. CV-05 does not defer descriptor preflight integrity.
