# Phase 45 記事確認HTML要件の整理

日付: 2026-09-02

対象Phase: [Phase 45: Document Project Operational Completion and Article 8 Integration](../../phase/phase-45.md)

関連開発項目: `DEV-016`

## 背景

SimpleModeling.orgの第8回記事を、従来の単一SmartDoxファイルではなく、
`src/main/doxsite`配下のDocument Projectとして作成する試行を開始した。

試行先は次のプロジェクトである。

```text
simplemodeling-org/
  src/main/doxsite/development-process/domain-modeling.dox/
```

Document Projectのscaffold、`inspect`、`plan`、`verify`、dashboard生成は実行
できた。一方、記事確認HTMLを求めた際に、SmartDoxの`index.dox`を通常の
HTMLへレンダリングしただけの成果物が「記事確認HTML」として提示された。

これは要件を満たさない。記事本文のHTMLと、記事内容をレビューするための
記事確認HTMLは、目的、入力、表示内容、ライフサイクルが異なる別のWork
Productである。

さらに、現行の次のコマンドも記事確認HTMLの代替にはならない。

```text
cozy document-project review <project> --kind slides
```

現行実装は`presentation/visual-pages.yaml`の内容を表形式で表示するソース投影
であり、記事の論理構成、主張、用語、図解意図をページ単位で確認する表示に
なっていない。

この実作業によって、Phase 45の`article-review.html`要件を具体化する必要が
明確になった。

## 成果物の区別

Phase 45では、少なくとも次の四つを区別する。

| Work Product | 目的 | 主な利用者 | 意味上の位置付け |
| --- | --- | --- | --- |
| `index.dox` | 記事本文を記述する | 著者、生成AI、SmartDox | 記事表現の正本 |
| article HTML | 公開・閲覧用の記事を表示する | 記事読者 | `index.dox`から生成する配布物 |
| `presentation/visual-pages.yaml` | 記事の主張、順序、可視化意図をページ単位で記述する | 著者、生成AI、レビュー機構 | 記事内容確認ページの意味上の正本 |
| `article-review.html` | 記事の論理構成と可視化を人間が確認する | 著者、レビュー担当者 | 読み取り専用のレビュー投影 |

project dashboardは、プロジェクト全体の状態、阻害要因、承認待ち、次の操作を
確認するための成果物であり、`article-review.html`の代替ではない。

Visual Page YAMLをそのまま表や`pre`要素へ表示したHTMLも、ソース診断には
利用できるが、`article-review.html`の代替ではない。

## 記事確認HTMLの目的

`article-review.html`は、公開記事を読むためのHTMLではなく、記事を公開可能な
状態へ近づけるための内容確認UIである。レビュー担当者は、次の事項を記事の
流れに沿って確認できる必要がある。

- 中心命題と記事のスコープ
- 節と節の論理的な接続
- 各ページが担う一つの説明上の役割
- 主張、補足、例、結論の順序
- 用語と初出表記
- 図解する対象と、図解が伝える関係
- 記事本文とVisual Pageの対応
- Content Coreの共有意味との対応
- infographic、まとめスライド、動画へ共有する内容と、記事固有の内容の区別

レビュー担当者がSmartDoxソースやYAMLの内部表現を解釈しなくても、記事の
説明構造を理解し、指摘できることが必要である。

## 表示要件

記事確認HTMLは、SimpleModeling.orgシリーズで従来使用してきた確認HTMLと
同様に、ページ単位で内容を確認できるUIとする。

最低限、次の表示を持つ。

- 記事名、シリーズ回、言語
- 現在ページと総ページ数
- 前後ページへの移動
- キーボードによる移動
- ページの安定した識別子
- ページタイトル
- 読者へ見せる主要な文言
- 図または関係の可視化
- 必要に応じた短い補足

内部の制作指示、プロンプト、renderer座標、デバッグ情報を読者向け表示へ
混在させない。`visualIntent`や記事節との対応は、レビュー補助情報として表示を
切り替えるか、診断ビューへ分離できる形にする。

## 意味上のauthority

記事確認HTML自体を意味上の正本にしない。

```text
Content Core
  ↓ shared semantic authority
index.dox
  ↓ article expression authority
presentation/visual-pages.yaml
  ↓ article-content page authority
article-review.html
  ↓ read-only review projection
human review decision
```

記事本文の修正は`index.dox`へ戻す。ページ構成、ページ上の主張、図解意図の
修正は`presentation/visual-pages.yaml`へ戻す。生成済みHTMLだけを直接修正して
意味変更を保存してはならない。

Content Coreで共有すべき指摘はContent Core候補・承認ループへ返す。記事固有の
文章表現や節構成への指摘は、記事・Visual Pageのローカルフィードバックとして
扱う。

## 生成契約

Phase 45でCozyが提供する記事確認HTML生成は、次を満たす必要がある。

1. `index.dox`、Content Core、Visual Pageを明示的な入力として扱う。
2. 入力のハッシュとプロファイルを生成receiptへ記録する。
3. 同一入力から決定的な自己完結HTMLを生成する。
4. 外部CDN、外部JavaScript、外部フォントを必須にしない。
5. Review生成は読み取り専用で、Content Core、記事、Visual Pageへ暗黙の
   write-backを行わない。
6. 現行入力とreceiptの入力identityが異なる場合はstaleとして表示する。
7. article HTML、article PDF、まとめスライドPDF、動画とは別のWork Product
   identityを持つ。
8. 生成先と公開先を分離し、確認HTMLを公開記事へ混入させない。

推奨するpublic surfaceは、Phase 45の計画どおり`article-review.html`を第一級の
Work Productとして追加する形である。`slides-review.html`を互換性なく別の意味へ
変更するのではなく、Visual Pageソース診断と記事内容確認を明確に分離する。

## レビューと承認

記事確認HTMLの生成だけでは、記事またはページ構成を承認済みにしない。

レビュー結果は、少なくとも次の分類を持つ。

- `accepted`: 現在の入力identityに対して人間が承認した
- `changes-requested`: 現在の入力identityに対して修正を要求した
- `pending`: まだ判断されていない
- `stale`: 承認後に依存入力が変化した

承認証拠は、対象となるContent Core、`index.dox`、Visual Page、共有
infographicのidentityを保持する。記事本文またはVisual Pageの変更後に、古い
承認を現在の承認として扱ってはならない。

日本語版と英語版は同一の意味を共有するが、文章をbyte-identicalにする必要は
ない。言語ごとの記事確認と、言語間のsemantic parity確認を分ける。

## Article 8ドライバで必要な確認

SimpleModeling.org第8回「ドメインモデリング」をPhase 45の実ドライバとして、
次を確認する。

- Document Projectが`src/main/doxsite/development-process/domain-modeling.dox/`
  に存在する。
- `index.dox`と`presentation/visual-pages.yaml`が別のauthorityとして存在する。
- `article-review.html`が記事本文のレンダリングではなく、ページ単位の記事内容
  確認UIとして生成される。
- 記事確認ページから記事の全主要節をたどれる。
- 用語、コンテキスト、境界づけられたコンテキスト、ユビキタス言語、三つの
  モデル構成要素、人間と生成AIの協業、注文確定の例、次回への接続を確認できる。
- Visual Page YAMLのraw表示だけでは受け入れない。
- project dashboardだけでは受け入れない。
- article HTMLだけでは受け入れない。
- `target/`、Content Core内部、review evidence、attempt、receiptを公開記事へ
  混入させない。
- 第7回以前の記事をDocument Projectへ移行せず、変更もしない。
- 公開、デプロイ、アップロード、pushなしでローカル受け入れを完了できる。

## 現在の一時対応と扱い

Article 8の試行では、Visual Page authorityから、ページ送り可能なローカル
`article-confirmation.html`を一時的に作成した。同時に、現行Cozyの
`slides-review.html`も生成したが、後者はVisual Pageソースの表形式投影である。

一時HTMLは、Phase 45の要求を具体化しレビューするための参考実装であり、Cozyの
受け入れ済み生成契約ではない。意味上のauthority、正式receipt、Phase完了証拠
として扱わない。

Phase 45では、次のgapを正式に閉じる。

```text
COZY-GAP-DOCUMENT-PROJECT-ARTICLE-REVIEW
```

閉じる条件は、単に同名のHTMLが生成されることではない。記事内容をページ単位で
確認でき、authority、currentness、review decision、stale propagationを含む
第一級Work ProductとしてDocument Project workflowへ統合されることである。

## Phase 45への対応付け

本記録の要件は、Phase 45の各Stageへ次のように対応する。

| 要件 | Phase 45 Stage |
| --- | --- |
| article HTMLとarticle reviewの区別 | P45-01 |
| `article-review.html`の第一級Work Product化 | P45-01、P45-03 |
| Content Coreとのshared semantic review | P45-02、P45-03 |
| Visual Pageを用いたページ単位の内容確認 | P45-03 |
| dashboardとの責務分離 | P45-04 |
| JA/ENの言語別確認とsemantic parity | P45-05 |
| Article 8でのローカル受け入れ | P45-06 |

## 分割後のPhase所有（2026-09-02）

承認済みの分割では、article reviewの契約上の第一級Work Product化はPhase 45の
P45-01、実際のarticle/video review投影とdashboard責務分離はPhase 45.1の
P45-03/P45-04、JA/EN整合とArticle 8 local acceptanceはPhase 45.2の
P45-05/P45-06が唯一の所有者である。この記録の「Phase 45」は、必要に応じて
この順序付きPhase列を指す。

## 非要件

- 記事確認HTMLから直接公開すること
- 記事確認HTMLを記事本文のauthorityにすること
- article HTMLを画面分割しただけの表示
- Visual Page YAMLを整形表示しただけのレビュー
- 生成AIによる自動承認
- 第7回以前の記事の移行
- Phase 45作業に伴う公開、デプロイ、アップロード、push

## 結論

記事確認HTMLは、記事HTMLの別名でも、Visual Pageソースの診断表示でもない。
記事の意味、説明順序、用語、図解意図をページ単位で人間が確認するための独立した
レビュー投影である。

Phase 45では、この区別をpublic contract、Work Product、生成receipt、レビュー
decision、stale propagation、Article 8のExecutable Specificationとして実装・
検証する。
