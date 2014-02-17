import sbt._
import Keys._
import play.Project.{fork => _, _}
import net.tgambet.requirejs.RequireJsPlugin._

//import org.ensime.sbt.Plugin.Settings.ensimeConfig
//import org.ensime.sbt.util.SExp._

object ApplicationBuild extends Build {

  val appName = "validator-suite"
  val appVersion = "0.2"

  val akkaVersion = "2.2.3"
  val scalazVersion = "7.0.5"
  val scalatestVersion = "2.0"
  val metricsVersion = "3.0.0"

  val appDependencies = Seq(
    // runtime dependencies
    cache,
    "org.scala-lang" % "scala-actors" % "2.10.2",
    "org.apache.commons" % "commons-lang3" % "3.1" intransitive(), // For StringUtils escaping functions
    "com.codecommit" %% "anti-xml" % "0.4-SNAPSHOT" from "http://jay.w3.org/~vivien/vs/jar/anti-xml_2.10_20130110.jar",
    "org.w3" % "validators" % "20131211" from "http://jay.w3.org/~vivien/vs/jar/validators-20131211.jar",
    "com.codahale.metrics" % "metrics-core" % metricsVersion excludeAll (ExclusionRule(organization = "org.slf4j")),
    "com.codahale.metrics" % "metrics-graphite" % metricsVersion excludeAll (ExclusionRule(organization = "org.slf4j")),
    "org.reactivemongo" %% "play2-reactivemongo" % "0.10.0" /*cross CrossVersion.full*/ excludeAll(ExclusionRule(organization = "io.netty"), ExclusionRule(organization = "com.typesafe.play")),
    "org.scalaz" %% "scalaz-core" % scalazVersion,
    "org.mindrot" % "jbcrypt" % "0.3m",
    "commons-io" % "commons-io" % "2.4",
    "org.apache.commons" % "commons-email" % "1.2",
    "com.typesafe.akka" %% "akka-slf4j"  % akkaVersion,
    // test dependencies
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
    "org.scalatest" %% "scalatest" % scalatestVersion % "test"
  )

  //  val assertorApi = Project("assertor-api", file("assertor-api"))

  val main = play.Project(appName, appVersion, appDependencies).settings(
    scalaVersion := "2.10.3",

    // activates full stacktrace and durations
    testOptions in Test := Nil,
    testOptions in Test += Tests.Argument( """-oDF"""),

    scalacOptions ++= Seq("-deprecation", "-unchecked", /* "-optimize",*/ "-feature", "-language:implicitConversions,higherKinds,reflectiveCalls"),
    scalacOptions in(Compile, doc) ++= Opts.doc.title("W3C Validator Suite"),
    scalacOptions in(Compile, doc) <++= baseDirectory map {
      bd => Seq("-sourcepath", bd.getAbsolutePath, "-doc-source-url", "https://github.com/w3c/validator-suite/tree/master€{FILE_PATH}.scala")
    },

    routesImport += "org.w3.vs.controllers._",
    routesImport += "org.w3.vs.model._",
    playAssetsDirectories <+= baseDirectory / "app/assets/js",
    coffeescriptEntryPoints := Seq.empty[File],
    javascriptEntryPoints := Seq.empty[File],
    templatesImport += "org.w3.vs.ValidatorSuite",
    templatesImport += "org.w3.vs.view._",
    templatesImport += "org.w3.vs.view.Forms._",
    templatesImport += "org.w3.vs.view.model._",
    templatesImport += "org.w3.vs.view.collection._",
    templatesImport += "org.w3.vs.model._",
    templatesImport += "org.w3.vs.exception._",
    templatesImport += "scala.util._",

    logLevel := Level.Debug,
    resolvers += "Sonatype Nexus Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
    //    resolvers += "sgodbillon" at "https://bitbucket.org/sgodbillon/repository/raw/master/snapshots/"
    //    resolvers += "jay-bertails" at "http://jay.w3.org/~bertails/ivy/"
    //    resolvers += "repo.codahale.com" at "http://repo.codahale.com",
    //    resolvers += "apache-repo-releases" at "http://repository.apache.org/content/repositories/releases/"
    //    ensimeConfig := sexp(
    //      key(":compiler-args"), sexp("-Ywarn-dead-code", "-Ywarn-shadowing"),
    //      key(":formatting-prefs"), sexp(
    //        key(":rewriteArrowSymbols"), false,
    //        key(":doubleIndentClassDeclaration"), false
    //      )
    //    )
  ).settings(requireBaseSettings: _*)
    .settings(
    RequireJS.baseDir <<= (baseDirectory)(_ / "project" / "target"),
    RequireJS.sourceDir <<= (baseDirectory)(_ / "app" / "assets" / "js"),
    RequireJS.targetDir <<= (resourceManaged in Compile)(_ / "public" / "js"),
    RequireJS.cacheFile <<= cacheDirectory(_ / "requirejs"),
    javascriptEntryPoints := PathFinder.empty
  )

}
