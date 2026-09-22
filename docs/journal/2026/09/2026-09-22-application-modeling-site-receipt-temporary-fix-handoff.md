# Application Modeling site build の receipt 停止：仮対処 handoff

Date: 2026-09-22
Status: handoff for the active Terra task; implementation and validation not performed here
Owner: active Cozy / SimpleModeling.org site-build repair task
Follow-up authority: Cozy Phase 71, especially P710-03 and P710-04

## 目的と境界

SimpleModeling.org の通常のローカル site build を、記事または動画の単独更新後も
完走させる。当面の Terra タスクでは現在の停止を解消する**狭い仮対処**を行い、
Cozy Phase 71 が共有ハッシュによる鮮度判定を make レベルの依存関係へ置き換える
本対応を所有する。この handoff は Phase 71 の再設計・完了を先取りしない。

既に別タスクの未コミット差分が Cozy の `CozyMediaReceipt.scala` と spec、
SimpleModeling.org の `application-modeling.dox/index.dox` などに存在する。
それらを破棄・上書きせず、着手時の差分と担当を再確認する。

## 観測された事実

貼付ログの `sh etc/runweb-production.sh` は段階的に次の停止を示した。

1. `domain-modeling/registration-v2/media.yaml` の `article-video-ja` が
   `Media prebuilt resource output is stale...` で停止。
2. 次の実行では同じ登録の `summary-slides-ja` が同じ理由で停止。
3. 最後の実行では domain-modeling の media build と `register-site` が通過。
   続く `application-modeling.dox/media-publication.json` の `register-site` が
   `Media resource lacks current cozy.media.receipt.v2 evidence:
   publication-article-en` で停止した。サイト全体の完走は未確認。

最後の停止は `CozyArticleMediaSiteBinding._pdf_candidate` が
`CozyMediaReceipt.requireCurrent` を呼ぶ経路にある。`publication-article-en`
の `article/article-en.pdf` は記録済みの出力と同一で、英語原稿
`article/article-en.dox` と `video/en/production.json` も記録値と同一。
一方、作業中の `index.dox` は記録時から変更されている。`index.dox` が
パッケージ共通 receipt に含まれるため、PDF 自身の入力・出力が不変でも
`CozyMediaReceipt.current` は false になり得る。さらに同じ経路で
`CozyMediaPdfReviewState.requireCurrent` が呼ばれるため、最初の停止を
越えた後にも package-wide な PDF review-state 判定を確認する必要がある。

この原因説明は最後のエラー、コードの呼出経路、およびファイル照合に基づく。
仮対処後の site build 成功や、他の未発見停止がないことはまだ証明していない。

## Terra タスクへの実装 handoff

- まず、現在の未コミット修正の所有境界を確認し、同じファイルの別作業を
  巻き戻さない。domain-modeling の動画・スライド停止と今回の PDF 停止を
  別々の観測として扱う。
- `application-modeling` の登録で、変わっていない英語記事 PDF が、無関係な
  共通入力の変更だけで拒否されないようにする。receipt 判定を緩めた結果、
  PDF review-state が次の同種の停止を起こすなら、同じ仮対処の範囲で確認する。
- 修正は site build を通すための最小限にする。新しいリソース別 SHA 例外、
  手動の manifest/receipt 書換え、receipt の削除、PDF/MP4 の無意味な
  バイト変更、無関係な成果物の再生成を解決策にしない。
- ただし、実際に欠落・破損した PDF、危険なパス、公開先の成果物との
  不一致は受理しない。公開物のハッシュ生成・実ファイル整合性検査を
  一律に廃止する趣旨ではない。
- `runweb-production.sh` 自体やサイトの公開・アップロード・Git 履歴を
  変更する必要が出た場合は、その根拠と範囲を独立して示す。

## 仮対処の受入条件

1. 隔離したコピーで、記事だけ変更・PDF の直接入力と PDF 自体は不変の
   `application-modeling` を登録できる。動画だけ変更するケースも、
   既存の domain-modeling 登録を退行させない。
2. `cozy media build` 単体ではなく、同じローカル site-build entry point を
   domain-modeling 登録後、application-modeling 登録後、最終生成サイトまで
   実行して結果を記録する。共有作業ツリーで無断の全体再実行はしない。
3. 不正な入力で止まる場合は、処理段階、resource ID、実際に満たせない条件、
   次の操作を表示する。裸の `stale` や receipt schema 名だけで終わらせない。
4. 適切な focused spec と元の再現経路で確認し、仮対処の残余制約を
   Phase 71 に渡す。仮対処の成功を Phase 71 完了とは記録しない。

## Phase 71 に残す本対応

選択された成果物の実際の producer/input 関係と更新日時で生成・再利用を
判断し、メディア生成・prebuilt 受入・ローカル site 登録準備から
過剰な receipt/hash 鮮度ゲートを除く。既存の hash 生成コードは残してよいが
内部の実行可否判定には使わない。公開物そのものの目的ある digest と
整合性検査は別境界として維持する。動画だけ・記事だけを差し替えた双方で
ローカル site build が完走することを Phase 71 の受入条件とする。
