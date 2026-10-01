package cozy.video

import cozy.CozySpecVocabulary
import cozy.media.{CozyVisualPage, CozyVisualPageBinding}
import io.circe.Json
import io.circe.parser
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import scala.collection.JavaConverters._

/*
 * @since   Aug. 26, 2026
 *  version Aug. 28, 2026
 *  version Sep. 30, 2026
 * @version Oct.  1, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyVideoStoryboardReviewSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  import CozyVideoStoryboardReviewSpec.VisualPageMaterial

  "Cozy Video Storyboard review evidence" should {
    "load absent or arbitrary historical approval and evidence hashes without parsing or re-emitting them" in {
      _with_temp_dir("historical-review-hashes") { root =>
        Given("a declared v2 review with current safe material and historical hash properties")
        val storyboard = _v2_storyboard()
        val source = _write_storyboard(root, storyboard)
        val material = _write_visual_page_material(root)
        val project = _write_v2_project(root, source, storyboard, material.binding, "obsolete")
        val original = parser.parse(Files.readString(project, StandardCharsets.UTF_8)).toOption.get
        val values = Vector(None, Some(Json.Null), Some(Json.True), Some(Json.fromInt(42)), Some(Json.obj("old" -> Json.fromInt(1))), Some(Json.arr()), Some(Json.fromString("not-a-hash")))
        values.foreach { value =>
          val declaration = original.mapObject { fields =>
            fields.add("storyboardReview", fields("storyboardReview").get.mapObject { review =>
              val plain = review.remove("approvedIdentity").add("visualPage", review("visualPage").get.mapObject(_.remove("approvedEvidenceIdentity")))
              value.map { old =>
                plain.add("approvedIdentity", old).add("visualPage", plain("visualPage").get.mapObject(_.add("approvedEvidenceIdentity", old)))
              }.getOrElse(plain)
            })
          }
          Files.writeString(project, declaration.noSpaces, StandardCharsets.UTF_8)

          When("current evidence is generated without any approval hash requirement")
          val generated = CozyVideo.storyboardReviewEvidence(CozyVideo.StoryboardReviewConfig(project, root.resolve("target/cozy-video/v2-review")))
          val evidence = parser.parse(Files.readString(generated.evidencePath, StandardCharsets.UTF_8)).toOption.get
          val handoff = parser.parse(Files.readString(generated.handoffPath, StandardCharsets.UTF_8)).toOption.get

          Then("generated evidence and handoff contain no digest identities")
          evidence.hcursor.downField("identity").focus shouldBe empty
          evidence.hcursor.downField("storyboardIdentity").focus shouldBe empty
          evidence.hcursor.downField("effectiveRenderers").downArray.downField("identity").focus shouldBe empty
          handoff.hcursor.downField("evidenceIdentity").focus shouldBe empty
          handoff.hcursor.downField("identity").focus shouldBe empty

          value.foreach { old =>
            def _historical_(document: Json): Json = document.mapObject { fields =>
              fields.add("identity", old).add("storyboardIdentity", old).add("evidenceIdentity", old).add("visualInputIdentities", old)
                .add("effectiveRenderers", fields("effectiveRenderers").get.mapArray(_.map(_.mapObject(_.add("identity", old)))))
                .add("visualPages", fields("visualPages").get.mapArray(_.map(_.mapObject { page =>
                  page.add("visualPageIdentity", old).add("assets", page("assets").get.mapArray(_.map(_.mapObject(_.add("sha256", old)))))
                })))
            }
            Files.writeString(generated.evidencePath, _historical_(evidence).noSpaces, StandardCharsets.UTF_8)
            Files.writeString(generated.handoffPath, _historical_(handoff).noSpaces, StandardCharsets.UTF_8)
          }
          When("the explicit review validator receives absent or arbitrary historical digest properties")
          val current = CozyVideo.storyboardReviewCurrent(project)

          Then("valid direct files and references remain usable without old proof gates")
          current.evidencePath shouldBe generated.evidencePath
          current.handoffPath shouldBe generated.handoffPath
        }
      }
    }

    "reject malformed non-hash review entries without comparing historical content" in {
      _with_temp_dir("review-entry-structure") { root =>
        Given("a valid v2 review package with current source, binding and renderer")
        val storyboard = _v2_storyboard()
        val source = _write_storyboard(root, storyboard)
        val material = _write_visual_page_material(root)
        val project = _write_v2_project(root, source, storyboard, material.binding, "obsolete")
        val generated = CozyVideo.storyboardReviewEvidence(CozyVideo.StoryboardReviewConfig(project, root.resolve("target/cozy-video/v2-review")))
        val evidence = parser.parse(Files.readString(generated.evidencePath, StandardCharsets.UTF_8)).toOption.get
        val handoff = parser.parse(Files.readString(generated.handoffPath, StandardCharsets.UTF_8)).toOption.get
        val invalidrows = Vector(
          "scenes" -> Json.arr(Json.Null),
          "scenes" -> Json.arr(),
          "visualInputs" -> Json.arr(Json.obj("reference" -> Json.fromString("missing"))),
          "effectiveRenderers" -> Json.arr(Json.Null),
          "effectiveRenderers" -> evidence.hcursor.downField("effectiveRenderers").focus.get.mapArray(_.map(_.mapObject(_.add("configuration", Json.True)))),
          "effectiveRenderers" -> evidence.hcursor.downField("effectiveRenderers").focus.get.mapArray(rows => rows ++ rows),
          "visualPages" -> evidence.hcursor.downField("visualPages").focus.get.mapArray(rows => rows ++ rows),
          "visualPages" -> evidence.hcursor.downField("visualPages").focus.get.mapArray(_.map(_.mapObject(_.add("kind", Json.fromString("unknown"))))),
          "visualPages" -> evidence.hcursor.downField("visualPages").focus.get.mapArray(_.map(_.mapObject(page => page.add("assets", page("assets").get.mapArray(rows => rows ++ rows)))))
        )

        When("explicit review receives malformed scene, input, renderer or page entries")
        val failures = Vector(generated.evidencePath -> evidence, generated.handoffPath -> handoff).flatMap { case (path, document) =>
          invalidrows.filter { case (name, _) => document.hcursor.downField(name).focus.isDefined }.map { case (name, rows) =>
            Files.writeString(path, document.mapObject(_.add(name, rows)).noSpaces, StandardCharsets.UTF_8)
            val failure = intercept[Exception](CozyVideo.storyboardReviewCurrent(project))
            Files.writeString(path, document.noSpaces, StandardCharsets.UTF_8)
            failure
          }
        }

        Then("each invalid non-hash structure is rejected independently of historical identities")
        failures.foreach(_.getMessage should include("STORYBOARD_REVIEW_"))
      }
    }

    "preserve v1 evidence and handoff schemas, canonical field order, and non-consumer output" in {
      _with_temp_dir("normal") { root =>
        Given("a declared Storyboard review without an optional visualStory declaration")
        val storyboard = _storyboard("")
        val source = _write_storyboard(root, storyboard)
        val project = _write_project(root, Some(_review_json(source, "obsolete", None)))
        val save = root.resolve("review")

        When("the typed storyboard review evidence command writes its package")
        val result = CozyVideo.storyboardReviewEvidence(CozyVideo.StoryboardReviewConfig(project, save))
        val evidencetext = Files.readString(result.evidencePath, StandardCharsets.UTF_8)
        val handofftext = Files.readString(result.handoffPath, StandardCharsets.UTF_8)
        val evidence = parser.parse(evidencetext).toOption.get
        val handoff = parser.parse(handofftext).toOption.get

        Then("the deterministic evidence and handoff carry current review data without declaring approval")
        evidence.hcursor.get[String]("schema").toOption shouldBe Some("cozy.video.storyboard-review-evidence.v1")
        evidence.hcursor.get[String]("status").toOption shouldBe Some("validated")
        evidence.hcursor.downField("identity").focus shouldBe empty
        evidence.hcursor.downField("visualInputs").focus shouldBe empty
        handoff.hcursor.get[String]("schema").toOption shouldBe Some("cozy.video.storyboard-handoff.v1")
        evidencetext should startWith("""{"schema":"cozy.video.storyboard-review-evidence.v1","status":"validated"""")
        handofftext should startWith("{\"schema\":\"cozy.video.storyboard-handoff.v1\",\"evidencePath\"")
        handoff.hcursor.downField("identity").focus shouldBe empty
        handoff.noSpaces.toLowerCase should not include "pptx"
        handoff.noSpaces.toLowerCase should not include "accepted"
      }
    }

    "copy requested visual inputs in authored order without digest identities" in {
      _with_temp_dir("visual-inputs") { root =>
        Given("an approved Storyboard and an ordered visualStory input subset")
        val storyboard = _storyboard("assets/title.svg", Vector("assets/title.svg"))
        val source = _write_storyboard(root, storyboard)
        val input = root.resolve("assets/title.svg")
        Files.createDirectories(input.getParent)
        Files.writeString(input, "title", StandardCharsets.UTF_8)
        val evidencedirectory = "target/cozy-video/storyboard-review"
        val project = _write_project(root, Some(_review_json(source, "obsolete", Some(evidencedirectory -> Vector("assets/title.svg")))))
        val save = root.resolve(evidencedirectory)

        When("the visual-story evidence command is generated")
        val result = CozyVideo.storyboardReviewEvidence(CozyVideo.StoryboardReviewConfig(project, save))
        val evidence = parser.parse(Files.readString(result.evidencePath, StandardCharsets.UTF_8)).toOption.get
        val rows = evidence.hcursor.downField("visualInputs").as[Vector[Json]].toOption.get

        Then("only the admitted current input is copied and references preserve source order")
        rows.map(_.hcursor.get[String]("reference").toOption.get) shouldBe Vector("assets/title.svg")
        rows.foreach(_.hcursor.downField("identity").focus shouldBe empty)
        result.saveDir shouldBe save.toRealPath()
        Files.isRegularFile(save.resolve("visual-inputs/assets/title.svg")) shouldBe true
        rows should have size 1
      }
    }

    "route storyboard review-evidence directly before unsupported storyboard dispatch" in {
      _with_temp_dir("route") { root =>
        Given("a valid project and a direct storyboard review-evidence command line")
        val storyboard = _storyboard("")
        val source = _write_storyboard(root, storyboard)
        val project = _write_project(root, Some(_review_json(source, "obsolete", None)))
        val save = root.resolve("route-review")

        When("the direct CLI route is executed")
        val routed = CozyVideo.execute(List("video", "storyboard", "review-evidence", project.toString, "--save", save.toString))

        Then("the direct route succeeds and emits both typed package files")
        routed shouldBe true
        Files.isRegularFile(save.resolve("review-evidence.json")) shouldBe true
        Files.isRegularFile(save.resolve("handoff.json")) shouldBe true
      }
    }

    "list every storyboard operation when the direct command has no subcommand" in {
      Given("the direct video storyboard command without a subcommand")

      When("the command is executed")
      val failure = intercept[IllegalArgumentException] {
        CozyVideo.execute(List("video", "storyboard"))
      }

      Then("the missing-command diagnostic lists every supported storyboard operation")
      failure.getMessage should include("validate")
      failure.getMessage should include("inspect")
      failure.getMessage should include("convert")
      failure.getMessage should include("review-evidence")
    }

    "generate changed valid Storyboard sources without an obsolete approval identity gate" in {
      _with_temp_dir("approval") { root =>
        Given("a review declaration containing an earlier arbitrary approval property")
        val source = _write_storyboard(root, _storyboard(""))
        val project = _write_project(root, Some(_review_json(source, "obsolete", None)))
        Files.writeString(source, CozyVideo.canonicalStoryboardJson(_storyboard("changed")), StandardCharsets.UTF_8)

        When("evidence generation reloads the selected valid Storyboard")
        val result = CozyVideo.storyboardReviewEvidence(CozyVideo.StoryboardReviewConfig(project, root.resolve("approval-review")))

        Then("the current source is projected without implying semantic approval")
        val evidence = parser.parse(Files.readString(result.evidencePath, StandardCharsets.UTF_8)).toOption.get
        evidence.hcursor.downField("storyboardIdentity").focus shouldBe empty
        evidence.hcursor.downField("scenes").downArray.get[String]("caption").toOption shouldBe Some("changed")
      }
    }

    "reject missing undeclared and symlinked visual inputs" in {
      _with_temp_dir("input-safety") { root =>
        Given("visualStory declarations covering missing, undeclared, and symlinked references")
        val sourcestoryboard = _storyboard("assets/title.svg", Vector("assets/title.svg"))
        val source = _write_storyboard(root, sourcestoryboard)
        val identity = "obsolete"
        val missingproject = _write_project(root, Some(_review_json(source, identity, Some("target/cozy-video/missing" -> Vector("assets/title.svg")))))
        val missingfailure = intercept[Exception] {
          CozyVideo.storyboardReviewEvidence(CozyVideo.StoryboardReviewConfig(missingproject, root.resolve("target/cozy-video/missing")))
        }
        val undeclaredproject = _write_project(root, Some(_review_json(source, identity, Some("target/cozy-video/undeclared" -> Vector("assets/other.svg")))))
        val undeclaredfailure = intercept[Exception] {
          CozyVideo.storyboardReviewEvidence(CozyVideo.StoryboardReviewConfig(undeclaredproject, root.resolve("target/cozy-video/undeclared")))
        }
        val target = root.resolve("outside.svg")
        Files.writeString(target, "outside", StandardCharsets.UTF_8)
        val linked = root.resolve("assets/title.svg")
        Files.createDirectories(linked.getParent)
        Files.createSymbolicLink(linked, target)
        val symlinkproject = _write_project(root, Some(_review_json(source, identity, Some("target/cozy-video/symlink" -> Vector("assets/title.svg")))))

        When("each visual input is admitted")
        val symlinkfailure = intercept[Exception] {
          CozyVideo.storyboardReviewEvidence(CozyVideo.StoryboardReviewConfig(symlinkproject, root.resolve("target/cozy-video/symlink")))
        }

        Then("every unsafe input fails closed with a specific diagnostic")
        missingfailure.getMessage should include("STORYBOARD_REVIEW_INPUT_UNSAFE")
        undeclaredfailure.getMessage should include("STORYBOARD_REVIEW_INPUT_UNDECLARED")
        symlinkfailure.getMessage should include("STORYBOARD_REVIEW_INPUT_UNSAFE")
      }
    }

    "reject malformed visual evidence while allowing valid input updates and obsolete approval properties" in {
      _with_temp_dir("build-gate") { root =>
        Given("a visual-story project with a generated evidence package")
        val storyboard = _storyboard("assets/title.svg", Vector("assets/title.svg"))
        val source = _write_storyboard(root, storyboard)
        val input = root.resolve("assets/title.svg")
        Files.createDirectories(input.getParent)
        Files.writeString(input, "title", StandardCharsets.UTF_8)
        val evidencedirectory = "target/cozy-video/build-gate"
        val project = _write_project(root, Some(_review_json(source, "obsolete", Some(evidencedirectory -> Vector("assets/title.svg")))))
        val save = root.resolve(evidencedirectory)
        val generated = CozyVideo.storyboardReviewEvidence(CozyVideo.StoryboardReviewConfig(project, save))
        val originalevidence = Files.readString(generated.evidencePath, StandardCharsets.UTF_8)
        _write_project(root, Some(_review_json(source, "obsolete", Some(evidencedirectory -> Vector("assets/title.svg")), Some("obsolete"))))
        Files.writeString(generated.evidencePath, "{}", StandardCharsets.UTF_8)

        When("the explicit review validator receives malformed saved visual evidence")
        val failure = intercept[Exception] {
          CozyVideoImplementation._validate_storyboard_review_current(CozyVideoImplementation._plan(project, None, None))
        }

        Then("malformed visual evidence is rejected before any tool or process work")
        failure.getMessage should include("STORYBOARD_REVIEW_EVIDENCE_SCHEMA_INVALID")

        Files.writeString(generated.evidencePath, originalevidence, StandardCharsets.UTF_8)
        Files.writeString(input, "changed", StandardCharsets.UTF_8)
        When("the selected visual input changes after evidence generation and review currentness is requested")
        CozyVideoImplementation._validate_storyboard_review_current(CozyVideoImplementation._plan(project, None, None))
        Then("valid current inputs are usable without a historical evidence comparison")
        Files.readString(input, StandardCharsets.UTF_8) shouldBe "changed"

        Files.writeString(input, "title", StandardCharsets.UTF_8)
        _write_project(root, Some(_review_json(source, "obsolete", Some(evidencedirectory -> Vector("assets/title.svg")), Some("sha256:" + "0" * 64))))
        When("the visual evidence is present but not human-approved and review currentness is requested")
        CozyVideoImplementation._validate_storyboard_review_current(CozyVideoImplementation._plan(project, None, None))
        Then("obsolete approval properties do not become processing gates")
        Files.isRegularFile(generated.evidencePath) shouldBe true

        Given("historical visual input identities and an arbitrary visual-story approval value")
        val historical = parser.parse(originalevidence).toOption.get.mapObject(fields => fields.add("visualInputs", fields("visualInputs").get.mapArray(_.map(_.mapObject(_.add("identity", Json.arr(Json.True)))))))
        Files.writeString(generated.evidencePath, historical.noSpaces, StandardCharsets.UTF_8)
        val declaration = parser.parse(Files.readString(project, StandardCharsets.UTF_8)).toOption.get.mapObject(fields => fields.add("storyboardReview", fields("storyboardReview").get.mapObject(review => review.add("visualStory", review("visualStory").get.mapObject(_.add("approvedEvidenceIdentity", Json.obj()))))))
        Files.writeString(project, declaration.noSpaces, StandardCharsets.UTF_8)
        When("explicit review consumes those obsolete values")
        CozyVideoImplementation._validate_storyboard_review_current(CozyVideoImplementation._plan(project, None, None))
        Then("the saved package remains usable without parsing hashes")
        Files.isRegularFile(generated.evidencePath) shouldBe true

        Given("a visual copy whose parent directory has been replaced by a symlink")
        val copiedparent = save.resolve("visual-inputs/assets")
        Files.delete(copiedparent.resolve("title.svg"))
        Files.delete(copiedparent)
        Files.createSymbolicLink(copiedparent, input.getParent)
        When("explicit review validates the copied input boundary")
        val copyfailure = intercept[Exception] {
          CozyVideoImplementation._validate_storyboard_review_current(CozyVideoImplementation._plan(project, None, None))
        }
        Then("the symlinked parent rejects even though its child is a regular file")
        copyfailure.getMessage should include("STORYBOARD_REVIEW_SYMLINK_COMPONENT")
      }
    }

    "keep legacy projects without storyboardReview behaviorally unchanged" in {
      _with_temp_dir("legacy") { root =>
        Given("a legacy project descriptor without a storyboardReview declaration")
        val project = _write_project(root, None)

        When("the existing dry-run build path plans the project")
        val output = CozyVideo.build(CozyVideo.BuildConfig(project, dryRun = true, checkTools = false), CozyVideo.VideoToolRegistry(Vector.empty))

        Then("the new gate is not applied to the legacy project")
        output should include("Dry-Run")
      }
    }

    "write v2 visual-page evidence and a self-identifying handoff from literal current inputs" in {
      _with_temp_dir("v2-visual-page") { root =>
        Given("a v2 Storyboard visual-page screen, its exact PageSet/catalog/binding resources, and a selected renderer")
        val storyboard = _v2_storyboard()
        val source = _write_storyboard(root, storyboard)
        val material = _write_visual_page_material(root)
        val project = _write_v2_project(root, source, storyboard, material.binding, "sha256:" + "0" * 64)
        val save = root.resolve("target/cozy-video/v2-review")

        When("the v2 review-evidence route derives the review package")
        val generated = CozyVideo.storyboardReviewEvidence(CozyVideo.StoryboardReviewConfig(project, save))
        val evidencetext = Files.readString(generated.evidencePath, StandardCharsets.UTF_8)
        val handofftext = Files.readString(generated.handoffPath, StandardCharsets.UTF_8)
        val evidence = parser.parse(evidencetext).toOption.get
        val handoff = parser.parse(handofftext).toOption.get

        Then("both v2 values retain literal screen references and asset observations without catalog or Binding snapshots")
        evidence.hcursor.get[String]("schema").toOption shouldBe Some("cozy.video.storyboard-review-evidence.v2")
        evidence.hcursor.downField("visualPages").downArray.get[String]("kind").toOption shouldBe Some("visual-page")
        evidence.hcursor.downField("visualPages").downArray.get[String]("source").toOption shouldBe Some("visual-pages.json")
        val assetproof = evidence.hcursor.downField("visualPages").downArray.downField("assets").downArray.focus.get
        assetproof.asObject.get.keys.toVector shouldBe Vector("id", "path", "mediaType", "modifiedAt")
        assetproof.hcursor.get[String]("id").toOption shouldBe Some("diagram")
        assetproof.hcursor.get[String]("path").toOption shouldBe Some("assets/diagram.svg")
        assetproof.hcursor.get[String]("mediaType").toOption shouldBe Some("image/svg+xml")
        assetproof.hcursor.get[String]("modifiedAt").toOption shouldBe Some(Files.getLastModifiedTime(root.resolve("assets/diagram.svg"), java.nio.file.LinkOption.NOFOLLOW_LINKS).toString)
        evidence.hcursor.downField("effectiveRenderers").downArray.get[String]("partId").toOption shouldBe Some("storyboard")
        evidence.hcursor.downField("effectiveRenderers").downArray.downField("identity").focus shouldBe empty
        handoff.hcursor.get[String]("schema").toOption shouldBe Some("cozy.video.storyboard-handoff.v2")
        handoff.hcursor.downField("identity").focus shouldBe empty
        evidencetext should startWith("""{"schema":"cozy.video.storyboard-review-evidence.v2","status":"validated","source":""")
        evidence.hcursor.downField("identity").focus shouldBe empty
        handofftext should startWith("""{"schema":"cozy.video.storyboard-handoff.v2","status":"validated","evidencePath":""")
        handoff.hcursor.downField("evidenceIdentity").focus shouldBe empty
        evidence.hcursor.downField("visualPages").downArray.keys.map(_.toVector) shouldBe Some(Vector("sceneId", "kind", "source", "catalog", "pageId", "assets"))
        handoff.hcursor.downField("visualPages").downArray.keys.map(_.toVector) shouldBe Some(Vector("sceneId", "kind", "source", "catalog", "pageId", "assets"))
      }
    }

    "reject a missing or unsafe v2 visualPage declaration without choosing a binding" in {
      _with_temp_dir("v2-visual-page-declaration") { root =>
        Given("a v2 visual-page Storyboard and direct candidate binding material")
        val storyboard = _v2_storyboard()
        val source = _write_storyboard(root, storyboard)
        val material = _write_visual_page_material(root)
        val missing = _write_v2_project(root, source, storyboard, material.binding, "sha256:" + "0" * 64, visualpage = None)

        When("the declaration is absent and then uses an escaping binding path")
        val missingfailure = intercept[Exception] {
          CozyVideo.storyboardReviewEvidence(CozyVideo.StoryboardReviewConfig(missing, root.resolve("target/cozy-video/missing")))
        }
        val unsafe = _write_v2_project(root, source, storyboard, material.binding, "sha256:" + "0" * 64, bindingref = "../binding.json")
        val unsafefailure = intercept[Exception] {
          CozyVideo.storyboardReviewEvidence(CozyVideo.StoryboardReviewConfig(unsafe, root.resolve("target/cozy-video/v2-review")))
        }

        Then("both routes fail closed rather than inferring or traversing a business binding")
        missingfailure.getMessage should include("STORYBOARD_REVIEW_VISUAL_PAGE_MISSING")
        unsafefailure.getMessage should include("STORYBOARD_REVIEW_PATH_INVALID")
      }
    }

    "process valid VisualPage material and Binding changes while rejecting invalid current inputs and independent review failures" in {
      _with_temp_dir("v2-build-gate") { root =>
        Given("an approved v2 visual-page evidence package with direct current inputs")
        val storyboard = _v2_storyboard()
        val source = _write_storyboard(root, storyboard)
        val material = _write_visual_page_material(root)
        val project = _write_v2_project(root, source, storyboard, material.binding, "sha256:" + "0" * 64)
        val save = root.resolve("target/cozy-video/v2-review")
        val generated = CozyVideo.storyboardReviewEvidence(CozyVideo.StoryboardReviewConfig(project, save))
        _write_v2_project(root, source, storyboard, material.binding, "obsolete")
        val binding = Files.readString(material.binding, StandardCharsets.UTF_8)
        val catalog = Files.readString(root.resolve("catalog.json"), StandardCharsets.UTF_8)
        val asset = root.resolve("assets/diagram.svg")
        val assetbytes = Files.readString(asset, StandardCharsets.UTF_8)
        val assetmodified = Files.getLastModifiedTime(asset, java.nio.file.LinkOption.NOFOLLOW_LINKS)

        When("the binding, catalog, page asset, effective renderer, part selection, and handoff are changed in turn and review currentness is requested")
        Files.writeString(material.binding, binding.replace("nodes-slot", "nodes-revised-slot"), StandardCharsets.UTF_8)
        val bindingcurrent = CozyVideo.storyboardReviewCurrent(project)
        Files.writeString(material.binding, binding, StandardCharsets.UTF_8)
        Files.writeString(root.resolve("catalog.json"), catalog.replace("\"core\"", "\"changed\""), StandardCharsets.UTF_8)
        val catalogfailure = intercept[Exception] {
          CozyVideo.storyboardReviewCurrent(project)
        }
        Files.writeString(root.resolve("catalog.json"), catalog, StandardCharsets.UTF_8)
        Files.writeString(asset, "<svg>changed</svg>", StandardCharsets.UTF_8)
        Files.setLastModifiedTime(asset, java.nio.file.attribute.FileTime.from(assetmodified.toInstant.plusSeconds(5)))
        val assetcurrent = CozyVideo.storyboardReviewCurrent(project)
        Files.writeString(asset, assetbytes, StandardCharsets.UTF_8)
        Files.setLastModifiedTime(asset, assetmodified)
        _write_v2_project(root, source, storyboard, material.binding, "obsolete", renderer = Some(Json.obj("engine" -> Json.fromString("remotion"), "fps" -> Json.fromInt(60))))
        val renderercurrent = CozyVideo.storyboardReviewCurrent(project)
        _write_v2_project(root, source, storyboard, material.binding, "obsolete", renderer = None)
        val norendererfailure = intercept[Exception] {
          CozyVideo.storyboardReviewCurrent(project)
        }
        _write_v2_project(root, source, storyboard, material.binding, "obsolete", storyboardpart = None)
        val nopartfailure = intercept[Exception] {
          CozyVideo.storyboardReviewCurrent(project)
        }
        _write_v2_project(root, source, storyboard, material.binding, "obsolete")
        Files.writeString(generated.handoffPath, "{}", StandardCharsets.UTF_8)
        val handofffailure = intercept[Exception] {
          CozyVideo.storyboardReviewCurrent(project)
        }

        Then("valid updates keep the operational references usable and actual input or remaining independent proof failures reject")
        bindingcurrent.evidencePath shouldBe generated.evidencePath
        catalogfailure.getMessage should include("VISUAL_PAGE_SCREEN_RESOLUTION_INVALID")
        assetcurrent.evidencePath shouldBe generated.evidencePath
        renderercurrent.evidencePath shouldBe generated.evidencePath
        norendererfailure.getMessage should include("STORYBOARD_REVIEW_EFFECTIVE_RENDERER_MISSING")
        nopartfailure.getMessage should include("STORYBOARD_REVIEW_STORYBOARD_PART_MISSING")
        handofffailure.getMessage should include("STORYBOARD_REVIEW_HANDOFF_SCHEMA_INVALID")
      }
    }

    "reject duplicate v2 evidence and handoff keys through explicit review currentness" in {
      _with_temp_dir("v2-duplicate-json") { root =>
        Given("an approved v2 visual-page evidence package without build runner involvement")
        val storyboard = _v2_storyboard()
        val source = _write_storyboard(root, storyboard)
        val material = _write_visual_page_material(root)
        val project = _write_v2_project(root, source, storyboard, material.binding, "sha256:" + "0" * 64)
        val save = root.resolve("target/cozy-video/v2-review")
        val generated = CozyVideo.storyboardReviewEvidence(CozyVideo.StoryboardReviewConfig(project, save))
        _write_v2_project(root, source, storyboard, material.binding, "obsolete")
        val originalevidence = Files.readString(generated.evidencePath, StandardCharsets.UTF_8)
        val originalhandoff = Files.readString(generated.handoffPath, StandardCharsets.UTF_8)
        When("the saved evidence and then the saved handoff repeat their schema field before review currentness")
        Files.writeString(
          generated.evidencePath,
          originalevidence.replace("{\"schema\":", "{\"schema\":\"tampered\",\"schema\":"),
          StandardCharsets.UTF_8
        )
        val evidencefailure = intercept[Exception] {
          CozyVideo.storyboardReviewCurrent(project)
        }
        Files.writeString(generated.evidencePath, originalevidence, StandardCharsets.UTF_8)
        Files.writeString(
          generated.handoffPath,
          originalhandoff.replace("{\"schema\":", "{\"schema\":\"tampered\",\"schema\":"),
          StandardCharsets.UTF_8
        )
        val handofffailure = intercept[Exception] {
          CozyVideo.storyboardReviewCurrent(project)
        }

        Then("both duplicate proof values fail closed through the independent review boundary")
        evidencefailure.getMessage should include("STORYBOARD_REVIEW_EVIDENCE_DUPLICATE_FIELD")
        handofffailure.getMessage should include("STORYBOARD_REVIEW_EVIDENCE_DUPLICATE_FIELD")
      }
    }

    "keep ordinary dry-run build independent from optional saved visual-review state" in {
      _with_temp_dir("ordinary-build-independent") { root =>
        Given("an ordinary project with malformed optional visual-review evidence")
        val storyboard = _storyboard("assets/title.svg", Vector("assets/title.svg"))
        val source = _write_storyboard(root, storyboard)
        val input = root.resolve("assets/title.svg")
        Files.createDirectories(input.getParent)
        Files.writeString(input, "title", StandardCharsets.UTF_8)
        val evidence = root.resolve("target/cozy-video/ordinary/review-evidence.json")
        Files.createDirectories(evidence.getParent)
        Files.writeString(evidence, "{}", StandardCharsets.UTF_8)
        val project = _write_project(root, Some(_review_json(source, "obsolete", Some("target/cozy-video/ordinary" -> Vector("assets/title.svg")), Some("sha256:" + "0" * 64))))

        When("the ordinary build is dry-run without explicitly requesting review currentness")
        val output = CozyVideo.build(CozyVideo.BuildConfig(project, dryRun = true, checkTools = false), CozyVideo.VideoToolRegistry(Vector.empty))

        Then("planning succeeds and does not create a build output despite stale optional review state")
        output should include("Dry-Run")
        Files.exists(root.resolve("build/final.mp4")) shouldBe false
      }
    }

    "keep generated evidence deterministic without digest identities" in {
      Given("a generator of equivalent Storyboard captions")
      val captions = Gen.oneOf(Vector("", "caption", "another caption"))
      val property = Prop.forAll(captions) { caption =>
        _with_temp_dir("identity-property") { root =>
          val storyboard = _storyboard(caption)
          val source = _write_storyboard(root, storyboard)
          val project = _write_project(root, Some(_review_json(source, "obsolete", None)))
          val first = CozyVideo.storyboardReviewEvidence(CozyVideo.StoryboardReviewConfig(project, root.resolve("first")))
          val second = CozyVideo.storyboardReviewEvidence(CozyVideo.StoryboardReviewConfig(project, root.resolve("second")))
          Files.readString(first.evidencePath, StandardCharsets.UTF_8) == Files.readString(second.evidencePath, StandardCharsets.UTF_8)
        }
      }

      When("ScalaCheck evaluates equivalent deterministic generator outputs")
      val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(30), property)

      Then("equivalent normalized Storyboard inputs generate deterministic evidence bytes")
      withClue(s"ScalaCheck status=${result.status}, succeeded=${result.succeeded}, discarded=${result.discarded}") {
        result.passed shouldBe true
      }
    }
  }

  private def _storyboard(caption: String, diagramrefs: Vector[String] = Vector.empty): CozyVideo.Storyboard =
    CozyVideo.Storyboard(
      "cozy.video.storyboard.v1",
      1,
      Vector(CozyVideo.StoryboardScene(
        "intro",
        1,
        "opening",
        "narrator",
        "narration",
        "Welcome to Cozy.",
        CozyVideo.StoryboardScreen("Welcome", "Screen"),
        caption,
        BigDecimal("1.0"),
        BigDecimal("0.0"),
        "cut",
        Vector.empty,
        diagramrefs,
        Vector.empty,
        Vector.empty,
        "Show the screen."
      ))
    )

  private def _v2_storyboard(): CozyVideo.Storyboard =
    CozyVideo.Storyboard(
      "cozy.video.storyboard.v2",
      2,
      Vector(CozyVideo.StoryboardScene(
        "visual",
        1,
        "opening",
        "narrator",
        "narration",
        "Visual Page proof.",
        CozyVideoImplementation.StoryboardVisualPageScreen("visual-pages.json", "catalog.json", "overview"),
        "Visual Page",
        BigDecimal("1.0"),
        BigDecimal("0.0"),
        "cut",
        Vector.empty,
        Vector.empty,
        Vector.empty,
        Vector.empty,
        "Review the exact visual page."
      ))
    )

  private def _write_visual_page_material(root: Path): VisualPageMaterial = {
    val asset = root.resolve("assets/diagram.svg")
    val source = root.resolve("sources/research.txt")
    Files.createDirectories(asset.getParent)
    Files.createDirectories(source.getParent)
    Files.writeString(asset, "<svg/>", StandardCharsets.UTF_8)
    Files.writeString(source, "research", StandardCharsets.UTF_8)
    val catalog = _visual_catalog()
    val page = CozyVisualPage.Page(
      "overview",
      "Cozy",
      "en",
      CozyVisualPage.CatalogReference("core", 1),
      CozyVisualPage.Logical(
        "sequence",
        Vector(
          CozyVisualPage.Node("discover", "step", "Discover", Vector("research")),
          CozyVisualPage.Node("apply", "step", "Apply", Vector("research"))
        ),
        Vector(CozyVisualPage.Relation("next", "next", "discover", "apply", Vector("research")))
      ),
      CozyVisualPage.Visual("flow-horizontal", Vector.empty),
      Vector(CozyVisualPage.Asset("diagram", "assets/diagram.svg", "image/svg+xml")),
      Vector(CozyVisualPage.SourceBinding("research", "sources/research.txt"))
    )
    Files.writeString(root.resolve("catalog.json"), CozyVisualPage.canonicalCatalogJson(catalog), StandardCharsets.UTF_8)
    Files.writeString(root.resolve("visual-pages.json"), CozyVisualPage.canonicalJson(CozyVisualPage.PageSet("pages", Vector(page))), StandardCharsets.UTF_8)
    val binding = root.resolve("binding.json")
    Files.writeString(binding, _binding_json, StandardCharsets.UTF_8)
    VisualPageMaterial(binding)
  }

  private def _write_v2_project(
    root: Path,
    source: Path,
    storyboard: CozyVideo.Storyboard,
    binding: Path,
    approval: String,
    visualpage: Option[Boolean] = Some(true),
    bindingref: String = "binding.json",
    renderer: Option[Json] = Some(Json.obj("engine" -> Json.fromString("remotion"), "fps" -> Json.fromInt(30))),
    storyboardpart: Option[String] = Some("storyboard.json")
  ): Path = {
    val reviewfields = Vector(
      "source" -> Json.fromString(root.relativize(source).toString.replace(java.io.File.separatorChar, '/')),
      "approvedIdentity" -> Json.fromString("obsolete")
    ) ++ visualpage.toVector.map { _ =>
      "visualPage" -> Json.obj(
        "binding" -> Json.fromString(bindingref),
        "evidenceDirectory" -> Json.fromString("target/cozy-video/v2-review"),
        "approvedEvidenceIdentity" -> Json.fromString(approval)
      )
    }
    val projectfields = Vector(
      Some("name" -> Json.fromString("v2-review")),
      renderer.map(value => "renderer" -> value),
      Some("storyboardReview" -> Json.obj(reviewfields: _*)),
      Some("parts" -> Json.fromValues(storyboardpart.toVector.map { value =>
        Json.obj("id" -> Json.fromString("storyboard"), "type" -> Json.fromString("storyboard"), "storyboard" -> Json.fromString(value))
      }))
    ).flatten
    val project = root.resolve("video-v2.json")
    Files.writeString(project, Json.obj(projectfields: _*).noSpaces, StandardCharsets.UTF_8)
    project
  }

  private def _visual_catalog(): CozyVisualPage.Catalog = CozyVisualPage.Catalog(
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
      CozyVisualPage.LogicalPattern("sequence", Vector(CozyVisualPage.NodeRole("step", 2, 8)), Vector(CozyVisualPage.RelationRule("next", Vector("step"), Vector("step"), 1, 7, "linear"))),
      CozyVisualPage.LogicalPattern("causal-chain", Vector(CozyVisualPage.NodeRole("cause", 1, 7), CozyVisualPage.NodeRole("effect", 1, 7)), Vector(CozyVisualPage.RelationRule("causes", Vector("cause"), Vector("effect"), 1, 16, "acyclic"), CozyVisualPage.RelationRule("enables", Vector("cause"), Vector("effect"), 1, 16, "acyclic"))),
      CozyVisualPage.LogicalPattern("dependency-map", Vector(CozyVisualPage.NodeRole("dependency", 1, 7), CozyVisualPage.NodeRole("dependent", 1, 7)), Vector(CozyVisualPage.RelationRule("depends-on", Vector("dependent"), Vector("dependency"), 1, 16, "acyclic"))),
      CozyVisualPage.LogicalPattern("mapping", Vector(CozyVisualPage.NodeRole("source", 1, 7), CozyVisualPage.NodeRole("target", 1, 7)), Vector(CozyVisualPage.RelationRule("maps-to", Vector("source"), Vector("target"), 1, 16, "bipartite")))
    ),
    Vector(
      CozyVisualPage.VisualPattern("flow-horizontal", Vector("causal-chain", "sequence"), Vector(CozyVisualPage.ParameterDefinition("emphasisNode", "node-ref", false), CozyVisualPage.ParameterDefinition("showRelationLabels", "boolean", false))),
      CozyVisualPage.VisualPattern("flow-vertical", Vector("causal-chain", "dependency-map", "sequence"), Vector(CozyVisualPage.ParameterDefinition("emphasisNode", "node-ref", false), CozyVisualPage.ParameterDefinition("showRelationLabels", "boolean", false))),
      CozyVisualPage.VisualPattern("mapping-columns", Vector("mapping"), Vector(CozyVisualPage.ParameterDefinition("showRelationLabels", "boolean", false), CozyVisualPage.ParameterDefinition("sourceColumnTitle", "string", true), CozyVisualPage.ParameterDefinition("targetColumnTitle", "string", true)))
    )
  )

  private def _binding_json: String = {
    val slots = Vector("knowledge", "nodes", "relations", "assets", "parameters").map { slot =>
      s"""{"semanticSlot":"$slot","physicalSlot":"$slot-slot"}"""
    }.mkString("[", ",", "]")
    val patterns = Vector("flow-horizontal", "flow-vertical", "mapping-columns").map { pattern =>
      s"""{"visualPattern":"$pattern","slots":$slots}"""
    }.mkString("[", ",", "]")
    s"""{"schema":"cozy.visual-page.binding.v1","version":1,"id":"business-default","profile":"business","catalog":{"id":"core","revision":1},"patterns":$patterns}"""
  }

  private def _write_storyboard(root: Path, storyboard: CozyVideo.Storyboard): Path = {
    val source = root.resolve("storyboard.json")
    Files.writeString(source, CozyVideo.canonicalStoryboardJson(storyboard), StandardCharsets.UTF_8)
    source
  }

  private def _review_json(
    source: Path,
    identity: String,
    visual: Option[(String, Vector[String])],
    evidenceidentity: Option[String] = None
  ): String = {
    val root = source.getParent
    val sourcepath = root.relativize(source).toString.replace(java.io.File.separatorChar, '/')
    val base = Json.obj(
      "source" -> Json.fromString(sourcepath),
      "approvedIdentity" -> Json.fromString(identity)
    )
    val review = visual.map { case (directory, refs) =>
      val visualfields = Vector(
        Some("evidenceDirectory" -> Json.fromString(directory)),
        Some("inputRefs" -> Json.fromValues(refs.map(Json.fromString))),
        evidenceidentity.map(value => "approvedEvidenceIdentity" -> Json.fromString(value))
      ).flatten
      base.deepMerge(Json.obj("visualStory" -> Json.obj(visualfields: _*)))
    }.getOrElse(base)
    review.noSpaces
  }

  private def _write_project(root: Path, review: Option[String]): Path = {
    val project = root.resolve("video.yaml")
    val base = Json.obj(
      "name" -> Json.fromString("review-test"),
      "parts" -> Json.arr()
    )
    val document = review.map(value => base.deepMerge(Json.obj("storyboardReview" -> parser.parse(value).toOption.get))).getOrElse(base)
    Files.writeString(project, document.spaces2, StandardCharsets.UTF_8)
    project
  }

  private def _with_temp_dir[A](name: String)(body: Path => A): A = {
    val root = Files.createTempDirectory("cozy-video-storyboard-review-" + name + "-")
    try body(root)
    finally _delete(root)
  }

  private def _delete(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try stream.iterator().asScala.toVector.sortBy(_.toString.length).reverse.foreach(Files.delete)
      finally stream.close()
    }
}

object CozyVideoStoryboardReviewSpec {
  private final case class VisualPageMaterial(binding: Path)
}
