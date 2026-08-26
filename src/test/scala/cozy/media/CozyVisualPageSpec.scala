package cozy.media

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path}
import java.security.MessageDigest
import scala.collection.JavaConverters._
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import cozy.CozySpecVocabulary

/*
 * @since   Aug. 26, 2026
 * @version Aug. 26, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyVisualPageSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  "Cozy Visual Page core" should {
    "converge JSON, YAML, and restricted Markdown to one typed semantic graph" in {
      _with_work("visual-page-representations") { root =>
        Given("one exact closed catalog, a declared direct source, and one sequence Visual Page")
        val catalog = _write_catalog(root)
        _write(root.resolve("sources/research.txt"), "research")
        val page = _sequence_page()
        val json = _write(root.resolve("page.json"), CozyVisualPage.canonicalJson(page))
        val yaml = _write(root.resolve("page.yaml"), CozyVisualPage.canonicalYaml(page))
        val markdown = _write(root.resolve("page.md"), CozyVisualPage.canonicalMarkdown(page))
        val set = _write(root.resolve("pages.json"), CozyVisualPage.canonicalJson(CozyVisualPage.PageSet("set-1", Vector(page))))
        val literalpage = page.copy(
          knowledge = "A&B *",
          logical = page.logical.copy(nodes = page.logical.nodes.updated(0, page.logical.nodes.head.copy(label = "A&B *")))
        )
        val literaljson = _write(root.resolve("literal.json"), CozyVisualPage.canonicalJson(literalpage))
        val literalyaml = _write(root.resolve("literal.yaml"), CozyVisualPage.canonicalYaml(literalpage))
        val nestedliteralyaml = _write(root.resolve("literal-nested.yaml"),
          """schema: "cozy.visual-page.v1"
            |version: 1
            |id: "sequence-page"
            |knowledge: "A&B *"
            |language: "en"
            |catalog:
            |  id: "core"
            |  revision: 1
            |logical:
            |  pattern: "sequence"
            |  nodes:
            |    - id: "discover"
            |      role: "step"
            |      label: "A&B *"
            |      sourceRefs: ["research"]
            |    - id: "apply"
            |      role: "step"
            |      label: "Apply"
            |      sourceRefs: ["research"]
            |  relations:
            |    - id: "transition"
            |      type: "next"
            |      from: "discover"
            |      to: "apply"
            |      sourceRefs: ["research"]
            |visual:
            |  pattern: "flow-horizontal"
            |  parameters: {}
            |assets: []
            |sources:
            |  - id: "research"
            |    path: "sources/research.txt"
            |""".stripMargin
        )

        When("each admitted representation, including quoted compact JSON scalar content, is loaded against that separate catalog")
        val resolved = Vector(json, yaml, markdown).map(path => CozyVisualPage.load(path, catalog))
        val resolvedset = CozyVisualPage.load(set, catalog)
        val literalresolved = Vector(literaljson, literalyaml, nestedliteralyaml).map(path => CozyVisualPage.load(path, catalog))

        Then("they retain one typed graph and representation-independent canonical identities without losing literal ampersand or asterisk content")
        resolved.map(_.canonicalJson).distinct shouldBe Vector(CozyVisualPage.canonicalJson(page))
        resolved.map(_.logicalIdentities).distinct should have size 1
        resolved.map(_.visualPageIdentities).distinct should have size 1
        resolved.head.document.pages.head.logical.nodes.map(_.id) shouldBe Vector("discover", "apply")
        resolvedset.document shouldBe CozyVisualPage.PageSet("set-1", Vector(page))
        resolvedset.documentIdentity should startWith("sha256:")
        literalresolved.map(_.canonicalJson).distinct shouldBe Vector(CozyVisualPage.canonicalJson(literalpage))
        literalresolved.map(_.documentIdentity).distinct should have size 1
      }
    }

    "prove canonical JSON and identities converge through at least thirty valid representation and key-order variations" in {
      _with_work("visual-page-property") { root =>
        Given("one valid page represented by generated JSON and YAML root-field orders")
        val catalog = _write_catalog(root)
        _write(root.resolve("sources/research.txt"), "research")
        val page = _sequence_page()
        val values = _root_values(CozyVisualPage.canonicalYaml(page))
        val keys = Vector("schema", "version", "id", "knowledge", "language", "catalog", "logical", "visual", "assets", "sources")
        val orders = Gen.chooseNum(0, 29).map(index => _key_order(keys, index))
        val formats = Gen.oneOf("json", "yaml")

        When("ScalaCheck loads generated admissible representation/key-order combinations")
        val check = Test.check(Test.Parameters.default.withMinSuccessfulTests(30), Prop.forAll(orders, formats) { (order, format) =>
          val text = if (format == "json") _ordered_json(order, values) else _ordered_yaml(order, values)
          val input = _write(root.resolve("page." + (if (format == "json") "json" else "yaml")), text)
          val loaded = CozyVisualPage.load(input, catalog)
          loaded.canonicalJson == CozyVisualPage.canonicalJson(page) &&
            loaded.logicalIdentities.head._2 == CozyVisualPage.logicalIdentity(page, _catalog()) &&
            loaded.visualPageIdentities.head._2 == CozyVisualPage.visualPageIdentity(page, _catalog())
        })

        Then("all successful generated cases converge rather than retaining source key order")
        check.passed shouldBe true
        check.succeeded should be >= 30
      }
    }

    "retain Relation meaning while separating compatible logical and visual identities" in {
      _with_work("visual-page-identities") { root =>
        Given("closed core pages that use next, causes, and depends-on Relations")
        val catalog = _write_catalog(root)
        _write(root.resolve("sources/research.txt"), "research")
        val sequence = _sequence_page("sequence-page", "flow-horizontal")
        val causal = _causal_page()
        val dependency = _dependency_page()
        val horizontal = _write(root.resolve("sequence-horizontal.json"), CozyVisualPage.canonicalJson(sequence))
        val vertical = _write(root.resolve("sequence-vertical.json"), CozyVisualPage.canonicalJson(sequence.copy(visual = CozyVisualPage.Visual("flow-vertical", sequence.visual.parameters))))
        val causalfile = _write(root.resolve("causal.json"), CozyVisualPage.canonicalJson(causal))
        val dependencyfile = _write(root.resolve("dependency.json"), CozyVisualPage.canonicalJson(dependency))

        When("the semantic and projection identities are resolved")
        val horizontalidentity = CozyVisualPage.load(horizontal, catalog)
        val verticalidentity = CozyVisualPage.load(vertical, catalog)
        val relationidentities = Vector(causalfile, dependencyfile).map(path => CozyVisualPage.load(path, catalog)) :+ horizontalidentity

        Then("Relation type remains identity-bearing even where arrows share a compatible projection")
        relationidentities.map(_.logicalIdentities.head._2).distinct should have size 3
        horizontalidentity.logicalIdentities.head._2 shouldBe verticalidentity.logicalIdentities.head._2
        horizontalidentity.visualPageIdentities.head._2 should not be verticalidentity.visualPageIdentities.head._2
      }
    }

    "accept every closed parameter type and reject catalog, graph, provenance, and lossy-input failures with structured diagnostics" in {
      _with_work("visual-page-rejections") { root =>
        Given("a core catalog and direct sources/assets for valid and adversarial values")
        val catalog = _write_catalog(root)
        _write(root.resolve("sources/research.txt"), "research")
        _write(root.resolve("assets/diagram.txt"), "diagram")
        val asset = CozyVisualPage.Asset("diagram", "assets/diagram.txt", "text/plain", _sha256(root.resolve("assets/diagram.txt")))
        val sequence = _sequence_page(assets = Vector(asset), parameters = Vector(
          CozyVisualPage.VisualParameter("emphasisNode", CozyVisualPage.StringParameter("discover")),
          CozyVisualPage.VisualParameter("showRelationLabels", CozyVisualPage.BooleanParameter(true))
        ))
        val mapping = _mapping_page()
        val validsequence = _write(root.resolve("valid-sequence.json"), CozyVisualPage.canonicalJson(sequence))
        val validmapping = _write(root.resolve("valid-mapping.json"), CozyVisualPage.canonicalJson(mapping))

        When("the closed parameter forms are loaded")
        val accepted = Vector(validsequence, validmapping).map(path => CozyVisualPage.load(path, catalog))

        Then("node-ref, boolean, and string parameters resolve through their exact catalog declarations")
        accepted.map(_.document.pages.head.visual.parameters.map(_.name).sorted) shouldBe Vector(
          Vector("emphasisNode", "showRelationLabels"),
          Vector("showRelationLabels", "sourceColumnTitle", "targetColumnTitle")
        )

        Given("schema, pattern, cardinality, parameter, path, asset, duplicate, legacy, and Markdown-loss variants")
        val duplicatefield = _write(root.resolve("duplicate.json"), CozyVisualPage.canonicalJson(sequence).replace("\"schema\":\"cozy.visual-page.v1\"", "\"schema\":\"cozy.visual-page.v1\",\"schema\":\"cozy.visual-page.v1\""))
        val unknownfield = _write(root.resolve("unknown.json"), CozyVisualPage.canonicalJson(sequence).dropRight(1) + ",\"unknown\":true}")
        val wrongtype = _write(root.resolve("wrong-type.json"), CozyVisualPage.canonicalJson(sequence).replace("\"version\":1", "\"version\":\"1\""))
        val unknownpattern = _write(root.resolve("unknown-pattern.json"), CozyVisualPage.canonicalJson(sequence.copy(logical = sequence.logical.copy(pattern = "unknown"))))
        val unknownrelation = _write(root.resolve("unknown-relation.json"), CozyVisualPage.canonicalJson(sequence.copy(logical = sequence.logical.copy(relations = Vector(sequence.logical.relations.head.copy(relationType = "unknown"))))))
        val incompatible = _write(root.resolve("incompatible.json"), CozyVisualPage.canonicalJson(sequence.copy(visual = CozyVisualPage.Visual("mapping-columns", Vector(
          CozyVisualPage.VisualParameter("sourceColumnTitle", CozyVisualPage.StringParameter("Source")),
          CozyVisualPage.VisualParameter("targetColumnTitle", CozyVisualPage.StringParameter("Target"))
        )))))
        val topology = _write(root.resolve("topology.json"), CozyVisualPage.canonicalJson(sequence.copy(logical = sequence.logical.copy(relations = Vector.empty))))
        val duplicatepage = _write(root.resolve("duplicate-pages.json"), CozyVisualPage.canonicalJson(CozyVisualPage.PageSet("set-duplicate", Vector(sequence, sequence))))
        val duplicatenode = _write(root.resolve("duplicate-node.json"), CozyVisualPage.canonicalJson(sequence.copy(logical = sequence.logical.copy(nodes = Vector(sequence.logical.nodes.head, sequence.logical.nodes.head.copy(id = sequence.logical.nodes.head.id))))))
        val duplicaterelation = _write(root.resolve("duplicate-relation.json"), CozyVisualPage.canonicalJson(sequence.copy(logical = sequence.logical.copy(relations = Vector(sequence.logical.relations.head, sequence.logical.relations.head.copy(id = sequence.logical.relations.head.id))))))
        val duplicatesource = _write(root.resolve("duplicate-source.json"), CozyVisualPage.canonicalJson(sequence.copy(sources = Vector(sequence.sources.head, sequence.sources.head))))
        val duplicateasset = _write(root.resolve("duplicate-asset.json"), CozyVisualPage.canonicalJson(sequence.copy(assets = Vector(asset, asset))))
        val unsafe = _write(root.resolve("unsafe.json"), CozyVisualPage.canonicalJson(sequence.copy(sources = Vector(CozyVisualPage.SourceBinding("research", "../research.txt")))))
        val missing = _write(root.resolve("missing.json"), CozyVisualPage.canonicalJson(sequence.copy(sources = Vector(CozyVisualPage.SourceBinding("research", "sources/missing.txt")))))
        val missingasset = _write(root.resolve("missing-asset.json"), CozyVisualPage.canonicalJson(sequence.copy(assets = Vector(asset.copy(path = "assets/missing.txt")))))
        val digestmismatch = _write(root.resolve("digest.json"), CozyVisualPage.canonicalJson(sequence.copy(assets = Vector(asset.copy(sha256 = "0" * 64)))))
        val legacy = _write(root.resolve("legacy.json"), "{\"schema\":\"cozy.slide-ir.v1\"}")
        val lossy = _write(root.resolve("lossy.md"), CozyVisualPage.canonicalMarkdown(sequence).replace("## Assets\nassets", "assets\n## Assets"))
        val yamlanchor = _write(root.resolve("anchor.yaml"), CozyVisualPage.canonicalYaml(sequence).replace("knowledge: \"knowledge-1\"", "knowledge: &knowledge \"knowledge\""))
        val yamlalias = _write(root.resolve("alias.yaml"), CozyVisualPage.canonicalYaml(sequence).replace("knowledge: \"knowledge-1\"", "knowledge: *knowledge"))
        val yamlembeddedquoteanchor = _write(root.resolve("embedded-quote-anchor.yaml"), CozyVisualPage.canonicalYaml(sequence).replace("knowledge: \"knowledge-1\"", "knowledge: foo\" &knowledge"))
        val yamlembeddedquotealias = _write(root.resolve("embedded-quote-alias.yaml"), CozyVisualPage.canonicalYaml(sequence).replace("knowledge: \"knowledge-1\"", "knowledge: foo\" *knowledge"))
        val yamlmerge = _write(root.resolve("merge.yaml"), "<<: {}\n" + CozyVisualPage.canonicalYaml(sequence))
        val semanticcomment = _write(root.resolve("semantic-comment.yaml"), "# schema: cozy.slide-ir.v1\n" + CozyVisualPage.canonicalYaml(sequence))
        val malformedutf8 = root.resolve("malformed-utf8.json")
        Files.write(malformedutf8, Array[Byte]('{'.toByte, 0xc3.toByte, 0x28.toByte, '}'.toByte))
        val mappingmissing = _write(root.resolve("mapping-missing.json"), CozyVisualPage.canonicalJson(mapping.copy(visual = CozyVisualPage.Visual("mapping-columns", Vector.empty))))
        val mappingunknown = _write(root.resolve("mapping-unknown.json"), CozyVisualPage.canonicalJson(mapping.copy(visual = mapping.visual.copy(parameters = mapping.visual.parameters :+ CozyVisualPage.VisualParameter("other", CozyVisualPage.StringParameter("x"))))))
        val booltype = _write(root.resolve("bool-type.json"), CozyVisualPage.canonicalJson(sequence.copy(visual = sequence.visual.copy(parameters = Vector(
          CozyVisualPage.VisualParameter("showRelationLabels", CozyVisualPage.StringParameter("true"))
        )))))
        val noderef = _write(root.resolve("node-ref.json"), CozyVisualPage.canonicalJson(sequence.copy(visual = sequence.visual.copy(parameters = Vector(
          CozyVisualPage.VisualParameter("emphasisNode", CozyVisualPage.StringParameter("absent"))
        )))))
        val symlinktarget = _write(root.resolve("outside.txt"), "outside")
        val symlink = root.resolve("sources/symlink.txt")
        Files.createSymbolicLink(symlink, symlinktarget)
        val symlinkpage = _write(root.resolve("symlink.json"), CozyVisualPage.canonicalJson(sequence.copy(sources = Vector(CozyVisualPage.SourceBinding("research", "sources/symlink.txt")))))

        When("each invalid source is loaded")
        val failures = Map(
          "duplicate-field" -> duplicatefield, "unknown-field" -> unknownfield, "wrong-type" -> wrongtype, "unknown-pattern" -> unknownpattern,
          "unknown-relation" -> unknownrelation, "incompatible" -> incompatible, "topology" -> topology, "duplicate-page" -> duplicatepage,
          "duplicate-node" -> duplicatenode, "duplicate-relation" -> duplicaterelation, "duplicate-source" -> duplicatesource,
          "duplicate-asset" -> duplicateasset, "unsafe" -> unsafe, "missing" -> missing, "missing-asset" -> missingasset,
          "digest-mismatch" -> digestmismatch, "legacy" -> legacy, "lossy" -> lossy, "yaml-anchor" -> yamlanchor,
          "yaml-alias" -> yamlalias, "yaml-embedded-quote-anchor" -> yamlembeddedquoteanchor,
          "yaml-embedded-quote-alias" -> yamlembeddedquotealias, "yaml-merge" -> yamlmerge, "semantic-comment" -> semanticcomment,
          "malformed-utf8" -> malformedutf8, "mapping-missing" -> mappingmissing, "mapping-unknown" -> mappingunknown,
          "bool-type" -> booltype, "node-ref" -> noderef, "symlink" -> symlinkpage
        ).map { case (name, path) => name -> _failure(CozyVisualPage.load(path, catalog)) }

        Then("every rejection carries a stable code, field path/source location, and concise reason")
        failures.values.map(_.getMessage).forall(message => message.contains("VISUAL_PAGE_") && message.contains("path=") && message.contains("reason=")) shouldBe true
        Vector("yaml-anchor", "yaml-alias", "yaml-embedded-quote-anchor", "yaml-embedded-quote-alias", "yaml-merge").map(failures).map(_.getMessage).forall(_.contains("VISUAL_PAGE_YAML_ALIAS")) shouldBe true
        failures("semantic-comment").getMessage should include_text("VISUAL_PAGE_YAML_COMMENT")
        failures("malformed-utf8").getMessage should include_text("VISUAL_PAGE_READ path=input")
      }
    }

    "provide deterministic validate, inspect, and fail-safe conversion commands without changing the legacy slide route" in {
      _with_work("visual-page-command") { root =>
        Given("one valid direct page and closed catalog")
        val catalog = _write_catalog(root)
        _write(root.resolve("sources/research.txt"), "research")
        val input = _write(root.resolve("page.json"), CozyVisualPage.canonicalJson(_sequence_page()))
        val output = root.resolve("page.md")
        val invalidoutput = root.resolve("invalid.md")
        val unsupportedoutput = root.resolve("invalid.txt")

        When("validate, inspect, and convert use the direct Visual Page command surface")
        val validate = CozyVisualPage.execute(List("validate", input.toString, "--catalog", catalog.toString))
        val inspect = CozyVisualPage.execute(List("inspect", input.toString, "--catalog", catalog.toString))
        val convert = CozyVisualPage.execute(List("convert", input.toString, "--catalog", catalog.toString, "--save", output.toString))
        val invalid = _write(root.resolve("invalid.json"), "{\"schema\":\"cozy.slide-ir.v1\"}")
        val parsefailure = _failure(CozyVisualPage.execute(List("convert", invalid.toString, "--catalog", catalog.toString, "--save", invalidoutput.toString)))
        val formatfailure = _failure(CozyVisualPage.execute(List("convert", input.toString, "--catalog", catalog.toString, "--save", unsupportedoutput.toString)))

        Then("summary/detail output is deterministic, conversion is canonical, failures leave no outputs, and slide behavior is not selected")
        validate.split("\n").forall(line => line.startsWith("schema:") || line.startsWith("version:") || line.startsWith("count:") || line.contains("Identity." ) || line.startsWith("catalogIdentity:") || line.startsWith("logicalCatalogIdentity:") || line.startsWith("documentIdentity:")) shouldBe true
        inspect should include_text("relation: transition type=next from=discover to=apply")
        convert should include_text("format: md")
        Files.readString(output, StandardCharsets.UTF_8) shouldBe CozyVisualPage.canonicalMarkdown(_sequence_page())
        parsefailure.getMessage should include_text("VISUAL_PAGE_SCHEMA_UNSUPPORTED")
        formatfailure.getMessage should include_text("VISUAL_PAGE_OUTPUT_FORMAT")
        Files.exists(invalidoutput, LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(unsupportedoutput, LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "dispatch Visual Page validation and expose its grammar through the top-level Cozy CLI" in {
      _with_work("visual-page-cli") { root =>
        Given("one valid Visual Page, its closed catalog, and declared direct source")
        val catalog = _write_catalog(root)
        _write(root.resolve("sources/research.txt"), "research")
        val input = _write(root.resolve("page.json"), CozyVisualPage.canonicalJson(_sequence_page()))

        When("the top-level Cozy CLI validates the page and displays command help")
        val validate = _capture_stdout {
          cozy.Cozy.main(Array("media", "visual-page", "validate", input.toString, "--catalog", catalog.toString))
        }
        val help = _capture_stdout {
          cozy.Cozy.main(Array("--help"))
        }

        Then("Visual Page validation reaches its stable summary and both command grammars are discoverable")
        validate.split("\n").filter(_.nonEmpty).map(_.takeWhile(_ != ':')) shouldBe Vector(
          "schema",
          "version",
          "count",
          "catalogIdentity",
          "logicalCatalogIdentity",
          "logicalIdentity.sequence-page",
          "visualPageIdentity.sequence-page",
          "documentIdentity"
        )
        validate should include_text("schema: cozy.visual-page.v1")
        validate should include_text("version: 1")
        validate should include_text("count: 1")
        help.split("\n").map(_.trim).toVector should contain("media visual-page validate|inspect <input> --catalog <catalog>")
        help.split("\n").map(_.trim).toVector should contain("media visual-page convert <input> --catalog <catalog> --save <output.json|output.yaml|output.yml|output.md>")
      }
    }
  }

  private def _sequence_page(
    id: String = "sequence-page",
    pattern: String = "flow-horizontal",
    assets: Vector[CozyVisualPage.Asset] = Vector.empty,
    parameters: Vector[CozyVisualPage.VisualParameter] = Vector.empty
  ): CozyVisualPage.Page =
    CozyVisualPage.Page(
      id,
      "knowledge-1",
      "en",
      CozyVisualPage.CatalogReference("core", 1),
      CozyVisualPage.Logical(
        "sequence",
        Vector(
          CozyVisualPage.Node("discover", "step", "Discover", Vector("research")),
          CozyVisualPage.Node("apply", "step", "Apply", Vector("research"))
        ),
        Vector(CozyVisualPage.Relation("transition", "next", "discover", "apply", Vector("research")))
      ),
      CozyVisualPage.Visual(pattern, parameters),
      assets,
      Vector(CozyVisualPage.SourceBinding("research", "sources/research.txt"))
    )

  private def _causal_page(): CozyVisualPage.Page =
    CozyVisualPage.Page(
      "causal-page", "knowledge-1", "en", CozyVisualPage.CatalogReference("core", 1),
      CozyVisualPage.Logical(
        "causal-chain",
        Vector(
          CozyVisualPage.Node("cause", "cause", "Cause", Vector("research")),
          CozyVisualPage.Node("effect", "effect", "Effect", Vector("research"))
        ),
        Vector(
          CozyVisualPage.Relation("cause-edge", "causes", "cause", "effect", Vector("research")),
          CozyVisualPage.Relation("enable-edge", "enables", "cause", "effect", Vector("research"))
        )
      ),
      CozyVisualPage.Visual("flow-horizontal", Vector.empty), Vector.empty,
      Vector(CozyVisualPage.SourceBinding("research", "sources/research.txt"))
    )

  private def _dependency_page(): CozyVisualPage.Page =
    CozyVisualPage.Page(
      "dependency-page", "knowledge-1", "en", CozyVisualPage.CatalogReference("core", 1),
      CozyVisualPage.Logical(
        "dependency-map",
        Vector(
          CozyVisualPage.Node("dependency", "dependency", "Dependency", Vector("research")),
          CozyVisualPage.Node("dependent", "dependent", "Dependent", Vector("research"))
        ),
        Vector(CozyVisualPage.Relation("dependency-edge", "depends-on", "dependent", "dependency", Vector("research")))
      ),
      CozyVisualPage.Visual("flow-vertical", Vector.empty), Vector.empty,
      Vector(CozyVisualPage.SourceBinding("research", "sources/research.txt"))
    )

  private def _mapping_page(): CozyVisualPage.Page =
    CozyVisualPage.Page(
      "mapping-page", "knowledge-1", "en", CozyVisualPage.CatalogReference("core", 1),
      CozyVisualPage.Logical(
        "mapping",
        Vector(
          CozyVisualPage.Node("source", "source", "Source", Vector("research")),
          CozyVisualPage.Node("target", "target", "Target", Vector("research"))
        ),
        Vector(CozyVisualPage.Relation("mapping-edge", "maps-to", "source", "target", Vector("research")))
      ),
      CozyVisualPage.Visual("mapping-columns", Vector(
        CozyVisualPage.VisualParameter("showRelationLabels", CozyVisualPage.BooleanParameter(true)),
        CozyVisualPage.VisualParameter("sourceColumnTitle", CozyVisualPage.StringParameter("Source")),
        CozyVisualPage.VisualParameter("targetColumnTitle", CozyVisualPage.StringParameter("Target"))
      )), Vector.empty,
      Vector(CozyVisualPage.SourceBinding("research", "sources/research.txt"))
    )

  private def _catalog(): CozyVisualPage.Catalog = CozyVisualPage.Catalog(
    "core", 1,
    Vector(
      CozyVisualPage.RelationDefinition("next", "from-to"),
      CozyVisualPage.RelationDefinition("causes", "from-to"),
      CozyVisualPage.RelationDefinition("depends-on", "from-to"),
      CozyVisualPage.RelationDefinition("enables", "from-to"),
      CozyVisualPage.RelationDefinition("maps-to", "from-to")
    ),
    Vector(
      CozyVisualPage.LogicalPattern("sequence", Vector(CozyVisualPage.NodeRole("step", 2, 8)), Vector(CozyVisualPage.RelationRule("next", Vector("step"), Vector("step"), 1, 7, "linear"))),
      CozyVisualPage.LogicalPattern("causal-chain", Vector(CozyVisualPage.NodeRole("cause", 1, 7), CozyVisualPage.NodeRole("effect", 1, 7)), Vector(
        CozyVisualPage.RelationRule("causes", Vector("cause"), Vector("effect"), 1, 16, "acyclic"),
        CozyVisualPage.RelationRule("enables", Vector("cause"), Vector("effect"), 1, 16, "acyclic")
      )),
      CozyVisualPage.LogicalPattern("dependency-map", Vector(CozyVisualPage.NodeRole("dependency", 1, 7), CozyVisualPage.NodeRole("dependent", 1, 7)), Vector(CozyVisualPage.RelationRule("depends-on", Vector("dependent"), Vector("dependency"), 1, 16, "acyclic"))),
      CozyVisualPage.LogicalPattern("mapping", Vector(CozyVisualPage.NodeRole("source", 1, 7), CozyVisualPage.NodeRole("target", 1, 7)), Vector(CozyVisualPage.RelationRule("maps-to", Vector("source"), Vector("target"), 1, 16, "bipartite")))
    ),
    Vector(
      CozyVisualPage.VisualPattern("flow-horizontal", Vector("causal-chain", "sequence"), Vector(CozyVisualPage.ParameterDefinition("emphasisNode", "node-ref", false), CozyVisualPage.ParameterDefinition("showRelationLabels", "boolean", false))),
      CozyVisualPage.VisualPattern("flow-vertical", Vector("causal-chain", "dependency-map", "sequence"), Vector(CozyVisualPage.ParameterDefinition("emphasisNode", "node-ref", false), CozyVisualPage.ParameterDefinition("showRelationLabels", "boolean", false))),
      CozyVisualPage.VisualPattern("mapping-columns", Vector("mapping"), Vector(CozyVisualPage.ParameterDefinition("showRelationLabels", "boolean", false), CozyVisualPage.ParameterDefinition("sourceColumnTitle", "string", true), CozyVisualPage.ParameterDefinition("targetColumnTitle", "string", true)))
    )
  )

  private def _write_catalog(root: Path): Path = _write(root.resolve("catalog.json"), CozyVisualPage.canonicalCatalogJson(_catalog()))

  private def _root_values(yaml: String): Map[String, String] = yaml.split("\n").toVector.filter(_.nonEmpty).map { line =>
    val index = line.indexOf(": ")
    line.take(index) -> line.drop(index + 2)
  }.toMap

  private def _key_order(keys: Vector[String], index: Int): Vector[String] = {
    val base = (index / keys.size) match {
      case 0 => keys
      case 1 => keys.reverse
      case _ => keys.updated(0, keys(1)).updated(1, keys(0))
    }
    val offset = index % keys.size
    base.drop(offset) ++ base.take(offset)
  }

  private def _ordered_json(order: Vector[String], values: Map[String, String]): String = order.map(key => "\"" + key + "\":" + values(key)).mkString("{", ",", "}")
  private def _ordered_yaml(order: Vector[String], values: Map[String, String]): String = order.map(key => key + ": " + values(key)).mkString("\n") + "\n"

  private def _failure[A](body: => A): RuntimeException = intercept[RuntimeException](body)

  private def _capture_stdout(body: => Unit): String = {
    val stream = new ByteArrayOutputStream
    Console.withOut(stream)(body)
    stream.toString(StandardCharsets.UTF_8.name)
  }

  private def _write(path: Path, value: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, value.getBytes(StandardCharsets.UTF_8))
    path
  }

  private def _sha256(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).map(byte => f"${byte & 0xff}%02x").mkString

  private def _with_work[A](name: String)(body: Path => A): A = {
    val root = Files.createTempDirectory("cozy-visual-page-" + name + "-")
    try body(root)
    finally _delete_tree(root)
  }

  private def _delete_tree(path: Path): Unit =
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)) Files.list(path).iterator().asScala.foreach(_delete_tree)
      Files.deleteIfExists(path)
    }
}
