# Phase 92 checklist — LexiDox Typed Sections and Knowledge Space Integration

status=in-progress
execution_started=true
Plan: [Phase 92](phase-92.md)
Development item: DEV-041
Owner: Cozy development; SmartDox owns its producer delivery

## Stage Status

- Current status: OPEN
- Owner: Cozy development
- Update rule: Record actual delivery and verification for each criterion.
- Closure basis: All P92-01–P92-04 items. Number allocation and plan registration are not implementation or acceptance.

## P92-01: Contracts and producer ownership

Evidence: [decision record](../design/lexidox-producer-consumer-boundary.md) and [handoff](../spec/lexidox-knowledge-space-handoff.md) adopt SmartDox design contract `7207fa191cd32bd383684ca4b278ff402ddbc096`, the committed conceptual proposal and separately pinned joint plan. Built producer delivery and P93-01 presentation decisions remain outstanding. P92-01 entries record the document decisions and comparison; workflow review remains pending. Contract documentation does not establish implementation acceptance.

- [x] 現行SmartDoxの解析・検証・serialization・metadata/RDF経路と、Cozy/SIEの消費境界を確認する。
- [x] 概念提案の採用改訂、SmartDoxのproducer担当・実装計画・受渡し形式・版と互換性を明示する。
- [x] 型別の標準節、必須性・cardinality、自由節、未知の大文字節の診断、BoK固有profileをdesign/specへ具体化する。
- [x] `concept_type`・`knowledge_form`・`space`の構文、SimpleModeling旧表記の認識、既存SmartDox構文と自由節の保持を定義する。
- [x] Spaceの識別、単一／複数Spaceと概念の対応、Term/Concept IRI、provenance・extensions・constraintsのRDF対応を決定し、未決事項を残したまま出力仕様を変更しない。

- [x] [共通計画](../notes/lexidox-phase92-93-integration.md)と正本LX-J01–LX-J08をP93-01と照合し、同じproducer文法・schema・metadata契約の基線／互換拡張、採用改訂と所有者を一つのdesign/spec決定記録へ結び付ける。
- [x] Space／Term／Conceptの解決と表示名の選択順序、概念分類・知識記述形式・表示役割の独立性を両Phaseで一致させる。

## P92-02: Typed section handoff

Producer delivery `f8a3847c8717ebe5fe479d79553e741c5e6604cc` and its physical/virtual fixture outputs have been received. The handoff implementation and executable specifications await workflow validation and review; unchecked entries are not acceptance claims. See the [handoff](../spec/lexidox-knowledge-space-handoff.md) for runtime selection and artifact identity.

- [ ] 受入済みSmartDox出力の実際の改訂・形式を用い、Cozyの既存受入経路へ接続する。
- [ ] 記述形式ごとの必須節と未知の大文字節に対するproducer診断を確認する。
- [ ] 小文字・mixed-case・日本語の自由節の内容・順序・リンク・ロケールを保持し、不要な順序制約を加えない。
- [ ] 既存DEFINITION/RELATIONS、aliases、公開metadataとSimpleModeling表記を維持する。
- [ ] Cozyに二重パーサーや欠落metadata/RDFの再構築を追加しない。

## P92-03: BoK and knowledge identities

- [ ] 合意した型・Space情報とTerm/Concept識別を、既存のBoK表示・索引・metadata/RDF連携へ反映する。
- [ ] Spaceをdirectory/categoryから推測せず、同じ語の異なる意味や合意した複数Spaceのケースを検証する。
- [ ] SOURCEとREFERENCES、CONTEXTとSIMPLEMODELINGの意味を保持し、汎用parserに特定BoKの意味を固定しない。
- [ ] RDF/JSON-LD/SKOSの変更が採用済み対応と一致し、既存生成境界・出力配置・SIE受渡しを保持する。

- [ ] 表示側へ渡すTerm／Concept／Space・ページ／参照先、正規ラベル・定義・関係・出典を同じproducer契約から取り出せることを確認し、表示profileを知識分類やIRIへ転用しない。

## P92-04: Integrated acceptance and closure

- [ ] 既存形式と型付き形式、日英、Patternと他の採用型、自由節を持つfixtureを正規のSmartDox→Cozy→BoK経路で検証する。
- [ ] 必須節欠落、未知の大文字節、旧表記、リンク、自由節のround-trip、型別profileとIRIの正常・拒否ケースを検証する。
- [ ] 販売・文学史に「作品」の別ページを生成し、横断検索にSpace名付きで両方を表示する。別概念と明示的な同一概念共有の各fixtureでページ・Concept IRIの対応を確認する。
- [ ] 文書Space内の一意参照、参照側のSpace指定による上書き、Space指定なしの全体一意参照を検証する。複数候補では原稿位置・語・候補ページ・Spaceを含む曖昧エラー、ゼロ件では未定義エラーとし、別Spaceへの誤リンクがないことを確認する。
- [ ] `concept_type=process` と `knowledge_form=pattern` の原稿がProcess索引に載り、SOLUTION欠落ではPatternの検証が失敗する。旧 `term_type=process` だけの原稿は従来の分類・節規則で扱う。
- [ ] 例の節見出し後に空行を保ち、既存語彙の一括書換えなしに利用できることを確認する。
- [ ] producer側の検証・受渡しとCozyのfocused/integration検証、独立レビュー、最終Cozy通常検証を実際の結果で確認する。
- [ ] スキーマ内容やExecutable Knowledgeへ新しい実行権限を与えていない。
- [ ] Phase 93へ引き渡す共通の実LexiDox fixture群と、採用済みの文法／metadata基線・互換拡張境界を特定し、基線の知識受入と将来の表示受入を区別する。
- [ ] 仕様・移行手順・Phase/checklist/Strategyを更新し、所定の完了手順と残課題の分離を行う。
