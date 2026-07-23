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
 * @version Jul. 23, 2026
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
          _resources_should_be_relative(manifest)
          And("the graph summary itself has a stable producer contract")
          val graph = _parse_json(dir.resolve("website.d/metadata/rdf/graph.json"))
          val graphcursor = graph.hcursor
          graphcursor.get[String]("schemaVersion") shouldBe Right("cozy.rdf-graph-summary.v1")
          graphcursor.get[String]("kind") shouldBe Right("rdf-graph-summary")
          graphcursor.downField("sourceRef").get[String]("kind") shouldBe Right("bok-site")
          graphcursor.downField("sourceRef").get[String]("value") shouldBe Right("knowledgehub")
          graphcursor.downField("sourceRef").get[String]("uri") shouldBe Right("https://example.com/knowledgehub/")
          graphcursor.get[Boolean]("truncated") shouldBe Right(false)
          graphcursor.get[Vector[Json]]("nodes") shouldBe Right(Vector.empty)
          graphcursor.get[Vector[Json]]("edges") shouldBe Right(Vector.empty)
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

      "rejects graph nodes and edges that violate the published v1 contract" in {
        Vector(
          "node" -> (
            """{"nodes":[{"id":"term","label":"Term"}],"edges":[],"truncated":false}""",
            "nodes[0].node_type"
          ),
          "edge" -> (
            """{"nodes":[{"id":"term","label":"Term","node_type":"term"}],"edges":[{"source":"term","predicate":"related"}],"truncated":false}""",
            "edges[0].target"
          )
        ).foreach { case (name, (rdfgraph, violation)) =>
          _with_temp_dir(s"cozy-bok-knowledge-source-invalid-$name") { dir =>
            Given(s"a BoK site whose graph summary has an invalid $name identity")
            _write_site_source(dir, glossaryterm = false)

            When("Cozy builds the public BoK site")
            val error = intercept[Throwable] {
              CozyBok.build(
                _build_config(dir),
                new MetadataRunner(includeterms = false, includerdf = true, rdfgraph = rdfgraph)
              )
            }

            Then("Cozy refuses to stamp the malformed graph as a versioned producer contract")
            error.getMessage should include(violation)
            error.getMessage should include("must be a non-empty string")
          }
        }
      }

      "preserves valid CAR and SAR component references in graph metadata" in {
        _with_temp_dir("cozy-bok-knowledge-source-component-refs") { dir =>
          Given("a BoK graph that declares component-reference nodes explicitly")
          _write_site_source(dir, glossaryterm = false)
          val rdfgraph =
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
          val componentreferences = Vector(
            "car" -> _component_reference_index_json(
              "car",
              Vector(_component_reference_entry_json("car", "textus-bok", Some("org.textus"), Vector("0.6.0")))
            ),
            "sar" -> _component_reference_index_json(
              "sar",
              Vector(_component_reference_entry_json("sar", "textus-search", None, Vector("1.2.0")))
            )
          )

          When("Cozy builds the public BoK site")
          CozyBok.build(
            _build_config(dir),
            new MetadataRunner(
              includeterms = false,
              includerdf = true,
              rdfgraph = rdfgraph,
              componentreferences = componentreferences
            )
          )

          Then("the declared componentRef metadata is preserved after graph versioning")
          val graph = _parse_json(dir.resolve("website.d/metadata/rdf/graph.json"))
          _graph_component_refs(graph) shouldBe Vector(
            ("car", "textus-bok", Some("org.textus"), Some("0.6.0")),
            ("sar", "textus-search", None, Some("1.2.0"))
          )

          And("the component-reference indexes are advertised as KnowledgeSource resources")
          _resources(_parse_json(dir.resolve("website.d/metadata/cncf/knowledge-source.json"))) should contain allOf(
            ("component-reference-index", "metadata/cncf/component-references/car.json", "application/json"),
            ("component-reference-index", "metadata/cncf/component-references/sar.json", "application/json")
          )
        }
      }

      "rejects malformed componentRef node metadata deterministically" in {
        Vector(
          "non-object" -> (
            """{"nodes":[{"id":"n","label":"N","node_type":"component-reference","componentRef":"car:textus-bok"}],"edges":[],"truncated":false}""",
            "nodes[0].componentRef must be a JSON object"
          ),
          "missing-kind" -> (
            """{"nodes":[{"id":"n","label":"N","node_type":"component-reference","componentRef":{"name":"textus-bok"}}],"edges":[],"truncated":false}""",
            "nodes[0].componentRef.kind must be a non-empty string"
          ),
          "missing-name" -> (
            """{"nodes":[{"id":"n","label":"N","node_type":"component-reference","componentRef":{"kind":"car"}}],"edges":[],"truncated":false}""",
            "nodes[0].componentRef.name must be a non-empty string"
          ),
          "unsupported-kind" -> (
            """{"nodes":[{"id":"n","label":"N","node_type":"component-reference","componentRef":{"kind":"war","name":"textus-bok"}}],"edges":[],"truncated":false}""",
            "nodes[0].componentRef.kind must be car or sar"
          ),
          "path-like-kind" -> (
            """{"nodes":[{"id":"n","label":"N","node_type":"component-reference","componentRef":{"kind":"../car","name":"textus-bok"}}],"edges":[],"truncated":false}""",
            "nodes[0].componentRef.kind must be car or sar"
          ),
          "empty-organization" -> (
            """{"nodes":[{"id":"n","label":"N","node_type":"component-reference","componentRef":{"kind":"car","name":"textus-bok","organization":""}}],"edges":[],"truncated":false}""",
            "nodes[0].componentRef.organization must be a non-empty string when present"
          ),
          "empty-version" -> (
            """{"nodes":[{"id":"n","label":"N","node_type":"component-reference","componentRef":{"kind":"car","name":"textus-bok","version":""}}],"edges":[],"truncated":false}""",
            "nodes[0].componentRef.version must be a non-empty string when present"
          ),
          "wrong-node-type" -> (
            """{"nodes":[{"id":"n","label":"N","node_type":"term","componentRef":{"kind":"car","name":"textus-bok"}}],"edges":[],"truncated":false}""",
            "nodes[0].componentRef is allowed only when node_type is component-reference"
          )
        ).foreach { case (name, (rdfgraph, message)) =>
          _with_temp_dir(s"cozy-bok-knowledge-source-invalid-component-ref-$name") { dir =>
            Given("a BoK graph with malformed componentRef metadata")
            _write_site_source(dir, glossaryterm = false)

            When("Cozy builds the public BoK site")
            val error = intercept[Throwable] {
              CozyBok.build(
                _build_config(dir),
                new MetadataRunner(includeterms = false, includerdf = true, rdfgraph = rdfgraph)
              )
            }

            Then("Cozy reports the exact handoff contract violation")
            error.getMessage should include(message)
          }
        }
      }

      "matches component references only through explicit component-reference indexes" in {
        Vector(
          "missing-index" -> (
            _component_ref_graph("car", "textus-bok", None, None),
            Vector.empty[(String, String)],
            "metadata/cncf/component-references/car.json is missing"
          ),
          "missing-entry" -> (
            _component_ref_graph("car", "textus-bok", None, None),
            Vector("car" -> _component_reference_index_json(
              "car",
              Vector(_component_reference_entry_json("car", "other-component", None, Vector("0.1.0")))
            )),
            "does not match any car component-reference index entry"
          ),
          "kind-mismatch" -> (
            _component_ref_graph("car", "textus-bok", None, None),
            Vector("car" -> _component_reference_index_json(
              "car",
              Vector(_component_reference_entry_json("sar", "textus-bok", None, Vector("0.1.0")))
            )),
            "does not match any car component-reference index entry"
          ),
          "version-mismatch" -> (
            _component_ref_graph("car", "textus-bok", None, Some("0.2.0")),
            Vector("car" -> _component_reference_index_json(
              "car",
              Vector(_component_reference_entry_json("car", "textus-bok", None, Vector("0.1.0")))
            )),
            "does not match any car component-reference index entry"
          ),
          "ambiguous" -> (
            _component_ref_graph("car", "textus-bok", None, None),
            Vector("car" -> _component_reference_index_json(
              "car",
              Vector(
                _component_reference_entry_json("car", "textus-bok", None, Vector("0.1.0")),
                _component_reference_entry_json("car", "textus-bok", None, Vector("0.2.0"))
              )
            )),
            "matches multiple car component-reference index entries"
          )
        ).foreach { case (name, (rdfgraph, componentreferences, message)) =>
          _with_temp_dir(s"cozy-bok-knowledge-source-component-ref-match-$name") { dir =>
            Given("a BoK graph with an explicit componentRef and selected generation indexes")
            _write_site_source(dir, glossaryterm = false)

            When("Cozy builds the public BoK site")
            val error = intercept[Throwable] {
              CozyBok.build(
                _build_config(dir),
                new MetadataRunner(
                  includeterms = false,
                  includerdf = true,
                  rdfgraph = rdfgraph,
                  componentreferences = componentreferences
                )
              )
            }

            Then("Cozy refuses unresolved or ambiguous component knowledge handoff")
            error.getMessage should include(message)
          }
        }
      }

      "does not infer component references from ordinary graph nodes" in {
        _with_temp_dir("cozy-bok-knowledge-source-no-component-ref-inference") { dir =>
          Given("a graph node whose label matches a component index entry but lacks componentRef")
          _write_site_source(dir, glossaryterm = false)
          val rdfgraph =
            """{
              |  "nodes": [
              |    {"id":"textus-bok","label":"textus-bok","node_type":"term"}
              |  ],
              |  "edges": [],
              |  "truncated": false
              |}
              |""".stripMargin

          When("Cozy builds the public BoK site")
          CozyBok.build(
            _build_config(dir),
            new MetadataRunner(
              includeterms = false,
              includerdf = true,
              rdfgraph = rdfgraph,
              componentreferences = Vector(
                "car" -> _component_reference_index_json(
                  "car",
                  Vector(_component_reference_entry_json("car", "textus-bok", None, Vector("0.1.0")))
                )
              )
            )
          )

          Then("the graph remains valid without adding componentRef by inference")
          val graph = _parse_json(dir.resolve("website.d/metadata/rdf/graph.json"))
          _graph_component_refs(graph) shouldBe Vector.empty
        }
      }

      "lists CAR and SAR publication metadata without rendered HTML discovery" in {
        _with_temp_dir("cozy-bok-knowledge-source-components") { dir =>
          Given("a BoK site whose generated metadata contains component publication records")
          _write_site_source(dir, glossaryterm = false)

          When("Cozy builds the public BoK site")
          CozyBok.build(
            _build_config(dir),
            new MetadataRunner(includeterms = false, includerdf = false, includecomponents = true)
          )

          Then("the manifest identifies every component metadata layer with relative resources")
          val manifest = _parse_json(dir.resolve("website.d/metadata/cncf/knowledge-source.json"))
          _resources(manifest) shouldBe Vector(
            ("component-catalog-project", "metadata/catalog/projects/textus-example.json", "application/json"),
            ("component-project-metadata", "metadata/projects/textus-example/metadata.json", "application/json"),
            ("component-repository-artifact", "metadata/artifacts/repository/textus-example.json", "application/json"),
            ("component-release-history", "metadata/releases/textus-example.json", "application/json"),
            ("component-catalog-project", "metadata/catalog/projects/textus-runtime.json", "application/json"),
            ("component-project-metadata", "metadata/projects/textus-runtime/metadata.json", "application/json"),
            ("component-repository-artifact", "metadata/artifacts/repository/textus-runtime.json", "application/json"),
            ("component-release-history", "metadata/releases/textus-runtime.json", "application/json")
          )
          _resources_should_be_relative(manifest)

          And("every advertised document retains the live Cozy publication schema and identity")
          Vector("textus-example" -> "car", "textus-runtime" -> "sar").foreach { case (name, _) =>
            Vector(
              s"metadata/catalog/projects/$name.json" -> "catalog-project",
              s"metadata/projects/$name/metadata.json" -> "project-metadata",
              s"metadata/artifacts/repository/$name.json" -> "repository-artifact",
              s"metadata/releases/$name.json" -> "release-history"
            ).foreach { case (href, documenttype) =>
              val path = dir.resolve("website.d").resolve(href)
              path should be_regular_file
              val document = _parse_json(path)
              document.hcursor.get[String]("schema") shouldBe Right("cozy.publish-project.v1")
              document.hcursor.get[String]("type") shouldBe Right(documenttype)
              document.hcursor.downField("project").get[String]("name") shouldBe Right(name)
            }
          }
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

  private class MetadataRunner(
      includeterms: Boolean,
      includerdf: Boolean,
      includecomponents: Boolean = false,
      rdfgraph: String = "{\"nodes\":[],\"edges\":[],\"truncated\":false}\n",
      componentreferences: Vector[(String, String)] = Vector.empty
  ) extends CozyBok.Runner {
    def run(command: Vector[String], cwd: Path): Unit =
      if (command.take(2) == Vector("dox", "site")) {
        if (includeterms)
          _write(cwd.resolve("doxsite.d/metadata/glossary/terms.json"), "{\"terms\":[]}\n")
        if (includerdf) {
          _write(cwd.resolve("doxsite.d/metadata/rdf/graph.json"), rdfgraph)
          _write(cwd.resolve("doxsite.d/site.jsonld"), "{\"@graph\":[]}\n")
          _write(cwd.resolve("doxsite.d/site.ttl"), "@prefix ex: <https://example.com/> .\n")
        }
        componentreferences.foreach { case (kind, content) =>
          _write(cwd.resolve(s"doxsite.d/metadata/cncf/component-references/$kind.json"), content)
        }
        if (includecomponents) {
          Vector("textus-example" -> "car", "textus-runtime" -> "sar").foreach { case (name, kind) =>
            _write(cwd.resolve(s"doxsite.d/metadata/catalog/projects/$name.json"), _component_catalog_json(name, kind))
            _write(cwd.resolve(s"doxsite.d/metadata/projects/$name/metadata.json"), _component_project_json(name, kind))
            _write(cwd.resolve(s"doxsite.d/metadata/artifacts/repository/$name.json"), _component_artifact_json(name, kind))
            _write(cwd.resolve(s"doxsite.d/metadata/releases/$name.json"), _component_release_json(name, kind))
          }
          _write(
            cwd.resolve("doxsite.d/metadata/projects/not-a-component/metadata.json"),
            """{"schema":"cozy.publish-project.v1","type":"project-metadata","project":{"name":"not-a-component","kind":"application"}}""" + "\n"
          )
        }
      }
  }

  private def _component_catalog_json(name: String, kind: String): String =
    s"""{
       |  "schema": "cozy.publish-project.v1",
       |  "type": "catalog-project",
       |  "project": {
       |    "name": "$name",
       |    "title": "$name",
       |    "kind": "$kind",
       |    "metadata": "metadata/projects/$name/metadata"
       |  }
       |}
       |""".stripMargin

  private def _component_project_json(name: String, kind: String): String =
    s"""{
       |  "schema": "cozy.publish-project.v1",
       |  "type": "project-metadata",
       |  "project": {
       |    "name": "$name",
       |    "title": "$name",
       |    "kind": "$kind",
       |    "organization": "org.textus",
       |    "version": "0.2.0-SNAPSHOT",
       |    "scalaVersion": "3.3.8",
       |    "sbtVersion": "1.9.7",
       |    "buildSettings": {
       |      "cozyPlugin": true,
       |      "cozyPackaging": "$kind",
       |      "cncfVersion": "0.5.1-SNAPSHOT",
       |      "cncfDependency": true,
       |      "sbtCozyPlugin": true
       |    }
       |  },
       |  "publication": {"path": "components/$name"}
       |}
       |""".stripMargin

  private def _component_artifact_json(name: String, kind: String): String =
    s"""{
       |  "schema": "cozy.publish-project.v1",
       |  "type": "repository-artifact",
       |  "project": {"name": "$name", "title": "$name", "kind": "$kind"},
       |  "artifact": {
       |    "layer": "repository",
       |    "status": "available",
       |    "kinds": [{"type": "$kind", "versions": ["0.2.0"], "latestRelease": "0.2.0"}],
       |    "files": [{"type": "$kind", "version": "0.2.0", "publicPath": "repository/$kind/$name/0.2.0/$name-0.2.0.$kind"}]
       |  }
       |}
       |""".stripMargin

  private def _component_release_json(name: String, kind: String): String =
    s"""{
       |  "schema": "cozy.publish-project.v1",
       |  "type": "release-history",
       |  "project": {"name": "$name", "title": "$name", "kind": "$kind"},
       |  "release": {
       |    "name": "$name",
       |    "latest": "0.2.0",
       |    "versions": [{"version": "0.2.0", "artifacts": []}]
       |  }
       |}
       |""".stripMargin

  private def _component_ref_graph(
      kind: String,
      name: String,
      organization: Option[String],
      version: Option[String]
  ): String = {
    val organizationfield = organization.map(x => """, "organization": """" + x + "\"").getOrElse("")
    val versionfield = version.map(x => """, "version": """" + x + "\"").getOrElse("")
    s"""{
       |  "nodes": [
       |    {
       |      "id": "$kind:$name",
       |      "label": "$name",
       |      "node_type": "component-reference",
       |      "componentRef": {"kind": "$kind", "name": "$name"$organizationfield$versionfield}
       |    }
       |  ],
       |  "edges": [],
       |  "truncated": false
       |}
       |""".stripMargin
  }

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

  private def _resources_should_be_relative(json: Json): Unit =
    _resources(json).map(_._2).foreach { href =>
      href should not startWith "/"
      href should not include "://"
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
