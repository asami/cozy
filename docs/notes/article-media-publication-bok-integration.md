# Article Media Publication and BoK Integration

Status: promoted historical note

Date: 2026-08-03

This note remains exploratory history and is non-normative. Its accepted
material has been promoted through the required notes -> design -> spec path:

- `docs/design/article-media-publication.md` records stable responsibilities,
  boundaries, invariants, and lifecycle.
- `docs/spec/article-media-publication.md` defines the authoritative serialized
  records, correlation, validation policy, and required executable evidence.

## Historical Direction

The original exploration established that a Cozy BoK may associate an optional
exact-locale detailed infographic and internally hosted video with an article,
while final media artifacts remain Git-external in a configured repository.
The site and artifact repository are one logical deployment URL space, not one
source tree. Ordinary `cozy bok build` remains a metadata/site operation;
generation and transcoding remain explicit operations.

The accepted SmartDox Phase 1 contract is pinned at commit
`fa21316973416c24bca7f8e366d65572c72720b7` using development coordinate
`org.smartdox:smartdox_2.12:2.4.17-SNAPSHOT`. This is sufficient Phase 26
dependency admission; public/non-SNAPSHOT publication is not a start gate.
SmartDox Phase 1 remains closed.

The exploratory proposal previously combined SmartDox association fields with
Cozy artifact metadata. The promoted contract separates them: SmartDox receives
only its accepted article-media fields, while Cozy owns the integrity and
provenance projection. Refer to the design and specification above rather than
using this note as implementation authority.

## Related History

- `docs/journal/2026/08/article-media-publication-bok-integration-handoff-2026-08-03.md`
- `docs/phase/phase-26.md`
- `docs/phase/phase-9.md`
- `docs/phase/phase-10.md`
- `docs/phase/phase-19.md`
