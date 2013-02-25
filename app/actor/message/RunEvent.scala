package org.w3.vs.model

import org.joda.time._
import akka.actor.ActorPath

/* any event that has an impact on the state of a run */
sealed trait RunEvent {
  def userId: UserId
  def jobId: JobId
  def runId: RunId
  def timestamp: DateTime
}

case class CreateRunEvent(userId: UserId, jobId: JobId, runId: RunId, actorPath: ActorPath, strategy: Strategy, createdAt: DateTime, timestamp: DateTime = DateTime.now(DateTimeZone.UTC)) extends RunEvent

case class CompleteRunEvent(userId: UserId, jobId: JobId, runId: RunId, runData: RunData, resourceDatas: Iterable[ResourceData], timestamp: DateTime = DateTime.now(DateTimeZone.UTC)) extends RunEvent

case class CancelRunEvent(userId: UserId, jobId: JobId, runId: RunId, runData: RunData, resourceDatas: Iterable[ResourceData], timestamp: DateTime = DateTime.now(DateTimeZone.UTC)) extends RunEvent

case class AssertorResponseEvent(userId: UserId, jobId: JobId, runId: RunId, ar: AssertorResponse, timestamp: DateTime = DateTime.now(DateTimeZone.UTC)) extends RunEvent

case class ResourceResponseEvent(userId: UserId, jobId: JobId, runId: RunId, rr: ResourceResponse, timestamp: DateTime = DateTime.now(DateTimeZone.UTC)) extends RunEvent

/*case class RunUpdate(
  userId: UserId,
  jobId: JobId,
  runId: RunId,
  runData: RunData,
  timestamp: DateTime
) extends RunEvent*/