package org.w3.vs

import play.api.{Logger, Mode, Configuration}
import Mode._

class ValidatorSuite(
  mode: Mode,
  name: String = "Validator Suite") {

  val logger: Logger = Logger(name + (mode match {
    case Test => " [Test]"
    case Dev =>  " [Dev]"
    case _ => ""
  }))

  logger.info("Application starting")

  val config: Configuration = {
    import java.io.File
    import com.typesafe.config.{ConfigParseOptions, ConfigFactory}

    def load(fileName: String): Configuration =
      Configuration(ConfigFactory.load(ConfigFactory.parseFileAnySyntax(new File(s"conf/${fileName}"))))

    mode match {
      // will look for application.conf on the class path
      case Prod => Configuration(ConfigFactory.load(ConfigParseOptions.defaults().setAllowMissing(false)))
      case Test => load("application.conf") ++ load("application-test.conf")
      case Dev =>  load("application.conf") ++ load("application-dev.conf")
    }
  }

  def shutdown() { logger.info("Application shut down") }

}









