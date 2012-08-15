package org.w3.vs.actor

import org.w3.vs._
import org.w3.vs.model._
import org.w3.vs.assertor._
import akka.actor._
import System.{ currentTimeMillis => now }
import scalaz._
import scala.collection.mutable.Queue
import org.w3.vs.actor.message.Stop
import scala.concurrent.stm._
import org.w3.util._

case class AssertorCall(assertor: FromHttpResponseAssertor, response: HttpResponse)

object AssertionsActor {

  val MAX_PENDING_ASSERTION = 2

}

import AssertionsActor._

class AssertionsActor(job: Job)(implicit conf: VSConfiguration) extends Actor {

  val logger = play.Logger.of(classOf[AssertionsActor])

  implicit val executionContext = conf.assertorExecutionContext

  var pendingAssertions: Ref[Int] = Ref(0)

  val queue = Queue[AssertorCall]()

  private final def scheduleAssertion(assertor: FromHttpResponseAssertor, response: HttpResponse): Unit = {

    pendingAssertions.single() += 1
    
    assertor assert response onComplete {
      case _ => pendingAssertions.single() -= 1
    } onComplete {
      case Failure(f: AssertorFailure) => self ! f
      case Success(result: AssertorResult) => self ! result
    }
    
  }

  def receive = {

    case Stop => {
      queue.dequeueAll(_ => true)
    }

    case result: AssertorResult => {
      // not sure why this is done this way (Alex)
      context.parent ! result
      while (queue.nonEmpty && pendingAssertions.single() <= MAX_PENDING_ASSERTION) {
        val AssertorCall(assertorId, nextRI) = queue.dequeue()
        scheduleAssertion(assertorId, nextRI)
      }
    }

    case result: AssertorFailure => {
      context.parent ! result
      while (queue.nonEmpty && pendingAssertions.single() <= MAX_PENDING_ASSERTION) {
        val AssertorCall(assertorId, nextRI) = queue.dequeue()
        scheduleAssertion(assertorId, nextRI)
      }
    }

    case call @ AssertorCall(assertorId, response) => {
      if (pendingAssertions.single() > MAX_PENDING_ASSERTION) {
        queue.enqueue(call)
      } else {
        scheduleAssertion(assertorId, response)
      }
    }

  }


}
