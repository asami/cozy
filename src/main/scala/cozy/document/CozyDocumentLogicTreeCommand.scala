package cozy.document

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths}
import scala.util.control.NonFatal

/*
 * @since   Sep. 11, 2026
 *  version Sep. 30, 2026
 * @version Oct.  8, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyDocumentLogicTreeCommand {
  private final case class Request(core: String, format: String, kind: String, save: String)

  private val _options = Set("core", "format", "kind", "save")

  def execute(args: List[String]): Boolean = args match {
    case "document-project" :: "logic-tree" :: "render" :: rest =>
      val request = _request(rest)
      val validated = CozyDocumentLogicTree.load(_input_path(request.core, "core"), _input_path(request.format, "format"))
      val rendered = request.kind match {
        case "overview" => CozyDocumentLogicTreeProjection.overview(validated)
        case "slides" => CozyDocumentLogicTreeProjection.slides(validated)
        case _ => _fail("LOGIC_TREE_CLI", "$.kind", "--kind must be overview or slides")
      }
      val destination = _destination(request.save)
      _publish(destination, rendered.html)
      println(s"Cozy Content Core Logic Tree Render\nkind: ${rendered.kind}\ncore: ${validated.core.id}\nlocale: ${validated.format.locale}\noutput: $destination")
      true
    case _ => false
  }

  private def _request(args: List[String]): Request = {
    @annotation.tailrec
    def _parse_(remaining: List[String], values: Map[String, String]): Map[String, String] = remaining match {
      case Nil => values
      case option :: value :: tail if option.startsWith("--") =>
        val name = option.drop(2)
        if (!_options.contains(name)) _fail("LOGIC_TREE_CLI", "$.command", s"unknown option: $option")
        if (values.contains(name)) _fail("LOGIC_TREE_CLI", "$.command", s"duplicate option: $option")
        if (value.startsWith("--")) _fail("LOGIC_TREE_CLI", "$.command", s"option $option requires one value")
        _parse_(tail, values + (name -> value))
      case option :: Nil if option.startsWith("--") => _fail("LOGIC_TREE_CLI", "$.command", s"option $option requires one value")
      case value :: _ => _fail("LOGIC_TREE_CLI", "$.command", s"unexpected command argument: $value")
    }
    val values = _parse_(args, Map.empty)
    if (values.keySet != _options) _fail("LOGIC_TREE_CLI", "$.command", "requires exactly --core <core.yaml> --format <format.yaml> --kind overview|slides --save <output.html>")
    Request(values("core"), values("format"), values("kind"), values("save"))
  }

  private def _destination(value: String): Path = {
    val path = try Paths.get(value).toAbsolutePath.normalize() catch {
      case NonFatal(_) => _fail("LOGIC_TREE_PATH", "$.save", "output path is invalid")
    }
    if (!path.getFileName.toString.endsWith(".html"))
      _fail("LOGIC_TREE_PATH", "$.save", "output destination must end with .html")
    if (Files.isSymbolicLink(path) || (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)))
      _fail("LOGIC_TREE_PATH", "$.save", "output destination must be an absent or direct regular non-symlink file")
    val parent = Option(path.getParent).getOrElse(_fail("LOGIC_TREE_PATH", "$.save", "output destination has no safe parent"))
    if (Files.isSymbolicLink(parent) || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS))
      _fail("LOGIC_TREE_PATH", "$.save", "output parent must be an existing direct non-symlink directory")
    path
  }

  private def _input_path(value: String, label: String): Path =
    try Paths.get(value) catch {
      case NonFatal(_) => _fail("LOGIC_TREE_PATH", s"$$.$label", "input path is invalid")
    }

  private def _publish(destination: Path, content: String): Unit = {
    try {
      Files.writeString(destination, content, StandardCharsets.UTF_8)
    } catch {
      case NonFatal(_) => _fail("LOGIC_TREE_PATH", "$.save", "output cannot be written")
    }
  }

  private def _fail(code: String, path: String, reason: String): Nothing =
    throw CozyDocumentLogicTree.LogicTreeFault(code, path, reason)
}
