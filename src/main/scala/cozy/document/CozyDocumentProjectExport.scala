package cozy.document

import io.circe.Json
import java.nio.charset.StandardCharsets
import java.nio.file.{AtomicMoveNotSupportedException, Files, LinkOption, Path, Paths, StandardCopyOption}
import java.security.MessageDigest
import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.io.InputSource

/*
 * @since   Sep. 11, 2026
 * @version Sep. 11, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyDocumentProjectExport {
  private[cozy] final case class Bundle(target: String, manifestsha256: String, outputsha256: String, sourceauthoritysha256: String, selectionsha256: String, retainedproductionreceiptsha256: String)
  private[cozy] final case class Currentness(sourceauthority: String, selection: String, retainedproductionevidence: String, manifestauthority: String, exportedbytes: String)
  private final case class Manifest(target: String, outputsha256: String)
  private final case class Receipt(manifestsha256: String, outputsha256: String, sourceauthoritysha256: String, selectionsha256: String, retainedproductionreceiptsha256: String)
  private final case class BundleComponents(bundle: Bundle, manifestpath: Path, outputpath: Path)

  private val _manifest_identity = "cozy.document-project-export-manifest.v1"
  private val _receipt_identity = "cozy.document-project-export-receipt.v1"
  private val _article_review_work_product = "article-review-html"
  private val _article_review_role = "article-review"
  private val _article_review_media_type = "text/html"
  private val _article_review_public_path = "work-products/article-review-html/article-review.html"
  private val _hash_pattern = "[0-9a-f]{64}".r
  private val _slug_pattern = "[a-z0-9][a-z0-9._-]*".r

  def export(project: Path, descriptor: CozyDocumentProject.Descriptor, target: String, save: String): String = {
    val attempt = CozyDocumentProjectEvidence.currentAcceptedNativeAttempt(project, descriptor, _article_review_work_product)
    val output = attempt.outputs.headOption.getOrElse(CozyDocumentProject._failure("DP-OP-001", "export requires accepted article-review-html output"))
    if (output.identity != _article_review_work_product || output.mediaType != _article_review_media_type)
      CozyDocumentProject._failure("DP-OP-001", "export accepted output is outside the article review contract")
    val outputbytes = Files.readAllBytes(_direct_output(project, output.path))
    if (_sha256(outputbytes) != output.sha256)
      CozyDocumentProject._failure("DP-OP-001", "export accepted output bytes are stale")
    val sourceauthority = _source_authority(project, descriptor)
    val selection = _selection_authority(descriptor)
    val destination = _admit_destination(save)
    val manifest = _manifest(target, output.sha256)
    val manifestsha = _sha256(manifest.getBytes(StandardCharsets.UTF_8))
    val receipt = _receipt(manifestsha, output.sha256, sourceauthority, selection, _retained_production_receipt_authority(project, attempt))
    _publish(destination, manifest, receipt, outputbytes)
    s"Cozy Document Project Export\ntarget: $target\nbundle: ${destination.getFileName}\nmanifest: manifest.yaml\nreceipt: receipt.yaml\nwork-product: ${_article_review_work_product}"
  }

  /* The consumer verifier deliberately receives no Document Project authority. */
  private[cozy] def verifyBundle(bundle: Path): Bundle = {
    val components = _bundle_components(bundle)
    if (components.bundle.manifestsha256 != _sha256(components.manifestpath)) _invalid("export receipt manifest authority is stale or tampered")
    if (components.bundle.outputsha256 != _sha256(components.outputpath)) _invalid("exported work product bytes are stale or tampered")
    components.bundle
  }

  private[cozy] def currentness(project: Path, descriptor: CozyDocumentProject.Descriptor, bundle: Path): Currentness = {
    val parsed = try Some(_bundle_components(bundle)) catch { case NonFatal(_) => None }
    val source = parsed.map { value => try _same(_source_authority(project, descriptor), value.bundle.sourceauthoritysha256) catch { case NonFatal(_) => "stale" } }.getOrElse("invalid")
    val selection = parsed.map(value => _same(_selection_authority(descriptor), value.bundle.selectionsha256)).getOrElse("invalid")
    val production = parsed.map { value => try { val attempt = CozyDocumentProjectEvidence.currentAcceptedNativeAttempt(project, descriptor, _article_review_work_product); _same(_retained_production_receipt_authority(project, attempt), value.bundle.retainedproductionreceiptsha256) } catch { case NonFatal(_) => "stale" } }.getOrElse("invalid")
    val manifest = parsed.map(value => _same(_sha256(value.manifestpath), value.bundle.manifestsha256)).getOrElse("invalid")
    val output = parsed.map(value => _same(_sha256(value.outputpath), value.bundle.outputsha256)).getOrElse("invalid")
    Currentness(source, selection, production, manifest, output)
  }

  private def _manifest(target: String, outputsha: String): String = Vector(
    s"identity: ${_manifest_identity}", s"target: $target", "workProducts:",
    s"  - identity: ${_article_review_work_product}", s"    role: ${_article_review_role}", s"    mediaType: ${_article_review_media_type}", s"    path: ${_article_review_public_path}", s"    sha256: $outputsha"
  ).mkString("\n") + "\n"

  private def _receipt(manifestsha: String, outputsha: String, sourceauthority: String, selection: String, retainedreceipt: String): String = Vector(
    s"identity: ${_receipt_identity}", "manifest:", s"  identity: ${_manifest_identity}", s"  sha256: $manifestsha", "exportedBytes:", s"  - path: ${_article_review_public_path}", s"    sha256: $outputsha", "authority:",
    s"  sourceAuthoritySha256: $sourceauthority", s"  selectionSha256: $selection", s"  retainedProductionReceiptSha256: $retainedreceipt"
  ).mkString("\n") + "\n"

  private def _source_authority(project: Path, descriptor: CozyDocumentProject.Descriptor): String = {
    val paths = Vector(descriptor.contentCore, "index.dox", "presentation/visual-pages.yaml", "infographic/infographic.svg")
    val identities = paths.map { relative =>
      val path = CozyDocumentProject._direct_file(project, relative, "export source authority")
      val normalized = project.relativize(path).toString.replace('\\', '/')
      s"$normalized:${_sha256(path)}"
    }
    _sha256(identities.sorted.mkString("|").getBytes(StandardCharsets.UTF_8))
  }

  private def _selection_authority(descriptor: CozyDocumentProject.Descriptor): String = _sha256((descriptor.profile +: descriptor.activeOptionalWorkProducts.sorted).mkString("|").getBytes(StandardCharsets.UTF_8))
  private def _retained_production_receipt_authority(project: Path, attempt: CozyDocumentProjectEvidence.Attempt): String = {
    val fields = _object(_load_json(CozyDocumentProject._direct_file(project, attempt.path.path, "retained accepted native attempt"), "retained accepted native attempt"), "retained accepted native attempt")
    val receipt = _object(_field(fields, "receipt", "retained accepted native attempt"), "retained accepted native production receipt")
    _sha256((_string(receipt, "identity", "retained accepted native production receipt") + "\n" + _string(receipt, "value", "retained accepted native production receipt")).getBytes(StandardCharsets.UTF_8))
  }
  private def _direct_output(project: Path, relative: String): Path = CozyDocumentProject._direct_file(project, relative, "export accepted output")

  private def _admit_destination(value: String): Path = {
    val destination = try Paths.get(value).toAbsolutePath.normalize() catch { case NonFatal(_) => CozyDocumentProject._failure("DP-PATH-001", "export bundle destination is invalid") }
    if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(destination)) CozyDocumentProject._failure("DP-PATH-001", "export bundle destination must be a new direct directory")
    _direct_directory_chain(Option(destination.getParent).getOrElse(CozyDocumentProject._failure("DP-PATH-001", "export bundle destination has no parent")), "export bundle parent")
    destination
  }

  private def _publish(destination: Path, manifest: String, receipt: String, output: Array[Byte]): Unit = {
    var staging: Option[Path] = None
    try {
      val temporary = Files.createTempDirectory(destination.getParent, s".${destination.getFileName}-")
      staging = Some(temporary)
      Files.writeString(temporary.resolve("manifest.yaml"), manifest, StandardCharsets.UTF_8)
      Files.writeString(temporary.resolve("receipt.yaml"), receipt, StandardCharsets.UTF_8)
      val workproduct = temporary.resolve(_article_review_public_path)
      Files.createDirectories(workproduct.getParent)
      Files.write(workproduct, output)
      _require_bundle_shape(temporary)
      if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(destination)) CozyDocumentProject._failure("DP-PATH-001", "export bundle destination became unsafe")
      Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE)
      staging = None
    } catch {
      case _: AtomicMoveNotSupportedException => CozyDocumentProject._failure("DP-PATH-001", "export bundle requires an atomic move")
      case NonFatal(error) => CozyDocumentProject._failure("DP-PATH-001", s"export bundle cannot be published atomically: ${Option(error.getMessage).getOrElse("unknown failure")}")
    } finally staging.foreach(_delete_tree)
  }

  private def _manifest_value(path: Path): Manifest = {
    _require_top_order(path, Vector("identity", "target", "workProducts"), "export manifest")
    val fields = _object(_load_json(path, "export manifest"), "export manifest")
    if (fields.keySet != Set("identity", "target", "workProducts") || _string(fields, "identity", "export manifest") != _manifest_identity) _invalid("export manifest identity or fields are invalid")
    val target = _slug(_string(fields, "target", "export manifest"), "export manifest target")
    val products = _field(fields, "workProducts", "export manifest").asArray.getOrElse(_invalid("export manifest workProducts must be an array"))
    if (products.size != 1) _invalid("export manifest must contain exactly one Work Product")
    val product = _object(products.head, "export manifest Work Product")
    if (product.keySet != Set("identity", "role", "mediaType", "path", "sha256") || _string(product, "identity", "export manifest Work Product") != _article_review_work_product || _string(product, "role", "export manifest Work Product") != _article_review_role || _string(product, "mediaType", "export manifest Work Product") != _article_review_media_type || _string(product, "path", "export manifest Work Product") != _article_review_public_path) _invalid("export manifest Work Product mapping is invalid")
    Manifest(target, _hash(_string(product, "sha256", "export manifest Work Product"), "export manifest Work Product SHA-256"))
  }

  private def _receipt_value(path: Path): Receipt = {
    _require_top_order(path, Vector("identity", "manifest", "exportedBytes", "authority"), "export receipt")
    val fields = _object(_load_json(path, "export receipt"), "export receipt")
    if (fields.keySet != Set("identity", "manifest", "exportedBytes", "authority") || _string(fields, "identity", "export receipt") != _receipt_identity) _invalid("export receipt identity or fields are invalid")
    val manifest = _object(_field(fields, "manifest", "export receipt"), "export receipt manifest identity")
    if (manifest.keySet != Set("identity", "sha256") || _string(manifest, "identity", "export receipt manifest identity") != _manifest_identity) _invalid("export receipt manifest identity is invalid")
    val bytes = _field(fields, "exportedBytes", "export receipt").asArray.getOrElse(_invalid("export receipt exportedBytes must be an array"))
    if (bytes.size != 1) _invalid("export receipt must contain exactly one exported byte identity")
    val output = _object(bytes.head, "export receipt exported byte identity")
    if (output.keySet != Set("path", "sha256") || _string(output, "path", "export receipt exported byte identity") != _article_review_public_path) _invalid("export receipt exported byte path is invalid")
    val authority = _object(_field(fields, "authority", "export receipt"), "export receipt authority")
    if (authority.keySet != Set("sourceAuthoritySha256", "selectionSha256", "retainedProductionReceiptSha256")) _invalid("export receipt authority is invalid")
    Receipt(_hash(_string(manifest, "sha256", "export receipt manifest identity"), "export receipt manifest SHA-256"), _hash(_string(output, "sha256", "export receipt exported byte identity"), "export receipt exported byte SHA-256"), _hash(_string(authority, "sourceAuthoritySha256", "export receipt authority"), "export receipt source authority SHA-256"), _hash(_string(authority, "selectionSha256", "export receipt authority"), "export receipt selection SHA-256"), _hash(_string(authority, "retainedProductionReceiptSha256", "export receipt authority"), "export receipt retained production receipt SHA-256"))
  }

  private def _same(current: String, expected: String): String = if (current == expected) "current" else "stale"

  private def _bundle_components(bundle: Path): BundleComponents = {
    val root = _direct_bundle_root(bundle)
    _require_bundle_shape(root)
    val manifestpath = _direct_file(root, "manifest.yaml", "export manifest")
    val receiptpath = _direct_file(root, "receipt.yaml", "export receipt")
    val outputpath = _direct_file(root, _article_review_public_path, "exported work product")
    val manifest = _manifest_value(manifestpath)
    val receipt = _receipt_value(receiptpath)
    if (manifest.outputsha256 != receipt.outputsha256) _invalid("export manifest and receipt output identities disagree")
    BundleComponents(Bundle(manifest.target, receipt.manifestsha256, receipt.outputsha256, receipt.sourceauthoritysha256, receipt.selectionsha256, receipt.retainedproductionreceiptsha256), manifestpath, outputpath)
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
      val files = entries.filter(path => Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).map(path => root.relativize(path).toString.replace('\\', '/')).toSet
      val directories = entries.filter(path => Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)).map(path => root.relativize(path).toString.replace('\\', '/')).toSet
      if (files != Set("manifest.yaml", "receipt.yaml", _article_review_public_path) || directories != Set("", "work-products", "work-products/article-review-html")) _invalid("export bundle has an invalid file shape")
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
    if (Files.isSymbolicLink(value) || !Files.isDirectory(value, LinkOption.NOFOLLOW_LINKS)) _invalid(s"$label must be a direct non-symlink directory")
  }

  private def _load_json(path: Path, label: String): Json = try { Files.readString(path, StandardCharsets.UTF_8); StructuredDocumentLoader.loadJson(InputSource(path.toFile)).take } catch { case NonFatal(_) => _invalid(s"$label is missing, unreadable, or malformed") }
  private def _require_top_order(path: Path, expected: Vector[String], label: String): Unit = { val keys = try Files.readAllLines(path, StandardCharsets.UTF_8).asScala.collect { case line if line.nonEmpty && !line.startsWith(" ") && !line.startsWith("\t") && line.contains(":") => line.takeWhile(_ != ':').trim }.toVector catch { case NonFatal(_) => _invalid(s"$label cannot be read") }; if (keys != expected) _invalid(s"$label top-level keys are invalid") }
  private def _object(value: Json, label: String): Map[String, Json] = value.asObject.map(_.toMap).getOrElse(_invalid(s"$label must be an object"))
  private def _field(fields: Map[String, Json], name: String, label: String): Json = fields.getOrElse(name, _invalid(s"$label is missing $name"))
  private def _string(fields: Map[String, Json], name: String, label: String): String = _field(fields, name, label).asString.getOrElse(_invalid(s"$label $name must be a string"))
  private def _slug(value: String, label: String): String = if (_slug_pattern.pattern.matcher(value).matches()) value else _invalid(s"$label is invalid")
  private def _hash(value: String, label: String): String = if (_hash_pattern.pattern.matcher(value).matches()) value else _invalid(s"$label is invalid")
  private def _sha256(path: Path): String = _sha256(Files.readAllBytes(path))
  private def _sha256(bytes: Array[Byte]): String = MessageDigest.getInstance("SHA-256").digest(bytes).map(value => f"${value & 0xff}%02x").mkString
  private def _invalid(message: String): Nothing = CozyDocumentProject._failure("DP-OP-001", message)
  private def _delete_tree(path: Path): Unit = { val stream = Files.walk(path); try stream.iterator().asScala.toVector.sortBy(_.toString.length).reverse.foreach(item => Files.deleteIfExists(item)) finally stream.close() }
}
