package org.w3.vs.exception

import org.w3.vs.model._

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

trait CouponException {
  def code: String
  def msg: String
}
object CouponException {
  def unapply(o: CouponException): Option[(String, String)] = Some((o.code, o.msg))

}
case class DuplicateCouponException(code: String) extends Exception("coupon.error.duplicate") with CouponException{
  val msg: String = "error.duplicate"
}
case class AlreadyUsedCouponException(code: String) extends Exception("coupon.error.alreadyUsed") with CouponException{
  val msg: String = "error.alreadyUsed"
}
case class NoSuchCouponException(code: String) extends Exception("coupon.error.notFound") with CouponException{
  val msg: String = "error.notFound"
}
case class ExpiredCouponException(code: String) extends Exception("coupon.error.expired") with CouponException{
  val msg: String = "error.expired"
}
case class InvalidSyntaxCouponException(code: String) extends Exception("coupon.error.syntax") with CouponException{
  val msg: String = "error.syntax"
}
case class StoreException(t: Throwable) extends Exception("StoreException")

// Contrary to UnauthorizedException this won't ask the user to log in but simply respond with a 404
case class AccessNotAllowed(msg: String = "You do not have permission to access the requested resource.") extends Exception("AccessNotAllowed")
case class PaymentRequired(job: Job) extends Exception("PaymentRequired")
