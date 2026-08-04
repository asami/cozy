package cozy.publication

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.security.MessageDigest
import scala.collection.JavaConverters._
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsArray, JsObject, JsString, JsValue, Json}

/*
 * @since   Aug.  4, 2026
 * @version Aug.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyArticleMediaVideoEvidenceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "CozyArticleMediaVideoEvidence" should {
    "project the exact configured video evidence" which {
      "produce canonical integrity from the selected metadata and configured repository bytes" in {
        Given("a configured snapshot with exact manifest and repository entries plus conflicting legacy public-path decoys")
        _with_fixture() { fixture =>
          val input = _input(fixture)

          When("the explicit video evidence is projected")
          val result = CozyArticleMediaVideoEvidence.project(input)
          val record = result.integrity.record

          Then("the canonical integrity uses only repositoryPublicPath, stripped warehousePath, and exact provenance")
          record.articleIdentity shouldBe "development-process/example"
          record.locale shouldBe "ja"
          record.role shouldBe CozyArticleMediaIntegrity.Role.Video
          record.artifact shouldBe CozyArticleMediaIntegrity.Artifact("example-video", "1.0.0")
          record.publicPath.toString shouldBe "/repository/video/example-video/1.0.0/example-video-1.0.0.mp4"
          record.publicPath.toString should not be "/legacy-site-public-path.mp4"
          record.publicPath.toString should not be "/repository/video/legacy-public-path-decoy.mp4"
          record.publicPath.toString should not be "/registry-public-path-decoy.mp4"
          record.repositoryPath shouldBe "video/example-video/1.0.0/example-video-1.0.0.mp4"
          record.mediaType shouldBe "video/mp4"
          record.sha256 shouldBe fixture.sha256
          record.provenance shouldBe CozyArticleMediaIntegrity.VideoPublication(
            "metadata/video/example-video/1.0.0/manifest.json",
            "metadata/artifacts/repository/example-video.json"
          )
          record.publicationState shouldBe CozyArticleMediaIntegrity.PublicationState.Published
          result.integrity.entryPath shouldBe "metadata/article-media-integrity/development-process/example/ja/video.json"
          result.artifactPath shouldBe fixture.artifact.toRealPath()
        }
      }

      "map every Cozy-owned registry status without accepting SmartDox input or status" in {
        Given("otherwise identical exact evidence with each admitted Cozy registry status")
        val statuses = Vector(
          "registered" -> CozyArticleMediaIntegrity.PublicationState.Registered,
          "published" -> CozyArticleMediaIntegrity.PublicationState.Published,
          "withdrawn" -> CozyArticleMediaIntegrity.PublicationState.Withdrawn
        )

        When("each configured snapshot is projected")
        val states = statuses.map { case (status, expected) =>
          _with_fixture(status) { fixture =>
            CozyArticleMediaVideoEvidence.project(_input(fixture)).integrity.record.publicationState -> expected
          }
        }

        Then("the status maps one-to-one as Cozy admission evidence")
        states.foreach { case (actual, expected) => actual shouldBe expected }
      }
    }

    "select evidence only by exact configured snapshot paths" which {
      "ignore nested and unrelated decoys without scanning any directory" in {
        Given("exact snapshot evidence and malformed nested repository and metadata decoys")
        _with_fixture() { fixture =>
          Files.createDirectories(fixture.root.resolve("metadata/video/example-video/1.0.0"))
          Files.write(fixture.root.resolve("metadata/video/example-video/1.0.0/manifest.json"), "not configured".getBytes(StandardCharsets.UTF_8))
          Files.createDirectories(fixture.repositoryroot.resolve("nested/decoy"))
          Files.write(fixture.repositoryroot.resolve("nested/decoy/unrelated.mp4"), "unrelated".getBytes(StandardCharsets.UTF_8))
          val decoys = Vector(
            _entry("metadata/videos/example-video/metadata.json", Json.obj("malformed" -> true)),
            _entry("metadata/video/example-video/latest.json", Json.obj("malformed" -> true)),
            _entry("metadata/unrelated/decoy.json", Json.obj("malformed" -> true))
          )
          val snapshot = _snapshot(fixture, fixture.manifest, fixture.registry, decoys)

          When("the projector resolves its exact two configured paths")
          val result = CozyArticleMediaVideoEvidence.project(_input(fixture, snapshot = snapshot))

          Then("only the supplied exact entry objects and exact artifact candidate affect the result")
          result.integrity.record.sha256 shouldBe fixture.sha256
          result.artifactPath shouldBe fixture.artifact.toRealPath()
        }
      }

      "reject missing and ambiguous exact manifest or registry entries deterministically" in {
        Given("snapshots with absent or duplicate exact selected paths")
        _with_fixture() { fixture =>
          val manifestpath = "metadata/video/example-video/1.0.0/manifest.json"
          val registrypath = "metadata/artifacts/repository/example-video.json"
          val missingmanifest = CozyArticleMediaRegistry.Snapshot(Vector(_entry(registrypath, fixture.registry)))
          val missingregistry = CozyArticleMediaRegistry.Snapshot(Vector(_entry(manifestpath, fixture.manifest)))
          val ambiguousmanifest = CozyArticleMediaRegistry.Snapshot(Vector(_entry(manifestpath, fixture.manifest), _entry(manifestpath, fixture.manifest), _entry(registrypath, fixture.registry)))
          val ambiguousregistry = CozyArticleMediaRegistry.Snapshot(Vector(_entry(manifestpath, fixture.manifest), _entry(registrypath, fixture.registry), _entry(registrypath, fixture.registry)))

          When("exact selection is attempted")
          val errors = Vector(
            intercept[IllegalArgumentException](CozyArticleMediaVideoEvidence.project(_input(fixture, snapshot = missingmanifest))),
            intercept[IllegalArgumentException](CozyArticleMediaVideoEvidence.project(_input(fixture, snapshot = missingregistry))),
            intercept[IllegalArgumentException](CozyArticleMediaVideoEvidence.project(_input(fixture, snapshot = ambiguousmanifest))),
            intercept[IllegalArgumentException](CozyArticleMediaVideoEvidence.project(_input(fixture, snapshot = ambiguousregistry)))
          )

          Then("the configured-path diagnostics distinguish missing from ambiguity")
          errors.take(2).foreach(_.getMessage should include("missing"))
          errors.drop(2).foreach(_.getMessage should include("ambiguous"))
        }
      }
    }

    "reject malformed and inconsistent explicit evidence" which {
      "reject non-object metadata, schema/type defects, and wrong selected identities" in {
        Given("a table of malformed manifest and registry object contracts")
        _with_fixture() { fixture =>
          val variants = Vector(
            _snapshot(fixture, JsString("manifest"), fixture.registry),
            _snapshot(fixture, fixture.manifest + ("schema" -> JsString("wrong")), fixture.registry),
            _snapshot(fixture, fixture.manifest + ("type" -> JsString("wrong")), fixture.registry),
            _snapshot(fixture, fixture.manifest + ("video" -> Json.obj("name" -> "other", "version" -> "1.0.0")), fixture.registry),
            _snapshot(fixture, fixture.manifest + ("video" -> Json.obj("name" -> "example-video", "version" -> "2.0.0")), fixture.registry),
            _snapshot(fixture, fixture.manifest, JsString("registry")),
            _snapshot(fixture, fixture.manifest, fixture.registry + ("schema" -> JsString("wrong"))),
            _snapshot(fixture, fixture.manifest, fixture.registry + ("type" -> JsString("wrong"))),
            _snapshot(fixture, fixture.manifest, fixture.registry + ("project" -> Json.obj("name" -> "other", "version" -> "1.0.0", "kind" -> "video"))),
            _snapshot(fixture, fixture.manifest, fixture.registry + ("project" -> Json.obj("name" -> "example-video", "version" -> "2.0.0", "kind" -> "video"))),
            _snapshot(fixture, fixture.manifest, fixture.registry + ("project" -> Json.obj("name" -> "example-video", "version" -> "1.0.0", "kind" -> "audio")))
          )

          When("each exact metadata object is validated")
          val errors = variants.map { snapshot =>
            intercept[IllegalArgumentException](CozyArticleMediaVideoEvidence.project(_input(fixture, snapshot = snapshot)))
          }

          Then("every schema, type, and selected identity defect fails deterministically")
          errors.size shouldBe variants.size
          errors.map(_.getMessage).foreach(_ should not be empty)
        }
      }

      "reject missing or invalid manifest artifact fields and registry selected-file fields" in {
        Given("manifest and registry variants that cannot establish one exact MP4 evidence record")
        _with_fixture() { fixture =>
          val artifact = (fixture.manifest \ "artifact").as[JsObject]
          val files = ((fixture.registry \ "artifact").as[JsObject] \ "files").as[JsArray]
          val file = files.value.head.as[JsObject]
          val variants = Vector(
            _snapshot(fixture, fixture.manifest - "artifact", fixture.registry),
            _snapshot(fixture, fixture.manifest + ("artifact" -> (artifact - "warehousePath")), fixture.registry),
            _snapshot(fixture, fixture.manifest + ("artifact" -> (artifact + ("warehousePath" -> JsString("repository/../escape.mp4")))), fixture.registry),
            _snapshot(fixture, fixture.manifest + ("artifact" -> (artifact + ("warehousePath" -> JsString("repository/video/example-video/1.0.0/example.webm")))), fixture.registry),
            _snapshot(fixture, fixture.manifest + ("artifact" -> (artifact - "repositoryPublicPath")), fixture.registry),
            _snapshot(fixture, fixture.manifest + ("artifact" -> (artifact + ("repositoryPublicPath" -> JsString("/repository/video/example.mp4")))), fixture.registry),
            _snapshot(fixture, fixture.manifest + ("artifact" -> (artifact + ("sha256" -> JsString("A" * 64)))), fixture.registry),
            _snapshot(fixture, fixture.manifest, fixture.registry + ("artifact" -> (((fixture.registry \ "artifact").as[JsObject]) - "files"))),
            _snapshot(fixture, fixture.manifest, _registry_with_files(fixture, Vector.empty)),
            _snapshot(fixture, fixture.manifest, _registry_with_files(fixture, Vector(file, file))),
            _snapshot(fixture, fixture.manifest, _registry_with_files(fixture, Vector(file + ("extension" -> JsString("webm"))))),
            _snapshot(fixture, fixture.manifest, _registry_with_files(fixture, Vector(file + ("type" -> JsString("audio"))))),
            _snapshot(fixture, fixture.manifest, _registry_with_files(fixture, Vector(file + ("version" -> JsString("2.0.0"))))),
            _snapshot(fixture, fixture.manifest, _registry_with_files(fixture, Vector(file + ("warehousePath" -> JsString("repository/video/other.mp4"))))),
            _snapshot(fixture, fixture.manifest, _registry_with_files(fixture, Vector(file + ("sha256" -> JsString("b" * 64))))),
            _snapshot(fixture, fixture.manifest, _registry_with_status(fixture, "draft"))
          )

          When("the artifact evidence and selected registry file are checked")
          val errors = variants.map { snapshot =>
            intercept[IllegalArgumentException](CozyArticleMediaVideoEvidence.project(_input(fixture, snapshot = snapshot)))
          }

          Then("each required field, selected file predicate, digest, and admission state is rejected")
          errors.size shouldBe variants.size
          errors.map(_.getMessage).foreach(_ should not be empty)
        }
      }
    }

    "enforce configured repository containment and bytes for every admission state" which {
      "reject manifest-registry disagreement, stale bytes, missing files, invalid roots, lexical escapes, and symlink escapes" in {
        Given("independent exact metadata and repository filesystem failures")
        _with_fixture() { fixture =>
          val artifact = (fixture.manifest \ "artifact").as[JsObject]
          val registryfile = (((fixture.registry \ "artifact").as[JsObject] \ "files").as[JsArray]).value.head.as[JsObject]
          val disagreement = _registry_with_files(fixture, Vector(registryfile + ("sha256" -> JsString("b" * 64))))
          val mismatch = _snapshot(fixture, fixture.manifest, disagreement)

          When("the exact evidence digests disagree")
          val disagreementerror = intercept[IllegalArgumentException](CozyArticleMediaVideoEvidence.project(_input(fixture, snapshot = mismatch)))

          Then("manifest and registry disagreement is rejected before projection")
          disagreementerror.getMessage should include("must match")

          Given("a selected artifact whose bytes no longer match both metadata digests")
          Files.write(fixture.artifact, "stale bytes".getBytes(StandardCharsets.UTF_8))

          When("the configured candidate bytes are hashed")
          val byteerror = intercept[IllegalArgumentException](CozyArticleMediaVideoEvidence.project(_input(fixture)))

          Then("actual byte mismatch fails deterministically")
          byteerror.getMessage should include("does not match")
        }

        _with_fixture() { fixture =>
          Given("a missing exact artifact and an invalid configured root")
          Files.delete(fixture.artifact)

          When("the evidence attempts configured-root resolution")
          val missingerror = intercept[IllegalArgumentException](CozyArticleMediaVideoEvidence.project(_input(fixture)))
          val rooterror = intercept[IllegalArgumentException](CozyArticleMediaVideoEvidence.project(_input(fixture, repositoryroot = fixture.root.resolve("missing"))))

          Then("both missing artifact and root conditions fail")
          missingerror.getMessage should include("regular file")
          rooterror.getMessage should include("existing directory")
        }

        _with_fixture() { fixture =>
          Given("a lexical warehouse escape and an external symbolic-link target")
          val artifact = (fixture.manifest \ "artifact").as[JsObject]
          val lexical = _snapshot(fixture, fixture.manifest + ("artifact" -> (artifact + ("warehousePath" -> JsString("repository/../external.mp4")))), fixture.registry)
          val lexicalerror = intercept[IllegalArgumentException](CozyArticleMediaVideoEvidence.project(_input(fixture, snapshot = lexical)))
          val external = fixture.root.resolve("external.mp4")
          Files.write(external, "external bytes".getBytes(StandardCharsets.UTF_8))
          val before = Files.readAllBytes(external).toVector
          Files.delete(fixture.artifact)
          try Files.createSymbolicLink(fixture.artifact, external)
          catch {
            case _: UnsupportedOperationException | _: SecurityException | _: IOException => cancel("The platform cannot create symbolic links for this specification")
          }

          When("the real selected candidate path is checked")
          val linkerror = intercept[IllegalArgumentException](CozyArticleMediaVideoEvidence.project(_input(fixture)))

          Then("both lexical and real-path escapes fail without changing external bytes")
          lexicalerror.getMessage should include("normalized relative path")
          linkerror.getMessage should include("real path escapes")
          Files.readAllBytes(external).toVector shouldBe before
        }
      }
    }

    "reject null and unsafe inputs before evidence projection" which {
      "fail null input, forged null snapshot structure, unsafe segments, and noncanonical identity values" in {
        Given("invalid public input values and forged snapshot records")
        _with_fixture() { fixture =>
          val invalids = Vector(
            () => CozyArticleMediaVideoEvidence.project(null),
            () => CozyArticleMediaVideoEvidence.project(_input(fixture).copy(snapshot = null)),
            () => CozyArticleMediaVideoEvidence.project(_input(fixture, snapshot = CozyArticleMediaRegistry.Snapshot(null))),
            () => CozyArticleMediaVideoEvidence.project(_input(fixture, snapshot = CozyArticleMediaRegistry.Snapshot(Vector(null)))),
            () => CozyArticleMediaVideoEvidence.project(_input(fixture).copy(repositoryRoot = null)),
            () => CozyArticleMediaVideoEvidence.project(_input(fixture).copy(videoName = "../video")),
            () => CozyArticleMediaVideoEvidence.project(_input(fixture).copy(videoVersion = "1.0.0/other")),
            () => CozyArticleMediaVideoEvidence.project(_input(fixture).copy(videoName = " example-video")),
            () => CozyArticleMediaVideoEvidence.project(_input(fixture).copy(videoVersion = "..")),
            () => CozyArticleMediaVideoEvidence.project(_input(fixture).copy(articleIdentity = "/development-process/example")),
            () => CozyArticleMediaVideoEvidence.project(_input(fixture).copy(locale = "ja-jp"))
          )

          When("input normalization runs before exact evidence selection")
          val errors = invalids.map(x => intercept[IllegalArgumentException](x()))

          Then("all input failures are deterministic contract failures")
          errors.size shouldBe invalids.size
          errors.map(_.getMessage).foreach(_ should not be empty)
        }
      }

      "retain deterministic output for generated admitted status orderings" in {
        Given("generated permutations of the three Cozy admission states and reset fixtures")
        val orders = Gen.someOf(Vector("registered", "published", "withdrawn")).map(_.toVector)
        val property = Prop.forAll(orders) { statuses =>
          statuses.forall { status =>
            _with_fixture(status) { fixture =>
              val expected = status
              CozyArticleMediaVideoEvidence.project(_input(fixture)).integrity.record.publicationState.name == expected
            }
          }
        }

        When("ScalaCheck evaluates independently reset configured evidence")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(30), property)

        Then("each exact status remains its deterministic Cozy-owned state")
        result.passed shouldBe true
      }
    }
  }

  private final class Fixture(
    val root: Path,
    val repositoryroot: Path,
    val artifact: Path,
    val sha256: String,
    val manifest: JsObject,
    val registry: JsObject
  )

  private def _with_fixture[A](status: String = "published")(f: Fixture => A): A = {
    val workroot = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve("target/cozy-article-media-video-evidence/work")
    Files.createDirectories(workroot)
    val root = Files.createTempDirectory(workroot, "evidence-")
    try f(_fixture(root, status))
    finally _delete(root)
  }

  private def _fixture(root: Path, status: String): Fixture = {
    val repositoryroot = Files.createDirectories(root.resolve("repository"))
    val artifact = repositoryroot.resolve("video/example-video/1.0.0/example-video-1.0.0.mp4")
    Files.createDirectories(artifact.getParent)
    Files.write(artifact, "exact selected video bytes".getBytes(StandardCharsets.UTF_8))
    val sha256 = _sha256(artifact)
    new Fixture(root, repositoryroot, artifact, sha256, _manifest(sha256), _registry(sha256, status))
  }

  private def _input(
    fixture: Fixture,
    snapshot: CozyArticleMediaRegistry.Snapshot = null,
    repositoryroot: Path = null
  ): CozyArticleMediaVideoEvidence.Input =
    CozyArticleMediaVideoEvidence.Input(
      articleIdentity = "development-process/example",
      locale = "ja",
      videoName = "example-video",
      videoVersion = "1.0.0",
      snapshot = Option(snapshot).getOrElse(_canonical_snapshot(fixture)),
      repositoryRoot = Option(repositoryroot).getOrElse(fixture.repositoryroot)
    )

  private def _canonical_snapshot(fixture: Fixture): CozyArticleMediaRegistry.Snapshot =
    _snapshot(fixture, fixture.manifest, fixture.registry)

  private def _snapshot(
    fixture: Fixture,
    manifest: JsValue,
    registry: JsValue,
    extra: Vector[CozyArticleMediaRegistry.Entry] = Vector.empty
  ): CozyArticleMediaRegistry.Snapshot =
    CozyArticleMediaRegistry.Snapshot(Vector(
      _entry("metadata/video/example-video/1.0.0/manifest.json", manifest),
      _entry("metadata/artifacts/repository/example-video.json", registry)
    ) ++ extra)

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
        "publicPath" -> "repository/video/legacy-public-path-decoy.mp4",
        "sitePublicPath" -> "/legacy-site-public-path.mp4",
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
          "publicPath" -> "/registry-public-path-decoy.mp4",
          "sitePublicPath" -> "/registry-site-public-path-decoy.mp4",
          "sha256" -> sha256
        ))
      )
    )

  private def _registry_with_files(fixture: Fixture, files: Vector[JsObject]): JsObject =
    fixture.registry + ("artifact" -> (((fixture.registry \ "artifact").as[JsObject]) + ("files" -> JsArray(files))))

  private def _registry_with_status(fixture: Fixture, status: String): JsObject =
    fixture.registry + ("artifact" -> (((fixture.registry \ "artifact").as[JsObject]) + ("status" -> JsString(status))))

  private def _sha256(path: Path): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val in = Files.newInputStream(path)
    val buffer = new Array[Byte](8192)
    try {
      var n = in.read(buffer)
      while (n >= 0) {
        if (n > 0)
          digest.update(buffer, 0, n)
        n = in.read(buffer)
      }
    } finally {
      in.close()
    }
    digest.digest().map(b => f"${b & 0xff}%02x").mkString
  }

  private def _delete(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try stream.iterator.asScala.toVector.sortBy(x => (-x.getNameCount, x.toString)).foreach(Files.deleteIfExists)
      finally stream.close()
    }
}
