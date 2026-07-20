package cozy.video

import cozy.config.CozyProjectYamlConfig
import io.circe.{Decoder, HCursor, Json}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import scala.collection.JavaConverters._
import org.goldenport.RAISE
import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.io.InputSource

/*
 * Renderer-neutral credit catalog, selection, and projection contract.
 * Personal attribution policy belongs in discovered profiles, never here.
 *
 * @since   Jul. 20, 2026
 * @version Jul. 20, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyVideoCredits {
  val SCHEMA = "cozy.video.credits.v1"

  final case class Settings(
    profile: Option[String],
    include: Vector[String],
    exclude: Vector[String]
  )
  object Settings {
    val empty: Settings = Settings(None, Vector.empty, Vector.empty)

    implicit val decoder: Decoder[Settings] = (c: HCursor) =>
      for {
        profile <- c.downField("profile").as[Option[String]]
        include <- _string_vector(c, "include")
        exclude <- _string_vector(c, "exclude")
      } yield Settings(_normalized_option(profile), _normalized(include), _normalized(exclude))
  }

  final case class CreditItem(
    id: String,
    category: String,
    labels: Map[String, String],
    publicationText: Map[String, String],
    creator: Option[String],
    sourceUrl: Option[String],
    termsUrl: Option[String],
    obligation: String,
    surfaces: Vector[String],
    group: Option[String],
    order: Int
  ) {
    def label(locale: String): Option[String] = _localized(labels, locale)
    def publication(locale: String): Option[String] = _localized(publicationText, locale)
    def isRequired: Boolean = obligation == "required"
    def appearsOn(surface: String): Boolean = surfaces.contains(surface)
  }
  object CreditItem {
    implicit val decoder: Decoder[CreditItem] = (c: HCursor) =>
      for {
        id <- c.downField("id").as[String]
        category <- c.downField("category").as[Option[String]]
        labels <- _localized_map(c, "labels", "label")
        publicationtext <- _localized_map(c, "publicationText", "publication-text")
        creator <- c.downField("creator").as[Option[String]]
        sourceurl <- _optional_string(c, "sourceUrl", "source-url")
        termsurl <- _optional_string(c, "termsUrl", "terms-url")
        obligation <- c.downField("obligation").as[Option[String]]
        surfaces <- _string_vector(c, "surfaces")
        group <- c.downField("group").as[Option[String]]
        order <- c.downField("order").as[Option[Int]]
      } yield CreditItem(
        _required_id(id, "credit item id"),
        category.map(_.trim.toLowerCase(java.util.Locale.ROOT)).filter(_.nonEmpty).getOrElse("material"),
        labels,
        publicationtext,
        _normalized_option(creator),
        _normalized_option(sourceurl),
        _normalized_option(termsurl),
        obligation.map(_.trim.toLowerCase(java.util.Locale.ROOT)).filter(_.nonEmpty).getOrElse("required"),
        if (surfaces.isEmpty) Vector("publication", "rdf", "video") else _normalized(surfaces.map(_.toLowerCase(java.util.Locale.ROOT))),
        _normalized_option(group),
        order.getOrElse(100)
      )
  }

  final case class SelectorCondition(
    characterIds: Vector[String],
    assetTags: Vector[String],
    assetLicenses: Vector[String],
    assetProvenance: Vector[String],
    audioProviders: Vector[String],
    voiceIdentities: Vector[String],
    voiceIds: Vector[String],
    modelIdentities: Vector[String],
    locales: Vector[String]
  ) {
    def isEmpty: Boolean =
      characterIds.isEmpty && assetTags.isEmpty && assetLicenses.isEmpty && assetProvenance.isEmpty &&
        audioProviders.isEmpty && voiceIdentities.isEmpty && voiceIds.isEmpty && modelIdentities.isEmpty && locales.isEmpty
  }
  object SelectorCondition {
    implicit val decoder: Decoder[SelectorCondition] = (c: HCursor) =>
      for {
        characterids <- _string_vector(c, "any-character-id", "character-ids", "characterIds")
        assettags <- _string_vector(c, "any-asset-tag", "asset-tags", "assetTags")
        assetlicenses <- _string_vector(c, "asset-license", "asset-licenses", "assetLicenses")
        assetprovenance <- _string_vector(c, "asset-provenance", "assetProvenance")
        audioproviders <- _string_vector(c, "audio-provider", "audio-providers", "audioProviders")
        voiceidentities <- _string_vector(c, "voice-identity", "voice-identities", "voiceIdentities")
        voiceids <- _string_vector(c, "voice-id", "voice-ids", "voiceIds")
        modelidentities <- _string_vector(c, "model-identity", "model-identities", "modelIdentities")
        locales <- _string_vector(c, "locale", "locales")
      } yield SelectorCondition(
        _normalized(characterids),
        _normalized(assettags),
        _normalized(assetlicenses),
        _normalized(assetprovenance),
        _normalized(audioproviders.map(_.toLowerCase(java.util.Locale.ROOT))),
        _normalized(voiceidentities),
        _normalized(voiceids),
        _normalized(modelidentities),
        _normalized(locales.map(_.toLowerCase(java.util.Locale.ROOT)))
      )
  }

  final case class Selector(conditions: SelectorCondition, include: Vector[String], exclude: Vector[String])
  object Selector {
    implicit val decoder: Decoder[Selector] = (c: HCursor) =>
      for {
        conditions <- c.downField("when").as[SelectorCondition]
        include <- _string_vector(c, "include")
        exclude <- _string_vector(c, "exclude")
      } yield Selector(conditions, _normalized(include), _normalized(exclude))
  }

  final case class Presentation(
    titles: Map[String, String],
    holdSeconds: Double
  ) {
    def title(locale: String): String = _localized(titles, locale).getOrElse("Credits")
  }
  object Presentation {
    val default: Presentation = Presentation(Map("default" -> "Credits"), 5.0)

    implicit val decoder: Decoder[Presentation] = (c: HCursor) =>
      for {
        titles <- _localized_map(c, "titles", "title")
        holdseconds <- _optional_double(c, "holdSeconds", "hold-seconds", "minimum-hold-seconds")
      } yield Presentation(if (titles.isEmpty) default.titles else titles, holdseconds.getOrElse(default.holdSeconds))
  }

  final case class Profile(
    schema: String,
    id: String,
    selectors: Vector[Selector],
    credits: Vector[CreditItem],
    requiredAudioProviders: Vector[String],
    presentation: Presentation
  ) {
    lazy val itemById: Map[String, CreditItem] = credits.map(x => x.id -> x).toMap
  }
  object Profile {
    implicit val decoder: Decoder[Profile] = (c: HCursor) =>
      for {
        schema <- c.downField("schema").as[Option[String]]
        id <- c.downField("profile").as[String]
        selectors <- c.downField("selectors").as[Option[Vector[Selector]]]
        credits <- c.downField("credits").as[Option[Vector[CreditItem]]]
        requiredaudioproviders <- _string_vector(c, "required-audio-providers", "requiredAudioProviders")
        presentation <- c.downField("presentation").as[Option[Presentation]]
      } yield Profile(
        schema.map(_.trim).filter(_.nonEmpty).getOrElse(SCHEMA),
        _required_id(id, "credit profile id"),
        selectors.getOrElse(Vector.empty),
        credits.getOrElse(Vector.empty),
        _normalized(requiredaudioproviders.map(_.toLowerCase(java.util.Locale.ROOT))),
        presentation.getOrElse(Presentation.default)
      )
  }

  final case class ProfileSource(profile: Profile, layer: String, path: Path)

  final case class ProfileSelection(id: String, layer: String, configPath: Option[Path])

  final case class AudioEvidence(
    provider: String,
    voiceIdentity: Option[String],
    voiceId: Option[String],
    modelIdentity: Option[String],
    speaker: Option[String],
    manifestPath: Path
  ) {
    def key: String = s"audio:$provider:${voiceIdentity.orElse(voiceId).orElse(modelIdentity).getOrElse("unknown")}"
  }

  final case class Evidence(
    characterIds: Vector[String],
    assets: Vector[CozyVideoAssets.CreditEvidence],
    audio: Vector[AudioEvidence]
  ) {
    lazy val assetTags: Vector[String] = _normalized(assets.flatMap(_.tags))
    lazy val assetLicenses: Vector[String] = _normalized(assets.map(_.license).filterNot(_ == "unspecified"))
    lazy val assetProvenance: Vector[String] = _normalized(assets.map(_.provenance).filterNot(_ == "unspecified"))
  }

  final case class Diagnostic(severity: String, code: String, message: String) {
    def isError: Boolean = severity == "error"
  }

  final case class EffectiveItem(item: CreditItem, evidence: Vector[String])

  final case class EffectiveSet(
    profile: Option[ProfileSource],
    selection: Option[ProfileSelection],
    locale: String,
    evidence: Evidence,
    items: Vector[EffectiveItem],
    diagnostics: Vector[Diagnostic]
  ) {
    def profileId: Option[String] = profile.map(_.profile.id)
    def holdSeconds: Double = profile.map(_.profile.presentation.holdSeconds).getOrElse(0.0)
    def videoItems: Vector[EffectiveItem] = items.filter(_.item.appearsOn("video"))
    def publicationItems: Vector[EffectiveItem] = items.filter(_.item.appearsOn("publication"))
    def rdfItems: Vector[EffectiveItem] = items.filter(_.item.appearsOn("rdf"))
    def hasVideoPage: Boolean = videoItems.nonEmpty
    def errors: Vector[Diagnostic] = diagnostics.filter(_.isError)
    def warnings: Vector[Diagnostic] = diagnostics.filterNot(_.isError)
    def requireValid(): EffectiveSet = {
      if (errors.nonEmpty)
        RAISE.invalidArgumentFault(errors.map(x => s"${x.code}: ${x.message}").mkString("Invalid video credits: ", "; ", ""))
      this
    }
    lazy val digest: String = _sha256(_semantic_json(this).noSpaces)
  }

  final case class OutputFiles(
    directory: Path,
    jsonFile: Path,
    markdownFile: Path,
    rendererPropsFile: Path,
    digest: String
  )

  def resolve(
    projectroot: Path,
    settings: Option[Settings],
    locale: Option[String],
    scripts: Vector[CozyVideo.VideoScript],
    assets: Vector[CozyVideoAssets.CreditEvidence],
    audiomanifests: Vector[(Path, Vector[CozyVideo.VideoAudioManifestEntry])]
  ): EffectiveSet = {
    val normalizedroot = projectroot.toAbsolutePath.normalize()
    val effectivelocale = locale.map(_.trim).filter(_.nonEmpty).getOrElse("en").toLowerCase(java.util.Locale.ROOT)
    val profiles = _discover_profiles(normalizedroot)
    val selection = _select_profile(normalizedroot, settings.flatMap(_.profile))
    val profile = selection.map { selected =>
      profiles.getOrElse(selected.id, RAISE.invalidArgumentFault(s"Unknown video credit profile: ${selected.id}"))
    }
    val evidence = _evidence(scripts, assets, audiomanifests)
    profile match {
      case Some(source) => _resolve_profile(source, selection.get, effectivelocale, evidence, settings.getOrElse(Settings.empty))
      case None =>
        val diagnostics = _unprofiled_diagnostics(evidence, settings.getOrElse(Settings.empty))
        EffectiveSet(None, None, effectivelocale, evidence, Vector.empty, diagnostics)
    }
  }

  def write(outputdir: Path, effective: EffectiveSet): OutputFiles = {
    effective.requireValid()
    val directory = outputdir.toAbsolutePath.normalize()
    Files.createDirectories(directory)
    val jsonfile = directory.resolve("credits.json")
    val markdownfile = directory.resolve("credits.md")
    val rendererpropsfile = directory.resolve("renderer-props.json")
    Files.writeString(jsonfile, toJson(effective).spaces2 + "\n", StandardCharsets.UTF_8)
    Files.writeString(markdownfile, toMarkdown(effective), StandardCharsets.UTF_8)
    Files.writeString(rendererpropsfile, toRendererProps(effective).spaces2 + "\n", StandardCharsets.UTF_8)
    OutputFiles(directory, jsonfile, markdownfile, rendererpropsfile, effective.digest)
  }

  def toJson(effective: EffectiveSet): Json =
    _canonical_json(effective).deepMerge(Json.obj("digest" -> Json.fromString(effective.digest)))

  def toMarkdown(effective: EffectiveSet): String = {
    val items = effective.publicationItems
    if (items.isEmpty)
      ""
    else {
      val title = effective.profile.map(_.profile.presentation.title(effective.locale)).getOrElse("Credits")
      val lines = items.map { resolved =>
        val item = resolved.item
        val text = item.publication(effective.locale).orElse(item.label(effective.locale)).getOrElse(item.id)
        val links = Vector(item.sourceUrl, item.termsUrl).flatten.distinct
        s"- $text${if (links.isEmpty) "" else links.mkString(" (", ", ", ")")}"
      }
      (Vector(s"## $title", "") ++ lines :+ "").mkString("\n")
    }
  }

  def toRendererProps(effective: EffectiveSet): Json =
    Json.obj(
      "schema" -> Json.fromString("cozy.video.credit-renderer-props.v1"),
      "profile" -> effective.profileId.map(Json.fromString).getOrElse(Json.Null),
      "digest" -> Json.fromString(effective.digest),
      "locale" -> Json.fromString(effective.locale),
      "title" -> Json.fromString(effective.profile.map(_.profile.presentation.title(effective.locale)).getOrElse("Credits")),
      "holdSeconds" -> Json.fromDoubleOrNull(effective.holdSeconds),
      "items" -> Json.fromValues(effective.videoItems.map { resolved =>
        Json.obj(
          "id" -> Json.fromString(resolved.item.id),
          "category" -> Json.fromString(resolved.item.category),
          "label" -> Json.fromString(resolved.item.label(effective.locale).getOrElse(resolved.item.id)),
          "creator" -> resolved.item.creator.map(Json.fromString).getOrElse(Json.Null)
        )
      })
    )

  private def _resolve_profile(
    source: ProfileSource,
    selection: ProfileSelection,
    locale: String,
    evidence: Evidence,
    settings: Settings
  ): EffectiveSet = {
    val profile = source.profile
    val duplicateids = profile.credits.groupBy(_.id).collect { case (id, xs) if xs.size > 1 => id }.toVector.sorted
    val diagnostics = Vector.newBuilder[Diagnostic]
    duplicateids.foreach(id => diagnostics += Diagnostic("error", "credit.item.duplicate", s"Profile ${profile.id} defines duplicate credit item $id."))
    if (profile.presentation.holdSeconds < 0.0)
      diagnostics += Diagnostic("error", "credit.presentation.invalid-hold", s"Profile ${profile.id} defines a negative credit hold duration.")
    profile.credits.foreach { item =>
      if (!_obligations.contains(item.obligation))
        diagnostics += Diagnostic("error", "credit.item.invalid-obligation", s"Credit item ${item.id} uses unsupported obligation ${item.obligation}.")
      val invalidsurfaces = item.surfaces.filterNot(_surfaces.contains)
      if (invalidsurfaces.nonEmpty)
        diagnostics += Diagnostic("error", "credit.item.invalid-surface", s"Credit item ${item.id} uses unsupported surfaces ${invalidsurfaces.mkString(", ")}.")
    }
    profile.selectors.zipWithIndex.foreach { case (selector, index) =>
      if (selector.conditions.isEmpty)
        diagnostics += Diagnostic("error", "credit.selector.empty", s"Profile ${profile.id} selector ${index + 1} has no usage condition.")
      (selector.include ++ selector.exclude).distinct.filterNot(profile.itemById.contains).foreach { id =>
        diagnostics += Diagnostic("error", "credit.selector.unknown-item", s"Profile ${profile.id} selector ${index + 1} references unknown item $id.")
      }
    }
    val selected = scala.collection.mutable.LinkedHashMap.empty[String, Vector[String]]
    val selectorexcluded = scala.collection.mutable.Set.empty[String]
    profile.selectors.foreach { selector =>
      _selector_evidence(selector.conditions, locale, evidence).foreach { reasons =>
        selector.include.foreach(id => selected.update(id, _normalized(selected.getOrElse(id, Vector.empty) ++ reasons)))
        selector.exclude.foreach(selectorexcluded.add)
      }
    }
    evidence.assets.foreach { asset =>
      asset.credits.foreach { id =>
        if (profile.itemById.contains(id))
          selected.update(id, _normalized(selected.getOrElse(id, Vector.empty) :+ s"asset:${asset.id}"))
        else {
          val severity = if (asset.creditObligation.contains("recommended")) "warning" else "error"
          diagnostics += Diagnostic(severity, "credit.asset.unknown-item", s"Asset ${asset.id} declares unknown credit item $id.")
        }
      }
    }
    settings.include.foreach { id =>
      if (profile.itemById.contains(id))
        selected.update(id, _normalized(selected.getOrElse(id, Vector.empty) :+ "project:include"))
      else
        diagnostics += Diagnostic("error", "credit.override.unknown-item", s"Project includes unknown credit item $id.")
    }
    val excluded = selectorexcluded.toSet ++ settings.exclude
    evidence.assets.foreach { asset =>
      asset.credits.filter(excluded).foreach { id =>
        val severity = if (asset.creditObligation.contains("recommended")) "warning" else "error"
        diagnostics += Diagnostic(severity, "credit.asset.excluded", s"Asset ${asset.id} requires excluded credit item $id.")
      }
    }
    excluded.foreach(selected.remove)
    val items = selected.toVector.flatMap { case (id, reasons) => profile.itemById.get(id).map(EffectiveItem(_, reasons)) }.
      sortBy(x => (x.item.order, x.item.group.getOrElse(""), x.item.id))
    profile.requiredAudioProviders.foreach { provider =>
      evidence.audio.filter(_.provider == provider).foreach { audio =>
        val covered = items.exists { resolved =>
          resolved.item.category == "voice" && resolved.item.isRequired && resolved.evidence.contains(audio.key)
        }
        if (!covered)
          diagnostics += Diagnostic("error", "credit.audio.unresolved", s"No required voice credit matches ${audio.key}.")
      }
    }
    items.foreach { resolved =>
      val item = resolved.item
      val severity = if (item.isRequired) "error" else "warning"
      val obligation = if (item.isRequired) "Required" else "Recommended"
      if (item.appearsOn("publication") && item.publication(locale).isEmpty)
        diagnostics += Diagnostic(severity, "credit.locale.publication-missing", s"$obligation credit ${item.id} has no publication text for locale $locale.")
      if (item.appearsOn("video") && item.label(locale).isEmpty)
        diagnostics += Diagnostic(severity, "credit.locale.label-missing", s"$obligation credit ${item.id} has no video label for locale $locale.")
    }
    EffectiveSet(Some(source), Some(selection), locale, evidence, items, diagnostics.result().distinct)
  }

  private def _unprofiled_diagnostics(evidence: Evidence, settings: Settings): Vector[Diagnostic] = {
    val assetdiagnostics = evidence.assets.flatMap { asset =>
      asset.credits.map { id =>
        val severity = if (asset.creditObligation.contains("recommended")) "warning" else "error"
        Diagnostic(severity, "credit.profile.missing", s"Asset ${asset.id} declares credit item $id but no credit profile is selected.")
      }
    }
    val overridediagnostics = (settings.include ++ settings.exclude).distinct.map { id =>
      Diagnostic("error", "credit.override.no-profile", s"Credit override $id requires a selected credit profile.")
    }
    (assetdiagnostics ++ overridediagnostics).distinct
  }

  private def _selector_evidence(condition: SelectorCondition, locale: String, evidence: Evidence): Option[Vector[String]] = {
    val characterreasons =
      if (condition.characterIds.isEmpty) Some(Vector.empty)
      else {
        val matched = evidence.characterIds.filter(condition.characterIds.contains)
        if (matched.nonEmpty) Some(matched.map(x => s"character:$x")) else None
      }
    val assettagreasons = _set_evidence(condition.assetTags, evidence.assetTags, "asset-tag")
    val assetlicensereasons = _set_evidence(condition.assetLicenses, evidence.assetLicenses, "asset-license")
    val assetprovenancereasons = _set_evidence(condition.assetProvenance, evidence.assetProvenance, "asset-provenance")
    val localereasons =
      if (condition.locales.isEmpty) Some(Vector.empty)
      else if (condition.locales.exists(x => _locale_matches(locale, x))) Some(Vector(s"locale:$locale"))
      else None
    val hasaudio = condition.audioProviders.nonEmpty || condition.voiceIdentities.nonEmpty || condition.voiceIds.nonEmpty || condition.modelIdentities.nonEmpty
    val audioreasons =
      if (!hasaudio)
        Some(Vector.empty)
      else {
        val matched = evidence.audio.filter { audio =>
          _optional_match(condition.audioProviders, audio.provider) &&
            _optional_option_match(condition.voiceIdentities, audio.voiceIdentity) &&
            _optional_option_match(condition.voiceIds, audio.voiceId) &&
            _optional_option_match(condition.modelIdentities, audio.modelIdentity)
        }
        if (matched.nonEmpty) Some(matched.map(_.key).distinct.sorted) else None
      }
    for {
      characters <- characterreasons
      tags <- assettagreasons
      licenses <- assetlicensereasons
      provenance <- assetprovenancereasons
      locales <- localereasons
      audio <- audioreasons
    } yield _normalized(characters ++ tags ++ licenses ++ provenance ++ locales ++ audio)
  }

  private def _set_evidence(required: Vector[String], actual: Vector[String], prefix: String): Option[Vector[String]] =
    if (required.isEmpty)
      Some(Vector.empty)
    else {
      val matched = actual.filter(required.contains)
      if (matched.nonEmpty) Some(matched.map(x => s"$prefix:$x")) else None
    }

  private def _optional_match(required: Vector[String], actual: String): Boolean =
    required.isEmpty || required.contains(actual)

  private def _optional_option_match(required: Vector[String], actual: Option[String]): Boolean =
    required.isEmpty || actual.exists(required.contains)

  private def _evidence(
    scripts: Vector[CozyVideo.VideoScript],
    assets: Vector[CozyVideoAssets.CreditEvidence],
    audiomanifests: Vector[(Path, Vector[CozyVideo.VideoAudioManifestEntry])]
  ): Evidence = {
    val characters = _normalized(
      scripts.flatMap(_.expandedScenes.flatMap(_.speaker)) ++ audiomanifests.flatMap(_._2.flatMap(_.speaker))
    )
    val configuredassets = assets.filter(_.status == "configured")
    val audio = audiomanifests.flatMap { case (path, entries) =>
      entries.flatMap { entry =>
        entry.provider.map { provider =>
          AudioEvidence(
            provider.trim.toLowerCase(java.util.Locale.ROOT),
            _normalized_option(entry.voiceIdentity),
            _normalized_option(entry.voiceId),
            _normalized_option(entry.modelIdentity),
            _normalized_option(entry.speaker),
            path
          )
        }
      }
    }.distinct.sortBy(x => (x.provider, x.voiceIdentity.getOrElse(""), x.voiceId.getOrElse(""), x.modelIdentity.getOrElse(""), x.speaker.getOrElse(""), x.manifestPath.toString))
    Evidence(characters, configuredassets, audio)
  }

  private def _select_profile(projectroot: Path, explicit: Option[String]): Option[ProfileSelection] =
    _normalized_option(explicit).map(x => ProfileSelection(x, "video-project", None)).orElse {
      _configuration_layers(projectroot).foldLeft(Option.empty[ProfileSelection]) {
        case (selected, (layer, root)) =>
          _config_files(root).foldLeft(selected) { (current, file) =>
            val value = CozyProjectYamlConfig.load(file).value("video.credits.default-profile")
            _normalized_option(value).map(x => ProfileSelection(x, layer, Some(file))).orElse(current)
          }
      }
    }

  private def _discover_profiles(projectroot: Path): Map[String, ProfileSource] =
    _configuration_layers(projectroot).foldLeft(Map.empty[String, ProfileSource]) {
      case (profiles, (layer, root)) =>
        val sources = _profile_files(root.resolve("video/credit-profiles")).map { file =>
          val profile = StructuredDocumentLoader.loadDocument[Profile](InputSource(file.toFile)).take
          if (profile.schema != SCHEMA)
            RAISE.invalidArgumentFault(s"Unsupported video credit profile schema ${profile.schema}: $file")
          ProfileSource(profile, layer, file)
        }
        val duplicateids = sources.groupBy(_.profile.id).collect { case (id, xs) if xs.size > 1 => id }.toVector.sorted
        if (duplicateids.nonEmpty)
          RAISE.invalidArgumentFault(s"Duplicate video credit profile ids in $layer: ${duplicateids.mkString(", ")}")
        sources.foldLeft(profiles) { (current, source) =>
          current.updated(source.profile.id, source)
        }
    }

  private def _configuration_layers(projectroot: Path): Vector[(String, Path)] =
    Vector(
      Option(System.getProperty("user.home")).map(x => "user" -> Path.of(x).resolve(".cozy").toAbsolutePath.normalize),
      Some("project-conf" -> projectroot.resolve("conf/cozy").toAbsolutePath.normalize),
      Some("project-local" -> projectroot.resolve(".cozy").toAbsolutePath.normalize)
    ).flatten

  private val _config_names = Vector("config.yaml", "config.yml", "config.json", "config.conf", "config.hocon", "config.xml")
  private val _obligations = Set("required", "recommended")
  private val _surfaces = Set("publication", "rdf", "video")

  private def _config_files(root: Path): Vector[Path] =
    _config_names.map(root.resolve).filter(Files.isRegularFile(_))

  private def _profile_files(directory: Path): Vector[Path] =
    if (!Files.isDirectory(directory))
      Vector.empty
    else {
      val stream = Files.list(directory)
      try stream.iterator.asScala.filter(Files.isRegularFile(_)).filter(_supported_profile_file).toVector.sortBy(_.getFileName.toString)
      finally stream.close()
    }

  private def _supported_profile_file(path: Path): Boolean = {
    val name = path.getFileName.toString.toLowerCase(java.util.Locale.ROOT)
    Vector(".yaml", ".yml", ".json", ".conf", ".hocon", ".xml").exists(name.endsWith)
  }

  private def _canonical_json(effective: EffectiveSet): Json =
    Json.obj(
      "schema" -> Json.fromString(SCHEMA),
      "profile" -> effective.profile.map { source =>
        Json.obj(
          "id" -> Json.fromString(source.profile.id),
          "sourceLayer" -> Json.fromString(source.layer),
          "sourcePath" -> Json.fromString(source.path.toString),
          "selectionLayer" -> Json.fromString(effective.selection.map(_.layer).getOrElse("none")),
          "selectionPath" -> effective.selection.flatMap(_.configPath).map(x => Json.fromString(x.toString)).getOrElse(Json.Null),
          "presentation" -> _presentation_json(effective)
        )
      }.getOrElse(Json.Null),
      "locale" -> Json.fromString(effective.locale),
      "evidence" -> Json.obj(
        "characterIds" -> Json.fromValues(effective.evidence.characterIds.map(Json.fromString)),
        "assetTags" -> Json.fromValues(effective.evidence.assetTags.map(Json.fromString)),
        "assetLicenses" -> Json.fromValues(effective.evidence.assetLicenses.map(Json.fromString)),
        "assetProvenance" -> Json.fromValues(effective.evidence.assetProvenance.map(Json.fromString)),
        "audio" -> Json.fromValues(effective.evidence.audio.map { audio =>
          Json.obj(
            "provider" -> Json.fromString(audio.provider),
            "voiceIdentity" -> audio.voiceIdentity.map(Json.fromString).getOrElse(Json.Null),
            "voiceId" -> audio.voiceId.map(Json.fromString).getOrElse(Json.Null),
            "modelIdentity" -> audio.modelIdentity.map(Json.fromString).getOrElse(Json.Null),
            "speaker" -> audio.speaker.map(Json.fromString).getOrElse(Json.Null),
            "manifestPath" -> Json.fromString(audio.manifestPath.toString)
          )
        })
      ),
      "credits" -> _effective_credits_json(effective),
      "diagnostics" -> _diagnostics_json(effective)
    )

  // The digest identifies effective credit semantics, not the machine-specific workspace.
  private def _semantic_json(effective: EffectiveSet): Json =
    Json.obj(
      "schema" -> Json.fromString(SCHEMA),
      "profile" -> effective.profile.map { source =>
        Json.obj(
          "id" -> Json.fromString(source.profile.id),
          "presentation" -> _presentation_json(effective)
        )
      }.getOrElse(Json.Null),
      "locale" -> Json.fromString(effective.locale),
      "evidence" -> Json.obj(
        "characterIds" -> Json.fromValues(effective.evidence.characterIds.map(Json.fromString)),
        "assetTags" -> Json.fromValues(effective.evidence.assetTags.map(Json.fromString)),
        "assetLicenses" -> Json.fromValues(effective.evidence.assetLicenses.map(Json.fromString)),
        "assetProvenance" -> Json.fromValues(effective.evidence.assetProvenance.map(Json.fromString)),
        "audio" -> Json.fromValues(effective.evidence.audio.map { audio =>
          Json.obj(
            "provider" -> Json.fromString(audio.provider),
            "voiceIdentity" -> audio.voiceIdentity.map(Json.fromString).getOrElse(Json.Null),
            "voiceId" -> audio.voiceId.map(Json.fromString).getOrElse(Json.Null),
            "modelIdentity" -> audio.modelIdentity.map(Json.fromString).getOrElse(Json.Null),
            "speaker" -> audio.speaker.map(Json.fromString).getOrElse(Json.Null)
          )
        })
      ),
      "credits" -> _effective_credits_json(effective),
      "diagnostics" -> _diagnostics_json(effective)
    )

  private def _presentation_json(effective: EffectiveSet): Json =
    Json.obj(
      "title" -> Json.fromString(effective.profile.map(_.profile.presentation.title(effective.locale)).getOrElse("Credits")),
      "holdSeconds" -> Json.fromDoubleOrNull(effective.holdSeconds)
    )

  private def _effective_credits_json(effective: EffectiveSet): Json =
    Json.fromValues(effective.items.map { resolved =>
      val item = resolved.item
      Json.obj(
        "id" -> Json.fromString(item.id),
        "category" -> Json.fromString(item.category),
        "label" -> item.label(effective.locale).map(Json.fromString).getOrElse(Json.Null),
        "publicationText" -> item.publication(effective.locale).map(Json.fromString).getOrElse(Json.Null),
        "creator" -> item.creator.map(Json.fromString).getOrElse(Json.Null),
        "sourceUrl" -> item.sourceUrl.map(Json.fromString).getOrElse(Json.Null),
        "termsUrl" -> item.termsUrl.map(Json.fromString).getOrElse(Json.Null),
        "obligation" -> Json.fromString(item.obligation),
        "surfaces" -> Json.fromValues(item.surfaces.map(Json.fromString)),
        "evidence" -> Json.fromValues(resolved.evidence.map(Json.fromString))
      )
    })

  private def _diagnostics_json(effective: EffectiveSet): Json =
    Json.fromValues(effective.diagnostics.map { diagnostic =>
      Json.obj(
        "severity" -> Json.fromString(diagnostic.severity),
        "code" -> Json.fromString(diagnostic.code),
        "message" -> Json.fromString(diagnostic.message)
      )
    })

  private def _sha256(value: String): String = {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
    digest.map("%02x".format(_)).mkString
  }

  private def _localized(values: Map[String, String], locale: String): Option[String] = {
    val normalized = locale.toLowerCase(java.util.Locale.ROOT)
    val language = normalized.takeWhile(x => x != '-' && x != '_')
    Vector(normalized, language, "default").distinct.flatMap(values.get).map(_.trim).find(_.nonEmpty)
  }

  private def _locale_matches(actual: String, expected: String): Boolean = {
    val a = actual.toLowerCase(java.util.Locale.ROOT)
    val e = expected.toLowerCase(java.util.Locale.ROOT)
    a == e || a.startsWith(e + "-") || a.startsWith(e + "_")
  }

  private def _required_id(value: String, label: String): String = {
    val normalized = value.trim
    if (normalized.isEmpty || normalized.exists(_.isWhitespace))
      RAISE.invalidArgumentFault(s"Invalid $label: $value")
    normalized
  }

  private def _normalized(values: Vector[String]): Vector[String] =
    values.map(_.trim).filter(_.nonEmpty).distinct.sorted

  private def _normalized_option(value: Option[String]): Option[String] =
    value.map(_.trim).filter(_.nonEmpty)

  private def _optional_string(c: HCursor, names: String*): Decoder.Result[Option[String]] =
    names.foldLeft[Decoder.Result[Option[String]]](Right(None)) {
      case (result @ Right(Some(_)), _) => result
      case (Right(None), name) => c.downField(name).as[Option[String]]
      case (left, _) => left
    }

  private def _optional_double(c: HCursor, names: String*): Decoder.Result[Option[Double]] =
    names.foldLeft[Decoder.Result[Option[Double]]](Right(None)) {
      case (result @ Right(Some(_)), _) => result
      case (Right(None), name) => c.downField(name).as[Option[Double]]
      case (left, _) => left
    }

  private def _string_vector(c: HCursor, names: String*): Decoder.Result[Vector[String]] = {
    val focus = names.toVector.flatMap(name => c.downField(name).focus).headOption
    focus match {
      case None => Right(Vector.empty)
      case Some(json) =>
        json.asString.map(x => Right(Vector(x))).getOrElse(json.as[Vector[String]])
    }
  }

  private def _localized_map(c: HCursor, names: String*): Decoder.Result[Map[String, String]] = {
    val focus = names.toVector.flatMap(name => c.downField(name).focus).headOption
    focus match {
      case None => Right(Map.empty)
      case Some(json) =>
        json.asString.map(x => Right(Map("default" -> x))).getOrElse(json.as[Map[String, String]])
    }
  }
}
