package org.w3.vs.exception

import org.w3.vs.view.form._
import org.w3.vs.model._
import play.api.mvc.Result

// TODO messages

//sealed trait SuiteException extends Exception
//case object Unknown extends Exception //with SuiteException
case class UnknownJob(id: JobId) extends Exception("UnknownJob") //with SuiteException
case object UnauthorizedJob extends Exception("UnauthorizedJob") //with SuiteException


trait UnauthorizedException
case object UnknownUser extends Exception("UnknownUser") with UnauthorizedException //with SuiteException
case object Unauthenticated extends Exception("Unauthenticated") with UnauthorizedException //with SuiteException


case class NotAcceptableException(supportedTypes: Seq[String]) extends Exception("NotAcceptableException")

case class StoreException(t: Throwable) extends Exception("StoreException") //with SuiteException

case class Unexpected(t: Throwable) extends Exception(t) //with SuiteException

case class InvalidFormException[A <: VSForm](form: A) extends Exception("InvalidFormException")

//case class ForceResult(result: Result) extends Exception("ForceResult carries a Result that can be used by Play")
