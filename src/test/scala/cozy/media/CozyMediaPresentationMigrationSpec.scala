package cozy.media

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path}
import java.security.MessageDigest
import scala.collection.JavaConverters._
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug. 27, 2026
 * @version Aug. 27, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyMediaPresentationMigrationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy media presentation migration" should {
    "migrate a complete human-authored map through the public CLI without invoking a renderer" in {
      _with_work("migration-success") { root =>
        Given("a direct legacy Slide IR, closed catalog, complete Visual Page Set, and bijective semantic map")
        val fixture = _fixture(root)

        When("the public presentation migration command receives separated and equals-form required options")
        val report = _capture_stdout {
          CozyMedia.execute(List(
            "media", "presentation", "migrate", fixture.legacy.toString,
            "--semantic-map=" + fixture.semanticmap.toString,
            "--catalog", fixture.catalog.toString,
            "--save=" + fixture.output.toString
          ))
        }
        val saved = CozyVisualPage.load(fixture.output, fixture.catalog)

        Then("it atomically saves the canonical target set and emits the deterministic raw-digest migration report")
        Files.readString(fixture.output, StandardCharsets.UTF_8) shouldBe CozyVisualPage.canonicalJson(fixture.pageset) + "\n"
        saved.document shouldBe fixture.pageset
        saved.documentIdentity shouldBe CozyVisualPage.visualPageSetIdentity(fixture.pageset, _catalog())
        report.stripSuffix("\n") shouldBe Vector(
          "schema: cozy.visual-page.migration-report.v1",
          "version: 1",
          s"legacySlideIrSha256: ${_sha256(fixture.legacy)}",
          "sourceElementCount: 6",
          "bindingCount: 6",
          s"catalogIdentity: ${saved.catalogIdentity}",
          s"visualPageSetIdentity: ${saved.documentIdentity}",
          "status: migrated"
        ).mkString("\n")
      }
    }

    "reject invalid or lossy migration inputs without a claimed output or replacement of existing bytes" in {
      _with_work("migration-rejections") { root =>
        Given("a valid migration fixture, a pre-existing output, and source/map/target failure variants")
        val fixture = _fixture(root)
        val digest = _sha256(fixture.legacy)
        val sentinel = "preserve-existing-output".getBytes(StandardCharsets.UTF_8)
        val incomplete = fixture.bindings.dropRight(1)
        val duplicate = fixture.bindings :+ fixture.bindings.head
        val outofrange = fixture.bindings.updated(1, fixture.bindings(1).replace("\"elementIndex\":1", "\"elementIndex\":99"))
        val duplicatetarget = fixture.bindings.updated(1, fixture.bindings.head.replace("\"slideId\":\"overview\",\"elementIndex\":0", "\"slideId\":\"overview\",\"elementIndex\":1"))
        val unresolvedtarget = fixture.bindings.updated(1, fixture.bindings(1).replace("\"id\":\"Detail\"", "\"id\":\"Missing\""))
        val wrongkind = fixture.bindings.updated(5, fixture.bindings(5).replace("\"kind\":\"asset-id\",\"id\":\"infographic\"", "\"kind\":\"source-path\",\"id\":\"sources/migration.txt\""))
        val wrongvalue = fixture.bindings.updated(1, fixture.bindings(1).replace("\"kind\":\"node-label\",\"id\":\"Detail\"", "\"kind\":\"source-path\",\"id\":\"sources/migration.txt\""))
        val malformedutf8 = root.resolve("malformed-utf8.json")
        Files.write(malformedutf8, Array[Byte]('{'.toByte, 0xc3.toByte, 0x28.toByte, '}'.toByte))
        val nonjson = _write(root.resolve("non-json.json"), "not JSON")
        val pagetarget = _write(root.resolve("page-target.json"), _migration_map(digest, fixture.page, fixture.bindings))
        val unknownfield = _write(root.resolve("unknown-field.json"), _migration_map(digest, fixture.pageset, fixture.bindings).dropRight(1) + ",\"unknown\":true}")
        val duplicatefield = _write(root.resolve("duplicate-field.json"), _migration_map(digest, fixture.pageset, fixture.bindings).replace("\"schema\":\"cozy.visual-page.migration-map.v1\"", "\"schema\":\"cozy.visual-page.migration-map.v1\",\"schema\":\"cozy.visual-page.migration-map.v1\""))
        val missingfield = _write(root.resolve("missing-field.json"), _migration_map(digest, fixture.pageset, fixture.bindings).replace("\"bindings\":", "\"omittedBindings\":"))
        val sourcemismatch = _write(root.resolve("source-mismatch.json"), _migration_map("0" * 64, fixture.pageset, fixture.bindings))
        val incompletepath = _write(root.resolve("incomplete.json"), _migration_map(digest, fixture.pageset, incomplete))
        val duplicatepath = _write(root.resolve("duplicate.json"), _migration_map(digest, fixture.pageset, duplicate))
        val outofrangepath = _write(root.resolve("out-of-range.json"), _migration_map(digest, fixture.pageset, outofrange))
        val duplicatetargetpath = _write(root.resolve("duplicate-target.json"), _migration_map(digest, fixture.pageset, duplicatetarget))
        val unresolvedtargetpath = _write(root.resolve("unresolved-target.json"), _migration_map(digest, fixture.pageset, unresolvedtarget))
        val wrongkindpath = _write(root.resolve("wrong-kind.json"), _migration_map(digest, fixture.pageset, wrongkind))
        val wrongvaluepath = _write(root.resolve("wrong-value.json"), _migration_map(digest, fixture.pageset, wrongvalue))
        val link = root.resolve("semantic-link.json")
        Files.createSymbolicLink(link, fixture.semanticmap)
        val variants = Vector(
          "source-digest" -> sourcemismatch,
          "incomplete" -> incompletepath,
          "duplicate-source" -> duplicatepath,
          "out-of-range" -> outofrangepath,
          "duplicate-target" -> duplicatetargetpath,
          "unresolved-target" -> unresolvedtargetpath,
          "wrong-kind" -> wrongkindpath,
          "wrong-value" -> wrongvaluepath,
          "unknown-field" -> unknownfield,
          "duplicate-field" -> duplicatefield,
          "missing-field" -> missingfield,
          "page-target" -> pagetarget,
          "non-json" -> nonjson,
          "malformed-utf8" -> malformedutf8,
          "direct-file" -> link
        )

        When("each invalid map is sent to the public CLI against the same pre-existing output")
        val failures = variants.map { case (name, semanticmap) =>
          Files.write(fixture.output, sentinel)
          val result = _capture_failure_stdout {
            CozyMedia.execute(List(
              "media", "presentation", "migrate", fixture.legacy.toString,
              "--semantic-map", semanticmap.toString,
              "--catalog", fixture.catalog.toString,
              "--save", fixture.output.toString
            ))
          }
          name -> (result._1, result._2, Files.readAllBytes(fixture.output))
        }.toMap

        Then("every structured migration diagnostic leaves no report and preserves the previously claimed output bytes")
        failures.values.foreach { case (failure, stdout, outputbytes) =>
          failure.getMessage should include("MIGRATION_")
          failure.getMessage should include("path=")
          failure.getMessage should include("reason=")
          stdout shouldBe ""
          outputbytes.sameElements(sentinel) shouldBe true
        }
        failures("source-digest")._1.getMessage should include("MIGRATION_LEGACY_DIGEST")
        failures("incomplete")._1.getMessage should include("MIGRATION_BINDING_MISSING")
        failures("duplicate-source")._1.getMessage should include("MIGRATION_BINDING_DUPLICATE")
        failures("out-of-range")._1.getMessage should include("MIGRATION_BINDING_RANGE")
        failures("duplicate-target")._1.getMessage should include("MIGRATION_TARGET_DUPLICATE")
        failures("unresolved-target")._1.getMessage should include("MIGRATION_TARGET_RESOLUTION")
        failures("wrong-kind")._1.getMessage should include("MIGRATION_TARGET_KIND")
        failures("wrong-value")._1.getMessage should include("MIGRATION_TARGET_VALUE")
        failures("unknown-field")._1.getMessage should include("MIGRATION_FIELDS")
        failures("duplicate-field")._1.getMessage should include("MIGRATION_DUPLICATE_FIELD")
        failures("missing-field")._1.getMessage should include("MIGRATION_FIELDS")
        failures("page-target")._1.getMessage should include("MIGRATION_TARGET_PAGE_SET")
        failures("non-json")._1.getMessage should include("MIGRATION_JSON")
        failures("malformed-utf8")._1.getMessage should include("MIGRATION_READ")
        failures("direct-file")._1.getMessage should include("MIGRATION_INPUT")
      }
    }

    "converge at least thirty valid binding-order variations to one canonical saved Visual Page Set" in {
      _with_work("migration-property") { root =>
        Given("one legacy source and map whose six exact bindings can be generated in distinct valid orders")
        val fixture = _fixture(root)
        val digest = _sha256(fixture.legacy)
        val orders = Gen.choose(0, 719).map(index => _permutation(fixture.bindings, index))

        When("ScalaCheck migrates at least thirty generated binding-order variations")
        val check = Test.check(Test.Parameters.default.withMinSuccessfulTests(30), Prop.forAll(orders) { bindings =>
          _write(fixture.semanticmap, _migration_map(digest, fixture.pageset, bindings))
          val report = CozyMediaPresentationMigration.execute(List(
            fixture.legacy.toString,
            "--semantic-map", fixture.semanticmap.toString,
            "--catalog", fixture.catalog.toString,
            "--save", fixture.output.toString
          ))
          Files.readString(fixture.output, StandardCharsets.UTF_8) == CozyVisualPage.canonicalJson(fixture.pageset) + "\n" &&
            CozyVisualPage.load(fixture.output, fixture.catalog).documentIdentity == CozyVisualPage.visualPageSetIdentity(fixture.pageset, _catalog()) &&
            report.contains("status: migrated")
        })

        Then("mapping source order does not alter the saved semantic value or its identity")
        check.passed shouldBe true
        check.succeeded should be >= 30
      }
    }
  }

  private final case class Fixture(
    legacy: Path,
    catalog: Path,
    page: CozyVisualPage.Page,
    pageset: CozyVisualPage.PageSet,
    bindings: Vector[String],
    semanticmap: Path,
    output: Path
  )

  private def _fixture(root: Path): Fixture = {
    _write(root.resolve("sources/migration.txt"), "migration provenance")
    _write(root.resolve("assets/infographic.png"), "infographic bytes")
    val legacy = _write(root.resolve("legacy-slide-ir.yaml"),
      """schema: cozy.slide-ir.v1
        |knowledge: article/example
        |language: en
        |slides:
        |  - id: overview
        |    title: Overview
        |    layout: content
        |    elements:
        |      - role: title
        |        text: Overview
        |      - role: body
        |        text: Detail
        |      - role: body
        |        text: Context
        |      - role: body
        |        text: Summary
        |      - role: caption
        |        text: Caption
        |      - role: figure
        |        asset: infographic
        |""".stripMargin
    )
    val asset = CozyVisualPage.Asset("infographic", "assets/infographic.png", "image/png", _sha256(root.resolve("assets/infographic.png")))
    val labels = Vector("Overview", "Detail", "Context", "Summary", "Caption")
    val nodes = labels.zipWithIndex.map { case (label, index) =>
      CozyVisualPage.Node("step-" + (index + 1), "step", label, Vector("migration"))
    }
    val relations = nodes.sliding(2).zipWithIndex.map { case (pair, index) =>
      CozyVisualPage.Relation("next-" + (index + 1), "next", pair.head.id, pair.last.id, Vector("migration"))
    }.toVector
    val page = CozyVisualPage.Page(
      "migration-page",
      "article/example",
      "en",
      CozyVisualPage.CatalogReference("core", 1),
      CozyVisualPage.Logical("sequence", nodes, relations),
      CozyVisualPage.Visual("flow-horizontal", Vector.empty),
      Vector(asset),
      Vector(CozyVisualPage.SourceBinding("migration", "sources/migration.txt"))
    )
    val pageset = CozyVisualPage.PageSet("migration-set", Vector(page))
    val bindings = Vector(
      _binding("overview", 0, "node-label", "Overview"),
      _binding("overview", 1, "node-label", "Detail"),
      _binding("overview", 2, "node-label", "Context"),
      _binding("overview", 3, "node-label", "Summary"),
      _binding("overview", 4, "node-label", "Caption"),
      _binding("overview", 5, "asset-id", "infographic")
    )
    val catalog = _write(root.resolve("catalog.json"), CozyVisualPage.canonicalCatalogJson(_catalog()))
    val semanticmap = _write(root.resolve("migration-map.json"), _migration_map(_sha256(legacy), pageset, bindings))
    Fixture(legacy, catalog, page, pageset, bindings, semanticmap, root.resolve("migrated.json"))
  }

  private def _binding(slideid: String, elementindex: Int, kind: String, id: String): String =
    s"""{"slideId":"$slideid","elementIndex":$elementindex,"target":{"pageId":"migration-page","kind":"$kind","id":"$id"}}"""

  private def _migration_map(digest: String, document: CozyVisualPage.Document, bindings: Vector[String]): String =
    s"""{"schema":"cozy.visual-page.migration-map.v1","version":1,"legacySlideIrSha256":"$digest","visualPageSet":${CozyVisualPage.canonicalJson(document)},"bindings":${bindings.mkString("[", ",", "]")}}"""

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

  private def _permutation[A](values: Vector[A], seed: Int): Vector[A] = {
    var remaining = values
    var remainder = seed
    var ordered = Vector.empty[A]
    while (remaining.nonEmpty) {
      val position = remainder % remaining.size
      remainder = remainder / remaining.size
      ordered :+= remaining(position)
      remaining = remaining.patch(position, Vector.empty, 1)
    }
    ordered
  }

  private def _capture_stdout(body: => Unit): String = {
    val stream = new ByteArrayOutputStream
    Console.withOut(stream)(body)
    stream.toString(StandardCharsets.UTF_8.name)
  }

  private def _capture_failure_stdout(body: => Unit): (RuntimeException, String) = {
    val stream = new ByteArrayOutputStream
    val failure = intercept[RuntimeException](Console.withOut(stream)(body))
    failure -> stream.toString(StandardCharsets.UTF_8.name)
  }

  private def _write(path: Path, value: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, value.getBytes(StandardCharsets.UTF_8))
    path
  }

  private def _sha256(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).map(byte => f"${byte & 0xff}%02x").mkString

  private def _with_work[A](name: String)(body: Path => A): A = {
    val root = Files.createTempDirectory("cozy-presentation-migration-" + name + "-")
    try body(root)
    finally _delete_tree(root)
  }

  private def _delete_tree(path: Path): Unit =
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)) Files.list(path).iterator().asScala.foreach(_delete_tree)
      Files.deleteIfExists(path)
    }
}
