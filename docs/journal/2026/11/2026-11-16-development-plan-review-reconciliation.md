# 新規開発計画の精査後補正とLexiDoxの採番

Date: 2026-11-16
Disposition: ADOPTED / documentation update only

利用者は、新規開発項目の精査で挙がった5点の修正、関連文書の整合、既存変更・過去journalの保持と、LexiDoxへの新しい開発番号の割当を指示しました。

## 補正と登録

- [Phase 88](../../../phase/phase-88.md)に[checklist](../../../phase/phase-88-checklist.md)を追加し、全6 StepとAuthHandleの拒否・失効・複数Domain呼出し・Domain側認可を完了条件へ結び付けました。ローカル受入と別範囲の実クラウド受入を区別しました。
- [Phase 89](../../../phase/phase-89.md)のconsumerを旧6.12からsm-workflow 8.1へ更新しました。所在を確認できないproducer-local Phaseへのリンクを、確認済みの受渡し表とDelivery IDへの参照へ置き換え、producer契約・受入改訂の未確認状態を保持しました。
- SimpleModeling.orgのLexiDox概念提案では、例の節見出し後に空行を置き、既存SmartDox構文を維持しました。同文書からCozy Phase 92の計画へ参照します。
- [Phase 91機能案](../../../notes/document-and-section-structure-patterns.md)、Phase、checklist、Strategyを、共有ルール3文書が固定ai-directive改訂606cb56に配置済みである現状へ更新しました。2026-11-15のjournalは当時の記録として変更していません。
- LexiDoxを[Phase 92](../../../phase/phase-92.md)／[checklist](../../../phase/phase-92-checklist.md)、DEV-041として採番し、Phase一覧とStrategyへ登録しました。Phase 89も既存範囲のままDEV-042としてStrategyへ登録しました。

## 責任と状態

LexiDoxはSimpleModelingの概念提案を参照し、SmartDoxのproducer契約とCozyのBoK消費を分けます。Cozy Phase 92の採番は他リポジトリの実装開始や所有権の移動を意味しません。Phase 90・91との機能境界と既存の優先順位を保持します。

今回は計画・例・進捗台帳の文書補正です。Phase 88〜92はplannedで、追加したchecklistは全項目OPENです。機能実装・製品テスト・公開・配備・commit・pushは開始していません。既存変更と過去のjournalを保持します。
