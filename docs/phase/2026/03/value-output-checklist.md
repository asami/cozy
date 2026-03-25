# VALUE Output Checklist

updated_at=2026-03-25
owner=cozy-modeler

## Completed

- [x] `MValue` の Scala 生成経路接続
- [x] VALUE 専用 family generator / transformer 追加
- [x] realm dispatch への `MValue` 接続
- [x] VALUE 生成の回帰テスト追加
- [x] 既存 parser 系テストを現行 ScalaTest API に合わせて修正
- [x] `sbt test` で全体確認
- [x] VALUE の Scala 出力先を `domain.value` 系で安定化
- [x] VALUE は ENTITY と別系統のまま Scala 出力する

## Open

- [x] `min/max/pattern/format` の Scala 出力仕様を確定する
- [x] 生成コードへ constraint の専用メタデータを反映する
- [x] Cozy 側で `domain/value/*.scala` の生成を検証する
- [x] Cozy 側で `domain/entity/*.scala` へ混入しないことを検証する
- [x] `explain` 系の診断実装
- [x] linkage diagnostics 実装
- [x] 上記変更の文書化

## Notes

- [x] VALUE の回帰テストは SimpleModeler 側で通過している
- [x] constraint はモデル上で保持され、生成コードへ反映される
- [x] Cozy 側の VALUE 出力期待値は検証済み
- [x] `min/max/pattern/format` は runtime validation として生成される
- [x] `explain` は section path / classified role / normalized target を返す
