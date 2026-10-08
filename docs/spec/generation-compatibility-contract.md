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

For a CAR project, the unmerged `project.yaml` must supply exactly one CNCF
compile dependency and a runtime
compatibility declaration with `minimum` and a non-empty `tested` set. The
compile target must be within the optional maximum, absent from `excluded`, and
present in `tested`.

`build.cozyVersion` is an optional exact generator override. The owning SBT
build may also set an exact Cozy version; conflicting explicit sources fail.
An explicit project-local development checkout is an alternative override and
must identify its exact version before execution. When no override exists,
the launcher selects the highest version among active, proven Cozy runtimes
listed for the exact CNCF compile target in the runtime catalog. Compare
numeric version segments, not publication time or lexical strings. An absent
mapping fails closed; an unproven or merely published Cozy runtime is not a
candidate. Mutable/SNAPSHOT generation requires an explicit version or
checkout when no proven default exists. The resolved exact generator version
must be bound to generation and package evidence; a changed resolution cannot
reuse stale generated output.

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

For CAR/SAR generation, sbt-cozy must reject a project missing its CNCF
compile dependency or declaring more
than one CNCF compile dependency declaration. Equivalent aliases or duplicate
declarations of the same version remain multiple inputs and must be rejected,
not collapsed. An explicit Cozy coordinate must be passed to the delegate and
incremental state. Without one, the exact CNCF coordinate must reach the
launcher for catalog selection and generated output must not be reused before
the resolved Cozy coordinate is known. An unavailable delegate must identify
the exact requested CNCF or Cozy coordinate and the recovery action. Resolving an exact CAR dependency archive
must not require loading, generating, or publishing that dependency's source
project. A flat SBT test runtime may extract the CAR's implementation JARs and
resolve only its `component-dependencies.yaml` `dependencies.local`
coordinates; `shared` and `provided` dependencies retain their runtime
ownership.

When `target/cozy/generation-provenance.json` exists, package admission validates
its known versions, canonical source identities, actual selected source
readability, and actual generated Scala path inventory against the accepted
project contract. Direct `cozy.generation-provenance.v1` and project aggregate
`cozy.generation-provenance.v2` remain supported at the existing path.
A successful package copies the accepted metadata bytes unchanged into the
CAR's top-level `generation-provenance.json`. This is an actual source-to-package
distribution transfer; its existing byte snapshot remains required. Validation
or a project-contract mismatch fails before archive writing. Provenance absence
remains permitted for nongenerated and legacy CAR sources. The generic source
path `src/main/car/generation-provenance.json` is reserved and rejected.

Both schemas contain exactly the emitted known projection:

- `schemaVersion` identifies v1 or v2;
- `target` contains `cncfVersion`;
- `generator` contains `cozyVersion`, `simpleModelerBackendVersion`, and
  `simpleModelingModelVersion`;
- v1 `source` contains `identity`, while v2 `sources` is a sorted nonempty
  unique list of `{identity, output:{artifacts:[{path}]}}`;
- `output` contains a nonempty sorted unique `artifacts:[{path}]` inventory.

Ordinary object loading ignores arbitrary extra fields and values at every
object level, including historical hash properties, and never re-emits them.
Known required fields and object/array-entry types remain strict. Missing or
unsupported schema, malformed known fields, empty selected versions, unsafe or
noncanonical paths, duplicate claims, and an unequal v2 source/top-level output
union fail with typed diagnostics. No hash-specific migration codec is used.

`SourceInput(path, identity)` is a normalized selected readable regular source
reference. Source selection remains explicit. The source is opened and closed
to surface actual access failures without capturing bytes, a timestamp, or an
immutability certificate. Generation and CML model metadata read that actual
source directly. The provenance write independently requires source readability
and agreement with the selected canonical identity at its own stage. Readable
source or output contents may change without a historical-content rejection.

Direct generation enumerates regular Scala paths under `target`, opens and
closes each selected file, and records the sorted relative path inventory.
Validation requires the same actual inventory; missing, added, removed, or
unreadable required output fails. Source/output contents are not compared.
Versions remain exact normalized nonempty values, and logical source/artifact
paths must be canonical relative paths without absolute, Windows-drive,
parent-escaping, or noncanonical claims.

The aggregate operation alone writes v2. It receives a nonempty explicit list
of `(delegated manifest path, delegated output root)` pairs. Every selected v1
pair is validated against its actual source and delegated output, and all pairs
must share the exact target and generator versions. Canonical source identities
must be unique and are sorted. The final installed Scala inventory is validated
excluding the selected delegate work roots. Shared output paths contribute one
sorted path claim regardless of differing readable contents; no source wins by
order and no content or ownership certificate replaces the removed hashes.
Filesystem enumeration, input position, hashes, and timestamps do not select a
source. A single accepted delegated v1 pair may produce v2, while existing
one-source v1 package admission remains supported.

A missing or unreadable delegated manifest/output root uses
`GENERATION_PROVENANCE_MISSING`; malformed known metadata uses
`GENERATION_PROVENANCE_MALFORMED`; required-source access and actual path
inventory failures retain `GENERATION_PROVENANCE_SOURCE_TAMPERED` and
`GENERATION_PROVENANCE_OUTPUT_TAMPERED` as stable diagnostic codes with text
that describes those access/path failures. Exact-input disagreement uses
`GENERATION_PROVENANCE_INPUT_MISMATCH`; duplicate or ambiguous source identity
uses `GENERATION_PROVENANCE_SOURCE_AMBIGUOUS`. Hash-only evidence-tamper and
output-content-conflict checks are removed.

Both producers complete their stage validation before creating or replacing
the final metadata document. They serialize temporary JSON, validate that
projection, atomically replace the final path, and clean temporary files on
failure. Previous successful metadata survives failed stage validation, and CLI
preflight does not eagerly delete it. This is metadata-output and preflight
failure preservation, with no new transactional backend source installer.
`requireValidPackagedMetadata` and aggregate `validatePackagedMetadata` validate
known schema, versions, canonical source/output declarations, and v2 union
without project source access or digest calculation.

Cozy owns this aggregate/rebind API, including a supported legacy single
rebind wrapper or one-source aggregate equivalent. Phase 63 does not change
CozySbtBridge request grammar, sbt-cozy manifest collection, generated-side
output installation, incremental behavior, plugin integration, or downstream
driver behavior. Those orchestration concerns remain exclusively Phase 63.1;
when it begins, sbt-cozy may call the settled Cozy API and remove delegate work
only after successful rebinding and installed-manifest verification.

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
project's readable selected source, actual generated Scala path inventory, and
exact CNCF/Cozy pair. Readable local source or generated contents alone do not
invalidate an intact CAR. Publication must snapshot the
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
successful Scala emission writes the v1 nonhash projection above after model
metadata production. Descriptor validation reads one immutable distributed byte
snapshot for its pin, schema/target validation, predefined Result catalog, and
component-style catalog. Changing the descriptor path after validation does not
change those accepted resource bytes. G03 descriptor and component-style
resource integrity remain independent actual byte verifiers; provenance emits
and compares no descriptor digest as local identity.

Cold and repeated generation with the same selected versions, canonical source
identity, and generated path inventory emits identical nonhash metadata across
output roots. Absolute paths, timestamps, traversal order, source/output bytes,
and local hash fields do not enter that metadata. Genuine required-source and
output access failures remain typed diagnostics.

CV-05A owns the Cozy producer and validator. The direct CLI is:

```text
generation-provenance-validate <model-file> --save <generation-output-root> --cncf-version <version> --cozy-generator-version <version> --generation-source-identity <project-relative-path> [--simplemodeling-model-version <version>]
```

The command compares the selected CNCF and Cozy versions, compiled backend
version, selected model version (defaulting to the current compiled model
version), and source identity, then validates actual source access and output
paths. It requires no source or descriptor hash argument and prints only the
successful metadata path. It does not replace descriptor preflight validation.

CV-05B owns the CNCF build consumer, including invocation of the current
validator inputs before generated Scala is accepted. External build/plugin
owners must consume the nonhash projection and current CLI inputs through their
own changes; this Cozy migration does not mutate or certify those repositories.
CV-06 retains compile/runtime consistency and A04 package/publication integrity.
