# 最終受入済み完全 Logical UI の次工程入力

実行証跡の保存先: `macmini:~/backup/journal/2026/10/cozy/COZY-SCALA2-SYNC-20261013-001/COZY-EVIDENCE-CLEANUP-20261013-003/`。同期完了が2026-10-13の場合の原則保持期限: 2026-11-12（完了から30日）。同期中は期限なしで保持し、期限到来だけで自動削除しません。

「ローカル保管」と記した元のリポジトリ相対パスは、保存先の `original-tree/<元の相対パス>` に対応します。大きな JSON・HTML は `.gz` 付きで保存し、`gzip -dc` で元の記録を読み取れます。別ホストではこの保存先の可用性を確認してください。

PHASE-50.1 / S50.1.2 / E4 で受け入れたtarget-neutral Logical UIを保持します。人による最終判断と次工程への入力はGit管理対象です。生成HTML、receipt、観測JSON、QA画像はローカル証跡です。

## 実際の人間判断

所有会話 `01a0ff01-4772-76b2-bba1-e2542f0a9647` の人間ユーザーは、クライアント日付2026-10-13に次の独立した最終受入れメッセージを述べました。個人名や正確な時刻は追加しません。

> この候補を最終受入れする。Phase 50.1を最後まで続けて。コミットは引き続き親による手動ローカルコミットで。

[acceptance.json](acceptance.json) は実判断、帰属と対象を保持します。先の「修正不要」という[フィードバック](../readiness-candidate-001/feedback.md)とは別個の判断です。

| 受入対象 | 保存済みの値 |
| --- | --- |
| candidateIdentity | `sha256:dcb64b218d5921c65be0f1be7b1483565446dfd9fc3dbefd215c202e83f813b1` |
| consumedInputIdentity | `sha256:dd1f3b249795e3857bdcb19a72abc2cc205edee8e4d736f8f2db42a2dd08c0a1` |
| decisionId | `P501-E4-FINAL-ACCEPTANCE-001` |
| reviewerId | `owning-chat-human:01a0ff01-4772-76b2-bba1-e2542f0a9647` |

## 次工程への意味入力

[accepted-logical-ui.json](accepted-logical-ui.json) の `binding` と `sourceInputs` がPhase 51への入力です。四つの権威入力役割 `kernelContent`、`projectionContent`、`semanticContent`、`readinessContent` を保持します。保存済みのnative accepted/decision値を再計算・仮定しません。生成Webソース、HTML、画像、実行receiptを次工程の意味権威にはしません。

[RenderAcceptedReadiness.scala](../../../../scripts/test/phase501/RenderAcceptedReadiness.scala)と[browser driver](../../../../scripts/test/phase501/run-readiness-browser.mjs)を再現用ソースとして保持します。SBTとブラウザ検証は通常の登録・直列実行契約に従い、生成物はignored/private出力先に置きます。新たな検証や受入れの主張には実際に必要な入力と実行記録を用います。

Phaseの完了状態・対象範囲は[Phase 50.1](../../../phase/phase-50.1.md)と[正規チェックリスト](../../../phase/phase-50.1-checklist.md)を参照します。証跡の保管期限はPhaseの完了や人間判断を取り消しません。
