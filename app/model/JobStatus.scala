package org.w3.vs.model

import java.nio.channels.ClosedChannelException
import org.joda.time.{ DateTime, DateTimeZone }
import org.w3.util.akkaext._
import akka.actor._
import play.api.libs.iteratee._
import play.Logger
import org.w3.util._
import scalaz.Equal
import scalaz.Equal._
import org.w3.vs._
import org.w3.vs.actor.JobActor._
import scala.concurrent.{ ops => _, _ }
import scala.concurrent.ExecutionContext.Implicits.global

sealed trait JobStatus

case object NeverStarted extends JobStatus
case object Zombie extends JobStatus
case class Running(runId: RunId, actorPath: ActorPath) extends JobStatus
case class Done(
  runId: RunId,
  reason: DoneReason,
  completedOn: DateTime,
  runData: RunData) extends JobStatus

sealed trait DoneReason
case object Cancelled extends DoneReason
case object Completed extends DoneReason
