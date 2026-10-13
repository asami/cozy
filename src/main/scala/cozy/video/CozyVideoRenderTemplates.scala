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
import java.time.{Duration => JDuration}
import java.util.concurrent.TimeUnit
import scala.collection.JavaConverters._
import scala.concurrent.{Await, ExecutionContext => ScalaExecutionContext, Future, blocking}
import scala.concurrent.duration._
import scala.util.Try
import scala.util.control.NonFatal

/*
 * @since   Aug. 14, 2026
 *  version Aug. 19, 2026
 *  version Sep. 29, 2026
 * @version Oct. 12, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] trait CozyVideoRenderTemplates {
  self: CozyVideoTypes with CozyVideoRuntime with CozyVideoCommand with CozyVideoNarration with CozyVideoToolValidation with CozyVideoTranscription with CozyVideoReviewEvidence with CozyVideoBuildReplay with CozyVideoRdf with CozyVideoRenderWorkspace with CozyVideoPlanning with CozyVideoPresentation =>
  private[video] def _remotion_props_json(
    plan: VideoPlan,
    part: VideoPartPlan,
    script: VideoScript,
    audio: VideoAudioInput,
    assets: Vector[(CozyVideoAssets.Resolved, Option[String])],
    characterdialogue: Boolean,
    template: Option[String],
    templateresources: Option[Vector[String]],
    dialogueassets: CharacterDialogueAssets,
    recording: Option[String]
  ): Json = {
    val renderer = plan.project.renderer
    val encoding = plan.encoding
    val fps = encoding.fps
    val width = encoding.width
    val height = encoding.height
    val crf = encoding.crf
    val x264preset = encoding.x264Preset
    val effects = CozyVideoEffects.expand(plan.project.visualEffects)
    val effectprofile = _part_renderer_property(part, "effectProfile").orElse(renderer.flatMap(_.effectProfile)).
      map(_.trim).filter(_.nonEmpty).getOrElse("compact")
    val sectionstarteffect = effects.exists(x => x.role == CozyVideoEffects.Role.SectionStart && x.primitives.nonEmpty)
    val sectiontransitionframes =
      if (characterdialogue && effectprofile == "compact" && sectionstarteffect)
        math.max(0, math.round(1.2 * fps).toInt)
      else
        0
    def _section_key_(scene: VideoScene, index: Int): String =
      scene.section.filter(_.nonEmpty).
        orElse(_json_string(scene.effects, "section").filter(_.nonEmpty)).
        orElse(_json_string(scene.visual, "section").filter(_.nonEmpty)).
        getOrElse(s"scene-$index")
    val scenesectiontransitions = script.expandedScenes.indices.map { index =>
      if (index > 0 && _section_key_(script.expandedScenes(index), index) != _section_key_(script.expandedScenes(index - 1), index - 1))
        sectiontransitionframes
      else
        0
    }
    val actualaudiodurations = audio.files.map(_wav_duration)
    val contentframes = math.max(
      1,
      audio.entries.zip(actualaudiodurations).zip(scenesectiontransitions).map { case ((entry, actualduration), transitionframes) =>
        VideoTiming.evaluate(entry.targetDuration, entry.leadSilence, actualduration, entry.tailSilence, fps).
          effectiveFrameCount.toInt + transitionframes
      }.sum
    )
    val isfirstpart = plan.parts.filter(_.renderable).headOption.exists(_.id == part.id)
    val openingseconds = _effect_parameter_double(
      effects,
      CozyVideoEffects.Role.Opening,
      "hold",
      "seconds"
    ).getOrElse(0.0)
    val openingframes =
      if (isfirstpart) math.max(0, math.round(openingseconds * fps).toInt)
      else 0
    val sectionframes = if (sectionstarteffect) math.min(contentframes, math.round(1.2 * fps).toInt) else 0
    val isfinalpart = plan.parts.filter(_.renderable).lastOption.exists(_.id == part.id)
    val summaryholdseconds = _effect_parameter_double(
      effects,
      CozyVideoEffects.Role.Summary,
      "hold",
      "seconds"
    )
    val summaryframes =
      if (isfinalpart && effects.exists(x => x.role == CozyVideoEffects.Role.Summary && x.primitives.nonEmpty))
        summaryholdseconds.
          map(seconds => math.max(0, math.round(seconds * fps).toInt)).
          getOrElse(math.min(contentframes, math.round(2.4 * fps).toInt))
      else
        0
    val summarystartframe =
      if (summaryholdseconds.isDefined)
        openingframes + contentframes
      else
        openingframes + math.max(0, contentframes - summaryframes)
    val summarystandaloneframes = if (summaryholdseconds.isDefined) summaryframes else 0
    val creditframes =
      if (isfinalpart && plan.credits.hasVideoPage)
        math.max(1, math.round(plan.credits.holdSeconds * fps).toInt)
      else
        0
    val holdseconds = _effect_parameter_double(
      effects,
      CozyVideoEffects.Role.FinalPage,
      "hold",
      "seconds"
    ).getOrElse(0.0)
    val finalframes = if (isfinalpart) math.max(0, math.round(holdseconds * fps).toInt) else 0
    val totalframes = openingframes + contentframes + summarystandaloneframes + creditframes + finalframes
    var startframe = 0
    val scenes = script.expandedScenes.zip(audio.entries).zip(audio.files).zip(actualaudiodurations).zip(scenesectiontransitions).map {
      case ((((scene, entry), file), actualduration), transitionframes) =>
        val authoreddurationframes = VideoTiming.evaluate(
          entry.targetDuration, entry.leadSilence, actualduration, entry.tailSilence, fps
        ).effectiveFrameCount.toInt
        val authoredleadframes = math.max(0, math.round(entry.leadSilence * fps).toInt)
        val audiodurationframes = math.max(1, math.ceil(actualduration * fps).toInt)
        val durationframes = authoreddurationframes + transitionframes
        val leadinframes = authoredleadframes + transitionframes
        val requestedtailsilence = scene.tailSilence.getOrElse(0.0)
        val stagedvisual = dialogueassets.visuals.getOrElse(scene.id.getOrElse(""), scene.visual)
        val json =
        Json.obj(
          "id" -> Json.fromString(entry.sceneId),
          "speaker" -> entry.speaker.map(Json.fromString).getOrElse(Json.Null),
          "text" -> Json.fromString(scene.narration.orElse(scene.line).orElse(scene.caption).getOrElse("")),
          "audioPath" -> Json.fromString(s"audio/${file.getFileName}"),
          "duration" -> Json.fromDoubleOrNull(_effective_render_duration(entry) + transitionframes.toDouble / fps),
          "line" -> scene.line.orElse(scene.narration).orElse(scene.caption).map(Json.fromString).getOrElse(Json.Null),
          "caption" -> scene.caption.orElse(scene.line).orElse(scene.narration).map(Json.fromString).getOrElse(Json.Null),
          "visual" -> stagedvisual,
          "section" -> scene.section.map(Json.fromString).getOrElse(Json.Null),
          "effects" -> scene.effects,
          "silent" -> Json.fromBoolean(scene.silent.getOrElse(false)),
          "startFrame" -> Json.fromInt(startframe),
          "durationFrames" -> Json.fromInt(durationframes),
          "leadInFrames" -> Json.fromInt(leadinframes),
          "sectionTransitionFrames" -> Json.fromInt(transitionframes),
          "audioDuration" -> Json.fromDoubleOrNull(entry.audioDuration),
          "audioDurationFrames" -> Json.fromInt(audiodurationframes),
          "requestedTailSilenceSeconds" -> Json.fromDoubleOrNull(requestedtailsilence),
          "effectiveTailSilenceSeconds" -> Json.fromDoubleOrNull(entry.tailSilence),
          "effectiveTailSilenceFrames" -> Json.fromInt(math.max(0, math.ceil(entry.tailSilence * fps).toInt))
        )
        startframe += durationframes
        json
    }
    val effectsjson = effects.map { expansion =>
      Json.obj(
        "role" -> Json.fromString(expansion.role.key),
        "profile" -> Json.fromString(expansion.profile),
        "primitives" -> Json.fromValues(expansion.primitives.map { primitive =>
          Json.obj(
            "name" -> Json.fromString(primitive.name),
            "parameters" -> Json.obj(primitive.parameters.map { case (key, value) => key -> Json.fromString(value) }: _*)
          )
        })
      )
    }
    val assetsjson = assets.map { case (asset, publicpath) =>
      Json.obj(
        "role" -> Json.fromString(asset.role.key),
        "path" -> publicpath.map(Json.fromString).getOrElse(Json.Null),
        "kind" -> Json.fromString(asset.kind),
        "required" -> Json.fromBoolean(asset.required),
        "license" -> Json.fromString(asset.license),
        "provenance" -> Json.fromString(asset.provenance),
        "tags" -> Json.fromValues(asset.tags.map(Json.fromString)),
        "credits" -> Json.fromValues(asset.credits.map(Json.fromString)),
        "creditObligation" -> asset.creditObligation.map(Json.fromString).getOrElse(Json.Null),
        "status" -> Json.fromString(asset.status)
      )
    }
    Json.obj(
      "partId" -> Json.fromString(part.id),
      "title" -> Json.fromString(plan.project.title.orElse(script.title).getOrElse(part.id)),
      "outputPath" -> Json.fromString(_project_relative(plan.projectRoot, part.outputPath)),
      "encodingPolicy" -> Json.fromString(encoding.policy.name),
      "qualityProfile" -> Json.fromString(encoding.effectiveQualityProfile.name),
      "audioBitrate" -> Json.fromString(encoding.audioBitrate),
      "encodingOrigins" -> CozyVideoQuality.encodingJson(encoding).hcursor.downField("origins").focus.get,
      "fps" -> Json.fromInt(fps),
      "width" -> Json.fromInt(width),
      "height" -> Json.fromInt(height),
      "crf" -> Json.fromInt(crf),
      "x264Preset" -> x264preset.map(Json.fromString).getOrElse(Json.Null),
      "durationSeconds" -> Json.fromDoubleOrNull(totalframes.toDouble / fps),
      "scenes" -> Json.fromValues(scenes),
      "characters" -> dialogueassets.characters,
      "sections" -> Json.fromValues(script.sections),
      "rendererTemplate" -> template.map(Json.fromString).getOrElse(Json.Null),
      "rendererTemplateResources" -> templateresources.map { resources =>
        Json.fromValues(resources.map { name =>
          Json.obj("path" -> Json.fromString(name))
        })
      }.getOrElse(Json.arr()),
      "recordingPath" -> recording.map(Json.fromString).getOrElse(Json.Null),
      "effectProfile" -> Json.fromString(effectprofile),
      "visualEffects" -> Json.fromValues(effectsjson),
      "assets" -> Json.fromValues(assetsjson),
      "credits" -> CozyVideoCredits.toRendererProps(plan.credits),
      "timing" -> Json.obj(
        "openingFrames" -> Json.fromInt(openingframes),
        "contentFrames" -> Json.fromInt(contentframes),
        "sectionStartFrame" -> Json.fromInt(openingframes),
        "sectionStartFrames" -> Json.fromInt(sectionframes),
        "summaryStartFrame" -> Json.fromInt(summarystartframe),
        "summaryFrames" -> Json.fromInt(summaryframes),
        "creditPageStartFrame" -> Json.fromInt(openingframes + contentframes + summarystandaloneframes),
        "creditPageHoldFrames" -> Json.fromInt(creditframes),
        "finalPageStartFrame" -> Json.fromInt(openingframes + contentframes + summarystandaloneframes + creditframes),
        "finalPageHoldFrames" -> Json.fromInt(finalframes),
        "totalFrames" -> Json.fromInt(totalframes)
      )
    )
  }

  private[video] val _character_dialogue_template_id = "cozy-character-dialogue-v1"
  private[video] val _character_web_demo_template_id = "cozy-character-web-demo-v1"

  private[video] def _effective_render_duration(entry: VideoAudioManifestEntry): Double =
    math.max(entry.targetDuration, entry.leadSilence + entry.audioDuration + entry.tailSilence)

  private[video] def _effect_parameter_double(
    effects: Vector[CozyVideoEffects.Expansion],
    role: CozyVideoEffects.Role,
    primitive: String,
    parameter: String
  ): Option[Double] =
    effects.find(_.role == role).toVector.flatMap(_.primitives).
      find(_.name == primitive).
      flatMap(_.parameters.find(_._1 == parameter).map(_._2)).
      flatMap(x => Try(x.toDouble).toOption)

  private[video] def _remotion_props_ts(json: Json): String =
    s"""export const cozyVideoProps = ${json.spaces2};
       |""".stripMargin

  private[video] def _simple_java2d_props_json(plan: VideoPlan, part: VideoPartPlan, script: VideoScript, audio: VideoSimpleJava2dInput, workdir: Path): Json = {
    val firstscene = script.expandedScenes.headOption
    val text = firstscene.flatMap(scene => scene.narration.orElse(scene.line).orElse(scene.caption).orElse(scene.id)).getOrElse(part.id)
    Json.obj(
      "partId" -> Json.fromString(part.id),
      "title" -> Json.fromString(script.title.orElse(plan.project.title).getOrElse(part.id)),
      "text" -> Json.fromString(text),
      "sceneCount" -> Json.fromInt(script.expandedScenes.size),
      "estimatedDuration" -> Json.fromDoubleOrNull(script.estimatedDuration),
      "qualityProfile" -> Json.fromString(plan.encoding.effectiveQualityProfile.name),
      "audioBitrate" -> Json.fromString(plan.encoding.audioBitrate),
      "encodingOrigins" -> CozyVideoQuality.encodingJson(plan.encoding).hcursor.downField("origins").focus.get,
      "width" -> Json.fromInt(1280),
      "height" -> Json.fromInt(720),
      "framePath" -> Json.fromString(workdir.resolve("frame.png").toString),
      "audioCombinedPath" -> Json.fromString(audio.combinedFile.toString),
      "outputPath" -> Json.fromString(part.outputPath.toString)
    )
  }

  private[video] val _simple_java2d_render_frame_py: String =
    """import json
      |from pathlib import Path
      |from PIL import Image, ImageDraw, ImageFont
      |
      |work_dir = Path(__file__).resolve().parent
      |props = json.loads((work_dir / "props.json").read_text(encoding="utf-8"))
      |width = int(props.get("width", 1280))
      |height = int(props.get("height", 720))
      |image = Image.new("RGB", (width, height), "#101820")
      |draw = ImageDraw.Draw(image)
      |
      |def font(size):
      |    for path in [
      |        "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
      |        "/usr/share/fonts/opentype/ipafont-gothic/ipag.ttf",
      |        "/System/Library/Fonts/ヒラギノ角ゴシック W3.ttc",
      |    ]:
      |        try:
      |            return ImageFont.truetype(path, size=size)
      |        except Exception:
      |            pass
      |    return ImageFont.load_default()
      |
      |title_font = font(54)
      |body_font = font(42)
      |meta_font = font(24)
      |draw.rectangle((0, 0, width, height), fill="#101820")
      |draw.text((72, 72), str(props.get("title", "")), fill="#f4efe6", font=title_font)
      |draw.text((72, 170), str(props.get("text", "")), fill="#f4efe6", font=body_font)
      |meta = f"part={props.get('partId', '')} scenes={props.get('sceneCount', 0)} duration={float(props.get('estimatedDuration', 0.0)):.2f}s"
      |draw.text((72, height - 88), meta, fill="#c8d0d6", font=meta_font)
      |image.save(str(work_dir / "frame.png"))
      |""".stripMargin

  private[video] def _project_relative(projectroot: Path, path: Path): String = {
    val root = projectroot.toAbsolutePath.normalize()
    val normalized = path.toAbsolutePath.normalize()
    if (normalized.startsWith(root))
      root.relativize(normalized).toString
    else
      normalized.toString
  }

  private[video] def _file_segment_id(value: String, label: String): String = {
    val normalized = value.trim
    if (normalized.isEmpty || normalized == "." || normalized == ".." || normalized.contains("/") || normalized.contains("\\") || normalized.indexOf(0.toChar) >= 0)
      RAISE.invalidArgumentFault(s"Invalid $label for file name: $value")
    normalized
  }

  private[video] val _remotion_package_json: String =
    """{
      |  "type": "module",
      |  "private": true,
      |  "scripts": {
      |    "render": "node src/render.mjs"
      |  }
      |}
      |""".stripMargin

  private[video] val _remotion_render_mjs: String =
    """import dns from 'node:dns';
      |import fs from 'node:fs';
      |import path from 'node:path';
      |import {createRequire} from 'node:module';
      |import {fileURLToPath} from 'node:url';
      |
      |const scriptDir = path.dirname(fileURLToPath(import.meta.url));
      |const workDir = path.resolve(scriptDir, '..');
      |const projectRoot = process.env.COZY_PROJECT_ROOT || process.cwd();
      |const propsPath = path.join(workDir, 'props.json');
      |const props = JSON.parse(fs.readFileSync(propsPath, 'utf8'));
      |const hasProfile = Object.hasOwn(props, 'qualityProfile');
      |if (hasProfile && !['lightweight', 'standard', 'quality', 'high-quality'].includes(props.qualityProfile)) {
      |  throw new Error('Invalid qualityProfile in encoding props');
      |}
      |const normalizeAudioBitrate = (value) => {
      |  if (typeof value === 'number') {
      |    if (!Number.isSafeInteger(value) || value <= 0) throw new Error('audioBitrate must be a positive safe integer');
      |    return String(value);
      |  }
      |  if (typeof value !== 'string') throw new Error('audioBitrate must be a positive integer or bitrate string');
      |  const trimmed = value.trim();
      |  const match = /^([1-9][0-9]*)([kM]?)$/.exec(trimmed);
      |  if (!match) throw new Error('Invalid audioBitrate in encoding props');
      |  const scaled = BigInt(match[1]) * (match[2] === 'k' ? 1000n : match[2] === 'M' ? 1000000n : 1n);
      |  if (scaled <= 0n || scaled > 9223372036854775807n) throw new Error('audioBitrate must fit a positive signed 64-bit value');
      |  return trimmed;
      |};
      |if (hasProfile && !Object.hasOwn(props, 'audioBitrate')) throw new Error('Canonical encoding props require audioBitrate');
      |const audioBitrate = Object.hasOwn(props, 'audioBitrate') ? normalizeAudioBitrate(props.audioBitrate) : '320k';
      |const entry = path.join(scriptDir, 'Root.tsx');
      |const publicDir = path.join(workDir, 'public');
      |const output = path.resolve(projectRoot, props.outputPath);
      |const localRequire = createRequire(import.meta.url);
      |const moduleRoots = [workDir, ...(process.env.NODE_PATH || '').split(path.delimiter).filter(Boolean), '/usr/local/lib/node_modules', '/usr/lib/node_modules'];
      |const cliRequire = createRequire(localRequire.resolve('@remotion/cli/package.json', {paths: moduleRoots}));
      |const {bundle} = cliRequire('@remotion/bundler');
      |const {selectComposition, renderFrames, renderMedia} = cliRequire('@remotion/renderer');
      |dns.setDefaultResultOrder('ipv4first');
      |const frames = props.timing?.totalFrames;
      |const fps = props.fps;
      |if (!Number.isSafeInteger(frames) || frames <= 0 || !Number.isSafeInteger(fps) || fps <= 0) {
      |  throw new Error('Cozy PCM mux requires positive safe integer timing.totalFrames and fps; rerender with valid composition timing');
      |}
      |const samples = BigInt(frames) * 48000n / BigInt(fps);
      |if (samples <= 0n) throw new Error('Cozy PCM mux composition has no 48k presentation samples');
      |const ffmpegOverride = ({type, args}) => {
      |  if (type !== 'pre-stitcher' && type !== 'stitcher') return args;
      |  const result = [...args];
      |  if (type === 'pre-stitcher') {
      |    result.splice(result.length - 1, 0, '-f', 'mp4');
      |    return result;
      |  }
      |  const audio = result.indexOf('-c:a');
      |  if (audio !== -1) {
      |    if (result[audio + 1] !== 'copy' || result.lastIndexOf('-c:a') !== audio) {
      |      throw new Error('Cozy PCM mux expected one stitcher -c:a copy pair; rerender using the configured PCM audio codec');
      |    }
      |    result.splice(audio, 2, '-c:a', 'libfdk_aac', '-b:a', audioBitrate, '-cutoff', '18000', '-ar', '48000', '-ac', '2');
      |    result.splice(result.length - 1, 0, '-af', `apad,atrim=end_sample=${samples},asetpts=PTS-STARTPTS`);
      |  }
      |  result.splice(result.length - 1, 0, '-movie_timescale', '48000', '-f', 'mp4');
      |  return result;
      |};
      |const serveUrl = await bundle({
      |  entryPoint: entry,
      |  publicDir,
      |  webpackOverride: (config) => ({...config, resolve: {...config.resolve,
      |    modules: [...(config.resolve?.modules || []), ...(cliRequire.resolve.paths('remotion') || [])]}})
      |});
      |const composition = await selectComposition({serveUrl, id: 'CozyVideo', inputProps: props});
      |if (composition.durationInFrames !== frames || composition.fps !== fps || composition.width !== props.width || composition.height !== props.height) {
      |  throw new Error('Selected Cozy composition differs from the planned full timing/dimensions');
      |}
      |if (process.argv.includes('--frame-audio')) {
      |  const frameDir = fs.mkdtempSync(path.join(workDir, 'frames-'));
      |  const rendered = await renderFrames({composition, serveUrl, inputProps: props, outputDir: frameDir,
      |    imageFormat: 'png', imageSequencePattern: 'frame-[frame].png', frameRange: [0, frames - 1],
      |    onFrameUpdate: (renderedFrames) => console.log(`Rendered ${renderedFrames}/${frames}`)});
      |  if (rendered.frameCount !== frames) throw new Error('Cozy renderFrames returned an unexpected frame count');
      |  const audioPath = path.join(workDir, 'composition.wav');
      |  await renderMedia({composition, serveUrl, inputProps: props, codec: 'wav', audioCodec: 'pcm-16',
      |    imageFormat: 'none', enforceAudioTrack: true, outputLocation: audioPath});
      |  const ordered = fs.readdirSync(frameDir).filter((name) => /^frame-\d+\.png$/.test(name))
      |    .sort((a, b) => Number(a.match(/\d+/)[0]) - Number(b.match(/\d+/)[0]));
      |  if (ordered.length !== frames || ordered.some((name, index) => Number(name.match(/\d+/)[0]) !== index)) {
      |    throw new Error('Cozy renderFrames did not produce the complete ordered composition');
      |  }
      |  fs.writeFileSync(path.join(workDir, 'frame-audio.json'), JSON.stringify({schema: 'cozy.video.frame-audio.v1',
      |    width: composition.width, height: composition.height, fps, frameCount: frames,
      |    frames: ordered.map((name) => `${path.basename(frameDir)}/${name}`), audioPath: 'composition.wav'}, null, 2));
      |} else {
      |const result = await renderMedia({
      |  composition,
      |  serveUrl,
      |  codec: 'h264',
      |  audioCodec: 'pcm-16',
      |  inputProps: props,
      |  crf: props.crf,
      |  ...(props.x264Preset ? {x264Preset: props.x264Preset} : {}),
      |  outputLocation: null,
      |  ffmpegOverride,
      |  onProgress: ({renderedFrames, encodedFrames}) => console.log(`Rendered ${renderedFrames}/${frames}, encoded ${encodedFrames}/${frames}`)
      |});
      |if (!Buffer.isBuffer(result.buffer)) throw new Error('Cozy Remotion render did not return an encoded media buffer');
      |fs.mkdirSync(path.dirname(output), {recursive: true});
      |fs.writeFileSync(output, result.buffer);
      |}
      |""".stripMargin

  private[video] val _remotion_scene_card_layout_js: String =
    """const _scene_card_layout = ({width = 1280, height = 720} = {}) => {
      |  const scale = Math.min(1, width / 1280, height / 720);
      |  return {
      |    padding: 80 * scale,
      |    fontSize: 56 * scale,
      |    labelFontSize: 24 * scale,
      |    labelBottom: 48 * scale,
      |    labelRight: 64 * scale
      |  };
      |};
      |""".stripMargin

  private[video] val _remotion_root_tsx: String =
    """import React from 'react';
      |import {AbsoluteFill, Audio, Composition, Img, Sequence, interpolate, registerRoot, spring, staticFile, useCurrentFrame, useVideoConfig} from 'remotion';
      |import {cozyVideoProps} from './props';
      |
      |""".stripMargin + _remotion_scene_card_layout_js + """
      |type Scene = {
      |  id: string;
      |  text: string;
      |  audioPath: string;
      |  duration: number;
      |  durationFrames?: number;
      |};
      |
      |type Primitive = {
      |  name: 'title-card' | 'subtle-motion' | 'flow-line' | 'underline-sweep' | 'summary-layout' | 'fade-rise' | 'spring-pop' | 'end-card' | 'hold';
      |  parameters: Record<string, string>;
      |};
      |
      |type VisualEffect = {
      |  role: 'opening' | 'section-start' | 'summary' | 'final-page';
      |  profile: string;
      |  primitives: Primitive[];
      |};
      |
      |type Asset = {
      |  role: 'opening' | 'section-start' | 'summary' | 'final-page';
      |  path: string | null;
      |  kind: string;
      |  required: boolean;
      |  license: string;
      |  provenance: string;
      |  status: string;
      |};
      |
      |type Timing = {
      |  openingFrames: number;
      |  contentFrames: number;
      |  sectionStartFrame: number;
      |  sectionStartFrames: number;
      |  summaryStartFrame: number;
      |  summaryFrames: number;
      |  creditPageStartFrame: number;
      |  creditPageHoldFrames: number;
      |  finalPageStartFrame: number;
      |  finalPageHoldFrames: number;
      |  totalFrames: number;
      |};
      |
      |type CreditItem = {
      |  id: string;
      |  category: string;
      |  label: string;
      |  creator: string | null;
      |};
      |
      |type Credits = {
      |  title: string;
      |  items: CreditItem[];
      |};
      |
      |type Props = {
      |  partId: string;
      |  title: string;
      |  fps: number;
      |  width: number;
      |  height: number;
      |  durationSeconds: number;
      |  scenes: Scene[];
      |  visualEffects: VisualEffect[];
      |  assets: Asset[];
      |  credits: Credits;
      |  timing: Timing;
      |};
      |
      |const CreditPage: React.FC<{credits: Credits}> = ({credits}) => (
      |  <AbsoluteFill style={{backgroundColor: '#101820', color: '#f4efe6', fontFamily: 'Noto Sans CJK JP, sans-serif', padding: '64px 84px'}}>
      |    <div style={{fontSize: 48, fontWeight: 800, marginBottom: 30}}>{credits.title}</div>
      |    <div style={{display: 'flex', flexDirection: 'column', gap: 18}}>
      |      {credits.items.map((item) => (
      |        <div key={item.id} style={{fontSize: 30, lineHeight: 1.3}}>
      |          <span style={{fontWeight: 700}}>{item.label}</span>
      |          {item.creator ? <span style={{opacity: 0.78}}> — {item.creator}</span> : null}
      |        </div>
      |      ))}
      |    </div>
      |  </AbsoluteFill>
      |);
      |
      |const roleEffect = (effects: VisualEffect[], role: VisualEffect['role']) => effects.find((effect) => effect.role === role);
      |const roleAsset = (assets: Asset[], role: Asset['role']) => assets.find((asset) => asset.role === role);
      |const primitive = (effect: VisualEffect | undefined, name: Primitive['name']) => effect?.primitives.find((item) => item.name === name);
      |
      |const AssetFrame: React.FC<{asset?: Asset; opacity?: number}> = ({asset, opacity = 1}) =>
      |  asset?.path ? <Img src={staticFile(asset.path)} style={{position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'cover', opacity}} /> : null;
      |
      |const SceneCard: React.FC<{scene: Scene}> = ({scene}) => {
      |  const {width, height} = useVideoConfig();
      |  const layout = _scene_card_layout({width, height});
      |  return (
      |  <AbsoluteFill style={{backgroundColor: '#101820', color: '#f4efe6', fontFamily: 'Noto Sans CJK JP, sans-serif', alignItems: 'center', justifyContent: 'center', padding: layout.padding}}>
      |    <div style={{fontSize: layout.fontSize, lineHeight: 1.25, textAlign: 'center'}}>{scene.text || scene.id}</div>
      |    <div style={{position: 'absolute', bottom: layout.labelBottom, right: layout.labelRight, fontSize: layout.labelFontSize, opacity: 0.7}}>{scene.id}</div>
      |    <Audio src={staticFile(scene.audioPath)} />
      |  </AbsoluteFill>
      |  );
      |};
      |
      |const Opening: React.FC<{title: string; effect: VisualEffect; asset?: Asset; durationInFrames: number}> = ({title, effect, asset, durationInFrames}) => {
      |  const frame = useCurrentFrame();
      |  const titleCard = primitive(effect, 'title-card');
      |  const motion = primitive(effect, 'subtle-motion');
      |  const hold = primitive(effect, 'hold');
      |  const configuredScale = Number.parseFloat(motion?.parameters.scale || '1');
      |  const scale = motion ? interpolate(frame, [0, Math.max(1, durationInFrames - 1)], [1, Number.isFinite(configuredScale) ? configuredScale : 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'}) : 1;
      |  return titleCard && hold ? (
      |    <AbsoluteFill style={{backgroundColor: '#101820', color: '#f4efe6', alignItems: 'center', justifyContent: 'center', overflow: 'hidden'}}>
      |      <div style={{position: 'absolute', inset: -16, transform: `scale(${scale})`}}><AssetFrame asset={asset} opacity={1} /></div>
      |    </AbsoluteFill>
      |  ) : null;
      |};
      |
      |const SectionStart: React.FC<{effect: VisualEffect; asset?: Asset; durationInFrames: number}> = ({effect, asset, durationInFrames}) => {
      |  const frame = useCurrentFrame();
      |  const flow = primitive(effect, 'flow-line');
      |  const underline = primitive(effect, 'underline-sweep');
      |  const progress = interpolate(frame, [0, Math.max(1, durationInFrames - 1)], [0, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
      |  const direction = flow?.parameters.direction === 'right-to-left' ? -1 : 1;
      |  return (
      |    <AbsoluteFill style={{backgroundColor: 'transparent', overflow: 'hidden', pointerEvents: 'none'}}>
      |      {flow ? <div style={{position: 'absolute', top: '45%', left: direction > 0 ? 0 : undefined, right: direction < 0 ? 0 : undefined, width: `${Math.round(progress * 100)}%`, height: 14, background: '#2f6f68'}} /> : null}
      |      {underline ? <div style={{position: 'absolute', left: '18%', bottom: '29%', width: `${Math.round(progress * 64)}%`, height: 7, background: '#d89b45'}} /> : null}
      |    </AbsoluteFill>
      |  );
      |};
      |
      |const Summary: React.FC<{effect: VisualEffect; asset?: Asset}> = ({effect, asset}) => {
      |  const frame = useCurrentFrame();
      |  const {fps} = useVideoConfig();
      |  const layout = primitive(effect, 'summary-layout');
      |  const fade = primitive(effect, 'fade-rise');
      |  const pop = primitive(effect, 'spring-pop');
      |  const rise = fade?.parameters.target === 'overview' ? interpolate(frame, [0, fps * 0.5], [36, 0], {extrapolateRight: 'clamp'}) : 0;
      |  const scale = pop?.parameters.target === 'conclusion' ? spring({frame, fps, config: {damping: 14, stiffness: 120}}) : 1;
      |  return (
      |    <AbsoluteFill style={{backgroundColor: '#e7f1ee', color: '#173f3b', fontFamily: 'Noto Sans CJK JP, sans-serif', padding: 72}}>
      |      <AssetFrame asset={asset} opacity={1} />
      |    </AbsoluteFill>
      |  );
      |};
      |
      |const FinalPage: React.FC<{effect: VisualEffect; asset?: Asset; transitionFrames: number}> = ({effect, asset, transitionFrames}) => {
      |  const frame = useCurrentFrame();
      |  const card = primitive(effect, 'end-card');
      |  const fade = primitive(effect, 'fade-rise');
      |  const hold = primitive(effect, 'hold');
      |  const opacity = fade?.parameters.target === 'end-card' && transitionFrames > 0 ? interpolate(frame, [0, transitionFrames], [0, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'}) : 1;
      |  return card && hold ? (
      |    <AbsoluteFill style={{backgroundColor: '#101820', color: '#f4efe6', alignItems: 'center', justifyContent: 'center', opacity}}>
      |      <AssetFrame asset={asset} opacity={1} />
      |    </AbsoluteFill>
      |  ) : null;
      |};
      |
      |export const CozyVideo: React.FC<Props> = ({title, scenes, fps, visualEffects, assets, credits, timing}) => {
      |  let start = timing.openingFrames;
      |  const opening = roleEffect(visualEffects, 'opening');
      |  const section = roleEffect(visualEffects, 'section-start');
      |  const summary = roleEffect(visualEffects, 'summary');
      |  const finalPage = roleEffect(visualEffects, 'final-page');
      |  const finalPageTransitionFrames = finalPage && primitive(finalPage, 'fade-rise')?.parameters.target === 'end-card' ? Math.min(Math.max(0, timing.finalPageStartFrame), Math.ceil(fps * 0.45)) : 0;
      |  return (
      |    <AbsoluteFill>
      |      {opening && timing.openingFrames > 0 ? <Sequence from={0} durationInFrames={timing.openingFrames}><Opening title={title} effect={opening} asset={roleAsset(assets, 'opening')} durationInFrames={timing.openingFrames} /></Sequence> : null}
      |      {scenes.map((scene) => {
      |        const duration = Math.max(1, scene.durationFrames ?? Math.ceil((scene.duration || 1) * fps));
      |        const sequence = <Sequence key={scene.id} from={start} durationInFrames={duration}><SceneCard scene={scene} /></Sequence>;
      |        start += duration;
      |        return sequence;
      |      })}
      |      {section && timing.sectionStartFrames > 0 ? <Sequence from={timing.sectionStartFrame} durationInFrames={timing.sectionStartFrames}><SectionStart effect={section} asset={roleAsset(assets, 'section-start')} durationInFrames={timing.sectionStartFrames} /></Sequence> : null}
      |      {summary && timing.summaryFrames > 0 ? <Sequence from={timing.summaryStartFrame} durationInFrames={timing.summaryFrames}><Summary effect={summary} asset={roleAsset(assets, 'summary')} /></Sequence> : null}
      |      {timing.creditPageHoldFrames > 0 && credits.items.length > 0 ? <Sequence from={timing.creditPageStartFrame} durationInFrames={timing.creditPageHoldFrames}><CreditPage credits={credits} /></Sequence> : null}
      |      {finalPage && timing.finalPageHoldFrames > 0 ? <Sequence from={timing.finalPageStartFrame - finalPageTransitionFrames} durationInFrames={timing.finalPageHoldFrames + finalPageTransitionFrames}><FinalPage effect={finalPage} asset={roleAsset(assets, 'final-page')} transitionFrames={finalPageTransitionFrames} /></Sequence> : null}
      |    </AbsoluteFill>
      |  );
      |};
      |
      |export const RemotionRoot: React.FC = () => (
      |  <Composition
      |    id="CozyVideo"
      |    component={CozyVideo}
      |    durationInFrames={Math.max(1, cozyVideoProps.timing?.totalFrames || Math.round((cozyVideoProps.durationSeconds || 1) * cozyVideoProps.fps))}
      |    fps={cozyVideoProps.fps}
      |    width={cozyVideoProps.width}
      |    height={cozyVideoProps.height}
      |    defaultProps={cozyVideoProps}
      |  />
      |);
      |
      |registerRoot(RemotionRoot);
      |
      |export default RemotionRoot;
      |""".stripMargin

  private[video] val _remotion_character_dialogue_root_tsx: String =
    """import React from 'react';
      |import {AbsoluteFill, Audio, Composition, Img, Sequence, interpolate, registerRoot, staticFile, useCurrentFrame} from 'remotion';
      |import {DialogueVideo} from './DialogueVideo.jsx';
      |import {cozyVideoProps} from './props';
      |
      |const assetFor = (role) => cozyVideoProps.assets?.find((asset) => asset.role === role);
      |const AssetSurface = ({role}) => {
      |  const asset = assetFor(role);
      |  return asset?.path ? <Img src={staticFile(asset.path)} style={{position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'contain', opacity: 1}} /> : null;
      |};
      |const OpeningSurface = () => {
      |  const frame = useCurrentFrame();
      |  const opening = cozyVideoProps.visualEffects?.find((effect) => effect.role === 'opening');
      |  const scaleValue = Number(opening?.primitives?.find((primitive) => primitive.name === 'subtle-motion')?.parameters?.scale || 1);
      |  const duration = Math.max(1, cozyVideoProps.timing?.openingFrames || 1);
      |  const scale = interpolate(frame, [0, duration - 1], [1, Number.isFinite(scaleValue) ? scaleValue : 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
      |  return <div style={{position: 'absolute', inset: -16, transform: `scale(${scale})`}}><AssetSurface role="opening" /></div>;
      |};
      |const CreditPage = ({credits}) => <AbsoluteFill style={{backgroundColor: '#101820', color: '#f4efe6', fontFamily: 'Noto Sans CJK JP, sans-serif', padding: '64px 84px'}}><div style={{fontSize: 48, fontWeight: 800, marginBottom: 30}}>{credits.title}</div><div style={{display: 'flex', flexDirection: 'column', gap: 18}}>{credits.items.map((item) => <div key={item.id} style={{fontSize: 30, lineHeight: 1.3}}><span style={{fontWeight: 700}}>{item.label}</span>{item.creator ? <span style={{opacity: 0.78}}> — {item.creator}</span> : null}</div>)}</div></AbsoluteFill>;
      |
      |const CharacterDialogueVideo = () => {
      |  const props = cozyVideoProps;
      |  const contentStart = props.timing?.openingFrames || 0;
      |  const contentFrames = props.timing?.contentFrames || 1;
      |  return <AbsoluteFill>
      |    {contentStart > 0 ? <Sequence from={0} durationInFrames={contentStart}><OpeningSurface /></Sequence> : null}
      |    <Sequence from={contentStart} durationInFrames={contentFrames}>
      |      <DialogueVideo characters={Object.fromEntries(Object.entries(props.characters || {}).map(([id, character]) => [id, Object.fromEntries(Object.entries(character).map(([key, value]) => (key === 'asset' || key === 'mouthClosedAsset' || key === 'mouthOpenAsset' || key.endsWith('Asset')) && typeof value === 'string' ? [key, staticFile(value)] : [key, value]))]))} sections={props.sections || []} scenes={(props.scenes || []).map((scene) => ({...scene, visual: scene.visual?.image ? {...scene.visual, image: staticFile(scene.visual.image)} : scene.visual}))} fps={props.fps} effectProfile={props.effectProfile} />
      |      {(props.scenes || []).map((scene) => <Sequence key={`audio-${scene.id}`} from={Math.max(0, scene.startFrame + scene.leadInFrames)} durationInFrames={Math.max(1, scene.audioDurationFrames || scene.durationFrames - scene.leadInFrames)}><Audio src={staticFile(scene.audioPath)} /></Sequence>)}
      |    </Sequence>
      |    {props.timing?.summaryFrames > 0 ? <Sequence from={props.timing.summaryStartFrame} durationInFrames={props.timing.summaryFrames}><AssetSurface role="summary" /></Sequence> : null}
      |    {props.timing?.creditPageHoldFrames > 0 && props.credits?.items?.length > 0 ? <Sequence from={props.timing.creditPageStartFrame} durationInFrames={props.timing.creditPageHoldFrames}><CreditPage credits={props.credits} /></Sequence> : null}
      |    {props.timing?.finalPageHoldFrames > 0 ? <Sequence from={props.timing.finalPageStartFrame} durationInFrames={props.timing.finalPageHoldFrames}><AssetSurface role="final-page" /></Sequence> : null}
      |  </AbsoluteFill>;
      |};
      |
      |export const RemotionRoot: React.FC = () => <Composition id="CozyVideo" component={CharacterDialogueVideo} durationInFrames={Math.max(1, cozyVideoProps.timing?.totalFrames || 1)} fps={cozyVideoProps.fps} width={cozyVideoProps.width} height={cozyVideoProps.height} defaultProps={cozyVideoProps} />;
      |registerRoot(RemotionRoot);
      |export default RemotionRoot;
      |""".stripMargin

  private[video] val _remotion_character_web_demo_root_tsx: String =
    """import React from 'react';
      |import {AbsoluteFill, Audio, Composition, Img, Sequence, Video, registerRoot, staticFile, useCurrentFrame} from 'remotion';
      |import {cozyVideoProps} from './props';
      |
      |const roleAsset = (role) => cozyVideoProps.assets?.find((asset) => asset.role === role);
      |const AssetSurface = ({role}) => {
      |  const asset = roleAsset(role);
      |  return asset?.path ? <Img src={staticFile(asset.path)} style={{position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'contain'}} /> : null;
      |};
      |const CreditPage = ({credits}) => <AbsoluteFill style={{backgroundColor: '#101820', color: '#f4efe6', fontFamily: 'Noto Sans CJK JP, sans-serif', padding: '64px 84px'}}><div style={{fontSize: 48, fontWeight: 800, marginBottom: 30}}>{credits.title}</div><div style={{display: 'flex', flexDirection: 'column', gap: 18}}>{credits.items.map((item) => <div key={item.id} style={{fontSize: 30, lineHeight: 1.3}}><span style={{fontWeight: 700}}>{item.label}</span>{item.creator ? <span style={{opacity: 0.78}}> — {item.creator}</span> : null}</div>)}</div></AbsoluteFill>;
      |const numberOr = (value, fallback) => Number.isFinite(Number(value)) ? Number(value) : fallback;
      |const DemoContent = () => {
      |  const frame = useCurrentFrame();
      |  const props = cozyVideoProps;
      |  const scene = (props.scenes || []).find((candidate) => frame >= candidate.startFrame && frame < candidate.startFrame + candidate.durationFrames) || props.scenes?.[0];
      |  const localFrame = Math.max(0, frame - (scene?.startFrame || 0));
      |  const character = scene?.speaker ? props.characters?.[scene.speaker] : null;
      |  const audioDurationFrames = Math.max(1, Number(scene?.audioDurationFrames || Math.ceil(Number(scene?.audioDuration || 0) * Number(props.fps || 1)) || ((scene?.durationFrames || 1) - (scene?.leadInFrames || 0))));
      |  const mouthOpen = !scene?.silent && character?.mouthOpenAsset && character?.mouthClosedAsset && localFrame >= (scene?.leadInFrames || 0) && localFrame < (scene?.leadInFrames || 0) + audioDurationFrames && Math.floor(localFrame / 4) % 2 === 0;
      |  const source = mouthOpen ? character.mouthOpenAsset : (character?.mouthClosedAsset || character?.asset);
      |  const side = character?.side === 'right' ? 'right' : 'left';
      |  const width = numberOr(character?.width, 252);
      |  const bottom = numberOr(character?.bottom, 150);
      |  const inset = numberOr(character?.inset, 28);
      |  const maxHeight = numberOr(character?.maxHeight, 430);
      |  return <AbsoluteFill style={{backgroundColor: '#101820', overflow: 'hidden'}}>
      |    {props.recordingPath ? <Video src={staticFile(props.recordingPath)} muted loop style={{position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'contain'}} /> : null}
      |    {source ? <Img src={staticFile(source)} style={{position: 'absolute', bottom, [side]: inset, width, maxHeight, objectFit: 'contain', transform: character?.flipX ? 'scaleX(-1)' : undefined, filter: character?.shadow?.color ? `drop-shadow(${character.shadow.x || 12}px ${character.shadow.y || 18}px ${character.shadow.blur || 0}px ${character.shadow.color})` : undefined}} /> : null}
      |    {scene?.caption || scene?.line ? <div style={{position: 'absolute', left: 72, right: 72, bottom: 18, minHeight: 110, display: 'flex', alignItems: 'center', background: 'rgba(16,18,22,.94)', color: '#fff', borderRadius: 10, padding: '18px 30px 18px 42px', boxSizing: 'border-box', fontSize: 30, fontWeight: 800, lineHeight: 1.34}}>{scene.caption || scene.line}</div> : null}
      |    {(props.scenes || []).map((candidate) => <Sequence key={`audio-${candidate.id}`} from={Math.max(0, candidate.startFrame + candidate.leadInFrames)} durationInFrames={Math.max(1, candidate.audioDurationFrames || candidate.durationFrames - candidate.leadInFrames)}><Audio src={staticFile(candidate.audioPath)} /></Sequence>)}
      |  </AbsoluteFill>;
      |};
      |const WebDemoVideo = () => {
      |  const props = cozyVideoProps;
      |  const contentStart = props.timing?.openingFrames || 0;
      |  return <AbsoluteFill>
      |    {contentStart > 0 ? <Sequence from={0} durationInFrames={contentStart}><AssetSurface role="opening" /></Sequence> : null}
      |    <Sequence from={contentStart} durationInFrames={props.timing?.contentFrames || 1}><DemoContent /></Sequence>
      |    {props.timing?.summaryFrames > 0 ? <Sequence from={props.timing.summaryStartFrame} durationInFrames={props.timing.summaryFrames}><AssetSurface role="summary" /></Sequence> : null}
      |    {props.timing?.creditPageHoldFrames > 0 && props.credits?.items?.length > 0 ? <Sequence from={props.timing.creditPageStartFrame} durationInFrames={props.timing.creditPageHoldFrames}><CreditPage credits={props.credits} /></Sequence> : null}
      |    {props.timing?.finalPageHoldFrames > 0 ? <Sequence from={props.timing.finalPageStartFrame} durationInFrames={props.timing.finalPageHoldFrames}><AssetSurface role="final-page" /></Sequence> : null}
      |  </AbsoluteFill>;
      |};
      |export const RemotionRoot: React.FC = () => <Composition id="CozyVideo" component={WebDemoVideo} durationInFrames={Math.max(1, cozyVideoProps.timing?.totalFrames || 1)} fps={cozyVideoProps.fps} width={cozyVideoProps.width} height={cozyVideoProps.height} defaultProps={cozyVideoProps} />;
      |registerRoot(RemotionRoot);
      |export default RemotionRoot;
      |""".stripMargin
}
