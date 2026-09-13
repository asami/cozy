package cozy.document

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/*
 * @since   Sep. 12, 2026
 * @version Sep. 13, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozySummaryConfirmationProjectionV2 {
  final case class Chrome(
    pageHeading: String,
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
    val documenttargets = _document_target_labels(validated.document.description.document, labels)
    val selected = summary.units.head
    val navigation = summary.units.zipWithIndex.map { case (unit, index) => _navigation_item(unit, selected.id, index) }.mkString(s"""<ol class="flow" style="--summary-unit-count:${summary.units.length}">""", "", "</ol>")
    val slides = summary.units.zipWithIndex.map { case (unit, index) => _semantic_panel(unit, selected.id, index, summary.units.length, validated, labels, nodelabels, vocabulary) }.mkString
    val evidence = summary.units.map(unit => _evidence_panel(unit, selected.id, validated, labels, nodelabels, documenttargets, vocabulary)).mkString
    val chrome = vocabulary.chrome
    val overviewstyles = if (summary.units.exists(_.overview.nonEmpty)) _overview_styles else ""
    s"""<!doctype html>
       |<html lang="${_html(validated.description.locale)}"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>${_html(summary.title)}</title><style>
       |:root{color-scheme:light dark;--background:#f7f8fb;--foreground:#182230;--card:#ffffff;--muted:#eef2f7;--muted-foreground:#607086;--border:#d7dee8;--primary:#2d67b1;--primary-soft:#e8f1fc;--primary-foreground:#133b6e;--ok:#18825d;--font-sans:Inter,"Hiragino Sans","Yu Gothic",sans-serif;--font-mono:ui-monospace,SFMono-Regular,Menlo,monospace}@media (prefers-color-scheme:dark){:root{--background:#111720;--foreground:#e7edf5;--card:#171f2a;--muted:#202b39;--muted-foreground:#a6b3c3;--border:#334154;--primary:#75aef0;--primary-soft:#203955;--primary-foreground:#e3f0ff;--ok:#6dd7ad}}*{box-sizing:border-box}body{margin:0;color:var(--foreground);background:var(--background);font-family:var(--font-sans);line-height:1.45}button{font:inherit}.page{max-width:1500px;margin:0 auto;padding:24px}.header{display:flex;align-items:flex-end;justify-content:space-between;gap:20px;padding-bottom:17px;border-bottom:1px solid var(--border)}.kicker{color:var(--muted-foreground);font-size:12px;font-weight:750;letter-spacing:.08em;text-transform:uppercase}h1{margin:3px 0 0;font-size:clamp(25px,3vw,38px);letter-spacing:-.035em}.status{display:flex;flex-wrap:wrap;gap:9px 17px;color:var(--muted-foreground);font-size:12px}.status strong{color:var(--foreground)}.flow-label{margin:18px 0 8px;color:var(--muted-foreground);font-size:12px;font-weight:750;letter-spacing:.035em}.flow{display:grid;grid-template-columns:repeat(var(--summary-unit-count),minmax(0,1fr));margin:0 0 18px;padding:0;border:1px solid var(--border);background:var(--card)}.flow li{position:relative;min-width:0;list-style:none;border-right:1px solid var(--border)}.flow li:last-child{border-right:0}.flow li:not(:last-child)::after{content:"›";position:absolute;right:6px;top:50%;transform:translateY(-50%);color:var(--muted-foreground);font-size:20px;pointer-events:none}.flow button{display:block;width:100%;min-height:62px;padding:11px 22px 11px 12px;border:0;border-bottom:3px solid transparent;color:var(--foreground);background:transparent;text-align:left;cursor:pointer}.flow button:hover,.flow button:focus-visible{outline:2px solid var(--primary);outline-offset:-2px}.flow button[aria-pressed="true"]{border-bottom-color:var(--primary);background:var(--primary-soft);color:var(--primary-foreground)}.flow small{display:block;color:var(--muted-foreground);font-size:9px;font-weight:750;letter-spacing:.05em}.flow b{display:block;margin-top:3px;overflow-wrap:anywhere;font-size:11px}.workspace{display:grid;grid-template-columns:minmax(0,1.55fr) minmax(250px,.55fr);gap:18px;align-items:start}.slide-wrap{min-width:0}.slide-number{display:flex;justify-content:space-between;margin-bottom:7px;color:var(--muted-foreground);font-size:11px}.slide{position:relative;display:grid;grid-template-rows:auto 1fr auto;width:100%;aspect-ratio:16 / 9;min-height:400px;overflow:hidden;border:1px solid var(--border);background:var(--card)}.slide::before{content:"";position:absolute;inset:0 auto 0 0;width:8px;background:var(--primary)}.slide-head{padding:clamp(20px,3vw,34px) clamp(28px,4vw,48px) 10px}.slide-head small{color:var(--primary);font-size:10px;font-weight:800;letter-spacing:.08em}.slide h2{margin:4px 0 0;font-size:clamp(22px,3vw,36px);line-height:1.2;letter-spacing:-.03em}.diagram{display:flex;align-items:center;justify-content:center;gap:clamp(6px,1vw,14px);padding:8px clamp(28px,4vw,48px)}.concept{display:flex;flex:1 1 0;align-items:center;justify-content:center;min-height:clamp(80px,10vw,116px);padding:12px;border:1px solid var(--border);background:var(--background);text-align:center;font-size:clamp(11px,1.4vw,17px);font-weight:800;white-space:pre-line}.concept.key{border-color:var(--primary);background:var(--primary-soft);color:var(--primary-foreground)}.arrow{min-width:50px;color:var(--primary);text-align:center;font-size:clamp(20px,3vw,35px);font-weight:800}.arrow small{display:block;color:var(--muted-foreground);font-size:8px;font-weight:750;white-space:nowrap}.message{margin:6px clamp(28px,4vw,48px) clamp(20px,3vw,31px);padding-top:12px;border-top:1px solid var(--border);color:var(--muted-foreground);font-size:clamp(11px,1.35vw,14px)}.inspector{border:1px solid var(--border);background:var(--card)}.inspector>h2{margin:0;padding:13px 15px;border-bottom:1px solid var(--border);font-size:14px}.inspector section{padding:14px 15px;border-bottom:1px solid var(--border)}.inspector section:last-child{border-bottom:0}.inspector h3,.inspector h4{margin:0 0 8px;color:var(--muted-foreground);font-size:10px;letter-spacing:.06em;text-transform:uppercase}.inspector ul,.inspector ol{margin:0;padding-left:17px;font-size:11px}.inspector li{margin:5px 0}.source-groups{display:grid;gap:7px;padding:0!important;list-style:none}.source-groups>li{padding:5px 7px;border-left:2px solid var(--primary);background:var(--muted)}.source-values{margin-top:4px!important}.identity{font-family:var(--font-mono);font-size:.82em;color:var(--muted-foreground);overflow-wrap:anywhere}.type-wording{color:var(--muted-foreground);font-weight:600}.detail-panel[hidden]{display:none}.secondary{margin-top:14px;padding:12px 14px;border-left:3px solid var(--primary);background:var(--muted);color:var(--muted-foreground);font-size:12px}.secondary dl{display:grid;grid-template-columns:max-content 1fr;gap:.3rem .75rem}.secondary dd{margin:0;overflow-wrap:anywhere}@media (max-width:850px){.page{padding:14px}.header{align-items:flex-start;flex-direction:column}.flow{grid-template-columns:1fr}.flow li{border-right:0;border-bottom:1px solid var(--border)}.flow li:last-child{border-bottom:0}.flow li:not(:last-child)::after{content:"↓";right:10px}.workspace{grid-template-columns:1fr}.slide{aspect-ratio:auto;min-height:430px}.diagram{flex-direction:column}.arrow{transform:rotate(90deg)}.arrow small{display:none}}@media print{.page{max-width:none}.unit-control{border-color:var(--border)}.detail-panel[hidden]{display:block}}
       |.primary-sources{margin:0;padding-left:17px;font-size:11px}.primary-sources li{margin:5px 0}.audit{border-top:1px solid var(--border);padding:14px 15px}.audit summary{cursor:pointer;color:var(--foreground);font-size:10px;font-weight:700;letter-spacing:.06em;text-transform:uppercase}.audit section{padding:10px 0;border:0}.audit h4{margin:0 0 8px}.audit .source-groups{font-size:11px}.audit .omission-target-content{white-space:pre-wrap}
       |$overviewstyles</style></head><body><div class="page" data-summary-id="${_html(validated.description.id)}" data-document-id="${_html(validated.document.description.id)}" data-core-id="${_html(validated.document.core.core.id)}" data-output-identity="${_html(outputidentity)}"><header class="header"><div><div class="kicker">${_html(summary.title)}</div><h1>${_html(chrome.pageHeading)}</h1></div><div id="status-region" class="status" aria-label="${_html(chrome.statusHeading)}"><span><strong>${_html(chrome.selectedSourcesCurrentAndAdmitted)}</strong></span><span><strong>${_html(chrome.noUnresolvedSelectedReferences)}</strong></span></div></header><div class="flow-label" id="summary-navigation-region" aria-label="${_html(chrome.navigationHeading)}">${_html(chrome.navigationHeading)}</div>$navigation<section id="summary-review-region" class="workspace" aria-describedby="status-region"><section id="semantic-panel-region" class="slide-wrap" aria-label="${_html(chrome.semanticPanelHeading)}" aria-live="polite"><div class="slide-number"><span>${_html(chrome.semanticPanelHeading)}</span><span data-summary-unit-count="true">1 / ${summary.units.length}</span></div>$slides</section><aside id="summary-evidence-region" class="inspector" aria-label="${_html(chrome.sourcesHeading)}" aria-live="polite"><h2>${_html(chrome.sourcesHeading)}</h2>$evidence</aside></section><details class="secondary"><summary>${_html(chrome.identitiesHeading)}</summary><dl><dt>${_html(chrome.coreIdentityLabel)}</dt><dd>${_html(validated.document.coreIdentity)}</dd><dt>${_html(chrome.documentIdentityLabel)}</dt><dd>${_html(validated.document.documentIdentity)}</dd><dt>${_html(chrome.summaryIdentityLabel)}</dt><dd>${_html(validated.summaryIdentity)}</dd><dt>${_html(chrome.outputIdentityLabel)}</dt><dd data-output-identity="${_html(outputidentity)}">${_html(outputidentity)}</dd></dl></details></div><script>
       |(function(){const controls=Array.from(document.querySelectorAll('[data-summary-unit-control]'));const panels=Array.from(document.querySelectorAll('[data-summary-unit-panel]'));const count=document.querySelector('[data-summary-unit-count]');const select=control=>{const id=control.getAttribute('data-summary-unit-id');controls.forEach(item=>item.setAttribute('aria-pressed',String(item===control)));panels.forEach(panel=>panel.hidden=panel.getAttribute('data-summary-unit-panel')!==id);if(count)count.textContent=(Number(control.getAttribute('data-summary-unit-index'))+1)+" / "+controls.length;};controls.forEach(control=>control.addEventListener('click',()=>select(control)));select(controls.find(control=>control.getAttribute('aria-pressed')==='true')||controls[0]);}());
       |</script></body></html>""".stripMargin
  }

  private def _navigation_item(unit: CozyDocumentDescriptionV2.SummaryUnit, selectedid: String, index: Int): String = {
    val pressed = unit.id == selectedid
    s"""<li><button type="button" class="unit-control" data-summary-unit-control="true" data-summary-unit-id="${_html(unit.id)}" data-summary-unit-index="$index" aria-pressed="$pressed" aria-controls="semantic-${_html(unit.id)} evidence-${_html(unit.id)}"><small>${_html(f"${index + 1}%02d")} · ${_html(unit.id)}</small><b>${_html(unit.navigationLabel)}</b></button></li>"""
  }

  private def _semantic_panel(
    unit: CozyDocumentDescriptionV2.SummaryUnit,
    selectedid: String,
    index: Int,
    count: Int,
    validated: CozyDocumentDescriptionV2.ValidatedSummary,
    labels: Map[String, String],
    nodelabels: Map[String, String],
    vocabulary: Vocabulary
  ): String = {
    val hidden = if (unit.id == selectedid) "" else " hidden"
    val chrome = vocabulary.chrome
    val diagram = unit.overview match {
      case Some(overview) => _overview_panel(unit, overview, validated.document.core, labels, nodelabels, vocabulary)
      case None => unit.diagram.map(value => _slide_diagram(value, validated.document.core, labels, nodelabels, vocabulary)).getOrElse(s"""<p class="message" data-empty-diagram="true">${_html(chrome.emptyDiagramMessage)}</p>""")
    }
    val overviewclass = if (unit.overview.nonEmpty) " summary-overview" else ""
    val overviewsource = unit.overview.map(value => s""" data-summary-overview-step="${_html(value.stepRef)}"""").getOrElse("")
    s"""<article id="semantic-${_html(unit.id)}" class="detail-panel semantic-panel slide$overviewclass" data-summary-unit-panel="${_html(unit.id)}" data-summary-slide-panel="true" data-emphasis="${_html(unit.emphasis)}" data-summary-unit-index="$index" data-summary-unit-total="$count"$overviewsource$hidden><header class="slide-head"><small>${_html(chrome.emphasisHeading)} · ${_html(unit.emphasis)}</small><h2>${_html(unit.heading)}</h2></header>$diagram<p class="message">${_html(unit.message)}</p></article>"""
  }

  private def _overview_panel(
    unit: CozyDocumentDescriptionV2.SummaryUnit,
    overview: CozyDocumentDescriptionV2.Overview,
    core: CozyDocumentLogicTree.ValidatedCore,
    labels: Map[String, String],
    nodelabels: Map[String, String],
    vocabulary: Vocabulary
  ): String = {
    val diagram = unit.diagram.get
    val root = core.stepsById(overview.stepRef)
    val steps = unit.coreRefs.steps.filterNot(_ == overview.stepRef).map(step => s"""<li data-overview-child-step="${_html(step)}">${_html(labels(step))}</li>""").mkString
    val flow = diagram.copy(items = diagram.items.filter(_.kind == "step"), edges = diagram.edges.filter(_.kind == "flow-transition"))
    val structure = diagram.copy(items = diagram.items.filter(_.kind == "node"), edges = diagram.edges.filter(_.kind == "relation"))
    s"""<div class="overview" data-overview-step="${_html(overview.stepRef)}"><section class="overview-containment" data-overview-region="containment"><h3>${_html(vocabulary.sourceCategories("steps"))}</h3><div class="overview-root" data-core-step-ref="${_html(overview.stepRef)}">${_html(labels(overview.stepRef))}</div><ul>$steps</ul></section><div class="overview-diagrams"><section data-overview-region="flow" data-core-flow-id="${_html(root.flow.id)}"><h3>${_html(vocabulary.sourceCategories("flows"))}</h3>${_slide_diagram(flow, core, labels, nodelabels, vocabulary)}</section><section data-overview-region="structure" data-core-step-ref="${_html(overview.stepRef)}"><h3>${_html(vocabulary.sourceCategories("nodes"))} / ${_html(vocabulary.sourceCategories("relations"))}</h3>${_slide_diagram(structure, core, labels, nodelabels, vocabulary)}</section></div></div>"""
  }

  private def _slide_diagram(
    diagram: CozyDocumentDescriptionV2.Diagram,
    core: CozyDocumentLogicTree.ValidatedCore,
    labels: Map[String, String],
    nodelabels: Map[String, String],
    vocabulary: Vocabulary
  ): String = {
    val outgoing = diagram.edges.groupBy(edge => _diagram_start(edge, core)).map { case (key, values) => key -> values.toVector }
    val concepts = diagram.items.flatMap { item =>
      val concept = s"""<div class="concept${if (item == diagram.items.head) " key" else ""}" data-diagram-item-id="${_html(item.id)}" data-diagram-item-kind="${_html(item.kind)}" data-core-ref="${_html(item.ref)}">${_html(_diagram_item_label(item, labels, nodelabels))}</div>"""
      concept +: outgoing.getOrElse(item.ref, Vector.empty).map(edge => _slide_arrow(edge, core, vocabulary))
    }
    val rendered = concepts.mkString
    s"""<div class="diagram" data-summary-diagram="true" aria-label="${_html(vocabulary.chrome.diagramHeading)}">$rendered</div>"""
  }

  private def _diagram_item_label(item: CozyDocumentDescriptionV2.DiagramItem, labels: Map[String, String], nodelabels: Map[String, String]): String =
    item.kind match {
      case "step" => labels(item.ref)
      case "node" => nodelabels(item.ref)
    }

  private def _diagram_start(edge: CozyDocumentDescriptionV2.DiagramEdge, core: CozyDocumentLogicTree.ValidatedCore): String =
    edge.kind match {
      case "relation" =>
        val relation = core.relationsById(edge.ref)
        if (edge.direction == "forward") relation.from else relation.to
      case "flow-transition" =>
        val transition = _transition_sources(core)(edge.ref).transition
        if (edge.direction == "forward") transition.fromStepId else transition.toStepId
    }

  private def _slide_arrow(edge: CozyDocumentDescriptionV2.DiagramEdge, core: CozyDocumentLogicTree.ValidatedCore, vocabulary: Vocabulary): String = {
    val wording = edge.kind match {
      case "relation" => vocabulary.relationTypes(core.relationsById(edge.ref).relationType)
      case "flow-transition" => vocabulary.flowTypes(_transition_sources(core)(edge.ref).transition.relationType)
    }
    val mark = if (edge.kind == "relation") "→" else "⇢"
    val category = if (edge.kind == "relation") "relations" else "flows"
    s"""<div class="arrow" data-diagram-edge-id="${_html(edge.id)}" data-diagram-edge-kind="${_html(edge.kind)}" data-core-ref="${_html(edge.ref)}" data-direction="${_html(edge.direction)}"${_edge_source_attributes(edge, core)}><small>${_html(vocabulary.sourceCategories(category))}</small><small>${_html(wording)}</small><span class="edge-mark" aria-hidden="true">$mark</span></div>"""
  }

  private def _edge_source_attributes(edge: CozyDocumentDescriptionV2.DiagramEdge, core: CozyDocumentLogicTree.ValidatedCore): String = {
    val (from, to, edgetype, flowid) = edge.kind match {
      case "relation" =>
        val relation = core.relationsById(edge.ref)
        (relation.from, relation.to, relation.relationType, Option.empty[String])
      case "flow-transition" =>
        val source = _transition_sources(core)(edge.ref)
        (source.transition.fromStepId, source.transition.toStepId, source.transition.relationType, Some(source.flowid))
    }
    val displayfrom = if (edge.direction == "forward") from else to
    val displayto = if (edge.direction == "forward") to else from
    val owner = flowid.map(id => s""" data-core-flow-id="${_html(id)}"""").getOrElse("")
    s""" data-core-edge-type="${_html(edgetype)}" data-core-from="${_html(from)}" data-core-to="${_html(to)}" data-display-from="${_html(displayfrom)}" data-display-to="${_html(displayto)}"$owner"""
  }

  private def _evidence_panel(
    unit: CozyDocumentDescriptionV2.SummaryUnit,
    selectedid: String,
    validated: CozyDocumentDescriptionV2.ValidatedSummary,
    labels: Map[String, String],
    nodelabels: Map[String, String],
    documenttargets: Map[(String, String), DocumentTarget],
    vocabulary: Vocabulary
  ): String = {
    val hidden = if (unit.id == selectedid) "" else " hidden"
    val chrome = vocabulary.chrome
    val sources = unit.coreRefs.steps.map(step => s"""<li>${_html(_step_label(step, labels))}</li>""").mkString("<ul class=\"primary-sources\">", "", "</ul>")
    val points = unit.retainedPoints.map(point =>
      s"""<li data-retained-point-id="${_html(point.id)}">${_html(point.text)}</li>"""
    ).mkString("<ul class=\"retained-points\">", "", "</ul>")
    val diagram = unit.diagram.map(value => _diagram(value, validated.document.core, labels, nodelabels, vocabulary)).getOrElse(s"""<p data-empty-diagram="true">${_html(chrome.emptyDiagramMessage)}</p>""")
    val omissions = unit.omissions.map(omission =>
      _primary_omission(omission, documenttargets)
    ).mkString("<ul class=\"omission-list\">", "", "</ul>")
    val auditpoints = unit.retainedPoints.map(point =>
      s"""<li data-retained-point-id="${_html(point.id)}"><p>${_html(point.text)} <span class="identity">${_html(point.id)}</span></p>${_references(point.coreRefs, labels, nodelabels, vocabulary)}</li>"""
    ).mkString("<ol class=\"retained-points\">", "", "</ol>")
    val auditomissions = unit.omissions.map { omission =>
      val target = documenttargets.getOrElse((omission.documentKind, omission.documentRef), _fail("SUMMARY_CONFIRMATION_V2_OMISSION_TARGET", s"missing admitted Document target: ${omission.documentKind}/${omission.documentRef}"))
      s"""<li data-omission-id="${_html(omission.id)}" data-document-kind="${_html(omission.documentKind)}" data-document-ref="${_html(omission.documentRef)}" data-omission-disposition="${_html(omission.disposition)}"><span class="type-wording">${_html(vocabulary.documentTargetKinds(omission.documentKind))}</span> <span class="identity">${_html(omission.documentRef)}</span> · <span class="type-wording">${_html(vocabulary.omissionDispositions(omission.disposition))}</span><p class="omission-target-label">${_html(target.compactlabel)}</p><p class="omission-target-content">${_html(target.fullcontent)}</p><p>${_html(chrome.rationaleHeading)}: ${_html(omission.rationale)}</p></li>"""
    }.mkString("<ol class=\"omission-list\">", "", "</ol>")
    val audit = s"""<details class="audit"><summary>${_html(chrome.diagramHeading)}</summary><section><h4>${_html(chrome.sourcesHeading)}</h4>${_references(unit.coreRefs, labels, nodelabels, vocabulary)}</section><section><h4>${_html(chrome.retainedPointsHeading)}</h4>$auditpoints</section><section><h4>${_html(chrome.diagramItemsHeading)} / ${_html(chrome.diagramEdgesHeading)}</h4>$diagram</section><section><h4>${_html(chrome.omissionsHeading)}</h4>$auditomissions</section></details>"""
    s"""<article id="evidence-${_html(unit.id)}" class="detail-panel" data-summary-unit-panel="${_html(unit.id)}" data-summary-evidence-panel="true"$hidden><section><h3>${_html(vocabulary.sourceCategories("steps"))}</h3>$sources</section><section><h3>${_html(chrome.retainedPointsHeading)}</h3>$points</section><section><h3>${_html(chrome.omissionsHeading)}</h3>$omissions</section>$audit</article>"""
  }

  private def _step_label(stepid: String, labels: Map[String, String]): String =
    labels.getOrElse(stepid, _fail("SUMMARY_CONFIRMATION_V2_DOCUMENT_LABEL", s"missing Document Step label: $stepid"))

  private def _primary_omission(
    omission: CozyDocumentDescriptionV2.Omission,
    documenttargets: Map[(String, String), DocumentTarget]
  ): String = {
    val target = documenttargets.getOrElse((omission.documentKind, omission.documentRef), _fail("SUMMARY_CONFIRMATION_V2_OMISSION_TARGET", s"missing admitted Document target: ${omission.documentKind}/${omission.documentRef}"))
    s"""<li data-omission-id="${_html(omission.id)}" data-document-kind="${_html(omission.documentKind)}" data-document-ref="${_html(omission.documentRef)}" data-omission-disposition="${_html(omission.disposition)}"><span class="omission-label">${_html(target.compactlabel)}</span><p>${_html(omission.rationale)}</p></li>"""
  }

  private def _document_target_labels(document: CozyDocumentDescriptionV2.Document, labels: Map[String, String]): Map[(String, String), DocumentTarget] =
    _document_target_labels(document.sections, labels)

  private def _document_target_labels(sections: Vector[CozyDocumentDescriptionV2.Section], labels: Map[String, String]): Map[(String, String), DocumentTarget] =
    sections.flatMap { section =>
      val sectiontarget = Vector(("section", section.id) -> DocumentTarget(section.heading, section.heading))
      val blocktargets = section.blocks.flatMap {
        case value: CozyDocumentDescriptionV2.Example => Vector(("block", value.id) -> DocumentTarget(value.title, value.text))
        case value: CozyDocumentDescriptionV2.Note => Vector(("block", value.id) -> DocumentTarget(value.title, value.text))
        case value: CozyDocumentDescriptionV2.Paragraph => Vector(("block", value.id) -> DocumentTarget(section.heading, value.text))
        case value: CozyDocumentDescriptionV2.ListBlock => Vector(("block", value.id) -> DocumentTarget(section.heading, value.items.map(_.text).mkString("\n"))) ++ value.items.map(item => ("list-item", item.id) -> DocumentTarget(if (item.text.length > 120) section.heading else item.text, item.text))
        case value: CozyDocumentDescriptionV2.LogicalStructure =>
          val label = _step_label(value.stepRef, labels)
          Vector(("block", value.id) -> DocumentTarget(label, label))
      }
      sectiontarget ++ blocktargets ++ _document_target_labels(section.sections, labels)
    }.toMap

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
    s"""<li data-diagram-edge-id="${_html(edge.id)}" data-diagram-edge-kind="${_html(edge.kind)}" data-core-ref="${_html(edge.ref)}" data-direction="${_html(edge.direction)}"${_edge_source_attributes(edge, core)}><span class="identity">${_html(edge.id)}</span> · <span class="type-wording">${_html(if (edge.kind == "relation") vocabulary.sourceCategories("relations") else vocabulary.sourceCategories("flows"))}</span> <span class="identity">${_html(edge.ref)}</span> · <span class="type-wording">${_html(vocabulary.directions(edge.direction))}</span><div>$endpoints</div></li>"""
  }

  private def _transition_sources(core: CozyDocumentLogicTree.ValidatedCore): Map[String, TransitionSource] =
    core.depthFirstSteps.flatMap(step => step.flow.transitions.map(transition => transition.id -> TransitionSource(step.flow.id, transition))).toMap

  private def _validate_summary(validated: CozyDocumentDescriptionV2.ValidatedSummary): Unit =
    if (validated.description.summary.units.isEmpty)
      _fail("SUMMARY_CONFIRMATION_V2_UNITS", "admitted Summary must contain at least one unit")

  private def _validate_vocabulary(validated: CozyDocumentDescriptionV2.ValidatedSummary, vocabulary: Vocabulary): Unit = {
    val chrome = vocabulary.chrome
    _required(Vector(
      chrome.pageHeading, chrome.statusHeading, chrome.selectedSourcesCurrentAndAdmitted, chrome.noUnresolvedSelectedReferences,
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

  private final case class DocumentTarget(compactlabel: String, fullcontent: String)
  private val _overview_styles = ".summary-overview h2{font-size:28px}.overview{display:grid;grid-template-rows:auto 1fr;gap:12px;padding:8px clamp(28px,4vw,48px);min-height:0}.overview h3{margin:0 0 7px;color:var(--muted-foreground);font-size:10px;font-weight:750}.overview-root{text-align:center;font-size:13px;font-weight:800}.overview-containment ul{display:flex;gap:10px;margin:6px 0 0;padding:8px 0 0;border-top:1px solid var(--border);list-style:none}.overview-containment li{flex:1;min-width:0;padding:6px;border:1px solid var(--border);background:var(--background);text-align:center;font-size:11px;font-weight:750}.overview-diagrams{display:grid;grid-template-columns:1.35fr 1fr;gap:12px;align-items:center}.overview-diagrams>section{min-width:0;padding:10px;border:1px solid var(--border)}.overview .diagram{padding:0;gap:8px}.overview .concept{min-width:0;min-height:64px;padding:7px;font-size:11px}.overview .arrow{min-width:24px;font-size:24px}.overview .arrow small{font-size:8px}"
  private final case class TransitionSource(flowid: String, transition: CozyDocumentLogicTree.Transition)
}
