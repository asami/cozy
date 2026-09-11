package cozy.document

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

/*
 * @since   Sep. 12, 2026
 * @version Sep. 12, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyDocumentDescriptionProjectionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy Document Description Projection" should {
    "admit the direct Article 9 Japanese Document authority at its exact current Core binding" in {
      _with_temp_dir("cozy-document-description-article-nine") { root =>
        Given("unchanged copied direct Article 9 Core and Japanese Document authorities")
        val fixture = _fixture(root)

        When("the Document authority is loaded through its typed admission boundary")
        val validated = CozyDocumentDescription.loadDocument(fixture._1, fixture._2)

        Then("the complete recursive authority remains current and has complete typed Core coverage")
        validated.description.id shouldBe "application-modeling-document-ja"
        validated.description.locale shouldBe "ja"
        validated.core.core.id shouldBe "application-modeling"
        validated.coreIdentity shouldBe "sha256:d7b8339d1a79790ad45d7e99bc9e7bf353322d7ffc2f05b44da2b8a98c826cbc"
        validated.documentIdentity shouldBe "sha256:1f7192fbf4fd47713bc7e6da515d27b5cf7ee91b45805be99a642d5cf31eb4df"
        _sections(validated.description.document.sections).map(_.id) should contain allOf ("application-modeling-introduction", "conclusion-section")
      }
    }

    "retain the real Article 9 local Structure and direct-child Flow semantics in exact depth-first order" in {
      _with_temp_dir("cozy-document-description-article-nine-semantics") { root =>
        Given("the copied real Article 9 Core and Japanese Document authorities")
        val fixture = _fixture(root)
        val validated = CozyDocumentDescription.loadDocument(fixture._1, fixture._2)

        When("the typed recursive Core is admitted for document review")
        val structures = validated.core.depthFirstSteps.map { step =>
          (
            step.id,
            step.structure.pattern,
            step.structure.nodes.map(node => node.id -> node.role),
            step.structure.relations.map(relation => (relation.id, relation.relationType, relation.from, relation.to))
          )
        }
        val flows = validated.core.depthFirstSteps.map { step =>
          (step.id, step.flow.id, step.flow.transitions.map(transition => (transition.id, transition.relationType, transition.fromStepId, transition.toStepId)))
        }

        Then("each local Structure and direct-child Flow preserves its catalog-constrained typed meaning")
        structures shouldBe Vector(
          ("application-modeling", "mapping", Vector("root-domain-model" -> "source", "root-application-model" -> "target"), Vector(("root-domain-maps-to-application", "maps-to", "root-domain-model", "root-application-model"))),
          ("application-foundation", "dependency-map", Vector("foundation-static-view" -> "dependency", "foundation-dynamic-view" -> "dependent"), Vector(("foundation-dynamic-depends-on-static", "depends-on", "foundation-dynamic-view", "foundation-static-view"))),
          ("use-case-realization", "mapping", Vector("realization-scenario" -> "source", "realization-model" -> "target"), Vector(("realization-scenario-maps-to-model", "maps-to", "realization-scenario", "realization-model"))),
          ("collaboration-and-interaction", "causal-chain", Vector("collaboration-responsibility" -> "cause", "collaboration-interaction" -> "effect"), Vector(("collaboration-responsibility-causes-interaction", "causes", "collaboration-responsibility", "collaboration-interaction"), ("collaboration-responsibility-enables-interaction", "enables", "collaboration-responsibility", "collaboration-interaction"))),
          ("executable-elements", "causal-chain", Vector("execution-event" -> "cause", "execution-service" -> "effect"), Vector(("execution-event-causes-service", "causes", "execution-event", "execution-service"), ("execution-event-enables-service", "enables", "execution-event", "execution-service"))),
          ("application-conclusion", "dependency-map", Vector("conclusion-review" -> "dependency", "conclusion-cml" -> "dependent"), Vector(("conclusion-cml-depends-on-review", "depends-on", "conclusion-cml", "conclusion-review")))
        )
        flows shouldBe Vector(
          ("application-modeling", "application-modeling-flow", Vector(("realization-depends-on-foundation", "depends-on", "use-case-realization", "application-foundation"), ("conclusion-depends-on-realization", "depends-on", "application-conclusion", "use-case-realization"))),
          ("application-foundation", "application-foundation-flow", Vector.empty),
          ("use-case-realization", "use-case-realization-flow", Vector(("execution-depends-on-collaboration", "depends-on", "executable-elements", "collaboration-and-interaction"))),
          ("collaboration-and-interaction", "collaboration-and-interaction-flow", Vector.empty),
          ("executable-elements", "executable-elements-flow", Vector.empty),
          ("application-conclusion", "application-conclusion-flow", Vector.empty)
        )
      }
    }

    "admit the direct Article 9 Japanese Summary authority at its exact current Core and Document bindings" in {
      _with_temp_dir("cozy-document-description-summary-article-nine") { root =>
        Given("unchanged copied direct Article 9 Core, Document, and Summary authorities")
        val fixture = _summary_fixture(root)

        When("the Summary authority is loaded through its typed admission boundary")
        val validated = CozyDocumentDescription.loadSummary(fixture._1, fixture._2, fixture._3)

        Then("the concise authority remains current against both direct upstream byte identities")
        validated.description.id shouldBe "application-modeling-summary-ja"
        validated.description.locale shouldBe "ja"
        validated.description.core.identity shouldBe "sha256:d7b8339d1a79790ad45d7e99bc9e7bf353322d7ffc2f05b44da2b8a98c826cbc"
        validated.description.document.id shouldBe "application-modeling-document-ja"
        validated.description.document.identity shouldBe "sha256:1f7192fbf4fd47713bc7e6da515d27b5cf7ee91b45805be99a642d5cf31eb4df"
        validated.summaryIdentity shouldBe "sha256:55b518b61fcb43a2b8d338c082d6eed63742dd7956ff32a85a64f1d34882877c"
        validated.description.summary.units.map(_.id) shouldBe Vector("foundation-purpose", "use-case-model", "collaboration-interaction", "executable-elements", "review-and-realization")
      }
    }

    "render a deterministic human-primary Japanese document with exact secondary Core traceability" in {
      _with_temp_dir("cozy-document-description-projection") { root =>
        Given("one fully admitted Article 9 Japanese Document authority")
        val fixture = _fixture(root)
        val validated = CozyDocumentDescription.loadDocument(fixture._1, fixture._2)

        When("the document review projection is rendered twice")
        val first = CozyDocumentDescriptionProjection.render(validated)
        val second = CozyDocumentDescriptionProjection.render(validated)

        Then("its exact bytes and identity are stable while readable authored prose is primary")
        first shouldBe second
        first.identity should startWith ("sha256:")
        first.html should include("SimpleModeling 第9回 アプリケーションモデリング")
        first.html should include("ユースケースから実現モデルへ")
        first.html should include("生成AIは対応案を作成でき")
        first.html should include("data-document-id=\"application-modeling-document-ja\"")
        first.html should include("data-core-identity=\"sha256:d7b8339d1a79790ad45d7e99bc9e7bf353322d7ffc2f05b44da2b8a98c826cbc\"")
        first.html should include("data-core-relations=\"root-domain-maps-to-application")
        first.html should include("data-relation-id=\"collaboration-responsibility-causes-interaction\" data-relation-type=\"causes\"")
        first.html should include("data-relation-id=\"collaboration-responsibility-enables-interaction\" data-relation-type=\"enables\"")
        first.html should include("data-relation-id=\"execution-event-causes-service\" data-relation-type=\"causes\"")
        first.html should include("data-relation-id=\"execution-event-enables-service\" data-relation-type=\"enables\"")
        first.html should not include("<table")
      }
    }

    "render separate Japanese reader subsections for each local Structure and direct-child Flow while retaining structural traceability attributes" in {
      _with_temp_dir("cozy-document-description-local-structure-and-flow") { root =>
        Given("the fully admitted Article 9 Japanese Document authority")
        val fixture = _fixture(root)
        val validated = CozyDocumentDescription.loadDocument(fixture._1, fixture._2)

        When("the document review projection is rendered")
        val html = CozyDocumentDescriptionProjection.render(validated).html

        Then("localized headings, labels, roles, and typed wording remain distinct from stable data attributes")
        html should include("<h3>ローカル構造: アプリケーションモデリング</h3>")
        html should include("<h3>直接の子ステップのフロー: アプリケーションモデリング</h3>")
        html should include("パターン: 対応付け")
        html should include("ドメインモデル")
        html should include("（役割: 対応付け元）")
        html should include("（対応付ける）")
        html should include("直接の子ステップ間のフローはありません。")
        html should include("data-step-id=\"application-modeling\"")
        html should include("data-structure-pattern=\"mapping\"")
        html should include("data-node-id=\"root-domain-model\" data-node-role=\"source\"")
        html should include("data-relation-id=\"root-domain-maps-to-application\" data-relation-type=\"maps-to\"")
        html should include("data-flow-id=\"application-modeling-flow\"")
        html should include("data-flow-transition-id=\"realization-depends-on-foundation\" data-flow-relation-type=\"depends-on\"")
        html should not include("ローカル構造: application-modeling")
        html should not include("pattern: mapping")
      }
    }

    "render a deterministic human-primary Japanese summary with exact typed Core and Document traceability" in {
      _with_temp_dir("cozy-document-description-summary-projection") { root =>
        Given("one fully admitted Article 9 Japanese Summary authority")
        val fixture = _summary_fixture(root)
        val validated = CozyDocumentDescription.loadSummary(fixture._1, fixture._2, fixture._3)

        When("the summary review projection is rendered twice")
        val first = CozyDocumentDescriptionProjection.renderSummary(validated)
        val second = CozyDocumentDescriptionProjection.renderSummary(validated)

        Then("its exact bytes and identity are stable while authored units, emphasis, and references remain primary and ordered")
        first shouldBe second
        first.identity should startWith ("sha256:")
        first.html should include("SimpleModeling 第9回 アプリケーションモデリング：要約")
        first.html should include("ドメインの意味を利用目的の実現へつなぐ")
        first.html should include("要求から振る舞いまでの対応を保ち")
        first.html should include("data-summary-id=\"application-modeling-summary-ja\"")
        first.html should include("data-summary-identity=\"sha256:55b518b61fcb43a2b8d338c082d6eed63742dd7956ff32a85a64f1d34882877c\"")
        first.html should include("data-document-id=\"application-modeling-document-ja\"")
        first.html should include("data-document-identity=\"sha256:1f7192fbf4fd47713bc7e6da515d27b5cf7ee91b45805be99a642d5cf31eb4df\"")
        first.html should include("data-core-id=\"application-modeling\"")
        first.html should include("data-core-identity=\"sha256:d7b8339d1a79790ad45d7e99bc9e7bf353322d7ffc2f05b44da2b8a98c826cbc\"")
        first.html should include("data-core-relations=\"root-domain-maps-to-application\"")
        first.html should include("data-core-relations=\"realization-scenario-maps-to-model\"")
        first.html should include("data-core-relations=\"conclusion-cml-depends-on-review\"")
        first.html should include("data-core-flows=\"application-modeling-flow\"")
        validated.description.summary.units.foreach { unit =>
          first.html should include(s"""data-unit-id="${unit.id}"""")
          first.html should include(s"""data-emphasis="${unit.emphasis}"""")
          first.html should include(unit.heading)
          first.html should include(unit.message)
          first.html should include(s"""data-core-steps="${unit.coreRefs.steps.mkString(" ")}"""")
          first.html should include(s"""data-core-claims="${unit.coreRefs.claims.mkString(" ")}"""")
          first.html should include(s"""data-core-nodes="${unit.coreRefs.nodes.mkString(" ")}"""")
          first.html should include(s"""data-core-relations="${unit.coreRefs.relations.mkString(" ")}"""")
          first.html should include(s"""data-core-flows="${unit.coreRefs.flows.mkString(" ")}"""")
        }
        val positions = validated.description.summary.units.map(unit => first.html.indexOf(s"""data-unit-id="${unit.id}""""))
        positions shouldBe positions.sorted
      }
    }

    "render every authored recursive section, closed prose block kind, list item, and local Core structure" in {
      _with_temp_dir("cozy-document-description-blocks") { root =>
        Given("the admitted real Article 9 authority with every closed document block kind")
        val fixture = _fixture(root)
        val validated = CozyDocumentDescription.loadDocument(fixture._1, fixture._2)

        When("the review HTML is composed from the authority")
        val html = CozyDocumentDescriptionProjection.render(validated).html

        Then("all authored records have escaped traceable projections and logical structure is visibly secondary")
        _sections(validated.description.document.sections).foreach(section => html should include(s"""data-section-id="${section.id}""""))
        _blocks(validated.description.document.sections).foreach(block => html should include(s"""data-block-id="${block.id}""""))
        html should include("data-list-item-id=\"introduction-list-domain\"")
        html should include("document-paragraph")
        html should include("document-list")
        html should include("document-example")
        html should include("document-note")
        html should include("logical-structure-projection")
        html should include("data-relation-id=\"root-domain-maps-to-application\"")
      }
    }

    "publish only the exact document command output and preserve in-memory projection bytes" in {
      _with_temp_dir("cozy-document-description-command") { root =>
        Given("direct Article 9 authorities and an existing direct output directory")
        val fixture = _fixture(root)
        val output = root.resolve("review.html")
        val expected = CozyDocumentDescriptionProjection.render(CozyDocumentDescription.loadDocument(fixture._1, fixture._2))

        When("the exact closed document render grammar is executed")
        val report = _execute(List("document-project", "description", "render", "--core", fixture._1.toString, "--document", fixture._2.toString, "--kind", "document", "--save", output.toString))

        Then("the atomically requested HTML equals the in-memory deterministic projection")
        Files.readString(output, StandardCharsets.UTF_8) shouldBe expected.html
        report should include("Cozy Document Description Render")
        report should include(expected.identity)
      }
    }

    "publish only the exact summary command output and preserve in-memory projection bytes" in {
      _with_temp_dir("cozy-document-description-summary-command") { root =>
        Given("direct Article 9 Core, Document, and Summary authorities with an existing direct output directory")
        val fixture = _summary_fixture(root)
        val output = root.resolve("summary-review.html")
        val validated = CozyDocumentDescription.loadSummary(fixture._1, fixture._2, fixture._3)
        val expected = CozyDocumentDescriptionProjection.renderSummary(validated)

        When("the exact closed summary render grammar is executed")
        val report = _execute(List("document-project", "description", "render", "--core", fixture._1.toString, "--document", fixture._2.toString, "--summary", fixture._3.toString, "--kind", "summary", "--save", output.toString))

        Then("the atomically requested HTML equals the in-memory deterministic Summary projection")
        Files.readString(output, StandardCharsets.UTF_8) shouldBe expected.html
        report should include("Cozy Summary Description Render")
        report should include(validated.summaryIdentity)
        report should include(expected.identity)
        report should include("application-modeling-document-ja")
      }
    }

    "reject missing, extra, duplicate, cross-kind, and malformed Summary command forms without an output" in {
      _with_temp_dir("cozy-document-description-command-rejection") { root =>
        Given("direct Article 9 authorities and invalid document or Summary command variants")
        val fixture = _summary_fixture(root)
        val missing = root.resolve("missing.html")
        val crosskind = root.resolve("cross-kind.html")
        val duplicate = root.resolve("duplicate.html")
        val malformed = root.resolve("malformed.html")
        val unsupported = root.resolve("unsupported.html")

        When("a required Summary option is missing, extra, duplicate, malformed, cross-kind, or unsupported")
        val failures = Vector(
          _failure(CozyDocumentDescriptionCommand.execute(List("document-project", "description", "render", "--core", fixture._1.toString, "--document", fixture._2.toString, "--kind", "summary", "--save", missing.toString))),
          _failure(CozyDocumentDescriptionCommand.execute(List("document-project", "description", "render", "--core", fixture._1.toString, "--document", fixture._2.toString, "--summary", fixture._3.toString, "--kind", "document", "--save", crosskind.toString))),
          _failure(CozyDocumentDescriptionCommand.execute(List("document-project", "description", "render", "--core", fixture._1.toString, "--document", fixture._2.toString, "--summary", fixture._3.toString, "--summary", fixture._3.toString, "--kind", "summary", "--save", duplicate.toString))),
          _failure(CozyDocumentDescriptionCommand.execute(List("document-project", "description", "render", "--core", fixture._1.toString, "--document", fixture._2.toString, "--summary", "--kind", "summary", "--save", malformed.toString))),
          _failure(CozyDocumentDescriptionCommand.execute(List("document-project", "description", "render", "--core", fixture._1.toString, "--document", fixture._2.toString, "--summary", fixture._3.toString, "--kind", "review", "--save", unsupported.toString)))
        )

        Then("the grammar rejects before publishing any output")
        failures.foreach(_.code shouldBe "DESCRIPTION_CLI")
        Files.exists(missing, LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(crosskind, LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(duplicate, LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(malformed, LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(unsupported, LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "reject stale Summary Core or Document bindings before a summary review output can be published" in {
      _with_temp_dir("cozy-document-description-summary-currentness") { root =>
        Given("copied Summary authorities with separately stale direct Core and Document bytes")
        val corefixture = _summary_fixture(Files.createDirectories(root.resolve("stale-core")))
        val documentfixture = _summary_fixture(Files.createDirectories(root.resolve("stale-document")))
        val coreidentity = "sha256:d7b8339d1a79790ad45d7e99bc9e7bf353322d7ffc2f05b44da2b8a98c826cbc"
        Files.writeString(corefixture._1, Files.readString(corefixture._1, StandardCharsets.UTF_8) + "\n", StandardCharsets.UTF_8)
        val currentcoreidentity = CozyDocumentLogicTree.loadCore(corefixture._1).coreIdentity
        Files.writeString(corefixture._2, Files.readString(corefixture._2, StandardCharsets.UTF_8).replace(coreidentity, currentcoreidentity), StandardCharsets.UTF_8)
        Files.writeString(documentfixture._2, Files.readString(documentfixture._2, StandardCharsets.UTF_8) + "\n", StandardCharsets.UTF_8)
        val coreoutput = root.resolve("stale-core.html")
        val documentoutput = root.resolve("stale-document.html")

        When("the closed Summary command is executed against either stale upstream binding")
        val failures = Vector(
          _failure(CozyDocumentDescriptionCommand.execute(List("document-project", "description", "render", "--core", corefixture._1.toString, "--document", corefixture._2.toString, "--summary", corefixture._3.toString, "--kind", "summary", "--save", coreoutput.toString))),
          _failure(CozyDocumentDescriptionCommand.execute(List("document-project", "description", "render", "--core", documentfixture._1.toString, "--document", documentfixture._2.toString, "--summary", documentfixture._3.toString, "--kind", "summary", "--save", documentoutput.toString)))
        )

        Then("currentness fails closed before either output destination is published")
        failures.map(_.code) shouldBe Vector("DESCRIPTION_SUMMARY_CORE", "DESCRIPTION_SUMMARY_DOCUMENT")
        Files.exists(coreoutput, LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(documentoutput, LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "reject missing options and unsafe output paths without publishing" in {
      _with_temp_dir("cozy-document-description-output-rejection") { root =>
        Given("direct Article 9 authorities and temporary paths for every closed output-safety case")
        val fixture = _fixture(root)
        val nonhtml = root.resolve("review.txt")
        val rootoutput = root.getRoot
        val symlinktarget = root.resolve("symlink-target.html")
        val symlinkdestination = root.resolve("symlink-destination.html")
        val targetbytes = "external-target"
        Files.writeString(symlinktarget, targetbytes, StandardCharsets.UTF_8)
        Files.createSymbolicLink(symlinkdestination, symlinktarget)
        val externalparent = Files.createDirectories(root.resolve("external-parent"))
        val symlinkparentoutput = externalparent.resolve("linked-output.html")
        val parenttargetbytes = "external-parent-target"
        Files.writeString(symlinkparentoutput, parenttargetbytes, StandardCharsets.UTF_8)
        val symlinkparent = root.resolve("symlink-parent")
        Files.createSymbolicLink(symlinkparent, externalparent)
        val symlinkparentdestination = symlinkparent.resolve("linked-output.html")
        val parentfile = root.resolve("parent-file")
        Files.writeString(parentfile, "not-a-directory", StandardCharsets.UTF_8)
        val nondirectorydestination = parentfile.resolve("child.html")
        val nonregulardestination = Files.createDirectories(root.resolve("directory.html"))

        When("the command is given missing options or each unsafe output destination")
        val clifailures = Vector(
          _failure(CozyDocumentDescriptionCommand.execute(List("document-project", "description", "render"))),
          _failure(CozyDocumentDescriptionCommand.execute(List("document-project", "description", "render", "--core", fixture._1.toString, "--document", fixture._2.toString, "--kind", "document")))
        )
        val pathfailures = Vector(
          _failure(CozyDocumentDescriptionCommand.execute(List("document-project", "description", "render", "--core", fixture._1.toString, "--document", fixture._2.toString, "--kind", "document", "--save", nonhtml.toString))),
          _failure(CozyDocumentDescriptionCommand.execute(List("document-project", "description", "render", "--core", fixture._1.toString, "--document", fixture._2.toString, "--kind", "document", "--save", rootoutput.toString))),
          _failure(CozyDocumentDescriptionCommand.execute(List("document-project", "description", "render", "--core", fixture._1.toString, "--document", fixture._2.toString, "--kind", "document", "--save", symlinkdestination.toString))),
          _failure(CozyDocumentDescriptionCommand.execute(List("document-project", "description", "render", "--core", fixture._1.toString, "--document", fixture._2.toString, "--kind", "document", "--save", symlinkparentdestination.toString))),
          _failure(CozyDocumentDescriptionCommand.execute(List("document-project", "description", "render", "--core", fixture._1.toString, "--document", fixture._2.toString, "--kind", "document", "--save", nondirectorydestination.toString))),
          _failure(CozyDocumentDescriptionCommand.execute(List("document-project", "description", "render", "--core", fixture._1.toString, "--document", fixture._2.toString, "--kind", "document", "--save", nonregulardestination.toString)))
        )

        Then("missing options fail as CLI faults and every unsafe output fails as a save-path fault")
        clifailures.foreach { failure =>
          failure.code shouldBe "DESCRIPTION_CLI"
          failure.path shouldBe "$.command"
        }
        pathfailures.foreach { failure =>
          failure.code shouldBe "DESCRIPTION_PATH"
          failure.path shouldBe "$.save"
        }
        Files.exists(nonhtml, LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.isDirectory(rootoutput, LinkOption.NOFOLLOW_LINKS) shouldBe true
        Files.isSymbolicLink(symlinkdestination) shouldBe true
        Files.readString(symlinktarget, StandardCharsets.UTF_8) shouldBe targetbytes
        Files.isSymbolicLink(symlinkparent) shouldBe true
        Files.readString(symlinkparentoutput, StandardCharsets.UTF_8) shouldBe parenttargetbytes
        Files.isRegularFile(parentfile, LinkOption.NOFOLLOW_LINKS) shouldBe true
        Files.exists(nondirectorydestination, LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.isDirectory(nonregulardestination, LinkOption.NOFOLLOW_LINKS) shouldBe true
      }
    }
  }

  private def _fixture(root: Path): (Path, Path) = {
    val content = Files.createDirectories(root.resolve("content"))
    val locale = Files.createDirectories(content.resolve("ja"))
    val core = content.resolve("core.yaml")
    val document = locale.resolve("document.yaml")
    Files.copy(_resource("/cozy/document/phase-58/application-modeling/content/core.yaml"), core)
    Files.copy(_resource("/cozy/document/phase-58/application-modeling/content/ja/document.yaml"), document)
    (core, document)
  }

  private def _summary_fixture(root: Path): (Path, Path, Path) = {
    val fixture = _fixture(root)
    val summary = fixture._2.getParent.resolve("summary.yaml")
    Files.copy(_resource("/cozy/document/phase-58/application-modeling/content/ja/summary.yaml"), summary)
    (fixture._1, fixture._2, summary)
  }

  private def _sections(values: Vector[CozyDocumentDescription.Section]): Vector[CozyDocumentDescription.Section] =
    values.flatMap(value => value +: _sections(value.sections))

  private def _blocks(values: Vector[CozyDocumentDescription.Section]): Vector[CozyDocumentDescription.Block] =
    values.flatMap(value => value.blocks ++ _blocks(value.sections))

  private def _resource(value: String): Path = Paths.get(getClass.getResource(value).toURI)

  private def _execute(args: List[String]): String = {
    val bytes = new ByteArrayOutputStream()
    Console.withOut(new PrintStream(bytes, true, "UTF-8")) {
      CozyDocumentDescriptionCommand.execute(args) shouldBe true
    }
    bytes.toString("UTF-8").trim
  }

  private def _failure(body: => Any): CozyDocumentDescription.DescriptionFault =
    intercept[CozyDocumentDescription.DescriptionFault](body)

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
