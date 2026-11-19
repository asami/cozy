# Phase 91 — Document and Section Structure Patterns

status=in-progress
execution_started=true

Planned: 2026-11-15
Purpose: 文書構造パターンと節構造パターンをCozyの正規モデルで扱い、Coreの作成、再帰的な構造検査、ロジックツリー表示と本文への対応を一貫させます。
Progress: [Phase 91 checklist](phase-91-checklist.md)
Proposal: [機能・モデル境界案](../notes/document-and-section-structure-patterns.md)
Origin: [利用者要求と調査記録](../journal/2026/11/2026-11-15-document-and-section-structure-patterns-plan.md)
Strategy: [DEV-040](../strategy/cozy-development-strategy.md#9-development-item-status)

## Scope

文書全体の構成を選ぶ5パターンと、各節・小節の説明を組み立てる9パターンを対象とします。背景・課題・目的などから問いを共有し、主張、そのブレイクダウンへ展開する構造を再帰的に扱います。各節が親と異なる型を選べるようにし、章立て上の役割、節の説明パターン、因果・根拠などの論理構造を区別します。

既存の再帰Coreの後継版へ文書・節パターンの選択と役割参照を正式に結び付け、選択したカタログに対して検査します。Coreの現行版とPhase 37の限定されたカタログ契約を維持し、既存形式への未定義フィールド追加や暗黙の移行を行いません。新しい形式・CLI・参照とローカライズの契約はP91-01でdesign/specへ具体化します。

## Planned Steps

1. P91-01: 既存契約と共有執筆ルールを整理し、カタログ、Core後継版、役割参照と表示のdesign/specを定める。
2. P91-02: 文書5型・節9型のカタログと、参照・役割・再帰構造の型付きモデルを実装する。
3. P91-03: 明示したパターンの構造検査と、内容を捏造しない作成支援を既存CLIへ接続する。
4. P91-04: ロジックツリーHTMLへパターン、問い、背景、主張、詳細と親子の展開を標準表示する。
5. P91-05: Documentとの役割対応と、本文・SmartDoxへ執筆用情報を混入させない受渡しを実装する。
6. P91-06: 観測モデルを基にしたCozy内fixtureで統合受入し、既存版の回帰確認と最終検証を行う。

各Stepの進捗はchecklistで管理します。P91-01の設計・仕様を起草しました。P91-02は作業中であり、検証、独立レビューおよび受入は未完了です。

## Dependencies and completion

Phase 37の説明カタログ・役割・明示選択の設計、既存の再帰Core、Document Description、native確認HTMLと本文生成の契約を再利用します。Phase 37の単一パターン・平坦なStepモデルを、再帰Coreへの対応済み機能として扱いません。現在変更中の確認表示とは境界を分け、開始時に受入済みの基線へ整合させます。

共有執筆ルール3文書は、2026-11-16時点の固定ai-directive改訂606cb56に配置済みです。P91-01でその内容とカタログの対応・採用版を確認します。新規配布を開始条件とせず、配置済みのルールと未実装のCozy機能を区別します。

観測モデルに現在ある別資料とNoteによる表示を、型付きの入力・検査・標準表示へ置き換えられることを受入対象とします。参照・役割・順序等の決定的な検査はCozy、主張や根拠の意味的な妥当性と文章の執筆は人間・AIが担当します。

本Phaseは計画追加のみです。既存Phaseの優先順位・状態・Goalは変更しません。スライド・動画への新規展開、文章の自動評価・自動生成、公開・配備・共有ルールの配布、実原稿への一括移行は対象外です。

P91-01の成果物: [design](../design/document-section-structure-patterns.md)、[spec](../spec/document-section-structure-patterns.md)。これらは後続実装の契約であり、機能の実装やPhaseの受入を示しません。
