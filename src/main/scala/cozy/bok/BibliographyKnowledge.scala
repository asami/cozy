package cozy.bok

import java.net.URI
import java.net.URLEncoder
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.time.Duration
import scala.util.control.NonFatal
import io.circe.{Decoder, Encoder, HCursor, Json}
import io.circe.parser
import io.circe.syntax._

/*
 * @since   Jun. 24, 2026
 * @version Jun. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] final case class BibliographyIndex(entries: Vector[BibliographyEntry]) {
  def toJsonString: String = Json.obj("entries" -> entries.asJson).spaces2 + "\n"
}

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
  entrytype: Option[String],
  sourceurl: Option[String],
  raw: Option[String]
)

private[cozy] final case class BibliographySourceRef(
  sourcepath: String,
  publicpath: String,
  category: Option[String],
  citationkey: String,
  ordinal: Int
)

private[cozy] final case class BibliographyQuality(
  missingcitation: Boolean,
  missingterms: Boolean,
  missingsource: Boolean,
  missingnarrative: Boolean,
  bibtexonly: Boolean,
  needscuration: Boolean
)

private[cozy] object BibliographyQuality {
  val empty: BibliographyQuality = BibliographyQuality(false, false, false, false, false, false)
}

private[cozy] final case class BibliographyEntry(
  id: String,
  key: Option[String],
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
  bodyhtml: String,
  sourcekind: String,
  refs: Vector[String],
  sourcerefs: Vector[BibliographySourceRef],
  needsresolution: Boolean,
  quality: BibliographyQuality,
  tags: Vector[String]
) {
  def categorySlug: String = category.getOrElse("bibliography")
  def isUnresolved: Boolean = needsresolution
}

private[cozy] object BibliographyEntry {
  implicit val _identifiers_encoder: Encoder[BibliographyIdentifiers] = (x: BibliographyIdentifiers) => Json.obj(
    "doi" -> x.doi.asJson,
    "isbn" -> x.isbn.asJson,
    "issn" -> x.issn.asJson,
    "url" -> x.url.asJson,
    "urn" -> x.urn.asJson,
    "arxiv" -> x.arxiv.asJson,
    "github" -> x.github.asJson,
    "wikidata" -> x.wikidata.asJson
  )

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

  implicit val _bibtex_encoder: Encoder[BibliographyBibtex] = (x: BibliographyBibtex) => Json.obj(
    "entry_type" -> x.entrytype.asJson,
    "source_url" -> x.sourceurl.asJson,
    "raw" -> x.raw.asJson
  )

  implicit val _bibtex_decoder: Decoder[BibliographyBibtex] = (c: HCursor) =>
    for {
      entrytype <- c.downField("entry_type").as[Option[String]]
      sourceurl <- c.downField("source_url").as[Option[String]]
      raw <- c.downField("raw").as[Option[String]]
    } yield BibliographyBibtex(entrytype, sourceurl, raw)

  implicit val _source_ref_encoder: Encoder[BibliographySourceRef] = (x: BibliographySourceRef) => Json.obj(
    "source_path" -> x.sourcepath.asJson,
    "public_path" -> x.publicpath.asJson,
    "category" -> x.category.asJson,
    "citation_key" -> x.citationkey.asJson,
    "ordinal" -> x.ordinal.asJson
  )

  implicit val _source_ref_decoder: Decoder[BibliographySourceRef] = (c: HCursor) =>
    for {
      sourcepath <- c.downField("source_path").as[String]
      publicpath <- c.downField("public_path").as[String]
      category <- c.downField("category").as[Option[String]]
      citationkey <- c.downField("citation_key").as[String]
      ordinal <- c.downField("ordinal").as[Option[Int]]
    } yield BibliographySourceRef(sourcepath, publicpath, category, citationkey, ordinal.getOrElse(0))

  implicit val _quality_encoder: Encoder[BibliographyQuality] = (x: BibliographyQuality) => Json.obj(
    "missing_citation" -> x.missingcitation.asJson,
    "missing_terms" -> x.missingterms.asJson,
    "missing_source" -> x.missingsource.asJson,
    "missing_narrative" -> x.missingnarrative.asJson,
    "bibtex_only" -> x.bibtexonly.asJson,
    "needs_curation" -> x.needscuration.asJson
  )

  implicit val _quality_decoder: Decoder[BibliographyQuality] = (c: HCursor) =>
    for {
      missingcitation <- c.downField("missing_citation").as[Option[Boolean]]
      missingterms <- c.downField("missing_terms").as[Option[Boolean]]
      missingsource <- c.downField("missing_source").as[Option[Boolean]]
      missingnarrative <- c.downField("missing_narrative").as[Option[Boolean]]
      bibtexonly <- c.downField("bibtex_only").as[Option[Boolean]]
      needscuration <- c.downField("needs_curation").as[Option[Boolean]]
    } yield BibliographyQuality(missingcitation.getOrElse(false), missingterms.getOrElse(false), missingsource.getOrElse(false), missingnarrative.getOrElse(false), bibtexonly.getOrElse(false), needscuration.getOrElse(false))

  implicit val _entry_encoder: Encoder[BibliographyEntry] = (x: BibliographyEntry) => Json.obj(
    "id" -> x.id.asJson,
    "key" -> x.key.asJson,
    "slug" -> x.slug.asJson,
    "entry_type" -> x.entrytype.asJson,
    "title" -> x.title.asJson,
    "summary" -> x.summary.asJson,
    "category" -> x.category.asJson,
    "source_path" -> x.sourcepath.asJson,
    "public_path" -> x.publicpath.asJson,
    "authors" -> x.authors.asJson,
    "published_at" -> x.publishedat.asJson,
    "publisher" -> x.publisher.asJson,
    "source_url" -> x.sourceurl.asJson,
    "accessed_at" -> x.accessedat.asJson,
    "terms" -> x.terms.asJson,
    "citation" -> x.citation.asJson,
    "identifiers" -> x.identifiers.asJson,
    "bibtex" -> x.bibtex.asJson,
    "body_html" -> x.bodyhtml.asJson,
    "source_kind" -> x.sourcekind.asJson,
    "refs" -> x.refs.asJson,
    "source_refs" -> x.sourcerefs.asJson,
    "needs_resolution" -> x.needsresolution.asJson,
    "quality" -> x.quality.asJson,
    "tags" -> x.tags.asJson
  )

  implicit val _entry_decoder: Decoder[BibliographyEntry] = (c: HCursor) =>
    for {
      id <- c.downField("id").as[String]
      key <- c.downField("key").as[Option[String]]
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
      sourcekind <- c.downField("source_kind").as[Option[String]]
      refs <- c.downField("refs").as[Option[Vector[String]]]
      sourcerefs <- c.downField("source_refs").as[Option[Vector[BibliographySourceRef]]]
      needsresolution <- c.downField("needs_resolution").as[Option[Boolean]]
      quality <- c.downField("quality").as[Option[BibliographyQuality]]
      tags <- c.downField("tags").as[Option[Vector[String]]]
    } yield BibliographyEntry(
      id,
      key,
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
      bibtex.getOrElse(BibliographyBibtex(None, None, None)),
      bodyhtml.getOrElse(""),
      sourcekind.getOrElse("internal"),
      refs.getOrElse(Vector(id)),
      sourcerefs.getOrElse(Vector.empty),
      needsresolution.getOrElse(false),
      quality.getOrElse(BibliographyQuality.empty),
      tags.getOrElse(Vector.empty)
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

private[cozy] final case class BibliographyUpdateConfig(project: Path, force: Boolean, reportonly: Boolean)

private[cozy] final case class BibliographySearchResult(
  provider: String,
  bibid: String,
  citationkey: String,
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
    s"${provider}: ${title} (${year.getOrElse("n.d.")})\n  bib-id: ${bibid}\n  citation-key: ${citationkey}\n  authors: ${author}${suffix}"
  }
}

private[cozy] trait BibliographySearchProvider {
  def name: String
  def search(query: String, limit: Int): Vector[BibliographySearchResult]
}

private[cozy] trait BibliographyBibtexFetcher {
  def fetch(sourceurl: String): Option[String]
  def fetchBibId(bibid: String): Option[String] = None
  def fetchEntry(entry: BibliographyEntry): Option[String] =
    entry.bibtex.sourceurl.flatMap(fetch).
      orElse(if (entry.needsresolution) fetchBibId(entry.id) else None)
}

private[cozy] final case class BibliographySearchRegistry(providers: Vector[BibliographySearchProvider]) {
  def search(config: BibliographySearchConfig): Vector[BibliographySearchResult] = {
    val selected = config.provider match {
      case "all" => providers
      case name => providers.filter(_.name == name)
    }
    val perproviderlimit = if (config.provider == "all") config.limit else config.limit
    _deduplicate(selected.flatMap(_.search(config.query, perproviderlimit))).take(config.limit)
  }

  private def _deduplicate(results: Vector[BibliographySearchResult]): Vector[BibliographySearchResult] =
    results.foldLeft(Vector.empty[BibliographySearchResult]) { (acc, x) =>
      if (acc.exists(y => y.bibid == x.bibid || y.title.equalsIgnoreCase(x.title))) acc else acc :+ x
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
          Some(BibliographySearchResult(name, doi.map("doi:" + _).getOrElse(_slug(title)), _citation_key(authors.headOption, year, title), title, authors, year, doi, None, c.downField("URL").as[String].toOption, "article"))
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
          val key = c.downField("key").as[String].toOption.map(_.stripPrefix("/"))
          Some(BibliographySearchResult(name, key.map("openlibrary:" + _).orElse(isbn.map("isbn:" + _)).getOrElse(_slug(title)), _citation_key(authors.headOption, year, title), title, authors, year, None, isbn, key.map("https://openlibrary.org/" + _), "book"))
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
          Some(BibliographySearchResult(name, if (key.startsWith("dblp:")) key else "dblp:" + key, _citation_key(authors.headOption, year, title), title, authors, year, None, None, info.downField("url").as[String].toOption, "paper"))
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
  private def _citation_key(author: Option[String], year: Option[String], title: String): String = {
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

  override def fetchBibId(bibid: String): Option[String] =
    if (bibid.startsWith("doi:"))
      fetch(s"https://api.crossref.org/works/${URLEncoder.encode(bibid.stripPrefix("doi:"), StandardCharsets.UTF_8.name())}/transform/application/x-bibtex")
    else if (bibid.startsWith("openlibrary:"))
      _openlibrary_bibtex(bibid, s"https://openlibrary.org/${bibid.stripPrefix("openlibrary:")}.json")
    else if (bibid.startsWith("isbn:"))
      _openlibrary_bibtex(bibid, s"https://openlibrary.org/isbn/${URLEncoder.encode(bibid.stripPrefix("isbn:"), StandardCharsets.UTF_8.name())}.json")
    else if (bibid.startsWith("dblp:"))
      fetch(s"https://dblp.org/rec/${bibid.stripPrefix("dblp:")}.bib")
    else
      None

  private def _openlibrary_bibtex(bibid: String, url: String): Option[String] =
    fetch(url).flatMap { body =>
      parser.parse(body).toOption.flatMap { json =>
        val c = json.hcursor
        c.downField("title").as[String].toOption.map { title =>
          val year = c.downField("publish_date").as[String].toOption.flatMap(_year)
          val key = _safe_citation_key(bibid)
          val fields = Vector(
            Some(s"  title = {${_bibtex_escape(title)}}"),
            year.map(x => s"  year = {$x}"),
            Some(s"  url = {$url}")
          ).flatten.mkString(",\n")
          s"@book{$key,\n$fields\n}\n"
        }
      }
    }

  private def _year(value: String): Option[String] =
    """([0-9]{4})""".r.findFirstIn(value)

  private def _safe_citation_key(value: String): String =
    value.replaceAll("[^A-Za-z0-9]+", "").toLowerCase(java.util.Locale.ROOT) match {
      case "" => "reference"
      case x => x
    }

  private def _bibtex_escape(value: String): String =
    value.replace("{", "").replace("}", "")
}

private[cozy] object BibliographyBibtexParser {
  def parse(text: String): Option[Map[String, String]] = {
    val bib = "(?s)@([A-Za-z]+)\\s*\\{\\s*([^,]+),(.+)\\}".r
    text.trim match {
      case bib(kind, id, body) =>
        Some(_parse_fields(body) ++ Map("type" -> kind.toLowerCase(java.util.Locale.ROOT), "id" -> id.trim))
      case _ => None
    }
  }

  private def _parse_fields(body: String): Map[String, String] = {
    var rest = body.trim
    var fields = Map.empty[String, String]
    while (rest.nonEmpty) {
      _read_field(rest) match {
        case Some((key, value, tail)) =>
          if (value.nonEmpty)
            fields += key.toLowerCase(java.util.Locale.ROOT) -> value
          rest = tail.dropWhile(c => c == ',' || c.isWhitespace)
        case None =>
          rest = ""
      }
    }
    fields
  }

  private def _read_field(text: String): Option[(String, String, String)] = {
    val field = """^\s*([A-Za-z][A-Za-z0-9_-]*)\s*=""".r
    field.findFirstMatchIn(text).flatMap { m =>
      val key = m.group(1)
      val start = m.end
      _read_value(text, start).map { case (value, end) =>
        (key, value.trim, text.substring(end))
      }
    }
  }

  private def _read_value(text: String, start: Int): Option[(String, Int)] = {
    val offset = _skip_space(text, start)
    if (offset >= text.length)
      None
    else
      text.charAt(offset) match {
        case '{' => Some(_read_braced_value(text, offset))
        case '"' => Some(_read_quoted_value(text, offset))
        case _ => Some(_read_bare_value(text, offset))
      }
  }

  private def _read_braced_value(text: String, start: Int): (String, Int) = {
    var i = start + 1
    var depth = 1
    val out = new StringBuilder
    while (i < text.length && depth > 0) {
      val c = text.charAt(i)
      if (c == '{') {
        depth += 1
        out.append(c)
      } else if (c == '}') {
        depth -= 1
        if (depth > 0)
          out.append(c)
      } else {
        out.append(c)
      }
      i += 1
    }
    (out.toString, i)
  }

  private def _read_quoted_value(text: String, start: Int): (String, Int) = {
    var i = start + 1
    val out = new StringBuilder
    var escaped = false
    var done = false
    while (i < text.length && !done) {
      val c = text.charAt(i)
      if (escaped) {
        out.append(c)
        escaped = false
      } else if (c == '\\') {
        out.append(c)
        escaped = true
      } else if (c == '"') {
        done = true
      } else {
        out.append(c)
      }
      i += 1
    }
    (out.toString, i)
  }

  private def _read_bare_value(text: String, start: Int): (String, Int) = {
    val end = text.indexOf(',', start) match {
      case -1 => text.length
      case n => n
    }
    (text.substring(start, end).trim, end)
  }

  private def _skip_space(text: String, start: Int): Int = {
    var i = start
    while (i < text.length && text.charAt(i).isWhitespace)
      i += 1
    i
  }

  def authors(value: String): Vector[String] =
    value.split("\\s+and\\s+").toVector.map(_.trim).filter(_.nonEmpty)

  def citation(fields: Map[String, String]): Option[String] =
    fields.get("title").map { title =>
      val author = fields.get("author").map(authors).getOrElse(Vector.empty).headOption.getOrElse("-")
      val year = fields.get("year").getOrElse("n.d.")
      s"${author}. ${title}. ${year}."
    }
}
