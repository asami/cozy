package cozy.modeler

import java.util.Locale
import org.goldenport.kaleidox.{Model => KaleidoxModel}
import org.goldenport.parser.LogicalSection
import play.api.libs.json._
import CmlSemanticFoundation.Presence
import CmlCapabilityModel._
import CmlCapabilitySource._
import CmlCapabilityJsonPart._
import CmlCapabilityDefinitionJsonPart._
import CmlCapabilityDeclarationJsonPart._

/*
 * @since   Oct. 5, 2026
 * @version Oct. 5, 2026
 * @author  ASAMI, Tomoharu
 */
private[modeler] object CmlCapabilityCmlSectionsPart {
  private final case class Located(section: LogicalSection, path: Vector[String]) {
    def _context(scope: SourceScope): Context = Context(scope, path, section.location.flatMap(_.line), "")
    def _children: Vector[Located] = section.blocks.sections.toVector.map(x => Located(x, path :+ x.nameForModel))
  }
  private val _root = "CAPABILITY-MODEL"
  private val _containers = Vector("APPLICATION-CAPABILITY", "COMPONENT-CAPABILITY", "COMPONENT-CAPABILITY-DECLARATION")
  private val _reserved = _root +: _containers
  private val _common_fields = Vector("identity", "contractVersion", "name", "summary", "description", "source")
  private val _shared_fields = Vector("discoveryReferences", "grounding", "traceability")
  private val _application_fields = _common_fields ++ Vector("demandReferences", "discoveryReferences", "componentMappings", "dependencies", "grounding", "traceability")
  private val _component_fields = _common_fields ++ _shared_fields
  private val _declaration_fields = Vector("component", "direction", "capability", "source", "realizations", "traceability")

  def _document(input: SourceInput): Result[Option[CapabilityDocument]] = {
    val issues = Vector.newBuilder[SyntaxIssue]
    val applications = Vector.newBuilder[ApplicationCapability]
    val components = Vector.newBuilder[ComponentCapability]
    val declarations = Vector.newBuilder[ComponentDeclaration]
    var compatibility: Option[Presence[Vector[CompatibilityEdge]]] = None
    var found = false

    def _capture_[A](result: Result[A])(accept: A => Unit): Unit = result match {
      case Right(value) => accept(value)
      case Left(errors) => issues ++= errors
    }
    def _issue_(node: Located, message: String): Unit =
      issues += node._context(input.scope)._issue(InvalidStructure, message)
    def _outside_(node: Located): Unit = {
      if (_reserved.contains(node.section.nameForModel.toUpperCase(Locale.ROOT)))
        _issue_(node, "Reserved container has unsupported placement or capitalization.")
      node._children.foreach(_outside_)
    }
    def _leaf_[A](node: Located, fields: Vector[String], decoder: Decoder[A])(accept: A => Unit): Unit = {
      if (node._children.nonEmpty)
        _issue_(node, "Record entries must be leaves.")
      _capture_(_fields(node, input.scope, fields).flatMap(decoder(_, node._context(input.scope))))(accept)
    }
    def _capability_root_(node: Located): Unit = {
      if (found) _issue_(node, "Only one CAPABILITY-MODEL root is allowed.")
      found = true
      _capture_(_fields(node, input.scope, Vector("compatibility")).flatMap { fields =>
        _record(fields, node._context(input.scope), Vector(_entry("compatibility", _presence(_array(_compatibility)))))
          .map(_._get[Presence[Vector[CompatibilityEdge]]]("compatibility"))
      })(x => compatibility = Some(x))
      val seen = scala.collection.mutable.Set.empty[String]
      node._children.foreach { container =>
        val name = container.section.nameForModel
        if (!_containers.contains(name)) {
          _issue_(container, "Only exact direct Capability containers are allowed.")
          container._children.foreach(_outside_)
        } else {
          if (!seen.add(name)) _issue_(container, "Repeated Capability container.")
          if (container.section.blocks.prologue.text.trim.nonEmpty)
            _issue_(container, "Capability containers require an empty prologue.")
          container._children.foreach { entry =>
            // Entry names, even reserved-looking names, are presentation labels.
            name match {
              case "APPLICATION-CAPABILITY" => _leaf_(entry, _application_fields, _application)(applications += _)
              case "COMPONENT-CAPABILITY" => _leaf_(entry, _component_fields, _component)(components += _)
              case "COMPONENT-CAPABILITY-DECLARATION" => _leaf_(entry, _declaration_fields, _declaration)(declarations += _)
            }
          }
        }
      }
    }

    _original_roots(input.model).foreach { section =>
      val node = Located(section, Vector(section.nameForModel))
      if (section.nameForModel == _root) _capability_root_(node) else _outside_(node)
    }
    val errors = issues.result()
    if (errors.nonEmpty) Left(errors)
    else if (!found) Right(None)
    else Right(Some(CapabilityDocument(input.scope, CapabilityModel(applications.result(), components.result(),
      declarations.result(), compatibility.get))))
  }

  private def _fields(node: Located, scope: SourceScope, expected: Vector[String]): Result[JsObject] = {
    val context = node._context(scope)
    val seen = scala.collection.mutable.Set.empty[String]
    val results = node.section.blocks.prologue.text.split("\n", -1).toVector.filter(_.trim.nonEmpty).map { line =>
      val separator = line.indexOf('=')
      if (separator < 0) _failure[(String, JsValue)](context, "A single key = JSON-value line is required.")
      else {
        val key = line.take(separator).trim
        val location = context._child(key)
        if (!expected.contains(key)) _failure[(String, JsValue)](location, "Unknown Capability field.")
        else if (!seen.add(key)) _failure[(String, JsValue)](location, "Repeated Capability field.")
        else _json(line.drop(separator + 1).trim, location).map(key -> _)
      }
    }
    val missing = expected.filterNot(seen).map(key => _failure[(String, JsValue)](context._child(key), "Mandatory Capability field is missing."))
    _sequence(results ++ missing).map(x => JsObject(x))
  }

  private def _original_roots(model: KaleidoxModel): Vector[LogicalSection] = {
    val sections = model.divisions.flatMap {
      case product: Product => product.productIterator.collectFirst { case section: LogicalSection => section }
      case _ => None
    }
    def _contains_(parent: LogicalSection, child: LogicalSection): Boolean =
      parent.blocks.sections.exists(x => (x eq child) || _contains_(x, child))
    sections.filterNot(x => sections.exists(y => !(x eq y) && _contains_(y, x)))
  }
}
