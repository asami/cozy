package cozy.modeler

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._
import cozy.{Cozy, CozyCapabilityProductionSpec}
import CozyCapabilityProductionSpec._
import CmlCapabilityGenerationContext._
import CmlCapabilityProductionRequest._

/*
 * @since   Oct. 14, 2026
 * @version Oct. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final class CmlCapabilityDirectoryDescriptorSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Normal Capability Directory publication (spec:R13)" should {
    "retain selected canonical Component releases" which {
      "C64D01 publish canonical descriptors through the real normal Modeler command" in {
        _with_root { root =>
          Given("the complete original primary CML, Store and Archive selections and actual build/runtime inputs")
          _copy_sources(root)
          val request = _request()
          val args = _normal_args(root, request, "modeler-scala")
          When("the normal Cozy command produces the complete selected product")
          Cozy.main(args)
          Then("both descriptors have the literal selected schema-3 release and all original products exist")
          request.producer.version shouldBe org.simplemodeling.cozy.BuildInfo.version
          request.packages.zip(Vector("Store", "Archive")).foreach { case (bundle, id) =>
            val directory = root.resolve("output/" + bundle.path)
            _json(directory.resolve("component-descriptor.json")) shouldBe _descriptor_shape(id, "1.2.3-SNAPSHOT")
            _json(directory.resolve(METADATA_PREFIX + "index.json")) shouldBe
              Json.obj("schemaVersion" -> 1, "projections" -> bundle.resources.map(_.filename))
            val projection = _json(directory.resolve(METADATA_PREFIX + bundle.resources.head.filename))
            (projection \ "component").as[JsObject] shouldBe _coordinate_json(bundle.component)
            (projection \ "package").as[JsObject] shouldBe _package_json(bundle.packagecoordinate)
          }
          request.scalaoutputs.foreach(x => Files.readString(root.resolve("output/" + x.path)) should include("def projectionJson"))
          val application = _json(root.resolve("output/" + request.applicationoutput))
          val definitions = (application \ "documents").as[Vector[JsObject]].head
          definitions.keys shouldBe Set("source", "applicationCapabilities")
          (definitions \ "applicationCapabilities").as[Vector[JsObject]].foreach(_.keys shouldBe
            Set("kind", "identity", "contractVersion", "name", "summary", "description", "source", "demandReferences",
              "discoveryReferences", "componentMappings", "dependencies", "grounding", "traceability"))
        }
      }
      "C64D02 retain three independent releases and opaque package domains" in {
        _with_root { root =>
          Given("both complete original CML files with independently supplied Store, Archive and Remote releases")
          _copy_sources(root)
          val original = _request(multiple = true)
          val releases = Vector("2.3.4-SNAPSHOT", "3.4.5-SNAPSHOT", "4.5.6-SNAPSHOT")
          val contexts = original.contexts.zipWithIndex.map { case (context, index) =>
            context.copy(component = context.component.copy(version = releases(index)),
              packagecoordinate = PackageCoordinate("opaque:package:" + index, "opaque:release:" + index))
          }
          val request = original.copy(contexts = contexts, packages = original.packages.zip(contexts).map { case (bundle, context) =>
            bundle.copy(component = context.component, packagecoordinate = context.packagecoordinate)
          })
          When("actual typed production prepares and publishes the complete multi-source request")
          val prepared = _prepare(root, request)
          _right(CmlCapabilityProduction.publish(root.resolve("output"), prepared))
          Then("each descriptor retains its selected literal release and each original projection its separate package domain")
          request.packages.zipWithIndex.foreach { case (bundle, index) =>
            val directory = root.resolve("output/" + bundle.path)
            _json(directory.resolve("component-descriptor.json")) shouldBe
              _descriptor_shape(Vector("Store", "Archive", "Remote")(index), releases(index))
            val projection = _json(directory.resolve(METADATA_PREFIX + bundle.resources.head.filename))
            (projection \ "component").as[JsObject] shouldBe _coordinate_json(contexts(index).component)
            (projection \ "package").as[JsObject] shouldBe
              Json.obj("coordinate" -> ("opaque:package:" + index), "version" -> ("opaque:release:" + index))
          }
          prepared.generated.projections.size shouldBe 3
        }
      }
      "C64D03 publish one shared descriptor with both explicit source resources in authored order" in {
        _with_root { root =>
          Given("two original sources sharing the Store release/package with explicit second.json then first.json, plus Archive")
          _copy_sources(root)
          val original = _request(multiple = true)
          val store = original.contexts.head
          val secondary = original.contexts.last.copy(component = store.component, packagecoordinate = store.packagecoordinate)
          val bundled = original.packages.head.copy(resources = Vector(
            ResourceOutput(secondary.snapshotKey, "second.json"), ResourceOutput(store.snapshotKey, "first.json")))
          val request = original.copy(contexts = original.contexts.dropRight(1) :+ secondary,
            packages = Vector(bundled, original.packages(1)),
            scalaoutputs = original.scalaoutputs.updated(2, original.scalaoutputs.last.copy(key = secondary.snapshotKey)))
          When("real preparation and publication process the complete shared selection")
          val prepared = _prepare(root, request)
          val paths = _right(CmlCapabilityProduction.publish(root.resolve("output"), prepared))
          Then("Store has one descriptor and both complete original snapshots retain the explicit index order")
          val directory = root.resolve("output/" + bundled.path)
          paths.count(_ == directory.resolve("component-descriptor.json")) shouldBe 1
          prepared.batch.artifacts.count(_.path == bundled.path + "/component-descriptor.json") shouldBe 1
          _json(directory.resolve("component-descriptor.json")) shouldBe _descriptor_shape("Store", "1.2.3-SNAPSHOT")
          _json(directory.resolve(METADATA_PREFIX + "index.json")) shouldBe
            Json.obj("schemaVersion" -> 1, "projections" -> Vector("second.json", "first.json"))
          bundled.resources.foreach { resource =>
            val projection = prepared.generated.projections.find(_.snapshotKey == resource.key).get
            Files.readString(directory.resolve(METADATA_PREFIX + resource.filename)) shouldBe projection.projectionJson + "\n"
            _json(directory.resolve(METADATA_PREFIX + resource.filename)) shouldBe projection.json
          }
          prepared.generated.projections.size shouldBe 3
          request.scalaoutputs.foreach(x => Files.isRegularFile(root.resolve("output/" + x.path)) shouldBe true)
          _json(root.resolve("output/" + request.applicationoutput)) shouldBe prepared.batch.application
        }
      }
    }
    "reject unsafe or conflicting complete publication images" which {
      "C64D04 preserve a different selected descriptor and reject all sibling writes" in {
        _with_root { root =>
          Given("real original sources and a selected descriptor containing a different sentinel")
          _copy_sources(root)
          val request = _request()
          val output = root.resolve("output")
          val descriptorpath = request.packages.head.path + "/component-descriptor.json"
          val sentinel = _write(output.resolve(descriptorpath), "descriptor sentinel")
          When("actual production preflights the complete request")
          val result = CmlCapabilityProduction.prepare(request, root, output, None)
          Then("the typed descriptor conflict preserves its bytes and no otherwise valid sibling is written")
          val issues = _preparation_issues(result)
          issues.map(x => x.kind -> x.fieldpath) should contain(IssueKind.OutputConflict -> descriptorpath)
          Files.readString(sentinel) shouldBe "descriptor sentinel"
          _no_siblings(output, request)
        }
      }
      "C64D05 preserve selected symlink, directory and case alias leaves without sibling writes" in {
        _with_root { root =>
          Given("real sources and distinct selected descriptor leaves that are a symlink, directory and case alias")
          _copy_sources(root)
          val request = _request()
          val outputs = Vector("linked", "directory", "alias").map(root.resolve(_))
          val leaves = outputs.map(_.resolve(request.packages.head.path + "/component-descriptor.json"))
          leaves.foreach(path => Files.createDirectories(path.getParent))
          val target = _write(root.resolve("descriptor-target.txt"), "target sentinel")
          Files.createSymbolicLink(leaves(0), target)
          Files.createDirectories(leaves(1))
          val inside = _write(leaves(1).resolve("notes.txt"), "directory sentinel")
          val alias = _write(leaves(2).resolveSibling("COMPONENT-DESCRIPTOR.JSON"), "alias sentinel")
          val notes = outputs.map(output => _write(output.resolve(request.packages.head.path + "/notes.txt"), "unrelated sentinel"))
          When("actual complete preparation preflights each private output root")
          val results = outputs.map(output => CmlCapabilityProduction.prepare(request, root, output, None))
          Then("every leaf fails as unsafe while original kinds, target and unrelated contents are retained")
          results.foreach(result => _preparation_issues(result).map(_.kind) should contain(IssueKind.UnsafeOutput))
          Files.isSymbolicLink(leaves(0)) shouldBe true
          Files.readSymbolicLink(leaves(0)) shouldBe target
          Files.readString(target) shouldBe "target sentinel"
          Files.isDirectory(leaves(1), LinkOption.NOFOLLOW_LINKS) shouldBe true
          Files.readString(inside) shouldBe "directory sentinel"
          Files.readString(alias) shouldBe "alias sentinel"
          notes.foreach(path => Files.readString(path) shouldBe "unrelated sentinel")
          outputs.foreach(output => _no_siblings(output, request))
        }
      }
      "C64D07 report malformed selected coordinates as typed complete-batch failures" in {
        _with_root { root =>
          Given("a real accepted Generated/rendered request and selected null-identity or invalid-release bundle variants")
          _copy_sources(root)
          val request = _request()
          val prepared = _prepare(root, request)
          val bundle = request.packages.head
          val variants = Vector(bundle.copy(component = bundle.component.copy(component = null)),
            bundle.copy(component = bundle.component.copy(version = " invalid release ")))
          val outputs = variants.indices.map(index => root.resolve("invalid" + index)).toVector
          When("the actual publication prepare API receives each malformed selected bundle on the real generated image")
          val results = variants.zip(outputs).map { case (variant, output) =>
            CmlCapabilityPackagePublication.prepare(output, prepared.generated,
              request.copy(packages = request.packages.updated(0, variant)), prepared.rendered)
          }
          Then("each returns Malformed at its component/descriptor location without throwing, defaulting or partial output")
          results.foreach { result =>
            result.isLeft shouldBe true
            val issues = result.left.get
            issues.map(x => x.kind -> x.fieldpath) should contain(IssueKind.Malformed -> "packages[0].component/descriptor")
            issues.find(_.fieldpath == "packages[0].component/descriptor").get.detail should not be empty
          }
          outputs.foreach(output => Files.exists(output, LinkOption.NOFOLLOW_LINKS) shouldBe false)
          Files.exists(root.resolve("output"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        }
      }
      "C64D08 reject every alternative canonical slot including equal generated content and case aliases" in {
        _with_root { root =>
          Given("real original sources, a generated descriptor image, every alternative six-format root/meta slot and unrelated notes")
          _copy_sources(root)
          val request = _request()
          val reference = root.resolve("reference")
          val prepared = _right(CmlCapabilityProduction.prepare(request, root, reference, None))
          _right(CmlCapabilityProduction.publish(reference, prepared))
          val generatedtext = Files.readString(reference.resolve(request.packages.head.path + "/component-descriptor.json"))
          val formats = Vector("json", "yaml", "yml", "conf", "hocon", "xml")
          val alternatives = formats.map("descriptor." + _) ++
            formats.filterNot(_ == "json").map("component-descriptor." + _) ++
            formats.map("meta/component-descriptor." + _) ++
            Vector("DESCRIPTOR.JSON", "COMPONENT-DESCRIPTOR.YAML", "meta/COMPONENT-DESCRIPTOR.JSON")
          val outputs = alternatives.indices.map(index => root.resolve("alternative" + index)).toVector
          val entries = alternatives.zip(outputs).map { case (slot, output) =>
            _write(output.resolve(request.packages.head.path + "/" + slot), generatedtext)
          }
          val notes = outputs.flatMap(output => Vector("notes.txt", "meta/notes.txt").map(name =>
            _write(output.resolve(request.packages.head.path + "/" + name), "unrelated notes")))
          val clean = root.resolve("clean")
          val cleannotes = Vector("notes.txt", "meta/notes.txt").map(name =>
            _write(clean.resolve(request.packages.head.path + "/" + name), "clean unrelated notes"))
          When("complete production preflights every alternative and publishes the clean noncanonical-asset root")
          val results = outputs.map(output => CmlCapabilityProduction.prepare(request, root, output, None))
          val cleanprepared = _right(CmlCapabilityProduction.prepare(request, root, clean, None))
          _right(CmlCapabilityProduction.publish(clean, cleanprepared))
          Then("all alternative slots reject before writes even with equal content while unrelated assets survive successful publication")
          alternatives.size shouldBe 20
          results.zip(entries).foreach { case (result, entry) =>
            _preparation_issues(result).map(x => x.kind -> x.fieldpath) should contain(IssueKind.OutputConflict -> entry.toString)
            Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS) shouldBe true
            Files.readString(entry) shouldBe generatedtext
          }
          outputs.foreach(output => _no_siblings(output, request))
          notes.foreach(path => Files.readString(path) shouldBe "unrelated notes")
          cleannotes.foreach(path => Files.readString(path) shouldBe "clean unrelated notes")
          _json(clean.resolve(request.packages.head.path + "/component-descriptor.json")) shouldBe
            _descriptor_shape("Store", "1.2.3-SNAPSHOT")
          cleanprepared.batch.artifacts.foreach(x => Files.readString(clean.resolve(x.path)) shouldBe x.content)
        }
      }
    }
    "restore and retain accepted selected images" which {
      "C64D06 restore only a missing generated descriptor and publish the accepted batch idempotently" in {
        _with_root { root =>
          Given("a real prepared and published complete batch with only its own Store descriptor removed")
          _copy_sources(root)
          val prepared = _prepare(root, _request())
          val output = root.resolve("output")
          val first = _right(CmlCapabilityProduction.publish(output, prepared))
          val descriptor = output.resolve(prepared.request.packages.head.path + "/component-descriptor.json")
          val shape = _json(descriptor)
          Files.delete(descriptor)
          When("the same accepted batch is published to restore its missing descriptor and then published identically again")
          val restored = _right(CmlCapabilityProduction.publish(output, prepared))
          val repeated = _right(CmlCapabilityProduction.publish(output, prepared))
          Then("both publications report every selected path and every accepted image remains exact")
          restored shouldBe first
          repeated shouldBe first
          _json(descriptor) shouldBe shape
          shape shouldBe _descriptor_shape("Store", "1.2.3-SNAPSHOT")
          prepared.batch.artifacts.foreach(x => Files.readString(output.resolve(x.path), StandardCharsets.UTF_8) shouldBe x.content)
        }
      }
    }
  }

  private def _descriptor_shape(id: String, version: String): JsObject = Json.obj(
    "schemaVersion" -> 3,
    "component" -> Json.obj("namespace" -> "org.simplemodeling.textus", "id" -> id, "version" -> version),
    "entities" -> Json.arr(), "extensions" -> Json.obj(), "config" -> Json.obj())

  private def _preparation_issues(result: Either[CmlCapabilityProduction.Failure, CmlCapabilityProduction.Prepared]): Vector[Issue] =
    result match {
      case Left(CmlCapabilityProduction.RequestOrAcquisition(issues)) => issues
      case other => fail("Expected typed complete preparation issues, received " + other)
    }

  private def _no_siblings(output: Path, request: ProductionRequest): Unit = {
    request.packages.foreach { bundle =>
      Files.exists(output.resolve(bundle.path + "/" + METADATA_PREFIX + "index.json"), LinkOption.NOFOLLOW_LINKS) shouldBe false
      bundle.resources.foreach(resource =>
        Files.exists(output.resolve(bundle.path + "/" + METADATA_PREFIX + resource.filename), LinkOption.NOFOLLOW_LINKS) shouldBe false)
    }
    request.scalaoutputs.foreach(x => Files.exists(output.resolve(x.path), LinkOption.NOFOLLOW_LINKS) shouldBe false)
    Files.exists(output.resolve(request.applicationoutput), LinkOption.NOFOLLOW_LINKS) shouldBe false
  }
}
