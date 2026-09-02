# Phase 45 Document Project要件再整理

日付: 2026-09-02

対象Phase: [Phase 45: Document Project Operational Completion and Article 8 Integration](../../phase/phase-45.md)

関連開発項目: `DEV-013`, `DEV-016`

関連記録:

- [Document Project Content Core direction](../08/2026-08-30-document-project-content-core-direction.md)
- [Document Project workflow management specification proposal](../../notes/document-project-workflow-management-specification-proposal.md)
- [Phase 42](../../phase/phase-42.md)
- [Phase 42.1](../../phase/phase-42.1.md)
- [Document Project specification](../../spec/document-project.md)
- [Phase 45 記事確認HTML要件](2026-09-02-phase-45-article-review-requirements.md)

## 目的

Document Projectの検討資料とPhase 42/42.1の最終仕様・実装を比較し、次の二種類の
差分をPhase 45の要件台帳として整理する。

1. 検討時に必要性が認識されていたが、最終仕様へ取り込まれなかった要件
2. Phase 42/42.1のGoalまたはStageには存在したが、実装・ドライバ受け入れが
   利用者ワークフローを成立させる水準へ達していない要件

Phase 42/42.1は、Document Projectのclosed descriptor、Workflow Definition、
Work Product、evidence-derived state、review/dashboard projection、attempt history、
scaffoldと安全なpath境界を確立した。この内部モデルは維持する。

一方、Document Projectを利用者が記事制作に使うためのend-to-end workflowは
つながっていない。Phase 45はPhase 42のkernelを作り直すPhaseではなく、kernelを
使って当初の利用者ワークフローを完成させるPhaseである。

## 比較結果の要約

### 最終仕様へ取り込まれなかった要件

| ID | 要件 | 検討時の意図 | Phase 42.*の最終状態 |
| --- | --- | --- | --- |
| P45-REQ-001 | 記事確認HTML | 記事の構成、主張、用語、図解意図を確認する`article-review.html` | Work Productとpublic review commandから脱落 |
| P45-REQ-002 | 日英成果物の関連付け | localized Content Coreと安定した意味IDでJA/EN成果物を同期 | 1プロジェクト1言語。言語間契約なし |
| P45-REQ-003 | 成果物ごとの意味的承認 | Coreと記事・図・スライド・動画の意味的対応を人間が承認 | hash、存在、currentnessは追えるが意味的承認者と判断を表せない |
| P45-REQ-004 | optional成果物の選択・有効化 | プロジェクト利用者が必要な成果物を選択してworkflowへ参加させる | profileによる静的`required/optional/disabled`のみ |
| P45-REQ-005 | infographicの実使用確認 | 同じinfographicが記事、PDF、スライド、動画へ可視的に組み込まれる | authorityと依存hashは追えるが、表示されたことを保証しない |
| P45-REQ-006 | `*.dox/`からサイト記事への投影 | project rootの`index.dox`を通常の記事identity/URLへ安全に投影 | safe mappingは証拠として表せるが、SmartDox hostへのsource projectionは対象外 |

### 要件は存在したが、実装・受け入れが不足しているもの

| ID | 要件 | 期待された利用者体験 | 現状の不足 |
| --- | --- | --- | --- |
| P45-REQ-007 | 利用者Dashboard | 現在位置、変更、blocker、承認待ち、次の操作を理解・選択できる | 内部DAG、provider、gate、state表が中心で、行動選択UIになっていない |
| P45-REQ-008 | Content Core候補レビュー | 候補と承認済みCoreを比較し、指摘、修正、承認できる | `core-review.html`は承認済みentryのみ。候補をmaterializeしない |
| P45-REQ-009 | AI対話からCore候補を作る経路 | ideaとAI対話から候補を作り、provenance付きでレビューへ渡す | `run`はproviderを実行せず、deferred diagnosticを持つAttemptを記録するだけ |
| P45-REQ-010 | Video Review | scene intent、narration、visual、timing、render結果を確認する | StoryboardとVisual Page YAMLのraw source projectionが中心 |
| P45-REQ-011 | Phase 41実連携 | Explanation Structure Reviewをarticle/slide/video reviewへ組み込む | Work Product名とsource projectionはあるが、Phase 41入力・生成物へ接続しない |

## 詳細要件

### P45-REQ-001: 記事確認HTML

`article-review.html`を第一級のWork Productとして追加する。

記事HTML、記事PDF、project dashboard、Visual Page YAMLのraw表示を代替として
認めない。記事確認HTMLは、記事の論理構成、narrative flow、用語、図解意図、
Content Coreとの対応をページ単位で確認できる自己完結・読み取り専用の投影とする。

authority、生成契約、review decision、stale propagation、Article 8 driverの詳細は
[Phase 45 記事確認HTML要件](2026-09-02-phase-45-article-review-requirements.md)
に記録する。

### P45-REQ-002: 日英成果物の関連付け

現在の`cozy.document-project.v1`は一つの`language`と一つの
`content/core-<language>.yaml`を選択する。これは単一localeのkernelとして維持する。

Phase 45では閉じた`cozy.document-project.v2` descriptorによって、次を表す。
この変更は運用前に行うため、v1 reader、migration、互換modeは保持しない。

- 一つのshared semantic identityに属するlocale variant
- 日本語Coreと英語Coreで対応するstable semantic item ID
- article、infographic、summary slides、videoのJA/EN variant identity
- 言語別review decision
- 言語間semantic parity decision
- 片方だけが変更された場合のdivergenceとpending parity

semantic parityはbyte identityや直訳一致を意味しない。同じ中心命題、範囲、用語の
対応、図の意味、例、結論を表していることを人間が承認する。

### P45-REQ-003: 成果物ごとの意味的承認記録

検討資料で候補となっていた`cozy.content-alignment.v1`相当の契約を定義する。

記録は少なくとも次を結ぶ。

- accepted Content Core revision identity
- artifact kindとlocale
- artifact authority pathとidentity
- shared media identity
- reviewer identity
- decision: `accepted`, `changes-requested`, `rejected`, `not-applicable`
- decision対象のsource/output identity
- rationaleまたはfeedback reference

file hash、receipt、生成成功は意味的承認を代替しない。Core変更後、artifact authority
変更後、shared infographic変更後は、依存するalignmentをstaleへ遷移させる。

### P45-REQ-004: optional成果物の選択・有効化

`required / optional / disabled`はWorkflow Definition上の静的dispositionとして維持
する。これに加えて、Document Projectごとにoptional Work Productをactiveにする
選択を表す。

最低限、次の利用者操作が必要である。

- 利用可能なoptional成果物を一覧する
- 一つまたは複数をactiveにする
- active optionalをcompletion、readiness、next actionへ参加させる
- active解除時に既存artifact/evidenceを削除せず、非参加として表示する
- profileでdisabledな成果物はactiveにできない

対象にはarticle PDF、summary-slides PDF、infographic PNG、必要に応じたreview
projectionを含む。選択はv2 descriptorの閉じたauthoring contractへ置く。

### P45-REQ-005: infographicの実使用確認

infographic SVG/PNGのauthority、hash、receiptが存在するだけでなく、宣言された
consumerへ可視的に組み込まれていることを確認する。

consumerごとに次を検証する。

- article HTMLで実際に表示される
- article PDFで実際に表示される
- summary slides PDFで指定ページに表示される
- videoでopening/summary等の指定sceneに表示される
- すべて同じaccepted SVG authorityから生成されたrepresentationを使う

receipt bindingだけではvisible inclusionを証明しない。renderer入力、生成物内の
参照、視覚review evidenceの組み合わせで受け入れる。

### P45-REQ-006: `*.dox/`からSimpleModeling.orgへの記事投影

Document Project packageをpublic source treeへそのまま露出させない。明示的な
source projection operationによって、acceptedな`index.dox`と登録済みpublic media
だけを既存のSmartDox article identityへ投影する。

投影契約は次を満たす。

- `<slug>.dox/index.dox`を既存記事URLの`<slug>.html`へ対応付ける
- project directory名からarticle identityを安定して得る
- Content Core、AI dialogue、attempt、review、receipt、raw media、`target/`を除外する
- accepted/currentなpublic sourceだけを投影する
- 通常のDoxsite buildで記事が生成される
- Article 7以前の単一file article behaviorを変更しない
- projectionとpublicationを分離する

Article 8の通常Doxsite buildで記事が生成されなかった事実を、Phase 45 driverの
reproduction caseとして保持する。

### P45-REQ-007: 利用者Dashboard

default dashboardは内部Workflow診断ではなく、利用者の状況把握と行動選択を主目的
にする。次を最初に表示する。

- 現在の制作段階
- 前回から変わったauthorityまたはartifact
- 優先blocker
- 承認待ち
- currentな成果物
- 推奨する次の操作
- 現在eligibleな操作
- optional成果物のactive状態

Workflow DAG、provider、gate、attempt、receipt、source hashはsecondary diagnostic
viewへ移す。dashboard自身は読み取り専用とし、操作実行は明示commandへ渡す。

### P45-REQ-008: Content Core候補レビュー

`core-review.html`はaccepted Coreだけでなく、現在のcandidateと差分を表示する。

レビューは次のloopを支える。

```text
candidate
  -> human feedback
  -> revised candidate
  -> explicit human acceptance
  -> accepted Content Core
```

candidate、rejected、superseded、accepted revisionを区別し、accepted authorityを
暗黙に上書きしない。shared semantic feedbackとartifact-local feedbackを分類する。

### P45-REQ-009: AI対話からCore候補を作る経路

`content-core.compose`のような明示operationを実行した場合、provider bindingに従って
実際にcandidateを生成する。Attempt記録だけで成功扱いにしない。

provenanceは少なくとも次を保持する。

- input source/idea identity
- providerとmodel
- request identity
- response identity
- candidate identity
- command/operation identity
- outcomeとdiagnostic

raw AI responseはauthorityではない。candidateへ構成し、人間の明示承認を経た場合
だけaccepted Coreを変更する。

### P45-REQ-010: Video Review

`video-review.html`はraw Storyboard/YAML表示ではなく、次をscene順に確認できるUIと
する。

- scene purposeとarticle/core mapping
- narration text、speaker、pronunciation metadata
- visible page/diagram/infographic
- scene durationとtransition
- rendered representative frame
- audio/video currentness
- final video evidence

Storyboard authority、Visual Page、audio manifest、render receipt、final video identityが
変わった場合のstale reasonを表示する。

### P45-REQ-011: Phase 41実連携

`explanation-structure-review-html`というWork Product名だけで完了扱いにしない。
Phase 41のaccepted input contractを実際に消費し、article/summary slide/videoの
page flow、page semantics、display structureを確認できるprojectionを生成する。

Phase 41 reviewは記事確認HTMLやvideo reviewを置き換えない。各artifact review内で
利用する共通の説明構造検査として接続する。

## 完成させる利用者ワークフロー

Phase 45で完成させる中心workflowは次である。

```text
アイデア・既存資料
  -> AI対話
  -> Content Core候補
  -> core-review.html
  -> 人間の指摘
  -> 修正候補
  -> 人間の明示承認
  -> 承認済みContent Core
  -> 成果物の選択・有効化
  -> 記事・infographic・PDF・slides・video authority
  -> artifactごとのreview HTML
  -> 人間のcontent-alignment承認
  -> local deliverables
  -> safe public source projection
```

生成、レビュー、承認、delivery、site projection、publicationを別の境界として扱う。
一つのcommandが暗黙に次段階へ進めてはならない。

## 優先順位

Article 8を実用的なdriverとして進めるための優先順位は次とする。

### Priority 1: 利用者workflowを成立させる

1. P45-REQ-008 Content Core候補レビュー
2. P45-REQ-009 AI対話から候補生成
3. P45-REQ-001 記事確認HTML
4. P45-REQ-007 利用者Dashboard
5. P45-REQ-004 optional成果物の選択
6. P45-REQ-006 SimpleModeling.orgへのsafe article projection

### Priority 2: 成果物の正しさを証明する

1. P45-REQ-003 content alignment承認
2. P45-REQ-005 infographicのvisible inclusion
3. P45-REQ-010 Video Review
4. P45-REQ-011 Phase 41実連携

### Priority 3: ローカライズされた一式を完成させる

1. P45-REQ-002 JA/EN linkageとsemantic parity

PriorityはStage実装順を確定するものではない。contract dependencyとExecutable
Specificationの分割によって前後してよいが、P45-06のArticle 8 driver acceptance
では全項目を確認する。

## Phase 45 Stageへの対応

| Requirement ID | Primary Stage | Related Stage |
| --- | --- | --- |
| P45-REQ-001 | P45-03 | P45-01, P45-06 |
| P45-REQ-002 | P45-05 | P45-01, P45-06 |
| P45-REQ-003 | P45-05 | P45-02, P45-03, P45-06 |
| P45-REQ-004 | P45-04 | P45-01, P45-06 |
| P45-REQ-005 | P45-05 | P45-03, P45-06 |
| P45-REQ-006 | P45-06 | P45-01 |
| P45-REQ-007 | P45-04 | P45-06 |
| P45-REQ-008 | P45-02 | P45-03, P45-06 |
| P45-REQ-009 | P45-02 | P45-06 |
| P45-REQ-010 | P45-03 | P45-05, P45-06 |
| P45-REQ-011 | P45-03 | P45-06 |

## 分割後のPhase所有（2026-09-02）

承認済みの分割により、上表のStage IDは維持するが、実行するPhaseは次の順序に
なる。P45-01/P45-02はPhase 45、P45-03/P45-04はPhase 45.1、P45-05/P45-06は
Phase 45.2が唯一の所有者である。したがって、Article 8のlocal driver acceptanceと
SimpleModeling.org更新rootの追加判断はPhase 45.2まで開始しない。

## Article 8開始前のgate

Article 8の意味設計や記事ドラフトは、Phase 45のdriver inputとして作成できる。
ただし、Document Project workflowが完成したと主張する前に、少なくとも次を満たす。

- Core候補を表示し、feedbackと明示承認を一往復できる
- 記事確認HTMLがarticle HTMLやraw Visual Page表示と区別される
- 利用者Dashboardからblocker、承認待ち、次操作を理解できる
- optional成果物をプロジェクト単位で選択できる
- accepted `index.dox`を通常のSimpleModeling.org記事URLへ安全に投影できる
- project内部情報がpublic sourceへ漏れない

Article 8の公開、デプロイ、アップロード、pushは別の明示的な権限を必要とする。

## 互換性と非要件

- `cozy.document-project.v1`をin-placeで拡張しない。
- Phase 42/42.1のaccepted state/evidence kernelを破壊しない。
- Article 7以前をDocument Projectへretrofitしない。
- hash/receiptを意味的承認として扱わない。
- dashboard、article HTML、raw source projectionをartifact reviewの代替にしない。
- 生成AIにContent Coreまたはartifact alignmentを自動承認させない。
- Phase 45にpublication、deployment、upload、pushを含めない。

## 結論

Phase 42/42.1によって、Document Projectの構造と内部状態モデルは成立した。しかし、
当初の中心workflowである次のloopは実運用できない。

```text
アイデア
  -> AI対話
  -> Core候補
  -> 内容確認HTML
  -> 人間の指摘・承認
  -> 承認済みCore
  -> 成果物を選択
  -> 記事・図・PDF・動画
```

Phase 45は、このloopを第一級のpublic contract、review projection、operation、
approval evidence、user dashboard、safe site projectionとして完成させる。Article 8は
そのlocal driverであり、単に`*.dox/`をscaffoldできることや内部stateを表示できる
ことでは受け入れない。
