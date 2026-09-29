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
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyDocumentSourceAdmissionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy Document source admission" should {
    "E-SRC-001 admit the direct hash-free Core Document and Summary grammar in source order" in {
      _with_temp_dir("cozy-source-admission-valid") { root =>
        Given("a complete localized source fixture under one direct content root")
        val fixture = _fixture(root)

        When("the source-only Summary API admits it")
        val admitted = CozyDocumentDescriptionV2.loadSourceSummary(fixture.core, fixture.document, fixture.summary)

        Then("semantic IDs, nested content, diagrams, omissions, and source order are retained without identities")
        admitted.coreid shouldBe "application-modeling"
        admitted.documentid shouldBe "application-modeling-document-ja-v2"
        admitted.id shouldBe "application-modeling-summary-ja-v2"
        admitted.locale shouldBe "ja"
        admitted.document.document.sections.map(_.id) shouldBe Vector("application-modeling-introduction")
        admitted.document.document.sections.head.sections.map(_.id) shouldBe Vector("use-case-realization-section", "conclusion-section")
        admitted.summary.units.map(_.id) shouldBe Vector(
          "application-overview",
          "foundation-purpose",
          "use-case-model",
          "collaboration-interaction",
          "executable-elements",
          "review-and-realization"
        )
        admitted.summary.units.head.overview shouldBe Some(CozyDocumentDescriptionV2.Overview("application-modeling"))
        admitted.summary.units.head.diagram.get.items.head shouldBe CozyDocumentDescriptionV2.DiagramItem(
          "overview-domain-model",
          "node",
          "root-domain-model"
        )
        admitted.summary.units.head.diagram.get.edges.head.ref shouldBe "root-domain-maps-to-application"
        admitted.summary.units.head.omissions.head.documentRef shouldBe "application-modeling-introduction"
        admitted.document.targets.sectionIds should contain("use-case-realization-section")
        admitted.document.labels.steps.map(_.stepRef) should contain allOf (
          "application-modeling",
          "application-foundation",
          "use-case-realization",
          "collaboration-and-interaction",
          "executable-elements",
          "application-conclusion"
        )
      }
    }

    "E-SRC-002 ignore absent and ordinary historical identity values" in {
      _with_temp_dir("cozy-source-admission-identity") { root =>
        Given("the same semantic sources with omitted and arbitrary historical identity values")
        val fixture = _fixture(root)
        val document = Files.readString(fixture.document, StandardCharsets.UTF_8)
        val summary = Files.readString(fixture.summary, StandardCharsets.UTF_8)
        val baselinecore = _copy_core(fixture.core, root.resolve("baseline/content/core.yaml"))
        val baselinedocument = _write(root.resolve("baseline/content/ja/document.yaml"), _without_identity(document))
        val baselinesummary = _write(root.resolve("baseline/content/ja/summary.yaml"), _without_identity(summary))
        val baseline = CozyDocumentDescriptionV2.loadSourceSummary(baselinecore, baselinedocument, baselinesummary)
        val variants = Vector("", "null", "[historic, identity]", "{ stale: true }", "true", "malformed identity")

        When("each direct source is admitted without using its historical value")
        val admitted = variants.zipWithIndex.map { case (value, index) =>
          val directory = root.resolve(s"identity-$index/content/ja")
          val core = _copy_core(fixture.core, root.resolve(s"identity-$index/content/core.yaml"))
          val source = if (value.isEmpty) _without_identity(document) else _with_identity(document, value)
          val documentpath = _write(directory.resolve("document.yaml"), source)
          val summarypath = _write(directory.resolve("summary.yaml"), if (value.isEmpty) _without_identity(summary) else _with_identity(summary, value))
          CozyDocumentDescriptionV2.loadSourceSummary(core, documentpath, summarypath)
        }

        Then("every complete result equals the absent-identity semantic model")
        admitted.foreach(_ shouldBe baseline)
        admitted.map(value => (value.coreid, value.documentid, value.id, value.locale, value.summary)) shouldBe
          Vector.fill(variants.size)((baseline.coreid, baseline.documentid, baseline.id, baseline.locale, baseline.summary))
      }
    }

    "E-SRC-003 reject wrong bindings locales unknown keys and cross-content paths" in {
      _with_temp_dir("cozy-source-admission-binding") { root =>
        Given("independent valid fixtures and exact invalid source-selection variants")
        val fixture = _fixture(root.resolve("base"))
        val document = Files.readString(fixture.document, StandardCharsets.UTF_8)
        val summary = Files.readString(fixture.summary, StandardCharsets.UTF_8)
        val wrongfixture = _fixture(root.resolve("wrong"))
        val missingfixture = _fixture(root.resolve("missing"))
        val unknownfixture = _fixture(root.resolve("unknown"))
        val localefixture = _fixture(root.resolve("locale"))
        val summarycorefixture = _fixture(root.resolve("summary-core"))
        val summarydocumentfixture = _fixture(root.resolve("summary-document"))
        val summarymissingfixture = _fixture(root.resolve("summary-missing"))
        val summaryunknownfixture = _fixture(root.resolve("summary-unknown"))
        val summarylocalefixture = _fixture(root.resolve("summary-locale"))
        val summarypathfixture = _fixture(root.resolve("summary-path"))
        val wrongid = _write(wrongfixture.document, _replace_once(document, "  id: application-modeling\n", "  id: wrong-core\n"))
        val missingid = _write(missingfixture.document, _replace_once(document, "  id: application-modeling\n", ""))
        val unknown = _write(unknownfixture.document, document + "unknown: forbidden\n")
        val wronglocale = _write(root.resolve("locale/content/en/document.yaml"), document)
        val crossed = _write(root.resolve("other/ja/document.yaml"), document)
        val wrongsummarycore = _write(summarycorefixture.summary, _replace_once(summary, "core:\n  id: application-modeling", "core:\n  id: wrong-summary-core"))
        val wrongsummarydocument = _write(summarydocumentfixture.summary, _replace_once(summary, "document:\n  id: application-modeling-document-ja-v2", "document:\n  id: wrong-summary-document"))
        val missingsummarydocument = _write(summarymissingfixture.summary, _replace_once(summary, "document:\n  id: application-modeling-document-ja-v2\n", "document:\n"))
        val unknownsummarybinding = _write(summaryunknownfixture.summary, _replace_once(summary, "document:\n  id: application-modeling-document-ja-v2\n", "document:\n  id: application-modeling-document-ja-v2\n  unknown: forbidden\n"))
        val wrongsummarylocale = _write(summarylocalefixture.summary, _replace_once(summary, "locale: ja", "locale: zz"))
        val differentlocaleparent = _write(root.resolve("summary-path/content/en/summary.yaml"), summary)

        When("the source API resolves each invalid binding or selection")
        val failures = Vector(
          _failure(CozyDocumentDescriptionV2.loadSourceDocument(wrongfixture.core, wrongid)),
          _failure(CozyDocumentDescriptionV2.loadSourceDocument(missingfixture.core, missingid)),
          _failure(CozyDocumentDescriptionV2.loadSourceDocument(unknownfixture.core, unknown)),
          _failure(CozyDocumentDescriptionV2.loadSourceDocument(localefixture.core, wronglocale)),
          _failure(CozyDocumentDescriptionV2.loadSourceDocument(fixture.core, crossed)),
          _failure(CozyDocumentDescriptionV2.loadSourceSummary(summarycorefixture.core, summarycorefixture.document, wrongsummarycore)),
          _failure(CozyDocumentDescriptionV2.loadSourceSummary(summarydocumentfixture.core, summarydocumentfixture.document, wrongsummarydocument)),
          _failure(CozyDocumentDescriptionV2.loadSourceSummary(summarymissingfixture.core, summarymissingfixture.document, missingsummarydocument)),
          _failure(CozyDocumentDescriptionV2.loadSourceSummary(summaryunknownfixture.core, summaryunknownfixture.document, unknownsummarybinding)),
          _failure(CozyDocumentDescriptionV2.loadSourceSummary(summarylocalefixture.core, summarylocalefixture.document, wrongsummarylocale)),
          _failure(CozyDocumentDescriptionV2.loadSourceSummary(summarypathfixture.core, summarypathfixture.document, differentlocaleparent))
        )

        Then("the closed schema, binding, locale, and content-root boundaries fail before any output exists")
        failures shouldBe Vector(
          "DESCRIPTION_V2_DOCUMENT_CORE",
          "DESCRIPTION_V2_FIELDS",
          "DESCRIPTION_V2_FIELDS",
          "DESCRIPTION_V2_LOCALE",
          "DESCRIPTION_V2_PATH",
          "DESCRIPTION_V2_SUMMARY_CORE",
          "DESCRIPTION_V2_SUMMARY_DOCUMENT",
          "DESCRIPTION_V2_FIELDS",
          "DESCRIPTION_V2_FIELDS",
          "DESCRIPTION_V2_LOCALE",
          "DESCRIPTION_V2_PATH"
        )
      }
    }

    "E-SRC-004 preserve shared tree content coverage and diagram validators" in {
      _with_temp_dir("cozy-source-admission-semantic") { root =>
        Given("independent valid sources with one precise Core, Document, or Summary semantic defect each")
        val fixture = _fixture(root.resolve("base"))
        val coretext = Files.readString(fixture.core, StandardCharsets.UTF_8)
        val documenttext = Files.readString(fixture.document, StandardCharsets.UTF_8)
        val summarytext = Files.readString(fixture.summary, StandardCharsets.UTF_8)
        val schemabrokenfixture = _fixture(root.resolve("schema"))
        val referencefixture = _fixture(root.resolve("reference"))
        val coveragefixture = _fixture(root.resolve("coverage"))
        val labelfixture = _fixture(root.resolve("labels"))
        val localefixture = _fixture(root.resolve("summary-locale"))
        val diagramreferencefixture = _fixture(root.resolve("diagram-reference"))
        val endpointfixture = _fixture(root.resolve("diagram-endpoint"))
        val groundingfixture = _fixture(root.resolve("diagram-grounding"))
        val omissionfixture = _fixture(root.resolve("omission"))
        val invalidcore = _write(schemabrokenfixture.core, _replace_once(coretext, "schema: cozy.content-core.logic-tree.v1", "schema: invalid"))
        val invalidreference = _write(
          referencefixture.document,
          _replace_once(
            documenttext,
            "steps: [application-modeling, application-foundation, use-case-realization, collaboration-and-interaction, executable-elements, application-conclusion]",
            "steps: [missing-step, application-foundation, use-case-realization, collaboration-and-interaction, executable-elements, application-conclusion]"
          )
        )
        val invalidcoverage = _write(coveragefixture.document, _replace_all_nonempty_claims(documenttext))
        val invalidlabels = _write(
          labelfixture.document,
          _replace_once(documenttext, "    - stepRef: application-modeling\n      text: アプリケーションモデリング\n", "")
        )
        val invalidsummarylocale = _write(localefixture.summary, _replace_once(summarytext, "locale: ja", "locale: zz"))
        val invaliddiagramreference = _write(diagramreferencefixture.summary, _replace_once(summarytext, "            ref: root-domain-model", "            ref: missing-source"))
        val invalidendpoint = _write(endpointfixture.summary, _replace_once(summarytext, "            ref: root-domain-model", "            ref: foundation-static-view"))
        val invalidgrounding = _write(groundingfixture.summary, _replace_once(summarytext, "        relations: [root-domain-maps-to-application]", "        relations: []"))
        val invalidomission = _write(omissionfixture.summary, _replace_once(summarytext, "documentRef: application-modeling-introduction", "documentRef: missing-target"))

        When("shared validators admit each malformed Core, Document, or Summary source")
        val failures = Vector(
          _failure(CozyDocumentDescriptionV2.loadSourceDocument(schemabrokenfixture.core, schemabrokenfixture.document)),
          _failure(CozyDocumentDescriptionV2.loadSourceDocument(referencefixture.core, invalidreference)),
          _failure(CozyDocumentDescriptionV2.loadSourceDocument(coveragefixture.core, invalidcoverage)),
          _failure(CozyDocumentDescriptionV2.loadSourceDocument(labelfixture.core, invalidlabels)),
          _failure(CozyDocumentDescriptionV2.loadSourceSummary(localefixture.core, localefixture.document, invalidsummarylocale)),
          _failure(CozyDocumentDescriptionV2.loadSourceSummary(diagramreferencefixture.core, diagramreferencefixture.document, invaliddiagramreference)),
          _failure(CozyDocumentDescriptionV2.loadSourceSummary(endpointfixture.core, endpointfixture.document, invalidendpoint)),
          _failure(CozyDocumentDescriptionV2.loadSourceSummary(groundingfixture.core, groundingfixture.document, invalidgrounding)),
          _failure(CozyDocumentDescriptionV2.loadSourceSummary(omissionfixture.core, omissionfixture.document, invalidomission))
        )

        Then("typed Core, reference, coverage, label, locale, diagram, grounding, and omission faults remain active")
        failures shouldBe Vector(
          "LOGIC_TREE_SCHEMA",
          "DESCRIPTION_V2_REFERENCE",
          "DESCRIPTION_V2_COVERAGE",
          "DESCRIPTION_V2_LABEL_COVERAGE",
          "DESCRIPTION_V2_LOCALE",
          "DESCRIPTION_V2_DIAGRAM_REFERENCE",
          "DESCRIPTION_V2_DIAGRAM_ENDPOINT",
          "DESCRIPTION_V2_DIAGRAM_GROUNDING",
          "DESCRIPTION_V2_OMISSION_REFERENCE"
        )
      }
    }

    "E-SRC-005 reject unsafe or lossy source forms without changing existing bytes" in {
      _with_temp_dir("cozy-source-admission-safe") { root =>
        Given("a valid source, a prior-product sentinel, and independent malformed duplicate anchor tag UTF-8 and symlink variants")
        val fixture = _fixture(root.resolve("base"))
        val sentinel = _write(root.resolve("prior-product.bin"), "prior product bytes\n")
        val original = Vector(fixture.core, fixture.document, fixture.summary, sentinel).map(path => path -> (Files.readAllBytes(path).toVector, Files.getLastModifiedTime(path)))
        val document = Files.readString(fixture.document, StandardCharsets.UTF_8)
        val duplicatefixture = _fixture(root.resolve("duplicate"))
        val anchorfixture = _fixture(root.resolve("anchor"))
        val tagfixture = _fixture(root.resolve("tag"))
        val lossyfixture = _fixture(root.resolve("lossy"))
        val linkfixture = _fixture(root.resolve("link"))
        val missingdocument = fixture.document.resolveSibling("missing-document.yaml")
        val missingsummary = fixture.summary.resolveSibling("missing-summary.yaml")
        val missingcore = fixture.core.resolveSibling("missing-core.yaml")
        val duplicate = _write(duplicatefixture.document, document + "id: duplicate\n")
        val anchor = _write(anchorfixture.document, _replace_once(document, "id: application-modeling-document-ja-v2", "id: &identity application-modeling-document-ja-v2"))
        val tag = _write(tagfixture.document, _replace_once(document, "id: application-modeling-document-ja-v2", "id: !!str application-modeling-document-ja-v2"))
        val lossy = lossyfixture.document
        Files.write(lossy, Array[Byte](0xC3.toByte, 0x28.toByte))
        Files.delete(linkfixture.document)
        val link = Files.createSymbolicLink(linkfixture.document, fixture.document)

        When("each unsafe direct source or missing source is admitted")
        val failures = Vector(
          _failure(CozyDocumentDescriptionV2.loadSourceDocument(duplicatefixture.core, duplicate)),
          _failure(CozyDocumentDescriptionV2.loadSourceDocument(anchorfixture.core, anchor)),
          _failure(CozyDocumentDescriptionV2.loadSourceDocument(tagfixture.core, tag)),
          _failure(CozyDocumentDescriptionV2.loadSourceDocument(lossyfixture.core, lossy)),
          _failure(CozyDocumentDescriptionV2.loadSourceDocument(linkfixture.core, link)),
          _failure(CozyDocumentDescriptionV2.loadSourceDocument(missingcore, fixture.document)),
          _failure(CozyDocumentDescriptionV2.loadSourceDocument(fixture.core, missingdocument)),
          _failure(CozyDocumentDescriptionV2.loadSourceSummary(fixture.core, fixture.document, missingsummary))
        )

        Then("source faults are precise and every original source and prior-product byte remains unchanged")
        failures shouldBe Vector(
          "DESCRIPTION_V2_SOURCE",
          "DESCRIPTION_V2_SOURCE",
          "DESCRIPTION_V2_SOURCE",
          "DESCRIPTION_V2_SOURCE",
          "DESCRIPTION_V2_PATH",
          "DESCRIPTION_V2_PATH",
          "DESCRIPTION_V2_PATH",
          "DESCRIPTION_V2_PATH"
        )
        original.foreach { case (path, (bytes, modified)) =>
          Files.readAllBytes(path).toVector shouldBe bytes
          Files.getLastModifiedTime(path) shouldBe modified
        }
      }
    }

    "E-SRC-006 expose only the strict read-only public CLI" in {
      _with_temp_dir("cozy-source-admission-cli") { root =>
        Given("one valid source request, a prior output sentinel, and malformed option sets")
        val fixture = _fixture(root)
        val request = List("document-project", "sources", "validate", "--core", fixture.core.toString, "--document", fixture.document.toString, "--summary", fixture.summary.toString)
        val prioroutput = _write(root.resolve("prior-output.html"), "prior output\n")
        val original = Vector(fixture.core, fixture.document, fixture.summary, prioroutput).map(path => path -> (Files.readAllBytes(path).toVector, Files.getLastModifiedTime(path)))

        When("the command is invoked through its source route and malformed option sets")
        val output = _stdout(CozyDocumentDescriptionCommand.execute(request))
        val failures = Vector(
          _failure(CozyDocumentDescriptionCommand.execute(request.dropRight(2))),
          _failure(CozyDocumentDescriptionCommand.execute(request ++ List("--save", root.resolve("new.html").toString))),
          _failure(CozyDocumentDescriptionCommand.execute(request ++ List("--core", fixture.core.toString))),
          _failure(CozyDocumentDescriptionCommand.execute(request ++ List("--core"))),
          _failure(CozyDocumentDescriptionCommand.execute(request ++ List("unexpected")))
        )
        val legacyfailure = _legacy_failure(CozyDocumentDescriptionCommand.execute(List("document-project", "description", "render")))

        Then("success prints semantic IDs without hash or identity text and invalid input never writes")
        output should include("Cozy Document Sources Validated")
        output should include("core: application-modeling")
        output should include("document: application-modeling-document-ja-v2")
        output should include("summary: application-modeling-summary-ja-v2")
        output.toLowerCase should not include "sha256"
        output.toLowerCase should not include "identity"
        failures shouldBe Vector.fill(5)("DESCRIPTION_V2_CLI")
        legacyfailure shouldBe "DESCRIPTION_CLI"
        original.foreach { case (path, (bytes, modified)) =>
          Files.readAllBytes(path).toVector shouldBe bytes
          Files.getLastModifiedTime(path) shouldBe modified
        }
        CozyDocumentDescriptionCommand.execute(List("document-project", "sources", "render")) shouldBe false
      }
    }

    "E-SRC-007 preserve stable source semantics for generated nonempty historical values" in {
      Given("bounded nonempty alphanumeric historical identity values")
      val values = Gen.choose(1, 12).flatMap(length => Gen.listOfN(length, Gen.alphaNumChar).map(_.mkString))

      When("ScalaCheck supplies each bounded alphanumeric historical identity payload")
      val check = Test.check(Test.Parameters.default.withMinSuccessfulTests(30), Prop.forAll(values) { value =>
        _with_temp_dir("cozy-source-admission-property") { root =>
          Given("direct Document and Summary sources below the same admitted Core root")
          val fixture = _fixture(root)
          val document = Files.readString(fixture.document, StandardCharsets.UTF_8)
          val summary = Files.readString(fixture.summary, StandardCharsets.UTF_8)
          val baseline = CozyDocumentDescriptionV2.loadSourceSummary(fixture.core, fixture.document, fixture.summary)
          _write(fixture.document, _with_identity(document, value))
          _write(fixture.summary, _with_identity(summary, value))

          When("the direct source route admits the generated historical values")
          val admitted = CozyDocumentDescriptionV2.loadSourceSummary(fixture.core, fixture.document, fixture.summary)

          Then("the complete semantic model remains deterministic and retains no historical payload")
          admitted shouldBe baseline
          admitted.document.id shouldBe "application-modeling-document-ja-v2"
          admitted.summary.title should not be empty
        }
        true
      })

      Then("generated checks pass deterministically")
      check.passed shouldBe true
      check.succeeded should be >= 30
    }
  }

  private final case class Fixture(core: Path, document: Path, summary: Path)

  private def _fixture(root: Path): Fixture = {
    val core = _copy_core(_resource("/cozy/document/phase-59/application-modeling/content-v2/core.yaml"), root.resolve("content/core.yaml"))
    val document = _copy(_resource("/cozy/document/phase-59/application-modeling/content-v2/ja/document.yaml"), root.resolve("content/ja/document.yaml"))
    val summary = _copy(_resource("/cozy/document/phase-59/application-modeling/content-v2/ja/summary.yaml"), root.resolve("content/ja/summary.yaml"))
    Fixture(core, document, summary)
  }

  private def _copy_core(source: Path, destination: Path): Path = _copy(source, destination)

  private def _copy(source: Path, destination: Path): Path = {
    Files.createDirectories(destination.getParent)
    Files.copy(source, destination)
    destination
  }

  private def _write(path: Path, value: String): Path = {
    Files.createDirectories(path.getParent)
    Files.writeString(path, value, StandardCharsets.UTF_8)
    path
  }

  private def _resource(value: String): Path = Paths.get(getClass.getResource(value).toURI)

  private def _with_identity(value: String, identity: String): String = value.replaceAll("(?m)^  identity: .*", s"  identity: $identity")

  private def _without_identity(value: String): String = value.replaceAll("(?m)^  identity: .*\\n", "")

  private def _replace_once(value: String, from: String, to: String): String = {
    val index = value.indexOf(from)
    if (index < 0) fail(s"fixture token not found: $from")
    val result = value.substring(0, index) + to + value.substring(index + from.length)
    result should not be value
    result
  }

  private def _replace_all_nonempty_claims(value: String): String = {
    val result = value.replaceAll("(?m)^(\\s+claims): \\[([^\\]]+)\\]$", "$1: []")
    result should not be value
    result
  }

  private def _failure(body: => Any): String =
    intercept[IllegalArgumentException](body) match {
      case fault: CozyDocumentDescriptionV2.DescriptionV2Fault => fault.code
      case fault: CozyDocumentLogicTree.LogicTreeFault => fault.code
      case fault => fail(s"unexpected fault subtype: ${fault.getClass.getName}")
    }

  private def _legacy_failure(body: => Any): String =
    intercept[CozyDocumentDescription.DescriptionFault](body).code

  private def _stdout(body: => Boolean): String = {
    val bytes = new ByteArrayOutputStream()
    Console.withOut(new PrintStream(bytes)) { body shouldBe true }
    bytes.toString("UTF-8")
  }

  private def _with_temp_dir(name: String)(body: Path => Unit): Unit = {
    val root = Files.createTempDirectory(name)
    try body(root)
    finally _delete(root)
  }

  private def _delete(path: Path): Unit = if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
    val stream = Files.walk(path)
    try stream.iterator().asScala.toVector.sortBy(_.toString.length).reverse.foreach(value => try Files.deleteIfExists(value) catch { case NonFatal(_) => () })
    finally stream.close()
  }
}
