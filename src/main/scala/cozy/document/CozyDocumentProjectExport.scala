package cozy.document

import io.circe.Json
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths}
import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import org.goldenport.util.CirceUtils
import org.yaml.snakeyaml.{LoaderOptions, Yaml}
import org.yaml.snakeyaml.constructor.{DuplicateKeyException, SafeConstructor}

/*
 * @since   Sep. 11, 2026
 *  version Sep. 29, 2026
 * @version Oct.  8, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyDocumentProjectExport {
  private[cozy] final case class Bundle(target: String)
  private final case class Manifest(target: String)

  private val _manifest_identity = "cozy.document-project-export-manifest.v1"
  private val _article_review_work_product = "article-review-html"
  private val _article_review_role = "article-review"
  private val _article_review_media_type = "text/html"
  private val _article_review_public_path = "work-products/article-review-html/article-review.html"
  private val _slug_pattern = "[a-z0-9][a-z0-9._-]*".r

  def export(project: Path, descriptor: CozyDocumentProject.Descriptor, target: String, save: String): String = {
    val attempt = CozyDocumentProjectEvidence.currentAcceptedNativeAttempt(project, descriptor, _article_review_work_product)
    val output = attempt.outputs.headOption.getOrElse(CozyDocumentProject._failure("DP-OP-001", "export requires accepted article-review-html output"))
    if (output.identity != _article_review_work_product || output.mediaType != _article_review_media_type)
      CozyDocumentProject._failure("DP-OP-001", "export accepted output is outside the article review contract")
    val outputbytes = Files.readAllBytes(_direct_output(project, output.path))
    val destination = _admit_destination(save)
    _publish(destination, _manifest(target), outputbytes)
    s"Cozy Document Project Export\ntarget: $target\nbundle: ${destination.getFileName}\nmanifest: manifest.yaml\nwork-product: ${_article_review_work_product}"
  }

  /* The consumer verifier deliberately receives no Document Project authority. */
  private[cozy] def verifyBundle(bundle: Path): Bundle = {
    val root = _direct_bundle_root(bundle)
    _require_bundle_shape(root)
    val manifest = _manifest_value(_direct_file(root, "manifest.yaml", "export manifest"))
    _direct_file(root, _article_review_public_path, "exported work product")
    Bundle(manifest.target)
  }

  private def _manifest(target: String): String = Vector(
    s"identity: ${_manifest_identity}", s"target: $target", "workProducts:",
    s"  - identity: ${_article_review_work_product}", s"    role: ${_article_review_role}", s"    mediaType: ${_article_review_media_type}", s"    path: ${_article_review_public_path}"
  ).mkString("\n") + "\n"

  private def _direct_output(project: Path, relative: String): Path = CozyDocumentProject._direct_file(project, relative, "export accepted output")

  private def _admit_destination(value: String): Path = {
    val destination = try Paths.get(value).toAbsolutePath.normalize() catch { case NonFatal(_) => CozyDocumentProject._failure("DP-PATH-001", "export bundle destination is invalid") }
    if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(destination)) CozyDocumentProject._failure("DP-PATH-001", "export bundle destination must be a new direct directory")
    val parent = Option(destination.getParent).getOrElse(CozyDocumentProject._failure("DP-PATH-001", "export bundle destination has no parent"))
    try _direct_directory_chain(parent, "export bundle parent") catch {
      case NonFatal(_) => CozyDocumentProject._failure("DP-PATH-001", "export bundle parent must be a direct non-symlink directory")
    }
    destination
  }

  private def _publish(destination: Path, manifest: String, output: Array[Byte]): Unit = {
    try {
      Files.createDirectory(destination)
      Files.writeString(destination.resolve("manifest.yaml"), manifest, StandardCharsets.UTF_8)
      val workproduct = destination.resolve(_article_review_public_path)
      Files.createDirectories(workproduct.getParent)
      Files.write(workproduct, output)
    } catch {
      case NonFatal(error) => CozyDocumentProject._failure("DP-PATH-001", s"export bundle cannot be written: ${Option(error.getMessage).getOrElse("unknown failure")}")
    }
  }

  private def _manifest_value(path: Path): Manifest = {
    _require_top_order(path, Vector("identity", "target", "workProducts"), "export manifest")
    val fields = _object(_load_bundle_json(path, "export manifest"), "export manifest")
    if (fields.keySet != Set("identity", "target", "workProducts") || _string(fields, "identity", "export manifest") != _manifest_identity) _invalid("export manifest identity or fields are invalid")
    val target = _slug(_string(fields, "target", "export manifest"), "export manifest target")
    val products = _field(fields, "workProducts", "export manifest").asArray.getOrElse(_invalid("export manifest workProducts must be an array"))
    if (products.size != 1) _invalid("export manifest must contain exactly one Work Product")
    val product = _object(products.head, "export manifest Work Product")
    if (product.keySet != Set("identity", "role", "mediaType", "path") || _string(product, "identity", "export manifest Work Product") != _article_review_work_product || _string(product, "role", "export manifest Work Product") != _article_review_role || _string(product, "mediaType", "export manifest Work Product") != _article_review_media_type || _string(product, "path", "export manifest Work Product") != _article_review_public_path) _invalid("export manifest Work Product mapping is invalid")
    Manifest(target)
  }

  private def _direct_bundle_root(bundle: Path): Path = {
    val root = try bundle.toAbsolutePath.normalize() catch { case NonFatal(_) => _invalid("export bundle path is invalid") }
    _direct_directory_chain(root, "export bundle")
    root
  }

  private def _require_bundle_shape(root: Path): Unit = {
    val stream = try Files.walk(root) catch { case NonFatal(_) => _invalid("export bundle cannot be read") }
    try {
      val entries = stream.iterator().asScala.toVector
      if (entries.exists(path => Files.isSymbolicLink(path))) _invalid("export bundle must not contain symbolic links")
      if (entries.exists(path => !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))) _invalid("export bundle must contain only direct regular files and directories")
      val files = entries.filter(path => Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).map(path => root.relativize(path).toString.replace('\\', '/')).toSet
      val directories = entries.filter(path => Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)).map(path => root.relativize(path).toString.replace('\\', '/')).toSet
      if (files != Set("manifest.yaml", _article_review_public_path) || directories != Set("", "work-products", "work-products/article-review-html")) _invalid("export bundle has an invalid file shape")
    } finally stream.close()
  }

  private def _direct_file(root: Path, relative: String, label: String): Path = {
    val path = root.resolve(relative).normalize()
    if (!path.startsWith(root)) _invalid(s"$label escapes export bundle")
    val parts = root.relativize(path)
    var parent = root
    (0 until parts.getNameCount - 1).foreach { index => parent = parent.resolve(parts.getName(index)); if (Files.isSymbolicLink(parent) || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) _invalid(s"$label parent is unsafe") }
    if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) _invalid(s"$label is missing or unsafe")
    path
  }

  private def _direct_directory_chain(path: Path, label: String): Unit = {
    val value = path.toAbsolutePath.normalize()
    var component = Option(value.getRoot).getOrElse(_invalid(s"$label must be a direct non-symlink directory"))
    if (Files.isSymbolicLink(component) || !Files.isDirectory(component, LinkOption.NOFOLLOW_LINKS)) _invalid(s"$label must be a direct non-symlink directory")
    value.iterator().asScala.foreach { part =>
      component = component.resolve(part)
      if (Files.isSymbolicLink(component) || !Files.isDirectory(component, LinkOption.NOFOLLOW_LINKS)) _invalid(s"$label must be a direct non-symlink directory")
    }
  }

  private def _load_bundle_json(path: Path, label: String): Json = try {
    val options = new LoaderOptions
    options.setAllowDuplicateKeys(false)
    val yaml = new Yaml(new SafeConstructor(options))
    val raw = yaml.load[java.lang.Object](Files.readString(path, StandardCharsets.UTF_8))
    CirceUtils.convertToJson(raw)
  } catch {
    case _: DuplicateKeyException => _invalid(s"$label contains duplicate mapping keys")
    case NonFatal(_) => _invalid(s"$label is missing, unreadable, or malformed")
  }
  private def _require_top_order(path: Path, expected: Vector[String], label: String): Unit = { val keys = try Files.readAllLines(path, StandardCharsets.UTF_8).asScala.collect { case line if line.nonEmpty && !line.startsWith(" ") && !line.startsWith("\t") && line.contains(":") => line.takeWhile(_ != ':').trim }.toVector catch { case NonFatal(_) => _invalid(s"$label cannot be read") }; if (keys != expected) _invalid(s"$label top-level keys are invalid") }
  private def _object(value: Json, label: String): Map[String, Json] = value.asObject.map(_.toMap).getOrElse(_invalid(s"$label must be an object"))
  private def _field(fields: Map[String, Json], name: String, label: String): Json = fields.getOrElse(name, _invalid(s"$label is missing $name"))
  private def _string(fields: Map[String, Json], name: String, label: String): String = _field(fields, name, label).asString.getOrElse(_invalid(s"$label $name must be a string"))
  private def _slug(value: String, label: String): String = if (_slug_pattern.pattern.matcher(value).matches()) value else _invalid(s"$label is invalid")
  private def _invalid(message: String): Nothing = CozyDocumentProject._failure("DP-OP-001", message)
}
