package org.w3.util

import scalaz.Validation
import akka.dispatch._

class ValidationW[E, S](private val inner: Validation[E, S]) {
  def failMap[EE](f: E => EE): Validation[EE, S] = inner.fail.map(f).validation
  def toFutureValidation(implicit context: ExecutionContext): FutureValidation[E, S] =
    new FutureValidation(Promise.successful(inner))
}
