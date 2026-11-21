# Phase 92 — LexiDox Typed Sections and Knowledge Space Integration

status=in-progress
execution_started=true

Planned: 2026-11-16
Development item: DEV-041
Purpose: 型付きのLexiDox節とKnowledge Spaceの意味を、SmartDoxの正規出力からCozyのBoK連携へ受け渡します。
Progress: [Phase 92 checklist](phase-92-checklist.md)
Proposal: [当初の機能案](lexidox-typed-schema-proposal.md)
Origin: [精査後の計画補正・採番](../journal/2026/11/2026-11-16-development-plan-review-reconciliation.md)
Concept authority: [SimpleModelingのスキーマ提案](https://github.com/asami/simplemodelingorg/blob/main/docs/notes/lexidox-knowledge-space-context-schema.md)

## Scope and ownership

型ごとの標準節、制約のない補足節、Knowledge SpaceとContext、Term/Conceptの識別、既存SimpleModeling節の互換性を対象とします。SimpleModelingの概念提案を意味の参照元とし、P92-01で採用する改訂と未決事項を明示します。提案を確定済みの実行時スキーマとして扱いません。

既存の[BoK生成境界](../spec/bok-generated-knowledge-boundary.md#responsibility-and-non-reparse-invariants)に従い、SmartDoxは原稿の解析と初期metadata/RDF生成、Cozyは受入・生成調整・metadataの最終化とBoK連携を担当します。新しい型付き節の検証・シリアライズのproducer契約と改訂はP92-01でSmartDoxの所有者へ確認します。Cozyに別のLexiDoxパーサーや欠落出力の再構築を追加しません。

この番号はCozyの計画・連携担当を識別します。SmartDox側の実装はその所有者の計画・受渡しで扱い、本計画の登録だけで他リポジトリの実装・公開を開始しません。

## Shared LexiDox grammar alignment — 2026-11-18

Follow the [joint Phase 92/93 delivery plan](../notes/lexidox-phase92-93-integration.md) and canonical LX-J01–LX-J08 requirements. P92-01 and P93-01 coordinate one SmartDox producer grammar/schema and metadata decision record with a named baseline and compatible extension. Do not define Phase-local dialects or reparse sources in Cozy.

Resolve Space and Term/Concept identity before display-label selection. Knowledge classification, knowledge form and display role remain distinct; profile switching preserves page/link targets, canonical definitions, provenance, relations and RDF knowledge. Shared source fixtures verify these invariants across both delivery routes.

Joint contract alignment precedes shared-boundary implementation. Phase 92 accepts the semantic baseline without waiting for all Phase 93 rendering; Phase 93 consumes the accepted required subset and verifies cumulative compatibility. Existing priorities and unrelated work are unchanged.

## P92-01 investigation — 2026-11-20

The [producer–consumer decision record](../design/lexidox-producer-consumer-boundary.md) and [handoff](../spec/lexidox-knowledge-space-handoff.md) adopt SmartDox contract revision `7207fa191cd32bd383684ca4b278ff402ddbc096`. The earlier investigation found the joint additions uncommitted; the contract separately pins the committed conceptual proposal and Cozy joint plan. At that investigation stage the built producer and workflow review were outstanding. P92-01 was subsequently accepted in commit `7a1a9360`; P93-01 retains its separate presentation decisions. P93-01 retains its own presentation decisions and acceptance, and this design delivery does not change public outputs.

## P92-02 producer delivery — 2026-11-21

SmartDox delivered source `f8a3847c8717ebe5fe479d79553e741c5e6604cc` and the locally published `org.smartdox:smartdox_2.12:2.4.19-lexidox-v1-SNAPSHOT` artifact. The [handoff](../spec/lexidox-knowledge-space-handoff.md) records the fixture evidence, external `dox` runtime selection and exact output paths. Cozy's metadata finalizer carries those outputs without reconstituting them; P92-02 was subsequently validated and accepted in commit `1c9d693d`; P92-03 projections were accepted in `994c6e56`.

## Planned Steps

1. P92-01: 現行の解析・metadata/RDF経路と所有者を確認し、型別スキーマ、節認識、互換性、Space/IRIとproducer受渡しのdesign/specを定める。
2. P92-02: 受入済みSmartDox出力を既存のCozy受入経路へ接続し、標準節・自由節・既存metadataを保持する。
3. P92-03: 合意したSpace/Term/Conceptの識別と型情報を、既存BoK表示・索引・metadata/RDF連携へ反映する。
4. P92-04: 既存形式と型付き形式の日英fixtureを正規経路で統合検証し、互換性・診断・利用手順と最終受入を確認する。

P92-01〜P92-04はそれぞれ `7a1a9360`、`1c9d693d`、`994c6e56`、`648cdafd` で受入済みです。Phase全体の通常検証も成功し、独立技術レビューはclosure記録の不整合のみを指摘しました。文書修復の独立レビューとnative local closureはこの記録時点では未了です。実行上のrelease状態は保持されたsm-workflow結果に従います。

## Acceptance boundaries

大文字の標準節は記述形式（knowledge_form）に対応するスキーマへ照合し、未知の大文字節を診断します。大文字の標準節に該当しない補足節は、小文字・mixed-case・日本語を含め、内容・順序・リンク・ロケールを保持します。標準節であることと必須であることを区別します。PatternのDEFINITION/CONTEXT/PROBLEM/SOLUTION、CONSEQUENCESの推奨扱い、BoK固有profileの境界はproducer仕様で明示します。

既存のSimpleModeling表記、DEFINITION/RELATIONS、aliases、公開metadata、HTMLとRDFの互換性を検証します。節見出しの空行など既存SmartDox構文を維持し、不正な例を受理するための別パーサーは作りません。P92-01でSpaceを明示する参照構文、受渡し形式、版選択と診断を確定します。

用語ページはSpaceごとに設け、横断検索では各ページにSpace名を表示します。本文中の用語参照は、参照に明示されたSpace、文書に宣言されたSpace、用語集全体の順で、最初に適用できる範囲を一つ選びます。その範囲で候補が一件ならリンクし、複数なら曖昧エラー、ゼロなら未定義エラーとします。他の範囲へ再検索しません。曖昧エラーには原稿位置・用語・候補ページとSpaceを表示します。横断検索で複数件を表示することは正常です。

`concept_type` は概念の分類でBoK索引に使い、`knowledge_form` は知識の記述形式で標準節の検証に使います。たとえば `concept_type=process` と `knowledge_form=pattern` を独立に指定できます。v1 opt-in の `lexidox.term_type` は `concept_type` の alias として読み、記述形式を推定しません。v1 非採用の旧 `term_type=process` は旧 producer の正規化により `concept` となり、従来の DEFINITION/RELATIONS を保持します。両項目は相互に推定しません。

Space別ページにはそのSpaceでの定義・用例・関連知識を掲載します。同じ語の異なる意味には別のConcept IRIを対応させ、複数Spaceで同じ意味を明示的に共有する場合は同じConcept IRIを参照できます。ページの分割だけでConcept IRIを新設しません。

Knowledge Spaceをpath/categoryへ暗黙対応させず、Term IRIとConcept IRIを同一視しません。複数Spaceと概念識別の未決事項を解決し、採用する対応を仕様化・検証してからRDF連携を変更します。未受領のproducer契約や改訂は未受領として残し、推測して実装しません。

## Dependencies and completion

[BoK/SIE契約](../design/bok-sie-integration-contract.md)と既存SmartDox metadata/RDF出力を再利用します。Phase 90の作業領域、Phase 91の説明パターンとは別の機能であり、それらの完了を包括的な開始条件にしません。LexiDoxのPattern型とPhase 91の節の説明パターンも別の概念として扱います。

チェックリスト、実際のproducer/consumer受渡し、focused/integration検証、独立レビュー、最終Cozy通常検証で完了を判断します。関連producerの検証はその所有者が担当し、Cozyの結果で代用しません。既存資料の一括書換え、新しい知識グラフ格納機構、文章の自動生成、スキーマ内容への実行権限付与、公開・配備は対象外です。既存Phaseの状態・優先順位を変更しません。

## P92-04 integration evidence — 2026-11-21

Native proof-04 passed all driver assertions using SmartDox `f8a3847c8717ebe5fe479d79553e741c5e6604cc` and the normal Cozy Dox/Antora route. Japanese and English pages, legacy behavior, Space identities, publisher metadata, KnowledgeSource and graph bytes were checked. Distinct pages and Concepts, and a shared Concept with direct finalization and matching copied bytes, passed. Ordered free headings `notes`, `Mixed Case`, `補足` and their link, document Space and explicit override, global unique/ambiguous/undefined references, missing SOLUTION, unknown uppercase heading and invalid IRI also passed. Earlier proof-03 stopped at the missing standalone `website.d`; that failure remains historical evidence.

The native receipt records exit 0 and the [proof log](../../target/sm-workflow/tasks/cozy-phase92-20261120/p92-04-proof-04.log) names the generated `p92-04-proof-04` workspace and producer; the [receipt](../../target/sm-workflow/tasks/cozy-phase92-20261120/p92-04-runtime-native-04.json) records execution. The [handoff](../spec/lexidox-knowledge-space-handoff.md) pins the reproducible fixture and invocation. Cozy compile and five selected suites (47 tests) passed. SmartDox upstream passed 64 selected and 596 normal tests, was reviewed and published locally. The [checklist](phase-92-checklist.md) records the full validation, independent technical review and remaining native local closure. Phase 93 separately accepts presentation and rendering.

<!-- sm-workflow completion ca93139a775f8cd96098b37aa2d6e871 phase phase-92 -->
Accepted phase phase-92.
Evidence: ca93139a775f8cd96098b37aa2d6e871:ordered-validation:373:validation
Evidence: file:///Users/asami/src/dev2025/cozy/target/sm-workflow/tasks/cozy-phase92-20261120/p92-phase-final-review-worker-result.json
Phase source accepted; normal local record/commit closure follows. No later synchronization outcome is inferred.
Source revision: 648cdafdee89bfa84243cb9697600ccdff0ca495/921b47a0b837b8ac4a6b593c38c67c392402af85. Source physical lines: start=7165; end=7826; net=661. Cohort cozy-phase92-20261120:unclassified (Step, class=unclassified, source=921b47a0b837b8ac4a6b593c38c67c392402af85, window=2026-11-20T00:16:02.317165Z..2026-11-20T23:57:22.961824Z): eventual=4/4 (1); first-pass=0/4 (unavailable); unknown first-pass=1; pending=0; outcomes=(accepted,4); profiles=standard@1/standard-2026-11-01; mixed=. P92-01 completion-ms=unavailable elapsed-ms=unavailable; P92-02 completion-ms=unavailable elapsed-ms=unavailable; P92-03 completion-ms=unavailable elapsed-ms=unavailable; P92-04 completion-ms=unavailable elapsed-ms=unavailable. codex tokens=unavailable unavailable; local tokens=unavailable unavailable. Ordinary-work observations do not establish model causality; missing resources are unavailable; First-pass history is incomplete; rate unavailable Issued profiles=standard@1. codex tokens=N/A (unavailable); tokens/net source line=N/A; N/A: attributable input/output usage is unavailable. local tokens=N/A (unavailable); tokens/net source line=N/A; N/A: attributable input/output usage is unavailable
Profile standard@1; mapping=caller-standard@standard-2026-11-01; logical dispatch=Capture(standard-2026-11-01,ordinary,Vector(ordinary, difficult, hardest),DELEGATED,None,HashMap(sol-medium -> difficult, ordinary -> difficult, small -> sol-medium, difficult -> hardest, mechanical -> small)); configured=unknown; selected=caller-retained or unknown; provider observations are retained separately per execution
Profile standard@1; mapping=caller-standard@standard-2026-11-01; logical dispatch=Capture(standard-2026-11-01,difficult,Vector(difficult, hardest),DELEGATED,None,HashMap(sol-medium -> difficult, ordinary -> difficult, small -> sol-medium, difficult -> hardest, mechanical -> small)); configured=unknown; selected=caller-retained or unknown; provider observations are retained separately per execution

No benchmark or model-causality claim. Missing original endpoints, usage, timestamps and launch observations remain unavailable.

Local record/commit closure is runtime-owned; later publication, return and synchronization outcomes are recorded separately.
