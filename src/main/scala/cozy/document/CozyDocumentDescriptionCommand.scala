package cozy.document

import java.nio.charset.StandardCharsets
import java.nio.file.{AtomicMoveNotSupportedException, Files, LinkOption, Path, Paths, StandardCopyOption}
import scala.util.control.NonFatal

/*
 * @since   Sep. 12, 2026
 * @version Sep. 12, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyDocumentDescriptionCommand {
  private final case class Request(core: String, document: String, summary: Option[String], kind: String, save: String)

  private val _required_options = Set("core", "document", "kind", "save")
  private val _options = _required_options + "summary"

  def execute(args: List[String]): Boolean = args match {
    case "document-project" :: "description" :: "render" :: rest =>
      val request = _request(rest)
      request.kind match {
        case "document" =>
          if (request.summary.nonEmpty) _fail("DESCRIPTION_CLI", "$.command", "--kind document does not accept --summary")
          val validated = CozyDocumentDescription.loadDocument(_input_path(request.core, "core"), _input_path(request.document, "document"))
          val rendered = CozyDocumentDescriptionProjection.render(validated)
          val destination = _destination(request.save)
          _publish(destination, rendered.html)
          println(s"Cozy Document Description Render\nkind: document\ncore: ${validated.core.core.id}\ndocument: ${validated.description.id}\nidentity: ${rendered.identity}\noutput: $destination")
          true
        case "summary" =>
          val summary = request.summary.getOrElse(_fail("DESCRIPTION_CLI", "$.command", "--kind summary requires exactly --core <core.yaml> --document <document.yaml> --summary <summary.yaml> --kind summary --save <output.html>"))
          val validated = CozyDocumentDescription.loadSummary(_input_path(request.core, "core"), _input_path(request.document, "document"), _input_path(summary, "summary"))
          val rendered = CozyDocumentDescriptionProjection.renderSummary(validated)
          val destination = _destination(request.save)
          _publish(destination, rendered.html)
          println(s"Cozy Summary Description Render\nkind: summary\nsummary: ${validated.description.id}\nsummary identity: ${validated.summaryIdentity}\nreview identity: ${rendered.identity}\ncore: ${validated.document.core.core.id}\ncore identity: ${validated.document.coreIdentity}\ndocument: ${validated.document.description.id}\ndocument identity: ${validated.document.documentIdentity}\noutput: $destination")
          true
        case _ => _fail("DESCRIPTION_CLI", "$.kind", "--kind must be document or summary")
      }
    case _ => false
  }

  private def _request(args: List[String]): Request = {
    @annotation.tailrec
    def _parse_(remaining: List[String], values: Map[String, String]): Map[String, String] = remaining match {
      case Nil => values
      case option :: value :: tail if option.startsWith("--") =>
        val name = option.drop(2)
        if (!_options.contains(name)) _fail("DESCRIPTION_CLI", "$.command", s"unknown option: $option")
        if (values.contains(name)) _fail("DESCRIPTION_CLI", "$.command", s"duplicate option: $option")
        if (value.startsWith("--")) _fail("DESCRIPTION_CLI", "$.command", s"option $option requires one value")
        _parse_(tail, values + (name -> value))
      case option :: Nil if option.startsWith("--") => _fail("DESCRIPTION_CLI", "$.command", s"option $option requires one value")
      case value :: _ => _fail("DESCRIPTION_CLI", "$.command", s"unexpected command argument: $value")
    }
    val values = _parse_(args, Map.empty)
    if (!_required_options.subsetOf(values.keySet)) _fail("DESCRIPTION_CLI", "$.command", "requires --core <core.yaml> --document <document.yaml> --kind <document|summary> --save <output.html>")
    Request(values("core"), values("document"), values.get("summary"), values("kind"), values("save"))
  }

  private def _input_path(value: String, label: String): Path =
    try Paths.get(value) catch { case NonFatal(_) => _fail("DESCRIPTION_PATH", s"$$.$label", "input path is invalid") }

  private def _destination(value: String): Path = {
    val path = try Paths.get(value).toAbsolutePath.normalize() catch { case NonFatal(_) => _fail("DESCRIPTION_PATH", "$.save", "output path is invalid") }
    val filename = Option(path.getFileName).getOrElse(_fail("DESCRIPTION_PATH", "$.save", "output destination has no filename"))
    if (!filename.toString.endsWith(".html")) _fail("DESCRIPTION_PATH", "$.save", "output destination must end with .html")
    if (Files.isSymbolicLink(path) || (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)))
      _fail("DESCRIPTION_PATH", "$.save", "output destination must be an absent or direct regular non-symlink file")
    val parent = Option(path.getParent).getOrElse(_fail("DESCRIPTION_PATH", "$.save", "output destination has no safe parent"))
    if (Files.isSymbolicLink(parent) || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) _fail("DESCRIPTION_PATH", "$.save", "output parent must be an existing direct non-symlink directory")
    path
  }

  private def _publish(destination: Path, content: String): Unit = {
    var temporary: Option[Path] = None
    try {
      if (Files.isSymbolicLink(destination) || (Files.exists(destination, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS))) _fail("DESCRIPTION_PATH", "$.save", "output destination became unsafe")
      val temporaryfile = Files.createTempFile(destination.getParent, s".${destination.getFileName}-", ".tmp")
      temporary = Some(temporaryfile)
      Files.writeString(temporaryfile, content, StandardCharsets.UTF_8)
      if (Files.isSymbolicLink(destination) || (Files.exists(destination, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS))) _fail("DESCRIPTION_PATH", "$.save", "output destination became unsafe")
      Files.move(temporaryfile, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
      temporary = None
    } catch {
      case fault: CozyDocumentDescription.DescriptionFault => throw fault
      case _: AtomicMoveNotSupportedException => _fail("DESCRIPTION_PATH", "$.save", "output requires an atomic move")
      case NonFatal(_) => _fail("DESCRIPTION_PATH", "$.save", "output cannot be published atomically")
    } finally {
      temporary.foreach(path => try Files.deleteIfExists(path) catch { case NonFatal(_) => () })
    }
  }

  private def _fail(code: String, path: String, reason: String): Nothing =
    throw CozyDocumentDescription.DescriptionFault(code, path, reason)
}
