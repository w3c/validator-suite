package org.w3.util

import akka.dispatch._
import scalaz._
import Scalaz._

class FutureW[S](future: Future[S]) {

  /**
   * lift a Future[S] to a FutureValidation[Throwable, S] where the potential exception is caught
   * and mapped to a Failure inside the Future
   */
  def lift[F]: FutureValidation[Throwable, S, Nothing, NOTSET] = {
    val lifted = future.map[Validation[Throwable, S]] { value =>
      Success(value)
    } recover { case t: Throwable =>
      Failure(t)
    }
    FutureValidation(lifted)
  }

  /**
   * lift a Future[S] to a FutureValidation[Throwable, S] where the potential exception is caught
   * and mapped to a Failure inside the Future after being passed to the given function
   */
  def liftWith[F](f: PartialFunction[Throwable, F]): FutureValidation[F, S, Nothing, NOTSET] = {
    val lifted = future.map[Validation[F, S]] { value =>
      Success(value)
    } recover { case t: Throwable =>
      Failure(f(t))
    }
    FutureValidation(lifted)
  }
}


