ThisBuild / scalaVersion := "2.12.18"

resolvers += "GitHab releases 2020" at "https://raw.github.com/asami/maven-repository/2020/releases"
resolvers += "GitHab releases" at "https://raw.github.com/asami/maven-repository/2025/releases"
resolvers += Resolver.defaultLocal
resolvers += Resolver.file("Local Ivy", file(Path.userHome.absolutePath + "/.ivy2/local"))(Resolver.ivyStylePatterns)
resolvers += "Local Maven Repository" at ("file://" + Path.userHome.absolutePath + "/.m2/repository")

useCoursier := false

libraryDependencies += "org.simplemodeling" %% "cozy" % sys.props("cozy.version")

// The generated fixture consumes the local SmartDox snapshot through Ivy.
// Bind its scala-lib snapshot explicitly to the published compile configuration.
libraryDependencies += "org.goldenport" %% "goldenport-scala-lib" % "2.3.32-SNAPSHOT" % "compile->compile"

dependencyOverrides ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % "2.1.0",
  "org.scala-lang.modules" %% "scala-parser-combinators" % "2.3.0"
)
