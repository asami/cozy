# Phase 71: Core起点の成果物更新と動画承認ハッシュの扱い

Date: 2026-09-21
Development item: DEV-034 / [Phase 71](../../../phase/phase-71.md)
Status: planning decision; implementation and acceptance remain open

## 背景と訂正

[Phase 30](../../../phase/phase-30.md)は、Storyboardの入力変更に伴う
音声・描画成果物の無効化と、Storyboardや確認用動画のハッシュを人間の
承認記録とみなして生成を拒否する条件を同時に導入した。変更された入力から
動画を作り直す通常の作業が、承認値の手動更新なしでは止まる。

[2026-09-14の決定](2026-09-14-confirmation-views-and-make-dependencies-decision.md)
では、利用者はハッシュによる成果物管理を退け、通常のmake程度の
更新日時による依存管理を選んだ。また、最終成果物の依頼から直接のDSLを
再帰的にCoreまでたどり、Core側から必要なDSLを生成・更新する流れを
指定した。[Phase 60](../../../phase/phase-60.md)はローカルHTMLの
makeレベル更新を実装し、[Phase 60.1](../../../phase/phase-60.1.md)は
Core起点の一般的なオーサリング経路を閉じた。ただしPhase 60.1の
実際の通し検証はCore・Document・Summary・記事と3種類のHTMLであり、
動画は検証していない。既存のPhase 30動画承認ゲートもこの決定に
合わせて修正されなかった。

## 今回の利用者判断

- 「動画を生成して」という依頼では、選択された動画から直接のDSL、
  その親DSLをCoreまでたどる。Core側から、更新日時により古くなった
  DSLを依存順に更新した後、現在の`storyboard.md`から動画を更新する。
- `storyboard.md`だけの変更なら対応する動画を更新する。Coreや
  中間DSLの変更なら、その子孫を順に更新してから動画を更新する。
  無関係な成果物は更新しない。
- 人間による事前承認は現段階の生成条件にしない。ハッシュを用いた
  精密な管理は当面運用を保留する。既存ロジックは残してよいが、
  ハッシュの欠落・不一致を生成エラーに使わず、スキルに値の設定を
  要求しない。

この決定は、人間が成果物を見て評価する行為や、別の公開・配信判断を
否定しない。生成の依存管理と承認を混同しないという境界である。

## 実装への引き継ぎ

[詳細ノート](../../../notes/core-rooted-make-level-media-regeneration.md)
をPhase 71の計画入力とする。Phase 71は一つの正本Core、一つの
動画Storyboard、宣言済みDSL依存辺と各ノードの作成担当を確定し、
逆向き解決・前向き更新を現在の動画経路に接続する。動画の
`approvedIdentity`群は互換的に読めても、通常の生成エラー判定には
使わない。確認用動画は任意で、最終動画の必須の承認段階ではない。

実際のSimpleModeling.org `ai-development-harness`の宣言を使った
隔離コピーで、Core変更からStoryboard更新、動画生成まで同じ入口を
通して確認する。直接の動画ビルド、架空のテスト用グラフ、古い
`script.json`への切り替えだけではPhase完了の証拠にならない。

この記録は方針と未解決の実装境界を残すもので、Cozy本体、スキル、
SimpleModeling.orgのソース、動画、Git履歴は変更しない。
