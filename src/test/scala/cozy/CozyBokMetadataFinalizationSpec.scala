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
 * @version Aug. 28, 2026
 * @author  ASAMI, Tomoharu
 */
class CozyBokMetadataFinalizationSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  "Cozy BoK metadata finalization" should {
    "prepared metadata finalization" which {
    "finalize prepared glossary and RDF metadata without changing rendered HTML" in {
      _with_temp_dir("cozy-bok-metadata-finalization-success") { dir =>
        Given("an existing configured website with a prepared generated glossary and RDF handoff")
        _write_site_source(dir, glossaryterm = true)
        _write_prepared_metadata(
          dir,
          includeterms = true,
          rdfgraph = _component_ref_graph(),
          componentreferences = _component_reference_indexes()
        )
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
          ("rdf-graph-summary", "metadata/rdf/graph.json"),
          ("component-reference-index", "metadata/cncf/component-references/car.json"),
          ("component-reference-index", "metadata/cncf/component-references/sar.json")
        )
        val graph = _parse_json(dir.resolve("website.d/metadata/rdf/graph.json"))
        graph.hcursor.get[String]("schemaVersion") shouldBe Right("cozy.rdf-graph-summary.v1")
        _graph_component_refs(graph) shouldBe Vector(
          ("car", "textus-bok", Some("org.textus"), Some("0.6.0")),
          ("sar", "textus-search", None, Some("1.2.0"))
        )
      }
    }

    "ignore an authored source graph when generated graph metadata is valid" in {
      _with_temp_dir("cozy-bok-metadata-finalization-source-graph-ignored") { dir =>
        Given("valid generated graph metadata and an authored source graph with an extra node")
        _write_site_source(dir, glossaryterm = false)
        _write(dir.resolve("website.d/index.html"), "<html>source-graph-sentinel</html>\n")
        _write_prepared_metadata(
          dir,
          includeterms = false,
          rdfgraph = _component_ref_graph(),
          componentreferences = _component_reference_indexes()
        )
        _write(
          dir.resolve("src/main/doxsite/metadata/rdf/graph.json"),
          """{
            |  "nodes": [
            |    {"id": "authored-overlay", "label": "Authored overlay", "node_type": "concept"}
            |  ],
            |  "edges": [],
            |  "truncated": false
            |}
            |""".stripMargin
        )

        When("the public metadata finalization API publishes the generated handoff")
        CozyBok.finalizeMetadata(_build_config(dir))

        Then("the published graph contains only generated nodes and not the authored overlay")
        val graph = _parse_json(dir.resolve("website.d/metadata/rdf/graph.json"))
        val nodeids = graph.hcursor.downField("nodes").as[Vector[Json]].fold(throw _, identity).map { node =>
          node.hcursor.get[String]("id").fold(throw _, identity)
        }
        nodeids shouldBe Vector("car:textus-bok", "sar:textus-search")
      }
    }

    "not publish an authored source graph when generated graph metadata is missing" in {
      _with_temp_dir("cozy-bok-metadata-finalization-source-graph-missing") { dir =>
        Given("missing generated graph metadata and an authored source graph")
        _write_site_source(dir, glossaryterm = false)
        _write(dir.resolve("website.d/index.html"), "<html>source-graph-sentinel</html>\n")
        _write_prepared_metadata(dir, includeterms = false)
        Files.deleteIfExists(dir.resolve("doxsite.d/metadata/rdf/graph.json"))
        _write(
          dir.resolve("src/main/doxsite/metadata/rdf/graph.json"),
          """{
            |  "nodes": [
            |    {"id": "authored-only", "label": "Authored only", "node_type": "concept"}
            |  ],
            |  "edges": [],
            |  "truncated": false
            |}
            |""".stripMargin
        )

        When("the public metadata finalization API publishes the available generated handoff")
        CozyBok.finalizeMetadata(_build_config(dir))

        Then("it publishes no graph resource or graph manifest entry from the authored source")
        Files.exists(
          dir.resolve("website.d/metadata/rdf/graph.json"),
          LinkOption.NOFOLLOW_LINKS
        ) shouldBe false
        val manifest = _parse_json(dir.resolve("website.d/metadata/cncf/knowledge-source.json"))
        _resources(manifest) shouldBe Vector(
          ("rdf-jsonld", "rdf/site.jsonld"),
          ("rdf-turtle", "rdf/site.ttl")
        )
      }
    }

    "finalize a source-declared glossary without RDF metadata" in {
      _with_temp_dir("cozy-bok-metadata-finalization-glossary-only") { dir =>
        Given("a configured website with source-declared glossary terms and only generated glossary metadata")
        _write_site_source(dir, glossaryterm = true)
        _write(dir.resolve("doxsite.d/metadata/glossary/terms.json"), "{\"terms\":[]}\n")
        _write(dir.resolve("website.d/index.html"), "<html>project-owned-marker</html>\n")

        When("the public metadata finalization API runs directly without RDF outputs")
        CozyBok.finalizeMetadata(_build_config(dir))

        Then("finalization succeeds with the glossary resource as the complete manifest inventory")
        Files.isRegularFile(
          dir.resolve("website.d/metadata/glossary/terms.json"),
          LinkOption.NOFOLLOW_LINKS
        ) shouldBe true
        val manifest = _parse_json(dir.resolve("website.d/metadata/cncf/knowledge-source.json"))
        _resources(manifest) shouldBe Vector(("glossary-terms", "metadata/glossary/terms.json"))
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

    "reject a malformed generated glossary before staging when the graph has no component references" in {
      _with_temp_dir("cozy-bok-metadata-finalization-malformed-glossary") { dir =>
        Given("a generated glossary with a decoder-invalid term and four existing website metadata sentinels")
        _write_site_source(dir, glossaryterm = false)
        _write_prepared_metadata(
          dir,
          includeterms = false,
          rdfgraph = "{\"nodes\":[],\"edges\":[],\"truncated\":false}\n"
        )
        _write(dir.resolve("doxsite.d/metadata/glossary/terms.json"), "{\"terms\":[{}]}\n")
        _write(dir.resolve("website.d/index.html"), "<html>glossary-sentinel</html>\n")
        _write(dir.resolve("website.d/metadata/cncf/knowledge-source.json"), "manifest-glossary-sentinel\n")
        _write(dir.resolve("website.d/metadata/glossary/terms.json"), "website-terms-sentinel\n")
        _write(dir.resolve("website.d/metadata/cncf/component-references/car.json"), "website-car-sentinel\n")

        When("the public metadata finalization API validates the generated resources")
        val error = intercept[Throwable] {
          CozyBok.finalizeMetadata(_build_config(dir))
        }

        Then("it identifies the glossary resource and leaves every website sentinel byte-identical")
        error.getMessage should include("metadata/glossary/terms.json")
        _read(dir.resolve("website.d/index.html")) shouldBe "<html>glossary-sentinel</html>\n"
        _read(dir.resolve("website.d/metadata/cncf/knowledge-source.json")) shouldBe "manifest-glossary-sentinel\n"
        _read(dir.resolve("website.d/metadata/glossary/terms.json")) shouldBe "website-terms-sentinel\n"
        _read(dir.resolve("website.d/metadata/cncf/component-references/car.json")) shouldBe "website-car-sentinel\n"
      }
    }

    "reject a malformed generated CAR index before staging when the graph has no component references" in {
      _with_temp_dir("cozy-bok-metadata-finalization-malformed-car") { dir =>
        Given("a generated CAR index with invalid entries and four existing website metadata sentinels")
        _write_site_source(dir, glossaryterm = false)
        _write_prepared_metadata(
          dir,
          includeterms = false,
          rdfgraph = "{\"nodes\":[],\"edges\":[],\"truncated\":false}\n",
          componentreferences = Vector(
            "car" ->
              "{\"schemaVersion\":\"cncf.component-reference-index.v1\",\"kind\":\"car\",\"entries\":{}}\n"
          )
        )
        _write(dir.resolve("website.d/index.html"), "<html>car-sentinel</html>\n")
        _write(dir.resolve("website.d/metadata/cncf/knowledge-source.json"), "manifest-car-sentinel\n")
        _write(dir.resolve("website.d/metadata/glossary/terms.json"), "website-terms-sentinel\n")
        _write(dir.resolve("website.d/metadata/cncf/component-references/car.json"), "website-car-sentinel\n")

        When("the public metadata finalization API validates the generated resources")
        val error = intercept[Throwable] {
          CozyBok.finalizeMetadata(_build_config(dir))
        }

        Then("it identifies the CAR index and leaves every website sentinel byte-identical")
        error.getMessage should include("metadata/cncf/component-references/car.json")
        _read(dir.resolve("website.d/index.html")) shouldBe "<html>car-sentinel</html>\n"
        _read(dir.resolve("website.d/metadata/cncf/knowledge-source.json")) shouldBe "manifest-car-sentinel\n"
        _read(dir.resolve("website.d/metadata/glossary/terms.json")) shouldBe "website-terms-sentinel\n"
        _read(dir.resolve("website.d/metadata/cncf/component-references/car.json")) shouldBe "website-car-sentinel\n"
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

    "source/output safety admission" which {
    "reject an unsafe bok.source from the public build command before reading site.conf" in {
      _with_temp_dir("cozy-bok-metadata-finalization-command-source") { dir =>
        val outside = Files.createTempDirectory("cozy-bok-unsafe-source")
        try {
          Given("a project config whose bok.source escapes the project and an outside site.conf that would reject if read")
          _write_site_source(dir, glossaryterm = false)
          _write(outside.resolve("site.conf"), "site.output.locale_mode = unsupported\n")
          _write(
            dir.resolve("conf/cozy/config.yaml"),
            s"""bok:
               |  source: ${dir.relativize(outside).toString}
               |""".stripMargin
          )
          _write(dir.resolve("website.d/index.html"), "<html>command-source-sentinel</html>\n")

          When("the public bok build command resolves its configured source")
          val error = intercept[Throwable] {
            CozyBok.execute(List("bok", "build", dir.toString))
          }

          Then("it rejects the source before reading site.conf or mutating build output")
          error.getMessage should include("BoK configured source root must be inside the project root")
          _read(dir.resolve("website.d/index.html")) shouldBe "<html>command-source-sentinel</html>\n"
        } finally {
          _delete(outside)
        }
      }
    }

    "reject a symbolic-link bok.source from the public build command before reading site.conf" in {
      _with_temp_dir("cozy-bok-metadata-finalization-command-source-link") { dir =>
        Given("a project config whose bok.source is a symbolic link and an external site.conf that would reject if read")
        val outside = Files.createTempDirectory("cozy-bok-unsafe-source-link")
        try {
          _write(outside.resolve("site.conf"), "site.output.locale_mode = unsupported\n")
          Files.createSymbolicLink(dir.resolve("source-link"), outside)
          _write(
            dir.resolve("conf/cozy/config.yaml"),
            "bok:\n  source: source-link\n"
          )
          _write(dir.resolve("website.d/index.html"), "<html>command-source-link-sentinel</html>\n")

          When("the public bok build command resolves its configured source")
          val error = intercept[Throwable] {
            CozyBok.execute(List("bok", "build", dir.toString))
          }

          Then("it rejects the symbolic link before reading site.conf or mutating build output")
          error.getMessage should include("BoK configured source root must be an existing non-symbolic-link directory")
          _read(dir.resolve("website.d/index.html")) shouldBe "<html>command-source-link-sentinel</html>\n"
        } finally {
          _delete(outside)
        }
      }
    }

    "reject malformed graph and unmatched component references before changing website sentinels" in {
      Vector(
        "malformed graph" -> (
          "{\"nodes\":[],\"edges\":{},\"truncated\":false}\n",
          Vector.empty[(String, String)],
          "Invalid BoK RDF graph metadata: edges must be an array."
        ),
        "unmatched component reference" -> (
          _component_ref_graph(),
          Vector(
            "car" -> _component_reference_index_json(
              "car",
              Vector(_component_reference_entry_json("car", "other-component", None, Vector("0.6.0")))
            ),
            "sar" -> _component_reference_index_json(
              "sar",
              Vector(_component_reference_entry_json("sar", "textus-search", None, Vector("1.2.0")))
            )
          ),
          "does not match any car component-reference index entry."
        )
      ).foreach { case (label, (rdfgraph, componentreferences, message)) =>
        _with_temp_dir(s"cozy-bok-metadata-finalization-invalid-${label.replace(' ', '-')}") { dir =>
          Given(s"a configured website with a $label and existing metadata sentinels")
          _write_site_source(dir, glossaryterm = false)
          _write_prepared_metadata(dir, includeterms = false, rdfgraph = rdfgraph, componentreferences = componentreferences)
          _write(dir.resolve("website.d/index.html"), "<html>project-owned-marker</html>\n")
          _write(dir.resolve("website.d/metadata/cncf/knowledge-source.json"), "sentinel-manifest\n")

          When("metadata finalization validates the prepared handoff")
          val error = intercept[Throwable] {
            CozyBok.finalizeMetadata(_build_config(dir))
          }

          Then("the existing diagnostic is reported before either website sentinel changes")
          error.getMessage should include(message)
          _read(dir.resolve("website.d/index.html")) shouldBe "<html>project-owned-marker</html>\n"
          _read(dir.resolve("website.d/metadata/cncf/knowledge-source.json")) shouldBe "sentinel-manifest\n"
        }
      }
    }

    "reject a symbolic-link website root before finalization writes" in {
      _with_temp_dir("cozy-bok-metadata-finalization-symbolic-website") { dir =>
        Given("a prepared metadata handoff and a configured website root that is a symbolic link")
        _write_site_source(dir, glossaryterm = false)
        _write_prepared_metadata(dir, includeterms = false)
        val websitetarget = dir.resolve("website-target")
        _write(websitetarget.resolve("index.html"), "<html>project-owned-marker</html>\n")
        Files.createSymbolicLink(dir.resolve("website.d"), websitetarget)

        When("metadata finalization validates the configured roots")
        val error = intercept[Throwable] {
          CozyBok.finalizeMetadata(_build_config(dir))
        }

        Then("the symbolic-link root is rejected without changing its target")
        error.getMessage should include("BoK website root must be an existing non-symbolic-link directory")
        _read(websitetarget.resolve("index.html")) shouldBe "<html>project-owned-marker</html>\n"
      }
    }

    "reject unsafe configured source paths before finalization writes" in {
      Vector("outside-root", "non-directory", "symbolic-link", "symbolic-parent").foreach { variant =>
        _with_temp_dir(s"cozy-bok-metadata-finalization-source-$variant") { dir =>
          Given(s"a configured source path that is $variant and existing website metadata sentinels")
          _write_site_source(dir, glossaryterm = false)
          _write_prepared_metadata(dir, includeterms = false)
          _write(dir.resolve("website.d/index.html"), "<html>project-owned-marker</html>\n")
          _write(dir.resolve("website.d/metadata/cncf/knowledge-source.json"), "sentinel-manifest\n")
          val baseconfig = _build_config(dir)
          val config = variant match {
            case "outside-root" =>
              baseconfig.copy(source = dir.resolve("../outside-source").normalize.toString)
            case "non-directory" =>
              val sourcefile = dir.resolve("source-file")
              _write(sourcefile, "not-a-directory\n")
              baseconfig.copy(source = "source-file")
            case "symbolic-link" =>
              val sourcetarget = dir.resolve("source-target")
              Files.createDirectories(sourcetarget)
              Files.createSymbolicLink(dir.resolve("source-link"), sourcetarget)
              baseconfig.copy(source = "source-link")
            case "symbolic-parent" =>
              val sourcetarget = dir.resolve("source-target")
              Files.createDirectories(sourcetarget.resolve("nested"))
              Files.createSymbolicLink(dir.resolve("source-parent-link"), sourcetarget)
              baseconfig.copy(source = "source-parent-link/nested")
          }

          When("metadata finalization validates the configured source admission")
          val error = intercept[Throwable] {
            CozyBok.finalizeMetadata(config)
          }

          Then("it reports the rejected source and leaves every website sentinel unchanged")
          error.getMessage should include("BoK configured source root")
          _read(dir.resolve("website.d/index.html")) shouldBe "<html>project-owned-marker</html>\n"
          _read(dir.resolve("website.d/metadata/cncf/knowledge-source.json")) shouldBe "sentinel-manifest\n"
        }
      }
    }
    }

    "normal BoK build finalization" which {
    "share normalized finalization with a production build using only a local runner" in {
      _with_temp_dir("cozy-bok-metadata-finalization-production") { dir =>
        Given("a production BoK source and a local runner that writes prepared Dox metadata")
        _write_site_source(dir, glossaryterm = false)
        val runner = new LocalMetadataRunner

        When("Cozy builds with strategy production through the fake runner")
        CozyBok.build(_build_config(dir, strategy = "production"), runner)

        Then("the ordinary build path reaches the same versioned graph and manifest finalization")
        runner.commands should not be empty
        runner.commands.exists(_.take(2) == Vector("dox", "site")) shouldBe true
        val manifest = _parse_json(dir.resolve("website.d/metadata/cncf/knowledge-source.json"))
        _resources(manifest) shouldBe Vector(
          ("rdf-jsonld", "rdf/site.jsonld"),
          ("rdf-turtle", "rdf/site.ttl"),
          ("rdf-graph-summary", "metadata/rdf/graph.json"),
          ("component-reference-index", "metadata/cncf/component-references/car.json"),
          ("component-reference-index", "metadata/cncf/component-references/sar.json")
        )
        val graph = _parse_json(dir.resolve("website.d/metadata/rdf/graph.json"))
        graph.hcursor.get[String]("schemaVersion") shouldBe Right("cozy.rdf-graph-summary.v1")
        _graph_component_refs(graph) shouldBe Vector(
          ("car", "textus-bok", Some("org.textus"), Some("0.6.0")),
          ("sar", "textus-search", None, Some("1.2.0"))
        )
      }
    }

    "reject an unsafe configured source before ordinary build mutation or runner invocation" in {
      _with_temp_dir("cozy-bok-metadata-finalization-build-source") { dir =>
        Given("a configured BoK build with an outside-root source and an existing website sentinel")
        _write_site_source(dir, glossaryterm = false)
        _write(dir.resolve("website.d/index.html"), "<html>build-source-sentinel</html>\n")
        val runner = new LocalMetadataRunner
        val config = _build_config(dir).copy(source = dir.resolve("../outside-source").normalize.toString)

        When("the ordinary public build path admits its configured source")
        val error = intercept[Throwable] {
          CozyBok.build(config, runner)
        }

        Then("it reports the configured source diagnostic before changing output or invoking the runner")
        error.getMessage should include("BoK configured source root")
        _read(dir.resolve("website.d/index.html")) shouldBe "<html>build-source-sentinel</html>\n"
        runner.commands shouldBe empty
      }
    }

    "reject a safe-but-absent source before ordinary build mutation or runner invocation" in {
      _with_temp_dir("cozy-bok-metadata-finalization-build-absent-source") { dir =>
        Given("a configured BoK build whose in-project source directory is absent and an existing website sentinel")
        _write(dir.resolve("website.d/index.html"), "<html>build-absent-source-sentinel</html>\n")
        val runner = new LocalMetadataRunner
        val config = _build_config(dir)

        When("the ordinary public build path admits its configured source")
        val error = intercept[Throwable] {
          CozyBok.build(config, runner)
        }

        Then("it rejects the absent source before changing output or invoking the runner")
        error.getMessage should include("BoK configured source root must be an existing non-symbolic-link directory")
        _read(dir.resolve("website.d/index.html")) shouldBe "<html>build-absent-source-sentinel</html>\n"
        runner.commands shouldBe empty
      }
    }

    "reject malformed generated CAR before an ordinary build copies machine metadata" in {
      _with_temp_dir("cozy-bok-metadata-finalization-build-malformed-car") { dir =>
        Given("a BoK source and a local runner that writes a no-reference RDF graph with a malformed CAR index")
        _write_site_source(dir, glossaryterm = false)
        val runner = new LocalMetadataRunner(
          rdfgraph = "{\"nodes\":[],\"edges\":[],\"truncated\":false}\n",
          componentreferences = Vector(
            "car" ->
              "{\"schemaVersion\":\"cncf.component-reference-index.v1\",\"kind\":\"car\",\"entries\":{}}\n"
          )
        )

        When("the ordinary public build path reaches its machine-metadata direct-copy route")
        val error = intercept[Throwable] {
          CozyBok.build(_build_config(dir), runner)
        }

        Then("it identifies the malformed CAR and writes neither the direct-copy CAR nor the KnowledgeSource manifest")
        error.getMessage should include("metadata/cncf/component-references/car.json")
        Files.exists(
          dir.resolve("website.d/metadata/cncf/component-references/car.json"),
          LinkOption.NOFOLLOW_LINKS
        ) shouldBe false
        Files.exists(
          dir.resolve("website.d/metadata/cncf/knowledge-source.json"),
          LinkOption.NOFOLLOW_LINKS
        ) shouldBe false
      }
    }
    }
  }

  private def _build_config(dir: Path, strategy: String = "preview"): CozyBok.BuildConfig =
    CozyBok.BuildConfig.create(List(dir.toString, "--strategy", strategy))

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

  private def _write_prepared_metadata(
      dir: Path,
      includeterms: Boolean,
      rdfgraph: String = "{\"nodes\":[],\"edges\":[],\"truncated\":false}\n",
      componentreferences: Vector[(String, String)] = Vector.empty
  ): Unit = {
    val doxsite = dir.resolve("doxsite.d")
    _write(doxsite.resolve("site.ttl"), "@prefix ex: <https://example.com/> .\n")
    _write(doxsite.resolve("site.jsonld"), "{\"@graph\":[]}\n")
    _write(doxsite.resolve("metadata/rdf/graph.json"), rdfgraph)
    if (includeterms)
      _write(doxsite.resolve("metadata/glossary/terms.json"), "{\"terms\":[]}\n")
    componentreferences.foreach { case (kind, content) =>
      _write(doxsite.resolve(s"metadata/cncf/component-references/$kind.json"), content)
    }
  }

  private def _component_ref_graph(): String =
    """{
      |  "nodes": [
      |    {
      |      "id": "car:textus-bok",
      |      "label": "Textus BoK",
      |      "node_type": "component-reference",
      |      "componentRef": {
      |        "kind": "car",
      |        "name": "textus-bok",
      |        "organization": "org.textus",
      |        "version": "0.6.0"
      |      }
      |    },
      |    {
      |      "id": "sar:textus-search",
      |      "label": "Textus Search",
      |      "node_type": "component-reference",
      |      "componentRef": {
      |        "kind": "sar",
      |        "name": "textus-search",
      |        "version": "1.2.0"
      |      }
      |    }
      |  ],
      |  "edges": [],
      |  "truncated": false
      |}
      |""".stripMargin

  private def _component_reference_indexes(): Vector[(String, String)] =
    Vector(
      "car" -> _component_reference_index_json(
        "car",
        Vector(_component_reference_entry_json("car", "textus-bok", Some("org.textus"), Vector("0.6.0")))
      ),
      "sar" -> _component_reference_index_json(
        "sar",
        Vector(_component_reference_entry_json("sar", "textus-search", None, Vector("1.2.0")))
      )
    )

  private def _component_reference_index_json(kind: String, entries: Vector[String]): String =
    s"""{
       |  "schemaVersion": "cncf.component-reference-index.v1",
       |  "kind": "$kind",
       |  "entries": [
       |${entries.mkString(",\n")}
       |  ],
       |  "diagnostics": []
       |}
       |""".stripMargin

  private def _component_reference_entry_json(
      kind: String,
      name: String,
      organization: Option[String],
      versions: Vector[String]
  ): String = {
    val organizationfield = organization.map(x => ",\n      \"organization\": \"" + x + "\"").getOrElse("")
    val versionentries = versions.map(x => s"""{"version": "$x"}""").mkString(", ")
    s"""    {
       |      "name": "$name",
       |      "title": "$name",
       |      "kind": "$kind"$organizationfield,
       |      "aliases": [],
       |      "tags": [],
       |      "terms": [],
       |      "source_path": "repository/$kind/$name",
       |      "versions": [$versionentries]
       |    }""".stripMargin
  }

  private def _graph_component_refs(json: Json): Vector[(String, String, Option[String], Option[String])] =
    json.hcursor.downField("nodes").as[Vector[Json]].fold(throw _, identity).flatMap { node =>
      node.hcursor.downField("componentRef").focus.flatMap(_.asObject).map { _ =>
        val cursor = node.hcursor.downField("componentRef")
        (
          cursor.get[String]("kind").fold(throw _, identity),
          cursor.get[String]("name").fold(throw _, identity),
          cursor.get[Option[String]]("organization").fold(throw _, identity),
          cursor.get[Option[String]]("version").fold(throw _, identity)
        )
      }
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

  private class LocalMetadataRunner(
      rdfgraph: String = _component_ref_graph(),
      componentreferences: Vector[(String, String)] = _component_reference_indexes()
  ) extends CozyBok.Runner {
    private var _commands = Vector.empty[Vector[String]]

    def commands: Vector[Vector[String]] = _commands

    def run(command: Vector[String], cwd: Path): Unit = {
      _commands = _commands :+ command
      if (command.take(2) == Vector("dox", "site"))
        _write_prepared_metadata(
          cwd,
          includeterms = false,
          rdfgraph = rdfgraph,
          componentreferences = componentreferences
        )
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
    if (Files.isSymbolicLink(path))
      Files.deleteIfExists(path)
    else if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      val stream = Files.walk(path)
      try {
        stream.iterator.asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.deleteIfExists)
      } finally {
        stream.close()
      }
    }
}
