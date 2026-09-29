# Document Source Admission

`document-project sources validate --core <core.yaml> --document <document.yaml> --summary <summary.yaml>` is a read-only admission command for one direct Core and its localized v2 Document and Summary sources. It neither renders nor writes output, evidence, receipts, or source files.

`CozyDocumentLogicTree.loadSourceCore`, `CozyDocumentDescriptionV2.loadSourceDocument`, and `loadSourceSummary` share the production semantic validators with the existing hash-bound loaders. The Core, Document, and Summary IDs must agree; locale values must match their direct locale directory; and the Document and Summary must share one direct locale parent below the Core content parent. YAML remains UTF-8, closed-schema, duplicate-key, anchor, tag, and symlink safe.

On this new source route only, the historical `identity` property in Core and Document bindings may be absent or have any YAML value. It is ignored: it is not parsed, retained, calculated, compared, emitted, or repaired. All other binding properties are rejected. Legacy loaders and projections retain their existing hash-bound contracts; their migration belongs to the successor work, not this slice.

Executable behavior is specified by `CozyDocumentSourceAdmissionSpec` (E-SRC-001 through E-SRC-007). File-update constraints remain governed by [file update management](file-update-management.md).
