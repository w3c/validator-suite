package org.w3.util

import java.util.concurrent.TimeoutException
import akka.dispatch.Await
import akka.dispatch.ExecutionContext
import akka.dispatch.Future
import akka.dispatch.Promise
import akka.util.Duration
import scalaz.Validation._
import scalaz.Scalaz._
import scalaz._

object FutureVal {
  
  def apply[S](body: => S)(implicit context: ExecutionContext): FutureVal[Throwable, S]  = {
    new FutureVal(Future(fromTryCatch(body)))
  }
  
  def pure[S](body: => S)(implicit context: ExecutionContext): FutureVal[Throwable, S] = {
    new FutureVal(Promise.successful(fromTryCatch(body)))
  }
  
  def applyWith[F, S](body: => S)(onThrowable: Throwable => F)(implicit context: ExecutionContext,
      onTimeout: TimeoutException => F): FutureVal[F, S]  = {
    new FutureVal(Future(fromTryCatch(body).fail.map(t => onThrowable(t)).validation))
  }
  
  def pureWith[F, S](body: => S)(onThrowable: Throwable => F)(implicit context: ExecutionContext,
      onTimeout: TimeoutException => F): FutureVal[F, S]  = {
    new FutureVal(Promise.successful(fromTryCatch(body).fail.map(t => onThrowable(t)).validation))
  }
  
  def applyVal[F, S](body: => Validation[F, S])(onThrowable: Throwable => F)(implicit context: ExecutionContext,
      onTimeout: TimeoutException => F): FutureVal[F, S]  = {
    new FutureVal(Future(fromTryCatch(body).fold(t => Failure(onThrowable(t)), s => s)))
  }
  
  def pureVal[F, S](body: => Validation[F, S])(onThrowable: Throwable => F)(implicit context: ExecutionContext,
      onTimeout: TimeoutException => F): FutureVal[F, S] = {
    new FutureVal(Promise.successful(fromTryCatch(body).fold(t => Failure(onThrowable(t)), s => s)))
  }
  
  def successful[F, S](success: S)(implicit context: ExecutionContext, 
      onTimeout: TimeoutException => F): FutureVal[F, S] = {
    new FutureVal(Promise.successful(Success(success)))
  }
  
  def failed[F, S](failure: F)(implicit context: ExecutionContext,
      onTimeout: TimeoutException => F): FutureVal[F, S] = {
    new FutureVal(Promise.successful(Failure(failure)))
  }
  
  def validated[F, S](validation: Validation[F, S])(implicit context: ExecutionContext,
      onTimeout: TimeoutException => F): FutureVal[F, S] = {
    new FutureVal(Promise.successful(validation))
  }
  
  def fromOption[S](option: Option[S])(implicit context: ExecutionContext): FutureVal[Throwable, S] = {
    option fold (
      s => FutureVal.successful(s),
      FutureVal.failed[Throwable, S](new NoSuchElementException)
    )
  }
  
  def applyTo[S](future: Future[S])(implicit context: ExecutionContext): FutureVal[Throwable, S] = {
    new FutureVal(future.map[Validation[Throwable, S]] { value =>
        Success(value)
      } recover { case t: Throwable =>
        Failure(t)
      })
  }
  
  def sequence[S](iterable: Iterable[Future[S]])(
      implicit context: ExecutionContext): FutureVal[Throwable, Iterable[S]] = {
    FutureVal.applyTo(Future.sequence(iterable))
  }
  
  // TODO sequence with FutureVals
  
  implicit def toFutureValException[A](f: FutureVal[Throwable, A]): FutureVal[Exception, A] =
    f failMap {
      case e: Exception => e
      case error => throw error
    }
  
}

class FutureVal[+F, +S] protected (
    private val future: Future[Validation[F, S]])(
    implicit val timeout: Function1[TimeoutException, F], context: ExecutionContext) {
  
  def asFuture: Future[Validation[F, S]] = future
  
  def isCompleted: Boolean = future.isCompleted
  
  def map[R](success: S => R): FutureVal[F, R] = 
    fold(f => f, success)
  
  def failMap[T](failure: F => T): FutureVal[T, S] = 
    fold(failure, s => s)
  
  def failWith[T >: F](failure: T): FutureVal[T, S] = {
    pureFold (
      _ => Failure(failure),
      s => Success(s)
    )
  }
  
  def fold[T, R](failure: F => T, success: S => R): FutureVal[T, R] = {
    pureFold (
      f => Failure(failure(f)),
      s => Success(success(s))
    )(t => failure(timeout(t)))
  }
  
  def pureFold[T, R](failure: F => Validation[T, R], success: S => Validation[T, R])(
      implicit onTimeout: TimeoutException => T): FutureVal[T, R] = {
    new FutureVal(future.map {
      case Failure(f) => failure(f)
      case Success(s) => success(s)
    })
  }
  
  def flatFold[T, R](failure: F => FutureVal[T, R], success: S => FutureVal[T, R])(
      implicit onTimeout: TimeoutException => T): FutureVal[T, R] = {
    new FutureVal(future.flatMap {
      case Failure(failure_) => failure(failure_).asFuture
      case Success(success_) => success(success_).asFuture
    })
  }
  
  // can't do a flatMap[A, A]: "[A, A] do not conform to method parameter bounds"
  def flatMap[T >: F, R](success: S => FutureVal[T, R]): FutureVal[T, R] = {
    new FutureVal(future.flatMap {
      case Failure(failure_) => Promise.successful(Failure(failure_))
      case Success(success_) => success(success_).asFuture
    })
  }
  
  def flatMapFail[T, R >: S](failure: F => FutureVal[T, R])(
      implicit onTimeout: TimeoutException => T): FutureVal[T, R] = {
    new FutureVal(future.flatMap {
      case Failure(failure_) => failure(failure_).asFuture
      case Success(success_) => Promise.successful(Success(success_))
    })
  }
  
  def recover[R >: S](pf: PartialFunction[F, R]): FutureVal[F, R] = {
    new FutureVal(future.map {
      case Failure(failure) if pf.isDefinedAt(failure)=> Success(pf(failure))
      case v => v 
    })
  }
  
  def discard[T >: F](pf: PartialFunction[S, T]): FutureVal[T, S] = {
    new FutureVal(future.map {
      case Success(success) if pf.isDefinedAt(success)=> Failure(pf(success))
      case v => v 
    })
  }
  
//  def transfer[T >: F, R >: S](pf: PartialFunction[Validation[F, S], Validation[T, R]])(
//      implicit onTimeout: TimeoutException => T): FutureVal[T, R] = {
//    new FutureVal(future.map {
//      case v if pf.isDefinedAt(v)=> pf(v)
//      case v => v
//    })
//  }
  
  def foreach(f: S => Unit): Unit = future foreach { _ foreach f }
  
  def value: Option[Validation[F, S]] = {
    future.value.fold (
      either => either.fold(
          _ => sys.error("The inner future of a FutureVal cannot have a value of Left[Throwable]."),
          vali => Some(vali)
        ),
      None
    )
    /* i.e:
    future.value match {
      case Some(Right(vali)) => Some(vali)
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
  
  def result: Validation[F, S] = {
    value.fold(
      s => s,
      Failure(timeout(new TimeoutException()))
    )
  }
  
  def result(atMost: Duration): Validation[F, S] = {
    try {
      Await.result(future, atMost)
    } catch {
      case e: TimeoutException => Failure(timeout(e))
    }
  }
  
  def waitFor(atMost: Duration): FutureVal[F, S] = {
    try {
      Await.result(future, atMost)
      this
    } catch {
      case e => this 
    }
  }
  
  def waitAnd(atMost: Duration)(f: FutureVal[F, S] => Unit): FutureVal[F, S] = {
    try {
      Await.result(future, atMost)
      f(this)
      this
    } catch {
      case e => { f(this); this } 
    }
  }
  
  def readyIn(atMost: Duration): FutureVal[F, S] = {
    FutureVal.validated(
      try {
        Await.result(future, atMost)
      } catch {
        case e: TimeoutException => Failure(timeout(e))
      }
    )
  }
  
  def onTimeout[T >: F](onTimeout: TimeoutException => T): FutureVal[T, S] = { 
    new FutureVal[T, S](future)(onTimeout, context)
  }
  
  def onSuccess(pf: PartialFunction[S, _]): FutureVal[F, S] =
    onComplete{case Success(s) => pf(s)}
  
  def onFailure(pf: PartialFunction[F, _]): FutureVal[F, S] =
    onComplete{case Failure(f) => pf(f)}
  
  def onComplete(pf: PartialFunction[Validation[F, S], _]): FutureVal[F, S] = {
    future.onSuccess(pf)
    this
  }
  
  def toPromise[R](implicit evF: F <:< R, evS: S <:< R): VSPromise[R] =
    VSPromise.applyTo(this.asInstanceOf[FutureVal[R, R]])
  
  def toPromise[R](onTimeout: TimeoutException => R)(implicit evF: F <:< R, evS: S <:< R): VSPromise[R] =
    VSPromise.applyTo(this.asInstanceOf[FutureVal[R, R]].onTimeout(onTimeout)) // TODO default timeout?
  
}

