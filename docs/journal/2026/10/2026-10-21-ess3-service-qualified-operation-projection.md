# ESS3 service-qualified operation projection — 2026-10-21

Implementation record for PHASE-3 / ESS3-02 / ESS3-02Q, producer contribution
CB-ESS3-GEN-001. Current status: projected output consumed by passing controlled
connected first-original and Book browser runs; independent integrated review,
final validation and whole-Phase/real-provider acceptance remain pending.

The original authoring and repair-054 entries below are retained as recorded.
Later parent observations are appended under the dated validation checkpoint.

## Initial authoring checkpoint — retained history

The frozen projection retains each actual parsed service name beside the same
normalized operation definition. The existing normalized-only projection now
projects those tuple values. Runtime model projection supplies the owner as
`Some(owner)` while preserving operation-name sorting and the existing complete
metadata projections. Entity-operation ownership is not inferred.

The static contract is
[service-qualified operation projection](../../../spec/service-qualified-operation-projection.md).
The executable counterpart is
[ServiceQualifiedOperationProjectionSpec](../../../../src/test/scala/cozy/modeler/ServiceQualifiedOperationProjectionSpec.scala).
It authors actual CML parsing/projection, native generated service contracts,
reversed command/query controls and deterministic twenty-sample owner coverage.
The editor has not executed those scenarios.

Cozy owns extracting the parsed service identity. SimpleModeler owns preserving
and selecting it for generated handlers, factories and API contracts. Kaleidox
and CNCF source changes are outside this unit. Existing unrelated same-file
naming and source-size debt remain separate follow-up work.

The parent owns focused producer tests and the required unchanged-SNAPSHOT or
source dependency refresh before Cozy consumes the new model field. Native
Studio cozyGenerate/compilation, runtime metadata consumer assessment, diff
verification and fresh independent integration review remain pending parent
gates. No generated output is edited by this contribution.

The original whole Phase3 authority, review ledger revision 7 and plan epoch 1
remain unchanged. This record makes no validation pass, Step closure, runtime
metadata isolation, first-original completion or whole Phase acceptance claim.

## ESS3-GEN-054-COZY — owner-specific leaf propagation

The parent's source-confirmed `focused002` result completed fourteen tests:
twelve passed and two failed, with SBT exit 1, wrapper exit 1 and the shared
lock released. The first real projection and generation cases passed after
repair 053. The remaining failures report conflicts between same-name Query
direct TYPE and Command TYPE-section declarations, including their inverse.
This is the recorded pre-repair outcome, not validation of repair 054.

The frozen same-scope repair carries owner-specific CML leaf contracts through
`ModelBuildContext` and a final defaulted `ModelBuilder` parameter. The native
builder binds the service model once and uses the existing per-service leaf
extractor. Both projector normalization calls now supply their actual owner
before the unchanged kind/input/output merge. Owned metadata never falls back
to the flat map; contexts with no owned map retain the existing flat lookup.
The Dart extractor and its duplicate selected-operation validation remain in
place. No executable specification or generated source is changed.

Repair 054 is authored and pending parent verification. The unchanged real CML,
property and reversed-generation scenarios remain the regression evidence to
execute, alongside structured-input and Dart generation regressions. Parent
diff verification, native consumer verification, independent integrated review
and final whole-Phase full validation remain pending. This entry claims no
verification pass or acceptance.

## Parent validation checkpoint — 2026-10-21

After repair 054, the parent recorded three focused Cozy suites with 90 tests
passed, zero failures, cancellations, ignored or pending tests, terminal
SBT/wrapper exit 0 and release of the shared serial lock. These include actual
CML projection, reversed same-name service-owner/kind generation,
structured-input and Dart regressions. The paired SimpleModeler focused
validation passed four suites and 31 tests.

Both producer changes are isolated on dedicated branches named
`codex/ess3-service-operation-owner`. Cozy's task checkout is
`.codex-workflow/checkouts/cozy-ess3` beneath the Studio worktree, based on
`172ac2f1dbf619a448af32097d39f3bfc7c2a699`; the sibling
`simple-modeler-ess3` checkout is based on
`ee51acf593c361568b9910700fa1781a39a2ab8a`. The original shared branches
were not changed.

The parent verified one necessary local refresh for each unchanged SNAPSHOT:

- `org.simplemodeling:simplemodeler_2.12:1.1.26-SNAPSHOT`, consumed by Cozy.
- `org.simplemodeling:cozy_2.12:0.3.3-SNAPSHOT`, consumed by Studio.

These normal local Ivy publications are dependency refreshes, not external
publication or acceptance. The normal Studio bridge subsequently generated
37 fresh Scala sources. The logical `readOriginal` uses
`OriginalSelectionQuery(materialRef, resourceId)` independently of the retained
Book `BookMaterialReadOriginalQuery(resourceId, originalId)`; no generated
Scala was hand-edited.

Actual Studio → Workbench → TKL software composition passed 19 native tests.
A subsequent six-suite invocation passed 92 tests, including the same 19,
actual PostgreSQL Book media/editor HTTP regressions, the exact ABI inventory
of six services, 13 operations and 25 types, and managed authentication
compatibility. A fixture-only repair-57 rebuild retained the same 19 passing
tests. The invocations overlap and must not be counted as 111 distinct tests.

Verified generation does not establish generic runtime service-owner
isolation. CNCF runtime metadata still has bare-name consumers; both current
Studio `readOriginal` operations have QUERY, `authenticated_only` and the same
binary-content confidentiality policies. Independent integrated review must
assess current security/dispatch impact without deferring a current blocker
or claiming support for differing hypothetical runtime policies.

Browser original-display acceptance remains pending. Controlled run 001
served the real page but failed its first explicit search with HTTP 401. Run
002 then stopped during empty-host bootstrap before a listener/client after
the profile correction was applied too early. The parent retained both failed
outcomes and authored fixture-only repair 58 to separate controlled bootstrap
from the provider-wired authenticated child. The source rebuild and a fresh
five-case browser run are not substituted by these generator results.

Independent integrated Step review, reviewed local Step commits, later
committed-source/private CAR smoke and final required participant full
validation remain pending. No main integration, push or external publication
occurred. ESS3-02/03/04 and the whole Phase remain open; real Google/App and
first-original completion are not claimed.

## Current connected consumer checkpoint — 2026-10-21

The parent recorded controlled Material browser run 008 passing five real
HTTP/DOM cases through Studio `KnowledgeMaterial` → Workbench `MaterialWork` →
TKL `Material`. Complete actual UI download bytes matched the admitted 68-byte,
1×1 PNG, with exactly three metadata/stat and three original/read pairs, zero
unexpected/external calls and zero page errors. Cases retain structured
Conclusion caller 403/missing 404, private headers, explicit queries without
Book/Candidate creation, actual Image loading before and failure after Blob
revocation, and native TKL status without inferred data access. Native readback
showed no Candidates/unchanged custody; JVM/Node/fixture cleanup completed
normally. The parent viewed the screenshot; the tiny synthetic PNG is not a
real Google/book original or actual App evidence.

All seven failed runs 001–007 remain retained. Following the earlier 401 and
bootstrap failures, run 003 reached search 200 but inherited Command mode;
run 004 used native Server JSON but expected camelCase instead of the existing
Record HTTP snake_case response keys. Run 005 passed search/inspection/PNG
decode and failed protocol-body equality. Run 006's diagnostic Blob fetch
conflicted with production CSP and retained no byte metrics. Failed run 007
isolated protocol capture 0 from the actual UI download of 68 exact bytes;
it did not establish product corruption or a passing run. The resolved causes
are authentication/bootstrap ordering, native Server mode and response naming;
the authenticated-profile guard remains and logical selectors stay camelCase.
Oracle 64 now always compares full actual UI download bytes, retaining tool
capture metrics as diagnostics. The vacuous fetch-Blob failure predicate was
removed while actual Image before/after revocation checks remain. CSP was not
relaxed. Run 008 is the current passing result.

The separate Book compatibility browser run passed 14 cases (10 Book/four
controlled OAuth), 100 literal/property and 100 message samples with no
discards. Native PostgreSQL readback showed two rows/11 writes; exactly two
bibliography lookups/two code exchanges/zero refreshes and three synthetic
navigation interceptions were recorded, with zero external calls/page errors.
Two legacy controlled packages of six files preserved original packets, byte
lengths and selected URN; the unknown persisted write remained with 503 and
no replay. Runtime/database/container cleanup completed normally. TKL M03,
real Google, Authenticated profile and JVM restart are separate obligations.

The 90 Cozy and 31 SimpleModeler focused passes, exactly one necessary local
refresh each at unchanged SNAPSHOT coordinates and 37 fresh Studio generated
sources with separate `readOriginal` ownership remain retained. Those sources
are now consumed by both passing browser runs. The earlier 19/92 native
PostgreSQL/ABI evidence remains overlapping, not summed. Workbench correction
62 changed only the authored ABI advertisement to two services/seven operations,
preserving all 18 original types plus nine new types; Candidate/source behavior
and dependencies were unchanged. No further `publishLocal`, main integration,
push or external publication occurred.

CAR lint before independent integrated Step review awaits the once-issued
native human question about retaining completed history, retiring missing old
Cozy runner `/root/material_reference_cozy_001` and using a fresh registered
runner. The original two commands completed and the record is idle. No approval,
command replay/reset or recovery mutation is recorded; this update asks no
second question. Existing whole-Phase binding/epoch and ESS3-01 acceptance
remain unchanged. ESS3-02/03/04 stay open, with no local Step commit or Phase
full review. CAR lint, independent review, final full/private CAR gates, M03
and real Google/App remain outstanding. First real Material/operator Google
registration/catalog are still unsupplied; TKL retains callback binding and
grant ownership. This checkpoint changes no specification or acceptance.
