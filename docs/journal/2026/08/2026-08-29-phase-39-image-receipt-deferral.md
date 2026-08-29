# Phase 39 Image and PDF Receipt Deferral

Date: 2026-08-29

## Decision

The user resolved `P39-DEC-IMAGE-RECEIPT-001` by removing Markdown image
admission and PDF receipt requirements from Phase 39.

## Disposition

- `DEV-012` is the dedicated Development Candidate for the deferred work.
- SmartDox owns the Markdown parser and normalized image model. The work must
  not be implemented by a Cozy source preprocessor or a local shadow model.
- `cozy pdf` does not produce a receipt. A future requirement for one needs its
  own Cozy contract and must not reuse `cozy.media.receipt.v2`.
- No SmartDox repository mutation, Cozy image admission, receipt schema, push,
  publication, or downstream acceptance was authorized by this decision.

## Phase 39 Consequence

Phase 39 retains PDF help, `--latex-format` validation, and the separation of
the PDF format namespace from Cozy Media `--profile`. Its checklist records the
deferred items as Future Development Candidate entries, so they cannot be
silently treated as incomplete Phase 39 work.
