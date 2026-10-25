# Document Project Reader Projection v2 Specification

## Status and scope

This is the normative DM06 specification for the pure Document Project Reader
Projection v2 in Phase 71.1 / P710-03-DM. It is paired with the
[Document Project Reader Projection v2 design](../design/document-project-reader-projection.md)
and its executable coverage is
[`CozyDocumentReaderProjectionV2Spec`](../../src/test/scala/cozy/document/CozyDocumentReaderProjectionV2Spec.scala).
The existing [local-build target specification](document-project-local-build-targets.md)
continues to own target selection, direct inputs, freshness, output installation,
and failure preservation.

This specification supersedes only reader-output digest and hash-traceability
claims. It does not supersede the v2 source-admission or model contract, the
confirmation vocabulary schema, or confirmation rendering.

## Pure reader contract

The reader accepts an already admitted `ValidatedDocument` and a confirmation
vocabulary. It projects one deterministic, self-contained HTML string. The
projection makes no reuse, freshness, approval, receipt, or identity choice;
the result carries HTML only.

The reader MUST NOT read `ValidatedDocument.coreIdentity` or
`ValidatedDocument.documentIdentity`, calculate a digest, or emit a digest,
identity, or hash traceability field. The root retains semantic `data-*` IDs for
the Document and Core. The former Core/Document/output hash audit is absent.
The `identitiesHeading` vocabulary remains required because Step claim-ID audit
details use it; `coreIdentityLabel`, `documentIdentityLabel`, and
`outputIdentityLabel` are not reader vocabulary requirements.

Legacy identity values, including null, blank, and non-hash values, do not
change reader HTML. They remain outside this pure projection contract.

## Preserved reader surface

The projection MUST preserve authored Japanese or escaped title, headings, and
prose; source order and recursive hierarchy; all closed block representations;
all five reference categories; bidirectional passage/Step correspondence;
containment versus direct-child Flow versus Step-local Structure; empty-Flow
wording; native accessible details; keyboard anchors; and responsive/print CSS
without external assets or scripts. Logical IDs and their semantic `identity`
CSS class remain references, not hashes.

Reader vocabulary validation still rejects missing or blank actual reader
wording and missing or blank required logical-pattern, node-role, relation-type,
or Flow-type map entries with `READER_V2_VOCABULARY`. It retains the required
`identitiesHeading` check for claim IDs.

## Executable coverage

The executable specification covers deterministic escaped output without digest
attributes, byte equality across varying legacy identity values, byte equality
and escaping across varying authored title/prose, acceptance of obsolete
identity wording fields, required wording/map failures, source order and
hierarchy, all five reference categories and multiple targets, and the distinct
accessible containment, Flow, and Structure surfaces.

The existing loader fixtures and their hash fields remain unchanged for the
next source-admission/model frontier. This specification adds no hash fixture,
compatibility reader, or source-loading behavior. It makes no Phase or Step
closure claim.

## Phase 71.1 DM08 supersession

Phase 71.1 / P710-03-DM-08 removes the referenced typed V2 hash fields and
fixture rebinding premise. Metadata-independence coverage now loads varied
id-valid sources through production V2 admission and proves identical reader
HTML for unchanged semantics. The reader continues to receive only current
semantic values; this does not change its output, vocabulary, or any Phase
completion status.

## Phase 87 additive figure/table amendment

Phase 87 extends the reader input with supplied immutable materialized
PNG/JPEG asset values. The projection remains pure with no filesystem or
network access; the existing overload supplies empty assets and fails
explicitly if a Figure needs one. Render semantic figure/figcaption/alt and
accessible table/caption/column headers, preserving exact Core membership,
source order, complete cells, backlinks, keyboard access and print behavior.
Embedded inert data URIs preserve self-contained output.

The [explanatory figure/table contract](document-project-explanatory-figures-and-tables.md)
supersedes only the earlier closed-kind, asset and pure-input clauses for this
extension. Implementation and executable acceptance are in progress; no PASS
is claimed. Phase 71 hash-removal semantics remain authoritative.
