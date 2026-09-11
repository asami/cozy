package cozy.document

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/*
 * @since   Sep. 11, 2026
 * @version Sep. 11, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyDocumentLogicTreeProjection {
  final case class Rendered(kind: String, html: String, identity: String)

  private val _overview_kind = "overview"
  private val _slides_kind = "slides"

  def overview(validated: CozyDocumentLogicTree.Validated): Rendered =
    _render(_overview_kind, _overview_html(validated))

  def slides(validated: CozyDocumentLogicTree.Validated): Rendered =
    _render(_slides_kind, _slides_html(validated))

  private def _render(kind: String, html: String): Rendered =
    Rendered(kind, html, "sha256:" + _sha256(html.getBytes(StandardCharsets.UTF_8)))

  private def _overview_html(validated: CozyDocumentLogicTree.Validated): String = {
    val chrome = validated.format.chrome
    val content =
      s"""<main id="logic-tree-overview" data-core-id="${_html(validated.core.id)}" data-core-identity="${_html(validated.coreIdentity)}">
         |<header class="document-heading"><h1>${_html(validated.titlesById(validated.core.root.id))}</h1><p>${_html(chrome.overviewLabel)} · ${_html(validated.format.locale)}</p></header>
         |${_overview_step(validated, validated.core.root, Vector.empty)}
         |</main>""".stripMargin
    _page(
      s"${chrome.overviewDocumentTitle} — ${validated.titlesById(validated.core.root.id)}",
      validated.format.locale,
      "logic-tree-overview-page",
      content
    )
  }

  private def _overview_step(
    validated: CozyDocumentLogicTree.Validated,
    step: CozyDocumentLogicTree.Step,
    ancestors: Vector[CozyDocumentLogicTree.Step]
  ): String = {
    val children =
      if (step.steps.isEmpty) ""
      else s"""<div class="nested-steps">${step.steps.map(child => _overview_step(validated, child, ancestors :+ step)).mkString}</div>"""
    s"""<article class="logic-tree-step-card" id="step-${_html(step.id)}" data-step-id="${_html(step.id)}" data-depth="${ancestors.size}">
       |<header><p class="step-path">${_html(_path_title(validated, ancestors :+ step))}</p><h2>${_html(validated.titlesById(step.id))}</h2><p class="semantic-role">${_html(step.semanticRole)}</p></header>
       |${_claims(validated, step)}
       |${_structure(validated, step)}
       |${_flow(validated, step, "step-")}
       |$children
       |</article>""".stripMargin
  }

  private def _slides_html(validated: CozyDocumentLogicTree.Validated): String = {
    val chrome = validated.format.chrome
    val pages = _step_paths(validated.core.root).zipWithIndex.map { case ((step, ancestors), index) =>
      _slide_page(validated, step, ancestors, index, validated.depthFirstSteps.size)
    }.mkString("\n")
    val content =
      s"""<main id="logic-tree-slides" data-core-id="${_html(validated.core.id)}" data-core-identity="${_html(validated.coreIdentity)}">
         |<header class="slide-deck-heading"><h1>${_html(validated.titlesById(validated.core.root.id))}</h1><p>${validated.depthFirstSteps.size} ${_html(chrome.pageCountLabel)} · ${_html(validated.format.locale)}</p></header>
         |$pages
         |</main>
         |<script>(function(){var pages=Array.prototype.slice.call(document.querySelectorAll('.slide-page'));function show(index){if(index>=0&&index<pages.length){pages[index].focus();location.hash=pages[index].id;}}document.addEventListener('keydown',function(event){var current=pages.indexOf(document.activeElement);if(current<0){current=pages.map(function(page){return page.id===location.hash.slice(1);}).indexOf(true);}if(event.key==='ArrowLeft'){event.preventDefault();show(Math.max(0,current-1));}if(event.key==='ArrowRight'){event.preventDefault();show(Math.min(pages.length-1,current+1));}});})();</script>""".stripMargin
    _page(
      s"${chrome.slidesDocumentTitle} — ${validated.titlesById(validated.core.root.id)}",
      validated.format.locale,
      "logic-tree-slides-page",
      content
    )
  }

  private def _slide_page(
    validated: CozyDocumentLogicTree.Validated,
    step: CozyDocumentLogicTree.Step,
    ancestors: Vector[CozyDocumentLogicTree.Step],
    index: Int,
    count: Int
  ): String = {
    val chrome = validated.format.chrome
    val previous = if (index == 0) s"""<span class="navigation-disabled">${_html(chrome.previous)}</span>""" else
      s"""<a class="previous" rel="prev" href="#slide-${_html(validated.depthFirstSteps(index - 1).id)}">${_html(chrome.previous)}</a>"""
    val next = if (index + 1 == count) s"""<span class="navigation-disabled">${_html(chrome.next)}</span>""" else
      s"""<a class="next" rel="next" href="#slide-${_html(validated.depthFirstSteps(index + 1).id)}">${_html(chrome.next)}</a>"""
    s"""<section class="slide-page" id="slide-${_html(step.id)}" tabindex="-1" data-step-id="${_html(step.id)}" data-page-number="${index + 1}">
       |<header><p class="slide-counter">${index + 1} / $count</p><p class="ancestor-context">${_html(_path_title(validated, ancestors :+ step))}</p><h2>${_html(validated.titlesById(step.id))}</h2></header>
       |<div class="slide-content">${_claims(validated, step)}${_structure(validated, step)}${_children(validated, step)}${_flow(validated, step, "slide-")}</div>
       |<nav class="slide-navigation" aria-label="${_html(chrome.navigationAriaLabel)}">$previous <a class="overview-link" href="#logic-tree-slides">${_html(chrome.deck)}</a> $next</nav>
       |</section>""".stripMargin
  }

  private def _claims(validated: CozyDocumentLogicTree.Validated, step: CozyDocumentLogicTree.Step): String = {
    val values = step.claims.map(claim => s"""<li data-claim-id="${_html(claim.id)}">${_html(validated.claimsById(claim.id))}</li>""").mkString
    val chrome = validated.format.chrome
    s"""<section class="step-claims" aria-label="${_html(chrome.claimsHeading)} ${_html(step.id)}"><h3>${_html(chrome.claimsHeading)}</h3><ul>$values</ul></section>"""
  }

  private def _structure(validated: CozyDocumentLogicTree.Validated, step: CozyDocumentLogicTree.Step): String = {
    val nodes = step.structure.nodes.map { node =>
      s"""<li class="logic-node" data-node-id="${_html(node.id)}"><span>${_html(validated.labelsById(node.id))}</span><code>${_html(node.role)}</code></li>"""
    }.mkString
    val relations = step.structure.relations.map { relation =>
      val from = validated.labelsById(relation.from)
      val to = validated.labelsById(relation.to)
      s"""<li class="typed-relation" data-relation-id="${_html(relation.id)}"><span>${_html(from)}</span><code>${_html(relation.relationType)}</code><span>${_html(to)}</span></li>"""
    }.mkString
    s"""<section class="local-structure" id="structure-${_html(step.id)}" data-pattern="${_html(step.structure.pattern)}"><h3>${_html(validated.format.chrome.localStructureHeading)} · ${_html(step.structure.pattern)}</h3><ol class="logic-nodes">$nodes</ol><ol class="typed-relations">$relations</ol></section>"""
  }

  private def _children(validated: CozyDocumentLogicTree.Validated, step: CozyDocumentLogicTree.Step): String = {
    val chrome = validated.format.chrome
    val values = if (step.steps.isEmpty) s"<li>${_html(chrome.noDirectChildren)}</li>" else step.steps.map { child =>
      s"""<li data-child-step-id="${_html(child.id)}"><a href="#slide-${_html(child.id)}">${_html(validated.titlesById(child.id))}</a></li>"""
    }.mkString
    s"""<section class="direct-children" aria-label="${_html(chrome.directChildrenHeading)} ${_html(step.id)}"><h3>${_html(chrome.directChildrenHeading)}</h3><ul>$values</ul></section>"""
  }

  private def _flow(validated: CozyDocumentLogicTree.Validated, step: CozyDocumentLogicTree.Step, targetprefix: String): String = {
    val chrome = validated.format.chrome
    val values = if (step.flow.transitions.isEmpty) s"<li>${_html(chrome.noDirectChildFlowTransitions)}</li>" else step.flow.transitions.map { transition =>
      s"""<li class="flow-edge" data-transition-id="${_html(transition.id)}"><a href="#${_html(targetprefix)}${_html(transition.fromStepId)}">${_html(validated.titlesById(transition.fromStepId))}</a><code>${_html(transition.relationType)}</code><a href="#${_html(targetprefix)}${_html(transition.toStepId)}">${_html(validated.titlesById(transition.toStepId))}</a></li>"""
    }.mkString
    s"""<section class="direct-child-flow" id="flow-${_html(step.id)}" aria-label="${_html(chrome.directChildFlowHeading)} ${_html(step.id)}"><h3>${_html(chrome.directChildFlowHeading)}</h3><ol>$values</ol></section>"""
  }

  private def _step_paths(root: CozyDocumentLogicTree.Step): Vector[(CozyDocumentLogicTree.Step, Vector[CozyDocumentLogicTree.Step])] = {
    def _collect_(step: CozyDocumentLogicTree.Step, ancestors: Vector[CozyDocumentLogicTree.Step]): Vector[(CozyDocumentLogicTree.Step, Vector[CozyDocumentLogicTree.Step])] =
      (step -> ancestors) +: step.steps.flatMap(child => _collect_(child, ancestors :+ step))
    _collect_(root, Vector.empty)
  }

  private def _path_title(validated: CozyDocumentLogicTree.Validated, steps: Vector[CozyDocumentLogicTree.Step]): String =
    steps.map(step => validated.titlesById(step.id)).mkString(" › ")

  private def _page(title: String, locale: String, pageclass: String, content: String): String =
    s"""<!doctype html>
       |<html lang="${_html(locale)}"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>${_html(title)}</title><style>
       |:root{color-scheme:light;font-family:system-ui,sans-serif;line-height:1.45;background:#f5f7fb;color:#172033}body{margin:0;padding:2rem}.document-heading,.slide-deck-heading{max-width:72rem;margin:0 auto 1rem}.logic-tree-step-card{background:#fff;border:1px solid #c8d1e0;border-radius:.75rem;margin:1rem auto;padding:1rem;max-width:72rem;box-shadow:0 .1rem .4rem #17203318}.nested-steps{border-left:.35rem solid #5477aa;margin:1rem 0 0 1rem;padding-left:1rem}.logic-tree-step-card h2,.slide-page h2{margin:.1rem 0}.step-path,.ancestor-context,.semantic-role,.slide-counter{color:#4e5f7c;font-size:.9rem}.local-structure,.direct-child-flow,.direct-children,.step-claims{margin-top:.8rem}.logic-nodes,.typed-relations,.direct-child-flow ol{display:flex;flex-wrap:wrap;gap:.5rem;padding:0;list-style:none}.logic-node,.typed-relation,.flow-edge{border:1px solid #b9c8dc;border-radius:.35rem;padding:.35rem .55rem;background:#f9fbff}.typed-relation code,.flow-edge code,.logic-node code{margin:0 .45rem;color:#875a00}.slide-page{box-sizing:border-box;width:min(100%,96rem);aspect-ratio:16/9;min-height:32rem;margin:1rem auto;padding:2rem;background:#fff;border:1px solid #c8d1e0;border-radius:.5rem;display:flex;flex-direction:column}.slide-content{display:grid;grid-template-columns:1fr 1fr;gap:0 2rem;overflow:auto}.slide-navigation{margin-top:auto;display:flex;justify-content:space-between;gap:1rem}.navigation-disabled{color:#71809a}@media (max-width:50rem){body{padding:.5rem}.slide-page{aspect-ratio:auto;min-height:0}.slide-content{grid-template-columns:1fr}}@media print{body{padding:0;background:#fff}.slide-deck-heading,.slide-navigation{display:none}.slide-page{width:100%;height:100vh;min-height:0;margin:0;border:0;border-radius:0;break-after:page;page-break-after:always}.slide-page:last-child{break-after:auto;page-break-after:auto}}</style></head><body class="$pageclass">$content</body></html>""".stripMargin

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

  private def _sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(value => f"${value & 0xff}%02x").mkString
}
