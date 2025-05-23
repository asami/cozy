organization := "org.simplemodeling"

name := "cozy"

version := "0.2.1"

scalaVersion := "2.12.18"
// crossScalaVersions := Seq("2.10.39.2", "2.9.1")

scalacOptions += "-deprecation"

scalacOptions += "-unchecked"

scalacOptions += "-feature"

// resolvers += "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases"

// resolvers += "GitHab releases 2019" at "https://raw.github.com/asami/maven-repository/2019/releases"

resolvers += "GitHab releases 2020" at "https://raw.github.com/asami/maven-repository/2020/releases"

// resolvers += "GitHab releases 2021" at "https://raw.github.com/asami/maven-repository/2021/releases"

// resolvers += "GitHab releases 2022" at "https://raw.github.com/asami/maven-repository/2022/releases"

// resolvers += "GitHab releases 2023" at "https://raw.github.com/asami/maven-repository/2023/releases"

// resolvers += "GitHab releases" at "https://raw.github.com/asami/maven-repository/2024/releases"

resolvers += "GitHab releases" at "https://raw.github.com/asami/maven-repository/2025/releases"

resolvers += "GitHub Packages" at "https://maven.pkg.github.com/asami/maven-repository"

// resolvers += "Asami Maven Repository" at "http://www.asamioffice.com/maven"

resolvers += "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository"

// override arcadia
libraryDependencies += "org.goldenport" %% "goldenport-scala-lib" % "2.2.2"

// override kaleidox
// libraryDependencies += "org.goldenport" %% "goldenport-record" % "1.3.70"

// override kaleidox
// libraryDependencies += "org.goldenport" %% "goldenport-sexpr" % "2.0.13"

// override kaleidox
// libraryDependencies += "org.smartdox" %% "smartdox" % "1.3.1"

libraryDependencies += "org.goldenport" %% "kaleidox" % "0.6.1"

libraryDependencies += "org.simplemodeling" %% "simplemodeler" % "1.1.1"

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

lazy val exportClasspath = taskKey[Unit]("Export full classpath to a file")

exportClasspath := {
  val cp = (Compile / fullClasspath).value.files
  val out = (Compile / target).value / "classpath.txt"
  IO.write(out, cp.mkString(":"))
  println(s"Classpath written to: $out")
}

// Publish
publishTo := Some(
  "GitHub Packages" at "https://maven.pkg.github.com/asami/maven-repository"
)

credentials += Credentials(Path.userHome / ".sbt" / ".credentials")

publishMavenStyle := true

// Docker
maintainer := "asami@asamioffice.com"

dockerBaseImage in Docker := "dockerfile/java"

// dockerExposedPorts in Docker := Seq(8080, 8080)

lazy val root = (project in file(".")).
  enablePlugins(BuildInfoPlugin).
  enablePlugins(JavaAppPackaging).
  settings(
    buildInfoKeys := Seq[BuildInfoKey](
      name, version, scalaVersion, sbtVersion,
      BuildInfoKey.action("build") {
        val fmt = new java.text.SimpleDateFormat("yyyyMMdd")
        fmt.setTimeZone(java.util.TimeZone.getTimeZone("JST"))
        fmt.format(new java.util.Date())
      }
    ),
    buildInfoPackage := "org.simplemodeling.cozy"
  )
