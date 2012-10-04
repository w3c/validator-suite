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
    "org.apache.commons" % "commons-lang3" % "3.1" intransitive(), // For StringUtils escaping functions
    "nu.validator.htmlparser" % "htmlparser" % "1.2.1" intransitive(),
    "com.codecommit" %% "anti-xml" % "0.4-SNAPSHOT" from "http://repo.typesafe.com/typesafe/scala-tools-snapshots/com/codecommit/anti-xml_2.9.1/0.4-SNAPSHOT/anti-xml_2.9.1-0.4-SNAPSHOT.jar",
    "net.databinder.dispatch" %% "core" % "0.9.0",
    "org.scalaz" %% "scalaz-core" % "7.0-SNAPSHOT",
    //"org.w3" %% "banana-sesame" % "x03-SNAPSHOT",
    "org.w3" %% "banana-jena" % "x09-SNAPSHOT",
    "org.w3" %% "css-validator-standalone" % "1.1-SNAPSHOT" intransitive(),
    "org.eclipse.jetty" % "jetty-webapp" % "8.0.1.v20110908" % "compile",
    "javax.servlet" % "servlet-api" % "2.5" % "provided",
    // "com.github.scala-incubator.io" %% "scala-io-core" % "0.4.1-seq",
    // "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.1-seq",
    // test dependencies
    "com.typesafe.akka" % "akka-testkit" % "2.0.2" % "test",
    "org.scalatest" %% "scalatest" % "1.7.1" % "test"
  )

//  val assertorApi = Project("assertor-api", file("assertor-api"))

//  lazy val bananaRdf = ProjectRef(uri("file:///home/betehess/projects/banana-rdf"), "banana-jena")
//  lazy val bananaRdf = ProjectRef(uri("file:///home/betehess/projects/banana-rdf"), "banana-sesame")

  val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
    scalaVersion := "2.9.2",
    testOptions in Test := Nil,
    scalacOptions ++= Seq("-Ydependent-method-types"),
    // activates full stacktrace and durations
    testOptions in Test += Tests.Argument("-oDF"),
    testOptions in Test += Tests.Argument("-l", "org.w3.vs.util.SlowTest"),
    routesImport += "org.w3.vs.controllers._",
    routesImport += "org.w3.vs.model._",
    playAssetsDirectories <+= baseDirectory / "app/assets/scripts",
    coffeescriptEntryPoints := Seq.empty[File],
    javascriptEntryPoints := Seq.empty[File],
    templatesImport += "org.w3.vs.view._",
    templatesImport += "org.w3.vs.view.form._",
    templatesImport += "org.w3.vs.view.model._",
    templatesImport += "org.w3.vs.model._",
    templatesImport += "org.w3.vs.exception._",
    templatesImport += "scalaz.{Validation, Failure, Success}",
    logLevel := Level.Debug,
    resolvers += "Sonatype Nexus Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
//    resolvers += "repo.codahale.com" at "http://repo.codahale.com",
//    resolvers += "apache-repo-releases" at "http://repository.apache.org/content/repositories/releases/"
    // resolvers += "sesame-repo-releases" at "http://repo.aduna-software.org/maven2/releases/"

    // ensimeConfig := sexp(
    //   key(":compiler-args"), sexp("-Ywarn-dead-code", "-Ywarn-shadowing"),
    //   key(":formatting-prefs"), sexp(
    //     key(":rewriteArrowSymbols"), false,
    //     key(":doubleIndentClassDeclaration"), true
    //   )
    // )
  ) //dependsOn (bananaRdf)

}
