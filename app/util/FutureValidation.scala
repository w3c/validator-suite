package org.w3.util

import akka.dispatch._
import scalaz._
import Scalaz._

/**
 * the combination of a Future and a Validation
 */
class FutureValidation[F, S](val futureValidation: Future[Validation[F, S]]) {

  /**
   * combines the computations in the future while respecting the semantics of the Validation
   */
  def flatMap[T](f: S => FutureValidation[F, T])(implicit executor: ExecutionContext): FutureValidation[F, T] = {
    val futureResult = futureValidation.flatMap[Validation[F, T]] {
      case Failure(failure) => Promise.successful(Failure(failure))
      case Success(value) => f(value).futureValidation
    }
    new FutureValidation(futureResult)
  }

  def map[T](f: S => T): FutureValidation[F, T] = 
    new FutureValidation(futureValidation map { _ map f })

  def failMap[T](f: F => T): FutureValidation[T, S] =
    new FutureValidation(futureValidation map { v => new ValidationW(v) failMap f })

//  def sequence[A, M[_] <: Traversable[_]](implicit executor: ExecutionContext, ev: S =:= M[A]): M[Future]

}

