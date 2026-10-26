package cozy.modeler

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.util.control.NonFatal
import play.api.libs.json._
import CmlModelEditModel._
import CmlModelEditJson._

/*
 * @since   Oct. 27, 2026
 * @version Oct. 27, 2026
 */
object CmlModelEditProduction {
  private val _keys = Set("schemaVersion", "source", "coverage", "elements", "relationships")
  private val _lifecycles = Set("Proposed", "InDevelopment", "Implemented", "Abandoned")

  def executingProducer: Producer = Producer("cozy", org.simplemodeling.cozy.BuildInfo.version,
    org.simplemodeling.cozy.BuildInfo.sourceRevision, org.simplemodeling.cozy.BuildInfo.sourceModified)

  def produce(path: Path, source: Source): Either[Vector[Diagnostic], Publication] = {
    val sourceissues = _source_issues(source, "source")
    if (sourceissues.nonEmpty) return Left(sourceissues)
    val selected = path.toAbsolutePath.normalize()
    if (!selected.getFileName.toString.endsWith(".cml") && !selected.getFileName.toString.endsWith(".dox"))
      return Left(Vector(Diagnostic("unsupported", "source", "Selected source must be CML or Dox.")))
    val header = CmlCapabilitySource.CapabilitySource(source.owner, selected.getFileName.toString, source.revision, None)
    CmlCapabilityProductionSourcePart._acquire(Vector(header), selected.getParent, None).
      left.map(_.map(x => Diagnostic("unavailable", x.fieldpath, x.detail))).flatMap { inputs =>
        val model = inputs.head.model
        if (model.errors.nonEmpty) Left(Vector(Diagnostic("parse-error", "source", "CML parser reported errors.")))
        else {
          val roots = CmlCapabilityCmlSectionsPart._original_roots(model)
          def _reserved_(section: org.goldenport.parser.LogicalSection, top: Boolean): Boolean =
            (section.nameForModel.equalsIgnoreCase("MODEL-EDIT") && (!top || section.nameForModel != "MODEL-EDIT")) ||
              section.blocks.sections.exists(x => _reserved_(x, false))
          if (roots.exists(x => _reserved_(x, true)))
            return Left(Vector(Diagnostic("unsupported", "MODEL-EDIT", "Reserved model-edit syntax has unsupported placement or capitalization.")))
          val matches = roots.filter(_.nameForModel == "MODEL-EDIT")
          if (matches.size > 1) Left(Vector(Diagnostic("unsupported", "MODEL-EDIT", "Exactly one MODEL-EDIT root is supported.")))
          else matches.headOption match {
            case None =>
              val declared = roots.map(_.nameForModel.toUpperCase(java.util.Locale.ROOT))
              val legacy = Map("ACTOR" -> "actor", "SERVICE" -> "operation", "USECASE" -> "use-case",
                "USE CASE" -> "use-case", "ENTITY" -> "entity", "EVENT" -> "domain-event",
                "COMPONENT" -> "component", "WORKFLOW" -> "reaction", "COMMAND" -> "command",
                "OPERATION" -> "operation", "QUERY" -> "query")
              val unsupported = declared.flatMap(legacy.get).toSet
              val coverage = families.map(x => Coverage(x, if (unsupported(x)) Unsupported else Missing,
                Some("No source-owned MODEL-EDIT declaration supplies this family.")))
              Right(Publication(publicationVersion, source, executingProducer,
                Document(schemaVersion, coverage, Vector.empty, Vector.empty),
                Vector(Limitation("missing-model-edit", "Legacy declarations do not supply model-edit identities or relationships."))))
            case Some(section) =>
              if (section.blocks.sections.nonEmpty)
                Left(Vector(Diagnostic("unsupported", "MODEL-EDIT", "MODEL-EDIT requires a leaf of key = JSON-value records.")))
              else {
                val lines = section.blocks.prologue.text.split("\n", -1).toVector.filter(_.trim.nonEmpty)
                val parsed = lines.map { line =>
                  val separator = line.indexOf('=')
                  if (separator < 1) Left(Diagnostic("unsupported", "MODEL-EDIT", "Expected one key = JSON-value line."))
                  else try Right(line.take(separator).trim -> Json.parse(line.drop(separator + 1).trim))
                  catch { case NonFatal(_) => Left(Diagnostic("malformed", "MODEL-EDIT", "Invalid JSON source record.")) }
                }
                val errors = parsed.collect { case Left(error) => error }
                val fields = parsed.collect { case Right(field) => field }
                if (errors.nonEmpty) Left(errors)
                else if (fields.map(_._1).toSet != _keys || fields.map(_._1).distinct.size != fields.size)
                  Left(Vector(Diagnostic("unsupported", "MODEL-EDIT", "Unknown, repeated or missing source declaration field.")))
                else {
                  val root = JsObject(fields)
                  for {
                    declaredsource <- _decode[Source](root.value("source"), "source")
                    _ <- if (declaredsource == source) Right(()) else Left(Vector(Diagnostic("source-mismatch", "source", "Selected and declared source must match exactly.")))
                    document <- _decode[Document](root - "source", "document")
                    admitted <- _admit_document(document, source)
                  } yield Publication(publicationVersion, source, executingProducer, admitted, Vector.empty)
                }
              }
          }
        }
      }
  }

  def read(value: JsValue): Either[Vector[Diagnostic], Publication] =
    _decode[Publication](value, "publication").flatMap { publication =>
      if (publication.schemaVersion != publicationVersion)
        Left(Vector(Diagnostic("unsupported", "schemaVersion", "Unsupported model-edit publication version.")))
      else {
        val issues = _source_issues(publication.source, "source") ++
          (if (publication.producer.name != "cozy" || !_text(publication.producer.version) || !_text(publication.producer.revision))
            Vector(Diagnostic("malformed", "producer", "Complete declared producer information is required.")) else Vector.empty)
        if (issues.nonEmpty) Left(issues)
        else _admit_document(publication.document, publication.source).map(x => publication.copy(document = x))
      }
    }

  def readCandidate(value: JsValue): Either[Vector[Diagnostic], Candidate] =
    _decode[Candidate](value, "candidate").flatMap { candidate =>
      if (candidate.schemaVersion != candidateVersion)
        Left(Vector(Diagnostic("unsupported", "schemaVersion", "Unsupported model-edit candidate version.")))
      else {
        val issues = _source_issues(candidate.source, "candidate.source")
        if (issues.nonEmpty) Left(issues) else Right(candidate)
      }
    }

  def renderCandidate(path: Path, source: Source, candidate: Candidate): Either[Vector[Diagnostic], Rendering] =
    for {
      checked <- readCandidate(Json.toJson(candidate))
      basis <- produce(path, source)
      _ <- if (checked.source.owner == source.owner && checked.source.identity == source.identity && checked.source.revision != source.revision)
        Right(()) else Left(Vector(Diagnostic("source-mismatch", "candidate.source", "Declare a successor revision of the same owned source.")))
      _ <- if (basis.limitations.isEmpty) Right(()) else Left(Vector(Diagnostic("unsupported", "source", "Only an existing admitted MODEL-EDIT block can be edited.")))
      result <- _edit(basis.document, source, checked)
      rendered <- _render(path, checked.source, result)
    } yield Rendering(candidateVersion, source, executingProducer, checked.source, rendered, Vector.empty)

  private def _edit(document: Document, source: Source, candidate: Candidate): Either[Vector[Diagnostic], Document] = {
    val issues = Vector.newBuilder[Diagnostic]
    def _issue_(path: String, detail: String): Unit = issues += Diagnostic("invalid-edit", path, detail)
    def _owned_(expected: RecordRef, replacement: RecordRef, attribution: Source, path: String): Unit = {
      if (expected.owner != source.owner || attribution.owner != source.owner || attribution.identity != source.identity)
        _issue_(path, "Only explicit existing targets owned by the selected source can be edited.")
      if (replacement.owner != expected.owner || replacement.identity != expected.identity || replacement.revision <= expected.revision)
        _issue_(path, "Preserve target identity and declare a higher logical revision.")
    }
    val elementtargets = candidate.elements.map(_.expected)
    val relationtargets = candidate.relationships.map(_.expected) ++ candidate.removedRelationships
    if (elementtargets.distinct.size != elementtargets.size || relationtargets.distinct.size != relationtargets.size)
      _issue_("candidate", "Each exact target can be edited once.")
    candidate.elements.foreach { edit =>
      document.elements.find(_.ref == edit.expected) match {
        case None => _issue_("elements", "The exact existing element target is missing.")
        case Some(original) =>
          _owned_(edit.expected, edit.replacement.ref, original.source, "elements")
          if (original.kind != edit.replacement.kind) _issue_("elements.kind", "Element kind cannot change during refinement.")
          if (edit.replacement.source != candidate.source) _issue_("elements.source", "Edited records must declare the successor source.")
      }
    }
    candidate.relationships.foreach { edit =>
      document.relationships.find(_.ref == edit.expected) match {
        case None => _issue_("relationships", "The exact existing relationship target is missing.")
        case Some(original) =>
          _owned_(edit.expected, edit.replacement.ref, original.source, "relationships")
          if (original.kind != edit.replacement.kind) _issue_("relationships.kind", "Relationship kind cannot change during refinement.")
          if (edit.replacement.source != candidate.source) _issue_("relationships.source", "Edited relationships must declare the successor source.")
      }
    }
    candidate.removedRelationships.foreach { ref =>
      document.relationships.find(_.ref == ref) match {
        case Some(original) if ref.owner == source.owner && original.source.owner == source.owner && original.source.identity == source.identity => ()
        case _ => _issue_("removedRelationships", "Select an exact existing relationship owned by this source.")
      }
    }
    val errors = issues.result()
    if (errors.nonEmpty) Left(errors)
    else {
      val elements = document.elements.map(x => candidate.elements.find(_.expected == x.ref).map(_.replacement).getOrElse(x))
      val relationships = document.relationships.filterNot(x => candidate.removedRelationships.contains(x.ref)).
        map(x => candidate.relationships.find(_.expected == x.ref).map(_.replacement).getOrElse(x))
      _admit_document(document.copy(elements = elements, relationships = relationships), candidate.source)
    }
  }

  private def _admit_document(document: Document, source: Source): Either[Vector[Diagnostic], Document] = {
    val issues = Vector.newBuilder[Diagnostic]
    def _issue_(path: String, detail: String): Unit = issues += Diagnostic("invalid-model", path, detail)
    if (document.schemaVersion != schemaVersion) return Left(Vector(Diagnostic("unsupported", "document.schemaVersion", "Unsupported model-edit source schema.")))
    if (document.coverage.map(_.family).distinct.size != document.coverage.size || document.coverage.exists(x => !families.contains(x.family)))
      _issue_("coverage", "Coverage families must be known and unique.")
    document.coverage.foreach { item =>
      if (item.status != Present && !item.detail.exists(_text)) _issue_("coverage", "Non-present coverage requires an explicit detail.")
    }
    val refs = document.elements.map(_.ref)
    if (refs.map(x => x.owner -> x.identity).distinct.size != refs.size)
      _issue_("elements", "Each stable element identity must be unique, independent of names and order.")
    val edges = document.relationships.map(_.ref)
    if (edges.map(x => x.owner -> x.identity).distinct.size != edges.size)
      _issue_("relationships", "Each relationship owns a unique stable identity.")
    def _ref_(ref: RecordRef, path: String): Unit =
      if (!_text(ref.owner) || !_text(ref.identity) || ref.revision <= 0) _issue_(path, "A complete explicit positive record reference is required.")
    def _coverage_(family: String): Unit =
      if (!document.coverage.exists(x => x.family == family && x.status == Present))
        _issue_(family, "Supplied records require explicit present coverage.")
    document.elements.foreach { element =>
      _ref_(element.ref, "elements.ref")
      issues ++= _source_issues(element.source, "elements.source")
      _coverage_(element.kind.name)
      if (!_text(element.title)) _issue_("elements.title", "A declared title is required; it is never an identity.")
      if (Set[ElementKind](Goal, Slice)(element.kind) && !element.intent.exists(_text))
        _issue_("elements.intent", "Goals and Slices require independent declared intent.")
      if (element.kind == Slice && !element.lifecycle.exists(_lifecycles))
        _issue_("elements.lifecycle", "Slice lifecycle must be explicitly supported.")
      if (element.kind != Slice && element.lifecycle.nonEmpty) _issue_("elements.lifecycle", "Lifecycle is a Slice field.")
      if (element.fields.map(_.name).distinct.size != element.fields.size) _issue_("elements.fields", "Fields must have distinct declared names.")
      element.fields.foreach { field =>
        if (!_text(field.name) || !_text(field.datatype) || !Set("1", "?", "*", "+")(field.multiplicity))
          _issue_("elements.fields", "Field name, datatype and supported multiplicity are required.")
      }
      (element.scope ++ element.acceptance).foreach(_ref_(_, "elements.references"))
    }
    def _endpoint_(endpoint: Endpoint): Unit = endpoint match {
      case LocalEndpoint(ref) =>
        _ref_(ref, "relationships.endpoint")
        if (!refs.contains(ref)) _issue_("relationships.endpoint", "The exact local endpoint revision is missing.")
      case ExternalEndpoint(ref) => _ref_(ref, "relationships.external")
      case SpecificationEndpoint(specification) =>
        if (!_text(specification.owner) || !_text(specification.identity) || !_text(specification.sourceRevision))
          _issue_("relationships.specification", "Specification owner, identity and source revision are required.")
      case TicketEndpoint(ticket) =>
        if (!_text(ticket.provider) || !_text(ticket.owner) || !_text(ticket.externalIdentity) ||
          ticket.observation.exists(x => !_text(x.state) || x.revision.exists(x => !_text(x))))
          _issue_("relationships.ticket", "Ticket immutable identity and separate observation must be explicit.")
    }
    def _kind_(endpoint: Endpoint, kind: ElementKind): Boolean = endpoint match {
      case LocalEndpoint(ref) => document.elements.exists(x => x.ref == ref && x.kind == kind)
      case _ => false
    }
    document.relationships.foreach { relation =>
      _ref_(relation.ref, "relationships.ref")
      issues ++= _source_issues(relation.source, "relationships.source")
      _coverage_(relation.kind.name)
      _endpoint_(relation.from)
      _endpoint_(relation.to)
      val valid = relation.kind match {
        case ActorGoal => _kind_(relation.from, Actor) && _kind_(relation.to, Goal)
        case GoalUseCase => _kind_(relation.from, Goal) && _kind_(relation.to, UseCase)
        case UseCaseSlice => _kind_(relation.from, UseCase) && _kind_(relation.to, Slice)
        case SliceSpecification => _kind_(relation.from, Slice) && relation.to.isInstanceOf[SpecificationEndpoint]
        case SliceTicket => _kind_(relation.from, Slice) && relation.to.isInstanceOf[TicketEndpoint]
        case Cause => (relation.from, relation.to) match {
          case (LocalEndpoint(_), LocalEndpoint(_)) => true
          case _ => false
        }
        case Reaction => _kind_(relation.from, DomainEvent) && _kind_(relation.to, Policy)
        case Dependency => relation.to.isInstanceOf[ExternalEndpoint] || _kind_(relation.to, Component)
        case Context => true
      }
      if (!valid) _issue_("relationships.kind", "Relationship endpoints do not match the declared semantic kind.")
    }
    val errors = issues.result()
    if (errors.nonEmpty) Left(errors)
    else {
      val complete = families.map(family => document.coverage.find(_.family == family).
        getOrElse(Coverage(family, Missing, Some("This family is not declared."))))
      Right(document.copy(coverage = complete))
    }
  }

  private def _render(path: Path, successor: Source, document: Document): Either[Vector[Diagnostic], String] =
    try {
      val text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
      val lines = text.split("(?<=\n)", -1).toVector
      val model = org.goldenport.kaleidox.Model.parse(org.goldenport.kaleidox.Config.default, text)
      val roots = CmlCapabilityCmlSectionsPart._original_roots(model)
      val locations = roots.map(_.location.flatMap(_.line).map(_ - 1))
      val selected = roots.filter(_.nameForModel == "MODEL-EDIT")
      if (model.errors.nonEmpty || selected.size != 1 || locations.exists(_.isEmpty) ||
        locations.flatten.exists(x => x < 0 || x >= lines.size))
        Left(Vector(Diagnostic("unsupported", "source", "Candidate rendering requires exact parsed root locations.")))
      else {
        val start = selected.head.location.flatMap(_.line).get - 1
        if (lines(start).stripLineEnd != "# MODEL-EDIT")
          Left(Vector(Diagnostic("unsupported", "source", "Candidate rendering requires one exact top-level MODEL-EDIT header.")))
        else {
          val end = locations.flatten.sorted.find(_ > start).getOrElse(lines.size)
          val json = Json.toJson(document).as[JsObject] + ("source" -> Json.toJson(successor))
          val block = "# MODEL-EDIT\n\n" + Vector("schemaVersion", "source", "coverage", "elements", "relationships").
            map(key => key + " = " + Json.stringify(json.value(key))).mkString("\n") + "\n\n"
          Right(lines.take(start).mkString + block + lines.drop(end).mkString)
        }
      }
    } catch { case NonFatal(_) => Left(Vector(Diagnostic("unavailable", "source", "Selected CML could not be read for candidate rendering."))) }

  private def _decode[A: Reads](value: JsValue, path: String): Either[Vector[Diagnostic], A] =
    value.validate[A] match {
      case JsSuccess(result, _) => Right(result)
      case JsError(errors) => Left(errors.toVector.map { case (field, _) =>
        Diagnostic("malformed", path + field.toString, "Unsupported or malformed typed model-edit record.")
      })
    }
  private def _text(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
  private def _source_issues(source: Source, path: String): Vector[Diagnostic] =
    if (source == null || !_text(source.owner) || !_text(source.identity) || !_text(source.revision))
      Vector(Diagnostic("malformed", path, "Complete explicit source owner, identity and revision are required."))
    else Vector.empty
}
