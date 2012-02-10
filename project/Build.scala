import sbt._
import Keys._
import PlayProject._

object ApplicationBuild extends Build {

  val appName         = "validator-suite"
  val appVersion      = "0.2"

  val appDependencies = Seq(
    // runtime dependencies
    "nu.validator.htmlparser" % "htmlparser" % "1.2.1" intransitive(),
    "com.codecommit" %% "anti-xml" % "0.4-SNAPSHOT" from "http://scala-tools.org/repo-snapshots/com/codecommit/anti-xml_2.9.1/0.4-SNAPSHOT/anti-xml_2.9.1-0.4-SNAPSHOT.jar",
    "org.joda" % "joda-convert" % "1.1" intransitive(),
    "net.databinder" %% "dispatch-http" % "0.8.6",
    "com.novus" %% "salat-core" % "0.0.8-SNAPSHOT",
    // test dependencies
    "com.typesafe.akka" % "akka-testkit" % "2.0-M2" % "test",
    "net.databinder" %% "unfiltered-filter" % "0.5.3" % "test",
    "net.databinder" %% "unfiltered-jetty" % "0.5.3" % "test"
  )

  val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
    resolvers += "repo.novus snaps" at "http://repo.novus.com/snapshots/",
    resolvers += "Sonatype Nexus Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
    resolvers += "repo.codahale.com" at "http://repo.codahale.com"
    // Add your own project settings here      
  )
  
}
