package org.w3.util

import akka.dispatch.Future
import scalaz.Validation

class FutureValidationW[F, S](futureValidation: Future[Validation[F, S]]) {

  def toFutureValidation: FutureValidation[F, S, Nothing, NOTSET] = FutureValidation(futureValidation)

}
