package cozy.modeler

import scala.util.control.NonFatal
import org.goldenport.RAISE
import org.goldenport.collection.VectorMap
import org.goldenport.kaleidox.{Model => KaleidoxModel}
import org.goldenport.kaleidox.model.ValueModel
import org.smartdox.{Dox, Section}
import org.smartdox.parser.Dox2Parser

/*
 * @since   Oct.  6, 2026
 * @version Oct.  6, 2026
 */
private[modeler] object DartCanonicalInputProjection {
  def enrich(model: KaleidoxModel, builder: Modeler.ModelBuilder): Modeler.ModelBuilder = {
    val declarations = builder.operation.values
    if (declarations.isEmpty) builder else {
      val sections = model.divisions.collect {
        case division: KaleidoxModel.OperationDivision =>
          _input_sections(Dox2Parser.parse(model.config.doxConfig, division.section))
      }.flatten.toVector
      val inventory = declarations.groupBy(_.name).toVector.sortBy(_._1)
      inventory.collectFirst {
        case (name, entries) if entries.size > 1 => name
      }.foreach { name =>
        val locations = sections.filter(_.nameForModel.trim == name).map(_source_location).sorted.mkString(", ")
        RAISE.invalidArgumentFault(s"Legacy input '$name' is declared more than once ($locations).")
      }
      val originals = sections.groupBy(_.nameForModel.trim)
      val missing = inventory.collect {
        case (name, _) if !builder.value.classes.contains(name) =>
          val section = originals.getOrElse(name, Vector.empty) match {
            case Vector(one) => one
            case Vector() => RAISE.invalidArgumentFault(s"Legacy input '$name' has no original canonical DTO Section.")
            case entries =>
              val locations = entries.map(_source_location).sorted.mkString(", ")
              RAISE.invalidArgumentFault(s"Legacy input '$name' has ambiguous original canonical DTO Sections ($locations).")
          }
          name -> _canonical_value(name, section)
      }
      val values = builder.value.copy(classes = VectorMap(builder.value.classes.toVector ++ missing))
      builder.copy(value = values)
    }
  }

  private def _input_sections(dox: Dox): Vector[Section] = dox match {
    case section: Section => section.keyForModel match {
      case "command" | "query" => section.sections.toVector
      case "operation" => Vector.empty
      case _ => section.sections.toVector.flatMap(_input_sections)
    }
    case other => other.elements.toVector.flatMap(_input_sections)
  }

  private def _canonical_value(name: String, section: Section): ValueModel.ValueClass = {
    val canonical = try {
      ValueModel.ValueClass.create(section)
    } catch {
      case NonFatal(error) =>
        RAISE.syntaxErrorFault(s"Legacy input '$name' canonical source factory failed at ${_source_location(section)}: ${error.getMessage}")
    }
    canonical.getOrElse {
      RAISE.invalidArgumentFault(s"Legacy input '$name' has no canonical ValueClass at ${_source_location(section)}.")
    }
  }

  private def _source_location(section: Section): String =
    section.location.map(_.show).filterNot(_ == "[]").getOrElse(s"Section '${section.nameForModel.trim}'")
}
