package org.w3.vs.actor

import org.w3.vs._
import org.w3.vs.model._
import org.w3.util._
import org.w3.vs.assertor._
import akka.dispatch._
import akka.actor._
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy
import java.util.concurrent.TimeUnit.MILLISECONDS
import play.Logger
import System.{ currentTimeMillis => now }
import org.w3.vs.http._
import akka.util.duration._
import akka.util.Duration
import scala.collection.mutable.LinkedList
import scala.collection.mutable.LinkedHashMap
import play.api.libs.iteratee.PushEnumerator
import org.w3.util.Headers.wrapHeaders
import akka.pattern.pipe
import message.GetJobData
import scalaz._
import Scalaz._
import org.joda.time.DateTime
import org.w3.util.akkaext._
import scala.collection.mutable.Queue
import org.w3.vs.actor.message.Stop

case class AssertorCall(assertorId: AssertorId, resourceInfo: ResourceInfo)

object AssertionsActor {

  val MAX_PENDING_ASSERTION = 2

}

import AssertionsActor._

class AssertionsActor(job: Job)(implicit val configuration: VSConfiguration) extends Actor {

  import configuration.assertorExecutionContext

  var pendingAssertions: Int = 0

  val queue = Queue[AssertorCall]()

  private final def scheduleAssertion(assertorId: AssertorId, resourceInfo: ResourceInfo): Unit = {

    val assertor = FromURLAssertor(assertorId)

    val url = resourceInfo.url

    val futureAssertionResult = Future {
      assertor.assert(url) fold (
        throwable => AssertorFail(
          url = url,
          assertorId = assertor.id,
          jobId = job.id,
          why = throwable.getMessage),
        assertions => Assertions(
          url = url,
          assertorId = assertor.id,
          jobId = job.id,
          assertions = assertions))
    }(assertorExecutionContext)
      
    // register callback on the completion of the future
    futureAssertionResult onComplete {
      case Left(throwable) => {
        val fail = AssertorFail(
          url = url,
          assertorId = assertor.id,
          jobId = job.id,
          why = throwable.getMessage)
        self ! fail
      }
      case Right(assertionResult) =>
        self ! assertionResult
    }
    
    pendingAssertions += 1
    
  }



  def receive = {

    case Stop => {
      queue.dequeueAll(_ => true)
    }

    case result: AssertorResult => {
      pendingAssertions -= 1
      context.parent ! result
      while (queue.nonEmpty && pendingAssertions <= MAX_PENDING_ASSERTION) {
        val AssertorCall(assertorId, nextRI) = queue.dequeue()
        scheduleAssertion(assertorId, nextRI)
      }
    }

    case call @ AssertorCall(assertorId, resourceInfo) => {
      if (pendingAssertions > MAX_PENDING_ASSERTION) {
        queue.enqueue(call)
      } else {
        scheduleAssertion(assertorId, resourceInfo)
      }
    }

  }


}
