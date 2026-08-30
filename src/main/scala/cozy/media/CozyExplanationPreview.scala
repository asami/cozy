package cozy.media

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path}
import java.security.MessageDigest

import scala.util.control.NonFatal

/*
 * @since   Aug. 30, 2026
 * @version Aug. 30, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyExplanationPreview {
  private final case class Config(
    plan: Path,
    composition: Path,
    projectionmap: Path,
    explanationcatalog: Path,
    presentationcatalog: Path,
    visualpageset: Path,
    save: Path,
    bindings: CozyExplanation.ResourceBindings
  )
  private final case class RenderedPage(
    step: CozyExplanation.PlanStep,
    mapping: CozyExplanationProjection.PresentationStepMapping,
    page: CozyVisualPage.Page
  )
  private final case class ValidatedPreview(
    composition: CozyExplanation.ValidatedComposition,
    projectionmap: CozyExplanationProjection.ValidatedProjectionMap,
    visualpages: CozyVisualPage.ValidatedDocument,
    pages: Vector[RenderedPage]
  )

  private val _schema = "cozy.explanation-preview.v1"
  private val _generator = "cozy media explanation preview"
  private val _renderer_profile = "cozy.explanation-preview.renderer.v1"
  private val _value_options = Set(
    "--composition", "--projection-map", "--explanation-catalog", "--presentation-catalog",
    "--visual-page-set", "--save", "--source", "--asset"
  )

  def execute(args: List[String]): String = {
    val config = _config(args)
    _validate_output(config.save)
    val validated = _validate(config)
    val html = _html(validated)
    val htmlidentity = _sha256(html.getBytes(StandardCharsets.UTF_8))
    val receipt = _receipt(validated, htmlidentity)
    CozyExplanationProjection._atomic_write(config.save, html)
    receipt
  }

  private def _config(args: List[String]): Config = args match {
    case plan :: rest if !plan.startsWith("--") =>
      var composition = Option.empty[Path]
      var projectionmap = Option.empty[Path]
      var explanationcatalog = Option.empty[Path]
      var presentationcatalog = Option.empty[Path]
      var visualpageset = Option.empty[Path]
      var save = Option.empty[Path]
      var sources = Map.empty[String, Path]
      var assets = Map.empty[String, Path]
      var remaining = rest
      while (remaining.nonEmpty) {
        remaining match {
          case option :: value :: tail if _value_options.contains(option) && !value.startsWith("--") =>
            option match {
              case "--composition" => composition = _single_path(composition, value, "$command.composition", option)
              case "--projection-map" => projectionmap = _single_path(projectionmap, value, "$command.projectionMap", option)
              case "--explanation-catalog" => explanationcatalog = _single_path(explanationcatalog, value, "$command.explanationCatalog", option)
              case "--presentation-catalog" => presentationcatalog = _single_path(presentationcatalog, value, "$command.presentationCatalog", option)
              case "--visual-page-set" => visualpageset = _single_path(visualpageset, value, "$command.visualPageSet", option)
              case "--save" => save = _single_path(save, value, "$command.save", option)
              case "--source" => sources = CozyExplanationProjection._add_binding(sources, value, "$command.source")
              case "--asset" => assets = CozyExplanationProjection._add_binding(assets, value, "$command.asset")
            }
            remaining = tail
          case option :: tail if option.startsWith("--") && option.contains("=") =>
            val index = option.indexOf('=')
            val name = option.take(index)
            val value = option.drop(index + 1)
            if (!_value_options.contains(name)) _fail("EXPLANATION_PREVIEW_COMMAND", "$command", s"unsupported option: $name")
            name match {
              case "--composition" => composition = _single_path(composition, value, "$command.composition", name)
              case "--projection-map" => projectionmap = _single_path(projectionmap, value, "$command.projectionMap", name)
              case "--explanation-catalog" => explanationcatalog = _single_path(explanationcatalog, value, "$command.explanationCatalog", name)
              case "--presentation-catalog" => presentationcatalog = _single_path(presentationcatalog, value, "$command.presentationCatalog", name)
              case "--visual-page-set" => visualpageset = _single_path(visualpageset, value, "$command.visualPageSet", name)
              case "--save" => save = _single_path(save, value, "$command.save", name)
              case "--source" => sources = CozyExplanationProjection._add_binding(sources, value, "$command.source")
              case "--asset" => assets = CozyExplanationProjection._add_binding(assets, value, "$command.asset")
            }
            remaining = tail
          case option :: _ if option.startsWith("--") =>
            _fail("EXPLANATION_PREVIEW_COMMAND", "$command", s"unsupported or valueless option: $option")
          case value :: _ =>
            _fail("EXPLANATION_PREVIEW_COMMAND", "$command", s"unexpected positional argument: $value")
        }
      }
      Config(
        CozyExplanationProjection._cli_path(plan, "$command.plan"),
        composition.getOrElse(_missing("--composition")),
        projectionmap.getOrElse(_missing("--projection-map")),
        explanationcatalog.getOrElse(_missing("--explanation-catalog")),
        presentationcatalog.getOrElse(_missing("--presentation-catalog")),
        visualpageset.getOrElse(_missing("--visual-page-set")),
        save.getOrElse(_missing("--save")),
        CozyExplanation.ResourceBindings(sources, assets)
      )
    case Nil => _fail("EXPLANATION_PREVIEW_COMMAND", "$command.plan", "missing direct Plan input")
    case option :: _ => _fail("EXPLANATION_PREVIEW_COMMAND", "$command.plan", s"Plan input must precede options: $option")
  }

  private def _single_path(current: Option[Path], value: String, path: String, option: String): Option[Path] = {
    if (current.nonEmpty) _fail("EXPLANATION_PREVIEW_COMMAND", path, s"$option may appear once")
    Some(CozyExplanationProjection._cli_path(value, path))
  }

  private def _validate(config: Config): ValidatedPreview = {
    val composition = CozyExplanation.loadComposition(
      config.composition,
      config.explanationcatalog,
      config.presentationcatalog,
      config.bindings
    )
    val projectionmap = CozyExplanationProjection.loadProjectionMap(
      config.projectionmap,
      config.composition,
      config.plan,
      config.explanationcatalog,
      config.presentationcatalog,
      config.bindings
    )
    val visualpages = try CozyVisualPage.load(config.visualpageset, config.presentationcatalog) catch {
      case NonFatal(error) => _stale("$command.visualPageSet", Option(error.getMessage).getOrElse("named VisualPageSet cannot be validated"))
    }
    val pageset = visualpages.document match {
      case value: CozyVisualPage.PageSet => value
      case _ => _stale("$command.visualPageSet", "named VisualPageSet must be cozy.visual-page-set.v1")
    }
    if (visualpages.catalogIdentity != projectionmap.plan.presentationCatalog.catalogIdentity)
      _stale("$command.visualPageSet", "named VisualPageSet full presentation catalog identity is not current")
    if (visualpages.logicalCatalogIdentity != projectionmap.plan.plan.presentationCatalog.identity)
      _stale("$command.visualPageSet", "named VisualPageSet logical presentation catalog identity is not current")
    _unique(pageset.pages.map(_.id), "$command.visualPageSet.pages", "page ID")
    val pageindex = pageset.pages.map(page => page.id -> page).toMap
    val rendered = projectionmap.plan.plan.steps.zipWithIndex.flatMap { case (step, index) =>
      val mapping = projectionmap.projectionMap.presentation.stepMappings(index)
      mapping.pageIds.map { pageid =>
        val page = pageindex.getOrElse(pageid,
          _stale(s"$$.presentation.stepMappings[$index].pageIds", s"mapped page ID does not resolve in the named VisualPageSet: $pageid")
        )
        if (page.logical != step.logical)
          _stale(s"$$.presentation.stepMappings[$index].pageIds", s"mapped page logical value does not equal Plan step ${step.id}")
        _validate_provenance(step, page, index)
        RenderedPage(step, mapping, page)
      }
    }
    ValidatedPreview(composition, projectionmap, visualpages, rendered)
  }

  private def _validate_provenance(step: CozyExplanation.PlanStep, page: CozyVisualPage.Page, index: Int): Unit = {
    val requiredsources = (step.sourceRefs ++ step.claims.flatMap(_.sourceRefs) ++ step.logical.nodes.flatMap(_.sourceRefs) ++ step.logical.relations.flatMap(_.sourceRefs)).distinct
    val requiredassets = (step.assetRefs ++ step.claims.flatMap(_.assetRefs)).distinct
    val pagesources = page.sources.map(_.id).toSet
    val pageassets = page.assets.map(_.id).toSet
    requiredsources.find(id => !pagesources.contains(id)).foreach { id =>
      _stale(s"$$.presentation.stepMappings[$index].pageIds", s"mapped page is missing source provenance: $id")
    }
    requiredassets.find(id => !pageassets.contains(id)).foreach { id =>
      _stale(s"$$.presentation.stepMappings[$index].pageIds", s"mapped page is missing asset provenance: $id")
    }
  }

  private def _html(validated: ValidatedPreview): String = {
    val plan = validated.projectionmap.plan.plan
    val mapping = validated.projectionmap.projectionMap.presentation.stepMappings
    val directions = validated.visualpages.catalog.relations.map(value => value.id -> value.direction).toMap
    val overview = plan.steps.zip(mapping).map { case (step, stepmapping) =>
      s"""<li data-step-id="${_escape(step.id)}"><code>${_escape(step.id)}</code>; role <code>${_escape(step.semanticRole)}</code>; maps to ${_codes(stepmapping.pageIds)}</li>"""
    }.mkString("\n")
    val cards = validated.pages.zipWithIndex.map { case (value, index) =>
      _page_card(value, index, directions)
    }.mkString("\n")
    Vector(
      "<!doctype html>",
      "<html lang=\"en\">",
      "<head>",
      "<meta charset=\"utf-8\">",
      "<title>Cozy Explanation Preview</title>",
      "<style>body{font-family:system-ui,sans-serif;margin:2rem;color:#20252b}main{max-width:76rem;margin:auto}code{font-family:ui-monospace,monospace}.identity,section{border:1px solid #ccd5de;padding:1rem;margin:1rem 0}.identity{display:grid;grid-template-columns:max-content 1fr;gap:.35rem 1rem}.identity dt{font-weight:600}h1,h2,h3,h4{margin-top:1.25rem}ol,ul{padding-left:1.5rem}.relation{border-left:4px solid #526d82;padding-left:.7rem}.flow{width:100%;height:3rem;border:1px solid #ccd5de;background:#f4f7fa}.visual-schematic{display:block;width:100%;height:auto;border:1px solid #ccd5de;background:#f4f7fa}.visual-schematic .node rect{fill:#fff;stroke:#526d82}.visual-schematic text{fill:#20252b;font-size:12px;text-anchor:middle}.visual-schematic .emphasis rect{stroke:#a23b3b;stroke-width:3}.visual-schematic .column-titles text{font-weight:600}</style>",
      "</head>",
      "<body>",
      "<main>",
      "<h1>Cozy Explanation Preview</h1>",
      "<dl class=\"identity\">",
      s"<dt>Renderer profile</dt><dd><code>${_renderer_profile}</code></dd>",
      s"<dt>Composition identity</dt><dd><code>${_escape(validated.composition.identity)}</code></dd>",
      s"<dt>Plan identity</dt><dd><code>${_escape(plan.identity)}</code></dd>",
      s"<dt>Projection Map identity</dt><dd><code>${_escape(validated.projectionmap.projectionMap.identity)}</code></dd>",
      s"<dt>Explanation Catalog identity</dt><dd><code>${_escape(plan.explanationCatalog.identity)}</code></dd>",
      s"<dt>Presentation Catalog identity</dt><dd><code>${_escape(validated.visualpages.catalogIdentity)}</code></dd>",
      s"<dt>Visual Page Set identity</dt><dd><code>${_escape(validated.visualpages.documentIdentity)}</code></dd>",
      "</dl>",
      "<section>",
      "<h2>Static explanation flow</h2>",
      s"<p>Subject Pattern <code>${_escape(plan.subjectPattern.id)}@${plan.subjectPattern.version}</code>; Explanation Pattern <code>${_escape(plan.explanationPattern.id)}@${plan.explanationPattern.version}</code>.</p>",
      "<svg class=\"flow\" viewBox=\"0 0 800 48\" role=\"img\" aria-label=\"Ordered Step to Page flow\"><defs><marker id=\"arrow\" markerWidth=\"8\" markerHeight=\"8\" refX=\"6\" refY=\"3\" orient=\"auto\"><path d=\"M0,0 L0,6 L6,3 z\" fill=\"#526d82\"/></marker></defs><path d=\"M20,24 H760\" stroke=\"#526d82\" stroke-width=\"2\" marker-end=\"url(#arrow)\"/><text x=\"28\" y=\"16\" fill=\"#20252b\">Plan steps map to reviewed Visual Pages in authored order</text></svg>",
      "<h3>Ordered Step-to-Page mapping</h3>",
      "<ol>",
      overview,
      "</ol>",
      "</section>",
      cards,
      "</main>",
      "</body>",
      "</html>",
      ""
    ).mkString("\n")
  }

  private def _page_card(
    rendered: RenderedPage,
    index: Int,
    directions: Map[String, String]
  ): String = {
    val step = rendered.step
    val page = rendered.page
    val claims = step.claims.map { claim =>
      s"<li><code>${_escape(claim.id)}</code>; emphasis <code>${_escape(claim.emphasis)}</code>; <q>${_escape(claim.text)}</q>; sources ${_codes(claim.sourceRefs)}; assets ${_codes(claim.assetRefs)}</li>"
    }.mkString("\n")
    val nodes = page.logical.nodes.map { node =>
      s"""<li data-node-id="${_escape(node.id)}"><code>${_escape(node.id)}</code>; role <code>${_escape(node.role)}</code>; label <q>${_escape(node.label)}</q>; sources ${_codes(node.sourceRefs)}</li>"""
    }.mkString("\n")
    val relations = page.logical.relations.map { relation =>
      val direction = directions.getOrElse(relation.relationType, "unresolved")
      s"""<li class="relation" data-relation-id="${_escape(relation.id)}"><code>${_escape(relation.id)}</code>; type <code>${_escape(relation.relationType)}</code>; direction <code>${_escape(direction)}</code>; from <code>${_escape(relation.from)}</code>; to <code>${_escape(relation.to)}</code>; sources ${_codes(relation.sourceRefs)}</li>"""
    }.mkString("\n")
    val parameters = page.visual.parameters.map { parameter =>
      val typed = parameter.value match {
        case CozyVisualPage.NodeReference(value) => "node-ref" -> value
        case CozyVisualPage.BooleanParameter(value) => "boolean" -> value.toString
        case CozyVisualPage.StringParameter(value) => "string" -> value
      }
      s"<li><code>${_escape(parameter.name)}</code>: <code>${_escape(typed._1)}</code> <code>${_escape(typed._2)}</code></li>"
    }.mkString("\n")
    val planparameters = step.parameterProvenance.values.map { parameter =>
      s"<li><code>${_escape(parameter.name)}</code>: <code>${_escape(_json_text(parameter.value))}</code></li>"
    }.mkString("\n")
    val sources = page.sources.map { source =>
      s"<li><code>${_escape(source.id)}</code>: <code>${_escape(source.path)}</code></li>"
    }.mkString("\n")
    val assets = page.assets.map { asset =>
      s"<li><code>${_escape(asset.id)}</code>: <code>${_escape(asset.mediaType)}</code>; <code>${_escape(asset.sha256)}</code>; <code>${_escape(asset.path)}</code></li>"
    }.mkString("\n")
    Vector(
      s"""<section class="page" data-step-id="${_escape(step.id)}" data-page-id="${_escape(page.id)}">""",
      s"<h2>Reviewed page ${index + 1}: <code>${_escape(page.id)}</code></h2>",
      s"<p>Plan Step <code>${_escape(step.id)}</code>; semantic role <code>${_escape(step.semanticRole)}</code>; mapped pages ${_codes(rendered.mapping.pageIds)}.</p>",
      "<h3>Claims</h3>",
      "<ul>",
      claims,
      "</ul>",
      s"<h3>Logical Pattern: <code>${_escape(page.logical.pattern)}</code></h3>",
      "<h4>Nodes</h4>",
      "<ol>",
      nodes,
      "</ol>",
      "<h4>Typed Relations</h4>",
      "<ol>",
      relations,
      "</ol>",
      s"<h3>Visual Pattern: <code>${_escape(page.visual.pattern)}</code></h3>",
      _visual_schematic(page),
      "<h4>Typed visual parameters</h4>",
      "<ul>",
      parameters,
      "</ul>",
      "<h4>Plan parameter values</h4>",
      "<ul>",
      planparameters,
      "</ul>",
      "<h3>Sources</h3>",
      "<ul>",
      sources,
      "</ul>",
      "<h3>Assets</h3>",
      "<ul>",
      assets,
      "</ul>",
      "</section>"
    ).mkString("\n")
  }

  private def _visual_schematic(page: CozyVisualPage.Page): String = page.visual.pattern match {
    case "flow-horizontal" => _flow_horizontal(page)
    case "flow-vertical" => _flow_vertical(page)
    case "mapping-columns" => _mapping_columns(page)
    case _ => ""
  }

  private def _flow_horizontal(page: CozyVisualPage.Page): String = {
    val nodewidth = 112
    val nodeheight = 42
    val nodegap = 28
    val width = math.max(360, 48 + page.logical.nodes.size * nodewidth + math.max(0, page.logical.nodes.size - 1) * nodegap + 48)
    val height = 112
    val positions = page.logical.nodes.zipWithIndex.map { case (node, nodeindex) =>
      node.id -> (48 + nodeindex * (nodewidth + nodegap), 48)
    }.toMap
    val edges = page.logical.relations.flatMap { relation =>
      for {
        from <- positions.get(relation.from)
        to <- positions.get(relation.to)
      } yield {
        val x0 = from._1 + nodewidth
        val y0 = from._2 + nodeheight / 2
        val x1 = to._1
        val y1 = to._2 + nodeheight / 2
        val arrow = if (x1 >= x0) s"""<path d="M${x1 - 8},${y1 - 5} L${x1},${y1} L${x1 - 8},${y1 + 5}"/>""" else s"""<path d="M${x1 + 8},${y1 - 5} L${x1},${y1} L${x1 + 8},${y1 + 5}"/>"""
        val label = if (_show_relation_labels(page)) s"""<text x="${(x0 + x1) / 2}" y="${y0 - 10}">${_escape(relation.relationType)}</text>""" else ""
        s"""<path d="M$x0,$y0 L$x1,$y1"/>$arrow$label"""
      }
    }.mkString
    val nodes = page.logical.nodes.map { node =>
      val position = positions(node.id)
      val emphasis = if (_emphasis_node(page).contains(node.id)) " emphasis" else ""
      s"""<g class="node$emphasis"><rect x="${position._1}" y="${position._2}" width="$nodewidth" height="$nodeheight" rx="4"/><text x="${position._1 + nodewidth / 2}" y="${position._2 + 25}">${_escape(node.label)}</text></g>"""
    }.mkString
    s"""<svg class="visual-schematic visual-flow-horizontal" data-visual-pattern="flow-horizontal" viewBox="0 0 $width $height" role="img" aria-label="Horizontal flow schematic"><g class="edges" fill="none" stroke="#526d82" stroke-width="2">$edges</g><g class="nodes">$nodes</g></svg>"""
  }

  private def _flow_vertical(page: CozyVisualPage.Page): String = {
    val nodewidth = 160
    val nodeheight = 42
    val nodegap = 26
    val width = 256
    val height = math.max(168, 48 + page.logical.nodes.size * nodeheight + math.max(0, page.logical.nodes.size - 1) * nodegap + 48)
    val positions = page.logical.nodes.zipWithIndex.map { case (node, nodeindex) =>
      node.id -> (48, 48 + nodeindex * (nodeheight + nodegap))
    }.toMap
    val edges = page.logical.relations.flatMap { relation =>
      for {
        from <- positions.get(relation.from)
        to <- positions.get(relation.to)
      } yield {
        val x0 = from._1 + nodewidth / 2
        val y0 = from._2 + nodeheight
        val x1 = to._1 + nodewidth / 2
        val y1 = to._2
        val arrow = if (y1 >= y0) s"""<path d="M${x1 - 5},${y1 - 8} L${x1},$y1 L${x1 + 5},${y1 - 8}"/>""" else s"""<path d="M${x1 - 5},${y1 + 8} L${x1},$y1 L${x1 + 5},${y1 + 8}"/>"""
        val label = if (_show_relation_labels(page)) s"""<text x="${x0 + 10}" y="${(y0 + y1) / 2}">${_escape(relation.relationType)}</text>""" else ""
        s"""<path d="M$x0,$y0 L$x1,$y1"/>$arrow$label"""
      }
    }.mkString
    val nodes = page.logical.nodes.map { node =>
      val position = positions(node.id)
      val emphasis = if (_emphasis_node(page).contains(node.id)) " emphasis" else ""
      s"""<g class="node$emphasis"><rect x="${position._1}" y="${position._2}" width="$nodewidth" height="$nodeheight" rx="4"/><text x="${position._1 + nodewidth / 2}" y="${position._2 + 25}">${_escape(node.label)}</text></g>"""
    }.mkString
    s"""<svg class="visual-schematic visual-flow-vertical" data-visual-pattern="flow-vertical" viewBox="0 0 $width $height" role="img" aria-label="Vertical flow schematic"><g class="edges" fill="none" stroke="#526d82" stroke-width="2">$edges</g><g class="nodes">$nodes</g></svg>"""
  }

  private def _mapping_columns(page: CozyVisualPage.Page): String = {
    val nodewidth = 168
    val nodeheight = 42
    val columnx = 48
    val targetx = 344
    val titley = 28
    val nodey = 58
    val nodegap = 24
    val sourcepositions = page.logical.nodes.filter(_.role == "source").zipWithIndex.map { case (node, nodeindex) =>
      node.id -> (columnx, nodey + nodeindex * (nodeheight + nodegap))
    }.toMap
    val targetpositions = page.logical.nodes.filter(_.role == "target").zipWithIndex.map { case (node, nodeindex) =>
      node.id -> (targetx, nodey + nodeindex * (nodeheight + nodegap))
    }.toMap
    val positions = sourcepositions ++ targetpositions
    val height = math.max(178, nodey + math.max(sourcepositions.size, targetpositions.size) * nodeheight + math.max(0, math.max(sourcepositions.size, targetpositions.size) - 1) * nodegap + 48)
    val edges = page.logical.relations.flatMap { relation =>
      for {
        from <- positions.get(relation.from)
        to <- positions.get(relation.to)
      } yield {
        val x0 = from._1 + nodewidth
        val y0 = from._2 + nodeheight / 2
        val x1 = to._1
        val y1 = to._2 + nodeheight / 2
        val label = if (_show_relation_labels(page)) s"""<text x="${(x0 + x1) / 2}" y="${y0 - 10}">${_escape(relation.relationType)}</text>""" else ""
        s"""<path d="M$x0,$y0 L$x1,$y1"/><path d="M${x1 - 8},${y1 - 5} L$x1,$y1 L${x1 - 8},${y1 + 5}"/>$label"""
      }
    }.mkString
    val nodes = page.logical.nodes.map { node =>
      val position = positions(node.id)
      s"""<g class="node"><rect x="${position._1}" y="${position._2}" width="$nodewidth" height="$nodeheight" rx="4"/><text x="${position._1 + nodewidth / 2}" y="${position._2 + 25}">${_escape(node.label)}</text></g>"""
    }.mkString
    val sourcetitle = _required_string_parameter(page, "sourceColumnTitle")
    val targettitle = _required_string_parameter(page, "targetColumnTitle")
    s"""<svg class="visual-schematic visual-mapping-columns" data-visual-pattern="mapping-columns" viewBox="0 0 560 $height" role="img" aria-label="Mapping columns schematic"><g class="column-titles"><text x="${columnx + nodewidth / 2}" y="$titley">${_escape(sourcetitle)}</text><text x="${targetx + nodewidth / 2}" y="$titley">${_escape(targettitle)}</text></g><g class="edges" fill="none" stroke="#526d82" stroke-width="2">$edges</g><g class="nodes">$nodes</g></svg>"""
  }

  private def _show_relation_labels(page: CozyVisualPage.Page): Boolean =
    page.visual.parameters.collectFirst {
      case parameter if parameter.name == "showRelationLabels" => parameter.value match {
        case CozyVisualPage.BooleanParameter(value) => value
        case _ => false
      }
    }.getOrElse(false)

  private def _emphasis_node(page: CozyVisualPage.Page): Option[String] =
    page.visual.parameters.collectFirst {
      case parameter if parameter.name == "emphasisNode" => parameter.value match {
        case CozyVisualPage.NodeReference(value) => value
        case _ => ""
      }
    }.filter(_.nonEmpty)

  private def _required_string_parameter(page: CozyVisualPage.Page, name: String): String =
    page.visual.parameters.collectFirst {
      case parameter if parameter.name == name => parameter.value match {
        case CozyVisualPage.StringParameter(value) => value
        case _ => _fail("EXPLANATION_PREVIEW_VISUAL_PARAMETER", s"$$.visual.parameters.$name", "required string parameter has an invalid type")
      }
    }.getOrElse(_fail("EXPLANATION_PREVIEW_VISUAL_PARAMETER", s"$$.visual.parameters.$name", "required string parameter is missing"))

  private def _receipt(validated: ValidatedPreview, htmlidentity: String): String = {
    val plan = validated.projectionmap.plan.plan
    val lines = Vector(
      s"schema: ${_schema}",
      "version: 1",
      s"generator: ${_generator}",
      s"rendererProfile: ${_renderer_profile}",
      s"compositionIdentity: ${validated.composition.identity}",
      s"planIdentity: ${plan.identity}",
      s"projectionMapIdentity: ${validated.projectionmap.projectionMap.identity}",
      s"explanationCatalogIdentity: ${plan.explanationCatalog.identity}",
      s"presentationCatalogIdentity: ${validated.visualpages.catalogIdentity}",
      s"visualPageSetIdentity: ${validated.visualpages.documentIdentity}",
      s"htmlIdentity: ${htmlidentity}"
    )
    (lines :+ s"identity: ${_sha256(lines.mkString("\n").getBytes(StandardCharsets.UTF_8))}").mkString("\n")
  }

  private def _validate_output(path: Path): Unit = {
    val output = try path.toAbsolutePath.normalize() catch {
      case NonFatal(_) => _fail("EXPLANATION_PREVIEW_COMMAND", "$command.save", "output path is invalid")
    }
    val name = Option(output.getFileName).map(_.toString).getOrElse("")
    if (!name.endsWith(".html"))
      _fail("EXPLANATION_PREVIEW_OUTPUT_FORMAT", "$command.save", "output must end in .html")
    val parent = Option(output.getParent).getOrElse(_fail("EXPLANATION_PREVIEW_COMMAND", "$command.save", "output parent is required"))
    if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(parent))
      _fail("EXPLANATION_PREVIEW_COMMAND", "$command.save", "output parent must be an existing direct directory")
    if (Files.exists(output, LinkOption.NOFOLLOW_LINKS) && (!Files.isRegularFile(output, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(output)))
      _fail("EXPLANATION_PREVIEW_COMMAND", "$command.save", "output must be absent or a direct regular non-symlink file")
  }

  private def _codes(values: Vector[String]): String =
    values.map(value => s"<code>${_escape(value)}</code>").mkString(", ")

  private def _json_text(value: CozyExplanation.JsonValue): String = value match {
    case CozyExplanation.JsonObject(fields) => fields.map { case (name, item) => s""""$name":${_json_text(item)}""" }.mkString("{", ",", "}")
    case CozyExplanation.JsonArray(values) => values.map(_json_text).mkString("[", ",", "]")
    case CozyExplanation.JsonString(item) => s""""$item""""
    case CozyExplanation.JsonNumber(item) => item.toString
    case CozyExplanation.JsonBoolean(item) => item.toString
  }

  private def _unique(values: Vector[String], path: String, label: String): Unit =
    values.groupBy(identity).collectFirst { case (value, duplicates) if duplicates.size > 1 => value }.foreach { value =>
      _stale(path, s"duplicate $label: $value")
    }

  private def _sha256(bytes: Array[Byte]): String =
    "sha256:" + MessageDigest.getInstance("SHA-256").digest(bytes).map(byte => f"${byte & 0xff}%02x").mkString

  private def _escape(value: String): String = Option(value).getOrElse("").flatMap {
    case '&' => "&amp;"
    case '<' => "&lt;"
    case '>' => "&gt;"
    case '"' => "&quot;"
    case '\'' => "&#39;"
    case character => character.toString
  }

  private def _missing(option: String): Nothing =
    _fail("EXPLANATION_PREVIEW_COMMAND", "$command", s"missing required $option")

  private def _stale(path: String, reason: String): Nothing =
    _fail("EXPLANATION_PREVIEW_STALE", path, reason)

  private def _fail(code: String, path: String, reason: String): Nothing =
    throw CozyExplanation.ExplanationFault(code, path, reason)
}
