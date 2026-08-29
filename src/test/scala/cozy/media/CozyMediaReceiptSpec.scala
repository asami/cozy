package cozy.media

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.nio.file.attribute.FileTime
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import io.circe.parser.parse

/*
 * @since   Aug. 25, 2026
 * @version Aug. 29, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyMediaReceiptSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy media receipt v2" should {
    "decode only strict receipt descriptor configuration" in {
      _with_temp_dir("strict") { root =>
        Given("otherwise valid descriptors with malformed receipt input configuration")
        _write(root.resolve("knowledge/article.dox"), "knowledge")
        _write(root.resolve("source.txt"), "source")
        val invalids = Vector(
          _descriptor("\"receipt\": {\"unknown\": true}"),
          _descriptor("\"receipt\": {\"inputs\": [{\"id\": \"cozy:reserved\", \"role\": \"template\", \"path\": \"source.txt\", \"normalization\": \"bytes\"}]}"),
          _descriptor("\"receipt\": {\"inputs\": [{\"id\": \"absolute\", \"role\": \"template\", \"path\": \"/tmp/input\", \"normalization\": \"bytes\"}]}"),
          _descriptor("\"receipt\": {\"inputs\": [{\"id\": \"duplicate\", \"role\": \"template\", \"path\": \"source.txt\", \"normalization\": \"bytes\"}, {\"id\": \"duplicate\", \"role\": \"template\", \"path\": \"source.txt\", \"normalization\": \"bytes\"}]}")
        )

        When("each descriptor is resolved")
        val errors = invalids.map { value =>
          val descriptor = root.resolve(s"invalid-${value.hashCode}.json")
          _write(descriptor, value)
          intercept[Exception](CozyMedia.resolvePlan(CozyMedia.CommandConfig(descriptor)))
        }

        Then("unknown, reserved, absolute, and duplicate inputs are rejected")
        errors should have size invalids.size
        errors.map(_.getMessage).foreach(_ should not be empty)
      }
    }

    "write a v2 receipt without absolute paths and preserve the legacy manifest surface" in {
      _with_temp_dir("shape") { root =>
        Given("a copy resource with an explicit template and structured slide IR input")
        _write(root.resolve("knowledge/article.dox"), "knowledge")
        _write(root.resolve("source.txt"), "source")
        _write(root.resolve("template.pptx"), "template")
        _write(root.resolve("slides/ir.json"), "{\"z\": [2, 1], \"a\": {\"b\": true}}")
        val descriptor = root.resolve("media.json")
        _write(descriptor, _descriptor("\"receipt\": {\"inputs\": [{\"id\": \"template\", \"role\": \"template\", \"path\": \"template.pptx\", \"normalization\": \"bytes\"}, {\"id\": \"slide-ir\", \"role\": \"slide-ir\", \"path\": \"slides/ir.json\", \"normalization\": \"structured-document\"}], \"producer\": {\"profile\": \"article-media-v2\", \"renderer\": {\"name\": \"renderer\", \"version\": \"1\"}}}"))

        When("Cozy accepts the selected output")
        CozyMedia.build(CozyMedia.CommandConfig(descriptor))
        val manifest = Files.readString(root.resolve("target/cozy-media/manifest.json"), StandardCharsets.UTF_8)
        val json = parse(manifest).fold(error => throw error, identity)
        val resource = json.hcursor.downField("resources").downArray

        Then("legacy fields and complete receipt evidence coexist without machine paths")
        json.hcursor.get[String]("schema").toOption shouldBe Some("cozy.media.v1")
        json.hcursor.get[String]("knowledge").toOption shouldBe Some("development-process/example")
        resource.get[String]("id").toOption shouldBe Some("example")
        resource.downField("receipt").get[String]("schema").toOption shouldBe Some("cozy.media.receipt.v2")
        resource.downField("receipt").downField("verification").get[String]("status").toOption shouldBe Some("valid")
        manifest should not include root.toString
      }
    }

    "normalize structured documents semantically and use content rather than mtime for freshness" in {
      _with_temp_dir("identity") { root =>
        Given("an accepted package with a structured slide IR input")
        _write(root.resolve("knowledge/article.dox"), "knowledge")
        _write(root.resolve("source.txt"), "source")
        _write(root.resolve("slides/ir.json"), "{\"b\": 2, \"a\": 1}")
        val descriptor = root.resolve("media.json")
        _write(descriptor, _descriptor("\"receipt\": {\"inputs\": [{\"id\": \"slide-ir\", \"role\": \"slide-ir\", \"path\": \"slides/ir.json\", \"normalization\": \"structured-document\"}]}"))
        CozyMedia.build(CozyMedia.CommandConfig(descriptor))
        val initial = CozyMediaReceipt.capture(CozyMedia.resolvePlan(CozyMedia.CommandConfig(descriptor))).inputSetSha256

        When("only JSON formatting and object-key order change")
        _write(root.resolve("slides/ir.json"), "{ \"a\" : 1, \"b\" : 2 }")
        val reordered = CozyMediaReceipt.capture(CozyMedia.resolvePlan(CozyMedia.CommandConfig(descriptor))).inputSetSha256

        Then("the structured input identity is unchanged")
        reordered shouldBe initial

        When("a source byte changes but its mtime is restored")
        val source = root.resolve("source.txt")
        val mtime = Files.getLastModifiedTime(source)
        _write(source, "changed-source")
        Files.setLastModifiedTime(source, FileTime.fromMillis(mtime.toMillis))

        Then("planning rebuilds on the receipt content identity")
        CozyMedia.plan(CozyMedia.CommandConfig(descriptor)) should include("example: build")
      }
    }

    "retain the v2 receipt shape for an accepted article-PDF output" in {
      _with_temp_dir("article-pdf-shape") { root =>
        Given("a localized article PDF resource using the existing receipt acceptance route")
        _write(root.resolve("knowledge/article.dox"), "knowledge")
        _write(root.resolve("infographic/article-ja.svg"), "<svg>infographic</svg>")
        val descriptor = root.resolve("media.json")
        _write(descriptor, _article_pdf_descriptor)
        val runner = new CozyMedia.ProcessRunner {
          def run(command: Vector[String], workingdirectory: Path): Int = {
            _write(Path.of(command(command.indexOf("--output") + 1)), "%PDF-1.7\naccepted")
            0
          }
        }

        When("Cozy accepts the renderer output")
        CozyMedia.build(CozyMedia.CommandConfig(descriptor), runner)
        val resource = _manifest_resource_json(root.resolve("target/cozy-media/manifest.json"), "article-pdf-ja")

        Then("the existing manifest and receipt schemas remain unchanged while storing the PDF hash")
        resource.asObject.map(_.keys.toSet) shouldBe Some(Set("id", "path", "sha256", "receipt"))
        resource.hcursor.downField("receipt").get[String]("schema").toOption shouldBe Some("cozy.media.receipt.v2")

        When("the accepted output bytes change")
        _write(root.resolve("target/cozy-media/article-ja.pdf"), "%PDF-1.7\nchanged")

        Then("a changed accepted output is stale under the same receipt model")
        CozyMedia.plan(CozyMedia.CommandConfig(descriptor)) should include("article-pdf-ja: build")
      }
    }

    "preserve the full stale unselected prebuilt manifest entry during a target build" in {
      _with_temp_dir("target-unselected-prebuilt") { root =>
        Given("an accepted copy target beside an accepted prebuilt resource")
        _write(root.resolve("knowledge/article.dox"), "knowledge")
        _write(root.resolve("source.txt"), "source")
        _write(root.resolve("prebuilt.txt"), "prebuilt")
        val descriptor = root.resolve("media.json")
        _write(descriptor, _descriptor("", resources = ",{\"id\":\"prebuilt\",\"kind\":\"document\",\"source\":\"prebuilt.txt\",\"build\":\"prebuilt\"}"))
        CozyMedia.build(CozyMedia.CommandConfig(descriptor))
        val manifest = root.resolve("target/cozy-media/manifest.json")
        val before = _manifest_resource_json(manifest, "prebuilt")
        _write(root.resolve("knowledge/article.dox"), "knowledge-changed")
        _write(root.resolve("source.txt"), "source-rebuilt")

        When("only the copy target is rebuilt after the prebuilt receipt becomes stale")
        CozyMedia.build(CozyMedia.CommandConfig(descriptor, target = Some("example")))
        val after = _manifest_resource_json(manifest, "prebuilt")

        Then("the full unselected JSON entry is retained without reaccepting the stale prebuilt")
        after shouldBe before
      }
    }

    "report receiptless prebuilt resources as adopt-prebuilt without mutation" in {
      _with_temp_dir("receiptless-prebuilt-plan") { root =>
        Given("a prebuilt source without a manifest receipt")
        _write(root.resolve("knowledge/article.dox"), "knowledge")
        _write(root.resolve("prebuilt.txt"), "prebuilt")
        val descriptor = root.resolve("prebuilt.json")
        _write(descriptor, _prebuilt_descriptor)

        When("the prebuilt resource is planned")
        val plan = CozyMedia.plan(CozyMedia.CommandConfig(descriptor))

        Then("the plan asks for explicit adoption and writes no manifest")
        plan should include("prebuilt: adopt-prebuilt")
        Files.exists(root.resolve("target/cozy-media/manifest.json")) shouldBe false
      }
    }

    "report externally refreshed prebuilt resources as adopt-prebuilt" in {
      _with_temp_dir("refreshed-prebuilt-plan") { root =>
        Given("an accepted prebuilt resource whose input and output are externally refreshed")
        _write(root.resolve("knowledge/article.dox"), "knowledge")
        _write(root.resolve("prebuilt.txt"), "prebuilt")
        val descriptor = root.resolve("prebuilt.json")
        _write(descriptor, _prebuilt_descriptor)
        CozyMedia.build(CozyMedia.CommandConfig(descriptor))
        _write(root.resolve("knowledge/article.dox"), "knowledge-changed")
        _write(root.resolve("prebuilt.txt"), "externally-refreshed-prebuilt")

        When("the refreshed prebuilt resource is planned")
        val plan = CozyMedia.plan(CozyMedia.CommandConfig(descriptor))

        Then("the plan reports the honest explicit-adoption action")
        plan should include("prebuilt: adopt-prebuilt")
      }
    }

    "replace manifest resources no longer declared by a full build" in {
      _with_temp_dir("full-build-replacement") { root =>
        Given("a manifest accepted from a descriptor with two copy resources")
        _write(root.resolve("knowledge/article.dox"), "knowledge")
        _write(root.resolve("source.txt"), "source")
        _write(root.resolve("source-b.txt"), "source-b")
        val descriptor = root.resolve("media.json")
        _write(descriptor, _descriptor("", resources = ",{\"id\":\"other\",\"kind\":\"document\",\"source\":\"source-b.txt\",\"output\":\"target/cozy-media/other.txt\",\"build\":\"copy\"}"))
        CozyMedia.build(CozyMedia.CommandConfig(descriptor))
        val prebuilt = root.resolve("prebuilt.json")
        _write(root.resolve("prebuilt.txt"), "prebuilt")
        _write(prebuilt, _prebuilt_descriptor)

        When("a full build uses a descriptor declaring only a prebuilt resource")
        CozyMedia.build(CozyMedia.CommandConfig(prebuilt))
        val resources = _manifest_resource_ids(root.resolve("target/cozy-media/manifest.json"))

        Then("the manifest drops resources no longer declared")
        resources shouldBe Vector("prebuilt")
      }
    }

    "reject a stale same-output prebuilt only when it is explicitly built" in {
      _with_temp_dir("stale-prebuilt-build") { root =>
        Given("an accepted prebuilt resource whose input changes while its output does not")
        _write(root.resolve("knowledge/article.dox"), "knowledge")
        _write(root.resolve("prebuilt.txt"), "prebuilt")
        val descriptor = root.resolve("prebuilt.json")
        _write(descriptor, _prebuilt_descriptor)
        CozyMedia.build(CozyMedia.CommandConfig(descriptor))
        _write(root.resolve("knowledge/article.dox"), "knowledge-changed")

        When("the stale prebuilt resource is explicitly built")
        val stale = intercept[RuntimeException](CozyMedia.build(CozyMedia.CommandConfig(descriptor)))

        Then("Cozy refuses to bless the unchanged external output")
        stale.getMessage should include("stale")
      }
    }

    "reject prepared publication input changes before destination writes" in {
      _with_temp_dir("prepared-input-race") { root =>
        Given("a prepared publication with a captured current input identity")
        _write(root.resolve("knowledge/article.dox"), "knowledge")
        _write(root.resolve("source.txt"), "source")
        val descriptor = root.resolve("media.json")
        _write(descriptor, _descriptor(""))
        CozyMedia.build(CozyMedia.CommandConfig(descriptor))
        Files.createDirectories(root.resolve("publication"))
        val prepared = CozyMedia.preparePublication(CozyMedia.CommandConfig(descriptor, profile = Some("site")), force = true)
        _write(root.resolve("knowledge/article.dox"), "knowledge-race")

        When("the stale preparation is committed")
        val raced = intercept[RuntimeException](CozyMedia.commitPublication(prepared))

        Then("the input rejection occurs with no destination write")
        raced.getMessage should include("inputs have changed")
        Files.exists(root.resolve("publication/example.txt")) shouldBe false
      }
    }
  }

  private def _manifest_resource_json(manifest: Path, id: String): io.circe.Json = {
    val resources = parse(Files.readString(manifest, StandardCharsets.UTF_8)).fold(error => throw error, identity).hcursor
      .downField("resources").focus.flatMap(_.asArray).getOrElse(throw new IllegalArgumentException("Manifest resources are required"))
    resources.find(_.hcursor.get[String]("id").toOption.contains(id)).getOrElse(
      throw new IllegalArgumentException(s"Manifest resource is required: $id")
    )
  }

  private def _manifest_resource_ids(manifest: Path): Vector[String] =
    parse(Files.readString(manifest, StandardCharsets.UTF_8)).fold(error => throw error, identity).hcursor
      .downField("resources").focus.flatMap(_.asArray).getOrElse(throw new IllegalArgumentException("Manifest resources are required"))
      .toVector.map(_.hcursor.get[String]("id").fold(error => throw error, identity))

  private def _descriptor(receipt: String, resources: String = ""): String =
    s"""{
       |  "schema": "cozy.media.v1",
       |  "knowledge": {"id": "development-process/example", "source": "knowledge/article.dox"},
       |  "profiles": {"site": {"root": "publication"}},
       |  "resources": [
       |    {"id": "example", "kind": "document", "source": "source.txt", "output": "target/cozy-media/example.txt", "build": "copy", "publications": {"site": "example.txt"}}$resources
       |  ]${if (receipt.nonEmpty) "," + receipt else ""}
       |}
       |""".stripMargin

  private def _prebuilt_descriptor: String =
    """{
      |  "schema": "cozy.media.v1",
      |  "knowledge": {"id": "development-process/example", "source": "knowledge/article.dox"},
      |  "resources": [
      |    {"id": "prebuilt", "kind": "document", "source": "prebuilt.txt", "build": "prebuilt"}
      |  ]
      |}
       |""".stripMargin

  private def _article_pdf_descriptor: String =
    """{
      |  "schema": "cozy.media.v1",
      |  "knowledge": {"id": "development-process/example", "source": "knowledge/article.dox"},
      |  "resources": [{
      |    "id": "article-pdf-ja", "kind": "document", "language": "ja",
      |    "source": "knowledge/article.dox", "output": "target/cozy-media/article-ja.pdf", "build": "article-pdf",
      |    "articleMedia": {"role": "article_pdf", "publicPath": "/articles/example/article-ja.pdf", "mediaType": "application/pdf"},
      |    "articlePdf": {"latexFormat": "business", "infographic": "infographic-ja", "renderer": {"name": "smartdox-pdf", "version": "2.4.18-SNAPSHOT", "command": ["smartdox"]}}
      |  }, {
      |    "id": "infographic-ja", "kind": "infographic", "language": "ja",
      |    "source": "infographic/article-ja.svg", "build": "prebuilt",
      |    "articleMedia": {"role": "infographic", "publicPath": "/articles/example/infographic-ja.svg"}
      |  }]
      |}
      |""".stripMargin

  private def _with_temp_dir[A](name: String)(f: Path => A): A = {
    val work = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve("target/cozy-media-receipt-spec")
    Files.createDirectories(work)
    val root = Files.createTempDirectory(work, name + "-")
    try f(root)
    finally _delete(root)
  }

  private def _write(path: Path, value: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, value.getBytes(StandardCharsets.UTF_8))
  }

  private def _delete(path: Path): Unit = {
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try stream.iterator().asScala.toVector.sortBy(_.toString.length).reverse.foreach(Files.deleteIfExists(_))
      finally stream.close()
    }
  }
}
