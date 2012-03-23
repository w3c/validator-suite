package org.w3.util

import akka.dispatch._
import scalaz._
import Scalaz._
import java.util.concurrent.TimeUnit
import play.api.mvc.Result
import play.api.libs.concurrent.{Promise => PlayPromise, _}
import annotation.implicitNotFound

abstract class SET
abstract class NOTSET

case class SetTimeOut[T](result: T, duration: Long, unit: TimeUnit)

object FutureValidation {

  def apply[F, S](futureValidation: Future[Validation[F, S]]): FutureValidation[F, S, Nothing, NOTSET] =
    new FutureValidation(futureValidation, None)

}

/**
 * the combination of a Future and a Validation
 */
class FutureValidation[+F, +S, TO, TimeOut] private (
  private val futureValidation: Future[Validation[F, S]],
  timeout: Option[(TO, Long, TimeUnit)]) {

  /**
   * combines the computations in the future while respecting the semantics of the Validation
   */
  def flatMap[FF >: F, T](f: S => FutureValidation[FF, T, TO, TimeOut])(implicit executor: ExecutionContext): FutureValidation[FF, T, TO, TimeOut] = {
    val futureResult = futureValidation.flatMap[Validation[FF, T]] {
      case Failure(failure) => Promise.successful(Failure(failure))
      case Success(value) => f(value).futureValidation
    }
    new FutureValidation(futureResult, timeout)
  }

  def flatMap[T](f: () => SetTimeOut[T]): FutureValidation[F, S, T, SET] = {
    val SetTimeOut(result, duration, unit) = f()
    new FutureValidation[F, S, T, SET](futureValidation, Some((result, duration, unit)))
  }
    

  def map[T](f: S => T): FutureValidation[F, T, TO, TimeOut] = 
    new FutureValidation(futureValidation map { _ map f }, timeout)

  def failMap[T](f: F => T): FutureValidation[T, S, TO, TimeOut] =
    new FutureValidation(futureValidation map { v => new ValidationW(v) failMap f }, timeout)

  def toPromise()(
    implicit ev: TimeOut =:= SET,
    evF: F <:< Result,
    evS: S <:< Result,
    evTO: TO <:< Result): PlayPromise[Result] = toPromiseT[Result]

  def toPromiseT[T](
    implicit ev: TimeOut =:= SET,
    evF: F <:< T,
    evS: S <:< T,
    evTO: TO <:< T): PlayPromise[T] = {
    val Some((result, duration, unit)) = timeout
    val akkaFutureResult = futureValidation map { _.fold(evF, evS) }
    akkaFutureResult.asPromise.orTimeout(result, duration, unit).map(_.fold(f => f, s => s))
  }
  
  def expiresWith[T](
    result: T,
    duration: Long,
    unit: TimeUnit): FutureValidation[F, S, T, SET] =
      new FutureValidation(futureValidation, Some((result, duration, unit)))

  def toFuture[T](
    implicit evF: F <:< T,
    evS: S <:< T): Future[T] = futureValidation.map{ _.fold(evF, evS) }

  // def toPromiseT[T]()(
  //   implicit evF: F <:< T,
  //   evS: S <:< T): PlayPromise[T] = futureValidation.map{ _.fold(evF, evS) }.asPromise

//  def sequence[A, M[_] <: Traversable[_]](implicit executor: ExecutionContext, ev: S =:= M[A]): M[Future]

}

