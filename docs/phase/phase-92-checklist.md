# Phase 92 checklist — LexiDox Typed Sections and Knowledge Space Integration

status=planned
execution_started=false
Plan: [Phase 92](phase-92.md)
Development item: DEV-041
Owner: Cozy development; SmartDox owns its producer delivery

## Stage Status

- Current status: OPEN
- Owner: Cozy development
- Update rule: Record actual delivery and verification for each criterion.
- Closure basis: All P92-01–P92-04 items. Number allocation and plan registration are not implementation or acceptance.

## P92-01: Contracts and producer ownership

- [ ] 現行SmartDoxの解析・検証・serialization・metadata/RDF経路と、Cozy/SIEの消費境界を確認する。
- [ ] 概念提案の採用改訂、SmartDoxのproducer担当・実装計画・受渡し形式・版と互換性を明示する。
- [ ] 型別の標準節、必須性・cardinality、自由節、未知の大文字節の診断、BoK固有profileをdesign/specへ具体化する。
- [ ] `type`・`space`の構文、SimpleModeling旧表記の認識、既存SmartDox構文と自由節の保持を定義する。
- [ ] Spaceの識別、単一／複数Spaceと概念の対応、Term/Concept IRI、provenance・extensions・constraintsのRDF対応を決定し、未決事項を残したまま出力仕様を変更しない。

## P92-02: Typed section handoff

- [ ] 受入済みSmartDox出力の実際の改訂・形式を用い、Cozyの既存受入経路へ接続する。
- [ ] 型ごとの必須節と未知の大文字節に対するproducer診断を確認する。
- [ ] 小文字・mixed-case・日本語の自由節の内容・順序・リンク・ロケールを保持し、不要な順序制約を加えない。
- [ ] 既存DEFINITION/RELATIONS、aliases、公開metadataとSimpleModeling表記を維持する。
- [ ] Cozyに二重パーサーや欠落metadata/RDFの再構築を追加しない。

## P92-03: BoK and knowledge identities

- [ ] 合意した型・Space情報とTerm/Concept識別を、既存のBoK表示・索引・metadata/RDF連携へ反映する。
- [ ] Spaceをdirectory/categoryから推測せず、同じ語の異なる意味や合意した複数Spaceのケースを検証する。
- [ ] SOURCEとREFERENCES、CONTEXTとSIMPLEMODELINGの意味を保持し、汎用parserに特定BoKの意味を固定しない。
- [ ] RDF/JSON-LD/SKOSの変更が採用済み対応と一致し、既存生成境界・出力配置・SIE受渡しを保持する。

## P92-04: Integrated acceptance and closure

- [ ] 既存形式と型付き形式、日英、Patternと他の採用型、自由節を持つfixtureを正規のSmartDox→Cozy→BoK経路で検証する。
- [ ] 必須節欠落、未知の大文字節、旧表記、リンク、自由節のround-trip、型別profileとIRIの正常・拒否ケースを検証する。
- [ ] 例の節見出し後に空行を保ち、既存語彙の一括書換えなしに利用できることを確認する。
- [ ] producer側の検証・受渡しとCozyのfocused/integration検証、独立レビュー、最終Cozy通常検証を実際の結果で確認する。
- [ ] スキーマ内容やExecutable Knowledgeへ新しい実行権限を与えていない。
- [ ] 仕様・移行手順・Phase/checklist/Strategyを更新し、所定の完了手順と残課題の分離を行う。
