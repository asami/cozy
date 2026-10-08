package cozy.archive

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.security.MessageDigest

import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}

import scala.collection.JavaConverters._

/*
 * @since   Aug. 25, 2026
 * @version Oct. 5, 2026
 */
final class ComponentReleaseSourceProjectionSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _public_policy = ComponentReleaseSourceProjection.Policy("public", "Apache-2.0")
  private val _restricted_policy = ComponentReleaseSourceProjection.Policy("restricted", "Apache-2.0")
  private val _evidence = ComponentReleaseSourceProjection.BuildEvidence(
    Vector("-deprecation"),
    Vector("-source:future"),
    Vector("org.example:generator:1.0.0"),
    Vector("org.simplemodeling:cozy:0.3.3-SNAPSHOT", "backend:cozy")
  )

  "ComponentReleaseSourceProjection" should {
    "required staged distribution semantics" which {
    "DOC03-SOURCE-RELEASE-AC-01 emit canonical public entries and normalized managed sources" in {
      _with_temp_dir("cozy-release-source-public") { root =>
        Given("a project with authored files and current main/test managed Scala sources")
        _write(root.resolve("src/main/scala/PublicApi.scala"), "final class PublicApi\n")
        _write(root.resolve("src/main/scala/Generated.class"), "class bytes\n")
        _write(root.resolve("src/main/.env.local"), "secret\n")
        _write(root.resolve("README.md"), "# Public\n")
        val mainroot = root.resolve("target/scala-3/src_managed/main")
        val testroot = root.resolve("target/scala-3/src_managed/test")
        val main = _write(mainroot.resolve("fixture/Generated.scala"), "final class Generated\n")
        val test = _write(testroot.resolve("fixture/GeneratedSpec.scala"), "final class GeneratedSpec\n")
        When("the public release-source stage is created")
        val stage = ComponentReleaseSourceProjection.stage(
          root,
          root.resolve("target/cozy/release-source"),
          _public_policy,
          Vector(main), Vector(mainroot), Vector(test), Vector(testroot), _evidence
        )
        val verified = ComponentReleaseSourceProjection.verify(stage, root, _public_policy)
        Then("the stage contains the exact public projection and verifies its declared payload")
        val manifest = Json.parse(Files.readString(stage.resolve(ComponentReleaseSourceProjection.MANIFEST_FILE_NAME), StandardCharsets.UTF_8))
        val paths = (manifest \ "entries").as[Vector[play.api.libs.json.JsObject]].map(value => (value \ "path").as[String])
        paths shouldBe paths.sorted
        paths should contain allOf (
          "README.md",
          "src/main/scala/PublicApi.scala",
          "generated-source/main/fixture/Generated.scala",
          "generated-source/test/fixture/GeneratedSpec.scala"
        )
        paths should not contain "src/main/scala/Generated.class"
        paths should not contain "src/main/.env.local"
        Files.readString(stage.resolve("generated-source/main/fixture/Generated.scala"), StandardCharsets.UTF_8) shouldBe
          "final class Generated\n"
        val generatedentry = (manifest \ "entries").as[Vector[play.api.libs.json.JsObject]].find(value => (value \ "path").as[String] == "generated-source/main/fixture/Generated.scala").get
        (generatedentry \ "sha256").as[String] shouldBe _sha256("final class Generated\n")
        Files.readString(stage.resolve(ComponentReleaseSourceProjection.MANIFEST_FILE_NAME), StandardCharsets.UTF_8) should not include root.toAbsolutePath.normalize().toString
        verified.entries.map(_.path) shouldBe paths
      }
    }

    "DOC03-SOURCE-RELEASE-AC-02 reject hostile, escaping, duplicate, and transient managed inputs" in {
      _with_temp_dir("cozy-release-source-hostile") { root =>
        Given("a project and managed-source inputs that include unsafe or escaping paths")
        _write(root.resolve("src/main/scala/PublicApi.scala"), "final class PublicApi\n")
        val managedroot = root.resolve("managed")
        Files.createDirectories(managedroot)
        val outside = _write(root.getParent.resolve(root.getFileName.toString + "-Outside.scala"), "outside\n")
        When("an escaping managed source is staged")
        val escaping = the[Throwable] thrownBy {
          ComponentReleaseSourceProjection.stage(
            root, root.resolve("target/cozy/release-source"), _public_policy,
            Vector(outside), Vector(managedroot), Vector.empty, Vector.empty, _evidence
          )
        }
        Then("an escaping managed source is rejected")
        escaping.getMessage should include("escapes")
        Files.delete(outside)
        Given("two managed roots selecting the same normalized path")
        val left = _write(root.resolve("left/Same.scala"), "left\n")
        val right = _write(root.resolve("right/Same.scala"), "right\n")
        When("duplicate managed source paths are staged")
        val duplicate = the[Throwable] thrownBy {
          ComponentReleaseSourceProjection.stage(
            root, root.resolve("target/cozy/release-source"), _public_policy,
            Vector(left, right), Vector(root.resolve("left"), root.resolve("right")),
            Vector.empty, Vector.empty, _evidence
          )
        }
        Then("duplicate managed source paths are rejected")
        duplicate.getMessage should include("duplicated")
        Given("a managed source represented by a symbolic link")
        val link = root.resolve("managed/Link.scala")
        Files.createDirectories(link.getParent)
        Files.createSymbolicLink(link, root.resolve("src/main/scala/PublicApi.scala"))
        When("a symbolic-link managed source is staged")
        val symlink = the[Throwable] thrownBy {
          ComponentReleaseSourceProjection.stage(
            root, root.resolve("target/cozy/release-source"), _public_policy,
            Vector(link), Vector(managedroot), Vector.empty, Vector.empty, _evidence
          )
        }
        Then("the projection rejects every unsafe managed-source form")
        symlink.getMessage should include("symbolic link")
      }
    }

    "DOC03-SOURCE-RELEASE-AC-03 represent restricted source as manifest-only" in {
      _with_temp_dir("cozy-release-source-restricted") { root =>
        Given("a project with private authored source and a restricted policy")
        _write(root.resolve("src/main/scala/PrivateApi.scala"), "final class PrivateApi\n")
        When("the restricted release-source stage is created and verified")
        val stage = ComponentReleaseSourceProjection.stage(
          root, root.resolve("target/cozy/release-source"), _restricted_policy,
          Vector.empty, Vector.empty, Vector.empty, Vector.empty, _evidence
        )
        val verified = ComponentReleaseSourceProjection.verify(stage, root, _restricted_policy)
        Then("restricted staging contains only its canonical manifest")
        val files = Files.walk(stage)
        val actual = try files.iterator().asScala.filter(Files.isRegularFile(_)).toVector
        finally files.close()
        actual.map(_.getFileName.toString) shouldBe Vector(ComponentReleaseSourceProjection.MANIFEST_FILE_NAME)
        val manifest = Json.parse(Files.readString(stage.resolve(ComponentReleaseSourceProjection.MANIFEST_FILE_NAME), StandardCharsets.UTF_8))
        (manifest \ "policy" \ "mode").as[String] shouldBe "restricted"
        (manifest \ "entries").as[Vector[play.api.libs.json.JsObject]] shouldBe Vector.empty
        verified.entries shouldBe Vector.empty
      }
    }

    "DOC03-SOURCE-RELEASE-AC-04 admit prebuilt authored payload after source edits or removal" in {
      _with_temp_dir("cozy-release-source-prebuilt") { root =>
        Given("a public stage built from an authored source")
        val source = _write(root.resolve("src/main/scala/PublicApi.scala"), "final class PublicApi\n")
        val stage = ComponentReleaseSourceProjection.stage(
          root, root.resolve("target/cozy/release-source"), _public_policy,
          Vector.empty, Vector.empty, Vector.empty, Vector.empty, _evidence
        )
        val original = Files.readString(stage.resolve(ComponentReleaseSourceProjection.MANIFEST_FILE_NAME))
        _write(source, "final class Changed\n")
        When("the supplied stage is admitted after current source editing and removal")
        val edited = ComponentReleaseSourceProjection.verify(stage, root, _public_policy)
        Files.delete(source)
        val removed = ComponentReleaseSourceProjection.verifyForPackaging(stage, root)
        Then("the original supplied distribution remains intact without consulting authored bytes")
        edited.entries shouldBe removed.entries
        removed.entries.map(_.path) shouldBe Vector("src/main/scala/PublicApi.scala")
        removed.manifestText shouldBe original
        Files.readString(stage.resolve("src/main/scala/PublicApi.scala")) shouldBe "final class PublicApi\n"
        Given("the admitted stage with a corrupted payload entry")
        _write(stage.resolve("src/main/scala/PublicApi.scala"), "tampered\n")
        When("the corrupted staged distribution is admitted")
        val error = the[Throwable] thrownBy ComponentReleaseSourceProjection.verifyForPackaging(stage, root)
        Then("the actual entry mismatch rejects with its payload diagnostic")
        error.getMessage should include("staged entry digest differs")
      }
    }

    "DOC03-SOURCE-RELEASE-AC-05 admit prebuilt managed payload after managed sources disappear" in {
      _with_temp_dir("cozy-release-source-managed-admission") { root =>
        Given("a public stage containing an explicitly selected managed Scala source")
        val managedroot = root.resolve("target/scala-3/src_managed/main")
        val managed = _write(managedroot.resolve("fixture/Generated.scala"), "final class Generated\n")
        val stage = ComponentReleaseSourceProjection.stage(
          root, root.resolve("target/cozy/release-source"), _public_policy,
          Vector(managed), Vector(managedroot), Vector.empty, Vector.empty, _evidence
        )
        _write(managed, "final class ChangedGenerated\n")
        When("the stage is admitted after managed source editing and removal")
        val edited = ComponentReleaseSourceProjection.verifyForPackaging(stage, root)
        _delete_tree(managedroot)
        val removed = ComponentReleaseSourceProjection.verifyForPackaging(stage, root)
        Then("the normalized managed payload remains admitted from staged bytes")
        edited.entries shouldBe removed.entries
        removed.entries.map(_.path) shouldBe Vector("generated-source/main/fixture/Generated.scala")
        Given("an extra file in the supplied stage")
        _write(stage.resolve("generated-source/main/fixture/Fake.scala"), "fake\n")
        When("the extra staged file is admitted")
        val extra = the[Throwable] thrownBy ComponentReleaseSourceProjection.verifyForPackaging(stage, root)
        Then("the undeclared staged file rejects")
        extra.getMessage should include("entries differ")
      }
    }

    "DOC03-SOURCE-RELEASE-AC-06 reject unsafe policy and build evidence while admitting deterministic identities" in {
      _with_temp_dir("cozy-release-source-evidence-admission") { root =>
        Given("representative path, state, host, timestamp, secret, and raw-byte metadata values")
        val hostile = Vector(
          "/tmp/project",
          "src/main/scala/Foo.scala",
          "target/classes",
          ".cozy/config.yaml",
          "https://example.com",
          "localhost:8080",
          "192.168.1.1",
          "192.168.1.1:8080",
          "2001:db8::1",
          "[2001:db8::1]:8080",
          "2026-08-25T12:34:56Z",
          "password=secret",
          "AAECAwQFBgcICQoLDA0ODw=="
        )
        When("each hostile value is supplied as a policy license or build-evidence value")
        val policyerrors = hostile.map { value =>
          the[Throwable] thrownBy ComponentReleaseSourceProjection.policy("public", value)
        }
        val evidenceerrors = hostile.map { value =>
          the[Throwable] thrownBy ComponentReleaseSourceProjection.stage(
            root,
            root.resolve("target/cozy/release-source"),
            _public_policy,
            Vector.empty,
            Vector.empty,
            Vector.empty,
            Vector.empty,
            ComponentReleaseSourceProjection.BuildEvidence(Vector(value), Vector.empty, Vector.empty, Vector.empty)
          )
        }
        val admitted = ComponentReleaseSourceProjection.stage(
          root,
          root.resolve("target/cozy/release-source"),
          _public_policy,
          Vector.empty,
          Vector.empty,
          Vector.empty,
          Vector.empty,
          _evidence
        )
        val admittedverification = ComponentReleaseSourceProjection.verify(admitted, root, _public_policy)
        val admittedpolicy = ComponentReleaseSourceProjection.policy("public", "Apache-2.0")
        Then("all hostile values are rejected")
        policyerrors.foreach(_.getMessage should include("safe license"))
        evidenceerrors.foreach(_.getMessage should include("local path"))
        admittedpolicy shouldBe _public_policy
        admittedverification.entries shouldBe Vector.empty
      }
    }
    }

    "ordinary supplied manifests" which {
      Vector("public", "restricted").foreach { mode =>
        Vector("verify", "verifyForPackaging").foreach { route =>
          s"reject leading and trailing LF CR and NUL in every required $mode evidence array through $route without changing the stage" in {
            _with_temp_dir("cozy-release-source-raw-controls") { root =>
              Given(s"a valid $mode stage and all required evidence fields with forbidden edge controls")
              _write(root.resolve("src/main/scala/PublicApi.scala"), "original")
              val policy = ComponentReleaseSourceProjection.Policy(mode, "Apache-2.0")
              val stage = ComponentReleaseSourceProjection.stage(
                root, root.resolve("target/cozy/release-source"), policy,
                Vector.empty, Vector.empty, Vector.empty, Vector.empty, _evidence
              )
              val manifestpath = stage.resolve(ComponentReleaseSourceProjection.MANIFEST_FILE_NAME)
              val required = Json.parse(Files.readString(manifestpath)).as[JsObject]
              val payload = (required \ "entries").as[Vector[JsObject]].map { entry =>
                val path = (entry \ "path").as[String]
                path -> Files.readAllBytes(stage.resolve(path)).toVector
              }
              val fields = Vector(
                "compileScalacOptions" -> "compile scalacOptions",
                "testScalacOptions" -> "test scalacOptions",
                "dependencies" -> "dependency", "generators" -> "generator"
              )
              for {
                (field, label) <- fields
                (controlname, control) <- Vector("LF" -> "\n", "CR" -> "\r", "NUL" -> "\u0000")
                placement <- Vector("leading", "trailing")
              } {
                Given(s"$field containing a $placement $controlname around an otherwise safe historical value")
                val value = if (placement == "leading") control + "-deprecation" else "-deprecation" + control
                val evidence = (required \ "buildEvidence").as[JsObject] + (field -> Json.arr(value))
                val historical = " \n" + Json.prettyPrint(required + ("buildEvidence" -> evidence)) + "\n "
                _write(manifestpath, historical)
                When(s"$route admits the supplied $mode stage")
                val error = the[Throwable] thrownBy {
                  if (route == "verify") ComponentReleaseSourceProjection.verify(stage, root, policy)
                  else ComponentReleaseSourceProjection.verifyForPackaging(stage, root)
                }
                Then("the field-specific evidence rejection preserves the supplied manifest and payload bytes")
                error.getMessage should include(s"$label evidence")
                Files.readString(manifestpath) shouldBe historical
                payload.foreach { case (path, bytes) => Files.readAllBytes(stage.resolve(path)).toVector shouldBe bytes }
              }
            }
          }
        }

        s"normalize producer padding and preserve exact safe historical $mode evidence through both admission routes" in {
          _with_temp_dir("cozy-release-source-safe-padding") { root =>
            Given(s"a $mode project with ordinary padded producer evidence in all four required arrays")
            _write(root.resolve("src/main/scala/PublicApi.scala"), "original")
            val policy = ComponentReleaseSourceProjection.Policy(mode, "Apache-2.0")
            val padded = ComponentReleaseSourceProjection.BuildEvidence(
              Vector(" -deprecation "), Vector(" -source:future "),
              Vector(" org.example:generator:1.0.0 "), Vector(" backend:cozy ")
            )
            When("the producer creates the release-source stage")
            val stage = ComponentReleaseSourceProjection.stage(
              root, root.resolve("target/cozy/release-source"), policy,
              Vector.empty, Vector.empty, Vector.empty, Vector.empty, padded
            )
            Then("producer evidence keeps its normalized values")
            val manifestpath = stage.resolve(ComponentReleaseSourceProjection.MANIFEST_FILE_NAME)
            val required = Json.parse(Files.readString(manifestpath)).as[JsObject]
            (required \ "buildEvidence").as[JsObject] shouldBe Json.obj(
              "compileScalacOptions" -> Json.arr("-deprecation"), "testScalacOptions" -> Json.arr("-source:future"),
              "dependencies" -> Json.arr("org.example:generator:1.0.0"), "generators" -> Json.arr("backend:cozy")
            )
            Given("safe historical arrays with padding, order, duplicates and generically ignored unknown properties")
            val evidence = Json.obj(
              "compileScalacOptions" -> Json.arr(" -deprecation", "-feature ", " -deprecation"),
              "testScalacOptions" -> Json.arr(" -source:future "),
              "dependencies" -> Json.arr(" org.example:generator:1.0.0 "),
              "generators" -> Json.arr(" backend:cozy ")
            )
            val expected = required + ("buildEvidence" -> evidence)
            val supplied = expected ++ Json.obj(
              "unknown" -> Json.arr(false), "sourceDigest" -> Json.arr(false),
              "buildEvidenceDigest" -> Json.obj("invalid" -> true),
              "buildEvidence" -> (evidence + ("unknown" -> Json.obj("nested" -> false)))
            )
            val historical = " \n" + Json.prettyPrint(supplied) + "\n "
            _write(manifestpath, historical)
            val payload = (required \ "entries").as[Vector[JsObject]].map { entry =>
              val path = (entry \ "path").as[String]
              path -> Files.readAllBytes(stage.resolve(path)).toVector
            }
            Vector("verify", "verifyForPackaging").foreach { route =>
              When(s"$route admits the historical $mode stage")
              val verified = if (route == "verify") ComponentReleaseSourceProjection.verify(stage, root, policy)
                else ComponentReleaseSourceProjection.verifyForPackaging(stage, root)
              Then("required historical values remain exact while unknown extras are ignored and input bytes stay intact")
              Json.parse(verified.manifestText) shouldBe expected
              Files.readString(manifestpath) shouldBe historical
              payload.foreach { case (path, bytes) => Files.readAllBytes(stage.resolve(path)).toVector shouldBe bytes }
            }
            if (mode == "public") {
              Given("the admissible historical manifest with one corrupted staged payload")
              _write(stage.resolve("src/main/scala/PublicApi.scala"), "tampered")
              Vector("verify", "verifyForPackaging").foreach { route =>
                When(s"$route admits the corrupted payload")
                val error = the[Throwable] thrownBy {
                  if (route == "verify") ComponentReleaseSourceProjection.verify(stage, root, policy)
                  else ComponentReleaseSourceProjection.verifyForPackaging(stage, root)
                }
                Then("named payload integrity still rejects with an entry-digest diagnostic")
                error.getMessage should include("staged entry digest differs")
                Files.readString(manifestpath) shouldBe historical
              }
            }
          }
        }
      }

      "normalize required metadata across fifty varied current-source changes and arbitrary extras" in {
        val extragen = Gen.oneOf[JsValue](Json.obj("nested" -> Json.arr(1, false)), Json.arr("anything", 3), Json.toJson(false), Json.toJson("invalid old value"))
        val property = Prop.forAll(Gen.alphaNumStr, extragen, Gen.oneOf(true, false), Gen.choose(0, 100)) { (text, extra, remove, layout) =>
          _with_temp_dir("cozy-release-source-history") { root =>
            Given("a public staged payload and required typed policy and build evidence")
            val source = _write(root.resolve("src/main/scala/PublicApi.scala"), "original")
            val managedroot = root.resolve("target/managed")
            val managed = _write(managedroot.resolve("Generated.scala"), "generated")
            val stage = ComponentReleaseSourceProjection.stage(
              root, root.resolve("target/cozy/release-source"), _public_policy,
              Vector(managed), Vector(managedroot), Vector.empty, Vector.empty, _evidence
            )
            val manifestpath = stage.resolve(ComponentReleaseSourceProjection.MANIFEST_FILE_NAME)
            val canonical = Files.readString(manifestpath)
            val required = Json.parse(canonical).as[JsObject]
            val entries = (required \ "entries").as[Vector[JsObject]].map(_ + ("unknown" -> extra))
            val supplied = required ++ Json.obj(
              "unknown" -> extra,
              "sourceDigest" -> Json.arr(false),
              "buildEvidenceDigest" -> Json.obj("invalid" -> true),
              "policy" -> ((required \ "policy").as[JsObject] + ("unknown" -> extra)),
              "buildEvidence" -> ((required \ "buildEvidence").as[JsObject] + ("unknown" -> extra)),
              "entries" -> JsArray(entries)
            )
            val fields = supplied.fields.reverse
            val shift = layout % fields.size
            val ordered = fields.drop(shift) ++ fields.take(shift)
            val historical = (" " * (layout % 5)) + "\n" + Json.prettyPrint(JsObject(ordered)) + "\n" + (" " * (layout % 7))
            _write(manifestpath, historical)
            _write(source, text)
            _write(managed, text.reverse)
            if (remove) { Files.delete(source); _delete_tree(managedroot) }
            When("the ordinary supplied manifest and staged payload are admitted independently of producer state")
            val verified = ComponentReleaseSourceProjection.verify(stage, root, _public_policy)
            Then("only required semantics are normalized while original metadata, bytes and input text are preserved")
            verified.manifest shouldBe manifestpath
            verified.manifestText shouldBe canonical
            Files.readString(manifestpath) shouldBe historical
            (Json.parse(verified.manifestText) \ "buildEvidence").as[JsObject] shouldBe (required \ "buildEvidence").as[JsObject]
            Files.readString(stage.resolve("src/main/scala/PublicApi.scala")) shouldBe "original"
            Files.readString(stage.resolve("generated-source/main/Generated.scala")) shouldBe "generated"
            verified.archiveEntries.map(_._2) should not contain "source/release-source-manifest.json"
            true
          }
        }
        Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property).passed shouldBe true
      }

      "reject malformed required metadata and actual staged mismatches" in {
        val cases = Vector(
          "schema" -> "schema",
          "policy" -> "mode",
          "identity" -> "source identity",
          "buildEvidence" -> "must contain strings",
          "unsafeEvidence" -> "local path",
          "digest" -> "content digest differs",
          "entryDigest" -> "invalid sha256",
          "unsafePath" -> "unsafe entry",
          "missing" -> "entries differ",
          "extra" -> "entries differ",
          "symlink" -> "symbolic link"
        )
        cases.foreach { case (kind, diagnostic) =>
          _with_temp_dir("cozy-release-source-rejection") { root =>
            Given(s"a valid public stage whose $kind operand is made invalid")
            _write(root.resolve("src/main/scala/PublicApi.scala"), "original")
            val stage = ComponentReleaseSourceProjection.stage(
              root, root.resolve("target/cozy/release-source"), _public_policy,
              Vector.empty, Vector.empty, Vector.empty, Vector.empty, _evidence
            )
            val manifestpath = stage.resolve(ComponentReleaseSourceProjection.MANIFEST_FILE_NAME)
            val json = Json.parse(Files.readString(manifestpath)).as[JsObject]
            val entry = (json \ "entries").as[Vector[JsObject]].head
            kind match {
              case "schema" => _write(manifestpath, Json.stringify(json - "schema"))
              case "policy" => _write(manifestpath, Json.stringify(json + ("policy" -> Json.obj("mode" -> false, "license" -> "Apache-2.0"))))
              case "identity" => _write(manifestpath, Json.stringify(json + ("sourceIdentity" -> Json.toJson(false))))
              case "buildEvidence" =>
                val invalid = (json \ "buildEvidence").as[JsObject] + ("generators" -> Json.arr(false))
                _write_json(manifestpath, json + ("buildEvidence" -> invalid))
              case "unsafeEvidence" =>
                val invalid = (json \ "buildEvidence").as[JsObject] + ("generators" -> Json.arr("/tmp/local"))
                _write_json(manifestpath, json + ("buildEvidence" -> invalid))
              case "digest" => _write(manifestpath, Json.stringify(json + ("contentDigest" -> Json.toJson("0" * 64))))
              case "entryDigest" =>
                val invalid = entry + ("sha256" -> Json.toJson("invalid"))
                _write_json(manifestpath, json + ("entries" -> Json.arr(invalid)))
              case "unsafePath" =>
                val invalid = entry + ("path" -> Json.toJson("../escape"))
                _write_json(manifestpath, json + ("entries" -> Json.arr(invalid)))
              case "missing" => Files.delete(stage.resolve("src/main/scala/PublicApi.scala"))
              case "extra" => _write(stage.resolve("extra.scala"), "extra")
              case "symlink" => Files.createSymbolicLink(stage.resolve("alias.scala"), root.resolve("src/main/scala/PublicApi.scala"))
            }
            When("the supplied distribution is admitted")
            val error = the[Throwable] thrownBy ComponentReleaseSourceProjection.verifyForPackaging(stage, root)
            Then("the required semantic or actual payload violation rejects specifically")
            error.getMessage should include(diagnostic)
          }
        }
      }

      "reject payloads in restricted stages and preserve an existing stage on a new producer request" in {
        _with_temp_dir("cozy-release-source-restricted-guards") { root =>
          Given("a manifest-only restricted stage")
          val stage = ComponentReleaseSourceProjection.stage(root, root.resolve("target/cozy/release-source"), _restricted_policy,
            Vector.empty, Vector.empty, Vector.empty, Vector.empty, _evidence)
          val original = Files.readString(stage.resolve(ComponentReleaseSourceProjection.MANIFEST_FILE_NAME))
          When("a fresh stage request targets that existing output")
          val existing = the[Throwable] thrownBy ComponentReleaseSourceProjection.stage(root, stage, _restricted_policy,
            Vector.empty, Vector.empty, Vector.empty, Vector.empty, _evidence)
          Then("the existing stage and manifest remain unchanged")
          existing.getMessage should include("already exists")
          Files.readString(stage.resolve(ComponentReleaseSourceProjection.MANIFEST_FILE_NAME)) shouldBe original
          Given("an undeclared payload in the restricted stage")
          _write(stage.resolve("Private.scala"), "private")
          When("the supplied restricted distribution is admitted")
          val payload = the[Throwable] thrownBy ComponentReleaseSourceProjection.verifyForPackaging(stage, root)
          Then("the restricted payload rejects")
          payload.getMessage should include("entries differ")
        }
      }
    }
  }

  private def _with_temp_dir[A](prefix: String)(f: Path => A): A = {
    val workroot = Path.of("target/cozy-test/work/component-release-source").toAbsolutePath.normalize()
    Files.createDirectories(workroot)
    val root = Files.createTempDirectory(workroot, prefix)
    try f(root)
    finally _delete_tree(root)
  }

  private def _write_json(path: Path, value: JsValue): Unit = {
    _write(path, Json.stringify(value))
  }

  private def _write(path: Path, content: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))
    path
  }

  private def _delete_tree(path: Path): Unit = {
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try stream.iterator().asScala.toVector.sortBy(_.toString.length).reverse.foreach(Files.deleteIfExists(_))
      finally stream.close()
    }
  }

  private def _sha256(content: String): String =
    MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)).map(byte => f"${byte & 0xff}%02x").mkString
}
