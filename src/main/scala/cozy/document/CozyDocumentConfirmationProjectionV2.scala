package cozy.document

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/*
 * @since   Sep. 12, 2026
 * @version Sep. 13, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyDocumentConfirmationProjectionV2 {
  final case class Chrome(
    pageHeading: String,
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
    val containment = s"""<ul class=\"containment-list\">${_containment(root, root.id, labels, vocabulary)}</ul>"""
    val flows = validated.core.depthFirstSteps.map(step => _flow_panel(step, root.id, labels, vocabulary)).mkString
    val structures = validated.core.depthFirstSteps.map(step => _structure_panel(step, root.id, labels, nodelabels, vocabulary)).mkString
    val prose = description.document.sections.map(section => _section(section, selected, vocabulary, labels, validated.core, 2)).mkString
    val chrome = vocabulary.chrome
    s"""<!doctype html>
       |<html lang="${_html(description.locale)}"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>${_html(description.document.title)}</title><style>
       |:root{color-scheme:light dark;--background:#f7f8fb;--foreground:#182230;--card:#ffffff;--muted:#eef2f7;--muted-foreground:#607086;--border:#d7dee8;--primary:#2d67b1;--primary-soft:#e8f1fc;--primary-foreground:#133b6e;--ok:#18825d;--font-sans:Inter,"Hiragino Sans","Yu Gothic",sans-serif;--font-mono:ui-monospace,SFMono-Regular,Menlo,monospace}@media (prefers-color-scheme:dark){:root{--background:#111720;--foreground:#e7edf5;--card:#171f2a;--muted:#202b39;--muted-foreground:#a6b3c3;--border:#334154;--primary:#75aef0;--primary-soft:#203955;--primary-foreground:#e3f0ff;--ok:#6dd7ad}}*{box-sizing:border-box}body{margin:0;color:var(--foreground);background:var(--background);font-family:var(--font-sans);line-height:1.55}button{font:inherit}.page{max-width:1500px;margin:0 auto;padding:24px}.header{display:flex;align-items:flex-end;justify-content:space-between;gap:20px;padding-bottom:17px;border-bottom:1px solid var(--border)}.kicker{color:var(--muted-foreground);font-size:12px;font-weight:750;letter-spacing:.08em;text-transform:uppercase}h1{margin:3px 0 0;font-size:clamp(25px,3vw,38px);letter-spacing:-.035em}.status{display:flex;flex-wrap:wrap;justify-content:flex-end;gap:9px 17px;color:var(--muted-foreground);font-size:12px}.status strong{color:var(--foreground)}.guide{display:grid;grid-template-columns:minmax(280px,.82fr) 62px minmax(430px,1.4fr);gap:16px;margin:18px 0 9px;color:var(--muted-foreground);font-size:12px;font-weight:750;letter-spacing:.035em}.workspace{display:grid;grid-template-columns:minmax(280px,.82fr) 62px minmax(430px,1.4fr);gap:16px;align-items:start}.panel{min-width:0;border:1px solid var(--border);background:var(--card)}.panel-head{display:flex;justify-content:space-between;align-items:baseline;gap:10px;padding:14px 16px;border-bottom:1px solid var(--border)}.panel-head strong{font-size:14px}.panel-head span{color:var(--muted-foreground);font-size:11px}.containment-list,.containment-list ul{list-style:none}.containment-list{margin:0;padding:15px 15px 8px}.containment-list li{position:relative;margin:6px 0}.containment-list .containment-list{margin:5px 0 0 15px;padding:0 0 0 17px;border-left:1px solid var(--border)}.containment-list .containment-list>li::before{content:"";position:absolute;left:-17px;top:20px;width:12px;border-top:1px solid var(--border)}.step-control{display:block;width:100%;padding:6px 10px;border:0;border-left:3px solid transparent;color:var(--foreground);background:var(--muted);text-align:left;cursor:pointer}.step-control:hover,.step-control:focus-visible{outline:2px solid var(--primary);outline-offset:1px}.step-control .identity{display:block;color:var(--muted-foreground);font-size:10px;letter-spacing:.05em}.step-control[aria-pressed="true"]{border-left-color:var(--primary);background:var(--primary-soft);color:var(--primary-foreground)}.local{margin:12px 16px 17px;padding-top:14px;border-top:1px solid var(--border)}.local h2{margin:0 0 10px;font-size:13px}.detail-panel[hidden]{display:none}.flow-list,.structure-list,.document-list{margin:0;padding-left:17px;font-size:12px}.flow-list li,.structure-list li{margin:5px 0}.relation{display:grid;grid-template-columns:1fr auto 1fr;align-items:center;gap:8px}.concept{display:flex;align-items:center;justify-content:center;min-height:55px;padding:9px;border:1px solid var(--border);background:var(--background);text-align:center;font-size:12px;font-weight:750}.arrow{color:var(--primary);text-align:center;font-size:18px;font-weight:800}.arrow small{display:block;color:var(--muted-foreground);font-size:9px;white-space:nowrap}.binding{display:flex;flex-direction:column;align-items:center;justify-content:center;min-height:320px;gap:8px;color:var(--primary);font-size:28px;font-weight:800}.binding span{color:var(--muted-foreground);font-size:10px;font-weight:750;writing-mode:vertical-rl;letter-spacing:.08em}.article{padding:clamp(20px,3vw,36px)}.article>h2{margin:0;font-size:clamp(22px,2.5vw,30px);letter-spacing:-.025em}.document-section{position:relative;margin:21px 0;padding-left:17px;border-left:3px solid var(--border);transition:background .18s ease,border-color .18s ease}.document-section.is-highlighted,.document-block.is-highlighted,.document-list-item.is-highlighted{border-left-color:var(--primary);background:linear-gradient(90deg,var(--primary-soft),transparent 70%)}.document-section h2,.document-section h3,.document-section h4{margin:0 0 6px;font-size:16px}.document-section p,.document-section li{margin:6px 0;font-size:13px}.document-block{margin:10px 0}.refs{display:inline-flex;flex-wrap:wrap;gap:5px;margin-top:6px}.refs code{padding:2px 6px;border:1px solid var(--border);background:var(--muted);color:var(--muted-foreground);font-family:var(--font-mono);font-size:9px}.document-example,.document-note{padding:0 0 0 17px;border-left:3px solid transparent;font-size:13px}.document-example,.document-note{background:transparent;color:var(--foreground)}.logical-structure-reference{padding:0 0 0 17px;background:transparent;color:var(--foreground)}.identity{font-family:var(--font-mono);font-size:.82em;color:var(--muted-foreground);overflow-wrap:anywhere}.type-wording{color:var(--muted-foreground);font-weight:600}.secondary{margin-top:14px;padding:12px 14px;border-left:3px solid var(--primary);background:var(--muted);color:var(--muted-foreground);font-size:12px}.secondary dl{display:grid;grid-template-columns:max-content 1fr;gap:.3rem .75rem}.secondary dd{margin:0;overflow-wrap:anywhere}@media (max-width:850px){.page{padding:14px}.header{align-items:flex-start;flex-direction:column}.status{justify-content:flex-start}.guide{display:none}.workspace{grid-template-columns:1fr;margin-top:16px}.binding{flex-direction:row;padding:2px 0}.binding span{writing-mode:horizontal-tb}}@media print{.page{max-width:none}.step-control{border-color:var(--border)}.detail-panel[hidden]{display:block}}
       |.step-control small{display:block;color:var(--muted-foreground);font-size:10px;letter-spacing:.05em}.step-control b{display:block;overflow-wrap:anywhere;font-size:13px}.document-section{padding:0;border:0}.document-section.is-highlighted{background:none;border:0}.document-block{padding-left:17px;border-left:3px solid transparent;transition:background .18s ease,border-color .18s ease}.document-list-item{padding-left:8px;border-left:3px solid transparent}.structure-unconnected{display:flex;gap:8px;flex-wrap:wrap;margin:8px 0}.structure-audit{margin-top:12px;color:var(--muted-foreground);font-size:11px}.structure-audit summary{cursor:pointer;color:var(--foreground);font-weight:700}.structure-audit dl{display:grid;grid-template-columns:max-content 1fr;gap:.25rem .6rem}.logical-structure-reference summary{display:inline;font-size:10px;cursor:pointer}@media screen and (min-width:851px){#containment-region{position:sticky;top:16px;max-height:calc(100vh - 32px);overflow-y:auto}}.document-block,.document-list-item{scroll-margin-top:16px}
       |.structure-mark{display:inline-block;margin-right:4px;color:var(--primary);font-weight:800}.structure-tag{display:inline-flex;align-items:center;padding:2px 7px;border:1px solid var(--border);border-radius:999px;background:var(--primary-soft);color:var(--primary-foreground);font-size:10px;font-weight:750;line-height:1.4;white-space:nowrap;vertical-align:middle}.structure-tags{display:flex;flex-wrap:wrap;gap:4px;margin-bottom:8px}.step-control .structure-tag{margin-bottom:3px}.refs .structure-tag{padding:0 4px;font-size:9px}
       |</style></head><body><div class="page" data-document-id="${_html(description.id)}" data-core-id="${_html(validated.core.core.id)}" data-output-identity="${_html(outputidentity)}"><header class="header"><div><div class="kicker">${_html(description.document.title)}</div><h1>${_html(chrome.pageHeading)}</h1></div><div id="status-region" class="status" aria-label="${_html(chrome.statusHeading)}"><span><strong>${_html(chrome.coverageComplete)}</strong></span><span><strong>${_html(chrome.currentSources)}</strong></span><span><strong>${_html(chrome.admittedState)}</strong></span><span><strong>${_html(chrome.noUnresolvedReferences)}</strong></span></div></header><div class="guide"><span>Core：意味と論理構造</span><span></span><span>文書記述：読者に向けた文章化</span></div><main class="workspace"><section id="containment-region" class="panel" aria-label="${_html(chrome.containmentHeading)}"><div class="panel-head"><strong>${_html(chrome.containmentHeading)}</strong><span>Step / Claim / Relation</span></div>$containment<section id="structure-region" class="local" aria-label="${_html(chrome.structureHeading)}" aria-live="polite"><h2>${_structure_mark("structure")} ${_html(chrome.structureHeading)}</h2>$structures</section><details id="flow-region" class="local" aria-label="${_html(chrome.flowHeading)}" aria-live="polite"><summary>${_structure_mark("flow")} ${_html(chrome.flowHeading)}</summary>$flows</details></section><div class="binding" aria-label="Coreから文章への具体化"><span>文章へ具体化</span>→</div><section id="prose-region" class="panel document-prose" aria-label="${_html(chrome.proseHeading)}" aria-describedby="status-region"><div class="panel-head"><strong>${_html(chrome.proseHeading)}</strong><span>読者順</span></div><article class="article"><h2>${_html(labels(root.id))}</h2>$prose</article></section></main><details class="secondary"><summary>${_html(chrome.identitiesHeading)}</summary><dl><dt>${_html(chrome.coreIdentityLabel)}</dt><dd>${_html(validated.coreIdentity)}</dd><dt>${_html(chrome.documentIdentityLabel)}</dt><dd>${_html(validated.documentIdentity)}</dd><dt>${_html(chrome.outputIdentityLabel)}</dt><dd data-output-identity="${_html(outputidentity)}">${_html(outputidentity)}</dd></dl></details></div><script>
       |(function(){const controls=Array.from(document.querySelectorAll('[data-step-control]'));const panels=Array.from(document.querySelectorAll('[data-step-panel]'));const targets=Array.from(document.querySelectorAll('[data-reference-target]'));const values=(element,name)=>(element.getAttribute(name)||'').split(' ').filter(Boolean);const select=(control,reveal=false)=>{const selected={steps:new Set([control.getAttribute('data-step-id')]),claims:new Set(values(control,'data-step-claims')),nodes:new Set(values(control,'data-step-nodes')),relations:new Set(values(control,'data-step-relations')),flows:new Set(values(control,'data-step-flows'))};controls.forEach(item=>item.setAttribute('aria-pressed',String(item===control)));panels.forEach(panel=>panel.hidden=panel.getAttribute('data-step-panel')!==control.getAttribute('data-step-id'));targets.forEach(target=>{const hit=['steps','claims','nodes','relations','flows'].some(kind=>values(target,'data-core-'+kind).some(value=>selected[kind].has(value)));target.classList.toggle('is-highlighted',hit);});if(reveal){const target=targets.find(item=>item.matches('.document-block.is-highlighted,.document-list-item.is-highlighted'));if(target){const bounds=target.getBoundingClientRect();if(bounds.top<16||bounds.bottom>window.innerHeight-16)target.scrollIntoView({block:bounds.height>window.innerHeight-32?'start':'center',inline:'nearest',behavior:'auto'});}}};controls.forEach(control=>control.addEventListener('click',()=>select(control,true)));select(controls.find(control=>control.getAttribute('aria-pressed')==='true')||controls[0]);}());
       |</script></body></html>""".stripMargin
  }

  private def _containment(step: CozyDocumentLogicTree.Step, selectedid: String, labels: Map[String, String], vocabulary: Vocabulary): String = {
    val pressed = step.id == selectedid
    val children = if (step.steps.isEmpty) "" else step.steps.map(child => _containment(child, selectedid, labels, vocabulary)).mkString("<ul class=\"containment-list\">", "", "</ul>")
    s"""<li><button type="button" class="step-control" title="${_html(step.id)}" data-step-control="true" data-step-id="${_html(step.id)}" data-step-claims="${_html(step.claims.map(_.id).mkString(" "))}" data-step-nodes="${_html(step.structure.nodes.map(_.id).mkString(" "))}" data-step-relations="${_html(step.structure.relations.map(_.id).mkString(" "))}" data-step-flows="${_html(step.flow.id)}" aria-pressed="$pressed" aria-controls="flow-${_html(step.id)} structure-${_html(step.id)} prose-region">${_pattern_tag(step.structure.pattern, vocabulary)}<b>${_html(labels(step.id))}</b></button>$children</li>"""
  }

  private def _flow_panel(step: CozyDocumentLogicTree.Step, selectedid: String, labels: Map[String, String], vocabulary: Vocabulary): String = {
    val hidden = if (step.id == selectedid) "" else " hidden"
    val content = if (step.flow.transitions.isEmpty) s"<p>${_html(vocabulary.chrome.noDirectChildFlowTransitions)}</p>" else step.flow.transitions.map { transition =>
      s"""<li data-flow-id="${_html(step.flow.id)}" data-flow-transition-id="${_html(transition.id)}" data-flow-type="${_html(transition.relationType)}"><span>${_html(labels(transition.fromStepId))} <span class="identity">${_html(transition.fromStepId)}</span></span> <span class="type-wording"><span class="edge-mark" aria-hidden="true">⇢</span> ${_type_tag("flow", transition.relationType, vocabulary.flowTypes)}</span> <span>${_html(labels(transition.toStepId))} <span class="identity">${_html(transition.toStepId)}</span></span></li>"""
    }.mkString("<ul class=\"flow-list\">", "", "</ul>")
    s"""<article id="flow-${_html(step.id)}" class="detail-panel" data-step-panel="${_html(step.id)}"$hidden><h3>${_html(labels(step.id))} <span class="identity">${_html(step.id)}</span></h3>$content</article>"""
  }

  private def _structure_panel(step: CozyDocumentLogicTree.Step, selectedid: String, labels: Map[String, String], nodelabels: Map[String, String], vocabulary: Vocabulary): String = {
    val hidden = if (step.id == selectedid) "" else " hidden"
    val structure = step.structure
    val relationnodes = structure.relations.flatMap(relation => Vector(relation.from, relation.to)).toSet
    val unconnected = structure.nodes.filterNot(node => relationnodes.contains(node.id)).map { node =>
      s"""<span class="concept" data-node-id="${_html(node.id)}" data-node-role="${_html(node.role)}">${_html(nodelabels(node.id))}</span>"""
    }.mkString("<div class=\"structure-unconnected\">", "", "</div>")
    val relations = structure.relations.map(relation => _structure_relation(relation, structure, nodelabels, vocabulary)).mkString
    val nodes = structure.nodes.map(node => s"""<li data-node-id="${_html(node.id)}" data-node-role="${_html(node.role)}">${_html(nodelabels(node.id))} <span class="identity">${_html(node.id)}</span> · ${_html(vocabulary.chrome.nodeRoleHeading)}: <span class="type-wording">${_html(vocabulary.nodeRoles(node.role))}</span></li>""").mkString("<ul class=\"structure-list\">", "", "</ul>")
    val relationaudit = structure.relations.map(relation => s"""<li data-relation-id="${_html(relation.id)}" data-relation-type="${_html(relation.relationType)}"><span class="identity">${_html(relation.id)}</span> · ${_html(nodelabels(relation.from))} <span class="identity">${_html(relation.from)}</span> <span class="type-wording">${_html(vocabulary.relationTypes(relation.relationType))}</span> ${_html(nodelabels(relation.to))} <span class="identity">${_html(relation.to)}</span></li>""").mkString("<ul class=\"structure-list\">", "", "</ul>")
    val inventory = s"""<div class="identity" data-core-steps="${_html(step.id)}" data-core-claims="${_html(step.claims.map(_.id).mkString(" "))}" data-core-nodes="${_html(structure.nodes.map(_.id).mkString(" "))}" data-core-relations="${_html(structure.relations.map(_.id).mkString(" "))}" data-core-flows="${_html(step.flow.id)}">${_html(step.id)} · ${_html(structure.pattern)} · ${_html(step.claims.map(_.id).mkString(" "))} · ${_html(structure.nodes.map(_.id).mkString(" "))} · ${_html(structure.relations.map(_.id).mkString(" "))} · ${_html(step.flow.id)}</div>"""
    s"""<article id="structure-${_html(step.id)}" class="detail-panel" data-step-panel="${_html(step.id)}"$hidden><div class="structure-tags">${_pattern_tag(structure.pattern, vocabulary)}</div>${relations}${if (relationnodes.size == structure.nodes.size) "" else unconnected}<details class="structure-audit"><summary>${_html(vocabulary.chrome.logicalPatternHeading)}</summary><p data-logical-pattern="${_html(structure.pattern)}">${_html(vocabulary.chrome.logicalPatternHeading)}: <span class="type-wording">${_html(vocabulary.logicalPatterns(structure.pattern))}</span> <span class="identity">${_html(structure.pattern)}</span></p><h4>${_html(vocabulary.chrome.nodesHeading)}</h4>$nodes<h4>${_html(vocabulary.chrome.relationsHeading)}</h4>$relationaudit$inventory</details></article>"""
  }

  private def _structure_relation(relation: CozyDocumentLogicTree.Relation, structure: CozyDocumentLogicTree.Structure, nodelabels: Map[String, String], vocabulary: Vocabulary): String = {
    val nodes = structure.nodes.map(node => node.id -> node).toMap
    val from = nodes(relation.from)
    val to = nodes(relation.to)
    s"""<div class="relation" data-relation-id="${_html(relation.id)}" data-relation-type="${_html(relation.relationType)}"><div class="concept" data-node-id="${_html(from.id)}" data-node-role="${_html(from.role)}">${_html(nodelabels(from.id))}</div><div class="arrow"><small>${_type_tag("structure", relation.relationType, vocabulary.relationTypes)}</small><span class="edge-mark" aria-hidden="true">→</span></div><div class="concept" data-node-id="${_html(to.id)}" data-node-role="${_html(to.role)}">${_html(nodelabels(to.id))}</div></div>"""
  }

  private def _section(section: CozyDocumentDescriptionV2.Section, selected: Selection, vocabulary: Vocabulary, labels: Map[String, String], core: CozyDocumentLogicTree.ValidatedCore, level: Int): String = {
    val heading = math.min(level, 6)
    val blocks = section.blocks.map(block => _block(block, selected, vocabulary, labels, core)).mkString
    val children = section.sections.map(child => _section(child, selected, vocabulary, labels, core, level + 1)).mkString
    val target = _target_attributes(section.coreRefs, selected, s"""data-section-id="${_html(section.id)}"""")
    s"""<section class="document-section" $target><h$heading>${_html(section.heading)}</h$heading>$blocks</section>$children"""
  }

  private def _block(block: CozyDocumentDescriptionV2.Block, selected: Selection, vocabulary: Vocabulary, labels: Map[String, String], core: CozyDocumentLogicTree.ValidatedCore): String = block match {
    case value: CozyDocumentDescriptionV2.Paragraph =>
      val target = _target_attributes(value.coreRefs, selected, s"""data-block-id="${_html(value.id)}"""")
      s"""<div class="document-block${_highlight_class(value.coreRefs, selected)}" $target><p>${_html(value.text)}</p>${_reference_chips(value.coreRefs, core, vocabulary)}</div>"""
    case value: CozyDocumentDescriptionV2.ListBlock =>
      val target = _target_attributes(value.coreRefs, selected, s"""data-block-id="${_html(value.id)}"""")
      val items = value.items.map(item => _list_item(item, selected)).mkString
      s"""<div class="document-block document-list${_highlight_class(value.coreRefs, selected)}" $target><ul>$items</ul>${_reference_chips(value.coreRefs, core, vocabulary)}</div>"""
    case value: CozyDocumentDescriptionV2.Example =>
      val target = _target_attributes(value.coreRefs, selected, s"""data-block-id="${_html(value.id)}"""")
      s"""<article class="document-block document-example${_highlight_class(value.coreRefs, selected)}" $target><h3>${_html(value.title)}</h3><p>${_html(value.text)}</p>${_reference_chips(value.coreRefs, core, vocabulary)}</article>"""
    case value: CozyDocumentDescriptionV2.Note =>
      val target = _target_attributes(value.coreRefs, selected, s"""data-block-id="${_html(value.id)}"""")
      s"""<aside class="document-block document-note${_highlight_class(value.coreRefs, selected)}" $target><h3>${_html(value.title)}</h3><p>${_html(value.text)}</p>${_reference_chips(value.coreRefs, core, vocabulary)}</aside>"""
    case value: CozyDocumentDescriptionV2.LogicalStructure =>
      val refs = CozyDocumentDescriptionV2.References(Vector(value.stepRef), Vector.empty, Vector.empty, Vector.empty, Vector.empty)
      val target = _target_attributes(refs, selected, s"""data-block-id="${_html(value.id)}"""")
      val label = labels.getOrElse(value.stepRef, _fail("CONFIRMATION_V2_DOCUMENT_LABEL", s"missing Document Step label: ${value.stepRef}"))
      s"""<details class="document-block logical-structure-reference${_highlight_class(refs, selected)}" $target data-step-ref="${_html(value.stepRef)}"><summary>${_structure_mark("structure")} ${_html(vocabulary.chrome.logicalStructureReference)}: ${_html(label)} ${_pattern_tag(core.stepsById(value.stepRef).structure.pattern, vocabulary)}</summary><p class="identity" data-step-ref="${_html(value.stepRef)}">${_html(value.stepRef)}</p></details>"""
  }

  private def _list_item(item: CozyDocumentDescriptionV2.ListItem, selected: Selection): String = {
    val target = _target_attributes(item.coreRefs, selected, s"""data-list-item-id="${_html(item.id)}"""")
    s"""<li class="document-list-item${_highlight_class(item.coreRefs, selected)}" $target>${_html(item.text)}</li>"""
  }

  private def _reference_chips(refs: CozyDocumentDescriptionV2.References, core: CozyDocumentLogicTree.ValidatedCore, vocabulary: Vocabulary): String = {
    val values = refs.steps.map(id => s"""<code>step/${_html(id)}</code>""") ++ refs.relations.map(id => s"""<code>${_structure_mark("structure")} ${_type_tag("structure", core.relationsById(id).relationType, vocabulary.relationTypes)} relation/${_html(id)}</code>""")
    if (values.isEmpty) "" else values.mkString("<div class=\"refs\">", "", "</div>")
  }

  private def _pattern_tag(pattern: String, vocabulary: Vocabulary): String =
    s"""<span class="structure-tag" data-logical-pattern="${_html(pattern)}">${_html(vocabulary.logicalPatterns(pattern))}</span>"""

  private def _type_tag(scope: String, kind: String, wording: Map[String, String]): String =
    s"""<span class="structure-tag" data-structure-kind="$scope" data-structure-type="${_html(kind)}">${_html(wording(kind))}</span>"""

  private def _structure_mark(scope: String): String = {
    val mark = if (scope == "flow") "⇢" else "→"
    s"""<span class="structure-mark" data-structure-kind="$scope" aria-hidden="true">$mark</span>"""
  }

  private def _target_attributes(refs: CozyDocumentDescriptionV2.References, selected: Selection, identity: String): String =
    s"""$identity data-reference-target="true" data-core-steps="${_html(refs.steps.mkString(" "))}" data-core-claims="${_html(refs.claims.mkString(" "))}" data-core-nodes="${_html(refs.nodes.mkString(" "))}" data-core-relations="${_html(refs.relations.mkString(" "))}" data-core-flows="${_html(refs.flows.mkString(" "))}""""

  private def _selection(step: CozyDocumentLogicTree.Step): Selection =
    Selection(Set(step.id), step.claims.map(_.id).toSet, step.structure.nodes.map(_.id).toSet, step.structure.relations.map(_.id).toSet, Set(step.flow.id))

  private def _highlight_class(refs: CozyDocumentDescriptionV2.References, selected: Selection): String =
    if (_matches(refs, selected)) " is-highlighted" else ""

  private def _matches(refs: CozyDocumentDescriptionV2.References, selected: Selection): Boolean =
    refs.steps.exists(selected.steps.contains) || refs.claims.exists(selected.claims.contains) || refs.nodes.exists(selected.nodes.contains) || refs.relations.exists(selected.relations.contains) || refs.flows.exists(selected.flows.contains)

  private def _validate_vocabulary(validated: CozyDocumentDescriptionV2.ValidatedDocument, vocabulary: Vocabulary): Unit = {
    val chrome = vocabulary.chrome
    _required(Vector(
      chrome.pageHeading, chrome.statusHeading, chrome.coverageComplete, chrome.currentSources, chrome.admittedState, chrome.noUnresolvedReferences,
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
