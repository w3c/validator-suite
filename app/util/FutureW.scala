package org.w3.util

import akka.dispatch._
import scalaz._
import Scalaz._

class FutureW[S](future: Future[S]) {

  def lift[F]: FutureValidation[Throwable, S] = {
    val lifted = future.map[Validation[Throwable, S]] { value =>
      Success(value)
    } recover { case t: Throwable =>
      Failure(t)
    }
    new FutureValidation(lifted)
  }

  def lift[F](f: PartialFunction[Throwable, F]): FutureValidation[F, S] = {
    val lifted = future.map[Validation[F, S]] { value =>
      Success(value)
    } recover { case t: Throwable =>
      Failure(f(t))
    }
    new FutureValidation(lifted)
  }
}
