# Document and Section Structure Patterns — Planned Capability

Status: non-normative implementation proposal
Date: 2026-11-15
Plan: [Phase 91](../phase/phase-91.md)

## 目的

文書全体と各節・小節で選択した構造パターンを、CozyのCore作成、構造検査、ロジックツリー表示と本文の執筆に共通して使います。現在の観測モデルでは、節別の別資料と確認用DocumentのNoteを用いてこの関係を表示しています。これをCozyの正規の型付きモデルへ取り込みます。

この資料は実装予定の機能と境界を整理するものです。P91-01で設計上の決定をdesignへ、スキーマ・文法・CLI・診断等の契約をspecへ具体化します。ここに書いた概念名は、新しいDSL項目やCLIが既に存在することを意味しません。

## 既存機能と追加する境界

Phase 37には、Subject PatternとExplanation Patternを分けたカタログ、明示した役割を持つComposition、決定的なPlanへの展開があります。既存v1の受入カタログはsoftware-productと製品説明等の限定した型であり、任意の技術文書の2型・8型を追加登録するだけでは使えません。CompositionのStep列も平坦で、一つの選択を再帰Coreの各節へ割り当てるモデルではありません。

現在の再帰Coreは、StepのsemanticRole、Claim、局所Structure、子Step間のFlowと子Stepを持ちます。文書全体の構造選択と、各Stepの説明パターンを保持する契約はありません。共通の意味はCore、言語別の表現と本文はFormat・Document、確認HTMLは派生表示という境界を維持して拡張します。

## 三つの構造

| 対象 | 決めること | 初期対象 |
| --- | --- | --- |
| 文書構造パターン | 文書全体の論点・役割と配置。 | 背景導入型background-first、定義・課題対応型definition-first。 |
| 節構造パターン | 各節・小節の問い、主張、説明の展開。 | 定義型、必要性型、課題対応型、構成分解型、仕組み型、比較選択型、実施方法型、具体例型。 |
| 論理構造 | 何が主張を支え、何が何を構成し、どの条件で作用するか。 | 既存の型付きRelation・Structureと、必要な参照。 |

節のsemanticRoleは、その節の文書内での役割です。節構造パターンは説明の組み立てであり、Structureのmappingやcausal-chainは論理構造です。これらを別々に保持します。

## カタログとモデルの方向

文書構造と節構造を、種別・識別子・版が分かるカタログで選択します。共有執筆ルールを意味の基準とし、Cozyは機械可読定義の受入・検査・表示を所有します。Markdown本文を実行時に解析してパターンを推測する方式にはしません。

初期の節構造IDはdefinition、rationale、problem-solution、decomposition、mechanism、comparison、procedure、worked-exampleです。各定義には、問いの種類、役割、必要な順序、必須性・省略・繰り返しの条件を持たせます。役割の完全な値集合と参照の文法はspecで確定します。

Phase 37のカタログ・役割・明示選択の設計を再利用します。同名のproblem-solutionも、既存Phase 37の2役割の定義を暗黙に今回の定義へ変更しません。カタログ・対象種別・版によって識別し、共有可能な型と契約をP91-01で整理します。旧契約を維持したまま新しい範囲を明示的に受け付けます。

推奨案は、既存のcozy.content-core.logic-tree.v1の後継版へ、文書構造と各Stepの節構造選択を正式に追加することです。歴史的なcozy.content-core.v1やpresentation-semantics.v2とは別の契約です。正確なschema名、必要なFormat・Documentの版、CLIでの選択と拒否条件はP91-01で確定します。

文書構造は役割と節のIDを対応付けます。節構造は問い、必要な背景・課題・目的、主張、そのブレイクダウンを、既存のClaimと子Stepへ対応付けます。意味や論理グラフを別の正本へコピーせず、安定した参照を使います。言語別の問い・見出し・本文と、表示するパターン名・役割名は、Coreの識別子と区別して扱います。

## 再帰的な適用

各節・小節では「必要な背景・課題・目的など → 主張 → そのブレイクダウン」のつながりを持たせます。親と子で異なる型を明示して選べます。例えば構成分解型の親の下に、定義型・仕組み型・課題対応型の子を配置できます。

背景・課題・目的を毎節ですべて要求せず、既に共有した文脈は明示した参照で再利用できるようにします。必須主張と、型ごとの省略・繰り返し・順序を検査し、読む順序や文の位置から役割を推測しません。段落を含めどこまでを独立した適用単位とするか、役割を一つの記述へまとめる場合の参照方法は、P91-01で定義します。

## 作成・検査・表示・本文への受渡し

作成支援では、ユーザーまたはAIが指定したパターンから役割付きの骨組みを用意します。内容が未執筆の箇所は未執筆として扱い、主張や根拠を推測して埋めません。骨組みの状態と、正規の構造検査を通過した状態を区別します。

構造検査では、パターンと版、役割、必須性、順序、繰り返し、参照種別と解決、親子との対応を検査します。因果や根拠の意味的な妥当性、比較軸の有効性、文章の読みやすさは人間・AIが判断します。参照が解決したことを、主張の正しさの証明とは扱いません。

ロジックツリーHTMLでは、文書構造の選択と役割割当て、節・小節の選択パターン、問い、背景・課題・目的、主張と詳細を確認できます。親の主張から子の説明への展開、包含、Flow、局所の論理関係を区別します。表示する型を見出しの装飾文字列やNoteの文面から抽出しません。

本文の執筆には同じ問い・主張・役割ごとの説明を渡します。Documentの節・ブロック・箇条書き・図表への対応を持たせ、執筆用のパターン名や役割ラベルを、記事本文・SmartDoxへ自動で流し込みません。独立した二つの本文正本を作らず、確認表示と読者向け文章を同じCoreに対応させます。

## 適用確認と範囲

観測モデルを基にしたCozy内の持ち運び可能なfixtureで、異なる型を持つ親子、2種類の文書構造、8種類の節構造を検証します。正常系に加え、未執筆、役割欠落、参照変更、上位文脈の再利用、型の切替、日本語・英語を扱います。現行Core・Document・Phase 37の契約とnative article経路の回帰も確認します。

既存Phaseの優先順位、現在の確認HTMLの受入、Phase 90の外部WorkRoot計画は変更しません。新しいスライド・動画機能、一般的な自動文章生成、意味的な自動合否判定、公開・配備、一括移行は今回の範囲外です。

## 参照

- [Phase 37の既存設計](../design/explanation-composition.md)
- [説明Compositionの既存仕様](../spec/explanation-composition.md)
- [再帰Coreの既存仕様](../spec/document-project-recursive-content-core.md)
- [Documentの既存DSL](../spec/document-project-document-and-summary-description-dsl.md)
- [確認HTMLの既存仕様](../spec/document-project-document-confirmation-renderer-v2.md)
- 共有正本：ai-directiveのcore/technical-writing.md、core/technical-writing-templates.md、core/technical-writing-patterns.md。今回採用した最新改訂はstandalone checkoutにあり、Cozyのsubmoduleへの配布は未実施です。採用する改訂と同期方法をP91-01で確定します。
