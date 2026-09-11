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
        validated.coreIdentity shouldBe "sha256:c2ea5ceccc33ce9db09a28eaa0064dd329023ed262694a7b14ff03b530989eb4"
        _sections(validated.description.document.sections).map(_.id) should contain allOf ("application-modeling-introduction", "conclusion-section")
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
        first.html should include("data-core-identity=\"sha256:c2ea5ceccc33ce9db09a28eaa0064dd329023ed262694a7b14ff03b530989eb4\"")
        first.html should include("data-core-relations=\"root-domain-to-application")
        first.html should not include("<table")
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
        html should include("data-relation-id=\"root-domain-to-application\"")
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

    "reject unknown, duplicate, and unsupported command forms without an output" in {
      _with_temp_dir("cozy-document-description-command-rejection") { root =>
        Given("direct Article 9 authorities and invalid command variants")
        val fixture = _fixture(root)
        val unknown = root.resolve("unknown.html")
        val duplicate = root.resolve("duplicate.html")
        val kind = root.resolve("kind.html")

        When("an unknown option, duplicate option, or summary kind is requested")
        val failures = Vector(
          _failure(CozyDocumentDescriptionCommand.execute(List("document-project", "description", "render", "--core", fixture._1.toString, "--document", fixture._2.toString, "--kind", "document", "--save", unknown.toString, "--summary", "summary.yaml"))),
          _failure(CozyDocumentDescriptionCommand.execute(List("document-project", "description", "render", "--core", fixture._1.toString, "--core", fixture._1.toString, "--document", fixture._2.toString, "--kind", "document", "--save", duplicate.toString))),
          _failure(CozyDocumentDescriptionCommand.execute(List("document-project", "description", "render", "--core", fixture._1.toString, "--document", fixture._2.toString, "--kind", "summary", "--save", kind.toString)))
        )

        Then("the grammar rejects before publishing any output")
        failures.foreach(_.code shouldBe "DESCRIPTION_CLI")
        Files.exists(unknown, LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(duplicate, LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(kind, LinkOption.NOFOLLOW_LINKS) shouldBe false
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
