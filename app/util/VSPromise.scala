package org.w3.util

import akka.dispatch.{Promise => AkkaPromise, _}
import akka.util.duration._
import akka.util.Duration
import scalaz._
import Scalaz._
import Validation._
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import play.api.mvc.Result
import play.api.libs.concurrent._
import scala.concurrent.stm._
import org.joda.time.DateTime
import org.w3.util.Pimps._

object VSPromise {
  
  def apply[A](block: => A)(onThrowable: Throwable => A)(implicit context: ExecutionContext): VSPromise[A] = {
    applyTo(FutureVal(block).failMap(onThrowable(_)))
  }
  
  def pure[A](body: => A)(onThrowable: Throwable => A)(implicit context: ExecutionContext,
      onTimeout: TimeoutException => A): VSPromise[A] = {
    applyTo(FutureVal.pure(body).failMap(onThrowable(_)).onTimeout(onTimeout))
  }
  
  def successful[A](success: A)(implicit context: ExecutionContext, 
      onTimeout: TimeoutException => A): VSPromise[A] = {
    applyTo(FutureVal.successful(success))
  }
  
  def failed[A](failure: A)(implicit context: ExecutionContext,
      onTimeout: TimeoutException => A): VSPromise[A] = {
    applyTo(FutureVal.failed(failure))
  }
  
  def fromValidation[A](validation: Validation[A, A])(implicit context: ExecutionContext,
      onTimeout: TimeoutException => A): VSPromise[A] = {
    applyTo(FutureVal.fromValidation(validation))
  }
    
  def applyTo[A](future: FutureVal[A, A]): VSPromise[A] = {
    val promise: VSPromise[A] = new VSPromise[A](future)
    future.onComplete{case r => {promise.redeem(r)}}
    promise
  }
  
}

class VSPromise[A] private (private val future: FutureVal[A, A]) extends Promise[A] {
  
  implicit val context = play.core.Invoker.promiseDispatcher
  
  private val redeemed: Ref[Option[Validation[A, A]]] = Ref(None.asInstanceOf[Option[Validation[A, A]]])
  
  private val callbacks: Ref[List[PartialFunction[Validation[A, A], _]]] = Ref[List[PartialFunction[Validation[A, A], _]]](List())
  
  private val timeout: Option[(Duration, DateTime)] = None
  
  // @deprecated(message = "Use onComplete", since = "")
  override def onRedeem(callback: A => Unit): Unit = {
    onComplete {case a => a.fold(f => callback(f), s => callback(s))}
  }
  
  // @deprecated(message = "Not implemented", since = "")
  override def recover[AA >: A](pf: PartialFunction[Throwable, AA]): Promise[AA] = {this}
  
  // @deprecated(message = "Extend does not make sense for a VSPromise. Implemented because of Play. Use pureFold.", since = "")
  override def extend[B](k: Function1[Promise[A], B]): Promise[B] = {
    def fail(t: Throwable): B = t match {
      case t: TimeoutException => k(VSPromise.successful(future.timeout(t))(context, future.timeout))
      case _ => sys.error("Using extend breaks the Promise if the passed function throws an Exception. Use pureFold on the promise instead.")
    }
    VSPromise (
      k(VSPromise.successful(await(1 hour).get)(context, future.timeout))
    )(fail)
  }
  
  // @deprecated(message = "Use pureFold", since = "")
  override def extend1[B](k: Function1[NotWaiting[A], B]): Promise[B] = {
    extend[B](p => k(p.value))//.await(1, TimeUnit.HOURS))) 
    /* as Play uses it:
    p.extend1 {
      case Redeemed(v) => handle(v)
      case Thrown(e) => {
        Logger("play").error("Waiting for a promise, but got an error: " + e.getMessage, e)
        handle(Results.InternalServerError)
      }
    }*/
  }
  
  // @deprecated(message = "This is a blocking call (5 sec) in Play's Promise! Use current() instead for a non-blocking probe of the current promise value.", since = "")
  override def value: NotWaiting[A] = super.value
  
  // I kept the semantic of await by returning a Thrown but timeouts are already dealt with by the inner FuturVal.  
  // FuturVals do not throw any exceptions, these are part of their failure value. Use await(d: akka.util.Duration).
  // @deprecated(message="Use await1", since="")
  override def await(timeout: Long, unit: TimeUnit = TimeUnit.MILLISECONDS): NotWaiting[A] = {
    val duration = akka.util.Duration(timeout, unit)
    atomic { implicit txn =>
      if (!redeemed().isDefined) {
        retryFor(duration.toNanos, scala.actors.threadpool.TimeUnit.NANOSECONDS)
        Thrown(new TimeoutException("No result computed after " + duration))
      } else {
        Redeemed(redeemed().get.fold(f => f, s => s))
      }
    }
  }
  
  // TODOS?
  override def or[B](other: Promise[B]): Promise[Either[A, B]] = {sys.error("VSPromise.or not implemented")}
  override def orTimeout[B](message: B, duration: Long, unit: TimeUnit = TimeUnit.MILLISECONDS): Promise[Either[A, B]] = {sys.error("VSPromise.orTimeout not implemented")}
  override def filter(p: A => Boolean): VSPromise[A] = {sys.error("VSPromise.filter not implemented")}
  
  
  
  
  
  
  // Based on the FutureVal API
  
  def asFutureVal = future
  
  def isCompleted: Boolean = redeemed.single().isDefined
  
  override def map[B](result: A => B): Promise[B] =
    fold(result, result)
  
  def mapSuccess(success: A => A): VSPromise[A] =
    fold(f => f, success)
  
  def mapFail(failure: A => A): VSPromise[A] =
    fold(failure, s => s)
  
  def failMap(failure: A => A): VSPromise[A] = mapFail(failure)
  
  def fold[B](failure: A => B, success: A => B): VSPromise[B] = {
    pureFold (
      f => Failure(failure(f)),
      s => Success(success(s))
    )
  }
  
  private def forwardTimeout(promise: VSPromise[_]) = {
    timeout map {
      case (duration, started) => started.toInstant.getMillis + duration.toMillis - DateTime.now().toInstant.getMillis
    } map {
      case time if (time > 0) => promise.timeoutIn(Duration(time, TimeUnit.MILLISECONDS))
      case _ =>
    }
  }
  
  def pureFold[B](failure: A => Validation[B, B], success: A => Validation[B, B]): VSPromise[B] = {
    atomic { implicit txn =>
      val p = new VSPromise[B](future = future.pureFold(failure, success)(t => failure(future.timeout(t)).fold(f => f, s => s)))
      redeemed.single() match {
        case Some(Failure(s)) => p.redeem(failure(s))
        case Some(Success(s)) => p.redeem(success(s))
        case None =>
      }
      forwardTimeout(p)
      p
    }
  }
  
  def flatFold[B](failure: A => VSPromise[B], success: A => VSPromise[B])(implicit onTimeout: TimeoutException => B): VSPromise[B] = {
    atomic { implicit txn =>
      val p = new VSPromise[B](future = future.flatFold(f => failure(f).future, s => success(s).future))
      redeemed.single() match {
        case Some(Failure(f)) => Future { p.redeem(Failure(failure(f).result(1 hour))) }
        case Some(Success(s)) => Future { p.redeem(Success(success(s).result(1 hour))) }
        case None =>
      }
      forwardTimeout(p)
      p
    }
  }
  
  override def flatMap[B](success: A => Promise[B]): Promise[B] = {
    sys.error("VSPromise.flatMap not implemented")
//    atomic { implicit txn =>
//      val p = new VSPromise[B](future = future.flatMap(f => VSPromise.failed[B](f)(context, future.timeout).future, s => success(s).future)(future.timeout))
//      redeemed.single() match {
//        case Some(Failure(f)) => p.redeem(Failure(failure(f).result(1 hour))) // p.redeem is non blocking
//        case Some(Success(s)) => p.redeem(Success(success(s).result(1 hour)))
//        case None =>
//      }
//      forwardTimeout(p)
//      p
//    }
  }
  
  def foreach(f: A => Unit): Unit = future foreach f
  
  def value1: Option[A] = {
    try {
      Some(redeemed.single().get.fold(f => f, s => s))
    } catch {
      case e => None
    }
  }
  
  def await(duration: Duration): Option[A] = {
    atomic { implicit txn =>
      if (!redeemed().isDefined) {
        retryFor(duration.toNanos, scala.actors.threadpool.TimeUnit.NANOSECONDS)
        None
      } else {
        Some(redeemed().get.fold(f => f, s => s))
      }
    }
  }
  
  def result: A = {
    try {
      redeemed.single().get.fold(f => f, s => s)
    } catch {
      case e => future.timeout(new TimeoutException("No result computed yet"))
    }
  }
  
  def result(atMost: Duration): A = {
    atomic { implicit txn =>
      if (!redeemed().isDefined) {
        retryFor(atMost.toNanos, scala.actors.threadpool.TimeUnit.NANOSECONDS)
        future.timeout(new TimeoutException("No result computed after " + atMost))
      } else {
        redeemed().get.fold(f => f, s => s)
      }
    }
  }
  
  def waitFor(atMost: Duration): VSPromise[A] = {
    atomic { implicit txn =>
      if (!redeemed().isDefined)
        retryFor(atMost.toNanos, scala.actors.threadpool.TimeUnit.NANOSECONDS)
    }
    this
  }
  
  def waitAnd(atMost: Duration)(f: Function1[VSPromise[A], Unit] = {_: VSPromise[A] => ()}): VSPromise[A] = {
    atomic { implicit txn =>
      if (!redeemed().isDefined)
        retryFor(atMost.toNanos, scala.actors.threadpool.TimeUnit.NANOSECONDS)
    }
    f(this)
    this
  }
  
  def readyIn(atMost: Duration): VSPromise[A] = {
    atomic { implicit txn =>
      if (!redeemed().isDefined) {
        retryFor(atMost.toNanos, scala.actors.threadpool.TimeUnit.NANOSECONDS)
        redeem(Failure(future.timeout(new TimeoutException("No result computed after " + atMost))))
      }
    }
    this
  }
  
  def onTimeout[B >: A](result: B): VSPromise[B] = { 
    new VSPromise[B](future.onTimeout(_ => result))
  }
  
  def onSuccess(callback: PartialFunction[A, _]): VSPromise[A] =
    onComplete{case Success(s) => callback(s)}
  
  def onFailure(callback: PartialFunction[A, _]): VSPromise[A] =
    onComplete{case Failure(f) => callback(f)}

  def onComplete(callback: PartialFunction[Validation[A, A], _]): VSPromise[A] = {
    atomic { implicit txn => callbacks() = callbacks() :+ callback }
    this
  }
  
  def timeoutIn(duration: Duration): VSPromise[A] = {
    play.core.Invoker.system.scheduler.scheduleOnce(duration) {
      redeem(Failure(asFutureVal.timeout(new TimeoutException("Timed out the promise"))))
    }
    this
  }
    
  def redeem(body: => Validation[A, A]): Unit = {
    atomic { implicit txn =>
      if (!redeemed().isDefined) {
        try {
          redeemed() = Some(body)
          callbacks() map { _(redeemed().get) }
        } catch {
          case e => () // TODO log
        }
      }
    }
  }
  
}