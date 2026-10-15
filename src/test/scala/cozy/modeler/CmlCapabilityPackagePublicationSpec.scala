package cozy.modeler

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.collection.JavaConverters._
import org.goldenport.realm.Realm
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
 * @version Oct. 15, 2026
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
          When("the complete canonical Component, Usage and Scala batch is published")
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
    "preflight selected paths and regenerate ordinary outputs" which {
      "C64B09 reject actual dynamic tree files at package and case-aliased Scala selections without writes" in {
        _with_root { root =>
          Given("an in-memory normal tree with an arbitrary exportable metadata leaf and complete conflicting request copies")
          val normalpath = "target/cozy/dynamic-normal-metadata.json"
          val tree = Realm.Builder().set(normalpath, "actual normal metadata").build()
          val original = _request()
          val variants = Vector(
            original.copy(packages = original.packages.updated(0, original.packages.head.copy(path = normalpath))) -> "packages[0].path",
            original.copy(scalaoutputs = original.scalaoutputs.updated(0, original.scalaoutputs.head.copy(
              path = "target/COZY/DYNAMIC-NORMAL-METADATA.JSON/metadata.scala"))) -> "scalaOutputs[0].path")
          val output = root.resolve("output")
          When("pure normal-tree preflight checks each explicit selection against actual leaves")
          val results = variants.map { case (request, field) =>
            field -> CmlCapabilityPackagePublication.preflightNormalTree(request, tree, output)
          }
          Then("each OutputConflict retains the exact selected field and actual normal path without creating output")
          results.foreach { case (field, result) =>
            result.isLeft shouldBe true
            val issues = result.left.get
            issues.map(_.kind) shouldBe Vector(IssueKind.OutputConflict)
            issues.map(_.fieldpath) shouldBe Vector(field)
            issues.head.detail should include("actual normal destination " + normalpath)
          }
          Files.exists(output) shouldBe false
        }
      }
      "C64B11 allow normal-tree siblings and reject only conditional component API descriptor overlaps without writes" in {
        _with_root { root =>
          Given("a real non-overlapping normal leaf, an EmptyData leaf and valid selected package and Scala siblings under target/cozy")
          val original = _request()
          val request = original.copy(
            packages = original.packages.zipWithIndex.map { case (bundle, index) => bundle.copy(path = "target/cozy/bundle-" + index) },
            scalaoutputs = original.scalaoutputs.zipWithIndex.map { case (selection, index) => selection.copy(path = "target/cozy/Carrier" + index + ".scala") })
          val tree = Realm.Builder().set("target/cozy/unrelated-normal.json", "normal content").build()
          tree.setNode(request.packages.head.path)
          val output = root.resolve("output")
          When("pure preflight checks the actual normal leaves rather than denying their directory")
          val siblings = CmlCapabilityPackagePublication.preflightNormalTree(request, tree, output)
          Then("valid siblings and a selected EmptyData leaf remain accepted without output")
          siblings shouldBe Right(())
          Files.exists(output) shouldBe false

          Given("an actual component API model tree whose initially absent descriptor is a conditional follow-on output")
          val modelpath = "target/cozy/component-api-model.json"
          val descriptorpath = "target/cozy/component-api-descriptor.json"
          val modeltree = Realm.Builder().set(modelpath, "generated model").build()
          val variants = Vector(
            request.copy(packages = request.packages.updated(0, request.packages.head.copy(path = descriptorpath))) -> "packages[0].path",
            request.copy(scalaoutputs = request.scalaoutputs.updated(0, request.scalaoutputs.head.copy(
              path = descriptorpath + "/metadata.scala"))) -> "scalaOutputs[0].path")
          Files.exists(output.resolve(descriptorpath)) shouldBe false
          When("pure preflight includes the descriptor that the existing post-writer would create for the generated model")
          val generatedresults = variants.map { case (selected, field) =>
            field -> CmlCapabilityPackagePublication.preflightNormalTree(selected, modeltree, output)
          }
          Then("the conditional descriptor conflicts at the exact package or Scala field without writes")
          generatedresults.foreach { case (field, result) =>
            result.isLeft shouldBe true
            result.left.get.map(_.kind) shouldBe Vector(IssueKind.OutputConflict)
            result.left.get.map(_.fieldpath) shouldBe Vector(field)
            result.left.get.head.detail should include("actual normal destination " + descriptorpath)
          }
          Files.exists(output) shouldBe false

          Given("an already regular component API model and the unrelated normal tree with the descriptor still absent")
          val existingmodel = _write(output.resolve(modelpath), "existing model sentinel\n")
          Files.exists(output.resolve(descriptorpath)) shouldBe false
          When("pure preflight evaluates the same conditional writer edge from the existing model file")
          val existingresults = variants.map { case (selected, field) =>
            field -> CmlCapabilityPackagePublication.preflightNormalTree(selected, tree, output)
          }
          Then("the descriptor overlaps fail while the complete existing file inventory and model bytes remain unchanged")
          existingresults.foreach { case (field, result) =>
            result.isLeft shouldBe true
            result.left.get.map(_.kind) shouldBe Vector(IssueKind.OutputConflict)
            result.left.get.map(_.fieldpath) shouldBe Vector(field)
            result.left.get.head.detail should include("actual normal destination " + descriptorpath)
          }
          val stream = Files.walk(output)
          val files = try stream.iterator.asScala.filter(path => Files.isRegularFile(path)).toVector finally stream.close()
          files shouldBe Vector(existingmodel)
          Files.readString(existingmodel, StandardCharsets.UTF_8) shouldBe "existing model sentinel\n"
          Files.exists(output.resolve(descriptorpath)) shouldBe false
        }
      }
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
          val variants = Vector(original.copy(packages = original.packages.updated(0, original.packages.head.copy(path = "target/../outside"))),
            original.copy(scalaoutputs = original.scalaoutputs.updated(0, original.scalaoutputs.head.copy(path = original.packages.head.path + "/Carrier.scala"))),
            original.copy(packages = original.packages.updated(1, original.packages(1).copy(path = original.packages.head.path + "/child"))),
            original.copy(packages = original.packages.updated(0, original.packages.head.copy(path = original.scalaoutputs.head.path))))
          When("actual complete preparation preflights the path selections and existing roots")
          val selected = variants.map(CmlCapabilityProduction.prepare(_, root, root.resolve("output"), None))
          val roots = Vector(symlink, ancestor, alias).map(output => CmlCapabilityProduction.prepare(original, root, output, None))
          Then("every conflict rejects the full batch without publishing a valid sibling")
          (selected ++ roots).foreach(_.isLeft shouldBe true)
          Files.exists(root.resolve("output")) shouldBe false
        }
      }
      "C64B06 repeat publication and regenerate selected regular outputs from a changed valid request" in {
        _with_root { root =>
          Given("one actual prepared batch and its private publication root")
          _copy_sources(root)
          val prepared = _prepare(root, _request())
          val output = root.resolve("output")
          When("the complete batch is published twice")
          val first = _right(CmlCapabilityProduction.publish(output, prepared))
          val second = _right(CmlCapabilityProduction.publish(output, prepared))
          Then("both publications return the exact selected paths and current artifact contents")
          second shouldBe first
          first shouldBe prepared.batch.artifacts.map(x => output.resolve(x.path))
          prepared.batch.artifacts.foreach(x => Files.readString(output.resolve(x.path), StandardCharsets.UTF_8) shouldBe x.content)

          Given("a selected regular output containing a sentinel and a valid request with an updated Scala object name")
          val sentinel = first.head
          Files.writeString(sentinel, "different sentinel", StandardCharsets.UTF_8)
          val selection = prepared.request.scalaoutputs.head
          val updatedselection = selection.copy(objectname = selection.objectname + "Regenerated")
          val updatedrequest = prepared.request.copy(scalaoutputs = prepared.request.scalaoutputs.updated(0, updatedselection))
          When("real production prepares and publishes the changed request over the selected regular outputs")
          val updated = _right(CmlCapabilityProduction.prepare(updatedrequest, root, output, None))
          val regenerated = _right(CmlCapabilityProduction.publish(output, updated))
          Then("every selected artifact contains the updated batch bytes at the same selected paths")
          regenerated shouldBe first
          regenerated shouldBe updated.batch.artifacts.map(x => output.resolve(x.path))
          updated.batch.artifacts.foreach(x => Files.readString(output.resolve(x.path), StandardCharsets.UTF_8) shouldBe x.content)
          updated.rendered.artifacts.find(_.selection.path == updatedselection.path).get.selection.objectname shouldBe updatedselection.objectname
          Files.readString(output.resolve(updatedselection.path), StandardCharsets.UTF_8) should include("object " + updatedselection.objectname + " {")
        }
      }
      "C64B07 report actual written paths after deterministic publication IO failure" in {
        _with_root { root =>
          Given("an accepted real batch and a file writer that performs one real ordinary write before an IO failure")
          _copy_sources(root)
          val prepared = _prepare(root, _request())
          var writes = 0
          val writer = (path: Path, content: String) => {
            if (writes == 1) throw new IOException("controlled second-write failure")
            CmlCapabilityPackagePublicationPart._write_file(path, content)
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
