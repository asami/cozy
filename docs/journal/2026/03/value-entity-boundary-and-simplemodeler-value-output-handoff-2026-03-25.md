# VALUE/ENTITY Boundary + SimpleModeler VALUE Output Handoff

updated_at=2026-03-25
owner=cozy-modeler
status=done

## 目的

`VALUE` と `ENTITY` を別系統のまま維持しつつ、`simple-modeler` で `VALUE` の Scala 出力を有効化する。

- 境界方針:
  - `VALUE` は `ENTITY` に正規化しない
  - ただし内部構造（`SchemaClass` / 属性構造 / constraint）は可能な範囲で共通化する

## 現状

Cozy/Kaleidox 側:

- `VALUE` は `ValueModel`、`ENTITY` は `EntityModel` で別モデルとして保持される。
- Cozy 側テストで `VALUE/ENTITY` 分離は確認済み。
- `modeler-scala-value` は `VALUE-only` CML を受理し、component 非生成は確認済み。

SimpleModeler 側:

- `MValue` / `MDomainValue` はモデル上存在する。
- `MValue` は Scala 生成経路に接続済み。
- `MDomainValue` 向けの VALUE family generator / transformer を追加済み。
- realm dispatch から `MValue` を `src_managed/main/scala/...` に流す経路を接続済み。
- VALUE 生成の回帰テストは SimpleModeler 側で通過済み。
- constraint は typed metadata と runtime validation として生成コードへ反映済み。

## 主作業リポジトリ

- `/Users/asami/src/dev2025/simple-modeler`

## 実装タスク（SimpleModeler）

1. Scala 生成経路で `MValue` を受理
- `MValue` が `None` で捨てられないようにする。
- 既存 `MEntity` / `MPowertype` / `MStateMachine` との分岐方針を揃える。

2. VALUE 専用 family generator/transformer を追加（または既存拡張）
- `MDomainValue` -> Scala case class 出力。
- package は `domain.value` をデフォルトとする（Cozy 側期待に整合）。
- 属性・multiplicity・constraint の出力を `Entity` と同等の品質で扱う。

3. Realm build dispatch に `MValue` を接続
- 既存の realm build で `MValue` が巡回対象になること。
- 出力先は他 family と同様に `src_managed/main/scala/...` 配下。

4. 回帰テスト追加（SimpleModeler）
- `VALUE` 入力で Scala ファイルが生成されること。
- `ENTITY` 生成と混線しないこと。
- constraint（`min/max/pattern/format`）が属性に反映されること。

## 連携タスク（Cozy）

1. Cozy 期待値テストの強化
- 現在は `VALUE-only` の「受理 + component 非生成」まで。
- SimpleModeler 対応後、以下を追加:
  - `domain/value/*.scala` の生成確認
  - `domain/entity/*.scala` へ混入しないこと

2. 文書の status 更新
- `docs/notes/cml-grammar-latest.md`
- `docs/journal/2026/03/literate-model-specification-latest-2026-03-24.md`

## 現在ワークストリームの残項目一覧

1. SimpleModeler VALUE 出力の本実装
- `MValue` 生成経路接続
- VALUE family generator/transformer 実装
- realm dispatch への接続
- SimpleModeler 側回帰テスト

2. Cozy 側 VALUE 出力検証の強化
- `modeler-scala-value` 実行後に `domain/value/*.scala` 生成を検証
- `domain/entity` への混入がないことを検証

3. Literate診断の最小実装（Cozy）
- `explain` 系コマンドを追加
- section を `structural` / `metadata` / `narrative` に分類して出力
- 出力項目は最低限 `section path`, `classified role`, `normalized target`

4. Linkage診断の最小実装（Cozy + CNCF境界）
- `statemachine -> event/subscription -> actioncall` の解決結果を diagnostics 出力
- 失敗時は message 依存ではなく taxonomy/facet ベースで判定可能にする
- 代表ケース（正常/未定義イベント/未束縛action）を test で固定
- 実装済み

5. 診断仕様の文書化と固定
- `docs/notes/cml-grammar-latest.md` に `explain`/linkage diagnostics の契約を追記
- journal に実装範囲・既知制約・検証コマンドを記録
- 現在の実装に合わせて更新済み

## 受け入れ条件

1. `VALUE` は `ENTITY` と別モデル境界を維持する。
2. `simple-modeler` で `VALUE` 定義から Scala ソースが生成される。
3. 生成物 package は `domain.value` デフォルトで安定する。
4. `VALUE` と `ENTITY` が相互に混入しない。
5. Cozy 側 `ModelerGenerationSpec` の VALUE 系テストが pass する。
6. `min/max/pattern/format` の constraint 仕様を生成コードへ反映する。

## 検証コマンド（目安）

SimpleModeler:

- `sbt compile`
- `sbt test`

Cozy:

- `sbt "testOnly cozy.modeler.ModelerGenerationSpec"`

## 追記

- `VALUE` の Scala 出力先は `domain.value` 系で安定している。
- `VALUE` の回帰テストは SimpleModeler 側で通過している。
- constraint は typed metadata と runtime validation として生成コードへ反映済み。
- `explain` と `linkage diagnostics` は Cozy 側で実装済み。

## 非目的（今回やらない）

- `VALUE` を `ENTITY(kind=VALUE)` に正規化すること
- VALUE 専用 runtime service の追加
- CNCF 側の新規 primitive 追加

## 参考

- Cozy Modeler: `/Users/asami/src/dev2025/cozy/src/main/scala/cozy/modeler/Modeler.scala`
- Cozy spec test: `/Users/asami/src/dev2025/cozy/src/test/scala/cozy/modeler/ModelerGenerationSpec.scala`
- SimpleModeler transform gap: `/Users/asami/src/dev2025/simple-modeler/src/main/scala/org/simplemodeling/SimpleModeler/transformer/scala/ScalaModelTransformer.scala`
