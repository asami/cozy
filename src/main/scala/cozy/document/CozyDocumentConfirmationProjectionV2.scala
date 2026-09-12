package cozy.document

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/*
 * @since   Sep. 12, 2026
 * @version Sep. 12, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyDocumentConfirmationProjectionV2 {
  final case class Chrome(
    statusHeading: String,
    coverageComplete: String,
    currentSources: String,
    admittedState: String,
    noUnresolvedReferences: String,
    containmentHeading: String,
    flowHeading: String,
    structureHeading: String,
    proseHeading: String,
    logicalPatternHeading: String,
    nodesHeading: String,
    nodeRoleHeading: String,
    relationsHeading: String,
    noDirectChildFlowTransitions: String,
    logicalStructureReference: String,
    identitiesHeading: String,
    coreIdentityLabel: String,
    documentIdentityLabel: String,
    outputIdentityLabel: String
  )
  final case class Vocabulary(
    chrome: Chrome,
    logicalPatterns: Map[String, String],
    nodeRoles: Map[String, String],
    relationTypes: Map[String, String],
    flowTypes: Map[String, String]
  )
  final case class Rendered(html: String, identity: String)
  final case class ProjectionFault(code: String, reason: String)
    extends IllegalArgumentException(s"$code reason=$reason")

  def render(validated: CozyDocumentDescriptionV2.ValidatedDocument, vocabulary: Vocabulary): Rendered = {
    _validate_vocabulary(validated, vocabulary)
    val draft = _page(validated, vocabulary, "")
    val identity = _identity(draft)
    Rendered(_page(validated, vocabulary, identity), identity)
  }

  private def _page(validated: CozyDocumentDescriptionV2.ValidatedDocument, vocabulary: Vocabulary, outputidentity: String): String = {
    val description = validated.description
    val root = validated.core.core.root
    val labels = description.labels.steps.map(value => value.stepRef -> value.text).toMap
    val nodelabels = description.labels.nodes.map(value => value.nodeRef -> value.text).toMap
    val selected = _selection(root)
    val containment = _containment(root, root.id, labels)
    val flows = validated.core.depthFirstSteps.map(step => _flow_panel(step, root.id, labels, vocabulary)).mkString
    val structures = validated.core.depthFirstSteps.map(step => _structure_panel(step, root.id, labels, nodelabels, vocabulary)).mkString
    val prose = description.document.sections.map(section => _section(section, selected, vocabulary, 2)).mkString
    val chrome = vocabulary.chrome
    s"""<!doctype html>
       |<html lang="${_html(description.locale)}"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>${_html(description.document.title)}</title><style>
       |:root{color-scheme:light;font-family:system-ui,sans-serif;color:#172033;background:#edf2f7;line-height:1.55}body{margin:0}.workspace{max-width:96rem;margin:0 auto;padding:clamp(1rem,3vw,2.5rem)}.workspace-header{margin-bottom:1rem}.workspace-header h1{margin:.1rem 0}.status{border-left:.35rem solid #2563a8;background:#fff;padding:.75rem 1rem;margin:1rem 0}.status h2,.workspace-grid h2{margin:.1rem 0 .55rem}.status ul{margin:.25rem 0;padding-left:1.3rem}.workspace-grid{display:grid;grid-template-columns:minmax(16rem,1fr) minmax(20rem,1.25fr) minmax(24rem,1.7fr);gap:1rem;align-items:start}.panel{background:#fff;border:1px solid #cbd5e1;border-radius:.45rem;padding:1rem}.containment-list{list-style:none;margin:0;padding-left:0}.containment-list .containment-list{padding-left:1rem;margin-top:.35rem;border-left:1px solid #cbd5e1}.step-control{width:100%;text-align:left;background:#f8fafc;border:1px solid #94a3b8;border-radius:.35rem;padding:.45rem .6rem;color:inherit;cursor:pointer}.step-control[aria-pressed="true"]{background:#dbeafe;border-color:#2563a8;font-weight:700}.step-control:focus-visible{outline:.22rem solid #e05a00;outline-offset:.18rem}.identity{font-family:ui-monospace,SFMono-Regular,monospace;font-size:.82em;color:#475569}.detail-panel[hidden]{display:none}.flow-list,.structure-list,.document-list{padding-left:1.25rem}.flow-list li,.structure-list li{margin:.45rem 0}.type-wording{color:#334e68;font-weight:600}.document-prose{grid-column:1 / -1}.document-section{margin:1.3rem 0;padding:.8rem;border-left:.3rem solid transparent}.document-section.is-highlighted,.document-block.is-highlighted,.document-list-item.is-highlighted{background:#fff4cc;border-color:#c87900}.document-block{margin:1rem 0;padding:.15rem .45rem;border-left:.2rem solid transparent}.document-example,.document-note{padding:.7rem;border:1px solid #cbd5e1;border-radius:.3rem}.document-note{background:#fffbeb}.logical-structure-reference{padding:.6rem;background:#f8fafc}.secondary{margin-top:1rem;color:#475569}.secondary dl{display:grid;grid-template-columns:max-content 1fr;gap:.3rem .75rem}.secondary dd{margin:0;overflow-wrap:anywhere}@media (max-width:58rem){.workspace-grid{grid-template-columns:1fr 1fr}.document-prose{grid-column:1 / -1}}@media (max-width:42rem){.workspace{padding:.75rem}.workspace-grid{grid-template-columns:1fr}.document-prose{grid-column:auto}.panel{padding:.8rem}}@media print{.workspace{max-width:none}.step-control{border-color:#64748b}.detail-panel[hidden]{display:block}}
       |</style></head><body><main class="workspace" data-document-id="${_html(description.id)}" data-core-id="${_html(validated.core.core.id)}" data-output-identity="${_html(outputidentity)}"><header class="workspace-header"><h1>${_html(description.document.title)}</h1></header><section id="status-region" class="status" aria-label="${_html(chrome.statusHeading)}"><h2>${_html(chrome.statusHeading)}</h2><ul><li>${_html(chrome.coverageComplete)}</li><li>${_html(chrome.currentSources)}</li><li>${_html(chrome.admittedState)}</li><li>${_html(chrome.noUnresolvedReferences)}</li></ul></section><div class="workspace-grid"><nav id="containment-region" class="panel" aria-label="${_html(chrome.containmentHeading)}"><h2>${_html(chrome.containmentHeading)}</h2>$containment</nav><section id="flow-region" class="panel" aria-label="${_html(chrome.flowHeading)}" aria-live="polite"><h2>${_html(chrome.flowHeading)}</h2>$flows</section><section id="structure-region" class="panel" aria-label="${_html(chrome.structureHeading)}" aria-live="polite"><h2>${_html(chrome.structureHeading)}</h2>$structures</section><section id="prose-region" class="panel document-prose" aria-label="${_html(chrome.proseHeading)}" aria-describedby="status-region"><h2>${_html(chrome.proseHeading)}</h2>$prose</section></div><details class="secondary"><summary>${_html(chrome.identitiesHeading)}</summary><dl><dt>${_html(chrome.coreIdentityLabel)}</dt><dd>${_html(validated.coreIdentity)}</dd><dt>${_html(chrome.documentIdentityLabel)}</dt><dd>${_html(validated.documentIdentity)}</dd><dt>${_html(chrome.outputIdentityLabel)}</dt><dd data-output-identity="${_html(outputidentity)}">${_html(outputidentity)}</dd></dl></details></main><script>
       |(function(){const controls=Array.from(document.querySelectorAll('[data-step-control]'));const panels=Array.from(document.querySelectorAll('[data-step-panel]'));const targets=Array.from(document.querySelectorAll('[data-reference-target]'));const values=(element,name)=>(element.getAttribute(name)||'').split(' ').filter(Boolean);const select=control=>{const selected={steps:new Set([control.getAttribute('data-step-id')]),claims:new Set(values(control,'data-step-claims')),nodes:new Set(values(control,'data-step-nodes')),relations:new Set(values(control,'data-step-relations')),flows:new Set(values(control,'data-step-flows'))};controls.forEach(item=>item.setAttribute('aria-pressed',String(item===control)));panels.forEach(panel=>panel.hidden=panel.getAttribute('data-step-panel')!==control.getAttribute('data-step-id'));targets.forEach(target=>{const hit=['steps','claims','nodes','relations','flows'].some(kind=>values(target,'data-core-'+kind).some(value=>selected[kind].has(value)));target.classList.toggle('is-highlighted',hit);});};controls.forEach(control=>control.addEventListener('click',()=>select(control)));select(controls.find(control=>control.getAttribute('aria-pressed')==='true')||controls[0]);}());
       |</script></body></html>""".stripMargin
  }

  private def _containment(step: CozyDocumentLogicTree.Step, selectedid: String, labels: Map[String, String]): String = {
    val pressed = step.id == selectedid
    val children = if (step.steps.isEmpty) "" else step.steps.map(child => _containment(child, selectedid, labels)).mkString("<ul class=\"containment-list\">", "", "</ul>")
    s"""<ul class="containment-list"><li><button type="button" class="step-control" data-step-control="true" data-step-id="${_html(step.id)}" data-step-claims="${_html(step.claims.map(_.id).mkString(" "))}" data-step-nodes="${_html(step.structure.nodes.map(_.id).mkString(" "))}" data-step-relations="${_html(step.structure.relations.map(_.id).mkString(" "))}" data-step-flows="${_html(step.flow.id)}" aria-pressed="$pressed" aria-controls="flow-${_html(step.id)} structure-${_html(step.id)} prose-region">${_html(labels(step.id))} <span class="identity">${_html(step.id)}</span></button>$children</li></ul>"""
  }

  private def _flow_panel(step: CozyDocumentLogicTree.Step, selectedid: String, labels: Map[String, String], vocabulary: Vocabulary): String = {
    val hidden = if (step.id == selectedid) "" else " hidden"
    val content = if (step.flow.transitions.isEmpty) s"<p>${_html(vocabulary.chrome.noDirectChildFlowTransitions)}</p>" else step.flow.transitions.map { transition =>
      s"""<li data-flow-id="${_html(step.flow.id)}" data-flow-transition-id="${_html(transition.id)}" data-flow-type="${_html(transition.relationType)}"><span>${_html(labels(transition.fromStepId))} <span class="identity">${_html(transition.fromStepId)}</span></span> <span class="type-wording">${_html(vocabulary.flowTypes(transition.relationType))}</span> <span>${_html(labels(transition.toStepId))} <span class="identity">${_html(transition.toStepId)}</span></li>"""
    }.mkString("<ul class=\"flow-list\">", "", "</ul>")
    s"""<article id="flow-${_html(step.id)}" class="detail-panel" data-step-panel="${_html(step.id)}"$hidden><h3>${_html(labels(step.id))} <span class="identity">${_html(step.id)}</span></h3>$content</article>"""
  }

  private def _structure_panel(step: CozyDocumentLogicTree.Step, selectedid: String, labels: Map[String, String], nodelabels: Map[String, String], vocabulary: Vocabulary): String = {
    val hidden = if (step.id == selectedid) "" else " hidden"
    val structure = step.structure
    val nodes = structure.nodes.map { node =>
      s"""<li data-node-id="${_html(node.id)}" data-node-role="${_html(node.role)}">${_html(nodelabels(node.id))} <span class="identity">${_html(node.id)}</span> · ${_html(vocabulary.chrome.nodeRoleHeading)}: <span class="type-wording">${_html(vocabulary.nodeRoles(node.role))}</span></li>"""
    }.mkString("<ul class=\"structure-list\">", "", "</ul>")
    val relations = structure.relations.map { relation =>
      s"""<li data-relation-id="${_html(relation.id)}" data-relation-type="${_html(relation.relationType)}"><span>${_html(nodelabels(relation.from))} <span class="identity">${_html(relation.from)}</span></span> <span class="type-wording">${_html(vocabulary.relationTypes(relation.relationType))}</span> <span>${_html(nodelabels(relation.to))} <span class="identity">${_html(relation.to)}</span></span> <span class="identity">${_html(relation.id)}</span></li>"""
    }.mkString("<ul class=\"structure-list\">", "", "</ul>")
    s"""<article id="structure-${_html(step.id)}" class="detail-panel" data-step-panel="${_html(step.id)}"$hidden><h3>${_html(labels(step.id))} <span class="identity">${_html(step.id)}</span></h3><p>${_html(vocabulary.chrome.logicalPatternHeading)}: <span class="type-wording" data-logical-pattern="${_html(structure.pattern)}">${_html(vocabulary.logicalPatterns(structure.pattern))}</span> <span class="identity">${_html(structure.pattern)}</span></p><h4>${_html(vocabulary.chrome.nodesHeading)}</h4>$nodes<h4>${_html(vocabulary.chrome.relationsHeading)}</h4>$relations</article>"""
  }

  private def _section(section: CozyDocumentDescriptionV2.Section, selected: Selection, vocabulary: Vocabulary, level: Int): String = {
    val heading = math.min(level, 6)
    val blocks = section.blocks.map(block => _block(block, selected, vocabulary)).mkString
    val children = section.sections.map(child => _section(child, selected, vocabulary, level + 1)).mkString
    val target = _target_attributes(section.coreRefs, selected, s"""data-section-id="${_html(section.id)}"""")
    s"""<section class="document-section${_highlight_class(section.coreRefs, selected)}" $target><h$heading>${_html(section.heading)}</h$heading>$blocks$children</section>"""
  }

  private def _block(block: CozyDocumentDescriptionV2.Block, selected: Selection, vocabulary: Vocabulary): String = block match {
    case value: CozyDocumentDescriptionV2.Paragraph =>
      val target = _target_attributes(value.coreRefs, selected, s"""data-block-id="${_html(value.id)}"""")
      s"""<p class="document-block${_highlight_class(value.coreRefs, selected)}" $target>${_html(value.text)}</p>"""
    case value: CozyDocumentDescriptionV2.ListBlock =>
      val target = _target_attributes(value.coreRefs, selected, s"""data-block-id="${_html(value.id)}"""")
      val items = value.items.map(item => _list_item(item, selected)).mkString
      s"""<ul class="document-block document-list${_highlight_class(value.coreRefs, selected)}" $target>$items</ul>"""
    case value: CozyDocumentDescriptionV2.Example =>
      val target = _target_attributes(value.coreRefs, selected, s"""data-block-id="${_html(value.id)}"""")
      s"""<article class="document-block document-example${_highlight_class(value.coreRefs, selected)}" $target><h3>${_html(value.title)}</h3><p>${_html(value.text)}</p></article>"""
    case value: CozyDocumentDescriptionV2.Note =>
      val target = _target_attributes(value.coreRefs, selected, s"""data-block-id="${_html(value.id)}"""")
      s"""<aside class="document-block document-note${_highlight_class(value.coreRefs, selected)}" $target><h3>${_html(value.title)}</h3><p>${_html(value.text)}</p></aside>"""
    case value: CozyDocumentDescriptionV2.LogicalStructure =>
      val refs = CozyDocumentDescriptionV2.References(Vector(value.stepRef), Vector.empty, Vector.empty, Vector.empty, Vector.empty)
      val target = _target_attributes(refs, selected, s"""data-block-id="${_html(value.id)}"""")
      s"""<aside class="document-block logical-structure-reference${_highlight_class(refs, selected)}" $target>${_html(vocabulary.chrome.logicalStructureReference)}: <span class="identity">${_html(value.stepRef)}</span></aside>"""
  }

  private def _list_item(item: CozyDocumentDescriptionV2.ListItem, selected: Selection): String = {
    val target = _target_attributes(item.coreRefs, selected, s"""data-list-item-id="${_html(item.id)}"""")
    s"""<li class="document-list-item${_highlight_class(item.coreRefs, selected)}" $target>${_html(item.text)}</li>"""
  }

  private def _target_attributes(refs: CozyDocumentDescriptionV2.References, selected: Selection, identity: String): String =
    s"""$identity data-reference-target="true" data-core-steps="${_html(refs.steps.mkString(" "))}" data-core-claims="${_html(refs.claims.mkString(" "))}" data-core-nodes="${_html(refs.nodes.mkString(" "))}" data-core-relations="${_html(refs.relations.mkString(" "))}" data-core-flows="${_html(refs.flows.mkString(" "))}"""

  private def _selection(step: CozyDocumentLogicTree.Step): Selection =
    Selection(Set(step.id), step.claims.map(_.id).toSet, step.structure.nodes.map(_.id).toSet, step.structure.relations.map(_.id).toSet, Set(step.flow.id))

  private def _highlight_class(refs: CozyDocumentDescriptionV2.References, selected: Selection): String =
    if (_matches(refs, selected)) " is-highlighted" else ""

  private def _matches(refs: CozyDocumentDescriptionV2.References, selected: Selection): Boolean =
    refs.steps.exists(selected.steps.contains) || refs.claims.exists(selected.claims.contains) || refs.nodes.exists(selected.nodes.contains) || refs.relations.exists(selected.relations.contains) || refs.flows.exists(selected.flows.contains)

  private def _validate_vocabulary(validated: CozyDocumentDescriptionV2.ValidatedDocument, vocabulary: Vocabulary): Unit = {
    val chrome = vocabulary.chrome
    _required(Vector(
      chrome.statusHeading, chrome.coverageComplete, chrome.currentSources, chrome.admittedState, chrome.noUnresolvedReferences,
      chrome.containmentHeading, chrome.flowHeading, chrome.structureHeading, chrome.proseHeading, chrome.logicalPatternHeading,
      chrome.nodesHeading, chrome.nodeRoleHeading, chrome.relationsHeading, chrome.noDirectChildFlowTransitions,
      chrome.logicalStructureReference, chrome.identitiesHeading, chrome.coreIdentityLabel, chrome.documentIdentityLabel,
      chrome.outputIdentityLabel
    ), "chrome")
    _required_map(vocabulary.logicalPatterns, validated.core.depthFirstSteps.map(_.structure.pattern).toSet, "logical pattern")
    _required_map(vocabulary.nodeRoles, validated.core.depthFirstSteps.flatMap(_.structure.nodes.map(_.role)).toSet, "node role")
    _required_map(vocabulary.relationTypes, validated.core.depthFirstSteps.flatMap(_.structure.relations.map(_.relationType)).toSet, "Relation type")
    _required_map(vocabulary.flowTypes, validated.core.depthFirstSteps.flatMap(_.flow.transitions.map(_.relationType)).toSet, "Flow type")
  }

  private def _required(values: Vector[String], category: String): Unit =
    values.zipWithIndex.find { case (value, _) => !_usable(value) }.foreach { case (_, index) => _fail("CONFIRMATION_V2_VOCABULARY", s"missing or blank $category wording at index $index") }

  private def _required_map(values: Map[String, String], required: Set[String], category: String): Unit =
    required.toVector.sorted.find(key => !_usable(values.getOrElse(key, null))).foreach(key => _fail("CONFIRMATION_V2_VOCABULARY", s"missing or blank $category wording: $key"))

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

  private final case class Selection(steps: Set[String], claims: Set[String], nodes: Set[String], relations: Set[String], flows: Set[String])
}
