package org.w3.vs.model

import org.joda.time._
import akka.actor.ActorPath
import org.w3.vs.web.URL

/* any event that has an impact on the state of a run */
sealed trait RunEvent {
  def userId: Option[UserId]
  def jobId: JobId
  def runId: RunId
  def timestamp: DateTime
}

case class CreateRunEvent(userId: Option[UserId], jobId: JobId, runId: RunId, actorName: RunningActorName, strategy: Strategy, timestamp: DateTime = DateTime.now(DateTimeZone.UTC)) extends RunEvent

case class DoneRunEvent(
  userId: Option[UserId],
  jobId: JobId,
  runId: RunId,
  doneReason: DoneReason,
  resources: Int,
  errors: Int,
  warnings: Int,
  resourceDatas: Map[URL, ResourceData],
  groupedAssertionsDatas: Iterable[GroupedAssertionData],
  timestamp: DateTime = DateTime.now(DateTimeZone.UTC)) extends RunEvent

case class AssertorResponseEvent(userId: Option[UserId], jobId: JobId, runId: RunId, ar: AssertorResponse, timestamp: DateTime = DateTime.now(DateTimeZone.UTC)) extends RunEvent

case class ResourceResponseEvent(userId: Option[UserId], jobId: JobId, runId: RunId, rr: ResourceResponse, timestamp: DateTime = DateTime.now(DateTimeZone.UTC)) extends RunEvent
