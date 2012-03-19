package org.w3.util

import scalaz.Validation
import akka.dispatch.Future

object Pimps {
  
  implicit def wrapValidation[E, S](validation: Validation[E, S]): ValidationW[E, S] = new ValidationW(validation)

  implicit def wrapFuture[T](future: Future[T]): FutureW[T] = new FutureW(future)
  
}
