package cozy.modeler

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 13, 2026
 * @version Oct. 13, 2026
 */
class DartSdkSupportSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Portable native Dart SDK discovery" should {
    "prefer the explicit SDK root or binary over executable, environment and PATH settings" in _fixture { root =>
      Given("distinct executable SDKs and a root containing spaces and Japanese characters")
      val sdkroot = root.resolve("SDK 日本 with spaces")
      val sdk = _binary(sdkroot.resolve("bin/dart"))
      val other = _binary(root.resolve("other/bin/dart"))
      val properties = Map("cozy.dart.sdk" -> sdkroot.toString, "cozy.dart.executable" -> other.toString)
      val environment = Map("COZY_DART_EXECUTABLE" -> other.toString, "DART_SDK" -> other.getParent.getParent.toString,
        "PATH" -> other.getParent.toString)
      When("either the SDK root or its direct binary is explicitly selected")
      val directory = DartSdkSupport.resolve(properties, environment)
      val executable = DartSdkSupport.resolve(properties.updated("cozy.dart.sdk", sdk.toString), environment)
      Then("both resolve to that exact native SDK, not another setting")
      directory shouldBe Right(sdk.toRealPath())
      executable shouldBe directory
    }

    "retain executable-property, executable-environment and SDK-environment precedence" in _fixture { root =>
      Given("three independent configured SDKs and a fourth on PATH")
      val property = _binary(root.resolve("property/bin/dart"))
      val executable = _binary(root.resolve("environment/bin/dart"))
      val sdk = _binary(root.resolve("sdk/bin/dart"))
      val path = _binary(root.resolve("path/bin/dart"))
      val environment = Map("COZY_DART_EXECUTABLE" -> executable.toString,
        "DART_SDK" -> sdk.getParent.getParent.toString, "PATH" -> path.getParent.toString)
      When("higher-priority settings are progressively absent")
      val propertyresult = DartSdkSupport.resolve(Map("cozy.dart.executable" -> property.toString), environment)
      val executableresult = DartSdkSupport.resolve(Map.empty, environment)
      val sdkresult = DartSdkSupport.resolve(Map.empty, environment - "COZY_DART_EXECUTABLE")
      Then("each next explicitly configured SDK is used before PATH")
      propertyresult shouldBe Right(property.toRealPath())
      executableresult shouldBe Right(executable.toRealPath())
      sdkresult shouldBe Right(sdk.toRealPath())
    }

    "find the first executable native Dart on PATH without any Homebrew installation" in _fixture { root =>
      Given("a missing PATH directory followed by two arbitrary SDK directories")
      val first = _binary(root.resolve("custom sdk/bin/dart"))
      val second = _binary(root.resolve("another/bin/dart"))
      val path = Vector(root.resolve("missing"), first.getParent, second.getParent).mkString(File.pathSeparator)
      When("no explicit SDK is configured")
      val result = DartSdkSupport.resolve(Map.empty, Map("PATH" -> path))
      Then("PATH order selects the first available native binary")
      result shouldBe Right(first.toRealPath())
    }

    "resolve Flutter wrappers through symlinks for Intel and Apple Silicon layouts at arbitrary versions" in _fixture { root =>
      Vector("usr/local" -> "3.47.4", "opt/homebrew" -> "3.47.5", "custom 日本 prefix" -> "next-version").foreach {
        case (prefix, version) =>
          Given(s"a $prefix Flutter $version layout below the isolated fixture root")
          val installation = root.resolve(prefix)
          val flutterbin = installation.resolve(s"Caskroom/flutter/$version/flutter/bin")
          val wrapper = _wrapper(flutterbin.resolve("dart"))
          val sdk = _binary(flutterbin.resolve("cache/dart-sdk/bin/dart"))
          val entry = installation.resolve("bin/dart")
          Files.createDirectories(entry.getParent)
          Files.createSymbolicLink(entry, entry.getParent.relativize(wrapper))
          When("PATH selects the symlink to Flutter's wrapper")
          val result = DartSdkSupport.resolve(Map.empty, Map("PATH" -> entry.getParent.toString))
          Then("the adjacent native SDK is selected without executing the wrapper")
          result shouldBe Right(sdk.toRealPath())
      }
    }

    "reject invalid explicit settings even when a valid Dart SDK is on PATH" in _fixture { root =>
      Given("a valid implicit SDK and explicitly missing SDK or binary settings")
      val sdk = _binary(root.resolve("valid/bin/dart"))
      val missing = root.resolve("missing").toString
      val path = Map("PATH" -> sdk.getParent.toString)
      When("each supported explicit setting selects a missing path")
      val results = Vector(
        DartSdkSupport.resolve(Map("cozy.dart.sdk" -> missing), path),
        DartSdkSupport.resolve(Map("cozy.dart.executable" -> missing), path),
        DartSdkSupport.resolve(Map.empty, path + ("COZY_DART_EXECUTABLE" -> missing)),
        DartSdkSupport.resolve(Map.empty, path + ("DART_SDK" -> missing)))
      Then("all fail at the explicit prerequisite instead of silently using PATH")
      results.foreach { result =>
        result.isLeft shouldBe true
        result.left.toOption.get should include(missing)
        result.left.toOption.get should include("not an executable file")
      }
    }

    "reject empty explicit settings rather than choosing the working directory or PATH" in _fixture { root =>
      Given("an empty SDK setting and a valid implicit SDK")
      val sdk = _binary(root.resolve("valid/bin/dart"))
      When("the empty setting has precedence over PATH")
      val result = DartSdkSupport.resolve(Map("cozy.dart.sdk" -> ""), Map("PATH" -> sdk.getParent.toString))
      Then("discovery reports the invalid setting without a fallback")
      result.isLeft shouldBe true
      result.left.toOption.get should include("must not be empty")
    }

    "reject explicitly selected shell wrappers even when their adjacent native SDK exists" in _fixture { root =>
      Given("a Flutter wrapper with a valid adjacent SDK")
      val wrapper = _wrapper(root.resolve("flutter/bin/dart"))
      _binary(wrapper.getParent.resolve("cache/dart-sdk/bin/dart"))
      When("the wrapper itself is explicitly selected")
      val result = DartSdkSupport.resolve(Map("cozy.dart.executable" -> wrapper.toString), Map.empty)
      Then("discovery rejects the wrapper rather than changing the explicit selection")
      result.isLeft shouldBe true
      result.left.toOption.get should include("never a shell wrapper")
    }

    "reject PATH wrappers without a usable adjacent native SDK" in _fixture { root =>
      Given("a PATH shell wrapper whose adjacent SDK is absent or another shell wrapper")
      val wrapper = _wrapper(root.resolve("flutter/bin/dart"))
      val environment = Map("PATH" -> wrapper.getParent.toString)
      When("discovery is attempted before and after adding a non-native adjacent file")
      val missing = DartSdkSupport.resolve(Map.empty, environment)
      _wrapper(wrapper.getParent.resolve("cache/dart-sdk/bin/dart"))
      val nonnative = DartSdkSupport.resolve(Map.empty, environment)
      Then("both fail instead of running a wrapper or canceling runtime acceptance")
      Vector(missing, nonnative).foreach { result =>
        result.isLeft shouldBe true
        result.left.toOption.get should include("direct native binary")
      }
    }

    "report SDK prerequisites when no SDK is available" in _fixture { root =>
      Given("no configuration and a PATH without Dart")
      When("discovery runs with only a nonexistent fixture directory on PATH")
      val result = DartSdkSupport.resolve(Map.empty, Map("PATH" -> root.resolve("missing").toString))
      Then("the diagnostic explains the supported explicit settings")
      result.isLeft shouldBe true
      Vector("cozy.dart.sdk", "cozy.dart.executable", "COZY_DART_EXECUTABLE", "DART_SDK", "PATH").foreach { name =>
        result.left.toOption.get should include(name)
      }
    }
  }

  private def _fixture(body: Path => Unit): Unit = {
    val parent = Paths.get("target/test-generated/dart-sdk-selection").toAbsolutePath
    Files.createDirectories(parent)
    val root = Files.createTempDirectory(parent, "discovery-")
    try body(root) finally {
      val entries = Files.walk(root)
      try entries.iterator().asScala.toVector.reverse.foreach(path => Files.delete(path)) finally entries.close()
    }
  }

  // Synthetic headers are used only for discovery; these files are never executed.
  private def _binary(path: Path): Path = _file(path, Array(0xcf, 0xfa, 0xed, 0xfe).map(_.toByte))

  private def _wrapper(path: Path): Path = _file(path, "#!/bin/sh\nexit 99\n".getBytes(StandardCharsets.UTF_8))

  private def _file(path: Path, bytes: Array[Byte]): Path = {
    Files.createDirectories(path.getParent)
    Files.write(path, bytes)
    require(path.toFile.setExecutable(true), s"Unable to mark isolated discovery fixture executable: $path")
    path
  }
}
