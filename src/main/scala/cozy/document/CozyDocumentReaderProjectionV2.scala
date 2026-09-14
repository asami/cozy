package cozy.document

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/*
 * @since   Sep. 14, 2026
 * @version Sep. 14, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyDocumentReaderProjectionV2 {
  final case class Rendered(html: String, identity: String)

  final case class ProjectionFault(code: String, reason: String)
    extends IllegalArgumentException(s"$code reason=$reason")

  private final case class DocumentTarget(kind: String, id: String, label: String, refs: CozyDocumentDescriptionV2.References)

  def render(validated: CozyDocumentDescriptionV2.ValidatedDocument, vocabulary: CozyDocumentConfirmationProjectionV2.Vocabulary): Rendered = {
    _validate_vocabulary(validated, vocabulary)
    val targets = _document_targets(validated.description.document.sections, validated.description.labels.steps.map(value => value.stepRef -> value.text).toMap)
    val draft = _page(validated, vocabulary, "", targets)
    val identity = _identity(draft)
    Rendered(_page(validated, vocabulary, identity, targets), identity)
  }

  private def _page(
    validated: CozyDocumentDescriptionV2.ValidatedDocument,
    vocabulary: CozyDocumentConfirmationProjectionV2.Vocabulary,
    outputidentity: String,
    targets: Vector[DocumentTarget]
  ): String = {
    val description = validated.description
    val document = description.document
    val core = validated.core
    val labels = description.labels.steps.map(value => value.stepRef -> value.text).toMap
    val nodelabels = description.labels.nodes.map(value => value.nodeRef -> value.text).toMap
    val chrome = vocabulary.chrome
    val sections = document.sections.map(section => _section(section, vocabulary, labels, nodelabels, core, 2)).mkString
    val containment = _containment_surface(core.core.root, labels, chrome)
    val correspondence = core.depthFirstSteps.map(step => _correspondence_step(step, targets, labels, vocabulary)).mkString
    val flows = core.depthFirstSteps.map(step => _flow_step(step, labels, vocabulary)).mkString
    val structures = core.depthFirstSteps.map(step => _structure_step(step, labels, nodelabels, vocabulary)).mkString
    s"""<!doctype html>
       |<html lang="${_html(description.locale)}"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>${_html(document.title)}</title><style>
       |:root{color-scheme:light dark;--background:#f7f8fb;--foreground:#182230;--card:#fff;--muted:#eef2f7;--muted-foreground:#607086;--border:#d7dee8;--primary:#2d67b1;--primary-soft:#e8f1fc;--primary-foreground:#133b6e;--font-sans:Inter,"Hiragino Sans","Yu Gothic",sans-serif;--font-mono:ui-monospace,SFMono-Regular,Menlo,monospace}@media (prefers-color-scheme:dark){:root{--background:#111720;--foreground:#e7edf5;--card:#171f2a;--muted:#202b39;--muted-foreground:#a6b3c3;--border:#334154;--primary:#75aef0;--primary-soft:#203955;--primary-foreground:#e3f0ff}}*{box-sizing:border-box}body{margin:0;color:var(--foreground);background:var(--background);font-family:var(--font-sans);line-height:1.6}a{color:var(--primary)}button,summary{font:inherit}.reader-page{max-width:1500px;margin:0 auto;padding:24px}.reader-header{padding-bottom:17px;border-bottom:1px solid var(--border)}.reader-kicker{margin:0;color:var(--muted-foreground);font-size:12px;font-weight:750;letter-spacing:.08em;text-transform:uppercase}.reader-header h1{margin:3px 0 0;font-size:clamp(25px,3vw,38px);letter-spacing:-.035em}.status{display:flex;flex-wrap:wrap;gap:9px 17px;margin:12px 0 0;color:var(--muted-foreground);font-size:12px}.status strong{color:var(--foreground)}.reader-layout{display:grid;grid-template-columns:minmax(0,1.45fr) minmax(300px,.75fr);gap:18px;margin-top:18px;align-items:start}.reader-primary,.core-secondary{min-width:0;border:1px solid var(--border);background:var(--card)}.reader-primary{padding:clamp(20px,3vw,36px)}.reader-primary>h2{margin:0 0 18px;font-size:13px;color:var(--muted-foreground);letter-spacing:.035em}.document-section{margin:21px 0}.document-section h2,.document-section h3,.document-section h4,.document-section h5,.document-section h6{margin:0 0 6px;line-height:1.35}.document-block{margin:10px 0}.document-block p{margin:6px 0;font-size:15px}.document-list{margin:6px 0;padding-left:1.5rem;font-size:15px}.document-list-item{margin:6px 0}.document-example,.document-note{padding:0 0 0 17px;border-left:3px solid var(--border)}.document-example h3,.document-note h3{font-size:15px}.logical-structure{padding:0 0 0 17px;border-left:3px solid var(--border)}.logical-structure summary{display:inline;font-size:13px;cursor:pointer}.references{display:flex;flex-wrap:wrap;gap:5px;margin-top:8px}.reference-link{display:inline-flex;align-items:baseline;gap:4px;padding:2px 6px;border:1px solid var(--border);background:var(--muted);font-family:var(--font-mono);font-size:10px;text-decoration:none}.reference-label{font-family:var(--font-sans);font-weight:650}.reference-kind,.target-kind{color:var(--muted-foreground);font-family:var(--font-sans);font-size:9px;font-weight:700;text-transform:uppercase}.identity{font-family:var(--font-mono);font-size:.82em;color:var(--muted-foreground);overflow-wrap:anywhere}.core-secondary{padding:14px}.core-secondary>h2{margin:0 0 10px;font-size:14px}.core-surface{margin:10px 0;border-top:1px solid var(--border);padding-top:10px}.core-surface>summary{cursor:pointer;color:var(--foreground);font-weight:750}.core-surface:focus-visible,.core-surface>summary:focus-visible,a:focus-visible{outline:2px solid var(--primary);outline-offset:2px}.containment-tree,.prose-targets,.claim-list,.flow-list,.structure-list{margin:8px 0;padding-left:1.25rem}.containment-tree{list-style:none;padding-left:0}.containment-tree ul{list-style:none;margin:5px 0 0;padding-left:16px;border-left:1px solid var(--border)}.containment-tree li{margin:6px 0}.containment-link{display:inline-block;padding:3px 6px;text-decoration:none}.core-step,.flow-step,.structure-step{margin:12px 0;padding:10px;border:1px solid var(--border);background:var(--background)}.core-step h3,.flow-step h3,.structure-step h3{margin:0 0 7px;font-size:13px}.core-step h3 a,.flow-step h3 a,.structure-step h3 a{text-decoration:none}.step-pattern{display:inline-flex;margin-left:4px;padding:2px 6px;border:1px solid var(--border);border-radius:999px;background:var(--primary-soft);color:var(--primary-foreground);font-size:10px;font-weight:750}.prose-targets{font-size:12px}.prose-targets li,.claim-list li,.flow-list li,.structure-list li{margin:5px 0}.prose-targets a{text-decoration:none}.edge-mark{color:var(--primary);font-weight:850;font-size:18px;line-height:1}.type-wording{color:var(--muted-foreground);font-weight:650}.structure-tag{display:inline-flex;align-items:center;padding:2px 6px;border:1px solid var(--border);border-radius:999px;background:var(--primary-soft);color:var(--primary-foreground);font-size:10px;font-weight:750}.structure-relations{display:grid;gap:8px}.structure-relation{display:grid;grid-template-columns:minmax(0,1fr) auto minmax(0,1fr);align-items:center;gap:8px;padding:7px;border:1px solid var(--border)}.structure-node{min-width:0}.structure-node a{font-weight:700}.structure-node small{display:block;color:var(--muted-foreground);font-size:10px}.structure-unconnected{margin:9px 0}.structure-unconnected li{list-style:none;margin:5px 0}.flow-list{font-size:12px}.flow-list a{font-weight:650;text-decoration:none}.flow-transition{display:grid;grid-template-columns:minmax(0,1fr) auto minmax(0,1fr);align-items:center;gap:7px}.flow-transition .type-wording{text-align:center}.audit{margin-top:10px;color:var(--muted-foreground);font-size:11px}.audit summary{cursor:pointer;color:var(--foreground);font-weight:700}.audit dl{display:grid;grid-template-columns:max-content minmax(0,1fr);gap:.3rem .6rem}.audit dd{margin:0;overflow-wrap:anywhere}@media screen and (min-width:851px){.core-secondary{position:sticky;top:16px;max-height:calc(100vh - 32px);overflow-y:auto}}@media (max-width:850px){.reader-page{padding:14px}.reader-layout{grid-template-columns:1fr;margin-top:16px}.core-secondary{position:static;max-height:none;overflow:visible}}@media print{.reader-page{max-width:none}.core-surface[open]{}a{color:inherit}.core-secondary{position:static;max-height:none;overflow:visible}}
       |</style></head><body><main id="document-reader" class="reader-page" data-document-id="${_html(description.id)}" data-document-identity="${_html(validated.documentIdentity)}" data-core-id="${_html(core.core.id)}" data-core-identity="${_html(validated.coreIdentity)}" data-output-identity="${_html(outputidentity)}"><header class="reader-header"><p class="reader-kicker">${_html(chrome.pageHeading)}</p><h1>${_html(document.title)}</h1><div id="status-region" class="status" aria-label="${_html(chrome.statusHeading)}"><span><strong>${_html(chrome.coverageComplete)}</strong></span><span><strong>${_html(chrome.currentSources)}</strong></span><span><strong>${_html(chrome.admittedState)}</strong></span><span><strong>${_html(chrome.noUnresolvedReferences)}</strong></span></div></header><div class="reader-layout"><article id="prose-region" class="reader-primary" aria-label="${_html(chrome.proseHeading)}" aria-describedby="status-region"><h2>${_html(chrome.proseHeading)}</h2><div id="document-content" class="reader-content">$sections</div></article><aside id="core-secondary" class="core-secondary" aria-label="${_html(chrome.containmentHeading)}"><h2>${_html(chrome.containmentHeading)}</h2>$containment<details id="correspondence-surface" class="core-surface" data-core-surface="correspondence" data-reader-surface="correspondence"><summary>${_html(chrome.proseHeading)}</summary>$correspondence</details><details id="flow-surface" class="core-surface" data-core-surface="flow" data-reader-surface="flow"><summary><span class="edge-mark" aria-hidden="true">⇢</span> ${_html(chrome.flowHeading)}</summary>$flows</details><details id="structure-surface" class="core-surface" data-core-surface="structure" data-reader-surface="structure"><summary><span class="edge-mark" aria-hidden="true">→</span> ${_html(chrome.structureHeading)}</summary>$structures</details><details class="audit"><summary>${_html(chrome.identitiesHeading)}</summary><dl><dt>${_html(chrome.coreIdentityLabel)}</dt><dd>${_html(validated.coreIdentity)}</dd><dt>${_html(chrome.documentIdentityLabel)}</dt><dd>${_html(validated.documentIdentity)}</dd><dt>${_html(chrome.outputIdentityLabel)}</dt><dd data-output-identity="${_html(outputidentity)}">${_html(outputidentity)}</dd></dl></details></aside></div></main></body></html>""".stripMargin
  }

  private def _section(
    section: CozyDocumentDescriptionV2.Section,
    vocabulary: CozyDocumentConfirmationProjectionV2.Vocabulary,
    labels: Map[String, String],
    nodelabels: Map[String, String],
    core: CozyDocumentLogicTree.ValidatedCore,
    level: Int
  ): String = {
    val heading = math.min(6, level)
    val blocks = section.blocks.map(block => _block(block, vocabulary, labels, nodelabels, core)).mkString
    val children = section.sections.map(child => _section(child, vocabulary, labels, nodelabels, core, level + 1)).mkString
    val attributes = _reference_attributes(section.coreRefs) + s""" data-document-kind="section" data-document-id="${_html(section.id)}""""
    s"""<section id="document-section-${_html(section.id)}" class="document-section" data-reference-target="true"$attributes><h$heading>${_html(section.heading)}</h$heading>$blocks$children</section>"""
  }

  private def _block(
    block: CozyDocumentDescriptionV2.Block,
    vocabulary: CozyDocumentConfirmationProjectionV2.Vocabulary,
    labels: Map[String, String],
    nodelabels: Map[String, String],
    core: CozyDocumentLogicTree.ValidatedCore
  ): String = block match {
    case value: CozyDocumentDescriptionV2.Paragraph =>
      val attributes = _reference_attributes(value.coreRefs) + s""" data-document-kind="block" data-document-id="${_html(value.id)}""""
      s"""<div id="document-block-${_html(value.id)}" class="document-block document-paragraph" data-reference-target="true"$attributes><p>${_html(value.text)}</p>${_reference_links(value.coreRefs, labels, nodelabels, core, vocabulary)}</div>"""
    case value: CozyDocumentDescriptionV2.ListBlock =>
      val attributes = _reference_attributes(value.coreRefs) + s""" data-document-kind="block" data-document-id="${_html(value.id)}""""
      val items = value.items.map(item => _list_item(item, labels, nodelabels, core, vocabulary)).mkString
      s"""<div id="document-block-${_html(value.id)}" class="document-block document-list-block" data-reference-target="true"$attributes><ul class="document-list">$items</ul>${_reference_links(value.coreRefs, labels, nodelabels, core, vocabulary)}</div>"""
    case value: CozyDocumentDescriptionV2.Example =>
      val attributes = _reference_attributes(value.coreRefs) + s""" data-document-kind="block" data-document-id="${_html(value.id)}""""
      s"""<article id="document-block-${_html(value.id)}" class="document-block document-example" data-reference-target="true"$attributes><h3>${_html(value.title)}</h3><p>${_html(value.text)}</p>${_reference_links(value.coreRefs, labels, nodelabels, core, vocabulary)}</article>"""
    case value: CozyDocumentDescriptionV2.Note =>
      val attributes = _reference_attributes(value.coreRefs) + s""" data-document-kind="block" data-document-id="${_html(value.id)}""""
      s"""<aside id="document-block-${_html(value.id)}" class="document-block document-note" data-reference-target="true"$attributes><h3>${_html(value.title)}</h3><p>${_html(value.text)}</p>${_reference_links(value.coreRefs, labels, nodelabels, core, vocabulary)}</aside>"""
    case value: CozyDocumentDescriptionV2.LogicalStructure =>
      val refs = CozyDocumentDescriptionV2.References(Vector(value.stepRef), Vector.empty, Vector.empty, Vector.empty, Vector.empty)
      val attributes = _reference_attributes(refs) + s""" data-document-kind="block" data-document-id="${_html(value.id)}" data-step-ref="${_html(value.stepRef)}""""
      val step = core.stepsById(value.stepRef)
      s"""<details id="document-block-${_html(value.id)}" class="document-block logical-structure" data-reference-target="true"$attributes><summary>${_html(vocabulary.chrome.logicalStructureReference)}: ${_html(labels(value.stepRef))} ${_pattern_tag(step.structure.pattern, vocabulary)}</summary>${_reference_links(refs, labels, nodelabels, core, vocabulary)}</details>"""
  }

  private def _list_item(
    item: CozyDocumentDescriptionV2.ListItem,
    labels: Map[String, String],
    nodelabels: Map[String, String],
    core: CozyDocumentLogicTree.ValidatedCore,
    vocabulary: CozyDocumentConfirmationProjectionV2.Vocabulary
  ): String = {
    val attributes = _reference_attributes(item.coreRefs) + s""" data-document-kind="list-item" data-document-id="${_html(item.id)}""""
    s"""<li id="document-list-item-${_html(item.id)}" class="document-list-item" data-reference-target="true"$attributes>${_html(item.text)}${_reference_links(item.coreRefs, labels, nodelabels, core, vocabulary)}</li>"""
  }

  private def _containment_surface(
    root: CozyDocumentLogicTree.Step,
    labels: Map[String, String],
    chrome: CozyDocumentConfirmationProjectionV2.Chrome
  ): String =
    s"""<details id="containment-surface" class="core-surface" data-core-surface="containment" data-reader-surface="containment"><summary>${_html(chrome.containmentHeading)}</summary><nav aria-label="${_html(chrome.containmentHeading)}"><ul class="containment-tree">${_containment(root, labels)}</ul></nav></details>"""

  private def _containment(step: CozyDocumentLogicTree.Step, labels: Map[String, String]): String = {
    val children = if (step.steps.isEmpty) "" else step.steps.map(value => _containment(value, labels)).mkString("<ul>", "", "</ul>")
    s"""<li data-core-parent="${_html(step.id)}"><a class="containment-link" href="#core-step-${_html(step.id)}" data-core-step-ref="${_html(step.id)}">${_html(labels(step.id))}</a>$children</li>"""
  }

  private def _correspondence_step(
    step: CozyDocumentLogicTree.Step,
    targets: Vector[DocumentTarget],
    labels: Map[String, String],
    vocabulary: CozyDocumentConfirmationProjectionV2.Vocabulary
  ): String = {
    val steptargets = targets.filter(value => value.refs.steps.contains(step.id))
    val targetlinks = steptargets.map { target =>
      s"""<li data-prose-target-kind="${_html(target.kind)}" data-prose-target-id="${_html(target.id)}"><a href="#document-${_html(target.kind)}-${_html(target.id)}"><span class="target-kind">${_html(target.kind)}</span> ${_html(target.label)} <span class="identity">${_html(target.id)}</span></a></li>"""
    }.mkString
    val claims = step.claims.map(claim => s"""<li id="core-claim-${_html(claim.id)}" data-core-claim-id="${_html(claim.id)}"><span class="reference-kind">claim</span> <span class="identity">${_html(claim.id)}</span></li>""").mkString
    val chrome = vocabulary.chrome
    s"""<article id="core-step-${_html(step.id)}" class="core-step" data-core-step-id="${_html(step.id)}"><h3><a href="#core-step-${_html(step.id)}">${_html(labels(step.id))}</a> ${_pattern_tag(step.structure.pattern, vocabulary)}</h3><details class="step-prose-targets"><summary>${_html(chrome.proseHeading)}</summary><ul class="prose-targets">$targetlinks</ul></details><details class="audit"><summary>${_html(chrome.identitiesHeading)}</summary><ul class="claim-list">$claims</ul></details></article>"""
  }

  private def _flow_step(
    step: CozyDocumentLogicTree.Step,
    labels: Map[String, String],
    vocabulary: CozyDocumentConfirmationProjectionV2.Vocabulary
  ): String = {
    val transitions = step.flow.transitions.map { transition =>
      s"""<li id="core-transition-${_html(transition.id)}" class="flow-transition" data-core-flow-id="${_html(step.flow.id)}" data-core-transition-id="${_html(transition.id)}" data-core-transition-type="${_html(transition.relationType)}" data-core-from-step-id="${_html(transition.fromStepId)}" data-core-to-step-id="${_html(transition.toStepId)}"><a href="#core-step-${_html(transition.fromStepId)}">${_html(labels(transition.fromStepId))}</a><span class="type-wording"><span class="edge-mark" aria-hidden="true">⇢</span> ${_html(vocabulary.flowTypes(transition.relationType))}</span><a href="#core-step-${_html(transition.toStepId)}">${_html(labels(transition.toStepId))}</a></li>"""
    }.mkString
    val content = if (step.flow.transitions.isEmpty) s"""<p data-empty-flow="true">${_html(vocabulary.chrome.noDirectChildFlowTransitions)}</p>""" else s"""<ul class="flow-list">$transitions</ul>"""
    s"""<article id="core-flow-${_html(step.flow.id)}" class="flow-step" data-core-step-ref="${_html(step.id)}" data-core-step-id="${_html(step.id)}"><h3><a href="#core-step-${_html(step.id)}">${_html(labels(step.id))}</a> <span class="identity" data-core-flow-id="${_html(step.flow.id)}">${_html(step.flow.id)}</span></h3>$content</article>"""
  }

  private def _structure_step(
    step: CozyDocumentLogicTree.Step,
    labels: Map[String, String],
    nodelabels: Map[String, String],
    vocabulary: CozyDocumentConfirmationProjectionV2.Vocabulary
  ): String = {
    val structure = step.structure
    val nodes = structure.nodes.map { node =>
      s"""<li data-core-node-id="${_html(node.id)}" data-core-node-role="${_html(node.role)}"><a id="core-node-${_html(node.id)}" href="#core-node-${_html(node.id)}">${_html(nodelabels(node.id))}</a> <small>${_html(vocabulary.chrome.nodeRoleHeading)}: <span class="type-wording">${_html(vocabulary.nodeRoles(node.role))}</span></small> <span class="identity">${_html(node.id)}</span></li>"""
    }.mkString("<ul class=\"structure-list structure-node-inventory\">", "", "</ul>")
    val relationnodes = structure.relations.flatMap(value => Vector(value.from, value.to)).toSet
    val relations = structure.relations.map { relation =>
      val from = structure.nodes.find(_.id == relation.from).get
      val to = structure.nodes.find(_.id == relation.to).get
      s"""<div id="core-relation-${_html(relation.id)}" class="structure-relation" data-core-relation-id="${_html(relation.id)}" data-core-relation-type="${_html(relation.relationType)}" data-core-from="${_html(relation.from)}" data-core-to="${_html(relation.to)}"><span class="structure-node"><a href="#core-node-${_html(from.id)}">${_html(nodelabels(from.id))}</a><small>${_html(vocabulary.nodeRoles(from.role))}</small></span><span class="type-wording"><span class="edge-mark" aria-hidden="true">→</span> ${_html(vocabulary.relationTypes(relation.relationType))}</span><span class="structure-node"><a href="#core-node-${_html(to.id)}">${_html(nodelabels(to.id))}</a><small>${_html(vocabulary.nodeRoles(to.role))}</small></span><span class="identity">${_html(relation.id)}</span></div>"""
    }.mkString
    val unconnected = structure.nodes.filterNot(node => relationnodes.contains(node.id)).map { node =>
      s"""<li><a href="#core-node-${_html(node.id)}">${_html(nodelabels(node.id))}</a> <span class="identity">${_html(node.id)}</span></li>"""
    }.mkString
    val unconnectedcontent = if (unconnected.isEmpty) "" else s"""<ul class="structure-list structure-unconnected">$unconnected</ul>"""
    s"""<article class="structure-step" data-core-step-ref="${_html(step.id)}" data-core-step-id="${_html(step.id)}" data-logical-pattern="${_html(structure.pattern)}"><h3><a href="#core-step-${_html(step.id)}">${_html(labels(step.id))}</a> ${_pattern_tag(structure.pattern, vocabulary)}</h3><h4>${_html(vocabulary.chrome.relationsHeading)}</h4><div class="structure-relations">$relations</div><h4>${_html(vocabulary.chrome.nodesHeading)}</h4>$unconnectedcontent$nodes</article>"""
  }

  private def _pattern_tag(pattern: String, vocabulary: CozyDocumentConfirmationProjectionV2.Vocabulary): String =
    s"""<span class="step-pattern" data-logical-pattern="${_html(pattern)}" title="${_html(vocabulary.chrome.logicalPatternHeading)}">${_html(vocabulary.logicalPatterns(pattern))}</span>"""

  private def _reference_links(
    refs: CozyDocumentDescriptionV2.References,
    labels: Map[String, String],
    nodelabels: Map[String, String],
    core: CozyDocumentLogicTree.ValidatedCore,
    vocabulary: CozyDocumentConfirmationProjectionV2.Vocabulary
  ): String = {
    val groups = Vector(
      "steps" -> refs.steps,
      "claims" -> refs.claims,
      "nodes" -> refs.nodes,
      "relations" -> refs.relations,
      "flows" -> refs.flows
    )
    val links = groups.flatMap { case (kind, values) => values.map(value => _reference_link(kind, value, labels, nodelabels, core, vocabulary)) }
    if (links.isEmpty) "" else links.mkString(s"""<nav class="references" aria-label="${_html(vocabulary.chrome.structureHeading)}">""", "", "</nav>")
  }

  private def _reference_link(
    kind: String,
    value: String,
    labels: Map[String, String],
    nodelabels: Map[String, String],
    core: CozyDocumentLogicTree.ValidatedCore,
    vocabulary: CozyDocumentConfirmationProjectionV2.Vocabulary
  ): String = {
    val anchor = kind match {
      case "steps" => s"core-step-$value"
      case "claims" => s"core-claim-$value"
      case "nodes" => s"core-node-$value"
      case "relations" => s"core-relation-$value"
      case "flows" => s"core-flow-$value"
    }
    val label = kind match {
      case "steps" => labels(value)
      case "claims" => value
      case "nodes" => nodelabels(value)
      case "relations" => vocabulary.relationTypes(core.relationsById(value).relationType)
      case "flows" => value
    }
    s"""<a class="reference-link" data-reference-kind="${_html(kind)}" data-reference-id="${_html(value)}" href="#${_html(anchor)}"><span class="reference-kind">${_html(kind.dropRight(1))}</span> <span class="reference-label">${_html(label)}</span> <span class="identity">${_html(value)}</span></a>"""
  }

  private def _reference_attributes(refs: CozyDocumentDescriptionV2.References): String =
    s""" data-core-steps="${_html(refs.steps.mkString(" "))}" data-core-claims="${_html(refs.claims.mkString(" "))}" data-core-nodes="${_html(refs.nodes.mkString(" "))}" data-core-relations="${_html(refs.relations.mkString(" "))}" data-core-flows="${_html(refs.flows.mkString(" "))}"""

  private def _document_targets(sections: Vector[CozyDocumentDescriptionV2.Section], labels: Map[String, String]): Vector[DocumentTarget] =
    sections.flatMap { section =>
      val sectiontarget = Vector(DocumentTarget("section", section.id, section.heading, section.coreRefs))
      val blocktargets = section.blocks.flatMap {
        case value: CozyDocumentDescriptionV2.Paragraph => Vector(DocumentTarget("block", value.id, value.text, value.coreRefs))
      case value: CozyDocumentDescriptionV2.ListBlock =>
          DocumentTarget("block", value.id, value.id, value.coreRefs) +: value.items.map(item => DocumentTarget("list-item", item.id, item.text, item.coreRefs))
        case value: CozyDocumentDescriptionV2.Example => Vector(DocumentTarget("block", value.id, value.title, value.coreRefs))
        case value: CozyDocumentDescriptionV2.Note => Vector(DocumentTarget("block", value.id, value.title, value.coreRefs))
        case value: CozyDocumentDescriptionV2.LogicalStructure =>
          Vector(DocumentTarget("block", value.id, labels(value.stepRef), CozyDocumentDescriptionV2.References(Vector(value.stepRef), Vector.empty, Vector.empty, Vector.empty, Vector.empty)))
      }
      sectiontarget ++ blocktargets ++ _document_targets(section.sections, labels)
    }

  private def _validate_vocabulary(validated: CozyDocumentDescriptionV2.ValidatedDocument, vocabulary: CozyDocumentConfirmationProjectionV2.Vocabulary): Unit = {
    val chrome = vocabulary.chrome
    _required(Vector(
      chrome.pageHeading, chrome.statusHeading, chrome.coverageComplete, chrome.currentSources, chrome.admittedState,
      chrome.noUnresolvedReferences, chrome.containmentHeading, chrome.flowHeading, chrome.structureHeading,
      chrome.proseHeading, chrome.logicalPatternHeading, chrome.nodesHeading, chrome.nodeRoleHeading,
      chrome.relationsHeading, chrome.noDirectChildFlowTransitions, chrome.logicalStructureReference,
      chrome.identitiesHeading, chrome.coreIdentityLabel, chrome.documentIdentityLabel, chrome.outputIdentityLabel
    ), "chrome")
    _required_map(vocabulary.logicalPatterns, validated.core.depthFirstSteps.map(_.structure.pattern).toSet, "logical pattern")
    _required_map(vocabulary.nodeRoles, validated.core.depthFirstSteps.flatMap(_.structure.nodes.map(_.role)).toSet, "node role")
    _required_map(vocabulary.relationTypes, validated.core.depthFirstSteps.flatMap(_.structure.relations.map(_.relationType)).toSet, "Relation type")
    _required_map(vocabulary.flowTypes, validated.core.depthFirstSteps.flatMap(_.flow.transitions.map(_.relationType)).toSet, "Flow type")
  }

  private def _required(values: Vector[String], category: String): Unit =
    values.zipWithIndex.find { case (value, _) => !_usable(value) }.foreach { case (_, index) => _fail("READER_V2_VOCABULARY", s"missing or blank $category wording at index $index") }

  private def _required_map(values: Map[String, String], required: Set[String], category: String): Unit =
    required.toVector.sorted.find(value => !_usable(values.getOrElse(value, null))).foreach(value => _fail("READER_V2_VOCABULARY", s"missing or blank $category wording: $value"))

  private def _usable(value: String): Boolean = value != null && value.nonEmpty && value == value.trim

  private def _identity(value: String): String =
    "sha256:" + MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)).map(byte => f"${byte & 0xff}%02x").mkString

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
}
