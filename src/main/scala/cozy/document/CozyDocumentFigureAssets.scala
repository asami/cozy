package cozy.document

import java.io.{ByteArrayInputStream, IOException}
import java.nio.file.{Files, LinkOption, Path}
import java.util.{Base64, Locale}
import javax.imageio.{IIOException, ImageIO}

/*
 * @since   Oct. 25, 2026
 * @version Oct. 25, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyDocumentFigureAssets {
  final class Materialized private[CozyDocumentFigureAssets] (
    private val _values: Map[String, String],
    val sourcePaths: Vector[Path]
  ) {
    def dataUri(reference: String): String =
      _values.getOrElse(reference, _fail("FIGURE_ASSET_MISSING", reference, "no materialized image for the declared reference"))
  }

  final case class FigureAssetFault(code: String, path: String, reason: String)
    extends IllegalArgumentException(s"$code path=$path reason=$reason")

  val empty: Materialized = new Materialized(Map.empty, Vector.empty)

  def references(document: CozyDocumentDescriptionV2.ValidatedDocument): Vector[String] =
    _references(document.description.document.sections).distinct

  def materialize(projectRoot: Path, document: CozyDocumentDescriptionV2.ValidatedDocument): Materialized =
    materialize(projectRoot, references(document))

  def materialize(projectRoot: Path, references: Vector[String]): Materialized = {
    val root = _root(projectRoot)
    val values = references.distinct.map { reference =>
      val path = _path(root, reference)
      (reference, _image(path, reference), path)
    }
    new Materialized(values.map { case (reference, uri, _) => reference -> uri }.toMap, values.map(_._3))
  }

  private def _references(sections: Vector[CozyDocumentDescriptionV2.Section]): Vector[String] =
    sections.flatMap(section => section.blocks.collect { case figure: CozyDocumentDescriptionV2.Figure => figure.asset } ++ _references(section.sections))

  private def _root(value: Path): Path = {
    if (!value.isAbsolute || value != value.normalize || !Files.isDirectory(value, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(value))
      _fail("FIGURE_ASSET_ROOT", value.toString, "must be an existing normalized absolute direct project directory")
    try {
      if (value.toRealPath() != value)
        _fail("FIGURE_ASSET_ROOT", value.toString, "must be canonical without symbolic-link aliases")
      value
    } catch {
      case fault: FigureAssetFault => throw fault
      case fault: IOException => _fail("FIGURE_ASSET_ROOT", value.toString, fault.getMessage)
    }
  }

  private def _path(root: Path, reference: String): Path = {
    if (reference.isEmpty || reference != reference.trim || reference.startsWith("/") ||
      reference.exists(character => character == '\\' || character == ':' || character == '?' || character == '#' || Character.isISOControl(character)) ||
      reference.split("/", -1).exists(component => component.isEmpty || component == "." || component == ".."))
      _fail("FIGURE_ASSET_PATH", reference, "must be a trimmed contained relative path without URI or traversal syntax")
    val path = root.resolve(reference)
    if (!path.startsWith(root)) _fail("FIGURE_ASSET_PATH", reference, "must remain inside the project directory")
    val components = reference.split("/").toVector
    components.indices.foreach { index =>
      val selected = root.resolve(components.take(index + 1).mkString("/"))
      if (Files.isSymbolicLink(selected)) _fail("FIGURE_ASSET_PATH", reference, "symbolic links are not image inputs")
      val direct = if (index == components.size - 1) Files.isRegularFile(selected, LinkOption.NOFOLLOW_LINKS)
        else Files.isDirectory(selected, LinkOption.NOFOLLOW_LINKS)
      if (!direct) _fail("FIGURE_ASSET_FILE", reference, "must select a direct regular file through direct directories")
    }
    path
  }

  private def _image(path: Path, reference: String): String = {
    val extension = path.getFileName.toString.toLowerCase(Locale.ROOT)
    val mime = if (extension.endsWith(".png")) "image/png"
      else if (extension.endsWith(".jpg") || extension.endsWith(".jpeg")) "image/jpeg"
      else _fail("FIGURE_ASSET_FORMAT", reference, "only PNG and JPEG images are admitted")
    try {
      val bytes = Files.readAllBytes(path)
      val signature = if (mime == "image/png") bytes.take(8).sameElements(Array[Byte](-119, 80, 78, 71, 13, 10, 26, 10))
        else bytes.length >= 3 && bytes(0) == -1 && bytes(1) == -40 && bytes(2) == -1
      if (!signature) _fail("FIGURE_ASSET_FORMAT", reference, "image signature must match the declared extension")
      val stream = new ByteArrayInputStream(bytes)
      val image = try ImageIO.read(stream) finally stream.close()
      if (image == null || image.getWidth <= 0 || image.getHeight <= 0)
        _fail("FIGURE_ASSET_FORMAT", reference, "must decode as a complete positive-dimension image")
      s"data:$mime;base64,${Base64.getEncoder.encodeToString(bytes)}"
    } catch {
      case fault: FigureAssetFault => throw fault
      case fault: IIOException => _fail("FIGURE_ASSET_FORMAT", reference, fault.getMessage)
      case fault: IOException => _fail("FIGURE_ASSET_IO", reference, fault.getMessage)
    }
  }

  private def _fail(code: String, path: String, reason: String): Nothing = throw FigureAssetFault(code, path, reason)
}
