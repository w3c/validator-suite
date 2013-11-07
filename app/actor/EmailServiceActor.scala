package org.w3.vs.actor

import akka.actor.{Props, OneForOneStrategy, ActorLogging, Actor}
import org.apache.commons.mail.{DefaultAuthenticator, HtmlEmail, EmailException}
import akka.actor.SupervisorStrategy.{Stop, Restart}
import scala.concurrent.duration.FiniteDuration
import org.w3.vs.EmailMessage
import akka.event.slf4j.Slf4jEventHandler
/**
 * Modified from: http://raulraja.com/post/40997612883/sending-email-with-scala-and-akka-actors
 */

/*
 * Copyright (C) 2012 47 Degrees, LLC
 * http://47deg.com
 * hello@47deg.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Smtp config
 * @param tls if tls should be used with the smtp connections
 * @param ssl if ssl should be used with the smtp connections
 * @param port the smtp port
 * @param host the smtp host name
 * @param credentials the smtp user
 */
case class SmtpConfig (
  tls : Boolean = false,
  ssl : Boolean = false,
  port : Int = 25,
  host : String,
  credentials: Option[(String, String)])

/**
 * An Email sender actor that sends out email messages
 * Retries delivery up to 10 times every 5 minutes as long as it receives
 * an EmailException, gives up at any other type of exception
 */
class EmailServiceActor(smtpConfig: SmtpConfig) extends Actor with ActorLogging {

  /**
   * The actor supervisor strategy attempts to send email up to 10 times if there is a EmailException
   */
  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10) {
      case emailException: EmailException => {
        log.warning("Restarting after receiving EmailException : {}", emailException.getMessage)
        Restart
      }
      case unknownException: Exception => {
        log.error(unknownException, "Giving up. Can you recover from this?")
        Stop
      }
      case unknownCase: Throwable => {
        log.error(unknownCase, "Giving up on unexpected case")
        Stop
      }
    }

  /**
   * Forwards messages to child workers
   */
  def receive = {
    case message: Any => context.actorOf(Props(new EmailServiceWorker(smtpConfig))) ! message
  }

}

/**
 * Email worker that delivers the message
 */
class EmailServiceWorker(smtpConfig: SmtpConfig) extends Actor with ActorLogging {

  /**
   * The email message in scope
   */
  private var emailMessage: Option[EmailMessage] = None
  private var deliveryAttempts: Int = 0

  /**
   * Delivers a message
   */
  def receive = {
    case email: EmailMessage => {
      emailMessage = Option(email)
      deliveryAttempts += 1
      log.debug("Atempting to deliver message")
      sendEmailSync(email)
      log.debug("Message delivered")
    }
    case attempts: Int => deliveryAttempts = attempts
    case unexpectedMessage: Any => {
      log.warning("Received unexepected message : {}", unexpectedMessage)
      throw new Exception("can't handle %s".format(unexpectedMessage))
    }
  }

  private def sendEmailSync(emailMessage: EmailMessage) {

    // Create the email message
    val email = new HtmlEmail()
    email.setTLS(smtpConfig.tls)
    email.setSSL(smtpConfig.ssl)
    email.setSmtpPort(smtpConfig.port)
    email.setHostName(smtpConfig.host)
    smtpConfig.credentials.map{ creds =>
      email.setAuthenticator(new DefaultAuthenticator(creds._1, creds._2))
    }
    email.setHtmlMsg(emailMessage.html)
      .setTextMsg(emailMessage.text)
      .addTo(emailMessage.recipient)
      .setFrom(emailMessage.from)
      .setSubject(emailMessage.subject)
      .send()
  }

  /**
   * If this child has been restarted due to an exception attempt redelivery
   * based on the message configured delay
   */
  override def preRestart(reason: Throwable, message: Option[Any]) {
    if (emailMessage.isDefined) {
      log.info("Scheduling email message to be sent after attempts: {}", deliveryAttempts)
      import context.dispatcher // Use this Actors' Dispatcher as ExecutionContext
      self ! deliveryAttempts
      context.system.scheduler.scheduleOnce(FiniteDuration(10, "minutes"), self, emailMessage.get)
    }
  }

  override def postStop() {
    if (emailMessage.isDefined) {
      log.info(s"Stopped child email worker after attempts: {} - {} - {}", deliveryAttempts, emailMessage.get.recipient, emailMessage.get.subject)
    }
  }

}
