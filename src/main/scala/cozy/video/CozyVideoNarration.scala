package cozy.video

import org.goldenport.RAISE
import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.context.{FaultException, NetworkIoFault, SubsystemIoFault}
import org.goldenport.io.InputSource
import cozy.config.{CozyProjectContext, CozyProjectYamlConfig}
import cozy.runtime.CozyCliArgs
import org.goldenport.cli.spec
import org.smartdox.semanticweb.{Rdf, RdfRenderer, Vocabulary}
import io.circe.{Decoder, HCursor, Json}
import io.circe.parser
import java.io.ByteArrayOutputStream
import java.net.{URI, URLEncoder}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{Files, LinkOption, Path, Paths, StandardCopyOption}
import java.security.MessageDigest
import java.time.{Duration => JDuration}
import java.util.concurrent.TimeUnit
import scala.collection.JavaConverters._
import scala.concurrent.{Await, ExecutionContext => ScalaExecutionContext, Future, blocking}
import scala.concurrent.duration._
import scala.util.Try
import scala.util.control.NonFatal

/*
 * @since   Aug. 14, 2026
 * @version Aug. 14, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] trait CozyVideoNarration {
  self: CozyVideoTypes with CozyVideoRuntime with CozyVideoCommand with CozyVideoToolValidation with CozyVideoTranscription with CozyVideoReviewEvidence with CozyVideoBuildReplay with CozyVideoRdf with CozyVideoRenderWorkspace with CozyVideoRenderTemplates with CozyVideoPlanning with CozyVideoPresentation =>
  private[video] final class VoicevoxNarrationProvider(
    baseUrl: String,
    client: VoicevoxClient
  ) extends NarrationProvider {
    private[video] val _speaker_ids = scala.collection.mutable.Map.empty[String, Int]

    val id = "voicevox"
    val executionMode = "external-http"

    def synthesize(text: String, voice: Json): NarrationAudio = {
      val cachekey = voice.noSpaces
      val speakerid = _speaker_ids.getOrElseUpdate(cachekey, _resolve_speaker_id(baseUrl, voice, client))
      val audioquery = _apply_voice_tuning(
        _with_external_service_failure("audio_query", "voicevox", baseUrl) {
          client.audioQuery(baseUrl, text, speakerid)
        },
        voice
      )
      NarrationAudio(
        _with_external_service_failure("synthesis", "voicevox", baseUrl) {
          client.synthesis(baseUrl, speakerid, audioquery)
        },
        Some(_voicevox_voice_identity(voice, Some(speakerid))),
        Some(speakerid.toString),
        None
      )
    }
  }

  private[video] final class MacosSayNarrationProvider(
    projectroot: Path,
    runner: VideoProcessRunner
  ) extends NarrationProvider {
    val id = "macos-say"
    val executionMode = "host"

    def synthesize(text: String, voice: Json): NarrationAudio = {
      val voicename = _json_string(voice, "voiceName").
        orElse(_json_string(voice, "name")).
        orElse(_json_string(voice, "speakerName")).
        getOrElse("Samantha")
      val rate = _json_int(voice, "rate").getOrElse(180)
      if (rate <= 0)
        RAISE.invalidArgumentFault(s"macos-say voice rate must be positive: $rate")
      val workdir = Files.createTempDirectory("cozy-macos-say-")
      val source = workdir.resolve("narration.aiff")
      val wav = workdir.resolve("narration.wav")
      try {
        val sayresult = runner.run(
          Vector("say", "-v", voicename, "-r", rate.toString, "-o", source.toString, text),
          projectroot
        )
        if (!sayresult.isSuccess)
          RAISE.invalidArgumentFault(s"macOS say narration failed: ${sayresult.text}")
        val ffmpegresult = runner.run(
          Vector(
            "ffmpeg",
            "-y",
            "-i",
            source.toString,
            "-ar",
            _default_sample_rate.toString,
            "-ac",
            _default_audio_channels.toString,
            "-sample_fmt",
            "s16",
            wav.toString
          ),
          projectroot
        )
        if (!ffmpegresult.isSuccess)
          RAISE.invalidArgumentFault(s"macOS say audio normalization failed: ${ffmpegresult.text}")
        if (!Files.isRegularFile(wav))
          RAISE.invalidArgumentFault(s"macOS say audio normalization did not create WAV output: $wav")
        NarrationAudio(
          Files.readAllBytes(wav),
          Some(voicename),
          None,
          Some("macos-say")
        )
      } finally {
        _delete_tree(workdir)
      }
    }
  }

  private[video] final class PiperNarrationProvider(
    projectroot: Path,
    execution: VideoExecutionConfig,
    runner: VideoProcessRunner
  ) extends NarrationProvider {
    val id = "piper"
    val executionMode = "docker"

    def synthesize(text: String, voice: Json): NarrationAudio = {
      val modelid = _json_string(voice, "model").
        orElse(_json_string(voice, "modelIdentity")).
        map(_.trim).
        filter(_.nonEmpty).
        getOrElse(_default_piper_model)
      val workroot = projectroot.resolve("target/cozy-video/piper").normalize()
      Files.createDirectories(workroot)
      val workdir = Files.createTempDirectory(workroot, "narration-")
      val input = workdir.resolve("narration.txt")
      val output = workdir.resolve("narration.wav")
      try {
        Files.writeString(input, text, StandardCharsets.UTF_8)
        val command = _execution_command(
          projectroot,
          execution,
          "textus-toolchain",
          Vector(
            "piper-synthesize",
            "--model",
            modelid,
            "--input",
            _execution_path(projectroot, execution, input),
            "--output",
            _execution_path(projectroot, execution, output)
          ),
          networkdisabled = true
        )
        val result = runner.run(command, projectroot)
        if (!result.isSuccess)
          RAISE.invalidArgumentFault(s"Piper narration failed for model $modelid: ${result.text}")
        if (!Files.isRegularFile(output))
          RAISE.invalidArgumentFault(s"Piper narration did not create WAV output for model $modelid.")
        NarrationAudio(
          Files.readAllBytes(output),
          Some(modelid),
          None,
          Some(modelid)
        )
      } finally {
        _delete_tree(workdir)
      }
    }
  }

  private[video] def _delete_tree(path: Path): Unit =
    if (Files.exists(path)) {
      val paths = Files.walk(path)
      try paths.iterator().asScala.toVector.reverse.foreach(Files.deleteIfExists)
      finally paths.close()
    }

  private[video] def _synthesize_script(
    scriptfile: Path,
    script: VideoScript,
    savedir: Path,
    provider: NarrationProvider,
    diagnostics: Vector[String],
    execution: VideoExecutionConfig,
    toolchecks: Vector[VideoToolCheck]
  ): VideoSynthesisResult = {
    Files.createDirectories(savedir)
    val concatparts = scala.collection.mutable.ArrayBuffer.empty[Path]
    val entries = script.expandedScenes.zipWithIndex.map {
      case (scene, index) =>
        val sceneid = scene.id.getOrElse(f"scene-${index + 1}%02d")
        val fileid = _scene_file_id(sceneid)
        val scenewav = _audio_output_file(savedir, f"${index + 1}%02d-$fileid.wav")
        val leadsilence = math.max(0.0, scene.leadSilence.getOrElse(0.0))
        val (manifestprovider, manifestexecutionmode, voiceidentity, voiceid, modelidentity) =
          if (_is_silent_scene(scene)) {
            _write_silence_wav(scenewav, 0.01)
            (None, None, None, None, None)
          } else {
            val voice = _voice_for_scene(script, scene)
            val text = _spoken_text(script, scene)
            val audio = provider.synthesize(text, voice)
            _write_wav(scenewav, _normalize_provider_wav(audio.wav, s"${provider.id}:$sceneid"))
            (
              Some(provider.id),
              Some(provider.executionMode),
              audio.voiceIdentity,
              audio.voiceId,
              audio.modelIdentity
            )
          }
        val audioduration = _wav_duration(scenewav)
        val targetduration = scene.durationSeconds
        if (leadsilence > 0) {
          val leadwav = _audio_output_file(savedir, f"${index + 1}%02d-$fileid-lead.wav")
          _write_silence_wav(leadwav, leadsilence)
          concatparts += leadwav
        }
        concatparts += scenewav
        val tailsilence = math.max(0.0, targetduration - leadsilence - audioduration)
        if (tailsilence > 0) {
          val silencewav = _audio_output_file(savedir, f"${index + 1}%02d-$fileid-silence.wav")
          _write_silence_wav(silencewav, tailsilence)
          concatparts += silencewav
        }
        VideoAudioManifestEntry(
          sceneid,
          scene.speaker,
          scenewav.getFileName.toString,
          _round3(leadsilence),
          _round3(audioduration),
          targetduration,
          _round3(tailsilence),
          manifestprovider,
          manifestexecutionmode,
          voiceidentity,
          voiceid,
          modelidentity,
          Some(_default_sample_rate),
          Some(_default_audio_channels),
          Some(_default_audio_bits_per_sample)
        )
    }
    val combined = _audio_output_file(savedir, s"${_basename(scriptfile)}.wav")
    _concatenate_wavs(concatparts.toVector, combined)
    val manifest = _audio_output_file(savedir, "manifest.json")
    Files.writeString(manifest, _manifest_json(entries).spaces2, StandardCharsets.UTF_8)
    VideoSynthesisResult(
      scriptfile,
      savedir,
      provider.id,
      provider.executionMode,
      diagnostics,
      execution.toolMode,
      execution.dockerImage,
      combined,
      manifest,
      entries,
      toolchecks
    )
  }

  private[video] def _plan_narration_providers(plan: VideoPlan): Set[String] =
    plan.parts.flatMap(_.script.map(_resolve_narration_selection(_).provider)).toSet

  private[video] def _resolve_narration_selection(script: VideoScript): NarrationSelection = {
    val canonical = _canonical_narration_provider(script.narration)
    val legacy = (
      _json_string(script.voice, "engine").toVector ++
        script.characters.values.toVector.flatMap(x => x.hcursor.downField("voice").focus.flatMap(_json_string(_, "engine")))
    ).map(_.trim.toLowerCase).filter(_.nonEmpty).distinct.sorted
    if (legacy.size > 1)
      RAISE.invalidArgumentFault(
        s"Conflicting legacy voice.engine providers: ${legacy.mkString(", ")}. Set one narration.provider for the script."
      )
    val legacyprovider = legacy.headOption
    (canonical, legacyprovider) match {
      case (Some(current), Some(old)) if current != old =>
        RAISE.invalidArgumentFault(
          s"Conflicting narration provider settings: narration.provider=$current, voice.engine=$old."
        )
      case (Some(current), Some(_)) =>
        NarrationSelection(current, Vector(_legacy_voice_engine_diagnostic))
      case (Some(current), None) =>
        NarrationSelection(current, Vector.empty)
      case (None, Some(old)) =>
        NarrationSelection(old, Vector(_legacy_voice_engine_diagnostic))
      case (None, None) =>
        NarrationSelection("voicevox", Vector.empty)
    }
  }

  private[video] def _canonical_narration_provider(narration: Json): Option[String] = {
    if (!narration.isObject)
      RAISE.invalidArgumentFault("narration must be an object containing provider.")
    narration.hcursor.downField("provider").focus.map { value =>
      val provider = value.asString.map(_.trim.toLowerCase).getOrElse(
        RAISE.invalidArgumentFault("narration.provider must be a non-empty string.")
      )
      if (provider.isEmpty)
        RAISE.invalidArgumentFault("narration.provider must be a non-empty string.")
      provider
    }
  }

  private[video] val _legacy_voice_engine_diagnostic =
    "Deprecated voice.engine authoring detected; use narration.provider instead."

  private[video] def _narration_voice_identity(provider: String, voice: Json): String =
    provider match {
      case "voicevox" => _voicevox_voice_identity(voice)
      case _ => _json_string(voice, "name").orElse(_json_string(voice, "voice")).getOrElse("default")
    }

  private[video] def _voicevox_voice_identity(voice: Json, resolvedid: Option[Int] = None): String = {
    val speakername = _json_string(voice, "speakerName").map(_.trim).filter(_.nonEmpty)
    val stylename = _json_string(voice, "styleName").map(_.trim).filter(_.nonEmpty)
    (speakername, stylename) match {
      case (Some(speaker), Some(style)) => s"$speaker/$style"
      case (None, None) =>
        resolvedid.orElse(_json_int(voice, "fallbackSpeakerId")).map(id => s"speaker-id:$id").getOrElse(
          RAISE.invalidArgumentFault("VOICEVOX requires explicit speakerName + styleName or fallbackSpeakerId")
        )
      case _ =>
        RAISE.invalidArgumentFault("VOICEVOX requires explicit speakerName + styleName or fallbackSpeakerId")
    }
  }

  private[video] def _scene_file_id(sceneid: String): String = {
    _file_segment_id(sceneid, "scene id")
  }

  private[video] def _audio_output_file(savedir: Path, filename: String): Path = {
    val path = savedir.resolve(filename).normalize()
    val root = savedir.toAbsolutePath.normalize()
    val absolute = path.toAbsolutePath.normalize()
    if (!absolute.startsWith(root))
      RAISE.invalidArgumentFault(s"Audio output path escapes --save directory: $filename")
    path
  }

  private[video] def _voice_for_scene(script: VideoScript, scene: VideoScene): Json =
    scene.speaker.flatMap { speaker =>
      script.characters.get(speaker).flatMap(_.hcursor.downField("voice").focus)
    }.getOrElse(script.voice)

  private[video] def _resolve_speaker_id(baseurl: String, voice: Json, voicevox: VoicevoxClient): Int = {
    val fallback = _json_int(voice, "fallbackSpeakerId")
    val speakername = _json_string(voice, "speakerName").map(_.trim).filter(_.nonEmpty)
    val stylename = _json_string(voice, "styleName").map(_.trim).filter(_.nonEmpty)
    (speakername, stylename) match {
      case (None, None) => fallback.getOrElse(
        RAISE.invalidArgumentFault("VOICEVOX requires explicit speakerName + styleName or fallbackSpeakerId")
      )
      case (Some(_), None) | (None, Some(_)) =>
        RAISE.invalidArgumentFault("VOICEVOX requires explicit speakerName + styleName or fallbackSpeakerId")
      case (Some(speaker), Some(style)) =>
        val speakers = _with_external_service_failure("speakers", "voicevox", baseurl) {
          voicevox.speakers(baseurl)
        }
        val matchid = speakers.asArray.toVector.flatten.flatMap { item =>
          if (_json_string(item, "name").contains(speaker))
            _json_array(item, "styles").toVector.flatten.find(x => _json_string(x, "name").contains(style)).flatMap(_json_int(_, "id"))
          else None
        }.headOption
        matchid.orElse(fallback).getOrElse(
          RAISE.invalidArgumentFault(s"VOICEVOX speaker style not found: $speaker/$style; configure fallbackSpeakerId to permit fallback")
        )
    }
  }

  private[video] def _spoken_text(script: VideoScript, scene: VideoScene): String = {
    val raw = scene.narration.orElse(scene.line).orElse(scene.caption).getOrElse(
      RAISE.invalidArgumentFault(s"Scene has no narration/line/caption text: ${scene.id.getOrElse("(no id)")}")
    )
    val normalized = _apply_voice_text_normalization(raw, script.voiceTextNormalization)
    CozyVideoPronunciations.default.applyTo(normalized, script.pronunciations)
  }

  private[video] def _apply_voice_text_normalization(text: String, options: Json): String = {
    if (_json_boolean(options, "removeSpaces").getOrElse(false))
      text.replaceAll("\\s+", "")
    else if (_json_boolean(options, "removeAsciiJapaneseSpaces").getOrElse(false)) {
      val japanese = "\\u3040-\\u30ff\\u3400-\\u9fff"
      val ascii = "A-Za-z0-9"
      text.
        replaceAll(s"([$japanese])\\s+([$ascii])", "$1$2").
        replaceAll(s"([$ascii])\\s+([$japanese])", "$1$2")
    } else {
      text
    }
  }

  private[video] def _is_silent_scene(scene: VideoScene): Boolean =
    scene.silent.getOrElse(false) || scene.narration.orElse(scene.line).orElse(scene.caption).isEmpty

  private[video] def _apply_voice_tuning(audioquery: Json, voice: Json): Json =
    Vector("speedScale", "pitchScale", "intonationScale", "volumeScale", "prePhonemeLength", "postPhonemeLength").foldLeft(audioquery) { (z, name) =>
      _json_double(voice, name).map(value => z.deepMerge(Json.obj(name -> Json.fromDoubleOrNull(value)))).getOrElse(z)
    }

  private[video] def _write_silence_wav(path: Path, duration: Double): Unit = {
    val frames = math.max(0, (duration * _default_sample_rate).toInt)
    _write_wav(path, WaveData(1, _default_sample_rate, _default_audio_channels, _default_audio_bits_per_sample, Array.fill(frames * 2)(0.toByte)))
  }

  private[video] def _wav_duration(path: Path): Double =
    _read_wav(path).durationSeconds

  private[video] def _concatenate_wavs(parts: Vector[Path], output: Path): Unit = {
    if (parts.isEmpty) {
      _write_silence_wav(output, 0.01)
    } else {
      val waves = parts.map(_read_wav)
      val head = waves.head
      waves.tail.foreach { wave =>
        if (
          wave.audioformat != head.audioformat ||
          wave.samplerate != head.samplerate ||
          wave.channels != head.channels ||
          wave.bitspersample != head.bitspersample
        )
          RAISE.invalidArgumentFault(s"Cannot concatenate wav with different params: $output")
      }
      val out = new ByteArrayOutputStream()
      waves.foreach(x => out.write(x.data))
      _write_wav(output, head.copy(data = out.toByteArray))
    }
  }

  private[video] def _read_wav(path: Path): WaveData = {
    _read_wav_bytes(Files.readAllBytes(path), path.toString)
  }

  private[video] def _read_wav_bytes(bytes: Array[Byte], label: String): WaveData = {
    if (bytes.length < 44 || _ascii(bytes, 0, 4) != "RIFF" || _ascii(bytes, 8, 4) != "WAVE")
      RAISE.invalidArgumentFault(s"Invalid WAV file: $label")
    var offset = 12
    var audioformat = 0
    var samplerate = 0
    var channels = 0
    var bitspersample = 0
    var data = Array.emptyByteArray
    while (offset + 8 <= bytes.length) {
      val id = _ascii(bytes, offset, 4)
      val size = _read_int_le(bytes, offset + 4)
      if (size < 0)
        RAISE.invalidArgumentFault(s"Invalid WAV file: $label")
      val start = offset + 8
      val end = start.toLong + size.toLong
      if (end > bytes.length.toLong)
        RAISE.invalidArgumentFault(s"Invalid WAV file: $label")
      id match {
        case "fmt " =>
          if (size < 16)
            RAISE.invalidArgumentFault(s"Invalid WAV file: $label")
          audioformat = _read_short_le(bytes, start)
          channels = _read_short_le(bytes, start + 2)
          samplerate = _read_int_le(bytes, start + 4)
          bitspersample = _read_short_le(bytes, start + 14)
        case "data" =>
          data = bytes.slice(start, end.toInt)
        case _ =>
      }
      val next = end + (size & 1)
      offset = math.min(next, bytes.length.toLong).toInt
    }
    if (audioformat <= 0 || samplerate <= 0 || channels <= 0 || bitspersample <= 0 || data.isEmpty)
      RAISE.invalidArgumentFault(s"Invalid WAV file: $label")
    WaveData(audioformat, samplerate, channels, bitspersample, data)
  }

  private[video] def _normalize_provider_wav(bytes: Array[Byte], label: String): WaveData = {
    val source = _read_wav_bytes(bytes, label)
    if (source.audioformat != 1)
      RAISE.invalidArgumentFault(s"Unsupported WAV encoding for $label: format=${source.audioformat}. Use PCM WAV.")
    val bytespersample = source.bitspersample / 8
    if (!Set(8, 16, 24, 32).contains(source.bitspersample) || bytespersample * 8 != source.bitspersample)
      RAISE.invalidArgumentFault(
        s"Unsupported PCM bit depth for $label: ${source.bitspersample}. Supported bit depths: 8, 16, 24, 32."
      )
    val framesize = bytespersample * source.channels
    if (source.data.length % framesize != 0)
      RAISE.invalidArgumentFault(s"Invalid PCM frame alignment for $label.")
    val sourceframes = source.data.length / framesize
    val mono = Array.tabulate(sourceframes) { frame =>
      var sum = 0.0
      var channel = 0
      while (channel < source.channels) {
        sum += _pcm_sample(source.data, (frame * source.channels + channel) * bytespersample, source.bitspersample)
        channel += 1
      }
      sum / source.channels.toDouble
    }
    val targetframes = math.max(1, math.round(sourceframes.toDouble * _default_sample_rate.toDouble / source.samplerate.toDouble).toInt)
    val data = new Array[Byte](targetframes * 2)
    var frame = 0
    while (frame < targetframes) {
      val position = frame.toDouble * source.samplerate.toDouble / _default_sample_rate.toDouble
      val lower = math.min(sourceframes - 1, position.toInt)
      val upper = math.min(sourceframes - 1, lower + 1)
      val fraction = position - lower.toDouble
      val sample = mono(lower) + (mono(upper) - mono(lower)) * fraction
      val encoded = math.round(math.max(-1.0, math.min(1.0, sample)) * 32767.0).toInt
      data(frame * 2) = (encoded & 0xff).toByte
      data(frame * 2 + 1) = ((encoded >>> 8) & 0xff).toByte
      frame += 1
    }
    WaveData(1, _default_sample_rate, _default_audio_channels, _default_audio_bits_per_sample, data)
  }

  private[video] def _pcm_sample(data: Array[Byte], offset: Int, bitspersample: Int): Double =
    bitspersample match {
      case 8 => ((data(offset) & 0xff) - 128).toDouble / 128.0
      case 16 => _read_short_le(data, offset).toShort.toDouble / 32768.0
      case 24 =>
        val raw = (data(offset) & 0xff) | ((data(offset + 1) & 0xff) << 8) | ((data(offset + 2) & 0xff) << 16)
        val signed = if ((raw & 0x800000) != 0) raw | 0xff000000 else raw
        signed.toDouble / 8388608.0
      case 32 => _read_int_le(data, offset).toDouble / 2147483648.0
    }

  private[video] def _write_wav(path: Path, wave: WaveData): Unit = {
    Files.createDirectories(path.getParent)
    val out = new ByteArrayOutputStream()
    _write_ascii(out, "RIFF")
    _write_int_le(out, 36 + wave.data.length)
    _write_ascii(out, "WAVE")
    _write_ascii(out, "fmt ")
    _write_int_le(out, 16)
    _write_short_le(out, wave.audioformat)
    _write_short_le(out, wave.channels)
    _write_int_le(out, wave.samplerate)
    _write_int_le(out, wave.samplerate * wave.channels * wave.bitspersample / 8)
    _write_short_le(out, wave.channels * wave.bitspersample / 8)
    _write_short_le(out, wave.bitspersample)
    _write_ascii(out, "data")
    _write_int_le(out, wave.data.length)
    out.write(wave.data)
    Files.write(path, out.toByteArray)
  }

  private[video] def _manifest_json(entries: Vector[VideoAudioManifestEntry]): Json =
    Json.fromValues(entries.map { entry =>
      Json.obj(
        "sceneId" -> Json.fromString(entry.sceneId),
        "speaker" -> entry.speaker.map(Json.fromString).getOrElse(Json.Null),
        "file" -> Json.fromString(entry.file),
        "leadSilence" -> Json.fromDoubleOrNull(entry.leadSilence),
        "audioDuration" -> Json.fromDoubleOrNull(entry.audioDuration),
        "targetDuration" -> Json.fromDoubleOrNull(entry.targetDuration),
        "tailSilence" -> Json.fromDoubleOrNull(entry.tailSilence),
        "provider" -> entry.provider.map(Json.fromString).getOrElse(Json.Null),
        "executionMode" -> entry.executionMode.map(Json.fromString).getOrElse(Json.Null),
        "voiceIdentity" -> entry.voiceIdentity.map(Json.fromString).getOrElse(Json.Null),
        "voiceId" -> entry.voiceId.map(Json.fromString).getOrElse(Json.Null),
        "modelIdentity" -> entry.modelIdentity.map(Json.fromString).getOrElse(Json.Null),
        "sampleRate" -> entry.sampleRate.map(Json.fromInt).getOrElse(Json.Null),
        "channels" -> entry.channels.map(Json.fromInt).getOrElse(Json.Null),
        "bitsPerSample" -> entry.bitsPerSample.map(Json.fromInt).getOrElse(Json.Null)
      )
    })

  private[video] def _write_ascii(out: ByteArrayOutputStream, value: String): Unit =
    out.write(value.getBytes(StandardCharsets.US_ASCII))

  private[video] def _write_int_le(out: ByteArrayOutputStream, value: Int): Unit = {
    out.write(value & 0xff)
    out.write((value >>> 8) & 0xff)
    out.write((value >>> 16) & 0xff)
    out.write((value >>> 24) & 0xff)
  }

  private[video] def _write_short_le(out: ByteArrayOutputStream, value: Int): Unit = {
    out.write(value & 0xff)
    out.write((value >>> 8) & 0xff)
  }

  private[video] def _read_int_le(bytes: Array[Byte], offset: Int): Int =
    (bytes(offset) & 0xff) |
      ((bytes(offset + 1) & 0xff) << 8) |
      ((bytes(offset + 2) & 0xff) << 16) |
      ((bytes(offset + 3) & 0xff) << 24)

  private[video] def _read_short_le(bytes: Array[Byte], offset: Int): Int =
    (bytes(offset) & 0xff) | ((bytes(offset + 1) & 0xff) << 8)

  private[video] def _ascii(bytes: Array[Byte], offset: Int, length: Int): String =
    new String(bytes, offset, length, StandardCharsets.US_ASCII)

  private[video] def _json_string(json: Json, name: String): Option[String] =
    json.hcursor.downField(name).as[String].toOption

  private[video] def _json_int(json: Json, name: String): Option[Int] =
    json.hcursor.downField(name).as[Int].toOption

  private[video] def _json_double(json: Json, name: String): Option[Double] =
    json.hcursor.downField(name).as[Double].toOption

  private[video] def _json_boolean(json: Json, name: String): Option[Boolean] =
    json.hcursor.downField(name).as[Boolean].toOption

  private[video] def _json_array(json: Json, name: String): Option[Vector[Json]] =
    json.hcursor.downField(name).focus.flatMap(_.asArray)

  private[video] def _basename(path: Path): String = {
    val name = path.getFileName.toString
    val index = name.lastIndexOf('.')
    if (index <= 0) name else name.substring(0, index)
  }

  private[video] def _round3(value: Double): Double =
    BigDecimal(value).setScale(3, BigDecimal.RoundingMode.HALF_UP).toDouble
}
