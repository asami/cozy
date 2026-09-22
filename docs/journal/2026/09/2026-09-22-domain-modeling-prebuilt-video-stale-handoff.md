# 第8回prebuilt動画の誤stale判定：Cozy修正handoff

Date: 2026-09-22
Status: diagnosis complete; implementation not started
Owner: Cozy / Phase 71, P710-03 の独立した先行修正候補

## 事象と再現点

SimpleModeling.orgで `sh etc/runweb-production.sh` を実行すると、第8回
`domain-modeling` の公開素材準備は5件の `current` を返して成功する。その次の
`cozy media build etc/media-registration/domain-modeling/registration-v2/media.yaml`
が次で停止する。

```text
Media prebuilt resource output is stale and must be refreshed: article-video-ja
```

この停止は `runweb-production.sh` に一時追加され、既に取り消された
`ai-development-harness` の登録行より前に起こる。今回の調査で本番サイト生成
スクリプトは変更していない。全体スクリプトの再実行も行っていない。

## 確認済みの入力差分

- `registration-v2/target/cozy-media/manifest.json` の受理時刻は
  `2026-09-21T14:43:09.205999Z`（日本時間23:43）。そのreceiptは第8回
  `index.dox` を `cozy:knowledge` と `site-document-route` の両方で参照する。
- receiptにある `index.dox` のSHA-256は
  `bbfbc2d2f70764de4561c5269e6642aa82ebb4cf1f4a18ab62776d`。日英併記へ
  更新された現行ファイルは
  `a4dcb93ae55be14b3fe472a8e02c4880bc80aa227c0928d47b77d14bb65e3104`。
  ファイルの更新時刻は日本時間2026-09-22 00:01。
- 第8回MP4 `target/media/development-process/domain-modeling/ja/final.mp4` の
  SHA-256はreceiptおよび `video/ja/production.json` の記録と同じ
  `b66b24a8a304c99e08d080ec5dfc14686d8fe7ac7568b8039d665dd670be070d`。
  `production.json` とアダプター内のコピーも同一である。
- 記事の日英併記化によって動画の受理済みバイト列は変更されていない。
  第8回PDF・動画の再生成は依頼されていない。

## 原因境界

`CozyMediaReceipt._validate_prebuilt_adoption` は、既存manifestの出力ハッシュと
現行prebuiltファイルのハッシュが同じで、共通の `inputSetSha256` が変わった場合に
選択リソースを拒否する。`index.dox` は登録アダプターの共通receipt入力であるため、
記事の変更が動画の実際の生成入力の変更かを区別せず、変更のない
`article-video-ja` までstaleになる。

これはCozyの既存仕様・`CozyMediaReceiptSpec` の「stale same-output prebuiltを
拒否する」期待に沿った動作であり、偶発的な例外ではない。Phase 71で採用した
makeレベルの依存管理・ハッシュを通常の生成停止条件にしない方針との
契約差分として修正する。単にmanifestを削除して初回採用させたり、
MP4のバイト列を変えて検査を通したりしない。

## 修正の境界

Phase 71のP710-03に、このprebuilt登録アダプターの修正を独立した先行項目として
取り込む。動画の実際のproducerと直接入力は既存のCozy動画manifest、
`production.json`、動画プロジェクトの宣言で確認する。記事本文の変更だけを
動画の再生成・再承認理由にしない。一方、欠落・破損したファイル、
公開情報と異なる動画ハッシュ、producerや実際の依存が不明な外部prebuiltを
無条件に受理しない。

コードの候補境界は `src/main/scala/cozy/media/CozyMediaReceipt.scala`、
実行可能仕様の候補境界は
`src/test/scala/cozy/media/CozyMediaReceiptSpec.scala`。必要なら登録処理の
近接仕様まで確認する。仕様・テストで従来の「同一出力＋共通入力変更なら拒否」
という期待を変更する前に、動画の実際の依存と受理条件を確定する。

## 受け入れ条件と非目標

1. 隔離した第8回フィクスチャで、受理後に記事本文だけを更新しても、
   変更のないMP4を原因として `media build` が停止しない。
2. 出力MP4のバイト列と `production.json` の動画SHA-256は不変。
   手動の承認ハッシュ転記を要求しない。
3. 欠落・破損・公開情報との不一致、およびproducer/依存が不明なprebuiltは
   明確に失敗する。既存の安全なパス・整合性検査を維持する。
4. 修正済みCozyで、元の `registration-v2/media.yaml` のビルドを狭く検証する。
   その後に限り、利用者側で通常の本番サイト生成を再試行できる。

`runweb-production.sh`、第8回のPDF・動画、YouTube公開物、
SimpleModeling.orgの登録定義は本修正で変更しない。Phase 71全体の
Core→Storyboard→動画の完了条件は別に残す。このhandoff自体は
Cozyの仕様やPhaseの完了状態を変更しない。
