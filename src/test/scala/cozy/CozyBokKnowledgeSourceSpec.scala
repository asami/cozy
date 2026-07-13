package cozy

import cozy.bok.CozyBok
import io.circe.Json
import io.circe.parser
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import scala.collection.JavaConverters._

/*
 * @since   Jul. 13, 2026
 * @version Jul. 13, 2026
 * @author  ASAMI, Tomoharu
 */
class CozyBokKnowledgeSourceSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  "Cozy BoK KnowledgeSource publication" should {
    "publish the CNCF KnowledgeSource manifest" which {
      "lists generated glossary and RDF metadata using relative references" in {
        _with_temp_dir("cozy-bok-knowledge-source") { dir =>
          Given("a BoK site with explicit identity and complete SmartDox metadata")
          _write_site_source(dir, glossaryterm = true)

          When("Cozy builds the public BoK site")
          CozyBok.build(_build_config(dir), new MetadataRunner(includeterms = true, includerdf = true))

          Then("the public site contains a deterministic KnowledgeSource manifest")
          val manifestpath = dir.resolve("website.d/metadata/cncf/knowledge-source.json")
          manifestpath should be_regular_file
          val manifest = _parse_json(manifestpath)
          val cursor = manifest.hcursor
          cursor.get[String]("schemaVersion") shouldBe Right("cncf.knowledge-source.v1")
          cursor.get[String]("kind") shouldBe Right("bok-site")
          cursor.get[String]("id") shouldBe Right("knowledgehub")
          cursor.get[String]("label") shouldBe Right("KnowledgeHub")
          cursor.downField("sourceRef").get[String]("kind") shouldBe Right("bok-site")
          cursor.downField("sourceRef").get[String]("value") shouldBe Right("knowledgehub")
          cursor.downField("sourceRef").get[String]("uri") shouldBe Right("https://example.com/knowledgehub/")

          And("the manifest declares only relative machine-readable resources")
          _resources(manifest) shouldBe Vector(
            ("glossary-terms", "metadata/glossary/terms.json", "application/json"),
            ("rdf-jsonld", "rdf/site.jsonld", "application/ld+json"),
            ("rdf-turtle", "rdf/site.ttl", "text/turtle"),
            ("rdf-graph-summary", "metadata/rdf/graph.json", "application/json")
          )
          _resources(manifest).map(_._2).forall(x => !x.startsWith("/") && !x.contains("://")) shouldBe true
          dir.resolve("website.d/.well-known/cncf-knowledge.json") shouldNot exist_path
        }
      }

      "omits RDF resources that were not generated" in {
        _with_temp_dir("cozy-bok-knowledge-source-no-rdf") { dir =>
          Given("a BoK site whose SmartDox output contains glossary metadata but no RDF output")
          _write_site_source(dir, glossaryterm = true)

          When("Cozy builds the public BoK site")
          CozyBok.build(_build_config(dir), new MetadataRunner(includeterms = true, includerdf = false))

          Then("the manifest lists the glossary resource without inventing RDF resources")
          _resources(_parse_json(dir.resolve("website.d/metadata/cncf/knowledge-source.json"))) shouldBe Vector(
            ("glossary-terms", "metadata/glossary/terms.json", "application/json")
          )
        }
      }

      "derives a stable site identifier when an explicit identifier is absent" in {
        _with_temp_dir("cozy-bok-knowledge-source-derived-id") { dir =>
          Given("a BoK site with a name but no explicit site identifier or public URL")
          _write_site_source(
            dir,
            glossaryterm = false,
            siteid = None,
            siteurl = None,
            sitename = "KnowledgeHub BoK"
          )

          When("Cozy builds the public BoK site")
          CozyBok.build(_build_config(dir), new MetadataRunner(includeterms = false, includerdf = false))

          Then("the normalized site name becomes the manifest and source reference identifier")
          val manifest = _parse_json(dir.resolve("website.d/metadata/cncf/knowledge-source.json"))
          manifest.hcursor.get[String]("id") shouldBe Right("knowledgehub-bok")
          manifest.hcursor.downField("sourceRef").get[String]("value") shouldBe Right("knowledgehub-bok")
          manifest.hcursor.downField("sourceRef").get[Option[String]]("uri") shouldBe Right(None)
        }
      }

      "keeps the canonical manifest path for multi-locale sites" in {
        _with_temp_dir("cozy-bok-knowledge-source-multi-locale") { dir =>
          Given("a multi-locale BoK site with SmartDox glossary and RDF metadata")
          _write_site_source(
            dir,
            glossaryterm = true,
            localemode = "multi_locale_subdirs",
            languages = Vector("ja", "en")
          )

          When("Cozy builds each locale below the public site root")
          CozyBok.build(_build_config(dir), new MetadataRunner(includeterms = true, includerdf = true))

          Then("the canonical root manifest and its relative resources remain available")
          val websiteroot = dir.resolve("website.d")
          val manifest = _parse_json(websiteroot.resolve("metadata/cncf/knowledge-source.json"))
          _resources(manifest) shouldBe Vector(
            ("glossary-terms", "metadata/glossary/terms.json", "application/json"),
            ("rdf-jsonld", "rdf/site.jsonld", "application/ld+json"),
            ("rdf-turtle", "rdf/site.ttl", "text/turtle"),
            ("rdf-graph-summary", "metadata/rdf/graph.json", "application/json")
          )
          _resources(manifest).foreach { case (_, href, _) =>
            websiteroot.resolve(href) should be_regular_file
          }

          And("locale-specific sites retain their own machine-readable handoff")
          dir.resolve("website.d/ja/metadata/cncf/knowledge-source.json") should be_regular_file
          dir.resolve("website.d/en/metadata/cncf/knowledge-source.json") should be_regular_file
        }
      }

      "fails explicitly when authored glossary terms lack SmartDox metadata" in {
        _with_temp_dir("cozy-bok-knowledge-source-missing-terms") { dir =>
          Given("an authored glossary term and a SmartDox runtime that omits terms metadata")
          _write_site_source(dir, glossaryterm = true)

          When("Cozy builds the public BoK site")
          val error = intercept[Throwable] {
            CozyBok.build(_build_config(dir), new MetadataRunner(includeterms = false, includerdf = false))
          }

          Then("the missing SmartDox handoff is reported instead of reconstructed")
          error.getMessage should include("SmartDox glossary metadata was not generated")
          error.getMessage should include("Cozy does not reconstruct the missing terms.json handoff")
        }
      }
    }
  }

  private class MetadataRunner(includeterms: Boolean, includerdf: Boolean) extends CozyBok.Runner {
    def run(command: Vector[String], cwd: Path): Unit =
      if (command.take(2) == Vector("dox", "site")) {
        if (includeterms)
          _write(cwd.resolve("doxsite.d/metadata/glossary/terms.json"), "{\"terms\":[]}\n")
        if (includerdf) {
          _write(cwd.resolve("doxsite.d/metadata/rdf/graph.json"), "{\"nodes\":[],\"edges\":[],\"truncated\":false}\n")
          _write(cwd.resolve("doxsite.d/site.jsonld"), "{\"@graph\":[]}\n")
          _write(cwd.resolve("doxsite.d/site.ttl"), "@prefix ex: <https://example.com/> .\n")
        }
      }
  }

  private def _build_config(dir: Path): CozyBok.BuildConfig =
    CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview"))

  private def _write_site_source(
      dir: Path,
      glossaryterm: Boolean,
      siteid: Option[String] = Some("knowledgehub"),
      siteurl: Option[String] = Some("https://example.com/knowledgehub"),
      sitename: String = "KnowledgeHub",
      localemode: String = "single_locale_root",
      languages: Vector[String] = Vector("en")
  ): Unit = {
    val idproperty = siteid.map(x => "    id = \"" + x + "\"\n").getOrElse("")
    val urlproperty = siteurl.map(x => "    url = \"" + x + "\"\n").getOrElse("")
    val languageproperty = languages.map(x => "\"" + x + "\"").mkString("[", ", ", "]")
    _write(
      dir.resolve("src/main/doxsite/site.conf"),
      s"""site {
         |  metadata {
         |${idproperty}    name = "${sitename}"
         |${urlproperty}    in_language = ${languageproperty}
         |  }
         |  output {
         |    locale_mode = "${localemode}"
         |    default_locale = "en"
         |  }
         |}
         |""".stripMargin
    )
    if (glossaryterm)
      _write(
        dir.resolve("src/main/doxsite/glossary/technology/embedding.dox"),
        "Embedding\n=========\n\n# Definition\n\nA vector representation.\n"
      )
  }

  private def _resources(json: Json): Vector[(String, String, String)] =
    json.hcursor.downField("resources").as[Vector[Json]].fold(throw _, identity).map { resource =>
      val cursor = resource.hcursor
      (
        cursor.get[String]("kind").fold(throw _, identity),
        cursor.get[String]("href").fold(throw _, identity),
        cursor.get[String]("mediaType").fold(throw _, identity)
      )
    }

  private def _parse_json(path: Path): Json =
    parser.parse(_read(path)).fold(throw _, identity)

  private def _with_temp_dir[A](name: String)(f: Path => A): A = {
    val dir = Files.createTempDirectory(name)
    try {
      f(dir)
    } finally {
      _delete(dir)
    }
  }

  private def _write(path: Path, content: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))
  }

  private def _read(path: Path): String =
    Files.readString(path, StandardCharsets.UTF_8)

  private def _delete(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try {
        stream.iterator.asScala.toVector.reverse.foreach(Files.deleteIfExists)
      } finally {
        stream.close()
      }
    }
}
