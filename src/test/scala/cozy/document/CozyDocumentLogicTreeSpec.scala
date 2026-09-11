package cozy.document

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

/*
 * @since   Sep. 11, 2026
 * @version Sep. 12, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyDocumentLogicTreeSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy Document Logic Tree" should {
    "admit the recursive Article 9 Core and Japanese Format" which {
      "retain every current Article 9 statement, stable identity, nested owner, and local CozyVisualPage vocabulary" in {
        _with_temp_dir("cozy-document-logic-tree-article-nine") { root =>
          Given("the direct Article 9 Core authority and its direct Japanese wording Format")
          val fixture = _fixture(root)

          When("the two closed authorities are loaded as one Logic Tree")
          val validated = CozyDocumentLogicTree.load(fixture._1, fixture._2)

          Then("the Core remains locale-free while every Step, claim, and node has one Japanese wording binding")
          validated.core.id shouldBe "application-modeling"
          validated.format.locale shouldBe "ja"
          validated.depthFirstSteps.map(_.id) shouldBe Vector(
            "application-modeling", "application-foundation", "use-case-realization",
            "collaboration-and-interaction", "executable-elements", "application-conclusion"
          )
          validated.claimsById.size shouldBe 18
          validated.claimsById("review-and-refinement") should include("textus-cbd-support")
          validated.labelsById("realization-model") shouldBe "ユースケース実現モデル"
          validated.stepsById("use-case-realization").flow.transitions.map(_.id) shouldBe Vector("execution-depends-on-collaboration")
          validated.core.root.structure.pattern shouldBe "mapping"
        }
      }

      "bind the Format to exact Core bytes instead of a Core id or parsed value alone" in {
        _with_temp_dir("cozy-document-logic-tree-identity") { root =>
          Given("a direct Article 9 Core and a Format bound to its original SHA-256 bytes")
          val fixture = _fixture(root)
          val original = Files.readString(fixture._1, StandardCharsets.UTF_8)
          Files.writeString(fixture._1, original + "\n", StandardCharsets.UTF_8)

          When("only otherwise parseable Core bytes change")
          val failure = _failure(CozyDocumentLogicTree.load(fixture._1, fixture._2))

          Then("the Format binding rejects the changed direct-byte identity")
          failure.code shouldBe "LOGIC_TREE_FORMAT_IDENTITY"
          failure.path shouldBe "$.coreIdentity"
        }
      }
    }

    "closed source and ownership validation" which {
      "reject malformed, unknown, locale-bearing, and label-bearing Core forms before a projection can be made" in {
        _with_temp_dir("cozy-document-logic-tree-closed") { root =>
          Given("four Core contents and one Core filename with malformed, unknown, or lossy authority shapes")
          val fixture = _fixture(root)
          val original = Files.readString(fixture._1, StandardCharsets.UTF_8)
          val malformed = _write(root.resolve("malformed/core.yaml"), "schema: cozy.content-core.logic-tree.v1\nid: application-modeling\nroot:\n")
          val locale = _write(root.resolve("locale/core.yaml"), original.replace("id: application-modeling\nroot:", "id: application-modeling\nlocale: ja\nroot:"))
          val label = _write(root.resolve("label/core.yaml"), original.replace("role: target\n    relations:", "role: target\n        label: forbidden\n    relations:"))
          val localizedname = _write(root.resolve("localized/core-ja.yaml"), original)

          When("the malformed, locale-bearing, presentation-label, and locale-suffixed Core inputs are submitted")
          val failures = Vector(
            _failure(CozyDocumentLogicTree.load(malformed, fixture._2)),
            _failure(CozyDocumentLogicTree.load(locale, fixture._2)),
            _failure(CozyDocumentLogicTree.load(label, fixture._2)),
            _failure(CozyDocumentLogicTree.load(localizedname, fixture._2))
          )

          Then("the closed source boundary rejects unsupported or lossy structure rather than inferring it")
          failures.map(_.code) should contain allOf ("LOGIC_TREE_STRUCTURE", "LOGIC_TREE_FIELDS", "LOGIC_TREE_PATH")
          failures.foreach(_.code should startWith ("LOGIC_TREE_"))
        }
      }

      "reject a duplicate Core root mapping key before lossy JSON normalization" in {
        _with_temp_dir("cozy-document-logic-tree-duplicate-core-key") { root =>
          Given("a direct Article 9 Core with a duplicate root id mapping key")
          val fixture = _fixture(root)
          val original = Files.readString(fixture._1, StandardCharsets.UTF_8)
          val duplicate = _write(
            root.resolve("duplicate-core-key/core.yaml"),
            original.replace("id: application-modeling\nroot:", "id: application-modeling\nid: duplicate-application-modeling\nroot:")
          )

          When("the duplicate-key Core authority is admitted")
          val failure = _failure(CozyDocumentLogicTree.load(duplicate, fixture._2))

          Then("the source boundary rejects it before a JSON object can collapse the repeated mapping")
          failure.code shouldBe "LOGIC_TREE_SOURCE"
          failure.path shouldBe "$.core"
        }
      }

      "reject a duplicate Format root mapping key before lossy JSON normalization" in {
        _with_temp_dir("cozy-document-logic-tree-duplicate-format-key") { root =>
          Given("a direct Article 9 Format with a duplicate root id mapping key")
          val fixture = _fixture(root)
          val original = Files.readString(fixture._2, StandardCharsets.UTF_8)
          val duplicate = _write(
            root.resolve("duplicate-format-key/format-ja.yaml"),
            original.replace("id: application-modeling-ja\ncoreId:", "id: application-modeling-ja\nid: duplicate-application-modeling-ja\ncoreId:")
          )

          When("the duplicate-key Format authority is admitted")
          val failure = _failure(CozyDocumentLogicTree.load(fixture._1, duplicate))

          Then("the source boundary rejects it before a JSON object can collapse the repeated mapping")
          failure.code shouldBe "LOGIC_TREE_SOURCE"
          failure.path shouldBe "$.format"
        }
      }

      "reject duplicate identities, repeated ownership, and ancestor re-entry in the recursive Step tree" in {
        _with_temp_dir("cozy-document-logic-tree-ownership") { root =>
          Given("Article 9 variants with a duplicated child, an ancestor-reentered child, and duplicate node and transition identities")
          val fixture = _fixture(root)
          val original = Files.readString(fixture._1, StandardCharsets.UTF_8)
          val flowless = _without_use_case_flow(original)
          val duplicatechild = _write(root.resolve("duplicate-child/core.yaml"), flowless.replace("id: executable-elements\n          semanticRole", "id: collaboration-and-interaction\n          semanticRole"))
          val ancestor = _write(root.resolve("ancestor/core.yaml"), flowless.replace("id: executable-elements\n          semanticRole", "id: use-case-realization\n          semanticRole"))
          val duplicatenode = _write(root.resolve("duplicate-node/core.yaml"), original.replace("id: root-application-model", "id: root-domain-model"))
          val duplicatetransition = _write(root.resolve("duplicate-transition/core.yaml"), original.replace("id: conclusion-depends-on-realization", "id: realization-depends-on-foundation"))

          When("the recursive ownership and identity variants are normalized")
          val failures = Vector(
            _failure(CozyDocumentLogicTree.load(duplicatechild, fixture._2)),
            _failure(CozyDocumentLogicTree.load(ancestor, fixture._2)),
            _failure(CozyDocumentLogicTree.load(duplicatenode, fixture._2)),
            _failure(CozyDocumentLogicTree.load(duplicatetransition, fixture._2))
          )

          Then("shared ownership, containment cycles, and duplicate local identity fail independently")
          failures.map(_.code) should contain allOf ("LOGIC_TREE_STEP_OWNERSHIP", "LOGIC_TREE_CONTAINMENT_CYCLE", "LOGIC_TREE_IDENTITY")
        }
      }

      "reject unresolved local Relations and non-direct-child Flow endpoints" in {
        _with_temp_dir("cozy-document-logic-tree-scope") { root =>
          Given("Article 9 variants with one escaped local node endpoint, one root Flow endpoint in a grandchild scope, and one Flow self-link")
          val fixture = _fixture(root)
          val original = Files.readString(fixture._1, StandardCharsets.UTF_8)
          val relation = _write(root.resolve("relation/core.yaml"), original.replace("from: root-domain-model", "from: missing-local-node"))
          val flow = _write(root.resolve("flow/core.yaml"), original.replace("fromStepId: use-case-realization", "fromStepId: collaboration-and-interaction"))
          val self = _write(root.resolve("self/core.yaml"), original.replace("toStepId: use-case-realization", "toStepId: application-conclusion"))

          When("the relation and Flow inputs are admitted")
          val relationfailure = _failure(CozyDocumentLogicTree.load(relation, fixture._2))
          val flowfailure = _failure(CozyDocumentLogicTree.load(flow, fixture._2))
          val selffailure = _failure(CozyDocumentLogicTree.load(self, fixture._2))

          Then("each endpoint is constrained to its exact local owner")
          relationfailure.code shouldBe "LOGIC_TREE_RELATION_ENDPOINT"
          flowfailure.code shouldBe "LOGIC_TREE_FLOW_SCOPE"
          selffailure.code shouldBe "LOGIC_TREE_FLOW_SELF"
        }
      }

      "reject missing, duplicate, unknown, and wrong-Core Format wording bindings" in {
        _with_temp_dir("cozy-document-logic-tree-format") { root =>
          Given("Article 9 Format variants that omit a claim, duplicate a binding, introduce an unknown binding, or name another Core")
          val fixture = _fixture(root)
          val original = Files.readString(fixture._2, StandardCharsets.UTF_8)
          val missing = _write(root.resolve("missing/format-ja.yaml"), original.replace("- id: event\n    text:", "- id: missing-event\n    text:"))
          val duplicate = _write(root.resolve("duplicate/format-ja.yaml"), original.replace("- id: event\n    text:", "- id: state-machine\n    text:"))
          val unknown = _write(root.resolve("unknown/format-ja.yaml"), original.replace("- id: event\n    text:", "- id: unknown-event\n    text:"))
          val wrongcore = _write(root.resolve("wrong-core/format-ja.yaml"), original.replace("coreId: application-modeling", "coreId: another-core"))

          When("each one-to-one wording boundary is validated")
          val failures = Vector(
            _failure(CozyDocumentLogicTree.load(fixture._1, missing)),
            _failure(CozyDocumentLogicTree.load(fixture._1, duplicate)),
            _failure(CozyDocumentLogicTree.load(fixture._1, unknown)),
            _failure(CozyDocumentLogicTree.load(fixture._1, wrongcore))
          )

          Then("missing, duplicate, unknown, and mismatched Core bindings cannot be projected")
          failures.map(_.code) should contain allOf ("LOGIC_TREE_FORMAT_COVERAGE", "LOGIC_TREE_IDENTITY", "LOGIC_TREE_FORMAT_CORE")
        }
      }
    }

    "deterministic reader projections" which {
      "render all recursive containment, local Structure, and typed Flow coverage in one overview document" in {
        _with_temp_dir("cozy-document-logic-tree-overview") { root =>
          Given("the admitted real Article 9 Logic Tree")
          val fixture = _fixture(root)
          val validated = CozyDocumentLogicTree.load(fixture._1, fixture._2)

          When("the overview is rendered twice")
          val first = CozyDocumentLogicTreeProjection.overview(validated)
          val second = CozyDocumentLogicTreeProjection.overview(validated)

          Then("the self-contained reader tree has stable bytes and every declared Step, local relation, and Flow edge")
          first shouldBe second
          first.html should include("id=\"logic-tree-overview\"")
          validated.depthFirstSteps.foreach(step => first.html should include(s"""id="step-${step.id}""""))
          first.html should include("root-domain-maps-to-application")
          first.html should include("realization-depends-on-foundation")
          first.html should include("ロジックツリーの概要")
          first.html should include("主張")
          first.html should include("ローカル構造")
          first.html should include("直接の子ステップのフロー")
          first.html should include("直接の子ステップ間フロー遷移はありません。")
          first.html should not include("Logic Tree overview")
          first.html should not include("Direct-child Flow")
          first.html should not include("<table")
        }
      }

      "render one deterministic 16:9 depth-first slide page for each parent and leaf with navigation and print boundaries" in {
        _with_temp_dir("cozy-document-logic-tree-slides") { root =>
          Given("the admitted real Article 9 Logic Tree")
          val fixture = _fixture(root)
          val validated = CozyDocumentLogicTree.load(fixture._1, fixture._2)

          When("the Step-slide document is rendered twice")
          val first = CozyDocumentLogicTreeProjection.slides(validated)
          val second = CozyDocumentLogicTreeProjection.slides(validated)

          Then("every parent and leaf has one depth-first page with ancestor context, keyboard navigation, and one-page print form")
          first shouldBe second
          validated.depthFirstSteps.foreach(step => first.html should include(s"""id="slide-${step.id}""""))
          first.html should include("aspect-ratio:16/9")
          first.html should include("ancestor-context")
          first.html should include("rel=\"prev\"")
          first.html should include("rel=\"next\"")
          first.html should include("ArrowLeft")
          first.html should include("page-break-after:always")
          first.html should include("ロジックツリーのステップスライド")
          first.html should include("深さ優先のステップページ")
          first.html should include("直接の子ステップ")
          first.html should include("前へ")
          first.html should include("次へ")
          first.html should include("スライド一覧")
          first.html should include("ステップページのナビゲーション")
          first.html should not include("Logic Tree Step Slides")
          first.html should not include("depth-first Step pages")
          first.html should not include("Step page navigation")
        }
      }

      "remain deterministic for generated overview and slide selection repetitions" in {
        _with_temp_dir("cozy-document-logic-tree-property") { root =>
          Given("one admitted Article 9 Logic Tree and generated valid projection-kind selections")
          val fixture = _fixture(root)
          val validated = CozyDocumentLogicTree.load(fixture._1, fixture._2)
          val kinds = Gen.oneOf("overview", "slides")

          When("ScalaCheck repeatedly selects a projection kind")
          val check = Test.check(Test.Parameters.default.withMinSuccessfulTests(30), Prop.forAll(kinds) { kind =>
            val first = if (kind == "overview") CozyDocumentLogicTreeProjection.overview(validated) else CozyDocumentLogicTreeProjection.slides(validated)
            val second = if (kind == "overview") CozyDocumentLogicTreeProjection.overview(validated) else CozyDocumentLogicTreeProjection.slides(validated)
            first == second && first.html.contains(validated.core.id)
          })

          Then("all generated selections retain an exact deterministic output identity")
          check.passed shouldBe true
          check.succeeded should be >= 30
        }
      }
    }

    "bounded command form" which {
      "accept exactly the no-alias render grammar and atomically write only its explicit safe HTML output" in {
        _with_temp_dir("cozy-document-logic-tree-command") { root =>
          Given("direct Core and Format inputs, an existing direct output parent, and no Document Project state")
          val fixture = _fixture(root)
          val output = root.resolve("overview.html")

          When("the exact overview command is executed")
          val report = _execute(List("document-project", "logic-tree", "render", "--core", fixture._1.toString, "--format", fixture._2.toString, "--kind", "overview", "--save", output.toString))

          Then("only the requested HTML is written and the report identifies the isolated Logic Tree render")
          Files.isRegularFile(output, LinkOption.NOFOLLOW_LINKS) shouldBe true
          Files.readString(output, StandardCharsets.UTF_8) should include("logic-tree-overview")
          report should include("Cozy Content Core Logic Tree Render")
          _relative_files(root) shouldBe Set("content/core.yaml", "content/format-ja.yaml", "overview.html")
        }
      }

      "reject aliases, unsupported kinds, and unsafe output suffixes without writing an output" in {
        _with_temp_dir("cozy-document-logic-tree-command-rejection") { root =>
          Given("direct Core and Format inputs with alias, unsupported-kind, and non-HTML output requests")
          val fixture = _fixture(root)
          val aliasoutput = root.resolve("alias.html")
          val kindoutput = root.resolve("kind.html")
          val unsafeoutput = root.resolve("unsafe.txt")

          When("the exact bounded command grammar is violated")
          val failures = Vector(
            _failure(CozyDocumentLogicTreeCommand.execute(List("document-project", "logic-tree", "render", "--input", fixture._1.toString, "--format", fixture._2.toString, "--kind", "overview", "--save", aliasoutput.toString))),
            _failure(CozyDocumentLogicTreeCommand.execute(List("document-project", "logic-tree", "render", "--core", fixture._1.toString, "--format", fixture._2.toString, "--kind", "tree", "--save", kindoutput.toString))),
            _failure(CozyDocumentLogicTreeCommand.execute(List("document-project", "logic-tree", "render", "--core", fixture._1.toString, "--format", fixture._2.toString, "--kind", "slides", "--save", unsafeoutput.toString)))
          )

          Then("no alias, unsupported mode, or non-HTML destination is admitted or written")
          failures.map(_.code) should contain allOf ("LOGIC_TREE_CLI", "LOGIC_TREE_PATH")
          Files.exists(aliasoutput, LinkOption.NOFOLLOW_LINKS) shouldBe false
          Files.exists(kindoutput, LinkOption.NOFOLLOW_LINKS) shouldBe false
          Files.exists(unsafeoutput, LinkOption.NOFOLLOW_LINKS) shouldBe false
        }
      }
    }
  }

  private def _fixture(root: Path): (Path, Path) = {
    val directory = Files.createDirectories(root.resolve("content"))
    val core = directory.resolve("core.yaml")
    val format = directory.resolve("format-ja.yaml")
    Files.copy(_resource("/cozy/document/phase-58/application-modeling/content/core.yaml"), core)
    Files.copy(_resource("/cozy/document/phase-58/application-modeling/content/format-ja.yaml"), format)
    (core, format)
  }

  private def _resource(value: String): Path =
    Paths.get(getClass.getResource(value).toURI)

  private def _write(path: Path, value: String): Path = {
    Files.createDirectories(path.getParent)
    Files.writeString(path, value, StandardCharsets.UTF_8)
    path
  }

  private def _without_use_case_flow(value: String): String =
    value.replace(
      """|      flow:
         |        id: use-case-realization-flow
         |        transitions:
         |          - id: execution-depends-on-collaboration
         |            relationType: depends-on
         |            fromStepId: executable-elements
         |            toStepId: collaboration-and-interaction""".stripMargin,
      """|      flow:
         |        id: use-case-realization-flow
         |        transitions: []""".stripMargin
    )

  private def _execute(args: List[String]): String = {
    val bytes = new ByteArrayOutputStream()
    Console.withOut(new PrintStream(bytes, true, "UTF-8")) {
      CozyDocumentLogicTreeCommand.execute(args) shouldBe true
    }
    bytes.toString("UTF-8").trim
  }

  private def _failure(body: => Any): CozyDocumentLogicTree.LogicTreeFault =
    intercept[CozyDocumentLogicTree.LogicTreeFault](body)

  private def _relative_files(root: Path): Set[String] = {
    val stream = Files.walk(root)
    try stream.iterator().asScala.filter(path => Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).map(root.relativize(_).toString.replace('\\', '/')).toSet
    finally stream.close()
  }

  private def _with_temp_dir(name: String)(body: Path => Unit): Unit = {
    val root = Files.createTempDirectory(name)
    try body(root)
    finally _delete(root)
  }

  private def _delete(path: Path): Unit =
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      val stream = Files.walk(path)
      try stream.iterator().asScala.toVector.sortBy(_.toString.length).reverse.foreach { item =>
        try Files.deleteIfExists(item) catch { case NonFatal(_) => () }
      }
      finally stream.close()
    }
}
