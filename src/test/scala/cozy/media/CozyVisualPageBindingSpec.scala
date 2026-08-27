package cozy.media

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path}

import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.JavaConverters._

import cozy.CozySpecVocabulary

/*
 * @since   Aug. 27, 2026
 * @version Aug. 27, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyVisualPageBindingSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  "Cozy Visual Page business binding" should {
    "normalize complete pattern bindings and converge equivalent JSON ordering to one identity" in {
      _with_work("binding-canonical") { root =>
        Given("one resolved Visual Page document and a complete business binding for its catalog")
        val validated = _validated()
        val first = _write(root.resolve("first.json"), _binding_json(Vector("flow-horizontal", "flow-vertical", "mapping-columns")))
        val second = _write(root.resolve("second.json"), _binding_json(Vector("mapping-columns", "flow-horizontal", "flow-vertical"), true, _root_fields.reverse))

        When("the strict direct loader reads both key and pattern-entry orderings")
        val firstloaded = CozyVisualPageBinding.load(first, validated)
        val secondloaded = CozyVisualPageBinding.load(second, validated)

        Then("canonical JSON and the SHA-256 binding identity converge while pattern order is canonical")
        firstloaded.canonicalJson shouldBe secondloaded.canonicalJson
        firstloaded.bindingIdentity shouldBe secondloaded.bindingIdentity
        firstloaded.bindingIdentity should startWith("sha256:")
        firstloaded.patterns.map(_.visualPattern) shouldBe Vector("flow-horizontal", "flow-vertical", "mapping-columns")
        firstloaded.canonicalJson should include_text("\"schema\":\"cozy.visual-page.binding.v1\",\"version\":1")
      }
    }

    "prove canonical convergence over generated valid root, pattern, and object key orderings" in {
      _with_work("binding-property") { root =>
        Given("one validated catalog, an independently loaded canonical binding, and generators for every admitted ordering")
        val validated = _validated()
        val reference = _write(root.resolve("reference.json"), _binding_json())
        val referencebinding = CozyVisualPageBinding.load(reference, validated)
        val patternids = Vector("flow-horizontal", "flow-vertical", "mapping-columns")
        val rootorders = Gen.oneOf(_root_fields.permutations.toVector)
        val patternorders = Gen.oneOf(patternids.permutations.toVector)
        val objectorders = Gen.oneOf(true, false)

        When("ScalaCheck loads generated complete bindings across every admitted root, pattern, and object key ordering")
        val check = Test.check(Test.Parameters.default.withMinSuccessfulTests(30), Prop.forAll(rootorders, patternorders, objectorders) { (rootorder, patternorder, shuffledorder) =>
          val generated = _write(root.resolve("generated.json"), _binding_json(patternorder, shuffled = shuffledorder, rootorder = rootorder))
          val loaded = CozyVisualPageBinding.load(generated, validated)
          loaded.canonicalJson == referencebinding.canonicalJson &&
            loaded.bindingIdentity == referencebinding.bindingIdentity &&
            loaded.patterns.map(_.visualPattern) == patternids.sorted
        })

        Then("every generated binding converges to the canonical JSON and identity with lexically ordered pattern IDs")
        check.passed shouldBe true
        check.succeeded should be >= 30
      }
    }

    "fail closed for a catalog pair mismatch and an unknown or unbound visual pattern" in {
      _with_work("binding-resolution") { root =>
        Given("one resolved catalog and complete binding input plus a binding with an unknown pattern")
        val validated = _validated()
        val complete = _write(root.resolve("complete.json"), _binding_json())
        val unknown = _write(root.resolve("unknown.json"), _binding_json(Vector("flow-horizontal", "flow-vertical", "unknown-pattern")))
        val mismatch = _validated(_catalog.copy(id = "other-catalog"))

        When("the binding is loaded against the mismatched catalog or contains an unbound pattern")
        val catalogfailure = _failure(CozyVisualPageBinding.load(complete, mismatch))
        val patternfailure = _failure(CozyVisualPageBinding.load(unknown, validated))

        Then("both failures carry binding diagnostics with field paths and reasons")
        catalogfailure.getMessage should include_text("VISUAL_PAGE_BINDING_CATALOG")
        catalogfailure.getMessage should include_text("path=$.catalog")
        patternfailure.getMessage should include_text("VISUAL_PAGE_BINDING_PATTERN")
        patternfailure.getMessage should include_text("path=$.patterns")
        Vector(catalogfailure, patternfailure).map(_.getMessage).forall(message => message.contains("VISUAL_PAGE_BINDING_") && message.contains("path=") && message.contains("reason=")) shouldBe true
      }
    }

    "reject malformed, unsafe, and nonconforming binding documents with structured diagnostics" in {
      _with_work("binding-rejections") { root =>
        Given("one resolved Visual Page document and adversarial binding files")
        val validated = _validated()
        val valid = _binding_json()
        val duplicateField = valid.replace("\"schema\":\"cozy.visual-page.binding.v1\"", "\"schema\":\"cozy.visual-page.binding.v1\",\"schema\":\"cozy.visual-page.binding.v1\"")
        val duplicateSemantic = valid.replace("\"semanticSlot\":\"nodes\"", "\"semanticSlot\":\"knowledge\"")
        val missingSemantic = valid.replace(",{\"semanticSlot\":\"parameters\",\"physicalSlot\":\"parameters-slot\"}", "")
        val unknownSemantic = valid.replace("\"semanticSlot\":\"knowledge\"", "\"semanticSlot\":\"unknown\"")
        val duplicatePhysical = valid.replace("\"physicalSlot\":\"parameters-slot\"", "\"physicalSlot\":\"knowledge-slot\"")
        val unknownField = valid.dropRight(1) + ",\"unknown\":true}"
        val wrongSchema = valid.replace("cozy.visual-page.binding.v1", "cozy.visual-page.binding.v2")
        val wrongVersion = valid.replace("\"version\":1", "\"version\":2")
        val wrongProfile = valid.replace("\"profile\":\"business\"", "\"profile\":\"other\"")
        val malformed = "{"
        val files = Vector(
          "duplicate-field" -> duplicateField,
          "duplicate-semantic" -> duplicateSemantic,
          "missing-semantic" -> missingSemantic,
          "unknown-semantic" -> unknownSemantic,
          "duplicate-physical" -> duplicatePhysical,
          "unknown-field" -> unknownField,
          "wrong-schema" -> wrongSchema,
          "wrong-version" -> wrongVersion,
          "wrong-profile" -> wrongProfile,
          "malformed" -> malformed
        ).map { case (name, text) => name -> _write(root.resolve(name + ".json"), text) }
        val unsupported = _write(root.resolve("binding.yaml"), valid)
        val target = _write(root.resolve("target.json"), valid)
        val malformedUtf8 = root.resolve("malformed-utf8.json")
        Files.write(malformedUtf8, Array[Byte]('{'.toByte, 0xc3.toByte, 0x28.toByte, '}'.toByte))
        val directory = Files.createDirectory(root.resolve("directory.json"))
        val link = root.resolve("link.json")
        Files.createSymbolicLink(link, target)

        When("each malformed or unsafe source is loaded")
        val failures = files.map { case (name, path) => name -> _failure(CozyVisualPageBinding.load(path, validated)) }.toMap ++ Map(
          "unsupported" -> _failure(CozyVisualPageBinding.load(unsupported, validated)),
          "malformed-utf8" -> _failure(CozyVisualPageBinding.load(malformedUtf8, validated)),
          "directory" -> _failure(CozyVisualPageBinding.load(directory, validated)),
          "symlink" -> _failure(CozyVisualPageBinding.load(link, validated))
        )

        Then("every rejection is fail-closed and reports a stable binding code, field path, and reason")
        failures.values.map(_.getMessage).forall(message => message.contains("VISUAL_PAGE_BINDING_") && message.contains("path=") && message.contains("reason=")) shouldBe true
        failures("duplicate-field").getMessage should include_text("VISUAL_PAGE_BINDING_DUPLICATE_FIELD")
        failures("duplicate-semantic").getMessage should include_text("VISUAL_PAGE_BINDING_DUPLICATE")
        failures("missing-semantic").getMessage should include_text("VISUAL_PAGE_BINDING_SLOTS")
        failures("unknown-semantic").getMessage should include_text("VISUAL_PAGE_BINDING_SEMANTIC_SLOT")
        failures("duplicate-physical").getMessage should include_text("VISUAL_PAGE_BINDING_DUPLICATE")
        failures("unknown-field").getMessage should include_text("VISUAL_PAGE_BINDING_FIELDS")
        failures("wrong-schema").getMessage should include_text("VISUAL_PAGE_BINDING_SCHEMA")
        failures("wrong-version").getMessage should include_text("VISUAL_PAGE_BINDING_VERSION")
        failures("wrong-profile").getMessage should include_text("VISUAL_PAGE_BINDING_PROFILE")
        failures("malformed").getMessage should include_text("VISUAL_PAGE_BINDING_JSON")
        failures("unsupported").getMessage should include_text("VISUAL_PAGE_BINDING_FORMAT")
        failures("malformed-utf8").getMessage should include_text("VISUAL_PAGE_BINDING_READ")
        failures("directory").getMessage should include_text("VISUAL_PAGE_BINDING_INPUT")
        failures("symlink").getMessage should include_text("VISUAL_PAGE_BINDING_INPUT")
      }
    }
  }

  private val _root_fields = Vector("schema", "version", "id", "profile", "catalog", "patterns")
  private val _semantic_slots = Vector("knowledge", "nodes", "relations", "assets", "parameters")

  private def _catalog: CozyVisualPage.Catalog = CozyVisualPage.Catalog(
    "core",
    1,
    Vector(
      CozyVisualPage.RelationDefinition("next", "from-to"),
      CozyVisualPage.RelationDefinition("causes", "from-to"),
      CozyVisualPage.RelationDefinition("depends-on", "from-to"),
      CozyVisualPage.RelationDefinition("enables", "from-to"),
      CozyVisualPage.RelationDefinition("maps-to", "from-to")
    ),
    Vector(
      CozyVisualPage.LogicalPattern("sequence", Vector(CozyVisualPage.NodeRole("step", 2, 8)), Vector(CozyVisualPage.RelationRule("next", Vector("step"), Vector("step"), 1, 7, "linear")))
    ),
    Vector(
      CozyVisualPage.VisualPattern("flow-horizontal", Vector("causal-chain", "sequence"), Vector(CozyVisualPage.ParameterDefinition("emphasisNode", "node-ref", false), CozyVisualPage.ParameterDefinition("showRelationLabels", "boolean", false))),
      CozyVisualPage.VisualPattern("flow-vertical", Vector("causal-chain", "dependency-map", "sequence"), Vector(CozyVisualPage.ParameterDefinition("emphasisNode", "node-ref", false), CozyVisualPage.ParameterDefinition("showRelationLabels", "boolean", false))),
      CozyVisualPage.VisualPattern("mapping-columns", Vector("mapping"), Vector(CozyVisualPage.ParameterDefinition("showRelationLabels", "boolean", false), CozyVisualPage.ParameterDefinition("sourceColumnTitle", "string", true), CozyVisualPage.ParameterDefinition("targetColumnTitle", "string", true)))
    )
  )

  private def _validated(catalog: CozyVisualPage.Catalog = _catalog): CozyVisualPage.ValidatedDocument = {
    val page = CozyVisualPage.Page(
      "page-1",
      "knowledge-1",
      "en",
      CozyVisualPage.CatalogReference("core", 1),
      CozyVisualPage.Logical(
        "sequence",
        Vector(CozyVisualPage.Node("discover", "step", "Discover", Vector.empty), CozyVisualPage.Node("apply", "step", "Apply", Vector.empty)),
        Vector(CozyVisualPage.Relation("transition", "next", "discover", "apply", Vector.empty))
      ),
      CozyVisualPage.Visual("flow-horizontal", Vector.empty),
      Vector.empty,
      Vector.empty
    )
    val canonical = CozyVisualPage.canonicalJson(page)
    CozyVisualPage.ValidatedDocument(
      page,
      catalog,
      canonical,
      CozyVisualPage.catalogIdentity(catalog),
      CozyVisualPage.logicalCatalogIdentity(catalog),
      Vector(page.id -> CozyVisualPage.logicalIdentity(page, catalog)),
      Vector(page.id -> CozyVisualPage.visualPageIdentity(page, catalog)),
      CozyVisualPage.visualPageIdentity(page, catalog)
    )
  }

  private def _binding_json(
    patterns: Vector[String] = Vector("flow-horizontal", "flow-vertical", "mapping-columns"),
    shuffled: Boolean = false,
    rootorder: Vector[String] = _root_fields
  ): String = {
    val values = Map(
      "schema" -> "\"cozy.visual-page.binding.v1\"",
      "version" -> "1",
      "id" -> "\"business-default\"",
      "profile" -> "\"business\"",
      "catalog" -> "{\"id\":\"core\",\"revision\":1}",
      "patterns" -> patterns.map(_pattern_json(_, shuffled)).mkString("[", ",", "]")
    )
    rootorder.map(name => "\"" + name + "\":" + values(name)).mkString("{", ",", "}")
  }

  private def _pattern_json(pattern: String, shuffled: Boolean): String = {
    val slots = _semantic_slots.map(slot => "{\"semanticSlot\":\"" + slot + "\",\"physicalSlot\":\"" + slot + "-slot\"}").mkString("[", ",", "]")
    if (shuffled) "{\"slots\":" + slots + ",\"visualPattern\":\"" + pattern + "\"}"
    else "{\"visualPattern\":\"" + pattern + "\",\"slots\":" + slots + "}"
  }

  private def _failure(body: => Any): CozyVisualPageBinding.BindingFault = intercept[CozyVisualPageBinding.BindingFault](body)

  private def _write(path: Path, value: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, value.getBytes(StandardCharsets.UTF_8))
    path
  }

  private def _with_work[A](name: String)(body: Path => A): A = {
    val root = Files.createTempDirectory("cozy-visual-page-binding-" + name + "-")
    try body(root)
    finally _delete_tree(root)
  }

  private def _delete_tree(path: Path): Unit =
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)) Files.list(path).iterator().asScala.foreach(_delete_tree)
      Files.deleteIfExists(path)
    }
}
