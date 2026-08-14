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

private[cozy] trait CozyBokFileSupport {
  self: CozyBokImplementation.type =>
  private[bok] def _html_escape(value: String): String =
    value.flatMap {
      case '&' => "&amp;"
      case '<' => "&lt;"
      case '>' => "&gt;"
      case '"' => "&quot;"
      case '\'' => "&#39;"
      case c => c.toString
    }

  private[bok] def _javascript_string(value: String): String =
    value.flatMap {
      case '\\' => "\\\\"
      case '\'' => "\\'"
      case '"' => "\\\""
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case c if c.isControl => f"\\u${c.toInt}%04x"
      case c => c.toString
    }

  private[bok] def _url_query_escape(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name)

  private[bok] def _clear_build_target(config: BuildConfig): Unit = {
    val projectroot = config.project.toAbsolutePath.normalize
    val targetroot = projectroot.resolve("target").normalize
    if (!targetroot.startsWith(projectroot))
      RAISE.invalidArgumentFault("BoK build target escapes its project root")
    if (Files.exists(targetroot, LinkOption.NOFOLLOW_LINKS)) {
      _require_direct_directory(targetroot, "BoK build target")
      val cozybok = targetroot.resolve("cozy-bok").normalize
      if (!cozybok.startsWith(targetroot))
        RAISE.invalidArgumentFault("BoK build target cache escapes its target root")
      _cleanup_build_target_children(targetroot, cozybok)
    }
  }

  private def _cleanup_build_target_children(targetroot: Path, cozybok: Path): Unit =
    _direct_children(targetroot, "BoK build target").foreach { child =>
      if (child == cozybok && Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(child))
        _cleanup_cozy_bok_target(child)
      else
        _delete_build_target_child(targetroot, child)
    }

  private def _cleanup_cozy_bok_target(cozybok: Path): Unit = {
    _require_direct_directory(cozybok, "BoK build target cache")
    val articlemedia = cozybok.resolve("article-media").normalize
    if (!articlemedia.startsWith(cozybok))
      RAISE.invalidArgumentFault("BoK article-media snapshot cache escapes its parent")
    _direct_children(cozybok, "BoK build target cache").foreach { child =>
      if (child == articlemedia && Files.exists(child, LinkOption.NOFOLLOW_LINKS)) {
        _require_direct_directory(child, "BoK article-media snapshot cache")
      } else {
        _delete_build_target_child(cozybok, child)
      }
    }
  }

  private def _direct_children(parent: Path, label: String): Vector[Path] = {
    _require_direct_directory(parent, label)
    val stream = Files.list(parent)
    try stream.iterator.asScala.toVector.map { child =>
      val normalized = child.toAbsolutePath.normalize
      if (!normalized.startsWith(parent) || normalized.getParent != parent)
        RAISE.invalidArgumentFault(s"$label child escapes its parent: $child")
      normalized
    }
    finally stream.close()
  }

  private def _require_direct_directory(path: Path, label: String): Unit =
    if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
      RAISE.invalidArgumentFault(s"$label must be a direct directory: $path")

  private def _delete_build_target_child(parent: Path, child: Path): Unit = {
    val normalized = child.toAbsolutePath.normalize
    if (!normalized.startsWith(parent) || normalized.getParent != parent)
      RAISE.invalidArgumentFault(s"BoK build target cleanup escapes its parent: $child")
    if (Files.isSymbolicLink(normalized))
      Files.deleteIfExists(normalized)
    else
      _delete_directory(normalized)
  }

  private[bok] def _delete_directory(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try {
        stream.iterator.asScala.toVector.reverse.foreach(Files.deleteIfExists)
      } finally {
        stream.close()
      }
    }

  private[bok] def _write(path: Path, content: String, policy: ProjectFilePolicy): Unit =
    policy match {
      case ProjectFilePolicy.Skip =>
        Unit
      case ProjectFilePolicy.Overwrite =>
        _write_text(path, content)
      case ProjectFilePolicy.Default =>
        if (!Files.exists(path))
          _write_text(path, content)
        else if (Files.readString(path, StandardCharsets.UTF_8) == content)
          Unit
        else
          _write_text(Paths.get(path.toString + ".bak"), content)
    }

  private[bok] def _write_text(path: Path, content: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, content, StandardCharsets.UTF_8)
  }

  private[bok] def _read_text(path: Path): String =
    Files.readString(path, StandardCharsets.UTF_8)

  private[bok] def _write_default_ui_bundle(path: Path, policy: ProjectFilePolicy): Unit =
    policy match {
      case ProjectFilePolicy.Skip =>
        Unit
      case ProjectFilePolicy.Overwrite =>
        _write_default_ui_bundle(path)
      case ProjectFilePolicy.Default =>
        if (!Files.exists(path))
          _write_default_ui_bundle(path)
    }

  private[bok] def _write_default_ui_bundle(path: Path): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    val out = new ZipOutputStream(Files.newOutputStream(path))
    try {
      _zip_text(out, "layouts/default.hbs", _default_ui_layout())
      _zip_text(out, "layouts/404.hbs", _default_ui_layout())
      _zip_text(out, "partials/header-content.hbs", _default_ui_header())
      _zip_text(out, "partials/nav.hbs", _default_ui_nav())
      _zip_text(out, "partials/nav-menu.hbs", _default_ui_nav_menu())
      _zip_text(out, "partials/nav-tree.hbs", _default_ui_nav_tree())
      _zip_text(out, "partials/footer-content.hbs", "")
      _zip_text(out, "helpers/eq.js", _default_ui_eq_helper())
      _zip_text(out, "helpers/increment.js", _default_ui_increment_helper())
      _zip_text(out, "helpers/or.js", _default_ui_or_helper())
      _zip_text(out, "helpers/relativize.js", _default_ui_relativize_helper())
      _zip_default_ui_assets(out)
    } finally {
      out.close()
    }
  }

  private[bok] def _zip_text(out: ZipOutputStream, name: String, content: String): Unit = {
    _zip_bytes(out, name, content.getBytes(StandardCharsets.UTF_8))
  }

  private[bok] def _zip_bytes(out: ZipOutputStream, name: String, content: Array[Byte]): Unit = {
    out.putNextEntry(new ZipEntry(name))
    out.write(content)
    out.closeEntry()
  }

  private def _zip_default_ui_assets(out: ZipOutputStream): Unit =
    _resource_text("cozy/antora-ui/manifest.txt") match {
      case Some(manifest) =>
        manifest.linesIterator.map(_.trim).filter(_.nonEmpty).foreach { name =>
          _resource_bytes(s"cozy/antora-ui/${name}") match {
            case Some(bytes) => _zip_bytes(out, name, bytes)
            case None => RAISE.noReachDefect
          }
        }
      case None =>
        _zip_text(out, "css/site.css", _default_ui_css())
        _zip_text(out, "js/site.js", "")
    }

  private[bok] def _resource_text(name: String): Option[String] =
    _resource_bytes(name).map(x => new String(x, StandardCharsets.UTF_8))

  private[bok] def _resource_bytes(name: String): Option[Array[Byte]] =
    Option(getClass.getClassLoader.getResourceAsStream(name)).map { in =>
      try {
        in.readAllBytes()
      } finally {
        in.close()
      }
    }

}
