# Managed CAR Catalog Model and Packaging Handoff

Date: 2026-07-21

## Context

Textus Control Center will need a logical view of managed CARs: development
projects, locally installed CARs, and explicitly selected public CARs.  Its
first implementation is intentionally application-owned.  This journal records
the reusable Cozy model/compiler and CAR-packaging capabilities that should be
considered later.

This is not a request to add a Control Center CAR list, Web UI, or runtime
catalog implementation to Cozy in the current work.

## Required Domain Separation

Cozy-facing models must keep these records distinct:

```text
logical CAR              source snapshot             runtime instance
-----------              ---------------             ----------------
artifact identity        development/local/public    launcher registration
component identity       available versions          endpoint and health
declared requirements    refresh/diagnostic facts    running process facts
```

The middle record is availability knowledge.  It is neither a process nor a
health record.  A known CAR that is not running therefore remains a valid
catalog record with a `not-running` runtime relation, rather than becoming a
stale source record.

## Future Cozy Capabilities

### 1. Descriptor and Packaging Projection

Standardize a generated machine-readable CAR descriptor projection, usable for
both a development project and a packaged CAR.  It should include:

- artifact ID (`project.name`), component name (`project.component.name`), and
  their versions;
- declared default server port, runtime requirements, and dependencies when
  available;
- descriptor schema/version and paths or archive entries for related metadata;
- stable identity suitable for generated APIs and catalog joins.

The projection gives generic consumers a supported format and avoids inferring
identity from a project directory name.  Cozy should generate and validate it
as part of CAR packaging; applications should not have to parse private build
structures or inspect CAR internals ad hoc.

### 2. CML Model Shape for Source Snapshots

When a generic managed-CAR domain is modeled in CML, support stable entities or
value types for:

- canonical artifact and component identifiers;
- source kind (`development`, `local-repository`, `public-repository`,
  `subscription`);
- availability/version facts;
- refresh timestamp, state, and diagnostics;
- an optional runtime-registration artifact correlation key.

Source locators need careful visibility rules.  A local development directory,
credential-bearing repository URL, or token must not be automatically exposed
by generated Web/API projections.  Diagnostics can expose safe display fields
while privileged operations retain the full locator.

### 3. Generated Operation Boundaries

If CML generates operations for catalog refresh, model them as explicit,
privileged source-refresh commands and read-only snapshot queries.  Generated
Web/API code must not grant arbitrary filesystem scans, repository crawling,
process discovery, or lifecycle control merely because a source record exists.

The generic operations should be able to consume a CNCF-provided catalog
runtime later, but must not depend on a Control Center-specific page model.

### 4. Catalog Sidecar Validation

Define optional CAR catalog sidecars/manifests that reference the descriptor
projection and versions.  Validation should be deterministic in local builds:

- reject inconsistent artifact/component identities;
- verify referenced descriptor/ABI/CML/model metadata entries when declared;
- preserve explicit version and compatibility metadata;
- produce actionable diagnostics for missing or ambiguous metadata.

Build and validation must not fetch public repositories, publish CARs, or
modify a user's CNCF home as side effects.

## Executable Specification Needs

Future Cozy coverage should establish that:

- descriptor identity is derived from `project.yaml` rather than a directory
  basename;
- development and packaged projections have compatible canonical identities;
- source snapshots can represent unavailable sources without becoming runtime
  health failures;
- generated public projections redact sensitive locators;
- optional runtime `artifactId` correlation is backward compatible;
- sidecar validation is deterministic and performs no network or process work.

## Dependency on CNCF

CNCF should define the runtime/repository catalog source contract, public
repository discovery manifest, and optional launcher `artifactId` correlation.
Cozy's role is the model, generated projection, and CAR metadata/validation
layer.  The boundary prevents every management application from inventing its
own project-file parser while also avoiding Control Center policy in the Cozy
compiler.

## Deferred Shared Catalog Capability

The next shared capability is **not** a generic CAR-list endpoint.  Before a
multi-user Control Center can safely enumerate CARs across machines, CNCF must
first define an authenticated source-registration and snapshot-journal
contract.  That contract should let a provider record, per source:

- source owner/scope and trust boundary;
- the descriptor projection or artifact identity it observed;
- observation time, availability state, version facts, and a safe diagnostic;
- a monotonically identifiable snapshot/revision so consumers can reconcile
  without treating a missed refresh as a lifecycle event; and
- visibility classification for locators and credentials.

Cozy can then model and validate those journal records, generate their
redacted read projections, and preserve their provenance.  CNCF remains
responsible for source discovery, authentication, transport, and any eventual
cross-host aggregation policy.  A generic list or search Operation is deferred
until those ownership, visibility, and consistency rules are adopted; it must
not be inferred from local filesystem scanning or a public repository crawl.

This journal item is a future-contract record only.  It does not add a Cozy
CAR-list service, scanner, repository client, or generated Control Center UI
in the current phase.

## Out Of Scope

- Implementing this generic model or generator in the current phase.
- Control Center list/detail pages or user-owned CAR registrations.
- CAR process lifecycle management.
- Automatic scanning of `src`, home directories, or public repositories.
