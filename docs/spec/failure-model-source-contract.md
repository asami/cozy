# Failure Model source contract

*Since / Version:* Oct. 3, 2026

This SOURCE contract is specified by `FailureModelCmlSpec`. Cozy owns authoring,
strict source admission and immutable development metadata. CNCF Phase 91 owns
`ResolvedFailureModel`, recipient validation and runtime resolution. sm-workflow
consumes that recipient-resolved contract. Execution names imply no failures or
mechanisms.

## Authored notation

| Declaration | Exact section path | Direct fields |
|---|---|---|
| Defaults | EXECUTION-MODEL / execution identity / FAILURE-MODEL / failure identity | `scope = IN_SCOPE` or `scope = OUT_OF_SCOPE` |
| Selector | COMPONENT / component identity / EXECUTION-MODEL | `model = execution identity` |
| Component delta | COMPONENT / component identity / FAILURE-MODEL / failure identity | `scope` |
| Service delta | SERVICE / service identity / FAILURE-MODEL / failure identity | `scope` |
| Operation delta | SERVICE / service identity / OPERATION / operation identity / FAILURE-MODEL / failure identity | `scope` |

Services nested directly in a component retain their original full section path.
PACKAGE retains its established meaning. Identities are nonempty, trimmed and
case-sensitive; control characters and dot/slash separators are forbidden. Failure
identities are opaque authored strings, including punctuation such as quotes and
backslashes. They are never a global catalog or implementation mechanism.

Each execution definition has exactly one nonempty FAILURE-MODEL container.
Each selector has exactly one model field. Only scope is legal in failure leaves;
only model is legal in selectors. Unknown/repeated fields, repeated containers,
duplicate names or entries (including equal repeated values), nested leaf children,
empty scopes and malformed scope tokens are rejected. A declared delta container
must be nonempty. An absent delta is an empty vector with source None.

## Ownership and admission

Original LogicalSection trees are scanned before normalization. EXECUTION-MODEL
and FAILURE-MODEL are legal only at the positions above. Promoted SERVICE sections
are scanned once at their original position. WORKFLOW/FAILURE-POLICY is separate.
All execution definitions are validated, including unused definitions.

A source bearing these declarations requires exactly one explicit resolved
component. A selected component may have no services. Refinements require a
selector and owners matching actual Kaleidox component/service/operation identities;
every delta entry must name a selected default. All resolved services and operations
inherit even without authored deltas. Sources without declarations return the
legacy empty vector, including legacy multi-component sources. Valid unused
execution definitions emit no component contract.

`normalize` returns Either[Vector[FailureModelIssue], Vector[ComponentFailureModel]].
There is no partial successful contract on rejection. Stable issue codes are
FAILURE_MODEL_INVALID_STRUCTURE, FAILURE_MODEL_INVALID_SCOPE,
FAILURE_MODEL_DUPLICATE, FAILURE_MODEL_UNKNOWN_EXECUTION,
FAILURE_MODEL_UNKNOWN_FAILURE and FAILURE_MODEL_INVALID_OWNER. The ModelBuilder
adapter maps issues to the established syntaxErrorFault boundary before creating
a builder, preserving its constructor arity and normal/value admission parity.

## Ordered immutable projection

Merge defaults, component delta, service delta and operation delta in that order.
An explicit narrower value wins in either direction. OUT_OF_SCOPE is a negative
constraint on the effective contract, not an irreversible ancestor prohibition.
Default order is unchanged; no failure is invented. Sibling deltas are isolated.

Public immutable case classes retain execution defaults and each owner's delta,
source and effective contract. Effective entries retain all authored origins in
merge order and the final winning source. Every source identity contains
declarationKind, full sectionPath and actual optional line; missing locations
remain None. Structured owner kind/identity fields distinguish owners rather
than concatenating identities. The pure projection is an executable comparison
oracle for recipient construction, with no runtime guarantees, retry, locking,
rollback, durability, hash or distributed semantics.

S72.2's accepted [generated ABI contract](failure-model-abi-contract.md) owns
generation and library-target policy. SOURCE commit
`6c15b65cd83181fdf61aa8674c9a847f79751805` and ABI commit
`1d9c80775e74f7d0ba0dd60f1cf4730ef84c3843` are accepted. The authored
[S72.3 recipient handoff](../phase/phase-72-cncf-handoff.md) gives the concrete
fixture oracle and CNCF91/sm-workflow acceptance prerequisites; HANDOFF is accepted at commit
`783a48552de5fa6a4b34a8dfe9c1184f005de62f`; the sole Phase-wide review passed.
Final full validation passed; the containing distinct parent manual local release commit closes Cozy Phase 72. These instructions do not change this normative source contract.
