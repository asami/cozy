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

private[cozy] trait CozyBokModel {
  self: CozyBokImplementation.type =>
  private[bok] final case class BokInspection(
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
  private[bok] final case class BokFix(description: String, apply: () => Unit)
  private[bok] final case class DoxMetadataSectionIssue(path: Path, line: Int, heading: String)
  private[bok] final case class MarkdownMetadataIssue(path: Path, message: String)
  private[bok] final case class CategoryContent(
    slug: String,
    title: String,
    description: String,
    purpose: BokPurpose,
    articles: Vector[CategoryPageItem],
    terms: Vector[CategoryPageItem]
  )
  private[bok] final case class CategoryPageItem(
    href: String,
    title: String,
    brief: String,
    modifiedatmillis: Long,
    reading: Option[String] = None
  )
  private[bok] final case class LocalizedGlossaryItem(
    href: String,
    title: String,
    reading: Option[String],
    categoryslug: String,
    categorytitle: String
  )
  private[bok] final case class ArticleIndexItem(
    categoryslug: String,
    categorytitle: String,
    hreffromarticleindex: String,
    title: String,
    brief: String,
    termextraction: TermExtraction
  )
  private[bok] final case class DashboardCounts(
    categorycount: Int,
    articlecount: Int,
    glossarytermcount: Int,
    totalitemcount: Int
  )
  private[bok] final case class DashboardBucket(
    label: String,
    startdate: String,
    enddate: String,
    count: Int,
    articlecount: Int,
    glossarytermcount: Int,
    hasbreakdown: Boolean
  )
  private[bok] final case class DashboardRdfSummary(
    resourcecount: Int,
    triplecount: Int,
    subjectcount: Int,
    predicatecount: Int
  )
  private[bok] final case class DashboardIncrements(
    scale: String,
    buckets: Vector[DashboardBucket]
  )
  private[bok] final case class DashboardRecentItem(
    href: String,
    title: String,
    category: Option[String],
    kindkey: String,
    modifiedatmillis: Long
  )
  private[bok] final case class DashboardCategory(
    name: String,
    title: String,
    counts: DashboardCounts,
    increments: DashboardIncrements,
    rdf: Option[DashboardRdfSummary]
  )
  private[bok] final case class BokDashboard(
    counts: DashboardCounts,
    rdf: DashboardRdfSummary,
    increments: DashboardIncrements,
    categories: Vector[DashboardCategory]
  )

  private[bok] final case class ScenarioIndex(scenarios: Vector[ScenarioEntry])
  private[bok] final case class ScenarioEntry(
    id: String,
    slug: String,
    scenariotype: String,
    title: String,
    summary: Option[String],
    category: Option[String],
    sourcepath: String,
    publicpath: String,
    terms: Vector[String],
    tags: Vector[String],
    status: Option[String],
    termextraction: TermExtraction
  ) {
    def categorySlug: String = category.getOrElse("scenario")
    def hrefFromHome: String = publicpath
    def hrefFromCategory: String = "../" + publicpath
    def isRelatedTo(term: TermEntry): Boolean =
      terms.exists(x => x == term.id || x == term.title || x == term.slug)
  }

  private[bok] final case class TermIndex(terms: Vector[TermEntry])
  private[bok] final case class TermEntry(
    id: String,
    slug: String,
    title: String,
    reading: Option[String],
    category: Option[String],
    sourcepath: String,
    publicpath: String,
    definitionhtml: String,
    summary: Option[String],
    aliases: Vector[String],
    articlerefs: Vector[TermReference],
    termrefs: Vector[TermReference],
    rdfrefs: Vector[TermRdfReference],
    videorefs: Vector[TermReference],
    termtype: String,
    resourcetype: Option[String],
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
      termtype match {
        case "event" => "koto"
        case "rule" => "rule"
        case _ => "mono"
      }
    def cmlLinks: Vector[TermCmlLink] = (cml ++ event.toVector.flatMap(_.cmlLinks)).distinct
  }
  private[bok] final case class TermReference(title: String, path: String, relation: String)
  private[bok] final case class TermRdfReference(resource: String, label: String, predicate: Option[String], direction: String)
  private[bok] final case class TermCmlLink(kind: String, value: String)
  private[bok] final case class TermEvent(
    occurredat: Option[String],
    startat: Option[String],
    endat: Option[String],
    location: Option[String],
    actors: Vector[String],
    roles: Vector[String],
    participants: Vector[String],
    scenarios: Vector[String],
    evidence: Vector[String],
    cmlevent: Option[String],
    cmlcomponent: Option[String],
    cmlstatemachine: Option[String]
  ) {
    def cmlLinks: Vector[TermCmlLink] =
      Vector(
        cmlcomponent.map(TermCmlLink("component", _)),
        cmlevent.map(TermCmlLink("event", _)),
        cmlstatemachine.map(TermCmlLink("statemachine", _))
      ).flatten
  }
  private[bok] final case class TermActor(roles: Vector[String], organization: Option[String], description: Option[String])
  private[bok] final case class TermRole(actors: Vector[String], responsibilities: Vector[String], permissions: Vector[String])
  private[bok] final case class TermQuality(isolated: Boolean, unreferenced: Boolean, weaklyconnected: Boolean)
  private[bok] final case class TermExtraction(status: Option[String], planned: Option[Boolean]) {
    def isPlanned: Boolean =
      planned.getOrElse(true) && !status.map(_normalize_status).exists(x => x == "skipped" || x == "out-of-scope")
    def isExtracted: Boolean =
      status.map(_normalize_status).exists(x => x == "extracted" || x == "done" || x == "complete" || x == "completed")
    private def _normalize_status(value: String): String =
      value.trim.toLowerCase.replace('_', '-')
  }

  private[bok] object TermExtraction {
    val empty: TermExtraction = TermExtraction(None, None)
  }

  private def _decode_term_extraction(c: HCursor): Decoder.Result[TermExtraction] =
    for {
      obj <- c.downField("workflow").downField("term").as[Option[TermExtraction]]
    } yield obj.getOrElse(TermExtraction.empty)

  private[bok] implicit val _term_extraction_decoder: Decoder[TermExtraction] = (c: HCursor) =>
    for {
      status <- c.downField("status").as[Option[String]]
      planned <- c.downField("planned").as[Option[Boolean]]
    } yield TermExtraction(status, planned)

  private[bok] final case class DocumentFragmentIndex(fragments: Vector[DocumentFragment]) {
    def get(sourcepath: String, locale: String): Option[DocumentFragment] =
      fragments.find(x => x.sourcepath == sourcepath && x.locale == locale)
  }
  private[bok] final case class DocumentFragment(
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
    termextraction: TermExtraction
  ) {
    def effectiveHeadline: Option[String] = headline.orElse(title)
    def effectiveBrief: Option[String] = brief.orElse(summary).orElse(description)
  }

  private[bok] implicit val _document_fragment_decoder: Decoder[DocumentFragment] = (c: HCursor) =>
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

  private[bok] implicit val _document_fragment_index_decoder: Decoder[DocumentFragmentIndex] = (c: HCursor) =>
    for {
      fragments <- c.downField("fragments").as[Option[Vector[DocumentFragment]]]
    } yield DocumentFragmentIndex(fragments.getOrElse(Vector.empty))

  private[bok] final case class TagIndex(tags: Vector[TagEntry]) {
    def isEmpty: Boolean = tags.isEmpty
    def forCategory(category: String): TagIndex =
      TagIndex(tags.flatMap { tag =>
        val refs = tag.refs.filter(_.category.contains(category))
        if (refs.isEmpty) None else Some(tag.copy(refs = refs))
      })
    def namespaces: Vector[String] =
      tags.flatMap(_.namespace).distinct.sorted
  }
  private[bok] final case class TagEntry(
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
  private[bok] final case class TagReference(kind: String, title: String, href: String, category: Option[String])

  private[bok] implicit val _tag_reference_decoder: Decoder[TagReference] = (c: HCursor) =>
    for {
      kind <- c.downField("kind").as[Option[String]]
      title <- c.downField("title").as[Option[String]]
      href <- c.downField("public_path").as[Option[String]]
      legacyhref <- c.downField("href").as[Option[String]]
      category <- c.downField("category").as[Option[String]]
    } yield TagReference(kind.getOrElse("document"), title.getOrElse(href.orElse(legacyhref).getOrElse("Untitled")), href.orElse(legacyhref).getOrElse("#"), category)

  private[bok] implicit val _tag_entry_decoder: Decoder[TagEntry] = (c: HCursor) =>
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

  private[bok] implicit val _tag_index_decoder: Decoder[TagIndex] = (c: HCursor) =>
    for {
      tags <- c.downField("tags").as[Option[Vector[TagEntry]]]
    } yield TagIndex(tags.getOrElse(Vector.empty))

  private[bok] implicit val _dashboard_counts_decoder: Decoder[DashboardCounts] = (c: HCursor) =>
    for {
      categorycount <- c.downField("category_count").as[Int]
      articlecount <- c.downField("article_count").as[Int]
      glossarytermcount <- c.downField("glossary_term_count").as[Int]
      totalitemcount <- c.downField("total_item_count").as[Int]
    } yield DashboardCounts(categorycount, articlecount, glossarytermcount, totalitemcount)

  private[bok] implicit val _dashboard_bucket_decoder: Decoder[DashboardBucket] = (c: HCursor) =>
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

  private[bok] implicit val _dashboard_rdf_summary_decoder: Decoder[DashboardRdfSummary] = (c: HCursor) =>
    for {
      resourcecount <- c.downField("resource_count").as[Int]
      triplecount <- c.downField("triple_count").as[Int]
      subjectcount <- c.downField("subject_count").as[Int]
      predicatecount <- c.downField("predicate_count").as[Int]
    } yield DashboardRdfSummary(resourcecount, triplecount, subjectcount, predicatecount)

  private[bok] implicit val _dashboard_increments_decoder: Decoder[DashboardIncrements] = (c: HCursor) =>
    for {
      scale <- c.downField("scale").as[String]
      buckets <- c.downField("buckets").as[Vector[DashboardBucket]]
    } yield DashboardIncrements(scale, buckets)

  private[bok] implicit val _dashboard_category_decoder: Decoder[DashboardCategory] = (c: HCursor) =>
    for {
      name <- c.downField("name").as[String]
      title <- c.downField("title").as[String]
      counts <- c.downField("counts").as[DashboardCounts]
      increments <- c.downField("increments").as[DashboardIncrements]
      rdf <- c.downField("rdf").as[Option[DashboardRdfSummary]]
    } yield DashboardCategory(name, title, counts, increments, rdf)

  private[bok] implicit val _bok_dashboard_decoder: Decoder[BokDashboard] = (c: HCursor) =>
    for {
      counts <- c.downField("counts").as[DashboardCounts]
      rdf <- c.downField("rdf").as[DashboardRdfSummary]
      increments <- c.downField("increments").as[DashboardIncrements]
      categories <- c.downField("categories").as[Vector[DashboardCategory]]
    } yield BokDashboard(counts, rdf, increments, categories)

  private[bok] implicit val _term_reference_decoder: Decoder[TermReference] = (c: HCursor) =>
    for {
      title <- c.downField("title").as[String]
      path <- c.downField("path").as[String]
      relation <- c.downField("relation").as[Option[String]]
    } yield TermReference(title, path, relation.getOrElse("related"))

  private[bok] implicit val _term_rdf_reference_decoder: Decoder[TermRdfReference] = (c: HCursor) =>
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

  private[bok] implicit val _term_event_decoder: Decoder[TermEvent] = (c: HCursor) =>
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

  private[bok] implicit val _term_actor_decoder: Decoder[TermActor] = (c: HCursor) =>
    for {
      roles <- c.downField("roles").as[Option[Vector[String]]]
      organization <- c.downField("organization").as[Option[String]]
      description <- c.downField("description").as[Option[String]]
    } yield TermActor(roles.getOrElse(Vector.empty), organization, description)

  private[bok] implicit val _term_role_decoder: Decoder[TermRole] = (c: HCursor) =>
    for {
      actors <- c.downField("actors").as[Option[Vector[String]]]
      responsibilities <- c.downField("responsibilities").as[Option[Vector[String]]]
      permissions <- c.downField("permissions").as[Option[Vector[String]]]
    } yield TermRole(actors.getOrElse(Vector.empty), responsibilities.getOrElse(Vector.empty), permissions.getOrElse(Vector.empty))

  private[bok] implicit val _term_quality_decoder: Decoder[TermQuality] = (c: HCursor) =>
    for {
      isolated <- c.downField("isolated").as[Option[Boolean]]
      unreferenced <- c.downField("unreferenced").as[Option[Boolean]]
      weaklyconnected <- c.downField("weakly_connected").as[Option[Boolean]]
    } yield TermQuality(isolated.getOrElse(false), unreferenced.getOrElse(false), weaklyconnected.getOrElse(false))

  private[bok] implicit val _term_entry_decoder: Decoder[TermEntry] = (c: HCursor) =>
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

  private[bok] implicit val _term_index_decoder: Decoder[TermIndex] = (c: HCursor) =>
    for {
      terms <- c.downField("terms").as[Option[Vector[TermEntry]]]
    } yield TermIndex(terms.getOrElse(Vector.empty))

  private[bok] implicit val _scenario_entry_decoder: Decoder[ScenarioEntry] = (c: HCursor) =>
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

  private[bok] implicit val _scenario_index_decoder: Decoder[ScenarioIndex] = (c: HCursor) =>
    for {
      scenarios <- c.downField("scenarios").as[Option[Vector[ScenarioEntry]]]
    } yield ScenarioIndex(scenarios.getOrElse(Vector.empty))

}
