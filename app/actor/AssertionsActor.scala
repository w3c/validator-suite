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

case class AssertorCall(assertor: FromHttpResponseAssertor, response: HttpResponse)

object AssertionsActor {

  val MAX_PENDING_ASSERTION = 2

}

import AssertionsActor._

class AssertionsActor(job: Job)(implicit val configuration: VSConfiguration) extends Actor {


  var pendingAssertions: Ref[Int] = Ref(0)

  val queue = Queue[AssertorCall]()

  private final def scheduleAssertion(assertor: FromHttpResponseAssertor, response: HttpResponse): Unit = {
    
    assertor.assert(response) onComplete {
      case _ => pendingAssertions.single() -= 1
    } onComplete {
      case Failure(f) => self ! f
      case Success(results) => results foreach (result => self ! result)
    }
    
    pendingAssertions.single() += 1
    
  }

  def receive = {

    case Stop => {
      queue.dequeueAll(_ => true)
    }
    case result: AssertorResult => {
      //pendingAssertions -= 1
      context.parent ! result
      while (queue.nonEmpty && pendingAssertions.single() <= MAX_PENDING_ASSERTION) {
        val AssertorCall(assertorId, nextRI) = queue.dequeue()
        scheduleAssertion(assertorId, nextRI)
      }
    }
    case result: AssertorFailure => {
      //pendingAssertions -= 1
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
