package cozy.modeler

import java.util.Locale
import org.goldenport.RAISE

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 */
private[modeler] object DartNaming {
  private val _keywords = Set(
    "abstract", "as", "assert", "async", "await", "base", "break", "case",
    "catch", "class", "const", "continue", "covariant", "default", "deferred",
    "do", "dynamic", "else", "enum", "export", "extends", "extension", "external",
    "factory", "false", "final", "finally", "for", "Function", "get", "hide",
    "if", "implements", "import", "in", "interface", "is", "late", "library",
    "mixin", "new", "null", "of", "on", "operator", "part", "required", "rethrow",
    "return", "sealed", "set", "show", "static", "super", "switch", "sync",
    "this", "throw", "true", "try", "type", "typedef", "var", "void", "when",
    "while", "with", "yield"
  )
  val reservedTypes: Set[String] = Set(
    "DartJsonCodec", "DartJsonDecodingException", "String", "Object", "List",
    "Map", "Set", "Future", "Enum", "ArgumentError", "Exception", "FormatException"
  )
  val objectMembers: Set[String] = Set("hashCode", "runtimeType", "toString", "noSuchMethod")
  val codecMembers: Set[String] = Set("fromJson", "toJson")
  val classificationMembers: Set[String] = Set("wireName", "sourceValue", "label", "value")
  val enumMembers: Set[String] = Set("values", "index", "name")

  def typeName(source: String, location: String): String = {
    val normalized = _tokens(source, location).map(_capitalize).mkString
    if (_keywords(normalized)) normalized + "Value" else normalized
  }

  def memberName(source: String, location: String): String = {
    val tokens = _tokens(source, location)
    val normalized = tokens.head + tokens.tail.map(_capitalize).mkString
    if (_keywords(normalized)) normalized + "_" else normalized
  }

  def fileName(source: String, location: String): String = _tokens(source, location).mkString("_")

  def literal(value: String): String = {
    val escaped = value.flatMap {
      case '\'' => "\\'"
      case '\\' => "\\\\"
      case '$' => "\\$"
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case c if c < ' ' || c == '\u007f' || c == '\u2028' || c == '\u2029' => f"\\u${c.toInt}%04x"
      case c => c.toString
    }
    "'" + escaped + "'"
  }

  private def _capitalize(token: String): String = token.take(1).toUpperCase(Locale.ROOT) + token.drop(1)

  private def _tokens(source: String, location: String): Vector[String] = {
    if (source.exists(_ > '\u007f'))
      RAISE.invalidArgumentFault(s"$location: non-ASCII identifier '$source'.")
    val separated = source.replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2").
      replaceAll("([a-z0-9])([A-Z])", "$1 $2")
    val tokens = separated.split("[^A-Za-z0-9]+").filter(_.nonEmpty).
      map(_.toLowerCase(Locale.ROOT)).toVector
    if (tokens.isEmpty || tokens.head.head.isDigit)
      RAISE.invalidArgumentFault(s"$location: empty or leading-numeric identifier '$source'.")
    tokens
  }
}
