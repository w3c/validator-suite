package org.w3.util

import java.util.concurrent.TimeoutException

import akka.dispatch.Await
import akka.dispatch.ExecutionContext
import akka.dispatch.Future
import akka.dispatch.{Promise => AkkaPromise}
import akka.util.Duration
import scalaz.Validation._
import scalaz.Scalaz._
import scalaz._
import org.w3.util.Pimps._

object FutureVal {
  
  def apply[F, S](body: => S)(implicit context: ExecutionContext, onThrowable: Throwable => F): FutureVal[F, S]  = {
    FutureVal.applyTo(Future{body})
  }
  
  def pure[F, S](body: => S)(implicit context: ExecutionContext, onThrowable: Throwable => F): FutureVal[F, S] = {
    FutureVal.fromValidation(fromTryCatch{body}.failMap(onThrowable(_)))
  }
  
  def successful[F, S](success: S)(implicit context: ExecutionContext, onTimeout: TimeoutException => F): FutureVal[F, S] = {
    new FutureVal(AkkaPromise.successful(Success(success)))
  }
  
  def failed[F, S](failure: F)(implicit context: ExecutionContext, onTimeout: TimeoutException => F): FutureVal[F, S] = {
    new FutureVal(AkkaPromise.successful(Failure(failure)))
  }
  
  def fromValidation[F, S](validation: Validation[F, S])(implicit context: ExecutionContext, onTimeout: TimeoutException => F): FutureVal[F, S] = {
    new FutureVal(AkkaPromise.successful(validation))
  }
  
  def applyTo[F, S](future: Future[S])(implicit context: ExecutionContext, onThrowable: Throwable => F): FutureVal[F, S] = {
    new FutureVal(future.map[Validation[F, S]] { value =>
        Success(value)
      } recover { case t: Throwable =>
        Failure(onThrowable(t))
      })
  }
  
  def sequence[S](iterable: Iterable[Future[S]])(implicit executor: ExecutionContext): FutureVal[Throwable, Iterable[S]] = {
    FutureVal.applyTo(Future.sequence(iterable))
  }
  
}


class FutureVal[+F, +S] (private val future: Future[Validation[F, S]])(implicit val onTimeout: TimeoutException => F, executor: ExecutionContext) {
  
  def asFuture: Future[Validation[F, S]] = future
  
  def isCompleted: Boolean = future.isCompleted
  
  def map[S1](f: S => S1): FutureVal[F, S1] = pureFold(fail => fail, f)
  
  def failMap[F1](f: F => F1): FutureVal[F1, S] = pureFold(f, success => success)
  
  def pureFold[F1, S1](f1: F => F1, f2: S => S1): FutureVal[F1, S1] = {
    new FutureVal(future.map {
      case Failure(failure) => Failure(f1(failure))
      case Success(success) => Success(f2(success))
    })(timeout => f1(onTimeout(timeout)), executor)
  }
  
  def flatMap[FF >: F, S1](f: S => FutureVal[FF, S1]): FutureVal[FF, S1] = {
    new FutureVal(future.flatMap[Validation[FF, S1]] {
      case Failure(failure) => AkkaPromise.successful(Failure(failure))
      case Success(value) => f(value).asFuture
    })
  }
  
  def foreach(f: S => Unit): Unit = future foreach { _ foreach f }
  
  def value: Option[Validation[F, S]] = { 
    future.value.fold (
      either => either.fold(
          _ => sys.error("The inner future of a FutureVal cannot have a value of Left[Throwable]."),
          vali => vali.fold(
              failure => Some(Failure(failure)),
              success => Some(Success(success))
            )
        ),
      None
    )
    /* i.e:
    future.value match {
      case Some(Right(vali)) => vali match {
        case Success(a) => Some(Success(a))
        case Failure(b) => Some(Failure(b))
      }
      case Some(Left(t)) => sys.error("The inner future of a FutureVal cannot have a value of Left[Throwable].")
      case None => None
    }
    */
  }

  def await(atMost: Duration): Option[Validation[F, S]] = {
    try {
      Some(Await.result(future, atMost))
    } catch {
      case timeout: TimeoutException => None
    }
  }
  
  def result(atMost: Duration): Validation[F, S] = {
    try {
      Await.result(future, atMost)
    } catch {
      case timeout: TimeoutException => Failure(onTimeout(timeout))
    }
  }
  
  def waitAnd[X](atMost: Duration, f: => X = ()): FutureVal[F, S] = {
    try {
      Await.result(future, atMost)
      f; this
    } catch {
      case e => {f; this} 
    }
  }
  
  def readyIn(atMost: Duration): FutureVal[F, S] = {
    FutureVal.fromValidation(
      try {
        Await.result(future, atMost)
      } catch {
        case timeout: TimeoutException => Failure(onTimeout(timeout))
      }
    )
  }

//  def toVSPromise[T](implicit evF: F <:< T, evS: S <:< T): VSPromise[T] = {
//    VSPromise(this.pureFold(f => evF(f), s => evS(s)))
//  }
  
  def onRedeem[T](pf: PartialFunction[Validation[F, S], T]): this.type = {
    future.onSuccess(pf)
    this
  }
  
  def onSuccess[T](pf: PartialFunction[S, T]): this.type = {
    this.onRedeem{case Success(s) => pf(s)}
  }
  
  def onFailure[T](pf: PartialFunction[F, T]): this.type = {
    this.onRedeem{case Failure(f) => pf(f)}
  }
  
}

