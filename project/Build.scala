import sbt._
import Keys._
import play.Project._
//import org.ensime.sbt.Plugin.Settings.ensimeConfig
//import org.ensime.sbt.util.SExp._

object ApplicationBuild extends Build {

  val appName         = "validator-suite"
  val appVersion      = "0.2"

  val appDependencies = Seq(
    // runtime dependencies
    "org.scala-lang" % "scala-actors" % "2.10.0-RC1",
    "org.apache.commons" % "commons-lang3" % "3.1" intransitive(), // For StringUtils escaping functions
    "nu.validator.htmlparser" % "htmlparser" % "1.2.1" intransitive(),
//    "com.codecommit" %% "anti-xml" % "0.4-SNAPSHOT" from "http://repo.typesafe.com/typesafe/scala-tools-snapshots/com/codecommit/anti-xml_2.9.1/0.4-SNAPSHOT/anti-xml_2.9.1-0.4-SNAPSHOT.jar",
    "com.codecommit" %% "anti-xml" % "0.4-SNAPSHOT" from "http://jay.w3.org/~bertails/jar/anti-xml_2.10-0.4-20121107.jar",
    "com.yammer.metrics" % "metrics-core" % "2.1.3" excludeAll(ExclusionRule(organization = "org.slf4j")),
    "com.yammer.metrics" % "metrics-graphite" % "2.1.3" excludeAll(ExclusionRule(organization = "org.slf4j")),
    "play.modules.reactivemongo" %% "play2-reactivemongo" % "0.1-SNAPSHOT"  cross CrossVersion.full,
    "org.scalaz" % "scalaz-core_2.10.0-M7" % "7.0.0-M3",
    "org.w3" % "validators" % "1.0-SNAPSHOT" from "http://jay.w3.org/~bertails/jar/validators-20121107-2.jar",
//    "org.w3" % "validators" % "1.0-SNAPSHOT" from "file:///home/betehess/projects/validators/target/validators.jar",
    // test dependencies
    "com.typesafe.akka" % "akka-testkit_2.10.0-RC1" % "2.1.0-RC1" % "test",
    "com.typesafe.akka" % "akka-dataflow_2.10.0-RC1" % "2.1.0-RC1",
//    "com.typesafe.akka" % "akka-testkit" % "2.0.2" % "test",
    "org.scalatest" % "scalatest_2.10.0-RC1" % "2.0.M4-2.10.0-RC1-B1"
  )

//  val assertorApi = Project("assertor-api", file("assertor-api"))

  val main = play.Project(appName, appVersion, appDependencies).settings(

    autoCompilerPlugins := true,
    libraryDependencies <+= scalaVersion {
      v => compilerPlugin("org.scala-lang.plugins" % "continuations" % "2.10.0-RC1")
    },
    scalacOptions += "-P:continuations:enable",

    testOptions in Test := Nil,
    testOptions in Test += Tests.Argument("""stdout(config="durations")"""),
    scalacOptions ++= Seq("-deprecation", "-unchecked", /* "-optimize",*/ "-feature", "-language:implicitConversions,higherKinds,reflectiveCalls"),
    // activates full stacktrace and durations
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
    resolvers += "sgodbillon" at "https://bitbucket.org/sgodbillon/repository/raw/master/snapshots/"
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
  )

}
