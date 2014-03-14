// Comment to get more information during initialization
logLevel := Level.Warn

// The Typesafe repository
resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

resolvers += Resolver.url("jay-vivien", new URL("http://jay.w3.org/~vivien/vs/ivy/"))(Resolver.ivyStylePatterns)

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % System.getProperty("play.version"))

addSbtPlugin("net.tgambet" % "play-requirejs" % "0.1-SNAPSHOT")

//addSbtPlugin("org.ensime" % "ensime-sbt-cmd" % "0.1.0")
