import sbt._
import Keys._
import PlayProject._
import org.ensime.sbt.Plugin.Settings.ensimeConfig
import org.ensime.sbt.util.SExp._

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
    "org.scalaz" %% "scalaz-core" % "7.0-SNAPSHOT",
    // test dependencies
    "com.typesafe.akka" % "akka-testkit" % "2.0.1" % "test",
    "net.databinder" %% "unfiltered-filter" % "0.5.3" % "test",
    "net.databinder" %% "unfiltered-jetty" % "0.5.3" % "test",
    "org.scalatest" %% "scalatest" % "1.7.1" % "test"
  )

  val assertorApi = Project("assertor-api", file("assertor-api"))

//  val bananaRdf = ProjectRef(uri("git://github.com/w3c/banana-rdf.git"), "jena")

  val bananaRdf = ProjectRef(uri("file:///home/monkey/projects/banana-rdf"), "jena")

  val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
//    scalaVersion := "2.9.1",
    testOptions in Test := Nil,
    routesImport += "org.w3.vs.controllers._",
    routesImport += "org.w3.vs.model._",
    templatesImport += "org.w3.vs.model._",
    templatesImport += "org.w3.vs.exception._",
    templatesImport += "scalaz.{Validation, Failure, Success}",
    resolvers += "repo.novus snaps" at "http://repo.novus.com/snapshots/",
    resolvers += "Sonatype Nexus Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
    resolvers += "repo.codahale.com" at "http://repo.codahale.com"//,
    // ensimeConfig := sexp(
    //   key(":compiler-args"), sexp("-Ywarn-dead-code", "-Ywarn-shadowing"),
    //   key(":formatting-prefs"), sexp(
    //     key(":rewriteArrowSymbols"), false,
    //     key(":doubleIndentClassDeclaration"), true
    //   )
    // )
  ) dependsOn (bananaRdf)
  
}
