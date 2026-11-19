# Phase 91 checklist — Document and Section Structure Patterns

status=in-progress
Plan: [Phase 91](phase-91.md)
Owner: Cozy development

## Stage Status

- Current status: IN PROGRESS
- Owner: Cozy development
- Update rule: 実装・検証結果に対応する項目だけ更新します。
- Closure basis: P91-01–P91-06の全受入項目。計画追加は実装完了ではありません。

## P91-01: Contracts and source ownership

P91-01はローカルで受入・コミット済みです。

- [x] 文書構造、節構造、節の意味上の役割、論理構造の責任と参照元がdesign/specに定義されている。
- [x] 共有執筆ルールの採用版と機械可読カタログの対応、カタログの識別・版・選択・更新方法が確定している。
      2026-11-16時点で3文書を含む固定ai-directive改訂606cb56を採用候補として確認する。文書の配置済みと機能の実装・受入を区別する。
- [x] 既存の再帰Coreの後継スキーマと、必要なFormat・Document・CLIの変更範囲、現行版を維持する明示的な受入経路が定義されている。
- [x] パターンの役割、必須性・省略・繰り返し・順序、問い・主張・詳細への型付き参照と、上位文脈の再利用条件が定義されている。
- [x] Phase 37の限定カタログとの名前・版・対象の区別が定義され、既存の同名problem-solution等の意味を変更していない。
- [x] 文法・診断・CLIのspec、責任境界のdesignと、対応するExecutable Specificationの配置が確定している。

## P91-02: Pattern catalogs and recursive Core model

拡張対象は文書5型・節9型です。元の2型・8型の義務を保持します。P91-02〜05はローカルで受入・コミット済みです。

- [x] concept-to-realization、comparison-and-selection、technology-survey-and-catalog と verification-and-evaluation を追加する。

- [x] 文書構造のbackground-first、definition-firstを、章立ての役割付き定義として選択できる。
- [x] 節構造のdefinition、rationale、problem-solution、decomposition、mechanism、comparison、procedure、worked-exampleを選択できる。
- [x] 文書選択と節選択を独立して保持し、各節・小節が親と異なる型を明示できる。
- [x] 問い・背景・主張・詳細と既存Claim・子Stepへの対応を保持し、論理グラフや本文を重複した正本にしていない。
- [x] パターン名・役割名と問いの日本語・英語表現を、論理上の識別子と区別して扱える。
- [x] 型付きモデル・codecが未知の項目・版、重複した識別子、不正な値を構造化診断で拒否する。

## P91-03: Validation and authoring support

- [x] 必須役割の欠落、未知の役割、許可しない繰り返し・順序、参照切れ・誤った参照種別を検出できる。
- [x] 再帰的な節選択と役割割当てが検査され、親子分解と読む順序、因果・根拠の関係を混同しない。
- [x] 省略可能な文脈と明示した上位文脈の再利用を受理し、背景・課題・目的のすべてを毎節で要求しない。
- [x] 既存CLIの正規経路で選択・検査結果を確認でき、旧版へ新項目を混入させない。
- [x] 作成支援が指定パターンの骨組みを出力し、問い・主張・根拠の未執筆状態を明示する。未執筆の内容を推測して充足しない。
- [x] Given / When / Thenと適切なPBTで、型・再帰・参照・順序・省略の正常系と拒否系を検証する。

## P91-04: Native logic-tree HTML

- [x] 節・小節の一覧に、内容の見出しと選択した節構造パターンを表示する。
- [x] 文書全体の構造パターンと、その役割への節の割当てを確認できる。
- [x] 選択した節で、問い、背景・課題・目的、主張、その詳細への展開を役割ごとに確認できる。
- [x] 子の論点が親のどの主張を説明するかを追え、論理関係・読む順序・包含を別々に表示する。
- [x] パターンや役割を見出し文字列・Noteの内容から推測せず、正規の型付き入力から表示する。
- [x] 日本語・英語の表示、選択・参照強調、キーボード操作、自己完結HTMLと既存表示を検証する。

## P91-05: Document correspondence and prose boundary

- [x] 構造上の役割とDocumentの節・ブロック・箇条書き・図表への対応を明示できる。
- [x] 執筆支援に、選択した型・問い・主張・役割ごとの説明を渡せる。
- [x] パターン変更やClaim・子Step変更後の欠落・不整合を、内容の推測や暗黙変換なしで検出できる。
- [x] パターン名や執筆用ラベルを本文・SmartDoxへ自動転記せず、確認用表示と読者向け本文を区別する。
- [x] 機械的な参照・構造検査と、人間・AIによる論理や文章の意味的な判断を区別する。

## P91-06: Integrated acceptance and closure

- [x] 拡張カタログの文書5型・節9型を統合経路で検証する。

- [x] 観測モデルを基にした持ち運び可能なCozy内fixtureで、2種類の文書構造と8種類の節構造、異なる型を持つ親子を検証する。
- [x] 作成支援→CoreとDocumentの執筆→構造検査→HTML確認→本文との対応を、一連の正規経路で実行する。
- [x] パターン切替、必須部分の欠落、上位文脈の再利用、参照変更、日本語・英語と失敗時の診断を検証する。
- [x] 手作業の表示用Noteや装飾見出しへの依存なしに、必要な構造を標準HTMLで確認できる。
- [x] 既存の再帰Core・Document、Phase 37のcatalog/composition/planとnative article経路の回帰を確認する。
- [ ] 対応するfocused/integration検証、独立レビュー、最終Cozy通常検証と所定の完了手順を実施する。後続の完了はPhaseのnative記録への追記とTerminalを参照する。
- [x] 仕様・利用手順・Phase/checklist/Strategyを実際の結果へ更新し、残課題を分離する。

P91-06は `18f14fcec67164c707e85d7f6514aff714ce4eae` で受入済みです。Phase validation 289はcompile/test成功（4,082成功、0失敗、23 canceled、277 suites完了）を報告しました。独立Phase全体レビューは実施され、2026-11-20時点で1件の修復と対象の再レビュー、ローカルnative Terminalが残りました。文書5型と節9型の45組を日英で通し、独立に執筆したSmartDox本文も日英で別経路から検査しました。ライブブラウザでの確認は行っていません。実行結果と制約は[受入記録](../journal/2026/11/2026-11-20-phase91-acceptance.md)、後続の完了状態はPhaseのnative記録を参照してください。
