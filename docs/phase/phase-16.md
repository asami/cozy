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
- existing dirty work in both driver repositories remains untouched while the
  Cozy-side contract is being established.

## Stage 16.6: Scaffold and Migration

Stage Status:

- Current status: OPEN
- Owner: cozy-scaffold
- Checklist basis: `CML16-08`
- Update rule: update when `CML16-08` scaffold or migration evidence changes

Focus:

- emit canonical Value grammar from Cozy scaffolds;
- retain read compatibility for legacy CML;
- migrate `textus-user-notification` as the primary representative CAR;
- validate `textus-user-account` as the larger operation-contract regression
  driver;
- compare generated metadata and API/ABI before and after each migration;
- verify that user-account literate contract metadata is not changed by Value
  normalization;
- replace string-only wrappers according to the accepted classification and
  compare generated validation, storage, form, and API contracts.

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
against CNCF runtime ownership, and both driver CARs preserve or explicitly
version their generated contracts.

## References

- `docs/phase/phase-16-checklist.md`
- `docs/journal/2026/07/cml-operation-value-refactoring-discussion-2026-07-15.md`
- `docs/notes/cml-operation-value-refactoring-spec-proposal.md`
- `docs/notes/cml-semantic-scalar-modeling-spec-proposal.md`
- `docs/notes/cml-grammar-latest.md`
- `docs/journal/2026/04/cml-operation-input-output-discussion-result.md`
- `docs/journal/2026/04/cml-operation-design-note.md`
