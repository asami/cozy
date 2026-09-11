# Phase 58 Checklist: Recursive Content Core Logic Tree Vertical Slice

Phase Status: COMPLETE

Development item: DEV-024
phase=[Phase 58](phase-58.md)

Planning rule: one executable vertical slice; preferred 4–8 h band.

## P58-01A: Recursive Core authority

Stage Status:
- Current status: DONE
- Owner: Cozy Document Project
- Update rule: Update this block from the checklist ledger; do not mark it DONE
  until the recursive contract and real Article 9 Core are both accepted.

- [x] Promote the recursive logic-tree decision into paired design and
      Markdown specification documents.
- [x] Define `content/core.yaml` without a locale suffix or root locale field.
- [x] Define one recursive Step type owning local Structure, child Flow, and
      child Steps.
- [x] Reuse compatible Logical Pattern, semantic-node, typed-Relation, and
      transition vocabulary without creating a loosely equivalent graph.
- [x] Reject duplicate IDs, containment cycles, multiple parents, unresolved
      endpoints, and child Flow outside its direct-parent scope.
- [x] Author the real SimpleModeling.org Article 9 `content/core.yaml`.

## P58-01B: Format and locale boundary

Stage Status:
- Current status: DONE
- Owner: Cozy Document Project
- Update rule: Update this block only when the format binding and its
  locale/Core identity checks are executable and covered.

- [x] Define one explicit format binding that owns locale and reader-facing
      text while binding stable Core IDs.
- [x] Provide the Japanese Article 9 format input used by both HTML outputs.
- [x] Reject a locale encoded in the Core filename or Core root contract.
- [x] Reject missing, duplicate, or unresolved format-to-Core bindings.

## P58-01C: Logic-tree overview HTML

Stage Status:
- Current status: DONE
- Owner: Cozy Document Project
- Update rule: Update this block only when the overview visibly and
  deterministically covers the complete accepted recursive Core.

- [x] Render one deterministic self-contained overview HTML.
- [x] Show Step nesting through visual containment.
- [x] Show child Flow through visible typed connections.
- [x] Show each Step's local Structure inside its Step boundary.
- [x] Keep diagnostic tables out of the primary reader-facing representation.
- [x] Prove that omission of any declared Step, Flow, or Structure fails
      semantic coverage.

## P58-01D: Step-slide HTML

Stage Status:
- Current status: DONE
- Owner: Cozy Document Project
- Update rule: Update this block only when every parent and leaf Step has one
  complete deterministic slide page and navigation is covered.

- [x] Render one deterministic self-contained 16:9 Step-slide HTML.
- [x] Produce one page for every parent and leaf Step.
- [x] Show ancestor path, local Structure, child Flow, and direct children.
- [x] Provide deterministic depth-first previous/next and keyboard navigation.
- [x] Provide a print representation with one Step per page.
- [x] Prove that the slide sequence covers the same accepted recursive Core as
      the overview HTML.

## P58-01E: Vertical-slice closure

Stage Status:
- Current status: DONE
- Owner: Cozy Document Project
- Update rule: Do not mark this stage DONE until every prior Phase 58 stage is
  DONE and all closure evidence below is recorded for the same settled tree.

- [x] Generate both HTML outputs from the real Article 9 Core and Japanese
      format binding.
- [x] Complete focused validation for schema, recursion, format binding,
      rendering, determinism, and semantic coverage.
- [x] Complete one independent Phase review with no Current Phase Blocker.
- [x] Complete full Cozy validation with the shared SBT lock released.
- [x] Complete Phase 58 through a distinct release commit.

## Closure boundary

The following remain outside Phase 58 and do not appear as OPEN work:

- migration of all existing Document Projects and profiles;
- retirement of Phase 46/46.1 products and compatibility behavior;
- public article/media production or publication; and
- deployment, upload, push, or external-service mutation.
