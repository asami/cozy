package cozy.publication

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.{AtomicMoveNotSupportedException, FileAlreadyExistsException, Files, LinkOption, Path, StandardCopyOption}
import java.security.MessageDigest
import scala.collection.JavaConverters._
import org.goldenport.RAISE
import org.smartdox.metadata.PublishMetadata
import play.api.libs.json.{JsArray, JsObject, JsString, JsValue, Json}

/*
 * @since   Aug.  5, 2026
 * @version Aug. 30, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyArticleMediaBuildContext {
  sealed abstract class Strategy(val name: String, val policy: CozyArticleMediaPolicy.Strategy)

  object Strategy {
    case object Draft extends Strategy("draft", CozyArticleMediaPolicy.Strategy.Preview)
    case object WorkInProgress extends Strategy("work-in-progress", CozyArticleMediaPolicy.Strategy.Preview)
    case object ProductionPreview extends Strategy("production-preview", CozyArticleMediaPolicy.Strategy.Preview)
    case object Production extends Strategy("production", CozyArticleMediaPolicy.Strategy.Production)
  }

  final case class Context(
    strategy: Strategy,
    contextDigest: String,
    publicationPath: Path,
    repositoryPath: Path,
    omittedKeys: Vector[CozyArticleMediaAssociation.Key],
    diagnostics: Vector[CozyArticleMediaPolicy.Diagnostic]
  )

  private final case class BundleFile(filename: String, source: Path, bytes: Array[Byte])

  private var _active_leases = Map.empty[Path, Int]
  private val _no_source_revalidation_hook: () => Unit = () => ()

  def withContext[A](
    projectRoot: Path,
    publicationRoot: Path,
    repositoryRoot: Path,
    strategy: String
  )(consumer: Context => A): A = {
    _with_context(projectRoot, publicationRoot, repositoryRoot, strategy, _no_source_revalidation_hook)(consumer)
  }

  /* This package-private seam makes the source-digest boundary observable
   * without widening the build contract.  It runs while the registry lock is
   * still held, after effective bytes exist and immediately before reloading
   * the source snapshot. */
  private[publication] def withContextBeforeSourceRevalidation[A](
    projectRoot: Path,
    publicationRoot: Path,
    repositoryRoot: Path,
    strategy: String
  )(beforeSourceRevalidation: () => Unit)(consumer: Context => A): A = {
    _with_context(projectRoot, publicationRoot, repositoryRoot, strategy, beforeSourceRevalidation)(consumer)
  }

  private def _with_context[A](
    projectrootarg: Path,
    publicationrootarg: Path,
    repositoryrootarg: Path,
    strategy: String,
    beforesourcerevalidation: () => Unit
  )(consumer: Context => A): A = {
    val selected = normalizeStrategy(strategy)
    if (consumer == null)
      _invalid("Article-media build-context consumer must be defined")
    if (beforesourcerevalidation == null)
      _invalid("Article-media build-context source-revalidation hook must be defined")
    val projectroot = _require_direct_directory(projectrootarg, "Article-media build-context project root")
    val publicationroot = _create_direct_directory_chain(publicationrootarg, "Article-media build-context publication root")
    val repositoryroot = _normalize_root(repositoryrootarg, "Article-media build-context repository root")
    val context = CozyArticleMediaRegistry.transaction(publicationroot) { transaction =>
      _materialize(projectroot, publicationroot, repositoryroot, selected, beforesourcerevalidation, transaction)
    }
    _lease(context)(consumer)
  }

  private[cozy] def normalizeStrategy(value: String): Strategy =
    Option(value).filter(_.nonEmpty) match {
      case Some("draft") => Strategy.Draft
      case Some("work-in-progress") | Some("wip") => Strategy.WorkInProgress
      case Some("production-preview") | Some("preview") => Strategy.ProductionPreview
      case Some("production") => Strategy.Production
      case _ => _invalid(s"Unsupported article-media build strategy: $value")
    }

  /* Producer and verifier deliberately share this one framing seam. */
  private[publication] def contextDigest(strategy: String, publication: Path): String = {
    val selected = normalizeStrategy(strategy)
    _digest(selected, _direct_files(publication))
  }

  private[publication] def activeLeaseCount(publication: Path): Int = synchronized {
    if (publication == null) 0 else _active_leases.getOrElse(publication.toAbsolutePath.normalize(), 0)
  }

  private def _materialize(
    projectroot: Path,
    publicationroot: Path,
    repositoryroot: Path,
    strategy: Strategy,
    beforesourcerevalidation: () => Unit,
    transaction: CozyArticleMediaRegistry.Transaction
  ): Context = {
    val snapshot = transaction.snapshot
    val projection = CozyArticleMediaRegistry.canonicalProjection(snapshot)
    val snapshots = _snapshots_directory(projectroot, strategy)
    val work = Files.createTempDirectory(snapshots, ".work-")
    var moved = false
    try {
      val publication = Files.createDirectory(work.resolve("publication"))
      val bundles = _copy_snapshot_bundles(publicationroot, publication, snapshot)
      val metadata = _load_metadata(publication, bundles)
      val policy = CozyArticleMediaPolicy.evaluate(strategy.policy, metadata, projection.integrityResults, repositoryroot)
      _apply_omissions(publication, bundles, projection, policy)
      val digest = _digest(strategy, _direct_files(publication))
      val target = snapshots.resolve(digest).normalize()
      if (!target.startsWith(snapshots))
        _invalid("Article-media build-context snapshot target escapes its parent")
      _verify_digest(strategy, publication, digest, bundles.map(_.filename).toSet)
      beforesourcerevalidation()
      transaction.validate(Vector.empty)
      if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
        _verify_installed(strategy, target.resolve("publication"), digest, bundles.map(_.filename).toSet)
      } else {
        _install(work, target, strategy, digest, bundles.map(_.filename).toSet)
        moved = Files.notExists(work, LinkOption.NOFOLLOW_LINKS)
      }
      Context(strategy, digest, target.resolve("publication"), repositoryroot, policy.omittedKeys, policy.diagnostics)
    } finally {
      if (!moved)
        _delete_work(work)
    }
  }

  private def _copy_snapshot_bundles(
    root: Path,
    destination: Path,
    snapshot: CozyArticleMediaRegistry.Snapshot
  ): Vector[BundleFile] = {
    if (snapshot == null || snapshot.bundleDigests == null)
      _invalid("Article-media build-context snapshot must be defined")
    snapshot.bundleDigests.toVector.sortBy(_._1).map { case (name, expected) =>
      if (name == null || expected == null)
        _invalid("Article-media build-context snapshot bundle must be defined")
      val filename = s"$name.json"
      val source = root.resolve(filename).normalize()
      if (!source.startsWith(root) || Files.isSymbolicLink(source) || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS))
        _invalid(s"Article-media build-context bundle must be a direct regular file: $filename")
      val bytes = Files.readAllBytes(source)
      if (_sha256(bytes) != expected)
        _invalid(s"Article-media build-context stale configured bundle: $filename")
      Files.write(destination.resolve(filename), bytes)
      BundleFile(filename, source, bytes)
    }
  }

  private def _load_metadata(publication: Path, bundles: Vector[BundleFile]): PublishMetadata =
    if (bundles.isEmpty)
      PublishMetadata(Vector.empty)
    else
      try PublishMetadata.load(publication.toFile).get
      catch {
        case _: Throwable => _invalid("Article-media build-context publication metadata is invalid")
      }

  private def _apply_omissions(
    publication: Path,
    bundles: Vector[BundleFile],
    projection: CozyArticleMediaRegistry.CanonicalProjection,
    policy: CozyArticleMediaPolicy.Result
  ): Unit = {
    val omitted = policy.omittedKeys.toSet
    val strictreplacements = projection.strictPublications.flatMap { strict =>
      val variants = strict.publication.variants.flatMap { variant =>
        val infographic = if (omitted.contains(_key(strict.publication.articleIdentity, variant.locale, CozyArticleMediaIntegrity.Role.Infographic))) None else variant.infographic
        val video = if (omitted.contains(_key(strict.publication.articleIdentity, variant.locale, CozyArticleMediaIntegrity.Role.Video))) None else variant.video
        if (infographic.isDefined || video.isDefined || variant.articlePdf.isDefined || variant.summarySlidesPdf.isDefined)
          Some(CozyArticleMediaPublication.Variant(
            locale = variant.locale,
            infographic = infographic,
            video = video,
            articlePdf = variant.articlePdf,
            summarySlidesPdf = variant.summarySlidesPdf
          ))
        else None
      }.toVector
      val replacement = if (variants.isEmpty) None else Some(CozyArticleMediaPublication.produce(strict.publication.articleIdentity, variants).metadata)
      if (replacement == Some(strict.metadata)) None else Some(strict.entryPath -> replacement)
    }.toMap
    val replacements = strictreplacements ++ policy.excludedIntegrityEntryPaths.map(_ -> None).toMap
    if (replacements.nonEmpty)
      bundles.foreach { bundle =>
        val changed = _rewrite_bundle(publication.resolve(bundle.filename), replacements)
        if (changed.isDefined)
          Files.write(publication.resolve(bundle.filename), changed.get)
      }
  }

  private def _rewrite_bundle(path: Path, replacements: Map[String, Option[JsObject]]): Option[Array[Byte]] = {
    val bundle = Json.parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8)).asOpt[JsObject].getOrElse(
      _invalid(s"Article-media build-context bundle is malformed: ${path.getFileName}")
    )
    val entries = bundle.value.get("entries") match {
      case Some(JsArray(values)) => values.toVector
      case _ => _invalid(s"Article-media build-context bundle entries are invalid: ${path.getFileName}")
    }
    var changed = false
    val rewritten = entries.flatMap { value =>
      val entry = value.asOpt[JsObject].getOrElse(_invalid(s"Article-media build-context bundle entry is invalid: ${path.getFileName}"))
      entry.value.get("path") match {
        case Some(JsString(name)) if replacements.contains(name) =>
          changed = true
          replacements(name).map(metadata => entry + ("metadata" -> metadata))
        case _ => Some(entry)
      }
    }.sortBy { value =>
      (value \ "path").asOpt[String].getOrElse(_invalid(s"Article-media build-context bundle entry path is invalid: ${path.getFileName}"))
    }
    if (!changed) None
    else Some(Json.stringify(bundle + ("entries" -> JsArray(rewritten))).getBytes(StandardCharsets.UTF_8))
  }

  private def _install(work: Path, target: Path, strategy: Strategy, digest: String, expected: Set[String]): Unit = {
    try {
      Files.move(work, target, StandardCopyOption.ATOMIC_MOVE)
    } catch {
      case _: FileAlreadyExistsException => _verify_installed(strategy, target.resolve("publication"), digest, expected)
      case _: AtomicMoveNotSupportedException => _invalid(s"Atomic article-media build-context installation is not supported: $target")
      case e: IOException =>
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS))
          _verify_installed(strategy, target.resolve("publication"), digest, expected)
        else _invalid(s"Atomic article-media build-context installation failed: ${e.getMessage}")
    }
  }

  private def _verify_installed(strategy: Strategy, publication: Path, digest: String, expected: Set[String]): Unit = {
    val snapshot = Option(publication).flatMap(x => Option(x.getParent)).getOrElse(
      _invalid("Article-media build-context installed snapshot must be defined")
    )
    if (Files.isSymbolicLink(snapshot) || !Files.isDirectory(snapshot, LinkOption.NOFOLLOW_LINKS) ||
      Files.isSymbolicLink(publication) || !Files.isDirectory(publication, LinkOption.NOFOLLOW_LINKS))
      _invalid(s"Article-media build-context installed publication must be a direct directory: $publication")
    _verify_digest(strategy, publication, digest, expected)
  }

  private def _verify_digest(strategy: Strategy, publication: Path, digest: String, expected: Set[String]): Unit = {
    val files = _direct_files(publication)
    if (files.map(_._1).toSet != expected)
      _invalid("Article-media build-context snapshot file set does not match the validated source")
    if (_digest(strategy, files) != digest)
      _invalid("Article-media build-context snapshot digest does not match its content")
  }

  private def _snapshots_directory(projectroot: Path, strategy: Strategy): Path =
    _create_direct_directory_chain(
      projectroot.resolve("target/cozy-bok/article-media").resolve(strategy.name).resolve("snapshots"),
      "Article-media build-context snapshot parent",
      projectroot
    )

  private def _direct_files(root: Path): Vector[(String, Array[Byte])] = {
    if (root == null || Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))
      _invalid(s"Article-media build-context publication must be a direct directory: $root")
    val stream = Files.list(root)
    try stream.iterator.asScala.toVector.map { path =>
      if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
        _invalid(s"Article-media build-context publication file must be direct and regular: ${path.getFileName}")
      path.getFileName.toString -> Files.readAllBytes(path)
    }
    finally stream.close()
  }

  private def _digest(strategy: Strategy, files: Vector[(String, Array[Byte])]): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val normalized = files.map { case (name, bytes) =>
      if (name == null || name.isEmpty || name.contains('/') || name.contains('\\') || bytes == null)
        _invalid("Article-media build-context digest input is invalid")
      name -> bytes
    }.sortWith { case ((left, _), (right, _)) => _unsigned_compare(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8)) < 0 }
    _frame(digest, strategy.name.getBytes(StandardCharsets.UTF_8))
    _frame_length(digest, normalized.size.toLong)
    normalized.foreach { case (name, bytes) =>
      _frame(digest, name.getBytes(StandardCharsets.UTF_8))
      _frame(digest, bytes)
    }
    digest.digest().map(x => f"${x & 0xff}%02x").mkString
  }

  private def _frame(digest: MessageDigest, bytes: Array[Byte]): Unit = {
    _frame_length(digest, bytes.length.toLong)
    digest.update(bytes)
  }

  private def _frame_length(digest: MessageDigest, value: Long): Unit = {
    if (value < 0)
      _invalid("Article-media build-context digest length must be non-negative")
    digest.update(ByteBuffer.allocate(8).putLong(value).array())
  }

  private def _unsigned_compare(left: Array[Byte], right: Array[Byte]): Int = {
    val limit = math.min(left.length, right.length)
    var index = 0
    while (index < limit && (left(index) & 0xff) == (right(index) & 0xff))
      index += 1
    if (index == limit) left.length.compare(right.length)
    else (left(index) & 0xff).compare(right(index) & 0xff)
  }

  private def _lease[A](context: Context)(consumer: Context => A): A = {
    val identity = context.publicationPath.toAbsolutePath.normalize()
    synchronized { _active_leases = _active_leases.updated(identity, _active_leases.getOrElse(identity, 0) + 1) }
    try consumer(context)
    finally synchronized {
      val remaining = _active_leases.getOrElse(identity, 1) - 1
      _active_leases = if (remaining <= 0) _active_leases - identity else _active_leases.updated(identity, remaining)
    }
  }

  private def _key(identity: String, locale: String, role: CozyArticleMediaIntegrity.Role): CozyArticleMediaAssociation.Key =
    CozyArticleMediaAssociation.Key(identity, locale, role)

  private def _require_direct_directory(value: Path, label: String): Path = {
    val root = _normalize_root(value, label)
    _require_direct_directory_component(root, label, value)
    root
  }

  private def _create_direct_directory_chain(value: Path, label: String): Path = {
    val root = _normalize_root(value, label)
    val ancestor = _deepest_existing_ancestor(root)
    _require_direct_directory_component(ancestor, label, root)
    _create_direct_directory_descendants(root, ancestor, label)
    root
  }

  private def _create_direct_directory_chain(value: Path, label: String, anchor: Path): Path = {
    val root = _normalize_root(value, label)
    val trustedroot = _require_direct_directory(anchor, s"$label configured project root")
    if (!root.startsWith(trustedroot))
      _invalid(s"$label must remain beneath its configured project root: $root")
    _create_direct_directory_descendants(root, trustedroot, label)
    root
  }

  private def _create_direct_directory_descendants(root: Path, anchor: Path, label: String): Unit = {
    var current = anchor
    val iterator = anchor.relativize(root).iterator()
    while (iterator.hasNext) {
      current = current.resolve(iterator.next())
      _require_or_create_direct_directory_component(current, label, root)
    }
  }

  private def _deepest_existing_ancestor(root: Path): Path = {
    var current = root
    while (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
      current = Option(current.getParent).getOrElse(
        _invalid(s"Article-media build-context directory root has no existing ancestor: $root")
      )
    }
    current
  }

  private def _require_or_create_direct_directory_component(path: Path, label: String, root: Path): Unit = {
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS))
      _require_direct_directory_component(path, label, root)
    else {
      try Files.createDirectory(path)
      catch {
        case _: FileAlreadyExistsException => ()
        case e: IOException => _invalid(s"$label cannot create directory: ${e.getMessage}")
      }
      _require_direct_directory_component(path, label, root)
    }
  }

  private def _require_direct_directory_component(path: Path, label: String, root: Path): Unit =
    if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
      _invalid(s"$label must have direct directory components: $root")

  private def _normalize_root(value: Path, label: String): Path =
    if (value == null) _invalid(s"$label must be defined") else value.toAbsolutePath.normalize()

  private def _delete_work(path: Path): Unit = {
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      val stream = Files.walk(path)
      try stream.iterator.asScala.toVector.sortBy(x => (-x.getNameCount, x.toString)).foreach(Files.deleteIfExists)
      catch { case _: IOException => () }
      finally stream.close()
    }
  }

  private def _sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(x => f"${x & 0xff}%02x").mkString

  private def _invalid(message: String): Nothing =
    RAISE.invalidArgumentFault(message)
}
