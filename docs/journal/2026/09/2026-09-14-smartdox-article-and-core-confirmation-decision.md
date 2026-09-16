# SmartDox Article and Core Confirmation Planning Decision

Date: 2026-09-14
Development item: DEV-028

## Review context

During Article 9 preparation, the user requested a normal article review HTML
before the final Antora route. The existing v2 Document confirmation showed
authoring DSL content with Core information, but did not render an article
authored in `index.dox`.

A local article-reading prototype was created from the validated Document
confirmation. The user found the combined logic-tree information useful:

> 確認用htmlには、今回作ってもらったようにロジック・ツリー情報が併記されていると確認しやすいね。
> ただ、index.doxを作るのはCodexでしかできないので、cozyでどこまでやるかだね。

The follow-up asked whether the article source should carry annotations to
support the correspondence. The user then requested this development as Cozy
notes, journal, and Phase documentation.

## Recorded direction

Create **Phase 60: SmartDox Article and Core Confirmation** as a planned,
separate producer boundary. Preserve closed Phase 58.2 and ongoing Phase 59.

Codex authors `index.dox` from the accepted logical and localized authoring
sources, recording article-to-Core correspondence. Cozy renders the actual
SmartDox article and places it alongside the recursive Core tree. The same
article source subsequently goes through SmartDox/Antora for the final site.

The useful review interaction is bidirectional: inspect a Step and find the
article blocks that explain it, or inspect a paragraph/figure and see its
authored logical grounding. The rendered article remains the primary reading
surface. Core containment, child-Step Flow, and local Structure retain their
distinct meanings.

## Evidence and dependency distinction

The current native `article.render-review` is a table-oriented review
projection. The v2 Document renderer consumes `document.yaml`. Neither is the
proposed annotated-article workspace. `article.compose` is a declared workflow
operation, not evidence of an available creative authoring implementation.

SmartDox has parser and normal HTML rendering boundaries. Existing generic
attributes are a possible investigation path, but a new `CORE_REF` directive
has not been established as supported syntax. Phase 60 therefore includes a
contract gate covering annotation attachment, AST preservation, normal HTML,
and Antora compatibility. Missing upstream functionality is handed to SmartDox
explicitly; Cozy does not invent a second parser.

The local prototype is presentation evidence only. Its source was generated
Document confirmation HTML, and Article 9 `index.dox` was still a scaffold at
the time of this planning decision. Implementation acceptance needs a real
annotated article fixture and cannot claim that the pipeline already exists.

## Companion skill creation handoff

The user added the following direction on 2026-09-14:

> 同時にスキルの作成も必要だね。作ったjournalをhandoff にしてスキルを作成したい。journalにこの情報も盛り込んで。

Develop a companion Codex skill alongside the Phase 60 product work. This
journal is the entry handoff for that skill-creation task; the proposal and
Phase checklist provide the linked context. Skill authoring can begin from
this handoff, while runnable integration uses only the subsequently frozen and
tested SmartDox/Cozy contracts. This journal does not define those contracts.

### Intended purpose and entry conditions

The skill supports initial SmartDox article authoring and iterative revision
in a Document Project, then generates and opens the article/Core confirmation
HTML. Accept a project path, locale, and user request or review feedback. Read
the selected `core.yaml`, localized `document.yaml`, existing `index.dox`,
relevant project instructions, terminology sources, and current review state.

Keep the skill reusable across projects. Article 9 is the first validation
driver, not a hard-coded project, locale, filesystem path, or prose template.
The final skill name is decided during creation after checking overlap with
existing document-authoring and preview skills.

### Responsibility and authoring loop

1. Establish the selected source identities and preserve an appropriate
   checkpoint before replacing an existing article. Retain unrelated edits.
2. Interpret the feedback at its semantic level. When logical meaning changes,
   update Core and the affected Document Description before deriving the
   article. Wording-only edits can remain in the article with unchanged
   grounding; do not rewrite Core merely for style.
3. Author or revise the actual `index.dox` using SmartDox authoring rules,
   project terminology, and approved content. Maintain stable block IDs and
   typed Core correspondence in the supported annotation format, including
   explicit unmapped editorial blocks/omissions where the contract allows.
4. Ask Cozy to validate and render the real article/Core confirmation through
   the confirmed Phase 60 CLI. Reuse the existing command-execution and
   Document Project preview skills instead of embedding another parser,
   renderer, server, or command-routing policy.
5. Display the generated HTML in the browser, using the existing preview
   refresh mechanism when a verified owned session is available. Report the
   actual output path/URL, source identities, diagnostics, and review status.
6. Incorporate subsequent human review feedback through the same loop. Human
   approval remains explicit; generating or opening HTML does not approve it.

Codex performs creative authoring and correspondence maintenance. Cozy performs
deterministic validation/rendering. SmartDox owns syntax and export semantics.
The skill consumes those product contracts rather than becoming their owner.
The annotated article remains the same source later consumed by Antora.

### Deliverables for the skill-creation task

- A `SKILL.md` with precise triggers, initial-authoring/update workflows,
  source authority, allowed changes, dependencies, diagnostics, and completion
  conditions; accompanying discovery metadata follows the current skill
  creation tooling.
- Small supporting references/examples only where necessary, linked to the
  promoted Phase 60 design/spec and tested CLI/version. Proposed `CORE_REF`
  syntax in the note must not be advertised as available functionality.
- A resumable work record containing project/locale, selected source paths and
  identities, affected Core/article IDs, last successful output, unresolved
  diagnostics, and pending human review, using existing state contracts where
  available rather than inventing another acceptance authority.
- Skill validation plus end-to-end evidence for initial authoring, semantic
  update, wording-only update, repeated preview refresh, malformed/unresolved
  annotations, stale Core bindings, and unavailable product capability.

If the required annotation or CLI capability is unavailable, preserve sources
and the previous successful output and report the exact dependency/resume
point. A table review or HTML-derived prototype is not reported as an actual
SmartDox article confirmation. Automatic PDF/media generation, publication,
deployment, commit, and modifications to other projects are separate requests.

### Restart instructions

A skill-creation task starts by reading this journal and its linked proposal,
Phase 60/checklist, current skill-creation guidance, and existing authoring,
command-execution, and preview skill contracts. Check the actual Phase 60 and
SmartDox capability/version before selecting an executable integration route.
Create and validate the companion skill in the explicitly selected skill
location; record remaining product dependencies without marking integration
complete prematurely.

This update records the skill handoff only. The companion skill itself has not
been created or installed, and Phase 60 implementation progress is unchanged.

## Documentation produced and next handoff

- [Exploratory proposal](../../../notes/document-project-smartdox-article-confirmation-proposal.md)
- [Phase 60 ledger](../../../phase/phase-60.md)
- [Phase 60 checklist](../../../phase/phase-60-checklist.md)

These documents track intended work and its history. Design/spec promotion and
executable specifications precede implementation. P600-01 is the restart point:
freeze the annotation/renderer/CLI contracts and the reference UI, establish
SmartDox dependency evidence, then proceed through the checked ledger.

This entry records planning only. No product implementation, article/media
regeneration, external-project source change, commit, publication, or deployment
was performed by this documentation update.
