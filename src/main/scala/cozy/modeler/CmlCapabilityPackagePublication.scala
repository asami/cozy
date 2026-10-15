package cozy.modeler

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.Locale
import scala.util.control.NonFatal
import org.goldenport.realm.Realm
import org.goldenport.tree.TreeNode
import play.api.libs.json.{JsArray, JsString, Json}
import cozy.archive.{CozyArchivePackager, CozyComponentReleaseCoordinateCodec}
import CmlCapabilityGeneration.Generated
import CmlCapabilityGenerationContext._
import CmlCapabilityGenerationScala.RenderedBatch
import CmlCapabilityProductionRequest._
import CmlCapabilitySource.{CapabilitySource, _valid_path}

/*
 * @since   Oct. 14, 2026
 * @version Oct. 15, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CmlCapabilityPackagePublication {
  final case class Artifact(path: String, content: String, source: Option[CapabilitySource])
  final class Batch private[CmlCapabilityPackagePublication] (
    val generated: Generated, val rendered: RenderedBatch, val packages: Vector[PackageOutput],
    val artifacts: Vector[Artifact]
  )

  private[cozy] def preflightNormalDestinations(request: ProductionRequest): Either[Vector[Issue], Unit] = {
    if (request == null || request.packages == null || request.scalaoutputs == null)
      return Left(Vector(_issue("request", "The complete explicit request is required.")))
    val reservedpaths = Vector(
      GenerationProvenance.METADATA_PATH -> "provenance",
      "target/cozy/model-metadata.json" -> "Modeler metadata",
      "target/cozy/model-metadata.yaml" -> "Modeler metadata")
    def _reserved_(path: String, field: String): Vector[Issue] =
      reservedpaths.collect { case (reservedpath, kind) if _overlap(path, reservedpath) =>
        Issue(IssueKind.OutputConflict, field, None, Vector.empty,
          "Selected output conflicts with reserved normal " + kind + " destination " + reservedpath + ".")
      }
    val packageissues = request.packages.zipWithIndex.flatMap { case (bundle, index) =>
      val field = s"packages[$index]"
      if (bundle == null || bundle.resources == null || bundle.usagecapabilities == null || bundle.component == null || bundle.packagecoordinate == null)
        Vector(_issue(field, "Every package requires complete coordinates and explicit resources."))
      else _reserved_(bundle.path, field + ".path") ++
        (if (_valid_target_path(bundle.path)) Vector.empty else Vector(_issue(field + ".path", "Canonical target/ package directory path is required.")))
    }
    val scalaissues = request.scalaoutputs.zipWithIndex.flatMap { case (selection, index) =>
      val field = s"scalaOutputs[$index]"
      if (selection == null) Vector(_issue(field, "An explicit selection is required."))
      else _reserved_(selection.path, field + ".path") ++
        (if (_valid_target_path(selection.path) && selection.path.endsWith(".scala")) Vector.empty
          else Vector(_issue(field + ".path", "Select a canonical project-relative POSIX target/*.scala path.")))
    }
    val issues = packageissues ++ scalaissues
    if (issues.nonEmpty) Left(issues) else Right(())
  }

  private[cozy] def preflightNormalTree(request: ProductionRequest, tree: Realm,
    outputRoot: Path): Either[Vector[Issue], Unit] =
    preflightNormalDestinations(request).flatMap { _ =>
      def _files_(node: TreeNode[Realm.Data], parent: String): Vector[String] =
        node.children.toVector.flatMap { child =>
          val path = if (parent.isEmpty) child.name else parent + "/" + child.name
          if (child.isLeaf) {
            if (child.content == Realm.EmptyData) Vector.empty else Vector(path)
          } else _files_(child, path)
        }
      val generatedpaths = _files_(tree.backendRoot, "")
      val modelpath = "target/cozy/component-api-model.json"
      val normalpaths = generatedpaths ++
        (if (generatedpaths.contains(modelpath) || Files.isRegularFile(outputRoot.resolve(modelpath)))
          Vector("target/cozy/component-api-descriptor.json") else Vector.empty)
      def _conflicts_(path: String, field: String): Vector[Issue] =
        normalpaths.collect { case normalpath if _overlap(path, normalpath) =>
          Issue(IssueKind.OutputConflict, field, None, Vector.empty,
            "Selected output conflicts with actual normal destination " + normalpath + ".")
        }
      val issues = request.packages.zipWithIndex.flatMap { case (bundle, index) =>
        _conflicts_(bundle.path, s"packages[$index].path")
      } ++ request.scalaoutputs.zipWithIndex.flatMap { case (selection, index) =>
        _conflicts_(selection.path, s"scalaOutputs[$index].path")
      }
      if (issues.nonEmpty) Left(issues) else Right(())
    }

  def prepare(outputRoot: Path, generated: Generated, request: ProductionRequest,
    rendered: RenderedBatch): Either[Vector[Issue], Batch] = {
    if (generated == null || request == null || rendered == null || request.packages == null || rendered.generated != generated)
      return Left(Vector(_issue("batch", "A Generated, its rendered Scala batch and explicit request are required.")))
    val projections = generated.projections.map(x => x.snapshotKey -> x).toMap
    val selected = scala.collection.mutable.ArrayBuffer.empty[SnapshotKey]
    val artifacts = scala.collection.mutable.ArrayBuffer.empty[Artifact]
    val issues = request.packages.zipWithIndex.flatMap { case (bundle, index) =>
      val field = s"packages[$index]"
      if (bundle == null || bundle.resources == null || bundle.usagecapabilities == null || bundle.component == null || bundle.packagecoordinate == null)
        Vector(_issue(field, "Every package requires complete coordinates and explicit resources."))
      else {
        val paths = if (_valid_target_path(bundle.path)) Vector.empty else Vector(_issue(field + ".path", "Canonical target/ package directory path is required."))
        val names = bundle.resources.flatMap(x => Option(x).map(_.filename))
        val resourceissues = bundle.resources.zipWithIndex.flatMap { case (resource, resourceindex) =>
          val location = s"$field.resources[$resourceindex]"
          if (resource == null) Vector(_issue(location, "Explicit resource selection is required."))
          else {
            selected += resource.key
            val nameissues = if (validFilename(resource.filename)) Vector.empty else Vector(_issue(location + ".filename", "Safe flat JSON filename excluding index.json is required."))
            projections.get(resource.key) match {
              case None => nameissues :+ Issue(IssueKind.UnknownSnapshot, location, None, Vector.empty, "Unknown selected SnapshotKey.")
              case Some(projection) =>
                val domains = if (projection.context.component == bundle.component && projection.context.packagecoordinate == bundle.packagecoordinate) Vector.empty
                else Vector(Issue(IssueKind.OutputConflict, location, Some(projection.context.source), Vector.empty, "Selected full Component release and independent package domain must match its projection."))
                val content = projection.projectionJson + "\n"
                artifacts += Artifact(bundle.path + "/" + METADATA_PREFIX + resource.filename, content, Some(projection.context.source))
                nameissues ++ domains ++ (if (content.getBytes(StandardCharsets.UTF_8).length <= SOURCE_LIMIT) Vector.empty
                  else Vector(_issue(location, "Projection resource exceeds 16MiB.")))
            }
          }
        }
        val indexcontent = Json.stringify(Json.obj("schemaVersion" -> 1, "projections" -> JsArray(names.map(JsString)))) + "\n"
        artifacts += Artifact(bundle.path + "/" + METADATA_PREFIX + "index.json", indexcontent, None)
        val descriptorissues = _descriptor(bundle, field + ".component/descriptor") match {
          case Left(issue) => Vector(issue)
          case Right(content) =>
            artifacts += Artifact(bundle.path + "/component-descriptor.json", content, None)
            Vector.empty
        }
        val usageissues = CmlCapabilityComponentUsageProjection.encode(generated, request.sources, request.producer, bundle) match {
          case Left(items) => items.map(x => x.copy(fieldpath = field + "." + x.fieldpath))
          case Right(json) if bundle.usagecapabilities.nonEmpty =>
            val content = Json.stringify(json) + "\n"
            val usageindex = Json.stringify(Json.obj("schemaVersion" -> 1, "projections" -> Vector("usage.json"))) + "\n"
            artifacts += Artifact(bundle.path + "/" + USAGE_METADATA_PREFIX + "index.json", usageindex, None)
            artifacts += Artifact(bundle.path + "/" + USAGE_METADATA_PREFIX + "usage.json", content, None)
            if (content.getBytes(StandardCharsets.UTF_8).length <= SOURCE_LIMIT) Vector.empty
            else Vector(_issue(field + ".usageCapabilities", "Usage projection exceeds 16MiB."))
          case Right(_) => Vector.empty
        }
        val packagedomainissues = if (CmlCapabilitySource._valid_text(bundle.packagecoordinate.coordinate) &&
          CmlCapabilitySource._valid_text(bundle.packagecoordinate.version)) Vector.empty
          else Vector(_issue(field + ".package", "Explicit valid independent package coordinate and version are required."))
        val inventoryissues = if (names.size <= SOURCE_COUNT && names.distinct.size == names.size) Vector.empty
          else Vector(_issue(field + ".resources", "Each bundle requires 0..256 unique selected resources."))
        val size = artifacts.filter(_.path.startsWith(bundle.path + "/" + METADATA_PREFIX)).map(_.content.getBytes(StandardCharsets.UTF_8).length.toLong).sum
        val sizeissues = if (indexcontent.getBytes(StandardCharsets.UTF_8).length <= REQUEST_LIMIT && size <= TOTAL_LIMIT) Vector.empty
          else Vector(_issue(field, "Bundle metadata exceeds index 1MiB or total 64MiB bounds."))
        paths ++ resourceissues ++ descriptorissues ++ usageissues ++ packagedomainissues ++ inventoryissues ++ sizeissues
      }
    }
    val duplicate = selected.groupBy(identity).toVector.filter(_._2.size > 1).map { case (key, _) =>
      Issue(IssueKind.DuplicateSnapshot, "packages.resources", projections.get(key).map(_.context.source), Vector.empty, "Every snapshot requires exactly one resource.") }
    val missing = generated.projections.filterNot(x => selected.contains(x.snapshotKey)).map(x =>
      Issue(IssueKind.MissingCoverage, "packages.resources", Some(x.context.source), Vector.empty, "Every generated snapshot requires one resource."))
    val bundlepaths = request.packages.flatMap(x => Option(x).map(_.path))
    val overlaps = bundlepaths.zipWithIndex.flatMap { case (path, index) => bundlepaths.drop(index + 1).collect {
      case other if _overlap(path, other) => _issue("packages.path", "Repeated, case-aliased or overlapping bundle directories are forbidden.")
    }}
    val externalpaths = rendered.artifacts.map(_.selection.path)
    val externaloverlaps = externalpaths.flatMap(path => bundlepaths.collect {
      case bundle if _overlap(bundle, path) => _issue(path, "Scala outputs must be outside every selected bundle.")
    })
    val allissues = issues ++ duplicate ++ missing ++ overlaps ++ externaloverlaps
    if (allissues.nonEmpty) Left(allissues)
    else {
      artifacts ++= rendered.artifacts.map(x => Artifact(x.selection.path, x.content,
        projections.get(x.selection.key).map(_.context.source)))
      val ordered = artifacts.toVector.sortBy(_.path)
      val fileconflicts = ordered.zipWithIndex.flatMap { case (artifact, index) => ordered.drop(index + 1).collect {
        case other if _overlap(artifact.path, other.path) => _issue(artifact.path, "Output files must have distinct nonoverlapping canonical paths.")
      }}
      if (fileconflicts.nonEmpty) Left(fileconflicts)
      else {
        val batch = new Batch(generated, rendered, request.packages, ordered)
        CmlCapabilityPackagePublicationPart._preflight(outputRoot, batch).map(_ => batch)
      }
    }
  }
  def publish(outputRoot: Path, batch: Batch): Either[PublicationFailure, Vector[Path]] =
    CmlCapabilityPackagePublicationPart._publish(outputRoot, batch, CmlCapabilityPackagePublicationPart._atomic_write)

  private def _descriptor(bundle: PackageOutput, field: String): Either[Issue, String] =
    try {
      val coordinate = CozyComponentReleaseCoordinateCodec.admit(
        bundle.component.component.namespace().value(), bundle.component.component.localId().value(),
        bundle.component.version, field)
      val content = CozyArchivePackager._capability_component_descriptor_json(coordinate)
      if (content.getBytes(StandardCharsets.UTF_8).length <= SOURCE_LIMIT) Right(content)
      else Left(_issue(field, "Component descriptor exceeds 16MiB."))
    } catch { case NonFatal(error) =>
      Left(_issue(field, error.getClass.getName + ": " + Option(error.getMessage).getOrElse("Component descriptor admission/render failed.")))
    }

  private def _issue(field: String, detail: String): Issue = Issue(IssueKind.Malformed, field, None, Vector.empty, detail)
  private def _valid_target_path(path: String): Boolean = _valid_path(path) && CmlCapabilitySource._valid_text(path) && path.startsWith("target/")
  private def _overlap(left: String, right: String): Boolean = {
    if (left == null || right == null) false
    else {
      val a = left.toLowerCase(Locale.ROOT)
      val b = right.toLowerCase(Locale.ROOT)
      a == b || a.startsWith(b + "/") || b.startsWith(a + "/")
    }
  }
}
