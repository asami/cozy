# Phase 90 checklist — Internal Document Packages and External Build Workspaces

status=planned
Updated: 2026-11-16
Plan: [Phase 90](phase-90.md)
Owner: Cozy development

## Stage Status

- Current status: OPEN
- Owner: Cozy development
- Update rule: 実装・検証結果に対応する項目だけ更新します。
- Closure basis: P90-01–P90-04の全受入項目。計画更新は実装・媒体生成の完了ではありません。

## P90-01: Contract and write-path inventory

- [ ] Project、provider、renderer、launcher、preview、state/cache/logについて、日英のPDF・infographic・スライド・音声・動画と確認HTMLの書き込み先を調査する。
- [ ] notesをdesign/specへ具体化し、配置設定、CLI、製品・ロケール選択、所有出力と衝突処理を定義する。
- [ ] 外部WorkRootの既定値・設定優先順・重複拒否・実行ごとの所有範囲と一時物の保持／破棄を定義する。
- [ ] Core、日英Document、統合index.dox、原図、スライドIR、動画台本、永続設定・外部素材、完成物、中間物を用途で分類し、手書きの入力を保護する。音声の完成物選択と中間音声を区別する。
- [ ] 原稿・設定・必要な画像と選択完成物のGit管理方針、WorkRootがGit作業ツリーに含まれる場合の限定ignoreを定義する。既存ignoreと追跡済みファイルを保持する。
- [ ] 共通toolchain・renderer・style設定と記事固有設定の境界、設定層・参照解決・再生成判定を定義する。現行の共有コールバックと上流APIの変更要否を調査する。
- [ ] 原稿資源の移設と既存絶対参照の移行、必要な資源materializationを定義する。記事PDFの日英入力を同じindex.doxとロケール選択で表す。
- [ ] 生成失敗と完成物反映の失敗／中断を区別し、同期領域内一時物なしの復旧方式と既存中間物の明示的な移行範囲を定義する。

## P90-02: External rendering workspace

- [ ] native PDFのja/en両経路と本文HTML・Core確認HTMLを外部WorkRootへ接続し、既存の図表・採番・ロケール選択を維持する。
- [ ] infographicの画像変換とスライドのレンダリング・組立て・確認画像を外部WorkRootへ接続する。
- [ ] ja/enの音声合成・発音用中間データ・対話レンダリング・部分動画・最終結合・動画確認の作業領域を外部WorkRootへ接続する。
- [ ] 子ツールのcwd、入力コピー、環境、一時物、資源解決、provider設定、バンドル・キャッシュ、ログ・一時state/receiptを外部領域で扱い、選択完成物と区別する。
- [ ] 共通設定とrendererを通常のnative経路から再利用し、記事ごとに実行コードを複製しない。共通入力の変更で必要な生成を判定できる。
- [ ] WorkRootが利用できない場合は診断し、Project・同期領域内へのフォールバックをしない。
- [ ] 全対象経路の実行中ファイル作成を観測し、Project・Package・同期領域内に中間物が一時的にも作られないことを確認する。
- [ ] Git管理するDocument Projectでも中間物と一時的な実行状態を外部WorkRootへ配置し、既存の通常プロジェクト生成との互換性を検証する。

## P90-03: Package placement and refresh

- [ ] Core・各言語Document・index.dox・原図・IR・台本・永続設定・必要素材をsource/へ、選択完成物だけを所定位置へ配置し、作業用コピー・ログ・target・キャッシュを除外する。
- [ ] 配置先source/を正本として編集・日英再生成でき、元のプロジェクトやホスト固有の絶対パスを必要としない。共有設定の同梱・外部依存の扱いを明示する。
- [ ] PDF・infographic・スライド・音声・動画ごとに成功した選択完成物のみを反映し、生成失敗では既存完成物を保持する。
- [ ] 所有外ファイルの衝突、別資料の保持、コピー失敗・中断、再試行と部分反映の復旧を検証する。
- [ ] 確認HTML・スライド確認画像・動画確認資料を外部領域で利用でき、明示選択したものだけを完成物として配置する。
- [ ] 既存中間物の移行・削除は明示した所有範囲に限定する。手書きの生成元・完成物・公開記録、既存ignore・追跡状態を自動削除しない。
- [ ] 承認ハッシュゲート、クラウドやYouTubeへの自動公開、元原稿の自動削除を導入しない。

## P90-04: Integrated acceptance and closure

- [ ] 匿名化した同等fixtureで配置→編集→外部生成→完成物更新→別パスへの再配置を、正常・失敗・中断経路で自動検証する。
- [ ] 観測モデルを隔離したローカル作業コピーで受入し、ja/enそれぞれの記事PDF・infographic・まとめスライド・音声・動画を既存native経路で生成・確認する。
- [ ] 知識ハーネスを隔離したローカル作業コピーで受入し、ja/enそれぞれの記事PDF・infographic・まとめスライド・音声・動画を既存native経路で生成・確認する。
- [ ] 両記事の全媒体で、Core・台本・原図・永続設定の保持、同じindex.doxからのロケール選択、媒体間の参照と選択完成物の成立を確認する。必要な音声・映像の確認も受入記録へ区別して残す。
- [ ] 両記事を別パスへ配置し、旧原稿・旧絶対パスを利用できない状態で図表を含む日英全媒体を再生成できることを確認する。
- [ ] 全対象の生成と正常・失敗・中断時に、Project・Package・同期領域への中間物の作成が実行中にもないことを確認する。
- [ ] Git管理fixtureで中間物が追加候補にならず、原稿・設定・必要画像が追跡可能なことを確認する。完成物方針と既存ignore・追跡状態を保持する。
- [ ] 既存のLive Prototyping内部資料の日本語PDF・確認HTMLの利用例を維持し、非公開内容を公開fixtureへ取り込まない。
- [ ] 実記事の既存完成物と公開情報を保持し、受入による本番成果物の上書き・アップロードを行わない。
- [ ] 必要なfocused/integration検証、独立レビュー、最終Cozy通常検証を実施する。
- [ ] 配置設定・共通設定・外部作業領域・再生成・復旧・未対応製品の利用手順と残課題を記録する。
- [ ] 実際の受入結果に基づきPhase/checklist/strategyを更新し、所定の完了手順を実施する。
