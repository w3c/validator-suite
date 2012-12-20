package org.w3.vs.assertor

import play.api._
import java.io.File
import org.w3.validator._

object LocalValidators extends LocalValidators({
  val configuration = Configuration.load(new File("."))
  val port = configuration.getInt("application.local-validator.port") getOrElse sys.error("application.local-validator.port")
  port
})

class LocalValidators(port: Int) {

  val logger = play.Logger.of(classOf[LocalValidators])

  var validators: Validators = null

  def start(): Unit =
    if (validators == null) {
      try {
        logger.debug("starting on port " + port)
        validators = new Validators(port, new nu.ValidatorNu("/nu"), new org.w3.validator.css.CSSValidator("/css"))
        validators.start()
      } catch { case be: java.net.BindException =>
        logger.debug("already started on port " + port)
      }
    }

  def stop(): Unit =
    if (validators != null) {
      logger.debug("stopping")
      validators.stop()
      validators = null
    }

  object CSSValidator extends CSSValidator("http://localhost:" + port + "/css")

  object ValidatorNu extends ValidatorNu("http://localhost:" + port + "/nu")
  
}
