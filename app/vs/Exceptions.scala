package org.w3.vs.exception

sealed trait SuiteException
case object UnknownJob extends SuiteException
case object UnauthorizedJob extends SuiteException
case object UnknownUser extends SuiteException
case object Unauthenticated extends SuiteException
case class Timeout(t: Throwable) extends SuiteException
case class StoreException(t: Throwable) extends SuiteException