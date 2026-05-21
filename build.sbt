import sbt.ScriptedPlugin
import sbt.ScriptedPlugin.autoImport._

lazy val publishCozyCoursierChannel = taskKey[File]("Publish the Cozy Coursier channel descriptor into the warehouse repository.")

def cozyPublishRepositoryFile(resolver: Resolver): Option[File] =
  resolver match {
    case m: MavenRepository =>
      val root = m.root
      if (root.startsWith("file:"))
        Some(new File(new java.net.URI(root)))
      else
        Some(file(root))
    case f: FileRepository =>
      f.patterns.artifactPatterns.headOption.flatMap { pattern =>
        val marker = "/[organisation]/"
        val index = pattern.indexOf(marker)
        if (index >= 0)
          Some(file(pattern.take(index)))
        else
          None
      }
    case _ =>
      None
  }

def cozyWarehouseDirFromMavenRepository(repository: File): File =
  repository.getCanonicalFile match {
    case canonical
        if canonical.getName == "maven" &&
          canonical.getParentFile != null &&
          canonical.getParentFile.getName == "repository" =>
      canonical.getParentFile.getParentFile
    case canonical if canonical.getName == "maven" =>
      canonical.getParentFile
    case canonical =>
      sys.error(
        s"Cozy Coursier channel publish requires publishTo to point at a Maven repository " +
          s"under a warehouse, but got: ${canonical}"
      )
  }

def cozyCoursierChannelJson(version: String): String =
  s"""{
     |  "cozy": {
     |    "repositories": [
     |      "central",
     |      "https://www.simplemodeling.org/repository/maven"
     |    ],
     |    "dependencies": [
     |      "org.simplemodeling:cozy_2.12:$version"
     |    ],
     |    "mainClass": "cozy.Cozy"
     |  }
     |}
     |""".stripMargin

def cozyPublishCoursierChannelFile(
  version: String,
  publishResolver: Option[Resolver],
  baseDir: File,
  log: sbt.util.Logger
): File = {
  val warehouseDir =
    publishResolver
      .flatMap(cozyPublishRepositoryFile)
      .map(cozyWarehouseDirFromMavenRepository)
      .getOrElse(cozyWarehouseDirFromMavenRepository(baseDir / "maven-local"))
  val target = warehouseDir / "repository" / "cozy" / "coursier-channel.json"
  IO.createDirectory(target.getParentFile)
  IO.write(target, cozyCoursierChannelJson(version))
  log.info(s"Published Cozy Coursier channel to ${target}")
  target
}

organization := "org.simplemodeling"

name := "cozy"

version := "0.2.20-SNAPSHOT"

lazy val cncfVersion = "0.4.9"

lazy val simpleModelingModelVersion = "0.1.7"

lazy val cncfCollaboratorApiVersion = "0.1.0"

scalaVersion := "2.12.18"
// crossScalaVersions := Seq("2.10.39.2", "2.9.1")

scalacOptions += "-deprecation"

scalacOptions += "-unchecked"

scalacOptions += "-feature"

javacOptions ++= Seq("--release", "21")

// resolvers += "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases"

// resolvers += "GitHab releases 2019" at "https://raw.github.com/asami/maven-repository/2019/releases"

resolvers += "GitHab releases 2020" at "https://raw.github.com/asami/maven-repository/2020/releases"

// resolvers += "GitHab releases 2021" at "https://raw.github.com/asami/maven-repository/2021/releases"

// resolvers += "GitHab releases 2022" at "https://raw.github.com/asami/maven-repository/2022/releases"

// resolvers += "GitHab releases 2023" at "https://raw.github.com/asami/maven-repository/2023/releases"

// resolvers += "GitHab releases" at "https://raw.github.com/asami/maven-repository/2024/releases"

resolvers += "GitHab releases" at "https://raw.github.com/asami/maven-repository/2025/releases"

resolvers += "GitHub Packages" at "https://maven.pkg.github.com/asami/maven-repository"

resolvers += "SimpleModeling.org" at "https://www.simplemodeling.org/repository/maven"

// resolvers += "Asami Maven Repository" at "http://www.asamioffice.com/maven"

resolvers += Resolver.file("Local Ivy", file(Path.userHome.absolutePath + "/.ivy2/local"))(Resolver.ivyStylePatterns)

resolvers += Resolver.defaultLocal

// override arcadia
libraryDependencies += "org.goldenport" %% "goldenport-scala-lib" % "2.3.2"

// override kaleidox
libraryDependencies += "org.goldenport" %% "goldenport-record" % "2.2.5"

// override kaleidox
// libraryDependencies += "org.goldenport" %% "goldenport-sexpr" % "2.0.13"

// override kaleidox
libraryDependencies += "org.smartdox" %% "smartdox" % "2.4.13"

libraryDependencies += "org.goldenport" %% "kaleidox" % "0.6.16-SNAPSHOT"

libraryDependencies += "org.simplemodeling" %% "simplemodeler" % "1.1.20-SNAPSHOT"

libraryDependencies += "org.goldenport" %% "arcadia" % "0.6.1"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.4.11"

libraryDependencies += "com.typesafe.play" %% "play-json" % "2.9.4"

libraryDependencies += "org.scalaj" %% "scalaj-http" % "2.4.1"

libraryDependencies += "commons-jxpath" % "commons-jxpath" % "1.3"

libraryDependencies += "cat.inspiracio" % "rhino-js-engine" % "1.7.7.1"

libraryDependencies += "org.apache.commons" % "commons-jexl3" % "3.0"

libraryDependencies += "org.scalanlp" %% "breeze" % "0.13.2"

libraryDependencies += "org.scalanlp" %% "breeze-viz" % "0.13.2"

libraryDependencies += "org.scalafx" %% "scalafx" % "8.0.181-R13"

libraryDependencies += "org.apache.camel" % "camel-core" % "2.23.1"

libraryDependencies += "com.amazonaws" % "aws-java-sdk" % "1.11.519"

libraryDependencies += "com.zaxxer" % "HikariCP-java7" % "2.4.13"

libraryDependencies += "mysql" % "mysql-connector-java" % "5.1.46"

libraryDependencies += "postgresql" %  "postgresql" % "8.4-702.jdbc4"

libraryDependencies += "com.h2database" % "h2" % "1.4.199"

libraryDependencies += "org.apache.spark" %% "spark-core" % "3.5.5" % "provided" exclude("org.glassfish.hk2", "hk2-utils") exclude("org.glassfish.hk2", "hk2-locator") exclude("javax.validation", "validation-api") exclude("org.slf4j", "slf4j-log4j12") exclude("org.apache.logging.log4j", "log4j-slf4j2-impl") // Useing old version for Scala 2.10

libraryDependencies += "org.apache.spark" %% "spark-sql" % "3.5.5" % "provided" exclude("org.glassfish.hk2", "hk2-utils") exclude("org.glassfish.hk2", "hk2-locator") exclude("javax.validation", "validation-api") exclude("org.slf4j", "slf4j-log4j12") exclude("org.apache.logging.log4j", "log4j-slf4j2-impl") // Useing old version for Scala 2.10

libraryDependencies += "org.apache.felix" % "org.apache.felix.main" % "5.4.0"

libraryDependencies += "org.eclipse.jetty" % "jetty-server" % "9.4.38.v20210224"

// libraryDependencies += "com.typesafe.akka" % "akka-http" % "10.2.2"

// libraryDependencies += "org.xerial" % "sqlite-jdbc" % "3.27.2.1"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19" % "test"

libraryDependencies += "junit" % "junit" % "4.10" % "test"

libraryDependencies ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % "2.1.0",
  "org.scala-lang.modules" %% "scala-parser-combinators" % "2.3.0"
)

dependencyOverrides ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % "2.1.0",
  "org.scala-lang.modules" %% "scala-parser-combinators" % "2.3.0"
)

dependencyOverrides ++= Seq(
  "org.apache.logging.log4j" % "log4j-core" % "2.20.0" % "provided",
  "org.apache.logging.log4j" % "log4j-api" % "2.20.0" % "provided"
)

excludeDependencies ++= Seq(
  ExclusionRule("org.apache.logging.log4j", "log4j-core"),
  ExclusionRule("org.apache.logging.log4j", "log4j-api"),
  ExclusionRule("org.apache.logging.log4j", "log4j-slf4j2-impl")
)

Compile / mainClass := Some("cozy.Cozy")

useCoursier := false

lazy val exportClasspath = taskKey[Unit]("Export full classpath to a file")

exportClasspath := {
  val cp = (Compile / fullClasspath).value.files
  val out = (Compile / target).value / "classpath.txt"
  IO.write(out, cp.mkString(":"))
  println(s"Classpath written to: $out")
}

Compile / packageBin := (Compile / packageBin).dependsOn(exportClasspath).value

// Publish
publishTo := {
  val repo = sys.env.get("SIMPLEMODELING_MAVEN_LOCAL")
    .map(file)
    .getOrElse(baseDirectory.value / "maven-local")

  Some(
    Resolver.file(
      "local-simplemodeling-maven",
      repo
    )
  )
}

credentials += Credentials(Path.userHome / ".sbt" / ".credentials")

publishMavenStyle := true

Compile / packageDoc / publishArtifact := false

Compile / doc / sources := Seq.empty

publishCozyCoursierChannel := {
  cozyPublishCoursierChannelFile(version.value, publishTo.value, baseDirectory.value, streams.value.log)
}

publish / packagedArtifacts := {
  publishCozyCoursierChannel.value
  (publish / packagedArtifacts).value
}

// Docker
maintainer := "asami@asamioffice.com"

(Docker / dockerBaseImage).withRank(KeyRanks.Invisible) := "dockerfile/java"

// dockerExposedPorts in Docker := Seq(8080, 8080)

lazy val root = (project in file(".")).
  enablePlugins(BuildInfoPlugin).
  enablePlugins(ScriptedPlugin).
  enablePlugins(JavaAppPackaging).
  settings(
    buildInfoKeys := Seq[BuildInfoKey](
      name, version, scalaVersion, sbtVersion,
      BuildInfoKey.action("cncfVersion")(cncfVersion),
      BuildInfoKey.action("simpleModelingModelVersion")(simpleModelingModelVersion),
      BuildInfoKey.action("cncfCollaboratorApiVersion")(cncfCollaboratorApiVersion),
      BuildInfoKey.action("build") {
        val fmt = new java.text.SimpleDateFormat("yyyyMMdd")
        fmt.setTimeZone(java.util.TimeZone.getTimeZone("JST"))
        fmt.format(new java.util.Date())
      }
    ),
    buildInfoPackage := "org.simplemodeling.cozy",
    scriptedBufferLog := false,
    scriptedLaunchOpts ++= Seq(
      s"-Dcozy.version=${version.value}"
    ),
    scriptedDependencies := (Compile / publishLocal).value
  )
