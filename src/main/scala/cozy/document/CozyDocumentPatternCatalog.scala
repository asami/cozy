package cozy.document

/** Immutable authoring catalog; identifiers are independent of localized labels. */
private[cozy] object CozyDocumentPatternCatalog {
  final case class Localized(ja: String, en: String)
  final case class Role(id: String, label: Localized, min: Int, max: Option[Int])
  final case class Pattern(id: String, label: Localized, question: Option[Localized], roles: Vector[Role]) {
    def answerRole: Option[String] = if (question.isDefined) roles.find(_.min > 0).map(_.id) else None
  }
  val id = "cozy.document-section-patterns"
  val version = 1

  private def _role(id: String, ja: String, en: String, min: Int = 1, max: Option[Int] = Some(1)): Role =
    Role(id, Localized(ja, en), min, max)
  private def _one(id: String, ja: String, en: String): Role = _role(id, ja, en)
  private def _optional(id: String, ja: String, en: String): Role = _role(id, ja, en, 0)
  private def _many(id: String, ja: String, en: String): Role = _role(id, ja, en, 1, None)
  private def _doc(id: String, ja: String, en: String, roles: Role*): Pattern = Pattern(id, Localized(ja, en), None, roles.toVector)
  private def _section(id: String, ja: String, en: String, qja: String, qen: String, roles: Role*): Pattern =
    Pattern(id, Localized(ja, en), Some(Localized(qja, qen)), roles.toVector)
  private val _lead = _one("lead", "リード", "Lead")
  private val _definition = _one("definition", "定義", "Definition")
  private val _composition = _one("composition", "構成", "Composition")
  private val _usage = _one("usage", "使い方", "Usage")
  private val _example = _optional("example", "具体例", "Example")
  private val _conclusion = _one("conclusion", "結論", "Conclusion")
  private val _context = _optional("context", "文脈", "Context")
  private val _purpose = _optional("purpose", "目的", "Purpose")
  val documents: Vector[Pattern] = Vector(
    _doc("background-first", "背景導入型", "Background First", _lead, _one("introduction", "導入", "Introduction"), _definition, _composition, _usage, _example, _conclusion),
    _doc("definition-first", "定義・課題対応型", "Definition First", _lead, _definition, _one("purpose", "目的", "Purpose"), _optional("application-framework", "活用の枠組み", "Application framework"), _one("issues", "対象となる課題", "Issues"), _composition, _one("responses", "課題への対応", "Responses"), _usage, _example, _conclusion),
    _doc("concept-to-realization", "概念・実現解説型", "Concept to Realization", _lead, _one("background-purpose", "背景と目的", "Background and purpose"), _one("concepts-overview", "概念と全体像", "Concepts and overview"), _many("design-concerns", "主要な設計上の関心事", "Principal design concerns"), _one("design-to-execution", "設計から実行へ", "From design to execution"), _one("worked-example", "具体例", "Worked example"), _one("summary", "まとめ", "Summary")),
    _doc("comparison-and-selection", "比較・選択型", "Comparison and Selection", _lead, _one("decision-context", "判断の文脈と目的", "Decision context and purpose"), _one("comparison-basis", "共通の比較基盤", "Common comparison basis"), _many("alternatives", "各選択肢の仕組み", "How each alternative works"), _one("worked-comparison", "具体的な比較", "Worked comparison"), _one("findings-tradeoffs", "比較結果とトレードオフ", "Findings and tradeoffs"), _one("selection-conditions", "選択条件", "Selection conditions"), _one("summary", "まとめ", "Summary")),
    _doc("technology-survey-and-catalog", "技術調査・カタログ型", "Technology Survey and Catalog", _lead, _one("background-scope", "背景と対象範囲", "Background and scope"), _one("classification-relationships", "分類と関係", "Classification and relationships"), _one("technology-index", "技術索引", "Technology index"), _many("catalog-entries", "カタログ項目", "Catalog entries"), _optional("combinations-outlook", "組合せと展望", "Combinations and outlook"))
  )
  val sections: Vector[Pattern] = Vector(
    _section("definition", "定義型", "Definition", "それは何か。", "What is it?", _context, _definition, _many("properties", "性質・範囲・境界", "Properties, scope and boundary")),
    _section("rationale", "必要性型", "Rationale", "なぜ必要か。", "Why is it needed?", _context, _one("necessity", "必要性", "Necessity"), _many("reasons", "理由", "Reasons"), _optional("conditions", "条件", "Conditions")),
    _section("problem-solution", "課題対応型", "Problem–Solution", "課題にどう対応するか。", "How can we address the problem?", _optional("problem", "課題", "Problem"), _one("response", "対応方針", "Response"), _many("cause-measure", "原因と対策", "Cause and measure"), _many("effect-condition", "効果と条件", "Effect and condition")),
    _section("decomposition", "構成分解型", "Decomposition", "何から成るか。", "What are its parts?", _purpose, _one("overview", "全体像", "Overview"), _many("components", "構成要素", "Components")),
    _section("mechanism", "仕組み型", "Mechanism", "どう働くか。", "How does it work?", _purpose, _one("principle", "仕組みの要点", "Principle"), _many("operation", "作用", "Operation"), _many("result-condition", "結果と条件", "Result and condition")),
    _section("comparison", "比較選択型", "Comparison", "選択肢をどう比較し、どの条件で選ぶか。", "How do the options compare? If a choice is needed, which option fits which conditions?", _purpose, _conclusion, _many("axes-evidence", "比較軸と根拠", "Axes and evidence"), _optional("selection-condition", "選択条件", "Selection condition")),
    _section("procedure", "実施方法型", "Procedure", "どう実施するか。", "How do we do it?", _optional("goal", "到達目標", "Goal"), _one("method", "方法", "Method"), _optional("prerequisites", "前提", "Prerequisites"), _many("steps", "手順", "Steps"), _one("verification", "結果の確認", "Verification")),
    _section("worked-example", "具体例型", "Worked Example", "実際にはどうなるか。", "What happens in practice?", _context, _one("example-point", "事例の要点", "Example point"), _optional("conditions", "条件", "Conditions"), _many("operation", "作用", "Operation"), _one("result", "結果", "Result"), _one("correspondence", "一般説明との対応", "Correspondence")),
    _section("verification-and-evaluation", "検証・評価型", "Verification and Evaluation", "根拠は主張について何をどの範囲で示すか。", "What does the evidence establish about the claim, and within which limits?", _one("judgment", "判断", "Judgment"), _one("claim-criteria", "主張と評価基準", "Claim and criteria"), _one("subject-conditions", "対象と条件", "Subject and conditions"), _many("evidence-results", "根拠と結果", "Evidence and results"), _many("interpretation", "解釈", "Interpretation"), _one("judgment-limits", "判断と限界", "Judgment and limits"))
  )
}
