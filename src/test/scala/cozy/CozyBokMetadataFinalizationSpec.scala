package cozy

import cozy.bok.CozyBok
import io.circe.Json
import io.circe.parser
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path}
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import scala.collection.JavaConverters._

/*
 * @since   Aug. 23, 2026
 * @version Aug. 23, 2026
 * @author  ASAMI, Tomoharu
 */
class CozyBokMetadataFinalizationSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  "Cozy BoK metadata finalization" should {
    "finalize prepared glossary and RDF metadata without changing rendered HTML" in {
      _with_temp_dir("cozy-bok-metadata-finalization-success") { dir =>
        Given("an existing configured website with a prepared generated glossary and RDF handoff")
        _write_site_source(dir, glossaryterm = true)
        _write_prepared_metadata(dir, includeterms = true)
        _write(dir.resolve("website.d/index.html"), "<html>project-owned-marker</html>\n")

        When("the public metadata finalization API runs without a site-build runner")
        CozyBok.finalizeMetadata(_build_config(dir))

        Then("it writes the canonical machine metadata while preserving the project-owned HTML")
        _read(dir.resolve("website.d/index.html")) shouldBe "<html>project-owned-marker</html>\n"
        val manifest = _parse_json(dir.resolve("website.d/metadata/cncf/knowledge-source.json"))
        _resources(manifest) shouldBe Vector(
          ("glossary-terms", "metadata/glossary/terms.json"),
          ("rdf-jsonld", "rdf/site.jsonld"),
          ("rdf-turtle", "rdf/site.ttl"),
          ("rdf-graph-summary", "metadata/rdf/graph.json")
        )
        _read(dir.resolve("website.d/metadata/rdf/graph.json")) should include("\"schemaVersion\" : \"cozy.rdf-graph-summary.v1\"")
      }
    }

    "reject a declared glossary with no generated terms before changing the website" in {
      _with_temp_dir("cozy-bok-metadata-finalization-missing-terms") { dir =>
        Given("a source-declared glossary, generated RDF metadata, and existing website metadata sentinels")
        _write_site_source(dir, glossaryterm = true)
        _write_prepared_metadata(dir, includeterms = false)
        _write(dir.resolve("website.d/index.html"), "<html>project-owned-marker</html>\n")
        _write(dir.resolve("website.d/metadata/cncf/knowledge-source.json"), "sentinel-manifest\n")

        When("metadata finalization sees the missing SmartDox glossary terms handoff")
        val error = intercept[Throwable] {
          CozyBok.finalizeMetadata(_build_config(dir))
        }

        Then("it reports the existing glossary diagnostic and leaves every sentinel intact")
        error.getMessage should include("SmartDox glossary metadata was not generated")
        _read(dir.resolve("website.d/index.html")) shouldBe "<html>project-owned-marker</html>\n"
        _read(dir.resolve("website.d/metadata/cncf/knowledge-source.json")) shouldBe "sentinel-manifest\n"
      }
    }

    "produce byte-identical website output when prepared metadata is finalized twice" in {
      _with_temp_dir("cozy-bok-metadata-finalization-deterministic") { dir =>
        Given("an existing website and unchanged prepared generated metadata")
        _write_site_source(dir, glossaryterm = true)
        _write_prepared_metadata(dir, includeterms = true)
        _write(dir.resolve("website.d/index.html"), "<html>project-owned-marker</html>\n")
        val config = _build_config(dir)

        When("the public finalizer runs twice over the same inputs")
        CozyBok.finalizeMetadata(config)
        val first = _tree_contents(dir.resolve("website.d"))
        CozyBok.finalizeMetadata(config)
        val second = _tree_contents(dir.resolve("website.d"))

        Then("the complete project-owned website tree remains byte-identical")
        second shouldBe first
      }
    }

    "dispatch the finalization command without accepting an external runner" in {
      _with_temp_dir("cozy-bok-metadata-finalization-command") { dir =>
        Given("an already generated configured metadata fixture and an existing website")
        _write_site_source(dir, glossaryterm = false)
        _write_prepared_metadata(dir, includeterms = false)
        _write(dir.resolve("website.d/index.html"), "<html>project-owned-marker</html>\n")

        When("the public command dispatcher handles finalize-metadata directly")
        val handled = CozyBok.execute(List("bok", "finalize-metadata", dir.toString, "--strategy", "preview"))

        Then("the command succeeds through the runner-free finalization boundary")
        handled shouldBe true
        Files.isRegularFile(dir.resolve("website.d/metadata/cncf/knowledge-source.json"), LinkOption.NOFOLLOW_LINKS) shouldBe true
      }
    }
  }

  private def _build_config(dir: Path): CozyBok.BuildConfig =
    CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview"))

  private def _write_site_source(dir: Path, glossaryterm: Boolean): Unit = {
    _write(
      dir.resolve("src/main/doxsite/site.conf"),
      """site {
        |  metadata {
        |    id = "knowledgehub"
        |    name = "KnowledgeHub"
        |    url = "https://example.com/knowledgehub"
        |    in_language = ["en"]
        |  }
        |  output {
        |    locale_mode = "single_locale_root"
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

  private def _write_prepared_metadata(dir: Path, includeterms: Boolean): Unit = {
    val doxsite = dir.resolve("doxsite.d")
    _write(doxsite.resolve("site.ttl"), "@prefix ex: <https://example.com/> .\n")
    _write(doxsite.resolve("site.jsonld"), "{\"@graph\":[]}\n")
    _write(
      doxsite.resolve("metadata/rdf/graph.json"),
      """{
        |  "nodes": [],
        |  "edges": [],
        |  "truncated": false
        |}
        |""".stripMargin
    )
    if (includeterms)
      _write(doxsite.resolve("metadata/glossary/terms.json"), "{\"terms\":[]}\n")
  }

  private def _resources(json: Json): Vector[(String, String)] =
    json.hcursor.downField("resources").as[Vector[Json]].fold(throw _, identity).map { resource =>
      val cursor = resource.hcursor
      (
        cursor.get[String]("kind").fold(throw _, identity),
        cursor.get[String]("href").fold(throw _, identity)
      )
    }

  private def _tree_contents(root: Path): Map[String, String] = {
    val stream = Files.walk(root)
    try {
      stream.iterator.asScala.toVector.
        filter(Files.isRegularFile(_, LinkOption.NOFOLLOW_LINKS)).
        sortBy(path => root.relativize(path).toString).
        map(path => root.relativize(path).toString -> _read(path)).toMap
    } finally {
      stream.close()
    }
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
    new String(Files.readAllBytes(path), StandardCharsets.UTF_8)

  private def _delete(path: Path): Unit =
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      val stream = Files.walk(path)
      try {
        stream.iterator.asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.deleteIfExists)
      } finally {
        stream.close()
      }
    }
}
