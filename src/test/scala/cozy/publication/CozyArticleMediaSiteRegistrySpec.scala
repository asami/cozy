package cozy.publication

import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.smartdox.metadata.PublishMetadata.{ImageReference, VideoPresentation, VideoReference, VideoStatus}
import play.api.libs.json.{JsArray, JsNull, JsObject, JsString, Json}

/*
 * @since   Aug. 11, 2026
 * @version Aug. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyArticleMediaSiteRegistrySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "CozyArticleMediaRegistry site transaction" should {
    "merge strict SmartDox roles without integrity synthesis" which {
      "create the canonical strict-only owner for a first infographic" in {
        _with_root { root =>
          Given("an empty publication registry and one exact site infographic")
          val update = _infographic_update()

          When("the strict-only transaction merges the role")
          val result = CozyArticleMediaRegistry.transaction(root)(_.mergeSite(Vector(update), () => ()))

          Then("the canonical article-media bundle contains only the strict record")
          result.bundlePaths shouldBe Vector(root.resolve("article-media.json"))
          result.entryPaths shouldBe Vector("metadata/article-media/development-process/example.json")
          result.snapshot.entries.map(_.path) shouldBe Vector("metadata/article-media/development-process/example.json")
          Files.exists(root.resolve("article-media.json")) shouldBe true

          And("no Cozy integrity entry is created")
          result.snapshot.entries.exists(_.path.startsWith("metadata/article-media-integrity/")) shouldBe false
        }
      }

      "coalesce two article identities with the canonical owner into one replacement" in {
        _with_root { root =>
          Given("an empty publication registry and two distinct site infographics")
          val first = _infographic_update("development-process/first")
          val second = _infographic_update("development-process/second")

          When("both strict-only roles merge in one transaction")
          val result = CozyArticleMediaRegistry.transaction(root)(_.mergeSite(Vector(first, second), () => ()))

          Then("one canonical bundle replacement contains both deterministic strict records")
          result.bundlePaths shouldBe Vector(root.resolve("article-media.json"))
          result.entryPaths shouldBe Vector(
            "metadata/article-media/development-process/first.json",
            "metadata/article-media/development-process/second.json"
          )
          result.snapshot.entries.map(_.path) shouldBe result.entryPaths

          And("the shared-owner merge synthesizes no integrity records")
          result.snapshot.entries.count(_.path.startsWith("metadata/article-media-integrity/")) shouldBe 0
        }
      }

      "reuse an owner discovered through the complete integrity prefix" in {
        _with_root { root =>
          Given("an article whose only existing registry evidence is an integrity record")
          val integrity = _integrity("development-process/integrity-owner", "ja", CozyArticleMediaIntegrity.Role.Video)
          _write_bundle(root, "existing-owner", Vector(_entry(integrity.entryPath, integrity.metadata)))

          When("a strict-only infographic is merged for that article")
          val result = CozyArticleMediaRegistry.transaction(root)(_.mergeSite(Vector(
            _infographic_update("development-process/integrity-owner")
          ), () => ()))

          Then("the existing owner receives the strict record without moving or replacing integrity evidence")
          result.bundlePaths shouldBe Vector(root.resolve("existing-owner.json"))
          result.snapshot.entries.map(_.bundleName).distinct shouldBe Vector("existing-owner")
          result.snapshot.entries.map(_.path) shouldBe Vector(
            integrity.entryPath,
            "metadata/article-media/development-process/integrity-owner.json"
          )
          Files.exists(root.resolve("article-media.json")) shouldBe false
        }
      }

      "replace only the requested locale and role" in {
        _with_root { root =>
          Given("one owner with bilingual strict media, integrity evidence, generic metadata, and an unrelated article")
          val identity = "development-process/example"
          val strict = CozyArticleMediaPublication.produce(identity, Vector(
            CozyArticleMediaPublication.Variant("ja", video = Some(_external_video("https://youtu.be/old-example"))),
            CozyArticleMediaPublication.Variant("en", infographic = Some(_image("/en/development-process/images/example.png")))
          ))
          val integrity = _integrity(identity, "ja", CozyArticleMediaIntegrity.Role.Video)
          val unrelated = CozyArticleMediaPublication.produce("development-process/other", Vector(
            CozyArticleMediaPublication.Variant("ja", infographic = Some(_image("/ja/development-process/images/other.png")))
          ))
          _write_bundle(root, "owner", Vector(
            _entry(strict.entryPath, strict.metadata),
            _entry(integrity.entryPath, integrity.metadata),
            _entry(unrelated.entryPath, unrelated.metadata),
            _entry("metadata/generic.json", Json.obj("kept" -> true))
          ))
          val beforeintegrity = CozyArticleMediaRegistry.load(root).entries.find(_.path == integrity.entryPath).map(_.metadata)

          When("the Japanese external-link video is replaced")
          val result = CozyArticleMediaRegistry.transaction(root)(_.mergeSite(Vector(
            _video_update(identity, "ja", "https://www.youtube.com/watch?v=new-example")
          ), () => ()))
          val merged = result.snapshot.entries.find(_.path == strict.entryPath).map(_.metadata).get

          Then("only that nested role changes")
          (((merged \ "variants").as[JsObject] \ "ja" \ "video" \ "watch_url").as[String]) shouldBe "https://www.youtube.com/watch?v=new-example"
          (((merged \ "variants").as[JsObject] \ "en" \ "infographic" \ "public_path").as[String]) shouldBe "/en/development-process/images/example.png"

          And("all unrelated records, generic entries, and integrity evidence remain semantically unchanged")
          result.snapshot.entries.find(_.path == unrelated.entryPath).map(_.metadata) shouldBe Some(unrelated.metadata)
          result.snapshot.entries.find(_.path == "metadata/generic.json").map(_.metadata) shouldBe Some(Json.obj("kept" -> true))
          result.snapshot.entries.find(_.path == integrity.entryPath).map(_.metadata) shouldBe beforeintegrity
        }
      }
    }

    "reject unsafe strict-only plans atomically" which {
      "reject duplicate normalized role keys and split owners before replacement" in {
        _with_root { root =>
          Given("an owner split between a strict path and its complete integrity prefix")
          val identity = "development-process/example"
          val strict = CozyArticleMediaPublication.produce(identity, Vector(
            CozyArticleMediaPublication.Variant("ja", video = Some(_external_video("https://youtu.be/original")))
          ))
          val integrity = _integrity(identity, "ja", CozyArticleMediaIntegrity.Role.Video)
          _write_bundle(root, "strict-owner", Vector(_entry(strict.entryPath, strict.metadata)))
          _write_bundle(root, "integrity-owner", Vector(_entry(integrity.entryPath, integrity.metadata)))
          val strictbefore = _bundle_bytes(root.resolve("strict-owner.json"))
          val integritybefore = _bundle_bytes(root.resolve("integrity-owner.json"))

          When("duplicate normalized updates and a split-owner update are attempted")
          val duplicate = intercept[IllegalArgumentException](CozyArticleMediaRegistry.transaction(root)(_.mergeSite(Vector(
            _infographic_update(" development-process/other "),
            _infographic_update("development-process/other")
          ), () => ())))
          val split = intercept[IllegalArgumentException](CozyArticleMediaRegistry.transaction(root)(_.mergeSite(Vector(
            _video_update(identity, "ja", "https://youtu.be/new-example")
          ), () => ())))

          Then("both failures occur without a registry replacement")
          duplicate.getMessage should include("Duplicate article-media site role update")
          split.getMessage should include("multiple bundle owners")
          _bundle_bytes(root.resolve("strict-owner.json")) shouldBe strictbefore
          _bundle_bytes(root.resolve("integrity-owner.json")) shouldBe integritybefore
          Files.exists(root.resolve("article-media.json")) shouldBe false
        }
      }

      "reject a site-hosted video and a missing callback before mutation" in {
        _with_root { root =>
          Given("an existing generic owner bundle and a strict-only site-hosted video")
          _write_bundle(root, "owner", Vector(_entry("metadata/generic.json", Json.obj("kept" -> true))))
          val before = _bundle_bytes(root.resolve("owner.json"))
          val hosted = CozyArticleMediaRegistry.SiteRoleUpdate(
            "development-process/example",
            CozyArticleMediaPublication.Variant("ja", video = Some(VideoReference(
              VideoPresentation.SiteHosted,
              VideoStatus.Published,
              None,
              None,
              Some(new URI("/repository/video/example.mp4"))
            )))
          )

          When("the transaction receives a hosted video or a null replacement callback")
          val hostederror = intercept[IllegalArgumentException](CozyArticleMediaRegistry.transaction(root)(_.mergeSite(Vector(hosted), () => ())))
          val callbackerror = intercept[IllegalArgumentException](CozyArticleMediaRegistry.transaction(root)(_.mergeSite(Vector(_infographic_update()), null)))

          Then("strict-only mutation rejects both conditions without rewriting the owner")
          hostederror.getMessage should include("external-link")
          callbackerror.getMessage should include("before-replace callback")
          _bundle_bytes(root.resolve("owner.json")) shouldBe before
          Files.exists(root.resolve("article-media.json")) shouldBe false
        }
      }

      "reject a variant without a medium before creating a canonical owner" in {
        _with_root { root =>
          Given("an empty publication registry and a site variant without media")
          val update = CozyArticleMediaRegistry.SiteRoleUpdate(
            "development-process/example",
            CozyArticleMediaPublication.Variant("ja")
          )

          When("the strict-only transaction receives the incomplete variant")
          val error = intercept[IllegalArgumentException](CozyArticleMediaRegistry.transaction(root)(_.mergeSite(Vector(update), () => ())))

          Then("the exactly-one-medium contract rejects the update without a canonical bundle write")
          error.getMessage should include("exactly one medium")
          Files.exists(root.resolve("article-media.json")) shouldBe false
        }
      }

      "reject a variant with both media before creating a canonical owner" in {
        _with_root { root =>
          Given("an empty publication registry and a site variant with an infographic and external-link video")
          val update = CozyArticleMediaRegistry.SiteRoleUpdate(
            "development-process/example",
            CozyArticleMediaPublication.Variant(
              "ja",
              infographic = Some(_image("/ja/development-process/images/example.png")),
              video = Some(_external_video("https://youtu.be/example"))
            )
          )

          When("the strict-only transaction receives the ambiguous variant")
          val error = intercept[IllegalArgumentException](CozyArticleMediaRegistry.transaction(root)(_.mergeSite(Vector(update), () => ())))

          Then("the exactly-one-medium contract rejects the update without a canonical bundle write")
          error.getMessage should include("exactly one medium")
          Files.exists(root.resolve("article-media.json")) shouldBe false
        }
      }

      "leave registry bytes unchanged when its callback throws" in {
        _with_root { root =>
          Given("an existing generic registry bundle and a site infographic")
          _write_bundle(root, "owner", Vector(_entry("metadata/generic.json", Json.obj("kept" -> true))))
          val before = _bundle_bytes(root.resolve("owner.json"))

          When("the callback throws before replacement")
          val error = intercept[RuntimeException](CozyArticleMediaRegistry.transaction(root)(_.mergeSite(
            Vector(_infographic_update()),
            () => throw new RuntimeException("callback failure")
          )))

          Then("the existing bundle is byte-identical and no strict owner is created")
          error.getMessage shouldBe "callback failure"
          _bundle_bytes(root.resolve("owner.json")) shouldBe before
          Files.exists(root.resolve("article-media.json")) shouldBe false
        }
      }

      "reject callback-driven snapshot drift without creating a transaction-generated strict record" in {
        _with_root { root =>
          Given("an existing generic registry bundle and a callback that mutates it")
          _write_bundle(root, "owner", Vector(_entry("metadata/generic.json", Json.obj("revision" -> 1))))
          val before = _bundle_bytes(root.resolve("owner.json"))

          When("the callback changes a configured bundle before revalidation")
          val error = intercept[IllegalArgumentException](CozyArticleMediaRegistry.transaction(root)(_.mergeSite(
            Vector(_infographic_update()),
            () => _write_bundle(root, "owner", Vector(_entry("metadata/generic.json", Json.obj("revision" -> 2))))
          )))

          Then("the callback mutation may remain, but no transaction-generated strict record is created")
          error.getMessage should include("stale configured snapshot")
          _bundle_bytes(root.resolve("owner.json")) should not be before
          CozyArticleMediaRegistry.load(root).entries.map(_.path) shouldBe Vector("metadata/generic.json")
          Files.exists(root.resolve("article-media.json")) shouldBe false
        }
      }
    }

    "run before replacement under the current transaction owner" which {
      "allow the callback to use the active owner-thread transaction snapshot" in {
        _with_root { root =>
          Given("an active transaction callback owner and one site infographic")
          val ownerthread = Thread.currentThread
          var callbackthread: Thread = null
          var callbacksnapshot: CozyArticleMediaRegistry.Snapshot = null

          When("the strict-only callback runs immediately before replacement")
          CozyArticleMediaRegistry.transaction(root) { transaction =>
            transaction.mergeSite(Vector(_infographic_update()), () => {
              callbackthread = Thread.currentThread
              callbacksnapshot = transaction.snapshot
            })
          }

          Then("the callback remains on the transaction owner thread with its active snapshot")
          callbackthread shouldBe ownerthread
          callbacksnapshot.entries shouldBe empty
        }
      }
    }

    "validate site plans read-only" which {
      "plan a strict site merge against an existing snapshot without creating a lock or bundle" in {
        _with_root { root =>
          Given("an existing generic publication bundle and one exact site infographic")
          _write_bundle(root, "owner", Vector(_entry("metadata/generic.json", Json.obj("kept" -> true))))
          val before = _bundle_bytes(root.resolve("owner.json"))

          When("the strict-only site plan is validated without a transaction")
          CozyArticleMediaRegistry.validateSiteReadOnly(root, Vector(_infographic_update()))

          Then("the existing bundle remains byte-identical")
          _bundle_bytes(root.resolve("owner.json")) shouldBe before

          And("no registry lock or canonical strict bundle is created")
          Files.exists(root.resolve(".cozy-publication-registry.lock")) shouldBe false
          Files.exists(root.resolve("article-media.json")) shouldBe false
        }
      }

      "reject duplicate site keys before a read-only registry mutation" in {
        _with_root { root =>
          Given("an existing generic publication bundle and duplicate normalized strict site roles")
          _write_bundle(root, "owner", Vector(_entry("metadata/generic.json", Json.obj("kept" -> true))))
          val before = _bundle_bytes(root.resolve("owner.json"))

          When("read-only planning receives the duplicate role key")
          val error = intercept[IllegalArgumentException](CozyArticleMediaRegistry.validateSiteReadOnly(root, Vector(
            _infographic_update(" development-process/example "),
            _infographic_update("development-process/example")
          )))

          Then("the duplicate is rejected without a lock, bundle replacement, or canonical owner")
          error.getMessage should include("Duplicate article-media site role update")
          _bundle_bytes(root.resolve("owner.json")) shouldBe before
          Files.exists(root.resolve(".cozy-publication-registry.lock")) shouldBe false
          Files.exists(root.resolve("article-media.json")) shouldBe false
        }
      }

      "reject multiple owners before a read-only registry mutation" in {
        _with_root { root =>
          Given("separate strict and integrity owners for one article identity")
          val identity = "development-process/example"
          val strict = CozyArticleMediaPublication.produce(identity, Vector(
            CozyArticleMediaPublication.Variant("ja", video = Some(_external_video("https://youtu.be/original")))
          ))
          val integrity = _integrity(identity, "ja", CozyArticleMediaIntegrity.Role.Video)
          _write_bundle(root, "strict-owner", Vector(_entry(strict.entryPath, strict.metadata)))
          _write_bundle(root, "integrity-owner", Vector(_entry(integrity.entryPath, integrity.metadata)))
          val strictbefore = _bundle_bytes(root.resolve("strict-owner.json"))
          val integritybefore = _bundle_bytes(root.resolve("integrity-owner.json"))

          When("read-only planning resolves the split identity")
          val error = intercept[IllegalArgumentException](CozyArticleMediaRegistry.validateSiteReadOnly(root, Vector(
            _infographic_update(identity)
          )))

          Then("ownership conflict rejects without a lock or replacement")
          error.getMessage should include("multiple bundle owners")
          _bundle_bytes(root.resolve("strict-owner.json")) shouldBe strictbefore
          _bundle_bytes(root.resolve("integrity-owner.json")) shouldBe integritybefore
          Files.exists(root.resolve(".cozy-publication-registry.lock")) shouldBe false
          Files.exists(root.resolve("article-media.json")) shouldBe false
        }
      }

      "reject null callbacks and callback-driven snapshot drift without a registry mutation" in {
        _with_root { root =>
          Given("an existing generic publication bundle and a site infographic")
          _write_bundle(root, "owner", Vector(_entry("metadata/generic.json", Json.obj("revision" -> 1))))
          val before = _bundle_bytes(root.resolve("owner.json"))

          When("the read-only callback is absent or changes the snapshot")
          val nullerror = intercept[IllegalArgumentException](CozyArticleMediaRegistry.validateSiteReadOnly(
            root,
            Vector(_infographic_update()),
            null
          ))
          val drifterror = intercept[IllegalArgumentException](CozyArticleMediaRegistry.validateSiteReadOnly(
            root,
            Vector(_infographic_update()),
            () => _write_bundle(root, "owner", Vector(_entry("metadata/generic.json", Json.obj("revision" -> 2))))
          ))

          Then("both callback boundaries reject before a strict replacement or lock creation")
          nullerror.getMessage should include("before-revalidation callback")
          drifterror.getMessage should include("stale read-only snapshot")
          _bundle_bytes(root.resolve("owner.json")) should not be before
          CozyArticleMediaRegistry.load(root).entries.map(_.path) shouldBe Vector("metadata/generic.json")
          Files.exists(root.resolve(".cozy-publication-registry.lock")) shouldBe false
          Files.exists(root.resolve("article-media.json")) shouldBe false
        }
      }
    }
  }

  private def _infographic_update(identity: String = "development-process/example", locale: String = "ja"): CozyArticleMediaRegistry.SiteRoleUpdate =
    CozyArticleMediaRegistry.SiteRoleUpdate(
      identity,
      CozyArticleMediaPublication.Variant(locale, infographic = Some(_image(s"/$locale/development-process/images/example.png")))
    )

  private def _video_update(identity: String, locale: String, watchurl: String): CozyArticleMediaRegistry.SiteRoleUpdate =
    CozyArticleMediaRegistry.SiteRoleUpdate(
      identity,
      CozyArticleMediaPublication.Variant(locale, video = Some(_external_video(watchurl)))
    )

  private def _image(publicpath: String): ImageReference =
    ImageReference(new URI(publicpath), Some("image/png"), Some("Example image"))

  private def _external_video(watchurl: String): VideoReference =
    VideoReference(VideoPresentation.ExternalLink, VideoStatus.Published, Some("youtube"), Some(new URI(watchurl)), None)

  private def _integrity(
    identity: String,
    locale: String,
    role: CozyArticleMediaIntegrity.Role
  ): CozyArticleMediaIntegrity.Result =
    CozyArticleMediaIntegrity.produce(CozyArticleMediaIntegrity.Input(
      articleIdentity = identity,
      locale = locale,
      role = role,
      artifact = CozyArticleMediaIntegrity.Artifact("example", "1.0.0"),
      publicPath = new URI("/repository/video/example.mp4"),
      repositoryPath = "video/example.mp4",
      mediaType = "video/mp4",
      sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
      provenance = CozyArticleMediaIntegrity.VideoPublication("metadata/video/example/manifest.json", "metadata/artifacts/repository/example.json"),
      publicationState = CozyArticleMediaIntegrity.PublicationState.Published
    ))

  private def _entry(path: String, metadata: JsObject): JsObject =
    Json.obj(
      "path" -> path,
      "key" -> JsString(path.stripPrefix("metadata/").stripSuffix(".json")),
      "metadata" -> metadata
    )

  private def _write_bundle(root: Path, name: String, entries: Vector[JsObject]): Unit = {
    val bundle = Json.obj(
      "schema" -> "cozy.publish-project.v1",
      "type" -> "publication-bundle",
      "publication" -> Json.obj("name" -> name),
      "sourceRepository" -> "cozy",
      "sourcePath" -> ".",
      "sourceCommit" -> JsNull,
      "entries" -> JsArray(entries)
    )
    Files.write(root.resolve(s"$name.json"), (Json.prettyPrint(bundle) + "\n").getBytes(StandardCharsets.UTF_8))
  }

  private def _bundle_bytes(path: Path): Vector[Byte] =
    Files.readAllBytes(path).toVector

  private def _with_root[A](f: Path => A): A = {
    val workroot = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve("target/cozy-article-media-site-registry/work")
    Files.createDirectories(workroot)
    val directory = Files.createTempDirectory(workroot, "registry-")
    try f(directory)
    finally _delete(directory)
  }

  private def _delete(path: Path): Unit = {
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try stream.iterator.asScala.toVector.sortBy(x => (-x.getNameCount, x.toString)).foreach(Files.deleteIfExists)
      finally stream.close()
    }
  }
}
