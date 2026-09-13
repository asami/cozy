package cozy.document

import io.circe.{Json, JsonObject}
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import java.nio.file.{Files, LinkOption, Path}
import java.security.MessageDigest
import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.io.InputSource
import org.yaml.snakeyaml.{LoaderOptions, Yaml}
import scala.collection.mutable
import scala.util.control.NonFatal

/*
 * @since   Sep. 12, 2026
 * @version Sep. 13, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyDocumentDescriptionV2 {
  final case class CoreBinding(id: String, identity: String)
  final case class DocumentBinding(id: String, identity: String)
  final case class References(
    steps: Vector[String],
    claims: Vector[String],
    nodes: Vector[String],
    relations: Vector[String],
    flows: Vector[String]
  )
  final case class StepLabel(stepRef: String, text: String)
  final case class NodeLabel(nodeRef: String, text: String)
  final case class Labels(steps: Vector[StepLabel], nodes: Vector[NodeLabel])
  final case class ListItem(id: String, text: String, coreRefs: References)
  sealed trait Block { def id: String }
  final case class Paragraph(id: String, text: String, coreRefs: References) extends Block
  final case class ListBlock(id: String, items: Vector[ListItem], coreRefs: References) extends Block
  final case class Example(id: String, title: String, text: String, coreRefs: References) extends Block
  final case class Note(id: String, title: String, text: String, coreRefs: References) extends Block
  final case class LogicalStructure(id: String, stepRef: String) extends Block
  final case class Section(
    id: String,
    heading: String,
    coreRefs: References,
    blocks: Vector[Block],
    sections: Vector[Section]
  )
  final case class Document(title: String, sections: Vector[Section])
  final case class DocumentDescription(
    schema: String,
    id: String,
    core: CoreBinding,
    locale: String,
    document: Document,
    labels: Labels
  )
  final case class RetainedPoint(id: String, text: String, coreRefs: References)
  final case class DiagramItem(id: String, kind: String, ref: String)
  final case class DiagramEdge(id: String, kind: String, ref: String, direction: String)
  final case class Diagram(items: Vector[DiagramItem], edges: Vector[DiagramEdge])
  final case class Overview(stepRef: String)
  final case class Omission(
    id: String,
    documentKind: String,
    documentRef: String,
    disposition: String,
    rationale: String
  )
  final case class SummaryUnit(
    id: String,
    heading: String,
    message: String,
    emphasis: String,
    coreRefs: References,
    navigationLabel: String,
    retainedPoints: Vector[RetainedPoint],
    diagram: Option[Diagram],
    omissions: Vector[Omission],
    overview: Option[Overview] = None
  )
  final case class Summary(title: String, units: Vector[SummaryUnit])
  final case class SummaryDescription(
    schema: String,
    id: String,
    core: CoreBinding,
    document: DocumentBinding,
    locale: String,
    summary: Summary
  )
  final case class DocumentTargets(sectionIds: Set[String], blockIds: Set[String], listItemIds: Set[String]) {
    def ++(rhs: DocumentTargets): DocumentTargets =
      DocumentTargets(sectionIds ++ rhs.sectionIds, blockIds ++ rhs.blockIds, listItemIds ++ rhs.listItemIds)
  }
  final case class ValidatedDocument(
    core: CozyDocumentLogicTree.ValidatedCore,
    description: DocumentDescription,
    coreIdentity: String,
    documentIdentity: String,
    documentTargets: DocumentTargets
  )
  final case class ValidatedSummary(
    document: ValidatedDocument,
    description: SummaryDescription,
    summaryIdentity: String
  )

  final case class DescriptionV2Fault(code: String, path: String, reason: String)
    extends IllegalArgumentException(s"$code path=$path reason=$reason")

  private final case class Coverage(
    steps: Set[String] = Set.empty,
    claims: Set[String] = Set.empty,
    nodes: Set[String] = Set.empty,
    relations: Set[String] = Set.empty,
    flows: Set[String] = Set.empty
  ) {
    def ++(rhs: Coverage): Coverage = Coverage(
      steps ++ rhs.steps,
      claims ++ rhs.claims,
      nodes ++ rhs.nodes,
      relations ++ rhs.relations,
      flows ++ rhs.flows
    )
  }
  private final case class ParsedDocument(description: DocumentDescription, targets: DocumentTargets)
  private final case class ParsedSection(section: Section, coverage: Coverage, targets: DocumentTargets)
  private final case class ParsedBlock(block: Block, coverage: Coverage, targets: DocumentTargets)
  private final case class ParsedListItem(item: ListItem, coverage: Coverage, targets: DocumentTargets)
  private final case class TransitionSource(flowid: String, transition: CozyDocumentLogicTree.Transition)

  private val _document_schema = "cozy.document-description.v2"
  private val _summary_schema = "cozy.summary-description.v2"
  private val _id_pattern = "[A-Za-z0-9][A-Za-z0-9._-]*".r
  private val _locale_pattern = "[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*".r
  private val _identity_pattern = "sha256:[0-9a-f]{64}".r
  private val _reference_fields = Set("steps", "claims", "nodes", "relations", "flows")

  def loadDocument(corePath: Path, documentPath: Path): ValidatedDocument = {
    val core = CozyDocumentLogicTree.loadCore(corePath)
    val documentpath = _admit_localized(documentPath, "document", "document.yaml")
    val documentbytes = _read_bytes(documentpath, "document")
    val parsed = _document_description(_load_document(documentpath, documentbytes, "document"), core)
    val description = parsed.description
    val identity = _identity(documentbytes)
    if (description.core.id != core.core.id)
      _fail("DESCRIPTION_V2_DOCUMENT_CORE", "$.core.id", "must exactly equal the directly admitted Core id")
    if (description.core.identity != core.coreIdentity)
      _fail("DESCRIPTION_V2_DOCUMENT_CORE", "$.core.identity", "must bind the direct Core byte SHA-256 identity")
    _validate_locale(documentpath, description.locale, "$.locale")
    ValidatedDocument(core, description, core.coreIdentity, identity, parsed.targets)
  }

  def loadSummary(corePath: Path, documentPath: Path, summaryPath: Path): ValidatedSummary = {
    val document = loadDocument(corePath, documentPath)
    val summarypath = _admit_localized(summaryPath, "summary", "summary.yaml")
    val summarybytes = _read_bytes(summarypath, "summary")
    val description = _summary_description(_load_document(summarypath, summarybytes, "summary"), document)
    if (description.core.id != document.description.core.id || description.core.identity != document.coreIdentity)
      _fail("DESCRIPTION_V2_SUMMARY_CORE", "$.core", "must bind the directly admitted Core id and byte identity")
    if (description.document.id != document.description.id || description.document.identity != document.documentIdentity)
      _fail("DESCRIPTION_V2_SUMMARY_DOCUMENT", "$.document", "must bind the directly admitted v2 Document id and byte identity")
    _validate_locale(summarypath, description.locale, "$.locale")
    ValidatedSummary(document, description, _identity(summarybytes))
  }

  private def _document_description(value: Json, core: CozyDocumentLogicTree.ValidatedCore): ParsedDocument = {
    val fields = _object(value, "$")
    _exact_fields(fields, Set("schema", "id", "core", "locale", "document", "labels"), "$")
    if (_string(fields, "schema", "$") != _document_schema)
      _fail("DESCRIPTION_V2_DOCUMENT_SCHEMA", "$.schema", s"must be exactly ${_document_schema}")
    val ids = mutable.Set.empty[String]
    val content = _object(_field(fields, "document", "$"), "$.document")
    _exact_fields(content, Set("title", "sections"), "$.document")
    val sections = _sections(_array(_field(content, "sections", "$.document"), "$.document.sections"), "$.document.sections", core, ids)
    val coverage = sections.map(_.coverage).foldLeft(Coverage())(_ ++ _)
    _validate_document_coverage(coverage, core)
    val labels = _labels(_field(fields, "labels", "$"), "$.labels", core)
    ParsedDocument(
      DocumentDescription(
        _document_schema,
        _id(_string(fields, "id", "$"), "$.id"),
        _core_binding(_field(fields, "core", "$"), "$.core"),
        _locale(_string(fields, "locale", "$"), "$.locale"),
        Document(_text(_string(content, "title", "$.document"), "$.document.title"), sections.map(_.section)),
        labels
      ),
      sections.map(_.targets).foldLeft(DocumentTargets(Set.empty, Set.empty, Set.empty))(_ ++ _)
    )
  }

  private def _summary_description(value: Json, document: ValidatedDocument): SummaryDescription = {
    val fields = _object(value, "$")
    _exact_fields(fields, Set("schema", "id", "core", "document", "locale", "summary"), "$")
    if (_string(fields, "schema", "$") != _summary_schema)
      _fail("DESCRIPTION_V2_SUMMARY_SCHEMA", "$.schema", s"must be exactly ${_summary_schema}")
    val content = _object(_field(fields, "summary", "$"), "$.summary")
    _exact_fields(content, Set("title", "units"), "$.summary")
    val units = _array(_field(content, "units", "$.summary"), "$.summary.units").zipWithIndex.map { case (item, index) =>
      _summary_unit(item, s"$$.summary.units[$index]", document)
    }
    _unique(units.map(_.id), "$.summary.units", "Summary unit id")
    val overviews = units.zipWithIndex.filter(_._1.overview.nonEmpty)
    if (overviews.size > 1 || overviews.headOption.exists(_._2 != 0))
      _fail("DESCRIPTION_V2_OVERVIEW_POSITION", "$.summary.units", "at most one explicit overview is allowed, as the first unit")
    SummaryDescription(
      _summary_schema,
      _id(_string(fields, "id", "$"), "$.id"),
      _core_binding(_field(fields, "core", "$"), "$.core"),
      _document_binding(_field(fields, "document", "$"), "$.document"),
      _locale(_string(fields, "locale", "$"), "$.locale"),
      Summary(_text(_string(content, "title", "$.summary"), "$.summary.title"), units)
    )
  }

  private def _labels(value: Json, path: String, core: CozyDocumentLogicTree.ValidatedCore): Labels = {
    val fields = _object(value, path)
    _exact_fields(fields, Set("steps", "nodes"), path)
    val steps = _array(_field(fields, "steps", path), s"$path.steps").zipWithIndex.map { case (item, index) =>
      _step_label(item, s"$path.steps[$index]", core)
    }
    val nodes = _array(_field(fields, "nodes", path), s"$path.nodes").zipWithIndex.map { case (item, index) =>
      _node_label(item, s"$path.nodes[$index]", core)
    }
    _exact_bindings(steps.map(_.stepRef), core.stepsById.keySet, s"$path.steps", "Step label")
    _exact_bindings(nodes.map(_.nodeRef), core.nodesById.keySet, s"$path.nodes", "Node label")
    Labels(steps, nodes)
  }

  private def _step_label(value: Json, path: String, core: CozyDocumentLogicTree.ValidatedCore): StepLabel = {
    val fields = _object(value, path)
    _exact_fields(fields, Set("stepRef", "text"), path)
    val ref = _id(_string(fields, "stepRef", path), s"$path.stepRef")
    if (!core.stepsById.contains(ref)) _fail("DESCRIPTION_V2_REFERENCE", s"$path.stepRef", "must resolve to a Core Step")
    StepLabel(ref, _text(_string(fields, "text", path), s"$path.text"))
  }

  private def _node_label(value: Json, path: String, core: CozyDocumentLogicTree.ValidatedCore): NodeLabel = {
    val fields = _object(value, path)
    _exact_fields(fields, Set("nodeRef", "text"), path)
    val ref = _id(_string(fields, "nodeRef", path), s"$path.nodeRef")
    if (!core.nodesById.contains(ref)) _fail("DESCRIPTION_V2_REFERENCE", s"$path.nodeRef", "must resolve to a Core node")
    NodeLabel(ref, _text(_string(fields, "text", path), s"$path.text"))
  }

  private def _sections(
    values: Vector[Json],
    path: String,
    core: CozyDocumentLogicTree.ValidatedCore,
    ids: mutable.Set[String]
  ): Vector[ParsedSection] =
    values.zipWithIndex.map { case (value, index) => _section(value, s"$path[$index]", core, ids) }

  private def _section(
    value: Json,
    path: String,
    core: CozyDocumentLogicTree.ValidatedCore,
    ids: mutable.Set[String]
  ): ParsedSection = {
    val fields = _object(value, path)
    _exact_fields(fields, Set("id", "heading", "coreRefs", "blocks", "sections"), path)
    val id = _id(_string(fields, "id", path), s"$path.id")
    _add_id(ids, id, s"$path.id")
    val refs = _references(_field(fields, "coreRefs", path), s"$path.coreRefs", core, required = true)
    val blocks = _array(_field(fields, "blocks", path), s"$path.blocks").zipWithIndex.map { case (item, index) =>
      _block(item, s"$path.blocks[$index]", core, ids)
    }
    val sections = _sections(_array(_field(fields, "sections", path), s"$path.sections"), s"$path.sections", core, ids)
    val coverage = (Coverage() ++ _coverage(refs)) ++ blocks.map(_.coverage).foldLeft(Coverage())(_ ++ _) ++ sections.map(_.coverage).foldLeft(Coverage())(_ ++ _)
    val targets = DocumentTargets(Set(id), Set.empty, Set.empty) ++
      blocks.map(_.targets).foldLeft(DocumentTargets(Set.empty, Set.empty, Set.empty))(_ ++ _) ++
      sections.map(_.targets).foldLeft(DocumentTargets(Set.empty, Set.empty, Set.empty))(_ ++ _)
    ParsedSection(Section(id, _text(_string(fields, "heading", path), s"$path.heading"), refs, blocks.map(_.block), sections.map(_.section)), coverage, targets)
  }

  private def _block(
    value: Json,
    path: String,
    core: CozyDocumentLogicTree.ValidatedCore,
    ids: mutable.Set[String]
  ): ParsedBlock = {
    val fields = _object(value, path)
    val id = _id(_string(fields, "id", path), s"$path.id")
    _add_id(ids, id, s"$path.id")
    _string(fields, "kind", path) match {
      case "paragraph" =>
        _exact_fields(fields, Set("id", "kind", "text", "coreRefs"), path)
        val refs = _references(_field(fields, "coreRefs", path), s"$path.coreRefs", core, required = true)
        ParsedBlock(Paragraph(id, _text(_string(fields, "text", path), s"$path.text"), refs), _coverage(refs), DocumentTargets(Set.empty, Set(id), Set.empty))
      case "list" =>
        _exact_fields(fields, Set("id", "kind", "items", "coreRefs"), path)
        val refs = _references(_field(fields, "coreRefs", path), s"$path.coreRefs", core, required = true)
        val items = _array(_field(fields, "items", path), s"$path.items").zipWithIndex.map { case (item, index) =>
          _list_item(item, s"$path.items[$index]", core, ids)
        }
        ParsedBlock(
          ListBlock(id, items.map(_.item), refs),
          _coverage(refs) ++ items.map(_.coverage).foldLeft(Coverage())(_ ++ _),
          DocumentTargets(Set.empty, Set(id), Set.empty) ++ items.map(_.targets).foldLeft(DocumentTargets(Set.empty, Set.empty, Set.empty))(_ ++ _)
        )
      case "example" | "note" =>
        _exact_fields(fields, Set("id", "kind", "title", "text", "coreRefs"), path)
        val refs = _references(_field(fields, "coreRefs", path), s"$path.coreRefs", core, required = true)
        val title = _text(_string(fields, "title", path), s"$path.title")
        val text = _text(_string(fields, "text", path), s"$path.text")
        val block: Block = if (_string(fields, "kind", path) == "example") Example(id, title, text, refs) else Note(id, title, text, refs)
        ParsedBlock(block, _coverage(refs), DocumentTargets(Set.empty, Set(id), Set.empty))
      case "logical-structure" =>
        _exact_fields(fields, Set("id", "kind", "stepRef"), path)
        val step = _id(_string(fields, "stepRef", path), s"$path.stepRef")
        if (!core.stepsById.contains(step)) _fail("DESCRIPTION_V2_REFERENCE", s"$path.stepRef", "must resolve to a Core Step")
        ParsedBlock(LogicalStructure(id, step), Coverage(steps = Set(step)), DocumentTargets(Set.empty, Set(id), Set.empty))
      case _ => _fail("DESCRIPTION_V2_BLOCK_KIND", s"$path.kind", "must be paragraph, list, example, note, or logical-structure")
    }
  }

  private def _list_item(
    value: Json,
    path: String,
    core: CozyDocumentLogicTree.ValidatedCore,
    ids: mutable.Set[String]
  ): ParsedListItem = {
    val fields = _object(value, path)
    _exact_fields(fields, Set("id", "text", "coreRefs"), path)
    val id = _id(_string(fields, "id", path), s"$path.id")
    _add_id(ids, id, s"$path.id")
    val refs = _references(_field(fields, "coreRefs", path), s"$path.coreRefs", core, required = true)
    ParsedListItem(ListItem(id, _text(_string(fields, "text", path), s"$path.text"), refs), _coverage(refs), DocumentTargets(Set.empty, Set.empty, Set(id)))
  }

  private def _summary_unit(value: Json, path: String, document: ValidatedDocument): SummaryUnit = {
    val fields = _object(value, path)
    val required = Set("id", "heading", "message", "emphasis", "coreRefs", "navigationLabel", "retainedPoints", "omissions")
    val permitted = required ++ Set("diagram", "overview")
    if (!required.subsetOf(fields.keys.toSet) || !fields.keys.toSet.subsetOf(permitted))
      _fail("DESCRIPTION_V2_FIELDS", path, s"must contain exactly: ${required.toVector.sorted.mkString(", ")}, optionally diagram and overview")
    val emphasis = _string(fields, "emphasis", path)
    if (!Set("primary", "supporting", "conclusion").contains(emphasis))
      _fail("DESCRIPTION_V2_EMPHASIS", s"$path.emphasis", "must be primary, supporting, or conclusion")
    val refs = _references(_field(fields, "coreRefs", path), s"$path.coreRefs", document.core, required = true)
    val points = _array(_field(fields, "retainedPoints", path), s"$path.retainedPoints").zipWithIndex.map { case (item, index) =>
      _retained_point(item, s"$path.retainedPoints[$index]", document.core)
    }
    if (points.isEmpty) _fail("DESCRIPTION_V2_RETAINED_POINTS", s"$path.retainedPoints", "must contain at least one retained point")
    _unique(points.map(_.id), s"$path.retainedPoints", "retained point id")
    val diagram = fields("diagram").map(value => _diagram(value, s"$path.diagram", document.core, refs))
    val overview = fields("overview").map(value => _overview(value, s"$path.overview", document.core, refs, points, diagram))
    val omissions = _array(_field(fields, "omissions", path), s"$path.omissions").zipWithIndex.map { case (item, index) =>
      _omission(item, s"$path.omissions[$index]", document.documentTargets)
    }
    if (omissions.isEmpty) _fail("DESCRIPTION_V2_OMISSIONS", s"$path.omissions", "must contain at least one omission")
    _unique(omissions.map(_.id), s"$path.omissions", "omission id")
    SummaryUnit(
      _id(_string(fields, "id", path), s"$path.id"),
      _text(_string(fields, "heading", path), s"$path.heading"),
      _text(_string(fields, "message", path), s"$path.message"),
      emphasis,
      refs,
      _text(_string(fields, "navigationLabel", path), s"$path.navigationLabel"),
      points,
      diagram,
      omissions,
      overview
    )
  }

  private def _overview(value: Json, path: String, core: CozyDocumentLogicTree.ValidatedCore, refs: References, points: Vector[RetainedPoint], diagram: Option[Diagram]): Overview = {
    val fields = _object(value, path)
    _exact_fields(fields, Set("stepRef"), path)
    val stepref = _id(_string(fields, "stepRef", path), s"$path.stepRef")
    val root = core.core.root
    if (stepref != root.id)
      _fail("DESCRIPTION_V2_OVERVIEW_ROOT", s"$path.stepRef", "must explicitly select the directly admitted Core Root Step")
    val expected = Coverage(
      (root.id +: root.steps.map(_.id)).toSet,
      root.claims.map(_.id).toSet,
      root.structure.nodes.map(_.id).toSet,
      root.structure.relations.map(_.id).toSet,
      Set(root.flow.id)
    )
    if (_coverage(refs) != expected || points.exists(point => (_coverage(point.coreRefs) ++ expected) != expected))
      _fail("DESCRIPTION_V2_OVERVIEW_SCOPE", path, "unit sources must exactly cover Root/direct-child scope and retained-point sources must be subsets")
    val selected = diagram.getOrElse(_fail("DESCRIPTION_V2_OVERVIEW_DIAGRAM", path, "must explicitly select the complete top-level diagram"))
    val items = root.steps.map(step => "step" -> step.id).toSet ++ root.structure.nodes.map(node => "node" -> node.id)
    val edges = root.structure.relations.map(relation => "relation" -> relation.id).toSet ++ root.flow.transitions.map(transition => "flow-transition" -> transition.id)
    if (selected.items.map(item => item.kind -> item.ref).toSet != items || selected.items.size != items.size ||
        selected.edges.map(edge => edge.kind -> edge.ref).toSet != edges || selected.edges.size != edges.size)
      _fail("DESCRIPTION_V2_OVERVIEW_DIAGRAM", path, "must explicitly select every Root Node/direct child Step and Root Relation/Flow transition exactly once")
    Overview(stepref)
  }

  private def _retained_point(value: Json, path: String, core: CozyDocumentLogicTree.ValidatedCore): RetainedPoint = {
    val fields = _object(value, path)
    _exact_fields(fields, Set("id", "text", "coreRefs"), path)
    RetainedPoint(
      _id(_string(fields, "id", path), s"$path.id"),
      _text(_string(fields, "text", path), s"$path.text"),
      _references(_field(fields, "coreRefs", path), s"$path.coreRefs", core, required = true)
    )
  }

  private def _diagram(value: Json, path: String, core: CozyDocumentLogicTree.ValidatedCore, refs: References): Diagram = {
    val fields = _object(value, path)
    _exact_fields(fields, Set("items", "edges"), path)
    val items = _array(_field(fields, "items", path), s"$path.items").zipWithIndex.map { case (item, index) =>
      _diagram_item(item, s"$path.items[$index]", core)
    }
    val edges = _array(_field(fields, "edges", path), s"$path.edges").zipWithIndex.map { case (item, index) =>
      _diagram_edge(item, s"$path.edges[$index]", core)
    }
    _unique(items.map(_.id) ++ edges.map(_.id), path, "diagram identity")
    val selected = items.map(value => value.kind -> value.ref).toSet
    edges.foreach(edge => _validate_diagram_edge(edge, selected, refs, core, s"$path.edges.${edge.id}"))
    Diagram(items, edges)
  }

  private def _diagram_item(value: Json, path: String, core: CozyDocumentLogicTree.ValidatedCore): DiagramItem = {
    val fields = _object(value, path)
    _exact_fields(fields, Set("id", "kind", "ref"), path)
    val kind = _string(fields, "kind", path)
    val ref = _id(_string(fields, "ref", path), s"$path.ref")
    kind match {
      case "step" if core.stepsById.contains(ref) => ()
      case "node" if core.nodesById.contains(ref) => ()
      case "step" | "node" => _fail("DESCRIPTION_V2_DIAGRAM_REFERENCE", s"$path.ref", s"must resolve to a Core $kind")
      case _ => _fail("DESCRIPTION_V2_DIAGRAM_KIND", s"$path.kind", "must be step or node")
    }
    DiagramItem(_id(_string(fields, "id", path), s"$path.id"), kind, ref)
  }

  private def _diagram_edge(value: Json, path: String, core: CozyDocumentLogicTree.ValidatedCore): DiagramEdge = {
    val fields = _object(value, path)
    _exact_fields(fields, Set("id", "kind", "ref", "direction"), path)
    val kind = _string(fields, "kind", path)
    val ref = _id(_string(fields, "ref", path), s"$path.ref")
    kind match {
      case "relation" if core.relationsById.contains(ref) => ()
      case "flow-transition" if _transitions(core).contains(ref) => ()
      case "relation" | "flow-transition" => _fail("DESCRIPTION_V2_DIAGRAM_REFERENCE", s"$path.ref", s"must resolve to a Core $kind")
      case _ => _fail("DESCRIPTION_V2_DIAGRAM_KIND", s"$path.kind", "must be relation or flow-transition")
    }
    val direction = _string(fields, "direction", path)
    if (!Set("forward", "inverse").contains(direction))
      _fail("DESCRIPTION_V2_DIAGRAM_DIRECTION", s"$path.direction", "must be forward or inverse")
    DiagramEdge(_id(_string(fields, "id", path), s"$path.id"), kind, ref, direction)
  }

  private def _validate_diagram_edge(
    edge: DiagramEdge,
    selected: Set[(String, String)],
    refs: References,
    core: CozyDocumentLogicTree.ValidatedCore,
    path: String
  ): Unit = edge.kind match {
    case "relation" =>
      if (!refs.relations.contains(edge.ref))
        _fail("DESCRIPTION_V2_DIAGRAM_GROUNDING", path, "Relation edge must occur in the unit coreRefs.relations")
      val relation = core.relationsById(edge.ref)
      val endpoints = if (edge.direction == "forward") Vector(relation.from, relation.to) else Vector(relation.to, relation.from)
      if (!selected.contains("node" -> endpoints.head) || !selected.contains("node" -> endpoints(1)))
        _fail("DESCRIPTION_V2_DIAGRAM_ENDPOINT", path, "Relation edge endpoints must be selected typed Node items in declared direction")
    case "flow-transition" =>
      val source = _transitions(core)(edge.ref)
      if (!refs.flows.contains(source.flowid))
        _fail("DESCRIPTION_V2_DIAGRAM_GROUNDING", path, "Flow-transition edge owner Flow must occur in the unit coreRefs.flows")
      val endpoints = if (edge.direction == "forward") Vector(source.transition.fromStepId, source.transition.toStepId) else Vector(source.transition.toStepId, source.transition.fromStepId)
      if (!selected.contains("step" -> endpoints.head) || !selected.contains("step" -> endpoints(1)))
        _fail("DESCRIPTION_V2_DIAGRAM_ENDPOINT", path, "Flow-transition edge endpoints must be selected typed Step items in declared direction")
    case _ => _fail("DESCRIPTION_V2_DIAGRAM_KIND", s"$path.kind", "must be relation or flow-transition")
  }

  private def _omission(value: Json, path: String, targets: DocumentTargets): Omission = {
    val fields = _object(value, path)
    _exact_fields(fields, Set("id", "documentKind", "documentRef", "disposition", "rationale"), path)
    val kind = _string(fields, "documentKind", path)
    val ref = _id(_string(fields, "documentRef", path), s"$path.documentRef")
    val resolved = kind match {
      case "section" => targets.sectionIds.contains(ref)
      case "block" => targets.blockIds.contains(ref)
      case "list-item" => targets.listItemIds.contains(ref)
      case _ => _fail("DESCRIPTION_V2_OMISSION_KIND", s"$path.documentKind", "must be section, block, or list-item")
    }
    if (!resolved) _fail("DESCRIPTION_V2_OMISSION_REFERENCE", s"$path.documentRef", s"must resolve to a v2 Document $kind")
    val disposition = _string(fields, "disposition", path)
    if (!Set("omitted", "condensed").contains(disposition))
      _fail("DESCRIPTION_V2_OMISSION_DISPOSITION", s"$path.disposition", "must be omitted or condensed")
    Omission(
      _id(_string(fields, "id", path), s"$path.id"),
      kind,
      ref,
      disposition,
      _text(_string(fields, "rationale", path), s"$path.rationale")
    )
  }

  private def _core_binding(value: Json, path: String): CoreBinding = {
    val fields = _object(value, path)
    _exact_fields(fields, Set("id", "identity"), path)
    CoreBinding(_id(_string(fields, "id", path), s"$path.id"), _identity_value(_string(fields, "identity", path), s"$path.identity"))
  }

  private def _document_binding(value: Json, path: String): DocumentBinding = {
    val fields = _object(value, path)
    _exact_fields(fields, Set("id", "identity"), path)
    DocumentBinding(_id(_string(fields, "id", path), s"$path.id"), _identity_value(_string(fields, "identity", path), s"$path.identity"))
  }

  private def _references(
    value: Json,
    path: String,
    core: CozyDocumentLogicTree.ValidatedCore,
    required: Boolean
  ): References = {
    val fields = _object(value, path)
    _exact_fields(fields, _reference_fields, path)
    val steps = _ids(_field(fields, "steps", path), s"$path.steps")
    val claims = _ids(_field(fields, "claims", path), s"$path.claims")
    val nodes = _ids(_field(fields, "nodes", path), s"$path.nodes")
    val relations = _ids(_field(fields, "relations", path), s"$path.relations")
    val flows = _ids(_field(fields, "flows", path), s"$path.flows")
    _resolve(steps, core.stepsById.keySet, s"$path.steps", "Step")
    _resolve(claims, core.claimsById.keySet, s"$path.claims", "claim")
    _resolve(nodes, core.nodesById.keySet, s"$path.nodes", "node")
    _resolve(relations, core.relationsById.keySet, s"$path.relations", "Relation")
    _resolve(flows, core.flowsById.keySet, s"$path.flows", "Flow")
    val refs = References(steps, claims, nodes, relations, flows)
    if (required && _coverage(refs) == Coverage()) _fail("DESCRIPTION_V2_REFERENCE", path, "must contain at least one resolved Core reference")
    refs
  }

  private def _ids(value: Json, path: String): Vector[String] = {
    val ids = _array(value, path).zipWithIndex.map { case (item, index) => _id(_string_value(item, s"$path[$index]"), s"$path[$index]") }
    _unique(ids, path, "reference id")
    ids
  }

  private def _resolve(values: Vector[String], admitted: Set[String], path: String, label: String): Unit =
    values.find(value => !admitted.contains(value)).foreach(value => _fail("DESCRIPTION_V2_REFERENCE", path, s"$label does not resolve in the bound Core: $value"))

  private def _coverage(refs: References): Coverage =
    Coverage(refs.steps.toSet, refs.claims.toSet, refs.nodes.toSet, refs.relations.toSet, refs.flows.toSet)

  private def _validate_document_coverage(coverage: Coverage, core: CozyDocumentLogicTree.ValidatedCore): Unit =
    if (coverage.steps != core.stepsById.keySet || coverage.claims != core.claimsById.keySet || coverage.nodes != core.nodesById.keySet ||
      coverage.relations != core.relationsById.keySet || coverage.flows != core.flowsById.keySet)
      _fail("DESCRIPTION_V2_COVERAGE", "$.document", "must explicitly cover every Core Step, claim, node, Relation, and Flow")

  private def _exact_bindings(values: Vector[String], admitted: Set[String], path: String, label: String): Unit = {
    _unique(values, path, s"$label reference")
    if (values.toSet != admitted || values.size != admitted.size)
      _fail("DESCRIPTION_V2_LABEL_COVERAGE", path, s"must provide one and only one record for every Core $label")
  }

  private def _transitions(core: CozyDocumentLogicTree.ValidatedCore): Map[String, TransitionSource] =
    core.depthFirstSteps.flatMap(step => step.flow.transitions.map(transition => transition.id -> TransitionSource(step.flow.id, transition))).toMap

  private def _admit_localized(value: Path, label: String, basename: String): Path = {
    val path = _admit_file(value, label)
    if (path.getFileName.toString != basename)
      _fail("DESCRIPTION_V2_PATH", s"$$.$label", s"must be a direct regular file named exactly $basename")
    path
  }

  private def _admit_file(value: Path, label: String): Path = {
    val path = try value.toAbsolutePath.normalize() catch { case NonFatal(_) => _fail("DESCRIPTION_V2_PATH", s"$$.$label", "path is invalid") }
    if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
      _fail("DESCRIPTION_V2_PATH", s"$$.$label", "must be a direct regular non-symlink file")
    val parent = Option(path.getParent).getOrElse(_fail("DESCRIPTION_V2_PATH", s"$$.$label", "has no safe parent"))
    if (Files.isSymbolicLink(parent) || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS))
      _fail("DESCRIPTION_V2_PATH", s"$$.$label", "parent must be a direct non-symlink directory")
    path
  }

  private def _validate_locale(path: Path, locale: String, fieldpath: String): Unit =
    if (path.getParent.getFileName.toString != locale)
      _fail("DESCRIPTION_V2_LOCALE", fieldpath, "must exactly equal the direct locale-directory basename")

  private def _load_document(path: Path, bytes: Array[Byte], label: String): Json =
    try {
      val text = _decode_utf8(bytes, s"$$.$label")
      val options = new LoaderOptions()
      options.setAllowDuplicateKeys(false)
      options.setAllowRecursiveKeys(false)
      options.setMaxAliasesForCollections(0)
      _reject_yaml_indirection(new Yaml(options).parse(new StringReader(text)), s"$$.$label")
      new Yaml(options).load(new StringReader(text))
      StructuredDocumentLoader.loadJson(InputSource(text, path.toUri)).take
    } catch {
      case fault: DescriptionV2Fault => throw fault
      case NonFatal(_) => _fail("DESCRIPTION_V2_SOURCE", s"$$.$label", "must be a well-formed YAML document without duplicate or lossy structure")
    }

  private def _reject_yaml_indirection(events: java.lang.Iterable[org.yaml.snakeyaml.events.Event], path: String): Unit = {
    val iterator = events.iterator
    while (iterator.hasNext) {
      iterator.next() match {
        case _: org.yaml.snakeyaml.events.AliasEvent =>
          _fail("DESCRIPTION_V2_SOURCE", path, "YAML aliases are not admitted")
        case value: org.yaml.snakeyaml.events.NodeEvent if value.getAnchor != null =>
          _fail("DESCRIPTION_V2_SOURCE", path, "YAML anchors are not admitted")
        case value: org.yaml.snakeyaml.events.ScalarEvent if value.getTag != null =>
          _fail("DESCRIPTION_V2_SOURCE", path, "explicit YAML tags are not admitted")
        case value: org.yaml.snakeyaml.events.CollectionStartEvent if value.getTag != null =>
          _fail("DESCRIPTION_V2_SOURCE", path, "explicit YAML tags are not admitted")
        case _ =>
      }
    }
  }

  private def _read_bytes(path: Path, label: String): Array[Byte] =
    try Files.readAllBytes(path) catch { case NonFatal(_) => _fail("DESCRIPTION_V2_SOURCE", s"$$.$label", "cannot be read") }

  private def _decode_utf8(bytes: Array[Byte], path: String): String =
    try StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString catch {
      case NonFatal(_) => _fail("DESCRIPTION_V2_SOURCE", path, "must be valid UTF-8")
    }

  private def _identity(bytes: Array[Byte]): String = "sha256:" + MessageDigest.getInstance("SHA-256").digest(bytes).map(value => f"${value & 0xff}%02x").mkString
  private def _object(value: Json, path: String): JsonObject = value.asObject.getOrElse(_fail("DESCRIPTION_V2_STRUCTURE", path, "must be an object"))
  private def _array(value: Json, path: String): Vector[Json] = value.asArray.map(_.toVector).getOrElse(_fail("DESCRIPTION_V2_STRUCTURE", path, "must be an array"))
  private def _field(fields: JsonObject, name: String, path: String): Json = fields(name).getOrElse(_fail("DESCRIPTION_V2_STRUCTURE", path, s"missing required field: $name"))
  private def _string(fields: JsonObject, name: String, path: String): String = _string_value(_field(fields, name, path), s"$path.$name")
  private def _string_value(value: Json, path: String): String = value.asString.getOrElse(_fail("DESCRIPTION_V2_STRUCTURE", path, "must be a string"))
  private def _exact_fields(fields: JsonObject, expected: Set[String], path: String): Unit = if (fields.keys.toSet != expected) _fail("DESCRIPTION_V2_FIELDS", path, s"must contain exactly: ${expected.toVector.sorted.mkString(", ")}")
  private def _id(value: String, path: String): String = if (_id_pattern.pattern.matcher(value).matches()) value else _fail("DESCRIPTION_V2_ID", path, "must be a nonempty stable identifier")
  private def _text(value: String, path: String): String = if (value.nonEmpty && value == value.trim) value else _fail("DESCRIPTION_V2_WORDING", path, "must be nonempty and trimmed")
  private def _locale(value: String, path: String): String = if (_locale_pattern.pattern.matcher(value).matches()) value else _fail("DESCRIPTION_V2_LOCALE", path, "must be a declared BCP-47 language tag")
  private def _identity_value(value: String, path: String): String = if (_identity_pattern.pattern.matcher(value).matches()) value else _fail("DESCRIPTION_V2_IDENTITY", path, "must be a sha256:<64 lowercase hexadecimal characters> identity")
  private def _add_id(ids: mutable.Set[String], id: String, path: String): Unit = if (!ids.add(id)) _fail("DESCRIPTION_V2_IDENTITY", path, s"duplicate document identity: $id")
  private def _unique(values: Vector[String], path: String, label: String): Unit = values.groupBy(identity).collectFirst { case (value, duplicates) if duplicates.size > 1 => value }.foreach(value => _fail("DESCRIPTION_V2_IDENTITY", path, s"duplicate $label: $value"))
  private def _fail(code: String, path: String, reason: String): Nothing = throw DescriptionV2Fault(code, path, reason)
}
