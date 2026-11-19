# Phase 92 — LexiDox Typed Sections and Knowledge Space Integration

status=planned
execution_started=false

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

## Planned Steps

1. P92-01: 現行の解析・metadata/RDF経路と所有者を確認し、型別スキーマ、節認識、互換性、Space/IRIとproducer受渡しのdesign/specを定める。
2. P92-02: 受入済みSmartDox出力を既存のCozy受入経路へ接続し、標準節・自由節・既存metadataを保持する。
3. P92-03: 合意したSpace/Term/Conceptの識別と型情報を、既存BoK表示・索引・metadata/RDF連携へ反映する。
4. P92-04: 既存形式と型付き形式の日英fixtureを正規経路で統合検証し、互換性・診断・利用手順と最終受入を確認する。

全Step・全checklist項目はOPENです。実装は未着手です。

## Acceptance boundaries

大文字の標準節は記述形式（knowledge_form）に対応するスキーマへ照合し、未知の大文字節を診断します。大文字の標準節に該当しない補足節は、小文字・mixed-case・日本語を含め、内容・順序・リンク・ロケールを保持します。標準節であることと必須であることを区別します。PatternのDEFINITION/CONTEXT/PROBLEM/SOLUTION、CONSEQUENCESの推奨扱い、BoK固有profileの境界はproducer仕様で明示します。

既存のSimpleModeling表記、DEFINITION/RELATIONS、aliases、公開metadata、HTMLとRDFの互換性を検証します。節見出しの空行など既存SmartDox構文を維持し、不正な例を受理するための別パーサーは作りません。P92-01でSpaceを明示する参照構文、受渡し形式、版選択と診断を確定します。

用語ページはSpaceごとに設け、横断検索では各ページにSpace名を表示します。本文中の用語参照は、参照に明示されたSpace、文書に宣言されたSpace、用語集全体の順で、最初に適用できる範囲を一つ選びます。その範囲で候補が一件ならリンクし、複数なら曖昧エラー、ゼロなら未定義エラーとします。他の範囲へ再検索しません。曖昧エラーには原稿位置・用語・候補ページとSpaceを表示します。横断検索で複数件を表示することは正常です。

`concept_type` は概念の分類でBoK索引に使い、`knowledge_form` は知識の記述形式で標準節の検証に使います。たとえば `concept_type=process` と `knowledge_form=pattern` を独立に指定できます。旧 `term_type` は `concept_type` の入力として読み、記述形式を省略した原稿には従来の節規則を適用します。両項目は相互に推定しません。

Space別ページにはそのSpaceでの定義・用例・関連知識を掲載します。同じ語の異なる意味には別のConcept IRIを対応させ、複数Spaceで同じ意味を明示的に共有する場合は同じConcept IRIを参照できます。ページの分割だけでConcept IRIを新設しません。

Knowledge Spaceをpath/categoryへ暗黙対応させず、Term IRIとConcept IRIを同一視しません。複数Spaceと概念識別の未決事項を解決し、採用する対応を仕様化・検証してからRDF連携を変更します。未受領のproducer契約や改訂は未受領として残し、推測して実装しません。

## Dependencies and completion

[BoK/SIE契約](../design/bok-sie-integration-contract.md)と既存SmartDox metadata/RDF出力を再利用します。Phase 90の作業領域、Phase 91の説明パターンとは別の機能であり、それらの完了を包括的な開始条件にしません。LexiDoxのPattern型とPhase 91の節の説明パターンも別の概念として扱います。

チェックリスト、実際のproducer/consumer受渡し、focused/integration検証、独立レビュー、最終Cozy通常検証で完了を判断します。関連producerの検証はその所有者が担当し、Cozyの結果で代用しません。既存資料の一括書換え、新しい知識グラフ格納機構、文章の自動生成、スキーマ内容への実行権限付与、公開・配備は対象外です。既存Phaseの状態・優先順位を変更しません。
