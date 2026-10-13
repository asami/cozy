package cozy.media

import cozy.media.CozyVisualPage._
import com.fasterxml.jackson.core.{JsonFactory, JsonToken}
import scala.collection.mutable
import scala.util.control.NonFatal

/*
 * @since   Oct. 1, 2026
 * @version Oct.  1, 2026
 * @author  ASAMI, Tomoharu
 */
private[media] object CozyVisualPageSyntax {
  private final case class YamlLine(indent: Int, value: String, line: Int)

  private val _token_pattern = "[A-Za-z0-9][A-Za-z0-9._-]*".r

  def parseJson(text: String, source: String): Value = {
    val parser = new JsonFactory().createParser(text)
    try {
      val token = parser.nextToken()
      if (token == null) _fail("VISUAL_PAGE_JSON", source, "empty JSON input")
      val value = _json_value(parser, source)
      if (parser.nextToken() != null) _fail("VISUAL_PAGE_JSON", source, "trailing JSON tokens are not admitted")
      value
    } catch {
      case fault: VisualPageFault => throw fault
      case NonFatal(e) => _fail("VISUAL_PAGE_JSON", source, Option(e.getMessage).getOrElse("invalid JSON"))
    } finally parser.close()
  }

  private def _json_value(parser: com.fasterxml.jackson.core.JsonParser, source: String): Value = parser.getCurrentToken match {
    case JsonToken.START_OBJECT =>
      var fields = Vector.empty[(String, Value)]
      var seen = Set.empty[String]
      while (parser.nextToken() != JsonToken.END_OBJECT) {
        if (parser.getCurrentToken != JsonToken.FIELD_NAME) _fail("VISUAL_PAGE_JSON", source, "object member name is required")
        val name = parser.getCurrentName
        if (seen.contains(name)) _fail("VISUAL_PAGE_DUPLICATE_FIELD", source, s"duplicate JSON field: $name")
        seen += name
        if (parser.nextToken() == null) _fail("VISUAL_PAGE_JSON", source, s"missing JSON value for field: $name")
        fields :+= name -> _json_value(parser, source)
      }
      VObject(fields)
    case JsonToken.START_ARRAY =>
      var values = Vector.empty[Value]
      while (parser.nextToken() != JsonToken.END_ARRAY) values :+= _json_value(parser, source)
      VArray(values)
    case JsonToken.VALUE_STRING => VString(parser.getText)
    case JsonToken.VALUE_NUMBER_INT =>
      try VNumber(parser.getLongValue) catch { case NonFatal(_) => _fail("VISUAL_PAGE_JSON", source, "integer is outside supported range") }
    case JsonToken.VALUE_TRUE => VBoolean(true)
    case JsonToken.VALUE_FALSE => VBoolean(false)
    case JsonToken.VALUE_NULL => VNull
    case JsonToken.VALUE_NUMBER_FLOAT => _fail("VISUAL_PAGE_JSON", source, "floating-point values are not admitted")
    case _ => _fail("VISUAL_PAGE_JSON", source, s"unsupported JSON token: ${parser.getCurrentToken}")
  }

  def parseYaml(text: String, source: String): Value = {
    val lines = _yaml_lines(text, source)
    if (lines.isEmpty) _fail("VISUAL_PAGE_YAML", source, "empty YAML input")
    var index = 0
    def _parse_block_(indent: Int): Value = {
      if (index >= lines.size || lines(index).indent != indent) _fail("VISUAL_PAGE_YAML", source, s"expected indentation $indent")
      if (lines(index).value.startsWith("-")) _parse_array_(indent) else _parse_object_(indent)
    }
    def _parse_object_(indent: Int): VObject = {
      var fields = Vector.empty[(String, Value)]
      var seen = Set.empty[String]
      while (index < lines.size && lines(index).indent == indent && !lines(index).value.startsWith("-")) {
        val line = lines(index)
        val pair = _yaml_pair(line.value, source, line.line)
        if (seen.contains(pair._1)) _fail("VISUAL_PAGE_DUPLICATE_FIELD", s"$source:${line.line}", s"duplicate YAML field: ${pair._1}")
        seen += pair._1
        index += 1
        val value =
          if (pair._2.nonEmpty) _yaml_scalar(pair._2, s"$source:${line.line}")
          else if (index < lines.size && lines(index).indent > indent) {
            if (lines(index).indent != indent + 2) _fail("VISUAL_PAGE_YAML", s"$source:${lines(index).line}", "nested YAML indentation must advance by two spaces")
            _parse_block_(indent + 2)
          } else VNull
        fields :+= pair._1 -> value
      }
      VObject(fields)
    }
    def _parse_array_(indent: Int): VArray = {
      var values = Vector.empty[Value]
      while (index < lines.size && lines(index).indent == indent && lines(index).value.startsWith("-")) {
        val line = lines(index)
        val rest = line.value.drop(1).trim
        index += 1
        val value =
          if (rest.isEmpty) {
            if (index >= lines.size || lines(index).indent != indent + 2) _fail("VISUAL_PAGE_YAML", s"$source:${line.line}", "array item requires an indented value")
            _parse_block_(indent + 2)
          } else if (_has_yaml_pair(rest)) {
            val pair = _yaml_pair(rest, source, line.line)
            val first = if (pair._2.nonEmpty) _yaml_scalar(pair._2, s"$source:${line.line}") else VNull
            val following =
              if (index < lines.size && lines(index).indent > indent) {
                if (lines(index).indent != indent + 2) _fail("VISUAL_PAGE_YAML", s"$source:${lines(index).line}", "array object indentation must advance by two spaces")
                _parse_object_(indent + 2)
              } else VObject(Vector.empty)
            if (following.fields.exists(_._1 == pair._1)) _fail("VISUAL_PAGE_DUPLICATE_FIELD", s"$source:${line.line}", s"duplicate YAML field: ${pair._1}")
            VObject((pair._1 -> first) +: following.fields)
          } else {
            if (index < lines.size && lines(index).indent > indent) _fail("VISUAL_PAGE_YAML", s"$source:${line.line}", "scalar array items cannot have nested values")
            _yaml_scalar(rest, s"$source:${line.line}")
          }
        values :+= value
      }
      VArray(values)
    }
    val value = _parse_block_(lines.head.indent)
    if (index != lines.size) _fail("VISUAL_PAGE_YAML", s"$source:${lines(index).line}", "unexpected YAML indentation or sequence form")
    value
  }

  private def _yaml_lines(text: String, source: String): Vector[YamlLine] =
    _normalize_newlines(text).split("\n", -1).toVector.zipWithIndex.flatMap { case (raw, index) =>
      val trimmed = raw.trim
      if (trimmed.isEmpty) None
      else if (trimmed.startsWith("#")) {
        val comment = trimmed.drop(1).trim
        if (_has_yaml_pair(comment))
          _fail("VISUAL_PAGE_YAML_COMMENT", s"$source:${index + 1}", "comments must not carry semantic YAML mapping fields")
        None
      }
      else {
        if (raw.contains("\t")) _fail("VISUAL_PAGE_YAML", s"$source:${index + 1}", "tabs are not admitted")
        if (_has_yaml_anchor_or_alias(trimmed) || trimmed.startsWith("<<:")) _fail("VISUAL_PAGE_YAML_ALIAS", s"$source:${index + 1}", "anchors, aliases, and merge keys are not admitted")
        val indent = raw.takeWhile(_ == ' ').length
        if (indent % 2 != 0) _fail("VISUAL_PAGE_YAML", s"$source:${index + 1}", "indentation must use two-space steps")
        Some(YamlLine(indent, raw.drop(indent), index + 1))
      }
    }

  private def _has_yaml_anchor_or_alias(value: String): Boolean = {
    var index = 0
    var quoted = false
    var escaped = false
    while (index < value.length) {
      val char = value.charAt(index)
      if (quoted) {
        if (escaped) escaped = false
        else if (char == '\\') escaped = true
        else if (char == '"') quoted = false
      } else if (char == '"' && {
        var prior = index - 1
        while (prior >= 0 && value.charAt(prior) == ' ') prior -= 1
        prior < 0 ||
          value.charAt(prior) == ':' ||
          (prior == 0 && value.charAt(prior) == '-')
      }) quoted = true
      else if (char == '&' || char == '*') return true
      index += 1
    }
    false
  }

  private def _yaml_pair(value: String, source: String, line: Int): (String, String) = {
    val index = value.indexOf(':')
    if (index <= 0) _fail("VISUAL_PAGE_YAML", s"$source:$line", "mapping entry requires key: value")
    val key = value.take(index).trim
    if (!_token_pattern.pattern.matcher(key).matches) _fail("VISUAL_PAGE_YAML", s"$source:$line", s"invalid YAML key: $key")
    key -> value.drop(index + 1).trim
  }

  private def _has_yaml_pair(value: String): Boolean = {
    val index = value.indexOf(':')
    index > 0 && _token_pattern.pattern.matcher(value.take(index).trim).matches
  }

  private def _yaml_scalar(value: String, source: String): Value = value match {
    case "true" => VBoolean(true)
    case "false" => VBoolean(false)
    case "null" | "~" => VNull
    case item if item.startsWith("\"") || item.startsWith("[") || item.startsWith("{") => parseJson(item, source)
    case item if item.matches("-?[0-9]+") =>
      try VNumber(item.toLong) catch { case NonFatal(_) => _fail("VISUAL_PAGE_YAML", source, "integer is outside supported range") }
    case item if item.nonEmpty && !item.contains(":") && !item.contains("#") => VString(item)
    case _ => _fail("VISUAL_PAGE_YAML", source, "unsupported or lossy YAML scalar")
  }

  def materialMarkdown(text: String): String = {
    def _assets_(value: Value, path: String): Value = VArray(_array(value, path).map { item =>
      VObject(_object(item, path).filter { case (key, _) => Set("id", "path", "mediaType").contains(key) })
    })
    _normalize_newlines(text).split("\n", -1).toVector.map { line =>
      val prefix = if (line.startsWith("assets: ")) Some("assets: ") else if (line.startsWith("pages: ")) Some("pages: ") else None
      prefix.map { value =>
        val payload = line.drop(value.length)
        val parsed = parseJson(payload, "$markdown")
        if (payload != _canonical(parsed))
          _fail("VISUAL_PAGE_MARKDOWN_LOSSY", "$", "material inline JSON must use compact canonical AST rendering")
        val projected = if (value == "assets: ") _assets_(parsed, "$.assets") else VArray(_array(parsed, "$.pages").map { page =>
          VObject(_object(page, "$.pages").map {
            case ("assets", assets) => "assets" -> _assets_(assets, "$.pages.assets")
            case field => field
          })
        })
        value + _canonical(projected)
      }.getOrElse(line)
    }.mkString("\n")
  }

  def parseMarkdown(text: String, source: String): Value = {
    val lines = _normalize_newlines(text).split("\n", -1).toVector match {
      case values if values.nonEmpty && values.last.isEmpty => values.dropRight(1)
      case values => values
    }
    if (lines.headOption.contains("# Cozy Visual Page")) _parse_page_markdown(lines, source)
    else if (lines.headOption.contains("# Cozy Visual Page Set")) _parse_set_markdown(lines, source)
    else _fail("VISUAL_PAGE_MARKDOWN", source, "restricted Markdown requires a Cozy Visual Page or Cozy Visual Page Set heading")
  }

  private def _parse_page_markdown(lines: Vector[String], source: String): Value = {
    val expected = Vector(
      "# Cozy Visual Page", "schema", "version", "id", "knowledge", "language", "## Catalog", "id", "revision",
      "## Logical", "pattern", "nodes", "relations", "## Visual", "pattern", "parameters", "## Assets", "assets", "## Sources", "sources"
    )
    if (lines.size != expected.size) _fail("VISUAL_PAGE_MARKDOWN", source, "restricted Markdown has an unexpected line count")
    val values = mutable.Map.empty[String, Vector[Value]].withDefaultValue(Vector.empty)
    expected.zipWithIndex.foreach { case (expectedline, index) =>
      val actual = lines(index)
      if (expectedline.startsWith("#")) {
        if (actual != expectedline) _fail("VISUAL_PAGE_MARKDOWN", s"$source:${index + 1}", s"expected heading: $expectedline")
      } else {
        val prefix = expectedline + ": "
        if (!actual.startsWith(prefix)) _fail("VISUAL_PAGE_MARKDOWN", s"$source:${index + 1}", s"expected canonical field: $expectedline")
        values.update(expectedline, values(expectedline) :+ parseJson(actual.drop(prefix.length), s"$source:${index + 1}"))
      }
    }
    def _value_(name: String, occurrence: Int): Value = values(name)(occurrence)
    VObject(Vector(
      "schema" -> _value_("schema", 0), "version" -> _value_("version", 0), "id" -> _value_("id", 0),
      "knowledge" -> _value_("knowledge", 0), "language" -> _value_("language", 0),
      "catalog" -> VObject(Vector("id" -> _value_("id", 1), "revision" -> _value_("revision", 0))),
      "logical" -> VObject(Vector("pattern" -> _value_("pattern", 0), "nodes" -> _value_("nodes", 0), "relations" -> _value_("relations", 0))),
      "visual" -> VObject(Vector("pattern" -> _value_("pattern", 1), "parameters" -> _value_("parameters", 0))),
      "assets" -> _value_("assets", 0), "sources" -> _value_("sources", 0)
    ))
  }

  private def _parse_set_markdown(lines: Vector[String], source: String): Value = {
    val expected = Vector("# Cozy Visual Page Set", "schema", "version", "id", "## Pages", "pages")
    if (lines.size != expected.size) _fail("VISUAL_PAGE_MARKDOWN", source, "restricted Markdown set has an unexpected line count")
    val values = expected.zipWithIndex.collect {
      case (name, index) if !name.startsWith("#") =>
        val prefix = name + ": "
        if (!lines(index).startsWith(prefix)) _fail("VISUAL_PAGE_MARKDOWN", s"$source:${index + 1}", s"expected canonical field: $name")
        name -> parseJson(lines(index).drop(prefix.length), s"$source:${index + 1}")
    }.toMap
    if (lines(0) != expected(0) || lines(4) != expected(4)) _fail("VISUAL_PAGE_MARKDOWN", source, "restricted Markdown set headings are out of order")
    VObject(Vector("schema" -> values("schema"), "version" -> values("version"), "id" -> values("id"), "pages" -> values("pages")))
  }

}
