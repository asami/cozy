package cozy.modeler

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._
import cozy.CozyCapabilityProductionSpec._
import CmlCapabilityGenerationContext._
import CmlCapabilityProductionRequest._

/*
 * @since   Oct. 14, 2026
 * @version Oct. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final class CmlCapabilityPackagePublicationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Complete Capability publication (spec:R13)" should {
    "preserve explicit canonical images" which {
      "C64B01 publish exact canonical index order and original projection bytes" in {
        _with_root { root =>
          Given("the two actual original sources, explicit selected filenames and successful production preparation")
          _copy_sources(root)
          val prepared = _prepare(root, _request(multiple = true))
          When("the complete canonical Component, Application and Scala batch is published")
          val paths = _right(CmlCapabilityProduction.publish(root.resolve("output"), prepared))
          Then("each explicit index and resource preserves original projection and authored inner vector order")
          prepared.request.packages.foreach { bundle =>
            val metadata = root.resolve("output/" + bundle.path + "/meta/component-capability")
            _json(metadata.resolve("index.json")) shouldBe Json.obj("schemaVersion" -> 1, "projections" -> bundle.resources.map(_.filename))
            bundle.resources.foreach { resource =>
              val projection = prepared.generated.projections.find(_.snapshotKey == resource.key).get
              Files.readString(metadata.resolve(resource.filename), StandardCharsets.UTF_8) shouldBe projection.projectionJson + "\n"
              _json(metadata.resolve(resource.filename)) shouldBe projection.json
            }
          }
          paths.map(_.toString) shouldBe paths.map(_.toString).sorted
          paths.size shouldBe prepared.batch.artifacts.size
        }
      }
      "C64B02 keep independent Component release and package domains in separate bundles" in {
        _with_root { root =>
          Given("three explicitly selected Component snapshots with distinct release and package versions")
          _copy_sources(root)
          val original = _request(multiple = true)
          val contexts = original.contexts.zipWithIndex.map { case (context, index) => context.copy(component = context.component.copy(version = s"1.2.$index-SNAPSHOT"),
            packagecoordinate = PackageCoordinate(s"independent:pkg:$index", s"independent:release:$index")) }
          val request = original.copy(contexts = contexts, packages = original.packages.zip(contexts).map { case (bundle, context) =>
            bundle.copy(component = context.component, packagecoordinate = context.packagecoordinate) })
          When("actual production prepares and publishes these independent selections")
          val prepared = _prepare(root, request)
          _right(CmlCapabilityProduction.publish(root.resolve("output"), prepared))
          Then("each bundle retains its exact full packaging domain without deriving it from its filename")
          request.packages.foreach { bundle =>
            val json = _json(root.resolve("output/" + bundle.path + "/meta/component-capability/" + bundle.resources.head.filename))
            (json \ "component" \ "version").as[String] shouldBe bundle.component.version
            (json \ "package").as[JsObject] shouldBe _package_json(bundle.packagecoordinate)
          }
          request.packages.map(_.path).distinct.size shouldBe 3
        }
      }
      "C64B03 reject incomplete snapshots, unsafe resources, overflow and overlapping selections" in {
        _with_root { root =>
          Given("a real generated source and explicit invalid package inventory variants")
          _copy_sources(root)
          val original = _request()
          val bundle = original.packages.head
          val resource = bundle.resources.head
          val invalidbundles = Vector(bundle.copy(resources = Vector.empty), bundle.copy(resources = Vector(resource, resource)),
            bundle.copy(resources = Vector(resource.copy(key = resource.key.copy(component = _component("Unknown"))))),
            bundle.copy(resources = Vector(resource.copy(filename = "index.json"))), bundle.copy(resources = Vector(resource.copy(filename = "../outside.json"))),
            bundle.copy(resources = Vector(resource.copy(filename = "nested/item.json"))), bundle.copy(resources = Vector(resource.copy(filename = "x.JSON"))),
            bundle.copy(resources = Vector.fill(257)(resource)), bundle.copy(path = original.packages(1).path + "/nested"))
          val requests = invalidbundles.map(x => original.copy(packages = original.packages.updated(0, x))) :+
            original.copy(packages = original.packages :+ bundle)
          When("the real production gate prepares each invalid complete selection")
          val results = requests.map(CmlCapabilityProduction.prepare(_, root, root.resolve("output"), None))
          Then("the whole batch fails without writing any otherwise valid resource")
          results.foreach(_.isLeft shouldBe true)
          Files.exists(root.resolve("output")) shouldBe false
        }
      }
    }
    "preflight and preserve existing filesystem content" which {
      "C64B04 reject unlisted, nested, symlink and nonregular metadata while preserving unrelated assets" in {
        _with_root { root =>
          Given("real inputs and separate invalid metadata inventories with unrelated nonJSON assets")
          _copy_sources(root)
          val request = _request()
          val outputs = (0 until 4).map(index => root.resolve("out" + index)).toVector
          outputs.foreach(output => _write(output.resolve(request.packages.head.path + "/meta/component-capability/notes.txt"), "unrelated asset"))
          val directories = outputs.map(_.resolve(request.packages.head.path + "/meta/component-capability"))
          _write(directories(0).resolve("unlisted.json"), "{}")
          _write(directories(1).resolve("nested/extra.json"), "{}")
          Files.createSymbolicLink(directories(2).resolve("Store.json"), root.resolve(_primary_source.path))
          Files.createDirectories(directories(3).resolve("Store.json"))
          When("actual complete preparation preflights the full selected metadata inventories")
          val results = outputs.map(output => CmlCapabilityProduction.prepare(request, root, output, None))
          val validoutput = root.resolve("valid")
          val asset = _write(validoutput.resolve(request.packages.head.path + "/meta/component-capability/notes.txt"), "unrelated asset")
          val prepared = _right(CmlCapabilityProduction.prepare(request, root, validoutput, None))
          _right(CmlCapabilityProduction.publish(validoutput, prepared))
          Then("invalid inventories are rejected and unrelated nonJSON content remains unchanged even on successful publication")
          results.foreach(_.isLeft shouldBe true)
          directories.foreach(path => Files.readString(path.resolve("notes.txt")) shouldBe "unrelated asset")
          Files.readString(asset) shouldBe "unrelated asset"
        }
      }
      "C64B05 reject root, ancestor, traversal, case alias and cross-product path conflicts before writes" in {
        _with_root { root =>
          Given("real inputs and explicit root, ancestor or selection path conflicts")
          _copy_sources(root)
          val original = _request()
          val symlink = root.resolve("linked")
          Files.createSymbolicLink(symlink, root)
          val ancestor = root.resolve("ancestor")
          Files.createDirectories(ancestor)
          Files.createSymbolicLink(ancestor.resolve("target"), root.resolve("model"))
          val alias = root.resolve("alias")
          Files.createDirectories(alias.resolve("TARGET"))
          val variants = Vector(original.copy(applicationoutput = "target/../outside.json"),
            original.copy(applicationoutput = original.packages.head.path + "/application.json"),
            original.copy(scalaoutputs = original.scalaoutputs.updated(0, original.scalaoutputs.head.copy(path = original.packages.head.path + "/Carrier.scala"))),
            original.copy(packages = original.packages.updated(1, original.packages(1).copy(path = original.packages.head.path + "/child"))),
            original.copy(applicationoutput = original.scalaoutputs.head.path))
          When("actual complete preparation preflights the path selections and existing roots")
          val selected = variants.map(CmlCapabilityProduction.prepare(_, root, root.resolve("output"), None))
          val roots = Vector(symlink, ancestor, alias).map(output => CmlCapabilityProduction.prepare(original, root, output, None))
          Then("every conflict rejects the full batch without publishing a valid sibling")
          (selected ++ roots).foreach(_.isLeft shouldBe true)
          Files.exists(root.resolve("output")) shouldBe false
        }
      }
      "C64B06 publish idempotently and preserve conflicting sentinel bytes" in {
        _with_root { root =>
          Given("one actual prepared batch and its private publication root")
          _copy_sources(root)
          val prepared = _prepare(root, _request())
          When("the batch is published twice, then a selected file is replaced with a test sentinel")
          val first = _right(CmlCapabilityProduction.publish(root.resolve("output"), prepared))
          val second = _right(CmlCapabilityProduction.publish(root.resolve("output"), prepared))
          val sentinel = first.head
          Files.writeString(sentinel, "different sentinel", StandardCharsets.UTF_8)
          val failed = CmlCapabilityProduction.publish(root.resolve("output"), prepared)
          Then("identical publication is idempotent and different existing content is never overwritten or removed")
          second shouldBe first
          failed.isLeft shouldBe true
          Files.readString(sentinel) shouldBe "different sentinel"
          first.tail.foreach(path => Files.isRegularFile(path) shouldBe true)
        }
      }
      "C64B07 report actual written paths after deterministic publication IO failure" in {
        _with_root { root =>
          Given("an accepted real batch and a file writer that performs one real atomic write before an IO failure")
          _copy_sources(root)
          val prepared = _prepare(root, _request())
          var writes = 0
          val writer = (path: Path, content: String) => {
            if (writes == 1) throw new IOException("controlled second-write failure")
            CmlCapabilityPackagePublicationPart._atomic_write(path, content)
            writes += 1
          }
          When("the actual publication core executes this deterministic file IO boundary")
          val failed = CmlCapabilityPackagePublicationPart._publish(root.resolve("output"), prepared.batch, writer)
          Then("the typed failure records exactly the real already-written file and preserves its contents")
          failed.isLeft shouldBe true
          failed.left.get.writtenpaths shouldBe Vector(root.resolve("output/" + prepared.batch.artifacts.head.path))
          failed.left.get.issues.map(_.kind) shouldBe Vector(IssueKind.IoFailure)
          Files.readString(failed.left.get.writtenpaths.head) shouldBe prepared.batch.artifacts.head.content
          Files.exists(root.resolve("output/" + prepared.batch.artifacts(1).path)) shouldBe false
        }
      }
      "C64B08 preserve selected images and explicit index order under active package permutations" in {
        _with_root { root =>
          Given("two actual sources sharing one explicitly selected full Component/package domain and a separate bundle")
          _copy_sources(root)
          val original = _request(multiple = true)
          val store = original.contexts.head
          val secondary = original.contexts.last.copy(component = store.component, packagecoordinate = store.packagecoordinate)
          val contexts = original.contexts.dropRight(1) :+ secondary
          val bundled = original.packages.head.copy(resources = Vector(ResourceOutput(secondary.snapshotKey, "second.json"), ResourceOutput(store.snapshotKey, "first.json")))
          val request = original.copy(contexts = contexts, packages = Vector(bundled, original.packages(1)),
            scalaoutputs = original.scalaoutputs.updated(2, original.scalaoutputs.last.copy(key = secondary.snapshotKey)))
          val baseline = _prepare(root, request)
          val orders = Gen.oneOf(request.packages.permutations.toVector)
          val property = Prop.forAll(orders) { order =>
            CmlCapabilityProduction.prepare(request.copy(packages = order), root, root.resolve("output"), None) match {
              case Left(_) => false
              case Right(prepared) => prepared.batch.artifacts == baseline.batch.artifacts &&
                (Json.parse(prepared.batch.artifacts.find(_.path == bundled.path + "/meta/component-capability/index.json").get.content) \ "projections")
                  .as[Vector[String]] == Vector("second.json", "first.json")
            }
          }
          When("ScalaCheck evaluates the real source-to-batch preparation over unfiltered traversal orders")
          val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
          Then("one hundred active samples preserve semantic images and each explicitly authored index order")
          result.passed shouldBe true
          result.succeeded should be >= 100
          result.discarded shouldBe 0
        }
      }
    }
  }
}
