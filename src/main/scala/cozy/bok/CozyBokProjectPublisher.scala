package cozy.bok

import org.goldenport.RAISE
import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.io.InputSource
import cozy.archive.{RepositoryArtifactCatalog, RepositoryArtifactCatalogVersion}
import cozy.config.CozyProjectYamlConfig
import cozy.modeler.CmlModelMetadata
import cozy.publication.CozyPublicationCompiler
import io.circe.{Decoder, HCursor}
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}
import java.net.URI
import java.nio.file.{Files, Path, Paths}
import java.security.MessageDigest
import scala.collection.JavaConverters._

/*
 * @since   Jun. 23, 2026
 *  version Jun. 24, 2026
 * @version Jul. 13, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyBokProjectPublisher {
  private val _schema = "cozy.bok.project.v1"
  private val _descriptor_names = Vector("project.yaml", "project.yml", "project.json")
  private val _slug_pattern = "^[a-z0-9][a-z0-9-]*$".r

  final case class PublishProjectConfig(
    packagedir: Path,
    savedir: Path,
    warehousedir: Path,
    version: Option[String],
    force: Boolean,
    bokprojectdir: Path,
    bokconfig: CozyProjectYamlConfig.Config,
    repositorydir: Option[Path] = None
  )

  final case class ProjectDescriptor(
    project: ProjectSection,
    car: Option[CarSection],
    cml: Option[CmlSection],
    sie: Option[SieSection],
    title: Option[String],
    version: Option[String],
    summary: Option[String],
    terms: Vector[String],
    tags: Vector[String],
    article: Option[String],
    publication: Option[PublicationSection]
  )
  object ProjectDescriptor {
    implicit val decoder: Decoder[ProjectDescriptor] = (c: HCursor) =>
      for {
        project <- c.downField("project").as[ProjectSection]
        car <- c.downField("car").as[Option[CarSection]]
        cml <- c.downField("cml").as[Option[CmlSection]]
        sie <- c.downField("sie").as[Option[SieSection]]
        title <- c.downField("title").as[Option[String]]
        version <- c.downField("version").as[Option[String]]
        summary <- c.downField("summary").as[Option[String]]
        terms <- c.downField("terms").as[Option[Vector[String]]]
        tags <- c.downField("tags").as[Option[Vector[String]]]
        article <- c.downField("article").as[Option[String]]
        publication <- c.downField("publication").as[Option[PublicationSection]]
      } yield ProjectDescriptor(project, car, cml, sie, title, version, summary, terms.getOrElse(Vector.empty), tags.getOrElse(Vector.empty), article, publication)
  }

  final case class ProjectSection(
    projecttype: String,
    name: String,
    mode: Option[String],
    ref: Option[String]
  )
  object ProjectSection {
    implicit val decoder: Decoder[ProjectSection] = (c: HCursor) =>
      for {
        projecttype <- c.downField("type").as[String]
        name <- c.downField("name").as[String]
        mode <- c.downField("mode").as[Option[String]]
        ref <- c.downField("ref").as[Option[String]]
      } yield ProjectSection(projecttype, name, mode, ref)
  }

  final case class CarSection(module: Option[String])
  object CarSection {
    implicit val decoder: Decoder[CarSection] = (c: HCursor) =>
      c.downField("module").as[Option[String]].map(CarSection.apply)
  }

  final case class CmlSection(source: Option[String], glossary: Option[CmlGlossarySection])
  object CmlSection {
    implicit val decoder: Decoder[CmlSection] = (c: HCursor) =>
      for {
        source <- c.downField("source").as[Option[String]]
        glossary <- c.downField("glossary").as[Option[CmlGlossarySection]]
      } yield CmlSection(source, glossary)
  }

  final case class CmlGlossarySection(category: Option[String])
  object CmlGlossarySection {
    implicit val decoder: Decoder[CmlGlossarySection] = (c: HCursor) =>
      c.downField("category").as[Option[String]].map(CmlGlossarySection.apply)
  }

  final case class SieSection(
    projection: String,
    component: Option[String],
    handoffbase: String
  )
  object SieSection {
    implicit val decoder: Decoder[SieSection] = (c: HCursor) =>
      for {
        projection <- c.downField("projection").as[String]
        component <- c.downField("component").as[Option[String]]
        handoffbase <- c.downField("handoff_base").as[String]
      } yield SieSection(projection, component, handoffbase)
  }

  final case class PublicationSection(path: Option[String])
  object PublicationSection {
    implicit val decoder: Decoder[PublicationSection] = (c: HCursor) =>
      c.downField("path").as[Option[String]].map(PublicationSection.apply)
  }

  final case class ResolvedBokProject(
    packagedir: Path,
    slug: String,
    descriptorfile: Path,
    descriptor: ProjectDescriptor,
    name: String,
    title: String,
    version: String,
    summary: Option[String],
    article: Path,
    articlepath: String,
    publicationpath: String,
    projectmode: String,
    projectref: Option[String],
    projectpath: Option[Path],
    reference: ProjectReference,
    module: String,
    versionsource: String,
    terms: Vector[String],
    tags: Vector[String],
    catalog: Option[ProjectCatalogInfo],
    cml: Option[ProjectCmlInfo],
    sie: Option[ProjectSieInfo]
  ) {
    def warehousePath: String =
      catalog.flatMap(_.selectedversion).flatMap(_.file).getOrElse(s"repository/car/${module}/${version}/${module}-${version}.car")
    def publicPath: String =
      warehousePath
  }

  final case class ProjectCatalogInfo(
    path: Path,
    catalog: RepositoryArtifactCatalog,
    selectedversion: Option[RepositoryArtifactCatalogVersion]
  )

  final case class ProjectSieInfo(
    projection: String,
    component: Option[String],
    handoffbase: String,
    manifest: String
  )

  final case class ProjectReference(
    mode: String,
    projectpath: Option[Path],
    repositorydir: Option[Path]
  ) {
    def isSourceOnly: Boolean = mode == "path"
  }

  final case class ProjectCmlInfo(
    sourcepath: Path,
    sourceprojectrelativepath: String,
    glossarycategory: String,
    sourcekind: String,
    modelmetadatapath: Option[Path],
    surface: CmlProjectSurface,
    modelElements: Vector[CmlModelElement]
  )

  final case class CmlProjectSurface(
    component: Option[CmlComponentSurface]
  )

  final case class CmlComponentSurface(
    name: String,
    termid: String,
    glossarypath: String,
    descriptive: CmlDescriptive,
    narrative: Option[String],
    services: Vector[CmlServiceSurface]
  )

  final case class CmlServiceSurface(
    name: String,
    termid: String,
    glossarypath: String,
    descriptive: CmlDescriptive,
    narrative: Option[String],
    operations: Vector[CmlOperationSurface]
  )

  final case class CmlOperationSurface(
    name: String,
    termid: String,
    glossarypath: String,
    descriptive: CmlDescriptive,
    narrative: Option[String],
    operationtype: Option[String],
    inputtype: Option[String],
    outputtype: Option[String],
    implementation: Vector[String]
  )

  final case class CmlModelElement(
    kind: String,
    name: String,
    termid: String,
    glossarypath: String,
    descriptive: CmlDescriptive,
    narrative: Option[String]
  )

  final case class CmlDescriptive(
    label: String,
    brief: Option[String],
    summary: Option[String],
    description: Option[String]
  )

  final case class PublishProjectResult(
    project: ResolvedBokProject,
    artifact: Path,
    artifactexists: Boolean,
    publicationbundle: Path
  )

  def publish(config: PublishProjectConfig): PublishProjectResult = {
    val project = resolve(config)
    val artifact = _repository_artifact_path(config, project.reference, project.warehousePath)
    _publish_metadata(config, project, artifact)
  }

  def artifactPath(config: PublishProjectConfig, warehousepath: String): Path =
    _repository_artifact_path(config, ProjectReference("public", None, config.repositorydir), warehousepath)

  def resolve(config: PublishProjectConfig): ResolvedBokProject = {
    val packagedir = config.packagedir.toAbsolutePath.normalize()
    _validate_package_dir(config, packagedir)
    val descriptorfile = _descriptor_file(packagedir)
    val descriptor = StructuredDocumentLoader.loadDocument[ProjectDescriptor](InputSource(descriptorfile.toFile)).take
    if (descriptor.project.projecttype != "car")
      RAISE.invalidArgumentFault(s"Unsupported project.type in BoK project package: ${descriptor.project.projecttype}")
    val slug = _validate_slug(packagedir.getFileName.toString, "project slug")
    val category = _project_category(config, packagedir)
    val name = _validate_slug(descriptor.project.name, "project.name")
    val title = descriptor.title.map(_.trim).filter(_.nonEmpty).getOrElse(name)
    val articlepath = _relative_path(descriptor.article.getOrElse("index.dox"), "article")
    val article = packagedir.resolve(articlepath).normalize()
    if (!Files.isRegularFile(article))
      RAISE.invalidArgumentFault(s"Missing CAR project article source: $article")
    val publicationpath = _validate_publication_path(descriptor.publication.flatMap(_.path).getOrElse(s"projects/${category}/${slug}"))
    val mode = descriptor.project.mode.map(_.trim).filter(_.nonEmpty).getOrElse("internal")
    if (mode != "internal" && mode != "external")
      RAISE.invalidArgumentFault(s"Unsupported project.mode: $mode")
    val module = _resolve_project_car(config, descriptor, name)
    val reference = _resolve_project_reference(config, mode, descriptor.project.ref, module)
    val catalog = _load_catalog(config, reference, module)
    val catalogversion = catalog.flatMap { case (_, c) => _catalog_effective_version(c) }
    val artifactversion = _repository_latest_artifact_version(config, reference, module)
    val configuredversion = _resolve_project_version(config, descriptor)
    val explicitversion = config.version.orElse(configuredversion).map(_.trim).filter(_.nonEmpty)
    val descriptorversion = descriptor.version.map(_.trim).filter(_.nonEmpty)
    val version = explicitversion.orElse(catalogversion).orElse(artifactversion).orElse(descriptorversion).getOrElse("0.0.0-SNAPSHOT")
    val cataloginfo = catalog.map {
      case (path, c) => ProjectCatalogInfo(path, c, c.versions.find(_.version == version))
    }
    val versionsource =
      if (config.version.nonEmpty) "cli"
      else if (configuredversion.nonEmpty) "project-config"
      else if (catalogversion.nonEmpty) "repository-catalog"
      else if (artifactversion.nonEmpty) "repository-artifact"
      else if (descriptorversion.nonEmpty) "descriptor"
      else "default"
    val cml = _resolve_cml_info(config, descriptor, reference, module)
    val sie = descriptor.sie.map(_resolve_sie_info)
    ResolvedBokProject(
      packagedir,
      slug,
      descriptorfile,
      descriptor,
      name,
      title,
      version,
      descriptor.summary.map(_.trim).filter(_.nonEmpty),
      article,
      articlepath,
      publicationpath,
      mode,
      descriptor.project.ref.map(_.trim).filter(_.nonEmpty),
      reference.projectpath,
      reference,
      module,
      versionsource,
      descriptor.terms.map(_.trim).filter(_.nonEmpty).distinct,
      descriptor.tags.map(_.trim).filter(_.nonEmpty).distinct,
      cataloginfo,
      cml,
      sie
    )
  }

  private def _publish_metadata(
    config: PublishProjectConfig,
    project: ResolvedBokProject,
    artifact: Path
  ): PublishProjectResult = {
    val exists = Files.isRegularFile(artifact)
    CozyPublicationCompiler.publishMetadata(
      config.savedir,
      project.name,
      Some(project.publicationpath),
      project.packagedir,
      Vector(
        s"metadata/catalog/projects/car/${project.name}.json" -> _catalog_json(project),
        s"metadata/projects/car/${project.name}/metadata.json" -> _project_metadata_json(config, project, artifact, exists),
        s"metadata/projects/car/${project.name}/${project.version}/manifest.json" -> _manifest_json(config, project, artifact, exists),
        s"metadata/projects/car/${project.name}/latest.json" -> _latest_json(project),
        s"metadata/artifacts/repository/${project.name}.json" -> _artifact_json(config, project, artifact, exists)
      )
    )
    PublishProjectResult(project, artifact, exists, config.savedir.resolve(s"${project.name}.json"))
  }

  private def _catalog_json(project: ResolvedBokProject): JsValue =
    Json.obj(
      "schema" -> _schema,
      "type" -> "catalog-bok-project",
      "project" -> Json.obj(
        "type" -> "car",
        "name" -> project.name,
        "title" -> project.title,
        "version" -> project.version,
        "metadata" -> s"metadata/projects/car/${project.name}/metadata",
        "publicationPath" -> project.publicationpath
      )
    )

  private def _project_metadata_json(config: PublishProjectConfig, project: ResolvedBokProject, artifact: Path, exists: Boolean): JsValue =
    Json.obj(
      "schema" -> _schema,
      "type" -> "bok-project-publication",
      "project" -> (Json.obj(
        "type" -> "car",
        "name" -> project.name,
        "title" -> project.title,
        "version" -> project.version,
        "summary" -> Json.toJson(project.summary.getOrElse("")),
        "terms" -> Json.toJson(project.terms),
        "tags" -> Json.toJson(project.tags),
        "articlePath" -> project.articlepath,
        "publicationPath" -> project.publicationpath,
        "sourcePackage" -> _project_relative_path(config.bokprojectdir, project.packagedir),
        "descriptorPath" -> project.descriptorfile.getFileName.toString,
        "descriptorSha256" -> _sha256(project.descriptorfile),
        "project" -> Json.obj(
          "mode" -> project.projectmode,
          "ref" -> Json.toJson(project.projectref.getOrElse(""))
        ),
        "car" -> Json.obj(
          "module" -> project.module
        ),
        "versionSource" -> project.versionsource,
        "catalog" -> _catalog_summary_json(config, project),
        "cml" -> _cml_json(config, project),
        "sie" -> _sie_json(project),
        "artifact" -> _artifact_file_json(project, artifact, exists)
      ))
    )

  private def _manifest_json(config: PublishProjectConfig, project: ResolvedBokProject, artifact: Path, exists: Boolean): JsValue =
    Json.obj(
      "schema" -> _schema,
      "type" -> "bok-project-registry-manifest",
      "project" -> Json.obj(
        "name" -> project.name,
        "title" -> project.title,
        "version" -> project.version,
        "metadataPath" -> s"metadata/projects/car/${project.name}/metadata",
        "latestPath" -> s"metadata/projects/car/${project.name}/latest"
      ),
      "artifact" -> _artifact_file_json(project, artifact, exists),
      "catalog" -> _catalog_summary_json(config, project),
      "cml" -> _cml_json(config, project),
      "sie" -> _sie_json(project),
      "diagnostics" -> JsArray(_diagnostics(project, exists))
    )

  private def _latest_json(project: ResolvedBokProject): JsValue =
    Json.obj(
      "schema" -> _schema,
      "type" -> "bok-project-latest",
      "project" -> Json.obj(
        "name" -> project.name,
        "version" -> project.version,
        "metadataPath" -> s"metadata/projects/car/${project.name}/metadata",
        "manifestPath" -> s"metadata/projects/car/${project.name}/${project.version}/manifest"
      )
    )

  private def _artifact_json(config: PublishProjectConfig, project: ResolvedBokProject, artifact: Path, exists: Boolean): JsValue = {
    val files = project.catalog.map { info =>
      info.catalog.versions.map(_catalog_artifact_file_json(config, project, _))
    }.getOrElse(Vector(_artifact_file_json(project, artifact, exists)))
    val status =
      if (project.reference.isSourceOnly) "source-only"
      else if (files.nonEmpty && files.forall(file => (file \ "status").asOpt[String].contains("published"))) "published"
      else "missing"
    Json.obj(
      "schema" -> _schema,
      "type" -> "repository-artifact",
      "project" -> Json.obj(
        "name" -> project.name,
        "title" -> project.title,
        "kind" -> "car",
        "version" -> project.version
      ),
      "artifact" -> Json.obj(
        "layer" -> "repository",
        "status" -> status,
        "kinds" -> Json.arr(Json.obj(
          "type" -> "car",
          "versions" -> Json.toJson(files.map(file => (file \ "version").as[String])),
          "latestRelease" -> project.version
        )),
        "files" -> JsArray(files)
      )
    )
  }

  private def _artifact_file_json(project: ResolvedBokProject, artifact: Path, exists: Boolean): JsObject = {
    val base = Json.obj(
      "layer" -> "repository",
      "type" -> "car",
      "module" -> project.module,
      "artifactId" -> project.module,
      "version" -> project.version,
      "extension" -> "car",
      "warehousePath" -> project.warehousePath,
      "publicPath" -> project.publicPath,
      "name" -> artifact.getFileName.toString,
      "expected" -> (!exists && !project.reference.isSourceOnly),
      "status" -> (if (project.reference.isSourceOnly && !exists) "undistributed" else if (exists) "published" else "missing"),
      "sourceOnly" -> project.reference.isSourceOnly
    )
    if (exists)
      base ++ Json.obj(
        "size" -> Files.size(artifact),
        "sha256" -> _sha256(artifact)
      )
    else
      base
  }

  private def _catalog_artifact_file_json(
    config: PublishProjectConfig,
    project: ResolvedBokProject,
    version: RepositoryArtifactCatalogVersion
  ): JsObject = {
    val warehousepath = version.file.getOrElse(s"repository/car/${project.module}/${version.version}/${project.module}-${version.version}.car")
    val artifact = _repository_artifact_path(config, project.reference, warehousepath)
    val exists = Files.isRegularFile(artifact)
    val base = Json.obj(
      "layer" -> "repository",
      "type" -> "car",
      "module" -> project.module,
      "artifactId" -> project.module,
      "version" -> version.version,
      "extension" -> "car",
      "warehousePath" -> warehousepath,
      "publicPath" -> warehousepath,
      "name" -> Paths.get(warehousepath).getFileName.toString,
      "channel" -> Json.toJson(version.channel.getOrElse("")),
      "catalogStatus" -> Json.toJson(version.status.getOrElse("active")),
      "publishedAt" -> Json.toJson(version.publishedAt.getOrElse("")),
      "status" -> (if (exists) "published" else "missing")
    )
    val withchecksum = version.checksumSha256.map(value => base ++ Json.obj("sha256" -> value)).getOrElse(base)
    if (exists)
      withchecksum ++ Json.obj("size" -> Files.size(artifact))
    else
      withchecksum
  }

  private def _catalog_summary_json(config: PublishProjectConfig, project: ResolvedBokProject): JsValue =
    project.catalog.map { info =>
      Json.obj(
        "source" -> "repository-catalog",
        "path" -> _warehouse_relative_path(config, project.reference, info.path),
        "artifactId" -> info.catalog.artifactId,
        "status" -> Json.toJson(info.catalog.status.getOrElse("")),
        "recommended" -> Json.toJson(info.catalog.recommended.getOrElse("")),
        "latestStable" -> Json.toJson(info.catalog.latestStable.getOrElse("")),
        "latestSnapshot" -> Json.toJson(info.catalog.latestSnapshot.getOrElse("")),
        "selectedVersion" -> Json.toJson(info.selectedversion.map(_.version).getOrElse("")),
        "versions" -> JsArray(info.catalog.versions.map { version =>
          Json.obj(
            "version" -> version.version,
            "channel" -> Json.toJson(version.channel.getOrElse("")),
            "status" -> Json.toJson(version.status.getOrElse("active")),
            "file" -> Json.toJson(version.file.getOrElse(""))
          )
        })
      )
    }.getOrElse(Json.obj("source" -> "descriptor"))

  private def _cml_json(config: PublishProjectConfig, project: ResolvedBokProject): JsValue =
    project.cml.map { cml =>
      Json.obj(
        "sourcePath" -> _cml_public_source_path(config, project, cml),
        "projectRelativePath" -> cml.sourceprojectrelativepath,
        "sourceKind" -> cml.sourcekind,
        "modelMetadataPath" -> Json.toJson(cml.modelmetadatapath.map(_warehouse_relative_path(config, project.reference, _)).getOrElse("")),
        "glossaryCategory" -> cml.glossarycategory,
        "surface" -> _cml_surface_json(cml.surface),
        "modelElements" -> JsArray(cml.modelElements.map(_cml_model_element_json))
      )
    }.getOrElse(Json.obj(
      "status" -> "missing",
      "message" -> "CML source is not registered for this CAR project."
    ))

  private def _sie_json(project: ResolvedBokProject): JsValue =
    project.sie.map { sie =>
      Json.obj(
        "status" -> "registered",
        "projection" -> sie.projection,
        "component" -> Json.toJson(sie.component.getOrElse("")),
        "handoffBase" -> sie.handoffbase,
        "manifest" -> sie.manifest
      )
    }.getOrElse(Json.obj("status" -> "not-configured"))

  private def _cml_surface_json(surface: CmlProjectSurface): JsObject =
    surface.component match {
      case Some(x) => Json.obj("component" -> _cml_component_json(x))
      case None => Json.obj("component" -> Json.obj())
    }

  private def _cml_component_json(component: CmlComponentSurface): JsObject =
    Json.obj(
      "name" -> component.name,
      "termId" -> component.termid,
      "glossaryPath" -> component.glossarypath,
      "descriptive" -> _cml_descriptive_json(component.descriptive),
      "narrative" -> Json.toJson(component.narrative.getOrElse("")),
      "services" -> JsArray(component.services.map(_cml_service_json))
    )

  private def _cml_service_json(service: CmlServiceSurface): JsObject =
    Json.obj(
      "name" -> service.name,
      "termId" -> service.termid,
      "glossaryPath" -> service.glossarypath,
      "descriptive" -> _cml_descriptive_json(service.descriptive),
      "narrative" -> Json.toJson(service.narrative.getOrElse("")),
      "operations" -> JsArray(service.operations.map(_cml_operation_json))
    )

  private def _cml_operation_json(operation: CmlOperationSurface): JsObject =
    Json.obj(
      "name" -> operation.name,
      "termId" -> operation.termid,
      "glossaryPath" -> operation.glossarypath,
      "descriptive" -> _cml_descriptive_json(operation.descriptive),
      "narrative" -> Json.toJson(operation.narrative.getOrElse("")),
      "operationType" -> Json.toJson(operation.operationtype.getOrElse("")),
      "inputType" -> Json.toJson(operation.inputtype.getOrElse("")),
      "outputType" -> Json.toJson(operation.outputtype.getOrElse("")),
      "implementation" -> Json.toJson(operation.implementation)
    )

  private def _cml_model_element_json(element: CmlModelElement): JsObject =
    Json.obj(
      "kind" -> element.kind,
      "name" -> element.name,
      "termId" -> element.termid,
      "glossaryPath" -> element.glossarypath,
      "descriptive" -> _cml_descriptive_json(element.descriptive),
      "narrative" -> Json.toJson(element.narrative.getOrElse(""))
    )

  private def _cml_descriptive_json(descriptive: CmlDescriptive): JsObject =
    Json.obj(
      "label" -> descriptive.label,
      "brief" -> Json.toJson(descriptive.brief.getOrElse("")),
      "summary" -> Json.toJson(descriptive.summary.getOrElse("")),
      "description" -> Json.toJson(descriptive.description.getOrElse(""))
    )

  private def _diagnostics(project: ResolvedBokProject, exists: Boolean): Vector[JsObject] =
    if (exists || project.reference.isSourceOnly)
      Vector.empty
    else {
      val projectdir = project.projectref.map(ref => s"<${ref}>").getOrElse("<project-dir>")
      Vector(Json.obj(
        "severity" -> "warning",
        "message" -> s"CAR artifact is not registered in artifact repository: ${project.warehousePath}",
        "action" -> s"Run cozy publish-car ${projectdir} --warehouse <warehouse-dir> --name ${project.module} --version ${project.version}"
      ))
    }


  private def _repository_latest_artifact_version(
    config: PublishProjectConfig,
    reference: ProjectReference,
    module: String
  ): Option[String] =
    _repository_dir(config, reference).flatMap { repositorydir =>
      val moduledir = repositorydir.resolve(s"car/${module}").toAbsolutePath.normalize()
      if (!Files.isDirectory(moduledir))
        None
      else {
        val stream = Files.list(moduledir)
        try {
          val versions = stream.iterator().asScala.toVector.filter(Files.isDirectory(_)).map(_.getFileName.toString).filter(_.trim.nonEmpty).filter { version =>
            Files.isRegularFile(moduledir.resolve(s"${version}/${module}-${version}.car"))
          }
          versions.sorted.lastOption
        } finally {
          stream.close()
        }
      }
    }

  private def _load_catalog(config: PublishProjectConfig, reference: ProjectReference, module: String): Option[(Path, RepositoryArtifactCatalog)] = {
    val path = _repository_catalog_dir(config, reference).resolve(s"$module.yaml").toAbsolutePath.normalize()
    if (Files.isRegularFile(path))
      Some(path -> RepositoryArtifactCatalog.load(path))
    else
      None
  }

  private def _resolve_cml_info(
    config: PublishProjectConfig,
    descriptor: ProjectDescriptor,
    reference: ProjectReference,
    module: String
  ): Option[ProjectCmlInfo] = {
    val glossarycategory = descriptor.cml.flatMap(_.glossary).flatMap(_.category).map(_validate_slug(_, "cml.glossary.category")).getOrElse("cml")
    _load_model_metadata(config, reference, module, glossarycategory).orElse(reference.projectpath.flatMap { projectdir =>
      val sourcerelative = descriptor.cml.flatMap(_.source).map(_relative_path(_, "cml.source")).getOrElse(s"src/main/cozy/${module}.cml")
      val sourcepath = projectdir.resolve(sourcerelative).toAbsolutePath.normalize()
      if (Files.isRegularFile(sourcepath)) {
        Some(ProjectCmlInfo(
          sourcepath,
          sourcerelative,
          glossarycategory,
          "direct-cml-scan",
          None,
          _cml_surface(sourcepath, glossarycategory),
          _cml_model_elements(sourcepath, glossarycategory)
        ))
      } else {
        None
      }
    })
  }

  private def _load_model_metadata(
    config: PublishProjectConfig,
    reference: ProjectReference,
    module: String,
    glossarycategory: String
  ): Option[ProjectCmlInfo] = {
    val catalogdir = _repository_catalog_dir(config, reference)
    val candidates = Vector(
      catalogdir.resolve(s"$module.model-metadata.json"),
      catalogdir.resolve(s"$module.model-metadata.yaml")
    )
    candidates.find(Files.isRegularFile(_)).map { path =>
      val json = StructuredDocumentLoader.loadJson(InputSource(path.toFile)).take
      val sourcepath = json.hcursor.downField("source").downField("path").as[String].getOrElse("")
      val source = if (sourcepath.trim.isEmpty) path else Paths.get(sourcepath).toAbsolutePath.normalize()
      val surface = _read_cml_surface(json.hcursor.downField("surface"), glossarycategory)
      val elements = json.hcursor.downField("modelElements").as[Vector[io.circe.Json]].getOrElse(Vector.empty).flatMap(element =>
        _read_cml_model_element(element.hcursor, glossarycategory)
      )
      ProjectCmlInfo(
        source,
        if (sourcepath.trim.isEmpty) "" else sourcepath,
        glossarycategory,
        "repository-model-metadata",
        Some(path),
        surface,
        elements
      )
    }
  }

  private def _cml_surface(sourcepath: Path, glossarycategory: String): CmlProjectSurface =
    _convert_surface(CmlModelMetadata.fromCml(sourcepath, glossarycategory).surface)

  private def _cml_model_elements(sourcepath: Path, glossarycategory: String): Vector[CmlModelElement] = {
    CmlModelMetadata.fromCml(sourcepath, glossarycategory).modelElements.map { element =>
      CmlModelElement(
        element.kind,
        element.name,
        element.termid,
        element.glossarypath,
        CmlDescriptive(
          element.descriptive.label,
          element.descriptive.brief,
          element.descriptive.summary,
          element.descriptive.description
        ),
        element.narrative
      )
    }.distinct
  }

  private def _convert_surface(surface: CmlModelMetadata.Surface): CmlProjectSurface =
    CmlProjectSurface(surface.component.map { component =>
      CmlComponentSurface(
        component.name,
        component.termid,
        component.glossarypath,
        _convert_descriptive(component.descriptive),
        component.narrative,
        component.services.map(service =>
          CmlServiceSurface(
            service.name,
            service.termid,
            service.glossarypath,
            _convert_descriptive(service.descriptive),
            service.narrative,
            service.operations.map(operation =>
              CmlOperationSurface(
                operation.name,
                operation.termid,
                operation.glossarypath,
                _convert_descriptive(operation.descriptive),
                operation.narrative,
                operation.operationtype,
                operation.inputtype,
                operation.outputtype,
                operation.implementation
              )
            )
          )
        )
      )
    })

  private def _convert_descriptive(descriptive: CmlModelMetadata.Descriptive): CmlDescriptive =
    CmlDescriptive(
      descriptive.label,
      descriptive.brief,
      descriptive.summary,
      descriptive.description
    )

  private def _read_cml_surface(cursor: io.circe.ACursor, glossarycategory: String): CmlProjectSurface =
    CmlProjectSurface(_read_cml_component(cursor.downField("component"), glossarycategory))

  private def _read_cml_component(cursor: io.circe.ACursor, glossarycategory: String): Option[CmlComponentSurface] = {
    val name = cursor.downField("name").as[String].getOrElse("")
    if (name.trim.isEmpty)
      None
    else {
      val services = cursor.downField("services").as[Vector[io.circe.Json]].getOrElse(Vector.empty).flatMap(service =>
        _read_cml_service(service.hcursor, glossarycategory)
      )
      Some(CmlComponentSurface(
        name,
        cursor.downField("termId").as[String].getOrElse(s"${glossarycategory}:${_slugify(name)}"),
        cursor.downField("glossaryPath").as[String].getOrElse(s"glossary/${glossarycategory}/${_slugify(name)}.html"),
        _read_cml_descriptive(cursor.downField("descriptive"), name),
        cursor.downField("narrative").as[String].toOption.filter(_.trim.nonEmpty),
        services
      ))
    }
  }

  private def _read_cml_service(cursor: io.circe.ACursor, glossarycategory: String): Option[CmlServiceSurface] = {
    val name = cursor.downField("name").as[String].getOrElse("")
    if (name.trim.isEmpty)
      None
    else {
      val operations = cursor.downField("operations").as[Vector[io.circe.Json]].getOrElse(Vector.empty).flatMap(operation =>
        _read_cml_operation(operation.hcursor, glossarycategory)
      )
      Some(CmlServiceSurface(
        name,
        cursor.downField("termId").as[String].getOrElse(s"${glossarycategory}:${_slugify(name)}"),
        cursor.downField("glossaryPath").as[String].getOrElse(s"glossary/${glossarycategory}/${_slugify(name)}.html"),
        _read_cml_descriptive(cursor.downField("descriptive"), name),
        cursor.downField("narrative").as[String].toOption.filter(_.trim.nonEmpty),
        operations
      ))
    }
  }

  private def _read_cml_operation(cursor: io.circe.ACursor, glossarycategory: String): Option[CmlOperationSurface] = {
    val name = cursor.downField("name").as[String].getOrElse("")
    if (name.trim.isEmpty)
      None
    else
      Some(CmlOperationSurface(
        name,
        cursor.downField("termId").as[String].getOrElse(s"${glossarycategory}:${_slugify(name)}"),
        cursor.downField("glossaryPath").as[String].getOrElse(s"glossary/${glossarycategory}/${_slugify(name)}.html"),
        _read_cml_descriptive(cursor.downField("descriptive"), name),
        cursor.downField("narrative").as[String].toOption.filter(_.trim.nonEmpty),
        cursor.downField("operationType").as[String].toOption.filter(_.trim.nonEmpty),
        cursor.downField("inputType").as[String].toOption.filter(_.trim.nonEmpty),
        cursor.downField("outputType").as[String].toOption.filter(_.trim.nonEmpty),
        cursor.downField("implementation").as[Vector[String]].getOrElse(Vector.empty).filter(_.trim.nonEmpty)
      ))
  }

  private def _read_cml_model_element(cursor: io.circe.ACursor, glossarycategory: String): Option[CmlModelElement] = {
    val name = cursor.downField("name").as[String].getOrElse("")
    if (name.trim.isEmpty)
      None
    else {
      val kind = cursor.downField("kind").as[String].getOrElse("unknown")
      Some(CmlModelElement(
        kind,
        name,
        cursor.downField("termId").as[String].getOrElse(s"${glossarycategory}:${_slugify(name)}"),
        cursor.downField("glossaryPath").as[String].getOrElse(s"glossary/${glossarycategory}/${_slugify(name)}.html"),
        _read_cml_descriptive(cursor.downField("descriptive"), name),
        cursor.downField("narrative").as[String].toOption.filter(_.trim.nonEmpty)
      ))
    }
  }

  private def _read_cml_descriptive(cursor: io.circe.ACursor, fallbacklabel: String): CmlDescriptive =
    CmlDescriptive(
      cursor.downField("label").as[String].getOrElse(fallbacklabel),
      cursor.downField("brief").as[String].toOption.filter(_.nonEmpty),
      cursor.downField("summary").as[String].toOption.filter(_.nonEmpty),
      cursor.downField("description").as[String].toOption.filter(_.nonEmpty)
    )

  private def _catalog_effective_version(catalog: RepositoryArtifactCatalog): Option[String] = {
    val selectors = Vector(catalog.recommended, catalog.latestStable, catalog.latestSnapshot).flatten
    selectors.find(version => catalog.versions.exists(v => v.version == version && v.effectiveStatus != "disabled")).
      orElse(catalog.versions.filter(_.effectiveStatus != "disabled").lastOption.map(_.version))
  }

  private def _descriptor_file(packagedir: Path): Path = {
    val files = _descriptor_names.map(packagedir.resolve).filter(Files.isRegularFile(_))
    files match {
      case Vector(x) => x
      case Vector() => RAISE.invalidArgumentFault(s"Missing CAR project descriptor. Expected one of: ${_descriptor_names.mkString(", ")}")
      case xs => RAISE.invalidArgumentFault(s"Multiple CAR project descriptors found: ${xs.map(_.getFileName.toString).mkString(", ")}")
    }
  }

  private def _validate_package_dir(config: PublishProjectConfig, path: Path): Unit = {
    val name = path.getFileName.toString
    if (name.endsWith(".car-product") || name.endsWith(".car-product.d"))
      RAISE.invalidArgumentFault(s".car-product source packages are no longer supported. Use src/main/doxsite/projects/<category>/<slug>: $path")
    if (!Files.isDirectory(path))
      RAISE.invalidArgumentFault(s"CAR project source package directory does not exist: $path")
    _project_category(config, path)
  }

  private def _project_category(config: PublishProjectConfig, path: Path): String = {
    val projects = config.bokprojectdir.resolve("src/main/doxsite/projects").toAbsolutePath.normalize()
    val target = path.toAbsolutePath.normalize()
    if (!target.startsWith(projects))
      RAISE.invalidArgumentFault(s"BoK project package must be under src/main/doxsite/projects/<category>/<slug>: $path")
    val relative = projects.relativize(target)
    if (relative.getNameCount != 2)
      RAISE.invalidArgumentFault(s"BoK project package must be src/main/doxsite/projects/<category>/<slug>: $path")
    _validate_slug(relative.getName(0).toString, "project category")
  }



  private def _resolve_project_version(
    config: PublishProjectConfig,
    descriptor: ProjectDescriptor
  ): Option[String] =
    descriptor.project.ref.map(_.trim).filter(_.nonEmpty).flatMap(x =>
      config.bokconfig.value(s"bok.projects.${x}.version").map(_.trim).filter(_.nonEmpty)
    )

  private def _resolve_sie_info(section: SieSection): ProjectSieInfo = {
    val projection = section.projection.trim
    if (projection.isEmpty)
      RAISE.invalidArgumentFault("SIE project metadata requires sie.projection")
    val component = section.component.map(_.trim).filter(_.nonEmpty).map(_validate_slug(_, "sie.component"))
    val handoffbase = _normalize_sie_handoff_base(section.handoffbase)
    ProjectSieInfo(
      projection,
      component,
      handoffbase,
      URI.create(handoffbase).resolve("metadata/cncf/knowledge-source.json").toString
    )
  }

  private def _normalize_sie_handoff_base(value: String): String = {
    val raw = value.trim
    val uri =
      try URI.create(raw)
      catch {
        case _: IllegalArgumentException =>
          RAISE.invalidArgumentFault(s"Invalid SIE handoff base URI: $value")
      }
    val scheme = Option(uri.getScheme).map(_.toLowerCase).getOrElse("")
    val authority = Option(uri.getRawAuthority).filter(_.nonEmpty)
    if (!uri.isAbsolute || authority.isEmpty || (scheme != "http" && scheme != "https"))
      RAISE.invalidArgumentFault(s"SIE handoff base must be an absolute HTTP(S) URI: $value")
    if (uri.getRawQuery != null || uri.getRawFragment != null)
      RAISE.invalidArgumentFault(s"SIE handoff base must not contain a query or fragment: $value")
    val normalized = uri.normalize().toString
    if (normalized.endsWith("/")) normalized else normalized + "/"
  }

  private def _resolve_project_car(
    config: PublishProjectConfig,
    descriptor: ProjectDescriptor,
    projectname: String
  ): String = {
    val projectref = descriptor.project.ref.map(_.trim).filter(_.nonEmpty)
    val configured = projectref.flatMap(x => config.bokconfig.value(s"bok.projects.${x}.car"))
    _validate_slug(configured.orElse(descriptor.car.flatMap(_.module)).getOrElse(projectname), "car.module")
  }

  private def _resolve_project_reference(
    config: PublishProjectConfig,
    mode: String,
    ref: Option[String],
    module: String
  ): ProjectReference = {
    val projectref = ref.map(_.trim).filter(_.nonEmpty)
    val repository = projectref.flatMap(x => config.bokconfig.value(s"bok.projects.${x}.repository").map(_.trim).filter(_.nonEmpty)).getOrElse("public")
    repository match {
      case "public" =>
        ProjectReference(repository, None, config.repositorydir)
      case "local" =>
        ProjectReference(repository, None, Some(_cncf_local_repository_dir()))
      case "path" =>
        val projectpath = _resolve_project_path(config, mode, projectref)
        if (projectpath.isEmpty)
          RAISE.invalidArgumentFault(s"CAR project repository 'path' requires bok.projects.${projectref.getOrElse(module)}.path")
        ProjectReference(repository, projectpath, None)
      case other =>
        RAISE.invalidArgumentFault(s"Unsupported BoK project repository for ${projectref.getOrElse(module)}: $other")
    }
  }

  private def _resolve_project_path(
    config: PublishProjectConfig,
    mode: String,
    ref: Option[String]
  ): Option[Path] =
    ref.map(_.trim).filter(_.nonEmpty).flatMap { projectref =>
      val pathoption = config.bokconfig.value(s"bok.projects.${projectref}.path").map(value =>
        config.bokprojectdir.resolve(value).toAbsolutePath.normalize()
      ).orElse {
        if (mode == "external")
          None
        else
          Some(config.bokprojectdir.resolve(projectref).toAbsolutePath.normalize())
      }
      pathoption.map { path =>
        if (!Files.isDirectory(path))
          RAISE.invalidArgumentFault(s"CAR project path does not exist: $path")
        path
      }
    }

  private def _cncf_local_repository_dir(): Path =
    Paths.get(System.getProperty("user.home"), ".cncf", "local", "repository").toAbsolutePath.normalize()

  private def _relative_path(value: String, label: String): String = {
    val path = Paths.get(value).normalize()
    if (path.isAbsolute || path.startsWith("..") || value.contains("\u0000"))
      RAISE.invalidArgumentFault(s"Invalid CAR project $label path: $value")
    path.toString
  }

  private def _validate_publication_path(value: String): String = {
    val normalized = value.trim.stripPrefix("/").stripSuffix("/")
    if (normalized.isEmpty || normalized.startsWith("metadata/") || normalized.startsWith("repository/") || normalized.contains(".."))
      RAISE.invalidArgumentFault(s"Invalid CAR project publication.path: $value")
    normalized
  }

  private def _validate_slug(value: String, label: String): String = {
    val normalized = value.trim.toLowerCase(java.util.Locale.ROOT)
    normalized match {
      case _slug_pattern() => normalized
      case _ => RAISE.invalidArgumentFault(s"Invalid CAR project ${label}: ${value}")
    }
  }

  private def _slugify(value: String): String =
    value.trim.replaceAll("([a-z0-9])([A-Z])", "$1-$2").
      replaceAll("[^A-Za-z0-9]+", "-").
      stripPrefix("-").
      stripSuffix("-").
      toLowerCase(java.util.Locale.ROOT)

  private def _project_relative_path(project: Path, path: Path): String = {
    val root = project.toAbsolutePath.normalize()
    val target = path.toAbsolutePath.normalize()
    if (target.startsWith(root))
      root.relativize(target).toString.replace(java.io.File.separatorChar, '/')
    else
      target.getFileName.toString
  }

  private def _warehouse_relative_path(config: PublishProjectConfig, reference: ProjectReference, path: Path): String = {
    val target = path.toAbsolutePath.normalize()
    reference.repositorydir.orElse(config.repositorydir).flatMap { repositorydir =>
      val root = repositorydir.toAbsolutePath.normalize()
      if (target.startsWith(root))
        Some("repository/" + root.relativize(target).toString.replace(java.io.File.separatorChar, '/'))
      else
        None
    }.getOrElse {
      val root = config.warehousedir.toAbsolutePath.normalize()
      if (target.startsWith(root))
        root.relativize(target).toString.replace(java.io.File.separatorChar, '/')
      else
        target.getFileName.toString
    }
  }

  private def _cml_public_source_path(config: PublishProjectConfig, project: ResolvedBokProject, cml: ProjectCmlInfo): String =
    cml.modelmetadatapath.map(_warehouse_relative_path(config, project.reference, _)).getOrElse(cml.sourceprojectrelativepath)

  private def _repository_artifact_path(config: PublishProjectConfig, reference: ProjectReference, warehousepath: String): Path =
    reference.repositorydir.orElse(config.repositorydir) match {
      case Some(repositorydir) if warehousepath == "repository" =>
        repositorydir.toAbsolutePath.normalize()
      case Some(repositorydir) if warehousepath.startsWith("repository/") =>
        repositorydir.resolve(warehousepath.stripPrefix("repository/")).toAbsolutePath.normalize()
      case _ =>
        config.warehousedir.resolve(warehousepath).toAbsolutePath.normalize()
    }

  private def _repository_dir(config: PublishProjectConfig, reference: ProjectReference): Option[Path] =
    reference.repositorydir.orElse(config.repositorydir).map(_.toAbsolutePath.normalize()).orElse {
      if (reference.mode == "path")
        None
      else
        Some(config.warehousedir.resolve("repository").toAbsolutePath.normalize())
    }

  private def _repository_catalog_dir(config: PublishProjectConfig, reference: ProjectReference): Path =
    _repository_dir(config, reference).map(_.resolve("catalog/car").toAbsolutePath.normalize()).getOrElse(
      config.warehousedir.resolve("repository/catalog/car").toAbsolutePath.normalize()
    )

  private def _sha256(path: Path): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val in = Files.newInputStream(path)
    try {
      val buffer = new Array[Byte](8192)
      var n = in.read(buffer)
      while (n >= 0) {
        if (n > 0)
          digest.update(buffer, 0, n)
        n = in.read(buffer)
      }
    } finally {
      in.close()
    }
    digest.digest().map(b => "%02x".format(b & 0xff)).mkString
  }
}
