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
import play.api.libs.json.{JsArray, JsObject, JsString, Json}

/*
 * @since   Aug.  4, 2026
 * @version Aug.  4, 2026
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

  private def _video_integrity(articleidentity: String = "development-process/example"): CozyArticleMediaIntegrity.Result =
    CozyArticleMediaIntegrity.produce(CozyArticleMediaIntegrity.Input(
      articleIdentity = articleidentity,
      locale = "ja",
      role = CozyArticleMediaIntegrity.Role.Video,
      artifact = CozyArticleMediaIntegrity.Artifact("example-video", "1.0.0"),
      publicPath = new URI("/repository/video/example.mp4"),
      repositoryPath = "video/example.mp4",
      mediaType = "video/mp4",
      sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
      provenance = CozyArticleMediaIntegrity.VideoPublication("metadata/video/example/1.0.0/manifest.json", "metadata/artifacts/repository/example.json"),
      publicationState = CozyArticleMediaIntegrity.PublicationState.Published
    ))

  private def _infographic_integrity(): CozyArticleMediaIntegrity.Result =
    CozyArticleMediaIntegrity.produce(CozyArticleMediaIntegrity.Input(
      articleIdentity = "development-process/example",
      locale = "ja",
      role = CozyArticleMediaIntegrity.Role.Infographic,
      artifact = CozyArticleMediaIntegrity.Artifact("example-infographic", "1.0.0"),
      publicPath = new URI("/ja/development-process/images/example.png"),
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
