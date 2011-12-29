package org.w3.vs.observer

import org.w3.vs._
import org.w3.vs.model._
import org.w3.util._
import org.w3.vs.assertor._
import scala.collection.mutable.{Map, Queue, Seq, Set}
import akka.actor._
import akka.dispatch._
import akka.util.duration._
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy
import java.util.concurrent.TimeUnit.MILLISECONDS
import play.Logger

object FromHttpResponseAssertor {
  def newInstance(
      observerId: ObserverId,
      assertorPicker: AssertorPicker) = {
    TypedActor.newInstance(
      classOf[FromHttpResponseAssertor],
      new FromHttpResponseAssertorImpl(observerId, assertorPicker),
      3.seconds.toMillis)
  }
}

/**
 * An asynchronous assertor based on an HttpResponse
 */
trait FromHttpResponseAssertor {
  def assert(httpResponse: HttpResponse): Unit
}

/**
 * A FromHttpResponseAssertor that binds an Observer and several assertors through an AssertorPicker
 */
class FromHttpResponseAssertorImpl private[observer] (
    observerId: ObserverId,
    assertorPicker: AssertorPicker) extends TypedActor with FromHttpResponseAssertor {
  
  val observer: Observer = Observer.byObserverId(observerId).get
  
  /**
   * - Pick the assertors based on the content-type
   * - Make assertions
   * - Send them back to the observer
   */
  def assert(httpResponse: HttpResponse): Unit = {
    val url = httpResponse.url
    val ctOption = httpResponse.headers.contentType
    val assertors = assertorPicker.pick(ctOption)
    val numberOfAssertors = assertors.size
    if (numberOfAssertors == 0)
      observer.noAssertion(url)
    else
      for {
        assertor <- assertors
      } {
        val f = assertor.assert(url) onResult {
            case observation =>
              observer.sendAssertion(
                  url,
                  assertor.id,
                  observation,
                  numberOfAssertors)
          } onException {
            case t: Throwable =>
              observer.sendAssertionError(
                  url,
                  assertor.id,
                  t,
                  numberOfAssertors)
          } onTimeout {
            f =>
              observer.sendAssertionError(
                  url,
                  assertor.id,
                  exception(url, assertor),
                  numberOfAssertors)
          }
        f.await
      }
  }
  
  def exception(url: URL, assertor: Assertor) =
    new FutureTimeoutException("observing %s with %s took too long" format (url, assertor.id))
  
}

