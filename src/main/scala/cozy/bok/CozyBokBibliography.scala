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

private[cozy] trait CozyBokBibliography {
  self: CozyBokImplementation.type =>
  private[bok] def _stash_bibliography_cache(config: BuildConfig): Option[Path] = {
    val cache = config.project.resolve("target/cozy-bok/bibliography/cache")
    if (!Files.isDirectory(cache))
      None
    else {
      val tmp = Files.createTempDirectory("cozy-bibliography-cache")
      _copy_directory(cache, tmp.resolve("cache"))
      Some(tmp.resolve("cache"))
    }
  }

  private[bok] def _restore_bibliography_cache(config: BuildConfig, cache: Option[Path]): Unit =
    cache.foreach { source =>
      if (Files.isDirectory(source))
        _copy_directory(source, config.project.resolve("target/cozy-bok/bibliography/cache"))
    }

  private[bok] final case class PreservedSieHandoff(destination: Path, source: Path)

  private[bok] def _stash_sie_handoffs(config: BuildConfig): Vector[PreservedSieHandoff] = {
    val projectroot = config.project.toAbsolutePath.normalize
    val targetroot = projectroot.resolve("target").normalize
    val paths = _load_config(config.project).values.toVector.collect {
      case (key, value) if key.startsWith("bok.projects.") && key.endsWith(".sie.path") =>
        val path = Paths.get(value)
        if (path.isAbsolute) path.normalize else projectroot.resolve(path).normalize
    }.distinct.filter(path => path.startsWith(targetroot) && Files.isDirectory(path)).sortBy(_.toString)
    paths.map { path =>
      val tmp = Files.createTempDirectory("cozy-sie-handoff")
      val source = tmp.resolve("handoff")
      _copy_sie_handoff_directory(path, source)
      PreservedSieHandoff(path, source)
    }
  }

  private[bok] def _restore_sie_handoffs(handoffs: Vector[PreservedSieHandoff]): Unit =
    handoffs.foreach { handoff =>
      try {
        if (Files.isDirectory(handoff.source))
          _copy_sie_handoff_directory(handoff.source, handoff.destination)
      } finally {
        Option(handoff.source.getParent).foreach(_delete_directory)
      }
    }

  private[bok] def _write_effective_bibliography_metadata(config: BuildConfig): Unit =
    _bibliography_index(config).foreach { index =>
      val effective = _effective_bibliography_index(config, index)
      _write_text(config.doxsitePath.resolve("metadata/bibliography/bibliography.json"), effective.toJsonString)
    }

  private[bok] def _resolve_bibliography_service_entries(config: BuildConfig, fetcher: BibliographyBibtexFetcher): Unit =
    _bibliography_index(config).foreach { index =>
      val attempted = index.entries.flatMap(_ensure_bibliography_cache(config, _, fetcher)).distinct
      val effective = _effective_bibliography_index(config, index)
      val effectiveids = effective.entries.map(_.id).toSet
      val unresolved = effective.entries.filter(_.needsresolution).map(_.id)
      val missing = (attempted.filter(effectiveids.contains) ++ unresolved).distinct
      if (missing.nonEmpty) {
        val details = missing.map(x => s"unresolved bibliography reference: ${x}").mkString("\n")
        if (config.bibliographyService)
          RAISE.invalidArgumentFault(
            s"${details}\nRun: cozy bok update-bibliography ${config.project} --force, or build with --no-bib-service to keep unresolved references as warnings."
          )
        else
          println(s"warning: ${details}\nRun: cozy bok update-bibliography ${config.project}")
      }
    }

  private def _effective_bibliography_index(config: BuildConfig, index: BibliographyIndex): BibliographyIndex = {
    val resolved = index.entries.map(_resolve_cached_bibliography_entry(config, _))
    BibliographyIndex(_merge_bibliography_citation_aliases(resolved))
  }

  private def _merge_bibliography_citation_aliases(entries: Vector[BibliographyEntry]): Vector[BibliographyEntry] = {
    val aliases = entries.filter(_is_unresolved_bare_bibliography_citation)
    val targetpairs = aliases.flatMap { alias =>
      _bibliography_alias_target(alias, entries).map(_ -> alias)
    }
    val mappedaliases = targetpairs.map(_._2).toSet
    val aliasbytarget = targetpairs.groupBy(_._1).map {
      case (id, values) => id -> values.map(_._2)
    }
    entries.filterNot(mappedaliases.contains).map { entry =>
      aliasbytarget.get(entry.id).map(_merge_bibliography_aliases(entry, _)).getOrElse(entry)
    }
  }

  private def _is_unresolved_bare_bibliography_citation(entry: BibliographyEntry): Boolean =
    entry.needsresolution && entry.sourcekind == "external-ref" && _is_bare_bibliography_citation_key(entry.id)

  private[bok] def _is_bare_bibliography_citation_key(value: String): Boolean =
    !value.contains(":")

  private def _bibliography_alias_target(alias: BibliographyEntry, entries: Vector[BibliographyEntry]): Option[String] = {
    val candidates = entries.filter { entry =>
      entry.id != alias.id &&
        !entry.needsresolution &&
        _same_bibliography_reference_source(alias, entry)
    }
    candidates match {
      case Vector(entry) => Some(entry.id)
      case _ => None
    }
  }

  private def _same_bibliography_reference_source(a: BibliographyEntry, b: BibliographyEntry): Boolean = {
    val asources = _bibliography_reference_sources(a)
    val bsources = _bibliography_reference_sources(b)
    asources.nonEmpty && asources.exists(bsources.contains)
  }

  private def _bibliography_reference_sources(entry: BibliographyEntry): Set[(String, Option[String])] =
    if (entry.sourcerefs.nonEmpty)
      entry.sourcerefs.map(ref => ref.sourcepath -> ref.category).toSet
    else if (entry.sourcepath.nonEmpty && !entry.sourcepath.startsWith("bibliography/"))
      Set(entry.sourcepath -> entry.category)
    else
      Set.empty

  private def _merge_bibliography_aliases(entry: BibliographyEntry, aliases: Vector[BibliographyEntry]): BibliographyEntry = {
    val aliaskeys = aliases.map(_.id).filter(_is_bare_bibliography_citation_key)
    val key =
      if ((entry.key.isEmpty || entry.sourcekind == "external-cache") && aliaskeys.nonEmpty)
        Some(aliaskeys.head)
      else
        entry.key
    entry.copy(
      key = key,
      refs = _distinct_preserving_order(entry.refs ++ aliases.flatMap(alias => alias.refs ++ Vector(alias.id))),
      sourcerefs = _distinct_bibliography_source_refs(entry.sourcerefs ++ aliases.flatMap(_.sourcerefs))
    )
  }

  private[bok] def _distinct_preserving_order(values: Vector[String]): Vector[String] =
    values.foldLeft(Vector.empty[String]) { (acc, x) =>
      if (acc.contains(x)) acc else acc :+ x
    }

  private def _distinct_bibliography_source_refs(values: Vector[BibliographySourceRef]): Vector[BibliographySourceRef] =
    values.foldLeft(Vector.empty[BibliographySourceRef]) { (acc, x) =>
      if (acc.exists(y => y.sourcepath == x.sourcepath && y.publicpath == x.publicpath && y.category == x.category && y.citationkey == x.citationkey && y.ordinal == x.ordinal))
        acc
      else
        acc :+ x
    }

  private[bok] def _sync_effective_bibliography_rdf(config: BuildConfig): Unit =
    _bibliography_index(config).foreach { index =>
      val aliases = _bibliography_rdf_aliases(index)
      if (aliases.nonEmpty) {
        _sync_effective_bibliography_turtle(config.doxsitePath.resolve("site.ttl"), aliases)
        _sync_effective_bibliography_jsonld(config.doxsitePath.resolve("site.jsonld"), aliases)
      }
    }

}
