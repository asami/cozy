package cozy.publication

import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths}
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{Callable, CountDownLatch, Executors, TimeUnit}
import scala.collection.JavaConverters._
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.smartdox.metadata.PublishMetadata.{ImageReference, PdfDocumentReference, VideoPresentation, VideoReference, VideoStatus}
import play.api.libs.json.{JsObject, Json}

private object CozyArticleMediaBuildContextFixture {
  final case class Data(root: Path, project: Path, publication: Path, repository: Path)
}

/*
 * @since   Aug.  5, 2026
 * @version Aug. 30, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyArticleMediaBuildContextSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "CozyArticleMediaBuildContext" should {
    "normalize its canonical strategy contract" which {
      "accept canonical values and aliases before any filesystem or callback effect" in {
        Given("the four canonical strategies, their two aliases, and an absent publication directory")
        _with_fixture { fixture =>
          val accepted = Vector(
            "draft" -> "draft",
            "wip" -> "work-in-progress",
            "work-in-progress" -> "work-in-progress",
            "preview" -> "production-preview",
            "production-preview" -> "production-preview",
            "production" -> "production"
          )
          var called = false

          When("each name is normalized while an unsupported name is passed to materialization")
          val names = accepted.map { case (input, _) => CozyArticleMediaBuildContext.normalizeStrategy(input).name }
          val error = intercept[IllegalArgumentException](CozyArticleMediaBuildContext.withContext(
            fixture.project, fixture.publication, fixture.repository, "unsupported"
          ) { _ => called = true })

          Then("only canonical names are retained and failure precedes root creation and callback invocation")
          names shouldBe accepted.map(_._2)
          error.getMessage should include("Unsupported article-media build strategy")
          called shouldBe false
          Files.exists(fixture.publication, LinkOption.NOFOLLOW_LINKS) shouldBe false
        }
      }
    }

    "frame the effective publication with the specified digest" which {
      "use the same producer and verifier digest seam for the fixed golden bytes" in {
        Given("a direct publication containing a.json=A and b.json=BC")
        _with_fixture { fixture =>
          val publication = Files.createDirectories(fixture.root.resolve("golden"))
          Files.write(publication.resolve("a.json"), "A".getBytes(StandardCharsets.UTF_8))
          Files.write(publication.resolve("b.json"), "BC".getBytes(StandardCharsets.UTF_8))

          When("the canonical draft digest is framed from the direct publication files")
          val digest = CozyArticleMediaBuildContext.contextDigest("draft", publication)

          Then("the exact U64 framing golden vector is produced")
          digest shouldBe "9b72e396d80c9ab436735bb4d0b8d2372200f40ef99c5d8762236b00bd6b017e"
        }
      }

      "remain independent of direct file enumeration order" in {
        Given("generated write orderings of the same direct bundle bytes")
        val orders = Gen.oneOf(Vector(Vector("a.json", "b.json"), Vector("b.json", "a.json")))
        val property = Prop.forAll(orders) { order =>
          _with_fixture { fixture =>
            val publication = Files.createDirectories(fixture.root.resolve("order"))
            order.foreach { name =>
              val bytes = if (name == "a.json") "A" else "BC"
              Files.write(publication.resolve(name), bytes.getBytes(StandardCharsets.UTF_8))
            }
            CozyArticleMediaBuildContext.contextDigest("draft", publication) == "9b72e396d80c9ab436735bb4d0b8d2372200f40ef99c5d8762236b00bd6b017e"
          }
        }

        When("ScalaCheck exercises both write orderings")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(30), property)

        Then("the digest remains the canonical framed identity")
        result.passed shouldBe true
      }
    }

    "materialize immutable policy-effective snapshots" which {
      "pass media-free and external watch-only bundles without touching an absent repository root" in {
        Given("a registry bundle with one generic entry and one external watch-only strict entry")
        _with_fixture { fixture =>
          val strict = CozyArticleMediaPublication.produce("development-process/example", Vector(
            CozyArticleMediaPublication.Variant("ja", video = Some(
              VideoReference(VideoPresentation.ExternalLink, VideoStatus.Published, None, Some(new URI("https://example.com/watch")), None)
            ))
          ))
          _write_bundle(fixture.publication, "publication", Vector(
            _entry("metadata/catalog/ordinary.json", Json.obj("kind" -> "ordinary")),
            _entry(strict.entryPath, strict.metadata)
          ))
          val source = Files.readAllBytes(fixture.publication.resolve("publication.json"))

          When("a draft context is built with an absent configured repository root")
          val context = CozyArticleMediaBuildContext.withContext(
            fixture.project, fixture.publication, fixture.repository, "draft"
          )(context => context)

          Then("the immutable publication is installed without creating or scanning that repository")
          Files.exists(fixture.repository, LinkOption.NOFOLLOW_LINKS) shouldBe false
          Files.readAllBytes(fixture.publication.resolve("publication.json")).toVector shouldBe source.toVector
          Files.isDirectory(context.publicationPath, LinkOption.NOFOLLOW_LINKS) shouldBe true
          Files.readAllBytes(context.publicationPath.resolve("publication.json")).toVector shouldBe source.toVector
        }
      }

      "omit only unavailable Preview strict roles while preserving generic and integrity entries" in {
        Given("a published infographic whose configured repository artifact is unavailable")
        _with_fixture { fixture =>
          Files.createDirectories(fixture.repository)
          val strict = CozyArticleMediaPublication.produce("development-process/example", Vector(
            CozyArticleMediaPublication.Variant("ja", infographic = Some(
              ImageReference(new URI("/ja/development-process/images/example.png"), Some("image/png"), None)
            ))
          ))
          val integrity = _integrity()
          _write_bundle(fixture.publication, "publication", Vector(
            _entry("metadata/catalog/ordinary.json", Json.obj("kind" -> "ordinary")),
            _entry("metadata/legacy/video.json", Json.obj("kind" -> "legacy")),
            _entry(strict.entryPath, strict.metadata),
            _entry(integrity.entryPath, integrity.metadata)
          ))
          val source = Files.readAllBytes(fixture.publication.resolve("publication.json"))

          When("Preview materializes its policy-effective context")
          val context = CozyArticleMediaBuildContext.withContext(
            fixture.project, fixture.publication, fixture.repository, "preview"
          )(context => context)
          val installed = Json.parse(new String(Files.readAllBytes(context.publicationPath.resolve("publication.json")), StandardCharsets.UTF_8))
          val paths = (installed \ "entries").as[Vector[JsObject]].map(x => (x \ "path").as[String])

          Then("only the strict role is removed, while generic/integrity content and the source bytes remain authoritative")
          context.omittedKeys.map(_.role) shouldBe Vector(CozyArticleMediaIntegrity.Role.Infographic)
          paths shouldBe Vector("metadata/article-media-integrity/development-process/example/ja/infographic.json", "metadata/catalog/ordinary.json", "metadata/legacy/video.json")
          Files.readAllBytes(fixture.publication.resolve("publication.json")).toVector shouldBe source.toVector
        }
      }

      "preserve both strict PDF roles when Preview omits an unavailable infographic" in {
        Given("a strict variant with an unavailable infographic and two direct SmartDox PDF references")
        _with_fixture { fixture =>
          val strict = CozyArticleMediaPublication.produce("development-process/example", Vector(
            CozyArticleMediaPublication.Variant(
              locale = "ja",
              infographic = Some(ImageReference(new URI("/ja/development-process/images/example.png"), Some("image/png"), None)),
              articlePdf = Some(PdfDocumentReference(new URI("/ja/development-process/pdf/example-article.pdf"), "application/pdf", Some("Article PDF"))),
              summarySlidesPdf = Some(PdfDocumentReference(new URI("/ja/development-process/pdf/example-summary.pdf"), "application/pdf", Some("Summary slides PDF")))
            )
          ))
          _write_bundle(fixture.publication, "publication", Vector(
            _entry("metadata/catalog/ordinary.json", Json.obj("kind" -> "ordinary")),
            _entry(strict.entryPath, strict.metadata)
          ))

          When("Preview materializes its effective context")
          val context = CozyArticleMediaBuildContext.withContext(
            fixture.project, fixture.publication, fixture.repository, "preview"
          )(context => context)
          val installed = Json.parse(new String(Files.readAllBytes(context.publicationPath.resolve("publication.json")), StandardCharsets.UTF_8))

          Then("the unavailable infographic is omitted but both PDF roles remain direct and uncorrelated")
          context.omittedKeys.map(_.role) shouldBe Vector(CozyArticleMediaIntegrity.Role.Infographic)
          (installed \ "entries").as[Vector[JsObject]].map(x => (x \ "path").as[String]) shouldBe Vector(strict.entryPath, "metadata/catalog/ordinary.json")
          val strictentry = (installed \ "entries").as[Vector[JsObject]].find(x => (x \ "path").as[String] == strict.entryPath).get
          (strictentry \ "metadata" \ "variants" \ "ja" \ "article_pdf" \ "media_type").as[String] shouldBe "application/pdf"
          (strictentry \ "metadata" \ "variants" \ "ja" \ "summary_slides_pdf" \ "public_path").as[String] shouldBe "/ja/development-process/pdf/example-summary.pdf"
        }
      }

      "exclude WIP strict video and integrity entries from a Production snapshot without touching the source registry" in {
        Given("generic metadata plus a canonical WIP strict video and matching WIP integrity in one registry bundle")
        _with_fixture { fixture =>
          val strict = CozyArticleMediaPublication.produce("development-process/example", Vector(
            CozyArticleMediaPublication.Variant("ja", video = Some(
              VideoReference(VideoPresentation.SiteHosted, VideoStatus.Published, None, None, Some(new URI("/ja/development-process/videos/example.mp4")))
            ))
          ))
          val integrity = _wip_site_video_integrity()
          _write_bundle(fixture.publication, "publication", Vector(
            _entry("metadata/catalog/ordinary.json", Json.obj("kind" -> "ordinary")),
            _entry(strict.entryPath, strict.metadata),
            _entry(integrity.entryPath, integrity.metadata)
          ))
          val source = Files.readAllBytes(fixture.publication.resolve("publication.json")).toVector

          When("a Production build context materializes against an absent artifact repository")
          val context = CozyArticleMediaBuildContext.withContext(
            fixture.project, fixture.publication, fixture.repository, "production"
          )(context => context)
          val installed = Json.parse(new String(Files.readAllBytes(context.publicationPath.resolve("publication.json")), StandardCharsets.UTF_8))

          Then("the snapshot retains generic metadata while removing the WIP strict/integrity pair and preserving source bytes")
          context.omittedKeys.map(_.role) shouldBe Vector(CozyArticleMediaIntegrity.Role.Video)
          Files.exists(fixture.repository, LinkOption.NOFOLLOW_LINKS) shouldBe false
          (installed \ "entries").as[Vector[JsObject]].map(x => (x \ "path").as[String]) shouldBe Vector("metadata/catalog/ordinary.json")
          Files.readAllBytes(fixture.publication.resolve("publication.json")).toVector shouldBe source
        }
      }

      "retain media-free and URL-less nonprojectable contexts without creating an absent repository" in {
        Given("a media-free bundle and a URL-less draft site-hosted strict bundle")
        val observations = Vector("media-free", "url-less").map { mode =>
          _with_fixture { fixture =>
            val entries = mode match {
              case "media-free" => Vector(_entry("metadata/catalog/ordinary.json", Json.obj("kind" -> "ordinary")))
              case "url-less" =>
                val strict = CozyArticleMediaPublication.produce("development-process/example", Vector(
                  CozyArticleMediaPublication.Variant("ja", video = Some(VideoReference(VideoPresentation.SiteHosted, VideoStatus.Draft)))
                ))
                Vector(_entry("metadata/catalog/ordinary.json", Json.obj("kind" -> "ordinary")), _entry(strict.entryPath, strict.metadata))
              case _ => throw new IllegalArgumentException("Invalid repository-free fixture")
            }
            _write_bundle(fixture.publication, "publication", entries)
            val source = Files.readAllBytes(fixture.publication.resolve("publication.json"))

            When(s"$mode materializes against an absent repository root")
            val context = CozyArticleMediaBuildContext.withContext(
              fixture.project, fixture.publication, fixture.repository, "draft"
            )(context => context)

            Then("the policy leaves repository absence unobservable and preserves effective bundle bytes")
            (mode, Files.exists(fixture.repository, LinkOption.NOFOLLOW_LINKS), Files.readAllBytes(context.publicationPath.resolve("publication.json")).toVector == source.toVector)
          }
        }

        When("both repository-free forms are materialized")
        val repositoryfree = observations

        Then("neither creates the configured repository and both preserve their source bundle")
        repositoryfree.map(_._1) shouldBe Vector("media-free", "url-less")
        repositoryfree.foreach { observation =>
          observation._2 shouldBe false
          observation._3 shouldBe true
        }
      }

      "materialize and reuse an empty configured registry without creating repository state" in {
        Given("absent publication and repository roots with no configured registry bundles")
        _with_fixture { fixture =>
          When("an empty draft context is materialized twice")
          val first = CozyArticleMediaBuildContext.withContext(
            fixture.project, fixture.publication, fixture.repository, "draft"
          ) { context =>
            CozyArticleMediaBuildContext.activeLeaseCount(context.publicationPath) shouldBe 1
            context
          }
          val firstattributes = Files.readAttributes(first.publicationPath, classOf[BasicFileAttributes])
          val firstchildren = _direct_children(first.publicationPath).map(_.getFileName.toString)
          val second = CozyArticleMediaBuildContext.withContext(
            fixture.project, fixture.publication, fixture.repository, "draft"
          ) { context =>
            CozyArticleMediaBuildContext.activeLeaseCount(context.publicationPath) shouldBe 1
            context
          }
          val secondattributes = Files.readAttributes(second.publicationPath, classOf[BasicFileAttributes])

          Then("both leases release while the empty digest snapshot is reused unchanged")
          CozyArticleMediaRegistry.load(fixture.publication).bundleDigests shouldBe empty
          _direct_children(fixture.publication).map(_.getFileName.toString).filter(_.endsWith(".json")) shouldBe empty
          Files.exists(fixture.repository, LinkOption.NOFOLLOW_LINKS) shouldBe false
          first.contextDigest shouldBe second.contextDigest
          first.publicationPath shouldBe second.publicationPath
          firstchildren shouldBe empty
          _direct_children(second.publicationPath).map(_.getFileName.toString) shouldBe firstchildren
          secondattributes.fileKey shouldBe firstattributes.fileKey
          secondattributes.lastModifiedTime shouldBe firstattributes.lastModifiedTime
          CozyArticleMediaBuildContext.activeLeaseCount(first.publicationPath) shouldBe 0
        }
      }

      "retain strict metadata failure for malformed nonempty bundles" in {
        Given("a nonempty bundle whose generic entry advertises malformed article-media metadata")
        _with_fixture { fixture =>
          _write_bundle(fixture.publication, "publication", Vector(
            _entry("metadata/catalog/malformed.json", Json.obj("type" -> "article-media-publication"))
          ))
          val source = Files.readAllBytes(fixture.publication.resolve("publication.json"))
          var called = false

          When("the malformed bundle is materialized")
          val error = intercept[IllegalArgumentException](CozyArticleMediaBuildContext.withContext(
            fixture.project, fixture.publication, fixture.repository, "draft"
          ) { _ => called = true })

          Then("strict nonempty metadata loading still fails before installation or callback")
          error.getMessage should include("publication metadata is invalid")
          called shouldBe false
          Files.readAllBytes(fixture.publication.resolve("publication.json")).toVector shouldBe source.toVector
          _direct_children(fixture.project.resolve("target/cozy-bok/article-media/draft/snapshots")) shouldBe empty
        }
      }

      "omit a missing Preview infographic without integrity while preserving generic bytes" in {
        Given("a generic entry and a strict infographic entry with no Cozy integrity")
        _with_fixture { fixture =>
          val strict = CozyArticleMediaPublication.produce("development-process/example", Vector(
            CozyArticleMediaPublication.Variant("ja", infographic = Some(
              ImageReference(new URI("/ja/development-process/images/example.png"), Some("image/png"), None)
            ))
          ))
          _write_bundle(fixture.publication, "publication", Vector(
            _entry("metadata/catalog/ordinary.json", Json.obj("kind" -> "ordinary")),
            _entry(strict.entryPath, strict.metadata)
          ))
          val source = Files.readAllBytes(fixture.publication.resolve("publication.json"))

          When("Preview materializes the missing optional role")
          val context = CozyArticleMediaBuildContext.withContext(
            fixture.project, fixture.publication, fixture.repository, "draft"
          )(context => context)
          val installed = Json.parse(new String(Files.readAllBytes(context.publicationPath.resolve("publication.json")), StandardCharsets.UTF_8))

          Then("the empty strict entry is omitted while the generic entry and source bytes remain")
          context.omittedKeys.map(_.role) shouldBe Vector(CozyArticleMediaIntegrity.Role.Infographic)
          (installed \ "entries").as[Vector[JsObject]].map(x => (x \ "path").as[String]) shouldBe Vector("metadata/catalog/ordinary.json")
          Files.readAllBytes(fixture.publication.resolve("publication.json")).toVector shouldBe source.toVector
        }
      }

      "fail Production before installation for unavailable path-bearing evidence" in {
        Given("a registered site-hosted video and an existing empty repository root")
        _with_fixture { fixture =>
          Files.createDirectories(fixture.repository)
          val strict = CozyArticleMediaPublication.produce("development-process/example", Vector(
            CozyArticleMediaPublication.Variant("ja", video = Some(
              VideoReference(VideoPresentation.SiteHosted, VideoStatus.Published, None, None, Some(new URI("/repository/video/example.mp4")))
            ))
          ))
          val integrity = _integrity(role = CozyArticleMediaIntegrity.Role.Video, state = CozyArticleMediaIntegrity.PublicationState.Registered)
          _write_bundle(fixture.publication, "publication", Vector(_entry(strict.entryPath, strict.metadata), _entry(integrity.entryPath, integrity.metadata)))

          When("Production materialization evaluates the canonical integrity state")
          val error = intercept[IllegalArgumentException](CozyArticleMediaBuildContext.withContext(
            fixture.project, fixture.publication, fixture.repository, "production"
          )(context => context))

          Then("no digest-addressed production snapshot is installed")
          error.getMessage should include("requires published Cozy integrity")
          val snapshots = fixture.project.resolve("target/cozy-bok/article-media/production/snapshots")
          _direct_children(snapshots) shouldBe empty
        }
      }

      "materialize valid Production evidence and reject every unavailable path-bearing state" in {
        Given("published matching bytes plus registered, missing, and stale production evidence cases")
        _with_fixture { fixture =>
          val artifact = "published production artifact".getBytes(StandardCharsets.UTF_8)
          Files.createDirectories(fixture.repository.resolve("video"))
          Files.write(fixture.repository.resolve("video/example.mp4"), artifact)
          _write_path_bearing_video_bundle(fixture.publication, _integrity(
            role = CozyArticleMediaIntegrity.Role.Video,
            state = CozyArticleMediaIntegrity.PublicationState.Published,
            sha256 = _sha256(artifact)
          ))

          When("Production receives matching published evidence")
          val valid = CozyArticleMediaBuildContext.withContext(
            fixture.project, fixture.publication, fixture.repository, "production"
          )(context => context)

          Then("the immutable production context is installed")
          Files.isDirectory(valid.publicationPath, LinkOption.NOFOLLOW_LINKS) shouldBe true
        }
        val observations = Vector(
          "registered" -> CozyArticleMediaIntegrity.PublicationState.Registered,
          "published-missing" -> CozyArticleMediaIntegrity.PublicationState.Published,
          "published-stale" -> CozyArticleMediaIntegrity.PublicationState.Published
        ).map { case (label, state) =>
          _with_fixture { fixture =>
            Files.createDirectories(fixture.repository.resolve("video"))
            val expected = "expected artifact".getBytes(StandardCharsets.UTF_8)
            if (label == "published-stale")
              Files.write(fixture.repository.resolve("video/example.mp4"), "stale artifact".getBytes(StandardCharsets.UTF_8))
            val integrity = _integrity(CozyArticleMediaIntegrity.Role.Video, state, _sha256(expected))
            _write_path_bearing_video_bundle(fixture.publication, integrity)
            var called = false
            val error = intercept[IllegalArgumentException](CozyArticleMediaBuildContext.withContext(
              fixture.project, fixture.publication, fixture.repository, "production"
            ) { _ => called = true })
            (label, error.getMessage, called, _direct_children(fixture.project.resolve("target/cozy-bok/article-media/production/snapshots")))
          }
        }

        When("Production receives unavailable registered, missing, and stale evidence")
        val unavailable = observations

        Then("every case rejects before consumer invocation or snapshot installation")
        unavailable.map(_._1) shouldBe Vector("registered", "published-missing", "published-stale")
        unavailable.foreach { observation =>
          observation._2 should include("Article-media")
          observation._3 shouldBe false
          observation._4 shouldBe empty
        }
      }

      "reuse an existing snapshot without changing its bytes, attributes, or active lease lifetime" in {
        Given("one external watch-only context built twice from unchanged source bytes")
        _with_fixture { fixture =>
          val strict = CozyArticleMediaPublication.produce("development-process/example", Vector(
            CozyArticleMediaPublication.Variant("ja", video = Some(
              VideoReference(VideoPresentation.ExternalLink, VideoStatus.Published, None, Some(new URI("https://example.com/watch")), None)
            ))
          ))
          _write_bundle(fixture.publication, "publication", Vector(_entry(strict.entryPath, strict.metadata)))
          val first = CozyArticleMediaBuildContext.withContext(fixture.project, fixture.publication, fixture.repository, "draft") { context =>
            CozyArticleMediaBuildContext.activeLeaseCount(context.publicationPath) shouldBe 1
            context
          }
          val bytes = Files.readAllBytes(first.publicationPath.resolve("publication.json"))
          val attributes = Files.readAttributes(first.publicationPath.resolve("publication.json"), classOf[BasicFileAttributes])
          val paths = _direct_children(first.publicationPath).map(_.getFileName.toString).sorted

          When("the same strategy and source invoke a second consumer")
          val second = CozyArticleMediaBuildContext.withContext(fixture.project, fixture.publication, fixture.repository, "draft")(context => context)

          Then("the exact installed identity is reused and the lease is released after each callback")
          second.contextDigest shouldBe first.contextDigest
          second.publicationPath shouldBe first.publicationPath
          Files.readAllBytes(second.publicationPath.resolve("publication.json")).toVector shouldBe bytes.toVector
          val reloaded = Files.readAttributes(second.publicationPath.resolve("publication.json"), classOf[BasicFileAttributes])
          reloaded.lastModifiedTime shouldBe attributes.lastModifiedTime
          reloaded.fileKey shouldBe attributes.fileKey
          _direct_children(second.publicationPath).map(_.getFileName.toString).sorted shouldBe paths
          CozyArticleMediaBuildContext.activeLeaseCount(first.publicationPath) shouldBe 0
          Files.exists(first.publicationPath, LinkOption.NOFOLLOW_LINKS) shouldBe true
        }
      }

      "release a lease after a failing consumer without changing its installed snapshot" in {
        Given("a materialized external watch-only publication")
        _with_watch_only_fixture { fixture =>
          val context = CozyArticleMediaBuildContext.withContext(fixture.project, fixture.publication, fixture.repository, "draft")(context => context)
          val bytes = Files.readAllBytes(context.publicationPath.resolve("publication.json"))

          When("a consumer observes its lease and fails")
          val error = intercept[IllegalStateException](CozyArticleMediaBuildContext.withContext(
            fixture.project, fixture.publication, fixture.repository, "draft"
          ) { active =>
            CozyArticleMediaBuildContext.activeLeaseCount(active.publicationPath) shouldBe 1
            throw new IllegalStateException("consumer failure")
          })

          Then("the lease is released and the immutable installed bytes remain")
          error.getMessage shouldBe "consumer failure"
          CozyArticleMediaBuildContext.activeLeaseCount(context.publicationPath) shouldBe 0
          Files.readAllBytes(context.publicationPath.resolve("publication.json")).toVector shouldBe bytes.toVector
        }
      }

      "reject stale source bytes at the locked source-revalidation boundary" in {
        Given("effective work bytes and a seam that changes the configured source bundle")
        _with_watch_only_fixture { fixture =>
          val originaldigest = CozyArticleMediaBuildContext.contextDigest("draft", fixture.publication)
          val source = fixture.publication.resolve("publication.json")
          var called = false

          When("the seam writes a different valid source bundle immediately before revalidation")
          val error = intercept[IllegalArgumentException](CozyArticleMediaBuildContext.withContextBeforeSourceRevalidation(
            fixture.project, fixture.publication, fixture.repository, "draft"
          )(() => _write_watch_only_bundle(fixture.publication, revision = 2)) { _ => called = true })

          Then("the stale snapshot prevents install and callback while the changed source remains authoritative")
          error.getMessage should include("stale configured snapshot")
          called shouldBe false
          Files.exists(fixture.project.resolve(s"target/cozy-bok/article-media/draft/snapshots/$originaldigest"), LinkOption.NOFOLLOW_LINKS) shouldBe false
          _direct_children(fixture.project.resolve("target/cozy-bok/article-media/draft/snapshots")).map(_.getFileName.toString).filter(_.startsWith(".work-")) shouldBe empty
          new String(Files.readAllBytes(source), StandardCharsets.UTF_8) should include("\"revision\":2")
        }
      }

      "reject pre-existing digest snapshots with mismatched bytes or file sets without mutation" in {
        Given("one valid installed watch-only snapshot")
        val observations = Vector("bytes", "file-set").map { mode =>
          _with_watch_only_fixture { fixture =>
            val initial = CozyArticleMediaBuildContext.withContext(fixture.project, fixture.publication, fixture.repository, "draft")(context => context)
            val installed = initial.publicationPath.resolve("publication.json")
            if (mode == "bytes") Files.write(installed, "mismatched".getBytes(StandardCharsets.UTF_8))
            else Files.write(initial.publicationPath.resolve("extra.json"), "extra".getBytes(StandardCharsets.UTF_8))
            val before = _direct_children(initial.publicationPath).map(_.getFileName.toString).sorted
            var called = false
            val error = intercept[IllegalArgumentException](CozyArticleMediaBuildContext.withContext(
              fixture.project, fixture.publication, fixture.repository, "draft"
            ) { _ => called = true })
            (mode, error.getMessage, called, before, _direct_children(initial.publicationPath).map(_.getFileName.toString).sorted)
          }
        }

        When("same-source materialization encounters each persisted mismatch")
        val mismatches = observations

        Then("both reject deterministically without replacing, removing, or consuming the installed snapshot")
        mismatches.map(_._1) shouldBe Vector("bytes", "file-set")
        mismatches.foreach { mismatch =>
          mismatch._2 should include("snapshot")
          mismatch._3 shouldBe false
          mismatch._5 shouldBe mismatch._4
        }
      }

      "keep an earlier leased snapshot immutable while a producer update gives a second consumer a new identity" in {
        Given("consumer A holding a first snapshot and a producer-compatible registry update")
        _with_watch_only_fixture { fixture =>
          val entered = new CountDownLatch(1)
          val release = new CountDownLatch(1)
          val observed = new AtomicReference[Vector[Byte]]()
          val firstcontext = new AtomicReference[CozyArticleMediaBuildContext.Context]()
          val executor = Executors.newSingleThreadExecutor()
          try {
            val firstfuture = executor.submit(new Callable[CozyArticleMediaBuildContext.Context] {
              override def call(): CozyArticleMediaBuildContext.Context =
                CozyArticleMediaBuildContext.withContext(fixture.project, fixture.publication, fixture.repository, "draft") { context =>
                  firstcontext.set(context)
                  entered.countDown()
                  release.await(10, TimeUnit.SECONDS) shouldBe true
                  observed.set(Files.readAllBytes(context.publicationPath.resolve("publication.json")).toVector)
                  context
                }
            })

            When("A is leased, a producer-style metadata replacement commits, and B materializes")
            entered.await(10, TimeUnit.SECONDS) shouldBe true
            val before = CozyArticleMediaRegistry.load(fixture.publication)
            CozyPublicationCompiler.replaceMetadata(
              fixture.publication,
              "publication",
              Vector("metadata/catalog/ordinary.json" -> Json.obj("revision" -> 2)),
              Vector.empty,
              before.bundleDigests
            )
            val second = CozyArticleMediaBuildContext.withContext(
              fixture.project, fixture.publication, fixture.repository, "draft"
            )(context => context)
            CozyArticleMediaBuildContext.activeLeaseCount(firstcontext.get.publicationPath) shouldBe 1
            release.countDown()
            val first = firstfuture.get(10, TimeUnit.SECONDS)

            Then("B has a different immutable digest while A read its original bytes and both snapshots remain")
            first.contextDigest should not be second.contextDigest
            first.publicationPath should not be second.publicationPath
            observed.get should not be null
            new String(observed.get.toArray, StandardCharsets.UTF_8) should include("\"revision\":1")
            Files.exists(first.publicationPath, LinkOption.NOFOLLOW_LINKS) shouldBe true
            Files.exists(second.publicationPath, LinkOption.NOFOLLOW_LINKS) shouldBe true
            CozyArticleMediaBuildContext.activeLeaseCount(first.publicationPath) shouldBe 0
            CozyArticleMediaBuildContext.activeLeaseCount(second.publicationPath) shouldBe 0
          } finally {
            release.countDown()
            executor.shutdownNow()
            executor.awaitTermination(10, TimeUnit.SECONDS) shouldBe true
          }
        }
      }

      "reject symbolic links and non-directory components across source, target, and path-bearing repository boundaries" in {
        Given("five unsafe path shapes at the direct publication and repository boundaries")
        val observations = Vector("bundle-link", "publication-component", "snapshot-parent-link", "repository-root-link", "repository-component-link").map { mode =>
          _with_fixture { fixture =>
            var called = false
            val action = mode match {
              case "bundle-link" =>
                val actual = fixture.root.resolve("actual")
                _write_watch_only_bundle(actual, revision = 1)
                Files.createDirectories(fixture.publication)
                Files.createSymbolicLink(fixture.publication.resolve("publication.json"), actual.resolve("publication.json"))
                () => CozyArticleMediaBuildContext.withContext(fixture.project, fixture.publication, fixture.repository, "draft") { _ => called = true }
              case "publication-component" =>
                val component = fixture.root.resolve("not-directory")
                Files.write(component, "file".getBytes(StandardCharsets.UTF_8))
                () => CozyArticleMediaBuildContext.withContext(fixture.project, component.resolve("publication"), fixture.repository, "draft") { _ => called = true }
              case "snapshot-parent-link" =>
                _write_watch_only_bundle(fixture.publication, revision = 1)
                val escape = Files.createDirectory(fixture.root.resolve("escape"))
                Files.createSymbolicLink(fixture.project.resolve("target"), escape)
                () => CozyArticleMediaBuildContext.withContext(fixture.project, fixture.publication, fixture.repository, "draft") { _ => called = true }
              case "repository-root-link" =>
                val actual = Files.createDirectory(fixture.root.resolve("repository-actual"))
                _write_repository_artifact(actual)
                _write_path_bearing_video_bundle(fixture.publication, _integrity(
                  CozyArticleMediaIntegrity.Role.Video,
                  CozyArticleMediaIntegrity.PublicationState.Published,
                  _sha256(Files.readAllBytes(actual.resolve("video/example.mp4")))
                ))
                Files.createSymbolicLink(fixture.repository, actual)
                () => CozyArticleMediaBuildContext.withContext(fixture.project, fixture.publication, fixture.repository, "production") { _ => called = true }
              case "repository-component-link" =>
                Files.createDirectories(fixture.repository)
                val actual = Files.createDirectory(fixture.root.resolve("video-actual"))
                _write_repository_artifact(actual)
                Files.createSymbolicLink(fixture.repository.resolve("video"), actual.resolve("video"))
                _write_path_bearing_video_bundle(fixture.publication, _integrity(
                  CozyArticleMediaIntegrity.Role.Video,
                  CozyArticleMediaIntegrity.PublicationState.Published,
                  _sha256(Files.readAllBytes(actual.resolve("video/example.mp4")))
                ))
                () => CozyArticleMediaBuildContext.withContext(fixture.project, fixture.publication, fixture.repository, "production") { _ => called = true }
              case _ => throw new IllegalArgumentException("Invalid path-safety fixture")
            }

            When(s"$mode is passed to materialization")
            val error = intercept[IllegalArgumentException](action())

            Then("the unsafe direct path is rejected before consumer or final snapshot escape")
            (mode, error.getMessage, called, Files.exists(fixture.project.resolve("target/cozy-bok/article-media/draft/snapshots"), LinkOption.NOFOLLOW_LINKS))
          }
        }

        When("each unsafe boundary shape is evaluated")
        val rejected = observations

        Then("every path is rejected without consumer invocation")
        rejected.map(_._1) shouldBe Vector("bundle-link", "publication-component", "snapshot-parent-link", "repository-root-link", "repository-component-link")
        rejected.foreach { rejectedcase =>
          rejectedcase._2.toLowerCase should (include("symbolic link") or include("directory") or include("direct components"))
          rejectedcase._3 shouldBe false
          rejectedcase._4 shouldBe false
        }
      }

      "accept a symbolic-link ancestor above configured roots while rejecting configured roots and descendants" in {
        Given("an actual watch-only project and publication tree exposed through a symbolic-link ancestor")
        _with_fixture { fixture =>
          val actual = Files.createDirectory(fixture.root.resolve("actual"))
          val project = Files.createDirectory(actual.resolve("project"))
          val publication = actual.resolve("publication")
          val alias = fixture.root.resolve("alias")
          _write_watch_only_bundle(publication, revision = 1)
          Files.createSymbolicLink(alias, actual)
          val configuredproject = alias.resolve("project")
          val configuredpublication = alias.resolve("publication")
          val configuredrepository = alias.resolve("repository")

          When("a media-free watch-only context uses lexical roots beneath that ancestor alias")
          val context = CozyArticleMediaBuildContext.withContext(
            configuredproject, configuredpublication, configuredrepository, "draft"
          )(context => context)

          Then("materialization succeeds while retaining lexical configured-root identity")
          context.publicationPath.startsWith(configuredproject) shouldBe true
          Files.isDirectory(context.publicationPath, LinkOption.NOFOLLOW_LINKS) shouldBe true
          Files.exists(project.resolve("target/cozy-bok/article-media/draft/snapshots"), LinkOption.NOFOLLOW_LINKS) shouldBe true
        }
      }
    }
  }

  private def _with_fixture[A](f: CozyArticleMediaBuildContextFixture.Data => A): A = {
    val workroot = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve("target/cozy-article-media-build-context-spec")
    Files.createDirectories(workroot)
    val root = Files.createTempDirectory(workroot, "fixture-")
    val fixture = CozyArticleMediaBuildContextFixture.Data(root, Files.createDirectory(root.resolve("project")), root.resolve("publication"), root.resolve("repository"))
    try f(fixture)
    finally _delete(root)
  }

  private def _with_watch_only_fixture[A](f: CozyArticleMediaBuildContextFixture.Data => A): A =
    _with_fixture { fixture =>
      _write_watch_only_bundle(fixture.publication, revision = 1)
      f(fixture)
    }

  private def _write_watch_only_bundle(root: Path, revision: Int): Unit = {
    val strict = CozyArticleMediaPublication.produce("development-process/example", Vector(
      CozyArticleMediaPublication.Variant("ja", video = Some(
        VideoReference(VideoPresentation.ExternalLink, VideoStatus.Published, None, Some(new URI("https://example.com/watch")), None)
      ))
    ))
    _write_bundle(root, "publication", Vector(
      _entry("metadata/catalog/ordinary.json", Json.obj("revision" -> revision)),
      _entry(strict.entryPath, strict.metadata)
    ))
  }

  private def _write_path_bearing_video_bundle(root: Path, integrity: CozyArticleMediaIntegrity.Result): Unit = {
    val strict = CozyArticleMediaPublication.produce("development-process/example", Vector(
      CozyArticleMediaPublication.Variant("ja", video = Some(
        VideoReference(VideoPresentation.SiteHosted, VideoStatus.Published, None, None, Some(new URI("/repository/video/example.mp4")))
      ))
    ))
    _write_bundle(root, "publication", Vector(_entry(strict.entryPath, strict.metadata), _entry(integrity.entryPath, integrity.metadata)))
  }

  private def _write_repository_artifact(root: Path): Unit = {
    Files.createDirectories(root.resolve("video"))
    Files.write(root.resolve("video/example.mp4"), "repository artifact".getBytes(StandardCharsets.UTF_8))
  }

  private def _entry(path: String, metadata: JsObject): JsObject =
    Json.obj("path" -> path, "key" -> path.stripPrefix("metadata/").stripSuffix(".json"), "metadata" -> metadata)

  private def _write_bundle(root: Path, name: String, entries: Vector[JsObject]): Unit = {
    Files.createDirectories(root)
    Files.write(root.resolve(s"$name.json"), Json.stringify(Json.obj(
      "type" -> "publication-bundle",
      "publication" -> Json.obj("name" -> name),
      "entries" -> entries
    )).getBytes(StandardCharsets.UTF_8))
  }

  private def _integrity(
    role: CozyArticleMediaIntegrity.Role = CozyArticleMediaIntegrity.Role.Infographic,
    state: CozyArticleMediaIntegrity.PublicationState = CozyArticleMediaIntegrity.PublicationState.Published,
    sha256: String = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
  ): CozyArticleMediaIntegrity.Result = {
    val medium = role match {
      case CozyArticleMediaIntegrity.Role.Infographic =>
        (new URI("/ja/development-process/images/example.png"), "images/example.png", "image/png", CozyArticleMediaIntegrity.MediaPackage("src/main/media/example.yaml", "example", "target/cozy-media/manifest.json"))
      case CozyArticleMediaIntegrity.Role.Video =>
        (new URI("/repository/video/example.mp4"), "video/example.mp4", "video/mp4", CozyArticleMediaIntegrity.VideoPublication("metadata/video/example/1.0.0/manifest.json", "metadata/artifacts/repository/example.json"))
      case _ => throw new IllegalArgumentException("Invalid fixture role")
    }
    CozyArticleMediaIntegrity.produce(CozyArticleMediaIntegrity.Input(
      articleIdentity = "development-process/example",
      locale = "ja",
      role = role,
      artifact = CozyArticleMediaIntegrity.Artifact("example", "1.0.0"),
      publicPath = medium._1,
      repositoryPath = medium._2,
      mediaType = medium._3,
      sha256 = sha256,
      provenance = medium._4,
      publicationState = state
    ))
  }

  private def _wip_site_video_integrity(): CozyArticleMediaIntegrity.Result =
    CozyArticleMediaIntegrity.produce(CozyArticleMediaIntegrity.Input(
      articleIdentity = "development-process/example",
      locale = "ja",
      role = CozyArticleMediaIntegrity.Role.Video,
      artifact = CozyArticleMediaIntegrity.Artifact("example-video", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
      publicPath = new URI("/ja/development-process/videos/example.mp4"),
      repositoryPath = "ja/development-process/videos/example.mp4",
      mediaType = "video/mp4",
      sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
      provenance = CozyArticleMediaIntegrity.WipSiteVideo("media.yaml", "example-video", "video/ja/production.json"),
      publicationState = CozyArticleMediaIntegrity.PublicationState.Published
    ))

  private def _delete(path: Path): Unit = {
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      val stream = Files.walk(path)
      try stream.iterator.asScala.toVector.sortBy(x => (-x.getNameCount, x.toString)).foreach(Files.deleteIfExists)
      finally stream.close()
    }
  }

  private def _direct_children(path: Path): Vector[Path] = {
    val stream = Files.list(path)
    try stream.iterator.asScala.toVector
    finally stream.close()
  }

  private def _sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(x => f"${x & 0xff}%02x").mkString
}
