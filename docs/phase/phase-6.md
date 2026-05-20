# Phase 6: Component Repository Publication and Scaffolding

Status: active

Start date: 2026-05-21

## Goal

Make CAR/SAR component repository publication and component project
scaffolding first-class Cozy workflows.

Phase 6 formalizes the CS work that began in the CAR/SAR catalog journal. The
goal is to make `textus` and CNCF component developers able to use published
CAR/SAR artifacts and initialize component projects without project-local
ad hoc scripts.

## Scope

In scope:

- CAR/SAR source catalogs and warehouse catalog publication
- derived `maven-metadata.xml` for current Textus compatibility
- `cozyPublishCar` / `cozyPublishSar` and sbt-cozy task bridge alignment
- simplified CNCF runtime compatibility and component-owned dependency policy
- canonical `artifact:version` Textus syntax alignment
- `cozy init component` scaffolding contract for new component projects
- `textus-knowledge-editor` initialization as the first Phase 6 scaffolding
  driver

Out of scope:

- production repository hosting operations
- CDN invalidation automation
- Textus direct CAR/SAR catalog resolution beyond metadata compatibility
- full component editor implementation
- runtime execution behavior inside CNCF

## Phase Items

- [x] CS-01: Explicit-version startup baseline
- [x] CS-02: Identify missing versionless metadata
- [x] CS-03: Confirm canonical artifact naming
- [x] CS-04: Define catalog responsibility split
- [x] CS-05: Define catalog path policy
- [x] CS-06: Define Car/Sar task naming policy
- [x] CS-07: Simplify CNCF runtime requirement contract
- [x] CS-08: Simplify SIE build dependencies
- [x] CS-08B: Simplify generated sbt scaffold dependencies
- [x] CS-09: Simplify SIE CAR manifest
- [x] CS-10: Codify CAR packaging defaults
- [x] CS-11: Add CAR/SAR catalog schema
- [x] CS-12: Add `publish-car` flow
- [x] CS-13: Add `publish-sar` flow
- [x] CS-14: Add sbt-cozy task bridge
- [x] CS-15: Generate derived Maven metadata
- [x] CS-16: Reflect latest SIE 0.1.1-SNAPSHOT spec
- [x] CS-17: Add Textus artifact version syntax
- [x] CS-17R: Propagate metadata-selected Textus version
- [ ] CS-18: Complete SIE 0.1.1 public publication verification
- [x] CS-19: Add Cozy component init scaffolding contract

## Acceptance Criteria

- `cozyPublishCar` and `cozyPublishSar` produce archive, source catalog,
  warehouse catalog, and derived metadata in one flow.
- Textus can start a published CAR/SAR artifact with explicit
  `artifact:version` syntax and, after public publication, versionless syntax.
- Ordinary CAR projects can rely on Cozy defaults for `src/main/car` and
  disabled dependency embedding.
- Component project scaffolding can accept package name, artifact name,
  component name, component kind, version, and display metadata at init time.
- `textus-knowledge-editor` initialization can use the Cozy component init path
  instead of manual scaffolding.

## References

- `docs/strategy/cozy-development-strategy.md`
- `docs/phase/phase-6-checklist.md`
- `docs/journal/2026/05/car-sar-catalog-publish-plan-2026-05-20.md`
