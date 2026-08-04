package cozy.publication

import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path}
import java.security.MessageDigest
import scala.collection.JavaConverters._
import scala.util.Try
import org.goldenport.RAISE
import org.smartdox.metadata.PublishMetadata.{ImageReference, VideoPresentation, VideoReference, VideoStatus}
import play.api.libs.json.{JsArray, JsObject, JsString, JsValue, Json}

/*
 * @since   Aug.  4, 2026
 * @version Aug.  4, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyArticleMediaRegistry {
  private final case class Bundle(
    filename: String,
    name: String,
    entries: Vector[Entry],
    rawdigest: String
  )

  final case class Entry(
    bundleName: String,
    path: String,
    key: String,
    metadata: JsValue
  )

  final case class Snapshot(
    entries: Vector[Entry],
    bundleDigests: Map[String, String] = Map.empty
  )

  final case class UpsertResult(
    bundlePath: Path,
    entryPaths: Vector[String],
    snapshot: Snapshot
  )

  def load(root: Path): Snapshot = {
    _validate_root(root)
    val stream = Files.list(root)
    try {
      val bundles = stream.iterator().asScala.toVector
        .filter(_is_direct_json_filename)
        .sortBy(_.getFileName.toString)
        .map(_load_bundle)
      val entries = bundles.flatMap(_.entries)
      _validate_duplicates(entries)
      Snapshot(
        entries.sortBy(x => (x.path, x.bundleName)),
        bundles.map(x => x.name -> x.rawdigest).toMap
      )
    } finally {
      stream.close()
    }
  }

  def upsert(
    root: Path,
    bundleName: String,
    publicationResult: CozyArticleMediaPublication.Result,
    integrityResults: Vector[CozyArticleMediaIntegrity.Result],
    expectedBundleDigests: Map[String, String] = Map.empty
  ): UpsertResult = {
    _validate_root(root)
    if (expectedBundleDigests == null)
      _invalid("Publication bundle expected configured digests must be defined")
    val name = _validate_bundle_name(bundleName)
    val bundlepath = root.resolve(s"$name.json")
    _validate_target_bundle(bundlepath, name)
    val snapshot = load(root)
    if (expectedBundleDigests.nonEmpty && expectedBundleDigests != snapshot.bundleDigests)
      _invalid("Publication bundle stale configured snapshot")
    val publication = _publication_entry(publicationResult)
    val integrities = _integrity_entries(publicationResult, integrityResults)
    val requested = (publication +: integrities).sortBy(x => (x.path, x.key))
    _validate_requested_duplicates(requested)
    _validate_requested_owners(snapshot, name, requested)
    _validate_article_owners(snapshot, name, publication.path)
    snapshot.bundleDigests.getOrElse(name,
      _invalid(s"Publication bundle digest is missing from configured snapshot: $name")
    )
    val integrityprefix = "metadata/article-media-integrity/" + publication.path.stripPrefix("metadata/article-media/").stripSuffix(".json") + "/"
    CozyPublicationCompiler.replaceMetadata(
      root,
      name,
      requested.map(x => x.path -> x.metadata),
      Vector(integrityprefix),
      snapshot.bundleDigests
    )
    UpsertResult(bundlepath, requested.map(_.path), load(root))
  }

  private def _is_direct_json_filename(path: Path): Boolean =
    path.getFileName.toString.endsWith(".json")

  private def _load_bundle(path: Path): Bundle = {
    val filename = path.getFileName.toString
    if (Files.isSymbolicLink(path))
      _invalid(s"Configured publication bundle must not be a symbolic link: $filename")
    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
      _invalid(s"Configured publication bundle must be a direct regular file: $filename")
    val bytes = Files.readAllBytes(path)
    val json = Try(Json.parse(new String(bytes, StandardCharsets.UTF_8))).getOrElse(
      _invalid(s"Configured publication bundle is malformed JSON: $filename")
    )
    val bundle = json.asOpt[JsObject].getOrElse(
      _invalid(s"Configured publication bundle must be a JSON object: $filename")
    )
    if ((bundle \ "type").asOpt[String].forall(_ != "publication-bundle"))
      _invalid(s"Configured publication bundle has invalid type: $filename")
    val name = _bundle_name(bundle, filename)
    val stem = _filename_stem(filename)
    if (name != stem)
      _invalid(s"Configured publication bundle publication.name must match filename stem: $filename")
    val entries = bundle.value.get("entries") match {
      case Some(JsArray(values)) => values.toVector
      case _ => _invalid(s"Configured publication bundle entries must be an array: $name")
    }
    Bundle(filename, name, entries.map(_entry(name, _)), _sha256(bytes))
  }

  private def _bundle_name(bundle: JsObject, filename: String): String = {
    val publication = (bundle \ "publication").asOpt[JsObject].getOrElse(
      _invalid(s"Configured publication bundle has invalid publication: $filename")
    )
    publication.value.get("name") match {
      case Some(JsString(name)) if name.matches("[A-Za-z0-9][A-Za-z0-9._-]*") && name != "." && name != ".." &&
        !name.contains('/') && !name.contains('\\') && !name.contains('\u0000') => _validate_bundle_name(name)
      case _ => _invalid(s"Configured publication bundle has invalid publication.name: $filename")
    }
  }

  private def _entry(bundlename: String, value: JsValue): Entry = {
    val entry = value.asOpt[JsObject].getOrElse(
      _invalid(s"Configured publication bundle entry must be an object: $bundlename")
    )
    val path = entry.value.get("path") match {
      case Some(JsString(value)) => _validate_path(value, bundlename)
      case _ => _invalid(s"Configured publication bundle entry path is invalid: $bundlename")
    }
    val key = _canonical_key(path)
    entry.value.get("key").foreach {
      case JsString(value) if value == key =>
      case _ => _invalid(s"Configured publication bundle entry key is invalid for $path in $bundlename")
    }
    val metadata = entry.value.getOrElse("metadata", _invalid(s"Configured publication bundle entry metadata is missing for $path in $bundlename"))
    _validate_recognized_article_media(path, key, metadata, bundlename)
    Entry(bundlename, path, key, metadata)
  }

  private def _validate_recognized_article_media(path: String, key: String, metadata: JsValue, bundlename: String): Unit =
    if (path.startsWith("metadata/article-media/")) {
      val result = _guarded("Article-media publication metadata is invalid", _strict_result(metadata))
      _validate_canonical_recognized_entry(path, key, metadata, result.entryPath, result.metadata, bundlename)
    } else if (path.startsWith("metadata/article-media-integrity/")) {
      val result = _guarded("Article-media integrity metadata is invalid", _integrity_result(metadata))
      _validate_canonical_recognized_entry(path, key, metadata, result.entryPath, result.metadata, bundlename)
    }

  private def _strict_result(metadata: JsValue): CozyArticleMediaPublication.Result = {
    val value = _json_object(metadata)
    _validate_fields(value, Set("type", "article", "variants"), Set.empty)
    if (_required_string(value, "type") != "article-media-publication")
      _invalid("Article-media publication metadata type is invalid")
    val article = _json_object(_required(value, "article"))
    _validate_fields(article, Set("identity"), Set.empty)
    val variants = _json_object(_required(value, "variants"))
    val parsedvariants = variants.fields.toVector.map { case (locale, variant) =>
      _strict_variant(locale, variant)
    }
    CozyArticleMediaPublication.produce(_required_string(article, "identity"), parsedvariants)
  }

  private def _strict_variant(locale: String, value: JsValue): CozyArticleMediaPublication.Variant = {
    val variant = _json_object(value)
    _validate_fields(variant, Set.empty, Set("infographic", "video"))
    CozyArticleMediaPublication.Variant(
      locale = locale,
      infographic = variant.value.get("infographic").map(_strict_infographic),
      video = variant.value.get("video").map(_strict_video)
    )
  }

  private def _strict_infographic(value: JsValue): ImageReference = {
    val infographic = _json_object(value)
    _validate_fields(infographic, Set("public_path"), Set("media_type", "alt"))
    ImageReference(
      publicPath = _uri(_required_string(infographic, "public_path")),
      mediaType = _optional_string(infographic, "media_type"),
      alt = _optional_string(infographic, "alt")
    )
  }

  private def _strict_video(value: JsValue): VideoReference = {
    val video = _json_object(value)
    _validate_fields(video, Set("presentation", "status"), Set("provider", "watch_url", "content_url"))
    VideoReference(
      presentation = _video_presentation(_required_string(video, "presentation")),
      status = _video_status(_required_string(video, "status")),
      provider = _optional_string(video, "provider"),
      watchUrl = _optional_string(video, "watch_url").map(_uri),
      contentUrl = _optional_string(video, "content_url").map(_uri)
    )
  }

  private def _integrity_result(metadata: JsValue): CozyArticleMediaIntegrity.Result = {
    val value = _json_object(metadata)
    _validate_fields(value, Set("schema", "articleIdentity", "locale", "role", "artifact", "publicPath", "repositoryPath", "mediaType", "sha256", "provenance", "publicationState"), Set.empty)
    if (_required_string(value, "schema") != "cozy.article-media-integrity.v1")
      _invalid("Article-media integrity metadata schema is invalid")
    val role = _integrity_role(_required_string(value, "role"))
    CozyArticleMediaIntegrity.produce(CozyArticleMediaIntegrity.Input(
      articleIdentity = _required_string(value, "articleIdentity"),
      locale = _required_string(value, "locale"),
      role = role,
      artifact = _integrity_artifact(_required(value, "artifact")),
      publicPath = _uri(_required_string(value, "publicPath")),
      repositoryPath = _required_string(value, "repositoryPath"),
      mediaType = _required_string(value, "mediaType"),
      sha256 = _required_string(value, "sha256"),
      provenance = _integrity_provenance(_required(value, "provenance")),
      publicationState = _publication_state(_required_string(value, "publicationState"))
    ))
  }

  private def _integrity_artifact(value: JsValue): CozyArticleMediaIntegrity.Artifact = {
    val artifact = _json_object(value)
    _validate_fields(artifact, Set("identity", "version"), Set.empty)
    CozyArticleMediaIntegrity.Artifact(_required_string(artifact, "identity"), _required_string(artifact, "version"))
  }

  private def _integrity_provenance(value: JsValue): CozyArticleMediaIntegrity.Provenance = {
    val provenance = _json_object(value)
    _required_string(provenance, "kind") match {
      case "video-publication" =>
        _validate_fields(provenance, Set("kind", "videoManifest", "repositoryRegistry"), Set.empty)
        CozyArticleMediaIntegrity.VideoPublication(
          _required_string(provenance, "videoManifest"),
          _required_string(provenance, "repositoryRegistry")
        )
      case "media-package" =>
        _validate_fields(provenance, Set("kind", "descriptor", "resourceId", "buildManifest"), Set.empty)
        CozyArticleMediaIntegrity.MediaPackage(
          _required_string(provenance, "descriptor"),
          _required_string(provenance, "resourceId"),
          _required_string(provenance, "buildManifest")
        )
      case _ => _invalid("Article-media integrity provenance kind is invalid")
    }
  }

  private def _video_presentation(value: String): VideoPresentation =
    value match {
      case x if x == VideoPresentation.ExternalLink.name => VideoPresentation.ExternalLink
      case x if x == VideoPresentation.SiteHosted.name => VideoPresentation.SiteHosted
      case _ => _invalid("Article-media video presentation is invalid")
    }

  private def _video_status(value: String): VideoStatus =
    value match {
      case x if x == VideoStatus.Draft.name => VideoStatus.Draft
      case x if x == VideoStatus.Published.name => VideoStatus.Published
      case x if x == VideoStatus.Withdrawn.name => VideoStatus.Withdrawn
      case _ => _invalid("Article-media video status is invalid")
    }

  private def _integrity_role(value: String): CozyArticleMediaIntegrity.Role =
    value match {
      case x if x == CozyArticleMediaIntegrity.Role.Infographic.name => CozyArticleMediaIntegrity.Role.Infographic
      case x if x == CozyArticleMediaIntegrity.Role.Video.name => CozyArticleMediaIntegrity.Role.Video
      case _ => _invalid("Article-media integrity role is invalid")
    }

  private def _publication_state(value: String): CozyArticleMediaIntegrity.PublicationState =
    value match {
      case x if x == CozyArticleMediaIntegrity.PublicationState.Registered.name => CozyArticleMediaIntegrity.PublicationState.Registered
      case x if x == CozyArticleMediaIntegrity.PublicationState.Published.name => CozyArticleMediaIntegrity.PublicationState.Published
      case x if x == CozyArticleMediaIntegrity.PublicationState.Withdrawn.name => CozyArticleMediaIntegrity.PublicationState.Withdrawn
      case _ => _invalid("Article-media integrity publication state is invalid")
    }

  private def _validate_canonical_recognized_entry(
    path: String,
    key: String,
    metadata: JsValue,
    canonicalpath: String,
    canonicalmetadata: JsObject,
    bundlename: String
  ): Unit =
    if (path != canonicalpath || key != _canonical_key(canonicalpath) || metadata != canonicalmetadata)
      _invalid(s"Configured publication bundle article-media entry is not canonical: $path in $bundlename")

  private def _guarded[A](message: String, f: => A): A =
    Try(f).getOrElse(_invalid(message))

  private def _json_object(value: JsValue): JsObject =
    value.asOpt[JsObject].getOrElse(_invalid("Article-media metadata value must be an object"))

  private def _validate_fields(value: JsObject, required: Set[String], optional: Set[String]): Unit = {
    val keys = value.keys
    if (!required.subsetOf(keys) || !keys.subsetOf(required ++ optional))
      _invalid("Article-media metadata fields are invalid")
  }

  private def _required(value: JsObject, name: String): JsValue =
    value.value.getOrElse(name, _invalid(s"Article-media metadata field is required: $name"))

  private def _required_string(value: JsObject, name: String): String =
    _required(value, name) match {
      case JsString(string) => string
      case _ => _invalid(s"Article-media metadata field must be a string: $name")
    }

  private def _optional_string(value: JsObject, name: String): Option[String] =
    value.value.get(name).map {
      case JsString(string) => string
      case _ => _invalid(s"Article-media metadata field must be a string: $name")
    }

  private def _uri(value: String): URI =
    new URI(value)

  private def _publication_entry(result: CozyArticleMediaPublication.Result): Entry = {
    if (result == null || result.publication == null || result.metadata == null)
      _invalid("Article-media publication result must be defined")
    val variants = Option(result.publication.variants).getOrElse(
      _invalid("Article-media publication result variants must be defined")
    ).map { variant =>
      if (variant == null || variant.infographic == null || variant.video == null)
        _invalid("Article-media publication result variant must be defined")
      CozyArticleMediaPublication.Variant(variant.locale, variant.infographic, variant.video)
    }
    val canonical = CozyArticleMediaPublication.produce(result.publication.articleIdentity, variants)
    if (result != canonical)
      _invalid("Article-media publication result must be canonical")
    Entry("", canonical.entryPath, _canonical_key(canonical.entryPath), canonical.metadata)
  }

  private def _integrity_entries(
    publicationresult: CozyArticleMediaPublication.Result,
    integrityresults: Vector[CozyArticleMediaIntegrity.Result]
  ): Vector[Entry] = {
    if (publicationresult == null || publicationresult.publication == null)
      _invalid("Article-media publication result must be defined")
    if (integrityresults == null)
      _invalid("Article-media integrity results must be defined")
    val publication = _publication_entry(publicationresult)
    val identity = publication.path.stripPrefix("metadata/article-media/").stripSuffix(".json")
    integrityresults.map { result =>
      if (result == null || result.record == null || result.metadata == null)
        _invalid("Article-media integrity result must be defined")
      val record = result.record
      val canonical = CozyArticleMediaIntegrity.produce(CozyArticleMediaIntegrity.Input(
        articleIdentity = record.articleIdentity,
        locale = record.locale,
        role = record.role,
        artifact = record.artifact,
        publicPath = record.publicPath,
        repositoryPath = record.repositoryPath,
        mediaType = record.mediaType,
        sha256 = record.sha256,
        provenance = record.provenance,
        publicationState = record.publicationState
      ))
      if (result != canonical)
        _invalid("Article-media integrity result must be canonical")
      val recordidentity = canonical.record.articleIdentity
      if (recordidentity != identity)
        _invalid(s"Article-media integrity identity must match strict publication identity: $recordidentity")
      Entry("", canonical.entryPath, _canonical_key(canonical.entryPath), canonical.metadata)
    }
  }

  private def _validate_target_bundle(bundlepath: Path, bundlename: String): Unit = {
    if (Files.isSymbolicLink(bundlepath))
      _invalid(s"Publication bundle target must not be a symbolic link: $bundlename")
    if (!Files.isRegularFile(bundlepath, LinkOption.NOFOLLOW_LINKS))
      _invalid(s"Publication bundle not found: $bundlename")
  }

  private def _validate_root(root: Path): Unit =
    if (root == null || !Files.isDirectory(root))
      _invalid(s"Configured publication root must be an existing directory: $root")

  private def _validate_bundle_name(bundlename: String): String = {
    val name = Option(bundlename).getOrElse("")
    if (!name.matches("[A-Za-z0-9][A-Za-z0-9._-]*") || name == "." || name == ".." ||
      name.contains('/') || name.contains('\\') || name.contains('\u0000'))
      _invalid(s"Publication bundle name must be a safe filename segment: $bundlename")
    name
  }

  private def _filename_stem(filename: String): String = {
    if (!filename.endsWith(".json"))
      _invalid(s"Configured publication bundle filename must end with .json: $filename")
    _validate_bundle_name(filename.stripSuffix(".json"))
  }

  private def _validate_path(value: String, bundlename: String): String = {
    val path = Option(value).getOrElse("")
    val segments = path.split("/", -1).toVector
    if (path.isEmpty || path != path.trim || path.startsWith("/") || path.contains('\\') || path.contains('\u0000') ||
      !path.startsWith("metadata/") || !path.endsWith(".json") || segments.exists(_.isEmpty) ||
      segments.contains(".") || segments.contains(".."))
      _invalid(s"Configured publication bundle entry path is invalid in $bundlename: $value")
    val key = _canonical_key(path)
    if (key.isEmpty || key.split("/", -1).exists(_.isEmpty))
      _invalid(s"Configured publication bundle entry path has an empty key in $bundlename: $value")
    path
  }

  private def _canonical_key(path: String): String =
    path.stripSuffix(".json").stripPrefix("metadata/")

  private def _validate_duplicates(entries: Vector[Entry]): Unit = {
    _duplicate(entries, _.path, "path")
    _duplicate(entries, _.key, "logical key")
  }

  private def _validate_requested_duplicates(entries: Vector[Entry]): Unit = {
    _duplicate(entries, _.path, "requested path")
    _duplicate(entries, _.key, "requested logical key")
  }

  private def _duplicate(entries: Vector[Entry], selector: Entry => String, label: String): Unit =
    entries.groupBy(selector).toVector.sortBy(_._1).collectFirst {
      case (value, owners) if owners.size > 1 =>
        val names = owners.map(_.bundleName).filter(_.nonEmpty).sorted
        val ownerlabel = if (names.nonEmpty) names.mkString(", ") else "requested entries"
        _invalid(s"Duplicate publication bundle $label '$value': $ownerlabel")
    }

  private def _validate_requested_owners(snapshot: Snapshot, bundlename: String, requested: Vector[Entry]): Unit =
    requested.foreach { entry =>
      snapshot.entries.find(x => (x.path == entry.path || x.key == entry.key) && x.bundleName != bundlename).foreach { owner =>
        _invalid(s"Publication registry entry is owned by another bundle: ${entry.path} (${owner.bundleName})")
      }
    }

  private def _validate_article_owners(snapshot: Snapshot, bundlename: String, publicationpath: String): Unit = {
    val identity = publicationpath.stripPrefix("metadata/article-media/").stripSuffix(".json")
    val integrityprefix = s"metadata/article-media-integrity/${identity}/"
    snapshot.entries.find { entry =>
      entry.bundleName != bundlename && (entry.path == publicationpath || entry.path.startsWith(integrityprefix))
    }.foreach { owner =>
      _invalid(s"Publication registry article-media identity is owned by another bundle: ${owner.path} (${owner.bundleName})")
    }
  }

  private def _sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(x => f"${x & 0xff}%02x").mkString

  private def _invalid(message: String): Nothing =
    RAISE.invalidArgumentFault(message)
}
