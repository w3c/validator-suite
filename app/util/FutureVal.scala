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
  
  def apply[S](future: Future[S])(implicit context: ExecutionContext): FutureVal[Throwable, S] = {
    new FutureVal(future.map[Validation[Throwable, S]] { value =>
      Success(value)
    } recover { case t: Throwable =>
      Failure(t)
    })
  }
  
  def apply[S](body: => S)(implicit context: ExecutionContext): FutureVal[Throwable, S]  = {
    new FutureVal(Future(fromTryCatch(body)))
  }
  
  def pure[S](body: => S)(implicit context: ExecutionContext): FutureVal[Throwable, S] = {
    new FutureVal(Promise.successful(fromTryCatch(body)))
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
  
  // I actually prefer to do Future.pure(option.get) which is equivalent so this method is not really useful.
  def fromOption[S](option: Option[S])(implicit context: ExecutionContext): FutureVal[Throwable, S] = {
    option fold (
      s => FutureVal.successful(s),
      FutureVal.failed[Throwable, S](new NoSuchElementException)
    )
  }
  
  def sequence[S](iterable: Iterable[Future[S]])(implicit context: ExecutionContext): FutureVal[Throwable, Iterable[S]] = {
    FutureVal(Future.sequence(iterable))
  }
  
  def sequence[F, S](iterable: Iterable[FutureVal[F, S]])(implicit context: ExecutionContext, onTimeout: TimeoutException => F): FutureVal[F, Iterable[S]] = {
    // manipulate Futures
    val futures: Iterable[Future[Validation[F, S]]] = iterable.view map { _.future }
    // reduce the Futures to a single one
    val future: Future[Iterable[Validation[F, S]]] = Future.sequence(futures)
    // we should move to scalaz's \/ type or just wait for Scala 2.10
    implicit val semigroup: Semigroup[F] = Semigroup.firstSemigroup[F]
    // there is no Traversable typeclass instance for Iterable made available in scalaz
    // but there is one for List, hence the .toList
    val futureValidation: Future[Validation[F, Iterable[S]]] =
      future map { validations => validations.toList.sequence[({type l[x] = Validation[F, x]})#l, S] }
    val futureVal: FutureVal[F, Iterable[S]] = new FutureVal(futureValidation)
    futureVal
  }
  
  // An implicit conversion from FutureVal[Throwable, A] to FutureVal[Exception, A]. We don't try to catch Error typed exceptions.
  implicit def toFutureValException[A](f: FutureVal[Throwable, A]): FutureVal[Exception, A] =
    f failMap {
      case e: Exception => e
      case error => println(error); throw error
    }
  
  implicit def toFutureValExceptionS[A](iterable: Iterable[FutureVal[Throwable, A]]): Iterable[FutureVal[Exception, A]] =
    iterable.map(a => toFutureValException(a))
  
}

class FutureVal[+F, +S] protected[util] (
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
  
  def flatMapValidation[T >: F, R](f: S => Validation[T, R]): FutureVal[T, R] =
    new FutureVal(future map { _.flatMap(f) })
    //new FutureVal(future map { _.flatMap(s => fromTryCatch(f(s)).fold(f => {println(f); throw f}, ss => ss)) })
  
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
    
  def result(atMost: Duration = Duration(5, "seconds")): Validation[F, S] = {
    try {
      Await.result(future, atMost)
    } catch {
      case e: TimeoutException => Failure(timeout(e))
    }
  }

  def getOrFail(atMost: Duration = Duration(5, "seconds"))(implicit ev: F <:< Throwable): S = {
    try {
      val r = Await.result(future, atMost)
      r.fold(t => throw ev(t), s => s)
    } catch {
      case e: TimeoutException => throw e
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

  import play.api.libs.concurrent.{ Promise => PlayPromise, _ }
  
  def toPromise[R](implicit evF: F <:< R, evS: S <:< R): PlayPromise[R] = {
    val f: Future[Validation[R, R]] = this.asInstanceOf[FutureVal[R, R]].future
    f.map(_.fold(f => f, s => s)).asPromise
  }
    
    // VSPromise.applyTo(this.asInstanceOf[FutureVal[R, R]])
  
  def toPromise[R](onTimeout: TimeoutException => R)(implicit evF: F <:< R, evS: S <:< R): PlayPromise[R] = {
    val f: Future[Validation[R, R]] = this.asInstanceOf[FutureVal[R, R]].future.onFailure {
      case te: TimeoutException => onTimeout(te)
    }
    f.map(_.fold(f => f, s => s)).asPromise
  }
//    VSPromise.applyTo(this.asInstanceOf[FutureVal[R, R]].onTimeout(onTimeout)) // TODO default timeout?
  
}

