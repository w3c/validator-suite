// Comment to get more information during initialization
logLevel := Level.Warn

// The Typesafe repository
resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

resolvers += Resolver.url("jay-tgambet", new URL("http://jay.w3.org/~tgambet/ivy/"))(Resolver.ivyStylePatterns)

addSbtPlugin("play" % "sbt-plugin" % "2.2-SNAPSHOT")

addSbtPlugin("net.tgambet" % "play-requirejs" % "0.1-SNAPSHOT")

//addSbtPlugin("org.ensime" % "ensime-sbt-cmd" % "0.1.0")
