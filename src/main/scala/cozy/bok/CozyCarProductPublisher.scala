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
import java.nio.file.{Files, Path, Paths}
import java.security.MessageDigest

/*
 * @since   Jun. 23, 2026
 * @version Jun. 24, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyCarProductPublisher {
  private val _schema = "cozy.bok.car-product.v1"
  private val _descriptor_names = Vector("product.yaml", "product.yml", "product.json")
  private val _slug_pattern = "^[a-z0-9][a-z0-9-]*$".r

  final case class PublishCarProductConfig(
    packagedir: Path,
    savedir: Path,
    warehousedir: Path,
    version: Option[String],
    force: Boolean,
    bokprojectdir: Path,
    bokconfig: CozyProjectYamlConfig.Config,
    repositorydir: Option[Path] = None
  )

  final case class ProductDescriptor(
    product: ProductSection,
    title: Option[String],
    version: Option[String],
    summary: Option[String],
    article: Option[String],
    publication: Option[PublicationSection]
  )
  object ProductDescriptor {
    implicit val decoder: Decoder[ProductDescriptor] = (c: HCursor) =>
      for {
        product <- c.downField("product").as[ProductSection]
        title <- c.downField("title").as[Option[String]]
        version <- c.downField("version").as[Option[String]]
        summary <- c.downField("summary").as[Option[String]]
        article <- c.downField("article").as[Option[String]]
        publication <- c.downField("publication").as[Option[PublicationSection]]
      } yield ProductDescriptor(product, title, version, summary, article, publication)
  }

  final case class ProductSection(
    producttype: String,
    name: String,
    project: Option[ProjectSection],
    car: Option[CarSection],
    cml: Option[CmlSection]
  )
  object ProductSection {
    implicit val decoder: Decoder[ProductSection] = (c: HCursor) =>
      for {
        producttype <- c.downField("type").as[String]
        name <- c.downField("name").as[String]
        project <- c.downField("project").as[Option[ProjectSection]]
        car <- c.downField("car").as[Option[CarSection]]
        cml <- c.downField("cml").as[Option[CmlSection]]
      } yield ProductSection(producttype, name, project, car, cml)
  }

  final case class ProjectSection(mode: Option[String], ref: Option[String])
  object ProjectSection {
    implicit val decoder: Decoder[ProjectSection] = (c: HCursor) =>
      for {
        mode <- c.downField("mode").as[Option[String]]
        ref <- c.downField("ref").as[Option[String]]
      } yield ProjectSection(mode, ref)
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

  final case class PublicationSection(path: Option[String])
  object PublicationSection {
    implicit val decoder: Decoder[PublicationSection] = (c: HCursor) =>
      c.downField("path").as[Option[String]].map(PublicationSection.apply)
  }

  final case class ResolvedCarProduct(
    packagedir: Path,
    slug: String,
    descriptorfile: Path,
    descriptor: ProductDescriptor,
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
    module: String,
    versionsource: String,
    catalog: Option[ProductCatalogInfo],
    cml: Option[ProductCmlInfo]
  ) {
    def warehousePath: String =
      catalog.flatMap(_.selectedversion).flatMap(_.file).getOrElse(s"repository/car/${module}/${version}/${module}-${version}.car")
    def publicPath: String =
      warehousePath
  }

  final case class ProductCatalogInfo(
    path: Path,
    catalog: RepositoryArtifactCatalog,
    selectedversion: Option[RepositoryArtifactCatalogVersion]
  )

  final case class ProductCmlInfo(
    sourcepath: Path,
    sourceprojectrelativepath: String,
    glossarycategory: String,
    sourcekind: String,
    modelmetadatapath: Option[Path],
    elements: Vector[CmlModelElement]
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

  final case class PublishCarProductResult(
    product: ResolvedCarProduct,
    artifact: Path,
    artifactexists: Boolean,
    publicationbundle: Path
  )

  def publish(config: PublishCarProductConfig): PublishCarProductResult = {
    val product = resolve(config)
    val artifact = artifactPath(config, product.warehousePath)
    _publish_metadata(config, product, artifact)
  }

  def artifactPath(config: PublishCarProductConfig, warehousepath: String): Path =
    _repository_artifact_path(config, warehousepath)

  def resolve(config: PublishCarProductConfig): ResolvedCarProduct = {
    val packagedir = config.packagedir.toAbsolutePath.normalize()
    _validate_package_dir(packagedir)
    val descriptorfile = _descriptor_file(packagedir)
    val descriptor = StructuredDocumentLoader.loadDocument[ProductDescriptor](InputSource(descriptorfile.toFile)).take
    if (descriptor.product.producttype != "car")
      RAISE.invalidArgumentFault(s"Unsupported product.type in CAR product package: ${descriptor.product.producttype}")
    val slug = packagedir.getFileName.toString.stripSuffix(".car-product")
    val name = _validate_slug(descriptor.product.name, "product.name")
    val title = descriptor.title.map(_.trim).filter(_.nonEmpty).getOrElse(name)
    val articlepath = _relative_path(descriptor.article.getOrElse("index.dox"), "article")
    val article = packagedir.resolve(articlepath).normalize()
    if (!Files.isRegularFile(article))
      RAISE.invalidArgumentFault(s"Missing CAR product article source: $article")
    val publicationpath = _validate_publication_path(descriptor.publication.flatMap(_.path).getOrElse(s"products/${name}"))
    val project = descriptor.product.project.getOrElse(ProjectSection(Some("internal"), None))
    val mode = project.mode.map(_.trim).filter(_.nonEmpty).getOrElse("internal")
    if (mode != "internal" && mode != "external")
      RAISE.invalidArgumentFault(s"Unsupported product.project.mode: $mode")
    val projectpath = _resolve_project_path(config, mode, project.ref)
    val module = _validate_slug(descriptor.product.car.flatMap(_.module).getOrElse(name), "product.car.module")
    val catalog = _load_catalog(config, module)
    val catalogversion = catalog.flatMap { case (_, c) => _catalog_effective_version(c) }
    val explicitversion = config.version.orElse(descriptor.version).map(_.trim).filter(_.nonEmpty)
    val version = explicitversion.orElse(catalogversion).getOrElse("0.0.0-SNAPSHOT")
    val cataloginfo = catalog.map {
      case (path, c) => ProductCatalogInfo(path, c, c.versions.find(_.version == version))
    }
    val versionsource =
      if (config.version.nonEmpty) "cli"
      else if (descriptor.version.exists(_.trim.nonEmpty)) "descriptor"
      else if (catalogversion.nonEmpty) "repository-catalog"
      else "default"
    val cml = _resolve_cml_info(config, descriptor, projectpath, module)
    ResolvedCarProduct(
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
      project.ref.map(_.trim).filter(_.nonEmpty),
      projectpath,
      module,
      versionsource,
      cataloginfo,
      cml
    )
  }

  private def _publish_metadata(
    config: PublishCarProductConfig,
    product: ResolvedCarProduct,
    artifact: Path
  ): PublishCarProductResult = {
    val exists = Files.isRegularFile(artifact)
    CozyPublicationCompiler.publishMetadata(
      config.savedir,
      product.name,
      Some(product.publicationpath),
      product.packagedir,
      Vector(
        s"metadata/catalog/products/car/${product.name}.json" -> _catalog_json(product),
        s"metadata/products/car/${product.name}/metadata.json" -> _product_metadata_json(config, product, artifact, exists),
        s"metadata/products/car/${product.name}/${product.version}/manifest.json" -> _manifest_json(config, product, artifact, exists),
        s"metadata/products/car/${product.name}/latest.json" -> _latest_json(product),
        s"metadata/artifacts/repository/${product.name}.json" -> _artifact_json(config, product, artifact, exists)
      )
    )
    PublishCarProductResult(product, artifact, exists, config.savedir.resolve(s"${product.name}.json"))
  }

  private def _catalog_json(product: ResolvedCarProduct): JsValue =
    Json.obj(
      "schema" -> _schema,
      "type" -> "catalog-car-product",
      "product" -> Json.obj(
        "type" -> "car",
        "name" -> product.name,
        "title" -> product.title,
        "version" -> product.version,
        "metadata" -> s"metadata/products/car/${product.name}/metadata",
        "publicationPath" -> product.publicationpath
      )
    )

  private def _product_metadata_json(config: PublishCarProductConfig, product: ResolvedCarProduct, artifact: Path, exists: Boolean): JsValue =
    Json.obj(
      "schema" -> _schema,
      "type" -> "car-product-publication",
      "product" -> (Json.obj(
        "type" -> "car",
        "name" -> product.name,
        "title" -> product.title,
        "version" -> product.version,
        "summary" -> Json.toJson(product.summary.getOrElse("")),
        "articlePath" -> product.articlepath,
        "publicationPath" -> product.publicationpath,
        "sourcePackage" -> _project_relative_path(config.bokprojectdir, product.packagedir),
        "descriptorPath" -> product.descriptorfile.getFileName.toString,
        "descriptorSha256" -> _sha256(product.descriptorfile),
        "project" -> Json.obj(
          "mode" -> product.projectmode,
          "ref" -> Json.toJson(product.projectref.getOrElse(""))
        ),
        "car" -> Json.obj(
          "module" -> product.module
        ),
        "versionSource" -> product.versionsource,
        "catalog" -> _catalog_summary_json(config, product),
        "cml" -> _cml_json(config, product),
        "artifact" -> _artifact_file_json(product, artifact, exists)
      ))
    )

  private def _manifest_json(config: PublishCarProductConfig, product: ResolvedCarProduct, artifact: Path, exists: Boolean): JsValue =
    Json.obj(
      "schema" -> _schema,
      "type" -> "car-product-registry-manifest",
      "product" -> Json.obj(
        "name" -> product.name,
        "title" -> product.title,
        "version" -> product.version,
        "metadataPath" -> s"metadata/products/car/${product.name}/metadata",
        "latestPath" -> s"metadata/products/car/${product.name}/latest"
      ),
      "artifact" -> _artifact_file_json(product, artifact, exists),
      "catalog" -> _catalog_summary_json(config, product),
      "cml" -> _cml_json(config, product),
      "diagnostics" -> JsArray(_diagnostics(product, exists))
    )

  private def _latest_json(product: ResolvedCarProduct): JsValue =
    Json.obj(
      "schema" -> _schema,
      "type" -> "car-product-latest",
      "product" -> Json.obj(
        "name" -> product.name,
        "version" -> product.version,
        "metadataPath" -> s"metadata/products/car/${product.name}/metadata",
        "manifestPath" -> s"metadata/products/car/${product.name}/${product.version}/manifest"
      )
    )

  private def _artifact_json(config: PublishCarProductConfig, product: ResolvedCarProduct, artifact: Path, exists: Boolean): JsValue = {
    val files = product.catalog.map { info =>
      info.catalog.versions.map(_catalog_artifact_file_json(config, product, _))
    }.getOrElse(Vector(_artifact_file_json(product, artifact, exists)))
    val status =
      if (files.nonEmpty && files.forall(file => (file \ "status").asOpt[String].contains("published"))) "published" else "missing"
    Json.obj(
      "schema" -> _schema,
      "type" -> "repository-artifact",
      "project" -> Json.obj(
        "name" -> product.name,
        "title" -> product.title,
        "kind" -> "car",
        "version" -> product.version
      ),
      "artifact" -> Json.obj(
        "layer" -> "repository",
        "status" -> status,
        "kinds" -> Json.arr(Json.obj(
          "type" -> "car",
          "versions" -> Json.toJson(files.map(file => (file \ "version").as[String])),
          "latestRelease" -> product.version
        )),
        "files" -> JsArray(files)
      )
    )
  }

  private def _artifact_file_json(product: ResolvedCarProduct, artifact: Path, exists: Boolean): JsObject = {
    val base = Json.obj(
      "layer" -> "repository",
      "type" -> "car",
      "module" -> product.module,
      "artifactId" -> product.module,
      "version" -> product.version,
      "extension" -> "car",
      "warehousePath" -> product.warehousePath,
      "publicPath" -> product.publicPath,
      "name" -> artifact.getFileName.toString,
      "expected" -> !exists,
      "status" -> (if (exists) "published" else "missing")
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
    config: PublishCarProductConfig,
    product: ResolvedCarProduct,
    version: RepositoryArtifactCatalogVersion
  ): JsObject = {
    val warehousepath = version.file.getOrElse(s"repository/car/${product.module}/${version.version}/${product.module}-${version.version}.car")
    val artifact = _repository_artifact_path(config, warehousepath)
    val exists = Files.isRegularFile(artifact)
    val base = Json.obj(
      "layer" -> "repository",
      "type" -> "car",
      "module" -> product.module,
      "artifactId" -> product.module,
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

  private def _catalog_summary_json(config: PublishCarProductConfig, product: ResolvedCarProduct): JsValue =
    product.catalog.map { info =>
      Json.obj(
        "source" -> "repository-catalog",
        "path" -> _warehouse_relative_path(config, info.path),
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

  private def _cml_json(config: PublishCarProductConfig, product: ResolvedCarProduct): JsValue =
    product.cml.map { cml =>
      Json.obj(
        "sourcePath" -> _cml_public_source_path(config, cml),
        "projectRelativePath" -> cml.sourceprojectrelativepath,
        "sourceKind" -> cml.sourcekind,
        "modelMetadataPath" -> Json.toJson(cml.modelmetadatapath.map(_warehouse_relative_path(config, _)).getOrElse("")),
        "glossaryCategory" -> cml.glossarycategory,
        "modelElements" -> JsArray(cml.elements.map { element =>
          Json.obj(
            "kind" -> element.kind,
            "name" -> element.name,
            "termId" -> element.termid,
            "glossaryPath" -> element.glossarypath,
            "descriptive" -> Json.obj(
              "label" -> element.descriptive.label,
              "brief" -> Json.toJson(element.descriptive.brief.getOrElse("")),
              "summary" -> Json.toJson(element.descriptive.summary.getOrElse("")),
              "description" -> Json.toJson(element.descriptive.description.getOrElse(""))
            ),
            "narrative" -> Json.toJson(element.narrative.getOrElse(""))
          )
        })
      )
    }.getOrElse(Json.obj(
      "status" -> "missing",
      "message" -> "CML source is not registered for this CAR product."
    ))

  private def _diagnostics(product: ResolvedCarProduct, exists: Boolean): Vector[JsObject] =
    if (exists)
      Vector.empty
    else {
      val projectdir = product.projectref.map(ref => s"<${ref}>").getOrElse("<project-dir>")
      Vector(Json.obj(
        "severity" -> "warning",
        "message" -> s"CAR artifact is not registered in artifact repository: ${product.warehousePath}",
        "action" -> s"Run cozy publish-car ${projectdir} --warehouse <warehouse-dir> --name ${product.module} --version ${product.version}"
      ))
    }

  private def _load_catalog(config: PublishCarProductConfig, module: String): Option[(Path, RepositoryArtifactCatalog)] = {
    val path = _repository_catalog_dir(config).resolve(s"$module.yaml").toAbsolutePath.normalize()
    if (Files.isRegularFile(path))
      Some(path -> RepositoryArtifactCatalog.load(path))
    else
      None
  }

  private def _resolve_cml_info(
    config: PublishCarProductConfig,
    descriptor: ProductDescriptor,
    projectpath: Option[Path],
    module: String
  ): Option[ProductCmlInfo] = {
    val glossarycategory = descriptor.product.cml.flatMap(_.glossary).flatMap(_.category).map(_validate_slug(_, "product.cml.glossary.category")).getOrElse("cml")
    _load_model_metadata(config, module, glossarycategory).orElse(projectpath.flatMap { projectdir =>
      val sourcerelative = descriptor.product.cml.flatMap(_.source).map(_relative_path(_, "product.cml.source")).getOrElse(s"src/main/cozy/${module}.cml")
      val sourcepath = projectdir.resolve(sourcerelative).toAbsolutePath.normalize()
      if (Files.isRegularFile(sourcepath)) {
        Some(ProductCmlInfo(
          sourcepath,
          sourcerelative,
          glossarycategory,
          "direct-cml-scan",
          None,
          _cml_model_elements(sourcepath, glossarycategory)
        ))
      } else {
        None
      }
    })
  }

  private def _load_model_metadata(
    config: PublishCarProductConfig,
    module: String,
    glossarycategory: String
  ): Option[ProductCmlInfo] = {
    val catalogdir = _repository_catalog_dir(config)
    val candidates = Vector(
      catalogdir.resolve(s"$module.model-metadata.json"),
      catalogdir.resolve(s"$module.model-metadata.yaml")
    )
    candidates.find(Files.isRegularFile(_)).map { path =>
      val json = StructuredDocumentLoader.loadJson(InputSource(path.toFile)).take
      val sourcepath = json.hcursor.downField("source").downField("path").as[String].getOrElse("")
      val source = if (sourcepath.trim.isEmpty) path else Paths.get(sourcepath).toAbsolutePath.normalize()
      val elements = json.hcursor.downField("elements").as[Vector[io.circe.Json]].getOrElse(Vector.empty).map { element =>
        val c = element.hcursor
        val name = c.downField("name").as[String].getOrElse("")
        val kind = c.downField("kind").as[String].getOrElse("unknown")
        val slug = _slugify(name)
        val descriptive = c.downField("descriptive")
        CmlModelElement(
          kind,
          name,
          c.downField("termId").as[String].getOrElse(s"${glossarycategory}:${slug}"),
          c.downField("glossaryPath").as[String].getOrElse(s"glossary/${glossarycategory}/${slug}.html"),
          CmlDescriptive(
            descriptive.downField("label").as[String].getOrElse(name),
            descriptive.downField("brief").as[String].toOption.filter(_.nonEmpty),
            descriptive.downField("summary").as[String].toOption.filter(_.nonEmpty),
            descriptive.downField("description").as[String].toOption.filter(_.nonEmpty)
          ),
          c.downField("narrative").as[String].toOption.filter(_.trim.nonEmpty)
        )
      }.filter(_.name.nonEmpty)
      ProductCmlInfo(
        source,
        if (sourcepath.trim.isEmpty) "" else sourcepath,
        glossarycategory,
        "repository-model-metadata",
        Some(path),
        elements
      )
    }
  }

  private def _cml_model_elements(sourcepath: Path, glossarycategory: String): Vector[CmlModelElement] = {
    CmlModelMetadata.fromCml(sourcepath, glossarycategory).elements.map { element =>
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

  private def _catalog_effective_version(catalog: RepositoryArtifactCatalog): Option[String] = {
    val selectors = Vector(catalog.recommended, catalog.latestStable, catalog.latestSnapshot).flatten
    selectors.find(version => catalog.versions.exists(v => v.version == version && v.effectiveStatus != "disabled")).
      orElse(catalog.versions.filter(_.effectiveStatus != "disabled").lastOption.map(_.version))
  }

  private def _descriptor_file(packagedir: Path): Path = {
    val files = _descriptor_names.map(packagedir.resolve).filter(Files.isRegularFile(_))
    files match {
      case Vector(x) => x
      case Vector() => RAISE.invalidArgumentFault(s"Missing CAR product descriptor. Expected one of: ${_descriptor_names.mkString(", ")}")
      case xs => RAISE.invalidArgumentFault(s"Multiple CAR product descriptors found: ${xs.map(_.getFileName.toString).mkString(", ")}")
    }
  }

  private def _validate_package_dir(path: Path): Unit = {
    val name = path.getFileName.toString
    if (name.endsWith(".car-product.d"))
      RAISE.invalidArgumentFault(s"*.car-product.d is reserved for generated/work directories, not CAR product source packages: $path")
    if (!name.endsWith(".car-product"))
      RAISE.invalidArgumentFault(s"CAR product source package directory must end with .car-product: $path")
    if (!Files.isDirectory(path))
      RAISE.invalidArgumentFault(s"CAR product source package directory does not exist: $path")
  }

  private def _resolve_project_path(
    config: PublishCarProductConfig,
    mode: String,
    ref: Option[String]
  ): Option[Path] =
    ref.map(_.trim).filter(_.nonEmpty).map { projectref =>
      val path = config.bokconfig.value(s"bok.projects.${projectref}.path").map(value =>
        config.bokprojectdir.resolve(value).toAbsolutePath.normalize()
      ).getOrElse {
        if (mode == "external")
          RAISE.invalidArgumentFault(s"Missing BoK project reference path: bok.projects.${projectref}.path")
        else
          config.bokprojectdir.resolve(projectref).toAbsolutePath.normalize()
      }
      if (!Files.isDirectory(path))
        RAISE.invalidArgumentFault(s"CAR product project path does not exist: $path")
      path
    }

  private def _relative_path(value: String, label: String): String = {
    val path = Paths.get(value).normalize()
    if (path.isAbsolute || path.startsWith("..") || value.contains("\u0000"))
      RAISE.invalidArgumentFault(s"Invalid CAR product $label path: $value")
    path.toString
  }

  private def _validate_publication_path(value: String): String = {
    val normalized = value.trim.stripPrefix("/").stripSuffix("/")
    if (normalized.isEmpty || normalized.startsWith("metadata/") || normalized.startsWith("repository/") || normalized.contains(".."))
      RAISE.invalidArgumentFault(s"Invalid CAR product publication.path: $value")
    normalized
  }

  private def _validate_slug(value: String, label: String): String = {
    val normalized = value.trim.toLowerCase(java.util.Locale.ROOT)
    normalized match {
      case _slug_pattern() => normalized
      case _ => RAISE.invalidArgumentFault(s"Invalid CAR product ${label}: ${value}")
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

  private def _warehouse_relative_path(config: PublishCarProductConfig, path: Path): String = {
    val target = path.toAbsolutePath.normalize()
    config.repositorydir.flatMap { repositorydir =>
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

  private def _cml_public_source_path(config: PublishCarProductConfig, cml: ProductCmlInfo): String =
    cml.modelmetadatapath.map(_warehouse_relative_path(config, _)).getOrElse(cml.sourceprojectrelativepath)

  private def _repository_artifact_path(config: PublishCarProductConfig, warehousepath: String): Path =
    config.repositorydir match {
      case Some(repositorydir) if warehousepath == "repository" =>
        repositorydir.toAbsolutePath.normalize()
      case Some(repositorydir) if warehousepath.startsWith("repository/") =>
        repositorydir.resolve(warehousepath.stripPrefix("repository/")).toAbsolutePath.normalize()
      case _ =>
        config.warehousedir.resolve(warehousepath).toAbsolutePath.normalize()
    }

  private def _repository_catalog_dir(config: PublishCarProductConfig): Path =
    config.repositorydir match {
      case Some(repositorydir) =>
        repositorydir.resolve("catalog/car").toAbsolutePath.normalize()
      case None =>
        config.warehousedir.resolve("repository/catalog/car").toAbsolutePath.normalize()
    }

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
