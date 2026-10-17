# Local execution evidence storage location — 2026-10-13

実行証跡の保存先: `macmini:~/backup/journal/2026/10/cozy/COZY-SCALA2-SYNC-20261013-001/COZY-EVIDENCE-CLEANUP-20261013-003/`。同期完了が2026-10-13の場合の原則保持期限: 2026-11-12（完了から30日）。同期中は期限なしで保持し、期限到来だけで自動削除しません。

「ローカル保管」と記した元のリポジトリ相対パスは、保存先の `original-tree/<元の相対パス>` に対応します。大きな JSON・HTML は `.gz` 付きで保存し、`gzip -dc` で元の記録を読み取れます。別ホストではこの保存先の可用性を確認してください。

Cozy Phase 50 compact review and Phase 50.1 candidate/accepted browser inspection material are local-only under the execution evidence policy. The backup index maps original repository paths to readable archived files, including gzip text and the full original Git bundle. The backup directory, raw JSON, compressed evidence and original Git-history bundle are outside the GitHub synchronization target.

Human acceptance and feedback, accepted Logical UI successor input, requirements and canonical Phase status/closure records remain development or management artifacts. Archived evidence expiry does not revoke Phase completion. Active/private execution control records stay at their bound paths until their consumers finish. No automatic cleanup or retention job is created by this storage record.
