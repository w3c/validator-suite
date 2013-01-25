package org.w3.vs.assertor

import java.io._
import play.api.Configuration

sealed trait MarkupValidatorConfiguration {
  def serviceUrl: String
}

case class Distant(serviceUrl: String) extends MarkupValidatorConfiguration

case class Local(serviceUrl: String, timeout: Int, binary: File, conf: File) extends MarkupValidatorConfiguration

object MarkupValidatorConfiguration {

  val logger = play.Logger.of(classOf[MarkupValidatorConfiguration])

  def apply(): MarkupValidatorConfiguration = {
    val confPath = "application.assertor.local-validator.markup-validator"
    val conf = Configuration.load(new File(".")).getConfig(confPath) getOrElse sys.error(confPath)
    MarkupValidatorConfiguration(conf)
  }

  def apply(configuration: Configuration): MarkupValidatorConfiguration = {
    val serviceUrl: String = configuration.getString("url") getOrElse sys.error("url")
    val enable: Boolean = configuration.getBoolean("check.enable") getOrElse false
    if (enable) {
      val timeout: Int = configuration.getInt("check.timeout") getOrElse sys.error("check.timeout")
      val binary = new File(configuration.getString("check.binary").get)
      val config = new File(configuration.getString("check.conf").get)
      val ok = binary.isFile && config.isFile
      if (ok) {
        Local(serviceUrl, timeout, binary, config)
      } else {
        logger.warn(s"Issue with the configuration for a local Markup Validator, falling back to ${serviceUrl}")
        Distant(serviceUrl)
      }
    } else {
      Distant(serviceUrl)
    }
  }

}
