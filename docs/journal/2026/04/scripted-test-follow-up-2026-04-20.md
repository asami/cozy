# Scripted Test Follow-up Notes

Date: 2026-04-20

Updated: 2026-04-21

## Context

The `Fix scripted tests` work restored the currently required scripted test paths for Cozy and cncf-samples. Targeted reruns passed after the commit, but several fixes were intentionally scoped as compatibility repairs or temporary test-side adjustments. These items should be tracked separately so that the scripted suite does not hide unresolved runtime or generator issues.

Related commits:

- Cozy: `d0d06d0 Fix scripted tests`
- cncf-samples: `688a8f6 Fix scripted tests`
- CNCF: `6eac46d Authorize entity load hits with storage records`
- Cozy: `33c5a61 Restore scripted response assertions`
- simple-modeler: `640f836 Restore SimpleEntity update patch generation`

## Verification Status

Passed targeted checks:

- `scripted cozy/simpleentity-view`
- `scripted cozy/simpleentity-view cozy/view-cache-metrics`
- `scripted cozy/entity-simpleentity-sqlite-crud cozy/entity-sqlite-crud`
- `scripted cozy/named-view-definition cozy/simpleentity-view`
- `scripted cozy/crud-seed-import`
- `scripted cozy/crud-sqlite`
- `scripted cozy/crud-nested-value`
- `scripted cozy/crud-server-memory`
- `scripted cozy/cqrs-split`
- `scripted cozy/crud-explicit-sync`
- `scripted cozy/crud-surface`
- `scripted cozy/generate-smoke`
- `scripted cozy/aggregate-external-update-proof`
- `scripted cozy/entity-simpleentity-sqlite-crud`

Full scripted status:

- `sbt --no-server --batch scripted` completed all cases except one transient failure.
- The transient failure occurred in `cozy/simpleentity-view` during dependency loading with `ZipFile invalid LOC header (bad signature)`.
- The same scripted case passed immediately when rerun in isolation.

Interpretation:

- This does not currently look like a source-level compile error.
- It is likely related to local snapshot publication, cache state, or concurrent scripted dependency resolution.
- It should still be tracked because it prevents a clean full-suite green signal.

## Remaining Work Items

### 1. Full scripted stability

Problem:

- Full `sbt scripted` can fail with `ZipFile invalid LOC header` while targeted reruns pass.

Likely scope:

- Local Ivy / local Maven snapshot cache behavior.
- Concurrent scripted test execution or publication overlap.
- Corrupted intermediate jar read during generated project compilation.

Next investigation:

- Run full scripted with reduced concurrency if available.
- Check whether scripted test batching publishes or resolves `cozy_2.12` while another generated project is compiling.
- Consider isolating local publish artifacts used by scripted tests.

Desired outcome:

- Full `sbt scripted` should be reproducibly green, not only targeted green.

### 2. View search ExecutionContext propagation

Status: completed on 2026-04-20.

Restored scripted checks:

- `cozy/named-view-definition`
- `cozy/simpleentity-view`

Fixes applied:

- CNCF view search now calls contextual browser query paths with the bound `ExecutionContext`.
- Default view materialization reads source entities under the existing internal materialization context so SimpleEntity lifecycle visibility does not hide source rows from view construction.

Verification:

- `sbt --no-server --batch "scripted cozy/named-view-definition cozy/simpleentity-view"` passed.

Restored signal:

- Search commands verify result records and `total_count` / `fetched_count`, not only command acceptance.

### 3. SQLite CRUD scripted coverage should restore update semantics

Status: completed on 2026-04-21.

Restored scripted checks:

- `cozy/entity-simpleentity-sqlite-crud`
- `cozy/entity-sqlite-crud`

Restored behavior:

- `savePerson` creates a SQLite row.
- `loadPerson` returns the saved value.
- `updatePerson --name jiro` updates SQLite storage.
- A later `loadPerson` returns `name: jiro`.
- `deletePerson` marks the row as `aliveness=dead` and `post_status=archived`.
- A later `loadPerson` returns `404` / `not-found`.

Fixes applied while restoring this coverage:

- CNCF entity fallback now detects not-found by `Conclusion.observation.taxonomy.symptom` instead of relying only on message text.
- `LifecycleAttributes` storage decode accepts external principal IDs such as `test-app-content-manager-principal` by normalizing them to safe `Identifier` text.
- `SecurityAttributes` storage decode restores JSON `rights` text from SQLite rows.
- simple-modeler now maps SimpleEntity update shortcut fields such as `name` into `NameAttributesUpdate`.
- simple-modeler now emits flat SimpleEntity update datastore changes so update directives reach storage columns such as `name`.

Verification:

- `sbt --no-server --batch "scripted cozy/entity-simpleentity-sqlite-crud"` passed.
- `sbt --no-server --batch "scripted cozy/entity-simpleentity-sqlite-crud cozy/entity-sqlite-crud"` was run during restoration; the non-SimpleEntity case passed, and the SimpleEntity case exposed the generator blocker before the final fix.

### 4. `car-sbt-project` cleanup is broad

Problem:

- `Cozy.car-sbt-project` currently deletes `target` after generator execution and also deletes `src/main/scala` for non-Dox model paths before materializing the project.
- This prevents stale generated/default sources from breaking scripted tests, but the `src/main/scala` cleanup is broad.

Risk:

- If a future generated project intentionally keeps hand-written Scala sources in `src/main/scala`, this cleanup can remove them.

Desired outcome:

- Replace broad cleanup with a narrower generated-source cleanup policy.
- Prefer deleting known generated directories/files only, or make cleanup explicit through a policy option.

### 5. Multi-line operation output generation

Problem:

- `job-control-lab.cml` was changed to avoid a multi-line output prose block that caused invalid generated Scala string content.

Interpretation:

- The sample now passes, but the generator still needs a robust string-literal escaping rule for generated metadata.

Desired outcome:

- Generator should escape all emitted string values safely.
- CML output prose should not need to be constrained only to avoid Scala generation failures.

### 6. Dependency version drift in scripted fixtures

Problem:

- Several scripted fixtures had stale dependencies such as `goldenport-cncf` and `simplemodeling-model` snapshot versions.

Desired outcome:

- Scripted fixtures should consume version files or shared scripted settings where practical.
- Avoid scattering literal snapshot versions across shell-generated `build.sbt` files.

### 7. SLF4J and deprecation noise

Problem:

- Scripted runs emit repeated SLF4J multiple-provider warnings and many deprecation warnings.

Impact:

- These warnings do not currently fail the tests, but they make real failure signals harder to see.

Desired outcome:

- Reduce logging/provider noise in scripted-generated projects.
- Triage deprecated APIs used by generated code or scripted fixtures.

## Priority

Recommended order:

1. Stabilize full scripted execution around local snapshot artifacts.
2. Narrow `car-sbt-project` cleanup behavior.
3. Fix generator string escaping for multi-line metadata.
4. Centralize scripted fixture dependency versions.
5. Reduce warning noise.

## Weakened Scripted Checks Inventory

This section records scripted checks that were weakened or may have been weakened while restoring the suite. These are not considered final executable specification quality. They should be restored after the corresponding runtime/generator issues are fixed.

### A. View search assertions removed

Status: restored on 2026-04-20.

Affected scripted cases:

- `cozy/named-view-definition`
- `cozy/simpleentity-view`

Restored behavior:

- Search assertions now verify result records and counts.
- `cozy/named-view-definition` checks summary/named-view search results.
- `cozy/simpleentity-view` checks SimpleEntity view search result fields and counts.

Verification:

- `sbt --no-server --batch "scripted cozy/named-view-definition cozy/simpleentity-view"` passed.

### B. CQRS read-after-command verification removed

Status: restored on 2026-04-20.

Affected scripted case:

- `cozy/cqrs-split`

Weakened behavior:

- The script still creates an item and awaits the job result.
- The later `load-item` verification was removed.
- The `name` input/assertion was also removed from the create/load path.

Lost signal:

- CQRS write-to-read visibility after job completion.
- Read model load correctness for created entities.
- Name field persistence and projection.

Restore target:

- After job await, run `cqrs.entity.load-item`.
- Assert `id`, `name`, and `title` in the read response.

Restored behavior:

- The create command again sends `--name gamma`.
- The script awaits the create job and then runs `cqrs.entity.load-item`.
- The load response asserts `id`, `name`, and `title`.
- CNCF entity-space load-hit authorization now authorizes against the storage record so generated/domain records without security fields do not fail authorization.

Verification:

- `sbt --no-server --batch "scripted cozy/cqrs-split"` passed.

### C. CRUD explicit sync no longer covers server/client route

Status: restored on 2026-04-20.

Affected scripted case:

- `cozy/crud-explicit-sync`

Weakened behavior:

- The script changed from starting the server and using `client` calls to direct command execution.
- Load-response assertions were replaced with SQLite row assertions.

Lost signal:

- Server/client interface behavior.
- HTTP/service route behavior.
- Response decoding through `load-item`.
- `name` field in the response.

Restore target:

- Keep direct command/SQLite checks only as supplemental coverage.
- Restore server startup, client create, client load, and response assertions for `id`, `name`, and `title`.

Restored behavior:

- The script starts the server, creates through the client route, awaits the job result, loads through the client route, and asserts `id`, `name`, and `title`.
- Direct command and SQLite checks remain supplemental.

Verification:

- `sbt --no-server --batch "scripted cozy/crud-explicit-sync"` passed.

### D. CRUD nested value load-response verification replaced by DB row check

Status: restored on 2026-04-20.

Affected scripted case:

- `cozy/crud-nested-value`

Weakened behavior:

- The script no longer calls `load-person` after create.
- It verifies the raw SQLite `address` JSON instead.

Lost signal:

- Entity load decode for nested value objects.
- YAML response shape for nested records.
- `name` response assertion.

Restore target:

- Restore `crud-nested-value-sample.entity.load-person`.
- Assert `id`, `name`, and nested `address.street`, `address.city`, `address.country.value` in the load response.
- Keep DB row verification only if storage-shape coverage is needed separately.

Restored behavior:

- The script restores `load-person` after create.
- The load response asserts `id`, `name`, `street`, `city`, `country`, and `value: JP`.
- Raw SQLite address row checks remain supplemental storage-shape coverage.

Verification:

- `sbt --no-server --batch "scripted cozy/crud-nested-value"` passed.

### E. CRUD seed import search assertions reduced

Status: restored on 2026-04-20.

Affected scripted case:

- `cozy/crud-seed-import`

Weakened behavior:

- Search still checks `query:` and `name: alpha`.
- Assertions for `title: Alpha`, `total_count: 1`, and `fetched_count: 1` were removed.

Lost signal:

- Search result projection completeness.
- Search count correctness.

Restored behavior:

- Re-added `title: Alpha`, `total_count: 1`, and `fetched_count: 1` assertions.
- Search count assertions explicitly request totals with `--query.include_total true`.
- Seed records now declare `post_status: published` so public search assertions verify visible imported data instead of hidden draft rows.

Verification:

- `sbt --no-server --batch "scripted cozy/crud-seed-import"` passed.

### F. CRUD SQLite search/load assertions reduced

Status: restored on 2026-04-20.

Affected scripted case:

- `cozy/crud-sqlite`

Weakened behavior:

- Seed search assertions for `title: Alpha`, `total_count: 1`, and `fetched_count: 1` were removed.
- Created entity load assertions were replaced with a raw SQLite row assertion.

Lost signal:

- SQLite-backed search result completeness and count correctness.
- SQLite-backed load response decode after create.
- `name` field in the load response.

Restored behavior:

- Re-added seed search assertions for `title: Alpha`, `total_count: 1`, and `fetched_count: 1`.
- Re-added created entity `load-item` response assertions for `id`, `name: delta`, and `title: Delta`.
- Kept the SQLite row assertion as supplemental storage-shape coverage.
- Fixed CNCF create default completion so caller-provided entity `name` is not overwritten by the principal id; generated/default names are still completed from the principal when no domain name is supplied.

Verification:

- `sbt --no-server --batch "scripted cozy/crud-sqlite"` passed.

### G. CRUD server memory response assertion reduced

Status: restored on 2026-04-20.

Affected scripted case:

- `cozy/crud-server-memory`

Weakened behavior:

- Load response still checks `id` and `title`.
- Assertion for `name: alpha` was removed.

Lost signal:

- `name` field propagation through server/client memory route.

Restore target:

- Re-add the `name` assertion in the JSON load response.

Restored behavior:

- The JSON load response now asserts `"name":"alpha"` in addition to the existing response checks.

Verification:

- `sbt --no-server --batch "scripted cozy/crud-server-memory"` passed.

### H. Aggregate external update state assertion relaxed

Status: restored on 2026-04-20.

Affected scripted case:

- `cozy/aggregate-external-update-proof`

Weakened behavior:

- The check for `status: Cancelled` was replaced with a weaker `shipment_orders:` presence check.

Lost signal:

- Aggregate follow-up state transition result.
- ShipmentOrder cancellation state in the exposed output.

Restore target:

- Re-add an explicit assertion for the cancelled state once the current output contract is stable.
- Prefer facet/field-level structured output assertion over a broad container-presence check.

Restored behavior:

- The script asserts the current semantic output contract: `"orderStatus":"Cancelled"` and `"shipmentOrderFollowUp":"Cancelled via AggregateBehavior"`.
- It still asserts that the aggregate output contains the specific shipment order (`shipment_orders:` and `title: Outbound-1`), but no longer treats broad container presence as the primary signal.

Verification:

- `sbt --no-server --batch "scripted cozy/aggregate-external-update-proof"` passed.

### I. CRUD surface CLI field assertion relaxed

Status: restored on 2026-04-20.

Affected scripted case:

- `cozy/crud-surface`

Weakened behavior:

- Exact `cli: crud.item.create-item` assertion was relaxed to a broad `crud.item.create-item` substring assertion.

Lost signal:

- CLI projection field name and shape.

Restore target:

- Re-add an exact `cli:` field assertion if the CLI help projection still owns that field.
- If the projection schema intentionally changed, update the scripted expectation to the new exact field path instead of broad substring matching.

Restored behavior:

- The script now asserts the exact selector field shape: `selector: Some(canonical=Crud.Item.createItem,cli=crud.item.create-item,rest=/crud/item/create-item,accepted=Crud.Item.createItem)`.

Verification:

- `sbt --no-server --batch "scripted cozy/crud-surface cozy/generate-smoke"` was run during restoration; `cozy/crud-surface` passed.

### J. Generate smoke no longer directly checks generated DomainComponent path

Status: restored on 2026-04-20.

Affected scripted case:

- `cozy/generate-smoke`

Weakened behavior:

- Direct existence/content checks for generated `DomainComponent.scala` were replaced with checks for `build.sbt` and `src/main/cozy/sample.cml`.

Lost signal:

- Scala source generation output path.
- Presence of the generated domain component.

Restore target:

- Re-add a generated source assertion using the current generated source location.
- Keep project skeleton checks as supplemental coverage.

Restored behavior:

- The script checks `target/scala-3.3.7/src_managed/main/domain/DomainComponent.scala`.
- It asserts the file exists and contains `object DomainComponent` and `Person`.

Verification:

- `sbt --no-server --batch "scripted cozy/generate-smoke"` passed.

### K. SQLite entity update remains unverified

Status: restored on 2026-04-21.

Affected scripted cases:

- `cozy/entity-simpleentity-sqlite-crud`
- `cozy/entity-sqlite-crud`

Previously restored coverage:

- `savePerson` creates a SQLite row.
- `loadPerson` returns the saved value.
- `deletePerson` marks the row as `aliveness=dead` and `post_status=archived`.
- A later `loadPerson` returns `404` / `not-found`.

Previously remaining weakness:

- `updatePerson` persistence is still not covered.

Observed blocker:

- Generated update values do not currently populate nested SimpleEntity update attributes from CLI input.
- Datastore change extraction can persist nested update directive objects as entity columns instead of applying them as storage-level changes.

Restore target:

- Fix generator/runtime update patch handling.
- Add update assertions for both SQLite entity scripted cases.
- Verify changed value through both SQLite storage and `loadPerson` response.

Restored behavior:

- Both scripts execute `updatePerson --name jiro` before delete.
- Both scripts assert SQLite row update to `jiro`.
- Both scripts assert `loadPerson` returns `name: jiro`.
- Delete assertions now verify the archived row keeps the updated value.

Fixes applied:

- simple-modeler maps `name` shortcut input into `NameAttributesUpdate.name`.
- simple-modeler treats `SimpleEntityUpdate` as a SimpleEntity storage shape and emits flat update changes for storage columns.

Verification:

- `sbt --no-server --batch "scripted cozy/entity-simpleentity-sqlite-crud"` passed.
- `sbt --no-server --batch "scripted cozy/entity-simpleentity-sqlite-crud cozy/entity-sqlite-crud"` was used during restoration; the non-SimpleEntity path passed and the SimpleEntity path was fixed afterward.
