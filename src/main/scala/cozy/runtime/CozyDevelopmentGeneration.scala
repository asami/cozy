package cozy.runtime

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.jar.JarFile
import scala.collection.JavaConverters._
import play.api.libs.json._
import org.goldenport.RAISE

/*
 * @since   Nov. 19, 2026
 * @version Nov. 19, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyDevelopmentGeneration {
  final case class Module(
    organization: String, name: String, requestedVersion: String,
    selectedVersion: String, baseVersion: String, artifact: String,
    development: Boolean, publication: Option[String], control: Option[String]
  ) {
    def identity: String = s"$organization:$name:$selectedVersion"
  }
  private final case class MavenModel(properties: Map[String, String], managed: Map[(String, String), String], dependencies: Map[(String, String), (String, String, String)])
  final case class Edge(from: String, to: String, requestedVersion: String)
  final case class Selection(scope: String, owner: String, selectionId: String, batch: String, publicationRoot: String)
  final case class Resolution(modules: Vector[Module], edges: Vector[Edge])
  final case class Batch(scope: String, owner: String, batch: String, producerSelectionId: String, complete: Boolean, members: Vector[Module])
  final case class Roots(build: String, owner: String, source: String, output: String, result: String)
  final case class Request(
    version: String, action: String, selection: Selection, roots: Roots,
    modules: Vector[Module], edges: Vector[Edge], classpath: Vector[String],
    resolution: String, batchRecord: String, arguments: Vector[String], settings: Map[String, String]
  )
  final case class Origin(className: String, codeSource: String, resource: String)
  final case class Observation(
    classpath: Vector[String], loaderUrls: Vector[String], cozy: Origin, simpleModeler: Origin,
    cozyVersion: String, requiredSimpleModelerVersion: String,
    simpleModelerVersion: String
  )
  final case class Result(
    version: String, outcome: String, selection: Selection,
    modules: Vector[Module], edges: Vector[Edge], resolvedClasspath: Vector[String],
    observation: Observation, generatedFiles: Vector[String], metadataFiles: Vector[String],
    diagnostics: Vector[String]
  )
  implicit val moduleFormat: OFormat[Module] = Json.format[Module]
  implicit val edgeFormat: OFormat[Edge] = Json.format[Edge]
  implicit val selectionFormat: OFormat[Selection] = Json.format[Selection]
  implicit val resolutionFormat: OFormat[Resolution] = Json.format[Resolution]
  implicit val batchFormat: OFormat[Batch] = Json.format[Batch]
  implicit val rootsFormat: OFormat[Roots] = Json.format[Roots]
  implicit val requestFormat: OFormat[Request] = Json.format[Request]
  implicit val originFormat: OFormat[Origin] = Json.format[Origin]
  implicit val observationFormat: OFormat[Observation] = Json.format[Observation]
  implicit val resultFormat: OFormat[Result] = Json.format[Result]

  // The v3 report is producer-supplied data. Its presence is not proof that an
  // SBT update ran; the producer retains and reconciles its in-memory value.
  final case class ModuleV3(
    organization: String, name: String, requestedVersion: String,
    selectedVersion: String, baseVersion: String, development: Boolean,
    descriptor: Option[String], control: Option[String]
  ) {
    def identity: String = s"$organization:$name:$selectedVersion"
  }
  final case class ArtifactKeyV3(
    module: String, name: String, artifactType: String, extension: String,
    classifier: Option[String], extraAttributes: Map[String, String]
  )
  final case class ArtifactV3(key: ArtifactKeyV3, resolverFile: String, launchFile: String, runtime: Boolean)
  final case class ReportArtifactV3(key: ArtifactKeyV3, file: String)
  final case class ExclusionV3(organization: String, name: String, artifact: String, configurations: Vector[String], attributes: Map[String, String])
  final case class CallerV3(
    from: String, sourceRevision: String, configurations: Vector[String], attributes: Map[String, String],
    explicitArtifacts: Vector[ArtifactKeyV3], exclusions: Vector[ExclusionV3],
    isForce: Boolean, isChanging: Boolean, isTransitive: Boolean, isDirectlyForce: Boolean
  )
  final case class ReportArtifactSelectorV3(
    name: String, artifactType: String, extension: String, classifier: Option[String],
    extraAttributes: Map[String, String]
  )
  final case class ReportTargetModuleV3(
    organization: String, name: String, revision: String, configurations: Option[String],
    explicitArtifacts: Vector[ReportArtifactSelectorV3], exclusions: Vector[ExclusionV3],
    isForce: Boolean, isChanging: Boolean, isTransitive: Boolean, extraAttributes: Map[String, String]
  )
  final case class EvictionV3(evicted: Boolean, selectedRevision: Option[String], details: Vector[String])
  final case class ReportRowV3(
    module: String, revision: String, configurations: Vector[String],
    artifacts: Vector[ReportArtifactV3], missingArtifacts: Vector[ArtifactKeyV3],
    callers: Vector[CallerV3], eviction: EvictionV3, problem: Option[String],
    attributes: Map[String, String], targetModule: ReportTargetModuleV3,
    resolver: Option[String], artifactResolver: Option[String]
  )
  final case class ResolutionV3(
    producerRunReference: String, requestRoots: Vector[String],
    exactOverrides: Map[String, String], requestedConfigurations: Vector[String],
    reportRows: Vector[ReportRowV3], modules: Vector[ModuleV3], artifacts: Vector[ArtifactV3],
    edges: Vector[Edge], runtimeClasspath: Vector[ArtifactKeyV3],
    producerJvm: JsObject, resolverConfiguration: JsObject, optionalDiagnostics: JsObject
  )
  final case class PublishedModuleV3(
    organization: String, name: String, selectedVersion: String, baseVersion: String,
    descriptor: String, control: String
  ) {
    def identity: String = s"$organization:$name:$selectedVersion"
  }
  final case class PublishedArtifactV3(key: ArtifactKeyV3, publicationFile: String)
  final case class BatchMemberV3(module: PublishedModuleV3, artifacts: Vector[PublishedArtifactV3])
  final case class BatchV3(
    scope: String, owner: String, batch: String, producerSelectionId: String,
    complete: Boolean, members: Vector[BatchMemberV3]
  )
  final case class RequestV3(
    version: String, action: String, selection: Selection, roots: Roots,
    resolution: ResolutionV3, batchRecord: String, arguments: Vector[String],
    settings: Map[String, String]
  )
  final case class ObservationV3(
    osName: String, osArch: String, javaVersion: String, javaVendor: String,
    classpath: Vector[String], loaderUrls: Vector[String], cozy: Origin, simpleModeler: Origin,
    cozyVersion: String, requiredSimpleModelerVersion: String, simpleModelerVersion: String
  )
  final case class ResultV3(
    version: String, outcome: String, selection: Selection, receivedResolution: ResolutionV3,
    observation: ObservationV3, generatedFiles: Vector[String], metadataFiles: Vector[String],
    diagnostics: Vector[String]
  )
  implicit val moduleV3Format: OFormat[ModuleV3] = Json.format[ModuleV3]
  implicit val artifactKeyV3Format: OFormat[ArtifactKeyV3] = Json.format[ArtifactKeyV3]
  implicit val artifactV3Format: OFormat[ArtifactV3] = Json.format[ArtifactV3]
  implicit val reportArtifactV3Format: OFormat[ReportArtifactV3] = Json.format[ReportArtifactV3]
  implicit val exclusionV3Format: OFormat[ExclusionV3] = Json.format[ExclusionV3]
  implicit val callerV3Format: OFormat[CallerV3] = Json.format[CallerV3]
  implicit val reportArtifactSelectorV3Format: OFormat[ReportArtifactSelectorV3] = Json.format[ReportArtifactSelectorV3]
  implicit val reportTargetModuleV3Format: OFormat[ReportTargetModuleV3] = Json.format[ReportTargetModuleV3]
  implicit val evictionV3Format: OFormat[EvictionV3] = Json.format[EvictionV3]
  implicit val reportRowV3Format: OFormat[ReportRowV3] = Json.format[ReportRowV3]
  implicit val resolutionV3Format: OFormat[ResolutionV3] = Json.format[ResolutionV3]
  implicit val publishedModuleV3Format: OFormat[PublishedModuleV3] = Json.format[PublishedModuleV3]
  implicit val publishedArtifactV3Format: OFormat[PublishedArtifactV3] = Json.format[PublishedArtifactV3]
  implicit val batchMemberV3Format: OFormat[BatchMemberV3] = Json.format[BatchMemberV3]
  implicit val batchV3Format: OFormat[BatchV3] = Json.format[BatchV3]
  implicit val requestV3Format: OFormat[RequestV3] = Json.format[RequestV3]
  implicit val observationV3Format: OFormat[ObservationV3] = Json.format[ObservationV3]
  implicit val resultV3Format: OFormat[ResultV3] = Json.format[ResultV3]

  def executeV3(requestPath: Path): Unit = {
    val request = Json.parse(Files.readAllBytes(requestPath)).validate[RequestV3]
      .getOrElse(_invalid("invalid v3 request"))
    validateV3(request)
    val observation = observeV3(request)
    val output = _path(request.roots.output)
    val result = _path(request.roots.result)
    def _write_(outcome: String, diagnostics: Vector[String]): Unit = {
      val files = if (Files.isDirectory(output)) _files(output) else Vector.empty
      Option(result.getParent).foreach(Files.createDirectories(_))
      Files.write(result, Json.prettyPrint(Json.toJson(ResultV3("v3", outcome, request.selection,
        request.resolution, observation, files, _metadata(files), diagnostics))).getBytes(StandardCharsets.UTF_8))
    }
    try {
      CozySbtBridge.runDevelopmentGeneration(request.arguments, request.settings)
      if (!Files.isDirectory(output) || !_files(output).exists(_.endsWith(".scala")))
        _invalid("generation produced no Scala files")
      _write_("generated", Vector.empty)
    } catch {
      case scala.util.control.NonFatal(error) =>
        _write_("failed", Vector(Option(error.getMessage).getOrElse(error.getClass.getName)))
        throw error
    }
  }

  def validateV3(request: RequestV3): Unit = {
    if (request.version != "v3" || request.action != "generate-development")
      _invalid("expected v3 generate-development")
    val selection = request.selection
    if (Vector(selection.scope, selection.owner, selection.selectionId, selection.batch).exists(_.trim.isEmpty))
      _invalid("scope, owner, selection identity and batch must be non-empty")
    Vector(request.roots.owner, selection.publicationRoot, request.roots.build, request.roots.source,
      request.roots.output, request.roots.result, request.batchRecord).foreach(_require_v3_no_links)
    val ownerroot = _existing_dir(request.roots.owner)
    val publicationroot = _existing_dir(selection.publicationRoot)
    val build = _existing_dir(request.roots.build)
    val source = _existing_dir(request.roots.source)
    val output = _path(request.roots.output)
    val result = _path(request.roots.result)
    if (!publicationroot.startsWith(ownerroot)) _invalid("publication root must belong to owner root")
    if (!output.startsWith(build) || !result.startsWith(build) || result.startsWith(output) || output == build)
      _invalid("output and result must be separate paths within the build root")
    _require_no_links(build, output)
    _require_no_links(build, result)
    if (Files.exists(output) && (!Files.isDirectory(output) || _files(output).nonEmpty))
      _invalid("output must be absent or empty")
    if (request.settings.get("sbt.project_dir").map(_path) != Some(build))
      _invalid("sbt.project_dir must equal the build root")
    if (request.arguments.size != 4 || !Set("modeler-scala", "modeler-scala-value").contains(request.arguments.head) ||
        request.arguments(2) != "--save") _invalid("arguments must contain a fixed generation action, source and --save output")
    val input = _path(request.arguments(1))
    _require_v3_no_links(request.arguments(1))
    if (!Files.isRegularFile(input) || !input.toRealPath().startsWith(source) || _path(request.arguments(3)) != output)
      _invalid("generation source or output is outside its admitted root")
    _require_no_links(source, input)

    val resolution = request.resolution
    val modules = resolution.modules
    val artifacts = resolution.artifacts
    val edges = resolution.edges
    if (resolution.producerRunReference.trim.isEmpty || resolution.requestedConfigurations.isEmpty || resolution.reportRows.isEmpty)
      _invalid("producer reference, requested configurations and report rows are required")
    if (modules.isEmpty || modules.exists(x => Vector(x.organization, x.name, x.requestedVersion, x.selectedVersion, x.baseVersion).exists(_.trim.isEmpty)))
      _invalid("module coordinates and bindings must be non-empty")
    if (modules.map(_.identity).distinct.size != modules.size || modules.map(x => (x.organization, x.name)).distinct.size != modules.size)
      _invalid("duplicate or conflicting modules")
    val ids = modules.map(_.identity).toSet
    if (!modules.exists(_.development)) _invalid("selection must contain a development publication")
    if (artifacts.map(_.key).distinct.size != artifacts.size || artifacts.exists(x => !ids(x.key.module)))
      _invalid("duplicate artifact identity or unknown module")
    if (artifacts.exists(x => Vector(x.key.name, x.key.artifactType, x.key.extension).exists(_.trim.isEmpty) ||
        x.key.classifier.exists(_.trim.isEmpty))) _invalid("incomplete artifact identity")
    val keys = artifacts.map(_.key).toSet
    if (resolution.runtimeClasspath.distinct.size != resolution.runtimeClasspath.size ||
        resolution.runtimeClasspath.toSet != artifacts.filter(_.runtime).map(_.key).toSet)
      _invalid("ordered classpath must contain every runtime artifact exactly once")
    val launchpaths = artifacts.map(x => _existing_file(x.launchFile))
    if (launchpaths.distinct.size != launchpaths.size) _invalid("artifact launch paths must be distinct")
    artifacts.foreach { artifact =>
      _require_v3_no_links(artifact.launchFile)
      val resolver = _existing_file(artifact.resolverFile)
      val launch = _existing_file(artifact.launchFile)
      if (!java.util.Arrays.equals(Files.readAllBytes(resolver), Files.readAllBytes(launch)))
        _invalid("resolver and launch artifact bytes differ")
    }
    if (resolution.reportRows.exists(x => x.missingArtifacts.nonEmpty || x.problem.exists(_.trim.nonEmpty)))
      _invalid("report contains missing artifacts or a resolution problem")
    if (resolution.reportRows.exists(x => !ids(x.module) || x.revision.trim.isEmpty ||
        x.eviction.evicted && !x.eviction.selectedRevision.contains(modules.find(_.identity == x.module).get.selectedVersion) ||
        !x.eviction.evicted && x.revision != modules.find(_.identity == x.module).get.selectedVersion))
      _invalid("report row does not bind to its selected module")
    if (resolution.reportRows.exists { row =>
      val module = modules.find(_.identity == row.module).get
      row.targetModule.organization != module.organization || row.targetModule.name != module.name ||
        row.targetModule.revision != row.revision
    }) _invalid("reported target module differs from its row")
    val selectedrows = resolution.reportRows.filterNot(_.eviction.evicted)
    if (modules.exists(x => !selectedrows.exists(_.module == x.identity)))
      _invalid("selected module is absent from non-evicted report rows")
    if (selectedrows.exists(x => x.artifacts.exists(a => a.key.module != x.module || !keys(a.key) ||
        !artifacts.exists(y => y.key == a.key && _path(y.resolverFile) == _path(a.file)))))
      _invalid("report artifact is missing from the selected artifact set")
    if (artifacts.exists(x => !selectedrows.exists(_.artifacts.exists(_.key == x.key))))
      _invalid("selected artifact is absent from the report rows")
    if (edges.distinct.size != edges.size || edges.exists(x => !ids(x.from) || !ids(x.to) || x.requestedVersion.trim.isEmpty))
      _invalid("graph has duplicate edges, missing modules or missing requested versions")
    val reportededges = resolution.reportRows.flatMap(row => row.callers.filter(x => ids(x.from))
      .map(caller => Edge(caller.from, row.module, row.revision))).toSet
    def _edge_matches_report_(edge: Edge, reported: Edge): Boolean = {
      val module = modules.find(_.identity == edge.to).get
      val boundbase = module.development && edge.requestedVersion == module.baseVersion &&
        reported.requestedVersion == module.selectedVersion &&
        resolution.exactOverrides.get(s"${module.organization}:${module.name}").contains(module.selectedVersion)
      edge.from == reported.from && edge.to == reported.to &&
        (edge.requestedVersion == reported.requestedVersion || boundbase)
    }
    if (edges.exists(edge => !reportededges.exists(_edge_matches_report_(edge, _))) ||
        reportededges.exists(reported => !edges.exists(_edge_matches_report_(_, reported))))
      _invalid("graph edges differ from report caller rows")
    if (resolution.exactOverrides.exists { case (key, revision) =>
      !modules.exists(module => s"${module.organization}:${module.name}" == key && module.selectedVersion == revision)
    }) _invalid("override differs from selected module")
    if (resolution.requestRoots.isEmpty || resolution.requestRoots.distinct.size != resolution.requestRoots.size ||
        resolution.requestRoots.exists(x => !ids(x))) _invalid("invalid generator roots")
    val cozy = modules.find(x => x.organization == "org.simplemodeling" && x.name == "cozy_2.12")
      .getOrElse(_invalid("missing Cozy generator root"))
    val modeler = modules.find(x => x.organization == "org.simplemodeling" && x.name == "simplemodeler_2.12")
      .getOrElse(_invalid("missing SimpleModeler generator root"))
    if (resolution.requestRoots.toSet != Set(cozy.identity, modeler.identity))
      _invalid("generator roots must be exactly Cozy and SimpleModeler")
    var reachable = resolution.requestRoots.toSet
    var next = reachable ++ edges.filter(x => reachable(x.from)).map(_.to)
    while (next != reachable) {
      reachable = next
      next = reachable ++ edges.filter(x => reachable(x.from)).map(_.to)
    }
    if (reachable != ids) _invalid("graph contains modules outside the root closure")
    if (modules.count(x => x.organization == "org.scala-lang" && x.name == "scala-library" && x.baseVersion.startsWith("2.12.")) != 1 ||
        modules.exists(_.name == "scala3-library_3")) _invalid("generator closure requires Scala 2.12 and no Scala 3 library")
    if (cozy.baseVersion != org.simplemodeling.cozy.BuildInfo.version ||
        modeler.baseVersion != org.simplemodeling.cozy.BuildInfo.simpleModelerVersion ||
        request.settings.get("generation.versions.cozy") != Some(cozy.baseVersion))
      _invalid("base versions do not match running Cozy and its backend requirement")

    val batchpath = _existing_file(request.batchRecord)
    if (!batchpath.startsWith(publicationroot)) _invalid("batch record outside publication root")
    _require_no_links(publicationroot, batchpath)
    val batch = Json.parse(Files.readAllBytes(batchpath)).validate[BatchV3].getOrElse(_invalid("invalid v3 batch record"))
    if (!batch.complete || batch.scope != selection.scope || batch.owner != selection.owner || batch.batch != selection.batch ||
        batch.producerSelectionId.trim.isEmpty || batch.members.isEmpty ||
        batch.members.map(_.module.identity).distinct.size != batch.members.size ||
        batch.members.map(x => (x.module.organization, x.module.name)).distinct.size != batch.members.size)
      _invalid("invalid completed batch binding")
    modules.foreach { module =>
      if (module.development) {
        if (module.selectedVersion == module.baseVersion) _invalid("development publication requires a distinct alias")
        val member = batch.members.find(_.module.identity == module.identity).getOrElse(_invalid("selected publication missing from batch"))
        if (member.module.baseVersion != module.baseVersion || !module.descriptor.contains(member.module.descriptor) ||
            !module.control.contains(member.module.control)) _invalid("selected module differs from completed batch binding")
        if (!Set(module.baseVersion, module.selectedVersion).contains(module.requestedVersion) ||
            edges.filter(_.to == module.identity).exists(x => !Set(module.baseVersion, module.selectedVersion).contains(x.requestedVersion)))
          _invalid("development requested version differs from its base or immutable alias")
        artifacts.filter(_.key.module == module.identity).foreach { artifact =>
          if (!member.artifacts.exists(x => x.key == artifact.key && _path(x.publicationFile) == _path(artifact.launchFile)))
            _invalid("selected artifact differs from completed publication")
        }
      } else if (module.selectedVersion != module.baseVersion || module.control.nonEmpty || module.descriptor.nonEmpty) {
        _invalid("ordinary module must have selected/base equality and no development binding")
      }
    }
    batch.members.foreach(member => _validate_v3_batch_member(member, batch, publicationroot))
  }

  private def _validate_v3_batch_member(member: BatchMemberV3, batch: BatchV3, publicationroot: Path): Unit = {
    val module = member.module
    def _segment_(value: String): Boolean =
      value.nonEmpty && value != "." && value != ".." && !value.contains('/') && !value.contains('\\')
    if (!Vector(module.organization, module.name, module.selectedVersion, module.baseVersion).forall(_segment_) ||
        module.selectedVersion == module.baseVersion || member.artifacts.isEmpty ||
        member.artifacts.map(_.key).distinct.size != member.artifacts.size ||
        member.artifacts.exists(_.key.module != module.identity))
      _invalid("invalid development batch member or artifact set")
    val revision = publicationroot.resolve(module.organization).resolve(module.name).resolve(module.selectedVersion).normalize()
    if (!revision.startsWith(publicationroot)) _invalid("batch member outside publication root")
    _require_v3_no_links(module.descriptor)
    _require_v3_no_links(module.control)
    val descriptor = _existing_file(module.descriptor)
    val control = _existing_file(module.control)
    if (descriptor != revision.resolve("ivys/ivy.xml") || !control.startsWith(revision))
      _invalid("batch descriptor or control outside canonical publication")
    _require_no_links(publicationroot, descriptor)
    _require_no_links(publicationroot, control)
    member.artifacts.foreach { artifact =>
      val key = artifact.key
      if (!Vector(key.name, key.artifactType, key.extension).forall(_segment_) || !key.classifier.forall(_segment_))
        _invalid("invalid publication artifact identity")
      val filename = key.name + key.classifier.map("-" + _).getOrElse("") + "." + key.extension
      _require_v3_no_links(artifact.publicationFile)
      val launch = _existing_file(artifact.publicationFile)
      if (launch != revision.resolve("jars").resolve(filename)) _invalid("artifact is not in its canonical Ivy publication location")
      _require_no_links(publicationroot, launch)
    }
    val json = Json.parse(Files.readAllBytes(control))
    val expected = Json.obj("scope" -> batch.scope, "owner" -> batch.owner,
      "producerSelectionId" -> batch.producerSelectionId, "batch" -> batch.batch,
      "complete" -> true, "module" -> Json.toJson(module), "artifacts" -> Json.toJson(member.artifacts))
    expected.fields.foreach { case (key, value) =>
      if ((json \ key).toOption != Some(value)) _invalid(s"batch member control mismatch: $key")
    }
    val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
    factory.setNamespaceAware(true)
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    factory.setXIncludeAware(false)
    factory.setExpandEntityReferences(false)
    val root = try factory.newDocumentBuilder().parse(descriptor.toFile).getDocumentElement catch {
      case scala.util.control.NonFatal(_) => _invalid("invalid Ivy descriptor")
    }
    val infos = root.getElementsByTagName("info")
    if (infos.getLength != 1) _invalid("invalid Ivy descriptor info")
    val info = infos.item(0).asInstanceOf[org.w3c.dom.Element]
    if (info.getAttribute("organisation") != module.organization || info.getAttribute("module") != module.name ||
        info.getAttribute("revision") != module.selectedVersion) _invalid("Ivy descriptor coordinates differ from batch")
    val publications = root.getElementsByTagName("publications")
    if (publications.getLength != 1) _invalid("invalid Ivy publications")
    val nodes = publications.item(0).getChildNodes
    val declarations = (0 until nodes.getLength).map(nodes.item).collect {
      case element: org.w3c.dom.Element if element.getTagName == "artifact" =>
        val classifier = Option(element.getAttributeNS("http://ant.apache.org/ivy/extra", "classifier"))
          .filter(_.nonEmpty).orElse(Option(element.getAttribute("m:classifier")).filter(_.nonEmpty))
        val attributes = element.getAttributes
        val extras = (0 until attributes.getLength).map(attributes.item).filter { attribute =>
          val name = attribute.getNodeName
          name != "name" && name != "type" && name != "ext" && name != "conf" &&
            !name.startsWith("xmlns") && !(attribute.getLocalName == "classifier" &&
              attribute.getNamespaceURI == "http://ant.apache.org/ivy/extra")
        }.map(attribute => attribute.getNodeName -> attribute.getNodeValue).toMap
        (element.getAttribute("name"), element.getAttribute("type"), element.getAttribute("ext"), classifier, extras)
    }.toVector
    val declared = member.artifacts.map(x => (x.key.name, x.key.artifactType, x.key.extension, x.key.classifier, x.key.extraAttributes))
    if (declarations.distinct.size != declarations.size || declarations.toSet != declared.toSet)
      _invalid("Ivy descriptor artifact set differs from batch")
  }

  private def _require_v3_no_links(value: String): Unit = {
    val path = Paths.get(value)
    if (!path.isAbsolute) _invalid(s"path must be absolute: $value")
    val root = path.getRoot
    var current = root
    root.relativize(path).iterator().asScala.foreach { segment =>
      current = current.resolve(segment)
      if (Files.isSymbolicLink(current)) _invalid(s"symbolic link in v3 path: $current")
    }
  }

  def observeV3(request: RequestV3): ObservationV3 = {
    val actual = collectObservation()
    val resolution = request.resolution
    val artifacts = resolution.artifacts.map(x => x.key -> x).toMap
    val expected = resolution.runtimeClasspath.map(key => _path(artifacts(key).launchFile).toString)
    if (actual.classpath != expected) _invalid("actual JVM classpath differs from resolved classpath")
    val cozy = resolution.modules.find(x => x.organization == "org.simplemodeling" && x.name == "cozy_2.12")
      .getOrElse(_invalid("missing Cozy generator root"))
    val modeler = resolution.modules.find(x => x.organization == "org.simplemodeling" && x.name == "simplemodeler_2.12")
      .getOrElse(_invalid("missing SimpleModeler generator root"))
    def _owns_(module: ModuleV3, origin: Origin): Boolean =
      resolution.artifacts.filter(x => x.key.module == module.identity && x.runtime)
        .exists(x => _existing_file(x.launchFile) == _existing_file(origin.codeSource))
    if (!_owns_(cozy, actual.cozy) || !_owns_(modeler, actual.simpleModeler))
      _invalid("actual class origins differ from selected artifacts")
    if (actual.cozyVersion != cozy.baseVersion || actual.requiredSimpleModelerVersion != modeler.baseVersion ||
        actual.simpleModelerVersion != modeler.baseVersion)
      _invalid("actual generator versions differ from declared base versions")
    ObservationV3(System.getProperty("os.name"), System.getProperty("os.arch"),
      System.getProperty("java.version"), System.getProperty("java.vendor"), actual.classpath,
      actual.loaderUrls, actual.cozy, actual.simpleModeler, actual.cozyVersion,
      actual.requiredSimpleModelerVersion, actual.simpleModelerVersion)
  }

  def execute(requestPath: Path): Unit = {
    val request = Json.parse(Files.readAllBytes(requestPath)).validate[Request] match {
      case JsSuccess(value, _) => value
      case JsError(errors) => _invalid(s"invalid request: $errors")
    }
    validate(request)
    val observation = observe(request)
    val output = _path(request.roots.output)
    val result = _path(request.roots.result)
    try {
      CozySbtBridge.runDevelopmentGeneration(request.arguments, request.settings)
      val files = if (Files.isDirectory(output)) _files(output) else Vector.empty
      if (!files.exists(_.endsWith(".scala"))) _invalid("generation produced no Scala files")
      _write_result(result, Result("v2", "generated", request.selection, request.modules,
        request.edges, request.classpath, observation, files, _metadata(files), Vector.empty))
    } catch {
      case scala.util.control.NonFatal(error) =>
        val files = if (Files.isDirectory(output)) _files(output) else Vector.empty
        _write_result(result, Result("v2", "failed", request.selection, request.modules,
          request.edges, request.classpath, observation, files, _metadata(files),
          Vector(Option(error.getMessage).getOrElse(error.getClass.getName))))
        throw error
    }
  }

  def validate(request: Request): Unit = {
    if (request.version != "v2" || request.action != "generate-development")
      _invalid("expected v2 generate-development")
    val selection = request.selection
    if (Vector(selection.scope, selection.owner, selection.selectionId, selection.batch).exists(_.trim.isEmpty))
      _invalid("scope, owner, selection identity and batch must be non-empty")
    val ownerroot = _existing_dir(request.roots.owner)
    val publicationroot = _existing_dir(selection.publicationRoot)
    if (!publicationroot.startsWith(ownerroot)) _invalid("publication root must belong to owner root")
    val build = _existing_dir(request.roots.build)
    val source = _existing_dir(request.roots.source)
    val output = _path(request.roots.output)
    val result = _path(request.roots.result)
    if (!output.startsWith(build) || !result.startsWith(build) || result.startsWith(output))
      _invalid("output and result must be separate paths within the build root")
    _require_no_links(build, output)
    _require_no_links(build, result)
    if (Files.exists(output) && (!Files.isDirectory(output) || _files(output).nonEmpty))
      _invalid("output must be absent or empty")
    if (request.settings.get("sbt.project_dir").map(_path) != Some(build))
      _invalid("sbt.project_dir must equal the build root")
    if (request.arguments.size != 4 || !Set("modeler-scala", "modeler-scala-value").contains(request.arguments.head) || request.arguments(2) != "--save")
      _invalid("arguments must contain a fixed generation action, source and --save output")
    val input = _path(request.arguments(1))
    if (!Files.isRegularFile(input) || !input.toRealPath().startsWith(source) || _path(request.arguments(3)) != output)
      _invalid("generation source or output is outside its admitted root")
    val modules = request.modules
    if (modules.isEmpty || modules.exists(x => Vector(x.organization, x.name, x.requestedVersion, x.selectedVersion, x.baseVersion).exists(_.trim.isEmpty)))
      _invalid("module coordinates and bindings must be non-empty")
    if (modules.map(_.identity).distinct.size != modules.size || modules.map(x => (x.organization, x.name)).distinct.size != modules.size)
      _invalid("duplicate or conflicting modules")
    if (!modules.exists(_.development)) _invalid("selection must contain a development publication")
    val artifacts = modules.map(x => _existing_file(x.artifact))
    if (artifacts.distinct.size != artifacts.size || request.classpath.map(_existing_file) != artifacts)
      _invalid("ordered classpath must exactly match distinct resolved artifacts")
    def ownedRecord(path: String): Path = {
      val actual = _existing_file(path)
      if (!actual.startsWith(ownerroot)) _invalid("evidence outside owner root")
      actual
    }
    val resolution = Json.parse(Files.readAllBytes(ownedRecord(request.resolution))).validate[Resolution]
      .getOrElse(_invalid("invalid resolution evidence"))
    if (resolution.modules != modules || resolution.edges.toSet != request.edges.toSet ||
      resolution.edges.distinct.size != resolution.edges.size)
      _invalid("resolved modules or dependency edges differ from resolution evidence")
    val batchpath = ownedRecord(request.batchRecord)
    if (!batchpath.startsWith(publicationroot)) _invalid("batch record outside publication root")
    val batch = Json.parse(Files.readAllBytes(batchpath)).validate[Batch].getOrElse(_invalid("invalid batch record"))
    if (!batch.complete || batch.scope != selection.scope || batch.owner != selection.owner || batch.batch != selection.batch ||
      batch.producerSelectionId.trim.isEmpty || batch.members.isEmpty ||
      batch.members.map(_.identity).distinct.size != batch.members.size || batch.members.exists(!_.development))
      _invalid("invalid completed batch binding")
    if (!modules.filter(_.development).forall(batch.members.contains)) _invalid("selected publication missing from batch")
    batch.members.foreach { member =>
      val artifact = _existing_file(member.artifact)
      val publication = member.publication.map(_existing_file).getOrElse(_invalid("batch publication missing"))
      val control = member.control.map(_existing_file).getOrElse(_invalid("batch control missing"))
      if (!artifact.startsWith(publicationroot) || !control.startsWith(publicationroot) || artifact != publication)
        _invalid("batch member outside publication root")
      val json = Json.parse(Files.readAllBytes(control))
      val expected = Json.obj("scope" -> batch.scope, "owner" -> batch.owner,
        "producerSelectionId" -> batch.producerSelectionId, "batch" -> batch.batch, "complete" -> true,
        "organization" -> member.organization, "name" -> member.name, "requestedVersion" -> member.requestedVersion,
        "selectedVersion" -> member.selectedVersion, "baseVersion" -> member.baseVersion, "artifact" -> member.artifact)
      expected.fields.foreach { case (key, value) =>
        if ((json \ key).toOption != Some(value)) _invalid(s"batch member control mismatch: $key")
      }
    }
    modules.foreach { module =>
      if (module.development) {
        val publication = module.publication.map(_path).getOrElse(_invalid("development publication missing"))
        val control = module.control.map(_existing_file).getOrElse(_invalid("development control missing"))
        if (!_existing_file(module.artifact).startsWith(publicationroot) || !control.startsWith(publicationroot) || _existing_file(module.artifact) != _existing_file(publication.toString))
          _invalid("development publication or control outside publication root")
        val json = Json.parse(Files.readAllBytes(control))
        val expected = Json.obj("scope" -> selection.scope, "owner" -> selection.owner,
          "producerSelectionId" -> batch.producerSelectionId, "batch" -> selection.batch, "complete" -> true, "organization" -> module.organization,
          "name" -> module.name, "requestedVersion" -> module.requestedVersion,
          "selectedVersion" -> module.selectedVersion, "baseVersion" -> module.baseVersion,
          "artifact" -> module.artifact)
        expected.fields.foreach { case (key, value) =>
          if ((json \ key).toOption != Some(value)) _invalid(s"development control mismatch: $key")
        }
        if (module.selectedVersion == module.baseVersion) _invalid("development publication requires an explicit distinct alias")
      } else if (module.publication.nonEmpty || module.control.nonEmpty || module.selectedVersion != module.baseVersion) {
        _invalid("ordinary module must have selected/base version equality and no development binding")
      }
    }
    _validate_publication_dependencies(modules, request.edges, publicationroot)
    val ids = modules.map(_.identity).toSet
    if (request.edges.distinct.size != request.edges.size || request.edges.exists(x => !ids(x.from) || !ids(x.to)))
      _invalid("graph has duplicate edges or missing modules")
    val cozy = _root(modules, "org.simplemodeling", "cozy_2.12")
    val modeler = _root(modules, "org.simplemodeling", "simplemodeler_2.12")
    var reachable = Set(cozy.identity, modeler.identity)
    var next = reachable ++ request.edges.filter(x => reachable(x.from)).map(_.to)
    while (next != reachable) {
      reachable = next
      next = reachable ++ request.edges.filter(x => reachable(x.from)).map(_.to)
    }
    if (reachable != ids) _invalid("graph contains modules outside the root closure")
    if (modules.count(x => x.organization == "org.scala-lang" && x.name == "scala-library" && x.baseVersion.startsWith("2.12.")) != 1 || modules.exists(x => x.name == "scala3-library_3"))
      _invalid("generator closure requires one Scala 2 library and no Scala 3 library")
    if (cozy.baseVersion != org.simplemodeling.cozy.BuildInfo.version ||
      modeler.baseVersion != org.simplemodeling.cozy.BuildInfo.simpleModelerVersion ||
      request.settings.get("generation.versions.cozy") != Some(cozy.baseVersion))
      _invalid("base versions do not match running Cozy and its backend requirement")
  }

  // This checks published metadata; SBT remains responsible for version mediation.
  private def _validate_publication_dependencies(modules: Vector[Module], edges: Vector[Edge], publicationroot: Path): Unit = {
    def _children_(element: org.w3c.dom.Element, name: String): Vector[org.w3c.dom.Element] = {
      val nodes = element.getChildNodes
      (0 until nodes.getLength).map(nodes.item).collect {
        case e: org.w3c.dom.Element if e.getTagName == name => e
      }.toVector
    }
    def _single_(element: org.w3c.dom.Element, name: String): org.w3c.dom.Element = {
      val values = _children_(element, name)
      if (values.size != 1) _invalid(s"missing or ambiguous Ivy $name")
      values.head
    }
    def _attr_(element: org.w3c.dom.Element, name: String): String = {
      val value = element.getAttribute(name)
      if (value.trim.isEmpty) _invalid(s"missing Ivy $name")
      value
    }
    var required = Set.empty[Edge]
    def _add_(module: Module, deporg: String, depname: String, requested: String): Unit = {
      val selected = modules.filter(x => x.organization == deporg && x.name == depname)
      if (selected.size != 1) _invalid(s"missing published dependency: ${module.identity} -> $deporg:$depname:$requested")
      required += Edge(module.identity, selected.head.identity, requested)
    }
    modules.foreach { module =>
      val artifact = _existing_file(module.artifact)
      if (!module.development && artifact.getFileName.toString == s"${module.name}-${module.selectedVersion}.jar") {
        _maven_dependencies(module, artifact).foreach { case (deporg, depname, requested) =>
          _add_(module, deporg, depname, requested)
        }
      } else {
        val jars = artifact.getParent
        val revision = Option(jars).map(_.getParent).getOrElse(_invalid("invalid Ivy artifact path"))
        val name = Option(revision.getParent).getOrElse(_invalid("invalid Ivy artifact path"))
        val organization = Option(name.getParent).getOrElse(_invalid("invalid Ivy artifact path"))
        if (jars.getFileName.toString != "jars" || artifact.getFileName.toString != s"${module.name}.jar" ||
            revision.getFileName.toString != module.selectedVersion || name.getFileName.toString != module.name ||
            organization.getFileName.toString != module.organization)
          _invalid("artifact is not in its canonical Ivy publication location")
        val descriptor = _existing_file(revision.resolve("ivys/ivy.xml").toString)
        if (!descriptor.startsWith(revision.toRealPath()) || (module.development && !descriptor.startsWith(publicationroot)))
          _invalid("foreign Ivy descriptor")
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        factory.setXIncludeAware(false)
        factory.setExpandEntityReferences(false)
        val root = try factory.newDocumentBuilder().parse(descriptor.toFile).getDocumentElement catch {
          case scala.util.control.NonFatal(_) => _invalid("invalid Ivy descriptor")
        }
        if (root.getTagName != "ivy-module" || root.getAttribute("version") != "2.0") _invalid("unsupported Ivy descriptor")
        val info = _single_(root, "info")
        if (_attr_(info, "organisation") != module.organization || _attr_(info, "module") != module.name || _attr_(info, "revision") != module.selectedVersion)
          _invalid("Ivy publication coordinates differ from selected artifact")
        val published = _children_(_single_(root, "publications"), "artifact").filter(x =>
          x.getAttribute("name") == module.name && x.getAttribute("type") == "jar" && x.getAttribute("ext") == "jar" &&
          !x.hasAttribute("classifier") && !x.hasAttribute("e:classifier"))
        if (published.size != 1) _invalid("Ivy publication does not identify the selected jar")
        val configurations = _children_(_single_(root, "configurations"), "conf")
        val runtime = configurations.filter(_.getAttribute("name") == "runtime")
        if (runtime.size != 1 || runtime.head.getAttribute("extends") != "compile" ||
            configurations.count(_.getAttribute("name") == "compile") != 1 ||
            configurations.find(_.getAttribute("name") == "compile").exists(_.hasAttribute("extends")))
          _invalid("unsupported Ivy runtime configuration")
        val dependencysections = _children_(root, "dependencies")
        if (dependencysections.size > 1) _invalid("ambiguous Ivy dependencies")
        dependencysections.foreach { section =>
          val nodes = section.getChildNodes
          (0 until nodes.getLength).map(nodes.item).collect { case e: org.w3c.dom.Element => e }.foreach { dependency =>
            if (dependency.getTagName != "dependency") _invalid("unsupported Ivy dependency rule")
            val conf = _attr_(dependency, "conf")
            if (!conf.matches("[A-Za-z][A-Za-z0-9_-]*->[^;,\\s]+"))
              _invalid("unsupported compound Ivy dependency configuration")
            val source = conf.substring(0, conf.indexOf("->"))
            if (!Set("compile", "runtime", "provided", "test", "scala-tool", "optional").contains(source))
              _invalid("unsupported Ivy dependency configuration")
            if (source == "compile" || source == "runtime") {
              val childnodes = dependency.getChildNodes
              if ((0 until childnodes.getLength).map(childnodes.item).exists(_.isInstanceOf[org.w3c.dom.Element]))
                _invalid("unsupported Ivy runtime dependency child")
              if (!Set("compile->default(compile)", "runtime->default(runtime)", "runtime->default(compile)").contains(conf) ||
                  dependency.hasAttribute("transitive") || dependency.hasAttribute("force") ||
                  _children_(dependency, "exclude").nonEmpty || _children_(dependency, "artifact").nonEmpty)
                _invalid("unsupported Ivy runtime dependency feature")
              val deporg = _attr_(dependency, "org")
              val depname = _attr_(dependency, "name")
              val requested = _attr_(dependency, "rev")
              _add_(module, deporg, depname, requested)
            }
          }
        }
      }
    }
    if (required != edges.toSet) _invalid("missing published dependency or edge, or undeclared graph edge")
  }

  // Read only locally cached Maven metadata; this is not a Maven resolver.
  private def _maven_dependencies(module: Module, artifact: Path): Vector[(String, String, String)] = {
    val versiondir = artifact.getParent
    val namedir = Option(versiondir).map(_.getParent).getOrElse(_invalid("invalid Maven artifact path"))
    var repository = Option(namedir.getParent).getOrElse(_invalid("invalid Maven artifact path"))
    val segments = module.organization.split("\\.").toVector
    segments.reverse.foreach { segment =>
      if (!Option(repository.getFileName).exists(_.toString == segment)) _invalid("invalid Maven organization path")
      repository = Option(repository.getParent).getOrElse(_invalid("invalid Maven repository path"))
    }
    if (namedir.getFileName.toString != module.name || versiondir.getFileName.toString != module.selectedVersion)
      _invalid("invalid Maven artifact coordinates")
    def _children_(element: org.w3c.dom.Element, name: String): Vector[org.w3c.dom.Element] = {
      val nodes = element.getChildNodes
      (0 until nodes.getLength).map(nodes.item).collect {
        case e: org.w3c.dom.Element if e.getTagName == name => e
      }.toVector
    }
    def _one_(element: org.w3c.dom.Element, name: String): Option[org.w3c.dom.Element] = {
      val values = _children_(element, name)
      if (values.size > 1) _invalid(s"ambiguous Maven $name")
      values.headOption
    }
    def _text_(element: org.w3c.dom.Element, name: String): Option[String] =
      _one_(element, name).map(_.getTextContent.trim).filter(_.nonEmpty)
    def _required_(element: org.w3c.dom.Element, name: String): String =
      _text_(element, name).getOrElse(_invalid(s"missing Maven $name"))
    def _pom_(group: String, name: String, version: String): Path = {
      if (Vector(group, name, version).exists(x => x.isEmpty || x.contains("/") || x.contains("\\") || x.contains("..") || x.contains("$")))
        _invalid("unsupported Maven coordinates")
      val directory = group.split("\\.").foldLeft(repository)((p, part) => p.resolve(part)).resolve(name).resolve(version)
      val pom = _existing_file(directory.resolve(s"$name-$version.pom").toString)
      if (!pom.startsWith(repository.toRealPath()) || pom.getParent != directory.toRealPath()) _invalid("foreign Maven POM")
      pom
    }
    val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    factory.setXIncludeAware(false)
    factory.setExpandEntityReferences(false)
    def _load_(group: String, name: String, version: String, seen: Set[(String, String, String)]): MavenModel = {
      val key = (group, name, version)
      if (seen(key)) _invalid("cyclic Maven parent")
      val root = try factory.newDocumentBuilder().parse(_pom_(group, name, version).toFile).getDocumentElement catch {
        case scala.util.control.NonFatal(_) => _invalid("invalid or missing Maven POM")
      }
      if (root.getTagName != "project" || _one_(root, "profiles").nonEmpty) _invalid("unsupported Maven project or profiles")
      if (_one_(root, "distributionManagement").exists(x => _one_(x, "relocation").nonEmpty))
        _invalid("unsupported Maven relocation")
      val parentnode = _one_(root, "parent")
      val parent = parentnode.map { p =>
        _load_(_required_(p, "groupId"), _required_(p, "artifactId"), _required_(p, "version"), seen + key)
      }.getOrElse(MavenModel(Map.empty, Map.empty, Map.empty))
      val actualgroup = _text_(root, "groupId").orElse(parentnode.map(_required_(_, "groupId"))).getOrElse(_invalid("missing Maven groupId"))
      val actualversion = _text_(root, "version").orElse(parentnode.map(_required_(_, "version"))).getOrElse(_invalid("missing Maven version"))
      if (actualgroup != group || _required_(root, "artifactId") != name || actualversion != version)
        _invalid("Maven POM coordinates differ from artifact")
      val declared = _one_(root, "properties").toVector.flatMap { element =>
        val nodes = element.getChildNodes
        (0 until nodes.getLength).map(nodes.item).collect { case e: org.w3c.dom.Element => e.getTagName -> e.getTextContent.trim }
      }.toMap
      if (parentnode.nonEmpty && declared.keySet.exists(parent.properties.contains))
        _invalid("unsupported Maven inherited property override")
      if (key == ((module.organization, module.name, module.selectedVersion)) && _text_(root, "packaging").exists(_ != "jar"))
        _invalid("Maven artifact is not a jar publication")
      val properties = parent.properties ++ declared ++ Map("project.groupId" -> group, "project.artifactId" -> name,
        "project.version" -> version, "pom.groupId" -> group, "pom.version" -> version)
      def _resolve_(value: String): String = {
        val pattern = "\\$\\{([^}]+)\\}".r
        var result = value
        var count = 0
        while (pattern.findFirstIn(result).nonEmpty && count < 32) {
          result = pattern.replaceAllIn(result, m => java.util.regex.Matcher.quoteReplacement(properties.getOrElse(m.group(1), _invalid("unresolved Maven property"))))
          count += 1
        }
        if (result.contains("$") || result.isEmpty || result.startsWith("[") || result.startsWith("(")) _invalid("unsupported Maven value")
        result
      }
      def _entries_(section: org.w3c.dom.Element, managed: Map[(String, String), String], management: Boolean): Map[(String, String), (String, String, String)] = {
        val declarations = _children_(section, "dependency").map { dependency =>
          val nodes = dependency.getChildNodes
          val supported = Set("groupId", "artifactId", "version", "scope", "optional", "type")
          if ((0 until nodes.getLength).map(nodes.item).collect { case e: org.w3c.dom.Element => e }.exists(e => !supported(e.getTagName)))
            _invalid("unsupported Maven dependency element")
          if (_one_(dependency, "exclusions").nonEmpty || _one_(dependency, "classifier").nonEmpty ||
              _text_(dependency, "type").exists(_ != "jar")) _invalid("unsupported Maven dependency feature")
          val depgroup = _resolve_(_required_(dependency, "groupId"))
          val depname = _resolve_(_required_(dependency, "artifactId"))
          val scope = _text_(dependency, "scope").map(_resolve_).getOrElse("compile")
          val optional = _text_(dependency, "optional").map(_resolve_).getOrElse("false")
          if (!Set("compile", "runtime", "provided", "test", "system").contains(scope) || !Set("true", "false").contains(optional) ||
              (management && (scope != "compile" || optional != "false")))
            _invalid("unsupported Maven scope or optional value")
          val depversion = _text_(dependency, "version").map(_resolve_).orElse(managed.get((depgroup, depname)))
          if ((management || (Set("compile", "runtime").contains(scope) && optional == "false")) && depversion.isEmpty)
            _invalid("missing Maven dependency version")
          (depgroup, depname) -> (scope, optional, depversion.getOrElse(""))
        }
        if (declarations.map(_._1).distinct.size != declarations.size) _invalid("ambiguous Maven dependency")
        declarations.toMap
      }
      val managedentries = _one_(root, "dependencyManagement").toVector.flatMap(x => _one_(x, "dependencies").toVector)
        .flatMap(x => _entries_(x, parent.managed, true)).toMap
      val managed = parent.managed ++ managedentries.map { case (key, (_, _, revision)) => key -> revision }
      val entries = _one_(root, "dependencies").map(_entries_(_, managed, false)).getOrElse(Map.empty)
      MavenModel(properties, managed, parent.dependencies ++ entries)
    }
    val model = _load_(module.organization, module.name, module.selectedVersion, Set.empty)
    model.dependencies.toVector.collect { case ((group, name), (scope, "false", version)) if scope == "compile" || scope == "runtime" =>
      (group, name, version)
    }
  }

  def collectObservation(): Observation = {
    val cozyorigin = _origin(classOf[cozy.Cozy])
    val modeler = _origin(Class.forName("org.simplemodeling.SimpleModeler.generator.scala.Scala3ClassGeneratorExecutor"))
    val actual = System.getProperty("java.class.path").split(File.pathSeparator).toVector.filter(_.nonEmpty).map(x => _path(x).toString)
    val jar = new JarFile(_path(modeler.codeSource).toFile)
    val version = try Option(jar.getManifest).flatMap(x => Option(x.getMainAttributes.getValue("Implementation-Version"))).getOrElse(_invalid("backend artifact has no Implementation-Version")) finally jar.close()
    val loaderurls = Iterator.iterate(Option(classOf[cozy.Cozy].getClassLoader))(_.flatMap(x => Option(x.getParent)))
      .takeWhile(_.nonEmpty).flatMap(_.toVector).flatMap {
        case loader: java.net.URLClassLoader => loader.getURLs.toVector.map(_.toExternalForm)
        case _ => Vector.empty[String]
      }.toVector
    Observation(actual, loaderurls, cozyorigin, modeler, org.simplemodeling.cozy.BuildInfo.version,
      org.simplemodeling.cozy.BuildInfo.simpleModelerVersion, version)
  }

  def observe(request: Request): Observation = {
    val observation = collectObservation()
    val cozymodule = _root(request.modules, "org.simplemodeling", "cozy_2.12")
    val modelermodule = _root(request.modules, "org.simplemodeling", "simplemodeler_2.12")
    if (_path(observation.cozy.codeSource).toRealPath() != _existing_file(cozymodule.artifact) ||
      _path(observation.simpleModeler.codeSource).toRealPath() != _existing_file(modelermodule.artifact))
      _invalid("actual class origins differ from selected artifacts")
    if (observation.classpath != request.classpath.map(x => _path(x).toString))
      _invalid("actual JVM classpath differs from resolved classpath")
    if (observation.simpleModelerVersion != modelermodule.baseVersion)
      _invalid("backend artifact version differs from its declared base version")
    observation
  }

  private def _metadata(files: Vector[String]): Vector[String] =
    files.filter(x => x.endsWith(".json") || x.endsWith(".yaml") || x.endsWith(".yml"))

  private def _write_result(path: Path, result: Result): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, Json.prettyPrint(Json.toJson(result)).getBytes(StandardCharsets.UTF_8))
  }

  private def _root(modules: Vector[Module], organization: String, name: String): Module =
    modules.find(x => x.organization == organization && x.name == name).getOrElse(_invalid(s"missing generator root $organization:$name"))

  private def _origin(clazz: Class[_]): Origin = {
    val location = Option(clazz.getProtectionDomain).flatMap(x => Option(x.getCodeSource)).map(_.getLocation).getOrElse(_invalid(s"missing CodeSource for ${clazz.getName}"))
    val resources = clazz.getClassLoader.getResources(clazz.getName.replace('.', '/') + ".class").asScala.toVector
    if (resources.size != 1) _invalid(s"class resource is missing or ambiguous: ${clazz.getName}")
    val resource = Option(clazz.getResource("/" + clazz.getName.replace('.', '/') + ".class")).getOrElse(_invalid(s"missing resource for ${clazz.getName}"))
    Origin(clazz.getName, Paths.get(location.toURI).toAbsolutePath.normalize.toString, resource.toExternalForm)
  }

  private def _path(value: String): Path = {
    val path = Paths.get(value)
    if (!path.isAbsolute) _invalid(s"path must be absolute: $value")
    path.normalize()
  }
  private def _require_no_links(root: Path, path: Path): Unit = {
    var current = root
    root.relativize(path).iterator().asScala.foreach { segment =>
      current = current.resolve(segment)
      if (Files.isSymbolicLink(current)) _invalid(s"symbolic link in output path: $current")
    }
  }
  private def _existing_file(value: String): Path = {
    val path = _path(value)
    if (!Files.isRegularFile(path)) _invalid(s"file does not exist: $path")
    path.toRealPath()
  }
  private def _existing_dir(value: String): Path = {
    val path = _path(value)
    if (!Files.isDirectory(path)) _invalid(s"directory does not exist: $path")
    path.toRealPath()
  }
  private def _files(root: Path): Vector[String] = {
    val stream = Files.walk(root)
    try stream.iterator.asScala.filter(Files.isRegularFile(_)).map(x => root.relativize(x).toString.replace('\\', '/')).toVector.sorted
    finally stream.close()
  }
  private def _invalid(detail: String): Nothing = RAISE.invalidArgumentFault(s"DEVELOPMENT_GENERATION_INVALID: $detail")
}
