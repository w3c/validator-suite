// Comment to get more information during initialization
logLevel := Level.Warn

// The Typesafe repository
resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("play" % "sbt-plugin" % "2.1-SNAPSHOT")

resolvers += "Scala-Tools Maven2 Snapshots Repository" at "http://scala-tools.org/repo-snapshots"

// addSbtPlugin("org.ensime" % "ensime-sbt-cmd" % "0.0.10")

libraryDependencies +=
 Defaults.sbtPluginExtra(
   "org.ensime" % "ensime-sbt-cmd" % "0.0.10",
   "0.11.2",
   "2.9.1"
 )
