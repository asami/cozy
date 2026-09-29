# Video Project Storyboard Narration Design

Status: selected bounded implementation design

The synthesize command has one provider pipeline and an immutable input
projection. No-part construction produces the legacy script input exactly as
before. Explicit `--part` first plans the real project, selects one declared
native Storyboard part, and uses that existing planned `VideoScript` directly.
It does not serialize a second script, load the legacy twenty-scene source, or
add a dispatcher, renderer, global graph planner, transaction, retry, or
rollback layer.

The projected Storyboard source remains the input provenance because the
renderer and narration consumers must agree on the combined-audio basename.
The real project descriptor/root/tools travel with the input so provider
configuration and tool checks stay in the project responsibility boundary.

The public input change is one optional `part` field appended to
`SynthesizeConfig`. Existing six-field construction and no-part behavior remain
source compatible. Case-class binary ABI compatibility is not asserted.

Project-mode output admission is deliberately pre-start and local: equality to
the planned audio directory, canonical project containment, existing-link and
existing-target rejection. It protects the command boundary without claiming
atomic provider/output execution after a provider has begun.
