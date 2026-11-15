# Phase 89 — Strict Scala 3 generation integration

status=planned
execution_started=false

Purpose: Strict Scala 3 generation integration.
Progress: [Phase 89 checklist](phase-89-checklist.md).
This is the producer's own Phase; sm-workflow Phase 8.1 is its consumer
(renumbered from 6.12 on 2026-11-11).

Planned: 2026-11-09
Development item: DEV-042 (Strategy registration 2026-11-16)
Updated: 2026-11-16
Consumer: [sm-workflow 8.1-B/C](https://github.com/asami/sm-workflow/blob/main/docs/phase/phase-8.1.md)
Coordination: [delivery dependencies](https://github.com/asami/sm-workflow/blob/main/docs/phase/phase-8.1-dependencies.md)
Behavior: [strict Scala contract](https://github.com/asami/sm-workflow/blob/main/docs/spec/scala-lint-profiles.md#three-stage-strict-scala-3-adoption)

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
[SimpleModeler SLP-GEN / GEN-03](https://github.com/asami/sm-workflow/blob/main/docs/phase/phase-8.1-dependencies.md#dependency-order-and-early-handoffs)
and applicable
[sbt-cozy SLP-BUILD qualification](https://github.com/asami/sm-workflow/blob/main/docs/phase/phase-8.1-dependencies.md#dependency-order-and-early-handoffs).
The C route additionally consumes
[Core SLP-CORE](https://github.com/asami/sm-workflow/blob/main/docs/phase/phase-8.1-dependencies.md#ownership-and-delivery-units)
and generator C adoption. Model-owned incompatibilities go to
[SimpleModeling Model SLP-MODEL](https://github.com/asami/sm-workflow/blob/main/docs/phase/phase-8.1-dependencies.md#ownership-and-delivery-units).
Do not wait for any producer's whole Phase, sm-workflow completion or a new
generator/consumer build cycle. Missing C delivery does not stop independent B
qualification or unrelated Cozy work. Reuse existing accepted generator/build
evidence when its revision, scope and conditions apply.

The links above select the confirmed coordination contract and its delivery
IDs. The producer-local Phase documents formerly linked here could not be
located in the inspected checkouts on 2026-11-16; their location and accepted
handoffs remain unconfirmed. Before COZY-02/04 consumption, record the actual
producer-owned contract, selected revision and accepted subset. The coordination
table and existing implementation alone are not producer acceptance. Do not
guess replacement Phase numbers or unavailable APIs.

This planned Phase changes no existing Phase priority or acceptance,
including the existing staged Phase 77 work. Preserve all Goals, Sessions,
history, counts and unrelated changes. No build, publication, commit, deployment
or workflow operation is started by this document.
