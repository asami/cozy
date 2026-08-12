package cozy.video

import cozy.CozySpecVocabulary
import cozy.config.CozyProjectContext
import io.circe.Json
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 20, 2026
 * @version Aug. 12, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyVideoCreditsSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  "Cozy Video credit resolution" should {
    "derive additive material and voice credits" which {
      Vector(false, true).foreach { hasreimumarisa =>
        Vector(false, true).foreach { haszundamon =>
          Vector("ja", "en").foreach { locale =>
            s"resolve the Reimu/Marisa=$hasreimumarisa Zundamon=$haszundamon locale=$locale baseline" in {
              _with_temp_dir(s"matrix-$hasreimumarisa-$haszundamon-$locale") { dir =>
                Given("a generic profile and actual script, asset, and audio-manifest evidence")
                _write_profile(dir, "baseline")
                _write_default(dir, "baseline")
                val speakers = _speakers(hasreimumarisa, haszundamon)
                val scripts = Vector(_script(speakers))
                val assets = if (haszundamon) Vector(_zundamon_asset) else Vector.empty
                val audio = Vector(dir.resolve("audio/manifest.json") -> _audio_entries(locale, speakers))

                When("Cozy resolves one effective structured credit set")
                val effective = CozyVideoCredits.resolve(dir, None, Some(locale), scripts, assets, audio).requireValid()

                Then("material presence and actual audio provider determine an additive deterministic set")
                effective.items.map(_.item.id) shouldBe _expected_ids(hasreimumarisa, haszundamon, locale)
                effective.items.map(_.item.id).distinct shouldBe effective.items.map(_.item.id)
                effective.profileId shouldBe Some("baseline")
                effective.selection.map(_.layer) shouldBe Some("project-conf")

                And("locale controls projection wording but does not select the audio provider")
                effective.locale shouldBe locale
                if (locale == "en") {
                  effective.items.map(_.item.id) should contain("macos-voice")
                  effective.items.map(_.item.id) should not(contain("voicevox-tool"))
                } else {
                  effective.items.map(_.item.id) should contain("voicevox-tool")
                  effective.items.map(_.item.id) should not(contain("macos-voice"))
                }
              }
            }
          }
        }
      }

      "distinguish image-only and voice-only Zundamon usage" in {
        _with_temp_dir("independent-zundamon") { dir =>
          Given("a profile whose image and voice selectors are independent")
          _write_profile(dir, "baseline")

          When("only a tagged image is present")
          val imageonly = CozyVideoCredits.resolve(
            dir,
            Some(CozyVideoCredits.Settings(Some("baseline"), Vector.empty, Vector.empty)),
            Some("ja"),
            Vector.empty,
            Vector(_zundamon_asset),
            Vector.empty
          ).requireValid()

          Then("only the character-material credit is selected")
          imageonly.items.map(_.item.id) shouldBe Vector("zundamon-image")

          When("only an authoritative Zundamon VOICEVOX manifest is present")
          val voiceonly = CozyVideoCredits.resolve(
            dir,
            Some(CozyVideoCredits.Settings(Some("baseline"), Vector.empty, Vector.empty)),
            Some("ja"),
            Vector.empty,
            Vector.empty,
            Vector(dir.resolve("audio/manifest.json") -> _audio_entries("ja", Vector("zundamon")))
          ).requireValid()

          Then("only the shared tool and actual speaker credits are selected")
          voiceonly.items.map(_.item.id) shouldBe Vector("voicevox-tool", "voice-zundamon")
        }
      }

      "trust audio manifests over stale script voice metadata" in {
        _with_temp_dir("authoritative-audio") { dir =>
          Given("a script that contains stale VOICEVOX authoring but audio produced by macOS say")
          _write_profile(dir, "baseline")
          val stalecharacters = Map("narrator" -> Json.obj(
            "voice" -> Json.obj(
              "engine" -> Json.fromString("voicevox"),
              "speakerName" -> Json.fromString("四国めたん")
            )
          ))
          val script = _script(Vector("narrator")).copy(characters = stalecharacters)
          val audio = Vector(dir.resolve("audio/manifest.json") -> _audio_entries("en", Vector("narrator")))

          When("credit resolution reads final synthesis provenance")
          val effective = CozyVideoCredits.resolve(
            dir,
            Some(CozyVideoCredits.Settings(Some("baseline"), Vector.empty, Vector.empty)),
            Some("en"),
            Vector(script),
            Vector.empty,
            audio
          ).requireValid()

          Then("the stale authored engine does not create a false VOICEVOX credit")
          effective.items.map(_.item.id) shouldBe Vector("macos-voice")
          effective.evidence.audio.map(_.provider).distinct shouldBe Vector("macos-say")
        }
      }
    }

    "apply profile configuration and validation contracts" which {
      "resolve a nested package through its marked project context" in {
        _with_temp_dir("nested-project-context") { dir =>
          Given("a nested video package beneath a project marker with a project credit default")
          val pkg = dir.resolve("src/main/media/development-process/object-modeling")
          Files.createDirectories(pkg)
          _write_config(dir.resolve("conf/cozy/config.yaml"), "root-profile")
          _write_profile_at(dir.resolve("conf/cozy"), "root-profile")

          When("the package-compatible Path overload resolves its project context once")
          val effective = _empty_resolution(pkg, None)

          Then("the project layer supplies both the selected profile and source provenance")
          effective.profileId shouldBe Some("root-profile")
          effective.selection.map(_.layer) shouldBe Some("project-conf")
          effective.profile.map(_.layer) shouldBe Some("project-conf")
        }
      }

      "retain package layers when no project marker is present" in {
        _with_temp_dir("package-without-marker") { dir =>
          Given("an unmarked package with package configuration and a package-local override")
          _write_profile_at(dir.resolve("conf/cozy"), "package-conf-profile")
          _write(dir.resolve("conf/cozy/config.json"), """{"video":{"credits":{"default-profile":"package-conf-profile"}}}""")
          _write_profile_at(dir.resolve(".cozy"), "package-local-profile")
          _write_config(dir.resolve(".cozy/config.yaml"), "package-local-profile")

          When("Cozy resolves the compatibility Path overload")
          val effective = _empty_resolution(dir, None)

          Then("the unmarked package keeps package-local precedence and provenance")
          effective.profileId shouldBe Some("package-local-profile")
          effective.selection.map(_.layer) shouldBe Some("package-local")
          effective.profile.map(_.layer) shouldBe Some("package-local")
        }
      }

      "apply all context layers once in their declared precedence order" in {
        _with_temp_dir("all-context-layers") { dir =>
          val home = dir.resolve("home")
          val pkg = dir.resolve("packages/part5")
          _with_user_home(home) {
            Given("user project and nested package layers each define the same credit profile ID")
            Files.createDirectories(pkg)
            _write_profile_at(home.resolve(".cozy"), "shared")
            _write_config(home.resolve(".cozy/config.yaml"), "shared")
            _write_profile_at(dir.resolve("conf/cozy"), "shared")
            _write_config(dir.resolve("conf/cozy/config.yaml"), "shared")
            _write_profile_at(dir.resolve(".cozy"), "shared")
            _write_config(dir.resolve(".cozy/config.yaml"), "shared")
            _write_profile_at(pkg.resolve("conf/cozy"), "shared")
            _write(pkg.resolve("conf/cozy/config.json"), """{"video":{"credits":{"default-profile":"shared"}}}""")
            _write_profile_at(pkg.resolve(".cozy"), "shared")
            _write_config(pkg.resolve(".cozy/config.yaml"), "shared")

            When("Cozy resolves the deep package without an explicit descriptor profile")
            val effective = _empty_resolution(pkg, None)

            Then("the later package-local layer wins over every earlier source exactly once")
            effective.profileId shouldBe Some("shared")
            effective.selection.map(_.layer) shouldBe Some("package-local")
            effective.profile.map(_.layer) shouldBe Some("package-local")
          }
        }
      }

      "avoid replaying the package roots when they are already the discovered project root" in {
        _with_temp_dir("same-root-no-replay") { dir =>
          Given("a descriptor package that is itself the marked project root")
          _write_config(dir.resolve("conf/cozy/config.yaml"), "root-profile")
          _write_profile_at(dir.resolve("conf/cozy"), "root-profile")

          When("a missing profile is diagnosed from its context layers")
          val error = intercept[RuntimeException] {
            _empty_resolution(dir, Some("missing"))
          }

          Then("only project layers are considered after the shared built-in and user layers")
          error.getMessage should include_text("project-conf(provenance=project-conf, root=")
          error.getMessage should include_text("project-local(provenance=project-local, root=absent")
          error.getMessage should not(include_text("package-conf(provenance="))
          error.getMessage should not(include_text("package-local(provenance="))
        }
      }

      "report every context layer for an unknown requested profile" in {
        _with_temp_dir("unknown-profile-context") { dir =>
          Given("a marked project with one known profile and absent optional layer roots")
          _write_config(dir.resolve("conf/cozy/config.yaml"), "known")
          _write_profile_at(dir.resolve("conf/cozy"), "known")

          When("the descriptor requests a profile that no context layer provides")
          val error = intercept[RuntimeException] {
            _empty_resolution(dir, Some("missing"))
          }

          Then("the diagnostic records the requested ID, all ordered layers, and considered sources")
          error.getMessage should include_text("Unknown video credit profile: missing")
          error.getMessage should include_text("built-in(provenance=built-in, root=absent")
          error.getMessage should include_text("user(provenance=user, root=")
          error.getMessage should include_text("project-conf(provenance=project-conf, root=")
          error.getMessage should include_text("project-local(provenance=project-local, root=absent")
          error.getMessage should include_text("known.yaml")
          error.getMessage should include_text("ids=[known]")
        }
      }

      "reject symbolic profile directories and profile files" in {
        _with_temp_dir("profile-symlinks") { dir =>
          Given("a direct project marker and a symbolic profile directory")
          _write_config(dir.resolve("conf/cozy/config.yaml"), "profile")
          val realdir = dir.resolve("real-profiles")
          _write_profile_at(realdir, "profile")
          val directorylink = dir.resolve(".cozy/video/credit-profiles")
          Option(directorylink.getParent).foreach(Files.createDirectories(_))
          Files.createSymbolicLink(directorylink, realdir)

          When("credit discovery enters the symbolic directory")
          val directoryerror = intercept[RuntimeException] {
            _empty_resolution(dir, Some("profile"))
          }

          Then("the directory is rejected before a profile is parsed")
          directoryerror.getMessage should include_text("direct non-symlink directory")

          And("a direct profile directory contains a symbolic profile file")
          Files.delete(directorylink)
          val profilelink = dir.resolve(".cozy/video/credit-profiles/profile.yaml")
          Option(profilelink.getParent).foreach(Files.createDirectories(_))
          Files.createSymbolicLink(profilelink, realdir.resolve("video/credit-profiles/profile.yaml"))

          When("credit discovery reads the symbolic profile file")
          val fileerror = intercept[RuntimeException] {
            _empty_resolution(dir, Some("profile"))
          }

          Then("the file is rejected before a profile is parsed")
          fileerror.getMessage should include_text("direct regular non-symlink file")
        }
      }

      "reject duplicate profile IDs in an unmarked package configuration layer" in {
        _with_temp_dir("package-conf-duplicate") { dir =>
          Given("an unmarked package-conf layer with two files declaring one profile ID")
          _write_profile_at(dir.resolve("conf/cozy"), "duplicate")
          _write_profile_at(dir.resolve("conf/cozy"), "duplicate-copy", _empty_profile_yaml("duplicate"))

          When("an explicit descriptor profile triggers package discovery")
          val error = intercept[RuntimeException] {
            _empty_resolution(dir, Some("duplicate"))
          }

          Then("the duplicate diagnostic identifies the package-conf layer")
          error.getMessage should include_text("Duplicate video credit profile ids in package-conf: duplicate")
        }
      }

      "honor explicit project local project conf and user default precedence" in {
        _with_temp_dir("precedence") { dir =>
          val userhome = dir.resolve("home")
          _with_user_home(userhome) {
            Given("the same environment has user project-conf project-local and explicit profile choices")
            _write_profile_at(userhome.resolve(".cozy"), "user-profile")
            _write_config(userhome.resolve(".cozy/config.yaml"), "user-profile")
            _write_profile_at(dir.resolve("conf/cozy"), "conf-profile")
            _write_config(dir.resolve("conf/cozy/config.yaml"), "conf-profile")
            _write_profile_at(dir.resolve(".cozy"), "local-profile")
            _write_config(dir.resolve(".cozy/config.yaml"), "local-profile")
            _write_profile_at(dir.resolve(".cozy"), "explicit-profile")

            When("Cozy resolves defaults and then an explicit video descriptor override")
            val local = _empty_resolution(dir, None)
            val explicit = _empty_resolution(dir, Some("explicit-profile"))

            Then("the latest project layer wins until the video descriptor explicitly selects a profile")
            local.profileId shouldBe Some("local-profile")
            local.selection.map(_.layer) shouldBe Some("project-local")
            explicit.profileId shouldBe Some("explicit-profile")
            explicit.selection.map(_.layer) shouldBe Some("video-project")

            And("removing later layers exposes project-conf and then user defaults")
            Files.delete(dir.resolve(".cozy/config.yaml"))
            _empty_resolution(dir, None).profileId shouldBe Some("conf-profile")
            Files.delete(dir.resolve("conf/cozy/config.yaml"))
            _empty_resolution(dir, None).profileId shouldBe Some("user-profile")
          }
        }
      }

      "apply explicit include and exclude overrides after selectors" in {
        _with_temp_dir("overrides") { dir =>
          Given("a selected profile and explicit item overrides")
          _write_profile(dir, "baseline")
          val settings = CozyVideoCredits.Settings(
            Some("baseline"),
            Vector("touhou-original"),
            Vector("kitsune-material")
          )

          When("a Reimu script would normally select both material credits")
          val effective = CozyVideoCredits.resolve(
            dir,
            Some(settings),
            Some("en"),
            Vector(_script(Vector("reimu"))),
            Vector.empty,
            Vector(dir.resolve("audio/manifest.json") -> _audio_entries("en", Vector("reimu")))
          ).requireValid()

          Then("the include is retained and the explicit exclusion removes its item")
          effective.items.map(_.item.id) shouldBe Vector("touhou-original", "macos-voice")
          effective.items.find(_.item.id == "touhou-original").get.evidence should contain("project:include")
        }
      }

      "fail unresolved required declarations and warn for recommended declarations" in {
        _with_temp_dir("obligations") { dir =>
          Given("assets that reference credit items absent from the selected catalog")
          _write_profile(dir, "baseline")
          val required = _zundamon_asset.copy(credits = Vector("missing-required"), creditObligation = Some("required"))
          val recommended = _zundamon_asset.copy(credits = Vector("missing-recommended"), creditObligation = Some("recommended"))

          When("Cozy verifies each effective set")
          val requiredset = CozyVideoCredits.resolve(
            dir,
            Some(CozyVideoCredits.Settings(Some("baseline"), Vector.empty, Vector.empty)),
            Some("ja"),
            Vector.empty,
            Vector(required),
            Vector.empty
          )
          val recommendedset = CozyVideoCredits.resolve(
            dir,
            Some(CozyVideoCredits.Settings(Some("baseline"), Vector.empty, Vector.empty)),
            Some("ja"),
            Vector.empty,
            Vector(recommended),
            Vector.empty
          )

          Then("required omissions block verification while recommended omissions remain inspectable warnings")
          val error = intercept[RuntimeException](requiredset.requireValid())
          error.getMessage should include_text("credit.asset.unknown-item")
          recommendedset.requireValid().warnings.map(_.code) should contain("credit.asset.unknown-item")
        }
      }

      "enforce declared asset obligations even when no profile is selected" in {
        _with_temp_dir("obligations-without-profile") { dir =>
          Given("required and recommended asset credits without a selected profile")
          val required = _zundamon_asset.copy(credits = Vector("required-item"), creditObligation = Some("required"))
          val recommended = _zundamon_asset.copy(credits = Vector("recommended-item"), creditObligation = Some("recommended"))

          When("Cozy resolves each project without a default or explicit profile")
          val requiredset = CozyVideoCredits.resolve(dir, None, Some("ja"), Vector.empty, Vector(required), Vector.empty)
          val recommendedset = CozyVideoCredits.resolve(dir, None, Some("ja"), Vector.empty, Vector(recommended), Vector.empty)

          Then("required attribution blocks the build while recommended attribution remains a warning")
          intercept[RuntimeException](requiredset.requireValid()).getMessage should include_text("credit.profile.missing")
          recommendedset.requireValid().warnings.map(_.code) should contain("credit.profile.missing")
        }
      }

      "warn when a selected recommended item lacks localized presentation" in {
        _with_temp_dir("recommended-localization") { dir =>
          Given("a selected recommended item with video and publication surfaces but no localized text")
          _write_profile_at(
            dir.resolve("conf/cozy"),
            "recommended-localization",
            """schema: cozy.video.credits.v1
              |profile: recommended-localization
              |credits:
              |  - id: advisory
              |    obligation: recommended
              |    surfaces: [video, publication]
              |""".stripMargin
          )

          When("Cozy resolves the item through an explicit include")
          val effective = CozyVideoCredits.resolve(
            dir,
            Some(CozyVideoCredits.Settings(Some("recommended-localization"), Vector("advisory"), Vector.empty)),
            Some("ja"),
            Vector.empty,
            Vector.empty,
            Vector.empty
          ).requireValid()

          Then("each incomplete projection is visible as a non-blocking warning")
          effective.warnings.map(_.code) should contain allOf (
            "credit.locale.publication-missing",
            "credit.locale.label-missing"
          )
        }
      }

      "fail a required audio provider without a matching speaker credit" in {
        _with_temp_dir("unmatched-audio") { dir =>
          Given("a VOICEVOX manifest whose speaker is absent from the profile selectors")
          _write_profile(dir, "baseline")
          val unknownvoice = _audio_entry("voicevox", "Unknown Voice", "narrator")

          When("Cozy verifies required provider coverage")
          val effective = CozyVideoCredits.resolve(
            dir,
            Some(CozyVideoCredits.Settings(Some("baseline"), Vector.empty, Vector.empty)),
            Some("ja"),
            Vector(_script(Vector("narrator"))),
            Vector.empty,
            Vector(dir.resolve("audio/manifest.json") -> Vector(unknownvoice))
          )

          Then("the unmatched authoritative speaker is a build-blocking diagnostic")
          val error = intercept[RuntimeException](effective.requireValid())
          error.getMessage should include_text("credit.audio.unresolved")
          error.getMessage should include_text("Unknown Voice")
        }
      }

      "reject invalid profile vocabulary and duplicate IDs within one layer" in {
        _with_temp_dir("invalid-profile") { dir =>
          Given("a profile with invalid presentation and credit vocabulary")
          _write_profile_at(
            dir.resolve("conf/cozy"),
            "invalid",
            """schema: cozy.video.credits.v1
              |profile: invalid
              |presentation:
              |  hold-seconds: -1
              |credits:
              |  - id: invalid-item
              |    obligation: optional
              |    surfaces: [broadcast]
              |""".stripMargin
          )

          When("Cozy resolves the selected invalid profile")
          val invalid = CozyVideoCredits.resolve(
            dir,
            Some(CozyVideoCredits.Settings(Some("invalid"), Vector("invalid-item"), Vector.empty)),
            Some("en"),
            Vector.empty,
            Vector.empty,
            Vector.empty
          )

          Then("profile vocabulary errors block publication rather than silently changing policy")
          invalid.errors.map(_.code) should contain allOf (
            "credit.presentation.invalid-hold",
            "credit.item.invalid-obligation",
            "credit.item.invalid-surface"
          )

          And("two files may not silently redefine one profile inside the same configuration layer")
          _write_profile_at(dir.resolve("conf/cozy"), "duplicate-file", _empty_profile_yaml("invalid"))
          val error = intercept[RuntimeException] {
            _empty_resolution(dir, Some("invalid"))
          }
          error.getMessage should include_text("Duplicate video credit profile ids in package-conf: invalid")
        }
      }
    }

    "project one effective set to every publication surface" in {
      _with_temp_dir("projections") { dir =>
        Given("a valid effective set with material and voice credits")
        _write_profile(dir, "baseline")
        val effective = CozyVideoCredits.resolve(
          dir,
          Some(CozyVideoCredits.Settings(Some("baseline"), Vector.empty, Vector.empty)),
          Some("ja"),
          Vector(_script(Vector("zundamon"))),
          Vector(_zundamon_asset),
          Vector(dir.resolve("audio/manifest.json") -> _audio_entries("ja", Vector("zundamon")))
        ).requireValid()

        When("Cozy writes structured publication and renderer-neutral projections")
        val files = CozyVideoCredits.write(dir.resolve("build/credits"), effective)
        val json = _read(files.jsonFile)
        val markdown = _read(files.markdownFile)
        val renderer = _read(files.rendererPropsFile)

        Then("all files carry the same selected credits and digest source of truth")
        files.jsonFile should be_regular_file
        files.markdownFile should be_regular_file
        files.rendererPropsFile should be_regular_file
        json should include_text(effective.digest)
        json should include_text("zundamon-image")
        markdown should include_text("ずんだもん立ち絵")
        renderer should include_text(effective.digest)
        renderer should include_text("VOICEVOX:ずんだもん")
      }
    }

    "produce a relocation-stable digest that includes presentation semantics" in {
      _with_temp_dir("digest-a") { dira =>
        _with_temp_dir("digest-b") { dirb =>
          Given("identical effective credit semantics in two different workspaces")
          _write_profile(dira, "baseline")
          _write_profile(dirb, "baseline")
          val settings = Some(CozyVideoCredits.Settings(Some("baseline"), Vector.empty, Vector.empty))
          val speakers = Vector("zundamon")
          val effectivea = CozyVideoCredits.resolve(
            dira,
            settings,
            Some("ja"),
            Vector(_script(speakers)),
            Vector(_zundamon_asset),
            Vector(dira.resolve("audio/manifest.json") -> _audio_entries("ja", speakers))
          ).requireValid()
          val effectiveb = CozyVideoCredits.resolve(
            dirb,
            settings,
            Some("ja"),
            Vector(_script(speakers)),
            Vector(_zundamon_asset),
            Vector(dirb.resolve("different/audio/manifest.json") -> _audio_entries("ja", speakers))
          ).requireValid()

          When("Cozy computes each effective digest")
          val relocateddigest = effectiveb.digest

          Then("profile and manifest filesystem locations do not affect semantic identity")
          relocateddigest shouldBe effectivea.digest

          When("the profile changes the credit-page hold contract")
          _write_profile_at(
            dirb.resolve("conf/cozy"),
            "baseline",
            _profile_yaml("baseline").replace("hold-seconds: 4.0", "hold-seconds: 6.0")
          )
          val changed = CozyVideoCredits.resolve(
            dirb,
            settings,
            Some("ja"),
            Vector(_script(speakers)),
            Vector(_zundamon_asset),
            Vector(dirb.resolve("different/audio/manifest.json") -> _audio_entries("ja", speakers))
          ).requireValid()

          Then("the digest changes because renderer-visible presentation is part of the contract")
          changed.digest should not be effectivea.digest
        }
      }
    }
  }

  private def _expected_ids(hasreimumarisa: Boolean, haszundamon: Boolean, locale: String): Vector[String] = {
    val materials =
      (if (hasreimumarisa) Vector("touhou-original", "kitsune-material") else Vector.empty) ++
        (if (haszundamon) Vector("zundamon-image") else Vector.empty)
    val voices =
      if (locale == "en")
        Vector("macos-voice")
      else {
        val speakers =
          if (hasreimumarisa && haszundamon) Vector("voice-metan", "voice-tsumugi", "voice-zundamon")
          else if (hasreimumarisa) Vector("voice-metan", "voice-tsumugi")
          else if (haszundamon) Vector("voice-zundamon")
          else Vector("voice-metan")
        Vector("voicevox-tool") ++ speakers
      }
    (materials ++ voices).sortBy(_credit_order)
  }

  private def _credit_order(id: String): Int = id match {
    case "touhou-original" => 10
    case "kitsune-material" => 20
    case "zundamon-image" => 30
    case "voicevox-tool" => 40
    case "voice-metan" => 50
    case "voice-tsumugi" => 60
    case "voice-zundamon" => 70
    case "macos-voice" => 80
    case _ => 999
  }

  private def _speakers(hasreimumarisa: Boolean, haszundamon: Boolean): Vector[String] = {
    val values =
      (if (hasreimumarisa) Vector("reimu", "marisa") else Vector.empty) ++
        (if (haszundamon) Vector("zundamon") else Vector.empty)
    if (values.isEmpty) Vector("narrator") else values
  }

  private def _script(speakers: Vector[String]): CozyVideo.VideoScript =
    CozyVideo.VideoScript(
      Some("Credit fixture"),
      None,
      Json.obj(),
      Json.obj(),
      Map.empty,
      Json.obj(),
      Map.empty,
      Vector.empty,
      speakers.zipWithIndex.map { case (speaker, index) =>
        CozyVideo.VideoScene(
          Some(s"scene-${index + 1}"),
          Some(speaker),
          None,
          Some("Narration"),
          None,
          Some(1.0),
          None,
          None,
          Vector.empty
        )
      }
    )

  private def _audio_entries(locale: String, speakers: Vector[String]): Vector[CozyVideo.VideoAudioManifestEntry] =
    speakers.map { speaker =>
      if (locale == "ja") {
        val identity = speaker match {
          case "marisa" => "春日部つむぎ"
          case "zundamon" => "ずんだもん"
          case _ => "四国めたん"
        }
        _audio_entry("voicevox", identity, speaker)
      } else {
        val identity = if (speaker == "marisa") "Karen" else "Samantha"
        _audio_entry("macos-say", identity, speaker)
      }
    }

  private def _audio_entry(provider: String, identity: String, speaker: String): CozyVideo.VideoAudioManifestEntry =
    CozyVideo.VideoAudioManifestEntry(
      s"scene-$speaker",
      Some(speaker),
      s"$speaker.wav",
      0.0,
      1.0,
      1.0,
      0.0,
      Some(provider),
      Some(if (provider == "macos-say") "host" else "external-http"),
      Some(identity),
      None,
      None
    )

  private val _zundamon_asset = CozyVideoAssets.CreditEvidence(
    "zundamon",
    "character-material",
    Vector("character.zundamon"),
    "LicenseRef-Zundamon",
    "creator:sakamoto-ahiru",
    Vector.empty,
    None,
    "configured"
  )

  private def _empty_resolution(dir: Path, explicit: Option[String]): CozyVideoCredits.EffectiveSet =
    CozyVideoCredits.resolve(
      dir,
      explicit.map(x => CozyVideoCredits.Settings(Some(x), Vector.empty, Vector.empty)),
      Some("en"),
      Vector.empty,
      Vector.empty,
      Vector.empty
    ).requireValid()

  private def _write_profile(dir: Path, id: String): Unit =
    _write_profile_at(dir.resolve("conf/cozy"), id, _profile_yaml(id))

  private def _write_profile_at(root: Path, id: String, contents: String = ""): Unit =
    _write(root.resolve(s"video/credit-profiles/$id.yaml"), if (contents.nonEmpty) contents else _empty_profile_yaml(id))

  private def _write_default(dir: Path, id: String): Unit =
    _write_config(dir.resolve("conf/cozy/config.yaml"), id)

  private def _write_config(path: Path, id: String): Unit =
    _write(path, s"video:\n  credits:\n    default-profile: $id\n")

  private def _empty_profile_yaml(id: String): String =
    s"""schema: cozy.video.credits.v1
       |profile: $id
       |presentation:
       |  title:
       |    default: Credits
       |""".stripMargin

  private def _profile_yaml(id: String): String =
    s"""schema: cozy.video.credits.v1
       |profile: $id
       |required-audio-providers: [voicevox]
       |presentation:
       |  title:
       |    ja: 使用素材・音声
       |    en: Credits
       |  hold-seconds: 4.0
       |selectors:
       |  - when:
       |      any-character-id: [reimu, marisa]
       |    include: [touhou-original, kitsune-material]
       |  - when:
       |      any-asset-tag: [character.zundamon]
       |    include: [zundamon-image]
       |  - when:
       |      audio-provider: voicevox
       |      voice-identity: 四国めたん
       |    include: [voicevox-tool, voice-metan]
       |  - when:
       |      audio-provider: voicevox
       |      voice-identity: 春日部つむぎ
       |    include: [voicevox-tool, voice-tsumugi]
       |  - when:
       |      audio-provider: voicevox
       |      voice-identity: ずんだもん
       |    include: [voicevox-tool, voice-zundamon]
       |  - when:
       |      audio-provider: macos-say
       |    include: [macos-voice]
       |credits:
       |  - id: touhou-original
       |    category: original-work
       |    order: 10
       |    label: {ja: 東方Project二次創作, en: Touhou Project derivative work}
       |    publication-text: {ja: 東方Project二次創作, en: Touhou Project derivative work}
       |    creator: Team Shanghai Alice
       |    source-url: https://www.touhou-project.news/
       |  - id: kitsune-material
       |    category: character-material
       |    order: 20
       |    label: {ja: きつねゆっくり素材, en: Kitsune Yukkuri material}
       |    publication-text: {ja: きつねゆっくり素材, en: Kitsune Yukkuri material}
       |    creator: Kitsune
       |    terms-url: https://ci-en.net/creator/34363/article/1770533
       |  - id: zundamon-image
       |    category: character-material
       |    order: 30
       |    label: {ja: ずんだもん立ち絵, en: Zundamon standing picture}
       |    publication-text: {ja: ずんだもん立ち絵, en: Zundamon standing picture}
       |    creator: Sakamoto Ahiru
       |    source-url: https://seiga.nicovideo.jp/seiga/im10788496
       |  - id: voicevox-tool
       |    category: tool
       |    order: 40
       |    label: {ja: VOICEVOX, en: VOICEVOX}
       |    publication-text: {ja: VOICEVOX, en: VOICEVOX}
       |  - id: voice-metan
       |    category: voice
       |    order: 50
       |    label: {ja: "VOICEVOX:四国めたん", en: "VOICEVOX:Shikoku Metan"}
       |    publication-text: {ja: "VOICEVOX:四国めたん", en: "VOICEVOX:Shikoku Metan"}
       |  - id: voice-tsumugi
       |    category: voice
       |    order: 60
       |    label: {ja: "VOICEVOX:春日部つむぎ", en: "VOICEVOX:Kasukabe Tsumugi"}
       |    publication-text: {ja: "VOICEVOX:春日部つむぎ", en: "VOICEVOX:Kasukabe Tsumugi"}
       |  - id: voice-zundamon
       |    category: voice
       |    order: 70
       |    label: {ja: "VOICEVOX:ずんだもん", en: "VOICEVOX:Zundamon"}
       |    publication-text: {ja: "VOICEVOX:ずんだもん", en: "VOICEVOX:Zundamon"}
       |  - id: macos-voice
       |    category: voice
       |    obligation: recommended
       |    order: 80
       |    label: {ja: macOSシステム音声, en: macOS system voice}
       |    publication-text: {ja: macOSシステム音声, en: macOS system voice}
       |""".stripMargin

  private def _with_temp_dir(name: String)(body: Path => Unit): Unit = {
    val root = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().
      resolve("target/test-generated/video-credits").resolve(name)
    _delete(root)
    Files.createDirectories(root)
    body(root)
  }

  private def _with_user_home[A](home: Path)(body: => A): A =
    CozyProjectContext.withUserHomeLock {
      val previoushome = Option(System.getProperty("user.home"))
      System.setProperty("user.home", home.toString)
      try body
      finally {
        previoushome.foreach(System.setProperty("user.home", _))
        if (previoushome.isEmpty) System.clearProperty("user.home")
      }
    }

  private def _write(path: Path, contents: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, contents, StandardCharsets.UTF_8)
  }

  private def _read(path: Path): String =
    Files.readString(path, StandardCharsets.UTF_8)

  private def _delete(path: Path): Unit =
    if (Files.exists(path)) {
      val paths = Files.walk(path)
      try paths.iterator().asScala.toVector.reverse.foreach(Files.delete)
      finally paths.close()
    }
}
