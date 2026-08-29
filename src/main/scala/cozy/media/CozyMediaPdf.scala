package cozy.media

import org.goldenport.RAISE
import io.circe.{Decoder, HCursor}
import java.nio.charset.StandardCharsets
import java.nio.file.{AtomicMoveNotSupportedException, Files, LinkOption, Path, StandardCopyOption}
import scala.util.control.NonFatal

/*
 * @since   Aug. 29, 2026
 * @version Aug. 29, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyMediaPdf {
  private[cozy] def _resource_article_media_decoder: Decoder[CozyMedia.ResourceArticleMedia] =
    (c: HCursor) => _decode_resource_article_media(c)

  private[cozy] def _decode_resource_article_media(c: HCursor): Decoder.Result[CozyMedia.ResourceArticleMedia] =
    for {
      _ <- _require_article_media_object(c)
      role <- c.downField("role").as[String]
      _ <- _require_resource_article_media_keys(c, role)
      publicpath <- _optional_field[String](c, "publicPath")
      mediatype <- _optional_field[String](c, "mediaType")
      alt <- _optional_field[String](c, "alt")
      production <- _optional_field[String](c, "production")
      label <- _optional_field[String](c, "label")
    } yield CozyMedia.ResourceArticleMedia(role, publicpath, mediatype, alt, production, label)

  private[cozy] def validateArticleMedia(resource: CozyMedia.Resource, media: CozyMedia.ResourceArticleMedia): Unit = {
    val publicpath = media.publicPath
    val mediatype = media.mediaType
    val label = media.label
    _validate_article_media_value(publicpath.getOrElse(""), s"Media resource ${resource.id} articleMedia.publicPath")
    _validate_article_media_value(mediatype.getOrElse(""), s"Media resource ${resource.id} articleMedia.mediaType")
    label.foreach(value => _validate_article_media_value(value, s"Media resource ${resource.id} articleMedia.label"))
    if (resource.kind != "document" || mediatype != Some("application/pdf") || !resource.language.exists(Set("ja", "en")))
      _invalid(s"Media resource ${resource.id} articleMedia ${media.role} requires kind document, mediaType application/pdf, and language ja or en")
    if (media.role == "summary_slides_pdf" && resource.build != "prebuilt")
      _invalid(s"Media resource ${resource.id} summary_slides_pdf requires build: prebuilt")
  }

  final case class RendererConfig(name: String, version: String, command: Vector[String])
  object RendererConfig {
    implicit val decoder: Decoder[RendererConfig] = (c: HCursor) =>
      for {
        _ <- _require_exact_keys(c, Set("name", "version", "command"), "articlePdf renderer")
        name <- c.downField("name").as[String]
        version <- c.downField("version").as[String]
        command <- c.downField("command").as[Vector[String]]
      } yield RendererConfig(name, version, command)
  }

  final case class Config(latexFormat: String, renderer: RendererConfig)
  object Config {
    implicit val decoder: Decoder[Config] = (c: HCursor) =>
      for {
        _ <- _require_exact_keys(c, Set("latexFormat", "renderer"), "articlePdf")
        latexformat <- c.downField("latexFormat").as[String]
        renderer <- c.downField("renderer").as[RendererConfig]
      } yield Config(latexformat, renderer)
  }

  def validateDescriptor(descriptor: CozyMedia.Descriptor, resource: CozyMedia.Resource, root: Path): Unit = {
    val configuration = resource.articlePdf.getOrElse(_invalid(s"Article PDF resource requires articlePdf configuration: ${resource.id}"))
    if (resource.kind != "document" || resource.source != Some(descriptor.knowledge.source) || resource.output.isEmpty || !resource.language.exists(Set("ja", "en")))
      _invalid(s"Article PDF resource requires document kind, direct knowledge.source, output, and language ja or en: ${resource.id}")
    resource.articleMedia match {
      case Some(CozyMedia.ResourceArticleMedia("article_pdf", _, Some("application/pdf"), _, _, _)) => ()
      case _ => _invalid(s"Article PDF resource requires articleMedia.role article_pdf and application/pdf: ${resource.id}")
    }
    if (!Set("standard", "business").contains(configuration.latexFormat))
      _invalid(s"Article PDF resource latexFormat must be standard or business: ${resource.id}")
    _identity(configuration.renderer.name, s"Article PDF renderer name ${resource.id}")
    _identity(configuration.renderer.version, s"Article PDF renderer version ${resource.id}")
    if (configuration.renderer.command.isEmpty || configuration.renderer.command.exists(x => x == null || x.isEmpty || x != x.trim))
      _invalid(s"Article PDF renderer command must be nonempty exact argv tokens: ${resource.id}")
    val source = root.resolve(resource.source.get).normalize()
    if (!_direct_regular_file(source))
      _invalid(s"Article PDF source must be a direct regular non-symlink file: $source")
  }

  def build(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource, runner: CozyMedia.ProcessRunner): String = {
    val resource = resolved.resource
    val configuration = resource.articlePdf.getOrElse(_invalid(s"Article PDF configuration is missing: ${resource.id}"))
    val source = resolved.source.getOrElse(_invalid(s"Article PDF source is missing: ${resource.id}"))
    val output = resolved.output.getOrElse(_invalid(s"Article PDF output is missing: ${resource.id}"))
    if (source != plan.knowledgeSource || !_direct_regular_file(source))
      _invalid(s"Article PDF source must be the current direct regular knowledge source: ${resource.id}")
    if (Files.exists(output, LinkOption.NOFOLLOW_LINKS) && !_direct_regular_file(output))
      _invalid(s"Article PDF output must be absent or a direct regular non-symlink file: $output")
    if (source == output)
      _invalid(s"Article PDF output must not replace its knowledge source: ${resource.id}")
    val parent = Option(output.getParent).getOrElse(_invalid(s"Article PDF output requires a parent: $output"))
    Files.createDirectories(parent)
    val before = CozyMediaReceipt.capture(plan).inputSetSha256
    val staged = Files.createTempFile(parent, ".cozy-media-pdf-", ".pdf")
    Files.delete(staged)
    try {
      val command = configuration.renderer.command ++ Vector(
        source.toString,
        "--output", staged.toString,
        "--locale", resource.language.get,
        "--latex-format", configuration.latexFormat
      )
      val exit = runner.run(command, plan.descriptorRoot)
      if (exit != 0)
        _invalid(s"Article PDF renderer failed for ${resource.id}: exit=$exit")
      if (!_direct_regular_file(staged) || !_pdf(staged))
        _invalid(s"Article PDF renderer did not create a direct regular PDF output: ${resource.id}")
      if (!_direct_regular_file(source))
        _invalid(s"Article PDF source changed during build: ${resource.id}")
      val after = CozyMediaReceipt.capture(plan).inputSetSha256
      if (before != after)
        _invalid(s"Article PDF inputs changed during build; no fresh acceptance evidence was written: ${resource.id}")
      try Files.move(staged, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
      catch {
        case _: AtomicMoveNotSupportedException =>
          _invalid(s"Article PDF output replacement requires atomic move: ${resource.id}")
      }
      s"${resource.id}: built $output"
    } finally {
      try Files.deleteIfExists(staged) catch { case NonFatal(_) => () }
    }
  }

  private def _pdf(path: Path): Boolean = {
    val bytes = Files.readAllBytes(path)
    bytes.length >= 5 && new String(bytes.take(5), StandardCharsets.US_ASCII) == "%PDF-"
  }

  private def _direct_regular_file(path: Path): Boolean = path != null && !Files.isSymbolicLink(path) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)

  private def _optional_field[A: Decoder](c: HCursor, field: String): Decoder.Result[Option[A]] =
    c.downField(field).success match {
      case Some(cursor) => cursor.as[A].map(Some(_))
      case None => Right(None)
    }

  private def _require_article_media_object(c: HCursor): Decoder.Result[Unit] =
    c.value.asObject match {
      case Some(_) => Right(())
      case None => Left(io.circe.DecodingFailure("articleMedia must be an object", c.history))
    }

  private def _require_resource_article_media_keys(c: HCursor, role: String): Decoder.Result[Unit] = {
    val keys = c.value.asObject.map(_.keys.toSet).getOrElse(
      return Left(io.circe.DecodingFailure("articleMedia must be an object", c.history))
    )
    role match {
      case "infographic" =>
        val required = Set("role", "publicPath")
        val allowed = required ++ Set("mediaType", "alt")
        if (required.subsetOf(keys) && keys.subsetOf(allowed)) Right(())
        else Left(io.circe.DecodingFailure("articleMedia infographic requires role and publicPath and permits only mediaType and alt", c.history))
      case "video" =>
        val expected = Set("role", "production")
        if (keys == expected) Right(())
        else Left(io.circe.DecodingFailure("articleMedia video requires exactly role and production", c.history))
      case "article_pdf" | "summary_slides_pdf" =>
        val required = Set("role", "publicPath", "mediaType")
        val allowed = required ++ Set("label")
        if (required.subsetOf(keys) && keys.subsetOf(allowed)) Right(())
        else Left(io.circe.DecodingFailure("articleMedia PDF requires role, publicPath, mediaType and permits only label", c.history))
      case _ => Left(io.circe.DecodingFailure("articleMedia role must be infographic, video, article_pdf, or summary_slides_pdf", c.history))
    }
  }

  private def _validate_article_media_value(value: String, label: String): Unit =
    if (value == null || value.trim.isEmpty || value != value.trim)
      _invalid(s"$label must be a non-empty trimmed string")

  private def _identity(value: String, label: String): Unit =
    if (value == null || value.isEmpty || value != value.trim)
      _invalid(s"$label must be a nonempty exact identity")

  private def _require_exact_keys(c: HCursor, keys: Set[String], label: String): Decoder.Result[Unit] =
    c.value.asObject match {
      case Some(value) if value.keys.toSet == keys => Right(())
      case Some(_) => Left(io.circe.DecodingFailure(s"$label requires exactly: ${keys.toVector.sorted.mkString(", ")}", c.history))
      case None => Left(io.circe.DecodingFailure(s"$label must be an object", c.history))
    }

  private def _invalid(message: String): Nothing = RAISE.invalidArgumentFault(message)
}
