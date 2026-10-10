package cozy

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path}
import java.util.zip.{ZipEntry, ZipOutputStream}

import scala.collection.JavaConverters._

import cozy.archive.{ComponentApiDependencyResolver, CozyComponentReleaseCoordinateCodec}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalacheck.{Gen, Prop, Test}
import play.api.libs.json._

/*
 * @since   Jul. 12, 2026
 *  version Aug.  7, 2026
 * @version Oct.  9, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentApiDependencyResolverSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Component API dependency resolver" should {
    "select declared API providers" which {
    "extract the one declared CAR API that satisfies a required contract" in {
      _with_temp_dir("cozy-api-dependency") { dir =>
        Given("a consumer requirement, matching provider CAR, and assembly coordinate")
        val consumer = _write(dir.resolve("consumer.json"), _consumer_descriptor("example.Api"))
        val provider = _provider_car(dir.resolve("provider.car"), "org.example", "Provider", "0.1.0", "example.Api")
        val assembly = _write(
          dir.resolve("assembly.yaml"),
          """subsystem: consumer
            |components:
            |  - namespace: org.example
            |    id: Provider
            |    version: 0.1.0
            |""".stripMargin
        )

        When("the dependency is resolved")
        val jars = ComponentApiDependencyResolver._resolve_dependencies(
          consumer,
          Vector(ComponentApiDependencyResolver.Dependency("org.example", "Provider", "0.1.0", provider)),
          dir.resolve("resolved"),
          Some(assembly)
        )

        Then("only the contract API JAR is extracted")
        jars shouldBe Vector(dir.resolve("resolved/org/example/example-provider/0.1.0/example-provider-api.jar"))
        Files.readString(jars.head) shouldBe "api"
      }
    }

    "fail when no declared CAR provides a required API" in {
      _with_temp_dir("cozy-api-missing") { dir =>
        Given("a required API and an unrelated provider CAR")
        val consumer = _write(dir.resolve("consumer.json"), _consumer_descriptor("example.Required"))
        val provider = _provider_car(dir.resolve("provider.car"), "org.example", "Provider", "0.1.0", "example.Other")

        When("dependency matching runs")
        val error = intercept[Throwable] {
          ComponentApiDependencyResolver._resolve_dependencies(
            consumer,
            Vector(ComponentApiDependencyResolver.Dependency("org.example", "Provider", "0.1.0", provider)),
            dir.resolve("resolved"),
            None
          )
        }

        Then("the missing required API is reported")
        error.getMessage should include("Required component API is not provided")
      }
    }

    "allow an unavailable optional API while resolving declared dependencies" in {
      _with_temp_dir("cozy-api-optional") { dir =>
        Given("an optional API requirement with no matching provider")
        val consumer = _write(
          dir.resolve("consumer.json"),
          """{"schemaVersion":"cncf.component-api.v2","component":{"namespace":"org.example","id":"Consumer","version":"0.1.0"},"provided":[],"required":[{"apiClass":"example.Optional","required":false}]}"""
        )

        When("dependency matching runs without providers")
        val jars = ComponentApiDependencyResolver._resolve_dependencies(consumer, Vector.empty, dir.resolve("resolved"), None)

        Then("no API JAR is required")
        jars shouldBe Vector.empty
      }
    }

    "fail when more than one CAR provides the same required API" in {
      _with_temp_dir("cozy-api-ambiguous") { dir =>
        Given("two provider CARs for one required API")
        val consumer = _write(dir.resolve("consumer.json"), _consumer_descriptor("example.Api"))
        val first = _provider_car(dir.resolve("first.car"), "org.example", "First", "0.1.0", "example.Api")
        val second = _provider_car(dir.resolve("second.car"), "org.example", "Second", "0.2.0", "example.Api")

        When("dependency matching runs")
        val error = intercept[Throwable] {
          ComponentApiDependencyResolver._resolve_dependencies(
            consumer,
            Vector(
              ComponentApiDependencyResolver.Dependency("org.example", "First", "0.1.0", first),
              ComponentApiDependencyResolver.Dependency("org.example", "Second", "0.2.0", second)
            ),
            dir.resolve("resolved"),
            None
          )
        }

        Then("the ambiguous contract is rejected")
        error.getMessage should include("provided ambiguously")
      }
    }

    "fail when assembly metadata omits a declared CAR dependency" in {
      _with_temp_dir("cozy-api-assembly") { dir =>
        Given("a valid API provider omitted from the assembly descriptor")
        val consumer = _write(dir.resolve("consumer.json"), _consumer_descriptor("example.Api"))
        val provider = _provider_car(dir.resolve("provider.car"), "org.example", "Provider", "0.1.0", "example.Api")
        val assembly = _write(dir.resolve("assembly.yaml"), "subsystem: consumer\ncomponents: []\n")

        When("dependency and assembly metadata are validated")
        val error = intercept[Throwable] {
          ComponentApiDependencyResolver._resolve_dependencies(
            consumer,
            Vector(ComponentApiDependencyResolver.Dependency("org.example", "Provider", "0.1.0", provider)),
            dir.resolve("resolved"),
            Some(assembly)
          )
        }

        Then("the coordinate mismatch is reported")
        error.getMessage should include("assembly-descriptor.yaml must contain declared CAR dependencies")
      }
    }

    "reject a provider CAR whose provided API release differs from its component release" in {
      _with_temp_dir("cozy-api-provider-release") { dir =>
        Given("a provider CAR with a stale provided.version")
        val consumer = _write(dir.resolve("consumer.json"), _consumer_descriptor("example.Api"))
        val provider = _provider_car(dir.resolve("provider.car"), "org.example", "Provider", "0.1.0", "example.Api", "0.0.9")

        When("the provider descriptor is admitted before API extraction")
        val error = intercept[Throwable] {
          ComponentApiDependencyResolver._resolve_dependencies(
            consumer,
            Vector(ComponentApiDependencyResolver.Dependency("org.example", "Provider", "0.1.0", provider)),
            dir.resolve("resolved"),
            None
          )
        }

        Then("the shared provided.version projection diagnostic is retained")
        error.getMessage should include("component.release-coordinate.projection-mismatch")
        error.getMessage should include("field=provided.version")
      }
    }

    "admit consumer identity before resolving an empty provider set" in {
      val cases = Vector(
        ("""{"schemaVersion":"cncf.component-api.v2","component":{"id":"Consumer","version":"0.1.0"},"provided":[],"required":[]}""", "component.release-coordinate.mismatch"),
        ("""{"schemaVersion":"cncf.component-api.v2","component":{"namespace":"org.example","id":"consumer","version":"0.1.0"},"provided":[],"required":[]}""", "component.identity.local-id.format"),
        ("""{"schemaVersion":"cncf.component-api.v2","component":{"namespace":"org.example","id":"Consumer"},"provided":[],"required":[]}""", "component.release-coordinate.mismatch")
      )
      _with_temp_dir("cozy-api-consumer-identity") { dir =>
        cases.zipWithIndex.foreach { case ((descriptor, code), index) =>
          Given("malformed consumer namespace, ID, and release values with no requirements")
          val consumer = _write(dir.resolve(s"consumer-${index}.json"), descriptor)

          When("the consumer descriptor is loaded before provider selection")
          val error = intercept[Throwable] {
            ComponentApiDependencyResolver._resolve_dependencies(consumer, Vector.empty, dir.resolve(s"resolved-${index}"), None)
          }

          Then("the shared consumer identity diagnostic is retained")
          error.getMessage should include(code)
          error.getMessage should include("consumer-descriptor:")
        }
      }
    }
    }
  }

  "Component API historical metadata" should {
    "ignore ordinary unknown extras" which {
      "extract a sole provider for arbitrary old values without changing its path or bytes" in {
        val examples = _historical_values.map(value => value -> value) :+
          (Some(JsString("sha256:consumer")) -> Some(JsString("sha256:provider")))
        val arbitrary = Gen.alphaNumStr.map(text => Some(JsString(text)): Option[JsValue])
        val extras = Gen.oneOf(Gen.oneOf(_historical_values), arbitrary)
        val property = Prop.forAll(extras, extras) { (consumerextra, providerextra) =>
          _extract_with_extras(consumerextra, providerextra)
          true
        }
        examples.foreach { case (consumerextra, providerextra) => _extract_with_extras(consumerextra, providerextra) }
        Then("bounded arbitrary metadata has the same selected payload contract")
        Test.check(Test.Parameters.default.withMinSuccessfulTests(40), property).passed shouldBe true
      }

      "retain genuine ambiguity for equal and differing extras on required and optional APIs" in {
        val property = Prop.forAll(Gen.alphaNumStr, Gen.oneOf(true, false), Gen.oneOf(true, false)) { (text, equal, required) =>
          _with_temp_dir("cozy-api-extras-ambiguous") { dir =>
            Given("two actual providers and a required or present optional API with historical extras")
            val consumer = _write(dir.resolve("consumer.json"), Json.stringify(_consumer_json(Vector(
              _with_extra(Json.obj("apiClass" -> "example.Api", "required" -> required), Some(JsString(text)))
            ))))
            val firstjson = _provider_json("org.example", "First", "0.1.0", Vector("example.Api"))
            val secondjson = _provider_json("org.example", "Second", "0.1.0", Vector("example.Api"))
            val first = _car_with_extra(dir.resolve("first.car"), firstjson, Some(JsString(text)))
            val second = _car_with_extra(dir.resolve("second.car"), secondjson, Some(JsString(if (equal) text else text + "different")))
            When("declared provider selection runs")
            val error = intercept[Throwable] {
              ComponentApiDependencyResolver._resolve_dependencies(consumer, Vector(
                ComponentApiDependencyResolver.Dependency("org.example", "First", "0.1.0", first),
                ComponentApiDependencyResolver.Dependency("org.example", "Second", "0.1.0", second)
              ), dir.resolve("resolved"), None)
            }
            Then("historical metadata cannot choose between actual providers")
            error.getMessage should include("provided ambiguously")
            error.getMessage should include("org.example.First:0.1.0")
            error.getMessage should include("org.example.Second:0.1.0")
          }
          true
        }
        Test.check(Test.Parameters.default.withMinSuccessfulTests(40), property).passed shouldBe true
      }
    }
  }

  "Component API extracted output" should {
    "consolidate selected artifacts" which {
      "extract repeated requirements and two API classes sharing a JAR once in selected order" in {
        val property = Prop.forAll(Gen.choose(1, 8)) { count =>
          _with_temp_dir("cozy-api-repeated") { dir =>
            Given("repeated requirements and two classes provided by one CAR API JAR")
            val requirements = Vector.fill(count)(Json.obj("apiClass" -> "example.Beta", "required" -> true)) :+
              Json.obj("apiClass" -> "example.Alpha", "required" -> true)
            val consumer = _write(dir.resolve("consumer.json"), Json.stringify(_consumer_json(requirements)))
            val provider = _car_with_extra(dir.resolve("provider.car"),
              _provider_json("org.example", "Provider", "0.1.0", Vector("example.Alpha", "example.Beta")), None)
            When("all consumer requirements are resolved")
            val jars = ComponentApiDependencyResolver._resolve_dependencies(consumer,
              Vector(ComponentApiDependencyResolver.Dependency("org.example", "Provider", "0.1.0", provider)), dir.resolve("resolved"), None)
            Then("the shared artifact has one final namespace-qualified path and its original bytes")
            jars shouldBe Vector(dir.resolve("resolved/org/example/example-provider/0.1.0/example-provider-api.jar"))
            Files.readString(jars.head) shouldBe "api"
          }
          true
        }
        Test.check(Test.Parameters.default.withMinSuccessfulTests(30), property).passed shouldBe true
      }

      "keep equal filenames from distinct namespace providers separate" in {
        _with_temp_dir("cozy-api-namespace") { dir =>
          Given("two distinct namespace providers selected for distinct API classes")
          val consumer = _write(dir.resolve("consumer.json"), Json.stringify(_consumer_json(Vector(
            Json.obj("apiClass" -> "example.Beta", "required" -> true),
            Json.obj("apiClass" -> "example.Alpha", "required" -> true)
          ))))
          val first = _provider_car(dir.resolve("first.car"), "org.alpha", "Shared", "0.1.0", "example.Alpha")
          val second = _provider_car(dir.resolve("second.car"), "org.beta", "Shared", "0.1.0", "example.Beta")
          When("both declared artifacts are selected")
          val jars = ComponentApiDependencyResolver._resolve_dependencies(consumer, Vector(
            ComponentApiDependencyResolver.Dependency("org.alpha", "Shared", "0.1.0", first),
            ComponentApiDependencyResolver.Dependency("org.beta", "Shared", "0.1.0", second)
          ), dir.resolve("resolved"), None)
          Then("first selected order and namespace isolation preserve both payloads")
          jars shouldBe Vector(
            dir.resolve("resolved/org/beta/beta-shared/0.1.0/beta-shared-api.jar"),
            dir.resolve("resolved/org/alpha/alpha-shared/0.1.0/alpha-shared-api.jar")
          )
          jars.map(Files.readString(_)) shouldBe Vector("api", "api")
        }
      }
    }
  }

  "Component API dependency reader" should {
    "reject legacy or unknown consumer component identity fields before selection" in {
      _with_temp_dir("cozy-api-reader-exact-component") { dir =>
        Vector(
          "{\"name\":\"Consumer\",\"version\":\"0.1.0\"}",
          "{\"namespace\":\"org.example\",\"id\":\"Consumer\",\"version\":\"0.1.0\",\"unknown\":\"x\"}"
        ).zipWithIndex.foreach { case (component, index) =>
          Given("a consumer with legacy or unknown component identity fields")
          val consumer = _write(dir.resolve(s"consumer-$index.json"), s"""{"schemaVersion":"cncf.component-api.v2","component":$component,"provided":[],"required":[]}""")
          When("the raw consumer identity is admitted")
          val error = intercept[Throwable] { ComponentApiDependencyResolver._resolve_dependencies(consumer, Vector.empty, dir.resolve(s"resolved-$index"), None) }
          Then("the exact component contract rejects unknown or legacy fields")
          error.getMessage should include("component.release-coordinate.mismatch")
        }
      }
    }
  }

  "Component API output generation" should {
    "report actual resolution outcomes" which {
      "report admission, selection and extraction failures" in {
        Vector("consumer-malformed", "provider-malformed", "schema", "required-field", "coordinate", "version", "artifact-path",
          "consumer-missing", "archive-missing", "descriptor-missing", "assembly", "required-missing", "ambiguous", "later-entry-missing"
        ).foreach { failure =>
          _with_temp_dir("cozy-api-failure") { dir =>
            Given(s"a requested generated output with $failure input")
            val outputdir = dir.resolve("resolved")
            val basejson = _provider_json("org.example", "Provider", "0.1.0", Vector("example.Api"))
            val provided = (basejson \ "provided").as[Vector[JsObject]].head
            val providerjson = failure match {
              case "schema" => basejson + ("schemaVersion" -> JsString("cncf.component-api.v1"))
              case "required-field" => basejson + ("provided" -> Json.arr(provided - "apiClass"))
              case "coordinate" => basejson + ("component" -> Json.obj("namespace" -> "org.other", "id" -> "Provider", "version" -> "0.1.0"))
              case "version" => basejson + ("provided" -> Json.arr(provided + ("version" -> JsString("0.0.9"))))
              case "artifact-path" => basejson + ("provided" -> Json.arr(provided + ("artifactPath" -> JsString("spi/wrong.jar"))))
              case _ => basejson
            }
            val artifactpath = (provided \ "artifactPath").as[String]
            val entries = failure match {
              case "descriptor-missing" => Map(artifactpath -> "new-api")
              case "provider-malformed" => Map("component-api-descriptor.json" -> "{", artifactpath -> "new-api")
              case _ => Map("component-api-descriptor.json" -> Json.stringify(providerjson), artifactpath -> "new-api")
            }
            val provider = _write_zip(dir.resolve("replacement.car"), entries)
            val consumertext = failure match {
              case "consumer-malformed" => "{"
              case "required-missing" => _consumer_descriptor("example.Missing")
              case "later-entry-missing" => Json.stringify(_consumer_json(Vector(
                Json.obj("apiClass" -> "example.Api", "required" -> true),
                Json.obj("apiClass" -> "example.Later", "required" -> true)
              )))
              case _ => _consumer_descriptor("example.Api")
            }
            val consumer = _write(dir.resolve("replacement.json"), consumertext)
            val archivepath = if (failure == "archive-missing") dir.resolve("absent.car") else provider
            val first = ComponentApiDependencyResolver.Dependency("org.example", "Provider", "0.1.0", archivepath)
            val dependencies = failure match {
              case "ambiguous" => Vector(first, ComponentApiDependencyResolver.Dependency("org.example", "Second", "0.1.0",
                _provider_car(dir.resolve("second.car"), "org.example", "Second", "0.1.0", "example.Api")))
              case "later-entry-missing" =>
                val later = _write_zip(dir.resolve("later.car"), Map("component-api-descriptor.json" ->
                  Json.stringify(_provider_json("org.example", "Later", "0.1.0", Vector("example.Later")))))
                Vector(first, ComponentApiDependencyResolver.Dependency("org.example", "Later", "0.1.0", later))
              case _ => Vector(first)
            }
            val assembly = if (failure == "assembly") Some(_write(dir.resolve("assembly.yaml"), "components: []\n")) else None
            val consumerpath = if (failure == "consumer-missing") dir.resolve("absent.json") else consumer
            When("the request is resolved and its actual CAR entries are extracted")
            val error = intercept[Throwable] {
              ComponentApiDependencyResolver._resolve_dependencies(consumerpath, dependencies, outputdir, assembly)
            }
            Then("the actual admission, selection or extraction error is reported")
            Option(error.getMessage).getOrElse("") should not be empty
            val diagnostic = failure match {
              case "schema" => Some("component.api.schema.unsupported")
              case "required-field" => Some("Invalid component API descriptor")
              case "coordinate" => Some("component.release-coordinate.mismatch")
              case "version" => Some("field=provided.version")
              case "artifact-path" => Some("field=artifactPath")
              case "descriptor-missing" => Some("Dependency CAR has no component-api-descriptor.json")
              case "assembly" => Some("assembly-descriptor.yaml must contain declared CAR dependencies")
              case "required-missing" => Some("Required component API is not provided")
              case "ambiguous" => Some("provided ambiguously")
              case "later-entry-missing" => Some("Dependency CAR API artifact is missing")
              case _ => None
            }
            diagnostic.foreach(code => error.getMessage should include(code))
          }
        }
      }

      "generate selected output and subsequently an empty optional result" in {
        _with_temp_dir("cozy-api-replace") { dir =>
          Given("a previous successful extraction containing obsolete files")
          _seed_output(dir)
          val outputdir = dir.resolve("resolved")
          val consumer = _write(dir.resolve("replacement.json"), _consumer_descriptor("example.Api"))
          val provider = _provider_car(dir.resolve("replacement.car"), "org.example", "Provider", "0.1.0", "example.Api")
          When("a complete new selection succeeds")
          val jars = ComponentApiDependencyResolver._resolve_dependencies(consumer,
            Vector(ComponentApiDependencyResolver.Dependency("org.example", "Provider", "0.1.0", provider)), outputdir, None)
          Then("only the new final paths and payload remain")
          jars shouldBe Vector(outputdir.resolve("org/example/example-provider/0.1.0/example-provider-api.jar"))
          Files.readString(jars.head) shouldBe "api"
          _relative_files(outputdir) shouldBe Vector("org/example/example-provider/0.1.0/example-provider-api.jar")
          Given("the next consumer requests only an unavailable optional API")
          _write(consumer, Json.stringify(_consumer_json(Vector(Json.obj("apiClass" -> "example.Optional", "required" -> false)))))
          When("the empty selection succeeds")
          val empty = ComponentApiDependencyResolver._resolve_dependencies(consumer, Vector.empty, outputdir, None)
          Then("an existing empty output directory replaces obsolete extracted files")
          empty shouldBe Vector.empty
          Files.isDirectory(outputdir) shouldBe true
          _relative_files(outputdir) shouldBe Vector.empty
        }
      }

      "refuse a regular file or supported symbolic link as the output without deleting it" in {
        _with_temp_dir("cozy-api-output-target") { dir =>
          Given("an empty admitted consumer and a regular-file output target")
          val consumer = _write(dir.resolve("consumer.json"), Json.stringify(_consumer_json(Vector.empty)))
          val target = _write(dir.resolve("target.txt"), "retain me")
          When("resolution tries to replace the regular file")
          val fileerror = intercept[Throwable] {
            ComponentApiDependencyResolver._resolve_dependencies(consumer, Vector.empty, target, None)
          }
          Then("the file remains and the output-target diagnostic explains rejection")
          fileerror.getMessage should include("non-symlink directory")
          Files.readString(target) shouldBe "retain me"
          Given("a symbolic-link output target where the filesystem supports it")
          val link = dir.resolve("output-link")
          val supported = try { Files.createSymbolicLink(link, target); true } catch {
            case _: UnsupportedOperationException => false
            case _: java.nio.file.FileSystemException => false
          }
          if (supported) {
            When("resolution tries to replace the symbolic link")
            val linkerror = intercept[Throwable] {
              ComponentApiDependencyResolver._resolve_dependencies(consumer, Vector.empty, link, None)
            }
            Then("both the link and its target remain")
            linkerror.getMessage should include("non-symlink directory")
            Files.isSymbolicLink(link) shouldBe true
            Files.readString(target) shouldBe "retain me"
          }
        }
      }
    }
  }

  private val _historical_values: Vector[Option[JsValue]] = Vector(None, Some(JsString("sha256:old")), Some(JsString("")),
    Some(JsNull), Some(JsNumber(3)), Some(JsBoolean(false)), Some(Json.arr("old", 7)),
    Some(Json.obj("malformed" -> Json.arr(false))), Some(JsString("not an ABI hash")))

  private def _with_extra(value: JsObject, extra: Option[JsValue]): JsObject =
    extra.fold(value)(item => value + ("abiHash" -> item))

  private def _consumer_json(requirements: Vector[JsObject]): JsObject = Json.obj(
    "schemaVersion" -> "cncf.component-api.v2",
    "component" -> Json.obj("namespace" -> "org.example", "id" -> "Consumer", "version" -> "0.1.0"),
    "provided" -> Json.arr(), "required" -> requirements
  )

  private def _provider_json(namespace: String, id: String, version: String, apiclasses: Vector[String]): JsObject = {
    val coordinate = CozyComponentReleaseCoordinateCodec.admit(namespace, id, version, "test-provider")
    Json.obj("schemaVersion" -> "cncf.component-api.v2", "component" -> coordinate.componentJson,
      "provided" -> apiclasses.map(apiclass => Json.obj("apiClass" -> apiclass, "version" -> version, "artifactPath" -> coordinate.apiArtifactPath)),
      "required" -> Json.arr())
  }

  private def _car_with_extra(path: Path, descriptor: JsObject, extra: Option[JsValue]): Path = {
    val provided = (descriptor \ "provided").as[Vector[JsObject]]
    val augmented = _with_extra(descriptor + ("provided" -> Json.toJson(provided.map(_with_extra(_, extra)))), extra)
    _write_zip(path, Map("component-api-descriptor.json" -> Json.stringify(augmented), (provided.head \ "artifactPath").as[String] -> "api"))
  }

  private def _extract_with_extras(consumerextra: Option[JsValue], providerextra: Option[JsValue]): Unit =
    _with_temp_dir("cozy-api-historical-extras") { dir =>
      Given("a sole provider and ordinary historical extras on the required and provided records")
      val consumer = _write(dir.resolve("consumer.json"), Json.stringify(_with_extra(_consumer_json(Vector(
        _with_extra(Json.obj("apiClass" -> "example.Api", "required" -> true), consumerextra)
      )), consumerextra)))
      val provider = _car_with_extra(dir.resolve("provider.car"), _provider_json("org.example", "Provider", "0.1.0", Vector("example.Api")), providerextra)
      When("ordinary descriptor loading selects and extracts the actual CAR API JAR")
      val jars = ComponentApiDependencyResolver._resolve_dependencies(consumer,
        Vector(ComponentApiDependencyResolver.Dependency("org.example", "Provider", "0.1.0", provider)), dir.resolve("resolved"), None)
      Then("required nonhash fields still select the final namespace-qualified payload")
      jars shouldBe Vector(dir.resolve("resolved/org/example/example-provider/0.1.0/example-provider-api.jar"))
      Files.readString(jars.head) shouldBe "api"
    }

  private def _seed_output(dir: Path): Path = {
    val consumer = _write(dir.resolve("prior.json"), _consumer_descriptor("example.Prior"))
    val provider = _provider_car(dir.resolve("prior.car"), "org.example", "Prior", "0.1.0", "example.Prior")
    val jars = ComponentApiDependencyResolver._resolve_dependencies(consumer,
      Vector(ComponentApiDependencyResolver.Dependency("org.example", "Prior", "0.1.0", provider)), dir.resolve("resolved"), None)
    _write(dir.resolve("resolved/obsolete.txt"), "previous extra")
    jars.head
  }

  private def _relative_files(path: Path): Vector[String] = {
    val stream = Files.walk(path)
    try stream.iterator().asScala.filter(Files.isRegularFile(_, LinkOption.NOFOLLOW_LINKS)).map(path.relativize(_).toString).toVector.sorted
    finally stream.close()
  }

  private def _consumer_descriptor(apiclass: String): String =
    s"""{"schemaVersion":"cncf.component-api.v2","component":{"namespace":"org.example","id":"Consumer","version":"0.1.0"},"provided":[],"required":[{"apiClass":"${apiclass}","required":true}]}"""

  private def _provider_car(path: Path, namespace: String, id: String, version: String, apiclass: String): Path =
    _provider_car(path, namespace, id, version, apiclass, version)

  private def _provider_car(path: Path, namespace: String, id: String, version: String, apiclass: String, providedversion: String): Path = {
    val coordinate = CozyComponentReleaseCoordinateCodec.admit(namespace, id, version, "test-provider")
    val descriptor =
      s"""{"schemaVersion":"cncf.component-api.v2","component":{"namespace":"${namespace}","id":"${id}","version":"${version}"},"provided":[{"apiClass":"${apiclass}","version":"${providedversion}","artifactPath":"${coordinate.apiArtifactPath}"}],"required":[]}"""
    _write_zip(path, Map("component-api-descriptor.json" -> descriptor, coordinate.apiArtifactPath -> "api", "component/main.jar" -> "implementation"))
  }

  private def _with_temp_dir[A](prefix: String)(f: Path => A): A = {
    val workroot = Path.of("target/cozy-test/work/component-api-dependency-resolver-spec").toAbsolutePath.normalize()
    Files.createDirectories(workroot)
    val dir = Files.createTempDirectory(workroot, s"$prefix-")
    try f(dir)
    finally {
      val stream = Files.walk(dir)
      try stream.iterator().asScala.toVector.sortBy(_.getNameCount)(Ordering[Int].reverse).foreach(Files.deleteIfExists(_))
      finally stream.close()
    }
  }

  private def _write(path: Path, content: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, content, StandardCharsets.UTF_8)
    path
  }

  private def _write_zip(path: Path, entries: Map[String, String]): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    val output = new ZipOutputStream(Files.newOutputStream(path))
    try entries.toVector.sortBy(_._1).foreach { case (name, content) =>
      output.putNextEntry(new ZipEntry(name))
      output.write(content.getBytes(StandardCharsets.UTF_8))
      output.closeEntry()
    } finally output.close()
    path
  }
}
