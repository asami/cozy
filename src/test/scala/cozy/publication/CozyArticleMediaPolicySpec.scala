package cozy.publication

import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import scala.collection.JavaConverters._
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.smartdox.metadata.PublishMetadata
import org.smartdox.metadata.PublishMetadata.{ImageReference, VideoPresentation, VideoReference, VideoStatus}
import play.api.libs.json.{JsObject, Json}

/*
 * @since   Aug.  4, 2026
 * @version Aug.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyArticleMediaPolicySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "CozyArticleMediaPolicy" should {
    "preserve ordinary metadata" which {
      "leave media-free metadata unchanged in Preview and Production" in {
        Given("a configured metadata bundle without article-media entries")

        When("both policy strategies evaluate it")
        val results = _with_metadata(Vector.empty) { metadata =>
          Vector(
            _evaluate(CozyArticleMediaPolicy.Strategy.Preview, metadata, Vector.empty),
            _evaluate(CozyArticleMediaPolicy.Strategy.Production, metadata, Vector.empty)
          )
        }

        Then("both decisions retain the metadata and have no policy output")
        results.foreach { result =>
          result.structural.media shouldBe empty
          result.stagedCorrelations shouldBe empty
          result.omittedKeys shouldBe empty
          result.diagnostics shouldBe empty
        }
      }

      "retain an absent exact locale without cross-locale fallback" in {
        Given("a Japanese-only native infographic with a published Cozy record")
        val publication = _publication("development-process/example", "ja", Some(_image("/ja/development-process/images/example.png")), None)
        val integrity = _infographic_integrity("development-process/example", "ja", "/ja/development-process/images/example.png")

        When("Preview evaluates the native metadata")
        val result = _with_metadata(Vector(publication))(metadata => _evaluate(CozyArticleMediaPolicy.Strategy.Preview, metadata, Vector(integrity)))

        Then("only the exact Japanese key is staged")
        result.structural.resolve("development-process/example", "en") shouldBe empty
        result.stagedCorrelations.map(_.key.locale) shouldBe Vector("ja")
      }
    }

    "admit projectable media" which {
      "omit an optional infographic without an integrity record in both strategies" in {
        Given("a native infographic with no registered Cozy integrity")
        val publication = _publication("development-process/example", "ja", Some(_image("/ja/development-process/images/example.png")), None)

        When("both strategies evaluate the optional association")
        val results = _with_metadata(Vector(publication)) { metadata =>
          Vector(
            _evaluate(CozyArticleMediaPolicy.Strategy.Preview, metadata, Vector.empty),
            _evaluate(CozyArticleMediaPolicy.Strategy.Production, metadata, Vector.empty)
          )
        }

        Then("Preview reports missing optional media while Production omits it without a diagnostic")
        results.head.omittedKeys.map(_.role) shouldBe Vector(CozyArticleMediaIntegrity.Role.Infographic)
        results.head.stagedCorrelations shouldBe empty
        results.head.diagnostics.map(x => (x.kind, x.publicationState)) shouldBe Vector(
          (CozyArticleMediaPolicy.DiagnosticKind.MissingMedia, None)
        )
        results(1).omittedKeys.map(_.role) shouldBe Vector(CozyArticleMediaIntegrity.Role.Infographic)
        results(1).stagedCorrelations shouldBe empty
        results(1).diagnostics shouldBe empty
      }

      "stage published infographic and published site-hosted video" in {
        Given("one exact-locale native infographic and video with published Cozy integrity")
        val publication = _publication(
          "development-process/example",
          "ja",
          Some(_image("/ja/development-process/images/example.png")),
          Some(_site_video(VideoStatus.Published, "/repository/video/example.mp4"))
        )
        val integrities = Vector(
          _video_integrity("development-process/example", "ja", "/repository/video/example.mp4"),
          _infographic_integrity("development-process/example", "ja", "/ja/development-process/images/example.png")
        )

        When("Production evaluates both exact associations")
        val result = _with_metadata(Vector(publication))(metadata => _evaluate(CozyArticleMediaPolicy.Strategy.Production, metadata, integrities))

        Then("both roles are admitted in canonical key order")
        result.stagedCorrelations.map(_.key.role) shouldBe Vector(
          CozyArticleMediaIntegrity.Role.Infographic,
          CozyArticleMediaIntegrity.Role.Video
        )
        result.omittedKeys shouldBe empty
        result.diagnostics shouldBe empty
      }

      "diagnose and omit every registered or withdrawn projectable role in Preview, while Production fails" in {
        Given("each projectable role backed by registered or withdrawn Cozy integrity")
        val cases = Vector(
          CozyArticleMediaIntegrity.Role.Infographic -> CozyArticleMediaIntegrity.PublicationState.Registered,
          CozyArticleMediaIntegrity.Role.Infographic -> CozyArticleMediaIntegrity.PublicationState.Withdrawn,
          CozyArticleMediaIntegrity.Role.Video -> CozyArticleMediaIntegrity.PublicationState.Registered,
          CozyArticleMediaIntegrity.Role.Video -> CozyArticleMediaIntegrity.PublicationState.Withdrawn
        )

        When("Preview and Production evaluate every unavailable projectable association")
        val observations = cases.map { case (role, state) =>
          val publication = _publication_for(role)
          val integrity = _integrity_for(role, state)
          _with_metadata(Vector(publication)) { metadata =>
            val preview = _evaluate(CozyArticleMediaPolicy.Strategy.Preview, metadata, Vector(integrity))
            val production = intercept[IllegalArgumentException](_evaluate(CozyArticleMediaPolicy.Strategy.Production, metadata, Vector(integrity)))
            (role, state, preview, production)
          }
        }

        Then("Preview is deterministic diagnostic omission and Production rejects before staging")
        observations.foreach { case (role, state, preview, production) =>
          preview.stagedCorrelations shouldBe empty
          preview.omittedKeys.map(_.role) shouldBe Vector(role)
          preview.diagnostics.map(x => (x.kind, x.publicationState, x.key.role)) shouldBe Vector(
            (CozyArticleMediaPolicy.DiagnosticKind.UnavailableMedia, Some(state), role)
          )
          production.getMessage should include("requires published Cozy integrity")
        }
      }

      "diagnose registered and withdrawn records without requiring their missing artifacts in Preview" in {
        Given("path-bearing projectable video records whose configured repository artifact is intentionally absent")
        val publication = _publication("development-process/example", "ja", None, Some(_site_video(VideoStatus.Published, "/repository/video/example.mp4")))
        val states = Vector(
          CozyArticleMediaIntegrity.PublicationState.Registered,
          CozyArticleMediaIntegrity.PublicationState.Withdrawn
        )

        When("Preview and Production inspect the unavailable evidence against an empty configured artifact root")
        val results = _with_metadata(Vector(publication)) { metadata =>
          _with_root { root =>
            states.map { state =>
              val integrity = _video_integrity("development-process/example", "ja", "/repository/video/example.mp4", state)
              CozyArticleMediaPolicy.evaluate(CozyArticleMediaPolicy.Strategy.Preview, metadata, Vector(integrity), root) ->
                intercept[IllegalArgumentException](CozyArticleMediaPolicy.evaluate(CozyArticleMediaPolicy.Strategy.Production, metadata, Vector(integrity), root))
            }
          }
        }

        Then("Preview omits with typed unavailable diagnostics while Production fails before staging")
        results.map(_._1.diagnostics.head.publicationState) shouldBe states.map(x => Some(x))
        results.foreach { case (_, error) => error.getMessage should include("requires published Cozy integrity") }
      }
    }

    "pass through nonprojectable media" which {
      "accept URL-less draft and withdrawn site-hosted forms without correlation or staging" in {
        Given("URL-less draft and withdrawn site-hosted video variants")
        val draft = _publication("development-process/draft", "ja", None, Some(VideoReference(VideoPresentation.SiteHosted, VideoStatus.Draft)))
        val withdrawn = _publication("development-process/withdrawn", "ja", None, Some(VideoReference(VideoPresentation.SiteHosted, VideoStatus.Withdrawn)))

        When("both strategies inspect those forms")
        val results = _with_metadata(Vector(draft, withdrawn)) { metadata =>
          Vector(
            _evaluate(CozyArticleMediaPolicy.Strategy.Preview, metadata, Vector.empty),
            _evaluate(CozyArticleMediaPolicy.Strategy.Production, metadata, Vector.empty)
          )
        }

        Then("they remain accepted pass-through forms")
        results.foreach { result =>
          result.structural.media shouldBe empty
          result.stagedCorrelations shouldBe empty
          result.omittedKeys shouldBe empty
          result.diagnostics shouldBe empty
        }
      }

      "accept a watch-only external video without integrity or staging" in {
        Given("a published external-link video with only a watch URL")
        val publication = _publication("development-process/example", "ja", None, Some(
          VideoReference(VideoPresentation.ExternalLink, VideoStatus.Published, None, Some(new URI("https://example.com/watch")), None)
        ))

        When("Preview evaluates the external form")
        val result = _with_metadata(Vector(publication))(metadata => _evaluate(CozyArticleMediaPolicy.Strategy.Preview, metadata, Vector.empty))

        Then("no Cozy integrity association is invented")
        result.structural.media shouldBe empty
        result.stagedCorrelations shouldBe empty
        result.diagnostics shouldBe empty
      }

      "retain path-bearing draft and withdrawn correlations without staging or unavailable diagnostics" in {
        Given("path-bearing draft and withdrawn videos with exact integrity records")
        val draft = _publication("development-process/draft", "ja", None, Some(_site_video(VideoStatus.Draft, "/repository/video/draft.mp4")))
        val withdrawn = _publication("development-process/withdrawn", "ja", None, Some(_site_video(VideoStatus.Withdrawn, "/repository/video/withdrawn.mp4")))
        val integrities = Vector(
          _video_integrity("development-process/draft", "ja", "/repository/video/draft.mp4", CozyArticleMediaIntegrity.PublicationState.Registered),
          _video_integrity("development-process/withdrawn", "ja", "/repository/video/withdrawn.mp4", CozyArticleMediaIntegrity.PublicationState.Withdrawn)
        )

        When("both strategies separate structural correlation from production admission")
        val results = _with_metadata(Vector(draft, withdrawn)) { metadata =>
          Vector(
            _evaluate(CozyArticleMediaPolicy.Strategy.Preview, metadata, integrities),
            _evaluate(CozyArticleMediaPolicy.Strategy.Production, metadata, integrities)
          )
        }

        Then("the correlations are retained as nonprojectable without staged or diagnostic output")
        results.foreach { result =>
          result.structural.media.map(x => (x.key.articleIdentity, x.integrity.map(_.record.publicationState), x.projectable)) shouldBe Vector(
            ("development-process/draft", Some(CozyArticleMediaIntegrity.PublicationState.Registered), false),
            ("development-process/withdrawn", Some(CozyArticleMediaIntegrity.PublicationState.Withdrawn), false)
          )
          result.stagedCorrelations shouldBe empty
          result.omittedKeys shouldBe empty
          result.diagnostics shouldBe empty
        }
      }
    }

    "retain hard structural failures" which {
      "retain canonicality and escaping paths as hard failures while diagnosing unavailable Published artifacts in Preview" in {
        Given("a published native video with a canonical record, a forged result, and isolated configured artifact roots")
        val publication = _publication("development-process/example", "ja", None, Some(_site_video(VideoStatus.Published, "/repository/video/example.mp4")))
        val integrity = _video_integrity("development-process/example", "ja", "/repository/video/example.mp4")
        val forged = integrity.copy(metadata = Json.obj("forged" -> true))
        val strategies = Vector(CozyArticleMediaPolicy.Strategy.Preview, CozyArticleMediaPolicy.Strategy.Production)

        When("each strategy evaluates forged and escaping inputs while Preview evaluates missing and stale artifacts")
        val observations = _with_metadata(Vector(publication)) { metadata =>
          val forgederrors = strategies.map(strategy => intercept[IllegalArgumentException](_evaluate(strategy, metadata, Vector(forged))))
          _with_root { root =>
            val missingpreview = CozyArticleMediaPolicy.evaluate(CozyArticleMediaPolicy.Strategy.Preview, metadata, Vector(integrity), root)
            val missingproduction = intercept[IllegalArgumentException](CozyArticleMediaPolicy.evaluate(CozyArticleMediaPolicy.Strategy.Production, metadata, Vector(integrity), root))
            _write_artifact(root.resolve("video/example.mp4"), "mismatched".getBytes(StandardCharsets.UTF_8))
            val stalepreview = CozyArticleMediaPolicy.evaluate(CozyArticleMediaPolicy.Strategy.Preview, metadata, Vector(integrity), root)
            val staleproduction = intercept[IllegalArgumentException](CozyArticleMediaPolicy.evaluate(CozyArticleMediaPolicy.Strategy.Production, metadata, Vector(integrity), root))
            val external = Files.createTempFile(root.getParent, "external-", ".mp4")
            try {
              Files.delete(root.resolve("video/example.mp4"))
              Files.createSymbolicLink(root.resolve("video/example.mp4"), external)
              val escaped = strategies.map(strategy => intercept[IllegalArgumentException](CozyArticleMediaPolicy.evaluate(strategy, metadata, Vector(integrity), root)))
              (forgederrors, missingpreview, missingproduction, stalepreview, staleproduction, escaped)
            } finally {
              Files.deleteIfExists(external)
            }
          }
        }

        Then("canonicality and real-path escape remain hard while Preview omits missing and stale Published media")
        observations._1 should have size 2
        observations._2.stagedCorrelations shouldBe empty
        observations._2.diagnostics.map(x => (x.kind, x.publicationState)) shouldBe Vector(
          (CozyArticleMediaPolicy.DiagnosticKind.UnavailableMedia, Some(CozyArticleMediaIntegrity.PublicationState.Published))
        )
        observations._3.getMessage should include("published artifact")
        observations._4.diagnostics.map(_.publicationState) shouldBe Vector(Some(CozyArticleMediaIntegrity.PublicationState.Published))
        observations._5.getMessage should include("SHA-256")
        observations._6 should have size 2
        (observations._1 ++ observations._6).map(_.getMessage).mkString(" ") should include("canonical")
      }

      "reject a path mismatch under both strategies instead of downgrading it to diagnostics" in {
        Given("a path-bearing native video with an exact key but different integrity public path")
        val publication = _publication("development-process/example", "ja", None, Some(_site_video(VideoStatus.Published, "/repository/video/example.mp4")))
        val integrity = _video_integrity("development-process/example", "ja", "/repository/video/other.mp4")

        When("Preview and Production inspect the structural association")
        val errors = _with_metadata(Vector(publication)) { metadata =>
          Vector(
            intercept[IllegalArgumentException](_evaluate(CozyArticleMediaPolicy.Strategy.Preview, metadata, Vector(integrity))),
            intercept[IllegalArgumentException](_evaluate(CozyArticleMediaPolicy.Strategy.Production, metadata, Vector(integrity)))
          )
        }

        Then("both strategies fail at the association boundary")
        errors.map(_.getMessage).foreach(_ should include("public path does not match integrity"))
      }

      "retain an existing non-regular published artifact as a hard failure in Preview and Production" in {
        Given("a published native video whose configured artifact path is an existing directory")
        val publication = _publication("development-process/example", "ja", None, Some(_site_video(VideoStatus.Published, "/repository/video/example.mp4")))
        val integrity = _video_integrity("development-process/example", "ja", "/repository/video/example.mp4")

        When("both strategies inspect the existing non-regular artifact")
        val errors = _with_metadata(Vector(publication)) { metadata =>
          _with_root { root =>
            Files.createDirectories(root.resolve("video/example.mp4"))
            Vector(
              intercept[IllegalArgumentException](CozyArticleMediaPolicy.evaluate(CozyArticleMediaPolicy.Strategy.Preview, metadata, Vector(integrity), root)),
              intercept[IllegalArgumentException](CozyArticleMediaPolicy.evaluate(CozyArticleMediaPolicy.Strategy.Production, metadata, Vector(integrity), root))
            )
          }
        }

        Then("neither strategy downgrades an existing directory to an unavailable-media omission")
        errors.map(_.getMessage).mkString(" ") should include("regular file")
      }

      "retain a POSIX-unreadable published artifact as a hard failure when POSIX permissions are supported" in {
        Given("a published native video whose regular artifact has no POSIX read permission")
        val publication = _publication("development-process/example", "ja", None, Some(_site_video(VideoStatus.Published, "/repository/video/example.mp4")))
        val integrity = _video_integrity("development-process/example", "ja", "/repository/video/example.mp4")

        When("Preview and Production open the unreadable configured artifact")
        val errors = _with_metadata(Vector(publication)) { metadata =>
          _with_root { root =>
            val artifact = root.resolve("video/example.mp4")
            _write_artifact(artifact, _artifact_bytes)
            if (!Files.getFileStore(artifact).supportsFileAttributeView("posix"))
              cancel("The filesystem does not support POSIX permission specifications")
            val original = Files.getPosixFilePermissions(artifact)
            Files.setPosixFilePermissions(artifact, PosixFilePermissions.fromString("---------"))
            try {
              Vector(
                intercept[Exception](CozyArticleMediaPolicy.evaluate(CozyArticleMediaPolicy.Strategy.Preview, metadata, Vector(integrity), root)),
                intercept[Exception](CozyArticleMediaPolicy.evaluate(CozyArticleMediaPolicy.Strategy.Production, metadata, Vector(integrity), root))
              )
            } finally {
              Files.setPosixFilePermissions(artifact, original)
            }
          }
        }

        Then("neither strategy converts a permission failure into an unavailable-media diagnostic")
        errors should have size 2
      }
    }

    "order policy results deterministically" which {
      "keep staged correlations, omitted keys, and diagnostics stable for shuffled integrity inputs" in {
        Given("three projectable records with published and unavailable integrity states")
        val publications = Vector(
          _publication("development-process/alpha", "en", Some(_image("/en/development-process/images/alpha.png")), None),
          _publication("development-process/beta", "ja", None, Some(_site_video(VideoStatus.Published, "/repository/video/beta.mp4"))),
          _publication("development-process/gamma", "en", Some(_image("/en/development-process/images/gamma.png")), None)
        )
        val integrities = Vector(
          _infographic_integrity("development-process/alpha", "en", "/en/development-process/images/alpha.png"),
          _video_integrity("development-process/beta", "ja", "/repository/video/beta.mp4", CozyArticleMediaIntegrity.PublicationState.Registered),
          _infographic_integrity("development-process/gamma", "en", "/en/development-process/images/gamma.png", CozyArticleMediaIntegrity.PublicationState.Withdrawn)
        )
        val property = Prop.forAll(Gen.oneOf(integrities.permutations.toVector)) { shuffled =>
          _with_metadata(publications) { metadata =>
            val result = _evaluate(CozyArticleMediaPolicy.Strategy.Preview, metadata, shuffled)
            result.stagedCorrelations.map(_.key.articleIdentity) == Vector("development-process/alpha") &&
              result.omittedKeys.map(_.articleIdentity) == Vector("development-process/beta", "development-process/gamma") &&
              result.diagnostics.map(_.key.articleIdentity) == Vector("development-process/beta", "development-process/gamma")
          }
        }

        When("ScalaCheck evaluates every generated integrity ordering")
        val propertyresult = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

        Then("canonical policy output is independent of collection ordering")
        propertyresult.passed shouldBe true
      }
    }

    "register explicit evidence" which {
      "upsert exactly one configured bundle with strict article, infographic, and video integrity paths" in {
        Given("one existing configured bundle and actual explicit video and infographic projector fixtures")
        _with_root { root =>
          _write_bundle(root, "publication", _video_provenance(root))
          Files.createDirectories(root.resolve("target/unrelated"))
          Files.write(root.resolve("target/unrelated/decoy.json"), "not json".getBytes(StandardCharsets.UTF_8))
          val infographic = _infographic_input(root)
          val publication = _publication(
            "development-process/example",
            "ja",
            Some(_image("/images/development-process/example-ja.png")),
            Some(_site_video(VideoStatus.Published, "/repository/video/example-video/1.0.0/example-video-1.0.0.mp4"))
          )

          When("the policy reloads configured provenance and projects the typed requests itself")
          val result = CozyArticleMediaPolicy.register(root, root, "publication", publication, Vector(_video_registration), Vector(infographic))

          Then("the exact projector-derived entry paths are written without discovering the unrelated decoy")
          result.upsertResult.entryPaths shouldBe Vector(
            "metadata/article-media-integrity/development-process/example/ja/infographic.json",
            "metadata/article-media-integrity/development-process/example/ja/video.json",
            "metadata/article-media/development-process/example.json"
          )
          result.videoEvidence.map(_.integrity.record.role) shouldBe Vector(CozyArticleMediaIntegrity.Role.Video)
          result.infographicEvidence.map(_.integrity.record.role) shouldBe Vector(CozyArticleMediaIntegrity.Role.Infographic)
          result.upsertResult.snapshot.entries.map(_.path) shouldBe Vector(
            "metadata/article-media-integrity/development-process/example/ja/infographic.json",
            "metadata/article-media-integrity/development-process/example/ja/video.json",
            "metadata/article-media/development-process/example.json",
            "metadata/artifacts/repository/example-video.json",
            "metadata/video/example-video/1.0.0/manifest.json"
          )
        }
      }

      "project video provenance from bundle B while atomically registering the article only in bundle A" in {
        Given("target bundle A, separate video-manifest bundle B, and explicit infographic evidence")
        _with_root { root =>
          _write_bundle(root, "alpha", Vector.empty)
          _write_bundle(root, "beta", _video_provenance(root))
          val infographic = _infographic_input(root)
          val publication = _publication(
            "development-process/example",
            "ja",
            Some(_image("/images/development-process/example-ja.png")),
            Some(_site_video(VideoStatus.Published, "/repository/video/example-video/1.0.0/example-video-1.0.0.mp4"))
          )
          val betabefore = Files.readAllBytes(root.resolve("beta.json")).toVector

          When("policy registration projects configured provenance from the complete two-bundle snapshot")
          val result = CozyArticleMediaPolicy.register(root, root, "alpha", publication, Vector(_video_registration), Vector(infographic))

          Then("article metadata is added only to A while B remains the provenance owner")
          result.upsertResult.bundlePath shouldBe root.resolve("alpha.json")
          CozyArticleMediaRegistry.load(root).entries.filter(_.bundleName == "alpha").map(_.path) should contain("metadata/article-media/development-process/example.json")
          Files.readAllBytes(root.resolve("beta.json")).toVector shouldBe betabefore
        }
      }

      "reject missing or changed configured provenance before mutating the configured bundle" in {
        Given("a canonical publication, typed projection requests, and a byte snapshot of the configured bundle")
        _with_root { root =>
          _write_bundle(root, "publication", _video_provenance(root))
          val infographic = _infographic_input(root)
          val publication = _publication(
            "development-process/example",
            "ja",
            Some(_image("/images/development-process/example-ja.png")),
            Some(_site_video(VideoStatus.Published, "/repository/video/example-video/1.0.0/example-video-1.0.0.mp4"))
          )
          val mismatched = _publication(
            "development-process/example",
            "ja",
            Some(_image("/images/development-process/other.png")),
            Some(_site_video(VideoStatus.Published, "/repository/video/example-video/1.0.0/example-video-1.0.0.mp4"))
          )
          val before = Files.readAllBytes(root.resolve("publication.json"))
          val invalids = Vector[() => Unit](
            () => CozyArticleMediaPolicy.register(root, root, "publication", publication, Vector(CozyArticleMediaPolicy.VideoRegistration("development-process/example", "ja", "missing", "1.0.0")), Vector(infographic)),
            () => CozyArticleMediaPolicy.register(root, root, "publication,", publication, Vector(_video_registration), Vector(infographic)),
            () => CozyArticleMediaPolicy.register(root, root, "publication", publication, Vector.empty, Vector(infographic)),
            () => CozyArticleMediaPolicy.register(root, root, "publication", publication, Vector(_video_registration), Vector(infographic.copy(buildManifest = root.resolve("project/target/cozy-media/missing.json")))),
            () => CozyArticleMediaPolicy.register(root, root, "publication", publication, Vector(_video_registration), Vector(infographic.copy(articleIdentity = "development-process/other"))),
            () => CozyArticleMediaPolicy.register(root, root, "publication", publication, Vector(_video_registration), Vector(infographic, infographic)),
            () => CozyArticleMediaPolicy.register(root, root, "publication", mismatched, Vector(_video_registration), Vector(infographic))
          )

          When("missing, malformed, duplicate, or mismatched configured provenance is projected")
          val errors = invalids.map(x => intercept[IllegalArgumentException](x()))
          val after = Files.readAllBytes(root.resolve("publication.json"))

          Then("every rejected provenance admission leaves the configured bundle byte-identical before the sole permitted upsert")
          errors should have size invalids.size
          after shouldBe before
        }
      }

      "leave the bundle unchanged for missing or changed video and infographic configured provenance" in {
        Given("independent configured video registry and explicit infographic descriptor fixtures")
        val changes = Vector[(Path, CozyArticleMediaInfographicEvidence.Input) => Unit](
          (_, _) => (),
          (root, _) => _write_artifact(root.resolve("video/example-video/1.0.0/example-video-1.0.0.mp4"), "changed video bytes".getBytes(StandardCharsets.UTF_8)),
          (_, input) => Files.delete(input.descriptorFile),
          (_, input) => _write_artifact(input.buildManifest, "{}".getBytes(StandardCharsets.UTF_8))
        )

        When("each configured provenance source is missing or changed before registration")
        val observations = changes.zipWithIndex.map { case (change, index) =>
          _with_root { root =>
            _write_bundle(root, "publication", _video_provenance(root))
            val infographic = _infographic_input(root)
            val publication = _publication(
              "development-process/example",
              "ja",
              Some(_image("/images/development-process/example-ja.png")),
              Some(_site_video(VideoStatus.Published, "/repository/video/example-video/1.0.0/example-video-1.0.0.mp4"))
            )
            val before = Files.readAllBytes(root.resolve("publication.json"))
            change(root, infographic)
            val request = if (index == 0) CozyArticleMediaPolicy.VideoRegistration("development-process/example", "ja", "missing", "1.0.0") else _video_registration
            val error = intercept[IllegalArgumentException](CozyArticleMediaPolicy.register(root, root, "publication", publication, Vector(request), Vector(infographic)))
            error.getMessage -> (Files.readAllBytes(root.resolve("publication.json")) shouldBe before)
          }
        }

        Then("all projector failures happen before the one permitted bundle mutation")
        observations should have size 4
        observations.map(_._1).foreach(_ should not be empty)
      }

      "reject invalid policy and registration boundaries deterministically" in {
        Given("valid metadata and a configured bundle with malformed policy inputs")
        _with_root { root =>
          _write_bundle(root, "publication", Vector.empty)
          val publication = _publication("development-process/example", "ja", None, Some(_site_video(VideoStatus.Published, "/repository/video/example.mp4")))
          val integrity = _video_integrity("development-process/example", "ja", "/repository/video/example.mp4")
          val invalids = _with_metadata(Vector(publication)) { metadata =>
            Vector[() => Unit](
              () => _evaluate(null, metadata, Vector(integrity)),
              () => CozyArticleMediaPolicy.register(null, root, "publication", publication, Vector.empty, Vector.empty),
              () => CozyArticleMediaPolicy.register(root, null, "publication", publication, Vector.empty, Vector.empty),
              () => CozyArticleMediaPolicy.register(root, root, "publication", publication, null, Vector.empty),
              () => CozyArticleMediaPolicy.register(root, root, "publication", publication, Vector(null), Vector.empty),
              () => CozyArticleMediaPolicy.register(root, root, "publication", publication, Vector.empty, Vector(null))
            )
          }

          When("the policy normalizes strategy and evidence boundaries")
          val errors = invalids.map(x => intercept[IllegalArgumentException](x()))

          Then("invalid strategy, roots, collections, and null request elements fail before registration")
          errors.map(_.getMessage).mkString(" ") should include("policy")
          errors.map(_.getMessage).mkString(" ") should include("defined")
        }
      }
    }
  }

  private def _publication(
    articleidentity: String,
    locale: String,
    infographic: Option[ImageReference],
    video: Option[VideoReference]
  ): CozyArticleMediaPublication.Result =
    CozyArticleMediaPublication.produce(articleidentity, Vector(CozyArticleMediaPublication.Variant(locale, infographic, video)))

  private def _publication_for(role: CozyArticleMediaIntegrity.Role): CozyArticleMediaPublication.Result =
    role match {
      case CozyArticleMediaIntegrity.Role.Infographic =>
        _publication("development-process/example", "ja", Some(_image("/ja/development-process/images/example.png")), None)
      case CozyArticleMediaIntegrity.Role.Video =>
        _publication("development-process/example", "ja", None, Some(_site_video(VideoStatus.Published, "/repository/video/example.mp4")))
      case _ => throw new IllegalArgumentException("Invalid test article-media role")
    }

  private def _integrity_for(
    role: CozyArticleMediaIntegrity.Role,
    state: CozyArticleMediaIntegrity.PublicationState
  ): CozyArticleMediaIntegrity.Result =
    role match {
      case CozyArticleMediaIntegrity.Role.Infographic =>
        _infographic_integrity("development-process/example", "ja", "/ja/development-process/images/example.png", state)
      case CozyArticleMediaIntegrity.Role.Video =>
        _video_integrity("development-process/example", "ja", "/repository/video/example.mp4", state)
      case _ => throw new IllegalArgumentException("Invalid test article-media role")
    }

  private def _image(publicpath: String): ImageReference =
    ImageReference(new URI(publicpath), Some("image/png"), Some("Example infographic"))

  private def _site_video(status: VideoStatus, publicpath: String): VideoReference =
    VideoReference(VideoPresentation.SiteHosted, status, None, None, Some(new URI(publicpath)))

  private def _video_integrity(
    articleidentity: String,
    locale: String,
    publicpath: String,
    publicationstate: CozyArticleMediaIntegrity.PublicationState = CozyArticleMediaIntegrity.PublicationState.Published
  ): CozyArticleMediaIntegrity.Result =
    CozyArticleMediaIntegrity.produce(CozyArticleMediaIntegrity.Input(
      articleIdentity = articleidentity,
      locale = locale,
      role = CozyArticleMediaIntegrity.Role.Video,
      artifact = CozyArticleMediaIntegrity.Artifact("example-video", "1.0.0"),
      publicPath = new URI(publicpath),
      repositoryPath = "video/example.mp4",
      mediaType = "video/mp4",
      sha256 = _artifact_sha256,
      provenance = CozyArticleMediaIntegrity.VideoPublication("metadata/video/example/1.0.0/manifest.json", "metadata/artifacts/repository/example.json"),
      publicationState = publicationstate
    ))

  private def _infographic_integrity(
    articleidentity: String,
    locale: String,
    publicpath: String,
    publicationstate: CozyArticleMediaIntegrity.PublicationState = CozyArticleMediaIntegrity.PublicationState.Published
  ): CozyArticleMediaIntegrity.Result =
    CozyArticleMediaIntegrity.produce(CozyArticleMediaIntegrity.Input(
      articleIdentity = articleidentity,
      locale = locale,
      role = CozyArticleMediaIntegrity.Role.Infographic,
      artifact = CozyArticleMediaIntegrity.Artifact("example-infographic", "1.0.0"),
      publicPath = new URI(publicpath),
      repositoryPath = "images/example.png",
      mediaType = "image/png",
      sha256 = _artifact_sha256,
      provenance = CozyArticleMediaIntegrity.MediaPackage("src/main/media/example.yaml", "example-infographic", "target/cozy-media/manifest.json"),
      publicationState = publicationstate
    ))

  private def _with_metadata[A](
    publications: Vector[CozyArticleMediaPublication.Result]
  )(f: PublishMetadata => A): A = {
    val workroot = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve("target").resolve("cozy-article-media-policy").resolve("work")
    Files.createDirectories(workroot)
    val directory = Files.createTempDirectory(workroot, "bundle-")
    try {
      val entries =
        if (publications.nonEmpty)
          publications.map(x => Json.obj("path" -> x.entryPath, "metadata" -> x.metadata))
        else
          Vector(Json.obj("path" -> "metadata/catalog/projects/ordinary.json", "metadata" -> Json.obj("kind" -> "ordinary")))
      val bundle = Json.obj("type" -> "publication-bundle", "entries" -> entries)
      Files.write(directory.resolve("publication.json"), Json.stringify(bundle).getBytes(StandardCharsets.UTF_8))
      f(PublishMetadata.load(directory.toFile).get)
    } finally {
      _delete(directory)
    }
  }

  private def _with_root[A](f: Path => A): A = {
    val workroot = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve("target").resolve("cozy-article-media-policy").resolve("work")
    Files.createDirectories(workroot)
    val root = Files.createTempDirectory(workroot, "repository-")
    try f(root)
    finally _delete(root)
  }

  private lazy val _artifact_bytes: Array[Byte] = "cozy-article-media-policy-artifact".getBytes(StandardCharsets.UTF_8)

  private lazy val _artifact_sha256: String =
    MessageDigest.getInstance("SHA-256").digest(_artifact_bytes).map(x => f"${x & 0xff}%02x").mkString

  private def _evaluate(
    strategy: CozyArticleMediaPolicy.Strategy,
    metadata: PublishMetadata,
    integrities: Vector[CozyArticleMediaIntegrity.Result]
  ): CozyArticleMediaPolicy.Result =
    _with_root { root =>
      _write_artifact(root.resolve("video/example.mp4"), _artifact_bytes)
      _write_artifact(root.resolve("images/example.png"), _artifact_bytes)
      CozyArticleMediaPolicy.evaluate(strategy, metadata, integrities, root)
    }

  private val _video_registration = CozyArticleMediaPolicy.VideoRegistration(
    "development-process/example",
    "ja",
    "example-video",
    "1.0.0"
  )

  private def _video_provenance(root: Path): Vector[JsObject] = {
    val artifact = root.resolve("video/example-video/1.0.0/example-video-1.0.0.mp4")
    _write_artifact(artifact, "exact selected video bytes".getBytes(StandardCharsets.UTF_8))
    val sha256 = _sha256(artifact)
    val manifest = Json.obj(
      "schema" -> "cozy.publish-project.v1",
      "type" -> "video-registry-manifest",
      "video" -> Json.obj("name" -> "example-video", "version" -> "1.0.0"),
      "artifact" -> Json.obj(
        "warehousePath" -> "repository/video/example-video/1.0.0/example-video-1.0.0.mp4",
        "repositoryPublicPath" -> "repository/video/example-video/1.0.0/example-video-1.0.0.mp4",
        "sha256" -> sha256
      )
    )
    val registry = Json.obj(
      "schema" -> "cozy.publish-project.v1",
      "type" -> "repository-artifact",
      "project" -> Json.obj("name" -> "example-video", "version" -> "1.0.0", "kind" -> "video"),
      "artifact" -> Json.obj("status" -> "published", "files" -> Json.arr(Json.obj(
        "type" -> "video",
        "version" -> "1.0.0",
        "extension" -> "mp4",
        "warehousePath" -> "repository/video/example-video/1.0.0/example-video-1.0.0.mp4",
        "sha256" -> sha256
      )))
    )
    Vector(
      Json.obj("path" -> "metadata/video/example-video/1.0.0/manifest.json", "key" -> "video/example-video/1.0.0/manifest", "metadata" -> manifest),
      Json.obj("path" -> "metadata/artifacts/repository/example-video.json", "key" -> "artifacts/repository/example-video", "metadata" -> registry)
    )
  }

  private def _infographic_input(root: Path): CozyArticleMediaInfographicEvidence.Input = {
    val project = Files.createDirectories(root.resolve("project"))
    val descriptor = project.resolve("media.yaml")
    val manifest = project.resolve("target/cozy-media/manifest.json")
    val buildoutput = project.resolve("target/cozy-media/example-ja.png")
    val destination = root.resolve("images/development-process/example-ja.png")
    val png = java.util.Base64.getDecoder.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=")
    _write_artifact(buildoutput, png)
    _write_artifact(destination, png)
    val sha256 = _sha256(destination)
    _write_artifact(descriptor,
      """{"schema":"cozy.media.v1","knowledge":{"id":"development-process/example","source":"knowledge/article.dox"},"profiles":{"site":{"root":".."}},"resources":[{"id":"example-infographic-ja","kind":"image","language":"ja","role":"detailed-infographic","source":"infographic/example.svg","output":"target/cozy-media/example-ja.png","build":"copy","publications":{"site":"images/development-process/example-ja.png"}}]}""".getBytes(StandardCharsets.UTF_8)
    )
    _write_artifact(manifest,
      s"""{"schema":"cozy.media.v1","knowledge":"development-process/example","resources":[{"id":"example-infographic-ja","path":"target/cozy-media/example-ja.png","sha256":"$sha256"}]}""".getBytes(StandardCharsets.UTF_8)
    )
    CozyArticleMediaInfographicEvidence.Input(
      root,
      descriptor,
      manifest,
      "development-process/example",
      "ja",
      "1.0.0",
      CozyArticleMediaInfographicEvidence.PublicationResult("site", root, destination, CozyArticleMediaInfographicEvidence.PublicationOutcome.Published),
      root
    )
  }

  private def _write_artifact(path: Path, bytes: Array[Byte]): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, bytes)
  }

  private def _sha256(path: Path): String =
    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).map(x => f"${x & 0xff}%02x").mkString

  private def _write_bundle(root: Path, name: String, entries: Vector[JsObject]): Unit = {
    val bundle = Json.obj(
      "type" -> "publication-bundle",
      "publication" -> Json.obj("name" -> name),
      "entries" -> entries
    )
    Files.write(root.resolve(s"$name.json"), Json.stringify(bundle).getBytes(StandardCharsets.UTF_8))
  }

  private def _delete(path: Path): Unit = {
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try stream.iterator.asScala.toVector.sortBy(x => (-x.getNameCount, x.toString)).foreach(Files.deleteIfExists)
      finally stream.close()
    }
  }
}
