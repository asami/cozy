# Phase 90の日英全媒体とリソース管理の範囲補完

Date: 2026-11-16
Disposition: ADOPTED / Phase 90 PLANNED

利用者はDocument Projectのリソース管理計画が、原稿・生成設定を残して中間物だけを外部へ分離する問題を解決するか確認した。その結果、Phase 90の方向は合うが、日本語PDF・確認HTMLに限定された初回範囲では、日英動画・音声・スライドなどを含む今回の媒体を扱い切れないことが分かった。

続く依頼「その方針でPhase 90の開発計画を更新して。日英のPDF・infographic・スライド・音声・動画を対象にして、観測モデルと知識ハーネスを受入例に入れて。」に基づき、[Phase 90](../../../phase/phase-90.md)、[checklist](../../../phase/phase-90-checklist.md)、[機能案](../../../notes/document-project-private-package.md)、[StrategyのDEV-039](../../../strategy/cozy-development-strategy.md)を更新した。

対象をja/enの5媒体と既存確認HTMLに広げ、全producer・子ツール・provider・preview・キャッシュ・一時state/receiptへ外部WorkRootを通す。Core、日英Document、統合index.dox、原図、スライドIR、動画台本、永続設定と必要素材は保持する。音声も選択した完成物と中間音声を用途で区別する。共通設定の集約、移設後の資源解決、共通入力を含む再生成判定をP90-01の設計／仕様化対象へ加えた。

受入例はSimpleModeling.orgの観測モデルと知識ハーネス。各記事のja/en×5媒体を、隔離したローカル作業コピーで実際に生成・確認する計画とした。生成中の中間物ゼロ、元パスを利用できない移設後の再生成、正常・失敗・中断と完成物反映の復旧、Git管理する入力の保持を確認する。既存のLive Prototyping内部資料の利用例も維持し、自動検証には匿名化した同等fixtureを使う。

今回は計画文書の更新だけであり、既存の媒体を再生成していない。実装・受入を開始せず、全Step・全checkboxをOPENのまま保持した。他Phaseの優先順位・Goal・状態、既存build.sbtの変更は保持する。公開・配備・元原稿削除・Gitコミット・pushは行っていない。具体的な設計と仕様への昇格は、Phase実行時のP90-01で扱う。
