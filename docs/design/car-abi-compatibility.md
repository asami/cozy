# CAR ABI Compatibility

Status: implemented baseline.

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
version. `*-SNAPSHOT`, current-version, higher-version, and non-SemVer
directories are ignored.

Missing baseline is a warning, even in strict mode, so the first ABI release can
start operation without a previous manifest.

## Release Operation

After releasing CAR version `x.y.z`, preserve the released ABI manifest as:

```text
src/main/car/x.y.z/abi-manifest.json
```

The top-level `src/main/car/abi-manifest.json` remains the current development
ABI manifest for the next build line.
