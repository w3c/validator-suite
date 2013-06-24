package org.w3.vs.model

import org.joda.time.{ DateTime, DateTimeZone }
import akka.actor.ActorPath
import org.w3.vs.web.URL

sealed trait JobStatus

case object NeverStarted extends JobStatus
case object Zombie extends JobStatus
case class Running(runId: RunId, actorName: RunningActorName) extends JobStatus
case class Done(
  runId: RunId,
  reason: DoneReason,
  completedOn: DateTime,
  runData: RunData) extends JobStatus
