package cozy.document

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/*
 * @since   Sep. 12, 2026
 * @version Sep. 12, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozySummaryConfirmationProjectionV2 {
  final case class Chrome(
    statusHeading: String,
    selectedSourcesCurrentAndAdmitted: String,
    noUnresolvedSelectedReferences: String,
    navigationHeading: String,
    semanticPanelHeading: String,
    emphasisHeading: String,
    retainedPointsHeading: String,
    diagramHeading: String,
    diagramItemsHeading: String,
    diagramEdgesHeading: String,
    emptyDiagramMessage: String,
    sourcesHeading: String,
    omissionsHeading: String,
    rationaleHeading: String,
    identitiesHeading: String,
    coreIdentityLabel: String,
    documentIdentityLabel: String,
    summaryIdentityLabel: String,
    outputIdentityLabel: String
  )
  final case class Vocabulary(
    chrome: Chrome,
    sourceCategories: Map[String, String],
    diagramItemKinds: Map[String, String],
    relationTypes: Map[String, String],
    flowTypes: Map[String, String],
    documentTargetKinds: Map[String, String],
    omissionDispositions: Map[String, String],
    directions: Map[String, String]
  )
  final case class Rendered(html: String, identity: String)
  final case class ProjectionFault(code: String, reason: String)
    extends IllegalArgumentException(s"$code reason=$reason")

  def render(validated: CozyDocumentDescriptionV2.ValidatedSummary, vocabulary: Vocabulary): Rendered = {
    _validate_summary(validated)
    _validate_vocabulary(validated, vocabulary)
    val draft = _page(validated, vocabulary, "")
    val identity = _identity(draft)
    Rendered(_page(validated, vocabulary, identity), identity)
  }

  private def _page(validated: CozyDocumentDescriptionV2.ValidatedSummary, vocabulary: Vocabulary, outputidentity: String): String = {
    val summary = validated.description.summary
    val labels = validated.document.description.labels.steps.map(value => value.stepRef -> value.text).toMap
    val nodelabels = validated.document.description.labels.nodes.map(value => value.nodeRef -> value.text).toMap
    val selected = summary.units.head
    val navigation = summary.units.map(unit => _navigation_item(unit, selected.id)).mkString("<ol class=\"unit-list\">", "", "</ol>")
    val slides = summary.units.map(unit => _semantic_panel(unit, selected.id, vocabulary)).mkString
    val evidence = summary.units.map(unit => _evidence_panel(unit, selected.id, validated, labels, nodelabels, vocabulary)).mkString
    val chrome = vocabulary.chrome
    s"""<!doctype html>
       |<html lang="${_html(validated.description.locale)}"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>${_html(summary.title)}</title><style>
       |:root{color-scheme:light;font-family:system-ui,sans-serif;color:#172033;background:#edf2f7;line-height:1.55}body{margin:0}.workspace{max-width:88rem;margin:0 auto;padding:clamp(1rem,3vw,2.5rem)}.workspace-header{margin-bottom:1rem}.workspace-header h1{margin:.1rem 0}.status{border-left:.35rem solid #2563a8;background:#fff;padding:.75rem 1rem;margin:1rem 0}.status h2,.panel h2,.panel h3{margin:.1rem 0 .55rem}.status ul{margin:.25rem 0;padding-left:1.3rem}.workspace-grid{display:grid;grid-template-columns:minmax(15rem,.8fr) minmax(24rem,1.4fr);gap:1rem;align-items:start}.panel{background:#fff;border:1px solid #cbd5e1;border-radius:.45rem;padding:1rem}.unit-list{list-style:none;padding:0;margin:0}.unit-list li+li{margin-top:.5rem}.unit-control{width:100%;text-align:left;background:#f8fafc;border:1px solid #94a3b8;border-radius:.35rem;padding:.55rem .7rem;color:inherit;cursor:pointer}.unit-control[aria-pressed=\"true\"]{background:#dbeafe;border-color:#2563a8;font-weight:700}.unit-control:focus-visible{outline:.22rem solid #e05a00;outline-offset:.18rem}.semantic-panel{aspect-ratio:16 / 9;display:flex;flex-direction:column;justify-content:center;background:linear-gradient(135deg,#f8fafc,#dbeafe);border:1px solid #94a3b8;border-radius:.45rem;padding:clamp(1rem,4vw,2.5rem)}.semantic-panel[data-emphasis=\"primary\"]{border-left:.45rem solid #2563a8}.semantic-panel[data-emphasis=\"supporting\"]{border-left:.45rem solid #64748b}.semantic-panel[data-emphasis=\"conclusion\"]{border-left:.45rem solid #0f766e}.evidence-panel{margin-top:1rem}.detail-panel[hidden]{display:none}.source-groups,.retained-points,.diagram-items,.diagram-edges,.omission-list{padding-left:1.25rem}.source-groups>li,.retained-points>li,.diagram-items>li,.diagram-edges>li,.omission-list>li{margin:.5rem 0}.source-values{padding-left:1.25rem}.identity{font-family:ui-monospace,SFMono-Regular,monospace;font-size:.82em;color:#475569;overflow-wrap:anywhere}.type-wording{color:#334e68;font-weight:600}.evidence-panel h4{margin:1rem 0 .3rem}.secondary{margin-top:1rem;color:#475569}.secondary dl{display:grid;grid-template-columns:max-content 1fr;gap:.3rem .75rem}.secondary dd{margin:0;overflow-wrap:anywhere}@media (max-width:56rem){.workspace-grid{grid-template-columns:1fr}}@media (max-width:38rem){.workspace{padding:.75rem}.panel{padding:.8rem}.semantic-panel{min-height:0}}@media print{.workspace{max-width:none}.unit-control{border-color:#64748b}.detail-panel[hidden]{display:block}}
       |</style></head><body><main class="workspace" data-summary-id="${_html(validated.description.id)}" data-document-id="${_html(validated.document.description.id)}" data-core-id="${_html(validated.document.core.core.id)}" data-output-identity="${_html(outputidentity)}"><header class="workspace-header"><h1>${_html(summary.title)}</h1></header><section id="status-region" class="status" aria-label="${_html(chrome.statusHeading)}"><h2>${_html(chrome.statusHeading)}</h2><ul><li>${_html(chrome.selectedSourcesCurrentAndAdmitted)}</li><li>${_html(chrome.noUnresolvedSelectedReferences)}</li></ul></section><div class="workspace-grid"><nav id="summary-navigation-region" class="panel" aria-label="${_html(chrome.navigationHeading)}"><h2>${_html(chrome.navigationHeading)}</h2>$navigation</nav><section id="summary-review-region" aria-describedby="status-region"><section id="semantic-panel-region" class="panel" aria-label="${_html(chrome.semanticPanelHeading)}" aria-live="polite"><h2>${_html(chrome.semanticPanelHeading)}</h2>$slides</section><section id="summary-evidence-region" class="panel evidence-panel" aria-label="${_html(chrome.sourcesHeading)}" aria-live="polite">$evidence</section></section></div><details class="secondary"><summary>${_html(chrome.identitiesHeading)}</summary><dl><dt>${_html(chrome.coreIdentityLabel)}</dt><dd>${_html(validated.document.coreIdentity)}</dd><dt>${_html(chrome.documentIdentityLabel)}</dt><dd>${_html(validated.document.documentIdentity)}</dd><dt>${_html(chrome.summaryIdentityLabel)}</dt><dd>${_html(validated.summaryIdentity)}</dd><dt>${_html(chrome.outputIdentityLabel)}</dt><dd data-output-identity="${_html(outputidentity)}">${_html(outputidentity)}</dd></dl></details></main><script>
       |(function(){const controls=Array.from(document.querySelectorAll('[data-summary-unit-control]'));const panels=Array.from(document.querySelectorAll('[data-summary-unit-panel]'));const select=control=>{const id=control.getAttribute('data-summary-unit-id');controls.forEach(item=>item.setAttribute('aria-pressed',String(item===control)));panels.forEach(panel=>panel.hidden=panel.getAttribute('data-summary-unit-panel')!==id);};controls.forEach(control=>control.addEventListener('click',()=>select(control)));select(controls.find(control=>control.getAttribute('aria-pressed')==='true')||controls[0]);}());
       |</script></body></html>""".stripMargin
  }

  private def _navigation_item(unit: CozyDocumentDescriptionV2.SummaryUnit, selectedid: String): String = {
    val pressed = unit.id == selectedid
    s"""<li><button type="button" class="unit-control" data-summary-unit-control="true" data-summary-unit-id="${_html(unit.id)}" aria-pressed="$pressed" aria-controls="semantic-${_html(unit.id)} evidence-${_html(unit.id)}">${_html(unit.navigationLabel)} <span class="identity">${_html(unit.id)}</span></button></li>"""
  }

  private def _semantic_panel(unit: CozyDocumentDescriptionV2.SummaryUnit, selectedid: String, vocabulary: Vocabulary): String = {
    val hidden = if (unit.id == selectedid) "" else " hidden"
    val chrome = vocabulary.chrome
    s"""<article id="semantic-${_html(unit.id)}" class="detail-panel semantic-panel" data-summary-unit-panel="${_html(unit.id)}" data-summary-slide-panel="true" data-emphasis="${_html(unit.emphasis)}"$hidden><p class="type-wording">${_html(chrome.emphasisHeading)}: ${_html(unit.emphasis)}</p><h3>${_html(unit.heading)}</h3><p>${_html(unit.message)}</p></article>"""
  }

  private def _evidence_panel(
    unit: CozyDocumentDescriptionV2.SummaryUnit,
    selectedid: String,
    validated: CozyDocumentDescriptionV2.ValidatedSummary,
    labels: Map[String, String],
    nodelabels: Map[String, String],
    vocabulary: Vocabulary
  ): String = {
    val hidden = if (unit.id == selectedid) "" else " hidden"
    val chrome = vocabulary.chrome
    val sources = _references(unit.coreRefs, labels, nodelabels, vocabulary)
    val points = unit.retainedPoints.map(point =>
      s"""<li data-retained-point-id="${_html(point.id)}"><p>${_html(point.text)} <span class="identity">${_html(point.id)}</span></p>${_references(point.coreRefs, labels, nodelabels, vocabulary)}</li>"""
    ).mkString("<ol class=\"retained-points\">", "", "</ol>")
    val diagram = unit.diagram.map(value => _diagram(value, validated.document.core, labels, nodelabels, vocabulary)).getOrElse(s"""<p data-empty-diagram="true">${_html(chrome.emptyDiagramMessage)}</p>""")
    val omissions = unit.omissions.map(omission =>
      s"""<li data-omission-id="${_html(omission.id)}" data-document-kind="${_html(omission.documentKind)}" data-document-ref="${_html(omission.documentRef)}" data-omission-disposition="${_html(omission.disposition)}"><span class="type-wording">${_html(vocabulary.documentTargetKinds(omission.documentKind))}</span> <span class="identity">${_html(omission.documentRef)}</span> · <span class="type-wording">${_html(vocabulary.omissionDispositions(omission.disposition))}</span><p>${_html(chrome.rationaleHeading)}: ${_html(omission.rationale)}</p></li>"""
    ).mkString("<ol class=\"omission-list\">", "", "</ol>")
    s"""<article id="evidence-${_html(unit.id)}" class="detail-panel" data-summary-unit-panel="${_html(unit.id)}" data-summary-evidence-panel="true"$hidden><h2>${_html(unit.heading)} <span class="identity">${_html(unit.id)}</span></h2><h3>${_html(chrome.sourcesHeading)}</h3>$sources<h3>${_html(chrome.retainedPointsHeading)}</h3>$points<h3>${_html(chrome.diagramHeading)}</h3>$diagram<h3>${_html(chrome.omissionsHeading)}</h3>$omissions</article>"""
  }

  private def _references(
    refs: CozyDocumentDescriptionV2.References,
    labels: Map[String, String],
    nodelabels: Map[String, String],
    vocabulary: Vocabulary
  ): String = {
    val values = Vector(
      "steps" -> refs.steps.map(value => s"""${_html(labels(value))} <span class="identity">${_html(value)}</span>"""),
      "claims" -> refs.claims.map(value => s"""<span class="identity">${_html(value)}</span>"""),
      "nodes" -> refs.nodes.map(value => s"""${_html(nodelabels(value))} <span class="identity">${_html(value)}</span>"""),
      "relations" -> refs.relations.map(value => s"""<span class="identity">${_html(value)}</span>"""),
      "flows" -> refs.flows.map(value => s"""<span class="identity">${_html(value)}</span>""")
    )
    values.map { case (kind, references) =>
      s"""<li data-source-category="${_html(kind)}"><span class="type-wording">${_html(vocabulary.sourceCategories(kind))}</span><ul class="source-values">${references.map(value => s"<li>$value</li>").mkString}</ul></li>"""
    }.mkString("<ul class=\"source-groups\">", "", "</ul>")
  }

  private def _diagram(
    diagram: CozyDocumentDescriptionV2.Diagram,
    core: CozyDocumentLogicTree.ValidatedCore,
    labels: Map[String, String],
    nodelabels: Map[String, String],
    vocabulary: Vocabulary
  ): String = {
    val chrome = vocabulary.chrome
    val items = diagram.items.map(item => {
      val label = item.kind match {
        case "step" => labels(item.ref)
        case "node" => nodelabels(item.ref)
      }
      s"""<li data-diagram-item-id="${_html(item.id)}" data-diagram-item-kind="${_html(item.kind)}" data-core-ref="${_html(item.ref)}"><span class="type-wording">${_html(vocabulary.diagramItemKinds(item.kind))}</span> ${_html(label)} <span class="identity">${_html(item.ref)}</span> <span class="identity">${_html(item.id)}</span></li>"""
    }).mkString("<ul class=\"diagram-items\">", "", "</ul>")
    val edges = diagram.edges.map(edge => _diagram_edge(edge, core, labels, nodelabels, vocabulary)).mkString("<ul class=\"diagram-edges\">", "", "</ul>")
    s"<h4>${_html(chrome.diagramItemsHeading)}</h4>$items<h4>${_html(chrome.diagramEdgesHeading)}</h4>$edges"
  }

  private def _diagram_edge(
    edge: CozyDocumentDescriptionV2.DiagramEdge,
    core: CozyDocumentLogicTree.ValidatedCore,
    labels: Map[String, String],
    nodelabels: Map[String, String],
    vocabulary: Vocabulary
  ): String = {
    val endpoints = edge.kind match {
      case "relation" =>
        val relation = core.relationsById(edge.ref)
        val values = if (edge.direction == "forward") Vector(relation.from, relation.to) else Vector(relation.to, relation.from)
        val wording = vocabulary.relationTypes(relation.relationType)
        s"""${_html(nodelabels(values.head))} <span class="identity">${_html(values.head)}</span> <span class="type-wording">${_html(wording)}</span> ${_html(nodelabels(values(1)))} <span class="identity">${_html(values(1))}</span>"""
      case "flow-transition" =>
        val source = _transition_sources(core)(edge.ref)
        val values = if (edge.direction == "forward") Vector(source.transition.fromStepId, source.transition.toStepId) else Vector(source.transition.toStepId, source.transition.fromStepId)
        val wording = vocabulary.flowTypes(source.transition.relationType)
        s"""${_html(labels(values.head))} <span class="identity">${_html(values.head)}</span> <span class="type-wording">${_html(wording)}</span> ${_html(labels(values(1)))} <span class="identity">${_html(values(1))}</span>"""
    }
    s"""<li data-diagram-edge-id="${_html(edge.id)}" data-diagram-edge-kind="${_html(edge.kind)}" data-core-ref="${_html(edge.ref)}" data-direction="${_html(edge.direction)}"><span class="identity">${_html(edge.id)}</span> · <span class="type-wording">${_html(if (edge.kind == "relation") vocabulary.sourceCategories("relations") else vocabulary.sourceCategories("flows"))}</span> <span class="identity">${_html(edge.ref)}</span> · <span class="type-wording">${_html(vocabulary.directions(edge.direction))}</span><div>$endpoints</div></li>"""
  }

  private def _transition_sources(core: CozyDocumentLogicTree.ValidatedCore): Map[String, TransitionSource] =
    core.depthFirstSteps.flatMap(step => step.flow.transitions.map(transition => transition.id -> TransitionSource(step.flow.id, transition))).toMap

  private def _validate_summary(validated: CozyDocumentDescriptionV2.ValidatedSummary): Unit =
    if (validated.description.summary.units.isEmpty)
      _fail("SUMMARY_CONFIRMATION_V2_UNITS", "admitted Summary must contain at least one unit")

  private def _validate_vocabulary(validated: CozyDocumentDescriptionV2.ValidatedSummary, vocabulary: Vocabulary): Unit = {
    val chrome = vocabulary.chrome
    _required(Vector(
      chrome.statusHeading, chrome.selectedSourcesCurrentAndAdmitted, chrome.noUnresolvedSelectedReferences,
      chrome.navigationHeading, chrome.semanticPanelHeading, chrome.emphasisHeading, chrome.retainedPointsHeading,
      chrome.diagramHeading, chrome.diagramItemsHeading, chrome.diagramEdgesHeading, chrome.emptyDiagramMessage,
      chrome.sourcesHeading, chrome.omissionsHeading, chrome.rationaleHeading, chrome.identitiesHeading,
      chrome.coreIdentityLabel, chrome.documentIdentityLabel, chrome.summaryIdentityLabel, chrome.outputIdentityLabel
    ), "chrome")
    _required_map(vocabulary.sourceCategories, Set("steps", "claims", "nodes", "relations", "flows"), "source category")
    val units = validated.description.summary.units
    _required_map(vocabulary.diagramItemKinds, units.flatMap(_.diagram.toVector.flatMap(_.items.map(_.kind))).toSet, "diagram item category")
    _required_map(vocabulary.documentTargetKinds, units.flatMap(_.omissions.map(_.documentKind)).toSet, "Document target kind")
    _required_map(vocabulary.omissionDispositions, units.flatMap(_.omissions.map(_.disposition)).toSet, "omission disposition")
    _required_map(vocabulary.directions, units.flatMap(_.diagram.toVector.flatMap(_.edges.map(_.direction))).toSet, "diagram direction")
    val relations = units.flatMap(_.diagram.toVector.flatMap(_.edges.collect { case edge if edge.kind == "relation" => validated.document.core.relationsById(edge.ref).relationType })).toSet
    val transitions = _transition_sources(validated.document.core)
    val flows = units.flatMap(_.diagram.toVector.flatMap(_.edges.collect { case edge if edge.kind == "flow-transition" => transitions(edge.ref).transition.relationType })).toSet
    _required_map(vocabulary.relationTypes, relations, "Relation type")
    _required_map(vocabulary.flowTypes, flows, "Flow type")
  }

  private def _required(values: Vector[String], category: String): Unit =
    values.zipWithIndex.find { case (value, _) => !_usable(value) }.foreach { case (_, index) => _fail("SUMMARY_CONFIRMATION_V2_VOCABULARY", s"missing or blank $category wording at index $index") }

  private def _required_map(values: Map[String, String], required: Set[String], category: String): Unit =
    required.toVector.sorted.find(key => !_usable(values.getOrElse(key, null))).foreach(key => _fail("SUMMARY_CONFIRMATION_V2_VOCABULARY", s"missing or blank $category wording: $key"))

  private def _usable(value: String): Boolean = value != null && value.nonEmpty && value == value.trim
  private def _identity(value: String): String = "sha256:" + MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)).map(byte => f"${byte & 0xff}%02x").mkString
  private def _html(value: String): String = {
    val builder = new StringBuilder
    value.foreach {
      case '&' => builder.append("&amp;")
      case '<' => builder.append("&lt;")
      case '>' => builder.append("&gt;")
      case '"' => builder.append("&quot;")
      case '\'' => builder.append("&#39;")
      case character => builder.append(character)
    }
    builder.toString
  }
  private def _fail(code: String, reason: String): Nothing = throw ProjectionFault(code, reason)

  private final case class TransitionSource(flowid: String, transition: CozyDocumentLogicTree.Transition)
}
