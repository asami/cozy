package cozy.media

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import io.circe.parser.parse

/*
 * @since   Aug. 25, 2026
 *  version Aug. 29, 2026
 *  version Sep. 22, 2026
 * @version Oct.  1, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyMediaReceiptSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy media receipt v2" should {
    "normalization, persistence, and freshness decisions" which {
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
        resource.downField("receipt").downField("inputSetSha256").focus shouldBe None
        val evidence = resource.downField("receipt").downField("inputs").focus.flatMap(_.asArray).get
        evidence.forall(_.hcursor.downField("sha256").focus.isEmpty) shouldBe true
        manifest should not include root.toString
      }
    }

    "ignore historical input hash properties without weakening output integrity" in {
      _with_temp_dir("historical-input-hashes") { root =>
        Given("an accepted copy resource carrying historical input hash properties")
        _write(root.resolve("knowledge/article.dox"), "knowledge")
        _write(root.resolve("source.txt"), "source")
        val descriptor = root.resolve("media.json")
        _write(descriptor, _descriptor(""))
        CozyMedia.build(CozyMedia.CommandConfig(descriptor))
        val manifestpath = root.resolve("target/cozy-media/manifest.json")
        val json = parse(Files.readString(manifestpath)).fold(error => throw error, identity)
        val historical = json.mapObject { fields =>
          fields.add("resources", io.circe.Json.fromValues(fields("resources").get.asArray.get.map { resource =>
            resource.mapObject { entry =>
              entry.add("receipt", entry("receipt").get.mapObject { receipt =>
                receipt.add("inputSetSha256", io.circe.Json.fromBoolean(false)).add(
                  "inputs", io.circe.Json.fromValues(receipt("inputs").get.asArray.get.map(
                    _.mapObject(_.add("sha256", io.circe.Json.Null))
                  ))
                )
              })
            }
          }))
        }
        _write(manifestpath, historical.noSpaces)

        When("the accepted receipt is read and verified")
        CozyMediaReceipt.manifest(manifestpath).isDefined shouldBe true
        CozyMedia.verify(CozyMedia.CommandConfig(descriptor))

        Then("removed historical properties do not prevent verification")
        CozyMedia.plan(CozyMedia.CommandConfig(descriptor)) should include("example: current")

        When("the accepted output bytes are modified")
        _write(root.resolve("target/cozy-media/example.txt"), "tampered")

        Then("local reuse remains timestamp-based but verification rejects the changed artifact")
        CozyMedia.plan(CozyMedia.CommandConfig(descriptor)) should include("example: current")
        intercept[Exception](CozyMedia.verify(CozyMedia.CommandConfig(descriptor)))
      }
    }

    "use declared input timestamps rather than content identities for freshness" in {
      _with_temp_dir("identity") { root =>
        Given("an accepted package with a structured slide IR input")
        _write(root.resolve("knowledge/article.dox"), "knowledge")
        _write(root.resolve("source.txt"), "source")
        _write(root.resolve("slides/ir.json"), "{\"b\": 2, \"a\": 1}")
        val descriptor = root.resolve("media.json")
        _write(descriptor, _descriptor("\"receipt\": {\"inputs\": [{\"id\": \"slide-ir\", \"role\": \"slide-ir\", \"path\": \"slides/ir.json\", \"normalization\": \"structured-document\"}]}"))
        CozyMedia.build(CozyMedia.CommandConfig(descriptor))
        val outputtime = Files.getLastModifiedTime(root.resolve("target/cozy-media/example.txt"))

        When("only JSON formatting and object-key order change")
        _write(root.resolve("slides/ir.json"), "{ \"a\" : 1, \"b\" : 2 }")
        Files.setLastModifiedTime(root.resolve("slides/ir.json"), FileTime.fromMillis(outputtime.toMillis + 1000))

        Then("the newer declared input selects regeneration even when its JSON meaning is unchanged")
        CozyMedia.plan(CozyMedia.CommandConfig(descriptor)) should include("example: build")

        When("a source byte changes but its mtime is restored")
        val source = root.resolve("source.txt")
        val mtime = Files.getLastModifiedTime(source)
        _write(source, "changed-source")
        Files.setLastModifiedTime(source, FileTime.fromMillis(mtime.toMillis))
        Files.setLastModifiedTime(root.resolve("slides/ir.json"), FileTime.fromMillis(outputtime.toMillis - 1000))

        Then("planning does not recreate a content-proof gate for a timestamp-preserved input")
        CozyMedia.plan(CozyMedia.CommandConfig(descriptor)) should include("example: current")
      }
    }

    "bind selected site configuration and document-route identities without serializing the site root" in {
      _with_temp_dir("site-context") { root =>
        Given("an accepted Article PDF with explicit selected SmartDox site context")
        val site = root.resolve("site").toAbsolutePath.normalize()
        val siteconfig = site.resolve("site.conf")
        val source = site.resolve("knowledge/article.dox")
        val descriptor = site.resolve("media.json")
        _write(root.resolve("conf/cozy/config.yaml"), _smartdox_profile("site"))
        _write(siteconfig, "site.base-url = https://example.test")
        _write(source, "knowledge")
        _write(site.resolve("infographic/article-ja.svg"), "<svg>infographic</svg>")
        _write(descriptor, _article_pdf_descriptor)
        val config = CozyMedia.CommandConfig(
          descriptor,
          profile = Some("site"),
          siteRoot = Some(site),
          siteConfig = Some(siteconfig)
        )
        val runner = new CozyMedia.ProcessRunner {
          def run(command: Vector[String], workingdirectory: Path): Int = {
            _write(Path.of(command(command.indexOf("--output") + 1)), "%PDF-1.7\naccepted")
            0
          }
        }

        When("Cozy accepts the PDF with the validated site inputs")
        CozyMedia.build(config, runner)
        val receipt = _manifest_resource_json(site.resolve("target/cozy-media/manifest.json"), "article-pdf-ja").hcursor.downField("receipt")
        val inputs = receipt.downField("inputs").focus.flatMap(_.asArray).getOrElse(fail("receipt inputs are required"))
        def _evidence_(id: String): io.circe.Json = inputs.find(_.hcursor.get[String]("id").toOption.contains(id)).getOrElse(fail(s"missing receipt evidence: $id"))
        val initial = CozyMediaReceipt.observeInputs(CozyMedia.resolvePlan(config))

        Then("the receipt carries root-relative authoritative configuration and source-route identities only")
        val configuration = _evidence_("cozy:site-config").hcursor
        configuration.get[String]("role").toOption shouldBe Some("site-configuration")
        configuration.get[String]("path").toOption shouldBe Some("site.conf")
        configuration.downField("sha256").focus shouldBe None
        val documentroute = _evidence_("cozy:site-document-route").hcursor
        documentroute.get[String]("role").toOption shouldBe Some("site-document-route")
        documentroute.get[String]("path").toOption shouldBe Some("knowledge/article.dox")
        documentroute.downField("sha256").focus shouldBe None
        Files.readString(site.resolve("target/cozy-media/manifest.json"), StandardCharsets.UTF_8) should not include site.toString

        When("the authoritative site configuration changes")
        _write(siteconfig, "site.base-url = https://changed.example.test")
        val changedconfiguration = CozyMediaReceipt.observeInputs(CozyMedia.resolvePlan(config))

        Then("the old receipt is stale through its configuration identity")
        changedconfiguration should not be initial
        CozyMedia.plan(config) should include("article-pdf-ja: build")

        When("the same document bytes are mapped from a new canonical site route")
        val remapped = site.resolve("knowledge/remapped-article.dox")
        _write(remapped, "knowledge")
        _write(descriptor, _article_pdf_descriptor.replace("knowledge/article.dox", "knowledge/remapped-article.dox"))
        val reroutedplan = CozyMedia.resolvePlan(config)
        val rerouted = CozyMediaReceipt.capture(reroutedplan)

        Then("the captured route evidence and currentness distinguish the new source-to-public-page identity")
        rerouted.inputs.find(_.id == "cozy:site-document-route").map(_.path) shouldBe Some("knowledge/remapped-article.dox")
        rerouted.observedFiles should not be changedconfiguration
        CozyMedia.plan(config) should include("article-pdf-ja: build")
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

        Then("planning uses timestamps while verify rejects changed accepted output bytes")
        CozyMedia.plan(CozyMedia.CommandConfig(descriptor)) should include("article-pdf-ja: current")
        intercept[Exception](CozyMedia.verify(CozyMedia.CommandConfig(descriptor)))
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

    "adopt an unchanged prebuilt after a valid input update" in {
      _with_temp_dir("stale-prebuilt-build") { root =>
        Given("an accepted prebuilt resource whose input changes while its output does not")
        _write(root.resolve("knowledge/article.dox"), "knowledge")
        _write(root.resolve("prebuilt.txt"), "prebuilt")
        val descriptor = root.resolve("prebuilt.json")
        _write(descriptor, _prebuilt_descriptor)
        CozyMedia.build(CozyMedia.CommandConfig(descriptor))
        val output = root.resolve("prebuilt.txt")
        val outputtime = Files.getLastModifiedTime(output)
        _write(root.resolve("knowledge/article.dox"), "knowledge-changed")

        When("the existing prebuilt resource is explicitly adopted")
        val result = CozyMedia.build(CozyMedia.CommandConfig(descriptor))

        Then("the current receipt is recorded without regenerating or touching the output")
        result should include("prebuilt: adopted prebuilt")
        Files.readString(output, StandardCharsets.UTF_8) shouldBe "prebuilt"
        Files.getLastModifiedTime(output) shouldBe outputtime
        CozyMedia.plan(CozyMedia.CommandConfig(descriptor)) should include("prebuilt: current")
      }
    }

    "adopt an unchanged site-video prebuilt when only the article content changes" in {
      _with_temp_dir("site-video-prebuilt-adoption") { root =>
        Given("an accepted domain-modeling video with its direct production evidence")
        _write(root.resolve("registration-v2/knowledge/index.dox"), "Japanese article")
        _write(root.resolve("conf/cozy/config.yaml"), _site_profile)
        val output = root.resolve("registration-v2/target/media/development-process/domain-modeling/ja/final.mp4")
        _write(output, "accepted-video")
        val sourceproduction = root.resolve("registration-v2/video/ja/source-production.json")
        _write(sourceproduction, _site_video_production(_sha256(output)))
        val production = root.resolve("registration-v2/video/ja/production.json")
        _write(production, Files.readString(sourceproduction, StandardCharsets.UTF_8))
        val descriptor = root.resolve("registration-v2/media.json")
        _write(descriptor, _site_video_prebuilt_descriptor)
        CozyMedia.build(CozyMedia.CommandConfig(descriptor))
        val outputsha = _sha256(output)
        val sourceproductionsha = _sha256(sourceproduction)
        val productionsha = _sha256(production)

        When("only the bilingual article source changes")
        _write(root.resolve("registration-v2/knowledge/index.dox"), "Japanese and English article")
        val result = CozyMedia.build(CozyMedia.CommandConfig(descriptor))

        Then("Cozy refreshes the receipt without requiring video regeneration or manual approval transfer")
        result should include("article-video-ja: adopted prebuilt")
        _sha256(output) shouldBe outputsha
        _sha256(sourceproduction) shouldBe sourceproductionsha
        _sha256(production) shouldBe productionsha
        CozyMedia.plan(CozyMedia.CommandConfig(descriptor)) should include("article-video-ja: current")
      }
    }

    "adopt the complete site-bound article-media package when only article source and route content change" in {
      _with_temp_dir("site-article-media-prebuilt-adoption") { root =>
        Given("an accepted infographic, summary-slides PDF, and video with their unchanged production authorities")
        val site = root.resolve("site").toAbsolutePath.normalize()
        val siteconfig = site.resolve("site.conf")
        val packageRoot = site.resolve("registration-v2")
        _write(root.resolve("conf/cozy/config.yaml"), _smartdox_profile("site"))
        _write(siteconfig, "site.base-url = https://example.test")
        _write(packageRoot.resolve("knowledge/index.dox"), "Japanese article")
        val video = packageRoot.resolve("target/media/development-process/domain-modeling/ja/final.mp4")
        val summary = packageRoot.resolve("summary/summary-ja.pdf")
        val infographic = packageRoot.resolve("infographic/summary-ja.png")
        _write(video, "accepted-video")
        _write(summary, "%PDF-1.7\naccepted-summary")
        _write(packageRoot.resolve("summary/summary-authority.pdf"), "%PDF-1.7\naccepted-summary")
        _write_png(infographic)
        _write(packageRoot.resolve("policy.txt"), "accepted-policy")
        val sourceproduction = packageRoot.resolve("video/ja/source-production.json")
        _write(sourceproduction, _site_video_production(_sha256(video)))
        _write(packageRoot.resolve("video/ja/production.json"), Files.readString(sourceproduction, StandardCharsets.UTF_8))
        val descriptor = packageRoot.resolve("media.json")
        _write(descriptor, _site_article_media_prebuilt_descriptor)
        val config = CozyMedia.CommandConfig(descriptor, profile = Some("site"), siteRoot = Some(site), siteConfig = Some(siteconfig))
        CozyMedia.build(config)
        val accepted = Vector(video, summary, infographic).map(_sha256)

        When("only the selected article source and its site document route content change")
        _write(packageRoot.resolve("knowledge/index.dox"), "Japanese article revised")
        val result = CozyMedia.build(config)

        Then("each unchanged article-media output is adopted and becomes current")
        result should include("article-video-ja: adopted prebuilt")
        result should include("summary-slides-ja: adopted prebuilt")
        result should include("infographic-ja: adopted prebuilt")
        Vector(video, summary, infographic).map(_sha256) shouldBe accepted
        val plan = CozyMedia.plan(config)
        plan should include("article-video-ja: current")
        plan should include("summary-slides-ja: current")
        plan should include("infographic-ja: current")
      }
    }

    "adopt site-bound article media after valid evidence updates without historical equality gates" in {
      val results = Vector[(String, Path => Unit)](
        "other-evidence" -> { root => _write(root.resolve("registration-v2/policy.txt"), "changed-policy") },
        "summary-authority" -> { root => _write(root.resolve("registration-v2/summary/summary-authority.pdf"), "%PDF-1.7\nchanged-authority") },
        "video-production" -> { root =>
          val production = _site_video_production(
            _sha256(root.resolve("registration-v2/target/media/development-process/domain-modeling/ja/final.mp4")),
            sourceauthority = "updated-authority"
          )
          _write(root.resolve("registration-v2/video/ja/source-production.json"), production)
          _write(root.resolve("registration-v2/video/ja/production.json"), production)
        }
      ).map { case (name, change) =>
        _with_temp_dir(s"site-article-media-$name") { root =>
          Given("an accepted complete article-media package with a later article-only change")
          _write(root.resolve("registration-v2/knowledge/index.dox"), "Japanese article")
          _write(root.resolve("conf/cozy/config.yaml"), _site_profile)
          val video = root.resolve("registration-v2/target/media/development-process/domain-modeling/ja/final.mp4")
          _write(video, "accepted-video")
          _write(root.resolve("registration-v2/summary/summary-ja.pdf"), "%PDF-1.7\naccepted-summary")
          _write(root.resolve("registration-v2/summary/summary-authority.pdf"), "%PDF-1.7\naccepted-summary")
          _write_png(root.resolve("registration-v2/infographic/summary-ja.png"))
          _write(root.resolve("registration-v2/policy.txt"), "accepted-policy")
          val sourceproduction = root.resolve("registration-v2/video/ja/source-production.json")
          _write(sourceproduction, _site_video_production(_sha256(video)))
          _write(root.resolve("registration-v2/video/ja/production.json"), Files.readString(sourceproduction, StandardCharsets.UTF_8))
          val descriptor = root.resolve("registration-v2/media.json")
          _write(descriptor, _site_article_media_prebuilt_descriptor)
          CozyMedia.build(CozyMedia.CommandConfig(descriptor))
          val outputs = Vector(video, root.resolve("registration-v2/summary/summary-ja.pdf"), root.resolve("registration-v2/infographic/summary-ja.png"))
          val accepted = outputs.map(path => (_sha256(path), Files.getLastModifiedTime(path)))
          _write(root.resolve("registration-v2/knowledge/index.dox"), "Japanese article revised")
          change(root)

          When("the unchanged package is explicitly adopted")
          val result = CozyMedia.build(CozyMedia.CommandConfig(descriptor))

          Then("valid current inputs replace old receipt evidence without altering the media")
          outputs.map(path => (_sha256(path), Files.getLastModifiedTime(path))) shouldBe accepted
          CozyMedia.plan(CozyMedia.CommandConfig(descriptor)) should include("article-video-ja: current")
          result
        }
      }

      results should have size 3
      results.foreach(_ should include("adopted prebuilt"))
    }

    "reject invalid current site-video production evidence without changing prior acceptance" in {
      val failures = Vector(
        "missing" -> { (root: Path) => Files.delete(root.resolve("registration-v2/video/ja/production.json")) },
        "corrupt" -> { (root: Path) => _write(root.resolve("registration-v2/video/ja/production.json"), "{") },
        "sha-mismatch" -> { (root: Path) => _write(root.resolve("registration-v2/video/ja/production.json"), _site_video_production("0" * 64)) },
        "qa-failed" -> { (root: Path) =>
          val production = _site_video_production(
            _sha256(root.resolve("registration-v2/target/media/development-process/domain-modeling/ja/final.mp4"))
          ).replace("technical-and-visual-qa-passed", "failed")
          _write(root.resolve("registration-v2/video/ja/production.json"), production)
        },
        "language-mismatch" -> { (root: Path) =>
          val production = _site_video_production(
            _sha256(root.resolve("registration-v2/target/media/development-process/domain-modeling/ja/final.mp4"))
          ).replace("\"language\": \"ja\"", "\"language\": \"en\"")
          _write(root.resolve("registration-v2/video/ja/source-production.json"), production)
          _write(root.resolve("registration-v2/video/ja/production.json"), production)
        }
      ).map { case (name, change) =>
        _with_temp_dir(s"site-video-prebuilt-$name") { root =>
          Given("an accepted site video whose common article input later changes")
          _write(root.resolve("registration-v2/knowledge/index.dox"), "Japanese article")
          _write(root.resolve("conf/cozy/config.yaml"), _site_profile)
          val output = root.resolve("registration-v2/target/media/development-process/domain-modeling/ja/final.mp4")
          _write(output, "accepted-video")
          val sourceproduction = root.resolve("registration-v2/video/ja/source-production.json")
          _write(sourceproduction, _site_video_production(_sha256(output)))
          _write(root.resolve("registration-v2/video/ja/production.json"), Files.readString(sourceproduction, StandardCharsets.UTF_8))
          val descriptor = root.resolve("registration-v2/media.json")
          _write(descriptor, _site_video_prebuilt_descriptor)
          CozyMedia.build(CozyMedia.CommandConfig(descriptor))
          val manifest = root.resolve("registration-v2/target/cozy-media/manifest.json")
          val previous = Files.readString(manifest, StandardCharsets.UTF_8)
          val outputtime = Files.getLastModifiedTime(output)
          _write(root.resolve("registration-v2/knowledge/index.dox"), "Japanese and English article")
          change(root)

          When("the unchanged video is explicitly rebuilt")
          val error = intercept[RuntimeException](CozyMedia.build(CozyMedia.CommandConfig(descriptor)))

          Then("invalid current production preserves the prior receipt and media")
          Files.readString(manifest, StandardCharsets.UTF_8) shouldBe previous
          Files.readString(output, StandardCharsets.UTF_8) shouldBe "accepted-video"
          Files.getLastModifiedTime(output) shouldBe outputtime
          error
        }
      }

      failures should have size 5
      failures.foreach(_.getMessage should not be empty)
      failures.exists(_.getMessage.contains("Media site-video production")) shouldBe true
    }

    "adopt current site video without historical producer or manifest proof fields" in {
      _with_temp_dir("site-video-operational-production") { root =>
        Given("a direct video and current production identity/status/QA without historical hashes or authority copies")
        _write(root.resolve("registration-v2/knowledge/index.dox"), "Japanese article")
        _write(root.resolve("conf/cozy/config.yaml"), _site_profile)
        _write(root.resolve("registration-v2/target/media/development-process/domain-modeling/ja/final.mp4"), "accepted-video")
        val production = """{"category":"development-process","article":"domain-modeling","language":"ja","render":{"status":"completed","qa":{"status":"technical-and-visual-qa-passed"}}}"""
        _write(root.resolve("registration-v2/video/ja/source-production.json"), production)
        _write(root.resolve("registration-v2/video/ja/production.json"), production)
        val descriptor = root.resolve("registration-v2/media.json")
        _write(descriptor, _site_video_prebuilt_descriptor)

        When("the video is explicitly adopted and adopted again after an article change")
        CozyMedia.build(CozyMedia.CommandConfig(descriptor))
        _write(root.resolve("registration-v2/knowledge/index.dox"), "Revised article")
        val result = CozyMedia.build(CozyMedia.CommandConfig(descriptor))

        Then("current operational evidence is sufficient without rebuilding historical proof fields")
        result should include("article-video-ja: adopted prebuilt")
        CozyMedia.plan(CozyMedia.CommandConfig(descriptor)) should include("article-video-ja: current")
      }
    }

    "adopt declared SVG infographics with site metadata and after article updates" in {
      Vector(None, Some("image/svg+xml")).foreach { mediatype =>
        _with_temp_dir("site-svg-prebuilt") { root =>
          Given("a valid SVG prebuilt with top-level site binding and an optional SVG media type")
          val source = root.resolve("knowledge/article.dox")
          val output = root.resolve("infographic/article-ja.svg")
          val descriptor = root.resolve("media.json")
          val svg = "<?xml version=\"1.0\"?><svg xmlns=\"http://www.w3.org/2000/svg\"><rect width=\"10\" height=\"10\"/></svg>"
          _write(source, "article")
          _write(root.resolve("conf/cozy/config.yaml"), _site_profile)
          _write(output, svg)
          _write(descriptor, _site_svg_prebuilt_descriptor(mediatype))
          val outputtime = Files.getLastModifiedTime(output)

          When("the SVG is initially adopted and adopted again after an article update")
          val initial = CozyMedia.build(CozyMedia.CommandConfig(descriptor))
          _write(source, "updated article")
          val updated = CozyMedia.build(CozyMedia.CommandConfig(descriptor))

          Then("both adoptions succeed without changing the external SVG or its timestamp")
          initial should include("infographic-ja: adopted prebuilt")
          updated should include("infographic-ja: adopted prebuilt")
          Files.readString(output, StandardCharsets.UTF_8) shouldBe svg
          Files.getLastModifiedTime(output) shouldBe outputtime
          CozyMedia.plan(CozyMedia.CommandConfig(descriptor)) should include("infographic-ja: current")
        }
      }
    }

    "reject malformed or non-SVG current infographic XML without replacing acceptance" in {
      Vector("not XML", "<svg>", "<document/>",
        "<svg xmlns=\"urn:not-svg\"/>",
        "<!DOCTYPE svg SYSTEM \"file:///nonexistent-cozy-svg.dtd\"><svg/>").foreach { invalidsvg =>
        _with_temp_dir("invalid-site-svg-prebuilt") { root =>
          Given("an accepted SVG subsequently replaced by invalid current SVG input")
          val output = root.resolve("infographic/article-ja.svg")
          val descriptor = root.resolve("media.json")
          _write(root.resolve("knowledge/article.dox"), "article")
          _write(root.resolve("conf/cozy/config.yaml"), _site_profile)
          _write(output, "<svg xmlns=\"http://www.w3.org/2000/svg\"/>")
          _write(descriptor, _site_svg_prebuilt_descriptor(Some("image/svg+xml")))
          CozyMedia.build(CozyMedia.CommandConfig(descriptor))
          val manifest = root.resolve("target/cozy-media/manifest.json")
          val previous = Files.readString(manifest, StandardCharsets.UTF_8)
          _write(output, invalidsvg)

          When("the invalid current SVG is explicitly adopted")
          val error = intercept[RuntimeException](CozyMedia.build(CozyMedia.CommandConfig(descriptor)))

          Then("SVG validation reports failure while retaining the receipt and external input")
          error.getMessage should include("SVG")
          Files.readString(manifest, StandardCharsets.UTF_8) shouldBe previous
          Files.readString(output, StandardCharsets.UTF_8) shouldBe invalidsvg
        }
      }
    }

    "validate current site-bound PDF and PNG formats even on first adoption" in {
      Vector("summary", "infographic").foreach { role =>
        _with_temp_dir(s"invalid-prebuilt-format-$role") { root =>
          Given("declared current inputs and one malformed external output without a prior receipt")
          val packageroot = root.resolve("registration-v2")
          _write(packageroot.resolve("knowledge/index.dox"), "Japanese article")
          _write(root.resolve("conf/cozy/config.yaml"), _site_profile)
          val video = packageroot.resolve("target/media/development-process/domain-modeling/ja/final.mp4")
          _write(video, "accepted-video")
          _write(packageroot.resolve("summary/summary-ja.pdf"), "%PDF-1.7\naccepted-summary")
          _write(packageroot.resolve("summary/summary-authority.pdf"), "%PDF-1.7\naccepted-summary")
          _write_png(packageroot.resolve("infographic/summary-ja.png"))
          _write(packageroot.resolve("policy.txt"), "accepted-policy")
          val production = _site_video_production(_sha256(video))
          _write(packageroot.resolve("video/ja/source-production.json"), production)
          _write(packageroot.resolve("video/ja/production.json"), production)
          val invalidoutput = packageroot.resolve(if (role == "summary") "summary/summary-ja.pdf" else "infographic/summary-ja.png")
          _write(invalidoutput, "not-the-declared-format")
          val descriptor = packageroot.resolve("media.json")
          _write(descriptor, _site_article_media_prebuilt_descriptor)

          When("the package is explicitly adopted")
          val error = intercept[RuntimeException](CozyMedia.build(CozyMedia.CommandConfig(descriptor)))

          Then("the real format error is reported without creating acceptance or altering external output")
          error.getMessage should include("header")
          Files.exists(packageroot.resolve("target/cozy-media/manifest.json")) shouldBe false
          Files.readString(invalidoutput, StandardCharsets.UTF_8) shouldBe "not-the-declared-format"
        }
      }
    }

    "accept a current same-output prebuilt when it is explicitly built" in {
      _with_temp_dir("current-prebuilt-build") { root =>
        Given("an accepted prebuilt resource whose input and output remain unchanged")
        _write(root.resolve("knowledge/article.dox"), "knowledge")
        _write(root.resolve("prebuilt.txt"), "prebuilt")
        val descriptor = root.resolve("prebuilt.json")
        _write(descriptor, _prebuilt_descriptor)
        CozyMedia.build(CozyMedia.CommandConfig(descriptor))

        When("the current prebuilt resource is explicitly built again")
        val outcome = scala.util.Try(CozyMedia.build(CozyMedia.CommandConfig(descriptor)))

        Then("Cozy accepts the unchanged external output without exception")
        outcome.isSuccess shouldBe true
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

  private def _site_video_prebuilt_descriptor: String =
    """{
      |  "schema": "cozy.media.v1",
      |  "knowledge": {"id": "development-process/domain-modeling", "source": "knowledge/index.dox"},
      |  "articleMedia": {"articleIdentity": "development-process/domain-modeling", "publicationProfile": "site"},
      |  "receipt": {"inputs": [
      |    {"id": "video-production", "role": "video-production-metadata", "path": "video/ja/source-production.json", "normalization": "bytes"},
      |    {"id": "adapter-video-production", "role": "adapter-video-production-metadata", "path": "video/ja/production.json", "normalization": "bytes"}
      |  ]},
      |  "resources": [{
      |    "id": "article-video-ja", "kind": "video", "language": "ja", "role": "article-introduction",
      |    "source": "target/media/development-process/domain-modeling/ja/final.mp4", "build": "prebuilt",
      |    "articleMedia": {"role": "video", "production": "video/ja/production.json"}
      |  }]
      |}
       |""".stripMargin

  private def _site_article_media_prebuilt_descriptor: String =
    """{
      |  "schema": "cozy.media.v1",
      |  "knowledge": {"id": "development-process/domain-modeling", "source": "knowledge/index.dox"},
      |  "articleMedia": {"articleIdentity": "development-process/domain-modeling", "publicationProfile": "site"},
      |  "receipt": {"inputs": [
      |    {"id": "video-production", "role": "video-production-metadata", "path": "video/ja/source-production.json", "normalization": "bytes"},
      |    {"id": "adapter-video-production", "role": "adapter-video-production-metadata", "path": "video/ja/production.json", "normalization": "bytes"},
      |    {"id": "site-document-route", "role": "site-document-route", "path": "knowledge/index.dox", "normalization": "bytes"},
      |    {"id": "summary-authority", "role": "final-summary-slides-pdf", "path": "summary/summary-authority.pdf", "normalization": "bytes"},
      |    {"id": "publication-policy", "role": "publication-policy", "path": "policy.txt", "normalization": "bytes"}
      |  ]},
      |  "resources": [{
      |    "id": "article-video-ja", "kind": "video", "language": "ja", "role": "article-introduction",
      |    "source": "target/media/development-process/domain-modeling/ja/final.mp4", "build": "prebuilt",
      |    "articleMedia": {"role": "video", "production": "video/ja/production.json"}
      |  }, {
      |    "id": "summary-slides-ja", "kind": "document", "language": "ja",
      |    "source": "summary/summary-ja.pdf", "build": "prebuilt",
      |    "articleMedia": {"role": "summary_slides_pdf", "publicPath": "/articles/domain-modeling/summary-ja.pdf", "mediaType": "application/pdf"}
      |  }, {
      |    "id": "infographic-ja", "kind": "infographic", "language": "ja",
      |    "source": "infographic/summary-ja.png", "build": "prebuilt",
      |    "articleMedia": {"role": "infographic", "publicPath": "/articles/domain-modeling/infographic-ja.png"}
      |  }]
      |}
       |""".stripMargin

  private def _site_profile: String =
    """project:
      |  id: simplemodeling-org
      |  kind: smartdox-site
      |media:
      |  publication-profiles:
      |    site:
      |      root: publication
      |      site-kind: smartdox
      |""".stripMargin

  private def _site_video_production(sha256: String, sourceauthority: String = "accepted-authority"): String =
    s"""{
       |  "category": "development-process",
       |  "article": "domain-modeling",
       |  "language": "ja",
       |  "render": {
       |    "status": "completed",
       |    "sha256": "$sha256",
       |    "sourceAuthority": "$sourceauthority",
       |    "videoManifestSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
       |    "qa": {"status": "technical-and-visual-qa-passed"}
       |  }
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

  private def _site_svg_prebuilt_descriptor(mediatype: Option[String]): String = {
    val media = mediatype.map(value => s""", "mediaType": "$value"""").getOrElse("")
    s"""{
       |  "schema": "cozy.media.v1",
       |  "knowledge": {"id": "development-process/example", "source": "knowledge/article.dox"},
       |  "articleMedia": {"articleIdentity": "development-process/example", "publicationProfile": "site"},
       |  "resources": [{
       |    "id": "infographic-ja", "kind": "infographic", "language": "ja",
       |    "source": "infographic/article-ja.svg", "build": "prebuilt",
       |    "articleMedia": {"role": "infographic", "publicPath": "/articles/example/infographic-ja.svg"$media}
       |  }]
       |}
       |""".stripMargin
  }

  private def _smartdox_profile(root: String): String =
    s"""project:
       |  id: example
       |  kind: smartdox-site
       |media:
       |  publication-profiles:
       |    site:
       |      root: $root
       |      site-kind: smartdox
       |""".stripMargin

  private def _sha256(path: Path): String =
    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).map(x => f"${x & 0xff}%02x").mkString

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

  private def _write_png(path: Path): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, Array[Byte](0x89.toByte, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00))
  }

  private def _delete(path: Path): Unit = {
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try stream.iterator().asScala.toVector.sortBy(_.toString.length).reverse.foreach(Files.deleteIfExists(_))
      finally stream.close()
    }
  }
}
