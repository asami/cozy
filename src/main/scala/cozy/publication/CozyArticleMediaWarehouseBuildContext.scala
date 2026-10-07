package cozy.publication

import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path}
import scala.collection.JavaConverters._
import cozy.media.CozyMediaWarehouse
import org.goldenport.RAISE
import org.smartdox.metadata.PublishMetadata
import org.smartdox.metadata.PublishMetadata.VideoPresentation
import play.api.libs.json.{JsArray, JsObject, Json}

/*
 * @since   Oct.  7, 2026
 * @version Oct.  7, 2026
 * @author  ASAMI, Tomoharu
 */
private[publication] object CozyArticleMediaWarehouseBuildContext {
  final case class Diagnostic(articleIdentity: String, locale: String, role: String, publicPath: String, reason: String)
  final case class Result(context: CozyArticleMediaBuildContext.Context, diagnostics: Vector[Diagnostic])
  private final case class Reference(identity: String, locale: String, role: String, uri: URI, mimetype: Option[String])

  def hasCanonicalReferences(projection: CozyArticleMediaRegistry.CanonicalProjection): Boolean =
    _references(projection).nonEmpty

  def materialize(
    projectRoot: Path,
    publicationRoot: Path,
    repositoryRoot: Path,
    strategy: CozyArticleMediaBuildContext.Strategy,
    current: CozyArticleMediaRegistry.CurrentSiteState,
    projection: CozyArticleMediaRegistry.CanonicalProjection
  ): Result = materializeWithWriter(projectRoot, publicationRoot, repositoryRoot, strategy, current, projection)(_write_bundle)

  private[publication] def materializeWithWriter(
    projectRoot: Path,
    publicationRoot: Path,
    repositoryRoot: Path,
    strategy: CozyArticleMediaBuildContext.Strategy,
    current: CozyArticleMediaRegistry.CurrentSiteState,
    projection: CozyArticleMediaRegistry.CanonicalProjection
  )(writeBundle: (Path, JsObject) => Unit): Result = {
    val root = CozyMediaWarehouse.requireWarehouseRoot(repositoryRoot.toString)
    val diagnostics = _references(projection).flatMap { reference =>
      val publicpath = _validate_reference(reference)
      if (_available(root, publicpath)) None
      else Some(Diagnostic(reference.identity, reference.locale, reference.role, publicpath, "Unavailable"))
    }.sortBy(x => (x.articleIdentity, x.locale, x.role, x.publicPath))
    val legacyreplacements = projection.strictPublications.map { strict =>
      strict.entryPath -> _publication(strict, Set.empty, Set.empty, legacyview = true)
    }.toMap
    val legacyentries = current.entries.flatMap { entry =>
      legacyreplacements.get(entry.path) match {
        case Some(replacement) => replacement.map(value => entry.copy(metadata = value))
        case None => Some(entry)
      }
    }
    val legacykeys = projection.strictPublications.flatMap { strict =>
      strict.publication.variants.flatMap { variant =>
        val image = variant.infographic.filterNot(x => _canonical(x.publicPath)).map(_ =>
          _key(strict.publication.articleIdentity, variant.locale, CozyArticleMediaIntegrity.Role.Infographic))
        val video = variant.video.filter(x => x.presentation == VideoPresentation.SiteHosted && x.contentUrl.nonEmpty).map(_ =>
          _key(strict.publication.articleIdentity, variant.locale, CozyArticleMediaIntegrity.Role.Video))
        image.toVector ++ video.toVector
      }
    }.toSet
    val integrities = projection.integrityResults.filter(x => legacykeys.contains(
      _key(x.record.articleIdentity, x.record.locale, x.record.role)))
    val semanticmetadata = PublishMetadata(legacyentries.map { entry =>
      val json = io.circe.parser.parse(Json.stringify(entry.metadata)).fold(
        _ => _invalid(s"Article-media semantic metadata is invalid: ${entry.path}"), identity)
      PublishMetadata.Entry(entry.path, entry.key, json)
    })
    val policy = CozyArticleMediaPolicy.evaluate(strategy.policy, semanticmetadata, integrities, root)
    val unavailable = diagnostics.map(x => (x.articleIdentity, x.locale, x.role)).toSet
    val replacements = projection.strictPublications.map { strict =>
      strict.entryPath -> _publication(strict, unavailable, policy.omittedKeys.toSet, legacyview = false)
    }.toMap ++ policy.excludedIntegrityEntryPaths.map(_ -> None).toMap
    // Bundle names come only from strict current-site admission. Retain the
    // admitted container and entry wrappers while replacing native metadata.
    val bundles = current.bundleNames.map { name =>
      val bundle = Json.parse(Files.readString(publicationRoot.resolve(s"$name.json"), StandardCharsets.UTF_8)).as[JsObject]
      val entries = (bundle \ "entries").as[Vector[JsObject]].flatMap { entry =>
        val path = (entry \ "path").as[String]
        replacements.get(path) match {
          case Some(replacement) => replacement.map(value => entry + ("metadata" -> value))
          case None => Some(entry)
        }
      }
      name -> (bundle + ("entries" -> JsArray(entries)))
    }
    val parent = _context_parent(projectRoot, strategy)
    val work = Files.createTempDirectory(parent, "context-")
    var completed = false
    try {
      val publication = Files.createDirectory(work.resolve("publication"))
      bundles.foreach { case (name, bundle) =>
        writeBundle(publication.resolve(s"$name.json"), bundle)
      }
      val context = CozyArticleMediaBuildContext.Context(strategy, publication, root, policy.omittedKeys, policy.diagnostics)
      completed = true
      Result(context, diagnostics)
    } finally {
      if (!completed) _delete_owned(work)
    }
  }

  private def _write_bundle(path: Path, bundle: JsObject): Unit = {
    Files.write(path, Json.stringify(bundle).getBytes(StandardCharsets.UTF_8))
    ()
  }

  private def _references(projection: CozyArticleMediaRegistry.CanonicalProjection): Vector[Reference] =
    projection.strictPublications.flatMap { strict =>
      strict.publication.variants.flatMap { variant =>
        val identity = strict.publication.articleIdentity
        variant.infographic.map(x => Reference(identity, variant.locale, "infographic", x.publicPath, x.mediaType)).toVector ++
          variant.articlePdf.map(x => Reference(identity, variant.locale, "article_pdf", x.publicPath, Some(x.mediaType))).toVector ++
          variant.summarySlidesPdf.map(x => Reference(identity, variant.locale, "summary_slides_pdf", x.publicPath, Some(x.mediaType))).toVector
      }
    }.filter(x => _canonical(x.uri))

  private def _canonical(uri: URI): Boolean = uri.toString.startsWith("/media/")

  private def _validate_reference(reference: Reference): String = {
    val uri = reference.uri
    val literal = uri.toString
    val extension = literal.substring(literal.lastIndexOf('.') + 1)
    val expected = CozyMediaWarehouse.canonicalPublicPath(reference.identity, reference.locale, reference.role, extension)
    if (uri.isAbsolute || uri.getRawAuthority != null || uri.getRawQuery != null || uri.getRawFragment != null || literal != expected)
      _invalid(s"Article-media canonical public path must equal $expected: $literal")
    val mimetype = if (reference.role == "infographic") Map(
      "svg" -> "image/svg+xml", "png" -> "image/png", "jpg" -> "image/jpeg", "jpeg" -> "image/jpeg", "webp" -> "image/webp"
    )(extension) else "application/pdf"
    if (reference.mimetype.exists(_ != mimetype))
      _invalid(s"Article-media canonical MIME must equal $mimetype: $literal")
    literal
  }

  private def _available(root: Path, publicpath: String): Boolean = {
    val destination = root.resolve(publicpath.drop(1))
    if (!destination.startsWith(root) || destination == root)
      _invalid(s"Article-media canonical destination escapes warehouse: $publicpath")
    var current = root
    val iterator = root.relativize(destination).iterator()
    var missing = false
    while (iterator.hasNext) {
      current = current.resolve(iterator.next())
      if (Files.isSymbolicLink(current)) _invalid(s"Article-media warehouse path traverses symlink: $current")
      if (Files.notExists(current, LinkOption.NOFOLLOW_LINKS)) missing = true
      else if (iterator.hasNext) {
        if (!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(current))
          _invalid(s"Article-media warehouse ancestor must be a direct readable directory: $current")
      } else if (!Files.isRegularFile(current, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(current))
        _invalid(s"Article-media warehouse media must be a direct regular readable file: $current")
    }
    !missing
  }

  private def _publication(
    strict: CozyArticleMediaPublication.Result,
    unavailable: Set[(String, String, String)],
    omitted: Set[CozyArticleMediaAssociation.Key],
    legacyview: Boolean
  ): Option[JsObject] = {
    val identity = strict.publication.articleIdentity
    val variants = strict.publication.variants.flatMap { variant =>
      val image = variant.infographic.filterNot { image =>
        (legacyview && _canonical(image.publicPath)) || unavailable.contains((identity, variant.locale, "infographic")) ||
          omitted.contains(_key(identity, variant.locale, CozyArticleMediaIntegrity.Role.Infographic))
      }
      val video = variant.video.filterNot(_ => omitted.contains(_key(identity, variant.locale, CozyArticleMediaIntegrity.Role.Video)))
      val articlepdf = variant.articlePdf.filterNot(_ => unavailable.contains((identity, variant.locale, "article_pdf")))
      val slidespdf = variant.summarySlidesPdf.filterNot(_ => unavailable.contains((identity, variant.locale, "summary_slides_pdf")))
      if (image.nonEmpty || video.nonEmpty || articlepdf.nonEmpty || slidespdf.nonEmpty)
        Some(CozyArticleMediaPublication.Variant(variant.locale, image, video, articlepdf, slidespdf))
      else None
    }
    if (variants.isEmpty) None else Some(CozyArticleMediaPublication.produce(identity, variants).metadata)
  }

  private def _key(identity: String, locale: String, role: CozyArticleMediaIntegrity.Role): CozyArticleMediaAssociation.Key =
    CozyArticleMediaAssociation.Key(identity, locale, role)

  private def _context_parent(projectroot: Path, strategy: CozyArticleMediaBuildContext.Strategy): Path = {
    val parent = projectroot.resolve("target/cozy-bok/article-media").resolve(strategy.name).resolve("warehouse-contexts")
    var current = projectroot
    val iterator = projectroot.relativize(parent).iterator()
    while (iterator.hasNext) {
      current = current.resolve(iterator.next())
      if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(current)
      if (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS))
        _invalid(s"Article-media warehouse context parent must have direct directory components: $current")
    }
    parent
  }

  private def _delete_owned(work: Path): Unit = {
    val stream = Files.walk(work)
    try stream.iterator.asScala.toVector.sortBy(x => (-x.getNameCount, x.toString)).foreach(Files.deleteIfExists)
    finally stream.close()
  }

  private def _invalid(message: String): Nothing = RAISE.invalidArgumentFault(message)
}
