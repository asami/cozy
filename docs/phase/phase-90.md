# Phase 90 — Internal Document Packages and External Build Workspaces

status=in_progress
execution_started=true

Planned: 2026-11-11
Updated: 2026-11-19
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

P90-01〜P90-04はそれぞれローカルコミット `c48ad34b`、`658ae925`、`c031dc96`、`202cf86b` に記録されました。両記事の隔離パッケージは移設後に13選択を再生成しました。利用者は隔離したrole修正済み観測モデルCoreと独立したホストDoxを選ぶprofileによる検証方法を採用し、日英各2件、計4音声の35秒地点から約20秒を試聴しました。元Coreの検証失敗と元サイトcallbackのcheckout依存は残ります。P90-04独立再レビューはissue-11〜14を受入れ、従来の14 issueはすべて解決と判定されました。Phase compile/testは修復前候補で成功しました。今回の記録修復後の再レビューとnative最終閉鎖は未了です。詳細と証拠は[受入記録](../journal/2026/11/2026-11-19-phase90-native-acceptance.md)を参照してください。

## Dependencies and completion

Document Project/local-build、SmartDox/LaTeXと既存の各媒体producerを再利用します。子ツール、provider、preview、receipt/stateを含めて外部WorkRootを通します。上流APIが受け取れない場合は所有者と最小変更を特定し、別レンダラーやProject内一時ファイルで迂回しません。公開exportの仕様は維持します。

受入例はSimpleModeling.orgの[観測モデル](../../../simplemodeling-org/src/main/doxsite/development-process/observed-model.dox/README.md)と[知識ハーネス](../../../simplemodeling-org/src/main/doxsite/development-process/knowledge-harness.dox/README.md)です。両記事についてja/en×5媒体を既存native経路で生成し、原稿・台本・原図・永続設定と既存完成物の保持、実行中の中間物ゼロ、元パスを参照できない移設後の再生成、正常・失敗・中断時の復旧を確認します。

匿名化した同等fixtureで自動検証し、実記事は明示したローカル受入範囲で使用します。既存のLive Prototyping内部資料の配置例も保持します。内部資料や実記事一式をCozyの公開fixtureへ取り込みません。今回の計画更新は記事の再生成を実行せず、公開・配備・元原稿削除も許可しません。Phase実行時の受入は隔離した作業コピーで行い、現行の完成物とYouTube公開を上書きしません。

公開・配備・pushはこのPhaseの受入範囲に含めません。

### P90-03 implementation candidate

The editable-package candidate adds explicit source and bundled-toolchain placement, ordered selected-product refresh, and external refresh receipts with conservative restoration. Command grammar and current restrictions are recorded in the [external WorkRoot specification](../spec/document-project-external-workroot.md#phase-90-implementation-candidate). P90-03 was committed after its native validation and review. Subsequent P90-04 evidence and its remaining limitations are recorded in the acceptance journal. Phase 90 remains in progress.

最終ローカル完了の判定は、この文書末尾に追記されるnative受付記録とnative Terminalを参照します。上記は記録修復時点の状態です。

<!-- sm-workflow completion 3d791a3eaf0b2e47f2c6b94b5c21a55c phase phase-90 -->
Accepted phase phase-90.
Evidence: 3d791a3eaf0b2e47f2c6b94b5c21a55c:ordered-validation:307:validation
Evidence: file:///Users/asami/src/dev2025/cozy-phase90/target/sm-workflow/tasks/cozy-phase90-20261119/p90-phase-release-rereview-intake/p90-phase-release-rereview-result.json
Phase source accepted; normal local record/commit closure follows. No later synchronization outcome is inferred.
Source revision: 202cf86bbdb23c9ff508e6abbf7a62441dc6f982/bbdaa345823314c44d799e011238bd3b927a37b1. Source physical lines: start=87955; end=90080; net=2125. Cohort cozy-phase90-20261119:unclassified (Step, class=unclassified, source=bbdaa345823314c44d799e011238bd3b927a37b1, window=2026-11-19T05:26:31.698130Z..2026-11-19T15:05:37.618982Z): eventual=4/4 (1); first-pass=0/4 (0); unknown first-pass=0; pending=0; outcomes=(accepted,4); profiles=standard@1/standard-2026-11-01; mixed=. P90-01 completion-ms=unavailable elapsed-ms=unavailable; P90-02 completion-ms=unavailable elapsed-ms=unavailable; P90-03 completion-ms=unavailable elapsed-ms=unavailable; P90-04 completion-ms=unavailable elapsed-ms=unavailable. codex tokens=unavailable unavailable; local tokens=unavailable unavailable. Ordinary-work observations do not establish model causality; missing resources are unavailable Issued profiles=standard@1. codex tokens=N/A (unavailable); tokens/net source line=N/A; N/A: attributable input/output usage is unavailable. local tokens=N/A (unavailable); tokens/net source line=N/A; N/A: attributable input/output usage is unavailable
Profile standard@1; mapping=caller-standard@standard-2026-11-01; logical dispatch=Capture(standard-2026-11-01,ordinary,Vector(ordinary, difficult, hardest),DELEGATED,None,HashMap(sol-medium -> difficult, ordinary -> difficult, small -> sol-medium, difficult -> hardest, mechanical -> small)); configured=unknown; selected=caller-retained or unknown; provider observations are retained separately per execution
Profile standard@1; mapping=caller-standard@standard-2026-11-01; logical dispatch=Capture(standard-2026-11-01,difficult,Vector(difficult, hardest),DELEGATED,None,HashMap(sol-medium -> difficult, ordinary -> difficult, small -> sol-medium, difficult -> hardest, mechanical -> small)); configured=unknown; selected=caller-retained or unknown; provider observations are retained separately per execution

No benchmark or model-causality claim. Missing original endpoints, usage, timestamps and launch observations remain unavailable.

Local record/commit closure is runtime-owned; later publication, return and synchronization outcomes are recorded separately.
