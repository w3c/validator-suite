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
import message._

class OrganizationActor(organizationData: OrganizationData)(implicit val configuration: VSConfiguration)
extends Actor {

  val jobsRef: ActorRef = context.actorOf(Props(new JobsActor()), name = "jobs")

  def receive = {
    case m: Message => jobsRef.forward(m)
    case other => println("&&&"+other)
  }

}
