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

private[cozy] trait CozyBokProjectResolution {
  self: CozyBokImplementation.type =>
  private[bok] def _project(parsed: ParsedArgs): Path =
    _resolve_bok_project(parsed.pathProperty("project-dir").
      orElse(parsed.pathProperty("project")).
      orElse(parsed.argument("project").map(_to_path)).
      getOrElse(_logical_cwd))

  private[bok] def _category_project(parsed: ParsedArgs): Path =
    _resolve_bok_project(parsed.pathProperty("project-dir").
      orElse(parsed.pathProperty("project")).
      getOrElse(_logical_cwd))

  private[bok] def _resolve_bok_project(input: Path): Path =
    _find_bok_root(input).getOrElse(input.toAbsolutePath.normalize)

  private[bok] def _find_bok_root(input: Path): Option[Path] = {
    val start = _existing_directory(input.toAbsolutePath.normalize)
    Iterator.iterate(Option(start))(_.flatMap(x => Option(x.getParent))).
      takeWhile(_.nonEmpty).
      flatten.
      find(_is_bok_root)
  }

  private def _existing_directory(path: Path): Path =
    if (Files.isRegularFile(path))
      Option(path.getParent).getOrElse(path)
    else
      path

  private def _is_bok_root(path: Path): Boolean =
    Files.isDirectory(path.resolve("src/main/doxsite")) ||
      Files.isRegularFile(path.resolve("src/main/doxsite/site.conf")) ||
      _has_bok_config(path)

  private def _has_bok_config(path: Path): Boolean =
    CozyProjectYamlConfig.operationDefaultFiles(path).filter(x => Files.isRegularFile(x)).exists { file =>
      val content = _read_text(file)
      content.contains("bok:") || content.contains("\"bok\"") || content.contains("bok.")
    }

  private[bok] def _load_config(project: Path): CozyProjectYamlConfig.Config =
    CozyProjectYamlConfig.loadOperationDefaults(project)

  private[bok] def _load_site_config(source: Path): SiteConfig = {
    val file = source.resolve("site.conf")
    if (!Files.isRegularFile(file))
      SiteConfig.empty
    else {
      val lines = Files.readAllLines(file, StandardCharsets.UTF_8).asScala.toVector
      SiteConfig(_parse_site_values(lines), _parse_site_lists(lines), _parse_site_goal_trees(lines))
    }
  }

  private[bok] def _to_path(value: Any): Path = value match {
    case m: java.io.File => m.toPath.toAbsolutePath.normalize
    case m: Path => m.toAbsolutePath.normalize
    case m =>
      val path = Paths.get(m.toString)
      if (path.isAbsolute)
        path.normalize
      else
        _logical_cwd.resolve(path).normalize
  }

  private[bok] def _logical_cwd: Path =
    sys.env.get("PWD").map(Paths.get(_).toAbsolutePath.normalize).getOrElse(Paths.get(".").toAbsolutePath.normalize)

  private[bok] def _boolean(config: CozyProjectYamlConfig.Config, path: String, default: Boolean): Boolean =
    config.boolean(path).getOrElse(default)

  private[bok] def _strategy(parsed: ParsedArgs): String =
    _strategy(parsed, "wip")

  private[bok] def _strategy(parsed: ParsedArgs, default: String): String =
    CozyArticleMediaBuildContext.normalizeStrategy(
      parsed.property("strategy").getOrElse(default)
    ).name

  private[bok] def _publication_settings(
    parsed: ParsedArgs,
    config: CozyProjectYamlConfig.Config,
    strategy: String
  ): PublicationSettings = {
    val publication = parsed.pathProperty("publication").
      map(_.toString).
      orElse(config.value("bok.publication")).
      getOrElse("src/main/publication")
    val (warehouse, repository) = _publication_artifact_roots(parsed, config)
    val merge = _boolean(config, "bok.rdf.merge-publication-artifacts", true)
    val defaultpolicy = if (strategy == "production") "fail" else "warn"
    val missingpolicy = parsed.property("rdf-missing-artifact-policy").
      orElse(config.value("bok.rdf.missing-artifact-policy")).
      getOrElse(defaultpolicy)
    PublicationSettings(publication, warehouse, repository, merge, missingpolicy)
  }

  private[bok] def _publication_artifact_roots(
    parsed: ParsedArgs,
    config: CozyProjectYamlConfig.Config
  ): (Option[String], String) = {
    val clirepository = parsed.pathProperty("repository").map(_.toString)
    val cliwarehouse = parsed.pathProperty("warehouse").map(_.toString)
    val configrepository = config.value("bok.repository")
    val configwarehouse = config.value("bok.warehouse")
    val warehouse = if (clirepository.isDefined || configrepository.isDefined)
      cliwarehouse
    else
      cliwarehouse.orElse(configwarehouse)
    val repository = clirepository.
      orElse(cliwarehouse.map(_repository_under_warehouse)).
      orElse(configrepository).
      orElse(configwarehouse.map(_repository_under_warehouse)).
      getOrElse("repository")
    warehouse -> repository
  }

  private[bok] def _repository_under_warehouse(warehouse: String): String =
    Paths.get(warehouse).resolve("repository").toString

  private[bok] val _generated_gitignore_entries = Vector(
    "/target/",
    "/website.d/",
    "/doxsite.d/",
    "/antora.d/",
    "/repository/",
    "/repository.d/",
    "/.bsp/",
    "/.metals/",
    "/.idea/"
  )
  private[bok] val _dox_metadata_section_names = Set("HEAD", "HEADLINE", "BRIEF", "SUMMARY", "DESCRIPTION", "LEAD", "ABSTRACT", "REMARKS", "TOOLTIP")

}
