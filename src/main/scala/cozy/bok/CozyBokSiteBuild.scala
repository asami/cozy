package cozy.bok

import org.goldenport.RAISE
import org.goldenport.cli.{Request => CliRequest}
import org.goldenport.cli.spec
import cozy.bok.scenario.ScenarioMetadata
import cozy.bok.BibliographyEntry._
import cozy.config.CozyProjectYamlConfig
import cozy.publication.{CozyArticleMediaBuildContext, CozyArticleMediaInfographicCommand, CozyArticleMediaInfographicEvidence, CozyArticleMediaVideoCommand}
import cozy.video.{CozyVideo, CozyVideoPublisher}
import org.smartdox.{Body, Document, Dox}
import org.smartdox.parser.Dox2Parser
import org.smartdox.transformers.Dox2HtmlTransformer
import org.smartdox.generator.{Context => SmartDoxContext}
import org.smartdox.metadata.DocumentMetaData
import org.goldenport.i18n.I18NContext
import java.net.URLEncoder
import java.time.{Instant, LocalDate, LocalDateTime, YearMonth, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.regex.Pattern
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths, StandardCopyOption}
import java.util.zip.{ZipEntry, ZipFile, ZipInputStream, ZipOutputStream}
import scala.collection.JavaConverters._
import scala.util.matching.Regex
import scala.util.control.NonFatal
import scala.sys.process._
import io.circe.{Decoder, HCursor, Json}
import io.circe.parser
import io.circe.syntax._

/*
 * @since   Aug. 14, 2026
 * @version Aug. 14, 2026
 * @author  ASAMI, Tomoharu
 */

private[cozy] trait CozyBokSiteBuild {
  self: CozyBokImplementation.type =>
  private[bok] def _run_antora(config: BuildConfig, runner: Runner): Unit = {
    config.localeMode match {
      case LocaleMode.SingleLocaleRoot =>
        _copy_ui_bundle(config, config.antoraPath)
        runner.run(_docker_antora(config, config.antora, config.website), config.project)
      case LocaleMode.MultiLocaleSubdirs =>
        config.languages.foreach { lang =>
          val antoradir = Paths.get(config.antora).resolve(lang).toString
          val websitedir = Paths.get(config.website).resolve(lang).toString
          _copy_ui_bundle(config, config.project.resolve(antoradir))
          runner.run(_docker_antora(config, antoradir, websitedir), config.project)
        }
    }
  }

  private[bok] def _dox_antora_command(config: BuildConfig, context: CozyArticleMediaBuildContext.Context): Vector[String] =
    Vector("dox", "antora", "-strategy", config.strategy) ++
      _publication_options(config, context, includerdf = false) ++
      Vector(config.source)

  private[bok] def _dox_site_command(config: BuildConfig, context: CozyArticleMediaBuildContext.Context): Vector[String] =
    Vector("dox", "site", "-strategy", config.strategy, "-output.scope.policy", config.siteOutputScopePolicy) ++
      _publication_options(config, context, includerdf = true) ++
      Vector(config.source)

  private[bok] def _planned_dox_antora_command(config: BuildConfig): Vector[String] =
    Vector("dox", "antora", "-strategy", config.strategy) ++
      _configured_publication_options(config, includerdf = false) ++
      Vector(config.source)

  private[bok] def _planned_dox_site_command(config: BuildConfig): Vector[String] =
    Vector("dox", "site", "-strategy", config.strategy, "-output.scope.policy", config.siteOutputScopePolicy) ++
      _configured_publication_options(config, includerdf = true) ++
      Vector(config.source)

  private[bok] def _publication_options(
    config: BuildConfig,
    context: CozyArticleMediaBuildContext.Context,
    includerdf: Boolean
  ): Vector[String] = {
    val base = Vector(
      "-publication", context.publicationPath.toString,
      "-publication.repository", context.repositoryPath.toString
    )
    if (includerdf && config.publication.mergeRdf)
      base ++ Vector(
        "-publication.rdf.missing.policy", config.publication.missingRdfPolicy
      )
    else
      base
  }

  private def _configured_publication_options(config: BuildConfig, includerdf: Boolean): Vector[String] = {
    val base = Vector(
      "-publication", config.publication.publicationPath(config.project).toString,
      "-publication.repository", config.publication.repositoryPath(config.project).toString
    )
    if (includerdf && config.publication.mergeRdf)
      base ++ Vector("-publication.rdf.missing.policy", config.publication.missingRdfPolicy)
    else
      base
  }

  private def _docker_antora(config: BuildConfig, workdir: String, output: String): Vector[String] =
    Vector(
      "docker",
      "run",
      "--rm",
      "-e",
      "SMARTDOX_KROKI_PORT=9609",
      "-e",
      s"SMARTDOX_KROKI_DOCKER_IMAGE=${config.dockerImage}",
      "-v",
      s"${config.project.toString}:/workspace",
      "-w",
      s"/workspace/${workdir}",
      config.dockerImage,
      "antora",
      "antora-playbook.yml",
      "--to-dir",
      s"/workspace/${output}"
    )

  private[bok] def _smartdox_toolchain_env(config: BuildConfig): Map[String, String] =
    Map(
      "SMARTDOX_KROKI_DOCKER_IMAGE" -> config.dockerImage,
      "SMARTDOX_PDF_DOCKER_IMAGE" -> config.dockerImage,
      "SMARTDOX_COZY_TOOLCHAIN_IMAGE" -> config.dockerImage
    )

  private[bok] def _normalize_doxsite_output(config: BuildConfig): Unit =
    config.localeMode match {
      case LocaleMode.SingleLocaleRoot =>
        _single_locale_doxsite_dirs(config).foreach(_delete_directory)
      case LocaleMode.MultiLocaleSubdirs =>
        Unit
    }

  private def _single_locale_doxsite_dirs(config: BuildConfig): Vector[Path] =
    (config.languages ++ Vector("ja", "en")).distinct.map(config.doxsitePath.resolve)

  private def _copy_ui_bundle(config: BuildConfig, target: Path): Unit =
    {
      if (!Files.isRegularFile(config.uiBundlePath))
        _write_default_ui_bundle(config.uiBundlePath, ProjectFilePolicy.Default)
      if (Files.isRegularFile(config.uiBundlePath)) {
        Files.createDirectories(target)
        _copy_ui_bundle_with_cozy_assets(config, config.uiBundlePath, target.resolve("ui-bundle.zip"))
        _write_text(
          target.resolve("supplemental-ui/partials/header-content.hbs"),
          _default_ui_header(config)
        )
      }
    }

  private def _copy_ui_bundle_with_cozy_assets(config: BuildConfig, source: Path, target: Path): Unit = {
    val assets = Vector(
      "layouts/default.hbs" -> Left(_default_ui_layout()),
      "layouts/404.hbs" -> Left(_default_ui_layout()),
      "partials/header-content.hbs" -> Left(_default_ui_header(config)),
      "partials/nav.hbs" -> Left(_default_ui_nav()),
      "partials/nav-menu.hbs" -> Left(_default_ui_nav_menu()),
      "partials/nav-tree.hbs" -> Left(_default_ui_nav_tree()),
      "helpers/eq.js" -> Left(_default_ui_eq_helper()),
      "helpers/increment.js" -> Left(_default_ui_increment_helper()),
      "helpers/or.js" -> Left(_default_ui_or_helper()),
      "helpers/relativize.js" -> Left(_default_ui_relativize_helper()),
      "css/bootstrap-grid.min.css" -> Right("cozy/antora-ui/css/bootstrap-grid.min.css"),
      "css/cozy-bok-dashboard.css" -> Right("cozy/antora-ui/css/cozy-bok-dashboard.css")
    )
    val assetnames = assets.map(_._1).toSet
    val tmp = Files.createTempFile(Option(target.getParent).getOrElse(Paths.get(".")), "ui-bundle-", ".zip")
    try {
      val in = new ZipInputStream(Files.newInputStream(source))
      val out = new ZipOutputStream(Files.newOutputStream(tmp))
      try {
        var entry = in.getNextEntry
        while (entry != null) {
          if (!entry.isDirectory) {
            if (!assetnames.contains(entry.getName)) {
              out.putNextEntry(new ZipEntry(entry.getName))
              in.transferTo(out)
              out.closeEntry()
            }
          }
          in.closeEntry()
          entry = in.getNextEntry
        }
        assets.foreach {
          case (name, resource) =>
            resource match {
              case Left(text) =>
                _zip_text(out, name, text)
              case Right(path) =>
                _resource_bytes(path) match {
                  case Some(bytes) => _zip_bytes(out, name, bytes)
                  case None => RAISE.noReachDefect
                }
            }
        }
      } finally {
        out.close()
        in.close()
      }
      Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
    } finally {
      Files.deleteIfExists(tmp)
    }
  }

  private[bok] def _copy_directory(source: Path, dest: Path): Unit =
    if (Files.exists(source)) {
      val stream = Files.walk(source)
      try {
        stream.iterator.asScala.foreach { path =>
          val rel = source.relativize(path)
          val target = dest.resolve(rel)
          if (Files.isDirectory(path))
            Files.createDirectories(target)
          else {
            Option(target.getParent).foreach(Files.createDirectories(_))
            Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING)
          }
        }
      } finally {
        stream.close()
      }
    }

  // Preserve links so handoff validation sees the same trust boundary after target cleanup.
  private[bok] def _copy_sie_handoff_directory(source: Path, dest: Path): Unit =
    if (Files.exists(source)) {
      val stream = Files.walk(source)
      try {
        stream.iterator.asScala.foreach { path =>
          val rel = source.relativize(path)
          val target = dest.resolve(rel)
          if (Files.isSymbolicLink(path)) {
            Option(target.getParent).foreach(Files.createDirectories(_))
            Files.deleteIfExists(target)
            Files.createSymbolicLink(target, Files.readSymbolicLink(path))
          } else if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
            Files.createDirectories(target)
          else {
            Option(target.getParent).foreach(Files.createDirectories(_))
            Files.copy(path, target, LinkOption.NOFOLLOW_LINKS, StandardCopyOption.REPLACE_EXISTING)
          }
        }
      } finally {
        stream.close()
      }
    }

}
