package cozy.document

import cozy.media.CozyMedia
import java.nio.file.Path
import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.io.InputSource
import scala.util.control.NonFatal

/*
 * @since   Sep. 14, 2026
 * @version Sep. 14, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozySummarySlidePdf {
  final case class Connection(
    projection: CozySummarySlideProjection.Projection,
    pageSetPath: Path
  )

  def prepare(projectRoot: Path, profilePath: Path): Connection = {
    val projection = CozySummarySlideProjection.project(projectRoot, profilePath)
    val descriptor = _descriptor(projection.mediaDescriptorPath)
    val resource = descriptor.resources.filter(_.id == projection.provenance.mediaTarget) match {
      case Vector(value) => value
      case _ => _fault("mediaTarget", "must resolve exactly one selected media resource")
    }
    val root = Option(projection.mediaDescriptorPath.getParent).getOrElse(
      _fault("mediaDescriptorPath", "media descriptor must have a parent directory")
    )
    Connection(projection, root.resolve(_connection_source(resource.source, resource.id)).normalize())
  }

  def write(projectRoot: Path, profilePath: Path): CozySummarySlideProjection.WrittenProjection = {
    val connection = prepare(projectRoot, profilePath)
    CozySummarySlideProjection.write(connection.projection, connection.pageSetPath)
  }

  def build(
    projectRoot: Path,
    profilePath: Path,
    runner: CozyMedia.ProcessRunner = CozyMedia.ProcessRunner.default
  ): String = {
    val connection = prepare(projectRoot, profilePath)
    CozySummarySlideProjection.write(connection.projection, connection.pageSetPath)
    CozyMedia.buildSummarySlidesPdf(
      CozyMedia.CommandConfig(
        connection.projection.mediaDescriptorPath,
        target = Some(connection.projection.provenance.mediaTarget)
      ),
      connection.pageSetPath,
      runner
    )
  }

  private def _descriptor(path: Path): CozyMedia.Descriptor =
    try StructuredDocumentLoader.loadDocument[CozyMedia.Descriptor](InputSource(path.toFile)).take
    catch { case NonFatal(_) => _fault("mediaDescriptorPath", "must be an existing readable media descriptor") }

  private def _connection_source(value: Option[String], target: String): Path = {
    val source = value.getOrElse(_fault("mediaTarget.source", s"summary-slides PDF target $target requires source"))
    if (source.isEmpty || source != source.trim || source.contains('\\') || source.exists(Character.isISOControl) ||
        source.startsWith("/") || source.matches("^[A-Za-z][A-Za-z0-9+.-]*:.*"))
      _fault("mediaTarget.source", "must be a normalized root-level project-relative PageSet source")
    val relative = try Path.of(source) catch {
      case NonFatal(_) => _fault("mediaTarget.source", "must be a valid normalized root-level project-relative PageSet source")
    }
    if (relative.isAbsolute || relative.getNameCount != 1 || relative.normalize().toString != source || source == "." || source == "..")
      _fault("mediaTarget.source", "must be a normalized root-level project-relative PageSet source")
    relative
  }

  private def _fault(path: String, reason: String): Nothing =
    throw CozySummarySlideProjection.ProjectionFault("SUMMARY_SLIDE_PDF_CONNECTION", path, reason)
}
