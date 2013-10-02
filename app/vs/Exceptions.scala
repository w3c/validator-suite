package org.w3.vs.exception

import org.w3.vs.view.form._
import org.w3.vs.model._
import play.api.mvc.Result

// TODO messages

//sealed trait SuiteException extends Exception
//case object Unknown extends Exception //with SuiteException
case class UnknownJob(id: JobId) extends Exception("UnknownJob") //with SuiteException
case object UnauthorizedJob extends Exception("UnauthorizedJob") //with SuiteException


trait UnauthorizedException {
  def email: String
}
object UnauthorizedException {
  def unapply(o: UnauthorizedException): Option[String] = o match {
    case UnknownUser(email) => Some(email)
    case Unauthenticated(email) => Some(email)
    case _ => None
  }
}

case class UnknownUser(email: String) extends Exception("UnknownUser") with UnauthorizedException //with SuiteException
case class Unauthenticated(email: String) extends Exception("Unauthenticated") with UnauthorizedException //with SuiteException

case class DuplicatedEmail(email: String) extends Exception(s"${email} already in use")

//case class NotAcceptableException(supportedTypes: Seq[String]) extends Exception("NotAcceptableException")

case class StoreException(t: Throwable) extends Exception("StoreException") //with SuiteException

//case class Unexpected(t: Throwable) extends Exception(t) //with SuiteException

case class InvalidFormException[A <: VSForm](form: A, userOpt: Option[User] = None) extends Exception("InvalidFormException")

// Contrary to UnauthorizedException this won't ask the user to log in but simply respond with a 404
case object AccessNotAllowed extends Exception("AccessNotAllowed")
case class PaymentRequired(job: Job) extends Exception("PaymentRequired")

//case class ForceResult(result: Result) extends Exception("ForceResult carries a Result that can be used by Play")
