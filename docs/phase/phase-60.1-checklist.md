# Phase 60.1 Checklist

Phase status: PLANNED
Updated: 2026-09-15
Scope: Subphase 60B goal-driven DSL dependency resolution and generation
Ledger for: [Phase 60.1](phase-60.1.md)
Predecessor: [Phase 60](phase-60.md)
Repository-full validation: aggregate final owner for `PHASE-60` → `PHASE-60.1`

P600-06 is DONE. P600-07 and P600-08 remain OPEN. Planning alone does not
complete an implementation item.

## P600-06: Goal-driven DSL dependency contract admission

Stage Status:

- Current status: DONE
- Owner: Cozy/Codex workflow contract owner
- Update rule: close against the following admitted-contract checklist and verifiable scenarios

- [x] A promoted workflow contract makes Document the complete localized prose authority, Core the logical authority, `index.dox` the derived SmartDox representation and Summary the deliberate concise-content authority.
- [x] The contract assigns request/dependency planning and dispatch to an orchestration skill, content-bearing intermediate DSL/document authoring to Codex, and admitted deterministic validation/confirmation/rendering actions to Cozy.
- [x] A skill-facing operation inventory maps each supported validation/confirmation/conversion/rendering/local-build action to its implemented Cozy contract; capability gaps are explicit, and Cozy is not assigned the overall AI authoring dependency planner.
- [x] Each action has an explicit Codex-authoring or Cozy-operation classification, input/output paths, success/failure conditions and next-step handoff; actual operation names come from implemented CLI/API contracts, not invented commands.
- [x] The explicit source-to-product graph covers Core-to-Document, Document-to-article, Document/Core-to-Summary, article HTML/PDF, summary-slide PDF, infographic and optional video; it identifies each actual input and authoring versus rendering action.
- [x] A product request with explicit project, locale and applicable profile resolves recursively through declared grounding DSL dependencies to Core; the plan exposes reverse traversal and forward dependency order rather than a hard-coded product pipeline.
- [x] Each node declares its producer, direct inputs, validation/reuse conditions and supported tool/skill route; multiple prerequisites and shared nodes are preserved, deduplicated and ordered without inferring semantic edges from filenames or prose.
- [x] Missing DSLs, invalid/stale existing bindings, unavailable producers and graph cycles have distinct diagnostics; missing Core meaning requires authorized authoring input or clarification, not unsupported invention.
- [x] Nonsemantic assets/configuration/tool inputs remain explicit external prerequisites, not fabricated Core-derived DSLs; review/reconciliation back-links are not cyclic generation edges.
- [x] Initial creation, prose-only feedback, logical changes, Summary-only edits and direct article edits have explicit source-update and reconciliation rules; accepted article prose never remains solely in `index.dox`.
- [x] A capability matrix selects existing maintained skills and tested Cozy/SmartDox/media routes for each requested product, with unsupported routes and Phase 59/61 dependencies explicit rather than claimed complete.
- [x] Observable workflow scenarios specify affected-product selection, checkpoints, failure/resume behavior, requested locale/output boundaries and separation of generation from human approval or publication.
- [x] The request contract distinguishes source-only editing from explicitly selected product generation and browser display; a preview server or old output does not implicitly select a generation target.

Evidence: paired design/specification contract admission, Class D mechanical
preflight, and independent protected Step review passed with no Current Phase
Blocker, Hygiene, or Development Candidate. Review disposition bundle:
`6a758cf0884eef7c3c1ecaad939e558bec9dbb35ffeb56820aaaa004379029af`.

## P600-07: Companion skill authoring and integration

Stage Status:

- Current status: OPEN
- Owner: Codex companion-skill implementation owner
- Update rule: close against the following maintained-skill and integration checklist

- [ ] The maintained article-authoring skill preserves accepted Document headings/prose in `index.dox`, allowing SmartDox syntax, term markup and declared assets without independent prose invention.
- [ ] A maintained orchestration entry-point skill is created or adapted with a usable request interface and dependency resolver, dispatching node-specific authoring skills/Codex work and implemented Cozy operations rather than documenting a manual-only sequence.
- [ ] Codex-authored Core, Document, Summary, SmartDox article and applicable storyboard/visual source nodes are persisted at declared project paths and validated before dependent actions; authored source documents are explicit workflow products, not hidden renderer inputs.
- [ ] The orchestration skill alternates Codex authoring and Cozy validation/confirmation/rendering where required by the graph, handling returned outputs and failures without treating all nodes as Cozy generators.
- [ ] Wording feedback is reflected into Document before accepted article derivation; direct article prose edits are reconciled back, while syntax-only repairs preserve Document prose.
- [ ] Article/media orchestration establishes or updates an explicitly authored Summary and supported visual/storyboard inputs grounded in Document/Core; Summary adaptation is not mistaken for a verbatim article or output-format IR.
- [ ] Changed DSL sources refresh their affected existing upstream bindings through admitted source-validation mechanisms, including Summary's Document binding when selected Summary wording is unchanged; this introduces no new local-artifact hash management.
- [ ] Skills invoke the admitted Phase 60 HTML targets and existing supported local product routes, rebuilding only affected requested products; authoring completion precedes dependent rendering.
- [ ] A shared resolver accepts the requested product(s), walks the admitted DSL graph back to Core and produces the ordered plan with reuse/create/update/render decisions before dependent actions run.
- [ ] Execution starts on the Core side, reuses valid nodes, authors missing/affected DSLs only after their prerequisites are ready, validates them and then invokes dependent authoring/rendering actions.
- [ ] Multiple requested products share each prerequisite action once; unrelated products and valid Core/Document prose are not regenerated simply because a downstream product was requested.
- [ ] Preview integration uses implemented target names and regeneration behavior; HTML remains a projection and serving/opening remains a separately owned operation.
- [ ] The article skill defaults to manuscript/DSL updates and necessary source validation only; selected-product orchestration, not each edit, dispatches rendering and authorized preview notifications.
- [ ] Maintained skill sources, dependency instructions and executable verification are reconciled through the applicable skill workflow; installed entry points are checked only within an explicit skill execution boundary.

## P600-08: Workflow acceptance and Phase closure

Stage Status:

- Current status: OPEN
- Owner: Cozy Phase owner; independent reviewer separate from implementation
- Update rule: close only against all preceding checklist items and the following acceptance evidence

- [ ] An isolated project demonstrates initial generation from Document through article and Summary to each selected supported local product; exact selections and unavailable requested routes are reported.
- [ ] An end-to-end orchestration-skill invocation actually creates or updates needed intermediate documents through Codex and invokes the admitted Cozy operations, with inspectable source files and generated outputs; manually supplied complete fixtures or renderer tests alone cannot satisfy this item.
- [ ] A feedback-driven invocation repeats the same skill dispatch path, reflects prose edits into Document and produces the requested supported local result; the entry point, producer skills and invoked Cozy commands are verified together.
- [ ] Each selected Cozy operation is verified independently and through the orchestration skill with the same declared input/output and failure contract; overall Codex authoring order is controlled by the skill, while Cozy may resolve its own declared local target prerequisites.
- [ ] With Core present and required downstream DSLs absent, an isolated product request demonstrates reverse discovery to Core and forward creation of each needed DSL before rendering.
- [ ] With all prerequisites valid and products current, the same request reuses them without rewriting DSL prose, rerendering or touching timestamps; with one affected prerequisite, only the requested dependency closure is updated.
- [ ] A multi-product request demonstrates branching dependencies and one execution per shared node; a cycle or unavailable required producer stops before dependent generation with a useful dependency path.
- [ ] A prose-only edit updates Document, derives the article and regenerates affected requested products without unnecessary Core edits; Summary is reviewed and updated when its selected content is affected.
- [ ] Logical feedback and a direct article-prose edit demonstrate the declared Core/Document update and reconciliation loops, preserving unrelated sources and locales.
- [ ] An editing-only request completes without renderer calls, browser opening or preview notification; an explicit selected-product request generates only its dependency closure, with display separately requested and no automatic PNG/frame-based QA.
- [ ] Failure preserves prior successful products and resumable source state; retry requires the needed authoring action rather than merely rerendering an old `index.dox`.
- [ ] Phase closure requires the admitted capability matrix and requested workflow scenarios to pass; an unavailable required route cannot be represented as accepted generation.
- [ ] Applicable Cozy/skill focused validation and independent Phase review close all in-scope blockers; this final owner verifies the Phase 60 predecessor and runs the serial chain's one repository-full SBT suite before its release commit. Documentation and ledgers distinguish implemented local generation from human approval, external project updates and publication.
