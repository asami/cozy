# Generation Compatibility Specification

Given exact Maven coordinates, explicit source values, lifecycle, and typed
compatibility evidence, When Cozy resolves and admits generation, Then it
returns a structured `Supported`, `Unsupported`, or `Incompatible` result with
diagnostics and evidence owner/location.

The evidence shape is `cozy.generation-compatibility.v1` with exactly
`schema`, `evidenceOwner`, `pairs`, and `publishedDefault` fields. Each pair
contains structured exact immutable CNCF and Cozy coordinates and one of
`proven`, `unproven`, or `incompatible` statuses. Persistent evidence is
immutable-only: a mutable or SNAPSHOT entry is malformed. `publishedDefault`
is either `null` or one exact immutable pair coordinate already present in
`pairs` with `proven` status; it is used only when no project, owning-build, or
CLI source selects a pair. The packaged
0.5.1/0.3.0 pair is `unproven`. Runtime minimum/maximum/excluded/tested values
are separate CAR metadata and are not used to infer generation compatibility.

The production loader validates schema version, exact field sets, non-empty
owner/location and coordinates, duplicate/conflicting pair records, and status
semantics, including immutable-only persistent coordinates. It returns typed deterministic diagnostics with source/expected/
actual/coordinate context. The admission API distinguishes missing CNCF,
missing Cozy, both missing, unsupported coordinates, unsupported pairs, known
unproven pairs, and explicitly incompatible pairs.

For a CAR project, the unmerged `project.yaml` must supply one non-empty
`build.cozyVersion`, exactly one CNCF compile dependency, and a runtime
compatibility declaration with `minimum` and a non-empty `tested` set. The
compile target must be within the optional maximum, absent from `excluded`, and
present in `tested`.

Package admission must resolve exactly one CNCF descriptor from the actual
input JARs. Its runtime must be `cncf`; its module organization, artifact, and
version plus its root descriptor version must identify the exact compile
dependency. Operation defaults must not replace project-owned compatibility
values or resolved-artifact evidence. Every contradiction must fail with a
typed deterministic diagnostic before archive acceptance.

Review and publication must reuse the project-only portion of this decision.
Integrated CAR lint and the Review Provider must report an accepted contract
or the same typed project diagnostic as packaging. Publication must reject an
invalid declared CAR before repository output and must derive the accepted
catalog runtime range from that contract, not merged operation defaults.
Resolved-JAR identity remains a package-only requirement.

An actual CAR generation or publication command must derive lifecycle from the
project component version. `publish-car --version` must equal that exact
version. `car-sbt-project` must provide its output version explicitly, and
CNCF value generation must use its selected CNCF target as output version.
Mutable generation coordinates must not produce immutable output.

An explicit mutable SNAPSHOT pair is admitted for development without
pre-registration in persistent evidence; the exact executing Cozy version must
equal the selected generator coordinate. Its provenance together with compile
and test results supplies development evidence. Release admission remains
immutable and requires an exact evidence-proven pair; version similarity and
fallback inference never supply proof.

For CAR/SAR generation, sbt-cozy must reject a project missing
`build.cozyVersion`, missing its CNCF compile dependency, or declaring more
than one CNCF compile dependency declaration. Equivalent aliases or duplicate
declarations of the same version remain multiple inputs and must be rejected,
not collapsed. The exact project pair must be passed to the delegate and
incremental state. An unavailable delegate must identify the exact Cozy
coordinate and the recovery action. Resolving an exact CAR dependency archive
must not require loading, generating, or publishing that dependency's source
project. A flat SBT test runtime may extract the CAR's implementation JARs and
resolve only its `component-dependencies.yaml` `dependencies.local`
coordinates; `shared` and `provided` dependencies retain their runtime
ownership.

When `target/cozy/generation-provenance.json` exists, package admission must
validate it against its recorded source and generated Scala artifacts, verify
its aggregate and evidence digests, and require its CNCF target and Cozy
generator to equal the accepted project contract. A successful package must
copy the bytes unchanged to the top-level CAR entry
`generation-provenance.json`. Any validation or contract mismatch must fail
before the archive is written. Validation and archive writing must consume one
immutable byte snapshot. Provenance absence must remain permitted for
non-generated and legacy CAR sources. The generic CAR source path
`src/main/car/generation-provenance.json` is reserved and must be rejected
rather than packaged without target validation.

For sbt-cozy generation, each isolated Cozy run first writes and validates its
own manifest. After sbt-cozy installs the generated Scala files, it must invoke
Cozy's `rebind-generation-provenance` bridge action. Cozy must validate the
exact delegated manifest path, recompute project-relative evidence while
excluding the disposable delegate-work root, and atomically publish
`target/cozy/generation-provenance.json`. sbt-cozy removes delegate work only
after successful rebinding and requires the installed manifest before
incremental reuse. Schema v1 carries one CML source identity, so multiple
delegated v1 manifests for one CAR must fail explicitly rather than select one
arbitrarily.

Runtime activation must not import Cozy or evaluate generation provenance.
CNCF runtime range, ABI, and archive-integrity admission remain the independent
CV-06C2 runtime contract.

For an accepted CAR project contract, packaging must create top-level
`car-runtime-manifest.json` with schema
`cncf.car-runtime-manifest.v1`. The document must preserve the CAR
name/version/component and accepted CNCF minimum, optional maximum, excluded,
and tested values, declare `SHA-256`, and list every other regular file in the
completed staging tree exactly once with a lowercase 64-hex digest. Cozy must
reject `src/main/car/car-runtime-manifest.json` rather than package
source-managed runtime evidence.

CNCF packaged-CAR extraction must validate the coordinate, range, supported
ABI sidecar and component export, exact file set, and digests before discovery
or classloading. `tested` must remain non-empty evidence but not an allowlist.
CAR-style development directories remain outside packaged archive-integrity
admission. CNCF may hash `generation-provenance.json` as opaque archive bytes,
but it must not interpret that schema or load Cozy.

Cozy publication of a declared prebuilt CAR must independently revalidate the
same package-generated runtime manifest before repository writes. Its CAR
name/version/component, accepted runtime range, exact regular-file set, and all
SHA-256 digests must agree. A generated immutable release must also contain
generation provenance whose bytes equal an immutable snapshot of
`target/cozy/generation-provenance.json` after validation against the owning
project's current source, generated Scala artifacts, exact CNCF/Cozy pair,
aggregate output digest, and evidence digest. Publication must snapshot the
prebuilt CAR before admission and publish that same snapshot, rejecting
arbitrary, stale, concurrently replaced, or post-package-modified archives
rather than trusting the `--car` path.

CV-03 owns CNCF generation invocation wiring and rejects absent or contradictory
invocation sources before generation. CV-04 owns runtime descriptor
target/schema/digest validation. When a generation invocation selects a
descriptor or descriptor digest, it must provide `--cncf-version`,
`--cncf-runtime-descriptor`, and
`--cncf-runtime-descriptor-sha256` together. Before source emission, Cozy must:

- reject a missing, unreadable, or malformed descriptor;
- require root schema `1` and runtime identity `cncf`;
- require the descriptor version to equal the selected CNCF target exactly;
- require predefined Result schema `cncf.predefined-result.v1`; and
- require the supplied lowercase hexadecimal SHA-256 to equal the descriptor
  bytes.

CLI and sbt-bridge entry points use the same validator and deterministic
diagnostic shape: code, source, expected, actual, message, and corrective
action. Repeated or cross-source CNCF target, descriptor path, and descriptor
digest values must agree exactly. Project defaults, owning-build bridge
settings, and request arguments that disagree are rejected with a typed
source-conflict diagnostic before delegated generation. Global `~/.cozy`
operation defaults must not participate in generation version or descriptor
resolution. The bridge forwards the descriptor digest extracted by sbt-cozy.
For a generation request containing the complete CNCF descriptor contract,
successful Scala emission must also write
`target/cozy/generation-provenance.json` with schema
`cozy.generation-provenance.v1`. The metadata, rather than generated Scala,
must carry:

- the exact CNCF target and runtime-descriptor SHA-256;
- the executing Cozy version, compiled simple-modeler backend version, and
  selected `simplemodeling-model` version;
- an explicitly selected, non-empty project-relative CML identity and digest;
- sorted project-output-relative Scala identities and individual digests;
- a deterministic aggregate generated-output digest; and
- a deterministic evidence digest over all preceding provenance fields.

Cold and repeated generation with identical logical inputs and bytes must
produce byte-identical provenance even when output roots differ. Absolute
paths, timestamps, filesystem traversal order, and other machine-local state
must not enter reproducibility-critical evidence. Validation must reject
unsupported or malformed schemas, expected-input contradictions, changed CML
bytes, changed generated file identity or bytes, and provenance fields that no
longer match their evidence digest.

Descriptor bytes must be read once for digest calculation, descriptor
validation, predefined Result catalog construction, and provenance identity.
Replacing the descriptor path after validation must not change the catalog or
the digest recorded for that generation. Missing or unreadable CML/output
evidence and invalid expected inputs must remain typed `Either` diagnostics
rather than escape as raw filesystem or argument exceptions.

The CML identity and bytes must be captured before source generation. Absolute,
parent-escaping, and Windows drive-prefixed logical identities must be rejected.
The modeler and CML model-metadata producer must consume an isolated
materialization of the captured bytes rather than re-read the mutable source
path. Before provenance is committed, Cozy must verify that the original source
path still resolves to the captured bytes. Source replacement, removal, or
unreadability during generation must reject provenance rather than associate
generated output with a later source revision. Existing provenance must be
cleared before generation, and replacement provenance must become visible only
through an atomic move after complete validation succeeds.

CV-05A owns the Cozy producer and validator. The public direct CLI command
`generation-provenance-validate <model-file> --save
<generation-output-root>` must require the expected CNCF target, runtime
descriptor digest, Cozy generator coordinate, project-relative source
identity, and pre-launch source SHA-256. It must apply the same typed
manifest/source/artifact/output/evidence validation as the production writer
and fail on any expected-input contradiction.

CV-05B invokes that command at the CNCF build boundary after generation and
before generated Scala is accepted. The same resolved source identity and
digest must feed generation and validation, and cold/repeated determinism must
compare provenance bytes as well as every generated Scala identity and digest.
CV-06 owns scaffold/package compile-runtime consistency.
