ThisBuild / scalaVersion := "3.3.8"

resolvers += Resolver.defaultLocal
resolvers += Resolver.file("Local Ivy", file(Path.userHome.absolutePath + "/.ivy2/local"))(Resolver.ivyStylePatterns)
resolvers += "Local Maven Repository" at ("file://" + Path.userHome.absolutePath + "/.m2/repository")
resolvers += "SimpleModeling.org" at "https://www.simplemodeling.org/repository/maven"

libraryDependencies += "org.goldenport" %% "goldenport-cncf" % "0.5.1-SNAPSHOT"
