# CAR電子署名・破損検出・リポジトリ運用の検討

Recorded: 2026-10-09.
Role: non-normative discussion and implementation planning notes.
Work: Phase 82の文書計画。現在の実装・検証作業を再開する記録ではない。
採用済みの挙動は[仕様](../spec/car-distribution-verification.md)を参照する。

## 議論の整理

以前のCAR受け入れ検証は、内部のSHA一覧と、manifest自身を除く全通常ファイルの集合・
SHAを照合していた。これは自己記述との不一致を検出するが、
内容と一覧を一緒に書き換えた攻撃者を認証できない。
生成時のSHA一覧作成と、受け入れ時の全ファイル読み直しの両方に追加I/Oがある。
所要時間を実測したという意味での性能評価はしていない。

配布物全体の外部SHAは、公開したCARの圧縮データや管理情報を含む全バイトを検査する。
内部ファイルの内容検査と検査範囲は異なる。再圧縮すると外部SHAは変わる。
SHAとCARを同時に差し替えられるので、SHAだけでは提供者の真正性を保証しない。

署名付きJARでは、標準の読み取り処理を通る署名対象エントリに検証が働く。
classだけの仕組みではないが、展開後の普通のファイル読み取りには適用されない。
CARではJARの逐次読み取り方式を再現せず、ダウンロード後にCAR全体を検証する方針を採用した。

ABI宣言は利用者向けメタデータとして有用だが、現行ローディング時の簡易検査は
実際のバイナリ互換性検査ではない。宣言を使うカタログやツールの実行時に必要な検査を行う。
「実行時」はCAR内のコードの実行だけでなく、これらの利用者ツールの実行も含む。

## 採用した配置

```text
完成したCAR
  → 公開時に外部SHAを作成し、必要なら外部署名を作成
  → リポジトリで配布
  → 一時領域へ取得
  → SHA照合、署名があれば信頼する鍵で検証
  → 正式キャッシュへ登録
  → 展開・ローディング
```

署名は公開CARでも任意。未署名CARには電子署名による改竄検証をしない。
内部SHA一覧やclass読み込み時のSHA処理は新規導入しない。

## P82-01で確定すること

- 外部署名形式・対応アルゴリズム・ライブラリ。OpenPGPは候補。
  単純な方式と既存配布ツールとの互換性を比較し、独自暗号処理を作らない。
- SHA sidecarの具体的な構文と、公開URLからの取得規則。
- 未署名と署名取得失敗を区別する配布上の表現。明示的な署名有無を
  既存のrelease metadataに置く案を評価し、公開CARの署名必須化に変更しない。
- namespace・提供者鍵情報の形式と、初期信頼鍵の登録方法。
- TUFの採用範囲。独自の鍵配布・鍵更新・期限管理を増やす前に既存実装を評価する。
- 鍵失効を既存キャッシュへどう反映するか、鍵情報の更新頻度、
  古い鍵情報の再提示を扱う範囲。通常ロードでCAR全体を再検証する方式とは分ける。
- 既存リポジトリ・SHAなしの旧公開物への移行。新契約ではSHA必須とし、
  暗黙の無検証フォールバックではなく、必要な互換運用を明示する。
- キャッシュの所有者・権限・同時取得・失敗時の扱い。
  一時取得から成功物を公開する境界に限定し、一般の生成物管理へ拡張しない。

## 運用の検討項目

提供者の鍵登録では、namespaceの管理権限と登録鍵を結び付ける。
リポジトリの初期信頼鍵は、クライアントに事前登録するか、
信頼する経路で指紋を確認して設定する。単に鍵ファイルを取得したことを登録理由にしない。
鍵更新・失効と秘密鍵の保管方法を公開手順と運用手順に分ける。
実際の公開サイトへの配備は、文書計画やローカルfixtureの成功とは別の実行境界である。

## CNCFへの引き継ぎ

[CNCF Phase 104](../../../cloud-native-component-framework/docs/phase/phase-104.md)は
この共通仕様を参照し、信頼設定・ダウンロード検証・正式キャッシュ登録と
ローディング時のABI責務整理を担当する。
[CNCF notes](../../../cloud-native-component-framework/docs/notes/car-distribution-verification.md)には
実際の取得処理への挿入候補と、失敗を通常の未取得へ潰さないための調査事項を記録した。
双方のwire形式を独立に決めず、P82-01とCDV-01で共有する。

## 参考資料

- [Java JAR仕様](https://docs.oracle.com/en/java/javase/21/docs/specs/jar/jar.html):
  署名メタデータとエントリdigestの関係。
- [Java JarFile API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/jar/JarFile.html):
  署名検証と署名者証明書の信頼判断の分離。
- [Maven Resolver checksums](https://maven.apache.org/resolver/about-checksums.html):
  転送破損検出と信頼するchecksum・署名の区別。
- [Apache配布検証](https://www.apache.org/info/verification):
  全体checksum、外部署名、公開鍵の真正性確認。
- [TUF overview](https://theupdateframework.io/docs/overview/):
  信頼する鍵・署名付きメタデータ・有効期限を用いる配布方式。
