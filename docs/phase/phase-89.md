# Phase 89 — Strict Scala 3 generation integration

status=planned
execution_started=false

Purpose: Strict Scala 3 generation integration.
Progress: [Phase 89 checklist](phase-89-checklist.md).
This is the producer's own Phase; sm-workflow Phase 6.12 is its consumer.

Planned: 2026-11-09
Consumer: [sm-workflow 6.12-B/C](../../../sm-workflow/docs/phase/phase-6.12.md)
Coordination: [delivery dependencies](../../../sm-workflow/docs/phase/phase-6.12-dependencies.md)
Behavior: [strict Scala contract](../../../sm-workflow/docs/spec/scala-lint-profiles.md#three-stage-strict-scala-3-adoption)

## Scope

Deliver SLP-COZY through normal CML projection and Scala generation. The
`cozy.modeler.ScalaGenerator` route uses SimpleModeler's Scala3RealmTransformer;
SimpleModeler owns its templates and already emits strictEquality in the shared
class base. Cozy must also inventory Scala fragments it emits directly, which
are not automatically covered by that base.

Keep library versus CNCF targets and other language backends distinct. Preserve
the generated/handwritten source boundary; generator qualification does not
require linting managed sources as handwritten code. CAR boundary lint remains
Cozy-owned and is reused, not reimplemented by the new Scalafix package.

## Work and acceptance

The [Phase 89 checklist](phase-89-checklist.md) is the completion
ledger. Its existing delivery IDs and acceptance criteria are retained unchanged.

## Dependencies and bounded completion

The B route waits only for
[SimpleModeler GEN-03](../../../simple-modeler/docs/phase/phase-1.md)
and applicable
[sbt-cozy build qualification](../../../sbt-cozy/docs/phase/phase-1.md).
The C route additionally consumes
[Core](../../../goldenport-core/docs/phase/phase-4.md)
and generator C adoption. Model-owned incompatibilities go to
[SimpleModeling Model](../../../simplemodeling-model/docs/phase/phase-1.md).
Do not wait for any producer's whole Phase, sm-workflow completion or a new
generator/consumer build cycle. Missing C delivery does not stop independent B
qualification or unrelated Cozy work. Reuse existing accepted generator/build
evidence when its revision, scope and conditions apply.

This planned Phase changes no existing Phase priority or acceptance,
including the existing staged Phase 77 work. Preserve all Goals, Sessions,
history, counts and unrelated changes. No build, publication, commit, deployment
or workflow operation is started by this document.
