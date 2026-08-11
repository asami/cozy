package cozy.publication

import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import java.security.MessageDigest
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.smartdox.metadata.PublishMetadata.{ImageReference, VideoPresentation, VideoStatus}

/*
 * @since   Aug. 11, 2026
 * @version Aug. 11, 2026
 * @author  ASAMI, Tomoharu
 */
private object SiteBindingPart5Fixture {
  final class Data(
    val root: Path,
    val descriptor: Path,
    val profileroot: Path
  )
}

final class CozyArticleMediaSiteBindingSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "CozyArticleMediaSiteBinding" should {
    "prepare immutable deterministic Part 5 site-media evidence" which {
      "retain typed strict variants, original accepted URLs, and file evidence" in {
        _with_part5_fixture("deterministic") { fixture =>
          Given("a normal Part 5 package with distinct media knowledge and article identities")
          val config = CozyArticleMediaSiteBinding.Config(fixture.descriptor)

          When("the read-only site binding plan is prepared")
          val plan = CozyArticleMediaSiteBinding.plan(config)

          Then("the candidate identifiers, canonical locales, and nested roles are deterministic")
          plan.config shouldBe config
          plan.articleIdentity shouldBe "development-process/part-5"
          plan.descriptor.knowledge.id shouldBe "media-package/part-5"
          plan.candidates.map(_.resourceId) shouldBe Vector(
            "part-5-summary-en",
            "part-5-summary-ja",
            "part-5-video-en",
            "part-5-video-ja"
          )
          plan.candidates.map(x => x.resourceId -> x.locale) shouldBe Vector(
            "part-5-summary-en" -> "en",
            "part-5-summary-ja" -> "ja",
            "part-5-video-en" -> "en",
            "part-5-video-ja" -> "ja"
          )
          plan.candidates.map(_.role.serializedName) shouldBe Vector("infographic", "infographic", "video", "video")

          And("top-level production roles remain independent of registration")
          plan.descriptor.resources.find(_.id == "part-5-summary-ja").flatMap(_.role) shouldBe Some("article-summary")
          plan.descriptor.resources.find(_.id == "part-5-video-ja").flatMap(_.role) shouldBe Some("article-introduction")

          And("the strict images retain only declared SmartDox fields")
          val jaimage = _candidate(plan, "part-5-summary-ja")
          jaimage.role shouldBe CozyArticleMediaSiteBinding.Role.Infographic
          jaimage.variant.infographic shouldBe Some(ImageReference(
            new URI("/ja/development-process/images/part-5/summary.png"),
            Some("image/png"),
            Some("Part 5 summary")
          ))
          jaimage.variant.video shouldBe empty

          And("the strict videos are watch-only published YouTube links with original URL spelling")
          val javideo = _candidate(plan, "part-5-video-ja")
          val envideo = _candidate(plan, "part-5-video-en")
          javideo.role shouldBe CozyArticleMediaSiteBinding.Role.Video
          javideo.variant.video.map(_.presentation) shouldBe Some(VideoPresentation.ExternalLink)
          javideo.variant.video.map(_.status) shouldBe Some(VideoStatus.Published)
          javideo.variant.video.flatMap(_.provider) shouldBe Some("youtube")
          javideo.variant.video.flatMap(_.watchUrl).map(_.toString) shouldBe Some("https://youtu.be/ja_Part5-1")
          envideo.variant.video.flatMap(_.watchUrl).map(_.toString) shouldBe Some("https://www.youtube.com/watch?v=en_Part5-2")
          javideo.variant.video.flatMap(_.contentUrl) shouldBe empty
          envideo.variant.video.flatMap(_.contentUrl) shouldBe empty

          And("descriptor, infographic, and production evidence retains normalized identities, sizes, and hashes")
          plan.descriptorEvidence.path shouldBe fixture.descriptor.toAbsolutePath.normalize()
          plan.descriptorEvidence.identity shouldBe plan.descriptorEvidence.path
          plan.descriptorEvidence.sha256 shouldBe _sha256(fixture.descriptor)
          plan.descriptorEvidence.size shouldBe Files.size(fixture.descriptor)
          plan.descriptorEvidence.fileKey should not be null
          plan.descriptorBytes shouldBe Files.readAllBytes(fixture.descriptor).toVector
          val imageevidence = jaimage.evidence.asInstanceOf[CozyArticleMediaSiteBinding.InfographicEvidence].destination
          imageevidence.path shouldBe fixture.profileroot.resolve("images/summary-ja.png").toAbsolutePath.normalize()
          imageevidence.identity shouldBe imageevidence.path
          imageevidence.sha256 shouldBe _sha256(imageevidence.path)
          imageevidence.size shouldBe Files.size(imageevidence.path)
          imageevidence.fileKey should not be null
          val videoevidence = javideo.evidence.asInstanceOf[CozyArticleMediaSiteBinding.VideoEvidence].production
          videoevidence.path shouldBe fixture.root.resolve("video/ja/production.json").toAbsolutePath.normalize()
          videoevidence.identity shouldBe videoevidence.path
          videoevidence.sha256 shouldBe _sha256(videoevidence.path)
          videoevidence.size shouldBe Files.size(videoevidence.path)
          videoevidence.fileKey should not be null

          When("the unchanged plan is revalidated from its original admitted paths")
          val revalidated = CozyArticleMediaSiteBinding.revalidate(plan)

          Then("the complete immutable evidence plan remains unchanged")
          revalidated shouldBe plan
        }
      }
    }

    "select declared Part 5 resources only" which {
      "narrow to one exact opted-in target without enrolling an unbound resource" in {
        _with_part5_fixture("target") { fixture =>
          Given("a Part 5 descriptor containing one unbound resource")

          When("an exact opted-in target is requested")
          val plan = CozyArticleMediaSiteBinding.plan(CozyArticleMediaSiteBinding.Config(
            fixture.descriptor,
            target = Some("part-5-video-ja")
          ))

          Then("only that declared resource is selected")
          plan.candidates.map(_.resourceId) shouldBe Vector("part-5-video-ja")
          plan.candidates.head.role shouldBe CozyArticleMediaSiteBinding.Role.Video
          plan.descriptor.resources.find(_.id == "part-5-unbound").flatMap(_.articleMedia) shouldBe empty
        }
      }

      "reject missing association and invalid selection boundaries" in {
        Given("separate Part 5 descriptor declarations without a usable site candidate set")

        When("the binding planner receives missing association or zero declarations")
        val associationfailure = _part5_plan_failure("missing-association", _part5_yaml(association = ""))
        val zerofailure = _part5_plan_failure("zero-candidates", _part5_yaml(
          summaryjabinding = "",
          summaryenbinding = "",
          videojabinding = "",
          videoenbinding = ""
        ))
        val nonexistentfailure = _with_part5_fixture("nonexistent-target") { fixture =>
          _failure(CozyArticleMediaSiteBinding.plan(CozyArticleMediaSiteBinding.Config(
            fixture.descriptor,
            target = Some("part-5-missing")
          )))
        }
        val unboundfailure = _with_part5_fixture("unbound-target") { fixture =>
          _failure(CozyArticleMediaSiteBinding.plan(CozyArticleMediaSiteBinding.Config(
            fixture.descriptor,
            target = Some("part-5-unbound")
          )))
        }

        Then("a target cannot infer or create a registration association")
        associationfailure.getMessage should include("top-level articleMedia")
        zerofailure.getMessage should include("at least one")
        nonexistentfailure.getMessage should include("does not exist")
        unboundfailure.getMessage should include("has no articleMedia")
      }
    }

    "enforce exact Part 5 registration identity keys" which {
      "reject missing or noncanonical locales and duplicate locale-role candidates" in {
        Given("Part 5 descriptors with invalid candidate locale declarations")

        When("the binding planner normalizes the article identity and candidate keys")
        val noncanonical = _part5_plan_failure(
          "noncanonical-locale",
          _part5_yaml().replace("language: ja", "language: ja-jp")
        )
        val missing = _part5_plan_failure(
          "missing-locale",
          _part5_yaml().replace("    language: ja\n", "")
        )
        val duplicate = _part5_plan_failure(
          "duplicate-locale-role",
          _part5_yaml().replace(
            "id: part-5-summary-en\n    kind: infographic\n    language: en",
            "id: part-5-summary-en\n    kind: infographic\n    language: ja"
          )
        )

        Then("only exact canonical locale-role registrations are admitted")
        noncanonical.getMessage should include("locale")
        missing.getMessage should include("language")
        duplicate.getMessage should include("Duplicate")
      }
    }

    "require published Part 5 infographic evidence" which {
      "reject missing selected profile mappings, unsafe declarations, and non-direct destinations" in {
        Given("Part 5 infographic declarations with independent evidence failures")

        When("the read-only planner resolves only selected profile publication evidence")
        val missingmapping = _part5_plan_failure(
          "missing-publication-mapping",
          _part5_yaml(summaryjapublication = None)
        )
        val missingprofile = _part5_plan_failure(
          "missing-profile",
          _part5_yaml().replace("publicationProfile: site", "publicationProfile: missing")
        )
        val escape = _part5_plan_failure(
          "publication-escape",
          _part5_yaml().replace("site: images/summary-ja.png", "site: ../summary-ja.png")
        )
        val invalidpublicpath = _part5_plan_failure(
          "invalid-public-path",
          _part5_yaml().replace(
            "publicPath: /ja/development-process/images/part-5/summary.png",
            "publicPath: https://example.com/summary.png"
          )
        )
        val missingdestination = _with_part5_fixture("missing-destination") { fixture =>
          Files.delete(fixture.profileroot.resolve("images/summary-ja.png"))
          _failure(CozyArticleMediaSiteBinding.plan(CozyArticleMediaSiteBinding.Config(fixture.descriptor)))
        }
        val symlinkdestination = _with_part5_fixture("symlink-destination") { fixture =>
          val destination = fixture.profileroot.resolve("images/summary-ja.png")
          val outside = fixture.root.resolve("outside-summary-ja.png")
          _write(outside, "outside")
          Files.delete(destination)
          Files.createSymbolicLink(destination, outside)
          _failure(CozyArticleMediaSiteBinding.plan(CozyArticleMediaSiteBinding.Config(fixture.descriptor)))
        }

        Then("no inferred, escaped, missing, or symlink-backed infographic can register")
        missingmapping.getMessage should include("no selected publication profile")
        missingprofile.getMessage should include("does not exist")
        escape.getMessage should include("normalized relative path")
        invalidpublicpath.getMessage should include("site-visible")
        missingdestination.getMessage should include("direct regular")
        symlinkdestination.getMessage should include("direct regular")
      }
    }

    "require accepted Part 5 video production evidence" which {
      "reject malformed production metadata, invalid lifecycle state, unsafe URLs, and symlinks" in {
        Given("Part 5 video production declarations outside the accepted evidence grammar")

        When("the planner parses the exact production file instead of a broad media scan")
        val malformed = _part5_production_failure("malformed-json", "{")
        val nonobject = _part5_production_failure("nonobject-json", "[]")
        val identity = _part5_production_failure("identity-mismatch", _part5_production_json(category = "other"))
        val language = _part5_production_failure("language-mismatch", _part5_production_json(language = "en"))
        val render = _part5_production_failure("render-mismatch", _part5_production_json(renderstatus = "pending"))
        val qa = _part5_production_failure("qa-mismatch", _part5_production_json(qastatus = "pending"))
        val youtube = _part5_production_failure("youtube-status-mismatch", _part5_production_json(youtubestatus = "pending"))
        val unsafeurls = Vector(
          "http://youtu.be/ja_Part5-1",
          "https://user@youtu.be/ja_Part5-1",
          "https://youtu.be/ja_Part5-1#fragment",
          "https://youtu.be:443/ja_Part5-1",
          "https://youtu.be/ja_Part5-1/extra",
          "https://youtu.be/ja_Part5-1?x=1",
          "https://www.youtube.com/watch?v=ja_Part5-1&x=1",
          "https://example.com/watch?v=ja_Part5-1"
        ).map(url => _part5_production_failure("unsafe-url-" + url.hashCode.abs, _part5_production_json(url = url)))
        val symlinkproduction = _with_part5_fixture("symlink-production") { fixture =>
          val production = fixture.root.resolve("video/ja/production.json")
          val outside = fixture.root.resolve("outside-production.json")
          _write(outside, _part5_production_json())
          Files.delete(production)
          Files.createSymbolicLink(production, outside)
          _failure(CozyArticleMediaSiteBinding.plan(CozyArticleMediaSiteBinding.Config(fixture.descriptor)))
        }

        Then("only completed technically-and-visually-approved published YouTube evidence is accepted")
        malformed.getMessage should include("JSON")
        nonobject.getMessage should include("JSON object")
        identity.getMessage should include("identity differs")
        language.getMessage should include("language differs")
        render.getMessage should include("render status")
        qa.getMessage should include("QA status")
        youtube.getMessage should include("YouTube status")
        unsafeurls.foreach(_.getMessage should include("URL"))
        symlinkproduction.getMessage should include("direct regular")
      }
    }

    "revalidate the complete Part 5 snapshot" which {
      "reject descriptor, profile, resource, infographic, and production drift" in {
        Given("prepared plans with their original descriptor and direct-file evidence")

        When("each plan is recomputed from exactly its admitted descriptor and evidence paths")
        val descriptordrift = _part5_revalidation_failure("descriptor-drift") { fixture =>
          _write(fixture.descriptor, _part5_yaml() + "# descriptor drift\n")
        }
        val profiledrift = _part5_revalidation_failure("profile-drift") { fixture =>
          Files.createDirectories(fixture.root.resolve("publication-alt"))
          _write(fixture.descriptor, _part5_yaml().replace("root: publication", "root: publication-alt"))
        }
        val resourcedrift = _part5_revalidation_failure("resource-drift") { fixture =>
          _write(fixture.descriptor, _part5_yaml().replace(
            "/ja/development-process/images/part-5/summary.png",
            "/ja/development-process/images/part-5/changed.png"
          ))
        }
        val infographicbytes = _part5_revalidation_failure("infographic-byte-drift") { fixture =>
          _write(fixture.profileroot.resolve("images/summary-ja.png"), "changed Part 5 image")
        }
        val infographicidentity = _part5_revalidation_failure("infographic-identity-drift") { fixture =>
          val destination = fixture.profileroot.resolve("images/summary-ja.png")
          val outside = fixture.root.resolve("replacement-summary-ja.png")
          _write(outside, Files.readString(destination))
          Files.delete(destination)
          Files.createSymbolicLink(destination, outside)
        }
          val productiondrift = _part5_revalidation_failure("production-drift") { fixture =>
            _write(fixture.root.resolve("video/ja/production.json"), _part5_production_json(url = "https://youtu.be/ja_Changed-3"))
          }
          val descriptorreplacement = _part5_revalidation_failure("descriptor-atomic-replacement") { fixture =>
            _atomic_replace(fixture.descriptor, _part5_yaml())
          }
          val infographicreplacement = _part5_revalidation_failure("infographic-atomic-replacement") { fixture =>
            _atomic_replace(fixture.profileroot.resolve("images/summary-ja.png"), "Part 5 summary ja")
          }
          val productionreplacement = _part5_revalidation_failure("production-atomic-replacement") { fixture =>
            _atomic_replace(fixture.root.resolve("video/ja/production.json"), _part5_production_json())
          }

        Then("any declaration, byte, identity, or production metadata change rejects revalidation")
        Vector(
          descriptordrift,
          profiledrift,
          resourcedrift,
          infographicbytes,
          infographicidentity,
          productiondrift,
          descriptorreplacement,
          infographicreplacement,
          productionreplacement
        ).foreach {
          _.getMessage should not be empty
        }
      }
    }
  }

  private def _candidate(plan: CozyArticleMediaSiteBinding.Plan, resourceid: String): CozyArticleMediaSiteBinding.Candidate =
    plan.candidates.find(_.resourceId == resourceid).getOrElse(throw new IllegalStateException(s"Missing Part 5 candidate: $resourceid"))

  private def _part5_plan_failure(name: String, yaml: String): RuntimeException =
    _with_part5_fixture(name, yaml) { fixture =>
      _failure(CozyArticleMediaSiteBinding.plan(CozyArticleMediaSiteBinding.Config(fixture.descriptor)))
    }

  private def _part5_production_failure(name: String, json: String): RuntimeException =
    _with_part5_fixture(name) { fixture =>
      _write(fixture.root.resolve("video/ja/production.json"), json)
      _failure(CozyArticleMediaSiteBinding.plan(CozyArticleMediaSiteBinding.Config(fixture.descriptor)))
    }

  private def _part5_revalidation_failure(name: String)(change: SiteBindingPart5Fixture.Data => Unit): RuntimeException =
    _with_part5_fixture(name) { fixture =>
      val plan = CozyArticleMediaSiteBinding.plan(CozyArticleMediaSiteBinding.Config(fixture.descriptor))
      change(fixture)
      _failure(CozyArticleMediaSiteBinding.revalidate(plan))
    }

  private def _failure(value: => Any): RuntimeException =
    intercept[RuntimeException](value)

  private def _with_part5_fixture[A](name: String, yaml: String = _part5_yaml())(f: SiteBindingPart5Fixture.Data => A): A = {
    val workroot = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve("target/cozy-article-media-site-binding/part-5")
    Files.createDirectories(workroot)
    val root = Files.createTempDirectory(workroot, name + "-")
    val descriptor = root.resolve("media.yaml")
    val profile = root.resolve("publication")
    try {
      _write(descriptor, yaml)
      _write(profile.resolve("images/summary-ja.png"), "Part 5 summary ja")
      _write(profile.resolve("images/summary-en.png"), "Part 5 summary en")
      _write(root.resolve("video/ja/production.json"), _part5_production_json())
      _write(root.resolve("video/en/production.json"), _part5_production_json(
        language = "en",
        url = "https://www.youtube.com/watch?v=en_Part5-2"
      ))
      f(new SiteBindingPart5Fixture.Data(root, descriptor, profile))
    } finally {
      _delete(root)
    }
  }

  private def _part5_yaml(
    association: String = "articleMedia:\n  articleIdentity: development-process/part-5\n  publicationProfile: site",
    summaryjabinding: String = _part5_infographic_binding("/ja/development-process/images/part-5/summary.png", "Part 5 summary"),
    summaryenbinding: String = _part5_infographic_binding("/en/development-process/images/part-5/summary.png", "Part 5 summary"),
    videojabinding: String = _part5_video_binding("video/ja/production.json"),
    videoenbinding: String = _part5_video_binding("video/en/production.json"),
    summaryjapublication: Option[String] = Some("images/summary-ja.png")
  ): String = {
    val summarypublication = summaryjapublication.map(value => s"    publications:\n      site: $value\n").getOrElse("")
    s"""schema: cozy.media.v1
       |knowledge:
       |  id: media-package/part-5
       |  source: knowledge/part-5.dox
       |languages:
       |  - ja
       |  - en
       |profiles:
       |  site:
       |    root: publication
       |$association
       |resources:
       |  - id: part-5-summary-ja
       |    kind: infographic
       |    language: ja
       |    role: article-summary
       |    source: input/summary-ja.png
       |    build: prebuilt
       |$summarypublication$summaryjabinding
       |  - id: part-5-summary-en
       |    kind: infographic
       |    language: en
       |    role: article-summary
       |    source: input/summary-en.png
       |    build: prebuilt
       |    publications:
       |      site: images/summary-en.png
       |$summaryenbinding
       |  - id: part-5-video-ja
       |    kind: video
       |    language: ja
       |    role: article-introduction
       |    source: target/video-ja.mp4
       |    build: prebuilt
       |$videojabinding
       |  - id: part-5-video-en
       |    kind: video
       |    language: en
       |    role: article-introduction
       |    source: target/video-en.mp4
       |    build: prebuilt
       |$videoenbinding
       |  - id: part-5-unbound
       |    kind: infographic
       |    language: ja
       |    role: article-summary
       |    source: input/unbound.png
       |    build: prebuilt
       |""".stripMargin
  }

  private def _part5_infographic_binding(publicpath: String, alt: String): String =
    s"""    articleMedia:
       |      role: infographic
       |      publicPath: $publicpath
       |      mediaType: image/png
       |      alt: $alt
       |""".stripMargin

  private def _part5_video_binding(production: String): String =
    s"""    articleMedia:
       |      role: video
       |      production: $production
       |""".stripMargin

  private def _part5_production_json(
    category: String = "development-process",
    article: String = "part-5",
    language: String = "ja",
    renderstatus: String = "completed",
    qastatus: String = "technical-and-visual-qa-passed",
    youtubestatus: String = "published",
    url: String = "https://youtu.be/ja_Part5-1"
  ): String =
    s"""{
       |  "category": "$category",
       |  "article": "$article",
       |  "language": "$language",
       |  "extra": {"accepted": true},
       |  "render": {
       |    "status": "$renderstatus",
       |    "qa": {"status": "$qastatus"},
       |    "listeningReview": {"status": "pending"}
       |  },
       |  "youtube": {"status": "$youtubestatus", "videoUrl": "$url"}
       |}
       |""".stripMargin

  private def _write(path: Path, value: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, value, StandardCharsets.UTF_8)
  }

  private def _atomic_replace(path: Path, value: String): Unit = {
    val replacement = path.resolveSibling(path.getFileName.toString + ".replacement")
    _write(replacement, value)
    Files.move(replacement, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
  }

  private def _sha256(path: Path): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(Files.readAllBytes(path))
    digest.digest().map(x => f"${x & 0xff}%02x").mkString
  }

  private def _delete(path: Path): Unit = {
    if (Files.exists(path)) {
      val paths = Files.walk(path).iterator().asScala.toVector.sortBy(_.getNameCount).reverse
      paths.foreach(Files.deleteIfExists(_))
    }
  }
}
