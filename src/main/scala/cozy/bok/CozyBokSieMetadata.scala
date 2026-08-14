package cozy.bok

import org.goldenport.RAISE
import org.goldenport.cli.{Request => CliRequest}
import org.goldenport.cli.spec
import cozy.bok.scenario.ScenarioMetadata
import cozy.bok.BibliographyEntry._
import cozy.config.CozyProjectYamlConfig
import cozy.publication.{CozyArticleMediaBuildContext, CozyArticleMediaInfographicCommand, CozyArticleMediaInfographicEvidence, CozyArticleMediaVideoCommand}
import cozy.video.{CozyVideo, CozyVideoPublisher}
import org.smartdox.{Body, Document, Dox}
import org.smartdox.parser.Dox2Parser
import org.smartdox.transformers.Dox2HtmlTransformer
import org.smartdox.generator.{Context => SmartDoxContext}
import org.smartdox.metadata.DocumentMetaData
import org.goldenport.i18n.I18NContext
import java.net.URLEncoder
import java.time.{Instant, LocalDate, LocalDateTime, YearMonth, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.regex.Pattern
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths, StandardCopyOption}
import java.util.zip.{ZipEntry, ZipFile, ZipInputStream, ZipOutputStream}
import scala.collection.JavaConverters._
import scala.util.matching.Regex
import scala.util.control.NonFatal
import scala.sys.process._
import io.circe.{Decoder, HCursor, Json}
import io.circe.parser
import io.circe.syntax._

/*
 * @since   Aug. 14, 2026
 * @version Aug. 14, 2026
 * @author  ASAMI, Tomoharu
 */

private[cozy] trait CozyBokSieMetadata {
  self: CozyBokImplementation.type =>
  private[bok] def _copy_machine_metadata_artifacts(config: BuildConfig, target: Path): Unit = {
    _copy_if_exists(config.doxsitePath.resolve("site.ttl"), target.resolve("rdf").resolve("site.ttl"))
    _copy_if_exists(config.doxsitePath.resolve("site.jsonld"), target.resolve("rdf").resolve("site.jsonld"))
    _copy_if_exists(config.doxsitePath.resolve("metadata/rdf/graph.json"), target.resolve("metadata/rdf/graph.json"))
    _copy_if_exists(config.doxsitePath.resolve("metadata/glossary/terms.json"), target.resolve("metadata/glossary/terms.json"))
    _copy_if_exists(config.doxsitePath.resolve("metadata/bibliography/bibliography.json"), target.resolve("metadata/bibliography/bibliography.json"))
    _copy_if_exists(config.doxsitePath.resolve("metadata/scenarios/scenarios.json"), target.resolve("metadata/scenarios/scenarios.json"))
    _copy_if_exists(config.doxsitePath.resolve("metadata/tags/tags.json"), target.resolve("metadata/tags/tags.json"))
    _copy_directory(config.doxsitePath.resolve("metadata/repository/car"), target.resolve("metadata/repository/car"))
    _copy_directory(config.doxsitePath.resolve("metadata/cncf/component-references"), target.resolve("metadata/cncf/component-references"))
    _copy_directory(config.doxsitePath.resolve("metadata/catalog/projects"), target.resolve("metadata/catalog/projects"))
    _copy_directory(config.doxsitePath.resolve("metadata/projects"), target.resolve("metadata/projects"))
    _copy_directory(config.doxsitePath.resolve("metadata/artifacts/repository"), target.resolve("metadata/artifacts/repository"))
    _copy_directory(config.doxsitePath.resolve("metadata/releases"), target.resolve("metadata/releases"))
    _sync_sie_metadata(config, target)
    _sync_source_rdf_graph_metadata(config, target)
    _version_graph_summary(config, target)
    _write_knowledge_source_manifest(config, target)
  }

  private[bok] def _sie_index(config: BuildConfig): CozyBokSieHandoff.Index =
    CozyBokSieHandoff.load(config.project, _load_config(config.project), _safe_resolved_project_packages(config))

  private def _sync_sie_metadata(config: BuildConfig, target: Path): Unit = {
    val index = _sie_index(config)
    if (!index.isEmpty) {
      _write_text(target.resolve("metadata/sie/integration.json"), index.toJson.spaces2 + "\n")
      val graphpath = target.resolve("metadata/rdf/graph.json")
      if (Files.isRegularFile(graphpath)) {
        val graph = parser.parse(Files.readString(graphpath, StandardCharsets.UTF_8)).fold(
          error => RAISE.invalidArgumentFault(s"Invalid BoK RDF graph metadata: ${error.message}"),
          identity
        )
        val merged = CozyBokSieHandoff.mergeGraph(graph, index).fold(
          message => RAISE.invalidArgumentFault(s"Invalid SIE RDF handoff: ${message}"),
          identity
        )
        _write_text(graphpath, merged.spaces2 + "\n")
      }
    }
  }

  private def _sync_source_rdf_graph_metadata(config: BuildConfig, target: Path): Unit = {
    val sourcegraphpath = config.sourcepath.resolve("metadata/rdf/graph.json")
    if (Files.isRegularFile(sourcegraphpath)) {
      val sourcegraph = parser.parse(Files.readString(sourcegraphpath, StandardCharsets.UTF_8)).fold(
        error => RAISE.invalidArgumentFault(s"Invalid BoK source RDF graph metadata: ${error.message}"),
        identity
      )
      val graphpath = target.resolve("metadata/rdf/graph.json")
      if (Files.isRegularFile(graphpath)) {
        val graph = parser.parse(Files.readString(graphpath, StandardCharsets.UTF_8)).fold(
          error => RAISE.invalidArgumentFault(s"Invalid BoK RDF graph metadata: ${error.message}"),
          identity
        )
        val merged = CozyBokSieHandoff.mergeGraphSummaries(graph, Vector(sourcegraph)).fold(
          message => RAISE.invalidArgumentFault(s"Invalid BoK source RDF graph metadata: ${message}"),
          identity
        )
        _write_text(graphpath, merged.spaces2 + "\n")
      } else {
        _write_text(graphpath, sourcegraph.spaces2 + "\n")
      }
    }
  }

  private def _version_graph_summary(config: BuildConfig, target: Path): Unit = {
    val graphpath = target.resolve("metadata/rdf/graph.json")
    if (Files.isRegularFile(graphpath)) {
      val graph = parser.parse(Files.readString(graphpath, StandardCharsets.UTF_8)).fold(
        error => RAISE.invalidArgumentFault(s"Invalid BoK RDF graph metadata: ${error.message}"),
        identity
      )
      val graphobject = graph.asObject.getOrElse(
        RAISE.invalidArgumentFault("Invalid BoK RDF graph metadata: graph summary must be a JSON object.")
      )
      graphobject("schemaVersion").flatMap(_.asString).foreach { version =>
        if (version != "cozy.rdf-graph-summary.v1")
          RAISE.invalidArgumentFault(s"Unsupported BoK RDF graph summary schema: $version")
      }
      graphobject("kind").flatMap(_.asString).foreach { kind =>
        if (kind != "rdf-graph-summary")
          RAISE.invalidArgumentFault(s"Unsupported BoK RDF graph summary kind: $kind")
      }
      val nodes = graphobject("nodes").flatMap(_.asArray).getOrElse(
        RAISE.invalidArgumentFault("Invalid BoK RDF graph metadata: nodes must be an array.")
      )
      val edges = graphobject("edges").flatMap(_.asArray).getOrElse(
        RAISE.invalidArgumentFault("Invalid BoK RDF graph metadata: edges must be an array.")
      )
      val truncated = graphobject("truncated").flatMap(_.asBoolean).getOrElse(
        RAISE.invalidArgumentFault("Invalid BoK RDF graph metadata: truncated must be a boolean.")
      )
      val componentrefs = _validate_graph_nodes(nodes)
      _validate_graph_edges(edges)
      _validate_graph_component_refs(target, componentrefs)
      val sourceref = Json.obj(
        (Vector(
          "kind" -> Json.fromString("bok-site"),
          "value" -> Json.fromString(config.siteId)
        ) ++ config.siteUrl.map(x => "uri" -> Json.fromString(x))).toSeq: _*
      )
      val versioned = Json.fromJsonObject(
        graphobject.
          add("schemaVersion", Json.fromString("cozy.rdf-graph-summary.v1")).
          add("kind", Json.fromString("rdf-graph-summary")).
          add("sourceRef", sourceref).
          add("nodes", Json.fromValues(nodes)).
          add("edges", Json.fromValues(edges)).
          add("truncated", Json.fromBoolean(truncated))
      )
      _write_text(graphpath, versioned.spaces2 + "\n")
    }
  }

  private final case class GraphComponentRef(
    kind: String,
    name: String,
    organization: Option[String],
    version: Option[String],
    location: String
  )

  private final case class GraphComponentReferenceEntry(
    kind: String,
    name: String,
    organization: Option[String],
    versions: Vector[String],
    location: String
  )

  private def _validate_graph_nodes(nodes: Vector[Json]): Vector[GraphComponentRef] =
    nodes.zipWithIndex.flatMap { case (node, index) =>
      val nodeobject = node.asObject.getOrElse(
        RAISE.invalidArgumentFault(s"Invalid BoK RDF graph metadata: nodes[$index] must be an object.")
      )
      Vector("id", "label", "node_type").foreach { field =>
        _required_graph_field(nodeobject, field, s"nodes[$index]")
      }
      nodeobject("componentRef").map { componentref =>
        val location = s"nodes[$index].componentRef"
        val nodetype = nodeobject("node_type").flatMap(_.asString).map(_.trim).getOrElse("")
        if (nodetype != "component-reference")
          RAISE.invalidArgumentFault(
            s"Invalid BoK RDF graph metadata: $location is allowed only when node_type is component-reference."
          )
        val refobject = componentref.asObject.getOrElse(
          RAISE.invalidArgumentFault(s"Invalid BoK RDF graph metadata: $location must be a JSON object.")
        )
        val kind = _required_graph_field(refobject, "kind", location)
        if (!Set("car", "sar").contains(kind))
          RAISE.invalidArgumentFault(
            s"Invalid BoK RDF graph metadata: $location.kind must be car or sar."
          )
        GraphComponentRef(
          kind,
          _required_graph_field(refobject, "name", location),
          _optional_graph_field(refobject, "organization", location),
          _optional_graph_field(refobject, "version", location),
          location
        )
      }
    }

  private def _validate_graph_edges(edges: Vector[Json]): Unit =
    edges.zipWithIndex.foreach { case (edge, index) =>
      val edgeobject = edge.asObject.getOrElse(
        RAISE.invalidArgumentFault(s"Invalid BoK RDF graph metadata: edges[$index] must be an object.")
      )
      Vector("source", "predicate", "target").foreach { field =>
        _required_graph_field(edgeobject, field, s"edges[$index]")
      }
    }

  private def _required_graph_field(graphobject: io.circe.JsonObject, field: String, location: String): String =
    graphobject(field).flatMap(_.asString).map(_.trim).filter(_.nonEmpty).getOrElse(
      RAISE.invalidArgumentFault(s"Invalid BoK RDF graph metadata: $location.$field must be a non-empty string.")
    )

  private def _optional_graph_field(graphobject: io.circe.JsonObject, field: String, location: String): Option[String] =
    graphobject(field).map { value =>
      value.asString.map(_.trim).filter(_.nonEmpty).getOrElse(
        RAISE.invalidArgumentFault(
          s"Invalid BoK RDF graph metadata: $location.$field must be a non-empty string when present."
        )
      )
    }

  private def _validate_graph_component_refs(target: Path, refs: Vector[GraphComponentRef]): Unit =
    refs.groupBy(_.kind).foreach { case (kind, kindrefs) =>
      val entries = _load_graph_component_reference_index(target, kind)
      kindrefs.foreach { ref =>
        val matches = entries.filter { entry =>
          entry.kind == ref.kind &&
            entry.name == ref.name &&
            ref.organization.forall(x => entry.organization.contains(x)) &&
            ref.version.forall(x => entry.versions.contains(x))
        }
        if (matches.isEmpty)
          RAISE.invalidArgumentFault(
            s"Invalid BoK RDF graph metadata: ${ref.location} does not match any $kind component-reference index entry."
          )
        else if (matches.size > 1)
          RAISE.invalidArgumentFault(
            s"Invalid BoK RDF graph metadata: ${ref.location} matches multiple $kind component-reference index entries."
          )
      }
    }

  private def _load_graph_component_reference_index(target: Path, kind: String): Vector[GraphComponentReferenceEntry] = {
    val path = target.resolve("metadata/cncf/component-references").resolve(s"$kind.json")
    if (!Files.isRegularFile(path))
      RAISE.invalidArgumentFault(
        s"Invalid BoK RDF graph metadata: component-reference index metadata/cncf/component-references/$kind.json is missing."
      )
    val json = parser.parse(Files.readString(path, StandardCharsets.UTF_8)).fold(
      error => RAISE.invalidArgumentFault(
        s"Invalid BoK component-reference index metadata/cncf/component-references/$kind.json: ${error.message}"
      ),
      identity
    )
    val indexobject = json.asObject.getOrElse(
      RAISE.invalidArgumentFault(
        s"Invalid BoK component-reference index metadata/cncf/component-references/$kind.json: index must be a JSON object."
      )
    )
    indexobject("schemaVersion").flatMap(_.asString).foreach { version =>
      if (version != "cncf.component-reference-index.v1")
        RAISE.invalidArgumentFault(
          s"Invalid BoK component-reference index metadata/cncf/component-references/$kind.json: unsupported schemaVersion $version."
        )
    }
    indexobject("kind").flatMap(_.asString).foreach { indexkind =>
      if (indexkind != kind)
        RAISE.invalidArgumentFault(
          s"Invalid BoK component-reference index metadata/cncf/component-references/$kind.json: kind must be $kind."
        )
    }
    val entries = indexobject("entries").flatMap(_.asArray).getOrElse(
      RAISE.invalidArgumentFault(
        s"Invalid BoK component-reference index metadata/cncf/component-references/$kind.json: entries must be an array."
      )
    )
    entries.zipWithIndex.map { case (entry, index) =>
      val location = s"metadata/cncf/component-references/$kind.json.entries[$index]"
      val entryobject = entry.asObject.getOrElse(
        RAISE.invalidArgumentFault(s"Invalid BoK component-reference index: $location must be a JSON object.")
      )
      GraphComponentReferenceEntry(
        _required_graph_field(entryobject, "kind", location),
        _required_graph_field(entryobject, "name", location),
        _optional_graph_field(entryobject, "organization", location),
        _component_reference_entry_versions(entryobject, location),
        location
      )
    }
  }

  private def _component_reference_entry_versions(
      entryobject: io.circe.JsonObject,
      location: String
  ): Vector[String] =
    entryobject("versions").flatMap(_.asArray).getOrElse(Vector.empty).zipWithIndex.map { case (version, index) =>
      val versionlocation = s"$location.versions[$index]"
      val versionobject = version.asObject.getOrElse(
        RAISE.invalidArgumentFault(s"Invalid BoK component-reference index: $versionlocation must be a JSON object.")
      )
      _required_graph_field(versionobject, "version", versionlocation)
    }

  private def _write_knowledge_source_manifest(config: BuildConfig, target: Path): Unit = {
    val terms = target.resolve("metadata/glossary/terms.json")
    if (_source_declares_glossary_terms(config) && !Files.isRegularFile(terms))
      RAISE.invalidArgumentFault(
        "SmartDox glossary metadata was not generated even though BoK source declares glossary terms. " +
          "Update the dox/SmartDox runtime used by cozy bok build; Cozy does not reconstruct the missing terms.json handoff."
      )
    val resources = Vector(
      KnowledgeSourceResource("glossary-terms", "metadata/glossary/terms.json", "application/json"),
      KnowledgeSourceResource("rdf-jsonld", "rdf/site.jsonld", "application/ld+json"),
      KnowledgeSourceResource("rdf-turtle", "rdf/site.ttl", "text/turtle"),
      KnowledgeSourceResource("rdf-graph-summary", "metadata/rdf/graph.json", "application/json"),
      KnowledgeSourceResource("component-reference-index", "metadata/cncf/component-references/car.json", "application/json"),
      KnowledgeSourceResource("component-reference-index", "metadata/cncf/component-references/sar.json", "application/json")
    ).filter(x => Files.isRegularFile(target.resolve(x.href))) ++
      _knowledge_source_component_resources(target)
    val sourceref = Json.obj(
      (Vector(
        "kind" -> Json.fromString("bok-site"),
        "value" -> Json.fromString(config.siteId)
      ) ++ config.siteUrl.map(x => "uri" -> Json.fromString(x))).toSeq: _*
    )
    val manifest = Json.obj(
      "schemaVersion" -> Json.fromString("cncf.knowledge-source.v1"),
      "kind" -> Json.fromString("bok-site"),
      "id" -> Json.fromString(config.siteId),
      "label" -> Json.fromString(config.siteTitle),
      "sourceRef" -> sourceref,
      "resources" -> Json.fromValues(resources.map(_.toJson))
    )
    _write_text(
      target.resolve("metadata/cncf/knowledge-source.json"),
      manifest.spaces2 + "\n"
    )
  }

  private def _knowledge_source_component_resources(target: Path): Vector[KnowledgeSourceResource] = {
    val projectroot = target.resolve("metadata/projects")
    if (!Files.isDirectory(projectroot))
      Vector.empty
    else {
      val stream = Files.walk(projectroot)
      try {
        stream.iterator.asScala.toVector.
          filter(path =>
            Files.isRegularFile(path) &&
              path.getFileName.toString == "metadata.json" &&
              Option(path.getParent).flatMap(x => Option(x.getParent)).contains(projectroot)
          ).
          flatMap(_knowledge_source_component_identity).
          sorted.
          flatMap { name =>
            Vector(
              KnowledgeSourceResource("component-catalog-project", s"metadata/catalog/projects/$name.json", "application/json"),
              KnowledgeSourceResource("component-project-metadata", s"metadata/projects/$name/metadata.json", "application/json"),
              KnowledgeSourceResource("component-repository-artifact", s"metadata/artifacts/repository/$name.json", "application/json"),
              KnowledgeSourceResource("component-release-history", s"metadata/releases/$name.json", "application/json")
            ).filter(x => Files.isRegularFile(target.resolve(x.href)))
          }
      } finally {
        stream.close()
      }
    }
  }

  private def _knowledge_source_component_identity(path: Path): Option[String] = {
    val json = parser.parse(Files.readString(path, StandardCharsets.UTF_8)).fold(
      error => RAISE.invalidArgumentFault(s"Invalid Cozy component project metadata JSON: $path: ${error.message}"),
      identity
    )
    val cursor = json.hcursor
    val schema = cursor.get[String]("schema").toOption
    val metadatatype = cursor.get[String]("type").toOption
    val project = cursor.downField("project")
    val name = project.get[String]("name").toOption.map(_.trim).filter(_.nonEmpty)
    val kind = project.get[String]("kind").toOption.map(_.trim.toLowerCase(Locale.ROOT))
    val canonicalname = Option(path.getParent).flatMap(x => Option(x.getFileName)).map(_.toString)
    (schema, metadatatype, name, kind) match {
      case (Some("cozy.publish-project.v1"), Some("project-metadata"), Some(componentname), Some(componentkind))
          if canonicalname.contains(componentname) && (componentkind == "car" || componentkind == "sar") =>
        Some(componentname)
      case _ =>
        None
    }
  }

  private def _source_declares_glossary_terms(config: BuildConfig): Boolean = {
    val root = config.sourcepath.resolve("glossary")
    if (!Files.isDirectory(root))
      false
    else {
      val stream = Files.walk(root)
      try {
        stream.iterator.asScala.exists { path =>
          Files.isRegularFile(path) &&
          _is_source_document(path) &&
          !_is_index_source_document(path)
        }
      } finally {
        stream.close()
      }
    }
  }

  private[bok] def _copy_if_exists(source: Path, target: Path): Unit =
    if (Files.isRegularFile(source)) {
      Option(target.getParent).foreach(Files.createDirectories(_))
      Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
    }

}
