# 文書構造・節構造パターンのCozy対応計画

Date: 2026-11-15
Disposition: ADOPTED / Phase 91 PLANNED / DEV-040

## 要求と検討結果

観測モデルの改稿で、文書全体の構造と、各節・小節の説明パターンを選択し、背景・課題・目的から主張、そのブレイクダウンへ展開する方針を整理しました。利用者はこれらをロジックツリーにも表示し、各パターンに沿って本文を書くことを希望しました。

SimpleModeling.org側では、各節のパターンを別の執筆用資料へ保持し、正規のDocument Noteを使う確認HTMLを生成しました。その後「文書構造パターン、節構造パターンをCozyで扱うことができるようにできる？」という検討依頼と、「phaseとして追加して。」という計画追加の指示を受けました。

Cozyの既存実装と仕様を読み、Phase 37の説明カタログ・Composition・Planは存在する一方、受入カタログが限定され、再帰Coreには文書構造と節構造の選択がないことを確認しました。既存版を維持し、再帰Coreの後継版へ正式に結び付ける方向を、計画の入力としました。

## 計画への反映

[Phase 91](../../../phase/phase-91.md)、[checklist](../../../phase/phase-91-checklist.md)、[実装予定の機能・モデル境界案](../../../notes/document-and-section-structure-patterns.md)を追加しました。Phase一覧とStrategyへDEV-040として登録しました。

文書構造2型、節構造8型、Coreとローカライズのモデル、再帰的な構造検査、骨組みの作成支援、native HTML表示、Document・本文への対応を計画しました。P91-01で設計上の決定をdesignへ、スキーマ・文法・CLI・診断をspecへ具体化します。Phaseには作業範囲と進捗を置き、今回のnotesを確定済み仕様として扱いません。

共有の執筆ルールには新しい方針が記述済みですが、standalone ai-directiveで未コミットの変更を含み、Cozyの固定submoduleには未配布です。P91-01で採用する改訂を確認する前提を残しました。今回、共有ルールのcommitやsubmodule更新は行っていません。

## 実施範囲と状態

今回の作業は計画文書の追加・登録のみです。Phase 91はplanned、execution_started=false、checklistは全項目OPENです。機能実装、テスト実行、コマンド追加、生成器変更、公開・配備・commit・pushは行っていません。既存の変更とPhaseの優先順位・状態を保持しました。
