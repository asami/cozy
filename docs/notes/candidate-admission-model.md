# Candidate-Admission Model in CML Workflow

- Date: 2026-09-21
- Status: Design principle
- Scope: CML StateMachine / Workflow producer model

## Purpose

Candidate-Admission Model (CAM) is the general AI/Human semantic-work pattern that Cozy CML Workflow must be able to express without embedding a particular AI provider.

> Semantic actor constructs a candidate. StateMachine admits it. Runtime commits it.

Cozy owns the declarative model and generated ABI needed to preserve this separation; CNCF owns runtime admission/progression/commitment.

## CML interpretation

A semantic Action does not own transition authority. It produces a typed candidate/result plus evidence. StateMachine guards/transitions and deterministic admission Actions decide whether that result is admissible.

~~~text
deterministic prepare
  -> semantic Action
  -> typed Candidate/Result/Evidence
  -> deterministic admission
  -> transition
~~~

JudgmentAction is the smallest direct example. A larger Workflow may use the same pattern for closure submission and evidence-driven admission.

## Admission Gap and Continuation

CML should be capable of describing the typed semantic requirement whose absence causes suspension, while leaving transport/provider details outside the model. Runtime may project that requirement as a Continuation.

The model should not require a rigid semantic Action merely to reproduce evidence already supplied and still fresh. Evidence requirements, scope/freshness metadata, and deterministic admission should remain expressible through generated typed contracts.

## Producer constraints

- no AI/Codex/jev-specific Action identity;
- no next-state directive in semantic Result;
- typed Result/Evidence contracts preserve enough identity for admission;
- deterministic admission is distinct from semantic work;
- generated ABI preserves Action/Result/Evidence provenance and declared progression;
- runtime commitment is not generated as an external semantic-worker responsibility;
- domain-specific candidate payloads remain application-owned.

sm-workflow is the initial reference consumer for validating this model. Cozy should generalize only concepts required across domains such as software delivery, organizational approval, peer review, publication, and knowledge admission.
