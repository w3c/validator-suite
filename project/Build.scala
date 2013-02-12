import sbt._
import Keys._
import play.Project.{ fork => _, _ }
//import org.ensime.sbt.Plugin.Settings.ensimeConfig
//import org.ensime.sbt.util.SExp._

object ApplicationBuild extends Build {

  val appName         = "validator-suite"
  val appVersion      = "0.2"

  val akkaVersion = "2.1.0"
  val scalazVersion = "7.0.0-M7"
  val scalatestVersion = "2.0.M5b"
  val metricsVersion = "2.1.3"

  val appDependencies = Seq(
    // runtime dependencies
    "org.scala-lang" % "scala-actors" % "2.10.0",
    "org.apache.commons" % "commons-lang3" % "3.1" intransitive(), // For StringUtils escaping functions
    "nu.validator.htmlparser" % "htmlparser" % "1.2.1" intransitive(),
    "com.codecommit" %% "anti-xml" % "0.4-SNAPSHOT" from "http://jay.w3.org/~bertails/jar/anti-xml_2.10_20130110.jar",
    "org.w3" % "validators" % "1.0-SNAPSHOT" from "http://jay.w3.org/~bertails/jar/validators-20130110.jar",
    "com.yammer.metrics" % "metrics-core" % metricsVersion excludeAll(ExclusionRule(organization = "org.slf4j")),
    "com.yammer.metrics" % "metrics-graphite" % metricsVersion excludeAll(ExclusionRule(organization = "org.slf4j")),
    "org.reactivemongo" % "play2-reactivemongo_2.10" % "0.9-SNAPSHOT" /*cross CrossVersion.full*/ excludeAll(ExclusionRule(organization = "io.netty"), ExclusionRule(organization = "play")),
    "org.mongodb" % "mongo-java-driver" % "2.10.0", // should disappear soon
    "org.scalaz" %% "scalaz-core" % scalazVersion,
    // "org.mindrot" % "jbcrypt" % "0.3m",
    // test dependencies
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
    "com.typesafe.akka" %% "akka-dataflow" % akkaVersion,
    "org.scalatest" %% "scalatest" % scalatestVersion
  )

//  val assertorApi = Project("assertor-api", file("assertor-api"))

  val main = play.Project(appName, appVersion, appDependencies).settings(

//    autoCompilerPlugins := true,
//    libraryDependencies <+= scalaVersion {
//      v => compilerPlugin("org.scala-lang.plugins" % "continuations" % "2.10.0-RC1")
//    },
    libraryDependencies += "commons-io" % "commons-io" % "2.4",

    // activates full stacktrace and durations
    testOptions in Test := Nil,
    testOptions in Test += Tests.Argument("""-oDF"""),

    scalacOptions ++= Seq("-deprecation", "-unchecked", /* "-optimize",*/ "-feature", "-language:implicitConversions,higherKinds,reflectiveCalls"),
    routesImport += "org.w3.vs.controllers._",
    routesImport += "org.w3.vs.model._",
    playAssetsDirectories <+= baseDirectory / "app/assets/scripts",
    coffeescriptEntryPoints := Seq.empty[File],
    javascriptEntryPoints := Seq.empty[File],
    templatesImport += "org.w3.vs.view._",
    templatesImport += "org.w3.vs.view.form._",
    templatesImport += "org.w3.vs.view.model._",
    templatesImport += "org.w3.vs.view.collection._",
    templatesImport += "org.w3.vs.model._",
    templatesImport += "org.w3.vs.exception._",
    templatesImport += "scala.util._",
    logLevel := Level.Debug,
    resolvers += "Sonatype Nexus Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
//    resolvers += "sgodbillon" at "https://bitbucket.org/sgodbillon/repository/raw/master/snapshots/"
    resolvers += "jay-bertails" at "http://jay.w3.org/~bertails/ivy/"
//    resolvers += "repo.codahale.com" at "http://repo.codahale.com",
//    resolvers += "apache-repo-releases" at "http://repository.apache.org/content/repositories/releases/"
//    ensimeConfig := sexp(
//      key(":compiler-args"), sexp("-Ywarn-dead-code", "-Ywarn-shadowing"),
//      key(":formatting-prefs"), sexp(
//        key(":rewriteArrowSymbols"), false,
//        key(":doubleIndentClassDeclaration"), false
//      )
//    )
  )

}
