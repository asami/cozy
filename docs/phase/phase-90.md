# Phase 90 — Internal Document Packages and External Build Workspaces

status=planned
execution_started=false

Planned: 2026-11-11
Updated: 2026-11-16
Purpose: 編集可能なDocument Projectと選択した完成物を保持し、日英の全媒体を外部作業領域で生成して、同期領域とGit管理に中間物を混入させないようにします。
Progress: [Phase 90 checklist](phase-90-checklist.md)
Proposal: [機能・境界案](../notes/document-project-private-package.md)
Origin: [利用者要求](../journal/2026/11/2026-11-11-private-document-package-plan.md)
Refinement: [Git管理対象](../journal/2026/11/2026-11-15-document-project-workroot-git-policy.md)、[日英全媒体と受入例](../journal/2026/11/2026-11-16-phase90-bilingual-media-resource-scope.md)

## Scope

初回配置、配置後の原稿編集、資源参照の移設、外部WorkRoot、選択した完成物の更新を対象とします。対象媒体は**日本語・英語それぞれの記事PDF、infographic、まとめスライド、音声、動画**です。本文確認HTMLとCore確認HTMLも既存の範囲として維持します。既存のnative Cozy/SmartDox・画像・プレゼンテーション・音声・動画経路へ同じWorkRootを通し、同期領域とProject内に一時ファイルを生成中も作りません。

Core、ロケール別Document、統合版index.dox、原図、スライドIR、動画台本、生成設定と必要な外部素材は編集可能な入力として保持します。記事PDFは日英で同じindex.doxを使い、ロケールで選択します。生成物の分類は用途に従い、音声も選択した完成物と中間音声を区別します。手書きの生成設定や台本を、再生成可能な中間物として削除しません。

Git管理するDocument Projectでも同じ外部WorkRootを使用します。中間音声、部分動画、LaTeX中間物、レンダラーのバンドル・キャッシュ、スライド組立て用データ、ログ、一時的な実行状態をProject外へ置きます。原稿・永続設定・必要な画像は管理対象に保ち、完成物の追跡はプロジェクトの方針に従います。既存ignore・追跡済みファイルを保持し、既存中間物の移行・整理は明示した範囲で扱います。

共通のtoolchain・renderer・style設定を既存の設定層に集約し、記事には入力資源、言語、生成対象と記事固有の選択を残します。現行記事の共有コールバックと原稿・素材の対応を調査し、配置後に元の絶対パスへ依存しない解決方法をP90-01で定めます。共通設定の変更も通常の再生成判定へ反映します。

## Planned Steps

1. P90-01: 全媒体の書き込み経路、資源分類、共通設定、配置・WorkRoot契約とCLIの設計／仕様化。
2. P90-02: 外部作業領域と資源materializationを日英のPDF・infographic・スライド・音声・動画と確認HTMLへ接続。
3. P90-03: 編集可能な案件パッケージ配置、配置先からの継続生成、選択完成物の更新と復旧。
4. P90-04: 観測モデル・知識ハーネスの日英全媒体、移設と失敗経路を含む統合受入、利用手順、最終検証。

全Stepと全受入項目はOPEN、実装は未着手です。詳細な設計・仕様はP90-01でnotesからdesign/specへ具体化します。

## Dependencies and completion

Document Project/local-build、SmartDox/LaTeXと既存の各媒体producerを再利用します。子ツール、provider、preview、receipt/stateを含めて外部WorkRootを通します。上流APIが受け取れない場合は所有者と最小変更を特定し、別レンダラーやProject内一時ファイルで迂回しません。公開exportの仕様は維持します。

受入例はSimpleModeling.orgの[観測モデル](../../../simplemodeling-org/src/main/doxsite/development-process/observed-model.dox/README.md)と[知識ハーネス](../../../simplemodeling-org/src/main/doxsite/development-process/knowledge-harness.dox/README.md)です。両記事についてja/en×5媒体を既存native経路で生成し、原稿・台本・原図・永続設定と既存完成物の保持、実行中の中間物ゼロ、元パスを参照できない移設後の再生成、正常・失敗・中断時の復旧を確認します。

匿名化した同等fixtureで自動検証し、実記事は明示したローカル受入範囲で使用します。既存のLive Prototyping内部資料の配置例も保持します。内部資料や実記事一式をCozyの公開fixtureへ取り込みません。今回の計画更新は記事の再生成を実行せず、公開・配備・元原稿削除も許可しません。Phase実行時の受入は隔離した作業コピーで行い、現行の完成物とYouTube公開を上書きしません。

既存Phaseの優先順位・状態・Goalを変更せず、実装・再生成・公開・配備・commitを開始しません。
