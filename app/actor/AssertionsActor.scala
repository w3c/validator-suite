package org.w3.vs.actor

import org.w3.vs._
import org.w3.vs.model._
import org.w3.vs.assertor._
import akka.actor.{ActorSystem => AkkaActorSystem, _}
import scala.concurrent._
import scala.util._
import scala.collection.mutable.Queue
import scala.concurrent.stm._
import org.w3.vs.util._
import JobActor.{ logger => _, _ }

object AssertionsActor {

  val MAX_PENDING_ASSERTION = 2

  val logger = play.Logger.of(classOf[AssertionsActor])

}

import AssertionsActor._

class AssertionsActor(job: Job)(implicit vs: ActorSystem) extends Actor {

  implicit val ec = vs.system.dispatchers.lookup("assertor-dispatcher")

  val pendingAssertions: Ref[Int] = Ref(0)

  val queue = Queue[AssertorCall]()

  private def scheduleAssertion(runId: RunId, assertor: FromHttpResponseAssertor, response: HttpResponse): Unit = {

    atomic { implicit txn => pendingAssertions += 1 }
    val sender = self
    
    Future {
      assertor.assert(response, job.strategy.assertorsConfiguration(assertor.id))
    } andThen { case _ =>
      atomic { implicit txn => pendingAssertions -= 1 }
    } andThen {
      case Failure(t) => {
        logger.error(s"${runId.shortId}: ${assertor} failed to assert ${response.url} because [${t.getMessage}]", t)
        sender ! AssertorFailure(assertor.id, response.url, why = t.getMessage)
      }
      case Success(assertorResponse) => sender ! assertorResponse
    }
    
  }

  def receive = {

    case result: AssertorResult => {
      // not sure why this is done this way (Alex)
      context.parent ! result
      while (queue.nonEmpty && pendingAssertions.single() <= MAX_PENDING_ASSERTION) {
        val AssertorCall(runId, assertorId, nextRI) = queue.dequeue()
        scheduleAssertion(runId, assertorId, nextRI)
      }
    }

    case result: AssertorFailure => {
      context.parent ! result
      while (queue.nonEmpty && pendingAssertions.single() <= MAX_PENDING_ASSERTION) {
        val AssertorCall(runId, assertorId, nextRI) = queue.dequeue()
        scheduleAssertion(runId, assertorId, nextRI)
      }
    }

    case call @ AssertorCall(runId, assertorId, response) => {
      
      if (pendingAssertions.single() > MAX_PENDING_ASSERTION) {
        queue.enqueue(call)
      } else {
        scheduleAssertion(runId, assertorId, response)
      }
    }

  }


}
