package cozy.bok

import cozy.bok.CozyBokProjectPublisher.ResolvedBokProject
import cozy.config.CozyProjectYamlConfig
import io.circe.{HCursor, Json, JsonObject}
import io.circe.parser
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path}
import scala.util.control.NonFatal

/*
 * @since   Jul. 13, 2026
 * @version Jul. 13, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyBokSieHandoff {
  val INTEGRATION_SCHEMA_VERSION = "cozy.bok.sie-integration.v1"
  val MANIFEST_SCHEMA_VERSION = "cncf.knowledge-source.v1"
  val PROVENANCE_SCHEMA_VERSION = "sie.provenance.v1"
  val INFORMATION_SCHEMA_VERSION = "sie.information-schema.v1"
  val INFORMATION_INSTANCES_VERSION = "sie.information-instances.v1"
  val MANIFEST_PATH = "metadata/cncf/knowledge-source.json"
  val PROVENANCE_PATH = "metadata/sie/provenance.json"
  val INFORMATION_SCHEMA_PATH = "metadata/sie/information-schema.json"
  val INFORMATION_INSTANCES_PATH = "metadata/sie/information-instances.json"
  private val _SUPPORTED_RESOURCE_KINDS = Set(
    "sie-provenance",
    "information-schema",
    "information-instances",
    "rdf-jsonld",
    "rdf-turtle",
    "rdf-graph-summary"
  )
  private val _REQUIRED_RESOURCE_KINDS = Vector("information-schema", "information-instances")

  final case class Diagnostic(
    code: String,
    severity: String,
    message: String,
    projectref: String,
    projection: String,
    resourcekind: Option[String] = None
  ) {
    def toJson: Json = Json.obj(
      "code" -> Json.fromString(code),
      "severity" -> Json.fromString(severity),
      "message" -> Json.fromString(message),
      "projectRef" -> Json.fromString(projectref),
      "projection" -> Json.fromString(projection),
      "resourceKind" -> resourcekind.map(Json.fromString).getOrElse(Json.Null)
    )
  }

  final case class Resource(kind: String, href: String, mediatype: String) {
    def toJson: Json = Json.obj(
      "kind" -> Json.fromString(kind),
      "href" -> Json.fromString(href),
      "mediaType" -> Json.fromString(mediatype)
    )
  }

  final case class InformationInstance(
    id: String,
    schema: String,
    label: String,
    summary: Option[String],
    rdfnode: Option[String],
    category: Option[String],
    termrefs: Vector[String],
    scenariorefs: Vector[String],
    projectrefs: Vector[String],
    tags: Vector[String],
    projection: String,
    projectref: String,
    projectpath: String
  ) {
    def graphNodeId: String =
      rdfnode.getOrElse(s"urn:cozy:sie:${_uri_segment(projection)}:information:${_uri_segment(id)}")

    def toJson: Json = Json.obj(
      "id" -> Json.fromString(id),
      "schema" -> Json.fromString(schema),
      "label" -> Json.fromString(label),
      "summary" -> summary.map(Json.fromString).getOrElse(Json.Null),
      "rdfNode" -> rdfnode.map(Json.fromString).getOrElse(Json.Null),
      "category" -> category.map(Json.fromString).getOrElse(Json.Null),
      "termRefs" -> Json.fromValues(termrefs.map(Json.fromString)),
      "scenarioRefs" -> Json.fromValues(scenariorefs.map(Json.fromString)),
      "projectRefs" -> Json.fromValues(projectrefs.map(Json.fromString)),
      "tags" -> Json.fromValues(tags.map(Json.fromString))
    )

    def toGraphNode: Json = Json.obj(
      "id" -> Json.fromString(graphNodeId),
      "label" -> Json.fromString(label),
      "node_type" -> Json.fromString("uri"),
      "category" -> category.map(Json.fromString).getOrElse(Json.Null),
      "degree" -> Json.fromInt(0),
      "informationSchema" -> Json.fromString(schema),
      "sie" -> Json.obj(
        "projection" -> Json.fromString(projection),
        "informationId" -> Json.fromString(id),
        "summary" -> summary.map(Json.fromString).getOrElse(Json.Null),
        "termRefs" -> Json.fromValues(termrefs.map(Json.fromString)),
        "scenarioRefs" -> Json.fromValues(scenariorefs.map(Json.fromString)),
        "projectRefs" -> Json.fromValues(projectrefs.map(Json.fromString)),
        "tags" -> Json.fromValues(tags.map(Json.fromString)),
        "projectPath" -> Json.fromString(projectpath)
      )
    )
  }

  final case class Projection(
    projectref: String,
    projecttitle: String,
    projectpath: String,
    projection: String,
    handoffbase: String,
    manifesturi: String,
    resources: Vector[Resource],
    informationschemas: Vector[Json],
    informationinstances: Vector[InformationInstance],
    graph: Option[Json],
    diagnostics: Vector[Diagnostic]
  ) {
    def toJson: Json = Json.obj(
      "projectRef" -> Json.fromString(projectref),
      "projectTitle" -> Json.fromString(projecttitle),
      "projectPath" -> Json.fromString(projectpath),
      "projection" -> Json.fromString(projection),
      "handoffBase" -> Json.fromString(handoffbase),
      "manifest" -> Json.fromString(manifesturi),
      "resources" -> Json.fromValues(resources.map(_.toJson)),
      "informationSchemas" -> Json.fromValues(informationschemas),
      "informationInstances" -> Json.fromValues(informationinstances.map(_.toJson)),
      "diagnostics" -> Json.fromValues(diagnostics.map(_.toJson))
    )
  }

  final case class Index(projections: Vector[Projection]) {
    def diagnostics: Vector[Diagnostic] = projections.flatMap(_.diagnostics)
    def informationinstances: Vector[InformationInstance] = projections.flatMap(_.informationinstances)
    def isEmpty: Boolean = projections.isEmpty

    def forProject(project: ResolvedBokProject): Option[Projection] = {
      val ref = project.projectref.getOrElse(project.name)
      projections.find(_.projectref == ref)
    }

    def toJson: Json = Json.obj(
      "schemaVersion" -> Json.fromString(INTEGRATION_SCHEMA_VERSION),
      "projections" -> Json.fromValues(projections.map(_.toJson)),
      "diagnostics" -> Json.fromValues(diagnostics.map(_.toJson))
    )
  }

  val empty: Index = Index(Vector.empty)

  def load(
    projectroot: Path,
    config: CozyProjectYamlConfig.Config,
    projects: Vector[ResolvedBokProject]
  ): Index =
    Index(projects.filter(_.sie.nonEmpty).sortBy(_.publicationpath).map(_load_projection(projectroot, config, _)))

  def mergeGraph(base: Json, index: Index): Either[String, Json] = {
    val baseobject = base.asObject.getOrElse(JsonObject.empty)
    val graphobjects = index.projections.flatMap(_.graph.flatMap(_.asObject))
    val schemas =
      _merge_named_json(
        _information_schemas(base) ++
          graphobjects.flatMap(x => _information_schemas(Json.fromJsonObject(x))) ++
          index.projections.flatMap(_.informationschemas),
        "Information schema"
      )
    val nodes =
      _merge_nodes(
        baseobject("nodes").flatMap(_.asArray).getOrElse(Vector.empty) ++
          graphobjects.flatMap(_("nodes").flatMap(_.asArray).getOrElse(Vector.empty)) ++
          index.informationinstances.map(_.toGraphNode)
      )
    val edges =
      _merge_edges(
        baseobject("edges").flatMap(_.asArray).getOrElse(Vector.empty) ++
          graphobjects.flatMap(_("edges").flatMap(_.asArray).getOrElse(Vector.empty))
      )
    for {
      mergedSchemas <- schemas
      mergedNodes <- nodes
      mergedEdges <- edges
    } yield {
      val informationview = _merge_information_view(baseobject("informationView"), mergedSchemas)
      Json.fromJsonObject(
        baseobject.
          add("informationView", informationview).
          add("nodes", Json.fromValues(mergedNodes)).
          add("edges", Json.fromValues(mergedEdges))
      )
    }
  }

  private def _load_projection(
    projectroot: Path,
    config: CozyProjectYamlConfig.Config,
    project: ResolvedBokProject
  ): Projection = {
    val sie = project.sie.get
    val projectref = project.projectref.getOrElse(project.name)
    val configuredpath = config.value(s"bok.projects.${projectref}.sie.path")
    val base = Projection(
      projectref,
      project.title,
      s"${project.publicationpath}/index.html",
      sie.projection,
      sie.handoffbase,
      sie.manifest,
      Vector.empty,
      Vector.empty,
      Vector.empty,
      None,
      Vector.empty
    )
    configuredpath match {
      case None =>
        base.copy(diagnostics = Vector(_diagnostic(
          "sie.handoff.freshness-unknown",
          "warning",
          "No local SIE handoff path is configured; Cozy does not fetch the public handoff during build.",
          projectref,
          sie.projection
        )))
      case Some(pathtext) =>
        val root = _resolve_path(projectroot, pathtext)
        _load_manifest(root, base)
    }
  }

  private def _load_manifest(root: Path, base: Projection): Projection = {
    val path = root.resolve(MANIFEST_PATH)
    if (!Files.isRegularFile(path))
      base.copy(diagnostics = Vector(_diagnostic(
        "sie.handoff.missing",
        "error",
        s"SIE handoff manifest is missing: ${MANIFEST_PATH}",
        base.projectref,
        base.projection
      )))
    else
      _parse_json(path) match {
        case Left(message) =>
          base.copy(diagnostics = Vector(_diagnostic(
            "sie.handoff.invalid",
            "error",
            s"SIE handoff manifest is invalid: ${message}",
            base.projectref,
            base.projection
          )))
        case Right(manifest) =>
          _validate_manifest(root, base, manifest)
      }
  }

  private def _validate_manifest(root: Path, base: Projection, manifest: Json): Projection = {
    val cursor = manifest.hcursor
    val schemaversion = _string(cursor, "schemaVersion")
    val kind = _string(cursor, "kind")
    val sourcerefkind = cursor.downField("sourceRef").get[String]("kind").toOption
    val sourcerefvalue = cursor.downField("sourceRef").get[String]("value").toOption
    if (schemaversion.exists(_ != MANIFEST_SCHEMA_VERSION))
      base.copy(diagnostics = Vector(_diagnostic(
        "sie.handoff.schema.unsupported",
        "error",
        s"Unsupported SIE handoff schema: ${schemaversion.get}",
        base.projectref,
        base.projection
      )))
    else if (!schemaversion.contains(MANIFEST_SCHEMA_VERSION) || !kind.contains("sie-projection") ||
      !sourcerefkind.contains("sie-projection") || !sourcerefvalue.contains(base.projection))
      base.copy(diagnostics = Vector(_diagnostic(
        "sie.handoff.invalid",
        "error",
        "SIE handoff manifest must identify the configured sie-projection.",
        base.projectref,
        base.projection
      )))
    else {
      val rawresources = cursor.downField("resources").focus.flatMap(_.asArray).getOrElse(Vector.empty)
      val decodedresources = rawresources.map(_decode_resource)
      val resources = decodedresources.flatten
      val duplicatekinds = resources.filter(x => _SUPPORTED_RESOURCE_KINDS.contains(x.kind)).groupBy(_.kind).
        collect { case (resourcekind, xs) if xs.size > 1 => resourcekind }.toVector.sorted
      val invalidresources = resources.filter { resource =>
        !_is_safe_relative_resource_href(resource.href) ||
          (_SUPPORTED_RESOURCE_KINDS.contains(resource.kind) && _safe_resource_path(root, resource.href).isEmpty)
      }
      if (decodedresources.exists(_.isEmpty))
        base.copy(diagnostics = Vector(_diagnostic(
          "sie.handoff.invalid",
          "error",
          "SIE handoff resource entries require kind, href, and mediaType.",
          base.projectref,
          base.projection
        )))
      else if (duplicatekinds.nonEmpty)
        base.copy(diagnostics = duplicatekinds.map(resourcekind => _diagnostic(
          "sie.handoff.invalid",
          "error",
          s"SIE handoff resource kind must be declared at most once: ${resourcekind}",
          base.projectref,
          base.projection,
          Some(resourcekind)
        )))
      else if (invalidresources.nonEmpty)
        base.copy(diagnostics = invalidresources.map(x => _diagnostic(
          "sie.handoff.invalid",
          "error",
          s"SIE resource href must be a safe relative path: ${x.href}",
          base.projectref,
          base.projection,
          Some(x.kind)
        )))
      else
        _load_resources(root, base.copy(resources = resources))
    }
  }

  private def _load_resources(root: Path, base: Projection): Projection = {
    val missingrequiredkinds = _REQUIRED_RESOURCE_KINDS.filterNot(kind => base.resources.exists(_.kind == kind))
    val initialdiagnostics =
      missingrequiredkinds.map(kind => _diagnostic(
        "sie.handoff.resource.missing",
        "error",
        s"SIE handoff manifest does not declare required resource: ${kind}",
        base.projectref,
        base.projection,
        Some(kind)
      )) ++
        base.resources.filterNot(x => _SUPPORTED_RESOURCE_KINDS.contains(x.kind)).map(x => _diagnostic(
          "sie.handoff.resource.unsupported",
          "warning",
          s"Unsupported optional SIE handoff resource: ${x.kind}",
          base.projectref,
          base.projection,
          Some(x.kind)
        ))
    val loaded = base.resources.foldLeft((Vector.empty[Json], Vector.empty[InformationInstance], Option.empty[Json], initialdiagnostics)) {
      case ((schemas, instances, graph, diagnostics), resource) =>
        if (!_SUPPORTED_RESOURCE_KINDS.contains(resource.kind))
          (schemas, instances, graph, diagnostics)
        else {
          val path = _safe_resource_path(root, resource.href).get
          if (!Files.isRegularFile(path))
            (schemas, instances, graph, diagnostics :+ _diagnostic(
              "sie.handoff.resource.missing",
              "error",
              s"Declared SIE handoff resource is missing: ${resource.href}",
              base.projectref,
              base.projection,
              Some(resource.kind)
            ))
          else resource.kind match {
            case "information-schema" =>
              _parse_json(path).flatMap(_decode_information_schemas(_, base.projection)) match {
                case Right(xs) => (schemas ++ xs, instances, graph, diagnostics)
                case Left(message) => (schemas, instances, graph, diagnostics :+ _resource_invalid(base, resource, message))
              }
            case "information-instances" =>
              _parse_json(path).flatMap(_decode_information_instances(_, base)) match {
                case Right(xs) => (schemas, instances ++ xs, graph, diagnostics)
                case Left(message) => (schemas, instances, graph, diagnostics :+ _resource_invalid(base, resource, message))
              }
            case "rdf-graph-summary" =>
              _parse_json(path) match {
                case Right(value) if value.asObject.isDefined => (schemas, instances, Some(value), diagnostics)
                case Right(_) => (schemas, instances, graph, diagnostics :+ _resource_invalid(base, resource, "RDF graph summary must be a JSON object."))
                case Left(message) => (schemas, instances, graph, diagnostics :+ _resource_invalid(base, resource, message))
              }
            case _ => (schemas, instances, graph, diagnostics)
          }
        }
    }
    val schemanames = loaded._1.flatMap(_json_string(_, "name")).toSet
    val undefinedinstances = loaded._2.filterNot(x => schemanames.contains(x.schema))
    val schemadiagnostics = undefinedinstances.map(x => _diagnostic(
      "sie.handoff.invalid",
      "error",
      s"SIE Information instance ${x.id} references undefined schema: ${x.schema}",
      base.projectref,
      base.projection,
      Some("information-instances")
    ))
    val validinstances = loaded._2.filter(x => schemanames.contains(x.schema))
    val provenance = base.resources.find(_.kind == "sie-provenance")
    val freshnessdiagnostics = provenance match {
      case None => Vector(_diagnostic(
        "sie.handoff.freshness-unknown",
        "warning",
        "SIE handoff has no provenance resource; freshness cannot be verified.",
        base.projectref,
        base.projection,
        Some("sie-provenance")
      ))
      case Some(resource) => _validate_provenance(root, base, resource)
    }
    base.copy(
      informationschemas = loaded._1,
      informationinstances = validinstances,
      graph = loaded._3,
      diagnostics = (loaded._4 ++ schemadiagnostics ++ freshnessdiagnostics).
        sortBy(x => (x.severity, x.code, x.resourcekind.getOrElse("")))
    )
  }

  private def _validate_provenance(root: Path, base: Projection, resource: Resource): Vector[Diagnostic] = {
    val path = _safe_resource_path(root, resource.href).get
    if (!Files.isRegularFile(path))
      Vector.empty // The generic declared-resource check already reports this.
    else
      _parse_json(path) match {
        case Left(message) => Vector(_resource_invalid(base, resource, message))
        case Right(value) =>
          val c = value.hcursor
          val schema = _string(c, "schemaVersion")
          val projection = _string(c, "projection")
          val projectref = _string(c, "projectRef").orElse(_string(c, "project_ref"))
          if (!schema.contains(PROVENANCE_SCHEMA_VERSION))
            Vector(_resource_invalid(base, resource, s"Expected ${PROVENANCE_SCHEMA_VERSION}."))
          else if (projection.exists(_ != base.projection) || projectref.exists(_ != base.projectref))
            Vector(_diagnostic(
              "sie.handoff.stale",
              "warning",
              "SIE provenance does not match the configured Project/projection.",
              base.projectref,
              base.projection,
              Some(resource.kind)
            ))
          else if (projection.isEmpty || projectref.isEmpty)
            Vector(_diagnostic(
              "sie.handoff.freshness-unknown",
              "warning",
              "SIE provenance does not identify both projection and projectRef.",
              base.projectref,
              base.projection,
              Some(resource.kind)
            ))
          else Vector.empty
      }
  }

  private def _decode_information_schemas(value: Json, projection: String): Either[String, Vector[Json]] = {
    val c = value.hcursor
    val schema = _string(c, "schemaVersion")
    val declaredprojection = _string(c, "projection")
    if (!schema.contains(INFORMATION_SCHEMA_VERSION))
      Left(s"Expected ${INFORMATION_SCHEMA_VERSION}.")
    else if (!declaredprojection.contains(projection))
      Left(s"Information schema projection must be ${projection}.")
    else {
      val entries = c.downField("informationSchemas").focus.orElse(c.downField("information_schemas").focus).
        flatMap(_.asArray).getOrElse(Vector.empty)
      val invalid = entries.find(x => _json_string(x, "name").forall(_.trim.isEmpty))
      if (entries.isEmpty)
        Left("Information schema resource must define at least one informationSchemas entry.")
      else if (invalid.nonEmpty)
        Left("Information schema entries require name.")
      else
        Right(entries.sortBy(x => _json_string(x, "name").getOrElse("")))
    }
  }

  private def _decode_information_instances(value: Json, base: Projection): Either[String, Vector[InformationInstance]] = {
    val c = value.hcursor
    val schema = _string(c, "schemaVersion")
    val declaredprojection = _string(c, "projection")
    if (!schema.contains(INFORMATION_INSTANCES_VERSION))
      Left(s"Expected ${INFORMATION_INSTANCES_VERSION}.")
    else if (!declaredprojection.contains(base.projection))
      Left(s"Information instances projection must be ${base.projection}.")
    else {
      val values = c.downField("instances").focus.flatMap(_.asArray).getOrElse(Vector.empty)
      val decoded = values.map(_decode_information_instance(_, base))
      decoded.collectFirst { case Left(message) => Left(message) }.getOrElse {
        val instances = decoded.collect { case Right(x) => x }
        val duplicateids = instances.groupBy(_.id).collect {
          case (id, entries) if entries.size > 1 => id
        }.toVector.sorted
        if (duplicateids.nonEmpty)
          Left(s"Information instance ids must be unique: ${duplicateids.mkString(", ")}")
        else
          Right(instances.sortBy(x => (x.id, x.graphNodeId)))
      }
    }
  }

  private def _decode_information_instance(value: Json, base: Projection): Either[String, InformationInstance] = {
    val c = value.hcursor
    val id = _string(c, "id").map(_.trim).filter(_.nonEmpty)
    val schema = _string(c, "schema").map(_.trim).filter(_.nonEmpty)
    val label = _string(c, "label").map(_.trim).filter(_.nonEmpty)
    (id, schema, label) match {
      case (Some(i), Some(s), Some(l)) => Right(InformationInstance(
        i,
        s,
        l,
        _string(c, "summary"),
        _string(c, "rdfNode").orElse(_string(c, "rdf_node")),
        _string(c, "category"),
        _strings(c, "termRefs", "term_refs"),
        _strings(c, "scenarioRefs", "scenario_refs"),
        _strings(c, "projectRefs", "project_refs"),
        _strings(c, "tags"),
        base.projection,
        base.projectref,
        base.projectpath
      ))
      case _ => Left("Information instance entries require id, schema, and label.")
    }
  }

  private def _decode_resource(value: Json): Option[Resource] = {
    val c = value.hcursor
    for {
      kind <- _string(c, "kind").map(_.trim).filter(_.nonEmpty)
      href <- _string(c, "href").map(_.trim).filter(_.nonEmpty)
      mediatype <- _string(c, "mediaType").orElse(_string(c, "media_type")).map(_.trim).filter(_.nonEmpty)
    } yield Resource(kind, href, mediatype)
  }

  private def _information_schemas(value: Json): Vector[Json] =
    value.hcursor.downField("informationView").downField("informationSchemas").focus.
      orElse(value.hcursor.downField("informationView").downField("information_schemas").focus).
      flatMap(_.asArray).getOrElse(Vector.empty)

  private def _merge_information_view(current: Option[Json], schemas: Vector[Json]): Json = {
    val base = current.flatMap(_.asObject).getOrElse(JsonObject.empty)
    Json.fromJsonObject(base.add("informationSchemas", Json.fromValues(schemas)))
  }

  private def _merge_named_json(values: Vector[Json], label: String): Either[String, Vector[Json]] = {
    val grouped = values.groupBy(x => _json_string(x, "name").getOrElse(""))
    grouped.toVector.sortBy(_._1).foldLeft[Either[String, Vector[Json]]](Right(Vector.empty)) {
      case (left @ Left(_), _) => left
      case (Right(result), (name, entries)) if name.isEmpty => Left(s"${label} requires name.")
      case (Right(result), (name, entries)) =>
        val distinct = entries.distinct
        if (distinct.size > 1) Left(s"Conflicting ${label.toLowerCase} metadata: ${name}")
        else Right(result :+ distinct.head)
    }
  }

  private def _merge_nodes(values: Vector[Json]): Either[String, Vector[Json]] = {
    val grouped = values.groupBy(x => _json_string(x, "id").getOrElse(""))
    grouped.toVector.sortBy(_._1).foldLeft[Either[String, Vector[Json]]](Right(Vector.empty)) {
      case (left @ Left(_), _) => left
      case (Right(result), (id, _)) if id.isEmpty => Left("RDF graph node requires id.")
      case (Right(result), (_, entries)) =>
        val merged = entries.foldLeft(Json.obj()) { (z, entry) => entry.deepMerge(z) }
        Right(result :+ merged)
    }
  }

  private def _merge_edges(values: Vector[Json]): Either[String, Vector[Json]] = {
    def key(value: Json): (String, String, String) = (
      _json_string(value, "source").getOrElse(""),
      _json_string(value, "predicate").orElse(_json_string(value, "label")).getOrElse(""),
      _json_string(value, "target").getOrElse("")
    )
    val invalid = values.find(x => key(x).productIterator.exists(_.toString.isEmpty))
    if (invalid.nonEmpty)
      Left("RDF graph edge requires source, predicate/label, and target.")
    else
      values.groupBy(key).toVector.sortBy(_._1).
        foldLeft[Either[String, Vector[Json]]](Right(Vector.empty)) {
          case (left @ Left(_), _) => left
          case (Right(result), (edgekey, entries)) =>
            val distinct = entries.distinct
            if (distinct.size > 1)
              Left(s"Conflicting RDF graph edge metadata: ${edgekey._1} ${edgekey._2} ${edgekey._3}")
            else
              Right(result :+ distinct.head)
        }
  }

  private def _resource_invalid(base: Projection, resource: Resource, message: String): Diagnostic =
    _diagnostic(
      "sie.handoff.invalid",
      "error",
      s"Invalid ${resource.kind} resource: ${message}",
      base.projectref,
      base.projection,
      Some(resource.kind)
    )

  private def _diagnostic(
    code: String,
    severity: String,
    message: String,
    projectref: String,
    projection: String,
    resourcekind: Option[String] = None
  ): Diagnostic = Diagnostic(code, severity, message, projectref, projection, resourcekind)

  private def _parse_json(path: Path): Either[String, Json] =
    try parser.parse(Files.readString(path, StandardCharsets.UTF_8)).left.map(_.message)
    catch {
      case NonFatal(e) => Left(Option(e.getMessage).getOrElse(e.getClass.getSimpleName))
    }

  private def _resolve_path(projectroot: Path, value: String): Path = {
    val path = java.nio.file.Paths.get(value)
    if (path.isAbsolute) path.normalize else projectroot.resolve(path).toAbsolutePath.normalize
  }

  private def _safe_resource_path(root: Path, href: String): Option[Path] = {
    if (!_is_safe_relative_resource_href(href))
      None
    else try {
      val candidate = java.nio.file.Paths.get(href.trim)
      val normalizedroot = root.toAbsolutePath.normalize
      val realroot = if (Files.exists(normalizedroot)) normalizedroot.toRealPath() else normalizedroot
      val resolved = realroot.resolve(candidate).normalize
      if (!resolved.startsWith(realroot))
        None
      else {
        val checked = (0 until candidate.getNameCount).foldLeft(Option(realroot)) {
          case (None, _) => None
          case (Some(parent), index) =>
            val child = parent.resolve(candidate.getName(index))
            if (Files.exists(child, LinkOption.NOFOLLOW_LINKS)) {
              val realchild = child.toRealPath()
              if (realchild.startsWith(realroot)) Some(realchild) else None
            } else
              Some(child)
        }
        checked.filter(_.normalize.startsWith(realroot))
      }
    } catch {
      case NonFatal(_) => None
    }
  }

  private def _is_safe_relative_resource_href(href: String): Boolean = {
    val value = href.trim
    val urischeme = "^[A-Za-z][A-Za-z0-9+.-]*:.*$".r
    if (value.isEmpty || value == "." || value.contains("\\") || value.contains('?') || value.contains('#') ||
      urischeme.pattern.matcher(value).matches || value.split('/').contains(".."))
      false
    else try {
      val candidate = java.nio.file.Paths.get(value)
      !candidate.isAbsolute && candidate.getNameCount > 0
    } catch {
      case NonFatal(_) => false
    }
  }

  private def _string(cursor: HCursor, field: String): Option[String] =
    cursor.get[String](field).toOption

  private def _strings(cursor: HCursor, fields: String*): Vector[String] =
    fields.iterator.flatMap(field => cursor.downField(field).focus.flatMap(_.asArray).toVector.flatten).
      flatMap(_.asString).map(_.trim).filter(_.nonEmpty).toVector.distinct

  private def _json_string(value: Json, field: String): Option[String] =
    value.hcursor.get[String](field).toOption

  private def _uri_segment(value: String): String =
    java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
}
