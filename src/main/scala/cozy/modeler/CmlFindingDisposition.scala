package cozy.modeler

import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import org.goldenport.parser.{EndBlock, LogicalParagraph, LogicalSection, StartBlock}
import play.api.libs.json._
import scala.util.control.NonFatal

/** Native source properties; CNCF owns interpretation of the generated declarations.
 * @since   Nov.  5, 2026
 * @version Nov.  9, 2026
 */
object CmlFindingDisposition {
  final case class Owner(name: String, packageName: String, serviceNames: Vector[String]) {
    def scope: String = "component:" + packageName + "." + name
  }
  final case class Properties(
    abiVersion: Int, rule: String, finding: String, scope: String, target: String,
    kind: String, reason: String, provenance: String
  ) {
    def toJson: JsObject = Json.obj(
      "abiVersion" -> abiVersion, "rule" -> rule, "finding" -> finding,
      "scope" -> scope, "target" -> target, "kind" -> kind,
      "reason" -> reason, "provenance" -> provenance)
  }
  final case class SourceDeclaration(owner: Owner, identity: String, properties: Properties)
  final case class Diagnostic(code: String, declaration: String, property: Option[String], message: String)
  sealed abstract class CoverageStatus(val name: String)
  object CoverageStatus {
    case object Completed extends CoverageStatus("completed")
    case object Unsupported extends CoverageStatus("unsupported")
    case object Failed extends CoverageStatus("failed")
  }
  final case class Coverage(declaration: String, status: CoverageStatus)
  final case class Inspection(
    declarations: Vector[SourceDeclaration], findings: Vector[Diagnostic], coverage: Vector[Coverage]
  ) {
    def isValid: Boolean = findings.isEmpty
  }

  private val _property_line = "^\\s*([A-Za-z][A-Za-z0-9]*)\\s*=\\s*(.+?)\\s*$".r
  private val _fields = Set("abiVersion", "rule", "finding", "scope", "target", "kind", "reason", "provenance")
  private val _kinds = Set("fix", "accept", "defer", "configure")
  private val _identifier = "[A-Za-z_][A-Za-z0-9_]*".r

  def inspectCml(source: String): Inspection =
    inspect(KaleidoxModel.parse(KaleidoxConfig.default.withoutLocation, source))

  def inspect(model: KaleidoxModel): Inspection = {
    if (model.errors.nonEmpty)
      return Inspection(Vector.empty, Vector(Diagnostic("FD77_CML_INVALID", "findingDisposition", None,
        "ordinary CML parser reported invalid input: " + model.errors.mkString("; "))), Vector(Coverage("findingDisposition", CoverageStatus.Failed)))
    val roots = model.divisions.collect {
      case division: KaleidoxModel.DocumentDivision if _title(division.section) == "FINDING-DISPOSITION" => division.section
    }
    val rows = roots.flatMap(root => _children(root).flatMap(group =>
      _children(group).map(section => (_title(group), section))))
    val initial = Inspection(Vector.empty, Vector.empty, Vector.empty)
    val inspected = rows.foldLeft(initial) { case (inspection, (component, section)) =>
      val reference = component + "/" + _title(section)
      val decoded = for {
        owner <- _owner(model, component, reference)
        fields <- _properties(section, reference)
        properties <- _decode(fields, owner, model, reference)
        _ <- if (inspection.declarations.exists(d => d.owner == owner && d.identity == _title(section)))
          _invalid("FD77_DECLARATION_DUPLICATE", reference, None, "duplicate stable source declaration")
          else Right(())
        _ <- if (inspection.declarations.exists(d => _key(d.properties) == _key(properties)))
          _invalid("FD77_FINDING_DUPLICATE", reference, None, "duplicate exact rule/finding/scope/target key")
          else Right(())
      } yield SourceDeclaration(owner, _title(section), properties)
      decoded match {
        case Right(declaration) => inspection.copy(declarations = inspection.declarations :+ declaration,
          coverage = inspection.coverage :+ Coverage(reference, CoverageStatus.Completed))
        case Left(diagnostic) => inspection.copy(findings = inspection.findings :+ diagnostic,
          coverage = inspection.coverage :+ Coverage(reference,
            if (diagnostic.code == "FD77_TARGET_UNSUPPORTED") CoverageStatus.Unsupported else CoverageStatus.Failed))
      }
    }
    // Empty groups or root prose are selected input, not successful skipped checks.
    val shapeerrors = roots.flatMap { root =>
      val rooterrors = if (_lines(root).nonEmpty || _unexpected(root)) Vector(Diagnostic("FD77_CML_INVALID", "findingDisposition", None,
        "properties belong to an explicit component/declaration, not the root")) else Vector.empty
      rooterrors ++ _children(root).flatMap { group =>
        if (_children(group).isEmpty || _lines(group).nonEmpty || _unexpected(group))
          Vector(Diagnostic("FD77_CML_INVALID", _title(group), None, "expected explicit declaration sections"))
        else Vector.empty
      }
    }
    inspected.copy(findings = inspected.findings ++ shapeerrors,
      coverage = inspected.coverage ++ shapeerrors.map(d => Coverage(d.declaration, CoverageStatus.Failed)))
  }

  private def _owner(model: KaleidoxModel, name: String, reference: String): Either[Diagnostic, Owner] = try {
    val components = model.divisions.collect { case division: KaleidoxModel.ComponentDivision => division }
      .flatMap(_.makeModel(model.config).components).filter(_.name == name)
    if (components.size != 1 || components.head.packageName.isEmpty)
      _invalid("FD77_COMPONENT_INVALID", reference, None, "owner must bind exactly one COMPONENT with an explicit package")
    else {
      val owner = Owner(name, components.head.packageName.get, components.head.services.map(_.name))
      if (!name.matches("[A-Z][A-Za-z0-9]*") ||
        !owner.packageName.split("\\.", -1).forall(_identifier.pattern.matcher(_).matches))
        _invalid("FD77_COMPONENT_INVALID", reference, None, "component/package is not a source identifier")
      else Right(owner)
    }
  } catch {
    case NonFatal(_) => _invalid("FD77_COMPONENT_INVALID", reference, None, "ordinary component model cannot resolve the declared owner")
  }

  private def _properties(section: LogicalSection, reference: String): Either[Diagnostic, Map[String, JsValue]] = {
    if (_children(section).nonEmpty || _unexpected(section))
      return _invalid("FD77_CML_INVALID", reference, None, "declaration cannot contain nested sections")
    _lines(section).foldLeft[Either[Diagnostic, Map[String, JsValue]]](Right(Map.empty)) { (prior, line) =>
      prior.flatMap { fields => line match {
        case _property_line(key, literal) =>
          if (!_fields.contains(key)) _invalid("FD77_PROPERTY_UNKNOWN", reference, Some(key), "unknown case-sensitive property")
          else if (fields.contains(key)) _invalid("FD77_PROPERTY_DUPLICATE", reference, Some(key), "repeated property")
          else try Right(fields + (key -> Json.parse(literal))) catch {
            case NonFatal(_) => _invalid("FD77_CML_INVALID", reference, Some(key), "expected a single-line JSON literal")
          }
        case _ => _invalid("FD77_CML_INVALID", reference, None, "expected name = JSON literal")
      }}
    }
  }

  private def _decode(fields: Map[String, JsValue], owner: Owner, model: KaleidoxModel,
      reference: String): Either[Diagnostic, Properties] = for {
    version <- fields.get("abiVersion") match {
      case None => Right(1)
      case Some(JsNumber(value)) if value.isWhole && value.isValidInt && value.toInt == 1 => Right(1)
      case _ => _invalid("FD77_ABI_UNSUPPORTED", reference, Some("abiVersion"), "expected CNCF ABI version 1")
    }
    rule <- _text(fields, "rule", reference)
    finding <- _text(fields, "finding", reference)
    scope <- if (fields.contains("scope")) _text(fields, "scope", reference) else Right(owner.scope)
    _ <- if (scope == owner.scope) Right(())
      else _invalid("FD77_TARGET_INCOMPATIBLE", reference, Some("scope"), "scope differs from the actual CML owner")
    target <- _text(fields, "target", reference)
    _ <- _target(model, owner, target, reference)
    kind <- _text(fields, "kind", reference)
    _ <- if (_kinds.contains(kind)) Right(())
      else _invalid("FD77_KIND_INVALID", reference, Some("kind"), "expected exact CNCF V1 fix/accept/defer/configure")
    reason <- _text(fields, "reason", reference)
    provenance <- _text(fields, "provenance", reference)
  } yield Properties(version, rule, finding, scope, target, kind, reason, provenance)

  private def _text(fields: Map[String, JsValue], key: String, reference: String): Either[Diagnostic, String] =
    fields.get(key) match {
      case Some(JsString(value)) if value.nonEmpty && value.trim == value && !value.exists(Character.isISOControl) => Right(value)
      case _ => _invalid(if (key == "reason") "FD77_REASON_REQUIRED" else "FD77_PROPERTY_INVALID", reference,
        Some(key), "required nonblank trimmed, control-free JSON string")
    }

  private def _target(model: KaleidoxModel, owner: Owner, target: String,
      reference: String): Either[Diagnostic, Unit] = try {
    val services = model.getServiceModel.toVector.flatMap(_.classes.values.toVector)
      .filter(service => owner.serviceNames.contains(service.name))
    val targets = Set(owner.scope) ++ services.map(service => "service:" + service.name) ++
      services.flatMap(service => service.operations.operations.values.map(operation =>
        "operation:" + service.name + "." + operation.name))
    if (!Set("component", "service", "operation").contains(target.takeWhile(_ != ':')))
      _invalid("FD77_TARGET_UNSUPPORTED", reference, Some("target"), "target category is not supported by this CML producer")
    else if (!targets.contains(target))
      _invalid("FD77_TARGET_INCOMPATIBLE", reference, Some("target"), "target does not name an exact actual CML element")
    else Right(())
  } catch {
    case NonFatal(_) => _invalid("FD77_TARGET_INCOMPATIBLE", reference, Some("target"), "ordinary service model cannot resolve the declared target")
  }

  private def _key(properties: Properties): (String, String, String, String) =
    (properties.rule, properties.finding, properties.scope, properties.target)
  private def _title(section: LogicalSection): String = section.nameForModel
  private def _children(section: LogicalSection): Vector[LogicalSection] =
    section.blocks.sections
  private def _lines(section: LogicalSection): Vector[String] =
    section.blocks.prologue.text.split("\n", -1).toVector.filter(_.trim.nonEmpty)
  private def _unexpected(section: LogicalSection): Boolean = section.blocks.blocks.exists {
    case _: LogicalParagraph | _: LogicalSection | StartBlock | EndBlock => false
    case _ => true
  }
  private def _invalid[A](code: String, reference: String, property: Option[String],
      message: String): Either[Diagnostic, A] = Left(Diagnostic(code, reference, property, message))
}
