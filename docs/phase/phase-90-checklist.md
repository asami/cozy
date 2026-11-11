# Phase 90 checklist — Internal Document Packages and External Build Workspaces

status=planned
Plan: [Phase 90](phase-90.md)
Owner: Cozy development

## Stage Status

- Current status: OPEN
- Owner: Cozy development
- Update rule: 実装・検証結果に対応する項目だけ更新します。
- Closure basis: P90-01–P90-04の全受入項目。計画作成は実装完了ではありません。

## P90-01: Contract and write-path inventory

- [ ] Project、provider、renderer、launcher、preview、state/cache/logの書き込み先を調査する。
- [ ] notesをdesign/specへ具体化し、配置設定、CLI、製品選択、所有出力と衝突処理を定義する。
- [ ] 外部WorkRootの既定値・設定優先順・重複拒否・一時物の保持／破棄を定義する。
- [ ] 原稿資源の移設と既存絶対参照の移行を定義し、必要な上流変更を特定する。
- [ ] 生成失敗と配布コピー失敗／中断を区別し、同期領域内一時物なしの復旧方式を定義する。

## P90-02: External rendering workspace

- [ ] native PDF、本文HTML、Core確認HTMLで中間物と実行状態を外部target/へ配置する。
- [ ] 子ツールのcwd、入力コピー、環境、一時物、画像資源を外部領域で解決する。
- [ ] WorkRootが利用できない場合は診断し、同期領域内へのフォールバックをしない。
- [ ] 実行中のファイル作成観測により、同期領域内の中間物ゼロを確認する。
- [ ] 既存プロジェクトの生成・図表・ロケール選択の互換性を検証する。

## P90-03: Package placement and refresh

- [ ] 原稿・永続設定・図をsource/へ、選択完成物をトップへ配置し、ログ・target・キャッシュを除外する。
- [ ] 配置先のsource/を正本として編集・再生成でき、元の絶対パスを必要としない。
- [ ] 成功した製品のみ所定位置へ反映し、生成失敗では旧完成物を保持する。
- [ ] 所有外ファイルの衝突、別資料の保持、出力更新、コピー中断・再試行を検証する。
- [ ] 確認HTMLを外部領域で閲覧でき、明示選択した場合のみ完成物として配置する。
- [ ] 承認ハッシュゲート、クラウドへの自動公開、元原稿の自動削除を導入しない。

## P90-04: Integrated acceptance and closure

- [ ] 同等fixtureで配置→編集→PDF/HTML再生成→完成物更新→別パスへ再配置を実行する。
- [ ] 旧原稿を参照できない状態で図・表・日本語PDFを生成し、ローカル実資料でも利用手順を確認する。
- [ ] 正常・失敗・中断経路で同期フォルダの実行中書き込み範囲を確認する。
- [ ] 必要なfocused/integration検証、独立レビュー、最終Cozy通常検証を実施する。
- [ ] 配置設定・外部作業領域・復旧・未対応製品の利用手順と残課題を記録する。
- [ ] 実際の受入結果に基づきPhase/checklist/strategyを更新し、所定の完了手順を実施する。
