package org.w3.util

import akka.dispatch._
import scalaz._
import Scalaz._

/**
 * the combination of a Future and a Validation
 */
class FutureValidation[+F, +S](val futureValidation: Future[Validation[F, S]]) {

  /**
   * combines the computations in the future while respecting the semantics of the Validation
   */
  def flatMap[FF >: F, T](f: S => FutureValidation[FF, T])(implicit executor: ExecutionContext): FutureValidation[FF, T] = {
    val futureResult = futureValidation.flatMap[Validation[FF, T]] {
      case Failure(failure) => Promise.successful(Failure(failure))
      case Success(value) => f(value).futureValidation
    }
    new FutureValidation(futureResult)
  }

  def map[T](f: S => T): FutureValidation[F, T] = 
    new FutureValidation(futureValidation map { _ map f })

  def failMap[T](f: F => T): FutureValidation[T, S] =
    new FutureValidation(futureValidation map { v => new ValidationW(v) failMap f })

  import java.util.concurrent.TimeUnit
  import play.api.mvc.Result
  import play.api.libs.concurrent._

  def toPromise()(
    implicit evF: F <:< Result,
    evS: S <:< Result): Promise[Result] =
      futureValidation.map{ _.fold(evF, evS) }.asPromise

  def toPromiseT[T]()(
    implicit evF: F <:< T,
    evS: S <:< T): Promise[T] =
      futureValidation.map{ _.fold(evF, evS) }.asPromise

  def expiresWith(
    result: Result,
    duration: Long,
    unit: TimeUnit)(
    implicit evF: F <:< Result,
    evS: S <:< Result): Promise[Result] = {
      val akkaFutureResult = futureValidation map { _.fold(evF, evS) }
      akkaFutureResult.asPromise.orTimeout(result, duration, unit).map(_.fold(f => f, s => s))
    }

//  def sequence[A, M[_] <: Traversable[_]](implicit executor: ExecutionContext, ev: S =:= M[A]): M[Future]

}

