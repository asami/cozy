package cozy

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.nio.file.attribute.PosixFilePermission
import java.util.Comparator
import scala.collection.JavaConverters._
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._
import cozy.modeler._
import CmlCapabilitySource._
import CmlCapabilityGenerationContext._
import CmlCapabilityProductionRequest._
import CmlCapabilityValidationScope._
import CmlSemanticFoundation._

/*
 * @since   Oct. 14, 2026
 * @version Oct. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyCapabilityProductionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import CozyCapabilityProductionSpec._
  "Normal Capability production (spec:R13)" should {
    "compose actual selected-source generation" which {
      "C64I01 produce Component, Application and real Scala products through modeler-scala" in {
        _with_root { root =>
          Given("the unchanged primary CML copied to its canonical model path, a closed request and actual runtime descriptor")
          _copy_sources(root)
          val request = _request()
          val args = _normal_args(root, request, "modeler-scala")
          When("the normal Cozy Modeler command reads the selected primary source")
          Cozy.main(args)
          val output = root.resolve("output")
          val index = _json(output.resolve(request.packages.head.path + "/meta/component-capability/index.json"))
          val application = _json(output.resolve(request.applicationoutput))
          val provenance = _json(output.resolve(GenerationProvenance.METADATA_PATH))
          Then("real canonical metadata and selected Scala files exist before provenance records its Scala path inventory")
          (index \ "projections").as[Vector[String]] shouldBe Vector("Store.json")
          (application \ "documents").as[Vector[JsObject]].head.keys shouldBe Set("source", "applicationCapabilities")
          request.scalaoutputs.foreach(x => Files.readString(output.resolve(x.path)) should include("def projectionJson"))
          (provenance \ "output" \ "artifacts").as[Vector[JsObject]].map(x => (x \ "path").as[String]) should contain allElementsOf request.scalaoutputs.map(_.path)
          (provenance \ "source" \ "identity").as[String] shouldBe request.sources.head.path
        }
      }
      "C64I02 retain independent revisions through modeler-scala-value" in {
        _with_root { root =>
          Given("the primary CML and independent header, definition and relationship revisions")
          _copy_sources(root)
          val request = _request()
          When("the normal value-generation command produces the selected product")
          Cozy.main(_normal_args(root, request, "modeler-scala-value"))
          val application = _json(root.resolve("output/" + request.applicationoutput))
          val document = (application \ "documents").as[Vector[JsObject]].head
          val definitions = (document \ "applicationCapabilities").as[Vector[JsObject]]
          Then("the original revisions remain independent semantic facts")
          (document \ "source" \ "sourceRevision").as[String] shouldBe "header:primary-r42"
          definitions.map(x => (x \ "source" \ "sourceRevision").as[String]).distinct shouldBe Vector("definition-r1")
          (definitions(1) \ "demandReferences" \ "value").as[Vector[JsObject]].map(x => (x \ "source" \ "sourceRevision").as[String]) shouldBe Vector("demand-r1", "demand-r2")
        }
      }
      "C64I03 preserve no-flags generation and reject malformed or Library opt-in before output" in {
        _with_root { root =>
          Given("an ordinary real Modeler fixture and distinct private output roots")
          val ordinary = _write(root.resolve("model/ordinary.dox"), Files.readString(Paths.get("src/test/resources/modeler/test.dox")))
          _copy_sources(root)
          val output = root.resolve("legacy")
          val invalid = Vector(Array("--capability-request", "request.json"), Array("--capability-request"),
            Array("--capability-request=a", "--capability-request=b", "--capability-source-root=c"),
            Array("--capability-request= padded", "--capability-source-root=c"),
            Array("--capability-request=a", "--capability-source-root=b", "--generation-target=library",
              "--cozy-generator-version=" + org.simplemodeling.cozy.BuildInfo.version))
          When("ordinary generation runs and each malformed or Library opt-in is attempted")
          Cozy.main(Array("modeler-scala", ordinary.toString, "--save", output.toString))
          val failures = invalid.map(flags => intercept[Throwable] {
            Cozy.main(Array("modeler-scala-value", root.resolve(_request().sources.head.path).toString, "--save", root.resolve("invalid").toString) ++ flags)
          })
          Then("legacy generation succeeds and rejected invocations create no invalid output")
          Files.exists(output.resolve("target/scala-3.3.8/src_managed/main/scala/domain/DomainComponent.scala")) shouldBe true
          failures.foreach(x => Option(x.getMessage) should not be empty)
          Files.exists(root.resolve("invalid")) shouldBe false
        }
      }
    }
    "retain strict request and finite fact authority" which {
      "C64I04 reject malformed closed request JSON without output" in {
        _with_root { root =>
          Given("a complete request and duplicate, missing, unknown, trailing, version and tag variants")
          val json = _request_json(_request())
          val text = Json.stringify(json)
          val variants = Vector(text.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1"),
            Json.stringify(json - "scope"), Json.stringify(json + ("unknown" -> JsNull)), text + " {}",
            Json.stringify(json + ("schemaVersion" -> JsBoolean(true))), Json.stringify(json + ("schemaVersion" -> JsNumber(BigDecimal("1.5")))),
            text.replace("\"schemaVersion\":1", "\"schemaVersion\":1.0"),
            Json.stringify(json + ("schemaVersion" -> JsNumber(2))), text.replace("USE_CASE", "UNKNOWN_KIND"),
            Json.stringify(json + ("producer" -> JsNull)))
          When("each actual strict request decoder receives the invalid representation")
          val results = variants.map(CmlCapabilityProductionRequestJsonPart.decode(_, root.resolve("request.json")))
          Then("each fails at the actual request location with no fabricated successful request or files")
          results.foreach { result => result.isLeft shouldBe true; result.left.get.foreach(_.fieldpath should startWith(root.resolve("request.json").toString)) }
          Files.exists(root.resolve("output")) shouldBe false
        }
      }
      "C64I05 reject producer disagreement even for empty and app-only sources" in {
        _with_root { root =>
          Given("actual empty and app-only local sources and requests with disagreeing producers")
          _copy_sources(root)
          val empty = _write(root.resolve("model/empty.cml"), "# CAPABILITY-MODEL\ncompatibility = {\"state\":\"PRESENT\",\"value\":[]}\n")
          val apponly = _write(root.resolve(_secondary_source.path), _secondary_text.split("## COMPONENT-CAPABILITY")(0))
          val requests = Vector(_empty_request("model/empty.cml", "Empty"), _request(multiple = true).copy(sources = Vector(_secondary_source), contexts = Vector.empty,
            packages = Vector.empty, scalaoutputs = Vector.empty))
          When("production prepares each actual source with a false id or version")
          val results = requests.flatMap(x => Vector(x.copy(producer = Producer("other", x.producer.version)), x.copy(producer = Producer("cozy", "false:version"))))
            .map(CmlCapabilityProduction.prepare(_, root, root.resolve("output"), None))
          Then("all producer mismatches reject before publication while the authored requests use the actual build version")
          results.foreach(_ shouldBe Left(CmlCapabilityProduction.RequestOrAcquisition(Vector(Issue(IssueKind.ProducerMismatch, "producer", None, Vector.empty,
            "Producer must equal cozy and the actual executing BuildInfo.version, including app-only input.")))))
          _request().producer.version shouldBe org.simplemodeling.cozy.BuildInfo.version
          Files.exists(empty) shouldBe true
          Files.exists(apponly) shouldBe true
        }
      }
      "C64I06 decode every independent finite scope domain as supplied" in {
        Given("all thirteen model kind tags and independently sourced vocabulary and contract facts")
        val request = _request()
        val base = request.scope.asInstanceOf[ScopeInput.Ready].scope
        val kinds = Vector(ModelKind.DomainConcept, ModelKind.Goal, ModelKind.UseCase, ModelKind.Scenario, ModelKind.UseCaseSlice,
          ModelKind.Component, ModelKind.ComponentCapability, ModelKind.ApplicationCapability, ModelKind.Specification, ModelKind.Evidence,
          ModelKind.Operation, ModelKind.Workflow, ModelKind.StateMachine)
        val scope = base.copy(modelfacts = kinds.map(x => ModelFact(ModelElementId("Fact:" + x.tag, "element:" + x.tag), x,
          CapabilitySource("Fact:" + x.tag, "facts/all.cml", "fact:" + x.tag, Some(4)))),
          applicationcontracts = Vector(ApplicationContract(ModelElementId("App:history", "a"), "app:old", CapabilitySource("App:history", "facts/app.cml", "app:fact", None))))
        val variant = request.copy(scope = ScopeInput.Ready(scope))
        When("the closed production request is decoded")
        val decoded = _right(CmlCapabilityProductionRequestJsonPart.decode(Json.stringify(_request_json(variant)), Paths.get("selected-request.json")))
        Then("every ordered fact and source domain survives with no acquisition or inferred alias")
        decoded shouldBe variant
        decoded.scope shouldBe ScopeInput.Ready(scope)
        scope.targetcontracts.map(_.generatedabiversion) shouldBe Vector("operation.abi:v7", "workflow.abi:v8", "state.abi:v9")
      }
    }
    "acquire only exact selected original sources" which {
      "C64I07 reject selected path, identity, source count and root mismatch" in {
        _with_root { root =>
          Given("the actual primary source input and deliberately mismatched explicit selections")
          _copy_sources(root)
          val request = _request()
          val sourceinput = GenerationProvenance.requireSourceInput(root.resolve(request.sources.head.path), request.sources.head.path)
          val variants = Vector((request, root, sourceinput.copy(identity = "model/other.cml")),
            (request, root, sourceinput.copy(path = root.resolve(_secondary_source.path))),
            (request, root.resolve("other"), sourceinput), (request.copy(sources = request.sources ++ request.sources), root, sourceinput),
            (_request(multiple = true), root, sourceinput),
            (request, root, sourceinput.copy(path = null)), (request, root, null))
          When("typed production requires correspondence to each selected source and normal CLI tries multiple selected sources")
          val results = variants.map { case (selected, sourceroot, sourceinput) => CmlCapabilityProduction.prepare(selected, sourceroot, root.resolve("output"), Some(sourceinput)) }
          val error = intercept[Throwable](Cozy.main(_normal_args(root, _request(multiple = true), "modeler-scala")))
          Then("every mismatch fails before ordinary or Capability output is created")
          results.foreach(_.isLeft shouldBe true)
          error.getMessage should include("Selected primary source")
          Files.exists(root.resolve("output")) shouldBe false

          Given("an outside positional source, a mismatched generation identity and existing ordinary output")
          val outside = _write(root.resolve("outside.cml"), _primary_text)
          val sentinel = _write(root.resolve("rejected/target/ordinary.scala"), "ordinary sentinel")
          val outsideargs = _normal_args(root, request, "modeler-scala", "rejected")
          outsideargs(1) = outside.toString
          val identityargs = _normal_args(root, request, "modeler-scala-value", "rejected")
          identityargs(identityargs.indexOf("--generation-source-identity") + 1) = _secondary_source.path
          When("the normal CLI receives each positional or semantic source correspondence mismatch")
          val correspondenceerrors = Vector(outsideargs, identityargs).map(args => intercept[Throwable](Cozy.main(args)))
          Then("the typed correspondence guard rejects before ordinary output mutation")
          correspondenceerrors.foreach { failure =>
            failure.getMessage should include("Malformed location=sources")
            failure.getMessage should include("exact selected identity and physical path before acquisition")
          }
          Files.readString(sentinel) shouldBe "ordinary sentinel"
          Files.exists(root.resolve("rejected/" + request.applicationoutput)) shouldBe false
          request.packages.foreach(x => Files.exists(root.resolve("rejected/" + x.path)) shouldBe false)
        }
      }
      "C64I08 reject missing, nonregular, unreadable and symlink source selections" in {
        _with_root { root =>
          Given("private missing, directory, unreadable and root/parent/leaf symlink inputs")
          _copy_sources(root)
          val source = root.resolve(_primary_source.path)
          val missing = root.resolve("missing")
          val directory = root.resolve("directory")
          Files.createDirectories(directory.resolve(_primary_source.path))
          val linkedroot = root.resolve("linked-root")
          Files.createSymbolicLink(linkedroot, root)
          val linkedparent = root.resolve("parent")
          Files.createDirectories(linkedparent)
          Files.createSymbolicLink(linkedparent.resolve("model"), root.resolve("model"))
          val linkedleaf = root.resolve("leaf")
          Files.createDirectories(linkedleaf.resolve("model"))
          Files.createSymbolicLink(linkedleaf.resolve(_primary_source.path), source)
          val permissions = Files.getPosixFilePermissions(source)
          When("production acquires these filesystem kinds and an unreadable selected leaf")
          val kinds = Vector(missing, directory, linkedroot, linkedparent, linkedleaf).map(x => CmlCapabilityProduction.prepare(_request(), x, root.resolve("output"), None))
          Files.setPosixFilePermissions(source, Set.empty[PosixFilePermission].asJava)
          val unreadable = try CmlCapabilityProduction.prepare(_request(), root, root.resolve("output"), None) finally Files.setPosixFilePermissions(source, permissions)
          Then("all invalid source kinds fail without reading outside the selected root")
          kinds.foreach(_.isLeft shouldBe true)
          unreadable.isLeft shouldBe true
          Files.exists(root.resolve("output")) shouldBe false

          Given("the same root, parent and leaf symlink selections with existing normal output")
          val request = _request()
          val sentinel = _write(root.resolve("rejected/target/ordinary.scala"), "ordinary sentinel")
          val linkedargs = Vector(linkedroot, linkedparent, linkedleaf).map { selectedroot =>
            val args = _normal_args(root, request, "modeler-scala", "rejected")
            args(1) = selectedroot.resolve(_primary_source.path).toString
            args(args.indexOf("--capability-source-root") + 1) = selectedroot.toString
            args
          }
          When("the real normal CLI receives each symlink source route")
          val linkederrors = linkedargs.map(args => intercept[Throwable](Cozy.main(args)))
          Then("the typed no-symlink guard rejects every route before ordinary output mutation")
          linkederrors.foreach { failure =>
            failure.getMessage should include("Malformed")
            failure.getMessage should include("without symlinks")
          }
          Files.readString(sentinel) shouldBe "ordinary sentinel"
          Files.exists(root.resolve("rejected/" + request.applicationoutput)) shouldBe false
          request.packages.foreach(x => Files.exists(root.resolve("rejected/" + x.path)) shouldBe false)
        }
      }
      "C64I09 reject malformed UTF8, canonical path, extension and acquisition bounds" in {
        _with_root { root =>
          Given("invalid source bytes, paths, extensions and explicit request/source size/count limits")
          _copy_sources(root)
          val request = _request()
          val malformed = Files.write(root.resolve(request.sources.head.path), Array(0xc3.toByte, 0x28.toByte))
          val variants = Vector(request, request.copy(sources = Vector(request.sources.head.copy(path = "../escape.cml"))),
            request.copy(sources = Vector(request.sources.head.copy(path = "model//a.cml"))),
            request.copy(sources = Vector(request.sources.head.copy(path = "model/a.txt"))), request.copy(sources = Vector.fill(257)(request.sources.head)))
          val large = _write(root.resolve("large-request.json"), " " * (REQUEST_LIMIT + 1))
          When("actual production acquisition and request loading enforce their selected boundaries")
          val results = variants.map(CmlCapabilityProduction.prepare(_, root, root.resolve("output"), None))
          val requestfailure = CmlCapabilityProduction.readRequest(large)
          Files.write(malformed, new Array[Byte](SOURCE_LIMIT + 1))
          val sourcefailure = CmlCapabilityProduction.prepare(request, root, root.resolve("output"), None)
          val totalsources = (0 until 5).map { index =>
            val path = s"model/large$index.cml"
            Files.write(root.resolve(path), new Array[Byte](SOURCE_LIMIT))
            CapabilitySource("Large:" + index, path, "large:" + index, None)
          }.toVector
          val totalfailure = CmlCapabilityProduction.prepare(request.copy(sources = totalsources), root, root.resolve("output"), None)
          Then("all bounded acquisition failures remain honest typed failures without success or output")
          (results :+ requestfailure :+ sourcefailure :+ totalfailure).foreach(_.isLeft shouldBe true)
          Files.exists(root.resolve("output")) shouldBe false

          Given("the same 16MiB plus one byte positional source and an existing ordinary output")
          val sentinel = _write(root.resolve("rejected/target/ordinary.scala"), "ordinary sentinel")
          val args = _normal_args(root, request, "modeler-scala", "rejected")
          When("the real normal CLI selects the oversized original positional source")
          val error = intercept[Throwable](Cozy.main(args))
          Then("the typed size guard rejects before acquisition or ordinary output mutation")
          error.getMessage should include("Malformed location=sources")
          error.getMessage should include("exceeds 16MiB before acquisition")
          Files.readString(sentinel) shouldBe "ordinary sentinel"
          Files.exists(root.resolve("rejected/" + request.applicationoutput)) shouldBe false
          request.packages.foreach(x => Files.exists(root.resolve("rejected/" + x.path)) shouldBe false)
        }
      }
    }
    "retain core failures and complete selections" which {
      "C64I10 preserve actual syntax and semantic diagnostic vectors" in {
        _with_root { root =>
          Given("real parsed CML with invalid syntax and separately supplied dangling, cyclic, version and ABI contradictions")
          _copy_sources(root)
          val request = _request()
          val path = root.resolve(_primary_source.path)
          val original = Files.readString(path)
          val texts = Vector(original.replace("contractVersion =", "unknownVersion ="), original.replace("usecase:one", "missing:usecase"),
            original.replace("Application:Independent", "Application:One"), original.replace("operation:v3", "operation:bad"), original.replace("operation.abi:v7", "operation.abi:bad"),
            original.replace("\"identity\":{\"modelId\":\"Other:能力/B\",\"elementId\":\"Application:Remote\"},\"contractVersion\":\"app:v1\"},\"boundary\":\"EXTERNAL\"",
              "\"identity\":{\"modelId\":\"Model:能力/A\",\"elementId\":\"Application:One\"},\"contractVersion\":\"app:v1\"},\"boundary\":\"LOCAL\""))
          When("actual local parsing and core generation evaluate each source")
          val outcomes = texts.map { text =>
            _write(path, text)
            val direct = CmlCapabilityGeneration.generate(Vector(SourceInput(SourceScope(_primary_source.modelAuthority, _primary_source.path),
              org.goldenport.kaleidox.Model.parse(org.goldenport.kaleidox.Config.default, text))), request.scope, request.contexts)
            val production = CmlCapabilityProduction.prepare(request, root, root.resolve("output"), None)
            direct -> production
          }
          Then("production wraps exactly the original syntax or semantic failure and publishes nothing")
          outcomes.foreach { case (direct, production) => direct.isLeft shouldBe true; production shouldBe direct.left.map(CmlCapabilityProduction.Core) }
          Files.exists(root.resolve("output")) shouldBe false
        }
      }
      "C64I11 preserve typed Rejected scope diagnostics in original order" in {
        _with_root { root =>
          Given("original actual CML and ordered unavailable and unauthorized acquisition diagnostics")
          _copy_sources(root)
          val diagnostics = Vector(CmlCapabilityValidationScope.Diagnostic(DiagnosticKind.Unavailable, "facts", Some(SourceScope(_primary_source.modelAuthority, _primary_source.path)),
            Some(_primary_source), Vector(_secondary_source), "Unavailable facts."),
            CmlCapabilityValidationScope.Diagnostic(DiagnosticKind.Unauthorized, "targets", None, Some(_secondary_source), Vector.empty, "Unauthorized target facts."))
          When("production receives the explicitly rejected scope")
          val result = CmlCapabilityProduction.prepare(_request().copy(scope = ScopeInput.Rejected(diagnostics)), root, root.resolve("output"), None)
          Then("the original source-correlated diagnostic values remain unchanged")
          result shouldBe Left(CmlCapabilityProduction.Core(SemanticFailure(diagnostics)))
          Files.exists(root.resolve("output")) shouldBe false
        }
      }
      "C64I12 reject incomplete or conflicting package and Scala snapshot coverage" in {
        _with_root { root =>
          Given("actual source input and unknown, missing, repeated or domain-disagreeing selections")
          _copy_sources(root)
          val request = _request()
          val bundle = request.packages.head
          val variants = Vector(request.copy(packages = request.packages.tail), request.copy(scalaoutputs = request.scalaoutputs.tail),
            request.copy(packages = request.packages :+ bundle), request.copy(scalaoutputs = request.scalaoutputs ++ request.scalaoutputs),
            request.copy(packages = request.packages.updated(0, bundle.copy(packagecoordinate = bundle.packagecoordinate.copy(version = "package:other")))),
            request.copy(packages = request.packages.updated(0, bundle.copy(resources = Vector(bundle.resources.head.copy(key = bundle.resources.head.key.copy(component = _component("Unknown"))))))))
          When("the complete preparation gate evaluates each explicit selection")
          val results = variants.map(CmlCapabilityProduction.prepare(_, root, root.resolve("output"), None))
          Then("every incomplete or ambiguous batch fails before publication")
          results.foreach(_.isLeft shouldBe true)
          Files.exists(root.resolve("output")) shouldBe false

          Given("normal Application selections at, case-aliasing and beneath the reserved provenance destination")
          val sentinel = _write(root.resolve("rejected/target/ordinary.scala"), "ordinary sentinel")
          val provenance = _write(root.resolve("rejected/" + GenerationProvenance.METADATA_PATH), "provenance sentinel")
          val reservedpaths = Vector(GenerationProvenance.METADATA_PATH, "target/cozy/Generation-Provenance.json",
            GenerationProvenance.METADATA_PATH + "/application.json")
          reservedpaths.foreach { path =>
            Given("the explicit Application selection " + path)
            val args = _normal_args(root, request.copy(applicationoutput = path), "modeler-scala", "rejected")
            When("the real normal CLI receives this reserved Application selection")
            val failure = intercept[Throwable](Cozy.main(args))
            Then("the reserved normal provenance conflict rejects without replacing existing output or creating selected resources")
            failure.getMessage should include("OutputConflict location=applicationOutput")
            failure.getMessage should include("reserved normal provenance destination " + GenerationProvenance.METADATA_PATH)
            Files.readString(sentinel) shouldBe "ordinary sentinel"
            Files.readString(provenance) shouldBe "provenance sentinel"
            request.packages.foreach(x => Files.exists(root.resolve("rejected/" + x.path + "/" + METADATA_PREFIX + "index.json")) shouldBe false)
            request.scalaoutputs.foreach(x => Files.exists(root.resolve("rejected/" + x.path)) shouldBe false)
          }

          Given("an otherwise valid normal request and initially absent Modeler metadata destinations")
          val metadata = "target/cozy/model-metadata.json"
          val metadatapaths = Vector(metadata, "target/cozy/Model-Metadata.json", metadata + "/application.json")
          metadatapaths.foreach { path =>
            Given("the explicit Application selection " + path + " with ordinary and provenance sentinels intact")
            Files.exists(root.resolve("rejected/" + metadata)) shouldBe false
            Files.exists(root.resolve("rejected/target/cozy/model-metadata.yaml")) shouldBe false
            Files.exists(root.resolve("rejected/" + path)) shouldBe false
            val args = _normal_args(root, request.copy(applicationoutput = path), "modeler-scala", "rejected")
            When("the real normal CLI receives this exact, case-aliased or descendant Modeler metadata selection")
            val failure = intercept[Throwable](Cozy.main(args))
            Then("the Application OutputConflict names the reserved Modeler metadata path before any output mutation")
            failure.getMessage should include("OutputConflict location=applicationOutput")
            failure.getMessage should include("reserved normal Modeler metadata destination " + metadata)
            Files.readString(sentinel) shouldBe "ordinary sentinel"
            Files.readString(provenance) shouldBe "provenance sentinel"
            Files.exists(root.resolve("rejected/" + metadata)) shouldBe false
            Files.exists(root.resolve("rejected/target/cozy/model-metadata.yaml")) shouldBe false
            Files.exists(root.resolve("rejected/" + path)) shouldBe false
            Files.exists(root.resolve("rejected/" + request.applicationoutput)) shouldBe false
            request.packages.foreach { bundle =>
              Files.exists(root.resolve("rejected/" + bundle.path + "/" + METADATA_PREFIX + "index.json")) shouldBe false
              bundle.resources.foreach(x => Files.exists(root.resolve("rejected/" + bundle.path + "/" + METADATA_PREFIX + x.filename)) shouldBe false)
            }
            request.scalaoutputs.foreach(x => Files.exists(root.resolve("rejected/" + x.path)) shouldBe false)
          }
        }
      }
      "C64I13 leave output untouched during preparation and rejected normal production" in {
        _with_root { root =>
          Given("valid actual source and an ordinary existing Scala sentinel")
          _copy_sources(root)
          val request = _request()
          val sentinel = _write(root.resolve("output/target/ordinary.scala"), "ordinary sentinel")
          When("a valid batch prepares and a missing-coverage request reaches the normal CLI")
          val prepared = CmlCapabilityProduction.prepare(request, root, root.resolve("output"), None)
          val error = intercept[Throwable](Cozy.main(_normal_args(root, request.copy(packages = Vector.empty), "modeler-scala")))
          Then("preparation succeeds without writes and rejection precedes ordinary output cleanup")
          prepared.isRight shouldBe true
          error.getMessage should include("MissingCoverage")
          Files.readString(sentinel) shouldBe "ordinary sentinel"
          Files.exists(root.resolve("output/" + request.applicationoutput)) shouldBe false

          Given("existing ordinary and provenance sentinels with bundle ancestors and a Scala descendant of the reserved destination")
          val provenance = _write(root.resolve("output/" + GenerationProvenance.METADATA_PATH), "provenance sentinel")
          val reserved = Vector(
            request.copy(packages = request.packages.updated(0, request.packages.head.copy(path = "target/cozy"))) -> "packages[0].path",
            request.copy(packages = request.packages.updated(0, request.packages.head.copy(path = "target"))) -> "packages[0].path",
            request.copy(scalaoutputs = request.scalaoutputs.updated(0, request.scalaoutputs.head.copy(
              path = GenerationProvenance.METADATA_PATH + "/metadata.scala"))) -> "scalaOutputs[0].path")
          reserved.foreach { case (selected, field) =>
            Given("the explicit reserved normal output selection at " + field)
            val args = _normal_args(root, selected, "modeler-scala-value")
            When("the real normal CLI receives this directory ancestor or Scala descendant")
            val failure = intercept[Throwable](Cozy.main(args))
            Then("the field-specific reserved destination diagnostic precedes all ordinary cleanup and Capability writes")
            failure.getMessage should include("OutputConflict location=" + field)
            failure.getMessage should include("reserved normal provenance destination " + GenerationProvenance.METADATA_PATH)
            Files.readString(sentinel) shouldBe "ordinary sentinel"
            Files.readString(provenance) shouldBe "provenance sentinel"
            Files.exists(root.resolve("output/" + request.applicationoutput)) shouldBe false
            selected.packages.foreach(x => Files.exists(root.resolve("output/" + x.path + "/" + METADATA_PREFIX + "index.json")) shouldBe false)
            selected.scalaoutputs.foreach(x => Files.exists(root.resolve("output/" + x.path)) shouldBe false)
          }

          Given("initially absent JSON and YAML Modeler metadata destinations with ordinary and provenance sentinels intact")
          val metadatapaths = Vector("target/cozy/model-metadata.json", "target/cozy/model-metadata.yaml")
          metadatapaths.foreach { metadata =>
            val variants = Vector(
              request.copy(packages = request.packages.updated(0, request.packages.head.copy(path = metadata))) -> "packages[0].path",
              request.copy(packages = request.packages.updated(0, request.packages.head.copy(path = metadata + "/bundle"))) -> "packages[0].path",
              request.copy(scalaoutputs = request.scalaoutputs.updated(0, request.scalaoutputs.head.copy(
                path = metadata + "/metadata.scala"))) -> "scalaOutputs[0].path")
            variants.foreach { case (selected, field) =>
              Given("the explicit normal " + field + " selection at or beneath " + metadata)
              metadatapaths.foreach(x => Files.exists(root.resolve("output/" + x)) shouldBe false)
              val args = _normal_args(root, selected, "modeler-scala-value")
              When("the real normal CLI receives this Modeler metadata bundle or Scala selection")
              val failure = intercept[Throwable](Cozy.main(args))
              Then("the field-specific OutputConflict names the reserved metadata file before any cleanup or product writes")
              failure.getMessage should include("OutputConflict location=" + field)
              failure.getMessage should include("reserved normal Modeler metadata destination " + metadata)
              Files.readString(sentinel) shouldBe "ordinary sentinel"
              Files.readString(provenance) shouldBe "provenance sentinel"
              metadatapaths.foreach(x => Files.exists(root.resolve("output/" + x)) shouldBe false)
              Files.exists(root.resolve("output/" + request.applicationoutput)) shouldBe false
              selected.packages.foreach { bundle =>
                Files.exists(root.resolve("output/" + bundle.path + "/" + METADATA_PREFIX + "index.json")) shouldBe false
                bundle.resources.foreach(x => Files.exists(root.resolve("output/" + bundle.path + "/" + METADATA_PREFIX + x.filename)) shouldBe false)
              }
              selected.scalaoutputs.foreach(x => Files.exists(root.resolve("output/" + x.path)) shouldBe false)
            }
          }
        }
      }
      "C64I14 retain explicitly acquired multi-source document order" in {
        _with_root { root =>
          Given("the two unchanged original CML files selected in reversed order under one source root")
          _copy_sources(root)
          val request = _request(multiple = true).copy(sources = Vector(_secondary_source, _primary_source))
          When("typed production prepares and publishes the explicitly selected original sources")
          val prepared = _right(CmlCapabilityProduction.prepare(request, root, root.resolve("output"), None))
          val paths = _right(CmlCapabilityProduction.publish(root.resolve("output"), prepared))
          Then("actual original document order and headers survive without a fabricated V1 provenance file")
          prepared.generated.validated.documents.map(_.scope) shouldBe request.sources.map(x => SourceScope(x.modelAuthority, x.path))
          (prepared.batch.application \ "documents").as[Vector[JsObject]].map(x => (x \ "source" \ "path").as[String]) shouldBe request.sources.map(_.path)
          paths should contain(root.resolve("output/" + request.applicationoutput))
          Files.exists(root.resolve("output/" + GenerationProvenance.METADATA_PATH)) shouldBe false
        }
      }
      "C64I15 isolate independent normal invocations and include their actual Scala provenance" in {
        _with_root { root =>
          Given("two independent requests with separate normal invocation output roots")
          _copy_sources(root)
          val first = _request()
          val second = first.copy(applicationoutput = "target/products/second.json", scalaoutputs = first.scalaoutputs.map(x =>
            x.copy(path = x.path.replace("generated/", "generated2/"), objectname = x.objectname + "Second")))
          When("two real normal commands run sequentially with independently selected requests")
          Cozy.main(_normal_args(root, first, "modeler-scala", "first"))
          Cozy.main(_normal_args(root, second, "modeler-scala", "second"))
          val provenance = _json(root.resolve("second/" + GenerationProvenance.METADATA_PATH))
          Then("the second request has its own products and original provenance vocabulary without leaked selections")
          Files.exists(root.resolve("second/" + first.applicationoutput)) shouldBe false
          Files.exists(root.resolve("second/" + second.applicationoutput)) shouldBe true
          (provenance \ "output" \ "artifacts").as[Vector[JsObject]].map(x => (x \ "path").as[String]) should contain allElementsOf second.scalaoutputs.map(_.path)
          (provenance \ "output" \ "artifacts").as[Vector[JsObject]].map(x => (x \ "path").as[String]) should not contain first.scalaoutputs.head.path
        }
      }
      "C64I16 preserve independent literal request values in one hundred active samples" in {
        Given("unfiltered literal authorities, identities, source and contract revisions with Unicode and delimiters")
        val literals = Gen.listOf(Gen.oneOf("日本語", "β", "|:=", "A", "🙂")).map(x => "literal:" + x.mkString + ":end")
        val property = Prop.forAll(Gen.zip(literals, literals, Gen.choose(1, 9999))) { case (authority, revision, line) =>
          val original = _request()
          val source = original.sources.head.copy(modelAuthority = authority, sourceRevision = revision, line = Some(line))
          val scope = original.scope.asInstanceOf[ScopeInput.Ready].scope.copy(scopeid = "scope:" + revision,
            modelfacts = Vector(ModelFact(ModelElementId(authority, "element:" + revision), ModelKind.Goal, source)))
          val request = original.copy(sources = Vector(source), scope = ScopeInput.Ready(scope),
            contexts = original.contexts.map(x => x.copy(source = source, packagecoordinate = x.packagecoordinate.copy(version = revision))))
          CmlCapabilityProductionRequestJsonPart.decode(Json.stringify(_request_json(request)), Paths.get("literal-request.json")) == Right(request)
        }
        When("ScalaCheck evaluates the actual closed request decoder without filtered or discarded samples")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
        Then("at least one hundred successful samples preserve every independently supplied value")
        result.passed shouldBe true
        result.succeeded should be >= 100
        result.discarded shouldBe 0
      }
    }
  }
}

private[cozy] object CozyCapabilityProductionSpec {
  private[cozy] def _right[A, B](value: Either[A, B]): B = value.fold(x => org.scalatest.Assertions.fail(x.toString), identity)
  private[cozy] def _with_root[A](body: Path => A): A = {
    val parent = Paths.get("target/cozy-test/work/capability-production").toAbsolutePath.normalize()
    Files.createDirectories(parent)
    val root = Files.createTempDirectory(parent, "selected-")
    try body(root) finally {
      val stream = Files.walk(root)
      try stream.sorted(Comparator.reverseOrder()).iterator().asScala.foreach(Files.deleteIfExists(_)) finally stream.close()
    }
  }
  private[cozy] def _write(path: Path, text: String): Path = {
    Files.createDirectories(path.getParent)
    Files.write(path, text.getBytes(StandardCharsets.UTF_8))
    path
  }
  private[cozy] def _json(path: Path): JsObject = Json.parse(Files.readAllBytes(path)).as[JsObject]
  private[cozy] def _primary_source: CapabilitySource = CapabilitySource("Model:能力/A", "model/capability-model.cml", "header:primary-r42", None)
  private[cozy] def _secondary_source: CapabilitySource = CapabilitySource("Other:能力/B", "model/capability-model-secondary.cml", "header:secondary-r43", Some(2))
  private[cozy] def _primary_text: String = Files.readString(Paths.get("src/test/resources/modeler/capability-model.cml"), StandardCharsets.UTF_8)
  private[cozy] def _secondary_text: String = Files.readString(Paths.get("src/test/resources/modeler/capability-model-secondary.cml"), StandardCharsets.UTF_8)
  private[cozy] def _copy_sources(root: Path): Unit = {
    _write(root.resolve(_primary_source.path), _primary_text)
    _write(root.resolve(_secondary_source.path), _secondary_text)
    ()
  }
  private[cozy] def _component(id: String): org.goldenport.cncf.component.identity.ComponentId =
    cozy.archive.CozyComponentReleaseCoordinateCodec.admitIdentity("org.simplemodeling.textus", id, "spec:R13").value
  private[cozy] def _fact_source(revision: String): CapabilitySource = _primary_source.copy(sourceRevision = revision)
  private[cozy] def _scope: Scope = {
    val authority = _primary_source.modelAuthority
    val models = Vector("usecase:one" -> ModelKind.UseCase, "goal:two" -> ModelKind.Goal,
      "scenario:one" -> ModelKind.Scenario, "concept:Order" -> ModelKind.DomainConcept).map { case (element, kind) =>
      ModelFact(ModelElementId(authority, element), kind, _fact_source("fact:" + element)) }
    val term = TermFact(TermId("BoK:語彙", "term:注文|α"), CapabilitySource("BoK:語彙", "catalog/terms.cml", "term:r8", None))
    val relation = RelationFact(RelationId("vocab:关系", "rel:uses|α"), CapabilitySource("vocab:关系", "catalog/relations.cml", "relation:r3", None))
    val profile = ProfileFact(ProfileId("profile:scope", "profile:one"), CapabilitySource("profile:scope", "catalog/profiles.cml", "profile:r7", None))
    val history = Vector("contract:一", "contract:三").map(version => ComponentContract(ModelElementId(authority, "Component:Store|α"), version, _fact_source("history:" + version)))
    val targets = Vector((CmlCapabilityModel.Operation, "operation:save", "operation:v3", "operation.abi:v7"),
      (CmlCapabilityModel.Workflow, "workflow:save", "workflow:v4", "workflow.abi:v8"),
      (CmlCapabilityModel.StateMachine, "state:store", "state:v5", "state.abi:v9")).map { case (kind, element, version, abi) =>
      TargetContract(kind, ModelElementId(authority, element), _component("Store"), version, abi, _fact_source("target:" + element)) }
    Scope("explicit-production-fixture", _fact_source("scope:r1"), models, Vector(term), Vector(relation), Vector(profile), Vector.empty, history, targets)
  }
  private[cozy] def _request(multiple: Boolean = false): ProductionRequest = {
    val producer = Producer("cozy", org.simplemodeling.cozy.BuildInfo.version)
    val sources = if (multiple) Vector(_primary_source, _secondary_source) else Vector(_primary_source)
    val domains = Vector(_primary_source -> "Store", _primary_source -> "Archive") ++ (if (multiple) Vector(_secondary_source -> "Remote") else Vector.empty)
    val contexts = domains.map { case (source, id) => GenerationContext(1, 1, "cncf.component-capability.v1", producer,
      ComponentCoordinate(_component(id), "1.2.3-SNAPSHOT"), PackageCoordinate("pkg:" + id + "|日本語", "package:r9"), source) }
    val packages = contexts.map(x => PackageOutput(x.component, x.packagecoordinate, "target/bundles/" + x.component.component.localId().value(),
      Vector(ResourceOutput(x.snapshotKey, x.component.component.localId().value() + ".json"))))
    val scalaoutputs = contexts.map(x => ScalaOutput(x.snapshotKey, "target/generated/" + x.component.component.localId().value() + ".scala",
      Vector("generated", "capability"), x.component.component.localId().value() + "Metadata"))
    ProductionRequest(producer, sources, ScopeInput.Ready(_scope), contexts, packages, scalaoutputs, "target/products/application.json")
  }
  private[cozy] def _empty_request(path: String, authority: String): ProductionRequest = _request().copy(
    sources = Vector(CapabilitySource(authority, path, "empty:header", None)), contexts = Vector.empty,
    packages = Vector.empty, scalaoutputs = Vector.empty)
  private[cozy] def _prepare(root: Path, request: ProductionRequest): CmlCapabilityProduction.Prepared =
    _right(CmlCapabilityProduction.prepare(request, root, root.resolve("output"), None))
  private[cozy] def _source_json(source: CapabilitySource): JsObject = Json.obj("modelAuthority" -> source.modelAuthority,
    "path" -> source.path, "sourceRevision" -> source.sourceRevision, "line" -> source.line.map[JsValue](x => JsNumber(x)).getOrElse[JsValue](JsNull))
  private[cozy] def _id_json(identity: ModelElementId): JsObject = Json.obj("modelId" -> identity.modelId, "elementId" -> identity.elementId)
  private[cozy] def _component_json(component: org.goldenport.cncf.component.identity.ComponentId): JsObject =
    Json.obj("namespace" -> component.namespace().value(), "id" -> component.localId().value())
  private[cozy] def _coordinate_json(value: ComponentCoordinate): JsObject = _component_json(value.component) + ("version" -> JsString(value.version))
  private[cozy] def _package_json(value: PackageCoordinate): JsObject = Json.obj("coordinate" -> value.coordinate, "version" -> value.version)
  private[cozy] def _snapshot_json(key: SnapshotKey): JsObject = Json.obj("source" -> Json.obj("modelAuthority" -> key.scope.modelAuthority,
    "path" -> key.scope.path), "component" -> _component_json(key.component))
  private[cozy] def _request_json(request: ProductionRequest): JsObject = {
    val scope = request.scope.asInstanceOf[ScopeInput.Ready].scope
    val producer = Json.obj("id" -> request.producer.id, "version" -> request.producer.version)
    val scopejson = Json.obj("scopeId" -> scope.scopeid, "source" -> _source_json(scope.source),
      "modelFacts" -> JsArray(scope.modelfacts.map(x => Json.obj("identity" -> _id_json(x.identity), "kind" -> x.kind.tag, "source" -> _source_json(x.source)))),
      "termFacts" -> JsArray(scope.termfacts.map(x => Json.obj("identity" -> Json.obj("vocabularyId" -> x.identity.vocabularyId, "termId" -> x.identity.termId), "source" -> _source_json(x.source)))),
      "relationFacts" -> JsArray(scope.relationfacts.map(x => Json.obj("identity" -> Json.obj("vocabularyId" -> x.identity.vocabularyId, "relationId" -> x.identity.relationId), "source" -> _source_json(x.source)))),
      "profileFacts" -> JsArray(scope.profilefacts.map(x => Json.obj("identity" -> Json.obj("vocabularyId" -> x.identity.vocabularyId, "profileId" -> x.identity.profileId), "source" -> _source_json(x.source)))),
      "applicationContracts" -> JsArray(scope.applicationcontracts.map(x => Json.obj("identity" -> _id_json(x.identity), "contractVersion" -> x.contractversion, "source" -> _source_json(x.source)))),
      "componentContracts" -> JsArray(scope.componentcontracts.map(x => Json.obj("identity" -> _id_json(x.identity), "contractVersion" -> x.contractversion, "source" -> _source_json(x.source)))),
      "targetContracts" -> JsArray(scope.targetcontracts.map { x =>
        val kind = x.kind match { case CmlCapabilityModel.Operation => "OPERATION"; case CmlCapabilityModel.Workflow => "WORKFLOW"; case CmlCapabilityModel.StateMachine => "STATE_MACHINE" }
        Json.obj("kind" -> kind, "identity" -> _id_json(x.identity), "component" -> _component_json(x.component), "contractVersion" -> x.contractversion,
          "generatedAbiVersion" -> x.generatedabiversion, "source" -> _source_json(x.source))
      }))
    Json.obj("schemaVersion" -> 1, "producer" -> producer, "sources" -> JsArray(request.sources.map(_source_json)), "scope" -> scopejson,
      "contexts" -> JsArray(request.contexts.map(x => Json.obj("projectionGeneration" -> x.projectiongeneration, "schemaVersion" -> x.schemaversion,
        "generatedAbiVersion" -> x.generatedabiversion, "producer" -> producer, "component" -> _coordinate_json(x.component), "package" -> _package_json(x.packagecoordinate), "source" -> _source_json(x.source)))),
      "packages" -> JsArray(request.packages.map(x => Json.obj("component" -> _coordinate_json(x.component), "package" -> _package_json(x.packagecoordinate), "path" -> x.path,
        "resources" -> JsArray(x.resources.map(r => Json.obj("snapshot" -> _snapshot_json(r.key), "filename" -> r.filename)))))),
      "scalaOutputs" -> JsArray(request.scalaoutputs.map(x => Json.obj("snapshot" -> _snapshot_json(x.key), "path" -> x.path,
        "packageName" -> x.packagename, "objectName" -> x.objectname))), "applicationOutput" -> request.applicationoutput)
  }
  private[cozy] def _normal_args(root: Path, request: ProductionRequest, command: String, output: String = "output"): Array[String] = {
    val requestpath = _write(root.resolve("request.json"), Json.stringify(_request_json(request)))
    val descriptor = _write(root.resolve("runtime.yaml"), "schemaVersion: 1\nruntime: cncf\nversion: 0.5.2-SNAPSHOT\npredefinedResults:\n  schemaVersion: cncf.predefined-result.v1\n  resultNames: []\n")
    Array(command, root.resolve(_primary_source.path).toString, "--capability-request", requestpath.toString, "--capability-source-root", root.toString,
      "--cncf-version", "0.5.2-SNAPSHOT", "--cozy-generator-version", org.simplemodeling.cozy.BuildInfo.version,
      "--component-version", request.contexts.head.component.version,
      "--cncf-runtime-descriptor", descriptor.toString,
      "--simplemodeling-model-version", org.simplemodeling.cozy.BuildInfo.simpleModelingModelVersion,
      "--generation-source-identity", _primary_source.path, "--save", root.resolve(output).toString)
  }
}
