# Logical UI 完全候補のレビュー・受入れガイド

実行証跡の保存先: `macmini:~/backup/journal/2026/10/cozy/COZY-SCALA2-SYNC-20261013-001/COZY-EVIDENCE-CLEANUP-20261013-003/`。同期完了が2026-10-13の場合の原則保持期限: 2026-11-12（完了から30日）。同期中は期限なしで保持し、期限到来だけで自動削除しません。

「ローカル保管」と記した元のリポジトリ相対パスは、保存先の `original-tree/<元の相対パス>` に対応します。大きな JSON・HTML は `.gz` 付きで保存し、`gzip -dc` で元の記録を読み取れます。別ホストではこの保存先の可用性を確認してください。

対象は PHASE-50.1 / S50.1.2 / E4 の `sales-order-target-ready-v1` です。生成HTML、QA画像、実行receipt、観測JSONと詳細レビューはローカル証跡として保管します。

## 人による判断と対象

所有会話 `01a0ff01-4772-76b2-bba1-e2542f0a9647` の人間ユーザーが2026-10-13に述べた「修正不要」は、意味ソースの改訂要求がない判断です。[feedback.md](feedback.md) に帰属と処置を保持します。

同じ所有会話で別個に述べた最終受入れは、次の実判断です。

> この候補を最終受入れする。Phase 50.1を最後まで続けて。コミットは引き続き親による手動ローカルコミットで。

[acceptance.json](../readiness-accepted-001/acceptance.json) はこの判断と対象を保持します。

| 対象 | 保存済みの値 |
| --- | --- |
| candidateIdentity | `sha256:dcb64b218d5921c65be0f1be7b1483565446dfd9fc3dbefd215c202e83f813b1` |
| consumedInputIdentity | `sha256:dd1f3b249795e3857bdcb19a72abc2cc205edee8e4d736f8f2db42a2dd08c0a1` |

上記は既存の人間判断が対象とした歴史的な値です。保存変更は判断や意味ソースを再計算しません。次工程への入力は[accepted-logical-ui.json](../readiness-accepted-001/accepted-logical-ui.json)の`binding` と `sourceInputs` に保持した四つの宣言役割です。

## レビューと再現

画面・操作・入力・状態・公開binding・診断と出典は[仕様](../../../spec/logical-ui-model.md)と[設計](../../../design/logical-ui-model.md)に従います。ソースfixtureから構造レビュー、操作シミュレーション、三つのviewport比較を再生成し、normal/loading/empty/validation-failed/conflict/unavailable/operation-failed、宣言された復旧、reset/replay、入力・選択・focusの保持を確認します。実Operation/backend/Workflow進行は別境界です。

[browser driver](../../../../scripts/test/phase501/run-readiness-browser.mjs)と[accepted renderer adapter](../../../../scripts/test/phase501/RenderAcceptedReadiness.scala)が再現用ソースです。SBTは登録runnerと共有直列wrapperで実行し、生成物はGit対象外のignored/private出力先に置きます。意味を改訂する場合は入力を改訂し、実際に必要な検証と別個の人による受入れを行います。

Phaseの完了状態は[Phase 50.1](../../../phase/phase-50.1.md)と[正規チェックリスト](../../../phase/phase-50.1-checklist.md)が権威です。元の詳細レビュー・生成物・原Git履歴は上記保存先で確認できます。
