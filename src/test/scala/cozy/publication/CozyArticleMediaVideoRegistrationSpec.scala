package cozy.publication

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.security.MessageDigest
import scala.collection.JavaConverters._
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.smartdox.metadata.PublishMetadata.VideoStatus
import cozy.video.CozyVideoPublisher
import play.api.libs.json.{JsNull, JsObject, JsString, JsValue, Json}

/*
 * @since   Aug.  4, 2026
 * @version Aug.  5, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyArticleMediaVideoRegistrationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "CozyVideoPublisher article-media descriptor binding" should {
    "resolve only the canonical nested block into normalized SmartDox binding" in {
      Given("a video package with the explicit camelCase articleMedia block")
      _with_descriptor(_descriptor(articlemedia = Some(Json.obj(
        "articleIdentity" -> " development-process/example ",
        "locale" -> "ja",
        "status" -> "published"
      )))) { packagedir =>

        When("the package is resolved before any video work")
        val resolved = CozyVideoPublisher.resolve(_config(packagedir))

        Then("it exposes normalized identity, exact locale, and typed SmartDox status")
        resolved.articleMedia shouldBe Some(CozyVideoPublisher.ArticleMediaBinding(
          "development-process/example",
          "ja",
          VideoStatus.Published
        ))
        resolved.publicPath shouldBe "videos/legacy-decoy.mp4"
      }
    }

    "reject every malformed explicit nested binding before publication work" which {
      "requires each canonical field and rejects non-string field values" in {
        Given("descriptors that omit or mistype one articleMedia field")
        val missing = Vector("articleIdentity", "locale", "status").map { field =>
          field -> (Json.obj(
            "articleIdentity" -> "development-process/example",
            "locale" -> "ja",
            "status" -> "draft"
          ) - field)
        }
        val wrongtypes = Vector[JsValue](
          Json.obj("articleIdentity" -> Json.obj("nested" -> "value"), "locale" -> "ja", "status" -> "draft"),
          Json.obj("articleIdentity" -> "development-process/example", "locale" -> 7, "status" -> "draft"),
          Json.obj("articleIdentity" -> "development-process/example", "locale" -> "ja", "status" -> JsNull),
          Json.obj("articleIdentity" -> "development-process/example", "locale" -> "ja", "status" -> "draft", "unexpected" -> "x"),
          JsNull
        )

        When("each descriptor is resolved")
        val errors = (missing.map(_._2) ++ wrongtypes).map { value =>
          _with_descriptor(_descriptor(articlemedia = Some(value))) { packagedir =>
            intercept[io.circe.DecodingFailure](CozyVideoPublisher.resolve(_config(packagedir)))
          }
        }

        Then("the canonical decoder fails instead of accepting partial, wrongly typed, or widened input")
        errors.size shouldBe missing.size + wrongtypes.size
        errors.foreach(error => error.getMessage should not be empty)
      }

      "reject invalid identity, locale, and non-exact SmartDox status values" in {
        Given("otherwise valid nested blocks with invalid semantic values")
        val invalid = Vector(
          Json.obj("articleIdentity" -> "/development-process/example", "locale" -> "ja", "status" -> "draft"),
          Json.obj("articleIdentity" -> "ja/development-process/example", "locale" -> "ja", "status" -> "draft"),
          Json.obj("articleIdentity" -> "development-process/example", "locale" -> "ja-jp", "status" -> "draft"),
          Json.obj("articleIdentity" -> "development-process/example", "locale" -> "ja", "status" -> "Draft"),
          Json.obj("articleIdentity" -> "development-process/example", "locale" -> "ja", "status" -> " published")
        )

        When("the packages are resolved")
        val errors = invalid.map { value =>
          _with_descriptor(_descriptor(articlemedia = Some(value))) { packagedir =>
            intercept[IllegalArgumentException](CozyVideoPublisher.resolve(_config(packagedir)))
          }
        }

        Then("normalization and exact status admission reject every invalid binding")
        errors.foreach(error => error.getMessage should not be empty)
      }
    }

    "preserve legacy descriptor behavior when the nested block is absent" in {
      Given("a descriptor populated with legacy article, locale, module, and public-path fields only")
      _with_descriptor(_descriptor(articlemedia = None, locale = Some("en"), article = Some("index.dox"))) { packagedir =>

        When("it is resolved")
        val resolved = CozyVideoPublisher.resolve(_config(packagedir))

        Then("no article-media association is inferred from legacy fields")
        resolved.articleMedia shouldBe None
        resolved.articlePath shouldBe "index.dox"
        resolved.publicPath shouldBe "videos/legacy-decoy.mp4"
      }

      And("the two-argument legacy publish constructor remains source compatible")
      CozyVideoPublisher.VideoDescriptorPublish(None, None).articleMedia shouldBe None
    }
  }

  "CozyArticleMediaVideoRegistration" should {
    "return no registration without inspecting a snapshot or repository when opt-in is absent" in {
      Given("a legacy publication result with no resolved article-media binding")
      val publication = _publication(_video(None), null)

      When("the adapter is prepared with null evidence inputs")
      val result = CozyArticleMediaVideoRegistration.prepare(
        CozyArticleMediaVideoRegistration.Input(publication, null, null)
      )

      Then("it remains a no-op")
      result shouldBe None
    }

    "project exactly one evidence-bound site-hosted role update" which {
      "use repository evidence rather than the legacy descriptor public path without writing any bundle" in {
        Given("an opt-in resolved video result and exact configured manifest, registry, and artifact evidence")
        _with_fixture() { fixture =>
          val publication = _publication(_video(Some(_binding(VideoStatus.Published))), fixture.artifact)
          val before = _tree(fixture.root)

          When("the pure registration adapter prepares its role update")
          val result = CozyArticleMediaVideoRegistration.prepare(_input(publication, fixture)).get

          Then("the strict video uses only validated repository evidence and matching canonical integrity")
          val video = result.roleUpdate.variant.video.get
          video.presentation shouldBe org.smartdox.metadata.PublishMetadata.VideoPresentation.SiteHosted
          video.status shouldBe VideoStatus.Published
          video.provider shouldBe None
          video.watchUrl shouldBe None
          video.contentUrl.map(_.toString) shouldBe Some("/repository/video/example-video/1.0.0/example-video-1.0.0.mp4")
          video.contentUrl.map(_.toString) should not be Some("/videos/legacy-decoy.mp4")
          result.roleUpdate.articleIdentity shouldBe "development-process/example"
          result.roleUpdate.integrity shouldBe result.evidence.integrity
          result.evidence.artifactPath shouldBe fixture.artifact.toRealPath()
          _tree(fixture.root) shouldBe before
        }
      }

      "reject an artifact path that is not the exact evidence-selected publication artifact" in {
        Given("a direct but different warehouse artifact on the same repository")
        _with_fixture() { fixture =>
          val other = fixture.repositoryroot.resolve("video/example-video/1.0.0/other.mp4")
          Files.write(other, "other artifact".getBytes(StandardCharsets.UTF_8))
          val publication = _publication(_video(Some(_binding(VideoStatus.Draft))), other)

          When("registration verifies result-to-evidence binding")
          val error = intercept[IllegalArgumentException](CozyArticleMediaVideoRegistration.prepare(_input(publication, fixture)))

          Then("a non-selected direct artifact is rejected")
          error.getMessage should include("does not match")
        }
      }

      "prepare staged video evidence before final artifact admission" in {
        Given("a bound prepared publication whose final repository artifact is still absent")
        _with_fixture() { fixture =>
          val staged = fixture.root.resolve("staged/final.mp4")
          Files.createDirectories(staged.getParent)
          Files.write(staged, Files.readAllBytes(fixture.artifact))
          Files.delete(fixture.artifact)
          val prepared = CozyVideoPublisher.PreparedPublication(
            _publication(_video(Some(_binding(VideoStatus.Published))), fixture.artifact),
            staged,
            Vector.empty,
            force = false
          )

          When("registration projects the prepared staged source against the final repository target")
          val result = CozyArticleMediaVideoRegistration.prepare(prepared, _snapshot(fixture), fixture.repositoryroot).get

          Then("the staged bytes satisfy projected evidence while the returned path remains the absent final target")
          result.evidence.artifactPath shouldBe fixture.artifact.toAbsolutePath.normalize()
          Files.exists(fixture.artifact) shouldBe false
          result.roleUpdate.integrity.record.sha256 shouldBe _sha256(staged)
        }
      }

      "prepare staged video evidence under an absent lexical repository root without creating it" in {
        Given("a bound prepared publication whose contained final root and artifact do not yet exist")
        _with_fixture() { fixture =>
          val staged = fixture.root.resolve("staged/absent-root.mp4")
          val repositoryroot = fixture.root.resolve("absent-repository-root")
          val artifact = repositoryroot.resolve("video/example-video/1.0.0/example-video-1.0.0.mp4")
          Files.createDirectories(staged.getParent)
          Files.write(staged, Files.readAllBytes(fixture.artifact))
          val prepared = CozyVideoPublisher.PreparedPublication(
            _publication(_video(Some(_binding(VideoStatus.Published))), artifact),
            staged,
            Vector.empty,
            force = false
          )

          When("registration projects staged bytes to the future lexical repository target")
          val result = CozyArticleMediaVideoRegistration.prepare(prepared, _snapshot(fixture), repositoryroot).get

          Then("it returns the contained final target without creating the absent repository root")
          result.evidence.artifactPath shouldBe artifact.toAbsolutePath.normalize()
          Files.exists(repositoryroot) shouldBe false
          result.roleUpdate.integrity.record.sha256 shouldBe _sha256(staged)
        }
      }

      "reject a staged projection when its repository root is a symlink" in {
        Given("a bound prepared publication with a direct staged source and a symbolic-link repository root")
        _with_fixture() { fixture =>
          val staged = fixture.root.resolve("staged/root-link.mp4")
          val alias = fixture.root.resolve("repository-root-alias")
          Files.createDirectories(staged.getParent)
          Files.write(staged, Files.readAllBytes(fixture.artifact))
          Files.createSymbolicLink(alias, fixture.repositoryroot)
          val prepared = CozyVideoPublisher.PreparedPublication(
            _publication(_video(Some(_binding(VideoStatus.Published))), fixture.artifact),
            staged,
            Vector.empty,
            force = false
          )

          When("registration validates the staged repository-root boundary")
          val error = intercept[IllegalArgumentException](
            CozyArticleMediaVideoRegistration.prepare(prepared, _snapshot(fixture), alias)
          )

          Then("the staged target cannot be projected through a symbolic-link repository root")
          error.getMessage should include("direct non-symlink directory")
        }
      }

      "reject a staged projection when an existing final-target parent is a symlink" in {
        Given("a bound prepared publication with a direct staged source and a symbolic-link final-target parent")
        _with_fixture() { fixture =>
          val staged = fixture.root.resolve("staged/parent-link.mp4")
          val external = fixture.root.resolve("external-video-parent")
          val videoroot = fixture.repositoryroot.resolve("video")
          Files.createDirectories(staged.getParent)
          Files.write(staged, Files.readAllBytes(fixture.artifact))
          Files.delete(fixture.artifact)
          Files.delete(videoroot.resolve("example-video/1.0.0"))
          Files.delete(videoroot.resolve("example-video"))
          Files.delete(videoroot)
          Files.createDirectories(external)
          Files.createSymbolicLink(videoroot, external)
          val prepared = CozyVideoPublisher.PreparedPublication(
            _publication(_video(Some(_binding(VideoStatus.Published))), fixture.artifact),
            staged,
            Vector.empty,
            force = false
          )

          When("registration validates the staged final-target parent boundary")
          val error = intercept[IllegalArgumentException](
            CozyArticleMediaVideoRegistration.prepare(prepared, _snapshot(fixture), fixture.repositoryroot)
          )

          Then("the final target cannot be projected through a symbolic-link parent")
          error.getMessage should include("parent must not be a symlink")
        }
      }

      "reject a staged projection when its final target is a symlink" in {
        Given("a bound prepared publication with a direct staged source and a symlink final target")
        _with_fixture() { fixture =>
          val staged = fixture.root.resolve("staged/final.mp4")
          Files.createDirectories(staged.getParent)
          Files.write(staged, Files.readAllBytes(fixture.artifact))
          val redirected = fixture.root.resolve("redirected.mp4")
          Files.write(redirected, "redirected".getBytes(StandardCharsets.UTF_8))
          Files.delete(fixture.artifact)
          Files.createSymbolicLink(fixture.artifact, redirected)
          val prepared = CozyVideoPublisher.PreparedPublication(
            _publication(_video(Some(_binding(VideoStatus.Published))), fixture.artifact),
            staged,
            Vector.empty,
            force = false
          )

          When("registration validates the staged final-target boundary")
          val error = intercept[IllegalArgumentException](
            CozyArticleMediaVideoRegistration.prepare(prepared, _snapshot(fixture), fixture.repositoryroot)
          )

          Then("the final target cannot bypass direct repository containment through a symlink")
          error.getMessage should include("target must not be a symlink")
        }
      }
    }

    "preserve the explicit SmartDox status while applying Cozy publication-state policy" which {
      "reject published SmartDox status when Cozy evidence is not published" in {
        Given("a published SmartDox binding backed by registered Cozy evidence")
        _with_fixture(cozystatus = "registered") { fixture =>
          val publication = _publication(_video(Some(_binding(VideoStatus.Published))), fixture.artifact)

          When("the adapter prepares the role update")
          val error = intercept[IllegalArgumentException](CozyArticleMediaVideoRegistration.prepare(_input(publication, fixture)))

          Then("the status is not silently derived or admitted")
          error.getMessage should include("Published SmartDox")
        }
      }

      "retain draft and withdrawn SmartDox values with correlated path-bearing evidence" in {
        Given("exact published Cozy evidence with non-published SmartDox bindings")
        _with_fixture() { fixture =>
          val statuses = Vector(VideoStatus.Draft, VideoStatus.Withdrawn)

          When("each role update is prepared")
          val results = statuses.map { status =>
            CozyArticleMediaVideoRegistration.prepare(_input(_publication(_video(Some(_binding(status))), fixture.artifact), fixture)).get
          }

          Then("each exact requested SmartDox status remains while the evidence URL is retained")
          results.map(_.roleUpdate.variant.video.get.status) shouldBe statuses
          results.foreach(_.roleUpdate.variant.video.get.contentUrl.map(_.toString) shouldBe Some("/repository/video/example-video/1.0.0/example-video-1.0.0.mp4"))
        }
      }
    }

    "preserve every admitted exact status and evidence URL under generated fixtures" in {
      Given("generated admitted SmartDox statuses and independently reset evidence fixtures")
      val statuses = Gen.oneOf(
        "draft" -> VideoStatus.Draft,
        "published" -> VideoStatus.Published,
        "withdrawn" -> VideoStatus.Withdrawn
      )

      When("at least thirty independently created fixtures prepare their binding")
      val check = Test.check(Test.Parameters.default.withMinSuccessfulTests(30), Prop.forAll(statuses) { pair =>
        _with_fixture() { fixture =>
          val publication = _publication(_video(Some(_binding(pair._2))), fixture.artifact)
          val result = CozyArticleMediaVideoRegistration.prepare(_input(publication, fixture)).get
          result.roleUpdate.variant.video.exists { video =>
            video.status == pair._2 && video.contentUrl.map(_.toString).contains("/repository/video/example-video/1.0.0/example-video-1.0.0.mp4")
          }
        }
      })

      Then("all admitted values preserve their typed status and evidence-derived content URL")
      check.passed shouldBe true
      check.succeeded should be >= 30
    }
  }

  private final class Fixture(
    val root: Path,
    val repositoryroot: Path,
    val artifact: Path,
    val manifest: JsObject,
    val registry: JsObject
  )

  private def _config(packagedir: Path): CozyVideoPublisher.PublishVideoConfig =
    CozyVideoPublisher.PublishVideoConfig(
      packageDir = packagedir,
      saveDir = packagedir.resolve("publication"),
      warehouseDir = packagedir.resolve("warehouse"),
      version = None,
      force = false
    )

  private def _descriptor(
    articlemedia: Option[JsValue],
    locale: Option[String] = None,
    article: Option[String] = None
  ): JsObject = {
    val base = Json.obj(
      "video" -> Json.obj("name" -> "example-video"),
      "version" -> "1.0.0",
      "article" -> JsString(article.getOrElse("index.dox")),
      "script" -> "script.json",
      "publish" -> Json.obj(
        "module" -> "example-video",
        "publicPath" -> "videos/legacy-decoy.mp4"
      )
    )
    val withlocale = locale.map(x => base + ("locale" -> JsString(x))).getOrElse(base)
    articlemedia.map { value =>
      withlocale + ("publish" -> ((withlocale \ "publish").as[JsObject] + ("articleMedia" -> value)))
    }.getOrElse(withlocale)
  }

  private def _with_descriptor[A](descriptor: JsObject)(f: Path => A): A = {
    val workroot = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve("target/cozy-article-media-video-registration/work")
    Files.createDirectories(workroot)
    val root = Files.createTempDirectory(workroot, "descriptor-")
    val packagedir = root.resolve("example.video")
    Files.createDirectories(packagedir)
    Files.write(packagedir.resolve("video.json"), Json.stringify(descriptor).getBytes(StandardCharsets.UTF_8))
    Files.write(packagedir.resolve("index.dox"), "= Example.\n".getBytes(StandardCharsets.UTF_8))
    Files.write(packagedir.resolve("script.json"), "{}".getBytes(StandardCharsets.UTF_8))
    try f(packagedir)
    finally _delete(root)
  }

  private def _binding(status: VideoStatus): CozyVideoPublisher.ArticleMediaBinding =
    CozyVideoPublisher.ArticleMediaBinding("development-process/example", "ja", status)

  private def _video(binding: Option[CozyVideoPublisher.ArticleMediaBinding]): CozyVideoPublisher.ResolvedVideoPackage =
    CozyVideoPublisher.ResolvedVideoPackage(
      packageDir = Paths.get("target/cozy-article-media-video-registration/work/example.video").toAbsolutePath.normalize(),
      slug = "example-video",
      descriptorFile = Paths.get("target/cozy-article-media-video-registration/work/example.video/video.json").toAbsolutePath.normalize(),
      descriptor = CozyVideoPublisher.VideoDescriptor(
        video = None,
        title = None,
        version = None,
        article = None,
        script = None,
        renderer = None,
        toolMode = None,
        publish = None,
        profile = None,
        visualEffects = None,
        assets = None,
        locale = None,
        credits = None,
        parts = Vector.empty
      ),
      name = "example-video",
      title = "Example video",
      version = "1.0.0",
      article = Paths.get("target/cozy-article-media-video-registration/work/index.dox").toAbsolutePath.normalize(),
      articlePath = "index.dox",
      script = Paths.get("target/cozy-article-media-video-registration/work/script.json").toAbsolutePath.normalize(),
      scriptPath = "script.json",
      renderer = "simple-java2d",
      toolMode = "docker",
      module = "example-video",
      publicPath = "videos/legacy-decoy.mp4",
      assets = Vector.empty,
      articleMedia = binding
    )

  private def _publication(
    video: CozyVideoPublisher.ResolvedVideoPackage,
    artifact: Path
  ): CozyVideoPublisher.PublishVideoResult =
    CozyVideoPublisher.PublishVideoResult(
      video = video,
      workspaceRoot = video.packageDir,
      projectFile = video.descriptorFile,
      warehouseArtifact = artifact,
      publicationBundle = video.packageDir.resolve("publication.json")
    )

  private def _input(
    publication: CozyVideoPublisher.PublishVideoResult,
    fixture: Fixture
  ): CozyArticleMediaVideoRegistration.Input =
    CozyArticleMediaVideoRegistration.Input(publication, _snapshot(fixture), fixture.repositoryroot)

  private def _with_fixture[A](cozystatus: String = "published")(f: Fixture => A): A = {
    val workroot = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve("target/cozy-article-media-video-registration/work")
    Files.createDirectories(workroot)
    val root = Files.createTempDirectory(workroot, "registration-")
    try f(_fixture(root, cozystatus))
    finally _delete(root)
  }

  private def _fixture(root: Path, cozystatus: String): Fixture = {
    val repositoryroot = Files.createDirectories(root.resolve("repository-root"))
    val artifact = repositoryroot.resolve("video/example-video/1.0.0/example-video-1.0.0.mp4")
    Files.createDirectories(artifact.getParent)
    Files.write(artifact, "exact selected video bytes".getBytes(StandardCharsets.UTF_8))
    val sha256 = _sha256(artifact)
    new Fixture(root, repositoryroot, artifact, _manifest(sha256), _registry(sha256, cozystatus))
  }

  private def _snapshot(fixture: Fixture): CozyArticleMediaRegistry.Snapshot =
    CozyArticleMediaRegistry.Snapshot(Vector(
      _entry("metadata/video/example-video/1.0.0/manifest.json", fixture.manifest),
      _entry("metadata/artifacts/repository/example-video.json", fixture.registry)
    ))

  private def _entry(path: String, metadata: JsValue): CozyArticleMediaRegistry.Entry =
    CozyArticleMediaRegistry.Entry("fixture", path, path.stripPrefix("metadata/").stripSuffix(".json"), metadata)

  private def _manifest(sha256: String): JsObject =
    Json.obj(
      "schema" -> "cozy.publish-project.v1",
      "type" -> "video-registry-manifest",
      "video" -> Json.obj("name" -> "example-video", "version" -> "1.0.0"),
      "artifact" -> Json.obj(
        "warehousePath" -> "repository/video/example-video/1.0.0/example-video-1.0.0.mp4",
        "repositoryPublicPath" -> "repository/video/example-video/1.0.0/example-video-1.0.0.mp4",
        "sha256" -> sha256
      )
    )

  private def _registry(sha256: String, status: String): JsObject =
    Json.obj(
      "schema" -> "cozy.publish-project.v1",
      "type" -> "repository-artifact",
      "project" -> Json.obj("name" -> "example-video", "version" -> "1.0.0", "kind" -> "video"),
      "artifact" -> Json.obj(
        "status" -> status,
        "files" -> Json.arr(Json.obj(
          "type" -> "video",
          "version" -> "1.0.0",
          "extension" -> "mp4",
          "warehousePath" -> "repository/video/example-video/1.0.0/example-video-1.0.0.mp4",
          "sha256" -> sha256
        ))
      )
    )

  private def _tree(root: Path): Vector[(String, String)] = {
    val stream = Files.walk(root)
    try stream.iterator().asScala.toVector.filter(Files.isRegularFile(_)).sortBy(_.toString).map { path =>
      root.relativize(path).toString -> _sha256(path)
    }
    finally stream.close()
  }

  private def _sha256(path: Path): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = Files.readAllBytes(path)
    digest.digest(bytes).map(b => f"${b & 0xff}%02x").mkString
  }

  private def _delete(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try stream.iterator.asScala.toVector.sortBy(x => (-x.getNameCount, x.toString)).foreach(Files.deleteIfExists)
      finally stream.close()
    }
}
