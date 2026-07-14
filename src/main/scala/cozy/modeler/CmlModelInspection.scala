package cozy.modeler

import java.nio.file.Path
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import org.goldenport.kaleidox.model.DataTypeModel.DataTypeClass
import org.goldenport.kaleidox.model.SchemaModel
import org.goldenport.record.v2.XString
import org.smartdox.parser.{Dox2Parser, DoxLinesParser}

/*
 * @since   Jul. 15, 2026
 * @version Jul. 15, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CmlModelInspection {
  sealed trait DeclarationKind {
    def name: String
  }
  object DeclarationKind {
    case object Entity extends DeclarationKind {
      val name = "entity"
    }
    case object Value extends DeclarationKind {
      val name = "value"
    }
    case object Datatype extends DeclarationKind {
      val name = "datatype"
    }
  }

  final case class Attribute(
    kind: DeclarationKind,
    owner: String,
    name: String,
    rawtypename: Option[String],
    line: Int
  ) {
    def isRawString: Boolean =
      rawtypename.exists(_is_string_name)
  }

  final case class StringScalar(
    kind: DeclarationKind,
    name: String,
    representation: String
  )

  final case class Result(
    attributes: Vector[Attribute],
    stringscalars: Vector[StringScalar]
  )

  def load(path: Path): Result = {
    val model = KaleidoxModel.load(KaleidoxConfig.default, path.toFile)
    from(model)
  }

  def from(model: KaleidoxModel): Result = {
    val declarationlines = _declaration_lines(model)
    val attributelines = _attribute_lines(model)
    val entityattributes = model.takeEntityModel.classes.values.toVector.flatMap { entity =>
      _attributes(DeclarationKind.Entity, entity.name, entity.schemaClass, declarationlines, attributelines)
    }
    val valueattributes = model.getValueModel.toVector.flatMap(_.classes.values).flatMap { value =>
      _attributes(DeclarationKind.Value, value.name, value.schemaClass, declarationlines, attributelines)
    }
    val valuescalars = model.getValueModel.toVector.flatMap(_.classes.values).flatMap { value =>
      val attributes = value.schemaClass.attributes
      if (attributes.length == 1 && attributes.head.name == "value" && _is_raw_string(attributes.head))
        Some(StringScalar(DeclarationKind.Value, value.name, "value:string"))
      else
        None
    }
    val datatypescalars = model.takeDataTypeModel.classes.values.toVector.flatMap {
      case datatype if _is_string_datatype(datatype) =>
        Some(StringScalar(DeclarationKind.Datatype, datatype.name, "value:string"))
      case _ =>
        None
    }
    Result(
      attributes = entityattributes ++ valueattributes,
      stringscalars = valuescalars ++ datatypescalars
    )
  }

  private def _attributes(
    kind: DeclarationKind,
    owner: String,
    schemaclass: SchemaModel.SchemaClass,
    declarationlines: Map[(DeclarationKind, String), Int],
    attributelines: Map[(DeclarationKind, String, String), Int]
  ): Vector[Attribute] =
    schemaclass.attributes.map { attribute =>
      Attribute(
        kind,
        owner,
        attribute.name,
        attribute.rawTypeName,
        attributelines.getOrElse(
          (kind, owner, attribute.name),
          declarationlines.getOrElse(kind -> owner, 1)
        )
      )
    }

  private def _attribute_lines(model: KaleidoxModel): Map[(DeclarationKind, String, String), Int] =
    model.divisions.toVector.flatMap {
      case division: KaleidoxModel.EntityDivision =>
        _attribute_lines(DeclarationKind.Entity, division.section)
      case division: KaleidoxModel.ValueDivision =>
        _attribute_lines(DeclarationKind.Value, division.section)
      case _ =>
        Vector.empty
    }.toMap

  private def _attribute_lines(
    kind: DeclarationKind,
    root: org.goldenport.parser.LogicalSection
  ): Vector[((DeclarationKind, String, String), Int)] =
    root.blocks.sections.toVector.flatMap { declaration =>
      declaration.blocks.sections.toVector.
        filter(section => _same_key(section, "ATTRIBUTE")).
        flatMap(section => Dox2Parser.parseSection(KaleidoxConfig.default.doxConfig, section).tableList).
        flatMap { table =>
          val columns = table.head.map(_.columns.map(_normalize_key)).getOrElse(Nil)
          val nameindex = columns.indexOf("name") match {
            case -1 => 0
            case index => index
          }
          val sourcerows = declaration.blocks.sections.toVector.
            filter(section => _same_key(section, "ATTRIBUTE")).
            flatMap(_.lines.lines).
            flatMap(line => DoxLinesParser.TableMark.get(line).collect {
              case row: DoxLinesParser.TableMark.TableRow => row
            })
          table.body.records.toVector.flatMap { row =>
            row.fields.lift(nameindex).flatMap { field =>
              val name = field.toText.trim
              val values = row.fields.map(_.toText.trim).toVector
              sourcerows.find(_.fields.map(_.trim).toVector == values).
                flatMap(_.line.location.flatMap(_.line)).
                map(line => (kind, declaration.nameForModel, name) -> line)
            }
          }
        }
    }

  private def _declaration_lines(model: KaleidoxModel): Map[(DeclarationKind, String), Int] =
    model.divisions.toVector.flatMap {
      case division: KaleidoxModel.EntityDivision =>
        _declaration_lines(DeclarationKind.Entity, division.section)
      case division: KaleidoxModel.ValueDivision =>
        _declaration_lines(DeclarationKind.Value, division.section)
      case _ =>
        Vector.empty
    }.toMap

  private def _declaration_lines(
    kind: DeclarationKind,
    root: org.goldenport.parser.LogicalSection
  ): Vector[((DeclarationKind, String), Int)] =
    root.blocks.sections.toVector.flatMap { section =>
      section.location.flatMap(_.line).map(line => (kind -> section.nameForModel) -> line)
    }

  private def _is_raw_string(p: SchemaModel.Attribute): Boolean =
    p.rawTypeName.fold(p.domain.datatype == XString)(_is_string_name)

  private def _is_string_datatype(p: DataTypeClass): Boolean = p match {
    case m: DataTypeClass.Plain =>
      m.datatype == XString
    case m: DataTypeClass.Complex if m.constitutes.size == 1 =>
      m.constitutes.get("value").exists(_is_string_datatype)
    case _ =>
      false
  }

  private def _is_string_name(p: String): Boolean =
    p.trim.toLowerCase(java.util.Locale.ROOT) match {
      case "string" | "java.lang.string" => true
      case _ => false
    }

  private def _same_key(
    section: org.goldenport.parser.LogicalSection,
    key: String
  ): Boolean =
    _normalize_key(section.keyForModel) == _normalize_key(key) ||
      _normalize_key(section.nameForModel) == _normalize_key(key)

  private def _normalize_key(p: String): String =
    p.toLowerCase(java.util.Locale.ROOT).filter(_.isLetterOrDigit)
}
