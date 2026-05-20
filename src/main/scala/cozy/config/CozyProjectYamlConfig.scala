package cozy.config

import org.goldenport.config.ConfigLoader
import org.goldenport.io.InputSource
import org.goldenport.value._
import io.circe.{Json => CJson}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.collection.JavaConverters._

/*
 * @since   May. 20, 2026
 * @version May. 20, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyProjectYamlConfig {
  final case class Config(
    values: Map[String, String],
    lists: Map[String, Vector[String]],
    json: Option[CJson] = None
  ) {
    def value(path: String): Option[String] = values.get(path).map(_.trim).filter(_.nonEmpty)
    def list(path: String): Vector[String] = lists.getOrElse(path, Vector.empty).map(_.trim).filter(_.nonEmpty)
    def mapUnder(path: String): Map[String, String] = {
      val prefix = path + "."
      values.collect {
        case (key, value) if key.startsWith(prefix) && value.trim.nonEmpty =>
          key.substring(prefix.length) -> value.trim
      }
    }
    def boolean(path: String): Option[Boolean] =
      value(path).map(_.toLowerCase(java.util.Locale.ROOT)).collect {
        case "true" | "yes" | "on" => true
        case "false" | "no" | "off" => false
      }
    def descriptiveAttributes: DescriptiveAttributes =
      json.map { root =>
        val top = DescriptiveAttributes.fromJson(root)
        val project = root.hcursor.downField("project").focus.map(DescriptiveAttributes.fromJson).getOrElse(DescriptiveAttributes.empty)
        val publication = root.hcursor.downField("publication").focus.map(DescriptiveAttributes.fromJson).getOrElse(DescriptiveAttributes.empty)
        top.orElse(project).orElse(publication)
      }.getOrElse(DescriptiveAttributes.empty)
    def publicationPageJsons: Vector[CJson] =
      json.flatMap(_.hcursor.downField("publication").downField("pages").focus).
        flatMap(_.asArray).
        getOrElse(Vector.empty)
    def merge(overrideconfig: Config): Config =
      Config(
        values ++ overrideconfig.values,
        lists ++ overrideconfig.lists,
        overrideconfig.json.orElse(json)
      )
  }
  object Config {
    val empty: Config = Config(Map.empty, Map.empty)
  }

  def load(path: Path): Config =
    if (Files.isRegularFile(path))
      parse(Files.readAllLines(path, StandardCharsets.UTF_8).asScala.toVector)
    else
      Config.empty

  def loadPublic(path: Path): Config =
    if (Files.isRegularFile(path)) {
      val json = ConfigLoader.loadConfig[CJson](InputSource(path.toFile)).take
      Config(_flatten_json(json), Map.empty, Some(json))
    } else {
      Config.empty
    }

  def parse(lines: Vector[String]): Config = {
    var stack = Vector.empty[(Int, String)]
    var values = Map.empty[String, String]
    var lists = Map.empty[String, Vector[String]]

    def _current_path_ : String = stack.map(_._2).mkString(".")
    def _append_list_(value: String): Unit = {
      val key = _current_path_
      if (key.nonEmpty)
        lists = lists.updated(key, lists.getOrElse(key, Vector.empty) :+ _unquote(value))
    }

    lines.foreach { raw =>
      val withoutcomment = _strip_comment(raw)
      if (withoutcomment.trim.nonEmpty) {
        val indent = withoutcomment.takeWhile(_ == ' ').length
        val trimmed = withoutcomment.trim
        if (trimmed.startsWith("- ")) {
          stack = stack.dropRight(stack.reverse.takeWhile(_._1 >= indent).length)
          _append_list_(trimmed.substring(2).trim)
        } else {
          val n = trimmed.indexOf(':')
          if (n >= 0) {
            val key = trimmed.substring(0, n).trim
            val rest = trimmed.substring(n + 1).trim
            stack = stack.dropRight(stack.reverse.takeWhile(_._1 >= indent).length)
            if (rest.isEmpty) {
              stack = stack :+ (indent -> key)
            } else {
              val path = (stack.map(_._2) :+ key).mkString(".")
              values = values.updated(path, _unquote(rest))
            }
          }
        }
      }
    }
    Config(values, lists)
  }

  private def _flatten_json(json: CJson): Map[String, String] =
    _flatten_json("", json)

  private def _flatten_json(prefix: String, json: CJson): Map[String, String] =
    json.asObject.map { obj =>
      obj.toMap.flatMap {
        case (k, v) =>
          val key = if (prefix.isEmpty) k else s"${prefix}.${k}"
          v.asString.map(key -> _).toMap ++ _flatten_json(key, v)
      }
    }.getOrElse(Map.empty)

  private def _strip_comment(s: String): String = {
    val trimmed = s.trim
    if (trimmed.startsWith("#"))
      ""
    else
      s
  }

  private def _unquote(s: String): String = {
    val t = s.trim
    if (t.length >= 2 && ((t.head == '"' && t.last == '"') || (t.head == '\'' && t.last == '\'')))
      t.substring(1, t.length - 1)
    else
      t
  }
}
