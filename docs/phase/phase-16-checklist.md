# Phase 16 Checklist

This checklist is the authoritative progress ledger for Phase 16: CML Value
and Datatype Refactoring.

## CML16-01: Discussion and Planning Record

Status: DONE

- [x] Record the 2026-07-15 operation Value refactoring discussion in journal.
- [x] Record the provisional operation Value grammar in notes.
- [x] Record the provisional semantic scalar and I18N classification in notes.
- [x] Register CML Value and Datatype Refactoring as Phase 16 in strategy and
      phase documents.
- [x] Set Phase 16 as the active phase in `docs/phase/README.md`.
- [x] Assign `textus-user-notification` as the primary migration driver.
- [x] Assign `textus-user-account` as the full-contract regression driver.

## CML16-02: Notes Implementation Proposal and Executable Baseline

Status: DONE

- [x] Review both implementation proposal notes and resolve ambiguities that
      block implementation.
- [x] Record investigation and decisions in journal entries while preserving
      their point-in-time context.
- [x] Record the complete candidate behavior and compatibility boundaries in
      `docs/notes` before parser/modeler changes and keep those notes current
      during implementation.
- [x] Add failing executable specifications before parser/modeler changes.
- [x] Cover canonical, compatibility, invalid, and metadata forms.
- [x] Classify and preserve the current working-tree changes in both drivers
      before recording their migration baselines.

## CML16-03: Top-Level Value Input Kind

Status: DONE

- [x] Parse `input-kind=COMMAND|QUERY` on top-level `# VALUE` definitions.
- [x] Require `input-kind` when a top-level Value is used as an operation input.
- [x] Reject unsupported input kinds with a location-aware Value diagnostic.
- [x] Reject operation-kind and input-kind mismatch.
- [x] Normalize top-level `# COMMAND`, `# QUERY`, and compatibility
      `VALUE/EXTENDS` through the same operation input-kind resolver.
- [x] Keep `inputValueKind` generator metadata stable as `COMMAND_VALUE` or
      `QUERY_VALUE`.

## CML16-04: Local Input Value

Status: DONE

- [x] Parse `INPUT/ATTRIBUTE` as an anonymous local input Value.
- [x] Generate `<OperationName>Command` for anonymous command input.
- [x] Generate `<OperationName>Query` for anonymous query input.
- [x] Parse `INPUT/VALUE` plus input schema as a named local Value.
- [x] Infer local input kind from the enclosing operation.
- [x] Preserve the existing nested named inline Value form.
- [x] Reject `INPUT TYPE` combined with a local input definition.
- [x] Define and test `PARAMETER` compatibility and generated-name migration.

## CML16-05: Local Output Result

Status: DONE

- [x] Parse `OUTPUT/ATTRIBUTE` as an anonymous local Result.
- [x] Generate `<OperationName>Result` for anonymous local output.
- [x] Parse `OUTPUT/VALUE` plus output schema as a named local Result.
- [x] Apply implicit `OperationResult` semantics to canonical local output.
- [x] Preserve the existing nested named inline Result form.
- [x] Reject `OUTPUT TYPE` combined with a local output definition.
- [x] Emit deterministic `resultFields` metadata.

## CML16-06: CML/CNCF Predefined Results

Status: DONE

- [x] Define CNCF runtime ownership for the predefined Result catalog.
- [x] Provide and verify `UnitResult` in CNCF.
- [x] Provide and verify `IntResult` with `value: int` in CNCF.
- [x] Make Cozy resolve the selected CNCF Result catalog.
- [x] Reject unknown predefined Result names.
- [x] Reject raw scalar operation output types.
- [x] Emit catalog-backed Result field metadata.

## CML16-07: Semantic Scalar and Text Range Contract

Status: OPEN

- [x] Inventory every string-only `VALUE` and `DATATYPE` in both driver CARs
      through the normalized CML AST/model API, with 35 Datatypes and no
      string-only Values recorded in
      `docs/notes/cml-semantic-scalar-driver-inventory.md`.
- [ ] Finalize classification of each item as predefined scalar, constrained
      domain scalar, composite Value, powertype, statemachine-owned state, or
      intentional opaque text.
  - [x] Record a provisional category, localization direction, and decision
        state for all 35 current driver Datatypes.
  - [ ] Resolve the domain-decision rows before changing driver source.
- [x] Define when a finite vocabulary uses `POWERTYPE` and when lifecycle state
      requires `STATEMACHINE`, including open registries and externally owned
      lifecycle state.
- [x] Confirm the CML predefined text catalog baseline from existing
      `name = Name` and `title = I18nTitle` semantics.
- [x] Establish natural I18N as the modeling goal: ordinary user-visible
      semantic text becomes locale-aware without hand-built locale containers,
      while technical identity text remains explicitly nonlocalized.
- [x] Fix `title` as one locale-aware type that accepts both one locale entry
      and multiple locale entries; do not introduce separate scalar-title and
      I18N-title concepts.
- [x] Audit `string`, `text`, label, description, message, and related families
      against that baseline without requiring artificial scalar/I18N pairs;
      record their runtime role matrix and legacy/open boundaries.
- [x] Connect the descriptive text families to `DescriptiveAttributes`, keeping
      stored locale-aware values separate from non-destructive `effective*`
      display fallback.
- [ ] Define which concepts are intentionally nonlocalized, such as stable
      identifiers, and which use localized label/title/text contracts.
- [ ] Define normalization and default or required length semantics for each
      predefined text type.
- [x] Define canonical `min-length` and `max-length` text constraints without
      overloading numeric `min` and `max` semantics.
- [x] Apply text length constraints independently to each localized entry.
- [ ] Define locale identity, default-locale, allowed-locale, duplicate-locale,
      and fallback behavior.
- [x] Preserve text constraints in normalized model metadata and project them
      to generated Web validation hints.
- [x] Remove Web validation metadata as a source of domain constraints;
      `MAttribute.Web` remains presentation and input-control metadata only.
- [ ] Generate validation and schema constraints consistently for Scala,
      datastore, form, automatic REST/OpenAPI, and Help surfaces.
- [ ] Preserve all locale-tagged values in datastore and API contracts; locale
      fallback must not destructively collapse an I18N value to one string.
- [ ] Define redaction and display behavior for hashes, secrets, tokens, and
      other opaque text values.
- [ ] Add executable specifications for classification diagnostics, predefined
      text types, boundary lengths, and invalid text values.
- [x] Compile generated Scala 3.3.8 and execute Create/Update text-length
      validation for multiple locales and `DescriptiveAttributes` fields.
- [x] Replace the pending `TextSpec` coverage with executable current-runtime
      boundary, invalid-control-character, and value-preservation
      specifications; keep canonical CML `text` classification open.
- [x] Replace the pending `I18nStringSpec` coverage with executable plain
      locale binding, structured round-trip, effective fallback, and escaped
      leading-brace specifications.
- [x] Replace the pending `I18nMessageSpec` coverage with executable direct
      entry construction and fixed display-priority specifications; record its
      non-codec representation as a legacy exception.
- [x] Replace the pending `I18nLabelSpec` coverage with executable plain and
      structured shared-codec specifications; accept `I18nLabel` as the
      locale-aware runtime label baseline.
- [x] Replace the pending `I18nDescriptionSpec` coverage with executable plain
      and structured shared-codec specifications, and extend
      `DescriptiveAttributesSpec` with effective fallback and preservation;
      keep the canonical CML `description` constraints open.
- [x] Replace the pending `I18nBriefSpec` coverage with executable plain and
      structured shared-codec specifications, and verify locale-aware headline
      and brief selection through `DescriptiveAttributesSpec`; keep distinct
      CML headline and brief constraints open.
- [x] Replace the pending `I18nSummarySpec` coverage with executable plain and
      structured shared-codec specifications, and verify locale-aware summary
      fallback through `DescriptiveAttributesSpec`; keep summary-family role
      classification and constraints open.
- [x] Replace the pending `I18nTextSpec` coverage with executable plain and
      structured shared-codec specifications; classify `I18nText` as localized
      plain narrative text while retaining the SD-01B single-document-body
      contract for `ContentBody`.
- [ ] Decide whether the display-projection overloads from `I18nText` to
      `ContentBody` remain compatibility inputs or should be deprecated to
      prevent accidental multilingual-storage assumptions.
- [x] Align generated `derived=content` aliases with SD-01B by returning
      `ContentBody` and omitting the obsolete locale overload.
- [x] Replace the pending `I18nTitleSpec` coverage with executable single-locale
      construction, multi-locale codec, locale fallback, and preservation
      specifications.

## CML16-08: Scaffold, Compatibility, and Migration

Status: OPEN

- [x] Make new scaffolds emit `# VALUE` plus `input-kind` for reusable inputs.
- [x] Make new scaffolds prefer local Values for one-use input/output schemas.
- [x] Make new scaffolds use predefined Results for simple outputs.
- [x] Stop emitting top-level `# COMMAND` and `# QUERY` in new CML.
- [x] Keep legacy top-level and inline forms readable.
- [x] Add compatibility diagnostics for intentional generated-name or ABI
      changes.
- [ ] Migrate `textus-user-notification` after the new grammar is implemented.
- [ ] Compare notification generated operation metadata and API/ABI across the
      migration.
- [ ] Migrate or validate `textus-user-account` after the primary migration is
      stable.
- [ ] Compare account generated operation metadata and API/ABI across the
      migration.
- [ ] Verify that account use-case, precondition, postcondition, rule, and
      scenario metadata survives Value normalization unchanged.
- [ ] Replace notification finite vocabularies and lifecycle state with the
      accepted powertype/statemachine models.
- [ ] Replace account finite vocabularies and lifecycle state with the accepted
      powertype/statemachine models.
- [ ] Replace generic string fields with appropriate predefined semantic text
      types plus explicit length constraints in both drivers.
- [ ] Keep domain-specific scalar wrappers only where the accepted inventory
      records additional semantics.
- [ ] Compare generated validation, datastore, form, REST/OpenAPI, and Help
      contracts across both driver migrations.

## CML16-09: Verification and Closure

Status: OPEN

- [x] Run focused Modeler operation and Value executable specifications.
- [x] Run scaffold executable specifications.
- [x] Run full `sbt --batch test` in Cozy.
- [ ] Generate and validate at least one command and one query CAR.
- [ ] Run focused and full tests in `textus-user-notification`.
- [ ] Run focused and full tests in `textus-user-account`.
- [ ] Run CAR lint for both driver projects.
- [ ] Run `git diff --check`.
- [ ] Record verification evidence in `docs/phase/phase-16.md`.
- [ ] Promote verified responsibilities and invariants from notes to
      `docs/design`.
- [ ] Promote verified behavior from notes to `docs/spec` and the accepted CML
      grammar record.
- [ ] Confirm all Phase 16 items are complete or explicitly deferred with a
      relocation target.
- [ ] Close Phase 16 only from checklist evidence.
