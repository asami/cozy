package cozy.modeler

import java.util.Locale
import org.goldenport.RAISE

/*
 * @since   Oct.  6, 2026
 * @version Oct.  6, 2026
 */
sealed trait DartJsonFieldStyle {
  def id: String
  def fieldName(name: String): String
}

object DartJsonFieldStyle {
  case object Source extends DartJsonFieldStyle {
    val id = "source"
    def fieldName(name: String): String = name
  }

  case object CncfSnakeCase extends DartJsonFieldStyle {
    val id = "cncf-snake-case"

    def fieldName(name: String): String = {
      val trimmed = name.trim
      val normalized = if (trimmed.exists(character => character == '-' || character == '_')) {
        trimmed.split("[-_]", -1).filter(_.nonEmpty).zipWithIndex.map { case (segment, index) =>
          val initial = segment.take(1)
          (if (index == 0) initial.toLowerCase(Locale.ROOT) else initial.toUpperCase(Locale.ROOT)) + segment.drop(1)
        }.mkString
      } else trimmed.take(1).toLowerCase(Locale.ROOT) + trimmed.drop(1)
      val segments = Vector.newBuilder[String]
      var start = 0
      normalized.indices.drop(1).foreach { index =>
        val character = normalized.charAt(index)
        if (character >= 'A' && character <= 'Z') {
          segments += normalized.substring(start, index).toLowerCase(Locale.ROOT)
          start = index
        }
      }
      segments += normalized.substring(start).toLowerCase(Locale.ROOT)
      segments.result().mkString("_")
    }
  }

  def parse(name: String): DartJsonFieldStyle = name match {
    case "source" => Source
    case "cncf-snake-case" => CncfSnakeCase
    case _ => RAISE.invalidArgumentFault(s"Invalid --json-field-style '$name'; expected source or cncf-snake-case.")
  }
}
