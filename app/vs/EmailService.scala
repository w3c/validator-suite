package org.w3.vs

import org.w3.vs.actor._
import akka.actor._
import akka.routing.SmallestMailboxRouter

trait EmailService extends ValidatorSuite { this: ValidatorSuite =>

  private object conf {
    val (tls, ssl, port, host, credentials) = (for {
      smtp <- config.getConfig("vs.smtp")
      tls <- smtp.getBoolean("tls")
      ssl <- smtp.getBoolean("ssl")
      port <- smtp.getInt("port")
      host <- smtp.getString("host")
    } yield {
      val credentials = for {
        user <- smtp.getString("user")
        password <- smtp.getString("password")
      } yield (user, password)
      (tls, ssl, port, host, credentials)
    }) getOrElse {
      throw config.globalError("Incomplete vs.smtp configuration object")
    }
  }

  val smtpConfig = SmtpConfig(conf.tls, conf.ssl, conf.port, conf.host, conf.credentials)

  /**
   * Uses the smallest inbox strategy to keep 20 instances alive ready to send out email
   * @see SmallestMailboxRouter
   */
  val emailServiceActor: ActorRef = system.actorOf(
    Props(new EmailServiceActor(smtpConfig)).withRouter(
      SmallestMailboxRouter(nrOfInstances = 20)
    ), name = "emailService"
  )

  def sendEmail(emailMessage: EmailMessage) {
    emailServiceActor ! emailMessage
  }

}


