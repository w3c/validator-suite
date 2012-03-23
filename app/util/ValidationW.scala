package org.w3.util

import scalaz.Validation
import akka.dispatch._

class ValidationW[E, S](private val inner: Validation[E, S]) {

  def failMap[EE](f: E => EE): Validation[EE, S] = inner.fail.map(f).validation

  /**
   * injects a Validation[E, S] into a FutureValidation[E, S]
   * where the result is supposed to be immediatly available
   */
  def toImmediateValidation(implicit context: ExecutionContext): FutureValidation[E, S, Nothing, NOTSET] =
    FutureValidation(Promise.successful(inner))

  /**
   * injects a Validation[E, S] into a FutureValidation[E, S]
   * where the result can take some time to be computed
   * (typically while interacting with a database)
   */
  def toDelayedValidation(implicit context: ExecutionContext): FutureValidation[E, S, Nothing, NOTSET] =
    FutureValidation(Future(inner))

}
