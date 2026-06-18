package cozy.config

import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.io.InputSource
import org.goldenport.value._
import io.circe.{Json => CJson}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.collection.JavaConverters._

/*
 * @since   May. 20, 2026
 *  version Jun.  8, 2026
 * @version Jun. 18, 2026
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

  private val _project_file_names: Vector[String] =
    Vector("project.yaml", "project.yml", "project.json", "project.conf", "project.hocon", "project.xml")

  private val _config_file_names: Vector[String] =
    Vector("config.yaml", "config.yml", "config.json", "config.conf", "config.hocon", "config.xml")

  def load(path: Path): Config =
    if (Files.isRegularFile(path)) {
      val json = StructuredDocumentLoader.loadJson(InputSource(path.toFile)).take
      _config_from_json(json)
    } else {
      Config.empty
    }

  def loadOperationDefaults(projectdir: Path): Config =
    operationDefaultFiles(projectdir).foldLeft(Config.empty) { (z, file) =>
      z.merge(load(file))
    }

  def loadProjectConfig(projectdir: Path): Config = {
    val project = loadProjectMetadata(projectdir)
    project.merge(loadOperationDefaults(projectdir))
  }

  def loadProjectMetadata(projectdir: Path): Config =
    _first_existing(projectdir, _project_file_names).map(load).getOrElse(Config.empty)

  def operationDefaultFiles(projectdir: Path): Vector[Path] = {
    val dirs = Vector(
      Option(System.getProperty("user.home")).map(h => Path.of(h).resolve(".cozy")),
      Some(projectdir.resolve("conf").resolve("cozy")),
      Some(projectdir.resolve(".cozy"))
    ).flatten
    dirs.flatMap(dir => _config_file_names.map(name => dir.resolve(name))).map(_.toAbsolutePath.normalize).filter(Files.isRegularFile(_))
  }

  def loadPublic(path: Path): Config =
    if (Files.isRegularFile(path)) {
      val json = StructuredDocumentLoader.loadJson(InputSource(path.toFile)).take
      _config_from_json(json)
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

  private def _first_existing(projectdir: Path, names: Vector[String]): Option[Path] =
    names.map(name => projectdir.resolve(name)).find(Files.isRegularFile(_))

  private def _config_from_json(json: CJson): Config =
    Config(_flatten_json(json), _flatten_json_lists(json), Some(json))

  private def _flatten_json(json: CJson): Map[String, String] =
    _flatten_json("", json)

  private def _flatten_json(prefix: String, json: CJson): Map[String, String] =
    json.asObject.map { obj =>
      obj.toMap.flatMap {
        case (k, v) =>
          val key = if (prefix.isEmpty) k else s"${prefix}.${k}"
          _json_scalar_string(v).map(key -> _).toMap ++ _flatten_json(key, v)
      }
    }.orElse {
      json.asArray.map { xs =>
        xs.zipWithIndex.flatMap {
          case (v, i) => _flatten_json(s"${prefix}.${i}", v)
        }.toMap
      }
    }.getOrElse(Map.empty)

  private def _json_scalar_string(json: CJson): Option[String] =
    json.asString.
      orElse(json.asBoolean.map(_.toString)).
      orElse(json.asNumber.map(_.toString))

  private def _flatten_json_lists(json: CJson): Map[String, Vector[String]] =
    _flatten_json_lists("", json)

  private def _flatten_json_lists(prefix: String, json: CJson): Map[String, Vector[String]] =
    json.asObject.map { obj =>
      obj.toMap.flatMap {
        case (k, v) =>
          val key = if (prefix.isEmpty) k else s"${prefix}.${k}"
          val list = v.asArray.map { xs =>
            xs.flatMap(_.asString.map(_.trim).filter(_.nonEmpty)).toVector
          }.filter(_.nonEmpty).map(key -> _).toMap
          list ++ _flatten_json_lists(key, v)
      }
    }.orElse {
      json.asArray.map { xs =>
        xs.zipWithIndex.flatMap {
          case (v, i) => _flatten_json_lists(s"${prefix}.${i}", v)
        }.toMap
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
