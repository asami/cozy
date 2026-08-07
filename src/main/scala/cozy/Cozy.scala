package cozy

import org.goldenport.RAISE
import org.goldenport.cli.{Config => CliConfig, _}
import org.goldenport.value._
import org.goldenport.parser.CommandParser
import org.goldenport.kaleidox.Kaleidox
import org.goldenport.kaleidox.http.HttpHandle
import cozy.archive.CozyArchivePackager
import cozy.bok.CozyBok
import cozy.lint.{CozyBuildLint, CozyCarAbiLint, CozyCarLint, CozyCmlLint, CozyRepositoryLint}
import cozy.media.CozyMedia
import cozy.publication.{CozyPublicationCompiler, CozySampleDistributor, CozyWarehouseIndexer}
import cozy.review.CozyCarReviewProviderCommand
import cozy.runtime.{CozyRuntime, CozySbtBridge}
import cozy.scaffold.CozyScaffold
import cozy.video.{CozyVideo, CozyVideoPublisher}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
import scala.io.{Codec, Source}
import scala.util.control.NonFatal

/*
 * @since   Dec.  4, 2021
 *  version Dec. 19, 2021
 *  version Jan.  1, 2022
 *  version Feb. 28, 2022
 *  version Aug. 20, 2025
 *  version Mar. 17, 2026
 *  version Apr. 29, 2026
 *  version May. 21, 2026
 *  version Jun. 30, 2026
 * @version Aug.  7, 2026
 * @author  ASAMI, Tomoharu
 */
class Cozy(
  val config: Config,
  val environment: Environment,
  services: Services,
  operations: Operations
) {
  private val _engine = Engine.standard(services, operations)

  def execute(args: Array[String]) = _engine.apply(environment, args)

  def run(args: Array[String]) {
    if (_is_help_request(args))
      println(Cozy._help_text)
    else {
      val effectiveargs = _operation_config_args(args.toList).toArray
      val call = _operation_call(effectiveargs)
      if (call.request.arguments.isEmpty || call.request.isInteractive)
        repl(call)
      else if (_is_cli_command(call))
        execute(effectiveargs)
      else
        executeDirect(effectiveargs)
    }
  }

  def repl(call: OperationCall) {
    val kal = interpreter
    kal.repl(call)
  }

  def interpreter = environment.appEnvironment match {
    case m: Context => m.kaleidox
    case _ => createInterpreter()
  }

  def createInterpreter(): Kaleidox =
    _create_interpreter(
      modeler.PredefinedResultCatalog.empty,
      modeler.ComponentStyleCatalog.EMPTY
    )

  private def _create_interpreter(
    predefinedresultcatalog: modeler.PredefinedResultCatalog,
    componentstylecatalog: modeler.ComponentStyleCatalog
  ): Kaleidox = {
    val kconfig = org.goldenport.kaleidox.Config.create(environment).
      setModeler(new modeler.Modeler(predefinedresultcatalog, componentstylecatalog)).
      setPrompt("cozy> ")
    new Kaleidox(kconfig, environment)
  }

  def createHttpHandle(): HttpHandle = {
    val args = Array[String]()
    val kal = interpreter
    val call = _operation_call(args)
    kal.http(call)
  }

  def executeDirect(args: Array[String]): Unit = {
    if (!_execute_version(args) && !_execute_lint(args) && !_execute_car_review_provider(args) && !CozyBok.execute(args.toList) && !CozyMedia.execute(args.toList) && !CozyVideo.execute(args.toList) && !_execute_generation_provenance_validate(args) && !_execute_modeler_scala(args) && !_execute_init(args) && !_execute_car_sbt_project(args) && !_execute_publish_car(args) && !_execute_publish_sar(args) && !_execute_publish_project(args) && !_execute_publish_video(args) && !_execute_distribute_samples(args) && !_execute_index_warehouse(args) && !_execute_sbt_bridge(args) && !_execute_package_archive(args))
      _to_repl_commandline(args) match {
        case Some(s) =>
          val c = _operation_call(Array(s))
          interpreter.execute(c)
        case None =>
          execute(args)
      }
  }

  private def _execute_version(args: Array[String]): Boolean =
    args.toList match {
      case "version" :: Nil =>
        println(s"cozy ${org.simplemodeling.cozy.BuildInfo.version}")
        true
      case "--version" :: Nil =>
        println(s"cozy ${org.simplemodeling.cozy.BuildInfo.version}")
        true
      case _ =>
        false
    }

  private def _execute_lint(args: Array[String]): Boolean =
    _leading_command(args) match {
      case Some(("lint", "build" :: rest)) =>
        val exitcode = CozyBuildLint.execute(rest)
        if (exitcode != 0)
          sys.exit(exitcode)
        true
      case Some(("lint", "cml" :: rest)) =>
        val exitcode = CozyCmlLint.execute(rest)
        if (exitcode != 0)
          sys.exit(exitcode)
        true
      case Some(("lint", "abi" :: rest)) =>
        val exitcode = CozyCarAbiLint.execute(rest)
        if (exitcode != 0)
          sys.exit(exitcode)
        true
      case Some(("lint", "car" :: rest)) =>
        val exitcode = CozyCarLint.execute(rest)
        if (exitcode != 0)
          sys.exit(exitcode)
        true
      case Some(("lint", "repository" :: rest)) =>
        val exitcode = CozyRepositoryLint.execute(rest)
        if (exitcode != 0)
          sys.exit(exitcode)
        true
      case Some(("car", "lint" :: rest)) =>
        val exitcode = CozyCarLint.execute(rest)
        if (exitcode != 0)
          sys.exit(exitcode)
        true
      case Some(("lint", Nil)) =>
        RAISE.invalidArgumentFault("Missing lint target: build, cml, abi, car, or repository")
      case Some(("lint", target :: _)) =>
        RAISE.invalidArgumentFault(s"Unsupported lint target: ${target}")
      case _ =>
        false
    }

  private def _execute_car_review_provider(args: Array[String]): Boolean =
    _leading_command(args) match {
      case Some(("review", "car-descriptor" :: rest)) =>
        CozyCarReviewProviderCommand.describe(rest) match {
          case Right(descriptor) =>
            println(descriptor)
            true
          case Left(code) =>
            RAISE.invalidArgumentFault(s"CAR Review provider descriptor command failed: $code")
        }
      case Some(("review", "car-evidence" :: rest)) =>
        val request = Source.fromInputStream(System.in)(Codec.UTF8).mkString
        CozyCarReviewProviderCommand.execute(rest, request) match {
          case Right(bundle) =>
            println(bundle)
            true
          case Left(code) =>
            RAISE.invalidArgumentFault(s"CAR Review provider command failed: $code")
        }
      case _ =>
        false
    }

  private def _is_cli_command(call: OperationCall): Boolean =
    call.argumentsAsString.headOption.fold(false)(_is_cli_command)

  private def _is_cli_command(name: String): Boolean =
    _engine.commandParser(name) match {
      case _: CommandParser.Found[_] => true
      case _: CommandParser.Candidates[_] => true
      case _: CommandParser.NotFound[_] => false
    }

  private def _is_help_request(args: Array[String]): Boolean =
    args.toList match {
      case "--help" :: Nil => true
      case "-h" :: Nil => true
      case "help" :: Nil => true
      case _ => false
    }

  private def _operation_call(args: Array[String]): OperationCall = {
    val req = spec.Request.empty
    val res = spec.Response()
    val op = spec.Operation("cozy", req, res)
    val request = Request.create(op, args)
    OperationCall(environment, op, request, Response())
  }

  private def _to_repl_commandline(args: Array[String]): Option[String] =
    _leading_command(args).map { case (command, rest) =>
      val normalized = _normalize_repl_args(command, rest)
      val withconfig = _operation_config_args(command, normalized)
      val converted = _convert_args(withconfig)
      (Vector(command) ++ converted).mkString(" ")
    }

  private def _normalize_repl_args(command: String, args: List[String]): List[String] =
    command match {
      case "modeler-scala" | "modeler-scala-value" => _normalize_first_positional_path(args)
      case _ => args
    }

  private def _operation_config_args(command: String, args: List[String]): List[String] =
    command match {
      case "pdf" => CozyOperationConfig._with_pdf_defaults(args)
      case _ => args
    }

  private def _operation_config_args(args: List[String]): List[String] =
    _leading_command(args.toArray) match {
      case Some((command, rest)) =>
        val i = args.indexOf(command)
        args.take(i + 1) ++ _operation_config_args(command, rest)
      case None => args
    }

  private def _normalize_first_positional_path(args: List[String]): List[String] = {
    @annotation.tailrec
    def _go_(xs: List[String], z: Vector[String], done: Boolean): List[String] = xs match {
      case Nil => z.toList
      case x :: xx if x.startsWith("--save=") =>
        _go_(xx, z :+ x, done)
      case x :: y :: yy if x == "--save" =>
        _go_(yy, z :+ x :+ y, done)
      case x :: xx if x.startsWith("-") =>
        _go_(xx, z :+ x, done)
      case x :: xx if !done =>
        _go_(xx, z :+ Cozy._cli_path(x).toString, done = true)
      case x :: xx =>
        _go_(xx, z :+ x, done)
    }
    _go_(args, Vector.empty, done = false)
  }

  private def _execute_car_sbt_project(args: Array[String]): Boolean =
    _leading_command(args) match {
      case Some(("car-sbt-project", rest)) =>
        val save = _save_path(rest).getOrElse {
          RAISE.invalidArgumentFault("Missing --save for car-sbt-project")
        }
        val policy = Cozy.ProjectFilePolicy.create(rest)
        val versions = Cozy.CarDependencyVersions.create(rest)
        val style = Cozy.ProjectLayoutStyle.create(rest)
        val scaffold = Cozy.CarScaffoldConfig.create(rest, save, style)
        val replargs = _without_car_scaffold_args(_without_style_args(_without_project_file_policy_args(rest)))
        val modelargs = _without_save_args(replargs)
        val normalizedmodelargs = _normalize_first_positional_path(modelargs)
        val modelpath = normalizedmodelargs.find(!_.startsWith("-")).map(Paths.get(_))
        val projectsave = _project_save_path(style, save)
        if (normalizedmodelargs.exists(!_.startsWith("-"))) {
          val generatedargs = normalizedmodelargs ++ List("--save", projectsave.toString)
          val repl = (Vector("modeler-scala") ++ _convert_args(generatedargs)).mkString(" ")
          val c = _operation_call(Array(repl))
          interpreter.execute(c)
          _delete_directory(projectsave.resolve("target"))
          if (!modelpath.exists(_.getFileName.toString.endsWith(".dox"))) {
            _delete_directory(projectsave.resolve("src/main/scala"))
          }
        }
        style match {
          case CozyScaffold.ProjectLayoutStyle.CarOnly =>
            _materialize_car_sbt_project(save, policy, versions, scaffold, modelpath)
          case CozyScaffold.ProjectLayoutStyle.CarSar =>
            _materialize_car_sar_sbt_project(save, policy, versions, scaffold, modelpath)
        }
        val init = Cozy.ComponentInitConfig(save, style, scaffold, scaffold.componentName)
        _materialize_car_project_yaml(projectsave, policy, versions, init)
        true
      case _ =>
        false
    }

  private def _execute_modeler_scala(args: Array[String]): Boolean =
    _leading_command(args) match {
      case Some((command @ ("modeler-scala" | "modeler-scala-value"), rest)) =>
        val normalized = _normalize_first_positional_path(rest)
        val validateddescriptor =
          cozy.compatibility.CncfRuntimeDescriptorContract.requireValidInvocation(
            command +: normalized,
            "modeler"
          )
        val catalog = validateddescriptor.
          map(modeler.PredefinedResultCatalog.fromValidatedDescriptor).
          getOrElse(modeler.PredefinedResultCatalog.empty)
        val componentstylecatalog = validateddescriptor.
          map(modeler.ComponentStyleCatalog.fromValidatedDescriptor).
          getOrElse(modeler.ComponentStyleCatalog.EMPTY)
        val sourcesnapshot = _generation_source_snapshot(
          normalized,
          validateddescriptor
        )
        if (validateddescriptor.nonEmpty)
          _save_path(normalized).foreach(
            cozy.modeler.GenerationProvenance.prepareOutput
          )
        sourcesnapshot match {
          case Some(snapshot) =>
            cozy.modeler.GenerationProvenance.withCapturedSource(snapshot) { capturedsource =>
              val capturedargs = _replace_first_positional_path(
                normalized,
                capturedsource
              )
              val repl = (Vector(command) ++ _convert_args(capturedargs)).mkString(" ")
              _create_interpreter(catalog, componentstylecatalog).execute(_operation_call(Array(repl)))
              _write_model_metadata(normalized, Some(capturedsource), componentstylecatalog)
            }
          case None =>
            val repl = (Vector(command) ++ _convert_args(normalized)).mkString(" ")
            _create_interpreter(catalog, componentstylecatalog).execute(_operation_call(Array(repl)))
            _write_model_metadata(normalized, None, componentstylecatalog)
        }
        _write_component_api_descriptor(normalized)
        _write_generation_provenance(
          normalized,
          validateddescriptor,
          sourcesnapshot
        )
        true
      case _ =>
        false
    }

  private def _execute_generation_provenance_validate(args: Array[String]): Boolean =
    _leading_command(args) match {
      case Some(("generation-provenance-validate", rest)) =>
        val normalized = _normalize_first_positional_path(rest)
        val source = normalized.find(!_.startsWith("-")).
          map(Paths.get(_).toAbsolutePath.normalize()).
          getOrElse(RAISE.invalidArgumentFault(
            "Generation provenance validation requires the original CML source"
          ))
        val output = _save_path(normalized).
          map(_.toAbsolutePath.normalize()).
          getOrElse(RAISE.invalidArgumentFault(
            "Generation provenance validation requires --save <generation-output-root>"
          ))
        val sourceidentity = cozy.modeler.GenerationProvenance.requireSourceIdentity(
          _option_value(normalized, "generation-source-identity"),
          "generation-provenance-validate"
        )
        val sourcesha = _required_option_value(
          normalized,
          "generation-source-sha256"
        )
        val targetversion = _required_option_value(normalized, "cncf-version")
        val descriptordigest = _required_option_value(
          normalized,
          "cncf-runtime-descriptor-sha256"
        )
        val cozyversion = _required_option_value(
          normalized,
          "cozy-generator-version"
        )
        val modelversion = _option_value(normalized, "simplemodeling-model-version").
          getOrElse(org.simplemodeling.cozy.BuildInfo.simpleModelingModelVersion)
        val manifest = cozy.modeler.GenerationProvenance.requireValidGeneratedOutput(
          output,
          source,
          cozy.modeler.GenerationProvenance.Inputs(
            cncfTargetVersion = targetversion,
            runtimeDescriptorSha256 = descriptordigest,
            cozyGeneratorVersion = cozyversion,
            simpleModelerBackendVersion =
              org.simplemodeling.cozy.BuildInfo.simpleModelerVersion,
            simpleModelingModelVersion = modelversion,
            sourceIdentity = sourceidentity,
            sourceSha256 = sourcesha
          )
        )
        println(
          s"Generation provenance valid: ${output.resolve(cozy.modeler.GenerationProvenance.METADATA_PATH)} evidence=${manifest.evidenceDigest}"
        )
        true
      case _ =>
        false
    }

  private def _generation_source_snapshot(
    args: List[String],
    validateddescriptor: Option[cozy.compatibility.CncfRuntimeDescriptorContract.ValidatedDescriptor]
  ): Option[cozy.modeler.GenerationProvenance.SourceSnapshot] =
    validateddescriptor.map { _ =>
      val source = args.find(!_.startsWith("-")).
        map(Paths.get(_).toAbsolutePath.normalize()).
        getOrElse(RAISE.invalidArgumentFault(
          "CNCF-aware generation requires a readable CML source"
        ))
      val sourceidentity = cozy.modeler.GenerationProvenance.requireSourceIdentity(
        _option_value(args, "generation-source-identity"),
        "modeler"
      )
      cozy.modeler.GenerationProvenance.requireSourceSnapshot(
        source,
        sourceidentity
      )
    }

  private def _replace_first_positional_path(
    args: List[String],
    replacement: Path
  ): List[String] = {
    @annotation.tailrec
    def _go_(
      rest: List[String],
      done: Boolean,
      result: Vector[String]
    ): List[String] =
      rest match {
        case Nil =>
          result.toList
        case head :: tail if !done && !head.startsWith("-") =>
          _go_(tail, done = true, result :+ replacement.toString)
        case head :: tail =>
          _go_(tail, done, result :+ head)
      }
    _go_(args, done = false, Vector.empty)
  }

  private def _write_model_metadata(
    args: List[String],
    capturedsource: Option[Path],
    componentstylecatalog: modeler.ComponentStyleCatalog
  ): Unit = {
    val save = _save_path(args)
    val input = args.find(!_.startsWith("-")).map(Paths.get(_).toAbsolutePath.normalize())
    for {
      savedir <- save
      source <- input
      if Files.isRegularFile(source)
      if Files.exists(savedir)
    } {
      val metadir = savedir.resolve("target/cozy")
      cozy.modeler.CmlModelMetadata.write(
        capturedsource.getOrElse(source),
        metadir.resolve("model-metadata.json"),
        metadir.resolve("model-metadata.yaml"),
        source.toString,
        "cml",
        componentstylecatalog
      )
    }
  }

  private def _write_component_api_descriptor(args: List[String]): Unit =
    _save_path(args).foreach { savedir =>
      val modelpath = savedir.resolve("target/cozy/component-api-model.json")
      if (Files.isRegularFile(modelpath)) {
        val namespace = _option_value(args, "component-namespace")
          .getOrElse(RAISE.invalidArgumentFault("Component API generation requires --component-namespace"))
        val id = _option_value(args, "component-id")
          .getOrElse(RAISE.invalidArgumentFault("Component API generation requires --component-id"))
        val version = _option_value(args, "component-version")
          .getOrElse(RAISE.invalidArgumentFault("Component API generation requires --component-version"))
        cozy.modeler.ComponentApiDescriptor.write(
          modelpath,
          savedir.resolve("target/cozy/component-api-descriptor.json"),
          namespace,
          id,
          version
        )
      }
    }

  private def _write_generation_provenance(
    args: List[String],
    validateddescriptor: Option[cozy.compatibility.CncfRuntimeDescriptorContract.ValidatedDescriptor],
    sourcesnapshot: Option[cozy.modeler.GenerationProvenance.SourceSnapshot]
  ): Unit = {
    val save = _save_path(args)
    (validateddescriptor, save, sourcesnapshot) match {
      case (Some(descriptor), Some(savedir), Some(snapshot))
          if Files.exists(savedir) =>
        val modelversion = _option_value(args, "simplemodeling-model-version").
          getOrElse(org.simplemodeling.cozy.BuildInfo.simpleModelingModelVersion)
        cozy.modeler.GenerationProvenance.write(
          savedir,
          snapshot,
          cozy.modeler.GenerationProvenance.Inputs(
            cncfTargetVersion = descriptor.targetVersion,
            runtimeDescriptorSha256 = descriptor.sha256,
            cozyGeneratorVersion = org.simplemodeling.cozy.BuildInfo.version,
            simpleModelerBackendVersion =
              org.simplemodeling.cozy.BuildInfo.simpleModelerVersion,
            simpleModelingModelVersion = modelversion,
            sourceIdentity = snapshot.identity,
            sourceSha256 = snapshot.sha256
          )
        )
      case _ =>
        ()
    }
  }

  private def _option_value(args: List[String], name: String): Option[String] = {
    val option = s"--$name"
    val prefix = s"$option="
    args.zipWithIndex.collectFirst {
      case (value, _) if value.startsWith(prefix) => value.drop(prefix.length)
      case (value, index) if value == option && index + 1 < args.length => args(index + 1)
    }.map(_.trim).filter(_.nonEmpty)
  }

  private def _required_option_value(args: List[String], name: String): String =
    _option_value(args, name).getOrElse(
      RAISE.invalidArgumentFault(
        s"Generation provenance validation requires --$name"
      )
    )

  private def _execute_init(args: Array[String]): Boolean =
    _leading_command(args) match {
      case Some(("init", "component" :: rest)) =>
        val init = Cozy.ComponentInitConfig.create(rest)
        val policy = Cozy.ProjectFilePolicy.create(rest)
        val versions = Cozy.CarDependencyVersions.create(rest)
        init.style match {
          case CozyScaffold.ProjectLayoutStyle.CarOnly =>
            _materialize_car_sbt_project(init.save, policy, versions, init.scaffold, None)
            _materialize_car_project_yaml(init.save, policy, versions, init)
          case CozyScaffold.ProjectLayoutStyle.CarSar =>
            _materialize_car_sar_sbt_project(init.save, policy, versions, init.scaffold, None)
            _materialize_car_project_yaml(init.save.resolve("component"), policy, versions, init)
        }
        true
      case Some(("init", other :: _)) =>
        RAISE.invalidArgumentFault(s"Unsupported init target: ${other}")
      case _ =>
        false
    }

  private def _execute_publish_car(args: Array[String]): Boolean =
    _leading_command(args) match {
      case Some(("publish-car", rest)) =>
        CozyCarPublisher.publish(rest)
        true
      case _ =>
        false
    }

  private def _execute_publish_sar(args: Array[String]): Boolean =
    _leading_command(args) match {
      case Some(("publish-sar", rest)) =>
        CozySarPublisher.publish(rest)
        true
      case _ =>
        false
    }

  private def _execute_package_archive(args: Array[String]): Boolean =
    _leading_command(args) match {
      case Some(("package-car", rest)) =>
        CozyArchivePackager.buildCar(rest)
        true
      case Some(("package-sar", rest)) =>
        CozyArchivePackager.buildSar(rest)
        true
      case _ =>
        false
    }

  private def _execute_publish_project(args: Array[String]): Boolean =
    _leading_command(args) match {
      case Some(("publish-project", rest)) =>
        CozyPublicationCompiler.publish(rest)
        true
      case Some(("publish-maven-repository", rest)) =>
        CozyPublicationCompiler.publishMavenRepository(rest)
        true
      case Some(("unpublish-project", rest)) =>
        CozyPublicationCompiler.unpublish(rest)
        true
      case _ =>
        false
    }

  private def _execute_publish_video(args: Array[String]): Boolean =
    _leading_command(args) match {
      case Some(("publish-video", rest)) =>
        CozyVideoPublisher.publish(rest)
        true
      case _ =>
        false
    }

  private def _execute_distribute_samples(args: Array[String]): Boolean =
    _leading_command(args) match {
      case Some(("distribute-samples", rest)) =>
        CozySampleDistributor.distribute(rest)
        true
      case _ =>
        false
    }

  private def _execute_index_warehouse(args: Array[String]): Boolean =
    _leading_command(args) match {
      case Some(("index-warehouse", rest)) =>
        CozyWarehouseIndexer.index(rest)
        true
      case _ =>
        false
    }

  private def _execute_sbt_bridge(args: Array[String]): Boolean =
    _leading_command(args) match {
      case Some(("sbt-bridge", rest)) =>
        CozySbtBridge.execute(rest)
        true
      case _ =>
        false
    }

  private def _leading_command(args: Array[String]): Option[(String, List[String])] = {
    val i = args.indexWhere(x => !x.startsWith("-"))
    if (i >= 0)
      Some((args(i), args.drop(i + 1).toList))
    else
      None
  }

  private def _convert_args(args: List[String]): Vector[String] = {
    @annotation.tailrec
    def _go_(xs: List[String], z: Vector[String]): Vector[String] = xs match {
      case Nil => z
      case x :: xx if x.startsWith(":") =>
        _go_(xx, z :+ x)
      case x :: xx if x.startsWith("--") =>
        _split_option(x.drop(2)) match {
          case Some((name, value)) =>
            _go_(xx, z :+ s":$name" :+ _quote(value))
          case None =>
            xx match {
              case y :: yy if !y.startsWith("-") =>
                _go_(yy, z :+ s":${x.drop(2)}" :+ _quote(y))
              case _ =>
                _go_(xx, z :+ s":${x.drop(2)}")
            }
        }
      case x :: xx =>
        _go_(xx, z :+ _quote(x))
    }
    _go_(args, Vector.empty)
  }

  private def _split_option(p: String): Option[(String, String)] = {
    val i = p.indexOf('=')
    if (i >= 0)
      Some((p.substring(0, i), p.substring(i + 1)))
    else
      None
  }

  private def _quote(p: String): String = {
    val escaped = p.
      replace("\\", "\\\\").
      replace("\"", "\\\"")
    s""""$escaped""""
  }

  private def _save_path(args: List[String]): Option[Path] =
    Cozy._save_path(args)

  private def _without_save_args(args: List[String]): List[String] = args match {
    case Nil => Nil
    case x :: xs if x == "--save" => xs.drop(1)
    case x :: xs if x.startsWith("--save=") => _without_save_args(xs)
    case x :: xs => x :: _without_save_args(xs)
  }

  private def _without_project_file_policy_args(args: List[String]): List[String] =
    args match {
      case Nil => Nil
      case x :: xs if Cozy.CarDependencyVersions.isFlagOption(x) =>
        _without_project_file_policy_args(xs.drop(1))
      case x :: xs if Cozy.ProjectFilePolicy.isFlagOption(x) =>
        _without_project_file_policy_args(xs)
      case x :: xs if Cozy.ProjectLayoutStyle.isFlagOption(x) =>
        _without_project_file_policy_args(xs.drop(1))
      case x :: xs if Cozy.ProjectFilePolicy.isInlineOption(x) || Cozy.CarDependencyVersions.isInlineOption(x) || Cozy.ProjectLayoutStyle.isInlineOption(x) =>
        _without_project_file_policy_args(xs)
      case x :: xs =>
        x :: _without_project_file_policy_args(xs)
    }

  private def _without_style_args(args: List[String]): List[String] =
    args match {
      case Nil => Nil
      case x :: xs if Cozy.ProjectLayoutStyle.isFlagOption(x) =>
        _without_style_args(xs.drop(1))
      case x :: xs if Cozy.ProjectLayoutStyle.isInlineOption(x) =>
        _without_style_args(xs)
      case x :: xs =>
        x :: _without_style_args(xs)
    }

  private def _without_car_scaffold_args(args: List[String]): List[String] =
    args match {
      case Nil => Nil
      case x :: xs if Cozy.CarScaffoldConfig.isFlagOption(x) =>
        _without_car_scaffold_args(xs.drop(1))
      case x :: xs if Cozy.CarScaffoldConfig.isSwitchOption(x) =>
        _without_car_scaffold_args(xs)
      case x :: xs if Cozy.CarScaffoldConfig.isInlineOption(x) =>
        _without_car_scaffold_args(xs)
      case x :: xs =>
        x :: _without_car_scaffold_args(xs)
    }

  private def _project_save_path(style: Cozy.ProjectLayoutStyle, save: Path): Path =
    style match {
      case CozyScaffold.ProjectLayoutStyle.CarOnly => save
      case CozyScaffold.ProjectLayoutStyle.CarSar => save.resolve("component")
    }

  private def _materialize_car_project_yaml(
    dir: Path,
    policy: Cozy.ProjectFilePolicy,
    versions: Cozy.CarDependencyVersions,
    init: Cozy.ComponentInitConfig
  ): Unit =
    if (!policy.isSkip)
      _write_project_file(
        dir.resolve("project.yaml"),
        Cozy._car_project_yaml(init, versions),
        policy
      )

  private def _materialize_car_sbt_project(
    dir: Path,
    policy: Cozy.ProjectFilePolicy,
    versions: Cozy.CarDependencyVersions,
    scaffold: Cozy.CarScaffoldConfig,
    modelpath: Option[Path] = None
  ): Unit = {
    if (policy.isSkip)
      return
    Files.createDirectories(dir)
    _write_project_file(
      dir.resolve("build.sbt"),
      Cozy._car_build_sbt(versions, scaffold),
      policy
    )
    val projectdir = dir.resolve("project")
    Files.createDirectories(projectdir)
    _write_project_file(
      projectdir.resolve("build.properties"),
      s"sbt.version=${Cozy._detect_sbt_version()}",
      policy
    )
    _write_project_file(
      projectdir.resolve("plugins.sbt"),
      Cozy._car_plugins_sbt(),
      policy
    )
    _write_project_file(
      projectdir.resolve("ProjectYamlBuild.scala"),
      Cozy._car_project_yaml_build_scala(),
      policy
    )
    val cozydir = dir.resolve("src/main/cozy")
    Files.createDirectories(cozydir)
    val samplemodel = cozydir.resolve(scaffold.modelFileName)
    val modelcontent = modelpath.filter(Files.exists(_)).
      map(Files.readString(_, StandardCharsets.UTF_8)).
      getOrElse(Cozy._car_sample_cml(scaffold))
    _write_project_file(
      samplemodel,
      modelcontent,
      policy
    )
    val cardir = dir.resolve("src/main/car")
    Files.createDirectories(cardir)
    val webdir = dir.resolve("src/main/web-inf")
    Files.createDirectories(webdir)
    _write_project_file(
      webdir.resolve("web.yaml"),
      Cozy._car_web_app_yaml(scaffold),
      policy
    )
    _write_project_file(
      webdir.resolve("form.yaml"),
      Cozy._car_web_descriptor_yaml(modelpath, scaffold),
      policy
    )
    if (modelpath.isEmpty) {
      val impldir = dir.resolve(scaffold.scalaPackageDir("src/main/scala")).resolve("impl")
      Files.createDirectories(impldir)
      _write_project_file(
        impldir.resolve("ComponentFactory.scala"),
        Cozy._car_component_factory_source(scaffold),
        policy
      )
    }
    if (scaffold.gitignore)
      _write_project_file(dir.resolve(".gitignore"), Cozy._car_gitignore(), policy)
    if (scaffold.readme)
      _write_project_file(dir.resolve("README.md"), Cozy._car_readme(scaffold), policy)
    if (scaffold.tests) {
      val testdir = dir.resolve(scaffold.scalaPackageDir("src/test/scala"))
      Files.createDirectories(testdir)
      _write_project_file(
        testdir.resolve("ComponentFactorySpec.scala"),
        Cozy._car_component_factory_spec_source(scaffold),
        policy
      )
    }
    val bindir = dir.resolve("bin")
    Files.createDirectories(bindir)
    _write_executable_project_file(
      bindir.resolve("launcher"),
      Cozy._car_launcher_script(),
      policy
    )
    val scriptsdir = dir.resolve("scripts")
    Files.createDirectories(scriptsdir)
    _write_project_file(
      scriptsdir.resolve("cncf-common.sh"),
      Cozy._car_cncf_common_script(versions),
      policy
    )
    _write_executable_project_file(
      scriptsdir.resolve("update-runtime-classpath.sh"),
      Cozy._car_update_runtime_classpath_script(),
      policy
    )
    _write_executable_project_file(
      scriptsdir.resolve("run-server.sh"),
      Cozy._car_run_server_script(),
      policy
    )
    _write_executable_project_file(
      scriptsdir.resolve("run-server-debug.sh"),
      Cozy._car_run_server_debug_script(),
      policy
    )
  }

  private def _materialize_car_sar_sbt_project(
    dir: Path,
    policy: Cozy.ProjectFilePolicy,
    versions: Cozy.CarDependencyVersions,
    scaffold: Cozy.CarScaffoldConfig,
    modelpath: Option[Path] = None
  ): Unit = {
    if (policy.isSkip)
      return
    val appname = scaffold.artifactName
    Files.createDirectories(dir)
    _write_project_file(
      dir.resolve("README.md"),
      Cozy._car_sar_readme(scaffold),
      policy
    )
    _write_project_file(
      dir.resolve("build.sbt"),
      Cozy._car_sar_build_sbt(scaffold, versions),
      policy
    )
    val projectdir = dir.resolve("project")
    Files.createDirectories(projectdir)
    _write_project_file(
      projectdir.resolve("build.properties"),
      s"sbt.version=${Cozy._detect_sbt_version()}",
      policy
    )
    _write_project_file(
      projectdir.resolve("plugins.sbt"),
      Cozy._car_plugins_sbt(),
      policy
    )
    _write_project_file(
      projectdir.resolve("ProjectYamlBuild.scala"),
      Cozy._car_project_yaml_build_scala(),
      policy
    )

    val componentdir = dir.resolve("component")
    val cozydir = componentdir.resolve(s"src/main/cozy")
    Files.createDirectories(cozydir)
    val samplemodel = cozydir.resolve(scaffold.modelFileName)
    val modelcontent = modelpath.filter(Files.exists(_)).
      map(Files.readString(_, StandardCharsets.UTF_8)).
      getOrElse(Cozy._car_sar_sample_cml(scaffold))
    _write_project_file(samplemodel, modelcontent, policy)
    val cardir = componentdir.resolve("src/main/car")
    Files.createDirectories(cardir)
    val webdir = componentdir.resolve("src/main/web-inf")
    Files.createDirectories(webdir)
    _write_project_file(
      webdir.resolve("web.yaml"),
      Cozy._car_web_app_yaml(scaffold),
      policy
    )
    _write_project_file(
      webdir.resolve("form.yaml"),
      Cozy._car_web_descriptor_yaml(modelpath, scaffold),
      policy
    )
    _write_project_file(
      componentdir.resolve("src/main/resources/.keep"),
      "",
      policy
    )
    _write_project_file(
      componentdir.resolve("src/test/scala/.keep"),
      "",
      policy
    )

    val subsystemdir = dir.resolve("subsystem")
    Files.createDirectories(subsystemdir)
    _write_project_file(
      subsystemdir.resolve("subsystem-descriptor.yaml"),
      Cozy._car_sar_subsystem_descriptor_yaml(scaffold),
      policy
    )
    _write_project_file(
      subsystemdir.resolve("src/main/resources/.keep"),
      "",
      policy
    )
    _write_project_file(
      subsystemdir.resolve("src/test/scala/.keep"),
      "",
      policy
    )
    val repositorydir = dir.resolve("repository.d")
    Files.createDirectories(repositorydir)
    _write_project_file(
      repositorydir.resolve("README.md"),
      Cozy._car_sar_repository_d_readme(appname),
      policy
    )
    val scriptsdir = subsystemdir.resolve("scripts")
    Files.createDirectories(scriptsdir)
    _write_project_file(
      scriptsdir.resolve("README.md"),
      Cozy._car_sar_scripts_readme(appname),
      policy
    )
  }

  private def _write_project_file(
    path: Path,
    content: String,
    policy: Cozy.ProjectFilePolicy
  ): Unit =
    policy match {
      case CozyScaffold.ProjectFilePolicy.Skip =>
        Unit
      case CozyScaffold.ProjectFilePolicy.Overwrite =>
        _write_text(path, content)
      case CozyScaffold.ProjectFilePolicy.Default =>
        if (!Files.exists(path))
          _write_text(path, content)
        else if (Files.readString(path, StandardCharsets.UTF_8) == content)
          Unit
        else
          _write_text(Paths.get(path.toString + ".bak"), content)
    }

  private def _write_executable_project_file(
    path: Path,
    content: String,
    policy: Cozy.ProjectFilePolicy
  ): Unit = {
    _write_project_file(path, content, policy)
    if (Files.exists(path))
      path.toFile.setExecutable(true, false)
  }

  private def _delete_directory(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try {
        stream.iterator.asScala.toVector.reverse.foreach(Files.deleteIfExists)
      } finally {
        stream.close()
      }
    }

  private def _write_text(path: Path, content: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, content, StandardCharsets.UTF_8)
  }
}

object Cozy {
  type CarDependencyVersions = CozyScaffold.CarDependencyVersions
  object CarDependencyVersions {
    def apply(
      cncfVersion: String,
      simpleModelingModelVersion: String,
      cncfCollaboratorApiVersion: String
    ): CarDependencyVersions =
      CozyScaffold.CarDependencyVersions(
        cncfVersion,
        simpleModelingModelVersion,
        cncfCollaboratorApiVersion
      )

    def unapply(value: CarDependencyVersions): Option[(String, String, String)] =
      CozyScaffold.CarDependencyVersions.unapply(value)

    def default: CarDependencyVersions =
      CozyScaffold.CarDependencyVersions.default

    def isOption(p: String): Boolean =
      CozyScaffold.CarDependencyVersions.isOption(p)

    def isInlineOption(p: String): Boolean =
      CozyScaffold.CarDependencyVersions.isInlineOption(p)

    def isFlagOption(p: String): Boolean =
      CozyScaffold.CarDependencyVersions.isFlagOption(p)

    def create(args: List[String]): CarDependencyVersions =
      CozyScaffold.CarDependencyVersions.create(args)

    def create(args: List[String], config: cozy.config.CozyProjectYamlConfig.Config): CarDependencyVersions =
      CozyScaffold.CarDependencyVersions.create(args, config)
  }

  type ProjectLayoutStyle = CozyScaffold.ProjectLayoutStyle
  object ProjectLayoutStyle {
    def isOption(p: String): Boolean =
      CozyScaffold.ProjectLayoutStyle.isOption(p)

    def isInlineOption(p: String): Boolean =
      CozyScaffold.ProjectLayoutStyle.isInlineOption(p)

    def isFlagOption(p: String): Boolean =
      CozyScaffold.ProjectLayoutStyle.isFlagOption(p)

    def create(args: List[String]): ProjectLayoutStyle =
      CozyScaffold.ProjectLayoutStyle.create(args)
  }

  type ProjectFilePolicy = CozyScaffold.ProjectFilePolicy
  object ProjectFilePolicy {
    def isPolicyOption(p: String): Boolean =
      CozyScaffold.ProjectFilePolicy.isPolicyOption(p)

    def isFlagOption(p: String): Boolean =
      CozyScaffold.ProjectFilePolicy.isFlagOption(p)

    def isInlineOption(p: String): Boolean =
      CozyScaffold.ProjectFilePolicy.isInlineOption(p)

    def create(args: List[String]): ProjectFilePolicy =
      CozyScaffold.ProjectFilePolicy.create(args)
  }

  type CarScaffoldConfig = CozyScaffold.CarScaffoldConfig
  object CarScaffoldConfig {
    def apply(
      componentName: String,
      packageName: String,
      artifactName: String,
      organization: String,
      version: String,
      boundedContext: String,
      domain: String,
      gitignore: Boolean,
      readme: Boolean,
      tests: Boolean
    ): CarScaffoldConfig =
      CozyScaffold.CarScaffoldConfig(
        componentName,
        "Notice",
        "Notice",
        "PostNotice",
        "SearchNotices",
        packageName,
        artifactName,
        organization,
        version,
        boundedContext,
        domain,
        gitignore,
        readme,
        tests,
        false
      )

    def apply(
      componentName: String,
      serviceName: String,
      entityName: String,
      commandOperationName: String,
      queryOperationName: String,
      packageName: String,
      artifactName: String,
      organization: String,
      version: String,
      boundedContext: String,
      domain: String,
      gitignore: Boolean,
      readme: Boolean,
      tests: Boolean
    ): CarScaffoldConfig =
      CozyScaffold.CarScaffoldConfig(
        componentName,
        serviceName,
        entityName,
        commandOperationName,
        queryOperationName,
        packageName,
        artifactName,
        organization,
        version,
        boundedContext,
        domain,
        gitignore,
        readme,
        tests,
        false
      )

    def unapply(value: CarScaffoldConfig): Option[(String, String, String, String, String, String, String, String, String, String, String, Boolean, Boolean, Boolean, Boolean)] =
      CozyScaffold.CarScaffoldConfig.unapply(value)

    def isFlagOption(p: String): Boolean =
      CozyScaffold.CarScaffoldConfig.isFlagOption(p)

    def isInlineOption(p: String): Boolean =
      CozyScaffold.CarScaffoldConfig.isInlineOption(p)

    def isSwitchOption(p: String): Boolean =
      CozyScaffold.CarScaffoldConfig.isSwitchOption(p)

    def create(args: List[String], save: Path, style: ProjectLayoutStyle = CozyScaffold.ProjectLayoutStyle.CarOnly): CarScaffoldConfig =
      CozyScaffold.CarScaffoldConfig.create(args, save, style)
  }

  type ComponentInitConfig = CozyScaffold.ComponentInitConfig
  object ComponentInitConfig {
    def apply(
      save: Path,
      style: ProjectLayoutStyle,
      scaffold: CarScaffoldConfig,
      displayName: String
    ): ComponentInitConfig =
      CozyScaffold.ComponentInitConfig(save, style, scaffold, displayName)

    def unapply(value: ComponentInitConfig): Option[(Path, ProjectLayoutStyle, CarScaffoldConfig, String)] =
      CozyScaffold.ComponentInitConfig.unapply(value)

    def create(args: List[String]): ComponentInitConfig =
      CozyScaffold.ComponentInitConfig.create(args)
  }

  private[cozy] def _detect_sbt_version(): String = CozyScaffold.detectSbtVersion()
  private[cozy] def _app_name_from_path(path: Path): String = CozyScaffold.appNameFromPath(path)
  private[cozy] def _car_build_sbt(): String = CozyScaffold.carBuildSbt()
  private[cozy] def _car_build_sbt(versions: CarDependencyVersions, scaffold: CarScaffoldConfig): String = CozyScaffold.carBuildSbt(versions, scaffold)
  private[cozy] def _car_project_yaml_build_scala(): String = CozyScaffold.carProjectYamlBuildScala()
  private[cozy] def _car_project_yaml(init: ComponentInitConfig, versions: CarDependencyVersions): String = CozyScaffold.carProjectYaml(init, versions)
  private[cozy] def _car_sar_build_sbt(scaffold: CarScaffoldConfig, versions: CarDependencyVersions): String = CozyScaffold.carSarBuildSbt(scaffold, versions)
  private[cozy] def _car_sar_readme(scaffold: CarScaffoldConfig): String = CozyScaffold.carSarReadme(scaffold)
  private[cozy] def _car_sar_sample_cml(scaffold: CarScaffoldConfig): String = CozyScaffold.carSarSampleCml(scaffold)
  private[cozy] def _car_sar_subsystem_descriptor_yaml(scaffold: CarScaffoldConfig): String =
    CozyScaffold.carSarSubsystemDescriptorYaml(scaffold)
  private[cozy] def _car_sar_repository_d_readme(appname: String): String = CozyScaffold.carSarRepositoryDReadme(appname)
  private[cozy] def _car_sar_scripts_readme(appname: String): String = CozyScaffold.carSarScriptsReadme(appname)
  private[cozy] def _car_plugins_sbt(): String = CozyScaffold.carPluginsSbt()
  private[cozy] def _car_sample_cml(scaffold: CarScaffoldConfig = CarScaffoldConfig.create(Nil, Paths.get("sample"))): String = CozyScaffold.carSampleCml(scaffold)
  private[cozy] def _car_web_descriptor_yaml(modelpath: Option[Path] = None, scaffold: CarScaffoldConfig = CarScaffoldConfig.create(Nil, Paths.get("sample"))): String = CozyScaffold.carWebDescriptorYaml(modelpath, scaffold)
  private[cozy] def _car_web_app_yaml(scaffold: CarScaffoldConfig = CarScaffoldConfig.create(Nil, Paths.get("sample"))): String = CozyScaffold.carWebAppYaml(scaffold)
  private[cozy] def _car_component_factory_source(scaffold: CarScaffoldConfig = CarScaffoldConfig.create(Nil, Paths.get("sample"))): String = CozyScaffold.carComponentFactorySource(scaffold)
  private[cozy] def _car_gitignore(): String = CozyScaffold.carGitignore()
  private[cozy] def _car_readme(scaffold: CarScaffoldConfig): String = CozyScaffold.carReadme(scaffold)
  private[cozy] def _car_component_factory_spec_source(scaffold: CarScaffoldConfig): String = CozyScaffold.carComponentFactorySpecSource(scaffold)
  private[cozy] def _car_cncf_common_script(versions: CarDependencyVersions): String = CozyScaffold.carCncfCommonScript(versions)
  private[cozy] def _car_update_runtime_classpath_script(): String = CozyScaffold.carUpdateRuntimeClasspathScript()
  private[cozy] def _car_run_server_script(): String = CozyScaffold.carRunServerScript()
  private[cozy] def _car_run_server_debug_script(): String = CozyScaffold.carRunServerDebugScript()
  private[cozy] def _car_launcher_script(): String = CozyScaffold.carLauncherScript()
  private[cozy] val _help_text: String = CozyScaffold.helpText

  val cozyServiceClass: CozyRuntime.CozyServiceClass.type = CozyRuntime.CozyServiceClass
  val cozyOperationClass: CozyRuntime.CozyOperationClass.type = CozyRuntime.CozyOperationClass
  val webOperationClass: CozyRuntime.WebOperationClass.type = CozyRuntime.WebOperationClass

  /** Source-compatibility aliases retained for code compiled against the former public labels. */
  @deprecated("Use cozyServiceClass", "0.3.2")
  val CozyServiceClass: CozyRuntime.CozyServiceClass.type = cozyServiceClass
  @deprecated("Use cozyOperationClass", "0.3.2")
  val CozyOperationClass: CozyRuntime.CozyOperationClass.type = cozyOperationClass
  @deprecated("Use webOperationClass", "0.3.2")
  val WebOperationClass: CozyRuntime.WebOperationClass.type = webOperationClass

  def build(args: Array[String]): Cozy = CozyRuntime.build(args)

  def main(args: Array[String]): Unit = {
    val preflight = CozyCliPreflight.parse(args)
    CozyCliLogging.configure(preflight.outputPolicy)
    if (!_execute_preflight_cli(preflight))
      CozyRuntime.main(args)
  }

  private[cozy] def _execute_preflight_cli(preflight: CozyCliPreflight): Boolean =
    preflight.jsonLintCommand.fold(false) {
      case (label, args) =>
        _execute_json_lint(label, args)
    }

  private def _execute_json_lint(label: String, args: List[String]): Boolean = {
    try {
      val exitcode = label match {
        case "build" => CozyBuildLint.execute(args)
        case "cml" => CozyCmlLint.execute(args)
        case "abi" => CozyCarAbiLint.execute(args)
        case "car" => CozyCarLint.execute(args)
        case "repository" => CozyRepositoryLint.execute(args)
      }
      if (exitcode != 0)
        sys.exit(exitcode)
    } catch {
      case NonFatal(e) =>
        System.err.println(s"cozy lint ${label} failed: ${e.getMessage}")
        sys.exit(1)
    }
    true
  }

  private[cozy] def _save_path(args: List[String]): Option[Path] = CozyRuntime.savePath(args)
  private[cozy] def _cli_path(value: String): Path = CozyRuntime.cliPath(value)
}

private object CozyOperationConfig {
  def _with_pdf_defaults(args: List[String]): List[String] =
    _with_pdf_defaults(args, _invocation_directory)

  private[cozy] def _with_pdf_defaults(args: List[String], root: Path): List[String] =
    _pdf_property_options.foldLeft(args) {
      case (z, (property, option)) =>
        if (_has_option(z, option))
          z
        else
          _value(root, property).fold(z)(v => z :+ option :+ v)
    }

  private val _pdf_property_options = Vector(
    "renderer" -> "--renderer",
    "latex-engine" -> "--latex-engine",
    "latex-format" -> "--latex-format",
    "latex-date" -> "--latex-date",
    "latex-affiliation" -> "--latex-affiliation",
    "latex-author" -> "--latex-author",
    "dependency-mode" -> "--dependency-mode",
    "docker-image" -> "--docker-image"
  )

  private def _value(root: Path, name: String): Option[String] = {
    val keys = _config_keys(name)
    _config_files(root).foldLeft(Option.empty[String]) { (z, file) =>
      _load(file).flatMap(m => keys.toStream.flatMap(m.get).lastOption).orElse(z)
    }
  }

  private def _config_keys(name: String): Vector[String] = {
    val canonical = name.trim.toLowerCase.replace('_', '-')
    val dotted = _normalize(name)
    val suffixes =
      if (canonical == dotted)
        Vector(canonical)
      else
        Vector(canonical, dotted)
    val bases = suffixes.flatMap(key => Vector(s"pdf.$key", s"smartdox.pdf.$key", s"cozy.pdf.$key"))
    if (canonical == "docker-image" || dotted == "docker.image")
      bases :+ "cozy.docker.image"
    else
      bases
  }

  private def _config_files(root: Path): Vector[Path] =
    cozy.config.CozyProjectYamlConfig.operationDefaultFiles(root.toAbsolutePath.normalize)

  private def _invocation_directory: Path =
    sys.env.get("COZY_INVOCATION_DIR").
      filter(_.nonEmpty).
      map(Paths.get(_)).
      getOrElse(Paths.get(sys.props("user.dir"))).
      toAbsolutePath.
      normalize()

  private def _load(path: Path): Option[Map[String, String]] =
    if (!Files.isRegularFile(path))
      None
    else
      Some(cozy.config.CozyProjectYamlConfig.load(path).values)

  private def _parse(lines: Vector[String]): Map[String, String] = {
    case class Context(section0: String = "", section2: String = "")
    lines.foldLeft((Context(), Map.empty[String, String])) {
      case ((ctx, acc), raw) =>
        _parse_line(raw) match {
          case None => (ctx, acc)
          case Some((indent, key, value)) =>
            indent match {
              case 0 =>
                value match {
                  case Some(v) => (Context(key), acc + (key -> v))
                  case None => (Context(key), acc)
                }
              case 2 =>
                val full = _join(ctx.section0, key)
                value match {
                  case Some(v) => (ctx.copy(section2 = key), acc + (full -> v))
                  case None => (ctx.copy(section2 = key), acc)
                }
              case _ =>
                val prefix =
                  if (ctx.section2.nonEmpty)
                    _join(ctx.section0, ctx.section2)
                  else
                    ctx.section0
                val full = _join(prefix, key)
                value match {
                  case Some(v) => (ctx, acc + (full -> v))
                  case None => (ctx, acc)
                }
            }
        }
    }._2
  }

  private def _parse_line(raw: String): Option[(Int, String, Option[String])] = {
    val line = raw.takeWhile(_ != '#')
    if (line.trim.isEmpty || !line.contains(":"))
      None
    else {
      val indent = line.takeWhile(_ == ' ').length
      val trimmed = line.trim
      val i = trimmed.indexOf(':')
      val key = _normalize(trimmed.substring(0, i))
      val rest = _trim_value(trimmed.substring(i + 1))
      Some((indent, key, if (rest.isEmpty) None else Some(rest)))
    }
  }

  private def _has_option(args: List[String], option: String): Boolean =
    args.exists(x => x == option || x.startsWith(s"$option="))

  private def _join(a: String, b: String): String =
    if (a.isEmpty)
      b
    else
      s"$a.$b"

  private def _normalize(p: String): String =
    p.trim.toLowerCase.replace('_', '.').replace('-', '.')

  private def _trim_value(p: String): String = {
    val s = p.trim
    if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'")))
      s.substring(1, s.length - 1)
    else
      s
  }
}
