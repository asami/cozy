package cozy.document

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/*
 * @since   Sep. 12, 2026
 * @version Sep. 12, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyDocumentDescriptionProjection {
  final case class Rendered(html: String, identity: String)

  def render(validated: CozyDocumentDescription.ValidatedDocument): Rendered = {
    val html = _page(validated)
    Rendered(html, "sha256:" + _sha256(html.getBytes(StandardCharsets.UTF_8)))
  }

  private def _page(validated: CozyDocumentDescription.ValidatedDocument): String = {
    val document = validated.description.document
    val sections = document.sections.map(section => _section(validated, section, 2)).mkString
    s"""<!doctype html>
       |<html lang="${_html(validated.description.locale)}"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>${_html(document.title)}</title><style>
       |:root{color-scheme:light;font-family:system-ui,sans-serif;line-height:1.7;color:#1c2433;background:#f4f7fb}body{margin:0;padding:2rem}.document-description{max-width:54rem;margin:0 auto;background:#fff;padding:clamp(1.5rem,5vw,4rem);box-shadow:0 .1rem .8rem #17203320}.document-heading{border-bottom:.25rem solid #38618d;margin-bottom:2rem}.document-heading h1{line-height:1.25;margin-bottom:.35rem}.traceability{color:#526174;font-size:.8rem}.document-section{margin:2.3rem 0}.document-section h2,.document-section h3,.document-section h4,.document-section h5,.document-section h6{line-height:1.35;margin:0 0 .7rem}.document-block{margin:1.25rem 0}.document-list{padding-left:1.4rem}.document-example,.document-note{border-left:.35rem solid #38618d;padding:.8rem 1rem;background:#f4f8fc}.document-note{border-color:#9a6a14;background:#fff9e8}.document-example h3,.document-note h3{margin:.1rem 0 .4rem}.logical-structure-projection{margin:1.4rem 0;padding:1rem;border:1px solid #a8bfd4;background:#eef6fb}.logical-structure-projection h3{margin-top:0}.structure-list{margin:.45rem 0;padding-left:1.3rem}.structure-relation{display:inline-block;margin:.2rem .3rem .2rem 0;padding:.15rem .45rem;border:1px solid #b9c8dc;border-radius:.3rem;background:#fff}.reader-content{font-size:1.05rem}@media (max-width:45rem){body{padding:0}.document-description{padding:1.25rem;box-shadow:none}}@media print{body{padding:0;background:#fff}.document-description{max-width:none;padding:0;box-shadow:none}}</style></head>
       |<body><main class="document-description" data-document-id="${_html(validated.description.id)}" data-document-identity="${_html(validated.documentIdentity)}" data-core-id="${_html(validated.core.core.id)}" data-core-identity="${_html(validated.coreIdentity)}"><header class="document-heading"><h1>${_html(document.title)}</h1><p class="traceability">文書ID: ${_html(validated.description.id)} · 文書識別子: ${_html(validated.documentIdentity)} · Core ID: ${_html(validated.core.core.id)} · Core 識別子: ${_html(validated.coreIdentity)}</p></header><div class="reader-content">$sections</div></main></body></html>""".stripMargin
  }

  private def _section(validated: CozyDocumentDescription.ValidatedDocument, section: CozyDocumentDescription.Section, level: Int): String = {
    val headinglevel = math.min(level, 6)
    val blocks = section.blocks.map(block => _block(validated, block)).mkString
    val children = section.sections.map(child => _section(validated, child, level + 1)).mkString
    s"""<section class="document-section" id="section-${_html(section.id)}" data-section-id="${_html(section.id)}"${_references(section.coreRefs)}><h$headinglevel>${_html(section.heading)}</h$headinglevel>$blocks$children</section>"""
  }

  private def _block(validated: CozyDocumentDescription.ValidatedDocument, block: CozyDocumentDescription.Block): String = block match {
    case value: CozyDocumentDescription.Paragraph =>
      s"""<p class="document-block document-paragraph" data-block-id="${_html(value.id)}"${_references(value.coreRefs)}>${_html(value.text)}</p>"""
    case value: CozyDocumentDescription.ListBlock =>
      val items = value.items.map(item => s"""<li data-list-item-id="${_html(item.id)}"${_references(item.coreRefs)}>${_html(item.text)}</li>""").mkString
      s"""<ol class="document-block document-list" data-block-id="${_html(value.id)}"${_references(value.coreRefs)}>$items</ol>"""
    case value: CozyDocumentDescription.Example =>
      s"""<aside class="document-block document-example" data-block-id="${_html(value.id)}"${_references(value.coreRefs)}><h3>${_html(value.title)}</h3><p>${_html(value.text)}</p></aside>"""
    case value: CozyDocumentDescription.Note =>
      s"""<aside class="document-block document-note" data-block-id="${_html(value.id)}"${_references(value.coreRefs)}><h3>${_html(value.title)}</h3><p>${_html(value.text)}</p></aside>"""
    case value: CozyDocumentDescription.LogicalStructure => _logical_structure(validated, value)
  }

  private def _logical_structure(validated: CozyDocumentDescription.ValidatedDocument, block: CozyDocumentDescription.LogicalStructure): String = {
    val step = validated.core.stepsById(block.stepRef)
    val nodes = step.structure.nodes.map(node => s"""<li data-node-id="${_html(node.id)}">${_html(node.id)} <span class="traceability">(${_html(node.role)})</span></li>""").mkString
    val relations = step.structure.relations.map(relation => s"""<li class="structure-relation" data-relation-id="${_html(relation.id)}">${_html(relation.from)} → ${_html(relation.to)} <span class="traceability">(${_html(relation.relationType)})</span></li>""").mkString
    s"""<section class="logical-structure-projection" data-block-id="${_html(block.id)}" data-step-id="${_html(step.id)}"><h3>ローカル構造: ${_html(step.id)}</h3><p class="traceability">pattern: ${_html(step.structure.pattern)}</p><ol class="structure-list">$nodes</ol><ul class="structure-list">$relations</ul></section>"""
  }

  private def _references(refs: CozyDocumentDescription.References): String =
    s""" data-core-steps="${_html(refs.steps.mkString(" "))}" data-core-claims="${_html(refs.claims.mkString(" "))}" data-core-nodes="${_html(refs.nodes.mkString(" "))}" data-core-relations="${_html(refs.relations.mkString(" "))}" data-core-flows="${_html(refs.flows.mkString(" "))}""""

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
