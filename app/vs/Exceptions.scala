package org.w3.vs.exception

sealed trait SuiteException extends Exception
case object Unknown extends Exception with SuiteException
case object UnknownJob extends Exception with SuiteException
case object UnauthorizedJob extends Exception with SuiteException
case object UnknownUser extends Exception with SuiteException
case object Unauthenticated extends Exception with SuiteException
case class StoreException(t: Throwable) extends Exception with SuiteException
case class Unexpected(t: Throwable) extends Exception with SuiteException
