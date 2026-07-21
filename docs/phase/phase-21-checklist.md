# Phase 21 Checklist

This checklist is the authoritative progress ledger for Phase 21: Component
Repository Discovery.

## CR21-01: CNCF Repository Index Contract

Status: DONE

- [x] Define the versioned `repository/catalog/index.json` schema.
- [x] Define CAR/SAR kind, artifact identity, catalog link, status, selector,
      and source-provenance semantics.
- [x] Define index/detail identity validation and relative-path safety rules.
- [x] Define compatibility behavior for a repository with no public index.
- [x] Keep availability snapshot and runtime registration/health distinct.
- [x] Publish deterministic valid and invalid contract fixtures.

## CR21-02: Cozy Publication and Lint

Status: DONE

- [x] Update the public index from `cozy publish-car`.
- [x] Update the public index from `cozy publish-sar`.
- [x] Write index changes atomically and order entries deterministically.
- [x] Preserve unrelated CAR/SAR entries during publication.
- [x] Validate index kind, identity, catalog path, selectors, and detailed
      catalog correspondence.
- [x] Reject traversal, duplicate identity, conflicting kind, and stale
      selector metadata.
- [x] Keep local lint deterministic and network-free.
- [x] Add CLI help, repository documentation, and executable specifications.

## CR21-03: Textus Launcher Public Discovery

Status: DONE

- [x] Define `textus repository list`, `show`, and bounded `refresh` grammar.
- [x] Retrieve only explicitly configured repository indexes.
- [x] Cache index content with source URL, retrieval time, schema, and
      diagnostics.
- [x] Combine public, cache, and local repository entries using documented
      precedence.
- [x] Resolve a selected index entry through its detailed CAR/SAR catalog.
- [x] Preserve known-artifact resolution when an index is absent.
- [x] Avoid CAR/SAR archive download during list operations.
- [x] Cover offline, stale-cache, malformed-index, and conflicting-source cases.

## CR21-04: CNCF Launcher Development Discovery

Status: DONE

- [x] Define the development/local repository list and show command grammar.
- [x] Admit development directories explicitly and derive identity from their
      descriptors rather than directory names.
- [x] Normalize development and local/public artifact identities to the shared
      CNCF entry model.
- [x] Report freshness and safe diagnostics without exposing credential-bearing
      locators to untrusted output.
- [x] Keep repository discovery separate from component process lifecycle.
- [x] Prove output identity compatibility with Textus Launcher fixtures.

## CR21-05: Cozy BoK Component Repository

Status: IN PROGRESS

- [x] Consume the public index as the CAR/SAR discovery source.
- [x] Preserve the existing CAR catalog pages and add complete SAR list/detail
      pages independent of SIE Project references.
- [x] Add CAR/SAR counts and navigation to Component Repository dashboards.
- [ ] Link entries to Project, descriptor, ABI, CML, model metadata, Help,
      Manual, OpenAPI, and MCP metadata when declared.
- [x] Report index/catalog mismatch and unavailable source diagnostics.
- [x] Preserve fallback behavior for known catalogs when `index.json` is absent.

## CR21-06: Cross-Repository Verification and Closure

Status: PLANNED

- [ ] Verify one repository containing multiple CARs and SARs.
- [ ] Verify stable, snapshot, disabled, missing, and conflicting entries.
- [ ] Verify Textus and CNCF Launcher normalize the same artifact identity.
- [ ] Verify BoK renders the same CAR/SAR set as the public index.
- [ ] Run focused and full tests in every modified repository.
- [ ] Run `git diff --check` in every modified repository.
- [ ] Complete post-implementation review and fix all actionable findings.
- [ ] Record operational evidence and close Phase 21 from checklist results.
