package cozy.bok

import org.goldenport.RAISE
import org.goldenport.cli.{Request => CliRequest}
import org.goldenport.cli.spec
import cozy.bok.scenario.ScenarioMetadata
import cozy.bok.BibliographyEntry._
import cozy.config.CozyProjectYamlConfig
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
import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import java.util.zip.{ZipEntry, ZipFile, ZipInputStream, ZipOutputStream}
import scala.collection.JavaConverters._
import scala.util.matching.Regex
import scala.util.control.NonFatal
import scala.sys.process._
import io.circe.{Decoder, HCursor, Json}
import io.circe.parser
import io.circe.syntax._

/*
 * @since   Jun.  3, 2026
 *  version Jun. 28, 2026
 * @version Jul. 13, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyBok {
  private val _default_docker_image = "ghcr.io/asami/textus-toolchain:latest"
  private val _ui_resource_base = "cozy.bok.BokUi"
  private val _ui_resource_config = I18NContext.ResourceBundleConfig.englishFallback

  sealed trait LocaleMode
  object LocaleMode {
    case object MultiLocaleSubdirs extends LocaleMode
    case object SingleLocaleRoot extends LocaleMode

    def create(value: String): LocaleMode =
      value match {
        case "multi_locale_subdirs" | "multi-locale-subdirs" => MultiLocaleSubdirs
        case "single_locale_root" | "single-locale-root" => SingleLocaleRoot
        case other => RAISE.invalidArgumentFault(s"Unsupported bok locale mode: ${other}")
      }
  }

  final case class CreateConfig(
    save: Path,
    name: String,
    url: String,
    language: String,
    policy: ProjectFilePolicy
  )
  final case class CategoryConfig(
    project: Path,
    name: String,
    title: String,
    description: String,
    purpose: BokPurpose,
    articles: Vector[CategoryArticle],
    terms: Vector[CategoryTerm],
    policy: ProjectFilePolicy
  )
  final case class BokGoal(title: String, subgoals: Vector[String]) {
    def isEmpty: Boolean = title.trim.isEmpty && subgoals.isEmpty
  }
  final case class BokPurpose(
    vision: Option[String],
    goals: Vector[BokGoal],
    flatGoals: Vector[String] = Vector.empty,
    flatSubgoals: Vector[String] = Vector.empty
  ) {
    def isEmpty: Boolean = vision.isEmpty && goals.isEmpty && flatGoals.isEmpty && flatSubgoals.isEmpty
  }
  object BokPurpose {
    val empty: BokPurpose = BokPurpose(None, Vector.empty)
  }
  final case class CategoryArticle(slug: String, title: String, purpose: String) {
    def fileName: String = s"${slug}.dox"
    def htmlName: String = s"${slug}.html"
  }
  final case class CategoryTerm(path: String, title: String, definition: String, reading: Option[String] = None) {
    def termPath: String = path.stripPrefix("glossary/").stripPrefix("/")
    def fileName: String = s"${termPath}.dox"
    def htmlName(category: String): String = s"../glossary/${category}/${termPath}.html"
  }
  final case class DoctorConfig(input: Path, fix: Boolean, dryRun: Boolean)
  final case class PreviewConfig(input: Path, port: Option[Int])
  private final case class BokInspection(
    input: Path,
    root: Option[Path],
    markers: Vector[String],
    issues: Vector[String],
    fixes: Vector[BokFix]
  ) {
    def status: String =
      if (root.isEmpty)
        "not-found"
      else if (issues.isEmpty)
        "ok"
      else
        "needs-fix"
  }
  private final case class BokFix(description: String, apply: () => Unit)
  private final case class DoxMetadataSectionIssue(path: Path, line: Int, heading: String)
  private final case class MarkdownMetadataIssue(path: Path, message: String)
  private final case class CategoryContent(
    slug: String,
    title: String,
    description: String,
    purpose: BokPurpose,
    articles: Vector[CategoryPageItem],
    terms: Vector[CategoryPageItem]
  )
  private final case class CategoryPageItem(
    href: String,
    title: String,
    brief: String,
    modifiedAtMillis: Long,
    reading: Option[String] = None
  )
  private final case class LocalizedGlossaryItem(
    href: String,
    title: String,
    reading: Option[String],
    categorySlug: String,
    categoryTitle: String
  )
  private final case class ArticleIndexItem(
    categorySlug: String,
    categoryTitle: String,
    hrefFromArticleIndex: String,
    title: String,
    brief: String,
    termExtraction: TermExtraction
  )
  private final case class DashboardCounts(
    categoryCount: Int,
    articleCount: Int,
    glossaryTermCount: Int,
    totalItemCount: Int
  )
  private final case class DashboardBucket(
    label: String,
    startDate: String,
    endDate: String,
    count: Int,
    articleCount: Int,
    glossaryTermCount: Int,
    hasBreakdown: Boolean
  )
  private final case class DashboardRdfSummary(
    resourceCount: Int,
    tripleCount: Int,
    subjectCount: Int,
    predicateCount: Int
  )
  private final case class DashboardIncrements(
    scale: String,
    buckets: Vector[DashboardBucket]
  )
  private final case class DashboardRecentItem(
    href: String,
    title: String,
    category: Option[String],
    kindkey: String,
    modifiedatmillis: Long
  )
  private final case class DashboardCategory(
    name: String,
    title: String,
    counts: DashboardCounts,
    increments: DashboardIncrements,
    rdf: Option[DashboardRdfSummary]
  )
  private final case class BokDashboard(
    counts: DashboardCounts,
    rdf: DashboardRdfSummary,
    increments: DashboardIncrements,
    categories: Vector[DashboardCategory]
  )

  private final case class ScenarioIndex(scenarios: Vector[ScenarioEntry])
  private final case class ScenarioEntry(
    id: String,
    slug: String,
    scenarioType: String,
    title: String,
    summary: Option[String],
    category: Option[String],
    sourcepath: String,
    publicpath: String,
    terms: Vector[String],
    tags: Vector[String],
    status: Option[String],
    termExtraction: TermExtraction
  ) {
    def categorySlug: String = category.getOrElse("scenario")
    def hrefFromHome: String = publicpath
    def hrefFromCategory: String = "../" + publicpath
    def isRelatedTo(term: TermEntry): Boolean =
      terms.exists(x => x == term.id || x == term.title || x == term.slug)
  }

  private final case class TermIndex(terms: Vector[TermEntry])
  private final case class TermEntry(
    id: String,
    slug: String,
    title: String,
    reading: Option[String],
    category: Option[String],
    sourcepath: String,
    publicpath: String,
    definitionHtml: String,
    summary: Option[String],
    aliases: Vector[String],
    articleRefs: Vector[TermReference],
    termRefs: Vector[TermReference],
    rdfRefs: Vector[TermRdfReference],
    videoRefs: Vector[TermReference],
    termType: String,
    resourceType: Option[String],
    cml: Vector[TermCmlLink],
    event: Option[TermEvent],
    actor: Option[TermActor],
    role: Option[TermRole],
    quality: TermQuality,
    tags: Vector[String]
  ) {
    def categorySlug: String = category.getOrElse("glossary")
    def glossaryHref: String = publicpath.stripPrefix("glossary/")
    def termHubHrefFromHome: String = publicpath
    def termHubHrefFromCategory: String = "../" + publicpath
    def rdfHrefFromHome: String = s"rdf/index.html?term=${_url_query_escape(id)}"
    def rdfHrefFromGlossary: String = s"../rdf/index.html?term=${_url_query_escape(id)}"
    def rdfHrefFromCategory: String = s"../rdf/index.html?term=${_url_query_escape(id)}"
    def rdfHrefFromTerm: String = s"../../rdf/index.html?term=${_url_query_escape(id)}"
    def monoKotoKind: String =
      termType match {
        case "event" => "koto"
        case "rule" => "rule"
        case _ => "mono"
      }
    def cmlLinks: Vector[TermCmlLink] = (cml ++ event.toVector.flatMap(_.cmlLinks)).distinct
  }
  private final case class TermReference(title: String, path: String, relation: String)
  private final case class TermRdfReference(resource: String, label: String, predicate: Option[String], direction: String)
  private final case class TermCmlLink(kind: String, value: String)
  private final case class TermEvent(
    occurredAt: Option[String],
    startAt: Option[String],
    endAt: Option[String],
    location: Option[String],
    actors: Vector[String],
    roles: Vector[String],
    participants: Vector[String],
    scenarios: Vector[String],
    evidence: Vector[String],
    cmlEvent: Option[String],
    cmlComponent: Option[String],
    cmlStatemachine: Option[String]
  ) {
    def cmlLinks: Vector[TermCmlLink] =
      Vector(
        cmlComponent.map(TermCmlLink("component", _)),
        cmlEvent.map(TermCmlLink("event", _)),
        cmlStatemachine.map(TermCmlLink("statemachine", _))
      ).flatten
  }
  private final case class TermActor(roles: Vector[String], organization: Option[String], description: Option[String])
  private final case class TermRole(actors: Vector[String], responsibilities: Vector[String], permissions: Vector[String])
  private final case class TermQuality(isolated: Boolean, unreferenced: Boolean, weaklyconnected: Boolean)
  private final case class TermExtraction(status: Option[String], planned: Option[Boolean]) {
    def isPlanned: Boolean =
      planned.getOrElse(true) && !status.map(_normalize_status).exists(x => x == "skipped" || x == "out-of-scope")
    def isExtracted: Boolean =
      status.map(_normalize_status).exists(x => x == "extracted" || x == "done" || x == "complete" || x == "completed")
    private def _normalize_status(value: String): String =
      value.trim.toLowerCase.replace('_', '-')
  }

  private object TermExtraction {
    val empty: TermExtraction = TermExtraction(None, None)
  }

  private def _decode_term_extraction(c: HCursor): Decoder.Result[TermExtraction] =
    for {
      obj <- c.downField("workflow").downField("term").as[Option[TermExtraction]]
    } yield obj.getOrElse(TermExtraction.empty)

  private implicit val _term_extraction_decoder: Decoder[TermExtraction] = (c: HCursor) =>
    for {
      status <- c.downField("status").as[Option[String]]
      planned <- c.downField("planned").as[Option[Boolean]]
    } yield TermExtraction(status, planned)

  private final case class DocumentFragmentIndex(fragments: Vector[DocumentFragment]) {
    def get(sourcepath: String, locale: String): Option[DocumentFragment] =
      fragments.find(x => x.sourcepath == sourcepath && x.locale == locale)
  }
  private final case class DocumentFragment(
    sourcepath: String,
    publicpath: String,
    locale: String,
    kind: Option[String],
    category: Option[String],
    title: Option[String],
    headline: Option[String],
    brief: Option[String],
    summary: Option[String],
    description: Option[String],
    bodyhtml: String,
    tags: Vector[String],
    termExtraction: TermExtraction
  ) {
    def effectiveHeadline: Option[String] = headline.orElse(title)
    def effectiveBrief: Option[String] = brief.orElse(summary).orElse(description)
  }

  private implicit val _document_fragment_decoder: Decoder[DocumentFragment] = (c: HCursor) =>
    for {
      sourcepath <- c.downField("source_path").as[String]
      publicpath <- c.downField("public_path").as[String]
      locale <- c.downField("locale").as[String]
      kind <- c.downField("kind").as[Option[String]]
      category <- c.downField("category").as[Option[String]]
      title <- c.downField("title").as[Option[String]]
      headline <- c.downField("headline").as[Option[String]]
      brief <- c.downField("brief").as[Option[String]]
      summary <- c.downField("summary").as[Option[String]]
      description <- c.downField("description").as[Option[String]]
      bodyhtml <- c.downField("body_html").as[String]
      tags <- c.downField("tags").as[Option[Vector[String]]]
      termextraction <- _decode_term_extraction(c)
    } yield DocumentFragment(sourcepath, publicpath, locale, kind, category, title, headline, brief, summary, description, bodyhtml, tags.getOrElse(Vector.empty), termextraction)

  private implicit val _document_fragment_index_decoder: Decoder[DocumentFragmentIndex] = (c: HCursor) =>
    for {
      fragments <- c.downField("fragments").as[Option[Vector[DocumentFragment]]]
    } yield DocumentFragmentIndex(fragments.getOrElse(Vector.empty))

  private final case class TagIndex(tags: Vector[TagEntry]) {
    def isEmpty: Boolean = tags.isEmpty
    def forCategory(category: String): TagIndex =
      TagIndex(tags.flatMap { tag =>
        val refs = tag.refs.filter(_.category.contains(category))
        if (refs.isEmpty) None else Some(tag.copy(refs = refs))
      })
    def namespaces: Vector[String] =
      tags.flatMap(_.namespace).distinct.sorted
  }
  private final case class TagEntry(
    id: String,
    key: String,
    segments: Vector[String],
    namespace: Option[String],
    parent: Option[String],
    slug: String,
    label: String,
    title: Option[String],
    summary: Option[String],
    aliases: Vector[String],
    locale: Option[String],
    sourcepath: Option[String],
    publicpath: String,
    bodyhtml: Option[String],
    refs: Vector[TagReference],
    children: Vector[String]
  ) {
    def count: Int = refs.size
    def effectiveTitle: String = title.getOrElse(key)
  }
  private final case class TagReference(kind: String, title: String, href: String, category: Option[String])

  private implicit val _tag_reference_decoder: Decoder[TagReference] = (c: HCursor) =>
    for {
      kind <- c.downField("kind").as[Option[String]]
      title <- c.downField("title").as[Option[String]]
      href <- c.downField("public_path").as[Option[String]]
      legacyhref <- c.downField("href").as[Option[String]]
      category <- c.downField("category").as[Option[String]]
    } yield TagReference(kind.getOrElse("document"), title.getOrElse(href.orElse(legacyhref).getOrElse("Untitled")), href.orElse(legacyhref).getOrElse("#"), category)

  private implicit val _tag_entry_decoder: Decoder[TagEntry] = (c: HCursor) =>
    for {
      key0 <- c.downField("key").as[Option[String]]
      id0 <- c.downField("id").as[Option[String]]
      segments0 <- c.downField("segments").as[Option[Vector[String]]]
      namespace <- c.downField("namespace").as[Option[String]]
      parent <- c.downField("parent").as[Option[String]]
      slug0 <- c.downField("slug").as[Option[String]]
      label <- c.downField("label").as[Option[String]]
      title <- c.downField("title").as[Option[String]]
      summary <- c.downField("summary").as[Option[String]]
      aliases <- c.downField("aliases").as[Option[Vector[String]]]
      locale <- c.downField("locale").as[Option[String]]
      sourcepath <- c.downField("source_path").as[Option[String]]
      publicpath0 <- c.downField("public_path").as[Option[String]]
      bodyhtml <- c.downField("body_html").as[Option[String]]
      refs <- c.downField("refs").as[Option[Vector[TagReference]]]
      children <- c.downField("children").as[Option[Vector[String]]]
    } yield {
      val rawkey = key0.orElse(id0.map(_.stripPrefix("tag:"))).getOrElse(label.getOrElse("tag"))
      val key = _tag_key(rawkey)
      val segments = segments0.map(_.map(_tag_segment).filter(_.nonEmpty)).filter(_.nonEmpty).getOrElse(key.split('.').toVector)
      val slug = slug0.map(_tag_slug_path).getOrElse(segments.mkString("/"))
      TagEntry(
        id0.getOrElse(s"tag:${key}"),
        key,
        segments,
        namespace.orElse(segments.headOption),
        parent.orElse(if (segments.size > 1) Some(s"tag:${segments.dropRight(1).mkString(".")}") else None),
        slug,
        label.getOrElse(segments.lastOption.getOrElse(key)),
        title,
        summary,
        aliases.getOrElse(Vector.empty),
        locale,
        sourcepath,
        publicpath0.getOrElse(s"tags/${slug}.html"),
        bodyhtml,
        refs.getOrElse(Vector.empty),
        children.getOrElse(Vector.empty)
      )
    }

  private implicit val _tag_index_decoder: Decoder[TagIndex] = (c: HCursor) =>
    for {
      tags <- c.downField("tags").as[Option[Vector[TagEntry]]]
    } yield TagIndex(tags.getOrElse(Vector.empty))

  private implicit val _dashboard_counts_decoder: Decoder[DashboardCounts] = (c: HCursor) =>
    for {
      categorycount <- c.downField("category_count").as[Int]
      articlecount <- c.downField("article_count").as[Int]
      glossarytermcount <- c.downField("glossary_term_count").as[Int]
      totalitemcount <- c.downField("total_item_count").as[Int]
    } yield DashboardCounts(categorycount, articlecount, glossarytermcount, totalitemcount)

  private implicit val _dashboard_bucket_decoder: Decoder[DashboardBucket] = (c: HCursor) =>
    for {
      label <- c.downField("label").as[String]
      startdate <- c.downField("start_date").as[String]
      enddate <- c.downField("end_date").as[String]
      count <- c.downField("count").as[Int]
      articlecount <- c.downField("article_count").as[Option[Int]]
      glossarytermcount <- c.downField("glossary_term_count").as[Option[Int]]
    } yield DashboardBucket(
      label,
      startdate,
      enddate,
      count,
      articlecount.getOrElse(0),
      glossarytermcount.getOrElse(0),
      articlecount.isDefined || glossarytermcount.isDefined
    )

  private implicit val _dashboard_rdf_summary_decoder: Decoder[DashboardRdfSummary] = (c: HCursor) =>
    for {
      resourcecount <- c.downField("resource_count").as[Int]
      triplecount <- c.downField("triple_count").as[Int]
      subjectcount <- c.downField("subject_count").as[Int]
      predicatecount <- c.downField("predicate_count").as[Int]
    } yield DashboardRdfSummary(resourcecount, triplecount, subjectcount, predicatecount)

  private implicit val _dashboard_increments_decoder: Decoder[DashboardIncrements] = (c: HCursor) =>
    for {
      scale <- c.downField("scale").as[String]
      buckets <- c.downField("buckets").as[Vector[DashboardBucket]]
    } yield DashboardIncrements(scale, buckets)

  private implicit val _dashboard_category_decoder: Decoder[DashboardCategory] = (c: HCursor) =>
    for {
      name <- c.downField("name").as[String]
      title <- c.downField("title").as[String]
      counts <- c.downField("counts").as[DashboardCounts]
      increments <- c.downField("increments").as[DashboardIncrements]
      rdf <- c.downField("rdf").as[Option[DashboardRdfSummary]]
    } yield DashboardCategory(name, title, counts, increments, rdf)

  private implicit val _bok_dashboard_decoder: Decoder[BokDashboard] = (c: HCursor) =>
    for {
      counts <- c.downField("counts").as[DashboardCounts]
      rdf <- c.downField("rdf").as[DashboardRdfSummary]
      increments <- c.downField("increments").as[DashboardIncrements]
      categories <- c.downField("categories").as[Vector[DashboardCategory]]
    } yield BokDashboard(counts, rdf, increments, categories)

  private implicit val _term_reference_decoder: Decoder[TermReference] = (c: HCursor) =>
    for {
      title <- c.downField("title").as[String]
      path <- c.downField("path").as[String]
      relation <- c.downField("relation").as[Option[String]]
    } yield TermReference(title, path, relation.getOrElse("related"))

  private implicit val _term_rdf_reference_decoder: Decoder[TermRdfReference] = (c: HCursor) =>
    for {
      resource <- c.downField("resource").as[String]
      label <- c.downField("label").as[String]
      predicate <- c.downField("predicate").as[Option[String]]
      direction <- c.downField("direction").as[Option[String]]
    } yield TermRdfReference(resource, label, predicate, direction.getOrElse("node"))

  private val _term_cml_short_keys: Vector[String] =
    Vector("entity", "value", "powertype", "statemachine", "rule", "event", "operation", "component", "service")

  private def _decode_term_cml_links(json: Option[Json]): Vector[TermCmlLink] =
    json.toVector.flatMap(_decode_term_cml_links)

  private def _decode_term_cml_links(json: Json): Vector[TermCmlLink] =
    json.asArray.map(_.toVector.flatMap(_decode_term_cml_links)).getOrElse {
      val cursor = json.hcursor
      val kind = cursor.downField("kind").as[Option[String]].getOrElse(None)
      val value = Vector(
        cursor.downField("name").as[Option[String]].getOrElse(None),
        cursor.downField("element_ref").as[Option[String]].getOrElse(None),
        cursor.downField("value").as[Option[String]].getOrElse(None)
      ).flatten.headOption
      val direct = (kind, value) match {
        case (Some(k), Some(v)) => Vector(TermCmlLink(k, v))
        case _ => Vector.empty
      }
      val shorthand = _term_cml_short_keys.flatMap { key =>
        cursor.downField(key).as[Option[String]].getOrElse(None).map(TermCmlLink(key, _))
      }
      direct ++ shorthand
    }

  private implicit val _term_event_decoder: Decoder[TermEvent] = (c: HCursor) =>
    for {
      occurredat <- c.downField("occurred_at").as[Option[String]]
      startat <- c.downField("start_at").as[Option[String]]
      endat <- c.downField("end_at").as[Option[String]]
      location <- c.downField("location").as[Option[String]]
      actors <- c.downField("actors").as[Option[Vector[String]]]
      roles <- c.downField("roles").as[Option[Vector[String]]]
      participants <- c.downField("participants").as[Option[Vector[String]]]
      scenarios <- c.downField("scenarios").as[Option[Vector[String]]]
      evidence <- c.downField("evidence").as[Option[Vector[String]]]
      cmlevent <- c.downField("cml_event").as[Option[String]]
      cmlcomponent <- c.downField("cml_component").as[Option[String]]
      cmlstatemachine <- c.downField("cml_statemachine").as[Option[String]]
    } yield TermEvent(occurredat, startat, endat, location, actors.getOrElse(Vector.empty), roles.getOrElse(Vector.empty), participants.getOrElse(Vector.empty), scenarios.getOrElse(Vector.empty), evidence.getOrElse(Vector.empty), cmlevent, cmlcomponent, cmlstatemachine)

  private implicit val _term_actor_decoder: Decoder[TermActor] = (c: HCursor) =>
    for {
      roles <- c.downField("roles").as[Option[Vector[String]]]
      organization <- c.downField("organization").as[Option[String]]
      description <- c.downField("description").as[Option[String]]
    } yield TermActor(roles.getOrElse(Vector.empty), organization, description)

  private implicit val _term_role_decoder: Decoder[TermRole] = (c: HCursor) =>
    for {
      actors <- c.downField("actors").as[Option[Vector[String]]]
      responsibilities <- c.downField("responsibilities").as[Option[Vector[String]]]
      permissions <- c.downField("permissions").as[Option[Vector[String]]]
    } yield TermRole(actors.getOrElse(Vector.empty), responsibilities.getOrElse(Vector.empty), permissions.getOrElse(Vector.empty))

  private implicit val _term_quality_decoder: Decoder[TermQuality] = (c: HCursor) =>
    for {
      isolated <- c.downField("isolated").as[Option[Boolean]]
      unreferenced <- c.downField("unreferenced").as[Option[Boolean]]
      weaklyconnected <- c.downField("weakly_connected").as[Option[Boolean]]
    } yield TermQuality(isolated.getOrElse(false), unreferenced.getOrElse(false), weaklyconnected.getOrElse(false))

  private implicit val _term_entry_decoder: Decoder[TermEntry] = (c: HCursor) =>
    for {
      id <- c.downField("id").as[String]
      slug <- c.downField("slug").as[String]
      title <- c.downField("title").as[String]
      reading <- c.downField("reading").as[Option[String]]
      category <- c.downField("category").as[Option[String]]
      sourcepath <- c.downField("source_path").as[String]
      publicpath <- c.downField("public_path").as[String]
      definitionhtml <- c.downField("definition_html").as[String]
      summary <- c.downField("summary").as[Option[String]]
      aliases <- c.downField("aliases").as[Option[Vector[String]]]
      articlerefs <- c.downField("article_refs").as[Option[Vector[TermReference]]]
      termrefs <- c.downField("term_refs").as[Option[Vector[TermReference]]]
      rdfrefs <- c.downField("rdf_refs").as[Option[Vector[TermRdfReference]]]
      videorefs <- c.downField("video_refs").as[Option[Vector[TermReference]]]
      termtype <- c.downField("term_type").as[Option[String]]
      resourcetype <- c.downField("resource_type").as[Option[String]]
      resourceobjecttype <- c.downField("resource").downField("type").as[Option[String]]
      event <- c.downField("event").as[Option[TermEvent]]
      actor <- c.downField("actor").as[Option[TermActor]]
      role <- c.downField("role").as[Option[TermRole]]
      quality <- c.downField("quality").as[Option[TermQuality]]
      tags <- c.downField("tags").as[Option[Vector[String]]]
    } yield TermEntry(id, slug, title, reading, category, sourcepath, publicpath, definitionhtml, summary, aliases.getOrElse(Vector.empty), articlerefs.getOrElse(Vector.empty), termrefs.getOrElse(Vector.empty), rdfrefs.getOrElse(Vector.empty), videorefs.getOrElse(Vector.empty), termtype.getOrElse(""), resourcetype.orElse(resourceobjecttype), _decode_term_cml_links(c.downField("cml").focus), event, actor, role, quality.getOrElse(TermQuality(false, false, false)), tags.getOrElse(Vector.empty))

  private implicit val _term_index_decoder: Decoder[TermIndex] = (c: HCursor) =>
    for {
      terms <- c.downField("terms").as[Option[Vector[TermEntry]]]
    } yield TermIndex(terms.getOrElse(Vector.empty))

  private implicit val _scenario_entry_decoder: Decoder[ScenarioEntry] = (c: HCursor) =>
    for {
      id <- c.downField("id").as[String]
      slug <- c.downField("slug").as[String]
      scenariotype <- c.downField("scenario_type").as[String]
      title <- c.downField("title").as[String]
      summary <- c.downField("summary").as[Option[String]]
      category <- c.downField("category").as[Option[String]]
      sourcepath <- c.downField("source_path").as[String]
      publicpath <- c.downField("public_path").as[String]
      terms <- c.downField("terms").as[Option[Vector[String]]]
      tags <- c.downField("tags").as[Option[Vector[String]]]
      status <- c.downField("status").as[Option[String]]
      termextraction <- _decode_term_extraction(c)
    } yield ScenarioEntry(id, slug, scenariotype, title, summary, category, sourcepath, publicpath, terms.getOrElse(Vector.empty), tags.getOrElse(Vector.empty), status, termextraction)

  private implicit val _scenario_index_decoder: Decoder[ScenarioIndex] = (c: HCursor) =>
    for {
      scenarios <- c.downField("scenarios").as[Option[Vector[ScenarioEntry]]]
    } yield ScenarioIndex(scenarios.getOrElse(Vector.empty))

  final case class BuildConfig(
    project: Path,
    source: String,
    website: String,
    antora: String,
    doxsite: String,
    arcadiaSite: String,
    uiBundle: String,
    strategy: String,
    dockerImage: String,
    siteOutputScopePolicy: String,
    siteTitle: String,
    localeMode: LocaleMode,
    defaultLocale: String,
    languages: Vector[String],
    arcadia: ArcadiaConfig,
    directAssets: DirectAssetsConfig,
    publication: PublicationSettings,
    dashboardColorGroup: String,
    bibliographyService: Boolean
  ) {
    def sourcepath: Path = project.resolve(source)
    def sourcePath: Path = sourcepath
    def websitePath: Path = project.resolve(website)
    def antoraPath: Path = project.resolve(antora)
    def doxsitePath: Path = project.resolve(doxsite)
    def arcadiaSitePath: Path = project.resolve(arcadiaSite)
    def uiBundlePath: Path = project.resolve(uiBundle)
  }

  final case class ArcadiaConfig(enabled: Boolean, source: String)
  final case class DirectAssetsConfig(enabled: Boolean, items: Vector[DirectAsset])
  final case class DirectAsset(source: String, destination: String)
  final case class PublicationSettings(
    path: String,
    warehouse: Option[String],
    repository: String,
    mergeRdf: Boolean,
    missingRdfPolicy: String
  ) {
    def publicationPath(project: Path): Path = project.resolve(path).toAbsolutePath.normalize()
    def warehousePath(project: Path): Option[Path] = warehouse.map(project.resolve(_).toAbsolutePath.normalize())
    def repositoryPath(project: Path): Path = project.resolve(repository).toAbsolutePath.normalize()
    def artifactBasePath(project: Path): Path = repositoryPath(project)
    def publicationRepositoryBasePath(project: Path): Path = repositoryPath(project)
  }
  final case class PublicationConfig(
    project: Path,
    source: String,
    publication: String,
    warehouse: Option[String],
    repository: String,
    version: Option[String],
    force: Boolean,
    videoEnabled: Boolean,
    dryRun: Boolean,
    strategy: String
  ) {
    def sourcepath: Path = project.resolve(source).toAbsolutePath.normalize()
    def publicationPath: Path = project.resolve(publication).toAbsolutePath.normalize()
    def warehousePath: Option[Path] = warehouse.map(project.resolve(_).toAbsolutePath.normalize())
    def repositoryPath: Path = project.resolve(repository).toAbsolutePath.normalize()
    def artifactBasePath: Path = repositoryPath
    def manifestPath: Path = project.resolve("target/cozy-bok/publish/latest/manifest.json").toAbsolutePath.normalize()
  }
  final case class WorkflowConfig(
    project: Path,
    name: String,
    command: Vector[String],
    env: Map[String, String],
    backup: Option[WebsiteBackupConfig] = None
  )
  final case class WebsiteBackupConfig(source: Path, root: Path, compressed: Boolean)
  private final case class PublishStep(name: String, status: String, message: String)
  private final case class WebsiteBackupSnapshot(path: Path, yearMonth: YearMonth, order: String)
  private final case class BibliographyRdfAlias(aliastail: String, targettail: String, title: String)
  private final case class PublishPreflight(
    stage: Option[WorkflowConfig],
    upload: WorkflowConfig,
    build: BuildConfig,
    videopackages: Vector[Path],
    projectpackages: Vector[Path]
  ) {
    def packageCount: Int = videopackages.size + projectpackages.size
  }
  private final case class ParsedArgs(request: CliRequest) {
    def argument(name: String): Option[String] =
      request.arguments.find(_.name == name).map(_.asString).map(_.trim).filter(_.nonEmpty)

    def property(name: String): Option[String] =
      request.properties.find(_.name == name).map(_.asString).map(_.trim).filter(_.nonEmpty)

    def properties(name: String): Vector[String] =
      request.properties.filter(_.name == name).map(_.asString).map(_.trim).filter(_.nonEmpty).toVector

    def pathProperty(name: String): Option[Path] =
      request.properties.find(_.name == name).map(x => _to_path(x.value))

    def requiredPath(name: String, usage: String): Path =
      pathProperty(name).getOrElse(RAISE.invalidArgumentFault(s"Missing --${name} ${usage}"))

    def optionalInt(name: String): Option[Int] =
      request.properties.find(_.name == name).map { value =>
        value.value match {
          case m: Int => m
          case other => RAISE.invalidArgumentFault(s"Invalid --${name} <number>: ${other}")
        }
      }

    def validateNoUnrecognized(): Unit = {
      request.switches.filter(_.spec.isEmpty).foreach { x =>
        RAISE.invalidArgumentFault(s"Unknown option: --${x.name}")
      }
      request.arguments.filter(_.spec.isEmpty).foreach { x =>
        RAISE.invalidArgumentFault(s"Unknown argument: ${x.asString}")
      }
    }
  }

  private object BokArgs {
    private val _p_save = spec.Parameter.propertyFileOption("save")
    private val _p_project = spec.Parameter.argumentFile("project")
    private val _p_project_property = spec.Parameter.propertyFileOption("project")
    private val _p_project_dir_property = spec.Parameter.propertyFileOption("project-dir")

    private val _p_no_project_files = spec.Parameter("no-project-files", spec.Parameter.SwitchKind)
    private val _p_no_scaffold_files = spec.Parameter("no-scaffold-files", spec.Parameter.SwitchKind)
    private val _p_overwrite_project_files = spec.Parameter("overwrite-project-files", spec.Parameter.SwitchKind)
    private val _p_force_project_files = spec.Parameter("force-project-files", spec.Parameter.SwitchKind)

    private val _p_article = spec.Parameter(
      "article",
      spec.Parameter.PropertyKind,
      spec.XString,
      spec.Multiplicity.ZeroMore
    )
    private val _p_term = spec.Parameter(
      "term",
      spec.Parameter.PropertyKind,
      spec.XString,
      spec.Multiplicity.ZeroMore
    )
    private val _p_goal = spec.Parameter(
      "goal",
      spec.Parameter.PropertyKind,
      spec.XString,
      spec.Multiplicity.ZeroMore
    )
    private val _p_subgoal = spec.Parameter(
      "subgoal",
      spec.Parameter.PropertyKind,
      spec.XString,
      spec.Multiplicity.ZeroMore
    )

    private val _create_request = spec.Request(
      _p_save,
      spec.Parameter.property("name"),
      spec.Parameter.property("url"),
      spec.Parameter.property("language"),
      _p_no_project_files,
      _p_no_scaffold_files,
      _p_overwrite_project_files,
      _p_force_project_files
    )

    private val _category_request = spec.Request(
      spec.Parameter.argument("name"),
      _p_project_property,
      _p_project_dir_property,
      spec.Parameter.property("title"),
      spec.Parameter.property("description"),
      spec.Parameter.property("vision"),
      _p_goal,
      _p_subgoal,
      spec.Parameter.property("kind"),
      spec.Parameter.propertyInt("order"),
      _p_article,
      _p_term,
      _p_no_project_files,
      _p_no_scaffold_files,
      _p_overwrite_project_files,
      _p_force_project_files
    )

    private val _build_request = spec.Request(
      _p_project_property,
      _p_project_dir_property,
      spec.Parameter.property("strategy"),
      spec.Parameter.property("docker-image"),
      spec.Parameter.property("dashboard-color-group"),
      spec.Parameter.propertyFileOption("warehouse"),
      spec.Parameter.propertyFileOption("repository"),
      spec.Parameter.propertyFileOption("publication"),
      spec.Parameter.property("rdf-missing-artifact-policy"),
      spec.Parameter("no-bib-service", spec.Parameter.SwitchKind)
    )

    private val _publication_request = spec.Request(
      _p_project,
      spec.Parameter.propertyFileOption("warehouse"),
      spec.Parameter.propertyFileOption("repository"),
      spec.Parameter.propertyFileOption("publication"),
      spec.Parameter.property("version"),
      spec.Parameter.property("strategy"),
      spec.Parameter("force", spec.Parameter.SwitchKind),
      spec.Parameter("dry-run", spec.Parameter.SwitchKind)
    )

    private val _workflow_request = spec.Request(_p_project)
    private val _doctor_request = spec.Request(
      spec.Parameter("fix", spec.Parameter.SwitchKind),
      spec.Parameter("dry-run", spec.Parameter.SwitchKind)
    )

    def create(args: List[String]): ParsedArgs = _parse("bok-create", _create_request, args)
    def category(args: List[String]): ParsedArgs = _parse("bok-create-category", _category_request, args)
    def build(args: List[String]): ParsedArgs = _parse("bok-build", _build_request, _normalize_optional_project_argument(args))
    def publication(name: String, args: List[String]): ParsedArgs = _parse(s"bok-${name}", _publication_request, args)
    def workflow(name: String, args: List[String]): ParsedArgs = _parse(s"bok-${name}", _workflow_request, args)
    def doctor(args: List[String]): ParsedArgs = _parse("bok-doctor", _doctor_request, args)

    private def _parse(name: String, request: spec.Request, args: List[String]): ParsedArgs =
      ParsedArgs(request.build(CliRequest(name), args))

    private def _normalize_optional_project_argument(args: List[String]): List[String] =
      args match {
        case x :: xs if !x.startsWith("--") => "--project" :: x :: xs
        case _ => args
      }
  }
  final case class SiteConfig(
    values: Map[String, String],
    lists: Map[String, Vector[String]],
    goalTrees: Map[String, Vector[BokGoal]] = Map.empty
  ) {
    def value(path: String): Option[String] = values.get(path).map(_.trim).filter(_.nonEmpty)
    def boolean(path: String): Option[Boolean] =
      value(path).map(_.toLowerCase(java.util.Locale.ROOT)).collect {
        case "true" | "yes" | "on" => true
        case "false" | "no" | "off" => false
      }
    def list(path: String): Vector[String] = lists.getOrElse(path, Vector.empty)
    def goalTree(path: String): Vector[BokGoal] = goalTrees.getOrElse(path, Vector.empty)
  }
  object SiteConfig {
    val empty: SiteConfig = SiteConfig(Map.empty, Map.empty, Map.empty)
  }

  private val _default_preview_port = "8980"

  trait Runner {
    def run(command: Vector[String], cwd: Path): Unit
    def run(command: Vector[String], cwd: Path, env: Map[String, String]): Unit =
      run(command, cwd)
  }

  object ProcessRunner extends Runner {
    def run(command: Vector[String], cwd: Path): Unit = {
      val exit = Process(command, cwd.toFile).!
      if (exit != 0)
        RAISE.invalidArgumentFault(s"Command failed with exit code ${exit}: ${command.mkString(" ")}")
    }
    override def run(command: Vector[String], cwd: Path, env: Map[String, String]): Unit = {
      val exit = Process(command, cwd.toFile, env.toSeq: _*).!
      if (exit != 0)
        RAISE.invalidArgumentFault(s"Command failed with exit code ${exit}: ${command.mkString(" ")}")
    }
  }

  def execute(args: List[String]): Boolean =
    args match {
      case "bok" :: "create" :: rest =>
        create(CreateConfig.create(rest))
        true
      case "bok" :: "create-category" :: rest =>
        createCategory(CategoryConfig.create(rest))
        true
      case "bok" :: "build" :: rest =>
        build(BuildConfig.create(rest), ProcessRunner)
        true
      case "bok" :: "update" :: rest =>
        build(BuildConfig.create(rest), ProcessRunner)
        true
      case "bok" :: "doctor" :: rest =>
        doctor(DoctorConfig.create(rest, fix = false))
        true
      case "bok" :: "fix" :: rest =>
        doctor(DoctorConfig.create(rest, fix = true))
        true
      case "bok" :: "guide" :: rest =>
        guide(rest)
        true
      case "bok" :: "tutorial" :: rest =>
        guide(rest)
        true
      case "bok" :: "search-bibliography" :: rest if _help_requested(rest) =>
        _print_bibliography_search_usage()
        true
      case "bok" :: "search-bibliography" :: rest =>
        println(searchBibliography(_search_bibliography_config(rest), BibliographySearchRegistry.default))
        true
      case "bok" :: "update-bibliography" :: rest if _help_requested(rest) =>
        _print_bibliography_update_usage()
        true
      case "bok" :: "update-bibliography" :: rest =>
        val config = _update_bibliography_config(rest)
        println(updateBibliography(config, _bibliography_fetcher(config.project, BibliographyHttpBibtexFetcher)))
        true
      case "bok" :: "publish-video" :: rest if _help_requested(rest) =>
        _print_publication_usage("publish-video")
        true
      case "bok" :: "publish-video" :: rest =>
        publishVideo(PublicationConfig.create("publish-video", rest), CozyVideo.VoicevoxClient.default, CozyVideo.VideoProcessRunner.default)
        true
      case "bok" :: "publish-projects" :: rest if _help_requested(rest) =>
        _print_publication_usage("publish-projects")
        true
      case "bok" :: "publish-projects" :: rest =>
        publishProjects(PublicationConfig.create("publish-projects", rest))
        true
      case "bok" :: "update-publication" :: rest if _help_requested(rest) =>
        _print_publication_usage("update-publication")
        true
      case "bok" :: "update-publication" :: rest =>
        updatePublication(PublicationConfig.create("update-publication", rest), CozyVideo.VoicevoxClient.default, CozyVideo.VideoProcessRunner.default)
        true
      case "bok" :: "publish" :: rest if _help_requested(rest) =>
        _print_publication_usage("publish")
        true
      case "bok" :: "publish" :: rest =>
        publish(PublicationConfig.create("publish", rest), ProcessRunner, CozyVideo.VoicevoxClient.default, CozyVideo.VideoProcessRunner.default)
        true
      case "bok" :: "preview" :: rest =>
        preview(rest, ProcessRunner)
        true
      case "bok" :: "stage" :: rest if _help_requested(rest) =>
        _print_workflow_usage("stage")
        true
      case "bok" :: "stage" :: rest =>
        runWorkflow(WorkflowConfig.create("stage", rest), ProcessRunner)
        true
      case "bok" :: "upload" :: rest if _help_requested(rest) =>
        _print_workflow_usage("upload")
        true
      case "bok" :: "upload" :: rest =>
        runWorkflow(WorkflowConfig.create("upload", rest), ProcessRunner)
        true
      case "bok" :: other :: _ =>
        RAISE.invalidArgumentFault(s"Unsupported bok command: ${other}")
      case _ =>
        false
    }

  private def _help_requested(args: List[String]): Boolean =
    args.exists(x => x == "--help" || x == "-h")

  private def _print_publication_usage(name: String): Unit = {
    name match {
      case "publish" =>
        println("Usage: cozy bok publish <project-dir> [--publication <dir>] [--repository <dir>] [--warehouse <dir>] [--version <version>] [--strategy production] [--force] [--dry-run]")
        println("Run update-publication, build, optional stage, and configured upload workflow. Use --dry-run to print the plan without publication, repository, site, or upload side effects.")
      case "publish-video" =>
        println("Usage: cozy bok publish-video <project-dir> [--publication <dir>] [--repository <dir>] [--warehouse <dir>] [--version <version>] [--force]")
        println("Publish .video packages into the BoK publication registry and artifact repository.")
      case "publish-projects" =>
        println("Usage: cozy bok publish-projects <project-dir> [--publication <dir>] [--repository <dir>] [--warehouse <dir>] [--version <version>] [--force]")
        println("Register src/main/doxsite/projects/<category>/<slug> project knowledge packages into the BoK publication registry. CAR artifact publishing remains the responsibility of cozy publish-car.")
      case "update-publication" =>
        println("Usage: cozy bok update-publication <project-dir> [--publication <dir>] [--repository <dir>] [--warehouse <dir>] [--version <version>] [--force]")
        println("Update the BoK publication registry for .video and project knowledge packages.")
      case other =>
        RAISE.invalidArgumentFault(s"Unknown publication command: ${other}")
    }
  }

  private def _print_workflow_usage(name: String): Unit = {
    println(s"Usage: cozy bok ${name} [<project-dir>]")
    println(s"Run the external command registered at bok.workflow.${name}.command.")
    if (name == "upload")
      println("When bok.backup.enabled is true, website.d is backed up before upload. Defaults: enabled=false, dir=website.backup, compressed=true.")
  }

  private def _print_bibliography_search_usage(): Unit = {
    println("Usage: cozy bok search-bibliography <query> [--provider crossref|openlibrary|dblp|all] [--limit <n>] [--format text|json]")
    println("Search external bibliography/reference providers. This command does not modify the BoK source tree.")
  }

  private def _print_bibliography_update_usage(): Unit = {
    println("Usage: cozy bok update-bibliography [<project-dir>] [--force] [--report-only|--no-fetch]")
    println("Fetch explicit BibTeX/cache sources registered in bibliography metadata into target/cozy-bok/bibliography/cache.")
    println("Local .bib files under repository/bibliography, repository/catalog/bibliography, or src/main/doxsite/bibliography are used before external providers.")
    println("With --report-only or --no-fetch, report missing bibliography cache entries without external fetches.")
  }

  private def _search_bibliography_config(args: List[String]): BibliographySearchConfig = {
    val (options, values) = _parse_bibliography_options(args)
    val query = values.mkString(" ").trim
    if (query.isEmpty)
      RAISE.invalidArgumentFault("Usage: cozy bok search-bibliography <query> [--provider crossref|openlibrary|dblp|all] [--limit <n>] [--format text|json]")
    val provider = options.getOrElse("provider", "all").toLowerCase(java.util.Locale.ROOT)
    if (!Set("crossref", "openlibrary", "dblp", "all").contains(provider))
      RAISE.invalidArgumentFault(s"Unsupported bibliography provider: ${provider}")
    val limit = options.get("limit").map(x => try x.toInt catch { case NonFatal(_) => RAISE.invalidArgumentFault(s"Invalid --limit <n>: ${x}") }).getOrElse(10)
    val format = options.getOrElse("format", "text").toLowerCase(java.util.Locale.ROOT)
    BibliographySearchConfig(query, provider, limit, format)
  }

  private def _update_bibliography_config(args: List[String]): BibliographyUpdateConfig = {
    val normalized = args match {
      case x :: xs if !x.startsWith("--") => "--project" :: x :: xs
      case _ => args
    }
    val (options, values) = _parse_bibliography_options(normalized)
    if (values.nonEmpty)
      RAISE.invalidArgumentFault(s"Unknown bibliography update argument: ${values.head}")
    BibliographyUpdateConfig(
      options.get("project").map(Paths.get(_)).getOrElse(Paths.get(".")),
      options.contains("force"),
      options.contains("report-only") || options.contains("no-fetch")
    )
  }

  private def _parse_bibliography_options(args: List[String]): (Map[String, String], List[String]) = {
    def _loop_(rest: List[String], options: Map[String, String], values: List[String]): (Map[String, String], List[String]) =
      rest match {
        case Nil => (options, values.reverse)
        case arg :: tail if arg.startsWith("--") && arg.contains("=") =>
          val Array(key, value) = arg.drop(2).split("=", 2)
          _loop_(tail, options + (key -> value), values)
        case "--force" :: tail => _loop_(tail, options + ("force" -> "true"), values)
        case arg :: value :: tail if arg.startsWith("--") =>
          _loop_(tail, options + (arg.drop(2) -> value), values)
        case arg :: _ if arg.startsWith("--") =>
          RAISE.invalidArgumentFault(s"Missing value for ${arg}")
        case value :: tail => _loop_(tail, options, value :: values)
      }
    _loop_(args, Map.empty, Nil)
  }

  def create(config: CreateConfig): Unit = {
    val sitedir = config.save.resolve("src/main/doxsite")
    _write(config.save.resolve("conf/cozy/config.yaml"), _cozy_config(Some(config)), config.policy)
    _write(config.save.resolve("README.md"), _readme(config), config.policy)
    _write(config.save.resolve("STRUCTURE.md"), _structure(config), config.policy)
    _write(sitedir.resolve("site.conf"), _site_conf(config), config.policy)
    _write(sitedir.resolve("index.dox"), _site_index(config), config.policy)
    _write(sitedir.resolve("glossary/category.yaml"), _category("Glossary", "用語集", "BoK全体で共有する用語集。"), config.policy)
    _write(sitedir.resolve("history/category.yaml"), _category("History", "History", "BoK運用と更新履歴。"), config.policy)
    _write(sitedir.resolve("history/index.dox"), _history_index(), config.policy)
    _write(sitedir.resolve("manual/local-rules.dox"), _manual_local_rules(config), config.policy)
    _write(sitedir.resolve("rdf/site.ttl"), _site_ttl(config), config.policy)
    _write(sitedir.resolve("rdf/site.jsonld"), _site_jsonld(config), config.policy)
    _write(sitedir.resolve("rdf/schema/knowledgehub.ttl"), _schema_ttl(config), config.policy)
    _write(sitedir.resolve("rdf/schema/knowledgehub.jsonld"), _schema_jsonld(), config.policy)
    _write(sitedir.resolve("rdf/ontology/knowledgehub.ttl"), _ontology_ttl(config), config.policy)
    _write(sitedir.resolve("rdf/ontology/knowledgehub.jsonld"), _ontology_jsonld(), config.policy)
    _write(sitedir.resolve("assets/css/knowledgehub.css"), _css(), config.policy)
    _write(config.save.resolve("etc/website-stage.sh.proto"), _website_stage_script(config), config.policy)
    _write(config.save.resolve("etc/website-upload.sh.proto"), _website_upload_script(config), config.policy)
    _write_default_ui_bundle(config.save.resolve("src/main/antora-ui/build/ui-bundle.zip"), config.policy)
  }

  def doctor(config: DoctorConfig): Unit = {
    val inspection = _inspect_bok(config.input)
    _print_bok_inspection(inspection, config)
    if (config.fix)
      _apply_bok_fixes(inspection, config)
  }

  def guide(args: List[String]): Unit = {
    val scenario = args match {
      case Nil => "overview"
      case name :: Nil => name
      case _ => RAISE.invalidArgumentFault(s"Usage: cozy bok guide [scenario]")
    }
    val normalized = scenario.toLowerCase(java.util.Locale.ROOT)
    normalized match {
      case "overview" | "list" =>
        println("Cozy BoK guide")
        println("Available scenarios:")
        _bok_guide_scenarios.foreach { case (name, title, _) =>
          println(s"  - ${name}: ${title}")
        }
        println()
        println("Run: cozy bok guide <scenario>")
      case "all" =>
        _bok_guide_scenarios.foreach { case (name, title, lines) =>
          _print_bok_guide_scenario(name, title, lines)
          println()
        }
      case name =>
        _bok_guide_scenarios.find(_._1 == name) match {
          case Some((n, title, lines)) => _print_bok_guide_scenario(n, title, lines)
          case None => RAISE.invalidArgumentFault(s"Unknown BoK guide scenario: ${scenario}")
        }
    }
  }

  def searchBibliography(config: BibliographySearchConfig, registry: BibliographySearchRegistry): String = {
    val results = registry.search(config)
    config.format match {
      case "json" => _bibliography_search_json(results)
      case "text" =>
        if (results.isEmpty)
          s"No bibliography candidates found for: ${config.query}"
        else
          results.map(_.text).mkString("\n\n")
      case other => RAISE.invalidArgumentFault(s"Unsupported bibliography search format: ${other}")
    }
  }

  def updateBibliography(config: BibliographyUpdateConfig, fetcher: BibliographyBibtexFetcher): String = {
    val metadata = config.project.resolve("doxsite.d/metadata/bibliography/bibliography.json")
    if (!Files.isRegularFile(metadata))
      s"No bibliography metadata found: ${metadata}. Run cozy bok build first."
    else {
      val index = parser.parse(Files.readString(metadata, StandardCharsets.UTF_8)).toOption.flatMap(_.as[BibliographyIndex].toOption).getOrElse(BibliographyIndex(Vector.empty))
      val cache = config.project.resolve("target/cozy-bok/bibliography/cache")
      var written = Vector.empty[Path]
      var missing = Vector.empty[String]
      index.entries.foreach { entry =>
        val path = cache.resolve(s"${_safe_file_name(entry.id)}.bib")
        if (config.force || !Files.exists(path)) {
          entry.bibtex.raw match {
            case Some(raw) =>
              if (!config.reportonly) {
                _write_text(path, raw)
                written :+= path
              }
            case None if config.reportonly =>
              if (entry.bibtex.sourceurl.nonEmpty || entry.needsresolution)
                missing :+= entry.id
            case None =>
              val body = fetcher.fetchEntry(entry)
              body match {
                case Some(value) =>
                  _write_text(path, value)
                  written :+= path
                case None =>
                  if (entry.bibtex.sourceurl.nonEmpty || entry.needsresolution)
                    missing :+= entry.id
              }
          }
        }
      }
      if (config.reportonly && missing.nonEmpty)
        missing.distinct.map(x => s"bibliography cache missing: ${x}").mkString("\n")
      else if (config.reportonly)
        s"bibliography cache: complete (${cache})"
      else if (missing.nonEmpty)
        missing.distinct.map(x => s"bibliography cache unresolved: ${x}").mkString("\n")
      else if (written.isEmpty)
        s"bibliography cache: no updates (${cache})"
      else
        written.map(path => s"bibliography cache: ${config.project.toAbsolutePath.normalize.relativize(path.toAbsolutePath.normalize)}").mkString("\n")
    }
  }


  private def _stash_bibliography_cache(config: BuildConfig): Option[Path] = {
    val cache = config.project.resolve("target/cozy-bok/bibliography/cache")
    if (!Files.isDirectory(cache))
      None
    else {
      val tmp = Files.createTempDirectory("cozy-bibliography-cache")
      _copy_directory(cache, tmp.resolve("cache"))
      Some(tmp.resolve("cache"))
    }
  }

  private def _restore_bibliography_cache(config: BuildConfig, cache: Option[Path]): Unit =
    cache.foreach { source =>
      if (Files.isDirectory(source))
        _copy_directory(source, config.project.resolve("target/cozy-bok/bibliography/cache"))
    }

  private def _write_effective_bibliography_metadata(config: BuildConfig): Unit =
    _bibliography_index(config).foreach { index =>
      val effective = _effective_bibliography_index(config, index)
      _write_text(config.doxsitePath.resolve("metadata/bibliography/bibliography.json"), effective.toJsonString)
    }

  private def _resolve_bibliography_service_entries(config: BuildConfig, fetcher: BibliographyBibtexFetcher): Unit =
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

  private def _is_bare_bibliography_citation_key(value: String): Boolean =
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

  private def _distinct_preserving_order(values: Vector[String]): Vector[String] =
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

  private def _sync_effective_bibliography_rdf(config: BuildConfig): Unit =
    _bibliography_index(config).foreach { index =>
      val aliases = _bibliography_rdf_aliases(index)
      if (aliases.nonEmpty) {
        _sync_effective_bibliography_turtle(config.doxsitePath.resolve("site.ttl"), aliases)
        _sync_effective_bibliography_jsonld(config.doxsitePath.resolve("site.jsonld"), aliases)
      }
    }

  private def _sync_effective_bibliography_fragments(config: BuildConfig): Unit =
    _bibliography_index(config).foreach { index =>
      val entries = index.entries.filterNot(_.needsresolution)
      val path = config.doxsitePath.resolve("metadata/documents/fragments.json")
      if (entries.nonEmpty && Files.isRegularFile(path))
        parser.parse(Files.readString(path, StandardCharsets.UTF_8)).toOption.foreach { json =>
          _write_text(path, _rewrite_bibliography_fragment_json(json, entries).spaces2 + "\n")
        }
    }

  private def _rewrite_bibliography_fragment_json(json: Json, entries: Vector[BibliographyEntry]): Json =
    json.arrayOrObject(
      json,
      values => Json.fromValues(values.map(_rewrite_bibliography_fragment_json(_, entries))),
      obj => {
        val fields = obj.toVector
        val sourcepath = fields.find(_._1 == "source_path").flatMap(_._2.asString)
        val locale = fields.find(_._1 == "locale").flatMap(_._2.asString).getOrElse("en")
        Json.obj(fields.map {
          case ("body_html", value) =>
            "body_html" -> value.asString.
              map(body => Json.fromString(_rewrite_bibliography_fragment_body(sourcepath, locale, body, entries))).
              getOrElse(_rewrite_bibliography_fragment_json(value, entries))
          case (key, value) =>
            key -> _rewrite_bibliography_fragment_json(value, entries)
        }: _*)
      }
    )

  private def _rewrite_bibliography_fragment_body(sourcepath: Option[String], locale: String, body: String, entries: Vector[BibliographyEntry]): String = {
    val normalized = _normalize_bibliography_fragment_heading(locale, body)
    sourcepath.map { source =>
      val references = entries.flatMap { entry =>
        entry.sourcerefs.filter(_.sourcepath == source).map(_ -> entry)
      }
      references.foldLeft(normalized) {
        case (html, (ref, entry)) => _rewrite_bibliography_reference_item(html, ref, entry)
      }
    }.getOrElse(normalized)
  }

  private def _normalize_bibliography_fragment_heading(locale: String, body: String): String = {
    val title = _html_escape(_ui(locale, "bibliography.title"))
    body.
      replace("<h2>参考文献</h2>", s"<h2>${title}</h2>").
      replace("<h2>参考情報</h2>", s"<h2>${title}</h2>").
      replace("<h2>参照情報</h2>", s"<h2>${title}</h2>")
  }

  private def _rewrite_bibliography_reference_item(html: String, ref: BibliographySourceRef, entry: BibliographyEntry): String = {
    val key = Pattern.quote(_html_escape(ref.citationkey))
    val pattern = Pattern.compile(
      s"""(?is)<li>(\\s*\\[\\d+\\]\\s*)<a class="bibliography-reference" href="[^"]*">${key}</a>\\.\\s*Unresolved bibliography reference[^<]*</li>"""
    )
    val matcher = pattern.matcher(html)
    val buffer = new StringBuffer
    while (matcher.find()) {
      val replacement =
        s"""<li>${matcher.group(1)}<a class="bibliography-reference" href="${_html_escape(_relative_href(ref.publicpath, entry.publicpath))}">${_html_escape(_bibliography_citation_label(entry))}</a>.</li>"""
      matcher.appendReplacement(buffer, java.util.regex.Matcher.quoteReplacement(replacement))
    }
    matcher.appendTail(buffer)
    buffer.toString
  }

  private def _relative_href(frompublicpath: String, targetpublicpath: String): String = {
    val depth = frompublicpath.split('/').dropRight(1).length
    ("../" * depth) + targetpublicpath
  }

  private def _bibliography_citation_label(entry: BibliographyEntry): String =
    entry.citation.
      filter(_.trim.nonEmpty).
      orElse(entry.key.map(key => s"${entry.title} [${key}]")).
      getOrElse(entry.title)

  private def _bibliography_rdf_aliases(index: BibliographyIndex): Vector[BibliographyRdfAlias] =
    index.entries.flatMap { entry =>
      _bibliography_alias_refs(entry).map { alias =>
        BibliographyRdfAlias(
          s"bibliography/${entry.categorySlug}/${_safe_file_name(alias)}",
          _html_path_without_suffix(entry.publicpath),
          entry.title
        )
      }
    }

  private def _bibliography_alias_refs(entry: BibliographyEntry): Vector[String] =
    _distinct_preserving_order(entry.refs ++ entry.key.toVector).
      filter(ref => ref != entry.id && _is_bare_bibliography_citation_key(ref))

  private def _html_path_without_suffix(path: String): String =
    path.stripSuffix(".html")

  private def _sync_effective_bibliography_turtle(path: Path, aliases: Vector[BibliographyRdfAlias]): Unit =
    if (Files.isRegularFile(path)) {
      val text = Files.readString(path, StandardCharsets.UTF_8)
      val replacements = _bibliography_turtle_replacements(text, aliases)
      if (replacements.nonEmpty || aliases.nonEmpty) {
        val source = if (replacements.nonEmpty) _remove_turtle_subject_blocks(text, replacements.keySet) else text
        val replaced = replacements.foldLeft(source) {
          case (acc, (from, to)) => acc.replace(s"<${from}>", s"<${to}>")
        }
        val rewritten = _deduplicate_turtle_subject_blocks(replaced)
        val cleaned = _replace_turtle_bibliography_placeholders(rewritten, aliases)
        _write_text(path, cleaned)
      }
    }

  private def _bibliography_turtle_replacements(text: String, aliases: Vector[BibliographyRdfAlias]): Map[String, String] =
    aliases.flatMap { alias =>
      _find_rdf_iri(text, alias.aliastail).flatMap { aliasiri =>
        _find_rdf_iri(text, alias.targettail).map { targetiri =>
          val resource = aliasiri -> targetiri
          val pages = _find_rdf_iris(text, s"${alias.aliastail}.html").map { pageiri =>
            pageiri -> pageiri.replace(alias.aliastail, alias.targettail)
          }
          resource +: pages
        }
      }.getOrElse(Vector.empty)
    }.toMap

  private def _find_rdf_iri(text: String, tail: String): Option[String] =
    _find_rdf_iris(text, tail).headOption

  private def _find_rdf_iris(text: String, tail: String): Vector[String] = {
    val pattern = ("(?i)<([^>]*" + Pattern.quote(tail) + ")>").r
    pattern.findAllMatchIn(text).map(_.group(1)).toVector.distinct
  }

  private def _remove_turtle_subject_blocks(text: String, subjects: Set[String]): String = {
    val lines = text.split("(?<=\\n)", -1).toVector
    val builder = new StringBuilder
    var i = 0
    while (i < lines.length) {
      _turtle_subject_iri(lines(i)) match {
        case Some(iri) if subjects.contains(iri) =>
          i += 1
          while (i < lines.length && _turtle_subject_iri(lines(i)).isEmpty)
            i += 1
        case _ =>
          builder.append(lines(i))
          i += 1
      }
    }
    builder.toString
  }

  private def _turtle_subject_iri(line: String): Option[String] = {
    val subject = "^<([^>]+)>\\s+.*".r
    line.trim match {
      case subject(iri) => Some(iri)
      case _ => None
    }
  }

  private def _deduplicate_turtle_subject_blocks(text: String): String = {
    val blocks = _turtle_blocks(text)
    val grouped = blocks.collect { case (Some(iri), block) => iri -> block }.groupBy(_._1).map {
      case (iri, values) => iri -> values.map(_._2)
    }
    blocks.foldLeft((Set.empty[String], Vector.empty[String])) {
      case ((seen, acc), (None, block)) => seen -> (acc :+ block)
      case ((seen, acc), (Some(iri), _)) if seen.contains(iri) => seen -> acc
      case ((seen, acc), (Some(iri), _)) =>
        val selected = _preferred_turtle_subject_block(grouped.getOrElse(iri, Vector.empty))
        (seen + iri) -> (acc :+ selected)
    }._2.mkString
  }

  private def _preferred_turtle_subject_block(blocks: Vector[String]): String =
    blocks.find(_is_resolved_turtle_subject_block).orElse(blocks.lastOption).getOrElse("")

  private def _is_resolved_turtle_subject_block(block: String): Boolean =
    block.contains("http://purl.org/dc/terms/source") ||
      block.contains("dcterms:source") ||
      !block.contains("Unresolved bibliography reference")

  private def _replace_turtle_bibliography_placeholders(text: String, aliases: Vector[BibliographyRdfAlias]): String =
    _turtle_blocks(text).map {
      case (Some(iri), block) =>
        aliases.find(alias => _ends_with_ignore_case(iri, alias.targettail)).
          map(alias => _replace_bibliography_placeholder_text(block, alias.title)).
          getOrElse(block)
      case (_, block) =>
        block
    }.mkString

  private def _replace_bibliography_placeholder_text(text: String, title: String): String =
    "Unresolved bibliography reference[^\"]*".r.replaceAllIn(text, java.util.regex.Matcher.quoteReplacement(_escape_rdf_string(title)))

  private def _escape_rdf_string(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"")

  private def _turtle_blocks(text: String): Vector[(Option[String], String)] = {
    val lines = text.split("(?<=\\n)", -1).toVector
    var blocks = Vector.empty[(Option[String], String)]
    var currentiri: Option[String] = None
    val current = new StringBuilder
    lines.foreach { line =>
      _turtle_subject_iri(line) match {
        case Some(iri) =>
          if (current.nonEmpty)
            blocks :+= currentiri -> current.toString
          current.clear()
          currentiri = Some(iri)
          current.append(line)
        case None =>
          current.append(line)
      }
    }
    if (current.nonEmpty)
      blocks :+= currentiri -> current.toString
    blocks
  }

  private def _sync_effective_bibliography_jsonld(path: Path, aliases: Vector[BibliographyRdfAlias]): Unit =
    if (Files.isRegularFile(path)) {
      val text = Files.readString(path, StandardCharsets.UTF_8)
      parser.parse(text).toOption.foreach { json =>
        val replacements = _bibliography_jsonld_replacements(json, aliases)
        if (replacements.nonEmpty || aliases.nonEmpty) {
          val rewritten = _rewrite_jsonld_ids(json, replacements)
          val cleaned = _replace_jsonld_bibliography_placeholders(rewritten, aliases)
          _write_text(path, cleaned.spaces2 + "\n")
        }
      }
    }

  private def _bibliography_jsonld_replacements(json: Json, aliases: Vector[BibliographyRdfAlias]): Map[String, String] = {
    val ids = _jsonld_ids(json)
    aliases.flatMap { alias =>
      ids.find(_ends_with_ignore_case(_, alias.aliastail)).flatMap { aliasid =>
        ids.find(_ends_with_ignore_case(_, alias.targettail)).map { targetid =>
          val resource = aliasid -> targetid
          val pages = ids.filter(_ends_with_ignore_case(_, s"${alias.aliastail}.html")).map { pageid =>
            pageid -> pageid.replace(alias.aliastail, alias.targettail)
          }
          resource +: pages
        }
      }.getOrElse(Vector.empty)
    }.toMap
  }

  private def _ends_with_ignore_case(value: String, suffix: String): Boolean =
    value.toLowerCase(Locale.ROOT).endsWith(suffix.toLowerCase(Locale.ROOT))

  private def _jsonld_ids(json: Json): Vector[String] =
    json.arrayOrObject(
      json.asString.toVector,
      _.flatMap(_jsonld_ids).toVector,
      obj => obj.toVector.flatMap {
        case ("@id", value) => value.asString.toVector ++ _jsonld_ids(value)
        case (_, value) => _jsonld_ids(value)
      }.toVector
    ).distinct

  private def _rewrite_jsonld_ids(json: Json, replacements: Map[String, String]): Json =
    json.arrayOrObject(
      json,
      values => Json.fromValues(values.map(_rewrite_jsonld_ids(_, replacements))),
      obj => Json.obj(obj.toVector.flatMap {
        case ("@graph", value) =>
          Some("@graph" -> _rewrite_jsonld_graph(value, replacements))
        case ("@id", value) =>
          Some("@id" -> value.asString.flatMap(replacements.get).map(Json.fromString).getOrElse(_rewrite_jsonld_ids(value, replacements)))
        case (key, value) =>
          Some(key -> _rewrite_jsonld_ids(value, replacements))
      }: _*)
    )

  private def _rewrite_jsonld_graph(json: Json, replacements: Map[String, String]): Json =
    json.asArray.map { values =>
      val rewritten = values.flatMap { value =>
        val id = value.hcursor.downField("@id").as[String].toOption
        if (id.exists(replacements.contains))
          None
        else
          Some(_rewrite_jsonld_ids(value, replacements))
      }
      Json.fromValues(_deduplicate_jsonld_graph_nodes(rewritten.toVector))
    }.getOrElse(_rewrite_jsonld_ids(json, replacements))

  private def _replace_jsonld_bibliography_placeholders(json: Json, aliases: Vector[BibliographyRdfAlias]): Json =
    _replace_jsonld_bibliography_placeholders(json, aliases, None)

  private def _replace_jsonld_bibliography_placeholders(json: Json, aliases: Vector[BibliographyRdfAlias], title: Option[String]): Json =
    json.arrayOrObject(
      json.asString.map(value => Json.fromString(if (title.exists(_ => _is_unresolved_bibliography_placeholder(value))) title.get else value)).getOrElse(json),
      values => Json.fromValues(values.map(_replace_jsonld_bibliography_placeholders(_, aliases, title))),
      obj => {
        val id = obj("@id").flatMap(_.asString)
        val nexttitle = id.flatMap(value => aliases.find(alias => _ends_with_ignore_case(value, alias.targettail)).map(_.title)).orElse(title)
        Json.obj(obj.toVector.map {
          case (key, value) => key -> _replace_jsonld_bibliography_placeholders(value, aliases, nexttitle)
        }: _*)
      }
    )

  private def _deduplicate_jsonld_graph_nodes(values: Vector[Json]): Vector[Json] = {
    val grouped = values.flatMap { value =>
      value.hcursor.downField("@id").as[String].toOption.map(_ -> value)
    }.groupBy(_._1).map {
      case (id, xs) => id -> xs.map(_._2)
    }
    values.foldLeft((Set.empty[String], Vector.empty[Json])) { (state, value) =>
      val (seen, acc) = state
      value.hcursor.downField("@id").as[String].toOption match {
        case Some(id) if seen.contains(id) =>
          seen -> acc
        case Some(id) =>
          val selected = _preferred_jsonld_graph_node(grouped.getOrElse(id, Vector.empty))
          (seen + id) -> (acc :+ selected)
        case None =>
          seen -> (acc :+ value)
      }
    }._2
  }

  private def _preferred_jsonld_graph_node(values: Vector[Json]): Json =
    values.find(_is_resolved_jsonld_graph_node).orElse(values.lastOption).getOrElse(Json.Null)

  private def _is_resolved_jsonld_graph_node(value: Json): Boolean = {
    val text = value.noSpaces
    text.contains("dcterms:source") ||
      text.contains("http://purl.org/dc/terms/source") ||
      !text.contains("Unresolved bibliography reference")
  }

  private def _ensure_bibliography_cache(config: BuildConfig, entry: BibliographyEntry, fetcher: BibliographyBibtexFetcher): Option[String] = {
    val path = config.project.resolve("target/cozy-bok/bibliography/cache").resolve(s"${_safe_file_name(entry.id)}.bib")
    if (_bibliography_source_needs_local_bib(entry)) {
      fetcher.fetchEntry(entry).foreach(_write_text(path, _))
      None
    } else if (!entry.needsresolution && entry.bibtex.raw.isEmpty && entry.bibtex.sourceurl.isEmpty)
      None
    else if (Files.isRegularFile(path))
      None
    else if (entry.bibtex.raw.nonEmpty) {
      _write_text(path, entry.bibtex.raw.get)
      None
    } else if (!config.bibliographyService)
      Some(entry.id)
    else {
      val body =
        fetcher.fetchEntry(entry)
      body match {
        case Some(value) =>
          _write_text(path, value)
          None
        case None => Some(entry.id)
      }
    }
  }

  private def _ensure_bibliography_metadata_handoff(config: BuildConfig): Unit =
    if (_source_declares_bibliography(config) && _bibliography_index(config).forall(_.entries.isEmpty))
      _raise_missing_bibliography_handoff()

  private def _raise_missing_bibliography_handoff(): Unit =
      RAISE.invalidArgumentFault(
        "SmartDox bibliography metadata was not generated even though BoK source declares bibliography references or BibTeX sources. " +
          "Update the dox/SmartDox runtime used by cozy bok build, or run through a launcher that provides SmartDox bibliography support."
      )

  private def _source_declares_bibliography(config: BuildConfig): Boolean = {
    val root = config.project.resolve("src/main/doxsite")
    if (!Files.isDirectory(root))
      false
    else {
      val stream = Files.walk(root)
      try {
        stream.iterator.asScala.exists { path =>
          Files.isRegularFile(path) && _source_declares_bibliography(path, root)
        }
      } finally {
        stream.close()
      }
    }
  }

  private def _source_declares_bibliography(path: Path, root: Path): Boolean = {
    val relative = root.relativize(path).toString.replace(java.io.File.separatorChar, '/')
    val name = path.getFileName.toString.toLowerCase(java.util.Locale.ROOT)
    if (relative.startsWith("bibliography/") && (name.endsWith(".dox") || name.endsWith(".md") || name.endsWith(".markdown") || name.endsWith(".bib")))
      true
    else if (name.endsWith(".dox") || name.endsWith(".md") || name.endsWith(".markdown")) {
      val text = Files.readString(path, StandardCharsets.UTF_8)
      text.split("\\r?\\n").exists { line =>
        val trimmed = line.trim
        trimmed.startsWith("bibliography.refs") ||
          trimmed.startsWith("references.bibliography") ||
          trimmed == "bibliography:" ||
          trimmed == "references:" ||
          trimmed.contains("bib:[") ||
          trimmed.startsWith("bibid") ||
          trimmed.startsWith("bibids")
      }
    } else {
      false
    }
  }

  private def _resolve_cached_bibliography_entry(config: BuildConfig, entry: BibliographyEntry): BibliographyEntry = {
    val path = config.project.resolve("target/cozy-bok/bibliography/cache").resolve(s"${_safe_file_name(entry.id)}.bib")
    if (!Files.isRegularFile(path))
      entry
    else {
      val raw = Files.readString(path, StandardCharsets.UTF_8)
      BibliographyBibtexParser.parse(raw).map { fields =>
        val authors = fields.get("author").map(BibliographyBibtexParser.authors).getOrElse(entry.authors)
        val title =
          if (_is_curated_bibliography_source(entry))
            entry.title
          else
            fields.get("title").getOrElse(entry.title)
        entry.copy(
          entrytype = fields.getOrElse("type", entry.entrytype),
          title = title,
          summary = _resolved_bibliography_summary(entry),
          authors = if (authors.nonEmpty) authors else entry.authors,
          publishedat = entry.publishedat.orElse(fields.get("year")),
          publisher = entry.publisher.orElse(fields.get("publisher")),
          sourceurl = entry.sourceurl.orElse(fields.get("url")),
          citation = entry.citation.orElse(BibliographyBibtexParser.citation(fields)),
          identifiers = entry.identifiers.copy(doi = entry.identifiers.doi.orElse(fields.get("doi")), isbn = entry.identifiers.isbn.orElse(fields.get("isbn")), url = entry.identifiers.url.orElse(fields.get("url"))),
          key = entry.key.orElse(fields.get("id")),
          bibtex = entry.bibtex.copy(entrytype = entry.bibtex.entrytype.orElse(fields.get("type")), raw = Some(raw)),
          sourcekind = if (entry.sourcekind == "external-ref") "external-cache" else entry.sourcekind,
          needsresolution = false,
          quality = entry.quality.copy(missingcitation = false, missingsource = false, missingnarrative = entry.bodyhtml.trim.isEmpty, needscuration = entry.bodyhtml.trim.isEmpty)
        )
      }.getOrElse(entry)
    }
  }

  private def _resolved_bibliography_summary(entry: BibliographyEntry): Option[String] =
    entry.summary.filterNot(_is_unresolved_bibliography_placeholder)

  private def _is_unresolved_bibliography_placeholder(value: String): Boolean =
    value.trim.toLowerCase(Locale.ROOT).startsWith("unresolved bibliography reference")

  private def _bibliography_source_needs_local_bib(entry: BibliographyEntry): Boolean =
    _is_curated_bibliography_source(entry) &&
      entry.key.isEmpty &&
      entry.bibtex.raw.isEmpty &&
      entry.bibtex.sourceurl.isEmpty

  private def _is_curated_bibliography_source(entry: BibliographyEntry): Boolean = {
    val source = entry.sourcepath.toLowerCase(Locale.ROOT)
    source.endsWith(".bib.dox") || source.endsWith(".bib.md") || source.endsWith(".bib.markdown")
  }

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
    build(config, runner, _bibliography_fetcher(config.project, BibliographyHttpBibtexFetcher))

  def build(config: BuildConfig, runner: Runner, bibliographyfetcher: BibliographyBibtexFetcher): Unit = {
    val bibliographycache = _stash_bibliography_cache(config)
    _delete_directory(config.project.resolve("target"))
    _restore_bibliography_cache(config, bibliographycache)
    _delete_directory(config.project.resolve(s"doxsite-cache-${config.strategy}.d"))
    _delete_directory(config.doxsitePath)
    _delete_directory(config.antoraPath)
    _delete_directory(config.websitePath)
    if (config.arcadia.enabled)
      _delete_directory(config.arcadiaSitePath)
    runner.run(_dox_antora_command(config), config.project, _smartdox_toolchain_env(config))
    _run_antora(config, runner)
    runner.run(_dox_site_command(config), config.project, _smartdox_toolchain_env(config))
    _normalize_doxsite_output(config)
    _write_scenario_metadata(config)
    _ensure_bibliography_metadata_handoff(config)
    _write_effective_bibliography_metadata(config)
    _resolve_bibliography_service_entries(config, bibliographyfetcher)
    _write_effective_bibliography_metadata(config)
    _sync_effective_bibliography_fragments(config)
    _sync_effective_bibliography_rdf(config)
    _write_repository_car_metadata(config)
    _delete_directory(config.project.resolve(s"doxsite-cache-${config.strategy}.d"))
    if (config.arcadia.enabled) {
      runner.run(Vector("arcadia", "site", config.arcadia.source, config.arcadiaSite), config.project)
      _copy_directory(config.arcadiaSitePath, config.websitePath)
    }
    _write_bok_pages(config)
    if (config.strategy == "production") {
      runner.run(Vector("dox", "site-mark", "-strategy", "production", "-output.scope.policy", "all", config.source), config.project)
      if (config.directAssets.enabled)
        config.directAssets.items.foreach { item =>
          _copy_directory(config.project.resolve(item.source), config.project.resolve(item.destination))
        }
    }
  }

  private def _bibliography_fetcher(project: Path, fallback: BibliographyBibtexFetcher): BibliographyBibtexFetcher = {
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

  private def _require_workflow(config: WorkflowConfig): Boolean =
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
    val monthlylast = snapshots.groupBy(_.yearMonth).collect {
      case (yearmonth, xs) if yearmonth != current =>
        xs.last.path
    }.toSet
    val currentmonth = snapshots.filter(_.yearMonth == current).map(_.path).toSet
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

  def publishVideo(
    config: PublicationConfig,
    voicevox: CozyVideo.VoicevoxClient,
    videorunner: CozyVideo.VideoProcessRunner
  ): Vector[CozyVideoPublisher.PublishVideoResult] =
    _publish_video_packages(config, voicevox, videorunner)

  def publishProjects(config: PublicationConfig): Vector[CozyBokProjectPublisher.PublishProjectResult] =
    _publish_project_packages(config)

  def updatePublication(
    config: PublicationConfig,
    voicevox: CozyVideo.VoicevoxClient,
    videorunner: CozyVideo.VideoProcessRunner
  ): Vector[String] =
    publishVideo(config, voicevox, videorunner).map(_.video.name) ++
      publishProjects(config).map(_.project.name)

  def publish(
    config: PublicationConfig,
    runner: Runner,
    voicevox: CozyVideo.VoicevoxClient,
    videorunner: CozyVideo.VideoProcessRunner
  ): Unit = {
    val preflight = _publish_preflight(config)
    val planned = Vector(
      PublishStep("preflight", "succeeded", "Publish preflight passed."),
      PublishStep("update-publication", if (preflight.packageCount == 0) "skipped" else if (config.dryRun) "planned" else "pending", _publication_package_message(preflight)),
      PublishStep("build", if (config.dryRun) "planned" else "pending", s"strategy=${preflight.build.strategy}"),
      PublishStep("stage", preflight.stage.map(_ => if (config.dryRun) "planned" else "pending").getOrElse("skipped"), preflight.stage.map(_.command.mkString(" ")).getOrElse("No stage workflow configured.")),
      PublishStep("upload", if (config.dryRun) "planned" else "pending", preflight.upload.command.mkString(" "))
    )
    if (config.dryRun) {
      _write_publish_manifest(config, preflight, planned)
      _print_publish_plan(config, preflight)
    } else {
      var steps = Vector(PublishStep("preflight", "succeeded", "Publish preflight passed."))
      try {
        if (preflight.packageCount == 0) {
          steps :+= PublishStep("update-publication", "skipped", "No publication packages to publish.")
          _publish_status("update-publication", "skipped")
        } else {
          _publish_status("update-publication", "start")
          val results = updatePublication(config, voicevox, videorunner)
          steps :+= PublishStep("update-publication", "succeeded", s"${results.size} package(s) published.")
          _publish_status("update-publication", "succeeded")
        }
        _write_publish_manifest(config, preflight, steps)

        _publish_status("build", "start")
        build(preflight.build, runner)
        steps :+= PublishStep("build", "succeeded", s"strategy=${preflight.build.strategy}")
        _publish_status("build", "succeeded")
        _write_publish_manifest(config, preflight, steps)

        preflight.stage match {
          case Some(stage) =>
            _publish_status("stage", "start")
            runWorkflow(stage, runner)
            steps :+= PublishStep("stage", "succeeded", stage.command.mkString(" "))
            _publish_status("stage", "succeeded")
          case None =>
            steps :+= PublishStep("stage", "skipped", "No stage workflow configured.")
            _publish_status("stage", "skipped")
        }
        _write_publish_manifest(config, preflight, steps)

        _publish_status("upload", "start")
        runWorkflow(preflight.upload, runner)
        steps :+= PublishStep("upload", "succeeded", preflight.upload.command.mkString(" "))
        _publish_status("upload", "succeeded")
        _write_publish_manifest(config, preflight, steps)
      } catch {
        case e: Throwable =>
          val failed = _failed_step(steps)
          steps :+= PublishStep(failed, "failed", Option(e.getMessage).getOrElse(e.toString))
          _publish_status(failed, "failed")
          _write_publish_manifest(config, preflight, steps)
          throw e
      }
    }
  }

  private def _publish_preflight(config: PublicationConfig): PublishPreflight = {
    val stage = WorkflowConfig.create("stage", List(config.project.toString))
    val upload = WorkflowConfig.create("upload", List(config.project.toString))
    val optionalstage = if (stage.command.nonEmpty) Some(stage) else None
    _require_workflow(upload)
    val buildargs = List(
      config.project.toString,
      "--strategy", config.strategy,
      "--repository", config.repositoryPath.toString,
      "--publication", config.publicationPath.toString
    )
    val buildconfig = BuildConfig.create(buildargs)
    val videopackages = if (config.videoEnabled) _video_packages(config) else Vector.empty
    val projects = _project_packages(config)
    _validate_publish_path("publication", config.publicationPath, config.project, config.sourcepath)
    config.warehousePath match {
      case Some(warehousepath) if config.repositoryPath == warehousepath.resolve("repository").toAbsolutePath.normalize() =>
        _validate_publish_path("warehouse", warehousepath, config.project, config.sourcepath)
      case Some(warehousepath) =>
        _validate_publish_path("warehouse", warehousepath, config.project, config.sourcepath)
        _validate_publish_path("repository", config.repositoryPath, config.project, config.sourcepath)
      case None =>
        _validate_publish_path("repository", config.repositoryPath, config.project, config.sourcepath)
    }
    _reject_path_overlap("publication", config.publicationPath, "artifact repository", config.repositoryPath)
    _reject_path_overlap("publication", config.publicationPath, "website", buildconfig.websitePath)
    _reject_path_overlap("publication", config.publicationPath, "doxsite", buildconfig.doxsitePath)
    _reject_path_overlap("artifact repository", config.repositoryPath, "website", buildconfig.websitePath)
    _reject_path_overlap("artifact repository", config.repositoryPath, "doxsite", buildconfig.doxsitePath)
    PublishPreflight(optionalstage, upload, buildconfig, videopackages, projects)
  }

  private def _validate_publish_path(name: String, path: Path, project: Path, source: Path): Unit = {
    if (path == source || path.startsWith(source) || source.startsWith(path))
      RAISE.invalidArgumentFault(s"Invalid ${name} path overlaps BoK source: ${path}")
    val parent = Option(path.getParent).getOrElse(project)
    if (Files.exists(path) && !Files.isDirectory(path))
      RAISE.invalidArgumentFault(s"Invalid ${name} path is not a directory: ${path}")
    if (!Files.exists(path) && !Files.exists(parent))
      RAISE.invalidArgumentFault(s"Invalid ${name} path parent does not exist: ${parent}")
    if (Files.exists(parent) && !Files.isWritable(parent))
      RAISE.invalidArgumentFault(s"Invalid ${name} path parent is not writable: ${parent}")
    if (Files.exists(path) && !Files.isWritable(path))
      RAISE.invalidArgumentFault(s"Invalid ${name} path is not writable: ${path}")
  }

  private def _reject_path_overlap(leftname: String, left: Path, rightname: String, right: Path): Unit =
    if ({
      val leftpath = left.toAbsolutePath.normalize()
      val rightpath = right.toAbsolutePath.normalize()
      leftpath == rightpath || leftpath.startsWith(rightpath) || rightpath.startsWith(leftpath)
    })
      RAISE.invalidArgumentFault(s"Invalid ${leftname}/${rightname} path overlap: ${left.toAbsolutePath.normalize()} / ${right.toAbsolutePath.normalize()}")

  private def _failed_step(steps: Vector[PublishStep]): String =
    if (!steps.exists(_.name == "update-publication"))
      "update-publication"
    else if (!steps.exists(_.name == "build"))
      "build"
    else if (!steps.exists(_.name == "stage"))
      "stage"
    else if (!steps.exists(_.name == "upload"))
      "upload"
    else
      "publish"

  private def _publish_status(step: String, status: String): Unit =
    println(s"bok publish: ${step}: ${status}")

  private def _print_publish_plan(config: PublicationConfig, preflight: PublishPreflight): Unit = {
    println("bok publish dry-run")
    println(s"project: ${config.project}")
    println(s"publication: ${config.publicationPath}")
    config.warehousePath.foreach(path => println(s"warehouse: ${path}"))
    println(s"repository: ${config.repositoryPath}")
    println(s"strategy: ${config.strategy}")
    println("steps:")
    println(s"- update-publication: ${_publication_package_message(preflight)}")
    println(s"- build: strategy=${preflight.build.strategy}")
    println(s"- stage: ${preflight.stage.map(_.command.mkString(" ")).getOrElse("skipped")}")
    println(s"- upload: ${preflight.upload.command.mkString(" ")}")
    println(s"manifest: ${config.manifestPath}")
  }

  private def _write_publish_manifest(
    config: PublicationConfig,
    preflight: PublishPreflight,
    steps: Vector[PublishStep]
  ): Unit = {
    Files.createDirectories(config.manifestPath.getParent)
    val json = Json.obj(
      "schema" -> Json.fromString("cozy.bok.publish-manifest.v1"),
      "project" -> Json.fromString(config.project.toString),
      "source" -> Json.fromString(config.sourcepath.toString),
      "publication" -> Json.fromString(config.publicationPath.toString),
      "warehouse" -> Json.fromString(config.warehousePath.map(_.toString).getOrElse("")),
      "repository" -> Json.fromString(config.repositoryPath.toString),
      "strategy" -> Json.fromString(config.strategy),
      "dryRun" -> Json.fromBoolean(config.dryRun),
      "force" -> Json.fromBoolean(config.force),
      "videoEnabled" -> Json.fromBoolean(config.videoEnabled),
      "videoPackages" -> Json.fromValues(preflight.videopackages.map(x => Json.fromString(x.toString))),
      "projectPackages" -> Json.fromValues(preflight.projectpackages.map(x => Json.fromString(x.toString))),
      "publicationArtifacts" -> Json.fromValues(_publication_packages(preflight).map(x => Json.obj(
        "sourcePackage" -> Json.fromString(x.toString),
        "registryRoot" -> Json.fromString(config.publicationPath.toString),
        "warehouseRoot" -> Json.fromString(config.warehousePath.map(_.toString).getOrElse("")),
        "repositoryRoot" -> Json.fromString(config.repositoryPath.toString)
      ))),
      "buildCommands" -> Json.arr(
        Json.fromValues(_dox_antora_command(preflight.build).map(Json.fromString)),
        Json.fromValues(_dox_site_command(preflight.build).map(Json.fromString))
      ),
      "buildCommand" -> Json.fromValues(_dox_site_command(preflight.build).map(Json.fromString)),
      "stageCommand" -> Json.fromValues(preflight.stage.toVector.flatMap(_.command).map(Json.fromString)),
      "uploadCommand" -> Json.fromValues(preflight.upload.command.map(Json.fromString)),
      "steps" -> Json.fromValues(steps.map(_publish_step_json))
    )
    Files.writeString(config.manifestPath, json.spaces2, StandardCharsets.UTF_8)
  }

  private def _publish_step_json(step: PublishStep): Json =
    Json.obj(
      "name" -> Json.fromString(step.name),
      "status" -> Json.fromString(step.status),
      "message" -> Json.fromString(step.message)
    )

  private def _publish_video_packages(
    config: PublicationConfig,
    voicevox: CozyVideo.VoicevoxClient,
    videorunner: CozyVideo.VideoProcessRunner
  ): Vector[CozyVideoPublisher.PublishVideoResult] =
    if (!config.videoEnabled)
      Vector.empty
    else
      _video_packages(config).map { packagedir =>
        CozyVideoPublisher.publish(
          CozyVideoPublisher.PublishVideoConfig(
            packagedir,
            config.publicationPath,
            config.artifactBasePath,
            config.version,
            config.force,
            Some(config.repositoryPath)
          ),
          voicevox,
          videorunner
        )
      }

  private def _publish_project_packages(config: PublicationConfig): Vector[CozyBokProjectPublisher.PublishProjectResult] = {
    val bokconfig = _load_config(config.project)
    _project_packages(config).map { packagedir =>
      CozyBokProjectPublisher.publish(
        CozyBokProjectPublisher.PublishProjectConfig(
          packagedir,
          config.publicationPath,
          config.artifactBasePath,
          config.version,
          config.force,
          config.project,
          bokconfig,
          Some(config.repositoryPath)
        )
      )
    }
  }

  private def _video_packages(config: PublicationConfig): Vector[Path] = {
    if (!Files.isDirectory(config.sourcepath))
      RAISE.invalidArgumentFault(s"Missing BoK source directory: ${config.sourcepath}")
    val stream = Files.walk(config.sourcepath)
    try {
      val dirs = stream.iterator.asScala.toVector.filter(Files.isDirectory(_)).map(_.toAbsolutePath.normalize())
      dirs.find(_.getFileName.toString.endsWith(".video.d")).foreach { path =>
        RAISE.invalidArgumentFault(s"*.video.d is reserved for generated/work directories: $path")
      }
      dirs.filter(_.getFileName.toString.endsWith(".video")).sortBy(_.toString).map { path =>
        if (!_has_video_descriptor(path))
          RAISE.invalidArgumentFault(s"Missing video descriptor in .video package: $path")
        path
      }
    } finally {
      stream.close()
    }
  }

  private def _has_video_descriptor(path: Path): Boolean =
    Vector("video.yaml", "video.yml", "video.json").exists(x => Files.isRegularFile(path.resolve(x)))

  private def _project_packages(config: PublicationConfig): Vector[Path] = {
    if (!Files.isDirectory(config.sourcepath))
      RAISE.invalidArgumentFault(s"Missing BoK source directory: ${config.sourcepath}")
    _project_package_dirs(config.sourcepath)
  }

  private def _project_package_dirs(sourcepath: Path): Vector[Path] = {
    if (!Files.isDirectory(sourcepath))
      return Vector.empty
    val projects = sourcepath.resolve("projects").toAbsolutePath.normalize()
    val stream = Files.walk(sourcepath)
    try {
      val dirs = stream.iterator.asScala.toVector.filter(Files.isDirectory(_)).map(_.toAbsolutePath.normalize())
      dirs.find(path => path.getFileName.toString.endsWith(".car-product") || path.getFileName.toString.endsWith(".car-product.d")).foreach { path =>
        RAISE.invalidArgumentFault(s".car-product source packages are no longer supported. Use src/main/doxsite/projects/<category>/<slug>: $path")
      }
      if (!Files.isDirectory(projects)) {
        Vector.empty
      } else {
        val projectdirs = dirs.filter { path =>
          path.startsWith(projects) && path != projects && projects.relativize(path).getNameCount == 2
        }.sortBy(_.toString)
        dirs.filter(path => path.startsWith(projects) && _has_project_descriptor(path)).foreach { path =>
          val relative = projects.relativize(path)
          if (relative.getNameCount != 2)
            RAISE.invalidArgumentFault(s"Project knowledge package must be src/main/doxsite/projects/<category>/<slug>: $path")
        }
        projectdirs.map { path =>
          if (!_has_project_descriptor(path))
            RAISE.invalidArgumentFault(s"Missing project descriptor in project knowledge package: $path")
          val relative = projects.relativize(path)
          val category = relative.getName(0).toString
          if (category.endsWith(".d"))
            RAISE.invalidArgumentFault(s"Generated/work project category directories are not supported: $path")
          path
        }
      }
    } finally {
      stream.close()
    }
  }

  private def _has_project_descriptor(path: Path): Boolean =
    Vector("project.yaml", "project.yml", "project.json").exists(x => Files.isRegularFile(path.resolve(x)))

  private def _publication_packages(preflight: PublishPreflight): Vector[Path] =
    preflight.videopackages ++ preflight.projectpackages

  private def _publication_package_message(preflight: PublishPreflight): String =
    s"${preflight.videopackages.size} .video package(s), ${preflight.projectpackages.size} project package(s)"

  private def _run_antora(config: BuildConfig, runner: Runner): Unit = {
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

  private def _dox_antora_command(config: BuildConfig): Vector[String] =
    Vector("dox", "antora", "-strategy", config.strategy) ++
      _publication_options(config, includerdf = false) ++
      Vector(config.source)

  private def _dox_site_command(config: BuildConfig): Vector[String] =
    Vector("dox", "site", "-strategy", config.strategy, "-output.scope.policy", config.siteOutputScopePolicy) ++
      _publication_options(config, includerdf = true) ++
      Vector(config.source)

  private def _publication_options(config: BuildConfig, includerdf: Boolean): Vector[String] = {
    val base = Vector(
      "-publication", config.publication.publicationPath(config.project).toString
    )
    if (includerdf && config.publication.mergeRdf)
      base ++ Vector(
        "-publication.repository", config.publication.publicationRepositoryBasePath(config.project).toString,
        "-publication.rdf.missing.policy", config.publication.missingRdfPolicy
      )
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

  private def _smartdox_toolchain_env(config: BuildConfig): Map[String, String] =
    Map(
      "SMARTDOX_KROKI_DOCKER_IMAGE" -> config.dockerImage,
      "SMARTDOX_PDF_DOCKER_IMAGE" -> config.dockerImage,
      "SMARTDOX_COZY_TOOLCHAIN_IMAGE" -> config.dockerImage
    )

  private def _normalize_doxsite_output(config: BuildConfig): Unit =
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

  private def _copy_directory(source: Path, dest: Path): Unit =
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

  private def _write_bok_pages(config: BuildConfig): Unit =
    config.localeMode match {
      case LocaleMode.SingleLocaleRoot =>
        _write_home_page(config, config.websitePath, config.defaultLocale)
        _write_special_pages(config, config.websitePath, config.defaultLocale, writeLocalizedGlossaryIndexes = true)
        _write_category_pages(config, config.websitePath, config.defaultLocale)
      case LocaleMode.MultiLocaleSubdirs =>
        config.languages.foreach { lang =>
          val target = config.websitePath.resolve(lang)
          _write_home_page(config, target, lang)
          _write_special_pages(config, target, lang, writeLocalizedGlossaryIndexes = false)
          _write_category_pages(config, target, lang)
        }
    }

  private def _write_home_page(config: BuildConfig, target: Path, locale: String): Unit =
    {
      val page = target.resolve("index.html")
      _write_text(
        page,
      s"""<!doctype html>
         |<html lang="${_html_escape(locale)}">
         |<head>
         |  <meta charset="utf-8">
         |  <meta name="viewport" content="width=device-width, initial-scale=1">
         |  <title>${_html_escape(_uif(locale, "home.document.title", config.siteTitle))}</title>
         |${_site_css_links(config, page)}
         |</head>
         |<body class="article ${_html_escape(_dashboard_theme_class(config))}">
         |<header class="header">
         |  <nav class="navbar">
         |    <div class="navbar-brand">
         |      <a class="navbar-item" href="index.html">${_html_escape(config.siteTitle)}</a>
         |      <button class="navbar-burger" aria-controls="topbar-nav" aria-expanded="false" aria-label="Toggle main menu">
         |        <span></span>
         |        <span></span>
         |        <span></span>
         |      </button>
         |    </div>
         |    <div id="topbar-nav" class="navbar-menu">
         |      <div class="navbar-end">
         |        <a class="navbar-item" href="index.html">${_html_escape(_ui(locale, "nav.home"))}</a>
         |        ${_bok_nav_menu(config, locale, "")}
         |        ${_category_nav_menu(locale, _regular_category_summaries(config.sourcepath), "")}
         |      </div>
         |    </div>
         |  </nav>
         |</header>
         |<div class="body body-dashboard">
         |  <main class="article">
         |    <div class="toolbar" role="navigation">
         |      <button class="nav-toggle"></button>
         |      <a href="index.html" class="home-link is-current"></a>
         |      <nav class="breadcrumbs" aria-label="breadcrumbs">
         |        <ul>
         |          <li><a href="index.html">${_html_escape(config.siteTitle)}</a></li>
         |          <li>${_html_escape(_ui(locale, "dashboard"))}</li>
         |        </ul>
         |      </nav>
         |    </div>
         |    <div class="content">
         |      <article class="doc">
         |        ${_home_dashboard(config, locale)}
         |        ${_source_narrative_section(config, _source_document(config.sourcepath, "index"), locale)}
         |      </article>
         |    </div>
         |  </main>
         |</div>
         |</body>
         |</html>
         |""".stripMargin
      )
    }

  private def _write_category_pages(config: BuildConfig, target: Path, locale: String): Unit = {
    val categories = _category_contents(config.sourcepath)
    categories.foreach { category =>
      val page = target.resolve(category.slug).resolve("index.html")
      _write_text(
        page,
        _category_html_page(config, category, categories, locale, page)
      )
    }
  }

  private def _write_special_pages(
    config: BuildConfig,
    target: Path,
    locale: String,
    writeLocalizedGlossaryIndexes: Boolean
  ): Unit = {
    val categories = _category_contents(config.sourcepath)
    val terms = _terms(config)
    val glossarybody = _glossary_dashboard_body(config, categories, terms, _language_index_root_prefix(config), locale)
    val historyhref = _latest_history_year_page(target.resolve("history"))
    _write_category_index_page(config, target, locale, categories)
    _write_article_page(config, target, locale, categories)
    _write_project_pages(config, target, locale, categories)
    _write_repository_car_page(config, target, locale, categories)
    _write_rdf_page(config, target, locale, categories)
    _write_scenario_page(config, target, locale, categories)
    _write_bibliography_page(config, target, locale, categories)
    _write_tag_pages(config, target, locale, categories)
    _write_term_hub_pages(config, target, locale, categories, terms)
    _write_text(
      target.resolve("glossary").resolve("index.html"),
      _glossary_dedicated_page(
        config,
        categories,
        locale,
        target.resolve("glossary").resolve("index.html"),
        glossarybody,
        terms
      )
    )
    if (historyhref.isEmpty) {
      val historypage = target.resolve("history").resolve("index.html")
      _write_text(
        historypage,
        _special_html_page_with_toc(
          config,
          categories,
          locale,
          historypage,
          _ui(locale, "history.title"),
          _ui(locale, "history.description"),
          _history_dashboard_body(locale),
          Vector("dashboard" -> "Dashboard", "timeline" -> "Timeline", "operation-notes" -> "Operation Notes")
        )
      )
    }
    val manualpage = target.resolve("manual").resolve("index.html")
    val manualbody = _manual_body_with_anchors(
      _source_narrative_html(_manual_index(locale), "cozy-bok-manual.dox", locale)
    )
    _write_text(
      manualpage,
      _manual_html_page(
        config,
        categories,
        locale,
        manualpage,
        _ui(locale, "manual.title"),
        _ui(locale, "manual.description"),
        manualbody
      )
    )
    _write_manual_source_page(
      config,
      categories,
      locale,
      target,
      "local-rules",
      "Local Rules",
      "Project-local BoK operation rules."
    )
    _post_process_knowledge_pages(config, target, locale, categories)
    if (writeLocalizedGlossaryIndexes) {
      _write_text(
        target.resolve("ja").resolve("glossary").resolve("index.html"),
        _localized_glossary_index_page(config, categories, "ja")
      )
      _write_text(
        target.resolve("en").resolve("glossary").resolve("index.html"),
        _localized_glossary_index_page(config, categories, "en")
      )
    }
  }

  private def _post_process_knowledge_pages(
    config: BuildConfig,
    target: Path,
    locale: String,
    categories: Vector[CategoryContent]
  ): Unit = {
    _remove_category_index_nav_items(target, categories)
    _inject_antora_knowledge_tag_chips(config, target, locale)
  }

  private def _remove_category_index_nav_items(target: Path, categories: Vector[CategoryContent]): Unit =
    categories.foreach { category =>
      val dir = target.resolve(category.slug)
      if (Files.isDirectory(dir)) {
        val stream = Files.walk(dir)
        try {
          stream.iterator.asScala.toVector.
            filter(Files.isRegularFile(_)).
            filter(_.getFileName.toString.endsWith(".html")).
            filterNot(_.getFileName.toString == "index.html").
            foreach { page =>
              val content = Files.readString(page, StandardCharsets.UTF_8)
              val title = Pattern.quote(_html_escape(category.title))
              val regex = ("""(?s)\s*<li class="nav-item" data-depth="1">\s*<a class="nav-link" href="index\.html">""" + title + """</a>\s*</li>""").r
              val updated = regex.replaceAllIn(content, "")
              if (updated != content)
                _write_text(page, updated)
            }
        } finally {
          stream.close()
        }
      }
    }

  private def _inject_antora_knowledge_tag_chips(config: BuildConfig, target: Path, locale: String): Unit = {
    val tagsbyhref = _tag_index(config, locale).tags.flatMap { tag =>
      tag.refs.collect {
        case ref if _is_antora_knowledge_tag_ref(ref) => ref.href -> tag
      }
    }.groupBy(_._1).map {
      case (href, xs) => href -> xs.map(_._2).distinct.sortBy(_.key)
    }
    tagsbyhref.foreach {
      case (href, tags) =>
        val page = target.resolve(href)
        if (Files.isRegularFile(page)) {
          val content = Files.readString(page, StandardCharsets.UTF_8)
          if (!content.contains("bok-knowledge-tag-chip-list")) {
            val chips = _knowledge_tag_chips_for_entries(target, page, tags, locale)
            val updated = _insert_after_page_title(content, chips)
            if (updated != content)
              _write_text(page, updated)
          }
        }
    }
  }

  private def _is_antora_knowledge_tag_ref(ref: TagReference): Boolean =
    ref.kind == "article" || ref.kind == "document" || ref.kind == "term" || ref.kind == "scenario"

  private def _knowledge_tag_chips_for_entries(target: Path, page: Path, tags: Vector[TagEntry], locale: String): String = {
    val groups = tags.groupBy(_tag_parent_label).toVector.sortBy(_._1).map {
      case (namespace, entries) =>
        val links = entries.sortBy(_.key).map { tag =>
          val href = _relative_href(page, target.resolve(tag.publicpath))
          val label = tag.segments.lastOption.filter(_.nonEmpty).getOrElse(tag.label)
          s"""<a class="bok-knowledge-tag-leaf" href="${_html_escape(href)}">${_html_escape(label)}</a>"""
        }.mkString
        s"""<div class="bok-knowledge-tag-group"><span class="bok-knowledge-tag-namespace">${_html_escape(namespace)}</span><span class="bok-knowledge-tag-leaves">${links}</span></div>"""
    }.mkString
    s"""<div class="bok-knowledge-tag-bar bok-knowledge-tag-chip-list" aria-label="${_html_escape(_ui(locale, "tag.title"))}">${groups}</div>"""
  }

  private def _tag_parent_label(tag: TagEntry): String =
    tag.segments.dropRight(1).mkString(".") match {
      case "" => tag.namespace.getOrElse("tags")
      case x => x
    }

  private def _insert_after_page_title(content: String, html: String): String = {
    val heading = """(?s)(<h1 class="page"[^>]*>.*?</h1>)""".r
    heading.findFirstMatchIn(content).map { m =>
      heading.replaceFirstIn(content, Regex.quoteReplacement(m.group(1) + "\n" + html))
    }.getOrElse(content)
  }

  private def _insert_before_article_end(content: String, html: String): String = {
    val end = """(?s)</article>""".r
    end.findFirstMatchIn(content).map { _ =>
      end.replaceFirstIn(content, Regex.quoteReplacement(html + "\n</article>"))
    }.getOrElse(content + "\n" + html)
  }

  private def _write_rdf_page(config: BuildConfig, target: Path, locale: String, categories: Vector[CategoryContent]): Unit = {
    _copy_machine_metadata_artifacts(config, target)
    val page = target.resolve("rdf").resolve("index.html")
    _write_text(
      page,
      _rdf_dedicated_page(config, categories, locale, page)
    )
    val nodepage = target.resolve("rdf").resolve("node.html")
    _write_text(
      nodepage,
      _rdf_node_detail_page(config, categories, locale, nodepage)
    )
  }

  private def _write_category_index_page(
    config: BuildConfig,
    target: Path,
    locale: String,
    categories: Vector[CategoryContent]
  ): Unit = {
    val page = target.resolve("category").resolve("index.html")
    _write_text(
      page,
      _category_index_page(config, categories, locale, page)
    )
  }

  private def _category_index_page(
    config: BuildConfig,
    categories: Vector[CategoryContent],
    locale: String,
    page: Path
  ): String = {
    val dashboard = _dashboard(config)
    val articlecount = _source_article_count(config)
    val termcount = dashboard.map(_.counts.glossaryTermCount).getOrElse(_terms(config).size)
    val rdfcount = dashboard.map(_.rdf.tripleCount).getOrElse(0)
    s"""<!doctype html>
       |<html lang="${_html_escape(locale)}">
       |<head>
       |  <meta charset="utf-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1">
       |  <title>${_html_escape(_ui(locale, "dashboard.kpi.categories"))} - ${_html_escape(config.siteTitle)}</title>
       |${_site_css_links(config, page)}
       |</head>
       |<body class="article ${_html_escape(_support_dashboard_theme_class)}">
       |${_category_header(config, categories, locale)}
       |<div class="body body-dashboard bok-category-index-body">
       |  <main class="article bok-category-index-main">
       |    <div class="content">
       |      <article class="doc bok-category-index-doc">
       |        <section class="bok-dashboard-shell bok-category-index-dashboard" id="dashboard">
       |          ${_dashboard_hero(
                    _ui(locale, "dashboard.kpi.categories"),
                    _ui(locale, "dashboard.kpi.categories.note"),
                    Vector(
                      _ui(locale, "dashboard.kpi.categories") -> categories.size.toString,
                      _ui(locale, "dashboard.kpi.articles") -> articlecount.toString,
                      _ui(locale, "dashboard.kpi.terms") -> termcount.toString,
                      _ui(locale, "dashboard.kpi.rdf.triples") -> rdfcount.toString
                    )
                  )}
       |          <div class="bok-dashboard container-fluid bok-dashboard-command-center">
       |            <div class="row g-3">
       |              ${_dashboard_card("col-12", "bok-card-matrix", _ui(locale, "dashboard.card.category.matrix"), dashboard.map(_category_matrix_body(config, locale, _, "../")).getOrElse(_category_source_matrix_body(locale, categories, "../")), Vector("reader", "contributor", "project_manager"))}
       |            </div>
       |          </div>
       |        </section>
       |      </article>
       |    </div>
       |  </main>
       |</div>
       |</body>
       |</html>
       |""".stripMargin
  }

  private def _write_article_page(
    config: BuildConfig,
    target: Path,
    locale: String,
    categories: Vector[CategoryContent]
  ): Unit = {
    val page = target.resolve("articles").resolve("index.html")
    _write_text(
      page,
      _article_index_page(config, categories, locale, page)
    )
  }

  private def _article_index_page(
    config: BuildConfig,
    categories: Vector[CategoryContent],
    locale: String,
    page: Path
  ): String = {
    val articles = _article_index_items(config, categories, locale)
    val articlecount = articles.size
    val categorycount = articles.map(_.categorySlug).distinct.size
    s"""<!doctype html>
       |<html lang="${_html_escape(locale)}">
       |<head>
       |  <meta charset="utf-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1">
       |  <title>${_html_escape(_ui(locale, "dashboard.kpi.articles"))} - ${_html_escape(config.siteTitle)}</title>
       |${_site_css_links(config, page)}
       |</head>
       |<body class="article ${_html_escape(_support_dashboard_theme_class)}">
       |${_category_header(config, categories, locale)}
       |<div class="body body-dashboard bok-article-body">
       |  <main class="article bok-article-main">
       |    <div class="content">
       |      <article class="doc bok-article-doc">
       |        <section class="bok-dashboard-shell bok-article-dashboard" id="dashboard">
       |          ${_dashboard_hero(
                    _ui(locale, "dashboard.kpi.articles"),
                    _ui(locale, "dashboard.kpi.articles.note"),
                    Vector(
                      _ui(locale, "dashboard.kpi.articles") -> articlecount.toString,
                      _ui(locale, "dashboard.kpi.categories") -> categorycount.toString
                    )
                  )}
       |          ${_article_dashboard_body(locale, articles)}
       |        </section>
       |      </article>
       |    </div>
       |  </main>
       |</div>
       |</body>
       |</html>
       |""".stripMargin
  }

  private def _article_index_items(config: BuildConfig, categories: Vector[CategoryContent], locale: String): Vector[ArticleIndexItem] = {
    val sourceitems = categories.flatMap { category =>
      category.articles.map { article =>
        ArticleIndexItem(
          category.slug,
          category.title,
          s"../${category.slug}/${article.href}",
          article.title,
          article.brief,
          TermExtraction.empty
        )
      }
    }
    val sourcehrefs = sourceitems.map(_.hrefFromArticleIndex).toSet
    val categorytitles = categories.map(x => x.slug -> x.title).toMap
    val fragmentitems = _document_fragment_index(config).toVector.flatMap(_.fragments).filter { fragment =>
      fragment.locale == locale &&
      fragment.category.nonEmpty &&
      _is_article_fragment_public_path(fragment.publicpath) &&
      !fragment.category.exists(category => _is_category_index_fragment_public_path(fragment.publicpath, category))
    }.map { fragment =>
      val category = fragment.category.get
      ArticleIndexItem(
        category,
        categorytitles.getOrElse(category, _titleize(category)),
        s"../${fragment.publicpath}",
        fragment.effectiveHeadline.getOrElse(_titleize(_source_document_stem(Paths.get(fragment.publicpath).getFileName.toString))),
        fragment.effectiveBrief.getOrElse(""),
        fragment.termExtraction
      )
    }.filterNot(x => sourcehrefs.contains(x.hrefFromArticleIndex))
    (sourceitems ++ fragmentitems).sortBy(x => (x.categorySlug, x.title, x.hrefFromArticleIndex))
  }

  private def _is_category_index_fragment_public_path(value: String, category: String): Boolean =
    value.stripPrefix("/") == s"${category}/index.html"

  private def _is_article_fragment_public_path(value: String): Boolean = {
    val path = value.stripPrefix("/")
    path.endsWith(".html") &&
    !path.startsWith("glossary/") &&
    !path.startsWith("history/") &&
    !path.startsWith("manual/") &&
    !path.startsWith("scenario/") &&
    !path.startsWith("scenarios/") &&
    !path.startsWith("projects/") &&
    !path.startsWith("bibliography/") &&
    path != "index.html"
  }

  private def _article_dashboard_body(locale: String, articles: Vector[ArticleIndexItem]): String = {
    val progress = _term_extraction_progress_card(
      locale,
      articles.count(x => x.termExtraction.isPlanned && _is_term_extraction_done(x.termExtraction)),
      articles.count(_.termExtraction.isPlanned)
    )
    val articlecards = articles.map { article =>
      val brief = if (article.brief.trim.isEmpty) "" else s"""<p>${_html_escape(article.brief)}</p>"""
      s"""<article class="bok-article-tile" data-article-category="${_html_escape(article.categorySlug)}">
         |  <div class="bok-article-tile-head">
         |    <span class="badge bok-badge-info">${_html_escape(article.categoryTitle)}</span>
         |  </div>
         |  <h3><a href="${_html_escape(article.hrefFromArticleIndex)}">${_html_escape(article.title)}</a></h3>
         |  ${brief}
         |</article>""".stripMargin
    }
    val body =
      if (articlecards.isEmpty)
        s"""<p class="bok-card-muted">${_html_escape(_ui(locale, "dashboard.article.empty"))}</p>"""
      else
        articlecards.mkString("""<div class="bok-article-grid">""", "\n", "</div>")
    s"""<div class="bok-dashboard container-fluid bok-dashboard-command-center">
       |  <div class="row g-3">
       |    ${_dashboard_card("col-12 col-xl-4", "bok-card-kpi bok-card-term-extraction-progress", _ui(locale, "term.extraction.progress.title"), progress, Vector("contributor", "project_manager"))}
       |    ${_dashboard_card("col-12", "bok-card-map bok-card-article-map", _ui(locale, "dashboard.card.article.map"), body, Vector("reader", "contributor", "project_manager"))}
       |  </div>
       |</div>
       |<script>
       |(() => {
       |  const category = new URLSearchParams(window.location.search).get('category');
       |  if (!category) return;
       |  document.querySelectorAll('[data-article-category]').forEach((item) => {
       |    item.hidden = item.dataset.articleCategory !== category;
       |  });
       |})();
       |</script>""".stripMargin
  }

  private def _write_scenario_page(
    config: BuildConfig,
    target: Path,
    locale: String,
    categories: Vector[CategoryContent]
  ): Unit = {
    val scenarios = _scenario_index(config).map(_.scenarios).getOrElse(Vector.empty)
    val page = target.resolve("scenarios").resolve("index.html")
    _write_text(
      page,
      _scenario_dedicated_page(
        config,
        categories,
        locale,
        page,
        scenarios
      )
    )
  }

  private def _write_bibliography_page(
    config: BuildConfig,
    target: Path,
    locale: String,
    categories: Vector[CategoryContent]
  ): Unit = {
    val entries = _bibliography_index(config).map(_.entries).getOrElse(Vector.empty)
    val page = target.resolve("bibliography").resolve("index.html")
    _write_text(
      page,
      _bibliography_dedicated_page(
        config,
        categories,
        locale,
        page,
        entries
      )
    )
    _write_bibliography_entry_pages(config, target, locale, categories, entries)
  }

  private def _write_bibliography_entry_pages(
    config: BuildConfig,
    target: Path,
    locale: String,
    categories: Vector[CategoryContent],
    entries: Vector[BibliographyEntry]
  ): Unit =
    entries.foreach { entry =>
      val page = target.resolve(entry.publicpath)
      _write_text(
        page,
        _bibliography_entry_html_page(
          config,
          categories,
          locale,
          page,
          entry
        )
      )
    }

  private def _write_tag_pages(
    config: BuildConfig,
    target: Path,
    locale: String,
    categories: Vector[CategoryContent]
  ): Unit = {
    val index = _tag_index(config, locale)
    val page = target.resolve("tags").resolve("index.html")
    _write_text(
      page,
      _tag_dedicated_page(config, categories, locale, page, index, None)
    )
    index.namespaces.foreach { namespace =>
      val namespacepage = target.resolve("tags").resolve(namespace).resolve("index.html")
      val namespaceindex = TagIndex(index.tags.filter(_.namespace.contains(namespace)))
      _write_text(
        namespacepage,
        _tag_dedicated_page(config, categories, locale, namespacepage, namespaceindex, None)
      )
    }
    index.tags.foreach { tag =>
      val tagpage = target.resolve(tag.publicpath)
      if (tag.sourcepath.exists(_.startsWith("tags/")) && Files.isRegularFile(tagpage))
        _post_process_tag_antora_page(config, tagpage, locale, tag)
      else
        _write_text(
          tagpage,
          _tag_dedicated_page(config, categories, locale, tagpage, TagIndex(Vector(tag)), Some(tag))
        )
    }
  }

  private def _post_process_tag_antora_page(
    config: BuildConfig,
    page: Path,
    locale: String,
    tag: TagEntry
  ): Unit = {
    val content = Files.readString(page, StandardCharsets.UTF_8)
    val withproperties =
      if (content.contains("bok-tag-detail-properties"))
        content
      else
        _insert_after_page_title(content, _tag_detail_properties(tag, locale))
    val updated =
      if (withproperties.contains("bok-tag-detail-links"))
        withproperties
      else {
        val links =
          s"""<section class="bok-tag-detail-section bok-tag-detail-links" id="links">
             |  <h2>${_html_escape(_ui(locale, "tag.detail.links"))}</h2>
             |  ${_tag_rdf_link(config, page, locale, tag)}
             |  ${_tag_refs_body(config, page, locale, tag.refs)}
             |</section>""".stripMargin
        _insert_before_article_end(withproperties, links)
      }
    if (updated != content)
      _write_text(page, updated)
  }

  private def _write_project_pages(
    config: BuildConfig,
    target: Path,
    locale: String,
    categories: Vector[CategoryContent]
  ): Unit = {
    val projects = _resolved_project_packages(config)
    _write_project_index_page(config, target, locale, categories, projects)
    projects.foreach { project =>
      val articlebody = _source_narrative_html(project.article, locale)
      val page = target.resolve(project.publicationpath).resolve("index.html")
      val cmlbody = _project_model_terms_html(config, locale, project, page, target)
      val surfacebody = _project_component_surface_html(locale, project)
      val dashboardbody = _project_detail_dashboard(config, locale, project)
      val repositorybody = _project_repository_car_html(config, locale, project, page, target)
      val narrativebody = _project_narrative_html(locale, articlebody)
      val pagebody =
        s"""${dashboardbody}
           |${surfacebody}
           |${cmlbody}
           |${repositorybody}
           |${narrativebody}""".stripMargin
      _write_text(
        page,
        _project_html_page(
          config,
          categories,
          locale,
          page,
          target,
          project.title,
          project.summary.getOrElse("CAR project."),
          project.tags,
          Some(_project_category(config, project)),
          pagebody
        )
      )
    }
  }

  private def _resolved_project_packages(config: BuildConfig): Vector[CozyBokProjectPublisher.ResolvedBokProject] = {
    val bokconfig = _load_config(config.project)
    _project_package_dirs(config.sourcepath).map { packagedir =>
      val publishconfig = CozyBokProjectPublisher.PublishProjectConfig(
        packagedir,
        config.publication.publicationPath(config.project),
        config.publication.artifactBasePath(config.project),
        None,
        force = false,
        config.project,
        bokconfig,
        Some(config.publication.repositoryPath(config.project))
      )
      CozyBokProjectPublisher.resolve(publishconfig)
    }
  }

  private def _write_project_index_page(
    config: BuildConfig,
    target: Path,
    locale: String,
    categories: Vector[CategoryContent],
    projects: Vector[CozyBokProjectPublisher.ResolvedBokProject]
  ): Unit = {
    val page = target.resolve("projects/index.html")
    _write_text(
      page,
      _project_index_page(config, categories, locale, page, target, projects)
    )
  }

  private def _project_index_page(
    config: BuildConfig,
    categories: Vector[CategoryContent],
    locale: String,
    page: Path,
    target: Path,
    projects: Vector[CozyBokProjectPublisher.ResolvedBokProject]
  ): String =
    s"""<!doctype html>
       |<html lang="${_html_escape(locale)}">
       |<head>
       |  <meta charset="utf-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1">
       |  <title>${_html_escape(_ui(locale, "project.title"))} - ${_html_escape(config.siteTitle)}</title>
       |${_site_css_links(config, page)}
       |</head>
       |<body class="article ${_html_escape(_support_dashboard_theme_class)}">
       |${_category_header(config, categories, locale)}
       |<div class="body body-dashboard bok-project-body">
       |  <main class="article bok-project-main">
       |    <div class="content">
       |      <article class="doc bok-project-doc">
       |        <section class="bok-dashboard-shell bok-project-dashboard" id="dashboard">
       |          ${_dashboard_hero(
                    _ui(locale, "project.title"),
                    _ui(locale, "project.description"),
                    Vector(
                      _ui(locale, "project.metric.total") -> projects.size.toString,
                      _ui(locale, "project.metric.categories") -> projects.map(_project_category(config, _)).distinct.size.toString
                    )
                  )}
       |          ${_project_dashboard_body(config, locale, page, target, projects)}
       |        </section>
       |      </article>
       |    </div>
       |  </main>
       |</div>
       |</body>
       |</html>
       |""".stripMargin

  private def _project_dashboard_body(
    config: BuildConfig,
    locale: String,
    page: Path,
    target: Path,
    projects: Vector[CozyBokProjectPublisher.ResolvedBokProject]
  ): String =
    if (projects.isEmpty)
      s"""<div class="bok-dashboard container-fluid bok-dashboard-command-center">
         |  <div class="row g-3">
         |    ${_dashboard_card("col-12", "bok-card-map bok-card-project-map", _ui(locale, "project.title"), s"""<p class="bok-card-muted">${_html_escape(_ui(locale, "project.empty"))}</p>""", Vector("reader", "contributor", "project_manager"))}
         |  </div>
         |</div>""".stripMargin
    else {
      val categories = projects.groupBy(x => _project_category(config, x)).toVector.sortBy(_._1)
      val artifactrecords = projects.flatMap(project => _project_artifact_records(config, project))
      val plannedartifactcount = artifactrecords.size
      val currentartifactcount = artifactrecords.count(_.exists)
      val missingartifactcount = plannedartifactcount - currentartifactcount
      val plannedartifacttypes = _project_artifact_type_counts(artifactrecords)
      val modecounts = _project_mode_counts(projects)
      val cmlelementcount = projects.flatMap(_.cml.toVector).map(_.modelElements.size).sum
      val cmlelementkinds = _project_cml_element_kind_counts(projects)
      val metrics = categories.map {
        case (category, xs) =>
          s"""<div class="bok-metric-card"><div class="bok-metric-label">${_html_escape(category)}</div><div class="bok-metric-value">${xs.size}</div><div class="bok-metric-note">${_html_escape(_ui(locale, "project.metric.note"))}</div></div>"""
      }.mkString("""<div class="bok-dashboard-grid bok-project-metrics">""", "", "</div>")
      val health = (Vector(
        s"""<li><span>${_html_escape(_ui(locale, "project.label.distribution.artifacts"))}${_project_artifact_availability_counts_html(locale, currentartifactcount, missingartifactcount)}</span><b>${plannedartifactcount}</b></li>""",
        s"""<li><span>${_html_escape(_ui(locale, "project.label.artifact.kind.counts"))}${_project_artifact_type_counts_html(locale, plannedartifacttypes)}</span><b>${plannedartifactcount}</b></li>"""
      ) :+ s"""<li><span>${_html_escape(_ui(locale, "project.label.placement"))}${_project_mode_counts_html(locale, modecounts)}</span><b>${projects.size}</b></li>""" :+ s"""<li><span>${_html_escape(_ui(locale, "project.label.cml.terms"))}${_project_cml_element_kind_counts_html(locale, cmlelementkinds)}</span><b>${cmlelementcount}</b></li>""").mkString("""<ul class="bok-project-health-list">""", "", "</ul>")
      val items = projects.take(30).map { project =>
        val category = _project_category(config, project)
        val summary = project.summary.map(x => s"""<p>${_html_escape(x)}</p>""").getOrElse("")
        val href = _relative_href(page, target.resolve(project.publicationpath).resolve("index.html"))
        val artifactrecords = _project_artifact_records(config, project)
        val plannedartifactcount = artifactrecords.size
        val currentartifactcount = artifactrecords.count(_.exists)
        val missingartifactcount = plannedartifactcount - currentartifactcount
        val plannedartifacttypes = _project_artifact_type_counts(artifactrecords)
        val artifactstatus = _project_artifact_status(config, project)
        val cmlcount = project.cml.map(_.modelElements.size).getOrElse(0)
        val cmlkinds = _project_cml_element_kind_counts(Vector(project))
        s"""<article class="bok-project-tile" data-project-category="${_html_escape(category)}">
           |  <div class="bok-project-tile-head">
           |    <span>${_html_escape(project.descriptor.project.projecttype)}</span>
           |    <code>${_html_escape(project.module)}</code>
           |  </div>
           |  <h3><a href="${_html_escape(href)}">${_html_escape(project.title)}</a></h3>
           |  ${summary}
           |  <div class="bok-project-meta"><span>${_html_escape(project.version)}</span><span>${_html_escape(_ui(locale, "project.label.placement"))}: ${_html_escape(_project_mode_label(locale, project.projectmode))}</span><span>${_html_escape(_ui(locale, "project.label.distribution.artifacts"))} ${plannedartifactcount}</span><span class="bok-project-status bok-project-status-${_html_escape(_tag_segment(artifactstatus))}">${_html_escape(_ui(locale, "project.label.distributed.artifacts"))} ${currentartifactcount}</span><span>${_html_escape(_ui(locale, "project.label.undistributed.artifacts"))} ${missingartifactcount}</span><span>${_html_escape(_ui(locale, "project.label.cml.terms"))} ${cmlcount}</span></div>
           |  <div class="bok-project-artifact-kind-line"><span>${_html_escape(_ui(locale, "project.label.artifact.kind.counts"))}</span>${_project_artifact_type_counts_html(locale, plannedartifacttypes)}</div>
           |  ${_project_cml_element_kind_counts_inline_html(locale, cmlkinds)}
           |</article>""".stripMargin
      }.mkString("""<div class="bok-project-grid">""", "", "</div>")
      s"""<div class="bok-dashboard container-fluid bok-dashboard-command-center">
         |  <div class="row g-3">
         |    ${_dashboard_card("col-12 col-xl-6", "bok-card-kpi bok-card-project-summary", _ui(locale, "project.metric.summary"), metrics, Vector("reader", "contributor", "project_manager"))}
         |    ${_dashboard_card("col-12 col-xl-6", "bok-card-map bok-card-project-health", _ui(locale, "project.card.health"), health, Vector("reader", "contributor", "project_manager"))}
         |    ${_dashboard_card("col-12", "bok-card-map bok-card-project-map", _ui(locale, "project.title"), items, Vector("reader", "contributor", "project_manager"))}
         |  </div>
         |</div>
         |<script>
         |(() => {
         |  const category = new URLSearchParams(window.location.search).get('category');
         |  if (!category) return;
         |  document.querySelectorAll('[data-project-category]').forEach((item) => {
         |    item.hidden = item.dataset.projectCategory !== category;
         |  });
         |})();
         |</script>""".stripMargin
    }

  private final case class RepositoryCarIndex(
    entries: Vector[RepositoryCarEntry],
    diagnostics: Vector[RepositoryCarDiagnostic]
  ) {
    def toJsonString: String =
      Json.obj(
        "entries" -> entries.map(_.toJson).asJson,
        "diagnostics" -> diagnostics.map(_.toJson).asJson
      ).spaces2 + "\n"
  }

  private final case class RepositoryCarDiagnostic(
    code: String,
    artifactid: String,
    version: Option[String],
    metadataname: Option[String],
    metadataversion: Option[String],
    projectpath: Option[String],
    projecttitle: Option[String]
  ) {
    def toJson: Json =
      Json.obj(
        "code" -> Json.fromString(code),
        "artifact_id" -> Json.fromString(artifactid),
        "version" -> version.asJson,
        "metadata_name" -> metadataname.asJson,
        "metadata_version" -> metadataversion.asJson,
        "project_path" -> projectpath.asJson,
        "project_title" -> projecttitle.asJson
      )
  }

  private final case class RepositoryCarEntry(
    artifactid: String,
    aliases: Vector[String],
    tags: Vector[String],
    terms: Vector[String],
    status: Option[String],
    recommended: Option[String],
    lateststable: Option[String],
    latestsnapshot: Option[String],
    sourcepath: String,
    sidecars: RepositoryCarSidecars,
    versions: Vector[RepositoryCarVersion]
  ) {
    def title: String = artifactid
    def publicPath: String = s"repository/car/${artifactid}/index.html"
    def versionPublicPath(version: RepositoryCarVersion): String =
      s"repository/car/${artifactid}/${version.version}.html"
    def effectiveVersion: Option[String] =
      recommended.orElse(lateststable).orElse(latestsnapshot).orElse(versions.headOption.map(_.version))
    def toJson: Json =
      Json.obj(
        "artifact_id" -> Json.fromString(artifactid),
        "aliases" -> aliases.asJson,
        "tags" -> tags.asJson,
        "terms" -> terms.asJson,
        "status" -> status.asJson,
        "recommended" -> recommended.asJson,
        "latest_stable" -> lateststable.asJson,
        "latest_snapshot" -> latestsnapshot.asJson,
        "source_path" -> Json.fromString(sourcepath),
        "sidecars" -> sidecars.toJson,
        "versions" -> versions.map(_.toJson).asJson
      )
    def toJsonString: String = toJson.spaces2 + "\n"
  }

  private final case class RepositoryCarSidecars(
    cml: Option[String],
    modelmetadatajson: Option[String],
    modelmetadatayaml: Option[String]
  ) {
    def paths: Vector[String] = Vector(cml, modelmetadatajson, modelmetadatayaml).flatten
    def toJson: Json =
      Json.obj(
        "cml" -> cml.asJson,
        "model_metadata_json" -> modelmetadatajson.asJson,
        "model_metadata_yaml" -> modelmetadatayaml.asJson
      )
  }

  private final case class RepositoryCarVersion(
    version: String,
    channel: Option[String],
    status: Option[String],
    component: Option[String],
    publishedat: Option[String],
    file: Option[String],
    runtimecncfminimum: Option[String],
    runtimecncfmaximum: Option[String],
    runtimecncftested: Vector[String],
    checksumsha256: Option[String],
    componentdescriptor: Option[Json],
    abimanifest: Option[Json],
    archiveavailable: Boolean
  ) {
    def toJson: Json =
      Json.obj(
        "version" -> Json.fromString(version),
        "channel" -> channel.asJson,
        "status" -> status.asJson,
        "component" -> component.asJson,
        "published_at" -> publishedat.asJson,
        "file" -> file.asJson,
        "runtime" -> Json.obj(
          "cncf" -> Json.obj(
            "minimum" -> runtimecncfminimum.asJson,
            "maximum" -> runtimecncfmaximum.asJson,
            "tested" -> runtimecncftested.asJson
          )
        ),
        "checksum" -> Json.obj("sha256" -> checksumsha256.asJson),
        "component_descriptor" -> componentdescriptor.asJson,
        "abi_manifest" -> abimanifest.asJson
      )
  }

  private final case class RepositoryCarArchiveMetadata(
    componentdescriptor: Option[Json],
    abimanifest: Option[Json],
    available: Boolean
  )

  private final case class RepositoryCarMetadataCoordinate(
    name: Option[String],
    version: Option[String],
    expectedname: String
  )

  private def _write_repository_car_metadata(config: BuildConfig): Unit = {
    val index = _repository_car_index(config)
    _write_text(
      config.doxsitePath.resolve("metadata/repository/car/index.json"),
      index.toJsonString
    )
    index.entries.foreach { entry =>
      _write_text(
        config.doxsitePath.resolve("metadata/repository/car").resolve(s"${entry.artifactid}.json"),
        entry.toJsonString
      )
    }
  }

  private def _write_repository_car_page(
    config: BuildConfig,
    target: Path,
    locale: String,
    categories: Vector[CategoryContent]
  ): Unit = {
    val index = _repository_car_index(config)
    val projects = _resolved_project_packages(config)
    _copy_repository_car_sidecars(config, target, index)
    val page = target.resolve("repository").resolve("car").resolve("index.html")
    _write_text(
      page,
      _special_html_page(
        config,
        categories,
        locale,
        page,
        _repository_car_title(locale),
        _repository_car_description(locale),
        _repository_car_dashboard_body(config, locale, page, target, index)
      )
    )
    index.entries.foreach { entry =>
      val modulepage = target.resolve(entry.publicPath)
      _write_text(
        modulepage,
        _special_html_page(
          config,
          categories,
          locale,
          modulepage,
          entry.artifactid,
          _repository_car_module_description(locale, entry),
          _repository_car_module_body(config, target, modulepage, locale, entry, projects)
        )
      )
      entry.versions.foreach { version =>
        val versionpage = target.resolve(entry.versionPublicPath(version))
        _write_text(
          versionpage,
          _special_html_page(
            config,
            categories,
            locale,
            versionpage,
            s"${entry.artifactid} ${version.version}",
            _repository_car_version_description(locale, entry, version),
            _repository_car_version_body(config, target, versionpage, locale, entry, version, projects)
          )
        )
      }
    }
  }

  private def _repository_car_index(config: BuildConfig): RepositoryCarIndex = {
    val projects = _safe_resolved_project_packages(config)
    val entries = _repository_car_catalog_paths(config).flatMap { path =>
      _read_repository_car_catalog(config, path)
    }.map(_repository_car_merge_project_metadata(_, projects)).sortBy(_.artifactid)
    val diagnostics = _repository_car_diagnostics(config, entries, projects)
    RepositoryCarIndex(entries, diagnostics)
  }

  private def _repository_car_merge_project_metadata(
    entry: RepositoryCarEntry,
    projects: Vector[CozyBokProjectPublisher.ResolvedBokProject]
  ): RepositoryCarEntry = {
    val relatedprojects = _repository_car_related_projects(entry, projects)
    entry.copy(
      tags = (entry.tags ++ relatedprojects.flatMap(_.tags)).distinct,
      terms = (entry.terms ++ relatedprojects.flatMap(_.terms)).distinct
    )
  }

  private def _repository_car_diagnostics(
    config: BuildConfig,
    entries: Vector[RepositoryCarEntry],
    projects: Vector[CozyBokProjectPublisher.ResolvedBokProject]
  ): Vector[RepositoryCarDiagnostic] = {
    val catalogdiagnostics = entries.flatMap { entry =>
      if (_repository_car_related_projects(entry, projects).nonEmpty)
        None
      else
        Some(RepositoryCarDiagnostic("catalog-without-project", entry.artifactid, None, None, None, None, None))
    }
    val archivemetadata = entries.flatMap { entry =>
      entry.versions.flatMap(_repository_car_archive_diagnostics(entry.artifactid, _))
    }
    val projectdiagnostics = projects.flatMap { project =>
      if (entries.exists(entry => _repository_car_related_projects(entry, Vector(project)).nonEmpty))
        None
      else
        Some(
          RepositoryCarDiagnostic(
            "project-without-catalog",
            project.module,
            None,
            None,
            None,
            Some(_project_relative_path(config.project, project.descriptorfile)),
            Some(project.title)
          )
        )
    }
    (catalogdiagnostics ++ archivemetadata ++ projectdiagnostics).
      sortBy(x => (x.code, x.artifactid, x.version.getOrElse(""), x.projectpath.getOrElse("")))
  }

  private def _repository_car_archive_diagnostics(
    artifactid: String,
    version: RepositoryCarVersion
  ): Vector[RepositoryCarDiagnostic] =
    if (!version.archiveavailable)
      Vector.empty
    else {
      def _diagnostic_(
        code: String,
        coordinate: Option[RepositoryCarMetadataCoordinate]
      ): RepositoryCarDiagnostic =
        RepositoryCarDiagnostic(
          code,
          artifactid,
          Some(version.version),
          coordinate.flatMap(_.name),
          coordinate.flatMap(_.version),
          None,
          None
        )
      val componentdescriptor = version.componentdescriptor match {
        case None => Vector(_diagnostic_("archive-without-component-descriptor", None))
        case Some(json) =>
          val coordinate = _repository_car_component_descriptor_coordinate(artifactid, version, json)
          if (_repository_car_coordinate_mismatches(version.version, coordinate))
            Vector(_diagnostic_("component-descriptor-coordinate-mismatch", coordinate))
          else
            Vector.empty
      }
      val abimanifest = version.abimanifest match {
        case None => Vector(_diagnostic_("archive-without-abi-manifest", None))
        case Some(json) =>
          val coordinate = _repository_car_abi_manifest_coordinate(artifactid, json)
          if (_repository_car_coordinate_mismatches(version.version, coordinate))
            Vector(_diagnostic_("abi-manifest-coordinate-mismatch", coordinate))
          else
            Vector.empty
      }
      componentdescriptor ++ abimanifest
    }

  private def _repository_car_component_descriptor_coordinate(
    artifactid: String,
    catalogversion: RepositoryCarVersion,
    json: Json
  ): Option[RepositoryCarMetadataCoordinate] = {
    val cursor = json.hcursor
    val topname = cursor.get[String]("name").toOption
    val componentname = cursor.downField("component").get[String]("name").toOption
    val name = topname.orElse(componentname)
    val version = cursor.get[String]("version").toOption.
      orElse(cursor.downField("component").get[String]("version").toOption)
    val expectedname = if (topname.isDefined) artifactid else catalogversion.component.getOrElse(artifactid)
    if (name.isDefined || version.isDefined)
      Some(RepositoryCarMetadataCoordinate(name, version, expectedname))
    else
      None
  }

  private def _repository_car_abi_manifest_coordinate(
    artifactid: String,
    json: Json
  ): Option[RepositoryCarMetadataCoordinate] = {
    val car = json.hcursor.downField("car")
    val name = car.get[String]("name").toOption
    val version = car.get[String]("version").toOption
    if (name.isDefined || version.isDefined)
      Some(RepositoryCarMetadataCoordinate(name, version, artifactid))
    else
      None
  }

  private def _repository_car_coordinate_mismatches(
    version: String,
    coordinate: Option[RepositoryCarMetadataCoordinate]
  ): Boolean =
    coordinate.exists { value =>
      value.name.exists(_ != value.expectedname) || value.version.exists(_ != version)
    }

  private def _repository_car_catalog_paths(config: BuildConfig): Vector[Path] = {
    val dir = config.publication.repositoryPath(config.project).resolve("catalog/car")
    if (!Files.isDirectory(dir))
      Vector.empty
    else {
      val stream = Files.walk(dir)
      try {
        stream.iterator.asScala.toVector.
          filter(Files.isRegularFile(_)).
          filter(path => _is_repository_car_catalog_file(path)).
          sortBy(_.toAbsolutePath.normalize.toString)
      } finally {
        stream.close()
      }
    }
  }

  private def _is_repository_car_catalog_file(path: Path): Boolean = {
    val name = path.getFileName.toString.toLowerCase(Locale.ROOT)
    !name.contains(".model-metadata.") &&
      (name.endsWith(".yaml") || name.endsWith(".yml") || name.endsWith(".json"))
  }

  private def _read_repository_car_catalog(config: BuildConfig, path: Path): Option[RepositoryCarEntry] =
    _load_repository_car_catalog(path) match {
      case catalog if catalog.kind == "car" => Some(_repository_car_entry(config, path, catalog))
      case _ => None
    }

  private def _load_repository_car_catalog(path: Path): _root_.cozy.archive.RepositoryArtifactCatalog = {
    val name = path.getFileName.toString.toLowerCase(Locale.ROOT)
    if (name.endsWith(".json"))
      _repository_car_catalog_from_json(path)
    else
      _root_.cozy.RepositoryArtifactCatalog.load(path)
  }

  private def _repository_car_catalog_from_json(path: Path): _root_.cozy.archive.RepositoryArtifactCatalog = {
    val json = parser.parse(Files.readString(path, StandardCharsets.UTF_8)).fold(throw _, identity)
    val c = json.hcursor
    def string(name: String): Option[String] =
      c.downField(name).as[Option[String]].getOrElse(None)
    def strings(name: String): Vector[String] =
      c.downField(name).as[Option[Vector[String]]].getOrElse(None).getOrElse(Vector.empty)
    val versions =
      c.downField("versions").as[Option[Vector[Json]]].getOrElse(None).getOrElse(Vector.empty).map { value =>
        val vc = value.hcursor
        def vstring(name: String): Option[String] =
          vc.downField(name).as[Option[String]].getOrElse(None)
        val runtime = vc.downField("runtime").downField("cncf")
        val tested = runtime.downField("tested").as[Option[Vector[String]]].getOrElse(None).getOrElse(Vector.empty)
        val requirement =
          if (
            runtime.downField("minimum").as[Option[String]].getOrElse(None).isEmpty &&
              runtime.downField("maximum").as[Option[String]].getOrElse(None).isEmpty &&
              runtime.downField("excluded").as[Option[Vector[String]]].getOrElse(None).getOrElse(Vector.empty).isEmpty &&
              tested.isEmpty
          )
            None
          else
            Some(
              _root_.cozy.RepositoryArtifactRuntimeRequirement(
                runtime.downField("minimum").as[Option[String]].getOrElse(None),
                runtime.downField("maximum").as[Option[String]].getOrElse(None),
                runtime.downField("excluded").as[Option[Vector[String]]].getOrElse(None).getOrElse(Vector.empty),
                tested
              )
            )
        _root_.cozy.RepositoryArtifactCatalogVersion(
          vstring("version").getOrElse(""),
          vstring("channel"),
          vstring("status"),
          vstring("component"),
          vstring("publishedAt").orElse(vstring("published_at")),
          vstring("file"),
          requirement,
          vc.downField("checksum").downField("sha256").as[Option[String]].getOrElse(None).orElse(vstring("checksumSha256"))
        )
      }
    val catalog = _root_.cozy.RepositoryArtifactCatalog(
      string("schemaVersion").getOrElse("1"),
      string("kind").getOrElse("car"),
      string("artifactId").orElse(string("artifact_id")).getOrElse(""),
      string("recommended"),
      string("latestStable").orElse(string("latest_stable")),
      string("latestSnapshot").orElse(string("latest_snapshot")),
      string("status"),
      strings("aliases"),
      versions,
      strings("tags"),
      strings("terms")
    ).validate
    _validate_repository_car_json_source_path(catalog, path)
    catalog
  }

  private def _validate_repository_car_json_source_path(
    catalog: _root_.cozy.archive.RepositoryArtifactCatalog,
    path: Path
  ): Unit = {
    val filename = path.getFileName.toString
    val stem = filename.stripSuffix(".json")
    if (stem != catalog.artifactId)
      RAISE.invalidArgumentFault(s"Catalog filename does not match artifactId: $filename != ${catalog.artifactId}")
    Option(path.getParent).flatMap(parent => Option(parent.getFileName)).foreach { kind =>
      if (kind.toString != catalog.kind)
        RAISE.invalidArgumentFault(s"Catalog path kind does not match catalog kind: $kind != ${catalog.kind}")
    }
  }

  private def _repository_car_entry(
    config: BuildConfig,
    path: Path,
    catalog: _root_.cozy.archive.RepositoryArtifactCatalog
  ): RepositoryCarEntry =
    RepositoryCarEntry(
      artifactid = catalog.artifactId,
      aliases = catalog.aliases,
      tags = catalog.tags,
      terms = catalog.terms,
      status = catalog.status,
      recommended = catalog.recommended,
      lateststable = catalog.latestStable,
      latestsnapshot = catalog.latestSnapshot,
      sourcepath = _project_relative_path(config.project, path),
      sidecars = _repository_car_sidecars(config, path, catalog.artifactId),
      versions = catalog.versions.map(_repository_car_version(config, _))
    )

  private def _repository_car_sidecars(
    config: BuildConfig,
    catalogpath: Path,
    artifactid: String
  ): RepositoryCarSidecars = {
    val catalogdir = catalogpath.getParent
    def _sidecar_(suffix: String): Option[String] = {
      val path = catalogdir.resolve(s"${artifactid}${suffix}")
      if (Files.isRegularFile(path)) Some(_repository_car_public_path(config, path)) else None
    }
    RepositoryCarSidecars(
      _sidecar_(".cml"),
      _sidecar_(".model-metadata.json"),
      _sidecar_(".model-metadata.yaml")
    )
  }

  private def _repository_car_public_path(config: BuildConfig, path: Path): String = {
    val repository = config.publication.repositoryPath(config.project)
    val relative = repository.relativize(path.toAbsolutePath.normalize()).toString.replace(java.io.File.separatorChar, '/')
    s"repository/${relative}"
  }

  private def _copy_repository_car_sidecars(config: BuildConfig, target: Path, index: RepositoryCarIndex): Unit = {
    val repository = config.publication.repositoryPath(config.project)
    index.entries.flatMap(_.sidecars.paths).distinct.foreach { publicpath =>
      val relative = publicpath.stripPrefix("repository/")
      _copy_if_exists(repository.resolve(relative), target.resolve(publicpath))
    }
  }

  private def _repository_car_version(
    config: BuildConfig,
    version: _root_.cozy.archive.RepositoryArtifactCatalogVersion
  ): RepositoryCarVersion = {
    val archive = _repository_car_archive_metadata(config, version.file)
    RepositoryCarVersion(
      version = version.version,
      channel = version.channel,
      status = version.status,
      component = version.component,
      publishedat = version.publishedAt,
      file = version.file,
      runtimecncfminimum = version.runtime.flatMap(_.minimum),
      runtimecncfmaximum = version.runtime.flatMap(_.maximum),
      runtimecncftested = version.runtime.map(_.tested).getOrElse(Vector.empty),
      checksumsha256 = version.checksumSha256,
      componentdescriptor = archive.componentdescriptor,
      abimanifest = archive.abimanifest,
      archiveavailable = archive.available
    )
  }

  private def _repository_car_archive_metadata(
    config: BuildConfig,
    file: Option[String]
  ): RepositoryCarArchiveMetadata =
    file.map(_project_artifact_path(config, _)).filter(Files.isRegularFile(_)).map { path =>
      val zip = new ZipFile(path.toFile)
      try {
        def _json_entry_(name: String): Option[Json] =
          Option(zip.getEntry(name)).map { entry =>
            val in = zip.getInputStream(entry)
            try {
              val text = new String(in.readAllBytes(), StandardCharsets.UTF_8)
              parser.parse(text).fold(
                error => RAISE.invalidArgumentFault(s"Invalid repository CAR metadata JSON: ${path}!/${name}: ${error.message}"),
                identity
              )
            } finally {
              in.close()
            }
          }
        RepositoryCarArchiveMetadata(
          _json_entry_("component-descriptor.json"),
          _json_entry_("abi-manifest.json"),
          available = true
        )
      } finally {
        zip.close()
      }
    }.getOrElse(RepositoryCarArchiveMetadata(None, None, available = false))

  private def _repository_car_dashboard_body(
    config: BuildConfig,
    locale: String,
    page: Path,
    target: Path,
    index: RepositoryCarIndex
  ): String = {
    val rows =
      if (index.entries.isEmpty)
        s"""<p class="bok-card-muted">${_html_escape(_repository_car_empty(locale))}</p>"""
      else {
        val body = index.entries.map { entry =>
          val version = entry.effectiveVersion.getOrElse("-")
          val versions = entry.versions.size.toString
          val source = entry.sourcepath
          val aliases = if (entry.aliases.isEmpty) "-" else entry.aliases.mkString(", ")
          val href = _relative_href(page, target.resolve(entry.publicPath))
          s"""<tr>
             |  <td><a href="${_html_escape(href)}"><code>${_html_escape(entry.artifactid)}</code></a></td>
             |  <td>${_html_escape(version)}</td>
             |  <td>${_html_escape(versions)}</td>
             |  <td>${_html_escape(aliases)}</td>
             |  <td><code>${_html_escape(source)}</code></td>
             |</tr>""".stripMargin
        }.mkString("\n")
        s"""<div class="bok-project-table-wrap">
           |  <table class="table table-sm bok-project-cml-table">
           |    <thead><tr><th>CAR</th><th>${_html_escape(_repository_car_latest_label(locale))}</th><th>${_html_escape(_repository_car_versions_label(locale))}</th><th>${_html_escape(_repository_car_aliases_label(locale))}</th><th>${_html_escape(_repository_car_catalog_label(locale))}</th></tr></thead>
           |    <tbody>
           |${body}
           |    </tbody>
           |  </table>
           |</div>""".stripMargin
      }
    val diagnostics = _repository_car_diagnostics_html(locale, index.diagnostics)
    s"""<section class="bok-dashboard-shell bok-repository-car-dashboard" id="dashboard">
       |  ${_dashboard_hero(
              _repository_car_title(locale),
              _repository_car_description(locale),
              Vector(
                "CAR" -> index.entries.size.toString,
                _repository_car_versions_label(locale) -> index.entries.map(_.versions.size).sum.toString
              )
            )}
       |  <div class="bok-dashboard container-fluid bok-dashboard-command-center">
       |    <div class="row g-3">
       |      ${_dashboard_card("col-12", "bok-card-map bok-card-project-map", _repository_car_title(locale), rows, Vector("reader", "contributor", "project_manager"))}
       |      ${diagnostics}
       |    </div>
       |  </div>
       |</section>""".stripMargin
  }

  private def _repository_car_diagnostics_html(locale: String, diagnostics: Vector[RepositoryCarDiagnostic]): String =
    if (diagnostics.isEmpty)
      ""
    else {
      val items = diagnostics.map { diagnostic =>
        val subject = diagnostic.projecttitle.getOrElse(
          diagnostic.version.map(x => s"${diagnostic.artifactid} ${x}").getOrElse(diagnostic.artifactid)
        )
        val coordinate =
          if (diagnostic.metadataname.isDefined || diagnostic.metadataversion.isDefined)
            Some(
              s"${_repository_car_metadata_coordinate_label(locale)}: " +
                Vector(diagnostic.metadataname, diagnostic.metadataversion).flatten.mkString(" ")
            )
          else
            None
        val details = Vector(diagnostic.projectpath, coordinate).flatten.
          map(x => s" <code>${_html_escape(x)}</code>").mkString
        s"""<li><strong>${_html_escape(subject)}</strong><span>${_html_escape(_repository_car_diagnostic_message(locale, diagnostic.code))}${details}</span></li>"""
      }.mkString("\n")
      val body =
        s"""<ul class="bok-repository-car-diagnostic-list">
           |${items}
           |</ul>""".stripMargin
      _dashboard_card(
        "col-12",
        "bok-card-map bok-card-project-issues",
        _repository_car_diagnostics_label(locale),
        body,
        Vector("contributor", "project_manager")
      )
    }

  private def _project_repository_car_html(
    config: BuildConfig,
    locale: String,
    project: CozyBokProjectPublisher.ResolvedBokProject,
    page: Path,
    target: Path
  ): String =
    project.catalog.map { info =>
      val versions = info.catalog.versions.map { version =>
        val current = info.selectedversion.exists(_.version == version.version)
        val file = version.file.getOrElse("")
        val versionpage = target.resolve("repository").resolve("car").resolve(info.catalog.artifactId).resolve(s"${version.version}.html")
        val versionhref = _relative_href(page, versionpage)
        s"""<tr>
           |  <td><a href="${_html_escape(versionhref)}">${if (current) s"""<strong>${_html_escape(version.version)}</strong>""" else _html_escape(version.version)}</a></td>
           |  <td>${_html_escape(version.channel.getOrElse("-"))}</td>
           |  <td>${_html_escape(version.status.getOrElse("active"))}</td>
           |  <td><code>${_html_escape(file)}</code></td>
           |</tr>""".stripMargin
      }.mkString("\n")
      s"""<section class="bok-project-section bok-project-repository-cars" id="project-repository-cars">
         |  <div class="bok-project-section-head">
         |    <h2>${_html_escape(_repository_car_title(locale))}</h2>
         |    <p>${_html_escape(_repository_car_project_description(locale))}</p>
         |  </div>
         |  <div class="bok-project-table-wrap">
         |    <table class="table table-sm bok-project-cml-table">
         |      <thead><tr><th>${_html_escape(_repository_car_version_label(locale))}</th><th>${_html_escape(_repository_car_channel_label(locale))}</th><th>${_html_escape(_repository_car_status_label(locale))}</th><th>${_html_escape(_repository_car_file_label(locale))}</th></tr></thead>
         |      <tbody>
         |${versions}
         |      </tbody>
         |    </table>
         |  </div>
         |  <p class="bok-card-muted"><code>${_html_escape(_project_relative_path(config.project, info.path))}</code></p>
         |</section>""".stripMargin
    }.getOrElse("")

  private def _repository_car_module_body(
    config: BuildConfig,
    target: Path,
    page: Path,
    locale: String,
    entry: RepositoryCarEntry,
    projects: Vector[CozyBokProjectPublisher.ResolvedBokProject]
  ): String = {
    val relatedprojects = _repository_car_related_projects(entry, projects)
    val category = relatedprojects.headOption.map(project => _project_category(config, project))
    val archivemetadatarows = entry.effectiveVersion.
      flatMap(selected => entry.versions.find(_.version == selected)).
      map(_repository_car_archive_metadata_rows(locale, _)).
      getOrElse(Vector.empty)
    val versionrows = entry.versions.map { version =>
      val href = _relative_href(page, target.resolve(entry.versionPublicPath(version)))
      val selected = entry.effectiveVersion.contains(version.version)
      s"""<tr>
         |  <td><a href="${_html_escape(href)}">${if (selected) s"""<strong>${_html_escape(version.version)}</strong>""" else _html_escape(version.version)}</a></td>
         |  <td>${_html_escape(version.channel.getOrElse("-"))}</td>
         |  <td>${_html_escape(version.status.getOrElse("active"))}</td>
         |  <td><code>${_html_escape(version.file.getOrElse("-"))}</code></td>
         |</tr>""".stripMargin
    }.mkString("\n")
    s"""<section class="bok-project-section bok-repository-car-detail" id="repository-car-detail">
       |  ${_repository_car_properties_table(locale, Vector(
              _repository_car_catalog_label(locale) -> s"<code>${_html_escape(entry.sourcepath)}</code>",
              _repository_car_latest_label(locale) -> _html_escape(entry.effectiveVersion.getOrElse("-")),
              _repository_car_status_label(locale) -> _html_escape(entry.status.getOrElse("active")),
              _repository_car_aliases_label(locale) -> _html_escape(if (entry.aliases.isEmpty) "-" else entry.aliases.mkString(", ")),
              _repository_car_tags_label(locale) -> _repository_car_tag_links(target, page, entry.tags, category),
              _repository_car_terms_label(locale) -> _repository_car_term_links(config, target, page, entry.terms),
              _repository_car_sidecars_label(locale) -> _repository_car_sidecar_links(target, page, locale, entry.sidecars)
            ) ++ archivemetadatarows)}
       |  <h2>${_html_escape(_repository_car_versions_label(locale))}</h2>
       |  <div class="bok-project-table-wrap">
       |    <table class="table table-sm bok-project-cml-table">
       |      <thead><tr><th>${_html_escape(_repository_car_version_label(locale))}</th><th>${_html_escape(_repository_car_channel_label(locale))}</th><th>${_html_escape(_repository_car_status_label(locale))}</th><th>${_html_escape(_repository_car_file_label(locale))}</th></tr></thead>
       |      <tbody>
       |${versionrows}
       |      </tbody>
       |    </table>
       |  </div>
       |  ${_repository_car_related_projects_html(target, page, locale, relatedprojects)}
       |</section>""".stripMargin
  }

  private def _repository_car_version_body(
    config: BuildConfig,
    target: Path,
    page: Path,
    locale: String,
    entry: RepositoryCarEntry,
    version: RepositoryCarVersion,
    projects: Vector[CozyBokProjectPublisher.ResolvedBokProject]
  ): String = {
    val relatedprojects = _repository_car_related_projects(entry, projects)
    val category = relatedprojects.headOption.map(project => _project_category(config, project))
    val runtime =
      Vector(
        version.runtimecncfminimum.map(x => "minimum" -> x),
        version.runtimecncfmaximum.map(x => "maximum" -> x),
        if (version.runtimecncftested.isEmpty) None else Some("tested" -> version.runtimecncftested.mkString(", "))
      ).flatten.map { case (label, value) =>
        s"${_html_escape(label)}: ${_html_escape(value)}"
      }.mkString("<br>")
    s"""<section class="bok-project-section bok-repository-car-version" id="repository-car-version">
       |  ${_repository_car_properties_table(locale, Vector(
              _repository_car_catalog_label(locale) -> s"<code>${_html_escape(entry.sourcepath)}</code>",
              _repository_car_version_label(locale) -> _html_escape(version.version),
              _repository_car_channel_label(locale) -> _html_escape(version.channel.getOrElse("-")),
              _repository_car_status_label(locale) -> _html_escape(version.status.getOrElse("active")),
              _repository_car_component_label(locale) -> _html_escape(version.component.getOrElse("-")),
              _repository_car_published_at_label(locale) -> _html_escape(version.publishedat.getOrElse("-")),
              _repository_car_file_label(locale) -> s"<code>${_html_escape(version.file.getOrElse("-"))}</code>",
              _repository_car_runtime_label(locale) -> (if (runtime.isEmpty) "-" else runtime),
              _repository_car_checksum_label(locale) -> _html_escape(version.checksumsha256.getOrElse("-")),
              _repository_car_tags_label(locale) -> _repository_car_tag_links(target, page, entry.tags, category),
              _repository_car_terms_label(locale) -> _repository_car_term_links(config, target, page, entry.terms),
              _repository_car_sidecars_label(locale) -> _repository_car_sidecar_links(target, page, locale, entry.sidecars)
            ) ++ _repository_car_archive_metadata_rows(locale, version))}
       |  ${_repository_car_related_projects_html(target, page, locale, relatedprojects)}
       |</section>""".stripMargin
  }

  private def _repository_car_properties_table(locale: String, rows: Vector[(String, String)]): String = {
    val body = rows.map { case (label, value) =>
      s"""<tr><th>${_html_escape(label)}</th><td>${value}</td></tr>"""
    }.mkString("\n")
    s"""<div class="bok-project-table-wrap">
       |  <table class="table table-sm bok-project-cml-table">
       |    <tbody>
       |${body}
       |    </tbody>
       |  </table>
       |</div>""".stripMargin
  }

  private def _repository_car_related_projects_html(
    target: Path,
    page: Path,
    locale: String,
    projects: Vector[CozyBokProjectPublisher.ResolvedBokProject]
  ): String =
    if (projects.isEmpty)
      s"""<p class="bok-card-muted">${_html_escape(_repository_car_no_related_project(locale))}</p>"""
    else {
      val items = projects.map { project =>
        val href = _relative_href(page, target.resolve(project.publicationpath).resolve("index.html"))
        s"""<li><a href="${_html_escape(href)}">${_html_escape(project.title)}</a></li>"""
      }.mkString("\n")
      s"""<section class="bok-project-section bok-repository-car-projects" id="repository-car-projects">
         |  <h2>${_html_escape(_repository_car_related_project_label(locale))}</h2>
         |  <ul>
         |${items}
         |  </ul>
         |</section>""".stripMargin
    }

  private def _repository_car_related_projects(
    entry: RepositoryCarEntry,
    projects: Vector[CozyBokProjectPublisher.ResolvedBokProject]
  ): Vector[CozyBokProjectPublisher.ResolvedBokProject] =
    projects.filter { project =>
      project.module == entry.artifactid ||
        project.catalog.exists(_.catalog.artifactId == entry.artifactid)
    }.sortBy(_.publicationpath)

  private def _repository_car_title(locale: String): String =
    locale match {
      case "ja" => "CARリポジトリ"
      case _ => "Repository CARs"
    }

  private def _repository_car_description(locale: String): String =
    locale match {
      case "ja" => "repository/catalog/car で管理される公開済みCARを知識化した一覧です。"
      case _ => "Published CAR knowledge entries from repository/catalog/car."
    }

  private def _repository_car_project_description(locale: String): String =
    locale match {
      case "ja" => "このProjectに対応するrepository CAR catalogと公開versionです。"
      case _ => "Repository CAR catalog versions associated with this Project."
    }

  private def _repository_car_module_description(locale: String, entry: RepositoryCarEntry): String =
    locale match {
      case "ja" => s"${entry.artifactid} のCAR catalogと公開versionです。"
      case _ => s"CAR catalog and published versions for ${entry.artifactid}."
    }

  private def _repository_car_version_description(locale: String, entry: RepositoryCarEntry, version: RepositoryCarVersion): String =
    locale match {
      case "ja" => s"${entry.artifactid} ${version.version} の公開CAR version情報です。"
      case _ => s"Published CAR version information for ${entry.artifactid} ${version.version}."
    }

  private def _repository_car_empty(locale: String): String =
    locale match {
      case "ja" => "Repository CAR catalogはまだありません。"
      case _ => "No repository CAR catalog entries are available."
    }

  private def _repository_car_latest_label(locale: String): String =
    locale match {
      case "ja" => "代表version"
      case _ => "Selected version"
    }

  private def _repository_car_versions_label(locale: String): String =
    locale match {
      case "ja" => "Versions数"
      case _ => "Versions"
    }

  private def _repository_car_version_label(locale: String): String =
    locale match {
      case "ja" => "Version"
      case _ => "Version"
    }

  private def _repository_car_channel_label(locale: String): String =
    locale match {
      case "ja" => "チャネル"
      case _ => "Channel"
    }

  private def _repository_car_status_label(locale: String): String =
    locale match {
      case "ja" => "状態"
      case _ => "Status"
    }

  private def _repository_car_file_label(locale: String): String =
    locale match {
      case "ja" => "ファイル"
      case _ => "File"
    }

  private def _repository_car_aliases_label(locale: String): String =
    locale match {
      case "ja" => "別名"
      case _ => "Aliases"
    }

  private def _repository_car_tags_label(locale: String): String =
    locale match {
      case "ja" => "タグ"
      case _ => "Tags"
    }

  private def _repository_car_tag_links(
    target: Path,
    page: Path,
    tags: Vector[String],
    category: Option[String]
  ): String = {
    val links = tags.map(_tag_key(_, category)).filter(_.nonEmpty).distinct.map { key =>
      val tag = _tag_entry_from_usage(key, Vector.empty)
      val href = _relative_href(page, target.resolve(tag.publicpath))
      s"""<a class="bok-tag-chip" href="${_html_escape(href)}">${_html_escape(tag.effectiveTitle)}</a>"""
    }
    if (links.isEmpty) "-" else links.mkString(" ")
  }

  private def _repository_car_terms_label(locale: String): String =
    locale match {
      case "ja" => "用語"
      case _ => "Terms"
    }

  private def _repository_car_term_links(
    config: BuildConfig,
    target: Path,
    page: Path,
    references: Vector[String]
  ): String = {
    val terms = _terms(config)
    val links = references.distinct.map { reference =>
      terms.find(_term_reference_matches(reference, _)) match {
        case Some(term) =>
          val termhref = _relative_href(page, target.resolve(term.publicpath))
          val rdfhref = s"${_relative_href(page, target.resolve("rdf/index.html"))}?term=${_url_query_escape(term.id)}"
          s"""<span class="bok-repository-car-term"><a href="${_html_escape(termhref)}">${_html_escape(term.title)}</a><a class="bok-term-rdf-mini" href="${_html_escape(rdfhref)}">RDF</a></span>"""
        case None => _html_escape(reference)
      }
    }
    if (links.isEmpty) "-" else links.mkString(", ")
  }

  private def _repository_car_catalog_label(locale: String): String =
    locale match {
      case "ja" => "カタログ"
      case _ => "Catalog"
    }

  private def _repository_car_component_label(locale: String): String =
    locale match {
      case "ja" => "Component"
      case _ => "Component"
    }

  private def _repository_car_published_at_label(locale: String): String =
    locale match {
      case "ja" => "公開日時"
      case _ => "Published at"
    }

  private def _repository_car_runtime_label(locale: String): String =
    locale match {
      case "ja" => "Runtime"
      case _ => "Runtime"
    }

  private def _repository_car_checksum_label(locale: String): String =
    locale match {
      case "ja" => "Checksum"
      case _ => "Checksum"
    }

  private def _repository_car_sidecars_label(locale: String): String =
    locale match {
      case "ja" => "関連metadata"
      case _ => "Related metadata"
    }

  private def _repository_car_sidecar_links(
    target: Path,
    page: Path,
    locale: String,
    sidecars: RepositoryCarSidecars
  ): String = {
    val items = Vector(
      sidecars.cml.map("CML" -> _),
      sidecars.modelmetadatajson.map(_repository_car_model_metadata_label(locale, "JSON") -> _),
      sidecars.modelmetadatayaml.map(_repository_car_model_metadata_label(locale, "YAML") -> _)
    ).flatten.map { case (label, publicpath) =>
      val href = _relative_href(page, target.resolve(publicpath))
      s"""<a href="${_html_escape(href)}">${_html_escape(label)}</a>"""
    }
    if (items.isEmpty) "-" else items.mkString("<br>")
  }

  private def _repository_car_model_metadata_label(locale: String, format: String): String =
    locale match {
      case "ja" => s"モデルメタデータ (${format})"
      case _ => s"Model metadata (${format})"
    }

  private def _repository_car_archive_metadata_rows(
    locale: String,
    version: RepositoryCarVersion
  ): Vector[(String, String)] =
    Vector(
      version.componentdescriptor.map(json =>
        _repository_car_component_descriptor_label(locale) -> _repository_car_component_descriptor_summary(json)
      ),
      version.abimanifest.map(json =>
        _repository_car_abi_manifest_label(locale) -> _repository_car_abi_manifest_summary(json)
      )
    ).flatten

  private def _repository_car_component_descriptor_summary(json: Json): String = {
    val cursor = json.hcursor
    val name = cursor.get[String]("name").toOption.
      orElse(cursor.downField("component").get[String]("name").toOption).
      getOrElse("-")
    val version = cursor.get[String]("version").toOption.
      orElse(cursor.downField("component").get[String]("version").toOption).
      getOrElse("-")
    val component = cursor.get[String]("component").toOption.
      orElse(cursor.get[String]("componentName").toOption).
      orElse(cursor.downField("component").get[String]("componentName").toOption).
      getOrElse(name)
    val entitycount = cursor.downField("entities").as[Vector[Json]].toOption.map(_.size).getOrElse(0)
    _html_escape(s"${name} ${version} / ${component} / entities ${entitycount}")
  }

  private def _repository_car_abi_manifest_summary(json: Json): String = {
    val cursor = json.hcursor
    val car = cursor.downField("car")
    val abi = cursor.downField("abi")
    val exports = abi.downField("exports")
    val name = car.get[String]("name").toOption.getOrElse("-")
    val version = car.get[String]("version").toOption.getOrElse("-")
    val abiversion = abi.get[Int]("version").toOption.getOrElse(1)
    def _count_(field: String): Int = exports.downField(field).as[Vector[Json]].toOption.map(_.size).getOrElse(0)
    _html_escape(
      s"${name} ${version} / ABI ${abiversion} / components ${_count_("components")} / operations ${_count_("operations")} / entities ${_count_("entities")}"
    )
  }

  private def _repository_car_component_descriptor_label(locale: String): String =
    locale match {
      case "ja" => "コンポーネント記述子"
      case _ => "Component descriptor"
    }

  private def _repository_car_abi_manifest_label(locale: String): String =
    locale match {
      case "ja" => "ABIマニフェスト"
      case _ => "ABI manifest"
    }

  private def _repository_car_related_project_label(locale: String): String =
    locale match {
      case "ja" => "関連Project"
      case _ => "Related Projects"
    }

  private def _repository_car_no_related_project(locale: String): String =
    locale match {
      case "ja" => "関連Projectはまだありません。"
      case _ => "No related Project is available."
    }

  private def _repository_car_diagnostics_label(locale: String): String =
    locale match {
      case "ja" => "CARリポジトリ診断"
      case _ => "CAR Repository Diagnostics"
    }

  private def _repository_car_metadata_coordinate_label(locale: String): String =
    locale match {
      case "ja" => "metadata座標"
      case _ => "metadata coordinate"
    }

  private def _repository_car_diagnostic_message(locale: String, code: String): String =
    (locale, code) match {
      case ("ja", "catalog-without-project") => "公開CARに対応するProject定義がありません。"
      case ("ja", "project-without-catalog") => "Projectに対応する公開CAR catalogがありません。"
      case ("ja", "archive-without-component-descriptor") => "CARにcomponent-descriptor.jsonがありません。"
      case ("ja", "archive-without-abi-manifest") => "CARにabi-manifest.jsonがありません。"
      case ("ja", "component-descriptor-coordinate-mismatch") => "component descriptorの座標がcatalogと一致しません。"
      case ("ja", "abi-manifest-coordinate-mismatch") => "ABI manifestの座標がcatalogと一致しません。"
      case (_, "catalog-without-project") => "The published CAR has no related Project definition."
      case (_, "project-without-catalog") => "The Project has no corresponding published CAR catalog."
      case (_, "archive-without-component-descriptor") => "The CAR does not contain component-descriptor.json."
      case (_, "archive-without-abi-manifest") => "The CAR does not contain abi-manifest.json."
      case (_, "component-descriptor-coordinate-mismatch") => "The component descriptor coordinate does not match the catalog."
      case (_, "abi-manifest-coordinate-mismatch") => "The ABI manifest coordinate does not match the catalog."
      case _ => code
    }

  private def _project_relative_path(project: Path, path: Path): String =
    try {
      project.toAbsolutePath.normalize.relativize(path.toAbsolutePath.normalize).toString.replace(java.io.File.separatorChar, '/')
    } catch {
      case NonFatal(_) => path.toString
    }

  private def _project_category(config: BuildConfig, project: CozyBokProjectPublisher.ResolvedBokProject): String = {
    val projects = config.sourcepath.resolve("projects").toAbsolutePath.normalize()
    val relative = projects.relativize(project.packagedir.toAbsolutePath.normalize())
    if (relative.getNameCount >= 1) relative.getName(0).toString else "project"
  }

  private final case class ProjectArtifactRecord(artifacttype: String, warehousepath: String, path: Path, exists: Boolean)

  private def _project_artifact_path(config: BuildConfig, project: CozyBokProjectPublisher.ResolvedBokProject): Path =
    _project_artifact_path(config, project.warehousePath)

  private def _project_artifact_path(config: BuildConfig, warehousepath: String): Path = {
    val repository = config.publication.repositoryPath(config.project)
    val warehouse = config.publication.artifactBasePath(config.project)
    warehousepath match {
      case "repository" =>
        repository.toAbsolutePath.normalize()
      case path if path.startsWith("repository/") =>
        repository.resolve(path.stripPrefix("repository/")).toAbsolutePath.normalize()
      case path =>
        warehouse.resolve(path).toAbsolutePath.normalize()
    }
  }

  private def _project_artifact_status(config: BuildConfig, project: CozyBokProjectPublisher.ResolvedBokProject): String =
    if (project.reference.isSourceOnly) "source-only"
    else if (Files.isRegularFile(_project_artifact_path(config, project))) "published"
    else "missing"

  private def _project_artifact_records(config: BuildConfig, project: CozyBokProjectPublisher.ResolvedBokProject): Vector[ProjectArtifactRecord] = {
    val paths = project.catalog.map { info =>
      info.catalog.versions.map { version =>
        version.file.getOrElse(s"repository/car/${project.module}/${version.version}/${project.module}-${version.version}.car")
      }
    }.getOrElse(Vector(project.warehousePath))
    paths.distinct.map { warehousepath =>
      val path = _project_artifact_path(config, warehousepath)
      ProjectArtifactRecord(_project_artifact_type(warehousepath), warehousepath, path, Files.isRegularFile(path))
    }
  }

  private def _project_artifact_type(warehousepath: String): String = {
    val name = Paths.get(warehousepath).getFileName.toString.toLowerCase(Locale.ROOT)
    if (name.endsWith(".jar")) "jar"
    else if (name.endsWith(".sar")) "sar"
    else if (name.endsWith(".car")) "car"
    else "other"
  }

  private def _project_artifact_type_counts(records: Vector[ProjectArtifactRecord]): Vector[(String, Int)] = {
    val grouped = records.groupBy(_.artifacttype).map {
      case (kind, xs) => kind -> xs.size
    }
    _project_artifact_type_order.map(kind => kind -> grouped.getOrElse(kind, 0)).toVector
  }

  private val _project_artifact_type_order: Vector[String] =
    Vector("car", "sar", "jar")

  private def _project_artifact_type_counts_html(locale: String, counts: Vector[(String, Int)]): String =
    counts.map {
      case (kind, count) =>
        s"""<span>${_html_escape(_project_artifact_type_label(kind))} ${count}</span>"""
    }.mkString(s"""<span class="bok-project-artifact-kind-counts" aria-label="${_html_escape(_ui(locale, "project.label.artifact.kind.counts"))}">""", "", "</span>")

  private def _project_artifact_type_label(kind: String): String =
    kind.toUpperCase(Locale.ROOT)

  private def _project_artifact_availability_counts_html(locale: String, distributed: Int, undistributed: Int): String =
    Vector(
      _ui(locale, "project.label.distributed.artifacts") -> distributed,
      _ui(locale, "project.label.undistributed.artifacts") -> undistributed
    ).map {
      case (label, count) =>
        s"""<span>${_html_escape(label)} ${count}</span>"""
    }.mkString(s"""<span class="bok-project-artifact-availability-counts" aria-label="${_html_escape(_ui(locale, "project.label.distribution.availability"))}">""", "", "</span>")

  private def _project_artifact_status_label(locale: String, status: String): String =
    status match {
      case "published" => _ui(locale, "project.status.published")
      case "missing" => _ui(locale, "project.status.missing")
      case "source-only" => _ui(locale, "project.status.source.only")
      case other => other
    }

  private def _project_artifact_status_summary_label(locale: String, status: String): String =
    status match {
      case "published" => _ui(locale, "project.status.summary.published")
      case "missing" => _ui(locale, "project.status.summary.missing")
      case "source-only" => _ui(locale, "project.status.summary.source.only")
      case other => other
    }

  private def _project_artifact_status_fact_label(locale: String, status: String): String =
    status match {
      case "published" => _ui(locale, "project.status.fact.published")
      case "missing" => _ui(locale, "project.status.fact.missing")
      case "source-only" => _ui(locale, "project.status.fact.source.only")
      case other => other
    }

  private def _project_mode_label(locale: String, mode: String): String =
    mode match {
      case "external" => _ui(locale, "project.mode.external")
      case "internal" => _ui(locale, "project.mode.internal")
      case "local" => _ui(locale, "project.mode.internal")
      case other => other
    }

  private def _project_mode_counts(projects: Vector[CozyBokProjectPublisher.ResolvedBokProject]): Vector[(String, Int)] = {
    val grouped = projects.groupBy(_.projectmode).map {
      case (mode, xs) => mode -> xs.size
    }
    _project_mode_order.map(mode => mode -> grouped.getOrElse(mode, 0)).toVector
  }

  private val _project_mode_order: Vector[String] =
    Vector("internal", "external")

  private def _project_mode_counts_html(locale: String, counts: Vector[(String, Int)]): String =
    counts.map {
      case (mode, count) =>
        s"""<span>${_html_escape(_project_mode_label(locale, mode))} ${count}</span>"""
    }.mkString(s"""<span class="bok-project-mode-counts" aria-label="${_html_escape(_ui(locale, "project.label.placement"))}">""", "", "</span>")

  private def _project_cml_element_kind_counts(projects: Vector[CozyBokProjectPublisher.ResolvedBokProject]): Vector[(String, Int)] =
    projects.flatMap(_.cml.toVector).flatMap(_.modelElements).groupBy(_.kind).toVector.map {
      case (kind, xs) => kind -> xs.size
    }.sortBy(_._1)

  private def _project_cml_element_kind_counts_html(locale: String, counts: Vector[(String, Int)]): String =
    if (counts.isEmpty)
      ""
    else
      counts.map {
        case (kind, count) =>
          s"""<span>${_html_escape(_project_cml_element_kind_label(kind))} ${count}</span>"""
      }.mkString(s"""<span class="bok-project-cml-kind-counts" aria-label="${_html_escape(_ui(locale, "project.label.cml.kind.counts"))}">""", "", "</span>")

  private def _project_cml_element_kind_label(kind: String): String =
    kind.split("[-_]").filter(_.nonEmpty).map { part =>
      part.head.toUpper + part.tail
    }.mkString(" ")

  private def _project_cml_element_kind_counts_inline_html(locale: String, counts: Vector[(String, Int)]): String =
    if (counts.isEmpty)
      ""
    else
      s"""<div class="bok-project-cml-kind-line"><span>${_html_escape(_ui(locale, "project.label.cml.kind.counts"))}</span>${_project_cml_element_kind_counts_html(locale, counts)}</div>"""

  private def _project_artifact_records_html(locale: String, planned: Vector[ProjectArtifactRecord], current: Vector[ProjectArtifactRecord]): String = {
    val plannedcounts = _project_artifact_type_counts(planned)
    val currentcounts = _project_artifact_type_counts(current)
    val planneditems =
      if (planned.isEmpty)
        s"""<li>${_html_escape(_ui(locale, "project.artifacts.none.planned"))}</li>"""
      else
        planned.map(record => s"""<li><code>${_html_escape(record.warehousepath)}</code></li>""").mkString
    val currentitems =
      if (current.isEmpty)
        s"""<li>${_html_escape(_ui(locale, "project.artifacts.none.current"))}</li>"""
      else
        current.map(record => s"""<li><code>${_html_escape(record.warehousepath)}</code></li>""").mkString
    s"""<div class="bok-project-artifact-lists">
       |  <div><h4>${_html_escape(_ui(locale, "project.label.planned.artifacts"))}</h4><div class="bok-project-artifact-kind-line"><span>${_html_escape(_ui(locale, "project.label.planned.artifact.kind.counts"))}</span>${_project_artifact_type_counts_html(locale, plannedcounts)}</div><ul>${planneditems}</ul></div>
       |  <div><h4>${_html_escape(_ui(locale, "project.label.current.artifacts"))}</h4><div class="bok-project-artifact-kind-line"><span>${_html_escape(_ui(locale, "project.label.current.artifact.kind.counts"))}</span>${_project_artifact_type_counts_html(locale, currentcounts)}</div><ul>${currentitems}</ul></div>
       |</div>""".stripMargin
  }

  private def _project_detail_dashboard(
    config: BuildConfig,
    locale: String,
    project: CozyBokProjectPublisher.ResolvedBokProject
  ): String = {
    val artifactrecords = _project_artifact_records(config, project)
    val plannedartifacts = artifactrecords
    val currentartifacts = artifactrecords.filter(_.exists)
    val missingartifactcount = plannedartifacts.size - currentartifacts.size
    val cmlcount = project.cml.map(_.modelElements.size).getOrElse(0)
    val cmlkinds = _project_cml_element_kind_counts(Vector(project))
    val source = project.projectref.getOrElse(project.projectmode)
    val facts = Vector(
      _ui(locale, "project.label.type") -> project.descriptor.project.projecttype,
      _ui(locale, "project.label.version") -> project.version,
      _ui(locale, "project.label.distribution.artifacts") -> plannedartifacts.size.toString,
      _ui(locale, "project.label.distributed.artifacts") -> currentartifacts.size.toString,
      _ui(locale, "project.label.undistributed.artifacts") -> missingartifactcount.toString,
      _ui(locale, "project.label.cml.terms") -> cmlcount.toString
    ).map {
      case (label, value) =>
        s"""<span class="bok-project-fact"><strong>${_html_escape(value)}</strong><em>${_html_escape(label)}</em></span>"""
    }.mkString
    val sourceonlynotice =
      if (project.reference.isSourceOnly)
        s"""<p class="bok-project-source-only-note">${_html_escape(_ui(locale, "project.artifacts.source.only"))}</p>"""
      else
        ""
    val product =
      s"""<dl class="bok-project-detail-dl">
         |  <dt>${_html_escape(_ui(locale, "project.label.module"))}</dt><dd><code>${_html_escape(project.module)}</code></dd>
         |  <dt>${_html_escape(_ui(locale, "project.label.placement"))}</dt><dd>${_html_escape(_project_mode_label(locale, project.projectmode))}</dd>
         |  <dt>${_html_escape(_ui(locale, "project.label.source.project"))}</dt><dd>${_html_escape(source)}</dd>
         |  <dt>${_html_escape(_ui(locale, "project.label.version.source"))}</dt><dd>${_html_escape(project.versionsource)}</dd>
         |  <dt>${_html_escape(_ui(locale, "project.label.package"))}</dt><dd><code>${_html_escape(config.sourcepath.relativize(project.packagedir).toString.replace(java.io.File.separatorChar, '/'))}</code></dd>
         |</dl>
         |${sourceonlynotice}
         |${_project_artifact_records_html(locale, plannedartifacts, currentartifacts)}
         |${_project_cml_element_kind_counts_inline_html(locale, cmlkinds)}""".stripMargin
    s"""<section class="bok-project-detail-dashboard" id="project-dashboard">
       |  <div class="bok-project-detail-hero">
       |    <div>
       |      <p class="bok-dashboard-eyebrow">${_html_escape(_ui(locale, "project.card.dashboard"))}</p>
       |      <h2>${_html_escape(project.title)}</h2>
       |      <p>${_html_escape(project.summary.getOrElse("CAR project."))}</p>
       |    </div>
       |    <div class="bok-project-facts">${facts}</div>
       |  </div>
       |  <div class="bok-dashboard container-fluid bok-dashboard-command-center">
       |    <div class="row g-3">
       |      ${_dashboard_card("col-12", "bok-card-map bok-card-project-overview", _ui(locale, "project.card.product.metadata"), product, Vector("reader", "contributor", "project_manager"))}
       |    </div>
       |  </div>
       |</section>""".stripMargin
  }

  private def _project_component_surface_html(locale: String, project: CozyBokProjectPublisher.ResolvedBokProject): String = {
    def operationItems(xs: Vector[CozyBokProjectPublisher.CmlOperationSurface]): String =
      if (xs.isEmpty)
        s"""<p class="bok-project-empty-note">${_html_escape(_ui(locale, "project.surface.no.operations"))}</p>"""
      else
        xs.map { element =>
          val summary = _project_descriptive_summary(element.descriptive)
          val function = _project_operation_function(locale, element)
          val functionhtml = if (function.isEmpty) "" else s"""<span><code>${_html_escape(function)}</code></span>"""
          val summaryhtml = if (summary.isEmpty) "" else s"""<span>${_html_escape(summary)}</span>"""
          s"""<li><strong>${_html_escape(element.name)}</strong>${functionhtml}${summaryhtml}</li>"""
        }.mkString("""<ul class="bok-project-operation-list">""", "", "</ul>")
    def serviceNode(service: CozyBokProjectPublisher.CmlServiceSurface): String = {
      val summary = service.descriptive.summary.orElse(service.descriptive.brief).orElse(service.descriptive.description).getOrElse("")
      s"""<li class="bok-project-service-node">
         |  <div class="bok-project-node-head"><span>Service</span><strong>${_html_escape(service.name)}</strong></div>
         |  ${if (summary.isEmpty) "" else s"""<p>${_html_escape(summary)}</p>"""}
         |  <div class="bok-project-operation-branch">
         |    <div class="bok-project-node-head bok-project-node-head-sub"><span>Operation</span></div>
         |    ${operationItems(service.operations)}
         |  </div>
         |</li>""".stripMargin
    }
    def componentTree(component: CozyBokProjectPublisher.CmlComponentSurface): String = {
      val summary = component.descriptive.summary.orElse(component.descriptive.brief).orElse(component.descriptive.description).getOrElse("")
      val servicebranch =
        if (component.services.isEmpty)
          s"""<div class="bok-project-service-branch">
             |  <div class="bok-project-node-head bok-project-node-head-sub"><span>Service</span></div>
             |  <p class="bok-project-empty-note">${_html_escape(_ui(locale, "project.surface.no.services"))}</p>
             |</div>""".stripMargin
        else
          component.services.map(serviceNode).mkString("""<ul class="bok-project-service-branch">""", "", "</ul>")
      s"""<article class="bok-project-component-tree">
         |  <div class="bok-project-node-head"><span>Component</span><strong><code>${_html_escape(component.name)}</code></strong></div>
         |  ${if (summary.isEmpty) "" else s"""<p>${_html_escape(summary)}</p>"""}
         |  ${servicebranch}
         |</article>""".stripMargin
    }
    val surface = project.cml.flatMap(_.surface.component).map(componentTree).getOrElse {
      s"""<article class="bok-project-component-tree">
         |  <div class="bok-project-node-head"><span>Component</span><strong><code>${_html_escape(project.module)}</code></strong></div>
         |  <p class="bok-project-empty-note">${_html_escape(_ui(locale, "project.surface.no.component"))}</p>
         |</article>""".stripMargin
    }
    s"""<section class="bok-project-section bok-project-surface" id="project-surface">
       |  <div class="bok-project-section-head">
       |    <h2>${_html_escape(_ui(locale, "project.section.surface"))}</h2>
       |    <p>${_html_escape(_ui(locale, "project.section.surface.description"))}</p>
       |  </div>
       |  ${surface}
       |</section>""".stripMargin
  }

  private final case class ProjectCmlTermRow(
    kind: String,
    name: String,
    terms: Vector[TermEntry],
    function: String,
    summary: String
  )

  private def _project_model_terms_html(config: BuildConfig, locale: String, project: CozyBokProjectPublisher.ResolvedBokProject, page: Path, target: Path): String =
    project.cml.map { cml =>
      val terms = _terms(config)
      val termrows = _project_cml_term_rows(locale, cml, terms)
      val linkedcount = termrows.count(_.terms.nonEmpty)
      val unlinkedcount = termrows.size - linkedcount
      def row_html(row: ProjectCmlTermRow, includesignature: Boolean): String = {
        val termhtml =
          if (row.terms.isEmpty)
            s"""<span class="bok-project-unlinked-term">-</span>"""
          else
            row.terms.map { term =>
              val href = _relative_href(page, target.resolve(term.publicpath))
              s"""<a href="${_html_escape(href)}">${_html_escape(term.title)}</a>"""
            }.mkString("""<span class="bok-project-linked-terms">""", "", "</span>")
        val signaturecell =
          if (includesignature)
            s"""  <td>${_html_escape(row.function)}</td>
               |""".stripMargin
          else
            ""
        s"""<tr>
           |  <td>${_html_escape(row.name)}</td>
           |  <td>${termhtml}</td>
           |${signaturecell}  <td>${_html_escape(row.summary)}</td>
           |</tr>""".stripMargin
      }
      val preferredkinds = Vector("Component", "Service", "Operation", "Entity")
      val actualkinds = termrows.map(_.kind).distinct
      val tabkinds = preferredkinds.filter(actualkinds.contains) ++ actualkinds.filterNot(preferredkinds.contains)
      val initialkind = tabkinds.find(_ == "Entity").orElse(tabkinds.headOption)
      val tabbuttons = tabkinds.map { kind =>
          val rows = termrows.filter(_.kind == kind)
          val termcount = rows.flatMap(_.terms.map(_.id)).distinct.size
          val modelcount = rows.size
          val active = initialkind.contains(kind)
          val tabid = s"project-cml-tab-${_tag_segment(kind)}"
          val panelid = s"project-cml-panel-${_tag_segment(kind)}"
          s"""<button type="button" role="tab" id="${_html_escape(tabid)}" aria-selected="${active}" aria-controls="${_html_escape(panelid)}" class="${if (active) "is-active" else ""}" data-project-cml-tab="${_html_escape(kind)}">${_html_escape(kind)}(${termcount}/${modelcount})</button>"""
      }.mkString("\n")
      val tabpanels = tabkinds.map { kind =>
          val includesignature = kind == "Operation"
          val rows = termrows.filter(_.kind == kind).map(row_html(_, includesignature)).mkString("\n")
          val headers =
            if (includesignature)
              s"""<th>${_html_escape(_ui(locale, "project.model.name"))}</th><th>${_html_escape(_ui(locale, "project.model.term"))}</th><th>${_html_escape(_ui(locale, "project.model.function"))}</th><th>${_html_escape(_ui(locale, "project.model.description"))}</th>"""
            else
              s"""<th>${_html_escape(_ui(locale, "project.model.name"))}</th><th>${_html_escape(_ui(locale, "project.model.term"))}</th><th>${_html_escape(_ui(locale, "project.model.description"))}</th>"""
          val active = initialkind.contains(kind)
          val tabid = s"project-cml-tab-${_tag_segment(kind)}"
          val panelid = s"project-cml-panel-${_tag_segment(kind)}"
          s"""<section id="${_html_escape(panelid)}" class="bok-project-cml-tab-panel ${if (active) "is-active" else ""}" role="tabpanel" aria-labelledby="${_html_escape(tabid)}" data-project-cml-panel="${_html_escape(kind)}"${if (active) "" else " hidden"}>
             |  <div class="bok-project-table-wrap">
             |    <table class="table table-sm bok-project-cml-table">
             |      <thead><tr>${headers}</tr></thead>
             |      <tbody>
             |${rows}
             |      </tbody>
             |    </table>
             |  </div>
             |</section>""".stripMargin
      }.mkString("\n")
      val summarycards =
        s"""<div class="bok-project-cml-link-summary">
           |  <article class="bok-project-cml-summary-card bok-project-cml-summary-total">
           |    <strong>${termrows.size}</strong>
           |    <span>${_html_escape(_ui(locale, "project.model.count.total"))}</span>
           |  </article>
           |  <article class="bok-project-cml-summary-card bok-project-cml-summary-breakdown">
           |    <span>${_html_escape(_ui(locale, "project.model.count.term.links"))}</span>
           |    <dl>
           |      <dt>${_html_escape(_ui(locale, "project.model.count.linked"))}</dt><dd>${linkedcount}</dd>
           |      <dt>${_html_escape(_ui(locale, "project.model.count.unlinked"))}</dt><dd>${unlinkedcount}</dd>
           |    </dl>
           |  </article>
           |</div>""".stripMargin
      val sources = _project_cml_source_cards(locale, cml)
      if (termrows.isEmpty)
        ""
      else
        s"""<section class="bok-project-section project-cml-glossary" id="project-model-terms">
           |  <div class="bok-project-section-head">
           |    <h2>${_html_escape(_ui(locale, "project.section.model.terms"))}</h2>
           |    <p>${_html_escape(_ui(locale, "project.section.model.terms.description"))}</p>
           |  </div>
           |  ${summarycards}
           |  ${sources}
           |  <div class="bok-project-cml-tabs" data-project-cml-tabs>
           |    <div class="bok-project-cml-tablist" role="tablist" aria-label="${_html_escape(_ui(locale, "project.section.model.terms"))}">
           |${tabbuttons}
           |    </div>
           |${tabpanels}
           |  </div>
           |  <script>
           |(() => {
           |  document.querySelectorAll('[data-project-cml-tabs]').forEach((root) => {
           |    const tabs = Array.from(root.querySelectorAll('[data-project-cml-tab]'));
           |    const panels = Array.from(root.querySelectorAll('[data-project-cml-panel]'));
           |    tabs.forEach((tab) => {
           |      tab.addEventListener('click', () => {
           |        const name = tab.dataset.projectCmlTab;
           |        tabs.forEach((x) => {
           |          const active = x === tab;
           |          x.classList.toggle('is-active', active);
           |          x.setAttribute('aria-selected', active ? 'true' : 'false');
           |        });
           |        panels.forEach((panel) => {
           |          const active = panel.dataset.projectCmlPanel === name;
           |          panel.classList.toggle('is-active', active);
           |          panel.hidden = !active;
           |        });
           |      });
           |    });
           |  });
           |})();
           |  </script>
           |</section>""".stripMargin
    }.getOrElse("")

  private def _project_cml_source_cards(locale: String, cml: CozyBokProjectPublisher.ProjectCmlInfo): String = {
    val path = cml.sourceprojectrelativepath
    val name = Option(java.nio.file.Paths.get(path).getFileName).map(_.toString).getOrElse(path)
    s"""<div class="bok-project-cml-sources" aria-label="${_html_escape(_ui(locale, "project.model.source.title"))}">
       |  <article class="bok-project-cml-source-card">
       |    <span>${_html_escape(_ui(locale, "project.model.source.title"))}</span>
       |    <strong>${_html_escape(name)}</strong>
       |    <code>${_html_escape(path)}</code>
       |  </article>
       |</div>""".stripMargin
  }

  private def _project_cml_term_rows(locale: String, cml: CozyBokProjectPublisher.ProjectCmlInfo, terms: Vector[TermEntry]): Vector[ProjectCmlTermRow] = {
    def linkedterms(kind: String, name: String): Vector[TermEntry] =
      terms.filter(term => term.cmlLinks.exists(link => _project_cml_link_matches(link, kind, name)))
    val surface = cml.surface.component.toVector.flatMap { component =>
      val componentrow = ProjectCmlTermRow(
        "Component",
        component.name,
        linkedterms("component", component.name),
        "",
        _project_descriptive_summary(component.descriptive)
      )
      val servicerows = component.services.flatMap { service =>
        val servicerow = ProjectCmlTermRow(
          "Service",
          service.name,
          linkedterms("service", service.name),
          "",
          _project_descriptive_summary(service.descriptive)
        )
        val operationrows = service.operations.map { operation =>
          ProjectCmlTermRow(
            "Operation",
            operation.name,
            linkedterms("operation", operation.name),
            _project_operation_function(locale, operation),
            _project_descriptive_summary(operation.descriptive)
          )
        }
        servicerow +: operationrows
      }
      componentrow +: servicerows
    }
    val model = cml.modelElements.map { element =>
      ProjectCmlTermRow(
        _project_cml_element_kind_label(element.kind),
        element.name,
        linkedterms(element.kind, element.name),
        "",
        _project_descriptive_summary(element.descriptive)
      )
    }
    surface ++ model
  }

  private def _project_descriptive_summary(descriptive: CozyBokProjectPublisher.CmlDescriptive): String =
    descriptive.summary.orElse(descriptive.description).orElse(descriptive.brief).getOrElse("")

  private def _project_operation_function(locale: String, operation: CozyBokProjectPublisher.CmlOperationSurface): String = {
    val input = operation.inputtype.getOrElse(_ui(locale, "project.surface.operation.input.unspecified"))
    val output = operation.outputtype.getOrElse(_ui(locale, "project.surface.operation.output.unspecified"))
    operation.operationtype match {
      case Some(kind) => s"${kind}: ${input} -> ${output}"
      case None => s"${input} -> ${output}"
    }
  }

  private def _project_cml_link_matches(link: TermCmlLink, kind: String, name: String): Boolean = {
    def normalize(x: String): String =
      x.trim.toLowerCase(Locale.ROOT).replace("_", "-")
    normalize(link.kind) == normalize(kind) && normalize(link.value) == normalize(name)
  }

  private def _project_narrative_html(locale: String, articlebody: String): String =
    if (articlebody.trim.isEmpty)
      ""
    else
      s"""<section class="bok-project-section bok-project-narrative" id="project-narrative">
         |  <div class="bok-project-section-head">
         |    <h2>${_html_escape(_ui(locale, "project.section.narrative"))}</h2>
         |  </div>
         |  <div class="bok-project-narrative-body">
         |${articlebody}
         |  </div>
         |</section>""".stripMargin

  private def _copy_machine_metadata_artifacts(config: BuildConfig, target: Path): Unit = {
    _copy_if_exists(config.doxsitePath.resolve("site.ttl"), target.resolve("rdf").resolve("site.ttl"))
    _copy_if_exists(config.doxsitePath.resolve("site.jsonld"), target.resolve("rdf").resolve("site.jsonld"))
    _copy_if_exists(config.doxsitePath.resolve("metadata/rdf/graph.json"), target.resolve("metadata/rdf/graph.json"))
    _copy_if_exists(config.doxsitePath.resolve("metadata/glossary/terms.json"), target.resolve("metadata/glossary/terms.json"))
    _copy_if_exists(config.doxsitePath.resolve("metadata/bibliography/bibliography.json"), target.resolve("metadata/bibliography/bibliography.json"))
    _copy_if_exists(config.doxsitePath.resolve("metadata/scenarios/scenarios.json"), target.resolve("metadata/scenarios/scenarios.json"))
    _copy_if_exists(config.doxsitePath.resolve("metadata/tags/tags.json"), target.resolve("metadata/tags/tags.json"))
    _copy_directory(config.doxsitePath.resolve("metadata/repository/car"), target.resolve("metadata/repository/car"))
  }

  private def _copy_if_exists(source: Path, target: Path): Unit =
    if (Files.isRegularFile(source)) {
      Option(target.getParent).foreach(Files.createDirectories(_))
      Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
    }

  private def _write_scenario_metadata(config: BuildConfig): Unit =
    ScenarioMetadata.write(
      config.sourcepath,
      config.doxsitePath.resolve("metadata/scenarios/scenarios.json")
    )

  private def _glossary_dashboard_body(
    config: BuildConfig,
    categories: Vector[CategoryContent],
    terms: Vector[TermEntry],
    languagerootprefix: String,
    locale: String
  ): String = {
    val dashboard = _dashboard(config)
    val projects = _safe_resolved_project_packages(config)
    s"""<div class="bok-dashboard container-fluid bok-dashboard-command-center bok-term-dashboard">
       |  <div class="row g-3">
       |    ${_dashboard_card("col-12", "bok-card-kpi bok-card-glossary-summary", _ui(locale, "term.dashboard.summary.title"), _glossary_metric_cards(locale, categories.size, categories.count(_.terms.nonEmpty), terms), Vector("reader", "contributor", "project_manager"))}
       |    ${_dashboard_card("col-12", "bok-card-map bok-card-mono-koto-workflow", _ui(locale, "term.analysis.workflow.title"), _glossary_mono_koto_workflow(config, locale, terms), Vector("reader", "contributor", "project_manager"))}
       |    ${_dashboard_card("col-12", "bok-card-map bok-card-glossary-analysis-routes", _ui(locale, "term.analysis.routes"), _glossary_type_analysis_routes(locale, terms), Vector("reader", "contributor", "project_manager"))}
       |    ${_dashboard_card("col-12 col-xl-6", "bok-card-map bok-card-glossary-usage", _ui(locale, "term.usage.title"), _glossary_usage_card(locale, terms), Vector("reader", "contributor", "project_manager"))}
       |    ${_dashboard_card("col-12 col-xl-6", "bok-card-map bok-card-glossary-rdf", _ui(locale, "term.rdf.connection.title"), s"""<div id="term-rdf-connections">${_glossary_rdf_connection_card(locale, terms, dashboard)}</div>""", Vector("reader", "contributor", "project_manager"))}
       |    ${_dashboard_card("col-12 col-xl-6", "bok-card-map bok-card-glossary-project", _ui(locale, "term.project.connection.title"), s"""<div id="term-project-connections">${_glossary_project_connection_card(locale, terms, projects)}</div>""", Vector("reader", "contributor", "project_manager"))}
       |    ${_dashboard_card("col-12", "bok-card-map bok-card-glossary-type-issues", _ui(locale, "term.analysis.missing"), s"""<div id="term-issue-list">${_glossary_type_issue_terms(locale, terms)}</div>""", Vector("contributor", "project_manager"))}
       |    ${_dashboard_card("col-12", "bok-card-map bok-card-glossary-map", _ui(locale, "term.dashboard.groups.title"), s"""<div id="term-groups">${_term_group_cards(locale, terms, categories)}</div>""", Vector("reader", "contributor", "project_manager"))}
       |    ${_dashboard_card("col-12 col-md-6", "bok-card-map bok-card-glossary-language", _ui(locale, "term.language.index"), s"""<div id="language-index">${_glossary_language_links(config, languagerootprefix)}</div>""", Vector("reader"))}
       |    ${_dashboard_card("col-12 col-md-6", "bok-card-activity bok-card-glossary-recent", _ui(locale, "term.recent"), s"""<div id="recent-terms">${_glossary_recent_terms(terms)}</div>""", Vector("reader", "contributor"))}
       |  </div>
       |</div>""".stripMargin
  }

  private def _glossary_dedicated_page(
    config: BuildConfig,
    categories: Vector[CategoryContent],
    locale: String,
    page: Path,
    glossarybody: String,
    terms: Vector[TermEntry]
  ): String =
    s"""<!doctype html>
       |<html lang="${_html_escape(locale)}">
       |<head>
       |  <meta charset="utf-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1">
       |  <title>${_html_escape(_ui(locale, "glossary.title"))} - ${_html_escape(config.siteTitle)}</title>
       |${_site_css_links(config, page)}
       |</head>
       |<body class="article ${_html_escape(_support_dashboard_theme_class)}">
       |${_category_header(config, categories, locale)}
       |<div class="body body-dashboard bok-glossary-body">
       |  <main class="article bok-glossary-main">
       |    <div class="content">
       |      <article class="doc bok-glossary-doc">
       |        <section class="bok-dashboard-shell bok-glossary-dashboard" id="dashboard">
       |          ${_dashboard_hero(
                    _ui(locale, "glossary.title"),
                    _ui(locale, "glossary.description"),
                    Vector(
                      _ui(locale, "dashboard.kpi.terms") -> terms.size.toString,
                      _ui(locale, "dashboard.matrix.category") -> terms.flatMap(_.category).distinct.size.toString,
                      "RDF" -> terms.map(_.rdfRefs.size).sum.toString
                    )
                  )}
       |          ${glossarybody}
       |        </section>
       |      </article>
       |    </div>
       |  </main>
       |</div>
       |</body>
       |</html>
       |""".stripMargin

  private def _history_dashboard_body(locale: String): String =
    s"""<div class="sect1" id="timeline">
       |  <h2>Timeline</h2>
       |  <div class="sectionbody">
       |    <p>${_html_escape(_ui(locale, "history.timeline.description"))}</p>
       |    <ul>
       |      <li>${_html_escape(_ui(locale, "history.timeline.item.started"))}</li>
       |      <li>${_html_escape(_ui(locale, "history.timeline.item.record"))}</li>
       |    </ul>
       |  </div>
       |</div>
       |<div class="sect1" id="operation-notes">
       |  <h2>Operation Notes</h2>
       |  <div class="sectionbody">
       |    <p>${_html_escape(_ui(locale, "history.operation.notes"))}</p>
       |  </div>
       |</div>""".stripMargin

  private def _write_manual_source_page(
    config: BuildConfig,
    categories: Vector[CategoryContent],
    locale: String,
    target: Path,
    name: String,
    title: String,
    description: String
  ): Unit = {
    val source = config.sourcepath.resolve("manual").resolve(s"${name}.dox")
    val body = _source_narrative_html(source, locale)
    if (body.nonEmpty) {
      val page = target.resolve("manual").resolve(s"${name}.html")
      _write_text(
        page,
        _manual_html_page(
          config,
          categories,
          locale,
          page,
          title,
          description,
          body
        )
      )
    }
  }

  private def _manual_dashboard_body(locale: String): String =
    s"""<div class="sect1" id="basic-operations">
       |  <h2>Basic Operations</h2>
       |  <div class="sectionbody">
       |    <ul>
       |      <li><code>cozy bok doctor</code>: ${_html_escape(_ui(locale, "manual.operation.doctor"))}</li>
       |      <li><code>cozy bok build --strategy preview</code>: ${_html_escape(_ui(locale, "manual.operation.build"))}</li>
       |      <li><code>cozy bok preview</code>: ${_html_escape(_ui(locale, "manual.operation.preview"))}</li>
       |      <li><code>cozy bok publish . --dry-run</code>: ${_html_escape(_ui(locale, "manual.operation.publish.dryrun"))}</li>
       |    </ul>
       |  </div>
       |</div>
       |<div class="sect1" id="page-types">
       |  <h2>Page Types</h2>
       |  <div class="sectionbody">
       |    <ul>
       |      <li>Home Dashboard: ${_html_escape(_ui(locale, "manual.page.home"))}</li>
       |      <li>Category Dashboard: ${_html_escape(_ui(locale, "manual.page.category"))}</li>
       |      <li>Glossary: ${_html_escape(_ui(locale, "manual.page.glossary"))}</li>
       |      <li>History: ${_html_escape(_ui(locale, "manual.page.history"))}</li>
       |    </ul>
       |  </div>
       |</div>
       |<div class="sect1" id="notes">
       |  <h2>Notes</h2>
       |  <div class="sectionbody">
       |    <p>${_html_escape(_ui(locale, "manual.notes"))}</p>
       |  </div>
       |</div>""".stripMargin

  private def _manual_body_with_anchors(body: String): String =
    body.replace(
      "<h3>Glossary Term Classification</h3>",
      """<h3 id="glossary-term-classification">Glossary Term Classification</h3>"""
    )

  private def _rdf_dedicated_page(
    config: BuildConfig,
    categories: Vector[CategoryContent],
    locale: String,
    page: Path
  ): String =
    s"""<!doctype html>
       |<html lang="${_html_escape(locale)}">
       |<head>
       |  <meta charset="utf-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1">
       |  <title>${_html_escape(_ui(locale, "rdf.graph.title"))} - ${_html_escape(config.siteTitle)}</title>
       |${_site_css_links(config, page)}
       |</head>
       |<body class="article ${_html_escape(_support_dashboard_theme_class)}">
       |${_category_header(config, categories, locale)}
       |<div class="body body-dashboard bok-rdf-body">
       |  <main class="article bok-rdf-main">
       |    <div class="content">
       |      <article class="doc bok-rdf-doc">
       |        ${_rdf_workspace(locale)}
       |      </article>
       |    </div>
       |  </main>
       |</div>
       |</body>
       |</html>
       |""".stripMargin

  private def _rdf_node_detail_page(
    config: BuildConfig,
    categories: Vector[CategoryContent],
    locale: String,
    page: Path
  ): String =
    s"""<!doctype html>
       |<html lang="${_html_escape(locale)}">
       |<head>
       |  <meta charset="utf-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1">
       |  <title>${_html_escape(_ui(locale, "rdf.graph.node.full.detail"))} - ${_html_escape(config.siteTitle)}</title>
       |${_site_css_links(config, page)}
       |</head>
       |<body class="article ${_html_escape(_support_dashboard_theme_class)}">
       |${_category_header(config, categories, locale)}
       |<div class="body body-dashboard bok-rdf-body">
       |  <main class="article bok-rdf-main">
       |    <div class="content">
       |      <article class="doc bok-rdf-doc">
       |        ${_rdf_node_detail_workspace(locale)}
       |      </article>
       |    </div>
       |  </main>
       |</div>
       |</body>
       |</html>
       |""".stripMargin

  private def _rdf_node_detail_workspace(locale: String): String =
    s"""<section class="bok-rdf-node-page" data-graph="../metadata/rdf/graph.json" data-terms="../metadata/glossary/terms.json">
       |  <div class="bok-rdf-hero">
       |    <div>
       |      <span class="bok-dashboard-eyebrow">RDF</span>
       |      <h1 class="page">${_html_escape(_ui(locale, "rdf.graph.node.full.detail"))}</h1>
       |      <p>${_html_escape(_ui(locale, "rdf.graph.node.full.description"))}</p>
       |    </div>
       |    <div class="bok-rdf-hero-actions">
       |      <a href="index.html">${_html_escape(_ui(locale, "rdf.graph.title"))}</a>
       |      <a href="site.ttl">site.ttl</a>
       |      <a href="site.jsonld">site.jsonld</a>
       |      <a href="../metadata/rdf/graph.json">graph.json</a>
       |    </div>
       |  </div>
       |  <div id="bok-rdf-node-page-status" class="bok-rdf-node-page-status">${_html_escape(_ui(locale, "rdf.graph.loading"))}</div>
       |  <div id="bok-rdf-node-page-content" class="bok-rdf-node-page-content"></div>
       |</section>
       |<script>
       |${_rdf_node_detail_script(locale)}
       |</script>""".stripMargin

  private def _rdf_workspace(locale: String): String =
    s"""<section class="bok-rdf-workspace" data-graph="../metadata/rdf/graph.json" data-terms="../metadata/glossary/terms.json" data-triples="site.ttl">
       |  <div class="bok-rdf-hero">
       |    <div>
       |      <span class="bok-dashboard-eyebrow">RDF</span>
       |      <h1 class="page">${_html_escape(_ui(locale, "rdf.graph.title"))}</h1>
       |      <p>${_html_escape(_ui(locale, "rdf.graph.description"))}</p>
       |    </div>
       |    <div class="bok-rdf-hero-actions">
       |      <a href="../index.html">${_html_escape(_ui(locale, "nav.home"))}</a>
       |      <a href="site.ttl">site.ttl</a>
       |      <a href="site.jsonld">site.jsonld</a>
       |      <a href="../metadata/rdf/graph.json">graph.json</a>
       |    </div>
       |  </div>
       |  <div class="bok-rdf-tabbar">
       |    <div class="bok-rdf-view-switch" role="tablist" aria-label="RDF views">
       |      <button class="is-active" type="button" role="tab" aria-selected="true" aria-controls="bok-rdf-panel-graph" data-rdf-view="graph">${_html_escape(_ui(locale, "rdf.graph.view.graph"))}</button>
       |      <button type="button" role="tab" aria-selected="false" aria-controls="bok-rdf-panel-nodes" data-rdf-view="nodes">${_html_escape(_ui(locale, "rdf.graph.view.nodes"))}</button>
       |      <button type="button" role="tab" aria-selected="false" aria-controls="bok-rdf-panel-triples" data-rdf-view="triples">${_html_escape(_ui(locale, "rdf.graph.view.triples"))}</button>
       |    </div>
       |  </div>
       |  <div class="bok-rdf-filterbar">
       |    <label>${_html_escape(_ui(locale, "rdf.graph.category.filter"))}<input id="bok-rdf-category-filter" type="text" placeholder="category"></label>
       |    <label>${_html_escape(_ui(locale, "rdf.graph.term.filter"))}<input id="bok-rdf-term-filter" type="text" placeholder="term"></label>
       |    <label>${_html_escape(_ui(locale, "tag.title"))}<input id="bok-rdf-tag-filter" type="text" placeholder="tag"></label>
       |    <span id="bok-rdf-viewer-status">${_html_escape(_ui(locale, "rdf.graph.loading"))}</span>
       |  </div>
       |  <div class="bok-rdf-panels">
       |    <section id="bok-rdf-panel-graph" class="bok-rdf-panel bok-rdf-panel-graph is-active" data-rdf-panel="graph" role="tabpanel" aria-label="${_html_escape(_ui(locale, "rdf.graph.view.graph"))}">
       |      <h2 class="bok-rdf-panel-title">${_html_escape(_ui(locale, "rdf.graph.view.graph"))}</h2>
       |      <div id="bok-rdf-viewer-graph" class="bok-rdf-viewer-graph"></div>
       |    </section>
       |    <section id="bok-rdf-panel-nodes" class="bok-rdf-panel bok-rdf-panel-nodes" data-rdf-panel="nodes" role="tabpanel" aria-label="${_html_escape(_ui(locale, "rdf.graph.view.nodes"))}">
       |      <h2 class="bok-rdf-panel-title">${_html_escape(_ui(locale, "rdf.graph.view.nodes"))}</h2>
       |      <div id="bok-rdf-node-list-view" class="bok-rdf-node-list-view"></div>
       |    </section>
       |    <section id="bok-rdf-panel-triples" class="bok-rdf-panel bok-rdf-panel-triples" data-rdf-panel="triples" role="tabpanel" aria-label="${_html_escape(_ui(locale, "rdf.graph.view.triples"))}">
       |      <div class="bok-rdf-triples-header">
       |        <strong>${_html_escape(_ui(locale, "rdf.graph.triples.title"))}</strong>
       |        <span id="bok-rdf-triples-status">${_html_escape(_ui(locale, "rdf.graph.triples.loading"))}</span>
       |      </div>
       |      <pre id="bok-rdf-triples-view" class="bok-rdf-triples-view"></pre>
       |    </section>
       |  </div>
       |</section>
       |<script>
       |${_rdf_viewer_script(locale)}
       |</script>""".stripMargin

  private def _rdf_node_detail_script(locale: String): String =
    s"""(function() {
       |  const root = document.querySelector('.bok-rdf-node-page');
       |  const status = document.getElementById('bok-rdf-node-page-status');
       |  const content = document.getElementById('bok-rdf-node-page-content');
       |  if (!root || !status || !content) return;
       |  const params = new URLSearchParams(window.location.search);
       |  const nodeId = params.get('id') || params.get('node') || '';
       |  let termIndex = {};
       |  function escapeHtml(value) {
       |    return String(value == null ? '' : value).replace(/[&<>"']/g, function(c) {
       |      return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];
       |    });
       |  }
       |  function compactRdfLabel(value) {
       |    const text = String(value || '');
       |    const prefixes = rdfNamespacePrefixes();
       |    for (let i = 0; i < prefixes.length; i += 1) {
       |      const prefix = prefixes[i][0];
       |      const iri = prefixes[i][1];
       |      if (text.indexOf(iri) === 0) return prefix + ':' + text.substring(iri.length);
       |    }
       |    const parts = text.split(/[\\/#]/).filter(Boolean);
       |    return parts.length ? parts[parts.length - 1] : text;
       |  }
       |  function compactNodeLabel(node) {
       |    const label = String((node && node.label) || '').trim();
       |    const id = String((node && node.id) || '').trim();
       |    if (id && (label === id || label.indexOf('http://') === 0 || label.indexOf('https://') === 0)) return compactRdfLabel(id);
       |    return label || compactRdfLabel(id) || '-';
       |  }
       |  function rdfNamespacePrefixes() {
       |    return [
       |      ['rdf', 'http://www.w3.org/1999/02/22-rdf-syntax-ns#'],
       |      ['rdfs', 'http://www.w3.org/2000/01/rdf-schema#'],
       |      ['owl', 'http://www.w3.org/2002/07/owl#'],
       |      ['skos', 'http://www.w3.org/2004/02/skos/core#'],
       |      ['dcterms', 'http://purl.org/dc/terms/'],
       |      ['schema', 'https://schema.org/'],
       |      ['prov', 'http://www.w3.org/ns/prov#'],
       |      ['textus', 'https://www.simplemodeling.org/ns/textus#'],
       |      ['bok', 'https://www.simplemodeling.org/bok/']
       |    ];
       |  }
       |  function asArray(value) {
       |    if (Array.isArray(value)) return value;
       |    if (value == null) return [];
       |    return [value];
       |  }
       |  function uniqueStrings(values) {
       |    const seen = new Set();
       |    const result = [];
       |    values.forEach(function(item) {
       |      asArray(item).forEach(function(value) {
       |        const text = String(value || '').trim();
       |        if (text && !seen.has(text)) { seen.add(text); result.push(text); }
       |      });
       |    });
       |    return result;
       |  }
       |  function nodeTerms(node) {
       |    return asArray((node && node.terms) || []).map(function(term) { return termIndex[term]; }).filter(Boolean);
       |  }
       |  function termAnalysisKind(term) {
       |    if (term && term.term_type === 'event') return '${_javascript_string(_ui(locale, "term.analysis.koto"))}';
       |    if (term && term.term_type === 'rule') return '${_javascript_string(_ui(locale, "term.analysis.rule"))}';
       |    return '${_javascript_string(_ui(locale, "term.analysis.mono"))}';
       |  }
       |  function termGeneralCmlLinks(term) {
       |    const keys = ['entity', 'value', 'powertype', 'statemachine', 'rule', 'event', 'operation', 'component', 'service'];
       |    const result = [];
       |    asArray(term && term.cml).forEach(function(item) {
       |      if (!item || typeof item !== 'object') return;
       |      if (item.kind && (item.name || item.element_ref || item.value)) result.push(item.kind + ': ' + (item.name || item.element_ref || item.value));
       |      keys.forEach(function(key) { if (item[key]) result.push(key + ': ' + item[key]); });
       |    });
       |    return result;
       |  }
       |  function termCmlLinks(term) {
       |    const event = term && term.event ? term.event : {};
       |    return termGeneralCmlLinks(term).concat([
       |      ['component', event.cml_component],
       |      ['event', event.cml_event],
       |      ['statemachine', event.cml_statemachine]
       |    ].filter(function(item) { return item[1]; }).map(function(item) { return item[0] + ': ' + item[1]; }));
       |  }
       |  function nodeMonoKotoValues(node) {
       |    return uniqueStrings(nodeTerms(node).map(function(term) { return termAnalysisKind(term); }));
       |  }
       |  function nodeCmlLinkValues(node) {
       |    return uniqueStrings(nodeTerms(node).map(termCmlLinks));
       |  }
       |  function defaultPredicateProfile() {
       |    return {
       |      name: 'cncf-rdf-1.5-hop-v1',
       |      roles: {
       |        identity: ['rdf:type', 'owl:sameAs', 'schema:sameAs', 'skos:exactMatch', 'skos:closeMatch', 'textus:primaryRdfAnchor'],
       |        descriptive: ['rdfs:label', 'rdfs:comment', 'skos:prefLabel', 'skos:altLabel', 'skos:definition', 'schema:name', 'schema:title', 'schema:description', 'schema:summary'],
       |        link: ['rdfs:seeAlso', 'schema:about', 'schema:url', 'schema:memberOf'],
       |        hierarchy: ['skos:broader', 'skos:narrower', 'dcterms:isPartOf', 'dcterms:hasPart', 'schema:isPartOf', 'schema:hasPart'],
       |        provenance: ['dcterms:source', 'prov:wasDerivedFrom', 'prov:generatedAtTime', 'rdfs:isDefinedBy']
       |      }
       |    };
       |  }
       |  function activePredicateProfile(data) {
       |    const base = defaultPredicateProfile();
       |    const configured = (data && data.informationView && data.informationView.predicateProfile) || {};
       |    const roles = {};
       |    Object.keys(base.roles).forEach(function(role) {
       |      roles[role] = configured.roles && Object.prototype.hasOwnProperty.call(configured.roles, role) ?
       |        uniqueStrings([base.roles[role], configured.roles[role]]) : uniqueStrings([base.roles[role]]);
       |    });
       |    if (configured.roles) {
       |      Object.keys(configured.roles).forEach(function(role) {
       |        if (!roles[role]) roles[role] = uniqueStrings([configured.roles[role]]);
       |      });
       |    }
       |    return {name: configured.name || base.name, roles: roles};
       |  }
       |  function defaultInformationView() {
       |    return {
       |      name: 'cncf-rdf-1.5-hop-information-view-v1',
       |      label: 'CNCF RDF 1.5+hop Information View',
       |      concept: '1.5+hop',
       |      informationSchemas: [{
       |        name: 'rdf-resource-information-v1',
       |        label: 'RDF Resource Information',
       |        match: {nodeTypes: ['uri', 'literal']},
       |        requiredPredicates: ['rdf:type'],
       |        descriptivePredicates: ['rdfs:label', 'skos:prefLabel', 'schema:name'],
       |        outgoingRequiredPredicates: [],
       |        incomingRequiredPredicates: []
       |      }],
       |      predicateProfile: defaultPredicateProfile()
       |    };
       |  }
       |  function activeInformationView(data) {
       |    const base = defaultInformationView();
       |    const configured = (data && data.informationView) || {};
       |    return {
       |      name: configured.name || base.name,
       |      label: configured.label || base.label,
       |      concept: configured.concept || base.concept,
       |      informationSchemas: asArray(configured.informationSchemas || configured.information_schemas || base.informationSchemas),
       |      predicateProfile: activePredicateProfile(data)
       |    };
       |  }
       |  function selectedInformationSchema(node, informationView) {
       |    const candidates = asArray(informationView.informationSchemas);
       |    const safeNode = node || {};
       |    const schema = safeNode.schema || {};
       |    const requested = String(schema.informationSchema || schema.information_schema || safeNode.informationSchema || safeNode.information_schema || '').trim();
       |    if (requested) {
       |      const direct = candidates.filter(function(item) { return String(item.name || item.id || '') === requested || String(item.label || '') === requested; })[0];
       |      if (direct) return direct;
       |    }
       |    const nodeType = String(safeNode.informationType || safeNode.information_type || safeNode.node_type || safeNode.type || '').trim();
       |    const category = String(safeNode.category || '').trim();
       |    const matched = candidates.filter(function(item) {
       |      const match = item.match || {};
       |      const nodeTypes = uniqueStrings([item.nodeTypes, item.node_types, match.nodeTypes, match.node_types]);
       |      const categories = uniqueStrings([item.categories, match.categories]);
       |      const typeOk = nodeTypes.length === 0 || nodeTypes.indexOf(nodeType) >= 0;
       |      const categoryOk = categories.length === 0 || categories.indexOf(category) >= 0;
       |      return typeOk && categoryOk;
       |    })[0];
       |    return matched || candidates[0] || {};
       |  }
       |  function informationSchemaPredicates(informationSchema, kind) {
       |    if (!informationSchema) return [];
       |    const schema = informationSchema.schema || {};
       |    const fields = {
       |      required: [informationSchema.requiredPredicates, informationSchema.required_predicates, schema.requiredPredicates, schema.required_predicates],
       |      descriptive: [informationSchema.descriptivePredicates, informationSchema.descriptive_predicates, schema.descriptivePredicates, schema.descriptive_predicates],
       |      outgoing: [informationSchema.outgoingRequiredPredicates, informationSchema.outgoing_required_predicates, schema.outgoingRequiredPredicates, schema.outgoing_required_predicates],
       |      incoming: [informationSchema.incomingRequiredPredicates, informationSchema.incoming_required_predicates, schema.incomingRequiredPredicates, schema.incoming_required_predicates]
       |    };
       |    return uniqueStrings(fields[kind] || []);
       |  }
       |  function profileRolePredicates(profile, role) {
       |    return uniqueStrings([profile && profile.roles && profile.roles[role]]);
       |  }
       |  function interpretation(node, data) {
       |    const schema = node.schema || {};
       |    const view = activeInformationView(data);
       |    const profile = view.predicateProfile || activePredicateProfile(data);
       |    const informationSchema = selectedInformationSchema(node, view);
       |    const required = uniqueStrings([informationSchemaPredicates(informationSchema, 'required'), node.requiredPredicates, node.required_predicates, schema.requiredPredicates, schema.required_predicates]);
       |    const descriptive = uniqueStrings([informationSchemaPredicates(informationSchema, 'descriptive'), node.descriptivePredicates, node.descriptive_predicates, schema.descriptivePredicates, schema.descriptive_predicates]);
       |    const outgoing = uniqueStrings([informationSchemaPredicates(informationSchema, 'outgoing'), schema.outgoingRequiredPredicates, schema.outgoing_required_predicates]);
       |    const incoming = uniqueStrings([informationSchemaPredicates(informationSchema, 'incoming'), schema.incomingRequiredPredicates, schema.incoming_required_predicates]);
       |    return {
       |      informationView: {
       |        name: view.name || '-',
       |        concept: view.concept || '1.5+hop',
       |        label: view.label || '-',
       |        predicateProfile: profile.name || 'cncf-rdf-1.5-hop-v1'
       |      },
       |      information: {
       |        schema: informationSchema.name || informationSchema.id || 'rdf-resource-information-v1',
       |        schemaLabel: informationSchema.label || informationSchema.name || informationSchema.id || 'RDF Resource Information',
       |        type: node.informationType || node.information_type || node.node_type || node.type || 'unknown',
       |        category: node.category || '-',
       |        identity: profileRolePredicates(profile, 'identity'),
       |        description: uniqueStrings([profileRolePredicates(profile, 'descriptive'), descriptive]),
       |        links: profileRolePredicates(profile, 'link'),
       |        hierarchy: profileRolePredicates(profile, 'hierarchy'),
       |        provenance: profileRolePredicates(profile, 'provenance')
       |      },
       |      monoKoto: {
       |        classification: nodeMonoKotoValues(node),
       |        cmlLinkage: nodeCmlLinkValues(node)
       |      },
       |      schema: {
       |        required: required,
       |        nodeDescription: descriptive,
       |        directional: uniqueStrings([outgoing, incoming]),
       |        expansion: required.length + descriptive.length + outgoing.length + incoming.length === 0 ? 'predicate profile fallback' : 'node schema metadata'
       |      },
       |      predicate: {
       |        roles: Object.keys(profile.roles || {}).sort()
       |      }
       |    };
       |  }
       |  function cssName(value) {
       |    return String(value == null ? 'unknown' : value).toLowerCase().replace(/[^a-z0-9_-]+/g, '-');
       |  }
       |  function propertyList(items) {
       |    return Object.keys(items).map(function(key) {
       |      const value = items[key];
       |      if (value && typeof value === 'object' && !Array.isArray(value)) {
       |        return '<section class="bok-rdf-node-schema-group bok-rdf-node-schema-group-' + escapeHtml(cssName(key)) + '">' +
       |          '<h3>' + escapeHtml(key) + '</h3>' +
       |          '<dl class="bok-rdf-node-schema-object">' + propertyList(value) + '</dl>' +
       |        '</section>';
       |      }
       |      const rendered = Array.isArray(value) ? (value.length ? value.map(function(x) { return '<code>' + escapeHtml(x) + '</code>'; }).join(' ') : '<em>-</em>') : '<code>' + escapeHtml(value) + '</code>';
       |      return '<dt>' + escapeHtml(key) + '</dt><dd>' + rendered + '</dd>';
       |    }).join('');
       |  }
       |  function edgeItem(edge) {
       |    const direction = edge.source === nodeId ? 'outgoing' : (edge.target === nodeId ? 'incoming' : 'related');
       |    return '<li><span>' + escapeHtml(direction) + '</span><code title="' + escapeHtml(edge.source || '') + '">' + escapeHtml(compactRdfLabel(edge.source)) + '</code> <b>' + escapeHtml(compactRdfLabel(edge.label || edge.predicate)) + '</b> <code title="' + escapeHtml(edge.target || '') + '">' + escapeHtml(compactRdfLabel(edge.target)) + '</code></li>';
       |  }
       |  function render(data) {
       |    if (!nodeId) {
       |      status.textContent = '${_javascript_string(_ui(locale, "rdf.graph.node.missing.id"))}';
       |      return;
       |    }
       |    const nodes = data.nodes || [];
       |    const edges = data.edges || [];
       |    const node = nodes.filter(function(item) { return item.id === nodeId; })[0];
       |    if (!node) {
       |      status.textContent = '${_javascript_string(_ui(locale, "rdf.graph.node.not.found"))}';
       |      content.innerHTML = '<p class="bok-rdf-empty"><code>' + escapeHtml(nodeId) + '</code></p>';
       |      return;
       |    }
       |    const related = edges.filter(function(edge) { return edge.source === node.id || edge.target === node.id; });
       |    const monokoto = nodeMonoKotoValues(node);
       |    const cmllinks = nodeCmlLinkValues(node);
       |    status.textContent = compactNodeLabel(node) + ' / ' + related.length + ' ${_javascript_string(_ui(locale, "rdf.graph.node.connections"))}';
       |    content.innerHTML =
       |      '<div class="bok-rdf-node-detail-grid">' +
       |        '<section class="bok-rdf-node-detail-card bok-rdf-node-detail-card-main">' +
       |          '<span class="bok-dashboard-eyebrow">Information</span>' +
       |          '<h2>' + escapeHtml(compactNodeLabel(node)) + '</h2>' +
       |          '<dl class="bok-rdf-node-detail-dl">' +
       |            '<dt>Compact label</dt><dd><code>' + escapeHtml(compactNodeLabel(node)) + '</code></dd>' +
       |            '<dt>Full IRI</dt><dd class="bok-rdf-node-full-iri" title="' + escapeHtml(node.id) + '">' + escapeHtml(node.id) + '</dd>' +
       |            '<dt>Source label</dt><dd>' + escapeHtml(node.label || '-') + '</dd>' +
       |            '<dt>Category</dt><dd>' + escapeHtml(node.category || '-') + '</dd>' +
       |            '<dt>Type</dt><dd>' + escapeHtml(node.node_type || node.type || '-') + '</dd>' +
       |            '<dt>${_javascript_string(_ui(locale, "term.analysis.kind"))}</dt><dd>' + escapeHtml(monokoto.length ? monokoto.join(', ') : '-') + '</dd>' +
       |            '<dt>${_javascript_string(_ui(locale, "term.analysis.cml.linkage"))}</dt><dd>' + escapeHtml(cmllinks.length ? cmllinks.join(', ') : '-') + '</dd>' +
       |            '<dt>${_javascript_string(_ui(locale, "rdf.graph.node.connections"))}</dt><dd>' + escapeHtml(node.degree == null ? related.length : node.degree) + '</dd>' +
       |          '</dl>' +
       |          '<div class="bok-rdf-node-detail-actions"><a href="index.html?node=' + encodeURIComponent(node.id || '') + '">${_javascript_string(_ui(locale, "rdf.graph.node.neighborhood"))}</a><a href="index.html">${_javascript_string(_ui(locale, "rdf.graph.title"))}</a></div>' +
       |        '</section>' +
       |        '<section class="bok-rdf-node-detail-card"><h2>${_javascript_string(_ui(locale, "rdf.graph.node.schema"))}</h2><div class="bok-rdf-node-schema-groups">' + propertyList(interpretation(node, data)) + '</div></section>' +
       |        '<section class="bok-rdf-node-detail-card bok-rdf-node-detail-card-relations"><h2>${_javascript_string(_ui(locale, "rdf.graph.node.relations"))}</h2><ul>' + (related.length ? related.map(edgeItem).join('') : '<li>-</li>') + '</ul></section>' +
       |      '</div>';
       |  }
       |  const graphPromise = fetch(root.getAttribute('data-graph')).then(function(response) {
       |    if (!response.ok) throw new Error('missing graph metadata');
       |    return response.json();
       |  });
       |  const termsPromise = fetch(root.getAttribute('data-terms')).then(function(response) {
       |    if (!response.ok) return {terms: []};
       |    return response.json();
       |  }).catch(function() { return {terms: []}; });
       |  Promise.all([graphPromise, termsPromise]).then(function(results) {
       |    const terms = results[1];
       |    (terms.terms || []).forEach(function(term) {
       |      if (term && term.id) termIndex[term.id] = term;
       |    });
       |    render(results[0]);
       |  }).catch(function() {
       |    status.textContent = '${_javascript_string(_ui(locale, "rdf.graph.metadata.missing"))}';
       |  });
       |})();""".stripMargin

  private def _rdf_viewer_script(locale: String): String =
    s"""(function() {
       |  const root = document.querySelector('.bok-rdf-workspace');
       |  const graphTarget = document.getElementById('bok-rdf-viewer-graph');
       |  const graphStatus = document.getElementById('bok-rdf-viewer-status');
       |  const triplesTarget = document.getElementById('bok-rdf-triples-view');
       |  const triplesStatus = document.getElementById('bok-rdf-triples-status');
       |  const nodeListTarget = document.getElementById('bok-rdf-node-list-view');
       |  const input = document.getElementById('bok-rdf-category-filter');
       |  const termInput = document.getElementById('bok-rdf-term-filter');
       |  const tagInput = document.getElementById('bok-rdf-tag-filter');
       |  if (!root || !graphTarget || !graphStatus) return;
       |  const params = new URLSearchParams(window.location.search);
       |  const initialCategory = params.get('category') || '';
       |  const initialTerm = params.get('term') || '';
       |  const initialTag = params.get('tag') || '';
       |  const initialNode = params.get('node') || '';
       |  let focusedNodeId = initialNode;
       |  if (input) input.value = initialCategory;
       |  if (termInput) termInput.value = initialTerm;
       |  if (tagInput) tagInput.value = initialTag;
       |  function activate(view) {
       |    document.querySelectorAll('[data-rdf-view]').forEach(function(button) {
       |      button.classList.toggle('is-active', button.getAttribute('data-rdf-view') === view);
       |      button.setAttribute('aria-selected', button.getAttribute('data-rdf-view') === view ? 'true' : 'false');
       |    });
       |    document.querySelectorAll('[data-rdf-panel]').forEach(function(panel) {
       |      panel.classList.toggle('is-active', panel.getAttribute('data-rdf-panel') === view);
       |    });
       |  }
       |  document.querySelectorAll('[data-rdf-view]').forEach(function(button) {
       |    button.addEventListener('click', function() { activate(button.getAttribute('data-rdf-view')); });
       |  });
       |  function hasTerm(item, term) {
       |    return !term || (item.terms || []).indexOf(term) >= 0;
       |  }
       |  function hasTag(item, tag) {
       |    return !tag || (item.tags || []).indexOf(tag) >= 0;
       |  }
       |  function termLabel(termIndex, term) {
       |    const item = termIndex[term];
       |    return item ? (item.title || term) : term;
       |  }
       |  function activeTermIndex() {
       |    return window.__bokRdfTermIndex || {};
       |  }
       |  function nodeTerms(node) {
       |    const index = activeTermIndex();
       |    return asArray((node && node.terms) || []).map(function(term) { return index[term]; }).filter(Boolean);
       |  }
       |  function termAnalysisKind(term) {
       |    if (term && term.term_type === 'event') return '${_javascript_string(_ui(locale, "term.analysis.koto"))}';
       |    if (term && term.term_type === 'rule') return '${_javascript_string(_ui(locale, "term.analysis.rule"))}';
       |    return '${_javascript_string(_ui(locale, "term.analysis.mono"))}';
       |  }
       |  function termGeneralCmlLinks(term) {
       |    const keys = ['entity', 'value', 'powertype', 'statemachine', 'rule', 'event', 'operation', 'component', 'service'];
       |    const result = [];
       |    asArray(term && term.cml).forEach(function(item) {
       |      if (!item || typeof item !== 'object') return;
       |      if (item.kind && (item.name || item.element_ref || item.value)) result.push(item.kind + ': ' + (item.name || item.element_ref || item.value));
       |      keys.forEach(function(key) { if (item[key]) result.push(key + ': ' + item[key]); });
       |    });
       |    return result;
       |  }
       |  function termCmlLinks(term) {
       |    const event = term && term.event ? term.event : {};
       |    return termGeneralCmlLinks(term).concat([
       |      ['component', event.cml_component],
       |      ['event', event.cml_event],
       |      ['statemachine', event.cml_statemachine]
       |    ].filter(function(item) { return item[1]; }).map(function(item) { return item[0] + ': ' + item[1]; }));
       |  }
       |  function nodeMonoKotoValues(node) {
       |    return uniqueStrings(nodeTerms(node).map(function(term) { return termAnalysisKind(term); }));
       |  }
       |  function nodeCmlLinkValues(node) {
       |    return uniqueStrings(nodeTerms(node).map(termCmlLinks));
       |  }
       |  function renderGraph(data, category, term, tag, termIndex) {
    const allNodes = (data.nodes || []).slice().sort(function(a, b) {
      const degree = (b.degree || 0) - (a.degree || 0);
      return degree !== 0 ? degree : String(a.id || '').localeCompare(String(b.id || ''));
    });
    const allEdges = (data.edges || []).slice().sort(function(a, b) {
      return String(a.source || '').localeCompare(String(b.source || '')) ||
        String(a.target || '').localeCompare(String(b.target || '')) ||
        String(a.predicate || a.label || '').localeCompare(String(b.predicate || b.label || ''));
    });
    const categoryNodeIds = new Set();
    const termNodeIds = new Set();
    const tagNodeIds = new Set();
    allNodes.forEach(function(node) {
      if (category && node.category === category) categoryNodeIds.add(node.id);
      if (term && hasTerm(node, term)) termNodeIds.add(node.id);
      if (tag && hasTag(node, tag)) tagNodeIds.add(node.id);
    });
    const matchingEdges = allEdges.filter(function(edge) {
      const matchesCategory = !category || edge.category === category || categoryNodeIds.has(edge.source) || categoryNodeIds.has(edge.target);
      const matchesTerm = !term || hasTerm(edge, term) || termNodeIds.has(edge.source) || termNodeIds.has(edge.target);
      const matchesTag = !tag || hasTag(edge, tag) || tagNodeIds.has(edge.source) || tagNodeIds.has(edge.target);
      return matchesCategory && matchesTerm && matchesTag;
    });
    const edgeNodeIds = new Set();
    matchingEdges.forEach(function(edge) {
      if (edge.source) edgeNodeIds.add(edge.source);
      if (edge.target) edgeNodeIds.add(edge.target);
    });
    const matchingNodes = allNodes.filter(function(node) {
      const matchesCategory = !category || node.category === category || edgeNodeIds.has(node.id);
      const matchesTerm = !term || hasTerm(node, term) || edgeNodeIds.has(node.id);
      const matchesTag = !tag || hasTag(node, tag) || edgeNodeIds.has(node.id);
      return matchesCategory && matchesTerm && matchesTag;
    }).slice(0, 120);
    const nodeIds = new Set(matchingNodes.map(function(node) { return node.id; }));
    const edges = matchingEdges.filter(function(edge) {
      return nodeIds.has(edge.source) && nodeIds.has(edge.target);
    }).slice(0, 180);
    const focus = focusedNodeId && nodeIds.has(focusedNodeId) ? focusedNodeId : null;
    if (focusedNodeId && !focus) focusedNodeId = null;
    const focusGraph = focus ? focusedGraphSlice(focus, matchingNodes, edges) : {
      nodes: matchingNodes,
      edges: edges,
      roles: {}
    };
    const visibleNodes = focusGraph.nodes;
    const visibleEdges = focusGraph.edges;
    graphStatus.textContent = visibleNodes.length + ' nodes / ' + visibleEdges.length + ' edges' + (focus ? ' / focus: ' + compactRdfLabel(focus) : '') + (data.truncated ? ' (truncated)' : '');
    graphTarget.innerHTML =
      '<div class="bok-rdf-graph-summary">' +
        '<span><b>' + visibleNodes.length + '</b>nodes</span>' +
        '<span><b>' + visibleEdges.length + '</b>edges</span>' +
        '<span><b>' + escapeHtml(category || 'all') + '</b>category</span>' +
        '<span><b>' + escapeHtml(term ? termLabel(termIndex, term) : 'all') + '</b>term</span>' +
        '<span><b>' + escapeHtml(tag || 'all') + '</b>tag</span>' +
      '</div>' +
      '<div class="bok-rdf-focus-bar">' +
        (focus ? '<span>${_javascript_string(_ui(locale, "rdf.graph.focus.node"))}: <b>' + escapeHtml(compactRdfLabel(focus)) + '</b></span><button type="button" data-rdf-clear-focus="true">${_javascript_string(_ui(locale, "rdf.graph.focus.clear"))}</button>' : '<span>${_javascript_string(_ui(locale, "rdf.graph.focus.help"))}</span>') +
      '</div>' +
      '<div class="bok-rdf-graph-layout">' +
        '<div class="bok-rdf-graph-canvas" data-rdf-graph-canvas="true"></div>' +
      '</div>';
    const canvas = graphTarget.querySelector('[data-rdf-graph-canvas]');
    if (!canvas) return;
    if (visibleNodes.length === 0) {
      canvas.innerHTML = '<div class="bok-rdf-empty">No graph nodes match the current filter.</div>';
      return;
    }
    const clearButton = graphTarget.querySelector('[data-rdf-clear-focus]');
    if (clearButton) clearButton.addEventListener('click', function() { focusedNodeId = null; renderGraph(data, category, term, tag, termIndex); });
    graphTarget.querySelectorAll('[data-rdf-focus-node]').forEach(function(button) {
      button.addEventListener('click', function() {
        const node = visibleNodes.filter(function(item) { return item.id === button.getAttribute('data-rdf-focus-node'); })[0];
        if (node) showNodeDetail(node, visibleEdges);
      });
    });
    renderGraphSvg(canvas, visibleNodes, visibleEdges, focusGraph.roles, focus);
    renderNodeListView(visibleNodes, visibleEdges, focusGraph.roles, focus);
  }
  function renderNodeListView(nodes, edges, roles, focus) {
    if (!nodeListTarget) return;
    const sortedNodes = nodes.slice().sort(function(a, b) {
      const roleOrder = {focus: 0, near: 1, schema: 2, normal: 3};
      const ar = roleOrder[roles[a.id] || 'normal'] == null ? 9 : roleOrder[roles[a.id] || 'normal'];
      const br = roleOrder[roles[b.id] || 'normal'] == null ? 9 : roleOrder[roles[b.id] || 'normal'];
      return ar - br || String(a.category || '').localeCompare(String(b.category || '')) || String(compactNodeLabel(a)).localeCompare(String(compactNodeLabel(b)));
    });
    const edgeCounts = {};
    edges.forEach(function(edge) {
      if (edge.source) edgeCounts[edge.source] = (edgeCounts[edge.source] || 0) + 1;
      if (edge.target) edgeCounts[edge.target] = (edgeCounts[edge.target] || 0) + 1;
    });
    nodeListTarget.innerHTML =
      '<div class="bok-rdf-node-list-header">' +
        '<div><span class="bok-dashboard-eyebrow">RDF nodes</span><h2>${_javascript_string(_ui(locale, "rdf.graph.view.nodes"))}</h2><p>${_javascript_string(_ui(locale, "rdf.graph.nodes.description"))}</p></div>' +
        '<div class="bok-rdf-node-list-summary"><span><b>' + sortedNodes.length + '</b>nodes</span><span><b>' + edges.length + '</b>edges</span></div>' +
      '</div>' +
      '<div class="bok-rdf-node-list-grid">' +
        (sortedNodes.length ? sortedNodes.map(function(node) { return nodeListCard(node, edgeCounts[node.id] || 0, roles[node.id] || 'normal', focus); }).join('') : '<div class="bok-rdf-empty">No RDF nodes match the current filter.</div>') +
      '</div>';
  }
  function nodeListCard(node, connections, role, focus) {
    const interpretation = schemaInterpretation(node);
    const roleLabel = role === 'focus' ? 'focus' : (role === 'near' ? 'near' : (role === 'schema' ? 'schema' : 'visible'));
    const category = node.category || '-';
    const type = node.node_type || node.type || '-';
    const monokoto = nodeMonoKotoValues(node);
    const cmllinks = nodeCmlLinkValues(node);
    return '<article class="bok-rdf-node-list-card bok-rdf-node-list-card-' + escapeHtml(cssName(roleLabel)) + '">' +
      '<div class="bok-rdf-node-list-card-head"><span>' + escapeHtml(roleLabel) + '</span><a href="node.html?id=' + encodeURIComponent(node.id || '') + '">${_javascript_string(_ui(locale, "rdf.graph.node.full.detail"))}</a></div>' +
      '<h3 title="' + escapeHtml(node.id || '') + '">' + escapeHtml(compactNodeLabel(node)) + '</h3>' +
      '<dl>' +
        '<dt>Category</dt><dd>' + escapeHtml(category) + '</dd>' +
        '<dt>Type</dt><dd>' + escapeHtml(type) + '</dd>' +
        '<dt>${_javascript_string(_ui(locale, "term.analysis.kind"))}</dt><dd>' + escapeHtml(monokoto.length ? monokoto.join(', ') : '-') + '</dd>' +
        '<dt>${_javascript_string(_ui(locale, "term.analysis.cml.linkage"))}</dt><dd>' + escapeHtml(cmllinks.length ? cmllinks.join(', ') : '-') + '</dd>' +
        '<dt>${_javascript_string(_ui(locale, "rdf.graph.node.connections"))}</dt><dd>' + escapeHtml(node.degree == null ? connections : node.degree) + '</dd>' +
        '<dt>Schema</dt><dd><code>' + escapeHtml(interpretation.informationSchema) + '</code></dd>' +
        '<dt>Expansion</dt><dd>' + escapeHtml(interpretation.expansion) + '</dd>' +
      '</dl>' +
      '<div class="bok-rdf-node-list-card-actions"><a href="index.html?node=' + encodeURIComponent(node.id || '') + '">${_javascript_string(_ui(locale, "rdf.graph.node.neighborhood"))}</a></div>' +
    '</article>';
  }
  function focusedGraphSlice(focus, nodes, edges) {
    const nodeMap = {};
    nodes.forEach(function(node) { nodeMap[node.id] = node; });
    const near = new Set([focus]);
    const oneHopEdges = [];
    edges.forEach(function(edge) {
      if (edge.source === focus && edge.target) {
        near.add(edge.target);
        oneHopEdges.push(edge);
      }
      if (edge.target === focus && edge.source) {
        near.add(edge.source);
        oneHopEdges.push(edge);
      }
    });
    const visibleIds = new Set(Array.from(near));
    const schemaEdges = [];
    const schemaEdgeKeys = new Set();
    for (let depth = 0; depth < 4; depth += 1) {
      let changed = false;
      edges.forEach(function(edge) {
        const sourceVisible = visibleIds.has(edge.source);
        const targetVisible = visibleIds.has(edge.target);
        if (!sourceVisible && !targetVisible) return;
        const sourceNode = nodeMap[edge.source];
        const targetNode = nodeMap[edge.target];
        const requiredFromSource = sourceVisible && isSchemaRequiredEdge(edge, sourceNode, 'outgoing');
        const requiredFromTarget = targetVisible && isSchemaRequiredEdge(edge, targetNode, 'incoming');
        if (!requiredFromSource && !requiredFromTarget) return;
        const key = edgeKey(edge);
        if (!schemaEdgeKeys.has(key)) {
          schemaEdgeKeys.add(key);
          schemaEdges.push(edge);
        }
        if (edge.source && !visibleIds.has(edge.source)) {
          visibleIds.add(edge.source);
          changed = true;
        }
        if (edge.target && !visibleIds.has(edge.target)) {
          visibleIds.add(edge.target);
          changed = true;
        }
      });
      if (!changed) break;
    }
    const roles = {};
    Array.from(visibleIds).forEach(function(id) { roles[id] = near.has(id) ? 'near' : 'schema'; });
    roles[focus] = 'focus';
    const visibleNodes = nodes.filter(function(node) { return visibleIds.has(node.id); }).slice(0, 100);
    const visibleNodeIds = new Set(visibleNodes.map(function(node) { return node.id; }));
    const visibleEdgeKeys = new Set();
    const visibleEdges = [];
    oneHopEdges.concat(schemaEdges).forEach(function(edge) {
      const key = edgeKey(edge);
      if (!visibleEdgeKeys.has(key) && visibleNodeIds.has(edge.source) && visibleNodeIds.has(edge.target)) {
        visibleEdgeKeys.add(key);
        visibleEdges.push(edge);
      }
    });
    return {nodes: visibleNodes, edges: visibleEdges.slice(0, 160), roles: roles};
  }
  function isSchemaRequiredEdge(edge, node, direction) {
    if (!node) return isDefaultDescriptionPredicate(edge.predicate || edge.label);
    const schemaPredicates = schemaRequiredPredicates(node, direction);
    if (schemaPredicates.length === 0) return isDefaultDescriptionPredicate(edge.predicate || edge.label);
    return schemaPredicates.some(function(predicate) { return predicateMatches(edge, predicate); });
  }
  function selectedInformationSchema(node) {
    const informationView = currentInformationView();
    const candidates = asArray(informationView.informationSchemas);
    const safeNode = node || {};
    const nodeSchema = safeNode.schema || {};
    const requested = String(nodeSchema.informationSchema || nodeSchema.information_schema || safeNode.informationSchema || safeNode.information_schema || '').trim();
    if (requested) {
      const direct = candidates.filter(function(item) {
        return String(item.name || item.id || '') === requested || String(item.label || '') === requested;
      })[0];
      if (direct) return direct;
    }
    const nodeType = String(safeNode.informationType || safeNode.information_type || safeNode.node_type || safeNode.type || '').trim();
    const category = String(safeNode.category || '').trim();
    const matched = candidates.filter(function(item) {
      const match = item.match || {};
      const nodeTypes = uniqueStrings([item.nodeTypes, item.node_types, match.nodeTypes, match.node_types]);
      const categories = uniqueStrings([item.categories, match.categories]);
      const typeOk = nodeTypes.length === 0 || nodeTypes.indexOf(nodeType) >= 0;
      const categoryOk = categories.length === 0 || categories.indexOf(category) >= 0;
      return typeOk && categoryOk;
    })[0];
    if (matched) return matched;
    return candidates[0] || {};
  }
  function informationSchemaPredicates(informationSchema, kind) {
    if (!informationSchema) return [];
    const schema = informationSchema.schema || {};
    const fields = {
      required: [informationSchema.requiredPredicates, informationSchema.required_predicates, schema.requiredPredicates, schema.required_predicates],
      descriptive: [informationSchema.descriptivePredicates, informationSchema.descriptive_predicates, schema.descriptivePredicates, schema.descriptive_predicates],
      outgoing: [informationSchema.outgoingRequiredPredicates, informationSchema.outgoing_required_predicates, schema.outgoingRequiredPredicates, schema.outgoing_required_predicates],
      incoming: [informationSchema.incomingRequiredPredicates, informationSchema.incoming_required_predicates, schema.incomingRequiredPredicates, schema.incoming_required_predicates]
    };
    return uniqueStrings(fields[kind] || []);
  }
  function schemaRequiredPredicates(node, direction) {
    const schema = node.schema || {};
    const informationSchema = selectedInformationSchema(node);
    let values = [];
    [
      informationSchemaPredicates(informationSchema, 'required'),
      informationSchemaPredicates(informationSchema, 'descriptive'),
      informationSchemaPredicates(informationSchema, direction),
      node.requiredPredicates,
      node.required_predicates,
      node.descriptivePredicates,
      node.descriptive_predicates,
      schema.requiredPredicates,
      schema.required_predicates,
      schema.descriptivePredicates,
      schema.descriptive_predicates,
      schema[direction + 'RequiredPredicates'],
      schema[direction + '_required_predicates']
    ].forEach(function(item) { values = values.concat(asArray(item)); });
    return values.map(function(value) { return String(value); }).filter(Boolean);
  }
  function schemaInterpretation(node) {
    const schema = node.schema || {};
    const informationView = currentInformationView();
    const profile = informationView.predicateProfile || currentPredicateProfile();
    const informationSchema = selectedInformationSchema(node);
    const required = uniqueStrings([informationSchemaPredicates(informationSchema, 'required'), node.requiredPredicates, node.required_predicates, schema.requiredPredicates, schema.required_predicates]);
    const descriptive = uniqueStrings([informationSchemaPredicates(informationSchema, 'descriptive'), node.descriptivePredicates, node.descriptive_predicates, schema.descriptivePredicates, schema.descriptive_predicates]);
    const outgoing = uniqueStrings([informationSchemaPredicates(informationSchema, 'outgoing'), schema.outgoingRequiredPredicates, schema.outgoing_required_predicates]);
    const incoming = uniqueStrings([informationSchemaPredicates(informationSchema, 'incoming'), schema.incomingRequiredPredicates, schema.incoming_required_predicates]);
    const fallback = required.length + descriptive.length + outgoing.length + incoming.length === 0;
    const schemaPredicates = uniqueStrings([required, descriptive, outgoing, incoming]);
    const identityPredicates = profileRolePredicates(profile, 'identity');
    const descriptivePredicates = uniqueStrings([profileRolePredicates(profile, 'descriptive'), descriptive]);
    const linkPredicates = profileRolePredicates(profile, 'link');
    const hierarchyPredicates = profileRolePredicates(profile, 'hierarchy');
    const provenancePredicates = profileRolePredicates(profile, 'provenance');
    return {
      informationViewName: informationView.name || 'cncf-rdf-1.5-hop-information-view-v1',
      informationViewConcept: informationView.concept || '1.5+hop',
      informationSchema: informationSchema.name || informationSchema.id || 'rdf-resource-information-v1',
      informationSchemaLabel: informationSchema.label || informationSchema.name || informationSchema.id || 'RDF Resource Information',
      profile: profile.name || 'cncf-rdf-1.5-hop-v1',
      informationViewLabel: informationView.label || 'CNCF RDF 1.5+hop Information View',
      informationType: node.informationType || node.information_type || node.node_type || node.type || 'unknown',
      category: node.category || '-',
      predicateRoles: fallback ? Object.keys(profile.roles || {}).sort() : predicateRoles(schemaPredicates, profile),
      identityPredicates: identityPredicates,
      descriptivePredicates: descriptivePredicates,
      linkPredicates: linkPredicates,
      hierarchyPredicates: hierarchyPredicates,
      provenancePredicates: provenancePredicates,
      requiredPredicates: required,
      nodeDescriptivePredicates: descriptive,
      outgoingRequiredPredicates: outgoing,
      incomingRequiredPredicates: incoming,
      fallback: fallback,
      expansion: fallback ? 'predicate profile fallback' : 'node schema metadata'
    };
  }
  function defaultInformationView() {
    return {
      name: 'cncf-rdf-1.5-hop-information-view-v1',
      label: 'CNCF RDF 1.5+hop Information View',
      concept: '1.5+hop',
      attributes: [
          'information.schema',
          'information.type',
          'information.category',
          'information.identity',
          'information.description',
          'information.links',
          'information.hierarchy',
          'information.provenance',
          'schema.required',
          'schema.directional',
          'schema.expansion'
      ],
      defaultInformationSchema: 'rdf-resource-information-v1',
      informationSchemas: [
        {
          name: 'rdf-resource-information-v1',
          label: 'RDF Resource Information',
          match: {
            nodeTypes: ['uri', 'literal']
          },
          requiredPredicates: ['rdf:type'],
          descriptivePredicates: ['rdfs:label', 'skos:prefLabel', 'schema:name'],
          outgoingRequiredPredicates: [],
          incomingRequiredPredicates: []
        }
      ],
      predicateProfile: defaultPredicateProfile(),
      predicateRoleAttributes: {
        identity: 'information.identity',
        descriptive: 'information.description',
        link: 'information.links',
        hierarchy: 'information.hierarchy',
        provenance: 'information.provenance'
      },
      nodePredicateFields: {
        required: ['requiredPredicates', 'schema.requiredPredicates'],
        descriptive: ['descriptivePredicates', 'schema.descriptivePredicates'],
        outgoing: ['schema.outgoingRequiredPredicates'],
        incoming: ['schema.incomingRequiredPredicates']
      },
      expansion: {
        base: 'direct 1-hop',
        plus: 'schema-required descriptive triples',
        limit: 3
      }
    };
  }
  function defaultPredicateProfile() {
    return {
      name: 'cncf-rdf-1.5-hop-v1',
      roles: {
        identity: [
          'rdf:type',
          'owl:sameAs',
          'schema:sameAs',
          'skos:exactMatch',
          'skos:closeMatch',
          'textus:primaryRdfAnchor'
        ],
        descriptive: [
          'rdfs:label',
          'rdfs:comment',
          'skos:prefLabel',
          'skos:altLabel',
          'skos:definition',
          'schema:name',
          'schema:title',
          'schema:description',
          'schema:summary'
        ],
        link: [
          'rdfs:seeAlso',
          'schema:about',
          'schema:url',
          'schema:memberOf'
        ],
        hierarchy: [
          'skos:broader',
          'skos:narrower',
          'dcterms:isPartOf',
          'dcterms:hasPart',
          'schema:isPartOf',
          'schema:hasPart'
        ],
        provenance: [
          'dcterms:source',
          'prov:wasDerivedFrom',
          'prov:generatedAtTime',
          'rdfs:isDefinedBy'
        ]
      }
    };
  }
  function activePredicateProfile(data) {
    const base = defaultPredicateProfile();
    const configured = (data && data.informationView && data.informationView.predicateProfile) || {};
    const roles = {};
    Object.keys(base.roles).forEach(function(role) {
      roles[role] = configured.roles && Object.prototype.hasOwnProperty.call(configured.roles, role) ?
        uniqueStrings([base.roles[role], configured.roles[role]]) :
        uniqueStrings([base.roles[role]]);
    });
    if (configured.roles) {
      Object.keys(configured.roles).forEach(function(role) {
        if (!roles[role]) roles[role] = uniqueStrings([configured.roles[role]]);
      });
    }
    return {
      name: configured.name || base.name,
      roles: roles
    };
  }
  function activeInformationView(data) {
    const base = defaultInformationView();
    const configured = (data && data.informationView) || {};
    const profile = activePredicateProfile(data);
    const informationSchemas = configured.informationSchemas || configured.information_schemas || base.informationSchemas;
    return {
      name: configured.name || base.name,
      label: configured.label || base.label,
      concept: configured.concept || base.concept,
      attributes: configured.attributes || base.attributes,
      defaultInformationSchema: configured.defaultInformationSchema || configured.default_information_schema || base.defaultInformationSchema,
      informationSchemas: asArray(informationSchemas),
      predicateProfile: profile,
      predicateRoleAttributes: configured.predicateRoleAttributes || base.predicateRoleAttributes,
      nodePredicateFields: configured.nodePredicateFields || base.nodePredicateFields,
      expansion: configured.expansion || base.expansion
    };
  }
  function currentPredicateProfile() {
    return window.__bokRdfPredicateProfile || defaultPredicateProfile();
  }
  function currentInformationView() {
    return window.__bokRdfInformationView || defaultInformationView();
  }
  function profileRolePredicates(profile, role) {
    return uniqueStrings([profile && profile.roles && profile.roles[role]]);
  }
  function profilePredicates(profile) {
    const roles = (profile && profile.roles) || {};
    return uniqueStrings(Object.keys(roles).map(function(role) { return roles[role]; }));
  }
  function predicateRoles(predicates, profile) {
    const roles = (profile && profile.roles) || {};
    const result = [];
    Object.keys(roles).sort().forEach(function(role) {
      const rolePredicates = profileRolePredicates(profile, role);
      const matched = predicates.some(function(predicate) {
        return rolePredicates.some(function(candidate) {
          return predicateKey(predicate) === predicateKey(candidate);
        });
      });
      if (matched) result.push(role);
    });
    return result;
  }
  function uniqueStrings(values) {
    const seen = new Set();
    const result = [];
    values.forEach(function(item) {
      asArray(item).forEach(function(value) {
        const text = String(value || '').trim();
        if (text && !seen.has(text)) {
          seen.add(text);
          result.push(text);
        }
      });
    });
    return result;
  }
  function renderSchemaInterpretation(node) {
    const interpretation = schemaInterpretation(node);
    return '<div class="bok-rdf-node-schema">' +
      '<strong>${_javascript_string(_ui(locale, "rdf.graph.node.schema"))}</strong>' +
      '<div class="bok-rdf-node-schema-groups">' +
        schemaGroup('informationView', [
          ['name', interpretation.informationViewName],
          ['concept', interpretation.informationViewConcept],
          ['label', interpretation.informationViewLabel],
          ['predicateProfile', interpretation.profile]
        ]) +
        schemaGroup('information', [
          ['schema', interpretation.informationSchema],
          ['schemaLabel', interpretation.informationSchemaLabel],
          ['type', interpretation.informationType],
          ['category', interpretation.category],
          ['identity', interpretation.identityPredicates],
          ['description', interpretation.descriptivePredicates],
          ['links', interpretation.linkPredicates],
          ['hierarchy', interpretation.hierarchyPredicates],
          ['provenance', interpretation.provenancePredicates]
        ]) +
        schemaGroup('monoKoto', [
          ['classification', nodeMonoKotoValues(node)],
          ['cmlLinkage', nodeCmlLinkValues(node)]
        ]) +
        schemaGroup('schema', [
          ['required', interpretation.requiredPredicates],
          ['nodeDescription', interpretation.nodeDescriptivePredicates],
          ['directional', uniqueStrings([interpretation.outgoingRequiredPredicates, interpretation.incomingRequiredPredicates])],
          ['expansion', interpretation.expansion],
          ['fallback', interpretation.fallback ? 'true' : 'false']
        ]) +
        schemaGroup('predicate', [
          ['roles', interpretation.predicateRoles]
        ]) +
      '</div>' +
    '</div>';
  }
  function schemaGroup(name, entries) {
    return '<section class="bok-rdf-node-schema-group bok-rdf-node-schema-group-' + escapeHtml(cssName(name)) + '">' +
      '<h3>' + escapeHtml(name) + '</h3>' +
      '<dl class="bok-rdf-node-schema-object">' +
        entries.map(function(entry) { return schemaProperty(entry[0], entry[1]); }).join('') +
      '</dl>' +
    '</section>';
  }
  function schemaProperty(name, value) {
    const rendered = Array.isArray(value) ? (value.length ? value.map(function(item) {
      return '<code>' + escapeHtml(item) + '</code>';
    }).join(' ') : '<em>-</em>') : '<code>' + escapeHtml(value) + '</code>';
    return '<dt>' + escapeHtml(name) + '</dt><dd>' + rendered + '</dd>';
  }
  function asArray(value) {
    if (Array.isArray(value)) return value;
    if (value == null) return [];
    return [value];
  }
  function predicateMatches(edge, predicate) {
    const expected = predicateKey(predicate);
    return predicateKey(edge.predicate) === expected || predicateKey(edge.label) === expected;
  }
  function isDefaultDescriptionPredicate(predicate) {
    const key = predicateKey(predicate);
    return profilePredicates(currentPredicateProfile()).some(function(candidate) {
      return predicateKey(candidate) === key;
    });
  }
  function predicateKey(value) {
    return String(value || '').split(/[\\/#:]/).filter(Boolean).pop().toLowerCase().replace(/[^a-z0-9]/g, '');
  }
  function edgeKey(edge) {
    return String(edge.source || '') + '\\n' + String(edge.predicate || edge.label || '') + '\\n' + String(edge.target || '');
  }
  function showNodeDetail(node, edges) {
    const canvas = graphTarget.querySelector('[data-rdf-graph-canvas]');
    if (!canvas || !node) return;
    const monokoto = nodeMonoKotoValues(node);
    const cmllinks = nodeCmlLinkValues(node);
    let panel = canvas.querySelector('.bok-rdf-node-popover');
    if (!panel) {
      panel = document.createElement('aside');
      panel.setAttribute('class', 'bok-rdf-node-popover');
      panel.setAttribute('role', 'dialog');
      panel.setAttribute('aria-label', '${_javascript_string(_ui(locale, "rdf.graph.node.detail"))}');
      canvas.appendChild(panel);
    }
    const related = edges.filter(function(edge) { return edge.source === node.id || edge.target === node.id; }).slice(0, 6);
    panel.hidden = false;
    panel.innerHTML =
      '<button type="button" class="bok-rdf-node-popover-close" data-rdf-node-popover-close="true" aria-label="${_javascript_string(_ui(locale, "rdf.graph.node.close"))}">×</button>' +
      '<div class="bok-rdf-node-popover-eyebrow">${_javascript_string(_ui(locale, "rdf.graph.node.detail"))}</div>' +
      '<h2>' + escapeHtml(compactNodeLabel(node)) + '</h2>' +
      '<div class="bok-rdf-node-popover-actions bok-rdf-node-popover-actions-primary"><button type="button" data-rdf-neighborhood="true">${_javascript_string(_ui(locale, "rdf.graph.node.neighborhood"))}</button><a href="node.html?id=' + encodeURIComponent(node.id || '') + '">${_javascript_string(_ui(locale, "rdf.graph.node.full.detail"))}</a></div>' +
      '<dl>' +
        '<dt>Compact label</dt><dd class="bok-rdf-node-compact-label" title="' + escapeHtml(compactNodeLabel(node)) + '"><code>' + escapeHtml(compactNodeLabel(node)) + '</code></dd>' +
        '<dt>Full IRI</dt><dd class="bok-rdf-node-full-iri" title="' + escapeHtml(node.id) + '">' + escapeHtml(node.id) + '</dd>' +
        '<dt>Source label</dt><dd>' + escapeHtml(node.label || '-') + '</dd>' +
        '<dt>Category</dt><dd>' + escapeHtml(node.category || '-') + '</dd>' +
        '<dt>Type</dt><dd>' + escapeHtml(node.node_type || node.type || '-') + '</dd>' +
        '<dt>${_javascript_string(_ui(locale, "term.analysis.kind"))}</dt><dd>' + escapeHtml(monokoto.length ? monokoto.join(', ') : '-') + '</dd>' +
        '<dt>${_javascript_string(_ui(locale, "term.analysis.cml.linkage"))}</dt><dd>' + escapeHtml(cmllinks.length ? cmllinks.join(', ') : '-') + '</dd>' +
        '<dt>${_javascript_string(_ui(locale, "rdf.graph.node.connections"))}</dt><dd>' + escapeHtml(node.degree == null ? '-' : node.degree) + '</dd>' +
      '</dl>' +
      renderSchemaInterpretation(node) +
      '<div class="bok-rdf-node-popover-relations"><strong>${_javascript_string(_ui(locale, "rdf.graph.node.relations"))}</strong><ul>' +
        (related.length ? related.map(function(edge) { return '<li>' + escapeHtml(compactRdfLabel(edge.source)) + ' <b>' + escapeHtml(compactRdfLabel(edge.label || edge.predicate)) + '</b> ' + escapeHtml(compactRdfLabel(edge.target)) + '</li>'; }).join('') : '<li>-</li>') +
      '</ul></div>';
    const close = panel.querySelector('[data-rdf-node-popover-close]');
    if (close) close.addEventListener('click', function() { panel.hidden = true; });
    const focusButton = panel.querySelector('[data-rdf-neighborhood]');
    if (focusButton) focusButton.addEventListener('click', function() {
      focusedNodeId = node.id;
      renderGraph(window.__bokRdfGraphData, input ? input.value.trim() : '', termInput ? termInput.value.trim() : '', tagInput ? tagInput.value.trim() : '', window.__bokRdfTermIndex || {});
    });
  }
  function renderGraphSvg(canvas, nodes, edges, roles, focus) {
    const svgNs = "http://www.w3.org/2000/svg";
    const width = 1120;
    const height = 660;
    const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    svg.setAttribute('class', 'bok-rdf-graph-svg');
    svg.setAttribute('viewBox', '0 0 ' + width + ' ' + height);
    svg.setAttribute('role', 'img');
    svg.setAttribute('aria-label', 'RDF graph');
    const defs = document.createElementNS(svgNs, 'defs');
    const marker = document.createElementNS(svgNs, 'marker');
    marker.setAttribute('id', 'bok-rdf-arrow');
    marker.setAttribute('viewBox', '0 0 10 10');
    marker.setAttribute('refX', '8');
    marker.setAttribute('refY', '5');
    marker.setAttribute('markerWidth', '6');
    marker.setAttribute('markerHeight', '6');
    marker.setAttribute('orient', 'auto-start-reverse');
    const arrow = document.createElementNS(svgNs, 'path');
    arrow.setAttribute('d', 'M 0 0 L 10 5 L 0 10 z');
    arrow.setAttribute('class', 'bok-rdf-graph-arrow');
    marker.appendChild(arrow);
    defs.appendChild(marker);
    svg.appendChild(defs);
    const positions = {};
    const cx = width / 2;
    const cy = height / 2;
    const rx = width * 0.38;
    const ry = height * 0.32;
    nodes.forEach(function(node, index) {
      const angle = nodes.length === 1 ? -Math.PI / 2 : (2 * Math.PI * index / nodes.length) - Math.PI / 2;
      positions[node.id] = {
        x: nodes.length === 1 ? cx : cx + Math.cos(angle) * rx,
        y: nodes.length === 1 ? cy : cy + Math.sin(angle) * ry
      };
    });
    const edgeLayer = document.createElementNS(svgNs, 'g');
    edgeLayer.setAttribute('class', 'bok-rdf-graph-edges');
    edges.forEach(function(edge) {
      const source = positions[edge.source];
      const target = positions[edge.target];
      if (!source || !target) return;
      const line = document.createElementNS(svgNs, 'line');
      line.setAttribute('class', 'bok-rdf-graph-edge');
      line.setAttribute('x1', source.x);
      line.setAttribute('y1', source.y);
      line.setAttribute('x2', target.x);
      line.setAttribute('y2', target.y);
      line.setAttribute('marker-end', 'url(#bok-rdf-arrow)');
      edgeLayer.appendChild(line);
      const text = document.createElementNS(svgNs, 'text');
      text.setAttribute('class', 'bok-rdf-graph-edge-label');
      text.setAttribute('x', (source.x + target.x) / 2);
      text.setAttribute('y', (source.y + target.y) / 2 - 6);
      text.textContent = compactRdfLabel(edge.label || edge.predicate || 'related');
      edgeLayer.appendChild(text);
    });
    svg.appendChild(edgeLayer);
    const nodeLayer = document.createElementNS(svgNs, 'g');
    nodeLayer.setAttribute('class', 'bok-rdf-graph-nodes');
    nodes.forEach(function(node) {
      const point = positions[node.id];
      const group = document.createElementNS(svgNs, 'g');
      const role = roles[node.id] || 'normal';
      group.setAttribute('class', 'bok-rdf-graph-node bok-rdf-graph-node-' + cssName(node.node_type || node.type || 'unknown') + ' bok-rdf-graph-node-role-' + role);
      group.setAttribute('transform', 'translate(' + point.x + ' ' + point.y + ')');
      group.setAttribute('data-rdf-focus-node', node.id);
      group.setAttribute('tabindex', '0');
      group.setAttribute('role', 'button');
      const circle = document.createElementNS(svgNs, 'circle');
      circle.setAttribute('r', Math.max(18, Math.min(34, 18 + (node.degree || 0) * 2)));
      const title = document.createElementNS(svgNs, 'title');
      title.textContent = compactNodeLabel(node) + ' / ' + (node.category || '-') + ' / ' + String(node.id || '');
      const text = document.createElementNS(svgNs, 'text');
      text.setAttribute('y', 48);
      text.textContent = compactNodeLabel(node);
      group.appendChild(title);
      group.appendChild(circle);
      group.appendChild(text);
      group.addEventListener('click', function() { showNodeDetail(node, edges); });
      group.addEventListener('keydown', function(event) { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); showNodeDetail(node, edges); } });
      nodeLayer.appendChild(group);
    });
    svg.appendChild(nodeLayer);
    canvas.innerHTML = '';
    canvas.appendChild(svg);
  }
  function renderTriples(text) {
       |    if (!triplesTarget || !triplesStatus) return;
       |    triplesTarget.textContent = text;
       |    triplesStatus.textContent = text.split('\\n').filter(function(line) { return line.trim(); }).length + ' lines';
       |  }
       |  function label(value) {
       |    const parts = String(value || '').split(/[\\/#]/).filter(Boolean);
       |    return parts.length ? parts[parts.length - 1] : value;
       |  }
       |  function compactNodeLabel(node) {
       |    if (!node) return '';
       |    return compactRdfLabel(node.id || node.label || '');
       |  }
       |  function compactRdfLabel(value) {
       |    const text = String(value || '');
       |    const compact = compactUri(text);
       |    if (compact) return compact;
       |    return label(text);
       |  }
       |  function compactUri(value) {
       |    const text = String(value || '');
       |    const prefixes = rdfNamespacePrefixes();
       |    for (let i = 0; i < prefixes.length; i += 1) {
       |      if (text.indexOf(prefixes[i][1]) === 0) {
       |        const local = text.substring(prefixes[i][1].length);
       |        return local ? prefixes[i][0] + ':' + local : prefixes[i][0] + ':';
       |      }
       |    }
       |    const compact = text.match(/^([A-Za-z][A-Za-z0-9_-]*):(.+)$$/);
       |    return compact && !/^https?:/.test(text) ? text : '';
       |  }
       |  function rdfNamespacePrefixes() {
       |    return [
       |      ['rdf', 'http://www.w3.org/1999/02/22-rdf-syntax-ns#'],
       |      ['rdfs', 'http://www.w3.org/2000/01/rdf-schema#'],
       |      ['owl', 'http://www.w3.org/2002/07/owl#'],
       |      ['xsd', 'http://www.w3.org/2001/XMLSchema#'],
       |      ['skos', 'http://www.w3.org/2004/02/skos/core#'],
       |      ['dcterms', 'http://purl.org/dc/terms/'],
       |      ['prov', 'http://www.w3.org/ns/prov#'],
       |      ['schema', 'https://schema.org/'],
       |      ['bok', 'https://www.simplemodeling.org/bok/'],
       |      ['cozy-video', 'https://www.simplemodeling.org/ns/cozy/video#'],
       |      ['textus', 'https://www.simplemodeling.org/ns/textus#'],
       |      ['sm', 'https://www.simplemodeling.org/']
       |    ];
       |  }
       |  function escapeHtml(value) {
       |    return String(value == null ? '' : value).replace(/[&<>"']/g, function(c) {
       |      return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];
       |    });
       |  }
       |  function cssName(value) {
       |    return String(value == null ? 'unknown' : value).toLowerCase().replace(/[^a-z0-9_-]+/g, '-');
       |  }
       |  const graphPromise = fetch(root.getAttribute('data-graph')).then(function(response) {
       |    if (!response.ok) throw new Error('missing graph metadata');
       |    return response.json();
       |  });
       |  const termsPromise = fetch(root.getAttribute('data-terms')).then(function(response) {
       |    if (!response.ok) return {terms: []};
       |    return response.json();
       |  }).catch(function() { return {terms: []}; });
       |  Promise.all([graphPromise, termsPromise]).then(function(results) {
       |    const data = results[0];
       |    const terms = results[1];
       |    const termIndex = {};
       |    (terms.terms || []).forEach(function(term) {
       |      if (term && term.id) termIndex[term.id] = term;
       |    });
       |    window.__bokRdfGraphData = data;
       |    window.__bokRdfTermIndex = termIndex;
       |    window.__bokRdfPredicateProfile = activePredicateProfile(data);
       |    window.__bokRdfInformationView = activeInformationView(data);
       |    renderGraph(data, initialCategory, initialTerm, initialTag, termIndex);
       |    function refresh() { focusedNodeId = null; renderGraph(data, input ? input.value.trim() : '', termInput ? termInput.value.trim() : '', tagInput ? tagInput.value.trim() : '', termIndex); }
       |    if (input) input.addEventListener('input', refresh);
       |    if (termInput) termInput.addEventListener('input', refresh);
       |    if (tagInput) tagInput.addEventListener('input', refresh);
       |  }).catch(function() {
       |    graphStatus.textContent = '${_javascript_string(_ui(locale, "rdf.graph.metadata.missing"))}';
       |    graphTarget.innerHTML = '<div class="bok-rdf-empty">${_javascript_string(_ui(locale, "rdf.graph.metadata.missing"))}</div>';
       |  });
       |  if (triplesTarget) {
       |    fetch(root.getAttribute('data-triples')).then(function(response) {
       |      if (!response.ok) throw new Error('missing RDF triples');
       |      return response.text();
       |    }).then(renderTriples).catch(function() {
       |      if (triplesStatus) triplesStatus.textContent = '${_javascript_string(_ui(locale, "rdf.graph.triples.missing"))}';
       |      triplesTarget.textContent = '';
       |    });
       |  }
       |}());""".stripMargin


  private def _term_type_summary_cards(locale: String, terms: Vector[TermEntry]): String = {
    val counts = terms.groupBy(_.termType).mapValues(_.size).toMap
    val items = Vector("concept", "event", "actor", "role", "rule").map { termtype =>
      val count = counts.getOrElse(termtype, 0)
      s"""<span class="bok-term-type-summary-item"><b>${count}</b>${_html_escape(_term_type_label(termtype, locale))}</span>"""
    }.mkString("\n")
    s"""<div class="bok-term-type-summary">${items}</div>"""
  }

  private def _mono_koto_summary_cards(locale: String, terms: Vector[TermEntry]): String = {
    val counts = terms.groupBy(_.monoKotoKind).mapValues(_.size).toMap
    val diagnostics = terms.count(_.cmlLinks.isEmpty)
    val items = Vector("mono", "koto", "rule").map { kind =>
      val count = counts.getOrElse(kind, 0)
      s"""<span class="bok-term-type-summary-item"><b>${count}</b>${_html_escape(_mono_koto_label(kind, locale))}</span>"""
    } :+ s"""<span class="bok-term-type-summary-item"><b>${diagnostics}</b>${_html_escape(_ui(locale, "term.analysis.unlinked"))}</span>"""
    s"""<div class="bok-term-type-summary bok-mono-koto-summary"><strong>${_html_escape(_ui(locale, "term.analysis"))}</strong>${items.mkString("\n")}</div>"""
  }

  private def _glossary_mono_koto_workflow(config: BuildConfig, locale: String, terms: Vector[TermEntry]): String = {
    val termcount = terms.size
    val extraction = _glossary_candidate_extraction_progress(config, terms)
    val candidateactual = extraction.actual
    val candidateplanned = extraction.planned
    val definedactual = terms.count(_is_term_basic_defined)
    val classifiedactual = terms.count(_is_term_classified)
    val rdfconnected = terms.count(_.rdfRefs.nonEmpty)
    val cmlconnected = terms.count(_.cmlLinks.nonEmpty)
    val stages = Vector(
      WorkflowStage("01", "extract", candidateactual, candidateplanned, extraction.systemError, extraction.systemErrorFiles, Vector("article.dashboard" -> "../articles/index.html", "scenario.dashboard" -> "../scenarios/index.html"), _workflow_source_detail(locale, extraction.missing)),
      WorkflowStage("02", "define", definedactual, Some(termcount), Vector("recent.terms" -> "#term-recent-terms"), _workflow_definition_detail(locale, terms)),
      WorkflowStage("03", "classify", classifiedactual, Some(termcount), Vector("check.types" -> "#term-analysis-routes"), _workflow_refine_detail(locale, terms)),
      WorkflowStage("04", "rdf", rdfconnected, Some(termcount), Vector("connect.rdf" -> "#term-rdf-connections"), _workflow_term_detail(locale, "rdf", terms.filter(_.rdfRefs.isEmpty))),
      WorkflowStage("05", "cml", cmlconnected, Some(termcount), Vector("connect.cml" -> "#term-project-connections"), _workflow_term_detail(locale, "cml", terms.filter(_.cmlLinks.isEmpty)))
    )
    val systemerrorfiles = stages.flatMap(_.systemErrorFiles).distinct
    val systemerror = if (stages.exists(_.systemError)) _workflow_system_error(locale, systemerrorfiles) else ""
    s"""<div class="bok-mono-koto-workflow">
       |  <p class="bok-mono-koto-lead">${_html_escape(_ui(locale, "term.analysis.workflow.lead"))}</p>
       |  ${systemerror}
       |  <div class="bok-workflow-stage-grid">
       |    ${stages.map(_workflow_stage_card(locale, _)).mkString("\n")}
       |  </div>
       |</div>""".stripMargin
  }

  private final case class WorkflowStage(
    step: String,
    key: String,
    actual: Int,
    planned: Option[Int],
    systemError: Boolean,
    systemErrorFiles: Vector[String],
    actions: Vector[(String, String)],
    detailHtml: String
  )

  private object WorkflowStage {
    def apply(step: String, key: String, actual: Int, planned: Option[Int], actions: Vector[(String, String)], detailHtml: String): WorkflowStage =
      WorkflowStage(step, key, actual, planned, false, Vector.empty, actions, detailHtml)
  }

  private final case class WorkflowSourceStatus(kind: String, title: String, href: String)

  private final case class WorkflowExtractionProgress(
    actual: Int,
    planned: Option[Int],
    missing: Vector[WorkflowSourceStatus],
    systemError: Boolean,
    systemErrorFiles: Vector[String]
  )

  private def _glossary_candidate_extraction_progress(
    config: BuildConfig,
    terms: Vector[TermEntry]
  ): WorkflowExtractionProgress = {
    val articleindex = _document_fragment_index(config)
    val scenarioindex = _scenario_index(config)
    val systemerror = articleindex.isEmpty || scenarioindex.isEmpty
    val systemerrorfiles =
      Vector(
        articleindex.isEmpty -> "doxsite.d/metadata/documents/fragments.json",
        scenarioindex.isEmpty -> "doxsite.d/metadata/scenarios/scenarios.json"
      ).collect {
        case (true, path) => path
      }
    val articles = articleindex.map(_.fragments.filter(_is_candidate_extraction_article)).getOrElse(Vector.empty)
    val scenarios = scenarioindex.map(_.scenarios).getOrElse(Vector.empty)
    val plannedarticles = articles.count(_.termExtraction.isPlanned)
    val plannedscenarios = scenarios.count(_.termExtraction.isPlanned)
    val extractedarticles = articles.count { article =>
      article.termExtraction.isPlanned &&
      _is_term_extraction_done(article.termExtraction)
    }
    val extractedscenarios = scenarios.count { scenario =>
      scenario.termExtraction.isPlanned &&
      _is_term_extraction_done(scenario.termExtraction)
    }
    val planned = plannedarticles + plannedscenarios
    val actual = extractedarticles + extractedscenarios
    val missingarticles = articles.filter { article =>
      article.termExtraction.isPlanned &&
      !_is_term_extraction_done(article.termExtraction)
    }.map { article =>
      WorkflowSourceStatus("article", article.effectiveHeadline.getOrElse(article.publicpath), "../" + article.publicpath)
    }
    val missingscenarios = scenarios.filter { scenario =>
      scenario.termExtraction.isPlanned &&
      !_is_term_extraction_done(scenario.termExtraction)
    }.map { scenario =>
      WorkflowSourceStatus("scenario", scenario.title, "../" + scenario.publicpath)
    }
    WorkflowExtractionProgress(actual, if (planned > 0) Some(planned) else None, missingarticles ++ missingscenarios, systemerror, systemerrorfiles)
  }

  private def _is_candidate_extraction_article(fragment: DocumentFragment): Boolean = {
    val source = _normalize_bok_path(fragment.sourcepath)
    val publicpath = _normalize_bok_path(fragment.publicpath)
    fragment.category.nonEmpty &&
    !source.startsWith("glossary/") &&
    !source.startsWith("scenario/") &&
    !source.startsWith("scenarios/") &&
    !source.startsWith("history/") &&
    !source.startsWith("manual/") &&
    !source.endsWith("/index.dox") &&
    !source.endsWith("/index.md") &&
    !publicpath.endsWith("/index.html")
  }

  private def _normalize_bok_path(value: String): String =
    value.stripPrefix("./").stripPrefix("../").stripPrefix("website.d/").stripPrefix("src/main/doxsite/")

  private def _is_term_extraction_done(progress: TermExtraction): Boolean =
    progress.isExtracted

  private def _workflow_stage_card(locale: String, stage: WorkflowStage): String = {
    val planned = stage.planned.filter(_ > 0)
    val planneddisplay = planned.map(_.toString).getOrElse("-")
    val percent = planned.map(x => math.max(0, math.min(100, stage.actual * 100 / x))).getOrElse(0)
    val statushtml =
      if (stage.systemError)
        _workflow_stage_status(locale, "system.error")
      else planned match {
        case None => _workflow_stage_status(locale, "system.error")
        case Some(x) if stage.actual >= x => _workflow_stage_status(locale, "complete")
        case Some(_) => _workflow_stage_status(locale, "incomplete")
      }
    s"""<article class="bok-workflow-stage bok-workflow-stage-${_html_escape(stage.step)}">
       |  <div class="bok-workflow-stage-head">
       |    <span>${_html_escape(stage.step)}</span>
       |    <strong>${_html_escape(_ui(locale, s"term.analysis.workflow.${stage.key}.title"))}</strong>
       |  </div>
       |  <div class="bok-workflow-stage-metric">
       |    <b>${stage.actual} / ${_html_escape(planneddisplay)}</b>
       |    <span>${_html_escape(_ui(locale, "term.analysis.workflow.metric.actual.planned"))}</span>
       |  </div>
       |  <div class="bok-workflow-progress" aria-label="${_html_escape(_ui(locale, "term.analysis.workflow.progress"))}"><span style="width:${percent}%"></span></div>
       |  ${statushtml}
       |  <p>${_html_escape(_ui(locale, s"term.analysis.workflow.${stage.key}.description"))}</p>
       |  ${stage.detailHtml}
       |  ${_workflow_stage_actions(locale, stage.actions)}
       |</article>""".stripMargin
  }

  private def _is_mono_koto_classified(termtype: String): Boolean =
    termtype match {
      case "unclassified" => false
      case "concept" | "entity" | "actor" | "role" | "resource" | "artifact" => true
      case "event" | "action" | "process" | "task" | "rule" | "state" | "scenario" => true
      case _ => false
    }

  private def _is_term_classified(term: TermEntry): Boolean =
    _normalized_term_type(term.termType) != "unclassified"

  private def _is_term_basic_defined(term: TermEntry): Boolean = {
    val hasdefinition = _strip_html(term.definitionHtml).trim.nonEmpty || term.summary.exists(_.trim.nonEmpty)
    term.title.trim.nonEmpty && hasdefinition
  }

  private def _strip_html(value: String): String =
    value.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ")

  private def _workflow_stage_actions(locale: String, links: Vector[(String, String)]): String =
    links.map {
      case (key, href) =>
        s"""<a class="bok-workflow-action" href="${_html_escape(href)}">${_html_escape(_ui(locale, s"term.analysis.workflow.action.${key}"))}</a>"""
    }.mkString("""<div class="bok-workflow-actions">""", "", "</div>")

  private def _workflow_system_error(locale: String, files: Vector[String]): String = {
    val details =
      if (files.isEmpty)
        ""
      else
        files.map(x => s"""<li><code>${_html_escape(x)}</code></li>""").mkString(
          s"""<div class="bok-workflow-system-error-files"><span>${_html_escape(_ui(locale, "term.analysis.workflow.system.error.files"))}</span><ul>""",
          "",
          "</ul></div>"
        )
    s"""<div class="bok-workflow-system-error">
       |  <strong>${_html_escape(_ui(locale, "term.analysis.workflow.system.error.title"))}</strong>
       |  <p>${_html_escape(_ui(locale, "term.analysis.workflow.system.error.description"))}</p>
       |  ${details}
       |  <p class="bok-workflow-system-error-repair">${_html_escape(_ui(locale, "term.analysis.workflow.system.error.repair"))}</p>
       |</div>""".stripMargin
  }

  private def _workflow_stage_status(locale: String, key: String): String =
    s"""<div class="bok-workflow-stage-status bok-workflow-stage-status-${_html_escape(key)}">${_html_escape(_ui(locale, s"term.analysis.workflow.status.${key}"))}</div>"""

  private def _workflow_source_detail(locale: String, missing: Vector[WorkflowSourceStatus]): String =
    _workflow_missing_detail(locale, missing.size, missing.take(4).map { source =>
      val label = _ui(locale, s"term.analysis.workflow.source.${source.kind}")
      s"""<li><a href="${_html_escape(source.href)}">${_html_escape(source.title)}</a><span>${_html_escape(label)}</span></li>"""
    })

  private def _workflow_term_detail(locale: String, key: String, missing: Vector[TermEntry]): String =
    _workflow_missing_detail(locale, missing.size, missing.take(4).map { term =>
      s"""<li><a href="${_html_escape(term.glossaryHref)}">${_html_escape(term.title)}</a><span>${_html_escape(_ui(locale, s"term.analysis.workflow.detail.${key}"))}</span></li>"""
    })

  private def _workflow_refine_detail(locale: String, terms: Vector[TermEntry]): String = {
    val missing = terms.filter(x => _normalized_term_type(x.termType) == "unclassified")
    val summary = _workflow_refine_summary(locale, terms)
    _workflow_missing_detail(locale, missing.size, missing.take(4).map { term =>
      s"""<li><a href="${_html_escape(term.glossaryHref)}">${_html_escape(term.title)}</a><span>${_html_escape(_ui(locale, "term.analysis.workflow.detail.refine"))}</span></li>"""
    }, summary)
  }

  private def _workflow_definition_detail(locale: String, terms: Vector[TermEntry]): String = {
    val missing = terms.filterNot(_is_term_basic_defined)
    _workflow_missing_detail(locale, missing.size, missing.take(4).map { term =>
      s"""<li><a href="${_html_escape(term.glossaryHref)}">${_html_escape(term.title)}</a><span>${_html_escape(_term_definition_issue_label(locale, term))}</span></li>"""
    })
  }

  private def _term_definition_issue_label(locale: String, term: TermEntry): String = {
    val hasdefinition = _strip_html(term.definitionHtml).trim.nonEmpty || term.summary.exists(_.trim.nonEmpty)
    if (!hasdefinition)
      _ui(locale, "term.analysis.workflow.detail.define.basic")
    else
      _ui(locale, "term.analysis.workflow.detail.define")
  }

  private def _workflow_refine_summary(locale: String, terms: Vector[TermEntry]): String = {
    val counts = terms.groupBy(x => _mono_koto_group_for_type(_normalized_term_type(x.termType))).map {
      case (k, v) => k -> v.size
    }
    val groups = Vector("mono", "koto", "unclassified")
    val items = groups.map { group =>
      val count = counts.getOrElse(group, 0)
      s"""<span class="bok-term-type-chip">${_html_escape(_ui(locale, s"term.analysis.workflow.group.${group}"))} ${count}</span>"""
    }.mkString
    s"""<div class="bok-workflow-refinement-split"><section><h4>${_html_escape(_ui(locale, "term.analysis.workflow.refine.breakdown"))}</h4><div class="bok-term-type-chip-list">${items}</div></section></div>"""
  }

  private def _mono_koto_group_for_type(termtype: String): String =
    termtype match {
      case "concept" | "entity" | "actor" | "role" | "resource" | "artifact" => "mono"
      case "event" | "action" | "process" | "task" | "state" | "scenario" | "rule" => "koto"
      case _ => "unclassified"
    }

  private def _workflow_missing_detail(locale: String, missingcount: Int, items: Vector[String], extraHtml: String = ""): String = {
    val title =
      if (missingcount == 0)
        _ui(locale, "term.analysis.workflow.detail.none")
      else
        _uif(locale, "term.analysis.workflow.detail.missing", missingcount.toString)
    val list =
      if (items.isEmpty)
        ""
      else
        s"""<ul>${items.mkString}</ul>"""
    s"""<div class="bok-workflow-detail"><strong>${_html_escape(title)}</strong>${extraHtml}${list}</div>"""
  }

  private def _term_extraction_progress_card(locale: String, actual: Int, planned: Int): String = {
    val percent = if (planned <= 0) 0 else math.max(0, math.min(100, actual * 100 / planned))
    val statushtml =
      if (planned <= 0)
        _workflow_stage_status(locale, "system.error")
      else if (actual >= planned)
        _workflow_stage_status(locale, "complete")
      else
        _workflow_stage_status(locale, "incomplete")
    s"""<div class="bok-term-extraction-progress">
       |  <div class="bok-workflow-stage-metric">
       |    <b>${actual} / ${if (planned <= 0) "-" else planned.toString}</b>
       |    <span>${_html_escape(_ui(locale, "term.analysis.workflow.metric.actual.planned"))}</span>
       |  </div>
       |  <div class="bok-workflow-progress" aria-label="${_html_escape(_ui(locale, "term.analysis.workflow.progress"))}"><span style="width:${percent}%"></span></div>
       |  ${statushtml}
       |  <p>${_html_escape(_ui(locale, "term.extraction.progress.description"))}</p>
       |</div>""".stripMargin
  }

  private def _glossary_rdf_connection_card(
    locale: String,
    terms: Vector[TermEntry],
    dashboard: Option[BokDashboard]
  ): String = {
    val termcount = terms.size
    val linkedterms = terms.count(_.rdfRefs.nonEmpty)
    val rdfrefs = terms.flatMap(_.rdfRefs)
    val externaluris = rdfrefs.count(ref => ref.resource.startsWith("http://") || ref.resource.startsWith("https://"))
    val triples = dashboard.map(_.rdf.tripleCount.toString).getOrElse("-")
    val nodes = dashboard.map(_.rdf.resourceCount.toString).getOrElse("-")
    val breakdown = _rdf_connection_breakdown(locale, rdfrefs)
    s"""<div class="bok-connection-card bok-connection-card-rdf">
       |  <p>${_html_escape(_ui(locale, "term.rdf.connection.lead"))}</p>
       |  <div class="bok-connection-metrics">
       |    ${_connection_metric(_ui(locale, "term.rdf.metric.linked.terms"), s"${linkedterms} / ${termcount}", _ui(locale, "term.rdf.metric.linked.terms.note"))}
       |    ${_connection_metric(_ui(locale, "term.rdf.metric.triples"), triples, _ui(locale, "term.rdf.metric.triples.note"))}
       |    ${_connection_metric(_ui(locale, "term.rdf.metric.nodes"), nodes, _ui(locale, "term.rdf.metric.nodes.note"))}
       |    ${_connection_metric(_ui(locale, "term.rdf.metric.external"), externaluris.toString, _ui(locale, "term.rdf.metric.external.note"))}
       |  </div>
       |  <div class="bok-connection-breakdown">
       |    <strong>${_html_escape(_ui(locale, "term.rdf.breakdown"))}</strong>
       |    ${breakdown}
       |  </div>
       |  <p class="bok-connection-checkpoints">${_html_escape(_ui(locale, "term.rdf.checkpoints"))}</p>
       |  <div class="bok-special-links"><a class="bok-special-link" href="../rdf/index.html">${_html_escape(_ui(locale, "rdf.graph.title"))}</a></div>
       |</div>""".stripMargin
  }

  private def _glossary_usage_card(locale: String, terms: Vector[TermEntry]): String = {
    val termcount = terms.size
    val articleused = terms.count(_.articleRefs.nonEmpty)
    val scenarioused = terms.count(_.event.exists(_.scenarios.nonEmpty))
    val relatedused = terms.count(_.termRefs.nonEmpty)
    val unused = terms.filterNot(_is_term_used_in_bok)
    val unusedlist =
      if (unused.isEmpty)
        s"""<p class="bok-card-muted">${_html_escape(_ui(locale, "term.usage.unused.none"))}</p>"""
      else
        unused.take(6).map { term =>
          s"""<li><a href="${_html_escape(term.glossaryHref)}">${_html_escape(term.title)}</a></li>"""
        }.mkString("""<ul class="list-group bok-alert-list">""", "", "</ul>")
    s"""<div class="bok-connection-card bok-connection-card-usage">
       |  <p>${_html_escape(_ui(locale, "term.usage.lead"))}</p>
       |  <div class="bok-connection-metrics">
       |    ${_connection_metric(_ui(locale, "term.usage.metric.article"), s"${articleused} / ${termcount}", _ui(locale, "term.usage.metric.article.note"))}
       |    ${_connection_metric(_ui(locale, "term.usage.metric.scenario"), s"${scenarioused} / ${termcount}", _ui(locale, "term.usage.metric.scenario.note"))}
       |    ${_connection_metric(_ui(locale, "term.usage.metric.related"), s"${relatedused} / ${termcount}", _ui(locale, "term.usage.metric.related.note"))}
       |    ${_connection_metric(_ui(locale, "term.usage.metric.unused"), unused.size.toString, _ui(locale, "term.usage.metric.unused.note"))}
       |  </div>
       |  <div class="bok-connection-breakdown">
       |    <strong>${_html_escape(_ui(locale, "term.usage.unused.title"))}</strong>
       |    ${unusedlist}
       |  </div>
       |</div>""".stripMargin
  }

  private def _is_term_used_in_bok(term: TermEntry): Boolean =
    term.articleRefs.nonEmpty ||
      term.event.exists(_.scenarios.nonEmpty) ||
      term.termRefs.nonEmpty

  private def _glossary_project_connection_card(
    locale: String,
    terms: Vector[TermEntry],
    projects: Vector[CozyBokProjectPublisher.ResolvedBokProject]
  ): String = {
    val termcount = terms.size
    val cmllinkedterms = terms.count(_.cmlLinks.nonEmpty)
    val projectlinkedterms = terms.count(_.cmlLinks.nonEmpty)
    val sourceonlyprojects = projects.count(_.projectmode == "source-only")
    val breakdown = _cml_connection_breakdown(locale, terms.flatMap(_.cmlLinks))
    val linkedterms = _project_connected_term_list(locale, terms.filter(_.cmlLinks.nonEmpty))
    s"""<div class="bok-connection-card bok-connection-card-project">
       |  <p>${_html_escape(_ui(locale, "term.project.connection.lead"))}</p>
       |  <div class="bok-connection-metrics">
       |    ${_connection_metric(_ui(locale, "term.project.metric.cml.linked.terms"), s"${cmllinkedterms} / ${termcount}", _ui(locale, "term.project.metric.cml.linked.terms.note"))}
       |    ${_connection_metric(_ui(locale, "term.project.metric.project.linked.terms"), projectlinkedterms.toString, _ui(locale, "term.project.metric.project.linked.terms.note"))}
       |    ${_connection_metric(_ui(locale, "term.project.metric.projects"), projects.size.toString, _ui(locale, "term.project.metric.projects.note"))}
       |    ${_connection_metric(_ui(locale, "term.project.metric.source.only"), sourceonlyprojects.toString, _ui(locale, "term.project.metric.source.only.note"))}
       |  </div>
       |  <div class="bok-connection-breakdown">
       |    <strong>${_html_escape(_ui(locale, "term.project.cml.breakdown"))}</strong>
       |    ${breakdown}
       |  </div>
       |  <div class="bok-connection-breakdown">
       |    <strong>${_html_escape(_ui(locale, "term.project.connected.terms"))}</strong>
       |    ${linkedterms}
       |  </div>
       |  <p class="bok-connection-checkpoints">${_html_escape(_ui(locale, "term.project.checkpoints"))}</p>
       |  <p class="bok-connection-note">${_html_escape(_ui(locale, "term.project.source.only.note"))}</p>
       |  <p class="bok-connection-note">${_html_escape(_ui(locale, "term.project.cml.note"))}</p>
       |  <div class="bok-special-links"><a class="bok-special-link" href="../projects/index.html">${_html_escape(_ui(locale, "project.title"))}</a></div>
       |</div>""".stripMargin
  }

  private def _project_connected_term_list(locale: String, terms: Vector[TermEntry]): String = {
    if (terms.isEmpty)
      s"""<p class="bok-connection-note">${_html_escape(_ui(locale, "term.project.connected.terms.empty"))}</p>"""
    else
      terms.sortBy(_.title).take(8).map { term =>
        val kinds = term.cmlLinks.map(_.kind).distinct.map { kind =>
          s"""<span class="bok-connection-chip"><b>${_html_escape(_cml_kind_label(locale, kind))}</b></span>"""
        }.mkString
        s"""<li><a href="${_html_escape(term.glossaryHref)}">${_html_escape(term.title)}</a><span class="bok-connection-chip-list">${kinds}</span></li>"""
      }.mkString("""<ul class="bok-project-connected-term-list">""", "", "</ul>")
  }

  private def _connection_metric(label: String, value: String, note: String): String =
    s"""<div class="bok-connection-metric">
       |  <span>${_html_escape(label)}</span>
       |  <b>${_html_escape(value)}</b>
       |  <em>${_html_escape(note)}</em>
       |</div>""".stripMargin

  private def _rdf_connection_breakdown(locale: String, refs: Vector[TermRdfReference]): String = {
    val counts = Vector(
      "outgoing" -> refs.count(_.direction == "outgoing"),
      "incoming" -> refs.count(_.direction == "incoming"),
      "identity" -> refs.count(ref => _rdf_predicate_group(ref.predicate) == "identity"),
      "description" -> refs.count(ref => _rdf_predicate_group(ref.predicate) == "description"),
      "hierarchy" -> refs.count(ref => _rdf_predicate_group(ref.predicate) == "hierarchy"),
      "provenance" -> refs.count(ref => _rdf_predicate_group(ref.predicate) == "provenance")
    )
    _connection_breakdown_chips(locale, counts)
  }

  private def _rdf_predicate_group(predicate: Option[String]): String = {
    val p = predicate.getOrElse("").toLowerCase(Locale.ROOT)
    if (p.contains("sameas") || p.contains("exactmatch") || p.contains("closematch") || p.contains("primaryrdfanchor") || p.endsWith("type"))
      "identity"
    else if (p.contains("label") || p.contains("comment") || p.contains("description") || p.contains("definition"))
      "description"
    else if (p.contains("broader") || p.contains("narrower") || p.contains("subclass") || p.contains("partof"))
      "hierarchy"
    else if (p.contains("source") || p.contains("provenance") || p.contains("wasderivedfrom") || p.contains("evidence"))
      "provenance"
    else
      "links"
  }

  private def _cml_connection_breakdown(locale: String, links: Vector[TermCmlLink]): String = {
    val kinds = Vector("entity", "value", "powertype", "event", "operation", "statemachine", "rule", "component", "service")
    val counts = kinds.map(kind => _cml_kind_label(locale, kind) -> links.count(_.kind == kind))
    _connection_breakdown_chips(locale, counts)
  }

  private def _cml_kind_label(locale: String, kind: String): String =
    kind match {
      case "entity" => "entity"
      case "value" => "value"
      case "powertype" => "powertype"
      case "event" => "event"
      case "operation" => "operation"
      case "statemachine" => "statemachine"
      case "rule" => "rule"
      case "component" => "component"
      case "service" => "service"
      case other => other
    }

  private def _connection_breakdown_chips(locale: String, counts: Vector[(String, Int)]): String =
    counts.map {
      case (label, count) =>
        s"""<span class="bok-connection-chip"><b>${_html_escape(label)}</b><em>${count}</em></span>"""
    }.mkString("""<div class="bok-connection-chip-list">""", "", "</div>")

  private def _safe_resolved_project_packages(config: BuildConfig): Vector[CozyBokProjectPublisher.ResolvedBokProject] =
    try {
      _resolved_project_packages(config)
    } catch {
      case NonFatal(_) => Vector.empty
    }

  private def _term_type_chip_list(termtypes: Vector[String], locale: String): String =
    termtypes.map { termtype =>
      s"""<span class="bok-term-type-chip bok-term-type-chip-${_html_escape(termtype)}">${_html_escape(_term_type_label(termtype, locale))}</span>"""
    }.mkString("""<div class="bok-term-type-chip-list">""", "", "</div>")

  private val _term_type_route_groups: Vector[(String, Vector[String])] =
    Vector(
      "unclassified" -> Vector("unclassified"),
      "mono" -> Vector("concept", "entity", "actor", "role", "resource", "artifact"),
      "koto" -> Vector("event", "action", "process", "task", "rule", "state", "scenario")
    )

  private val _term_type_route_order: Vector[String] =
    _term_type_route_groups.flatMap(_._2)

  private def _glossary_type_analysis_routes(locale: String, terms: Vector[TermEntry]): String = {
    val grouped = terms.groupBy(x => _normalized_term_type(x.termType)).withDefaultValue(Vector.empty)
    val sections = _term_type_route_groups.map {
      case (kind, termtypes) =>
        val count = termtypes.map(t => grouped(t).size).sum
        val issuecount = termtypes.flatMap(t => grouped(t)).map(_glossary_term_issues(_, locale).size).sum
        val routes = termtypes.map(_glossary_type_analysis_route_card(locale, grouped, _)).mkString("\n")
        s"""<section class="bok-term-analysis-route-group bok-term-analysis-route-group-${_html_escape(kind)}">
           |  <div class="bok-term-analysis-route-group-head">
           |    <div>
           |      <span class="badge bok-badge-info">${_html_escape(_term_type_route_group_label(kind, locale))}</span>
           |      <h4>${_html_escape(_ui(locale, s"term.analysis.route.group.${kind}.title"))}</h4>
           |      <p>${_html_escape(_ui(locale, s"term.analysis.route.group.${kind}.description"))}</p>
           |    </div>
           |    <dl>
           |      <dt>${_html_escape(_ui(locale, "term.metric.terms"))}</dt><dd>${count}</dd>
           |      <dt>${_html_escape(_ui(locale, "term.analysis.route.issue.count"))}</dt><dd>${issuecount}</dd>
           |    </dl>
           |  </div>
           |  <div class="bok-term-analysis-route-grid">${routes}</div>
           |</section>""".stripMargin
    }.mkString("\n")
    s"""<div id="term-analysis-routes" class="bok-term-analysis-route-groups">${sections}</div>"""
  }

  private def _glossary_type_analysis_route_card(
    locale: String,
    grouped: Map[String, Vector[TermEntry]],
    termtype: String
  ): String = {
    val xs = grouped.getOrElse(termtype, Vector.empty).sortBy(x => (-_glossary_term_score(x), x.title))
    val examples = _glossary_route_term_links(xs.take(3))
    val issuecount = xs.map(_glossary_term_issues(_, locale).size).sum
    s"""<article class="bok-term-analysis-route bok-term-analysis-route-${_html_escape(termtype)}">
       |  <div class="bok-term-analysis-route-head">
       |    <span class="badge bok-badge-info">${_html_escape(_term_type_label(termtype, locale))}</span>
       |    <strong>${xs.size}</strong>
       |  </div>
       |  <p>${_html_escape(_ui(locale, s"term.analysis.route.${termtype}.description"))}</p>
       |  <dl class="bok-term-analysis-route-checkpoints">
       |    <dt>${_html_escape(_ui(locale, "term.analysis.route.checkpoints"))}</dt>
       |    <dd>${_html_escape(_ui(locale, s"term.analysis.route.${termtype}.checkpoints"))}</dd>
       |    <dt>${_html_escape(_ui(locale, "term.analysis.route.issue.count"))}</dt>
       |    <dd>${issuecount}</dd>
       |  </dl>
       |  ${examples}
       |  <div class="bok-workflow-actions"><a class="bok-workflow-action" href="../manual/index.html#glossary-term-classification">${_html_escape(_ui(locale, "term.analysis.route.manual"))}</a></div>
       |</article>""".stripMargin
  }

  private def _is_entity_nesting_relation(value: String): Boolean = {
    val v = Option(value).map(_.trim.toLowerCase.replace("_", "-")).getOrElse("")
    v == "parent" ||
      v == "child" ||
      v == "part-of" ||
      v == "has-part" ||
      v == "contains" ||
      v == "contained-by" ||
      v == "broader" ||
      v == "narrower"
  }

  private val _operation_target_order: Vector[String] =
    Vector("entity", "file", "url", "external-id", "rdf-node", "artifact", "message-event", "job-task", "none")

  private val _operation_target_groups: Vector[(String, Vector[String])] =
    Vector(
      "managed" -> Vector("entity", "artifact", "message-event", "job-task"),
      "addressable" -> Vector("file", "url", "external-id", "rdf-node"),
      "none" -> Vector("none")
    )

  private def _glossary_operation_target_card(locale: String, terms: Vector[TermEntry]): String = {
    s"""<div id="term-operation-targets" class="bok-operation-targets">
       |  <p class="bok-card-lead">${_html_escape(_ui(locale, "term.operation.targets.lead"))}</p>
       |  <div class="bok-software-term-grid">
       |    ${_space_knowledge_panel(locale, terms)}
       |    ${_space_term_panel(locale, terms)}
       |    ${_space_project_panel(locale, terms)}
       |  </div>
       |  <div class="bok-workflow-actions">
       |    <a class="bok-workflow-action" href="#term-project-connections">${_html_escape(_ui(locale, "term.operation.targets.action.project"))}</a>
       |    <a class="bok-workflow-action" href="#term-rdf-connections">${_html_escape(_ui(locale, "term.operation.targets.action.rdf"))}</a>
       |    <a class="bok-workflow-action" href="#term-analysis-routes">${_html_escape(_ui(locale, "term.operation.targets.action.types"))}</a>
       |  </div>
       |</div>""".stripMargin
  }

  private def _space_knowledge_panel(locale: String, terms: Vector[TermEntry]): String = {
    val articlelinked = terms.count(_.articleRefs.nonEmpty)
    val scenariolinked = terms.count(_.event.exists(_.scenarios.nonEmpty))
    val rdflinked = terms.count(_.rdfRefs.nonEmpty)
    val relationlinked = terms.count(_.termRefs.nonEmpty)
    s"""<section class="bok-software-term-panel bok-software-term-panel-knowledge">
       |  <div class="bok-software-term-panel-head">
       |    <span>${_html_escape(_ui(locale, "term.space.knowledge.eyebrow"))}</span>
       |    <h4>${_html_escape(_ui(locale, "term.space.knowledge.title"))}</h4>
       |    <strong>${terms.size}</strong>
       |  </div>
       |  <p>${_html_escape(_ui(locale, "term.space.knowledge.description"))}</p>
       |  <dl class="bok-software-term-metrics">
       |    <dt>${_html_escape(_ui(locale, "term.space.metric.article"))}</dt><dd>${articlelinked}</dd>
       |    <dt>${_html_escape(_ui(locale, "term.space.metric.scenario"))}</dt><dd>${scenariolinked}</dd>
       |    <dt>${_html_escape(_ui(locale, "term.space.metric.rdf"))}</dt><dd>${rdflinked}</dd>
       |    <dt>${_html_escape(_ui(locale, "term.space.metric.term.relation"))}</dt><dd>${relationlinked}</dd>
       |  </dl>
       |</section>""".stripMargin
  }

  private def _space_term_panel(locale: String, allterms: Vector[TermEntry]): String = {
    val terms = allterms.filter(_is_resource_term)
    val entityterms = terms.filter(term => _effective_resource_type(term).contains("entity"))
    val typedterms = terms.filter(term => _effective_resource_type(term).isDefined)
    val untypedterms = terms.filter(term => _effective_resource_type(term).isEmpty)
    val nonentitychips = _resource_type_order.filterNot(_ == "entity").map { resourceType =>
      val count = terms.count(term => _effective_resource_type(term).contains(resourceType))
      s"""<span class="bok-software-target-chip"><b>${_html_escape(_ui(locale, s"term.resource.type.${resourceType}.label"))}</b><em>${count}</em></span>"""
    }.mkString("""<div class="bok-software-target-chip-list">""", "", "</div>")
    s"""<section class="bok-software-term-panel bok-software-term-panel-entity">
       |  <div class="bok-software-term-panel-head">
       |    <span>${_html_escape(_ui(locale, "term.space.term.eyebrow"))}</span>
       |    <h4>${_html_escape(_ui(locale, "term.space.term.title"))}</h4>
       |    <strong>${terms.size}</strong>
       |  </div>
       |  <p>${_html_escape(_ui(locale, "term.space.term.description"))}</p>
       |  <dl class="bok-software-term-metrics">
       |    <dt>${_html_escape(_ui(locale, "term.project.metric.resource.entity"))}</dt><dd>${entityterms.size}</dd>
       |    <dt>${_html_escape(_ui(locale, "term.project.metric.resource.typed"))}</dt><dd>${typedterms.size}</dd>
       |    <dt>${_html_escape(_ui(locale, "term.project.metric.resource.untyped"))}</dt><dd>${untypedterms.size}</dd>
       |  </dl>
       |  ${nonentitychips}
       |  ${_glossary_route_term_links(terms.sortBy(x => (-_glossary_term_score(x), x.title)).take(4))}
       |</section>""".stripMargin
  }

  private def _space_project_panel(locale: String, terms: Vector[TermEntry]): String = {
    val actorroleterms = terms.filter(term => Set("actor", "role").contains(_normalized_term_type(term.termType)))
    val actorroleentity = actorroleterms.count(_.cmlLinks.exists(x => _normalize_token(x.kind) == "entity"))
    val cmlentity = terms.count(_.cmlLinks.exists(x => _normalize_token(x.kind) == "entity"))
    val cmlother = terms.count(term => term.cmlLinks.exists(x => _normalize_token(x.kind) != "entity"))
    val artifact = terms.count(term => _effective_resource_type(term).contains("artifact"))
    s"""<section class="bok-software-term-panel bok-software-term-panel-non-entity">
       |  <div class="bok-software-term-panel-head">
       |    <span>${_html_escape(_ui(locale, "term.space.project.eyebrow"))}</span>
       |    <h4>${_html_escape(_ui(locale, "term.space.project.title"))}</h4>
       |    <strong>${cmlentity + cmlother + artifact}</strong>
       |  </div>
       |  <p>${_html_escape(_ui(locale, "term.space.project.description"))}</p>
       |  <dl class="bok-software-term-metrics">
       |    <dt>${_html_escape(_ui(locale, "term.project.metric.mapping.cml.entity"))}</dt><dd>${cmlentity}</dd>
       |    <dt>${_html_escape(_ui(locale, "term.project.metric.mapping.cml.other"))}</dt><dd>${cmlother}</dd>
       |    <dt>${_html_escape(_ui(locale, "term.project.metric.actor.role.entity"))}</dt><dd>${actorroleentity}</dd>
       |    <dt>${_html_escape(_ui(locale, "term.project.metric.mapping.publication"))}</dt><dd>${artifact}</dd>
       |  </dl>
       |  ${_glossary_route_term_links(terms.filter(_.cmlLinks.nonEmpty).sortBy(x => (-_glossary_term_score(x), x.title)).take(4))}
       |</section>""".stripMargin
  }

  private val _resource_type_order: Vector[String] =
    Vector("entity", "file", "url", "external_id", "rdf_node", "artifact", "dataset", "service_endpoint")

  private def _is_resource_term(term: TermEntry): Boolean =
    _normalized_term_type(term.termType) == "resource" ||
      _effective_resource_type(term).isDefined

  private def _effective_resource_type(term: TermEntry): Option[String] =
    term.resourceType.map(_normalize_token).orElse {
      _normalized_term_type(term.termType) match {
        case "entity" => Some("entity")
        case "artifact" => Some("artifact")
        case _ => None
      }
    }.map(_.replace("-", "_"))

  private def _term_matches_operation_target(term: TermEntry, target: String): Boolean = {
    val matches = _operation_targets_for_term(term)
    target match {
      case "none" => matches.isEmpty
      case _ => matches.contains(target)
    }
  }

  private def _operation_targets_for_term(term: TermEntry): Set[String] = {
    val termtype = _normalized_term_type(term.termType)
    val cmlkinds = term.cmlLinks.map(x => _normalize_token(x.kind)).toSet
    val rdfresources = term.rdfRefs.map(_.resource)
    Set.empty[String] ++
      _operation_target_if(termtype == "entity" || cmlkinds.contains("entity"), "entity") ++
      _operation_target_if(termtype == "artifact" || cmlkinds.contains("artifact"), "artifact") ++
      _operation_target_if(termtype == "event" || cmlkinds.contains("event"), "message-event") ++
      _operation_target_if(termtype == "task" || cmlkinds.contains("operation"), "job-task") ++
      _operation_target_if(term.rdfRefs.nonEmpty, "rdf-node") ++
      _operation_target_if(rdfresources.exists(_looks_like_url), "url") ++
      _operation_target_if(rdfresources.exists(_looks_like_external_id), "external-id") ++
      _operation_target_if(rdfresources.exists(_looks_like_file_target), "file")
  }

  private def _operation_target_if(condition: Boolean, value: String): Set[String] =
    if (condition) Set(value) else Set.empty

  private def _normalize_token(value: String): String =
    Option(value).map(_.trim.toLowerCase(Locale.ROOT).replace("_", "-")).getOrElse("")

  private def _looks_like_url(value: String): Boolean = {
    val v = Option(value).map(_.trim.toLowerCase(Locale.ROOT)).getOrElse("")
    v.startsWith("http://") || v.startsWith("https://")
  }

  private def _looks_like_external_id(value: String): Boolean = {
    val v = Option(value).map(_.trim.toLowerCase(Locale.ROOT)).getOrElse("")
    v.contains("wikidata.org/entity/") ||
      v.contains("doi.org/") ||
      v.startsWith("doi:") ||
      v.startsWith("isbn:") ||
      v.startsWith("orcid:")
  }

  private def _looks_like_file_target(value: String): Boolean = {
    val v = Option(value).map(_.trim.toLowerCase(Locale.ROOT)).getOrElse("")
    v.startsWith("file:") ||
      v.startsWith("/") ||
      v.endsWith(".pdf") ||
      v.endsWith(".dox") ||
      v.endsWith(".md") ||
      v.endsWith(".json") ||
      v.endsWith(".ttl") ||
      v.endsWith(".jsonld")
  }

  private def _normalized_term_type(value: String): String =
    if (Option(value).map(_.trim).getOrElse("").isEmpty)
      "unclassified"
    else
      value.trim

  private def _term_type_route_group_label(value: String, locale: String): String = value match {
    case "unclassified" => _ui(locale, "term.type.unclassified")
    case other => _mono_koto_label(other, locale)
  }

  private def _glossary_route_term_links(terms: Vector[TermEntry]): String =
    if (terms.isEmpty)
      """<p class="bok-card-muted">-</p>"""
    else
      terms.map { term =>
        s"""<li><a href="${_html_escape(term.glossaryHref)}">${_html_escape(term.title)}</a>${_reading_label(term)}</li>"""
      }.mkString("""<ul class="list-group bok-map-list bok-term-analysis-route-terms">""", "", "</ul>")

  private def _glossary_type_issue_terms(locale: String, terms: Vector[TermEntry]): String = {
    val groups = _term_type_route_order.flatMap { termtype =>
      val rows = terms.filter(x => _normalized_term_type(x.termType) == termtype).flatMap { term =>
        val issues = _glossary_term_issues(term, locale)
        if (issues.isEmpty)
          None
        else
          Some(
            s"""<li class="list-group-item">
               |  <a href="${_html_escape(term.glossaryHref)}">${_html_escape(term.title)}</a>
               |  <span>${issues.map(_html_escape).mkString(", ")}</span>
               |</li>""".stripMargin
          )
      }
      if (rows.isEmpty)
        None
      else
        Some(
          s"""<section class="bok-term-type-issue-group bok-term-type-issue-group-${_html_escape(termtype)}">
             |  <h4>${_html_escape(_term_type_label(termtype, locale))}</h4>
             |  ${rows.take(6).mkString("""<ul class="list-group bok-alert-list">""", "", "</ul>")}
             |</section>""".stripMargin
        )
    }
    if (groups.isEmpty)
      s"""<p class="bok-card-muted">${_html_escape(_ui(locale, "term.analysis.missing.none"))}</p>"""
    else
      groups.mkString("""<div class="bok-term-type-issue-grid">""", "\n", "</div>")
  }

  private def _glossary_term_score(term: TermEntry): Int =
    term.rdfRefs.size * 3 +
      term.articleRefs.size * 3 +
      term.termRefs.size * 2 +
      term.videoRefs.size +
      term.cmlLinks.size * 2 +
      term.tags.size

  private def _glossary_term_issues(term: TermEntry, locale: String): Vector[String] = {
    val quality = Vector(
      term.quality.isolated -> _ui(locale, "term.quality.isolated"),
      term.quality.unreferenced -> _ui(locale, "term.quality.unreferenced"),
      term.quality.weaklyconnected -> _ui(locale, "term.quality.weakly.connected")
    ).collect { case (true, label) => label }
    val typeissues = _normalized_term_type(term.termType) match {
      case "unclassified" =>
        Vector(_ui(locale, "term.analysis.issue.unclassified"))
      case "event" =>
        val event = term.event
        Vector(
          event.forall(_.scenarios.isEmpty) -> _ui(locale, "term.analysis.issue.event.scenario.missing"),
          event.forall(_.actors.isEmpty) -> _ui(locale, "term.analysis.issue.event.actor.missing"),
          event.forall(_.roles.isEmpty) -> _ui(locale, "term.analysis.issue.event.role.missing"),
          !term.cmlLinks.exists(x => x.kind == "event" || x.kind == "statemachine") -> _ui(locale, "term.analysis.issue.event.cml.missing")
        ).collect { case (true, label) => label }
      case "actor" =>
        Vector(
          term.actor.forall(_.roles.isEmpty) -> _ui(locale, "term.analysis.issue.actor.role.missing"),
          (term.termRefs.isEmpty && term.articleRefs.isEmpty) -> _ui(locale, "term.analysis.issue.actor.usage.missing")
        ).collect { case (true, label) => label }
      case "role" =>
        Vector(
          term.role.forall(_.actors.isEmpty) -> _ui(locale, "term.analysis.issue.role.actor.missing"),
          term.role.forall(_.responsibilities.isEmpty) -> _ui(locale, "term.analysis.issue.role.responsibility.missing"),
          term.role.forall(_.permissions.isEmpty) -> _ui(locale, "term.analysis.issue.role.permission.missing")
        ).collect { case (true, label) => label }
      case "rule" =>
        Vector(
          !term.cmlLinks.exists(_.kind == "rule") -> _ui(locale, "term.analysis.issue.rule.cml.missing"),
          (term.articleRefs.isEmpty && term.rdfRefs.isEmpty) -> _ui(locale, "term.analysis.issue.rule.evidence.missing")
        ).collect { case (true, label) => label }
      case _ =>
        Vector(
          term.rdfRefs.isEmpty -> _ui(locale, "term.analysis.issue.concept.rdf.missing"),
          term.articleRefs.isEmpty -> _ui(locale, "term.analysis.issue.concept.article.missing"),
          term.cmlLinks.isEmpty -> _ui(locale, "term.analysis.issue.concept.cml.missing")
        ).collect { case (true, label) => label }
    }
    (typeissues ++ quality).distinct
  }

  private def _glossary_metric_cards(
    locale: String,
    categorycount: Int,
    categorieswithterms: Int,
    terms: Vector[TermEntry]
  ): String = {
    val termcount = terms.size
    val typecount = terms.map(_.termType).filter(_.nonEmpty).distinct.size
    val rdfcount = terms.map(_.rdfRefs.size).sum
    val articlecount = terms.map(_.articleRefs.size).sum
    val cmlcount = terms.map(_.cmlLinks.size).sum
    val rdfconnected = terms.count(_.rdfRefs.nonEmpty)
    val articleconnected = terms.count(_.articleRefs.nonEmpty)
    val cmlconnected = terms.count(_.cmlLinks.nonEmpty)
    s"""<div class="bok-dashboard-grid bok-glossary-summary-metrics">
       |  ${_glossary_metric_card(_ui(locale, "term.metric.terms"), termcount.toString, _ui(locale, "term.metric.terms.note"))}
       |  ${_glossary_metric_card(_ui(locale, "term.metric.categories"), categorycount.toString, _uif(locale, "term.metric.categories.note", categorieswithterms.toString))}
       |  ${_glossary_metric_card(_ui(locale, "term.metric.types"), typecount.toString, _ui(locale, "term.metric.types.note"))}
       |  ${_glossary_metric_card(_ui(locale, "term.metric.rdf.links"), rdfcount.toString, _ui(locale, "term.metric.rdf.links.note"))}
       |  ${_glossary_metric_card(_ui(locale, "term.metric.article.links"), articlecount.toString, _ui(locale, "term.metric.article.links.note"))}
       |  ${_glossary_metric_card(_ui(locale, "term.metric.cml.links"), cmlcount.toString, _ui(locale, "term.metric.cml.links.note"))}
       |</div>
       |<div class="bok-glossary-connection-rates">
       |  ${_glossary_connection_rate(_ui(locale, "term.metric.rdf.connected"), rdfconnected, termcount)}
       |  ${_glossary_connection_rate(_ui(locale, "term.metric.article.connected"), articleconnected, termcount)}
       |  ${_glossary_connection_rate(_ui(locale, "term.metric.cml.connected"), cmlconnected, termcount)}
       |</div>""".stripMargin
  }

  private def _glossary_metric_card(label: String, value: String, note: String): String =
    s"""<div class="bok-metric-card">
       |  <div class="bok-metric-label">${_html_escape(label)}</div>
       |  <div class="bok-metric-value">${_html_escape(value)}</div>
       |  <div class="bok-metric-note">${_html_escape(note)}</div>
       |</div>""".stripMargin

  private def _glossary_connection_rate(label: String, connected: Int, total: Int): String =
    s"""<span class="bok-glossary-connection-rate"><b>${_html_escape(label)}</b><span>${connected} / ${total}</span></span>"""

  private def _language_index_root_prefix(config: BuildConfig): String =
    config.localeMode match {
      case LocaleMode.SingleLocaleRoot => "../"
      case LocaleMode.MultiLocaleSubdirs => "../../"
    }

  private def _glossary_language_links(config: BuildConfig, rootprefix: String): String = {
    val langs = (config.languages ++ Vector("ja", "en")).distinct.filter(x => x == "ja" || x == "en")
    langs.map {
      case "ja" => s"""<a class="bok-special-link" href="${rootprefix}ja/glossary/index.html">日本語索引ページ</a>"""
      case "en" => s"""<a class="bok-special-link" href="${rootprefix}en/glossary/index.html">英語索引ページ</a>"""
      case other => s"""<a class="bok-special-link" href="${rootprefix}${_html_escape(other)}/glossary/index.html">${_html_escape(other)} index page</a>"""
    }.mkString("""<div class="bok-special-links">""", "\n", "</div>")
  }

  private def _glossary_recent_terms(terms: Vector[TermEntry]): String =
    if (terms.isEmpty)
      "<p>No glossary terms yet.</p>"
    else
      terms.take(10).map { term =>
        s"""<li><a href="${_html_escape(term.glossaryHref)}">${_html_escape(term.title)}</a>${_reading_label(term)}: ${_html_escape(term.categorySlug)}</li>"""
      }.mkString("<ol>\n", "\n", "\n</ol>")

  private def _glossary_index_href(href: String): String =
    href.stripPrefix("../glossary/").stripPrefix("glossary/")

  private def _terms(config: BuildConfig): Vector[TermEntry] =
    _term_index(config).map(_.terms).filter(_.nonEmpty).getOrElse(Vector.empty)

  private def _term_index(config: BuildConfig): Option[TermIndex] = {
    val paths = Vector(
      config.doxsitePath.resolve("metadata/glossary/terms.json"),
      config.sourcepath.resolve("metadata/glossary/terms.json")
    ).distinct
    val indexes = paths.flatMap(_read_term_index)
    indexes.find(_.terms.nonEmpty).orElse(indexes.headOption)
  }

  private def _read_term_index(path: Path): Option[TermIndex] =
    if (!Files.isRegularFile(path))
      None
    else
      parser.parse(Files.readString(path, StandardCharsets.UTF_8)).toOption.flatMap(_.as[TermIndex].toOption)

  private def _bibliography_index(config: BuildConfig): Option[BibliographyIndex] = {
    val path = config.doxsitePath.resolve("metadata/bibliography/bibliography.json")
    if (!Files.isRegularFile(path))
      None
    else
      parser.parse(Files.readString(path, StandardCharsets.UTF_8)).toOption.flatMap(_.as[BibliographyIndex].toOption)
  }

  private def _tag_index(config: BuildConfig, locale: String): TagIndex = {
    val usage = _usage_derived_tag_index(config, locale)
    _tag_handoff_index(config, locale).map(_merge_tag_indexes(_, usage)).getOrElse(usage)
  }

  private def _merge_tag_indexes(handoff: TagIndex, usage: TagIndex): TagIndex = {
    val handoffbykey = handoff.tags.map(x => x.key -> x).toMap
    val usagebykey = usage.tags.map(x => x.key -> x).toMap
    val entries = (handoffbykey.keySet ++ usagebykey.keySet).toVector.map { key =>
      (handoffbykey.get(key), usagebykey.get(key)) match {
        case (Some(handofftag), Some(usagetag)) =>
          handofftag.copy(
            refs = _distinct_tag_refs(handofftag.refs ++ usagetag.refs),
            children = (handofftag.children ++ usagetag.children).distinct.sorted
          )
        case (Some(handofftag), None) =>
          handofftag.copy(refs = _distinct_tag_refs(handofftag.refs))
        case (None, Some(usagetag)) =>
          usagetag.copy(refs = _distinct_tag_refs(usagetag.refs))
        case _ =>
          _tag_entry_from_usage(key, Vector.empty)
      }
    }.sortBy(x => (x.key, x.publicpath))
    TagIndex(entries)
  }

  private def _tag_handoff_index(config: BuildConfig, locale: String): Option[TagIndex] = {
    val path = config.doxsitePath.resolve("metadata/tags/tags.json")
    if (!Files.isRegularFile(path))
      None
    else
      parser.parse(Files.readString(path, StandardCharsets.UTF_8)).toOption.flatMap(_.as[TagIndex].toOption).map { index =>
        val tags = index.tags.
          filter(tag => tag.locale.forall(_ == locale)).
          map(tag => tag.copy(refs = _distinct_tag_refs(tag.refs))).
          sortBy(tag => (tag.key, tag.publicpath))
        TagIndex(tags)
      }
  }

  private def _usage_derived_tag_index(config: BuildConfig, locale: String): TagIndex = {
    val projects = _safe_resolved_project_packages(config)
    val documentrefs = _document_fragment_index(config).toVector.flatMap(_.fragments).filter(_.locale == locale).flatMap { fragment =>
      val title = fragment.effectiveHeadline.orElse(fragment.effectiveBrief).getOrElse(fragment.publicpath)
      _tag_refs(fragment.tags, TagReference(fragment.kind.getOrElse("article"), title, fragment.publicpath, fragment.category))
    }
    val termrefs = _terms(config).flatMap { term =>
      _tag_refs(term.tags, TagReference("term", term.title, term.publicpath, term.category))
    }
    val scenariorefs = _scenario_index(config).toVector.flatMap(_.scenarios).flatMap { scenario =>
      _tag_refs(scenario.tags, TagReference("scenario", scenario.title, scenario.publicpath, scenario.category))
    }
    val bibliographyrefs = _bibliography_index(config).toVector.flatMap(_.entries).flatMap { entry =>
      _tag_refs(entry.tags, TagReference("bibliography", entry.title, entry.publicpath, entry.category))
    }
    val projectrefs = projects.flatMap { project =>
      val category = Some(_project_category(config, project))
      _tag_refs(project.tags, TagReference("project", project.title, s"${project.publicationpath}/index.html", category))
    }
    val repositorycarrefs = _repository_car_index(config).entries.flatMap { entry =>
      val category = _repository_car_related_projects(entry, projects).headOption.map(_project_category(config, _))
      _tag_refs(entry.tags, TagReference("repository-car", entry.title, entry.publicPath, category))
    }
    val entries = (documentrefs ++ termrefs ++ scenariorefs ++ bibliographyrefs ++ projectrefs ++ repositorycarrefs).
      groupBy(_._1).
      toVector.
      map { case (key, refs) =>
        val distinctrefs = _distinct_tag_refs(refs.map(_._2))
        _tag_entry_from_usage(key, distinctrefs)
      }.
      sortBy(x => (x.key, x.publicpath))
    TagIndex(entries)
  }

  private def _tag_refs(tags: Vector[String], ref: TagReference): Vector[(String, TagReference)] =
    tags.map(tag => _tag_key(tag, ref.category)).filter(_.nonEmpty).distinct.map(_ -> ref)

  private def _tag_chips(config: BuildConfig, page: Path, tags: Vector[String], category: Option[String], locale: String): String = {
    val chips = tags.map(tag => _tag_key(tag, category)).filter(_.nonEmpty).distinct.map { key =>
      val entry = _tag_entry_from_usage(key, Vector.empty)
      val href = _relative_href(page, config.websitePath.resolve(entry.publicpath))
      s"""<a class="bok-tag-chip" href="${_html_escape(href)}"><span>${_html_escape(entry.effectiveTitle)}</span></a>"""
    }
    if (chips.isEmpty)
      ""
    else
      chips.mkString(s"""<div class="bok-tag-chip-list" aria-label="${_html_escape(_ui(locale, "tag.title"))}">""", "", "</div>")
  }

  private def _distinct_tag_refs(refs: Vector[TagReference]): Vector[TagReference] =
    refs.groupBy(x => (x.kind, x.href)).values.map(_.last).toVector.
      sortBy(x => (x.kind, x.category.getOrElse(""), x.title, x.href))

  private def _tag_entry_from_usage(key: String, refs: Vector[TagReference]): TagEntry = {
    val segments = key.split('.').toVector.filter(_.nonEmpty)
    val slug = segments.mkString("/")
    TagEntry(
      _tag_id(key),
      key,
      segments,
      segments.headOption,
      if (segments.size > 1) Some(s"tag:${segments.dropRight(1).mkString(".")}") else None,
      slug,
      segments.lastOption.getOrElse(key),
      Some(key),
      None,
      Vector.empty,
      None,
      None,
      s"tags/${slug}.html",
      None,
      refs,
      Vector.empty
    )
  }

  private def _tag_id(value: String): String =
    s"tag:${_tag_key(value)}"

  private def _tag_key(value: String): String =
    _tag_key(value, None)

  private def _tag_key(value: String, category: Option[String]): String = {
    val segments = value.trim.split("[./]+").toVector.map(_tag_segment).filter(_.nonEmpty)
    val normalized = segments.mkString(".")
    if (normalized.isEmpty)
      ""
    else if (segments.size > 1)
      normalized
    else
      category.map(c => Vector(_tag_segment(c), normalized).filter(_.nonEmpty).mkString(".")).filter(_.nonEmpty).getOrElse(normalized)
  }

  private def _tag_segment(value: String): String =
    value.trim.toLowerCase(Locale.ROOT).replaceAll("\\s+", "-").
      replaceAll("[\\\\/]+", "-").
      replaceAll("[^\\p{L}\\p{N}_-]+", "-").
      stripPrefix("-").
      stripSuffix("-")

  private def _tag_slug(value: String): String = {
    _tag_key(value).replace('.', '-') match {
      case "" => "tag"
      case x => x
    }
  }

  private def _tag_slug_path(value: String): String =
    value.trim.split("[./]+").toVector.map(_tag_segment).filter(_.nonEmpty).mkString("/") match {
      case "" => "tag"
      case x => x
    }

  private def _tag_dedicated_page(
    config: BuildConfig,
    categories: Vector[CategoryContent],
    locale: String,
    page: Path,
    index: TagIndex,
    focus: Option[TagEntry]
  ): String =
    focus.map(tag => _tag_detail_page(config, categories, locale, page, tag)).getOrElse(
      _tag_index_page(config, categories, locale, page, index)
    )

  private def _tag_index_page(
    config: BuildConfig,
    categories: Vector[CategoryContent],
    locale: String,
    page: Path,
    index: TagIndex
  ): String = {
    val title = _ui(locale, "tag.title")
    val description = _ui(locale, "tag.description")
    s"""<!doctype html>
       |<html lang="${_html_escape(locale)}">
       |<head>
       |  <meta charset="utf-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1">
       |  <title>${_html_escape(title)} - ${_html_escape(config.siteTitle)}</title>
       |${_site_css_links(config, page)}
       |</head>
       |<body class="article ${_html_escape(_support_dashboard_theme_class)}">
       |${_category_header(config, categories, locale)}
       |<div class="body body-dashboard bok-tag-body">
       |  <main class="article bok-tag-main">
       |    <div class="content">
       |      <article class="doc bok-tag-doc">
       |        <section class="bok-dashboard-shell bok-tag-dashboard" id="dashboard">
       |          ${_dashboard_hero(
                    title,
                    description,
                    Vector(
                      _ui(locale, "tag.metric.total") -> index.tags.size.toString,
                      _ui(locale, "tag.metric.references") -> index.tags.map(_.count).sum.toString,
                      _ui(locale, "tag.metric.types") -> index.tags.flatMap(_.refs.map(_.kind)).distinct.size.toString
                    )
                  )}
       |          ${_tag_dashboard_body(config, page, locale, index)}
       |        </section>
       |      </article>
       |    </div>
       |  </main>
       |</div>
       |</body>
       |</html>
       |""".stripMargin
  }

  private def _tag_detail_page(
    config: BuildConfig,
    categories: Vector[CategoryContent],
    locale: String,
    page: Path,
    tag: TagEntry
  ): String = {
    val title = _tag_display_title(tag)
    val description = tag.summary.getOrElse(_uif(locale, "tag.detail.description", title))
    val prefix = _site_root_prefix(config, page)
    val hierarchy = _tag_breadcrumb_items(prefix, tag, title)
    s"""<!doctype html>
       |<html lang="${_html_escape(locale)}">
       |<head>
       |  <meta charset="utf-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1">
       |  <title>${_html_escape(title)} - ${_html_escape(config.siteTitle)}</title>
       |${_site_css_links(config, page)}
       |</head>
       |<body class="article ${_html_escape(_support_dashboard_theme_class)}">
       |${_category_header(config, categories, locale)}
       |<div class="body bok-tag-detail-body">
       |  ${_special_nav_container(config, categories, prefix)}
       |  <main class="article">
       |    <div class="toolbar" role="navigation">
       |      <button class="nav-toggle"></button>
       |      <a href="${_html_escape(prefix)}index.html" class="home-link"></a>
       |      <nav class="breadcrumbs" aria-label="breadcrumbs">
       |        <ul>
       |          <li><a href="${_html_escape(prefix)}index.html">${_html_escape(config.siteTitle)}</a></li>
       |          <li><a href="${_html_escape(prefix)}tags/index.html">${_html_escape(_ui(locale, "tag.title"))}</a></li>
       |          ${hierarchy}
       |        </ul>
       |      </nav>
       |    </div>
       |    <div class="content">
       |      ${_tag_detail_toc_panel(config, prefix, locale)}
       |      <article class="doc bok-tag-detail-doc">
       |        <header class="bok-tag-detail-header">
       |          <h1 class="page">${_html_escape(title)}</h1>
       |          <p class="bok-tag-detail-lead">${_html_escape(description)}</p>
       |          ${_tag_detail_properties(tag, locale)}
       |        </header>
       |        <section class="bok-tag-detail-section bok-tag-detail-purpose" id="purpose">
       |          <h2>${_html_escape(_ui(locale, "tag.detail.purpose"))}</h2>
       |          ${_tag_purpose_body(tag, locale)}
       |        </section>
       |        <section class="bok-tag-detail-section bok-tag-detail-links" id="links">
       |          <h2>${_html_escape(_ui(locale, "tag.detail.links"))}</h2>
       |          ${_tag_rdf_link(config, page, locale, tag)}
       |          ${_tag_refs_body(config, page, locale, tag.refs)}
       |        </section>
       |      </article>
       |    </div>
       |  </main>
       |</div>
       |</body>
       |</html>
       |""".stripMargin
  }

  private def _tag_breadcrumb_items(prefix: String, tag: TagEntry, title: String): String = {
    val namespace = tag.namespace.orElse(tag.segments.headOption).getOrElse("")
    val namespaceitem =
      if (namespace.isEmpty)
        ""
      else
        s"""<li><a href="${_html_escape(prefix)}tags/${_html_escape(namespace)}/index.html">${_html_escape(namespace)}</a></li>"""
    val intermediate = tag.segments.drop(1).dropRight(1).map { segment =>
      s"""<li>${_html_escape(_tag_segment_display_label(segment))}</li>"""
    }.mkString
    s"${namespaceitem}${intermediate}<li>${_html_escape(title)}</li>"
  }

  private def _tag_detail_properties(tag: TagEntry, locale: String): String =
    s"""<table class="tableblock frame-all grid-all stretch bok-tag-detail-properties">
       |  <tbody>
       |    <tr><th>${_html_escape(_ui(locale, "tag.detail.fqn"))}</th><td><code>${_html_escape(tag.key)}</code></td></tr>
       |  </tbody>
       |</table>""".stripMargin

  private def _tag_rdf_link(config: BuildConfig, page: Path, locale: String, tag: TagEntry): String = {
    val href = s"${_relative_href(page, config.websitePath.resolve("rdf/index.html"))}?tag=${_url_query_escape(tag.key)}"
    s"""<p class="bok-tag-rdf-link"><a href="${_html_escape(href)}">${_html_escape(_ui(locale, "rdf.graph.title"))}</a></p>"""
  }

  private def _tag_display_title(tag: TagEntry): String =
    tag.label.trim match {
      case "" => tag.segments.lastOption.getOrElse(tag.key)
      case x => x
    }

  private def _tag_segment_display_label(segment: String): String =
    segment.trim

  private def _tag_detail_toc_panel(config: BuildConfig, prefix: String, locale: String): String =
    s"""<aside class="toc sidebar" data-title="Contents" data-levels="2">
       |  <div class="toc-menu">
       |    <h3>On this page</h3>
       |    <ul>
       |      <li><a href="#purpose">${_html_escape(_ui(locale, "tag.detail.purpose"))}</a></li>
       |      <li><a href="#links">${_html_escape(_ui(locale, "tag.detail.links"))}</a></li>
       |    </ul>
       |    <div class="bok-special-links">
       |      <h3>BoK Console</h3>
       |      <a class="bok-special-link" href="${_html_escape(prefix)}tags/index.html">${_html_escape(_ui(locale, "tag.title"))}</a>
       |      <a class="bok-special-link" href="${_html_escape(prefix)}glossary/index.html">${_html_escape(_ui(locale, "glossary.title"))}</a>
       |      <a class="bok-special-link" href="${_html_escape(_history_href(config, prefix))}">${_html_escape(_ui(locale, "history.title"))}</a>
       |    </div>
       |  </div>
       |</aside>""".stripMargin

  private def _tag_dashboard_body(config: BuildConfig, page: Path, locale: String, index: TagIndex): String =
    if (index.isEmpty)
      s"""<div class="bok-dashboard container-fluid bok-dashboard-command-center">
         |  <div class="row g-3">
         |    ${_dashboard_card("col-12", "bok-card-map bok-card-tag-map", _ui(locale, "tag.title"), s"""<p class="bok-card-muted">${_html_escape(_ui(locale, "tag.empty"))}</p>""", Vector("reader", "contributor", "project_manager"))}
         |  </div>
         |</div>""".stripMargin
    else {
      val tagtree = _tag_tree_body(config, page, index.tags)
      val refs = _tag_overview_body(config, page, locale, index.tags)
      s"""<div class="bok-dashboard container-fluid bok-dashboard-command-center">
         |  <div class="row g-3">
         |    ${_dashboard_card("col-12 col-xl-4", "bok-card-map bok-card-tag-summary", _ui(locale, "tag.metric.summary"), tagtree, Vector("reader", "contributor", "project_manager"))}
         |    ${_dashboard_card("col-12 col-xl-8", "bok-card-map bok-card-tag-map", _ui(locale, "tag.title"), refs, Vector("reader", "contributor", "project_manager"))}
         |  </div>
         |</div>
         |<script>
         |(() => {
         |  const category = new URLSearchParams(window.location.search).get('category');
         |  if (!category) return;
         |  document.querySelectorAll('[data-tag-categories]').forEach((item) => {
         |    item.hidden = !item.dataset.tagCategories.split(' ').includes(category);
         |  });
         |  document.querySelectorAll('[data-tag-category]').forEach((item) => {
         |    item.hidden = item.dataset.tagCategory !== category;
         |  });
         |})();
         |</script>""".stripMargin
    }

  private def _tag_purpose_body(tag: TagEntry, locale: String): String =
    tag.bodyhtml.filter(_.trim.nonEmpty).map { html =>
      s"""<section class="bok-tag-definition">${html}</section>"""
    }.getOrElse(s"""<p>${_html_escape(tag.summary.getOrElse(_uif(locale, "tag.detail.description", tag.effectiveTitle)))}</p>""")

  private def _tag_tree_body(config: BuildConfig, page: Path, tags: Vector[TagEntry]): String = {
    val bynamespace = tags.groupBy(tag => tag.segments.headOption.getOrElse(tag.namespace.getOrElse("")))
    val items = bynamespace.toVector.sortBy(_._1).map { case (namespace, xs) =>
      val namespacehref =
        if (namespace.isEmpty) "#"
        else _relative_href(page, config.websitePath.resolve(s"tags/${namespace}/index.html"))
      val namespacebody = _tag_tree_children(config, page, xs, 1)
      s"""<li><a class="bok-tag-tree-namespace" href="${_html_escape(namespacehref)}">${_html_escape(if (namespace.isEmpty) "tags" else namespace)}</a>${namespacebody}</li>"""
    }.mkString
    s"""<nav class="bok-tag-tree" aria-label="tag tree"><ul>${items}</ul></nav>"""
  }

  private def _tag_tree_children(config: BuildConfig, page: Path, tags: Vector[TagEntry], depth: Int): String = {
    val branches = tags.filter(_.segments.length > depth).groupBy(_.segments(depth)).toVector.sortBy(_._1).map {
      case (segment, xs) =>
        val exact = xs.find(_.segments.length == depth + 1)
        val children = xs.filter(_.segments.length > depth + 1)
        val label = exact.map(_tag_display_title).getOrElse(_tag_segment_display_label(segment))
        if (children.isEmpty)
          exact.map { tag =>
            val href = _relative_href(page, config.websitePath.resolve(tag.publicpath))
            s"""<li class="bok-tag-tree-leaf"><a href="${_html_escape(href)}">${_html_escape(label)}</a><span>${tag.count}</span></li>"""
          }.getOrElse("")
        else {
          val heading = exact.map { tag =>
            val href = _relative_href(page, config.websitePath.resolve(tag.publicpath))
            s"""<a class="bok-tag-tree-segment" href="${_html_escape(href)}">${_html_escape(label)}</a><span>${tag.count}</span>"""
          }.getOrElse(s"""<span class="bok-tag-tree-segment">${_html_escape(label)}</span>""")
          s"""<li class="bok-tag-tree-branch"><div class="bok-tag-tree-branch-heading">${heading}</div>${_tag_tree_children(config, page, children, depth + 1)}</li>"""
        }
    }
    branches.mkString("<ul>", "", "</ul>")
  }

  private def _tag_overview_body(config: BuildConfig, page: Path, locale: String, tags: Vector[TagEntry]): String =
    tags.take(40).map { tag =>
      val kindsummary = tag.refs.groupBy(_.kind).toVector.sortBy(_._1).map { case (kind, refs) =>
        s"${kind}: ${refs.size}"
      }.mkString(", ")
      val categories = tag.refs.flatMap(_.category).distinct.sorted.mkString(" ")
      val href = _relative_href(page, config.websitePath.resolve(tag.publicpath))
      s"""<article class="bok-tag-tile" data-tag-categories="${_html_escape(categories)}">
         |  <h3><a href="${_html_escape(href)}">${_html_escape(_tag_display_title(tag))}</a></h3>
         |  <code>${_html_escape(tag.key)}</code>
         |  <p>${_html_escape(_uif(locale, "tag.reference.count", tag.count.toString))}</p>
         |  <code>${_html_escape(kindsummary)}</code>
         |</article>""".stripMargin
    }.mkString("""<div class="bok-tag-grid">""", "", "</div>")

  private def _tag_refs_body(config: BuildConfig, page: Path, locale: String, refs: Vector[TagReference]): String =
    refs.groupBy(_.kind).toVector.sortBy(_._1).map { case (kind, xs) =>
      val items = xs.take(20).map { ref =>
        val category = ref.category.map(x => s""" <span class="badge bok-badge-info">${_html_escape(x)}</span>""").getOrElse("")
        val categorydata = ref.category.map(x => s""" data-tag-category="${_html_escape(x)}"""").getOrElse("")
        val href = _relative_href(page, config.websitePath.resolve(ref.href))
        s"""<li class="list-group-item"${categorydata}><a href="${_html_escape(href)}">${_html_escape(ref.title)}</a>${category}<span>${_html_escape(kind)}</span></li>"""
      }.mkString("\n")
      s"""<section class="bok-tag-reference-group">
         |  <h3>${_html_escape(_tag_kind_label(kind, locale))}</h3>
         |  <ul class="list-group bok-map-list">${items}</ul>
         |</section>""".stripMargin
    }.mkString("\n")

  private def _tag_kind_label(kind: String, locale: String): String =
    kind match {
      case "term" => _ui(locale, "dashboard.kpi.terms")
      case "scenario" => _ui(locale, "scenario.title")
      case "bibliography" => _ui(locale, "bibliography.title")
      case "project" => _ui(locale, "project.title")
      case "repository-car" => _repository_car_title(locale)
      case "article" | "document" => _ui(locale, "dashboard.kpi.articles")
      case other => other
    }

  private def _bibliography_dedicated_page(
    config: BuildConfig,
    categories: Vector[CategoryContent],
    locale: String,
    page: Path,
    entries: Vector[BibliographyEntry]
  ): String =
    s"""<!doctype html>
       |<html lang="${_html_escape(locale)}">
       |<head>
       |  <meta charset="utf-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1">
       |  <title>${_html_escape(_ui(locale, "bibliography.title"))} - ${_html_escape(config.siteTitle)}</title>
       |${_site_css_links(config, page)}
       |</head>
       |<body class="article ${_html_escape(_support_dashboard_theme_class)}">
       |${_category_header(config, categories, locale)}
       |<div class="body body-dashboard bok-bibliography-body">
       |  <main class="article bok-bibliography-main">
       |    <div class="content">
       |      <article class="doc bok-bibliography-doc">
       |        <section class="bok-dashboard-shell bok-bibliography-dashboard" id="dashboard">
       |          ${_dashboard_hero(
                    _ui(locale, "bibliography.title"),
                    _ui(locale, "bibliography.description"),
                    Vector(
                      _ui(locale, "bibliography.metric.total") -> entries.size.toString,
                      _ui(locale, "bibliography.metric.types") -> entries.map(_.entrytype).distinct.size.toString,
                      _ui(locale, "bibliography.metric.unresolved") -> entries.count(_.needsresolution).toString
                    )
                  )}
       |          ${_bibliography_dashboard_body(locale, entries)}
       |        </section>
       |      </article>
       |    </div>
       |  </main>
       |</div>
       |</body>
       |</html>
       |""".stripMargin

  private def _bibliography_dashboard_body(locale: String, entries: Vector[BibliographyEntry]): String =
    if (entries.isEmpty)
      s"""<div class="bok-dashboard container-fluid bok-dashboard-command-center">
         |  <div class="row g-3">
         |    ${_dashboard_card("col-12", "bok-card-map bok-card-bibliography-map", _ui(locale, "bibliography.title"), s"""<p class="bok-card-muted">${_html_escape(_ui(locale, "bibliography.empty"))}</p>""", Vector("reader", "contributor", "project_manager"))}
         |  </div>
         |</div>""".stripMargin
    else {
      val bytype = entries.groupBy(_.entrytype).toVector.sortBy(_._1)
      val metrics = bytype.map {
        case (kind, xs) =>
          s"""<div class="bok-metric-card"><div class="bok-metric-label">${_html_escape(kind)}</div><div class="bok-metric-value">${xs.size}</div><div class="bok-metric-note">${_html_escape(_ui(locale, "bibliography.metric.note"))}</div></div>"""
      }.mkString("""<div class="bok-dashboard-grid bok-bibliography-metrics">""", "", "</div>")
      val items = entries.take(40).map { entry =>
        val summary = entry.summary.map(x => s"""<p>${_html_escape(x)}</p>""").getOrElse("")
        val authors = if (entry.authors.isEmpty) "" else s"""<span>${_html_escape(entry.authors.mkString(", "))}</span>"""
        val identifiers = Vector(entry.identifiers.doi.map("DOI " + _), entry.identifiers.isbn.map("ISBN " + _), entry.identifiers.github.map("GitHub " + _)).flatten.mkString(", ")
        val ids = if (identifiers.isEmpty) "" else s"""<code>${_html_escape(identifiers)}</code>"""
        val source = entry.sourceurl.orElse(entry.identifiers.url).map(x => s"""<a href="${_html_escape(x)}">${_html_escape(_ui(locale, "bibliography.open.source"))}</a>""").getOrElse("")
        val resolution = if (entry.needsresolution) s"""<span class="badge bok-badge-warning">${_html_escape(_ui(locale, "bibliography.unresolved"))}</span>""" else ""
        s"""<article class="bok-bibliography-tile" data-bibliography-category="${_html_escape(entry.categorySlug)}">
           |  <div class="bok-bibliography-tile-head">
           |    <span>${_html_escape(entry.entrytype)}</span>
           |    <span class="badge bok-badge-info">${_html_escape(entry.sourcekind)}</span>
           |    ${resolution}
           |  </div>
           |  <h3><a href="../${_html_escape(entry.publicpath)}">${_html_escape(entry.title)}</a></h3>
           |  <div class="bok-bibliography-meta">${authors}${ids}</div>
           |  ${summary}
           |  <div class="bok-bibliography-actions">${source}</div>
           |</article>""".stripMargin
      }.mkString("""<div class="bok-bibliography-grid">""", "", "</div>")
      s"""<div class="bok-dashboard container-fluid bok-dashboard-command-center">
         |  <div class="row g-3">
         |    ${_dashboard_card("col-12 col-xl-4", "bok-card-kpi bok-card-bibliography-summary", _ui(locale, "bibliography.metric.summary"), metrics, Vector("reader", "contributor", "project_manager"))}
         |    ${_dashboard_card("col-12 col-xl-8", "bok-card-map bok-card-bibliography-map", _ui(locale, "bibliography.title"), items, Vector("reader", "contributor", "project_manager"))}
         |  </div>
         |</div>
         |<script>
         |(() => {
         |  const category = new URLSearchParams(window.location.search).get('category');
         |  if (!category) return;
         |  document.querySelectorAll('[data-bibliography-category]').forEach((item) => {
         |    item.hidden = item.dataset.bibliographyCategory !== category;
         |  });
         |})();
         |</script>""".stripMargin
    }

  private def _bibliography_entry_html_page(
    config: BuildConfig,
    categories: Vector[CategoryContent],
    locale: String,
    page: Path,
    entry: BibliographyEntry
  ): String = {
    val rootprefix = _site_root_prefix(config, page)
    val body = _bibliography_entry_body(config, page, locale, entry, rootprefix)
    s"""<!doctype html>
       |<html lang="${_html_escape(locale)}">
       |<head>
       |  <meta charset="utf-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1">
       |  <title>${_html_escape(entry.title)} - ${_html_escape(config.siteTitle)}</title>
       |${_site_css_links(config, page)}
       |</head>
       |<body class="article">
       |${_category_header(config, categories, locale, rootprefix)}
       |<div class="body">
       |  <main class="article">
       |    <div class="toolbar" role="navigation">
       |      <button class="nav-toggle"></button>
       |      <a href="${_html_escape(rootprefix)}index.html" class="home-link"></a>
       |      <nav class="breadcrumbs" aria-label="breadcrumbs">
       |        <ul>
       |          <li><a href="${_html_escape(rootprefix)}index.html">${_html_escape(config.siteTitle)}</a></li>
       |          <li><a href="${_html_escape(rootprefix)}bibliography/index.html">${_html_escape(_ui(locale, "bibliography.title"))}</a></li>
       |          <li>${_html_escape(entry.title)}</li>
       |        </ul>
       |      </nav>
       |    </div>
       |    <div class="content">
       |      <article class="doc bok-bibliography-entry">
       |        <h1 class="page">${_html_escape(entry.title)}</h1>
       |        ${body}
       |      </article>
       |    </div>
       |  </main>
       |</div>
       |</body>
       |</html>
       |""".stripMargin
  }

  private def _bibliography_entry_body(config: BuildConfig, page: Path, locale: String, entry: BibliographyEntry, rootprefix: String): String = {
    val summary = entry.summary.map(x => s"""<p>${_html_escape(x)}</p>""").getOrElse("")
    val tagchips = _tag_chips(config, page, entry.tags, entry.category, locale)
    val body = if (entry.bodyhtml.trim.isEmpty) "" else s"""<section><h2>${_html_escape(_ui(locale, "bibliography.narrative"))}</h2>${entry.bodyhtml}</section>"""
    val citedby = _bibliography_cited_by_body(locale, entry, rootprefix)
    val source = entry.sourceurl.orElse(entry.identifiers.url).map { url =>
      s"""<a href="${_html_escape(url)}">${_html_escape(_ui(locale, "bibliography.open.source"))}</a>"""
    }.getOrElse("-")
    val resolution = if (entry.needsresolution) _ui(locale, "bibliography.unresolved") else "resolved"
    val identifiers = Vector(
      entry.identifiers.doi.map("DOI " + _),
      entry.identifiers.isbn.map("ISBN " + _),
      entry.identifiers.issn.map("ISSN " + _),
      entry.identifiers.url.map("URL " + _),
      entry.identifiers.github.map("GitHub " + _),
      entry.identifiers.wikidata.map("Wikidata " + _)
    ).flatten
    val authorbody = if (entry.authors.isEmpty) "-" else entry.authors.map(_html_escape).mkString(", ")
    val termbody = if (entry.terms.isEmpty) "-" else entry.terms.map(_html_escape).mkString(", ")
    val identifierbody = if (identifiers.isEmpty) "-" else identifiers.map(x => s"<code>${_html_escape(x)}</code>").mkString(" ")
    val rows = Vector(
      _ui(locale, "bibliography.metadata.id") -> s"<code>${_html_escape(entry.id)}</code>",
      _ui(locale, "bibliography.metadata.key") -> s"<code>${_html_escape(entry.key.getOrElse("-"))}</code>",
      _ui(locale, "bibliography.metadata.type") -> _html_escape(entry.entrytype),
      _ui(locale, "bibliography.metadata.source.kind") -> _html_escape(entry.sourcekind),
      _ui(locale, "bibliography.metadata.status") -> _html_escape(resolution),
      _ui(locale, "bibliography.metadata.authors") -> authorbody,
      _ui(locale, "bibliography.metadata.published") -> _html_escape(entry.publishedat.getOrElse("-")),
      _ui(locale, "bibliography.metadata.publisher") -> _html_escape(entry.publisher.getOrElse("-")),
      _ui(locale, "bibliography.metadata.identifiers") -> identifierbody,
      _ui(locale, "bibliography.metadata.terms") -> termbody,
      _ui(locale, "bibliography.metadata.source") -> source,
      _ui(locale, "bibliography.metadata.dashboard") ->
        s"""<a href="${_html_escape(rootprefix)}bibliography/index.html">${_html_escape(_ui(locale, "bibliography.title"))}</a>"""
    ).map { case (label, value) =>
      s"""<tr><th scope="row">${_html_escape(label)}</th><td>${value}</td></tr>"""
    }.mkString("\n")
    s"""${summary}
       |${tagchips}
       |<section>
       |  <h2>${_html_escape(_ui(locale, "bibliography.metadata"))}</h2>
       |  <table class="table bok-bibliography-metadata-table">
       |    <tbody>
       |${rows}
       |    </tbody>
       |  </table>
       |</section>
       |${citedby}
       |${body}""".stripMargin
  }

  private def _bibliography_cited_by_body(locale: String, entry: BibliographyEntry, rootprefix: String): String =
    if (entry.sourcerefs.isEmpty)
      ""
    else {
      val items = entry.sourcerefs.sortBy(x => (x.sourcepath, x.ordinal, x.citationkey)).map { ref =>
        val href = rootprefix + ref.publicpath
        val category = ref.category.map(x => s""" <span class="badge bok-badge-info">${_html_escape(x)}</span>""").getOrElse("")
        s"""<li><a href="${_html_escape(href)}">${_html_escape(ref.sourcepath)}</a>${category} <code>${_html_escape(ref.citationkey)}</code></li>"""
      }.mkString("\n")
      s"""<section class="bok-bibliography-cited-by">
         |  <h2>${_html_escape(_ui(locale, "bibliography.cited.by"))}</h2>
         |  <ul>${items}</ul>
         |</section>""".stripMargin
    }

  private def _scenario_index(config: BuildConfig): Option[ScenarioIndex] = {
    val path = config.doxsitePath.resolve("metadata/scenarios/scenarios.json")
    if (!Files.isRegularFile(path))
      None
    else
      parser.parse(Files.readString(path, StandardCharsets.UTF_8)).toOption.flatMap(_.as[ScenarioIndex].toOption)
  }

  private def _scenario_dedicated_page(
    config: BuildConfig,
    categories: Vector[CategoryContent],
    locale: String,
    page: Path,
    scenarios: Vector[ScenarioEntry]
  ): String =
    s"""<!doctype html>
       |<html lang="${_html_escape(locale)}">
       |<head>
       |  <meta charset="utf-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1">
       |  <title>${_html_escape(_ui(locale, "scenario.title"))} - ${_html_escape(config.siteTitle)}</title>
       |${_site_css_links(config, page)}
       |</head>
       |<body class="article ${_html_escape(_support_dashboard_theme_class)}">
       |${_category_header(config, categories, locale)}
       |<div class="body body-dashboard bok-scenario-body">
       |  <main class="article bok-scenario-main">
       |    <div class="content">
       |      <article class="doc bok-scenario-doc">
       |        <section class="bok-dashboard-shell bok-scenario-dashboard" id="dashboard">
       |          ${_dashboard_hero(
                    _ui(locale, "scenario.title"),
                    _ui(locale, "scenario.description"),
                    Vector(
                      _ui(locale, "scenario.metric.total") -> scenarios.size.toString,
                      _ui(locale, "scenario.metric.types") -> scenarios.map(_.scenarioType).distinct.size.toString,
                      _ui(locale, "scenario.metric.categories") -> scenarios.flatMap(_.category).distinct.size.toString
                    )
                  )}
       |          ${_scenario_dashboard_body(config, page, locale, scenarios)}
       |        </section>
       |      </article>
       |    </div>
       |  </main>
       |</div>
       |</body>
       |</html>
       |""".stripMargin

  private def _scenario_dashboard_body(config: BuildConfig, page: Path, locale: String, scenarios: Vector[ScenarioEntry]): String =
    if (scenarios.isEmpty)
      s"""<div class="bok-dashboard container-fluid bok-dashboard-command-center">
         |  <div class="row g-3">
         |    ${_dashboard_card("col-12 col-xl-4", "bok-card-kpi bok-card-term-extraction-progress", _ui(locale, "term.extraction.progress.title"), _term_extraction_progress_card(locale, 0, 0), Vector("contributor", "project_manager"))}
         |    ${_dashboard_card("col-12", "bok-card-map bok-card-scenario-map", _ui(locale, "scenario.title"), s"""<p class="bok-card-muted">${_html_escape(_ui(locale, "scenario.empty"))}</p>""", Vector("reader", "contributor", "project_manager"))}
         |  </div>
         |</div>""".stripMargin
    else {
      val progress = _term_extraction_progress_card(
        locale,
        scenarios.count(x => x.termExtraction.isPlanned && _is_term_extraction_done(x.termExtraction)),
        scenarios.count(_.termExtraction.isPlanned)
      )
      val bytype = scenarios.groupBy(_.scenarioType).toVector.sortBy(_._1)
      val metrics = bytype.map {
        case (kind, xs) =>
          s"""<div class="bok-metric-card"><div class="bok-metric-label">${_html_escape(kind)}</div><div class="bok-metric-value">${xs.size}</div><div class="bok-metric-note">${_html_escape(_ui(locale, "scenario.metric.note"))}</div></div>"""
      }.mkString("""<div class="bok-dashboard-grid bok-scenario-metrics">""", "", "</div>")
      val items = scenarios.take(30).map { scenario =>
        val summary = scenario.summary.map(x => s"""<p>${_html_escape(x)}</p>""").getOrElse("")
        val terms = if (scenario.terms.isEmpty) "" else scenario.terms.take(5).map(x => s"""<span class="badge bok-badge-info">${_html_escape(x)}</span>""").mkString(" ")
        val tagchips = _tag_chips(config, page, scenario.tags, scenario.category, locale)
        val href = s"../${scenario.hrefFromHome}"
        s"""<article class="bok-scenario-tile" data-scenario-category="${_html_escape(scenario.categorySlug)}">
           |  <div class="bok-scenario-tile-head">
           |    <span>${_html_escape(scenario.scenarioType)}</span>
           |    <code>${_html_escape(scenario.id)}</code>
           |  </div>
           |  <h3><a href="${_html_escape(href)}">${_html_escape(scenario.title)}</a></h3>
           |  ${summary}
           |  <div class="bok-scenario-terms">${terms}</div>
           |  ${tagchips}
           |</article>""".stripMargin
      }.mkString("""<div class="bok-scenario-grid">""", "", "</div>")
      s"""<div class="bok-dashboard container-fluid bok-dashboard-command-center">
         |  <div class="row g-3">
         |    ${_dashboard_card("col-12 col-xl-4", "bok-card-kpi bok-card-term-extraction-progress", _ui(locale, "term.extraction.progress.title"), progress, Vector("contributor", "project_manager"))}
         |    ${_dashboard_card("col-12 col-xl-4", "bok-card-kpi bok-card-scenario-summary", _ui(locale, "scenario.metric.summary"), metrics, Vector("reader", "contributor", "project_manager"))}
         |    ${_dashboard_card("col-12 col-xl-8", "bok-card-map bok-card-scenario-map", _ui(locale, "scenario.title"), items, Vector("reader", "contributor", "project_manager"))}
         |  </div>
         |</div>
         |<script>
         |(() => {
         |  const category = new URLSearchParams(window.location.search).get('category');
         |  if (!category) return;
         |  document.querySelectorAll('[data-scenario-category]').forEach((item) => {
         |    item.hidden = item.dataset.scenarioCategory !== category;
         |  });
         |})();
         |</script>""".stripMargin
    }

  private def _term_group_cards(locale: String, terms: Vector[TermEntry], categories: Vector[CategoryContent]): String = {
    val titles = categories.map(x => x.slug -> x.title).toMap
    val cards = terms.groupBy(_.categorySlug).toVector.sortBy(_._1).map {
      case (category, xs) =>
        val title = titles.getOrElse(category, category)
        val links = xs.sortBy(_.title).take(8).map { term =>
          val typelabel = if (term.termType == "concept") "" else s""" <span class="badge bok-badge-info">${_html_escape(_term_type_label(term.termType, locale))}</span>"""
          s"""<li><a href="${_html_escape(term.glossaryHref)}">${_html_escape(term.title)}</a>${_reading_label(term)}${typelabel} <a class="bok-term-rdf-mini" href="${_html_escape(term.rdfHrefFromGlossary)}">RDF</a></li>"""
        }.mkString("<ul>", "", "</ul>")
        val more = if (xs.size > 8) s"""<div class="bok-more">${_html_escape(_uif(locale, "dashboard.more", xs.size - 8))}</div>""" else ""
        s"""<div class="bok-term-group-card"><h3><a href="../${_html_escape(category)}/index.html">${_html_escape(title)}</a></h3>${links}${more}</div>"""
    }.mkString("\n")
    s"""<div class="bok-term-group-grid">${cards}</div>"""
  }

  private def _write_term_hub_pages(
    config: BuildConfig,
    target: Path,
    locale: String,
    categories: Vector[CategoryContent],
    terms: Vector[TermEntry]
  ): Unit =
    terms.foreach { term =>
      val page = target.resolve(term.publicpath)
      if (!(term.sourcepath.startsWith("glossary/") && Files.isRegularFile(page))) {
        val scenarios = _scenario_index(config).map(_.scenarios.filter(_.isRelatedTo(term))).getOrElse(Vector.empty)
        val bibliographies = _bibliography_index(config).map(_.entries.filter(_bibliography_related_to_term(_, term))).getOrElse(Vector.empty)
        val repositorycars = _repository_car_index(config).entries.filter(_repository_car_related_to_term(_, term))
        _write_text(page, _term_hub_page(config, categories, locale, page, term, scenarios, bibliographies, repositorycars))
      }
    }

  private def _term_hub_page(
    config: BuildConfig,
    categories: Vector[CategoryContent],
    locale: String,
    page: Path,
    term: TermEntry,
    scenarios: Vector[ScenarioEntry],
    bibliographies: Vector[BibliographyEntry],
    repositorycars: Vector[RepositoryCarEntry]
  ): String =
    s"""<!doctype html>
       |<html lang="${_html_escape(locale)}">
       |<head>
       |  <meta charset="utf-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1">
       |  <title>${_html_escape(term.title)} - ${_html_escape(config.siteTitle)}</title>
       |${_site_css_links(config, page)}
       |</head>
       |<body class="article ${_html_escape(_support_dashboard_theme_class)}">
       |${_category_header(config, categories, locale, "../../")}
       |<div class="body body-dashboard bok-term-hub-body">
       |  <main class="article">
       |    <div class="content">
       |      <article class="doc">
       |        ${_term_hub(config, page, term, locale, scenarios, bibliographies, repositorycars)}
       |      </article>
       |    </div>
       |  </main>
       |</div>
       |</body>
       |</html>
       |""".stripMargin

  private def _term_hub(
    config: BuildConfig,
    page: Path,
    term: TermEntry,
    locale: String,
    scenarios: Vector[ScenarioEntry],
    bibliographies: Vector[BibliographyEntry],
    repositorycars: Vector[RepositoryCarEntry]
  ): String =
    s"""<section class="bok-dashboard-shell bok-term-hub" id="term-hub">
       |  <header class="bok-dashboard-hero">
       |    <div class="bok-dashboard-hero-copy">
       |      <p class="bok-dashboard-eyebrow">${_html_escape(_ui(locale, "term.hub.eyebrow"))}</p>
       |      <h1 class="page">${_html_escape(term.title)}</h1>
       |      <p class="bok-dashboard-lead">${_html_escape(term.summary.getOrElse(_ui(locale, "term.hub.description")))}</p>
       |      ${_tag_chips(config, page, term.tags, term.category, locale)}
       |    </div>
       |    <div class="bok-dashboard-hero-facts">
       |      <span class="bok-dashboard-hero-fact"><strong>${_html_escape(term.categorySlug)}</strong><em>${_html_escape(_ui(locale, "dashboard.matrix.category"))}</em></span>
       |      <span class="bok-dashboard-hero-fact"><strong>${_html_escape(_term_type_label(term.termType, locale))}</strong><em>${_html_escape(_ui(locale, "term.type"))}</em></span>
       |      <span class="bok-dashboard-hero-fact"><strong>${_html_escape(_mono_koto_label(term.monoKotoKind, locale))}</strong><em>${_html_escape(_ui(locale, "term.analysis.kind"))}</em></span>
       |      <span class="bok-dashboard-hero-fact"><strong>${term.rdfRefs.size}</strong><em>RDF</em></span>
       |      <span class="bok-dashboard-hero-fact"><strong>${term.termRefs.size}</strong><em>${_html_escape(_ui(locale, "term.related.terms"))}</em></span>
       |    </div>
       |  </header>
       |  <div class="bok-dashboard container-fluid bok-dashboard-command-center">
       |    <div class="row g-3">
       |      ${_dashboard_card("col-12 col-xl-7", "bok-card-purpose bok-card-term-definition", _ui(locale, "term.definition"), _term_definition_body(term))}
       |      ${_dashboard_card("col-12 col-xl-5", "bok-card-analysis bok-card-mono-koto", _ui(locale, "term.analysis"), _term_analysis_body(term, scenarios, locale))}
       |      ${_term_type_cards(term, locale)}
       |      ${_dashboard_card("col-12 col-xl-5", "bok-card-readiness", _ui(locale, "term.quality"), _term_quality_body(term, locale))}
       |      ${_dashboard_card("col-12 col-xl-6", "bok-card-related", _ui(locale, "term.rdf.resources"), _term_rdf_refs_body(term, locale))}
       |      ${_dashboard_card("col-12 col-xl-3", "bok-card-map", _ui(locale, "term.related.articles"), _term_refs_body(term.articleRefs, locale))}
       |      ${_dashboard_card("col-12 col-xl-3", "bok-card-map", _ui(locale, "term.related.terms"), _term_refs_body(term.termRefs, locale))}
       |      ${_dashboard_card("col-12 col-xl-3", "bok-card-map", _ui(locale, "term.related.videos"), _term_refs_body(term.videoRefs, locale))}
       |      ${_dashboard_card("col-12 col-xl-3", "bok-card-map", _ui(locale, "term.related.scenarios"), _term_scenarios_body(scenarios, locale))}
       |      ${_dashboard_card("col-12 col-xl-3", "bok-card-map", _ui(locale, "term.related.bibliography"), _term_bibliography_body(bibliographies, locale))}
       |      ${_dashboard_card("col-12 col-xl-3", "bok-card-map bok-card-repository-car", _repository_car_title(locale), _term_repository_cars_body(config, page, repositorycars, locale))}
       |      ${_dashboard_card("col-12 col-xl-3", "bok-card-actions", _ui(locale, "dashboard.card.next.actions"), _term_actions_body(term, locale))}
       |    </div>
       |  </div>
       |</section>""".stripMargin


  private def _term_type_cards(term: TermEntry, locale: String): String = term.termType match {
    case "event" =>
      term.event.map(x => _dashboard_card("col-12 col-xl-5", "bok-card-related bok-card-term-type", _ui(locale, "term.type.event"), _term_event_body(x, locale))).getOrElse("")
    case "actor" =>
      term.actor.map(x => _dashboard_card("col-12 col-xl-5", "bok-card-related bok-card-term-type", _ui(locale, "term.type.actor"), _term_actor_body(x, locale))).getOrElse("")
    case "role" =>
      term.role.map(x => _dashboard_card("col-12 col-xl-5", "bok-card-related bok-card-term-type", _ui(locale, "term.type.role"), _term_role_body(x, locale))).getOrElse("")
    case _ => ""
  }

  private def _term_type_label(value: String, locale: String): String = value match {
    case "unclassified" => _ui(locale, "term.type.unclassified")
    case "event" => _ui(locale, "term.type.event")
    case "actor" => _ui(locale, "term.type.actor")
    case "role" => _ui(locale, "term.type.role")
    case "entity" => _ui(locale, "term.type.entity")
    case "resource" => _ui(locale, "term.type.resource")
    case "artifact" => _ui(locale, "term.type.artifact")
    case "action" => _ui(locale, "term.type.action")
    case "process" => _ui(locale, "term.type.process")
    case "task" => _ui(locale, "term.type.task")
    case "scenario" => _ui(locale, "term.type.scenario")
    case "state" => _ui(locale, "term.type.state")
    case "rule" => _ui(locale, "term.type.rule")
    case _ => _ui(locale, "term.type.concept")
  }

  private def _mono_koto_label(value: String, locale: String): String = value match {
    case "koto" => _ui(locale, "term.analysis.koto")
    case "rule" => _ui(locale, "term.analysis.rule")
    case _ => _ui(locale, "term.analysis.mono")
  }

  private def _term_analysis_body(term: TermEntry, scenarios: Vector[ScenarioEntry], locale: String): String = {
    val cmlvalues = term.cmlLinks.map(x => s"${x.kind}: ${x.value}")
    val diagnostics = _term_analysis_diagnostics(term, scenarios, locale)
    val rows = Vector(
      _ui(locale, "term.analysis.kind") -> Vector(_mono_koto_label(term.monoKotoKind, locale)),
      _ui(locale, "term.type") -> Vector(_term_type_label(term.termType, locale)),
      _ui(locale, "term.analysis.cml.linkage") -> cmlvalues,
      _ui(locale, "term.related.scenarios") -> scenarios.map(_.title),
      _ui(locale, "term.analysis.diagnostics") -> diagnostics
    )
    _term_metadata_table(rows, locale)
  }

  private def _term_analysis_diagnostics(term: TermEntry, scenarios: Vector[ScenarioEntry], locale: String): Vector[String] = {
    val cml = if (term.cmlLinks.isEmpty) Vector(_ui(locale, "term.analysis.diagnostic.cml.missing")) else Vector.empty
    val scenario =
      if (term.termType == "event" && scenarios.isEmpty && term.event.forall(_.scenarios.isEmpty))
        Vector(_ui(locale, "term.analysis.diagnostic.scenario.missing"))
      else
        Vector.empty
    val mismatch =
      if (term.cmlLinks.nonEmpty && term.cmlLinks.forall(x => _cml_analysis_kind(x.kind) != term.monoKotoKind))
        Vector(_ui(locale, "term.analysis.diagnostic.classification.mismatch"))
      else
        Vector.empty
    val result = cml ++ scenario ++ mismatch
    if (result.isEmpty) Vector(_ui(locale, "term.analysis.diagnostic.ok")) else result
  }

  private def _cml_analysis_kind(kind: String): String = kind match {
    case "event" | "operation" | "statemachine" => "koto"
    case "rule" => "rule"
    case _ => "mono"
  }

  private def _term_event_body(event: TermEvent, locale: String): String = {
    val rows = Vector(
      _ui(locale, "term.event.occurred.at") -> event.occurredAt.toVector,
      _ui(locale, "term.event.period") -> Vector(event.startAt.toVector.mkString, event.endAt.toVector.mkString).filter(_.nonEmpty),
      _ui(locale, "term.event.location") -> event.location.toVector,
      _ui(locale, "term.event.actors") -> event.actors,
      _ui(locale, "term.event.roles") -> event.roles,
      _ui(locale, "term.event.participants") -> event.participants,
      _ui(locale, "term.event.scenarios") -> event.scenarios,
      _ui(locale, "term.event.evidence") -> event.evidence,
      _ui(locale, "term.event.cml") -> Vector(event.cmlComponent, event.cmlEvent, event.cmlStatemachine).flatten
    )
    _term_metadata_table(rows, locale)
  }

  private def _term_actor_body(actor: TermActor, locale: String): String =
    _term_metadata_table(Vector(
      _ui(locale, "term.actor.organization") -> actor.organization.toVector,
      _ui(locale, "term.actor.roles") -> actor.roles,
      _ui(locale, "term.actor.description") -> actor.description.toVector
    ), locale)

  private def _term_role_body(role: TermRole, locale: String): String =
    _term_metadata_table(Vector(
      _ui(locale, "term.role.actors") -> role.actors,
      _ui(locale, "term.role.responsibilities") -> role.responsibilities,
      _ui(locale, "term.role.permissions") -> role.permissions
    ), locale)

  private def _term_metadata_table(rows: Vector[(String, Vector[String])], locale: String): String = {
    val body = rows.collect { case (label, values) if values.nonEmpty =>
      s"""<tr><th>${_html_escape(label)}</th><td>${values.map(_html_escape).mkString("<br>")}</td></tr>"""
    }.mkString("\n")
    if (body.isEmpty)
      s"""<p class="bok-card-muted">${_html_escape(_ui(locale, "term.type.metadata.empty"))}</p>"""
    else
      s"""<table class="table table-sm bok-metadata-table"><tbody>${body}</tbody></table>"""
  }

  private def _term_definition_body(term: TermEntry): String = {
    val reading = term.reading.filterNot(_ == term.title).map(x => s"""<p class="bok-term-reading-large">${_html_escape(x)}</p>""").getOrElse("")
    s"""${reading}<div class="bok-term-definition-html">${term.definitionHtml}</div>"""
  }

  private def _term_quality_body(term: TermEntry, locale: String): String = {
    val flags = Vector(
      term.quality.isolated -> _ui(locale, "term.quality.isolated"),
      term.quality.unreferenced -> _ui(locale, "term.quality.unreferenced"),
      term.quality.weaklyconnected -> _ui(locale, "term.quality.weakly.connected")
    ).collect { case (true, label) => label }
    if (flags.isEmpty)
      s"""<p class="bok-card-muted">${_html_escape(_ui(locale, "term.quality.ok"))}</p>"""
    else
      flags.map(x => s"""<li class="list-group-item"><span class="badge bok-badge-info">info</span>${_html_escape(x)}</li>""").mkString("""<ul class="list-group bok-alert-list">""", "", "</ul>")
  }

  private def _term_rdf_refs_body(term: TermEntry, locale: String): String =
    if (term.rdfRefs.isEmpty)
      s"""<p class="bok-card-muted">${_html_escape(_ui(locale, "term.rdf.empty"))}</p>"""
    else
      term.rdfRefs.take(8).map { ref =>
        val predicate = ref.predicate.map(x => s" <small>${_html_escape(_short_uri_label(x))}</small>").getOrElse("")
        s"""<li class="list-group-item"><span>${_html_escape(ref.label)}</span>${predicate}<em>${_html_escape(ref.direction)}</em></li>"""
      }.mkString("""<ul class="list-group bok-map-list">""", "", "</ul>")

  private def _term_refs_body(refs: Vector[TermReference], locale: String): String =
    if (refs.isEmpty)
      s"""<p class="bok-card-muted">${_html_escape(_ui(locale, "term.refs.empty"))}</p>"""
    else
      refs.take(6).map(x => s"""<li class="list-group-item"><a href="${_html_escape(x.path)}">${_html_escape(x.title)}</a><span>${_html_escape(x.relation)}</span></li>""").mkString("""<ul class="list-group bok-map-list">""", "", "</ul>")

  private def _term_scenarios_body(scenarios: Vector[ScenarioEntry], locale: String): String =
    if (scenarios.isEmpty)
      s"""<p class="bok-card-muted">${_html_escape(_ui(locale, "scenario.empty"))}</p>"""
    else
      scenarios.take(6).map { scenario =>
        s"""<li class="list-group-item"><a href="../../${_html_escape(scenario.publicpath)}">${_html_escape(scenario.title)}</a><span>${_html_escape(scenario.scenarioType)}</span></li>"""
      }.mkString("""<ul class="list-group bok-map-list">""", "", "</ul>")

  private def _term_bibliography_body(entries: Vector[BibliographyEntry], locale: String): String =
    if (entries.isEmpty)
      s"""<p class="bok-card-muted">${_html_escape(_ui(locale, "bibliography.empty"))}</p>"""
    else
      entries.take(6).map { entry =>
        val resolution = if (entry.needsresolution) s" / ${_ui(locale, "bibliography.unresolved")}" else ""
        s"""<li class="list-group-item"><a href="../../${_html_escape(entry.publicpath)}">${_html_escape(entry.title)}</a><span>${_html_escape(entry.entrytype + " / " + entry.sourcekind + resolution)}</span></li>"""
      }.mkString("""<ul class="list-group bok-map-list">""", "", "</ul>")

  private def _term_repository_cars_body(
    config: BuildConfig,
    page: Path,
    entries: Vector[RepositoryCarEntry],
    locale: String
  ): String =
    if (entries.isEmpty)
      s"""<p class="bok-card-muted">${_html_escape(_repository_car_empty(locale))}</p>"""
    else
      entries.take(6).map { entry =>
        val href = _relative_href(page, config.websitePath.resolve(entry.publicPath))
        s"""<li class="list-group-item"><a href="${_html_escape(href)}">${_html_escape(entry.title)}</a><span>${_html_escape(entry.effectiveVersion.getOrElse("-"))}</span></li>"""
      }.mkString("""<ul class="list-group bok-map-list">""", "", "</ul>")

  private def _repository_car_related_to_term(entry: RepositoryCarEntry, term: TermEntry): Boolean =
    entry.terms.exists(_term_reference_matches(_, term))

  private def _term_reference_matches(value: String, term: TermEntry): Boolean =
    value == term.id || value == term.title || value == term.slug || term.aliases.contains(value)

  private def _bibliography_related_to_term(entry: BibliographyEntry, term: TermEntry): Boolean =
    entry.terms.exists(_term_reference_matches(_, term))

  private def _term_actions_body(term: TermEntry, locale: String): String =
    Vector(
      _ui(locale, "term.action.open.rdf") -> term.rdfHrefFromTerm,
      _ui(locale, "glossary.title") -> "../../glossary/index.html"
    ).map { case (label, href) => s"""<li><a href="${_html_escape(href)}">${_html_escape(label)}</a></li>""" }.mkString("""<ol class="bok-action-list">""", "", "</ol>")

  private def _bibliography_search_json(results: Vector[BibliographySearchResult]): String =
    results.map { result =>
      val authors = result.authors.map(_json_string).mkString("[", ", ", "]")
      val fields = Vector(
        "provider" -> Some(result.provider),
        "bib_id" -> Some(result.bibid),
        "citation_key" -> Some(result.citationkey),
        "title" -> Some(result.title),
        "year" -> result.year,
        "doi" -> result.doi,
        "isbn" -> result.isbn,
        "source_url" -> result.sourceurl,
        "entry_type" -> Some(result.entrytype)
      ).collect { case (key, Some(value)) => s"${_json_string(key)}: ${_json_string(value)}" }
      (fields :+ s"${_json_string("authors")}: ${authors}").mkString("{", ", ", "}")
    }.mkString("{\n  \"candidates\": [\n    ", ",\n    ", "\n  ]\n}")

  private def _json_string(value: String): String =
    "\"" + value.flatMap {
      case '\\' => "\\\\"
      case '"' => "\\\""
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case c if c < ' ' => ""
      case c => c.toString
    } + "\""

  private def _safe_file_name(value: String): String =
    value.replaceAll("[^A-Za-z0-9_-]+", "-").stripPrefix("-").stripSuffix("-") match {
      case "" => "reference"
      case x => x
    }

  private def _short_uri_label(value: String): String = {
    val a = value.split('#').lastOption.getOrElse(value)
    a.split('/').filter(_.nonEmpty).lastOption.getOrElse(a)
  }

  private def _localized_glossary_index_page(
    config: BuildConfig,
    categories: Vector[CategoryContent],
    lang: String
  ): String = {
    val categorytitles = categories.map(x => x.slug -> x.title).toMap
    val terms = _localized_glossary_terms(lang, _terms(config).map { term =>
      val categoryslug = term.categorySlug
      LocalizedGlossaryItem(
        s"../../${term.publicpath}",
        term.title,
        term.reading,
        categoryslug,
        categorytitles.getOrElse(categoryslug, categoryslug)
      )
    })
    val uilang = config.defaultLocale
    val title = _localized_glossary_index_title(uilang, lang)
    val description = _localized_glossary_index_description(uilang, lang)
    val indexheading = _localized_glossary_index_heading(uilang)
    val termsheading = _localized_glossary_terms_heading(uilang)
    val indexnav = _localized_glossary_index_nav(lang, terms)
    val sections = _localized_glossary_index_sections(lang, terms)
    s"""<!doctype html>
       |<html lang="${_html_escape(lang)}">
       |<head>
       |  <meta charset="utf-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1">
       |  <title>${_html_escape(title)} - ${_html_escape(config.siteTitle)}</title>
       |  <link rel="stylesheet" href="../../_/css/bootstrap-grid.min.css">
       |  <link rel="stylesheet" href="../../_/css/site.css">
       |  <link rel="stylesheet" href="../../_/css/cozy-bok-dashboard.css">
       |</head>
       |<body class="article ${_html_escape(_support_dashboard_theme_class)}">
       |${_category_header(config, categories, uilang, "../../")}
       |<div class="body">
       |  <main class="article">
       |    <div class="toolbar" role="navigation">
       |      <a href="../../index.html" class="home-link"></a>
       |      <nav class="breadcrumbs" aria-label="breadcrumbs">
       |        <ul>
       |          <li><a href="../../index.html">${_html_escape(config.siteTitle)}</a></li>
       |          <li><a href="../../glossary/index.html">Glossary</a></li>
       |          <li>${_html_escape(title)}</li>
       |        </ul>
       |      </nav>
       |    </div>
       |    <div class="content">
       |      ${_localized_glossary_toc_panel(config)}
       |      <article class="doc">
       |        <h1 class="page">${_html_escape(title)}</h1>
       |        <p>${_html_escape(description)}</p>
       |        <div class="sect1" id="index">
       |          <h2>${_html_escape(indexheading)}</h2>
       |          <div class="sectionbody">
       |            ${indexnav}
       |          </div>
       |        </div>
       |        <div class="sect1" id="terms">
       |          <h2>${_html_escape(termsheading)}</h2>
       |          <div class="sectionbody">
       |            ${sections}
       |          </div>
       |        </div>
       |      </article>
       |    </div>
       |  </main>
       |</div>
       |</body>
       |</html>
       |""".stripMargin
  }

  private def _localized_glossary_index_title(
    uilang: String,
    indexlang: String
  ): String =
    uilang match {
      case "ja" =>
        indexlang match {
          case "ja" => "日本語用語索引"
          case "en" => "英語用語索引"
          case other => s"${other} 用語索引"
        }
      case _ =>
        indexlang match {
          case "ja" => "Japanese Glossary Index"
          case "en" => "English Glossary Index"
          case other => s"${other} Glossary Index"
        }
    }

  private def _localized_glossary_index_description(
    uilang: String,
    indexlang: String
  ): String =
    uilang match {
      case "ja" =>
        indexlang match {
          case "ja" => "日本語で用語を探すための索引ページです。"
          case "en" => "英語表記の用語を探すための索引ページです。"
          case other => s"${other} 表記の用語を探すための索引ページです。"
        }
      case _ => "This page is a language-specific entry point for glossary browsing."
    }

  private def _localized_glossary_index_heading(uilang: String): String =
    uilang match {
      case "ja" => "索引"
      case _ => "Index"
    }

  private def _localized_glossary_terms_heading(uilang: String): String =
    uilang match {
      case "ja" => "用語"
      case _ => "Terms"
    }

  private def _localized_glossary_terms(
    lang: String,
    terms: Vector[LocalizedGlossaryItem]
  ): Vector[LocalizedGlossaryItem] =
    lang match {
      case "ja" => terms.filter(term => _has_japanese_character(_ja_index_text(term)))
      case _ => terms
    }

  private def _localized_glossary_index_nav(
    lang: String,
    terms: Vector[LocalizedGlossaryItem]
  ): String = {
    val grouped = terms.groupBy(term => _localized_index_key(lang, term))
    _localized_index_keys(lang).map { key =>
      if (grouped.get(key).exists(_.nonEmpty))
        s"""<a class="bok-index-link" href="#${_html_escape(_localized_index_anchor(key))}">${_html_escape(key)}</a>"""
      else
        s"""<span class="bok-index-link is-disabled">${_html_escape(key)}</span>"""
    }.mkString("""<div class="bok-index-nav">""", "\n", "</div>")
  }

  private def _localized_glossary_index_sections(
    lang: String,
    terms: Vector[LocalizedGlossaryItem]
  ): String =
    if (terms.isEmpty)
      "<p>No glossary terms yet.</p>"
    else {
      val keys = _localized_index_keys(lang)
      val grouped = terms.groupBy(term => _localized_index_key(lang, term))
      val activekeys = keys.filter(key => grouped.get(key).exists(_.nonEmpty))
      activekeys.map { key =>
        val items = grouped.getOrElse(key, Vector.empty).sortBy { term =>
          (_localized_index_sort_key(lang, term), term.categorySlug)
        }.map { term =>
          val category =
            s""" <span class="bok-index-term-category">[${_html_escape(term.categoryTitle)}]</span>"""
          s"""<li><a href="${_html_escape(term.href)}">${_html_escape(_localized_term_title(lang, term))}</a>${_localized_reading_label(lang, term)}${category}</li>"""
        }.mkString("<ul>\n", "\n", "\n</ul>")
        s"""<section class="bok-index-section" id="${_html_escape(_localized_index_anchor(key))}">
           |  <h3>${_html_escape(key)}</h3>
           |  ${items}
           |</section>""".stripMargin
      }.mkString("\n")
    }

  private def _localized_index_keys(lang: String): Vector[String] =
    lang match {
      case "ja" =>
        "あいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやゆよらりるれろわをん".map(_.toString).toVector :+ "その他"
      case "en" =>
        ('A' to 'Z').map(_.toString).toVector :+ "Other"
      case _ =>
        ('A' to 'Z').map(_.toString).toVector :+ "Other"
    }

  private def _localized_index_key(lang: String, term: LocalizedGlossaryItem): String =
    lang match {
      case "ja" => _ja_index_key(_ja_index_text(term))
      case "en" => _en_index_key(term.title)
      case _ => _en_index_key(term.title)
    }

  private def _localized_index_sort_key(lang: String, term: LocalizedGlossaryItem): String =
    lang match {
      case "ja" => _ja_index_text(term)
      case _ => term.title.toLowerCase(java.util.Locale.ROOT)
    }

  private def _ja_index_text(term: LocalizedGlossaryItem): String =
    term.reading.getOrElse(term.title)

  private def _localized_term_title(lang: String, term: LocalizedGlossaryItem): String =
    lang match {
      case "ja" => term.reading.getOrElse(term.title)
      case _ => term.title
    }

  private def _localized_reading_label(lang: String, term: LocalizedGlossaryItem): String =
    if (_localized_term_title(lang, term) == term.reading.getOrElse(""))
      ""
    else
      term.reading.filterNot(_ == term.title).map { reading =>
        s""" <span class="bok-term-reading">(${_html_escape(reading)})</span>"""
      }.getOrElse("")

  private def _reading_label(term: CategoryPageItem): String =
    term.reading.filterNot(_ == term.title).map { reading =>
      s""" <span class="bok-term-reading">(${_html_escape(reading)})</span>"""
    }.getOrElse("")

  private def _reading_label(term: TermEntry): String =
    term.reading.filterNot(_ == term.title).map { reading =>
      s""" <span class="bok-term-reading">(${_html_escape(reading)})</span>"""
    }.getOrElse("")

  private def _localized_index_anchor(key: String): String =
    key match {
      case "その他" => "index-other"
      case "Other" => "index-other"
      case other => s"index-${other.toLowerCase(java.util.Locale.ROOT)}"
    }

  private def _ja_index_key(title: String): String = {
    val keys = _localized_index_keys("ja").toSet
    title.trim.headOption.map(_normalize_kana).flatMap { ch =>
      val value = ch.toString
      if (keys.contains(value))
        Some(value)
      else
        _voiced_kana_base.get(ch).map(_.toString).filter(keys.contains)
    }.getOrElse("その他")
  }

  private def _en_index_key(title: String): String =
    title.trim.find(_.isLetter).map(_.toUpper).filter(ch => ch >= 'A' && ch <= 'Z').
      map(_.toString).getOrElse("Other")

  private def _has_japanese_character(value: String): Boolean =
    value.exists(ch =>
      (ch >= '\u3040' && ch <= '\u309f') ||
      (ch >= '\u30a0' && ch <= '\u30ff') ||
      (ch >= '\u4e00' && ch <= '\u9fff')
    )

  private def _normalize_kana(ch: Char): Char =
    if (ch >= '\u30a1' && ch <= '\u30f6')
      (ch - 0x60).toChar
    else
      ch match {
        case 'ぁ' => 'あ'
        case 'ぃ' => 'い'
        case 'ぅ' => 'う'
        case 'ぇ' => 'え'
        case 'ぉ' => 'お'
        case 'ゃ' => 'や'
        case 'ゅ' => 'ゆ'
        case 'ょ' => 'よ'
        case 'ゎ' => 'わ'
        case other => other
      }

  private val _voiced_kana_base: Map[Char, Char] =
    Map(
      'が' -> 'か', 'ぎ' -> 'き', 'ぐ' -> 'く', 'げ' -> 'け', 'ご' -> 'こ',
      'ざ' -> 'さ', 'じ' -> 'し', 'ず' -> 'す', 'ぜ' -> 'せ', 'ぞ' -> 'そ',
      'だ' -> 'た', 'ぢ' -> 'ち', 'づ' -> 'つ', 'で' -> 'て', 'ど' -> 'と',
      'ば' -> 'は', 'び' -> 'ひ', 'ぶ' -> 'ふ', 'べ' -> 'へ', 'ぼ' -> 'ほ',
      'ぱ' -> 'は', 'ぴ' -> 'ひ', 'ぷ' -> 'ふ', 'ぺ' -> 'へ', 'ぽ' -> 'ほ'
    )

  private def _special_html_page(
    config: BuildConfig,
    categories: Vector[CategoryContent],
    locale: String,
    page: Path,
    title: String,
    description: String,
    body: String
  ): String =
    _special_html_page_with_toc(
      config,
      categories,
      locale,
      page,
      title,
      description,
      body,
      Vector("dashboard" -> "Dashboard", "term-groups" -> "Term Groups", "language-index" -> "Language Index", "recent-terms" -> "Recent Terms")
    )

  private def _special_html_page_with_toc(
    config: BuildConfig,
    categories: Vector[CategoryContent],
    locale: String,
    page: Path,
    title: String,
    description: String,
    body: String,
    tocitems: Vector[(String, String)]
  ): String =
    s"""<!doctype html>
       |<html lang="${_html_escape(locale)}">
       |<head>
       |  <meta charset="utf-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1">
       |  <title>${_html_escape(title)} - ${_html_escape(config.siteTitle)}</title>
       |${_site_css_links(config, page)}
       |</head>
       |<body class="article ${_html_escape(_support_dashboard_theme_class)}">
       |${_category_header(config, categories, locale)}
       |<div class="body">
       |  ${_special_nav_container(config, categories)}
       |  <main class="article">
       |    <div class="toolbar" role="navigation">
       |      <button class="nav-toggle"></button>
       |      <a href="../index.html" class="home-link"></a>
       |      <nav class="breadcrumbs" aria-label="breadcrumbs">
       |        <ul>
       |          <li><a href="../index.html">${_html_escape(config.siteTitle)}</a></li>
       |          <li>${_html_escape(title)}</li>
       |        </ul>
       |      </nav>
       |    </div>
       |    <div class="content">
       |      ${_special_toc_panel(config, tocitems)}
       |      <article class="doc">
       |        <h1 class="page">${_html_escape(title)}</h1>
       |        <p>${_html_escape(description)}</p>
       |        <div class="sect1" id="dashboard">
       |          <h2>Dashboard</h2>
       |          <div class="sectionbody">
       |            <p>${_html_escape(_ui(locale, "special.console.description"))}</p>
       |          </div>
       |        </div>
       |        ${body}
       |      </article>
       |    </div>
       |  </main>
       |</div>
       |</body>
       |</html>
       |""".stripMargin

  private def _project_html_page(
    config: BuildConfig,
    categories: Vector[CategoryContent],
    locale: String,
    page: Path,
    target: Path,
    title: String,
    description: String,
    tags: Vector[String],
    category: Option[String],
    body: String
  ): String = {
    val homehref = _relative_href(page, target.resolve("index.html"))
    val projectshref = _relative_href(page, target.resolve("projects").resolve("index.html"))
    val rootprefix = homehref.stripSuffix("index.html")
    val tagchips = _tag_chips(config, page, tags, category, locale)
    s"""<!doctype html>
       |<html lang="${_html_escape(locale)}">
       |<head>
       |  <meta charset="utf-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1">
       |  <title>${_html_escape(title)} - ${_html_escape(config.siteTitle)}</title>
       |${_site_css_links(config, page)}
       |</head>
       |<body class="bok-project-page ${_html_escape(_support_dashboard_theme_class)}">
       |${_category_header(config, categories, locale, rootprefix)}
       |<main class="bok-project-page-main">
       |  <nav class="bok-project-breadcrumbs" aria-label="breadcrumbs">
       |    <a href="${_html_escape(homehref)}">${_html_escape(config.siteTitle)}</a>
       |    <span>/</span>
       |    <a href="${_html_escape(projectshref)}">${_html_escape(_ui(locale, "project.title"))}</a>
       |    <span>/</span>
       |    <span>${_html_escape(title)}</span>
       |  </nav>
       |  <header class="bok-project-page-title">
       |    <p class="bok-dashboard-eyebrow">${_html_escape(_ui(locale, "project.page.eyebrow"))}</p>
       |    <h1>${_html_escape(title)}</h1>
       |    <p>${_html_escape(description)}</p>
       |    ${tagchips}
       |  </header>
       |  ${body}
       |</main>
       |</body>
       |</html>
       |""".stripMargin
  }

  private def _manual_html_page(
    config: BuildConfig,
    categories: Vector[CategoryContent],
    locale: String,
    page: Path,
    title: String,
    description: String,
    body: String
  ): String =
    s"""<!doctype html>
       |<html lang="${_html_escape(locale)}">
       |<head>
       |  <meta charset="utf-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1">
       |  <title>${_html_escape(title)} - ${_html_escape(config.siteTitle)}</title>
       |${_site_css_links(config, page)}
       |</head>
       |<body class="article ${_html_escape(_support_dashboard_theme_class)}">
       |${_category_header(config, categories, locale)}
       |<div class="body">
       |  ${_special_nav_container(config, categories)}
       |  <main class="article">
       |    <div class="toolbar" role="navigation">
       |      <button class="nav-toggle"></button>
       |      <a href="../index.html" class="home-link"></a>
       |      <nav class="breadcrumbs" aria-label="breadcrumbs">
       |        <ul>
       |          <li><a href="../index.html">${_html_escape(config.siteTitle)}</a></li>
       |          <li>${_html_escape(title)}</li>
       |        </ul>
       |      </nav>
       |    </div>
       |    <div class="content">
       |      <article class="doc">
       |        <h1 class="page">${_html_escape(title)}</h1>
       |        <p>${_html_escape(description)}</p>
       |        ${body}
       |      </article>
       |    </div>
       |  </main>
       |</div>
       |</body>
       |</html>
       |""".stripMargin

  private def _special_toc_panel(config: BuildConfig, tocitems: Vector[(String, String)]): String = {
    val items = tocitems.map {
      case (id, label) => s"""        <li><a href="#${_html_escape(id)}">${_html_escape(label)}</a></li>"""
    }.mkString("\n")
    s"""<aside class="toc sidebar" data-title="Contents" data-levels="2">
       |    <div class="toc-menu">
       |      <h3>On this page</h3>
       |      <ul>
       |${items}
       |      </ul>
       |      <div class="bok-special-links">
       |        <h3>BoK Console</h3>
       |        <a class="bok-special-link" href="../glossary/index.html">Glossary</a>
       |        <a class="bok-special-link" href="${_html_escape(_history_href(config, "../"))}">History</a>
       |        <a class="bok-special-link" href="../manual/index.html">Manual</a>
       |      </div>
       |    </div>
       |  </aside>""".stripMargin
  }

  private def _special_nav_container(config: BuildConfig, categories: Vector[CategoryContent], prefix: String = "../"): String = {
    val items = categories.map { category =>
      s"""<li class="nav-item" data-depth="1"><a class="nav-link" href="${_html_escape(prefix)}${_html_escape(category.slug)}/index.html">${_html_escape(category.title)}</a></li>"""
    }.mkString("\n                  ")
    s"""<div class="nav-container" data-component="home" data-version="">
       |    <aside class="nav">
       |      <div class="panels">
       |        <div class="nav-panel-menu is-active" data-panel="menu">
       |          <nav class="nav-menu">
       |            <button class="nav-menu-toggle" aria-label="Toggle expand/collapse all" style="display: none"></button>
       |            <h3 class="title"><a href="${_html_escape(prefix)}index.html">${_html_escape(config.siteTitle)}</a></h3>
       |            <ul class="nav-list">
       |              <li class="nav-item" data-depth="0">
       |                <ul class="nav-list">
       |                  ${items}
       |                </ul>
       |              </li>
       |            </ul>
       |          </nav>
       |        </div>
       |      </div>
       |    </aside>
       |  </div>""".stripMargin
  }

  private def _glossary_toc_panel(config: BuildConfig): String =
    s"""<aside class="toc sidebar" data-title="Contents" data-levels="2">
      |    <div class="toc-menu">
      |      <h3>On this page</h3>
      |      <ul>
      |        <li><a href="#dashboard">Dashboard</a></li>
      |        <li><a href="#term-groups">Term Groups</a></li>
      |        <li><a href="#language-index">Language Index</a></li>
      |        <li><a href="#recent-terms">Recent Terms</a></li>
      |      </ul>
      |      <div class="bok-special-links">
      |        <h3>BoK Console</h3>
      |        <a class="bok-special-link" href="../glossary/index.html">Glossary</a>
      |        <a class="bok-special-link" href="${_html_escape(_history_href(config, "../"))}">History</a>
      |        <a class="bok-special-link" href="../manual/index.html">Manual</a>
      |      </div>
      |    </div>
      |  </aside>""".stripMargin

  private def _localized_glossary_toc_panel(config: BuildConfig): String =
    s"""<aside class="toc sidebar" data-title="Contents" data-levels="2">
       |    <div class="toc-menu">
       |      <h3>On this page</h3>
       |      <ul>
       |        <li><a href="#terms">Terms</a></li>
       |      </ul>
       |      <div class="bok-special-links">
       |        <h3>BoK Console</h3>
       |        <a class="bok-special-link" href="../../glossary/index.html">Glossary</a>
       |        <a class="bok-special-link" href="${_html_escape(_history_href(config, "../../"))}">History</a>
       |        <a class="bok-special-link" href="../../manual/index.html">Manual</a>
       |      </div>
       |    </div>
       |  </aside>""".stripMargin

  private def _category_html_page(
    config: BuildConfig,
    category: CategoryContent,
    categories: Vector[CategoryContent],
    locale: String,
    page: Path
  ): String =
    s"""<!doctype html>
       |<html lang="${_html_escape(locale)}">
       |<head>
       |  <meta charset="utf-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1">
       |  <title>${_html_escape(_uif(locale, "category.document.title", category.title, config.siteTitle))}</title>
       |${_site_css_links(config, page)}
       |</head>
       |<body class="article ${_html_escape(_category_dashboard_theme_class(config))}">
       |${_category_header(config, categories, locale)}
       |<div class="body body-dashboard">
       |  <main class="article">
       |    <div class="toolbar" role="navigation">
       |      <button class="nav-toggle"></button>
       |      <a href="../index.html" class="home-link"></a>
       |      <nav class="breadcrumbs" aria-label="breadcrumbs">
       |        <ul>
       |          <li><a href="../index.html">${_html_escape(config.siteTitle)}</a></li>
       |          <li>${_html_escape(category.title)}</li>
       |        </ul>
       |      </nav>
       |    </div>
       |    <div class="content">
       |      <article class="doc">
       |        ${_category_dashboard(config, category, locale)}
       |        ${_source_narrative_section(config, _source_document(config.sourcepath.resolve(category.slug), "index"), locale)}
       |      </article>
       |    </div>
       |  </main>
       |</div>
       |</body>
       |</html>
       |""".stripMargin

  private def _category_header(
    config: BuildConfig,
    categories: Vector[CategoryContent],
    locale: String,
    rootPrefix: String = "../"
  ): String = {
    s"""<header class="header">
       |  <nav class="navbar">
       |    <div class="navbar-brand">
       |      <a class="navbar-item" href="${_html_escape(rootPrefix)}index.html">${_html_escape(config.siteTitle)}</a>
       |      <button class="navbar-burger" aria-controls="topbar-nav" aria-expanded="false" aria-label="Toggle main menu">
       |        <span></span>
       |        <span></span>
       |        <span></span>
       |      </button>
       |    </div>
       |    <div id="topbar-nav" class="navbar-menu">
       |      <div class="navbar-end">
       |        <a class="navbar-item" href="${_html_escape(rootPrefix)}index.html">${_html_escape(_ui(locale, "nav.home"))}</a>
       |        ${_bok_nav_menu(config, locale, rootPrefix)}
       |        ${_category_nav_menu(locale, categories.map(x => CategorySummary(x.slug, x.title, x.description, x.purpose)), rootPrefix)}
       |      </div>
       |    </div>
       |  </nav>
       |</header>""".stripMargin
  }

  private def _category_nav_container(
    config: BuildConfig,
    current: CategoryContent,
    categories: Vector[CategoryContent]
  ): String = {
    val items = categories.map { category =>
      val currentclass = if (category.slug == current.slug) " is-current" else ""
      s"""<li class="nav-item" data-depth="1"><a class="nav-link${currentclass}" href="../${_html_escape(category.slug)}/index.html">${_html_escape(category.title)}</a></li>"""
    }.mkString("\n                  ")
    s"""<div class="nav-container" data-component="home" data-version="">
       |    <aside class="nav">
       |      <div class="panels">
       |        <div class="nav-panel-menu is-active" data-panel="menu">
       |          <nav class="nav-menu">
       |            <button class="nav-menu-toggle" aria-label="Toggle expand/collapse all" style="display: none"></button>
       |            <h3 class="title"><a href="../index.html">${_html_escape(config.siteTitle)}</a></h3>
       |            <ul class="nav-list">
       |              <li class="nav-item" data-depth="0">
       |                <ul class="nav-list">
       |                  ${items}
       |                </ul>
       |              </li>
       |            </ul>
       |          </nav>
       |        </div>
       |      </div>
       |    </aside>
       |  </div>""".stripMargin
  }

  private def _category_toc_panel(config: BuildConfig, category: CategoryContent, locale: String): String =
    s"""<aside class="toc sidebar" data-title="Contents" data-levels="2">
      |    <div class="toc-menu">
      |      <h3>On this page</h3>
       |      <ul>
       |        <li><a href="#dashboard">Dashboard</a></li>
      |      </ul>
      |      <div class="bok-special-links">
      |        <h3>BoK Console</h3>
      |        <a class="bok-special-link" href="../glossary/index.html">Glossary</a>
      |        <a class="bok-special-link" href="${_html_escape(_history_href(config, "../"))}">History</a>
      |        <a class="bok-special-link" href="../manual/index.html">Manual</a>
      |      </div>
      |    </div>
      |  </aside>""".stripMargin

  private val _source_document_suffixes = Vector(".dox", ".md", ".markdown")

  private def _source_document(dir: Path, stem: String): Option[Path] =
    _source_document_suffixes.
      map(suffix => dir.resolve(s"${stem}${suffix}")).
      find(Files.isRegularFile(_))

  private def _is_source_document(path: Path): Boolean =
    _source_document_suffixes.exists(path.getFileName.toString.endsWith)

  private def _is_markdown_source_document(path: Path): Boolean = {
    val name = path.getFileName.toString.toLowerCase(java.util.Locale.ROOT)
    name.endsWith(".md") || name.endsWith(".markdown")
  }

  private def _is_index_source_document(path: Path): Boolean = {
    val name = path.getFileName.toString
    _source_document_suffixes.exists(suffix => name == s"index${suffix}")
  }

  private def _source_document_html_href(rel: String): String =
    _source_document_suffixes.
      find(rel.endsWith).
      map(suffix => rel.dropRight(suffix.length) + ".html").
      getOrElse(rel + ".html")

  private def _source_document_stem(name: String): String =
    _source_document_suffixes.
      find(name.endsWith).
      map(suffix => name.dropRight(suffix.length)).
      getOrElse(name)

  private def _source_narrative_section(config: BuildConfig, path: Option[Path], locale: String): String = {
    val body = _source_document_fragment(config, path, locale).
      map(x => _effective_document_fragment_body(x.bodyhtml)).
      getOrElse("")
    if (body.isEmpty)
      ""
    else
      s"""<div class="bok-narrative-corner" id="narrative">
         |  <div class="sectionbody">
         |    ${body}
         |  </div>
         |</div>""".stripMargin
  }

  private def _effective_document_fragment_body(body: String): String = {
    val trimmed = body.trim
    if (trimmed.isEmpty)
      ""
    else if (trimmed.toLowerCase(Locale.ROOT).startsWith("<html"))
      _article_body(trimmed).map(_empty_html_to_blank).getOrElse(_empty_html_to_blank(trimmed))
    else
      _empty_html_to_blank(trimmed)
  }

  private def _article_body(html: String): Option[String] = {
    val pattern = Pattern.compile("(?is)<article\\b[^>]*>(.*)</article>")
    val matcher = pattern.matcher(html)
    if (matcher.find())
      Some(matcher.group(1).trim)
    else
      None
  }

  private def _empty_html_to_blank(html: String): String =
    if (_is_effectively_empty_html(html))
      ""
    else
      html

  private def _is_effectively_empty_html(html: String): Boolean = {
    val text = html.
      replaceAll("(?is)<script\\b[^>]*>.*?</script>", "").
      replaceAll("(?is)<style\\b[^>]*>.*?</style>", "").
      replaceAll("(?is)<[^>]+>", "").
      replace("&nbsp;", " ").
      trim
    text.isEmpty
  }

  private def _source_document_fragment(config: BuildConfig, path: Option[Path], locale: String): Option[DocumentFragment] =
    for {
      source <- path.filter(Files.isRegularFile(_))
      rel <- _source_document_relative(config, source)
      index <- _document_fragment_index(config)
      fragment <- index.get(rel, locale).orElse(_source_document_fragment_by_public_path(index, rel, locale))
    } yield fragment

  private def _source_document_fragment_by_public_path(index: DocumentFragmentIndex, sourcepath: String, locale: String): Option[DocumentFragment] =
    _source_document_public_path(sourcepath).flatMap(index.get(_, locale))

  private def _source_document_public_path(sourcepath: String): Option[String] =
    _source_document_suffixes.find(sourcepath.endsWith).map { suffix =>
      sourcepath.stripSuffix(suffix) + ".html"
    }

  private def _source_document_relative(config: BuildConfig, source: Path): Option[String] =
    try {
      Some(config.sourcepath.relativize(source).toString.replace(java.io.File.separatorChar, '/'))
    } catch {
      case NonFatal(_) => None
    }

  private def _source_narrative_html(path: Option[Path], locale: String): String =
    path.filter(Files.isRegularFile(_)).map { source =>
      val content = Files.readString(source, StandardCharsets.UTF_8)
      _source_narrative_html(content, source.toString, locale)
    }.getOrElse("")

  private def _source_narrative_html(path: Path, locale: String): String =
    _source_narrative_html(Option(path), locale)

  private def _source_narrative_html(content: String, name: String, locale: String): String = {
    val dox = Dox2Parser.parseWithFilename(Dox2Parser.Config.default, name, content)
    val rule = Dox2HtmlTransformer.Rule(isDocument = false, sectionBaseNumber = Some(2), isDefaultCss = false)
    val body = _source_narrative_dox(dox)
    _rendered_body_fragment(Dox2HtmlTransformer(SmartDoxContext.create(), rule).transform(body).take).trim
  }

  private def _source_narrative_dox(dox: Dox): Dox =
    dox match {
      case m: Document => Dox.toDox(m.body.contents)
      case m: Body => Dox.toDox(m.contents)
      case m => m
    }

  private def _ui(locale: String, key: String): String =
    _ui_context(_to_locale(locale)).message(key)

  private def _uif(locale: String, key: String, args: Any*): String =
    _ui_context(_to_locale(locale)).message(key, args: _*)

  private def _to_locale(value: String): Locale =
    Locale.forLanguageTag(value.replace('_', '-'))

  private def _ui_context(locale: Locale): I18NContext = {
    val bundle = I18NContext.loadResourceBundle(_ui_resource_base, locale, _ui_resource_config)
    I18NContext.default.copy(locale = locale, resourceBundle = bundle)
  }


  private def _dashboard_theme_class(config: BuildConfig): String =
    s"bok-dashboard-theme-${config.dashboardColorGroup}"

  private def _category_dashboard_theme_class(config: BuildConfig): String =
    s"bok-dashboard-theme-${_category_dashboard_color_group(config.dashboardColorGroup)}"

  private def _support_dashboard_theme_class: String =
    "bok-dashboard-theme-paper"

  private def _category_dashboard_color_group(group: String): String =
    group match {
      case "aurora" => "lagoon"
      case "lagoon" => "meadow"
      case "meadow" => "lagoon"
      case "ocean" => "sand"
      case "ember" => "slate"
      case "slate" => "aurora"
      case _ => "lagoon"
    }

  private val _dashboard_color_groups = Set("aurora", "lagoon", "meadow", "ocean", "ember", "slate", "sand")

  private def _dashboard_color_group(parsed: ParsedArgs, config: CozyProjectYamlConfig.Config, site: SiteConfig): String = {
    val raw =
      parsed.property("dashboard-color-group").
        orElse(config.value("bok.dashboard.color-group")).
        orElse(config.value("bok.dashboard.color_group")).
        orElse(site.value("site.metadata.dashboard_color_group")).
        orElse(site.value("site.metadata.dashboard-color-group")).
        orElse(site.value("site.metadata.dashboard.color_group")).
        getOrElse("aurora")
    val normalized = raw.trim.toLowerCase(java.util.Locale.ROOT).replace('_', '-').replace(' ', '-')
    if (_dashboard_color_groups.contains(normalized)) normalized else "aurora"
  }

  private def _site_asset_href(config: BuildConfig, page: Path, path: String): String =
    _site_root_prefix(config, page) + path

  private def _relative_href(page: Path, destination: Path): String =
    page.toAbsolutePath.normalize.getParent.relativize(destination.toAbsolutePath.normalize).toString.replace(java.io.File.separatorChar, '/')

  private def _site_css_links(config: BuildConfig, page: Path): String =
    s"""  <link rel="stylesheet" href="${_html_escape(_site_asset_href(config, page, "_/css/bootstrap-grid.min.css"))}">
       |  <link rel="stylesheet" href="${_html_escape(_site_asset_href(config, page, "_/css/site.css"))}">
       |  <link rel="stylesheet" href="${_html_escape(_site_asset_href(config, page, "_/css/cozy-bok-dashboard.css"))}">""".stripMargin

  private def _site_root_prefix(config: BuildConfig, page: Path): String = {
    val pagedir = Option(page.getParent).getOrElse(config.websitePath)
    val relative = config.websitePath.toAbsolutePath.normalize.relativize(pagedir.toAbsolutePath.normalize)
    val depth =
      if (relative.toString.isEmpty)
        0
      else
        relative.iterator.asScala.length
    if (depth == 0)
      ""
    else
      "../" * depth
  }

  private def _rendered_body_fragment(html: String): String =
    _regex_first(html, """(?s)<body>\s*<article[^>]*>(.*?)</article>\s*</body>""").
      orElse(_regex_first(html, """(?s)<body[^>]*>(.*?)</body>""")).
      getOrElse(html)

  private def _regex_first(value: String, regex: String): Option[String] =
    regex.r.findFirstMatchIn(value).map(_.group(1))

  private def _category_nav_menu(locale: String, categories: Vector[CategorySummary], prefix: String): String =
    if (categories.isEmpty)
      ""
    else {
      val dropdownitems = categories.map { category =>
        s"""<a class="navbar-item navbar-dropdown-item" href="${_html_escape(prefix)}${_html_escape(category.slug)}/index.html">${_html_escape(category.title)}</a>"""
      }.mkString("\n          ")
      s"""<div class="navbar-item has-dropdown is-hoverable navbar-category-nav navbar-category-dropdown" aria-label="${_html_escape(_ui(locale, "nav.categories"))}">
         |  <a class="navbar-link navbar-category-toggle" href="#">${_html_escape(_ui(locale, "nav.categories"))}</a>
         |  <div class="navbar-dropdown navbar-category-menu">
         |          ${dropdownitems}
         |  </div>
         |</div>""".stripMargin
    }

  private def _bok_nav_menu(config: BuildConfig, locale: String, prefix: String): String =
    s"""<div class="navbar-item has-dropdown is-hoverable navbar-bok-nav navbar-bok-dropdown" aria-label="${_html_escape(_ui(locale, "nav.bok"))}">
       |  <a class="navbar-link navbar-bok-toggle" href="#">${_html_escape(_ui(locale, "nav.bok"))}</a>
       |  <div class="navbar-dropdown navbar-bok-menu">
       |    <a class="navbar-item navbar-dropdown-item" href="${_html_escape(prefix)}glossary/index.html">${_html_escape(_ui(locale, "glossary.title"))}</a>
       |    <a class="navbar-item navbar-dropdown-item" href="${_html_escape(prefix)}articles/index.html">${_html_escape(_ui(locale, "dashboard.kpi.articles"))}</a>
       |    <a class="navbar-item navbar-dropdown-item" href="${_html_escape(prefix)}scenarios/index.html">${_html_escape(_ui(locale, "scenario.title"))}</a>
       |    <a class="navbar-item navbar-dropdown-item" href="${_html_escape(prefix)}projects/index.html">${_html_escape(_ui(locale, "project.title"))}</a>
       |    <a class="navbar-item navbar-dropdown-item" href="${_html_escape(prefix)}bibliography/index.html">${_html_escape(_ui(locale, "bibliography.title"))}</a>
       |    <a class="navbar-item navbar-dropdown-item" href="${_html_escape(prefix)}tags/index.html">${_html_escape(_ui(locale, "tag.title"))}</a>
       |    <a class="navbar-item navbar-dropdown-item" href="${_html_escape(_history_href(config, prefix))}">${_html_escape(_ui(locale, "history.title"))}</a>
       |    <a class="navbar-item navbar-dropdown-item" href="${_html_escape(prefix)}manual/index.html">${_html_escape(_ui(locale, "manual.title"))}</a>
       |  </div>
       |</div>""".stripMargin

  private def _home_nav_container(config: BuildConfig): String = {
    val items = _regular_category_summaries(config.sourcepath).map { category =>
      s"""<li class="nav-item" data-depth="1"><a class="nav-link" href="${_html_escape(category.slug)}/index.html">${_html_escape(category.title)}</a></li>"""
    }.mkString("\n          ")
    s"""<div class="nav-container" data-component="home" data-version="">
       |    <aside class="nav">
       |      <div class="panels">
       |        <div class="nav-panel-menu is-active" data-panel="menu">
       |          <nav class="nav-menu">
       |            <button class="nav-menu-toggle" aria-label="Toggle expand/collapse all" style="display: none"></button>
       |            <h3 class="title"><a href="index.html">${_html_escape(config.siteTitle)}</a></h3>
       |            <ul class="nav-list">
       |              <li class="nav-item" data-depth="0">
       |                <ul class="nav-list">
       |                  ${items}
       |                </ul>
       |              </li>
       |            </ul>
       |          </nav>
       |        </div>
       |      </div>
       |    </aside>
       |  </div>""".stripMargin
  }

  private def _home_toc_panel(config: BuildConfig, locale: String): String =
    s"""<aside class="toc sidebar" data-title="Contents" data-levels="2">
      |    <div class="toc-menu">
       |      <h3>On this page</h3>
       |      <ul>
       |        ${_home_dashboard_toc_item(config)}
       |      </ul>
      |      <div class="bok-special-links">
      |        <h3>BoK Console</h3>
      |        <a class="bok-special-link" href="glossary/index.html">Glossary</a>
      |        <a class="bok-special-link" href="${_html_escape(_history_href(config, ""))}">History</a>
      |        <a class="bok-special-link" href="manual/index.html">Manual</a>
      |      </div>
      |    </div>
      |  </aside>""".stripMargin

  private def _home_dashboard_toc_item(config: BuildConfig): String =
    if (_dashboard(config).isDefined || !_bok_purpose(config).isEmpty)
      """<li><a href="#dashboard">Dashboard</a></li>"""
    else
      ""

  private def _home_dashboard(config: BuildConfig, locale: String): String = {
    val purpose = _bok_purpose(config)
    val dashboard = _dashboard(config)
    val sourcedocument = _source_document(config.sourcepath, "index")
    val fragment = _source_document_fragment(config, sourcedocument, locale)
    val articlecount = _source_article_count(config)
    if (dashboard.isEmpty && purpose.isEmpty)
      ""
    else {
      val hero = _dashboard_hero(
        fragment.flatMap(_.effectiveHeadline).orElse(sourcedocument.map(_dox_title)).getOrElse(_uif(locale, "home.page.title", config.siteTitle)),
        fragment.flatMap(_.effectiveBrief).orElse(sourcedocument.map(_dox_brief)).getOrElse(_ui(locale, "home.intro")),
        Vector(
          _ui(locale, "dashboard.kpi.categories") -> dashboard.map(_.counts.categoryCount.toString).getOrElse("-"),
          _ui(locale, "dashboard.kpi.articles") -> articlecount.toString,
          _ui(locale, "dashboard.kpi.terms") -> dashboard.map(_.counts.glossaryTermCount.toString).getOrElse("-"),
          _ui(locale, "dashboard.kpi.rdf.triples") -> dashboard.map(_.rdf.tripleCount.toString).getOrElse("-")
        )
      )
      s"""<section class="bok-dashboard-shell" id="dashboard">
         |  ${hero}
         |  ${_home_dashboard_grid(config, purpose, dashboard, locale)}
         |</section>""".stripMargin
    }
  }

  private def _category_dashboard(config: BuildConfig, category: CategoryContent, locale: String): String = {
    val site = _dashboard(config)
    val dashboard = site.flatMap(_.categories.find(_.name == category.slug))
    val sourcedocument = _source_document(config.sourcepath.resolve(category.slug), "index")
    val fragment = _source_document_fragment(config, sourcedocument, locale)
    val categoryterms = _category_term_page_items(config, category.slug)
    val categoryarticlecount = category.articles.size
    if (!category.purpose.isEmpty || dashboard.isDefined) {
      val categoryrdf = dashboard.flatMap(_.rdf)
      val hero = _dashboard_hero(
        fragment.flatMap(_.effectiveHeadline).orElse(sourcedocument.map(_dox_title)).getOrElse(_uif(locale, "category.page.title", category.title)),
        fragment.flatMap(_.effectiveBrief).orElse(sourcedocument.map(_dox_brief)).getOrElse(_uif(locale, "category.intro", category.description)),
        Vector(
          _ui(locale, "dashboard.kpi.articles") -> categoryarticlecount.toString,
          _ui(locale, "dashboard.kpi.terms") -> dashboard.map(_.counts.glossaryTermCount.toString).getOrElse(categoryterms.size.toString),
          _ui(locale, "dashboard.kpi.rdf") -> categoryrdf.map(_.tripleCount.toString).getOrElse("-")
        )
      )
      s"""<section class="bok-dashboard-shell" id="dashboard">
         |  ${hero}
         |  ${_category_dashboard_grid(config, category, categoryterms, dashboard, categoryrdf, locale)}
         |</section>""".stripMargin
    }
    else
      s"<p>${_html_escape(_ui(locale, "dashboard.unavailable"))}</p>"
  }

  private def _dashboard_hero(title: String, body: String, facts: Vector[(String, String)]): String = {
    val facthtml = facts.map {
      case (label, value) =>
        s"""<span class="bok-dashboard-hero-fact"><strong>${_html_escape(value)}</strong><em>${_html_escape(label)}</em></span>"""
    }.mkString("\n")
    s"""<header class="bok-dashboard-hero">
       |  <div class="bok-dashboard-hero-copy">
       |    <p class="bok-dashboard-eyebrow">Dashboard</p>
       |    <h1 class="page">${_html_escape(title)}</h1>
       |    <p class="bok-dashboard-lead">${_html_escape(body)}</p>
       |  </div>
       |  <div class="bok-dashboard-hero-facts">
       |    ${facthtml}
       |  </div>
       |</header>""".stripMargin
  }

  private def _bok_purpose(config: BuildConfig): BokPurpose =
    _site_purpose(_load_site_config(config.sourcepath))

  private def _site_purpose(site: SiteConfig): BokPurpose =
    _purpose_from_parts(
      site.value("site.metadata.vision"),
      _first_non_empty(
        site.goalTree("site.metadata.goals"),
        site.goalTree("site.metadata.goal_tree")
      ),
      site.list("site.metadata.goals"),
      site.list("site.metadata.subgoals")
    )

  private def _first_non_empty[T](xs: Vector[T]*): Vector[T] =
    xs.find(_.nonEmpty).getOrElse(Vector.empty)

  private def _purpose_from_parts(
    vision: Option[String],
    structuredgoals: Vector[BokGoal],
    flatgoals: Vector[String],
    flatsubgoals: Vector[String]
  ): BokPurpose =
    if (structuredgoals.nonEmpty)
      BokPurpose(vision, structuredgoals)
    else
      BokPurpose(vision, Vector.empty, flatgoals, flatsubgoals)

  private def _purpose_dashboard(purpose: BokPurpose): String =
    if (purpose.isEmpty)
      ""
    else
      s"""<div class="bok-purpose">
         |  ${purpose.vision.map(x => s"""<p><strong>Vision:</strong> ${_html_escape(x)}</p>""").getOrElse("")}
         |  ${_purpose_list("Goals", if (purpose.goals.nonEmpty) purpose.goals.map(_.title) else purpose.flatGoals)}
         |  ${_purpose_list("Subgoals", purpose.flatSubgoals)}
         |</div>""".stripMargin

  private def _purpose_list(label: String, values: Vector[String]): String =
    if (values.isEmpty)
      ""
    else
      values.map(x => s"<li>${_html_escape(x)}</li>").mkString(s"<div><strong>${label}:</strong><ul>", "", "</ul></div>")

  private def _home_dashboard_grid(config: BuildConfig, purpose: BokPurpose, dashboard: Option[BokDashboard], locale: String): String = {
    val scenarios = _scenario_index(config).map(_.scenarios).getOrElse(Vector.empty)
    val bibliographies = _bibliography_index(config).map(_.entries).getOrElse(Vector.empty)
    val termcount = dashboard.map(_.counts.glossaryTermCount).getOrElse(_terms(config).size)
    val articlecount = _source_article_count(config)
    val rdfcount = dashboard.map(_.rdf.tripleCount).getOrElse(0)
    val projectcount = _project_package_dirs(config.sourcepath).size
    val tagcount = _tag_index(config, locale).tags.size
    val cards = Vector[Option[String]](
      dashboard.map(x => _recent_activity_card(config, locale, x, _home_recent_items(config))),
      if (purpose.isEmpty) None else Some(_dashboard_card("col-12 col-xl-8", "bok-card-purpose", _ui(locale, "dashboard.card.vision"), _purpose_card_body(locale, purpose), Vector("reader", "contributor", "project_manager"))),
      Some(_analysis_entry_card(locale, termcount, articlecount, scenarios.size, projectcount, rdfcount, "articles/index.html", "glossary/index.html", "scenarios/index.html", "projects/index.html", "rdf/index.html")),
      dashboard.map(x => _dashboard_card(if (purpose.isEmpty) "col-12 col-xl-7" else "col-12 col-xl-4", "bok-card-matrix", _ui(locale, "dashboard.card.category.matrix"), _category_matrix_body(config, locale, x), Vector("reader", "contributor", "project_manager"), Some("categories"))),
      dashboard.map(x => _kpi_card_link("col-6 col-md-3", _ui(locale, "dashboard.kpi.categories"), x.counts.categoryCount.toString, _ui(locale, "dashboard.kpi.categories.note"), "category/index.html")),
      dashboard.map(_ => _kpi_card_link("col-6 col-md-3", _ui(locale, "dashboard.kpi.articles"), articlecount.toString, _ui(locale, "dashboard.kpi.articles.note"), "articles/index.html")),
      dashboard.map(x => _kpi_card_link("col-6 col-md-3", _ui(locale, "dashboard.kpi.terms"), x.counts.glossaryTermCount.toString, _ui(locale, "dashboard.kpi.terms.note"), "glossary/index.html")),
      dashboard.map(x => _kpi_card_link("col-6 col-md-3", _ui(locale, "dashboard.kpi.rdf.triples"), x.rdf.tripleCount.toString, _ui(locale, "dashboard.kpi.rdf.triples.note"), "rdf/index.html")),
      Some(_kpi_card_link("col-6 col-md-3", _ui(locale, "dashboard.kpi.scenarios"), scenarios.size.toString, _ui(locale, "dashboard.kpi.scenarios.note"), "scenarios/index.html")),
      Some(_kpi_card_link("col-6 col-md-3", _ui(locale, "project.title"), projectcount.toString, _ui(locale, "project.kpi.note"), "projects/index.html")),
      Some(_kpi_card_link("col-6 col-md-3", _ui(locale, "tag.title"), tagcount.toString, _ui(locale, "tag.kpi.note"), "tags/index.html")),
      Some(_kpi_card_link("col-6 col-md-3", _ui(locale, "dashboard.kpi.bibliography"), bibliographies.size.toString, _ui(locale, "dashboard.kpi.bibliography.note"), "bibliography/index.html", Vector("contributor", "project_manager"))),
      Some(_dashboard_card("col-12 col-xl-4", "bok-card-quality", _ui(locale, "dashboard.card.quality.alerts"), _quality_alerts_body(locale, dashboard.isDefined), Vector("contributor", "project_manager"))),
      dashboard.map { x =>
        val increments = _article_adjusted_increments(x.increments, x.counts.articleCount - articlecount)
        _dashboard_card("col-12 col-xl-8", "bok-card-chart", _ui(locale, "dashboard.card.growth"), _dashboard_increment_chart(locale, increments, _ui(locale, "dashboard.chart.bok.additions")), Vector("project_manager", "contributor"))
      },
      Some(_dashboard_card("col-12 col-md-6 col-xl-6", "bok-card-readiness", _ui(locale, "dashboard.card.readiness"), _home_readiness_body(locale, config, dashboard), Vector("site_administrator", "project_manager"))),
      Some(_dashboard_card("col-12 col-md-6 col-xl-6", "bok-card-actions", _ui(locale, "dashboard.card.next.actions"), _next_actions_body(locale, config), Vector("site_administrator", "project_manager")))
    ).flatten
    _dashboard_container(locale, cards)
  }

  private def _category_dashboard_grid(
    config: BuildConfig,
    category: CategoryContent,
    categoryTerms: Vector[CategoryPageItem],
    dashboard: Option[DashboardCategory],
    rdf: Option[DashboardRdfSummary],
    locale: String
  ): String = {
    val categoryscenarios = _scenario_index(config).map(_.scenarios.filter(_.category.contains(category.slug))).getOrElse(Vector.empty)
    val categoryprojects = _project_package_dirs(config.sourcepath).count { path =>
      val projects = config.sourcepath.resolve("projects").toAbsolutePath.normalize()
      val relative = projects.relativize(path.toAbsolutePath.normalize())
      relative.getNameCount >= 1 && relative.getName(0).toString == category.slug
    }
    val categoryarticlecount = category.articles.size
    val categoryrdfcount = rdf.map(_.tripleCount).getOrElse(0)
    val categorytagcount = _tag_index(config, locale).forCategory(category.slug).tags.size
    val cards = Vector[Option[String]](
      if (category.purpose.isEmpty) None else Some(_dashboard_card("col-12 col-xl-8", "bok-card-purpose bok-card-category-purpose", _ui(locale, "dashboard.card.category.vision"), _purpose_card_body(locale, category.purpose), Vector("reader", "contributor", "project_manager"))),
      Some(_analysis_entry_card(locale, categoryTerms.size, categoryarticlecount, categoryscenarios.size, categoryprojects, categoryrdfcount, s"../articles/index.html?category=${_url_query_escape(category.slug)}", s"../glossary/${category.slug}/index.html", s"../scenarios/index.html?category=${_url_query_escape(category.slug)}", s"../projects/index.html?category=${_url_query_escape(category.slug)}", s"../rdf/index.html?category=${_url_query_escape(category.slug)}")),
      Some(_dashboard_card("col-12 col-xl-6", "bok-card-map", _ui(locale, "dashboard.card.term.map"), _page_map_body(categoryTerms, _ui(locale, "dashboard.term.empty")), Vector("reader", "contributor", "project_manager"))),
      Some(_dashboard_card("col-12 col-xl-6", "bok-card-map", _ui(locale, "dashboard.card.article.map"), _page_map_body(category.articles, _ui(locale, "dashboard.article.empty")), Vector("reader", "contributor", "project_manager"))),
      rdf.map(x => _category_rdf_kpi_card(locale, category, x)),
      dashboard.map(_ => _kpi_card(locale, "col-6 col-md-3", _ui(locale, "dashboard.kpi.articles"), categoryarticlecount.toString, _ui(locale, "dashboard.kpi.category.articles.note"))),
      dashboard.map(x => _kpi_card(locale, "col-6 col-md-3", _ui(locale, "dashboard.kpi.terms"), x.counts.glossaryTermCount.toString, _ui(locale, "dashboard.kpi.category.terms.note"))),
      Some(_kpi_card_link("col-6 col-md-3", _ui(locale, "dashboard.kpi.scenarios"), categoryscenarios.size.toString, _ui(locale, "dashboard.kpi.scenarios.note"), s"../scenarios/index.html?category=${_url_query_escape(category.slug)}")),
      Some(_kpi_card_link("col-6 col-md-3", _ui(locale, "project.title"), categoryprojects.toString, _ui(locale, "project.kpi.note"), s"../projects/index.html?category=${_url_query_escape(category.slug)}")),
      Some(_kpi_card_link("col-6 col-md-3", _ui(locale, "tag.title"), categorytagcount.toString, _ui(locale, "tag.kpi.note"), s"../tags/index.html?category=${_url_query_escape(category.slug)}")),
      Some(_kpi_card(locale, "col-6 col-md-3", _ui(locale, "dashboard.kpi.issues"), "0", _ui(locale, "dashboard.kpi.issues.note"))),
      Some(_dashboard_card("col-12 col-xl-5", "bok-card-quality", _ui(locale, "dashboard.card.local.quality.alerts"), _quality_alerts_body(locale, dashboard.isDefined), Vector("contributor", "project_manager"))),
      dashboard.map { x =>
        val increments = _article_adjusted_increments(x.increments, x.counts.articleCount - categoryarticlecount)
        _dashboard_card("col-12 col-xl-7", "bok-card-chart", _ui(locale, "dashboard.card.category.growth"), _dashboard_increment_chart(locale, increments, _uif(locale, "dashboard.chart.category.additions", x.title)), Vector("project_manager", "contributor"))
      },
      Some(_dashboard_card("col-12 col-md-6 col-xl-3", "bok-card-readiness", _ui(locale, "dashboard.card.category.readiness"), _category_readiness_body(locale, dashboard), Vector("site_administrator", "project_manager"))),
      _recent_activity_card(config, locale, category),
      Some(_dashboard_card("col-12 col-md-6 col-xl-5", "bok-card-related", _ui(locale, "dashboard.card.related.knowledge"), _related_knowledge_body(locale, config, category, rdf), Vector("reader", "contributor", "project_manager")))
    ).flatten
    _dashboard_container(locale, cards)
  }

  private def _dashboard_container(locale: String, cards: Vector[String]): String = {
    val defaultactor = "reader"
    cards.mkString(
      s"""<div class="bok-dashboard container-fluid bok-dashboard-command-center" data-bok-dashboard="true" data-bok-default-actor="${defaultactor}" data-bok-default-card-mode="hide" data-bok-status-all="${_html_escape(_uif(locale, "dashboard.actor.status.all", cards.size.toString))}" data-bok-status-filtered="${_html_escape(_ui(locale, "dashboard.actor.status.filtered"))}" data-bok-status-dimmed="${_html_escape(_ui(locale, "dashboard.actor.status.dimmed"))}">
         |  ${_dashboard_actor_filter(locale, cards, defaultactor)}
         |  <div class="row g-3">""".stripMargin,
      "\n",
      s"""  </div>
         |  ${_dashboard_actor_filter_script}
         |</div>""".stripMargin
    )
  }

  private def _dashboard_actor_filter(locale: String, cards: Vector[String], defaultactor: String): String = {
    val cardcount = cards.size
    val defaultcount = _dashboard_actor_count(cards, defaultactor)
    val actoroptions = Vector(
      "all" -> _ui(locale, "dashboard.actor.all"),
      "reader" -> _ui(locale, "dashboard.actor.reader"),
      "contributor" -> _ui(locale, "dashboard.actor.contributor"),
      "project_manager" -> _ui(locale, "dashboard.actor.project.manager"),
      "site_administrator" -> _ui(locale, "dashboard.actor.site.administrator")
    ).map {
      case (key, label) =>
        val selected = if (key == defaultactor) " selected" else ""
        s"""<option value="${_html_escape(key)}"${selected}>${_html_escape(label)}</option>"""
    }.mkString("\n")
    val modeoptions = Vector(
      "hide" -> _ui(locale, "dashboard.actor.mode.hide"),
      "dim" -> _ui(locale, "dashboard.actor.mode.dim")
    ).map {
      case (key, label) =>
        val selected = if (key == "hide") " selected" else ""
        s"""<option value="${_html_escape(key)}"${selected}>${_html_escape(label)}</option>"""
    }.mkString("\n")
    s"""<div class="bok-dashboard-actor-filter" role="group" aria-label="${_html_escape(_ui(locale, "dashboard.actor.filter"))}">
       |  <label class="bok-dashboard-actor-select-label"><span>${_html_escape(_ui(locale, "dashboard.actor.filter"))}</span><select class="bok-dashboard-actor-select" data-bok-actor-filter aria-label="${_html_escape(_ui(locale, "dashboard.actor.filter"))}">
       |${actoroptions}
       |  </select></label>
       |  <label class="bok-dashboard-actor-select-label bok-dashboard-actor-mode-select-label"><span>${_html_escape(_ui(locale, "dashboard.actor.mode"))}</span><select class="bok-dashboard-actor-select" data-bok-actor-mode aria-label="${_html_escape(_ui(locale, "dashboard.actor.mode"))}">
       |${modeoptions}
       |  </select></label>
       |  <span class="bok-dashboard-actor-status" data-bok-actor-status="true">${_html_escape(_uif(locale, "dashboard.actor.status.filtered", defaultcount.toString, cardcount.toString, _ui(locale, "dashboard.actor.reader")))}</span>
      |</div>""".stripMargin
  }

  private def _dashboard_actor_count(cards: Vector[String], actor: String): Int =
    if (actor == "all" || actor == "site_administrator")
      cards.size
    else
      cards.count { x =>
        val marker = "data-bok-actors=\""
        val start = x.indexOf(marker)
        if (start < 0)
          false
        else {
          val rest = x.substring(start + marker.length)
          val end = rest.indexOf('"')
          val value = if (end >= 0) rest.substring(0, end) else rest
          value.split("\\s+").contains(actor)
        }
      }

  private def _dashboard_actor_filter_script: String =
    """<script>
      |(function () {
      |  var validActors = ["all", "reader", "contributor", "project_manager", "site_administrator"];
      |  var validModes = ["hide", "dim"];
      |
      |  function defaultActor(root) {
      |    var value = root.getAttribute("data-bok-default-actor") || "reader";
      |    return validActors.indexOf(value) >= 0 ? value : "reader";
      |  }
      |
      |  function actorFromUrl(root) {
      |    try {
      |      var params = new URLSearchParams(window.location.search);
      |      var fallback = defaultActor(root);
      |      var value = params.get("actor") || fallback;
      |      return validActors.indexOf(value) >= 0 ? value : fallback;
      |    } catch (e) {
      |      return defaultActor(root);
      |    }
      |  }
      |
      |  function modeFromUrl() {
      |    try {
      |      var params = new URLSearchParams(window.location.search);
      |      var value = params.get("display") || "hide";
      |      return validModes.indexOf(value) >= 0 ? value : "hide";
      |    } catch (e) {
      |      return "hide";
      |    }
      |  }
      |
      |  function updateUrl(root, actor, mode) {
      |    if (!window.history || !window.history.replaceState) return;
      |    try {
      |      var url = new URL(window.location.href);
      |      if (actor === defaultActor(root)) {
      |        url.searchParams.delete("actor");
      |      } else {
      |        url.searchParams.set("actor", actor);
      |      }
      |      if (mode === "hide") {
      |        url.searchParams.delete("display");
      |      } else {
      |        url.searchParams.set("display", mode);
      }
      |      window.history.replaceState({}, "", url.toString());
      |    } catch (e) {
      |    }
      |  }
      |
      |  function format(template, values) {
      |    return template.replace(/\{(\d+)\}/g, function (_, index) {
      |      return values[index] || "";
      |    });
      |  }
      |
      |  function actorLabel(root, actor) {
      |    var select = root.querySelector("select[data-bok-actor-filter]");
      |    if (select) {
      |      var option = select.querySelector("option[value='" + actor + "']");
      |      if (option) return option.textContent;
      |    }
      |    var button = root.querySelector("[data-bok-actor-filter='" + actor + "']");
      |    return button ? button.textContent : actor;
      |  }
      |
      |  function ensureActorChips(root) {
      |    root.querySelectorAll(".bok-card[data-bok-actors]").forEach(function (card) {
      |      if (card.querySelector(".bok-card-actor-chips")) return;
      |      var actors = (card.getAttribute("data-bok-actors") || "").split(/\s+/).filter(Boolean);
      |      if (actors.length === 0) return;
      |      var chips = document.createElement("div");
      |      chips.className = "bok-card-actor-chips";
      |      actors.forEach(function (actor) {
      |        var chip = document.createElement("span");
      |        chip.textContent = actorLabel(root, actor);
      |        chips.appendChild(chip);
      |      });
      |      var title = card.querySelector(".card-title");
      |      if (title) {
      |        title.insertAdjacentElement("afterend", chips);
      |      }
      |    });
      |  }
      |
      |  function applyActor(root, actor, mode, updateLocation) {
      |    root.setAttribute("data-bok-current-actor", actor);
      |    root.setAttribute("data-bok-card-mode", mode);
      |    root.querySelectorAll("[data-bok-actor-filter]").forEach(function (control) {
      |      if (control.tagName === "SELECT") {
      |        control.value = actor;
      |      } else {
      |        var selected = control.getAttribute("data-bok-actor-filter") === actor;
      |        control.classList.toggle("is-active", selected);
      |        control.setAttribute("aria-pressed", selected ? "true" : "false");
      |      }
      |    });
      |    root.querySelectorAll("[data-bok-actor-mode]").forEach(function (control) {
      |      if (control.tagName === "SELECT") {
      |        control.value = mode;
      |      } else {
      |        var selected = control.getAttribute("data-bok-actor-mode") === mode;
      |        control.classList.toggle("is-active", selected);
      |        control.setAttribute("aria-pressed", selected ? "true" : "false");
      |      }
      |    });
      |    root.querySelectorAll(".bok-card[data-bok-actors]").forEach(function (card) {
      |      var actors = (card.getAttribute("data-bok-actors") || "").split(/\s+/);
      |      var matches = actor === "all" || actor === "site_administrator" || actors.indexOf(actor) >= 0;
      |      var hide = !matches && mode === "hide";
      |      var dim = !matches && mode === "dim";
      |      var wrapper = card.closest("[data-bok-card]") || card;
      |      wrapper.hidden = hide;
      |      wrapper.classList.toggle("is-bok-filter-hidden", hide);
      |      wrapper.classList.toggle("is-bok-filter-dimmed", dim);
      |    });
      |    var cards = Array.prototype.slice.call(root.querySelectorAll(".bok-card[data-bok-actors]"));
      |    var total = cards.length;
      |    var matching = cards.filter(function (card) {
      |      var actors = (card.getAttribute("data-bok-actors") || "").split(/\s+/);
      |      return actor === "all" || actor === "site_administrator" || actors.indexOf(actor) >= 0;
      |    }).length;
      |    var visible = cards.filter(function (card) {
      |      return !(card.closest("[data-bok-card]") || card).hidden;
      |    }).length;
      |    var status = root.querySelector("[data-bok-actor-status]");
      |    if (status) {
      |      if (actor === "all" || actor === "site_administrator") {
      |        status.textContent = (root.getAttribute("data-bok-status-all") || format("All {0} cards are visible.", [String(total)])).replace("{0}", String(total));
      |      } else if (mode === "dim") {
      |        status.textContent = format(root.getAttribute("data-bok-status-dimmed") || "{2}: highlighting {0} of {1} cards.", [String(matching), String(total), actorLabel(root, actor)]);
      |      } else {
      |        status.textContent = format(root.getAttribute("data-bok-status-filtered") || "{2}: showing {0} of {1} cards.", [String(visible), String(total), actorLabel(root, actor)]);
      |      }
      |    }
      |    if (updateLocation) updateUrl(root, actor, mode);
      |  }
      |
      |  document.querySelectorAll("[data-bok-dashboard]").forEach(function (root) {
      |    ensureActorChips(root);
      |    applyActor(root, actorFromUrl(root), modeFromUrl(), false);
      |    root.querySelectorAll("[data-bok-actor-filter]").forEach(function (control) {
      |      var eventName = control.tagName === "SELECT" ? "change" : "click";
      |      control.addEventListener(eventName, function () {
      |        var value = control.tagName === "SELECT" ? control.value : control.getAttribute("data-bok-actor-filter");
      |        applyActor(root, value || defaultActor(root), root.getAttribute("data-bok-card-mode") || "hide", true);
      |      });
      |    });
      |    root.querySelectorAll("[data-bok-actor-mode]").forEach(function (control) {
      |      var eventName = control.tagName === "SELECT" ? "change" : "click";
      |      control.addEventListener(eventName, function () {
      |        var value = control.tagName === "SELECT" ? control.value : control.getAttribute("data-bok-actor-mode");
      |        applyActor(root, root.getAttribute("data-bok-current-actor") || defaultActor(root), value || "hide", true);
      |      });
      |    });
      |  });
      |}());
      |</script>""".stripMargin

  private def _dashboard_card(column: String, semantic: String, title: String, body: String, actors: Vector[String] = Vector.empty, anchorId: Option[String] = None): String = {
    val actorattr =
      if (actors.isEmpty)
        ""
      else
        s""" data-bok-actors="${_html_escape(actors.mkString(" "))}""""
    val idattr = anchorId.map(x => s""" id="${_html_escape(x)}"""").getOrElse("")
    s"""<div class="${_html_escape(column)}" data-bok-card="true">
       |  <section class="card bok-card ${_html_escape(semantic)}"${idattr}${actorattr}>
       |    <div class="card-body">
       |      <h3 class="card-title">${_html_escape(title)}</h3>
       |      ${body}
       |    </div>
       |  </section>
       |</div>""".stripMargin
  }

  private def _recent_activity_card(config: BuildConfig, locale: String, dashboard: BokDashboard, fallbackitems: Vector[DashboardRecentItem]): String = {
    val actorattr = """ data-bok-actors="reader contributor project_manager""""
    s"""<div class="col-12" data-bok-card="true">
       |  <section class="card bok-card bok-card-activity bok-card-notification"${actorattr}>
       |    <div class="card-body">
       |      <h3 class="card-title bok-card-title-with-action"><span>${_html_escape(_ui(locale, "dashboard.card.recent.activity"))}</span><a class="bok-card-title-link" href="${_html_escape(_history_href(config, ""))}">${_html_escape(_ui(locale, "dashboard.activity.open.history"))}</a></h3>
       |      ${_recent_activity_body(locale, dashboard.increments, fallbackitems, true)}
       |    </div>
       |  </section>
       |</div>""".stripMargin
  }

  private def _recent_activity_card(config: BuildConfig, locale: String, category: CategoryContent): Option[String] = {
    val items = _category_recent_items(config, category)
    if (items.isEmpty)
      None
    else {
    val actorattr = """ data-bok-actors="reader contributor project_manager""""
    Some(s"""<div class="col-12 col-md-6 col-xl-4" data-bok-card="true">
       |  <section class="card bok-card bok-card-activity bok-card-notification"${actorattr}>
       |    <div class="card-body">
       |      <h3 class="card-title bok-card-title-with-action"><span>${_html_escape(_ui(locale, "dashboard.card.recent.changes"))}</span><a class="bok-card-title-link" href="${_html_escape(_history_href(config, "../"))}">${_html_escape(_ui(locale, "dashboard.activity.open.history"))}</a></h3>
       |      ${_recent_activity_body(locale, DashboardIncrements("day", Vector.empty), items, false)}
       |    </div>
       |  </section>
       |</div>""".stripMargin)
    }
  }

  private def _kpi_card(locale: String, column: String, label: String, value: String, note: String): String =
    _dashboard_card(
      column,
      "bok-card-kpi",
      label,
      s"""<div class="bok-kpi-value">${_html_escape(value)}</div>
         |<div class="bok-kpi-label">${_html_escape(label)}</div>
         |<div class="bok-kpi-note">${_html_escape(note)}</div>""".stripMargin,
      Vector("reader", "contributor", "project_manager")
    )

  private def _kpi_card_link(column: String, label: String, value: String, note: String, href: String, actors: Vector[String] = Vector("reader", "contributor", "project_manager")): String =
    _dashboard_card(
      column,
      "bok-card-kpi bok-card-kpi-link",
      label,
      s"""<a class="bok-kpi-link" href="${_html_escape(href)}">
         |  <span class="bok-kpi-value">${_html_escape(value)}</span>
         |  <span class="bok-kpi-label">${_html_escape(label)}</span>
         |  <span class="bok-kpi-note">${_html_escape(note)}</span>
         |</a>""".stripMargin,
      actors
    )

  private def _analysis_entry_card(
    locale: String,
    termcount: Int,
    articlecount: Int,
    scenariocount: Int,
    projectcount: Int,
    rdfcount: Int,
    articleHref: String,
    glossaryHref: String,
    scenarioHref: String,
    projectHref: String,
    rdfHref: String
  ): String =
    _dashboard_card(
      "col-12",
      "bok-card-analysis-entry",
      _ui(locale, "dashboard.card.analysis.entry"),
      s"""<p class="bok-analysis-entry-lead">${_html_escape(_ui(locale, "dashboard.analysis.entry.description"))}</p>
         |<div class="bok-analysis-entry-flow">
         |  <div class="bok-analysis-entry-analysis">
         |    <div class="bok-analysis-entry-group-title">${_html_escape(_ui(locale, "dashboard.analysis.entry.mono.koto.title"))}</div>
         |    <div class="bok-analysis-entry-relation">
         |      <div class="bok-analysis-entry-column bok-analysis-entry-inputs">
         |        ${_analysis_entry_tile(Some(articleHref), _ui(locale, "dashboard.kpi.articles"), _ui(locale, "dashboard.analysis.entry.article.note"), _uif(locale, "dashboard.analysis.entry.count.articles", articlecount.toString), "article")}
         |        ${_analysis_entry_tile(Some(scenarioHref), _ui(locale, "scenario.title"), _ui(locale, "dashboard.analysis.entry.scenario.note"), _uif(locale, "dashboard.analysis.entry.count.scenarios", scenariocount.toString), "scenario")}
         |      </div>
         |      <div class="bok-analysis-entry-arrow-column bok-analysis-entry-input-arrows" aria-hidden="true">
         |        <span class="bok-analysis-entry-arrow">➜</span>
         |        <span class="bok-analysis-entry-arrow">➜</span>
         |      </div>
         |      <div class="bok-analysis-entry-center">
         |        ${_analysis_entry_tile(Some(glossaryHref), _ui(locale, "glossary.title"), _ui(locale, "dashboard.analysis.entry.glossary.note"), _uif(locale, "dashboard.analysis.entry.count.terms", termcount.toString), "mono-koto-process")}
         |      </div>
         |    </div>
         |  </div>
         |  <div class="bok-analysis-entry-output-relation">
         |    <div class="bok-analysis-entry-arrow-column bok-analysis-entry-output-arrows" aria-hidden="true">
         |      <span class="bok-analysis-entry-arrow">➜</span>
         |      <span class="bok-analysis-entry-arrow">➜</span>
         |    </div>
         |    <div class="bok-analysis-entry-column bok-analysis-entry-outputs">
         |      ${_analysis_entry_tile(Some(rdfHref), _ui(locale, "dashboard.kpi.rdf.triples"), _ui(locale, "dashboard.analysis.entry.rdf.note"), _uif(locale, "dashboard.analysis.entry.count.rdf", rdfcount.toString), "rdf")}
         |      ${_analysis_entry_tile(Some(projectHref), _ui(locale, "project.title"), _ui(locale, "dashboard.analysis.entry.project.note"), _uif(locale, "dashboard.analysis.entry.count.projects", projectcount.toString), "project")}
         |    </div>
         |  </div>
         |</div>""".stripMargin,
      Vector("reader", "contributor", "project_manager")
    )

  private def _analysis_entry_tile(href: Option[String], title: String, note: String, count: String, kind: String): String = {
    val body =
      s"""  <span class="bok-analysis-entry-kicker">${_html_escape(count)}</span>
         |  <strong>${_html_escape(title)}</strong>
         |  <em>${_html_escape(note)}</em>""".stripMargin
    href match {
      case Some(value) =>
        s"""<a class="bok-analysis-entry-tile bok-analysis-entry-${_html_escape(kind)}" href="${_html_escape(value)}">
           |${body}
           |</a>""".stripMargin
      case None =>
        s"""<div class="bok-analysis-entry-tile bok-analysis-entry-${_html_escape(kind)} bok-analysis-entry-static">
           |${body}
           |</div>""".stripMargin
    }
  }

  private def _purpose_card_body(locale: String, purpose: BokPurpose): String =
    if (purpose.isEmpty)
      ""
    else
      s"""${purpose.vision.map(x => s"""<div class="bok-purpose-vision-panel"><span class="bok-purpose-node-label">V</span><span class="bok-purpose-vision-copy"><small>${_html_escape(_ui(locale, "dashboard.purpose.vision"))}</small><strong>${_html_escape(x)}</strong></span></div>""").getOrElse("")}
         |${_purpose_tree(locale, purpose)}""".stripMargin

  private def _purpose_tree(locale: String, purpose: BokPurpose): String =
    if (purpose.goals.nonEmpty)
      _purpose_goal_tree(locale, purpose.goals)
    else
      _flat_purpose_body(locale, purpose.flatGoals, purpose.flatSubgoals)

  private def _flat_purpose_body(locale: String, goals: Vector[String], subgoals: Vector[String]): String =
    if (goals.isEmpty && subgoals.isEmpty)
      ""
    else
      Vector(
        if (goals.nonEmpty) Some(_purpose_flat_list(locale, "dashboard.purpose.goals", goals)) else None,
        if (subgoals.nonEmpty) Some(_purpose_flat_list(locale, "dashboard.purpose.subgoals", subgoals)) else None
      ).flatten.mkString("""<div class="bok-purpose-flat">""", "", "</div>")

  private def _purpose_flat_list(locale: String, labelkey: String, values: Vector[String]): String =
    values.take(5).zipWithIndex.map {
      case (value, i) =>
        s"""<li><span class="bok-purpose-node-label">${i + 1}</span><span>${_html_escape(value)}</span></li>"""
    }.mkString(s"""<div class="bok-purpose-flat-group"><strong>${_html_escape(_ui(locale, labelkey))}</strong><ul class="bok-purpose-flat-list">""", "", "</ul></div>")

  private def _purpose_goal_tree(locale: String, goals: Vector[BokGoal]): String = {
    val shown = goals.filterNot(_.isEmpty).take(3)
    if (shown.isEmpty)
      ""
    else {
      val body = shown.zipWithIndex.map {
        case (goal, i) =>
          val subgoalhtml =
            if (goal.subgoals.isEmpty)
              ""
            else
              goal.subgoals.take(3).zipWithIndex.map {
                case (subgoal, j) =>
                  val goallabel = s"G${i + 1}"
                  s"""<li><span class="bok-purpose-node-label">S${j + 1}</span><span class="bok-purpose-subgoal-copy"><small>${_html_escape(_uif(locale, "dashboard.purpose.supports.goal", goallabel))}</small><span>${_html_escape(subgoal)}</span></span></li>"""
              }.mkString("""<ul class="bok-purpose-subgoals">""", "", "</ul>")
          val moresubgoals =
            if (goal.subgoals.size > 3)
              s"""<div class="bok-more">${_html_escape(_uif(locale, "dashboard.more", goal.subgoals.size - 3))}</div>"""
            else
              ""
          s"""<div class="bok-purpose-goal">
             |  <div class="bok-purpose-goal-head"><span class="bok-purpose-node-label">G${i + 1}</span><strong>${_html_escape(goal.title)}</strong></div>
             |  ${subgoalhtml}${moresubgoals}
             |</div>""".stripMargin
      }.mkString
      val moregoals =
        if (goals.filterNot(_.isEmpty).size > 3)
          s"""<div class="bok-more">${_html_escape(_uif(locale, "dashboard.more", goals.filterNot(_.isEmpty).size - 3))}</div>"""
        else
          ""
      s"""<div class="bok-purpose-tree"><div class="bok-purpose-tree-label"><span class="bok-purpose-node-label">G</span><strong>${_html_escape(_ui(locale, "dashboard.purpose.goals"))}</strong></div>${body}${moregoals}</div>"""
    }
  }

  private def _home_readiness_body(locale: String, config: BuildConfig, dashboard: Option[BokDashboard]): String =
    _definition_list(Vector(
      _ui(locale, "dashboard.readiness.strategy") -> config.strategy,
      _ui(locale, "dashboard.readiness.scope") -> config.siteOutputScopePolicy,
      _ui(locale, "dashboard.readiness.metadata") -> dashboard.map(_ => _ui(locale, "dashboard.status.available")).getOrElse(_ui(locale, "dashboard.status.missing")),
      _ui(locale, "dashboard.readiness.issue.count") -> _ui(locale, "dashboard.status.zero.known")
    ))

  private def _category_readiness_body(locale: String, dashboard: Option[DashboardCategory]): String =
    _definition_list(Vector(
      _ui(locale, "dashboard.readiness.status") -> _ui(locale, "dashboard.status.active"),
      _ui(locale, "dashboard.readiness.metadata") -> dashboard.map(_ => _ui(locale, "dashboard.status.available")).getOrElse(_ui(locale, "dashboard.status.missing")),
      _ui(locale, "dashboard.readiness.freshness") -> dashboard.flatMap(_.increments.buckets.lastOption.map(_.label)).getOrElse(_ui(locale, "dashboard.activity.none")),
      _ui(locale, "dashboard.readiness.issue.count") -> _ui(locale, "dashboard.status.zero.known")
    ))

  private def _definition_list(items: Vector[(String, String)]): String =
    items.map {
      case (label, value) =>
        s"""<div class="bok-definition-row"><span>${_html_escape(label)}</span><strong>${_html_escape(value)}</strong></div>"""
    }.mkString("""<div class="bok-definition-list">""", "", "</div>")

  private def _quality_alerts_body(locale: String, metadataavailable: Boolean): String = {
    val items =
      if (metadataavailable)
        Vector(_ui(locale, "dashboard.quality.no.critical"), _ui(locale, "dashboard.quality.diagnostics.available"))
      else
        Vector(_ui(locale, "dashboard.quality.metadata.missing"), _ui(locale, "dashboard.quality.refresh"))
    items.take(5).map(x => s"""<li class="list-group-item"><span class="badge bok-badge-info">info</span>${_html_escape(x)}</li>""").
      mkString("""<ul class="list-group bok-alert-list">""", "", "</ul>")
  }

  private def _category_matrix_body(config: BuildConfig, locale: String, dashboard: BokDashboard, prefix: String = ""): String = {
    val articlecounts = _category_contents(config.sourcepath).map(x => x.slug -> x.articles.size).toMap
    val cards =
      if (dashboard.categories.isEmpty)
        s"""<p class="bok-card-muted">${_html_escape(_ui(locale, "dashboard.category.metadata.empty"))}</p>"""
      else
        dashboard.categories.sortBy(_.name).map { category =>
          val freshness = category.increments.buckets.lastOption.map(_.label).getOrElse("-")
          val rdfitem = _category_rdf_metric(locale, category, prefix)
          val articlecount = articlecounts.getOrElse(category.name, category.counts.articleCount)
          s"""<div class="bok-category-summary-card">
             |  <a class="bok-category-summary-title" href="${_html_escape(prefix)}${_html_escape(category.name)}/index.html">${_html_escape(category.title)}</a>
             |  <span class="bok-category-summary-freshness">${_html_escape(_ui(locale, "dashboard.readiness.freshness"))}: ${_html_escape(freshness)}</span>
             |  <span class="bok-category-summary-metrics">
             |    <span><b>${articlecount}</b>${_html_escape(_ui(locale, "dashboard.kpi.articles"))}</span>
             |    <span><b>${category.counts.glossaryTermCount}</b>${_html_escape(_ui(locale, "dashboard.kpi.terms"))}</span>
             |    ${rdfitem}
             |  </span>
             |</div>""".stripMargin
        }.mkString("\n")
    val counts = dashboard.counts.copy(articleCount = _source_article_count(config))
    s"""<div class="bok-category-summary-grid">
       |  ${cards}
       |</div>
       |${_dashboard_distribution_chart(locale, counts, _ui(locale, "dashboard.chart.item.distribution"))}""".stripMargin
  }

  private def _category_source_matrix_body(locale: String, categories: Vector[CategoryContent], prefix: String): String = {
    val cards =
      if (categories.isEmpty)
        s"""<p class="bok-card-muted">${_html_escape(_ui(locale, "dashboard.category.metadata.empty"))}</p>"""
      else
        categories.sortBy(_.slug).map { category =>
          s"""<div class="bok-category-summary-card">
             |  <a class="bok-category-summary-title" href="${_html_escape(prefix)}${_html_escape(category.slug)}/index.html">${_html_escape(category.title)}</a>
             |  <span class="bok-category-summary-freshness">${_html_escape(_ui(locale, "dashboard.readiness.freshness"))}: -</span>
             |  <span class="bok-category-summary-metrics">
             |    <span><b>${category.articles.size}</b>${_html_escape(_ui(locale, "dashboard.kpi.articles"))}</span>
             |    <span><b>${category.terms.size}</b>${_html_escape(_ui(locale, "dashboard.kpi.terms"))}</span>
             |    <span class="bok-category-rdf-value"><b>-</b>${_html_escape(_ui(locale, "dashboard.kpi.rdf"))}</span>
             |  </span>
             |</div>""".stripMargin
        }.mkString("\n")
    s"""<div class="bok-category-summary-grid">
       |  ${cards}
       |</div>""".stripMargin
  }

  private def _article_adjusted_increments(increments: DashboardIncrements, overcount: Int): DashboardIncrements =
    if (overcount <= 0)
      increments
    else {
      var remaining = overcount
      val buckets = increments.buckets.map { bucket =>
        val removed = math.min(remaining, bucket.articleCount)
        remaining = remaining - removed
        if (removed == 0)
          bucket
        else
          bucket.copy(
            count = math.max(0, bucket.count - removed),
            articleCount = math.max(0, bucket.articleCount - removed)
          )
      }
      increments.copy(buckets = buckets)
    }

  private def _recent_activity_body(locale: String, increments: DashboardIncrements, fallbackitems: Vector[DashboardRecentItem] = Vector.empty, includecategory: Boolean = false): String =
    if (fallbackitems.nonEmpty)
      _recent_activity_fallback_body(locale, fallbackitems, includecategory)
    else
      _recent_activity_buckets(increments) match {
        case Vector() =>
          s"""<p class="bok-card-muted">${_html_escape(_ui(locale, "dashboard.activity.empty"))}</p>"""
        case buckets =>
          s"""${buckets.map { bucket =>
            s"""<li class="list-group-item"><time datetime="${_html_escape(bucket.startDate)}">${_html_escape(bucket.label)}</time><span class="bok-activity-kind">${_html_escape(_recent_activity_bucket_kind(locale, bucket))}</span><span>${_html_escape(_uif(locale, "dashboard.activity.count", bucket.count.toString))}</span></li>"""
          }.mkString("""<ul class="list-group bok-activity-list">""", "", "</ul>")}"""
      }

  private def _recent_activity_fallback_body(locale: String, items: Vector[DashboardRecentItem], includecategory: Boolean): String =
    s"""${items.map { item =>
      val date = Instant.ofEpochMilli(item.modifiedatmillis).atZone(ZoneOffset.UTC).toLocalDate.toString
      val category = if (includecategory) s"""<span class="bok-activity-category">${_html_escape(item.category.getOrElse("-"))}</span>""" else ""
      s"""<li class="list-group-item"><time datetime="${_html_escape(date)}">${_html_escape(date)}</time><span class="bok-activity-kind">${_html_escape(_ui(locale, item.kindkey))}</span>${category}<a href="${_html_escape(item.href)}">${_html_escape(item.title)}</a></li>"""
    }.mkString(s"""<ul class="list-group bok-activity-list${if (includecategory) " bok-activity-list-with-category" else ""}">""", "", "</ul>")}"""

  private def _recent_activity_bucket_kind(locale: String, bucket: DashboardBucket): String = {
    val kinds = Vector(
      (bucket.articleCount > 0) -> _ui(locale, "dashboard.activity.kind.article"),
      (bucket.glossaryTermCount > 0) -> _ui(locale, "dashboard.activity.kind.term")
    ).collect { case (true, label) => label }
    if (kinds.isEmpty)
      _ui(locale, "dashboard.activity.kind.update")
    else
      kinds.mkString(" / ")
  }

  private def _recent_activity_buckets(increments: DashboardIncrements): Vector[DashboardBucket] = {
    val active = increments.buckets.filter(_.count > 0)
    val dated = active.flatMap(bucket => _dashboard_bucket_date(bucket).map(_ -> bucket))
    if (dated.nonEmpty) {
      val latestdate = dated.maxBy(_._1.toEpochDay)._1
      val cutoff = latestdate.minusMonths(1)
      dated.
        filter {
          case (date, _) => !date.isBefore(cutoff) && !date.isAfter(latestdate)
        }.
        sortBy(_._1.toEpochDay).
        map(_._2).
        takeRight(5).
        reverse
    } else {
      active.takeRight(5).reverse
    }
  }

  private def _dashboard_bucket_date(bucket: DashboardBucket): Option[LocalDate] =
    _parse_local_date(bucket.endDate).orElse(_parse_local_date(bucket.startDate))

  private def _parse_local_date(value: String): Option[LocalDate] =
    try {
      Some(LocalDate.parse(value))
    } catch {
      case NonFatal(_) => None
    }

  private def _home_recent_items(config: BuildConfig): Vector[DashboardRecentItem] = {
    val categories = _category_contents(config.sourcepath)
    val categorylabels = categories.map(x => x.slug -> x.title).toMap
    val articleitems = categories.flatMap { category =>
      val articles = category.articles.map { item =>
        DashboardRecentItem(s"${category.slug}/${item.href}", item.title, Some(category.title), "dashboard.activity.kind.article", item.modifiedAtMillis)
      }
      articles
    }
    val termitems = _recent_term_items_from_home(config, categories)
    val scenarioitems = _scenario_index(config).map(_.scenarios.map { item =>
      DashboardRecentItem(item.hrefFromHome, item.title, item.category.map(x => categorylabels.getOrElse(x, x)), "dashboard.activity.kind.scenario", _source_modified_at_millis(config, item.sourcepath))
    }).getOrElse(Vector.empty)
    val bibliographyitems = _bibliography_index(config).map(_.entries.map { item =>
      DashboardRecentItem(item.publicpath, item.title, item.category.map(x => categorylabels.getOrElse(x, x)), "dashboard.activity.kind.bibliography", _source_modified_at_millis(config, item.sourcepath))
    }).getOrElse(Vector.empty)
    val items = (articleitems ++ termitems ++ scenarioitems ++ bibliographyitems).groupBy(_.href).values.map(_.maxBy(_.modifiedatmillis)).toVector
    val dated = items.flatMap(item => _modified_date(item.modifiedatmillis).map(_ -> item))
    if (dated.nonEmpty) {
      val latestdate = dated.maxBy(_._1.toEpochDay)._1
      val cutoff = latestdate.minusMonths(1)
      dated.
        filter {
          case (date, _) => !date.isBefore(cutoff) && !date.isAfter(latestdate)
        }.
        sortBy(_._1.toEpochDay).
        map(_._2).
        takeRight(5).
        reverse
    } else {
      Vector.empty
    }
  }

  private def _category_recent_items(config: BuildConfig, category: CategoryContent): Vector[DashboardRecentItem] = {
    val articles = category.articles.map { item =>
      DashboardRecentItem(item.href, item.title, Some(category.title), "dashboard.activity.kind.article", item.modifiedAtMillis)
    }
    val terms = _recent_term_items_from_category(config, category)
    val scenarios = _scenario_index(config).map(_.scenarios.filter(_.category.contains(category.slug)).map { item =>
      DashboardRecentItem(item.hrefFromCategory, item.title, Some(category.title), "dashboard.activity.kind.scenario", _source_modified_at_millis(config, item.sourcepath))
    }).getOrElse(Vector.empty)
    val bibliographies = _bibliography_index(config).map(_.entries.filter(_.category.contains(category.slug)).map { item =>
      DashboardRecentItem("../" + item.publicpath, item.title, Some(category.title), "dashboard.activity.kind.bibliography", _source_modified_at_millis(config, item.sourcepath))
    }).getOrElse(Vector.empty)
    (articles ++ terms ++ scenarios ++ bibliographies).sortBy(-_.modifiedatmillis).take(5)
  }

  private def _recent_term_items_from_home(config: BuildConfig, categories: Vector[CategoryContent]): Vector[DashboardRecentItem] = {
    val categorylabels = categories.map(x => x.slug -> x.title).toMap
    val terms = _terms(config)
    if (terms.nonEmpty)
      terms.map { term =>
        DashboardRecentItem(
          term.termHubHrefFromHome,
          term.title,
          term.category.map(x => categorylabels.getOrElse(x, x)),
          "dashboard.activity.kind.term",
          _source_modified_at_millis(config, term.sourcepath)
        )
      }
    else
      Vector.empty
  }

  private def _recent_term_items_from_category(config: BuildConfig, category: CategoryContent): Vector[DashboardRecentItem] = {
    val terms = _terms(config).filter(_.category.contains(category.slug))
    if (terms.nonEmpty)
      terms.map { term =>
        DashboardRecentItem(
          term.termHubHrefFromCategory,
          term.title,
          Some(category.title),
          "dashboard.activity.kind.term",
          _source_modified_at_millis(config, term.sourcepath)
        )
      }
    else
      Vector.empty
  }

  private def _source_modified_at_millis(config: BuildConfig, sourcepath: String): Long = {
    val path = config.sourcepath.resolve(sourcepath)
    if (Files.isRegularFile(path))
      _modified_at_millis(path)
    else
      0L
  }

  private def _modified_date(millis: Long): Option[LocalDate] =
    if (millis <= 0L)
      None
    else
      Some(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate)

  private def _next_actions_body(locale: String, config: BuildConfig): String = {
    val project = if (config.project == _logical_cwd) "" else " <bok-root>"
    Vector(
      s"cozy bok build${project} --strategy preview",
      s"cozy bok preview${project}",
      s"cozy bok publish${project} --dry-run",
      _ui(locale, "dashboard.action.fix.diagnostics")
    ).map(x => s"<li><code>${_html_escape(x)}</code></li>").
      mkString("<ol class=\"bok-action-list\">", "", "</ol>")
  }

  private def _quick_links_body(locale: String, config: BuildConfig, prefix: String): String =
    Vector(
      _ui(locale, "glossary.title") -> s"${prefix}glossary/index.html",
      _ui(locale, "history.title") -> _history_href(config, prefix),
      _ui(locale, "manual.title") -> s"${prefix}manual/index.html"
    ).map {
      case (label, href) =>
        s"""<li class="list-group-item"><a href="${_html_escape(href)}">${_html_escape(label)}</a></li>"""
    }.mkString("""<ul class="list-group bok-quick-links-list">""", "", "</ul>")

  private def _page_map_body(items: Vector[CategoryPageItem], empty: String): String =
    if (items.isEmpty)
      s"""<p class="bok-card-muted">${_html_escape(empty)}</p>"""
    else {
      val shown = items.take(5).map { item =>
        s"""<li class="list-group-item"><a href="${_html_escape(item.href)}">${_html_escape(item.title)}</a><span>${_html_escape(item.brief)}</span></li>"""
      }.mkString
      val more = if (items.size > 5) s"""<li class="list-group-item bok-more">+${items.size - 5} more</li>""" else ""
      s"""<ul class="list-group bok-map-list">${shown}${more}</ul>"""
    }

  private def _related_knowledge_body(locale: String, config: BuildConfig, category: CategoryContent, rdf: Option[DashboardRdfSummary]): String = {
    val rdfitem =
      rdf.filter(_.tripleCount > 0).
        map(_ => s"""  <li class="list-group-item"><a href="../rdf/index.html?category=${_html_escape(_url_query_escape(category.slug))}">${_html_escape(_ui(locale, "rdf.graph.title"))}</a></li>""").
        getOrElse("")
    val scenarioitem =
      _scenario_index(config).map(_.scenarios.count(_.category.contains(category.slug))).filter(_ > 0).
        map(count => s"""  <li class="list-group-item"><a href="../scenarios/index.html?category=${_html_escape(_url_query_escape(category.slug))}">${_html_escape(_uif(locale, "scenario.category.link", count.toString))}</a></li>""").
        getOrElse("")
    val bibliographyitem =
      _bibliography_index(config).map(_.entries.count(_.category.contains(category.slug))).filter(_ > 0).
        map(count => s"""  <li class="list-group-item"><a href="../bibliography/index.html?category=${_html_escape(_url_query_escape(category.slug))}">${_html_escape(_uif(locale, "bibliography.category.link", count.toString))}</a></li>""").
        getOrElse("")
    val projectitem = {
      val count = _project_package_dirs(config.sourcepath).count { path =>
        val projects = config.sourcepath.resolve("projects").toAbsolutePath.normalize()
        val relative = projects.relativize(path.toAbsolutePath.normalize())
        relative.getNameCount >= 1 && relative.getName(0).toString == category.slug
      }
      if (count > 0)
        s"""  <li class="list-group-item"><a href="../projects/index.html?category=${_html_escape(_url_query_escape(category.slug))}">${_html_escape(_uif(locale, "project.category.link", count.toString))}</a></li>"""
      else
        ""
    }
    s"""<ul class="list-group bok-related-list">
       |  <li class="list-group-item"><a href="../glossary/index.html">${_html_escape(_ui(locale, "glossary.title"))}</a></li>
       |  <li class="list-group-item"><a href="../glossary/${_html_escape(category.slug)}/index.html">${_html_escape(_uif(locale, "dashboard.related.category.terms", category.title))}</a></li>
       |${rdfitem}
       |${scenarioitem}
       |${projectitem}
       |${bibliographyitem}
       |  <li class="list-group-item"><a href="${_html_escape(_history_href(config, "../"))}">${_html_escape(_ui(locale, "history.title"))}</a></li>
       |  <li class="list-group-item"><a href="../manual/index.html">${_html_escape(_ui(locale, "manual.title"))}</a></li>
       |</ul>""".stripMargin
  }

  private def _category_rdf_metric(locale: String, category: DashboardCategory, prefix: String = ""): String = {
    val value = category.rdf.map(_.tripleCount.toString).getOrElse("-")
    category.rdf.filter(_.tripleCount > 0).
      map(_ => s"""<a class="bok-category-rdf-link" href="${_html_escape(prefix)}rdf/index.html?category=${_html_escape(_url_query_escape(category.name))}"><b>${_html_escape(value)}</b>${_html_escape(_ui(locale, "dashboard.kpi.rdf"))}</a>""").
      getOrElse(s"""<span class="bok-category-rdf-value"><b>${_html_escape(value)}</b>${_html_escape(_ui(locale, "dashboard.kpi.rdf"))}</span>""")
  }

  private def _category_rdf_kpi_card(locale: String, category: CategoryContent, rdf: DashboardRdfSummary): String =
    if (rdf.tripleCount > 0)
      _kpi_card_link("col-6 col-md-3", _ui(locale, "dashboard.kpi.rdf"), rdf.tripleCount.toString, _ui(locale, "dashboard.kpi.rdf.note"), s"../rdf/index.html?category=${_url_query_escape(category.slug)}")
    else
      _kpi_card(locale, "col-6 col-md-3", _ui(locale, "dashboard.kpi.rdf"), rdf.tripleCount.toString, _ui(locale, "dashboard.kpi.rdf.note"))

  private def _dashboard_cards(counts: DashboardCounts, includecategories: Boolean): String = {
    val categorycard =
      if (includecategories)
        s"""  <div class="bok-metric-card">
           |    <div class="bok-metric-label">Categories</div>
           |    <div class="bok-metric-value">${counts.categoryCount}</div>
           |    <div class="bok-metric-note">SmartDox categories</div>
           |  </div>
           |""".stripMargin
      else
        ""
    s"""<div class="bok-dashboard-grid">
       |${categorycard}  <div class="bok-metric-card">
       |    <div class="bok-metric-label">Articles</div>
       |    <div class="bok-metric-value">${counts.articleCount}</div>
       |    <div class="bok-metric-note">Article / Blog pages</div>
       |  </div>
       |  <div class="bok-metric-card">
       |    <div class="bok-metric-label">Terms</div>
       |    <div class="bok-metric-value">${counts.glossaryTermCount}</div>
       |    <div class="bok-metric-note">Site glossary terms</div>
       |  </div>
       |  <div class="bok-metric-card">
       |    <div class="bok-metric-label">Total Items</div>
       |    <div class="bok-metric-value">${counts.totalItemCount}</div>
       |    <div class="bok-metric-note">Articles + terms</div>
       |  </div>
       |</div>""".stripMargin
  }

  private def _dashboard_distribution_chart(locale: String, counts: DashboardCounts, label: String): String = {
    val articlecount = counts.articleCount
    val termcount = counts.glossaryTermCount
    val total = math.max(1, articlecount + termcount)
    val articlewidth = _dashboard_bar_width(articlecount, total)
    val termwidth = _dashboard_bar_width(termcount, total)
    s"""<div class="bok-dashboard-chart" aria-label="${_html_escape(label)}" data-chart="distribution-ratio">
       |  <div class="bok-chart-row"><span>${_html_escape(_ui(locale, "dashboard.kpi.articles"))}</span><div><b style="width:${articlewidth}%"></b></div><em>${articlecount}</em></div>
       |  <div class="bok-chart-row"><span>${_html_escape(_ui(locale, "dashboard.kpi.terms"))}</span><div><b style="width:${termwidth}%"></b></div><em>${termcount}</em></div>
       |</div>""".stripMargin
  }

  private def _dashboard_rdf_cards(rdf: DashboardRdfSummary): String =
    s"""<div class="bok-dashboard-grid">
       |  <div class="bok-metric-card">
       |    <div class="bok-metric-label">RDF Resources</div>
       |    <div class="bok-metric-value">${rdf.resourceCount}</div>
       |    <div class="bok-metric-note">Site RDF resources</div>
       |  </div>
       |  <div class="bok-metric-card">
       |    <div class="bok-metric-label">RDF Triples</div>
       |    <div class="bok-metric-value">${rdf.tripleCount}</div>
       |    <div class="bok-metric-note">Generated site graph triples</div>
       |  </div>
       |  <div class="bok-metric-card">
       |    <div class="bok-metric-label">RDF Subjects</div>
       |    <div class="bok-metric-value">${rdf.subjectCount}</div>
       |    <div class="bok-metric-note">Distinct graph subjects</div>
       |  </div>
       |  <div class="bok-metric-card">
       |    <div class="bok-metric-label">RDF Predicates</div>
       |    <div class="bok-metric-value">${rdf.predicateCount}</div>
       |    <div class="bok-metric-note">Distinct graph predicates</div>
       |  </div>
       |</div>""".stripMargin

  private def _dashboard_increment_chart(locale: String, increments: DashboardIncrements, label: String): String =
    if (increments.buckets.isEmpty)
      s"""<p>${_html_escape(_ui(locale, "dashboard.increment.empty"))}</p>"""
    else if (!increments.buckets.forall(_.hasBreakdown))
      _dashboard_increment_total_chart(locale, increments, label)
    else {
      val cumulativearticles = increments.buckets.scanLeft(0)(_ + _.articleCount).tail
      val cumulativeterms = increments.buckets.scanLeft(0)(_ + _.glossaryTermCount).tail
      val points = increments.buckets.zip(cumulativearticles.zip(cumulativeterms))
      val max = math.max(1, (cumulativearticles ++ cumulativeterms).max)
      val pointcount = increments.buckets.length
      def x(index: Int): Double =
        if (pointcount <= 1) 50.0 else 8.0 + (84.0 * index.toDouble / (pointcount - 1).toDouble)
      def y(value: Int): Double =
        88.0 - (76.0 * value.toDouble / max.toDouble)
      def coordinates(values: Vector[Int]): String =
        values.zipWithIndex.map {
          case (value, index) => f"${x(index)}%.2f,${y(value)}%.2f"
        }.mkString(" ")
      val articlecoordinates = coordinates(cumulativearticles)
      val termcoordinates = coordinates(cumulativeterms)
      val articlemarkers = increments.buckets.zip(cumulativearticles).zipWithIndex.map {
        case ((bucket, value), index) =>
          val cx = x(index)
          val cy = y(value)
          val title = _uif(locale, "dashboard.chart.title.cumulative.articles", bucket.label, value, bucket.articleCount)
          f"""      <circle class="bok-cumulative-point-articles" cx="${cx}%.2f" cy="${cy}%.2f" r="2.8"><title>${_html_escape(title)}</title></circle>"""
      }.mkString("\n")
      val termmarkers = increments.buckets.zip(cumulativeterms).zipWithIndex.map {
        case ((bucket, value), index) =>
          val cx = x(index)
          val cy = y(value)
          val title = _uif(locale, "dashboard.chart.title.cumulative.terms", bucket.label, value, bucket.glossaryTermCount)
          f"""      <circle class="bok-cumulative-point-terms" cx="${cx}%.2f" cy="${cy}%.2f" r="2.8"><title>${_html_escape(title)}</title></circle>"""
      }.mkString("\n")
      val axis = points.map {
        case (bucket, (articlevalue, termvalue)) =>
          s"""    <span><time datetime="${_html_escape(bucket.startDate)}">${_html_escape(bucket.label)}</time><em>${_html_escape(_uif(locale, "dashboard.chart.axis.article.term", articlevalue, termvalue))}</em></span>"""
      }.mkString("\n")
      val range = s"${increments.buckets.head.startDate} - ${increments.buckets.last.endDate}"
      s"""<div class="bok-dashboard-chart" aria-label="${_html_escape(label)}" data-chart="cumulative-date" data-scale="${_html_escape(increments.scale)}">
         |  <div class="bok-cumulative-chart">
         |    <div class="bok-cumulative-chart-head">
         |      <span class="bok-cumulative-chart-title">${_html_escape(label)}</span>
         |      <span class="bok-cumulative-chart-range">${_html_escape(range)}</span>
         |      <span class="bok-cumulative-chart-scale">${_html_escape(increments.scale)}</span>
         |    </div>
         |    <svg class="bok-cumulative-chart-svg" viewBox="0 0 100 100" role="img" aria-label="${_html_escape(_uif(locale, "dashboard.chart.aria.cumulative", label))}">
         |      <line class="bok-cumulative-axis-x" x1="6" y1="88" x2="94" y2="88"></line>
         |      <line class="bok-cumulative-axis-y" x1="6" y1="12" x2="6" y2="88"></line>
         |      <polyline class="bok-cumulative-line bok-cumulative-line-articles" points="${articlecoordinates}"></polyline>
         |      <polyline class="bok-cumulative-line bok-cumulative-line-terms" points="${termcoordinates}"></polyline>
         |      <g class="bok-cumulative-points">
         |${articlemarkers}
         |${termmarkers}
         |      </g>
         |    </svg>
         |    <div class="bok-cumulative-legend">
         |      <span><i class="bok-cumulative-marker bok-cumulative-marker-articles"></i>${_html_escape(_ui(locale, "dashboard.kpi.articles"))}</span>
         |      <span><i class="bok-cumulative-marker bok-cumulative-marker-terms"></i>${_html_escape(_ui(locale, "dashboard.kpi.terms"))}</span>
         |    </div>
         |    <div class="bok-cumulative-axis">
         |${axis}
         |    </div>
         |  </div>
         |</div>""".stripMargin
    }

  private def _dashboard_increment_total_chart(locale: String, increments: DashboardIncrements, label: String): String = {
    val cumulativetotals = increments.buckets.scanLeft(0)(_ + _.count).tail
    val max = math.max(1, cumulativetotals.max)
    val pointcount = increments.buckets.length
    def x(index: Int): Double =
      if (pointcount <= 1) 50.0 else 8.0 + (84.0 * index.toDouble / (pointcount - 1).toDouble)
    def y(value: Int): Double =
      88.0 - (76.0 * value.toDouble / max.toDouble)
    val coordinates = cumulativetotals.zipWithIndex.map {
      case (value, index) => f"${x(index)}%.2f,${y(value)}%.2f"
    }.mkString(" ")
    val markers = increments.buckets.zip(cumulativetotals).zipWithIndex.map {
      case ((bucket, value), index) =>
        val cx = x(index)
        val cy = y(value)
        val title = _uif(locale, "dashboard.chart.title.cumulative.total", bucket.label, value, bucket.count)
        f"""      <circle class="bok-cumulative-point-total" cx="${cx}%.2f" cy="${cy}%.2f" r="2.8"><title>${_html_escape(title)}</title></circle>"""
    }.mkString("\n")
    val axis = increments.buckets.zip(cumulativetotals).map {
      case (bucket, value) =>
        s"""    <span><time datetime="${_html_escape(bucket.startDate)}">${_html_escape(bucket.label)}</time><em>${value}</em></span>"""
    }.mkString("\n")
    val range = s"${increments.buckets.head.startDate} - ${increments.buckets.last.endDate}"
    s"""<div class="bok-dashboard-chart" aria-label="${_html_escape(label)}" data-chart="cumulative-date" data-scale="${_html_escape(increments.scale)}">
       |  <div class="bok-cumulative-chart">
       |    <div class="bok-cumulative-chart-head">
       |      <span class="bok-cumulative-chart-title">${_html_escape(label)}</span>
       |      <span class="bok-cumulative-chart-range">${_html_escape(range)}</span>
       |      <span class="bok-cumulative-chart-scale">${_html_escape(increments.scale)}</span>
       |    </div>
       |    <svg class="bok-cumulative-chart-svg" viewBox="0 0 100 100" role="img" aria-label="${_html_escape(_uif(locale, "dashboard.chart.aria.cumulative", label))}">
       |      <line class="bok-cumulative-axis-x" x1="6" y1="88" x2="94" y2="88"></line>
       |      <line class="bok-cumulative-axis-y" x1="6" y1="12" x2="6" y2="88"></line>
       |      <polyline class="bok-cumulative-line bok-cumulative-line-total" points="${coordinates}"></polyline>
       |      <g class="bok-cumulative-points">
       |${markers}
       |      </g>
       |    </svg>
       |    <div class="bok-cumulative-legend">
       |      <span><i class="bok-cumulative-marker bok-cumulative-marker-total"></i>${_html_escape(_ui(locale, "dashboard.matrix.total"))}</span>
       |    </div>
       |    <div class="bok-cumulative-axis">
       |${axis}
       |    </div>
       |  </div>
       |</div>""".stripMargin
  }

  private def _document_fragment_index(config: BuildConfig): Option[DocumentFragmentIndex] = {
    val path = config.doxsitePath.resolve("metadata/documents/fragments.json")
    if (!Files.isRegularFile(path))
      None
    else
      parser.parse(Files.readString(path, StandardCharsets.UTF_8)).toOption.flatMap(_.as[DocumentFragmentIndex].toOption)
  }

  private def _category_term_page_items(config: BuildConfig, category: String): Vector[CategoryPageItem] =
    _terms(config).filter(_.category.contains(category)).map { term =>
      CategoryPageItem(
        term.termHubHrefFromCategory,
        term.title,
        term.summary.getOrElse(""),
        0L,
        term.reading
      )
    }

  private def _dashboard(config: BuildConfig): Option[BokDashboard] = {
    val path = config.doxsitePath.resolve("metadata/dashboard/site.json")
    if (!Files.isRegularFile(path))
      None
    else {
      val content = Files.readString(path, StandardCharsets.UTF_8)
      parser.parse(content) match {
        case Left(_) => None
        case Right(json) => json.as[BokDashboard] match {
          case Left(_) => None
          case Right(dashboard) => Some(dashboard)
        }
      }
    }
  }

  private def _history_href(config: BuildConfig, prefix: String): String =
    _latest_history_year_page(config.websitePath.resolve("history")) match {
      case Some(file) => s"${prefix}history/${file}"
      case None => s"${prefix}history/index.html"
    }

  private def _latest_history_year_page(dir: Path): Option[String] =
    if (!Files.isDirectory(dir))
      None
    else {
      val stream = Files.list(dir)
      try {
        stream.iterator.asScala.toVector.
          filter(Files.isRegularFile(_)).
          map(_.getFileName.toString).
          collect { case name if name.matches("""\d{4}\.html""") => name }.
          sortBy(identity).
          lastOption
      } finally {
        stream.close()
      }
    }

  private def _home_category_list(config: BuildConfig, locale: String): String = {
    val categories = _regular_category_summaries(config.sourcepath)
    if (categories.isEmpty)
      s"<p>${_html_escape(_ui(locale, "home.categories.empty"))}</p>"
    else
      categories.map { category =>
        val htmlclass = if (category.slug == "glossary") """ class="glossary"""" else ""
        s"""<li><a${htmlclass} href="${_html_escape(category.slug)}/index.html">${_html_escape(category.title)}</a>: ${_html_escape(category.description)}</li>"""
      }.mkString("<ul>\n", "\n", "\n</ul>")
  }

  private final case class CategorySummary(slug: String, title: String, description: String, purpose: BokPurpose)

  private def _regular_category_summaries(source: Path): Vector[CategorySummary] =
    _category_summaries(source).filterNot(x => _is_special_category(x.slug))

  private def _is_special_category(slug: String): Boolean =
    slug == "glossary" || slug == "history"

  private def _category_summaries(source: Path): Vector[CategorySummary] =
    if (!Files.isDirectory(source))
      Vector.empty
    else {
      val stream = Files.list(source)
      try {
        stream.iterator.asScala.toVector.filter(Files.isDirectory(_)).flatMap { dir =>
          val category = dir.resolve("category.yaml")
          if (Files.isRegularFile(category))
            Some(CategorySummary(
              source.relativize(dir).toString,
              _yaml_value(category, "title").getOrElse(dir.getFileName.toString),
              _yaml_description(category).getOrElse(""),
              _yaml_purpose(category)
            ))
          else
            None
        }.sortBy(_.slug)
      } finally {
        stream.close()
      }
    }

  private def _category_contents(source: Path): Vector[CategoryContent] =
    _regular_category_summaries(source).map { summary =>
      val dir = source.resolve(summary.slug)
      CategoryContent(
        summary.slug,
        summary.title,
        summary.description,
        summary.purpose,
        _article_page_items(dir),
        _glossary_page_items(source.resolve("glossary").resolve(summary.slug), summary.slug)
      )
    }

  private def _source_article_count(config: BuildConfig): Int =
    _category_contents(config.sourcepath).map(_.articles.size).sum

  private def _article_page_items(dir: Path): Vector[CategoryPageItem] =
    if (!Files.isDirectory(dir))
      Vector.empty
    else {
      val stream = Files.walk(dir)
      try {
        stream.iterator.asScala.toVector.
          filter(Files.isRegularFile(_)).
          filter(_is_source_document).
          filterNot(_is_index_source_document).
          filterNot(x => dir.relativize(x).toString.replace(java.io.File.separatorChar, '/').startsWith("glossary/")).
          map { file =>
            val rel = dir.relativize(file).toString.replace(java.io.File.separatorChar, '/')
            CategoryPageItem(
              _source_document_html_href(rel),
              _dox_title(file),
              _dox_brief(file),
              _modified_at_millis(file)
            )
          }.sortBy(_.href)
      } finally {
        stream.close()
      }
    }

  private def _glossary_page_items(dir: Path, category: String): Vector[CategoryPageItem] =
    if (!Files.isDirectory(dir))
      Vector.empty
    else {
      val stream = Files.walk(dir)
      try {
        stream.iterator.asScala.toVector.
          filter(Files.isRegularFile(_)).
          filter(_is_source_document).
          filterNot(_is_index_source_document).
          map { file =>
            val rel = dir.relativize(file).toString.replace(java.io.File.separatorChar, '/')
            val href = s"../glossary/${category}/${_source_document_html_href(rel)}"
            val content = Files.readString(file, StandardCharsets.UTF_8)
            CategoryPageItem(
              href,
              _dox_title(file),
              _dox_brief(file),
              _modified_at_millis(file),
              _dox_reading(file)
            )
          }.sortBy(_.href)
      } finally {
        stream.close()
      }
    }

  private def _dox_title(file: Path): String = {
    val content = Files.readString(file, StandardCharsets.UTF_8)
    val metadata = _dox_metadata(file)
    _effective_headline(metadata, "en").
      orElse(metadata.flatMap(_.getTitleStringDefault).filter(_.nonEmpty)).
      getOrElse(content.linesIterator.map(_.trim).find(_.nonEmpty).getOrElse(_titleize(_source_document_stem(file.getFileName.toString))))
  }

  private def _dox_brief(file: Path): String = {
    val metadata = _dox_metadata(file)
    _dox_metadata_property_string(metadata, Vector("brief", "summary", "description")).
      orElse(_markdown_front_matter_value(file, Vector("brief", "summary", "description"))).
      orElse(_effective_brief(metadata, "en")).
      getOrElse(_dox_brief_fallback(Files.readString(file, StandardCharsets.UTF_8)))
  }

  private def _dox_brief_fallback(content: String): String = {
    val lines = content.linesIterator.toVector
    val sectionnames = Set("## BRIEF", "## SUMMARY", "## DESCRIPTION")
    lines.zipWithIndex.collectFirst {
      case (line, i) if sectionnames.contains(line.trim) =>
        lines.drop(i + 1).map(_.trim).find(_.nonEmpty).getOrElse("")
    }.filter(_.nonEmpty).getOrElse("Category entry.")
  }

  private def _dox_metadata(file: Option[Path]): Option[DocumentMetaData] =
    file.flatMap(_dox_metadata)

  private def _dox_metadata(file: Path): Option[DocumentMetaData] =
    if (Files.isRegularFile(file)) {
      val content = Files.readString(file, StandardCharsets.UTF_8)
      val filename = file.getFileName.toString
      val suffix = filename.lastIndexOf('.') match {
        case n if n >= 0 => filename.substring(n + 1).toLowerCase(Locale.ROOT)
        case _ => ""
      }
      val config = suffix match {
        case "md" | "markdown" => Dox2Parser.Config.markdown
        case _ => Dox2Parser.Config.default
      }
      val dox = Dox2Parser.parseWithFilename(config, file.toString, content)
      _dox_metadata_from_dox(dox)
    } else {
      None
    }

  private def _dox_metadata_from_dox(dox: Dox): Option[DocumentMetaData] =
    Dox.getMetadata(dox).flatMap(_.toOption).orElse {
      dox.elements.toStream.flatMap(_dox_metadata_from_dox).headOption
    }

  private def _effective_headline(metadata: Option[DocumentMetaData], locale: String): Option[String] =
    metadata.flatMap(_.getEffectiveHeadlineString(_to_locale(locale))).filter(_.nonEmpty)

  private def _effective_brief(metadata: Option[DocumentMetaData], locale: String): Option[String] =
    metadata.flatMap(_.getEffectiveBriefString(_to_locale(locale))).filter(_.nonEmpty)

  private def _dox_reading(file: Path): Option[String] = {
    val content = Files.readString(file, StandardCharsets.UTF_8)
    _dox_metadata_property_string(_dox_metadata(file), Vector("reading", "yomi", "読み")).
      orElse(_markdown_front_matter_value(file, Vector("reading", "yomi", "読み"))).
      orElse(_dox_head_value(content, Vector("reading", "yomi", "読み")))
  }

  private def _markdown_front_matter_value(file: Path, keys: Vector[String]): Option[String] =
    if (!_is_markdown_source_document(file))
      None
    else {
      val lines = Files.readAllLines(file, StandardCharsets.UTF_8).asScala.toVector
      if (!lines.headOption.exists(_.trim == "---"))
        None
      else {
        val keyset = keys.map(_.toLowerCase(Locale.ROOT)).toSet
        lines.zipWithIndex.drop(1).find(_._1.trim == "---").flatMap { case (_, end) =>
          lines.slice(1, end).collectFirst {
            case line if line.contains(":") && keyset.contains(line.takeWhile(_ != ':').trim.toLowerCase(Locale.ROOT)) =>
              _unquote(line.dropWhile(_ != ':').drop(1).trim)
          }.filter(_.nonEmpty)
        }
      }
    }

  private def _dox_metadata_property_string(metadata: Option[DocumentMetaData], keys: Vector[String]): Option[String] =
    metadata.flatMap(_.properties).flatMap { hocon =>
      keys.toStream.flatMap { key =>
        try {
          if (hocon.hasPath(key))
            Some(hocon.getString(key)).filter(_.nonEmpty)
          else
            None
        } catch {
          case NonFatal(_) => None
        }
      }.headOption
    }

  private def _dox_head_value(content: String, keys: Vector[String]): Option[String] = {
    val keyset = keys.toSet
    val lines = content.linesIterator.toVector
    val headindex = lines.indexWhere(_.trim == "# HEAD")
    if (headindex < 0)
      None
    else {
      val headlines = lines.drop(headindex + 1).takeWhile { line =>
        val s = line.trim
        !s.startsWith("# ") || s.startsWith("## ")
      }
      headlines.collectFirst {
        case line if _head_property_key(line).exists(keyset.contains) =>
          _head_property_value(line)
      }.flatten
    }
  }

  private def _head_property_key(line: String): Option[String] =
    line.trim.indexOf('=') match {
      case n if n > 0 => Some(line.trim.substring(0, n).trim)
      case _ => None
    }

  private def _head_property_value(line: String): Option[String] =
    line.trim.indexOf('=') match {
      case n if n >= 0 => Some(line.trim.substring(n + 1).trim).filter(_.nonEmpty)
      case _ => None
    }

  private def _modified_at_millis(file: Path): Long =
    Files.getLastModifiedTime(file).toMillis

  private def _yaml_value(file: Path, key: String): Option[String] =
    Files.readAllLines(file, StandardCharsets.UTF_8).asScala.collectFirst {
      case line if line.trim.startsWith(s"${key}:") =>
        _unquote(line.trim.substring(key.length + 1).trim)
    }.filter(_.nonEmpty)

  private def _yaml_description(file: Path): Option[String] = {
    val lines = Files.readAllLines(file, StandardCharsets.UTF_8).asScala.toVector
    _yaml_value(file, "description").orElse {
      lines.sliding(2).collectFirst {
        case Vector(a, b) if a.trim == "description:" && b.trim.startsWith("ja:") =>
          _unquote(b.trim.substring(3).trim)
      }
    }.orElse {
      lines.sliding(3).collectFirst {
        case Vector(a, _, c) if a.trim == "description:" && c.trim.startsWith("ja:") =>
          _unquote(c.trim.substring(3).trim)
      }
    }
  }

  private def _yaml_purpose(file: Path): BokPurpose =
    _purpose_from_parts(
      _yaml_value(file, "vision"),
      _first_non_empty(_yaml_goal_tree(file, "goals"), _yaml_goal_tree(file, "goal_tree")),
      _yaml_list(file, "goals"),
      _yaml_list(file, "subgoals")
    )

  private def _yaml_goal_tree(file: Path, key: String): Vector[BokGoal] = {
    val lines = Files.readAllLines(file, StandardCharsets.UTF_8).asScala.toVector
    lines.zipWithIndex.collectFirst {
      case (line, i) if line.trim == s"${key}:" =>
        val baseindent = line.takeWhile(_.isWhitespace).length
        val block = lines.drop(i + 1).takeWhile { raw =>
          raw.trim.isEmpty || raw.takeWhile(_.isWhitespace).length > baseindent
        }
        _parse_yaml_goal_block(block)
    }.getOrElse(Vector.empty)
  }

  private def _parse_yaml_goal_block(lines: Vector[String]): Vector[BokGoal] = {
    var goals = Vector.empty[BokGoal]
    var current: Option[BokGoal] = None
    var subcollecting = false
    var subindent = 0

    def flush(): Unit = {
      current.filterNot(_.isEmpty).foreach(x => goals = goals :+ x)
      current = None
      subcollecting = false
    }

    lines.foreach { raw =>
      val trimmed = raw.trim
      val indent = raw.takeWhile(_.isWhitespace).length
      if (trimmed.nonEmpty) {
        if (subcollecting && indent <= subindent && !trimmed.startsWith("-")) {
          subcollecting = false
        }
        if (trimmed.startsWith("- ")) {
          val rest = trimmed.drop(2).trim
          if (subcollecting) {
            val value = _unquote(rest)
            current = current.map(g => g.copy(subgoals = g.subgoals :+ value))
          } else {
            flush()
            _parse_key_value(rest) match {
              case Some((k, v)) if k == "title" || k == "goal" || k == "name" =>
                current = Some(BokGoal(v, Vector.empty))
              case _ =>
                current = Some(BokGoal(_unquote(rest), Vector.empty))
            }
          }
        } else if (trimmed == "subgoals:") {
          subcollecting = true
          subindent = indent
        } else _parse_key_value(trimmed) match {
          case Some((k, v)) if k == "title" || k == "goal" || k == "name" =>
            current = Some(current.getOrElse(BokGoal("", Vector.empty)).copy(title = v))
          case Some((k, v)) if k == "subgoals" =>
            current = current.map(g => g.copy(subgoals = g.subgoals ++ _parse_inline_list(v)))
          case _ =>
        }
      }
    }
    flush()
    goals.filterNot(_.isEmpty)
  }

  private def _yaml_list(file: Path, key: String): Vector[String] = {
    val lines = Files.readAllLines(file, StandardCharsets.UTF_8).asScala.toVector
    _yaml_value(file, key).map(_parse_inline_list).filter(_.nonEmpty).getOrElse {
      lines.zipWithIndex.collectFirst {
        case (line, i) if line.trim == s"${key}:" =>
          val baseindent = line.takeWhile(_.isWhitespace).length
          lines.drop(i + 1).takeWhile { raw =>
            val trimmed = raw.trim
            trimmed.isEmpty || raw.takeWhile(_.isWhitespace).length > baseindent
          }.map(_.trim).collect {
            case item if item.startsWith("-") => _unquote(item.drop(1).trim)
          }.filter(_.nonEmpty)
      }.getOrElse(Vector.empty)
    }
  }

  private def _html_escape(value: String): String =
    value.flatMap {
      case '&' => "&amp;"
      case '<' => "&lt;"
      case '>' => "&gt;"
      case '"' => "&quot;"
      case '\'' => "&#39;"
      case c => c.toString
    }

  private def _javascript_string(value: String): String =
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

  private def _url_query_escape(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name)

  private def _delete_directory(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try {
        stream.iterator.asScala.toVector.reverse.foreach(Files.deleteIfExists)
      } finally {
        stream.close()
      }
    }

  private def _write(path: Path, content: String, policy: ProjectFilePolicy): Unit =
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

  private def _write_text(path: Path, content: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, content, StandardCharsets.UTF_8)
  }

  private def _read_text(path: Path): String =
    Files.readString(path, StandardCharsets.UTF_8)

  private def _write_default_ui_bundle(path: Path, policy: ProjectFilePolicy): Unit =
    policy match {
      case ProjectFilePolicy.Skip =>
        Unit
      case ProjectFilePolicy.Overwrite =>
        _write_default_ui_bundle(path)
      case ProjectFilePolicy.Default =>
        if (!Files.exists(path))
          _write_default_ui_bundle(path)
    }

  private def _write_default_ui_bundle(path: Path): Unit = {
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

  private def _zip_text(out: ZipOutputStream, name: String, content: String): Unit = {
    _zip_bytes(out, name, content.getBytes(StandardCharsets.UTF_8))
  }

  private def _zip_bytes(out: ZipOutputStream, name: String, content: Array[Byte]): Unit = {
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

  private def _resource_text(name: String): Option[String] =
    _resource_bytes(name).map(x => new String(x, StandardCharsets.UTF_8))

  private def _resource_bytes(name: String): Option[Array[Byte]] =
    Option(getClass.getClassLoader.getResourceAsStream(name)).map { in =>
      try {
        in.readAllBytes()
      } finally {
        in.close()
      }
    }

  private def _default_ui_layout(): String =
    """<!doctype html>
      |<html lang="{{site.keys.lang}}">
      |<head>
      |  <meta charset="utf-8">
      |  <meta name="viewport" content="width=device-width, initial-scale=1">
      |  <title>{{page.title}} - {{site.title}}</title>
      |  <link rel="stylesheet" href="{{uiRootPath}}/css/bootstrap-grid.min.css">
      |  <link rel="stylesheet" href="{{uiRootPath}}/css/site.css">
      |  <link rel="stylesheet" href="{{uiRootPath}}/css/cozy-bok-dashboard.css">
      |</head>
      |<body class="article">
      |  {{> header-content}}
      |  <div class="body">
      |{{> nav}}
      |    <main class="article">
      |      <div class="toolbar" role="navigation">
      |        <button class="nav-toggle"></button>
      |        <a href="{{siteRootPath}}/index.html" class="home-link{{#if page.home}} is-current{{/if}}"></a>
      |        <nav class="breadcrumbs" aria-label="breadcrumbs">
      |          <ul>
      |            <li><a href="{{siteRootPath}}/index.html">{{site.title}}</a></li>
      |            <li>{{page.title}}</li>
      |          </ul>
      |        </nav>
      |      </div>
      |      <div class="content">
      |        <aside class="toc sidebar" data-title="Contents" data-levels="2">
      |          <div class="toc-menu"></div>
      |        </aside>
      |        <article class="doc">
      |          <h1 class="page">{{page.title}}</h1>
      |          {{{page.contents}}}
      |        </article>
      |      </div>
      |    </main>
      |  </div>
      |  {{> footer-content}}
      |  <script src="{{uiRootPath}}/js/site.js"></script>
      |</body>
      |</html>
      |""".stripMargin

  private def _default_ui_nav(): String =
    """    <div class="nav-container"{{#if page.component}} data-component="{{page.component.name}}" data-version="{{page.version}}"{{/if}}>
      |      <aside class="nav">
      |        <div class="panels">
      |{{> nav-menu}}
      |        </div>
      |      </aside>
      |    </div>
      |""".stripMargin

  private def _default_ui_nav_menu(): String =
    """{{#with page.navigation}}
      |          <div class="nav-panel-menu is-active" data-panel="menu">
      |            <nav class="nav-menu">
      |              <button class="nav-menu-toggle" aria-label="Toggle expand/collapse all" style="display: none"></button>
      |              {{#with @root.page.componentVersion}}
      |              <h3 class="title"><a href="{{{relativize ./url}}}">{{./title}}</a></h3>
      |              {{/with}}
      |{{> nav-tree navigation=this}}
      |            </nav>
      |          </div>
      |{{/with}}
      |""".stripMargin

  private def _default_ui_nav_tree(): String =
    """{{#if navigation.length}}
      |              <ul class="nav-list">
      |                {{#each navigation}}
      |                <li class="nav-item{{#if (eq ./url @root.page.url)}} is-current-page{{/if}}" data-depth="{{or ../level 0}}">
      |                  {{#if ./content}}
      |                  {{#if ./items.length}}
      |                  <button class="nav-item-toggle"></button>
      |                  {{/if}}
      |                  {{#if ./url}}
      |                  <a class="nav-link" href="
      |                    {{~#if (eq ./urlType 'internal')}}{{{relativize ./url}}}
      |                    {{~else}}{{{./url}}}{{~/if}}">{{{./content}}}</a>
      |                  {{else}}
      |                  <span class="nav-text">{{{./content}}}</span>
      |                  {{/if}}
      |                  {{/if}}
      |{{> nav-tree navigation=./items level=(increment ../level)}}
      |                </li>
      |                {{/each}}
      |              </ul>
      |{{/if}}
      |""".stripMargin

  private def _default_ui_header(): String =
    """<header class="header">
      |  <nav class="navbar">
      |    <div class="navbar-brand">
      |      <a class="navbar-item" href="{{{or site.url siteRootPath}}}/">{{site.title}}</a>
      |      <button class="navbar-burger" aria-controls="topbar-nav" aria-expanded="false" aria-label="Toggle main menu">
      |        <span></span>
      |        <span></span>
      |        <span></span>
      |      </button>
      |    </div>
      |    <div id="topbar-nav" class="navbar-menu">
      |      <div class="navbar-end">
      |        <a class="navbar-item" href="{{siteRootPath}}/index.html">Home</a>
      |        <div class="navbar-item has-dropdown is-hoverable navbar-bok-nav navbar-bok-dropdown" aria-label="BoK">
      |          <a class="navbar-link navbar-bok-toggle" href="#">BoK</a>
      |          <div class="navbar-dropdown navbar-bok-menu">
      |            <a class="navbar-item navbar-dropdown-item" href="{{siteRootPath}}/glossary/index.html">Glossary</a>
      |            <a class="navbar-item navbar-dropdown-item" href="{{siteRootPath}}/history/index.html">History</a>
      |            <a class="navbar-item navbar-dropdown-item" href="{{siteRootPath}}/manual/index.html">BoK Manual</a>
      |          </div>
      |        </div>
      |      </div>
      |    </div>
      |  </nav>
      |</header>
      |""".stripMargin

  private def _default_ui_header(config: BuildConfig): String = {
    val locale = config.defaultLocale
    val prefix = "{{siteRootPath}}/"
    s"""<header class="header">
       |  <nav class="navbar">
       |    <div class="navbar-brand">
       |      <a class="navbar-item" href="{{siteRootPath}}/index.html">{{site.title}}</a>
       |      <button class="navbar-burger" aria-controls="topbar-nav" aria-expanded="false" aria-label="Toggle main menu">
       |        <span></span>
       |        <span></span>
       |        <span></span>
       |      </button>
       |    </div>
       |    <div id="topbar-nav" class="navbar-menu">
       |      <div class="navbar-end">
       |        <a class="navbar-item" href="{{siteRootPath}}/index.html">${_html_escape(_ui(locale, "nav.home"))}</a>
       |        ${_bok_nav_menu(config, locale, prefix)}
       |        ${_category_nav_menu(locale, _regular_category_summaries(config.sourcepath), prefix)}
       |      </div>
       |    </div>
       |  </nav>
       |</header>
       |""".stripMargin
  }

  private def _default_ui_eq_helper(): String =
    """'use strict'
      |
      |module.exports = (a, b) => a === b
      |""".stripMargin

  private def _default_ui_increment_helper(): String =
    """'use strict'
      |
      |module.exports = (value) => (value || 0) + 1
      |""".stripMargin

  private def _default_ui_or_helper(): String =
    """'use strict'
      |
      |module.exports = (...args) => {
      |  const numArgs = args.length
      |  if (numArgs === 3) return args[0] || args[1]
      |  if (numArgs < 3) throw new Error('{{or}} helper expects at least 2 arguments')
      |  args.pop()
      |  return args.some((it) => it)
      |}
      |""".stripMargin

  private def _default_ui_relativize_helper(): String =
    """'use strict'
      |
      |const { posix: path } = require('path')
      |
      |module.exports = (to, from, ctx) => {
      |  if (!to) return '#'
      |  if (to.charAt() !== '/') return to
      |  if (!ctx) from = (ctx = from).data.root.page.url
      |  if (!from) return (ctx.data.root.site.path || '') + to
      |  let hash = ''
      |  const hashIdx = to.indexOf('#')
      |  if (~hashIdx) {
      |    hash = to.slice(hashIdx)
      |    to = to.slice(0, hashIdx)
      |  }
      |  if (to === from) return hash || (isDir(to) ? './' : path.basename(to))
      |  const rel = path.relative(path.dirname(from + '.'), to)
      |  return rel ? (isDir(to) ? rel + '/' : rel) + hash : (isDir(to) ? './' : '../' + path.basename(to)) + hash
      |}
      |
      |function isDir (str) {
      |  return str.charAt(str.length - 1) === '/'
      |}
      |""".stripMargin

  private def _default_ui_css(): String =
    s"""body {
      |  margin: 0;
      |  color: #1f2933;
      |  background: #ffffff;
      |  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      |  line-height: 1.7;
      |}
      |
      |.header {
      |  border-bottom: 1px solid #d8dee4;
      |  background: #f7f9fb;
      |}
      |
      |.navbar {
      |  display: flex;
      |  align-items: center;
      |  gap: 2rem;
      |  min-height: 3.25rem;
      |  max-width: 72rem;
      |  margin: 0 auto;
      |  padding: 0 1rem;
      |}
      |
      |.navbar-brand {
      |  flex: 0 0 auto;
      |}
      |
      |.navbar-menu {
      |  display: flex;
      |  flex: 1 1 auto;
      |  justify-content: flex-end;
      |  min-width: 0;
      |}
      |
      |.navbar-end {
      |  display: flex;
      |  align-items: center;
      |  flex-wrap: wrap;
      |  justify-content: flex-end;
      |  gap: 0.25rem 1rem;
      |}
      |
      |.navbar-item {
      |  color: #1f2933;
      |  font-weight: 600;
      |  text-decoration: none;
      |  white-space: nowrap;
      |}
      |
      |.navbar-brand .navbar-item {
      |  font-size: 1.05rem;
      |}
      |
      |main.article {
      |  min-width: 0;
      |  padding: 2rem 2rem 4rem;
      |}
      |
      |.body {
      |  display: grid;
      |  grid-template-columns: minmax(12rem, 16rem) minmax(0, 1fr) minmax(11rem, 14rem);
      |  gap: 2rem;
      |  max-width: 88rem;
      |  margin: 0 auto;
      |  padding: 0 1rem;
      |}
      |
      |.body-dashboard {
      |  display: block;
      |  max-width: 112rem;
      |}
      |
      |.body-dashboard main.article {
      |  padding-left: 0;
      |  padding-right: 0;
      |}
      |
      |.body-dashboard .content {
      |  display: block;
      |}
      |
      |.nav,
      |.toc {
      |  color: #52616b;
      |  font-size: 0.92rem;
      |  padding-top: 2rem;
      |}
      |
      |.nav-container,
      |.toc-menu {
      |  position: sticky;
      |  top: 1rem;
      |}
      |
      |.nav-title,
      |.toc-menu h3 {
      |  margin: 0 0 0.75rem;
      |  color: #1f2933;
      |  font-size: 0.78rem;
      |  letter-spacing: 0.08em;
      |  text-transform: uppercase;
      |}
      |
      |.nav-list,
      |.toc-menu ul {
      |  list-style: none;
      |  margin: 0;
      |  padding: 0;
      |}
      |
      |.nav-list li,
      |.toc-menu li {
      |  margin: 0.35rem 0;
      |}
      |
      |.nav a,
      |.toc a {
      |  color: #34495e;
      |  text-decoration: none;
      |}
      |
      |.nav a:hover,
      |.toc a:hover {
      |  color: #0b5cad;
      |  text-decoration: underline;
      |}
      |
      |.nav-hint,
      |.toc-hint {
      |  margin: 0;
      |  color: #7b8794;
      |}
      |
      |@media (max-width: 64rem) {
      |  .body {
      |    display: block;
      |    max-width: 72rem;
      |  }
      |
      |  .nav,
      |  .toc {
      |    display: none;
      |  }
      |
      |  main.article {
      |    padding: 1.5rem 1rem 3rem;
      |  }
      |}
      |
      |.doc h1,
      |.doc h2,
      |.doc h3 {
      |  line-height: 1.3;
      |}
      |
      |.doc a {
      |  color: #0b5cad;
      |}
      |
      |.doc .reference-section {
      |  margin-top: 3rem;
      |  padding-top: 1rem;
      |  border-top: 1px solid #d8dee4;
      |}
      |
      |.container-fluid {
      |  width: 100%;
      |  box-sizing: border-box;
      |}
      |
      |.row {
      |  display: grid;
      |  grid-template-columns: repeat(12, minmax(0, 1fr));
      |}
      |
      |.g-3 {
      |  gap: 1rem;
      |}
      |
      |.col-12 {
      |  grid-column: span 12;
      |}
      |
      |.col-6 {
      |  grid-column: span 6;
      |}
      |
      |@media (min-width: 48rem) {
      |  .col-md-3 {
      |    grid-column: span 3;
      |  }
      |
      |  .col-md-6 {
      |    grid-column: span 6;
      |  }
      |}
      |
      |@media (min-width: 75rem) {
      |  .col-xl-2 {
      |    grid-column: span 2;
      |  }
      |
      |  .col-xl-3 {
      |    grid-column: span 3;
      |  }
      |
      |  .col-xl-4 {
      |    grid-column: span 4;
      |  }
      |
      |  .col-xl-5 {
      |    grid-column: span 5;
      |  }
      |
      |  .col-xl-6 {
      |    grid-column: span 6;
      |  }
      |
      |  .col-xl-7 {
      |    grid-column: span 7;
      |  }
      |
      |  .col-xl-8 {
      |    grid-column: span 8;
      |  }
      |}
      |
      |.card {
      |  height: 100%;
      |  background: #ffffff;
      |  border: 1px solid rgba(31, 41, 51, 0.08);
      |  border-radius: 16px;
      |  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.08);
      |}
      |
      |.card-body {
      |  padding: 1rem;
      |}
      |
      |.card-title {
      |  margin: 0 0 0.75rem;
      |  color: #1f2933;
      |  font-size: 0.95rem;
      |  font-weight: 800;
      |  letter-spacing: 0.01em;
      |}
      |
      |.badge {
      |  display: inline-flex;
      |  align-items: center;
      |  border-radius: 999px;
      |  padding: 0.14rem 0.5rem;
      |  font-size: 0.72rem;
      |  font-weight: 700;
      |}
      |
      |.list-group {
      |  list-style: none;
      |  margin: 0;
      |  padding: 0;
      |}
      |
      |.list-group-item {
      |  display: flex;
      |  align-items: center;
      |  justify-content: space-between;
      |  gap: 0.75rem;
      |  padding: 0.48rem 0;
      |  border-bottom: 1px solid #eef2f7;
      |}
      |
      |.list-group-item:last-child {
      |  border-bottom: 0;
      |}
      |
      |.progress {
      |  height: 0.45rem;
      |  overflow: hidden;
      |  background: #e9ecef;
      |  border-radius: 999px;
      |}
      |
      |.bok-dashboard {
      |  margin: 1rem 0 1.75rem;
      |}
      |
      |.bok-card {
      |  position: relative;
      |  overflow: hidden;
      |}
      |
      |.bok-card::before {
      |  content: "";
      |  position: absolute;
      |  inset: 0 auto 0 0;
      |  width: 0.28rem;
      |  background: #228be6;
      |}
      |
      |.bok-card-purpose::before {
      |  background: #0ca678;
      |}
      |
      |.bok-card-readiness::before {
      |  background: #f08c00;
      |}
      |
      |.bok-card-kpi::before {
      |  background: #5c7cfa;
      |}
      |
      |.bok-card-analysis-entry {
      |  background: linear-gradient(135deg, rgba(236,253,245,.96), rgba(239,246,255,.94)) !important;
      |}
      |
      |.bok-card-analysis-entry::before {
      |  background: #10b981;
      |}
      |
      |.bok-analysis-entry-lead {
      |  margin: 0 0 .42rem;
      |  color: #334155;
      |  font-size: .84rem;
      |  font-weight: 750;
      |  line-height: 1.36;
      |}
      |
      |.bok-analysis-entry-grid {
      |  display: grid;
      |  grid-template-columns: repeat(2, minmax(0, 1fr));
      |  gap: .9rem;
      |}
      |
      |.bok-analysis-entry-flow {
      |  display: grid;
      |  grid-template-columns: max-content max-content;
      |  align-items: stretch;
      |  gap: .18rem;
      |  width: fit-content;
      |  max-width: 100%;
      |  margin: .45rem auto 0;
      |}
      |
      |.bok-analysis-entry-analysis {
      |  display: grid;
      |  gap: .24rem;
      |  padding: .32rem;
      |  border: 1px solid rgba(16,185,129,.18);
      |  border-radius: 16px;
      |  background: linear-gradient(135deg, rgba(236,253,245,.56), rgba(255,255,255,.62));
      |}
      |
      |.bok-analysis-entry-group-title {
      |  color: #0f766e;
      |  font-size: .72rem;
      |  font-weight: 950;
      |  letter-spacing: .08em;
      |  text-transform: uppercase;
      |}
      |
      |.bok-analysis-entry-relation {
      |  display: grid;
      |  grid-template-columns: 13.2rem 2.2rem 13.2rem;
      |  align-items: stretch;
      |  gap: .16rem;
      |}
      |
      |.bok-analysis-entry-output-relation {
      |  display: grid;
      |  grid-template-columns: 2.2rem 13.2rem;
      |  align-items: stretch;
      |  gap: .16rem;
      |}
      |
      |.bok-analysis-entry-column {
      |  display: grid;
      |  gap: .24rem;
      |}
      |
      |.bok-analysis-entry-center {
      |  display: grid;
      |}
      |
      |.bok-analysis-entry-center .bok-analysis-entry-tile {
      |  min-height: 100%;
      |  align-content: center;
      |  border-color: rgba(16,185,129,.42);
      |  background: linear-gradient(135deg, rgba(236,253,245,.96), rgba(255,255,255,.88));
      |  box-shadow: 0 22px 46px rgba(6,95,70,.16);
      |}
      |
      |.bok-analysis-entry-mono-koto-process {
      |  outline: 2px solid rgba(16,185,129,.18);
      |  outline-offset: 2px;
      |}
      |
      |.bok-analysis-entry-arrow {
      |  display: grid;
      |  place-items: center;
      |  color: #0f766e;
      |  font-size: 1.78rem;
      |  font-weight: 1000;
      |  line-height: 1;
      |}
      |
      |.bok-analysis-entry-arrow-column {
      |  display: grid;
      |  gap: .16rem;
      |  align-items: stretch;
      |  justify-items: center;
      |  width: 2.2rem;
      |}
      |
      |.bok-analysis-entry-arrow-column .bok-analysis-entry-arrow {
      |  min-height: 5.2rem;
      |}
      |
      |.bok-analysis-entry-tile {
      |  display: grid;
      |  gap: .2rem;
      |  min-width: 0;
      |  min-height: 5.2rem;
      |  padding: .54rem .66rem;
      |  color: #0f172a !important;
      |  text-decoration: none !important;
      |  border: 1px solid rgba(15,118,110,.18);
      |  border-radius: 16px;
      |  background: rgba(255,255,255,.78);
      |  box-shadow: 0 14px 30px rgba(15,23,42,.10);
      |  transition: transform .16s ease, box-shadow .16s ease, border-color .16s ease;
      |}
      |
      |.bok-analysis-entry-analysis .bok-analysis-entry-tile,
      |.bok-analysis-entry-analysis .bok-analysis-entry-arrow-column .bok-analysis-entry-arrow {
      |  min-height: 3.95rem;
      |}
      |
      |.bok-analysis-entry-analysis .bok-analysis-entry-tile {
      |  padding: .42rem .54rem;
      |}
      |
      |.bok-analysis-entry-analysis .bok-analysis-entry-tile strong {
      |  font-size: .98rem;
      |}
      |
      |.bok-analysis-entry-analysis .bok-analysis-entry-tile em {
      |  font-size: .8rem;
      |  line-height: 1.16;
      |}
      |
      |.bok-analysis-entry-tile:hover,
      |.bok-analysis-entry-tile:focus {
      |  transform: translateY(-2px);
      |  border-color: rgba(16,185,129,.45);
      |  box-shadow: 0 20px 42px rgba(15,23,42,.16);
      |}
      |
      |.bok-analysis-entry-static {
      |  cursor: default;
      |}
      |
      |.bok-analysis-entry-static:hover,
      |.bok-analysis-entry-static:focus {
      |  transform: none;
      |  border-color: rgba(15,118,110,.18);
      |  box-shadow: 0 14px 30px rgba(15,23,42,.10);
      |}
      |
      |.bok-analysis-entry-tile strong {
      |  color: #0f172a;
      |  font-size: 1.04rem;
      |  font-weight: 950;
      |  letter-spacing: -.03em;
      |  white-space: nowrap;
      |}
      |
      |.bok-analysis-entry-tile em {
      |  color: #475569;
      |  font-style: normal;
      |  font-weight: 750;
      |  line-height: 1.22;
      |  font-size: .84rem;
      |}
      |
      |.bok-analysis-entry-kicker {
      |  width: max-content;
      |  padding: .14rem .38rem;
      |  color: #065f46;
      |  background: rgba(16,185,129,.14);
      |  border-radius: 999px;
      |  font-size: .64rem;
      |  font-weight: 900;
      |  letter-spacing: .05em;
      |}
      |
      |.bok-analysis-entry-scenario .bok-analysis-entry-kicker {
      |  color: #1d4ed8;
      |  background: rgba(59,130,246,.14);
      |}
      |
      |.bok-analysis-entry-article .bok-analysis-entry-kicker {
      |  color: #9a3412;
      |  background: rgba(251,146,60,.16);
      |}
      |
      |.bok-analysis-entry-project .bok-analysis-entry-kicker {
      |  color: #6d28d9;
      |  background: rgba(167,139,250,.18);
      |}
      |
      |.bok-analysis-entry-rdf .bok-analysis-entry-kicker {
      |  color: #0e7490;
      |  background: rgba(34,211,238,.18);
      |}
      |
      |.bok-article-grid {
      |  display: grid;
      |  grid-template-columns: repeat(auto-fit, minmax(16rem, 1fr));
      |  gap: .75rem;
      |}
      |
      |.bok-article-tile {
      |  display: grid;
      |  gap: .45rem;
      |  padding: .9rem 1rem;
      |  border: 1px solid rgba(148,163,184,.24);
      |  border-radius: 18px;
      |  background: rgba(255,255,255,.78);
      |  box-shadow: 0 12px 28px rgba(15,23,42,.08);
      |}
      |
      |.bok-article-tile-head {
      |  display: flex;
      |  gap: .45rem;
      |  align-items: center;
      |}
      |
      |.bok-article-tile h3 {
      |  margin: 0;
      |  font-size: 1.08rem;
      |  line-height: 1.3;
      |}
      |
      |.bok-article-tile p {
      |  margin: 0;
      |  color: #475569;
      |  font-weight: 650;
      |  line-height: 1.45;
      |}
      |
      |.bok-card-chart::before {
      |  background: #15aabf;
      |}
      |
      |.bok-card-quality::before {
      |  background: #e67700;
      |}
      |
      |.bok-card-matrix::before,
      |.bok-card-map::before {
      |  background: #7048e8;
      |}
      |
      |.bok-card-quick-links::before,
      |.bok-card-actions::before,
      |.bok-card-related::before {
      |  background: #495057;
      |}
      |
      |.bok-purpose-vision {
      |  margin: 0 0 0.65rem;
      |  color: #243b53;
      |  font-size: 1rem;
      |}
      |
      |.bok-purpose-vision-panel {
      |  display: flex;
      |  align-items: flex-start;
      |  gap: 0.7rem;
      |  margin: 0 0 0.85rem;
      |  padding: 0.9rem 0.95rem 0.95rem 1rem;
      |  border: 1px solid rgba(255, 255, 255, 0.18);
      |  border-radius: 16px;
      |  background: rgba(255, 255, 255, 0.16);
      |  box-shadow: 0 10px 24px rgba(0, 0, 0, 0.14);
      |}
      |
      |.bok-purpose-vision-copy {
      |  display: grid;
      |  gap: 0.18rem;
      |  min-width: 0;
      |}
      |
      |.bok-purpose-vision-copy small {
      |  color: #bfdbfe;
      |  font-size: 0.66rem;
      |  font-weight: 900;
      |  letter-spacing: 0.08em;
      |  text-transform: uppercase;
      |}
      |
      |.bok-purpose-vision-copy strong {
      |  color: #fff;
      |  line-height: 1.45;
      |}
      |
      |.bok-card-category-purpose .card-body {
      |  gap: 0.34rem !important;
      |  padding: 0.72rem 0.88rem 0.62rem !important;
      |}
      |
      |.bok-card-category-purpose .bok-purpose-vision-panel {
      |  gap: 0.42rem;
      |  margin-bottom: 0;
      |  padding: 0.54rem 0.62rem 0.56rem;
      |  border-radius: 14px;
      |  box-shadow: 0 8px 18px rgba(0, 0, 0, 0.10);
      |}
      |
      |.bok-card-category-purpose .bok-purpose-vision-copy {
      |  gap: 0.08rem;
      |}
      |
      |.bok-card-category-purpose .bok-purpose-vision-copy strong {
      |  line-height: 1.35;
      |}
      |
      |.bok-card-category-purpose .bok-purpose-tree {
      |  gap: 0.34rem;
      |  margin-top: 0.3rem;
      |  padding: 0.42rem 0.52rem 0.44rem;
      |  border: 1px solid rgba(255, 255, 255, 0.20);
      |  border-radius: 14px;
      |  background: rgba(255, 255, 255, 0.10);
      |  box-shadow: 0 8px 18px rgba(0, 0, 0, 0.08);
      |}
      |
      |.bok-purpose-tree-label {
      |  display: flex;
      |  align-items: center;
      |  gap: 0.5rem;
      |  color: #fff;
      |  font-size: 0.72rem;
      |  font-weight: 900;
      |  letter-spacing: 0.08em;
      |  text-transform: uppercase;
      |}
      |
      |.bok-card-category-purpose .bok-purpose-goal {
      |  gap: 0.34rem;
      |  padding: 0.46rem 0.58rem 0.48rem 0.68rem !important;
      |  border-radius: 14px;
      |  border: 1px solid rgba(255, 255, 255, 0.32);
      |  background: rgba(255, 255, 255, 0.18);
      |  box-shadow: 0 0 0 3px rgba(255, 255, 255, 0.06), 0 8px 18px rgba(0, 0, 0, 0.10);
      |}
      |
      |.bok-card-category-purpose .bok-purpose-goal::before {
      |  left: 0.52rem;
      |  top: 2.08rem;
      |  bottom: 0.56rem;
      |  width: 2px;
      |  background: rgba(255, 255, 255, 0.24);
      |}
      |
      |.bok-card-category-purpose .bok-purpose-goal-head {
      |  gap: 0.45rem;
      |}
      |
      |.bok-card-category-purpose .bok-purpose-subgoals {
      |  margin: 0.3rem 0 0 1.55rem;
      |  gap: 0.24rem;
      |}
      |
      |.bok-card-category-purpose .bok-purpose-subgoals li::before {
      |  left: -1rem;
      |  width: 0.72rem;
      |}
      |
      |.bok-purpose-list {
      |  margin-top: 0.45rem;
      |}
      |
      |.bok-purpose-list ul,
      |.bok-action-list {
      |  margin: 0.25rem 0 0;
      |  padding-left: 1.2rem;
      |}
      |
      |.bok-more,
      |.bok-card-muted {
      |  color: #6c757d;
      |}
      |
      |.bok-kpi-value {
      |  color: #172b4d;
      |  font-size: 2rem;
      |  font-weight: 850;
      |  line-height: 1;
      |}
      |
      |.bok-kpi-label {
      |  margin-top: 0.4rem;
      |  color: #1f2933;
      |  font-weight: 800;
      |}
      |
      |.bok-kpi-note {
      |  color: #687782;
      |  font-size: 0.82rem;
      |}
      |
      |.bok-definition-row {
      |  display: flex;
      |  align-items: center;
      |  justify-content: space-between;
      |  gap: 1rem;
      |  padding: 0.38rem 0;
      |  border-bottom: 1px solid #eef2f7;
      |}
      |
      |.bok-definition-row span {
      |  color: #687782;
      |}
      |
      |.bok-definition-row strong {
      |  color: #1f2933;
      |  text-align: right;
      |}
      |
      |.bok-badge-info {
      |  color: #0b5cad;
      |  background: #e7f5ff;
      |}
      |
      |.bok-alert-list .list-group-item {
      |  justify-content: flex-start;
      |}
      |
      |.bok-card-glossary-summary .bok-dashboard-grid {
      |  grid-template-columns: repeat(auto-fit, minmax(11rem, 1fr));
      |  margin: 0.95rem 0 0;
      |}
      |
      |.bok-card-glossary-summary .card-body {
      |  min-height: 0 !important;
      |}
      |
      |.bok-glossary-summary-metrics {
      |  grid-template-columns: repeat(auto-fit, minmax(10rem, 1fr));
      |}
      |
      |.bok-glossary-connection-rates {
      |  display: grid;
      |  grid-template-columns: repeat(auto-fit, minmax(11rem, 1fr));
      |  gap: 0.55rem;
      |  margin-top: 0.85rem;
      |}
      |
      |.bok-glossary-connection-rate {
      |  display: flex;
      |  align-items: center;
      |  justify-content: space-between;
      |  gap: 0.75rem;
      |  padding: 0.58rem 0.72rem;
      |  border: 1px solid rgba(59, 130, 246, 0.18);
      |  border-radius: 12px;
      |  color: #1e293b;
      |  background: rgba(239, 246, 255, 0.72);
      |}
      |
      |.bok-glossary-connection-rate b {
      |  color: #334155;
      |}
      |
      |.bok-glossary-connection-rate span {
      |  color: #0f172a;
      |  font-weight: 900;
      |}
      |
      |.bok-mono-koto-workflow {
      |  display: grid;
      |  gap: 0.9rem;
      |}
      |
      |.bok-mono-koto-lead {
      |  margin: 0;
      |  color: #1e293b;
      |  font-weight: 750;
      |}
      |
      |.bok-workflow-stage-grid {
      |  display: grid;
      |  grid-template-columns: repeat(auto-fit, minmax(14rem, 1fr));
      |  gap: 0.85rem;
      |}
      |
      |.bok-workflow-stage {
      |  position: relative;
      |  min-height: 13rem;
      |  display: flex;
      |  flex-direction: column;
      |  gap: 0.72rem;
      |  padding: 0.95rem;
      |  overflow: hidden;
      |  border: 1px solid rgba(148, 163, 184, 0.22);
      |  border-radius: 18px;
      |  color: #0f172a;
      |  background: linear-gradient(160deg, rgba(255,255,255,.96), rgba(239,246,255,.82));
      |  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.08);
      |}
      |
      |.bok-workflow-stage::before {
      |  content: "";
      |  position: absolute;
      |  inset: 0 0 auto;
      |  height: 0.32rem;
      |  background: #3b82f6;
      |}
      |
      |.bok-workflow-stage-head {
      |  display: flex;
      |  align-items: flex-start;
      |  gap: 0.62rem;
      |}
      |
      |.bok-workflow-stage-head span {
      |  display: inline-flex;
      |  flex: 0 0 auto;
      |  align-items: center;
      |  justify-content: center;
      |  width: 2rem;
      |  height: 2rem;
      |  border: 2px solid rgba(255, 255, 255, 0.92);
      |  border-radius: 999px;
      |  color: #fff !important;
      |  background: #0f172a;
      |  font-size: 0.78rem;
      |  font-weight: 950;
      |  line-height: 1;
      |  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.45);
      |  box-shadow: 0 5px 14px rgba(15, 23, 42, 0.22);
      |}
      |
      |.bok-workflow-stage-head strong {
      |  color: #1e293b;
      |  font-size: 0.92rem;
      |  line-height: 1.35;
      |}
      |
      |.bok-workflow-stage-metric {
      |  display: flex;
      |  align-items: baseline;
      |  justify-content: space-between;
      |  gap: 0.75rem;
      |  padding: 0.62rem 0.72rem;
      |  border-radius: 14px;
      |  background: rgba(219, 234, 254, 0.72);
      |}
      |
      |.bok-workflow-stage-metric b {
      |  color: #0f172a;
      |  font-size: 1.75rem;
      |  font-weight: 950;
      |  line-height: 1;
      |  letter-spacing: -0.04em;
      |}
      |
      |.bok-workflow-stage-metric span {
      |  color: #475569;
      |  font-size: 0.76rem;
      |  font-weight: 850;
      |  text-align: right;
      |}
      |
      |.bok-workflow-stage p {
      |  margin: 0;
      |  color: #475569;
      |  font-size: 0.86rem;
      |  line-height: 1.55;
      |}
      |
      |.bok-workflow-stage .bok-term-type-chip-list {
      |  margin-top: auto;
      |}
      |
      |.bok-workflow-actions {
      |  display: flex;
      |  flex-wrap: wrap;
      |  gap: 0.45rem;
      |  margin-top: auto;
      |  padding-top: 0.25rem;
      |}
      |
      |.bok-workflow-action {
      |  display: inline-flex;
      |  align-items: center;
      |  justify-content: center;
      |  min-height: 2rem;
      |  padding: 0.42rem 0.62rem;
      |  border: 1px solid rgba(37, 99, 235, 0.18);
      |  border-radius: 999px;
      |  color: #1d4ed8;
      |  background: rgba(255, 255, 255, 0.74);
      |  font-size: 0.75rem;
      |  font-weight: 850;
      |  line-height: 1.2;
      |  text-decoration: none;
      |}
      |
      |.bok-workflow-action:hover {
      |  color: #1e40af;
      |  border-color: rgba(37, 99, 235, 0.34);
      |  background: rgba(219, 234, 254, 0.82);
      |  text-decoration: none;
      |}
      |
      |.bok-workflow-refinement-split {
      |  display: grid;
      |  grid-template-columns: repeat(auto-fit, minmax(10rem, 1fr));
      |  gap: 0.55rem;
      |  margin-top: auto;
      |}
      |
      |.bok-workflow-refinement-split h4 {
      |  margin: 0 0 0.38rem;
      |  color: #334155;
      |  font-size: 0.78rem;
      |  font-weight: 950;
      |}
      |
      |.bok-workflow-refinement-split .bok-term-type-chip-list {
      |  margin-top: 0;
      |}
      |
      |.bok-workflow-refinement-split .bok-term-type-chip {
      |  min-height: 1.45rem;
      |  padding: 0.12rem 0.45rem;
      |  font-size: 0.72rem;
      |}
      |
      |.bok-connection-card {
      |  display: grid;
      |  gap: 0.85rem;
      |}
      |
      |.bok-connection-card > p {
      |  margin: 0;
      |}
      |
      |.bok-connection-metrics {
      |  display: grid;
      |  grid-template-columns: repeat(auto-fit, minmax(9rem, 1fr));
      |  gap: 0.65rem;
      |}
      |
      |.bok-connection-metric {
      |  display: grid;
      |  gap: 0.25rem;
      |  min-height: 7.4rem;
      |  padding: 0.78rem;
      |  border: 1px solid rgba(148, 163, 184, 0.22);
      |  border-radius: 16px;
      |  background: rgba(255, 255, 255, 0.74);
      |}
      |
      |.bok-connection-metric span {
      |  color: #475569;
      |  font-size: 0.76rem;
      |  font-weight: 900;
      |}
      |
      |.bok-connection-metric b {
      |  color: #0f172a;
      |  font-size: 1.95rem;
      |  font-weight: 950;
      |  line-height: 1;
      |  letter-spacing: -0.04em;
      |}
      |
      |.bok-connection-metric em {
      |  color: #64748b;
      |  font-size: 0.72rem;
      |  font-style: normal;
      |  font-weight: 750;
      |}
      |
      |.bok-connection-breakdown {
      |  display: grid;
      |  gap: 0.45rem;
      |}
      |
      |.bok-connection-breakdown > strong {
      |  color: #334155;
      |}
      |
      |.bok-connection-chip-list {
      |  display: flex;
      |  flex-wrap: wrap;
      |  gap: 0.38rem;
      |}
      |
      |.bok-connection-chip {
      |  display: inline-flex;
      |  align-items: center;
      |  gap: 0.42rem;
      |  padding: 0.18rem 0.52rem;
      |  border: 1px solid rgba(148, 163, 184, 0.24);
      |  border-radius: 999px;
      |  background: rgba(248, 250, 252, 0.86);
      |}
      |
      |.bok-connection-chip b {
      |  color: #334155;
      |  font-size: 0.76rem;
      |}
      |
      |.bok-connection-chip em {
      |  color: #0f172a;
      |  font-style: normal;
      |  font-weight: 950;
      |}
      |
      |.bok-connection-checkpoints,
      |.bok-connection-note {
      |  padding: 0.62rem 0.72rem;
      |  border-radius: 12px;
      |  background: rgba(239, 246, 255, 0.68);
      |  color: #334155;
      |  font-weight: 750;
      |}
      |
      |.bok-connection-note {
      |  background: rgba(255, 247, 237, 0.78);
      |}
      |
      |.bok-mono-koto-steps {
      |  display: grid;
      |  grid-template-columns: repeat(auto-fit, minmax(16rem, 1fr));
      |  gap: 0.85rem;
      |  margin: 0;
      |  padding: 0;
      |  list-style: none;
      |}
      |
      |.bok-mono-koto-steps > li {
      |  position: relative;
      |  padding: 0.9rem 0.95rem 0.95rem;
      |  border: 1px solid rgba(130, 105, 70, 0.18);
      |  border-radius: 14px;
      |  background: rgba(255, 255, 255, 0.68);
      |}
      |
      |.bok-mono-koto-steps > li > strong {
      |  display: block;
      |  margin-bottom: 0.38rem;
      |  color: #1f2937;
      |}
      |
      |.bok-mono-koto-steps p {
      |  margin: 0.3rem 0 0;
      |}
      |
      |.bok-mono-koto-refinement-grid {
      |  display: grid;
      |  grid-template-columns: repeat(auto-fit, minmax(12rem, 1fr));
      |  gap: 0.65rem;
      |  margin-top: 0.65rem;
      |}
      |
      |.bok-mono-koto-refinement-grid section {
      |  padding: 0.72rem;
      |  border: 1px solid rgba(59, 130, 246, 0.16);
      |  border-radius: 12px;
      |  background: rgba(239, 246, 255, 0.58);
      |}
      |
      |.bok-mono-koto-refinement-grid h4 {
      |  margin: 0 0 0.3rem;
      |  color: #334155;
      |  font-size: 0.92rem;
      |}
      |
      |.bok-term-type-chip-list {
      |  display: flex;
      |  flex-wrap: wrap;
      |  gap: 0.38rem;
      |  margin-top: 0.6rem;
      |}
      |
      |.bok-term-type-chip {
      |  display: inline-flex;
      |  align-items: center;
      |  min-height: 1.65rem;
      |  padding: 0.18rem 0.55rem;
      |  color: #0f172a;
      |  background: rgba(255, 255, 255, 0.82);
      |  border: 1px solid rgba(148, 163, 184, 0.28);
      |  border-radius: 999px;
      |  font-size: 0.78rem;
      |  font-weight: 850;
      |}
      |
      |.bok-mono-koto-principle {
      |  margin: 0;
      |  padding: 0.72rem 0.85rem;
      |  color: #334155;
      |  background: rgba(255, 247, 237, 0.82);
      |  border: 1px solid rgba(251, 146, 60, 0.22);
      |  border-radius: 14px;
      |  font-weight: 750;
      |}
      |
      |.bok-mono-koto-cml-note {
      |  margin: 0;
      |  padding: 0.72rem 0.85rem;
      |  color: #1e3a8a;
      |  background: rgba(219, 234, 254, 0.78);
      |  border: 1px solid rgba(37, 99, 235, 0.18);
      |  border-radius: 14px;
      |  font-weight: 750;
      |}
      |
      |.bok-term-type-summary {
      |  display: grid;
      |  grid-template-columns: repeat(auto-fit, minmax(9.5rem, 1fr));
      |  gap: 0.55rem;
      |  margin-top: 0.9rem;
      |}
      |
      |.bok-term-type-summary-item {
      |  display: flex;
      |  align-items: baseline;
      |  justify-content: space-between;
      |  gap: 0.5rem;
      |  padding: 0.55rem 0.7rem;
      |  border: 1px solid rgba(130, 105, 70, 0.18);
      |  border-radius: 12px;
      |  background: rgba(255, 255, 255, 0.58);
      |}
      |
      |.bok-term-type-summary-item b {
      |  font-size: 1.2rem;
      |}
      |
      |.bok-mono-koto-summary {
      |  align-items: stretch;
      |  padding-top: 0.75rem;
      |  border-top: 1px solid rgba(130, 105, 70, 0.16);
      |}
      |
      |.bok-mono-koto-summary > strong {
      |  display: flex;
      |  align-items: center;
      |  color: #334155;
      |}
      |
      |.bok-term-analysis-route-grid,
      |.bok-term-type-issue-grid {
      |  display: grid;
      |  grid-template-columns: repeat(auto-fit, minmax(15rem, 1fr));
      |  gap: 0.85rem;
      |}
      |
      |.bok-term-analysis-route-groups {
      |  display: grid;
      |  gap: 1rem;
      |}
      |
      |.bok-term-analysis-route-group {
      |  display: grid;
      |  gap: 0.85rem;
      |  padding: 0.95rem;
      |  border: 1px solid rgba(148, 163, 184, 0.20);
      |  border-radius: 18px;
      |  background: rgba(248, 250, 252, 0.72);
      |}
      |
      |.bok-term-analysis-route-group-head {
      |  display: flex;
      |  align-items: flex-start;
      |  justify-content: space-between;
      |  gap: 1rem;
      |  padding-bottom: 0.75rem;
      |  border-bottom: 1px solid rgba(148, 163, 184, 0.18);
      |}
      |
      |.bok-term-analysis-route-group-head h4 {
      |  margin: 0.35rem 0 0.2rem;
      |  color: #1e293b;
      |  font-size: 1.08rem;
      |}
      |
      |.bok-term-analysis-route-group-head p {
      |  margin: 0;
      |  color: #64748b;
      |}
      |
      |.bok-term-analysis-route-group-head dl {
      |  display: grid;
      |  grid-template-columns: auto auto;
      |  gap: 0.22rem 0.65rem;
      |  min-width: 9rem;
      |  margin: 0;
      |  padding: 0.55rem 0.65rem;
      |  border-radius: 12px;
      |  background: rgba(219, 234, 254, 0.62);
      |}
      |
      |.bok-term-analysis-route-group-head dt {
      |  color: #64748b;
      |  font-size: 0.72rem;
      |  font-weight: 850;
      |}
      |
      |.bok-term-analysis-route-group-head dd {
      |  margin: 0;
      |  color: #0f172a;
      |  font-weight: 950;
      |  text-align: right;
      |}
      |
      |.bok-term-analysis-route,
      |.bok-term-type-issue-group {
      |  border: 1px solid rgba(130, 105, 70, 0.18);
      |  border-radius: 12px;
      |  background: rgba(255, 255, 255, 0.62);
      |  padding: 0.9rem;
      |}
      |
      |.bok-term-analysis-route-head {
      |  display: flex;
      |  align-items: center;
      |  justify-content: space-between;
      |  gap: 0.75rem;
      |  margin-bottom: 0.65rem;
      |}
      |
      |.bok-term-analysis-route-head strong {
      |  font-size: 1.45rem;
      |  color: #1f2937;
      |}
      |
      |.bok-term-analysis-route-checkpoints {
      |  margin: 0.7rem 0;
      |}
      |
      |.bok-term-analysis-route-checkpoints dt {
      |  color: #64748b;
      |  font-size: 0.78rem;
      |  font-weight: 800;
      |}
      |
      |.bok-term-analysis-route-checkpoints dd {
      |  margin: 0 0 0.45rem;
      |}
      |
      |.bok-index-term-category {
      |  color: var(--bok-muted, #64748b);
      |  font-size: 0.88rem;
      |  font-weight: 800;
      |  white-space: nowrap;
      |}
      |
      |.bok-term-type-issue-group h4 {
      |  margin: 0 0 0.6rem;
      |  font-size: 0.95rem;
      |}
      |
      |.bok-card-term-type .bok-metadata-table {
      |  margin-bottom: 0;
      |}
      |
      |.bok-card-term-type .bok-metadata-table th {
      |  width: 34%;
      |  color: var(--bok-muted);
      |  font-size: 0.78rem;
      |  text-transform: uppercase;
      |  letter-spacing: 0.05em;
      |}
      |
      |.bok-matrix-table {
      |  width: 100%;
      |  border-collapse: collapse;
      |  font-size: 0.86rem;
      |}
      |
      |.bok-matrix-table th,
      |.bok-matrix-table td {
      |  padding: 0.45rem 0.35rem;
      |  border-bottom: 1px solid #eef2f7;
      |  text-align: left;
      |}
      |
      |.bok-matrix-table th {
      |  color: #52616b;
      |  font-size: 0.75rem;
      |  letter-spacing: 0.05em;
      |  text-transform: uppercase;
      |}
      |
      |.bok-map-list .list-group-item {
      |  align-items: flex-start;
      |  flex-direction: column;
      |}
      |
      |.bok-map-list span {
      |  color: #687782;
      |  font-size: 0.82rem;
      |}
      |
      |.bok-tag-tree ul {
      |  margin: 0;
      |  padding-left: 1rem;
      |  list-style: none;
      |}
      |
      |.bok-tag-tree > ul {
      |  padding-left: 0;
      |}
      |
      |.bok-tag-tree li {
      |  margin: 0.35rem 0;
      |}
      |
      |.bok-tag-tree li li {
      |  display: flex;
      |  align-items: center;
      |  justify-content: space-between;
      |  gap: 0.75rem;
      |  padding: 0.32rem 0;
      |  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
      |}
      |
      |.bok-tag-tree a {
      |  color: #174ea6;
      |  font-weight: 750;
      |  text-decoration: none;
      |}
      |
      |.bok-tag-tree span {
      |  min-width: 2rem;
      |  color: #52616b;
      |  font-size: 0.78rem;
      |  font-weight: 800;
      |  text-align: right;
      |}
      |
      |.bok-tag-tree-branch > ul {
      |  padding-left: 0.9rem;
      |}
      |
      |.bok-tag-tree-branch-heading {
      |  display: flex;
      |  align-items: center;
      |  justify-content: space-between;
      |  gap: 0.75rem;
      |}
      |
      |.bok-tag-tree .bok-tag-tree-segment {
      |  display: block;
      |  min-width: 0;
      |  margin: 0.38rem 0 0.2rem;
      |  color: #334155;
      |  font-size: 0.78rem;
      |  font-weight: 900;
      |  text-align: left;
      |}
      |
      |a.bok-tag-tree-segment {
      |  text-decoration: none;
      |}
      |
      |.bok-tag-tree-namespace {
      |  display: inline-flex;
      |  margin: 0.35rem 0;
      |  color: #102a43 !important;
      |  font-size: 0.88rem;
      |  letter-spacing: 0.04em;
      |  text-transform: uppercase;
      |}
      |
      |.bok-tag-detail-doc {
      |  padding-top: 1.5rem;
      |}
      |
      |.bok-tag-chip-list {
      |  display: flex;
      |  flex-wrap: wrap;
      |  gap: 0.38rem;
      |  align-items: center;
      |}
      |
      |.bok-tag-chip {
      |  display: inline-flex;
      |  align-items: center;
      |  gap: 0.32rem;
      |  min-height: 1.55rem;
      |  padding: 0.18rem 0.52rem;
      |  border: 1px solid rgba(37, 99, 235, 0.18);
      |  border-radius: 999px;
      |  background: linear-gradient(135deg, rgba(219, 234, 254, 0.92), rgba(236, 253, 245, 0.88));
      |  color: #1d4ed8;
      |  font-size: 0.72rem;
      |  font-weight: 850;
      |  letter-spacing: 0.01em;
      |  line-height: 1;
      |  text-decoration: none;
      |  box-shadow: 0 5px 14px rgba(15, 23, 42, 0.06);
      |}
      |
      |.bok-tag-chip:hover {
      |  border-color: rgba(37, 99, 235, 0.34);
      |  color: #174ea6;
      |  text-decoration: none;
      |}
      |
      |.bok-tag-chip strong {
      |  display: inline-flex;
      |  align-items: center;
      |  justify-content: center;
      |  min-width: 1.1rem;
      |  height: 1.1rem;
      |  padding: 0 0.28rem;
      |  border-radius: 999px;
      |  background: rgba(37, 99, 235, 0.12);
      |  color: #1e3a8a;
      |  font-size: 0.66rem;
      |}
      |
      |.bok-knowledge-tag-bar {
      |  display: flex;
      |  flex-wrap: wrap;
      |  gap: 0.45rem 0.8rem;
      |  align-items: center;
      |  margin: -0.2rem 0 0.8rem;
      |  padding: 0.46rem 0.62rem;
      |  border: 1px solid rgba(148, 163, 184, 0.20);
      |  border-radius: 14px;
      |  background: rgba(248, 250, 252, 0.78);
      |}
      |
      |.bok-knowledge-tag-group {
      |  display: inline-flex;
      |  flex-wrap: wrap;
      |  gap: 0.42rem;
      |  align-items: center;
      |  padding: 0.22rem 0.32rem 0.22rem 0.22rem;
      |  border: 1px solid rgba(37, 99, 235, 0.12);
      |  border-radius: 999px;
      |  background: rgba(239, 246, 255, 0.72);
      |  color: #334155;
      |  font-size: 0.82rem;
      |  line-height: 1.35;
      |}
      |
      |.bok-knowledge-tag-namespace {
      |  display: inline-flex;
      |  align-items: center;
      |  min-height: 1.55rem;
      |  padding: 0.16rem 0.62rem;
      |  border: 1px solid rgba(15, 23, 42, 0.08);
      |  border-radius: 999px;
      |  background: rgba(255, 255, 255, 0.78);
      |  color: #334155;
      |  font-weight: 900;
      |}
      |
      |.bok-knowledge-tag-leaves {
      |  display: inline-flex;
      |  flex-wrap: wrap;
      |  gap: 0.32rem;
      |  align-items: center;
      |}
      |
      |.bok-knowledge-tag-leaf {
      |  display: inline-flex;
      |  align-items: center;
      |  min-height: 1.55rem;
      |  padding: 0.16rem 0.56rem;
      |  border: 1px solid rgba(37, 99, 235, 0.18);
      |  border-radius: 999px;
      |  background: rgba(219, 234, 254, 0.72);
      |  color: #1d4ed8;
      |  font-weight: 820;
      |  text-decoration: none;
      |}
      |
      |.bok-knowledge-tag-leaf:hover {
      |  color: #174ea6;
      |  text-decoration: underline;
      |}
      |
      |.bok-tag-detail-lead {
      |  max-width: 46rem;
      |  margin: 0.75rem 0 1rem;
      |  color: #52616b;
      |  font-size: 1rem;
      |  line-height: 1.65;
      |}
      |
      |.bok-tag-detail-section {
      |  margin: 2rem 0;
      |}
      |
      |.bok-tag-detail-section h2 {
      |  margin: 0 0 0.75rem;
      |}
      |
      |.bok-tag-reference-group {
      |  margin: 1.25rem 0;
      |}
      |
      |.bok-dashboard-grid {
      |  display: grid;
      |  grid-template-columns: repeat(auto-fit, minmax(10rem, 1fr));
      |  gap: 1rem;
      |  margin: 1rem 0;
      |}
      |
      |.bok-metric-card {
      |  background: #ffffff;
      |  border: 1px solid #e9ecef;
      |  border-radius: 0.8rem;
      |  padding: 1rem;
      |  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.06);
      |}
      |
      |.bok-metric-label {
      |  color: #52616b;
      |  font-size: 0.78rem;
      |  font-weight: 700;
      |  letter-spacing: 0.06em;
      |  text-transform: uppercase;
      |}
      |
      |.bok-metric-value {
      |  color: #172b4d;
      |  font-size: 1.9rem;
      |  font-weight: 850;
      |}
      |
      |.bok-metric-note {
      |  color: #687782;
      |  font-size: 0.82rem;
      |}
      |
      |.bok-dashboard-chart[data-chart="distribution-ratio"] {
      |  margin: 1rem 0 0;
      |}
      |
      |.bok-chart-row {
      |  display: grid;
      |  grid-template-columns: 5rem minmax(0, 1fr) 2.5rem;
      |  align-items: center;
      |  gap: 0.6rem;
      |  margin: 0.45rem 0;
      |  color: #52616b;
      |  font-size: 0.86rem;
      |}
      |
      |.bok-chart-row div {
      |  height: 0.55rem;
      |  overflow: hidden;
      |  background: #edf2f7;
      |  border-radius: 999px;
      |}
      |
      |.bok-chart-row b {
      |  display: block;
      |  height: 100%;
      |  background: linear-gradient(90deg, #5c7cfa, #15aabf);
      |  border-radius: 999px;
      |}
      |
      |.bok-chart-row em {
      |  color: #1f2933;
      |  font-style: normal;
      |  font-weight: 700;
      |  text-align: right;
      |}
      |
      |.bok-dashboard-chart[data-chart="cumulative-date"] {
      |  background: transparent;
      |  border-radius: 0;
      |  margin: 0;
      |  padding: 0;
      |  box-shadow: none;
      |}
      |
      |.bok-cumulative-chart-head {
      |  display: flex;
      |  align-items: baseline;
      |  flex-wrap: wrap;
      |  gap: 0.5rem 1rem;
      |  margin-bottom: 0.6rem;
      |}
      |
      |.bok-cumulative-chart-title {
      |  color: #1f2933;
      |  font-weight: 700;
      |}
      |
      |.bok-cumulative-chart-range,
      |.bok-cumulative-chart-scale {
      |  color: #5d5d5d;
      |  font-size: 0.82rem;
      |}
      |
      |.bok-cumulative-chart-svg {
      |  display: block;
      |  width: 100%;
      |  height: 12rem;
      |  overflow: visible;
      |}
      |
      |.bok-cumulative-axis-x,
      |.bok-cumulative-axis-y {
      |  stroke: #d8dee4;
      |  stroke-width: 0.7;
      |}
      |
      |.bok-cumulative-line {
      |  fill: none;
      |  stroke-width: 2.6;
      |  stroke-linecap: round;
      |  stroke-linejoin: round;
      |}
      |
      |.bok-cumulative-line-articles {
      |  stroke: #5c7cfa;
      |}
      |
      |.bok-cumulative-line-terms {
      |  stroke: #15aabf;
      |}
      |
      |.bok-cumulative-line-total {
      |  stroke: #0b7285;
      |}
      |
      |.bok-cumulative-points circle {
      |  stroke: #fff;
      |  stroke-width: 1.2;
      |}
      |
      |.bok-cumulative-point-articles {
      |  fill: #5c7cfa;
      |}
      |
      |.bok-cumulative-point-terms {
      |  fill: #15aabf;
      |}
      |
      |.bok-cumulative-point-total {
      |  fill: #0b7285;
      |}
      |
      |.bok-cumulative-legend {
      |  display: flex;
      |  flex-wrap: wrap;
      |  gap: 0.5rem 1rem;
      |  color: #424242;
      |  font-size: 0.85rem;
      |  margin: 0.25rem 0 0.6rem;
      |}
      |
      |.bok-cumulative-legend span {
      |  display: inline-flex;
      |  align-items: center;
      |  gap: 0.35rem;
      |}
      |
      |.bok-cumulative-marker {
      |  width: 0.7rem;
      |  height: 0.7rem;
      |  border-radius: 999px;
      |}
      |
      |.bok-cumulative-marker-articles {
      |  background: #5c7cfa;
      |}
      |
      |.bok-cumulative-marker-terms {
      |  background: #15aabf;
      |}
      |
      |.bok-cumulative-marker-total {
      |  background: #0b7285;
      |}
      |
      |.bok-cumulative-axis {
      |  display: flex;
      |  justify-content: space-between;
      |  gap: 0.75rem;
      |  color: #5d5d5d;
      |  font-size: 0.75rem;
      |  overflow-x: auto;
      |}
      |
      |.bok-cumulative-axis span {
      |  display: flex;
      |  flex-direction: column;
      |  align-items: center;
      |  min-width: 4.5rem;
      |}
      |
      |.bok-cumulative-axis em {
      |  color: #1f2933;
      |  font-style: normal;
      |  font-weight: 700;
      |}
      |
      |.navbar-category-dropdown,
      |.navbar-bok-dropdown {
      |  position: relative;
      |}
      |
      |.navbar-category-dropdown > .navbar-category-toggle,
      |.navbar-bok-dropdown > .navbar-bok-toggle {
      |  color: #fff;
      |  font-weight: 700;
      |  background: transparent;
      |  border: 0;
      |  border-radius: 0;
      |  box-shadow: none;
      |}
      |
      |.navbar-category-dropdown > .navbar-category-menu,
      |.navbar-bok-dropdown > .navbar-bok-menu {
      |  min-width: 14rem;
      |  padding: 0.45rem;
      |  border: 1px solid rgba(148, 163, 184, 0.28);
      |  border-radius: 16px;
      |  background: #0f172a;
      |  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.30);
      |  left: auto;
      |  right: 0;
      |  max-width: min(22rem, calc(100vw - 1rem));
      |}
      |
      |.navbar-dropdown-item {
      |  display: block;
      |  padding: 0.55rem 0.7rem;
      |  border-radius: 11px;
      |  color: #e0f2fe !important;
      |  font-weight: 800;
      |  text-decoration: none !important;
      |}
      |
      |.navbar-dropdown-item:hover {
      |  color: #ffffff !important;
      |  background: rgba(56, 189, 248, 0.16);
      |}
      |
      |.navbar-category-dropdown:hover > .navbar-category-menu,
      |.navbar-category-dropdown:focus-within > .navbar-category-menu,
      |.navbar-bok-dropdown:hover > .navbar-bok-menu,
      |.navbar-bok-dropdown:focus-within > .navbar-bok-menu {
      |  display: grid;
      |  gap: 0.18rem;
      |}
      |
      |@media screen and (max-width: 1023.5px) {
      |  .navbar-category-dropdown > .navbar-category-toggle,
      |  .navbar-bok-dropdown > .navbar-bok-toggle {
      |    color: #1f2933;
      |  }
      |
      |  .navbar-category-dropdown > .navbar-category-menu,
      |  .navbar-bok-dropdown > .navbar-bok-menu {
      |    position: static;
      |    display: block;
      |    width: 100%;
      |    background: #ffffff;
      |    margin: 0.25rem 0 0.5rem;
      |    border-radius: 0.5rem;
      |    box-shadow: none;
      |  }
      |}
      |
      |.bok-category-matrix-link {
      |  color: #0f4f9f;
      |  font-weight: 850;
      |  text-decoration: none;
      |}
      |
      |.bok-category-matrix-link:hover {
      |  text-decoration: underline;
      |}
      |
      |.bok-category-summary-grid {
      |  display: grid;
      |  grid-template-columns: repeat(auto-fit, minmax(13rem, 1fr));
      |  gap: 0.75rem;
      |  margin-bottom: 1rem;
      |}
      |
      |.bok-category-summary-card {
      |  display: grid;
      |  gap: 0.45rem;
      |  padding: 0.9rem;
      |  color: #1f2937;
      |  text-decoration: none;
      |  background: #ffffff;
      |  border: 1px solid #e0e7ff;
      |  border-radius: 18px;
      |  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.08);
      |}
      |
      |.bok-category-summary-card:hover,
      |.bok-category-summary-card:focus {
      |  color: #0f4f9f;
      |  text-decoration: none;
      |  transform: translateY(-1px);
      |  box-shadow: 0 16px 34px rgba(15, 23, 42, 0.13);
      |}
      |
      |.bok-category-summary-title {
      |  color: #111827;
      |  font-size: 1rem;
      |  font-weight: 900;
      |}
      |
      |.bok-category-summary-freshness {
      |  color: #64748b;
      |  font-size: 0.78rem;
      |}
      |
      |.bok-category-summary-metrics {
      |  display: grid;
      |  grid-template-columns: repeat(3, minmax(0, 1fr));
      |  gap: 0.45rem;
      |}
      |
      |.bok-category-summary-metrics span {
      |  display: flex;
      |  min-height: 3.1rem;
      |  flex-direction: column;
      |  justify-content: center;
      |  padding: 0.45rem;
      |  color: #475569;
      |  background: #f8fafc;
      |  border-radius: 12px;
      |  text-align: center;
      |  font-size: 0.72rem;
      |  font-weight: 800;
      |}
      |
      |.bok-category-summary-metrics b {
      |  color: #0f172a;
      |  font-size: 1.25rem;
      |  line-height: 1;
      |}
      |
      |.body.body-dashboard {
      |  max-width: 118rem;
      |  background: radial-gradient(circle at top left, rgba(92, 124, 250, 0.18), transparent 24rem), linear-gradient(135deg, #f4f7fb 0, #eef4ff 48%, #f8fafc 100%);
      |  border-radius: 28px;
      |  padding: 1.25rem;
      |}
      |
      |.body-dashboard main.article {
      |  padding-top: 0.75rem;
      |}
      |
      |.body-dashboard .doc {
      |  padding: 0 0 3rem;
      |}
      |
      |.body-dashboard h1.page {
      |  color: #fff;
      |  background: linear-gradient(135deg, #172b4d, #2554a6);
      |  border-radius: 22px;
      |  padding: 1.35rem 1.6rem;
      |  box-shadow: 0 18px 42px rgba(15, 23, 42, 0.18);
      |  letter-spacing: 0.01em;
      |}
      |
      |.bok-card {
      |  min-height: 100%;
      |  border: 0;
      |  border-radius: 22px;
      |  background: rgba(255, 255, 255, 0.92);
      |  box-shadow: 0 16px 42px rgba(15, 23, 42, 0.10), 0 1px 0 rgba(255, 255, 255, 0.75) inset;
      |  backdrop-filter: blur(6px);
      |  transition: transform 0.18s ease, box-shadow 0.18s ease;
      |}
      |
      |.bok-card:hover {
      |  transform: translateY(-2px);
      |  box-shadow: 0 22px 54px rgba(15, 23, 42, 0.14), 0 1px 0 rgba(255, 255, 255, 0.8) inset;
      |}
      |
      |.bok-card::before {
      |  width: 0.42rem;
      |}
      |
      |.bok-card .card-body {
      |  padding: 1.1rem 1.2rem 1.15rem;
      |}
      |
      |.bok-card .card-title {
      |  display: flex;
      |  align-items: center;
      |  gap: 0.5rem;
      |  margin-bottom: 0.9rem;
      |  color: #334155;
      |  font-size: 0.78rem;
      |  font-weight: 900;
      |  letter-spacing: 0.1em;
      |  text-transform: uppercase;
      |}
      |
      |.bok-card .card-title::before {
      |  content: "";
      |  width: 0.62rem;
      |  height: 0.62rem;
      |  border-radius: 999px;
      |  background: currentColor;
      |  opacity: 0.48;
      |}
      |
      |/* KPI cards: centered highlight numbers read better as dashboard metrics. */
      |.body-dashboard .bok-card-kpi .card-body {
      |  align-items: center !important;
      |  text-align: center;
      |}
      |
      |.body-dashboard .bok-card-kpi .bok-kpi-link {
      |  justify-items: center;
      |  text-align: center;
      |}
      |
      |.body-dashboard .bok-card-kpi .bok-kpi-value,
      |.body-dashboard .bok-card-kpi .bok-kpi-label,
      |.body-dashboard .bok-card-kpi .bok-kpi-note {
      |  width: 100%;
      |  text-align: center;
      |}
      |
      |/* Dashboard hero: separate marker, title, and summary as distinct zones. */
      |.body-dashboard .bok-dashboard-hero-copy {
      |  display: grid !important;
      |  align-content: center;
      |  gap: 1.1rem;
      |}
      |
      |.body-dashboard .bok-dashboard-eyebrow {
      |  margin: 0 !important;
      |  align-self: start;
      |}
      |
      |.body-dashboard .bok-dashboard-hero h1.page {
      |  margin: 0.2rem 0 0.1rem !important;
      |}
      |
      |.body-dashboard .bok-dashboard-lead {
      |  max-width: 58rem;
      |  margin: 0.2rem 0 0 !important;
      |  padding: 0.85rem 0 0.15rem 1.05rem;
      |  border-left: 4px solid rgba(191, 231, 255, 0.48);
      |  line-height: 1.78;
      |}
      |
      |/* Layout corrections: use full-width separators and keep Recent Changes compact. */
      |.body-dashboard .bok-card .card-title {
      |  width: 100%;
      |  padding-bottom: 0.56rem !important;
      |  border-bottom: 1px solid rgba(148, 163, 184, 0.28);
      |}
      |
      |.body-dashboard .bok-card-purpose .card-title {
      |  border-bottom-color: rgba(255, 255, 255, 0.24);
      |}
      |
      |.body-dashboard .bok-card-notification .card-body {
      |  display: grid !important;
      |  grid-template-columns: minmax(10rem, 13rem) minmax(0, 1fr) auto;
      |  gap: 0.6rem 0.85rem !important;
      |  align-items: center;
      |}
      |
      |.body-dashboard .bok-card-notification .card-title {
      |  grid-column: 1 / -1;
      |  padding-bottom: 0 !important;
      |  border-bottom: 0;
      |}
      |
      |.body-dashboard .bok-card-notification .bok-activity-list {
      |  display: flex !important;
      |  flex-wrap: wrap;
      |  gap: 0.42rem;
      |  margin: 0;
      |}
      |
      |.body-dashboard .bok-card-notification .bok-activity-list .list-group-item {
      |  display: inline-flex;
      |  align-items: center;
      |  gap: 0.35rem;
      |  width: auto;
      |  margin: 0;
      |  padding: 0.34rem 0.55rem;
      |  border: 1px solid rgba(20, 184, 166, 0.22);
      |  border-radius: 999px;
      |  background: rgba(255, 255, 255, 0.74);
      |  font-size: 0.82rem;
      |  line-height: 1.2;
      |}
      |
      |.body-dashboard .bok-card-notification .bok-activity-list .list-group-item a,
      |.body-dashboard .bok-card-notification .bok-activity-list .list-group-item time {
      |  white-space: nowrap;
      |}
      |
      |@media (max-width: 64rem) {
      |  .body-dashboard .bok-card-notification .card-body {
      |    grid-template-columns: 1fr;
      |    align-items: stretch;
      |  }
      |
      |  .body-dashboard .bok-card-notification .bok-card-link {
      |    white-space: normal;
      |  }
      |}
      |
      |.bok-card-purpose {
      |  color: #f8fafc;
      |  background: linear-gradient(135deg, #0f766e 0, #0f4f78 100%);
      |}
      |
      |.bok-card-category-purpose {
      |  min-height: auto;
      |  height: fit-content;
      |  align-self: start;
      |}
      |
      |[data-bok-card]:has(.bok-card-category-purpose) {
      |  align-self: start;
      |}
      |
      |.bok-card-purpose .card-title,
      |.bok-card-purpose .bok-purpose-vision,
      |.bok-card-purpose .bok-purpose-list strong,
      |.bok-card-purpose .bok-more {
      |  color: #fff;
      |}
      |
      |.bok-card-purpose li {
      |  color: #dff7f2;
      |}
      |
      |.bok-card-readiness {
      |  background: linear-gradient(135deg, #fff7ed, #ffffff);
      |}
      |
      |.bok-card-kpi {
      |  background: linear-gradient(160deg, #ffffff 0, #f8fbff 60%, #edf4ff 100%);
      |}
      |
      |.bok-card-kpi .card-body {
      |  min-height: 8.4rem;
      |  display: flex;
      |  flex-direction: column;
      |  justify-content: center;
      |}
      |
      |.bok-kpi-value {
      |  color: #0f3d73;
      |  font-size: 2.85rem;
      |  font-weight: 900;
      |  letter-spacing: -0.05em;
      |}
      |
      |.bok-kpi-label {
      |  font-size: 0.92rem;
      |  letter-spacing: 0.03em;
      |  text-transform: uppercase;
      |}
      |
      |.bok-kpi-note {
      |  margin-top: 0.2rem;
      |}
      |
      |.bok-card-chart {
      |  background: linear-gradient(180deg, #ffffff, #f8fbff);
      |}
      |
      |.bok-card-chart .bok-dashboard-chart {
      |  min-height: 15rem;
      |}
      |
      |.bok-cumulative-chart-svg {
      |  height: 14rem;
      |}
      |
      |.bok-card-quality {
      |  background: linear-gradient(160deg, #fffaf0, #fff);
      |}
      |
      |.bok-card-matrix,
      |.bok-card-map {
      |  background: linear-gradient(160deg, #ffffff, #faf8ff);
      |}
      |
      |.bok-card-quick-links,
      |.bok-card-actions,
      |.bok-card-related {
      |  background: linear-gradient(160deg, #ffffff, #f8fafc);
      |}
      |
      |.bok-alert-list .list-group-item {
      |  border-radius: 12px;
      |  margin: 0.35rem 0;
      |  padding: 0.55rem 0.65rem;
      |  background: #fff7ed;
      |  border-bottom: 0;
      |}
      |
      |.bok-action-list li {
      |  margin: 0.38rem 0;
      |}
      |
      |.bok-chart-row div {
      |  height: 0.78rem;
      |  background: #e2e8f0;
      |}
      |
      |.bok-chart-row b {
      |  background: linear-gradient(90deg, #2563eb, #06b6d4);
      |}
      |
      |@media (max-width: 48rem) {
      |  .bok-analysis-entry-flow {
      |    grid-template-columns: 1fr;
      |  }
      |
      |  .bok-analysis-entry-relation {
      |    grid-template-columns: 1fr;
      |  }
      |
      |  .bok-analysis-entry-output-relation {
      |    grid-template-columns: 1fr;
      |  }
      |
      |  .bok-analysis-entry-column {
      |    grid-template-columns: repeat(auto-fit, minmax(13rem, 1fr));
      |  }
      |
      |  .bok-analysis-entry-arrow-column {
      |    grid-template-columns: repeat(auto-fit, minmax(13rem, 1fr));
      |  }
      |
      |  .bok-analysis-entry-arrow {
      |    transform: rotate(90deg);
      |  }
      |}
      |
      |@media (max-width: 48rem) {
      |  .body.body-dashboard {
      |    border-radius: 0;
      |    padding: 0.75rem;
      |  }
      |
      |  .body-dashboard h1.page {
      |    border-radius: 18px;
      |  }
      |
      |  .bok-kpi-value {
      |    font-size: 2.2rem;
      |  }
      |
      |  .bok-analysis-entry-grid {
      |    grid-template-columns: 1fr;
      |  }
      |
      |  .bok-analysis-entry-column {
      |    grid-template-columns: 1fr;
      |  }
      |
      |  .bok-analysis-entry-arrow-column {
      |    grid-template-columns: 1fr;
      |  }
      |
      |  .bok-analysis-entry-tile {
      |    min-height: 7.2rem;
      |  }
      |
      |  .bok-analysis-entry-arrow-column .bok-analysis-entry-arrow {
      |    min-height: 2.2rem;
      |  }
      |}
      |
      |.body.body-dashboard {
      |  max-width: none;
      |  margin: 0;
      |  padding: 0;
      |  background: #edf3fb;
      |}
      |
      |.body-dashboard .content {
      |  display: block;
      |}
      |
      |.body-dashboard .doc {
      |  max-width: none;
      |  margin: 0;
      |  padding: 0 1.25rem 3rem;
      |}
      |
      |.bok-dashboard-shell {
      |  max-width: 118rem;
      |  margin: 0 auto;
      |  padding: 1.25rem 0 2rem;
      |}
      |
      |.bok-dashboard-hero {
      |  display: grid;
      |  grid-template-columns: minmax(0, 1fr) minmax(18rem, 26rem);
      |  gap: 1.25rem;
      |  align-items: stretch;
      |  margin: 0 0 1.25rem;
      |  padding: 1.4rem;
      |  color: #fff;
      |  background: radial-gradient(circle at 14% 8%, rgba(255, 255, 255, 0.24), transparent 20rem), linear-gradient(135deg, #0f2742 0, #214f95 48%, #0f766e 100%);
      |  border-radius: 28px;
      |  box-shadow: 0 24px 64px rgba(15, 23, 42, 0.22);
      |}
      |
      |.bok-dashboard-hero h1.page {
      |  margin: 0 !important;
      |  padding: 0 !important;
      |  color: #fff !important;
      |  background: transparent !important;
      |  border-radius: 0 !important;
      |  box-shadow: none !important;
      |  font-size: clamp(2rem, 4vw, 4rem);
      |  line-height: 0.95;
      |  letter-spacing: -0.05em;
      |}
      |
      |.bok-dashboard-eyebrow {
      |  margin: 0 0 0.65rem;
      |  color: #bfe7ff;
      |  font-size: 0.78rem;
      |  font-weight: 900;
      |  letter-spacing: 0.22em;
      |  text-transform: uppercase;
      |}
      |
      |.bok-dashboard-lead {
      |  max-width: 54rem;
      |  margin: 0.9rem 0 0;
      |  color: #e6f2ff;
      |  font-size: 1.02rem;
      |  line-height: 1.65;
      |}
      |
      |.bok-dashboard-hero-facts {
      |  display: grid;
      |  grid-template-columns: repeat(3, minmax(0, 1fr));
      |  gap: 0.75rem;
      |  align-content: end;
      |}
      |
      |.bok-dashboard-hero-fact {
      |  display: flex;
      |  min-height: 7rem;
      |  flex-direction: column;
      |  justify-content: center;
      |  padding: 0.9rem;
      |  text-align: center;
      |  background: rgba(255, 255, 255, 0.12);
      |  border: 1px solid rgba(255, 255, 255, 0.18);
      |  border-radius: 16px;
      |}
      |
      |.bok-dashboard-hero-fact strong {
      |  color: #fff;
      |  font-size: 2.35rem;
      |  font-weight: 950;
      |  line-height: 1;
      |  text-decoration: none;
      |}
      |
      |.bok-dashboard-hero-fact em {
      |  margin-top: 0.45rem;
      |  color: #d7ecff;
      |  font-size: 0.75rem;
      |  font-style: normal;
      |  font-weight: 800;
      |  letter-spacing: 0.08em;
      |  text-transform: uppercase;
      |}
      |
      |.bok-dashboard.container-fluid {
      |  padding-left: 0;
      |  padding-right: 0;
      |}
      |
      |.bok-dashboard > .row {
      |  --bs-gutter-x: 1.15rem;
      |  --bs-gutter-y: 1.15rem;
      |}
      |
      |.navbar-category-nav,
      |.navbar-bok-nav {
      |  display: inline-flex;
      |  align-items: center;
      |  margin-left: 0.3rem;
      |}
      |
      |.navbar-category-dropdown > .navbar-category-toggle,
      |.navbar-bok-dropdown > .navbar-bok-toggle {
      |  min-height: 2.2rem;
      |  padding: 0.5rem 0.75rem;
      |  color: #fff !important;
      |  background: transparent;
      |  border: 0;
      |  border-radius: 0;
      |  box-shadow: none;
      |  font-weight: 800;
      |}
      |
      |.navbar-category-dropdown > .navbar-category-toggle:hover,
      |.navbar-category-dropdown > .navbar-category-toggle:focus,
      |.navbar-bok-dropdown > .navbar-bok-toggle:hover,
      |.navbar-bok-dropdown > .navbar-bok-toggle:focus {
      |  color: #fff !important;
      |  background: transparent;
      |  text-decoration: underline;
      |  text-underline-offset: 0.18rem;
      |}
      |
      |@media (max-width: 64rem) {
      |  .bok-dashboard-hero {
      |    grid-template-columns: 1fr;
      |  }
      |}
      |
      |@media (max-width: 48rem) {
      |  .body-dashboard .doc {
      |    padding-left: 0.75rem;
      |    padding-right: 0.75rem;
      |  }
      |
      |  .bok-dashboard-hero,
      |  .bok-dashboard-hero-facts {
      |    grid-template-columns: 1fr;
      |  }
      |}
      |
      |.bok-purpose-tree {
      |  display: grid;
      |  gap: 0.65rem;
      |  margin-top: 0.85rem;
      |}
      |
      |.bok-purpose-goal {
      |  position: relative;
      |  padding: 0.72rem 0.78rem 0.72rem 0.95rem;
      |  background: rgba(255, 255, 255, 0.12);
      |  border: 1px solid rgba(255, 255, 255, 0.16);
      |  border-radius: 16px;
      |}
      |
      |.bok-purpose-goal::before {
      |  content: "";
      |  position: absolute;
      |  left: 0.43rem;
      |  top: 1.05rem;
      |  bottom: 0.78rem;
      |  width: 2px;
      |  background: rgba(255, 255, 255, 0.28);
      |}
      |
      |.bok-purpose-goal-head {
      |  display: flex;
      |  align-items: flex-start;
      |  gap: 0.55rem;
      |}
      |
      |.bok-purpose-goal-head strong {
      |  color: #fff;
      |  line-height: 1.45;
      |}
      |
      |.bok-purpose-node-label {
      |  display: inline-flex;
      |  flex: 0 0 auto;
      |  align-items: center;
      |  justify-content: center;
      |  min-width: 1.7rem;
      |  height: 1.35rem;
      |  color: #fff;
      |  background: rgba(255, 255, 255, 0.20);
      |  border-radius: 999px;
      |  font-size: 0.68rem;
      |  font-weight: 900;
      |  letter-spacing: 0.04em;
      |}
      |
      |.bok-purpose-subgoals {
      |  display: grid;
      |  gap: 0.42rem;
      |  margin: 0.55rem 0 0 2.25rem;
      |  padding: 0;
      |  list-style: none;
      |}
      |
      |.bok-purpose-subgoals li {
      |  position: relative;
      |  display: flex;
      |  align-items: flex-start;
      |  gap: 0.5rem;
      |  color: #dff7f2;
      |  line-height: 1.45;
      |}
      |
      |.bok-purpose-subgoals li::before {
      |  content: "";
      |  position: absolute;
      |  left: -1.35rem;
      |  top: 0.68rem;
      |  width: 1.05rem;
      |  height: 2px;
      |  background: rgba(255, 255, 255, 0.28);
      |}
      |
      |.bok-purpose-unassigned {
      |  margin-top: 0.65rem;
      |  padding: 0.65rem 0.75rem;
      |  background: rgba(255, 255, 255, 0.10);
      |  border: 1px dashed rgba(255, 255, 255, 0.30);
      |  border-radius: 14px;
      |}
      |
      |.bok-purpose-unassigned > strong {
      |  color: #fff;
      |}
      |
      |.body-dashboard .toolbar {
      |  display: none !important;
      |}
      |
      |.body.body-dashboard {
      |  min-height: calc(100vh - 3.5rem);
      |  background: #0b1220 !important;
      |  background-image: radial-gradient(circle at 10% 0, rgba(34, 197, 94, 0.22), transparent 24rem), radial-gradient(circle at 86% 10%, rgba(59, 130, 246, 0.28), transparent 28rem), linear-gradient(135deg, #0b1220 0, #111827 48%, #172554 100%) !important;
      |}
      |
      |.body-dashboard .doc {
      |  color: #dbeafe;
      |}
      |
      |.bok-dashboard-shell strong,
      |.bok-dashboard-shell b,
      |.bok-dashboard-shell em {
      |  text-decoration: none !important;
      |}
      |
      |.bok-dashboard-hero {
      |  border: 1px solid rgba(148, 163, 184, 0.28);
      |  background: linear-gradient(135deg, rgba(15, 23, 42, 0.94), rgba(30, 64, 175, 0.78) 55%, rgba(20, 83, 45, 0.82)) !important;
      |  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.42), 0 0 0 1px rgba(255, 255, 255, 0.06) inset;
      |}
      |
      |.bok-dashboard-hero-fact {
      |  min-height: 8.2rem;
      |  background: linear-gradient(180deg, rgba(255, 255, 255, 0.18), rgba(255, 255, 255, 0.08));
      |  box-shadow: 0 18px 36px rgba(0, 0, 0, 0.22), 0 1px 0 rgba(255, 255, 255, 0.18) inset;
      |}
      |
      |.bok-dashboard-hero-fact strong {
      |  font-size: 3rem;
      |  letter-spacing: -0.08em;
      |}
      |
      |.bok-dashboard > .row {
      |  --bs-gutter-x: 1.35rem;
      |  --bs-gutter-y: 1.35rem;
      |}
      |
      |.bok-card {
      |  background: #f8fafc !important;
      |  border: 1px solid rgba(148, 163, 184, 0.20) !important;
      |  border-radius: 26px !important;
      |  box-shadow: 0 22px 56px rgba(0, 0, 0, 0.28) !important;
      |}
      |
      |.bok-card::before {
      |  width: 100% !important;
      |  height: 0.42rem !important;
      |  inset: 0 0 auto 0 !important;
      |}
      |
      |.bok-card-purpose {
      |  background: linear-gradient(135deg, #0f766e 0, #164e63 50%, #1e3a8a 100%) !important;
      |}
      |
      |.bok-card-kpi .card-body {
      |  min-height: 10.5rem !important;
      |}
      |
      |.bok-kpi-value {
      |  color: #0f172a !important;
      |  font-size: 3.45rem !important;
      |}
      |""".stripMargin


  private def _project(parsed: ParsedArgs): Path =
    _resolve_bok_project(parsed.pathProperty("project-dir").
      orElse(parsed.pathProperty("project")).
      orElse(parsed.argument("project").map(_to_path)).
      getOrElse(_logical_cwd))

  private def _category_project(parsed: ParsedArgs): Path =
    _resolve_bok_project(parsed.pathProperty("project-dir").
      orElse(parsed.pathProperty("project")).
      getOrElse(_logical_cwd))

  private def _resolve_bok_project(input: Path): Path =
    _find_bok_root(input).getOrElse(input.toAbsolutePath.normalize)

  private def _find_bok_root(input: Path): Option[Path] = {
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

  private def _load_config(project: Path): CozyProjectYamlConfig.Config =
    CozyProjectYamlConfig.loadOperationDefaults(project)

  private def _load_site_config(source: Path): SiteConfig = {
    val file = source.resolve("site.conf")
    if (!Files.isRegularFile(file))
      SiteConfig.empty
    else {
      val lines = Files.readAllLines(file, StandardCharsets.UTF_8).asScala.toVector
      SiteConfig(_parse_site_values(lines), _parse_site_lists(lines), _parse_site_goal_trees(lines))
    }
  }

  private def _to_path(value: Any): Path = value match {
    case m: java.io.File => m.toPath.toAbsolutePath.normalize
    case m: Path => m.toAbsolutePath.normalize
    case m =>
      val path = Paths.get(m.toString)
      if (path.isAbsolute)
        path.normalize
      else
        _logical_cwd.resolve(path).normalize
  }

  private def _logical_cwd: Path =
    sys.env.get("PWD").map(Paths.get(_).toAbsolutePath.normalize).getOrElse(Paths.get(".").toAbsolutePath.normalize)

  private def _boolean(config: CozyProjectYamlConfig.Config, path: String, default: Boolean): Boolean =
    config.boolean(path).getOrElse(default)

  private def _strategy(parsed: ParsedArgs): String =
    _strategy(parsed, "wip")

  private def _strategy(parsed: ParsedArgs, default: String): String =
    parsed.property("strategy").getOrElse(default) match {
      case "wip" => "work-in-progress"
      case "draft" => "draft"
      case "preview" => "production-preview"
      case "production" => "production"
      case other => other
    }

  private def _publication_settings(
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

  private def _publication_artifact_roots(
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

  private def _repository_under_warehouse(warehouse: String): String =
    Paths.get(warehouse).resolve("repository").toString

  private val _generated_gitignore_entries = Vector(
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
  private val _dox_metadata_section_names = Set("HEAD", "HEADLINE", "BRIEF", "SUMMARY", "DESCRIPTION", "LEAD", "ABSTRACT", "REMARKS", "TOOLTIP")

  private def _inspect_bok(input: Path): BokInspection = {
    val root = _find_bok_root(input)
    val markers = root.map(_bok_markers).getOrElse(Vector.empty)
    val issues = root.map(_bok_issues).getOrElse(Vector(s"BoK root was not found from ${input.toAbsolutePath.normalize}"))
    val fixes = root.map(_bok_fixes).getOrElse(Vector.empty)
    BokInspection(input.toAbsolutePath.normalize, root, markers, issues, fixes)
  }

  private def _bok_markers(root: Path): Vector[String] =
    Vector(
      root.resolve(".cozy/config.yaml"),
      root.resolve(".cozy/config.yml"),
      root.resolve(".cozy/config.json"),
      root.resolve(".cozy/config.conf"),
      root.resolve("conf/cozy/config.yaml"),
      root.resolve("conf/cozy/config.yml"),
      root.resolve("conf/cozy/config.json"),
      root.resolve("conf/cozy/config.conf"),
      root.resolve("src/main/doxsite"),
      root.resolve("src/main/doxsite/site.conf"),
      root.resolve("src/main/publication")
    ).filter(p => Files.exists(p)).map(p => root.relativize(p).toString)

  private def _bok_issues(root: Path): Vector[String] = {
    val configfiles = CozyProjectYamlConfig.operationDefaultFiles(root).filter(x => Files.isRegularFile(x))
    val config = _load_config(root)
    val missingconfig =
      if (configfiles.isEmpty) Vector("Missing BoK config: .cozy/config.yaml or conf/cozy/config.yaml") else Vector.empty
    val olddocker =
      if (configfiles.exists(p => _read_text(p).contains("simplemodeling/cozy-toolchain:latest")))
        Vector("Legacy Docker image reference found: simplemodeling/cozy-toolchain:latest")
      else
        Vector.empty
    val missingsource =
      if (!Files.isDirectory(root.resolve("src/main/doxsite")))
        Vector("Missing BoK source directory: src/main/doxsite")
      else
        Vector.empty
    val missingsite =
      if (!Files.isRegularFile(root.resolve("src/main/doxsite/site.conf")))
        Vector("Missing BoK site config: src/main/doxsite/site.conf")
      else
        Vector.empty
    val missinggitignore = _missing_gitignore_entries(root)
    val gitignoreissue =
      if (missinggitignore.nonEmpty)
        Vector(s"Generated/work directories are not fully ignored: ${missinggitignore.mkString(", ")}")
      else
        Vector.empty
    val missingupload =
      if (config.value("bok.workflow.upload.command").isEmpty)
        Vector("Missing upload workflow command: bok.workflow.upload.command")
      else
        Vector.empty
    val doxissues = _dox_metadata_section_issues(root).map { issue =>
      s"SmartDox Dox metadata section heading must be followed by a blank line: ${root.relativize(issue.path)}:${issue.line} ${issue.heading}"
    }
    val markdownissues = _markdown_metadata_issues(root).map { issue =>
      s"Markdown front matter metadata issue: ${root.relativize(issue.path)}: ${issue.message}"
    }
    missingconfig ++ olddocker ++ missingsource ++ missingsite ++ gitignoreissue ++ missingupload ++ doxissues ++ markdownissues
  }

  private def _bok_fixes(root: Path): Vector[BokFix] = {
    val configfiles = CozyProjectYamlConfig.operationDefaultFiles(root).filter(x => Files.isRegularFile(x))
    val createconfig =
      if (configfiles.isEmpty)
        Vector(BokFix("Create .cozy/config.yaml with current BoK defaults", () => _write_text(root.resolve(".cozy/config.yaml"), _cozy_config())))
      else
        Vector.empty
    val updatedocker =
      configfiles.filter(p => _read_text(p).contains("simplemodeling/cozy-toolchain:latest")).map { path =>
        BokFix(s"Replace legacy Docker image in ${root.relativize(path)}", () => {
          val current = _read_text(path)
          _write_text(path, current.replace("simplemodeling/cozy-toolchain:latest", _default_docker_image))
        })
      }.toVector
    val gitignoreentries = _missing_gitignore_entries(root)
    val gitignorefix =
      if (gitignoreentries.nonEmpty)
        Vector(BokFix("Append generated/work directory ignores to .gitignore", () => _append_gitignore_entries(root.resolve(".gitignore"), gitignoreentries)))
      else
        Vector.empty
    val doxfixes = _dox_metadata_section_issues(root).groupBy(_.path).toVector.sortBy(_._1.toString).map {
      case (path, issues) =>
        val description =
          if (issues.size == 1)
            s"Insert blank line after SmartDox metadata section heading in ${root.relativize(path)}:${issues.head.line}"
          else
            s"Insert blank lines after ${issues.size} SmartDox metadata section headings in ${root.relativize(path)}"
        BokFix(description, () => _fix_dox_metadata_section_spacing(path))
    }
    createconfig ++ updatedocker ++ gitignorefix ++ doxfixes
  }

  private def _dox_metadata_section_issues(root: Path): Vector[DoxMetadataSectionIssue] = {
    val source = root.resolve("src/main/doxsite")
    if (!Files.isDirectory(source))
      Vector.empty
    else {
      val stream = Files.walk(source)
      try {
        stream.iterator.asScala.toVector.
          filter(path => Files.isRegularFile(path) && path.getFileName.toString.endsWith(".dox")).
          flatMap(_dox_metadata_section_issues_in_file)
      } finally {
        stream.close()
      }
    }
  }

  private def _dox_metadata_section_issues_in_file(path: Path): Vector[DoxMetadataSectionIssue] = {
    val lines = Files.readAllLines(path, StandardCharsets.UTF_8).asScala.toVector
    lines.zipWithIndex.flatMap {
      case (line, index) =>
        _dox_metadata_section_heading(line) match {
          case Some(heading) if index + 1 >= lines.length || lines(index + 1).trim.nonEmpty =>
            Some(DoxMetadataSectionIssue(path, index + 1, heading))
          case _ =>
            None
        }
    }
  }

  private def _dox_metadata_section_heading(line: String): Option[String] = {
    val trimmed = line.trim
    val markerlength = trimmed.takeWhile(c => c == '#' || c == '*').length
    if (markerlength > 0 && trimmed.length > markerlength && trimmed.charAt(markerlength) == ' ') {
      val name = trimmed.drop(markerlength + 1).trim
      if (_dox_metadata_section_names.contains(name))
        Some(trimmed)
      else
        None
    } else
      None
  }

  private def _markdown_metadata_issues(root: Path): Vector[MarkdownMetadataIssue] = {
    val source = root.resolve("src/main/doxsite")
    if (!Files.isDirectory(source))
      Vector.empty
    else {
      val stream = Files.walk(source)
      try {
        stream.iterator.asScala.toVector.
          filter(path => Files.isRegularFile(path) && _is_markdown_source_document(path)).
          flatMap(_markdown_metadata_issues_in_file)
      } finally {
        stream.close()
      }
    }
  }

  private def _markdown_metadata_issues_in_file(path: Path): Vector[MarkdownMetadataIssue] = {
    val lines = Files.readAllLines(path, StandardCharsets.UTF_8).asScala.toVector
    if (lines.headOption.exists(_.trim == "---")) {
      val end = lines.zipWithIndex.drop(1).find(_._1.trim == "---").map(_._2)
      end match {
        case Some(n) =>
          val keys = lines.slice(1, n).flatMap { line =>
            val trimmed = line.trim
            if (trimmed.startsWith("#") || !trimmed.contains(":"))
              None
            else
              Some(trimmed.takeWhile(_ != ':').trim.toLowerCase(java.util.Locale.ROOT))
          }.toSet
          val title = keys.contains("title") || keys.contains("headline")
          val brief = keys.contains("brief") || keys.contains("summary") || keys.contains("description")
          Vector(
            if (title) None else Some(MarkdownMetadataIssue(path, "front matter should include title or headline")),
            if (brief) None else Some(MarkdownMetadataIssue(path, "front matter should include brief, summary, or description"))
          ).flatten
        case None =>
          Vector(MarkdownMetadataIssue(path, "front matter starts with --- but has no closing ---"))
      }
    } else {
      Vector.empty
    }
  }

  private def _fix_dox_metadata_section_spacing(path: Path): Unit = {
    val original = Files.readAllLines(path, StandardCharsets.UTF_8).asScala.toVector
    val fixed = original.zipWithIndex.flatMap {
      case (line, index) =>
        val needsblank = _dox_metadata_section_heading(line).isDefined &&
          (index + 1 >= original.length || original(index + 1).trim.nonEmpty)
        if (needsblank)
          Vector(line, "")
        else
          Vector(line)
    }
    _write_text(path, fixed.mkString("\n") + "\n")
  }

  private val _bok_guide_scenarios: Vector[(String, String, Vector[String])] = Vector(
    (
      "create-bok",
      "Create a new BoK project",
      Vector(
        "1. cozy bok create --save <project-dir> --name <name> --url <site-url> --language ja",
        "2. cd <project-dir>",
        "3. edit src/main/doxsite/site.conf and category sources",
        "4. define BoK Vision, Goals, and Subgoals in src/main/doxsite/site.conf",
        "5. define category Vision, Goals, and Subgoals in category.yaml when needed",
        "6. cozy bok doctor",
        "7. cozy bok build"
      )
    ),
    (
      "daily-build",
      "Inspect and build an existing BoK",
      Vector(
        "1. cd <project-dir>",
        "2. cozy bok doctor",
        "3. cozy bok fix --dry-run",
        "4. update site.conf/category.yaml Vision, Goals, and Subgoals if operational intent changed",
        "5. cozy bok build --strategy preview",
        "6. use cozy bok build --strategy preview --no-bib-service for offline/cache-only bibliography builds",
        s"7. cozy bok preview --port ${_default_preview_port}",
        s"8. open http://127.0.0.1:${_default_preview_port}/ in a browser; use the local Web server instead of opening website.d directly"
      )
    ),
    (
      "publish-dry-run",
      "Verify publication without side effects",
      Vector(
        "1. cd <project-dir>",
        "2. configure bok.workflow.upload.command in .cozy/config.yaml or conf/cozy/config.yaml",
        "3. cozy bok publish . --dry-run",
        "4. inspect target/cozy-bok/publish/latest/manifest.json",
        "5. run cozy bok publish . only after the plan is correct"
      )
    ),
    (
      "video-publication",
      "Publish .video packages into the BoK registry",
      Vector(
        "1. create src/main/doxsite/<category>/<slug>.video/index.dox",
        "2. create src/main/doxsite/<category>/<slug>.video/video.yaml",
        "3. keep generated mp4/rdf/captions outside the .video source package",
        "4. cozy bok publish-video .",
        "5. cozy bok build --strategy preview"
      )
    ),
    (
      "stage-upload",
      "Connect project-owned staging and upload scripts",
      Vector(
        "1. copy etc/website-stage.sh.proto to etc/website-stage.sh when staging is needed",
        "2. copy etc/website-upload.sh.proto to etc/website-upload.sh and edit the target",
        "3. configure bok.workflow.stage.command and bok.workflow.upload.command",
        "4. cozy bok stage .",
        "5. cozy bok upload ."
      )
    )
  )

  private def _print_bok_guide_scenario(name: String, title: String, lines: Vector[String]): Unit = {
    println(s"Cozy BoK guide: ${name}")
    println(title)
    lines.foreach(line => println(s"  ${line}"))
  }

  private def _missing_gitignore_entries(root: Path): Vector[String] = {
    val path = root.resolve(".gitignore")
    val existing =
      if (Files.isRegularFile(path))
        Files.readAllLines(path, StandardCharsets.UTF_8).asScala.map(_.trim).filter(_.nonEmpty).toSet
      else
        Set.empty[String]
    _generated_gitignore_entries.filterNot(existing.contains)
  }

  private def _append_gitignore_entries(path: Path, entries: Vector[String]): Unit = {
    val current = if (Files.isRegularFile(path)) _read_text(path) else ""
    val separator = if (current.isEmpty || current.endsWith("\n")) "" else "\n"
    _write_text(path, current + separator + entries.mkString("\n") + "\n")
  }

  private def _print_bok_inspection(inspection: BokInspection, config: DoctorConfig): Unit = {
    println("bok doctor")
    println(s"input: ${inspection.input}")
    println(s"status: ${inspection.status}")
    inspection.root match {
      case Some(root) => println(s"root: ${root}")
      case None => println("root: <not found>")
    }
    _print_list("bok root markers", inspection.markers)
    _print_list("issues", inspection.issues)
    _print_list(if (config.fix && config.dryRun) "planned fixes" else "fixes", inspection.fixes.map(_.description))
    _print_list("next steps", _bok_next_steps(inspection))
  }

  private def _bok_next_steps(inspection: BokInspection): Vector[String] =
    inspection.root.toVector.flatMap { root =>
      val port = _bok_preview_port(root)
      Vector(
        "Build generated site from the BoK root: cozy bok build --strategy preview",
        "Build generated site from another directory: cozy bok build <bok-root> --strategy preview",
        s"Serve website.d from the BoK root: cozy bok preview --port ${port}",
        s"Serve website.d from another directory: cozy bok preview <bok-root> --port ${port}",
        s"Open http://127.0.0.1:${port}/ in a browser; use the local Web server instead of opening generated HTML directly",
        "Strategy states: draft -> wip (work-in-progress) -> preview -> production/publish",
        "Strategy meaning: draft/wip are authoring states, preview is local/site verification, production is publish/upload readiness"
      )
    }

  private def _bok_preview_port(root: Path): String =
    _load_config(root).value("bok.preview.port").getOrElse(_default_preview_port)

  private def _print_list(label: String, values: Vector[String]): Unit = {
    println(s"${label}:")
    if (values.isEmpty)
      println("  - none")
    else
      values.foreach(x => println(s"  - ${x}"))
  }

  private def _apply_bok_fixes(inspection: BokInspection, config: DoctorConfig): Unit =
    inspection.root match {
      case None =>
        RAISE.invalidArgumentFault(s"Cannot fix because BoK root was not found from ${inspection.input}")
      case Some(_) if config.dryRun =>
        println("fix mode: dry-run")
      case Some(_) =>
        inspection.fixes.foreach(_.apply())
        println(s"applied fixes: ${inspection.fixes.length}")
    }

  private def _split_command(value: String): Vector[String] =
    value.trim.split("\\s+").toVector.filter(_.nonEmpty)

  private def _languages(config: CozyProjectYamlConfig.Config): Vector[String] =
    config.list("site.metadata.in_language") match {
      case xs if xs.nonEmpty => xs
      case _ =>
        config.value("site.metadata.in_language").map { x =>
          x.stripPrefix("[").stripSuffix("]").split(",").toVector.map(_.trim.stripPrefix("\"").stripSuffix("\"")).filter(_.nonEmpty)
        }.getOrElse(Vector("ja", "en"))
    }

  private def _languages(config: CozyProjectYamlConfig.Config, site: SiteConfig): Vector[String] =
    _languages(config) match {
      case xs if xs != Vector("ja", "en") => xs
      case _ =>
        site.list("site.metadata.in_language") match {
          case xs if xs.nonEmpty => xs
          case _ => site.value("site.metadata.in_language").map(_parse_inline_list).filter(_.nonEmpty).getOrElse(Vector("ja", "en"))
        }
    }

  private def _locale_mode(config: CozyProjectYamlConfig.Config, site: SiteConfig): LocaleMode =
    LocaleMode.create(
      config.value("site.output.locale_mode").
        orElse(config.value("bok.output.locale_mode")).
        orElse(site.value("site.output.locale_mode")).
        getOrElse {
          if (site.boolean("simplemodelingorg").getOrElse(false))
            "multi_locale_subdirs"
          else
            "single_locale_root"
        }
    )

  private def _default_locale(
    config: CozyProjectYamlConfig.Config,
    site: SiteConfig,
    languages: Vector[String]
  ): String =
    config.value("site.output.default_locale").
      orElse(config.value("bok.output.default_locale")).
      orElse(site.value("site.output.default_locale")).
      getOrElse(languages.headOption.getOrElse("ja"))

  private def _direct_assets(project: Path, config: CozyProjectYamlConfig.Config): DirectAssetsConfig = {
    val enabled = _boolean(config, "bok.direct-assets.enabled", false)
    val pairs = config.mapUnder("bok.direct-assets.items").toVector.sortBy(_._1)
    val grouped = pairs.groupBy(_._1.takeWhile(_ != '.')).toVector.sortBy(_._1).flatMap {
      case (_, kvs) =>
        val m = kvs.map { case (k, v) => k.dropWhile(_ != '.').drop(1) -> v }.toMap
        for {
          source <- m.get("source")
          dest <- m.get("destination")
        } yield DirectAsset(source, dest)
    }
    DirectAssetsConfig(enabled, if (grouped.nonEmpty) grouped else _direct_asset_list_items(project))
  }

  private def _direct_asset_list_items(project: Path): Vector[DirectAsset] = {
    CozyProjectYamlConfig.operationDefaultFiles(project).flatMap(_direct_asset_list_items_in_file)
  }

  private def _direct_asset_list_items_in_file(file: Path): Vector[DirectAsset] = {
    if (!Files.isRegularFile(file))
      Vector.empty
    else {
      var initems = false
      var itemsindent = -1
      var current = Map.empty[String, String]
      var items = Vector.empty[DirectAsset]
      var stack = Vector.empty[(Int, String)]

      def _flush_(): Unit = {
        for {
          source <- current.get("source")
          dest <- current.get("destination")
        } items = items :+ DirectAsset(source, dest)
        current = Map.empty
      }

      Files.readAllLines(file, StandardCharsets.UTF_8).asScala.foreach { raw =>
        val line = raw.takeWhile(_ != '#')
        val indent = line.takeWhile(_ == ' ').length
        val trimmed = line.trim
        if (trimmed.nonEmpty && !trimmed.startsWith("-")) {
          val n = trimmed.indexOf(':')
          if (n >= 0) {
            val key = trimmed.substring(0, n).trim
            stack = stack.dropRight(stack.reverse.takeWhile(_._1 >= indent).length) :+ (indent -> key)
          }
        }
        val path = stack.map(_._2).mkString(".")
        if (trimmed == "items:" && path == "bok.direct-assets.items") {
          initems = true
          itemsindent = indent
        } else if (initems && indent <= itemsindent && trimmed.nonEmpty && !trimmed.startsWith("-")) {
          _flush_()
          initems = false
        } else if (initems && trimmed.startsWith("- ")) {
          _flush_()
          _parse_key_value(trimmed.substring(2)).foreach { case (k, v) => current = current.updated(k, v) }
        } else if (initems) {
          _parse_key_value(trimmed).foreach { case (k, v) => current = current.updated(k, v) }
        }
      }
      _flush_()
      items
    }
  }

  private def _parse_key_value(value: String): Option[(String, String)] = {
    val n = value.indexOf(':')
    if (n < 0)
      None
    else {
      val key = value.substring(0, n).trim
      val v = _unquote(value.substring(n + 1).trim)
      if (key.isEmpty || v.isEmpty) None else Some(key -> v)
    }
  }

  private val _assignment: Regex = """^\s*([A-Za-z0-9_.-]+)\s*[:=]\s*(.+?)\s*$""".r

  private def _parse_site_values(lines: Vector[String]): Map[String, String] = {
    var stack = Vector.empty[(Int, String)]
    var values = Map.empty[String, String]
    lines.foreach { raw =>
      val line = raw.takeWhile(_ != '#')
      val trimmed = line.trim
      if (trimmed.nonEmpty && trimmed != "}" && !trimmed.startsWith("[") && !trimmed.startsWith("-")) {
        val indent = line.takeWhile(_.isWhitespace).length
        if (trimmed.endsWith("{")) {
          val key = trimmed.dropRight(1).trim
          stack = stack.dropRight(stack.reverse.takeWhile(_._1 >= indent).length) :+ (indent -> key)
        } else trimmed match {
          case _assignment(key, value) =>
            stack = stack.dropRight(stack.reverse.takeWhile(_._1 >= indent).length)
            val path = (stack.map(_._2) :+ key).mkString(".")
            values = values.updated(path, _unquote(value.stripSuffix(",")))
          case _ =>
        }
      }
    }
    values
  }

  private def _parse_site_lists(lines: Vector[String]): Map[String, Vector[String]] =
    _parse_site_values(lines).collect {
      case (key, value) if value.startsWith("[") && value.endsWith("]") =>
        key -> _parse_inline_list(value)
    } ++ _parse_site_block_lists(lines)

  private def _parse_site_goal_trees(lines: Vector[String]): Map[String, Vector[BokGoal]] = {
    val candidates = Set("site.metadata.goals", "site.metadata.goal_tree")
    var stack = Vector.empty[(Int, String)]
    var result = Map.empty[String, Vector[BokGoal]]
    var collecting: Option[(Int, String, Vector[BokGoal], Option[BokGoal], Boolean, Vector[String])] = None

    def flushCurrent(path: String, goals: Vector[BokGoal], current: Option[BokGoal]): Vector[BokGoal] =
      current.filterNot(_.isEmpty).map(goals :+ _).getOrElse(goals)

    lines.foreach { raw =>
      val line = raw.takeWhile(_ != '#')
      val trimmed = line.trim.stripSuffix(",")
      val indent = line.takeWhile(_.isWhitespace).length
      collecting match {
        case Some((baseindent, path, goals, current, subcollecting, subitems)) =>
          if (subcollecting) {
            if (trimmed == "]") {
              val updated = current.map(g => g.copy(subgoals = g.subgoals ++ subitems))
              collecting = Some((baseindent, path, goals, updated, false, Vector.empty))
            } else if (trimmed.startsWith("\"") || trimmed.startsWith("'")) {
              collecting = Some((baseindent, path, goals, current, true, subitems :+ _unquote(trimmed)))
            }
          } else if (trimmed == "]" && indent <= baseindent) {
            result = result.updated(path, flushCurrent(path, goals, current))
            collecting = None
          } else if (trimmed == "{" || trimmed == "{") {
            collecting = Some((baseindent, path, flushCurrent(path, goals, current), Some(BokGoal("", Vector.empty)), false, Vector.empty))
          } else if (trimmed == "}" || trimmed == "},") {
            collecting = Some((baseindent, path, flushCurrent(path, goals, current), None, false, Vector.empty))
          } else trimmed match {
            case _assignment(key, value) if key == "title" || key == "goal" || key == "name" =>
              val updated = current.map(_.copy(title = _unquote(value.stripSuffix(",")))).orElse(Some(BokGoal(_unquote(value.stripSuffix(",")), Vector.empty)))
              collecting = Some((baseindent, path, goals, updated, false, Vector.empty))
            case _assignment(key, value) if key == "subgoals" && value.startsWith("[") && value.endsWith("]") =>
              val updated = current.map(g => g.copy(subgoals = g.subgoals ++ _parse_inline_list(value)))
              collecting = Some((baseindent, path, goals, updated, false, Vector.empty))
            case _assignment(key, value) if key == "subgoals" && value == "[" =>
              collecting = Some((baseindent, path, goals, current, true, Vector.empty))
            case _ =>
          }
        case None =>
          if (trimmed.nonEmpty && trimmed != "}") {
            if (trimmed.endsWith("{")) {
              val key = trimmed.dropRight(1).trim
              stack = stack.dropRight(stack.reverse.takeWhile(_._1 >= indent).length) :+ (indent -> key)
            } else trimmed match {
              case _assignment(key, value) if value == "[" =>
                stack = stack.dropRight(stack.reverse.takeWhile(_._1 >= indent).length)
                val path = (stack.map(_._2) :+ key).mkString(".")
                if (candidates.contains(path))
                  collecting = Some((indent, path, Vector.empty, None, false, Vector.empty))
              case _ =>
            }
          }
      }
    }
    collecting.foreach {
      case (_, path, goals, current, _, _) =>
        result = result.updated(path, flushCurrent(path, goals, current))
    }
    result.map { case (k, v) => k -> v.filterNot(_.isEmpty) }.filter(_._2.nonEmpty)
  }

  private def _parse_site_block_lists(lines: Vector[String]): Map[String, Vector[String]] = {
    var stack = Vector.empty[(Int, String)]
    var lists = Map.empty[String, Vector[String]]
    var collecting: Option[(Int, String, Vector[String])] = None
    lines.foreach { raw =>
      val line = raw.takeWhile(_ != '#')
      val trimmed = line.trim
      collecting match {
        case Some((baseindent, path, items)) =>
          if (trimmed == "]") {
            lists = lists.updated(path, items)
            collecting = None
          } else if (trimmed.startsWith("\"") || trimmed.startsWith("'") || trimmed.endsWith(",")) {
            val item = _unquote(trimmed.stripSuffix(","))
            if (item.nonEmpty)
              collecting = Some((baseindent, path, items :+ item))
          } else if (trimmed.nonEmpty && line.takeWhile(_.isWhitespace).length <= baseindent) {
            lists = lists.updated(path, items)
            collecting = None
          }
        case None =>
          if (trimmed.nonEmpty && trimmed != "}") {
            val indent = line.takeWhile(_.isWhitespace).length
            if (trimmed.endsWith("{")) {
              val key = trimmed.dropRight(1).trim
              stack = stack.dropRight(stack.reverse.takeWhile(_._1 >= indent).length) :+ (indent -> key)
            } else trimmed match {
              case _assignment(key, value) if value == "[" =>
                stack = stack.dropRight(stack.reverse.takeWhile(_._1 >= indent).length)
                val path = (stack.map(_._2) :+ key).mkString(".")
                collecting = Some((indent, path, Vector.empty))
              case _ =>
            }
          }
      }
    }
    collecting.foreach { case (_, path, items) =>
      lists = lists.updated(path, items)
    }
    lists
  }

  private def _parse_inline_list(value: String): Vector[String] =
    value.stripPrefix("[").stripSuffix("]").split(",").toVector.map(x => _unquote(x.trim)).filter(_.nonEmpty)

  private def _unquote(value: String): String = {
    val s = value.trim
    if (s.length >= 2 && ((s.head == '"' && s.last == '"') || (s.head == '\'' && s.last == '\'')))
      s.substring(1, s.length - 1)
    else
      s
  }

  private def _cozy_config(): String =
    _cozy_config(None)

  private def _cozy_config(config: Option[CreateConfig]): String =
    s"""cozy:
       |  docker-image: ${_default_docker_image}
       |
       |bok:
       |  source: src/main/doxsite
       |  publication: src/main/publication
       |  repository: repository
       |  strategy: production
       |  website: website.d
       |  website-staging: ${config.map(_default_website_staging).getOrElse("../website-staging")}
       |  backup:
       |    enabled: false
       |    dir: website.backup
       |    compressed: true
       |  antora: antora.d
       |  doxsite: doxsite.d
       |  ui-bundle: src/main/antora-ui/build/ui-bundle.zip
       |  rdf:
       |    merge-publication-artifacts: true
       |  video:
       |    enabled: true
       |    force: false
       |  arcadia:
       |    enabled: false
       |    source: src/main/arcadiasite
       |  direct-assets:
       |    enabled: false
       |    items: []
       |  workflow:
       |    stage:
       |      # Copy etc/website-stage.sh.proto to etc/website-stage.sh and configure it.
       |      command: ""
       |    upload:
       |      # Copy etc/website-upload.sh.proto to etc/website-upload.sh and configure it.
       |      command: ""
       |""".stripMargin

  private def _readme(config: CreateConfig): String =
    s"""# ${config.name}
       |
       |This project is a SmartDox BoK source project for ${config.name}.
       |
       |- Operation URL: ${config.url}
       |- Main language: ${config.language}
       |- Site source: `src/main/doxsite`
       |- Generated public site files and publication repositories are intentionally outside this source scaffold.
       |
       |Category structure is defined by directories that contain `category.yaml`.
       |""".stripMargin

  private def _structure(config: CreateConfig): String =
    s"""# BoK Structure
       |
       |`src/main/doxsite` is the cozy-generated SmartDox source tree.
       |`site.conf` holds site metadata such as name, URL, language, license, navigation mode, and output mode.
       |
       |Directories with `category.yaml` are BoK categories.
       |`knowledgehub/` contains KnowledgeHub framework articles.
       |`book-knowledge/` contains Book/RDF/embedding articles.
       |`glossary/` contains terms used for automatic glossary linking.
       |`history/` contains operation history entries.
       |`manual/` contains Cozy BoK operation guidance.
       |`rdf/` contains minimal RDF and JSON-LD machine-readable placeholders.
       |`src/main/antora-ui/build/ui-bundle.zip` is a minimal local Antora UI bundle for offline BoK generation.
       |`assets/css/` contains restrained reading CSS only.
       |
       |Only source files, metadata, RDF seeds, glossary terms, and minimal CSS are generated here.
       |""".stripMargin

  private def _site_conf(config: CreateConfig): String =
    s"""site {
       |  metadata {
       |    name = "${config.name}"
       |    url = "${config.url}"
       |    in_language = ["${config.language}"]
       |    license = "CC-BY-SA-4.0"
       |    # Select one of: aurora, lagoon, meadow, ocean, ember, slate
       |    dashboard_color_group = "aurora"
       |    vision = "Build a shared knowledge base for ${config.name}."
       |    goals = [
       |      {
       |        title = "Organize concepts and technology knowledge"
       |        subgoals = ["Maintain category dashboards"]
       |      },
       |      {
       |        title = "Keep knowledge searchable and reusable"
       |        subgoals = ["Maintain glossary and history"]
       |      }
       |    ]
       |  }
       |  navigation {
       |    mode = "category"
       |  }
       |  output {
       |    locale_mode = "single_locale_root"
       |    default_locale = "${config.language}"
       |  }
       |  header {
       |    language_toggle = false
       |  }
       |}
       |
       |output.scope.policy = home_only
       |""".stripMargin

  private def _today: String =
    LocalDate.now.toString

  private def _site_index(config: CreateConfig): String =
    s"""Home
       |======
       |
       |${_dox_head(config.name, _site_index_brief(config))}
       |
       |# Overview
       |
       |${_site_index_overview(config)}
       |""".stripMargin

  private def _site_index_brief(config: CreateConfig): String =
    if (_is_japanese(config.language))
      s"${config.name} のカテゴリ、用語、RDFから知識を探索するための短い導入。"
    else
      s"A short introduction for exploring ${config.name} through categories, terms, and RDF."

  private def _site_index_overview(config: CreateConfig): String =
    if (_is_japanese(config.language))
      s"${config.name}は、カテゴリ、用語、RDFのつながりから知識を探索するためのBoKです。"
    else
      s"${config.name} is a BoK site for exploring knowledge through categories, terms, and RDF relationships."

  private def _is_japanese(language: String): Boolean =
    language.toLowerCase(Locale.ROOT).startsWith("ja")

  private def _category(
    name: String,
    title: String,
    description: String,
    purpose: BokPurpose = BokPurpose.empty
  ): String =
    s"""name: ${name}
       |title: ${title}
       |description:
       |  en: ${description}
       |  ja: ${description}
       |${_purpose_yaml(purpose)}
       |""".stripMargin

  private def _category_index(
    category: String,
    title: String,
    purpose: String,
    articles: Vector[CategoryArticle] = Vector.empty,
    terms: Vector[CategoryTerm] = Vector.empty
  ): String =
    s"""${title}
       |======
       |
       |${_dox_head(title, purpose)}
       |
       |# Overview
       |
       |${purpose}
       |""".stripMargin

  private def _dox_head(headline: String, brief: String): String =
    s"""# HEAD
       |
       |status=work-in-progress
       |published_at=${_today}
       |
       |${_dox_metadata_section("HEADLINE", headline)}
       |
       |${_dox_metadata_section("BRIEF", brief)}""".stripMargin

  private def _dox_metadata_section(name: String, body: String): String =
    s"""## ${name}
       |
       |${body}""".stripMargin

  private def _purpose_yaml(purpose: BokPurpose): String =
    if (purpose.isEmpty)
      ""
    else
      Vector(
        purpose.vision.map(x => s"vision: ${_yaml_quote(x)}"),
        if (purpose.goals.nonEmpty) Some(_yaml_goal_tree_block("goals", purpose.goals)) else None,
        if (purpose.flatGoals.nonEmpty) Some(_yaml_list_block("goals", purpose.flatGoals)) else None,
        if (purpose.flatSubgoals.nonEmpty) Some(_yaml_list_block("subgoals", purpose.flatSubgoals)) else None
      ).flatten.mkString("", "\n", "\n")

  private def _yaml_goal_tree_block(key: String, values: Vector[BokGoal]): String =
    values.filterNot(_.isEmpty).map { goal =>
      val subgoals =
        if (goal.subgoals.isEmpty)
          ""
        else
          goal.subgoals.map(x => s"      - ${_yaml_quote(x)}").mkString("\n    subgoals:\n", "\n", "")
      s"  - title: ${_yaml_quote(goal.title)}${subgoals}"
    }.mkString(s"${key}:\n", "\n", "")

  private def _yaml_list_block(key: String, values: Vector[String]): String =
    values.map(x => s"  - ${_yaml_quote(x)}").mkString(s"${key}:\n", "\n", "")

  private def _yaml_quote(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  private def _dashboard_bar_width(value: Int, total: Int): Int =
    if (total <= 0)
      8
    else
      math.max(8, math.round(value.toDouble / total.toDouble * 100.0).toInt)

  private def _glossary_index(): String =
    s"""用語集
      |======
      |
      |${_dox_head("用語集", "BoK全体で共有する用語と概念のDashboard。")}
      |
      |# Dashboard
      |
      |用語集はBoK全体の語彙、カテゴリ横断の概念、ドメイン固有語を集約します。
      |
      |## Quick Links
      |
      |- <a href="../index.html">BoK Home</a>
      |- <a href="../history/index.html">History</a>
      |
      |## Term Groups
      |
      |- Terms in categories: `glossary/<category>/` にあるカテゴリ別用語。
      |- Shared terms: この用語集直下に置くBoK横断用語。
      |
      |## Operation Notes
      |
      |用語はSmartDoxの自動リンクとRDF/JSON-LD連携の基盤です。新しいカテゴリ用語を追加した場合は、カテゴリトップページと用語集Dashboardの両方から辿れるようにします。
      |""".stripMargin

  private def _history_index(): String =
    s"""History
      |=======
      |
      |${_dox_head("History", "BoK運用、更新履歴、公開履歴のDashboard。")}
      |
      |# Dashboard
      |
      |HistoryはBoKの変更、公開、運用イベントを集約するページです。
      |
      |## Quick Links
      |
      |- <a href="../index.html">BoK Home</a>
      |- <a href="../glossary/index.html">Glossary</a>
      |
      |## Timeline
      |
      |- 2026: Cozy BoK source and site operations started.
      |
      |## Operation Notes
      |
      |公開、構成変更、カテゴリ追加、重要な用語変更はここに記録します。
      |""".stripMargin

  private def _manual_index(locale: String): String =
    if (_is_japanese(locale))
      s"""BoK Manual
         |==========
         |
         |${_dox_head("BoK Manual", "Cozy BoK source and site operation manual.")}
         |
         |# Dashboard
         |
         |このManualは、BoK標準運用の手順と責務分担をまとめる技術マニュアルです。プロジェクト固有のルールは Local Rules に記録します。
         |
         |## Quick Links
         |
         |- <a href="../index.html">BoK Home</a>
         |- <a href="../glossary/index.html">Glossary</a>
         |- <a href="../history/index.html">History</a>
         |- <a href="local-rules.html">Local Rules</a>
         |
         |## Basic Operations
         |
         |- `cozy bok create --save <dir>`: BoK source scaffoldを作成します。
         |- `cozy bok create-category <name> --project <dir>`: カテゴリDashboard、記事seed、用語seedを追加します。
         |- `cozy bok doctor <dir>`: BoK source treeを検査します。
         |- `cozy bok build <dir> --strategy preview`: SmartDox/Antoraを使って `website.d` を生成します。
         |- `cozy bok preview <dir> --port <port>`: 生成済み `website.d` をローカルWebサーバーで確認します。
         |- `cozy bok publish <dir> --dry-run`: 公開前の計画と副作用境界を確認します。
         |
         |## Source Document Formats
         |
         |BoKのsource文書形式はGitHub MarkdownとSmartDoxです。一般のKnowledge ContributorにはGitHub Markdownを推奨します。記事本文を通常のMarkdownとして書けるため、GitHub上の編集、レビュー、Pull Requestとの相性が良いからです。
         |
         |BoKのフル機能を使いたい上級者にはSmartDoxを推奨します。SmartDoxはBoK metadata、用語連携、RDF連携、SmartDox固有の構造化表現を扱えます。
         |
         |マルチリンガルBoKはSmartDoxのみを対象にします。GitHub Markdownは単一言語の通常記事向けとして扱います。
         |
         |## Glossary Term Classification
         |
         |用語分類は、未分類の候補を確認し、モノ/コトの補助線で整理した上で、BoK内での役割に基づいて用語タイプを決める作業です。
         |
         |1. 未分類の用語を確認します。
         |2. 記事、シナリオ、参考資料、Project/CML情報から、その語がBoK内で何を担うかを確認します。
         |3. 存在として扱う対象は、concept、entity、actor、role、resource、artifactから選びます。
         |4. 起きる、行う、変化する、制御する対象は、event、action、process、task、rule、state、scenarioから選びます。
         |5. 判定に迷う場合は未分類のまま残し、根拠記事や関連用語を先に整備します。
         |6. 分類後は用語ハブで定義、関連記事、RDF接続、Project/CML接続を確認します。
         |
         |## Actor Operations
         |
         |BoK運用では、Knowledge Contributor、BoK管理者、サイト管理者の責務を分けます。Knowledge Contributorは、ある知識のKnowledge OwnerとしてGit sourceを編集します。自分がownerではない知識への修正はPull Requestで提案します。BoK管理者とサイト管理者はPull Requestを境界にレビュー、merge、公開判断を行います。
         |
         |### 知識提供者 / Knowledge Contributor
         |
         |1. 自分がKnowledge Ownerである記事、用語、カテゴリ、RDF seedなどのGit sourceを編集します。
         |2. ownerではない知識への修正は、作業branchで差分を作りPull Requestとして提案します。
         |3. `cozy bok doctor` で構造、メタデータ、リンク、用語、RDFの基本品質を確認します。
         |4. `cozy bok build --strategy preview` と `cozy bok preview` でDashboard、Category Pages、Glossary、Term Hub、RDF Graph、Recent Changesを確認します。
         |5. Git commitし、作業branchをpushします。
         |6. 必要に応じてPull Requestを作成し、該当Knowledge OwnerまたはBoK管理者へレビューを依頼します。
         |
         |### BoK管理者 / BoK Manager
         |
         |1. Pull Requestを受け取り、内容、構造、用語、RDF、整合性をレビューします。
         |2. 必要に応じてPull Request上で修正依頼し、承認後にmainへmergeします。
         |3. `cozy bok publish --dry-run` で公開前のビルド、配備、検証計画を確認します。
         |4. 高品質で一貫性のある知識だけを公開フローへ渡します。
         |
         |### サイト管理者 / Site Administrator
         |
         |1. ホスティング環境、upload設定、secret、アクセス制御を管理します。
         |2. 公開対象の変更はPull Requestで確認できる状態を前提に、stage/upload workflowを運用します。
         |3. 監視、バックアップ、障害対応、キャッシュ削除などのサイト運用を担当します。
         |
         |## Source / Generated Boundary
         |
         |Knowledge Contributorが編集するのはGit sourceだけです。Knowledge Ownerである知識は直接保守し、ownerではない知識はPull Requestで提案します。
         |
         |### 編集するSource
         |
         |- `src/main/doxsite/**/*.dox`
         |- `src/main/doxsite/**/*.md`
         |- `src/main/doxsite/**/category.yaml`
         |- `src/main/doxsite/glossary/**/*.dox`
         |- `src/main/doxsite/rdf/**` when RDF seed is project-owned
         |
         |### 生成物は編集しない
         |
         |- `website.d/`
         |- `doxsite.d/`
         |- `antora.d/`
         |- `target/`
         |- `repository/`
         |
         |## Page Types
         |
         |- Home: BoK全体Dashboard。
         |- Category Dashboard: カテゴリ単位のKPI、記事、用語、運用メモ。
         |- Glossary: BoK全体の語彙Dashboard。
         |- History: BoK運用と公開履歴Dashboard。
         |- Manual: BoK運用手順の入口。
         |
         |## Operation Notes
         |
         |BoKの標準ページはDashboardとして扱い、通常記事とは異なる情報集約ページにします。Glossary、History、Manualはカテゴリ一覧ではなくBoK Consoleとして扱います。
         |Manualは運用手順ページなので、SmartDoxの自動用語リンク対象外です。
         |""".stripMargin
    else
      s"""BoK Manual
         |==========
         |
         |${_dox_head("BoK Manual", "Cozy BoK source and site operation manual.")}
         |
         |# Dashboard
         |
         |This manual describes the standard BoK operation workflow and responsibilities. Project-local rules belong to Local Rules.
         |
         |## Quick Links
         |
         |- <a href="../index.html">BoK Home</a>
         |- <a href="../glossary/index.html">Glossary</a>
         |- <a href="../history/index.html">History</a>
         |- <a href="local-rules.html">Local Rules</a>
         |
         |## Basic Operations
         |
         |- `cozy bok create --save <dir>`: Create a BoK source scaffold.
         |- `cozy bok create-category <name> --project <dir>`: Add a category dashboard, article seed, and term seed.
         |- `cozy bok doctor <dir>`: Inspect the BoK source tree.
         |- `cozy bok build <dir> --strategy preview`: Generate `website.d` through SmartDox and Antora.
         |- `cozy bok preview <dir> --port <port>`: Serve generated `website.d` through a local Web server.
         |- `cozy bok publish <dir> --dry-run`: Verify the publication plan and side-effect boundary.
         |
         |## Source Document Formats
         |
         |BoK source documents can be written in GitHub Markdown or SmartDox. GitHub Markdown is recommended for general Knowledge Contributors because it keeps ordinary article authoring close to GitHub editing, review, and Pull Request workflows.
         |
         |SmartDox is recommended for advanced contributors who need the full BoK feature set: BoK metadata, glossary linkage, RDF linkage, and SmartDox-specific structured authoring.
         |
         |Multilingual BoK authoring is SmartDox-only. GitHub Markdown is treated as the normal single-language article format.
         |
         |## Glossary Term Classification
         |
         |Term classification starts from unclassified candidates, uses mono/koto as an analysis aid, and then assigns a term type based on the term's role in the BoK knowledge space.
         |
         |1. Review unclassified terms.
         |2. Check articles, scenarios, bibliography, and Project/CML metadata to understand the term's role.
         |3. For existence-like terms, choose concept, entity, actor, role, resource, or artifact.
         |4. For terms that happen, are performed, change, or control decisions, choose event, action, process, task, rule, state, or scenario.
         |5. Keep uncertain candidates unclassified until evidence, related articles, or related terms are clearer.
         |6. After classification, verify definition, related articles, RDF links, and Project/CML links in the Term Hub.
         |
         |## Actor Operations
         |
         |BoK operation separates responsibilities among Knowledge Contributors, BoK Managers, and Site Administrators. A Knowledge Contributor is the Knowledge Owner for some knowledge. For knowledge they own, they edit Git source and push a branch. For knowledge they do not own, they propose changes through a Pull Request. BoK Managers and Site Administrators use Pull Requests as the review, merge, and publication decision boundary.
         |
         |### Knowledge Contributor
         |
         |1. Edit Git source for knowledge they own, such as articles, terms, categories, and RDF seeds.
         |2. For knowledge they do not own, prepare the change on a working branch and propose it through a Pull Request.
         |3. Run `cozy bok doctor` to check structure, metadata, links, terms, and RDF basics.
         |4. Run `cozy bok build --strategy preview` and `cozy bok preview` to verify dashboards, category pages, glossary, term hub, RDF graph, and recent changes.
         |5. Commit and push the working branch.
         |6. Open a Pull Request when ownership review, BoK Manager review, or publication review is needed.
         |
         |### BoK Manager
         |
         |1. Review Pull Requests for content, structure, terms, RDF, and consistency.
         |2. Request fixes in the Pull Request when needed, then merge approved changes into main.
         |3. Run `cozy bok publish --dry-run` to verify the publication plan.
         |4. Pass only consistent, high-quality knowledge to the publication flow.
         |
         |### Site Administrator
         |
         |1. Manage hosting, upload settings, secrets, and access control.
         |2. Operate stage/upload workflows after the publication change is reviewable through Pull Requests.
         |3. Handle monitoring, backups, incident response, and cache invalidation.
         |
         |## Source / Generated Boundary
         |
         |Knowledge Contributors edit Git source only. They maintain knowledge they own directly and propose changes to knowledge they do not own through Pull Requests.
         |
         |### Editable Source
         |
         |- `src/main/doxsite/**/*.dox`
         |- `src/main/doxsite/**/*.md`
         |- `src/main/doxsite/**/category.yaml`
         |- `src/main/doxsite/glossary/**/*.dox`
         |- `src/main/doxsite/rdf/**` when RDF seed is project-owned
         |
         |### Do Not Edit Generated Artifacts
         |
         |- `website.d/`
         |- `doxsite.d/`
         |- `antora.d/`
         |- `target/`
         |- `repository/`
         |
         |## Page Types
         |
         |- Home: whole-BoK dashboard.
         |- Category Dashboard: category-local KPI, articles, terms, and operation notes.
         |- Glossary: BoK-wide vocabulary dashboard.
         |- History: BoK operation and publication history dashboard.
         |- Manual: BoK operation entry point.
         |
         |## Operation Notes
         |
         |Standard BoK pages are dashboards, not ordinary articles. Glossary, History, and Manual are BoK console pages rather than normal categories.
         |Manual pages are excluded from automatic glossary linking.
         |""".stripMargin

  private def _manual_local_rules(config: CreateConfig): String =
    if (_is_japanese(config.language))
      s"""Local Rules
         |===========
         |
         |${_dox_head("Local Rules", s"${config.name} project-local BoK operation rules.")}
         |
         |# Dashboard
         |
         |このページは`${config.name}`固有の運用ルールを記録します。BoK標準運用は現在のCozy runtimeが持つ標準Manualから生成されます。
         |
         |## Project Scope
         |
         |- BoK name: `${config.name}`
         |- Source root: `src/main/doxsite`
         |- Generated outputs: `website.d`, `doxsite.d`, `antora.d`, `target`
         |
         |## Local Rules
         |
         |- このBoK固有のカテゴリ、レビュー基準、公開判断、アップロード手順をここに記録します。
         |- 機微情報やsecretは`.cozy/`または外部の安全な管理場所に置きます。
         |
         |## Upload And Publication
         |
         |- `cozy bok publish --dry-run`で公開計画を確認します。
         |- 実uploadはプロジェクト所有のworkflow scriptで行います。
         |""".stripMargin
    else
      s"""Local Rules
         |===========
         |
         |${_dox_head("Local Rules", s"${config.name} project-local BoK operation rules.")}
         |
         |# Dashboard
         |
         |This page records operation rules specific to `${config.name}`. The standard BoK workflow is generated from the current Cozy runtime manual.
         |
         |## Project Scope
         |
         |- BoK name: `${config.name}`
         |- Source root: `src/main/doxsite`
         |- Generated outputs: `website.d`, `doxsite.d`, `antora.d`, `target`
         |
         |## Local Rules
         |
         |- Record project-specific categories, review criteria, publication decisions, and upload procedures here.
         |- Keep sensitive values and secrets in `.cozy/` or another safe external location.
         |
         |## Upload And Publication
         |
         |- Run `cozy bok publish --dry-run` to verify the publication plan.
         |- Actual upload is handled by project-owned workflow scripts.
         |""".stripMargin

  private def _category_name(name: String): String =
    name.split("[^A-Za-z0-9]+").toVector.filter(_.nonEmpty).map { part =>
      part.head.toUpper + part.tail
    }.mkString match {
      case "" => "Category"
      case x => x
    }

  private def _article(title: String, purpose: String): String =
    s"""${title}
       |======
       |
       |${_dox_head(title, purpose)}
       |
       |# 目的
       |
       |${purpose}
       |
       |# 執筆メモ
       |
       |This is a cozy-generated BoK article seed.
       |""".stripMargin

  private def _glossary(title: String, definition: String, reading: Option[String]): String = {
    val headproperties =
      (Vector("status=work-in-progress", s"published_at=${_today}") ++ reading.toVector.map(x => s"reading=${x}")).mkString("\n")
    s"""${title}
       |======
       |
       |# HEAD
       |
       |${headproperties}
       |
       |# Definition
       |
       |${definition}
       |""".stripMargin
  }

  private def _site_ttl(config: CreateConfig): String =
    s"""@prefix schema: <https://schema.org/> .
       |@prefix kh: <${config.url}/rdf/ontology/knowledgehub#> .
       |
       |<${config.url}/>
       |  a schema:WebSite ;
       |  schema:name "${config.name}" ;
       |  schema:inLanguage "${config.language}" .
       |""".stripMargin

  private def _site_jsonld(config: CreateConfig): String =
    s"""{
       |  "@context": "https://schema.org",
       |  "@type": "WebSite",
       |  "name": "${config.name}",
       |  "url": "${config.url}",
       |  "inLanguage": "${config.language}"
       |}
       |""".stripMargin

  private def _schema_ttl(config: CreateConfig): String =
    s"""@prefix schema: <https://schema.org/> .
      |@prefix kh: <${config.url}/rdf/schema/knowledgehub#> .
      |
      |kh:KnowledgeItem a schema:DefinedTerm .
      |""".stripMargin

  private def _schema_jsonld(): String =
    """{
      |  "@context": "https://schema.org",
      |  "@type": "DefinedTermSet",
      |  "name": "KnowledgeHub Schema"
      |}
      |""".stripMargin

  private def _ontology_ttl(config: CreateConfig): String =
    s"""@prefix owl: <http://www.w3.org/2002/07/owl#> .
      |@prefix kh: <${config.url}/rdf/ontology/knowledgehub#> .
      |
      |kh:KnowledgeHubOntology a owl:Ontology .
      |""".stripMargin

  private def _ontology_jsonld(): String =
    """{
      |  "@context": {
      |    "owl": "http://www.w3.org/2002/07/owl#"
      |  },
      |  "@type": "owl:Ontology",
      |  "name": "KnowledgeHub Ontology"
      |}
      |""".stripMargin

  private def _css(): String =
    """body {
      |  line-height: 1.7;
      |}
      |
      |main {
      |  max-width: 78rem;
      |}
      |""".stripMargin

  private def _default_website_staging(config: CreateConfig): String = {
    val name = Option(config.save.getFileName).map(_.toString).filter(_.nonEmpty).getOrElse("bok")
    s"../${name}-website"
  }

  private def _website_stage_script(config: CreateConfig): String = {
    val staging = _default_website_staging(config)
    s"""#!/bin/sh
       |set -eu
       |
       |# Prototype staging workflow.
       |# Copy this file to etc/website-stage.sh, review the paths, and set it in
       |# bok.workflow.stage.command when the project needs a persistent published
       |# website working tree. Direct upload from website.d is usually simpler.
       |
       |PROJECT_DIR=$$(cd "$$(dirname "$$0")/.." && pwd)
       |cd "$$PROJECT_DIR"
       |
       |WEBSITE_BUILD_DIR=$${WEBSITE_SOURCE_DIR:-website.d}
       |REPOSITORY_SOURCE_DIR=$${REPOSITORY_SOURCE_DIR:-repository}
       |WEBSITE_STAGING_DIR=$${WEBSITE_STAGING_DIR:-$staging}
       |
       |if [ ! -d "$$WEBSITE_BUILD_DIR" ]; then
       |  echo "Website build directory is missing: $$WEBSITE_BUILD_DIR" >&2
       |  echo "Run: cozy bok build" >&2
       |  exit 2
       |fi
       |
       |mkdir -p "$$WEBSITE_STAGING_DIR"
       |rsync -av --checksum --delete "$$WEBSITE_BUILD_DIR"/ "$$WEBSITE_STAGING_DIR"/
       |
       |if [ -d "$$REPOSITORY_SOURCE_DIR" ]; then
       |  mkdir -p "$$WEBSITE_STAGING_DIR/repository"
       |  rsync -av --checksum "$$REPOSITORY_SOURCE_DIR"/ "$$WEBSITE_STAGING_DIR/repository"/
       |else
       |  echo "Repository source directory is not present; skipping artifact repository staging: $$REPOSITORY_SOURCE_DIR" >&2
       |fi
       |
       |if [ -d "$$WEBSITE_STAGING_DIR/.git" ]; then
       |  git -C "$$WEBSITE_STAGING_DIR" status --short
       |fi
       |""".stripMargin
  }

  private def _website_upload_script(config: CreateConfig): String =
    """#!/bin/sh
      |set -eu
      |
      |# Prototype AWS S3 upload workflow.
      |# Copy this file to etc/website-upload.sh and set it in
      |# bok.workflow.upload.command. Cozy reads conf/cozy/config.* and .cozy/config.*
      |# and passes bok.workflow.upload.env.* values to this script as environment
      |# variables. Put sensitive values in .cozy/config.*.
      |
      |PROJECT_DIR=$(cd "$(dirname "$0")/.." && pwd)
      |cd "$PROJECT_DIR"
      |
      |WEBSITE_SOURCE_DIR=${WEBSITE_SOURCE_DIR:-website.d}
      |REPOSITORY_SOURCE_DIR=${REPOSITORY_SOURCE_DIR:-repository}
      |AWS_S3_URI=${AWS_S3_URI:-}
      |AWS_S3_SYNC_DELETE=${AWS_S3_SYNC_DELETE:-true}
      |AWS_CLOUDFRONT_DISTRIBUTION_ID=${AWS_CLOUDFRONT_DISTRIBUTION_ID:-}
      |
      |if [ ! -d "$WEBSITE_SOURCE_DIR" ]; then
      |  echo "Website source directory is missing: $WEBSITE_SOURCE_DIR" >&2
      |  echo "Run: cozy bok build" >&2
      |  echo "Or set WEBSITE_SOURCE_DIR via bok.workflow.upload.env.WEBSITE_SOURCE_DIR." >&2
      |  exit 2
      |fi
      |
      |if [ -z "$AWS_S3_URI" ]; then
      |  cat >&2 <<'MSG'
      |AWS_S3_URI is not configured.
      |
      |Configure the upload target through Cozy workflow environment settings.
      |Use conf/cozy/config.yaml for public defaults and .cozy/config.yaml for
      |sensitive local overrides. Example:
      |
      |bok:
      |  workflow:
      |    upload:
      |      env:
      |        WEBSITE_SOURCE_DIR: website.d
      |        AWS_S3_URI: s3://example-bucket/path/
      |        AWS_S3_SYNC_DELETE: "true"
      |        AWS_CLOUDFRONT_DISTRIBUTION_ID: EXAMPLE123
      |
      |Cozy intentionally does not embed hosting credentials or provider policy.
      |MSG
      |  exit 2
      |fi
      |
      |if ! command -v aws >/dev/null 2>&1; then
      |  echo "aws CLI is required for this upload workflow." >&2
      |  exit 2
      |fi
      |
      |if [ "$AWS_S3_SYNC_DELETE" = "true" ]; then
      |  aws s3 sync "$WEBSITE_SOURCE_DIR"/ "$AWS_S3_URI" --delete --exclude "repository/*"
      |else
      |  aws s3 sync "$WEBSITE_SOURCE_DIR"/ "$AWS_S3_URI" --exclude "repository/*"
      |fi
      |
      |if [ -d "$WEBSITE_SOURCE_DIR/repository" ]; then
      |  aws s3 sync "$WEBSITE_SOURCE_DIR/repository"/ "$AWS_S3_URI/repository/"
      |fi
      |
      |if [ -d "$REPOSITORY_SOURCE_DIR" ]; then
      |  aws s3 sync "$REPOSITORY_SOURCE_DIR"/ "$AWS_S3_URI/repository/"
      |else
      |  echo "Repository source directory is not present; skipping artifact repository upload: $REPOSITORY_SOURCE_DIR" >&2
      |fi
      |
      |if [ -n "$AWS_CLOUDFRONT_DISTRIBUTION_ID" ]; then
      |  aws cloudfront create-invalidation --distribution-id "$AWS_CLOUDFRONT_DISTRIBUTION_ID" --paths "/*"
      |fi
      |""".stripMargin


  sealed trait ProjectFilePolicy
  object ProjectFilePolicy {
    case object Default extends ProjectFilePolicy
    case object Skip extends ProjectFilePolicy
    case object Overwrite extends ProjectFilePolicy

    def create(args: ParsedArgs): ProjectFilePolicy =
      if (args.request.switches.exists(x => x.name == "no-project-files" || x.name == "no-scaffold-files"))
        Skip
      else if (args.request.switches.exists(x => x.name == "overwrite-project-files" || x.name == "force-project-files"))
        Overwrite
      else
        Default
  }

  object CreateConfig {
    def create(args: List[String]): CreateConfig = {
      val parsed = BokArgs.create(args)
      val save = parsed.requiredPath("save", "<dir>")
      parsed.validateNoUnrecognized()
      CreateConfig(
        save,
        parsed.property("name").getOrElse("KnowledgeHub BoK"),
        parsed.property("url").getOrElse("https://www.asamioffice.com/kokubunji/knowledgehub"),
        parsed.property("language").getOrElse("ja"),
        ProjectFilePolicy.create(parsed)
      )
    }
  }

  object CategoryConfig {
    def create(args: List[String]): CategoryConfig = {
      val parsed = BokArgs.category(args)
      val name = parsed.argument("name").getOrElse(
        RAISE.invalidArgumentFault("Missing category name for bok create-category")
      )
      parsed.validateNoUnrecognized()
      CategoryConfig(
        _category_project(parsed),
        name,
        parsed.property("title").getOrElse(_titleize(name)),
        parsed.property("description").getOrElse(s"${_titleize(name)} category."),
        BokPurpose(parsed.property("vision"), Vector.empty, parsed.properties("goal"), parsed.properties("subgoal")),
        parsed.properties("article").map(_parse_category_article),
        parsed.properties("term").map(_parse_category_term),
        ProjectFilePolicy.create(parsed)
      )
    }
  }

  object DoctorConfig {
    def create(args: List[String], fix: Boolean): DoctorConfig = {
      var input: Option[Path] = None
      var fixswitch = false
      var dryrun = false
      args.foreach {
        case "--fix" => fixswitch = true
        case "--dry-run" => dryrun = true
        case x if x.startsWith("--") => RAISE.invalidArgumentFault(s"Unknown option: ${x}")
        case x =>
          if (input.isDefined)
            RAISE.invalidArgumentFault(s"Unknown argument: ${x}")
          input = Some(_to_path(x))
      }
      DoctorConfig(
        input.getOrElse(_logical_cwd),
        fix || fixswitch,
        dryrun
      )
    }
  }

  object PreviewConfig {
    def create(args: List[String]): PreviewConfig = {
      var input: Option[Path] = None
      var port: Option[Int] = None

      def take(xs: List[String]): Unit =
        xs match {
          case Nil =>
          case "--port" :: value :: rest =>
            port = Some(_parse_port(value))
            take(rest)
          case "--port" :: Nil =>
            RAISE.invalidArgumentFault("Missing --port <port>")
          case x :: _ if x.startsWith("--port=") =>
            RAISE.invalidArgumentFault("Use --port <port>, not --port=<port>")
          case x :: _ if x.startsWith("--") =>
            RAISE.invalidArgumentFault(s"Unknown option: ${x}")
          case x :: rest =>
            if (input.isDefined)
              RAISE.invalidArgumentFault(s"Unknown argument: ${x}")
            input = Some(_to_path(x))
            take(rest)
        }

      take(args)
      PreviewConfig(input.getOrElse(_logical_cwd), port)
    }

    private def _parse_port(value: String): Int =
      try {
        value.toInt
      } catch {
        case _: NumberFormatException => RAISE.invalidArgumentFault(s"Invalid --port <number>: ${value}")
      }
  }

  private def _parse_category_article(value: String): CategoryArticle = {
    val xs = value.split(":", 3).toVector
    xs match {
      case Vector(slug, title, purpose) => CategoryArticle(slug, title, purpose)
      case Vector(slug, title) => CategoryArticle(slug, title, s"${title} article.")
      case Vector(slug) => CategoryArticle(slug, _titleize(slug), s"${_titleize(slug)} article.")
      case _ => RAISE.invalidArgumentFault(s"Invalid bok category article: ${value}")
    }
  }

  private def _parse_category_term(value: String): CategoryTerm = {
    val xs = value.split(":", 4).toVector
    xs match {
      case Vector(path, title, definition, reading) => CategoryTerm(path, title, definition, Some(reading).filter(_.nonEmpty))
      case Vector(path, title, definition) => CategoryTerm(path, title, definition)
      case Vector(path, title) => CategoryTerm(path, title, s"${title} definition.")
      case Vector(path) => CategoryTerm(path, _titleize(path), s"${_titleize(path)} definition.")
      case _ => RAISE.invalidArgumentFault(s"Invalid bok category term: ${value}")
    }
  }

  private def _titleize(value: String): String =
    value.split("[/_-]+").toVector.filter(_.nonEmpty).map { part =>
      part.head.toUpper + part.tail
    }.mkString(" ")

  object BuildConfig {
    def create(args: List[String]): BuildConfig = {
      val parsed = BokArgs.build(args)
      val project = _project(parsed)
      parsed.validateNoUnrecognized()
      val config = _load_config(project)
      val source = config.value("bok.source").getOrElse("src/main/doxsite")
      val site = _load_site_config(project.resolve(source))
      val languages = _languages(config, site)
      val dockerimage =
        parsed.property("docker-image").
          orElse(config.value("bok.docker-image")).
          orElse(config.value("cozy.docker-image")).
          orElse(config.value("pdf.docker-image")).
          orElse(config.value("smartdox.pdf.docker-image")).
          orElse(config.value("cozy.pdf.docker-image")).
          getOrElse(_default_docker_image)
      BuildConfig(
        project,
        source,
        config.value("bok.website").getOrElse("website.d"),
        config.value("bok.antora").getOrElse("antora.d"),
        config.value("bok.doxsite").getOrElse("doxsite.d"),
        config.value("bok.arcadia-site").getOrElse("arcadiasite.d"),
        config.value("bok.ui-bundle").getOrElse("src/main/antora-ui/build/ui-bundle.zip"),
        _strategy(parsed),
        dockerimage,
        site.value("output.scope.policy").orElse(config.value("bok.output.scope.policy")).getOrElse("home_only"),
        site.value("site.metadata.name").getOrElse("KnowledgeHub BoK"),
        _locale_mode(config, site),
        _default_locale(config, site, languages),
        languages,
        ArcadiaConfig(_boolean(config, "bok.arcadia.enabled", false), config.value("bok.arcadia.source").getOrElse("src/main/arcadiasite")),
        _direct_assets(project, config),
        _publication_settings(parsed, config, _strategy(parsed)),
        _dashboard_color_group(parsed, config, site),
        !parsed.request.switches.exists(_.name == "no-bib-service")
      )
    }
  }

  object PublicationConfig {
    def create(name: String, args: List[String]): PublicationConfig = {
      val parsed = BokArgs.publication(name, args)
      val project = _project(parsed)
      parsed.validateNoUnrecognized()
      val config = _load_config(project)
      val strategy = _strategy(parsed, config.value("bok.strategy").getOrElse("production"))
      val publication = parsed.pathProperty("publication").
        map(_.toString).
        orElse(config.value("bok.publication")).
        getOrElse("src/main/publication")
      val (warehouse, repository) = _publication_artifact_roots(parsed, config)
      val force = parsed.request.switches.exists(_.name == "force") || _boolean(config, "bok.video.force", false)
      val videoenabled = _boolean(config, "bok.video.enabled", true)
      val dryrun = parsed.request.switches.exists(_.name == "dry-run")
      if (dryrun && name != "publish")
        RAISE.invalidArgumentFault(s"--dry-run is only supported by bok publish: ${name}")
      PublicationConfig(
        project,
        config.value("bok.source").getOrElse("src/main/doxsite"),
        publication,
        warehouse,
        repository,
        parsed.property("version"),
        force,
        videoenabled,
        dryrun,
        strategy
      )
    }
  }

  private def _workflow_command(config: CozyProjectYamlConfig.Config, name: String): Vector[String] =
    config.value(s"bok.workflow.${name}.command").map(x => Vector("sh", "-c", x)).getOrElse(Vector.empty)

  private def _workflow_env(config: CozyProjectYamlConfig.Config, name: String): Map[String, String] = {
    val defaults = Map(
      "WEBSITE_SOURCE_DIR" -> config.value("bok.website").getOrElse("website.d"),
      "REPOSITORY_SOURCE_DIR" -> config.value("bok.repository").
        orElse(config.value("bok.warehouse").map(_repository_under_warehouse)).
        getOrElse("repository")
    ) ++ (if (name == "stage") config.value("bok.website-staging").map("WEBSITE_STAGING_DIR" -> _).toMap else Map.empty)
    defaults ++ config.mapUnder(s"bok.workflow.${name}.env")
  }

  object WorkflowConfig {
    def create(name: String, args: List[String]): WorkflowConfig = {
      val parsed = BokArgs.workflow(name, args)
      val project = _project(parsed)
      parsed.validateNoUnrecognized()
      val config = _load_config(project)
      val env = _workflow_env(config, name)
      WorkflowConfig(
        project,
        name,
        _workflow_command(config, name),
        env,
        _workflow_backup_config(project, config, name, env)
      )
    }
  }

  private def _workflow_backup_config(
    project: Path,
    config: CozyProjectYamlConfig.Config,
    name: String,
    env: Map[String, String]
  ): Option[WebsiteBackupConfig] =
    if (name == "upload" && _boolean(config, "bok.backup.enabled", false)) {
      val source = project.resolve(env.getOrElse("WEBSITE_SOURCE_DIR", config.value("bok.website").getOrElse("website.d"))).toAbsolutePath.normalize()
      val root = project.resolve(
        config.value("bok.backup.dir").
          orElse(config.value("bok.backup.path")).
          getOrElse("website.backup")
      ).toAbsolutePath.normalize()
      Some(WebsiteBackupConfig(source, root, _boolean(config, "bok.backup.compressed", true)))
    } else {
      None
    }
}
