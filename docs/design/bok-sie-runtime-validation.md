# BoK SIE Runtime And Launcher Validation

## Purpose

This contract validates the launcher and runtime selections used by BoK/SIE
integration work without changing published dependency coordinates. Cozy does
not parse launcher configuration from inside the Cozy runtime. Each launcher
is the source of truth for its own effective selection and must report that
selection through its native diagnostics.

## Development Selection

Cozy launcher development candidates belong in `.cozy/launcher.yaml` for a
local workspace override:

```yaml
development:
  enabled: true
  launcher:
    enabled: true
    dev-dir: /path/to/cozy-launcher
  runtime:
    enabled: true
    dev-dir: /path/to/cozy
```

The section-specific `enabled` values are explicit because a higher-precedence
launcher configuration may override the common switch. Use
`conf/cozy/launcher.yaml` only when the development selection is intentionally
shared. Do not put launcher paths in `conf/cozy/config.yaml`; that file belongs
to Cozy/BoK operation configuration.

CNCF uses the equivalent `development.*` contract in
`.cncf/launcher.yaml`. Textus may select the same CNCF runtime with
`--runtime-dev-dir` or its launcher configuration. SIE is a CNCF component
checkout, not another runtime implementation, and is selected with
`--component-dev-dir` for development validation.

## Authoritative Diagnostics

| Layer | Diagnostic | Required evidence |
| --- | --- | --- |
| Cozy launcher/runtime | `cozy runtime config show` and `cozy version` | effective development switches, runtime development directory, and Cozy SNAPSHOT version |
| CNCF launcher/runtime | `cncf runtime config show` and `cncf --runtime-dev-dir <cncf> version` | active runtime development directory and CNCF SNAPSHOT version |
| Textus runtime route | `textus --runtime-dev-dir <cncf> version` | Textus delegates to the selected CNCF runtime and reports its version |
| SIE component | `cncf dev check --no-project-component-dev-dir --runtime-dev-dir <cncf> --component-dev-dir <sie>` | SIE dependency component checkout and its runtime compatibility requirement are valid |

`runtime config show` is the configuration-selection diagnostic. `version`
proves which runtime implementation actually executes. `cncf dev check`
proves the SIE component classpath and CNCF compatibility together. A PATH
lookup or a Cozy-side reconstruction of launcher settings is not equivalent
evidence.

## Development Validation

Run the following without changing `build.sbt`, `version.sbt`, CAR/SAR
catalogs, or published coordinates:

```console
cozy runtime config show
cozy version
cncf runtime config show
cncf --runtime-dev-dir <cncf-runtime-checkout> version
textus --runtime-dev-dir <cncf-runtime-checkout> version
cncf dev check \
  --no-project-component-dev-dir \
  --runtime-dev-dir <cncf-runtime-checkout> \
  --component-dev-dir <sie-component-checkout>
```

Expected development versions end in `-SNAPSHOT`. `cncf dev check` must report
the CNCF runtime and SIE dependency component paths as `OK`. Other project
warnings, such as an absent main-project CAR descriptor when the command is
run from Cozy, do not invalidate the explicit SIE dependency check.

## Release Validation

Disable `development.enabled` and any section-specific development switches,
then run the same launcher diagnostics without `--runtime-dev-dir` or
`--component-dev-dir`. The reported runtime versions must be published release
versions and repository resolution must use published CAR/SAR catalogs.

Published release coordinates are immutable. If validation exposes a defect in
a released launcher, runtime, CAR, or SAR, first move that project to its next
SNAPSHOT coordinate. Do not modify, commit, or `publishLocal` a fix while the
project still carries a published release version.

## Verified Baseline

The Phase 14 development validation on July 13, 2026 established:

- Cozy runtime: `0.2.26-SNAPSHOT` from the Cozy development checkout;
- CNCF runtime: `0.5.1-SNAPSHOT` from the CNCF development checkout;
- Textus launcher route: the same CNCF `0.5.1-SNAPSHOT` runtime;
- SIE dependency component: the development checkout with a valid generated
  runtime classpath and CNCF `0.4.13-SNAPSHOT` minimum/tested requirement.

No release coordinate was changed during this validation.
