# Phase 71: hash保持方針の撤回とファイル更新管理

Date: 2026-09-28
Status: decision recorded; product implementation and acceptance pending
Tracking: Phase 71 / DEV-034

## 利用者判断

公開・配布成果物の整合性確認など、明確な目的のあるdigest以外の
hashロジックはすべて削除する。ファイル更新を厳密に管理する場合は
正確なメタデータ管理を導入し、当面はファイル更新日時を使う。

この判断は9月21日の「既存ロジックは残してよい」を撤回する。
計算・保存・読込・比較・専用モデル・設定も削除対象とし、無効化した
コードや互換性・診断・将来用という理由で保持しない。既存文書の旧hash
プロパティは通常の読込で無視するか移行で除去し、必要な非hash情報を守る。
公開・配布用digestは対象成果物と整合性保証の目的を特定して残す。

## 反映先と実装時の確認

- [更新管理契約](../../../spec/file-update-management.md)に方針を記載。
- [Phase 71](../../../phase/phase-71.md)の作業項目・完了条件を改訂。
- Phase一覧とCore起点の更新メモを揃え、旧journalには改訂先を追記。
- 実装時にはhash利用を棚卸しし、保持対象の根拠と削除対象を確定する。
  無効化だけで完了とせず、生成フィールド・設定・テストも整理する。
- 更新日時による再生成・再利用、明示的prebuilt採用、旧文書の扱い、
  公開・配布用digestの整合性検証を実行可能仕様で確認する。

今回の変更は方針・契約・計画文書のみ。コード削除、テスト実行、
Phase完了、コミット、pushは実施していない。

## 2026-09-29 split execution ownership

The removal decision is unchanged; no permission to retain obsolete application
hash logic is restored. [Phase 71](../../../phase/phase-71.md) inventories all
uses/purposes and proves the real Core-rooted video bootstrap;
[71.1](../../../phase/phase-71.1.md) removes document/media and inventoried
adjacent consumers; [71.2](../../../phase/phase-71.2.md) removes remaining video
uses; [71.3](../../../phase/phase-71.3.md) corrects local site/PDF/integrity
adapters; [71.4](../../../phase/phase-71.4.md) audits complete removal and
combined-tree acceptance. The serial final-only full SBT owner is 71.4.
All are planned/not started. This is planning ownership, not implementation,
product acceptance, commit or skill modification.
