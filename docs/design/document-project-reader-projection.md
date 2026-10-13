# Document Project Reader Projection v2 Design

## Status and authority

This design records the bounded DM06 reader boundary in Phase 71.1 / P710-03-DM.
The normative contract is the paired
[Document Project Reader Projection v2 specification](../spec/document-project-reader-projection.md).
The local-build producer and freshness behavior remain owned by the
[Document Project Local Build Targets design](document-project-local-build-targets.md).

## Projection responsibility

`CozyDocumentReaderProjectionV2` is a pure presentation projection over an
already admitted v2 Document Description, its validated Core, and the shared
confirmation vocabulary. It renders authored Document prose and the typed Core
surfaces together so readers can navigate the same semantic references in both
directions. It does not decide whether a generated file is current or reusable.

The projection renders once. It does not calculate, compare, disclose, or
preserve a local output digest. Core and Document identity fields may still
exist in the unfinished v2 loading/model boundary, but this reader never reads
them. Semantic Core, Document, Step, Claim, Node, Relation, Flow, passage, and
target IDs remain ordinary navigational references and keep their existing
anchors and `identity` CSS class.

## Preserved interaction and layout

The narrow deletion removes only the root digest attributes and the bottom
Core/Document/output hash audit. It retains authored text escaping, ordering,
recursive sections, closed block rendering, all five reference inventories,
bidirectional correspondence, native details, keyboard-operable anchors,
containment, direct-child Flow, Step-local Structure, empty-Flow wording, and
responsive/print styling. The Step claim-ID audit continues to use
`identitiesHeading`; obsolete identity label wording is not part of reader
validation.

No external script or asset is introduced. The unused `.audit dl` and `.audit dd`
rules are now removed; `.audit`, `.audit summary`, and every selector supporting
semantic IDs or Step claim details remain.

## Boundaries and unfinished work

This design supersedes only reader output digest and hash-traceability claims.
It does not redesign v2 source admission, alter `ValidatedDocument` or loader
fixtures, change confirmation vocabulary schema, or change the confirmation
projection. Those source/model and confirmation consumers remain unfinished
next-frontier work. Local-build path selection, declared inputs, mtime freshness,
receipt/adoption policy, output preservation, and producer dispatch remain under
their existing contracts. This DM06 design makes no Phase or Step closure claim.

## Phase 71.1 DM08 supersession

Phase 71.1 / P710-03-DM-08 completes the bounded source/model deletion that
this document left unfinished. The reader's model has no Core or Document hash
fields, and metadata-independence is established by production V2 loading of
unchanged semantic sources. No reader layout, vocabulary, currentness, receipt,
or local-build policy is changed.
