package cozy.modeler

import org.goldenport.kaleidox.{Model => KaleidoxModel}
import org.goldenport.parser._
import play.api.libs.json._
import scala.util.control.NonFatal

/*
 * @since   Oct. 18, 2026
 * @version Oct. 18, 2026
 * @author  ASAMI, Tomoharu
 */
/** Ordinary CML AST to typed source data; CNCF owns declaration semantics. */
private[modeler] object ValidationMetadataCml {
  final case class Diagnostic(code: String, path: String, reason: String)
  final case class ProviderName(packageName: String, className: String) {
    def fullName: String = packageName + "." + className
  }
  final case class SourceDeclaration(provider: ProviderName, properties: Properties)
  final case class FeatureReference(scope: String, identity: String)
  final case class DurationValue(amount: Long, unit: DurationUnit)
  final case class ExecutionDisposition(
    placement: ExecutionPlacement,
    independence: ExecutionIndependence,
    originContextIdentity: String
  )
  final case class ExecutionRequirement(
    capabilities: Vector[String],
    risk: String,
    reasoning: ReasoningLevel,
    reviewRequired: Boolean,
    reasoningClass: Option[String],
    disposition: Option[ExecutionDisposition]
  )
  final case class Properties(
    abiVersion: Int = 1,
    purposes: Option[Set[String]] = None,
    features: Set[FeatureReference] = Set.empty,
    expectedDuration: Option[DurationValue] = None,
    durationClass: Option[String] = None,
    executionRequirement: Option[ExecutionRequirement] = None,
    requiredSemantics: Set[String] = Set.empty
  )
  sealed abstract class DurationUnit(val label: String)
  object DurationUnit {
    case object Nanoseconds extends DurationUnit("NANOSECONDS")
    case object Microseconds extends DurationUnit("MICROSECONDS")
    case object Milliseconds extends DurationUnit("MILLISECONDS")
    case object Seconds extends DurationUnit("SECONDS")
    case object Minutes extends DurationUnit("MINUTES")
    case object Hours extends DurationUnit("HOURS")
    case object Days extends DurationUnit("DAYS")
    val values: Vector[DurationUnit] = Vector(Nanoseconds, Microseconds, Milliseconds, Seconds, Minutes, Hours, Days)
  }
  sealed abstract class ReasoningLevel(val label: String, val constructor: String)
  object ReasoningLevel {
    case object Routine extends ReasoningLevel("ROUTINE", "Routine")
    case object Standard extends ReasoningLevel("STANDARD", "Standard")
    case object Deep extends ReasoningLevel("DEEP", "Deep")
    case object Critical extends ReasoningLevel("CRITICAL", "Critical")
    val values: Vector[ReasoningLevel] = Vector(Routine, Standard, Deep, Critical)
  }
  sealed abstract class ExecutionPlacement(val label: String, val constructor: String)
  object ExecutionPlacement {
    case object Inline extends ExecutionPlacement("INLINE", "Inline")
    case object Delegated extends ExecutionPlacement("DELEGATED", "Delegated")
    val values: Vector[ExecutionPlacement] = Vector(Inline, Delegated)
  }
  sealed abstract class ExecutionIndependence(val label: String, val constructor: String)
  object ExecutionIndependence {
    case object Optional extends ExecutionIndependence("OPTIONAL", "Optional")
    case object Required extends ExecutionIndependence("REQUIRED", "Required")
    val values: Vector[ExecutionIndependence] = Vector(Optional, Required)
  }
  private final case class SectionInput(properties: Map[String, JsValue], children: Vector[LogicalSection])
  private val _property_line = "(?s)^\\s*([A-Za-z][A-Za-z0-9]*)\\s*=\\s*(.+?)\\s*$".r
  private val _identifier = "[A-Za-z_][A-Za-z0-9_]*".r
  private val _class_name = "[A-Z][A-Za-z0-9]*".r
  private val _keywords = Set("abstract", "case", "catch", "class", "def", "do", "else", "enum", "export",
    "extends", "false", "final", "finally", "for", "forSome", "given", "if", "implicit", "import", "inline",
    "lazy", "macro", "match", "new", "null", "object", "opaque", "open", "override", "package", "private", "protected",
    "return", "sealed", "super", "then", "this", "throw", "trait", "transparent", "true", "try", "type",
    "using", "val", "var", "while", "with", "yield", "end", "extension", "infix", "derives", "as", "_")

  def fromModel(model: KaleidoxModel): Either[Diagnostic, Vector[SourceDeclaration]] = {
    if (model == null || model.divisions == null || model.divisions.contains(null) || model.errors == null)
      _element("validationMetadata", "null ordinary model shape")
    else if (model.errors.nonEmpty) _invalid("validationMetadata", "ordinary model parser reported invalid input")
    else {
      val documents = model.divisions.collect { case division: KaleidoxModel.DocumentDivision => division.section }
      _sequence(documents.zipWithIndex) { case (section, index) =>
        _shape(section, s"validationMetadata[$index]").map(_ => section)
      }.flatMap { checked =>
        val roots = checked.filter(section => _title(section) == "VALIDATION-METADATA")
        _sequence(roots.zipWithIndex) { case (root, index) => _root(root, s"validationMetadata[$index]") }
          .flatMap { groups =>
            groups.flatten.foldLeft[Either[Diagnostic, Vector[SourceDeclaration]]](Right(Vector.empty)) {
              case (result, (declaration, path)) => result.flatMap { declarations =>
                if (declarations.exists(_.provider == declaration.provider))
                  Left(Diagnostic("VALIDATION_METADATA_PROVIDER_DUPLICATE", path + ".provider", "duplicate full provider name"))
                else Right(declarations :+ declaration)
              }
            }
          }
      }
    }
  }

  private def _root(section: LogicalSection, path: String): Either[Diagnostic, Vector[(SourceDeclaration, String)]] =
    _input(section, path, Set.empty).flatMap { input =>
      _sequence(input.children.zipWithIndex) { case (child, index) =>
        val childpath = s"$path.sections[$index]"
        if (_title(child) != "DECLARATION") _invalid(childpath, "expected immediate DECLARATION")
        else _declaration(child, childpath).map(declaration => declaration -> childpath)
      }
    }

  private def _declaration(section: LogicalSection, path: String): Either[Diagnostic, SourceDeclaration] =
    for {
      input <- _input(section, path, Set("provider", "properties"))
      _ <- if (input.children.isEmpty) Right(()) else _invalid(path + ".sections[0]", "declaration cannot contain child sections")
      provider <- _required(input.properties, "provider", path)(_string).flatMap(_provider(_, path + ".provider"))
      properties <- _required(input.properties, "properties", path)(_properties)
    } yield SourceDeclaration(provider, properties)

  private def _provider(value: String, path: String): Either[Diagnostic, ProviderName] = {
    val segments = value.split("\\.", -1).toVector
    if (segments.size < 2 || !segments.forall(segment => _identifier.pattern.matcher(segment).matches() &&
        !_keywords.contains(segment)) || !_class_name.pattern.matcher(segments.last).matches())
      Left(Diagnostic("VALIDATION_METADATA_PROVIDER_INVALID", path, "expected explicit qualified Scala package and UpperCamel class name"))
    else Right(ProviderName(segments.init.mkString("."), segments.last))
  }

  private def _properties(value: JsValue, path: String): Either[Diagnostic, Properties] =
    for {
      fields <- _object(value, path, Set("abiVersion", "purposes", "features", "expectedDuration", "durationClass",
        "executionRequirement", "requiredSemantics"))
      abi <- _optional(fields, "abiVersion", path)(_int).map(_.getOrElse(1))
      purposes <- _optional(fields, "purposes", path)(_strings).map(_.map(_.toSet))
      features <- _optional(fields, "features", path) { (member, memberpath) =>
        _array(member, memberpath)(_feature)
      }.map(_.getOrElse(Vector.empty).toSet)
      duration <- _optional(fields, "expectedDuration", path)(_duration)
      durationclass <- _optional(fields, "durationClass", path)(_string)
      requirement <- _optional(fields, "executionRequirement", path)(_requirement)
      semantics <- _optional(fields, "requiredSemantics", path)(_strings).map(_.getOrElse(Vector.empty).toSet)
    } yield Properties(abi, purposes, features, duration, durationclass, requirement, semantics)

  private def _feature(value: JsValue, path: String): Either[Diagnostic, FeatureReference] =
    for {
      fields <- _object(value, path, Set("scope", "identity"))
      scope <- _required(fields, "scope", path)(_string)
      identity <- _required(fields, "identity", path)(_string)
    } yield FeatureReference(scope, identity)

  private def _duration(value: JsValue, path: String): Either[Diagnostic, DurationValue] =
    for {
      fields <- _object(value, path, Set("amount", "unit"))
      amount <- _required(fields, "amount", path)(_long)
      label <- _required(fields, "unit", path)(_string)
      unit <- _label(label, path + ".unit", DurationUnit.values.map(unit => unit.label -> unit))
    } yield DurationValue(amount, unit)

  private def _requirement(value: JsValue, path: String): Either[Diagnostic, ExecutionRequirement] =
    for {
      fields <- _object(value, path, Set("capabilities", "risk", "reasoning", "reviewRequired", "reasoningClass", "disposition"))
      capabilities <- _required(fields, "capabilities", path)(_strings)
      risk <- _required(fields, "risk", path)(_string)
      label <- _required(fields, "reasoning", path)(_string)
      reasoning <- _label(label, path + ".reasoning", ReasoningLevel.values.map(level => level.label -> level))
      review <- _required(fields, "reviewRequired", path)(_boolean)
      reasoningclass <- _optional(fields, "reasoningClass", path)(_string)
      disposition <- _optional(fields, "disposition", path)(_disposition)
    } yield ExecutionRequirement(capabilities, risk, reasoning, review, reasoningclass, disposition)

  private def _disposition(value: JsValue, path: String): Either[Diagnostic, ExecutionDisposition] =
    for {
      fields <- _object(value, path, Set("placement", "independence", "originContextIdentity"))
      placementlabel <- _required(fields, "placement", path)(_string)
      placement <- _label(placementlabel, path + ".placement", ExecutionPlacement.values.map(value => value.label -> value))
      independencelabel <- _required(fields, "independence", path)(_string)
      independence <- _label(independencelabel, path + ".independence", ExecutionIndependence.values.map(value => value.label -> value))
      origin <- _required(fields, "originContextIdentity", path)(_string)
    } yield ExecutionDisposition(placement, independence, origin)

  private def _label[A](value: String, path: String, labels: Vector[(String, A)]): Either[Diagnostic, A] =
    labels.find(_._1 == value).map(pair => Right(pair._2)).getOrElse(_invalid(path, "unsupported exact finite ABI label"))

  private def _object(value: JsValue, path: String, allowed: Set[String]): Either[Diagnostic, Map[String, JsValue]] = value match {
    case obj: JsObject => obj.fields.find(pair => !allowed.contains(pair._1)) match {
      case Some((key, _)) => _invalid(path + "." + key, "unknown case-sensitive property")
      case None => Right(obj.fields.toMap)
    }
    case _ => _invalid(path, "expected JSON object")
  }
  private def _required[A](fields: Map[String, JsValue], key: String, path: String)(
      decode: (JsValue, String) => Either[Diagnostic, A]): Either[Diagnostic, A] = fields.get(key) match {
    case None => _invalid(path + "." + key, "required property is absent")
    case Some(value) => decode(value, path + "." + key)
  }
  private def _optional[A](fields: Map[String, JsValue], key: String, path: String)(
      decode: (JsValue, String) => Either[Diagnostic, A]): Either[Diagnostic, Option[A]] = fields.get(key) match {
    case None => Right(None)
    case Some(value) => decode(value, path + "." + key).map(Some(_))
  }
  private def _array[A](value: JsValue, path: String)(decode: (JsValue, String) => Either[Diagnostic, A]): Either[Diagnostic, Vector[A]] =
    value match {
      case JsArray(values) => _sequence(values.toVector.zipWithIndex) { case (member, index) => decode(member, s"$path[$index]") }
      case _ => _invalid(path, "expected JSON array")
    }
  private def _strings(value: JsValue, path: String): Either[Diagnostic, Vector[String]] = _array(value, path)(_string)
  private def _string(value: JsValue, path: String): Either[Diagnostic, String] = value match {
    case JsString(text) => Right(text)
    case _ => _invalid(path, "expected JSON string")
  }
  private def _int(value: JsValue, path: String): Either[Diagnostic, Int] = value match {
    case JsNumber(number) if number.isWhole && number.isValidInt => Right(number.toInt)
    case _ => _invalid(path, "expected exact Int JSON number")
  }
  private def _long(value: JsValue, path: String): Either[Diagnostic, Long] = value match {
    case JsNumber(number) if number.isWhole && number.isValidLong => Right(number.toLong)
    case _ => _invalid(path, "expected exact Long JSON number")
  }
  private def _boolean(value: JsValue, path: String): Either[Diagnostic, Boolean] = value match {
    case JsBoolean(boolean) => Right(boolean)
    case _ => _invalid(path, "expected JSON boolean")
  }

  private def _input(section: LogicalSection, path: String, allowed: Set[String]): Either[Diagnostic, SectionInput] = {
    val blocks = section.blocks.blocks
    val firstchild = blocks.indexWhere(_.isInstanceOf[LogicalSection])
    val prologue = if (firstchild < 0) blocks else blocks.take(firstchild)
    val remainder = if (firstchild < 0) Vector.empty else blocks.drop(firstchild)
    val spill = remainder.exists {
      case paragraph: LogicalParagraph => paragraph.lines.lines.exists(_.text.trim.nonEmpty)
      case _: LogicalSection | StartBlock | EndBlock => false
      case _ => true
    }
    if (spill) _invalid(path, "non-section child spill after first child")
    else _sequence(prologue) {
      case paragraph: LogicalParagraph => Right(paragraph.lines.lines)
      case StartBlock | EndBlock => Right(Vector.empty[LogicalLine])
      case _ => _invalid(path, "unsupported prologue block")
    }.flatMap { groups =>
      groups.flatten.filter(_.text.trim.nonEmpty).foldLeft[Either[Diagnostic, Map[String, JsValue]]](Right(Map.empty)) {
        case (result, line) => result.flatMap { properties =>
          line.text match {
            case _property_line(key, literal) =>
              val memberpath = path + "." + key
              if (!allowed.contains(key)) _invalid(memberpath, "unknown case-sensitive property")
              else if (properties.contains(key)) _invalid(memberpath, "repeated direct property")
              else if (line.physicalLines.size > 1 || literal.contains('\n') || literal.contains('\r'))
                _invalid(memberpath, "expected single-line JSON literal")
              else _literal(literal, memberpath).map(value => properties + (key -> value))
            case _ => _invalid(path, "expected one key = JSON literal assignment per line")
          }
        }
      }.map(properties => SectionInput(properties, remainder.collect { case child: LogicalSection => child }))
    }
  }
  private def _literal(value: String, path: String): Either[Diagnostic, JsValue] =
    try Right(Json.parse(value)) catch {
      case NonFatal(_) => _invalid(path, "invalid JSON literal")
    }

  private def _shape(section: LogicalSection, path: String): Either[Diagnostic, Unit] = {
    if (section == null || section.title == null || section.blocks == null || section.blocks.blocks == null ||
      section.mark == null || section.mark.contains(null) || section.location == null || section.location.contains(null))
      _element(path, "null section or optional AST shape")
    else _title_shape(section, path).flatMap { _ =>
      _sequence(section.blocks.blocks.zipWithIndex) { case (block, index) =>
        val blockpath = s"$path.blocks[$index]"
        block match {
          case null => _element(blockpath, "null block")
          case child: LogicalSection => _shape(child, blockpath)
          case paragraph: LogicalParagraph =>
            if (paragraph.lines == null || paragraph.lines.lines == null) _element(blockpath, "null paragraph lines")
            else _sequence(paragraph.lines.lines) { line =>
              if (line == null || line.text == null || line.physicalLines == null || line.physicalLines.contains(null) ||
                line.location == null || line.location.contains(null)) _element(blockpath, "null logical line shape")
              else Right(())
            }.map(_ => ())
          case _ => Right(())
        }
      }.map(_ => ())
    }
  }
  private def _title_shape(section: LogicalSection, path: String): Either[Diagnostic, Unit] =
    try {
      if (section.title.toI18NString == null || section.title.toI18NString.en == null) _element(path, "null section title")
      else Right(())
    } catch {
      case _: NullPointerException => _element(path, "null section title representation")
    }
  private def _title(section: LogicalSection): String = section.title.toI18NString.en
  private def _sequence[A, B](values: Vector[A])(decode: A => Either[Diagnostic, B]): Either[Diagnostic, Vector[B]] =
    values.foldLeft[Either[Diagnostic, Vector[B]]](Right(Vector.empty)) { (result, value) =>
      result.flatMap(elements => decode(value).map(elements :+ _))
    }
  private def _invalid[A](path: String, reason: String): Either[Diagnostic, A] =
    Left(Diagnostic("VALIDATION_METADATA_CML_INVALID", path, reason))
  private def _element[A](path: String, reason: String): Either[Diagnostic, A] =
    Left(Diagnostic("VALIDATION_METADATA_ELEMENT_INVALID", path, reason))
}
