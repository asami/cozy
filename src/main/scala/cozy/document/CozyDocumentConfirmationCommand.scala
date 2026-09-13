package cozy.document

import java.nio.charset.StandardCharsets
import java.nio.file.{AtomicMoveNotSupportedException, Files, LinkOption, Path, Paths, StandardCopyOption}
import scala.util.control.NonFatal

/*
 * @since   Sep. 12, 2026
 * @version Sep. 12, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyDocumentConfirmationCommand {
  private final case class Request(core: String, document: String, vocabulary: String, summary: Option[String], kind: String, save: String)

  private val _required_options = Set("core", "document", "vocabulary", "kind", "save")
  private val _options = _required_options + "summary"

  def execute(args: List[String]): Boolean = args match {
    case "document-project" :: "confirmation" :: "render" :: rest =>
      val request = _request(rest)
      request.kind match {
        case "document" =>
          if (request.summary.nonEmpty) _fail("CONFIRMATION_CLI", "$.command", "--kind document does not accept --summary")
          val validated = CozyDocumentDescriptionV2.loadDocument(_input_path(request.core, "core"), _input_path(request.document, "document"))
          val vocabulary = CozyDocumentConfirmationVocabulary.loadDocument(_input_path(request.vocabulary, "vocabulary"), validated.description.locale)
          val rendered = CozyDocumentConfirmationProjectionV2.render(validated, vocabulary)
          val destination = _destination(request.save)
          _publish(destination, rendered.html)
          println(s"Cozy Document Confirmation Render\nkind: document\ncore: ${validated.core.core.id}\ncore identity: ${validated.coreIdentity}\ndocument: ${validated.description.id}\ndocument identity: ${validated.documentIdentity}\nidentity: ${rendered.identity}\noutput: $destination")
          true
        case "summary" =>
          val summary = request.summary.getOrElse(_fail("CONFIRMATION_CLI", "$.command", "--kind summary requires exactly --core <core.yaml> --document <document.yaml> --vocabulary <vocabulary.yaml> --summary <summary.yaml> --kind summary --save <output.html>"))
          val validated = CozyDocumentDescriptionV2.loadSummary(_input_path(request.core, "core"), _input_path(request.document, "document"), _input_path(summary, "summary"))
          val vocabulary = CozyDocumentConfirmationVocabulary.loadSummary(_input_path(request.vocabulary, "vocabulary"), validated.description.locale)
          val rendered = CozySummaryConfirmationProjectionV2.render(validated, vocabulary)
          val destination = _destination(request.save)
          _publish(destination, rendered.html)
          println(s"Cozy Summary Confirmation Render\nkind: summary\ncore: ${validated.document.core.core.id}\ncore identity: ${validated.document.coreIdentity}\ndocument: ${validated.document.description.id}\ndocument identity: ${validated.document.documentIdentity}\nsummary: ${validated.description.id}\nsummary identity: ${validated.summaryIdentity}\nidentity: ${rendered.identity}\noutput: $destination")
          true
        case _ => _fail("CONFIRMATION_CLI", "$.kind", "--kind must be document or summary")
      }
    case _ => false
  }

  private def _request(args: List[String]): Request = {
    @annotation.tailrec
    def _parse_(remaining: List[String], values: Map[String, String]): Map[String, String] = remaining match {
      case Nil => values
      case option :: value :: tail if option.startsWith("--") =>
        val name = option.drop(2)
        if (!_options.contains(name)) _fail("CONFIRMATION_CLI", "$.command", s"unknown option: $option")
        if (values.contains(name)) _fail("CONFIRMATION_CLI", "$.command", s"duplicate option: $option")
        if (value.startsWith("--")) _fail("CONFIRMATION_CLI", "$.command", s"option $option requires one value")
        _parse_(tail, values + (name -> value))
      case option :: Nil if option.startsWith("--") => _fail("CONFIRMATION_CLI", "$.command", s"option $option requires one value")
      case value :: _ => _fail("CONFIRMATION_CLI", "$.command", s"unexpected command argument: $value")
    }
    val values = _parse_(args, Map.empty)
    if (!_required_options.subsetOf(values.keySet)) _fail("CONFIRMATION_CLI", "$.command", "requires --core <core.yaml> --document <document.yaml> --vocabulary <vocabulary.yaml> --kind <document|summary> --save <output.html>")
    Request(values("core"), values("document"), values("vocabulary"), values.get("summary"), values("kind"), values("save"))
  }

  private def _input_path(value: String, label: String): Path =
    try Paths.get(value) catch { case NonFatal(_) => _fail("CONFIRMATION_PATH", s"$$.$label", "input path is invalid") }

  private def _destination(value: String): Path = {
    val path = try Paths.get(value).toAbsolutePath.normalize() catch { case NonFatal(_) => _fail("CONFIRMATION_PATH", "$.save", "output path is invalid") }
    val filename = Option(path.getFileName).getOrElse(_fail("CONFIRMATION_PATH", "$.save", "output destination has no filename"))
    if (!filename.toString.endsWith(".html")) _fail("CONFIRMATION_PATH", "$.save", "output destination must end with .html")
    if (Files.isSymbolicLink(path) || (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)))
      _fail("CONFIRMATION_PATH", "$.save", "output destination must be an absent or direct regular non-symlink file")
    val parent = Option(path.getParent).getOrElse(_fail("CONFIRMATION_PATH", "$.save", "output destination has no safe parent"))
    if (Files.isSymbolicLink(parent) || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) _fail("CONFIRMATION_PATH", "$.save", "output parent must be an existing direct non-symlink directory")
    path
  }

  private def _publish(destination: Path, content: String): Unit = {
    var temporary: Option[Path] = None
    try {
      if (Files.isSymbolicLink(destination) || (Files.exists(destination, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS))) _fail("CONFIRMATION_PATH", "$.save", "output destination became unsafe")
      val temporaryfile = Files.createTempFile(destination.getParent, s".${destination.getFileName}-", ".tmp")
      temporary = Some(temporaryfile)
      Files.writeString(temporaryfile, content, StandardCharsets.UTF_8)
      if (Files.isSymbolicLink(destination) || (Files.exists(destination, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS))) _fail("CONFIRMATION_PATH", "$.save", "output destination became unsafe")
      Files.move(temporaryfile, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
      temporary = None
    } catch {
      case fault: CozyDocumentConfirmationVocabulary.VocabularyFault => throw fault
      case fault: CozyDocumentDescriptionV2.DescriptionV2Fault => throw fault
      case fault: CozyDocumentConfirmationProjectionV2.ProjectionFault => throw fault
      case fault: CozySummaryConfirmationProjectionV2.ProjectionFault => throw fault
      case _: AtomicMoveNotSupportedException => _fail("CONFIRMATION_PATH", "$.save", "output requires an atomic move")
      case NonFatal(_) => _fail("CONFIRMATION_PATH", "$.save", "output cannot be published atomically")
    } finally {
      temporary.foreach(path => try Files.deleteIfExists(path) catch { case NonFatal(_) => () })
    }
  }

  private def _fail(code: String, path: String, reason: String): Nothing =
    throw CozyDocumentDescriptionV2.DescriptionV2Fault(code, path, reason)
}
