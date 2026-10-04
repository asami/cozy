# Phase 79: Textus Component Project Area Scaffold Integration

Status: planned
Planned: 2026-10-05
Depends on: CNCF Phase 101 minimum project-resource layout contract
Driven by: sm-workflow Phase 5-7 project-resource/worktree scenario

## Goal

Apply the CNCF Phase 101 Component Project Resource convention to Cozy-generated projects so the initial project scaffold already has a stable Git/version-control policy for present and future Textus Components.

The generated project must not require later runtime mutation of `.gitignore` when sm-workflow or another Component creates DataStore state, worktrees, temporary work, or caches.

## Required scaffold rule

Every applicable Textus/CNCF project scaffold MUST generate a `.gitignore` rule equivalent to:

```gitignore
.textus/*/work.d/
```

The rule is Component-independent and resource-kind-independent. Non-versioned Component project resources are placed below `work.d/` by the CNCF Phase 101 local provider convention.

Version-controlled Component definitions at `.textus/<component>/` root and durable resources under `resources/` remain visible to Git.

## Generator behavior

1. Extend the common project-scaffold generation path where possible; do not duplicate the rule independently in each generator.
2. Existing project-specific `.gitignore` entries remain intact.
3. Generation is deterministic and idempotent: regeneration MUST NOT duplicate the standard rule.
4. Runtime Components MUST NOT be required to edit `.gitignore`.
5. Cozy MUST NOT enumerate known Component IDs such as `sm-workflow` when generating ignore policy.
6. Cozy MUST NOT enumerate `state`, `worktrees`, `cache`, or future work-resource kinds in `.gitignore`; `work.d/` is the stable boundary.
7. Do not eagerly create unused Component directories merely to establish the policy.

## Existing scaffold integration

Update the existing CAR project scaffold specification and the reusable scaffold/template implementation that emits project-root Git metadata. Other Cozy project generators that create Textus/CNCF project roots MUST consume the same baseline policy rather than maintain divergent copies.

If no single common implementation currently exists, Phase 79 may extract the smallest deterministic helper/template needed to share this one project baseline; it MUST NOT introduce a broad new project-generation framework solely for this rule.

## CNCF ownership boundary

CNCF Phase 101 is authoritative for:

- logical Component resources;
- `.textus/<component>/` local-provider layout semantics;
- `resources/` versus `work.d/` lifecycle/version-control boundary.

Cozy is authoritative for projecting the accepted convention into newly generated project files. Cozy application generators and generated source code do not expose or reinterpret CNCF resource semantics.

## Executable Specification requirements

Demonstrate at least:

1. a newly generated CAR/Textus project contains the standard `.textus/*/work.d/` ignore rule;
2. `.textus/sm-workflow/config.yaml` and `.textus/sm-workflow/resources/example` are not ignored by that baseline;
3. `.textus/sm-workflow/work.d/state/example.db` and `.textus/sm-workflow/work.d/worktrees/cncf/...` are ignored without sm-workflow-specific rules;
4. the same rule covers an arbitrary future Component ID;
5. regenerating/updating a scaffold does not duplicate the rule;
6. existing unrelated project `.gitignore` content is preserved;
7. no runtime execution is needed to repair Git policy after project generation.

## Closure relationship

Phase 79 may implement once the CNCF Phase 101 minimum layout contract is stable. Its executable evidence should be available before the sm-workflow Phase 7 dogfooding scenario is considered the final project-generation compatibility proof. CNCF Phase 101 closure remains governed by its sm-workflow Phase 7 full acceptance gate.

## Non-goals

- Runtime editing of `.gitignore`.
- Component-specific ignore rules.
- Making Cozy own CNCF Component Resource semantics.
- Pre-creating all possible Component directories.
- Introducing integrity/hash/rollback machinery around generated Git metadata.
