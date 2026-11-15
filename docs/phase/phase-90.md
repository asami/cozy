# Phase 90 — Internal Document Packages and External Build Workspaces

status=planned
execution_started=false

Planned: 2026-11-11
Purpose: 内部資料の案件フォルダに編集可能なDocument Projectと完成物を配置し、Dropbox同期とGit管理に中間物を混入させず、外部作業領域で継続生成できるようにします。
Progress: [Phase 90 checklist](phase-90-checklist.md)
Proposal: [機能・境界案](../notes/document-project-private-package.md)
Origin: [利用者要求](../journal/2026/11/2026-11-11-private-document-package-plan.md)
Refinement: [Git管理対象の明確化](../journal/2026/11/2026-11-15-document-project-workroot-git-policy.md)

## Scope

初回配置、配置後の原稿編集、資源参照の移設、外部WorkRoot、日本語PDFと確認HTMLの生成、選択した完成物の更新を対象とします。Dropboxの同期領域では一時ファイルを生成中も作りません。既存のnative Cozy/SmartDox生成経路を使用します。

Git管理するDocument Projectでも同じ外部WorkRootを使用し、原稿・永続設定・必要な画像は管理対象に保ちます。中間生成物・ログ・キャッシュ・一時的な実行状態はGit対象外とします。WorkRootがより大きなGit作業ツリーに含まれる場合の限定したignore設定と、完成物を管理するかの選択をP90-01で定義します。既存の追跡済みファイルを自動で削除・追跡解除しません。`.gitignore`による除外はDropbox同期の回避にはならず、同期領域への書き込み自体を外へ出します。

## Planned Steps

1. P90-01: 既存書き込み経路の調査、配置・資源・WorkRoot契約とCLIの設計／仕様化。
2. P90-02: 外部作業領域と資源materializationをPDF・HTML生成へ接続。
3. P90-03: 案件パッケージ配置、配置先からの編集・生成、完成物更新。
4. P90-04: 移設と失敗経路を含む統合受入、利用手順、最終検証。

各Stepの実装結果はchecklistに記録します。現時点で全項目OPENです。

## Dependencies and completion

Document Project/local-build、SmartDox/LaTeXの既存経路を再利用します。外部作業領域へ渡せない上流APIが見つかった場合はP90-01で所有者と最小変更を特定し、別レンダラーや案件内一時ファイルで回避しません。公開exportの仕様は維持します。

匿名化した同等fixtureを自動検証に使用し、実際のLive Prototyping資料をローカル受入例とします。内部資料をCozyのfixtureや公開リポジトリに取り込みません。実際のDropbox配置・元原稿削除は個別の実行範囲として扱います。

この記録は計画追加のみです。既存Phaseの優先順位・状態・Goalを変更せず、実装・公開・配備・commitを開始しません。
