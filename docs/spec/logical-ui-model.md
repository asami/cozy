# Logical UI Model v1 Specification

Document role: normative behavior contract for `cozy.logical-ui.v1`.
The ownership and layering rationale is in
[`docs/design/logical-ui-model.md`](../design/logical-ui-model.md).

## 1. Candidate document

The deterministic codec accepts and emits one closed candidate object with this
exact field set and canonical field order:

```json
{
  "schema": "cozy.logical-ui.v1",
  "version": 1,
  "kind": "candidate",
  "inputIdentity": "sha256:<64-lowercase-hex>",
  "componentSurfaces": [],
  "componentBindings": [],
  "useCases": {},
  "identity": "sha256:<64-lowercase-hex>"
}
```

The codec MUST reject a missing, unknown, duplicate, malformed, mistyped, or
wrong-version field before it exposes a candidate. It MUST reject an
`inputIdentity` or `identity` that differs from the normalized canonical value.
JSON object order is transport-only; canonical emission always uses the order
above. Array ordering is normalized as defined below before identity creation.

The candidate `identity` is the SHA-256 digest of canonical logical content:
`schema`, `version`, `componentSurfaces`, `componentBindings`, and `useCases`.
The self `identity`, codec `kind`, and transport formatting are not part of that
digest. `inputIdentity` is a distinct SHA-256 identity over that normalized
logical content under the `consumed-input` role.

## 2. Exact Component admission

A Component coordinate is exactly:

```json
{"namespace":"org.example.sales","id":"SalesOrder","version":"1.0.0"}
```

`namespace` is dot-separated canonical segments, `id` is one canonical segment,
and `version` is a nonempty trimmed identity value. `namespace.id` is the only
qualified Component name. A Component surface is exactly:

```json
{
  "component": {"namespace":"org.example.sales","id":"SalesOrder","version":"1.0.0"},
  "exportIds": ["order-summary"]
}
```

and a binding is exactly:

```json
{
  "component": {"namespace":"org.example.sales","id":"SalesOrder","version":"1.0.0"},
  "exportId": "order-summary"
}
```

At least one unique surface and binding are required. A coordinate may occur in
a binding only when it equals a declared surface coordinate including version.
An `exportId` is an opaque, nonempty, trimmed identifier and may occur only
when exactly listed by that matching surface. No source lookup, local name,
unexported element, fallback version, or inferred export is admissible.
Surfaces sort by full coordinate, export IDs lexicographically, and bindings by
full coordinate plus export ID; duplicates fail rather than being deduplicated.

## 3. UseCase layers

`useCases` is exactly:

```json
{
  "business": {"layer":"business","id":"business-confirm-sales-order"},
  "system": {"layer":"system","id":"system-confirm-sales-order"},
  "ui": {"layer":"ui","id":"ui-confirm-sales-order"},
  "realizations": [
    {
      "source": {"layer":"business","id":"business-confirm-sales-order"},
      "target": {"layer":"system","id":"system-confirm-sales-order"}
    },
    {
      "source": {"layer":"system","id":"system-confirm-sales-order"},
      "target": {"layer":"ui","id":"ui-confirm-sales-order"}
    }
  ]
}
```

All three layer identities MUST be nonempty and pairwise distinct. The candidate
MUST contain exactly the two shown realization relations, after which they are
sorted by source and target. Wrong-layer, missing, reversed, duplicate, direct
Business-to-UI, and additional links fail closed. This is a semantic identity
boundary, not an inheritance or a name-resolution rule.

## 4. Candidate and accepted authority

`CozyLogicalUi.candidate` is the only candidate constructor. It validates and
normalizes the supplied `CandidateInput` before calculating identity.
`CozyLogicalUi.accept` requires both a candidate and an `AcceptanceDecision`.
The decision has nonempty `decisionId` and a `candidateIdentity` exactly equal
to that candidate. Its `decisionIdentity` is SHA-256 of the canonical
acceptance-decision content. An accepted identity is SHA-256 of the candidate,
consumed-input, and decision identities under the `accepted` role, and MUST
differ from the candidate identity.

Raw authoring prompts, serialized codec bytes, generated HTML, route tables,
target code, and caches are not fields of `CandidateInput` or acceptance. They
cannot create accepted authority or affect semantic identities.

## 5. Executable specification

`src/test/scala/cozy/ui/CozyLogicalUiSpec.scala` is the executable
specification for canonical admission, closed-export and malformed-coordinate
rejection, three-layer realization validation, candidate/accepted separation,
canonical identity plus strict codec roundtrip, and non-authority boundaries.
It includes a ScalaCheck ordering property in addition to Given/When/Then
scenarios.
