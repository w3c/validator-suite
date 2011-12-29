import sbt._
import Keys._
import PlayProject._

object ApplicationBuild extends Build {

  val appName         = "validator-suite"
  val appVersion      = "0.2"

  val appDependencies = Seq(
    "com.ning" % "async-http-client" % "1.6.2" intransitive(),
    "nu.validator.htmlparser" % "htmlparser" % "1.2.1" intransitive(),
    "com.codecommit" %% "anti-xml" % "0.4-SNAPSHOT" from "http://scala-tools.org/repo-snapshots/com/codecommit/anti-xml_2.9.1/0.4-SNAPSHOT/anti-xml_2.9.1-0.4-SNAPSHOT.jar",
    "org.joda" % "joda-convert" % "1.1" intransitive(),
    "net.databinder" %% "dispatch-http" % "0.8.6",
    "se.scalablesolutions.akka" % "akka-typed-actor" % "1.3-RC2",
    "se.scalablesolutions.akka" % "akka-testkit" % "1.3-RC2" % "test"
  )

  val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
    // Add your own project settings here      
  )
  
}
