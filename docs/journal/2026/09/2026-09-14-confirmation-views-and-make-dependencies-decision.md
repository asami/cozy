# Confirmation Views and Make-Level Dependency Management Decision

Date: 2026-09-14
Development item: DEV-028 / Phase 60
Status: planning record; no implementation acceptance

## Discussion and observed boundary

Article 9 had Document Description review HTML while its `index.dox` remained
a scaffold. After an actual article was authored, standalone SmartDox generated
an HTML from that source. This clarified that DSL review and actual article
rendering serve different purposes. Current Cozy article review presents
parsed article narrative in tables; it is not the standalone rendering route.

The user proposed adding a document-centered confirmation HTML alongside the
current structure-centered DSL view. They also requested a Cozy Document Project
entry point for actual article HTML, even when SmartDox performs the rendering,
because the resulting artifact still needs dependency management.

## Accepted direction

1. DSL-derived structure-centered HTML reviews Step nesting, inter-Step Flow and
   local Structure, with corresponding prose.
2. DSL-derived document-centered HTML reads like an article, with Core and diagrams
   as secondary information. Both views consume the same authored DSL.
3. `index.dox`-derived HTML reviews the actual article representation. Cozy owns
   the target/action/output boundary and SmartDox owns parsing and rendering.

The existing Summary view remains separate. The existing table article review
does not count as one of these reading/rendering views.

The user progressively rejected hash-based artifact management and concluded:

> 通常のmakeコマンドでやっているレベルの依存管理でよいと思うよ。

The recorded policy therefore uses direct/transitive file dependencies and
modification times: absent output builds, newer dependency rebuilds, otherwise
reuse. Output locations, target definitions and action results are enough.
No new content hashes, digest receipts or evidence-management framework are
required. Build freshness does not imply human content approval.

## Source authority and replaced proposal

The [prose authority decision](2026-09-14-document-description-prose-authority-decision.md)
continues to apply: Document Description owns organization and prose, Core owns
logical meaning, and `index.dox` is the SmartDox-targeted representation.
Cozy rendering does not rewrite or automatically compose that representation.

The earlier Phase 60 proposal required actual article/Core correspondence via
new supported article annotations. The present direction moves explanatory
context into the document-centered DSL projection and does not require those
annotations for actual article rendering. The user confirmed Phase 60 was
unimplemented and requested that its existing plan be revised in place instead
of creating a separate Phase. The initial Phase-62 draft is therefore folded
into Phase 60 / DEV-028, and the temporary Phase-62 work documents are removed.
All current P600 Steps remain OPEN; earlier records retain the planning history.
No new SmartDox annotation extension is required by the revised Phase 60.

## Development handoff

- P600-01: promote source/view/CLI/target and make-level dependency contracts,
  with reference HTML and executable specifications.
- P600-02: add the document-centered projection while preserving the current
  structure view and Summary behavior.
- P600-03: provide the Document Project actual-article HTML target backed by
  ordinary SmartDox rendering.
- P600-04: integrate declared target dependencies, modification-time freshness,
  generated prerequisites, reuse, force rebuild and failure preservation.
- P600-05: validate isolated drivers, regressions, help and one Phase review.

Target names and CLI spelling are frozen at contract admission, not invented
by this journal. Existing source-schema checks and native acceptance/export
systems are not silently replaced by the lightweight build policy.

Companion article-authoring/preview skills should be reconciled separately after
the implemented product contract is available. This planning request does not
modify or create an installed skill, renderer or server.

Only planning documents and development indexes are updated. No article, media,
Cozy code, SmartDox code, Git stage/commit, site build, upload or publication is
performed. Phase 59/61 priorities and closed Phase 58.2 behavior are preserved.

## Subsequent direction: Document-rooted Codex capability set

The user subsequently requested:

> document.yamlを起点に各種成果物を生成するようにcodexの機能セットを整える必要があるね。
> この点をphase 60に取り込んで。指摘のあったnotesからの取り込みも行なって。

Phase 60 now retains the three-view local builder as 60A and adds 60B for the
Codex authoring/update capability set. This supersedes the earlier instruction
to reconcile companion skills separately outside Phase 60; it does not alter
the local HTML builder into a creative authoring engine.

The added plan establishes Document-to-SmartDox article derivation and
Document/Core-to-Summary adaptation, then coordinates requested supported
article HTML/PDF, summary-slide PDF, infographic and optional video routes.
Document owns complete prose, Summary owns concise selection, and Core owns
logic. Accepted wording changes update Document; direct article prose edits
are reconciled back before acceptance. Existing tools and maintained skills
are reused, with unavailable routes and independent Phase 59/61 dependencies
explicit. Media rendering capabilities are not claimed by planning alone.

The notes' representation-update boundary is now explicit in P600-01/P600-05:
Document changes alone can rebuild DSL views while article HTML remains current
against unchanged `index.dox`. Renderer freshness is not latest-Document
reflection. Codex authoring precedes rendering in the 60B workflow. No new
hash management, article annotations or renderer is required by this direction.

P600-06 admits the workflow and capability matrix; P600-07 implements companion
skill reconciliation; P600-08 owns isolated workflow acceptance and whole-Phase
closure. P600-05 now closes the local-build subphase, not the entire expanded
Phase. All implementation items remain OPEN. This action updates only the
proposal, journal, Phase and checklist; no skill, code, article, media,
Git stage/commit or publication operation is performed.

## Subsequent clarification: Request-to-Core planning and forward generation

The user clarified the desired workflow:

> 欲しいのはXXXが欲しい、というと一つづつ根拠のDSLをたぐってcoreに行き着いたら、
> coreから順にDSLを生成していく、という流れだね。

The user then requested that Phase 60 take this structure. Subphase 60B now
starts with a product request, resolves declared DSL prerequisites recursively
back to Core, and creates/updates the needed DSLs from the Core side in
dependency order before rendering. Document remains the complete prose
authority; Summary remains the concise-selection authority. This replaces a
fixed Document-first product pipeline with a shared goal-driven dependency
graph, without replacing either source authority.

P600-06 admits reverse resolution, node producers/inputs, reuse rules, branching,
shared dependencies and cycle/missing-producer diagnostics. P600-07 integrates
one planner with maintained producer skills and supported render targets.
P600-08 verifies Core-only initial input, all-current reuse, selective update,
shared prerequisites and blocked-path reporting in isolated projects.

Valid Core and other DSLs are reused. Missing or changed dependencies are
authored only within the requested scope; missing Core meaning requires
authorized user input or clarification. External rendering assets/configuration
remain explicit prerequisites, not invented semantic DSLs. Reconciliation
back-links do not become cyclic production dependencies. Existing source
validation and make-level rendering semantics remain intact, with no new hash
management or generic workflow engine.

This clarification updates only the Phase, checklist, proposal and this journal.
All implementation items remain OPEN; no skill, code, media, Git state or
publication change is performed.

## Subsequent clarification: Skill-led Codex authoring and Cozy dispatch

The user clarified that Codex-generated source documents are intermediate
nodes of the workflow, and skills must orchestrate and select Cozy calls.
The user requested that this working capability be built within Phase 60.

Phase 60B now explicitly delivers an orchestration entry-point skill plus
reused/adapted producer skills. The skill owns the goal/dependency plan and
dispatch. Codex authors content-bearing DSLs/documents at declared paths;
Cozy performs admitted validation, confirmation, rendering and local build
operations. These actions can alternate as dependency order requires. This
is not a Cozy-only deterministic build and not solely a plan for future skills.

P600-06 admits the responsibility split and action handoffs. P600-07 creates
or adapts the maintained orchestration skill and integrates producer skills
with actual Cozy CLI/API contracts. P600-08 requires end-to-end skill execution
with actual Codex-authored intermediate documents, Cozy calls and a subsequent
feedback update. Prepopulated fixtures or renderer-only tests alone cannot
close this workflow item. Existing source authorities and publication boundaries
are preserved; all implementation items remain OPEN.

Only the four existing planning documents are updated by this action. No skill
is created/installed now, and no code, article, media or Git-state mutation occurs.

## Subsequent clarification: Cozy as the skill-facing operation provider

The user confirmed that Cozy supplies the function set invoked by skills and
requested reflection in Phase 60 where needed. The plan now explicitly names
Cozy's callable operation set and its input/output/failure contracts as a
deliverable, alongside the maintained skill workflow.

The orchestration skill owns the overall goal-to-Core plan, Codex authoring
order and dispatch. Cozy performs the selected admitted operation and may
resolve that target's declared local build prerequisites. This does not assign
Cozy the overall AI authoring workflow or remove 60A make-level dependencies.
An operation inventory distinguishes supported calls from capability gaps.
Acceptance checks the same operation contract independently and through the
skill. No command or renderer is claimed implemented by this planning change.

The Phase, checklist, proposal and journal are updated; implementation items
remain OPEN. No skill, code, article, media, Git state or publication is changed.

## References

- [Proposal](../../../notes/document-project-confirmation-views-and-make-dependencies-proposal.md)
- [Phase 60](../../../phase/phase-60.md)
- [Checklist](../../../phase/phase-60-checklist.md)
- [Earlier Phase 60 decision](2026-09-14-smartdox-article-and-core-confirmation-decision.md)
