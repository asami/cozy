# Phase 16: CML Value and Datatype Refactoring

Status: open

Start date: 2026-07-15

## Goal

Refactor CML around a coherent Value model, beginning with operation inputs and
outputs. Reusable command/query inputs become `# VALUE` definitions with
`input-kind`; one-use inputs and outputs may be defined locally; simple outputs
may use CNCF-owned predefined Result types.

Refactor domain scalar modeling at the same time. A class that only wraps
`string` is not automatically a meaningful Value or Datatype. Finite
vocabularies should normally become powertypes, lifecycle state should be
modeled by a statemachine when transitions matter, and ordinary text should use
the most precise CML predefined type and explicit value-range constraints.

An ordinary CML model should become naturally I18N-capable. Model authors use
semantic display-text types instead of manually constructing locale storage;
plain text is the single-locale shorthand and structured input adds multiple
locale entries to the same generated type. Stable names, identifiers, tokens,
and other identity-oriented text remain explicitly nonlocalized.

The phase preserves explicitly identified compatibility syntax while moving
new CML and Cozy scaffolds to the canonical grammar. Development is driven by
both `textus-user-notification` and `textus-user-account`, so the grammar is
verified against compact notification flows and a larger identity lifecycle
rather than against parser-only fixtures.

Compatibility preservation applies to the operation Value grammar, not to the
current Scala lowering of plain `DATATYPE` declarations. Phase 16 deliberately
redefines a plain `DATATYPE` as a nominal scalar: generated Scala uses a
distinct validated type, while its canonical Wire and datastore
representations remain scalar. This is an explicit breaking generated-source
contract with no feature flag, legacy `String` adapter, implicit conversion,
or dual scalar/object codec. A complex `DATATYPE` remains a structured type.

## Development Drivers

`textus-user-notification` is the primary migration driver:

- its command/query payloads provide a focused canonical grammar migration;
- its generated operation metadata and API/ABI provide the first before/after
  comparison;
- its notification, delivery, and preference services exercise reusable
  payloads without the broader account-domain surface.

`textus-user-account` is the full-contract regression driver:

- its user and management services exercise a larger set of command/query
  inputs and Result shapes;
- its authentication, session, credential, profile, and administrative
  operations verify that local and predefined Results remain practical in a
  real component;
- its existing literate use-case and operation contract metadata must survive
  Value normalization unchanged.

Driver source changes begin only after the implementation proposals in notes
and failing Cozy specifications exist. Existing uncommitted driver work must
first be classified and preserved; the migration baseline records the starting
commit together with the classified working-tree state.

## Documentation Lifecycle

Phase 16 follows the Cozy documentation workflow:

1. record each investigation, discussion, and decision at that time in
   `docs/journal` without rewriting its historical context;
2. keep the current implementation specification in `docs/notes`, updating it
   whenever a later decision supersedes an earlier proposal;
3. express the current notes as executable specifications and implementation;
4. verify the implementation through Cozy and the two driver CARs;
5. promote only the verified contract to `docs/design`, `docs/spec`, and the
   accepted grammar record.

Journal entries preserve the decision process. Notes are the latest
implementation input. Design and specification documents record the resulting
stable contract after implementation; they are not prerequisites that
prematurely freeze the proposal.

## Scope

In scope:

- top-level `# VALUE` plus `input-kind=COMMAND|QUERY`;
- operation-kind and input-kind validation;
- anonymous and named local input Values;
- anonymous and named local output Results;
- CML/CNCF predefined Result resolution, beginning with `UnitResult` and
  `IntResult`;
- a classification rule for `VALUE`, `DATATYPE`, `POWERTYPE`,
  `STATEMACHINE`, and predefined scalar types;
- a nominal-scalar model and Scala generator contract for plain `DATATYPE`
  declarations, including typed Create, Read, Query, and Update surfaces;
- scalar codecs and datastore projection for nominal scalars, with named
  schema identity, underlying datatype, and constraints preserved;
- an inventory and migration of string-only wrappers in both driver CARs;
- predefined text types such as `name`, `title`, and `text`, with explicit
  normalization and length contracts;
- the existing semantic localization model, including nonlocalized `name` and
  `title` as one `I18nTitle` structure that stores either single-locale or
  multi-locale entries, plus locale identity, fallback, and per-entry
  value-range rules;
- natural I18N generation for user-visible semantic text, with plain scalar
  source as single-locale shorthand and structured source for multiple locale
  entries;
- text constraint metadata, validation, generated schema, datastore, form, and
  API propagation;
- compatibility normalization for top-level `# COMMAND`, `# QUERY`, current
  named inline Values, and `PARAMETER` convenience input;
- normalized metadata and deterministic diagnostics;
- canonical scaffold output and representative CAR migration;
- primary migration through `textus-user-notification` and full-contract
  regression through `textus-user-account`;
- executable specifications and grammar documentation promotion.

Out of scope:

- removing compatibility grammar in Phase 16;
- compatibility adapters for the former plain-`DATATYPE` to Scala `String`
  lowering;
- making Cozy own CNCF Result runtime implementation;
- unrelated CML domain, entity, relationship, event, or use-case redesign;
- replacing opaque identifiers, hashes, secrets, or external references with
  powertypes merely because their runtime representation is text;
- silent ABI renaming without migration evidence.

## Stage 16.1: Contract Baseline

Stage Status:

- Current status: DONE
- Owner: cozy-modeler
- Checklist basis: `CML16-01` and `CML16-02`
- Update rule: update when `CML16-01` documentation and executable contract
  items change

Focus:

- record the discussion and current implementation facts;
- maintain the operation grammar and semantic scalar implementation proposals
  in notes;
- establish executable specifications for canonical and compatibility forms.

## Stage 16.2: Value Kind Normalization

Stage Status:

- Current status: DONE
- Owner: cozy-modeler
- Checklist basis: `CML16-03`
- Update rule: update when `CML16-03` items begin or their evidence changes

Focus:

- parse `input-kind` on top-level Values;
- normalize legacy `# COMMAND` and `# QUERY` through the same Value model;
- make kind mismatch and missing-kind failures deterministic.

Verification evidence:

- Kaleidox `CmlSectionFormatSpec`: 34 tests passed, including AST-backed Value
  property preservation;
- Cozy `ModelerOperationValueKindSpec` and `ModelerServiceOperationSpec`: 31
  tests passed, covering canonical, invalid, compatibility, and generated
  metadata contracts;
- implementation uses Kaleidox `0.6.18-SNAPSHOT`; no release-version source was
  modified in place;
- Kaleidox full test suite: 114 tests passed and 2 tests were ignored;
- Cozy full test suite: 499 tests passed;
- unchanged notification and account driver CML generated successfully,
  preserving 13 and 20 operation metadata entries respectively.

## Stage 16.3: Local Operation Values

Stage Status:

- Current status: DONE
- Owner: cozy-modeler
- Checklist basis: `CML16-04` and `CML16-05`
- Update rule: update when `CML16-04` or `CML16-05` items begin or their
  evidence changes

Focus:

- add anonymous and named local `INPUT` Values;
- add anonymous and named local `OUTPUT` Results;
- preserve current named inline Value parsing;
- establish stable local identities and generated names.

Verification evidence:

- Kaleidox `CmlSectionFormatSpec`: 36 tests passed, including anonymous and
  named operation-local Value AST contracts;
- Cozy `ModelerLocalOperationValueSpec` and `ModelerServiceOperationSpec`: 37
  tests passed, covering generation, metadata, compatibility, conflicts, and
  projected-name collisions;
- Kaleidox full test suite: 116 tests passed and 2 tests were ignored;
- Cozy full test suite: 509 tests passed;
- unchanged notification and account driver generation succeeded, and each
  generated tree was identical to its Phase 16.2 baseline.

## Stage 16.4: Predefined Result Contract

Stage Status:

- Current status: DONE
- Owner: cozy-modeler and cncf-runtime
- Checklist basis: `CML16-06`
- Update rule: update when `CML16-06` catalog ownership or implementation
  evidence changes

Focus:

- establish the CNCF-owned predefined Result catalog;
- add Cozy type resolution and metadata projection;
- verify `UnitResult` and `IntResult` end to end.

Current evidence:

- CNCF owns `OperationResult`, `UnitResult`, and `IntResult` runtime classes;
- CNCF `PredefinedResultCatalog` fixes the initial exact, case-sensitive
  catalog and `IntResult.value: int` payload schema;
- CNCF embeds the canonical catalog in its runtime descriptor and sbt-cozy
  transports that descriptor from the resolved runtime JAR into generation;
- Cozy verifies the selected runtime version, resolves exact catalog names,
  rejects unknown or raw scalar outputs, and projects catalog-backed
  `resultFields` for `UnitResult` and `IntResult`.

## Stage 16.5: Semantic Scalar and Text Range Modeling

Stage Status:

- Current status: IN PROGRESS
- Owner: cozy-modeler
- Checklist basis: `CML16-07`
- Update rule: update when the scalar inventory, predefined type catalog, or
  text constraint evidence changes

Focus:

- classify each string-only Value and Datatype in both driver CARs;
- redefine each accepted plain Datatype as a nominal scalar instead of
  lowering its attributes to the underlying Scala primitive;
- add the corresponding SimpleModeler model element and Cozy projection, then
  generate validated Scala case classes, `ValueReader`, scalar codecs,
  presentation, scalar datastore conversion, and named schema metadata;
- propagate nominal types through required, optional, repeated, Create, Read,
  Query, and Update forms without `String`, `Condition[String]`, or
  `Update[String]` fallback;
- use powertypes for finite vocabularies and statemachines for transition-owned
  lifecycle state;
- use predefined `name`, `title`, `text`, identifier, URI, locale, and timezone
  types where their contracts fit;
- use the existing `name = Name` and `title = I18nTitle` structure as the
  baseline, recognizing that one `I18nTitle` supports both single-locale and
  multi-locale values, then classify labels, descriptions, messages, and body
  text by the same semantic method;
- retain domain Values or Datatypes only when they add narrower validation,
  normalization, privacy, or composition semantics;
- define explicit text length constraints for scalar and per-locale I18N values
  and preserve locale entries and constraints through generated metadata and
  runtime boundaries.

Current evidence:

- `CmlModelInspection` loads the shared Kaleidox CML AST/model and inventories
  normalized Value and Datatype declarations without Markdown-table or
  description-list text reparsing;
- the current driver inventory contains 19 string-only Datatypes in
  `textus-user-notification`, 16 in `textus-user-account`, and no string-only
  Values;
- every item has a provisional structural category, localization direction,
  and confirmed-or-domain-decision state in
  `docs/notes/cml-semantic-scalar-driver-inventory.md`;
- `simplemodeling-lib` `I18nTitleSpec` now fixes single-locale construction,
  multi-locale codec round-trip, effective locale fallback, and preservation
  of every stored locale entry as executable baseline behavior;
- `simplemodeling-lib` `NameSpec` now fixes the nonlocalized generic name
  range at 1 through 256 characters and its validation failure boundary;
- `simplemodeling-lib` `TextSpec` now fixes the current nonlocalized runtime
  range at 0 through 8192 Scala string length units, exact printable-value
  preservation, and rejection of newline/control-character input; this is
  evidence for the catalog audit, not yet the accepted CML `text` contract;
- `simplemodeling-lib` `I18nStringSpec` now fixes execution-locale binding for
  plain input, ordered multi-locale JSON round-trip, non-destructive effective
  fallback, and escaped leading-brace input as the shared I18N codec baseline;
- `simplemodeling-lib` `I18nMessageSpec` now fixes the legacy direct-entry
  representation and root, English, Japanese, first-entry display priority;
  this remains compatibility evidence rather than the accepted CML `message`;
- `simplemodeling-lib` `I18nLabelSpec` now fixes plain construction and ordered
  structured round-trip through `I18nString`, establishing the locale-aware
  runtime label baseline without deciding field exposure or text constraints;
- `simplemodeling-lib` `I18nDescriptionSpec` now fixes plain construction and
  ordered structured round-trip, while `DescriptiveAttributesSpec` fixes
  non-destructive effective description fallback through `I18nString`;
  description-specific range and multiline policy remain open;
- `simplemodeling-lib` `I18nBriefSpec` now fixes plain construction and ordered
  structured round-trip, while `DescriptiveAttributesSpec` fixes
  non-destructive locale selection for the existing headline and brief fields;
  distinct headline and brief range policy remains open;
- `simplemodeling-lib` `I18nSummarySpec` now fixes plain construction and
  ordered structured round-trip, while `DescriptiveAttributesSpec` fixes
  non-destructive locale fallback from summary to lead; summary-family role and
  range policy remain open;
- `simplemodeling-lib` `I18nTextSpec` now fixes plain construction and ordered
  structured round-trip; cross-checking CNCF SD-01B classifies it as localized
  plain narrative text, while `ContentBody` remains a single document body and
  its `I18nText` overload is only a display projection or compatibility input;
- canonical CML `text` now resolves through the predefined scalar catalog to
  `I18nText`, with a default 1..8192 character range applied independently to
  every locale entry;
- SimpleModeler now applies inherited predefined-scalar constraints to
  generated Value, Create, and Update validation, while constrained nominal
  scalar attributes continue to delegate validation to their nominal type;
- executable Scala 3.3.8 coverage constructs plain and multi-locale `text`,
  preserves all locale entries, and rejects empty or overlong entries at both
  Create and Update boundaries;
- SimpleModeler generated `derived=content` aliases now return `ContentBody`
  and omit the obsolete locale overload, keeping generated entity APIs aligned
  with SD-01B;
- the semantic text family matrix now connects label, headline/brief,
  summary-family, and description roles to `DescriptiveAttributes`; stored
  locale-aware values remain distinct from its non-destructive `effective*`
  display fallback;
- the powertype/statemachine boundary now uses vocabulary closure and
  transition ownership, leaving extensible registries as open scalars and
  externally owned lifecycle state outside local statemachines;
- canonical `min-length` and `max-length` now flow through normalized domain
  constraints to generated Scala validation and Web hints, while
  `MAttribute.Web` no longer injects domain validation and localized values are
  checked entry by entry;
- new CAR scaffolds now keep one-use command and query inputs as named local
  Values below each operation and use the CNCF-owned `OperationResult` for
  simple outputs, while reusable entity description data remains a top-level
  Value;
- scaffold executable specifications generate both command and query metadata
  from that CML, and the generated model sources compile together with the
  scaffolded `ComponentFactory` under Scala 3.3.8;
- the focused bridge, local-Value, predefined-Result, and scaffold
  specifications pass, the scaffold compiles through the normal sbt-cozy path
  under Scala 3.3.8, and the full Cozy suite passes 545 tests;
- executable compatibility coverage keeps legacy top-level `# COMMAND` and
  `# QUERY` inputs plus service-scoped named inline Values readable and
  normalizes them to the same generated operation metadata as canonical
  Values;
- CAR ABI diagnostics now identify changed operation kind, generated input and
  output type names, and execution as explicit old-to-new values while keeping
  the existing SemVer compatibility policy authoritative;
- the generated Scala 3.3.8 contract is compiled and executed by scripted
  coverage for Create and Update models, including every locale entry and the
  `NameAttributes` title plus `DescriptiveAttributes` headline, summary, and
  description boundaries;
- plain Datatypes now project through Cozy to SimpleModeler nominal-scalar
  model elements and generate validated distinct Scala types with scalar
  `ValueReader`, Circe, datastore, and Record boundaries;
- nominal identity is preserved through required, optional, repeated, Create,
  Read, Query, and Update forms, and executable generation coverage rejects
  primitive `String`, `Condition[String]`, and `Update[String]` fallback;
- text `min` and `max` are numeric-only; textual constraints use
  `min-length`/`max-length`, with single scalar diagnostics referring to the
  value and collection/I18N diagnostics referring to entries;
- generated query models no longer assume a Circe codec for shared
  SimpleModeling values whose own runtime contract does not provide one;
- both driver repositories now exercise the Cozy-side nominal scalar and
  predefined text contracts as migration drivers.
- Cozy CML lint now reads normalized Datatype constraints and accepts a
  string-backed nominal scalar when `min-length`, `max-length`, `pattern`, or
  `format` declares a narrower domain contract;
- unconstrained wrappers remain review warnings, and a predefined-looking
  wrapper is rejected as redundant only when no narrower constraint exists;
- the accepted-domain-scalar lint contract is covered by executable AST and
  lint specifications without reparsing CML source text.

## Stage 16.6: Scaffold and Migration

Stage Status:

- Current status: IN PROGRESS
- Owner: cozy-scaffold
- Checklist basis: `CML16-08`
- Update rule: update when `CML16-08` scaffold or migration evidence changes

Focus:

- emit canonical Value grammar from Cozy scaffolds;
- `car-sbt-project` and `init component` now emit reusable command and query
  inputs under `# VALUE` with explicit `input-kind`, and executable scaffold
  specifications reject new top-level `# COMMAND` and `# QUERY` output;
- retain read compatibility for legacy CML;
- migrate `textus-user-notification` as the primary representative CAR;
- validate `textus-user-account` as the larger operation-contract regression
  driver;
- compare generated metadata and API/ABI before and after each migration;
- verify that user-account literate contract metadata is not changed by Value
  normalization;
- replace string-only wrappers according to the accepted classification and
  compare generated validation, storage, form, and API contracts.
- migrate both driver operation inputs and entity surfaces to the same
  predefined, nominal, powertype, statemachine, or structured types;
- remove driver-owned handwritten scalar wrapper classes after generated
  nominal types are available;
- replace notification JSON-in-string fields with structured values and split
  notification lifecycle state from delivery-attempt result state;
- treat resulting Scala/API/ABI changes as explicit versioned breaking changes
  rather than preserving the former generated `String` contract.

Verification update on 2026-07-15:

- `textus-user-notification` migrated all 13 authored operation inputs from
  legacy top-level command/query sections to canonical Values with explicit
  `input-kind`;
- generated component source and all 13 model-metadata operation signatures
  remained identical across the migration;
- Cozy now derives the default CAR ABI operation surface from generated CML
  model metadata, while explicit and source-managed ABI manifests retain
  precedence;
- the reconstructed baseline and current ABI exports were identical, with 13
  authored service operations and four entity identities;
- the CAR manifest and `target/cozy/abi-manifest.json` sidecar were identical;
- executable verification passed with 526 Cozy tests, 73 sbt-cozy tests,
  1,703 CNCF tests, and 19 notification tests;
- CML CAR packaging now requires generated model metadata when no explicit or
  source-managed ABI exists, and incremental sbt-cozy generation regenerates
  missing metadata side output instead of reusing only generated Scala;
- notification preference writes now enforce subject ownership, support
  privileged administration, and derive one stable EntityId from each
  subject/type/channel identity;
- the preference write path now uses the generated create shape through the
  CNCF internal `entity_upsert` DSL, and 16 concurrent writes converge on the
  same persistent identity and one row;
- CNCF `DataStore.save` now delegates one atomic dialect upsert statement to
  SQLite or MySQL instead of issuing a read-before-write existence check;
- create/update authorization is selected from the row loaded inside the same
  process-local EntityStore identity lock as the upsert, preventing a competing
  writer from retaining stale create authorization; EntitySpace cache
  publication completes inside that lock so an earlier writer cannot overwrite
  a later cached value;
- notification creation, read, and dismissal timestamps use the CNCF
  `ExecutionContext` clock and are covered by a fixed-clock executable spec;
- notification CAR lint has no deterministic FAIL, while strict release lint
  still reports the expected development-state warnings for the SNAPSHOT
  sbt-cozy plugin, unpublished dependency, absent release ABI baseline, and
  non-standard ServiceLoader declaration.

The detailed decision and evidence are recorded in
`docs/journal/2026/07/cml-notification-operation-value-migration-2026-07-15.md`.

Notification finite-vocabulary update on 2026-07-15:

- `UserNotificationAudienceKind` is a generated powertype with `direct`,
  `multicast`, and `broadcast` values;
- `UserNotificationChannel` is a generated powertype with `in_app`, `email`,
  `sms`, and `push` values, while provider variability remains an open provider
  contract;
- `UserNotificationPriority` is a generated powertype with `low`, `normal`,
  `high`, and `urgent` values;
- the canonical external spelling is the declared snake_case value and no
  compatibility alias is retained for `in-app`;
- notification lifecycle status and delivery-attempt result status remain
  separate follow-up work because they have different transition ownership;
- generated Scala 3.3.8 compilation succeeds and executable specifications
  reject undeclared values.

The vocabulary decision is recorded in
`docs/journal/2026/07/cml-notification-powertype-migration-2026-07-15.md`.

Notification quiet-hours update on 2026-07-15:

- the string-backed `UserNotificationQuietHours` wrapper was removed;
- preference entity and command fields now use predefined `localtime` and
  generate `java.time.LocalTime` consistently;
- ISO local-time parsing rejects malformed values at generated input
  construction;
- executable notification behavior verifies a `22:00` to `07:00` UTC interval
  defers a `23:30` notification until the next `07:00`;
- interval completeness and daylight-saving policy remain follow-up domain
  decisions.

The decision is recorded in
`docs/journal/2026/07/cml-notification-quiet-hours-localtime-migration-2026-07-15.md`.

Notification action-reference update on 2026-07-15:

- the string-backed `UserNotificationActionReference` wrapper was removed;
- notification entity and command fields now use predefined `uri` and generate
  `java.net.URI` consistently;
- relative application routes and absolute URIs are both preserved;
- malformed URI syntax is rejected at generated input construction;
- authorization for the resolved target remains an application/runtime policy,
  not a URI parsing concern.

The decision is recorded in
`docs/journal/2026/07/cml-notification-action-reference-uri-migration-2026-07-15.md`.

Notification body update on 2026-07-16:

- the string-backed `UserNotificationBody` wrapper was removed;
- notification entity and command fields now use canonical predefined `text`
  and generate `I18nText` consistently;
- plain input creates one root-locale entry and structured input preserves all
  locale-tagged entries through generated datastore encoding;
- generated validation applies the 1..8192 default range independently to
  every locale entry;
- generated operation and Schema metadata retain canonical `text`, required
  multiplicity, and the same per-locale range;
- Help and automatic REST/OpenAPI project those constraints, with REST
  accepting a plain string or a locale map;
- generated operation form HTML projects the same metadata as a required
  textarea with `minlength=1` and `maxlength=8192`, verified by the executable
  `GeneratedOperationFormProjectionSpec`;
- generated API `Record` output and datastore encoding both preserve every
  locale entry, while recipient-facing presentation selects effective text
  explicitly with the CNCF `ExecutionContext` locale;
- generated datastore conversion now treats inherited `NameAttributes` and
  `DescriptiveAttributes` as datastore values, preventing title, headline,
  summary, and description from passing through display-oriented conversion;
- focused verification passed for simplemodeling-lib I18N codecs (10 tests),
  SimpleModeler Value generation (11 tests), Cozy operation generation (27
  tests), CNCF Help/OpenAPI projection (5 tests), and notification operation
  metadata plus behavior (24 tests across two suites).

The decision is recorded in
`docs/journal/2026/07/cml-notification-body-text-migration-2026-07-16.md`.

The operation metadata and structural boundary decision is recorded in
`docs/journal/2026/07/cml-text-operation-boundary-projection-2026-07-16.md`.

Account semantic-scalar verification update on 2026-07-15:

- Cozy now owns one executable predefined scalar catalog for localized
  descriptive roles and low-ambiguity account scalar aliases;
- `textus-user-account` uses predefined `title`, `email`, `phone`, `locale`,
  `timezone`, and `ip-address` types instead of nominal string wrappers;
- generated entity, Create, Update, query, datastore, and operation boundaries
  use `I18nTitle`, `EmailAddress`, `PhoneNumber`, `Locale`, `TimeZone`, and
  `IpAddress` directly;
- locale and timezone external/datastore projections are canonical BCP 47 tags
  and zone IDs rather than JVM `toString` representations;
- `PhoneNumber` requires canonical E.164 identity and normalizes explicitly
  international input without guessing a country code;
- unresolved login, actor, session, client, hash/token, suspension, device, and
  user-agent contracts remain domain-decision wrappers rather than being
  replaced by broader types;
- generated Scala 3.3.8 compilation and all 86 account tests pass;
- notification clean generation and all 19 notification tests pass;
- simplemodeling-lib passes 368 tests with 124 pending, SimpleModeler passes 32
  tests, simplemodeling-model passes 44 tests with 27 pending, and Cozy passes
  545 tests;
- the four sample projects previously blocked by unresolved `sbt-cozy 0.1.10`
  now use 0.1.14 and compile successfully under Scala 3.3.8;
- Account CAR packaging produces a descriptor with name, version, and
  component plus a readable ABI manifest;
- CAR lint reports no deterministic FAIL for either account or notification.
  Remaining warnings cover SNAPSHOT build tooling, absent release ABI
  baselines, notification publication prerequisites/ServiceLoader policy, and
  existing account ComponentFactory internal-DSL migration debt.
- `git diff --check` passes in every repository touched by this validation
  slice.

The implementation decision and remaining domain boundaries are recorded in
`docs/journal/2026/07/cml-account-semantic-scalar-migration-2026-07-15.md`.

Account operation Value verification update on 2026-07-16:

- all 20 authored account operation inputs migrated from legacy top-level
  command/query sections to canonical Values with explicit `input-kind`;
- a reconstructed legacy worktree and the canonical worktree were regenerated
  with the same current Cozy implementation and Scala 3.3.8;
- generated `UserAccountComponent.scala` and `abi-manifest.json` are
  byte-for-byte identical across the migration;
- normalized `model-metadata.json` is exactly equal after excluding only the
  differing source path and source digest, proving that use-case,
  precondition, postcondition, rule, scenario, descriptive, and narrative
  metadata survives unchanged;
- the canonical grammar adds 20 generated top-level Value classes and removes
  no generated source;
- Cozy now applies datatype constraints and canonical Value/input-kind
  metadata identically to legacy and canonical operation inputs, and removes
  structural properties from narrative through the CML/SmartDox AST;
- executable verification passed with 37 focused Cozy tests, 550 full Cozy
  tests, seven Scala 3.3.8 generated-runtime scripted tests, 34 SimpleModeler
  tests, and 87 User Account tests;
- clean Account CAR packaging succeeds and CAR lint reports no deterministic
  FAIL.

The detailed comparison and artifact hashes are recorded in
`docs/journal/2026/07/cml-account-operation-value-migration-2026-07-16.md`.

Notification lifecycle modeling update on 2026-07-16:

- `UserNotificationStatus` is now a closed lifecycle-state type with
  `Queued`, `Sending`, `Sent`, `Delivered`, `Failed`, and `Canceled` values;
- `notificationLifecycle` defines delivery start, acceptance, confirmation,
  failure, retry, and cancel transitions;
- recipient read and dismissal data remains in `NotificationUserState` rather
  than being conflated with delivery lifecycle state;
- `NotificationDeliveryAttempt.status` and its create/search operation fields
  now use the independent `UserNotificationDeliveryResultStatus` closed type
  with `Pending`, `Succeeded`, and `Failed` outcomes;
- Cozy carries the state-machine name, state field, source state, target state,
  and numeric state values through SimpleModeler into the generated CNCF
  transition rules;
- CNCF compares the current and proposed records at the entity update boundary,
  executes the matching semantic transition for a declared state change, and
  rejects undeclared state changes without persisting them;
- generated executable specifications reject cross-contract and undeclared
  values, verify the emitted transition topology, execute `Queued -> Sending`,
  and reject `Queued -> Delivered` while preserving the persisted state.

The decision is recorded in
`docs/journal/2026/07/cml-notification-lifecycle-modeling-2026-07-16.md`.

Account lifecycle modeling update on 2026-07-16:

- `UserAccountStatus` is now generated from CML as the closed
  `provisional`, `registered`, `formal`, and `suspended` vocabulary;
- its datastore values remain `0`, `1`, `2`, and `3`;
- `UserAccount.status`, account create/update/list operation Values, and the
  `status` state machine use the same generated type;
- the generated state-machine rules carry source and target states and storage
  values, and handwritten status and transition-table sources were removed;
- access and refresh sessions continue to express lifecycle through issue,
  expiry, revocation, and rotation timestamps rather than an invented string
  state vocabulary.

The decision is recorded in
`docs/journal/2026/07/cml-account-lifecycle-modeling-2026-07-16.md`.

Account operation semantic-type alignment update on 2026-07-16:

- registration and login-name lookup Values now use
  `UserAccountLoginName`, matching `UserAccount.loginName`;
- status-update suspension reason now uses
  `UserAccountSuspensionReason`, matching
  `UserAccount.suspensionReason`;
- generated metadata specifications verify that matching entity and operation
  concepts no longer split into semantic entity types and generic operation
  strings.

Domain-scalar ownership audit on 2026-07-16:

- the nine remaining notification domain scalars and ten remaining account
  domain scalars all correspond to explicit accepted-inventory entries;
- all nineteen nominal Scala types are generated from the driver CML into
  `target/scala-3.3.8/src_managed/main`;
- neither driver keeps handwritten scalar-wrapper source under
  `src/main/scala`;
- unresolved normalization, length, redaction, and structured-data decisions
  remain properties of the accepted CML contracts rather than justification
  for parallel handwritten wrappers.

The audit is recorded in
`docs/journal/2026/07/cml-domain-scalar-generated-ownership-audit-2026-07-16.md`.

Account authentication request scalar update on 2026-07-16:

- request-only password fields now use the predefined nonlocalized `Password`
  runtime type with an explicit 1..1024 input boundary;
- login identifiers, challenge IDs, verification codes, and
  proof/reset/refresh values use predefined `Token` with role-specific bounds;
- login identifiers intentionally do not use the narrower lexical
  `Identifier`, because the command accepts login names or email addresses;
- generated Scala constructors, operation metadata, and Web validation hints
  carry the same authored constraints without primitive `String` fallback;
- password strength and confidentiality-aware redaction remain separate
  policy/runtime responsibilities.

The decision is recorded in
`docs/journal/2026/07/cml-account-authentication-request-scalar-boundary-2026-07-16.md`.

Generated driver semantic-type verification on 2026-07-16:

- `GeneratedSemanticTypeContractSpec` in both driver CARs reads the Scala
  3.3.8 sources produced by the normal sbt-cozy generation path;
- the contracts cover every classified driver field across generated Create,
  Read, Query, Update, and operation Value case classes, including nominal and
  predefined scalars, lifecycle vocabularies, timestamps, identifiers, and
  structured profile/attribute values;
- each generated occurrence must retain its nominal, predefined, powertype,
  statemachine, or structured type and rejects primitive `String`,
  `Option[String]`, `Condition[String]`, and `Update[String]` fallback;
- both focused executable specifications pass against the current driver CML;
- full driver validation passes with 92 account tests and 28 notification
  tests, and both CAR lint runs report no deterministic failure.

## Stage 16.7: Verification and Closure

Stage Status:

- Current status: OPEN
- Owner: cozy-modeler
- Checklist basis: `CML16-09`
- Update rule: update when `CML16-09` verification evidence changes

Focus:

- run focused and full Cozy executable specifications;
- validate representative downstream CAR generation;
- promote verified behavior from notes to design, specification, and accepted
  grammar documents;
- close only from checklist evidence.

## Closure Condition

Phase 16 closes only when every required item in
`docs/phase/phase-16-checklist.md` is checked, the accepted grammar has been
promoted out of notes, the semantic scalar and I18N contracts are specified,
Cozy focused and full specifications pass, predefined Results are verified
against CNCF runtime ownership, plain Datatypes generate nominal Scala types
without primitive fallback, and both driver CARs explicitly version and verify
their breaking generated contracts.

## References

- `docs/phase/phase-16-checklist.md`
- `docs/journal/2026/07/cml-operation-value-refactoring-discussion-2026-07-15.md`
- `docs/notes/cml-operation-value-refactoring-spec-proposal.md`
- `docs/notes/cml-semantic-scalar-modeling-spec-proposal.md`
- `docs/notes/cml-grammar-latest.md`
- `docs/journal/2026/04/cml-operation-input-output-discussion-result.md`
- `docs/journal/2026/04/cml-operation-design-note.md`
