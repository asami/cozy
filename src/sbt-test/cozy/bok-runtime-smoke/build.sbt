ThisBuild / scalaVersion := "2.12.18"

resolvers += "GitHab releases 2020" at "https://raw.github.com/asami/maven-repository/2020/releases"
resolvers += "GitHab releases" at "https://raw.github.com/asami/maven-repository/2025/releases"
resolvers += Resolver.defaultLocal
resolvers += Resolver.file("Local Ivy", file(Path.userHome.absolutePath + "/.ivy2/local"))(Resolver.ivyStylePatterns)
resolvers += "Local Maven Repository" at ("file://" + Path.userHome.absolutePath + "/.m2/repository")

useCoursier := false

Global / onLoad := {
  val previous = (Global / onLoad).value
  state =>
    val state1 = previous(state)
    val cozyversion = sys.props("cozy.version")
    IO.createDirectory(file("target"))
    IO.write(file("target/cozy-version.txt"), cozyversion)
    state1
}

libraryDependencies += "org.simplemodeling" %% "cozy" % sys.props("cozy.version")

dependencyOverrides ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % "2.1.0",
  "org.scala-lang.modules" %% "scala-parser-combinators" % "2.3.0"
)
