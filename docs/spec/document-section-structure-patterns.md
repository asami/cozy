# Document and Section Structure Patterns — Specification

Status: P91-01 specified, not implemented or accepted. Paired design: [responsibilities](../design/document-section-structure-patterns.md). Existing [recursive Core v1](document-project-recursive-content-core.md) and [Document Description](document-project-document-and-summary-description-dsl.md) contracts remain authoritative for their own inputs.

## Catalog and selection

The bundled catalog identity is `cozy.document-section-patterns`, version `1`; its source is the three documents at ai-directive revision `606cb56`. Version 1 is immutable. A future change creates a new explicit version and tests migration; unknown identities, versions, pattern IDs and role IDs reject. Inputs select by stable ID, never localized label. The machine-readable catalog includes ordered role definitions, min/max cardinality and Japanese/English pattern and role labels and questions. Labels are nonempty; both locales `ja` and `en` are required. The two document patterns and eight section patterns below are the complete v1 set. Document and section choice are independent; each Step including descendants explicitly chooses its own section pattern.

The pattern labels and canonical questions are catalog data, not user-authored content. Document templates have no canonical question.

| Kind / ID | Japanese label | English label | Japanese question | English question |
| --- | --- | --- | --- | --- |
| document / background-first | 背景導入型 | Background First | — | — |
| document / definition-first | 定義・課題対応型 | Definition First | — | — |
| section / definition | 定義型 | Definition | それは何か。 | What is it? |
| section / rationale | 必要性型 | Rationale | なぜ必要か。 | Why is it needed? |
| section / problem-solution | 課題対応型 | Problem–Solution | 課題にどう対応するか。 | How is the problem addressed? |
| section / decomposition | 構成分解型 | Decomposition | 何から成るか。 | What does it consist of? |
| section / mechanism | 仕組み型 | Mechanism | どう働くか。 | How does it work? |
| section / comparison | 比較選択型 | Comparison | 何を選ぶか。 | What should be chosen? |
| section / procedure | 実施方法型 | Procedure | どう実施するか。 | How is it performed? |
| section / worked-example | 具体例型 | Worked Example | 実際にはどうなるか。 | What happens in practice? |

Role labels are likewise localized catalog data. The following is the complete role vocabulary for this profile; the same ID has the same label in either kind of pattern. English labels are shown after the slash.

| Role ID | Japanese / English |
| --- | --- |
| lead | リード / Lead |
| introduction | 導入 / Introduction |
| definition | 定義 / Definition |
| composition | 構成 / Composition |
| usage | 使い方 / Usage |
| example | 具体例 / Example |
| conclusion | 結論 / Conclusion |
| purpose | 目的 / Purpose |
| application-framework | 活用の枠組み / Application framework |
| issues | 対象となる課題 / Issues |
| responses | 課題への対応 / Responses |
| context | 文脈 / Context |
| properties | 性質・範囲・境界 / Properties, scope and boundary |
| necessity | 必要性 / Necessity |
| reasons | 理由 / Reasons |
| conditions | 条件 / Conditions |
| problem | 課題 / Problem |
| response | 対応方針 / Response |
| cause-measure | 原因と対策 / Cause and measure |
| effect-condition | 効果と条件 / Effect and condition |
| overview | 全体像 / Overview |
| components | 構成要素 / Components |
| principle | 仕組みの要点 / Principle |
| operation | 作用 / Operation |
| result-condition | 結果と条件 / Result and condition |
| axes-evidence | 比較軸と根拠 / Axes and evidence |
| selection-condition | 選択条件 / Selection condition |
| goal | 到達目標 / Goal |
| method | 方法 / Method |
| prerequisites | 前提 / Prerequisites |
| steps | 手順 / Steps |
| verification | 結果の確認 / Verification |
| example-point | 事例の要点 / Example point |
| result | 結果 / Result |
| correspondence | 一般説明との対応 / Correspondence |

For each table, `1` means exactly one, `?` zero or one and `+` one or more. Assignments appear in the given role order; repeated instances remain contiguous. These counts govern the selected Cozy profile, not an assertion that the shared prose demands separate headings for every role. One Step may occupy multiple document roles explicitly. Each document role reference resolves to a distinct immediate child of the root within that role; repetitions must follow child order. Document roles are:

| ID | Ordered roles and cardinalities |
| --- | --- |
| background-first | lead 1, introduction 1, definition 1, composition 1, usage 1, example ?, conclusion 1 |
| definition-first | lead 1, definition 1, purpose 1, application-framework ?, issues 1, composition 1, responses 1, usage 1, example ?, conclusion 1 |

Every section has one authored question, one answer claim, and ordered role assignments below. Context roles may be omitted when not needed or replaced by explicitly inherited context as specified below; other listed required roles cannot be replaced by inherited context.

| ID | Ordered roles and cardinalities |
| --- | --- |
| definition | context ?, definition 1, properties + |
| rationale | context ?, necessity 1, reasons +, conditions ? |
| problem-solution | problem ?, response 1, cause-measure +, effect-condition + |
| decomposition | purpose ?, overview 1, components + |
| mechanism | purpose ?, principle 1, operation +, result-condition + |
| comparison | purpose ?, conclusion 1, axes-evidence +, selection-condition ? |
| procedure | goal ?, method 1, prerequisites ?, steps +, verification 1 |
| worked-example | context ?, example-point 1, conditions ?, operation +, result 1, correspondence 1 |

The answer role (`definition`, `necessity`, `response`, `overview`, `principle`, `conclusion`, `method`, `example-point` respectively) must reference the Step's answer claim. All other role assignments explicitly reference a claim of that Step or an immediate child Step. A child assignment has `explainsClaim` referencing a claim of its parent; it does not imply a Relation or Flow. Order in assignments is reading order, child nesting is containment, and existing Relations/Flow remain separate logical data. No role may refer to a node as a claim or to a distant child as an immediate child.

## Source shape and references

All examples are YAML; JSON uses the same keys and values. Source admission follows v1 direct `core.yaml`, UTF-8 and duplicate-key rules. All objects introduced here are closed, all arrays ordered, IDs use the existing Core ID grammar, and duplicates in a given assignment list reject. Envelope schema is `cozy.content-core.logic-tree.v2`. Required root keys are exactly `schema`, `core`, `catalog`, `documentPattern`, `sections`. `core` is the complete unchanged v1 Core object, including its v1 schema; the existing validator validates it. `catalog` has exactly `id` and `version`, with the values above. `documentPattern` has exactly `pattern` and `roles`; each document role has exactly `id`, `role` and `step`; its assignment ID shares the envelope-wide unique assignment ID namespace with section-role assignments. `sections` has exactly one entry for each recursive Core Step, in depth-first order; each has `step`, `pattern`, `question`, `answer`, `roles`, `inheritedContext`. Question is a stable ID unique across the envelope; answer is an existing claim ID of the same Step. A section role has exactly `id`, `role`, `target`; assignment IDs are unique throughout the envelope and identify occurrences even when a role repeats; `target` is either `{kind: claim, id: <claim-id>}` or `{kind: child, id: <immediate-child-step-id>, explainsClaim: <parent-claim-id>}`. `inheritedContext` is an array of exactly `{ancestor: <step-id>, role: <role-id>, target: <claim-id>}`. The eligible optional section roles are `context` in definition, rationale and worked-example; `problem` in problem-solution; `purpose` in decomposition, mechanism and comparison; and `goal` in procedure. No other role is eligible. The ancestor must be a strict ancestor whose own section has a local assignment of the same role directly to the specified claim; inherited assignments cannot be re-exported. The child's selected pattern must admit that exact role as an eligible optional role. Cross-role substitution (including `context` to `purpose`) rejects. For each eligible role in a child, at most one inherited entry is allowed, and it is mutually exclusive with any local assignment of that role. Duplicate entries, multiple ancestors for the same role, a local/inherited conflict, noneligible roles, wrong targets and non-ancestor references reject. Inheritance is displayed as inherited; it never copies or infers content.

The following complete structural example uses one child in several document roles explicitly. The root and child each have their own section pattern and claim. It requires matching localized Format and, for correspondence validation, Document inputs; it does not assert their existence.

```yaml
schema: cozy.content-core.logic-tree.v2
core:
  schema: cozy.content-core.logic-tree.v1
  id: sample
  root:
    id: root
    semanticRole: explanation
    claims: [root-claim]
    structure:
      pattern: mapping
      nodes: [{id: root-source, role: source}, {id: root-target, role: target}]
      relations: [{id: root-map, relationType: maps-to, from: root-source, to: root-target}]
    flow: {id: root-flow, transitions: []}
    steps:
      - id: child
        semanticRole: explanation
        claims: [child-claim]
        structure:
          pattern: mapping
          nodes: [{id: child-source, role: source}, {id: child-target, role: target}]
          relations: [{id: child-map, relationType: maps-to, from: child-source, to: child-target}]
        flow: {id: child-flow, transitions: []}
        steps: []
catalog: {id: cozy.document-section-patterns, version: 1}
documentPattern:
  pattern: background-first
  roles:
    - {id: doc-lead, role: lead, step: child}
    - {id: doc-introduction, role: introduction, step: child}
    - {id: doc-definition, role: definition, step: child}
    - {id: doc-composition, role: composition, step: child}
    - {id: doc-usage, role: usage, step: child}
    - {id: doc-conclusion, role: conclusion, step: child}
sections:
  - step: root
    pattern: definition
    question: q-root
    answer: root-claim
    roles:
      - {id: root-definition, role: definition, target: {kind: claim, id: root-claim}}
      - {id: root-properties, role: properties, target: {kind: child, id: child, explainsClaim: root-claim}}
    inheritedContext: []
  - step: child
    pattern: definition
    question: q-child
    answer: child-claim
    roles:
      - {id: child-definition, role: definition, target: {kind: claim, id: child-claim}}
      - {id: child-properties, role: properties, target: {kind: claim, id: child-claim}}
    inheritedContext: []
```

V2 Format has exactly `schema`, `coreId`, `locale`, `base`, `questionBindings`; schema is `cozy.content-core.logic-tree-format.v2`. `base` is the unchanged complete v1 Format bound to the embedded Core, and its locale and `coreId` must equal the envelope fields. Each question binding is exactly `{id, text}`, one nonblank localized value for each question and no unknown or duplicate ID. Catalog labels supply pattern/role wording; authored question and existing v1 bindings supply content. Japanese and English use separate Format instances; missing wording fails rather than falling back.

V2-aware Document loading explicitly admits the v2 envelope, then passes its already validated embedded v1 Core to the existing Document v2 identity/reference projection; the original v1 path remains unchanged. Document `core.id` must equal the embedded Core id. The mapping schema `cozy.document-pattern-correspondence.v1` has exactly `schema`, `coreId`, `documentId`, `locale`, `assignments`; all bindings must match the admitted Core and Document. Each assignment has exactly `roleAssignment`, `targetKind`, `targetId`. `roleAssignment` resolves an envelope-wide unique document-role or section-role assignment ID. `targetKind` is `section`, `block`, or `list-item` and `targetId` resolves uniquely to that kind in the bound Document v2. For a document-role assignment the target must directly reference the assigned Step in `coreRefs.steps`; for a section-role claim target it must directly reference that claim in `coreRefs.claims`; for a section-role child target it must directly reference that child in `coreRefs.steps`. For a logical-structure block its direct `stepRef` qualifies as the Step reference, but cannot qualify as a claim reference. References of containing or contained objects do not count implicitly.

The existing Document v2 model and validator ([CozyDocumentDescriptionV2.scala](../../src/main/scala/cozy/document/CozyDocumentDescriptionV2.scala)) already include Figure and Table as blocks with `coreRefs`: their block IDs qualify with `targetKind: block` under the same reference rule. No Figure/Table format extension or change to existing Document semantics is required. Section, block and list-item IDs retain the existing Document v2 uniqueness and validation rules. A single target may explicitly correspond to several roles. Every document-role and section-role assignment must have at least one mapping for correspondence acceptance; duplicate identical mappings, unknown or stale assignments, wrong target kinds and targets without the required direct reference reject. Mapping does not modify or generate Document wording. For example, with a Document section `chapter` whose `coreRefs.steps` contains `child`, a paragraph `detail` whose `coreRefs.claims` contains `root-claim`, and a figure block `diagram` whose `coreRefs.steps` contains `child`, the following entries are valid against the example above:

```yaml
schema: cozy.document-pattern-correspondence.v1
coreId: sample
documentId: sample-document
locale: en
assignments:
  - {roleAssignment: doc-lead, targetKind: section, targetId: chapter}
  - {roleAssignment: root-definition, targetKind: block, targetId: detail}
  - {roleAssignment: root-properties, targetKind: block, targetId: diagram}
```

This is an excerpt of the correspondence entries: a complete accepted mapping must also cover every other assignment and bind an admitted Document with the stated references.

## Diagnostics, commands and draft

Admission rejects unknown fields, schema/catalog/version/IDs, wrong types, duplicate IDs, invalid v1 payloads, invalid cardinality/order, missing or wrong-kind references, out-of-scope children/ancestors, stale mappings and missing localized wording. Diagnostics carry stable category, source path, offending ID where present and expected versus actual; errors occur before output writes. Shape errors, catalog errors, reference errors, role/order errors, localization errors and correspondence errors are distinct categories. Machine checks do not judge semantic quality.

The v2 command grammar is exactly:

```text
cozy document-project logic-tree validate --core <core.yaml> --format <format.yaml> [--document <document.yaml> --correspondence <correspondence.yaml>]
cozy document-project logic-tree scaffold --catalog cozy.document-section-patterns --version 1 --document-pattern <id> --section-pattern <id> --save <core.yaml>
cozy document-project logic-tree render --core <core.yaml> --format <format.yaml> --kind overview --save <output.html>
```

Document and correspondence options occur together. Scaffold writes a separate `cozy.content-core.logic-tree-draft.v1` document, not a v2 envelope. Its closed root has exactly `schema`, `catalog`, `documentPattern`, `sections`; catalog has exactly `id`, `version`; documentPattern has exactly `pattern`, `slots`; sections is an ordered nonempty array of exactly `{path, pattern, question, answer, slots}`. Scaffold creates one root entry with `path: root` and the requested section pattern. Question and answer are the literal string `unwritten`, never inferred content. Each slot has exactly `{role, occurrence, state}`: `occurrence` is a positive integer starting at 1 per role and `state` is `unwritten`. Slots follow catalog role order: one slot for every `1`, `?`, and `+` role; the latter represents its first required occurrence, while `?` represents an optional opportunity, not a requirement. There is no generated Core, claim, question ID or fabricated text. For example:

```yaml
schema: cozy.content-core.logic-tree-draft.v1
catalog: {id: cozy.document-section-patterns, version: 1}
documentPattern:
  pattern: background-first
  slots:
    - {role: lead, occurrence: 1, state: unwritten}
    - {role: introduction, occurrence: 1, state: unwritten}
    - {role: definition, occurrence: 1, state: unwritten}
    - {role: composition, occurrence: 1, state: unwritten}
    - {role: usage, occurrence: 1, state: unwritten}
    - {role: example, occurrence: 1, state: unwritten}
    - {role: conclusion, occurrence: 1, state: unwritten}
sections:
  - path: root
    pattern: definition
    question: unwritten
    answer: unwritten
    slots:
      - {role: context, occurrence: 1, state: unwritten}
      - {role: definition, occurrence: 1, state: unwritten}
      - {role: properties, occurrence: 1, state: unwritten}
```

Scaffold output is deterministic YAML in that key and catalog slot order, UTF-8, with no localized authored content; JSON with the same keys and values is also admitted as draft. Reloading a draft checks its exact shape, catalog identity and version, pattern IDs, ordered initial slots, literal markers and unique paths; malformed drafts receive shape or catalog diagnostics. Strict validate and render recognize a well-formed draft by its schema and report the stable category `draft-incomplete`, at `schema`, without writing output; malformed drafts report their actual diagnostic rather than being treated as complete v2. To finish, an author creates a complete v2 envelope and Format with actual Core, assignments, IDs and wording, retaining the selected patterns explicitly; the normal v2 admission then applies. Draft markers are never accepted as v2 values or silently converted.  Existing v1 render grammar and behavior remain intact; v2 render accepts only overview until another contract adds a kind. Paths follow existing direct non-symlink rules; validation and semantic failure precede writes, while ordinary I/O failure can leave partial output. Output is deterministic escaped self-contained HTML, includes both document and section choices, localized questions, context, claims, ordered detail, parent claim links, and separately presents containment, Relation and Flow. Selection/reference highlighting and keyboard access must work in `ja` and `en` without external assets. No authoring label is injected into Document or SmartDox.

Executable Given/When/Then specifications and appropriate property-based cases under `src/test/scala/cozy/document`, with portable fixtures under `src/test/resources/phase91`, cover all choices, independent recursive choices, typed references and failures, cardinality/order, explicit inherited context, draft rejection, localization, HTML, Document mappings and v1/Phase 37/native article regression. This specification does not claim implementation or test results.
