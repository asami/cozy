package cozy.publication

import java.io.IOException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.concurrent.{ConcurrentLinkedQueue, CountDownLatch}
import scala.collection.JavaConverters._
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.smartdox.metadata.PublishMetadata.{VideoPresentation, VideoReference, VideoStatus}
import play.api.libs.json.{JsArray, JsNull, JsObject, JsString, Json}

/*
 * @since   Aug.  4, 2026
 * @version Aug. 12, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyArticleMediaRegistrySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "CozyArticleMediaRegistry" should {
    "load configured publication bundles" which {
      "reject null and non-directory configured roots" in {
        Given("a null root and a regular file path")
        _with_root { root =>
          val file = root.resolve("not-a-directory")
          Files.write(file, Vector.empty[Byte].toArray)

          When("the registry loads either invalid root")
          val nullerror = intercept[IllegalArgumentException](CozyArticleMediaRegistry.load(null))
          val fileerror = intercept[IllegalArgumentException](CozyArticleMediaRegistry.load(file))

          Then("both roots are rejected as existing directories")
          nullerror.getMessage should include("existing directory")
          fileerror.getMessage should include("existing directory")
        }
      }

      "preserve exact metadata and sort direct entries by path and bundle name" in {
        Given("two direct configured bundles with deliberately reversed entry order")
        _with_root { root =>
          val alpha = Json.obj("kind" -> "alpha", "nested" -> Json.obj("kept" -> true))
          val beta = Json.obj("kind" -> "beta", "values" -> Json.arr(2, 1))
          _write_bundle(root, "zeta", Vector(_entry("metadata/z.json", beta)))
          _write_bundle(root, "alpha", Vector(_entry("metadata/a.json", alpha)))

          When("the registry loads the exact configured root")
          val snapshot = CozyArticleMediaRegistry.load(root)

          Then("entries have deterministic path order and preserve their JSON values")
          snapshot.entries.map(x => (x.bundleName, x.path, x.key)) shouldBe Vector(
            ("alpha", "metadata/a.json", "a"),
            ("zeta", "metadata/z.json", "z")
          )
          snapshot.entries.head.metadata shouldBe alpha
          snapshot.entries(1).metadata shouldBe beta
        }
      }

      "ignore a malformed nested target decoy without opening it" in {
        Given("one valid direct bundle and malformed JSON below a nested target directory")
        _with_root { root =>
          _write_bundle(root, "publication", Vector(_entry("metadata/existing.json", Json.obj("kept" -> true))))
          val target = Files.createDirectories(root.resolve("target/generated"))
          Files.write(target.resolve("decoy.json"), "not json".getBytes(StandardCharsets.UTF_8))

          When("the registry loads direct configured bundles")
          val snapshot = CozyArticleMediaRegistry.load(root)

          Then("only the direct configured entry is present")
          snapshot.entries.map(_.path) shouldBe Vector("metadata/existing.json")
        }
      }

      "reject malformed direct JSON" in {
        Given("a malformed direct configured bundle")
        _with_root { root =>
          Files.write(root.resolve("publication.json"), "not json".getBytes(StandardCharsets.UTF_8))

          When("the registry loads it")
          val error = intercept[IllegalArgumentException](CozyArticleMediaRegistry.load(root))

          Then("the malformed bundle is rejected")
          error.getMessage should include("malformed JSON")
        }
      }

      "reject a direct non-bundle JSON file" in {
        Given("a direct JSON file with a different type")
        _with_root { root =>
          Files.write(root.resolve("publication.json"), Json.stringify(Json.obj("type" -> "decoy")).getBytes(StandardCharsets.UTF_8))

          When("the registry loads configured bundles")
          val error = intercept[IllegalArgumentException](CozyArticleMediaRegistry.load(root))

          Then("the non-bundle file is rejected")
          error.getMessage should include("invalid type")
        }
      }

      "reject invalid publication declarations, entries, and entry shapes" in {
        Given("direct bundles with an invalid publication object, name, entries value, entry value, or missing metadata")
        val invalids = Vector(
          "publication-object" -> Json.obj("type" -> "publication-bundle", "publication" -> "invalid", "entries" -> Json.arr()),
          "publication-name" -> Json.obj("type" -> "publication-bundle", "publication" -> Json.obj("name" -> " "), "entries" -> Json.arr()),
          "entries" -> Json.obj("type" -> "publication-bundle", "publication" -> Json.obj("name" -> "entries"), "entries" -> Json.obj()),
          "entry" -> Json.obj("type" -> "publication-bundle", "publication" -> Json.obj("name" -> "entry"), "entries" -> Json.arr("invalid")),
          "metadata" -> Json.obj("type" -> "publication-bundle", "publication" -> Json.obj("name" -> "metadata"), "entries" -> Json.arr(Json.obj("path" -> "metadata/value.json", "key" -> "value")))
        )

        When("the registry loads each invalid direct bundle")
        val errors = invalids.map { case (filename, bundle) =>
          _with_root { root =>
            Files.write(root.resolve(s"$filename.json"), Json.stringify(bundle).getBytes(StandardCharsets.UTF_8))
            intercept[IllegalArgumentException](CozyArticleMediaRegistry.load(root)).getMessage
          }
        }

        Then("each structural contract is rejected")
        errors(0) should include("invalid publication")
        errors(1) should include("invalid publication.name")
        errors(2) should include("entries must be an array")
        errors(3) should include("entry must be an object")
        errors(4) should include("metadata is missing")
      }

      "reject invalid direct entry paths" in {
        Given("a direct configured bundle with a backslash entry path")
        _with_root { root =>
          _write_bundle(root, "publication", Vector(_entry("metadata\\invalid.json", Json.obj())))

          When("the registry validates bundle entry shape")
          val error = intercept[IllegalArgumentException](CozyArticleMediaRegistry.load(root))

          Then("the first invalid entry is rejected deterministically")
          error.getMessage should include("entry path is invalid")
        }
      }

      "reject a supplied key that differs from its canonical path key" in {
        Given("a direct configured bundle with a mismatched entry key")
        _with_root { root =>
          _write_bundle(root, "publication", Vector(_entry("metadata/valid.json", Json.obj(), "wrong")))

          When("the registry derives the canonical logical key")
          val error = intercept[IllegalArgumentException](CozyArticleMediaRegistry.load(root))

          Then("the mismatched declared key is rejected")
          error.getMessage should include("entry key is invalid")
        }
      }

      "reject duplicate normalized paths across direct bundles" in {
        Given("two direct configured bundles that own the same metadata path")
        _with_root { root =>
          _write_bundle(root, "alpha", Vector(_entry("metadata/shared.json", Json.obj("owner" -> "alpha"))))
          _write_bundle(root, "beta", Vector(_entry("metadata/shared.json", Json.obj("owner" -> "beta"))))

          When("the registry loads all direct configured bundles")
          val error = intercept[IllegalArgumentException](CozyArticleMediaRegistry.load(root))

          Then("the duplicate path identifies both deterministic owners")
          error.getMessage should include("Duplicate publication bundle path 'metadata/shared.json': alpha, beta")
        }
      }

      "reject duplicate canonical paths within one direct bundle" in {
        Given("one direct bundle declaring the same normalized metadata path twice")
        _with_root { root =>
          _write_bundle(root, "publication", Vector(
            _entry("metadata/shared.json", Json.obj("ordinal" -> 1)),
            _entry("metadata/shared.json", Json.obj("ordinal" -> 2))
          ))

          When("the registry loads its direct entries")
          val error = intercept[IllegalArgumentException](CozyArticleMediaRegistry.load(root))

          Then("the duplicate canonical tuple/path is rejected")
          error.getMessage should include("Duplicate publication bundle path 'metadata/shared.json': publication, publication")
        }
      }

      "derive injective canonical snapshot keys for distinct admitted metadata paths" in {
        Given("one direct bundle with distinct normalized metadata paths")
        _with_root { root =>
          _write_bundle(root, "publication", Vector(
            _entry("metadata/alpha.json", Json.obj()),
            _entry("metadata/alpha/nested.json", Json.obj()),
            _entry("metadata/beta.json", Json.obj())
          ))

          When("the registry derives each canonical snapshot key")
          val entries = CozyArticleMediaRegistry.load(root).entries

          Then("distinct admitted paths have distinct canonical keys")
          entries.map(_.path).distinct.size shouldBe entries.size
          entries.map(_.key).distinct.size shouldBe entries.size
        }
      }

      "reject direct filename and declaration identity mismatch before constructing a snapshot" in {
        Given("two direct bundle files whose declarations do not match their filename stems")
        _with_root { root =>
          _write_bundle(root, "zeta", Vector.empty, declaredname = "publication")
          _write_bundle(root, "alpha", Vector.empty, declaredname = "publication")

          When("the registry loads the configured root")
          val error = intercept[IllegalArgumentException](CozyArticleMediaRegistry.load(root))

        Then("the first sorted direct filename mismatch is reported")
        error.getMessage should include("publication.name must match filename stem: alpha.json")
        }
      }

      "reject a declaration whose name does not exactly match its direct filename stem" in {
        Given("a direct bundle file whose declared publication name differs from its filename")
        _with_root { root =>
          _write_bundle(root, "stored", Vector.empty, declaredname = "declared")

          When("the registry loads the configured root")
          val error = intercept[IllegalArgumentException](CozyArticleMediaRegistry.load(root))

          Then("the filename and declaration identity mismatch is rejected before snapshot construction")
          error.getMessage should include("match filename stem")
        }
      }

      "reject a canonical strict record stored under a different article-media path" in {
        Given("a strict record whose canonical metadata identity is stored under an alias path")
        _with_root { root =>
          val strict = _strict()
          _write_bundle(root, "publication", Vector(_entry("metadata/article-media/development-process/example-alias.json", strict.metadata)))

          When("the registry loads the recognized strict metadata namespace")
          val error = intercept[IllegalArgumentException](CozyArticleMediaRegistry.load(root))

          Then("the canonical metadata path binding is required before snapshot construction")
          error.getMessage should include("not canonical")
        }
      }

      "reject an integrity identity path that differs from its canonical metadata identity" in {
        Given("an integrity record stored under a different article identity")
        _with_root { root =>
          val integrity = _video_integrity()
          _write_bundle(root, "publication", Vector(_entry("metadata/article-media-integrity/development-process/other/ja/video.json", integrity.metadata)))
          val before = _bundle_bytes(root.resolve("publication.json"))

          When("a canonical strict upsert loads the recognized integrity metadata")
          val error = intercept[IllegalArgumentException](CozyArticleMediaRegistry.upsert(root, "publication", _strict(), Vector.empty))

          Then("the canonical identity tuple is required before target mutation")
          error.getMessage should include("not canonical")
          _bundle_bytes(root.resolve("publication.json")) shouldBe before
        }
      }

      "reject an integrity locale path that differs from its canonical metadata locale" in {
        Given("an integrity record stored under a different canonical locale")
        _with_root { root =>
          val integrity = _video_integrity()
          _write_bundle(root, "publication", Vector(_entry("metadata/article-media-integrity/development-process/example/en/video.json", integrity.metadata)))
          val before = _bundle_bytes(root.resolve("publication.json"))

          When("a canonical strict upsert loads the recognized integrity metadata")
          val error = intercept[IllegalArgumentException](CozyArticleMediaRegistry.upsert(root, "publication", _strict(), Vector.empty))

          Then("the canonical locale tuple is required before target mutation")
          error.getMessage should include("not canonical")
          _bundle_bytes(root.resolve("publication.json")) shouldBe before
        }
      }

      "reject an integrity role path that differs from its canonical metadata role" in {
        Given("a video integrity record stored under the infographic role path")
        _with_root { root =>
          val integrity = _video_integrity()
          _write_bundle(root, "publication", Vector(_entry("metadata/article-media-integrity/development-process/example/ja/infographic.json", integrity.metadata)))
          val before = _bundle_bytes(root.resolve("publication.json"))

          When("a canonical strict upsert loads the recognized integrity metadata")
          val error = intercept[IllegalArgumentException](CozyArticleMediaRegistry.upsert(root, "publication", _strict(), Vector.empty))

          Then("the canonical role tuple is required before target mutation")
          error.getMessage should include("not canonical")
          _bundle_bytes(root.resolve("publication.json")) shouldBe before
        }
      }

      "reject malformed and noncanonical recognized integrity metadata during load" in {
        Given("recognized integrity records with schema, role, locale, or canonical JSON disagreements")
        val integrity = _video_integrity()
        val malformed = Vector(
          integrity.metadata + ("schema" -> JsString("cozy.article-media-integrity.v2")),
          integrity.metadata + ("role" -> JsString("invalid-role")),
          integrity.metadata + ("locale" -> JsString("JA")),
          integrity.metadata + ("unexpected" -> JsString("field"))
        )

        When("the registry loads each recognized integrity record")
        val errors = malformed.map { metadata =>
          _with_root { root =>
            _write_bundle(root, "publication", Vector(_entry(integrity.entryPath, metadata)))
            intercept[IllegalArgumentException](CozyArticleMediaRegistry.load(root))
          }
        }

        Then("each record is rejected before a snapshot can be constructed")
        errors.size shouldBe 4
        errors.foreach(_.getMessage should include("Article-media integrity metadata is invalid"))
      }
    }

    "upsert article-media metadata" which {
      "register strict and multiple integrity entries in an existing bundle while preserving unrelated entries" in {
        Given("an existing configured bundle, a strict publication, and two integrity results")
        _with_root { root =>
          _write_bundle(root, "publication", Vector(_entry("metadata/unrelated.json", Json.obj("kept" -> true))))
          val strict = _strict()
          val video = _video_integrity()
          val infographic = _infographic_integrity()

          When("the registry upserts the requested article entries")
          val result = CozyArticleMediaRegistry.upsert(root, "publication", strict, Vector(video, infographic))

          Then("the exact entry paths and canonical keys are deterministically retained")
          result.bundlePath shouldBe root.resolve("publication.json")
          result.entryPaths shouldBe Vector(
            "metadata/article-media-integrity/development-process/example/ja/infographic.json",
            "metadata/article-media-integrity/development-process/example/ja/video.json",
            "metadata/article-media/development-process/example.json"
          )
          result.snapshot.entries.map(_.path) shouldBe Vector(
            "metadata/article-media-integrity/development-process/example/ja/infographic.json",
            "metadata/article-media-integrity/development-process/example/ja/video.json",
            "metadata/article-media/development-process/example.json",
            "metadata/unrelated.json"
          )
          result.snapshot.entries.map(_.key) shouldBe Vector(
            "article-media-integrity/development-process/example/ja/infographic",
            "article-media-integrity/development-process/example/ja/video",
            "article-media/development-process/example",
            "unrelated"
          )
          result.snapshot.entries.find(_.path == "metadata/unrelated.json").map(_.metadata) shouldBe Some(Json.obj("kept" -> true))
        }
      }

      "replace matching same-bundle paths without adding duplicates" in {
        Given("one existing bundle and two strict results for the same identity")
        _with_root { root =>
          _write_bundle(root, "publication", Vector.empty)
          val first = _strict("development-process/example", "https://example.com/first")
          val replacement = _strict("development-process/example", "https://example.com/replacement")

          When("the same strict entry is upserted twice")
          CozyArticleMediaRegistry.upsert(root, "publication", first, Vector.empty)
          val result = CozyArticleMediaRegistry.upsert(root, "publication", replacement, Vector.empty)

          Then("only one matching entry remains with the replacement metadata")
          result.snapshot.entries.map(_.path) shouldBe Vector("metadata/article-media/development-process/example.json")
          ((result.snapshot.entries.head.metadata \ "variants").as[JsObject] \ "ja" \ "video" \ "watch_url").as[String] shouldBe "https://example.com/replacement"
        }
      }

      "load and upsert canonical strict and integrity records" in {
        Given("an existing bundle containing canonical strict and integrity metadata")
        _with_root { root =>
          val strict = _strict()
          val integrity = _video_integrity()
          _write_bundle(root, "publication", Vector(
            _entry(strict.entryPath, strict.metadata),
            _entry(integrity.entryPath, integrity.metadata)
          ))

          When("the registry loads and then upserts the same canonical records")
          val loaded = CozyArticleMediaRegistry.load(root)
          val result = CozyArticleMediaRegistry.upsert(root, "publication", strict, Vector(integrity))

          Then("both canonical records remain valid registry entries")
          loaded.entries.map(_.path) shouldBe Vector(integrity.entryPath, strict.entryPath)
          result.snapshot.entries.map(_.path) shouldBe Vector(integrity.entryPath, strict.entryPath)
        }
      }

      "replace the complete same-article integrity set without disturbing other article or bundle entries" in {
        Given("an initial article with infographic and video evidence plus unrelated same-bundle and other-article entries")
        _with_root { root =>
          val unrelated = _entry("metadata/unrelated.json", Json.obj("kept" -> true))
          val otherintegrity = _video_integrity(articleidentity = "development-process/other")
          val otherarticle = _entry(otherintegrity.entryPath, otherintegrity.metadata)
          _write_bundle(root, "publication", Vector(unrelated, otherarticle))
          val initial = _strict_with_media(includeinfographic = true)
          CozyArticleMediaRegistry.upsert(root, "publication", initial, Vector(_video_integrity(), _infographic_integrity()))

          When("a replacement registration omits the formerly declared infographic")
          val replacement = _strict_with_media(includeinfographic = false)
          val result = CozyArticleMediaRegistry.upsert(root, "publication", replacement, Vector(_video_integrity()))

          Then("only current article entries remain while unrelated and other-article bytes stay semantically intact")
          result.snapshot.entries.map(_.path) shouldBe Vector(
            "metadata/article-media-integrity/development-process/example/ja/video.json",
            "metadata/article-media-integrity/development-process/other/ja/video.json",
            "metadata/article-media/development-process/example.json",
            "metadata/unrelated.json"
          )
          result.snapshot.entries.find(_.path == "metadata/unrelated.json").map(_.metadata) shouldBe Some(Json.obj("kept" -> true))
          result.snapshot.entries.find(_.path == "metadata/article-media-integrity/development-process/other/ja/video.json").map(_.metadata) shouldBe Some(otherintegrity.metadata)
          result.snapshot.entries.exists(_.path.endsWith("/example/ja/infographic.json")) shouldBe false
        }
      }

      "replace legacy parent integrity records by decoded exact identity without removing child a/b evidence" in {
        Given("one bundle with canonical a and a/b strict and integrity metadata")
        _with_root { root =>
          val parentstrict = _strict("a")
          val childstrict = _strict("a/b")
          val parentintegrity = _video_integrity(articleidentity = "a")
          val childintegrity = _video_integrity(articleidentity = "a/b")
          _write_bundle(root, "publication", Vector(
            _entry(parentstrict.entryPath, parentstrict.metadata),
            _entry(parentintegrity.entryPath, parentintegrity.metadata),
            _entry(childstrict.entryPath, childstrict.metadata),
            _entry(childintegrity.entryPath, childintegrity.metadata)
          ))
          val childbefore = CozyArticleMediaRegistry.load(root).entries.filter(x => x.path == childstrict.entryPath || x.path == childintegrity.entryPath)

          When("legacy upsert replaces a while omitting its former integrity evidence")
          val result = CozyArticleMediaRegistry.upsert(root, "publication", parentstrict, Vector.empty)
          val childafter = result.snapshot.entries.filter(x => x.path == childstrict.entryPath || x.path == childintegrity.entryPath)

          Then("only a's decoded integrity path is removed and a/b remains byte-identical semantically")
          result.snapshot.entries.exists(_.path == parentintegrity.entryPath) shouldBe false
          childafter shouldBe childbefore
        }
      }

      "reject a stale expected configured snapshot without rewriting the bundle" in {
        Given("a configured target bundle whose bytes change after a digest snapshot")
        _with_root { root =>
          _write_bundle(root, "publication", Vector(_entry("metadata/unrelated.json", Json.obj("revision" -> 1))))
          val stale = CozyArticleMediaRegistry.load(root).bundleDigests("publication")
          _write_bundle(root, "publication", Vector(_entry("metadata/unrelated.json", Json.obj("revision" -> 2))))
          val before = _bundle_bytes(root.resolve("publication.json"))

          When("article media is upserted against the stale expected digest")
          val error = intercept[IllegalArgumentException](CozyArticleMediaRegistry.upsert(root, "publication", _strict(), Vector.empty, Map("publication" -> stale)))

          Then("the stale configured snapshot is rejected before mutation")
          error.getMessage should include("stale configured snapshot")
          _bundle_bytes(root.resolve("publication.json")) shouldBe before
        }
      }

      "reject missing or mismatched configured bundle names" in {
        Given("a configured bundle whose declaration differs from the requested name")
        _with_root { root =>
          _write_bundle(root, "stored", Vector.empty, declaredname = "declared")

          When("upsert requires the requested configured bundle")
          val missing = intercept[IllegalArgumentException](CozyArticleMediaRegistry.upsert(root, "publication", _strict(), Vector.empty))
          val mismatch = intercept[IllegalArgumentException](CozyArticleMediaRegistry.upsert(root, "stored", _strict(), Vector.empty))

          Then("both missing and declaration mismatch conditions fail")
          missing.getMessage should include("not found")
          mismatch.getMessage should include("match filename stem")
        }
      }

      "reject unsafe configured bundle names without changing the bundle" in {
        Given("a valid direct configured bundle and an unsafe requested name")
        _with_root { root =>
          _write_bundle(root, "publication", Vector.empty)
          val before = _bundle_bytes(root.resolve("publication.json"))

          When("upsert receives a traversal-like bundle name")
          val error = intercept[IllegalArgumentException](CozyArticleMediaRegistry.upsert(root, "../publication", _strict(), Vector.empty))

          Then("the unsafe name is rejected before mutation")
          error.getMessage should include("safe filename segment")
          _bundle_bytes(root.resolve("publication.json")) shouldBe before
        }
      }

      "reject an entry already owned by another configured bundle" in {
        Given("another direct bundle owning the requested strict entry path")
        _with_root { root =>
          _write_bundle(root, "publication", Vector.empty)
          val strict = _strict()
          _write_bundle(root, "other", Vector(_entry(strict.entryPath, strict.metadata)))
          val before = _bundle_bytes(root.resolve("publication.json"))

          When("the target bundle attempts the upsert")
          val error = intercept[IllegalArgumentException](CozyArticleMediaRegistry.upsert(root, "publication", strict, Vector.empty))

          Then("the foreign canonical owner is rejected before target mutation or alternate entry creation")
          error.getMessage should include("owned by another bundle")
          error.getMessage should include("other")
          _bundle_bytes(root.resolve("publication.json")) shouldBe before
          CozyArticleMediaRegistry.load(root).entries.count(_.path == strict.entryPath) shouldBe 1
        }
      }

      "reject a cross-bundle strict alias before canonical upsert without mutating either bundle" in {
        Given("an empty target bundle and another bundle containing strict metadata at an alias path")
        _with_root { root =>
          val strict = _strict()
          _write_bundle(root, "publication", Vector.empty)
          _write_bundle(root, "other", Vector(_entry("metadata/article-media/development-process/example-alias.json", strict.metadata)))
          val targetbefore = _bundle_bytes(root.resolve("publication.json"))
          val foreignbefore = _bundle_bytes(root.resolve("other.json"))

          When("the target attempts a canonical upsert for the aliased article identity")
          val error = intercept[IllegalArgumentException](CozyArticleMediaRegistry.upsert(root, "publication", strict, Vector.empty))

          Then("recognized metadata binding rejects the foreign alias before either bundle changes or a canonical target entry exists")
          error.getMessage should include("not canonical")
          _bundle_bytes(root.resolve("publication.json")) shouldBe targetbefore
          _bundle_bytes(root.resolve("other.json")) shouldBe foreignbefore
          (Json.parse(Files.readString(root.resolve("publication.json"))) \ "entries").as[JsArray].value.map(x => (x \ "path").as[String]) should not contain strict.entryPath
        }
      }

      "reject an alias strict record before canonical upsert without mutating the target bundle" in {
        Given("a target bundle containing strict metadata at a noncanonical alias path")
        _with_root { root =>
          val strict = _strict()
          _write_bundle(root, "publication", Vector(_entry("metadata/article-media/development-process/example-alias.json", strict.metadata)))
          val before = _bundle_bytes(root.resolve("publication.json"))

          When("a canonical strict upsert loads the target bundle")
          val error = intercept[IllegalArgumentException](CozyArticleMediaRegistry.upsert(root, "publication", strict, Vector.empty))

          Then("the alias is rejected before any rewrite or alternate canonical entry")
          error.getMessage should include("not canonical")
          _bundle_bytes(root.resolve("publication.json")) shouldBe before
          (Json.parse(Files.readString(root.resolve("publication.json"))) \ "entries").as[JsArray].value.map(x => (x \ "path").as[String]) should not contain strict.entryPath
        }
      }

      "reject a direct filename and declaration mismatch before foreign ownership checks or mutation" in {
        Given("the requested target and a second direct file whose declaration differs from its filename")
        _with_root { root =>
          _write_bundle(root, "publication", Vector.empty)
          _write_bundle(root, "foreign", Vector(_entry("metadata/article-media/development-process/example.json", Json.obj("owner" -> "foreign"))), declaredname = "publication")
          val targetbefore = _bundle_bytes(root.resolve("publication.json"))
          val foreignbefore = _bundle_bytes(root.resolve("foreign.json"))

          When("the target bundle attempts an upsert")
          val error = intercept[IllegalArgumentException](CozyArticleMediaRegistry.upsert(root, "publication", _strict(), Vector.empty))

          Then("the filename and declaration identity mismatch wins deterministically before any mutation")
          error.getMessage should include("match filename stem")
          _bundle_bytes(root.resolve("publication.json")) shouldBe targetbefore
          _bundle_bytes(root.resolve("foreign.json")) shouldBe foreignbefore
        }
      }

      "reject duplicate canonical tuples within one requested bundle before mutation" in {
        Given("one direct bundle and duplicate integrity records for a single normalized tuple")
        _with_root { root =>
          _write_bundle(root, "publication", Vector.empty)
          val before = _bundle_bytes(root.resolve("publication.json"))
          val integrity = _video_integrity()

          When("upsert receives the same canonical integrity path twice")
          val error = intercept[IllegalArgumentException](CozyArticleMediaRegistry.upsert(root, "publication", _strict(), Vector(integrity, integrity)))

          Then("the duplicate canonical tuple/path is rejected before mutation")
          error.getMessage should include("Duplicate publication bundle requested path")
          _bundle_bytes(root.resolve("publication.json")) shouldBe before
        }
      }

      "reject null, copied, tampered, and malformed typed results before mutation" in {
        Given("a direct bundle and strict or integrity results that no longer equal their canonical production")
        _with_root { root =>
          _write_bundle(root, "publication", Vector.empty)
          val before = _bundle_bytes(root.resolve("publication.json"))
          val strict = _strict()
          val integrity = _video_integrity()
          val copiedstrict = _strict().copy(entryPath = "metadata/article-media/copied.json")
          val copiedintegrity = _video_integrity().copy(entryPath = "metadata/article-media-integrity/copied.json")
          val tamperedstrict = strict.copy(metadata = Json.obj("tampered" -> true))
          val tamperedintegrity = integrity.copy(metadata = Json.obj("tampered" -> true))
          val malformedstrict = strict.copy(publication = strict.publication.copy(articleIdentity = "development-process/../invalid"))
          val malformedintegrity = integrity.copy(record = integrity.record.copy(repositoryPath = "video/../invalid.mp4"))

          When("the registry reconstructs canonical typed results before rewriting")
          val errors = Vector(
            intercept[IllegalArgumentException](CozyArticleMediaRegistry.upsert(root, "publication", null, Vector.empty)),
            intercept[IllegalArgumentException](CozyArticleMediaRegistry.upsert(root, "publication", strict, Vector(null))),
            intercept[IllegalArgumentException](CozyArticleMediaRegistry.upsert(root, "publication", copiedstrict, Vector.empty)),
            intercept[IllegalArgumentException](CozyArticleMediaRegistry.upsert(root, "publication", _strict(), Vector(copiedintegrity))),
            intercept[IllegalArgumentException](CozyArticleMediaRegistry.upsert(root, "publication", tamperedstrict, Vector.empty)),
            intercept[IllegalArgumentException](CozyArticleMediaRegistry.upsert(root, "publication", _strict(), Vector(tamperedintegrity))),
            intercept[IllegalArgumentException](CozyArticleMediaRegistry.upsert(root, "publication", malformedstrict, Vector.empty)),
            intercept[IllegalArgumentException](CozyArticleMediaRegistry.upsert(root, "publication", _strict(), Vector(malformedintegrity)))
          )

          Then("every non-canonical typed result is rejected without changing bundle bytes")
          errors.size shouldBe 8
          errors.foreach(_.getMessage should not be empty)
          _bundle_bytes(root.resolve("publication.json")) shouldBe before
        }
      }

      "reject direct JSON symbolic links without reading or mutating their external target" in {
        Given("a direct symbolic link to an external valid publication bundle")
        _with_root { externalroot =>
          _write_bundle(externalroot, "external", Vector.empty, declaredname = "publication")
          val external = externalroot.resolve("external.json")
          val before = _bundle_bytes(external)
          _with_root { root =>
            try Files.createSymbolicLink(root.resolve("publication.json"), external)
            catch {
              case _: UnsupportedOperationException | _: SecurityException | _: IOException => cancel("The platform cannot create symbolic links for this specification")
            }

            When("load and upsert encounter the direct symbolic link")
            val loaderror = intercept[IllegalArgumentException](CozyArticleMediaRegistry.load(root))
            val upserterror = intercept[IllegalArgumentException](CozyArticleMediaRegistry.upsert(root, "publication", _strict(), Vector.empty))

            Then("both operations reject the link and preserve external bundle bytes")
            loaderror.getMessage should include("symbolic link")
            upserterror.getMessage should include("symbolic link")
            _bundle_bytes(external) shouldBe before
          }
        }
      }

      "reject an integrity identity differing from the strict publication identity" in {
        Given("a strict publication and integrity result for different normalized articles")
        _with_root { root =>
          _write_bundle(root, "publication", Vector.empty)
          val integrity = _video_integrity(articleidentity = "development-process/other")

          When("the registry validates article identity agreement")
          val error = intercept[IllegalArgumentException](CozyArticleMediaRegistry.upsert(root, "publication", _strict(), Vector(integrity)))

          Then("the conflicting identity is rejected")
          error.getMessage should include("must match strict publication identity")
        }
      }

      "ignore a nested target decoy while upserting an existing direct bundle" in {
        Given("a valid direct bundle and malformed JSON only below target")
        _with_root { root =>
          _write_bundle(root, "publication", Vector.empty)
          val target = Files.createDirectories(root.resolve("target/decoy"))
          Files.write(target.resolve("ignored.json"), "not json".getBytes(StandardCharsets.UTF_8))

          When("the registry upserts article media")
          val result = CozyArticleMediaRegistry.upsert(root, "publication", _strict(), Vector.empty)

          Then("the direct bundle is updated without inspecting the decoy")
          result.entryPaths shouldBe Vector("metadata/article-media/development-process/example.json")
        }
      }

      "produce byte-identical bundles for shuffled integrity input" in {
        Given("generated orderings of two independent integrity records and reset fixtures")
        val video = _video_integrity()
        val infographic = _infographic_integrity()
        val orders = Gen.oneOf(Vector(Vector(video, infographic), Vector(infographic, video)))
        val property = Prop.forAll(orders) { order =>
          _with_root { root =>
            _write_bundle(root, "publication", Vector.empty)
            val result = CozyArticleMediaRegistry.upsert(root, "publication", _strict(), order)
            val bytes = Files.readAllBytes(root.resolve("publication.json")).toVector
            _with_root { baseline =>
              _write_bundle(baseline, "publication", Vector.empty)
              val expected = CozyArticleMediaRegistry.upsert(baseline, "publication", _strict(), Vector(video, infographic))
              val expectedbytes = Files.readAllBytes(baseline.resolve("publication.json")).toVector
              bytes == expectedbytes && result.entryPaths == expected.entryPaths && result.snapshot == expected.snapshot
            }
          }
        }

        When("ScalaCheck evaluates independently reset generated orderings")
        val propertyresult = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

        Then("all orderings produce identical bundles and result ordering")
        propertyresult.passed shouldBe true
      }

      "reject a changed non-target bundle from the complete expected snapshot without changing target bytes" in {
        Given("target A, video provenance bundle B, and an expected complete configured snapshot")
        _with_root { root =>
          _write_bundle(root, "alpha", Vector.empty)
          _write_bundle(root, "beta", Vector(_entry("metadata/video/evidence.json", Json.obj("revision" -> 1))))
          val expected = CozyArticleMediaRegistry.load(root).bundleDigests
          _write_bundle(root, "beta", Vector(_entry("metadata/video/evidence.json", Json.obj("revision" -> 2))))
          val before = _bundle_bytes(root.resolve("alpha.json"))

          When("the target registration retains the stale two-bundle provenance snapshot")
          val error = intercept[IllegalArgumentException](CozyArticleMediaRegistry.upsert(root, "alpha", _strict(), Vector.empty, expected))

          Then("the changed evidence bundle aborts the target mutation byte-identically")
          error.getMessage should include("stale configured snapshot")
          _bundle_bytes(root.resolve("alpha.json")) shouldBe before
        }
      }

      "serialize generic metadata registrations across real-root aliases and preserve both updates" in {
        Given("a direct bundle, a real root, and a safe symbolic-link alias to that root")
        _with_root { root =>
          _write_bundle(root, "publication", Vector.empty)
          val alias = root.resolveSibling(root.getFileName.toString + "-alias")
          try Files.createSymbolicLink(alias, root)
          catch {
            case _: UnsupportedOperationException | _: SecurityException | _: IOException => cancel("The platform cannot create symbolic links for this specification")
          }
          try {
            val ready = new CountDownLatch(2)
            val start = new CountDownLatch(1)
            val failures = new ConcurrentLinkedQueue[Throwable]()
            val first = new Thread(new Runnable {
              override def run(): Unit = _concurrent_register(root, "metadata/concurrent/real.json", ready, start, failures)
            })
            val second = new Thread(new Runnable {
              override def run(): Unit = _concurrent_register(alias, "metadata/concurrent/alias.json", ready, start, failures)
            })

            When("both generic registrations begin from distinct lexical roots at one barrier")
            first.start()
            second.start()
            ready.await()
            start.countDown()
            first.join()
            second.join()
            val snapshot = CozyArticleMediaRegistry.load(root)

            Then("the real-root registry lock merges both updates without failure")
            failures.isEmpty shouldBe true
            snapshot.entries.map(_.path) shouldBe Vector("metadata/concurrent/alias.json", "metadata/concurrent/real.json")
          } finally {
            Files.deleteIfExists(alias)
          }
        }
      }

      "reject generic registration when entries are missing without mutation" in {
        Given("an existing direct bundle whose entries field is missing")
        _with_root { root =>
          _write_bundle_json(root, "publication", Json.obj(
            "schema" -> "cozy.publish-project.v1",
            "type" -> "publication-bundle",
            "publication" -> Json.obj("name" -> "publication"),
            "sourceRepository" -> "cozy",
            "sourcePath" -> ".",
            "sourceCommit" -> "0123456789abcdef"
          ))
          val before = _bundle_bytes(root.resolve("publication.json"))

          When("generic registration loads the malformed existing bundle")
          val error = intercept[IllegalArgumentException](CozyPublicationCompiler.registerMetadata(root, "publication", Vector("metadata/registered.json" -> Json.obj("registered" -> true))))
          val temporaries = _direct_names(root).filter(name => name.startsWith("publication.json.") && name.endsWith(".tmp"))
          val siblingnames = _direct_names(root).filterNot(name => name == "publication.json" || name == ".cozy-publication-registry.lock")

          Then("missing entries are rejected before any target or temporary sibling is written")
          error.getMessage should include("Invalid publication bundle entries")
          _bundle_bytes(root.resolve("publication.json")) shouldBe before
          Files.exists(root.resolve("publication.json.json")) shouldBe false
          temporaries shouldBe empty
          siblingnames shouldBe empty
        }
      }

      "reject generic registration when entries are not an array without mutation" in {
        Given("an existing direct bundle whose entries field is an object")
        _with_root { root =>
          _write_bundle_json(root, "publication", Json.obj(
            "schema" -> "cozy.publish-project.v1",
            "type" -> "publication-bundle",
            "publication" -> Json.obj("name" -> "publication"),
            "sourceRepository" -> "cozy",
            "sourcePath" -> ".",
            "sourceCommit" -> "0123456789abcdef",
            "entries" -> Json.obj("unexpected" -> true)
          ))
          val before = _bundle_bytes(root.resolve("publication.json"))

          When("generic registration loads the malformed existing bundle")
          val error = intercept[IllegalArgumentException](CozyPublicationCompiler.registerMetadata(root, "publication", Vector("metadata/registered.json" -> Json.obj("registered" -> true))))
          val temporaries = _direct_names(root).filter(name => name.startsWith("publication.json.") && name.endsWith(".tmp"))
          val siblingnames = _direct_names(root).filterNot(name => name == "publication.json" || name == ".cozy-publication-registry.lock")

          Then("non-array entries are rejected before any target or temporary sibling is written")
          error.getMessage should include("Invalid publication bundle entries")
          _bundle_bytes(root.resolve("publication.json")) shouldBe before
          Files.exists(root.resolve("publication.json.json")) shouldBe false
          temporaries shouldBe empty
          siblingnames shouldBe empty
        }
      }

      "reject generic traversal, declaration mismatch, and a lock symlink without mutation or temporary siblings" in {
        Given("a direct configured bundle, malicious generic paths, and a byte snapshot")
        _with_root { root =>
          _write_bundle(root, "publication", Vector.empty)
          _write_bundle(root, "stored", Vector.empty, declaredname = "declared")
          val before = _bundle_bytes(root.resolve("publication.json"))
          val invalidpaths = Vector("metadata/a\\..\\b.json", "metadata/a/../b.json", "metadata/a//b.json")

          When("generic registration receives traversal and a mismatched declaration")
          val patherrors = invalidpaths.map { path =>
            intercept[IllegalArgumentException](CozyPublicationCompiler.registerMetadata(root, "publication", Vector(path -> Json.obj())))
          }
          val declarationerror = intercept[IllegalArgumentException](CozyPublicationCompiler.registerMetadata(root, "stored", Vector("metadata/valid.json" -> Json.obj())))
          val temporaries = _direct_names(root).filter(_.endsWith(".tmp"))

          Then("all failures retain the locked target and create neither alternate targets nor temporary siblings")
          patherrors.map(_.getMessage).mkString(" ") should include("Invalid publication bundle path")
          declarationerror.getMessage should include("does not match")
          _bundle_bytes(root.resolve("publication.json")) shouldBe before
          Files.exists(root.resolve("b.json")) shouldBe false
          temporaries shouldBe empty
        }
      }

      "reject a symbolic-link registry lock before generic metadata mutation" in {
        Given("a direct configured bundle and a registry lock path substituted by a symbolic link")
        _with_root { root =>
          _write_bundle(root, "publication", Vector.empty)
          val before = _bundle_bytes(root.resolve("publication.json"))
          val external = Files.createTempFile(root.getParent, "registry-lock-", ".lock")
          try Files.createSymbolicLink(root.resolve(".cozy-publication-registry.lock"), external)
          catch {
            case _: UnsupportedOperationException | _: SecurityException | _: IOException => cancel("The platform cannot create symbolic links for this specification")
          }
          try {
            When("generic registration acquires the root-wide persistent lock")
            val error = intercept[IllegalArgumentException](CozyPublicationCompiler.registerMetadata(root, "publication", Vector("metadata/valid.json" -> Json.obj())))

            Then("the lock link is rejected and target bytes remain unchanged")
            error.getMessage should include("registry lock")
            _bundle_bytes(root.resolve("publication.json")) shouldBe before
          } finally {
            Files.deleteIfExists(root.resolve(".cozy-publication-registry.lock"))
            Files.deleteIfExists(external)
          }
        }
      }
    }

    "validate render-independent role intent" which {
      "select the existing deterministic owner without creating a bundle or fabricating integrity evidence" in {
        Given("one configured bundle owning canonical strict and video integrity records")
        _with_root { root =>
          val strict = _site_strict("development-process/example", "/repository/video/example.mp4")
          val integrity = _video_integrity()
          _write_bundle(root, "owner", Vector(_entry(strict.entryPath, strict.metadata), _entry(integrity.entryPath, integrity.metadata)))
          val before = _bundle_bytes(root.resolve("owner.json"))

          When("a video producer validates only its normalized role intent")
          val result = CozyArticleMediaRegistry.validateReadOnlyRoleIntents(root, Vector(
            CozyArticleMediaRegistry.RoleIntent("development-process/example", "ja", CozyArticleMediaIntegrity.Role.Video)
          ))

          Then("the same canonical owner is reported from a read-only snapshot")
          result.owners.map(x => (x.intent.articleIdentity, x.intent.locale, x.intent.role.name, x.owner)) shouldBe Vector(
            ("development-process/example", "ja", "video", "owner")
          )
          result.snapshot.entries.map(_.path) should contain(integrity.entryPath)
          _bundle_bytes(root.resolve("owner.json")) shouldBe before
          Files.exists(root.resolve("article-media.json")) shouldBe false
        }
      }

      "reject ownership ambiguity before a producer renders an artifact" in {
        Given("strict and integrity records for one article split across configured bundles")
        _with_root { root =>
          val strict = _site_strict("development-process/example", "/repository/video/example.mp4")
          val integrity = _video_integrity()
          _write_bundle(root, "strict-owner", Vector(_entry(strict.entryPath, strict.metadata)))
          _write_bundle(root, "integrity-owner", Vector(_entry(integrity.entryPath, integrity.metadata)))
          val strictbefore = _bundle_bytes(root.resolve("strict-owner.json"))
          val integritybefore = _bundle_bytes(root.resolve("integrity-owner.json"))

          When("a video producer validates its current role ownership")
          val error = intercept[IllegalArgumentException](CozyArticleMediaRegistry.validateReadOnlyRoleIntents(root, Vector(
            CozyArticleMediaRegistry.RoleIntent("development-process/example", "ja", CozyArticleMediaIntegrity.Role.Video)
          )))

          Then("the conflicting current owner is rejected with no registry mutation")
          error.getMessage should include("multiple bundle owners")
          _bundle_bytes(root.resolve("strict-owner.json")) shouldBe strictbefore
          _bundle_bytes(root.resolve("integrity-owner.json")) shouldBe integritybefore
        }
      }

      "reject a changed complete snapshot through the bounded read-only callback" in {
        Given("a configured bundle and a role intent whose owner is otherwise valid")
        _with_root { root =>
          val strict = _site_strict("development-process/example", "/repository/video/example.mp4")
          val integrity = _video_integrity()
          _write_bundle(root, "owner", Vector(_entry(strict.entryPath, strict.metadata), _entry(integrity.entryPath, integrity.metadata)))
          val changed = Json.prettyPrint(Json.obj(
            "schema" -> "cozy.publish-project.v1",
            "type" -> "publication-bundle",
            "publication" -> Json.obj("name" -> "owner"),
            "sourceRepository" -> "cozy",
            "sourcePath" -> ".",
            "sourceCommit" -> JsString("0123456789abcdef"),
            "entries" -> Json.arr(_entry(strict.entryPath, strict.metadata), _entry(integrity.entryPath, integrity.metadata))
          )).replace("\n", "\n\n").getBytes(StandardCharsets.UTF_8)

          When("the configured bundle changes between snapshot reads")
          val error = intercept[IllegalArgumentException](CozyArticleMediaRegistry.validateReadOnlyRoleIntents(
            root,
            Vector(CozyArticleMediaRegistry.RoleIntent("development-process/example", "ja", CozyArticleMediaIntegrity.Role.Video)),
            () => Files.write(root.resolve("owner.json"), changed)
          ))

          Then("the stale snapshot is rejected and the externally changed bytes remain untouched")
          error.getMessage should include("stale read-only snapshot")
          _bundle_bytes(root.resolve("owner.json")) shouldBe changed.toVector
          Files.exists(root.resolve("article-media.json")) shouldBe false
        }
      }
    }

    "merge role-local article-media registration transactions" which {
      "validate an exact role plan without replacing its owner bundle" in {
        Given("an existing generic owner bundle and one valid infographic role update")
        _with_root { root =>
          _write_bundle(root, "owner", Vector(_entry("metadata/generic.json", Json.obj("kept" -> true))))
          val before = _bundle_bytes(root.resolve("owner.json"))

          When("the transaction validates the role plan before an external artifact commit")
          CozyArticleMediaRegistry.transaction(root)(_.validate(Vector(_infographic_role_update())))

          Then("the owner bundle remains byte-identical until merge is explicitly requested")
          _bundle_bytes(root.resolve("owner.json")) shouldBe before
          Files.exists(root.resolve("article-media.json")) shouldBe false
        }
      }

      "create the canonical article-media bundle when the article has no owner" in {
        Given("an empty configured publication root and one exact infographic role update")
        _with_root { root =>
          val update = _infographic_role_update()

          When("the transaction merges the first article role")
          val result = CozyArticleMediaRegistry.transaction(root)(_.merge(Vector(update)))
          val stored = Json.parse(Files.readString(root.resolve("article-media.json"))).as[JsObject]

          Then("the canonical compiler bundle owns the complete strict and integrity record without extension fields")
          result.bundlePaths shouldBe Vector(root.resolve("article-media.json"))
          result.entryPaths shouldBe Vector(
            "metadata/article-media-integrity/development-process/example/ja/infographic.json",
            "metadata/article-media/development-process/example.json"
          )
          result.snapshot.entries.map(_.bundleName).distinct shouldBe Vector("article-media")
          stored shouldBe Json.obj(
            "schema" -> "cozy.publish-project.v1",
            "type" -> "publication-bundle",
            "publication" -> Json.obj("name" -> "article-media"),
            "sourceRepository" -> "",
            "sourcePath" -> ".",
            "sourceCommit" -> JsNull,
            "entries" -> Json.arr(
              _entry("metadata/article-media-integrity/development-process/example/ja/infographic.json", update.integrity.metadata),
              _entry("metadata/article-media/development-process/example.json", CozyArticleMediaPublication.produce(update.articleIdentity, Vector(update.variant)).metadata)
            )
          )
        }
      }

      "reuse strict or integrity-only ownership without moving an article" in {
        Given("one strict-owned article and one integrity-only article in separate declared bundles")
        _with_root { root =>
          _write_bundle(root, "strict-owner", Vector(_entry(_strict().entryPath, _strict().metadata)))
          val integrity = _video_integrity(articleidentity = "development-process/integrity-only")
          _write_bundle(root, "integrity-owner", Vector(_entry(integrity.entryPath, integrity.metadata)))

          When("role-local infographic updates are committed for both identities")
          val strictresult = CozyArticleMediaRegistry.transaction(root)(_.merge(Vector(_infographic_role_update())))
          val integrityresult = CozyArticleMediaRegistry.transaction(root)(_.merge(Vector(_infographic_role_update("development-process/integrity-only"))))

          Then("each article remains in its existing ownership bundle")
          strictresult.bundlePaths shouldBe Vector(root.resolve("strict-owner.json"))
          integrityresult.bundlePaths shouldBe Vector(root.resolve("integrity-owner.json"))
          Files.exists(root.resolve("article-media.json")) shouldBe false
        }
      }

      "preserve unaffected roles, locales, generic entries, and bundle declaration fields" in {
        Given("a custom owner with Japanese video, English infographic, generic metadata, and extension fields")
        _with_root { root =>
          val strict = CozyArticleMediaPublication.produce("development-process/example", Vector(
            CozyArticleMediaPublication.Variant("ja", video = Some(_site_video())),
            CozyArticleMediaPublication.Variant("en", infographic = Some(_infographic_image("/en/development-process/images/example.png")))
          ))
          val javideo = _video_integrity()
          val eninfographic = _infographic_integrity(locale = "en", publicpath = "/en/development-process/images/example.png")
          val bundle = Json.obj(
            "schema" -> "cozy.publish-project.v1",
            "type" -> "publication-bundle",
            "publication" -> Json.obj("name" -> "custom", "title" -> "Kept declaration"),
            "sourceRepository" -> "cozy",
            "sourcePath" -> ".",
            "sourceCommit" -> JsString("0123456789abcdef"),
            "extension" -> Json.obj("kept" -> true),
            "entries" -> Json.arr(
              _entry(strict.entryPath, strict.metadata),
              _entry(javideo.entryPath, javideo.metadata),
              _entry(eninfographic.entryPath, eninfographic.metadata),
              _entry("metadata/generic.json", Json.obj("kept" -> true))
            )
          )
          _write_bundle_json(root, "custom", bundle)

          When("only the Japanese infographic role is replaced")
          val result = CozyArticleMediaRegistry.transaction(root)(_.merge(Vector(_infographic_role_update())))
          val stored = Json.parse(Files.readString(root.resolve("custom.json"))).as[JsObject]
          val merged = result.snapshot.entries.find(_.path == strict.entryPath).map(_.metadata).get

          Then("the exact role changes while all independent registry content remains")
          result.bundlePaths shouldBe Vector(root.resolve("custom.json"))
          (((merged \ "variants").as[JsObject] \ "ja" \ "video" \ "content_url").as[String]) shouldBe "/repository/video/example.mp4"
          (((merged \ "variants").as[JsObject] \ "en" \ "infographic" \ "public_path").as[String]) shouldBe "/en/development-process/images/example.png"
          result.snapshot.entries.find(_.path == "metadata/generic.json").map(_.metadata) shouldBe Some(Json.obj("kept" -> true))
          (stored \ "publication" \ "title").as[String] shouldBe "Kept declaration"
          (stored \ "extension" \ "kept").as[Boolean] shouldBe true
        }
      }

      "reject duplicate, mismatched, and multi-owner plans before mutation" in {
        Given("a role update, a mismatched integrity result, and an article split over two bundles")
        _with_root { root =>
          _write_bundle(root, "alpha", Vector(_entry(_strict().entryPath, _strict().metadata)))
          _write_bundle(root, "beta", Vector(_entry(_video_integrity().entryPath, _video_integrity().metadata)))
          val update = _infographic_role_update()
          val mismatch = _infographic_role_update().copy(integrity = _infographic_integrity(publicpath = "/ja/development-process/images/other.png"))
          val beforealpha = _bundle_bytes(root.resolve("alpha.json"))
          val beforebeta = _bundle_bytes(root.resolve("beta.json"))

          When("the transaction receives invalid complete plans")
          val duplicate = intercept[IllegalArgumentException](CozyArticleMediaRegistry.transaction(root)(_.merge(Vector(update, update))))
          val mismatchederror = intercept[IllegalArgumentException](CozyArticleMediaRegistry.transaction(root)(_.merge(Vector(mismatch))))
          val ownererror = intercept[IllegalArgumentException](CozyArticleMediaRegistry.transaction(root)(_.merge(Vector(update))))

          Then("every rejection happens before either current owner is rewritten")
          duplicate.getMessage should include("Duplicate article-media role update")
          mismatchederror.getMessage should include("same identity, locale, role, and public path")
          ownererror.getMessage should include("multiple bundle owners")
          _bundle_bytes(root.resolve("alpha.json")) shouldBe beforealpha
          _bundle_bytes(root.resolve("beta.json")) shouldBe beforebeta
        }
      }

      "preserve child identity bytes when a parent role is merged in the same owner bundle" in {
        Given("canonical parent a and child a/b strict and integrity records in one owner bundle")
        _with_root { root =>
          val parentstrict = _site_strict("a", "/repository/video/a.mp4")
          val childstrict = _site_strict("a/b", "/repository/video/a-b.mp4")
          val parentintegrity = _video_integrity(articleidentity = "a", publicpath = "/repository/video/a.mp4")
          val childintegrity = _video_integrity(articleidentity = "a/b", publicpath = "/repository/video/a-b.mp4")
          _write_bundle(root, "same", Vector(
            _entry(parentstrict.entryPath, parentstrict.metadata),
            _entry(parentintegrity.entryPath, parentintegrity.metadata),
            _entry(childstrict.entryPath, childstrict.metadata),
            _entry(childintegrity.entryPath, childintegrity.metadata)
          ))
          val childbefore = CozyArticleMediaRegistry.load(root).entries.filter(x => x.path == childstrict.entryPath || x.path == childintegrity.entryPath)
          val childmetadatabytes = childbefore.map(x => x.path -> Json.stringify(x.metadata))

          When("the parent role is merged in the same bundle")
          val sameresult = CozyArticleMediaRegistry.transaction(root)(_.merge(Vector(_infographic_role_update("a"))))
          val samechild = CozyArticleMediaRegistry.load(root).entries.filter(x => x.path == childstrict.entryPath || x.path == childintegrity.entryPath)

          Then("only a is selected and a/b retains exact owner bytes and metadata")
          sameresult.bundlePaths shouldBe Vector(root.resolve("same.json"))
          samechild shouldBe childbefore
          samechild.map(x => x.path -> Json.stringify(x.metadata)) shouldBe childmetadatabytes

        }
      }

      "preserve distinct parent and child owners when each exact identity is merged" in {
        Given("canonical parent a and child a/b strict and integrity records in distinct owner bundles")
        _with_root { root =>
          val parentstrict = _site_strict("a", "/repository/video/a.mp4")
          val childstrict = _site_strict("a/b", "/repository/video/a-b.mp4")
          val parentintegrity = _video_integrity(articleidentity = "a", publicpath = "/repository/video/a.mp4")
          val childintegrity = _video_integrity(articleidentity = "a/b", publicpath = "/repository/video/a-b.mp4")
          _write_bundle(root, "alpha", Vector(_entry(parentstrict.entryPath, parentstrict.metadata), _entry(parentintegrity.entryPath, parentintegrity.metadata)))
          _write_bundle(root, "beta", Vector(_entry(childstrict.entryPath, childstrict.metadata), _entry(childintegrity.entryPath, childintegrity.metadata)))
          val childbytes = _bundle_bytes(root.resolve("beta.json"))
          val parentbytes = _bundle_bytes(root.resolve("alpha.json"))

          When("the parent and then child roles are merged independently")
          val parentresult = CozyArticleMediaRegistry.transaction(root)(_.merge(Vector(_infographic_role_update("a"))))
          val childafterparent = _bundle_bytes(root.resolve("beta.json"))
          val parentafter = _bundle_bytes(root.resolve("alpha.json"))
          val childresult = CozyArticleMediaRegistry.transaction(root)(_.merge(Vector(_infographic_role_update("a/b"))))

          Then("each exact identity selects only its owner with no false multi-owner and preserves the other bytes")
          parentresult.bundlePaths shouldBe Vector(root.resolve("alpha.json"))
          childresult.bundlePaths shouldBe Vector(root.resolve("beta.json"))
          childafterparent shouldBe childbytes
          _bundle_bytes(root.resolve("alpha.json")) should not be parentbytes
          _bundle_bytes(root.resolve("alpha.json")) shouldBe parentafter
        }
      }

      "merge two independently owned articles in deterministic bundle order without creating a canonical owner" in {
        Given("two valid owner bundles with declaration and generic metadata that must survive")
        _with_root { root =>
          val parentstrict = _site_strict("a", "/repository/video/a.mp4")
          val childstrict = _site_strict("a/b", "/repository/video/a-b.mp4")
          val parentintegrity = _video_integrity(articleidentity = "a", publicpath = "/repository/video/a.mp4")
          val childintegrity = _video_integrity(articleidentity = "a/b", publicpath = "/repository/video/a-b.mp4")
          _write_bundle_json(root, "alpha", _bundle_with_declaration("alpha", "Alpha", Vector(
            _entry(parentstrict.entryPath, parentstrict.metadata),
            _entry(parentintegrity.entryPath, parentintegrity.metadata),
            _entry("metadata/generic-alpha.json", Json.obj("kept" -> "alpha"))
          )))
          _write_bundle_json(root, "beta", _bundle_with_declaration("beta", "Beta", Vector(
            _entry(childstrict.entryPath, childstrict.metadata),
            _entry(childintegrity.entryPath, childintegrity.metadata),
            _entry("metadata/generic-beta.json", Json.obj("kept" -> "beta"))
          )))

          When("one transaction merges one role for each distinct owner")
          val result = CozyArticleMediaRegistry.transaction(root)(_.merge(Vector(
            _infographic_role_update("a"),
            _infographic_role_update("a/b")
          )))
          val alpha = Json.parse(Files.readString(root.resolve("alpha.json"))).as[JsObject]
          val beta = Json.parse(Files.readString(root.resolve("beta.json"))).as[JsObject]

          Then("both deterministic bundles update while declarations, generic entries, and owner topology remain intact")
          result.bundlePaths shouldBe Vector(root.resolve("alpha.json"), root.resolve("beta.json"))
          result.entryPaths shouldBe Vector(
            "metadata/article-media-integrity/a/b/ja/infographic.json",
            "metadata/article-media-integrity/a/b/ja/video.json",
            "metadata/article-media-integrity/a/ja/infographic.json",
            "metadata/article-media-integrity/a/ja/video.json",
            "metadata/article-media/a.json",
            "metadata/article-media/a/b.json"
          )
          result.snapshot.entries.find(_.path == "metadata/generic-alpha.json").map(_.metadata) shouldBe Some(Json.obj("kept" -> "alpha"))
          result.snapshot.entries.find(_.path == "metadata/generic-beta.json").map(_.metadata) shouldBe Some(Json.obj("kept" -> "beta"))
          (((alpha \ "entries").as[JsArray].value.find(x => (x \ "path").as[String] == parentstrict.entryPath).get \ "metadata" \ "variants" \ "ja" \ "infographic").as[JsObject].fields) should not be empty
          (((beta \ "entries").as[JsArray].value.find(x => (x \ "path").as[String] == childstrict.entryPath).get \ "metadata" \ "variants" \ "ja" \ "infographic").as[JsObject].fields) should not be empty
          (alpha \ "publication" \ "title").as[String] shouldBe "Alpha"
          (beta \ "publication" \ "title").as[String] shouldBe "Beta"
          Files.exists(root.resolve("article-media.json")) shouldBe false
        }
      }

      "enforce callback lifetime, owner thread confinement, and opaque transaction construction" in {
        Given("a captured callback transaction, one valid role update, and a worker attempting retained access")
        _with_root { root =>
          val update = _infographic_role_update()
          var captured: CozyArticleMediaRegistry.Transaction = null
          val activeerrors = new ConcurrentLinkedQueue[Throwable]()
          val releasederrors = new ConcurrentLinkedQueue[Throwable]()
          val second = CozyArticleMediaRegistry.transaction(root) { transaction =>
            captured = transaction
            val worker = new Thread(new Runnable {
              override def run(): Unit = {
                try transaction.snapshot catch { case e: Throwable => activeerrors.add(e) }
                try transaction.merge(Vector(update)) catch { case e: Throwable => activeerrors.add(e) }
              }
            })
            worker.start()
            worker.join()
            transaction.merge(Vector(update))
            intercept[IllegalArgumentException](transaction.merge(Vector(update)))
          }

          When("the callback has returned and another thread retains the transaction")
          val lifetime = intercept[IllegalArgumentException](captured.snapshot)
          val released = new Thread(new Runnable {
            override def run(): Unit = {
              try captured.snapshot catch { case e: Throwable => releasederrors.add(e) }
              try captured.merge(Vector(update)) catch { case e: Throwable => releasederrors.add(e) }
            }
          })
          released.start()
          released.join()

          Then("only the callback owner may use the private-constructed transaction and worker attempts cause no mutation")
          classOf[CozyArticleMediaRegistry.Transaction].getConstructors.toVector shouldBe empty
          second.getMessage should include("one merge commit")
          lifetime.getMessage should include("no longer active")
          activeerrors.asScala.toVector.map(_.getMessage) shouldBe Vector.fill(2)("Article-media registry transaction must be used by its callback owner thread")
          releasederrors.asScala.toVector.map(_.getMessage) shouldBe Vector.fill(2)("Article-media registry transaction must be used by its callback owner thread")
          CozyArticleMediaRegistry.load(root).entries.map(_.path) shouldBe Vector(
            "metadata/article-media-integrity/development-process/example/ja/infographic.json",
            "metadata/article-media/development-process/example.json"
          )
        }
      }

      "reject a full digest changed during an injected callback preflight before mutation" in {
        Given("a captured complete snapshot and an independently changed configured bundle")
        _with_root { root =>
          _write_bundle(root, "evidence", Vector(_entry("metadata/evidence.json", Json.obj("revision" -> 1))))
          val before = _bundle_bytes(root.resolve("evidence.json"))

          When("a callback changes the configured evidence bytes before its first merge")
          val error = intercept[IllegalArgumentException](CozyArticleMediaRegistry.transaction(root) { transaction =>
            transaction.snapshot.bundleDigests should contain key "evidence"
            _write_bundle(root, "evidence", Vector(_entry("metadata/evidence.json", Json.obj("revision" -> 2))))
            transaction.merge(Vector(_infographic_role_update()))
          })

          Then("the stale complete snapshot fails before article-media bundle creation")
          error.getMessage should include("stale configured snapshot")
          _bundle_bytes(root.resolve("evidence.json")) should not be before
          Files.exists(root.resolve("article-media.json")) shouldBe false
        }
      }

      "serialize concurrent same-article role updates without losing either role" in {
        Given("two distinct role updates started together for one previously unowned article")
        _with_root { root =>
          val ready = new CountDownLatch(2)
          val start = new CountDownLatch(1)
          val failures = new ConcurrentLinkedQueue[Throwable]()
          val first = new Thread(new Runnable {
            override def run(): Unit = _concurrent_role_merge(root, _infographic_role_update(), ready, start, failures)
          })
          val second = new Thread(new Runnable {
            override def run(): Unit = _concurrent_role_merge(root, _video_role_update(locale = "en"), ready, start, failures)
          })

          When("both callbacks request the common real-root transaction lock")
          first.start()
          second.start()
          ready.await()
          start.countDown()
          first.join()
          second.join()
          val strict = CozyArticleMediaRegistry.load(root).entries.find(_.path == "metadata/article-media/development-process/example.json").map(_.metadata).get

          Then("both exact locale-role variants are retained with no transaction failure")
          failures.asScala.toVector shouldBe empty
          (((strict \ "variants").as[JsObject] \ "ja" \ "infographic").as[JsObject].fields) should not be empty
          (((strict \ "variants").as[JsObject] \ "en" \ "video").as[JsObject].fields) should not be empty
        }
      }

      "produce deterministic results for shuffled distinct role updates" in {
        Given("generated orderings of independent Japanese infographic and English video updates")
        val infographic = _infographic_role_update()
        val video = _video_role_update(locale = "en")
        val orders = Gen.oneOf(Vector(Vector(infographic, video), Vector(video, infographic)))
        val property = Prop.forAll(orders) { order =>
          _with_root { root =>
            val result = CozyArticleMediaRegistry.transaction(root)(_.merge(order))
            val bytes = _bundle_bytes(root.resolve("article-media.json"))
            _with_root { baseline =>
              val expected = CozyArticleMediaRegistry.transaction(baseline)(_.merge(Vector(infographic, video)))
              bytes == _bundle_bytes(baseline.resolve("article-media.json")) && result.entryPaths == expected.entryPaths && result.snapshot == expected.snapshot
            }
          }
        }

        When("ScalaCheck evaluates independently reset orderings")
        val propertyresult = Test.check(Test.Parameters.default.withMinSuccessfulTests(30), property)

        Then("the persisted canonical bundle and complete snapshot are order-independent")
        propertyresult.passed shouldBe true
      }
    }

    "plan WIP role updates read-only" which {
      "plan mixed strict-only infographic and correlated provider-neutral video without writing" in {
        Given("fresh infographic and WIP video updates against an empty registry")
        _with_root { root =>
          val infographic = CozyArticleMediaRegistry.WipRoleUpdate(
            "development-process/example",
            CozyArticleMediaPublication.Variant("ja", infographic = Some(_infographic_image("/ja/development-process/images/example.png"))),
            None
          )
          val video = _wip_video_update()
          val before = _direct_names(root)

          When("the registry builds its read-only WIP plan")
          val plan = CozyArticleMediaRegistry.validateWipReadOnly(root, Vector(video, infographic))

          Then("video has its one integrity while infographic creates none and no file changes")
          plan.articles.map(_.strict.publication.articleIdentity) shouldBe Vector("development-process/example")
          plan.articles.head.strict.publication.variants.map(_.locale) shouldBe Vector("ja")
          plan.articles.head.integrities.map(_.record.role) shouldBe Vector(CozyArticleMediaIntegrity.Role.Video)
          plan.videoStates((video.articleIdentity, "ja", "video")) shouldBe CozyArticleMediaRegistry.WipVideoState.Fresh
          _direct_names(root) shouldBe before
        }
      }

      "admit only an exact current WIP repeat and preserve unrelated ownership" in {
        Given("a bundle with an exact WIP video pair and unrelated metadata")
        _with_root { root =>
          val update = _wip_video_update()
          val strict = CozyArticleMediaPublication.produce(update.articleIdentity, Vector(update.variant))
          val integrity = update.integrity.get
          _write_bundle(root, "publication", Vector(
            _entry(strict.entryPath, strict.metadata),
            _entry(integrity.entryPath, integrity.metadata),
            _entry("metadata/unrelated.json", Json.obj("owner" -> "preserved"))
          ))

          When("the exact WIP pair is planned again")
          val plan = CozyArticleMediaRegistry.validateWipReadOnly(root, Vector(update))

          Then("the repeat state is explicit and the original snapshot/owner are retained")
          plan.videoStates((update.articleIdentity, "ja", "video")) shouldBe CozyArticleMediaRegistry.WipVideoState.ExactRepeat
          plan.articles.head.owner shouldBe "publication"
          plan.snapshot.entries.map(_.path) should contain("metadata/unrelated.json")
        }
      }

      "reject duplicate, partial, external, and production-owned video tuples" in {
        Given("duplicate WIP updates and a pre-existing non-WIP strict video")
        _with_root { root =>
          val update = _wip_video_update()
          _write_bundle(root, "publication", Vector(_entry(_strict().entryPath, _strict().metadata)))

          When("the planner isolates the exact video tuple")
          val duplicate = intercept[IllegalArgumentException](CozyArticleMediaRegistry.validateWipReadOnly(root, Vector(update, update)))
          val external = intercept[IllegalArgumentException](CozyArticleMediaRegistry.validateWipReadOnly(root, Vector(update)))

          Then("all non-exact existing video state fails before mutation")
          duplicate.getMessage should include("Duplicate")
          external.getMessage should include("existing video tuple")
        }
      }

      "revalidate the complete snapshot after planning" in {
        Given("a fresh WIP update and a callback that changes a separate bundle")
        _with_root { root =>
          _write_bundle(root, "publication", Vector(_entry("metadata/unrelated.json", Json.obj("revision" -> 1))))
          val update = _wip_video_update()

          When("the snapshot changes before read-only revalidation")
          val error = intercept[IllegalArgumentException](CozyArticleMediaRegistry.validateWipReadOnly(root, Vector(update), () =>
            _write_bundle(root, "publication", Vector(_entry("metadata/unrelated.json", Json.obj("revision" -> 2))))
          ))

          Then("planning fails closed without a merge")
          error.getMessage should include("stale read-only snapshot")
        }
      }
    }
  }

  private def _strict(identity: String = "development-process/example", watchurl: String = "https://example.com/watch"): CozyArticleMediaPublication.Result =
    CozyArticleMediaPublication.produce(
      identity,
      Vector(CozyArticleMediaPublication.Variant(
        "ja",
        video = Some(VideoReference(VideoPresentation.ExternalLink, VideoStatus.Published, None, Some(new URI(watchurl)), None))
      ))
    )

  private def _strict_with_media(includeinfographic: Boolean): CozyArticleMediaPublication.Result =
    CozyArticleMediaPublication.produce(
      "development-process/example",
      Vector(CozyArticleMediaPublication.Variant(
        "ja",
        infographic = if (includeinfographic) Some(org.smartdox.metadata.PublishMetadata.ImageReference(new URI("/ja/development-process/images/example.png"), Some("image/png"), None)) else None,
        video = Some(VideoReference(VideoPresentation.SiteHosted, VideoStatus.Published, None, None, Some(new URI("/repository/video/example.mp4"))))
      ))
    )

  private def _site_strict(identity: String, publicpath: String): CozyArticleMediaPublication.Result =
    CozyArticleMediaPublication.produce(
      identity,
      Vector(CozyArticleMediaPublication.Variant("ja", video = Some(_site_video(publicpath))))
    )

  private def _site_video(publicpath: String = "/repository/video/example.mp4"): VideoReference =
    VideoReference(VideoPresentation.SiteHosted, VideoStatus.Published, None, None, Some(new URI(publicpath)))

  private def _infographic_image(publicpath: String): org.smartdox.metadata.PublishMetadata.ImageReference =
    org.smartdox.metadata.PublishMetadata.ImageReference(new URI(publicpath), Some("image/png"), None)

  private def _video_role_update(identity: String = "development-process/example", locale: String = "ja"): CozyArticleMediaRegistry.RoleUpdate =
    CozyArticleMediaRegistry.RoleUpdate(
      identity,
      CozyArticleMediaPublication.Variant(locale, video = Some(_site_video())),
      _video_integrity(articleidentity = identity, locale = locale)
    )

  private def _infographic_role_update(identity: String = "development-process/example", locale: String = "ja"): CozyArticleMediaRegistry.RoleUpdate =
    CozyArticleMediaRegistry.RoleUpdate(
      identity,
      CozyArticleMediaPublication.Variant(locale, infographic = Some(_infographic_image("/ja/development-process/images/example.png"))),
      _infographic_integrity(articleidentity = identity, locale = locale)
    )

  private def _wip_video_update(
    identity: String = "development-process/example",
    locale: String = "ja"
  ): CozyArticleMediaRegistry.WipRoleUpdate = {
    val digest = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    val path = s"/$locale/development-process/videos/example.mp4"
    val variant = CozyArticleMediaPublication.Variant(locale, video = Some(_site_video(path)))
    val integrity = CozyArticleMediaIntegrity.produce(CozyArticleMediaIntegrity.Input(
      articleIdentity = identity,
      locale = locale,
      role = CozyArticleMediaIntegrity.Role.Video,
      artifact = CozyArticleMediaIntegrity.Artifact(s"example-video-$locale", digest),
      publicPath = new URI(path),
      repositoryPath = s"$locale/development-process/videos/example.mp4",
      mediaType = "video/mp4",
      sha256 = digest,
      provenance = CozyArticleMediaIntegrity.WipSiteVideo("media/article.yaml", s"example-video-$locale", s"video/$locale/production.json"),
      publicationState = CozyArticleMediaIntegrity.PublicationState.Published
    ))
    CozyArticleMediaRegistry.WipRoleUpdate(identity, variant, Some(integrity))
  }

  private def _video_integrity(articleidentity: String = "development-process/example", locale: String = "ja", publicpath: String = "/repository/video/example.mp4"): CozyArticleMediaIntegrity.Result =
    CozyArticleMediaIntegrity.produce(CozyArticleMediaIntegrity.Input(
      articleIdentity = articleidentity,
      locale = locale,
      role = CozyArticleMediaIntegrity.Role.Video,
      artifact = CozyArticleMediaIntegrity.Artifact("example-video", "1.0.0"),
      publicPath = new URI(publicpath),
      repositoryPath = "video/example.mp4",
      mediaType = "video/mp4",
      sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
      provenance = CozyArticleMediaIntegrity.VideoPublication("metadata/video/example/1.0.0/manifest.json", "metadata/artifacts/repository/example.json"),
      publicationState = CozyArticleMediaIntegrity.PublicationState.Published
    ))

  private def _infographic_integrity(articleidentity: String = "development-process/example", locale: String = "ja", publicpath: String = "/ja/development-process/images/example.png"): CozyArticleMediaIntegrity.Result =
    CozyArticleMediaIntegrity.produce(CozyArticleMediaIntegrity.Input(
      articleIdentity = articleidentity,
      locale = locale,
      role = CozyArticleMediaIntegrity.Role.Infographic,
      artifact = CozyArticleMediaIntegrity.Artifact("example-infographic", "1.0.0"),
      publicPath = new URI(publicpath),
      repositoryPath = "images/development-process/example.png",
      mediaType = "image/png",
      sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
      provenance = CozyArticleMediaIntegrity.MediaPackage("src/main/media/example.yaml", "example-infographic", "target/cozy-media/manifest.json"),
      publicationState = CozyArticleMediaIntegrity.PublicationState.Registered
    ))

  private def _entry(path: String, metadata: JsObject, key: String = null): JsObject =
    {
      val entrykey: String = Option(key).getOrElse(path.stripSuffix(".json").stripPrefix("metadata/"))
      Json.obj("path" -> path, "key" -> JsString(entrykey), "metadata" -> metadata)
    }

  private def _write_bundle(root: Path, filename: String, entries: Vector[JsObject], declaredname: String = null): Unit = {
    val name = Option(declaredname).getOrElse(filename)
    val bundle = Json.obj(
      "schema" -> "cozy.publish-project.v1",
      "type" -> "publication-bundle",
      "publication" -> Json.obj("name" -> name),
      "sourceRepository" -> "cozy",
      "sourcePath" -> ".",
      "sourceCommit" -> JsString("0123456789abcdef"),
      "entries" -> JsArray(entries)
    )
    Files.write(root.resolve(s"$filename.json"), (Json.prettyPrint(bundle) + "\n").getBytes(StandardCharsets.UTF_8))
  }

  private def _write_bundle_json(root: Path, filename: String, bundle: JsObject): Unit =
    Files.write(root.resolve(s"$filename.json"), (Json.prettyPrint(bundle) + "\n").getBytes(StandardCharsets.UTF_8))

  private def _bundle_with_declaration(name: String, title: String, entries: Vector[JsObject]): JsObject =
    Json.obj(
      "schema" -> "cozy.publish-project.v1",
      "type" -> "publication-bundle",
      "publication" -> Json.obj("name" -> name, "title" -> title),
      "sourceRepository" -> "cozy",
      "sourcePath" -> ".",
      "sourceCommit" -> JsString("0123456789abcdef"),
      "extension" -> Json.obj("owner" -> name),
      "entries" -> JsArray(entries)
    )

  private def _bundle_bytes(path: Path): Vector[Byte] =
    Files.readAllBytes(path).toVector

  private def _direct_names(root: Path): Vector[String] = {
    val stream = Files.list(root)
    try stream.iterator.asScala.toVector.map(_.getFileName.toString).sorted
    finally stream.close()
  }

  private def _concurrent_register(
    root: Path,
    path: String,
    ready: CountDownLatch,
    start: CountDownLatch,
    failures: ConcurrentLinkedQueue[Throwable]
  ): Unit =
    try {
      ready.countDown()
      start.await()
      CozyPublicationCompiler.registerMetadata(root, "publication", Vector(path -> Json.obj("registered" -> true)))
    } catch {
      case e: Throwable => failures.add(e)
    }

  private def _concurrent_role_merge(
    root: Path,
    update: CozyArticleMediaRegistry.RoleUpdate,
    ready: CountDownLatch,
    start: CountDownLatch,
    failures: ConcurrentLinkedQueue[Throwable]
  ): Unit =
    try {
      ready.countDown()
      start.await()
      CozyArticleMediaRegistry.transaction(root)(_.merge(Vector(update)))
    } catch {
      case e: Throwable => failures.add(e)
    }

  private def _with_root[A](f: Path => A): A = {
    val workroot = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve("target/cozy-article-media-registry/work")
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
