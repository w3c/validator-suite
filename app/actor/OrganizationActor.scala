package org.w3.vs.actor

import org.w3.vs._
import org.w3.vs.model._
import org.w3.util._
import org.w3.vs.assertor._
import org.w3.vs.actor.message._
import akka.dispatch._
import akka.actor._
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy
import java.util.concurrent.TimeUnit.MILLISECONDS
import play.Logger
import play.api.libs.iteratee._
import System.{ currentTimeMillis => now }
import org.w3.vs.http._
import akka.util.duration._
import akka.util.Duration
import scala.collection.mutable.LinkedList
import scala.collection.mutable.LinkedHashMap
import org.w3.util.Headers.wrapHeaders
import akka.pattern.pipe
import scalaz._
import Scalaz._
import org.joda.time.DateTime
import org.w3.vs.actor.message._
import org.w3.util.akkaext._

class OrganizationActor(organization: Organization)(implicit val configuration: VSConfiguration) extends Actor {

  val logger = play.Logger.of(classOf[OrganizationActor])

  val jobsRef: ActorRef = context.actorOf(Props(new JobsActor()), name = "jobs")
  
  lazy val (enumerator, channel) = Concurrent.broadcast[RunUpdate]
  
  def receive: Actor.Receive = {
    case m: Tell => {
      m.message match {
        case GetEnumerator => sender ! enumerator
        case _ => jobsRef.forward(m) 
      }
    }
    case m: RunUpdate => channel.push(m)
  }

}
