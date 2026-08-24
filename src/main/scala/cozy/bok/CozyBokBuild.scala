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
 * @version Aug. 23, 2026
 * @author  ASAMI, Tomoharu
 */

private[cozy] trait CozyBokBuild {
  self: CozyBokImplementation.type =>
  def createCategory(config: CategoryConfig): Unit = {
    val dir = config.project.resolve("src/main/doxsite").resolve(config.name)
    _write(dir.resolve("category.yaml"), _category(_category_name(config.name), config.title, config.description, config.purpose), config.policy)
    _write(dir.resolve("index.dox"), _category_index(config.name, config.title, config.description, config.articles, config.terms), config.policy)
    config.articles.foreach { article =>
      _write(dir.resolve(article.fileName), _article(article.title, article.purpose), config.policy)
    }
    val glossarydir = config.project.resolve("src/main/doxsite/glossary").resolve(config.name)
    config.terms.foreach { term =>
      _write(glossarydir.resolve(term.fileName), _glossary(term.title, term.definition, term.reading), config.policy)
    }
  }

  def build(config: BuildConfig, runner: Runner): Unit =
    {
      val admittedconfig = _admit_build_config(config)
      _build_with_config(admittedconfig, runner, _bibliography_fetcher(admittedconfig.project, BibliographyHttpBibtexFetcher))
    }

  def build(config: BuildConfig, runner: Runner, bibliographyfetcher: BibliographyBibtexFetcher): Unit =
    {
      val admittedconfig = _admit_build_config(config)
      _build_with_config(admittedconfig, runner, bibliographyfetcher)
    }

  private def _admit_build_config(config: BuildConfig): BuildConfig = {
    val projectroot = _finalization_project_root(config.project)
    val admittedsource = _admit_finalization_source(config.sourcepath, projectroot)
    val admittedrelative = projectroot.toRealPath().relativize(admittedsource).toString
    config.copy(source = admittedrelative)
  }

  private def _build_with_config(
    config: BuildConfig,
    runner: Runner,
    bibliographyfetcher: BibliographyBibtexFetcher
  ): Unit =
    CozyArticleMediaBuildContext.withContext(
      config.project,
      config.publication.publicationPath(config.project),
      config.publication.repositoryPath(config.project),
      config.strategy
    ) { context =>
      _build(config.copy(strategy = context.strategy.name), context, runner, bibliographyfetcher)
    }

  private def _build(
    config: BuildConfig,
    context: CozyArticleMediaBuildContext.Context,
    runner: Runner,
    bibliographyfetcher: BibliographyBibtexFetcher
  ): Unit = {
    val bibliographycache = _stash_bibliography_cache(config)
    val siehandoffs = _stash_sie_handoffs(config)
    _clear_build_target(config)
    _restore_bibliography_cache(config, bibliographycache)
    _restore_sie_handoffs(siehandoffs)
    _delete_directory(config.project.resolve(s"doxsite-cache-${config.strategy}.d"))
    _delete_directory(config.doxsitePath)
    _delete_directory(config.antoraPath)
    _delete_directory(config.websitePath)
    if (config.arcadia.enabled)
      _delete_directory(config.arcadiaSitePath)
    runner.run(_dox_antora_command(config, context), config.project, _smartdox_toolchain_env(config))
    _run_antora(config, runner)
    runner.run(_dox_site_command(config, context), config.project, _smartdox_toolchain_env(config))
    _normalize_doxsite_output(config)
    _write_scenario_metadata(config)
    _ensure_bibliography_metadata_handoff(config)
    _write_effective_bibliography_metadata(config)
    _resolve_bibliography_service_entries(config, bibliographyfetcher)
    _write_effective_bibliography_metadata(config)
    _sync_effective_bibliography_fragments(config)
    _sync_effective_bibliography_rdf(config)
    _write_repository_car_metadata(config)
    _write_component_reference_metadata(config)
    _delete_directory(config.project.resolve(s"doxsite-cache-${config.strategy}.d"))
    if (config.arcadia.enabled) {
      runner.run(Vector("arcadia", "site", config.arcadia.source, config.arcadiaSite), config.project)
      _copy_directory(config.arcadiaSitePath, config.websitePath)
    }
    _write_bok_pages(config)
    if (config.strategy == "production") {
      runner.run(
        Vector("dox", "site-mark", "-strategy", "production", "-output.scope.policy", "all") ++
          _publication_options(config, context, includerdf = true) ++
          Vector(config.source),
        config.project
      )
      if (config.directAssets.enabled)
        config.directAssets.items.foreach { item =>
          _copy_directory(config.project.resolve(item.source), config.project.resolve(item.destination))
        }
    }
    finalizeMetadata(config)
  }

  private[bok] def _bibliography_fetcher(project: Path, fallback: BibliographyBibtexFetcher): BibliographyBibtexFetcher = {
    val config = _load_config(project)
    val repository = config.value("bok.repository").
      orElse(config.value("bok.warehouse").map(_repository_under_warehouse)).
      getOrElse("repository")
    new ProjectBibliographyBibtexFetcher(project.toAbsolutePath.normalize(), project.resolve(repository).toAbsolutePath.normalize(), fallback)
  }

  private class ProjectBibliographyBibtexFetcher(project: Path, repository: Path, fallback: BibliographyBibtexFetcher) extends BibliographyBibtexFetcher {
    def fetch(sourceurl: String): Option[String] =
      _local_source(sourceurl).orElse(if (_has_uri_scheme(sourceurl)) fallback.fetch(sourceurl) else None)

    override def fetchBibId(bibid: String): Option[String] =
      _local_bibid(bibid, None).orElse(fallback.fetchBibId(bibid))

    override def fetchEntry(entry: BibliographyEntry): Option[String] =
      entry.bibtex.sourceurl.flatMap(fetch).
        orElse(_local_bibid(entry.id, entry.category)).
        orElse(if (entry.needsresolution) fallback.fetchBibId(entry.id) else None)

    private def _local_source(sourceurl: String): Option[String] = {
      val candidates =
        if (sourceurl.startsWith("repository/bibliography/"))
          _local_source_candidate(repository.resolve("bibliography"), sourceurl.stripPrefix("repository/bibliography/"))
        else if (sourceurl.startsWith("repository/catalog/bibliography/"))
          _local_source_candidate(repository.resolve("catalog/bibliography"), sourceurl.stripPrefix("repository/catalog/bibliography/"))
        else if (sourceurl.startsWith("bibliography/"))
          _local_source_candidate(project.resolve("src/main/doxsite/bibliography"), sourceurl.stripPrefix("bibliography/"))
        else if (_has_uri_scheme(sourceurl))
          Vector.empty
        else
          Vector.empty
      candidates.map(_.toAbsolutePath.normalize()).find(Files.isRegularFile(_)).flatMap(_read_bibtex_file)
    }

    private def _local_source_candidate(root: Path, relative: String): Vector[Path] = {
      val normalizedroot = root.toAbsolutePath.normalize()
      val path = normalizedroot.resolve(relative).toAbsolutePath.normalize()
      val name = path.getFileName.toString.toLowerCase(Locale.ROOT)
      if (path.startsWith(normalizedroot) && name.endsWith(".bib"))
        Vector(path)
      else
        Vector.empty
    }

    private def _local_bibid(bibid: String, category: Option[String]): Option[String] =
      _bibtex_files(category).flatMap(_read_bibtex_entries).find(_matches_bibid(_, bibid))

    private def _bibtex_files(category: Option[String]): Vector[Path] =
      Vector(
        project.resolve("src/main/doxsite/bibliography"),
        repository.resolve("bibliography"),
        repository.resolve("catalog/bibliography")
      ).distinct.flatMap(_bibtex_files_in(_, category))

    private def _bibtex_files_in(root: Path, category: Option[String]): Vector[Path] =
      if (!Files.isDirectory(root))
        Vector.empty
      else
        category match {
          case Some(value) =>
            _bibtex_files_in_directory(root.resolve(value)) ++ _bibtex_files_in_directory(root)
          case None =>
            _bibtex_files_in_directory(root) ++ _category_bibtex_files_in(root)
        }

    private def _category_bibtex_files_in(root: Path): Vector[Path] = {
      val stream = Files.list(root)
      try {
        stream.iterator().asScala.toVector.
          filter(Files.isDirectory(_)).
          sortBy(_.getFileName.toString).
          flatMap(_bibtex_files_in_directory)
      } finally {
        stream.close()
      }
    }

    private def _bibtex_files_in_directory(dir: Path): Vector[Path] =
      if (!Files.isDirectory(dir))
        Vector.empty
      else {
        val stream = Files.list(dir)
        try {
          stream.iterator().asScala.toVector.
            filter(path => Files.isRegularFile(path) && path.getFileName.toString.toLowerCase(Locale.ROOT).endsWith(".bib")).
            sortBy(_.getFileName.toString)
        } finally {
          stream.close()
        }
      }

    private def _read_bibtex_file(path: Path): Option[String] =
      try {
        Some(Files.readString(path, StandardCharsets.UTF_8)).filter(_.trim.nonEmpty)
      } catch {
        case NonFatal(_) => None
      }

    private def _read_bibtex_entries(path: Path): Vector[String] =
      _read_bibtex_file(path).map(_split_bibtex_entries).getOrElse(Vector.empty)

    private def _split_bibtex_entries(text: String): Vector[String] = {
      var entries = Vector.empty[String]
      var i = 0
      while (i < text.length) {
        val start = text.indexOf('@', i)
        if (start < 0)
          i = text.length
        else {
          _entry_end(text, start) match {
            case Some(end) =>
              entries :+= text.substring(start, end)
              i = end
            case None =>
              i = text.length
          }
        }
      }
      entries
    }

    private def _entry_end(text: String, start: Int): Option[Int] = {
      val open = text.indexOf('{', start)
      if (open < 0)
        None
      else {
        var i = open + 1
        var depth = 1
        while (i < text.length && depth > 0) {
          text.charAt(i) match {
            case '{' => depth += 1
            case '}' => depth -= 1
            case _ =>
          }
          i += 1
        }
        if (depth == 0) Some(i) else None
      }
    }

    private def _matches_bibid(entry: String, bibid: String): Boolean =
      BibliographyBibtexParser.parse(entry).exists { fields =>
        val normalized = _normalize_bibid(bibid)
        val candidates = Vector(
          fields.get("id").map("bib:" + _),
          fields.get("doi").map("doi:" + _),
          fields.get("isbn").map("isbn:" + _),
          fields.get("url")
        ).flatten.map(_normalize_bibid)
        candidates.contains(normalized)
      }

    private def _normalize_bibid(value: String): String =
      value.trim.toLowerCase(Locale.ROOT)

    private def _has_uri_scheme(value: String): Boolean =
      "^[A-Za-z][A-Za-z0-9+.-]*:.*$".r.pattern.matcher(value).matches()
  }

  def preview(args: List[String], runner: Runner): Unit = {
    if (args.exists(x => x == "--help" || x == "-h")) {
      println("Usage: cozy bok preview [<project-dir>] [--port <port>]")
      println("Serve generated website.d through a local Web server for browser preview.")
      return
    }
    val previewconfig = PreviewConfig.create(args)
    val project = _resolve_bok_project(previewconfig.input)
    val config = _load_config(project)
    val port = previewconfig.port.map(_.toString).
      orElse(config.value("bok.preview.port")).
      getOrElse(_default_preview_port)
    val website = config.value("bok.website").getOrElse("website.d")
    val websitepath = project.resolve(website).toAbsolutePath.normalize
    println(s"Serving ${websitepath} at http://127.0.0.1:${port}/")
    println("Use this local Web server instead of opening generated HTML files directly.")
    runner.run(Vector("python3", "-m", "http.server", port), websitepath)
  }

  def runWorkflow(config: WorkflowConfig, runner: Runner): Unit =
    if (_require_workflow(config)) {
      config.backup.foreach(_backup_website)
      runner.run(config.command, config.project, config.env)
    }

  private[bok] def _require_workflow(config: WorkflowConfig): Boolean =
    if (config.command.isEmpty)
      RAISE.invalidArgumentFault(
        s"Missing bok workflow command: bok.workflow.${config.name}.command\n" +
          s"""Example:
             |bok:
             |  workflow:
             |    ${config.name}:
             |      command: "etc/website-${config.name}.sh"
             |""".stripMargin
      )
    else
      true

  private def _backup_website(config: WebsiteBackupConfig): Unit = {
    _validate_website_backup_config(config)
    if (!Files.isDirectory(config.source))
      RAISE.invalidArgumentFault(s"Website backup source directory is missing: ${config.source}")
    val now = LocalDateTime.now()
    val snapshotdir = _website_backup_snapshot_dir(config.root, now)
    Files.createDirectories(snapshotdir)
    val sourcename = config.source.getFileName.toString
    if (config.compressed)
      _zip_directory(config.source, snapshotdir.resolve(s"${sourcename}.zip"), sourcename)
    else
      _copy_directory(config.source, snapshotdir.resolve(sourcename))
    _rotate_website_backups(config.root, YearMonth.from(now))
  }

  private def _validate_website_backup_config(config: WebsiteBackupConfig): Unit =
    if (config.root == config.source || config.root.startsWith(config.source))
      RAISE.invalidArgumentFault(
        s"Website backup directory must be outside the website source directory: backup=${config.root}, source=${config.source}"
      )

  private def _website_backup_snapshot_dir(root: Path, now: LocalDateTime): Path = {
    val basedir = root.
      resolve(f"${now.getYear}%04d").
      resolve(f"${now.getMonthValue}%02d").
      resolve(f"${now.getDayOfMonth}%02d")
    val basename = now.format(DateTimeFormatter.ofPattern("HHmmss"))
    Iterator.from(0).map { index =>
      val name = if (index == 0) basename else f"${basename}-${index}%02d"
      basedir.resolve(name)
    }.find(path => !Files.exists(path)).get
  }

  private def _zip_directory(source: Path, zip: Path, rootname: String): Unit = {
    Option(zip.getParent).foreach(Files.createDirectories(_))
    val out = new ZipOutputStream(Files.newOutputStream(zip))
    try {
      val stream = Files.walk(source)
      try {
        stream.iterator.asScala.toVector.sortBy(_.toString).filter(Files.isRegularFile(_)).foreach { path =>
          val rel = source.relativize(path).toString.replace(java.io.File.separatorChar, '/')
          out.putNextEntry(new ZipEntry(s"${rootname}/${rel}"))
          Files.copy(path, out)
          out.closeEntry()
        }
      } finally {
        stream.close()
      }
    } finally {
      out.close()
    }
  }

  private def _rotate_website_backups(root: Path, current: YearMonth): Unit = {
    val snapshots = _website_backup_snapshots(root)
    val first = snapshots.headOption.map(_.path).toSet
    val monthlylast = snapshots.groupBy(_.yearmonth).collect {
      case (yearmonth, xs) if yearmonth != current =>
        xs.last.path
    }.toSet
    val currentmonth = snapshots.filter(_.yearmonth == current).map(_.path).toSet
    val keep = first ++ monthlylast ++ currentmonth
    snapshots.map(_.path).filterNot(keep.contains).foreach(_delete_directory)
  }

  private def _website_backup_snapshots(root: Path): Vector[WebsiteBackupSnapshot] =
    if (!Files.isDirectory(root))
      Vector.empty
    else
      _directory_children(root).flatMap { yearpath =>
        val year = yearpath.getFileName.toString
        if (!year.matches("\\d{4}"))
          Vector.empty
        else
          _directory_children(yearpath).flatMap { monthpath =>
            val month = monthpath.getFileName.toString
            if (!month.matches("\\d{2}"))
              Vector.empty
            else
              _directory_children(monthpath).flatMap { daypath =>
                val day = daypath.getFileName.toString
                if (!day.matches("\\d{2}"))
                  Vector.empty
                else
                  _directory_children(daypath).filter(_is_website_backup_snapshot).map { snapshot =>
                    val yearmonth = YearMonth.of(year.toInt, month.toInt)
                    WebsiteBackupSnapshot(snapshot, yearmonth, s"${year}${month}${day}${snapshot.getFileName}")
                  }
              }
          }
      }.sortBy(_.order)

  private def _directory_children(path: Path): Vector[Path] =
    if (!Files.isDirectory(path))
      Vector.empty
    else {
      val stream = Files.list(path)
      try {
        stream.iterator.asScala.toVector.filter(Files.isDirectory(_)).sortBy(_.toString)
      } finally {
        stream.close()
      }
    }

  private def _is_website_backup_snapshot(path: Path): Boolean =
    Files.isDirectory(path) && (
      Files.exists(path.resolve("website.d")) ||
      Files.exists(path.resolve("website.d.zip"))
    )

}
