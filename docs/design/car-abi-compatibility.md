# CAR ABI Compatibility

Status: implemented full-surface baseline comparison.

CAR ABI compatibility is a CAR public contract check. It is not a JVM
class-file ABI check. `cozy lint abi` compares a current `abi-manifest.json`
with a previously released CAR ABI manifest and applies SemVer rules.

## Manifest Layout

- `src/main/car/abi-manifest.json` is the source-managed current CAR ABI
  manifest. `package-car` embeds it when present, unless `--abi-manifest`
  explicitly provides another current manifest.
- `src/main/car/<version>/abi-manifest.json` is a released-version baseline
  manifest. These files are used by ABI lint and are not embedded into the
  current CAR archive.
- `target/cozy/abi-baseline.json` and `target/abi-baseline.json` remain
  fallback baseline locations for generated workflows.

Automatic baseline selection uses the highest released SemVer directory under
`src/main/car/<version>/abi-manifest.json` that is lower than the current CAR
version. SemVer prerelease identifiers participate in precedence, so for
example `1.4.0-rc.2` follows `1.4.0-rc.1` and precedes `1.4.0`. Numeric
identifiers use numeric order, a final version follows every prerelease at the
same core version, and build metadata does not affect precedence.
`*-SNAPSHOT`, current-version, higher-version, malformed SemVer, and arbitrary
named directories are ignored as retained release baselines.

The retained directory name must exactly match `car.version` in its manifest,
and the baseline `car.name` must match the current CAR. Coordinate mismatches
fail before SemVer compatibility policy is applied.

Missing baseline is a warning, even in strict mode, so the first ABI release can
start operation without a previous manifest.

## Compared Surface

ABI lint compares exported components and services, service-qualified operation
identities and signatures, request/response/value types and their field
contracts, entity fields, and component ABI dependency ranges. A field contract
contains its type, multiplicity, and required state.

- patch versions must keep this surface unchanged;
- minor versions may add services, operations, types, entities, and optional
  fields, but must not remove or change an existing contract;
- major versions report breaking changes without rejecting them;
- a current prerelease below the retained baseline is a version regression,
  including a `1.4.0-SNAPSHOT` current build compared with final `1.4.0`.

## Release Operation

After releasing CAR version `x.y.z`, preserve the exact generated and embedded
ABI manifest as:

```text
src/main/car/x.y.z/abi-manifest.json
```

The generated `target/cozy/abi-manifest.json` remains the current development
ABI manifest for the next build line. A project that intentionally owns a
source-managed current manifest may continue to use top-level
`src/main/car/abi-manifest.json`; versioned baseline directories are never
embedded into the new CAR.
