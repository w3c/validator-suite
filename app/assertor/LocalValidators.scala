package org.w3.vs.assertor

import play.api._
import java.io.File
import org.w3.validator._
import org.w3.vs.web.Cache
import java.lang.reflect.{ Field, Modifier }
import org.apache.commons.httpclient.CacheOnlyHttpClient

object LocalValidators extends LocalValidators ({
  val configuration = Configuration.load(new File("."))
  val port = configuration.getInt("application.assertor.local-validator.port") getOrElse sys.error("application.assertor.local-validator.port")
  val cacheOpt = Cache(configuration)
  (port, cacheOpt)
}) {

  val logger = play.Logger.of(classOf[LocalValidators])

  /* http://stackoverflow.com/questions/3301635/change-private-static-final-field-using-java-reflection */
  def setFinalStatic(field: Field, newValue: Any): Unit = {
    field.setAccessible(true)
    val modifiersField: Field = classOf[Field].getDeclaredField("modifiers")
    modifiersField.setAccessible(true)
    modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL)
    field.set(null, newValue)
  }

}

class LocalValidators(port: Int, cacheOpt: Option[Cache]) {

  def this(args: (Int, Option[Cache])) = this(args._1, args._2)

  import LocalValidators.{ logger, setFinalStatic }

  var validators: Validators = null

  def start(): Unit =
    if (validators == null) {
      // activate the cache
      cacheOpt foreach { cache =>
        cache.setAsDefaultCache()
        // make sure that validator.nu will use the cache, always
//        setFinalStatic(
//          classOf[_root_.nu.validator.xml.PrudentHttpEntityResolver].getDeclaredField("client"),
//          new CacheOnlyHttpClient(cache))
      }
      // start the validators
      try {
        logger.info("starting local assertors server on port " + port)
        validators = new Validators(port, new nu.ValidatorNu("/nu"), new org.w3.validator.css.CSSValidator("/css"))
        validators.start()
      } catch { case be: java.net.BindException =>
        logger.warn("already started on port " + port)
      }
    }

  def stop(): Unit =
    if (validators != null) {
      cacheOpt foreach { _.restorePreviousCache() }
      logger.info("stopping local assertors server")
      validators.stop()
      validators = null
    }

  object CSSValidator extends CSSValidator("http://localhost:" + port + "/css/")

  object ValidatorNu extends ValidatorNu("http://localhost:" + port + "/nu/")
  
}
