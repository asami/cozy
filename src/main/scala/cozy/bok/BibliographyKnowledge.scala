package cozy.bok

import java.net.URI
import java.net.URLEncoder
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.time.Duration
import scala.util.control.NonFatal
import io.circe.{Decoder, HCursor, Json}
import io.circe.parser

/*
 * @since   Jun. 24, 2026
 * @version Jun. 24, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] final case class BibliographyIndex(entries: Vector[BibliographyEntry])

private[cozy] final case class BibliographyIdentifiers(
  doi: Option[String],
  isbn: Option[String],
  issn: Option[String],
  url: Option[String],
  urn: Option[String],
  arxiv: Option[String],
  github: Option[String],
  wikidata: Option[String]
)

private[cozy] final case class BibliographyBibtex(
  key: Option[String],
  entrytype: Option[String],
  sourceurl: Option[String],
  raw: Option[String]
)

private[cozy] final case class BibliographyEntry(
  id: String,
  slug: String,
  entrytype: String,
  title: String,
  summary: Option[String],
  category: Option[String],
  sourcepath: String,
  publicpath: String,
  authors: Vector[String],
  publishedat: Option[String],
  publisher: Option[String],
  sourceurl: Option[String],
  accessedat: Option[String],
  terms: Vector[String],
  citation: Option[String],
  identifiers: BibliographyIdentifiers,
  bibtex: BibliographyBibtex,
  bodyhtml: String
) {
  def categorySlug: String = category.getOrElse("bibliography")
}

private[cozy] object BibliographyEntry {
  implicit val _identifiers_decoder: Decoder[BibliographyIdentifiers] = (c: HCursor) =>
    for {
      doi <- c.downField("doi").as[Option[String]]
      isbn <- c.downField("isbn").as[Option[String]]
      issn <- c.downField("issn").as[Option[String]]
      url <- c.downField("url").as[Option[String]]
      urn <- c.downField("urn").as[Option[String]]
      arxiv <- c.downField("arxiv").as[Option[String]]
      github <- c.downField("github").as[Option[String]]
      wikidata <- c.downField("wikidata").as[Option[String]]
    } yield BibliographyIdentifiers(doi, isbn, issn, url, urn, arxiv, github, wikidata)

  implicit val _bibtex_decoder: Decoder[BibliographyBibtex] = (c: HCursor) =>
    for {
      key <- c.downField("key").as[Option[String]]
      entrytype <- c.downField("entry_type").as[Option[String]]
      sourceurl <- c.downField("source_url").as[Option[String]]
      raw <- c.downField("raw").as[Option[String]]
    } yield BibliographyBibtex(key, entrytype, sourceurl, raw)

  implicit val _entry_decoder: Decoder[BibliographyEntry] = (c: HCursor) =>
    for {
      id <- c.downField("id").as[String]
      slug <- c.downField("slug").as[String]
      entrytype <- c.downField("entry_type").as[String]
      title <- c.downField("title").as[String]
      summary <- c.downField("summary").as[Option[String]]
      category <- c.downField("category").as[Option[String]]
      sourcepath <- c.downField("source_path").as[String]
      publicpath <- c.downField("public_path").as[String]
      authors <- c.downField("authors").as[Option[Vector[String]]]
      publishedat <- c.downField("published_at").as[Option[String]]
      publisher <- c.downField("publisher").as[Option[String]]
      sourceurl <- c.downField("source_url").as[Option[String]]
      accessedat <- c.downField("accessed_at").as[Option[String]]
      terms <- c.downField("terms").as[Option[Vector[String]]]
      citation <- c.downField("citation").as[Option[String]]
      identifiers <- c.downField("identifiers").as[Option[BibliographyIdentifiers]]
      bibtex <- c.downField("bibtex").as[Option[BibliographyBibtex]]
      bodyhtml <- c.downField("body_html").as[Option[String]]
    } yield BibliographyEntry(
      id,
      slug,
      entrytype,
      title,
      summary,
      category,
      sourcepath,
      publicpath,
      authors.getOrElse(Vector.empty),
      publishedat,
      publisher,
      sourceurl,
      accessedat,
      terms.getOrElse(Vector.empty),
      citation,
      identifiers.getOrElse(BibliographyIdentifiers(None, None, None, None, None, None, None, None)),
      bibtex.getOrElse(BibliographyBibtex(None, None, None, None)),
      bodyhtml.getOrElse("")
    )

  implicit val _index_decoder: Decoder[BibliographyIndex] = (c: HCursor) =>
    for {
      entries <- c.downField("entries").as[Option[Vector[BibliographyEntry]]]
    } yield BibliographyIndex(entries.getOrElse(Vector.empty))
}

private[cozy] final case class BibliographySearchConfig(
  query: String,
  provider: String,
  limit: Int,
  format: String
)

private[cozy] final case class BibliographyUpdateConfig(project: Path, force: Boolean)

private[cozy] final case class BibliographySearchResult(
  provider: String,
  bibid: String,
  bibtexkey: String,
  title: String,
  authors: Vector[String],
  year: Option[String],
  doi: Option[String],
  isbn: Option[String],
  sourceurl: Option[String],
  entrytype: String
) {
  def text: String = {
    val author = if (authors.isEmpty) "-" else authors.mkString(", ")
    val ids = Vector(doi.map(x => s"DOI ${x}"), isbn.map(x => s"ISBN ${x}")).flatten.mkString(", ")
    val suffix = if (ids.isEmpty) "" else s" [${ids}]"
    s"${provider}: ${title} (${year.getOrElse("n.d.")})\n  bib-id: ${bibid}\n  bibtex-key: ${bibtexkey}\n  authors: ${author}${suffix}"
  }
}

private[cozy] trait BibliographySearchProvider {
  def name: String
  def search(query: String, limit: Int): Vector[BibliographySearchResult]
}

private[cozy] trait BibliographyBibtexFetcher {
  def fetch(sourceurl: String): Option[String]
}

private[cozy] final case class BibliographySearchRegistry(providers: Vector[BibliographySearchProvider]) {
  def search(config: BibliographySearchConfig): Vector[BibliographySearchResult] = {
    val selected = config.provider match {
      case "all" => providers
      case name => providers.filter(_.name == name)
    }
    selected.flatMap(_.search(config.query, config.limit)).take(config.limit)
  }
}

private[cozy] object BibliographySearchRegistry {
  val default: BibliographySearchRegistry = BibliographySearchRegistry(Vector(
    BibliographyHttpSearchProvider.crossref,
    BibliographyHttpSearchProvider.openlibrary,
    BibliographyHttpSearchProvider.dblp
  ))
}

private[cozy] object BibliographyHttpSearchProvider {
  private val _client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build()

  val crossref: BibliographySearchProvider = new BibliographySearchProvider {
    val name = "crossref"
    def search(query: String, limit: Int): Vector[BibliographySearchResult] =
      _get_json(s"https://api.crossref.org/works?query.bibliographic=${_url(query)}&rows=${limit}").flatMap { json =>
        json.hcursor.downField("message").downField("items").as[Vector[Json]].toOption.map(_.flatMap { item =>
          val c = item.hcursor
          val title = c.downField("title").as[Vector[String]].toOption.flatMap(_.headOption).getOrElse(query)
          val doi = c.downField("DOI").as[String].toOption
          val year = c.downField("issued").downField("date-parts").as[Vector[Vector[Int]]].toOption.flatMap(_.headOption.flatMap(_.headOption)).map(_.toString)
          val authors = c.downField("author").as[Vector[Json]].toOption.getOrElse(Vector.empty).flatMap { a =>
            val ac = a.hcursor
            val given = ac.downField("given").as[String].toOption.getOrElse("")
            val family = ac.downField("family").as[String].toOption.getOrElse("")
            Some(s"${given} ${family}".trim).filter(_.nonEmpty)
          }
          Some(BibliographySearchResult(name, doi.map("doi:" + _).getOrElse(_slug(title)), _bibtex_key(authors.headOption, year, title), title, authors, year, doi, None, c.downField("URL").as[String].toOption, "article"))
        })
      }.getOrElse(Vector.empty)
  }

  val openlibrary: BibliographySearchProvider = new BibliographySearchProvider {
    val name = "openlibrary"
    def search(query: String, limit: Int): Vector[BibliographySearchResult] =
      _get_json(s"https://openlibrary.org/search.json?q=${_url(query)}&limit=${limit}").flatMap { json =>
        json.hcursor.downField("docs").as[Vector[Json]].toOption.map(_.flatMap { item =>
          val c = item.hcursor
          val title = c.downField("title").as[String].toOption.getOrElse(query)
          val authors = c.downField("author_name").as[Vector[String]].toOption.getOrElse(Vector.empty)
          val year = c.downField("first_publish_year").as[Int].toOption.map(_.toString)
          val isbn = c.downField("isbn").as[Vector[String]].toOption.flatMap(_.headOption)
          Some(BibliographySearchResult(name, isbn.map("isbn:" + _).getOrElse(_slug(title)), _bibtex_key(authors.headOption, year, title), title, authors, year, None, isbn, c.downField("key").as[String].toOption.map("https://openlibrary.org" + _), "book"))
        })
      }.getOrElse(Vector.empty)
  }

  val dblp: BibliographySearchProvider = new BibliographySearchProvider {
    val name = "dblp"
    def search(query: String, limit: Int): Vector[BibliographySearchResult] =
      _get_json(s"https://dblp.org/search/publ/api?q=${_url(query)}&format=json&h=${limit}").flatMap { json =>
        json.hcursor.downField("result").downField("hits").downField("hit").as[Vector[Json]].toOption.map(_.flatMap { hit =>
          val info = hit.hcursor.downField("info")
          val title = info.downField("title").as[String].toOption.getOrElse(query)
          val authors = _dblp_authors(info.downField("authors").downField("author").focus)
          val year = info.downField("year").as[String].toOption
          val key = info.downField("key").as[String].toOption.getOrElse(_slug(title))
          Some(BibliographySearchResult(name, key, _bibtex_key(authors.headOption, year, title), title, authors, year, None, None, info.downField("url").as[String].toOption, "paper"))
        })
      }.getOrElse(Vector.empty)
  }

  private def _dblp_authors(json: Option[Json]): Vector[String] =
    json.map { value =>
      value.asArray.map(_.flatMap(_.asString)).orElse(value.asString.map(Vector(_))).getOrElse(Vector.empty)
    }.getOrElse(Vector.empty)

  private def _get_json(url: String): Option[Json] =
    try {
      val req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build()
      val res = _client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
      if (res.statusCode() >= 200 && res.statusCode() < 300)
        parser.parse(res.body()).toOption
      else
        None
    } catch {
      case NonFatal(_) => None
    }

  private def _url(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
  private def _slug(value: String): String = value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-").stripPrefix("-").stripSuffix("-")
  private def _bibtex_key(author: Option[String], year: Option[String], title: String): String = {
    val a = author.map(x => x.split("\\s+").lastOption.getOrElse(x)).getOrElse("ref").toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "")
    val y = year.getOrElse("nd")
    val t = _slug(title).split('-').take(3).mkString("")
    s"${a}${y}${t}"
  }
}

private[cozy] object BibliographyHttpBibtexFetcher extends BibliographyBibtexFetcher {
  private val _client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build()

  def fetch(sourceurl: String): Option[String] =
    try {
      val req = HttpRequest.newBuilder(URI.create(sourceurl)).timeout(Duration.ofSeconds(10)).GET().build()
      val res = _client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
      if (res.statusCode() >= 200 && res.statusCode() < 300)
        Some(res.body()).filter(_.trim.nonEmpty)
      else
        None
    } catch {
      case NonFatal(_) => None
    }
}
